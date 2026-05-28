"""
RING-GO 백엔드 서버 (Phase 1: Claude Sonnet 4.6 API)
─────────────────────────────────────────────────────────────────
- POST /prepare-reply       : SMS 수신 시 fire-and-forget. 백그라운드에서 Claude 호출.
- GET  /suggestions/{phone} : 캐시된 답변 후보 3개 즉시 반환.
- GET  /admin/usage         : 최근 24시간 API 호출수/토큰/비용.
- GET  /healthz             : 헬스체크.

설계 요약:
  · 포트 8000, 0.0.0.0 바인딩 (Tailscale 내부에서만 접근)
  · LLM = Anthropic Claude Sonnet 4.6 (CLAUDE_API_KEY 환경변수 필수)
  · 캐시 = SQLite (cache.db) — suggestions_cache + api_usage 두 테이블
  · pricing.md mtime 기반 자동 reload
  · 사장님 톤 샘플(ownerToneSamples) few-shot 으로 모방
  · prompt caching = system prompt 캐싱(5분 TTL) → 입력 토큰 비용 ~90% 절약
  · rate limit = 일 2500건 / 한 번호당 일 200건
"""

from __future__ import annotations

import asyncio
import datetime as _dt
import json
import os
import sqlite3
import time
from contextlib import asynccontextmanager, contextmanager
from pathlib import Path
from typing import Optional

import anthropic
import httpx
from fastapi import FastAPI, HTTPException
from fastapi.responses import HTMLResponse, FileResponse, Response
from pydantic import BaseModel, Field

# ============================================================================
# 설정
# ============================================================================
BASE_DIR = Path(__file__).parent
DB_PATH = BASE_DIR / "cache.db"
PRICING_PATH = BASE_DIR / "pricing.md"

CLAUDE_API_KEY = os.environ.get("CLAUDE_API_KEY")
if not CLAUDE_API_KEY:
    raise RuntimeError(
        "CLAUDE_API_KEY env var not set. "
        "launchd plist 의 EnvironmentVariables 에 박혀 있는지 확인하세요."
    )

# §13 — 카카오 로컬 API 키 (선택 — 없으면 /api/address-resolve 는 항상 null 반환)
# launchd plist 의 EnvironmentVariables 또는 .env 로 설정 가능.
KAKAO_REST_API_KEY = os.environ.get("KAKAO_REST_API_KEY")
KAKAO_TIMEOUT_SEC = 5.0

# §14 (SYNC 2026-05-28 12:30 안드로이드 요청) — Gemini 2.5 Flash 키 (✨ 다듬기 endpoint 용)
# launchd plist EnvironmentVariables 에 GEMINI_API_KEY 박혀있어야 /api/refine 동작.
# 미설정 시 endpoint 가 503 응답 → 안드로이드는 "AI 서버 연결 실패" 토스트.
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")
GEMINI_MODEL = "gemini-2.5-flash"
GEMINI_TIMEOUT_SEC = 30.0
GEMINI_MAX_OUTPUT_TOKENS = 500

# §15 — Admin token (사업 metric endpoint 보호용. /api/admin/* 호출 시 X-Admin-Token 헤더 필요)
# 미설정 시 admin endpoint 는 503 (인증 비활성화).
# 사장님이 launchd plist EnvironmentVariables 에 ADMIN_TOKEN=<랜덤 문자열> 박아야 활성화.
ADMIN_TOKEN = os.environ.get("ADMIN_TOKEN")

CLAUDE_MODEL = "claude-sonnet-4-6"
CLAUDE_MAX_TOKENS = 800
CLAUDE_TIMEOUT = 60.0  # 초. 한 호출이 60초 넘으면 끊는다.

# Sonnet 4.6 가격 (per 1M tokens) — 2026 기준
COST_INPUT_PER_M = 3.0
COST_CACHED_INPUT_PER_M = 0.30
COST_OUTPUT_PER_M = 15.0
KRW_PER_USD = 1380  # 대략. 정확한 환율은 외부 API 로 교체 가능.

# Rate limit
DAILY_TOTAL_CALLS_LIMIT = 2500       # 폭주 차단
PER_PHONE_DAILY_LIMIT = 200          # 한 사용자가 다 못 쓰게

# Anthropic 비동기 클라이언트 (asyncio 와 자연스럽게 통합. cancel 도 됨.)
claude_client = anthropic.AsyncAnthropic(api_key=CLAUDE_API_KEY)

# 진행 중인 LLM 백그라운드 태스크: phone -> asyncio.Task
_inflight_tasks: dict[str, asyncio.Task] = {}


# ============================================================================
# pricing.md 자동 reload (mtime 캐시)
# ============================================================================
_pricing_cache: dict = {"mtime": 0.0, "content": ""}


def load_pricing() -> str:
    """pricing.md 를 읽어 반환. 파일이 바뀐 경우에만 디스크에서 다시 읽음."""
    if not PRICING_PATH.exists():
        return "(가격표 파일 없음)"
    mtime = PRICING_PATH.stat().st_mtime
    if mtime != _pricing_cache["mtime"]:
        _pricing_cache["mtime"] = mtime
        _pricing_cache["content"] = PRICING_PATH.read_text(encoding="utf-8")
    return _pricing_cache["content"]


# ============================================================================
# SQLite (캐시 + 사용량)
# ============================================================================
def db_init() -> None:
    with sqlite3.connect(DB_PATH) as con:
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS suggestions_cache (
                phone                   TEXT PRIMARY KEY,
                status                  TEXT NOT NULL,
                based_on_message        TEXT,
                based_on_received_at_ms INTEGER,
                generated_at_ms         INTEGER,
                suggestions_json        TEXT,
                updated_at_ms           INTEGER NOT NULL
            )
            """
        )
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS api_usage (
                id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                phone               TEXT NOT NULL,
                endpoint            TEXT NOT NULL,
                input_tokens        INTEGER NOT NULL DEFAULT 0,
                cached_input_tokens INTEGER NOT NULL DEFAULT 0,
                output_tokens       INTEGER NOT NULL DEFAULT 0,
                cost_usd            REAL    NOT NULL DEFAULT 0,
                created_at_ms       INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_api_usage_phone_created "
            "ON api_usage(phone, created_at_ms)"
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_api_usage_created "
            "ON api_usage(created_at_ms)"
        )
        # P0+P1+P2: 카드/대화 요약 + next-action 결과 캐시
        # key = (phone, endpoint, latest_msg_ts) — 새 메시지 들어오면 자동 invalidation.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS summary_cache (
                phone           TEXT NOT NULL,
                endpoint        TEXT NOT NULL,
                latest_msg_ts   INTEGER NOT NULL,
                response_json   TEXT NOT NULL,
                generated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (phone, endpoint, latest_msg_ts)
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_summary_cache_phone "
            "ON summary_cache(phone, endpoint)"
        )
        # §12.1 — LLM 토큰 모니터링 로그
        # endpoint × model 별 호출수/토큰/원화 비용을 한 줄씩 누적 기록한다.
        # /api/usage-stats?period=today|month|all 가 여기서 집계해서 응답.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS llm_usage_log (
                id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp_ms        INTEGER NOT NULL,
                endpoint            TEXT NOT NULL,
                model               TEXT NOT NULL,
                prompt_tokens       INTEGER NOT NULL DEFAULT 0,
                completion_tokens   INTEGER NOT NULL DEFAULT 0,
                cache_read_tokens   INTEGER NOT NULL DEFAULT 0,
                cache_write_tokens  INTEGER NOT NULL DEFAULT 0,
                cost_krw            REAL    NOT NULL DEFAULT 0
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_llm_usage_log_ts "
            "ON llm_usage_log(timestamp_ms)"
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_llm_usage_log_endpoint_ts "
            "ON llm_usage_log(endpoint, timestamp_ms)"
        )
        # §15 — subscribers 테이블 (사업 metric 의 기반)
        # 한 사용자 = 한 phone. plan 별 가격 정책 + lifecycle (started/churned) 추적.
        # MRR / ARPU / Margin / Churn 계산의 source of truth.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS subscribers (
                phone               TEXT PRIMARY KEY,
                plan_tier           TEXT NOT NULL DEFAULT 'beta',
                monthly_price_krw   INTEGER NOT NULL DEFAULT 0,
                name                TEXT,
                company             TEXT,
                started_at_ms       INTEGER NOT NULL,
                churned_at_ms       INTEGER,
                notes               TEXT,
                created_at_ms       INTEGER NOT NULL,
                updated_at_ms       INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_subscribers_active "
            "ON subscribers(churned_at_ms) WHERE churned_at_ms IS NULL"
        )
        con.commit()


def _now_ms() -> int:
    return int(time.time() * 1000)


@contextmanager
def db_conn():
    con = sqlite3.connect(DB_PATH)
    con.row_factory = sqlite3.Row
    try:
        yield con
        con.commit()
    finally:
        con.close()


def db_set_generating(phone: str, latest_msg: str, received_at_ms: int) -> None:
    now = _now_ms()
    with sqlite3.connect(DB_PATH) as con:
        con.execute(
            """
            INSERT INTO suggestions_cache
                (phone, status, based_on_message, based_on_received_at_ms,
                 generated_at_ms, suggestions_json, updated_at_ms)
            VALUES (?, 'generating', ?, ?, NULL, NULL, ?)
            ON CONFLICT(phone) DO UPDATE SET
                status='generating',
                based_on_message=excluded.based_on_message,
                based_on_received_at_ms=excluded.based_on_received_at_ms,
                generated_at_ms=NULL,
                suggestions_json=NULL,
                updated_at_ms=excluded.updated_at_ms
            """,
            (phone, latest_msg, received_at_ms, now),
        )
        con.commit()


def db_set_ready(phone: str, suggestions: list[str]) -> None:
    now = _now_ms()
    with sqlite3.connect(DB_PATH) as con:
        con.execute(
            """
            UPDATE suggestions_cache SET
                status='ready',
                suggestions_json=?,
                generated_at_ms=?,
                updated_at_ms=?
            WHERE phone=?
            """,
            (json.dumps(suggestions, ensure_ascii=False), now, now, phone),
        )
        con.commit()


def db_set_missing(phone: str) -> None:
    now = _now_ms()
    with sqlite3.connect(DB_PATH) as con:
        con.execute(
            "UPDATE suggestions_cache SET status='missing', updated_at_ms=? WHERE phone=?",
            (now, phone),
        )
        con.commit()


def db_get(phone: str) -> Optional[dict]:
    with sqlite3.connect(DB_PATH) as con:
        cur = con.execute(
            """
            SELECT status, based_on_message, based_on_received_at_ms,
                   generated_at_ms, suggestions_json
            FROM suggestions_cache
            WHERE phone=?
            """,
            (phone,),
        )
        row = cur.fetchone()
    if not row:
        return None
    status, msg, recv_at, gen_at, suggs_json = row
    return {
        "status": status,
        "basedOnMessage": msg,
        "basedOnReceivedAtMs": recv_at,
        "generatedAtMs": gen_at,
        "suggestions": json.loads(suggs_json) if suggs_json else None,
    }


# ============================================================================
# Rate limit + 사용량 기록
# ============================================================================
def check_rate_limit(phone: str) -> None:
    """일일 한도 초과면 HTTPException(429) 던짐."""
    one_day_ms = 24 * 60 * 60 * 1000
    cutoff = _now_ms() - one_day_ms
    with db_conn() as conn:
        total = conn.execute(
            "SELECT COUNT(*) FROM api_usage WHERE created_at_ms > ?",
            (cutoff,),
        ).fetchone()[0]
        if total >= DAILY_TOTAL_CALLS_LIMIT:
            raise HTTPException(429, "일일 호출 한도 초과")
        per_phone = conn.execute(
            "SELECT COUNT(*) FROM api_usage WHERE phone = ? AND created_at_ms > ?",
            (phone, cutoff),
        ).fetchone()[0]
        if per_phone >= PER_PHONE_DAILY_LIMIT:
            raise HTTPException(429, f"{phone} 일일 호출 한도 초과")


def _compute_cost_usd(
    input_tokens: int,
    cached_input_tokens: int,
    cache_creation_tokens: int,
    output_tokens: int,
) -> float:
    # cache_creation 은 정가 input 과 동일 가격
    return (
        ((input_tokens + cache_creation_tokens) / 1_000_000) * COST_INPUT_PER_M
        + (cached_input_tokens / 1_000_000) * COST_CACHED_INPUT_PER_M
        + (output_tokens / 1_000_000) * COST_OUTPUT_PER_M
    )


def log_usage(phone: str, endpoint: str, response: "anthropic.types.Message") -> None:
    usage = response.usage
    input_tokens = getattr(usage, "input_tokens", 0) or 0
    cached_input_tokens = getattr(usage, "cache_read_input_tokens", 0) or 0
    cache_creation = getattr(usage, "cache_creation_input_tokens", 0) or 0
    output_tokens = getattr(usage, "output_tokens", 0) or 0

    cost = _compute_cost_usd(
        input_tokens=input_tokens,
        cached_input_tokens=cached_input_tokens,
        cache_creation_tokens=cache_creation,
        output_tokens=output_tokens,
    )
    with db_conn() as conn:
        conn.execute(
            """
            INSERT INTO api_usage
                (phone, endpoint, input_tokens, cached_input_tokens,
                 output_tokens, cost_usd, created_at_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                phone,
                endpoint,
                input_tokens,
                cached_input_tokens,
                output_tokens,
                cost,
                _now_ms(),
            ),
        )


# ─── §12.3 모델별 단가표 (per 1M tokens, USD) ───
# - input: 비캐시 정가 입력
# - cache_read: 캐시 적중된 입력 (정가의 1/10)
# - cache_write: ephemeral 캐시 생성 시 입력 (정가의 1.25x — 5분 TTL)
# - output: 출력
# 출처: https://www.anthropic.com/pricing  (2026-05 기준)
#
# §12.3 보강 — Anthropic API 의 response.model 은 정식 ID (예: "claude-haiku-4-5-20251001")
# 로 오므로, 단축형 key 의 dict.get() 매칭은 fail 가능. prefix 매칭을 쓴다.
# dict 는 단축형 prefix 로 둠.
# 추가로 "kakao-local" (§13 의 비-LLM 카운트용) 도 단가 0 으로 등록 — log_llm_usage
# 가 kakao 호출도 카운트하되 비용은 0 으로 박히게.
#
MODEL_PRICING_USD_PER_M = {
    # Claude 계열 — prefix 매칭으로 정식 ID(...-YYYYMMDD) 도 같이 잡힘
    "claude-sonnet-4-6": {
        "input":       3.00,
        "cache_read":  0.30,
        "cache_write": 3.75,
        "output":     15.00,
    },
    "claude-opus-4-6": {
        "input":      15.00,
        "cache_read":  1.50,
        "cache_write": 18.75,
        "output":     75.00,
    },
    "claude-haiku-4-5": {
        "input":       1.00,
        "cache_read":  0.10,
        "cache_write": 1.25,
        "output":      5.00,
    },
    # §13 — 카카오 로컬 API (LLM 아니지만 endpoint 호출수 잡기 위해 0원으로 등록)
    "kakao-local": {
        "input":       0.0,
        "cache_read":  0.0,
        "cache_write": 0.0,
        "output":      0.0,
    },
    # §14 — Google Gemini 2.5 Flash (✨ 다듬기 endpoint)
    # 단가 출처: https://ai.google.dev/pricing (2026 기준, per 1M tokens, USD)
    # · input          $0.075 / 1M tokens
    # · cached input   $0.01875 / 1M tokens  (= 입력의 1/4)
    # · output         $0.30 / 1M tokens
    # cache_write 는 별도 가격표 없음 → input 단가와 동일 적용 (보수적).
    # prefix 매칭으로 "gemini-2.5-flash-001" 같은 정식 ID 도 같이 잡힘.
    "gemini-2.5-flash": {
        "input":       0.075,
        "cache_read":  0.01875,
        "cache_write": 0.075,
        "output":      0.30,
    },
}


