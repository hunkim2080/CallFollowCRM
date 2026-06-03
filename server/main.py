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

CLAUDE_MODEL = "claude-sonnet-4-6"        # 매출 직결 워크로드 (prepare-reply)
HAIKU_MODEL  = "claude-haiku-4-5"         # 단순 요약/분류 워크로드 (card/conversation/next-action). Sonnet 의 ~1/3 비용.
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
        # §16 — Tone RAG (4단계 킬러콘텐츠) — owner_tone 메타테이블
        # 사장님이 보낸 sent SMS 풀. RAG retrieval 의 source.
        # 같은 device_id 안에서 text_hash 중복 INSERT 차단 (dedup).
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS owner_tone (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id       TEXT NOT NULL,
                text            TEXT NOT NULL,
                text_hash       TEXT NOT NULL,
                timestamp_ms    INTEGER NOT NULL,
                created_at_ms   INTEGER NOT NULL,
                UNIQUE(device_id, text_hash)
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_owner_tone_device "
            "ON owner_tone(device_id, created_at_ms)"
        )
        # §16 patch — embedding BLOB 을 owner_tone 컬럼에 저장 (sqlite-vec 의존성 제거)
        # Mac 의 system Python 은 enable_load_extension 비활성화라 sqlite-vec 못 씀.
        # numpy 로 application 단 cosine 검색 — 50,000 건 미만이면 ~수십ms 응답.
        try:
            con.execute("ALTER TABLE owner_tone ADD COLUMN embedding BLOB")
        except sqlite3.OperationalError:
            pass  # 이미 컬럼 있음 (재시작 시)
        # §17 — Customer Personas (5단계 킬러콘텐츠) — phone 별 한두 줄 요약
        # 24h cache + 만료 시 백그라운드 Haiku 자동 생성.
        # prepare-reply 의 [고객 정보] 영역에 inject → 사장님 톤 + 고객 맞춤 답변.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS customer_personas (
                phone                   TEXT PRIMARY KEY,
                persona_text            TEXT NOT NULL,
                model_used              TEXT NOT NULL,
                source_message_count    INTEGER NOT NULL DEFAULT 0,
                generated_at_ms         INTEGER NOT NULL,
                last_refresh_started_ms INTEGER
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_customer_personas_age "
            "ON customer_personas(generated_at_ms)"
        )
        # §19 — 시공접수서 (고객 자가확인 폼)
        # 사장님이 채팅에서 [접수서 링크 보내기] 누름 → 서버 토큰 발급 → 고객 모바일
        # 브라우저로 진입 → 폼 입력 → 제출. 앱은 status polling 으로 작성 여부 확인.
        # 토큰 7일 만료 (사장님 결정).
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS intake_forms (
                token           TEXT PRIMARY KEY,
                phone           TEXT NOT NULL,
                customer_name   TEXT,
                issued_at_ms    INTEGER NOT NULL,
                expires_at_ms   INTEGER NOT NULL,
                submitted_at_ms INTEGER,
                payload_json    TEXT,
                device_id       TEXT,
                owner_phone     TEXT,
                created_at_ms   INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_intake_phone "
            "ON intake_forms(phone, issued_at_ms)"
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_intake_device "
            "ON intake_forms(device_id, issued_at_ms)"
        )
        # §20 — 팀 관리 (99k 티어, 프로토 1:1 — 대표/팀원 2개 역할)
        # 프로토 design-preview/ringgo-redesign.html 의 team 배열·openMemberView·
        # teamPhotoAlert·departed 흐름을 서버에서 재현. 팀원은 앱 설치 X, URL 링크로.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS team_members (
                member_id       TEXT PRIMARY KEY,        -- 'tm_' + 8자 base62
                owner_phone     TEXT NOT NULL,           -- 사장님 phone (팀 식별)
                phone           TEXT NOT NULL,           -- 팀원 phone (대표면 == owner_phone)
                name            TEXT NOT NULL,           -- 표시명 ('김기사' 등)
                role            TEXT NOT NULL,           -- 'owner' | 'worker' (프로토 '대표'/'팀원')
                tint            INTEGER NOT NULL DEFAULT 0,  -- 아바타 색
                created_at_ms   INTEGER NOT NULL,
                removed_at_ms   INTEGER,                 -- NULL=활성
                UNIQUE(owner_phone, phone)
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_team_members_owner "
            "ON team_members(owner_phone, removed_at_ms)"
        )
        # 팀원 화면 URL 토큰 (접수서와 동일 패턴, 시공 다음날 자정 만료)
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS team_member_links (
                token                   TEXT PRIMARY KEY,
                member_id               TEXT NOT NULL,
                owner_phone             TEXT NOT NULL,
                issued_at_ms            INTEGER NOT NULL,
                expires_at_ms           INTEGER NOT NULL,
                schedule_snapshot_json  TEXT,            -- 사장님이 박은 일정 데이터
                last_accessed_ms        INTEGER,
                FOREIGN KEY (member_id) REFERENCES team_members(member_id)
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_team_links_member "
            "ON team_member_links(member_id, issued_at_ms)"
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_team_links_owner "
            "ON team_member_links(owner_phone, issued_at_ms)"
        )
        # 팀원 이벤트 — 출발/사진/도착 (사장님 polling 으로 알림 카드 띄움)
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS team_member_events (
                event_id        INTEGER PRIMARY KEY AUTOINCREMENT,
                token           TEXT,
                member_id       TEXT NOT NULL,
                owner_phone     TEXT NOT NULL,
                event_type      TEXT NOT NULL,           -- 'departed'|'photo'|'arrived'
                payload_json    TEXT,
                created_at_ms   INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_team_events_owner_created "
            "ON team_member_events(owner_phone, created_at_ms)"
        )
        # 팀원 사진 업로드 (시공 전/중/후/추가)
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS team_site_photos (
                photo_id        INTEGER PRIMARY KEY AUTOINCREMENT,
                token           TEXT,
                member_id       TEXT NOT NULL,
                owner_phone     TEXT NOT NULL,
                label           TEXT,                    -- '시공 전'|'시공 중'|'시공 후'|'추가 사진'
                image_data_url  TEXT,                    -- base64 (작은 썸네일)
                image_path      TEXT,                    -- 큰 사진은 디스크 경로
                note            TEXT,
                uploaded_at_ms  INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_team_photos_owner_uploaded "
            "ON team_site_photos(owner_phone, uploaded_at_ms)"
        )
        # §19 patch — 프로토타입 openQuote 1:1: 시공일·견적·계약금은 issue 때 받아서
        # 폼에 "표시만". 고객은 전화·주소·메모·유입경로만 입력.
        for col_def in [
            "scheduled_at_ms INTEGER",       # 사장님 정한 확정 시공일 (epoch ms)
            "scheduled_days INTEGER DEFAULT 1",
            "estimate_items_json TEXT",      # 사장님 견적 항목 [{name, price_man, unit?, area?}]
            "total_man INTEGER DEFAULT 0",   # 합계 (만원)
            "deposit_amount_krw INTEGER DEFAULT 0",
            "deposit_mode TEXT DEFAULT 'none'",   # 'none'|'ratio'|'fixed'
            "deposit_ratio_pct INTEGER",
            "biz_name TEXT",                  # 발급 시점 사장님 사업자명 (snapshot)
            # §19.2 — 사장님 2026-06-02 명세 (프로토 bizInfo/quoteCfg 1:1)
            "work_month INTEGER",             # 프로토 qmon
            "work_day INTEGER",               # 프로토 qday
            "work_year INTEGER",              # 프로토 qyear (없으면 2026 default — 추후 +1)
            "deposit_value INTEGER",          # 프로토 c.depVal (mode==ratio: %, fixed: krw)
            "biz_owner TEXT",                 # 프로토 bizInfo.owner (대표자명)
            "biz_no TEXT",                    # 프로토 bizInfo.bizNo (사업자등록번호)
            "biz_addr TEXT",                  # 프로토 bizInfo.addr
            "biz_phone TEXT",                 # 프로토 bizInfo.phone
            "biz_seal TEXT",                  # 프로토 bizInfo.seal (직인 문구 "디테일라인 직인" 등)
            "biz_valid_days INTEGER",         # 프로토 bizInfo.validDays (견적서 유효기간)
            "confirmed_date_iso TEXT",        # 고객이 확인한 시공일 ISO (있으면)
            "survey_json TEXT",               # 고객 제출 시 유입경로 등 (finalizeQuote.src 구조)
        ]:
            col_name = col_def.split()[0]
            try:
                con.execute(f"ALTER TABLE intake_forms ADD COLUMN {col_def}")
            except sqlite3.OperationalError:
                pass  # already exists
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


def db_set_ready(phone: str, v2: dict) -> None:
    """v2 dict 통째로 suggestions_json 에 저장.

    v2 = {scenario, scenario_confidence, scenario_reason, suggestions:[3 obj]}
    suggestions_cache 테이블의 schema 는 안 만짐 — suggestions_json (TEXT) 안에 dict 직렬화.
    """
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
            (json.dumps(v2, ensure_ascii=False), now, now, phone),
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
    """레거시 호환용 — 단일 string. 새 코드는 build_system_blocks 권장."""
    return (
        SYSTEM_PROMPT_TEMPLATE
        .replace("__PRICING__", load_pricing())
        .replace("__OWNER_TONE_SAMPLES__", format_owner_tone(owner_tone_samples))
    )


# ─── v2 (SYNC 2026-05-28 19:00 android 요청) Intent Pool v1 ───
# 8개 시나리오 × 3종 intent. fallback_default 는 분류 신뢰도 < 0.6 일 때.
# 사장님 결정 (2026-05-28): "답변 3개의 말투 차이" → "상담 전략 3개의 의도 차이" 패러다임 전환.
INTENT_POOL_V1: dict[str, list[dict]] = {
    "initial_inquiry": [
        {"intent_key": "quick",        "label": "📞 빠른 답변"},
        {"intent_key": "info",         "label": "❓ 정보 요청"},
        {"intent_key": "assure",       "label": "🤝 안심 설명"},
    ],
    "price_inquiry": [
        {"intent_key": "quote",        "label": "💰 견적 안내"},
        {"intent_key": "condition",    "label": "✅ 조건 확인"},
        {"intent_key": "booking",      "label": "📅 예약 유도"},
    ],
    "hesitation": [
        {"intent_key": "price_explain", "label": "💬 가격 설명"},
        {"intent_key": "case",          "label": "📷 사례 제시"},
        {"intent_key": "nudge",         "label": "➡️ 결정 유도"},
    ],
    "schedule": [
        {"intent_key": "date_confirm",  "label": "🗓️ 날짜 확정"},
        {"intent_key": "alternative",   "label": "🔄 대안 제시"},
        {"intent_key": "prep",          "label": "📋 준비 안내"},
    ],
    "pre_booking": [
        {"intent_key": "deposit",       "label": "💵 계약금 안내"},
        {"intent_key": "final_check",   "label": "✔️ 최종 확인"},
        {"intent_key": "caution",       "label": "⚠️ 주의사항 안내"},
    ],
    "pre_service": [
        {"intent_key": "visit",         "label": "🚪 방문 안내"},
        {"intent_key": "prep_req",      "label": "📝 준비 요청"},
        {"intent_key": "assure_pre",    "label": "🛡️ 안심 안내"},
    ],
    "post_service": [
        {"intent_key": "usage",         "label": "📖 사용 안내"},
        {"intent_key": "review",        "label": "⭐ 후기 요청"},
        {"intent_key": "upsell",        "label": "🎁 추가 제안"},
    ],
    # 분류 신뢰도 < 0.6 일 때 사용 — initial_inquiry 가 아닌 별도 fallback (사장님 결정 19:00)
    "fallback_default": [
        {"intent_key": "general",       "label": "💬 무난 답변"},
        {"intent_key": "clarify",       "label": "❓ 추가 확인"},
        {"intent_key": "manual",        "label": "✍️ 직접 확인"},
    ],
}
VALID_SCENARIOS = set(INTENT_POOL_V1.keys())
# {scenario: {intent_key: label}} — 빠른 lookup 용
_INTENT_LABEL_MAP: dict[str, dict[str, str]] = {
    sc: {it["intent_key"]: it["label"] for it in items}
    for sc, items in INTENT_POOL_V1.items()
}
SCENARIO_CONFIDENCE_FLOOR = 0.6  # 이 미만이면 fallback_default 로 강등


# ─── prompt caching 최적화 — system 을 4 block 으로 분리 ───
# Anthropic prompt caching: 각 block 에 cache_control 박으면 breakpoint 생성.
# 같은 block 이 5분 내 재호출 시 cache_read 단가 (입력의 1/10) 적용.
#
# 분리 전략:
#   A. 고정 규칙        — 영원히 안 변함. 가장 큰 cache 적중률
#   B. 가격표           — pricing.md mtime 변경 시만 갱신
#   C. 사장님 톤 샘플   — 같은 사장님이면 거의 동일 (앱이 보낸 50건)
#   D. 답 형식 강제     — 영원히 안 변함. 마지막 위치
#
# Anthropic 의 prompt caching breakpoint 최대 4개 — 정확히 4 block.
_SYSTEM_BLOCK_A_FIXED = """너는 줄눈 시공 사장님이 고객 문자에 답장할 때 도와주는 비서다.

────── 사장님 톤 학습 (반드시 모방할 것) ──────
다음은 사장님이 평소 고객에게 보낸 실제 메시지들이다.
어휘·문장 길이·반말/존댓말 비율·이모지 사용·인사 방식·문장 끝 처리를
모방해야 한다. 절대 사장님이 안 쓸 법한 단어/문체로 답하지 말 것.

──────────────────────────────────────────────
[v2 — 상담 전략 분화 (사장님 결정 2026-05-28)]
──────────────────────────────────────────────

══════ 답변 패러다임 ══════
"답변 3개의 말투 차이" 가 아니라 **"상담 전략 3개의 의도 차이"** 다.
세 후보는 명확히 서로 다른 상담 방향을 가진다.
**단순 말투/길이/친절도 차이는 실패다.**

══════ 1단계: 시나리오 분류 ══════
최근 대화 (recent_messages + 방금 받은 메시지) 를 보고
다음 8개 중 정확히 하나를 골라 `scenario` 에 박는다.
신뢰도를 0.0~1.0 으로 측정해서 `scenario_confidence` 에 박는다.
신뢰도 < 0.6 이면 반드시 `fallback_default` 로 분류한다.

  - `initial_inquiry`   : 초기 문의 (첫 접촉, 막연한 관심)
  - `price_inquiry`     : 가격 문의 (구체적 가격 질문 또는 견적 요청)
  - `hesitation`        : 고객 망설임 (가격 보고 멈칫, 비교 검토 중)
  - `schedule`          : 일정 조율 (시공 날짜·시간 조율 중)
  - `pre_booking`       : 예약 확정 전 (일정 합의됐고 계약금 단계)
  - `pre_service`       : 시공 전 (계약금 받음, 시공일 임박)
  - `post_service`      : 시공 후 (시공 완료, AS·후기 단계)
  - `fallback_default`  : 신뢰도 부족 또는 위 7개 어디에도 안 맞음

`scenario_reason` 에 한 줄로 왜 그 시나리오로 분류했는지 적는다 (사장님 디버그용).

══════ 2단계: 시나리오 별 정의된 3종 intent ══════
각 시나리오의 3종 intent_key 와 label 은 정해져 있다. **다른 키 사용 금지.**

`initial_inquiry`:
  - "quick"   / "📞 빠른 답변"    — 한 문장 즉답. 사장님이 바쁠 때 그대로
  - "info"    / "❓ 정보 요청"    — 고객 정보 한 가지 물어보기 (평수, 위치, 사진 등)
  - "assure"  / "🤝 안심 설명"    — 시공 절차·일정·품질 간단 안내로 신뢰감

`price_inquiry`:
  - "quote"     / "💰 견적 안내"   — 가격표 기반 견적 (정확한 금액 또는 범위)
  - "condition" / "✅ 조건 확인"   — 가격 산정에 필요한 추가 정보 물어보기 (신축/구축, 타일 종류 등)
  - "booking"   / "📅 예약 유도"   — 가격 안내하면서 동시에 시공일 잡으러 nudge

`hesitation`:
  - "price_explain" / "💬 가격 설명"  — 왜 그 가격인지 가치 풀어 설명
  - "case"          / "📷 사례 제시"  — 비슷한 시공 사례·사진 보여주기 (사장님 직접 첨부 안내)
  - "nudge"         / "➡️ 결정 유도"  — 망설임 끊고 결정 push (한정·할인 X — 자연스러운 push)

`schedule`:
  - "date_confirm" / "🗓️ 날짜 확정"  — 특정 날짜 제안 + 확정 요청
  - "alternative"  / "🔄 대안 제시"  — 대안 날짜 2-3 개 제시
  - "prep"         / "📋 준비 안내"  — 시공 당일 고객 준비사항 (가구 이동, 청소 등)

`pre_booking`:
  - "deposit"     / "💵 계약금 안내"  — 계약금 금액·입금 방법
  - "final_check" / "✔️ 최종 확인"   — 시공 내용·일정·비용 최종 확인
  - "caution"     / "⚠️ 주의사항 안내" — 시공 전 알아둘 점 (먼지, 소음, 환기 등)

`pre_service`:
  - "visit"     / "🚪 방문 안내"     — 방문 시간·연락 방식
  - "prep_req"  / "📝 준비 요청"     — 고객이 미리 할 일 (자리 비우기, 가구 이동 등)
  - "assure_pre"/ "🛡️ 안심 안내"     — 시공 품질·안전 관련 안심 메시지

`post_service`:
  - "usage"   / "📖 사용 안내"      — 시공 후 사용·관리법 (건조 시간, 청소법 등)
  - "review"  / "⭐ 후기 요청"       — 만족도 + 후기·사진 요청
  - "upsell"  / "🎁 추가 제안"      — 다른 시공 제안 (욕실, 주방 등)

`fallback_default` (신뢰도 부족 시 — 분류가 애매할 때 안전한 default):
  - "general" / "💬 무난 답변"      — 정중하고 무난한 일반 답변
  - "clarify" / "❓ 추가 확인"      — 고객 의도 추가로 물어보기
  - "manual"  / "✍️ 직접 확인"      — "사장님이 직접 확인 후 답변드릴게요" 류 안전 답변

══════ 3단계: 각 답변에 why 필드 ══════
각 답변(suggestions[i]) 의 `why` 에 한 줄로 "왜 이 답변을 추천했는지" 적는다 (사장님 UI 미노출, 로깅·품질 개선용).

══════ 결정적 룰 ══════
1. 정확히 3개. 시나리오의 3 intent 모두 사용. 같은 intent_key 두 번 X.
2. label 은 위 표 그대로 (이모지 + 텍스트 정확 일치).
3. text 는 실제 사장님이 고객에게 보낼 문장. 사장님 톤 모방.
4. 가격/날짜/시간은 대화 또는 가격표에서만. 추측 X.
5. 시나리오의 의도가 다르므로 3개 답변은 자연스럽게 다른 전략이 됨.
"""

_SYSTEM_BLOCK_D_FORMAT = """────── 기본 규칙 ──────
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

══════ 답 형식 — 반드시 지켜라 (v2) ══════
- 응답 첫 글자는 무조건 '{' 로 시작. 인사·설명·코드블럭·백틱·tag 절대 X.
- 정확히 하나의 JSON 객체만 반환. 그 외 텍스트(앞·뒤 어디든) 완전 금지.
- 형태 (필수 필드 모두):
{
  "scenario": "<위 8개 enum 중 하나>",
  "scenario_confidence": <0.0 ~ 1.0 사이 float>,
  "scenario_reason": "<왜 이 시나리오로 분류했는지 한 줄>",
  "suggestions": [
    {"intent_key":"<위 시나리오의 정의된 키>","label":"<위 시나리오의 정의된 라벨 그대로>","text":"<실제 답변>","why":"<왜 이걸 추천했는지 한 줄>"},
    {"intent_key":"...","label":"...","text":"...","why":"..."},
    {"intent_key":"...","label":"...","text":"...","why":"..."}
  ]
}
- 정확히 3개. 시나리오의 3 intent 모두 사용. intent_key 중복 X.
- scenario_confidence < 0.6 이면 "scenario":"fallback_default" 사용 (강제).
"""


def build_system_blocks(owner_tone_samples: list[str]) -> list[dict]:
    """레거시 동기 버전 — RAG 없이 ownerToneSamples 만 사용.

    Tone RAG 비활성화 (의존성 없음) 시 fallback. 또는 단순 호출자가 async 못 쓸 때.
    """
    pricing_block = "────── 가격표 ──────\n" + load_pricing()
    tone_block = (
        "────── 사장님 톤 샘플 (모방 대상) ──────\n"
        + format_owner_tone(owner_tone_samples)
    )
    return [
        {"type": "text", "text": _SYSTEM_BLOCK_A_FIXED, "cache_control": {"type": "ephemeral"}},
        {"type": "text", "text": pricing_block,        "cache_control": {"type": "ephemeral"}},
        {"type": "text", "text": tone_block,           "cache_control": {"type": "ephemeral"}},
        {"type": "text", "text": _SYSTEM_BLOCK_D_FORMAT},
    ]


async def build_system_blocks_async(
    owner_tone_samples: list[str],
    latest_msg: str,
    device_id: str = "owner-anon",
) -> list[dict]:
    """§16 — Tone RAG 통합 system 빌더.

    block C (사장님 톤) 위치에 RAG 로 retrieved top-10 inject.
    RAG 비활성화 또는 풀 비어있으면 기존 ownerToneSamples 로 fallback.

    block C 가 cache_control 박혀있어서:
      - 같은 device + 같은 query → 같은 retrieved set → cache hit
      - 다른 query → cache miss + 새 retrieval
    """
    pricing_block = "────── 가격표 ──────\n" + load_pricing()

    # RAG retrieve 시도 — 실패 시 None
    rag_samples = None
    try:
        rag_samples = await retrieve_rag_tone_samples(device_id, latest_msg, top_k=10)
    except Exception as e:
        print(f"[prepare-reply] RAG retrieve 예외 (fallback to ownerToneSamples): {type(e).__name__}: {e}")

    if rag_samples:
        # RAG 적중 — 의미적으로 가까운 사장님 답변 top-10
        tone_block = (
            "────── 사장님 톤 샘플 (RAG: 유사 답변 top-10) ──────\n"
            + format_owner_tone(rag_samples)
        )
    else:
        # fallback — 안드가 보낸 ownerToneSamples 50건
        tone_block = (
            "────── 사장님 톤 샘플 (모방 대상) ──────\n"
            + format_owner_tone(owner_tone_samples or [])
        )

    return [
        {"type": "text", "text": _SYSTEM_BLOCK_A_FIXED, "cache_control": {"type": "ephemeral"}},
        {"type": "text", "text": pricing_block,        "cache_control": {"type": "ephemeral"}},
        {"type": "text", "text": tone_block,           "cache_control": {"type": "ephemeral"}},
        {"type": "text", "text": _SYSTEM_BLOCK_D_FORMAT},
    ]


def build_user_message(req: "PrepareReplyRequest", persona_hint: Optional[str] = None) -> str:
    """user message 빌더 — 페르소나 hint 가 있으면 [고객 정보] 영역에 inject (§17)."""
    c = req.customer
    lines: list[str] = []
    lines.append("[고객 정보]")
    lines.append(f"이름: {c.name if (c and c.name) else '없음'}")
    lines.append(f"메모: {c.memo if (c and c.memo) else '없음'}")
    lines.append(f"리드 온도: {c.leadHeat if (c and c.leadHeat) else '없음'}")
    deposit = "받음" if (c and c.depositPaid) else "안 받음"
    lines.append(f"계약금 입금: {deposit}")
    if persona_hint:
        # §17 — Haiku 가 만든 한두 줄 페르소나. 사장님이 다음 답장 만들 때 컨텍스트.
        lines.append(f"AI 분석: {persona_hint}")
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


def _coerce_v2_suggestions(parsed: dict) -> dict:
    """v2 응답 검증 + coerce. 항상 정상적인 v2 dict 반환 — fallback_default 로 안전 강등.

    입력: Claude 가 반환한 JSON dict (이상적으로 v2 schema)
    출력: {scenario, scenario_confidence, scenario_reason, suggestions:[3 obj]}

    안전망:
      - scenario 가 enum 아님 → fallback_default
      - confidence < 0.6 → fallback_default (사장님 결정 19:00)
      - intent_key 가 해당 시나리오에 정의 안 됨 → fallback_default 로 전체 강등
      - suggestions 3개 미만 → fallback_default 의 3 intent 로 padding
    """
    # 1) scenario
    scenario = str(parsed.get("scenario", "")).strip()
    if scenario not in VALID_SCENARIOS:
        scenario = "fallback_default"

    # 2) confidence
    try:
        confidence = float(parsed.get("scenario_confidence", 0))
    except (TypeError, ValueError):
        confidence = 0.0
    confidence = max(0.0, min(1.0, confidence))

    # 3) confidence floor — < 0.6 면 fallback_default 로 강등
    if confidence < SCENARIO_CONFIDENCE_FLOOR and scenario != "fallback_default":
        print(f"[prepare-reply] confidence {confidence:.2f} < {SCENARIO_CONFIDENCE_FLOOR} → fallback_default 강등 (원래 scenario={scenario})")
        scenario = "fallback_default"

    reason = str(parsed.get("scenario_reason", "")).strip()[:200]

    # 4) suggestions 추출 + 검증
    raw_suggs = parsed.get("suggestions")
    valid_intents = _INTENT_LABEL_MAP[scenario]  # {intent_key: label}

    coerced: list[dict] = []
    if isinstance(raw_suggs, list):
        seen_keys: set[str] = set()
        for s in raw_suggs[:3]:
            if not isinstance(s, dict):
                continue
            intent_key = str(s.get("intent_key", "")).strip()
            if intent_key not in valid_intents or intent_key in seen_keys:
                continue
            seen_keys.add(intent_key)
            text = str(s.get("text", "")).strip()
            why  = str(s.get("why", "")).strip()[:200]
            coerced.append({
                "intent_key": intent_key,
                "label":      valid_intents[intent_key],   # ← 우리 정의로 강제 overwrite (모델이 다른 라벨 박았어도)
                "text":       text,
                "why":        why,
            })

    # 5) 3개 미만이면 — 시나리오의 누락 intent 로 padding (text 비움)
    if len(coerced) < 3:
        seen_keys = {s["intent_key"] for s in coerced}
        for intent in INTENT_POOL_V1[scenario]:
            if intent["intent_key"] not in seen_keys:
                coerced.append({
                    "intent_key": intent["intent_key"],
                    "label":      intent["label"],
                    "text":       "",  # 빈 답변 — 안드로이드가 표시 안 함
                    "why":        "padded — model 이 이 intent 답변 안 만듦",
                })
                if len(coerced) >= 3:
                    break

    return {
        "scenario":            scenario,
        "scenario_confidence": round(confidence, 3),
        "scenario_reason":     reason,
        "suggestions":         coerced[:3],
    }