def _resolve_pricing(model: str) -> dict:
    """§12.3 — model ID 를 단축형 prefix 로 매칭.

    Anthropic API 의 response.model 은 보통 정식 ID 형태 (예:
    "claude-haiku-4-5-20251001") 로 오기 때문에, 단축형 키 (예: "claude-haiku-4-5")
    로 박힌 dict 와 정확 일치(dict.get)로만 매칭하면 fail → cost_krw=0 박혀 모니터링
    무용해지는 위험이 있다. 그래서 key 길이 내림차순으로 prefix 매칭.

    매칭 실패 시 sonnet-4-6 단가로 over-estimate (안전).
    """
    if not model:
        return MODEL_PRICING_USD_PER_M["claude-sonnet-4-6"]
    for key in sorted(MODEL_PRICING_USD_PER_M.keys(), key=len, reverse=True):
        if model.startswith(key):
            return MODEL_PRICING_USD_PER_M[key]
    # 어떤 prefix 와도 매칭 안 되면 가장 보수적 (sonnet) 단가
    return MODEL_PRICING_USD_PER_M["claude-sonnet-4-6"]


def calculate_cost_krw(
    model: str,
    prompt_tokens: int,
    completion_tokens: int,
    cache_read_tokens: int = 0,
    cache_write_tokens: int = 0,
) -> float:
    """§12.3 — 모델별 단가 + ₩1380/USD 환율로 원화 비용 계산.

    Anthropic SDK 의 usage 객체는 input_tokens / cache_read_input_tokens /
    cache_creation_input_tokens / output_tokens 4개 카테고리를 따로 보고한다.
    prompt_tokens 는 그중 '비캐시 정가' 분량만 의미한다 (cache_read/write 제외).
    """
    pricing = _resolve_pricing(model)

    cost_usd = (
        (prompt_tokens      * pricing["input"]      / 1_000_000)
        + (cache_read_tokens  * pricing["cache_read"]  / 1_000_000)
        + (cache_write_tokens * pricing["cache_write"] / 1_000_000)
        + (completion_tokens  * pricing["output"]     / 1_000_000)
    )
    return round(cost_usd * KRW_PER_USD, 4)


def log_llm_usage(
    endpoint: str,
    model: str,
    prompt_tokens: int,
    completion_tokens: int,
    cache_read_tokens: int = 0,
    cache_write_tokens: int = 0,
) -> None:
    """§12.2 — 모든 Claude 호출 직후 한 줄 기록. /api/usage-stats 가 여기서 집계."""
    cost_krw = calculate_cost_krw(
        model=model,
        prompt_tokens=prompt_tokens,
        completion_tokens=completion_tokens,
        cache_read_tokens=cache_read_tokens,
        cache_write_tokens=cache_write_tokens,
    )
    with db_conn() as conn:
        conn.execute(
            """
            INSERT INTO llm_usage_log
                (timestamp_ms, endpoint, model,
                 prompt_tokens, completion_tokens,
                 cache_read_tokens, cache_write_tokens, cost_krw)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                _now_ms(),
                endpoint,
                model,
                prompt_tokens,
                completion_tokens,
                cache_read_tokens,
                cache_write_tokens,
                cost_krw,
            ),
        )


def _log_llm_usage_from_response(
    endpoint: str, response: "anthropic.types.Message"
) -> None:
    """Anthropic SDK 의 usage 객체에서 토큰 4종을 뽑아 log_llm_usage 호출.

    log_usage(api_usage 기록) 바로 뒤에 같이 부르면 됨.
    """
    usage = response.usage
    log_llm_usage(
        endpoint=endpoint,
        model=getattr(response, "model", None) or CLAUDE_MODEL,
        prompt_tokens=getattr(usage, "input_tokens", 0) or 0,
        completion_tokens=getattr(usage, "output_tokens", 0) or 0,
        cache_read_tokens=getattr(usage, "cache_read_input_tokens", 0) or 0,
        cache_write_tokens=getattr(usage, "cache_creation_input_tokens", 0) or 0,
    )


# ============================================================================
# 프롬프트
# ============================================================================
SYSTEM_PROMPT_TEMPLATE = """너는 줄눈 시공 사장님이 고객 문자에 답장할 때 도와주는 비서다.

────── 사장님 톤 학습 (반드시 모방할 것) ──────
다음은 사장님이 평소 고객에게 보낸 실제 메시지들이다.
어휘·문장 길이·반말/존댓말 비율·이모지 사용·인사 방식·문장 끝 처리를
모방해야 한다. 절대 사장님이 안 쓸 법한 단어/문체로 답하지 말 것.

__OWNER_TONE_SAMPLES__

────── 답변 후보 3개의 차별화 (반드시 다른 방향) ──────
세 후보는 명확히 다른 방향성을 가진다:

1번 = 짧은 답변
- 한 문장. 즉답. 사장님이 바쁠 때 그대로 보낼 수 있어야.
- 예: "내일 오전 10시 가능합니다."

2번 = 친절한 답변
- 두 문장. 추가 안내/배려 한 줄 더.
- 예: "내일 오전 10시 방문드리겠습니다. 시공 시 1시간 정도 비워두시면 좋아요."

3번 = 전환 유도 답변
- 두 문장. 다음 단계로 자연스럽게 유도 (사진 요청 / 일정 확정 / 견적 안내 / 입금 안내).
- 예: "내일 가능합니다. 정확한 견적을 위해 시공 부위 사진 한 장만 보내주실 수 있나요?"

세 후보가 비슷비슷하면 실패. 사장님이 상황 따라 골라 쓸 수 있도록 다양해야 함.

────── 가격표 ──────
__PRICING__

────── 기본 규칙 ──────
- 정확히 3개 답변을 JSON 으로 답하라.
- 고객에게 보낼 메시지이므로 존댓말.
- 이모지·따옴표를 임의로 새로 넣지 마라 (사장님 톤 샘플에 있으면 OK).
- 가격·날짜·시간은 대화 또는 가격표에서만 추출. 추측하지 마라.
- 금기어: "급하면" 계열, "싸다" 계열 → 변형 표현도 피하라.

가격 문의 케이스 처리:
- 신축/구축 미확정 → 한 후보는 "신축이세요 구축이세요?" 물어보기.
- 타일 크기 미언급 → 한 후보는 "타일 사진 보내주시면 정확히 견적 드려요" 안내.
- 답변 형식: 표 금지. 항목별 줄바꿈 나열 + 합계.
- 실리콘 제거/셀프줄눈/누수 가능성 = "현장 확인 후 추가될 수 있어요" 한 문장 덧붙임.

답 형식 — 반드시 지켜라:
- 응답 첫 글자는 무조건 '{' 로 시작. 인사·설명·코드블럭·백틱 절대 X.
- 정확히 하나의 JSON 객체만 반환. 그 외 텍스트(앞·뒤 어디든) 완전 금지.
- 형태: {"suggestions": ["답변1", "답변2", "답변3"]}
"""


def format_owner_tone(samples: list[str]) -> str:
    """톤 샘플 정제: 너무 짧은/긴 거 제외, 중복 제거, 최대 50건."""
    if not samples:
        return "(샘플 없음 — 기본 톤으로 답하되 군더더기 없이)"
    cleaned: list[str] = []
    seen: set[str] = set()
    for s in samples:
        s = (s or "").strip()
        if len(s) < 5 or len(s) > 300:
            continue
        if s in seen:
            continue
        seen.add(s)
        cleaned.append(s)
        if len(cleaned) >= 50:
            break
    if not cleaned:
        return "(샘플 없음 — 기본 톤으로 답하되 군더더기 없이)"
    return "\n".join(f"- {s}" for s in cleaned)


def build_system_prompt(owner_tone_samples: list[str]) -> str:
    return (
        SYSTEM_PROMPT_TEMPLATE
        .replace("__PRICING__", load_pricing())
        .replace("__OWNER_TONE_SAMPLES__", format_owner_tone(owner_tone_samples))
    )


def build_user_message(req: "PrepareReplyRequest") -> str:
    c = req.customer
    lines: list[str] = []
    lines.append("[고객 정보]")
    lines.append(f"이름: {c.name if (c and c.name) else '없음'}")
    lines.append(f"메모: {c.memo if (c and c.memo) else '없음'}")
    lines.append(f"리드 온도: {c.leadHeat if (c and c.leadHeat) else '없음'}")
    deposit = "받음" if (c and c.depositPaid) else "안 받음"
    lines.append(f"계약금 입금: {deposit}")
    lines.append("")
    lines.append("[최근 대화]")
    for turn in (req.recentHistory or []):
        lines.append(f"({turn.role}): {turn.body}")
    lines.append(f"(customer): {req.latestMessage}   ← 이게 방금 받은 메시지")
    lines.append("")
    lines.append("답변 후보 3개를 JSON 으로 생성하라.")
    return "\n".join(lines)


# ============================================================================
# 요청 스키마
# ============================================================================
class HistoryTurn(BaseModel):
    role: str  # "customer" | "owner"
    body: str
    timestampMs: int


class CustomerInfo(BaseModel):
    name: Optional[str] = None
    memo: Optional[str] = None
    leadHeat: Optional[str] = None
    depositPaid: Optional[bool] = False


class PrepareReplyRequest(BaseModel):
    phone: str
    latestMessage: str
    latestMessageReceivedAtMs: int
    recentHistory: list[HistoryTurn] = Field(default_factory=list)
    customer: Optional[CustomerInfo] = None
    # 새 필드 — 안드로이드 측에서 SmsRepository.querySentMessages(50) 결과를 보냄
    ownerToneSamples: list[str] = Field(default_factory=list)


# ─── P0+P1+P2: 공통 ConversationContext (사양서 §1) ───
# 세 endpoint 가 같은 입력 스키마를 공유한다 (snake_case — 사양서 §1 그대로).
class Message(BaseModel):
    role: str          # "owner" | "customer"
    body: str
    timestamp_ms: int


class CallSummary(BaseModel):
    summary: str
    duration_sec: int
    started_at_ms: int
    direction: str     # "incoming" | "outgoing" | "missed"


class ConversationContext(BaseModel):
    phone: str
    customer_name: Optional[str] = None
    customer_status: Optional[str] = None        # "신규 문의" / "견적 발송" 등
    customer_memo: Optional[str] = None
    lead_heat: Optional[str] = None              # "HOT" / "WARM" / "COLD" / null
    deposit_paid: bool = False
    scheduled_work_date: Optional[int] = None    # 시공 예약 epoch ms
    recent_messages: list[Message] = Field(default_factory=list)
    call_summaries: list[CallSummary] = Field(default_factory=list)
    owner_tone_samples: list[str] = Field(default_factory=list)


# ─── 응답 enum (사양서 §3, §4) ───
VALID_STAGES = {
    "inquiry", "photo_pending", "estimate_pending", "estimate_sent",
    "schedule_pending", "deposit_pending", "scheduled", "done", "as_needed",
}
VALID_ACTION_TYPES = {
    "send_estimate", "confirm_schedule", "request_deposit",
    "register_schedule", "send_followup", "none",
}
VALID_URGENCIES = {"high", "medium", "low", "none"}


# ============================================================================
# Claude 호출
# ============================================================================
def _parse_json_object(raw_text: str) -> dict:
    """Claude 응답에서 JSON 객체 한 개 추출. leading prose / 코드블럭 / trailing 처리.

    Sonnet 4.6 은 assistant prefill 미지원이라 응답에 prose 가 섞일 수 있음.
    """
    text = (raw_text or "").strip()
    if not text:
        raise ValueError("Empty response from Claude")

    # 코드블럭 한 번 벗기기 (```json ... ``` 또는 ``` ... ```)
    if text.startswith("```"):
        text = text.split("\n", 1)[1] if "\n" in text else text[3:]
        if text.endswith("```"):
            text = text[:-3].rstrip()

    # 첫 '{' 부터 시작
    brace_idx = text.find("{")
    if brace_idx == -1:
        raise ValueError(f"No JSON object found in: {text[:300]!r}")
    text = text[brace_idx:]

    try:
        parsed, _ = json.JSONDecoder().raw_decode(text)
    except json.JSONDecodeError as e:
        raise ValueError(f"Bad JSON from Claude: {text[:300]!r}") from e

    if not isinstance(parsed, dict):
        raise ValueError(f"Not a JSON object: {parsed!r}")
    return parsed


def _parse_suggestions(raw_text: str) -> list[str]:
    """Claude 응답에서 suggestions 리스트 추출. 정확히 3개로 보정."""
    parsed = _parse_json_object(raw_text)
    suggestions = parsed.get("suggestions")
    if not isinstance(suggestions, list) or len(suggestions) == 0:
        raise ValueError(f"Bad suggestions shape: {parsed!r}")

    suggs = [str(s).strip() for s in suggestions[:3]]
    while len(suggs) < 3:
        suggs.append("")
    return suggs


async def call_claude_for_suggestions_with_meta(
    req: PrepareReplyRequest,
) -> tuple[list[str], "anthropic.types.Message"]:
    """Claude Sonnet 4.6 호출 → (suggestions 3개, raw response) 반환."""
    system_text = build_system_prompt(req.ownerToneSamples or [])
    user_msg = build_user_message(req)

    response = await claude_client.messages.create(
        model=CLAUDE_MODEL,
        max_tokens=CLAUDE_MAX_TOKENS,
        timeout=CLAUDE_TIMEOUT,
        system=[
            {
                "type": "text",
                "text": system_text,
                # prompt caching — 동일 system prompt 5분 내 재호출 시 입력 비용 1/10
                "cache_control": {"type": "ephemeral"},
            }
        ],
        messages=[
            # Sonnet 4.6 은 assistant prefill 미지원. user 메시지로만 끝낸다.
            # JSON 강제는 시스템 프롬프트의 "응답 첫 글자는 무조건 {" 지시 + 파서가 책임.
            {"role": "user", "content": user_msg},
        ],
    )

    # response.content 는 ContentBlock 리스트. text 블록만 합친다.
    text_parts = [
        getattr(block, "text", "")
        for block in response.content
        if getattr(block, "type", None) == "text"
    ]
    raw_text = "".join(text_parts)
    suggestions = _parse_suggestions(raw_text)
    return suggestions, response


# ============================================================================
# 백그라운드 처리
# ============================================================================
async def generate_and_cache(req: PrepareReplyRequest) -> None:
    phone = req.phone
    try:
        # 1) rate limit (초과면 HTTPException → 아래에서 missing 처리)
        check_rate_limit(phone)
        # 2) Claude 호출
        suggestions, response = await call_claude_for_suggestions_with_meta(req)
        # 3) 사용량 기록 (성공한 호출만)
        log_usage(phone, "prepare-reply", response)
        _log_llm_usage_from_response("prepare-reply", response)  # §12.2
        # 4) 결과 캐싱
        db_set_ready(phone, suggestions)
        usage = response.usage
        print(
            f"[ready] {phone} → 3 suggestions "
            f"(in={getattr(usage,'input_tokens',0)} "
            f"cache_read={getattr(usage,'cache_read_input_tokens',0)} "
            f"out={getattr(usage,'output_tokens',0)})"
        )
    except asyncio.CancelledError:
        print(f"[cancelled] {phone}")
        raise
    except HTTPException as e:
        print(f"[rate-limit] {phone}: {e.detail}")
        db_set_missing(phone)
    except Exception as e:
        print(f"[failed] {phone}: {type(e).__name__}: {e}")
        db_set_missing(phone)
    finally:
        cur = _inflight_tasks.get(phone)
        if cur is not None and cur.done():
            _inflight_tasks.pop(phone, None)


# ============================================================================
# FastAPI
# ============================================================================
@asynccontextmanager
async def lifespan(app: FastAPI):
    db_init()
    load_pricing()
    print(f"[boot] DB at {DB_PATH}")
    print(f"[boot] pricing.md mtime = {_pricing_cache['mtime']}")
    print(f"[boot] Claude model = {CLAUDE_MODEL}")
    yield


app = FastAPI(title="RING-GO Server (Claude Sonnet 4.6)", lifespan=lifespan)


@app.post("/prepare-reply")
async def prepare_reply(req: PrepareReplyRequest):
    db_set_generating(req.phone, req.latestMessage, req.latestMessageReceivedAtMs)

    # 진행 중인 같은 phone 태스크가 있으면 취소
    old = _inflight_tasks.get(req.phone)
    if old is not None and not old.done():
        old.cancel()

    task = asyncio.create_task(generate_and_cache(req))
    _inflight_tasks[req.phone] = task
    return {"ok": True}


@app.get("/suggestions/{phone}")
async def get_suggestions(phone: str):
    row = db_get(phone)
    if row is None:
        return {"status": "missing"}

    status = row["status"]
    if status in ("generating", "missing"):
        return {"status": status}

    # ready
    return {
        "status": "ready",
        "phone": phone,
        "basedOnMessage": row["basedOnMessage"],
        "basedOnReceivedAtMs": row["basedOnReceivedAtMs"],
        "generatedAtMs": row["generatedAtMs"],
        "suggestions": row["suggestions"] or [],
    }


# ============================================================================
# §12.4 — GET /api/usage-stats?period=today|month|all
# ─────────────────────────────────────────────────────────────────────────────
# llm_usage_log 테이블에서 endpoint × model 별 호출수/토큰/원화 비용을 집계.
# period 기준은 KST(Asia/Seoul) 자정/1일.
# ============================================================================
_KST = _dt.timezone(_dt.timedelta(hours=9))


def _usage_stats_since_ms(period: str) -> int:
    """period → 집계 시작 시각 (epoch ms). 알 수 없는 값은 'all'(=0) 처리."""
    period = (period or "all").strip().lower()
    if period == "all":
        return 0
    now_kst = _dt.datetime.now(_KST)
    if period == "today":
        anchor = now_kst.replace(hour=0, minute=0, second=0, microsecond=0)
    elif period == "month":
        anchor = now_kst.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    else:
        return 0
    return int(anchor.timestamp() * 1000)


@app.get("/api/usage-stats")
async def usage_stats(period: str = "all") -> dict:
    """§12.4 — LLM 사용량/비용 모니터링.

    period:
      - "today": 오늘 KST 00:00 이후
      - "month": 이번 달 1일 KST 00:00 이후
      - "all":   전체 기간 (default)

    응답:
      {
        "period": "today",
        "since_ms": 1748352000000,
        "by_endpoint": {
          "prepare-reply": {"calls":..,"prompt_tokens":..,...,"cost_krw":..},
          ...
        },
        "by_model": {"claude-sonnet-4-6": {...}},
        "total":   {"calls":..,"prompt_tokens":..,...,"cost_krw":..}
      }
    """
    period = (period or "all").strip().lower()
    if period not in ("today", "month", "all"):
        period = "all"
    since_ms = _usage_stats_since_ms(period)

    with db_conn() as conn:
        # endpoint 별 집계
        ep_rows = conn.execute(
            """
            SELECT endpoint,
                   COUNT(*)                                AS calls,
                   COALESCE(SUM(prompt_tokens), 0)         AS prompt_tokens,
                   COALESCE(SUM(completion_tokens), 0)     AS completion_tokens,
                   COALESCE(SUM(cache_read_tokens), 0)     AS cache_read_tokens,
                   COALESCE(SUM(cache_write_tokens), 0)    AS cache_write_tokens,
                   COALESCE(SUM(cost_krw), 0)              AS cost_krw
            FROM llm_usage_log
            WHERE timestamp_ms >= ?
            GROUP BY endpoint
            ORDER BY calls DESC
            """,
            (since_ms,),
        ).fetchall()

        # model 별 집계 (혹시 하이브리드로 가게 됐을 때 유용)
        model_rows = conn.execute(
            """
            SELECT model,
                   COUNT(*)                                AS calls,
                   COALESCE(SUM(prompt_tokens), 0)         AS prompt_tokens,
                   COALESCE(SUM(completion_tokens), 0)     AS completion_tokens,
                   COALESCE(SUM(cache_read_tokens), 0)     AS cache_read_tokens,
                   COALESCE(SUM(cache_write_tokens), 0)    AS cache_write_tokens,
                   COALESCE(SUM(cost_krw), 0)              AS cost_krw
            FROM llm_usage_log
            WHERE timestamp_ms >= ?
            GROUP BY model
            ORDER BY calls DESC
            """,
            (since_ms,),
        ).fetchall()

        # grand total
        total_row = conn.execute(
            """
            SELECT COUNT(*)                                AS calls,
                   COALESCE(SUM(prompt_tokens), 0)         AS prompt_tokens,
                   COALESCE(SUM(completion_tokens), 0)     AS completion_tokens,
                   COALESCE(SUM(cache_read_tokens), 0)     AS cache_read_tokens,
                   COALESCE(SUM(cache_write_tokens), 0)    AS cache_write_tokens,
                   COALESCE(SUM(cost_krw), 0)              AS cost_krw
            FROM llm_usage_log
            WHERE timestamp_ms >= ?
            """,
            (since_ms,),
        ).fetchone()

        # endpoint × model 매트릭스 — 대시보드 "기능×모델" 카드 용
        ep_model_rows = conn.execute(
            """
            SELECT endpoint, model,
                   COUNT(*)                            AS calls,
                   COALESCE(SUM(cost_krw), 0)          AS cost_krw
            FROM llm_usage_log
            WHERE timestamp_ms >= ?
            GROUP BY endpoint, model
            """,
            (since_ms,),
        ).fetchall()

        # 일별 추이 (최근 7일) — 대시보드 mini bar chart 용
        # KST 자정 기준 day bucket
        seven_days_ago_ms = since_ms if period == "today" else max(
            since_ms, int((_dt.datetime.now(_KST) - _dt.timedelta(days=6))
                          .replace(hour=0, minute=0, second=0, microsecond=0).timestamp() * 1000)
        )
        daily_rows = conn.execute(
            """
            SELECT
                CAST((timestamp_ms + 9*3600*1000) / (24*3600*1000) AS INTEGER) AS day_kst,
                COUNT(*)                                                       AS calls,
                COALESCE(SUM(cost_krw), 0)                                     AS cost_krw
            FROM llm_usage_log
            WHERE timestamp_ms >= ?
            GROUP BY day_kst
            ORDER BY day_kst ASC
            """,
            (seven_days_ago_ms,),
        ).fetchall()

    def _row_to_dict(r) -> dict:
        return {
            "calls":               r["calls"],
            "prompt_tokens":       r["prompt_tokens"],
            "completion_tokens":   r["completion_tokens"],
            "cache_read_tokens":   r["cache_read_tokens"],
            "cache_write_tokens":  r["cache_write_tokens"],
            "cost_krw":            round(r["cost_krw"], 4),
        }

    # endpoint × model 매트릭스: { endpoint: { model: {calls, cost_krw} } }
    by_endpoint_model: dict = {}
    for r in ep_model_rows:
        by_endpoint_model.setdefault(r["endpoint"], {})[r["model"]] = {
            "calls":     r["calls"],
            "cost_krw":  round(r["cost_krw"], 4),
        }

    # 일별 추이: [{date: "YYYY-MM-DD", calls, cost_krw}, ...] KST 기준
    daily_trend = []
    for r in daily_rows:
        # day_kst = KST 자정부터의 일수
        day_ms = r["day_kst"] * 24 * 3600 * 1000 - 9 * 3600 * 1000
        date_str = _dt.datetime.fromtimestamp(day_ms / 1000, tz=_KST).strftime("%Y-%m-%d")
        daily_trend.append({
            "date":      date_str,
            "calls":     r["calls"],
            "cost_krw":  round(r["cost_krw"], 4),
        })

    return {
        "period":            period,
        "since_ms":          since_ms,
        "krw_per_usd":       KRW_PER_USD,
        "by_endpoint":       {r["endpoint"]: _row_to_dict(r) for r in ep_rows},
        "by_model":          {r["model"]:    _row_to_dict(r) for r in model_rows},
        "by_endpoint_model": by_endpoint_model,
        "daily_trend":       daily_trend,
        "total":             _row_to_dict(total_row),
    }


# ============================================================================
# /admin — HTML 대시보드
# ─────────────────────────────────────────────────────────────────────────────
# /admin/usage 와 /api/usage-stats 의 JSON 을 fetch 해서 사람이 보기 좋게 렌더.
# 폰(Tailnet) 에서도 보이도록 mobile-first.
# 외부 CDN 의존 X, 인라인 CSS+JS. 30초 자동 새로고침.
# ============================================================================
# ─── v2: 한글화 + 모델별 사용량 카드 ───
_ADMIN_DASHBOARD_HTML = r"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover" />
<meta name="theme-color" content="#1d2746" />
<title>RING-GO 사용량</title>
<!-- 폰 홈화면 추가용 아이콘 (iOS / Android) -->
<link rel="apple-touch-icon" sizes="180x180" href="/icon-180.png" />
<link rel="icon" type="image/png" sizes="192x192" href="/icon-192.png" />
<link rel="icon" type="image/png" sizes="512x512" href="/icon-512.png" />
<link rel="icon" type="image/svg+xml" href="/icon.svg" />
<link rel="manifest" href="/manifest.json" />
<meta name="apple-mobile-web-app-capable" content="yes" />
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
<meta name="apple-mobile-web-app-title" content="RING-GO" />
<meta name="mobile-web-app-capable" content="yes" />
<style>
  :root {
    --bg: #f5f5f7;
    --card: #ffffff;
    --text: #1d1d1f;
    --muted: #86868b;
    --accent: #0a84ff;
    --hot: #ff3b30;
    --warn: #ff9500;
    --ok: #34c759;
    --line: #e5e5ea;
    --shadow: 0 1px 3px rgba(0,0,0,0.04), 0 4px 12px rgba(0,0,0,0.06);
  }
  * { box-sizing: border-box; }
  html, body { margin:0; padding:0; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display",
                 "Pretendard", "Apple SD Gothic Neo", system-ui, sans-serif;
    background: var(--bg);
    color: var(--text);
    -webkit-font-smoothing: antialiased;
    padding: max(env(safe-area-inset-top), 16px) 16px 32px;
    max-width: 720px;
    margin: 0 auto;
  }
  header {
    display: flex; align-items: baseline; justify-content: space-between;
    padding: 8px 4px 16px; gap: 12px;
  }
  header h1 { font-size: 20px; margin: 0; font-weight: 700; letter-spacing: -0.3px; }
  header .meta { font-size: 12px; color: var(--muted); font-variant-numeric: tabular-nums; }
  .refresh {
    background: var(--accent); color: white; border: 0; border-radius: 999px;
    padding: 6px 14px; font-size: 13px; font-weight: 600; cursor: pointer;
    -webkit-tap-highlight-color: transparent;
  }
  .refresh:active { transform: scale(0.97); }

  .row { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; margin-bottom: 16px; }
  @media (max-width: 480px) { .row { grid-template-columns: 1fr 1fr; } .row > .all { grid-column: span 2; } }

  .hero {
    background: var(--card); border-radius: 14px; padding: 14px 14px 16px;
    box-shadow: var(--shadow);
  }
  .hero .label { font-size: 12px; color: var(--muted); font-weight: 600; letter-spacing: 0.2px; }
  .hero .price { font-size: 26px; font-weight: 700; margin-top: 4px; letter-spacing: -0.5px; font-variant-numeric: tabular-nums; }
  .hero .sub { font-size: 12px; color: var(--muted); margin-top: 2px; font-variant-numeric: tabular-nums; }

  .card {
    background: var(--card); border-radius: 14px; padding: 16px;
    box-shadow: var(--shadow); margin-bottom: 14px;
  }
  .card h2 { font-size: 13px; font-weight: 700; margin: 0 0 12px; color: var(--muted); letter-spacing: 0.3px; text-transform: uppercase; }

  /* 모델 카드 */
  .model-card {
    border: 1px solid var(--line); border-radius: 10px; padding: 12px 14px;
    display: grid; grid-template-columns: 1fr auto auto auto; gap: 12px; align-items: center;
  }
  @media (max-width: 480px) {
    .model-card { grid-template-columns: 1fr 1fr; row-gap: 8px; }
    .model-card .model-name { grid-column: span 2; }
  }
  .model-card + .model-card { margin-top: 8px; }
  .model-card.empty { background: #fafafc; color: var(--muted); }
  .model-card .model-name { font-size: 14px; font-weight: 700; }
  .model-card .model-tier { font-size: 11px; color: var(--muted); margin-top: 2px; font-weight: 500; }
  .model-card .stat { text-align: right; font-variant-numeric: tabular-nums; }
  .model-card .stat .v { font-size: 14px; font-weight: 700; }
  .model-card .stat .k { font-size: 10px; color: var(--muted); display: block; }

  table { width: 100%; border-collapse: collapse; font-variant-numeric: tabular-nums; }
  th, td { text-align: left; padding: 8px 0; font-size: 14px; vertical-align: middle; }
  th { font-weight: 600; color: var(--muted); border-bottom: 1px solid var(--line); font-size: 12px; }
  td { border-bottom: 1px solid var(--line); }
  tr:last-child td { border-bottom: 0; }
  td.num, th.num { text-align: right; }
  td.ep { font-weight: 600; }
  td.ep .ep-en { font-size: 10.5px; color: var(--muted); font-weight: 400; margin-top: 2px; }

  .bar { height: 6px; border-radius: 3px; background: var(--line); overflow: hidden; margin-top: 4px; }
  .bar > span { display: block; height: 100%; background: var(--accent); transition: width .3s; }
  .bar.warn > span { background: var(--warn); }
  .bar.hot  > span { background: var(--hot); }

  .kv { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; }
  .kv + .kv { border-top: 1px solid var(--line); }
  .kv .k { color: var(--muted); }
  .kv .v { font-weight: 600; font-variant-numeric: tabular-nums; }

  .err {
    background: #fee; color: var(--hot); border-radius: 10px; padding: 10px 12px;
    font-size: 13px; margin-bottom: 14px; display: none;
  }
  .footer { color: var(--muted); font-size: 11px; text-align: center; margin-top: 18px; line-height: 1.5; }
  .footer code { background: var(--line); padding: 1px 5px; border-radius: 4px; font-size: 10.5px; }
</style>
</head>
<body>
  <header>
    <div>
      <h1>🛠 RING-GO 사용량</h1>
      <div class="meta" id="meta">마지막 업데이트 -</div>
    </div>
    <button class="refresh" id="refreshBtn" onclick="loadAll()">↻ 새로고침</button>
  </header>

  <div class="err" id="err"></div>

  <!-- Admin token (사장님 전용 사업 metric 잠금 해제) -->
  <div class="card" id="adminLockCard" style="display:none;">
    <h2>🔒 관리자 모드</h2>
    <div style="font-size:13px;color:var(--muted);margin-bottom:10px;line-height:1.5;">
      💼 사업 건강도 / 👥 사용자 분석 / 📊 시간 heatmap 을 보려면 관리자 토큰 입력.<br/>
      토큰은 plist 의 <code>ADMIN_TOKEN</code> 값과 일치해야 함. 입력하면 이 브라우저에 저장됨.
    </div>
    <div style="display:flex;gap:8px;">
      <input id="adminTokenInput" type="password" placeholder="ADMIN_TOKEN 입력"
        style="flex:1;padding:8px 12px;border:1px solid var(--line);border-radius:8px;font-size:14px;" />
      <button class="refresh" onclick="saveAdminToken()">잠금 해제</button>
    </div>
  </div>

  <!-- 오늘 / 이번 달 / 전체 누적 비용 -->
  <div class="row">
    <div class="hero"><div class="label">오늘</div>
      <div class="price" id="todayCost">—</div><div class="sub" id="todayCalls">— 건</div></div>
    <div class="hero"><div class="label">이번 달</div>
      <div class="price" id="monthCost">—</div><div class="sub" id="monthCalls">— 건</div></div>
    <div class="hero all"><div class="label">전체 누적</div>
      <div class="price" id="allCost">—</div><div class="sub" id="allCalls">— 건</div></div>
  </div>

  <!-- 💼 사업 건강도 (관리자만) -->
  <div class="card admin-only" id="businessHealthCard" style="display:none;">
    <h2>💼 사업 건강도 (이번 달)</h2>
    <div class="row" style="grid-template-columns:1fr 1fr 1fr;">
      <div class="hero" style="padding:10px;">
        <div class="label">MRR</div>
        <div class="price" id="bizMrr" style="font-size:20px;">—</div>
        <div class="sub"><span id="bizActiveCount">—</span>명 활성</div>
      </div>
      <div class="hero" style="padding:10px;">
        <div class="label">COGS (LLM 비용)</div>
        <div class="price" id="bizCogs" style="font-size:20px;">—</div>
        <div class="sub" id="bizCallsMonth">— 건</div>
      </div>
      <div class="hero" style="padding:10px;">
        <div class="label">Gross Margin</div>
        <div class="price" id="bizMargin" style="font-size:20px;">—</div>
        <div class="sub" id="bizMarginStatus">—</div>
      </div>
    </div>
    <div style="margin-top:14px;">
      <div class="kv"><span class="k">ARPU (사용자당 매출)</span><span class="v" id="bizArpu">—</span></div>
      <div class="kv"><span class="k">Cost per user (사용자당 비용)</span><span class="v" id="bizCostPerUser">—</span></div>
      <div class="kv"><span class="k">Churned (해지)</span><span class="v" id="bizChurned">—</span></div>
    </div>
    <div style="margin-top:12px;font-size:11px;color:var(--muted);line-height:1.6;">
      💡 SaaS 건강 기준선: Gross Margin <b>80%+</b> = 좋음, 50-80% = 주의, 50% 미만 = 단가 인상 또는 모델 다운그레이드 검토.
    </div>
  </div>

  <!-- 👥 Top 사용자 (관리자만) -->
  <div class="card admin-only" id="topUsersCard" style="display:none;">
    <h2>👥 Top 사용자 (이번 달, 호출수 내림차순)</h2>
    <div style="overflow-x:auto;">
      <table id="topUsersTable" style="min-width:100%;font-size:12px;">
        <thead>
          <tr>
            <th style="text-align:left;">사용자</th>
            <th class="num">호출</th>
            <th class="num">비용</th>
            <th class="num">구독료</th>
            <th class="num">유저 마진</th>
          </tr>
        </thead>
        <tbody id="topUsersBody"><tr><td colspan="5" style="text-align:center;color:var(--muted);padding:12px 0">로딩 중…</td></tr></tbody>
      </table>
    </div>
    <div style="margin-top:8px;font-size:11px;color:var(--muted);line-height:1.5;">
      🔥 빨강 = heavy user (비용이 구독료의 50% 넘음 또는 미등록 사용자가 비용 ₩1k 초과). 정책 검토 필요.
    </div>
  </div>

  <!-- 📊 시간 × 요일 heatmap (관리자만) -->
  <div class="card admin-only" id="heatmapCard" style="display:none;">
    <h2>📊 사용 패턴 — 요일 × 시간대 (최근 7일, KST)</h2>
    <div style="overflow-x:auto;">
      <table id="heatmapTable" style="border-collapse:collapse;font-size:10px;width:100%;">
        <thead id="heatmapHead"></thead>
        <tbody id="heatmapBody"></tbody>
      </table>
    </div>
    <div style="margin-top:8px;font-size:11px;color:var(--muted);line-height:1.5;">
      셀 색이 진할수록 호출 많음. 사장님들이 언제 가장 많이 쓰나 = 인프라 scale 결정에 핵심.
    </div>
  </div>

  <!-- 모델별 사용량 (이번 달) -->
  <div class="card">
    <h2>🤖 모델별 사용량 (이번 달)</h2>
    <div id="modelCards">
      <div style="text-align:center;color:var(--muted);padding:12px 0">로딩 중…</div>
    </div>
    <div style="margin-top:12px;font-size:11px;color:var(--muted);line-height:1.6;">
      💡 단가 안내 (1만 토큰 ≈ 한글 약 7천 자):<br/>
      · <b>소넷 4.6 (고급, Claude)</b> — 입력 ₩41 / 캐시 적중 ₩4 / 출력 ₩207 (1만 토큰)<br/>
      · <b>하이쿠 4.5 (경량, Claude)</b> — 입력 ₩14 / 캐시 적중 ₩1.4 / 출력 ₩69 (소넷의 1/3)<br/>
      · <b>제미나이 2.5 Flash (Google)</b> — 입력 ₩1.0 / 캐시 적중 ₩0.26 / 출력 ₩4.1 (소넷의 1/40, ✨ 다듬기용)<br/>
      · <b>카카오 로컬</b> — 무료 (호출 카운트만 잡힘, 📍 주소 resolve)
    </div>
  </div>

  <!-- 기능별 사용량 (이번 달) -->
  <div class="card">
    <h2>📊 기능별 사용량 (이번 달)</h2>
    <table id="epTable">
      <thead><tr><th>기능</th><th class="num">호출</th><th class="num">비용 (₩)</th></tr></thead>
      <tbody id="epBody"><tr><td colspan="3" style="text-align:center;color:var(--muted);padding:12px 0">로딩 중…</td></tr></tbody>
    </table>
  </div>

  <!-- 기능 × 모델 매트릭스 (이번 달) — 어느 기능이 어느 모델 쓰나 한눈에 -->
  <div class="card">
    <h2>🧩 기능 × 모델 매트릭스 (이번 달)</h2>
    <div style="overflow-x:auto;">
      <table id="matrixTable" style="min-width:100%;font-size:12px;">
        <thead id="matrixHead"></thead>
        <tbody id="matrixBody"><tr><td style="text-align:center;color:var(--muted);padding:12px 0">로딩 중…</td></tr></tbody>
      </table>
    </div>
    <div style="margin-top:8px;font-size:11px;color:var(--muted);">
      셀 = 호출수 (회색은 0건). 같은 기능이 여러 모델 쓰면 hybrid 운영 중.
    </div>
  </div>

  <!-- 최근 7일 추이 + 월말 예상 -->
  <div class="card">
    <h2>📈 최근 7일 추이 + 월말 예상</h2>
    <div class="kv"><span class="k">이번 달 누적</span>
      <span class="v"><span id="monthCostNow">—</span> · <span id="monthCallsNow">— 건</span></span></div>
    <div class="kv"><span class="k">현재 페이스 기준 월말 예상</span>
      <span class="v" id="monthForecast">—</span></div>
    <div style="margin-top:10px;font-size:11px;color:var(--muted);margin-bottom:4px;">최근 7일 일별 비용 (KST)</div>
    <svg id="trendChart" viewBox="0 0 700 100" style="width:100%;height:auto;" preserveAspectRatio="none"></svg>
    <div id="trendLabels" style="display:flex;justify-content:space-between;font-size:10px;color:var(--muted);margin-top:2px;"></div>
  </div>

  <!-- Rate limit / 캐시 -->
  <div class="card">
    <h2>⚙️ 시스템 상태 (24시간 기준)</h2>
    <div class="kv"><span class="k">최근 24시간 전체 호출</span>
      <span class="v"><span id="rl24">—</span> / <span id="rlMax">—</span></span></div>
    <div class="bar" id="rlBar"><span style="width:0%"></span></div>
    <div class="kv"><span class="k">요약 캐시 행수 (호출 절약량 가늠)</span><span class="v" id="cacheRows">—</span></div>
    <div class="kv"><span class="k">한 번호당 일일 한도</span><span class="v" id="perPhone">—</span></div>
  </div>

  <div class="footer">
    JSON 원본: <code>/admin/usage</code> · <code>/api/usage-stats?period=today</code><br/>
    30초마다 자동 새로고침
  </div>

<script>
// ─── 영어 → 한글 매핑 ───
const EP_NAMES_KO = {
  'prepare-reply':         '답장 추천 (3개 후보)',
  'card-summary':          '카드 한 줄 요약',
  'conversation-summary':  '대화 상세 요약',
  'next-action-suggest':   '다음 액션 제안',
  'refine':                '✨ 다듬기',
  'address-resolve':       '📍 주소 resolve',
  'intent-classify':       '의도 분류',
  'style-profile-learn':   '말투 학습',
  'reply-suggest':         '답변 추천 (보조)',
};

// prefix 매칭용 — model ID prefix 별 한글명 + 설명
const MODEL_NAMES_KO = {
  'claude-sonnet-4-6':            { name: '소넷 4.6',         tier: '고급 — 정확도 우선 (Claude)' },
  'claude-opus-4-6':              { name: '오푸스 4.6',        tier: '최고급 — 매우 어려운 작업용 (Claude)' },
  'claude-haiku-4-5':             { name: '하이쿠 4.5',        tier: '경량 — 단순 작업 + 비용 1/3 (Claude)' },
  'gemini-2.5-flash':             { name: '제미나이 2.5 Flash', tier: '경량/저가 — ✨ 다듬기 전용 (Google, Sonnet 의 1/40 비용)' },
  'kakao-local':                  { name: '카카오 로컬',        tier: '비-LLM — 📍 주소 resolve (무료, 호출 카운트만 잡음)' },
};

// model id 가 정식 ID (예: claude-haiku-4-5-20251001) 면 prefix 매칭
function resolveModelMeta(modelId) {
  if (!modelId) return { name: 'unknown', tier: '' };
  const keys = Object.keys(MODEL_NAMES_KO).sort((a, b) => b.length - a.length);
  for (const key of keys) {
    if (modelId.startsWith(key)) return MODEL_NAMES_KO[key];
  }
  return { name: modelId, tier: '(미등록 모델)' };
}

const fmt    = n => (n == null ? '—' : new Intl.NumberFormat('ko-KR').format(Math.round(n)));
const fmtKRW = n => '₩' + fmt(n);
const fmtKRW2 = n => {  // 소수점 둘째 자리까지 (한 건당 평균 단가용)
  if (n == null) return '—';
  if (n >= 10) return '₩' + fmt(n);
  return '₩' + Number(n).toFixed(2);
};

async function fetchJSON(url, opts) {
  const r = await fetch(url, Object.assign({ cache: 'no-store' }, opts || {}));
  if (!r.ok) throw new Error(url + ' → HTTP ' + r.status);
  return r.json();
}

// ─── Admin token (localStorage) ───
function getAdminToken() {
  try { return localStorage.getItem('ringgo_admin_token') || ''; } catch (e) { return ''; }
}
function saveAdminToken() {
  const v = document.getElementById('adminTokenInput').value.trim();
  try { localStorage.setItem('ringgo_admin_token', v); } catch (e) {}
  loadAll();
}

// ─── Heatmap 색 강도 (호출수 → 0~1 → CSS color) ───
const DOW_KO = ['일','월','화','수','목','금','토'];
function heatmapCellColor(value, maxValue) {
  if (!value || maxValue === 0) return '#fafafc';
  const ratio = Math.min(1, value / maxValue);
  // 흰색 → 파란색 그라데이션
  const r = Math.round(255 - ratio * (255 - 10));
  const g = Math.round(255 - ratio * (255 - 132));
  const b = Math.round(255 - ratio * (255 - 255));
  return `rgb(${r},${g},${b})`;
}

async function loadAll() {
  const btn = document.getElementById('refreshBtn');
  btn.disabled = true;
  document.getElementById('err').style.display = 'none';
  try {
    const [today, month, allp, ad] = await Promise.all([
      fetchJSON('/api/usage-stats?period=today'),
      fetchJSON('/api/usage-stats?period=month'),
      fetchJSON('/api/usage-stats?period=all'),
      fetchJSON('/admin/usage'),
    ]);

    // ─── 오늘/이번달/전체 hero card ───
    document.getElementById('todayCost').textContent  = fmtKRW(today.total.cost_krw);
    document.getElementById('todayCalls').textContent = fmt(today.total.calls) + ' 건';
    document.getElementById('monthCost').textContent  = fmtKRW(month.total.cost_krw);
    document.getElementById('monthCalls').textContent = fmt(month.total.calls) + ' 건';
    document.getElementById('allCost').textContent    = fmtKRW(allp.total.cost_krw);
    document.getElementById('allCalls').textContent   = fmt(allp.total.calls) + ' 건';

    // ─── 모델별 사용량 카드 ───
    // "사용 중" 판단 — 이번 달 호출수 > 0 인 모델은 모두 사용 중 (multi-model 지원)
    // configured (사양상 default) 모델만 표시했던 옛 로직을 호출수 기반으로 변경.
    const usedModels = Object.entries(month.by_model);  // [ [id, stats], ... ]
    const modelStats = {};
    usedModels.forEach(([id, s]) => modelStats[id] = s);

    // 표시할 모델 — 사용된 모델 + (자리 마련용) hybrid 후보들
    const shownModelIds = new Set(usedModels.map(([id]) => id));
    shownModelIds.add('claude-sonnet-4-6');           // 기본 LLM
    shownModelIds.add('gemini-2.5-flash');            // ✨ 다듬기
    shownModelIds.add('claude-haiku-4-5');            // 미래 hybrid 후보
    shownModelIds.add('kakao-local');                 // 📍 주소 resolve

    const sortedIds = [...shownModelIds].sort((a, b) => {
      const ca = modelStats[a]?.cost_krw || 0;
      const cb = modelStats[b]?.cost_krw || 0;
      if (cb !== ca) return cb - ca;
      // 비용 같으면 호출수 많은 순
      return (modelStats[b]?.calls || 0) - (modelStats[a]?.calls || 0);
    });

    const cards = sortedIds.map(id => {
      const meta = resolveModelMeta(id);
      const s = modelStats[id];
      const calls = s ? s.calls : 0;
      const cost  = s ? s.cost_krw : 0;
      const empty = !s || calls === 0;
      const avg   = (s && s.calls > 0) ? (s.cost_krw / s.calls) : 0;
      // "사용 중" badge — 이번 달 1건이라도 호출 있으면
      const badge = empty
        ? ' (현재 사용 안 함)'
        : ' · <span style="color:var(--accent)">사용 중</span>';
      return `
        <div class="model-card ${empty ? 'empty' : ''}">
          <div class="model-name">
            ${meta.name}${badge}
            <div class="model-tier">${meta.tier}</div>
          </div>
          <div class="stat"><span class="v">${fmt(calls)}</span><span class="k">호출</span></div>
          <div class="stat"><span class="v">${fmtKRW(cost)}</span><span class="k">총 비용</span></div>
          <div class="stat"><span class="v">${empty ? '—' : fmtKRW2(avg)}</span><span class="k">건당 평균</span></div>
        </div>
      `;
    }).join('');
    document.getElementById('modelCards').innerHTML = cards;

    // ─── 기능별 사용량 표 (한글명 + 영어 보조) ───
    const eps = Object.entries(month.by_endpoint).sort((a,b) => b[1].cost_krw - a[1].cost_krw);
    const body = document.getElementById('epBody');
    if (eps.length === 0) {
      body.innerHTML = '<tr><td colspan="3" style="text-align:center;color:var(--muted);padding:12px 0">이번 달 호출 없음</td></tr>';
    } else {
      body.innerHTML = eps.map(([name, s]) => {
        const nameKo = EP_NAMES_KO[name] || name;
        return `
          <tr>
            <td class="ep">${nameKo}<div class="ep-en">${name}</div></td>
            <td class="num">${fmt(s.calls)}</td>
            <td class="num">${fmtKRW(s.cost_krw)}</td>
          </tr>
        `;
      }).join('');
    }

    // ─── 기능 × 모델 매트릭스 ───
    const matrixData = month.by_endpoint_model || {};
    // 행 = endpoint (호출수 내림차순), 열 = model (호출수 내림차순)
    const matrixEps = Object.keys(matrixData).sort((a, b) => {
      const aSum = Object.values(matrixData[a]).reduce((acc, v) => acc + v.calls, 0);
      const bSum = Object.values(matrixData[b]).reduce((acc, v) => acc + v.calls, 0);
      return bSum - aSum;
    });
    const matrixModels = [...new Set(
      matrixEps.flatMap(ep => Object.keys(matrixData[ep]))
    )].sort((a, b) => {
      const aSum = matrixEps.reduce((acc, ep) => acc + (matrixData[ep][a]?.calls || 0), 0);
      const bSum = matrixEps.reduce((acc, ep) => acc + (matrixData[ep][b]?.calls || 0), 0);
      return bSum - aSum;
    });

    const matrixHead = document.getElementById('matrixHead');
    const matrixBody = document.getElementById('matrixBody');
    if (matrixEps.length === 0 || matrixModels.length === 0) {
      matrixHead.innerHTML = '';
      matrixBody.innerHTML = '<tr><td style="text-align:center;color:var(--muted);padding:12px 0">이번 달 호출 없음</td></tr>';
    } else {
      // 헤더 row
      matrixHead.innerHTML = `
        <tr>
          <th style="text-align:left;">기능 \\ 모델</th>
          ${matrixModels.map(m => {
            const meta = resolveModelMeta(m);
            return `<th class="num" style="font-size:11px;padding:4px 6px;">${meta.name}</th>`;
          }).join('')}
        </tr>
      `;
      // body rows
      matrixBody.innerHTML = matrixEps.map(ep => {
        const epKo = EP_NAMES_KO[ep] || ep;
        return `
          <tr>
            <td class="ep" style="padding:6px 4px;">${epKo}</td>
            ${matrixModels.map(m => {
              const cell = matrixData[ep][m];
              const calls = cell ? cell.calls : 0;
              const styleEmpty = calls === 0 ? 'color:var(--line);' : '';
              return `<td class="num" style="padding:6px 6px;${styleEmpty}">${fmt(calls)}</td>`;
            }).join('')}
          </tr>
        `;
      }).join('');
    }

    // ─── 월말 예상 + 일별 추이 ───
    document.getElementById('monthCostNow').textContent  = fmtKRW(month.total.cost_krw);
    document.getElementById('monthCallsNow').textContent = fmt(month.total.calls) + ' 건';

    // 월말 예상 = 이번 달 누적 / 경과 일수 * 이번 달 총 일수
    const nowDate = new Date();
    const monthStart = new Date(nowDate.getFullYear(), nowDate.getMonth(), 1);
    const monthEnd = new Date(nowDate.getFullYear(), nowDate.getMonth() + 1, 0);
    const daysElapsed = Math.max(1, Math.ceil((nowDate - monthStart) / (24 * 3600 * 1000)));
    const daysInMonth = monthEnd.getDate();
    const projectedCost = month.total.cost_krw / daysElapsed * daysInMonth;
    document.getElementById('monthForecast').textContent =
      fmtKRW(projectedCost) + ' (경과 ' + daysElapsed + '/' + daysInMonth + '일)';

    // 일별 추이 SVG bar chart
    const trend = today.daily_trend || month.daily_trend || [];
    const chart = document.getElementById('trendChart');
    const labels = document.getElementById('trendLabels');
    if (trend.length === 0) {
      chart.innerHTML = '<text x="350" y="50" text-anchor="middle" font-size="12" fill="#86868b">데이터 없음</text>';
      labels.innerHTML = '';
    } else {
      const maxCost = Math.max(...trend.map(d => d.cost_krw), 1);
      const barWidth = 700 / Math.max(7, trend.length);
      const barGap = barWidth * 0.2;
      chart.innerHTML = trend.map((d, i) => {
        const h = (d.cost_krw / maxCost) * 80;
        const x = i * barWidth + barGap / 2;
        const y = 90 - h;
        return `
          <rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${(barWidth - barGap).toFixed(1)}" height="${h.toFixed(1)}" fill="var(--accent)" rx="2"/>
          <text x="${(x + (barWidth - barGap) / 2).toFixed(1)}" y="${(y - 3).toFixed(1)}" text-anchor="middle" font-size="9" fill="#1d1d1f">₩${Math.round(d.cost_krw)}</text>
        `;
      }).join('');
      labels.innerHTML = trend.map(d => {
        const dd = d.date.slice(5).replace('-', '/');  // "05/28"
        return `<span style="flex:1;text-align:center;">${dd}</span>`;
      }).join('');
    }

    // ─── 시스템 카드 ───
    document.getElementById('rl24').textContent      = fmt(ad.calls);
    document.getElementById('rlMax').textContent     = fmt(ad.dailyTotalLimit);
    document.getElementById('perPhone').textContent  = fmt(ad.perPhoneDailyLimit) + ' 건/일';
    document.getElementById('cacheRows').textContent = fmt(ad.summaryCacheRows);

    const pct = ad.dailyTotalLimit > 0 ? (ad.calls / ad.dailyTotalLimit) * 100 : 0;
    const bar = document.getElementById('rlBar');
    bar.querySelector('span').style.width = Math.min(100, pct).toFixed(1) + '%';
    bar.classList.toggle('warn', pct >= 60 && pct < 85);
    bar.classList.toggle('hot',  pct >= 85);

    const now = new Date();
    document.getElementById('meta').textContent = '마지막 업데이트 ' +
      now.toLocaleString('ko-KR', { hour12: false });

    // ─── Admin section (사장님 전용) ───
    await loadAdminSection();
  } catch (e) {
    const err = document.getElementById('err');
    err.textContent = '데이터 로딩 실패: ' + e.message;
    err.style.display = 'block';
  } finally {
    btn.disabled = false;
  }
}

// ─── Admin section 로딩 (사업 건강도 + Top users + heatmap) ───
async function loadAdminSection() {
  const token = getAdminToken();
  const lockCard = document.getElementById('adminLockCard');
  const adminCards = document.querySelectorAll('.admin-only');

  if (!token) {
    // 토큰 없으면 lock card 보이고 admin-only 숨김
    lockCard.style.display = 'block';
    adminCards.forEach(c => c.style.display = 'none');
    return;
  }

  // 토큰으로 business-stats 호출 시도
  let stats;
  try {
    stats = await fetchJSON('/api/admin/business-stats', {
      headers: { 'X-Admin-Token': token },
    });
  } catch (e) {
    // 토큰 잘못됨 또는 ADMIN_TOKEN 미설정
    lockCard.style.display = 'block';
    adminCards.forEach(c => c.style.display = 'none');
    const ti = document.getElementById('adminTokenInput');
    if (ti) ti.placeholder = '토큰 잘못됨 — 다시 입력';
    return;
  }

  // 인증 성공 → admin 카드들 보이게
  lockCard.style.display = 'none';
  adminCards.forEach(c => c.style.display = 'block');

  // 💼 사업 건강도
  document.getElementById('bizMrr').textContent = fmtKRW(stats.mrr_krw);
  document.getElementById('bizActiveCount').textContent = fmt(stats.active_subscribers);
  document.getElementById('bizCogs').textContent = fmtKRW(stats.month_cogs_krw);
  document.getElementById('bizCallsMonth').textContent = fmt(stats.month_calls) + ' 건';
  document.getElementById('bizChurned').textContent = fmt(stats.churned_subscribers) + ' 명';
  document.getElementById('bizArpu').textContent = fmtKRW(stats.arpu_krw);
  document.getElementById('bizCostPerUser').textContent = fmtKRW2(stats.cost_per_user_krw);

  const marginEl = document.getElementById('bizMargin');
  const marginStatus = document.getElementById('bizMarginStatus');
  if (stats.gross_margin_pct == null) {
    marginEl.textContent = '—';
    marginStatus.textContent = '활성 사용자 없음';
  } else {
    marginEl.textContent = stats.gross_margin_pct.toFixed(1) + '%';
    if (stats.gross_margin_pct >= 80) {
      marginEl.style.color = 'var(--ok)';
      marginStatus.textContent = '✓ 건강 (80%+)';
    } else if (stats.gross_margin_pct >= 50) {
      marginEl.style.color = 'var(--warn)';
      marginStatus.textContent = '⚠ 주의 (50-80%)';
    } else {
      marginEl.style.color = 'var(--hot)';
      marginStatus.textContent = '🔥 적자 위험 (< 50%)';
    }
  }

  // 👥 Top 사용자
  const topBody = document.getElementById('topUsersBody');
  if (!stats.top_users || stats.top_users.length === 0) {
    topBody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--muted);padding:12px 0">이번 달 사용 기록 없음</td></tr>';
  } else {
    topBody.innerHTML = stats.top_users.map(u => {
      const heavy = u.heavy_user;
      const phoneDisp = u.name ? `${u.name}<div style="font-size:10px;color:var(--muted);">${u.phone}</div>` : u.phone;
      const marginDisp = u.user_margin_pct == null
        ? '<span style="color:var(--muted);">—</span>'
        : (u.user_margin_pct >= 50
            ? `<span style="color:var(--ok);">${u.user_margin_pct}%</span>`
            : `<span style="color:var(--hot);">${u.user_margin_pct}%</span>`);
      const rowStyle = heavy ? 'background:rgba(255,59,48,0.06);' : '';
      return `
        <tr style="${rowStyle}">
          <td class="ep" style="padding:6px 4px;">${heavy ? '🔥 ' : ''}${phoneDisp}<div style="font-size:10px;color:var(--muted);">${u.plan_tier}</div></td>
          <td class="num">${fmt(u.calls)}</td>
          <td class="num">${fmtKRW2(u.cost_krw)}</td>
          <td class="num">${u.monthly_price_krw > 0 ? fmtKRW(u.monthly_price_krw) : '—'}</td>
          <td class="num">${marginDisp}</td>
        </tr>
      `;
    }).join('');
  }

  // 📊 시간 × 요일 heatmap
  const heatmap = stats.heatmap || [];
  const maxValue = Math.max(...heatmap.flat(), 1);
  const head = document.getElementById('heatmapHead');
  const body = document.getElementById('heatmapBody');
  head.innerHTML = `
    <tr>
      <th style="padding:4px;text-align:left;width:32px;"></th>
      ${Array.from({length: 24}, (_, h) =>
        `<th style="padding:2px 1px;font-size:9px;color:var(--muted);font-weight:400;text-align:center;">${h}</th>`
      ).join('')}
    </tr>
  `;
  body.innerHTML = heatmap.map((row, dow) => `
    <tr>
      <td style="padding:2px 4px;font-weight:600;color:${dow===0||dow===6?'var(--hot)':'var(--text)'};">${DOW_KO[dow]}</td>
      ${row.map((v, h) => `
        <td style="padding:0;border:1px solid var(--bg);">
          <div title="${DOW_KO[dow]} ${h}시: ${v}건"
               style="width:100%;aspect-ratio:1;background:${heatmapCellColor(v, maxValue)};display:flex;align-items:center;justify-content:center;font-size:8px;color:#444;min-height:14px;">
            ${v > 0 && v >= maxValue * 0.5 ? v : ''}
          </div>
        </td>
      `).join('')}
    </tr>
  `).join('');
}

loadAll();
setInterval(loadAll, 30 * 1000);  // 30초 자동 새로고침
</script>
</body>
</html>
"""


@app.get("/admin", response_class=HTMLResponse)
async def admin_dashboard() -> HTMLResponse:
    """사람이 보기 좋은 사용량 대시보드.

    데이터는 /api/usage-stats 와 /admin/usage 의 JSON 을 fetch 해서 렌더.
    Tailnet 안의 폰 브라우저에서도 잘 보이도록 mobile-first.
    """
    return HTMLResponse(content=_ADMIN_DASHBOARD_HTML)


# ============================================================================
# 앱 아이콘 + manifest (폰 홈화면 추가용 PWA-ish 메타)
# ─────────────────────────────────────────────────────────────────────────────
# - apple-touch-icon (iOS): /icon-180.png
# - Android maskable: /icon-192.png, /icon-512.png
# - SVG (모던 브라우저 fallback): /icon.svg
# - manifest.json: Android PWA 친화 메타
# ============================================================================

_RINGGO_ICON_SVG = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <rect width="512" height="512" rx="112" fill="#1d2746"/>
  <circle cx="256" cy="256" r="180" fill="none" stroke="#ffffff" stroke-width="26"/>
  <circle cx="192" cy="256" r="17" fill="#ffffff"/>
  <circle cx="256" cy="256" r="17" fill="#ffffff"/>
  <circle cx="320" cy="256" r="17" fill="#ffffff"/>
</svg>"""


def _icon_file(filename: str) -> FileResponse:
    """static/ 폴더의 PNG 반환. 파일 없으면 404."""
    path = BASE_DIR / "static" / filename
    if not path.exists():
        raise HTTPException(404, f"icon {filename} not found")
    return FileResponse(
        path,
        media_type="image/png",
        headers={"Cache-Control": "public, max-age=86400"},  # 1일 캐시
    )


@app.get("/icon.svg")
async def icon_svg() -> Response:
    return Response(
        content=_RINGGO_ICON_SVG,
        media_type="image/svg+xml",
        headers={"Cache-Control": "public, max-age=86400"},
    )


@app.get("/icon-180.png")
async def icon_180() -> FileResponse:
    return _icon_file("icon-180.png")


@app.get("/icon-192.png")
async def icon_192() -> FileResponse:
    return _icon_file("icon-192.png")


@app.get("/icon-512.png")
async def icon_512() -> FileResponse:
    return _icon_file("icon-512.png")


@app.get("/manifest.json")
async def manifest() -> dict:
    """Android Chrome 의 PWA manifest. iOS 는 거의 무시하지만 표준이라 박아둠."""
    return {
        "name":             "RING-GO 사용량",
        "short_name":       "RING-GO",
        "description":      "줄눈/타일 시공 사장님의 SMS 답장 추천 AI 사용량 대시보드",
        "start_url":        "/admin",
        "display":          "standalone",
        "orientation":      "portrait",
        "background_color": "#f5f5f7",
        "theme_color":      "#1d2746",
        "icons": [
            {"src": "/icon-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any maskable"},
            {"src": "/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any maskable"},
        ],
    }


# ============================================================================
# §15 — Admin endpoints (사업 metric: MRR, COGS, Margin, Top users, heatmap)
# ─────────────────────────────────────────────────────────────────────────────
# 인증: X-Admin-Token 헤더 = ADMIN_TOKEN 환경변수 일치 필요.
# ADMIN_TOKEN 미설정 시 endpoint 는 503 — 사장님이 plist 에 박아야 활성화.
# ============================================================================
from fastapi import Header  # noqa: E402  (다른 import 와 분리해도 OK)


def _admin_auth(x_admin_token: Optional[str]) -> None:
    """X-Admin-Token 헤더 검증. 실패 시 HTTPException."""
    if not ADMIN_TOKEN:
        raise HTTPException(
            503,
            "ADMIN_TOKEN 미설정. plist EnvironmentVariables 에 박아주세요."
        )
    if not x_admin_token or x_admin_token != ADMIN_TOKEN:
        raise HTTPException(401, "invalid admin token")


class SubscriberUpsertRequest(BaseModel):
    phone: str
    plan_tier: str = "beta"      # "founder" | "beta" | "pro" | "enterprise"
    monthly_price_krw: int = 0
    name: Optional[str] = None
    company: Optional[str] = None
    notes: Optional[str] = None
    churned: bool = False        # True 면 churned_at_ms 박음 (해지 처리)


VALID_PLAN_TIERS = {"founder", "beta", "pro", "enterprise"}


@app.post("/api/admin/subscribers/upsert")
async def admin_upsert_subscriber(
    req: SubscriberUpsertRequest,
    x_admin_token: Optional[str] = Header(None),
) -> dict:
    """사용자 등록/수정. 같은 phone 이면 update, 처음이면 insert.

    churned=True 면 해지 처리 (churned_at_ms 박음). 다시 활성화 하려면 churned=False 로 호출.
    """
    _admin_auth(x_admin_token)
    if req.plan_tier not in VALID_PLAN_TIERS:
        raise HTTPException(400, f"plan_tier must be one of {VALID_PLAN_TIERS}")
    if req.monthly_price_krw < 0:
        raise HTTPException(400, "monthly_price_krw must be >= 0")

    now = _now_ms()
    with db_conn() as conn:
        existing = conn.execute(
            "SELECT phone, churned_at_ms FROM subscribers WHERE phone=?",
            (req.phone,),
        ).fetchone()

        if existing:
            churned_at_ms = now if req.churned else None
            conn.execute(
                """
                UPDATE subscribers SET
                    plan_tier         = ?,
                    monthly_price_krw = ?,
                    name              = ?,
                    company           = ?,
                    notes             = ?,
                    churned_at_ms     = ?,
                    updated_at_ms     = ?
                WHERE phone = ?
                """,
                (
                    req.plan_tier, req.monthly_price_krw,
                    req.name, req.company, req.notes,
                    churned_at_ms, now, req.phone,
                ),
            )
            action = "updated"
        else:
            conn.execute(
                """
                INSERT INTO subscribers
                  (phone, plan_tier, monthly_price_krw, name, company, notes,
                   started_at_ms, churned_at_ms, created_at_ms, updated_at_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    req.phone, req.plan_tier, req.monthly_price_krw,
                    req.name, req.company, req.notes,
                    now, (now if req.churned else None),
                    now, now,
                ),
            )
            action = "created"

    print(f"[admin] subscriber {action}: {req.phone} plan={req.plan_tier} price={req.monthly_price_krw}")
    return {"ok": True, "action": action, "phone": req.phone}