def _repair_json_text(raw_text: str) -> Optional[str]:
    """JSON 흔한 깨짐 복구: 코드블럭 제거 / trailing garbage 컷 / trailing comma /
    닫는 괄호 누락 보완. 실패 시 None.

    §48 ② 빈답변 버그 fix — 1차 _parse_json_object 실패 시 보수 시도.
    """
    import re as _re
    text = (raw_text or "").strip()
    if not text:
        return None
    if text.startswith("```"):
        text = text.split("\n", 1)[1] if "\n" in text else text[3:]
        if text.endswith("```"):
            text = text[:-3].rstrip()
    brace_idx = text.find("{")
    if brace_idx == -1:
        return None
    text = text[brace_idx:]
    # 마지막 } 까지로 컷 (그 뒤 garbage 자르기)
    last_brace = text.rfind("}")
    if last_brace > 0:
        # 단 } 뒤에 valid 닫는 ] 가 더 있을 수도 있어 보수적으로 } 다음 ] 까지
        tail = text[last_brace + 1:].strip()
        if tail and tail[0] in "]}":
            text = text[:last_brace + 1 + len(tail.split()[0])]
        else:
            text = text[:last_brace + 1]
    # trailing comma 제거
    text = _re.sub(r',\s*([\}\]])', r'\1', text)
    # 누락 닫는 괄호 보완
    opens = text.count("{") - text.count("}")
    if opens > 0:
        text = text + ("}" * opens)
    opens_arr = text.count("[") - text.count("]")
    if opens_arr > 0:
        text = text + ("]" * opens_arr)
    return text


def _extract_suggestion_texts_fallback(raw_text: str) -> list[str]:
    """JSON 완전 깨졌을 때 정규식으로 "text":"..." 값만 추출 (최후 보루).

    §48 ② 의 "최소 1개라도 유효 답변 보장" 요구사항용.
    """
    import re as _re
    if not raw_text:
        return []
    matches = _re.findall(r'"text"\s*:\s*"((?:[^"\\]|\\.)*)"', raw_text)
    out: list[str] = []
    for m in matches[:3]:
        try:
            decoded = json.loads(f'"{m}"')
            if isinstance(decoded, str) and decoded.strip():
                out.append(decoded.strip())
        except json.JSONDecodeError:
            if m.strip():
                out.append(m.strip())
    return out


_HARDCODED_FALLBACK_TEXT = "안녕하세요. 문의 주신 내용 확인하고 빠르게 답변드릴게요 ^^"


def _build_emergency_v2(texts: list[str], reason: str) -> dict:
    """fallback_default 인텐트 3개 + 회수된 텍스트로 v2 dict 구성.

    텍스트 0개면 1번째 인텐트에 _HARDCODED_FALLBACK_TEXT 박아 최소 1개 보장.
    """
    intents = INTENT_POOL_V1["fallback_default"]
    suggestions = []
    for i, intent in enumerate(intents):
        text = texts[i] if i < len(texts) else ""
        if i == 0 and not text:
            text = _HARDCODED_FALLBACK_TEXT
            why = "model output 깨짐 — 임시 안내 답변 (최소 1개 보장)"
        elif text:
            why = "recovered from malformed JSON"
        else:
            why = "padded — model output 깨짐"
        suggestions.append({
            "intent_key": intent["intent_key"],
            "label":      intent["label"],
            "text":       text,
            "why":        why,
        })
    return {
        "scenario":            "fallback_default",
        "scenario_confidence": 0.0,
        "scenario_reason":     reason,
        "suggestions":         suggestions,
    }


def _parse_suggestions_v2(raw_text: str) -> dict:
    """v2 응답 파싱 — Claude/Gemini raw text → v2 dict.

    §48 ② 빈답변 버그 fix — 4단계 복구로 최소 1개 유효 답변 보장:
    1차: _parse_json_object → _coerce_v2_suggestions (정상 케이스)
    2차: _repair_json_text 후 재시도 (trailing comma·괄호 누락 등)
    3차: 정규식으로 "text":"..." 만 추출 → emergency v2
    4차: 최후 hardcoded fallback (1번째 인텐트에 generic 한국어 답변)
    """
    # 1차
    try:
        parsed = _parse_json_object(raw_text)
        v2 = _coerce_v2_suggestions(parsed)
        # 최종 sanity — text 가 다 비어있고 fallback_default 면 추가 복구 시도
        non_empty = sum(1 for s in v2["suggestions"] if (s.get("text") or "").strip())
        if non_empty > 0:
            return v2
        # 다 빈값 = 모델이 진짜 빈 답변 보낸 경우. 추가 시도.
        print(f"[prepare-reply] 1차 파싱은 됐으나 text 전부 빔 — 복구 시도")
    except (ValueError, KeyError) as e:
        print(f"[prepare-reply] 1차 파싱 실패: {type(e).__name__}: {e}")

    # 2차: JSON repair
    repaired = _repair_json_text(raw_text)
    if repaired:
        try:
            parsed = json.loads(repaired)
            if isinstance(parsed, dict):
                v2 = _coerce_v2_suggestions(parsed)
                non_empty = sum(1 for s in v2["suggestions"] if (s.get("text") or "").strip())
                if non_empty > 0:
                    print(f"[prepare-reply] 2차 JSON repair 통과 ({non_empty}개 유효)")
                    return v2
        except (json.JSONDecodeError, ValueError, KeyError) as e:
            print(f"[prepare-reply] 2차 repair 실패: {type(e).__name__}: {e}")

    # 3차: 정규식 부분추출
    texts = _extract_suggestion_texts_fallback(raw_text)
    if texts:
        print(f"[prepare-reply] 3차 정규식 부분추출 — {len(texts)}개 회수")
        return _build_emergency_v2(texts, "recovered from malformed JSON (regex fallback)")

    # 4차: 최후 hardcoded fallback
    print(f"[prepare-reply] 4차 hardcoded fallback (raw len={len(raw_text or '')})")
    return _build_emergency_v2([], "model output not parseable — hardcoded fallback (최소 1개 답변 보장)")


async def call_claude_for_suggestions_with_meta(
    req: PrepareReplyRequest,
) -> tuple[dict, "anthropic.types.Message"]:
    """Claude Sonnet 4.6 호출 → (v2 dict, raw response) 반환.

    v2 dict 구조: {scenario, scenario_confidence, scenario_reason, suggestions:[3 obj]}

    system 을 4 block 으로 분리 + 각 block 에 cache_control 박아서 prompt caching
    적극 활용. 같은 사장님이 5분 내 재호출 시 ~90% cache 적중 (입력 비용 1/10).
    """
    # §16 — Tone RAG 통합. block C 가 RAG retrieved 또는 ownerToneSamples fallback.
    system_blocks = await build_system_blocks_async(
        owner_tone_samples=req.ownerToneSamples or [],
        latest_msg=req.latestMessage or "",
        device_id="owner-anon",  # 사장님 1인 운영. 멀티유저 시 안드가 device_id 보낼 것.
    )

    # §17 — 페르소나 hint. 캐시된 게 있으면 user msg 의 [고객 정보] 영역에 inject.
    # 없거나 stale 이면 백그라운드 Haiku 생성 트리거 (다음 호출부터 활용).
    persona_ctx = _persona_ctx_from_prepare_req(req)
    persona_hint = trigger_persona_refresh_if_needed(req.phone, persona_ctx)
    user_msg = build_user_message(req, persona_hint=persona_hint)

    response = await claude_client.messages.create(
        model=CLAUDE_MODEL,
        max_tokens=CLAUDE_MAX_TOKENS,
        timeout=CLAUDE_TIMEOUT,
        system=system_blocks,
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
    v2 = _parse_suggestions_v2(raw_text)
    return v2, response


# ============================================================================
# §49 — Gemini 2.5 Flash A/B (사장님 2026-06-03 SYNC#49 지시)
# 동일 v2 schema (scenario + suggestions[3]) 강제 → 앱 수정 0.
# Gemini structured output (response_schema) 으로 JSON 빈답변 폴백 X.
# 호출: prepare-reply?model=gemini → 이 경로. 기본은 sonnet 유지.
# ============================================================================

# Gemini response_schema (OpenAPI subset, "type" 대문자 — Gemini 명세)
_GEMINI_V2_SUGGESTIONS_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "scenario": {
            "type": "STRING",
            "enum": list(VALID_SCENARIOS),
        },
        "scenario_confidence": {"type": "NUMBER"},
        "scenario_reason": {"type": "STRING"},
        "suggestions": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "intent_key": {"type": "STRING"},
                    "label":      {"type": "STRING"},
                    "text":       {"type": "STRING"},
                    "why":        {"type": "STRING"},
                },
                "required": ["intent_key", "label", "text", "why"],
            },
        },
    },
    "required": ["scenario", "scenario_confidence", "scenario_reason", "suggestions"],
}