@app.get("/api/admin/subscribers")
async def admin_list_subscribers(
    x_admin_token: Optional[str] = Header(None),
    include_churned: bool = False,
) -> dict:
    """전체 사용자 목록 (관리용)."""
    _admin_auth(x_admin_token)
    where = "" if include_churned else " WHERE churned_at_ms IS NULL"
    with db_conn() as conn:
        rows = conn.execute(
            f"""
            SELECT phone, plan_tier, monthly_price_krw, name, company, notes,
                   started_at_ms, churned_at_ms
            FROM subscribers{where}
            ORDER BY monthly_price_krw DESC, started_at_ms ASC
            """
        ).fetchall()
    return {
        "count": len(rows),
        "subscribers": [
            {
                "phone":             r["phone"],
                "plan_tier":         r["plan_tier"],
                "monthly_price_krw": r["monthly_price_krw"],
                "name":              r["name"],
                "company":           r["company"],
                "notes":             r["notes"],
                "started_at_ms":     r["started_at_ms"],
                "churned_at_ms":     r["churned_at_ms"],
            }
            for r in rows
        ],
    }


@app.get("/api/admin/business-stats")
async def admin_business_stats(
    x_admin_token: Optional[str] = Header(None),
) -> dict:
    """사업 건강도 metric — MRR, COGS, Margin, Top users, 시간 heatmap.

    응답:
      {
        "mrr_krw":               (활성 사용자의 monthly_price_krw 합)
        "active_subscribers":    int
        "churned_subscribers":   int
        "month_cogs_krw":        (이번 달 LLM 비용 합 — usage-stats month total)
        "month_calls":           int
        "gross_margin_pct":      (MRR - COGS) / MRR * 100  (MRR=0 이면 null)
        "arpu_krw":              MRR / active_subscribers
        "cost_per_user_krw":     month_cogs / active_subscribers
        "top_users": [
          {phone, name, calls, cost_krw, plan_tier, monthly_price_krw,
           user_margin_pct (= (price - cost) / price * 100), heavy_user (bool)}
        ],
        "heatmap": [[24개]×7,  요일×시간 호출수 매트릭스 (KST, 최근 7일)],
        "by_plan": {plan_tier: {count, mrr_krw}},
      }
    """
    _admin_auth(x_admin_token)
    now = _now_ms()

    # 이번 달 시작 (KST)
    kst = _KST
    nowdt = _dt.datetime.now(kst)
    month_start = nowdt.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    month_start_ms = int(month_start.timestamp() * 1000)
    week_ago_ms = int((nowdt - _dt.timedelta(days=6)).replace(
        hour=0, minute=0, second=0, microsecond=0).timestamp() * 1000)

    with db_conn() as conn:
        # 1) MRR — 활성 사용자의 monthly_price_krw 합
        mrr_row = conn.execute(
            """
            SELECT COUNT(*)                                AS active_count,
                   COALESCE(SUM(monthly_price_krw), 0)     AS mrr_krw
            FROM subscribers WHERE churned_at_ms IS NULL
            """
        ).fetchone()
        active = mrr_row["active_count"]
        mrr = mrr_row["mrr_krw"]

        churned_row = conn.execute(
            "SELECT COUNT(*) AS c FROM subscribers WHERE churned_at_ms IS NOT NULL"
        ).fetchone()
        churned = churned_row["c"]

        # 2) Plan 별 분포
        plan_rows = conn.execute(
            """
            SELECT plan_tier,
                   COUNT(*)                                AS count,
                   COALESCE(SUM(monthly_price_krw), 0)     AS mrr_krw
            FROM subscribers WHERE churned_at_ms IS NULL
            GROUP BY plan_tier
            """
        ).fetchall()
        by_plan = {
            r["plan_tier"]: {"count": r["count"], "mrr_krw": r["mrr_krw"]}
            for r in plan_rows
        }

        # 3) 이번 달 COGS — llm_usage_log 의 cost_krw 합
        cogs_row = conn.execute(
            """
            SELECT COUNT(*)                                AS calls,
                   COALESCE(SUM(cost_krw), 0)              AS cost_krw
            FROM llm_usage_log WHERE timestamp_ms >= ?
            """,
            (month_start_ms,),
        ).fetchone()
        month_cogs = round(cogs_row["cost_krw"], 4)
        month_calls = cogs_row["calls"]

        # 4) Top users — 이번 달 호출수 가장 많은 10명 (subscribers 와 join)
        #    llm_usage_log 에 phone 컬럼 없으니 api_usage 로 join (phone 있음).
        top_user_rows = conn.execute(
            """
            SELECT au.phone                                AS phone,
                   COUNT(*)                                AS calls,
                   COALESCE(SUM(au.cost_usd), 0) * ?       AS cost_krw,
                   s.name                                  AS name,
                   s.plan_tier                             AS plan_tier,
                   COALESCE(s.monthly_price_krw, 0)        AS monthly_price_krw
            FROM api_usage au
            LEFT JOIN subscribers s ON s.phone = au.phone
            WHERE au.created_at_ms >= ?
            GROUP BY au.phone
            ORDER BY calls DESC
            LIMIT 10
            """,
            (KRW_PER_USD, month_start_ms),
        ).fetchall()
        top_users = []
        for r in top_user_rows:
            cost_k = round(r["cost_krw"], 2)
            price_k = r["monthly_price_krw"] or 0
            # heavy user = 월 비용이 구독료의 50% 넘으면. 무료 사용자(price=0) 는 비용 > 1000원이면 heavy.
            if price_k > 0:
                user_margin = ((price_k - cost_k) / price_k * 100) if price_k > 0 else None
                heavy = cost_k > (price_k * 0.5)
            else:
                user_margin = None
                heavy = cost_k > 1000
            top_users.append({
                "phone":             r["phone"],
                "name":              r["name"],
                "plan_tier":         r["plan_tier"] or "(미등록)",
                "monthly_price_krw": price_k,
                "calls":             r["calls"],
                "cost_krw":          cost_k,
                "user_margin_pct":   None if user_margin is None else round(user_margin, 1),
                "heavy_user":        heavy,
            })

        # 5) 시간×요일 heatmap (최근 7일, KST 기준)
        heatmap_rows = conn.execute(
            """
            SELECT
                CAST(strftime('%w',
                    datetime((timestamp_ms + 9*3600*1000)/1000, 'unixepoch')
                ) AS INTEGER) AS dow,
                CAST(strftime('%H',
                    datetime((timestamp_ms + 9*3600*1000)/1000, 'unixepoch')
                ) AS INTEGER) AS hour,
                COUNT(*)      AS calls
            FROM llm_usage_log
            WHERE timestamp_ms >= ?
            GROUP BY dow, hour
            """,
            (week_ago_ms,),
        ).fetchall()
        # heatmap[dow][hour] = calls. KST 기준. dow: 0=일, 1=월, ..., 6=토 (sqlite strftime %w)
        heatmap = [[0] * 24 for _ in range(7)]
        for r in heatmap_rows:
            d, h = r["dow"], r["hour"]
            if 0 <= d <= 6 and 0 <= h <= 23:
                heatmap[d][h] = r["calls"]

    # 계산
    gross_margin_pct = ((mrr - month_cogs) / mrr * 100) if mrr > 0 else None
    arpu = (mrr / active) if active > 0 else 0
    cost_per_user = (month_cogs / active) if active > 0 else 0

    return {
        "mrr_krw":              mrr,
        "active_subscribers":   active,
        "churned_subscribers":  churned,
        "month_cogs_krw":       month_cogs,
        "month_calls":          month_calls,
        "gross_margin_pct":     None if gross_margin_pct is None else round(gross_margin_pct, 1),
        "arpu_krw":             round(arpu),
        "cost_per_user_krw":    round(cost_per_user, 2),
        "top_users":            top_users,
        "heatmap":              heatmap,
        "by_plan":              by_plan,
        "month_start_ms":       month_start_ms,
        "now_ms":               now,
    }


@app.get("/admin/usage")
async def admin_usage() -> dict:
    """최근 24시간 API 호출수 + 토큰 + 비용 (USD/KRW) + endpoint 별 breakdown."""
    one_day_ms = 24 * 60 * 60 * 1000
    cutoff = _now_ms() - one_day_ms
    with db_conn() as conn:
        row = conn.execute(
            """
            SELECT COUNT(*) AS calls,
                   COALESCE(SUM(input_tokens), 0)        AS input_tokens,
                   COALESCE(SUM(cached_input_tokens), 0) AS cached_input_tokens,
                   COALESCE(SUM(output_tokens), 0)       AS output_tokens,
                   COALESCE(SUM(cost_usd), 0)            AS cost_usd
            FROM api_usage WHERE created_at_ms > ?
            """,
            (cutoff,),
        ).fetchone()
        # endpoint 별 호출수 + 비용
        per_ep_rows = conn.execute(
            """
            SELECT endpoint,
                   COUNT(*) AS calls,
                   COALESCE(SUM(cost_usd), 0) AS cost_usd
            FROM api_usage WHERE created_at_ms > ?
            GROUP BY endpoint
            ORDER BY calls DESC
            """,
            (cutoff,),
        ).fetchall()
        # summary_cache 적중률 — 캐시 행수 / (총 Claude 호출 + 캐시 행수)
        cache_row_count = conn.execute(
            "SELECT COUNT(*) FROM summary_cache"
        ).fetchone()[0]

    by_endpoint = {
        r["endpoint"]: {
            "calls": r["calls"],
            "costUsd": round(r["cost_usd"], 4),
            "costKrw": round(r["cost_usd"] * KRW_PER_USD),
        }
        for r in per_ep_rows
    }
    return {
        "windowHours": 24,
        "calls": row["calls"],
        "inputTokens": row["input_tokens"],
        "cachedInputTokens": row["cached_input_tokens"],
        "outputTokens": row["output_tokens"],
        "costUsd": round(row["cost_usd"], 4),
        "costKrw": round(row["cost_usd"] * KRW_PER_USD),
        "byEndpoint": by_endpoint,
        "summaryCacheRows": cache_row_count,
        "dailyTotalLimit": DAILY_TOTAL_CALLS_LIMIT,
        "perPhoneDailyLimit": PER_PHONE_DAILY_LIMIT,
    }