async def _call_gemini_for_suggestions_raw(
    system_text: str, user_msg: str,
) -> tuple[str, dict]:
    """Gemini 2.5 Flash 호출 — response_schema 로 JSON 강제.

    Returns: (raw_json_text, usage_metadata dict)
    """
    if not GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY env var not set")

    url = (
        f"https://generativelanguage.googleapis.com/v1beta/models/"
        f"{GEMINI_MODEL}:generateContent?key={GEMINI_API_KEY}"
    )
    payload = {
        "systemInstruction": {"parts": [{"text": system_text}]},
        "contents": [{"role": "user", "parts": [{"text": user_msg}]}],
        "generationConfig": {
            "temperature": 0.7,
            "topP": 0.95,
            "maxOutputTokens": 2048,
            "responseMimeType": "application/json",
            "responseSchema": _GEMINI_V2_SUGGESTIONS_SCHEMA,
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
    raw_json = "".join(p.get("text", "") for p in parts).strip()
    if not raw_json:
        finish = candidates[0].get("finishReason", "?")
        raise RuntimeError(f"Gemini empty text (finishReason={finish}): {str(data)[:300]}")

    usage_meta = data.get("usageMetadata") or {}
    return raw_json, usage_meta


async def call_gemini_for_suggestions_with_meta(
    req: PrepareReplyRequest,
) -> tuple[dict, dict]:
    """Gemini 경로 — Sonnet 과 동일 v2 dict 반환.

    Returns: (v2 dict, usage_meta dict)
    usage_meta = {promptTokenCount, candidatesTokenCount, totalTokenCount, model}
    """
    # Sonnet 과 동일한 시스템 블록 사용 (RAG·페르소나 inject 포함)
    # → 4 block 을 하나 string 으로 합쳐서 systemInstruction 으로 전달
    system_blocks = await build_system_blocks_async(
        owner_tone_samples=req.ownerToneSamples or [],
        latest_msg=req.latestMessage or "",
        device_id="owner-anon",
    )
    system_text = "\n\n".join(b.get("text", "") for b in system_blocks if b.get("text"))

    persona_ctx = _persona_ctx_from_prepare_req(req)
    persona_hint = trigger_persona_refresh_if_needed(req.phone, persona_ctx)
    user_msg = build_user_message(req, persona_hint=persona_hint)

    raw_json, usage_meta = await _call_gemini_for_suggestions_raw(system_text, user_msg)
    v2 = _parse_suggestions_v2(raw_json)  # 견고화된 파서 (response_schema 로 거의 안 깨지지만 보험)
    usage_meta["model"] = GEMINI_MODEL
    return v2, usage_meta


def _log_gemini_suggestions_usage(usage_meta: dict, latency_sec: float) -> None:
    """Gemini 호출 사용량 → llm_usage_log 기록 (Sonnet 경로의 _log_llm_usage_from_response 와 평행)."""
    try:
        prompt_tokens = int(usage_meta.get("promptTokenCount", 0) or 0)
        completion_tokens = int(usage_meta.get("candidatesTokenCount", 0) or 0)
        log_llm_usage(
            endpoint="prepare-reply",
            model=GEMINI_MODEL,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            cache_read_tokens=0,
            cache_write_tokens=0,
        )
    except Exception as e:
        print(f"[prepare-reply/gemini] usage 로깅 실패: {type(e).__name__}: {e}")


# ============================================================================
# 백그라운드 처리
# ============================================================================
async def generate_and_cache(req: PrepareReplyRequest, model: str = "sonnet") -> None:
    """prepare-reply 백그라운드 처리.

    model="sonnet" (기본) → Sonnet 4.6 + Anthropic SDK (기존 경로)
    model="gemini"        → Gemini 2.5 Flash + response_schema (§49 A/B 비교)
    """
    phone = req.phone
    start_ms = _now_ms()
    try:
        check_rate_limit(phone)

        if model == "gemini":
            v2, usage_meta = await call_gemini_for_suggestions_with_meta(req)
            latency_sec = (_now_ms() - start_ms) / 1000.0
            _log_gemini_suggestions_usage(usage_meta, latency_sec)
            db_set_ready(phone, v2)
            print(
                f"[ready/gemini] {phone} scenario={v2['scenario']} conf={v2['scenario_confidence']} "
                f"intents={[s['intent_key'] for s in v2['suggestions']]} "
                f"(in={usage_meta.get('promptTokenCount',0)} "
                f"out={usage_meta.get('candidatesTokenCount',0)} "
                f"latency={latency_sec:.1f}s)"
            )
        else:
            # 기본 = Sonnet (기존 경로)
            v2, response = await call_claude_for_suggestions_with_meta(req)
            latency_sec = (_now_ms() - start_ms) / 1000.0
            log_usage(phone, "prepare-reply", response)
            _log_llm_usage_from_response("prepare-reply", response)
            db_set_ready(phone, v2)
            usage = response.usage
            print(
                f"[ready/sonnet] {phone} scenario={v2['scenario']} conf={v2['scenario_confidence']} "
                f"intents={[s['intent_key'] for s in v2['suggestions']]} "
                f"(in={getattr(usage,'input_tokens',0)} "
                f"cache_read={getattr(usage,'cache_read_input_tokens',0)} "
                f"out={getattr(usage,'output_tokens',0)} "
                f"latency={latency_sec:.1f}s)"
            )
    except asyncio.CancelledError:
        print(f"[cancelled] {phone}")
        raise
    except HTTPException as e:
        print(f"[rate-limit] {phone}: {e.detail}")
        db_set_missing(phone)
    except Exception as e:
        print(f"[failed/{model}] {phone}: {type(e).__name__}: {e}")
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


PREPARE_REPLY_DEFAULT_MODEL = os.environ.get("PREPARE_REPLY_MODEL", "gemini").lower()
"""prepare-reply 기본 모델. 사장님 2026-06-03 톤 비교 후 'gemini' 로 전환.

ENV 토글: launchd plist 의 EnvironmentVariables 에 PREPARE_REPLY_MODEL=sonnet 박으면
즉시 Sonnet 으로 되돌림 (롤백 안전망). 쿼리 파라미터 ?model=... 가 박히면 그게 우선.
"""


@app.post("/prepare-reply")
async def prepare_reply(req: PrepareReplyRequest, model: Optional[str] = None):
    """추천 답변 생성 백그라운드 트리거.

    §49 A/B + 사장님 톤 판정 결과 → 기본 = Gemini 2.5 Flash (속도·비용·JSON 안정성).
    - 쿼리 파라미터 ?model=sonnet 박으면 Sonnet 경로 (수동 override)
    - ENV PREPARE_REPLY_MODEL=sonnet 박으면 default 가 sonnet 으로 되돌림 (롤백)
    - 응답 schema 는 동일 v2 (앱 수정 0)
    """
    chosen_model = (model or PREPARE_REPLY_DEFAULT_MODEL).lower()
    if chosen_model not in ("sonnet", "gemini"):
        raise HTTPException(400, f"model must be 'sonnet' or 'gemini', got {chosen_model!r}")
    if chosen_model == "gemini" and not GEMINI_API_KEY:
        # graceful — Gemini 키 없으면 자동 Sonnet 폴백 (서비스 무중단)
        print(f"[prepare-reply] GEMINI_API_KEY 미설정 → Sonnet 자동 폴백")
        chosen_model = "sonnet"

    db_set_generating(req.phone, req.latestMessage, req.latestMessageReceivedAtMs)

    # 진행 중인 같은 phone 태스크가 있으면 취소
    old = _inflight_tasks.get(req.phone)
    if old is not None and not old.done():
        old.cancel()

    task = asyncio.create_task(generate_and_cache(req, model=chosen_model))
    _inflight_tasks[req.phone] = task
    return {"ok": True, "model": chosen_model}


@app.get("/suggestions/{phone}")
async def get_suggestions(phone: str):
    """안드로이드 ChatScreen 이 호출. v2 schema (scenario + 의도 분화).

    응답 (READY 상태):
    {
      "status": "ready",
      "phone": "...",
      "basedOnMessage": "...",
      "basedOnReceivedAtMs": 0,
      "generatedAtMs": 0,
      "scenario": "price_inquiry",
      "scenario_confidence": 0.78,
      "scenario_reason": "...",
      "suggestions": [
        {"intent_key":"quote","label":"💰 견적 안내","text":"...","why":"..."},
        ...3개
      ]
    }

    안드로이드 19:00 작업에서 v1 (suggestions:[str]) + v2 (suggestions:[obj]) 둘 다 parse 가능.
    """
    row = db_get(phone)
    if row is None:
        return {"status": "missing"}

    status = row["status"]
    if status in ("generating", "missing"):
        return {"status": status}

    # ready — v2 dict 을 suggestions_json 안에 박아둠
    stored = row["suggestions"] or {}

    # 옛 데이터(v1: list[str]) 와의 호환 — 이전에 박힌 캐시
    if isinstance(stored, list):
        # 옛 string list 였으면 v2 모양으로 wrap (fallback_default 시나리오)
        v2_suggs = []
        for i, s in enumerate(stored[:3]):
            it = INTENT_POOL_V1["fallback_default"][i]
            v2_suggs.append({
                "intent_key": it["intent_key"],
                "label":      it["label"],
                "text":       str(s),
                "why":        "legacy v1 cache",
            })
        return {
            "status":              "ready",
            "phone":               phone,
            "basedOnMessage":      row["basedOnMessage"],
            "basedOnReceivedAtMs": row["basedOnReceivedAtMs"],
            "generatedAtMs":       row["generatedAtMs"],
            "scenario":            "fallback_default",
            "scenario_confidence": 0.0,
            "scenario_reason":     "v1 legacy cache (cowork 가 v2 로 박기 전 데이터)",
            "suggestions":         v2_suggs,
        }

    # v2 dict — 정상 케이스
    return {
        "status":              "ready",
        "phone":               phone,
        "basedOnMessage":      row["basedOnMessage"],
        "basedOnReceivedAtMs": row["basedOnReceivedAtMs"],
        "generatedAtMs":       row["generatedAtMs"],
        "scenario":            stored.get("scenario", "fallback_default"),
        "scenario_confidence": stored.get("scenario_confidence", 0.0),
        "scenario_reason":     stored.get("scenario_reason", ""),
        "suggestions":         stored.get("suggestions", []),
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
# §16 — Tone RAG (4단계 킬러콘텐츠) — 사장님 sent SMS 풀에서 의미 유사한 답변 retrieval
# ─────────────────────────────────────────────────────────────────────────────
# 핵심:
#   - 안드로이드가 사장님 sent SMS 들을 batch upload → 서버가 임베딩 + 저장
#   - prepare-reply 호출 시 latestMessage 를 임베딩 → cosine top-10 retrieve
#   - retrieved top-10 을 build_system_blocks 의 block C 위치에 inject
#   - 효과: "사장님이 비슷한 상황에서 친 진짜 답변" 을 LLM 에 컨텍스트로 → 톤 정확도 ↑
#
# 의존성 (graceful degrade — 없으면 RAG 비활성화, 기존 ownerToneSamples 사용):
#   - FlagEmbedding (BAAI/bge-m3, 1024 dim, multilingual, 한국어 강함)
#   - sqlite-vec (vec0 virtual table, cosine 거리 KNN)
#
# 안드 19:30 사양:
#   - text 5자 미만 / 500자 초과 제외 (학습 가치 낮은 자동 답장/스팸 등)
#   - dedup: (device_id, text_hash) UNIQUE (text_hash = sha256(text))
#   - device_id = "owner-anon" 하드코딩 (사장님 1인 운영 단계)
# ============================================================================
import hashlib  # noqa: E402

# 임베딩 모델 — lazy load. 첫 호출 시 ~2GB download (HuggingFace cache).
_bge_model = None
_bge_lock: Optional[asyncio.Lock] = None  # event loop 안에서만 생성
_bge_dim = 1024  # bge-m3 dense vector dim
_bge_available = True  # graceful degrade flag


def _get_bge_lock() -> asyncio.Lock:
    global _bge_lock
    if _bge_lock is None:
        _bge_lock = asyncio.Lock()
    return _bge_lock


async def get_bge_model():
    """bge-m3 lazy load. 실패 시 None 반환 + _bge_available=False."""
    global _bge_model, _bge_available
    if _bge_model is not None or not _bge_available:
        return _bge_model
    async with _get_bge_lock():
        if _bge_model is not None:
            return _bge_model
        try:
            # FlagEmbedding 의 BGEM3FlagModel — bge-m3 공식 wrapper
            from FlagEmbedding import BGEM3FlagModel
            print("[tone-rag] loading bge-m3 (첫 로딩은 1~2분 — 모델 download)...")
            # CPU only (Mac mini 는 GPU 없음). use_fp16=False → 정확도 우선.
            model = await asyncio.to_thread(
                BGEM3FlagModel, "BAAI/bge-m3", use_fp16=False, device="cpu"
            )
            _bge_model = model
            print("[tone-rag] bge-m3 loaded ✓")
        except Exception as e:
            _bge_available = False
            print(f"[tone-rag] bge-m3 load 실패 (RAG 비활성화): {type(e).__name__}: {e}")
            return None
    return _bge_model


async def encode_texts_async(texts: list[str]) -> Optional[list[list[float]]]:
    """texts 를 bge-m3 dense vector list 로 encode. 실패 시 None."""
    model = await get_bge_model()
    if model is None:
        return None
    try:
        # BGEM3FlagModel.encode 는 {"dense_vecs": np.array, ...} 반환
        result = await asyncio.to_thread(
            model.encode, texts, batch_size=16, max_length=512, return_dense=True
        )
        vecs = result.get("dense_vecs") if isinstance(result, dict) else result
        if vecs is None:
            return None
        # numpy → list[list[float]]
        return [list(map(float, v)) for v in vecs]
    except Exception as e:
        print(f"[tone-rag] encode 실패: {type(e).__name__}: {e}")
        return None


# sqlite-vec extension 의존성 제거 — Mac system Python 이 enable_load_extension
# 비활성화 빌드라 사용 불가. numpy 로 application 단 cosine 검색 (50K row 미만
# 이면 ~수십ms). numpy 는 FlagEmbedding 의존성으로 이미 install 됨.
_numpy_available = True
try:
    import numpy as _np
except ImportError:
    _numpy_available = False
    print("[tone-rag] numpy 없음 — RAG 비활성화")

# _vec_available 은 하위 호환용 alias — 실질적으로 _numpy_available 과 동일
_vec_available = _numpy_available


def _text_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _vec_to_blob(vec: list[float]) -> bytes:
    """list[float] → float32 BLOB (owner_tone.embedding 컬럼에 저장용)."""
    import struct
    return struct.pack(f"{len(vec)}f", *vec)


def _blob_to_vec(blob: bytes):
    """float32 BLOB → numpy array (cosine 검색용)."""
    if not _numpy_available:
        return None
    import struct
    n = len(blob) // 4
    return _np.array(struct.unpack(f"{n}f", blob), dtype=_np.float32)


def _filter_tone_text(text: str) -> Optional[str]:
    """학습 가치 있는 텍스트만 통과. 안드 사양: 5자 미만 / 500자 초과 제외."""
    if not text:
        return None
    t = text.strip()
    if len(t) < 5 or len(t) > 500:
        return None
    return t


def count_owner_tone_pool(device_id: str) -> int:
    with db_conn() as conn:
        row = conn.execute(
            "SELECT COUNT(*) AS c FROM owner_tone WHERE device_id=?",
            (device_id,),
        ).fetchone()
    return row["c"] if row else 0


async def retrieve_rag_tone_samples(
    device_id: str, query_text: str, top_k: int = 10
) -> Optional[list[str]]:
    """latest_msg 를 bge-m3 로 임베딩 + numpy cosine top-k retrieve.

    None 반환 = RAG 비활성화 (의존성 없음 / 모델 load 실패 / pool 비어있음 등).
    이 경우 caller 가 기존 ownerToneSamples 로 fallback.

    50K row 미만이면 ~수십ms (numpy 벡터 연산). 그 이상이면 hnswlib 이나 별도 인덱스 도입.
    """
    if not _bge_available or not _numpy_available:
        return None
    if not query_text or count_owner_tone_pool(device_id) == 0:
        return None

    # 1) query 임베딩
    vecs = await encode_texts_async([query_text])
    if not vecs:
        return None

    query_vec = _np.array(vecs[0], dtype=_np.float32)
    query_norm = _np.linalg.norm(query_vec)
    if query_norm < 1e-8:
        return None
    query_unit = query_vec / query_norm

    # 2) DB 에서 embedding 가진 row 전부 (device 필터)
    try:
        with db_conn() as conn:
            rows = conn.execute(
                """
                SELECT text, embedding
                FROM owner_tone
                WHERE device_id=? AND embedding IS NOT NULL
                """,
                (device_id,),
            ).fetchall()
    except Exception as e:
        print(f"[tone-rag] DB 조회 실패: {type(e).__name__}: {e}")
        return None

    if not rows:
        return None

    # 3) numpy cosine — batched matrix 곱셈으로 빠름
    try:
        all_vecs = []
        all_texts = []
        for r in rows:
            v = _blob_to_vec(r["embedding"])
            if v is None or v.shape[0] != _bge_dim:
                continue
            n = _np.linalg.norm(v)
            if n < 1e-8:
                continue
            all_vecs.append(v / n)
            all_texts.append(r["text"])

        if not all_vecs:
            return None

        # matrix (N, dim) @ unit (dim,) = (N,) similarity
        mat = _np.stack(all_vecs)
        sims = mat @ query_unit  # higher = more similar (cosine)

        # top-k indices
        top_idx = _np.argsort(-sims)[:top_k]
        seen: set[str] = set()
        out: list[str] = []
        for i in top_idx:
            t = all_texts[int(i)]
            if t in seen:
                continue
            seen.add(t)
            out.append(t)
            if len(out) >= top_k:
                break
        return out if out else None
    except Exception as e:
        print(f"[tone-rag] numpy cosine 계산 실패: {type(e).__name__}: {e}")
        return None


class ToneUploadMessage(BaseModel):
    text: str
    timestamp_ms: int = 0


class ToneBatchUploadRequest(BaseModel):
    device_id: str = "owner-anon"
    messages: list[ToneUploadMessage] = Field(default_factory=list)


@app.post("/api/owner-tone/batch-upload")
async def owner_tone_batch_upload(req: ToneBatchUploadRequest) -> dict:
    """§16 — 사장님 sent SMS 풀 batch upload.

    Request:
      {device_id: "owner-anon", messages: [{text, timestamp_ms}, ...]}

    처리:
      1. 5자 미만 / 500자 초과 / 빈 텍스트 제외
      2. 동일 (device_id, text_hash) 중복 SKIP
      3. 새 text 들 batch embedding (bge-m3)
      4. INSERT owner_tone + owner_tone_vec 트랜잭션
      5. 전체 풀 count 반환

    Response:
      {received, stored, total_in_pool, embeddings_available: bool}
    """
    device_id = (req.device_id or "owner-anon").strip()

    # 0) 필터링 + dedup 후보 추출
    filtered: list[ToneUploadMessage] = []
    for m in (req.messages or []):
        t = _filter_tone_text(m.text)
        if t is not None:
            filtered.append(ToneUploadMessage(text=t, timestamp_ms=m.timestamp_ms))

    received = len(req.messages or [])

    if not filtered:
        return {
            "received":              received,
            "stored":                0,
            "total_in_pool":         count_owner_tone_pool(device_id),
            "embeddings_available":  _bge_available and _vec_available,
        }

    # 1) 이미 있는 hash + embedding 상태 조회 (dedup + backfill 판정)
    hashes = [_text_hash(m.text) for m in filtered]
    existing: dict[str, dict] = {}  # text_hash → {id, has_embedding}
    with db_conn() as conn:
        for i in range(0, len(hashes), 500):
            chunk = hashes[i:i + 500]
            placeholders = ",".join("?" * len(chunk))
            rows = conn.execute(
                f"""
                SELECT id, text_hash,
                       (embedding IS NOT NULL) AS has_emb
                FROM owner_tone
                WHERE device_id=? AND text_hash IN ({placeholders})
                """,
                [device_id, *chunk],
            ).fetchall()
            for r in rows:
                existing[r["text_hash"]] = {
                    "id": r["id"],
                    "has_emb": bool(r["has_emb"]),
                }

    # 2) 새 INSERT 후보 vs backfill 후보 (이미 있지만 embedding NULL) 분리
    new_msgs: list[tuple[str, str, int]] = []      # (text, hash, ts)
    backfill_rows: list[tuple[int, str]] = []      # (id, text)
    for m, h in zip(filtered, hashes):
        if h in existing:
            if not existing[h]["has_emb"]:
                backfill_rows.append((existing[h]["id"], m.text))
        else:
            new_msgs.append((m.text, h, m.timestamp_ms))

    if not new_msgs and not backfill_rows:
        return {
            "received":              received,
            "stored":                0,
            "backfilled":            0,
            "total_in_pool":         count_owner_tone_pool(device_id),
            "embeddings_available":  _bge_available and _numpy_available,
        }

    # 3) batch embedding — 새 + backfill 합쳐서 한 번에
    all_texts = [t[0] for t in new_msgs] + [t[1] for t in backfill_rows]
    embeddings = await encode_texts_async(all_texts) if all_texts else None
    embeddings_available = _bge_available and _numpy_available and (embeddings is not None)

    # 4) INSERT 새 row + UPDATE backfill
    now = _now_ms()
    stored = 0
    backfilled = 0
    try:
        with db_conn() as conn:
            conn.execute("BEGIN")
            # 4a) 새 row INSERT (embedding 같이)
            for idx, (text, h, ts) in enumerate(new_msgs):
                try:
                    emb_blob = (
                        _vec_to_blob(embeddings[idx]) if embeddings else None
                    )
                    conn.execute(
                        """
                        INSERT INTO owner_tone
                            (device_id, text, text_hash, timestamp_ms, created_at_ms, embedding)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        (device_id, text, h, ts, now, emb_blob),
                    )
                    stored += 1
                except sqlite3.IntegrityError:
                    pass  # race condition

            # 4b) backfill (기존 row 의 embedding NULL → UPDATE)
            if embeddings:
                offset = len(new_msgs)
                for j, (row_id, _text) in enumerate(backfill_rows):
                    try:
                        conn.execute(
                            "UPDATE owner_tone SET embedding=? WHERE id=?",
                            (_vec_to_blob(embeddings[offset + j]), row_id),
                        )
                        backfilled += 1
                    except Exception:
                        pass
            conn.commit()
    except Exception as e:
        print(f"[tone-rag] batch-upload INSERT/UPDATE 실패: {type(e).__name__}: {e}")

    return {
        "received":              received,
        "stored":                stored,
        "backfilled":            backfilled,
        "total_in_pool":         count_owner_tone_pool(device_id),
        "embeddings_available":  embeddings_available,
    }


@app.get("/api/owner-tone/pool-stats")
async def owner_tone_pool_stats(device_id: str = "owner-anon") -> dict:
    """풀 통계 (안드 Settings 카드의 카운트 표시용)."""
    # embedding 가진 row 수 (RAG 실효 카운트)
    with db_conn() as conn:
        emb_row = conn.execute(
            "SELECT COUNT(*) AS c FROM owner_tone WHERE device_id=? AND embedding IS NOT NULL",
            (device_id,),
        ).fetchone()
    embedded_count = emb_row["c"] if emb_row else 0

    return {
        "device_id":            device_id,
        "total_in_pool":        count_owner_tone_pool(device_id),
        "embedded_count":       embedded_count,
        "embeddings_available": _bge_available and _numpy_available,
    }


# ============================================================================
# §17 — Customer Personas (5단계 킬러콘텐츠)
# ─────────────────────────────────────────────────────────────────────────────
# phone 별 한두 줄 요약 ("이 고객은 ...") 을 Haiku 로 자동 생성 + 24h cache.
# prepare-reply 호출 시 build_user_message 의 [고객 정보] 영역에 inject.
# stale (24h 지남) 시 백그라운드 refresh — 사장님 요청 차단 X.
# 사장님이 명시한 사양 그대로:
#   - customer_personas 테이블 + 24h cache + Haiku 자동 생성
#   - GET /api/customer-persona/{phone}
#   - prepare-reply prompt 의 customer hint 영역에 페르소나 inject
# ============================================================================

PERSONA_CACHE_TTL_MS = 24 * 60 * 60 * 1000   # 24h
PERSONA_MIN_MESSAGES = 3                      # 메시지 3건 미만이면 페르소나 생성 안 함
PERSONA_GENERATION_TIMEOUT = 60.0
PERSONA_MAX_TOKENS = 200                      # 한두 줄 출력이라 짧게

# 백그라운드 페르소나 생성 태스크 — phone 당 1개 (중복 방지)
_persona_inflight: dict[str, asyncio.Task] = {}


PERSONA_SYSTEM_PROMPT = """너는 줄눈/타일 시공 사장님의 비서다. 주어진 고객과의 대화·메모·통화 기록을 보고
"이 고객은 누구이고 어떤 상태인가" 를 한두 문장으로 요약한다.

규칙:
- 정보 우선순위: 거주지/시공 부위 → 시공 관심도 → 현재 단계(문의/견적/예약/시공/완료) → 특이사항(예산 민감/타사 비교/긴급 등)
- 명시되지 않은 정보 추측 X
- 가격·날짜·시간은 대화에 명시된 것만
- 너무 일반적 형용사 ("친절하다", "좋은 분") X — 구체적 사실만
- 사장님이 다음 답장 만들 때 도움이 되는 "한 줄 요약" 이어야 함

답 형식 — 반드시 지켜라:
- 정확히 1~2 문장
- "이 고객은 ..." 로 시작
- 출력은 다듬어진 문장만. JSON·따옴표·코드블럭·태그 절대 X
"""


def _persona_get_cached(phone: str) -> Optional[dict]:
    """캐시된 페르소나 조회. stale 여부 포함 반환. 없으면 None."""
    with db_conn() as conn:
        row = conn.execute(
            """
            SELECT persona_text, model_used, source_message_count,
                   generated_at_ms, last_refresh_started_ms
            FROM customer_personas WHERE phone=?
            """,
            (phone,),
        ).fetchone()
    if not row:
        return None
    age_ms = _now_ms() - row["generated_at_ms"]
    return {
        "phone":                phone,
        "persona_text":         row["persona_text"],
        "model_used":           row["model_used"],
        "source_message_count": row["source_message_count"],
        "generated_at_ms":      row["generated_at_ms"],
        "age_ms":               age_ms,
        "stale":                age_ms > PERSONA_CACHE_TTL_MS,
        "last_refresh_started_ms": row["last_refresh_started_ms"],
    }


def _persona_save(
    phone: str, persona_text: str, model: str, source_count: int
) -> None:
    now = _now_ms()
    with db_conn() as conn:
        conn.execute(
            """
            INSERT INTO customer_personas
                (phone, persona_text, model_used, source_message_count, generated_at_ms)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(phone) DO UPDATE SET
                persona_text         = excluded.persona_text,
                model_used           = excluded.model_used,
                source_message_count = excluded.source_message_count,
                generated_at_ms      = excluded.generated_at_ms,
                last_refresh_started_ms = NULL
            """,
            (phone, persona_text, model, source_count, now),
        )


def _persona_mark_refresh_started(phone: str) -> None:
    """백그라운드 생성 시작 마킹 — 다른 호출이 중복 생성 안 하게."""
    with db_conn() as conn:
        conn.execute(
            """
            INSERT INTO customer_personas
                (phone, persona_text, model_used, source_message_count, generated_at_ms, last_refresh_started_ms)
            VALUES (?, '', '', 0, 0, ?)
            ON CONFLICT(phone) DO UPDATE SET
                last_refresh_started_ms = excluded.last_refresh_started_ms
            """,
            (phone, _now_ms()),
        )


def _persona_build_user_input(ctx: "ConversationContext") -> Optional[str]:
    """페르소나 생성용 user message. 메시지 너무 적으면 None (skip)."""
    msg_count = len(ctx.recent_messages or [])
    if msg_count < PERSONA_MIN_MESSAGES:
        return None

    lines: list[str] = []
    if ctx.customer_name or ctx.customer_memo or ctx.customer_status:
        lines.append("[고객 메타 정보]")
        if ctx.customer_name:    lines.append(f"이름: {ctx.customer_name}")
        if ctx.customer_status:  lines.append(f"상태: {ctx.customer_status}")
        if ctx.customer_memo:    lines.append(f"메모: {ctx.customer_memo}")
        if ctx.lead_heat:        lines.append(f"리드 온도: {ctx.lead_heat}")
        lines.append(f"계약금: {'받음' if ctx.deposit_paid else '안 받음'}")
        lines.append("")

    lines.append("[최근 대화 — 시간순]")
    for m in (ctx.recent_messages or [])[-30:]:
        role = "사장님" if m.role == "owner" else "고객"
        lines.append(f"({role}): {(m.body or '').strip()}")
    lines.append("")

    if ctx.call_summaries:
        lines.append("[통화 요약]")
        for c in ctx.call_summaries[-5:]:
            lines.append(f"({c.direction} {c.duration_sec}초): {c.summary}")
        lines.append("")

    lines.append('위 정보를 보고 이 고객을 한두 문장으로 요약하라. "이 고객은 ..." 로 시작.')
    return "\n".join(lines)


async def _persona_generate(phone: str, ctx: "ConversationContext") -> None:
    """백그라운드 페르소나 생성 — Haiku 호출 + DB 저장. 실패는 silent."""
    user_input = _persona_build_user_input(ctx)
    if user_input is None:
        print(f"[persona] {phone} — 메시지 {len(ctx.recent_messages or [])} 건 < {PERSONA_MIN_MESSAGES}, skip")
        return

    try:
        response = await claude_client.messages.create(
            model=HAIKU_MODEL,
            max_tokens=PERSONA_MAX_TOKENS,
            timeout=PERSONA_GENERATION_TIMEOUT,
            system=[
                {
                    "type": "text",
                    "text": PERSONA_SYSTEM_PROMPT,
                    "cache_control": {"type": "ephemeral"},  # 사장님 1인이면 cache hit
                }
            ],
            messages=[{"role": "user", "content": user_input}],
        )

        text_parts = [
            getattr(b, "text", "") for b in response.content
            if getattr(b, "type", None) == "text"
        ]
        persona_text = "".join(text_parts).strip()

        # 안전망: 빈 응답 또는 너무 길면 잘라냄
        if not persona_text:
            print(f"[persona] {phone} — Haiku empty response, skip")
            return
        persona_text = persona_text[:500]

        _persona_save(phone, persona_text, HAIKU_MODEL, len(ctx.recent_messages or []))
        _log_llm_usage_from_response("customer-persona", response)
        print(f"[persona] {phone} → {persona_text[:60]}...")
    except asyncio.CancelledError:
        raise
    except Exception as e:
        print(f"[persona] {phone} 생성 실패: {type(e).__name__}: {e}")
    finally:
        cur = _persona_inflight.pop(phone, None)
        if cur and not cur.done():
            cur.cancel()


def trigger_persona_refresh_if_needed(
    phone: str, ctx: "ConversationContext"
) -> Optional[str]:
    """페르소나 캐시 조회. stale 시 백그라운드 refresh 트리거.

    Returns: 현재 캐시된 persona_text (stale 여부 무관) 또는 None.
    """
    cached = _persona_get_cached(phone)

    # stale 또는 없으면 백그라운드 생성 (중복 차단)
    need_refresh = cached is None or cached["stale"]
    if need_refresh:
        # 다른 호출이 이미 생성 중이면 skip
        if phone in _persona_inflight and not _persona_inflight[phone].done():
            pass
        else:
            _persona_mark_refresh_started(phone)
            task = asyncio.create_task(_persona_generate(phone, ctx))
            _persona_inflight[phone] = task

    return cached["persona_text"] if cached and cached["persona_text"] else None


def _persona_ctx_from_prepare_req(req: "PrepareReplyRequest") -> "ConversationContext":
    """PrepareReplyRequest → ConversationContext 어댑터 (페르소나 생성용).

    PrepareReplyRequest.recentHistory (HistoryTurn) → recent_messages (Message)
    PrepareReplyRequest.customer (CustomerInfo) → customer_name/memo/lead_heat 등
    latestMessage 도 customer role 로 마지막에 추가.
    """
    msgs: list[Message] = []
    for turn in (req.recentHistory or []):
        msgs.append(Message(
            role=turn.role if turn.role in ("owner", "customer") else "customer",
            body=turn.body or "",
            timestamp_ms=turn.timestampMs,
        ))
    # 방금 받은 메시지도 마지막에 (페르소나 생성에 포함)
    if req.latestMessage:
        msgs.append(Message(
            role="customer",
            body=req.latestMessage,
            timestamp_ms=req.latestMessageReceivedAtMs,
        ))

    c = req.customer
    return ConversationContext(
        phone=req.phone,
        customer_name=(c.name if c else None),
        customer_memo=(c.memo if c else None),
        lead_heat=(c.leadHeat if c else None),
        deposit_paid=bool(c.depositPaid) if c else False,
        recent_messages=msgs,
        call_summaries=[],
        owner_tone_samples=[],
    )


@app.get("/api/customer-persona/{phone}")
async def get_customer_persona(phone: str) -> dict:
    """페르소나 조회 — 단순 lookup. 생성은 prepare-reply 호출 시 자동.

    Response:
      {
        phone,
        persona_text or null,
        model_used,
        source_message_count,
        generated_at_ms,
        age_ms,
        stale (24h 지났나)
      }
    """
    cached = _persona_get_cached(phone)
    if cached is None or not cached.get("persona_text"):
        return {"phone": phone, "persona_text": None, "stale": True}
    return cached


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
# §49 — /admin/prepare-reply/compare (Sonnet vs Gemini 나란히)
# 사장님이 캐시된 최근 메시지 5~10건으로 두 모델 답변을 한 화면에서 톤 비교.
# 호출 한 번에 Sonnet+Gemini 둘 다 병렬 호출 → 응답시간·토큰·v2 결과 같이.
# ============================================================================

def _fetch_recent_prepare_inputs(limit: int = 10) -> list[dict]:
    """suggestions_cache 에서 최근 호출 phone 들의 입력 메시지를 회수.

    Returns: [{phone, latestMessage, latestMessageReceivedAtMs}, ...]
    실제 호출에 쓸 PrepareReplyRequest 의 최소 필드.
    """
    with db_conn() as con:
        rows = con.execute(
            "SELECT phone, based_on_message, based_on_received_at_ms "
            "FROM suggestions_cache "
            "WHERE based_on_message IS NOT NULL AND based_on_message != '' "
            "ORDER BY updated_at_ms DESC LIMIT ?",
            (limit,),
        ).fetchall()
    return [
        {
            "phone": r[0],
            "latestMessage": r[1],
            "latestMessageReceivedAtMs": int(r[2] or 0),
        }
        for r in rows
    ]


async def _run_both_models_for_compare(
    sample: dict, owner_tone_samples: list[str],
) -> dict:
    """한 샘플에 대해 Sonnet · Gemini 둘 다 호출 (병렬). 결과 + 메타.

    실제 prepare-reply 와 동일 입력 — RAG·페르소나 적용.
    """
    req = PrepareReplyRequest(
        phone=sample["phone"],
        latestMessage=sample["latestMessage"],
        latestMessageReceivedAtMs=sample["latestMessageReceivedAtMs"],
        recentHistory=[],
        customer=None,
        ownerToneSamples=owner_tone_samples,
    )

    async def _run_sonnet():
        start = _now_ms()
        try:
            v2, response = await call_claude_for_suggestions_with_meta(req)
            latency = (_now_ms() - start) / 1000.0
            usage = response.usage
            return {
                "ok": True,
                "v2": v2,
                "latency_sec": latency,
                "input_tokens": getattr(usage, "input_tokens", 0),
                "cache_read_tokens": getattr(usage, "cache_read_input_tokens", 0),
                "output_tokens": getattr(usage, "output_tokens", 0),
                "model": CLAUDE_MODEL,
            }
        except Exception as e:
            return {"ok": False, "error": f"{type(e).__name__}: {e}",
                    "latency_sec": (_now_ms() - start) / 1000.0,
                    "model": CLAUDE_MODEL}

    async def _run_gemini():
        start = _now_ms()
        try:
            v2, usage_meta = await call_gemini_for_suggestions_with_meta(req)
            latency = (_now_ms() - start) / 1000.0
            return {
                "ok": True,
                "v2": v2,
                "latency_sec": latency,
                "input_tokens": int(usage_meta.get("promptTokenCount", 0) or 0),
                "cache_read_tokens": 0,
                "output_tokens": int(usage_meta.get("candidatesTokenCount", 0) or 0),
                "model": GEMINI_MODEL,
            }
        except Exception as e:
            return {"ok": False, "error": f"{type(e).__name__}: {e}",
                    "latency_sec": (_now_ms() - start) / 1000.0,
                    "model": GEMINI_MODEL}

    sonnet, gemini = await asyncio.gather(_run_sonnet(), _run_gemini())
    return {"input": sample, "sonnet": sonnet, "gemini": gemini}


@app.get("/admin/prepare-reply/compare", response_class=HTMLResponse)
async def admin_prepare_reply_compare(
    x_admin_token: Optional[str] = Header(default=None, alias="X-Admin-Token"),
    limit: int = 5,
    device_id: str = "owner-anon",
) -> HTMLResponse:
    """Sonnet vs Gemini 비교 페이지 — admin token 필수.

    동작:
    1. suggestions_cache 의 최근 메시지 N건 회수 (사장님 실제 데이터)
    2. 각 메시지마다 Sonnet · Gemini 둘 다 병렬 호출
    3. 응답시간 · 토큰 · 답변 텍스트 한 화면 비교
    """
    _admin_auth(x_admin_token)
    limit = max(1, min(limit, 10))

    # owner_tone 풀에서 RAG 샘플 일부 가져옴 (Sonnet 과 동일 조건)
    samples = _fetch_recent_prepare_inputs(limit)
    if not samples:
        return HTMLResponse(content=_compare_empty_html(), status_code=200)

    # 톤 샘플 (가능하면 owner_tone 에서, 없으면 빈 리스트)
    tone_samples: list[str] = []
    with db_conn() as con:
        rows = con.execute(
            "SELECT text FROM owner_tone WHERE device_id = ? "
            "ORDER BY created_at_ms DESC LIMIT 30",
            (device_id,),
        ).fetchall()
        tone_samples = [r[0] for r in rows if r[0]]

    # 모든 샘플 병렬 처리
    tasks = [_run_both_models_for_compare(s, tone_samples) for s in samples]
    results = await asyncio.gather(*tasks)

    return HTMLResponse(content=_compare_render_html(results, tone_samples_count=len(tone_samples)))


def _compare_empty_html() -> str:
    return """<!doctype html><html lang="ko"><body style="font-family:sans-serif;padding:40px;text-align:center;background:#F4F5F7">
<h2>비교할 메시지가 없어요</h2>
<p>suggestions_cache 에 캐시된 메시지가 0건입니다. 앱에서 SMS 받아 prepare-reply 호출되면 채워집니다.</p>
</body></html>"""


def _compare_render_html(results: list[dict], tone_samples_count: int) -> str:
    """비교 결과 → HTML. 좌 Sonnet · 우 Gemini, 차이점 강조."""
    import html as _html

    def _render_v2(model_result: dict) -> str:
        if not model_result.get("ok"):
            return (
                f'<div class="err">❌ 호출 실패<br>'
                f'<span class="errmsg">{_html.escape(model_result.get("error", "?"))}</span>'
                f'<div class="meta">{model_result["latency_sec"]:.1f}s · {_html.escape(model_result["model"])}</div></div>'
            )
        v2 = model_result["v2"]
        scenario = _html.escape(v2.get("scenario", "?"))
        conf = v2.get("scenario_confidence", 0)
        reason = _html.escape(v2.get("scenario_reason", ""))
        sugs_html = []
        empty_marker = '<em style="color:#9AA3AF">(빈 답변)</em>'
        for s in v2.get("suggestions", []):
            label = _html.escape(s.get("label", "?"))
            text = _html.escape(s.get("text", ""))
            why = _html.escape(s.get("why", ""))
            text_display = text if text else empty_marker
            sugs_html.append(
                f'<div class="sug"><div class="sug-label">{label}</div>'
                f'<div class="sug-text">{text_display}</div>'
                f'<div class="sug-why">{why}</div></div>'
            )
        return (
            f'<div class="scenario"><b>{scenario}</b> '
            f'<span class="conf">conf={conf:.2f}</span></div>'
            f'<div class="reason">{reason}</div>'
            f'<div class="sugs">{"".join(sugs_html)}</div>'
            f'<div class="meta">⏱ {model_result["latency_sec"]:.1f}s · '
            f'in={model_result["input_tokens"]} '
            f'cache={model_result["cache_read_tokens"]} '
            f'out={model_result["output_tokens"]} · '
            f'<span class="model">{_html.escape(model_result["model"])}</span></div>'
        )

    # 평균 응답시간 계산
    sonnet_lat = [r["sonnet"]["latency_sec"] for r in results if r["sonnet"].get("ok")]
    gemini_lat = [r["gemini"]["latency_sec"] for r in results if r["gemini"].get("ok")]
    avg_sonnet = sum(sonnet_lat) / len(sonnet_lat) if sonnet_lat else 0
    avg_gemini = sum(gemini_lat) / len(gemini_lat) if gemini_lat else 0
    speedup = (avg_sonnet / avg_gemini) if avg_gemini > 0 else 0

    rows_html = []
    for i, r in enumerate(results, 1):
        msg = _html.escape(r["input"]["latestMessage"])
        phone = _html.escape(r["input"]["phone"])
        rows_html.append(f'''
        <div class="case">
          <div class="case-head">
            <span class="case-no">#{i}</span>
            <span class="case-phone">{phone}</span>
          </div>
          <div class="case-msg">"{msg}"</div>
          <div class="case-grid">
            <div class="model-col sonnet"><div class="col-head">🟦 Sonnet 4.6</div>{_render_v2(r["sonnet"])}</div>
            <div class="model-col gemini"><div class="col-head">🟨 Gemini 2.5 Flash</div>{_render_v2(r["gemini"])}</div>
          </div>
        </div>
        ''')

    return f'''<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Sonnet vs Gemini 비교 — RING-GO</title>
<style>
  :root {{
    --blue:#3182F6; --blue-dark:#1B64DA; --blue-tint:#EEF4FF;
    --bg:#F4F5F7; --t1:#0B0F19; --t2:#5A6472; --t3:#9AA3AF; --line:#EEF0F3;
    --error:#F0436A; --success:#16C172; --amber:#F6A609;
  }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; padding:24px 18px; background:var(--bg);
         font-family:'Pretendard',-apple-system,system-ui,sans-serif;
         color:var(--t1); line-height:1.55; }}
  .wrap {{ max-width:1200px; margin:0 auto; }}
  h1 {{ font-size:22px; font-weight:700; margin:0 0 4px; }}
  .sub {{ font-size:13px; color:var(--t2); margin-bottom:18px; }}
  .summary {{ background:#fff; border-radius:14px; padding:18px 20px; margin-bottom:20px;
              box-shadow:0 1px 3px rgba(0,0,0,.04); display:flex; gap:24px; flex-wrap:wrap; }}
  .summary .m {{ font-size:13px; }}
  .summary .m b {{ display:block; font-size:22px; color:var(--blue); margin-top:3px; }}
  .summary .m.lat-sonnet b {{ color:#1B64DA; }}
  .summary .m.lat-gemini b {{ color:#B8780A; }}
  .case {{ background:#fff; border-radius:14px; margin-bottom:16px;
           box-shadow:0 1px 3px rgba(0,0,0,.04); overflow:hidden; }}
  .case-head {{ padding:12px 18px; background:#F8F9FB; border-bottom:1px solid var(--line);
                display:flex; gap:12px; align-items:center; }}
  .case-no {{ font-size:12px; font-weight:800; color:var(--blue); background:var(--blue-tint);
              padding:3px 10px; border-radius:999px; }}
  .case-phone {{ font-size:12px; color:var(--t3); font-family:monospace; }}
  .case-msg {{ padding:14px 18px; font-size:14px; color:var(--t1); background:#FAFBFC;
               border-bottom:1px solid var(--line); }}
  .case-grid {{ display:grid; grid-template-columns:1fr 1fr; }}
  .model-col {{ padding:16px 18px; border-right:1px solid var(--line); }}
  .model-col:last-child {{ border-right:0; }}
  .model-col.sonnet {{ background:#fff; }}
  .model-col.gemini {{ background:#FFFCF5; }}
  .col-head {{ font-size:13px; font-weight:700; margin-bottom:10px; }}
  .scenario {{ font-size:14px; margin-bottom:5px; }}
  .scenario .conf {{ font-size:11px; color:var(--t3); margin-left:6px; }}
  .reason {{ font-size:11.5px; color:var(--t2); margin-bottom:10px; line-height:1.5; }}
  .sugs {{ display:flex; flex-direction:column; gap:7px; }}
  .sug {{ background:#F4F5F7; border-radius:9px; padding:9px 11px; }}
  .sug-label {{ font-size:11px; font-weight:700; color:var(--blue); margin-bottom:4px; }}
  .sug-text {{ font-size:13px; line-height:1.55; color:var(--t1); white-space:pre-wrap; }}
  .sug-why {{ font-size:10.5px; color:var(--t3); margin-top:4px; }}
  .meta {{ margin-top:10px; font-size:10.5px; color:var(--t3); font-family:monospace; }}
  .model {{ font-weight:700; }}
  .err {{ color:var(--error); font-size:13px; }}
  .errmsg {{ font-size:11px; color:var(--t3); font-family:monospace; }}
  @media (max-width:780px) {{ .case-grid {{ grid-template-columns:1fr; }} .model-col {{ border-right:0; border-bottom:1px solid var(--line); }} }}
</style>
</head>
<body>
<div class="wrap">
  <h1>🟦 Sonnet 4.6 vs 🟨 Gemini 2.5 Flash — A/B 비교</h1>
  <div class="sub">사장님 실제 캐시 메시지 {len(results)}건 · 톤 RAG 샘플 {tone_samples_count}건 · 2026-06-03</div>
  <div class="summary">
    <div class="m lat-sonnet">Sonnet 평균 응답시간 <b>{avg_sonnet:.1f}s</b></div>
    <div class="m lat-gemini">Gemini 평균 응답시간 <b>{avg_gemini:.1f}s</b></div>
    <div class="m">속도 비율 <b>{speedup:.1f}×</b></div>
    <div class="m">샘플 수 <b>{len(results)}</b></div>
  </div>
  {"".join(rows_html)}
</div>
</body>
</html>'''


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
    *, system_prompt: str, user_msg: str, max_tokens: int = 600,
    model: str = CLAUDE_MODEL,
) -> tuple[dict, "anthropic.types.Message"]:
    """공통 Claude JSON 호출 (사양서 §6 prompt caching 적용).

    `model` 파라미터 — 기본은 Sonnet (CLAUDE_MODEL). 단순 요약/분류 endpoint 는
    HAIKU_MODEL 명시해서 비용 ~1/3 로 내림. Anthropic API 가 단축형/정식ID 둘 다
    받음 — 우리 단가 dict 는 prefix 매칭이라 어느 쪽 박혀도 정확 계산.

    Returns: (parsed JSON dict, raw response).
    """
    response = await claude_client.messages.create(
        model=model,
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
    model: str = CLAUDE_MODEL,
    extra_response_fields: Optional[dict] = None,
) -> dict:
    """3개 endpoint 가 공유하는 처리 흐름: cache → rate-limit → Claude → coerce → cache set.

    `model` — 기본은 Sonnet, 단순 요약/분류는 HAIKU_MODEL 권장 (비용 ~1/3).
    """
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

    # 3) Claude 호출 (model 명시 — Haiku 등으로 비용 최적화 가능)
    system_prompt = _build_summary_system_prompt(system_template, ctx.owner_tone_samples or [])
    user_msg = build_context_user_message(ctx)
    try:
        parsed, response = await call_claude_json(
            system_prompt=system_prompt, user_msg=user_msg, max_tokens=max_tokens,
            model=model,
        )
    except Exception as e:
        print(f"[{endpoint_label}] {ctx.phone} Claude 호출 실패 (model={model}): {type(e).__name__}: {e}")
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
    """HomeScreen 카드에 표시할 한 줄 요약 (15~25자).

    모델: Haiku 4.5 — 단순 요약 워크로드, Sonnet 대비 1/3 비용.
    """
    return await _handle_summary_endpoint(
        ctx=ctx,
        endpoint_label="card-summary",
        system_template=CARD_SUMMARY_SYSTEM,
        coerce_fn=_coerce_card_summary,
        max_tokens=200,
        model=HAIKU_MODEL,
        extra_response_fields={"based_on_message_count": len(ctx.recent_messages)},
    )


@app.post("/api/conversation-summary")
async def conversation_summary(ctx: ConversationContext) -> dict:
    """ChatScreen 진입 시 표시할 3~5줄 상세 요약 + current_stage.

    모델: Haiku 4.5 — 정형 요약 + enum 분류, Haiku 면 충분.
    """
    return await _handle_summary_endpoint(
        ctx=ctx,
        endpoint_label="conversation-summary",
        system_template=CONVERSATION_SUMMARY_SYSTEM,
        coerce_fn=_coerce_conversation_summary,
        max_tokens=600,
        model=HAIKU_MODEL,
    )


@app.post("/api/next-action-suggest")
async def next_action_suggest(ctx: ConversationContext) -> dict:
    """다음 액션 1개 + urgency.

    모델: Haiku 4.5 — 분류 워크로드. Sonnet 의 미세한 톤 보다 빠르고 싸게.
    """
    return await _handle_summary_endpoint(
        ctx=ctx,
        endpoint_label="next-action-suggest",
        system_template=NEXT_ACTION_SUGGEST_SYSTEM,
        coerce_fn=_coerce_next_action,
        max_tokens=400,
        model=HAIKU_MODEL,
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
# §18 — POST /api/call-summary  (에이닷 통화요약 원문 → 1줄 + 불릿 + 후속 문자 초안)
# ─────────────────────────────────────────────────────────────────────────────
# 안드로이드 측 흐름:
#   1) 사장님이 에이닷 통화요약 텍스트를 RING-GO 로 공유 (Android share intent)
#   2) 앱이 raw_text + 통화 메타(direction/duration/started_at) 를 서버로 POST
#   3) 서버: Haiku 호출 → 정제된 응답 { one_line, bullets, suggested_followup_sms }
#   4) 앱이 CallSummaryEntity 에 저장. one_line 은 ChatScreen 의 📞 카드 안에 노출.
#   5) 이후 conversation-summary / card-summary / next-action-suggest 호출 시 앱이
#      ConversationContext.call_summaries 에 {summary=one_line, duration_sec,
#      started_at_ms, direction} 형태로 포함 → 통화 내용이 자동 반영됨.
#
# 모델: Haiku 4.5 — 압축/정형화 워크로드 (Sonnet 의 ~1/3 비용).
# 캐시: summary_cache (phone, endpoint="call-summary", latest_ts=started_at_ms).
#       동일 통화 재요청은 캐시 적중 → DB 부담 0, LLM 비용 0.
# 자동 SMS 발송 절대 금지 정책: suggested_followup_sms 는 "초안" 일 뿐, 발송은 앱
# 측에서 사장님이 ▶ 버튼으로 직접.
# ============================================================================

class CallSummaryRequest(BaseModel):
    phone: str
    raw_text: str                                   # 에이닷 통화요약 원문 (길 수 있음)
    direction: str                                  # "incoming" | "outgoing" | "missed"
    duration_sec: int = 0
    started_at_ms: int = 0                          # 통화 시작 epoch ms (캐시 키)
    customer_name: Optional[str] = None
    customer_memo: Optional[str] = None
    owner_tone_samples: list[str] = Field(default_factory=list)


CALL_SUMMARY_SYSTEM = """너는 1인 시공자(줄눈/타일) 사장님의 비서다.
에이닷(또는 유사 통화기록 앱) 이 만든 긴 통화요약 텍스트를 받아서,
사장님이 채팅 타임라인의 📞 카드 안에서 즉판할 수 있도록 정제한다.

규칙:
- one_line: 정확히 18~28 한국어 글자(공백 포함). 이 통화의 핵심 1줄.
  반드시 통화 결과(예: "견적 요청", "일정 확정", "AS 문의") 가 보여야 함.
- bullets: 3~5줄. 각 줄 앞 이모지 1개 (📍📷💰📅✅⏳⚠️🔧📞 중 적절히).
  각 줄 한 문장, 30자 이내. 통화에서 나온 사실만. 추측 금지.
- suggested_followup_sms: 통화 직후 사장님이 고객에게 보내면 좋을 문자 초안.
  없으면 null. 있으면 1~3 문장. 사장님 톤(존댓말 + ^^/!) 유지. 200자 이내.
  자동 발송 절대 X — 사장님이 앱에서 ▶ 직접 누르는 "초안" 일 뿐.
- 가격·일정 등은 통화 원문에 명시된 것만. 추측·창작 금지.
- 통화 방향(direction) 도 답에 반영. missed 면 one_line 에 "부재중" 명시.

────── 가격표 (참고용) ──────
__PRICING__

────── 사장님 톤 (어휘 참고) ──────
__OWNER_TONE_SAMPLES__

답 형식 — 반드시 지켜라:
- 응답 첫 글자는 '{' 로 시작. 다른 텍스트 일체 X.
- 형식: {"one_line":"...","bullets":["📞 ...","📅 ..."],"suggested_followup_sms":"..." 또는 null}
"""


def _coerce_call_summary(parsed: dict) -> dict:
    """LLM 응답을 안전한 dict 로 정리. 누락 필드는 기본값 채움.

    one_line, bullets, suggested_followup_sms 만 통과시킴 (extra 키 무시).
    """
    one_line = str(parsed.get("one_line") or "").strip()
    if not one_line:
        raise ValueError("one_line 누락")
    # 30자 안전 컷 (LLM 가 가끔 넘침)
    if len(one_line) > 40:
        one_line = one_line[:40].rstrip() + "…"

    raw_bullets = parsed.get("bullets")
    bullets: list[str] = []
    if isinstance(raw_bullets, list):
        for b in raw_bullets[:5]:
            s = str(b).strip()
            if s:
                bullets.append(s if len(s) <= 60 else s[:60].rstrip() + "…")
    # bullets 비어있으면 최소 one_line 한 줄이라도 — 앱 측 안전망
    if not bullets:
        bullets = [one_line]

    fup_raw = parsed.get("suggested_followup_sms")
    if isinstance(fup_raw, str) and fup_raw.strip():
        fup = fup_raw.strip()
        if len(fup) > 240:
            fup = fup[:240].rstrip() + "…"
    else:
        fup = None

    return {
        "one_line": one_line,
        "bullets": bullets,
        "suggested_followup_sms": fup,
    }


def _build_call_summary_user_message(req: CallSummaryRequest) -> str:
    lines: list[str] = []
    lines.append("[고객 정보]")
    lines.append(f"전화번호: {req.phone}")
    lines.append(f"이름: {req.customer_name or '미등록'}")
    if req.customer_memo:
        lines.append(f"메모: {req.customer_memo}")
    lines.append("")
    lines.append("[통화 메타]")
    lines.append(f"방향: {req.direction}")
    lines.append(f"길이(초): {req.duration_sec}")
    lines.append(f"시작 시각(epoch ms): {req.started_at_ms}")
    lines.append("")
    lines.append("[에이닷 통화요약 원문]")
    # 원문 안전 컷 (Haiku context 보호. 평균 통화요약 < 4k chars 가정, 8k 컷)
    raw = req.raw_text or ""
    if len(raw) > 8000:
        raw = raw[:8000] + "\n…(truncated)"
    lines.append(raw if raw else "(원문 없음)")
    return "\n".join(lines)


@app.post("/api/call-summary")
async def call_summary_endpoint(req: CallSummaryRequest) -> dict:
    """에이닷 통화요약 텍스트 → one_line + bullets + suggested_followup_sms.

    모델: Haiku 4.5. 캐시 키: phone + endpoint="call-summary" + started_at_ms.
    """
    if not req.raw_text or not req.raw_text.strip():
        raise HTTPException(400, "raw_text 비어있음")

    # 캐시 키 = started_at_ms (없으면 0 → 동일 phone 의 0 호출들은 함께 캐시)
    cache_ts = req.started_at_ms or 0
    cached = summary_cache_get(req.phone, "call-summary", cache_ts)
    if cached is not None:
        print(f"[call-summary] {req.phone} → cache HIT (started_at_ms={cache_ts})")
        return cached

    # rate limit (phone 기준)
    check_rate_limit(req.phone)

    # 시스템 프롬프트 빌드 (가격표 + 톤 샘플 inject)
    system_prompt = _build_summary_system_prompt(
        CALL_SUMMARY_SYSTEM, req.owner_tone_samples or []
    )
    user_msg = _build_call_summary_user_message(req)

    try:
        parsed, response = await call_claude_json(
            system_prompt=system_prompt,
            user_msg=user_msg,
            max_tokens=600,
            model=HAIKU_MODEL,
        )
    except Exception as e:
        print(f"[call-summary] {req.phone} Claude 호출 실패: {type(e).__name__}: {e}")
        raise HTTPException(502, f"LLM 호출 실패: {type(e).__name__}")

    log_usage(req.phone, "call-summary", response)
    _log_llm_usage_from_response("call-summary", response)
    usage = response.usage
    print(
        f"[call-summary] {req.phone} → ready "
        f"(in={getattr(usage,'input_tokens',0)} "
        f"cache_read={getattr(usage,'cache_read_input_tokens',0)} "
        f"out={getattr(usage,'output_tokens',0)})"
    )

    try:
        coerced = _coerce_call_summary(parsed)
    except ValueError as e:
        raise HTTPException(502, f"LLM 응답 형식 오류: {e}")

    response_payload = {
        **coerced,
        "phone": req.phone,
        "direction": req.direction,
        "duration_sec": req.duration_sec,
        "started_at_ms": req.started_at_ms,
        "generated_at_ms": _now_ms(),
    }
    summary_cache_set(req.phone, "call-summary", cache_ts, response_payload)
    response_payload["_cache_hit"] = False
    return response_payload


# ============================================================================
# §19 — 시공접수서 (고객 자가확인 폼) — 프로토타입 openQuote 1:1
# ─────────────────────────────────────────────────────────────────────────────
# 정답 스펙: design-preview/ringgo-redesign.html 의 openQuote() / finalizeQuote().
# CLAUDE.md §0 (프로토=실전 스펙, 100% verbatim).
#
# 흐름:
#   1) 사장님(앱)이 POST /api/intake-form/issue 호출 시 견적 데이터까지 함께 보냄:
#      - phone (고객), customer_name?, device_id?, owner_phone?
#      - scheduled_at_ms (확정 시공일), scheduled_days
#      - estimate_items [{name, price_man, unit?, area?}] (사장님 견적 항목)
#      - total_man (합계, 만원)
#      - deposit_mode ('none'|'ratio'|'fixed'), deposit_amount_krw, deposit_ratio_pct?
#      - biz_name (발급 시점 사업자명 snapshot — 헤더에 표시)
#   2) 서버 8자 base62 토큰 발급 → URL 반환 (7일 만료)
#   3) 앱이 SMS 본문에 URL prefill → 사장님 ▶ 직접 발송 (자동 발송 X)
#   4) 고객 모바일 브라우저로 진입 → /intake/{token} HTML 폼
#      - 카드 1 = 시공일 (확정 배지, 표시만)
#      - 카드 2 = 견적 내역 (항목·합계·부가세 별도·계약금, 표시만)
#      - 카드 3 = 연락처·현장 정보 (전화·주소·동/호수·메모, 입력)
#      - 카드 4 = 유입 경로 설문 (선택, 건너뛰기 가능)
#      - 동의 체크 + [접수 완료하기]
#   5) 고객 [제출] → 주소 확인 dialog → POST /api/intake-form/submit
#      페이로드: {token, contact_phone, road_address, building_detail?, memo?, source?}
#   6) 앱 polling: GET /api/intake-form/status?phone=... → 응답에 견적 + 제출 결과
#
# 자동 SMS 발송 절대 금지 정책: 서버는 URL 만 발급, 발송은 앱 ▶.
# ============================================================================

INTAKE_TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"  # 0/O/1/I/l 제외
INTAKE_TOKEN_LEN = 8
INTAKE_TTL_MS = 7 * 24 * 60 * 60 * 1000  # 7일 (사장님 결정)
INTAKE_PUBLIC_BASE_URL = os.environ.get(
    "INTAKE_PUBLIC_BASE_URL",
    "http://100.86.114.49:8000",
)


def _generate_intake_token() -> str:
    """8자 base62 토큰. 충돌 시 8회 재시도."""
    import secrets
    for _ in range(8):
        tok = "".join(secrets.choice(INTAKE_TOKEN_ALPHABET) for _ in range(INTAKE_TOKEN_LEN))
        with db_conn() as con:
            row = con.execute(
                "SELECT 1 FROM intake_forms WHERE token = ? LIMIT 1", (tok,)
            ).fetchone()
            if not row:
                return tok
    raise HTTPException(500, "토큰 생성 실패 (8회 재시도)")


def _fetch_owner_biz_name(owner_phone: Optional[str]) -> str:
    """subscribers 테이블에서 사장님 사업자명 lookup. 없으면 빈 문자열."""
    if not owner_phone:
        return ""
    with db_conn() as con:
        row = con.execute(
            "SELECT company, name FROM subscribers WHERE phone = ?",
            (owner_phone,),
        ).fetchone()
    if not row:
        return ""
    return (row[0] or row[1] or "").strip()


class IntakeEstimateItem(BaseModel):
    name: str
    price_man: int = 0       # 만원 단위 (프로토 lineTotal 결과치)
    unit: Optional[str] = None    # "pyeong" 이면 area 도 같이 옴
    area: Optional[float] = None  # 평수 (unit='pyeong' 일 때)


class IntakeIssueRequest(BaseModel):
    phone: str                                       # 고객 phone
    customer_name: Optional[str] = None
    device_id: Optional[str] = None                  # 사장님 device
    owner_phone: Optional[str] = None                # 사장님 phone (subscribers lookup)
    # 견적 데이터 (사장님이 정한 것 — 폼에서 표시만)
    scheduled_at_ms: int = 0                         # 확정 시공일 (0 = 미정 — 폼에 "미정" 표시)
    scheduled_days: int = 1
    estimate_items: list[IntakeEstimateItem] = Field(default_factory=list)
    total_man: int = 0                               # 합계 (만원)
    deposit_mode: str = "none"                       # 'none' | 'ratio' | 'fixed'
    deposit_amount_krw: int = 0
    deposit_ratio_pct: Optional[int] = None
    biz_name: Optional[str] = None                   # 명시 override (없으면 subscribers lookup)


class IntakeSubmitRequest(BaseModel):
    """프로토 finalizeQuote 의 페이로드 1:1."""
    token: str
    contact_phone: str                                # 전화번호 (필수)
    road_address: str                                 # 도로명 주소 (필수)
    building_detail: Optional[str] = None             # 동/호수 (선택)
    memo: Optional[str] = None                        # 현장 메모 (선택)
    source: Optional[str] = None                      # 유입 경로 합쳐서 (선택, finalizeQuote 의 src)


@app.post("/api/intake-form/issue")
async def intake_form_issue(req: IntakeIssueRequest) -> dict:
    """접수서 토큰 발급 + 견적 데이터 보관 (7일 만료).

    응답: { token, url, issued_at_ms, expires_at_ms }
    """
    if not req.phone or not req.phone.strip():
        raise HTTPException(400, "phone 필수")
    if req.deposit_mode not in ("none", "ratio", "fixed"):
        raise HTTPException(400, "deposit_mode 는 none/ratio/fixed")

    now = _now_ms()
    token = _generate_intake_token()
    expires_at = now + INTAKE_TTL_MS
    biz_name = (req.biz_name or "").strip() or _fetch_owner_biz_name(req.owner_phone) or ""

    items_payload = [
        {
            "name": it.name,
            "price_man": int(it.price_man or 0),
            "unit": it.unit,
            "area": it.area,
        }
        for it in (req.estimate_items or [])
    ]

    with db_conn() as con:
        con.execute(
            """
            INSERT INTO intake_forms
                (token, phone, customer_name, issued_at_ms, expires_at_ms,
                 submitted_at_ms, payload_json, device_id, owner_phone, created_at_ms,
                 scheduled_at_ms, scheduled_days, estimate_items_json, total_man,
                 deposit_amount_krw, deposit_mode, deposit_ratio_pct, biz_name)
            VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                token, req.phone, req.customer_name, now, expires_at,
                req.device_id, req.owner_phone, now,
                req.scheduled_at_ms or 0, max(1, int(req.scheduled_days or 1)),
                json.dumps(items_payload, ensure_ascii=False),
                int(req.total_man or 0),
                int(req.deposit_amount_krw or 0),
                req.deposit_mode,
                req.deposit_ratio_pct,
                biz_name,
            ),
        )
        con.commit()
    url = f"{INTAKE_PUBLIC_BASE_URL.rstrip('/')}/intake/{token}"
    print(f"[intake-form/issue] phone={req.phone} biz={biz_name!r} → token={token}")
    return {
        "token": token,
        "url": url,
        "issued_at_ms": now,
        "expires_at_ms": expires_at,
    }


@app.post("/api/intake-form/submit")
async def intake_form_submit(req: IntakeSubmitRequest) -> dict:
    """고객이 폼 제출. 프로토 finalizeQuote 페이로드.

    응답: { ok, submitted_at_ms, phone }
    """
    contact_phone = (req.contact_phone or "").strip()
    road_address = (req.road_address or "").strip()
    if not contact_phone:
        raise HTTPException(400, "contact_phone 필수")
    if not road_address:
        raise HTTPException(400, "road_address 필수")

    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            "SELECT phone, expires_at_ms, submitted_at_ms FROM intake_forms WHERE token = ?",
            (req.token,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "유효하지 않은 토큰")
        phone, expires_at, submitted_at = row
        if submitted_at is not None:
            raise HTTPException(409, "이미 제출된 접수서입니다")
        if now > expires_at:
            raise HTTPException(410, "만료된 접수서 (7일 경과)")

        payload = {
            "contact_phone": contact_phone,
            "road_address": road_address,
            "building_detail": (req.building_detail or "").strip() or None,
            "memo": (req.memo or "").strip() or None,
            "source": (req.source or "").strip() or None,
        }
        con.execute(
            "UPDATE intake_forms SET submitted_at_ms = ?, payload_json = ? WHERE token = ?",
            (now, json.dumps(payload, ensure_ascii=False), req.token),
        )
        con.commit()
    print(f"[intake-form/submit] token={req.token} phone={phone} → submitted")
    return {"ok": True, "submitted_at_ms": now, "phone": phone}


def _intake_row_to_dict(row: tuple) -> dict:
    """SELECT * FROM intake_forms 결과 → API 응답 dict.

    Columns order: token, phone, customer_name, issued_at_ms, expires_at_ms,
                   submitted_at_ms, payload_json, device_id, owner_phone, created_at_ms,
                   scheduled_at_ms, scheduled_days, estimate_items_json, total_man,
                   deposit_amount_krw, deposit_mode, deposit_ratio_pct, biz_name
    """
    (token, phone, customer_name, issued_at_ms, expires_at_ms, submitted_at_ms,
     payload_json, device_id, owner_phone, created_at_ms,
     scheduled_at_ms, scheduled_days, estimate_items_json, total_man,
     deposit_amount_krw, deposit_mode, deposit_ratio_pct, biz_name) = row
    payload = None
    if payload_json:
        try:
            payload = json.loads(payload_json)
        except json.JSONDecodeError:
            payload = None
    items = []
    if estimate_items_json:
        try:
            items = json.loads(estimate_items_json) or []
        except json.JSONDecodeError:
            items = []
    return {
        "token": token,
        "phone": phone,
        "customer_name": customer_name,
        "issued_at_ms": issued_at_ms,
        "expires_at_ms": expires_at_ms,
        "submitted_at_ms": submitted_at_ms,
        "payload": payload,
        "url": f"{INTAKE_PUBLIC_BASE_URL.rstrip('/')}/intake/{token}",
        "scheduled_at_ms": scheduled_at_ms or 0,
        "scheduled_days": scheduled_days or 1,
        "estimate_items": items,
        "total_man": total_man or 0,
        "deposit_amount_krw": deposit_amount_krw or 0,
        "deposit_mode": deposit_mode or "none",
        "deposit_ratio_pct": deposit_ratio_pct,
        "biz_name": biz_name or "",
    }


_INTAKE_SELECT_COLS = (
    "token, phone, customer_name, issued_at_ms, expires_at_ms, submitted_at_ms, "
    "payload_json, device_id, owner_phone, created_at_ms, "
    "scheduled_at_ms, scheduled_days, estimate_items_json, total_man, "
    "deposit_amount_krw, deposit_mode, deposit_ratio_pct, biz_name"
)


@app.get("/api/intake-form/status")
async def intake_form_status(phone: str, device_id: Optional[str] = None) -> dict:
    """해당 phone 의 가장 최근 intake (앱 polling 용)."""
    if not phone:
        raise HTTPException(400, "phone 필수")
    where = "WHERE phone = ?"
    params: list = [phone]
    if device_id:
        where += " AND device_id = ?"
        params.append(device_id)
    with db_conn() as con:
        row = con.execute(
            f"SELECT {_INTAKE_SELECT_COLS} FROM intake_forms {where} "
            "ORDER BY issued_at_ms DESC LIMIT 1",
            params,
        ).fetchone()
    if not row:
        return {"phone": phone, "intake": None}
    return {"phone": phone, "intake": _intake_row_to_dict(row)}


@app.get("/api/intake-form/list")
async def intake_form_list(
    device_id: Optional[str] = None,
    owner_phone: Optional[str] = None,
    limit: int = 30,
) -> dict:
    """사장님 발급한 전체 목록 (관리용). device_id 또는 owner_phone 으로 필터."""
    limit = max(1, min(limit, 200))
    where_parts: list[str] = []
    params: list = []
    if device_id:
        where_parts.append("device_id = ?")
        params.append(device_id)
    if owner_phone:
        where_parts.append("owner_phone = ?")
        params.append(owner_phone)
    where = ("WHERE " + " AND ".join(where_parts)) if where_parts else ""
    with db_conn() as con:
        rows = con.execute(
            f"SELECT {_INTAKE_SELECT_COLS} FROM intake_forms {where} "
            "ORDER BY issued_at_ms DESC LIMIT ?",
            [*params, limit],
        ).fetchall()
    items = [_intake_row_to_dict(r) for r in rows]
    return {"items": items, "count": len(items)}


# ─── /intake/{token} HTML 폼 (프로토 openQuote 1:1) ───
# 디자인 토큰 (--blue=#3182F6, --bg=#F4F5F7 ...) + 클래스명(q-scroll/q-hero/q-card/...)
# + 카드 구조 (1.시공일 2.견적 3.연락처 4.설문) 모두 프로토 그대로.

INTAKE_FORM_HTML_TEMPLATE = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=yes,maximum-scale=5">
<title>시공 접수서</title>
<style>
  :root {{
    --blue:#3182F6; --blue-dark:#1B64DA; --blue-tint:#EEF4FF;
    --bg:#F4F5F7; --card:#FFFFFF;
    --t1:#0B0F19; --t2:#5A6472; --t3:#9AA3AF; --line:#EEF0F3;
    --error:#F0436A; --success:#16C172;
    --shadow:0 1px 3px rgba(0,0,0,.04);
  }}
  * {{ box-sizing:border-box; -webkit-tap-highlight-color:transparent; }}
  html, body {{ margin:0; padding:0; background:var(--bg); }}
  body {{
    font-family:'Pretendard',-apple-system,BlinkMacSystemFont,system-ui,"Apple SD Gothic Neo","Noto Sans KR",sans-serif;
    color:var(--t1); line-height:1.5; min-height:100vh;
  }}
  .q-scroll {{ max-width:480px; margin:0 auto; }}

  /* hero — 다크 그라데이션 */
  .q-hero {{ background:linear-gradient(150deg,#272D3D,#14171F); color:#fff; padding:26px 22px 24px; }}
  .q-hero .q-biz {{ font-size:12px; font-weight:800; color:rgba(255,255,255,.7); letter-spacing:.02em; }}
  .q-hero .q-title {{ font-size:21px; font-weight:800; letter-spacing:-.03em; margin-top:9px; line-height:1.4; }}
  .q-hero .q-title b {{ color:#8FD6FF; }}
  .q-hero .q-hero-sub {{ font-size:12.5px; font-weight:700; color:rgba(255,255,255,.62); margin-top:12px; display:inline-flex; align-items:center; gap:6px; background:rgba(255,255,255,.1); padding:5px 12px; border-radius:999px; }}

  /* body — 카드 */
  .q-body {{ padding:16px; }}
  .q-card {{ background:#fff; border-radius:18px; padding:17px; margin-bottom:12px; box-shadow:var(--shadow); }}
  .q-card.q-card-date {{ border:1.5px solid var(--blue); background:linear-gradient(180deg,#F4F8FF,#fff); }}
  .q-card-h {{ font-size:13px; font-weight:800; color:var(--t1); margin-bottom:12px; display:flex; align-items:center; gap:6px; }}
  .q-step {{ display:inline-flex; align-items:center; justify-content:center; width:20px; height:20px; border-radius:50%; background:var(--blue); color:#fff; font-size:11px; font-weight:800; }}
  .q-step-ok {{ margin-left:auto; font-size:11px; font-weight:800; color:var(--blue); background:var(--blue-tint); padding:3px 9px; border-radius:999px; }}

  /* 카드 1 — 시공일 (q-fixed) */
  .q-fixed {{ display:flex; align-items:center; gap:11px; background:var(--blue-tint); border-radius:12px; padding:14px; }}
  .q-fixed .qf-ic {{ width:38px; height:38px; border-radius:50%; background:#fff; color:var(--blue); display:flex; align-items:center; justify-content:center; flex-shrink:0; font-size:18px; }}
  .q-fixed .qf-b {{ flex:1; min-width:0; }}
  .q-fixed .qf-l {{ font-size:11.5px; font-weight:800; color:var(--blue); }}
  .q-fixed .qf-d {{ font-size:16px; font-weight:800; color:var(--blue-dark); }}
  .q-fixed .qf-badge {{ font-size:11px; font-weight:800; color:#0a8f44; background:#E7F8EF; padding:4px 10px; border-radius:999px; flex-shrink:0; }}

  /* 카드 2 — 견적 내역 */
  .q-item {{ display:flex; align-items:center; padding:9px 0; border-bottom:1px solid var(--line); }}
  .q-item:last-of-type {{ border-bottom:0; }}
  .q-item .qi-n {{ font-size:14px; color:var(--t1); flex:1; }}
  .q-item .qi-n .unit {{ font-size:11px; color:var(--t3); margin-left:4px; }}
  .q-item .qi-p {{ font-size:14px; font-weight:700; color:var(--t1); }}
  .q-total {{ display:flex; align-items:baseline; gap:8px; margin-top:13px; padding-top:13px; border-top:2px solid var(--t1); }}
  .q-total span:first-child {{ font-size:14px; font-weight:800; color:var(--t1); }}
  .q-total b {{ font-size:21px; font-weight:800; color:var(--blue); margin-left:auto; }}
  .q-vat {{ text-align:right; font-size:11px; color:var(--t3); margin-top:3px; }}
  .q-deposit {{ background:var(--blue-tint); border-radius:12px; padding:11px 13px; font-size:12.5px; color:var(--blue-dark); font-weight:700; margin-top:12px; line-height:1.5; }}
  .q-empty {{ font-size:13px; color:var(--t3); padding:6px 0; }}

  /* 카드 3 — 입력 */
  .q-label {{ font-size:12px; font-weight:800; color:var(--t3); margin:13px 2px 6px; }}
  .q-label:first-child {{ margin-top:0; }}
  .q-input {{ width:100%; background:var(--bg); border:1.5px solid var(--line); border-radius:12px; padding:13px 14px; font-size:15px; font-family:inherit; color:var(--t1); outline:none; }}
  .q-input:focus {{ border-color:var(--blue); }}
  .q-input + .q-input {{ margin-top:8px; }}
  .q-addr-field {{ width:100%; background:var(--bg); border:1.5px solid var(--line); border-radius:12px; padding:13px 14px; font-size:15px; color:var(--t3); cursor:pointer; display:flex; align-items:center; gap:7px; min-height:48px; }}
  .q-addr-field.filled {{ color:var(--t1); }}
  .q-addr-field .ico {{ color:var(--blue); }}

  /* 카드 4 — 유입경로 설문 */
  .qs-head {{ display:flex; align-items:center; gap:6px; font-size:11.5px; font-weight:800; color:var(--blue); margin-bottom:11px; }}
  .qs-q {{ font-size:15px; font-weight:800; color:var(--t1); letter-spacing:-.01em; }}
  .qs-sub {{ font-size:12.5px; color:var(--t2); margin-top:5px; line-height:1.5; }}
  .qs-btns {{ display:flex; gap:8px; margin-top:13px; }}
  .qs-btns button {{ flex:1; }}
  .qs-ok, .qs-skip {{ border:0; border-radius:12px; padding:13px; font-size:14px; font-weight:800; font-family:inherit; cursor:pointer; min-height:48px; }}
  .qs-skip {{ background:var(--bg); color:var(--t2); }}
  .qs-ok {{ background:var(--blue); color:#fff; }}
  .qs-chips {{ display:flex; flex-wrap:wrap; gap:8px; margin-top:13px; }}
  .qs-chip {{ background:#fff; border:1.5px solid var(--line); border-radius:999px; padding:10px 16px; font-size:13.5px; font-weight:700; color:var(--t1); cursor:pointer; min-height:42px; display:inline-flex; align-items:center; }}
  .qs-chip.on, .qs-chip:active {{ background:var(--blue-tint); border-color:var(--blue); color:var(--blue); }}
  .qs-done {{ display:flex; align-items:flex-start; gap:9px; font-size:14px; font-weight:700; color:var(--blue-dark); line-height:1.5; }}
  .qs-done.muted {{ color:var(--t2); }}
  .qs-done .ic {{ color:var(--success); flex-shrink:0; margin-top:1px; }}
  .qs-detail {{ font-size:12px; color:var(--t3); margin-top:4px; font-weight:600; }}

  /* 동의 + 제출 */
  .q-agree {{ display:flex; align-items:center; gap:10px; background:#fff; border:1.5px solid var(--line); border-radius:14px; padding:14px; margin-bottom:12px; cursor:pointer; font-size:13.5px; font-weight:700; color:var(--t1); min-height:52px; }}
  .q-agree.on {{ border-color:var(--blue); background:var(--blue-tint); }}
  .q-agree .qa-box {{ width:22px; height:22px; border-radius:7px; border:2px solid var(--line); display:flex; align-items:center; justify-content:center; flex-shrink:0; font-size:13px; color:transparent; }}
  .q-agree.on .qa-box {{ background:var(--blue); border-color:var(--blue); color:#fff; }}
  .q-submit {{ width:100%; background:var(--blue); color:#fff; border:0; border-radius:15px; padding:16px; font-size:16px; font-weight:800; font-family:inherit; cursor:pointer; box-shadow:0 10px 24px rgba(49,130,246,.28); min-height:56px; }}
  .q-submit:disabled {{ opacity:.45; box-shadow:none; cursor:default; }}
  .q-alt {{ text-align:center; font-size:13px; font-weight:700; color:var(--t3); margin-top:14px; cursor:pointer; text-decoration:underline; padding:8px; }}
  .q-foot {{ text-align:center; font-size:11px; color:var(--t3); margin:16px 0 28px; line-height:1.5; padding:0 16px; }}

  /* confirm modal */
  .cm-bd {{ position:fixed; inset:0; background:rgba(0,0,0,.45); display:none; align-items:flex-end; justify-content:center; z-index:50; }}
  .cm-bd.show {{ display:flex; }}
  .cm-card {{ background:#fff; border-radius:20px 20px 0 0; width:100%; max-width:480px; padding:22px 20px 18px; }}
  .cm-t {{ font-size:18px; font-weight:800; color:var(--t1); }}
  .cm-s {{ font-size:13.5px; color:var(--t2); margin-top:10px; line-height:1.55; white-space:pre-line; }}
  .cm-btns {{ display:flex; gap:8px; margin-top:18px; }}
  .cm-btns button {{ flex:1; border:0; border-radius:12px; padding:14px; font-size:14.5px; font-weight:800; font-family:inherit; cursor:pointer; min-height:50px; }}
  .cm-cancel {{ background:var(--bg); color:var(--t2); }}
  .cm-ok {{ background:var(--blue); color:#fff; }}

  /* 상태 페이지 */
  .status-page {{ max-width:480px; margin:0 auto; padding:60px 24px; text-align:center; }}
  .status-page h2 {{ font-size:22px; font-weight:800; }}
  .status-page p {{ font-size:14px; color:var(--t2); line-height:1.6; margin-top:10px; }}
</style>
</head>
<body>
<div class="q-scroll">

  <div class="q-hero">
    <div class="q-biz">{biz_html}</div>
    <div class="q-title">시공일 확정을 위해<br>접수서를 <b>정확하게</b> 작성해주세요 😊</div>
    <div class="q-hero-sub">✓ 3가지만 확인하면 끝나요</div>
  </div>

  <div class="q-body">

    <!-- 카드 1: 시공일 (표시만) -->
    <div class="q-card q-card-date">
      <div class="q-card-h"><span class="q-step">1</span>시공일 <span class="q-step-ok">확정</span></div>
      <div class="q-fixed">
        <span class="qf-ic">📅</span>
        <div class="qf-b">
          <div class="qf-l">사장님과 정한 확정 시공일</div>
          <div class="qf-d">{schedule_label_html}</div>
        </div>
        <span class="qf-badge">확정</span>
      </div>
    </div>

    <!-- 카드 2: 견적 내역 (표시만) -->
    <div class="q-card">
      <div class="q-card-h"><span class="q-step">2</span>견적 내역</div>
      {items_html}
      <div class="q-total"><span>합계</span><b>{total_man_html}만원</b></div>
      <div class="q-vat">부가세 별도</div>
      {deposit_html}
    </div>

    <!-- 카드 3: 연락처·현장 정보 (고객 입력) -->
    <div class="q-card">
      <div class="q-card-h"><span class="q-step">3</span>연락처 · 현장 정보</div>
      <div class="q-label">전화번호</div>
      <input class="q-input" id="q-phone" inputmode="numeric" value="010-" autocomplete="tel">
      <div class="q-label">현장 주소</div>
      <div class="q-addr-field" id="q-addr-field" onclick="openAddr()"><span class="ico">🔍</span><span id="q-addr-text">주소 검색 (탭)</span></div>
      <input class="q-input" id="q-dong" placeholder="동/호수 (선택)" style="margin-top:8px">
      <div class="q-label">현장 메모 (선택)</div>
      <textarea class="q-input" id="q-memo" style="height:62px;resize:none" placeholder="현관 비밀번호·주차 안내 등 편하게 남겨주세요"></textarea>
    </div>

    <!-- 카드 4: 유입경로 설문 (선택, 건너뛰기 가능) -->
    <div class="q-card" id="quote-survey">
      <div class="qs-head"><span>✨</span>마케팅에 도움돼요 (선택)</div>
      <div id="qs-body"></div>
    </div>

    <!-- 동의 -->
    <div class="q-agree" id="q-agree" onclick="toggleAgree()">
      <span class="qa-box">✓</span>
      위 내용을 모두 확인했고, 시공을 접수합니다.
    </div>

    <!-- 제출 -->
    <button class="q-submit" id="q-submit" disabled onclick="submitQuote()">접수 완료하기</button>

    <div class="q-alt" onclick="requestEdit()">내용 수정을 요청할래요</div>

    <div class="q-foot">이 링크는 {biz_html} 이(가) 보냈어요 · 발행일로부터 7일 후 만료</div>

  </div>
</div>

<!-- confirm modal -->
<div class="cm-bd" id="cm-bd">
  <div class="cm-card">
    <div class="cm-t" id="cm-t">이 주소가 정확한가요?</div>
    <div class="cm-s" id="cm-s"></div>
    <div class="cm-btns">
      <button class="cm-cancel" onclick="closeConfirm()">아니요, 수정</button>
      <button class="cm-ok" onclick="confirmOk()">네, 맞아요 · 접수</button>
    </div>
  </div>
</div>

<script src="//t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js"></script>
<script>
  var TOKEN = "{token_js}";
  var BIZ = {biz_js};
  var quoteSel = {{ agree:false }};
  var quoteAddr = "";
  var quoteSurvey = {{ asked:false, busy:false, source:null, keyword:'', category:null, etc:'', done:false }};
  var pendingConfirm = null;

  // 전화번호 입력 자동 하이픈
  document.getElementById('q-phone').addEventListener('input', function(e) {{
    var v = (e.target.value || '').replace(/[^0-9]/g, '');
    if (v.length > 11) v = v.slice(0, 11);
    var out = v;
    if (v.length >= 7) out = v.slice(0, 3) + '-' + v.slice(3, 7) + '-' + v.slice(7);
    else if (v.length >= 4) out = v.slice(0, 3) + '-' + v.slice(3);
    e.target.value = out;
    updateSubmit();
  }});

  // 주소 검색 (다음 우편번호 위젯, 무료)
  function openAddr() {{
    new daum.Postcode({{
      oncomplete: function(data) {{
        quoteAddr = data.roadAddress || data.jibunAddress || data.address || '';
        var el = document.getElementById('q-addr-field');
        var t = document.getElementById('q-addr-text');
        if (t) t.textContent = '📍 ' + quoteAddr;
        el.classList.add('filled');
        document.getElementById('q-dong').focus();
        updateSubmit();
      }}
    }}).open();
  }}

  // 동의 체크
  function toggleAgree() {{
    quoteSel.agree = !quoteSel.agree;
    document.getElementById('q-agree').classList.toggle('on', quoteSel.agree);
    updateSubmit();
  }}

  function updateSubmit() {{
    var btn = document.getElementById('q-submit');
    if (!btn) return;
    btn.disabled = !(quoteSel.agree && quoteAddr);
  }}

  // 유입경로 설문 (프로토 renderQuoteSurvey 1:1)
  function chip(label, fn) {{
    return '<span class="qs-chip" onclick="' + fn + '">' + label + '</span>';
  }}
  function renderSurvey() {{
    var s = quoteSurvey;
    var b = document.getElementById('qs-body');
    if (!b) return;
    var h = '';
    if (s.done) {{
      var dp = [];
      if (s.source) dp.push(s.source);
      if (s.keyword) dp.push('"' + s.keyword + '"');
      if (s.category && s.category !== '기타') dp.push(s.category);
      if (s.etc) dp.push(s.etc);
      var detail = dp.join(' · ');
      h = '<div class="qs-done"><span class="ic">✓</span><div>알려주셔서 감사해요! 큰 도움이 됐어요 😊'
        + (detail ? '<div class="qs-detail">' + detail + '</div>' : '')
        + '</div></div>';
    }} else if (s.busy) {{
      h = '<div class="qs-done muted"><span class="ic">✓</span>괜찮아요! 바쁘신데 봐주셔서 감사합니다 🙏</div>';
    }} else if (!s.asked) {{
      h = '<div class="qs-q">혹시 질문 하나 드려도 될까요?</div>'
        + '<div class="qs-sub">저희 같은 작은 업체엔 정말 큰 도움이 돼요!</div>'
        + '<div class="qs-btns"><button class="qs-skip" onclick="surveyBusy()">지금은 바빠요</button>'
        + '<button class="qs-ok" onclick="surveyAsk()">네, 좋아요!</button></div>';
    }} else if (!s.source) {{
      h = '<div class="qs-q">어떤 경로로 저희를 알게 되셨어요?</div>'
        + '<div class="qs-chips">'
        + chip('네이버 검색', "surveySource('네이버 검색')")
        + chip('인스타그램', "surveySource('인스타그램')")
        + chip('구글', "surveySource('구글')")
        + chip('기타', "surveySource('기타')")
        + '</div>';
    }} else if ((s.source === '네이버 검색' || s.source === '구글') && !s.keyword) {{
      h = '<div class="qs-q">어떤 키워드로 검색하셨어요?</div>'
        + '<input class="q-input" id="qs-kw" placeholder="예: 천호동 줄눈">'
        + '<button class="qs-ok" style="width:100%;margin-top:10px" onclick="surveyKeyword()">다음</button>';
    }} else if ((s.source === '네이버 검색' || s.source === '구글') && !s.category) {{
      h = '<div class="qs-q">어디서 저희를 보셨어요?</div>'
        + '<div class="qs-chips">'
        + chip('파워링크', "surveyCategory('파워링크')")
        + chip('블로그', "surveyCategory('블로그')")
        + chip('카페', "surveyCategory('카페')")
        + chip('웹사이트', "surveyCategory('웹사이트')")
        + '</div>';
    }} else if (s.source === '인스타그램' && !s.category) {{
      h = '<div class="qs-q">인스타그램에서 어떻게 보셨어요?</div>'
        + '<div class="qs-chips">'
        + chip('브랜드 계정 홍보를 보고', "surveyCategory('브랜드 계정 홍보')")
        + chip('알고리즘으로 우연히', "surveyCategory('알고리즘 우연히')")
        + chip('기타', "surveyCategory('기타')")
        + '</div>';
    }} else if ((s.source === '기타' || (s.source === '인스타그램' && s.category === '기타')) && !s.done) {{
      h = '<div class="qs-q">어떻게 알게 되셨는지 알려주실래요?</div>'
        + '<input class="q-input" id="qs-etc" placeholder="예: 아파트 게시판 전단, 친구 추천 등">'
        + '<button class="qs-ok" style="width:100%;margin-top:10px" onclick="surveyEtc()">완료</button>';
    }}
    b.innerHTML = h;
  }}
  function surveyAsk()   {{ quoteSurvey.asked = true;  renderSurvey(); }}
  function surveyBusy()  {{ quoteSurvey.busy  = true;  renderSurvey(); }}
  function surveySource(src) {{ quoteSurvey.source = src; renderSurvey(); }}
  function surveyKeyword() {{
    var i = document.getElementById('qs-kw');
    quoteSurvey.keyword = (i && i.value.trim()) || '(미입력)';
    renderSurvey();
  }}
  function surveyCategory(cat) {{
    quoteSurvey.category = cat;
    if (cat !== '기타') quoteSurvey.done = true;
    renderSurvey();
  }}
  function surveyEtc() {{
    var i = document.getElementById('qs-etc');
    quoteSurvey.etc = (i && i.value.trim()) || '(미입력)';
    quoteSurvey.done = true;
    renderSurvey();
  }}
  renderSurvey();

  // 유입경로 → 합쳐서 src 문자열 (프로토 finalizeQuote 와 동일 포맷)
  function buildSrc() {{
    var s = quoteSurvey;
    if (!s.done) return '';
    var out = s.source || '';
    if (s.keyword) out += ' · "' + s.keyword + '"';
    if (s.category && s.category !== '기타') out += ' · ' + s.category;
    if (s.etc) out += ' · ' + s.etc;
    return out;
  }}

  // confirm modal
  function openConfirm(title, sub, onOk) {{
    document.getElementById('cm-t').textContent = title;
    document.getElementById('cm-s').textContent = sub;
    pendingConfirm = onOk;
    document.getElementById('cm-bd').classList.add('show');
  }}
  function closeConfirm() {{
    pendingConfirm = null;
    document.getElementById('cm-bd').classList.remove('show');
  }}
  function confirmOk() {{
    var fn = pendingConfirm;
    closeConfirm();
    if (fn) fn();
  }}

  // 제출 (프로토 submitQuote → finalizeQuote)
  function submitQuote() {{
    if (!quoteSel.agree) {{ alert('맨 아래 확인에 체크해주세요'); return; }}
    if (!quoteAddr)      {{ alert('현장 주소를 검색해 주세요'); return; }}
    var dong = (document.getElementById('q-dong').value || '').trim();
    var full = quoteAddr + (dong ? (' ' + dong) : '');
    openConfirm(
      '이 주소가 정확한가요?',
      '📍 ' + full + '\\n\\n기사님이 이 주소로 찾아가요. 맞으면 접수할게요.',
      function() {{ finalize(); }}
    );
  }}

  async function finalize() {{
    var btn = document.getElementById('q-submit');
    btn.disabled = true;
    btn.textContent = '제출 중...';
    var dong = (document.getElementById('q-dong').value || '').trim();
    var memo = (document.getElementById('q-memo').value || '').trim();
    var phone = (document.getElementById('q-phone').value || '').trim();
    var payload = {{
      token: TOKEN,
      contact_phone: phone,
      road_address: quoteAddr,
      building_detail: dong || null,
      memo: memo || null,
      source: buildSrc() || null,
    }};
    try {{
      var resp = await fetch('/api/intake-form/submit', {{
        method: 'POST',
        headers: {{ 'Content-Type': 'application/json' }},
        body: JSON.stringify(payload),
      }});
      if (resp.ok) {{
        document.querySelector('.q-scroll').innerHTML =
          '<div class="status-page"><h2 style="color:#16C172">✅ 접수 완료!</h2>'
          + '<p>시공접수서를 제출했어요.<br>사장님이 확인 후 시공일이 최종 확정돼요 😊</p></div>';
      }} else {{
        var err = await resp.json().catch(function() {{ return {{}}; }});
        var msg = err.detail;
        if (Array.isArray(msg)) {{
          msg = msg.map(function(d) {{
            return (d.loc ? d.loc[d.loc.length-1] + ': ' : '') + (d.msg || JSON.stringify(d));
          }}).join('\\n');
        }}
        alert('제출 실패 (' + resp.status + ')\\n' + (msg || ''));
        btn.disabled = false;
        btn.textContent = '접수 완료하기';
      }}
    }} catch (e) {{
      alert('네트워크 오류: ' + e.message);
      btn.disabled = false;
      btn.textContent = '접수 완료하기';
    }}
  }}

  function requestEdit() {{
    alert('사장님께 수정 요청 연락 부탁드려요.\\n(이 화면을 닫고 사장님께 문자/전화 주세요)');
  }}
</script>
</body>
</html>
"""


def _format_schedule_label(scheduled_at_ms: int, scheduled_days: int) -> str:
    """epoch ms → '5/31 (일요일)' 또는 '5/31 ~ 6/2 (3일간 시공)'.

    scheduled_at_ms == 0 → '미정 (사장님이 곧 알려드려요)'.
    """
    if not scheduled_at_ms or scheduled_at_ms <= 0:
        return "미정 (사장님이 곧 알려드려요)"
    import datetime
    # KST 변환 (UTC+9)
    dt = datetime.datetime.utcfromtimestamp(scheduled_at_ms / 1000) + datetime.timedelta(hours=9)
    wn = ["월", "화", "수", "목", "금", "토", "일"]
    wd = wn[dt.weekday()]
    if scheduled_days and scheduled_days > 1:
        end_dt = dt + datetime.timedelta(days=scheduled_days - 1)
        return f"{dt.month}/{dt.day} ~ {end_dt.month}/{end_dt.day} ({scheduled_days}일간 시공)"
    return f"{dt.month}/{dt.day} ({wd}요일)"


def _format_won(amount_krw: int) -> str:
    """1234567 → '1,234,567' (원 단위 thousands separator)."""
    try:
        return f"{int(amount_krw):,}"
    except (TypeError, ValueError):
        return "0"


def _build_items_html(items: list[dict]) -> str:
    """견적 항목 → HTML <div class='q-item'> 리스트."""
    import html as _html
    if not items:
        return '<div class="q-empty">견적 항목이 등록되지 않았어요.</div>'
    rows = []
    for it in items:
        name = _html.escape(str(it.get("name") or ""))
        price = int(it.get("price_man") or 0)
        unit_html = ""
        if (it.get("unit") == "pyeong") and it.get("area"):
            unit_html = (
                f' <span class="unit">({int(price)}만원/평 × '
                f'{it.get("area")}평)</span>'
            )
        rows.append(
            f'<div class="q-item"><span class="qi-n">{name}{unit_html}</span>'
            f'<span class="qi-p">{price}만원</span></div>'
        )
    return "".join(rows)


def _build_deposit_html(deposit_mode: str, deposit_amount_krw: int,
                       deposit_ratio_pct: Optional[int]) -> str:
    """계약금 안내 박스 HTML (프로토 q-deposit 1:1)."""
    if deposit_mode == "none" or not deposit_amount_krw:
        return ""
    amount = _format_won(deposit_amount_krw)
    suffix = ""
    if deposit_mode == "ratio" and deposit_ratio_pct:
        suffix = f" (총액의 {int(deposit_ratio_pct)}%)"
    return (
        f'<div class="q-deposit">계약금 {amount}원{suffix}'
        f' · 입금 계좌는 확정 후 안내드려요</div>'
    )


@app.get("/intake/{token}", response_class=HTMLResponse)
async def intake_form_page(token: str) -> HTMLResponse:
    """고객 브라우저용 폼 HTML (프로토 openQuote 1:1).

    토큰 유효/만료/이미 제출 상태에 따라 다른 페이지 반환.
    """
    import html as _html
    with db_conn() as con:
        row = con.execute(
            f"SELECT {_INTAKE_SELECT_COLS} FROM intake_forms WHERE token = ?",
            (token,),
        ).fetchone()
    if not row:
        return HTMLResponse(
            content="<html><body style='font-family:sans-serif;padding:40px;text-align:center'>"
                    "<h2 style='color:#F0436A'>❌ 유효하지 않은 링크</h2>"
                    "<p>사장님께 다시 링크를 받아 주세요.</p></body></html>",
            status_code=404,
        )

    data = _intake_row_to_dict(row)
    now = _now_ms()
    if data["submitted_at_ms"] is not None:
        return HTMLResponse(
            content="<html><body style='font-family:sans-serif;padding:40px;text-align:center'>"
                    "<h2 style='color:#16C172'>✅ 이미 제출된 접수서입니다</h2>"
                    "<p>접수 내용 확인은 사장님께 연락 주세요.</p></body></html>",
        )
    if now > data["expires_at_ms"]:
        return HTMLResponse(
            content="<html><body style='font-family:sans-serif;padding:40px;text-align:center'>"
                    "<h2 style='color:#F0436A'>⌛ 만료된 링크</h2>"
                    "<p>이 접수서 링크는 발급 7일이 지나 만료되었어요.<br>"
                    "사장님께 새 링크를 요청해 주세요.</p></body></html>",
            status_code=410,
        )

    biz = (data["biz_name"] or "").strip() or "RING-GO 시공"
    schedule_label = _format_schedule_label(data["scheduled_at_ms"], data["scheduled_days"])
    items_html = _build_items_html(data["estimate_items"])
    deposit_html = _build_deposit_html(
        data["deposit_mode"], data["deposit_amount_krw"], data["deposit_ratio_pct"]
    )

    page = INTAKE_FORM_HTML_TEMPLATE.format(
        biz_html=_html.escape(biz),
        biz_js=json.dumps(biz, ensure_ascii=False),
        schedule_label_html=_html.escape(schedule_label),
        items_html=items_html,
        total_man_html=_html.escape(str(int(data["total_man"] or 0))),
        deposit_html=deposit_html,
        token_js=_html.escape(token, quote=True),
    )
    return HTMLResponse(content=page)


# ============================================================================
# §19.2 — 시공접수서 v2 (사장님 2026-06-02 명세, 프로토 openQuote/openQuoteDoc 1:1)
# ─────────────────────────────────────────────────────────────────────────────
# 결정 (사장님 답):
#   1. camelCase API (앱 Kotlin 네이티브 일치)
#   2. 시공일 = month/day 분리 (프로토 quoteCfg.qmon/qday 그대로)
#   3. biz 전체 객체 + 견적서 직인 endpoint 추가
#   4. URL path = /q/{token}, /q/{token}/submit (짧음, SMS 친화)
#
# 5 endpoint:
#   - POST /api/quote/issue         (생성)
#   - GET  /q/{token}               (고객용 접수서 폼 — 프로토 openQuote 1:1)
#   - POST /q/{token}/submit        (고객 제출)
#   - GET  /api/quote/submissions   (사장님 폴링)
#   - GET  /q/{token}/doc           (견적서 직인 HTML — 프로토 openQuoteDoc 1:1)
#
# 기존 §19 의 /api/intake-form/* + /intake/{token} 은 alias 로 같은 DB 행 사용.
# 안드로이드가 새 path 로 옮길 동안 호환 유지.
# ============================================================================

# ─── Pydantic 모델 (camelCase, alias 패턴) ───
# Pydantic 의 Field(alias=...) 로 camelCase 입력 받고 내부는 snake_case 로 보관.

from pydantic import ConfigDict, AliasChoices


def _camel_model_config() -> "ConfigDict":
    return ConfigDict(populate_by_name=True)


class QuoteItemReq(BaseModel):
    """프로토 quoteItems 의 한 줄. price 는 만원 단위 (프로토 lineTotal 결과)."""
    model_config = _camel_model_config()
    name: str
    price: int = 0
    unit: Optional[str] = None       # 'flat' | 'pyeong'
    area: Optional[float] = None     # unit='pyeong' 일 때 평수


class QuoteBizInfo(BaseModel):
    """프로토 bizInfo (line 1781 부근) 1:1."""
    model_config = _camel_model_config()
    name: Optional[str] = None
    owner: Optional[str] = None
    bizNo: Optional[str] = None
    addr: Optional[str] = None
    phone: Optional[str] = None
    seal: Optional[str] = None       # 직인 문구 ("디테일라인 직인" 등)
    validDays: Optional[int] = None  # 견적서 유효기간


class QuoteIssueRequest(BaseModel):
    """프로토 finalizeQuote 가 보내는 quotePending 구조 + 발급 시점 데이터.

    한 줄로 보내면 서버가 토큰 발급 + DB 저장 + URL 반환.
    """
    model_config = _camel_model_config()
    customerName: Optional[str] = None
    customerPhone: str                                 # 고객 phone (필수)
    items: list[QuoteItemReq] = Field(default_factory=list)
    total: int = 0                                     # 합계 (만원)
    workMonth: int = 0                                 # 프로토 qmon (0 = 미정)
    workDay: int = 0                                   # 프로토 qday
    workYear: int = 2026                               # 프로토 qyear (default = 올해)
    workDays: int = 1                                  # 프로토 qdays (1=단일, 2+ = 기간)
    depositMode: str = "none"                          # 'none' | 'ratio' | 'fixed'
    depositValue: int = 0                              # ratio 면 %, fixed 면 원
    biz: Optional[QuoteBizInfo] = None
    # 메타
    devicePhone: Optional[str] = None                  # 사장님 phone (앱 식별/티어 검증)
    deviceId: Optional[str] = None


class QuoteSubmitRequest(BaseModel):
    """고객이 폼에서 [접수 완료하기] 누를 때 보내는 페이로드 (프로토 finalizeQuote).

    호환 alias: 옛 §19 폼 + 안드로이드 IntakeFormRepository 의 snake_case 필드도 받음.
    - phone / contact_phone
    - address / road_address
    - dong / building_detail
    - survey (dict) / source (str — 자동으로 survey:{source:...} 로 wrapping)
    """
    model_config = _camel_model_config()
    phone: str = Field(validation_alias=AliasChoices("phone", "contact_phone"))
    address: str = Field(validation_alias=AliasChoices("address", "road_address"))
    dong: Optional[str] = Field(
        default=None,
        validation_alias=AliasChoices("dong", "building_detail"),
    )
    memo: Optional[str] = None
    confirmedDate: Optional[str] = Field(
        default=None,
        validation_alias=AliasChoices("confirmedDate", "confirmed_date"),
    )
    survey: Optional[dict] = None                      # 유입경로 {source, keyword?, category?, etc?}
    # 옛 폼 alias — source 문자열 (서버에서 survey:{source:...} 로 wrapping)
    source: Optional[str] = None


# ─── helper ───

def _workdate_to_epoch_ms(year: int, month: int, day: int) -> int:
    """프로토 quoteCfg qyear/qmon/qday → KST 0시 epoch ms.

    year=0 또는 month=0 면 0 반환 (시공일 미정).
    """
    if not month or not day:
        return 0
    import datetime
    try:
        dt = datetime.datetime(year or 2026, month, day, 0, 0, 0)
        return int((dt - datetime.timedelta(hours=9)).timestamp() * 1000)
    except (ValueError, OverflowError):
        return 0


def _deposit_resolve_krw(total_man: int, mode: str, value: int) -> int:
    """프로토 lineTotal/depWonOf 로직 1:1.

    mode='ratio' → total_man * value% (만원 → 원 환산)
    mode='fixed' → value (이미 원 단위로 보낸다고 가정)
    mode='none' → 0
    """
    if mode == "ratio" and value:
        # 만원 → 원, * percent / 100
        return int(round(total_man * 10000 * value / 100))
    if mode == "fixed" and value:
        return int(value)
    return 0


def _persist_quote_issue_to_db(
    token: str, req: QuoteIssueRequest, biz: dict, now: int, expires_at: int,
) -> None:
    """QuoteIssueRequest 를 intake_forms 테이블에 INSERT.

    기존 §19 컬럼 (호환) + §19.2 새 컬럼 모두 채운다.
    """
    items_payload = [
        {"name": it.name, "price_man": int(it.price or 0),
         "unit": it.unit, "area": it.area}
        for it in (req.items or [])
    ]
    scheduled_at_ms = _workdate_to_epoch_ms(req.workYear, req.workMonth, req.workDay)
    deposit_krw = _deposit_resolve_krw(req.total or 0, req.depositMode, req.depositValue or 0)
    biz_name = (biz.get("name") or "").strip() or _fetch_owner_biz_name(req.devicePhone)

    with db_conn() as con:
        con.execute(
            """
            INSERT INTO intake_forms (
                token, phone, customer_name, issued_at_ms, expires_at_ms,
                submitted_at_ms, payload_json, device_id, owner_phone, created_at_ms,
                scheduled_at_ms, scheduled_days, estimate_items_json, total_man,
                deposit_amount_krw, deposit_mode, deposit_ratio_pct, biz_name,
                work_month, work_day, work_year, deposit_value,
                biz_owner, biz_no, biz_addr, biz_phone, biz_seal, biz_valid_days,
                confirmed_date_iso, survey_json
            ) VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?,
                      ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)
            """,
            (
                token, req.customerPhone, req.customerName, now, expires_at,
                req.deviceId, req.devicePhone, now,
                scheduled_at_ms, max(1, int(req.workDays or 1)),
                json.dumps(items_payload, ensure_ascii=False),
                int(req.total or 0),
                deposit_krw,
                req.depositMode,
                int(req.depositValue) if req.depositMode == "ratio" else None,
                biz_name or "",
                int(req.workMonth or 0) or None,
                int(req.workDay or 0) or None,
                int(req.workYear or 2026),
                int(req.depositValue or 0) or None,
                biz.get("owner") or None,
                biz.get("bizNo") or None,
                biz.get("addr") or None,
                biz.get("phone") or None,
                biz.get("seal") or None,
                int(biz["validDays"]) if biz.get("validDays") else None,
            ),
        )
        con.commit()


# ─── API 1: POST /api/quote/issue ───

@app.post("/api/quote/issue")
async def quote_issue(req: QuoteIssueRequest) -> dict:
    """접수서 토큰 발급. 사장님이 채팅 견적 시트에서 [시공접수서 보내기] 누름 → 앱이 호출.

    응답: {token, url, issuedAtMs, expiresAtMs, smsDraft}
    smsDraft = SMS 본문 prefill 용 한국어 문구 (자동발송 X, 사장님 ▶ 직접).
    """
    if not (req.customerPhone or "").strip():
        raise HTTPException(400, "customerPhone 필수")
    if req.depositMode not in ("none", "ratio", "fixed"):
        raise HTTPException(400, "depositMode 는 none/ratio/fixed")
    if req.workMonth and not (1 <= req.workMonth <= 12):
        raise HTTPException(400, "workMonth 1~12")
    if req.workDay and not (1 <= req.workDay <= 31):
        raise HTTPException(400, "workDay 1~31")

    now = _now_ms()
    token = _generate_intake_token()
    # 유효기간: biz.validDays 우선, 없으면 INTAKE_TTL_MS (7일)
    valid_days = (req.biz.validDays if (req.biz and req.biz.validDays) else None)
    if valid_days and valid_days > 0:
        expires_at = now + int(valid_days) * 24 * 60 * 60 * 1000
    else:
        expires_at = now + INTAKE_TTL_MS

    biz_dict = req.biz.model_dump(exclude_none=False) if req.biz else {}
    _persist_quote_issue_to_db(token, req, biz_dict, now, expires_at)

    url = f"{INTAKE_PUBLIC_BASE_URL.rstrip('/')}/q/{token}"
    biz_name = biz_dict.get("name") or _fetch_owner_biz_name(req.devicePhone) or "RING-GO 시공"
    sms_draft = (
        f"안녕하세요{(' ' + req.customerName + '님') if req.customerName else ''}, {biz_name} 입니다.\n"
        f"시공일 확정을 위해 접수서를 작성 부탁드려요. 1분이면 끝나요 😊\n"
        f"▶ {url}"
    )
    print(f"[quote/issue] customerPhone={req.customerPhone} → token={token} url={url}")
    return {
        "token": token,
        "url": url,
        "issuedAtMs": now,
        "expiresAtMs": expires_at,
        "smsDraft": sms_draft,
    }


# ─── API 2: GET /q/{token} ───
# 화면 자체는 기존 §19 의 INTAKE_FORM_HTML_TEMPLATE 재활용 (프로토 openQuote 1:1).
# 단 폼 제출 path 를 /q/{token}/submit 으로 박아야 하므로 별도 변형 템플릿 사용.

INTAKE_FORM_HTML_V2_TEMPLATE = INTAKE_FORM_HTML_TEMPLATE.replace(
    "/api/intake-form/submit",
    "__QUOTE_SUBMIT_PATH__",
)


def _render_quote_form_html(token: str, row: tuple) -> str:
    """row = SELECT _INTAKE_SELECT_COLS FROM intake_forms WHERE token=?

    프로토 openQuote 1:1 — schedule_label / items / deposit 박아 렌더.
    """
    import html as _html
    data = _intake_row_to_dict(row)
    biz = (data["biz_name"] or "").strip() or "RING-GO 시공"
    schedule_label = _format_schedule_label(data["scheduled_at_ms"], data["scheduled_days"])
    items_html = _build_items_html(data["estimate_items"])
    deposit_html = _build_deposit_html(
        data["deposit_mode"], data["deposit_amount_krw"], data["deposit_ratio_pct"]
    )
    # 폼 제출 경로 = /q/{token}/submit
    submit_path = f"/q/{token}/submit"
    page = (INTAKE_FORM_HTML_V2_TEMPLATE
            .replace("__QUOTE_SUBMIT_PATH__", submit_path)
            .format(
                biz_html=_html.escape(biz),
                biz_js=json.dumps(biz, ensure_ascii=False),
                schedule_label_html=_html.escape(schedule_label),
                items_html=items_html,
                total_man_html=_html.escape(str(int(data["total_man"] or 0))),
                deposit_html=deposit_html,
                token_js=_html.escape(token, quote=True),
            ))
    return page


def _quote_status_page(title: str, body: str, status_code: int = 200) -> HTMLResponse:
    """접수서 만료/제출됨/유효X 상태 페이지 공통."""
    html = (
        "<html><body style='font-family:-apple-system,sans-serif;padding:40px;text-align:center;background:#F4F5F7'>"
        f"<h2 style='color:#F0436A'>{title}</h2><p style='color:#5A6472;line-height:1.6'>{body}</p>"
        "</body></html>"
    )
    return HTMLResponse(content=html, status_code=status_code)


@app.get("/q/{token}", response_class=HTMLResponse)
async def quote_page(token: str) -> HTMLResponse:
    """고객 브라우저용 접수서 폼 (프로토 openQuote 1:1)."""
    with db_conn() as con:
        row = con.execute(
            f"SELECT {_INTAKE_SELECT_COLS} FROM intake_forms WHERE token = ?",
            (token,),
        ).fetchone()
    if not row:
        return _quote_status_page("❌ 유효하지 않은 링크", "사장님께 다시 링크를 받아 주세요.", 404)
    data = _intake_row_to_dict(row)
    now = _now_ms()
    if data["submitted_at_ms"] is not None:
        return _quote_status_page("✅ 이미 제출된 접수서입니다",
                                  "접수 내용 확인은 사장님께 연락 주세요.")
    if now > data["expires_at_ms"]:
        return _quote_status_page("⌛ 만료된 링크",
                                  "이 접수서 링크는 만료되었어요. 사장님께 새 링크를 요청해 주세요.", 410)
    return HTMLResponse(content=_render_quote_form_html(token, row))


# ─── API 3: POST /q/{token}/submit ───

@app.post("/q/{token}/submit")
async def quote_submit(token: str, req: QuoteSubmitRequest) -> dict:
    """고객 제출 (프로토 finalizeQuote). 응답: {ok, submittedAtMs, customerPhone}."""
    phone = (req.phone or "").strip()
    address = (req.address or "").strip()
    if not phone:
        raise HTTPException(400, "phone 필수")
    if not address:
        raise HTTPException(400, "address 필수")

    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            "SELECT phone, expires_at_ms, submitted_at_ms FROM intake_forms WHERE token = ?",
            (token,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "유효하지 않은 토큰")
        owner_customer_phone, expires_at, submitted_at = row
        if submitted_at is not None:
            raise HTTPException(409, "이미 제출된 접수서입니다")
        if now > expires_at:
            raise HTTPException(410, "만료된 접수서")

        # survey 우선순위: req.survey (dict) > req.source (str) — 옛 폼 호환
        effective_survey = req.survey
        if not effective_survey and req.source:
            effective_survey = {"source": req.source}

        payload = {
            "phone": phone,
            "address": address,
            "dong": (req.dong or "").strip() or None,
            "memo": (req.memo or "").strip() or None,
            "confirmedDate": (req.confirmedDate or "").strip() or None,
            # 호환 alias (구 schema — 안드로이드 옛 IntakeFormRepository)
            "contact_phone": phone,
            "road_address": address,
            "building_detail": (req.dong or "").strip() or None,
        }
        if effective_survey:
            payload["source"] = (effective_survey.get("source") or "") + \
                (' · "' + effective_survey["keyword"] + '"' if effective_survey.get("keyword") else "") + \
                (" · " + effective_survey["category"] if effective_survey.get("category") else "") + \
                (" · " + effective_survey["etc"] if effective_survey.get("etc") else "")
        elif req.source:
            # 옛 폼이 평탄화 source 문자열 직접 보낸 경우
            payload["source"] = req.source.strip() or None
        con.execute(
            "UPDATE intake_forms SET submitted_at_ms = ?, payload_json = ?, "
            "confirmed_date_iso = ?, survey_json = ? WHERE token = ?",
            (now, json.dumps(payload, ensure_ascii=False),
             (req.confirmedDate or "").strip() or None,
             json.dumps(effective_survey, ensure_ascii=False) if effective_survey else None,
             token),
        )
        con.commit()
    print(f"[quote/submit] token={token} customerPhone={owner_customer_phone} → submitted")
    return {"ok": True, "submittedAtMs": now, "customerPhone": owner_customer_phone}


# ─── API 4: GET /api/quote/submissions ───

@app.get("/api/quote/submissions")
async def quote_submissions_list(
    devicePhone: Optional[str] = None,
    deviceId: Optional[str] = None,
    sinceMs: int = 0,
    limit: int = 50,
) -> dict:
    """사장님 폴링 — 발급한 접수서들 최신순. 제출됨 + 미제출 모두 포함.

    응답: {items: [{token, customerPhone, customerName, issuedAtMs, expiresAtMs,
                    submittedAtMs|null, payload|null, total, workMonth, workDay, workDays,
                    biz, url}]}
    """
    limit = max(1, min(limit, 200))
    where_parts: list[str] = []
    params: list = []
    if devicePhone:
        where_parts.append("owner_phone = ?")
        params.append(devicePhone)
    if deviceId:
        where_parts.append("device_id = ?")
        params.append(deviceId)
    if sinceMs:
        where_parts.append("issued_at_ms > ?")
        params.append(sinceMs)
    where = ("WHERE " + " AND ".join(where_parts)) if where_parts else ""
    with db_conn() as con:
        rows = con.execute(
            f"SELECT {_INTAKE_SELECT_COLS}, work_month, work_day, work_year, "
            f"deposit_value, biz_owner, biz_no, biz_addr, biz_phone, biz_seal, "
            f"biz_valid_days, confirmed_date_iso, survey_json "
            f"FROM intake_forms {where} ORDER BY issued_at_ms DESC LIMIT ?",
            [*params, limit],
        ).fetchall()
    items = []
    for r in rows:
        base = _intake_row_to_dict(r[:18])  # 기존 18 컬럼
        # 새 컬럼 12 개 (work_month..survey_json)
        (wm, wd, wy, dv, bo, bn, ba, bp, bs, bvd, cdi, sj) = r[18:30]
        survey = None
        if sj:
            try:
                survey = json.loads(sj)
            except json.JSONDecodeError:
                survey = None
        items.append({
            **base,
            "customerPhone": base["phone"],
            "customerName": base["customer_name"],
            "issuedAtMs": base["issued_at_ms"],
            "expiresAtMs": base["expires_at_ms"],
            "submittedAtMs": base["submitted_at_ms"],
            "total": base["total_man"],
            "workMonth": wm, "workDay": wd, "workYear": wy, "workDays": base["scheduled_days"],
            "depositMode": base["deposit_mode"],
            "depositValue": dv,
            "biz": {
                "name": base["biz_name"], "owner": bo, "bizNo": bn,
                "addr": ba, "phone": bp, "seal": bs, "validDays": bvd,
            },
            "confirmedDate": cdi,
            "survey": survey,
        })
    return {"items": items, "count": len(items)}


# ─── API 5: GET /q/{token}/doc — 견적서 직인 HTML (프로토 openQuoteDoc 1:1) ───

QUOTE_DOC_HTML_TEMPLATE = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>견적서 · {biz_html}</title>
<style>
  :root {{
    --blue:#3182F6; --bg:#F4F5F7; --t1:#0B0F19; --t2:#5A6472; --t3:#9AA3AF; --line:#EEF0F3;
    --error:#F0436A;
  }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; padding:24px 16px; background:var(--bg);
         font-family:'Pretendard',-apple-system,system-ui,"Noto Sans KR",sans-serif;
         color:var(--t1); line-height:1.55; }}
  .wrap {{ max-width:480px; margin:0 auto; background:#fff; border-radius:14px;
          padding:28px 22px; box-shadow:0 2px 8px rgba(0,0,0,.06); }}
  .qd-title {{ text-align:center; font-size:23px; font-weight:800; letter-spacing:9px;
              color:#111; padding-left:9px; margin-bottom:16px; }}
  .qd-meta {{ display:flex; justify-content:space-between; font-size:11px; color:var(--t3);
             margin-bottom:14px; flex-wrap:wrap; gap:4px; }}
  .qd-to {{ font-size:15px; margin-bottom:14px; border-bottom:2px solid #111; padding-bottom:8px; }}
  .qd-table {{ width:100%; border-collapse:collapse; font-size:12.5px; }}
  .qd-table th {{ background:var(--bg); border:1px solid #cfd6df; padding:8px 6px; font-weight:800; }}
  .qd-table td {{ border:1px solid #cfd6df; padding:9px 8px; }}
  .qd-table .qd-n {{ font-weight:700; }}
  .qd-table .qd-u {{ color:var(--t3); text-align:center; font-size:11.5px; }}
  .qd-table .qd-a {{ text-align:right; font-weight:700; }}
  .qd-table .qd-sum td {{ background:#FAFBFC; font-weight:800; }}
  .qd-table .qd-sum td:last-child {{ color:var(--blue); text-align:right; }}
  .qd-notes {{ font-size:12px; color:var(--t2); line-height:1.9; margin-top:14px; }}
  .qd-dep {{ color:var(--error); font-weight:700; }}
  .qd-foot {{ display:flex; align-items:flex-end; justify-content:space-between;
             margin-top:24px; gap:10px; }}
  .qd-co-n {{ font-size:15px; font-weight:800; }}
  .qd-co-i {{ font-size:11px; color:var(--t3); margin-top:2px; }}
  .qd-seal {{ flex:0 0 auto; width:62px; height:62px; border-radius:50%;
             border:2.5px solid #d6342c; color:#d6342c;
             display:flex; align-items:center; justify-content:center;
             text-align:center; font-size:11px; font-weight:800; line-height:1.25;
             transform:rotate(-12deg); opacity:.85; }}
  @media print {{ body {{ background:#fff; padding:0; }} .wrap {{ box-shadow:none; border-radius:0; }} }}
</style>
</head>
<body>
<div class="wrap">
  <div class="qd-title">견 적 서</div>
  <div class="qd-meta">
    <span>발행일: {issued_label_html}</span>
    <span>유효기간: {valid_label_html}</span>
  </div>
  <div class="qd-to">수신: <b>{customer_label_html}</b> 귀하</div>

  <table class="qd-table">
    <thead>
      <tr><th>품목</th><th>단가</th><th>금액</th></tr>
    </thead>
    <tbody>
      {items_rows_html}
      <tr class="qd-sum">
        <td colspan="2">합계 (부가세 별도)</td>
        <td>{total_label_html}원</td>
      </tr>
    </tbody>
  </table>

  <div class="qd-notes">
    · 본 견적은 현장 상황 확인 후 일부 조정될 수 있어요.<br>
    {dep_row_html}
    · 시공일은 별도 안내드린 시공접수서에서 확인해 주세요.
  </div>

  <div class="qd-foot">
    <div>
      <div class="qd-co-n">{biz_html}</div>
      <div class="qd-co-i">{owner_label_html}{biz_no_html}{addr_html}{phone_html}</div>
    </div>
    <div class="qd-seal">{seal_label_html}</div>
  </div>
</div>
</body>
</html>
"""


def _format_quote_doc_label_won(amount_man: int) -> str:
    """만원 단위 → '1,234,567' 원 표기."""
    return f"{int(amount_man) * 10000:,}"


def _format_quote_doc_unit(it: dict) -> str:
    """단가 표기 — pyeong/flat 구분."""
    price = int(it.get("price_man") or 0)
    if it.get("unit") == "pyeong" and it.get("area"):
        return f"{price}만원/평 × {it.get('area')}평"
    return f"{price}만원"


def _format_quote_doc_items_rows(items: list[dict]) -> str:
    import html as _html
    if not items:
        return '<tr><td colspan="3" class="qd-u">견적 항목이 없습니다</td></tr>'
    rows = []
    for it in items:
        name = _html.escape(str(it.get("name") or ""))
        price = int(it.get("price_man") or 0)
        unit = _html.escape(_format_quote_doc_unit(it))
        # area * price (만원) → 원
        if it.get("unit") == "pyeong" and it.get("area"):
            amount_man = int(round(price * float(it["area"])))
        else:
            amount_man = price
        amount = f"{amount_man * 10000:,}"
        rows.append(
            f'<tr><td class="qd-n">{name}</td>'
            f'<td class="qd-u">{unit}</td>'
            f'<td class="qd-a">{amount}원</td></tr>'
        )
    return "".join(rows)


def _format_quote_doc_issue_date(issued_at_ms: int) -> str:
    import datetime
    dt = datetime.datetime.utcfromtimestamp(issued_at_ms / 1000) + datetime.timedelta(hours=9)
    return f"{dt.year}. {dt.month:02d}. {dt.day:02d}"


def _format_quote_doc_valid_label(expires_at_ms: int) -> str:
    import datetime
    dt = datetime.datetime.utcfromtimestamp(expires_at_ms / 1000) + datetime.timedelta(hours=9)
    return f"{dt.year}. {dt.month:02d}. {dt.day:02d} 까지"


@app.get("/q/{token}/doc", response_class=HTMLResponse)
async def quote_doc_page(token: str) -> HTMLResponse:
    """견적서 직인 HTML (프로토 openQuoteDoc 1:1).

    같은 token 으로 발급된 견적 데이터를 표·직인 형태로 렌더.
    인쇄(브라우저 [인쇄]) 또는 캡쳐로 PDF 변환 가능.
    """
    import html as _html
    with db_conn() as con:
        row = con.execute(
            f"SELECT {_INTAKE_SELECT_COLS}, biz_owner, biz_no, biz_addr, biz_phone, biz_seal "
            f"FROM intake_forms WHERE token = ?",
            (token,),
        ).fetchone()
    if not row:
        return _quote_status_page("❌ 유효하지 않은 견적서 링크", "사장님께 다시 링크를 받아 주세요.", 404)
    base = _intake_row_to_dict(row[:18])
    (biz_owner, biz_no, biz_addr, biz_phone, biz_seal) = row[18:23]

    biz = (base["biz_name"] or "").strip() or "RING-GO 시공"
    customer = (base["customer_name"] or base["phone"] or "고객")
    items = base["estimate_items"]
    items_rows = _format_quote_doc_items_rows(items)
    total_label = _format_quote_doc_label_won(base["total_man"])
    issued_label = _format_quote_doc_issue_date(base["issued_at_ms"])
    valid_label = _format_quote_doc_valid_label(base["expires_at_ms"])

    dep_row = ""
    if base["deposit_mode"] != "none" and base["deposit_amount_krw"]:
        amount = f"{int(base['deposit_amount_krw']):,}"
        suffix = ""
        if base["deposit_mode"] == "ratio" and base["deposit_ratio_pct"]:
            suffix = f" (총액의 {int(base['deposit_ratio_pct'])}%)"
        dep_row = f'· <span class="qd-dep">계약금 {amount}원{suffix}</span> 입금 시 시공일 확정<br>'

    # 사업자정보 라인
    owner_label = (biz_owner or "")
    if owner_label:
        owner_label = _html.escape(f"대표 {owner_label}")
    biz_no_html = (f" · 사업자 {_html.escape(biz_no)}" if biz_no else "")
    addr_html = (f" · {_html.escape(biz_addr)}" if biz_addr else "")
    phone_html = (f" · ☎ {_html.escape(biz_phone)}" if biz_phone else "")
    seal_label = _html.escape((biz_seal or biz)[:8])  # 직인 안에 8자 컷

    page = QUOTE_DOC_HTML_TEMPLATE.format(
        biz_html=_html.escape(biz),
        customer_label_html=_html.escape(customer),
        issued_label_html=_html.escape(issued_label),
        valid_label_html=_html.escape(valid_label),
        items_rows_html=items_rows,
        total_label_html=_html.escape(total_label),
        dep_row_html=dep_row,
        owner_label_html=owner_label,
        biz_no_html=biz_no_html,
        addr_html=addr_html,
        phone_html=phone_html,
        seal_label_html=seal_label,
    )
    return HTMLResponse(content=page)


# ============================================================================
# §20 — 팀 관리 (99k 티어) — 프로토 1:1
# ─────────────────────────────────────────────────────────────────────────────
# 정답 스펙: design-preview/ringgo-redesign.html 의
#   - team 배열 (line 1597~), openAddMember/renderTeam (line 2371~),
#   - openMemberView (line 2391~) "내 일정" URL 화면,
#   - memberPhotos/teamPhotoAlert (line 2417~) 팀원 사진 업로드,
#   - departed 출발 알림 (line 2390~).
#
# 핵심 결정 (사장님):
# - 역할 2개: 'owner' (대표) / 'worker' (팀원). 프로토 그대로.
# - 팀원 화면 = URL 링크 (접수서 패턴). 앱 설치 X. 시공 다음날 자정 만료.
# - 현장 배정 = 안드로이드 측 customers 테이블의 assigned_member_phone (서버는 신호만).
# - 99k 티어 (subscribers.plan_tier == 'team_99k') 검증.
# - 자동 SMS 발송 X — 초대 URL 은 발급만, 발송은 앱 ▶.
# ============================================================================

TEAM_LINK_TOKEN_LEN = 10  # 팀원 링크는 좀 더 긴 토큰
TEAM_TIER_NAMES = {"team_99k", "team", "team_99000"}  # 어느 식별자든 통과


def _generate_team_token() -> str:
    """팀원 링크용 10자 base62 토큰 (INTAKE_TOKEN_ALPHABET 재사용)."""
    import secrets
    for _ in range(8):
        tok = "".join(secrets.choice(INTAKE_TOKEN_ALPHABET) for _ in range(TEAM_LINK_TOKEN_LEN))
        with db_conn() as con:
            row = con.execute(
                "SELECT 1 FROM team_member_links WHERE token = ? LIMIT 1", (tok,)
            ).fetchone()
            if not row:
                return tok
    raise HTTPException(500, "팀 토큰 생성 실패")


def _generate_member_id() -> str:
    """팀원 ID 'tm_' + 8자 base62."""
    import secrets
    for _ in range(8):
        mid = "tm_" + "".join(secrets.choice(INTAKE_TOKEN_ALPHABET) for _ in range(8))
        with db_conn() as con:
            row = con.execute(
                "SELECT 1 FROM team_members WHERE member_id = ?", (mid,)
            ).fetchone()
            if not row:
                return mid
    raise HTTPException(500, "팀원 ID 생성 실패")


def _check_team_tier(owner_phone: str) -> None:
    """subscribers 의 plan_tier 가 team_99k 인지 검증. 미가입은 403.

    개발용 우회: ENV `TEAM_TIER_BYPASS=1` 이면 무조건 통과 (사장님 테스트 편의).
    """
    if os.environ.get("TEAM_TIER_BYPASS") == "1":
        return
    if not owner_phone:
        raise HTTPException(403, "owner_phone 필수")
    with db_conn() as con:
        row = con.execute(
            "SELECT plan_tier, churned_at_ms FROM subscribers WHERE phone = ?",
            (owner_phone,),
        ).fetchone()
    if not row:
        raise HTTPException(
            403,
            "subscribers 에 등록되지 않은 사장님입니다. 99k 가입 후 다시 시도해 주세요."
        )
    plan_tier, churned_at = row
    if churned_at is not None:
        raise HTTPException(403, "구독이 해지된 사장님입니다.")
    if plan_tier not in TEAM_TIER_NAMES:
        raise HTTPException(403, f"99k(팀) 요금제 필요. 현재 tier: {plan_tier}")


def _team_link_expiry_default(scheduled_at_ms: int = 0) -> int:
    """URL 만료 시각 — 시공일이 박혀있으면 시공 다음날 자정(KST), 아니면 30일 후."""
    now = _now_ms()
    if scheduled_at_ms and scheduled_at_ms > 0:
        # 시공 다음날 자정 KST = 시공일 (KST 자정) + 2일 (KST 23:59:59)
        import datetime
        dt = datetime.datetime.utcfromtimestamp(scheduled_at_ms / 1000) + datetime.timedelta(hours=9)
        # 시공일 다음날 자정
        midnight_kst = (dt.replace(hour=0, minute=0, second=0, microsecond=0)
                        + datetime.timedelta(days=2))
        # KST → UTC ms
        return int((midnight_kst - datetime.timedelta(hours=9)).timestamp() * 1000)
    return now + 30 * 24 * 60 * 60 * 1000  # 30일


# ─── 모델 (Pydantic) ───

class TeamInviteRequest(BaseModel):
    owner_phone: str                       # 사장님 phone
    name: str                              # 팀원 이름 (예: '김기사')
    phone: str                             # 팀원 phone
    role: str = "worker"                   # 'owner' | 'worker' (프로토에서 사장님 자신은 명시 추가 없이도 OK)
    tint: int = 0


class TeamMemberOut(BaseModel):
    member_id: str
    owner_phone: str
    phone: str
    name: str
    role: str
    tint: int
    created_at_ms: int


class TeamScheduleSnapshot(BaseModel):
    """사장님이 박는 팀원별 일정 데이터 (URL 화면에 표시될 내용)."""
    member_id: str
    items: list[dict] = Field(default_factory=list)
    # 각 item: {when, customer_label, addr, time, work_summary, memo,
    #          customer_phone_masked, days, is_today?}


class TeamDepartEventRequest(BaseModel):
    token: str
    departed_at_ms: Optional[int] = None       # default = now


class TeamArriveEventRequest(BaseModel):
    token: str
    arrived_at_ms: Optional[int] = None


class TeamPhotoUploadRequest(BaseModel):
    token: str
    label: Optional[str] = None                # '시공 전'|'시공 중'|'시공 후'|'추가 사진'
    image_data_url: Optional[str] = None       # base64 (작은 사진)
    note: Optional[str] = None


# ─── API 1: 팀원 초대 (이름 + 전화 + URL 발급) ───
# 프로토 openAddMember/addMemberSubmit 1:1 — 자동발송 X, URL 만 반환 → 앱이 SMS prefill.

@app.post("/api/team/member/invite")
async def team_member_invite(req: TeamInviteRequest) -> dict:
    """팀원 추가 + URL 토큰 발급.

    응답: {member_id, name, role, token, url, expires_at_ms, sms_draft}
    sms_draft = 사장님이 SMS 본문 prefill 용 문구 (자동발송 X).
    """
    _check_team_tier(req.owner_phone)
    name = (req.name or "").strip()
    phone = (req.phone or "").strip()
    if not name or not phone:
        raise HTTPException(400, "name, phone 필수")
    role = req.role if req.role in ("owner", "worker") else "worker"

    now = _now_ms()
    # 이미 같은 phone 으로 등록된 팀원 있으면 재활용 (removed_at_ms 가 null 인 경우)
    with db_conn() as con:
        existing = con.execute(
            "SELECT member_id FROM team_members WHERE owner_phone = ? AND phone = ? "
            "AND removed_at_ms IS NULL",
            (req.owner_phone, phone),
        ).fetchone()
        if existing:
            member_id = existing[0]
        else:
            member_id = _generate_member_id()
            con.execute(
                """
                INSERT INTO team_members (member_id, owner_phone, phone, name, role,
                                          tint, created_at_ms, removed_at_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL)
                """,
                (member_id, req.owner_phone, phone, name, role, int(req.tint or 0), now),
            )
        # 토큰 발급 (30일 default — 일정이 박힐 때 refresh-link 로 시공 다음날 자정 재발급)
        token = _generate_team_token()
        expires_at = _team_link_expiry_default(0)
        con.execute(
            """
            INSERT INTO team_member_links
                (token, member_id, owner_phone, issued_at_ms, expires_at_ms,
                 schedule_snapshot_json, last_accessed_ms)
            VALUES (?, ?, ?, ?, ?, NULL, NULL)
            """,
            (token, member_id, req.owner_phone, now, expires_at),
        )
        con.commit()
    url = f"{INTAKE_PUBLIC_BASE_URL.rstrip('/')}/team/member/{token}"
    sms_draft = (
        f"{name}님 안녕하세요, RING-GO 팀원 화면 링크예요.\n"
        f"앱 설치 없이 바로 열리고, 배정된 현장과 주소만 보여요.\n{url}"
    )
    print(f"[team/invite] {req.owner_phone} → member={member_id} ({name}) token={token}")
    return {
        "member_id": member_id,
        "name": name,
        "role": role,
        "token": token,
        "url": url,
        "issued_at_ms": now,
        "expires_at_ms": expires_at,
        "sms_draft": sms_draft,
    }


# ─── API 2: 팀원 목록 ───

@app.get("/api/team/members")
async def team_member_list(owner_phone: str, include_removed: bool = False) -> dict:
    """사장님 팀원 목록 (프로토 renderTeam).

    응답: {items: [{member_id, name, role, tint, phone, created_at_ms, ...}]}
    """
    if not owner_phone:
        raise HTTPException(400, "owner_phone 필수")
    where = "WHERE owner_phone = ?"
    if not include_removed:
        where += " AND removed_at_ms IS NULL"
    with db_conn() as con:
        rows = con.execute(
            f"SELECT member_id, owner_phone, phone, name, role, tint, "
            f"created_at_ms, removed_at_ms "
            f"FROM team_members {where} ORDER BY created_at_ms ASC",
            (owner_phone,),
        ).fetchall()
    items = [
        {
            "member_id": r[0], "owner_phone": r[1], "phone": r[2], "name": r[3],
            "role": r[4], "tint": r[5], "created_at_ms": r[6],
            "removed_at_ms": r[7],
        }
        for r in rows
    ]
    return {"items": items, "count": len(items)}


# ─── API 3: 팀원 제외 (프로토 removeMember — 링크 차단) ───

@app.delete("/api/team/member/{member_id}")
async def team_member_remove(member_id: str, owner_phone: str) -> dict:
    """팀원 제외. 토큰들도 만료시킴 (URL 차단)."""
    if not owner_phone:
        raise HTTPException(400, "owner_phone 필수")
    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            "SELECT owner_phone, name FROM team_members WHERE member_id = ?",
            (member_id,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "팀원 없음")
        if row[0] != owner_phone:
            raise HTTPException(403, "다른 사장님의 팀원입니다")
        con.execute(
            "UPDATE team_members SET removed_at_ms = ? WHERE member_id = ?",
            (now, member_id),
        )
        # 모든 토큰 즉시 만료
        con.execute(
            "UPDATE team_member_links SET expires_at_ms = ? "
            "WHERE member_id = ? AND expires_at_ms > ?",
            (now, member_id, now),
        )
        con.commit()
    return {"ok": True, "member_id": member_id, "removed_at_ms": now}


# ─── API 4: 사장님이 팀원 일정 snapshot 갱신 ───
# 안드로이드는 배정 정보가 바뀔 때마다 이걸 호출. URL 화면에 표시될 내용 박는다.

@app.post("/api/team/schedule-snapshot")
async def team_schedule_snapshot(req: TeamScheduleSnapshot) -> dict:
    """팀원의 현재 활성 토큰에 일정 데이터 박음.

    items 예: [
      {when:'오늘', customer_label:'강동 천호동 현장',
       time:'09:00', addr:'서울 강동구 ...', work_summary:'욕실 2곳 줄눈 + 코킹',
       memo:'현관 비번 1234#', days:1, is_today:true,
       scheduled_at_ms: 1779840000000}
    ]
    """
    with db_conn() as con:
        # 가장 최근 활성 토큰
        row = con.execute(
            "SELECT token FROM team_member_links WHERE member_id = ? "
            "AND expires_at_ms > ? ORDER BY issued_at_ms DESC LIMIT 1",
            (req.member_id, _now_ms()),
        ).fetchone()
        if not row:
            raise HTTPException(404, "활성 토큰 없음 — invite 또는 refresh-link 호출")
        token = row[0]
        # 첫 item 의 scheduled_at_ms → 만료 자동 갱신 (시공 다음날 자정)
        scheduled_at_ms = 0
        for it in (req.items or []):
            v = it.get("scheduled_at_ms") if isinstance(it, dict) else None
            if v and (not scheduled_at_ms or v > scheduled_at_ms):
                scheduled_at_ms = int(v)
        new_expiry = _team_link_expiry_default(scheduled_at_ms)
        con.execute(
            "UPDATE team_member_links SET schedule_snapshot_json = ?, expires_at_ms = ? "
            "WHERE token = ?",
            (json.dumps(req.items, ensure_ascii=False), new_expiry, token),
        )
        con.commit()
    return {"ok": True, "token": token, "expires_at_ms": new_expiry,
            "items_count": len(req.items or [])}


# ─── API 5: 사장님 polling — 팀원 이벤트 (출발/사진/도착) ───

@app.get("/api/team/events")
async def team_events_list(owner_phone: str, since_ms: int = 0, limit: int = 30) -> dict:
    """팀원이 발생시킨 이벤트들 (출발/사진/도착) 시간순.

    응답: {events: [{event_id, member_id, member_name, event_type, payload, created_at_ms}]}
    """
    if not owner_phone:
        raise HTTPException(400, "owner_phone 필수")
    limit = max(1, min(limit, 200))
    with db_conn() as con:
        rows = con.execute(
            """
            SELECT e.event_id, e.member_id, m.name, e.event_type, e.payload_json, e.created_at_ms
            FROM team_member_events e
            LEFT JOIN team_members m ON m.member_id = e.member_id
            WHERE e.owner_phone = ? AND e.created_at_ms > ?
            ORDER BY e.created_at_ms DESC LIMIT ?
            """,
            (owner_phone, since_ms, limit),
        ).fetchall()
    events = []
    for r in rows:
        payload = None
        if r[4]:
            try:
                payload = json.loads(r[4])
            except json.JSONDecodeError:
                payload = None
        events.append({
            "event_id": r[0],
            "member_id": r[1],
            "member_name": r[2],
            "event_type": r[3],
            "payload": payload,
            "created_at_ms": r[5],
        })
    return {"events": events, "count": len(events)}


# ─── API 6: 팀원 [출발] ───
# 프로토 doDepart() — 팀원이 URL 화면에서 [출발] 누름.

@app.post("/api/team/event/depart")
async def team_event_depart(req: TeamDepartEventRequest) -> dict:
    """팀원이 [출발] 누름. 사장님 측 알림 카드 트리거.

    응답: {ok, event_id, departed_at_ms}
    """
    with db_conn() as con:
        row = con.execute(
            "SELECT member_id, owner_phone, expires_at_ms, schedule_snapshot_json "
            "FROM team_member_links WHERE token = ?",
            (req.token,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "유효하지 않은 토큰")
        member_id, owner_phone, expires_at, snap = row
        now = _now_ms()
        if now > expires_at:
            raise HTTPException(410, "만료된 링크")
        departed_at = int(req.departed_at_ms or now)
        # snapshot 의 첫 today item 의 customer_label·addr 끌어와 payload 에
        payload = {"departed_at_ms": departed_at}
        if snap:
            try:
                items = json.loads(snap) or []
                today = next((it for it in items if it.get("is_today")), None) or (items[0] if items else None)
                if today:
                    payload["customer_label"] = today.get("customer_label")
                    payload["addr"] = today.get("addr")
                    payload["time"] = today.get("time")
            except json.JSONDecodeError:
                pass
        cur = con.execute(
            """
            INSERT INTO team_member_events
                (token, member_id, owner_phone, event_type, payload_json, created_at_ms)
            VALUES (?, ?, ?, 'departed', ?, ?)
            """,
            (req.token, member_id, owner_phone,
             json.dumps(payload, ensure_ascii=False), now),
        )
        event_id = cur.lastrowid
        con.execute(
            "UPDATE team_member_links SET last_accessed_ms = ? WHERE token = ?",
            (now, req.token),
        )
        con.commit()
    print(f"[team/depart] token={req.token} member={member_id} → event_id={event_id}")
    return {"ok": True, "event_id": event_id, "departed_at_ms": departed_at}


# ─── API 7: 팀원 [도착] ───

@app.post("/api/team/event/arrive")
async def team_event_arrive(req: TeamArriveEventRequest) -> dict:
    """팀원이 현장 도착. 출발과 같은 패턴."""
    with db_conn() as con:
        row = con.execute(
            "SELECT member_id, owner_phone, expires_at_ms "
            "FROM team_member_links WHERE token = ?",
            (req.token,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "유효하지 않은 토큰")
        member_id, owner_phone, expires_at = row
        now = _now_ms()
        if now > expires_at:
            raise HTTPException(410, "만료된 링크")
        arrived_at = int(req.arrived_at_ms or now)
        cur = con.execute(
            """
            INSERT INTO team_member_events
                (token, member_id, owner_phone, event_type, payload_json, created_at_ms)
            VALUES (?, ?, ?, 'arrived', ?, ?)
            """,
            (req.token, member_id, owner_phone,
             json.dumps({"arrived_at_ms": arrived_at}, ensure_ascii=False), now),
        )
        event_id = cur.lastrowid
        con.commit()
    return {"ok": True, "event_id": event_id, "arrived_at_ms": arrived_at}


# ─── API 8: 팀원 사진 업로드 ───
# 프로토 memberUpload() — 시공 전/중/후 라벨 + base64 이미지.

@app.post("/api/team/event/photo")
async def team_event_photo(req: TeamPhotoUploadRequest) -> dict:
    """팀원이 현장 사진 업로드. label = 시공 전/중/후/추가.

    image_data_url 은 base64 data URL (작은 사진만, 200KB 컷 권장).
    응답: {ok, photo_id, event_id}
    """
    with db_conn() as con:
        row = con.execute(
            "SELECT member_id, owner_phone, expires_at_ms "
            "FROM team_member_links WHERE token = ?",
            (req.token,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "유효하지 않은 토큰")
        member_id, owner_phone, expires_at = row
        now = _now_ms()
        if now > expires_at:
            raise HTTPException(410, "만료된 링크")
        # base64 안전 컷 (1MB 초과 시 거부 — 디스크 저장은 Phase B)
        data_url = req.image_data_url or ""
        if data_url and len(data_url) > 1_400_000:  # 약 1MB base64
            raise HTTPException(413, "사진 용량 초과 (1MB 이하만)")
        label = (req.label or "").strip() or "추가 사진"
        cur = con.execute(
            """
            INSERT INTO team_site_photos
                (token, member_id, owner_phone, label, image_data_url, image_path,
                 note, uploaded_at_ms)
            VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """,
            (req.token, member_id, owner_phone, label, data_url or None,
             (req.note or "").strip() or None, now),
        )
        photo_id = cur.lastrowid
        # 동시에 event 로도 기록 (사장님 polling 알림용)
        cur2 = con.execute(
            """
            INSERT INTO team_member_events
                (token, member_id, owner_phone, event_type, payload_json, created_at_ms)
            VALUES (?, ?, ?, 'photo', ?, ?)
            """,
            (req.token, member_id, owner_phone,
             json.dumps({"photo_id": photo_id, "label": label}, ensure_ascii=False), now),
        )
        event_id = cur2.lastrowid
        con.commit()
    return {"ok": True, "photo_id": photo_id, "event_id": event_id, "label": label}


# ─── API 9: 사진 목록 (사장님이 봄) ───

@app.get("/api/team/photos")
async def team_photos_list(
    owner_phone: str,
    member_id: Optional[str] = None,
    since_ms: int = 0,
    limit: int = 50,
) -> dict:
    """사장님 측에서 팀원이 올린 사진 조회 (프로토 openTeamPhotoView)."""
    if not owner_phone:
        raise HTTPException(400, "owner_phone 필수")
    limit = max(1, min(limit, 200))
    where = "WHERE owner_phone = ? AND uploaded_at_ms > ?"
    params: list = [owner_phone, since_ms]
    if member_id:
        where += " AND member_id = ?"
        params.append(member_id)
    with db_conn() as con:
        rows = con.execute(
            f"SELECT photo_id, member_id, label, image_data_url, image_path, "
            f"note, uploaded_at_ms FROM team_site_photos {where} "
            f"ORDER BY uploaded_at_ms DESC LIMIT ?",
            [*params, limit],
        ).fetchall()
    photos = [
        {
            "photo_id": r[0], "member_id": r[1], "label": r[2],
            "image_data_url": r[3], "image_path": r[4],
            "note": r[5], "uploaded_at_ms": r[6],
        }
        for r in rows
    ]
    return {"photos": photos, "count": len(photos)}


# ─── URL HTML 화면 (프로토 openMemberView 1:1) ───
# 팀원 폰 브라우저에서 /team/member/{token} 열면 보는 화면.
# "🔗 링크로 열린 화면 (앱 설치 불필요) / 대표님이 배정한 일정만 보여요"

TEAM_MEMBER_HTML_TEMPLATE = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=yes,maximum-scale=5">
<title>{member_name_html} · 내 일정</title>
<style>
  :root {{
    --blue:#3182F6; --blue-dark:#1B64DA; --blue-tint:#EEF4FF;
    --bg:#F4F5F7; --card:#FFFFFF;
    --t1:#0B0F19; --t2:#5A6472; --t3:#9AA3AF; --line:#EEF0F3;
    --error:#F0436A; --success:#16C172;
    --shadow:0 1px 3px rgba(0,0,0,.04);
  }}
  * {{ box-sizing:border-box; -webkit-tap-highlight-color:transparent; }}
  html, body {{ margin:0; padding:0; background:var(--bg); }}
  body {{
    font-family:'Pretendard',-apple-system,BlinkMacSystemFont,system-ui,"Apple SD Gothic Neo","Noto Sans KR",sans-serif;
    color:var(--t1); line-height:1.5;
  }}
  .wrap {{ max-width:480px; margin:0 auto; min-height:100vh; display:flex; flex-direction:column; }}

  .appbar {{ display:flex; align-items:center; padding:14px 18px 12px; background:#fff; border-bottom:1px solid var(--line); }}
  .appbar .title {{ font-size:15px; font-weight:800; color:var(--t1); }}
  .appbar .me {{ margin-left:auto; font-size:11.5px; font-weight:700; color:var(--t3); display:inline-flex; align-items:center; gap:5px; }}
  .appbar .me .d {{ width:7px; height:7px; border-radius:50%; background:var(--success); }}

  .mv-note {{ background:#FFF8E1; color:#7A5A00; font-size:12px; padding:10px 16px; line-height:1.5; }}
  .mv-note b {{ color:var(--blue-dark); }}

  .scroll {{ flex:1; min-height:0; overflow-y:auto; padding:14px 16px 16px; }}
  .sec-sub {{ font-size:11.5px; font-weight:800; color:var(--t3); margin:14px 2px 8px; letter-spacing:.02em; }}
  .sec-sub:first-child {{ margin-top:4px; }}

  .card {{ background:#fff; border-radius:14px; padding:15px; margin-bottom:10px; box-shadow:var(--shadow); }}
  .card .row {{ display:flex; align-items:center; gap:8px; }}
  .card .hd {{ width:8px; height:8px; border-radius:50%; background:var(--blue); flex-shrink:0; }}
  .card .hd.hot {{ background:var(--error); }}
  .card .name {{ font-size:15px; font-weight:800; color:var(--t1); }}
  .card .time {{ margin-left:auto; font-size:12.5px; font-weight:800; color:var(--blue); background:var(--blue-tint); padding:3px 9px; border-radius:8px; }}
  .card .preview {{ font-size:13.5px; color:var(--t2); margin-top:9px; line-height:1.55; }}
  .card .work {{ font-size:13px; color:var(--t2); margin-top:8px; }}

  .hbtn {{
    display:inline-flex; align-items:center; justify-content:center; gap:6px;
    background:var(--bg); color:var(--blue); border:0; border-radius:11px;
    padding:11px 14px; font-size:13.5px; font-weight:800; font-family:inherit;
    cursor:pointer; min-height:42px;
  }}
  .mv-depart {{
    flex:1; background:var(--blue); color:#fff; border:0; border-radius:11px;
    padding:12px; font-size:14.5px; font-weight:800; font-family:inherit; cursor:pointer;
    display:inline-flex; align-items:center; justify-content:center; gap:7px; min-height:46px;
  }}
  .mv-depart:disabled {{ background:#9AA3AF; cursor:default; }}
  .mv-depart.done {{ background:var(--success); }}
  .nav-chip {{
    background:#fff; border:1.5px solid var(--line); border-radius:999px;
    padding:8px 14px; font-size:12.5px; font-weight:700; color:var(--t1);
    font-family:inherit; cursor:pointer; min-height:38px;
  }}
  .nav-chip:active {{ background:var(--blue-tint); border-color:var(--blue); color:var(--blue); }}

  .mv-photos {{ background:#fff; border-radius:14px; padding:14px; margin-top:10px; box-shadow:var(--shadow); }}
  .mv-ph-top {{ display:flex; align-items:center; gap:7px; font-size:13.5px; font-weight:800; color:var(--t1); }}
  .mv-ph-top .mv-ph-sub {{ margin-left:auto; font-size:11.5px; font-weight:700; color:var(--t3); }}
  .ph-help {{ font-size:11.5px; color:var(--t3); margin-top:5px; line-height:1.5; }}
  .photo-grid {{ display:grid; grid-template-columns:repeat(3, 1fr); gap:8px; margin-top:11px; }}
  .photo-thumb {{
    aspect-ratio:1; border-radius:10px; background:var(--bg);
    display:flex; flex-direction:column; align-items:center; justify-content:center;
    font-size:11px; color:var(--t3); cursor:pointer; position:relative;
    border:1.5px dashed var(--line);
  }}
  .photo-thumb.uploaded {{ background:#E7F8EF; border:0; color:var(--success); font-weight:800; }}
  .photo-thumb .pl {{ font-size:11px; font-weight:800; }}
  .photo-thumb .ph-sent {{ position:absolute; top:6px; right:6px; font-size:11px; }}

  .empty {{ font-size:13px; color:var(--t3); text-align:center; padding:30px 16px; line-height:1.6; }}

  .foot-note {{ font-size:12px; color:var(--t3); text-align:center; margin-top:20px; line-height:1.6; }}
  .foot-link {{ font-size:11.5px; color:var(--t3); text-align:center; margin-top:12px; background:var(--bg); border-radius:10px; padding:9px; }}

  .status-page {{ padding:60px 24px; text-align:center; }}
  .status-page h2 {{ font-size:22px; font-weight:800; }}
  .status-page p {{ font-size:14px; color:var(--t2); line-height:1.6; margin-top:10px; }}
</style>
</head>
<body>
<div class="wrap">

  <div class="appbar">
    <div class="title">내 일정</div>
    <span class="me"><span class="d"></span>{member_name_html}</span>
  </div>

  <div class="mv-note">
    🔗 링크로 열린 화면 (앱 설치 불필요)<br>
    <b>{owner_label_html}</b>이(가) 배정한 일정만 보여요 · 고객 연락처·매출은 안 보여요
  </div>

  <div class="scroll">
    {today_block}
    {next_block}
    <div class="foot-note">상담·정산·통계·고객정보는 대표님만 봐요.<br>나는 내 현장만 깔끔하게 ✓</div>
    <div class="foot-link">🔗 이 링크는 {expiry_label_html} 자정에 만료돼요</div>
  </div>

</div>

<script>
  var TOKEN = "{token_js}";
  var DEPARTED = false;

  async function doDepart() {{
    if (DEPARTED) return;
    var btn = document.getElementById('mv-depart');
    btn.disabled = true;
    btn.textContent = '전송 중...';
    try {{
      var resp = await fetch('/api/team/event/depart', {{
        method:'POST',
        headers:{{'Content-Type':'application/json'}},
        body: JSON.stringify({{token: TOKEN}}),
      }});
      if (resp.ok) {{
        DEPARTED = true;
        btn.classList.add('done');
        btn.disabled = true;
        btn.innerHTML = '✓ 출발 알림 보냄';
      }} else {{
        var err = await resp.json().catch(function(){{return{{}};}});
        alert('실패: ' + (err.detail || resp.status));
        btn.disabled = false;
        btn.innerHTML = '🚗 출발';
      }}
    }} catch (e) {{
      alert('네트워크 오류');
      btn.disabled = false;
      btn.innerHTML = '🚗 출발';
    }}
  }}

  function copyAddr() {{
    var addr = (document.getElementById('today-addr')||{{}}).textContent || '';
    addr = addr.replace(/^📍\\s*/, '');
    if (!addr) return;
    if (navigator.clipboard) navigator.clipboard.writeText(addr);
    alert('주소 복사됨\\n' + addr);
  }}

  function openNav(app) {{
    var addr = (document.getElementById('today-addr')||{{}}).textContent || '';
    addr = encodeURIComponent(addr.replace(/^📍\\s*/, ''));
    var url = '';
    if (app === '카카오맵')   url = 'https://map.kakao.com/?q=' + addr;
    else if (app === '티맵') url = 'tmap://search?name=' + addr;
    else                      url = 'https://map.kakao.com/?q=' + addr;
    window.location.href = url;
  }}

  async function pickPhoto(label) {{
    var f = document.createElement('input');
    f.type = 'file'; f.accept = 'image/*';
    f.capture = 'environment';
    f.onchange = async function(e) {{
      var file = e.target.files && e.target.files[0];
      if (!file) return;
      // 압축: 캔버스에 1024px 너비로 리사이즈
      var dataUrl = await resizeImage(file, 1024, 0.82);
      var btn = document.getElementById('ph-' + label);
      if (btn) btn.textContent = '⏳ 전송 중...';
      try {{
        var resp = await fetch('/api/team/event/photo', {{
          method:'POST',
          headers:{{'Content-Type':'application/json'}},
          body: JSON.stringify({{token: TOKEN, label: label, image_data_url: dataUrl}}),
        }});
        if (resp.ok) {{
          if (btn) btn.outerHTML = '<div class="photo-thumb uploaded"><span class="ph-sent">✓</span><span class="pl">' + label + '</span></div>';
        }} else {{
          var err = await resp.json().catch(function(){{return{{}};}});
          alert('실패: ' + (err.detail || resp.status));
          if (btn) btn.textContent = label;
        }}
      }} catch (e) {{
        alert('네트워크 오류');
      }}
    }};
    f.click();
  }}

  function resizeImage(file, maxW, q) {{
    return new Promise(function(resolve) {{
      var img = new Image();
      img.onload = function() {{
        var w = img.width, h = img.height;
        if (w > maxW) {{ h = h * maxW / w; w = maxW; }}
        var c = document.createElement('canvas');
        c.width = w; c.height = h;
        c.getContext('2d').drawImage(img, 0, 0, w, h);
        resolve(c.toDataURL('image/jpeg', q));
      }};
      img.src = URL.createObjectURL(file);
    }});
  }}
</script>
</body>
</html>
"""


def _build_today_card_html(item: dict) -> str:
    """오늘 현장 카드 HTML (프로토 openMemberView 의 card 부분 1:1)."""
    import html as _html
    if not item:
        return '<div class="sec-sub">오늘 현장</div><div class="empty">오늘 배정된 현장이 없어요</div>'
    name = _html.escape(str(item.get("customer_label") or "현장"))
    time = _html.escape(str(item.get("time") or "—"))
    addr = _html.escape(str(item.get("addr") or "주소 미입력"))
    work = _html.escape(str(item.get("work_summary") or ""))
    memo = _html.escape(str(item.get("memo") or ""))
    work_html = ""
    if work or memo:
        parts = [p for p in [work, memo] if p]
        work_html = f'<div class="work">{" · ".join(parts)}</div>'
    return f'''
    <div class="sec-sub">오늘 현장</div>
    <div class="card">
      <div class="row">
        <span class="hd hot"></span>
        <span class="name">{name}</span>
        <span class="time">{time}</span>
      </div>
      <div class="preview" id="today-addr">📍 {addr}</div>
      {work_html}
      <div style="display:flex;gap:8px;margin-top:14px">
        <button class="hbtn" onclick="copyAddr()">📋 주소 복사</button>
        <button class="mv-depart" id="mv-depart" onclick="doDepart()">🚗 출발</button>
      </div>
      <div style="display:flex;gap:7px;margin-top:9px;flex-wrap:wrap">
        <button class="nav-chip" onclick="openNav('카카오맵')">카카오맵</button>
        <button class="nav-chip" onclick="openNav('카카오내비')">카카오내비</button>
        <button class="nav-chip" onclick="openNav('티맵')">티맵</button>
      </div>
    </div>
    <div class="mv-photos">
      <div class="mv-ph-top">📷 현장 사진 올리기<span class="mv-ph-sub">대표님에게 바로 전송</span></div>
      <div class="ph-help">시공 전·후 사진을 찍어 올리면 대표님 앱에 자동으로 쌓여요.</div>
      <div class="photo-grid">
        <div class="photo-thumb" id="ph-시공 전" onclick="pickPhoto('시공 전')">📷<span class="pl">시공 전</span></div>
        <div class="photo-thumb" id="ph-시공 중" onclick="pickPhoto('시공 중')">📷<span class="pl">시공 중</span></div>
        <div class="photo-thumb" id="ph-시공 후" onclick="pickPhoto('시공 후')">📷<span class="pl">시공 후</span></div>
      </div>
    </div>
    '''


def _build_next_block_html(items: list[dict]) -> str:
    """다음 일정 블록 (today 제외)."""
    import html as _html
    upcoming = [it for it in (items or []) if not it.get("is_today")]
    if not upcoming:
        return ""
    rows = []
    for it in upcoming[:5]:
        name = _html.escape(str(it.get("customer_label") or "현장"))
        when = _html.escape(str(it.get("when") or ""))
        time = _html.escape(str(it.get("time") or ""))
        addr = _html.escape(str(it.get("addr") or ""))
        rows.append(
            f'<div class="card">'
            f'<div class="row"><span class="hd"></span><span class="name">{name}</span>'
            f'<span class="time">{when} {time}</span></div>'
            f'<div class="preview">📍 {addr}</div></div>'
        )
    return '<div class="sec-sub">다음 일정</div>' + "".join(rows)


def _expiry_label(expires_at_ms: int) -> str:
    """epoch ms → '5/31 (일요일)' 형태 (만료 안내 표시용)."""
    import datetime
    dt = datetime.datetime.utcfromtimestamp(expires_at_ms / 1000) + datetime.timedelta(hours=9)
    wn = ["월","화","수","목","금","토","일"]
    return f"{dt.month}/{dt.day} ({wn[dt.weekday()]}요일)"


@app.get("/team/member/{token}", response_class=HTMLResponse)
async def team_member_page(token: str) -> HTMLResponse:
    """팀원 브라우저용 화면 (프로토 openMemberView 1:1)."""
    import html as _html
    with db_conn() as con:
        row = con.execute(
            """
            SELECT l.member_id, l.owner_phone, l.expires_at_ms,
                   l.schedule_snapshot_json, m.name, m.removed_at_ms
            FROM team_member_links l
            LEFT JOIN team_members m ON m.member_id = l.member_id
            WHERE l.token = ?
            """,
            (token,),
        ).fetchone()
    if not row:
        return HTMLResponse(
            content="<html><body style='font-family:sans-serif;padding:40px;text-align:center'>"
                    "<h2 style='color:#F0436A'>❌ 유효하지 않은 링크</h2>"
                    "<p>대표님께 다시 링크를 받아 주세요.</p></body></html>",
            status_code=404,
        )
    member_id, owner_phone, expires_at, snap_json, member_name, removed_at = row
    if removed_at is not None:
        return HTMLResponse(
            content="<html><body style='font-family:sans-serif;padding:40px;text-align:center'>"
                    "<h2 style='color:#F0436A'>🔒 링크가 차단되었어요</h2>"
                    "<p>대표님께 문의해 주세요.</p></body></html>",
            status_code=410,
        )
    now = _now_ms()
    if now > expires_at:
        return HTMLResponse(
            content="<html><body style='font-family:sans-serif;padding:40px;text-align:center'>"
                    "<h2 style='color:#F0436A'>⌛ 만료된 링크</h2>"
                    "<p>대표님께 새 링크를 요청해 주세요.</p></body></html>",
            status_code=410,
        )

    items = []
    if snap_json:
        try:
            items = json.loads(snap_json) or []
        except json.JSONDecodeError:
            items = []
    today = next((it for it in items if it.get("is_today")), None) or (items[0] if items else None)

    # last_accessed 기록
    with db_conn() as con:
        con.execute("UPDATE team_member_links SET last_accessed_ms = ? WHERE token = ?", (now, token))
        con.commit()

    owner_label = "대표님"
    biz = _fetch_owner_biz_name(owner_phone)
    if biz:
        owner_label = biz + " 대표님"

    page = TEAM_MEMBER_HTML_TEMPLATE.format(
        member_name_html=_html.escape(member_name or "팀원"),
        owner_label_html=_html.escape(owner_label),
        today_block=_build_today_card_html(today),
        next_block=_build_next_block_html(items),
        expiry_label_html=_html.escape(_expiry_label(expires_at)),
        token_js=_html.escape(token, quote=True),
    )
    return HTMLResponse(content=page)


# ============================================================================
# §21 — GET /api/tone/profile (내 말투 학습 화면 채우기)
# ─────────────────────────────────────────────────────────────────────────────
# 명세: docs/SERVER_TONE_PROFILE_PROMPT.md (안드로이드 Claude 작성, 2026-06-03)
#
# 응답 (앱이 이 JSON 그대로 파싱):
# {
#   "deviceId": "owner-anon",
#   "analyzed": true,
#   "sampleCount": 381,
#   "learnRatePct": 76,
#   "traits": [{"k":"말끝","v":"~요체"}, ...] (5개 고정 키),
#   "example": {"question":"...","plain":"...","mine":"..."},
#   "editCount": 0
# }
#
# 폴백: sampleCount < 30 → analyzed:false, traits:[], example:null
#       LLM 실패 시도 동일하게 graceful.
# 캐시: summary_cache (device_id, "tone-profile", sampleCount) + 24h TTL +
#       sampleCount Δ50 미만이면 동일 캐시 hit.
# 모델: traits/plain = Haiku, mine = Sonnet (prepare-reply 라우팅과 동일).
# learnRatePct 공식: min(100, round(sampleCount/500*100)) — 막내비서 카드와 통일.
# ============================================================================

TONE_PROFILE_LEARN_TARGET = 500   # 학습률% 계산 분모 (앱 막내비서 카드와 동일)
TONE_PROFILE_MIN_SAMPLES = 30     # analyzed=true 임계값
TONE_PROFILE_CACHE_TTL_MS = 24 * 60 * 60 * 1000   # 24h
TONE_PROFILE_SAMPLE_DELTA = 50    # sampleCount 변화 임계 (이거 넘으면 재계산)
TONE_PROFILE_EXAMPLE_QUESTION = "24평 화장실 2개 줄눈 얼마예요?"
TONE_PROFILE_SAMPLE_PICK_N = 100  # traits 분석용 코퍼스 sample 수


def _tone_profile_pick_samples(device_id: str, n: int = TONE_PROFILE_SAMPLE_PICK_N) -> list[str]:
    """owner_tone 에서 최근 N건 본문 sample."""
    with db_conn() as con:
        rows = con.execute(
            "SELECT text FROM owner_tone WHERE device_id = ? "
            "ORDER BY created_at_ms DESC LIMIT ?",
            (device_id, n),
        ).fetchall()
    return [r[0] for r in rows if r[0]]


_TONE_TRAITS_KEYS = ["말끝", "이모티콘", "길이", "호칭", "시그니처"]

_TONE_TRAITS_SYSTEM = """너는 한 사장님의 문자 코퍼스를 분석해 말투 특징 5개를 뽑는 비서다.

아래는 한 사장님(줄눈/타일 시공자)이 고객에게 보낸 실제 문자들이다.
이 사람의 말투를 정확히 5개 항목으로 요약하라 — **앱 화면의 작은 칩(chip) 한 줄에 들어가야 한다.**

★ 규칙 (반드시 지켜라):
- 각 v 는 **12~15자 이내의 짧은 명사구**. 절대 완전한 문장 쓰지 마라.
- 금지: "~다.", "~이다.", "~습니다." 같은 문장 종결 어미. 설명문 X.
- 허용: 형용사+명사 ("친근한 ~요체"), 부사+빈도 ("자주", "거의 매번"), 큰따옴표 인용 ("고객님").
- 이모티콘은 유니코드/아스키 그대로 (😊 ^^ ㅠ 👍).
- 코퍼스에 실제로 자주 나오는 어휘만 인용 (없는 거 지어내지 마라).

5개 항목:
1. 말끝     — "~요"체 / "~습니다"체 / 반말 혼용 등
2. 이모티콘 — 어떤 게 자주 + 빈도 ("메시지당 ~1개", "자주", "가끔")
3. 길이     — "짧고 핵심만 (N줄)" 식
4. 호칭     — "고객님" / "사장님" / "호칭 없음" 등 (인용부호 포함)
5. 시그니처 — 마무리 인사 짧게 ("편하게 문의주세요!" 같이 인용)

★ 정답 예시 (이 형식 그대로 — 길이·구조 완전히 따라해라):
{"traits":[
  {"k":"말끝","v":"친근한 \\"~요\\"체"},
  {"k":"이모티콘","v":"😊 자주 (메시지당 ~1개)"},
  {"k":"길이","v":"짧고 핵심만 (2~3줄)"},
  {"k":"호칭","v":"\\"고객님\\""},
  {"k":"시그니처","v":"\\"편하게 문의주세요!\\""}
]}

답 형식 — 반드시 지켜라:
- 응답 첫 글자는 '{' 로 시작. 다른 텍스트 일체 X.
- traits 배열 5개 고정, 키 순서: 말끝/이모티콘/길이/호칭/시그니처.
"""


async def _tone_extract_traits(samples: list[str]) -> list[dict]:
    """Haiku 로 5개 말투 특징 추출. 실패 시 빈 리스트 반환 (graceful)."""
    if not samples:
        return []
    # 코퍼스 안전 컷 (200건 × 평균 80자 = 16KB max)
    corpus_lines = [f"- {s.strip()[:200]}" for s in samples[:TONE_PROFILE_SAMPLE_PICK_N] if s.strip()]
    user_msg = "[사장님이 고객에게 보낸 실제 문자 코퍼스]\n" + "\n".join(corpus_lines)
    try:
        parsed, response = await call_claude_json(
            system_prompt=_TONE_TRAITS_SYSTEM,
            user_msg=user_msg,
            max_tokens=800,
            model=HAIKU_MODEL,
        )
        _log_llm_usage_from_response("tone-profile-traits", response)
    except Exception as e:
        print(f"[tone/profile] traits Haiku 실패: {type(e).__name__}: {e}")
        return []

    raw_traits = parsed.get("traits")
    if not isinstance(raw_traits, list):
        return []

    # 키 5개 고정 — 빠진 키는 빈 값으로 채움, 알 수 없는 키는 무시
    by_key = {}
    for t in raw_traits:
        if not isinstance(t, dict):
            continue
        k = str(t.get("k") or "").strip()
        v = str(t.get("v") or "").strip()
        if k and v:
            by_key[k] = v
    out = []
    for fixed_k in _TONE_TRAITS_KEYS:
        v = by_key.get(fixed_k, "")
        if not v:
            # Haiku 가 키를 살짝 다르게 쓸 수 있어 fuzzy fallback
            for raw_k, raw_v in by_key.items():
                if fixed_k in raw_k or raw_k in fixed_k:
                    v = raw_v
                    break
        if v:
            # 안전 컷 — 칩 UI 깨짐 방지 (25자 hard limit, 가능하면 한 문장 첫 절만)
            v_clean = v.replace("\n", " ").strip()
            # 흔한 종결어미가 문장 중간 분리 신호로 자주 등장 → 첫 절만 취함
            for sep in ["입니다.", "합니다.", "됩니다.", "있어요.", ". ", " · "]:
                if sep in v_clean:
                    v_clean = v_clean.split(sep, 1)[0].rstrip(".·,")
                    break
            if len(v_clean) > 25:
                v_clean = v_clean[:25].rstrip() + "…"
            out.append({"k": fixed_k, "v": v_clean})
    return out


_TONE_PLAIN_SYSTEM = """너는 일반 AI 답변 도우미다.
한국어로 답하지만 특정 사장님 톤을 흉내내지 마라. 정중하지만 평이한 "~합니다" 체.
- 이모티콘 X
- 자기소개나 인사 길게 X
- 견적/시공 질문에 대한 일반적이고 짧은 답 (2~3문장)
- 정확한 가격을 모르면 "현장 사진 보내주시면 정확히 안내드리겠습니다" 같이.

답 형식: 평문 한국어. JSON 아님. 텍스트 그대로.
"""


async def _tone_plain_answer(question: str) -> Optional[str]:
    """Haiku 로 톤 없는 일반 답변 (RAG 미사용). 실패 시 None."""
    try:
        response = await claude_client.messages.create(
            model=HAIKU_MODEL,
            max_tokens=300,
            timeout=CLAUDE_TIMEOUT,
            system=[{"type": "text", "text": _TONE_PLAIN_SYSTEM,
                     "cache_control": {"type": "ephemeral"}}],
            messages=[{"role": "user", "content": question}],
        )
        _log_llm_usage_from_response("tone-profile-plain", response)
        parts = [getattr(b, "text", "") for b in response.content
                 if getattr(b, "type", None) == "text"]
        return "".join(parts).strip() or None
    except Exception as e:
        print(f"[tone/profile] plain Haiku 실패: {type(e).__name__}: {e}")
        return None


_TONE_MINE_SYSTEM = """너는 1인 시공자(줄눈/타일) 사장님의 비서다.
아래 [사장님 톤 샘플] 을 그대로 흉내내어, 사장님이 직접 답한 것처럼 답장 1개를 만든다.

규칙:
- 사장님 말끝/이모티콘/호칭/길이/시그니처를 거의 똑같이 따라해라.
- 톤 샘플에 자주 나오는 어휘와 인사말을 적극 재사용.
- 가격을 정확히 모르면 "현장 사진" 같이 사장님이 톤 샘플에서 쓰는 표현으로 안내.
- 1~3문장. 너무 길게 X.

답 형식: 평문 한국어. JSON 아님. 텍스트 그대로.
"""


async def _tone_mine_answer(question: str, samples: list[str]) -> Optional[str]:
    """Sonnet 으로 사장님 톤 적용 답변 (RAG 샘플 inject). 실패 시 None."""
    if not samples:
        return None
    tone_lines = [f"- {s.strip()[:140]}" for s in samples[:30] if s.strip()]
    user_msg = (
        "[사장님 톤 샘플 — 이 사람 말투를 흉내내라]\n"
        + "\n".join(tone_lines)
        + f"\n\n[고객 질문]\n{question}\n\n[답변 (사장님 톤으로)]"
    )
    try:
        response = await claude_client.messages.create(
            model=CLAUDE_MODEL,
            max_tokens=300,
            timeout=CLAUDE_TIMEOUT,
            system=[{"type": "text", "text": _TONE_MINE_SYSTEM,
                     "cache_control": {"type": "ephemeral"}}],
            messages=[{"role": "user", "content": user_msg}],
        )
        _log_llm_usage_from_response("tone-profile-mine", response)
        parts = [getattr(b, "text", "") for b in response.content
                 if getattr(b, "type", None) == "text"]
        return "".join(parts).strip() or None
    except Exception as e:
        print(f"[tone/profile] mine Sonnet 실패: {type(e).__name__}: {e}")
        return None


def _tone_profile_cache_get(device_id: str, sample_count: int) -> Optional[dict]:
    """캐시 hit 조건: sampleCount Δ < 50 AND age < 24h.

    summary_cache 재활용 — (phone=device_id, endpoint='tone-profile', latest_msg_ts=cached_sample_count).
    가장 최근 row 1개만 보고, 두 조건 다 만족하면 그 payload 반환.
    """
    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            "SELECT latest_msg_ts, response_json, generated_at_ms "
            "FROM summary_cache WHERE phone = ? AND endpoint = 'tone-profile' "
            "ORDER BY generated_at_ms DESC LIMIT 1",
            (device_id,),
        ).fetchone()
    if not row:
        return None
    cached_count, response_json, generated_at = row
    if now - generated_at > TONE_PROFILE_CACHE_TTL_MS:
        return None
    if abs(int(sample_count) - int(cached_count or 0)) >= TONE_PROFILE_SAMPLE_DELTA:
        return None
    try:
        payload = json.loads(response_json)
    except json.JSONDecodeError:
        return None
    # sampleCount/learnRatePct 는 캐시 무시하고 최신값으로 갱신 (사장님 혼란 X)
    payload["sampleCount"] = sample_count
    payload["learnRatePct"] = min(100, round(sample_count / TONE_PROFILE_LEARN_TARGET * 100))
    payload["_cache_hit"] = True
    return payload


def _tone_profile_cache_set(device_id: str, sample_count: int, payload: dict) -> None:
    """캐시 저장. summary_cache 의 (phone, endpoint, latest_msg_ts) PK 재활용."""
    now = _now_ms()
    # 같은 sampleCount 면 REPLACE (PK 충돌 회피)
    with db_conn() as con:
        con.execute(
            "INSERT OR REPLACE INTO summary_cache "
            "(phone, endpoint, latest_msg_ts, response_json, generated_at_ms) "
            "VALUES (?, 'tone-profile', ?, ?, ?)",
            (device_id, int(sample_count),
             json.dumps(payload, ensure_ascii=False), now),
        )
        con.commit()


def _tone_profile_fallback(device_id: str, sample_count: int) -> dict:
    """폴백 응답 (analyzed:false). hero 의 % 와 문자 수는 항상 채움."""
    return {
        "deviceId": device_id,
        "analyzed": False,
        "sampleCount": sample_count,
        "learnRatePct": min(100, round(sample_count / TONE_PROFILE_LEARN_TARGET * 100)),
        "traits": [],
        "example": None,
        "editCount": 0,
    }


@app.get("/api/tone/profile")
async def tone_profile(device_id: str = "owner-anon") -> dict:
    """내 말투 학습 화면용 — 학습률·말투 특징 5개·before/after 비교 1개."""
    if not device_id or not device_id.strip():
        raise HTTPException(400, "device_id 필수")

    sample_count = count_owner_tone_pool(device_id)
    learn_rate_pct = min(100, round(sample_count / TONE_PROFILE_LEARN_TARGET * 100))

    # 폴백 — 30건 미만이면 LLM 호출 안 함
    if sample_count < TONE_PROFILE_MIN_SAMPLES:
        print(f"[tone/profile] {device_id} sampleCount={sample_count} < {TONE_PROFILE_MIN_SAMPLES} → 폴백")
        return _tone_profile_fallback(device_id, sample_count)

    # 캐시 hit?
    cached = _tone_profile_cache_get(device_id, sample_count)
    if cached:
        print(f"[tone/profile] {device_id} → cache HIT (sampleCount={sample_count})")
        return cached

    # rate limit (device_id 기준 — phone 자리 대용)
    check_rate_limit(device_id)

    # 새로 생성
    samples = _tone_profile_pick_samples(device_id)
    print(f"[tone/profile] {device_id} sampleCount={sample_count} → 신규 분석 (samples={len(samples)})")

    # 병렬 호출 (asyncio.gather)
    traits_task = _tone_extract_traits(samples)
    plain_task = _tone_plain_answer(TONE_PROFILE_EXAMPLE_QUESTION)
    mine_task = _tone_mine_answer(TONE_PROFILE_EXAMPLE_QUESTION, samples)
    traits, plain_text, mine_text = await asyncio.gather(
        traits_task, plain_task, mine_task, return_exceptions=False,
    )

    # example — 둘 중 하나라도 빠지면 example=null (앱 placeholder 유지)
    if plain_text and mine_text:
        example = {
            "question": TONE_PROFILE_EXAMPLE_QUESTION,
            "plain": plain_text,
            "mine": mine_text,
        }
    else:
        example = None

    # traits 가 비고 example 도 없으면 analyzed=false 로 (LLM 다 실패한 케이스)
    analyzed = bool(traits) or example is not None

    payload = {
        "deviceId": device_id,
        "analyzed": analyzed,
        "sampleCount": sample_count,
        "learnRatePct": learn_rate_pct,
        "traits": traits,
        "example": example,
        "editCount": 0,
    }

    # 캐시 저장 (분석 성공한 경우만)
    if analyzed:
        _tone_profile_cache_set(device_id, sample_count, payload)
    payload["_cache_hit"] = False
    return payload


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