@app.get("/healthz")
@app.get("/health")
def healthz():
    """헬스체크. 앱은 /health, 서버 자체 테스트는 /healthz 둘 다 받는다."""
    return {
        "ok": True,
        "model": CLAUDE_MODEL,
        "pricing_loaded": PRICING_PATH.exists(),
    }


# ============================================================================
# P0+P1+P2: summary_cache 헬퍼 + 공통 Claude JSON 호출 + 3개 endpoint
# ─────────────────────────────────────────────────────────────────────────────
# 캐시 정책 (사양서 §5):
#   key = (phone, endpoint, latest_msg_ts)
#   같은 phone + 같은 timestamp 면 cache 반환 (Claude 호출 안 함, 비용 0)
#   새 메시지 들어오면 latest_msg_ts 가 바뀌어 자동 invalidate
# ============================================================================

def _compute_latest_msg_ts(ctx: "ConversationContext") -> int:
    """recent_messages 와 call_summaries 중 가장 최근 시각을 반환.
    아무것도 없으면 0 (캐시 키로 쓰임 — 빈 컨텍스트도 그 자체로 캐시됨)."""
    candidates: list[int] = []
    if ctx.recent_messages:
        candidates.extend(m.timestamp_ms for m in ctx.recent_messages)
    if ctx.call_summaries:
        candidates.extend(c.started_at_ms for c in ctx.call_summaries)
    return max(candidates) if candidates else 0


def summary_cache_get(phone: str, endpoint: str, latest_msg_ts: int) -> Optional[dict]:
    """캐시 hit 면 response_json 파싱해서 반환. miss 면 None."""
    with db_conn() as conn:
        row = conn.execute(
            """
            SELECT response_json, generated_at_ms FROM summary_cache
            WHERE phone=? AND endpoint=? AND latest_msg_ts=?
            """,
            (phone, endpoint, latest_msg_ts),
        ).fetchone()
    if not row:
        return None
    try:
        cached = json.loads(row["response_json"])
        cached["_cache_hit"] = True
        cached["generated_at_ms"] = row["generated_at_ms"]
        return cached
    except json.JSONDecodeError:
        return None


def summary_cache_set(phone: str, endpoint: str, latest_msg_ts: int, response: dict) -> None:
    """결과 저장 (UPSERT)."""
    now = _now_ms()
    payload = json.dumps(response, ensure_ascii=False)
    with db_conn() as conn:
        conn.execute(
            """
            INSERT INTO summary_cache (phone, endpoint, latest_msg_ts, response_json, generated_at_ms)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(phone, endpoint, latest_msg_ts) DO UPDATE SET
                response_json=excluded.response_json,
                generated_at_ms=excluded.generated_at_ms
            """,
            (phone, endpoint, latest_msg_ts, payload, now),
        )


def build_context_user_message(ctx: "ConversationContext") -> str:
    """3개 endpoint 가 공유하는 user 메시지 빌더. 사장님 입장에서 본 고객 정보."""
    lines: list[str] = []
    lines.append("[고객 정보]")
    lines.append(f"전화번호: {ctx.phone}")
    lines.append(f"이름: {ctx.customer_name or '미등록'}")
    lines.append(f"상태: {ctx.customer_status or '미분류'}")
    lines.append(f"메모: {ctx.customer_memo or '없음'}")
    lines.append(f"리드 온도: {ctx.lead_heat or '없음'}")
    lines.append(f"계약금: {'받음' if ctx.deposit_paid else '안 받음'}")
    if ctx.scheduled_work_date:
        lines.append(f"예약 시공일(epoch ms): {ctx.scheduled_work_date}")
    else:
        lines.append("예약 시공일: 없음")
    lines.append("")
    lines.append("[최근 대화 (시간순)]")
    if not ctx.recent_messages:
        lines.append("(없음)")
    for m in ctx.recent_messages[-20:]:  # 최근 20건
        lines.append(f"({m.role} @ {m.timestamp_ms}): {m.body}")
    lines.append("")
    lines.append("[통화 요약]")
    if not ctx.call_summaries:
        lines.append("(없음)")
    for c in ctx.call_summaries[-10:]:
        lines.append(f"({c.direction} {c.duration_sec}초 @ {c.started_at_ms}): {c.summary}")
    return "\n".join(lines)


async def call_claude_json(
    *, system_prompt: str, user_msg: str, max_tokens: int = 600
) -> tuple[dict, "anthropic.types.Message"]:
    """공통 Claude JSON 호출 (사양서 §6 prompt caching 적용).
    Returns: (parsed JSON dict, raw response).
    """
    response = await claude_client.messages.create(
        model=CLAUDE_MODEL,
        max_tokens=max_tokens,
        timeout=CLAUDE_TIMEOUT,
        system=[
            {
                "type": "text",
                "text": system_prompt,
                "cache_control": {"type": "ephemeral"},
            }
        ],
        messages=[{"role": "user", "content": user_msg}],
    )
    text_parts = [
        getattr(b, "text", "") for b in response.content
        if getattr(b, "type", None) == "text"
    ]
    raw_text = "".join(text_parts)
    parsed = _parse_json_object(raw_text)
    return parsed, response


# ─── 3개 endpoint 의 시스템 프롬프트 (사양서 §2, §3, §4) ───
# 공통: 사장님 톤 코퍼스가 매번 다르면 prompt caching hit 안 됨 → 톤 샘플은
# system prompt 의 마지막 블록에 배치. 톤 샘플이 동일한 5분 내 호출은 캐시 적중.

CARD_SUMMARY_SYSTEM = """너는 1인 시공자(줄눈/타일) 사장님의 비서다.
고객과 주고받은 메시지·통화 기록을 보고, 사장님이 카드 한 줄만 봐도
"아, 이 사람" 즉판 가능한 한 줄 요약을 만든다.

규칙:
- 정확히 15~25 한국어 글자(공백 포함). 절대 30자 넘기지 마라.
- 정보 우선순위: (1) 현재 단계(문의/견적/예약/시공) (2) 핵심 키워드 (3) 다음 일정
- 명시되지 않은 정보 추측 금지. 가격은 사장님이 언급한 것만.
- 이모지 사용 OK (시각 분류용으로 1개 정도).
- 좋은 예: "타일 견적 문의 + 5/26 시공 의향", "계약금 받음, 5/30 시공 확정"

────── 가격표 (참고용) ──────
__PRICING__

────── 사장님 톤 (어휘 참고) ──────
__OWNER_TONE_SAMPLES__

답 형식 — 반드시 지켜라:
- 응답 첫 글자는 '{' 로 시작. 다른 텍스트 일체 X.
- 형식: {"summary": "..."}
"""


CONVERSATION_SUMMARY_SYSTEM = """너는 1인 시공자(줄눈/타일) 사장님의 비서다.
ChatScreen 진입 시 상단에 표시할 3~5줄 상세 요약을 만든다.

규칙:
- 정확히 3~5줄. 각 줄 앞에 이모지 1개(📍📷💰📅✅⏳⚠️ 중 적절히).
- 각 줄은 한 문장. 한 줄당 40자 이내.
- 사장님이 빠뜨린 행동 명시 (예: "사장님이 답변 안 함").
- 가격·일정은 메시지에 명시된 것만. 추측 금지.
- 통화 요약이 있으면 핵심을 한 줄에 포함.

current_stage 는 다음 enum 중 하나만 (현재 상황에 가장 맞는 것):
- inquiry: 신규 문의 / 첫 접촉
- photo_pending: 사진 요청·대기
- estimate_pending: 견적 작성 중
- estimate_sent: 견적 발송 완료, 응답 대기
- schedule_pending: 일정 조율
- deposit_pending: 일정 확정, 계약금 대기
- scheduled: 시공 확정 (날짜·계약금 다 확보)
- done: 시공 완료
- as_needed: AS 필요

────── 가격표 (참고용) ──────
__PRICING__

────── 사장님 톤 (어휘 참고) ──────
__OWNER_TONE_SAMPLES__

답 형식 — 반드시 지켜라:
- 응답 첫 글자는 '{' 로 시작. 다른 텍스트 일체 X.
- 형식: {"summary_lines": ["📍 ...", "💰 ..."], "current_stage": "estimate_pending"}
"""


NEXT_ACTION_SUGGEST_SYSTEM = """너는 1인 시공자(줄눈/타일) 사장님의 비서다.
지금 사장님이 이 고객에게 다음에 무엇을 해야 하는지 한 줄로 제안한다.

action_type enum (시나리오에 가장 맞는 것 1개만 골라라):
- send_estimate: 사진/정보 받았고 견적 보낼 차례 (사장님이 응답 누락)
- confirm_schedule: 견적 보냈고 일정 협의 차례
- request_deposit: 일정 확정됐고 계약금 안내 차례
- register_schedule: 일정 합의됐는데 캘린더 미등록
- send_followup: 시공 후 N일 지났고 후기/AS 점검 안내 시점
- none: 사장님이 지금 할 일 없음 (고객 답 대기 등)

urgency enum:
- high: 24시간 안에 처리 (고객 답 늦으면 잃을 위험)
- medium: 1~3일 안에 (정상 페이스)
- low: 여유 있음
- none: 제안 없음 (action_type=none 일 때)

title: 버튼 라벨이 될 짧은 문구 (예: "견적 보내기", "일정 협의", "계약금 안내").
subtitle: 한 줄 설명 (예: "사장님이 본 사진 기준 견적 작성"). 30자 이내.
primary_action.label: 버튼 텍스트. primary_action.action: 앱이 실행할 액션 키
(예: open_template_estimate / open_template_deposit / open_reply_suggest / open_date_picker / open_template_followup).
secondary_action: 보통 null. 필요시 같은 구조.

────── 가격표 (참고용) ──────
__PRICING__

────── 사장님 톤 (어휘 참고) ──────
__OWNER_TONE_SAMPLES__

답 형식 — 반드시 지켜라:
- 응답 첫 글자는 '{' 로 시작. 다른 텍스트 일체 X.
- 형식: {"action_type":"send_estimate","title":"견적 보내기","subtitle":"...","primary_action":{"label":"견적 작성하기","action":"open_template_estimate"},"secondary_action":null,"urgency":"high"}
"""


def _build_summary_system_prompt(template: str, owner_tone_samples: list[str]) -> str:
    return (
        template
        .replace("__PRICING__", load_pricing())
        .replace("__OWNER_TONE_SAMPLES__", format_owner_tone(owner_tone_samples))
    )


# ─── 응답 검증 + 보정 헬퍼 ───
def _coerce_card_summary(parsed: dict) -> dict:
    """Claude 응답 → 사양서 §2 출력 스키마로 정리."""
    summary = str(parsed.get("summary", "")).strip()
    if not summary:
        raise ValueError(f"card-summary: empty summary. raw={parsed!r}")
    # 길이 보정 — 너무 길면 25자 자름 (사양서 15~25)
    if len(summary) > 30:
        summary = summary[:25].rstrip() + "…"
    return {"summary": summary}


def _coerce_conversation_summary(parsed: dict) -> dict:
    """Claude 응답 → 사양서 §3 출력 스키마."""
    lines = parsed.get("summary_lines")
    if not isinstance(lines, list) or not lines:
        raise ValueError(f"conversation-summary: bad summary_lines. raw={parsed!r}")
    # 3~5 줄로 맞춤 (너무 많으면 자름)
    lines = [str(s).strip() for s in lines if str(s).strip()][:5]
    if not lines:
        raise ValueError(f"conversation-summary: all lines empty. raw={parsed!r}")
    stage = str(parsed.get("current_stage", "inquiry")).strip().lower()
    if stage not in VALID_STAGES:
        stage = "inquiry"
    return {"summary_lines": lines, "current_stage": stage}


def _coerce_next_action(parsed: dict) -> dict:
    """Claude 응답 → 사양서 §4 출력 스키마."""
    action_type = str(parsed.get("action_type", "none")).strip()
    if action_type not in VALID_ACTION_TYPES:
        action_type = "none"
    urgency = str(parsed.get("urgency", "none")).strip()
    if urgency not in VALID_URGENCIES:
        urgency = "none"
    # primary_action 정상화
    pa = parsed.get("primary_action") or None
    if isinstance(pa, dict):
        pa = {
            "label": str(pa.get("label", "")).strip(),
            "action": str(pa.get("action", "")).strip(),
        }
        if not pa["label"] or not pa["action"]:
            pa = None
    else:
        pa = None
    sa = parsed.get("secondary_action") or None
    if isinstance(sa, dict):
        sa = {
            "label": str(sa.get("label", "")).strip(),
            "action": str(sa.get("action", "")).strip(),
        }
        if not sa["label"] or not sa["action"]:
            sa = None
    else:
        sa = None
    return {
        "action_type": action_type,
        "title": str(parsed.get("title", "")).strip(),
        "subtitle": str(parsed.get("subtitle", "")).strip(),
        "primary_action": pa,
        "secondary_action": sa,
        "urgency": urgency,
    }


# ─── 공통 endpoint 처리 ───
async def _handle_summary_endpoint(
    *,
    ctx: ConversationContext,
    endpoint_label: str,
    system_template: str,
    coerce_fn,
    max_tokens: int,
    extra_response_fields: Optional[dict] = None,
) -> dict:
    """3개 endpoint 가 공유하는 처리 흐름: cache → rate-limit → Claude → coerce → cache set."""
    latest_ts = _compute_latest_msg_ts(ctx)

    # 1) 캐시 hit
    cached = summary_cache_get(ctx.phone, endpoint_label, latest_ts)
    if cached is not None:
        print(f"[{endpoint_label}] {ctx.phone} → cache HIT (ts={latest_ts})")
        return cached

    # 2) rate limit
    try:
        check_rate_limit(ctx.phone)
    except HTTPException:
        raise

    # 3) Claude 호출
    system_prompt = _build_summary_system_prompt(system_template, ctx.owner_tone_samples or [])
    user_msg = build_context_user_message(ctx)
    try:
        parsed, response = await call_claude_json(
            system_prompt=system_prompt, user_msg=user_msg, max_tokens=max_tokens
        )
    except Exception as e:
        print(f"[{endpoint_label}] {ctx.phone} Claude 호출 실패: {type(e).__name__}: {e}")
        raise HTTPException(502, f"LLM 호출 실패: {type(e).__name__}")

    # 4) 사용량 로그
    log_usage(ctx.phone, endpoint_label, response)
    _log_llm_usage_from_response(endpoint_label, response)  # §12.2
    usage = response.usage
    print(
        f"[{endpoint_label}] {ctx.phone} → ready "
        f"(in={getattr(usage,'input_tokens',0)} "
        f"cache_read={getattr(usage,'cache_read_input_tokens',0)} "
        f"out={getattr(usage,'output_tokens',0)})"
    )

    # 5) 형식 정리
    try:
        coerced = coerce_fn(parsed)
    except ValueError as e:
        raise HTTPException(502, f"LLM 응답 형식 오류: {e}")

    # 6) 캐시 저장 + 응답
    now = _now_ms()
    response_payload = {
        **coerced,
        "generated_at_ms": now,
    }
    if extra_response_fields:
        response_payload.update(extra_response_fields)
    summary_cache_set(ctx.phone, endpoint_label, latest_ts, response_payload)
    response_payload["_cache_hit"] = False
    return response_payload


@app.post("/api/card-summary")
async def card_summary(ctx: ConversationContext) -> dict:
    """HomeScreen 카드에 표시할 한 줄 요약 (15~25자)."""
    return await _handle_summary_endpoint(
        ctx=ctx,
        endpoint_label="card-summary",
        system_template=CARD_SUMMARY_SYSTEM,
        coerce_fn=_coerce_card_summary,
        max_tokens=200,
        extra_response_fields={"based_on_message_count": len(ctx.recent_messages)},
    )


@app.post("/api/conversation-summary")
async def conversation_summary(ctx: ConversationContext) -> dict:
    """ChatScreen 진입 시 표시할 3~5줄 상세 요약 + current_stage."""
    return await _handle_summary_endpoint(
        ctx=ctx,
        endpoint_label="conversation-summary",
        system_template=CONVERSATION_SUMMARY_SYSTEM,
        coerce_fn=_coerce_conversation_summary,
        max_tokens=600,
    )


@app.post("/api/next-action-suggest")
async def next_action_suggest(ctx: ConversationContext) -> dict:
    """다음 액션 1개 + urgency."""
    return await _handle_summary_endpoint(
        ctx=ctx,
        endpoint_label="next-action-suggest",
        system_template=NEXT_ACTION_SUGGEST_SYSTEM,
        coerce_fn=_coerce_next_action,
        max_tokens=400,
    )


# ============================================================================
# §13 — POST /api/address-resolve  (아파트명 → 풀 주소, 카카오 로컬 API)
# ─────────────────────────────────────────────────────────────────────────────
# 고객 메시지에서 추출된 아파트명 후보를 카카오 keyword.json (category_group_code=AP1)
# 로 검색해서 풀 주소 + 좌표 반환.
#
# KAKAO_REST_API_KEY 미설정 시 — {resolved: null, confidence: 0.0} 반환 (500 X).
# 모든 후보 실패 시 — 동일.
#
# log_llm_usage 도 호출 (model="kakao-local", 단가 0) — endpoint 호출수 모니터링용.
# ============================================================================

class AddressResolveRequest(BaseModel):
    candidate_keywords: list[str] = Field(default_factory=list)
    context_text: Optional[str] = None


async def _search_kakao_local(query: str) -> Optional[dict]:
    """카카오 키워드 검색 (아파트 그룹 AP1). 첫 hit 반환.

    실패(키 없음 / 네트워크 오류 / 200 아님 / docs 빈 배열) 시 None.
    """
    if not KAKAO_REST_API_KEY:
        return None
    url = "https://dapi.kakao.com/v2/local/search/keyword.json"
    headers = {"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"}
    params = {"query": query, "category_group_code": "AP1", "size": 5}
    try:
        async with httpx.AsyncClient(timeout=KAKAO_TIMEOUT_SEC) as client:
            resp = await client.get(url, headers=headers, params=params)
        if resp.status_code != 200:
            print(f"[address-resolve] kakao status={resp.status_code} query={query!r}")
            return None
        docs = resp.json().get("documents", [])
        return docs[0] if docs else None
    except Exception as e:
        print(f"[address-resolve] kakao error: {type(e).__name__}: {e} (query={query!r})")
        return None


def _log_address_resolve_call() -> None:
    """endpoint 호출 카운트만 잡고 비용은 0 (kakao-local 단가 0)."""
    log_llm_usage(
        endpoint="address-resolve",
        model="kakao-local",
        prompt_tokens=0,
        completion_tokens=0,
        cache_read_tokens=0,
        cache_write_tokens=0,
    )


@app.post("/api/address-resolve")
async def address_resolve(req: AddressResolveRequest) -> dict:
    """§13 — 아파트명 후보 → 풀 주소.

    입력: { candidate_keywords: [...], context_text: "..." }
    출력 (성공): { resolved, road_address, place_name, lat, lng, confidence }
    출력 (실패): { resolved: null, confidence: 0.0 }
    """
    # 1) 키 미설정 시 — 검증 §13 #2 ("키 없을 때 null") 케이스
    if not KAKAO_REST_API_KEY:
        print("[address-resolve] KAKAO_REST_API_KEY 미설정 — resolved=null 반환")
        _log_address_resolve_call()
        return {"resolved": None, "confidence": 0.0}

    # 2) 후보 keyword 별 순차 검색 — 첫 hit 사용
    for kw in (req.candidate_keywords or []):
        kw_clean = (kw or "").strip()
        if not kw_clean:
            continue
        hit = await _search_kakao_local(kw_clean)
        if hit:
            _log_address_resolve_call()
            return {
                "resolved":      hit.get("address_name"),
                "road_address":  hit.get("road_address_name"),
                "place_name":    hit.get("place_name"),
                "lat":           float(hit.get("y") or 0),
                "lng":           float(hit.get("x") or 0),
                "confidence":    0.9,
            }

    # 3) 모든 후보 실패 — context_text LLM fallback 은 사양서 §13.2 에서 "옵션" 으로
    #    표기되어 있고 비용/지연 trade-off 가 있어 일단 미구현. 미래 sprint 에서 추가.
    print(f"[address-resolve] all candidates failed: {req.candidate_keywords}")
    _log_address_resolve_call()
    return {"resolved": None, "confidence": 0.0}


# ============================================================================
# §14 — POST /api/refine  (✨ 다듬기, Google Gemini 2.5 Flash)
# ─────────────────────────────────────────────────────────────────────────────
# 안드로이드 ChatScreen 의 [✨ 다듬기] 버튼이 호출.
# 사장님이 친 원문 + 컨텍스트 (사장님 톤 + 최근 대화 + 고객 정보) 를 받아
# 자연스럽게 다듬어 한 줄로 반환.
#
# 모델은 Gemini 2.5 Flash — 다듬기는 단순 변환이라 Flash 가성비 최적.
# Claude (Sonnet) 대비 1/40 비용 (input $0.075 / output $0.30 per 1M).
#
# GEMINI_API_KEY 미설정 시 — HTTP 503. 안드로이드는 토스트 "AI 서버 연결 실패".
# Gemini API 호출 실패 시 — HTTP 502. 안드로이드는 같은 토스트.
#
# SYNC.md 2026-05-28 12:30 안드로이드 블록의 사양 그대로.
# ============================================================================

class RefineMessage(BaseModel):
    role: str          # "owner" | "customer"
    body: str
    timestamp_ms: int = 0


class RefineRequest(BaseModel):
    raw: str
    recent_messages: list[RefineMessage] = Field(default_factory=list)
    owner_tone_samples: list[str] = Field(default_factory=list)
    customer_name: Optional[str] = None
    customer_memo: Optional[str] = None


def _build_refine_system_prompt(owner_tone_samples: list[str]) -> str:
    """사장님 톤 few-shot 까지 포함한 system prompt."""
    cleaned: list[str] = []
    seen: set[str] = set()
    for s in (owner_tone_samples or []):
        s = (s or "").strip()
        if len(s) < 5 or len(s) > 300:
            continue
        if s in seen:
            continue
        seen.add(s)
        cleaned.append(s)
        if len(cleaned) >= 50:
            break
    tone_block = "\n".join(f"- {s}" for s in cleaned) if cleaned else "(샘플 없음 — 기본 정중한 한국어로)"

    return f"""너는 줄눈/타일 시공 사장님이 고객에게 보낼 문장을 자연스럽게 다듬어주는 비서다.

────── 규칙 (절대 지킬 것) ──────
- 원문의 의미를 절대 바꾸지 마라
- 정중하고 자연스러운 한국어 (존댓말)
- 가격·날짜·시간·시공 종류 등 사장님이 원문에 안 쓴 정보는 절대 추가 금지
- 길이는 원문과 비슷하게 유지 (한두 글자 차이 OK, 두 배 X)
- 사장님 톤 샘플의 어휘·문장 길이·존댓말 비율·이모지 사용을 모방
- 사장님이 안 쓸 법한 단어/문체로 답하지 마라
- 금기어: "급하면" 계열, "싸다" 계열 → 변형 표현도 피하라

────── 사장님 톤 샘플 (이 문체를 따라라) ──────
{tone_block}

────── 답 형식 — 반드시 지켜라 ──────
- 출력은 다듬어진 문장 한 줄 (또는 짧은 단락) 만
- 인사·설명·따옴표·코드블럭·백틱·JSON·태그 절대 X
- 첫 글자부터 다듬어진 문장으로 시작
"""


def _build_refine_user_message(req: RefineRequest) -> str:
    """Gemini 에 보낼 user 메시지. 고객 정보 → 최근 대화 → 다듬을 원문 순."""
    lines: list[str] = []

    # 고객 정보 (있으면)
    has_customer = bool((req.customer_name and req.customer_name.strip())
                        or (req.customer_memo and req.customer_memo.strip()))
    if has_customer:
        lines.append("[고객 정보]")
        if req.customer_name and req.customer_name.strip():
            lines.append(f"이름: {req.customer_name.strip()}")
        if req.customer_memo and req.customer_memo.strip():
            lines.append(f"메모: {req.customer_memo.strip()}")
        lines.append("")

    # 최근 대화 (있으면)
    recent = req.recent_messages or []
    if recent:
        lines.append("[최근 대화 (시간순, 흐름 참고용)]")
        for m in recent[-20:]:
            role_ko = "사장님" if m.role == "owner" else "고객"
            body = (m.body or "").strip()
            if body:
                lines.append(f"({role_ko}): {body}")
        lines.append("")

    # 다듬을 원문
    lines.append("[다듬을 원문]")
    lines.append(req.raw)
    lines.append("")
    lines.append("위 원문을 위 흐름과 사장님 톤에 맞게 자연스럽게 다듬어, 한 줄로만 답하라.")
    return "\n".join(lines)


async def _call_gemini_refine(
    system_prompt: str, user_msg: str
) -> tuple[str, dict]:
    """Gemini 2.5 Flash 호출. (polished_text, usage_metadata dict) 반환.

    실패 시 RuntimeError raise.
    """
    if not GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY env var not set")

    url = (
        f"https://generativelanguage.googleapis.com/v1beta/models/"
        f"{GEMINI_MODEL}:generateContent?key={GEMINI_API_KEY}"
    )
    payload = {
        "systemInstruction": {"parts": [{"text": system_prompt}]},
        "contents": [
            {"role": "user", "parts": [{"text": user_msg}]},
        ],
        "generationConfig": {
            "temperature": 0.7,
            "maxOutputTokens": GEMINI_MAX_OUTPUT_TOKENS,
            "topP": 0.95,
        },
    }

    async with httpx.AsyncClient(timeout=GEMINI_TIMEOUT_SEC) as client:
        resp = await client.post(
            url, json=payload, headers={"Content-Type": "application/json"}
        )

    if resp.status_code != 200:
        raise RuntimeError(
            f"Gemini API status {resp.status_code}: {resp.text[:300]}"
        )

    data = resp.json()
    candidates = data.get("candidates") or []
    if not candidates:
        raise RuntimeError(f"Gemini empty candidates: {str(data)[:300]}")

    content = candidates[0].get("content") or {}
    parts = content.get("parts") or []
    polished = "".join(p.get("text", "") for p in parts).strip()
    if not polished:
        # finishReason 이 SAFETY 같은 경우 candidates 는 있지만 text 가 비어있음
        finish = candidates[0].get("finishReason", "?")
        raise RuntimeError(f"Gemini empty polished (finishReason={finish}): {str(data)[:300]}")

    usage_meta = data.get("usageMetadata") or {}
    return polished, usage_meta


@app.post("/api/refine")
async def refine_endpoint(req: RefineRequest) -> dict:
    """§14 — 사장님 원문을 Gemini 2.5 Flash 로 다듬어 polished 한 줄 반환.

    입력: { raw, recent_messages, owner_tone_samples, customer_name?, customer_memo? }
    출력: { polished: "..." }

    실패 시:
      - GEMINI_API_KEY 미설정 → 503
      - Gemini API 호출 실패 → 502
      - raw 비어있음 → 400
    """
    raw = (req.raw or "").strip()
    if not raw:
        raise HTTPException(400, "raw 가 비어있음")

    if not GEMINI_API_KEY:
        print("[refine] GEMINI_API_KEY 미설정 — 503 반환")
        raise HTTPException(
            503,
            "GEMINI_API_KEY 미설정. Mac mini 의 launchd plist EnvironmentVariables 에 박아주세요."
        )

    system_prompt = _build_refine_system_prompt(req.owner_tone_samples or [])
    user_msg = _build_refine_user_message(req)

    try:
        polished, usage_meta = await _call_gemini_refine(system_prompt, user_msg)
    except Exception as e:
        print(f"[refine] Gemini 호출 실패: {type(e).__name__}: {e}")
        raise HTTPException(502, f"Gemini 호출 실패: {type(e).__name__}")

    # log_llm_usage — endpoint 카운트 + 비용 계산 (단가 dict 의 gemini-2.5-flash)
    prompt_tokens = int(usage_meta.get("promptTokenCount", 0) or 0)
    completion_tokens = int(usage_meta.get("candidatesTokenCount", 0) or 0)
    cache_read_tokens = int(usage_meta.get("cachedContentTokenCount", 0) or 0)
    log_llm_usage(
        endpoint="refine",
        model=GEMINI_MODEL,
        prompt_tokens=prompt_tokens,
        completion_tokens=completion_tokens,
        cache_read_tokens=cache_read_tokens,
        cache_write_tokens=0,
    )
    print(
        f"[refine] OK in={prompt_tokens} out={completion_tokens} "
        f"cache_read={cache_read_tokens} polished_len={len(polished)}"
    )

    return {"polished": polished}


# ============================================================================
# 안드로이드 앱 호환 stub 엔드포인트 (404 방지)
# ─────────────────────────────────────────────────────────────────────────────
# 앱의 일부 화면이 부르는 엔드포인트들이 아직 서버에 없으면 빨간 표시·크래시 유발.
# 일단 200 OK no-op 으로 받아둠. 실제 로직은 추후 구현.
# ============================================================================
@app.post("/api/style-profile/learn")
async def style_profile_learn(payload: Optional[dict] = None) -> dict:
    """말투 학습 요청. 현재는 no-op (ownerToneSamples 는 /prepare-reply 에
    매 요청마다 함께 보내므로 별도 저장 불필요). 추후 영구 저장 필요시 확장."""
    samples_count = 0
    if isinstance(payload, dict):
        for key in ("samples", "ownerToneSamples", "messages"):
            v = payload.get(key)
            if isinstance(v, list):
                samples_count = len(v)
                break
    print(f"[style-profile/learn] received {samples_count} samples (no-op stub)")
    return {"ok": True, "stored": samples_count, "note": "stub — samples are read per-request via ownerToneSamples"}


@app.post("/api/intent/classify")
async def intent_classify_stub(payload: Optional[dict] = None) -> dict:
    """의도 분류 stub. 추후 구현 시 Claude 호출로 교체."""
    return {"ok": True, "intent": "unknown", "note": "stub — not yet implemented"}


@app.post("/api/reply/suggest")
async def reply_suggest_stub(payload: Optional[dict] = None) -> dict:
    """답변 추천 (다른 채널) stub. 추후 구현 시 Claude 호출로 교체."""
    return {"ok": True, "suggestions": [], "note": "stub — use /prepare-reply instead"}
