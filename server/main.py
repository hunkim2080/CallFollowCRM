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
import base64
import binascii
import datetime as _dt
import json
import os
import sqlite3
import sys
import time
import urllib.parse
from contextlib import asynccontextmanager, contextmanager
from pathlib import Path
from typing import Optional

import anthropic
import httpx
from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import HTMLResponse, FileResponse, Response, JSONResponse
from pydantic import BaseModel, Field

# Pillow(이미지 썸네일 축소)는 선택 의존성 — 없으면 원본 그대로 반환(안전 폴백).
try:
    from PIL import Image as _PILImage
    _PIL_OK = True
except Exception:
    _PILImage = None
    _PIL_OK = False

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
# §26 fix (2026-06-10): 11분+ 통화는 transcript 4000~6000자 → Gemini 처리 시간 30~90초.
# 짧은 통화 (prepare-reply) 와 긴 통화 (call-audio-summary) 둘 다 안전한 120초.
GEMINI_TIMEOUT_SEC = 120.0
GEMINI_MAX_OUTPUT_TOKENS = 2048  # 핸드오프30 (2026-06-15): 500 → 2048. 긴 원문 다듬기 시 끊김 해결.

# §15 — Admin token (사업 metric endpoint 보호용. /api/admin/* 호출 시 X-Admin-Token 헤더 필요)
# 미설정 시 admin endpoint 는 503 (인증 비활성화).
# 사장님이 launchd plist EnvironmentVariables 에 ADMIN_TOKEN=<랜덤 문자열> 박아야 활성화.
ADMIN_TOKEN = os.environ.get("ADMIN_TOKEN")

CLAUDE_MODEL = "claude-sonnet-4-6"        # 매출 직결 워크로드 (prepare-reply)
HAIKU_MODEL  = "claude-haiku-4-5"         # 단순 요약/분류 워크로드 (card/conversation/next-action). Sonnet 의 ~1/3 비용.
CLAUDE_MAX_TOKENS = 2048  # 2026-06-16: 800 → 2048. 추천답변 3개(이유 포함)는 800 토큰에 빠듯해 truncation 위험 → 상향(sonnet 경로 안전망)
CLAUDE_TIMEOUT = 60.0  # 초. 한 호출이 60초 넘으면 끊는다.

# Sonnet 4.6 가격 (per 1M tokens) — 2026 기준
COST_INPUT_PER_M = 3.0
COST_CACHED_INPUT_PER_M = 0.30
COST_OUTPUT_PER_M = 15.0
KRW_PER_USD = 1380  # 대략. 정확한 환율은 외부 API 로 교체 가능.

# Rate limit — 추가86: env 로 조절 가능 (재시작 시 반영)
DAILY_TOTAL_CALLS_LIMIT = int(os.environ.get("DAILY_TOTAL_CALLS_LIMIT", "2500"))  # 폭주 차단
# 추가86b — 폰당 한도 200→100 (사장님 실측 기준: 본인 두 번호(최고 헤비유저)가
# 월 40~50건 = 피크 일 2~30건. 100 = 피크의 3배 여유. 정상 사용자는 절대 안 걸림.)
PER_PHONE_DAILY_LIMIT = int(os.environ.get("PER_PHONE_DAILY_LIMIT", "100"))       # 한 사용자가 다 못 쓰게

# 추가86 — 앱 가입 (SMS 인증번호) + 자동 등업 cap
AUTO_ENROLL_CAP = int(os.environ.get("AUTO_ENROLL_CAP", "100"))   # 선착 자동 베타 등업 인원
# 추가112 — 배포/회귀 테스트가 찍는 합성(가짜) 번호. 통계 제외 + 자가 청소 대상.
#   deploy_phase1.sh(991/992) · test_section12.sh(912/913/914) · SYNC 예제 curl(999/9866) 등.
TEST_SYNTHETIC_PHONES = {
    "01099999912", "01099999913", "01099999914",
    "01099999991", "01099999992", "01099999999",
    "01099998866", "01000000000",
}
# 추가89 — 통계 집중도(폭주) 계산에서 제외할 번호 (사장님 본인 + 테스트 합성번호 + 외부 봇)
#   → 데이터 지배/오염 방지. env 로 덮어쓰기 가능(콤마 구분). 기본값에 테스트번호·봇 포함.
_DEFAULT_EXCLUDE = ",".join([
    "01080056674", "01064610131",              # 사장님 본인 테스트 폰
    *sorted(TEST_SYNTHETIC_PHONES),            # 배포/회귀 합성 번호
    "+4127225226", "4127225226",               # 외부 스캐너/봇 (미국 412)
])
STATS_EXCLUDE_PHONES = set(
    p.strip() for p in os.environ.get(
        "STATS_EXCLUDE_PHONES", _DEFAULT_EXCLUDE).split(",") if p.strip()
)
FREE_TRIAL_DAYS = int(os.environ.get("FREE_TRIAL_DAYS", "60"))    # 무료 체험 기간 (2개월)
AUTH_CODE_TTL_SEC = 300           # 인증번호 유효 5분
AUTH_CODE_MAX_PER_DAY = 5         # 번호당 하루 발송 한도 (문자폭탄 방지)
AUTH_CODE_MIN_INTERVAL_SEC = 60   # 재발송 최소 간격
AUTH_CODE_GLOBAL_PER_DAY = 500    # 전체 하루 발송 한도 (비용 방파제)
# SOLAPI (문자 발송) — plist EnvironmentVariables 에 박아야 활성화
SOLAPI_API_KEY = os.environ.get("SOLAPI_API_KEY", "")
SOLAPI_API_SECRET = os.environ.get("SOLAPI_API_SECRET", "")
SOLAPI_SENDER = os.environ.get("SOLAPI_SENDER", "")  # 사전 등록된 발신번호

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
        # 추가86 (2026-07-03) — 앱 가입 SMS 인증번호
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS auth_codes (
                phone         TEXT PRIMARY KEY,
                code          TEXT NOT NULL,
                expires_at_ms INTEGER NOT NULL,
                attempts      INTEGER NOT NULL DEFAULT 0,
                last_sent_ms  INTEGER NOT NULL,
                sent_today    INTEGER NOT NULL DEFAULT 0,
                sent_day      TEXT
            )
            """
        )
        # 추가97 (2026-07-06) — 개인정보 동의 기록 (append-only 이력 = 동의 영수증)
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS consents (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                phone         TEXT NOT NULL,
                doc_type      TEXT NOT NULL,
                doc_version   TEXT NOT NULL,
                agreed        INTEGER NOT NULL,
                ip            TEXT,
                created_at_ms INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_consents_phone ON consents(phone, doc_type)"
        )
        # 추가118 (Q12) — 동의 시점의 고지 문안 스냅샷·해시 보관용 컬럼 (idempotent)
        _cc = {r[1] for r in con.execute("PRAGMA table_info(consents)").fetchall()}
        if "detail_json" not in _cc:
            con.execute("ALTER TABLE consents ADD COLUMN detail_json TEXT")
        # ── 추가119 (2026-07-13) — 본폰 "일정 미러 링크" (읽기전용 뷰어) ──
        # 업무폰 N대가 일정+돈 스냅샷을 push → 본폰이 링크 하나로 통합 열람(수정 불가).
        # 사장님 확정: 4자리 비번 O / 전화·메모 포함 / 범위 = 일정 + 돈 요약.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS mirror_links (
                token            TEXT PRIMARY KEY,
                main_key         TEXT NOT NULL,     -- 여러 사업장을 묶는 그룹키
                pin_hash         TEXT,              -- 4자리 비번 sha256(salt+pin). NULL=비번없음
                pin_salt         TEXT,
                created_at_ms    INTEGER NOT NULL,
                last_accessed_ms INTEGER,
                revoked          INTEGER NOT NULL DEFAULT 0
            )
            """
        )
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS mirror_sources (
                token         TEXT NOT NULL,        -- mirror_links.token
                owner_phone   TEXT NOT NULL,        -- 업무폰(사업장) 식별
                label         TEXT,                 -- 상호 (칩에 표시)
                tint          INTEGER DEFAULT 0,    -- 사업장 색 인덱스
                snapshot_json TEXT,                 -- 일정 items[] (덮어쓰기)
                money_json    TEXT,                 -- 돈 요약 {todayIn, unpaid, unpaidCount}
                updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (token, owner_phone)
            )
            """
        )
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS mirror_pair_codes (
                code          TEXT PRIMARY KEY,     -- 6자리
                token         TEXT NOT NULL,
                expires_at_ms INTEGER NOT NULL,
                used_at_ms    INTEGER
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_mirror_src_owner "
            "ON mirror_sources(owner_phone)"
        )
        # 뷰어 쿠키 서명용 서버 시크릿 (최초 1회 생성 후 재사용)
        con.execute(
            "CREATE TABLE IF NOT EXISTS server_kv ("
            "k TEXT PRIMARY KEY, v TEXT NOT NULL)"
        )
        # ── 추가122 (2026-07-14) — 미러 v2 "공유 신청/수락" (SERVER_HANDOFF_mirror_v2.md) ──
        # 규칙: 업무폰이 고정 코드를 만들고, 본폰이 그 코드를 넣어 신청, 업무폰이 수락.
        # 코드가 새도 수락 안 하면 무해 → v1 의 링크+비번 대신 "수락"이 게이트.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS mirror_codes (
                owner_phone   TEXT PRIMARY KEY,   -- 업무폰
                code          TEXT NOT NULL UNIQUE,  -- 고정 코드 (전역 UNIQUE · 8자리↑ · 랜덤)
                label         TEXT,
                tint          INTEGER DEFAULT 0,
                updated_at_ms INTEGER NOT NULL
            )
            """
        )
        # 추가123 — QR 자동수락용 시크릿 (코드와 별개, 추측 불가). QR 로 들어오면 수락 생략.
        _mc = {r[1] for r in con.execute("PRAGMA table_info(mirror_codes)").fetchall()}
        if "auto_secret" not in _mc:
            con.execute("ALTER TABLE mirror_codes ADD COLUMN auto_secret TEXT")
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS mirror_shares (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_phone   TEXT NOT NULL,
                home_phone    TEXT NOT NULL,      -- 신청한 본폰 (이름표 · 미검증)
                status        TEXT NOT NULL,      -- pending | accepted | rejected
                created_at_ms INTEGER NOT NULL,
                decided_at_ms INTEGER,
                UNIQUE (owner_phone, home_phone)
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_mirror_shares_owner "
            "ON mirror_shares(owner_phone, status)"
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_mirror_shares_home "
            "ON mirror_shares(home_phone, status)"
        )
        # v2 스냅샷 저장소 — owner_phone 키 (v1 mirror_sources 는 존치·병행 갱신)
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS mirror_snapshots (
                owner_phone   TEXT PRIMARY KEY,
                label         TEXT,
                tint          INTEGER DEFAULT 0,
                snapshot_json TEXT,
                money_json    TEXT,
                updated_at_ms INTEGER NOT NULL
            )
            """
        )
        # 본폰 열기 비번 (선택 — 기본 없음)
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS mirror_home (
                home_phone TEXT PRIMARY KEY,
                pin_hash   TEXT,
                pin_salt   TEXT
            )
            """
        )
        # ── 추가121 (2026-07-13) — 상담함/문자함 Haiku 분류 캐시 (같은 내용 재호출 0) ──
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS thread_classify_cache (
                cache_key     TEXT PRIMARY KEY,   -- sha256(suffix + 본문)
                suffix        TEXT,
                result_json   TEXT NOT NULL,      -- {bucket, confidence, reason}
                created_at_ms INTEGER NOT NULL
            )
            """
        )
        # 추가93 (2026-07-04) — 🩺 시스템 에러 기록 (5xx/미처리 예외 → 대시보드 건강 카드)
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS system_errors (
                id     INTEGER PRIMARY KEY AUTOINCREMENT,
                ts_ms  INTEGER NOT NULL,
                path   TEXT,
                status INTEGER,
                detail TEXT
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_system_errors_ts ON system_errors(ts_ms)"
        )
        # 추가79 (2026-07-02) — API 크레딧 충전 기록 (Anthropic 자동충전 크로스체크용).
        # 사장님이 /admin 에서 [충전 등록] 클릭 → 그 시점 이후 llm_usage_log 사용액과
        # 대조해 금액 누수(미추적 사용) 감지.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS api_recharges (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                amount_usd      REAL    NOT NULL,
                note            TEXT,
                recharged_at_ms INTEGER NOT NULL
            )
            """
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
                event_type      TEXT NOT NULL,           -- 'departed'|'photo'|'arrived'|'completed'|'note'
                payload_json    TEXT,
                created_at_ms   INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_team_events_owner_created "
            "ON team_member_events(owner_phone, created_at_ms)"
        )
        # 현장 메모 양방향(2026-06-06) — 사장님 읽음 확인 + 답글. 기존 DB 엔 ALTER 로 추가.
        for col_def in [
            "read_at_ms INTEGER",      # 사장님이 그 메모를 확인한 시각 (NULL=아직 안 봄)
            "reply_text TEXT",         # 사장님 답글 내용
            "reply_at_ms INTEGER",     # 사장님 답글 시각
        ]:
            try:
                con.execute(f"ALTER TABLE team_member_events ADD COLUMN {col_def}")
            except sqlite3.OperationalError:
                pass  # already exists
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
        # §25 — team_site_photos 에 customer_phone 추가 (안드로이드 SERVER_HANDOFF 2026-06-04)
        # 사진을 "어느 고객 현장" 으로 묶기 위해 추가. 기존 row 호환 위해 NULL 허용.
        # 매칭: 정확 일치 OR 끝 8자리 suffix (고객 phone 형태 다양 호환).
        try:
            con.execute("ALTER TABLE team_site_photos ADD COLUMN customer_phone TEXT")
        except sqlite3.OperationalError:
            pass  # already exists
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_team_photos_owner_customer "
            "ON team_site_photos(owner_phone, customer_phone, uploaded_at_ms DESC)"
        )
        # §F (2026-06-13) — team_site_photos 에 share_id 연결 (협업 현장 사진).
        # 핸드오프 SERVER_HANDOFF_collab_expansion §F: "기존 site_photos 흐름에 협업 현장 연결.
        # 새 저장소 불필요, 라벨/연결만." 협업 현장 사진은 share_id 로 묶임 (A·B 둘 다 같은 share_id 로 업로드/조회).
        # 벽: shared_sites 의 owner_phone/partner_phone 중 하나가 요청자 owner_phone 과 일치해야 권한.
        try:
            con.execute("ALTER TABLE team_site_photos ADD COLUMN share_id TEXT")
        except sqlite3.OperationalError:
            pass  # already exists
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_team_photos_share "
            "ON team_site_photos(share_id, uploaded_at_ms DESC)"
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
            "owner_memo TEXT",                # 추가95③ — 사장님 특이사항 메모 (폼에 표시)
            "vat_included INTEGER",           # 추가102 — 부가세 포함(1)/별도(0). NULL=옛 발급분(별도 취급)
        ]:
            col_name = col_def.split()[0]
            try:
                con.execute(f"ALTER TABLE intake_forms ADD COLUMN {col_def}")
            except sqlite3.OperationalError:
                pass  # already exists
        # §22 — admin/beta/intake (사장님 베타 운영 셋팅 폼, HOU-128 통합)
        # 사장님이 /admin/beta/intake 에서 10 카테고리 폼 채우고 제출하면
        # 매 저장마다 새 revision 행이 들어감 (auto-save draft + 명시 submit 구분).
        # response_json = 전체 폼 데이터 직렬화. 최신 revision 이 canonical state.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS beta_intake_responses (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                submitted_at  TEXT,
                draft         INTEGER NOT NULL DEFAULT 1,
                revision      INTEGER NOT NULL DEFAULT 1,
                response_json TEXT NOT NULL,
                created_at    TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at    TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_bir_revision "
            "ON beta_intake_responses (revision DESC)"
        )
        # §23 — 베타 모집 랜딩페이지 신청 저장 (landing.html → POST /api/beta-signup)
        # 같은 phone 중복 신청은 PRIMARY KEY 로 차단 (가장 최근 응답만 keep).
        # source 는 'landing/<host>' 또는 'admin/manual' 등 유입경로 기록.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS beta_signups (
                phone               TEXT PRIMARY KEY,
                industry            TEXT,
                region              TEXT,
                monthly_inquiries   TEXT,
                note                TEXT,
                agreed_at_ms        INTEGER NOT NULL,
                source              TEXT,
                ip                  TEXT,
                ua                  TEXT,
                status              TEXT NOT NULL DEFAULT 'pending',
                created_at_ms       INTEGER NOT NULL,
                updated_at_ms       INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_beta_signups_created "
            "ON beta_signups(created_at_ms DESC)"
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_beta_signups_status "
            "ON beta_signups(status, created_at_ms DESC)"
        )
        # 추가57 (2026-06-25) — 신청 폼에 업체명 + 옛 앱 사용 경험 추가
        # 사장님: "번호만 있으니까 누군지 분간 안 가, 업체명 적게 해줘"
        try:
            con.execute("ALTER TABLE beta_signups ADD COLUMN business_name TEXT")
        except sqlite3.OperationalError:
            pass  # already exists
        # 추가31 (2026-06-15) — 베타 화이트리스트 (테스터 폰번호 기반 첫 진입 게이트).
        # 사장님이 admin 페이지에서 폰번호 등록 → 앱 첫 진입 시 본인 phone 입력 → 매칭 OK 면 진입.
        # 코드·SMS·관리 시스템 없이 폰번호 1개만으로 화이트리스트 운영.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS beta_whitelist (
                phone           TEXT PRIMARY KEY,            -- 사장님이 등록한 테스터 폰 (digits only)
                name            TEXT,                        -- 누구인지 (메모, 예: '강동 박사장')
                memo            TEXT,                        -- 모집 경로 등 메모
                added_at_ms     INTEGER NOT NULL,
                first_seen_ms   INTEGER,                     -- 앱이 첫 호출한 시각 (실제 사용 시작)
                last_seen_ms    INTEGER,                     -- 최근 호출 시각
                use_count       INTEGER NOT NULL DEFAULT 0   -- check 호출 카운트
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_beta_whitelist_added "
            "ON beta_whitelist(added_at_ms DESC)"
        )
        # §27 — 협업 현장 (사장↔사장 공유, 안드로이드 SERVER_HANDOFF 2026-06-08)
        # A(owner_phone) 가 B(partner_phone) 에게 현장 1건 공유. 고객 phone/대화 절대 X.
        # progress = assigned/departed/arrived/completed. completed 시 B 가 계좌 payload 보냄.
        # paid = A 가 입금 완료 표시.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS shared_sites (
                share_id          TEXT PRIMARY KEY,        -- 'sh_' + 10자 base62
                owner_phone       TEXT NOT NULL,           -- A (현장 주인)
                partner_phone     TEXT NOT NULL,           -- B (협업 사장)
                title             TEXT,                    -- '강동 천호동 현장'
                addr              TEXT,                    -- '강동구 천호동 …'
                scheduled_at_ms   INTEGER,
                work_summary      TEXT,                    -- '욕실 줄눈 2곳'
                memo              TEXT,                    -- '현관 비번 1234#'
                customer_label    TEXT,                    -- '강동 서사장님 현장' (고객 phone 대신 안전한 라벨)
                status            TEXT NOT NULL DEFAULT 'pending',   -- pending/accepted/declined
                progress          TEXT NOT NULL DEFAULT 'assigned',  -- assigned/departed/arrived/completed
                account_bank      TEXT,                    -- B 가 completed 시 보내는 계좌 (A 가 입금용)
                account_no        TEXT,
                account_holder    TEXT,
                paid_at_ms        INTEGER,                 -- A 가 입금 완료 표시한 시각
                daily_wage        INTEGER,                 -- §A (2026-06-13) 그날 일당 (만원 단위, 정수). NULL 허용.
                created_at_ms     INTEGER NOT NULL,
                updated_at_ms     INTEGER NOT NULL
            )
            """
        )
        # §A 마이그레이션 — 기존 cache.db 에 daily_wage 컬럼 없으면 추가.
        try:
            con.execute("ALTER TABLE shared_sites ADD COLUMN daily_wage INTEGER")
        except Exception:
            pass  # 이미 있음
        # §A-2 (2026-06-13) — invite payload 에 time_label("오전 9시") 직접 받기. NULL 허용.
        # scheduled_at_ms 가 있으면 HH:MM 자동 생성하지만, 앱이 자연어 형태로 보내주면 그대로 echo.
        try:
            con.execute("ALTER TABLE shared_sites ADD COLUMN time_label_raw TEXT")
        except Exception:
            pass
        # §A-3 (2026-06-13) — invite payload 에 owner_name(초대한 사장 상호 '디테일라인') 직접 받기.
        # 협업 사장 화면에 "디테일라인과 함께"로 표시. 없으면 _is_registered_owner fallback.
        try:
            con.execute("ALTER TABLE shared_sites ADD COLUMN owner_name_raw TEXT")
        except Exception:
            pass
        # 추가63 (2026-06-29) — 협업 진행 카드: 전환 시각 4개 추가.
        # 안드로이드 "파트너 사장 채팅 타임라인 카드" 의 핵심 = "언제 처리됐나".
        # respond/progress endpoint 가 각 step 시점에 박음. paid_at_ms 는 이미 있음.
        for _col in ("accepted_at_ms", "departed_at_ms", "arrived_at_ms", "completed_at_ms"):
            try:
                con.execute(f"ALTER TABLE shared_sites ADD COLUMN {_col} INTEGER")
            except Exception:
                pass
        # 추가50 (2026-06-21) — 앱 onboarding 에서 사장님이 고른 업종 (ownerTrade) 저장.
        # 옛 ownerTrade 는 LLM 호출에만 inject 되고 저장 X. 이제 가장 최근 값 박음.
        # owner_phone + ownerTrade 같이 오는 LLM 호출에서 자동 저장.
        try:
            con.execute("ALTER TABLE beta_whitelist ADD COLUMN owner_trade TEXT")
        except Exception:
            pass
        # 추가86 (2026-07-03) — 무료 체험 만료 시각 (앱 자동가입/등업 시 now+60일)
        try:
            con.execute("ALTER TABLE beta_whitelist ADD COLUMN free_until_ms INTEGER")
        except Exception:
            pass
        # §I (2026-06-18) 핸드오프 06-18 §1 — B(협업자) 가 respond/progress 시 보낸 본인 상호.
        # by-me 응답의 partner_name 으로 echo → A 화면 "🤝 OO 사장님과 함께".
        # 없으면 _is_registered_owner(partner_phone) fallback, 최종 "협업 사장".
        try:
            con.execute("ALTER TABLE shared_sites ADD COLUMN partner_name_raw TEXT")
        except Exception:
            pass
        # §D (2026-06-13) — 출동 2h 전 알림 dedup 컬럼. NULL = 아직 발송 안 됨.
        # poller 가 UPDATE ... WHERE reminded_at_ms IS NULL 로 race 차단.
        try:
            con.execute("ALTER TABLE shared_sites ADD COLUMN reminded_at_ms INTEGER")
        except Exception:
            pass
        # §G (2026-06-13) — 일당 모집 시스템 (broadcast → 지원 → 선택).
        # 핸드오프 SERVER_HANDOFF_collab_expansion §G. 데이터 2종 = recruits + recruit_applications.
        # 정확 주소는 확정 후 공개. 지원자끼리 안 보임. 고객 정보 미노출.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS recruits (
                recruit_id      TEXT PRIMARY KEY,        -- 'rec_' + 10자 base62
                owner_phone     TEXT NOT NULL,           -- A (모집자)
                date_ms         INTEGER,                 -- 모집 일자/시각 (scheduled)
                place           TEXT,                    -- 대략 위치 '인천 송도' (확정 전 공개)
                full_addr       TEXT,                    -- 정확한 주소 (확정 후만 공개)
                work            TEXT,                    -- 작업 종류 '줄눈'
                daily_wage      INTEGER,                 -- 만원 단위
                status          TEXT NOT NULL DEFAULT 'open', -- open/closed
                created_at_ms   INTEGER NOT NULL,
                updated_at_ms   INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_recruits_owner "
            "ON recruits(owner_phone, created_at_ms DESC)"
        )
        # recruit_applications — 초대받음(invited) → 지원함(applied) → 선택(selected)/미선택(rejected)
        # invited 단계 row 는 create 시 일괄 생성. apply 시 applied_at_ms 박힘 = 순번 결정.
        # select 시 selected/rejected + decided_at_ms 박힘. (§B history 와 합쳐 past_count/total 계산)
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS recruit_applications (
                recruit_id      TEXT NOT NULL,
                partner_phone   TEXT NOT NULL,
                status          TEXT NOT NULL DEFAULT 'invited',  -- invited/applied/selected/rejected
                invited_at_ms   INTEGER NOT NULL,
                applied_at_ms   INTEGER,                          -- NULL = 아직 지원 X. ORDER BY 순번.
                decided_at_ms   INTEGER,                          -- selected or rejected 시각
                share_id        TEXT,                             -- selected 시 자동 생성된 shared_sites.share_id
                PRIMARY KEY (recruit_id, partner_phone)
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_recruit_apps_partner "
            "ON recruit_applications(partner_phone, invited_at_ms DESC)"
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_recruit_apps_applied "
            "ON recruit_applications(recruit_id, applied_at_ms ASC)"
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_shared_sites_owner "
            "ON shared_sites(owner_phone, created_at_ms DESC)"
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_shared_sites_partner "
            "ON shared_sites(partner_phone, created_at_ms DESC)"
        )
        # §28 — 협업 진행 이벤트 (안드로이드 SERVER_HANDOFF_collab_notify_calendar 2026-06-09)
        # B 가 /api/shared/progress 호출 시 A 앞으로 이벤트 적재.
        # A 가 /api/shared/owner-events 폴링으로 받음 (TeamEventCenter 패턴).
        # 벽: 고객 phone / 대화 / 타 고객 절대 X. share 의 title + partner_name 만 노출.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS shared_owner_events (
                event_id        TEXT PRIMARY KEY,        -- 'evt_' + 10자 base62
                share_id        TEXT NOT NULL,
                owner_phone     TEXT NOT NULL,           -- A (현장 주인 — 이 이벤트 받는 사람)
                partner_phone   TEXT NOT NULL,           -- B (현장 진행자)
                step            TEXT NOT NULL,           -- departed/arrived/completed
                title           TEXT,                    -- share 의 title (배달 푸시 본문용)
                account_bank    TEXT,                    -- completed 시만
                account_no      TEXT,
                account_holder  TEXT,
                created_at_ms   INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_shared_owner_events_owner "
            "ON shared_owner_events(owner_phone, created_at_ms DESC)"
        )
        # 추가74 (2026-06-29) — 협업 현장 "한 줄 댓글" (안드로이드 요청).
        # A(owner) + B(partner) 둘이 현장 진행 얘기 나누는 채팅 스레드.
        # 접근 제어: 그 site_id 의 owner_phone 또는 partner_phone 만 읽기/쓰기.
        # 화이트리스트 게이트 X (미가입 partner 도 대화 가능).
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS shared_comments (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                site_id       TEXT NOT NULL,              -- shared_sites.share_id 와 같은 키
                author_phone  TEXT NOT NULL,              -- digits only
                author_name   TEXT,                       -- 표시용 (없으면 앱이 번호로)
                body          TEXT NOT NULL,
                created_at    INTEGER NOT NULL            -- epoch ms
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_shared_comments_site "
            "ON shared_comments(site_id, created_at)"
        )
        # §29 — 일당 마켓 Phase 1 (안드로이드 PLAN_labor_market 2026-06-11)
        # 직원/협업일당/협업사장 공통 흐름 — 완료·계좌 = 정산 스위치 + 번호별 이력 적립.
        # 키 = 전화 끝 8자리 (laborer.phone_suffix). 고객 정보 절대 미노출 (안전 라벨만).
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS laborer (
                phone_suffix              TEXT PRIMARY KEY,   -- 전화 끝 8자리
                completed_count           INTEGER NOT NULL DEFAULT 0,
                last_worked_at_ms         INTEGER,
                saved_bank                TEXT,
                saved_account_no          TEXT,
                saved_holder              TEXT,
                saved_account_updated_at_ms INTEGER,
                created_at_ms             INTEGER NOT NULL,
                updated_at_ms             INTEGER NOT NULL
            )
            """
        )
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS laborer_sites (
                site_id              INTEGER PRIMARY KEY AUTOINCREMENT,
                phone_suffix         TEXT NOT NULL,
                token                TEXT,
                owner_phone          TEXT NOT NULL,
                label                TEXT NOT NULL,   -- 안전 라벨 (고객 정보 X)
                worked_at_ms         INTEGER NOT NULL,
                photos_json          TEXT,            -- ["url1", ...]
                paid_at_ms           INTEGER,         -- 입금완료 시각 (옵션)
                completed_event_id   TEXT,            -- shared_owner_events.event_id
                created_at_ms        INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_laborer_sites_phone "
            "ON laborer_sites(phone_suffix, worked_at_ms DESC)"
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_laborer_sites_token "
            "ON laborer_sites(token, worked_at_ms DESC)"
        )
        # §30 — FCM 푸시 (SERVER_HANDOFF_fcm_push 2026-06-12)
        # 앱이 onNewToken 마다 등록. 같은 phone 의 token list 보관 (폰 여러 대 가능).
        # token 무효 (UnregisteredError) 시 자동 삭제.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS push_tokens (
                token            TEXT PRIMARY KEY,
                phone            TEXT NOT NULL,         -- 숫자만 (bizPhone)
                platform         TEXT,                  -- 'android' / 'ios'
                registered_at_ms INTEGER NOT NULL,
                updated_at_ms    INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_push_tokens_phone "
            "ON push_tokens(phone, updated_at_ms DESC)"
        )
        # 추가51 (2026-06-21) — 사용자 여정 트래킹 (사장님 요청: "베타테스터들 앱 사용 여정")
        # 앱이 화면 진입·버튼 클릭·캡쳐 시도 시점에 POST /api/event 호출 → 여기 INSERT.
        # admin/user/{phone} 페이지의 "🚶 사용자 여정" 카드에서 timeline 으로 봄.
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS app_events (
                event_id      INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_phone   TEXT NOT NULL,           -- 사장님 phone (누가 한 행동)
                event_name    TEXT NOT NULL,           -- 'screen_view' / 'button_click' / 'screenshot' / 'feature_use' 등
                screen        TEXT,                    -- 'home' / 'chat' / 'collab' / 'intake_form' 등
                target        TEXT,                    -- '버튼·요소 식별자' (예: 'btn_reply_suggest')
                extra_json    TEXT,                    -- 자유 페이로드 (JSON 객체)
                created_at_ms INTEGER NOT NULL
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_app_events_owner "
            "ON app_events(owner_phone, created_at_ms DESC)"
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_app_events_screen "
            "ON app_events(screen, created_at_ms DESC)"
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


def db_set_ready(phone: str, v2: dict, based_on_received_at_ms: Optional[int] = None) -> bool:
    """v2 dict 통째로 suggestions_json 에 저장.

    추가58 (2026-06-25) — 덮어쓰기 가드.
    based_on_received_at_ms = 이 prepare 호출이 기준으로 삼은 메시지 시각.
    저장 시점에 캐시의 based_on_received_at_ms 가 더 새것이면 = 다른 prepare 가 새 값 박은 상태.
    그러면 이번 ready 는 옛 결과라 폐기 (UPDATE 가 0 rows 영향).

    return: True = 저장 됐음. False = 새 prepare 가 덮어써서 skip.

    v2 = {scenario, scenario_confidence, scenario_reason, suggestions:[3 obj]}
    """
    now = _now_ms()
    with sqlite3.connect(DB_PATH) as con:
        if based_on_received_at_ms is not None:
            # 가드 적용 — atomic WHERE 로 옛 ready 덮어쓰기 차단.
            cur = con.execute(
                """
                UPDATE suggestions_cache SET
                    status='ready',
                    suggestions_json=?,
                    generated_at_ms=?,
                    updated_at_ms=?
                WHERE phone=? AND based_on_received_at_ms = ?
                """,
                (json.dumps(v2, ensure_ascii=False), now, now, phone, based_on_received_at_ms),
            )
            con.commit()
            if cur.rowcount == 0:
                print(f"[ready/skip-stale] {phone} based={based_on_received_at_ms} — 새 prepare 가 이미 더 새 값 박음, 폐기")
                return False
            return True
        else:
            # 옛 호출자 (가드 없이) — 그대로 덮어씀
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
            return True


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
# ============================================================================
# 추가96 — 등급 잠금 게이팅 1단계 (사장님 확정 목록: PRODUCT_MONETIZATION_DRAFT §9)
# 스위치: env GATING_ENFORCE=1 일 때만 작동. 기본 OFF = 베타 기간 아무 변화 없음.
# 규칙 (OFF→ON 시):
#   - tester(무료) 이고 free_until_ms 가 지났으면 → AI·접수서·견적서 402
#   - free_until_ms 가 NULL 인 tester (초기 베타 사장님들) = 무제한 (베타 답례, 유료화 draft §7-1)
#   - standard/premium (subscribers 활성 유료) = 통과
# 402 응답 detail 은 JSON 문자열 {"code":"free_expired", ...} — 앱이 파싱해 upsell 모달.
# ============================================================================

GATING_ENFORCE = os.environ.get("GATING_ENFORCE", "0") == "1"


def _check_free_trial_gate(phone: Optional[str]) -> None:
    """무료 체험 만료 게이트. GATING_ENFORCE=1 + tester + free_until 경과 시 402."""
    if not GATING_ENFORCE or not phone:
        return
    phone_digits = _norm_phone(phone)
    if not phone_digits:
        return
    now = _now_ms()
    with db_conn() as con:
        sub = con.execute(
            "SELECT monthly_price_krw, plan_tier FROM subscribers "
            "WHERE phone = ? AND churned_at_ms IS NULL",
            (phone_digits,),
        ).fetchone()
        if sub and ((sub[0] or 0) > 0 or sub[1] in ("standard_50k", "premium_100k", "pro")):
            return  # 유료 = 통과
        wl = con.execute(
            "SELECT free_until_ms FROM beta_whitelist WHERE phone = ?",
            (phone_digits,),
        ).fetchone()
    if not wl:
        return  # 비멤버는 whitelist 게이트가 별도 처리
    free_until = wl[0]
    if free_until is None:
        return  # 초기 베타 = 무제한
    if now > free_until:
        print(f"[gating] BLOCK {phone_digits} (무료 만료 {((now - free_until) // 86_400_000)}일 경과)")
        raise HTTPException(402, json.dumps({
            "code": "free_expired",
            "message": "무료 체험 기간이 끝났어요. 요금제를 선택하면 계속 쓸 수 있어요.",
            "freeUntilMs": free_until,
        }, ensure_ascii=False))


def check_rate_limit(phone: str) -> None:
    """일일 한도 초과면 HTTPException(429) 던짐.
    추가96 — AI 기능 잠금 초크포인트 겸용 (_check_free_trial_gate)."""
    _check_free_trial_gate(phone)  # 추가96 — 모든 LLM endpoint 가 여길 지나감
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


# §26 (2026-06-11) — Anthropic/Gemini exception → 정확한 HTTP status + 한국어 메시지.
# 이전엔 모두 502 로 묻혀서 진단 늦어짐 (예: 크레딧 부족인지 rate limit 인지 모름).
# 이제 endpoint 가 명확한 코드 + 한국어 메시지 반환 → 안드로이드 토스트 + 사장님 즉시 인지.

def _classify_llm_error(err: Exception) -> tuple[int, str]:
    """LLM exception → (HTTP status, 한국어 메시지).

    Anthropic BadRequestError(credit) / RateLimitError(429) / overloaded(529) 구분.
    Gemini API status 503 등도 같은 패턴.
    """
    name = type(err).__name__
    msg_lower = str(err).lower()
    # 크레딧 부족 (Anthropic 의 BadRequestError + "credit balance" 메시지)
    if "credit balance" in msg_lower or "billing" in msg_lower:
        return 402, "AI 서비스 크레딧 부족 — console.anthropic.com 에서 충전 필요"
    # Rate limit (429)
    if name == "RateLimitError" or "rate limit" in msg_lower or " 429" in msg_lower:
        return 429, "AI 서비스 호출 한도 초과 — 잠시 후 다시 시도"
    # Overloaded (Anthropic 529 / Gemini 503)
    if "overloaded" in msg_lower or " 529" in msg_lower or " 503" in msg_lower:
        return 503, "AI 서비스 일시 과부하 — 잠시 후 다시 시도"
    # 인증/API 키 문제
    if "api key" in msg_lower or "authentication" in msg_lower or "unauthorized" in msg_lower or " 401" in msg_lower:
        return 503, "AI 서버 인증 실패 — 사장님께 알림 필요"
    # 그 외 (네트워크 / 미상)
    return 502, f"AI 서비스 호출 실패: {name}"


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

{PRICE_CASE}

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

# 2026-06-17 멀티업종 — 가격 문의 케이스 처리를 업종별로. 줄눈/타일은 기존 그대로, 그 외는 범용.
_PRICE_CASE_TILE = """가격 문의 케이스 처리:
- 신축/구축 미확정 → 한 후보는 "신축이세요 구축이세요?" 물어보기.
- 타일 크기 미언급 → 한 후보는 "타일 사진 보내주시면 정확히 견적 드려요" 안내.
- 답변 형식: 표 금지. 항목별 줄바꿈 나열 + 합계.
- 실리콘 제거/셀프줄눈/누수 가능성 = "현장 확인 후 추가될 수 있어요" 한 문장 덧붙임."""

_PRICE_CASE_GENERIC = """가격 문의 케이스 처리:
- 견적에 필요한 정보(작업 범위·면적·현장 상태 등)가 부족하면 한 후보는 그 정보를 물어보기.
- 정확한 금액이 애매하면 한 후보는 "사진 보내주시면 정확히 견적 드려요" 또는 "현장 보고 정확히 안내드릴게요".
- 답변 형식: 표 금지. 항목별 줄바꿈 나열 + 합계."""


def _is_tile_trade(trade: Optional[str]) -> bool:
    """줄눈/타일 계열 업종인가. 미전송(구버전·줄눈 사장님)이면 True = 기존 줄눈 동작 유지."""
    t = (trade or "").replace(" ", "")
    if not t:
        return True
    return any(k in t for k in ("줄눈", "타일", "메지", "방수", "에폭시"))


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
    trade: Optional[str] = None,
    price_list: Optional[str] = None,
    principles: Optional[list[str]] = None,
) -> list[dict]:
    """§16 — Tone RAG 통합 system 빌더. (+2026-06-17 멀티업종: 역할·가격표를 사장님별로)

    block C (사장님 톤) 위치에 RAG 로 retrieved top-10 inject.
    RAG 비활성화 또는 풀 비어있으면 기존 ownerToneSamples 로 fallback.

    block C 가 cache_control 박혀있어서:
      - 같은 device + 같은 query → 같은 retrieved set → cache hit
      - 다른 query → cache miss + 새 retrieval
    """
    # block A 역할 — 업종이 오면 "{업종} 사장님", 없으면 기존 "줄눈 시공" (구버전·줄눈 폴백)
    role = (trade or "").strip()
    block_a = (
        _SYSTEM_BLOCK_A_FIXED.replace("줄눈 시공 사장님", f"{role} 사장님", 1)
        if role else _SYSTEM_BLOCK_A_FIXED
    )

    # 2026-06-17 "막내가 알아낸 사장님 원칙" — 말투/사례 위 3번째 층(판단 기준)을 block A 에 주입.
    #   답변 전략을 고를 때 이 원칙을 우선 적용. (규칙이 아니라 가이드 — 상황 보고 유연하게)
    clean_principles = [p.strip() for p in (principles or []) if p and p.strip()]
    if clean_principles:
        block_a = block_a + (
            "\n\n────── 사장님의 응대 원칙 (반드시 우선 반영) ──────\n"
            "이 사장님이 실제로 지키는 판단 기준이다. 답변 전략·내용을 고를 때 먼저 적용하라.\n"
            "단, 절대 규칙이 아니라 가이드다 — 상황이 분명히 안 맞으면 무리해서 끼워맞추지 마라.\n"
            + "\n".join(f"- {p}" for p in clean_principles)
        )

    # 가격표 — 사장님이 앱에 입력한 게 오면 그걸, 없으면: 줄눈/타일은 전역 pricing.md(폴백), 그 외는 "없음"
    if price_list and price_list.strip():
        pricing_block = "────── 가격표 (사장님 입력) ──────\n" + price_list.strip()
    elif _is_tile_trade(trade):
        pricing_block = "────── 가격표 ──────\n" + load_pricing()
    else:
        pricing_block = (
            "────── 가격표 ──────\n"
            "(아직 등록된 가격표가 없어요. 금액을 추측하지 말고, 한 후보는 "
            "'사진 보내주시면 정확히 견적 드려요' 또는 '현장 보고 정확히 안내드릴게요' 로 답하세요.)"
        )

    # block D — 가격 문의 케이스 처리를 업종별로
    block_d = _SYSTEM_BLOCK_D_FORMAT.replace(
        "{PRICE_CASE}", _PRICE_CASE_TILE if _is_tile_trade(trade) else _PRICE_CASE_GENERIC
    )

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
        {"type": "text", "text": block_a,        "cache_control": {"type": "ephemeral"}},
        {"type": "text", "text": pricing_block,  "cache_control": {"type": "ephemeral"}},
        {"type": "text", "text": tone_block,     "cache_control": {"type": "ephemeral"}},
        {"type": "text", "text": block_d},
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
    # 2026-06-17 — 폰별 말투 풀 격리. 이 폰의 owner_tone 에서만 RAG retrieval.
    #   미전송(구버전 앱) 이면 "owner-anon" 폴백 → 기존 동작 유지(점진 마이그레이션).
    deviceId: Optional[str] = None
    # 2026-06-17 멀티업종 — 대표 업종(AI 역할) + 사장님 가격표(전역 줄눈 pricing.md 대체).
    #   미전송이면 줄눈 폴백(구버전·줄눈 사장님). 빈 가격표면 "가격표 없음" 처리.
    ownerTrade: Optional[str] = None
    priceList: Optional[str] = None
    # 2026-06-17 "막내가 알아낸 사장님 원칙" — 답변의 판단 기준(왜 그렇게 답하는지). 켜진 것만 옴.
    principles: list[str] = Field(default_factory=list)
    # 추가37 (2026-06-18) — 화이트리스트 게이트용 사장님 phone.
    # 위 phone 은 *고객* (대화 상대) 라 가드에 부적절. owner_phone 으로 owner 식별.
    # 안드로이드가 보내면 가드, 없으면 skip (graceful, 점진 적용).
    owner_phone: Optional[str] = None


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
    # 추가38 (2026-06-18) — owner 단위 집계용 사장님 phone
    owner_phone: Optional[str] = None
    # 추가50 (2026-06-21) — 앱 onboarding 업종
    owner_trade: Optional[str] = None


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
        device_id=(req.deviceId or "owner-anon"),  # 2026-06-17 폰별 격리. 미전송(구버전)이면 폴백.
        trade=req.ownerTrade,
        price_list=req.priceList,
        principles=req.principles,
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
            "maxOutputTokens": 3072,
            # ★ 2026-06-16 (android Claude, 사장님 승인) — fallback_default 버그 fix.
            #   Gemini 2.5 Flash 는 thinking 모델. thinking 토큰이 maxOutputTokens 를 같이 먹어서,
            #   가격 4개짜리 긴 문의처럼 복잡한 입력에선 thinking 이 예산을 다 써 실제 JSON 이 truncate →
            #   파서 4단계 전부 실패 → "💬 무난 답변" 하드코딩으로 빠지던 원인.
            #   추천 답변은 추론 불필요(톤 모방 생성)라 thinking 끔 → truncation 해소 + 더 빠르고 저렴.
            "thinkingConfig": {"thinkingBudget": 0},
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
        device_id=(req.deviceId or "owner-anon"),  # 2026-06-17 폰별 격리. 미전송(구버전)이면 폴백.
        trade=req.ownerTrade,
        price_list=req.priceList,
        principles=req.principles,
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

    추가62 (2026-06-29) — Gemini 실패 시 Sonnet 자동 폴백.
    안드로이드 진단: ↻ 새로고침이 "시간 초과" — prepare-reply 백그라운드 Gemini 호출이
    실패해서 db_set_missing 박혀버려 status='missing' 남는 경우. → Sonnet 으로 재시도.

    model="sonnet" → Sonnet 4.6 + Anthropic SDK (기존 경로)
    model="gemini" → Gemini 2.5 Flash + response_schema → 실패 시 Sonnet 폴백
    """
    phone = req.phone
    start_ms = _now_ms()
    based_ts = getattr(req, "latestMessageReceivedAtMs", None)

    async def _run_sonnet():
        v2, response = await call_claude_for_suggestions_with_meta(req)
        sec = (_now_ms() - start_ms) / 1000.0
        log_usage(phone, "prepare-reply", response)
        _log_llm_usage_from_response("prepare-reply", response)
        saved = db_set_ready(phone, v2, based_on_received_at_ms=based_ts)
        usage = response.usage
        print(
            f"[ready/sonnet] {phone} scenario={v2['scenario']} conf={v2['scenario_confidence']} "
            f"intents={[s['intent_key'] for s in v2['suggestions']]} "
            f"(in={getattr(usage,'input_tokens',0)} "
            f"cache_read={getattr(usage,'cache_read_input_tokens',0)} "
            f"out={getattr(usage,'output_tokens',0)} "
            f"latency={sec:.1f}s saved={saved})"
        )

    async def _run_gemini():
        v2, usage_meta = await call_gemini_for_suggestions_with_meta(req)
        sec = (_now_ms() - start_ms) / 1000.0
        _log_gemini_suggestions_usage(usage_meta, sec)
        saved = db_set_ready(phone, v2, based_on_received_at_ms=based_ts)
        print(
            f"[ready/gemini] {phone} scenario={v2['scenario']} conf={v2['scenario_confidence']} "
            f"intents={[s['intent_key'] for s in v2['suggestions']]} "
            f"(in={usage_meta.get('promptTokenCount',0)} "
            f"out={usage_meta.get('candidatesTokenCount',0)} "
            f"latency={sec:.1f}s saved={saved})"
        )

    try:
        check_rate_limit(phone)
        if model == "gemini":
            try:
                await _run_gemini()
            except asyncio.CancelledError:
                raise
            except Exception as gemini_e:
                # 추가62 — Gemini 실패 → Sonnet 자동 폴백. 사용자 영향 X.
                import traceback
                print(f"[fallback/gemini→sonnet] {phone}: {type(gemini_e).__name__}: {gemini_e}")
                print(traceback.format_exc())
                await _run_sonnet()
        else:
            await _run_sonnet()
    except asyncio.CancelledError:
        print(f"[cancelled] {phone}")
        raise
    except HTTPException as e:
        print(f"[rate-limit] {phone}: {e.detail}")
        db_set_missing(phone)
    except Exception as e:
        # 추가62 — Sonnet 도 실패하면 = 진짜 missing. traceback 까지.
        import traceback
        print(f"[failed/all] {phone}: {type(e).__name__}: {e}")
        print(traceback.format_exc())
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
    # §D (2026-06-13) — 출동 2h 전 알림 poller. uvicorn 살아있는 동안 무한.
    remind_task = asyncio.create_task(_remind_poller_loop())
    print(f"[boot] §D remind poller scheduled")
    # 추가105 — 블로그 매일 07:30 자동 발행 (BLOG_AUTOPUBLISH=0 으로 off)
    blog_task = asyncio.create_task(_blog_autopublish_loop())
    print(f"[boot] blog autopublish scheduled (enabled={_BLOG_AUTOPUBLISH})")
    try:
        yield
    finally:
        remind_task.cancel()
        blog_task.cancel()


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
    # 추가37 (2026-06-18) — 화이트리스트 게이트는 owner_phone 으로 (req.phone 은 customer phone).
    # 안드로이드가 owner_phone 안 보내면 skip (graceful, 점진 적용).
    # 추가50 (2026-06-21) — ownerTrade 같이 저장.
    _ensure_and_touch_beta_whitelist(req.owner_phone, owner_trade=req.ownerTrade)
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

  /* ─── 추가80 시각화 강화 ─── */
  .hero.today { border-top: 3px solid var(--accent); }
  .hero.all   { border-top: 3px solid #5e5ce6; }
  .delta { font-size: 11px; font-weight: 700; margin-left: 6px; }
  .delta.up   { color: var(--hot); }   /* 비용 증가 = 빨강 */
  .delta.down { color: var(--ok); }
  .share-bar {
    grid-column: 1 / -1; height: 6px; border-radius: 3px;
    background: var(--line); overflow: hidden; margin-top: 2px;
  }
  .share-bar > span { display:block; height:100%; border-radius:3px; transition: width .3s; }
  .share-pct { font-size: 10px; color: var(--muted); grid-column: 1 / -1; margin-top: 2px; }
  .ep-bar { height: 5px; border-radius: 3px; background: var(--line); overflow: hidden; margin-top: 5px; max-width: 220px; }
  .ep-bar > span { display:block; height:100%; background: var(--accent); border-radius:3px; }

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
    <div style="display:flex;align-items:center;gap:10px;">
      <a href="/admin" style="text-decoration:none;font-size:20px;color:var(--accent);
        background:var(--card);border-radius:10px;padding:6px 10px;box-shadow:var(--shadow);
        line-height:1;-webkit-tap-highlight-color:transparent;">←</a>
      <div>
        <h1>🛠 RING-GO 사용량</h1>
        <div class="meta" id="meta">마지막 업데이트 -</div>
      </div>
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
    <div class="hero today"><div class="label">오늘</div>
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
            <th style="text-align:left;">상호 · 업종</th>
            <th class="num">호출</th>
            <th class="num">비용</th>
            <th class="num">구독료</th>
            <th class="num">유저 마진</th>
          </tr>
        </thead>
        <tbody id="topUsersBody"><tr><td colspan="6" style="text-align:center;color:var(--muted);padding:12px 0">로딩 중…</td></tr></tbody>
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

  <!-- 🔋 크레딧 크로스체크 (관리자만) -->
  <div class="card admin-only" id="rechargeCard" style="display:none;">
    <h2>🔋 크레딧 크로스체크</h2>
    <div id="rechargeLast" style="font-size:13px;line-height:1.7;color:var(--muted);">로딩 중…</div>
    <div style="background:var(--line);border-radius:6px;height:10px;margin:10px 0;overflow:hidden;">
      <div id="rechargeBar" style="background:var(--ok);height:100%;width:0%;transition:width .3s;"></div>
    </div>
    <div style="display:flex;gap:8px;align-items:center;margin-bottom:10px;">
      <span style="font-size:13px;">$</span>
      <input id="rechargeAmount" type="number" step="0.01" value="11"
        style="width:90px;padding:8px 10px;border:1px solid var(--line);border-radius:8px;font-size:14px;" />
      <input id="rechargeNote" type="text" placeholder="메모 (예: 자동충전)"
        style="flex:1;padding:8px 10px;border:1px solid var(--line);border-radius:8px;font-size:13px;" />
      <button class="refresh" onclick="registerRecharge()">⚡ 충전 등록</button>
    </div>
    <div style="overflow-x:auto;">
      <table style="width:100%;border-collapse:collapse;font-size:12px;">
        <thead><tr style="color:var(--muted);text-align:left;">
          <th style="padding:4px 6px;">충전일</th>
          <th style="padding:4px 6px;" class="num">충전액</th>
          <th style="padding:4px 6px;" class="num">그 후 가계부 사용액</th>
          <th style="padding:4px 6px;">대조</th>
        </tr></thead>
        <tbody id="rechargeHistoryBody"></tbody>
      </table>
    </div>
    <div style="margin-top:8px;font-size:11px;color:var(--muted);line-height:1.5;">
      💡 사용법: Anthropic 자동충전 결제 알림이 오면 [⚡ 충전 등록] 클릭.
      다음 충전이 왔을 때 "그 후 가계부 사용액"이 충전액과 비슷하면 정상,
      가계부보다 훨씬 빨리 충전이 반복되면 <b>누수 의심</b> (외부에서 같은 API 키 사용 등).
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
  'address-resolve':       '📍 주소 찾기',
  'intent-classify':       '의도 분류',
  'style-profile-learn':   '말투 학습',
  'reply-suggest':         '답변 추천 (보조)',
  'call-summary':          '통화 요약 (문자로)',
  'call-audio-summary':    '통화 녹음 요약',
  'customer-persona':      '고객 성향 분석',
  'infer-principle':       '사장님 원칙 추론',
  'tone-profile-plain':    '말투 비교 (기본 답변)',
  'tone-profile-mine':     '말투 비교 (사장님 톤)',
  'tone-profile-traits':   '말투 특징 분석',
  'extract-pricing':       '문자에서 가격표 추출',
  'pricing-starter':       '업종 스타터 가격표',
  'name-template':         '템플릿 제목 작명',
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
    document.getElementById('monthCost').textContent  = fmtKRW(month.total.cost_krw);
    document.getElementById('monthCalls').textContent = fmt(month.total.calls) + ' 건';
    document.getElementById('allCost').textContent    = fmtKRW(allp.total.cost_krw);
    document.getElementById('allCalls').textContent   = fmt(allp.total.calls) + ' 건';

    // 오늘 카드 — 어제 대비 증감 (추가80): month.daily_trend 의 마지막 두 날 비교
    (function () {
      const el = document.getElementById('todayCalls');
      let deltaHtml = '';
      const tr = month.daily_trend || [];
      if (tr.length >= 2) {
        const yst = tr[tr.length - 2].cost_krw;
        const cur = today.total.cost_krw;
        if (yst > 0) {
          const diff = cur - yst;
          const pct = Math.round(Math.abs(diff) / yst * 100);
          if (Math.abs(diff) >= 1) {
            deltaHtml = diff > 0
              ? '<span class="delta up">▲ 어제보다 ' + pct + '%</span>'
              : '<span class="delta down">▼ 어제보다 ' + pct + '%</span>';
          } else {
            deltaHtml = '<span class="delta" style="color:var(--muted)">어제와 비슷</span>';
          }
        }
      }
      el.innerHTML = fmt(today.total.calls) + ' 건' + deltaHtml;
    })();

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

    // 추가80 — 모델별 비용 비중 바 (이번 달 총비용 대비 %)
    const monthTotalCost = Math.max(1, month.total.cost_krw || 0);
    const MODEL_BAR_COLOR = {
      'claude-sonnet-4-6': 'var(--accent)',
      'claude-haiku-4-5': 'var(--ok)',
      'gemini-2.5-flash': 'var(--warn)',
      'kakao-local': '#5e5ce6',
    };
    function modelBarColor(id) {
      for (const k of Object.keys(MODEL_BAR_COLOR)) {
        if (id.startsWith(k)) return MODEL_BAR_COLOR[k];
      }
      return 'var(--accent)';
    }

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
      const sharePct = empty ? 0 : Math.min(100, cost / monthTotalCost * 100);
      const shareHtml = empty ? '' : `
          <div class="share-bar"><span style="width:${sharePct.toFixed(1)}%;background:${modelBarColor(id)};"></span></div>
          <div class="share-pct">이번 달 비용의 ${sharePct.toFixed(1)}%</div>
      `;
      return `
        <div class="model-card ${empty ? 'empty' : ''}">
          <div class="model-name">
            ${meta.name}${badge}
            <div class="model-tier">${meta.tier}</div>
          </div>
          <div class="stat"><span class="v">${fmt(calls)}</span><span class="k">호출</span></div>
          <div class="stat"><span class="v">${fmtKRW(cost)}</span><span class="k">총 비용</span></div>
          <div class="stat"><span class="v">${empty ? '—' : fmtKRW2(avg)}</span><span class="k">건당 평균</span></div>
          ${shareHtml}
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
      // 추가80 — 기능별 비용 비중 바 (최대 비용 기능 = 100%)
      const epMaxCost = Math.max(1, ...eps.map(([, s]) => s.cost_krw));
      body.innerHTML = eps.map(([name, s]) => {
        const nameKo = EP_NAMES_KO[name] || ('미분류 기능 (' + name + ')');
        // 한글 이름 있으면 영어 원문 숨김 (사장님 요청 2026-07-02). 미분류만 원문 노출.
        const w = Math.min(100, s.cost_krw / epMaxCost * 100);
        const pctOfMonth = monthTotalCost > 1 ? (s.cost_krw / monthTotalCost * 100) : 0;
        return `
          <tr>
            <td class="ep">${nameKo}
              <div class="ep-bar"><span style="width:${w.toFixed(1)}%"></span></div></td>
            <td class="num">${fmt(s.calls)}</td>
            <td class="num">${fmtKRW(s.cost_krw)}<div style="font-size:10px;color:var(--muted)">${pctOfMonth.toFixed(0)}%</div></td>
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
      // body rows — 추가80: 히트맵 색칠 (호출수 비례, 진할수록 많음)
      const matrixMax = Math.max(1, ...matrixEps.flatMap(ep =>
        matrixModels.map(m => matrixData[ep][m]?.calls || 0)));
      matrixBody.innerHTML = matrixEps.map(ep => {
        const epKo = EP_NAMES_KO[ep] || ep;
        return `
          <tr>
            <td class="ep" style="padding:6px 4px;">${epKo}</td>
            ${matrixModels.map(m => {
              const cell = matrixData[ep][m];
              const calls = cell ? cell.calls : 0;
              const bg = heatmapCellColor(calls, matrixMax);
              const ratio = calls / matrixMax;
              const fg = calls === 0 ? 'var(--line)' : (ratio > 0.55 ? '#fff' : 'var(--text)');
              return `<td class="num" style="padding:6px 6px;background:${bg};color:${fg};border-radius:4px;">${calls === 0 ? '·' : fmt(calls)}</td>`;
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
      // 추가80 — 오늘(마지막) 막대 강조 + 7일 평균 점선 + 라벨은 최대/오늘만 (덜 어지럽게)
      const maxCost = Math.max(...trend.map(d => d.cost_krw), 1);
      const avgCost = trend.reduce((a, d) => a + d.cost_krw, 0) / trend.length;
      const maxIdx = trend.reduce((mi, d, i) => d.cost_krw > trend[mi].cost_krw ? i : mi, 0);
      const barWidth = 700 / Math.max(7, trend.length);
      const barGap = barWidth * 0.25;
      const avgY = 90 - (avgCost / maxCost) * 80;
      const bars = trend.map((d, i) => {
        const isToday = i === trend.length - 1;
        const h = Math.max(2, (d.cost_krw / maxCost) * 80);
        const x = i * barWidth + barGap / 2;
        const y = 90 - h;
        const fill = isToday ? 'var(--hot)' : 'var(--accent)';
        const opacity = isToday ? 1 : 0.75;
        const showLabel = i === maxIdx || isToday;
        const label = showLabel
          ? `<text x="${(x + (barWidth - barGap) / 2).toFixed(1)}" y="${(y - 4).toFixed(1)}" text-anchor="middle" font-size="10" font-weight="700" fill="${isToday ? '#ff3b30' : '#1d1d1f'}">₩${fmt(Math.round(d.cost_krw))}</text>`
          : '';
        return `
          <rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${(barWidth - barGap).toFixed(1)}" height="${h.toFixed(1)}" fill="${fill}" opacity="${opacity}" rx="3">
            <title>${d.date} · ₩${fmt(Math.round(d.cost_krw))} · ${fmt(d.calls)}건</title>
          </rect>
          ${label}
        `;
      }).join('');
      chart.innerHTML =
        `<line x1="0" y1="${avgY.toFixed(1)}" x2="700" y2="${avgY.toFixed(1)}" stroke="#86868b" stroke-width="1" stroke-dasharray="4 4" opacity="0.6"/>` +
        `<text x="698" y="${(avgY - 4).toFixed(1)}" text-anchor="end" font-size="9" fill="#86868b">7일 평균 ₩${fmt(Math.round(avgCost))}</text>` +
        bars;
      labels.innerHTML = trend.map((d, i) => {
        const dd = d.date.slice(5).replace('-', '/');  // "05/28"
        const isToday = i === trend.length - 1;
        return `<span style="flex:1;text-align:center;${isToday ? 'color:var(--hot);font-weight:700;' : ''}">${isToday ? '오늘' : dd}</span>`;
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

// ─── 🔋 크레딧 크로스체크 (추가79) ───
async function loadRechargeCard(token) {
  const s = await fetchJSON('/api/admin/recharge/status', {
    headers: { 'X-Admin-Token': token },
  });
  const lastEl = document.getElementById('rechargeLast');
  const bar = document.getElementById('rechargeBar');
  const body = document.getElementById('rechargeHistoryBody');
  if (!s.last) {
    lastEl.innerHTML = '아직 충전 기록 없음 — Anthropic 결제가 확인되면 아래 [⚡ 충전 등록]을 눌러 시작하세요.';
    bar.style.width = '0%';
    body.innerHTML = '';
    return;
  }
  const d = new Date(s.last.rechargedAtMs);
  const dateStr = (d.getMonth()+1) + '/' + d.getDate() + ' ' +
    String(d.getHours()).padStart(2,'0') + ':' + String(d.getMinutes()).padStart(2,'0');
  lastEl.innerHTML =
    '마지막 충전: <b>$' + s.last.amountUsd.toFixed(2) + '</b> (' + dateStr +
    (s.last.note ? ' · ' + s.last.note : '') + ')<br/>' +
    '그 후 사용: <b>' + fmtKRW(s.last.usedSinceKrw) + '</b> (≈ $' + s.last.usedSinceUsd.toFixed(2) + ')' +
    ' · 추정 잔여 <b>$' + s.last.remainingEstUsd.toFixed(2) + '</b> (' + s.last.usedPct + '% 소진)';
  bar.style.width = s.last.usedPct + '%';
  bar.style.background = s.last.usedPct >= 85 ? 'var(--hot)' :
                         s.last.usedPct >= 60 ? 'var(--warn)' : 'var(--ok)';
  body.innerHTML = s.history.map(function(h, i) {
    const hd = new Date(h.rechargedAtMs);
    const ds = hd.getFullYear() + '.' + (hd.getMonth()+1) + '.' + hd.getDate();
    let check = '';
    if (i === 0) {
      check = '<span style="color:var(--muted)">진행 중</span>';
    } else {
      const ratio = h.amountUsd > 0 ? h.usedSinceUsd / h.amountUsd : 0;
      check = (ratio >= 0.7 && ratio <= 1.3)
        ? '<span style="color:var(--ok)">✓ 정상</span>'
        : '<span style="color:var(--hot)">⚠ 차이 큼 (' + (ratio*100).toFixed(0) + '%)</span>';
    }
    return '<tr>' +
      '<td style="padding:4px 6px;">' + ds + (h.note ? ' <span style="color:var(--muted)">' + h.note + '</span>' : '') + '</td>' +
      '<td style="padding:4px 6px;" class="num">$' + h.amountUsd.toFixed(2) + '</td>' +
      '<td style="padding:4px 6px;" class="num">' + fmtKRW(h.usedSinceKrw) + ' (≈$' + h.usedSinceUsd.toFixed(2) + ')</td>' +
      '<td style="padding:4px 6px;">' + check + '</td>' +
      '</tr>';
  }).join('');
}

async function registerRecharge() {
  const token = getAdminToken();
  if (!token) { alert('관리자 토큰 필요'); return; }
  const amount = parseFloat(document.getElementById('rechargeAmount').value);
  if (!(amount > 0)) { alert('금액을 확인해주세요'); return; }
  const note = document.getElementById('rechargeNote').value.trim();
  if (!confirm('$' + amount.toFixed(2) + ' 충전을 지금 시각으로 등록할까요?')) return;
  try {
    await fetchJSON('/api/admin/recharge', {
      method: 'POST',
      headers: { 'X-Admin-Token': token, 'Content-Type': 'application/json' },
      body: JSON.stringify({ amountUsd: amount, note: note || null }),
    });
    document.getElementById('rechargeNote').value = '';
    await loadRechargeCard(token);
  } catch (e) {
    alert('등록 실패: ' + e);
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

  // 🔋 크레딧 크로스체크 (추가79) — 실패해도 다른 카드 안 막음
  loadRechargeCard(token).catch(e => console.warn('recharge card', e));

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
    topBody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--muted);padding:12px 0">이번 달 사용 기록 없음</td></tr>';
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
      // 추가113 — 상호·업종 셀 (여러 소스에서 해석). 없으면 '미확인'.
      const esc = (s) => (s || '').replace(/[<>&"]/g, c => ({'<':'&lt;','>':'&gt;','&':'&amp;','"':'&quot;'}[c]));
      let bizCell;
      if (u.biz_name || u.trade) {
        const nm = u.biz_name ? `<b style="color:var(--fg);">${esc(u.biz_name)}</b>` : '';
        const tr = u.trade ? `<span style="display:inline-block;font-size:10px;color:var(--accent,#3182F6);background:rgba(49,130,246,.1);border-radius:6px;padding:1px 6px;margin-left:${u.biz_name?'4px':'0'};">${esc(u.trade)}</span>` : '';
        bizCell = `${nm}${tr}`;
      } else {
        bizCell = '<span style="color:var(--muted);font-size:11px;">미확인</span>';
      }
      return `
        <tr style="${rowStyle}">
          <td class="ep" style="padding:6px 4px;">${heavy ? '🔥 ' : ''}${phoneDisp}<div style="font-size:10px;color:var(--muted);">${u.plan_tier}</div></td>
          <td style="padding:6px 4px;">${bizCell}</td>
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


@app.get("/admin/usage-chart", response_class=HTMLResponse)
async def admin_dashboard() -> HTMLResponse:
    """사람이 보기 좋은 사용량 대시보드 (기존). 추가33 (2026-06-15) 이후 path 변경:
    `/admin` 은 새 admin 홈 허브가 차지 → 이 페이지는 `/admin/usage-chart` 로.

    데이터는 /api/usage-stats 와 /admin/usage 의 JSON 을 fetch 해서 렌더.
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


VALID_PLAN_TIERS = {"founder", "beta", "pro", "enterprise", "team_99k",
                    "standard_50k", "premium_100k"}  # 추가84 — 일반(5만)/특별(10만)


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
              AND au.phone NOT IN (%s)
            GROUP BY au.phone
            ORDER BY calls DESC
            LIMIT 10
            """ % (",".join("?" * len(STATS_EXCLUDE_PHONES)) or "''"),
            (KRW_PER_USD, month_start_ms, *STATS_EXCLUDE_PHONES),
        ).fetchall()
        # 추가113 — 미등록 사용자도 식별되게: 상호/업종을 여러 소스에서 해석.
        #   subscribers.company/name → 최근 intake_forms.biz_name(발행 시 입력한 상호)
        #   → beta_whitelist 의 name/owner_trade. 하나라도 있으면 표시.
        def _resolve_biz(phone: str) -> dict:
            biz, trade = "", ""
            row = conn.execute(
                "SELECT company, name FROM subscribers WHERE phone = ?", (phone,)
            ).fetchone()
            if row:
                biz = (row["company"] or row["name"] or "").strip()
            if not biz:
                row = conn.execute(
                    "SELECT biz_name FROM intake_forms WHERE owner_phone = ? "
                    "AND biz_name IS NOT NULL AND TRIM(biz_name) != '' "
                    "ORDER BY created_at_ms DESC LIMIT 1", (phone,)
                ).fetchone()
                if row:
                    biz = (row["biz_name"] or "").strip()
            row = conn.execute(
                "SELECT owner_trade, name FROM beta_whitelist WHERE phone = ?", (phone,)
            ).fetchone()
            if row:
                trade = (row["owner_trade"] or "").strip()
                if not biz:
                    biz = (row["name"] or "").strip()
            return {"biz_name": biz, "trade": trade}

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
            biz = _resolve_biz(r["phone"])
            top_users.append({
                "phone":             r["phone"],
                "name":              r["name"],
                "biz_name":          biz["biz_name"],   # 추가113
                "trade":             biz["trade"],      # 추가113
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


# ============================================================================
# 추가79 — 크레딧 충전 크로스체크 (Anthropic 자동충전 vs 우리 가계부)
# 사장님 요청 (2026-07-02): 자동충전 결제가 오면 [충전 등록] 클릭 → 그 시점 기록
# → 이후 사용액이 다시 쌓임 → 다음 충전 때 "가계부 사용액 vs 실제 결제" 대조로
# 금액 누수 (같은 키 외부 사용, 단가표 오류 등) 감지.
# ============================================================================

class RechargeCreateRequest(BaseModel):
    amountUsd: float = 11.0     # 기본 = $10 충전 + 부가세
    note: Optional[str] = None


@app.post("/api/admin/recharge")
async def admin_recharge_create(
    req: RechargeCreateRequest,
    x_admin_token: Optional[str] = Header(None),
) -> dict:
    """충전 등록 — 사장님이 Anthropic 결제 확인한 시점에 클릭."""
    _admin_auth(x_admin_token)
    try:
        amount = float(req.amountUsd)
    except (TypeError, ValueError):
        raise HTTPException(400, "amountUsd 숫자여야 합니다")
    if not (0 < amount <= 10_000):
        raise HTTPException(400, "amountUsd 는 0 초과 10,000 이하")
    note = (req.note or "").strip()[:100] or None
    now = _now_ms()
    with db_conn() as con:
        cur = con.execute(
            "INSERT INTO api_recharges (amount_usd, note, recharged_at_ms) VALUES (?, ?, ?)",
            (amount, note, now),
        )
        con.commit()
        rid = cur.lastrowid
    print(f"[recharge] 등록 id={rid} ${amount} note={note or '-'}")
    return {"ok": True, "id": rid, "amountUsd": amount, "rechargedAtMs": now}


@app.get("/api/admin/recharge/status")
async def admin_recharge_status(
    x_admin_token: Optional[str] = Header(None),
) -> dict:
    """마지막 충전 이후 사용액 + 충전 이력 (구간별 가계부 사용액 포함)."""
    _admin_auth(x_admin_token)
    now = _now_ms()
    with db_conn() as con:
        rows = con.execute(
            "SELECT id, amount_usd, note, recharged_at_ms FROM api_recharges "
            "ORDER BY recharged_at_ms DESC LIMIT 12"
        ).fetchall()

        def _usage_between(a_ms: int, b_ms: int) -> float:
            r = con.execute(
                "SELECT COALESCE(SUM(cost_krw), 0) FROM llm_usage_log "
                "WHERE timestamp_ms >= ? AND timestamp_ms < ?",
                (a_ms, b_ms),
            ).fetchone()
            return float(r[0] or 0.0)

        history = []
        prev_end = now  # rows 는 최신순 — 각 충전의 구간 = [이 충전, 다음(더 최신) 충전)
        for r in rows:
            used_krw = _usage_between(r["recharged_at_ms"], prev_end)
            history.append({
                "id": r["id"],
                "amountUsd": round(r["amount_usd"], 2),
                "note": r["note"],
                "rechargedAtMs": r["recharged_at_ms"],
                "usedSinceKrw": round(used_krw),
                "usedSinceUsd": round(used_krw / KRW_PER_USD, 2),
            })
            prev_end = r["recharged_at_ms"]

    if not history:
        return {"ok": True, "last": None, "history": []}

    last = history[0]
    remaining = round(last["amountUsd"] - last["usedSinceUsd"], 2)
    used_pct = (
        round(min(100.0, last["usedSinceUsd"] / last["amountUsd"] * 100), 1)
        if last["amountUsd"] > 0 else 0.0
    )
    return {
        "ok": True,
        "last": {
            **last,
            "remainingEstUsd": remaining,
            "usedPct": used_pct,
        },
        "history": history,
        "krwPerUsd": KRW_PER_USD,
    }


# ============================================================================
# 추가93 — 🩺 시스템 에러 추적 미들웨어
# 5xx 응답·미처리 예외를 system_errors 에 기록 → 대시보드 건강 카드가 보여줌.
# 사장님이 stderr.log 를 열지 않아도 "서버 아픈지"를 대시보드에서 알 수 있게.
# ============================================================================

def _record_system_error(path: str, status: int, detail: str) -> None:
    """에러 기록 — 절대 요청을 죽이지 않음 (기록 실패는 조용히 무시)."""
    try:
        now = _now_ms()
        with db_conn() as con:
            con.execute(
                "INSERT INTO system_errors (ts_ms, path, status, detail) VALUES (?, ?, ?, ?)",
                (now, (path or "")[:120], status, (detail or "")[:300]),
            )
            # 7일 지난 기록 정리 (테이블 비대 방지)
            con.execute("DELETE FROM system_errors WHERE ts_ms < ?",
                        (now - 7 * 86_400_000,))
            con.commit()
    except Exception:
        pass


@app.middleware("http")
async def _error_tracking_middleware(request: Request, call_next):
    try:
        response = await call_next(request)
    except Exception as e:
        _record_system_error(request.url.path, 500, f"{type(e).__name__}: {e}")
        raise
    if response.status_code >= 500:
        _record_system_error(request.url.path, response.status_code, "")
    return response


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
# §22 — /admin/beta/intake (HOU-128 통합, chief 리환 작업물 어댑테이션)
# ─────────────────────────────────────────────────────────────────────────────
# Chief 가 만든 10 카테고리 베타 운영 셋팅 폼 (목표/일정/시간/리소스/타겟/제외/
# 가격/개인정보/말투/결재). 사장님이 이 폼 채우고 제출하면 chief 가 깨어나
# Phase 0 (3명 내부 테스트) 시작 조건 충족.
#
# Chief 의 원본 (server/admin_beta_intake.py + RINGGO_ADMIN_TOKEN + ringgo.db)
# 을 우리 컨벤션에 맞춰 어댑테이션:
#   - cache.db (별도 ringgo.db X — db_init 의 §22 테이블 자동 생성)
#   - ADMIN_TOKEN env (plist 의 5302 — RINGGO_ADMIN_TOKEN X)
#   - main.py inline (routes/ 모듈 분리 X — 우리 모놀리식 컨벤션)
#
# 인증: Authorization: Bearer <ADMIN_TOKEN> (chief HTML 컨벤션 그대로 유지 —
# HTML 코드 verbatim. 우리 다른 admin endpoint 의 X-Admin-Token 패턴과 다르지만
# 이 화면 한정 — sessionStorage 클라이언트 측 토큰 보관 방식).
# ============================================================================

_BETA_INTAKE_HTML_PATH = BASE_DIR / "static" / "admin_beta_intake.html"


def _admin_auth_bearer_from_header(authorization: Optional[str]) -> None:
    """Authorization: Bearer <ADMIN_TOKEN> 검증 (chief HTML 컨벤션)."""
    if not ADMIN_TOKEN:
        raise HTTPException(
            status_code=503,
            detail="ADMIN_TOKEN 미설정. plist EnvironmentVariables 에 박아주세요.",
        )
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=401, detail="Authorization: Bearer <token> 헤더 필요"
        )
    token = authorization[7:].strip()
    if token != ADMIN_TOKEN:
        raise HTTPException(status_code=403, detail="토큰 불일치")


@app.get(
    "/admin/beta/intake", response_class=HTMLResponse, include_in_schema=False
)
async def admin_beta_intake_page():
    """베타 운영 셋팅 폼 (HTML SPA). client-side 토큰 처리, 서버 인증 없음."""
    if not _BETA_INTAKE_HTML_PATH.exists():
        raise HTTPException(
            status_code=500,
            detail=(
                f"HTML not found at {_BETA_INTAKE_HTML_PATH}. "
                "server/static/admin_beta_intake.html 확인."
            ),
        )
    return _BETA_INTAKE_HTML_PATH.read_text(encoding="utf-8")


@app.get("/admin/beta/intake/data")
async def admin_beta_intake_get_data(
    authorization: Optional[str] = Header(default=None),
):
    """최신 revision 의 폼 데이터 반환 (Bearer 토큰 필요)."""
    _admin_auth_bearer_from_header(authorization)
    with sqlite3.connect(DB_PATH) as con:
        con.row_factory = sqlite3.Row
        row = con.execute(
            "SELECT * FROM beta_intake_responses ORDER BY revision DESC LIMIT 1"
        ).fetchone()
    if not row:
        return {"found": False}
    return {
        "found": True,
        "id": row["id"],
        "draft": row["draft"],
        "revision": row["revision"],
        "submitted_at": row["submitted_at"],
        "response_json": json.loads(row["response_json"]),
        "updated_at": row["updated_at"],
    }


class BetaIntakePayload(BaseModel):
    """베타 셋팅 폼 저장 페이로드. response_json 은 10 카테고리 전체 dict."""

    draft: int = 1  # 1 = auto-save / 0 = 명시 제출
    response_json: dict


@app.post("/admin/beta/intake")
async def admin_beta_intake_save(
    body: BetaIntakePayload,
    authorization: Optional[str] = Header(default=None),
):
    """폼 저장. 매 호출마다 새 revision 행 INSERT.
    draft=1 = auto-save / draft=0 = 명시 제출 (submitted_at 기록)."""
    _admin_auth_bearer_from_header(authorization)
    now = _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    submitted_at = now if body.draft == 0 else None
    with sqlite3.connect(DB_PATH) as con:
        last = con.execute(
            "SELECT revision FROM beta_intake_responses ORDER BY revision DESC LIMIT 1"
        ).fetchone()
        rev = (last[0] + 1) if last else 1
        con.execute(
            """
            INSERT INTO beta_intake_responses
              (submitted_at, draft, revision, response_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                submitted_at,
                body.draft,
                rev,
                json.dumps(body.response_json, ensure_ascii=False),
                now,
                now,
            ),
        )
        con.commit()
    print(f"[admin_beta_intake] saved rev={rev} draft={body.draft}")
    return {"ok": True, "revision": rev, "draft": body.draft, "saved_at": now}


# ============================================================================
# §23 — 베타 모집 랜딩페이지 + 신청 폼 (RING-GO 첫 공개 페이지)
# ─────────────────────────────────────────────────────────────────────────────
# api.si0in.kr/ 의 루트가 랜딩페이지 = 50명 베타 모집. 신청 폼은 5 항목
# (전화번호 / 업종 / 지역 / 한 달 문의 수 / 동의) + 자유 메모.
# 디자인 톤은 design-preview/ringgo-redesign.html (프로토) 1:1 — 라일락-블루
# 메인, 둥근 카드, "막내 비서" 브랜드, 친근 ~요체.
#
# 베타 정책 (가격/인원/기간) 은 사장님이 HOU-128 셋팅 폼 (§22) 에 채운 값으로
# 자동 갱신 — 일단 정적 placeholder (50명/4주/무료) 로 시작.
#
# 신청 저장: cache.db.beta_signups (phone PK = 중복 신청 차단). status=pending.
# 사장님이 /admin/beta/signups 에서 리스트 확인 + status 변경 (accept/reject).
# 추후: 선정 시 SOLAPI Zapier 로 사장님이 SMS 발송 (자동 발송 금지 — 항상 수동).
# ============================================================================

_LANDING_HTML_PATH = BASE_DIR / "static" / "landing.html"


class BetaSignupRequest(BaseModel):
    """랜딩페이지 신청 폼 페이로드. 모두 string (전화번호는 숫자 11자리, 하이픈 제거)."""

    phone: str
    industry: str
    region: str
    monthly_inquiries: str
    note: str = ""
    agreed: bool = False
    source: str = "landing/unknown"
    business_name: str = ""  # 추가57 — 업체명 (사장님이 admin 페이지에서 신청자 분간용)
    industry_detail: str = ""  # 추가82 — 업종 '기타' 선택 시 구체 업종 (예: 방수)


_VALID_INDUSTRIES = {"줄눈", "타일", "도배", "장판", "인테리어", "기타"}
_VALID_MONTHLY = {"0-10", "10-30", "30-60", "60-100", "100+"}


@app.get("/", response_class=HTMLResponse, include_in_schema=False)
async def landing_root():
    """api.si0in.kr/ → 랜딩페이지 (베타 모집).

    추후 si0in.kr 루트 도메인을 Cloudflare Tunnel 에 추가하면 그쪽도 이 endpoint 가 응답.
    """
    if not _LANDING_HTML_PATH.exists():
        raise HTTPException(
            status_code=500,
            detail=(
                f"landing.html not found at {_LANDING_HTML_PATH}. "
                "server/static/landing.html 확인."
            ),
        )
    return _inject_site_verify(_LANDING_HTML_PATH.read_text(encoding="utf-8"))


@app.get("/landing", response_class=HTMLResponse, include_in_schema=False)
async def landing_alias():
    """/landing alias — / 와 동일."""
    return await landing_root()


# ─── 추가104 — 마케팅 홈페이지 (사장님 2026-07-08 숙제) ───
# 검색 노출용 페이지 4종: 기능소개 / 요금제 / 블로그 / 업데이트 + SEO (sitemap, robots).
# 블로그 = 팁 글 → 기승전-시공막내 (pluuug 인사이트 벤치마킹). 파일 = static/home_*.html, blog_*.html.

_HOME_STATIC_PAGES = {
    "features": "home_features.html",
    "pricing": "home_pricing.html",
    "blog": "home_blog.html",
    "updates": "home_updates.html",
}
_BLOG_POST_PAGES = {
    "missed-call-cost": "blog_missed_call_cost.html",
    "estimate-text-mistakes": "blog_estimate_text_mistakes.html",
    "schedule-double-booking": "blog_schedule_double_booking.html",
}


def _serve_home_page(filename: str) -> HTMLResponse:
    path = BASE_DIR / "static" / filename
    if not path.exists():
        raise HTTPException(404, "페이지 준비 중입니다")
    return HTMLResponse(content=_inject_site_verify(path.read_text(encoding="utf-8")))


@app.get("/features", response_class=HTMLResponse, include_in_schema=False)
async def home_features():
    return _serve_home_page(_HOME_STATIC_PAGES["features"])


@app.get("/pricing", response_class=HTMLResponse, include_in_schema=False)
async def home_pricing():
    return _serve_home_page(_HOME_STATIC_PAGES["pricing"])


@app.get("/blog", response_class=HTMLResponse, include_in_schema=False)
async def home_blog():
    # 추가105 — DB 자동 발행분 + 초기 3편 합쳐 동적 렌더
    return HTMLResponse(content=_render_blog_index_html())


@app.get("/updates", response_class=HTMLResponse, include_in_schema=False)
async def home_updates():
    # 추가105 — app_updates 주 단위 자동 그룹을 상단에 주입
    return HTMLResponse(content=_render_updates_dynamic())


@app.get("/blog/{slug}", response_class=HTMLResponse, include_in_schema=False)
async def home_blog_post(slug: str):
    filename = _BLOG_POST_PAGES.get(slug)
    if filename:
        return _serve_home_page(filename)
    # 추가105 — 자동 발행분 (DB)
    _blog_db_init()
    with db_conn() as con:
        row = con.execute(
            "SELECT slug, title, description, category, body_html, thumb, created_at_ms, tags "
            "FROM blog_posts WHERE slug = ?", (slug,)).fetchone()
    if not row:
        raise HTTPException(404, "글을 찾을 수 없습니다")
    post = dict(zip(
        ("slug", "title", "description", "category", "body_html", "thumb", "created_at_ms", "tags"), row))
    return HTMLResponse(content=_render_blog_post_html(post))


# 추가109 — 정적 3편의 태그 (자동발행분은 DB tags)
_STATIC_POST_TAGS = {
    "missed-call-cost": ["부재중 문자", "시공 어플", "고객 응대", "자동 응답"],
    "estimate-text-mistakes": ["견적서 양식", "시공 견적", "부가세 별도", "시공 어플"],
    "schedule-double-booking": ["시공 캘린더", "공수 캘린더", "일정 관리", "시공 어플"],
}


def _all_tags_index() -> dict:
    """태그 slug → (표시명, [(post_slug,title,thumb,desc,cat)...]) 인덱스."""
    _blog_db_init()
    idx: dict = {}

    def _add(tag, post):
        s = _tag_slug(tag)
        if not s:
            return
        if s not in idx:
            idx[s] = {"name": tag.strip(), "posts": []}
        idx[s]["posts"].append(post)

    with db_conn() as con:
        rows = con.execute(
            "SELECT slug, title, thumb, description, category, tags, created_at_ms "
            "FROM blog_posts ORDER BY created_at_ms DESC").fetchall()
    for slug, title, thumb, desc, cat, tags, ms in rows:
        p = {"slug": slug, "title": title, "thumb": thumb or "/static/thumbs/default.png",
             "description": desc, "category": cat}
        for t in (tags or "").split(","):
            if t.strip():
                _add(t, p)
    for slug, meta in _BLOG_STATIC_META_MAP.items():
        p = {"slug": slug, "title": meta["title"], "thumb": meta["thumb"],
             "description": meta["description"], "category": meta["category"]}
        for t in _STATIC_POST_TAGS.get(slug, []):
            _add(t, p)
    return idx


@app.get("/tag/{tag_slug}", response_class=HTMLResponse, include_in_schema=False)
async def home_tag_page(tag_slug: str):
    import html as _html
    idx = _all_tags_index()
    entry = idx.get(tag_slug)
    if not entry:
        raise HTTPException(404, "해당 키워드의 글이 아직 없습니다")
    name = entry["name"]
    cards = "".join(
        f'<a class="post" href="/blog/{p["slug"]}">'
        f'<img src="{p["thumb"]}" alt="{_html.escape(p["title"])}" loading="lazy">'
        f'<div class="pbody"><span class="cat">{_html.escape(p["category"])}</span>'
        f'<h2>{_html.escape(p["title"])}</h2><p>{_html.escape(p["description"])}</p></div></a>'
        for p in entry["posts"])
    extra_css = """
  .hero{max-width:920px;margin:0 auto;padding:48px 18px 6px;}
  .hero p{color:var(--t2);font-size:14.5px;margin-top:8px;}
  .wrap{max-width:920px;margin:0 auto;padding:26px 18px 70px;display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:16px;align-items:start;}
  .post{display:block;background:#fff;border:1px solid var(--line);border-radius:18px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.04);transition:transform .15s,border-color .15s;}
  .post:hover{border-color:var(--blue);transform:translateY(-3px);}
  .post img{width:100%;height:auto;display:block;aspect-ratio:1200/630;object-fit:cover;}
  .post .pbody{padding:16px 18px 18px;}
  .post .cat{margin-bottom:8px;}.post h2{font-size:16.5px;margin:0;line-height:1.45;}
  .post p{font-size:13.2px;color:var(--t2);margin:8px 0 0;}
"""
    html = (
        '<!doctype html><html lang="ko"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">'
        f'<title>#{_html.escape(name)} 관련 글 — 시공막내 블로그</title>'
        f'<meta name="description" content="{_html.escape(name)} 관련 시공 사장님 실전 팁 모음. 시공막내 블로그.">'
        f'<link rel="canonical" href="{_HOME_BASE}/tag/{tag_slug}">'
        '<meta property="og:type" content="website">'
        f'<meta property="og:title" content="#{_html.escape(name)} — 시공막내 블로그">'
        f'<meta property="og:image" content="{_HOME_BASE}/static/thumbs/blog.png">'
        '<meta name="twitter:card" content="summary_large_image">'
        f'<meta name="twitter:image" content="{_HOME_BASE}/static/thumbs/blog.png">'
        '<link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css" rel="stylesheet">'
        f'<style>{_BLOG_SHELL_CSS}{extra_css}</style></head><body>'
        + _HOME_NAV +
        f'<div class="hero"><h1>#{_html.escape(name)}</h1>'
        f'<p>{_html.escape(name)} 관련 시공 사장님 실전 글 모음이에요.</p></div>'
        f'<div class="wrap">{cards}</div>' + _HOME_FOOTER + '</body></html>'
    )
    return HTMLResponse(content=_inject_site_verify(html))


@app.get("/sitemap.xml", include_in_schema=False)
async def sitemap_xml():
    base = "https://si0in.kr"
    urls = ["/", "/features", "/pricing", "/blog", "/updates", "/terms", "/privacy"]
    urls += _KEYWORD_LANDING_URLS + _TOOL_URLS  # 추가110·111
    urls += [f"/blog/{s}" for s in _BLOG_POST_PAGES]
    # 추가105 — 자동 발행분 포함 + 추가109 태그 페이지
    try:
        _blog_db_init()
        with db_conn() as con:
            urls += [f"/blog/{r[0]}" for r in con.execute("SELECT slug FROM blog_posts").fetchall()]
        urls += [f"/tag/{s}" for s in _all_tags_index().keys()]
    except Exception:
        pass
    body = "".join(
        f"<url><loc>{base}{u}</loc><changefreq>weekly</changefreq></url>" for u in urls
    )
    xml = (
        '<?xml version="1.0" encoding="UTF-8"?>'
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">' + body + "</urlset>"
    )
    return Response(content=xml, media_type="application/xml")


@app.get("/robots.txt", include_in_schema=False)
async def robots_txt():
    return Response(
        content=(
            "User-agent: *\n"
            "Allow: /\n"
            "Disallow: /admin\n"
            "Disallow: /api/\n"
            "Disallow: /q/\n"
            "Disallow: /intake/\n"
            "Sitemap: https://si0in.kr/sitemap.xml\n"
        ),
        media_type="text/plain",
    )


# ============================================================================
# 추가110 — 키워드 직격 랜딩 + 추가111 — 무료 계산기 도구
# ============================================================================
_KEYWORD_LANDINGS = {
    "시공어플": "kw_construction_app.html",
    "공사어플": "kw_construction_app2.html",
    "시공캘린더": "kw_construction_calendar.html",
    "공수캘린더": "kw_manday_calendar.html",
}
_KEYWORD_LANDING_URLS = [f"/{k}" for k in _KEYWORD_LANDINGS]

_TOOLS = {
    "tools": "tool_index.html",
    "manday": "tool_manday.html",       # 공수 계산기
    "vat": "tool_vat.html",             # 부가세 계산기
    "daywage": "tool_daywage.html",     # 일당 정산 계산기
}
_TOOL_URLS = ["/tools", "/tools/manday", "/tools/vat", "/tools/daywage"]


@app.get("/시공어플", response_class=HTMLResponse, include_in_schema=False)
async def kw_1(): return _serve_home_page(_KEYWORD_LANDINGS["시공어플"])

@app.get("/공사어플", response_class=HTMLResponse, include_in_schema=False)
async def kw_2(): return _serve_home_page(_KEYWORD_LANDINGS["공사어플"])

@app.get("/시공캘린더", response_class=HTMLResponse, include_in_schema=False)
async def kw_3(): return _serve_home_page(_KEYWORD_LANDINGS["시공캘린더"])

@app.get("/공수캘린더", response_class=HTMLResponse, include_in_schema=False)
async def kw_4(): return _serve_home_page(_KEYWORD_LANDINGS["공수캘린더"])

@app.get("/tools", response_class=HTMLResponse, include_in_schema=False)
async def tool_index(): return _serve_home_page(_TOOLS["tools"])

@app.get("/tools/manday", response_class=HTMLResponse, include_in_schema=False)
async def tool_manday(): return _serve_home_page(_TOOLS["manday"])

@app.get("/tools/vat", response_class=HTMLResponse, include_in_schema=False)
async def tool_vat(): return _serve_home_page(_TOOLS["vat"])

@app.get("/tools/daywage", response_class=HTMLResponse, include_in_schema=False)
async def tool_daywage(): return _serve_home_page(_TOOLS["daywage"])


# ============================================================================
# 추가105 — 블로그 매일 자동 발행 + 대표 섬네일 + 업데이트 주간 정리 (사장님 2026-07-09)
# ─────────────────────────────────────────────────────────────────────────────
# ① 매일 07:30 KST 주제 큐에서 1개 → Claude 로 글 생성 → blog_posts 저장 (+섬네일 PNG)
#    env BLOG_AUTOPUBLISH=0 으로 끌 수 있음 (기본 ON). 수동: POST /api/admin/blog/generate
# ② 섬네일: Pillow 로 1200×630 브랜드 이미지 (제목 텍스트 자동 줄바꿈·축소).
#    Pillow 없으면 default.png fallback. (deploy 스크립트가 pillow 설치 보장)
# ③ /updates = app_updates 테이블을 주 단위 자동 그룹 렌더 (이번 주 항목이 쌓이면
#    자동으로 "N째 주" 블록이 됨). 등록: POST /api/admin/updates/entry (cowork 가 배포마다)
# ============================================================================

_HOME_BASE = "https://si0in.kr"
_BLOG_AUTOPUBLISH = os.environ.get("BLOG_AUTOPUBLISH", "1") == "1"
_THUMBS_DIR = BASE_DIR / "static" / "thumbs"

# 추가108 — 검색엔진 소유확인 (env 로 값만 넣으면 <head> 에 meta 자동 삽입)
_NAVER_SITE_VERIFY = os.environ.get("NAVER_SITE_VERIFY", "").strip()
_GOOGLE_SITE_VERIFY = os.environ.get("GOOGLE_SITE_VERIFY", "").strip()
# 추가114 — 방문자 분석 (env 로 ID 만 넣으면 전 페이지에 자동 삽입)
_GA4_ID = os.environ.get("GA4_MEASUREMENT_ID", "").strip()          # 예: G-XXXXXXX
_NAVER_ANALYTICS_ID = os.environ.get("NAVER_ANALYTICS_ID", "").strip()  # 네이버 애널리틱스 wa 값

# 추가114 — 사이트 공통 구조화 데이터 (검색결과 로고·사이트링크 노출용). 항상 삽입.
_SITE_JSONLD = (
    '<script type="application/ld+json">'
    '{"@context":"https://schema.org","@type":"Organization",'
    '"name":"시공막내","alternateName":"막내컴퍼니","url":"https://si0in.kr",'
    '"logo":"https://si0in.kr/static/thumbs/default.png",'
    '"description":"시공 사장님을 위한 전화·문자·견적·일정·정산 올인원 비서 앱",'
    '"contactPoint":{"@type":"ContactPoint","email":"hello@si0in.kr","contactType":"customer support"}}'
    '</script>'
    '<script type="application/ld+json">'
    '{"@context":"https://schema.org","@type":"WebSite","name":"시공막내",'
    '"url":"https://si0in.kr"}'
    '</script>'
)


def _analytics_snippet() -> str:
    s = ""
    if _GA4_ID:
        s += (f'<script async src="https://www.googletagmanager.com/gtag/js?id={_GA4_ID}"></script>'
              '<script>window.dataLayer=window.dataLayer||[];function gtag(){dataLayer.push(arguments);}'
              f"gtag('js',new Date());gtag('config','{_GA4_ID}');</script>")
    if _NAVER_ANALYTICS_ID:
        s += ('<script src="//wcs.naver.net/wcslog.js"></script>'
              '<script>if(!window.wcs_add)var wcs_add={};'
              f'wcs_add["wa"]="{_NAVER_ANALYTICS_ID}";'
              'if(window.wcs){window.wcs_do();}</script>')
    return s


def _inject_site_verify(html: str) -> str:
    """홈/정적 페이지 <head> 에 소유확인 meta + 구조화데이터 + 방문자분석 삽입.

    (함수명은 추가108 호환 유지 — 실제로는 head 공통 주입기 역할)
    """
    if "</head>" not in html:
        return html
    tags = ""
    if _NAVER_SITE_VERIFY:
        tags += f'<meta name="naver-site-verification" content="{_NAVER_SITE_VERIFY}">'
    if _GOOGLE_SITE_VERIFY:
        tags += f'<meta name="google-site-verification" content="{_GOOGLE_SITE_VERIFY}">'
    tags += _SITE_JSONLD              # 항상
    tags += _analytics_snippet()     # env 있을 때만
    return html.replace("</head>", tags + "</head>", 1)

# 주제 큐 — (slug, 주제/각도, 검색 키워드, 연결할 기능)
_BLOG_TOPIC_QUEUE: list[tuple[str, str, str, str]] = [
    ("deposit-no-show", "계약금 없이 날짜부터 잡아주면 생기는 노쇼 문제와 계약금 제안 화법", "시공 계약금 노쇼", "접수서 계약금 표시"),
    ("as-call-regulars", "시공 끝나고 3개월 뒤 AS 안부 문자가 단골을 만드는 이유", "인테리어 AS 관리 단골", "정기 문자 예약"),
    ("photo-before-after", "시공 전후 사진을 남기는 사장님이 분쟁에서 이기는 이유", "시공 전후 사진 분쟁", "협업 증거사진"),
    ("kakao-vs-sms", "고객 응대, 카톡으로 할까 문자로 할까 — 시공업의 정답", "시공 고객 카톡 문자", "추천 답장"),
    ("unpaid-balance", "잔금 못 받는 사장님의 공통점 3가지", "시공 잔금 미수금", "수금·정산 관리"),
    ("estimate-visit-fee", "실측 방문비, 받아야 하나 말아야 하나", "실측 방문비 견적", "접수서 링크"),
    ("winter-offseason", "비수기에 일감 만드는 사장님들의 문자 습관", "시공 비수기 영업", "정기 문자 예약"),
    ("review-request", "시공 끝나고 리뷰 부탁, 언제 어떻게 말해야 자연스러울까", "인테리어 리뷰 요청 문자", "추천 답장"),
    ("collab-day-rate", "일당 사장님과 협업할 때 돈 문제 안 생기게 하는 법", "시공 일당 정산 협업", "협업 현장 공유"),
    ("phone-only-business", "명함도 사무실도 없이 전화 하나로 연 매출 올리는 구조", "1인 시공업 운영", "고객 카드·통화 요약"),
    ("customer-memo", "고객마다 '현관 비번·주차·특이사항'을 기억하는 법", "시공 고객 관리 메모", "특이사항 메모"),
    ("double-quote", "같은 고객에게 견적을 두 번 보내게 될 때 실수 안 하는 법", "견적 재발행", "접수서 링크 갱신"),
    ("morning-brief", "하루 5분, 아침에 이것만 확인하면 펑크가 없다", "시공 일정 아침 루틴", "일정·하루 전 안내"),
    ("voice-memo-dead", "운전 중에 들은 고객 요청, 저녁까지 살아남게 하는 법", "통화 메모 요약", "AI 통화 요약"),
    ("price-haggle", "\"좀 깎아주세요\"에 손해 안 보고 답하는 문자 화법", "시공 가격 흥정 대응", "추천 답장"),
    ("no-show-day", "고객이 시공일에 집에 없을 때 — 헛걸음 막는 전날 확인 절차", "시공 헛걸음 방지", "하루 전 안내 문자"),
    ("tax-invoice-basic", "개인사업자 시공 사장님의 부가세 기초 — 견적서에 별도/포함부터", "시공 부가세 별도 포함", "견적서 부가세 표기"),
    ("referral-engine", "소개가 소개를 부르는 구조 만들기 — 시공 후 30일 플랜", "인테리어 소개 영업", "정기 문자 예약"),
    ("busy-season-triage", "성수기에 문의가 몰릴 때, 어떤 고객부터 잡아야 하나", "시공 문의 우선순위", "고객 카드·발행 이력"),
    ("estimate-speed", "견적은 빠른 놈이 이긴다 — 첫 응답 30분의 법칙", "견적 빨리 보내는 법", "직인 견적서"),
    ("old-customer-db", "폰에 잠든 옛 고객 연락처가 사실 금광인 이유", "기존 고객 재영업", "고객 타임라인"),
    ("dispute-evidence", "말싸움이 아니라 기록싸움 — 분쟁에서 이기는 문서 3종", "시공 분쟁 대응", "접수서 영수증"),
    ("family-help-out", "사모님이 전화 받아주던 시절을 졸업하는 법", "부부 시공업 전화 응대", "부재중 자동 응대"),
    ("first-impression-sms", "첫 문자가 견적보다 중요하다 — 신규 문의 첫 응대 문장", "시공 첫 문의 응대", "추천 답장"),
    ("area-expand", "옆 동네까지 상권 넓힐 때 문자로 미리 씨 뿌리는 법", "시공 상권 확장", "정기 문자 예약"),
    ("subcontract-clear", "하도급 일 받을 때 돈 떼이지 않는 최소 장치", "하도급 대금 정산", "협업 정산"),
    ("app-vs-paper", "수첩 사장님이 앱 사장님에게 밀리는 진짜 이유", "시공 고객 관리 앱", "발행 이력"),
    ("night-message", "밤 9시 넘어 온 문의, 바로 답해도 될까 — 야간 응대 원칙", "야간 고객 문자", "부재중 자동 응대"),
    ("season-checkup", "장마 전 점검 문자 한 통이 만드는 7월 매출", "장마 방수 점검 영업", "정기 문자 예약"),
    ("quote-followup", "견적 보내고 답 없는 고객, 며칠 뒤에 어떻게 찔러볼까", "견적 후 팔로업 문자", "추천 답장"),
]

_BLOG_CATEGORIES = ["고객 응대", "견적", "일정 관리", "수금·정산", "영업·단골"]

# ── 공통 셸 (토큰 치환 방식 — CSS 중괄호 이슈 회피) ──
_BLOG_SHELL_CSS = """
  :root{--blue:#3182F6;--blue-dark:#1B64DA;--blue-tint:#EEF4FF;--bg:#FAFBFC;--t1:#0B0F19;--t2:#5A6472;--t3:#9AA3AF;--line:#EEF0F3;}
  *{box-sizing:border-box;margin:0;padding:0;}
  body{font-family:'Pretendard',-apple-system,system-ui,sans-serif;background:var(--bg);color:var(--t1);line-height:1.8;}
  a{text-decoration:none;color:inherit;}
  .nav{position:sticky;top:0;background:rgba(255,255,255,.92);backdrop-filter:blur(8px);border-bottom:1px solid var(--line);z-index:10;}
  .nav-in{max-width:920px;margin:0 auto;display:flex;align-items:center;gap:18px;padding:13px 18px;font-size:14px;}
  .nav-logo{font-weight:900;font-size:16px;letter-spacing:-.04em;margin-right:auto;}
  .nav-logo .dot{display:inline-block;width:8px;height:8px;border-radius:50%;background:var(--blue);margin-right:6px;vertical-align:middle;}
  .nav a.item{color:var(--t2);font-weight:600;}
  .nav a.item.on{color:var(--blue-dark);font-weight:800;}
  .nav a.cta{background:var(--blue);color:#fff;font-weight:800;padding:8px 14px;border-radius:9px;font-size:13.5px;}
  .art{max-width:680px;margin:0 auto;padding:46px 18px 70px;}
  .cat{font-size:12px;font-weight:800;color:var(--blue-dark);background:var(--blue-tint);border-radius:6px;padding:3px 9px;display:inline-block;margin-bottom:12px;}
  h1{font-size:26px;font-weight:900;letter-spacing:-.03em;line-height:1.45;}
  .meta{font-size:12.5px;color:var(--t3);margin:12px 0 30px;}
  h2{font-size:19px;font-weight:900;margin:36px 0 10px;letter-spacing:-.02em;}
  p{font-size:15px;color:#333D4B;margin-bottom:14px;}
  p b{color:var(--t1);}
  .box{background:#fff;border:1px solid var(--line);border-radius:14px;padding:18px 20px;margin:18px 0;font-size:14.5px;color:var(--t2);}
  .box b{color:var(--t1);}
  ul{margin:0 0 14px 20px;}
  li{font-size:15px;color:#333D4B;margin-bottom:8px;}
  .cta-band{margin-top:44px;background:linear-gradient(135deg,var(--blue),var(--blue-dark));border-radius:18px;padding:30px 22px;text-align:center;color:#fff;}
  .cta-band h2{font-size:19px;font-weight:900;margin:0 0 8px;}
  .cta-band p{font-size:13.5px;opacity:.9;color:#fff;margin-bottom:16px;}
  .cta-band a{display:inline-block;background:#fff;color:var(--blue-dark);font-weight:900;padding:12px 24px;border-radius:11px;font-size:14.5px;}
  footer{padding:32px 18px 60px;text-align:center;color:var(--t3);font-size:11.5px;line-height:1.7;background:#fff;border-top:1px solid var(--line);margin-top:50px;}
  footer a{color:var(--t3);text-decoration:underline;}
  @media(max-width:560px){.nav-in{gap:11px;overflow-x:auto;-webkit-overflow-scrolling:touch;}.nav-logo{margin-right:6px;}.nav a.item{font-size:13px;}.nav a.cta{padding:7px 10px;font-size:12px;white-space:nowrap;} h1{font-size:22px;}}
"""

_HOME_NAV = """<nav class="nav"><div class="nav-in">
  <a class="nav-logo" href="/"><span class="dot"></span>시공막내</a>
  <a class="item" href="/features">기능</a>
  <a class="item" href="/pricing">요금제</a>
  <a class="item on" href="/blog">블로그</a>
  <a class="item" href="/tools">계산기</a>
  <a class="item" href="/updates">업데이트</a>
  <a class="cta" href="/">무료로 시작하기</a>
</div></nav>"""

_HOME_FOOTER = """<footer>
  <div>상호: 막내컴퍼니 | 대표: 김상훈 | 사업자등록번호: 454-07-03372<br>
  주소: 경기도 화성시 동탄지성로 11, 동탄에스알골드프라자 7층 714-D128호 | 전화: 010-8005-6674</div>
  <div style="margin-top:6px;"><a href="/terms">이용약관</a> · <a href="/privacy">개인정보 처리방침</a> · <a href="/blog">블로그 홈</a></div>
  <div style="margin-top:6px;">© 2026 막내컴퍼니 · 시공막내</div>
</footer>"""


def _blog_db_init() -> None:
    with db_conn() as con:
        con.execute("""
            CREATE TABLE IF NOT EXISTS blog_posts (
                slug          TEXT PRIMARY KEY,
                title         TEXT NOT NULL,
                description   TEXT NOT NULL,
                category      TEXT NOT NULL,
                body_html     TEXT NOT NULL,
                thumb         TEXT,
                created_at_ms INTEGER NOT NULL
            )""")
        try:
            con.execute("ALTER TABLE blog_posts ADD COLUMN tags TEXT")  # 추가109 — 해시태그(콤마 구분)
        except sqlite3.OperationalError:
            pass
        con.execute("""
            CREATE TABLE IF NOT EXISTS app_updates (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                kind          TEXT NOT NULL DEFAULT 'new',   -- new|fix|imp
                text          TEXT NOT NULL,
                created_at_ms INTEGER NOT NULL
            )""")
        con.commit()


def _kst_now() -> "_dt.datetime":
    return _dt.datetime.utcnow() + _dt.timedelta(hours=9)


def _make_blog_thumb(title: str, category: str, slug: str) -> str:
    """1200×630 대표 섬네일 PNG 생성. 실패 시 default.png fallback."""
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError:
        print("[blog/thumb] Pillow 없음 → default.png fallback")
        return "/static/thumbs/default.png"
    try:
        W, H = 1200, 630
        font_candidates = [
            "/System/Library/Fonts/AppleSDGothicNeo.ttc",              # macOS
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",     # linux
            "/Library/Fonts/AppleGothic.ttf",
        ]
        fpath = next((p for p in font_candidates if Path(p).exists()), None)
        if not fpath:
            return "/static/thumbs/default.png"

        img = Image.new("RGB", (W, H))
        d = ImageDraw.Draw(img)
        c1, c2 = (49, 130, 246), (23, 88, 196)
        for y in range(H):
            t = y / H
            d.line([(0, y), (W, y)], fill=(
                int(c1[0] + (c2[0] - c1[0]) * t),
                int(c1[1] + (c2[1] - c1[1]) * t),
                int(c1[2] + (c2[2] - c1[2]) * t)))
        ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        od = ImageDraw.Draw(ov)
        od.ellipse([880, -160, 1440, 400], fill=(255, 255, 255, 20))
        od.ellipse([980, 60, 1380, 460], fill=(255, 255, 255, 15))
        img = img.convert("RGBA")
        img.alpha_composite(ov)
        d = ImageDraw.Draw(img)

        def _font(sz):
            return ImageFont.truetype(fpath, sz)

        def _wrap(text, font, maxw):
            lines = []
            for para in text.split("\n"):
                line = ""
                for word in para.split(" "):
                    t = (line + " " + word).strip()
                    if d.textlength(t, font=font) > maxw and line:
                        lines.append(line)
                        line = word
                    else:
                        line = t
                lines.append(line)
            return lines

        d.ellipse([70, 64, 102, 96], fill="white")
        d.text((118, 50), "시공막내", font=_font(44), fill="white")
        top = 170
        if category:
            f = _font(30)
            tw = d.textlength(category, font=f)
            d.rounded_rectangle([70, top, 70 + tw + 48, top + 58], radius=29, fill="white")
            d.text((94, top + 9), category, font=f, fill=(27, 100, 218))
            top += 92
        ts = 84
        lines = [title]
        f = _font(ts)
        while ts >= 50:
            f = _font(ts)
            lines = _wrap(title, f, 1040)
            if top + len(lines) * int(ts * 1.3) <= H - 130 and len(lines) <= 3:
                break
            ts -= 6
        y = top + 6
        for ln in lines[:3]:
            d.text((70, y), ln, font=f, fill="white")
            y += int(ts * 1.3)
        d.text((70, H - 80), "시공 사장님의 막내 비서 · si0in.kr",
               font=ImageFont.truetype(fpath, 30), fill=(255, 255, 255))
        _THUMBS_DIR.mkdir(parents=True, exist_ok=True)
        out = _THUMBS_DIR / f"post-{slug}.png"
        img.convert("RGB").save(out, "PNG", optimize=True)
        return f"/static/thumbs/post-{slug}.png"
    except Exception as e:
        print(f"[blog/thumb] 생성 실패 ({e}) → default.png")
        return "/static/thumbs/default.png"


_BLOG_WRITE_SYSTEM = """너는 '시공막내' 공식 블로그의 전속 작가다. 독자는 혼자 일하는 한국의 시공·인테리어 사장님 (40~60대, 현장직, 존댓말 선호).

시공막내 = 시공 사장님의 막내 비서 앱. 기능: 전화 오면 고객 카드, 부재중 자동 문자, AI 통화 요약, AI 추천 답장(말투 학습), 직인 견적서 이미지(부가세 별도/포함 명시), 시공접수서 링크(고객이 작성→일정 자동 등록→읽기전용 영수증), 시공 하루 전 자동 안내 문자, 계약금·잔금 정산 관리, 협업 현장 공유(도착·증거사진·일당 정산), 정기 문자 예약. 60일 무료.

글 스타일 (반드시):
- 도입: 사장님이 겪는 구체적 장면으로 공감 후킹 (2~3문단)
- 본론: 진짜 유용한 팁 2~4개 (h2 소제목) — 팁 자체로 가치가 있어야 함
- 마무리: "이걸 손으로 다 하기 어렵다" → 자연스럽게 시공막내의 해당 기능 연결 (기승전-시공막내). 과장 광고 금지, 담백하게.
- 숫자·계산 예시를 넣으면 좋음. 감탄사·이모지 남발 금지.
- 허용 HTML: <h2> <p> <ul> <li> <b> <div class="box">(강조 박스). 다른 태그 금지.
- 이해를 돕는 그림: 독자가 글만으로 이해하기 어려운 대목마다 <figure data-fig="키">한 줄 캡션</figure> 을 본문 중간에 2~3개 배치 (서버가 그림으로 바꿔줌). 키 종류:
  calc(돈 계산·손실 크기) / flow(일 처리 3단계 흐름) / compare(나쁜 방식 vs 좋은 방식) / phone(고객과 문자 주고받기) / calendar(일정·날짜 겹침) / money(수금·정산 현황). 내용에 맞는 키만 사용.

출력은 반드시 JSON 하나:
{"title": "후킹형 제목 (35자 이내)", "description": "검색 결과에 보일 요약 (100자 내외, 키워드 포함)", "category": "고객 응대|견적|일정 관리|수금·정산|영업·단골 중 하나", "tags": ["시공 사장님이 검색할 법한 키워드 4~6개 (예: 시공 어플, 공수 계산, 견적서 양식, 부재중 문자, 인테리어 단골). # 없이 단어만"], "body_html": "<p>...</p><h2>...</h2>... (본문 전체, 1200~1800자, CTA 밴드는 넣지 말 것 — 서버가 붙임)"}"""


def _render_blog_post_html(post: dict) -> str:
    created = _dt.datetime.utcfromtimestamp(post["created_at_ms"] / 1000) + _dt.timedelta(hours=9)
    date_label = f"{created.year}. {created.month}. {created.day}"
    thumb_url = _HOME_BASE + (post.get("thumb") or "/static/thumbs/default.png")
    url = f"{_HOME_BASE}/blog/{post['slug']}"
    import html as _html
    title_esc = _html.escape(post["title"])
    desc_esc = _html.escape(post["description"])
    ld = json.dumps({
        "@context": "https://schema.org", "@type": "BlogPosting",
        "headline": post["title"], "datePublished": created.strftime("%Y-%m-%d"),
        "dateModified": created.strftime("%Y-%m-%d"),
        "author": {"@type": "Organization", "name": "시공막내"},
        "publisher": {"@type": "Organization", "name": "막내컴퍼니"},
        "image": thumb_url, "mainEntityOfPage": url,
    }, ensure_ascii=False)
    return (
        '<!doctype html><html lang="ko"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">'
        f'<title>{title_esc} — 시공막내 블로그</title>'
        f'<meta name="description" content="{desc_esc}">'
        f'<link rel="canonical" href="{url}">'
        '<meta property="og:type" content="article">'
        f'<meta property="og:title" content="{title_esc}">'
        f'<meta property="og:description" content="{desc_esc}">'
        f'<meta property="og:url" content="{url}">'
        '<meta property="og:site_name" content="시공막내">'
        f'<meta property="og:image" content="{thumb_url}">'
        '<meta property="og:image:width" content="1200"><meta property="og:image:height" content="630">'
        '<meta name="twitter:card" content="summary_large_image">'
        f'<meta name="twitter:title" content="{title_esc}">'
        f'<meta name="twitter:image" content="{thumb_url}">'
        f'<script type="application/ld+json">{ld}</script>'
        '<link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css" rel="stylesheet">'
        f'<style>{_BLOG_SHELL_CSS}</style></head><body>'
        + _HOME_NAV +
        f'<article class="art"><span class="cat">{_html.escape(post["category"])}</span>'
        f'<h1>{title_esc}</h1>'
        f'<div class="meta">시공막내 · {date_label}</div>'
        f'<img src="{post.get("thumb") or "/static/thumbs/default.png"}" alt="{title_esc}" '
        'style="width:100%;height:auto;border-radius:16px;margin-bottom:26px;'
        'box-shadow:0 6px 18px rgba(27,100,218,.18)">'
        + post["body_html"] +
        _render_tag_chips(post.get("tags")) +
        '<div class="cta-band"><h2>이 모든 걸, 막내가 대신합니다</h2>'
        '<p>60일 무료 · 카드 등록 없음 · 전화번호 인증이면 끝</p>'
        '<a href="/">시공막내 무료로 시작하기 →</a></div>'
        '<div style="margin-top:34px;font-size:14px;"><a href="/blog" style="color:var(--blue-dark);font-weight:700;">← 블로그 목록으로</a></div>'
        '</article>' + _HOME_FOOTER + '</body></html>'
    )


def _tag_slug(tag: str) -> str:
    """태그 → URL slug (한글 유지, 공백→-, 소문자)."""
    import re as _re
    s = tag.strip().lower().replace(" ", "-")
    return _re.sub(r"[^0-9a-z가-힣\-]", "", s)


def _render_tag_chips(tags) -> str:
    import html as _html
    if not tags:
        return ""
    items = (tags.split(",") if isinstance(tags, str) else tags)
    chips = "".join(
        f'<a href="/tag/{_tag_slug(t)}" style="display:inline-block;font-size:13px;'
        f'font-weight:700;color:var(--blue-dark);background:var(--blue-tint);'
        f'border-radius:999px;padding:7px 14px;margin:4px 6px 4px 0;text-decoration:none">'
        f'#{_html.escape(t.strip())}</a>'
        for t in items if t.strip())
    if not chips:
        return ""
    return ('<div style="margin-top:36px;padding-top:20px;border-top:1px solid var(--line)">'
            '<div style="font-size:13px;font-weight:800;color:var(--t3);margin-bottom:8px">관련 키워드</div>'
            + chips + '</div>')


# ── 추가106 — 본문 중간 이해용 일러스트 세트 ──
# Claude 가 <figure data-fig="키">캡션</figure> 로 위치만 고르면 서버가 SVG 로 치환.
_FIG_SVG_COMMON = 'xmlns="http://www.w3.org/2000/svg" style="width:100%;height:auto;display:block"'
_FIG_SVGS: dict[str, str] = {
    # 돈 계산 인포그래픽 (놓친 매출/미수금 등)
    "calc": f'''<svg viewBox="0 0 640 210" {_FIG_SVG_COMMON} role="img">
<rect width="640" height="210" rx="16" fill="#EEF4FF"/>
<rect x="24" y="30" width="150" height="150" rx="14" fill="#fff"/><text x="99" y="86" font-size="34" text-anchor="middle">📵</text><text x="99" y="122" font-size="14" text-anchor="middle" font-weight="800" fill="#0B0F19">놓친 전화</text><text x="99" y="146" font-size="12" text-anchor="middle" fill="#5A6472">월 10통</text>
<text x="205" y="112" font-size="26" text-anchor="middle" fill="#1B64DA" font-weight="900">×</text>
<rect x="236" y="30" width="150" height="150" rx="14" fill="#fff"/><text x="311" y="86" font-size="34" text-anchor="middle">🤝</text><text x="311" y="122" font-size="14" text-anchor="middle" font-weight="800" fill="#0B0F19">계약 전환</text><text x="311" y="146" font-size="12" text-anchor="middle" fill="#5A6472">약 30%</text>
<text x="417" y="112" font-size="26" text-anchor="middle" fill="#1B64DA" font-weight="900">=</text>
<rect x="448" y="30" width="168" height="150" rx="14" fill="#1B64DA"/><text x="532" y="86" font-size="34" text-anchor="middle">💸</text><text x="532" y="122" font-size="15" text-anchor="middle" font-weight="900" fill="#fff">증발한 매출</text><text x="532" y="146" font-size="12" text-anchor="middle" fill="#CFE0FF">매달 수백만 원</text></svg>''',
    # 3단계 흐름
    "flow": f'''<svg viewBox="0 0 640 190" {_FIG_SVG_COMMON} role="img">
<rect width="640" height="190" rx="16" fill="#F4F5F7"/>
<rect x="26" y="46" width="170" height="98" rx="14" fill="#fff"/><text x="111" y="88" font-size="28" text-anchor="middle">📞</text><text x="111" y="120" font-size="13" text-anchor="middle" font-weight="800" fill="#0B0F19">일이 생기는 순간</text>
<path d="M206 95 h26 m-8 -7 l8 7 l-8 7" stroke="#3182F6" stroke-width="3" fill="none" stroke-linecap="round"/>
<rect x="242" y="46" width="170" height="98" rx="14" fill="#fff"/><text x="327" y="88" font-size="28" text-anchor="middle">📝</text><text x="327" y="120" font-size="13" text-anchor="middle" font-weight="800" fill="#0B0F19">그 자리에서 기록</text>
<path d="M422 95 h26 m-8 -7 l8 7 l-8 7" stroke="#3182F6" stroke-width="3" fill="none" stroke-linecap="round"/>
<rect x="458" y="46" width="156" height="98" rx="14" fill="#1B64DA"/><text x="536" y="88" font-size="28" text-anchor="middle">✅</text><text x="536" y="120" font-size="13" text-anchor="middle" font-weight="900" fill="#fff">자동으로 처리</text></svg>''',
    # 나쁜 예 vs 좋은 예
    "compare": f'''<svg viewBox="0 0 640 230" {_FIG_SVG_COMMON} role="img">
<rect width="640" height="230" rx="16" fill="#F4F5F7"/>
<rect x="24" y="26" width="284" height="178" rx="14" fill="#fff"/><rect x="24" y="26" width="284" height="6" rx="3" fill="#F0436A"/>
<text x="166" y="66" font-size="15" text-anchor="middle" font-weight="900" fill="#F0436A">❌ 이렇게 하면</text>
<rect x="48" y="86" width="236" height="34" rx="10" fill="#FDE8ED"/><text x="166" y="108" font-size="12.5" text-anchor="middle" fill="#B01E45">말로만 · 기억에 의존 · 기록 없음</text>
<text x="166" y="152" font-size="12.5" text-anchor="middle" fill="#5A6472">"그때 그렇게 말씀하셨잖아요"</text>
<text x="166" y="176" font-size="12.5" text-anchor="middle" fill="#5A6472">분쟁 · 재작업 · 신뢰 하락</text>
<rect x="332" y="26" width="284" height="178" rx="14" fill="#fff"/><rect x="332" y="26" width="284" height="6" rx="3" fill="#16C172"/>
<text x="474" y="66" font-size="15" text-anchor="middle" font-weight="900" fill="#0B7A45">⭕ 이렇게 하면</text>
<rect x="356" y="86" width="236" height="34" rx="10" fill="#E7F8EF"/><text x="474" y="108" font-size="12.5" text-anchor="middle" fill="#0B7A45">문서로 · 자동 기록 · 언제든 확인</text>
<text x="474" y="152" font-size="12.5" text-anchor="middle" fill="#5A6472">"여기 적혀 있는 대로 진행할게요"</text>
<text x="474" y="176" font-size="12.5" text-anchor="middle" fill="#5A6472">깔끔한 마무리 · 소개로 이어짐</text></svg>''',
    # 폰 문자 주고받기
    "phone": f'''<svg viewBox="0 0 640 220" {_FIG_SVG_COMMON} role="img">
<rect width="640" height="220" rx="16" fill="#EEF4FF"/>
<rect x="150" y="24" width="340" height="172" rx="18" fill="#fff"/>
<rect x="174" y="48" width="200" height="40" rx="14" fill="#F4F5F7"/><text x="188" y="73" font-size="13" fill="#333D4B">사장님, 견적 얼마예요?</text>
<rect x="266" y="100" width="200" height="40" rx="14" fill="#3182F6"/><text x="280" y="125" font-size="13" fill="#fff">견적서와 링크 보내드렸어요 📄</text>
<rect x="174" y="152" width="130" height="30" rx="12" fill="#E7F8EF"/><text x="188" y="172" font-size="12" fill="#0B7A45">접수 완료 ✓</text></svg>''',
    # 달력 겹침/일정
    "calendar": f'''<svg viewBox="0 0 640 220" {_FIG_SVG_COMMON} role="img">
<rect width="640" height="220" rx="16" fill="#F4F5F7"/>
<rect x="60" y="28" width="520" height="164" rx="14" fill="#fff"/>
<rect x="60" y="28" width="520" height="40" rx="14" fill="#1B64DA"/><text x="320" y="54" font-size="15" text-anchor="middle" fill="#fff" font-weight="800">7월</text>
<g font-size="13" fill="#5A6472" text-anchor="middle"><text x="120" y="98">월</text><text x="220" y="98">화</text><text x="320" y="98">수</text><text x="420" y="98">목</text><text x="520" y="98">금</text></g>
<rect x="285" y="112" width="70" height="30" rx="9" fill="#EEF4FF"/><text x="320" y="132" font-size="11.5" text-anchor="middle" fill="#1B64DA" font-weight="800">A 현장</text>
<rect x="285" y="148" width="70" height="30" rx="9" fill="#FDE8ED"/><text x="320" y="168" font-size="11.5" text-anchor="middle" fill="#F0436A" font-weight="800">B 현장?!</text>
<rect x="385" y="112" width="70" height="30" rx="9" fill="#E7F8EF"/><text x="420" y="132" font-size="11.5" text-anchor="middle" fill="#0B7A45" font-weight="800">자동 등록</text></svg>''',
    # 돈/정산
    "money": f'''<svg viewBox="0 0 640 200" {_FIG_SVG_COMMON} role="img">
<rect width="640" height="200" rx="16" fill="#F4F5F7"/>
<rect x="40" y="34" width="270" height="132" rx="14" fill="#fff"/>
<text x="60" y="70" font-size="13.5" font-weight="800" fill="#0B0F19">동탄 욕실 — 잔금</text>
<rect x="60" y="86" width="160" height="12" rx="6" fill="#FDE8ED"/><text x="290" y="97" font-size="13" text-anchor="end" font-weight="900" fill="#F0436A">90만 미수</text>
<text x="60" y="140" font-size="12" fill="#9AA3AF">잊고 있으면 그냥 사라지는 돈</text>
<rect x="330" y="34" width="270" height="132" rx="14" fill="#fff"/>
<text x="350" y="70" font-size="13.5" font-weight="800" fill="#0B0F19">수원 타일 — 계약금</text>
<rect x="350" y="86" width="200" height="12" rx="6" fill="#E7F8EF"/><text x="580" y="97" font-size="13" text-anchor="end" font-weight="900" fill="#0B7A45">입금 확인 ✓</text>
<text x="350" y="140" font-size="12" fill="#9AA3AF">현장별로 화면에 남아 있는 돈</text></svg>''',
}


def _inject_blog_figures(body: str) -> str:
    """<figure data-fig="키">캡션</figure> → 실제 SVG + 캡션으로 치환."""
    import re as _re
    import html as _html

    def _rep(m):
        key = m.group(1)
        caption = m.group(2).strip()
        svg = _FIG_SVGS.get(key)
        if not svg:
            return ""
        cap = (f'<figcaption style="font-size:12.5px;color:#9AA3AF;text-align:center;'
               f'margin-top:8px">{_html.escape(caption)}</figcaption>') if caption else ""
        return (f'<figure style="margin:22px 0;border-radius:16px;overflow:hidden">'
                f'{svg}{cap}</figure>')

    return _re.sub(r'<figure data-fig="([a-z]+)">([\s\S]*?)</figure>', _rep, body)


def _sanitize_blog_body(body: str) -> str:
    """허용 태그 외 <script> 등 제거 (Claude 출력 방어). figure 는 먼저 SVG 치환."""
    import re as _re
    body = _re.sub(r"<script[\s\S]*?</script>", "", body, flags=_re.I)
    body = _inject_blog_figures(body)  # 추가106 — 치환된 figure/svg 는 신뢰 콘텐츠
    # figure/svg 블록은 보호하고 나머지만 화이트리스트 필터
    parts = _re.split(r"(<figure[\s\S]*?</figure>)", body)
    cleaned = []
    for i, part in enumerate(parts):
        if part.startswith("<figure"):
            cleaned.append(part)
            continue
        part = _re.sub(r"<(?!/?(h2|p|ul|li|b|div)\b)[^>]*>", "", part)
        part = _re.sub(r'<div(?![^>]*class="box")[^>]*>', '<div class="box">', part)
        cleaned.append(part)
    return "".join(cleaned)


async def _blog_generate_one(slug: Optional[str] = None) -> dict:
    """주제 큐에서 미발행 1개(또는 지정 slug) 를 Claude 로 생성해 저장."""
    _blog_db_init()
    with db_conn() as con:
        done = {r[0] for r in con.execute("SELECT slug FROM blog_posts").fetchall()}
    if slug:
        topic = next((t for t in _BLOG_TOPIC_QUEUE if t[0] == slug), None)
        if not topic:
            raise HTTPException(404, f"주제 큐에 없는 slug: {slug}")
        if slug in done:
            raise HTTPException(409, f"이미 발행됨: {slug}")
    else:
        topic = next((t for t in _BLOG_TOPIC_QUEUE if t[0] not in done), None)
        if not topic:
            return {"ok": False, "reason": "주제 큐 소진 — cowork 에게 주제 보충 요청"}
    tslug, angle, keyword, feature = topic
    user_msg = (f"주제: {angle}\n노릴 검색 키워드: {keyword}\n"
                f"기승전으로 연결할 시공막내 기능: {feature}\n"
                f"위 스타일 규칙대로 JSON 으로 글을 써줘.")
    parsed, response = await call_claude_json(
        system_prompt=_BLOG_WRITE_SYSTEM, user_msg=user_msg, max_tokens=3000)
    _log_llm_usage_from_response("blog-autopublish", response)
    title = (parsed.get("title") or angle)[:60].strip()
    desc = (parsed.get("description") or "")[:160].strip()
    category = parsed.get("category") if parsed.get("category") in _BLOG_CATEGORIES else "영업·단골"
    body = _sanitize_blog_body(parsed.get("body_html") or "")
    if len(body) < 400:
        raise HTTPException(502, "생성 본문이 너무 짧음 — 재시도 필요")
    # 추가109 — 태그 정제 (# 제거, 12자 컷, 최대 6개, 키워드 매핑)
    raw_tags = parsed.get("tags") or []
    if isinstance(raw_tags, str):
        raw_tags = [t for t in raw_tags.replace("#", "").split(",")]
    tags = []
    for t in raw_tags:
        t = str(t).replace("#", "").strip()[:14]
        if t and t not in tags:
            tags.append(t)
    tags = tags[:6]
    tags_str = ",".join(tags)
    thumb = _make_blog_thumb(title, category, tslug)
    now = _now_ms()
    with db_conn() as con:
        con.execute(
            "INSERT OR REPLACE INTO blog_posts (slug, title, description, category, body_html, thumb, created_at_ms, tags) "
            "VALUES (?,?,?,?,?,?,?,?)",
            (tslug, title, desc, category, body, thumb, now, tags_str))
        con.commit()
    print(f"[blog/autopublish] 발행: {tslug} — {title!r}")
    return {"ok": True, "slug": tslug, "title": title, "url": f"{_HOME_BASE}/blog/{tslug}"}


async def _blog_autopublish_loop() -> None:
    """매일 07:30 KST 에 1편 자동 발행. (uvicorn 살아있는 동안)"""
    while True:
        try:
            now = _kst_now()
            target = now.replace(hour=7, minute=30, second=0, microsecond=0)
            if now >= target:
                target += _dt.timedelta(days=1)
            await asyncio.sleep((target - now).total_seconds())
            if not _BLOG_AUTOPUBLISH:
                continue
            # 오늘 이미 발행됐으면 skip (수동 트리거 중복 방지)
            _blog_db_init()
            today0 = int(_kst_now().replace(hour=0, minute=0, second=0, microsecond=0)
                         .replace(tzinfo=_dt.timezone(_dt.timedelta(hours=9))).timestamp() * 1000)
            with db_conn() as con:
                n = con.execute("SELECT COUNT(*) FROM blog_posts WHERE created_at_ms >= ?",
                                (today0,)).fetchone()[0]
            if n:
                print("[blog/autopublish] 오늘 발행분 있음 — skip")
                continue
            await _blog_generate_one()
        except asyncio.CancelledError:
            return
        except Exception as e:
            print(f"[blog/autopublish] 오류: {e}")
            await asyncio.sleep(3600)


@app.post("/api/admin/blog/generate")
async def admin_blog_generate(
    payload: Optional[dict] = None,
    authorization: Optional[str] = Header(default=None),
) -> dict:
    """수동 발행 트리거 (다음 주제 1개, 또는 {slug} 지정)."""
    _admin_auth_bearer_from_header(authorization)
    return await _blog_generate_one((payload or {}).get("slug"))


# ── 동적 라우트: 섬네일 / 블로그 / 업데이트 / sitemap ──

@app.get("/static/thumbs/{name}", include_in_schema=False)
async def serve_thumb(name: str):
    if not name.replace("-", "").replace("_", "").replace(".", "").isalnum() or not name.endswith(".png"):
        raise HTTPException(404)
    path = _THUMBS_DIR / name
    if not path.exists():
        path = _THUMBS_DIR / "default.png"
        if not path.exists():
            raise HTTPException(404)
    return FileResponse(path, media_type="image/png",
                        headers={"Cache-Control": "public, max-age=86400"})


# 초기 3편 (정적 파일) 의 인덱스 메타
_BLOG_STATIC_META = [
    {"slug": "missed-call-cost", "category": "고객 응대",
     "title": "현장에서 놓친 전화 한 통, 얼마짜리인지 계산해 보셨나요",
     "description": "부재중 전화의 70%는 다시 걸려 오지 않습니다. 못 받는 건 어쩔 수 없어도, 놓치지 않는 방법은 있습니다.",
     "date": "2026. 7. 8", "thumb": "/static/thumbs/blog-missed-call-cost.png"},
    {"slug": "estimate-text-mistakes", "category": "견적",
     "title": "견적을 문자로 “300이요”라고 보내면 생기는 일 3가지",
     "description": "부가세 별도인지, 자재 포함인지, 언제까지 유효한지 — 한 줄 견적이 만드는 분쟁 3가지.",
     "date": "2026. 7. 8", "thumb": "/static/thumbs/blog-estimate-text-mistakes.png"},
    {"slug": "schedule-double-booking", "category": "일정 관리",
     "title": "시공일 겹침 사고, 달력 앱 탓이 아닙니다",
     "description": "문제는 기억력이 아니라 “잡히는 순간 기록되는 구조”가 없다는 것.",
     "date": "2026. 7. 8", "thumb": "/static/thumbs/blog-schedule-double-booking.png"},
]
_BLOG_STATIC_META_MAP = {m["slug"]: m for m in _BLOG_STATIC_META}


def _render_blog_index_html() -> str:
    import html as _html
    _blog_db_init()
    with db_conn() as con:
        rows = con.execute(
            "SELECT slug, title, description, category, created_at_ms FROM blog_posts "
            "ORDER BY created_at_ms DESC").fetchall()
    cards = []
    for slug, title, desc, cat, ms in rows:
        dt = _dt.datetime.utcfromtimestamp(ms / 1000) + _dt.timedelta(hours=9)
        with db_conn() as con2:
            trow = con2.execute("SELECT thumb FROM blog_posts WHERE slug = ?", (slug,)).fetchone()
        cards.append({"slug": slug, "title": title, "description": desc,
                      "category": cat, "date": f"{dt.year}. {dt.month}. {dt.day}",
                      "thumb": (trow[0] if trow and trow[0] else "/static/thumbs/default.png")})
    cards += _BLOG_STATIC_META
    items = "".join(
        f'<a class="post" href="/blog/{c["slug"]}">'
        f'<img src="{c.get("thumb") or "/static/thumbs/default.png"}" alt="{_html.escape(c["title"])}" loading="lazy">'
        f'<div class="pbody"><span class="cat">{_html.escape(c["category"])}</span>'
        f'<h2>{_html.escape(c["title"])}</h2>'
        f'<p>{_html.escape(c["description"])}</p>'
        f'<div class="pmeta">{c["date"]} · 시공막내</div></div></a>'
        for c in cards)
    extra_css = """
  .hero{max-width:920px;margin:0 auto;padding:48px 18px 6px;}
  .hero p{color:var(--t2);font-size:14.5px;margin-top:8px;}
  .wrap{max-width:920px;margin:0 auto;padding:26px 18px 70px;display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:16px;align-items:start;}
  .post{display:block;background:#fff;border:1px solid var(--line);border-radius:18px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.04);transition:transform .15s,border-color .15s;}
  .post:hover{border-color:var(--blue);transform:translateY(-3px);}
  .post img{width:100%;height:auto;display:block;aspect-ratio:1200/630;object-fit:cover;}
  .post .pbody{padding:16px 18px 18px;}
  .post .cat{margin-bottom:8px;}
  .post h2{font-size:16.5px;margin:0;line-height:1.45;}
  .post p{font-size:13.2px;color:var(--t2);margin:8px 0 0;}
  .post .pmeta{font-size:11.5px;color:var(--t3);margin-top:10px;}
"""
    return (
        '<!doctype html><html lang="ko"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">'
        '<title>시공막내 블로그 — 시공 사장님을 위한 영업·응대·운영 팁</title>'
        '<meta name="description" content="혼자 일하는 시공·인테리어 사장님을 위한 실전 팁. 부재중 전화 응대, 견적서, 일정 관리, 수금까지 매일 한 편씩.">'
        f'<link rel="canonical" href="{_HOME_BASE}/blog">'
        '<meta property="og:type" content="website">'
        '<meta property="og:title" content="시공막내 블로그 — 시공 사장님 실전 운영 팁">'
        '<meta property="og:description" content="현장 뛰면서 놓치는 돈 줄이는 법을 매일 씁니다.">'
        f'<meta property="og:url" content="{_HOME_BASE}/blog">'
        '<meta property="og:site_name" content="시공막내">'
        f'<meta property="og:image" content="{_HOME_BASE}/static/thumbs/blog.png">'
        '<meta property="og:image:width" content="1200"><meta property="og:image:height" content="630">'
        '<meta name="twitter:card" content="summary_large_image">'
        f'<meta name="twitter:image" content="{_HOME_BASE}/static/thumbs/blog.png">'
        '<link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css" rel="stylesheet">'
        f'<style>{_BLOG_SHELL_CSS}{extra_css}</style></head><body>'
        + _HOME_NAV +
        '<div class="hero"><h1>시공 사장님을 위한 실전 노트</h1>'
        '<p>영업 잘하는 법 말고, 현장 뛰면서 놓치는 돈 줄이는 법을 씁니다. 매일 아침 한 편.</p></div>'
        f'<div class="wrap">{items}</div>'
        + _HOME_FOOTER + '</body></html>'
    )


_UPDATE_KIND_ICON = {"new": "✨", "fix": "🔧", "imp": "⚡"}
_WEEK_KO = ["첫째", "둘째", "셋째", "넷째", "다섯째"]


@app.post("/api/admin/updates/entry")
async def admin_updates_entry(
    payload: dict,
    authorization: Optional[str] = Header(default=None),
) -> dict:
    """업데이트 소식 등록. {items: [{kind: new|fix|imp, text: "..."}]}"""
    _admin_auth_bearer_from_header(authorization)
    _blog_db_init()
    items = payload.get("items") or []
    if not items:
        raise HTTPException(400, "items 필수")
    now = _now_ms()
    with db_conn() as con:
        for it in items:
            kind = it.get("kind") if it.get("kind") in _UPDATE_KIND_ICON else "new"
            text = (it.get("text") or "").strip()
            if text:
                con.execute("INSERT INTO app_updates (kind, text, created_at_ms) VALUES (?,?,?)",
                            (kind, text, now))
        con.commit()
    return {"ok": True, "count": len(items)}


def _render_updates_dynamic() -> str:
    """app_updates 를 주 단위로 자동 그룹 → home_updates.html 상단에 주입."""
    import html as _html
    base_path = BASE_DIR / "static" / "home_updates.html"
    base = base_path.read_text(encoding="utf-8") if base_path.exists() else ""
    _blog_db_init()
    with db_conn() as con:
        rows = con.execute(
            "SELECT kind, text, created_at_ms FROM app_updates ORDER BY created_at_ms DESC").fetchall()
    if not rows or not base:
        return base
    weeks: dict[str, list] = {}
    order: list[str] = []
    for kind, text, ms in rows:
        dt = _dt.datetime.utcfromtimestamp(ms / 1000) + _dt.timedelta(hours=9)
        wk_idx = min((dt.day - 1) // 7, 4)
        label = f"{dt.year}. {dt.month}. {_WEEK_KO[wk_idx]} 주"
        if label not in weeks:
            weeks[label] = []
            order.append(label)
        weeks[label].append((kind, text))
    kind_icon = {"new": "✨", "fix": "🔧", "imp": "⚡"}
    blocks = []
    for label in order:
        tiles = "".join(
            f'<div class="tile {k}"><span class="tic">{kind_icon.get(k, "✨")}</span>'
            f'<p>{_html.escape(t)}</p></div>' for k, t in weeks[label])
        blocks.append(
            f'<div class="rel"><span class="date">{label}</span>'
            f'<h2>이번 주의 막내</h2><div class="tiles">{tiles}</div></div>')
    return base.replace('<div class="wrap">', '<div class="wrap">' + "".join(blocks), 1)


@app.post("/api/beta-signup")
async def beta_signup(req: BetaSignupRequest, request: Request):
    """랜딩페이지 신청 저장. phone PK = 중복 시 UPSERT (가장 최근 응답 keep)."""
    # 검증
    phone_digits = "".join(ch for ch in req.phone if ch.isdigit())
    if len(phone_digits) < 10 or len(phone_digits) > 11:
        raise HTTPException(status_code=400, detail="전화번호 형식 오류 (10~11자리)")
    if not req.agreed:
        raise HTTPException(status_code=400, detail="개인정보 수집·이용 동의 필요")
    if req.industry not in _VALID_INDUSTRIES:
        raise HTTPException(status_code=400, detail="업종 선택 오류")
    if req.monthly_inquiries not in _VALID_MONTHLY:
        raise HTTPException(status_code=400, detail="문의 수 선택 오류")
    region = (req.region or "").strip()
    if len(region) < 2 or len(region) > 40:
        raise HTTPException(status_code=400, detail="활동 지역 입력 오류")
    note = (req.note or "").strip()[:500]  # 추가57 — 한말씀에 옛 앱 경험도 적게 → 300 → 500
    business_name = (req.business_name or "").strip()[:60]  # 추가57
    # 추가82 — 업종 '기타' + 상세 업종이 오면 "기타(방수)" 형태로 저장 (admin 에서 바로 보임)
    industry_stored = req.industry
    if req.industry == "기타":
        detail = (req.industry_detail or "").strip()[:20]
        if detail:
            industry_stored = f"기타({detail})"

    # 메타
    ip = request.headers.get("x-forwarded-for", "").split(",")[0].strip() or (
        request.client.host if request.client else ""
    )
    ua = request.headers.get("user-agent", "")[:300]
    now = _now_ms()

    # 사장님 결정: 신청 즉시 사이트에서 다운로드 가능 (status='accepted' 자동).
    # cap (50명) 초과 시 'waitlist' 로 분기 → install 링크 미제공.
    with sqlite3.connect(DB_PATH) as con:
        # 현재 accepted/pending 카운트 (cap 도달 여부 판정)
        current_active = con.execute(
            "SELECT COUNT(*) FROM beta_signups WHERE status IN ('accepted','pending')"
        ).fetchone()[0]
        # 기존 신청자 (재신청) 인지 확인
        existing_row = con.execute(
            "SELECT status FROM beta_signups WHERE phone = ?", (phone_digits,)
        ).fetchone()
        existing_status = existing_row[0] if existing_row else None

        # 신규 신청자만 cap 체크. 기존 신청자는 이미 가진 status 유지.
        if existing_status:
            new_status = existing_status  # 그대로 유지 (재신청 시 status 안 바꿈)
        elif current_active < 50:
            new_status = "accepted"  # cap 미달 → 즉시 다운로드 가능
        else:
            new_status = "waitlist"  # cap 초과 → 대기자

        con.execute(
            """
            INSERT INTO beta_signups
              (phone, industry, region, monthly_inquiries, note, agreed_at_ms,
               source, ip, ua, status, created_at_ms, updated_at_ms, business_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(phone) DO UPDATE SET
              industry          = excluded.industry,
              region            = excluded.region,
              monthly_inquiries = excluded.monthly_inquiries,
              note              = excluded.note,
              agreed_at_ms      = excluded.agreed_at_ms,
              source            = excluded.source,
              ip                = excluded.ip,
              ua                = excluded.ua,
              updated_at_ms     = excluded.updated_at_ms,
              business_name     = excluded.business_name
            """,
            (
                phone_digits,
                industry_stored,
                region,
                req.monthly_inquiries,
                note,
                now,
                req.source[:100],
                ip[:60],
                ua,
                new_status,
                now,
                now,
                business_name,
            ),
        )
        count = con.execute(
            "SELECT COUNT(*) FROM beta_signups WHERE status != 'rejected'"
        ).fetchone()[0]
        con.commit()

    print(
        f"[beta_signup] phone={phone_digits} industry={req.industry} "
        f"region={region[:20]} status={new_status} total={count}"
    )
    install_url = "/install" if new_status == "accepted" else None
    return {
        "ok": True,
        "message": (
            "신청이 접수됐어요. 지금 바로 설치하실 수 있어요."
            if new_status == "accepted"
            else "신청이 접수됐어요. 50명 마감으로 대기자 등록됐어요 — 자리 나면 안내드릴게요."
        ),
        "status": new_status,
        "install_url": install_url,
        "total_so_far": count,
    }


@app.get("/api/beta-signup-count")
async def beta_signup_count():
    """랜딩페이지 라이브 카운터용. 거절된 신청 제외한 총 인원."""
    with sqlite3.connect(DB_PATH) as con:
        n = con.execute(
            "SELECT COUNT(*) FROM beta_signups WHERE status != 'rejected'"
        ).fetchone()[0]
    return {"total": n, "cap": 100}


_BETA_SIGNUPS_HTML_PATH = BASE_DIR / "static" / "admin_beta_signups.html"


@app.get(
    "/admin/beta/signups", response_class=HTMLResponse, include_in_schema=False
)
async def admin_beta_signups_page():
    """사장님 admin — 신청자 리스트 HTML SPA.

    client-side 토큰 모달 + sessionStorage (admin_beta_intake 패턴과 일관).
    서버 인증은 /admin/beta/signups/data 호출 시점에 Bearer 검증.
    """
    if not _BETA_SIGNUPS_HTML_PATH.exists():
        raise HTTPException(
            status_code=500,
            detail=(
                f"HTML not found at {_BETA_SIGNUPS_HTML_PATH}. "
                "server/static/admin_beta_signups.html 확인."
            ),
        )
    return _BETA_SIGNUPS_HTML_PATH.read_text(encoding="utf-8")


@app.get("/admin/beta/signups/data")
async def admin_beta_signups_data(
    authorization: Optional[str] = Header(default=None),
    status: str = "all",
    limit: int = 100,
):
    """신청자 데이터 JSON 응답 (Bearer 토큰 인증).

    admin_beta_signups.html 에서 sessionStorage 토큰으로 호출.
    """
    _admin_auth_bearer_from_header(authorization)
    limit = max(1, min(limit, 500))
    where = ""
    params: tuple = ()
    if status in {"pending", "accepted", "rejected"}:
        where = "WHERE status = ?"
        params = (status,)
    with sqlite3.connect(DB_PATH) as con:
        con.row_factory = sqlite3.Row
        rows = con.execute(
            f"""
            SELECT phone, industry, region, monthly_inquiries, note, source,
                   ip, ua, status, created_at_ms, updated_at_ms, business_name
            FROM beta_signups
            {where}
            ORDER BY created_at_ms DESC
            LIMIT {limit}
            """,
            params,
        ).fetchall()
        totals = dict(
            con.execute(
                "SELECT status, COUNT(*) FROM beta_signups GROUP BY status"
            ).fetchall()
        )
        # 추가56b (2026-06-25) — 화이트리스트 등록 여부 한 번에 (set lookup, O(1))
        # 사장님이 한 번 클릭으로 신청자 → 화이트리스트 등록할 수 있게.
        wl_phones = {r[0] for r in con.execute("SELECT phone FROM beta_whitelist").fetchall()}

    def _fmt_phone(p: str) -> str:
        if len(p) == 11:
            return f"{p[:3]}-{p[3:7]}-{p[7:]}"
        if len(p) == 10:
            return f"{p[:3]}-{p[3:6]}-{p[6:]}"
        return p

    items = []
    for r in rows:
        items.append(
            {
                "phone": _fmt_phone(r["phone"]),
                "phone_raw": r["phone"],
                "industry": r["industry"],
                "region": r["region"],
                "monthly_inquiries": r["monthly_inquiries"],
                "note": r["note"] or "",
                "source": r["source"] or "",
                "ip": r["ip"] or "",
                "ua": (r["ua"] or "")[:120],
                "status": r["status"],
                "created_at_ms": r["created_at_ms"],
                "updated_at_ms": r["updated_at_ms"],
                "is_whitelisted": r["phone"] in wl_phones,  # 추가56b
                "business_name": r["business_name"] or "",  # 추가57
            }
        )
    return {
        "items": items,
        "totals": {
            "pending": totals.get("pending", 0),
            "accepted": totals.get("accepted", 0),
            "rejected": totals.get("rejected", 0),
            "all": sum(totals.values()),
        },
        "filter": status,
        "limit": limit,
    }


# 추가57b (2026-06-25) — 베타 신청자 status 변경 (운영자 결정 = 거절)
# 사장님 의도: "여기 거절도 있어야하지않을까" — admin 페이지에서 한 번 클릭으로 거절 처리.
class BetaSignupStatusPatch(BaseModel):
    status: str  # 'rejected' | 'accepted' | 'pending'


@app.patch("/admin/beta/signups/{phone}")
async def admin_beta_signups_patch(
    phone: str,
    body: BetaSignupStatusPatch,
    authorization: Optional[str] = Header(default=None),
):
    """신청자 status 변경. 거절 ↔ 복구 둘 다 같은 endpoint."""
    _admin_auth_bearer_from_header(authorization)
    phone_digits = "".join(ch for ch in phone if ch.isdigit())
    if not phone_digits:
        raise HTTPException(400, "phone 필수")
    new_status = (body.status or "").strip().lower()
    if new_status not in {"pending", "accepted", "rejected", "waitlist"}:
        raise HTTPException(400, "status = pending|accepted|rejected|waitlist 중 하나")
    now = _now_ms()
    with sqlite3.connect(DB_PATH) as con:
        cur = con.execute(
            "UPDATE beta_signups SET status = ?, updated_at_ms = ? WHERE phone = ?",
            (new_status, now, phone_digits),
        )
        if cur.rowcount == 0:
            raise HTTPException(404, "해당 phone 신청자 없음")
        con.commit()
    print(f"[admin/beta/signups] {phone_digits} → status={new_status}")
    return {"ok": True, "phone": phone_digits, "status": new_status}


# ============================================================================
# 추가31 (2026-06-15) — 베타 화이트리스트 (테스터 폰번호 게이트)
# ─────────────────────────────────────────────────────────────────────────────
# 사장님이 admin 페이지에서 폰번호 등록 → 앱 첫 진입 시 본인 phone 입력 →
# 서버 매칭 OK 면 진입. 코드·SMS·관리 없음. 폰번호 1개로 화이트리스트 운영.
#
# Endpoint:
#   POST /api/beta/check            — 앱 첫 진입 (인증 X, phone 하나만)
#   POST /admin/beta/whitelist      — 사장님이 추가 (Bearer 인증)
#   DELETE /admin/beta/whitelist/{phone} — 제거
#   GET /admin/beta/whitelist/data  — 목록 조회
#   GET /admin/beta/whitelist       — admin HTML SPA (사장님 폰에서 추가/제거)
# ============================================================================


class BetaCheckRequest(BaseModel):
    phone: str


@app.post("/api/beta/check")
async def beta_check(req: BetaCheckRequest) -> dict:
    """앱 첫 진입 시 호출. phone 이 화이트리스트에 있는지 확인.

    응답: { ok: true, name: "강동 박사장" } / { ok: false, reason: "..." }
    """
    phone_digits = _norm_phone(req.phone)
    if not phone_digits:
        return {"ok": False, "reason": "폰번호 형식이 올바르지 않습니다"}
    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            "SELECT name, first_seen_ms, use_count FROM beta_whitelist WHERE phone = ?",
            (phone_digits,),
        ).fetchone()
        if not row:
            print(f"[beta/check] {phone_digits} → 미등록")
            return {
                "ok": False,
                "reason": "베타 등록되지 않은 번호입니다. 사장님께 문의해주세요.",
            }
        name, first_seen, use_count = row
        # 첫 사용 시각 기록 + use_count 증가
        new_first_seen = first_seen or now
        con.execute(
            """
            UPDATE beta_whitelist
            SET first_seen_ms = COALESCE(first_seen_ms, ?),
                last_seen_ms = ?,
                use_count = use_count + 1
            WHERE phone = ?
            """,
            (now, now, phone_digits),
        )
        con.commit()
    print(f"[beta/check] {phone_digits} → OK (name={name}, use_count={(use_count or 0) + 1})")
    return {"ok": True, "name": name or "테스터"}


class BetaWhitelistAddRequest(BaseModel):
    phone: str
    name: Optional[str] = None
    memo: Optional[str] = None


@app.post("/admin/beta/whitelist")
async def admin_beta_whitelist_add(
    req: BetaWhitelistAddRequest,
    authorization: Optional[str] = Header(default=None),
) -> dict:
    """사장님이 베타 테스터 폰번호 추가."""
    _admin_auth_bearer_from_header(authorization)
    phone_digits = _norm_phone(req.phone)
    if not phone_digits:
        raise HTTPException(400, "유효하지 않은 폰번호")
    name = (req.name or "").strip()[:60] or None
    memo = (req.memo or "").strip()[:200] or None
    now = _now_ms()
    with db_conn() as con:
        # 이미 있으면 UPDATE (이름·메모 갱신), 없으면 INSERT
        existing = con.execute(
            "SELECT phone FROM beta_whitelist WHERE phone = ?",
            (phone_digits,),
        ).fetchone()
        if existing:
            con.execute(
                "UPDATE beta_whitelist SET name = ?, memo = ? WHERE phone = ?",
                (name, memo, phone_digits),
            )
            action = "updated"
        else:
            con.execute(
                """
                INSERT INTO beta_whitelist (phone, name, memo, added_at_ms, use_count)
                VALUES (?, ?, ?, ?, 0)
                """,
                (phone_digits, name, memo, now),
            )
            action = "added"
        con.commit()
    print(f"[admin/beta/whitelist] {action} {phone_digits} name={name} memo={memo}")
    return {"ok": True, "phone": phone_digits, "name": name, "memo": memo, "action": action}


@app.delete("/admin/beta/whitelist/{phone}")
async def admin_beta_whitelist_remove(
    phone: str,
    authorization: Optional[str] = Header(default=None),
) -> dict:
    """사장님이 베타 테스터 제거."""
    _admin_auth_bearer_from_header(authorization)
    phone_digits = _norm_phone(phone)
    if not phone_digits:
        raise HTTPException(400, "유효하지 않은 폰번호")
    with db_conn() as con:
        cur = con.execute(
            "DELETE FROM beta_whitelist WHERE phone = ?",
            (phone_digits,),
        )
        con.commit()
    print(f"[admin/beta/whitelist] removed {phone_digits} (rows={cur.rowcount})")
    return {"ok": True, "phone": phone_digits, "removed": cur.rowcount}


class BetaWhitelistPatchRequest(BaseModel):
    owner_trade: Optional[str] = None   # 업종 수동 변경 (예: "줄눈", "타일", "도배")


@app.patch("/admin/beta/whitelist/{phone}")
async def admin_beta_whitelist_patch(
    phone: str,
    req: BetaWhitelistPatchRequest,
    authorization: Optional[str] = Header(default=None),
) -> dict:
    """추가50 (2026-06-21) — 사장님이 잘못 설정된 업종 수동 변경.

    body: {"owner_trade": "줄눈"}  → 빈 문자열 보내면 NULL 로 reset.
    """
    _admin_auth_bearer_from_header(authorization)
    phone_digits = _norm_phone(phone)
    if not phone_digits:
        raise HTTPException(400, "유효하지 않은 폰번호")
    new_trade = (req.owner_trade or "").strip()[:30]
    new_trade_val = new_trade or None  # 빈 문자열은 NULL
    with db_conn() as con:
        cur = con.execute(
            "UPDATE beta_whitelist SET owner_trade = ? WHERE phone = ?",
            (new_trade_val, phone_digits),
        )
        con.commit()
    if cur.rowcount == 0:
        raise HTTPException(404, "phone 화이트리스트 미등록")
    print(f"[admin/beta/whitelist] patch {phone_digits} → owner_trade={new_trade_val!r}")
    return {"ok": True, "phone": phone_digits, "owner_trade": new_trade_val}


@app.get("/admin/beta/whitelist/data")
async def admin_beta_whitelist_data(
    authorization: Optional[str] = Header(default=None),
) -> dict:
    """베타 화이트리스트 전체 목록 (사장님 admin)."""
    _admin_auth_bearer_from_header(authorization)
    with db_conn() as con:
        rows = con.execute(
            """
            SELECT phone, name, memo, added_at_ms, first_seen_ms, last_seen_ms, use_count
            FROM beta_whitelist
            ORDER BY added_at_ms DESC
            """
        ).fetchall()
    items = []
    for r in rows:
        items.append({
            "phone": _fmt_phone(r[0]),
            "phone_raw": r[0],
            "name": r[1] or "",
            "memo": r[2] or "",
            "added_at_ms": r[3],
            "first_seen_ms": r[4],
            "last_seen_ms": r[5],
            "use_count": r[6],
            "activated": bool(r[4]),  # 첫 진입 했는지
        })
    return {
        "items": items,
        "total": len(items),
        "activated": sum(1 for it in items if it["activated"]),
    }


_BETA_WHITELIST_HTML = """<!doctype html>
<html lang="ko"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>베타 화이트리스트 — RING-GO</title>
<style>
  :root { --blue:#3182F6; --blue-dark:#1B64DA; --bg:#F4F5F7; --card:#fff;
          --t1:#0B0F19; --t2:#5A6472; --t3:#9AA3AF; --line:#EEF0F3;
          --error:#F0436A; --success:#16C172; }
  * { box-sizing:border-box; }
  body { margin:0; background:var(--bg); font-family:'Pretendard',-apple-system,system-ui,sans-serif;
         color:var(--t1); line-height:1.5; }
  .wrap { max-width:760px; margin:0 auto; padding:18px 16px 40px; }
  h1 { font-size:21px; font-weight:800; margin:0 0 4px; }
  .sub { font-size:13px; color:var(--t2); margin-bottom:18px; }
  .stat { display:inline-block; background:var(--card); border-radius:10px; padding:8px 12px;
          font-size:13px; font-weight:700; margin-right:8px; box-shadow:0 1px 3px rgba(0,0,0,.04); }
  .stat b { color:var(--blue-dark); font-size:15px; margin-left:4px; }
  .card { background:var(--card); border-radius:14px; padding:16px; box-shadow:0 1px 3px rgba(0,0,0,.04);
          margin-top:16px; }
  .card h2 { font-size:15px; font-weight:800; margin:0 0 12px; }
  label { display:block; font-size:12px; font-weight:700; color:var(--t2); margin:8px 0 4px; }
  input, textarea { width:100%; border:1.5px solid var(--line); border-radius:10px; padding:11px 12px;
                    font-size:14px; font-family:inherit; }
  input:focus { outline:none; border-color:var(--blue); }
  .row { display:flex; gap:8px; }
  .row > * { flex:1; }
  button { background:var(--blue); color:#fff; border:0; border-radius:10px; padding:11px 16px;
           font-size:14px; font-weight:800; font-family:inherit; cursor:pointer; }
  button:disabled { opacity:.5; cursor:default; }
  button.warn { background:#fff; color:var(--error); border:1.5px solid var(--error); padding:6px 10px;
                font-size:12px; }
  table { width:100%; border-collapse:collapse; font-size:13px; }
  th, td { text-align:left; padding:9px 6px; border-bottom:1px solid var(--line); }
  th { font-size:11px; color:var(--t3); font-weight:700; }
  .badge { display:inline-block; padding:2px 7px; border-radius:6px; font-size:11px; font-weight:700; }
  .badge.on { background:#E7F8EF; color:var(--success); }
  .badge.off { background:#FFF2F5; color:var(--error); }
  .msg { margin-top:10px; font-size:13px; }
  .msg.ok { color:var(--success); }
  .msg.err { color:var(--error); }
  #tokenModal { position:fixed; inset:0; background:rgba(0,0,0,.5); display:none;
                align-items:center; justify-content:center; z-index:50; }
  #tokenModal.show { display:flex; }
  #tokenModal .box { background:#fff; border-radius:14px; padding:22px; max-width:90vw; width:340px; }
</style></head>
<body>
<div id="tokenModal"><div class="box">
  <h3 style="margin:0 0 8px">ADMIN_TOKEN 입력</h3>
  <p style="font-size:13px; color:#5A6472; margin:0 0 12px">brower 에 저장됩니다.</p>
  <input id="tokenInput" type="password" placeholder="토큰">
  <button onclick="saveToken()" style="margin-top:10px; width:100%">저장</button>
</div></div>

<div class="wrap">
  <a href="/admin" style="display:inline-block; font-size:13px; color:#3182F6; text-decoration:none; margin-bottom:8px;">← admin 홈</a>
  <h1>🧪 베타 화이트리스트</h1>
  <p class="sub">테스터 폰번호 관리 — 등록된 번호만 앱 첫 진입 가능</p>
  <div>
    <span class="stat">전체 <b id="statTotal">-</b></span>
    <span class="stat">활성 <b id="statActive">-</b></span>
  </div>

  <div class="card">
    <h2>+ 새 테스터 추가</h2>
    <div class="row">
      <div><label>폰번호 *</label><input id="addPhone" type="tel" inputmode="numeric" placeholder="010-1234-5678" maxlength="13" oninput="autoHyphen(this)"></div>
      <div><label>이름 (메모)</label><input id="addName" placeholder="강동 박사장"></div>
    </div>
    <label>메모 (모집 경로 등)</label>
    <input id="addMemo" placeholder="시공카페 댓글, 디테일라인 인스타 ...">
    <button onclick="addPhone()" style="margin-top:10px">추가</button>
    <div id="addMsg" class="msg"></div>
  </div>

  <div class="card">
    <h2>등록된 테스터</h2>
    <table>
      <thead><tr><th>폰번호</th><th>이름·메모</th><th>상태</th><th>사용 수</th><th></th></tr></thead>
      <tbody id="rows"><tr><td colspan="5" style="text-align:center; padding:20px; color:#9AA3AF">로딩중...</td></tr></tbody>
    </table>
  </div>
</div>

<script>
  function getToken() { return sessionStorage.getItem('admin_token') || ''; }
  function saveToken() {
    var t = document.getElementById('tokenInput').value.trim();
    if (!t) return;
    sessionStorage.setItem('admin_token', t);
    document.getElementById('tokenModal').classList.remove('show');
    load();
  }
  function ensureToken() {
    if (!getToken()) { document.getElementById('tokenModal').classList.add('show'); return false; }
    return true;
  }
  async function api(method, path, body) {
    var opts = { method: method, headers: { 'Authorization': 'Bearer ' + getToken() } };
    if (body) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
    var r = await fetch(path, opts);
    if (r.status === 401) { sessionStorage.removeItem('admin_token'); ensureToken(); throw new Error('인증 실패'); }
    if (!r.ok) { var t = await r.text(); throw new Error(t || 'API 오류'); }
    return r.json();
  }
  async function load() {
    if (!ensureToken()) return;
    try {
      var d = await api('GET', '/admin/beta/whitelist/data');
      document.getElementById('statTotal').textContent = d.total;
      document.getElementById('statActive').textContent = d.activated;
      var html = '';
      if (d.items.length === 0) {
        html = '<tr><td colspan="5" style="text-align:center; padding:20px; color:#9AA3AF">아직 등록된 테스터가 없어요</td></tr>';
      } else {
        for (var i=0; i<d.items.length; i++) {
          var it = d.items[i];
          var date = it.added_at_ms ? new Date(it.added_at_ms).toLocaleDateString('ko') : '';
          // 추가34 (2026-06-18) — 폰번호 클릭 시 /admin/user/{phone} 으로 (스케줄·활동 다 보임)
          html += '<tr>'
            + '<td><a href="/admin/user/' + encodeURIComponent(it.phone_raw) + '" style="color:#3182F6; text-decoration:none"><b>' + it.phone + '</b></a><br><span style="font-size:11px; color:#9AA3AF">' + date + '</span></td>'
            + '<td><b>' + escape(it.name || '-') + '</b><br><span style="font-size:11.5px; color:#5A6472">' + escape(it.memo || '') + '</span></td>'
            + '<td>' + (it.activated ? '<span class="badge on">사용중</span>' : '<span class="badge off">미진입</span>') + '</td>'
            + '<td>' + it.use_count + '</td>'
            + '<td><button class="warn" onclick="rm(\\''+it.phone_raw+'\\')">삭제</button></td>'
            + '</tr>';
        }
      }
      document.getElementById('rows').innerHTML = html;
    } catch(e) { alert('로드 실패: ' + e.message); }
  }
  async function addPhone() {
    if (!ensureToken()) return;
    var phone = document.getElementById('addPhone').value.trim();
    var name = document.getElementById('addName').value.trim();
    var memo = document.getElementById('addMemo').value.trim();
    if (!phone) { alert('폰번호 입력'); return; }
    var msg = document.getElementById('addMsg');
    msg.className = 'msg'; msg.textContent = '추가중...';
    try {
      var r = await api('POST', '/admin/beta/whitelist', { phone: phone, name: name, memo: memo });
      msg.className = 'msg ok';
      msg.textContent = (r.action === 'updated' ? '갱신됨' : '추가됨') + ': ' + r.phone;
      document.getElementById('addPhone').value = '';
      document.getElementById('addName').value = '';
      document.getElementById('addMemo').value = '';
      load();
    } catch(e) { msg.className = 'msg err'; msg.textContent = '실패: ' + e.message; }
  }
  async function rm(phone) {
    if (!confirm(phone + ' 삭제할까요?')) return;
    try { await api('DELETE', '/admin/beta/whitelist/' + encodeURIComponent(phone)); load(); }
    catch(e) { alert('삭제 실패: ' + e.message); }
  }
  function escape(s) { s = String(s||''); return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
  function autoHyphen(el) {
    var d = el.value.replace(/\D/g, '').slice(0, 11);
    if (d.length <= 3) el.value = d;
    else if (d.length <= 7) el.value = d.slice(0,3) + '-' + d.slice(3);
    else el.value = d.slice(0,3) + '-' + d.slice(3,7) + '-' + d.slice(7);
  }
  load();
</script></body></html>
"""


@app.get("/admin/beta/whitelist", response_class=HTMLResponse, include_in_schema=False)
async def admin_beta_whitelist_page():
    """추가83 (2026-07-03) — 화이트리스트 페이지는 종합 대시보드 '전체 멤버 관리'로 통합.
    옛 북마크/링크 호환 위해 리다이렉트만 남김. (추가/제거/목록 = 대시보드에서)
    API endpoint (POST/DELETE/PATCH/data) 는 그대로 유지 — 통합 UI 가 사용."""
    return HTMLResponse(
        content='<meta http-equiv="refresh" content="0; url=/admin/beta/dashboard">'
                '<a href="/admin/beta/dashboard">전체 멤버 관리로 이동 →</a>'
    )


# ============================================================================
# 추가32 (2026-06-15) — 베타 종합 대시보드 (사장님 IR + 베타 운영)
# ─────────────────────────────────────────────────────────────────────────────
# 사장님 요청: "내 베타테스터들이 어떤 활동을 하는지 정확하게 판단할 수 있게."
# - DAU/WAU/MAU
# - 기능별 사용량 (refine, 통화요약, 협업 invite 등)
# - 일별 활성도 라인 차트
# - 사용자별 활동 테이블
# - LLM 비용 (인프라)
# - 네트워크 신호 (협업·모집 발생)
# ============================================================================


# ============================================================================
# 추가84 — 멤버 등급 변경 (사장님 설계: 카페 등업처럼 한 곳에서 승급/강등)
#   applicant(등업대기) → tester(베타) → standard(일반 5만) → premium(특별 10만)
# 각 등급 전환이 실제로 하는 일:
#   → tester   : beta_whitelist 등록 (앱 접근 허용). 유료였다면 subscribers 해지 처리.
#   → standard : whitelist 유지/등록 + subscribers upsert (standard_50k, 50,000원).
#   → premium  : whitelist 유지/등록 + subscribers upsert (premium_100k, 100,000원).
#   → applicant: whitelist 제거 (앱 접근 차단) + subscribers 해지. 신청 기록은 남음.
# ============================================================================

_GRADE_TIER = {
    "standard": ("standard_50k", 50_000),
    "premium": ("premium_100k", 100_000),
}


class MemberGradeRequest(BaseModel):
    phone: str = ""
    grade: str = ""   # applicant | tester | standard | premium
    name: Optional[str] = None  # 등업대기→베타 승급 시 이름 (신청 폼 business_name)


@app.post("/api/admin/member/grade")
async def admin_member_grade(
    req: MemberGradeRequest,
    authorization: Optional[str] = Header(default=None),
) -> dict:
    _admin_auth_bearer_from_header(authorization)
    phone = _norm_phone(req.phone)
    if not phone:
        raise HTTPException(400, "phone 필수")
    grade = (req.grade or "").strip()
    if grade not in ("applicant", "tester", "standard", "premium"):
        raise HTTPException(400, "grade = applicant|tester|standard|premium")
    now = _now_ms()
    with db_conn() as con:
        # 이름 결정: 요청 name → whitelist name → 신청 폼 business_name
        name = (req.name or "").strip()[:60] or None
        if not name:
            r = con.execute("SELECT name FROM beta_whitelist WHERE phone=?", (phone,)).fetchone()
            name = (r[0] if r else None)
        if not name:
            r = con.execute("SELECT business_name FROM beta_signups WHERE phone=?", (phone,)).fetchone()
            name = (r[0] if r else None)

        if grade == "applicant":
            con.execute("DELETE FROM beta_whitelist WHERE phone=?", (phone,))
            con.execute(
                "UPDATE subscribers SET churned_at_ms=?, updated_at_ms=? "
                "WHERE phone=? AND churned_at_ms IS NULL", (now, now, phone))
        else:
            # tester/standard/premium 모두 앱 접근 필요 → whitelist 보장
            exists = con.execute(
                "SELECT 1 FROM beta_whitelist WHERE phone=?", (phone,)).fetchone()
            if not exists:
                # 추가86 — 등업 승인 시 무료 체험 60일 시작
                con.execute(
                    "INSERT INTO beta_whitelist "
                    "(phone, name, memo, added_at_ms, use_count, free_until_ms) "
                    "VALUES (?, ?, ?, ?, 0, ?)",
                    (phone, name, "멤버관리 등업", now,
                     now + FREE_TRIAL_DAYS * 86_400_000))
            if grade == "tester":
                con.execute(
                    "UPDATE subscribers SET churned_at_ms=?, updated_at_ms=? "
                    "WHERE phone=? AND churned_at_ms IS NULL", (now, now, phone))
            else:
                tier, price = _GRADE_TIER[grade]
                sub = con.execute(
                    "SELECT phone FROM subscribers WHERE phone=?", (phone,)).fetchone()
                if sub:
                    con.execute(
                        "UPDATE subscribers SET plan_tier=?, monthly_price_krw=?, "
                        "churned_at_ms=NULL, updated_at_ms=? WHERE phone=?",
                        (tier, price, now, phone))
                else:
                    con.execute(
                        "INSERT INTO subscribers (phone, plan_tier, monthly_price_krw, name, "
                        "started_at_ms, churned_at_ms, created_at_ms, updated_at_ms) "
                        "VALUES (?, ?, ?, ?, ?, NULL, ?, ?)",
                        (phone, tier, price, name, now, now, now))
        con.commit()
    print(f"[member/grade] {phone} → {grade}")
    return {"ok": True, "phone": phone, "grade": grade}


@app.get("/admin/beta/dashboard/data")
async def admin_beta_dashboard_data(
    days: int = 30,
    authorization: Optional[str] = Header(default=None),
) -> dict:
    """베타 종합 대시보드 데이터.

    days: 7 / 30 / 90 / 365 (시계열·집계 기간).
    응답: { kpi, daily_active, feature_usage, users[], cost, network }
    """
    _admin_auth_bearer_from_header(authorization)
    days = max(1, min(days, 365))
    now = _now_ms()
    cutoff = now - days * 86_400_000
    cutoff_7d = now - 7 * 86_400_000

    # 폰 format helper (module-level _fmt_phone 가 다른 함수 nested 라 사용 불가)
    def _fmt_phone(p):
        s = "".join(ch for ch in (p or "") if ch.isdigit())
        if len(s) == 11 and s.startswith("010"):
            return f"{s[:3]}-{s[3:7]}-{s[7:]}"
        if len(s) == 10:
            return f"{s[:3]}-{s[3:6]}-{s[6:]}"
        return s or ""

    # 베타 화이트리스트 이름 map (helper) — 사장님이 admin 에서 박은 name 활용.
    # _is_registered_owner (subscribers/beta_signups) 에 없는 사용자도 화이트리스트 name 으로 표시.
    _wl_name_map: dict = {}
    # (wl_rows 는 아래에서 로드되므로 closure 로 lazy lookup)
    def _user_name(phone, default="사장님"):
        if not phone:
            return default
        return (
            _is_registered_owner(phone)
            or _wl_name_map.get(phone)
            or default
        )

    with db_conn() as con:
        # ── 화이트리스트 사용자 ──
        wl_rows = con.execute(
            """
            SELECT phone, name, memo, added_at_ms, first_seen_ms, last_seen_ms, use_count,
                   owner_trade, free_until_ms
            FROM beta_whitelist ORDER BY added_at_ms DESC
            """
        ).fetchall()

        total_users = len(wl_rows)
        activated = sum(1 for r in wl_rows if r[4])
        new_7d = sum(1 for r in wl_rows if r[3] and r[3] >= cutoff_7d)
        # 화이트리스트 name map 구축 (위 _user_name closure 가 이걸 lookup)
        # 추가50 (2026-06-21) — owner_trade map 도 같이 (앱 onboarding 업종)
        wl_trade_map: dict = {}
        for r in wl_rows:
            if r[1]:  # name 있는 것만
                _wl_name_map[r[0]] = r[1]
            if len(r) > 7 and r[7]:  # owner_trade 있는 것만
                wl_trade_map[r[0]] = r[7]
        # 활성 정의: last_seen 7일 이내
        active_7d = sum(1 for r in wl_rows if r[5] and r[5] >= cutoff_7d)
        active_30d = sum(1 for r in wl_rows if r[5] and r[5] >= now - 30 * 86_400_000)

        wl_phones = [r[0] for r in wl_rows]
        # 추가49 (2026-06-21) — phone → industry 매핑.
        # 추가73 fix (2026-06-29) — 사장님 지적: "업종 = 앱 onboarding (owner_trade) 우선".
        #   옛: beta_signups.industry (모집 폼 자기보고) 만
        #   새: 앱 onboarding 값 (beta_whitelist.owner_trade) 우선 + 모집 폼 폴백
        industry_map: dict = {}
        if wl_phones:
            placeholders_i = ",".join(["?"] * len(wl_phones))
            industry_rows = con.execute(
                f"SELECT phone, industry FROM beta_signups WHERE phone IN ({placeholders_i})",
                wl_phones,
            ).fetchall()
            for ir in industry_rows:
                if ir[1]:
                    industry_map[ir[0]] = ir[1]  # 모집 폼 폴백
        # 앱 onboarding + admin 수동 설정 (owner_trade) 우선. 덮어씀.
        for _p, _trade in wl_trade_map.items():
            if _trade:
                industry_map[_p] = _trade

        # ── api_usage 데이터 (베타 phone 만) ──
        api_rows: list = []
        if wl_phones:
            placeholders = ",".join(["?"] * len(wl_phones))
            api_rows = con.execute(
                f"""
                SELECT phone, endpoint, input_tokens, output_tokens, cost_usd, created_at_ms
                FROM api_usage
                WHERE created_at_ms >= ?
                  AND phone IN ({placeholders})
                """,
                (cutoff, *wl_phones),
            ).fetchall()

        # ── 기능 사용량 집계 (endpoint 별) ──
        feature_count: dict = {}
        feature_cost: dict = {}
        feature_label = {
            "prepare-reply": "답장 추천",
            "refine": "친절 다듬기",
            "call-summary": "통화 요약 (텍스트)",
            "call-audio-summary": "통화 요약 (녹음)",
            "conversation-summary": "대화 요약",
            "card-summary": "고객 카드 요약",
            "next-action-suggest": "다음 행동 제안",
            "tone-import": "톤 학습",
        }
        for r in api_rows:
            endpoint = r[1]
            feature_count[endpoint] = feature_count.get(endpoint, 0) + 1
            feature_cost[endpoint] = feature_cost.get(endpoint, 0.0) + (r[4] or 0.0)

        # ── 일별 활성 (daily) ──
        daily_active: dict = {}     # date_key → set of phones
        daily_calls: dict = {}      # date_key → call count
        for r in api_rows:
            ts = r[5] or 0
            try:
                dt = _dt.datetime.utcfromtimestamp(ts / 1000) + _dt.timedelta(hours=9)
                k = dt.strftime("%Y-%m-%d")
            except Exception:
                continue
            daily_active.setdefault(k, set()).add(r[0])
            daily_calls[k] = daily_calls.get(k, 0) + 1
        # 빈 날짜 0 채우기 (시계열 연속)
        daily_series: list = []
        for i in range(days - 1, -1, -1):
            d = _dt.datetime.utcfromtimestamp((now - i * 86_400_000) / 1000) + _dt.timedelta(hours=9)
            k = d.strftime("%Y-%m-%d")
            daily_series.append({
                "date": k,
                "active": len(daily_active.get(k, set())),
                "calls": daily_calls.get(k, 0),
            })

        # ── 협업 / 모집 / 팀원 신호 (Network) ──
        collab_total = con.execute(
            "SELECT COUNT(*) FROM shared_sites WHERE created_at_ms >= ?", (cutoff,),
        ).fetchone()[0]
        collab_accepted = con.execute(
            "SELECT COUNT(*) FROM shared_sites WHERE created_at_ms >= ? AND status = 'accepted'",
            (cutoff,),
        ).fetchone()[0]
        collab_completed = con.execute(
            "SELECT COUNT(*) FROM shared_sites WHERE created_at_ms >= ? AND progress = 'completed'",
            (cutoff,),
        ).fetchone()[0]
        recruit_total = con.execute(
            "SELECT COUNT(*) FROM recruits WHERE created_at_ms >= ?", (cutoff,),
        ).fetchone()[0]
        recruit_apps = con.execute(
            "SELECT COUNT(*) FROM recruit_applications WHERE invited_at_ms >= ?", (cutoff,),
        ).fetchone()[0]
        team_members_total = con.execute(
            "SELECT COUNT(*) FROM team_members WHERE removed_at_ms IS NULL",
        ).fetchone()[0]
        photos_total = con.execute(
            "SELECT COUNT(*) FROM team_site_photos WHERE uploaded_at_ms >= ?", (cutoff,),
        ).fetchone()[0]

        # ── LLM 비용 (전체 — llm_usage_log) ──
        cost_30d = con.execute(
            "SELECT COALESCE(SUM(cost_krw),0), COUNT(*) FROM llm_usage_log WHERE timestamp_ms >= ?",
            (cutoff,),
        ).fetchone()
        cost_all = con.execute(
            "SELECT COALESCE(SUM(cost_krw),0), COUNT(*) FROM llm_usage_log",
        ).fetchone()

        # ── 카드 drill-down 상세 (최근 30개씩) ──
        def _fmt_dt(ms):
            if not ms: return ""
            try:
                dt = _dt.datetime.utcfromtimestamp(ms / 1000) + _dt.timedelta(hours=9)
                return dt.strftime("%m/%d %H:%M")
            except Exception:
                return ""
        # 협업 요청 (전체)
        rows = con.execute(
            """
            SELECT share_id, owner_phone, partner_phone, title, scheduled_at_ms,
                   status, progress, daily_wage, time_label_raw, owner_name_raw, created_at_ms
            FROM shared_sites WHERE created_at_ms >= ?
            ORDER BY created_at_ms DESC LIMIT 30
            """, (cutoff,),
        ).fetchall()
        collab_details = [
            {
                "owner_name": (r[9] or "").strip() or _user_name(r[1], "사장님"),
                "owner_phone": _fmt_phone(r[1]),
                "partner_name": _user_name(r[2], "협업 사장"),
                "partner_phone": _fmt_phone(r[2]),
                "title": r[3] or "",
                "scheduled": _fmt_dt(r[4]) or (r[8] or ""),
                "status": r[5],
                "progress": r[6],
                "daily_wage": r[7],
                "created": _fmt_dt(r[10]),
            } for r in rows
        ]
        collab_accepted_details = [d for d in collab_details if d["status"] == "accepted"][:30]
        collab_completed_details = [d for d in collab_details if d["progress"] == "completed"][:30]

        # 모집 공고
        rows = con.execute(
            """
            SELECT recruit_id, owner_phone, date_ms, place, work, daily_wage, status, created_at_ms
            FROM recruits WHERE created_at_ms >= ?
            ORDER BY created_at_ms DESC LIMIT 30
            """, (cutoff,),
        ).fetchall()
        recruit_details = [
            {
                "owner_name": _user_name(r[1], "사장님"),
                "owner_phone": _fmt_phone(r[1]),
                "date": _fmt_dt(r[2]),
                "place": r[3] or "",
                "work": r[4] or "",
                "daily_wage": r[5],
                "status": r[6],
                "created": _fmt_dt(r[7]),
            } for r in rows
        ]

        # 모집 지원
        rows = con.execute(
            """
            SELECT r.recruit_id, ra.partner_phone, ra.invited_at_ms, ra.applied_at_ms,
                   ra.status, r.place, r.work, r.daily_wage
            FROM recruit_applications ra
            JOIN recruits r ON r.recruit_id = ra.recruit_id
            WHERE ra.invited_at_ms >= ? AND ra.applied_at_ms IS NOT NULL
            ORDER BY ra.applied_at_ms DESC LIMIT 30
            """, (cutoff,),
        ).fetchall()
        recruit_app_details = [
            {
                "partner_name": _user_name(r[1], "협업 사장"),
                "partner_phone": _fmt_phone(r[1]),
                "applied": _fmt_dt(r[3]),
                "status": r[4],
                "place": r[5] or "",
                "work": r[6] or "",
                "daily_wage": r[7],
            } for r in rows
        ]

        # 팀원 등록 (활성)
        rows = con.execute(
            """
            SELECT member_id, owner_phone, name, phone, role, created_at_ms
            FROM team_members WHERE removed_at_ms IS NULL
            ORDER BY created_at_ms DESC LIMIT 30
            """
        ).fetchall()
        team_details = [
            {
                "owner_name": _user_name(r[1], "사장님"),
                "owner_phone": _fmt_phone(r[1]),
                "name": r[2] or "",
                "phone": _fmt_phone(r[3]),
                "role": r[4] or "worker",
                "created": _fmt_dt(r[5]),
            } for r in rows
        ]

        # 현장 사진
        rows = con.execute(
            """
            SELECT photo_id, owner_phone, label, customer_phone, share_id, uploaded_at_ms, member_id
            FROM team_site_photos WHERE uploaded_at_ms >= ?
            ORDER BY uploaded_at_ms DESC LIMIT 30
            """, (cutoff,),
        ).fetchall()
        photo_details = [
            {
                "owner_name": _user_name(r[1], "사장님"),
                "owner_phone": _fmt_phone(r[1]),
                "label": r[2] or "",
                "customer_phone": _fmt_phone(r[3]) if r[3] else "",
                "share_id": r[4] or "",
                "uploaded": _fmt_dt(r[5]),
                "uploader": r[6] or "",
            } for r in rows
        ]

        # ── 사용자별 활동 (top 50) ──
        # 각 phone 의 endpoint 별 호출 수 + last_seen + 활성 일수
        # 추가75 (2026-06-29) — 사장님 지적: "지인 2496 = 초기부터 앱 사용, 그치만 활성 일수 2일"
        #   원인: 옛 active_days 는 api_usage (LLM 호출) 기준. 앱은 켰지만 LLM 안 쓴 날 = 카운트 X.
        #   fix: (1) 이름 = "AI 사용 일수" 로 명확화 (2) 진짜 "앱 사용 일수" (app_events 기준) 추가.
        per_user_calls: dict = {}
        per_user_ai_days: dict = {}   # LLM 호출 유니크 날짜 (옛 active_days)
        per_user_cost: dict = {}
        for r in api_rows:
            phone, endpoint, _it, _ot, cost_usd, ts = r
            per_user_calls[phone] = per_user_calls.get(phone, 0) + 1
            per_user_cost[phone] = per_user_cost.get(phone, 0.0) + (cost_usd or 0.0)
            try:
                dt = _dt.datetime.utcfromtimestamp(ts / 1000) + _dt.timedelta(hours=9)
                per_user_ai_days.setdefault(phone, set()).add(dt.strftime("%Y-%m-%d"))
            except Exception:
                pass
        # 추가75 — 진짜 "앱 사용 일수" = app_events 유니크 날짜 (screen_view 등)
        per_user_app_days: dict = {}
        if wl_phones:
            placeholders_ap = ",".join(["?"] * len(wl_phones))
            ap_rows = con.execute(
                f"""SELECT owner_phone, created_at_ms FROM app_events
                    WHERE owner_phone IN ({placeholders_ap}) AND created_at_ms >= ?""",
                wl_phones + [cutoff],
            ).fetchall()
            for ar_row in ap_rows:
                try:
                    dt = _dt.datetime.utcfromtimestamp(ar_row[1] / 1000) + _dt.timedelta(hours=9)
                    per_user_app_days.setdefault(ar_row[0], set()).add(dt.strftime("%Y-%m-%d"))
                except Exception:
                    pass

        # 추가84 (2026-07-03) — 등급 4단계 (사장님 설계):
        #   applicant = 등업대기 (beta_signups 만) / tester = 베타 테스터 (whitelist, 무료)
        #   standard = 일반 사장님 (5만) / premium = 특별 사장님 (10만)
        # subscribers 활성 행 기준: 10만+ 또는 premium tier → premium, 유료(>0) → standard.
        sub_map: dict = {}
        for sr in con.execute(
            "SELECT phone, plan_tier, monthly_price_krw, churned_at_ms, started_at_ms "
            "FROM subscribers"
        ).fetchall():
            sub_map[sr[0]] = {"tier": sr[1] or "", "price": sr[2] or 0,
                              "churned": sr[3], "started": sr[4]}

        # 추가85 (2026-07-03) — 결제 주기 계산 (사장님: 결제일 + 재결제 D-00).
        # 결제일 앵커 = 구독 시작일의 '일(day)'. 매달 그 날 재결제로 간주.
        # (토스 실결제 붙으면 이 계산이 실제 빌링 날짜로 대체 — 응답 인터페이스는 동일 유지)
        import calendar as _cal

        def _billing_info(started_ms):
            if not started_ms:
                return None
            start = _dt.datetime.fromtimestamp(started_ms / 1000, tz=_KST)
            today = _dt.datetime.fromtimestamp(now / 1000, tz=_KST)
            anchor_day = start.day
            y, m = today.year, today.month
            nxt = None
            for _ in range(2):
                last_day = _cal.monthrange(y, m)[1]
                day = min(anchor_day, last_day)
                cand = _dt.datetime(y, m, day, tzinfo=_KST)
                if cand.date() >= today.date():
                    nxt = cand
                    break
                m += 1
                if m == 13:
                    m, y = 1, y + 1
            if nxt is None:
                return None
            d_day = (nxt.date() - today.date()).days
            months_sub = (today.year - start.year) * 12 + (today.month - start.month)
            if today.day < start.day:
                months_sub -= 1
            return {
                "anchor_day": anchor_day,
                "next_billing_ms": int(nxt.timestamp() * 1000),
                "d_day": d_day,
                "months_subscribed": max(0, months_sub),
            }

        def _grade_of(phone: str) -> str:
            s = sub_map.get(phone)
            if s and s["churned"] is None:
                if s["price"] >= 100_000 or s["tier"] == "premium_100k":
                    return "premium"
                if s["price"] > 0 or s["tier"] in ("standard_50k", "pro"):
                    return "standard"
            return "tester"

        users = []
        for r in wl_rows:
            # 추가50 — owner_trade / 추가86 — free_until_ms 컬럼 (9개). unpack 도 9개로.
            phone, name, memo, added, first, last, uc, _ot, _fu = r
            calls = per_user_calls.get(phone, 0)
            ai_days = len(per_user_ai_days.get(phone, set()))
            app_days = len(per_user_app_days.get(phone, set()))
            # 옛 이름 유지 (안드로이드/앞 호환): active_days = AI 사용 일수 (LLM)
            active_days = ai_days
            # 평균 일일 호출 (AI 사용 날 기준 — 안 쓴 날 빼고)
            avg_per_active_day = round(calls / ai_days, 1) if ai_days > 0 else 0
            # 평균 일일 호출 (전체 기간 기준 — 가입 이후)
            days_since_added = max(1, int((now - (added or now)) / 86_400_000))
            days_observed = min(days_since_added, days)
            avg_per_day = round(calls / days_observed, 2) if days_observed > 0 else 0
            users.append({
                "phone": _fmt_phone(phone),
                "phone_raw": phone,
                "name": name or "",
                # 추가50 (2026-06-21) — 앱 onboarding 업종 우선, 폴백으로 모집 폼 industry
                "industry": (wl_trade_map.get(phone) or industry_map.get(phone, "")),
                "industry_source": ("app" if wl_trade_map.get(phone) else ("signup" if industry_map.get(phone) else "")),
                "memo": memo or "",
                "added_at_ms": added,
                "first_seen_ms": first,
                "last_seen_ms": last,
                "use_count": uc or 0,
                "calls": calls,
                "active_days": active_days,   # 옛 호환 (= ai_days)
                "ai_days": ai_days,           # 추가75 — AI 사용 일수 (명확한 이름)
                "app_days": app_days,         # 추가75 — 진짜 앱 사용 일수 (app_events 기준)
                "avg_per_day": avg_per_day,
                "avg_per_active_day": avg_per_active_day,
                "cost_usd": round(per_user_cost.get(phone, 0.0), 4),
                "grade": _grade_of(phone),  # 추가84
            })
            # 추가85 — 유료 회원이면 결제 주기 정보 부착
            if users[-1]["grade"] in ("standard", "premium"):
                _s = sub_map.get(phone) or {}
                users[-1]["billing"] = _billing_info(_s.get("started"))
                users[-1]["price_krw"] = _s.get("price") or 0
            else:
                users[-1]["billing"] = None
                users[-1]["price_krw"] = 0
            users[-1]["free_until_ms"] = _fu  # 추가86 — 무료 체험 만료 시각
        # 추가84 — 등업대기자 (beta_signups 에만 있고 whitelist 에 없는 신청자) 도 명단에 포함
        wl_set = set(wl_phones)
        signup_rows = con.execute(
            """SELECT phone, business_name, industry, note, created_at_ms, status
               FROM beta_signups ORDER BY created_at_ms DESC"""
        ).fetchall()
        for sg in signup_rows:
            if sg[0] in wl_set:
                continue
            users.append({
                "phone": _fmt_phone(sg[0]),
                "phone_raw": sg[0],
                "name": sg[1] or "",
                "industry": sg[2] or "",
                "industry_source": "signup",
                "memo": ("[신청 " + (sg[5] or "?") + "] " + (sg[3] or ""))[:120],
                "added_at_ms": sg[4],
                "first_seen_ms": None,
                "last_seen_ms": None,
                "use_count": 0,
                "calls": 0,
                "active_days": 0, "ai_days": 0, "app_days": 0,
                "avg_per_day": 0, "avg_per_active_day": 0,
                "cost_usd": 0.0,
                "grade": "applicant",
                "billing": None, "price_krw": 0,
                "free_until_ms": None,
            })
        # 정렬: 마지막 활동 최근순
        users.sort(key=lambda u: u["last_seen_ms"] or 0, reverse=True)

        # 추가85 — 💳 구독 관제 (사장님 요청 + cowork 판단으로 딥하게):
        #   유료회원 수(등급별) / 이번 달 입금액(도래분) / 다음 달 예상(활성 합) /
        #   재결제 D-7 임박 명단 / 신규 유료·해지 (이번 달) / 유료 휴면(돈 내는데 안 씀 = 이탈 위험).
        wl_last_map = {r[0]: r[5] for r in wl_rows}
        today_kst = _dt.datetime.fromtimestamp(now / 1000, tz=_KST)
        month_start_ms = int(_dt.datetime(
            today_kst.year, today_kst.month, 1, tzinfo=_KST).timestamp() * 1000)
        paid_items = []
        month_collected = 0
        for p, s in sub_map.items():
            if s["churned"] is not None or (s["price"] or 0) <= 0:
                continue
            bi = _billing_info(s["started"])
            grade_p = _grade_of(p)
            item = {
                "phone": _fmt_phone(p), "phone_raw": p,
                "name": _user_name(p, default=""),
                "grade": grade_p, "price_krw": s["price"],
                "billing": bi,
                "last_seen_ms": wl_last_map.get(p),
            }
            paid_items.append(item)
            # 이번 달 입금액 — 이번 달 결제일이 이미 지났으면(당일 포함) 입금된 것으로 간주
            if bi:
                last_day_cm = _cal.monthrange(today_kst.year, today_kst.month)[1]
                bill_day_cm = min(bi["anchor_day"], last_day_cm)
                bill_ms_cm = int(_dt.datetime(
                    today_kst.year, today_kst.month, bill_day_cm, tzinfo=_KST).timestamp() * 1000)
                if bill_ms_cm <= now and (s["started"] or 0) <= bill_ms_cm + 86_400_000:
                    month_collected += s["price"]
        due_soon = sorted(
            [it for it in paid_items if it["billing"] and it["billing"]["d_day"] <= 7],
            key=lambda it: it["billing"]["d_day"])
        paid_dormant = [
            it for it in paid_items
            if not it["last_seen_ms"] or (now - it["last_seen_ms"]) > 7 * 86_400_000
        ]
        churned_this_month = sum(
            1 for s in sub_map.values()
            if s["churned"] is not None and s["churned"] >= month_start_ms)
        new_paid_this_month = sum(
            1 for s in sub_map.values()
            if s["churned"] is None and (s["price"] or 0) > 0
            and (s["started"] or 0) >= month_start_ms)
        subscription = {
            "paid_total": len(paid_items),
            "paid_standard": sum(1 for it in paid_items if it["grade"] == "standard"),
            "paid_premium": sum(1 for it in paid_items if it["grade"] == "premium"),
            "month_collected_krw": month_collected,
            "next_month_expected_krw": sum(it["price_krw"] for it in paid_items),
            "due_7d_count": len(due_soon),
            "new_paid_this_month": new_paid_this_month,
            "churned_this_month": churned_this_month,
            "due_soon": due_soon[:20],
            "paid_dormant": paid_dormant[:20],
        }

        # 추가89 — 🌱 정착 (시공일 등록) + 📱 자주 쓰는 화면 (adoption 페이지 흡수)
        sc_rows89 = con.execute(
            "SELECT owner_phone, COUNT(*) FROM app_events "
            "WHERE event_name = 'schedule_create' GROUP BY owner_phone"
        ).fetchall()
        sc_map89 = {r[0]: r[1] for r in sc_rows89}
        settled_phones = [p for p in wl_phones if sc_map89.get(p)]
        unsettled_phones = [p for p in wl_phones if not sc_map89.get(p)]
        adoption = {
            "settled": len(settled_phones),
            "total": len(wl_phones),
            "rate_pct": round(len(settled_phones) / len(wl_phones) * 100, 1) if wl_phones else 0,
            "unsettled": [
                {
                    "phone": _fmt_phone(p), "phone_raw": p,
                    "name": _user_name(p, default="-"),
                    "last": wl_last_map.get(p),
                }
                for p in unsettled_phones
            ][:30],
        }

        def _norm_screen89(s):
            if not s:
                return ""
            base = str(s).split("?")[0].split("&")[0].strip().rstrip("/")
            if "/" in base:
                segs = [x for x in base.split("/") if x and not (x.startswith("{") and x.endswith("}"))]
                base = segs[-1] if segs else base.rsplit("/", 1)[-1]
            return base.lower()

        scr_rows89 = con.execute(
            "SELECT screen, owner_phone FROM app_events "
            "WHERE event_name = 'screen_view' AND created_at_ms >= ?",
            (cutoff,),
        ).fetchall()
        scr_agg89: dict = {}
        for s89, p89 in scr_rows89:
            nrm = _norm_screen89(s89)
            if not nrm:
                continue
            scr_agg89.setdefault(nrm, {})
            scr_agg89[nrm][p89] = scr_agg89[nrm].get(p89, 0) + 1

        # 추가90 — 기능 발견율 (사장님: "Top 화면 순위는 뻔한 결과" → "묻힌 기능 찾기"로 교체).
        # 통과 화면(홈/채팅/로그인/온보딩/설정)은 제외. 카탈로그 기반이라
        # 조회 0건인 기능도 "0명"으로 드러남 — 그게 진짜 묻힌 기능.
        _FEATURE_CATALOG = [
            ("schedule",  "일정 (시공일)",  {"schedule"}),
            ("intake",    "접수서",        {"intake_form", "intake"}),
            ("customer",  "고객 상세",      {"customer", "customer_detail", "customers"}),
            ("collab",    "협업현장",       {"collab", "collab_sites", "collab_inbox"}),
            ("settlement","정산",          {"settlement"}),
            ("stats",     "통계",          {"stats"}),
            ("pricing",   "가격표",        {"pricing_items", "pricing"}),
            ("templates", "템플릿",        {"templates"}),
            ("notebook",  "노트",          {"notebook"}),
            ("team",      "팀원",          {"team"}),
            ("report",    "리포트",        {"report"}),
            ("search",    "검색",          {"search"}),
        ]
        feature_discovery = []
        for _fk, _fl, _aliases in _FEATURE_CATALOG:
            pm_all: dict = {}
            for _al in _aliases:
                for _p, _cnt in (scr_agg89.get(_al) or {}).items():
                    pm_all[_p] = pm_all.get(_p, 0) + _cnt
            _users = len(pm_all)
            feature_discovery.append({
                "key": _fk, "label": _fl,
                "users": _users,
                "users_pct": round(_users / len(wl_phones) * 100) if wl_phones else 0,
                "total": sum(pm_all.values()),
            })
        feature_discovery.sort(key=lambda x: (-x["users"], -x["total"]))
        adoption["feature_discovery"] = feature_discovery

        # 추가93 — 🩺 시스템 건강 (에러 미들웨어 기록 집계)
        today_start_ms = int(_dt.datetime(
            today_kst.year, today_kst.month, today_kst.day, tzinfo=_KST).timestamp() * 1000)
        err_today = con.execute(
            "SELECT COUNT(*) FROM system_errors WHERE ts_ms >= ?", (today_start_ms,)
        ).fetchone()[0]
        err_24h = con.execute(
            "SELECT COUNT(*) FROM system_errors WHERE ts_ms >= ?", (now - 86_400_000,)
        ).fetchone()[0]
        recent_errs = con.execute(
            "SELECT ts_ms, path, status, detail FROM system_errors "
            "ORDER BY ts_ms DESC LIMIT 10"
        ).fetchall()
        system_health = {
            "today_errors": err_today,
            "errors_24h": err_24h,
            "recent": [
                {"ts_ms": r[0], "path": r[1], "status": r[2], "detail": r[3] or ""}
                for r in recent_errs
            ],
        }

        # 추가94 — 주간 추세 (이번 주 vs 지난주 — "사진 → 동영상")
        wk_ms = 7 * 86_400_000
        w1, w2 = now - wk_ms, now - 2 * wk_ms
        _ph = ",".join(["?"] * len(wl_phones)) if wl_phones else ""

        def _active_between(a_ms, b_ms):
            if not wl_phones:
                return 0
            return con.execute(
                f"SELECT COUNT(DISTINCT owner_phone) FROM app_events "
                f"WHERE owner_phone IN ({_ph}) AND created_at_ms >= ? AND created_at_ms < ?",
                (*wl_phones, a_ms, b_ms),
            ).fetchone()[0]

        def _sincere_between(a_ms, b_ms):
            if not wl_phones:
                return 0
            return con.execute(
                f"SELECT COUNT(*) FROM (SELECT phone FROM api_usage "
                f"WHERE phone IN ({_ph}) AND created_at_ms >= ? AND created_at_ms < ? "
                f"GROUP BY phone HAVING COUNT(*) >= 5)",
                (*wl_phones, a_ms, b_ms),
            ).fetchone()[0]

        _wl_set94 = set(wl_phones)
        first_sc94 = con.execute(
            "SELECT owner_phone, MIN(created_at_ms) FROM app_events "
            "WHERE event_name = 'schedule_create' GROUP BY owner_phone"
        ).fetchall()
        trends = {
            "active": {"cur": _active_between(w1, now), "prev": _active_between(w2, w1)},
            "sincere": {"cur": _sincere_between(w1, now), "prev": _sincere_between(w2, w1)},
            "new": {
                "cur": sum(1 for r in wl_rows if r[3] and r[3] >= w1),
                "prev": sum(1 for r in wl_rows if r[3] and w2 <= r[3] < w1),
            },
            "settled": {
                "cur": sum(1 for r in first_sc94 if r[0] in _wl_set94 and r[1] >= w1),
                "prev": sum(1 for r in first_sc94 if r[0] in _wl_set94 and w2 <= r[1] < w1),
            },
        }

        # 전체 평균 (KPI 카드용)
        active_users = [u for u in users if u["calls"] > 0]
        if active_users:
            avg_calls_per_user_per_day = round(
                sum(u["calls"] for u in active_users) / len(active_users) / days, 2
            )
            # 추가75+ (2026-06-29) — "AI 사용일" 헷갈림 지적. "평균 앱 사용 일수" 로 통일.
            # app_days (app_events 기준 = 앱 진짜 켠 날 수) 평균으로 계산.
            avg_active_days_per_user = round(
                sum(u["app_days"] for u in active_users) / len(active_users), 1
            )
            avg_use_count = round(
                sum(u["use_count"] for u in active_users) / len(active_users), 1
            )
        else:
            avg_calls_per_user_per_day = 0
            avg_active_days_per_user = 0
            avg_use_count = 0

    feature_list = []
    for ep, cnt in feature_count.items():
        feature_list.append({
            "endpoint": ep,
            "label": feature_label.get(ep, ep),
            "count": cnt,
            "cost_usd": round(feature_cost.get(ep, 0.0), 4),
        })
    feature_list.sort(key=lambda f: f["count"], reverse=True)

    return {
        "days": days,
        "generated_at_ms": now,
        "subscription": subscription,     # 추가85 — 💳 구독 관제
        "adoption": adoption,             # 추가89 — 🌱 정착 + 📱 화면 Top
        "system_health": system_health,   # 추가93 — 🩺 시스템 건강
        "trends": trends,                 # 추가94 — 주간 추세
        "kpi": {
            "total_users": total_users,
            "activated": activated,
            "new_7d": new_7d,
            "active_7d": active_7d,
            "active_30d": active_30d,
            "total_api_calls": len(api_rows),
            "avg_calls_per_user_per_day": avg_calls_per_user_per_day,
            "avg_active_days_per_user": avg_active_days_per_user,
            "avg_use_count": avg_use_count,
        },
        "network": {
            "collab_total": collab_total,
            "collab_accepted": collab_accepted,
            "collab_completed": collab_completed,
            "recruit_total": recruit_total,
            "recruit_apps": recruit_apps,
            "team_members": team_members_total,
            "photos": photos_total,
        },
        "cost": {
            "period_krw": round(cost_30d[0] or 0, 0),
            "period_calls": cost_30d[1],
            "all_krw": round(cost_all[0] or 0, 0),
            "all_calls": cost_all[1],
        },
        "daily_series": daily_series,
        "feature_usage": feature_list,
        "users": users,
        "details": {
            "collab_total": collab_details,
            "collab_accepted": collab_accepted_details,
            "collab_completed": collab_completed_details,
            "recruit_total": recruit_details,
            "recruit_apps": recruit_app_details,
            "team_members": team_details,
            "photos": photo_details,
        },
    }


_BETA_DASHBOARD_HTML = """<!doctype html>
<html lang="ko"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>베타 종합 대시보드 — RING-GO</title>
<style>
  :root { --blue:#3182F6; --blue-dark:#1B64DA; --blue-tint:#EEF4FF;
          --bg:#F4F5F7; --card:#fff;
          --t1:#0B0F19; --t2:#5A6472; --t3:#9AA3AF; --line:#EEF0F3;
          --error:#F0436A; --success:#16C172; --warning:#F59E0B;
          --shadow:0 1px 3px rgba(0,0,0,.04); }
  * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
  body { margin:0; background:var(--bg); font-family:'Pretendard',-apple-system,system-ui,sans-serif;
         color:var(--t1); line-height:1.5; }
  .wrap { max-width:1200px; margin:0 auto; padding:20px 18px 60px; }
  .head { display:flex; align-items:center; justify-content:space-between; margin-bottom:6px; }
  h1 { font-size:24px; font-weight:800; margin:0; }
  .sub { font-size:13px; color:var(--t2); margin-bottom:22px; }
  .toolbar { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:18px; }
  .toolbar button { background:#fff; border:1.5px solid var(--line); padding:8px 14px;
                    border-radius:9px; font-size:13px; font-weight:700; cursor:pointer;
                    color:var(--t2); font-family:inherit; }
  .toolbar button.active { background:var(--blue); color:#fff; border-color:var(--blue); }
  .gchip { background:#fff; border:1.5px solid var(--line); padding:6px 12px; border-radius:999px;
           font-size:12px; font-weight:700; cursor:pointer; color:var(--t2); font-family:inherit; }
  .gchip.active { background:var(--blue); color:#fff; border-color:var(--blue); }
  /* 추가92 — 탭 (계기판 아래 3층) */
  .tabbtn { background:none; border:0; border-bottom:3px solid transparent; padding:10px 16px 8px;
            font-size:14.5px; font-weight:800; color:var(--t3); cursor:pointer; font-family:inherit;
            margin-bottom:-2px; }
  .tabbtn.active { color:var(--blue-dark); border-bottom-color:var(--blue); }
  th.sortable { cursor:pointer; user-select:none; }
  th.sortable:hover { color:var(--blue); }
  .kpi-grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(160px, 1fr)); gap:12px;
              margin-bottom:18px; }
  .kpi { background:var(--card); border-radius:12px; padding:14px 16px; box-shadow:var(--shadow); }
  .kpi .lbl { font-size:11.5px; color:var(--t3); font-weight:700; text-transform:uppercase; letter-spacing:0.3px; }
  .kpi .val { font-size:28px; font-weight:800; color:var(--t1); margin-top:2px; }
  .kpi .sub2 { font-size:11.5px; color:var(--t2); margin-top:2px; }
  .kpi.blue .val { color:var(--blue-dark); }
  .kpi.green .val { color:var(--success); }
  .kpi.orange .val { color:var(--warning); }
  .row { display:grid; grid-template-columns:2fr 1fr; gap:16px; margin-bottom:18px; }
  @media (max-width:760px) { .row { grid-template-columns:1fr; } }
  .card { background:var(--card); border-radius:14px; padding:18px; box-shadow:var(--shadow); }
  .card h2 { font-size:14px; font-weight:800; margin:0 0 14px; color:var(--t1); }
  .chart-box { height:220px; }
  .feat-row { display:flex; align-items:center; gap:10px; margin-bottom:9px; font-size:13px; }
  .feat-row .name { flex:0 0 130px; color:var(--t1); font-weight:600; }
  .feat-row .bar { flex:1; height:10px; background:var(--bg); border-radius:5px; overflow:hidden; }
  .feat-row .bar .fill { height:100%; background:linear-gradient(90deg, var(--blue), var(--blue-dark)); }
  .feat-row .num { flex:0 0 60px; text-align:right; color:var(--t2); font-weight:700; font-size:12px; }
  .net-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:10px; }
  .net-item { padding:10px; background:var(--bg); border-radius:10px; cursor:pointer;
              transition:background .15s, transform .1s; }
  .net-item:hover { background:#E8EEF7; }
  .net-item:active { transform:scale(0.98); }
  .net-item.empty { cursor:default; opacity:.4; }
  .net-item.empty:hover { background:var(--bg); }
  .net-item .lbl { font-size:11px; color:var(--t3); font-weight:700; }
  .net-item .val { font-size:18px; font-weight:800; color:var(--blue-dark); margin-top:2px; }

  /* drill-down 모달 */
  .modal { position:fixed; inset:0; background:rgba(0,0,0,.55); display:none;
           align-items:center; justify-content:center; z-index:60; padding:20px; }
  .modal.show { display:flex; }
  .modal-box { background:#fff; border-radius:16px; max-width:780px; width:100%;
               max-height:85vh; overflow:hidden; display:flex; flex-direction:column;
               box-shadow:0 20px 60px rgba(0,0,0,.3); }
  .modal-head { display:flex; align-items:center; justify-content:space-between;
                padding:18px 20px 14px; border-bottom:1px solid var(--line); }
  .modal-head h3 { margin:0; font-size:17px; font-weight:800; }
  .modal-head .close { background:none; border:0; font-size:22px; color:var(--t3);
                       cursor:pointer; padding:4px 8px; }
  .modal-body { overflow-y:auto; padding:0 4px 14px; }
  .modal-body table { font-size:13px; }
  .modal-body th, .modal-body td { padding:10px 16px; }
  .modal-body .nodata { text-align:center; padding:40px; color:var(--t3); font-size:13px; }
  table { width:100%; border-collapse:collapse; font-size:12.5px; }
  th, td { text-align:left; padding:9px 8px; border-bottom:1px solid var(--line); }
  th { font-size:11px; color:var(--t3); font-weight:700; text-transform:uppercase; }
  td.right { text-align:right; }
  .badge { display:inline-block; padding:2px 7px; border-radius:6px; font-size:11px; font-weight:700; }
  .badge.on { background:#E7F8EF; color:var(--success); }
  .badge.off { background:#FFF2F5; color:var(--error); }
  .badge.cool { background:#FFF8E1; color:#7A5A00; }
  #tokenModal { position:fixed; inset:0; background:rgba(0,0,0,.6); display:none;
                align-items:center; justify-content:center; z-index:50; }
  #tokenModal.show { display:flex; }
  #tokenModal .box { background:#fff; border-radius:14px; padding:24px; max-width:90vw; width:360px; }
  #tokenModal input { width:100%; border:1.5px solid var(--line); border-radius:10px; padding:12px;
                      font-size:14px; font-family:inherit; margin:10px 0; }
  #tokenModal button { width:100%; background:var(--blue); color:#fff; border:0; border-radius:10px;
                       padding:12px; font-size:14px; font-weight:800; cursor:pointer; font-family:inherit; }
</style></head>
<body>
<div id="tokenModal"><div class="box">
  <h3 style="margin:0 0 6px">관리자 인증</h3>
  <p style="font-size:13px; color:#5A6472; margin:0">ADMIN_TOKEN 을 입력하세요. 브라우저에 저장됩니다.</p>
  <input id="tokenInput" type="password" placeholder="토큰">
  <button onclick="saveToken()">시작</button>
</div></div>

<div class="wrap">
  <a href="/admin" style="display:inline-block; font-size:13px; color:#3182F6; text-decoration:none; margin-bottom:6px;">← admin 홈</a>
  <div class="head">
    <div>
      <h1>📊 베타 종합 대시보드</h1>
      <p class="sub">테스터 활동 · 기능 사용 · 네트워크 · 인프라 비용 한눈에</p>
    </div>
    <div style="font-size:11px; color:var(--t3); text-align:right;">
      <div id="genTime">로딩중...</div>
      <div style="margin-top:2px">시공막내 · RING-GO</div>
    </div>
  </div>

  <div class="toolbar">
    <button data-days="7" onclick="setPeriod(7)">최근 7일</button>
    <button data-days="30" class="active" onclick="setPeriod(30)">최근 30일</button>
    <button data-days="90" onclick="setPeriod(90)">최근 90일</button>
    <button data-days="365" onclick="setPeriod(365)">최근 1년</button>
    <button onclick="load()" style="margin-left:auto">↻ 새로고침</button>
  </div>

  <!-- KPI 카드 -->
  <div class="kpi-grid" id="kpiGrid"></div>

  <!-- 추가92 — 3층 구조 (사장님: "난잡하다"): 계기판(KPI, 상시) + 탭 3개.
       내용물은 그대로, 배치만 층 분리. 마지막 본 탭 기억 (localStorage). -->
  <div id="tabBar" style="display:flex; gap:8px; margin:4px 0 16px; border-bottom:2px solid var(--line); padding-bottom:0;">
    <button class="tabbtn" data-tab="members" onclick="setTab('members')">👥 멤버</button>
    <button class="tabbtn" data-tab="subs" onclick="setTab('subs')">💳 구독</button>
    <button class="tabbtn" data-tab="analytics" onclick="setTab('analytics')">📈 분석</button>
  </div>

  <div id="tab-subs" style="display:none;">
  <!-- 추가85 — 💳 구독 관제 (유료회원 현금흐름 + 이탈 위험) -->
  <div class="card" style="margin-bottom:18px">
    <h2>💳 구독 관제</h2>
    <div class="kpi-grid" id="subKpiGrid" style="margin-bottom:4px"></div>
    <div class="row" style="margin-bottom:0">
      <div>
        <div style="font-size:13px; font-weight:800; margin:8px 0 8px;">⏰ 재결제 임박 (7일 이내) — 이탈 방지 전화 타이밍</div>
        <div id="subDueList" style="font-size:13px;"></div>
      </div>
      <div>
        <div style="font-size:13px; font-weight:800; margin:8px 0 8px;">😴 유료 휴면 — 돈은 내는데 안 씀 (해지 예고 신호)</div>
        <div id="subDormantList" style="font-size:13px;"></div>
      </div>
    </div>
  </div>

  </div><!-- /tab-subs -->

  <div id="tab-analytics" style="display:none;">
  <!-- 일별 활성 라인 차트 + Network 신호 -->
  <div class="row">
    <div class="card">
      <h2>일별 활성 사용자 + API 호출 수</h2>
      <div class="chart-box"><canvas id="dailyChart"></canvas></div>
    </div>
    <div class="card">
      <h2>🤝 Network 신호 (협업·모집·팀)</h2>
      <div class="net-grid" id="netGrid"></div>
    </div>
  </div>

  <!-- 기능 사용 막대 + LLM 비용 -->
  <div class="row">
    <div class="card">
      <h2>📈 기능 사용량 (호출 수)</h2>
      <div id="featList"></div>
    </div>
    <div class="card">
      <h2>💸 LLM 인프라 비용</h2>
      <div id="costBox"></div>
    </div>
  </div>

  <!-- 추가90 — 🔍 기능 발견율 (Top 화면 순위 대체: "뻔한 결과" → "묻힌 기능 찾기") -->
  <div class="card" style="margin-bottom:18px">
    <h2>🔍 기능 발견율 — 묻힌 기능 찾기</h2>
    <div id="screenList"></div>
    <div style="margin-top:8px; font-size:11px; color:#9AA3AF;">
      홈·채팅 같은 통과 화면 제외, 일부러 만든 기능만. 📉 묻힌 기능 = 튜토리얼에 넣거나 · 버튼 위치 옮기거나 · 버리거나.
    </div>
  </div>

  </div><!-- /tab-analytics -->

  <div id="tab-members">
  <!-- 추가83 — 전체 멤버 관리 (옛 화이트리스트 페이지 통합: 검색·정렬·추가·제거 여기서 다) -->
  <div class="card" style="margin-bottom:18px">
    <div style="display:flex; align-items:center; justify-content:space-between; gap:10px; flex-wrap:wrap; margin-bottom:10px;">
      <h2 style="margin:0">👥 전체 멤버 관리 <span id="memberCount" style="color:#3182F6"></span></h2>
      <div style="display:flex; gap:8px; flex:1; max-width:420px; min-width:220px;">
        <input id="memberSearch" type="search" placeholder="이름·전화·업종·메모 검색"
          oninput="renderUsers()"
          style="flex:1; border:1.5px solid var(--line); border-radius:9px; padding:8px 12px; font-size:13px; font-family:inherit;">
        <button onclick="openAddModal()" style="background:var(--blue); color:#fff; border:0; border-radius:9px; padding:8px 14px; font-size:13px; font-weight:800; cursor:pointer; white-space:nowrap; font-family:inherit;">+ 멤버 추가</button>
      </div>
    </div>
    <!-- 추가84 — 등급 필터 칩 (카페 멤버 등급 필터처럼) -->
    <div id="gradeChips" style="display:flex; gap:6px; flex-wrap:wrap; margin-bottom:12px;">
      <button data-grade="" class="gchip active" onclick="setGradeFilter('')">전체</button>
      <button data-grade="applicant" class="gchip" onclick="setGradeFilter('applicant')">⏳ 등업대기</button>
      <button data-grade="tester" class="gchip" onclick="setGradeFilter('tester')">🧪 베타 테스터</button>
      <button data-grade="standard" class="gchip" onclick="setGradeFilter('standard')">💙 일반 사장님 (5만)</button>
      <button data-grade="premium" class="gchip" onclick="setGradeFilter('premium')">👑 특별 사장님 (10만)</button>
    </div>
    <div style="overflow-x:auto">
      <table>
        <thead><tr>
          <th>폰 · 이름</th>
          <th>등급</th>
          <th>업종</th>
          <th>메모</th>
          <th class="sortable" data-sort="added_at_ms" onclick="setSort('added_at_ms')">등록일 ↕</th>
          <th class="sortable" data-sort="last_seen_ms" onclick="setSort('last_seen_ms')">마지막 실행 ↕</th>
          <th class="sortable right" data-sort="app_days" onclick="setSort('app_days')" title="앱을 실제로 켠 유니크 날짜 수">앱 사용일 ↕</th>
          <th class="sortable right" data-sort="use_count" onclick="setSort('use_count')">진입 ↕</th>
          <th class="sortable right" data-sort="calls" onclick="setSort('calls')" title="AI 기능 사용 총 회수">AI 사용 ↕</th>
          <th class="sortable right" data-sort="cost_usd" onclick="setSort('cost_usd')">비용 ↕</th>
          <th>상태</th>
          <th>관리</th>
        </tr></thead>
        <tbody id="userRows"><tr><td colspan="12" style="text-align:center; padding:30px; color:#9AA3AF">로딩중...</td></tr></tbody>
      </table>
    </div>
    <div style="margin-top:8px; font-size:11px; color:#9AA3AF;">
      열 제목(↕) 클릭 = 정렬 · 폰번호 클릭 = 상세 페이지 · [제거] = 화이트리스트에서 삭제 (앱 접근 차단)
    </div>
  </div>
  </div><!-- /tab-members -->
</div>

<!-- 추가83 — 멤버 추가 모달 -->
<div class="modal" id="addModal" onclick="if(event.target.id==='addModal')closeAddModal()">
  <div class="modal-box" style="max-width:420px;">
    <div class="modal-head"><h3>+ 새 멤버 (베타 테스터) 추가</h3>
      <button class="close" type="button" onclick="closeAddModal()">×</button></div>
    <div style="padding:16px 20px 20px;">
      <label style="display:block; font-size:12px; font-weight:700; color:#5A6472; margin:8px 0 4px;">폰번호 *</label>
      <input id="addPhone" type="tel" inputmode="numeric" maxlength="13"
        style="width:100%; border:1.5px solid var(--line); border-radius:10px; padding:11px 12px; font-size:14px; font-family:inherit;">
      <label style="display:block; font-size:12px; font-weight:700; color:#5A6472; margin:10px 0 4px;">이름 (상호)</label>
      <input id="addName" style="width:100%; border:1.5px solid var(--line); border-radius:10px; padding:11px 12px; font-size:14px; font-family:inherit;">
      <label style="display:block; font-size:12px; font-weight:700; color:#5A6472; margin:10px 0 4px;">메모 (모집 경로 등)</label>
      <input id="addMemo" style="width:100%; border:1.5px solid var(--line); border-radius:10px; padding:11px 12px; font-size:14px; font-family:inherit;">
      <button onclick="submitAdd()" style="margin-top:14px; width:100%; background:var(--blue); color:#fff; border:0; border-radius:10px; padding:12px; font-size:14px; font-weight:800; cursor:pointer; font-family:inherit;">추가</button>
      <div id="addMsg" style="margin-top:8px; font-size:13px;"></div>
    </div>
  </div>
</div>

<!-- drill-down 모달 -->
<div class="modal" id="drillModal" onclick="if(event.target.id==='drillModal')closeDrill()">
  <div class="modal-box">
    <div class="modal-head">
      <h3 id="drillTitle">상세</h3>
      <button class="close" type="button" onclick="closeDrill()">×</button>
    </div>
    <div class="modal-body" id="drillBody"></div>
  </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
<script>
  var DAYS = 30;
  var CHART_REF = null;

  function getToken() { return sessionStorage.getItem('admin_token') || ''; }
  function saveToken() {
    var t = document.getElementById('tokenInput').value.trim();
    if (!t) return;
    sessionStorage.setItem('admin_token', t);
    document.getElementById('tokenModal').classList.remove('show');
    load();
  }
  function ensureToken() {
    if (!getToken()) { document.getElementById('tokenModal').classList.add('show'); return false; }
    return true;
  }
  function setPeriod(d) {
    DAYS = d;
    document.querySelectorAll('.toolbar button[data-days]').forEach(function(b){
      b.classList.toggle('active', Number(b.dataset.days) === d);
    });
    load();
  }

  // 추가92 — 탭 전환 (마지막 탭 기억)
  function setTab(name) {
    ['members', 'subs', 'analytics'].forEach(function(t){
      var el = document.getElementById('tab-' + t);
      if (el) el.style.display = (t === name) ? '' : 'none';
    });
    document.querySelectorAll('.tabbtn').forEach(function(b){
      b.classList.toggle('active', b.dataset.tab === name);
    });
    try { localStorage.setItem('ringgo_dash_tab', name); } catch(e) {}
    // 숨겨진 상태에서 그린 차트는 크기가 0 → 분석 탭 열 때 리사이즈
    if (name === 'analytics' && CHART_REF) {
      setTimeout(function(){ try { CHART_REF.resize(); } catch(e) {} }, 60);
    }
  }
  function initTab() {
    var saved = '';
    try { saved = localStorage.getItem('ringgo_dash_tab') || ''; } catch(e) {}
    setTab(['members','subs','analytics'].indexOf(saved) !== -1 ? saved : 'members');
  }
  async function load() {
    if (!ensureToken()) return;
    try {
      var r = await fetch('/admin/beta/dashboard/data?days=' + DAYS, {
        headers: { 'Authorization': 'Bearer ' + getToken() }
      });
      if (r.status === 401) { sessionStorage.removeItem('admin_token'); ensureToken(); return; }
      if (!r.ok) throw new Error('API 오류 ' + r.status);
      var d = await r.json();
      render(d);
    } catch(e) { alert('로드 실패: ' + e.message); }
  }
  function render(d) {
    document.getElementById('genTime').textContent = '데이터 갱신: ' + new Date(d.generated_at_ms).toLocaleString('ko-KR');
    // KPI 카드
    var kpi = d.kpi;
    var pct = function(n, tot) { return tot > 0 ? Math.round(n / tot * 100) : 0; };
    // 추가48 (2026-06-21) — 사용자 유형 분류
    // 추가73 fix (2026-06-29) — 사장님 지적: "진성+구경꾼+안쓰는사람 = 6 ≠ 7명 (누락 1명)".
    //   원인: "7일 활동 + LLM 1~4회 사용자" 가 어디에도 안 속함.
    //   fix: 4분류 mutually exclusive.
    //     안 쓰는 사람 (7일 무활동) → 초심 (활동 + LLM 1-4) → 구경꾼 (활동 + LLM 0) → 진성 (LLM 5+)
    // 추가87 — KPI 12개 → 6개 정리 (사장님: "정리 필요").
    //   원칙: 카드 1 = 질문 1. 분류 기준 = 접속(last_seen) 단일 체계 (모순 제거).
    //   유형 4카드 → 막대 1카드. 사용량 지표(LLM 호출/평균 등) = 사용량 페이지와 중복 → 삭제.
    //   ⏳ 등업대기(신청자)는 멤버 분류에서 제외 (총 멤버 카드의 sub 로만 표시).
    var nowMs = Date.now();
    var allUsers = d.users || [];
    var sevenAgo = nowMs - 7 * 86400000;
    var members = allUsers.filter(function(u){ return (u.grade || 'tester') !== 'applicant'; });
    var applicants = allUsers.length - members.length;
    var paidStd = members.filter(function(u){ return u.grade === 'standard'; }).length;
    var paidPre = members.filter(function(u){ return u.grade === 'premium'; }).length;
    var dead = 0, watcher = 0, beginner = 0, sincere = 0;
    members.forEach(function(u){
      var calls = u.calls || 0;
      var active = u.last_seen_ms && u.last_seen_ms >= sevenAgo;
      if (!active) dead++;
      else if (calls >= 5) sincere++;
      else if (calls === 0) watcher++;
      else beginner++;  // 1~4회 사용자
    });
    // ⚠️ 이탈 위험 명단 (클릭 → 모달)
    var atRiskList = members.filter(function(u){
      return !(u.last_seen_ms && u.last_seen_ms >= sevenAgo);
    }).map(function(u){
      return { name: u.name || '-', phone: u.phone, phone_raw: u.phone_raw,
               last: u.last_seen_ms ? timeAgo(nowMs - u.last_seen_ms) : '접속 기록 없음' };
    });
    var mix = members.length;
    function mixSeg(nv, color) {
      var w = mix > 0 ? (nv / mix * 100) : 0;
      return '<span style="display:inline-block; height:100%; width:' + w.toFixed(1) + '%; background:' + color + ';"></span>';
    }
    var mixCard =
      '<div class="kpi"><div class="lbl">사용자 유형 (멤버 ' + mix + '명)</div>'
      + '<div style="height:14px; border-radius:7px; overflow:hidden; background:var(--bg); margin:10px 0 8px; font-size:0; white-space:nowrap;">'
      + mixSeg(sincere, '#16C172') + mixSeg(beginner, '#3182F6') + mixSeg(watcher, '#F59E0B') + mixSeg(dead, '#F0436A')
      + '</div>'
      + '<div class="sub2">🟢 진성 ' + sincere + ' · 🔵 초심 ' + beginner + ' · 🟡 구경꾼 ' + watcher + ' · 🔴 미접속 ' + dead + '</div>'
      + '</div>';
    // 추가93 — 🩺 시스템 건강 카드 (에러 0 = 초록 한 줄, 있으면 빨강 + 클릭 명단)
    var sh = d.system_health || { today_errors: 0, errors_24h: 0, recent: [] };
    var healthCard = sh.today_errors === 0
      ? '<div class="kpi"><div class="lbl">🩺 시스템</div>'
        + '<div class="val" style="color:var(--success); font-size:20px;">정상</div>'
        + '<div class="sub2">오늘 에러 0건' + (sh.errors_24h > 0 ? ' · 24시간 ' + sh.errors_24h + '건' : '') + '</div></div>'
      : '<div class="kpi" style="cursor:pointer; border:1.5px solid var(--error);" onclick="openDrill(\\'sys_errors\\', \\'🩺 최근 서버 에러\\')">'
        + '<div class="lbl">🩺 시스템</div>'
        + '<div class="val" style="color:var(--error)">에러 ' + sh.today_errors + '건</div>'
        + '<div class="sub2">오늘 발생 · 클릭 = 상세</div></div>';
    var tr = d.trends || {};
    document.getElementById('kpiGrid').innerHTML =
      kpiCard('blue', '총 멤버', members.length + '명',
              '💙 일반 ' + paidStd + ' · 👑 특별 ' + paidPre + ' · ⏳ 등업대기 ' + applicants
              + trendBadge(tr.new && { cur: tr.new.cur, prev: tr.new.prev })) +
      kpiCard('green', '이번 주 활성', kpi.active_7d + '명',
              pct(kpi.active_7d, kpi.total_users) + '% (접속 기준)' + trendBadge(tr.active)) +
      kpiCard('green', '🔥 진성 사용자', sincere,
              'AI 5회+ 사용' + trendBadge(tr.sincere)) +
      '<div class="kpi" style="cursor:pointer;" onclick="openDrill(\\'at_risk\\', \\'⚠️ 이탈 위험 — 7일+ 미접속\\')">'
        + '<div class="lbl">⚠️ 이탈 위험</div>'
        + '<div class="val" style="color:' + (dead > 0 ? 'var(--error)' : 'var(--success)') + '">' + dead + '</div>'
        + '<div class="sub2">7일+ 미접속 · 클릭 = 명단</div></div>' +
      // 추가89 — 🌱 정착률 (시공일 등록 = 사장님 핵심 KPI). 클릭 = 미등록 명단.
      '<div class="kpi" style="cursor:pointer;" onclick="openDrill(\\'unsettled\\', \\'🌱 시공일 미등록 — 전화/튜토리얼 대상\\')">'
        + '<div class="lbl">🌱 정착률 (시공일 등록)</div>'
        + '<div class="val" style="color:' + ((d.adoption && d.adoption.rate_pct >= 50) ? 'var(--success)' : 'var(--warning)') + '">'
        + (d.adoption ? d.adoption.rate_pct : 0) + '%</div>'
        + '<div class="sub2">' + (d.adoption ? d.adoption.settled + '/' + d.adoption.total : '-')
        + '명 등록 · 클릭 = 미등록 명단'
        + trendBadge(tr.settled && { cur: tr.settled.cur, prev: tr.settled.prev }) + '</div></div>' +
      healthCard +
      mixCard;

    // Network 신호 (클릭 시 drill-down)
    LAST_DETAILS = d.details || {};
    LAST_DETAILS['at_risk'] = atRiskList;  // 추가87 — 이탈 위험 명단 (KPI 카드 클릭)
    // 추가89 — 시공일 미등록 명단 (정착률 카드 클릭)
    LAST_DETAILS['unsettled'] = (d.adoption && d.adoption.unsettled || []).map(function(u){
      return { name: u.name, phone: u.phone, phone_raw: u.phone_raw,
               last: u.last ? timeAgo(Date.now() - u.last) : '접속 기록 없음' };
    });
    // 추가93 — 최근 서버 에러 (건강 카드 클릭)
    LAST_DETAILS['sys_errors'] = (sh.recent || []).map(function(e){
      return { t: new Date(e.ts_ms).toLocaleString('ko-KR', { hour12: false }),
               path: e.path || '-', status: e.status, detail: e.detail || '-' };
    });
    var n = d.network;
    // 추가88 — 협업 깔때기 전환율 (요청→수락→완료 가 이야기가 되게) + 기간 명시 + 빈 박스 제거
    var acceptPct = n.collab_total > 0 ? Math.round(n.collab_accepted / n.collab_total * 100) : 0;
    var donePct = n.collab_accepted > 0 ? Math.round(n.collab_completed / n.collab_accepted * 100) : 0;
    var funnelHtml = n.collab_total > 0
      ? '<div style="grid-column:1 / -1; font-size:12px; color:#5A6472; padding:8px 10px; background:#EEF4FF; border-radius:10px;">'
        + '🤝 협업 깔때기: 요청 ' + n.collab_total + ' → 수락 ' + n.collab_accepted
        + ' (<b style="color:' + (acceptPct >= 60 ? '#16C172' : '#F59E0B') + '">' + acceptPct + '%</b>)'
        + ' → 완료 ' + n.collab_completed
        + ' (<b style="color:' + (donePct >= 60 ? '#16C172' : '#F59E0B') + '">' + donePct + '%</b>)'
        + '</div>'
      : '';
    document.getElementById('netGrid').innerHTML =
      funnelHtml +
      netItem('🤝 협업 요청', n.collab_total, 'collab_total') +
      netItem('✓ 협업 수락', n.collab_accepted, 'collab_accepted') +
      netItem('🏁 협업 완료', n.collab_completed, 'collab_completed') +
      netItem('📸 현장 사진', n.photos, 'photos') +
      netItem('📣 모집 공고', n.recruit_total, 'recruit_total') +
      netItem('👋 모집 지원', n.recruit_apps, 'recruit_apps') +
      netItem('👷 팀원 등록', n.team_members, 'team_members') +
      '<div class="net-item empty" style="display:flex; align-items:center; justify-content:center; font-size:11px; color:#9AA3AF;">기간: 최근 ' + d.days + '일</div>';

    // 일별 활성 차트
    if (CHART_REF) CHART_REF.destroy();
    var ctx = document.getElementById('dailyChart').getContext('2d');
    var labels = d.daily_series.map(function(p){ return p.date.slice(5); });
    var actives = d.daily_series.map(function(p){ return p.active; });
    var calls = d.daily_series.map(function(p){ return p.calls; });
    CHART_REF = new Chart(ctx, {
      type: 'line',
      data: {
        labels: labels,
        datasets: [
          { label: '활성 사용자', data: actives, borderColor: '#3182F6', backgroundColor: 'rgba(49,130,246,.1)', tension: 0.3, yAxisID: 'y' },
          { label: 'API 호출 수', data: calls, borderColor: '#16C172', backgroundColor: 'rgba(22,193,114,.1)', tension: 0.3, yAxisID: 'y1' },
        ]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        scales: {
          // 추가88 — 활성 사용자는 사람 수 → 정수 눈금만 (3.5명 금지)
          y: { type: 'linear', position: 'left', title: { display: true, text: '활성' }, beginAtZero: true,
               ticks: { stepSize: 1, precision: 0 } },
          y1: { type: 'linear', position: 'right', title: { display: true, text: 'API' }, beginAtZero: true, grid: { drawOnChartArea: false } },
        },
        plugins: { legend: { labels: { font: { size: 11 } } } },
      }
    });

    // 기능 사용 막대 — 추가88: 자동/수동 구분 (사장님: "많이 불림 ≠ 인기" 착시 제거)
    // 수동 = 사용자가 버튼을 직접 누른 기능. 자동 = 앱이 알아서 부르는 기능.
    var MANUAL_EPS = ['prepare-reply', 'refine', 'extract-pricing', 'pricing-starter',
                      'name-template', 'reply-suggest', 'tone-import'];
    var feat = d.feature_usage;
    if (feat.length === 0) {
      document.getElementById('featList').innerHTML = '<div style="text-align:center; padding:20px; color:#9AA3AF; font-size:13px">아직 사용 데이터 없음</div>';
    } else {
      var USD2KRW = 1450;
      var maxN = feat[0].count;
      function featRow(f) {
        var pct2 = maxN > 0 ? Math.round(f.count / maxN * 100) : 0;
        var costKrw = Math.round((f.cost_usd || 0) * USD2KRW);
        return '<div class="feat-row">'
              + '<span class="name">' + escape(f.label) + '</span>'
              + '<span class="bar"><span class="fill" style="width:' + pct2 + '%; display:block"></span></span>'
              + '<span class="num">' + f.count + '회<br><span style="font-size:10px; color:#9AA3AF; font-weight:400">₩' + costKrw.toLocaleString() + '</span></span>'
              + '</div>';
      }
      var manual = feat.filter(function(f){ return MANUAL_EPS.indexOf(f.endpoint) !== -1; });
      var auto = feat.filter(function(f){ return MANUAL_EPS.indexOf(f.endpoint) === -1; });
      var html = '';
      html += '<div style="font-size:11.5px; font-weight:800; color:#1B64DA; margin-bottom:8px;">🖱 직접 사용 (사장님이 버튼 누른 것 — 진짜 인기 지표)</div>';
      html += manual.length ? manual.map(featRow).join('')
            : '<div style="font-size:12px; color:#9AA3AF; padding:4px 0 8px;">아직 없음</div>';
      html += '<div style="font-size:11.5px; font-weight:800; color:#9AA3AF; margin:14px 0 8px;">⚙️ 자동 실행 (앱이 알아서 부르는 것 — 비용 지표)</div>';
      html += auto.map(featRow).join('');
      document.getElementById('featList').innerHTML = html;
    }

    // LLM 비용 — 추가88: 일평균 + 사용자당 (유료화 마진 계산 직결)
    var c = d.cost;
    var activeMembersCnt = members.filter(function(u){ return (u.calls || 0) > 0; }).length;
    var dailyAvgKrw = d.days > 0 ? c.period_krw / d.days : 0;
    var perUserKrw = activeMembersCnt > 0 ? c.period_krw / activeMembersCnt : 0;
    document.getElementById('costBox').innerHTML =
      '<div style="margin-bottom:12px"><div style="font-size:11.5px; color:#9AA3AF; font-weight:700">기간 (' + d.days + '일)</div>'
      + '<div style="font-size:24px; font-weight:800; color:#1B64DA">' + Math.round(c.period_krw).toLocaleString() + '원</div>'
      + '<div style="font-size:11.5px; color:#5A6472">' + c.period_calls + '회 호출 · 일평균 ' + Math.round(dailyAvgKrw).toLocaleString() + '원</div></div>'
      + '<div style="margin-bottom:12px"><div style="font-size:11.5px; color:#9AA3AF; font-weight:700">사용자당 (' + d.days + '일, 활성 ' + activeMembersCnt + '명)</div>'
      + '<div style="font-size:18px; font-weight:800; color:#0B0F19">' + Math.round(perUserKrw).toLocaleString() + '원</div>'
      + '<div style="font-size:11.5px; color:#5A6472">월 5만원 구독 대비 ' + (perUserKrw > 0 ? (perUserKrw / 50000 * 100).toFixed(1) : '0') + '% 원가</div></div>'
      + '<div><div style="font-size:11.5px; color:#9AA3AF; font-weight:700">누적</div>'
      + '<div style="font-size:18px; font-weight:800; color:#0B0F19">' + Math.round(c.all_krw).toLocaleString() + '원</div>'
      + '<div style="font-size:11.5px; color:#5A6472">' + c.all_calls + '회 호출</div></div>';

    // 추가90 — 🔍 기능 발견율 (발견된 기능 = 막대 / 묻힌 기능 = 빨간 경고 줄)
    var fd = (d.adoption && d.adoption.feature_discovery) || [];
    var scrEl = document.getElementById('screenList');
    if (fd.length === 0) {
      scrEl.innerHTML = '<div style="text-align:center; padding:16px; color:#9AA3AF; font-size:13px">아직 화면 데이터 없음</div>';
    } else {
      var found = fd.filter(function(f){ return f.users >= 2; });
      var buried = fd.filter(function(f){ return f.users <= 1; });
      var html89 = '';
      if (found.length) {
        html89 += '<div style="font-size:11.5px; font-weight:800; color:#16C172; margin-bottom:8px;">📈 발견된 기능 (2명+ 사용)</div>';
        html89 += found.map(function(f){
          return '<div class="feat-row">'
            + '<span class="name">' + escape(f.label) + '</span>'
            + '<span class="bar"><span class="fill" style="width:' + Math.min(100, f.users_pct) + '%; display:block"></span></span>'
            + '<span class="num"><b>' + f.users + '명</b> (' + f.users_pct + '%)<br><span style="font-size:10px; color:#9AA3AF; font-weight:400">' + f.total + '회</span></span>'
            + '</div>';
        }).join('');
      }
      if (buried.length) {
        html89 += '<div style="font-size:11.5px; font-weight:800; color:#F0436A; margin:14px 0 8px;">📉 묻힌 기능 (0~1명) — 만들었는데 못 찾는 것들</div>';
        html89 += '<div style="display:flex; flex-wrap:wrap; gap:6px;">'
          + buried.map(function(f){
              return '<span style="background:#FFF2F5; color:#F0436A; padding:4px 10px; border-radius:999px; font-size:12px; font-weight:700;">'
                + escape(f.label) + ' ' + f.users + '명</span>';
            }).join('')
          + '</div>';
      }
      scrEl.innerHTML = html89;
    }

    // 추가83 — 전체 멤버 관리 (검색·정렬은 renderUsers 가 담당)
    ALL_USERS = d.users || [];
    renderUsers();

    // 추가85 — 💳 구독 관제
    renderSubscription(d.subscription || {});
  }

  // ─── 추가85: 구독 관제 렌더 ───
  function fmtWon(n) { return (n || 0).toLocaleString('ko-KR') + '원'; }
  function ddayBadge(dday) {
    var color = dday <= 3 ? '#F0436A' : dday <= 7 ? '#F59E0B' : '#5A6472';
    var txt = dday === 0 ? 'D-DAY (오늘)' : 'D-' + dday;
    return '<span style="color:' + color + '; font-weight:800;">' + txt + '</span>';
  }
  function renderSubscription(s) {
    var g = document.getElementById('subKpiGrid');
    if (!s || !s.paid_total) {
      g.innerHTML = kpiCard('', '유료회원', '0명', '멤버 관리에서 등급을 올리면 여기가 채워져요');
      document.getElementById('subDueList').innerHTML = '<span style="color:#9AA3AF">-</span>';
      document.getElementById('subDormantList').innerHTML = '<span style="color:#9AA3AF">-</span>';
      return;
    }
    g.innerHTML =
        kpiCard('blue', '유료회원', s.paid_total + '명',
                '💙 일반 ' + s.paid_standard + ' · 👑 특별 ' + s.paid_premium)
      + kpiCard('orange', '재결제 임박 (7일)', s.due_7d_count + '명', '아래 명단 — 미리 챙기기')
      + kpiCard('green', '이번 달 입금액', fmtWon(s.month_collected_krw), '결제일 도래분 합계')
      + kpiCard('green', '다음 달 예상', fmtWon(s.next_month_expected_krw), '현재 구독 유지 가정')
      + kpiCard('', '이번 달 신규 유료', '+' + s.new_paid_this_month + '명', '')
      + kpiCard(s.churned_this_month > 0 ? 'orange' : '', '이번 달 해지', s.churned_this_month + '명', '');
    var due = document.getElementById('subDueList');
    if (!s.due_soon || s.due_soon.length === 0) {
      due.innerHTML = '<span style="color:#9AA3AF">7일 안에 재결제 도래 없음</span>';
    } else {
      due.innerHTML = s.due_soon.map(function(it){
        var b = it.billing || {};
        return '<div style="display:flex; justify-content:space-between; gap:8px; padding:7px 0; border-bottom:1px solid var(--line);">'
          + '<span><a href="/admin/user/' + encodeURIComponent(it.phone_raw) + '" style="color:#3182F6; text-decoration:none; font-weight:700;">' + escape(it.name || it.phone) + '</a>'
          + ' <span style="font-size:11px; color:#9AA3AF">' + it.phone + '</span></span>'
          + '<span>' + ddayBadge(b.d_day) + ' <span style="font-size:11px; color:#5A6472">매월 ' + b.anchor_day + '일 · ' + fmtWon(it.price_krw) + '</span></span>'
          + '</div>';
      }).join('');
    }
    var dor = document.getElementById('subDormantList');
    if (!s.paid_dormant || s.paid_dormant.length === 0) {
      dor.innerHTML = '<span style="color:#9AA3AF">유료 휴면 없음 — 모두 잘 쓰고 있어요 👍</span>';
    } else {
      dor.innerHTML = s.paid_dormant.map(function(it){
        var lastTxt = it.last_seen_ms ? '마지막 접속 ' + timeAgo(Date.now() - it.last_seen_ms) : '접속 기록 없음';
        return '<div style="display:flex; justify-content:space-between; gap:8px; padding:7px 0; border-bottom:1px solid var(--line);">'
          + '<span><a href="/admin/user/' + encodeURIComponent(it.phone_raw) + '" style="color:#3182F6; text-decoration:none; font-weight:700;">' + escape(it.name || it.phone) + '</a>'
          + ' <span style="font-size:11px; color:#9AA3AF">' + it.phone + '</span></span>'
          + '<span style="color:#F0436A; font-size:12px; font-weight:700;">' + lastTxt + '</span>'
          + '</div>';
      }).join('');
    }
  }

  // ─── 추가83/84: 전체 멤버 관리 (검색 + 등급 필터 + 정렬 + 추가/제거 + 등급 변경) ───
  var ALL_USERS = [];
  var SORT_KEY = 'last_seen_ms';
  var SORT_DESC = true;
  var GRADE_FILTER = '';
  var GRADE_META = {
    applicant: { label: '⏳ 등업대기', bg: '#FFF8E1', fg: '#7A5A00' },
    tester:    { label: '🧪 베타 테스터', bg: '#EEF4FF', fg: '#1B64DA' },
    standard:  { label: '💙 일반 사장님', bg: '#E7F8EF', fg: '#0B7A45' },
    premium:   { label: '👑 특별 사장님', bg: '#F3E8FF', fg: '#7C3AED' },
  };
  function setGradeFilter(g) {
    GRADE_FILTER = g;
    document.querySelectorAll('#gradeChips .gchip').forEach(function(b){
      b.classList.toggle('active', b.dataset.grade === g);
    });
    renderUsers();
  }
  function setSort(key) {
    if (SORT_KEY === key) SORT_DESC = !SORT_DESC;
    else { SORT_KEY = key; SORT_DESC = true; }
    renderUsers();
  }
  function renderUsers() {
    var q = (document.getElementById('memberSearch').value || '').trim().toLowerCase();
    var list = ALL_USERS.filter(function(u){
      if (GRADE_FILTER && (u.grade || 'tester') !== GRADE_FILTER) return false;
      if (!q) return true;
      var gLabel = (GRADE_META[u.grade || 'tester'] || {}).label || '';
      return (u.phone_raw + ' ' + (u.name||'') + ' ' + (u.industry||'') + ' ' + (u.memo||'') + ' ' + gLabel)
        .toLowerCase().indexOf(q) !== -1;
    });
    list = list.slice().sort(function(a,b){
      var av = a[SORT_KEY] || 0, bv = b[SORT_KEY] || 0;
      return SORT_DESC ? (bv - av) : (av - bv);
    });
    document.getElementById('memberCount').textContent = list.length + '명';
    if (list.length === 0) {
      document.getElementById('userRows').innerHTML = '<tr><td colspan="12" style="text-align:center; padding:30px; color:#9AA3AF">' + (q ? '검색 결과 없음' : '등록된 멤버 없음') + '</td></tr>';
      return;
    }
    var html2 = '';
    var now = Date.now();
    list.forEach(function(u){
      var added = u.added_at_ms ? new Date(u.added_at_ms).toLocaleDateString('ko') : '-';
      var last = u.last_seen_ms ? timeAgo(now - u.last_seen_ms) : '<span style="color:#9AA3AF">미진입</span>';
      var statusBadge;
      if (!u.first_seen_ms) statusBadge = '<span class="badge off">미진입</span>';
      else if (u.last_seen_ms && (now - u.last_seen_ms) < 7 * 86400000) statusBadge = '<span class="badge on">활성</span>';
      else statusBadge = '<span class="badge cool">휴면</span>';
      var industryHtml = u.industry
        ? '<span style="background:#EEF4FF; color:#1B64DA; padding:2px 7px; border-radius:6px; font-size:11px; font-weight:700;">' + escape(u.industry) + '</span>'
        : '<span style="color:#9AA3AF; font-size:11px;">-</span>';
      // 추가84 — 등급 select (토글): 바꾸면 바로 등업/강등
      var g = u.grade || 'tester';
      var gm = GRADE_META[g];
      var gradeSel = '<select onchange="changeGrade(\\'' + u.phone_raw + '\\', this, \\'' + g + '\\')" '
        + 'style="border:1.5px solid ' + gm.fg + '33; background:' + gm.bg + '; color:' + gm.fg + '; '
        + 'border-radius:8px; padding:4px 6px; font-size:11.5px; font-weight:700; font-family:inherit; cursor:pointer;">'
        + ['applicant','tester','standard','premium'].map(function(k){
            return '<option value="' + k + '"' + (k === g ? ' selected' : '') + '>' + GRADE_META[k].label + '</option>';
          }).join('')
        + '</select>';
      // 추가85 — 유료회원이면 등급 아래에 결제일 + 재결제 D-day
      var billingHtml = '';
      if (u.billing) {
        billingHtml = '<div style="font-size:10.5px; margin-top:3px; color:#5A6472;">💳 매월 '
          + u.billing.anchor_day + '일 · ' + ddayBadge(u.billing.d_day)
          + (u.billing.months_subscribed > 0 ? ' · ' + u.billing.months_subscribed + '개월째' : ' · 첫 달')
          + '</div>';
      } else if (g === 'tester' && u.free_until_ms) {
        // 추가86 — 무료 체험 D-day (만료 지나면 빨간 표시)
        var freeD = Math.ceil((u.free_until_ms - Date.now()) / 86400000);
        billingHtml = '<div style="font-size:10.5px; margin-top:3px;">'
          + (freeD >= 0
              ? '🎁 <span style="color:' + (freeD <= 7 ? '#F0436A' : '#5A6472') + '; font-weight:700;">무료 D-' + freeD + '</span>'
              : '<span style="color:#F0436A; font-weight:800;">무료 만료 ' + Math.abs(freeD) + '일 지남</span>')
          + '</div>';
      }
      html2 += '<tr>'
            + '<td><a href="/admin/user/' + encodeURIComponent(u.phone_raw) + '" style="color:#3182F6; text-decoration:none"><b>' + u.phone + '</b></a><br><span style="font-size:11px; color:#5A6472">' + escape(u.name || '-') + '</span></td>'
            + '<td>' + gradeSel + billingHtml + '</td>'
            + '<td>' + industryHtml + '</td>'
            + '<td style="font-size:11px; color:#5A6472; max-width:140px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" title="' + escape(u.memo || '') + '">' + escape(u.memo || '-') + '</td>'
            + '<td>' + added + '</td>'
            + '<td>' + last + '</td>'
            + '<td class="right">' + (u.app_days || 0) + '일</td>'
            + '<td class="right">' + u.use_count + '</td>'
            + '<td class="right"><b>' + u.calls + '</b></td>'
            + '<td class="right">$' + u.cost_usd.toFixed(3) + '</td>'
            + '<td>' + statusBadge + '</td>'
            + '<td>' + (g === 'applicant'
                ? '<span style="font-size:11px; color:#9AA3AF;">등급▲로 승인</span>'
                : '<button onclick="removeUser(\\'' + u.phone_raw + '\\', \\'' + escape(u.name || '') + '\\')" style="background:#fff; color:#F0436A; border:1.5px solid #F0436A; border-radius:7px; padding:4px 9px; font-size:11px; font-weight:700; cursor:pointer; font-family:inherit;">제거</button>')
            + '</td>'
            + '</tr>';
    });
    document.getElementById('userRows').innerHTML = html2;
  }
  function openAddModal() { document.getElementById('addModal').classList.add('show'); document.getElementById('addMsg').textContent = ''; }
  function closeAddModal() { document.getElementById('addModal').classList.remove('show'); }
  async function submitAdd() {
    var phone = document.getElementById('addPhone').value.trim();
    var name = document.getElementById('addName').value.trim();
    var memo = document.getElementById('addMemo').value.trim();
    var msg = document.getElementById('addMsg');
    if (!phone) { msg.textContent = '폰번호를 입력해주세요.'; msg.style.color = '#F0436A'; return; }
    try {
      var r = await fetch('/admin/beta/whitelist', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + getToken(), 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone: phone, name: name, memo: memo }),
      });
      var j = await r.json();
      if (!r.ok) throw new Error(j.detail || ('HTTP ' + r.status));
      msg.textContent = (j.action === 'added' ? '✓ 추가됨: ' : '✓ 갱신됨: ') + j.phone;
      msg.style.color = '#16C172';
      document.getElementById('addPhone').value = ''; document.getElementById('addName').value = ''; document.getElementById('addMemo').value = '';
      load();
    } catch(e) { msg.textContent = '실패: ' + e.message; msg.style.color = '#F0436A'; }
  }
  async function removeUser(phone, name) {
    if (!confirm((name || phone) + ' 을(를) 화이트리스트에서 제거할까요?\\n제거하면 이 번호는 앱 진입이 차단됩니다.')) return;
    try {
      var r = await fetch('/admin/beta/whitelist/' + encodeURIComponent(phone), {
        method: 'DELETE',
        headers: { 'Authorization': 'Bearer ' + getToken() },
      });
      if (!r.ok) throw new Error('HTTP ' + r.status);
      load();
    } catch(e) { alert('제거 실패: ' + e.message); }
  }
  // 추가84 — 등급 변경 (등업/강등)
  async function changeGrade(phone, sel, oldGrade) {
    var newGrade = sel.value;
    if (newGrade === oldGrade) return;
    var msg = {
      applicant: '등업대기로 강등 = 앱 진입 차단 + 유료 해지',
      tester: '베타 테스터 (무료) 로 변경' + (oldGrade === 'applicant' ? ' = 등업 승인 (앱 진입 허용)' : ' (유료였다면 해지 처리)'),
      standard: '일반 사장님 (월 5만) 으로 변경',
      premium: '특별 사장님 (월 10만, 팀·협업 포함) 으로 변경',
    }[newGrade];
    if (!confirm(phone + '\\n→ ' + GRADE_META[newGrade].label + '\\n\\n' + msg + '\\n진행할까요?')) {
      sel.value = oldGrade; return;
    }
    try {
      var r = await fetch('/api/admin/member/grade', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + getToken(), 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone: phone, grade: newGrade }),
      });
      var j = await r.json();
      if (!r.ok) throw new Error(j.detail || ('HTTP ' + r.status));
      load();
    } catch(e) { alert('등급 변경 실패: ' + e.message); sel.value = oldGrade; }
  }
  function kpiCard(cls, lbl, val, sub) {
    // 추가94 — sub 는 raw HTML 허용 (추세 배지 삽입용. 호출부 전부 내부 문자열이라 안전)
    return '<div class="kpi ' + cls + '">'
         + '<div class="lbl">' + escape(lbl) + '</div>'
         + '<div class="val">' + val + '</div>'
         + '<div class="sub2">' + sub + '</div>'
         + '</div>';
  }
  // 추가94 — 주간 추세 배지 (지표는 늘면 초록 ▲, 줄면 빨강 ▼)
  function trendBadge(t) {
    if (!t || (t.cur === 0 && t.prev === 0)) return '';
    var diff = t.cur - t.prev;
    if (diff === 0) return '<br><span style="color:#9AA3AF; font-size:11px; font-weight:700;">지난주와 동일</span>';
    var up = diff > 0;
    return '<br><span style="color:' + (up ? 'var(--success)' : 'var(--error)') + '; font-size:11px; font-weight:800;">'
         + (up ? '▲' : '▼') + ' 지난주 ' + t.prev + ' → 이번 주 ' + t.cur + '</span>';
  }
  function netItem(lbl, val, kind) {
    if (!lbl) return '<div class="net-item empty"></div>';
    var clickAttr = (val > 0 && kind) ? ' onclick="openDrill(\\''+kind+'\\',\\''+lbl.replace(/'/g,"\\\\'")+'\\')"' : ' class="net-item empty"';
    var cls = (val > 0 && kind) ? 'net-item' : 'net-item empty';
    return '<div class="' + cls + '"' + (val > 0 && kind ? ' onclick="openDrill(\\''+kind+'\\', \\''+lbl.replace(/'/g,"")+'\\')"' : '') + '>'
         + '<div class="lbl">' + escape(lbl) + '</div><div class="val">' + val + '</div></div>';
  }

  // drill-down 모달
  var LAST_DETAILS = {};
  function openDrill(kind, title) {
    var rows = LAST_DETAILS[kind] || [];
    document.getElementById('drillTitle').textContent = title + ' · ' + rows.length + '건';
    document.getElementById('drillBody').innerHTML = renderDrillTable(kind, rows);
    document.getElementById('drillModal').classList.add('show');
  }
  function closeDrill() { document.getElementById('drillModal').classList.remove('show'); }

  function renderDrillTable(kind, rows) {
    if (!rows || rows.length === 0) return '<div class="nodata">상세 데이터 없음</div>';
    var cols, head;
    if (kind === 'collab_total' || kind === 'collab_accepted' || kind === 'collab_completed') {
      head = ['요청자', '협업 사장', '현장', '예정', '일당', '상태', '생성'];
      cols = function(r){ return [
        '<b>'+escape(r.owner_name)+'</b><br><span style="font-size:11px; color:#9AA3AF">'+escape(r.owner_phone)+'</span>',
        '<b>'+escape(r.partner_name)+'</b><br><span style="font-size:11px; color:#9AA3AF">'+escape(r.partner_phone)+'</span>',
        escape(r.title || '-'),
        escape(r.scheduled || '-'),
        r.daily_wage ? r.daily_wage + '만' : '-',
        '<span class="badge ' + (r.status === 'accepted' ? 'on' : r.status === 'completed' ? 'on' : 'cool') + '">' + escape(r.status) + '</span>',
        escape(r.created),
      ]; };
    } else if (kind === 'recruit_total') {
      head = ['모집자', '날짜', '위치', '작업', '일당', '상태', '생성'];
      cols = function(r){ return [
        '<b>'+escape(r.owner_name)+'</b><br><span style="font-size:11px; color:#9AA3AF">'+escape(r.owner_phone)+'</span>',
        escape(r.date || '-'),
        escape(r.place || '-'),
        escape(r.work || '-'),
        r.daily_wage ? r.daily_wage + '만' : '-',
        '<span class="badge ' + (r.status === 'open' ? 'on' : 'cool') + '">' + escape(r.status) + '</span>',
        escape(r.created),
      ]; };
    } else if (kind === 'recruit_apps') {
      head = ['지원자', '지원 시각', '상태', '위치', '작업', '일당'];
      cols = function(r){ return [
        '<b>'+escape(r.partner_name)+'</b><br><span style="font-size:11px; color:#9AA3AF">'+escape(r.partner_phone)+'</span>',
        escape(r.applied),
        '<span class="badge cool">' + escape(r.status) + '</span>',
        escape(r.place || '-'),
        escape(r.work || '-'),
        r.daily_wage ? r.daily_wage + '만' : '-',
      ]; };
    } else if (kind === 'team_members') {
      head = ['사장님', '팀원 이름', '팀원 폰', '역할', '등록'];
      cols = function(r){ return [
        '<b>'+escape(r.owner_name)+'</b><br><span style="font-size:11px; color:#9AA3AF">'+escape(r.owner_phone)+'</span>',
        '<b>'+escape(r.name || '-')+'</b>',
        escape(r.phone),
        '<span class="badge cool">' + escape(r.role) + '</span>',
        escape(r.created),
      ]; };
    } else if (kind === 'photos') {
      head = ['사장님', '라벨', '고객 phone', '협업 share', '업로더', '시각'];
      cols = function(r){ return [
        '<b>'+escape(r.owner_name)+'</b><br><span style="font-size:11px; color:#9AA3AF">'+escape(r.owner_phone)+'</span>',
        escape(r.label || '-'),
        escape(r.customer_phone || '-'),
        r.share_id ? '<span style="font-family:monospace; font-size:11px">' + escape(r.share_id.slice(0,12)) + '</span>' : '-',
        escape(r.uploader || '-'),
        escape(r.uploaded),
      ]; };
    } else if (kind === 'sys_errors') {
      // 추가93 — 🩺 최근 서버 에러
      head = ['시각', '경로', '상태', '내용'];
      cols = function(r){ return [
        '<span style="font-size:11px;">' + escape(r.t) + '</span>',
        '<span style="font-family:monospace; font-size:11px;">' + escape(r.path) + '</span>',
        '<span class="badge off">' + r.status + '</span>',
        '<span style="font-size:11px; color:#5A6472;">' + escape(r.detail) + '</span>',
      ]; };
    } else if (kind === 'at_risk' || kind === 'unsettled') {
      // 추가87 — ⚠️ 이탈 위험 / 추가89 — 🌱 시공일 미등록 명단 (같은 컬럼)
      head = ['이름', '전화', '마지막 접속'];
      cols = function(r){ return [
        '<b>' + escape(r.name) + '</b>',
        '<a href="/admin/user/' + encodeURIComponent(r.phone_raw) + '" style="color:#3182F6; text-decoration:none;">' + escape(r.phone) + '</a>',
        '<span style="color:#F0436A; font-weight:700;">' + escape(r.last) + '</span>',
      ]; };
    } else {
      return '<div class="nodata">상세 데이터 없음</div>';
    }
    var html = '<table><thead><tr>';
    head.forEach(function(h){ html += '<th>' + h + '</th>'; });
    html += '</tr></thead><tbody>';
    rows.forEach(function(r){
      html += '<tr>';
      cols(r).forEach(function(c){ html += '<td>' + c + '</td>'; });
      html += '</tr>';
    });
    html += '</tbody></table>';
    return html;
  }
  function timeAgo(ms) {
    var s = Math.floor(ms / 1000);
    if (s < 60) return s + '초 전';
    var m = Math.floor(s / 60);
    if (m < 60) return m + '분 전';
    var h = Math.floor(m / 60);
    if (h < 24) return h + '시간 전';
    var d = Math.floor(h / 24);
    return d + '일 전';
  }
  function escape(s) { s = String(s||''); return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
  initTab();  // 추가92 — 마지막 본 탭 복원 (기본 = 멤버)
  load();
</script></body></html>
"""


@app.get("/admin/beta/dashboard", response_class=HTMLResponse, include_in_schema=False)
async def admin_beta_dashboard_page():
    """베타 종합 대시보드 HTML."""
    return HTMLResponse(content=_BETA_DASHBOARD_HTML)


# ============================================================================
# 추가70 (2026-06-29) — /admin/adoption 정착 대시보드
# ─────────────────────────────────────────────────────────────────────────────
# 사장님 요청: Play Store 정식 출시 2일 앞. "사람들이 어떻게 적응하는지"
# 실시간 파악 필요. 신규 vs 정착 vs 이탈 구분 + Top 화면 + 이탈 위험 사장님.
# ============================================================================


@app.get("/admin/adoption/data")
async def admin_adoption_data(
    authorization: Optional[str] = Header(default=None),
    days: int = 30,
) -> dict:
    """정착 대시보드 데이터. Bearer 인증.

    응답:
      - new_users: {success, exploring, at_risk} — 7일 이내 가입 사용자 분류
      - activation: {d1_3, d4_7, d8_plus, never} — 첫 시공일 등록까지 D+N 히스토그램
      - top_screens: [{screen, total, unique_users}] — 자주 쓰는 화면 Top 10
      - at_risk: [{phone, name, added_days_ago, days_since_last, schedule_count}] — 이탈 위험 사장님
      - stats: {total_users, active_7d, activated} — 전체 요약
    """
    _admin_auth_bearer_from_header(authorization)
    days = max(7, min(days, 90))
    now = _now_ms()
    seven_days_ago = now - 7 * 86400 * 1000
    three_days_ago = now - 3 * 86400 * 1000
    thirty_days_ago = now - 30 * 86400 * 1000

    with sqlite3.connect(DB_PATH) as con:
        con.row_factory = sqlite3.Row

        # 1) 전체 사용자 + 정착 상태 (schedule_create 이벤트 있으면 정착)
        # phone → {added, last_seen, schedule_count, first_schedule_at}
        wl_rows = con.execute(
            "SELECT phone, name, added_at_ms, first_seen_ms, last_seen_ms FROM beta_whitelist"
        ).fetchall()

        # phone 별 schedule_create 카운트 + 첫 등록 시각
        sc_rows = con.execute(
            """SELECT owner_phone, COUNT(*) as cnt, MIN(created_at_ms) as first_ms
               FROM app_events WHERE event_name = 'schedule_create'
               GROUP BY owner_phone"""
        ).fetchall()
        sc_map = {r[0]: {"cnt": r[1], "first_ms": r[2]} for r in sc_rows}

        new_success = 0
        new_exploring = 0
        new_at_risk = 0
        activation_hist = {"d1_3": 0, "d4_7": 0, "d8_plus": 0, "never": 0}
        at_risk_list = []
        total_users = len(wl_rows)
        active_7d = 0
        activated = 0

        for wl in wl_rows:
            phone = wl["phone"]
            name = wl["name"] or ""
            added = wl["added_at_ms"]
            last = wl["last_seen_ms"] or 0
            sc = sc_map.get(phone, {"cnt": 0, "first_ms": None})
            sc_cnt = sc["cnt"]
            sc_first = sc["first_ms"]

            is_new = added >= seven_days_ago
            has_scheduled = sc_cnt > 0

            if last >= seven_days_ago:
                active_7d += 1
            if has_scheduled:
                activated += 1

            # 신규 사용자 분류
            if is_new:
                if has_scheduled:
                    new_success += 1
                elif last >= three_days_ago:
                    new_exploring += 1
                else:
                    new_at_risk += 1
                    at_risk_list.append({
                        "phone": phone,
                        "name": name,
                        "added_days_ago": max(1, (now - added) // (86400 * 1000)),
                        "days_since_last": (now - last) // (86400 * 1000) if last else None,
                        "schedule_count": sc_cnt,
                    })

            # 정착 히스토그램 (첫 시공일 등록 D+N)
            if has_scheduled and sc_first and added:
                days_to = max(0, (sc_first - added) // (86400 * 1000))
                if days_to <= 3:
                    activation_hist["d1_3"] += 1
                elif days_to <= 7:
                    activation_hist["d4_7"] += 1
                else:
                    activation_hist["d8_plus"] += 1
            elif added and (now - added) >= 7 * 86400 * 1000:
                # 가입 7일 넘었는데 아직 시공일 X = "never" 카테고리
                activation_hist["never"] += 1

        # 2) 화면 인기 Top 10 (screen_view 이벤트 지난 N일)
        cutoff = now - days * 86400 * 1000
        # screen 정규화 (chat?phone=... → chat 등)
        def _norm(s):
            if not s:
                return ""
            base = str(s).split("?")[0].split("&")[0].strip().rstrip("/")
            if "/" in base:
                segs = [x for x in base.split("/") if x and not (x.startswith("{") and x.endswith("}"))]
                base = segs[-1] if segs else base.rsplit("/", 1)[-1]
            return base.lower()

        screen_rows = con.execute(
            """SELECT screen, owner_phone FROM app_events
               WHERE event_name = 'screen_view' AND created_at_ms >= ?""",
            (cutoff,),
        ).fetchall()
        # 추가71 (2026-06-29) — 사장님 통찰: "5명이 474회? 아님 1명이 400 나머지 74?"
        # → 침투율 (penetration) + 인당 평균 + 최다 사용자 비율 추가.
        screen_agg = {}  # normalized screen → phone → count
        for sr in screen_rows:
            norm = _norm(sr[0])
            if not norm:
                continue
            if norm not in screen_agg:
                screen_agg[norm] = {}
            phone = sr[1]
            screen_agg[norm][phone] = screen_agg[norm].get(phone, 0) + 1

        top_screens_raw = []
        for scr, phone_map in screen_agg.items():
            total = sum(phone_map.values())
            unique = len(phone_map)
            max_cnt = max(phone_map.values()) if phone_map else 0
            top_screens_raw.append({
                "screen": scr,
                "total": total,
                "unique_users": unique,
                "penetration_pct": round(unique / total_users * 100, 1) if total_users else 0,
                "per_user_avg": round(total / unique, 1) if unique else 0,
                "max_user_share_pct": round(max_cnt / total * 100, 1) if total else 0,
            })
        top_screens = sorted(top_screens_raw, key=lambda x: x["total"], reverse=True)[:10]

    # at_risk 정렬 (미진입 오래된 순)
    at_risk_list.sort(key=lambda x: -(x["days_since_last"] or 999))

    return {
        "new_users": {
            "success": new_success,
            "exploring": new_exploring,
            "at_risk": new_at_risk,
        },
        "activation": activation_hist,
        "top_screens": top_screens,
        "at_risk": at_risk_list[:20],
        "stats": {
            "total_users": total_users,
            "active_7d": active_7d,
            "activated": activated,
            "activation_rate": round(activated / total_users * 100, 1) if total_users else 0,
        },
    }


_ADOPTION_HTML = """<!doctype html>
<html lang="ko"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>정착 대시보드 — RING-GO admin</title>
<style>
  :root { --blue:#3182F6; --bg:#F4F5F7; --card:#fff;
          --t1:#0B0F19; --t2:#5A6472; --t3:#9AA3AF; --line:#EEF0F3;
          --success:#16C172; --warn:#FF8B40; --error:#F0436A; }
  * { box-sizing:border-box; }
  body { margin:0; background:var(--bg); font-family:'Pretendard',-apple-system,sans-serif;
         color:var(--t1); line-height:1.5; }
  .wrap { max-width:900px; margin:0 auto; padding:16px 14px 40px; }
  .back { display:inline-block; font-size:13px; color:var(--blue); text-decoration:none; margin-bottom:8px; }
  h1 { font-size:22px; font-weight:800; margin:0 0 4px; }
  .sub { font-size:13px; color:var(--t2); margin-bottom:14px; }
  .card { background:var(--card); border-radius:14px; padding:16px; margin-top:14px;
          box-shadow:0 1px 3px rgba(0,0,0,.04); }
  .card h2 { font-size:14px; font-weight:800; margin:0 0 12px; }
  .stats-4 { display:grid; grid-template-columns:repeat(4,1fr); gap:10px; margin-top:12px; }
  .stat { background:var(--card); border-radius:12px; padding:12px 10px; text-align:center;
          box-shadow:0 1px 3px rgba(0,0,0,.04); }
  .stat .v { font-size:22px; font-weight:800; }
  .stat .lab { font-size:11px; color:var(--t3); font-weight:700; margin-top:4px; }
  .seg { display:grid; grid-template-columns:repeat(3,1fr); gap:8px; }
  .seg .s { padding:10px; border-radius:10px; }
  .seg .s.success { background:#E7F8EF; color:#0a8f44; }
  .seg .s.warn    { background:#FFF3DF; color:#C9820B; }
  .seg .s.risk    { background:#FDEAEF; color:var(--error); }
  .seg .s .v { font-size:24px; font-weight:800; }
  .seg .s .lab { font-size:11.5px; font-weight:700; margin-top:2px; }
  .hist { display:flex; align-items:flex-end; gap:10px; height:140px;
          padding:8px 4px; border-bottom:1px solid var(--line); }
  .hist .bar { flex:1; display:flex; flex-direction:column; align-items:center; gap:4px; }
  .hist .bar .b { width:100%; background:var(--blue); border-radius:6px 6px 0 0; min-height:2px; }
  .hist .bar .b.risk { background:var(--error); }
  .hist .bar .n { font-size:14px; font-weight:800; }
  .hist-lbl { display:flex; gap:10px; padding:6px 4px 0; font-size:11px; color:var(--t3); }
  .hist-lbl div { flex:1; text-align:center; font-weight:700; }
  .screen-row { display:flex; align-items:center; padding:8px 0; border-bottom:1px solid var(--line);
                font-size:13px; }
  .screen-row:last-child { border-bottom:0; }
  .screen-row .rank { width:22px; font-weight:800; color:var(--t3); }
  .screen-row .name { flex:1; font-weight:700; display:flex; align-items:center; gap:6px; flex-wrap:wrap; }
  .screen-row .n { color:var(--t2); font-size:11px; text-align:right; white-space:nowrap; }
  .scr-badge { font-size:10.5px; font-weight:800; padding:2px 6px; border-radius:6px; }
  .scr-badge.success { background:#E7F8EF; color:#0a8f44; }
  .scr-badge.warn { background:#FFF3DF; color:#C9820B; }
  .scr-badge.risk { background:#FDEAEF; color:var(--error); }
  @media (max-width:600px) {
    .screen-row { flex-direction:column; align-items:flex-start; gap:4px; }
    .screen-row .n { text-align:left; }
  }
  .risk-row { display:flex; align-items:center; padding:9px 0; border-bottom:1px solid var(--line);
              font-size:13px; }
  .risk-row:last-child { border-bottom:0; }
  .risk-row .info { flex:1; }
  .risk-row .name { font-weight:800; }
  .risk-row .meta { font-size:11.5px; color:var(--error); font-weight:700; }
  .risk-row a.link { color:var(--blue); text-decoration:none; font-size:12px; font-weight:800; }
  .empty { color:var(--t3); font-size:13px; text-align:center; padding:14px 0; }
  #tokenModal { position:fixed; inset:0; background:rgba(0,0,0,.5); display:none;
                align-items:center; justify-content:center; z-index:50; }
  #tokenModal.show { display:flex; }
  #tokenModal .box { background:#fff; border-radius:14px; padding:22px; max-width:90vw; width:340px; }
  #tokenModal input { width:100%; border:1.5px solid var(--line); border-radius:10px;
                       padding:11px 12px; font-size:14px; font-family:inherit; }
  #tokenModal button { width:100%; background:var(--blue); color:#fff; border:0;
                       border-radius:10px; padding:11px; font-size:14px; font-weight:800;
                       font-family:inherit; cursor:pointer; margin-top:10px; }
</style></head>
<body>
<div id="tokenModal"><div class="box">
  <h3 style="margin:0 0 8px">ADMIN_TOKEN 입력</h3>
  <input id="tokenInput" type="password" placeholder="토큰">
  <button onclick="saveToken()">저장</button>
</div></div>

<div class="wrap">
  <a class="back" href="/admin">← admin 홈</a>
  <h1>📊 정착 대시보드</h1>
  <div class="sub" id="sub">로딩중…</div>

  <div class="stats-4">
    <div class="stat"><div class="v" id="sTotal">-</div><div class="lab">전체 사용자</div></div>
    <div class="stat"><div class="v" id="sActive">-</div><div class="lab">7일 활동</div></div>
    <div class="stat"><div class="v" id="sActivated">-</div><div class="lab">정착 성공</div></div>
    <div class="stat"><div class="v" id="sRate">-</div><div class="lab">정착률</div></div>
  </div>

  <div class="card">
    <h2>🆕 신규 사용자 (7일 이내 가입)</h2>
    <div class="seg">
      <div class="s success"><div class="v" id="nSuccess">-</div><div class="lab">🟢 정착 성공<br>(시공일 등록)</div></div>
      <div class="s warn"><div class="v" id="nExploring">-</div><div class="lab">🟡 탐색중<br>(등록 X)</div></div>
      <div class="s risk"><div class="v" id="nAtRisk">-</div><div class="lab">🔴 이탈 위험<br>(3일+ 안 켬)</div></div>
    </div>
  </div>

  <div class="card">
    <h2>⏱ 첫 시공일 등록까지</h2>
    <div class="hist" id="histBars"></div>
    <div class="hist-lbl">
      <div>D+3 이내</div><div>D+4~7</div><div>D+8~</div><div>가입 7일+<br>미등록</div>
    </div>
  </div>

  <div class="card">
    <h2>🏆 자주 쓰는 화면 Top 10 (지난 30일)</h2>
    <div id="topScreens"><div class="empty">로딩중…</div></div>
  </div>

  <div class="card">
    <h2>🚨 이탈 위험 사장님 <span style="font-size:11px; color:var(--t3); font-weight:700;">— 신규 가입 + 3일+ 안 켬</span></h2>
    <div id="atRisk"><div class="empty">로딩중…</div></div>
  </div>
</div>

<script>
  function getToken() { return sessionStorage.getItem('admin_token') || ''; }
  function saveToken() {
    var t = document.getElementById('tokenInput').value.trim();
    if (!t) return;
    sessionStorage.setItem('admin_token', t);
    document.getElementById('tokenModal').classList.remove('show');
    load();
  }
  function ensureToken() {
    if (!getToken()) { document.getElementById('tokenModal').classList.add('show'); return false; }
    return true;
  }
  function esc(s) { s = String(s||''); return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
  function fmtPhone(p) {
    p = String(p||'').replace(/[^0-9]/g,'');
    if (p.length === 11) return p.slice(0,3) + '-' + p.slice(3,7) + '-' + p.slice(7);
    return p;
  }

  async function load() {
    if (!ensureToken()) return;
    try {
      var r = await fetch('/admin/adoption/data', { headers: { 'Authorization': 'Bearer ' + getToken() } });
      if (r.status === 401) { sessionStorage.removeItem('admin_token'); ensureToken(); return; }
      if (!r.ok) throw new Error('HTTP ' + r.status);
      var d = await r.json();

      // 상단 4 stats
      document.getElementById('sTotal').textContent     = d.stats.total_users;
      document.getElementById('sActive').textContent    = d.stats.active_7d;
      document.getElementById('sActivated').textContent = d.stats.activated;
      document.getElementById('sRate').textContent      = d.stats.activation_rate + '%';

      // 신규 사용자 3분류
      document.getElementById('nSuccess').textContent   = d.new_users.success;
      document.getElementById('nExploring').textContent = d.new_users.exploring;
      document.getElementById('nAtRisk').textContent    = d.new_users.at_risk;

      // 정착 히스토그램
      var h = d.activation;
      var vals = [h.d1_3, h.d4_7, h.d8_plus, h.never];
      var max = Math.max.apply(null, vals.concat([1]));
      var bars = '';
      var classes = ['', '', '', 'risk'];
      for (var i=0; i<vals.length; i++) {
        var pct = Math.max(2, Math.round(vals[i] / max * 100));
        bars += '<div class="bar"><div class="n">' + vals[i] + '</div><div class="b ' + classes[i]
              + '" style="height:' + pct + '%"></div></div>';
      }
      document.getElementById('histBars').innerHTML = bars;

      // Top 화면
      var ts = d.top_screens || [];
      var LBL = {
        'home':'홈','chat':'채팅','collab':'협업현장','collab_inbox':'협업 인박스',
        'intake_form':'접수서','schedule':'일정','customer':'고객 상세','customer_detail':'고객 상세',
        'call':'통화상담','team':'팀원','settings':'설정','settlement':'정산','stats':'통계',
        'business_info':'업체 정보','collab_sites':'협업현장','pricing_items':'가격표',
        'customers':'고객 목록','new_leads':'신규 리드','visited':'방문 예정','search':'검색',
        'notebook':'노트','report':'리포트','templates':'템플릿','login':'로그인','onboarding':'온보딩',
      };
      // 추가71 — 균등도 (진짜 인기 vs 한 명 폭주)
      // 추가72 (2026-06-29) — badge 기준 = 전체 사용자 수 상대적.
      //   완전 균등 = 1/N. 균등 판정 = 1.5/N (5명이면 30%, 10명이면 15%).
      //   폭주 판정 = 3/N (5명이면 60%, 10명이면 30%).
      //   상하한 clamp: 균등 [15%, 40%], 폭주 [35%, 70%] (극단 사용자 수 방지).
      var N = Math.max(1, d.stats.total_users || 1);
      var evenTh  = Math.max(15, Math.min(40, 150 / N));
      var surgeTh = Math.max(35, Math.min(70, 300 / N));
      var thtml = '';
      if (ts.length === 0) thtml = '<div class="empty">데이터 없음</div>';
      else for (var i=0; i<ts.length; i++) {
        var s = ts[i];
        var lbl = LBL[s.screen] || s.screen;
        var badge, badgeClass;
        if (s.max_user_share_pct <= evenTh) { badge = '🟢 균등'; badgeClass = 'success'; }
        else if (s.max_user_share_pct <= surgeTh) { badge = '🟡 보통'; badgeClass = 'warn'; }
        else { badge = '🔴 한 명 폭주'; badgeClass = 'risk'; }
        thtml += '<div class="screen-row"><div class="rank">' + (i+1) + '</div>'
              + '<div class="name">' + esc(lbl)
              + ' <span class="scr-badge ' + badgeClass + '">' + badge + '</span></div>'
              + '<div class="n">' + s.total + '회 · '
              + s.unique_users + '명 (' + s.penetration_pct + '%) · '
              + '인당 ' + s.per_user_avg + '회 · 최다 ' + s.max_user_share_pct + '%'
              + '</div></div>';
      }
      // 카드 밑에 판정 기준 안내 (사용자 수 반영 자동 조정 명시)
      thtml += '<div style="margin-top:10px; font-size:10.5px; color:#9AA3AF; text-align:center;">'
            + '판정 기준 (N=' + N + '명): 균등 ≤' + evenTh.toFixed(0) + '% · 폭주 >' + surgeTh.toFixed(0) + '%'
            + '</div>';
      document.getElementById('topScreens').innerHTML = thtml;

      // 이탈 위험
      var ar = d.at_risk || [];
      var arhtml = '';
      if (ar.length === 0) arhtml = '<div class="empty" style="color:var(--success)">🎉 이탈 위험 사장님 없음</div>';
      else for (var i=0; i<ar.length; i++) {
        var u = ar[i];
        var displayName = u.name || fmtPhone(u.phone);
        var meta = (u.days_since_last !== null ? u.days_since_last + '일 안 켬' : '진입 X')
                 + ' · 가입 ' + u.added_days_ago + '일차 · 시공일 ' + u.schedule_count + '건';
        arhtml += '<div class="risk-row"><div class="info">'
              + '<div class="name">' + esc(displayName) + '</div>'
              + '<div class="meta">' + esc(meta) + '</div></div>'
              + '<a class="link" href="/admin/user/' + encodeURIComponent(u.phone) + '">보기 ›</a></div>';
      }
      document.getElementById('atRisk').innerHTML = arhtml;

      document.getElementById('sub').textContent = '지난 30일 기준 · ' + new Date().toLocaleTimeString('ko');
    } catch(e) {
      document.getElementById('sub').textContent = '로드 실패: ' + e.message;
    }
  }
  load();
</script></body></html>
"""


@app.get("/admin/adoption", response_class=HTMLResponse, include_in_schema=False)
async def admin_adoption_page():
    """추가89 (2026-07-03) — 정착 대시보드는 종합 대시보드로 흡수.
    (🌱 정착률 KPI 카드 + 📱 자주 쓰는 화면 Top 5). 옛 링크 호환 리다이렉트.
    /admin/adoption/data 는 호환 위해 유지."""
    return HTMLResponse(
        content='<meta http-equiv="refresh" content="0; url=/admin/beta/dashboard">'
                '<a href="/admin/beta/dashboard">종합 대시보드로 이동 →</a>'
    )


# ============================================================================
# 추가34 (2026-06-18) — /admin/user/{phone} 사용자 종합 활동 페이지
# ─────────────────────────────────────────────────────────────────────────────
# 사장님 요청: "베타테스터들 번호 클릭하면 스케줄 등록은 했는지 다 보였으면 좋겠어."
# → whitelist + dashboard 양쪽에서 폰번호 클릭 → 이 페이지로 이동.
# 맨 위: 등록한 일정·현장 (intake_forms + shared_sites.owner)
# 그 아래: 통화 요약 / 답장 사용 / 협업 (sent/received) / 최근 활동·가입일.
# ============================================================================


@app.get("/admin/user/{phone}/data")
async def admin_user_detail_data(
    phone: str,
    authorization: Optional[str] = Header(default=None),
) -> dict:
    """베타 사용자 한 명의 종합 활동 데이터 (JSON).

    응답:
      profile: { phone, name, memo, added_at_ms, first_seen_ms, last_seen_ms,
                 use_count, registered_name }
      intakes: [{ token, customer_name, issued_at_ms, submitted_at_ms,
                  status('issued'|'submitted'), summary }]
      shared_sent: [{ share_id, partner_phone, partner_name, title,
                      scheduled_at_ms, status, progress }]
      shared_received: [{ share_id, owner_phone, owner_name, title,
                          scheduled_at_ms, status, progress }]
      feature_counts: { prepare-reply: N, refine: N, call-audio-summary: N, ... }
      recent_api: [{ endpoint, created_at_ms }] (최근 10건)
      last_active_ms: int (api_usage MAX 또는 whitelist.last_seen_ms)
    """
    _admin_auth_bearer_from_header(authorization)
    target = _norm_phone(phone)
    if not target:
        raise HTTPException(400, "phone 필수")

    with db_conn() as con:
        # ── 1) 프로필 (whitelist + registered) ──
        wl_row = con.execute(
            """SELECT phone, name, memo, added_at_ms, first_seen_ms,
                       last_seen_ms, use_count, owner_trade
               FROM beta_whitelist WHERE phone = ?""",
            (target,),
        ).fetchone()
        if wl_row:
            profile = {
                "phone": _fmt_phone(target),
                "phone_raw": target,
                "name": wl_row[1] or "",
                "memo": wl_row[2] or "",
                "added_at_ms": wl_row[3],
                "first_seen_ms": wl_row[4],
                "last_seen_ms": wl_row[5],
                "use_count": wl_row[6] or 0,
            }
        else:
            profile = {
                "phone": _fmt_phone(target),
                "phone_raw": target,
                "name": "",
                "memo": "",
                "added_at_ms": None,
                "first_seen_ms": None,
                "last_seen_ms": None,
                "use_count": 0,
            }
        profile["registered_name"] = _is_registered_owner(target) or ""
        # 추가49 (2026-06-21) — 업종 (beta_signups.industry). 사장님이 admin 에서 직접 추가한
        # 사용자는 beta_signups 에 없을 수 있음 → None.
        signup_row = con.execute(
            "SELECT industry, region FROM beta_signups WHERE phone = ?",
            (target,),
        ).fetchone()
        # 추가50 (2026-06-21) — 앱 onboarding 에서 고른 업종 우선 (owner_trade) → 모집 폼 폴백 (industry).
        owner_trade_from_app = (wl_row[7] if wl_row and len(wl_row) > 7 else None) or ""
        signup_industry = (signup_row[0] if signup_row else None) or ""
        profile["owner_trade"] = owner_trade_from_app  # 앱 onboarding 원본
        profile["industry"] = owner_trade_from_app or signup_industry  # 표시용 우선순위
        profile["industry_source"] = "app" if owner_trade_from_app else ("signup" if signup_industry else "")
        profile["region"] = (signup_row[1] if signup_row else None) or ""

        # ── 2) 등록한 접수서 (intake_forms.owner_phone) ──
        intake_rows = con.execute(
            """SELECT token, customer_name, issued_at_ms, submitted_at_ms, payload_json
               FROM intake_forms
               WHERE owner_phone = ?
               ORDER BY issued_at_ms DESC
               LIMIT 50""",
            (target,),
        ).fetchall()
        intakes = []
        for r in intake_rows:
            token, cust, issued, submitted, payload = r
            summary = ""
            try:
                if payload:
                    pj = _json.loads(payload)
                    # payload_json 안에서 작업 종류 또는 메모 한 줄 뽑기 (best-effort)
                    summary = (
                        pj.get("work") or pj.get("work_summary")
                        or pj.get("memo") or pj.get("note") or ""
                    )
                    if isinstance(summary, str):
                        summary = summary.strip()[:60]
                    else:
                        summary = ""
            except Exception:
                summary = ""
            intakes.append({
                "token": token,
                "customer_name": cust or "",
                "issued_at_ms": issued,
                "submitted_at_ms": submitted,
                "status": "submitted" if submitted else "issued",
                "summary": summary,
            })

        # ── 3) 협업 — 보낸 현장 (owner_phone = target) ──
        sent_rows = con.execute(
            """SELECT share_id, partner_phone, title, scheduled_at_ms, status,
                       progress, partner_name_raw
               FROM shared_sites
               WHERE owner_phone = ?
               ORDER BY created_at_ms DESC
               LIMIT 50""",
            (target,),
        ).fetchall()
        shared_sent = []
        for r in sent_rows:
            sid, pp, title, sched, status, prog, pn_raw = r
            shared_sent.append({
                "share_id": sid,
                "partner_phone": _fmt_phone(pp),
                "partner_name": (pn_raw or "").strip() or _is_registered_owner(pp) or "협업 사장",
                "title": title or "",
                "scheduled_at_ms": sched,
                "status": status,
                "progress": prog,
            })

        # ── 4) 협업 — 받은 현장 (partner_phone = target) ──
        recv_rows = con.execute(
            """SELECT share_id, owner_phone, title, scheduled_at_ms, status,
                       progress, owner_name_raw
               FROM shared_sites
               WHERE partner_phone = ?
               ORDER BY created_at_ms DESC
               LIMIT 50""",
            (target,),
        ).fetchall()
        shared_received = []
        for r in recv_rows:
            sid, op, title, sched, status, prog, on_raw = r
            shared_received.append({
                "share_id": sid,
                "owner_phone": _fmt_phone(op),
                "owner_name": (on_raw or "").strip() or _is_registered_owner(op) or "사장님",
                "title": title or "",
                "scheduled_at_ms": sched,
                "status": status,
                "progress": prog,
            })

        # ── 5) 기능별 사용량 (api_usage.endpoint) ──
        feat_rows = con.execute(
            """SELECT endpoint, COUNT(*)
               FROM api_usage
               WHERE phone = ?
               GROUP BY endpoint""",
            (target,),
        ).fetchall()
        feature_counts = {r[0]: r[1] for r in feat_rows}

        # ── 6) 최근 api_usage 10건 (어떤 기능을 언제 썼는지) ──
        recent_rows = con.execute(
            """SELECT endpoint, created_at_ms
               FROM api_usage
               WHERE phone = ?
               ORDER BY created_at_ms DESC
               LIMIT 10""",
            (target,),
        ).fetchall()
        recent_api = [{"endpoint": r[0], "created_at_ms": r[1]} for r in recent_rows]

        # ── 6.5) 사용자 여정 events (추가51 — 최근 N건)
        # 추가69 (2026-06-29) — 50 → 200 확장. 활성 사용자 (일 이벤트 100~200 건) 도 어제/그저께까지 잡힘.
        # 이벤트 하나 = 짧은 JSON, 200 건도 응답 20KB 미만 (프론트 렌더 부담 X).
        event_rows = con.execute(
            """SELECT event_name, screen, target, extra_json, created_at_ms
               FROM app_events
               WHERE owner_phone = ?
               ORDER BY created_at_ms DESC
               LIMIT 200""",
            (target,),
        ).fetchall()
        # 추가52 (2026-06-23) — screen 정규화 (URL route → 짧은 이름).
        # 추가66 (2026-06-29) — placeholder segment ({customerId}, {phone} 등) 무시.
        # 예: "customer/{customerId}" → "customer" (마지막 실제 segment)
        # 예: "chat/{phone}/{customerId}" → "chat"
        def _norm_screen(s):
            if not s:
                return ""
            base = str(s).split("?")[0].split("&")[0].strip().rstrip("/")
            if "/" in base:
                # placeholder ({param}) 세그먼트 제외, 실제 이름만
                segs = [x for x in base.split("/") if x and not (x.startswith("{") and x.endswith("}"))]
                base = segs[-1] if segs else base.rsplit("/", 1)[-1]
            return base.lower()

        events_journey = []
        for er in event_rows:
            ename, scr, tgt, extra_str, ts = er
            extra_obj = None
            if extra_str:
                try:
                    extra_obj = json.loads(extra_str)
                except Exception:
                    extra_obj = None
            events_journey.append({
                "event_name": ename,
                "screen": _norm_screen(scr),
                "target": tgt or "",
                "extra": extra_obj,
                "at_ms": ts,
            })

        # ── 7) last_active_ms = 앱 실행 시각 (추가41 — beta_whitelist.last_seen_ms 만)
        # 사장님 의도: "앱 켜기만 해도 활동". 폴링 endpoint 들이 _touch_beta_whitelist 호출해서 갱신.
        # LLM 사용 (api_usage MAX) 은 별도 의미라 합치지 않음.
        last_active_ms = profile.get("last_seen_ms") or None

        # ── 8) schedule_count = 캘린더 시공일 등록 누적 (추가55 — 2026-06-23)
        # 안드로이드가 일정 등록 시 EventTracker.track("schedule_create", ...) 호출.
        # 3경로 (일정탭 / 채팅 AI 제안 / 고객상세 날짜픽커) 다 잡힘. 취소는 제외.
        # = 사장님 KPI ("사람들이 캘린더에 시공일 꾸준히 등록") 의 진짜 측정 지표.
        sc_row = con.execute(
            "SELECT COUNT(*) FROM app_events WHERE owner_phone = ? AND event_name = 'schedule_create'",
            (target,),
        ).fetchone()
        schedule_count = sc_row[0] if sc_row else 0

    return {
        "profile": profile,
        "intakes": intakes,
        "shared_sent": shared_sent,
        "shared_received": shared_received,
        "feature_counts": feature_counts,
        "recent_api": recent_api,
        "events_journey": events_journey,  # 추가51 (2026-06-21) — 사용자 여정
        "last_active_ms": last_active_ms,
        "schedule_count": schedule_count,  # 추가55 (2026-06-23) — 캘린더 시공일 등록 누적
    }


_ADMIN_USER_DETAIL_HTML = """<!doctype html>
<html lang="ko"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>사용자 상세 — RING-GO admin</title>
<style>
  :root { --blue:#3182F6; --blue-dark:#1B64DA; --bg:#F4F5F7; --card:#fff;
          --t1:#0B0F19; --t2:#5A6472; --t3:#9AA3AF; --line:#EEF0F3;
          --error:#F0436A; --success:#16C172; --warn:#FF8B40; }
  * { box-sizing:border-box; }
  body { margin:0; background:var(--bg);
         font-family:'Pretendard',-apple-system,system-ui,sans-serif;
         color:var(--t1); line-height:1.5; }
  .wrap { max-width:760px; margin:0 auto; padding:16px 14px 40px; }
  .back { display:inline-block; font-size:13px; color:var(--blue);
          text-decoration:none; margin-bottom:8px; }
  .back:hover { text-decoration:underline; }
  h1 { font-size:21px; font-weight:800; margin:0 0 4px; }
  .sub { font-size:13px; color:var(--t2); margin-bottom:14px; }
  .card { background:var(--card); border-radius:14px; padding:16px;
          box-shadow:0 1px 3px rgba(0,0,0,.04); margin-top:14px; }
  .card h2 { font-size:14px; font-weight:800; margin:0 0 12px;
             display:flex; align-items:center; gap:6px; }
  .card h2 .cnt { font-size:11.5px; font-weight:700; color:var(--t3);
                  background:var(--bg); border-radius:999px; padding:2px 8px; }
  .row { display:flex; flex-wrap:wrap; gap:10px; }
  .meta { font-size:12.5px; color:var(--t2); }
  .meta b { color:var(--t1); }
  .pill { display:inline-block; padding:3px 9px; border-radius:7px;
          font-size:11px; font-weight:800; }
  .pill.ok { background:#E7F8EF; color:var(--success); }
  .pill.pending { background:#FFF7E5; color:var(--warn); }
  .pill.declined { background:#FFF2F5; color:var(--error); }
  .pill.ended { background:#EEF0F3; color:var(--t3); }
  .pill.accepted { background:#E5F0FF; color:var(--blue); }
  .item { padding:10px 0; border-bottom:1px solid var(--line); font-size:13px; }
  .item:last-child { border-bottom:0; }
  .item .title { font-weight:700; font-size:13.5px; }
  .item .sub2 { color:var(--t2); font-size:12px; margin-top:3px; }
  .empty { color:var(--t3); font-size:13px; text-align:center; padding:14px 0; }
  .kv { display:grid; grid-template-columns:90px 1fr; gap:6px 12px;
        font-size:13px; }
  .kv .k { color:var(--t2); font-weight:700; }
  .feat-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:8px; }
  .feat { background:var(--bg); border-radius:10px; padding:10px 12px;
          display:flex; align-items:center; justify-content:space-between; }
  .feat .lab { font-size:12.5px; color:var(--t2); font-weight:700; }
  .feat .n { font-size:16px; font-weight:800; color:var(--t1); }
  .summary-cards { display:grid; grid-template-columns:repeat(2,1fr); gap:10px;
                   margin-top:12px; }
  .s-card { background:var(--card); border-radius:12px; padding:12px;
            box-shadow:0 1px 3px rgba(0,0,0,.04); }
  .s-card .lab { font-size:11.5px; color:var(--t3); font-weight:700; }
  .s-card .v { font-size:17px; font-weight:800; margin-top:3px; }
  #tokenModal, #tradeModal { position:fixed; inset:0; background:rgba(0,0,0,.5); display:none;
                align-items:center; justify-content:center; z-index:50; }
  #tokenModal.show, #tradeModal.show { display:flex; }
  #tokenModal .box, #tradeModal .box { background:#fff; border-radius:14px; padding:22px;
                     max-width:90vw; width:340px; }
  #tokenModal input { width:100%; border:1.5px solid var(--line);
                      border-radius:10px; padding:11px 12px; font-size:14px;
                      font-family:inherit; }
  #tokenModal button { width:100%; background:var(--blue); color:#fff; border:0;
                       border-radius:10px; padding:11px 16px; font-size:14px;
                       font-weight:800; font-family:inherit; cursor:pointer;
                       margin-top:10px; }
  /* 추가52 (2026-06-23) — 사용자 여정 가독성 */
  .ses-hdr { font-weight:800; color:var(--t2); padding:8px 6px 4px;
             border-top:1px dashed var(--line); margin-top:4px;
             font-size:12.5px; letter-spacing:-0.2px; }
  .ses-hdr:first-child { border-top:0; margin-top:0; }
  .ses-meta { color:var(--t3); font-weight:600; font-size:11px; margin-left:6px; }
  .ev-row { border-left:3px solid transparent; padding-left:9px; }
  .ev-view  { border-left-color:#B8BEC7; }
  .ev-ai    { border-left-color:#2196F3; }
  .ev-click { border-left-color:#4CAF50; }
  .ev-cap   { border-left-color:#FF9800; }
  .ev-sch   { border-left-color:#9C27B0; }  /* 추가55 — 시공일 등록 (보라) */
  .ev-err   { border-left-color:#F44336; }
  .ev-x     { background:var(--bg); border-radius:6px; padding:1px 6px;
              font-size:11px; font-weight:800; color:var(--t2); margin-left:6px; }
  /* 추가65 (2026-06-29) — 세션 카운트 요약 + raw 접힘 */
  .ses-summary { display:flex; flex-wrap:wrap; gap:4px 6px; padding:4px 2px 2px; }
  .ses-cnt { background:var(--bg); border-radius:7px; padding:3px 8px;
             font-size:12px; color:var(--t2); white-space:nowrap; }
  .ses-cnt b { color:var(--t1); font-weight:800; margin-left:3px; }
  details.ses-raw { margin:0 0 4px; }
  details.ses-raw > summary { padding:3px 4px; font-size:10.5px; color:var(--t3);
                              cursor:pointer; list-style:none; font-weight:700;
                              text-align:right; }
  details.ses-raw > summary::-webkit-details-marker { display:none; }
  details.ses-raw[open] > summary { color:var(--t2); }
  details.ses-raw > summary:hover { color:var(--blue); }
  /* 추가67 — 1건 세션 인라인 (헤더 옆 화면 이름) */
  .ses-hdr.ses-single { padding:8px 8px 6px; border-top:1px dashed var(--line);
                        margin-top:2px; }
  .ses-hdr.ses-single .ses-inline { color:var(--t1); font-weight:800;
                                    font-size:12.5px; margin-left:4px; }
  /* 추가54 (2026-06-23) — 페이지 재설계 (Hero / 숫자 / 탭 / 접힘) */
  .hero { background:linear-gradient(135deg, #0B0F19 0%, #1B2236 100%);
          color:#fff; border-radius:16px; padding:18px 18px 16px; margin-top:14px;
          box-shadow:0 4px 12px rgba(0,0,0,.08); }
  .hero .name { font-size:20px; font-weight:800; line-height:1.25;
                display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
  .hero .name .org { color:#B8C2D0; font-weight:600; font-size:15px; }
  .hero .ph { font-size:13.5px; color:#D4DBE5; margin-top:6px; letter-spacing:0.3px; }
  .hero .ph a { color:#82B1FF; text-decoration:none; font-size:11.5px;
                font-weight:700; margin-left:6px; }
  .hero .meta-row { margin-top:12px; display:flex; align-items:center;
                    gap:10px; flex-wrap:wrap; font-size:12.5px; color:#D4DBE5; }
  .hero .meta-row b { color:#fff; font-weight:700; }
  .badge { display:inline-flex; align-items:center; gap:5px;
           padding:4px 10px; border-radius:999px;
           font-size:11.5px; font-weight:800; letter-spacing:-0.1px; }
  .badge.real { background:rgba(22,193,114,.22); color:#52E6A0; }
  .badge.peek { background:rgba(255,139,64,.22); color:#FFB983; }
  .badge.dead { background:rgba(240,67,106,.22); color:#FF8FAB; }
  .badge.cold { background:rgba(184,194,208,.22); color:#D4DBE5; }
  .big-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:8px;
              margin-top:10px; }
  .big-card { background:var(--card); border-radius:12px; padding:12px 8px;
              box-shadow:0 1px 3px rgba(0,0,0,.04); text-align:center; }
  .big-card .v { font-size:22px; font-weight:800; color:var(--t1);
                 line-height:1.1; letter-spacing:-0.5px; }
  .big-card .lab { font-size:11px; color:var(--t3); font-weight:700;
                   margin-top:4px; }
  @media (max-width:420px) {
    .big-card .v { font-size:19px; }
    .big-card .lab { font-size:10.5px; }
  }
  /* 탭 */
  .tabs { display:flex; gap:4px; background:var(--bg); padding:4px;
          border-radius:11px; margin-top:14px; overflow-x:auto; }
  .tab-btn { flex:1; min-width:max-content; padding:9px 12px; border-radius:8px;
             font-size:12.5px; font-weight:800; color:var(--t2);
             background:transparent; border:0; cursor:pointer;
             font-family:inherit; letter-spacing:-0.2px;
             transition:background .15s; }
  .tab-btn.active { background:#fff; color:var(--t1);
                    box-shadow:0 1px 2px rgba(0,0,0,.06); }
  .tab-pane { display:none; }
  .tab-pane.active { display:block; }
  /* 접힘 */
  details.more { background:var(--card); border-radius:12px; padding:0;
                 box-shadow:0 1px 3px rgba(0,0,0,.04); margin-top:14px;
                 overflow:hidden; }
  details.more > summary { padding:13px 16px; font-size:13.5px;
                           font-weight:800; color:var(--t2); cursor:pointer;
                           list-style:none; display:flex; align-items:center;
                           justify-content:space-between; }
  details.more > summary::-webkit-details-marker { display:none; }
  details.more > summary::after { content:'▾'; color:var(--t3);
                                  transition:transform .15s; }
  details.more[open] > summary::after { transform:rotate(180deg); }
  details.more > div { padding:0 16px 16px; border-top:1px solid var(--line); }
</style></head>
<body>
<div id="tokenModal"><div class="box">
  <h3 style="margin:0 0 8px">ADMIN_TOKEN 입력</h3>
  <p style="font-size:13px; color:#5A6472; margin:0 0 12px">browser 에 저장됩니다.</p>
  <input id="tokenInput" type="password" placeholder="토큰">
  <button onclick="saveToken()">저장</button>
</div></div>

<!-- 추가53 (2026-06-23) — 업종 선택 modal -->
<div id="tradeModal"><div class="box">
  <h3 style="margin:0 0 8px">업종 선택</h3>
  <p style="font-size:13px; color:#5A6472; margin:0 0 10px">아래 list 에서 고르거나, 직접 입력.</p>
  <select id="tradeSel" onchange="onTradeSelChange()"
          style="width:100%; padding:11px 12px; border:1.5px solid #E5E8EC;
                 border-radius:10px; font-size:14px; font-family:inherit;
                 background:#fff; appearance:none;
                 background-image:url('data:image/svg+xml;utf8,<svg xmlns=&quot;http://www.w3.org/2000/svg&quot; width=&quot;12&quot; height=&quot;8&quot; viewBox=&quot;0 0 12 8&quot;><path fill=&quot;%236B7280&quot; d=&quot;M6 8L0 0h12z&quot;/></svg>');
                 background-repeat:no-repeat; background-position:right 12px center;
                 padding-right:32px;"></select>
  <div id="tradeCustomWrap" style="display:none; margin-top:10px;">
    <input id="tradeCustom" type="text" placeholder="업종 직접 입력"
           style="width:100%; padding:11px 12px; border:1.5px solid #E5E8EC;
                  border-radius:10px; font-size:14px; font-family:inherit;">
  </div>
  <div style="display:flex; gap:8px; margin-top:12px;">
    <button onclick="closeTradeModal()"
            style="flex:1; background:#F1F3F5; color:#5A6472; border:0;
                   border-radius:10px; padding:11px; font-size:14px; font-weight:700;
                   font-family:inherit; cursor:pointer;">취소</button>
    <button onclick="saveTrade()"
            style="flex:1; background:#3182F6; color:#fff; border:0;
                   border-radius:10px; padding:11px; font-size:14px; font-weight:800;
                   font-family:inherit; cursor:pointer;">저장</button>
  </div>
</div></div>

<div class="wrap">
  <a class="back" href="/admin">← admin 홈</a>

  <!-- ① Hero — 누구이고 잘 쓰는지 한 줄 -->
  <div class="hero" id="hero">
    <div class="name" id="heroName">로딩중…</div>
    <div class="ph" id="heroPh"></div>
    <div class="meta-row" id="heroMeta"></div>
  </div>

  <!-- ② 핵심 숫자 4 (추가55 — "현장"→"시공일" 라벨 변경: 캘린더 KPI) -->
  <div class="big-grid">
    <div class="big-card"><div class="v" id="nSchedule">-</div><div class="lab">시공일</div></div>
    <div class="big-card"><div class="v" id="nCollab">-</div><div class="lab">협업</div></div>
    <div class="big-card"><div class="v" id="nAI">-</div><div class="lab">AI 사용</div></div>
    <div class="big-card"><div class="v" id="nDays">-</div><div class="lab">가입한지</div></div>
  </div>

  <!-- ③ 활동 (탭 — 한 번에 하나) -->
  <div class="tabs">
    <button class="tab-btn active" data-pane="paneJourney">🚶 여정</button>
    <button class="tab-btn"        data-pane="paneIntake">📋 현장</button>
    <button class="tab-btn"        data-pane="paneCollab">🤝 협업</button>
    <button class="tab-btn"        data-pane="paneAI">⚙️ AI 사용</button>
  </div>
  <div class="card">
    <div class="tab-pane active" id="paneJourney">
      <h2>🚶 사용자 여정 <span class="cnt" id="cJourney">0</span></h2>
      <div id="journeyList"><div class="empty">로딩중…</div></div>
    </div>
    <div class="tab-pane" id="paneIntake">
      <h2>📋 접수서 발급/제출 <span class="cnt" id="cIntakes">0</span></h2>
      <p style="font-size:11.5px; color:#9AA3AF; margin:0 0 8px;">
        ※ 사장님이 고객한테 시공 정보 폼 보낼 때. 캘린더 시공일은 위 "시공일" 숫자 + 여정 탭 참고.
      </p>
      <div id="intakeList"><div class="empty">로딩중…</div></div>
    </div>
    <div class="tab-pane" id="paneCollab">
      <h2>🤝 협업 현장 <span class="cnt" id="cCollab">0</span></h2>
      <div id="collabList"><div class="empty">로딩중…</div></div>
    </div>
    <div class="tab-pane" id="paneAI">
      <h2>⚙️ AI · 기능 사용 (누적)</h2>
      <div class="feat-grid" id="featGrid"><div class="empty">로딩중…</div></div>
      <p style="font-size:11px; color:#9AA3AF; margin:12px 0 0; text-align:center;">
        ※ 시간 흐름은 "여정" 탭에서 보세요.
      </p>
    </div>
  </div>

  <!-- ④ 자세히 (접힘) — 옛 메타·프로필 -->
  <details class="more">
    <summary>▸ 자세히 (가입일·메모·옛 데이터)</summary>
    <div>
      <div class="kv" id="profileKV"></div>
    </div>
  </details>
</div>

<script>
  // URL path 마지막 segment 를 phone 으로
  var PHONE = decodeURIComponent(location.pathname.split('/').filter(Boolean).pop() || '');

  function getToken() { return sessionStorage.getItem('admin_token') || ''; }
  function saveToken() {
    var t = document.getElementById('tokenInput').value.trim();
    if (!t) return;
    sessionStorage.setItem('admin_token', t);
    document.getElementById('tokenModal').classList.remove('show');
    load();
  }
  function ensureToken() {
    if (!getToken()) { document.getElementById('tokenModal').classList.add('show'); return false; }
    return true;
  }
  async function api(path) {
    var r = await fetch(path, { headers: { 'Authorization': 'Bearer ' + getToken() } });
    if (r.status === 401) { sessionStorage.removeItem('admin_token'); ensureToken(); throw new Error('인증 실패'); }
    if (!r.ok) { var t = await r.text(); throw new Error(t || 'API 오류'); }
    return r.json();
  }
  function esc(s) { s = String(s||''); return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
  function fmtDate(ms) {
    if (!ms) return '-';
    var d = new Date(ms);
    return d.toLocaleString('ko', { year:'numeric', month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit' });
  }
  function fmtRel(ms) {
    if (!ms) return '없음';
    var diff = Date.now() - ms;
    var d = Math.floor(diff / 86400000);
    if (d < 1) return '오늘';
    if (d < 7) return d + '일 전';
    if (d < 30) return Math.floor(d/7) + '주 전';
    if (d < 365) return Math.floor(d/30) + '개월 전';
    return Math.floor(d/365) + '년 전';
  }
  var FEATURE_LABEL = {
    'prepare-reply':       '답장 추천',
    'refine':              '친절 다듬기',
    'call-summary':        '통화 요약 (텍스트)',
    'call-audio-summary':  '통화 요약 (녹음)',
    'conversation-summary':'대화 요약',
    'card-summary':        '고객 카드 요약',
    'next-action-suggest': '다음 행동 제안',
    'tone-import':         '톤 학습',
    'infer-principle':     '원칙 발견',
  };

  // 추가53 (2026-06-23) — 업종 수정: select dropdown modal (prompt() 제거)
  // 안드로이드 OnboardingScreen.kt 의 Step 1 "업종 선택" 과 동일 순서·이름 (15개).
  // 안드로이드 Claude 확인: 2026-06-23.
  var TRADE_OPTIONS = [
    '줄눈',
    '실리콘·코킹',
    '도배',
    '장판·마루',
    '타일',
    '욕실 리모델링',
    '페인트·도색',
    '인테리어 필름',
    '방충망·모기장',
    '중문·샷시',
    '에어컨 설치·청소',
    '입주·이사 청소',
    '누수·방수',
    '도어·현관',
    '조명·전기'
  ];
  window.editTrade = function() {
    var current = (window._currentProfile && window._currentProfile.owner_trade) || '';
    var opts = '';
    opts += '<option value="">— 비우기 (없음) —</option>';
    for (var i=0; i<TRADE_OPTIONS.length; i++) {
      var v = TRADE_OPTIONS[i];
      var sel = (v === current) ? ' selected' : '';
      opts += '<option value="' + esc(v) + '"' + sel + '>' + esc(v) + '</option>';
    }
    // current 가 list 에 없으면 직접 입력으로
    var inList = TRADE_OPTIONS.indexOf(current) >= 0 || current === '';
    var customSel = inList ? '' : ' selected';
    opts += '<option value="__custom__"' + customSel + '>✏️ 직접 입력...</option>';
    document.getElementById('tradeSel').innerHTML = opts;
    document.getElementById('tradeCustom').value = inList ? '' : current;
    document.getElementById('tradeCustomWrap').style.display = inList ? 'none' : 'block';
    document.getElementById('tradeModal').classList.add('show');
  };
  window.closeTradeModal = function() {
    document.getElementById('tradeModal').classList.remove('show');
  };
  window.onTradeSelChange = function() {
    var v = document.getElementById('tradeSel').value;
    document.getElementById('tradeCustomWrap').style.display = (v === '__custom__') ? 'block' : 'none';
  };
  window.saveTrade = async function() {
    var sel = document.getElementById('tradeSel').value;
    var newTrade = '';
    if (sel === '__custom__') {
      newTrade = (document.getElementById('tradeCustom').value || '').trim();
      if (!newTrade) { alert('업종을 적어주세요'); return; }
    } else {
      newTrade = sel;  // '' 면 reset
    }
    try {
      var r = await fetch('/admin/beta/whitelist/' + encodeURIComponent(PHONE), {
        method: 'PATCH',
        headers: { 'Authorization': 'Bearer ' + getToken(), 'Content-Type': 'application/json' },
        body: JSON.stringify({ owner_trade: newTrade })
      });
      if (!r.ok) { alert('실패: ' + r.status + ' ' + (await r.text())); return; }
      closeTradeModal();
      load();
    } catch(e) { alert('실패: ' + e.message); }
  };

  // 추가54 (2026-06-23) — 페이지 재설계: Hero / 숫자 / 탭 / 접힘
  // 짧은 상대 시각 (load 밖에도 쓰게 외부에 둠)
  function fmtShort(ms) {
    if (!ms) return '-';
    var diff = Date.now() - ms;
    if (diff < 0) return '방금';
    var sec = Math.floor(diff / 1000);
    if (sec < 60) return '방금';
    var min = Math.floor(sec / 60);
    if (min < 60) return min + '분 전';
    var hr = Math.floor(min / 60);
    if (hr < 24) return hr + '시간 전';
    var day = Math.floor(hr / 24);
    if (day === 1) return '어제';
    if (day < 7) return day + '일 전';
    return Math.floor(day / 7) + '주 전';
  }
  // 등급 결정 — 마지막 활동 + 누적 사용
  function userGrade(lastMs, useCount) {
    if (!lastMs) return ['cold', '⚫ 진입 안 함'];
    var d = (Date.now() - lastMs) / 86400000;
    if (d < 1 && (useCount || 0) >= 5) return ['real', '🟢 진성'];
    if (d < 1)  return ['peek', '🟡 사용중'];
    if (d < 7)  return ['peek', '🟡 띄엄띄엄'];
    if (d < 30) return ['dead', '🟠 잠수'];
    return ['cold', '⚫ 휴면'];
  }
  // 폰 포맷 (010-1234-5678)
  function fmtPhone(p) {
    p = String(p||'').replace(/[^0-9]/g,'');
    if (p.length === 11) return p.slice(0,3) + '-' + p.slice(3,7) + '-' + p.slice(7);
    if (p.length === 10) return p.slice(0,3) + '-' + p.slice(3,6) + '-' + p.slice(6);
    return p;
  }
  // 탭 전환 (1번 init)
  function initTabs() {
    var btns = document.querySelectorAll('.tab-btn');
    btns.forEach(function(btn) {
      btn.addEventListener('click', function() {
        var paneId = btn.getAttribute('data-pane');
        btns.forEach(function(b){ b.classList.remove('active'); });
        document.querySelectorAll('.tab-pane').forEach(function(p){ p.classList.remove('active'); });
        btn.classList.add('active');
        document.getElementById(paneId).classList.add('active');
      });
    });
  }

  async function load() {
    if (!ensureToken()) return;
    try {
      var d = await api('/admin/user/' + encodeURIComponent(PHONE) + '/data');
      var p = d.profile;
      window._currentProfile = p;

      // ── ① Hero — 누구이고 잘 쓰는지 ─────────────────────
      var displayName = p.name || p.registered_name || '(이름 없음)';
      var org = p.memo ? '· ' + esc(p.memo) : '';
      var industryTxt = p.industry
        ? esc(p.industry)
        : '<span style="color:#B8C2D0">업종 미입력</span>';
      var industryEdit = ' <a href="javascript:editTrade()">[' + (p.industry?'수정':'설정') + ']</a>';
      var grade = userGrade(p.last_seen_ms, p.use_count);
      var lastTxt = p.last_seen_ms ? fmtShort(p.last_seen_ms) + ' 진입' : '진입 기록 없음';

      document.getElementById('heroName').innerHTML =
        esc(displayName) + (org ? ' <span class="org">' + org + '</span>' : '');
      document.getElementById('heroPh').innerHTML =
        '📞 ' + esc(fmtPhone(p.phone)) + ' · 🔧 ' + industryTxt + industryEdit;
      document.getElementById('heroMeta').innerHTML =
        '<span class="badge ' + grade[0] + '">' + grade[1] + '</span>'
        + '<span>· ' + lastTxt + '</span>'
        + '<span>· 누적 <b>' + (p.use_count||0) + '</b>번 실행</span>';

      // ── ② 숫자 4 ─────────────────────────────────────
      var totalCollab = d.shared_sent.length + d.shared_received.length;
      var totalAI = 0;
      for (var k in d.feature_counts) totalAI += d.feature_counts[k] || 0;
      var addedMs = p.added_at_ms || p.first_seen_ms;
      var nDays = addedMs ? Math.max(1, Math.floor((Date.now() - addedMs) / 86400000)) : null;

      // 추가55 — "시공일" = schedule_create 이벤트 누적 (캘린더 KPI)
      document.getElementById('nSchedule').textContent = (d.schedule_count || 0);
      document.getElementById('nCollab').textContent   = totalCollab;
      document.getElementById('nAI').textContent       = totalAI;
      document.getElementById('nDays').textContent     = nDays !== null ? nDays + '일' : '-';

      // ── ③-A 현장 탭 ──────────────────────────────────
      document.getElementById('cIntakes').textContent = d.intakes.length;
      var ihtml = '';
      if (d.intakes.length === 0) {
        ihtml = '<div class="empty">아직 접수서·일정 등록한 게 없어요</div>';
      } else {
        for (var i=0; i<d.intakes.length; i++) {
          var it = d.intakes[i];
          var statusPill = it.status === 'submitted'
            ? '<span class="pill ok">제출됨</span>'
            : '<span class="pill pending">발급만</span>';
          ihtml += '<div class="item">'
            + '<div class="title">' + esc(it.customer_name || '(고객 미상)') + ' ' + statusPill + '</div>'
            + '<div class="sub2">'
            + '발급: ' + fmtDate(it.issued_at_ms)
            + (it.submitted_at_ms ? ' · 제출: ' + fmtDate(it.submitted_at_ms) : '')
            + (it.summary ? ' · ' + esc(it.summary) : '')
            + '</div></div>';
        }
      }
      document.getElementById('intakeList').innerHTML = ihtml;

      // ── ③-B 협업 탭 (보냄+받음 통합, 시간순 DESC) ─────
      var merged = [];
      for (var i=0; i<d.shared_sent.length; i++) {
        merged.push(Object.assign({_dir:'sent'}, d.shared_sent[i]));
      }
      for (var i=0; i<d.shared_received.length; i++) {
        merged.push(Object.assign({_dir:'recv'}, d.shared_received[i]));
      }
      merged.sort(function(a,b){ return (b.scheduled_at_ms||0) - (a.scheduled_at_ms||0); });
      document.getElementById('cCollab').textContent = merged.length;
      // 추가91 — 협업 탭 가독성 (사장님: "보기 어렵다")
      //   ① 상태 2개(status+progress 영어) → 한글 한 개로 통합
      //   ② 긴 주소 → 아파트명 크게 + 지역 작게
      //   ③ 보냄/받음 → 색 칩 ("→ 내가 공유" / "← 받은 현장")
      function collabState(s) {
        if ((s.progress || '') === 'completed') return { t:'✅ 완료',     bg:'#E7F8EF', fg:'#0B7A45' };
        if (s.status === 'pending')  return { t:'⏳ 수락 대기', bg:'#FFF8E1', fg:'#7A5A00' };
        if (s.status === 'declined') return { t:'❌ 거절됨',   bg:'#FFF2F5', fg:'#F0436A' };
        if (s.status === 'ended')    return { t:'⏹ 종료',     bg:'#F1F2F4', fg:'#5A6472' };
        var p = { assigned:'🤝 배정됨', departed:'🚗 출발함', working:'🔨 작업 중' }[s.progress || ''];
        return { t: p || '🤝 진행 중', bg:'#EEF4FF', fg:'#1B64DA' };
      }
      function collabTitle(t) {
        t = (t || '협업 현장').replace(/\s*현장\s*$/, '');
        var m = t.match(/\(([^)]+)\)/);
        if (m) {
          // "인천 부평구 원적로..." → 지역 앞 2단어만 부제로
          var region = t.split(' ').slice(0, 2).join(' ');
          return { main: m[1], sub: region };
        }
        return { main: t, sub: '' };
      }
      var chtml = '';
      if (merged.length === 0) chtml = '<div class="empty">협업 현장 없음</div>';
      else {
        for (var i=0; i<merged.length; i++) {
          var s = merged[i];
          var counter = s._dir === 'sent' ? s.partner_name : s.owner_name;
          var dirChip = s._dir === 'sent'
            ? '<span style="background:#EEF4FF; color:#1B64DA; padding:2px 8px; border-radius:6px; font-size:10.5px; font-weight:800;">→ 내가 공유</span>'
            : '<span style="background:#F3E8FF; color:#7C3AED; padding:2px 8px; border-radius:6px; font-size:10.5px; font-weight:800;">← 받은 현장</span>';
          var tt = collabTitle(s.title);
          var st = collabState(s);
          chtml += '<div class="item">'
            + '<div class="title" style="display:flex; align-items:center; gap:7px; flex-wrap:wrap;">'
            + dirChip
            + '<span>' + esc(tt.main) + '</span>'
            + (tt.sub ? '<span style="color:#9AA3AF; font-size:11.5px; font-weight:500;">' + esc(tt.sub) + '</span>' : '')
            + '</div>'
            + '<div class="sub2" style="display:flex; align-items:center; gap:8px; flex-wrap:wrap; margin-top:3px;">'
            + '<span style="background:' + st.bg + '; color:' + st.fg + '; padding:2px 8px; border-radius:6px; font-size:11px; font-weight:700;">' + st.t + '</span>'
            + (counter ? '<span>🤝 <b>' + esc(counter) + '</b></span>' : '')
            + (s.scheduled_at_ms ? '<span>📅 시공 ' + fmtDate(s.scheduled_at_ms) + '</span>' : '')
            + '</div></div>';
        }
      }
      document.getElementById('collabList').innerHTML = chtml;

      // 기능별 사용량
      var fhtml = '';
      var feats = Object.keys(d.feature_counts || {}).sort(function(a,b){
        return (d.feature_counts[b] || 0) - (d.feature_counts[a] || 0);
      });
      if (feats.length === 0) fhtml = '<div class="empty">아직 기능 사용 없음</div>';
      else {
        for (var i=0; i<feats.length; i++) {
          var ep = feats[i];
          fhtml += '<div class="feat"><span class="lab">'
            + esc(FEATURE_LABEL[ep] || ep) + '</span><span class="n">'
            + (d.feature_counts[ep] || 0) + '</span></div>';
        }
      }
      document.getElementById('featGrid').innerHTML = fhtml;

      // 추가54+ (2026-06-23) — 옛 "최근 timeline" 제거됨 (여정 탭으로 통합).
      // cRecent / recentList element 가 없어서 호출하면 null 에러 → 통째 삭제.

      // 추가52 (2026-06-23) — 사용자 여정 timeline (세션 묶음 + 압축 + 한글)
      var journey = d.events_journey || [];
      document.getElementById('cJourney').textContent = journey.length;
      var EVENT_ICON = {
        'screen_view':    '👀',
        'button_click':   '👆',
        'screenshot':     '📸',
        'feature_use':    '⚙️',
        'llm_use':        '⚙️',
        'schedule_create':'📅',  // 추가55 — 시공일 등록
        'error':          '⚠️',
      };
      var EVENT_LABEL = {
        'screen_view':    '화면 진입',
        'button_click':   '버튼',
        'screenshot':     '캡쳐',
        'feature_use':    'AI 사용',
        'llm_use':        'AI 사용',
        'schedule_create':'시공일 등록',  // 추가55
        'error':          '에러',
      };
      var EVENT_CLASS = {
        'screen_view':    'ev-view',
        'button_click':   'ev-click',
        'screenshot':     'ev-cap',
        'feature_use':    'ev-ai',
        'llm_use':        'ev-ai',
        'schedule_create':'ev-sch',  // 추가55
        'error':          'ev-err',
      };
      var SCREEN_LABEL = {
        'home':            '홈',
        'chat':            '채팅',
        'collab':          '협업현장',
        'collab_inbox':    '협업 인박스',
        'intake_form':     '접수서',
        'schedule':        '일정',
        'customer':        '고객 상세',
        'customer_detail': '고객 상세',
        'call':            '통화상담',
        'team':            '팀원',
        'settings':        '설정',
        'onboarding':      '온보딩',
        // 추가64 (2026-06-29) — 안드로이드 새 화면 매핑 (송도 사장님 여정 진단 후 발견)
        'settlement':      '정산',
        'stats':           '통계',
        'profile':         '내 프로필',
        'business':        '업체 정보',
        'business_info':   '업체 정보',   // 추가66
        'account':         '계좌',
        'notification':    '알림',
        'recruit':         '일당 모집',
        'recruit_inbox':   '일당 지원함',
        'laborer':         '팀원(일당)',
        'billing':         '결제',
        'help':            '도움말',
        'privacy':         '개인정보',
        'tone':            '톤 학습',
        'principle':       '원칙 발견',
        'photo':           '사진',
        'photo_gallery':   '사진 갤러리',
        // 추가66 (2026-06-29) — 추가 발견
        'collab_sites':    '협업현장',
        'pricing_items':   '가격표',
        'customers':       '고객 목록',
        // 추가67 (2026-06-29) — 안드로이드 답 (모든 route 목록 받음)
        'visited':         '방문 예정',
        'search':          '검색',
        'new_leads':       '신규 리드',
        'settings_autosms':'자동문자 설정',
        'closing_brief':   '마감 브리핑',
        'notebook':        '노트',
        'report':          '리포트',
        'trade_select':    '업종 선택',
        'recurring':       '정기 반복',
        'recurring_due':   '반복 예정',
        'schedule_reminder':'일정 알림',
        'estimate_followup':'견적 팔로업',
        'ai_message':      'AI 메시지',
        'style_learning':  '톤 학습',
        'principles':      '원칙',
        'spam_list':       '스팸 목록',
        'personal_list':   '개인 목록',
        'templates':       '템플릿',
        'template_edit':   '템플릿 편집',
        'schedule_add':    '일정 추가',
        'login':           '로그인',
        'permissions':     '권한 허용',
        'follow_up':       '팔로업',
        'call_summary':    '통화 요약',
      };

      // 짧은 상대 시각 (방금 / 5분 전 / 2시간 전 / 어제 / N일 전)
      function fmtShort(ms) {
        if (!ms) return '-';
        var diff = Date.now() - ms;
        if (diff < 0) return '방금';
        var sec = Math.floor(diff / 1000);
        if (sec < 60) return '방금';
        var min = Math.floor(sec / 60);
        if (min < 60) return min + '분 전';
        var hr = Math.floor(min / 60);
        if (hr < 24) return hr + '시간 전';
        var day = Math.floor(hr / 24);
        if (day === 1) return '어제';
        if (day < 7) return day + '일 전';
        return Math.floor(day / 7) + '주 전';
      }
      // 세션 헤더 시간 표시 (오늘/어제/날짜 + 시각 범위)
      function fmtSesHdr(start, end) {
        var s = new Date(start), e = new Date(end);
        var diffDay = Math.floor((Date.now() - end) / 86400000);
        var dayLbl;
        if (diffDay < 1) dayLbl = '오늘';
        else if (diffDay < 2) dayLbl = '어제';
        else dayLbl = (s.getMonth()+1) + '월 ' + s.getDate() + '일';
        var hm = function(d) {
          var h = d.getHours(), m = d.getMinutes();
          var ap = h < 12 ? '오전' : '오후';
          var h12 = h % 12; if (h12 === 0) h12 = 12;
          return ap + ' ' + h12 + ':' + (m<10?'0':'') + m;
        };
        if (Math.abs(end - start) < 60000) return dayLbl + ' ' + hm(s);
        return dayLbl + ' ' + hm(s) + ' ~ ' + hm(e);
      }

      var jhtml = '';
      if (journey.length === 0) {
        jhtml = '<div class="empty">아직 여정 데이터 없음 (안드로이드 측 이벤트 발사 후 보임)</div>';
      } else {
        // 1) ASC 정렬 (세션 분리는 시간순으로 한 번 훑어야 함)
        var asc = journey.slice().sort(function(a,b){ return a.at_ms - b.at_ms; });
        // 2) 세션 분리 (5분 이상 gap)
        var GAP = 5*60*1000;
        var sessions = [];
        var cur = null;
        for (var i=0; i<asc.length; i++) {
          var ev = asc[i];
          if (!cur || ev.at_ms - cur.end_ms > GAP) {
            cur = { start_ms: ev.at_ms, end_ms: ev.at_ms, events: [] };
            sessions.push(cur);
          }
          cur.end_ms = ev.at_ms;
          cur.events.push(ev);
        }
        // 3) 각 세션 안 연속 같은 (event,screen,target) 압축
        function compress(list) {
          var out = [];
          for (var i=0; i<list.length; i++) {
            var e = list[i];
            var last = out[out.length-1];
            var same = last && last.event_name === e.event_name
                       && last.screen === e.screen
                       && (last.target||'') === (e.target||'');
            if (same) {
              last.count += 1;
              last.last_ms = e.at_ms;
            } else {
              out.push({
                event_name: e.event_name, screen: e.screen, target: e.target,
                count: 1, last_ms: e.at_ms
              });
            }
          }
          return out;
        }
        // 4) HTML — 세션은 최근(아래)→옛(위)순, 세션 안은 시간 흐름 그대로 (옛→새)
        // 추가65 (2026-06-29) — 세션별 "화면 카운트 요약" + raw timeline 접힘.
        // 추가67 (2026-06-29) — 1건 세션은 인라인 축약 (헤더 옆에 한 줄).
        // 사장님 의도: "이 사람이 뭐 하고 나갔는지 이해가 안 된다" — 21줄 나열 대신 한 줄 요약.
        for (var s=sessions.length-1; s>=0; s--) {
          var ses = sessions[s];
          var compressed = compress(ses.events);
          var durSec = Math.round((ses.end_ms - ses.start_ms) / 1000);
          var durLbl;
          if (durSec < 60) durLbl = durSec + '초';
          else durLbl = Math.round(durSec/60) + '분';

          // 추가67 — 1건 세션 = 인라인 축약 (헤더 옆 그 한 건 표시). 요약 카드 + 자세히 접힘 X.
          if (ses.events.length === 1) {
            var ev0 = ses.events[0];
            var sc0 = SCREEN_LABEL[ev0.screen] || ev0.screen || '(빈)';
            var lbl0;
            if (ev0.event_name === 'llm_use' || ev0.event_name === 'feature_use') lbl0 = '⚙️ AI · ' + sc0;
            else if (ev0.event_name === 'schedule_create') lbl0 = '📅 시공일 등록';
            else if (ev0.event_name === 'screenshot') lbl0 = '📸 캡쳐 · ' + sc0;
            else if (ev0.event_name === 'button_click') lbl0 = '👆 ' + (ev0.target || '버튼');
            else lbl0 = sc0;
            jhtml += '<div class="ses-hdr ses-single">🕐 ' + fmtSesHdr(ses.start_ms, ses.end_ms)
                  + '<span class="ses-inline"> · ' + esc(lbl0) + '</span></div>';
            continue;
          }

          jhtml += '<div class="ses-hdr">🕐 ' + fmtSesHdr(ses.start_ms, ses.end_ms)
                + '<span class="ses-meta">· ' + durLbl + ' · ' + ses.events.length + '건</span></div>';

          // 화면 카운트 집계 (event_name + screen 기준, target 무시)
          // 추가68 — screen_view 는 아이콘 없이 (default = 진입). 다른 event 만 아이콘.
          var scCounts = {};
          var scOrder = [];
          for (var i=0; i<ses.events.length; i++) {
            var ev = ses.events[i];
            var evScreen = SCREEN_LABEL[ev.screen] || ev.screen || '(빈)';
            var scKey;
            if (ev.event_name === 'llm_use' || ev.event_name === 'feature_use') {
              scKey = '⚙️ AI · ' + evScreen;
            } else if (ev.event_name === 'schedule_create') {
              scKey = '📅 시공일 등록';
            } else if (ev.event_name === 'screenshot') {
              scKey = '📸 캡쳐 · ' + evScreen;
            } else if (ev.event_name === 'button_click') {
              scKey = '👆 ' + (ev.target || '버튼');
            } else {
              // screen_view = 아이콘 없이 이름만 (default 라 노이즈 제거)
              scKey = evScreen;
            }
            if (scCounts[scKey] === undefined) {
              scCounts[scKey] = 0;
              scOrder.push(scKey);
            }
            scCounts[scKey] += 1;
          }
          // 카운트 내림차순 정렬 (동률 = 최초 등장 순)
          scOrder.sort(function(a, b) {
            var d = scCounts[b] - scCounts[a];
            return d !== 0 ? d : scOrder.indexOf(a) - scOrder.indexOf(b);
          });
          var summaryHtml = '';
          for (var m=0; m<scOrder.length; m++) {
            var k = scOrder[m];
            summaryHtml += '<div class="ses-cnt">' + esc(k) + ' <b>' + scCounts[k] + '회</b></div>';
          }
          jhtml += '<div class="ses-summary">' + summaryHtml + '</div>';

          // Raw timeline = 접힘 (기본 닫힘). 필요 시 자세히 눌러서 열기.
          var detailsHtml = '';
          for (var k2=0; k2<compressed.length; k2++) {
            var c = compressed[k2];
            var icon2 = EVENT_ICON[c.event_name] || '·';
            var screen2 = SCREEN_LABEL[c.screen] || c.screen || '';
            var lbl2 = EVENT_LABEL[c.event_name] || c.event_name;
            var cls2 = EVENT_CLASS[c.event_name] || '';
            var countTxt2 = c.count > 1 ? '<span class="ev-x">×' + c.count + '</span>' : '';
            var tgtTxt2 = c.target ? ' · ' + esc(c.target) : '';
            detailsHtml += '<div class="item ev-row ' + cls2 + '">'
              + '<div class="title">' + icon2 + ' ' + esc(screen2) + countTxt2 + tgtTxt2 + '</div>'
              + '<div class="sub2">' + fmtShort(c.last_ms) + ' · ' + lbl2 + '</div></div>';
          }
          jhtml += '<details class="ses-raw"><summary>⋯ 자세히</summary>' + detailsHtml + '</details>';
        }
      }
      document.getElementById('journeyList').innerHTML = jhtml;

      // 프로필 메타
      var pk = '';
      pk += '<div class="k">폰</div><div>' + esc(p.phone) + '</div>';
      pk += '<div class="k">이름(메모)</div><div>' + esc(p.name || '-') + '</div>';
      pk += '<div class="k">메모</div><div>' + esc(p.memo || '-') + '</div>';
      pk += '<div class="k">가입자명</div><div>' + esc(p.registered_name || '-') + '</div>';
      pk += '<div class="k">화이트리스트 등록</div><div>' + fmtDate(p.added_at_ms) + '</div>';
      pk += '<div class="k">첫 진입</div><div>' + fmtDate(p.first_seen_ms) + '</div>';
      pk += '<div class="k">마지막 진입</div><div>' + fmtDate(p.last_seen_ms) + ' (' + fmtRel(p.last_seen_ms) + ')</div>';
      pk += '<div class="k">앱 실행 횟수</div><div>' + (p.use_count || 0) + '</div>';
      document.getElementById('profileKV').innerHTML = pk;

    } catch(e) {
      document.getElementById('heroName').textContent = '로드 실패: ' + e.message;
    }
  }
  initTabs();
  load();
</script></body></html>
"""


@app.get("/admin/user/{phone}", response_class=HTMLResponse, include_in_schema=False)
async def admin_user_detail_page(phone: str):
    """사용자 종합 활동 페이지 HTML — phone path 는 JS 가 사용."""
    return HTMLResponse(content=_ADMIN_USER_DETAIL_HTML)


# ============================================================================
# 추가33 (2026-06-15) — admin 홈 허브 (PWA — 사장님 폰 홈화면)
# ─────────────────────────────────────────────────────────────────────────────
# /admin → 사장님 전용 콘솔 홈. PWA 로 설치 → 폰 홈화면에서 바로 진입.
# 진입 카드: 베타 대시보드 / 화이트리스트 / 베타 신청자 / 베타 인테이크.
# ============================================================================


@app.get("/admin/home/data")
async def admin_home_data(authorization: Optional[str] = Header(default=None)) -> dict:
    """홈 허브 미리보기 — 3개 카드용 통계 한 번에."""
    _admin_auth_bearer_from_header(authorization)
    now = _now_ms()
    cutoff_7d = now - 7 * 86_400_000
    with db_conn() as con:
        # 화이트리스트
        wl_total = con.execute("SELECT COUNT(*) FROM beta_whitelist").fetchone()[0]
        wl_active = con.execute(
            "SELECT COUNT(*) FROM beta_whitelist WHERE last_seen_ms >= ?", (cutoff_7d,)
        ).fetchone()[0]
        wl_pending = con.execute(
            "SELECT COUNT(*) FROM beta_whitelist WHERE first_seen_ms IS NULL"
        ).fetchone()[0]
        # 베타 신청자
        signup_total = con.execute("SELECT COUNT(*) FROM beta_signups").fetchone()[0]
        signup_pending = con.execute(
            "SELECT COUNT(*) FROM beta_signups WHERE status = 'pending'"
        ).fetchone()[0]
        signup_7d = con.execute(
            "SELECT COUNT(*) FROM beta_signups WHERE created_at_ms >= ?", (cutoff_7d,)
        ).fetchone()[0]
        # 대시보드 미리보기 — 활성 사용자 + 7일 호출
        api_7d = con.execute(
            "SELECT COUNT(*) FROM api_usage WHERE created_at_ms >= ?", (cutoff_7d,)
        ).fetchone()[0]
        cost_7d = con.execute(
            "SELECT COALESCE(SUM(cost_krw),0) FROM llm_usage_log WHERE timestamp_ms >= ?",
            (cutoff_7d,),
        ).fetchone()[0]
    return {
        "whitelist": {"total": wl_total, "active_7d": wl_active, "pending": wl_pending},
        "signups": {"total": signup_total, "pending": signup_pending, "new_7d": signup_7d},
        "dashboard": {"active_7d": wl_active, "api_calls_7d": api_7d, "cost_krw_7d": round(cost_7d or 0, 0)},
    }


_ADMIN_HOME_HTML = """<!doctype html>
<html lang="ko"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#3182F6">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="시공막내 admin">
<link rel="manifest" href="/manifest/admin.webmanifest">
<link rel="apple-touch-icon" href="/manifest/admin-icon.svg">
<title>시공막내 admin · 콘솔</title>
<style>
  :root { --blue:#3182F6; --blue-dark:#1B64DA; --blue-tint:#EEF4FF;
          --bg:#F4F5F7; --card:#fff;
          --t1:#0B0F19; --t2:#5A6472; --t3:#9AA3AF; --line:#EEF0F3;
          --success:#16C172; --warning:#F59E0B; --error:#F0436A;
          --shadow:0 1px 3px rgba(0,0,0,.04); --shadow-lg:0 8px 24px rgba(0,0,0,.08); }
  * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
  html, body { margin:0; padding:0; background:var(--bg); }
  body { font-family:'Pretendard',-apple-system,system-ui,sans-serif; color:var(--t1); line-height:1.5; min-height:100vh; }
  .wrap { max-width:560px; margin:0 auto; padding:20px 16px 50px; }
  .head { display:flex; align-items:center; gap:12px; margin-bottom:6px; }
  .brand-mark { width:42px; height:42px; border-radius:12px;
                background:linear-gradient(135deg,var(--blue),var(--blue-dark));
                color:#fff; font-size:18px; font-weight:900;
                display:flex; align-items:center; justify-content:center; }
  .brand-name { font-size:18px; font-weight:800; }
  .brand-sub { font-size:11.5px; color:var(--t3); font-weight:700; }
  h1 { font-size:23px; font-weight:800; margin:18px 0 4px; }
  .sub { font-size:13px; color:var(--t2); margin-bottom:20px; }

  .pwa-install { display:block; width:100%; background:#fff; color:var(--blue-dark);
                 border:1.5px solid var(--blue); border-radius:12px; padding:13px;
                 font-size:14px; font-weight:800; cursor:pointer; font-family:inherit;
                 margin-bottom:18px; }
  .pwa-install:active { background:var(--blue-tint); }

  .menu-grid { display:grid; gap:12px; }
  .menu-card { background:var(--card); border-radius:16px; padding:18px; box-shadow:var(--shadow);
               cursor:pointer; transition:transform .12s, box-shadow .12s;
               display:flex; align-items:center; gap:14px; }
  .menu-card:hover { transform:translateY(-1px); box-shadow:var(--shadow-lg); }
  .menu-card:active { transform:scale(0.99); }
  .menu-card .icon { width:56px; height:56px; border-radius:14px; flex:0 0 56px;
                     display:flex; align-items:center; justify-content:center;
                     font-size:28px; }
  .menu-card .icon.blue { background:var(--blue-tint); }
  .menu-card .icon.green { background:#E7F8EF; }
  .menu-card .icon.orange { background:#FFF8E1; }
  .menu-card .body { flex:1; min-width:0; }
  .menu-card .title { font-size:16px; font-weight:800; color:var(--t1); margin-bottom:2px; }
  .menu-card .desc { font-size:12.5px; color:var(--t2); margin-bottom:6px; }
  .menu-card .stats { display:flex; gap:10px; flex-wrap:wrap; font-size:11.5px; color:var(--t3); font-weight:700; }
  .menu-card .stats b { color:var(--blue-dark); font-size:13px; }
  .menu-card .arrow { font-size:18px; color:var(--t3); }

  .foot { margin-top:30px; padding:14px; background:#fff; border-radius:12px;
          text-align:center; font-size:11.5px; color:var(--t3); box-shadow:var(--shadow); }
  .foot a { color:var(--blue-dark); text-decoration:none; font-weight:700; }

  /* 베타 모집 링크 카드 */
  .share-card { background:var(--card); border-radius:16px; padding:18px; box-shadow:var(--shadow);
                margin-top:22px; }
  .share-card h3 { margin:0 0 6px; font-size:16px; font-weight:800; }
  .share-desc { font-size:12.5px; color:var(--t2); margin:0 0 14px; }
  .share-url-box { background:var(--blue-tint); border:1.5px dashed var(--blue); border-radius:12px;
                   padding:14px; cursor:pointer; transition:background .12s;
                   display:flex; flex-direction:column; align-items:center; gap:6px; }
  .share-url-box:hover { background:#DDE9FB; }
  .share-url-box:active { transform:scale(0.99); }
  .share-url { font-size:17px; font-weight:800; color:var(--blue-dark); font-family:monospace; }
  .share-copy-hint { font-size:11.5px; color:var(--blue-dark); font-weight:700; }
  .share-msg { width:100%; border:1.5px solid var(--line); border-radius:10px; padding:12px;
               font-size:13px; font-family:inherit; line-height:1.6; color:var(--t1);
               margin-top:12px; resize:vertical; min-height:140px; }
  .share-msg:focus { outline:none; border-color:var(--blue); }
  .share-actions { display:grid; grid-template-columns:1fr 1fr; gap:8px; margin-top:10px; }
  .share-btn { background:#fff; border:1.5px solid var(--line); border-radius:10px;
               padding:12px 10px; font-size:13px; font-weight:800; cursor:pointer; font-family:inherit;
               color:var(--t1); }
  .share-btn.primary { background:var(--blue); color:#fff; border-color:var(--blue); }
  .share-btn:active { transform:scale(0.98); }
  .share-toast { margin-top:10px; font-size:12.5px; font-weight:700; text-align:center;
                 color:var(--success); min-height:18px; transition:opacity .3s; }

  /* PWA 설치 안내 시트 */
  .pwa-sheet { position:fixed; inset:0; background:rgba(0,0,0,.5); display:none;
               align-items:flex-end; justify-content:center; z-index:60; }
  .pwa-sheet.show { display:flex; }
  .pwa-sheet-card { width:100%; max-width:560px; background:#fff; border-radius:16px 16px 0 0;
                    padding:22px 20px 28px; }
  .pwa-sheet-card h3 { margin:0 0 10px; font-size:17px; font-weight:800; }
  .pwa-sheet-card p { margin:0 0 18px; font-size:14px; color:var(--t2); line-height:1.6; }
  .pwa-sheet-close { width:100%; background:var(--blue); color:#fff; border:0; border-radius:12px;
                     padding:14px; font-size:15px; font-weight:800; font-family:inherit; cursor:pointer; }

  /* 토큰 모달 */
  #tokenModal { position:fixed; inset:0; background:rgba(0,0,0,.6); display:none;
                align-items:center; justify-content:center; z-index:50; padding:20px; }
  #tokenModal.show { display:flex; }
  #tokenModal .box { background:#fff; border-radius:14px; padding:24px; max-width:90vw; width:360px; }
  #tokenModal input { width:100%; border:1.5px solid var(--line); border-radius:10px; padding:12px;
                      font-size:14px; font-family:inherit; margin:10px 0; }
  #tokenModal button { width:100%; background:var(--blue); color:#fff; border:0; border-radius:10px;
                       padding:12px; font-size:14px; font-weight:800; cursor:pointer; font-family:inherit; }
</style></head>
<body>
<div id="tokenModal"><div class="box">
  <h3 style="margin:0 0 6px">관리자 인증</h3>
  <p style="font-size:13px; color:#5A6472; margin:0">ADMIN_TOKEN 입력. 브라우저에 저장됩니다.</p>
  <input id="tokenInput" type="password" placeholder="토큰">
  <button onclick="saveToken()">시작</button>
</div></div>

<div class="pwa-sheet" id="pwa-sheet" onclick="if(event.target.id==='pwa-sheet')closePwaSheet()">
  <div class="pwa-sheet-card">
    <h3>📲 홈 화면에 추가하기</h3>
    <p id="pwa-sheet-msg">브라우저 메뉴를 열고 "현재 페이지를 홈 화면에 추가" 를 눌러주세요.</p>
    <button class="pwa-sheet-close" onclick="closePwaSheet()">확인</button>
  </div>
</div>

<div class="wrap">
  <div class="head">
    <div class="brand-mark">R</div>
    <div>
      <div class="brand-name">RING-GO</div>
      <div class="brand-sub">시공막내 admin</div>
    </div>
  </div>
  <h1>👋 안녕하세요, 사장님!</h1>
  <p class="sub">베타 운영 콘솔 — 대시보드 · 화이트리스트 · 신청자 관리</p>

  <button class="pwa-install" type="button" onclick="onInstallClick()">📲 홈 화면에 추가</button>

  <div class="menu-grid">
    <a href="/admin/beta/dashboard" class="menu-card" style="text-decoration:none; color:inherit;">
      <div class="icon blue">📊</div>
      <div class="body">
        <div class="title">종합 대시보드 · 멤버 관리</div>
        <div class="desc">테스터 활동 · 멤버 추가/제거 · LLM 비용</div>
        <div class="stats" id="statsDashboard">로딩...</div>
        <div class="stats" id="statsWhitelist">로딩...</div>
      </div>
      <div class="arrow">›</div>
    </a>

    <a href="/admin/beta/signups" class="menu-card" style="text-decoration:none; color:inherit;">
      <div class="icon orange">📝</div>
      <div class="body">
        <div class="title">베타 신청자</div>
        <div class="desc">si0in.kr 가입 폼 들어온 신청자</div>
        <div class="stats" id="statsSignups">로딩...</div>
      </div>
      <div class="arrow">›</div>
    </a>

    <!-- 추가56 (2026-06-25) — 베타 인테이크 폼 카드 제거. 페이지 (/admin/beta/intake) 자체는
         유지 (사장님이 직접 URL 치면 접근 가능). 사장님이 안 쓰는 죽은 카드라 admin 홈에서만 뺌. -->

    <!-- 추가70 정착 대시보드 카드 → 추가89 (2026-07-03) 종합 대시보드로 흡수 (카드 제거).
         /admin/adoption 은 리다이렉트로 호환. -->

    <a href="/admin/usage-chart" class="menu-card" style="text-decoration:none; color:inherit;">
      <div class="icon green">📈</div>
      <div class="body">
        <div class="title">LLM 사용량 차트</div>
        <div class="desc">prepare-reply / refine / call-summary 사용량 시계열</div>
        <div class="stats"><span>일별 · 모델별 토큰·비용</span></div>
      </div>
      <div class="arrow">›</div>
    </a>
  </div>

  <!-- 베타 모집 링크 공유 -->
  <div class="card share-card">
    <h3>📤 베타 모집 링크</h3>
    <p class="share-desc">다른 시공 사장님께 보낼 신청 링크예요. 탭해서 공유하거나 복사하세요.</p>

    <div class="share-url-box" onclick="copyUrl()">
      <span class="share-url" id="shareUrl">https://si0in.kr</span>
      <span class="share-copy-hint">📋 탭하면 복사</span>
    </div>

    <textarea class="share-msg" id="shareMsg" rows="14">🛠️ 시공막내가 등장했어요!

1인 시공 사장님을 위한 운영 비서.

📞 통화하면 알아서 요약해줘요
   "그때 25만원 말했었나..?" 더 이상 헷갈리지 마세요

💬 똑같은 패턴 지겹지 않으세요?
   문자 답장을 사장님 톤으로 미리 준비

📋 시공접수서로 고객이 직접 주소 등록
   매번 받아 적던 거, 이젠 고객이 입력

💰 견적도 클릭 한 번이면 끝
   "화장실 1곳, 베란다 1곳..." 매번 똑같이 말하던 거,
   부위별 단가 미리 박아두고 탭하면 견적 완성

📊 시공 매출까지 자동 집계
   누가 얼마 냈는지 노트에 안 적어도 됩니다

✍️ 블로그글이 알아서 생성돼요
   고객과의 통화·문자 기반으로 자연스럽게

📸 핸드폰 번호에 현장사진 등록
   그 고객 카드에 시공한 사진이 다 함께

베타 무료, 줄눈/타일 사장님 위주 모집!
신청 ↓
https://si0in.kr</textarea>

    <div class="share-actions">
      <button class="share-btn primary" onclick="shareLink()">📲 카톡·문자로 보내기</button>
      <button class="share-btn" onclick="copyMsg()">📋 메시지 통째 복사</button>
    </div>
    <div class="share-toast" id="shareToast"></div>
  </div>

  <div class="foot">
    🏗️ <b>시공막내</b> — 1인 시공 사장님의 운영 OS<br>
    <a href="https://si0in.kr" target="_blank">si0in.kr</a> · <a href="/healthz" target="_blank">healthz</a>
  </div>
</div>

<script>
  function getToken() { return sessionStorage.getItem('admin_token') || ''; }
  function saveToken() {
    var t = document.getElementById('tokenInput').value.trim();
    if (!t) return;
    sessionStorage.setItem('admin_token', t);
    document.getElementById('tokenModal').classList.remove('show');
    loadStats();
  }
  function ensureToken() {
    if (!getToken()) { document.getElementById('tokenModal').classList.add('show'); return false; }
    return true;
  }
  async function loadStats() {
    if (!ensureToken()) return;
    try {
      var r = await fetch('/admin/home/data', { headers: { 'Authorization': 'Bearer ' + getToken() } });
      if (r.status === 401) { sessionStorage.removeItem('admin_token'); ensureToken(); return; }
      if (!r.ok) throw new Error('통계 로드 실패');
      var d = await r.json();
      document.getElementById('statsDashboard').innerHTML =
        '활성 <b>' + d.dashboard.active_7d + '</b>명 · 호출 <b>' + d.dashboard.api_calls_7d.toLocaleString() + '</b>건 · ' + Math.round(d.dashboard.cost_krw_7d).toLocaleString() + '원 (7일)';
      document.getElementById('statsWhitelist').innerHTML =
        '등록 <b>' + d.whitelist.total + '</b>명 · 활성 <b>' + d.whitelist.active_7d + '</b>명 · 미진입 <b>' + d.whitelist.pending + '</b>명';
      document.getElementById('statsSignups').innerHTML =
        '총 <b>' + d.signups.total + '</b>건 · 대기 <b>' + d.signups.pending + '</b>건 · 신규 <b>' + d.signups.new_7d + '</b>건 (7일)';
    } catch(e) {
      console.error(e);
      document.querySelectorAll('.stats').forEach(function(el){ el.textContent = '통계 로드 실패'; });
    }
  }

  // PWA 설치
  var deferredPrompt = null;
  window.addEventListener('beforeinstallprompt', function(e) { e.preventDefault(); deferredPrompt = e; });
  function onInstallClick() {
    if (deferredPrompt) {
      deferredPrompt.prompt();
      deferredPrompt.userChoice.then(function(){ deferredPrompt = null; });
      return;
    }
    var ua = navigator.userAgent.toLowerCase();
    var msg = '브라우저 메뉴를 열고 "현재 페이지를 홈 화면에 추가" 를 눌러주세요.';
    if (/iphone|ipad|ipod/.test(ua)) msg = '하단 공유 버튼 (▢↑) 을 누르고 "홈 화면에 추가" 를 선택해주세요.';
    else if (ua.indexOf('samsungbrowser') !== -1) msg = '하단 메뉴 (≡) → "현재 페이지를 홈 화면에 추가" 를 눌러주세요.';
    document.getElementById('pwa-sheet-msg').textContent = msg;
    document.getElementById('pwa-sheet').classList.add('show');
  }
  function closePwaSheet() { document.getElementById('pwa-sheet').classList.remove('show'); }

  // 베타 모집 링크 공유
  function showToast(msg, isError) {
    var t = document.getElementById('shareToast');
    t.textContent = msg;
    t.style.color = isError ? '#F0436A' : '#16C172';
    t.style.opacity = '1';
    setTimeout(function(){ t.style.opacity = '0'; setTimeout(function(){ t.textContent = ''; t.style.opacity = '1'; }, 300); }, 2500);
  }
  function copyUrl() {
    var url = document.getElementById('shareUrl').textContent;
    copyToClipboard(url, '✓ URL 복사됨!');
  }
  function copyMsg() {
    var msg = document.getElementById('shareMsg').value;
    copyToClipboard(msg, '✓ 메시지 통째 복사됨!');
  }
  function copyToClipboard(text, successMsg) {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(function(){ showToast(successMsg); }).catch(function(){ fallbackCopy(text, successMsg); });
    } else { fallbackCopy(text, successMsg); }
  }
  function fallbackCopy(text, successMsg) {
    var ta = document.createElement('textarea');
    ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.select();
    try { document.execCommand('copy'); showToast(successMsg); } catch(e) { showToast('복사 실패: 길게 눌러 직접 복사하세요', true); }
    document.body.removeChild(ta);
  }
  function shareLink() {
    var msg = document.getElementById('shareMsg').value;
    var url = document.getElementById('shareUrl').textContent;
    if (navigator.share) {
      navigator.share({
        title: '시공막내 베타 사장님 모집',
        text: msg,
        url: url
      }).then(function(){ showToast('✓ 공유 시트 띄움'); }).catch(function(e){
        if (e.name !== 'AbortError') showToast('공유 실패: ' + e.message, true);
      });
    } else {
      // Web Share API 미지원 (데스크탑 등) → 메시지 복사 폴백
      copyToClipboard(msg, '✓ 공유 미지원 — 메시지 복사함');
    }
  }

  loadStats();
</script></body></html>
"""


@app.get("/admin", response_class=HTMLResponse, include_in_schema=False)
async def admin_home_page():
    """사장님 admin 홈 허브 — PWA + 카드 메뉴."""
    return HTMLResponse(content=_ADMIN_HOME_HTML)


@app.get("/manifest/admin.webmanifest")
async def admin_manifest():
    """PWA manifest — admin 홈."""
    manifest = {
        "name": "시공막내 admin",
        "short_name": "시공막내 admin",
        "start_url": "/admin",
        "scope": "/admin",
        "display": "standalone",
        "background_color": "#F4F5F7",
        "theme_color": "#3182F6",
        "icons": [
            {"src": "/manifest/admin-icon.svg", "sizes": "any", "type": "image/svg+xml", "purpose": "any maskable"},
            {"src": "/manifest/admin-icon.svg", "sizes": "192x192", "type": "image/svg+xml"},
            {"src": "/manifest/admin-icon.svg", "sizes": "512x512", "type": "image/svg+xml"},
        ],
    }
    return JSONResponse(content=manifest, media_type="application/manifest+json")


@app.get("/manifest/admin-icon.svg")
async def admin_icon():
    """PWA 아이콘 — admin 홈 (파란 박스 + R)."""
    svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">'
        '<defs><linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="100%">'
        '<stop offset="0%" stop-color="#3182F6"/>'
        '<stop offset="100%" stop-color="#1B64DA"/></linearGradient></defs>'
        '<rect width="512" height="512" rx="96" fill="url(#g)"/>'
        '<text x="256" y="345" text-anchor="middle" font-family="-apple-system,system-ui,sans-serif" '
        'font-size="280" font-weight="900" fill="#fff">R</text>'
        '</svg>'
    )
    return Response(content=svg, media_type="image/svg+xml")


# ============================================================================
# §24 — APK 직접 서빙 + 설치 안내 페이지 (베타 50명 다운로드 채널)
# ─────────────────────────────────────────────────────────────────────────────
# Google Play 비공개 테스트 셋업 전 임시 다리. 사장님이 안드로이드 APK 빌드 후
# /Users/hun/ringgo-server/apk/shigongmagne.apk 에 cp 하면 즉시 활성.
#
# 사용자 흐름:
#   1. 베타 선정 SMS → "https://api.si0in.kr/install" 링크
#   2. 설치 안내 페이지 → "출처 알 수 없는 앱 허용" 설명 + 다운 버튼
#   3. APK 다운로드 → 탭하면 설치
#
# 보안 우려 완화:
#   - 사장님 사업자 정보 명시 (디테일라인 직인)
#   - 단순 안내 페이지로 거부감 ↓
#   - 추후 Play 등록 후 자동 마이그레이션 안내
# ============================================================================

_APK_DIR = Path("/Users/hun/ringgo-server/apk")
_APK_PATH = _APK_DIR / "shigongmagne.apk"
_APK_VERSION_PATH = _APK_DIR / "VERSION.txt"  # 사장님이 빌드 시 버전 정보 박는 곳 (optional)
_APK_VERSION_CODE_PATH = _APK_DIR / "VERSION_CODE.txt"  # 추가58 (2026-06-25) — int versionCode (앱 업데이트 비교용)
_INSTALL_HTML_PATH = BASE_DIR / "static" / "install.html"
_PRIVACY_HTML_PATH = BASE_DIR / "static" / "privacy.html"


@app.get("/download/shigongmagne.apk", include_in_schema=False)
async def download_apk():
    """APK 다운로드 (직접 서빙). 사장님이 cp 한 후에만 활성."""
    if not _APK_PATH.exists():
        raise HTTPException(
            status_code=404,
            detail=(
                "APK 파일이 아직 업로드 안 됨. "
                "사장님이 /Users/hun/ringgo-server/apk/shigongmagne.apk 에 빌드한 APK 를 cp 하면 활성."
            ),
        )
    return FileResponse(
        _APK_PATH,
        media_type="application/vnd.android.package-archive",
        filename="shigongmagne.apk",
        headers={
            "Content-Disposition": 'attachment; filename="shigongmagne.apk"',
            # 캐시 제어 — APK 업데이트 시 즉시 반영
            "Cache-Control": "no-cache, no-store, must-revalidate",
        },
    )


_APK_VC_CACHE: dict = {}      # {mtime_ns: (version_code, version_name)} — 재파싱 방지


def _apk_version_from_binary(apk_path: Path) -> tuple:
    """APK 에서 versionCode/versionName 을 **직접** 읽는다 (VERSION_CODE.txt stale 재발 방지).

    aapt 가 있으면 그걸 쓰고, 없으면 AndroidManifest.xml(바이너리 AXML)을 직접 파싱.
    실패하면 (0, "") 반환 → 호출부가 VERSION_CODE.txt 로 폴백.
    반환: (version_code:int, version_name:str)
    """
    import struct
    import zipfile
    import shutil
    import subprocess
    # 1) aapt 우선 (있으면 가장 확실)
    aapt = shutil.which("aapt") or shutil.which("aapt2")
    if aapt:
        try:
            out = subprocess.run([aapt, "dump", "badging", str(apk_path)],
                                 capture_output=True, text=True, timeout=15).stdout
            import re as _re
            mc = _re.search(r"versionCode='(\d+)'", out)
            mn = _re.search(r"versionName='([^']*)'", out)
            if mc:
                return int(mc.group(1)), (mn.group(1) if mn else "")
        except Exception as e:  # noqa: BLE001
            print(f"[download/version] aapt 실패, AXML 파싱으로 폴백: {e}")
    # 2) AndroidManifest.xml (바이너리 AXML) 직접 파싱 — versionCode 리소스ID = 0x0101021b
    try:
        with zipfile.ZipFile(apk_path) as z:
            axml = z.read("AndroidManifest.xml")
    except Exception:
        return 0, ""
    try:
        VC_RES, VN_RES = 0x0101021B, 0x0101021C
        total = len(axml)
        res_map = []
        str_pool = []
        pos = 8                                   # 파일 magic(4) + 파일크기(4) 건너뜀
        vcode, vname = 0, ""
        guard = 0
        while pos + 8 <= total and guard < 100000:
            guard += 1
            ctype, hsize, csize = struct.unpack_from("<HHI", axml, pos)
            if csize < 8 or pos + csize > total or hsize < 8 or hsize > csize:
                break
            if ctype == 0x0001:                   # 문자열 풀
                scount, _sc, sflags, sstart, _sty = struct.unpack_from("<IIIII", axml, pos + 8)
                # 상한·범위 검증 — 어긋난 바이트에서 거대한 배열 할당 방지
                if scount > 20000 or pos + hsize + scount * 4 > pos + csize:
                    pos += csize
                    continue
                utf8 = bool(sflags & (1 << 8))
                offs = struct.unpack_from("<%dI" % scount, axml, pos + hsize)
                base = pos + sstart
                for o in offs:
                    p = base + o
                    try:
                        if utf8:
                            n = axml[p + 1]
                            s = axml[p + 2:p + 2 + n].decode("utf-8", "replace")
                        else:
                            n = struct.unpack_from("<H", axml, p)[0]
                            if n > 5000:
                                s = ""
                            else:
                                s = axml[p + 2:p + 2 + n * 2].decode("utf-16-le", "replace")
                    except Exception:  # noqa: BLE001
                        s = ""
                    str_pool.append(s)
            elif ctype == 0x0180:                 # 리소스 맵
                n = min((csize - hsize) // 4, 20000)
                if n > 0:
                    res_map = list(struct.unpack_from("<%dI" % n, axml, pos + hsize))
            elif ctype == 0x0102:                 # start element
                body = pos + hsize                # hsize=16 (node 헤더)
                attr_start, _asz, attr_cnt = struct.unpack_from("<HHH", axml, body + 8)
                if attr_cnt <= 200:
                    abase = body + attr_start
                    for i in range(attr_cnt):
                        ap = abase + i * 20
                        if ap + 20 > total:
                            break
                        a_name = struct.unpack_from("<I", axml, ap + 4)[0]
                        a_data = struct.unpack_from("<I", axml, ap + 16)[0]
                        res = res_map[a_name] if a_name < len(res_map) else 0
                        if res == VC_RES and not vcode:
                            vcode = a_data
                        elif res == VN_RES and not vname:
                            vname = str_pool[a_data] if a_data < len(str_pool) else ""
                if vcode:
                    break
            pos += csize
        # 그럴듯한 versionCode 범위만 신뢰 (오탐 방지). 벗어나면 0 → txt 폴백.
        if not (1 <= vcode <= 10_000_000):
            return 0, (vname or "")
        return vcode, vname
    except Exception as e:  # noqa: BLE001
        print(f"[download/version] AXML 파싱 실패: {e}")
        return 0, ""


@app.get("/api/download/version", include_in_schema=False)
async def download_apk_version():
    """현재 서빙 중인 APK 메타 정보 (size, mtime, versionCode/Name).

    추가126 F — versionCode 는 **APK 에서 직접 추출**(캐시). VERSION_CODE.txt(수동)가
    갱신 안 돼 stale 이던 문제(6/29 749 고정 → 업데이트 배너 안 뜸) 재발 방지.
    추출 실패 시에만 VERSION_CODE.txt 로 폴백.
    """
    if not _APK_PATH.exists():
        return {"available": False}
    stat = _APK_PATH.stat()
    version_text = ""
    if _APK_VERSION_PATH.exists():
        try:
            version_text = _APK_VERSION_PATH.read_text(encoding="utf-8").strip()[:80]
        except Exception:
            pass
    # 추가126 F — APK 에서 직접 추출 (mtime 키로 캐시). 실패 시 VERSION_CODE.txt 폴백.
    version_code = 0
    src = "apk"
    ck = stat.st_mtime_ns
    if ck in _APK_VC_CACHE:
        version_code, vn = _APK_VC_CACHE[ck]
    else:
        version_code, vn = _apk_version_from_binary(_APK_PATH)
        _APK_VC_CACHE.clear()                       # APK 하나뿐 — 옛 캐시 비움
        _APK_VC_CACHE[ck] = (version_code, vn)
    if vn and not version_text:                     # AXML 에서 얻은 versionName 로 표기 보강
        version_text = vn[:80]
    if not version_code:                            # 추출 실패 → 수동 txt 폴백
        src = "txt"
        if _APK_VERSION_CODE_PATH.exists():
            try:
                raw = _APK_VERSION_CODE_PATH.read_text(encoding="utf-8").strip()
                version_code = int(raw) if raw else 0
            except Exception as e:  # noqa: BLE001
                print(f"[download/version] VERSION_CODE.txt 파싱 실패: {e}")
    return {
        "available": True,
        "size_bytes": stat.st_size,
        "size_mb": round(stat.st_size / 1024 / 1024, 1),
        "mtime_ms": int(stat.st_mtime * 1000),
        "mtime_iso": _dt.datetime.fromtimestamp(stat.st_mtime).strftime(
            "%Y-%m-%d %H:%M"
        ),
        "version": version_text or "v0.2-beta",  # §3 (2026-06-18) — VERSION.txt 없을 때 fallback.
        "version_code": version_code,  # 추가126 — APK 에서 직접 추출 (0 이면 안드로이드는 mtime 폴백).
        "version_code_source": src,    # apk = APK 직접 추출, txt = VERSION_CODE.txt 폴백
    }


@app.get("/install", response_class=HTMLResponse, include_in_schema=False)
async def install_page():
    """설치 안내 페이지 — '출처 알 수 없는 앱 허용 → 다운 → 설치' 3단계.

    선정자 SMS 의 링크 (api.si0in.kr/install) 가 여기로.
    """
    if not _INSTALL_HTML_PATH.exists():
        raise HTTPException(
            status_code=500,
            detail=f"install.html 없음 (server/static/install.html 확인).",
        )
    return _INSTALL_HTML_PATH.read_text(encoding="utf-8")


@app.get("/privacy", response_class=HTMLResponse, include_in_schema=False)
async def privacy_page():
    """개인정보 처리방침 — Google Play Console 7단계(데이터 보안) 의 URL 입력값.

    내용: server/static/privacy.html
    Play Console 정식 출시 전부터 활성 (Internal Testing 시 필수).
    """
    if not _PRIVACY_HTML_PATH.exists():
        raise HTTPException(
            status_code=500,
            detail="privacy.html 없음 (server/static/privacy.html 확인).",
        )
    return _PRIVACY_HTML_PATH.read_text(encoding="utf-8")


# ============================================================================
# 추가97 — 개인정보 동의 세트 (사장님 요청, SKT AI메시지 동의 구조 벤치마킹)
#   GET /consent/required  — (필수) 수집·이용 동의문
#   GET /consent/optional  — (선택) 품질 향상 동의문
#   POST /api/consent      — 동의/철회 기록 (append-only 이력 = 분쟁 대비 영수증)
#   GET /api/consent/status — 최신 동의 상태 (앱 부팅 시 확인용)
# ⚠️ 문안은 초안 — 정식 출시 전 privacy.go.kr 표준 양식 대조/전문가 검토 권장.
# ============================================================================

_CONSENT_DOC_VERSION = "2026-07-06.3"  # 추가100 — 운영사 막내컴퍼니 명의
_CONSENT_DOC_TYPES = {"required", "optional_quality"}


@app.get("/terms", response_class=HTMLResponse, include_in_schema=False)
async def terms_page():
    """추가98 — 이용약관 (초안. AI 면책 §7 + 환불 §8 포함). PG 심사·결제 페이지에서 링크."""
    p = BASE_DIR / "static" / "terms.html"
    if not p.exists():
        raise HTTPException(500, "terms.html 없음")
    return p.read_text(encoding="utf-8")


@app.get("/privacy/v1", response_class=HTMLResponse, include_in_schema=False)
async def privacy_v1_page():
    """추가97b — 이전 버전 처리방침 보관 (작성지침: 변경 이력 열람)."""
    p = BASE_DIR / "static" / "privacy_v1.html"
    if not p.exists():
        raise HTTPException(500, "privacy_v1.html 없음")
    return p.read_text(encoding="utf-8")


@app.get("/consent/required", response_class=HTMLResponse, include_in_schema=False)
async def consent_required_page():
    p = BASE_DIR / "static" / "consent_required.html"
    if not p.exists():
        raise HTTPException(500, "consent_required.html 없음")
    return p.read_text(encoding="utf-8")


@app.get("/consent/optional", response_class=HTMLResponse, include_in_schema=False)
async def consent_optional_page():
    p = BASE_DIR / "static" / "consent_optional.html"
    if not p.exists():
        raise HTTPException(500, "consent_optional.html 없음")
    return p.read_text(encoding="utf-8")


class ConsentRequest(BaseModel):
    phone: str = ""
    docType: str = ""        # "required" | "optional_quality"
    docVersion: str = ""     # 앱이 표시한 문서 버전 (기본 = 서버 현재 버전)
    agreed: bool = True      # False = 철회


@app.post("/api/consent")
async def consent_record(req: ConsentRequest, request: Request) -> dict:
    phone = _norm_phone(req.phone)
    if not phone:
        raise HTTPException(400, "phone 필수")
    if req.docType not in _CONSENT_DOC_TYPES:
        raise HTTPException(400, f"docType 은 {sorted(_CONSENT_DOC_TYPES)}")
    if req.docType == "required" and not req.agreed:
        # 필수 동의 철회 = 탈퇴 절차로 처리해야 함 (기록만 남기고 안내)
        pass
    version = (req.docVersion or "").strip()[:20] or _CONSENT_DOC_VERSION
    now = _now_ms()
    ip = request.headers.get("x-forwarded-for", "").split(",")[0].strip() or (
        request.client.host if request.client else "")
    with db_conn() as con:
        con.execute(
            "INSERT INTO consents (phone, doc_type, doc_version, agreed, ip, created_at_ms) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            (phone, req.docType, version, 1 if req.agreed else 0, ip[:60], now),
        )
        con.commit()
    print(f"[consent] {phone} {req.docType} v{version} agreed={req.agreed}")
    return {"ok": True, "phone": phone, "docType": req.docType,
            "docVersion": version, "agreed": req.agreed, "recordedAtMs": now}


@app.get("/api/consent/status")
async def consent_status(phone: str) -> dict:
    phone_digits = _norm_phone(phone)
    if not phone_digits:
        raise HTTPException(400, "phone 필수")
    out = {}
    with db_conn() as con:
        for dt_ in _CONSENT_DOC_TYPES:
            row = con.execute(
                "SELECT agreed, doc_version, created_at_ms FROM consents "
                "WHERE phone = ? AND doc_type = ? ORDER BY created_at_ms DESC LIMIT 1",
                (phone_digits, dt_),
            ).fetchone()
            out[dt_] = (
                {"agreed": bool(row[0]), "docVersion": row[1], "recordedAtMs": row[2]}
                if row else None
            )
    return {"phone": phone_digits, "currentDocVersion": _CONSENT_DOC_VERSION,
            "consents": out}


# ============================================================================
# 추가117 (B2) — 유료전환 D-14 사전 동의 (전상법 §13조의2, 약관 §8 이행)
# ─────────────────────────────────────────────────────────────────────────────
# 무료 체험 만료 14일 전에 대금·결제주기·해지조건을 고지하고 동의를 수집해야
# 자동 유료전환·결제가 가능. (실제 결제 = 토스 연동 후. 지금은 동의 인프라만.)
#   - GET  /api/conversion/status?phone=      : 무료 만료 D-day / 고지 도래 / 동의 여부
#   - POST /api/conversion/consent            : 유료전환 사전 동의/철회 기록 (consents 테이블)
#   - GET  /api/admin/conversion/upcoming     : 14일 내 만료 예정자 목록 (알림 발송용, Bearer)
# ============================================================================
_CONVERSION_NOTICE_DAYS = 14
_CONVERSION_DOC_TYPE = "paid_conversion"
_CONVERSION_DOC_VERSION = "2026-07-11.1"   # Q9 법정고지 완비(변동전후·해지조건효과·부가세포함 총액)
_VALID_PLANS = {"standard_50k", "premium_100k"}

# Q9 — 전상법 §13조의2·시행령 §20조의2 법정 고지 문안(서버 원본).
# 다크패턴 규제(순차공개 가격) 대비: 첫 화면부터 '부가세 포함 총액' 표시.
_PLAN_INFO = {
    "standard_50k": {"label": "스탠다드", "supplyKrw": 50000, "totalKrw": 55000},
    "premium_100k": {"label": "프리미엄", "supplyKrw": 100000, "totalKrw": 110000},
}
_CANCEL_NOTICE = (
    "언제든지 앱 설정 > 구독 관리에서 해지할 수 있습니다. "
    "다음 결제일 전 해지 시 추가 결제가 없으며, 결제 후 중도 해지 시 이용 일수를 공제한 잔액을 환불합니다. "
    "결제 후 7일 이내 미사용 시 전액 환불됩니다."
)
_PRICE_INCREASE_NOTE = (
    "향후 요금이 인상되는 경우 인상 30일 전에 별도로 동의를 받으며, 동의가 없으면 인상된 금액으로 결제되지 않습니다."
)


def _conversion_notice(plan: str, free_until_ms: Optional[int]) -> Optional[dict]:
    """유료전환 법정 고지 1건(요금제별) — 앱 모달이 이 문안 그대로 표시하고, 동의 시 스냅샷 저장."""
    info = _PLAN_INFO.get(plan)
    if not info:
        return None
    start_txt = None
    if free_until_ms:
        import datetime as _dt
        _d = _dt.datetime.fromtimestamp(free_until_ms / 1000)
        start_txt = f"{_d.year}년 {_d.month}월 {_d.day}일"
    after = (f"{start_txt + '부터 ' if start_txt else ''}"
             f"월 {info['totalKrw']:,}원(부가세 포함)")
    return {
        "plan": plan,
        "planLabel": info["label"],
        "beforeText": "현재 무료 체험 (0원)",           # 변동 전 대금
        "afterText": after,                              # 변동 후 대금 (부가세 포함 총액)
        "totalKrwVatIncluded": info["totalKrw"],
        "supplyKrw": info["supplyKrw"],
        "cycle": "월 1회 정기결제",
        "startAtMs": free_until_ms,
        "cancel": _CANCEL_NOTICE,                        # 해지 조건·방법·효과
        "priceIncreaseNote": _PRICE_INCREASE_NOTE,
        "docVersion": _CONVERSION_DOC_VERSION,
    }


def _free_until_for(phone_digits: str) -> Optional[int]:
    with db_conn() as con:
        wl = con.execute(
            "SELECT free_until_ms FROM beta_whitelist WHERE phone = ?", (phone_digits,)
        ).fetchone()
    return wl[0] if wl else None


def _latest_conversion_consent(phone_digits: str) -> Optional[tuple]:
    with db_conn() as con:
        return con.execute(
            "SELECT agreed, doc_version, created_at_ms FROM consents "
            "WHERE phone = ? AND doc_type = ? ORDER BY created_at_ms DESC LIMIT 1",
            (phone_digits, _CONVERSION_DOC_TYPE),
        ).fetchone()


@app.get("/api/conversion/status")
async def conversion_status(phone: str) -> dict:
    """무료 만료 D-day + 유료전환 고지 도래(≤14일) + 사전 동의 여부."""
    pd = _norm_phone(phone)
    if not pd:
        raise HTTPException(400, "phone 필수")
    free_until = _free_until_for(pd)
    now = _now_ms()
    days_left = None if free_until is None else (free_until - now) // 86_400_000
    notice_due = free_until is not None and days_left is not None and days_left <= _CONVERSION_NOTICE_DAYS
    row = _latest_conversion_consent(pd)
    return {
        "phone": pd,
        "freeUntilMs": free_until,          # None = 초기 베타(무제한)
        "daysLeft": days_left,
        "noticeDueDays": _CONVERSION_NOTICE_DAYS,
        "noticeDue": bool(notice_due),      # True면 앱이 D-14 유료전환 안내+동의 화면 노출
        "consented": bool(row[0]) if row else False,
        "consentedPlan": (row[1].split("|")[-1] if row and "|" in (row[1] or "") else None),
        "consentDocVersion": _CONVERSION_DOC_VERSION,
        # Q9 — 모달이 이 문안을 그대로 표시(변동 전후 대금·해지 조건·효과·부가세 포함 총액).
        "plans": [n for n in (_conversion_notice(p, free_until)
                              for p in ("standard_50k", "premium_100k")) if n],
        "cancelNotice": _CANCEL_NOTICE,
    }


class ConversionConsentRequest(BaseModel):
    phone: str = ""
    plan: str = ""             # standard_50k | premium_100k
    agreed: bool = True        # False = 전환 거부(자동결제 안 함)


@app.post("/api/conversion/consent")
async def conversion_consent(req: ConversionConsentRequest, request: Request) -> dict:
    """유료전환 사전 동의/철회 기록. 대금·주기·해지 고지 후 앱이 호출.

    동의가 없으면(또는 거부) 결제 연동 후에도 자동 유료전환·결제가 이루어지지 않음.
    """
    pd = _norm_phone(req.phone)
    if not pd:
        raise HTTPException(400, "phone 필수")
    if req.agreed and req.plan not in _VALID_PLANS:
        raise HTTPException(400, f"plan 은 {sorted(_VALID_PLANS)}")
    now = _now_ms()
    ip = request.headers.get("x-forwarded-for", "").split(",")[0].strip() or (
        request.client.host if request.client else "")
    ua = (request.headers.get("user-agent", "") or "")[:200]
    # doc_version 에 요금제 태그를 붙여 어떤 요금제로 동의했는지 남김
    ver = f"{_CONVERSION_DOC_VERSION}|{req.plan}" if req.agreed else _CONVERSION_DOC_VERSION
    # Q12 — '무엇에 동의했는지' 재현용: 당시 표시된 고지 문안 스냅샷 + 해시 + 기기정보 보관.
    # (전상법 시행령 §6에 따라 계약·청약철회 기록으로 5년 보존 대상)
    import hashlib as _hashlib
    snapshot = _conversion_notice(req.plan, _free_until_for(pd)) if req.agreed else None
    snap_str = json.dumps(snapshot, ensure_ascii=False, sort_keys=True) if snapshot else ""
    detail = {
        "snapshot": snapshot,
        "snapshotSha256": _hashlib.sha256(snap_str.encode("utf-8")).hexdigest() if snap_str else None,
        "userAgent": ua,
        "retention": "전상법 시행령 §6 · 계약/청약철회 기록 5년 보존",
    }
    with db_conn() as con:
        con.execute(
            "INSERT INTO consents (phone, doc_type, doc_version, agreed, ip, created_at_ms, detail_json) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            (pd, _CONVERSION_DOC_TYPE, ver[:40], 1 if req.agreed else 0, ip[:60], now,
             json.dumps(detail, ensure_ascii=False)),
        )
        con.commit()
    print(f"[conversion/consent] {pd} plan={req.plan} agreed={req.agreed} snap={detail['snapshotSha256']}")
    return {"ok": True, "phone": pd, "plan": req.plan if req.agreed else None,
            "agreed": req.agreed, "recordedAtMs": now,
            "snapshotSha256": detail["snapshotSha256"]}


@app.get("/api/admin/conversion/upcoming")
async def admin_conversion_upcoming(
    days: int = 14,
    authorization: Optional[str] = Header(default=None),
) -> dict:
    """무료 만료 N일 내 예정자 목록 (D-14 안내 발송용). 이미 동의/거부한 사람은 표시만."""
    _admin_auth_bearer_from_header(authorization)
    days = max(1, min(days, 60))
    now = _now_ms()
    horizon = now + days * 86_400_000
    out = []
    with db_conn() as con:
        rows = con.execute(
            "SELECT phone, free_until_ms FROM beta_whitelist "
            "WHERE free_until_ms IS NOT NULL AND free_until_ms <= ? "
            "ORDER BY free_until_ms ASC", (horizon,)
        ).fetchall()
        for ph, fu in rows:
            crow = con.execute(
                "SELECT agreed FROM consents WHERE phone = ? AND doc_type = ? "
                "ORDER BY created_at_ms DESC LIMIT 1", (ph, _CONVERSION_DOC_TYPE)
            ).fetchone()
            out.append({
                "phone": ph,
                "freeUntilMs": fu,
                "daysLeft": (fu - now) // 86_400_000,
                "expired": fu < now,
                "consented": bool(crow[0]) if crow else False,
            })
    return {"days": days, "count": len(out), "items": out}


def _record_intake_consent(customer_phone: Optional[str]) -> None:
    """추가98 — 접수서 폼에서 고객이 개인정보 동의 체크 시 영수증 기록.
    doc_type='intake_customer'. 실패해도 접수 자체는 막지 않음."""
    try:
        p = _norm_phone(customer_phone)
        if not p:
            return
        with db_conn() as con:
            con.execute(
                "INSERT INTO consents (phone, doc_type, doc_version, agreed, ip, created_at_ms) "
                "VALUES (?, 'intake_customer', ?, 1, '', ?)",
                (p, _CONSENT_DOC_VERSION, _now_ms()),
            )
            con.commit()
    except Exception:
        pass


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


# ─── 로컬 LLM (Ollama / Qwen2.5 7B) — §26 Option A (2026-06-10) ───
# /api/call-audio-summary 의 LLM 호출을 Anthropic Haiku → Ollama 로 전환.
# 비용 0 + 데이터 프라이버시. 실패 시 Haiku fallback.

OLLAMA_BASE_URL = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434")
DEFAULT_LOCAL_LLM = os.environ.get("LOCAL_LLM_MODEL", "qwen2.5:7b")


async def call_ollama_json(
    *,
    system_prompt: str,
    user_msg: str,
    model: str = DEFAULT_LOCAL_LLM,
    timeout: float = 120.0,
    temperature: float = 0.3,
    max_tokens: int = 600,
) -> dict:
    """Ollama 로컬 LLM 으로 JSON 응답. 실패 시 raise (caller 가 fallback).

    Qwen2.5 7B 가 GPT-OSS 보다 instruction-following 정확. 첫 호출 ~10-30초
    (모델 로드), 이후 ~5-10초.
    """
    async with httpx.AsyncClient(timeout=timeout) as client:
        r = await client.post(
            f"{OLLAMA_BASE_URL}/api/generate",
            json={
                "model": model,
                "system": system_prompt,
                "prompt": user_msg,
                "format": "json",
                "stream": False,
                "options": {
                    "temperature": temperature,
                    "num_predict": max_tokens,
                },
            },
        )
        r.raise_for_status()
        data = r.json()
    raw = data.get("response", "") or ""
    raw = raw.strip()
    if not raw:
        raise ValueError("ollama 빈 응답")
    return json.loads(raw)


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
        code, msg = _classify_llm_error(e)
        print(f"[{endpoint_label}] {ctx.phone} Claude 호출 실패 (model={model}): {type(e).__name__}: {e} → {code} '{msg}'")
        raise HTTPException(code, msg)

    # 4) 사용량 로그
    # 추가38+42+50 (2026-06-18/20/21) — owner 단위 통계 + heartbeat + 업종 저장
    log_usage(ctx.owner_phone or ctx.phone, endpoint_label, response)
    _touch_beta_whitelist(ctx.owner_phone, owner_trade=getattr(ctx, 'owner_trade', None))
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
    phone: Optional[str] = None        # legacy — 무엇이 들어왔는지 모호. owner_phone 우선 사용.
    owner_phone: Optional[str] = None  # 추가37 (2026-06-18) — 화이트리스트 게이트용 사장님 phone.
    ownerTrade: Optional[str] = None   # 추가50 (2026-06-21) — 앱 onboarding 업종 (저장용)


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
    if cleaned:
        tone_block = "\n".join(f"- {s}" for s in cleaned)
        tone_guide = (
            "위 사장님 톤 샘플의 어휘·이모지·말투를 적절히 모방하되, **상담원 페르소나의 친절함을 우선**하라.\n"
            "(샘플이 너무 짧거나 무뚝뚝해도, 친절도는 한 단계 더 올려라.)"
        )
    else:
        tone_block = "(샘플 없음 — 아래 기본 친절 상담원 톤으로)"
        tone_guide = (
            "톤 샘플이 없으므로 아래 친절한 시공 상담원 톤으로:\n"
            '  · 인사: "네 안녕하세요~", "안녕하세요 사장님!"\n'
            '  · 공감: "확인해 보니~", "신경 써 주셔서~", "걱정 마세요~"\n'
            '  · 안내: "~ 해드릴게요", "~ 진행하겠습니다", "편하게 말씀 주세요"\n'
            '  · 마무리: "감사합니다~", "더 궁금한 점 있으시면 언제든 연락 주세요"'
        )

    return f"""너는 **줄눈/타일 시공 사장님을 도와 친절한 응대 메시지를 작성해주는 상담원**이다.

────── 핵심 임무 ──────
사장님이 짧고 거칠게 입력한 원문을 받아서 — **고객에게 보낼 정중하고 따뜻한 상담원 메시지**로 다시 써라.
사장님은 손이 바빠 짧게 적었지만, 너는 그 의도를 살려서 **더 길고, 더 친절하고, 더 세심한 메시지**로 풀어 써야 한다.

목표: 사장님이 "이렇게 답하고 싶었어" 라고 느낄, "전문 상담원" 수준의 응대.

────── 풍성하게 다시 쓰는 가이드 ──────
원문이 한 줄이라도, 다음 요소들을 자연스럽게 채워라:
1. **인사** — "네 안녕하세요~" / "사장님, 안녕하세요!" (대화 맥락에 맞게)
2. **공감·확인** — 고객이 물어본 것 / 사장님이 약속한 것을 풀어서 다시 짚기
3. **본 내용** — 원문의 핵심 (날짜·금액·시공 종류·약속 등) 을 자연스러운 문장으로
4. **부드러운 마무리** — "편하게 말씀 주세요~", "감사합니다~", "더 궁금한 점 있으시면 언제든 연락 주세요"

이렇게 풀면 원문 1줄이 자연스럽게 3~6줄로 늘어난다. 그게 정상이다.

────── 절대 지킬 것 (의미 보존) ──────
- **사장님이 원문에 쓴 내용 (날짜·시간·금액·시공 종류·약속) 은 그대로 살려라**
- **원문에 없는 정보 (가격·날짜·할인·시공 종류 등) 는 절대 추가 금지** — 친절하다고 거짓말 X
- 의미·약속을 반대로 뒤집지 마라
- 금기어: "급하면" 계열, "싸다" 계열 → 변형 표현도 피하라
- 거짓 약속·과장된 보장 ("100% 만족" 등) 금지

────── 톤 ──────
{tone_guide}

────── 사장님 톤 샘플 (참고) ──────
{tone_block}

────── 답 형식 ──────
- 출력은 **고객에게 그대로 보낼 수 있는 메시지 본문**만
- 자연스러운 줄바꿈 OK (긴 메시지는 줄 끊어서 읽기 편하게)
- 인사·설명·따옴표·코드블럭·백틱·JSON·태그 절대 X
- 첫 글자부터 메시지로 시작 ("네 안녕하세요~" 같이)
- **원문보다 길고, 더 따뜻하고, 더 친절해야 한다**. 짧게 다듬는 게 아니라 친절하게 다시 쓰는 거다.
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
            "temperature": 0.85,  # 핸드오프30 후속 (2026-06-15): 친절 상담원 다양성 ↑ (0.7 → 0.85)
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

    # 핸드오프30 (2026-06-15) — MAX_TOKENS 로 끊기는 경우 stdout 로그 (조기 발견).
    finish_reason = candidates[0].get("finishReason", "")
    if finish_reason == "MAX_TOKENS":
        print(
            f"[gemini/refine] WARN finishReason=MAX_TOKENS "
            f"(maxOutputTokens={GEMINI_MAX_OUTPUT_TOKENS}, polished_len={len(polished)})"
        )

    usage_meta = data.get("usageMetadata") or {}
    return polished, usage_meta


async def _call_gemini_json_for_summary(
    system_prompt: str, user_msg: str, max_output_tokens: int = 800
) -> tuple[dict, dict]:
    """§26 (2026-06-10) Gemini 2.5 Flash 통화요약 JSON. 실패 시 raise.

    response_schema 로 {one_line, bullets, suggested_followup_sms} 강제.
    Haiku 1/10 비용 + Paid tier 라 데이터 학습 안 함 (고객 프라이버시 보호).
    응답: (parsed_dict, usage_metadata)
    """
    if not GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY env var not set")

    url = (
        f"https://generativelanguage.googleapis.com/v1beta/models/"
        f"{GEMINI_MODEL}:generateContent?key={GEMINI_API_KEY}"
    )
    payload = {
        "systemInstruction": {"parts": [{"text": system_prompt}]},
        "contents": [{"role": "user", "parts": [{"text": user_msg}]}],
        "generationConfig": {
            "temperature": 0.3,
            "maxOutputTokens": max_output_tokens,
            "topP": 0.9,
            "responseMimeType": "application/json",
            "responseSchema": {
                "type": "OBJECT",
                "properties": {
                    "title": {"type": "STRING"},  # 추가61 — 6~12자 짧은 제목 (앱 헤더용)
                    "one_line": {"type": "STRING"},
                    "bullets": {
                        "type": "ARRAY",
                        "items": {"type": "STRING"},
                    },
                    "suggested_followup_sms": {"type": "STRING"},
                },
                "required": ["title", "one_line", "bullets"],
            },
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
    raw = "".join(p.get("text", "") for p in parts).strip()
    if not raw:
        finish = candidates[0].get("finishReason", "?")
        raise RuntimeError(
            f"Gemini empty response (finishReason={finish}): {str(data)[:300]}"
        )
    parsed = json.loads(raw)
    usage_meta = data.get("usageMetadata") or {}
    return parsed, usage_meta


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
    # 추가37 (2026-06-18) — 화이트리스트 게이트는 owner_phone (없으면 legacy phone) 으로.
    _ensure_and_touch_beta_whitelist(req.owner_phone, owner_trade=req.ownerTrade)  # 추가37+50
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
    # 핸드오프30 후속 (2026-06-15) — 진단 로그: raw 와 동일하면 톤 샘플/프롬프트 점검 필요.
    same = raw == polished
    print(
        f"[refine] OK in={prompt_tokens} out={completion_tokens} "
        f"cache_read={cache_read_tokens} "
        f"tone_samples={len(req.owner_tone_samples or [])} "
        f"raw_len={len(raw)} polished_len={len(polished)} "
        f"changed={'NO (원문 동일)' if same else 'YES'}"
    )

    return {"polished": polished}


# ============================================================================
# §50 — POST /infer-principle  (원칙 발견 Phase 2, 2026-06-17)
# ─────────────────────────────────────────────────────────────────────────────
# 안드로이드 ChatViewModel.maybeInferPrinciple() 이 호출.
# 사장님이 답장 발송 직후 (추천≠실제답, 편집거리 12자+, 하루 2회 cap, scenario_confidence≥0.6)
# 트리거 — 서버는 LLM 으로 "재사용 가능한 판단 원칙" 한 줄 추론.
#
# 모델: Haiku 4.5 (빈도 낮음, 짧은 출력 — 비용 최소).
# 출력 강건: JSON 강제 + 파싱 실패 시 {"principle": null} 폴백 (앱은 5xx 도 silent).
#
# 핸드오프: docs/SERVER_HANDOFF_infer_principle.md
# ============================================================================


class InferPrincipleRequest(BaseModel):
    customerMessage: str
    aiSuggestion: str
    ownerReply: str
    scenario: Optional[str] = None
    existingPrinciples: list = Field(default_factory=list)
    deviceId: Optional[str] = None
    ownerTrade: Optional[str] = None


def _build_infer_principle_system_prompt(
    existing_principles: list, owner_trade: Optional[str]
) -> str:
    """원칙 발견용 system prompt — Haiku 가 짧은 JSON 한 줄로 답하게."""
    trade = (owner_trade or "").strip() or "시공"
    cleaned: list = []
    seen: set = set()
    for p in (existing_principles or []):
        s = (p or "").strip()
        if not s or s in seen:
            continue
        seen.add(s)
        cleaned.append(s)
        if len(cleaned) >= 30:
            break
    if cleaned:
        existing_block = "\n".join(f"- {p}" for p in cleaned)
    else:
        existing_block = "(없음)"

    return f"""너는 {trade} 사장님의 답장 습관에서 "다시 써먹을 수 있는 한 줄 약속"을 찾아내는 분석가다.

★★★ 추가40 (2026-06-20) — 글 만드는 가장 중요한 규칙 ★★★
**초등학생도 한 번에 알아듣는 쉬운 한국말로 써라.**
이거 안 지키면 사장님이 카드 보고 안 누른다. 결과 = null 보내는 게 나음.

────── 입력 ──────
- 손님이 보낸 메시지
- AI 가 추천했던 답장
- 사장님이 실제로 보낸 답장
- 사장님이 이미 켜놓은 한 줄 약속들 (중복 안 만들기)

────── 무엇을 찾는가 ──────
1. 추천과 실제 답이 **'생각(작전) 차이'** 인지, 단순 **말투/오타/길이** 차이인지 본다.
   - 말투만 다르면 → null. (그건 다른 데서 배운다)
2. 생각 차이면 그걸 **누구한테나 쓸 수 있는 한 줄 약속** 으로.
   - "이 손님" 이 아니라 "이런 손님한테는" 으로 넓게.
3. 이미 있는 약속과 비슷하면 → 무조건 null.
4. 다음에 또 쓸 일 거의 없으면 → null.
5. 애매하면 null. (틀린 거 만드는 것보다 안 만드는 게 낫다)

★★★ 추가46 (2026-06-21) — 가장 자주 틀리는 함정 ★★★

【함정 1】 형식·정보 덧붙임만 다른데 작전으로 오인하지 마라
- AI 추천: "잔금 280만원이에요"
  사장님 실제: "잔금 280만원입니다. 카카오뱅크 3333-XX 김상훈" (계좌 정보 덧붙임)
  → ❌ "잔금 안내 시 계좌를 같이 보낸다" 같은 원칙 만들지 마라. 그건 단순 정보 덧붙임이지 작전 차이 아님.
  → ✅ **null** (당연한 형식, 원칙 X)
- AI 추천: "네 알겠습니다"
  사장님 실제: "네 알겠습니다 😊 감사해요!" (인사·이모지 덧붙임)
  → ❌ "고객에게 친근한 인사 덧붙임" 같은 거 만들지 마라.
  → ✅ **null** (말투/인사 덧붙임)
- AI 추천: "내일 9시 가요"
  사장님 실제: "내일 오전 9시 도착 예정입니다. 주소 다시 확인 부탁드려요."
  → ❌ "주소 확인을 요청한다" 같은 거 만들지 마라. 단순 추가 정보·확인 요청.
  → ✅ **null**

= **계좌·인사·이모지·시간·주소 확인 같은 "정보 덧붙임" 만 다르면 무조건 null.**

【함정 2】 입력에 없는 말·맥락 절대 지어내지 마라
- 입력에 "주소" 얘기 한 마디도 없는데 → ❌ "주소 확인 후 ~한다" 만들기 금지
- 입력에 "신축" 단어 없는데 → ❌ "신축 손님" 만들기 금지
- 입력에 "급한" 표시 없는데 → ❌ "급한 손님" 만들기 금지
= **principle 이 손님 메시지 + AI 추천 + 사장님 답 안의 단어·맥락만 가지고 만들어져야**. 모델이 추측·일반화·창작 금지.

【함정 3】 확신 없으면 무조건 null (헛스윙 < 침묵)
- "이거 작전 차이 같긴 한데..." → null
- 좋은 원칙 1개 < 어설픈 원칙 0개. 카드 안 띄우는 게 사장님 짜증 안 나게 함.

────── 글 쓰는 법 (★ 가장 중요) ──────
1. **초등학생도 한 번 보고 알아듣게**. 어렵게 쓰면 사장님이 짜증낸다.
2. 평서문 한 줄, 20자~40자. "~한다" 또는 "~준다" 또는 "~본다" 로 끝.
3. 손님 이름·금액·날짜 절대 쓰지 마라 (넓게).

★ 금지 단어 (절대 쓰지 마라 — 어렵고 딱딱한 말) ★
응대 / 원칙 / 권유 / 권한다 / 제시 / 제공 / 회피 / 식별 / 안내 / 발송 / 추천 /
요청 / 조치 / 의뢰 / 검토 / 확인 / 분류 / 처리 / 답변 / 응답 / 사전 / 우선 /
즉시 / 즉답 / 직접 / 기존 / 추후 / 사후 / 전제 / 방침 / 원리 / 견적 (이건 "값 알려준다" 같이 풀어쓰기)

✅ 대신 이런 말로 (사장님이 카톡할 때 쓰는 말):
말한다, 알려준다, 묻는다, 물어본다, 보내준다, 받는다, 가본다, 한 번 들렀다 온다,
먼저, 바로, 그냥, 한 번, 천천히, 빨리, 손님, 사진, 가격, 날짜

────── 좋은 예 ──────
✅ "신축 손님은 한 번 가서 보고 값 알려준다"
✅ "급한 손님한테는 가격부터 먼저 알려준다"
✅ "사진 먼저 보내달라고 한다"
✅ "잘 모르겠는 손님한테는 통화로 한 번 더 물어본다"

────── 나쁜 예 (절대 이렇게 쓰지 마라) ──────
❌ "신축 문의엔 즉답 견적 대신 방문 견적을 먼저 권한다"  ← '즉답·방문·권한다' 다 어려움
❌ "긴급 문의에 대한 우선 가격 제시"  ← 한자어 잔치
❌ "신규 고객 응대 시 사전 식별 후 단계별 안내"  ← 보고서 말투
❌ "사장님의 응대 원칙은 ~ 입니다"  ← '응대·원칙' 금지어

────── question (카드에 보이는 말) 도 쉽게 ──────
- 친구한테 카톡으로 묻듯이.
- "방금 보니까 ~ 하시던데, 사장님 평소 그렇게 하세요?" 같은 자연스러운 말투.
- 한자어·보고서 말투 똑같이 금지.

✅ 좋은 question:
"방금 보니까, 신축 손님한테는 가격 바로 말 안 하고 한 번 보고 알려주시던데. 사장님 평소 그렇게 하세요?"
"급한 손님한테는 사장님이 바로 가격부터 말해주시더라구요. 항상 그렇게 하세요?"
"사진 먼저 보내달라고 하시네요. 평소 그래요?"

❌ 나쁜 question:
"신축 문의에 대한 직접 견적 회피 및 방문 견적 우선 권유가 사장님의 응대 원칙입니까?"
"긴급 사례에 가격 정보 우선 제공이 일반 응대 방침인지 확인 요청드립니다"
"이게 사장님 원칙이에요?"  ← '원칙' 단어 자체 금지

────── 이미 켜놓은 한 줄 약속 (중복 안 만들기) ──────
{existing_block}

────── 출력 형식 — JSON 만 ──────
배울 게 있을 때:
{{"principle":"<20~40자, 초등학생도 알아듣는 한국말>","question":"<친구한테 묻듯이 자연스러운 말>"}}

배울 게 없을 때:
{{"principle":null}}

엄격: JSON 한 덩어리만. 코드펜스 · 설명 · 인사 · 마크다운 절대 금지.
"""


def _build_infer_principle_user_message(req: InferPrincipleRequest) -> str:
    """LLM 에 보낼 user 메시지."""
    parts: list = []
    if req.scenario:
        parts.append(f"[추천 시나리오] {req.scenario}")
        parts.append("")
    parts.append("[고객 메시지]")
    parts.append(req.customerMessage or "")
    parts.append("")
    parts.append("[AI 가 추천했던 답]")
    parts.append(req.aiSuggestion or "")
    parts.append("")
    parts.append("[사장님이 실제로 보낸 답]")
    parts.append(req.ownerReply or "")
    parts.append("")
    parts.append("위 차이가 의도(전략) 차이인지 말투 차이인지 판단하고, 의도 차이면 일반화된 한 줄 원칙으로. 형식: JSON 한 덩어리만.")
    return "\n".join(parts)


def _parse_infer_principle_response(text: str) -> dict:
    """LLM 응답에서 {principle, question} 강건 파싱. 실패 시 {principle: None}.

    1. 코드펜스(```json ... ```) 제거.
    2. 첫 { 부터 마지막 } 까지 추출.
    3. json.loads 시도.
    4. principle 키 검증 (str 또는 None).
    5. principle 이 너무 짧거나(<10자) 너무 길면(>80자) → None.
    """
    if not text:
        return {"principle": None}
    s = text.strip()
    # 코드펜스 제거
    if s.startswith("```"):
        # ```json\n...\n``` 또는 ```\n...\n```
        s = s.lstrip("`")
        # json 또는 빈 줄 시작 처리
        if s.lower().startswith("json"):
            s = s[4:].lstrip()
        # 끝 ``` 제거
        if s.endswith("```"):
            s = s[:-3].rstrip()
    # 첫 { 부터 마지막 } 까지
    start = s.find("{")
    end = s.rfind("}")
    if start < 0 or end < 0 or end <= start:
        return {"principle": None}
    json_str = s[start:end + 1]
    try:
        data = json.loads(json_str)
    except (json.JSONDecodeError, ValueError):
        return {"principle": None}
    if not isinstance(data, dict):
        return {"principle": None}
    principle = data.get("principle")
    # principle 검증
    if principle is None:
        return {"principle": None}
    if not isinstance(principle, str):
        return {"principle": None}
    principle = principle.strip()
    # 너무 짧거나 너무 길면 (스펙: 25~45자 권장, 안전 마진 10~80)
    if len(principle) < 10 or len(principle) > 80:
        return {"principle": None}
    question = data.get("question")
    if isinstance(question, str):
        question = question.strip()
        if len(question) > 300:
            question = question[:300]
    else:
        question = None
    out = {"principle": principle}
    if question:
        out["question"] = question
    return out


@app.post("/infer-principle")
async def infer_principle_endpoint(req: InferPrincipleRequest) -> dict:
    """§50 — 사장님 답장 vs AI 추천 차이에서 재사용 가능한 원칙 한 줄 추론.

    실패·애매 시 모두 {"principle": null}. 앱은 그것 받으면 카드 안 띄움.
    5xx 도 앱은 silent (catch swallow).
    """
    customer_msg = (req.customerMessage or "").strip()
    ai_sugg = (req.aiSuggestion or "").strip()
    owner_reply = (req.ownerReply or "").strip()
    # 필수 입력 비어있으면 null (앱이 검증해야 하지만 안전망)
    if not customer_msg or not ai_sugg or not owner_reply:
        print(f"[infer-principle] 입력 비어있음 → null (msg={bool(customer_msg)} sugg={bool(ai_sugg)} reply={bool(owner_reply)})")
        return {"principle": None}

    if not CLAUDE_API_KEY:
        print("[infer-principle] CLAUDE_API_KEY 미설정 → null")
        return {"principle": None}

    system_prompt = _build_infer_principle_system_prompt(
        req.existingPrinciples or [], req.ownerTrade
    )
    user_msg = _build_infer_principle_user_message(req)

    try:
        response = await claude_client.messages.create(
            model=HAIKU_MODEL,
            max_tokens=400,
            system=system_prompt,
            messages=[{"role": "user", "content": user_msg}],
        )
    except anthropic.BadRequestError as e:
        # 4xx — 크레딧 부족 등. silent null.
        print(f"[infer-principle] Anthropic 4xx: {type(e).__name__}: {str(e)[:200]}")
        return {"principle": None}
    except Exception as e:
        print(f"[infer-principle] Anthropic 호출 실패: {type(e).__name__}: {str(e)[:200]}")
        return {"principle": None}

    # 응답 텍스트 추출
    raw_text = ""
    try:
        for block in response.content:
            if getattr(block, "type", None) == "text":
                raw_text += getattr(block, "text", "") or ""
    except Exception as e:
        print(f"[infer-principle] response.content 추출 실패: {type(e).__name__}: {e}")
        return {"principle": None}

    # 사용량 로깅
    try:
        usage = response.usage
        prompt_tokens = getattr(usage, "input_tokens", 0) or 0
        completion_tokens = getattr(usage, "output_tokens", 0) or 0
        cache_read = getattr(usage, "cache_read_input_tokens", 0) or 0
        cache_write = getattr(usage, "cache_creation_input_tokens", 0) or 0
        log_llm_usage(
            endpoint="infer-principle",
            model=response.model or HAIKU_MODEL,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            cache_read_tokens=cache_read,
            cache_write_tokens=cache_write,
        )
    except Exception as e:
        print(f"[infer-principle] usage 기록 실패 (무시): {type(e).__name__}: {e}")
        prompt_tokens = completion_tokens = 0

    parsed = _parse_infer_principle_response(raw_text)
    print(
        f"[infer-principle] OK in={prompt_tokens} out={completion_tokens} "
        f"deviceId={req.deviceId or '-'} trade={req.ownerTrade or '-'} "
        f"existing={len(req.existingPrinciples or [])} "
        f"result={'principle' if parsed.get('principle') else 'null'} "
        f"raw_len={len(raw_text)}"
    )
    return parsed


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
    phone: str                                      # *고객* phone (통화 상대)
    raw_text: str                                   # 에이닷 통화요약 원문 (길 수 있음)
    direction: str                                  # "incoming" | "outgoing" | "missed"
    duration_sec: int = 0
    started_at_ms: int = 0                          # 통화 시작 epoch ms (캐시 키)
    customer_name: Optional[str] = None
    customer_memo: Optional[str] = None
    owner_tone_samples: list[str] = Field(default_factory=list)
    # 추가37 (2026-06-18) — 화이트리스트 게이트용 사장님 phone (req.phone 은 customer 라 부적절).
    owner_phone: Optional[str] = None
    ownerTrade: Optional[str] = None   # 추가50 (2026-06-21) — 앱 onboarding 업종 (저장용)


CALL_SUMMARY_SYSTEM = """너는 1인 시공자(줄눈/타일) 사장님의 비서다.
통화녹음 받아쓰기 텍스트를 받아서, 사장님이 채팅 📞 카드에서 한눈에 파악 가능하게 정제한다.

⚠️ 가장 중요 — 통화는 **두 사람(사장님 + 고객)** 의 대화다.
받아쓰기는 화자 라벨이 없으니, 맥락으로 누가 무슨 말 했는지 추정해야 한다.
**누가 묻고 누가 답했는지를 명확히 박는 게 이 요약의 핵심 가치다.**

화자 추정 단서:
- 사장님 발화: "네 시공막내입니다", "안녕하세요" 인사받기, 가격 제시, 일정 제안, "사장님" 호칭 받음
- 고객 발화: 문의 시작, 질문 ("얼마예요?" "가능해요?"), "사장님 ~?" 호칭, 본인 위치/평수 알림
- 양쪽 다 인사하면: 보통 먼저 "여보세요/안녕하세요" 가 고객, 응답이 사장님

규칙:
- title: **6~12자** 짧은 제목 1줄. 앱의 통화카드 헤더에 굵게 표시.
  통화 핵심 키워드만 (예: "욕실 줄눈 견적 문의", "잔금 입금 약속", "시공일 변경 요청", "부재중 콜백 요청").
  · one_line 의 짧은 버전. 가격·평수 등 숫자 제외.
  · 명사구 (동사로 끝나는 거 X). "~ 문의 / ~ 약속 / ~ 요청 / ~ 안내 / ~ 통화" 같은 끝.
  · 부재중 = "부재중 (콜백 필요)" / "부재중 콜백" 류.

- one_line: 18~28자. 이 통화의 핵심 결과 1줄 (예: "24평 화장실 줄눈 견적 65만원 안내", "수원-인천 출장비 협의 필요").
  단순 "견적 요청" 식 키워드 X — 결과까지 들어가야 한다.

- bullets: **4~7줄**. 각 줄 이모지 1개로 시작 (🙋 고객 / 🏢 사장님 구분 권장).
  각 줄 한 문장, 40자 이내. **누가 누구한테 무엇을 했는지** 박아라.
  좋은 예:
    "🙋 고객: 24평 화장실 2곳 줄눈 견적 문의"
    "🏢 사장님 답: 65만원 안내"
    "🙋 고객: 사무실 어디인지 질문"
    "🏢 사장님 답: 수원이라고 안내"
    "🙋 고객: 본인은 인천 거주라고 알림"
    "💰 견적 65만원 / 📍 사장님=수원, 고객=인천 / 📅 첫입주 시기 확인 필요"
  나쁜 예 (현재 약점):
    "📍 수원 위치"  ← 누가 수원인지 모름
    "💰 65만원 견적"  ← 누가 제시한 가격인지 모름

- suggested_followup_sms: 통화 후 고객에게 보낼 정리 문자 (사장님이 ▶ 발송).
  · "고객님, 통화 내용 정리드립니다." 로 시작 + \\n.
  · 그 뒤 "- " 로 시작하는 2~5줄. **통화에서 합의·확인된 사항 + 추가로 보낼 정보**.
  · 사장님 → 고객 관점 (예: "- 양쪽 화장실 줄눈 65만원으로 안내드렸어요").
  · 통화에서 나온 사실만. 가격·날짜·평수 창작·추측 금지.
  · 마무리 한 줄 사장님 톤 (예: "확인 부탁드립니다 ^^" / "감사합니다 ~").
  · 길이 320자 이내. 자동 발송 절대 X — 사장님 ▶ 검수 후 발송.

- 통화 방향(direction): missed 면 one_line 에 "부재중" 명시.
- 원문에 명시된 가격·일정·약속만 답에 박아라. 추측·창작 일절 금지.

────── 가격표 (참고용) ──────
__PRICING__

────── 사장님 톤 (어휘 참고) ──────
__OWNER_TONE_SAMPLES__

답 형식 — 반드시:
- 응답 첫 글자는 '{' 로. 다른 텍스트 X.
- {"title":"...","one_line":"...","bullets":["🙋 ...","🏢 ..."],"suggested_followup_sms":"..."}
"""


def _coerce_call_summary(parsed: dict) -> dict:
    """LLM 응답을 안전한 dict 로 정리. 누락 필드는 기본값 채움.

    title, one_line, bullets, suggested_followup_sms 만 통과시킴 (extra 키 무시).
    """
    one_line = str(parsed.get("one_line") or "").strip()
    if not one_line:
        raise ValueError("one_line 누락")
    # 30자 안전 컷 (LLM 가 가끔 넘침)
    if len(one_line) > 40:
        one_line = one_line[:40].rstrip() + "…"

    # 추가61 (2026-06-25) — title (6~12자 짧은 제목). 없으면 one_line 폴백.
    title = str(parsed.get("title") or "").strip()
    if title:
        # 안전 컷 16자 (LLM 가 가끔 넘침). 길면 잘라 + …
        if len(title) > 16:
            title = title[:16].rstrip() + "…"
    else:
        # 폴백 = one_line 앞 14자 (앱이 헤더 표시할 게 필요)
        title = one_line[:14].rstrip() + ("…" if len(one_line) > 14 else "")

    raw_bullets = parsed.get("bullets")
    bullets: list[str] = []
    if isinstance(raw_bullets, list):
        # §26 (2026-06-10) — 5 → 7줄로 확장 (Q&A 흐름 + 화자 구분 박을 공간 확보)
        for b in raw_bullets[:7]:
            s = str(b).strip()
            if s:
                bullets.append(s if len(s) <= 80 else s[:80].rstrip() + "…")
    # bullets 비어있으면 최소 one_line 한 줄이라도 — 앱 측 안전망
    if not bullets:
        bullets = [one_line]

    fup_raw = parsed.get("suggested_followup_sms")
    if isinstance(fup_raw, str) and fup_raw.strip():
        fup = fup_raw.strip()
        # 고객용 정리 문자 = 여러 줄 가능 → 컷을 넉넉히(잘려서 어색해지는 것 방지).
        if len(fup) > 480:
            fup = fup[:480].rstrip() + "…"
    else:
        fup = None

    return {
        "title": title,  # 추가61 — 6~12자 짧은 제목
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
    # 추가37 (2026-06-18) — 가드는 owner_phone 으로 (req.phone 은 *고객* phone). 없으면 skip.
    _ensure_and_touch_beta_whitelist(req.owner_phone, owner_trade=getattr(req, 'ownerTrade', None))
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
        code, msg = _classify_llm_error(e)
        print(f"[call-summary] {req.phone} Claude 호출 실패: {type(e).__name__}: {e} → {code} '{msg}'")
        raise HTTPException(code, msg)

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
# §26 — POST /api/call-audio-summary (통화 녹음 → 로컬 Whisper STT → Haiku 요약)
# ─────────────────────────────────────────────────────────────────────────────
# 안드로이드 SERVER_HANDOFF 2026-06-08: 에이닷 "통화 텍스트 저장"이 유료 → 녹음
# (m4a) 공유는 무료 → 사장님 결정 "무료 녹음 + 로컬 무료 STT".
#
# 흐름:
#   1. 업로드된 오디오 (m4a/mp3/wav) → tempfile 저장
#   2. faster-whisper "base" 모델 (~75MB, CPU int8) 로 한국어 STT → transcript
#   3. transcript 를 기존 /api/call-summary 와 같은 Haiku 요약 (재사용)
#   4. 응답 = {one_line, bullets, suggested_followup_sms, transcript}
#   5. 캐시 키 (phone, "call-audio-summary", started_at_ms) — 재호출 시 STT+LLM 둘 다 0원
#
# 비용: API 0, 전기값만. Mac mini CPU 1분 통화 ~10초 처리.
# 동시 호출 직렬화 (asyncio.Lock) — 메모리 보호.
# ============================================================================

_WHISPER_MODEL = None
_WHISPER_LOCK: Optional["asyncio.Lock"] = None


def _get_whisper_model():
    """faster-whisper base 모델 lazy load (첫 호출 시 ~75MB 다운로드 + 메모리 적재)."""
    global _WHISPER_MODEL
    if _WHISPER_MODEL is not None:
        return _WHISPER_MODEL
    try:
        from faster_whisper import WhisperModel
    except ImportError as e:
        raise HTTPException(
            503,
            "faster-whisper 패키지 미설치. requirements.txt 의 faster-whisper 설치 후 재시작 필요.",
        ) from e
    # §26 fix 502 #2 (2026-06-10):
    # faster-whisper venv 직접 호출은 OK 인데 uvicorn 안에서만 워커 SIGSEGV → 502.
    # 원인 = 내부 OMP/MKL/CPU 멀티스레드 + uvicorn async loop 충돌.
    # → cpu_threads=1, num_workers=1, OMP/MKL_NUM_THREADS=1 로 직렬화 (속도 약간 ↓ 대신 안정).
    os.environ.setdefault("OMP_NUM_THREADS", "1")
    os.environ.setdefault("MKL_NUM_THREADS", "1")
    os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")
    print("[whisper] base 모델 로드 시작 (CPU, default, cpu_threads=1, num_workers=1)")
    _WHISPER_MODEL = WhisperModel(
        "base",
        device="cpu",
        compute_type="default",
        cpu_threads=1,
        num_workers=1,
    )
    print("[whisper] base 모델 로드 완료 (한국어 STT)")
    return _WHISPER_MODEL


def _whisper_lock() -> "asyncio.Lock":
    """전역 lock — 동시 STT 호출 직렬화 (메모리/CPU 보호)."""
    global _WHISPER_LOCK
    if _WHISPER_LOCK is None:
        _WHISPER_LOCK = asyncio.Lock()
    return _WHISPER_LOCK


def _audio_suffix(filename: Optional[str]) -> str:
    """파일명 끝 확장자 안전 추출 (m4a/mp3/wav). 모르면 .m4a 기본."""
    name = (filename or "").lower().strip()
    for ext in (".m4a", ".mp3", ".wav", ".aac", ".ogg", ".flac"):
        if name.endswith(ext):
            return ext
    return ".m4a"


# §26 Option A (2026-06-10) — 긴 통화 청크 병렬 처리
# Cloudflare 100초 hard timeout 우회 위해 audio 를 5분씩 split, STT 병렬 실행.
# 전체 시간 = max(청크 처리) — 11분 통화도 ~60-80초 안에 끝남.
CHUNK_DURATION_SEC = 300  # 5분
LONG_CALL_THRESHOLD_SEC = 320  # 5분 20초 이상이면 chunk (300초 정확히는 chunk 안 함, 여유)

# §26 fix (2026-06-10): launchd 의 PATH 에 /opt/homebrew/bin 없어서 ffmpeg/ffprobe
# 못 찾음 → 절대경로 fallback. shutil.which 먼저 시도, 없으면 homebrew 표준 경로.
import shutil as _shutil
_FFMPEG_BIN = _shutil.which("ffmpeg") or "/opt/homebrew/bin/ffmpeg"
_FFPROBE_BIN = _shutil.which("ffprobe") or "/opt/homebrew/bin/ffprobe"


async def _ffprobe_duration(audio_path: str) -> float:
    """ffprobe 로 audio 길이 (초) 반환. 실패 시 0.0."""
    try:
        proc = await asyncio.create_subprocess_exec(
            _FFPROBE_BIN,
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            audio_path,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        out, _ = await asyncio.wait_for(proc.communicate(), timeout=15.0)
        return float(out.decode().strip() or "0")
    except Exception as e:
        print(f"[chunk] ffprobe 실패: {type(e).__name__}: {e}")
        return 0.0


async def _split_audio_to_chunks(
    audio_path: str, chunk_seconds: int = CHUNK_DURATION_SEC
) -> list[tuple[str, int, int]]:
    """audio 를 chunk_seconds 씩 split. (chunk_path, start_sec, end_sec) 리스트.

    ffmpeg 로 -c copy (재인코딩 없이 stream copy) — 빠름.
    실패 시 빈 list (caller 는 단일 처리로 fallback).
    """
    duration = await _ffprobe_duration(audio_path)
    if duration <= 0 or duration <= LONG_CALL_THRESHOLD_SEC:
        # 짧은 통화 또는 길이 파악 실패 — 단일 처리 (caller 에서 처리)
        return []

    import tempfile
    suffix = Path(audio_path).suffix or ".m4a"
    n_chunks = int((duration + chunk_seconds - 1) // chunk_seconds)
    chunks: list[tuple[str, int, int]] = []
    for i in range(n_chunks):
        start = i * chunk_seconds
        end = min(start + chunk_seconds, int(duration) + 1)
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
            chunk_path = tmp.name
        proc = await asyncio.create_subprocess_exec(
            _FFMPEG_BIN,
            "-y", "-loglevel", "error",
            "-i", audio_path,
            "-ss", str(start),
            "-t", str(end - start),
            "-c:a", "copy",
            chunk_path,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        try:
            _, err_b = await asyncio.wait_for(proc.communicate(), timeout=30.0)
        except asyncio.TimeoutError:
            proc.kill()
            await proc.wait()
            print(f"[chunk {i+1}/{n_chunks}] ffmpeg split timeout")
            # cleanup 이미 만든 chunks
            for cp, _, _ in chunks:
                try: os.unlink(cp)
                except: pass
            try: os.unlink(chunk_path)
            except: pass
            return []
        if proc.returncode != 0:
            err = err_b.decode("utf-8", errors="replace")[:200]
            print(f"[chunk {i+1}/{n_chunks}] ffmpeg 실패 rc={proc.returncode}: {err}")
            for cp, _, _ in chunks:
                try: os.unlink(cp)
                except: pass
            try: os.unlink(chunk_path)
            except: pass
            return []
        chunks.append((chunk_path, start, end))
    print(f"[chunk] split 완료 {n_chunks}개 청크 (총 {int(duration)}초)")
    return chunks


async def _run_stt_one_subprocess(audio_path: str, label: str = "") -> str:
    """단일 audio (또는 chunk) STT subprocess 호출. 실패 시 raise."""
    worker_script = BASE_DIR / "whisper_worker.py"
    if not worker_script.exists():
        raise HTTPException(503, "whisper_worker.py 미배포")
    venv_python = "/Users/hun/ringgo-server/venv/bin/python"
    if not Path(venv_python).exists():
        venv_python = sys.executable
    proc = await asyncio.create_subprocess_exec(
        venv_python, str(worker_script), audio_path,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout_b, stderr_b = await asyncio.wait_for(
            proc.communicate(), timeout=120.0
        )
    except asyncio.TimeoutError:
        proc.kill()
        await proc.wait()
        raise HTTPException(504, f"STT timeout {label}".strip())
    if proc.returncode != 0:
        err = (stderr_b.decode("utf-8", errors="replace") or "").strip()[:400]
        raise HTTPException(502, f"STT 실패 {label} rc={proc.returncode}: {err[:200]}")
    return (stdout_b.decode("utf-8", errors="replace") or "").strip()


def _fmt_chunk_label(start: int, end: int) -> str:
    """0:00-5:00 형식."""
    def fmt(s: int) -> str:
        return f"{s//60}:{s%60:02d}"
    return f"{fmt(start)}-{fmt(end)}"


async def _run_stt_with_chunking(audio_path: str) -> str:
    """audio 길이 기반 자동 분기. 5분 미만 → 단일, 5분+ → 청크 병렬.

    응답 transcript 에 [1/N start-end] 라벨 박힘.
    """
    chunks = await _split_audio_to_chunks(audio_path)
    if not chunks:
        # 단일 처리
        print("[chunk] 단일 처리 (5분 미만 또는 split 실패)")
        return await _run_stt_one_subprocess(audio_path, label="single")

    # 병렬 STT
    n = len(chunks)
    print(f"[chunk] 병렬 STT 시작 {n}개 청크")
    try:
        transcripts = await asyncio.gather(
            *[
                _run_stt_one_subprocess(cp, label=f"{i+1}/{n}")
                for i, (cp, _, _) in enumerate(chunks)
            ]
        )
    finally:
        # chunk 파일 cleanup
        for cp, _, _ in chunks:
            try: os.unlink(cp)
            except Exception: pass

    # 청크 라벨 박힌 전체 transcript 조립
    parts = []
    for i, ((_, start, end), txt) in enumerate(zip(chunks, transcripts)):
        label = f"[{i+1}/{n} {_fmt_chunk_label(start, end)}]"
        parts.append(f"{label}\n{txt}")
    full = "\n\n".join(parts).strip()
    print(f"[chunk] 병렬 STT 완료 {n}개 청크 → 합친 transcript {len(full)}자")
    return full


@app.post("/api/call-audio-summary")
async def call_audio_summary_endpoint(
    file: UploadFile = File(...),
    phone: str = Form(...),                          # *고객* phone (통화 상대)
    started_at_ms: int = Form(...),
    direction: str = Form("incoming"),
    duration_sec: int = Form(0),
    customer_name: Optional[str] = Form(None),
    customer_memo: Optional[str] = Form(None),
    owner_tone_samples: Optional[str] = Form(None),  # JSON 배열 string (최대 10개)
    force_refresh: bool = Form(False),                # §26 (2026-06-10) — true 면 캐시 무시 + 새로 처리
    owner_phone: Optional[str] = Form(None),         # 추가37 (2026-06-18) — 화이트리스트 게이트용 사장님 phone
    owner_trade: Optional[str] = Form(None),         # 추가50 (2026-06-21) — 앱 onboarding 업종
) -> dict:
    """통화 녹음 → Whisper STT → Gemini/Haiku 요약 → one_line + bullets + 후속 문자 + transcript.

    응답에 cached 필드:
      - cached=true  → DB 캐시에서 즉시 응답 (이미 처리됨, 안드로이드가 토스트 띄울 단서)
      - cached=false → 새로 STT + LLM 처리 후 응답
    재요약 흐름: 안드로이드가 cached=true 받음 → 사장님께 "다시 요약?" 물음 → Yes 면
    force_refresh=true 로 재호출 → 캐시 무시 + 새 처리.

    동기 응답. 통화 5분 미만 = 단일 / 5분+ = 청크 병렬. 앱은 read timeout 120s+ 권장.
    """
    if not phone:
        raise HTTPException(400, "phone 필수")
    # 추가37 (2026-06-18) — 가드는 owner_phone 으로 (phone 은 *고객* phone). 안드로이드가 안 보내면 skip.
    _ensure_and_touch_beta_whitelist(owner_phone)
    phone_digits = "".join(ch for ch in phone if ch.isdigit())
    if not phone_digits:
        raise HTTPException(400, "phone 형식 오류")

    cache_ts = started_at_ms or 0

    # 1) 캐시 hit + force_refresh=false 면 즉시 응답 (안드로이드가 cached=true 보고 토스트)
    if not force_refresh:
        cached = summary_cache_get(phone_digits, "call-audio-summary", cache_ts)
        if cached is not None:
            print(f"[call-audio-summary] {phone_digits} → cache HIT (started_at_ms={cache_ts})")
            # cached 응답에 명시적으로 cached=True (안드로이드가 분기)
            cached_with_flag = dict(cached)
            cached_with_flag["cached"] = True
            cached_with_flag["_cache_hit"] = True
            return cached_with_flag
    else:
        print(f"[call-audio-summary] {phone_digits} → force_refresh=true (캐시 무시)")

    check_rate_limit(phone_digits)

    # 2) 오디오 파일 → tempfile
    audio_data = await file.read()
    if not audio_data:
        raise HTTPException(400, "오디오 파일 비어있음")
    if len(audio_data) > 50 * 1024 * 1024:  # 50MB cap (긴 통화 보호)
        raise HTTPException(413, "오디오 파일 너무 큼 (50MB 이하)")

    import tempfile
    suffix = _audio_suffix(file.filename)
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp.write(audio_data)
        tmp_path = tmp.name

    transcript = ""
    try:
        # 3) Whisper STT — §26 Option A (2026-06-10):
        # 5분 미만 = 단일 subprocess, 5분 이상 = chunk 병렬 (Cloudflare 100s timeout 우회).
        # subprocess 격리 유지 (uvicorn worker 보호).
        lock = _whisper_lock()
        async with lock:  # 메모리 보호 위해 동시 요청 1건만 (각 요청 안의 청크는 병렬)
            try:
                print(f"[call-audio-summary] {phone_digits} STT 시작 audio={len(audio_data)//1024}KB")
                transcript = await _run_stt_with_chunking(tmp_path)
                print(f"[call-audio-summary] {phone_digits} STT 완료 transcript={len(transcript)}자")
            except HTTPException:
                raise
            except Exception as e:
                import traceback as _tb
                print(f"[call-audio-summary] STT 실패: {type(e).__name__}: {e}")
                print(_tb.format_exc())
                raise HTTPException(502, f"STT 실패: {type(e).__name__}: {e}")
    finally:
        try:
            os.unlink(tmp_path)
        except Exception:
            pass

    if not transcript:
        raise HTTPException(422, "받아쓰기 결과 비어있음 (무음 또는 인식 불가)")

    print(
        f"[call-audio-summary] {phone_digits} STT 완료 "
        f"(audio={len(audio_data)//1024}KB transcript={len(transcript)}자)"
    )

    # 4) owner_tone_samples JSON 파싱 (실패 시 빈 list)
    samples_list: list[str] = []
    if owner_tone_samples:
        try:
            parsed_samples = json.loads(owner_tone_samples)
            if isinstance(parsed_samples, list):
                samples_list = [str(s) for s in parsed_samples if s][:10]
        except Exception:
            samples_list = []

    # 5) 기존 /api/call-summary 와 같은 패턴으로 user message 빌드
    user_lines: list[str] = []
    user_lines.append("[고객 정보]")
    user_lines.append(f"전화번호: {phone_digits}")
    user_lines.append(f"이름: {customer_name or '미등록'}")
    if customer_memo:
        user_lines.append(f"메모: {customer_memo}")
    user_lines.append("")
    user_lines.append("[통화 메타]")
    user_lines.append(f"방향: {direction}")
    user_lines.append(f"길이(초): {duration_sec}")
    user_lines.append(f"시작 시각(epoch ms): {started_at_ms}")
    user_lines.append("")
    user_lines.append("[통화 받아쓰기 — Whisper STT]")
    raw = transcript
    if len(raw) > 8000:
        raw = raw[:8000] + "\n…(truncated)"
    user_lines.append(raw)
    user_msg = "\n".join(user_lines)

    system_prompt = _build_summary_system_prompt(CALL_SUMMARY_SYSTEM, samples_list)

    # §26 (2026-06-10) — 사장님 결정: 1차 Gemini 2.5 Flash + 2차 Haiku fallback
    # Gemini Flash = Haiku 의 ~1/10 비용 + 정확도 동급/우수 + Paid tier 데이터 학습 X
    # ollama (Qwen 7B) 는 화자 구분 부족으로 정확도 떨어져서 제외 (코드는 유지 — env 토글로 재활성 가능)
    parsed = None
    response = None  # Haiku Anthropic 응답 객체 (Gemini 일 땐 None)
    used_llm = ""
    gemini_usage: dict = {}
    try:
        # §26 fix (2026-06-10): 긴 통화 (4000+ 자 transcript) → 응답 풍부
        # → 800 token 모자라서 응답 잘림 (JSONDecodeError: Unterminated string).
        # 2000 token 으로 늘림 (Gemini 비용은 output token 기준 무시할 수준).
        parsed, gemini_usage = await _call_gemini_json_for_summary(
            system_prompt, user_msg, max_output_tokens=2000
        )
        used_llm = "gemini-2.5-flash"
        print(
            f"[call-audio-summary] {phone_digits} → gemini OK "
            f"(in={gemini_usage.get('promptTokenCount','?')} "
            f"out={gemini_usage.get('candidatesTokenCount','?')})"
        )
    except Exception as gemini_err:
        print(
            f"[call-audio-summary] {phone_digits} gemini 실패, Haiku fallback: "
            f"{type(gemini_err).__name__}: {gemini_err}"
        )
        try:
            # §26 fix (2026-06-10): 600 token 짧음 (Bad JSON 자름) → 1500 token.
            parsed, response = await call_claude_json(
                system_prompt=system_prompt,
                user_msg=user_msg,
                max_tokens=1500,
                model=HAIKU_MODEL,
            )
            used_llm = HAIKU_MODEL
        except Exception as claude_err:
            # §26 (2026-06-11) — Anthropic 4xx 명확화 (사장님 즉시 진단)
            code, msg = _classify_llm_error(claude_err)
            print(
                f"[call-audio-summary] {phone_digits} Haiku 도 실패: "
                f"{type(claude_err).__name__}: {claude_err} → {code} '{msg}'"
            )
            raise HTTPException(code, msg)

    # 비용 로깅: Haiku 일 때만 (Gemini Paid 비용은 별도 추적 안 함 — 매우 저렴)
    if response is not None:
        log_usage(phone_digits, "call-audio-summary", response)
        _log_llm_usage_from_response("call-audio-summary", response)
        usage = response.usage
        print(
            f"[call-audio-summary] {phone_digits} → ready haiku "
            f"(in={getattr(usage,'input_tokens',0)} "
            f"cache_read={getattr(usage,'cache_read_input_tokens',0)} "
            f"out={getattr(usage,'output_tokens',0)})"
        )
    else:
        print(f"[call-audio-summary] {phone_digits} → ready gemini")

    try:
        coerced = _coerce_call_summary(parsed)
    except ValueError as e:
        raise HTTPException(502, f"LLM 응답 형식 오류: {e}")

    response_payload = {
        **coerced,
        "transcript": transcript,
        "phone": phone_digits,
        "direction": direction,
        "duration_sec": duration_sec,
        "started_at_ms": started_at_ms,
        "generated_at_ms": _now_ms(),
    }
    # 캐시 저장 (transcript 포함 — 같은 통화 재호출 시 STT+LLM 둘 다 0원)
    summary_cache_set(phone_digits, "call-audio-summary", cache_ts, response_payload)
    response_payload["_cache_hit"] = False
    response_payload["cached"] = False  # §26 (2026-06-10) — 안드로이드 UX 분기용
    return response_payload


# ============================================================================
# §27 — 협업 현장 (사장 ↔ 사장 공유, 안드로이드 SERVER_HANDOFF 2026-06-08)
# ─────────────────────────────────────────────────────────────────────────────
# A(현장 주인) 가 B(협업 사장) 에게 현장 1건을 공유. 둘 다 RING-GO 가입 사장.
#
# 핵심 벽 (모든 응답에서 절대 안 나가는 것):
#   ❌ 고객 phone / 고객 대화 / A 의 다른 고객 / A 의 매출
# 응답에 박는 것 (안전):
#   ✅ 현장 정보 (title, addr, scheduled_at_ms, work_summary, memo)
#   ✅ A 의 이름 (subscribers.name) + customer_label ('강동 서사장님 현장' 같은 안전 라벨)
#   ✅ 상태 (status, progress) + 계좌 (B 가 completed 시 보내는 것, A 에게만)
#
# 인증:
#   - owner_phone (A) 또는 partner_phone (B) 본인만 접근. 다른 share_id 는 403.
#   - bizPhone = 가입 사장 = subscribers 또는 beta_signups (accepted) 에 등록된 phone.
#
# 6 endpoint:
#   ① GET  /api/owner/exists?phone=        가입 사장 디렉터리 (inapp/link 분기용)
#   ② POST /api/shared/invite              A → B 협업 요청
#   ③ GET  /api/shared/with-me?phone=B     B 가 받은 공유 현장 목록 (B 화면 핵심)
#   ④ POST /api/shared/respond             B 수락/거절
#   ⑤ POST /api/shared/progress            B 출발/도착/완료 (completed 시 계좌 payload)
#   ⑥ POST /api/shared/paid                A 입금 완료 표시
# ============================================================================

import secrets as _secrets_collab  # noqa: E402  (§27 share_id 생성 전용)
_SHARE_ID_ALPHABET = (
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
)


def _gen_share_id() -> str:
    """sh_ + 10자 base62 (충돌 확률 ~62^10 = ~8e17 — 충분히 안전)."""
    return "sh_" + "".join(
        _secrets_collab.choice(_SHARE_ID_ALPHABET) for _ in range(10)
    )


def _norm_phone(p: Optional[str]) -> str:
    """전화번호 정규화: 숫자만 남김. 빈/None → ''."""
    return "".join(ch for ch in (p or "") if ch.isdigit())


def _fmt_phone(p: Optional[str]) -> str:
    """전화번호 표시 format — '010-1234-5678' 형태.

    module-level (NameError 방지 — 다른 nested _fmt_phone 가 있어 module 호출 시 못 찾는 케이스 fix).
    """
    s = _norm_phone(p)
    if len(s) == 11 and s.startswith("010"):
        return f"{s[:3]}-{s[3:7]}-{s[7:]}"
    if len(s) == 10:
        return f"{s[:3]}-{s[3:6]}-{s[6:]}"
    return s or ""


def _is_registered_owner(phone_digits: str) -> Optional[str]:
    """가입 사장 디렉터리 확인 — subscribers 우선, 그 다음 beta_signups (accepted).

    반환: 가입자면 표시명 (subscribers.name 또는 beta_signups 의 region/industry 조합),
          미가입이면 None.
    """
    if not phone_digits:
        return None
    with db_conn() as con:
        # 1) subscribers (정식 가입 우선)
        row = con.execute(
            "SELECT name, company FROM subscribers WHERE phone = ? AND churned_at_ms IS NULL",
            (phone_digits,),
        ).fetchone()
        if row:
            name = (row[0] or "").strip()
            company = (row[1] or "").strip()
            return name or company or "사장님"
        # 2) beta_signups (status=accepted) 도 사장 디렉터리에 포함 (베타 기간)
        row = con.execute(
            "SELECT industry, region FROM beta_signups WHERE phone = ? AND status = 'accepted'",
            (phone_digits,),
        ).fetchone()
        if row:
            industry = (row[0] or "").strip()
            region = (row[1] or "").strip()
            label = " ".join([s for s in (region, industry) if s]).strip()
            return label or "사장님"
    return None


def _shared_site_row_to_dict(row: tuple, viewer_kind: str = "partner") -> dict:
    """shared_sites row → API 응답 dict. viewer_kind = 'owner'|'partner'.

    벽: 고객 phone / 대화 / 다른 고객 절대 X. 안전한 필드만 셀렉트.
    계좌는 모든 viewer 에게 (A 는 입금용, B 는 본인이 등록한 정보 확인).
    """
    (
        share_id,
        owner_phone,
        partner_phone,
        title,
        addr,
        scheduled_at_ms,
        work_summary,
        memo,
        customer_label,
        status,
        progress,
        account_bank,
        account_no,
        account_holder,
        paid_at_ms,
        daily_wage,                 # §A (2026-06-13)
        time_label_raw,             # §A-2 (2026-06-13)
        owner_name_raw,             # §A-3 (2026-06-13)
        created_at_ms,
        updated_at_ms,
        accepted_at_ms,             # 추가63 (2026-06-29) — 협업 진행 카드용 전환 시각
        departed_at_ms,
        arrived_at_ms,
        completed_at_ms,
    ) = row
    # 시각 라벨 (HH:MM, 안드로이드 측 편의)
    # §A-2 (2026-06-13) — invite 시 받은 자연어 time_label_raw ('오전 9시') 우선, 없으면 HH:MM 자동.
    time_label = (time_label_raw or "").strip()
    if not time_label and scheduled_at_ms:
        try:
            time_label = _dt.datetime.fromtimestamp(scheduled_at_ms / 1000).strftime("%H:%M")
        except Exception:
            time_label = ""
    out = {
        "share_id": share_id,
        "owner_phone": owner_phone,    # A 측 phone (B 가 누가 공유했는지 알아야)
        "partner_phone": partner_phone,
        "title": title or "",
        "addr": addr or "",
        "scheduled_at_ms": scheduled_at_ms or 0,
        "time_label": time_label,
        "work_summary": work_summary or "",
        "memo": memo or "",
        "customer_label": customer_label or "",
        "status": status,
        "progress": progress,
        "created_at_ms": created_at_ms,
        "updated_at_ms": updated_at_ms,
    }
    if viewer_kind == "partner":
        # B 가 owner_name 보여야 ("디테일라인과 함께") — §A-3: invite payload 의 owner_name 우선.
        out["owner_name"] = (
            (owner_name_raw or "").strip()
            or _is_registered_owner(owner_phone)
            or "사장님"
        )
    # 계좌 — completed 이상에서만 (B 가 보낸 후 A 가 입금용으로 봐야)
    if account_bank or account_no:
        out["account"] = {
            "bank": account_bank or "",
            "account_no": account_no or "",
            "holder": account_holder or "",
        }
    if paid_at_ms:
        out["paid_at_ms"] = paid_at_ms
    # §A (2026-06-13) 일당 echo — 값 있을 때만 (앱 graceful).
    if daily_wage is not None:
        try:
            out["daily_wage"] = int(daily_wage)
        except Exception:
            pass
    # 추가63 (2026-06-29) — 전환 시각 echo (값 있을 때만, 앱 graceful)
    if accepted_at_ms:
        out["accepted_at_ms"] = accepted_at_ms
    if departed_at_ms:
        out["departed_at_ms"] = departed_at_ms
    if arrived_at_ms:
        out["arrived_at_ms"] = arrived_at_ms
    if completed_at_ms:
        out["completed_at_ms"] = completed_at_ms
    return out


_SHARED_SITES_COLS = (
    "share_id, owner_phone, partner_phone, title, addr, scheduled_at_ms, "
    "work_summary, memo, customer_label, status, progress, "
    "account_bank, account_no, account_holder, paid_at_ms, "
    "daily_wage, "        # §A (2026-06-13)
    "time_label_raw, "    # §A-2 (2026-06-13)
    "owner_name_raw, "    # §A-3 (2026-06-13)
    "created_at_ms, updated_at_ms, "
    "accepted_at_ms, departed_at_ms, arrived_at_ms, completed_at_ms"  # 추가63
)


# ─── ① GET /api/owner/exists ───
# 가입 사장 디렉터리 확인 (인앱/링크 분기용)

@app.get("/api/owner/exists")
async def shared_owner_exists(phone: str) -> dict:
    """phone 이 가입 사장이면 {registered: true, name}, 아니면 {registered: false}."""
    phone_digits = _norm_phone(phone)
    if not phone_digits:
        raise HTTPException(400, "phone 필수")
    name = _is_registered_owner(phone_digits)
    if name:
        return {"registered": True, "name": name}
    # §FCM-fix (2026-06-12): push_tokens 도 인앱 판정 소스로 인정.
    #   subscribers/beta_signups 디렉터리에 없어도 앱이 FCM 토큰 등록했으면 = 인앱 사용자.
    #   (안드로이드 진단 SYNC 2026-06-12: B 가 register code=200 인데 link 로 떨어짐.)
    if _get_tokens_for_phone(phone_digits):
        return {"registered": True, "name": "사장님"}
    return {"registered": False, "name": None}


# ─── ② POST /api/shared/invite ───
# A → B 협업 요청 (고객 phone 받지 않음)

class SharedInviteRequest(BaseModel):
    owner_phone: str                       # A
    partner_phone: str                     # B
    title: Optional[str] = None
    addr: Optional[str] = None
    scheduled_at_ms: Optional[int] = None
    work_summary: Optional[str] = None
    memo: Optional[str] = None
    customer_label: Optional[str] = None   # '강동 서사장님 현장' 같은 안전 라벨 (고객 phone 대신)
    daily_wage: Optional[int] = None       # §A (2026-06-13) 그날 일당 (만원 단위, 예: 25 = 25만)
    time_label: Optional[str] = None       # §A-2 (2026-06-13) 시각 라벨 ('오전 9시' 자연어)
    owner_name: Optional[str] = None       # §A-3 (2026-06-13) 초대한 사장 상호 ('디테일라인')


@app.post("/api/shared/invite")
async def shared_invite(req: SharedInviteRequest) -> dict:
    """A 가 B 에게 현장 공유 요청. 베타 기간 _check_team_tier 통과 (TEAM_TIER_BYPASS=1)."""
    owner_phone = _norm_phone(req.owner_phone)
    partner_phone = _norm_phone(req.partner_phone)
    if not owner_phone:
        raise HTTPException(400, "owner_phone 필수")
    if not partner_phone:
        raise HTTPException(400, "partner_phone 필수")
    if owner_phone == partner_phone:
        raise HTTPException(400, "본인에게 공유할 수 없습니다")
    _ensure_and_touch_beta_whitelist(owner_phone)  # 추가36 (2026-06-18) — 화이트리스트 게이트 (owner 만)
    _check_team_tier(owner_phone)

    now = _now_ms()
    share_id = _gen_share_id()
    title = (req.title or "").strip()[:100]
    addr = (req.addr or "").strip()[:200]
    work_summary = (req.work_summary or "").strip()[:200]
    memo = (req.memo or "").strip()[:500]
    customer_label = (req.customer_label or "").strip()[:60]

    # §A (2026-06-13) 일당 — 음수/비현실값 가드. None 이면 그대로 None.
    daily_wage_val: Optional[int] = None
    if req.daily_wage is not None:
        try:
            dw = int(req.daily_wage)
            if 0 < dw <= 10000:  # 만원 단위, 1만~1억 사이
                daily_wage_val = dw
        except Exception:
            daily_wage_val = None
    # §A-2 (2026-06-13) 시각 라벨 ('오전 9시' 자연어). 비면 None.
    time_label_raw_val: Optional[str] = None
    if req.time_label:
        s = req.time_label.strip()[:30]
        if s:
            time_label_raw_val = s
    # §A-3 (2026-06-13) 초대한 사장 상호 ('디테일라인'). 비면 None.
    owner_name_raw_val: Optional[str] = None
    if req.owner_name:
        s = req.owner_name.strip()[:60]
        if s:
            owner_name_raw_val = s

    # §dedup (2026-06-13, SYNC android 추가2 요청):
    # 같은 owner+partner+title 이고 미완(pending/accepted, paid_at_ms NULL) share 있으면 그것 반환.
    # 추가35 (2026-06-18 버그fix): scheduled_at_ms 도 매치 조건에 포함.
    #   - 이전: title 만 비교 → 다른 날짜 invite 가 옛 share 와 dedup 되어 신규 share 안 만들어짐.
    #     사장님 보고: 17일·24일 일정이 새로 공유 안 됨 (옛 pending share 가 매치되어 re-poke 만 발생).
    #   - 수정: 같은 날짜(scheduled_at_ms) 에 같은 owner+partner+title 인 경우에만 dedup.
    #     날짜 다르면 새 share 만들어짐. 둘 다 NULL 인 케이스(날짜 없는 invite) 는 기존처럼 매치.
    with db_conn() as _con:
        title_match = title or ""
        existing = _con.execute(
            """
            SELECT share_id FROM shared_sites
            WHERE owner_phone = ?
              AND partner_phone = ?
              AND IFNULL(title,'') = ?
              AND IFNULL(scheduled_at_ms, 0) = IFNULL(?, 0)
              AND status IN ('pending','accepted')
              AND paid_at_ms IS NULL
              AND (progress IS NULL OR progress != 'completed')
            ORDER BY created_at_ms DESC
            LIMIT 1
            """,
            (owner_phone, partner_phone, title_match, req.scheduled_at_ms),
        ).fetchone()
    if existing:
        existing_id = existing[0]
        print(
            f"[shared/invite/dedup] {owner_phone} → {partner_phone} title='{title_match}' "
            f"→ 기존 share={existing_id} 재사용 (re-poke FCM)"
        )
        # ④ re-poke (2026-06-13) — 같은 협업 재요청 시 B 에게 FCM 한 번 더 (리마인드 효과).
        # 새 share 안 만들고 기존 share_id 그대로. dedup 응답은 동일.
        owner_name_for_fcm = (
            owner_name_raw_val
            or _is_registered_owner(owner_phone)
            or "사장님"
        )
        _send_fcm_data_to_phone(partner_phone, {
            "type": "collab_invite",
            "share_id": existing_id,
            "owner_name": owner_name_for_fcm,
            "title": title or "협업 현장",
        })
        return {
            "share_id": existing_id,
            "route": "inapp",
            "url": None,
            "sms_draft": None,
            "deduped": True,
        }

    with db_conn() as con:
        con.execute(
            f"""
            INSERT INTO shared_sites
                ({_SHARED_SITES_COLS})
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', 'assigned',
                    NULL, NULL, NULL, NULL, ?, ?, ?, ?, ?,
                    NULL, NULL, NULL, NULL)
            """,  # 추가63 fix — 4 컬럼 (accepted/departed/arrived/completed_at_ms) 추가 후 VALUES 에 NULL 누락 → 500 fix.
            (
                share_id,
                owner_phone,
                partner_phone,
                title or None,
                addr or None,
                req.scheduled_at_ms,
                work_summary or None,
                memo or None,
                customer_label or None,
                daily_wage_val,        # §A
                time_label_raw_val,    # §A-2
                owner_name_raw_val,    # §A-3
                now,
                now,
            ),
        )
        con.commit()

    # B 가 가입 사장이면 인앱, 아니면 링크 + sms_draft
    # §FCM-fix (2026-06-12): push_tokens 도 인앱 판정 소스로 인정.
    #   subscribers/beta_signups 디렉터리에 없어도 앱이 FCM 토큰 등록했으면 = 인앱 사용자.
    #   (안드로이드 진단 SYNC 2026-06-12: B 가 register code=200 인데 link 로 떨어짐 → 문자창 + FCM skip.)
    partner_name = _is_registered_owner(partner_phone)
    partner_tokens = _get_tokens_for_phone(partner_phone)
    if partner_name or partner_tokens:
        route = "inapp"
        url = None
        sms_draft = None
        print(
            f"[shared/invite] {owner_phone} → {partner_phone} (inapp) "
            f"share={share_id} registered={bool(partner_name)} push_tokens={len(partner_tokens)}"
        )
        # §30 FCM 푸시 — B 폰에 즉시 알림 (앱 꺼져 있어도)
        # §A-3 (2026-06-13) — invite 의 owner_name(상호 '디테일라인') 우선.
        owner_name_for_fcm = (
            owner_name_raw_val
            or _is_registered_owner(owner_phone)
            or "사장님"
        )
        _send_fcm_data_to_phone(partner_phone, {
            "type": "collab_invite",
            "share_id": share_id,
            "owner_name": owner_name_for_fcm,
            "title": title or "협업 현장",
        })
        return {"share_id": share_id, "route": route, "url": url, "sms_draft": sms_draft}
    # 미가입 — 기존 팀 웹뷰 패턴 재사용 권장 (Phase 1 은 단순 SMS 안내문)
    owner_name = _is_registered_owner(owner_phone) or "사장님"
    base = INTAKE_PUBLIC_BASE_URL.rstrip("/")
    url = f"{base}/shared/{share_id}"  # Phase 1: 링크 화면은 별도 (스펙 외)
    sms_draft = (
        f"{owner_name}님이 협업 현장을 공유했어요.\n"
        f"{title or '현장'} — 자세히 보기: {url}\n"
        f"(시공막내 앱 설치 시 인앱으로 받을 수 있어요)"
    )
    print(f"[shared/invite] {owner_phone} → {partner_phone} (link) share={share_id}")
    return {"share_id": share_id, "route": "link", "url": url, "sms_draft": sms_draft}


# ─── ③ GET /api/shared/with-me ───
# B 가 받은 공유 현장 목록 (B 화면 핵심)

@app.get("/api/shared/with-me")
async def shared_with_me(phone: str, since_ms: int = 0, limit: int = 50) -> dict:
    """B(partner_phone) 가 받은 공유 현장 목록. 본인 phone 만 접근 가능 (벽)."""
    partner_phone = _norm_phone(phone)
    if not partner_phone:
        raise HTTPException(400, "phone 필수")
    _touch_beta_whitelist(partner_phone)  # 추가41 (2026-06-20) — 앱 실행 heartbeat
    limit = max(1, min(limit, 200))
    with db_conn() as con:
        rows = con.execute(
            f"""
            SELECT {_SHARED_SITES_COLS}
            FROM shared_sites
            WHERE partner_phone = ? AND updated_at_ms > ?
            ORDER BY created_at_ms DESC
            LIMIT ?
            """,
            (partner_phone, since_ms, limit),
        ).fetchall()
    sites = [_shared_site_row_to_dict(r, viewer_kind="partner") for r in rows]
    return {"sites": sites}


# ─── ④ POST /api/shared/respond ───
# B 수락/거절

class SharedRespondRequest(BaseModel):
    share_id: str
    partner_phone: str
    accept: bool
    partner_name: Optional[str] = None   # §I (2026-06-18) — B 본인 상호 ('박지훈전문줄눈'). by-me 의 partner_name echo 용.


@app.post("/api/shared/respond")
async def shared_respond(req: SharedRespondRequest) -> dict:
    """B 가 수락 또는 거절. share_id + partner_phone 일치 필수 (벽).

    §H (2026-06-13): accept=true 시 A 에게 collab_event(step=accepted) FCM 발송.
        — 핸드오프 SERVER_HANDOFF_collab_expansion §H ★ "A 가 수락 알림을 못 받음" 보강.
    """
    share_id = (req.share_id or "").strip()
    partner_phone = _norm_phone(req.partner_phone)
    if not share_id or not partner_phone:
        raise HTTPException(400, "share_id, partner_phone 필수")
    now = _now_ms()
    new_status = "accepted" if req.accept else "declined"
    with db_conn() as con:
        # §H — owner_phone / title 도 같이 가져옴 (FCM 본문용)
        row = con.execute(
            "SELECT partner_phone, status, owner_phone, title FROM shared_sites WHERE share_id = ?",
            (share_id,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "share_id 없음")
        if row[0] != partner_phone:
            raise HTTPException(403, "권한 없음 (이 공유는 본인 것이 아닙니다)")
        if row[1] in ("accepted", "declined", "ended"):
            # 이미 응답한 / 종료된 share 는 재변경 차단 (단순화)
            raise HTTPException(409, f"이미 {row[1]} 상태입니다")
        owner_phone_for_fcm = row[2]
        site_title = row[3] or ""
        # §I (2026-06-18) — B 가 보낸 partner_name(상호) 박기 (있을 때만).
        partner_name_raw_val = (req.partner_name or "").strip() if hasattr(req, "partner_name") else ""
        # 추가63 (2026-06-29) — accept=true 시 accepted_at_ms = now (협업 진행 카드용).
        accepted_clause = ", accepted_at_ms = ?" if new_status == "accepted" else ""
        accepted_param = (now,) if new_status == "accepted" else ()
        if partner_name_raw_val:
            con.execute(
                f"UPDATE shared_sites SET status = ?, partner_name_raw = ?, updated_at_ms = ?{accepted_clause} WHERE share_id = ?",
                (new_status, partner_name_raw_val, now) + accepted_param + (share_id,),
            )
        else:
            con.execute(
                f"UPDATE shared_sites SET status = ?, updated_at_ms = ?{accepted_clause} WHERE share_id = ?",
                (new_status, now) + accepted_param + (share_id,),
            )
        con.commit()
    print(f"[shared/respond] share={share_id} {partner_phone} → {new_status} pn={partner_name_raw_val!r}")
    # §H (2026-06-13) FCM — A 에게 수락/거절 알림 (앱 꺼진 상태에서도).
    # 핸드오프: 기존 collab_event 재사용 → type=collab_event, step=accepted|declined.
    # 앱 측 ("🤝 협업 수락 · OOO 님이 수락했어요") 문구는 클라이언트 생성.
    # §I (2026-06-18) — B 가 보낸 상호 우선, 없으면 registered, 최종 fallback.
    partner_name_for_fcm = partner_name_raw_val or _is_registered_owner(partner_phone) or "협업 사장"
    _send_fcm_data_to_phone(owner_phone_for_fcm, {
        "type": "collab_event",
        "share_id": share_id,
        "step": new_status,             # 'accepted' or 'declined'
        "partner_name": partner_name_for_fcm,
        "title": site_title or "협업 현장",
    })
    return {"ok": True, "share_id": share_id, "status": new_status, "updated_at_ms": now}


# ─── ④-bis GET /api/shared/by-me ───  (§H 2026-06-13)
# A 가 보낸 협업 목록 — 캘린더 협업 표시("🤝 박지훈 사장님 · 함께") + 수락/거절 반영.
# 핸드오프 §H: 앱 현재 (로컬 prefs collab_assignments) 한계 = 상대 수락 여부 모름 → 서버가 정답 보냄.
# 벽: 고객 phone 미포함. share_id / partner_name / status / scheduled_at_ms / title / daily_wage 만.

@app.get("/api/shared/by-me")
async def shared_by_me(phone: str, since_ms: int = 0, limit: int = 100) -> dict:
    """A(owner_phone) 가 내보낸 공유 목록. 본인 phone 만 접근 가능 (벽).

    응답: { sites: [ { share_id, partner_phone, partner_name, status,
                       scheduled_at_ms, title, daily_wage? } ] }
    """
    owner_phone = _norm_phone(phone)
    if not owner_phone:
        raise HTTPException(400, "phone 필수")
    _touch_beta_whitelist(owner_phone)  # 추가41 (2026-06-20) — 앱 실행 heartbeat
    limit = max(1, min(limit, 300))
    with db_conn() as con:
        rows = con.execute(
            """
            SELECT share_id, partner_phone, title, scheduled_at_ms, status,
                   daily_wage, time_label_raw, owner_name_raw, partner_name_raw,
                   created_at_ms, updated_at_ms, progress,
                   accepted_at_ms, departed_at_ms, arrived_at_ms, completed_at_ms, paid_at_ms
            FROM shared_sites
            WHERE owner_phone = ? AND updated_at_ms > ?
            ORDER BY created_at_ms DESC
            LIMIT ?
            """,
            (owner_phone, since_ms, limit),
        ).fetchall()
    sites = []
    for r in rows:
        (share_id, partner_phone, title, scheduled_at_ms, status,
         daily_wage, time_label_raw, owner_name_raw, partner_name_raw,
         created_at_ms, updated_at_ms, progress,
         accepted_at_ms, departed_at_ms, arrived_at_ms, completed_at_ms, paid_at_ms) = r
        # §I (2026-06-18) — B 가 respond/progress 에 박은 상호 (partner_name_raw) 우선.
        # 없으면 registered owner, 최종 fallback "협업 사장".
        partner_name = (
            (partner_name_raw or "").strip()
            or _is_registered_owner(partner_phone)
            or "협업 사장"
        )
        # §A-2 time_label (raw 우선, 없으면 HH:MM)
        tl = (time_label_raw or "").strip()
        if not tl and scheduled_at_ms:
            try:
                tl = _dt.datetime.fromtimestamp(scheduled_at_ms / 1000).strftime("%H:%M")
            except Exception:
                tl = ""
        # §A-3 owner_name (raw 우선, 없으면 registered owner, 최종 fallback "사장님")
        on = (owner_name_raw or "").strip() or _is_registered_owner(owner_phone) or "사장님"
        item = {
            "share_id": share_id,
            "partner_phone": partner_phone,    # A 가 본인 보낸 목록 — 노출 OK (B phone)
            "partner_name": partner_name,
            "owner_name": on,                  # §A-3 — by-me 도 echo (사장님 명시 요청)
            "status": status,                  # pending | accepted | declined
            "progress": progress,              # 추가63 — 협업 진행 카드용
            "scheduled_at_ms": scheduled_at_ms or 0,
            "time_label": tl,
            "title": title or "",
            "created_at_ms": created_at_ms,
            "updated_at_ms": updated_at_ms,
        }
        if daily_wage is not None:
            try:
                item["daily_wage"] = int(daily_wage)
            except Exception:
                pass
        # 추가63 (2026-06-29) — 전환 시각 (값 있을 때만)
        if accepted_at_ms:
            item["accepted_at_ms"] = accepted_at_ms
        if departed_at_ms:
            item["departed_at_ms"] = departed_at_ms
        if arrived_at_ms:
            item["arrived_at_ms"] = arrived_at_ms
        if completed_at_ms:
            item["completed_at_ms"] = completed_at_ms
        if paid_at_ms:
            item["paid_at_ms"] = paid_at_ms
        sites.append(item)
    return {"sites": sites}


# ─── ⑤ POST /api/shared/progress ───
# B 출발/도착/완료. completed 시 계좌 payload 받음 (A 에게 푸시 위해 저장).

class SharedProgressPayload(BaseModel):
    bank: Optional[str] = None
    account_no: Optional[str] = None
    holder: Optional[str] = None


class SharedProgressRequest(BaseModel):
    share_id: str
    partner_phone: str
    step: str                                      # 'departed'|'arrived'|'completed'
    payload: Optional[SharedProgressPayload] = None
    auto: Optional[bool] = False                   # §E (2026-06-13) — geofence 자동 감지 (arrived 만 의미 있음)
    partner_name: Optional[str] = None             # §I (2026-06-18) — B 본인 상호 (by-me 의 partner_name echo 용)


_VALID_PROGRESS_STEPS = {"departed", "arrived", "completed"}


@app.post("/api/shared/progress")
async def shared_progress(req: SharedProgressRequest) -> dict:
    """B 진행 업데이트. share_id + partner_phone 권한 필수.

    step=completed 시 payload.bank/account_no/holder 를 shared_sites 에 저장 (A 입금용).
    departed/arrived 는 payload 없음.
    """
    share_id = (req.share_id or "").strip()
    partner_phone = _norm_phone(req.partner_phone)
    step = (req.step or "").strip()
    if not share_id or not partner_phone:
        raise HTTPException(400, "share_id, partner_phone 필수")
    if step not in _VALID_PROGRESS_STEPS:
        raise HTTPException(400, f"step must be one of {_VALID_PROGRESS_STEPS}")
    now = _now_ms()
    with db_conn() as con:
        # 추가44 (2026-06-21) — progress 도 SELECT (되돌리기 판정용)
        row = con.execute(
            "SELECT partner_phone, status, owner_phone, title, progress FROM shared_sites WHERE share_id = ?",
            (share_id,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "share_id 없음")
        if row[0] != partner_phone:
            raise HTTPException(403, "권한 없음")
        if row[1] != "accepted":
            raise HTTPException(409, "수락된 공유만 진행 가능")
        owner_phone_for_event = row[2]
        site_title = row[3] or ""
        current_progress = (row[4] or "assigned")
        # 추가44 (2026-06-21) — '완료 되돌리기' = 현재 progress=completed + 새 step=arrived
        # 사장님 보고: 앱에 완료→도착 되돌리기 버튼. 정산 reset + 불필요한 재알림 X.
        is_revert = (step == "arrived" and current_progress == "completed")
        # §I (2026-06-18) — B 가 보낸 partner_name 박기 (있을 때만, partial UPDATE).
        partner_name_raw_val = (req.partner_name or "").strip() if hasattr(req, "partner_name") else ""
        # 1) shared_sites 업데이트
        event_bank = None
        event_account_no = None
        event_holder = None
        # 추가63 (2026-06-29) — step 별 전환 시각 컬럼 매핑. 협업 진행 카드용.
        # 한 step 시점에 그 컬럼만 박음 (다른 시각 컬럼은 그대로 유지).
        _STEP_TS_COL = {"departed": "departed_at_ms", "arrived": "arrived_at_ms", "completed": "completed_at_ms"}
        step_ts_col = _STEP_TS_COL.get(step)
        if step == "completed":
            payload = req.payload or SharedProgressPayload()
            bank = (payload.bank or "").strip()[:30]
            account_no = (payload.account_no or "").strip()[:30]
            holder = (payload.holder or "").strip()[:30]
            event_bank = bank or None
            event_account_no = account_no or None
            event_holder = holder or None
            if partner_name_raw_val:
                con.execute(
                    """
                    UPDATE shared_sites SET
                      progress = ?, account_bank = ?, account_no = ?, account_holder = ?,
                      partner_name_raw = ?, updated_at_ms = ?, completed_at_ms = ?
                    WHERE share_id = ?
                    """,
                    (step, event_bank, event_account_no, event_holder, partner_name_raw_val, now, now, share_id),
                )
            else:
                con.execute(
                    """
                    UPDATE shared_sites SET
                      progress = ?, account_bank = ?, account_no = ?, account_holder = ?,
                      updated_at_ms = ?, completed_at_ms = ?
                    WHERE share_id = ?
                    """,
                    (step, event_bank, event_account_no, event_holder, now, now, share_id),
                )
        elif is_revert:
            # 추가44 (2026-06-21) — 완료 되돌리기. progress='arrived' + account_* NULL reset.
            # paid_at_ms 는 보존 (사장님 별도 액션). FCM 안 보냄 (사장님 요청: 불필요 재알림 X).
            # 추가63 — 완료 되돌리기 시 completed_at_ms 도 NULL reset (다시 완료 시 새 시각 박힘).
            if partner_name_raw_val:
                con.execute(
                    """
                    UPDATE shared_sites SET
                      progress = 'arrived', account_bank = NULL, account_no = NULL, account_holder = NULL,
                      completed_at_ms = NULL,
                      partner_name_raw = ?, updated_at_ms = ?
                    WHERE share_id = ?
                    """,
                    (partner_name_raw_val, now, share_id),
                )
            else:
                con.execute(
                    """
                    UPDATE shared_sites SET
                      progress = 'arrived', account_bank = NULL, account_no = NULL, account_holder = NULL,
                      completed_at_ms = NULL,
                      updated_at_ms = ?
                    WHERE share_id = ?
                    """,
                    (now, share_id),
                )
        else:
            # 추가63 — step 별 시각 컬럼 동적 추가 (departed_at_ms / arrived_at_ms).
            ts_clause = f", {step_ts_col} = ?" if step_ts_col else ""
            ts_param = (now,) if step_ts_col else ()
            if partner_name_raw_val:
                con.execute(
                    f"UPDATE shared_sites SET progress = ?, partner_name_raw = ?, updated_at_ms = ?{ts_clause} WHERE share_id = ?",
                    (step, partner_name_raw_val, now) + ts_param + (share_id,),
                )
            else:
                con.execute(
                    f"UPDATE shared_sites SET progress = ?, updated_at_ms = ?{ts_clause} WHERE share_id = ?",
                    (step, now) + ts_param + (share_id,),
                )
        # 2) §28 — A 앞으로 이벤트 적재 (TeamEventCenter 패턴 폴링용)
        event_id = "evt_" + "".join(
            _secrets_collab.choice(_SHARE_ID_ALPHABET) for _ in range(10)
        )
        con.execute(
            """
            INSERT INTO shared_owner_events
                (event_id, share_id, owner_phone, partner_phone, step, title,
                 account_bank, account_no, account_holder, created_at_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                event_id,
                share_id,
                owner_phone_for_event,
                partner_phone,
                step,
                site_title or None,
                event_bank,
                event_account_no,
                event_holder,
                now,
            ),
        )
        con.commit()
    is_auto = bool(req.auto) and step == "arrived"   # §E — geofence 자동
    print(
        f"[shared/progress] share={share_id} {partner_phone} → {step} "
        f"(event={event_id}{', auto' if is_auto else ''})"
    )
    # §30 FCM 푸시 — A 에게 진행/완료 알림 (앱 꺼져 있어도)
    # §E (2026-06-13): step=arrived & auto=true 시 앱이 "거의 도착해가요" 문구로 표시.
    # §I (2026-06-18) — B 가 보낸 상호 우선, 없으면 registered, 최종 fallback.
    partner_name = partner_name_raw_val or _is_registered_owner(partner_phone) or "협업 사장"
    fcm_data: dict = {
        "type": "collab_event",
        "share_id": share_id,
        "event_id": event_id,
        "step": step,
        "partner_name": partner_name,
        "title": site_title or "협업 현장",
    }
    if is_auto:
        fcm_data["auto"] = "true"   # FCM data 는 string. 앱이 분기.
    if step == "completed" and (event_bank or event_account_no):
        fcm_data["bank"] = event_bank or ""
        fcm_data["account_no"] = event_account_no or ""
        fcm_data["holder"] = event_holder or ""
    # 추가44 (2026-06-21) — 되돌리기는 FCM 안 보냄 (사장님 요청: 불필요 재알림 X).
    # A 앱이 owner-events 폴링으로 알아챔 (가장 최근 step=arrived 이벤트).
    if is_revert:
        fcm_data["revert"] = "true"   # 응답에 표식 (앱이 알게 — 푸시는 안 보냄)
        print(f"[shared/progress] share={share_id} REVERT (completed→arrived). FCM skip.")
    else:
        _send_fcm_data_to_phone(owner_phone_for_event, fcm_data)
    # §E (2026-06-13) — auto arrived 시 B 에게 "사장님께 알려드렸어요" 확인 FCM 별도 발송.
    # 핸드오프 SERVER_HANDOFF_collab_expansion §E: b-remind 아래 푸시.
    if is_auto:
        _send_fcm_data_to_phone(partner_phone, {
            "type": "collab_arrived_confirm",
            "share_id": share_id,
            "title": site_title or "협업 현장",
        })
    return {"ok": True, "share_id": share_id, "progress": step, "event_id": event_id, "updated_at_ms": now}


# ─── ⑥ POST /api/shared/paid ───
# A 가 입금 완료 표시 → B 에게 푸시 신호

class SharedPaidRequest(BaseModel):
    share_id: str
    owner_phone: str


@app.post("/api/shared/paid")
async def shared_paid(req: SharedPaidRequest) -> dict:
    """A 가 입금 완료 표시. share_id + owner_phone 권한 필수."""
    share_id = (req.share_id or "").strip()
    owner_phone = _norm_phone(req.owner_phone)
    if not share_id or not owner_phone:
        raise HTTPException(400, "share_id, owner_phone 필수")
    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            "SELECT owner_phone, progress FROM shared_sites WHERE share_id = ?",
            (share_id,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "share_id 없음")
        if row[0] != owner_phone:
            raise HTTPException(403, "권한 없음")
        if row[1] != "completed":
            raise HTTPException(409, "완료된 공유만 입금 표시 가능")
        con.execute(
            "UPDATE shared_sites SET paid_at_ms = ?, updated_at_ms = ? WHERE share_id = ?",
            (now, now, share_id),
        )
        con.commit()
    print(f"[shared/paid] share={share_id} {owner_phone} → paid")
    # §30 FCM 푸시 — B 에게 입금 완료 알림
    with db_conn() as _con:
        prow = _con.execute(
            "SELECT partner_phone, title FROM shared_sites WHERE share_id = ?",
            (share_id,),
        ).fetchone()
    if prow and prow[0]:
        _send_fcm_data_to_phone(prow[0], {
            "type": "collab_paid",
            "share_id": share_id,
            "title": prow[1] or "협업 현장",
            "paid_at_ms": now,
        })
    return {"ok": True, "share_id": share_id, "paid_at_ms": now}


# ─── ⑦ GET /api/shared/owner-events ───
# A(현장 주인) 폴링 — B 의 진행(departed/arrived/completed) 이벤트 받음.
# TeamEventCenter 패턴. since_ms 이후만, 최신순.
# 벽: 고객 phone 절대 X. title + partner_name 만 노출.

@app.get("/api/shared/owner-events")
async def shared_owner_events(
    phone: str, since_ms: int = 0, limit: int = 50
) -> dict:
    """A 가 본인 협업 현장에 대한 진행 이벤트 폴링."""
    owner_phone = _norm_phone(phone)
    if not owner_phone:
        raise HTTPException(400, "phone 필수")
    _touch_beta_whitelist(owner_phone)  # 추가41 (2026-06-20) — 앱 실행 heartbeat
    limit = max(1, min(limit, 200))
    with db_conn() as con:
        # §A (2026-06-13) JOIN shared_sites 로 daily_wage echo.
        rows = con.execute(
            """
            -- 추가39 (2026-06-20) — declined/ended share 의 옛 progress 이벤트 (departed/arrived) 제외.
            -- 안드로이드 보고: "B 가 거절했는데 A 한테 출발(departed) 푸시 감". 원인 = 옛 departed
            -- 이벤트가 share status='declined'/'ended' 가 된 뒤에도 폴링에 다시 return 됨.
            -- labor 흐름 (share_id 자리에 token 박힌 이벤트) 은 JOIN 매치 X → s.status IS NULL 통과.
            SELECT e.event_id, e.share_id, e.partner_phone, e.step, e.title,
                   e.account_bank, e.account_no, e.account_holder, e.created_at_ms,
                   s.daily_wage
            FROM shared_owner_events e
            LEFT JOIN shared_sites s ON s.share_id = e.share_id
            WHERE e.owner_phone = ? AND e.created_at_ms > ?
              AND (s.status IS NULL OR s.status NOT IN ('declined', 'ended'))
            ORDER BY e.created_at_ms DESC
            LIMIT ?
            """,
            (owner_phone, since_ms, limit),
        ).fetchall()
    events = []
    for r in rows:
        partner_phone = r[2]
        # 벽: 고객 phone 노출 X. partner_name 으로만.
        partner_name = _is_registered_owner(partner_phone) or "협업 사장"
        step = r[3]
        evt = {
            "event_id": r[0],
            "share_id": r[1],
            "title": r[4] or "",
            "partner_name": partner_name,
            "step": step,
            "at_ms": r[8],
        }
        # completed 일 때만 계좌 포함 (A 가 입금용)
        if step == "completed" and (r[5] or r[6] or r[7]):
            evt["account"] = {
                "bank": r[5] or "",
                "account_no": r[6] or "",
                "holder": r[7] or "",
            }
        # §A 일당 echo — 값 있을 때만 (앱 graceful).
        if r[9] is not None:
            try:
                evt["daily_wage"] = int(r[9])
            except Exception:
                pass
        events.append(evt)
    return {"events": events}


# ─── ⑧ GET /api/shared/partners ───  (§B 2026-06-13)
# B 가 받은 협업들 → owner_phone(A) 별 집계.
# 핸드오프 §B (SERVER_HANDOFF_collab_expansion): "업체별 히스토리 + 누적 수입".
#
# 응답: { partners: [{ owner_phone, owner_name, count, total_wage, paid_total, last_at_ms }] }
#   count       = B 와 그 A 가 한 협업 횟수 (모든 status)
#   total_wage  = 완료(progress=completed) 된 협업의 daily_wage 합 (만원)
#   paid_total  = A 가 markPaid 한(=paid_at_ms 있는) 협업의 daily_wage 합 (만원)
#   last_at_ms  = 가장 최근 협업 created_at_ms
#
# 벽: A 의 고객 phone/대화/매출 미포함. B 와 A 간 일당만 노출. owner_phone 은 본인이 받은
# 협업의 사장 번호라 노출 OK (B 가 어차피 알던 번호).

@app.get("/api/shared/partners")
async def shared_partners(phone: str, limit: int = 100) -> dict:
    """B(partner_phone) 가 받은 협업들의 A(owner_phone) 별 집계."""
    partner_phone = _norm_phone(phone)
    if not partner_phone:
        raise HTTPException(400, "phone 필수")
    limit = max(1, min(limit, 300))
    with db_conn() as con:
        rows = con.execute(
            """
            SELECT
              owner_phone,
              COUNT(*)                                                                AS cnt,
              COALESCE(SUM(CASE WHEN progress = 'completed' THEN COALESCE(daily_wage, 0) ELSE 0 END), 0) AS total_wage,
              COALESCE(SUM(CASE WHEN paid_at_ms IS NOT NULL THEN COALESCE(daily_wage, 0) ELSE 0 END), 0) AS paid_total,
              MAX(created_at_ms)                                                      AS last_at_ms
            FROM shared_sites
            WHERE partner_phone = ?
            GROUP BY owner_phone
            ORDER BY last_at_ms DESC
            LIMIT ?
            """,
            (partner_phone, limit),
        ).fetchall()
    partners = []
    for r in rows:
        owner_phone, cnt, total_wage, paid_total, last_at_ms = r
        partners.append({
            "owner_phone": owner_phone,
            "owner_name": _is_registered_owner(owner_phone) or "사장님",
            "count": int(cnt or 0),
            "total_wage": int(total_wage or 0),    # 만원 단위
            "paid_total": int(paid_total or 0),    # 만원 단위
            "last_at_ms": int(last_at_ms or 0),
        })
    return {"partners": partners}


# ─── ⑨ GET /api/shared/history ───  (§B 2026-06-13)
# B 가 특정 A 와 한 현장 내역.
# 핸드오프 §B: "그 업체와 한 현장 내역" — b-biz 화면용.
#
# 응답: { sites: [{ share_id, title, scheduled_at_ms, daily_wage, paid }] }
#   paid = paid_at_ms IS NOT NULL (boolean)
#
# 벽: A 의 고객 phone/대화/타 현장 미포함. B 본인이 참여한 협업만.

@app.get("/api/shared/history")
async def shared_history(phone: str, owner_phone: str, limit: int = 200) -> dict:
    """B(partner_phone) 가 특정 A(owner_phone) 와 한 협업 현장 내역."""
    partner_digits = _norm_phone(phone)
    owner_digits = _norm_phone(owner_phone)
    if not partner_digits:
        raise HTTPException(400, "phone 필수")
    if not owner_digits:
        raise HTTPException(400, "owner_phone 필수")
    limit = max(1, min(limit, 500))
    with db_conn() as con:
        rows = con.execute(
            """
            SELECT share_id, title, scheduled_at_ms, daily_wage, paid_at_ms,
                   status, progress, created_at_ms
            FROM shared_sites
            WHERE partner_phone = ? AND owner_phone = ?
            ORDER BY COALESCE(scheduled_at_ms, created_at_ms) DESC, created_at_ms DESC
            LIMIT ?
            """,
            (partner_digits, owner_digits, limit),
        ).fetchall()
    sites = []
    for r in rows:
        share_id, title, scheduled_at_ms, daily_wage, paid_at_ms, status, progress, created_at_ms = r
        item = {
            "share_id": share_id,
            "title": title or "",
            "scheduled_at_ms": int(scheduled_at_ms or 0),
            "paid": bool(paid_at_ms),
            "status": status,
            "progress": progress,
            "created_at_ms": int(created_at_ms or 0),
        }
        if daily_wage is not None:
            try:
                item["daily_wage"] = int(daily_wage)
            except Exception:
                pass
        sites.append(item)
    return {"sites": sites}


# ─── ⑩ POST /api/shared/photo ───  (§F-2 2026-06-13)
# 협업 현장 증거사진 업로드 — 핸드오프 SERVER_HANDOFF_collab_expansion §F + 사장님 워딩 그대로.
# body: { share_id, partner_phone (= 업로더 phone), image_base64, label? }
# 벽: share_id 의 owner_phone 또는 partner_phone 중 하나가 업로더 phone 과 일치.
# 저장: team_site_photos 재사용 (§F share_id 컬럼). 영구 보존 (§C).

class SharedPhotoUploadRequest(BaseModel):
    share_id: str
    partner_phone: str                      # 업로더 phone (owner 든 partner 든)
    image_base64: str                       # base64 (data URL 또는 raw base64)
    label: Optional[str] = None             # '시공 전'|'시공 중'|'시공 후'|'추가 사진'
    note: Optional[str] = None


@app.post("/api/shared/photo")
async def shared_photo_upload(req: SharedPhotoUploadRequest) -> dict:
    """협업 현장 사진 업로드. share_id 의 owner/partner 만 가능."""
    share_id = (req.share_id or "").strip()
    uploader_phone = _norm_phone(req.partner_phone)
    if not share_id or not uploader_phone:
        raise HTTPException(400, "share_id, partner_phone 필수")
    image_b64 = (req.image_base64 or "").strip()
    if not image_b64:
        raise HTTPException(400, "image_base64 필수")
    # data URL 이 아니면 prefix 붙임 (기존 site_photos 호환)
    if not image_b64.startswith("data:"):
        image_data_url = f"data:image/jpeg;base64,{image_b64}"
    else:
        image_data_url = image_b64
    if len(image_data_url) > 1_400_000:  # 약 1MB
        raise HTTPException(413, "사진 용량 초과 (1MB 이하만)")
    with db_conn() as _con:
        _row = _con.execute(
            "SELECT owner_phone, partner_phone, title FROM shared_sites WHERE share_id = ?",
            (share_id,),
        ).fetchone()
    if not _row:
        raise HTTPException(404, "share_id 없음")
    share_owner = _norm_phone(_row[0])
    share_partner = _norm_phone(_row[1])
    site_title = _row[2] or "협업 현장"  # 추가74c — FCM 본문용
    if uploader_phone not in (share_owner, share_partner):
        raise HTTPException(403, "권한 없음 (이 협업 현장의 owner/partner 만 업로드 가능)")
    # 업로더 종류 결정 — owner 이면 member_id='OWNER', partner 이면 'PARTNER:{phone}' (구분용)
    if uploader_phone == share_owner:
        member_id = "OWNER"
        store_owner_phone = share_owner
    else:
        member_id = f"PARTNER:{uploader_phone}"
        store_owner_phone = share_owner  # owner_phone 컬럼은 share 의 주인(A) 으로 저장 — A 가 보존 권한
    label = (req.label or "").strip() or "현장 사진"
    now = _now_ms()
    with db_conn() as con:
        cur = con.execute(
            """
            INSERT INTO team_site_photos
                (token, member_id, owner_phone, label, image_data_url, image_path,
                 note, uploaded_at_ms, customer_phone, share_id)
            VALUES (NULL, ?, ?, ?, ?, NULL, ?, ?, NULL, ?)
            """,
            (
                member_id,
                store_owner_phone,
                label,
                image_data_url,
                (req.note or "").strip() or None,
                now,
                share_id,
            ),
        )
        photo_id = cur.lastrowid
        con.commit()
    print(
        f"[shared/photo] share={share_id} uploader={uploader_phone} "
        f"({'OWNER' if uploader_phone == share_owner else 'PARTNER'}) "
        f"photo_id={photo_id} label={label}"
    )

    # 추가74c (2026-06-29) — 상대 참여자에게 FCM data 푸시 (collab_photo).
    # 댓글 (collab_comment) 과 동일 패턴. type 만 다름.
    target_phone = share_partner if uploader_phone == share_owner else share_owner
    if target_phone:
        try:
            uploader_name = _is_registered_owner(uploader_phone) or "협업 사장"
            _send_fcm_data_to_phone(target_phone, {
                "type": "collab_photo",
                "site_id": share_id,
                "title": site_title,
                "uploader_name": uploader_name,
            })
        except Exception as e:
            print(f"[shared/photo] FCM 발송 실패 (무시): {type(e).__name__}: {e}")

    return {
        "ok": True,
        "photo_id": photo_id,
        "share_id": share_id,
        "label": label,
        "uploaded_at_ms": now,
    }


# ─── ⑪ GET /api/shared/photos ───  (§F-2 2026-06-13)
# 협업 현장 사진 목록 — share_id 의 모든 사진 (owner 업로드 + partner 업로드 둘 다).
# 사장님 워딩 그대로 ?share_id= 만. 안전벽: phone 옵셔널 query (있으면 검증).

@app.get("/api/shared/photos")
async def shared_photos_list(
    share_id: str,
    phone: Optional[str] = None,
    since_ms: int = 0,
    limit: int = 100,
) -> dict:
    """협업 현장 사진 조회. phone 제공 시 owner/partner 권한 검증."""
    share_id = (share_id or "").strip()
    if not share_id:
        raise HTTPException(400, "share_id 필수")
    limit = max(1, min(limit, 300))
    if phone:
        phone_digits = _norm_phone(phone)
        with db_conn() as _con:
            _row = _con.execute(
                "SELECT owner_phone, partner_phone FROM shared_sites WHERE share_id = ?",
                (share_id,),
            ).fetchone()
        if not _row:
            raise HTTPException(404, "share_id 없음")
        if phone_digits not in (_norm_phone(_row[0]), _norm_phone(_row[1])):
            raise HTTPException(403, "권한 없음")
    with db_conn() as con:
        rows = con.execute(
            """
            SELECT photo_id, member_id, label, image_data_url, note, uploaded_at_ms
            FROM team_site_photos
            WHERE share_id = ? AND uploaded_at_ms > ?
            ORDER BY uploaded_at_ms DESC
            LIMIT ?
            """,
            (share_id, since_ms, limit),
        ).fetchall()
    photos = []
    for r in rows:
        photo_id, member_id, label, image_data_url, note, uploaded_at_ms = r
        mid = member_id or ""
        if mid == "OWNER":
            uploader_kind = "owner"
            uploader_name = "사장님"
        elif mid.startswith("PARTNER:"):
            uploader_kind = "partner"
            uploader_phone_part = mid.split(":", 1)[1]
            uploader_name = _is_registered_owner(uploader_phone_part) or "협업 사장"
        else:
            uploader_kind = "member"
            uploader_name = "팀원"
        photos.append({
            "photo_id": photo_id,
            "label": label or "",
            "image_data_url": image_data_url or "",
            "note": note or "",
            "uploaded_at_ms": uploaded_at_ms,
            "uploader_kind": uploader_kind,
            "uploader_name": uploader_name,
        })
    return {"photos": photos, "count": len(photos)}


# ─── ⑪-bis POST /api/shared/photo/delete ───  (2026-07-07 사장님 — 개별 삭제)
# 협업 증거사진 삭제. 올린 본인만. (앱은 내가 올린 사진에만 ✕ 노출)
# 핸드오프: docs/SERVER_HANDOFF_collab_photo_delete.md 그대로.

class SharedPhotoDeleteRequest(BaseModel):
    share_id: str
    photo_id: int
    partner_phone: str            # 요청자(=업로더) phone


@app.post("/api/shared/photo/delete")
async def shared_photo_delete(req: SharedPhotoDeleteRequest) -> dict:
    share_id = (req.share_id or "").strip()
    requester = _norm_phone(req.partner_phone)
    if not share_id or not req.photo_id or not requester:
        raise HTTPException(400, "share_id, photo_id, partner_phone 필수")
    with db_conn() as con:
        srow = con.execute(
            "SELECT owner_phone, partner_phone FROM shared_sites WHERE share_id = ?",
            (share_id,),
        ).fetchone()
        if not srow:
            raise HTTPException(404, "share_id 없음")
        share_owner = _norm_phone(srow[0])
        prow = con.execute(
            "SELECT member_id FROM team_site_photos WHERE photo_id = ? AND share_id = ?",
            (req.photo_id, share_id),
        ).fetchone()
        if not prow:
            raise HTTPException(404, "photo 없음")
        member_id = prow[0] or ""
        # 업로더 판정
        if member_id == "OWNER":
            uploader = share_owner
        elif member_id.startswith("PARTNER:"):
            uploader = _norm_phone(member_id.split(":", 1)[1])
        else:
            uploader = ""
        if requester != uploader:
            raise HTTPException(403, "권한 없음 (올린 본인만 삭제 가능)")
        con.execute(
            "DELETE FROM team_site_photos WHERE photo_id = ? AND share_id = ?",
            (req.photo_id, share_id),
        )
        con.commit()
    print(f"[shared/photo/delete] share={share_id} photo_id={req.photo_id} by={requester}")
    return {"ok": True}


# ─── ⑫ POST /api/shared/cancel ───  (§dedup 2026-06-13)
# A 가 보낸 협업 요청 취소 (B 가 아직 응답 안 한 pending 만).
# 핸드오프 SYNC android 추가2: "(선택) 요청 취소 /api/shared/cancel → 상대 pending 제거"

class SharedCancelRequest(BaseModel):
    share_id: str
    owner_phone: str


@app.post("/api/shared/cancel")
async def shared_cancel(req: SharedCancelRequest) -> dict:
    """A 본인이 보낸 협업 요청 취소. pending 만 가능. 'declined' 로 변경 (보존, B 측 안 보이게)."""
    share_id = (req.share_id or "").strip()
    owner_phone = _norm_phone(req.owner_phone)
    if not share_id or not owner_phone:
        raise HTTPException(400, "share_id, owner_phone 필수")
    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            "SELECT owner_phone, status FROM shared_sites WHERE share_id = ?",
            (share_id,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "share_id 없음")
        if row[0] != owner_phone:
            raise HTTPException(403, "권한 없음 (이 요청은 본인이 보낸 것이 아닙니다)")
        if row[1] != "pending":
            raise HTTPException(409, f"취소 불가 (현재 status={row[1]}, pending 만 취소 가능)")
        con.execute(
            "UPDATE shared_sites SET status = 'declined', updated_at_ms = ? WHERE share_id = ?",
            (now, share_id),
        )
        con.commit()
    print(f"[shared/cancel] share={share_id} owner={owner_phone} → declined (취소)")
    return {"ok": True, "share_id": share_id, "status": "declined", "updated_at_ms": now}


# ─── ⑬ POST /api/shared/end ───  (2026-06-13)
# 협업 해제 — owner 또는 partner 둘 다 호출 가능, pending + accepted 둘 다 처리.
# 사장님 워딩: "협업 해제 / 그만하기 / 마음 안 맞아 돌려보내기".
# status='ended' (declined 와 구분 — 거절이 아니라 종료).
# 기록(사진·메모) 보존 (§C). row 삭제 X.
# 상대에게 FCM collab_ended (by_name = 끝낸 사람 이름).

class SharedEndRequest(BaseModel):
    share_id: str
    phone: str                              # 호출자 phone
    by: str                                 # 'owner' or 'partner'


@app.post("/api/shared/end")
async def shared_end(req: SharedEndRequest) -> dict:
    """협업 해제. owner 또는 partner 본인 권한. pending+accepted 모두 처리."""
    share_id = (req.share_id or "").strip()
    caller_phone = _norm_phone(req.phone)
    by = (req.by or "").strip().lower()
    if not share_id or not caller_phone:
        raise HTTPException(400, "share_id, phone 필수")
    if by not in ("owner", "partner"):
        raise HTTPException(400, "by must be 'owner' or 'partner'")
    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            """
            SELECT owner_phone, partner_phone, status, title, owner_name_raw
            FROM shared_sites WHERE share_id = ?
            """,
            (share_id,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "share_id 없음")
        owner_phone, partner_phone, status, title, owner_name_raw = row
        # 권한 검증
        if by == "owner":
            if _norm_phone(owner_phone) != caller_phone:
                raise HTTPException(403, "권한 없음 (owner 본인이 아닙니다)")
            other_phone = partner_phone
        else:  # partner
            if _norm_phone(partner_phone) != caller_phone:
                raise HTTPException(403, "권한 없음 (partner 본인이 아닙니다)")
            other_phone = owner_phone
        # 이미 종료된 share 차단
        if status in ("ended", "declined"):
            raise HTTPException(409, f"이미 종료된 협업입니다 (status={status})")
        if status not in ("pending", "accepted"):
            raise HTTPException(409, f"해제 불가 (status={status})")
        con.execute(
            "UPDATE shared_sites SET status = 'ended', updated_at_ms = ? WHERE share_id = ?",
            (now, share_id),
        )
        con.commit()
    print(f"[shared/end] share={share_id} by={by} caller={caller_phone} → ended")
    # FCM collab_ended → 상대에게. by_name = 끝낸 사람 이름.
    if by == "owner":
        by_name = (owner_name_raw or "").strip() or _is_registered_owner(owner_phone) or "사장님"
    else:
        by_name = _is_registered_owner(partner_phone) or "협업 사장"
    _send_fcm_data_to_phone(other_phone, {
        "type": "collab_ended",
        "share_id": share_id,
        "title": title or "협업 현장",
        "by_name": by_name,
        "by": by,
    })
    return {"ok": True, "share_id": share_id, "status": "ended", "updated_at_ms": now}


# ============================================================================
# 추가74 (2026-06-29) — 협업 현장 "한 줄 댓글" (안드로이드 요청)
# ─────────────────────────────────────────────────────────────────────────────
# A(owner) + B(partner) 가 공유 현장 상세에서 나누는 채팅 스레드.
# 접근 제어: 그 site_id 의 owner_phone / partner_phone 만.
# ⚠️ 화이트리스트 게이트 X — 협업은 본질적으로 미가입 partner 도 허용.
#     안드로이드 지적: /api/shared/invite 403 계열 실수 주의.
# ============================================================================


class SharedCommentPostRequest(BaseModel):
    site_id: str
    author_phone: str
    author_name: Optional[str] = None
    body: str


@app.post("/api/shared/comment")
async def shared_comment_post(req: SharedCommentPostRequest) -> dict:
    """댓글 작성. site_id 참여자 (owner or partner) 만 가능.

    추가74b (2026-06-29) — 저장 성공 후 상대 참여자에게 FCM data 푸시.
    """
    site_id = (req.site_id or "").strip()
    author_phone = _norm_phone(req.author_phone)
    author_name = (req.author_name or "").strip()[:60] or None
    body = (req.body or "").strip()
    if not site_id:
        raise HTTPException(400, "site_id 필수")
    if not author_phone:
        raise HTTPException(400, "author_phone 필수")
    if not body:
        raise HTTPException(400, "body 는 공백 불가")
    if len(body) > 1000:
        raise HTTPException(413, "body 는 최대 1000자")
    with db_conn() as con:
        # 추가74b — title 도 같이 (FCM 본문에 넣기 위해)
        row = con.execute(
            "SELECT owner_phone, partner_phone, title FROM shared_sites WHERE share_id = ?",
            (site_id,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "site_id 없음")
        owner_p = _norm_phone(row[0])
        partner_p = _norm_phone(row[1])
        site_title = row[2] or "협업 현장"
        # 접근 제어: owner 또는 partner 만. 화이트리스트 검증 X (미가입 partner OK).
        if author_phone != owner_p and author_phone != partner_p:
            raise HTTPException(403, "이 현장의 참여자만 댓글 작성 가능")
        now = _now_ms()
        cur = con.execute(
            """
            INSERT INTO shared_comments (site_id, author_phone, author_name, body, created_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            (site_id, author_phone, author_name, body, now),
        )
        comment_id = cur.lastrowid
        con.commit()
    print(f"[shared/comment/post] site={site_id} author={author_phone} id={comment_id}")

    # 추가74b (2026-06-29) — 상대 참여자에게 FCM data 푸시 (data-only, 문자열).
    # 작성자 = owner 면 target = partner, 반대도. 실패해도 응답 영향 X (폴링 안전망).
    target_phone = partner_p if author_phone == owner_p else owner_p
    if target_phone:
        try:
            body_cut = body[:60] + ("…" if len(body) > 60 else "")
            _send_fcm_data_to_phone(target_phone, {
                "type": "collab_comment",
                "site_id": site_id,
                "title": site_title,
                "author_name": author_name or "",
                "author_phone": author_phone,
                "body": body_cut,
            })
        except Exception as e:
            print(f"[shared/comment/post] FCM 발송 실패 (무시): {type(e).__name__}: {e}")

    return {"ok": True, "comment_id": comment_id, "created_at": now}


@app.get("/api/shared/comments")
async def shared_comments_list(site_id: str, phone: str) -> dict:
    """댓글 목록. site_id 참여자만. 오래된→최신 순 (앱이 채팅처럼 아래로).

    phone = 요청자 phone (접근 검증용. 화이트리스트 검증 X).
    """
    site_id = (site_id or "").strip()
    caller_phone = _norm_phone(phone)
    if not site_id:
        raise HTTPException(400, "site_id 필수")
    if not caller_phone:
        raise HTTPException(400, "phone 필수")
    with db_conn() as con:
        row = con.execute(
            "SELECT owner_phone, partner_phone FROM shared_sites WHERE share_id = ?",
            (site_id,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "site_id 없음")
        owner_p = _norm_phone(row[0])
        partner_p = _norm_phone(row[1])
        if caller_phone != owner_p and caller_phone != partner_p:
            raise HTTPException(403, "이 현장의 참여자만 조회 가능")
        rows = con.execute(
            """
            SELECT id, author_phone, author_name, body, created_at
            FROM shared_comments
            WHERE site_id = ?
            ORDER BY created_at ASC
            """,
            (site_id,),
        ).fetchall()
    comments = [
        {
            "id": r[0],
            "author_phone": r[1],
            "author_name": r[2] or "",
            "body": r[3],
            "created_at": r[4],
        }
        for r in rows
    ]
    return {"comments": comments}


# ─── §D — 출동 2h 전 자동 알림 (uvicorn startup background task) ───
# 핸드오프 SERVER_HANDOFF_collab_expansion §D: "확정된 협업 현장의 scheduled_at_ms − 2h 시점에
# B 에게 FCM type=collab_remind." 서버 크론 (uvicorn 내부 폴링) 선택 — 사장님 launchd plist
# 변경 0, uvicorn 살아있는 동안 자동 동작.
#
# 동작:
#   - 매 60초마다 polling.
#   - status='accepted' + scheduled_at_ms 가 [now, now + 2h] 안 + reminded_at_ms IS NULL
#     + paid_at_ms IS NULL + progress != 'completed' 인 share 검색.
#   - 각 share 에 대해 collab_remind FCM 발사 → B 폰 (partner_phone).
#   - UPDATE reminded_at_ms = now WHERE share_id=? AND reminded_at_ms IS NULL → race 차단.
#   - 발사 후 [shared/remind] stdout 로그.
#
# dedup: reminded_at_ms IS NULL 가드로 같은 share 두 번 발사 차단 (핸드오프 명시).
# 안전: 1차 발사 전 알람 막힌 share (accept 후 시간 지나 reminded_at_ms 박힘) 도 안전.

_REMIND_POLL_INTERVAL_SEC = 60
_REMIND_WINDOW_MS = 2 * 60 * 60 * 1000  # 2시간


def _remind_pass() -> None:
    """1회 폴링 — 2h 안 들어온 accepted share 찾아서 B 에게 FCM."""
    now = _now_ms()
    with db_conn() as con:
        rows = con.execute(
            """
            SELECT share_id, owner_phone, partner_phone, title, scheduled_at_ms,
                   time_label_raw, daily_wage, owner_name_raw
            FROM shared_sites
            WHERE status = 'accepted'
              AND scheduled_at_ms IS NOT NULL
              AND scheduled_at_ms > ?
              AND scheduled_at_ms - ? <= ?
              AND reminded_at_ms IS NULL
              AND paid_at_ms IS NULL
              AND (progress IS NULL OR progress != 'completed')
            LIMIT 50
            """,
            (now, now, _REMIND_WINDOW_MS),
        ).fetchall()
    for r in rows:
        share_id, owner_phone, partner_phone, title, scheduled_at_ms, time_label_raw, daily_wage, owner_name_raw = r
        # race 차단: 이 쿼리가 1행 UPDATE 한 경우만 FCM 발사 (다른 worker 가 먼저 박았으면 0행).
        with db_conn() as con:
            cur = con.execute(
                """
                UPDATE shared_sites
                SET reminded_at_ms = ?, updated_at_ms = ?
                WHERE share_id = ? AND reminded_at_ms IS NULL
                """,
                (now, now, share_id),
            )
            con.commit()
            if cur.rowcount != 1:
                continue
        # FCM 발사
        tl = (time_label_raw or "").strip()
        if not tl and scheduled_at_ms:
            try:
                tl = _dt.datetime.fromtimestamp(scheduled_at_ms / 1000).strftime("%H:%M")
            except Exception:
                tl = ""
        # §A-3 — owner_name_raw 우선 ('디테일라인')
        owner_name = (owner_name_raw or "").strip() or _is_registered_owner(owner_phone) or "사장님"
        fcm_data = {
            "type": "collab_remind",
            "share_id": share_id,
            "title": title or "협업 현장",
            "owner_name": owner_name,
            "time_label": tl,
        }
        if daily_wage is not None:
            fcm_data["daily_wage"] = str(int(daily_wage))   # FCM data 는 string
        _send_fcm_data_to_phone(partner_phone, fcm_data)
        print(
            f"[shared/remind] share={share_id} partner={partner_phone} "
            f"scheduled={scheduled_at_ms} time={tl} sent"
        )


async def _remind_poller_loop() -> None:
    """무한 poller — uvicorn 살아있는 동안 매 60초 _remind_pass()."""
    print(f"[shared/remind] poller started (interval={_REMIND_POLL_INTERVAL_SEC}s window=2h)")
    while True:
        try:
            _remind_pass()
        except Exception as e:
            print(f"[shared/remind] poller err: {type(e).__name__}: {e}")
        await asyncio.sleep(_REMIND_POLL_INTERVAL_SEC)


# §D startup — lifespan (line 1888) 에서 통합 호출. on_event("startup") 는
#   FastAPI 가 lifespan= 받으면 무시되므로 사용 안 함.


# ============================================================================
# §G — 일당 모집 시스템 (broadcast → 지원 → 선택)
# ─────────────────────────────────────────────────────────────────────────────
# 핸드오프 SERVER_HANDOFF_collab_expansion §G. 프로토 m-compose/m-push/m-detail/
# m-applicants/m-result.
#
# 흐름:
#   1) A 가 partner_phones[] 에 모집 broadcast (FCM recruit_invite).
#   2) B 들이 apply (선착순 applied_at_ms).
#   3) A 가 applicants 조회 (순번 + §B past_count/past_total 가산점).
#   4) A 가 select(selected_phones) → 자동 협업 현장 확정(shared_sites accepted) +
#      FCM recruit_confirmed(정확 주소 공개) / recruit_rejected.
#
# 안전벽: 모집 단계 full_addr 비공개, 지원자끼리 상호 미노출, 고객 정보 0.
# ============================================================================


def _gen_recruit_id() -> str:
    return "rec_" + "".join(
        _secrets_collab.choice(_SHARE_ID_ALPHABET) for _ in range(10)
    )


def _recruit_past_for(partner_phone: str, owner_phone: str) -> tuple:
    """§B 재사용 — 그 partner 가 그 owner 와 한 협업 history (count, total_wage)."""
    with db_conn() as con:
        row = con.execute(
            """
            SELECT COUNT(*) AS cnt,
                   COALESCE(SUM(CASE WHEN progress='completed' THEN COALESCE(daily_wage,0) ELSE 0 END), 0) AS total
            FROM shared_sites
            WHERE partner_phone = ? AND owner_phone = ?
            """,
            (partner_phone, owner_phone),
        ).fetchone()
    return (int(row[0] or 0), int(row[1] or 0))


# ─── ① POST /api/recruit/create ───
class RecruitCreateRequest(BaseModel):
    owner_phone: str
    date_ms: Optional[int] = None           # 모집 일자/시각
    place: Optional[str] = None             # '인천 송도' 대략 위치 (확정 전 공개)
    full_addr: Optional[str] = None         # 정확한 주소 (확정 후만 공개)
    work: Optional[str] = None              # '줄눈'
    daily_wage: Optional[int] = None        # 만원 단위
    partner_phones: list                    # 모집 받을 후보들


@app.post("/api/recruit/create")
async def recruit_create(req: RecruitCreateRequest) -> dict:
    """A 가 모집 공고 broadcast. partner_phones 각자에게 FCM 발사."""
    owner_phone = _norm_phone(req.owner_phone)
    if not owner_phone:
        raise HTTPException(400, "owner_phone 필수")
    _check_team_tier(owner_phone)
    place = (req.place or "").strip()[:100]
    full_addr = (req.full_addr or "").strip()[:200]
    work = (req.work or "").strip()[:60]
    daily_wage_val: Optional[int] = None
    if req.daily_wage is not None:
        try:
            dw = int(req.daily_wage)
            if 0 < dw <= 10000:
                daily_wage_val = dw
        except Exception:
            pass
    # partner_phones 정규화 + 본인 제외 + 중복 제거
    raw_phones = req.partner_phones or []
    if not isinstance(raw_phones, list) or not raw_phones:
        raise HTTPException(400, "partner_phones 비어있음")
    partners: list = []
    seen = set()
    for p in raw_phones:
        if not isinstance(p, str):
            continue
        d = _norm_phone(p)
        if not d or d == owner_phone or d in seen:
            continue
        seen.add(d)
        partners.append(d)
    if not partners:
        raise HTTPException(400, "유효한 partner_phones 없음")

    recruit_id = _gen_recruit_id()
    now = _now_ms()
    with db_conn() as con:
        con.execute(
            """
            INSERT INTO recruits
                (recruit_id, owner_phone, date_ms, place, full_addr, work, daily_wage,
                 status, created_at_ms, updated_at_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'open', ?, ?)
            """,
            (
                recruit_id, owner_phone, req.date_ms,
                place or None, full_addr or None, work or None, daily_wage_val,
                now, now,
            ),
        )
        for p in partners:
            con.execute(
                """
                INSERT INTO recruit_applications
                    (recruit_id, partner_phone, status, invited_at_ms)
                VALUES (?, ?, 'invited', ?)
                """,
                (recruit_id, p, now),
            )
        con.commit()
    # FCM recruit_invite — 각 partner 에게 (full_addr 미포함 = 확정 전 비공개)
    owner_name = _is_registered_owner(owner_phone) or "사장님"
    fcm_data_base = {
        "type": "recruit_invite",
        "recruit_id": recruit_id,
        "owner_name": owner_name,
        "place": place or "",
        "work": work or "",
    }
    if req.date_ms:
        fcm_data_base["date_ms"] = str(int(req.date_ms))
    if daily_wage_val is not None:
        fcm_data_base["daily_wage"] = str(daily_wage_val)
    sent = 0
    for p in partners:
        _send_fcm_data_to_phone(p, dict(fcm_data_base))
        sent += 1
    print(
        f"[recruit/create] recruit={recruit_id} owner={owner_phone} partners={len(partners)} "
        f"work='{work}' wage={daily_wage_val} sent={sent}"
    )
    return {"recruit_id": recruit_id, "partner_count": len(partners)}


# ─── ② GET /api/recruit/with-me ───
@app.get("/api/recruit/with-me")
async def recruit_with_me(phone: str, since_ms: int = 0, limit: int = 50) -> dict:
    """B 가 받은 모집들. invited/applied/selected/rejected 모두 포함.

    벽: full_addr 은 본인이 selected 일 때만 노출.
    """
    partner_phone = _norm_phone(phone)
    if not partner_phone:
        raise HTTPException(400, "phone 필수")
    limit = max(1, min(limit, 200))
    with db_conn() as con:
        rows = con.execute(
            """
            SELECT r.recruit_id, r.owner_phone, r.date_ms, r.place, r.full_addr,
                   r.work, r.daily_wage, r.status, r.created_at_ms,
                   a.status, a.invited_at_ms, a.applied_at_ms, a.decided_at_ms, a.share_id
            FROM recruit_applications a
            JOIN recruits r ON r.recruit_id = a.recruit_id
            WHERE a.partner_phone = ? AND a.invited_at_ms > ?
            ORDER BY a.invited_at_ms DESC
            LIMIT ?
            """,
            (partner_phone, since_ms, limit),
        ).fetchall()
    out = []
    for r in rows:
        (recruit_id, owner_phone, date_ms, place, full_addr, work, daily_wage,
         recruit_status, created_at_ms,
         my_status, invited_at_ms, applied_at_ms, decided_at_ms, share_id) = r
        owner_name = _is_registered_owner(owner_phone) or "사장님"
        item = {
            "recruit_id": recruit_id,
            "owner_phone": owner_phone,    # 본인이 받은 모집의 owner 번호 — 노출 OK
            "owner_name": owner_name,
            "date_ms": int(date_ms or 0),
            "place": place or "",
            "work": work or "",
            "recruit_status": recruit_status,    # open/closed
            "my_status": my_status,              # invited/applied/selected/rejected
            "invited_at_ms": int(invited_at_ms or 0),
            "applied_at_ms": int(applied_at_ms or 0),
            "decided_at_ms": int(decided_at_ms or 0),
            "created_at_ms": int(created_at_ms or 0),
        }
        if daily_wage is not None:
            try:
                item["daily_wage"] = int(daily_wage)
            except Exception:
                pass
        # full_addr 은 selected 일 때만 노출 (벽 — 모집 단계 비공개)
        if my_status == "selected":
            item["full_addr"] = full_addr or ""
            if share_id:
                item["share_id"] = share_id    # 자동 생성된 협업 현장 link
        out.append(item)
    return {"recruits": out}


# ─── ③ POST /api/recruit/apply ───
class RecruitApplyRequest(BaseModel):
    recruit_id: str
    partner_phone: str


@app.post("/api/recruit/apply")
async def recruit_apply(req: RecruitApplyRequest) -> dict:
    """B 가 지원. applied_at_ms 박힘 = 선착순 순번."""
    recruit_id = (req.recruit_id or "").strip()
    partner_phone = _norm_phone(req.partner_phone)
    if not recruit_id or not partner_phone:
        raise HTTPException(400, "recruit_id, partner_phone 필수")
    now = _now_ms()
    with db_conn() as con:
        r = con.execute(
            "SELECT status FROM recruits WHERE recruit_id = ?",
            (recruit_id,),
        ).fetchone()
        if not r:
            raise HTTPException(404, "recruit_id 없음")
        if r[0] != "open":
            raise HTTPException(409, f"모집 마감됨 (status={r[0]})")
        app_row = con.execute(
            "SELECT status, applied_at_ms FROM recruit_applications WHERE recruit_id = ? AND partner_phone = ?",
            (recruit_id, partner_phone),
        ).fetchone()
        if not app_row:
            raise HTTPException(403, "초대받지 않은 모집입니다")
        if app_row[0] != "invited":
            raise HTTPException(409, f"이미 처리된 지원입니다 (status={app_row[0]})")
        con.execute(
            """
            UPDATE recruit_applications
            SET status = 'applied', applied_at_ms = ?
            WHERE recruit_id = ? AND partner_phone = ?
            """,
            (now, recruit_id, partner_phone),
        )
        con.commit()
    print(f"[recruit/apply] recruit={recruit_id} partner={partner_phone} applied_at={now}")
    return {"ok": True, "recruit_id": recruit_id, "applied_at_ms": now}


# ─── ④ GET /api/recruit/applicants ───
@app.get("/api/recruit/applicants")
async def recruit_applicants(recruit_id: str, owner_phone: str) -> dict:
    """A(owner) 가 지원자 목록 조회. 본인 모집만. 선착순 + §B history 가산점.

    응답: applicants[{partner_phone, partner_name, applied_at_ms, rank,
                     past_count, past_total, status}]
    """
    recruit_id = (recruit_id or "").strip()
    owner_digits = _norm_phone(owner_phone)
    if not recruit_id or not owner_digits:
        raise HTTPException(400, "recruit_id, owner_phone 필수")
    with db_conn() as con:
        r = con.execute(
            "SELECT owner_phone FROM recruits WHERE recruit_id = ?",
            (recruit_id,),
        ).fetchone()
        if not r:
            raise HTTPException(404, "recruit_id 없음")
        if _norm_phone(r[0]) != owner_digits:
            raise HTTPException(403, "권한 없음 (본인 모집만 조회 가능)")
        rows = con.execute(
            """
            SELECT partner_phone, status, applied_at_ms, decided_at_ms, share_id
            FROM recruit_applications
            WHERE recruit_id = ? AND applied_at_ms IS NOT NULL
            ORDER BY applied_at_ms ASC
            """,
            (recruit_id,),
        ).fetchall()
    applicants = []
    for i, row in enumerate(rows):
        partner_phone, status, applied_at_ms, decided_at_ms, share_id = row
        past_count, past_total = _recruit_past_for(partner_phone, owner_digits)
        applicants.append({
            "partner_phone": partner_phone,
            "partner_name": _is_registered_owner(partner_phone) or "협업 사장",
            "applied_at_ms": int(applied_at_ms or 0),
            "rank": i + 1,
            "past_count": past_count,
            "past_total": past_total,
            "status": status,    # applied/selected/rejected
            "decided_at_ms": int(decided_at_ms or 0),
            "share_id": share_id,
        })
    return {"applicants": applicants, "count": len(applicants)}


# ─── ⑤ POST /api/recruit/select ───
class RecruitSelectRequest(BaseModel):
    recruit_id: str
    owner_phone: str
    selected_phones: list                    # 선택된 partner phones (1명 이상)


@app.post("/api/recruit/select")
async def recruit_select(req: RecruitSelectRequest) -> dict:
    """A 가 선택. 선택자 → shared_sites 자동 확정 + recruit_confirmed FCM (정확 주소 공개).
    미선택 지원자 → recruit_rejected FCM. recruit 자체 status=closed.
    """
    recruit_id = (req.recruit_id or "").strip()
    owner_digits = _norm_phone(req.owner_phone)
    if not recruit_id or not owner_digits:
        raise HTTPException(400, "recruit_id, owner_phone 필수")
    sel_raw = req.selected_phones or []
    if not isinstance(sel_raw, list) or not sel_raw:
        raise HTTPException(400, "selected_phones 비어있음")
    selected_set = set()
    for p in sel_raw:
        if isinstance(p, str):
            d = _norm_phone(p)
            if d:
                selected_set.add(d)
    if not selected_set:
        raise HTTPException(400, "유효한 selected_phones 없음")
    now = _now_ms()
    with db_conn() as con:
        rec = con.execute(
            """
            SELECT owner_phone, date_ms, place, full_addr, work, daily_wage, status
            FROM recruits WHERE recruit_id = ?
            """,
            (recruit_id,),
        ).fetchone()
        if not rec:
            raise HTTPException(404, "recruit_id 없음")
        if _norm_phone(rec[0]) != owner_digits:
            raise HTTPException(403, "권한 없음")
        if rec[6] == "closed":
            raise HTTPException(409, "이미 마감된 모집입니다")
        owner_phone = rec[0]
        date_ms = rec[1]
        place = rec[2] or ""
        full_addr = rec[3] or ""
        work = rec[4] or ""
        daily_wage = rec[5]
        # 지원한 applicants 만 select 대상 — 초대만 받고 지원 안 한 사람은 'invited' 그대로 둠
        app_rows = con.execute(
            """
            SELECT partner_phone, status FROM recruit_applications
            WHERE recruit_id = ? AND applied_at_ms IS NOT NULL
            """,
            (recruit_id,),
        ).fetchall()
        selected_results = []      # (partner_phone, share_id)
        rejected_results = []      # partner_phone
        for partner_phone, app_status in app_rows:
            if app_status in ("selected", "rejected"):
                continue
            if partner_phone in selected_set:
                # 1) shared_sites 자동 생성 (status=accepted, progress=assigned)
                share_id = _gen_share_id()
                title = work or "협업 현장"
                con.execute(
                    f"""
                    INSERT INTO shared_sites
                        ({_SHARED_SITES_COLS})
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'accepted', 'assigned',
                            NULL, NULL, NULL, NULL, ?, NULL, NULL, ?, ?,
                            ?, NULL, NULL, NULL)
                    """,  # 추가63 fix — owner_name_raw=NULL + 4 컬럼 추가. accepted_at_ms=now (즉시 수락).
                    (
                        share_id, owner_phone, partner_phone,
                        title, full_addr or None, date_ms,
                        work or None, None, None,
                        daily_wage,
                        now, now,
                        now,  # accepted_at_ms — recruit 선택 = 즉시 accepted
                    ),
                )
                # 2) recruit_applications UPDATE
                con.execute(
                    """
                    UPDATE recruit_applications
                    SET status = 'selected', decided_at_ms = ?, share_id = ?
                    WHERE recruit_id = ? AND partner_phone = ?
                    """,
                    (now, share_id, recruit_id, partner_phone),
                )
                selected_results.append((partner_phone, share_id))
            else:
                con.execute(
                    """
                    UPDATE recruit_applications
                    SET status = 'rejected', decided_at_ms = ?
                    WHERE recruit_id = ? AND partner_phone = ?
                    """,
                    (now, recruit_id, partner_phone),
                )
                rejected_results.append(partner_phone)
        con.execute(
            "UPDATE recruits SET status = 'closed', updated_at_ms = ? WHERE recruit_id = ?",
            (now, recruit_id),
        )
        con.commit()
    # FCM 발사
    owner_name = _is_registered_owner(owner_phone) or "사장님"
    title = work or "협업 현장"
    for partner_phone, share_id in selected_results:
        fcm_data = {
            "type": "recruit_confirmed",
            "recruit_id": recruit_id,
            "share_id": share_id,
            "owner_name": owner_name,
            "title": title,
            "full_addr": full_addr,      # 확정 후 정확한 주소 공개
            "place": place,
        }
        if date_ms:
            fcm_data["date_ms"] = str(int(date_ms))
        if daily_wage is not None:
            fcm_data["daily_wage"] = str(int(daily_wage))
        _send_fcm_data_to_phone(partner_phone, fcm_data)
    for partner_phone in rejected_results:
        _send_fcm_data_to_phone(partner_phone, {
            "type": "recruit_rejected",
            "recruit_id": recruit_id,
            "owner_name": owner_name,
            "title": title,
        })
    print(
        f"[recruit/select] recruit={recruit_id} owner={owner_phone} "
        f"selected={len(selected_results)} rejected={len(rejected_results)}"
    )
    return {
        "ok": True,
        "recruit_id": recruit_id,
        "selected": [{"partner_phone": p, "share_id": s} for p, s in selected_results],
        "rejected_count": len(rejected_results),
    }


# ============================================================================
# §29 — 일당 마켓 Phase 1 (안드로이드 PLAN_labor_market 2026-06-11)
# ─────────────────────────────────────────────────────────────────────────────
# 직원/협업일당 공통 흐름 — 완료·계좌 = 정산 스위치 + 번호별 이력 적립.
# 키 = 전화 끝 8자리. 고객 정보 절대 미노출 (안전 라벨만).
#
# endpoint 4종:
#   ① POST /api/labor/complete   — 완료 + 계좌 입력 + owner-events 적재 + 이력 +1
#   ② GET  /api/labor/history    — 번호별 이력 조회 (count, sites)
#   ③ POST /api/labor/paid       — (옵션) 사장 입금 완료 마크
#   ④ GET  /api/labor/account    — 웹뷰 prefill 용 saved_account 조회
# ============================================================================


def _phone_suffix(p: Optional[str]) -> str:
    """전화번호 → 끝 8자리. 미만이면 빈 string."""
    digits = "".join(ch for ch in (p or "") if ch.isdigit())
    return digits[-8:] if len(digits) >= 8 else ""


def _safe_site_label_from_snapshot(snap_json: Optional[str], fallback_name: str) -> str:
    """schedule_snapshot 에서 안전 라벨 추출. 고객 phone/대화 포함 X."""
    if snap_json:
        try:
            snap = json.loads(snap_json) or {}
            if isinstance(snap, dict):
                candidates = (
                    snap.get("jobs")
                    or snap.get("items")
                    or snap.get("schedule")
                    or []
                )
                if isinstance(candidates, list) and candidates:
                    first = candidates[0]
                    if isinstance(first, dict):
                        for key in ("title", "label", "site_label", "name"):
                            v = (first.get(key) or "").strip()
                            if v:
                                return v[:60]
                        # title 없으면 짧은 주소 (고객 정보 X 인 한)
                        addr = (first.get("addr") or first.get("address") or "").strip()
                        if addr:
                            return addr[:60]
        except Exception:
            pass
    return f"{fallback_name or '일당'} 현장"


# ─── ① POST /api/labor/complete ───
# 참여자(일당/팀원) 가 웹뷰에서 [완료·계좌] 누르면 호출.

class LaborCompleteRequest(BaseModel):
    token: str
    bank: Optional[str] = None
    account_no: Optional[str] = None
    holder: Optional[str] = None


@app.post("/api/labor/complete")
async def labor_complete(req: LaborCompleteRequest) -> dict:
    """일당/팀원 완료 + 계좌 등록.

    동작:
      1. 그 배정 완료 기록 (laborer_sites + completed_event_id)
      2. shared_owner_events 에 completed + account payload (사장 폴링용)
      3. 번호 (phone_suffix) 이력 +1 적립 + saved_account UPSERT
    """
    token = (req.token or "").strip()
    if not token:
        raise HTTPException(400, "token 필수")
    bank = (req.bank or "").strip()[:30]
    account_no = (req.account_no or "").strip()[:30]
    holder = (req.holder or "").strip()[:30]

    with db_conn() as con:
        row = con.execute(
            """
            SELECT l.owner_phone, l.member_id, l.schedule_snapshot_json,
                   l.expires_at_ms, m.phone, m.name
            FROM team_member_links l
            LEFT JOIN team_members m ON m.member_id = l.member_id
            WHERE l.token = ?
            """,
            (token,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "유효하지 않은 token")
        owner_phone, member_id, snap_json, expires_at, worker_phone, worker_name = row
        now = _now_ms()
        if now > (expires_at or 0):
            raise HTTPException(410, "만료된 token")

        worker_phone_str = (worker_phone or "").strip()
        phone_suffix = _phone_suffix(worker_phone_str)
        if not phone_suffix:
            raise HTTPException(400, "참여자 전화번호가 짧음 (8자리 미만)")

        site_label = _safe_site_label_from_snapshot(snap_json, worker_name or "")

        # 2) laborer UPSERT + saved_account
        account_provided = bool(bank or account_no or holder)
        con.execute(
            """
            INSERT INTO laborer
                (phone_suffix, completed_count, last_worked_at_ms,
                 saved_bank, saved_account_no, saved_holder,
                 saved_account_updated_at_ms, created_at_ms, updated_at_ms)
            VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(phone_suffix) DO UPDATE SET
              completed_count = completed_count + 1,
              last_worked_at_ms = excluded.last_worked_at_ms,
              saved_bank = CASE WHEN excluded.saved_bank IS NOT NULL
                                THEN excluded.saved_bank ELSE saved_bank END,
              saved_account_no = CASE WHEN excluded.saved_account_no IS NOT NULL
                                       THEN excluded.saved_account_no ELSE saved_account_no END,
              saved_holder = CASE WHEN excluded.saved_holder IS NOT NULL
                                   THEN excluded.saved_holder ELSE saved_holder END,
              saved_account_updated_at_ms = CASE WHEN excluded.saved_account_updated_at_ms IS NOT NULL
                                                  THEN excluded.saved_account_updated_at_ms ELSE saved_account_updated_at_ms END,
              updated_at_ms = excluded.updated_at_ms
            """,
            (
                phone_suffix, now,
                bank or None, account_no or None, holder or None,
                now if account_provided else None,
                now, now,
            ),
        )

        # 3) shared_owner_events 에 completed + account (사장 폴링)
        event_id = "evt_" + "".join(
            _secrets_collab.choice(_SHARE_ID_ALPHABET) for _ in range(10)
        )
        con.execute(
            """
            INSERT INTO shared_owner_events
                (event_id, share_id, owner_phone, partner_phone, step, title,
                 account_bank, account_no, account_holder, created_at_ms)
            VALUES (?, ?, ?, ?, 'completed', ?, ?, ?, ?, ?)
            """,
            (
                event_id, token,  # share_id 자리에 token (labor 흐름)
                owner_phone, worker_phone_str,
                site_label,
                bank or None, account_no or None, holder or None,
                now,
            ),
        )

        # 4) laborer_sites 적재
        cur = con.execute(
            """
            INSERT INTO laborer_sites
                (phone_suffix, token, owner_phone, label, worked_at_ms,
                 photos_json, completed_event_id, created_at_ms)
            VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """,
            (phone_suffix, token, owner_phone, site_label, now, event_id, now),
        )
        site_id = cur.lastrowid

        con.commit()

    print(
        f"[labor/complete] token={token[:10]}.. suffix={phone_suffix} "
        f"owner={owner_phone} event={event_id} site={site_id}"
    )
    return {
        "ok": True,
        "site_id": site_id,
        "event_id": event_id,
        "completed_at_ms": now,
        "phone_suffix": phone_suffix,
        "label": site_label,
    }


# ─── ② GET /api/labor/history ───
# 번호별 일당 이력. 사장이 배정/초대 화면에서 호출 — 그 사람 경력 가늠.

@app.get("/api/labor/history")
async def labor_history(phone: str, limit: int = 50) -> dict:
    """번호 (끝 8자리) 별 이력. count + last_worked_at + sites[].

    벽: 고객 전화·대화·계약 절대 미포함. label/date/photos 만.
    """
    phone_digits = "".join(ch for ch in (phone or "") if ch.isdigit())
    if len(phone_digits) < 8:
        raise HTTPException(400, "phone 끝 8자리 필요")
    phone_suffix = phone_digits[-8:]
    limit = max(1, min(limit, 200))

    with db_conn() as con:
        row = con.execute(
            "SELECT completed_count, last_worked_at_ms FROM laborer WHERE phone_suffix = ?",
            (phone_suffix,),
        ).fetchone()
        if not row:
            return {
                "count": 0,
                "last_worked_at_ms": None,
                "last_worked_at": None,
                "sites": [],
            }
        count, last_worked = row
        rows = con.execute(
            """
            SELECT label, worked_at_ms, photos_json, paid_at_ms
            FROM laborer_sites
            WHERE phone_suffix = ?
            ORDER BY worked_at_ms DESC
            LIMIT ?
            """,
            (phone_suffix, limit),
        ).fetchall()

    sites = []
    for r in rows:
        label, worked_at_ms, photos_json, paid_at_ms = r
        photos: list = []
        if photos_json:
            try:
                p = json.loads(photos_json)
                if isinstance(p, list):
                    photos = p
            except Exception:
                photos = []
        try:
            date_str = _dt.datetime.fromtimestamp(worked_at_ms / 1000).strftime("%Y-%m-%d")
        except Exception:
            date_str = ""
        sites.append({
            "label": label or "",
            "date": date_str,
            "worked_at_ms": worked_at_ms,
            "photos": photos,
            "paid": paid_at_ms is not None,
        })

    last_worked_at_str = ""
    if last_worked:
        try:
            last_worked_at_str = _dt.datetime.fromtimestamp(last_worked / 1000).strftime("%Y-%m-%d")
        except Exception:
            pass

    return {
        "count": count,
        "last_worked_at_ms": last_worked,
        "last_worked_at": last_worked_at_str,
        "sites": sites,
    }


# ─── ③ POST /api/labor/paid ───
# (옵션) 사장이 입금 완료 표시. token 또는 event_id 로 식별.

class LaborPaidRequest(BaseModel):
    token: Optional[str] = None
    event_id: Optional[str] = None


@app.post("/api/labor/paid")
async def labor_paid(req: LaborPaidRequest) -> dict:
    """입금 완료 표시. token 또는 event_id 하나는 필수.

    laborer_sites.paid_at_ms 업데이트. 이력 화면에 "정산완료 ✓" 표시.
    """
    token = (req.token or "").strip() or None
    event_id = (req.event_id or "").strip() or None
    if not token and not event_id:
        raise HTTPException(400, "token 또는 event_id 필수")
    now = _now_ms()
    with db_conn() as con:
        if event_id:
            cur = con.execute(
                """
                UPDATE laborer_sites SET paid_at_ms = ?
                WHERE completed_event_id = ? AND paid_at_ms IS NULL
                """,
                (now, event_id),
            )
        else:
            cur = con.execute(
                """
                UPDATE laborer_sites SET paid_at_ms = ?
                WHERE token = ? AND paid_at_ms IS NULL
                """,
                (now, token),
            )
        affected = cur.rowcount
        con.commit()
    print(f"[labor/paid] token={token or ''} event={event_id or ''} marked={affected}")
    return {"ok": True, "marked_paid": affected, "paid_at_ms": now}


# ─── ④ GET /api/labor/account ───
# 웹뷰 prefill 용 saved_account 조회 (token 검증 후 그 참여자의 저장 계좌).

@app.get("/api/labor/account")
async def labor_account(token: str) -> dict:
    """token 검증 후 그 참여자의 saved_account 반환 (웹뷰 prefill 용).

    응답: {found: bool, bank?, account_no?, holder?, count, last_worked_at?}
    벽: 다른 사람의 계좌 절대 노출 X (token 매칭 필수).
    """
    token = (token or "").strip()
    if not token:
        raise HTTPException(400, "token 필수")
    with db_conn() as con:
        row = con.execute(
            """
            SELECT m.phone FROM team_member_links l
            LEFT JOIN team_members m ON m.member_id = l.member_id
            WHERE l.token = ?
            """,
            (token,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "유효하지 않은 token")
        worker_phone = (row[0] or "").strip()
        phone_suffix = _phone_suffix(worker_phone)
        if not phone_suffix:
            return {"found": False, "count": 0}
        lrow = con.execute(
            """
            SELECT saved_bank, saved_account_no, saved_holder,
                   completed_count, last_worked_at_ms
            FROM laborer WHERE phone_suffix = ?
            """,
            (phone_suffix,),
        ).fetchone()
    if not lrow:
        return {"found": False, "count": 0}
    bank, account_no, holder, count, last_worked = lrow
    return {
        "found": True,
        "bank": bank or "",
        "account_no": account_no or "",
        "holder": holder or "",
        "count": count or 0,
        "last_worked_at_ms": last_worked,
    }


# ============================================================================
# §30 — FCM 푸시 (안드로이드 SERVER_HANDOFF_fcm_push 2026-06-12)
# ─────────────────────────────────────────────────────────────────────────────
# 협업 invite/progress/paid 시점에 앱 꺼져 있어도 즉시 푸시 알림.
# 폴링은 그대로 유지 (안전망 — FCM 실패해도 폴링으로 결국 받음).
#
# 데이터 모델:
#   push_tokens (token PK, phone, platform, registered_at_ms, updated_at_ms)
#
# 흐름:
#   1. 앱 onNewToken → POST /api/push/register {phone, token, platform}
#   2. 협업 invite (route=inapp) → partner_phone 의 token 들로 FCM data-only 전송
#   3. 협업 progress / paid 동일 패턴
#
# 벽: data payload 에 고객 phone/대화 절대 X. owner_name + title 만.
# ============================================================================

# Firebase Admin SDK lazy init — 첫 호출 시 service account 로드.
# 미설정 (FCM_SERVICE_ACCOUNT_JSON env 없거나 파일 X) → FCM 호출 skip (폴링 폴백).
FCM_SA_JSON_PATH = os.environ.get("FCM_SERVICE_ACCOUNT_JSON")
_FCM_INITIALIZED = False
_FCM_AVAILABLE = False


def _init_firebase() -> bool:
    """첫 호출 시 firebase-admin 초기화. 성공 시 True, 실패/미설정 시 False."""
    global _FCM_INITIALIZED, _FCM_AVAILABLE
    if _FCM_INITIALIZED:
        return _FCM_AVAILABLE
    _FCM_INITIALIZED = True
    if not FCM_SA_JSON_PATH:
        print("[fcm] FCM_SERVICE_ACCOUNT_JSON env 미설정 — FCM 비활성")
        _FCM_AVAILABLE = False
        return False
    if not Path(FCM_SA_JSON_PATH).exists():
        print(f"[fcm] service account JSON 파일 없음: {FCM_SA_JSON_PATH}")
        _FCM_AVAILABLE = False
        return False
    try:
        import firebase_admin
        from firebase_admin import credentials
        cred = credentials.Certificate(FCM_SA_JSON_PATH)
        firebase_admin.initialize_app(cred)
        _FCM_AVAILABLE = True
        print(f"[fcm] Firebase Admin 초기화 완료 ({FCM_SA_JSON_PATH})")
        return True
    except Exception as e:
        print(f"[fcm] Firebase Admin 초기화 실패: {type(e).__name__}: {e}")
        _FCM_AVAILABLE = False
        return False


def _get_tokens_for_phone(phone_digits: str) -> list[str]:
    """phone 의 등록된 FCM token list."""
    if not phone_digits:
        return []
    with db_conn() as con:
        rows = con.execute(
            "SELECT token FROM push_tokens WHERE phone = ? ORDER BY updated_at_ms DESC LIMIT 10",
            (phone_digits,),
        ).fetchall()
    return [r[0] for r in rows if r[0]]


def _delete_invalid_token(token: str) -> None:
    """무효 토큰 (UnregisteredError) 자동 삭제."""
    if not token:
        return
    try:
        with db_conn() as con:
            con.execute("DELETE FROM push_tokens WHERE token = ?", (token,))
            con.commit()
        print(f"[fcm] 무효 token 삭제: {token[:20]}...")
    except Exception as e:
        print(f"[fcm] token 삭제 실패: {type(e).__name__}: {e}")


def _send_fcm_data_to_phone(phone_digits: str, data: dict) -> dict:
    """phone 의 모든 토큰으로 FCM data-only 메시지 전송.

    반환: {sent, failed, removed} 카운트. 실패해도 raise 안 함 (폴링 폴백 위해).
    """
    result = {"sent": 0, "failed": 0, "removed": 0}
    if not _init_firebase():
        return result
    tokens = _get_tokens_for_phone(phone_digits)
    if not tokens:
        return result
    try:
        from firebase_admin import messaging
    except ImportError:
        return result
    # data payload 는 모두 string (FCM 요구사항)
    data_str = {k: str(v) for k, v in data.items() if v is not None}
    for tok in tokens:
        try:
            message = messaging.Message(
                data=data_str,
                token=tok,
                android=messaging.AndroidConfig(priority="high"),
            )
            messaging.send(message)
            result["sent"] += 1
        except Exception as e:
            err_name = type(e).__name__
            err_msg = str(e).lower()
            # 무효 토큰 = 삭제
            if "unregistered" in err_msg or "invalid" in err_msg or err_name == "UnregisteredError":
                _delete_invalid_token(tok)
                result["removed"] += 1
            else:
                result["failed"] += 1
                print(f"[fcm] send 실패 token={tok[:20]}... {err_name}: {e}")
    print(
        f"[fcm] {phone_digits} type={data.get('type','?')} "
        f"sent={result['sent']} failed={result['failed']} removed={result['removed']}"
    )
    return result


# ============================================================================
# 추가51 (2026-06-21) — 사용자 여정 트래킹 (사장님 요청)
# ─────────────────────────────────────────────────────────────────────────────
# 앱이 화면 진입·버튼 클릭·캡쳐 시도 시점에 호출 → app_events INSERT.
# 가벼운 1개 / 배치 (events 배열) 둘 다 지원.
# 미등록 phone 도 receive (graceful — 베타 운영 측면).
# ============================================================================


class AppEventItem(BaseModel):
    event_name: str                            # 'screen_view' / 'button_click' / 'screenshot' / 'feature_use'
    screen: Optional[str] = None               # 'home' / 'chat' / 'collab' / ...
    target: Optional[str] = None               # 'btn_reply_suggest' 같은 식별자
    extra: Optional[dict] = None               # 자유 페이로드
    timestamp_ms: Optional[int] = None         # 안드로이드 측 발생 시각 (없으면 서버 now)


class AppEventRequest(BaseModel):
    owner_phone: str                           # 사장님 phone
    # 단일: event 만 보내거나, 배치: events 보냄
    event: Optional[AppEventItem] = None
    events: Optional[list[AppEventItem]] = None


@app.post("/api/event")
async def app_event_log(req: AppEventRequest) -> dict:
    """추가51 (2026-06-21) — 앱 이벤트 1개 또는 배치 INSERT.

    body 예시 (단일):
      {"owner_phone":"01080052080","event":{"event_name":"screen_view","screen":"home"}}
    body 예시 (배치):
      {"owner_phone":"01080052080","events":[{...},{...}]}
    """
    owner_phone = _norm_phone(req.owner_phone)
    if not owner_phone:
        raise HTTPException(400, "owner_phone 필수")
    _touch_beta_whitelist(owner_phone)  # 앱 실행 신호 (last_seen 갱신)
    items: list[AppEventItem] = []
    if req.events:
        items.extend(req.events)
    if req.event:
        items.append(req.event)
    if not items:
        raise HTTPException(400, "event 또는 events 필수")
    if len(items) > 100:
        raise HTTPException(400, "한 번에 최대 100건")
    now = _now_ms()
    rows = []
    for it in items:
        ts = int(it.timestamp_ms) if it.timestamp_ms else now
        # 추가56 fix (2026-06-25) — _json undefined NameError 500. 안드로이드 진단으로 잡힘.
        # extra 가 있는 이벤트 (backfill 의 {backfilled:true} 등) = 500 → 배치 통째 실패 → 재발사 무한 루프.
        extra_str = json.dumps(it.extra, ensure_ascii=False) if it.extra else None
        rows.append((
            owner_phone,
            (it.event_name or "")[:50],
            (it.screen or "")[:50] or None,
            (it.target or "")[:80] or None,
            extra_str,
            ts,
        ))
    with db_conn() as con:
        con.executemany(
            """
            INSERT INTO app_events
                (owner_phone, event_name, screen, target, extra_json, created_at_ms)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            rows,
        )
        con.commit()
    print(f"[event] owner={owner_phone} count={len(rows)}")
    return {"ok": True, "count": len(rows)}


# ─── POST /api/push/register ───
# 앱이 onNewToken / 앱 시작 시 호출. phone ↔ token 매핑 UPSERT.

class PushRegisterRequest(BaseModel):
    phone: str
    token: str
    platform: Optional[str] = "android"


@app.post("/api/push/register")
async def push_register(req: PushRegisterRequest) -> dict:
    """FCM 토큰 등록 (UPSERT). 같은 token 재호출 시 phone 갱신."""
    phone_digits = "".join(ch for ch in (req.phone or "") if ch.isdigit())
    token = (req.token or "").strip()
    if not phone_digits:
        raise HTTPException(400, "phone 필수")
    if not token:
        raise HTTPException(400, "token 필수")
    _touch_beta_whitelist(phone_digits)  # 추가47 (2026-06-21) — 옛 빌드 첫 진입에서도 잡힘
    platform = (req.platform or "android").strip()[:20]
    now = _now_ms()
    with db_conn() as con:
        con.execute(
            """
            INSERT INTO push_tokens (token, phone, platform, registered_at_ms, updated_at_ms)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(token) DO UPDATE SET
              phone = excluded.phone,
              platform = excluded.platform,
              updated_at_ms = excluded.updated_at_ms
            """,
            (token, phone_digits, platform, now, now),
        )
        con.commit()
    print(f"[push/register] phone={phone_digits} token={token[:20]}... platform={platform}")
    return {"ok": True}


# ─── POST /api/push/unregister ───
# 로그아웃 / 토큰 무효 시 호출.

class PushUnregisterRequest(BaseModel):
    token: str


@app.post("/api/push/unregister")
async def push_unregister(req: PushUnregisterRequest) -> dict:
    """FCM 토큰 삭제."""
    token = (req.token or "").strip()
    if not token:
        raise HTTPException(400, "token 필수")
    with db_conn() as con:
        cur = con.execute("DELETE FROM push_tokens WHERE token = ?", (token,))
        con.commit()
    return {"ok": True, "deleted": cur.rowcount}


# ─── ⑧ GET /shared/{share_id} — 미가입 사장 link 도착 페이지 (HTML) ───
# 협업 invite 시 partner 가 미가입이면 sms_draft 에 박힌 link 가 여기로 옴.
# 안내문 + 현장 정보 일부 + 시공막내 앱 설치 안내 + /install 큰 버튼.
# 벽: 고객 phone / 대화 / 타 고객 절대 노출 X. customer_label + 현장 메타만.

@app.get("/shared/{share_id}", response_class=HTMLResponse, include_in_schema=False)
async def shared_link_page(share_id: str) -> HTMLResponse:
    """미가입 사장이 SMS link 클릭하면 보이는 안내 페이지."""
    import html as _html
    with db_conn() as con:
        row = con.execute(
            f"SELECT {_SHARED_SITES_COLS} FROM shared_sites WHERE share_id = ?",
            (share_id,),
        ).fetchone()
    if not row:
        return HTMLResponse(
            content=(
                "<!doctype html><html lang='ko'><head><meta charset='utf-8'>"
                "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                "<title>시공막내 — 링크 만료</title>"
                "<style>body{font-family:-apple-system,sans-serif;background:#F4F5F7;color:#0B0F19;padding:60px 20px;text-align:center;}"
                "h2{font-size:22px;color:#F0436A;margin-bottom:10px;letter-spacing:-.03em}"
                "p{color:#5A6472;font-size:14px;line-height:1.6;}</style></head>"
                "<body><h2>❌ 유효하지 않은 링크</h2>"
                "<p>이 협업 공유 링크가 잘못되었거나 만료되었어요.<br>"
                "사장님께 새 링크를 요청해 주세요.</p></body></html>"
            ),
            status_code=404,
        )
    data_dict = _shared_site_row_to_dict(row, viewer_kind="partner")
    owner_name = _html.escape(data_dict.get("owner_name") or "사장님")
    title = _html.escape(data_dict.get("title") or "협업 현장")
    addr = _html.escape(data_dict.get("addr") or "")
    work_summary = _html.escape(data_dict.get("work_summary") or "")
    memo = _html.escape(data_dict.get("memo") or "")
    customer_label = _html.escape(data_dict.get("customer_label") or "")
    time_label = _html.escape(data_dict.get("time_label") or "")
    scheduled_at = data_dict.get("scheduled_at_ms") or 0
    date_label = ""
    if scheduled_at:
        try:
            date_label = _dt.datetime.fromtimestamp(scheduled_at / 1000).strftime("%Y년 %m월 %d일 (%a)")
        except Exception:
            date_label = ""
    date_label = _html.escape(date_label)
    html_page = f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#3182F6">
<title>시공막내 — {owner_name}님이 협업 현장 공유</title>
<link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css" rel="stylesheet">
<style>
  *{{box-sizing:border-box;margin:0;padding:0;}}
  body{{font-family:'Pretendard',-apple-system,system-ui,sans-serif;background:#F4F5F7;color:#0B0F19;line-height:1.55;letter-spacing:-.01em;-webkit-font-smoothing:antialiased;}}
  .wrap{{max-width:480px;margin:0 auto;padding:20px 18px 60px;}}
  .topbar{{display:flex;align-items:center;gap:8px;margin-bottom:20px;}}
  .topbar .logo{{font-size:17px;font-weight:900;letter-spacing:-.04em;color:#0B0F19;}}
  .topbar .logo .dot{{display:inline-block;width:8px;height:8px;border-radius:50%;background:#3182F6;margin-right:4px;vertical-align:middle;box-shadow:0 0 0 3px #EEF4FF;}}
  .topbar .badge{{margin-left:auto;background:#F1ECFF;color:#7C5CFC;font-size:11px;font-weight:800;padding:5px 10px;border-radius:999px;}}
  .hero{{background:linear-gradient(135deg,#EAF2FF 0%,#F1ECFF 100%);border:1px solid rgba(49,130,246,.18);border-radius:18px;padding:22px 20px;text-align:center;margin-bottom:18px;}}
  .hero .h-ic{{font-size:36px;margin-bottom:8px;}}
  .hero h1{{font-size:20px;font-weight:900;letter-spacing:-.035em;line-height:1.35;color:#0B0F19;margin-bottom:6px;}}
  .hero h1 b{{color:#3182F6;}}
  .hero p{{font-size:13.5px;color:#5A6472;line-height:1.6;}}
  .card{{background:#fff;border:1px solid #EEF0F3;border-radius:16px;padding:18px;margin-bottom:12px;box-shadow:0 2px 10px rgba(17,24,39,.05);}}
  .card-title{{font-size:14px;font-weight:900;letter-spacing:-.025em;color:#0B0F19;margin-bottom:12px;display:flex;align-items:center;gap:8px;}}
  .row{{display:flex;gap:12px;padding:9px 0;border-bottom:1px solid #EEF0F3;}}
  .row:last-child{{border-bottom:0;}}
  .row .k{{width:70px;font-size:12px;color:#9AA3AF;font-weight:700;flex-shrink:0;}}
  .row .v{{flex:1;font-size:13.5px;color:#0B0F19;font-weight:600;word-break:break-word;}}
  .row .v.memo{{color:#5A6472;font-weight:500;white-space:pre-wrap;}}
  .install-cta{{background:linear-gradient(135deg,#3182F6 0%,#7C5CFC 100%);color:#fff;padding:16px;border-radius:14px;font-size:15px;font-weight:900;text-align:center;text-decoration:none;display:block;box-shadow:0 8px 22px rgba(49,130,246,.32);margin-top:18px;letter-spacing:-.02em;transition:transform .12s ease;}}
  .install-cta:active{{transform:scale(.98);}}
  .footer-note{{text-align:center;font-size:11.5px;color:#9AA3AF;margin-top:18px;line-height:1.7;}}
  .footer-note a{{color:#3182F6;text-decoration:underline;}}
</style>
</head>
<body>
<script>
(function(){{
  // §27 deep link — 앱 설치된 사용자는 자동으로 앱으로 (custom scheme 시도)
  // 안드로이드 측 manifest 에 intent-filter (scheme="shigongmagne") 등록되어 있어야 작동.
  // 미등록 / 미설치 → iframe load fail, HTML 페이지 그대로 표시 (사용자 눈에 안 보임).
  // 추후 Android App Link (autoVerify) 등록 시 OS 가 URL 차원에서 분기 — 이 JS 도 불필요해짐.
  try {{
    var shareId = "{share_id}";
    var iframe = document.createElement('iframe');
    iframe.style.cssText = 'display:none;width:0;height:0;border:0;position:absolute;';
    iframe.src = 'shigongmagne://shared/' + shareId;
    document.body.appendChild(iframe);
    // 1초 후 iframe 제거 (메모리 정리)
    setTimeout(function(){{ try {{ iframe.remove(); }} catch(e){{}} }}, 1000);
  }} catch(e){{}}
}})();
</script>

<div class="wrap">
  <div class="topbar">
    <div class="logo"><span class="dot"></span>시공막내</div>
    <div class="badge">협업 현장</div>
  </div>

  <div class="hero">
    <div class="h-ic">🤝</div>
    <h1><b>{owner_name}님</b>이<br>협업 현장을 공유했어요</h1>
    <p>시공막내 앱 설치 후 같은 번호로 가입하시면<br>인앱으로 받으실 수 있어요.</p>
  </div>

  <div class="card">
    <div class="card-title">📋 현장 정보</div>
    {f'<div class="row"><div class="k">현장명</div><div class="v">{title}</div></div>' if title else ''}
    {f'<div class="row"><div class="k">날짜</div><div class="v">{date_label} {time_label}</div></div>' if date_label or time_label else ''}
    {f'<div class="row"><div class="k">주소</div><div class="v">{addr}</div></div>' if addr else ''}
    {f'<div class="row"><div class="k">작업</div><div class="v">{work_summary}</div></div>' if work_summary else ''}
    {f'<div class="row"><div class="k">메모</div><div class="v memo">{memo}</div></div>' if memo else ''}
    {f'<div class="row"><div class="k">고객</div><div class="v">{customer_label}</div></div>' if customer_label else ''}
  </div>

  <a href="/install" class="install-cta">📲 시공막내 앱 설치하기 →</a>

  <div class="footer-note">
    설치 후 가입 시 <b>{owner_name}님이 공유한 이 현장이 자동으로 협업 목록에 추가</b>됩니다.<br>
    문의: <a href="mailto:hello@si0in.kr">hello@si0in.kr</a>
  </div>
</div>
</body>
</html>"""
    return HTMLResponse(content=html_page)


# ─── ⑧ GET /.well-known/assetlinks.json — Android App Link autoVerify ───
# 안드로이드 OS 가 이 도메인이 시공막내 앱을 인증했는지 확인하는 파일.
# 이 파일이 있어야 https://api.si0in.kr/shared/{...} link 가 브라우저 안 거치고 바로 앱 열림.
#
# 검증: https://digitalassetlinks.googleapis.com/v1/statements:list
#       ?source.web.site=https://api.si0in.kr&relation=delegate_permission/common.handle_all_urls
#
# sha256 = release 키 (ringgo-release.jks) 지문. 안드로이드 측 SERVER_HANDOFF 2026-06-08.

_ANDROID_ASSETLINKS = [
    {
        "relation": ["delegate_permission/common.handle_all_urls"],
        "target": {
            "namespace": "android_app",
            "package_name": "com.detailline.callfollowcrm",
            "sha256_cert_fingerprints": [
                # release 키 (ringgo-release.jks)
                "4B:C6:27:28:45:43:98:B8:9F:F9:D0:BD:41:02:9C:D6:6F:1D:39:7B:42:84:F0:61:5B:BD:26:71:86:4B:22:EE",
                # debug 키 (~/.android/debug.keystore) — 사장님 debug 빌드 테스트 호환
                "E7:83:43:AF:8F:3C:12:2B:E4:6A:C0:56:24:2E:EC:DF:60:55:0C:FD:B0:67:01:97:80:D6:27:73:91:4F:ED:84",
            ],
        },
    }
]


@app.get("/.well-known/assetlinks.json", include_in_schema=False)
async def assetlinks_json() -> list:
    """App Link 도메인 검증용. Content-Type: application/json (FastAPI 자동), 200, 본문 = 위 list."""
    return _ANDROID_ASSETLINKS


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
    "https://api.si0in.kr",
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
    privacy_agreed: bool = False                      # 추가98 — 고객 개인정보 동의 (영수증 기록용)


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
        # 추가59 (2026-06-25) — owner_phone 도 SELECT (제출 후 사장님 폰에 즉시 FCM)
        row = con.execute(
            "SELECT phone, expires_at_ms, submitted_at_ms, owner_phone FROM intake_forms WHERE token = ?",
            (req.token,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "유효하지 않은 토큰")
        phone, expires_at, submitted_at, owner_phone = row
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
    print(f"[intake-form/submit] token={req.token} phone={phone} owner={owner_phone} → submitted")
    if req.privacy_agreed:
        _record_intake_consent(contact_phone)  # 추가98 — 고객 동의 영수증

    # 추가59 (2026-06-25) — 사장님 폰에 즉시 FCM data-only (60초 폴링 대기 X).
    # 앱 (RingGoFcmService) 가 type=intake_submitted 받으면 즉시 sync.
    # owner_phone 없으면 발급 phone 폴백. 실패해도 응답에 영향 X.
    fcm_target = owner_phone or phone
    if fcm_target:
        try:
            _send_fcm_data_to_phone(fcm_target, {
                "type": "intake_submitted",
                "token": req.token,
                "customer_phone": contact_phone,
            })
        except Exception as e:
            print(f"[intake-form/submit] FCM 발송 실패 (무시): {type(e).__name__}: {e}")

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
     deposit_amount_krw, deposit_mode, deposit_ratio_pct, biz_name,
     owner_memo, vat_included) = row  # 추가95③ owner_memo + 추가102 vat_included (20개)
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
        "owner_memo": owner_memo or "",  # 추가95③
        "vat_included": bool(vat_included),  # 추가102 — NULL/0=별도, 1=포함
    }


_INTAKE_SELECT_COLS = (
    "token, phone, customer_name, issued_at_ms, expires_at_ms, submitted_at_ms, "
    "payload_json, device_id, owner_phone, created_at_ms, "
    "scheduled_at_ms, scheduled_days, estimate_items_json, total_man, "
    "deposit_amount_krw, deposit_mode, deposit_ratio_pct, biz_name, "
    "owner_memo, vat_included"  # 추가95③ + 추가102 (20컬럼)
)


def _fmt_phone_dashed(p: Optional[str]) -> str:
    """전화번호 010-XXXX-XXXX / 010-XXX-XXXX 포맷. 비어있거나 형식 모르면 '010-' fallback.

    §19 시공접수서 폼의 phone input prefill 용 (사장님 2026-06-04 요청).
    웹은 폰 번호 자동 못 읽으니 발급 시 저장한 intake_forms.phone 으로 prefill.
    """
    digits = "".join(ch for ch in (p or "") if ch.isdigit())
    if len(digits) == 11:
        return f"{digits[:3]}-{digits[3:7]}-{digits[7:]}"
    if len(digits) == 10:
        return f"{digits[:3]}-{digits[3:6]}-{digits[6:]}"
    return (p or "").strip() or "010-"


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
{owner_memo_html}
    <!-- 카드 2: 견적 내역 (표시만) -->
    <div class="q-card">
      <div class="q-card-h"><span class="q-step">2</span>견적 내역</div>
      {items_html}
      <div class="q-total"><span>합계</span><b>{total_man_html}만원</b></div>
      <div class="q-vat">{vat_label_html}</div>
      {deposit_html}
    </div>

    <!-- 카드 3: 연락처·현장 정보 (고객 입력) -->
    <div class="q-card">
      <div class="q-card-h"><span class="q-step">3</span>연락처 · 현장 정보</div>
      <div class="q-label">전화번호</div>
      <input class="q-input" id="q-phone" inputmode="numeric" value="{phone_html}" autocomplete="tel">
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

    <!-- 추가98 — (필수) 고객 개인정보 수집·이용 동의 (법정 필수 — 사장님 승인 하 프로토 외 추가) -->
    <div class="q-agree" id="q-privacy" onclick="togglePrivacy()">
      <span class="qa-box">✓</span>
      <span>(필수) 개인정보 수집·이용 동의
        <a href="javascript:void(0)" id="q-priv-more"
           onclick="event.stopPropagation(); togglePrivacyDetail();"
           style="margin-left:6px; font-size:12px; font-weight:700; color:var(--blue-dark);">자세히 ▾</a><br>
        <span id="q-priv-detail" style="display:none; font-weight:500; font-size:12px; color:var(--t2); line-height:1.6;">
        <b>수집·이용 주체(개인정보처리자):</b> {biz_html} (접수서를 발행한 시공업체)<br>
        <b>목적:</b> 시공 접수, 상담 연락, 일정 확정<br>
        <b>항목:</b> 휴대전화 번호, 현장 주소, 현장 메모(고객이 직접 입력한 내용)<br>
        <b>보유·이용 기간:</b> 시공 완료일부터 1년(시공이 진행되지 않은 경우 접수일부터 6개월)이 지난 때 지체 없이 파기. 고객 또는 {biz_html}의 삭제 요청 시 즉시 파기.<br>
        <b>처리 위탁:</b> 앱 운영사 막내컴퍼니가 {biz_html}으로부터 위탁받아 시공막내 서버에 보관·처리합니다(개인정보 보호법 제26조). 막내컴퍼니는 위탁 목적 외로 이 정보를 이용하지 않으며, AI 자동응대에 사용하지 않습니다.<br>
        귀하는 동의를 거부할 권리가 있습니다. 다만 필수 항목 동의를 거부하시면 시공 접수가 불가능합니다.
        <a href="/q/{token_js}/privacy" target="_blank" onclick="event.stopPropagation()" style="color:var(--blue-dark); font-weight:700;">{biz_html} 개인정보 처리방침 보기</a>
        </span></span>
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
  var quoteSel = {{ agree:false, privacy:false }};
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

  // 추가98 — (필수) 개인정보 동의
  function togglePrivacy() {{
    quoteSel.privacy = !quoteSel.privacy;
    document.getElementById('q-privacy').classList.toggle('on', quoteSel.privacy);
    updateSubmit();
  }}

  // 추가120 (사장님 2026-07-13) — 동의 상세는 기본 접힘. [자세히 ▾] 로만 펼침.
  // 체크(togglePrivacy)와 분리 — 이 링크의 onclick 에서 stopPropagation 함.
  function togglePrivacyDetail() {{
    var d = document.getElementById('q-priv-detail');
    var a = document.getElementById('q-priv-more');
    var open = (d.style.display !== 'none');
    d.style.display = open ? 'none' : 'inline';
    a.textContent = open ? '자세히 ▾' : '접기 ▴';
  }}

  function updateSubmit() {{
    var btn = document.getElementById('q-submit');
    if (!btn) return;
    btn.disabled = !(quoteSel.agree && quoteSel.privacy && quoteAddr);
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
    // 추가115 (핸드오프③) — 제출 전 오조작 복구: 뒤로가기/재선택
    if (s.done || s.busy) {{
      h += '<button class="qs-skip" style="margin-top:10px;width:100%" onclick="surveyReset()">↺ 다시 선택할래요</button>';
    }} else if (s.asked) {{
      h += '<div style="margin-top:8px;text-align:center"><span onclick="surveyBack()" style="font-size:12.5px;color:var(--t3);cursor:pointer;text-decoration:underline">← 이전으로</span></div>';
    }}
    b.innerHTML = h;
  }}
  function surveyReset() {{
    quoteSurvey = {{ asked:false, busy:false, source:null, keyword:'', category:null, etc:'', done:false }};
    renderSurvey();
  }}
  function surveyBack() {{
    var s = quoteSurvey;
    if (s.done) {{ s.done = false; if (s.etc) {{ s.etc = ''; }} else if (s.category) {{ s.category = null; }} }}
    else if (s.etc) {{ s.etc = ''; }}
    else if (s.category) {{ s.category = null; }}
    else if (s.keyword) {{ s.keyword = ''; }}
    else if (s.source) {{ s.source = null; }}
    else if (s.asked) {{ s.asked = false; }}
    renderSurvey();
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
    if (!quoteSel.privacy) {{ alert('개인정보 수집·이용 동의에 체크해주세요'); return; }}
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
      privacy_agreed: quoteSel.privacy === true,  // 추가98 — 고객 동의 영수증용
    }};
    try {{
      var resp = await fetch('/api/intake-form/submit', {{
        method: 'POST',
        headers: {{ 'Content-Type': 'application/json' }},
        body: JSON.stringify(payload),
      }});
      if (resp.ok) {{
        // 추가60 (2026-06-25) — 완료 화면 = 안내문 + [확인!] 버튼 (자연 종료)
        document.querySelector('.q-scroll').innerHTML =
          '<div class="status-page"><h2 style="color:#16C172">✅ 접수 완료!</h2>'
          + '<p>시공접수서를 제출했어요.<br><br>'
          + '사장님이 확인하면 <b>"확인했어요" 알림 문자</b>가<br>'
          + '자동으로 전송돼요. 작성 후 잠시만 기다려주시면 돼요 😊</p>'
          + '<button class="q-submit" style="max-width:220px;margin:22px auto 0" onclick="closeIntake()">확인!</button></div>';
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

  // 추가60 (2026-06-25) — 완료 화면의 [확인!] 버튼 핸들러.
  // 안내 메시지 보여주고 best-effort 닫기. 모바일에선 window.close() 가 막혀도
  // "잠시만 기다려주세요" 메시지로 자연스럽게 끝나게.
  function closeIntake() {{
    document.querySelector('.q-scroll').innerHTML =
      '<div class="status-page"><h2>네! 조금만 기다려주세요 😊</h2>'
      + '<p>접수서 창을 종료할게요!</p></div>';
    setTimeout(function() {{
      try {{ window.open('', '_self'); window.close(); }} catch(e) {{}}
    }}, 1200);
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
                       deposit_ratio_pct: Optional[int], total_won: int = 0) -> str:
    """계약금 안내 박스 HTML (프로토 q-deposit 1:1).

    추가115 (핸드오프②) — 접수서는 계약금 이미 받은 뒤 작성 흐름.
    "입금 계좌 확정 후 안내"(모순) → "계약금 받음 · 시공 종료 후 잔금(총액−계약금) 입금".
    """
    if deposit_mode == "none" or not deposit_amount_krw:
        return ""
    amount = _format_won(deposit_amount_krw)
    suffix = ""
    if deposit_mode == "ratio" and deposit_ratio_pct:
        suffix = f" (총액의 {int(deposit_ratio_pct)}%)"
    remain = max(int(total_won or 0) - int(deposit_amount_krw or 0), 0)
    if total_won and remain > 0:
        tail = f' · 시공 종료 후 잔금 {_format_won(remain)}원을 입금해주시면 됩니다'
    else:
        tail = ' · 시공 종료 후 잔금을 입금해주시면 됩니다'
    return f'<div class="q-deposit">계약금 {amount}원{suffix}{tail}</div>'


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
        # 추가101 (핸드오프 ④) — 확인용 영수증 (읽기전용, 만료 무관)
        return HTMLResponse(content=_render_intake_receipt_html(data))
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
        data["deposit_mode"], data["deposit_amount_krw"], data["deposit_ratio_pct"],
        int(data.get("total_man") or 0) * 10000,  # 추가115 — 잔금 동적
    )

    page = INTAKE_FORM_HTML_TEMPLATE.format(
        biz_html=_html.escape(biz),
        biz_js=json.dumps(biz, ensure_ascii=False),
        schedule_label_html=_html.escape(schedule_label),
        items_html=items_html,
        total_man_html=_html.escape(str(int(data["total_man"] or 0))),
        deposit_html=deposit_html,
        token_js=_html.escape(token, quote=True),
        phone_html=_html.escape(_fmt_phone_dashed(data["phone"]), quote=True),
        owner_memo_html=_build_owner_memo_html(data.get("owner_memo")),  # 추가95③
        vat_label_html=_vat_label(data.get("vat_included")),  # 추가102
    )
    return HTMLResponse(content=page)


def _vat_label(vat_included) -> str:
    """추가102 — 부가세 별도/포함 한 줄 표기 (사장님 2026-07-07: 분쟁 방지 명시).

    앱 견적서(QuoteDocScreen) 규칙과 일치:
      별도(false): 합계 = 공급가 + VAT(공급가×10%) → 표기 "부가세 별도 (공급가의 10% 별도)"
      포함(true):  합계 = 총액 (VAT 역산 포함)     → 표기 "부가세 포함"
    NULL(옛 발급분) = 별도 취급 (기존 폼 하드코딩과 동일).
    """
    return "부가세 포함" if vat_included else "부가세 별도 (공급가의 10% 별도)"


def _build_owner_memo_html(memo) -> str:
    """추가95③ — 사장님 특이사항 메모 카드 (있을 때만, 프로토 카드 톤)."""
    import html as _html
    m = (memo or "").strip()
    if not m:
        return ""
    return (
        '\n    <div class="q-card">'
        '\n      <div class="q-card-h"><span class="q-step">📌</span>사장님 특이사항</div>'
        '\n      <div style="padding:4px 2px; font-size:14.5px; line-height:1.6; '
        'color:#0B0F19; white-space:pre-wrap;">' + _html.escape(m) + "</div>"
        "\n    </div>"
    )


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
    ownerMemo: str = ""                                # 추가95③ — 특이사항 메모 (폼에 표시)
    vatIncluded: bool = False                          # 추가102 — 부가세 포함(true)/별도(false). 앱 default false


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
    privacyAgreed: bool = Field(                       # 추가98 — 고객 개인정보 동의
        default=False,
        validation_alias=AliasChoices("privacyAgreed", "privacy_agreed"),
    )


# ─── helper ───

def _workdate_to_epoch_ms(year: int, month: int, day: int) -> int:
    """프로토 quoteCfg qyear/qmon/qday → KST 0시 epoch ms.

    year=0 또는 month=0 면 0 반환 (시공일 미정).
    """
    if not month or not day:
        return 0
    import datetime
    # 추가95 (2026-07-06, P0 날짜 -1 fix) — 옛 코드는 naive dt 에서 손으로 -9h 한 뒤
    # .timestamp() 가 서버 로컬(KST)로 해석하며 또 -9h 효과 → 이중 보정으로 전날로 밀림.
    # tzinfo=KST 명시 = 서버 TZ 무관하게 항상 정확한 KST 자정.
    try:
        dt = datetime.datetime(year or 2026, month, day, 0, 0, 0, tzinfo=_KST)
        return int(dt.timestamp() * 1000)
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
                confirmed_date_iso, survey_json, owner_memo, vat_included
            ) VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?,
                      ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?)
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
                (req.ownerMemo or "").strip()[:300] or None,  # 추가95③
                1 if req.vatIncluded else 0,  # 추가102 — 부가세 포함/별도
            ),
        )
        con.commit()


# ============================================================================
# 추가99 — 기존 접수서 날짜 -9h 보정 (추가95 P0 fix 의 과거 데이터 마이그레이션)
# work_year/month/day (사장님이 고른 원본 값) 기준으로 재계산 →
# 정확히 -9시간 밀린 행만 보정. dry-run(기본) → apply=true 2단계.
# ============================================================================

@app.post("/api/admin/stats/purge-test-usage")
async def admin_purge_test_usage(
    payload: Optional[dict] = None,
    authorization: Optional[str] = Header(default=None),
) -> dict:
    """추가112 — 배포/회귀 테스트가 남긴 합성 번호의 사용 기록 청소.

    api_usage + llm_usage_log(있으면) 에서 TEST_SYNTHETIC_PHONES 행 삭제.
    {apply:false}=미리보기(건수만) / {apply:true}=실제 삭제. 실 고객 데이터는 안 건드림.
    """
    _admin_auth_bearer_from_header(authorization)
    apply = bool((payload or {}).get("apply"))
    phones = sorted(TEST_SYNTHETIC_PHONES)
    ph_ph = ",".join("?" * len(phones))
    result = {}
    with db_conn() as con:
        n_api = con.execute(
            f"SELECT COUNT(*) FROM api_usage WHERE phone IN ({ph_ph})", phones
        ).fetchone()[0]
        result["api_usage"] = n_api
        # llm_usage_log 는 phone 컬럼이 있을 때만
        try:
            n_llm = con.execute(
                f"SELECT COUNT(*) FROM llm_usage_log WHERE phone IN ({ph_ph})", phones
            ).fetchone()[0]
            result["llm_usage_log"] = n_llm
        except sqlite3.OperationalError:
            result["llm_usage_log"] = None
        if apply:
            con.execute(f"DELETE FROM api_usage WHERE phone IN ({ph_ph})", phones)
            if result.get("llm_usage_log") is not None:
                con.execute(f"DELETE FROM llm_usage_log WHERE phone IN ({ph_ph})", phones)
            con.commit()
    print(f"[stats/purge-test] apply={apply} phones={len(phones)} rows={result}")
    return {"apply": apply, "phones": phones, "counts": result,
            "note": "apply=false 는 미리보기. TEST_SYNTHETIC_PHONES 만 삭제 (실 고객 무관)."}


@app.post("/api/admin/intake/fix-dates")
async def admin_intake_fix_dates(
    payload: Optional[dict] = None,
    authorization: Optional[str] = Header(default=None),
) -> dict:
    _admin_auth_bearer_from_header(authorization)
    apply = bool((payload or {}).get("apply"))
    nine_h = 9 * 3600 * 1000
    fixed = []
    with db_conn() as con:
        rows = con.execute(
            "SELECT token, work_year, work_month, work_day, scheduled_at_ms "
            "FROM intake_forms "
            "WHERE work_month IS NOT NULL AND work_day IS NOT NULL AND scheduled_at_ms > 0"
        ).fetchall()
        for tok, wy, wm, wd, cur in rows:
            expected = _workdate_to_epoch_ms(wy or 2026, wm, wd)
            if expected and cur == expected - nine_h:
                fixed.append({
                    "token": tok,
                    "workDate": f"{wy or 2026}-{wm:02d}-{wd:02d}",
                    "before_ms": cur,
                    "after_ms": expected,
                })
                if apply:
                    con.execute(
                        "UPDATE intake_forms SET scheduled_at_ms = ? WHERE token = ?",
                        (expected, tok),
                    )
        if apply:
            con.commit()
    print(f"[intake/fix-dates] apply={apply} 대상={len(fixed)}건")
    return {"apply": apply, "count": len(fixed), "items": fixed[:100],
            "note": "apply=false 는 미리보기만. 원본(work_*) 기준 재계산이라 정확히 -9h 인 행만 보정."}


# ─── API 1: POST /api/quote/issue ───

@app.post("/api/quote/issue")
async def quote_issue(req: QuoteIssueRequest) -> dict:
    """접수서 토큰 발급. 사장님이 채팅 견적 시트에서 [시공접수서 보내기] 누름 → 앱이 호출.

    응답: {token, url, issuedAtMs, expiresAtMs, smsDraft}
    smsDraft = SMS 본문 prefill 용 한국어 문구 (자동발송 X, 사장님 ▶ 직접).
    """
    # 추가47 (2026-06-21) — 옛 빌드도 last_seen 잡힘 (devicePhone = 사장님 phone)
    _touch_beta_whitelist(req.devicePhone)
    _check_free_trial_gate(req.devicePhone)  # 추가96 — 접수서·견적서 발급 잠금 (스위치 OFF 기본)
    if not (req.customerPhone or "").strip():
        raise HTTPException(400, "customerPhone 필수")
    if req.depositMode not in ("none", "ratio", "fixed"):
        raise HTTPException(400, "depositMode 는 none/ratio/fixed")
    if req.workMonth and not (1 <= req.workMonth <= 12):
        raise HTTPException(400, "workMonth 1~12")
    if req.workDay and not (1 <= req.workDay <= 31):
        raise HTTPException(400, "workDay 1~31")

    now = _now_ms()
    # 추가95② — 재발행 upsert: 같은 고객(owner+customer)의 아직 미제출 접수서가 있으면
    # 같은 링크(token) 재사용 + 내용만 갱신. 제출된 폼은 매칭 안 됨 (제출본 보호).
    reused = False
    _row_reuse = None
    if req.devicePhone:
        with db_conn() as con:
            _row_reuse = con.execute(
                "SELECT token FROM intake_forms WHERE owner_phone = ? AND phone = ? "
                "AND submitted_at_ms IS NULL ORDER BY created_at_ms DESC LIMIT 1",
                (req.devicePhone, req.customerPhone),
            ).fetchone()
    if _row_reuse:
        token = _row_reuse[0]
        reused = True
        with db_conn() as con:  # 같은 token 으로 재-INSERT (=같은 URL) 위해 옛 행 삭제
            con.execute("DELETE FROM intake_forms WHERE token = ?", (token,))
            con.commit()
    else:
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
    print(f"[quote/issue] customerPhone={req.customerPhone} → token={token} url={url}"
          + (" (재발행 — 기존 링크 재사용)" if reused else ""))
    return {
        "token": token,
        "url": url,
        "issuedAtMs": now,
        "expiresAtMs": expires_at,
        "smsDraft": sms_draft,
        "reused": reused,  # 추가95② — 앱이 "기존 링크가 갱신됐어요" 안내용
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
        data["deposit_mode"], data["deposit_amount_krw"], data["deposit_ratio_pct"],
        int(data.get("total_man") or 0) * 10000,  # 추가115 — 잔금 동적
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
                phone_html=_html.escape(_fmt_phone_dashed(data["phone"]), quote=True),
                owner_memo_html=_build_owner_memo_html(data.get("owner_memo")),  # 추가95③
                vat_label_html=_vat_label(data.get("vat_included")),  # 추가102
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


def _render_intake_receipt_html(data: dict) -> str:
    """추가101 (핸드오프 ④) — 제출 완료된 접수서 재방문 = 확인용 영수증 (읽기전용).

    고객·사장님 둘 다 링크를 다시 눌러 내용을 확인. 편집·재제출 없음. 만료돼도 표시.
    내용: 시공일 · 견적 항목·합계 · 계약금/잔금 · 고객 제출 주소/동·호/메모 · 사장님 특이사항.
    """
    import datetime
    import html as _html
    biz = _html.escape((data.get("biz_name") or "").strip() or "RING-GO 시공")
    schedule_label = _html.escape(
        _format_schedule_label(data.get("scheduled_at_ms") or 0, data.get("scheduled_days") or 1))
    payload = data.get("payload") or {}

    # 제출 시각 (KST)
    submitted_label = ""
    if data.get("submitted_at_ms"):
        _dt = (datetime.datetime.utcfromtimestamp(data["submitted_at_ms"] / 1000)
               + datetime.timedelta(hours=9))
        submitted_label = f"{_dt.year}. {_dt.month}. {_dt.day}. {_dt.hour:02d}:{_dt.minute:02d} 접수"

    # 견적 항목
    items = data.get("estimate_items") or []
    item_rows = []
    for it in items:
        name = _html.escape(str(it.get("name") or ""))
        price = int(it.get("price_man") or 0)
        unit = ""
        if (it.get("unit") == "pyeong") and it.get("area"):
            unit = f" <span style='color:#9AA3AF;font-size:12.5px'>({price}만원/평 × {it.get('area')}평)</span>"
        item_rows.append(
            "<div style='display:flex;justify-content:space-between;padding:9px 0;"
            "border-bottom:1px solid #F1F3F6;font-size:14.5px'>"
            f"<span style='color:#333D4B'>{name}{unit}</span>"
            f"<span style='font-weight:700;color:#0B0F19'>{price}만원</span></div>")
    if not item_rows:
        item_rows.append("<div style='padding:9px 0;color:#9AA3AF;font-size:14px'>견적 항목이 등록되지 않았어요.</div>")
    total_man = int(data.get("total_man") or 0)
    total_won = total_man * 10000

    # 계약금 / 잔금
    deposit_html = ""
    dep = int(data.get("deposit_amount_krw") or 0)
    if (data.get("deposit_mode") or "none") != "none" and dep > 0:
        suffix = ""
        if data.get("deposit_mode") == "ratio" and data.get("deposit_ratio_pct"):
            suffix = f" (총액의 {int(data['deposit_ratio_pct'])}%)"
        remain = max(total_won - dep, 0)
        deposit_html = (
            "<div style='display:flex;justify-content:space-between;padding:9px 0;font-size:14px;color:#333D4B'>"
            f"<span>계약금{suffix}</span><span style='font-weight:700'>{_format_won(dep)}원</span></div>"
            "<div style='display:flex;justify-content:space-between;padding:2px 0 4px;font-size:14px;color:#333D4B'>"
            f"<span>잔금</span><span style='font-weight:700'>{_format_won(remain)}원</span></div>")

    def _row(label: str, value: str) -> str:
        return ("<div style='display:flex;gap:12px;padding:8px 0;border-bottom:1px solid #F1F3F6;font-size:14.5px'>"
                f"<span style='width:88px;flex:none;color:#8B95A1'>{label}</span>"
                f"<span style='color:#0B0F19;white-space:pre-wrap'>{value}</span></div>")

    # 고객 제출 정보
    cust_rows = []
    _phone = (payload.get("phone") or payload.get("contact_phone") or data.get("phone") or "")
    if _phone:
        cust_rows.append(_row("연락처", _html.escape(_fmt_phone_dashed(_phone))))
    _addr = (payload.get("address") or payload.get("road_address") or "").strip()
    _dong = (payload.get("dong") or payload.get("building_detail") or "").strip()
    if _addr:
        cust_rows.append(_row("시공 주소", _html.escape(_addr + (f" {_dong}" if _dong else ""))))
    if (payload.get("confirmedDate") or "").strip():
        cust_rows.append(_row("확인 시공일", _html.escape(payload["confirmedDate"].strip())))
    if (payload.get("memo") or "").strip():
        cust_rows.append(_row("남기신 메모", _html.escape(payload["memo"].strip())))

    # 사장님 특이사항
    owner_memo_html = ""
    _om = (data.get("owner_memo") or "").strip()
    if _om:
        owner_memo_html = (
            "<div class='card'><div class='card-h'>📌 사장님 특이사항</div>"
            f"<div style='font-size:14.5px;line-height:1.6;color:#0B0F19;white-space:pre-wrap'>{_html.escape(_om)}</div></div>")

    return f"""<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>접수 완료 — {biz}</title>
<style>
  body {{ font-family:-apple-system,'Pretendard',sans-serif; background:#F4F5F7; color:#0B0F19;
         margin:0; padding:20px 16px 40px; }}
  .wrap {{ max-width:480px; margin:0 auto; }}
  .done {{ background:#fff; border-radius:16px; padding:26px 20px; text-align:center;
          box-shadow:0 1px 3px rgba(0,0,0,.05); }}
  .done .ic {{ font-size:40px; }}
  .done h1 {{ font-size:18px; font-weight:800; margin:10px 0 4px; color:#0B0F19; }}
  .done p {{ font-size:13px; color:#8B95A1; margin:0; }}
  .card {{ background:#fff; border-radius:16px; padding:18px 20px; margin-top:12px;
          box-shadow:0 1px 3px rgba(0,0,0,.05); }}
  .card-h {{ font-size:14px; font-weight:800; color:#3182F6; margin-bottom:8px; }}
  .total {{ display:flex; justify-content:space-between; padding:12px 0 2px; font-size:15px; font-weight:800; }}
  .foot {{ text-align:center; font-size:12px; color:#9AA3AF; margin-top:20px; line-height:1.6; }}
</style></head><body><div class="wrap">
  <div class="done"><div class="ic">✅</div>
    <h1>접수가 정상적으로 완료되었습니다</h1>
    <p>{biz} · {submitted_label}</p>
  </div>
  <div class="card"><div class="card-h">🗓️ 시공 예정일</div>
    <div style="font-size:16px;font-weight:800">{schedule_label}</div>
  </div>
  <div class="card"><div class="card-h">📋 견적 내용</div>
    {''.join(item_rows)}
    <div class="total"><span>합계</span><span style="color:#3182F6">{_format_won(total_won)}원</span></div>
    <div style="text-align:right;font-size:12px;color:#9AA3AF;margin-top:2px">{_vat_label(data.get("vat_included"))}</div>
    {deposit_html}
  </div>
  {("<div class='card'><div class='card-h'>🙋 접수하신 정보</div>" + ''.join(cust_rows) + "</div>") if cust_rows else ""}
  {owner_memo_html}
  <div class="foot">이 페이지는 확인용이에요. 내용 수정이 필요하면 사장님께 연락해 주세요.</div>
</div></body></html>"""


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
        # 추가101 (핸드오프 ④) — 확인용 영수증 (읽기전용, 만료 무관)
        return HTMLResponse(content=_render_intake_receipt_html(data))
    if now > data["expires_at_ms"]:
        return _quote_status_page("⌛ 만료된 링크",
                                  "이 접수서 링크는 만료되었어요. 사장님께 새 링크를 요청해 주세요.", 410)
    return HTMLResponse(content=_render_quote_form_html(token, row))


@app.get("/q/{token}/privacy", response_class=HTMLResponse)
async def quote_owner_privacy(token: str) -> HTMLResponse:
    """추가116 (B1) — 접수서 발행 업체(사장님)별 개인정보 처리방침.

    개인정보처리자=업체 명의의 표준 처리방침을 상호·연락처 치환해 렌더.
    (막내컴퍼니=수탁자 구조. 법률 사전점검 반영본. 전문가 확정 전 draft.)
    """
    import html as _html
    with db_conn() as con:
        row = con.execute(
            f"SELECT {_INTAKE_SELECT_COLS} FROM intake_forms WHERE token = ?", (token,)
        ).fetchone()
    if not row:
        return _quote_status_page("❌ 유효하지 않은 링크", "사장님께 다시 링크를 받아 주세요.", 404)
    data = _intake_row_to_dict(row)
    biz = _html.escape((data.get("biz_name") or "").strip() or "시공업체")
    _eff_date = "2026. 7. 11"   # 처리방침 시행일 (버전 표기용)
    css = ("body{font-family:'Pretendard',-apple-system,system-ui,sans-serif;background:#FAFBFC;"
           "color:#0B0F19;line-height:1.7;margin:0;padding:0 16px 60px}.w{max-width:680px;margin:0 auto;padding-top:32px}"
           "h1{font-size:22px;font-weight:800}h2{font-size:16px;font-weight:800;margin:24px 0 8px;color:#1B64DA}"
           "p,li{font-size:14px;color:#333D4B}.tag{display:inline-block;background:#EEF4FF;color:#1B64DA;font-size:12px;"
           "font-weight:700;border-radius:6px;padding:3px 9px;margin-bottom:10px}"
           ".muted{font-size:12px;color:#9AA3AF}a{color:#1B64DA;font-weight:700}")
    html = (
        f'<!doctype html><html lang="ko"><head><meta charset="utf-8">'
        f'<meta name="viewport" content="width=device-width,initial-scale=1">'
        f'<title>{biz} 개인정보 처리방침</title>'
        f'<meta name="robots" content="noindex">'
        f'<link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css" rel="stylesheet">'
        f'<style>{css}</style></head><body><div class="w">'
        f'<span class="tag">시공접수서 개인정보 처리방침</span>'
        f'<h1>{biz} 개인정보 처리방침</h1>'
        f'<p class="muted">본 방침은 <b>{biz}</b>(개인정보처리자)이 시공접수서를 통해 수집하는 고객 개인정보에 적용됩니다. '
        f'앱 운영사 막내컴퍼니가 수탁자로서 시공막내 서버에 보관·처리합니다.</p>'
        f'<h2>1. 수집 항목 및 목적</h2>'
        f'<p>휴대전화 번호, 현장 주소, 현장 메모를 <b>시공 접수·상담 연락·일정 확정</b> 목적으로 수집·이용합니다.</p>'
        f'<h2>2. 보유 기간 및 파기 절차·방법</h2>'
        f'<p>시공 완료일부터 1년(시공이 진행되지 않은 경우 접수일부터 6개월)이 지난 때 지체 없이 파기합니다. '
        f'고객 또는 {biz}의 삭제 요청 시 즉시 파기합니다. '
        f'보유 기간이 지난 개인정보는 복구할 수 없는 방법으로 서버에서 완전 삭제(전자적 파일은 영구 삭제)합니다.</p>'
        f'<h2>3. 처리 위탁 (수탁자)</h2>'
        f'<p>{biz}은(는) 개인정보 처리를 <b>막내컴퍼니</b>에 위탁합니다(개인정보 보호법 제26조). '
        f'막내컴퍼니는 위탁 목적 외로 이 정보를 이용하지 않으며, 시공막내 앱·서버로 안전하게 보관·처리합니다. '
        f'접수서로 수집된 정보는 AI 자동응대에 사용하지 않습니다.</p>'
        f'<h2>4. 개인정보의 안전성 확보조치</h2>'
        f'<p>이 정보는 수탁자 막내컴퍼니의 시스템에서 전송구간 암호화(TLS), 접근 권한 통제, '
        f'접속기록 보관(1년 이상), 악성프로그램 방지 등의 조치로 보호됩니다.</p>'
        f'<h2>5. 정보주체의 권리</h2>'
        f'<p>고객은 접수를 받은 {biz} 또는 막내컴퍼니(hello@si0in.kr)에 열람·정정·삭제·처리정지를 요구할 수 있습니다.</p>'
        f'<h2>6. 권익침해 구제방법</h2>'
        f'<p>개인정보 침해로 인한 분쟁 조정·상담이 필요하면 개인정보분쟁조정위원회(1833-6972), '
        f'개인정보침해신고센터(118)에 신청할 수 있습니다.</p>'
        f'<h2>7. 자동 수집 사항</h2>'
        f'<p>접수서 웹페이지는 서비스 제공에 필요한 최소한의 접속 기록 외에 쿠키 등 자동 수집 장치를 사용하지 않습니다.</p>'
        f'<h2>8. 문의 연락처</h2>'
        f'<p>이 접수서 처리에 관한 문의는 <b>{biz}</b>(접수서를 보내드린 연락처) 또는 '
        f'수탁자 막내컴퍼니(hello@si0in.kr)로 해주시면 됩니다.</p>'
        f'<h2>9. 처리방침의 변경</h2>'
        f'<p>이 방침이 변경되는 경우 이 페이지에 시행일과 함께 게시합니다. (현재 버전 시행일: {_eff_date})</p>'
        f'<p style="margin-top:24px"><a href="/privacy">시공막내(막내컴퍼니) 개인정보 처리방침 전문 →</a></p>'
        f'<p class="muted" style="margin-top:20px">※ 본 방침은 초안이며 정식 출시 전 전문가 검토를 거쳐 확정됩니다.</p>'
        f'</div></body></html>'
    )
    return HTMLResponse(content=html)


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
    if req.privacyAgreed:
        _record_intake_consent(phone)  # 추가98 — 고객 동의 영수증
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
    # 추가47 (2026-06-21) — 옛 빌드도 last_seen 잡힘. devicePhone = 사장님 본인 phone.
    _touch_beta_whitelist(devicePhone)
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
        # 추가45 (2026-06-21) — sinceMs 필터 정정. issued_at_ms 만 보면 옛 발급 + 최근 제출 누락.
        # 안드로이드는 sinceMs 에 마지막 본 submittedAtMs 최댓값을 넣음 → submitted_at_ms 기준이 맞음.
        # 두 가지 다 OR — 새로 발급된 미제출 폼 + 옛 발급 새 제출 둘 다 잡힘.
        where_parts.append("(issued_at_ms > ? OR submitted_at_ms > ?)")
        params.extend([sinceMs, sinceMs])
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
        base = _intake_row_to_dict(r[:20])  # 추가102 — vat_included 포함 20 컬럼
        # 새 컬럼 12 개 (work_month..survey_json)
        (wm, wd, wy, dv, bo, bn, ba, bp, bs, bvd, cdi, sj) = r[20:32]
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
            "ownerMemo": base.get("owner_memo") or "",  # 추가95③ — 앱 타임라인 카드용
            "vatIncluded": bool(base.get("vat_included")),  # 추가102 — 앱 이력 카드용
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
        <td colspan="2">합계 ({vat_doc_label_html})</td>
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
    base = _intake_row_to_dict(row[:20])  # 추가102 — vat_included 포함 20 컬럼
    (biz_owner, biz_no, biz_addr, biz_phone, biz_seal) = row[20:25]

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
        vat_doc_label_html=("부가세 포함" if base.get("vat_included") else "부가세 별도"),  # 추가102
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
TEAM_TIER_NAMES = {"team_99k", "team", "team_99000",
                   "premium_100k"}  # 추가84 — 특별 사장님(10만) = 팀/협업 포함. 어느 식별자든 통과


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


def _touch_beta_whitelist(phone: Optional[str], owner_trade: Optional[str] = None) -> None:
    """추가41 (2026-06-20) — 폴링 endpoint 들이 부르는 가벼운 heartbeat.
    추가50 (2026-06-21) — owner_trade 받으면 같이 저장 (앱 onboarding 업종).

    사장님 의도: "앱 켜기만 해도 활동" — 협업 화면 들어가서 폴링만 해도 "최근 앱 실행"
    갱신되게.

    동작:
    - phone 이 beta_whitelist 에 있으면 last_seen_ms 만 갱신 (use_count 는 X — 그건 게이트 통과 카운트)
    - first_seen_ms 가 NULL 이면 함께 박음 (지금까지 미진입이었으면 첫 진입으로 기록)
    - 화이트리스트에 없으면 무시 (graceful, 가드 아님)
    - 빈 phone 도 skip

    by-me / with-me / owner-events 같은 폴링 endpoint 진입에 박는다.
    """
    if not phone:
        return
    phone_digits = _norm_phone(phone)
    if not phone_digits:
        return
    now = _now_ms()
    trade_clean = (owner_trade or "").strip()[:30] if owner_trade else ""
    try:
        with db_conn() as con:
            if trade_clean:
                # 추가50 — owner_trade 도 같이 (가장 최근 값으로 덮어쓰기)
                con.execute(
                    """
                    UPDATE beta_whitelist
                    SET first_seen_ms = COALESCE(first_seen_ms, ?),
                        last_seen_ms = ?,
                        owner_trade = ?
                    WHERE phone = ?
                    """,
                    (now, now, trade_clean, phone_digits),
                )
            else:
                con.execute(
                    """
                    UPDATE beta_whitelist
                    SET first_seen_ms = COALESCE(first_seen_ms, ?),
                        last_seen_ms = ?
                    WHERE phone = ?
                    """,
                    (now, now, phone_digits),
                )
    except Exception:
        # 가벼운 heartbeat — 실패해도 본 endpoint 동작 막으면 안 됨
        pass


def _ensure_and_touch_beta_whitelist(phone: Optional[str], owner_trade: Optional[str] = None) -> None:
    """추가42 (2026-06-20) — 가드 + heartbeat 동시. 추가50 (2026-06-21) — owner_trade 도 같이.

    사장님 보고: "협업 화면 들어가야 갱신되면 안 됨. 앱 켜기만 해도 활동."
    → 모든 owner_phone 받는 endpoint 진입에 이거 한 줄 박으면
       (1) 가드 통과 검증 + (2) last_seen_ms 자동 갱신 + (3) owner_trade 저장.
    """
    _ensure_beta_whitelist(phone)
    _touch_beta_whitelist(phone, owner_trade=owner_trade)


def _ensure_beta_whitelist(phone: Optional[str]) -> None:
    """추가36 (2026-06-18) — beta_whitelist 게이트.

    사장님 보고: "화이트리스트에 체크하지 않은 번호가 어플 로그인 가능".
    원인: `/api/beta/check` 는 정상이지만 다른 핵심 endpoint 들이 phone 만 받고
    화이트리스트 확인 안 함 → 앱이 게이트 우회 시 모든 기능 사용 가능.
    대응: 핵심 endpoint 진입에 이 helper 호출 → 미등록 phone 이면 403.

    개발용 우회: ENV `BETA_WHITELIST_BYPASS=1` 이면 무조건 통과 (테스트 편의).
    빈 phone 은 skip — 해당 endpoint 가 phone 필수면 자체 검증으로 거부, 아니면 그대로 진행.
    """
    if os.environ.get("BETA_WHITELIST_BYPASS") == "1":
        return
    if not phone:
        return  # phone 없으면 다른 검증에 맡김
    phone_digits = _norm_phone(phone)
    if not phone_digits:
        return
    with db_conn() as con:
        row = con.execute(
            "SELECT 1 FROM beta_whitelist WHERE phone = ?",
            (phone_digits,),
        ).fetchone()
    if not row:
        print(f"[beta_whitelist_guard] BLOCK {phone_digits} (미등록)")
        raise HTTPException(
            403,
            "베타 등록되지 않은 번호입니다. 사장님께 문의해주세요."
        )


def _check_team_tier(owner_phone: str) -> None:
    """subscribers 의 plan_tier 가 team_99k 인지 검증. 미가입은 403.

    개발용 우회: ENV `TEAM_TIER_BYPASS=1` 이면 무조건 통과 (사장님 테스트 편의).
    추가76 (2026-07-02) — 베타 화이트리스트 사장님은 코드 레벨에서 통과.
      사고: launchd plist 재설치/재기동 시 TEAM_TIER_BYPASS env 소실 →
            베타 사장님 협업 invite 가 "갑자기" 403 (사장님 보고: 일당 공유 실패).
      env 의존 제거 — beta_whitelist 등록이면 team tier 게이트 skip.
      정식 출시 시 이 블록 제거하면 유료(99k) 게이트 복원.
      + 모든 403 에 print 로그 (다음 사고 시 stderr.log 로 원인 즉시 파악).
    """
    if os.environ.get("TEAM_TIER_BYPASS") == "1":
        return
    if not owner_phone:
        raise HTTPException(403, "owner_phone 필수")
    phone_digits = _norm_phone(owner_phone) or owner_phone
    with db_conn() as con:
        beta = con.execute(
            "SELECT 1 FROM beta_whitelist WHERE phone = ?",
            (phone_digits,),
        ).fetchone()
        if beta:
            return  # 베타 사장님 = team tier 통과 (추가76)
        row = con.execute(
            "SELECT plan_tier, churned_at_ms FROM subscribers WHERE phone = ?",
            (owner_phone,),
        ).fetchone()
    if not row:
        print(f"[team_tier_guard] BLOCK {phone_digits} (subscribers 미등록)")
        raise HTTPException(
            403,
            "subscribers 에 등록되지 않은 사장님입니다. 99k 가입 후 다시 시도해 주세요."
        )
    plan_tier, churned_at = row
    if churned_at is not None:
        print(f"[team_tier_guard] BLOCK {phone_digits} (구독 해지, churned_at={churned_at})")
        raise HTTPException(403, "구독이 해지된 사장님입니다.")
    if plan_tier not in TEAM_TIER_NAMES:
        print(f"[team_tier_guard] BLOCK {phone_digits} (tier={plan_tier})")
        raise HTTPException(403, f"99k(팀) 요금제 필요. 현재 tier: {plan_tier}")


# 추가29 (2026-06-15) — 영구 링크 결정 확정 (사장님): 팀원당 stable 토큰 1개, 만료 없음.
# 제외/퇴사는 team_members.removed_at_ms 로 차단 (만료 대신).
# expires_at_ms 컬럼은 그대로 유지하되 매우 큰 값(5138년)으로 통일 → 기존 `expires_at_ms > now`
# 조회 로직 다 그대로 통과. 코드 한 줄 변경으로 영구 링크 효과.
_PERMANENT_LINK_EXPIRY_MS = 99_999_999_999_999  # ≈ 5138-11-16 UTC


def _team_link_expiry_default(scheduled_at_ms: int = 0) -> int:
    """URL 만료 시각 — 추가29 (2026-06-15) 이후 항상 영구.

    이전 동작 (시공 다음날 자정 만료) 은 deprecated. scheduled_at_ms 인자는 호환 위해 유지.
    """
    return _PERMANENT_LINK_EXPIRY_MS


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


class TeamCompleteEventRequest(BaseModel):
    token: str
    completed_at_ms: Optional[int] = None


class TeamNoteEventRequest(BaseModel):
    token: str
    text: str


class TeamPhotoUploadRequest(BaseModel):
    token: str
    label: Optional[str] = None                # '시공 전'|'시공 중'|'시공 후'|'추가 사진'
    image_data_url: Optional[str] = None       # base64 (작은 사진)
    note: Optional[str] = None
    customer_phone: Optional[str] = None       # §25 — 고객 연결 (없으면 schedule_snapshot 자동 매핑 시도)


class OwnerSitePhotoRequest(BaseModel):
    """§25 — 사장님 본인이 현장사진 업로드 (팀원 토큰 없이).

    §F (2026-06-13) — share_id 옵셔널 추가. 협업 현장 사진은 share_id 로 묶임.
    customer_phone 은 협업 현장에선 안전 라벨 또는 빈 값일 수 있음 → §F 에서 미필수화.
    """

    owner_phone: str
    customer_phone: Optional[str] = None       # §F: share_id 만으로도 업로드 가능 (협업 현장).
    image_data_url: str                        # base64 data URL (1MB 컷)
    label: Optional[str] = None
    note: Optional[str] = None
    share_id: Optional[str] = None             # §F: 협업 현장 사진 (shared_sites.share_id)


# ─── API 1: 팀원 초대 (이름 + 전화 + URL 발급) ───
# 프로토 openAddMember/addMemberSubmit 1:1 — 자동발송 X, URL 만 반환 → 앱이 SMS prefill.

@app.post("/api/team/member/invite")
async def team_member_invite(request: Request) -> dict:
    """팀원 추가 + URL 토큰 발급.

    응답: {member_id, name, role, token, url, expires_at_ms, sms_draft}
    sms_draft = 사장님이 SMS 본문 prefill 용 문구 (자동발송 X).

    SYNC 추가28 (2026-06-14): 기존 `req: TeamInviteRequest` 자동 바인딩이
    "There was an error parsing the body" 400 으로 떨어지는 증상 보고됨.
    원인 추적을 위해 **방어적 수동 파싱**으로 전환:
      1. raw body 직접 읽어서 json.loads
      2. TeamInviteRequest(**body) 수동 생성
      3. 실패 시 stdout 에 실제 에러 클래스·메시지 찍고 명확한 detail 반환
    다른 POST endpoint 는 동일 패턴인데 정상 → 이 endpoint 만 이상하면 stdout 로그가 결정타.
    """
    # SYNC 추가28 — 방어적 파싱 (FastAPI 자동 바인딩 우회)
    try:
        raw_body = await request.body()
    except Exception as e:
        print(f"[team/invite] raw body 읽기 실패: {type(e).__name__}: {e}")
        raise HTTPException(400, f"본문 읽기 실패: {type(e).__name__}")
    print(f"[team/invite] raw body bytes={len(raw_body)} ct={request.headers.get('content-type')}")
    if not raw_body:
        raise HTTPException(400, "본문이 비어있습니다")
    try:
        import json as _json
        body_dict = _json.loads(raw_body.decode("utf-8"))
    except Exception as e:
        print(f"[team/invite] JSON decode 실패: {type(e).__name__}: {e} / first 100 bytes: {raw_body[:100]!r}")
        raise HTTPException(400, f"JSON 디코드 실패: {type(e).__name__}: {e}")
    try:
        req = TeamInviteRequest(**body_dict)
    except Exception as e:
        print(f"[team/invite] TeamInviteRequest 변환 실패: {type(e).__name__}: {e} / body_dict={body_dict}")
        raise HTTPException(400, f"필드 검증 실패: {type(e).__name__}: {e}")
    print(f"[team/invite] 파싱 OK owner={req.owner_phone} name={req.name} phone={req.phone} role={req.role}")
    _check_team_tier(req.owner_phone)
    name = (req.name or "").strip()
    phone = (req.phone or "").strip()
    if not name or not phone:
        raise HTTPException(400, "name, phone 필수")
    role = req.role if req.role in ("owner", "worker") else "worker"

    now = _now_ms()
    # SYNC 추가28 (2026-06-14) — UNIQUE 충돌 fix.
    # 기존: removed_at_ms IS NULL 만 SELECT → 제거된 팀원 재등록 시 INSERT → UNIQUE 충돌(500).
    # 수정: removed 여부 무관하게 SELECT → 있으면 UPDATE(이름·role·tint 갱신 + reactivate)
    #       없으면 INSERT (신규).
    with db_conn() as con:
        existing = con.execute(
            "SELECT member_id, removed_at_ms FROM team_members "
            "WHERE owner_phone = ? AND phone = ?",
            (req.owner_phone, phone),
        ).fetchone()
        if existing:
            member_id = existing[0]
            was_removed = existing[1] is not None
            con.execute(
                """
                UPDATE team_members
                SET name = ?, role = ?, tint = ?, removed_at_ms = NULL
                WHERE member_id = ?
                """,
                (name, role, int(req.tint or 0), member_id),
            )
            print(
                f"[team/invite] {'재활성화' if was_removed else '재사용'} "
                f"member={member_id} (이름·role·tint 갱신)"
            )
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
        # 추가29 (2026-06-15) 영구 링크 — 멤버당 토큰 1개 재사용.
        existing_token = con.execute(
            "SELECT token FROM team_member_links WHERE member_id = ? "
            "ORDER BY issued_at_ms ASC LIMIT 1",
            (member_id,),
        ).fetchone()
        if existing_token:
            token = existing_token[0]
            con.execute(
                "UPDATE team_member_links SET expires_at_ms = ? WHERE token = ?",
                (_PERMANENT_LINK_EXPIRY_MS, token),
            )
        else:
            token = _generate_team_token()
            con.execute(
                """
                INSERT INTO team_member_links
                    (token, member_id, owner_phone, issued_at_ms, expires_at_ms,
                     schedule_snapshot_json, last_accessed_ms)
                VALUES (?, ?, ?, ?, ?, NULL, NULL)
                """,
                (token, member_id, req.owner_phone, now, _PERMANENT_LINK_EXPIRY_MS),
            )
        expires_at = _PERMANENT_LINK_EXPIRY_MS
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
    _touch_beta_whitelist(owner_phone)  # 추가47 (2026-06-21) — 옛 빌드도 잡힘
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
        # 활성 토큰 전체 — invite 는 호출마다 새 토큰을 발급(옛 토큰 만료 안 함)하므로
        # 한 팀원이 여러 링크를 갖고 있을 수 있다. 팀원이 어떤 링크를 보고 있든 최신 배정이
        # 보이도록 활성 토큰 '전부'에 snapshot 을 박는다. (예전엔 최신 1개만 갱신 → 팀원이
        # 옛 링크를 열고 있으면 배정·사진매핑이 안 떴음.)
        rows = con.execute(
            "SELECT token FROM team_member_links WHERE member_id = ? "
            "AND expires_at_ms > ? ORDER BY issued_at_ms DESC",
            (req.member_id, _now_ms()),
        ).fetchall()
        if not rows:
            raise HTTPException(404, "활성 토큰 없음 — invite 또는 refresh-link 호출")
        tokens = [r[0] for r in rows]
        # 첫 item 의 scheduled_at_ms → 만료 자동 갱신 (시공 다음날 자정)
        scheduled_at_ms = 0
        for it in (req.items or []):
            v = it.get("scheduled_at_ms") if isinstance(it, dict) else None
            if v and (not scheduled_at_ms or v > scheduled_at_ms):
                scheduled_at_ms = int(v)
        new_expiry = _team_link_expiry_default(scheduled_at_ms)
        snap_str = json.dumps(req.items, ensure_ascii=False)
        con.executemany(
            "UPDATE team_member_links SET schedule_snapshot_json = ?, expires_at_ms = ? "
            "WHERE token = ?",
            [(snap_str, new_expiry, t) for t in tokens],
        )
        con.commit()
    return {"ok": True, "token": tokens[0], "tokens_updated": len(tokens),
            "expires_at_ms": new_expiry, "items_count": len(req.items or [])}


# ─── API 5: 사장님 polling — 팀원 이벤트 (출발/사진/도착) ───

@app.get("/api/team/events")
async def team_events_list(owner_phone: str, since_ms: int = 0, limit: int = 30) -> dict:
    """팀원이 발생시킨 이벤트들 (출발/사진/도착) 시간순.

    응답: {events: [{event_id, member_id, member_name, event_type, payload, created_at_ms}]}
    """
    if not owner_phone:
        raise HTTPException(400, "owner_phone 필수")
    _touch_beta_whitelist(owner_phone)  # 추가47 (2026-06-21) — 옛 빌드도 잡힘
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
                    if today.get("customer_phone"):
                        payload["customer_phone"] = today.get("customer_phone")
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
    """팀원이 현장 도착. 출발과 같은 패턴 — 사장님 알림에 현장명 넣게 snapshot 에서 끌어옴."""
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
        arrived_at = int(req.arrived_at_ms or now)
        payload = {"arrived_at_ms": arrived_at}
        if snap:
            try:
                items = json.loads(snap) or []
                today = next((it for it in items if it.get("is_today")), None) or (items[0] if items else None)
                if today:
                    payload["customer_label"] = today.get("customer_label")
                    payload["addr"] = today.get("addr")
                    if today.get("customer_phone"):
                        payload["customer_phone"] = today.get("customer_phone")
            except json.JSONDecodeError:
                pass
        cur = con.execute(
            """
            INSERT INTO team_member_events
                (token, member_id, owner_phone, event_type, payload_json, created_at_ms)
            VALUES (?, ?, ?, 'arrived', ?, ?)
            """,
            (req.token, member_id, owner_phone,
             json.dumps(payload, ensure_ascii=False), now),
        )
        event_id = cur.lastrowid
        con.commit()
    return {"ok": True, "event_id": event_id, "arrived_at_ms": arrived_at}


@app.post("/api/team/event/complete")
async def team_event_complete(req: TeamCompleteEventRequest) -> dict:
    """팀원이 작업 완료. 출발/도착과 같은 패턴 — 진행 단계바 마지막 단계."""
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
        completed_at = int(req.completed_at_ms or now)
        payload = {"completed_at_ms": completed_at}
        if snap:
            try:
                items = json.loads(snap) or []
                today = next((it for it in items if it.get("is_today")), None) or (items[0] if items else None)
                if today:
                    payload["customer_label"] = today.get("customer_label")
                    payload["addr"] = today.get("addr")
                    if today.get("customer_phone"):
                        payload["customer_phone"] = today.get("customer_phone")
            except json.JSONDecodeError:
                pass
        cur = con.execute(
            """
            INSERT INTO team_member_events
                (token, member_id, owner_phone, event_type, payload_json, created_at_ms)
            VALUES (?, ?, ?, 'completed', ?, ?)
            """,
            (req.token, member_id, owner_phone,
             json.dumps(payload, ensure_ascii=False), now),
        )
        event_id = cur.lastrowid
        con.commit()
    return {"ok": True, "event_id": event_id, "completed_at_ms": completed_at}


@app.post("/api/team/event/note")
async def team_event_note(req: TeamNoteEventRequest) -> dict:
    """팀원이 현장 특이사항 메모를 남김 → 사장님께 전달(현장명 같이). 직원→사장 방향."""
    text = (req.text or "").strip()
    if not text:
        raise HTTPException(400, "내용을 입력해 주세요")
    if len(text) > 1000:
        text = text[:1000]
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
        payload = {"text": text}
        if snap:
            try:
                items = json.loads(snap) or []
                today = next((it for it in items if it.get("is_today")), None) or (items[0] if items else None)
                if today:
                    payload["customer_label"] = today.get("customer_label")
                    payload["addr"] = today.get("addr")
                    # 고객 전화 — 사장님 고객 카드에 메모를 연결하는 키.
                    cp = today.get("customer_phone")
                    if cp:
                        payload["customer_phone"] = cp
            except json.JSONDecodeError:
                pass
        cur = con.execute(
            """
            INSERT INTO team_member_events
                (token, member_id, owner_phone, event_type, payload_json, created_at_ms)
            VALUES (?, ?, ?, 'note', ?, ?)
            """,
            (req.token, member_id, owner_phone,
             json.dumps(payload, ensure_ascii=False), now),
        )
        event_id = cur.lastrowid
        con.commit()
    return {"ok": True, "event_id": event_id, "text": text}


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
        # §25 — customer_phone 결정: req 우선, 없으면 token 의 schedule_snapshot 에서 추출 시도
        customer_phone = (req.customer_phone or "").strip() or None
        if not customer_phone:
            try:
                snap_row = con.execute(
                    "SELECT schedule_snapshot_json FROM team_member_links WHERE token = ?",
                    (req.token,),
                ).fetchone()
                if snap_row and snap_row[0]:
                    snap = json.loads(snap_row[0])
                    # snapshot 구조 다양 — 안드로이드는 items[] LIST 로 보냄. dict(jobs/items/schedule) 도 호환.
                    if isinstance(snap, list):
                        candidates = snap
                    elif isinstance(snap, dict):
                        candidates = snap.get("jobs") or snap.get("items") or snap.get("schedule") or []
                    else:
                        candidates = []
                    if isinstance(candidates, list) and candidates:
                        # 오늘 현장 우선(팀원은 오늘 카드에서 올림), 없으면 첫 항목.
                        today = next((j for j in candidates if isinstance(j, dict) and j.get("is_today")), None)
                        ordered = ([today] if today else []) + [j for j in candidates if j is not today]
                        for j in ordered:
                            if isinstance(j, dict):
                                cp = (j.get("customer_phone") or j.get("phone") or j.get("customerPhone") or "").strip()
                                if cp:
                                    customer_phone = cp
                                    break
            except Exception:
                pass  # snapshot 파싱 실패는 NULL 로 두고 통과 (안드로이드는 req 에 직접 보내는 게 안정)
        # 한 현장(customer_phone) 최대 20장 — 사장님 정책(2026-06-05). customer_phone 미매핑이면 컷 생략.
        if customer_phone:
            cust_digits = "".join(ch for ch in customer_phone if ch.isdigit())
            suffix_8 = cust_digits[-8:] if len(cust_digits) >= 8 else cust_digits
            cnt = con.execute(
                """
                SELECT COUNT(*) FROM team_site_photos
                WHERE owner_phone = ?
                  AND (
                    customer_phone = ?
                    OR REPLACE(REPLACE(REPLACE(REPLACE(IFNULL(customer_phone,''), '-', ''), ' ', ''), '+', ''), '_', '') LIKE ?
                  )
                """,
                (owner_phone, customer_phone, f"%{suffix_8}"),
            ).fetchone()[0]
            if cnt >= 20:
                raise HTTPException(409, "한 현장에 사진은 20장까지예요")
        cur = con.execute(
            """
            INSERT INTO team_site_photos
                (token, member_id, owner_phone, label, image_data_url, image_path,
                 note, uploaded_at_ms, customer_phone)
            VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?)
            """,
            (req.token, member_id, owner_phone, label, data_url or None,
             (req.note or "").strip() or None, now, customer_phone),
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
    _touch_beta_whitelist(owner_phone)  # 추가47 (2026-06-21) — 옛 빌드도 잡힘
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


@app.get("/api/team/photo/{photo_id}")
async def team_photo_raw(photo_id: int, token: str, w: Optional[int] = None) -> Response:
    """팀원 웹뷰에서 이미 올린 사진을 <img>로 다시 보여줄 때 쓰는 이미지 바이트.

    토큰(활성 + 그 사진의 owner_phone 일치) 검증 후 base64 data URL → 이미지 바이트로 디코드해 반환.
    HTML 에 base64 를 다 박지 않아 페이지가 가볍고, 브라우저가 캐시한다.
    w(폭, px) 주면 그 크기로 줄이고 화질 70 으로 재압축해 더 가볍게(썸네일). Pillow 없으면 원본.
    """
    with db_conn() as con:
        link = con.execute(
            "SELECT owner_phone, expires_at_ms FROM team_member_links WHERE token = ?",
            (token,),
        ).fetchone()
        if not link:
            raise HTTPException(404, "유효하지 않은 토큰")
        owner_phone, expires_at = link
        if _now_ms() > expires_at:
            raise HTTPException(410, "만료된 링크")
        ph = con.execute(
            "SELECT image_data_url, owner_phone FROM team_site_photos WHERE photo_id = ?",
            (photo_id,),
        ).fetchone()
    if not ph or ph[1] != owner_phone or not ph[0]:
        raise HTTPException(404, "사진 없음")
    data_url = ph[0]
    media = "image/jpeg"
    b64 = data_url
    if data_url.startswith("data:"):
        header, _, b64 = data_url.partition(",")
        if ";" in header and ":" in header:
            media = header[header.index(":") + 1:header.index(";")] or media
    try:
        raw = base64.b64decode(b64)
    except (ValueError, binascii.Error):
        raise HTTPException(500, "이미지 디코드 실패")
    # 썸네일 요청(w) — Pillow 있으면 줄여 보내 더 빠름. 없으면 원본 그대로(안전 폴백).
    if w and _PIL_OK:
        try:
            from io import BytesIO
            tw = max(40, min(int(w), 1600))
            im = _PILImage.open(BytesIO(raw)).convert("RGB")
            if im.width > tw:
                th = max(1, int(im.height * tw / im.width))
                im = im.resize((tw, th))
            buf = BytesIO()
            im.save(buf, format="JPEG", quality=70, optimize=True)
            raw = buf.getvalue()
            media = "image/jpeg"
        except Exception:
            pass  # 변환 실패 → 원본 반환
    return Response(content=raw, media_type=media,
                    headers={"Cache-Control": "private, max-age=86400"})


@app.get("/api/team/notes")
async def team_notes_list(owner_phone: str, customer_phone: str, limit: int = 100,
                          mark_read: int = 1) -> dict:
    """사장님 고객 카드용 — 그 고객(현장)에 팀원이 남긴 현장 메모 모음(최신순).

    응답: {notes: [{event_id, text, member_name, created_at_ms, read_at_ms, reply_text, reply_at_ms}]}
    note 이벤트 payload 의 customer_phone(끝 8자리) 으로 매칭.
    mark_read=1(기본): 사장님이 조회 = 확인. 안 읽은 메모에 read_at_ms 를 박음(팀원 화면에 "확인됨" 표시됨).
    """
    if not owner_phone or not customer_phone:
        raise HTTPException(400, "owner_phone, customer_phone 필수")
    _touch_beta_whitelist(owner_phone)  # 추가47 (2026-06-21) — 옛 빌드도 잡힘
    digits = "".join(ch for ch in customer_phone if ch.isdigit())
    suffix_8 = digits[-8:] if len(digits) >= 8 else digits
    limit = max(1, min(limit, 200))
    out = []
    to_mark = []
    now = _now_ms()
    with db_conn() as con:
        rows = con.execute(
            """
            SELECT e.event_id, e.payload_json, e.created_at_ms, m.name,
                   e.read_at_ms, e.reply_text, e.reply_at_ms
            FROM team_member_events e
            LEFT JOIN team_members m ON m.member_id = e.member_id
            WHERE e.owner_phone = ? AND e.event_type = 'note'
            ORDER BY e.created_at_ms DESC LIMIT 400
            """,
            (owner_phone,),
        ).fetchall()
        for eid, pj, cms, mname, read_at, reply_text, reply_at in rows:
            try:
                p = json.loads(pj) if pj else {}
            except json.JSONDecodeError:
                p = {}
            text = (p.get("text") or "").strip()
            if not text:
                continue
            cp = "".join(ch for ch in str(p.get("customer_phone") or "") if ch.isdigit())
            cp8 = cp[-8:] if len(cp) >= 8 else cp
            if suffix_8 and cp8 == suffix_8:
                eff_read = read_at or (now if mark_read else None)
                if mark_read and not read_at:
                    to_mark.append(eid)
                out.append({
                    "event_id": eid, "text": text, "member_name": mname or "팀원",
                    "created_at_ms": cms, "read_at_ms": eff_read,
                    "reply_text": reply_text, "reply_at_ms": reply_at,
                })
            if len(out) >= limit:
                break
        if to_mark:
            con.executemany(
                "UPDATE team_member_events SET read_at_ms = ? WHERE event_id = ?",
                [(now, eid) for eid in to_mark],
            )
            con.commit()
    return {"notes": out, "count": len(out)}


class TeamNoteReplyRequest(BaseModel):
    owner_phone: str
    event_id: int
    text: str


@app.post("/api/team/note/reply")
async def team_note_reply(req: TeamNoteReplyRequest) -> dict:
    """사장님이 팀원 현장 메모에 답글. 팀원 링크 화면에 '대표님 답글'로 보임."""
    text = (req.text or "").strip()
    if not text:
        raise HTTPException(400, "내용을 입력해 주세요")
    if len(text) > 1000:
        text = text[:1000]
    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            "SELECT owner_phone, event_type FROM team_member_events WHERE event_id = ?",
            (req.event_id,),
        ).fetchone()
        if not row or row[1] != "note":
            raise HTTPException(404, "메모 없음")
        if row[0] != req.owner_phone:
            raise HTTPException(403, "권한 없음")
        con.execute(
            "UPDATE team_member_events SET reply_text = ?, reply_at_ms = ? WHERE event_id = ?",
            (text, now, req.event_id),
        )
        con.commit()
    return {"ok": True, "event_id": req.event_id, "reply_at_ms": now}


@app.delete("/api/team/photo/{photo_id}")
async def team_photo_delete(
    photo_id: int, token: Optional[str] = None, owner_phone: Optional[str] = None
) -> dict:
    """사진 삭제. 두 경로 허용:
      - 팀원(token): 본인이 올린 사진만(member_id + owner_phone 일치).
      - 사장님(owner_phone): 자기 현장 사진이면 누구 것이든 삭제(2026-06-07 — 퇴사한 팀원 사진 정리).
    사진은 사장님 고객 카드/팀원 화면 양쪽에서 사라짐.
    """
    with db_conn() as con:
        ph = con.execute(
            "SELECT member_id, owner_phone FROM team_site_photos WHERE photo_id = ?",
            (photo_id,),
        ).fetchone()
        if not ph:
            raise HTTPException(404, "사진 없음")
        ph_member, ph_owner = ph[0], ph[1]
        allowed = False
        # 사장님 직접 삭제 — owner_phone 일치하면 그 현장 사진 전부 삭제 가능.
        if owner_phone and owner_phone == ph_owner:
            allowed = True
        elif token:
            link = con.execute(
                "SELECT member_id, owner_phone, expires_at_ms FROM team_member_links WHERE token = ?",
                (token,),
            ).fetchone()
            if not link:
                raise HTTPException(404, "유효하지 않은 토큰")
            m_id, m_owner, expires_at = link
            if _now_ms() > expires_at:
                raise HTTPException(410, "만료된 링크")
            if ph_owner == m_owner and ph_member == m_id:
                allowed = True
        if not allowed:
            raise HTTPException(403, "삭제 권한이 없어요")
        con.execute("DELETE FROM team_site_photos WHERE photo_id = ?", (photo_id,))
        con.commit()
    return {"ok": True, "photo_id": photo_id}


# ============================================================================
# §25 — 현장 사진 팀↔사장님 공유 (안드로이드 SERVER_HANDOFF 2026-06-04)
# ─────────────────────────────────────────────────────────────────────────────
# 사장님 요청: "팀원도 그 현장에 사진 올리고, 나(사장님)도 그 현장에 올려서,
# 고객 카드에서 같이 본다."
#
# 추가:
#   1. team_site_photos.customer_phone 컬럼 (db_init §25)
#   2. POST /api/site-photo/owner-upload — 사장님 본인 업로드 (member_id='OWNER')
#   3. /api/team/event/photo 에 customer_phone 매핑 (req 우선 + schedule_snapshot 폴백)
#   4. GET /api/site-photos — 고객별 사진 전체 (팀원 + 사장님) + 업로더 이름
#
# 정책 유지:
#   - base64 1MB 컷
#   - owner_phone 검증 (_check_team_tier 재사용)
#   - 큰 사진은 image_path 디스크 (Phase B, 미구현)
# ============================================================================


@app.post("/api/site-photo/owner-upload")
async def owner_site_photo_upload(req: OwnerSitePhotoRequest) -> dict:
    """§25 — 사장님 본인 현장사진 업로드. member_id='OWNER' / token=NULL.

    팀원 토큰 없이 owner_phone 검증으로 통과.
    응답: {ok, photo_id, label, customer_phone}
    """
    owner_phone = (req.owner_phone or "").strip()
    customer_phone = (req.customer_phone or "").strip()
    share_id = (req.share_id or "").strip()    # §F
    if not owner_phone:
        raise HTTPException(400, "owner_phone 필수")
    # §F: customer_phone 또는 share_id 중 하나 필수 (협업 현장은 share_id 만).
    if not customer_phone and not share_id:
        raise HTTPException(400, "customer_phone 또는 share_id 중 하나 필수")
    # 추가36 (2026-06-18) — 화이트리스트 게이트
    _ensure_and_touch_beta_whitelist(owner_phone)
    # 사장님이 어떤 티어든 팀 기능 활성 상태인지 확인 (기존 helper 재사용)
    _check_team_tier(owner_phone)
    # §F 벽: share_id 제공 시 그 share 의 owner 또는 partner 중 하나가 요청자 owner_phone 이어야.
    if share_id:
        with db_conn() as _con:
            _row = _con.execute(
                "SELECT owner_phone, partner_phone FROM shared_sites WHERE share_id = ?",
                (share_id,),
            ).fetchone()
        if not _row:
            raise HTTPException(404, "share_id 없음")
        owner_digits = _norm_phone(owner_phone)
        if owner_digits not in (_norm_phone(_row[0]), _norm_phone(_row[1])):
            raise HTTPException(403, "권한 없음 (이 협업 현장의 owner/partner 만 업로드 가능)")
    data_url = req.image_data_url or ""
    if not data_url:
        raise HTTPException(400, "image_data_url 필수")
    if len(data_url) > 1_400_000:  # 약 1MB base64
        raise HTTPException(413, "사진 용량 초과 (1MB 이하만)")
    label = (req.label or "").strip() or "추가 사진"
    now = _now_ms()
    with db_conn() as con:
        cur = con.execute(
            """
            INSERT INTO team_site_photos
                (token, member_id, owner_phone, label, image_data_url, image_path,
                 note, uploaded_at_ms, customer_phone, share_id)
            VALUES (NULL, 'OWNER', ?, ?, ?, NULL, ?, ?, ?, ?)
            """,
            (
                owner_phone,
                label,
                data_url,
                (req.note or "").strip() or None,
                now,
                customer_phone or None,
                share_id or None,
            ),
        )
        photo_id = cur.lastrowid
        con.commit()
    print(
        f"[owner_site_photo] owner={owner_phone} "
        f"customer={(customer_phone or '')[:13]} share={share_id or '-'} "
        f"photo_id={photo_id} label={label}"
    )
    return {
        "ok": True,
        "photo_id": photo_id,
        "label": label,
        "customer_phone": customer_phone or None,
        "share_id": share_id or None,
        "uploaded_at_ms": now,
    }


@app.get("/api/site-photos")
async def site_photos_by_customer(
    owner_phone: str,
    customer_phone: Optional[str] = None,
    share_id: Optional[str] = None,            # §F (2026-06-13)
    since_ms: int = 0,
    limit: int = 50,
) -> dict:
    """§25 + §F — 특정 고객 현장 또는 협업 현장 사진 전부 (팀원 + 사장님) 조회.

    응답: photos[] 각 row 에 uploader_kind ('owner'|'member'), uploader_name 포함.
    매칭: customer_phone 정확 일치 OR 끝 8자리 suffix (다양한 형태 호환).
    §F: share_id 제공 시 그 share 의 owner 또는 partner 중 하나가 요청자 owner_phone 이어야 벽 통과.
        share_id 제공 시 owner_phone 무관 모든 사진 (A 가 올린 것 + B 가 올린 것) 반환.
    """
    owner_phone = (owner_phone or "").strip()
    customer_phone = (customer_phone or "").strip()
    share_id = (share_id or "").strip()
    if not owner_phone:
        raise HTTPException(400, "owner_phone 필수")
    if not customer_phone and not share_id:
        raise HTTPException(400, "customer_phone 또는 share_id 중 하나 필수")
    limit = max(1, min(limit, 200))

    # §F — share_id 모드: 그 share 의 owner/partner 권한 확인
    if share_id:
        with db_conn() as _con:
            _row = _con.execute(
                "SELECT owner_phone, partner_phone FROM shared_sites WHERE share_id = ?",
                (share_id,),
            ).fetchone()
        if not _row:
            raise HTTPException(404, "share_id 없음")
        owner_digits = _norm_phone(owner_phone)
        if owner_digits not in (_norm_phone(_row[0]), _norm_phone(_row[1])):
            raise HTTPException(403, "권한 없음 (이 협업 현장의 owner/partner 만 조회 가능)")
        with db_conn() as con:
            rows = con.execute(
                """
                SELECT p.photo_id, p.member_id, p.label, p.image_data_url, p.image_path,
                       p.note, p.uploaded_at_ms, p.customer_phone, m.name
                FROM team_site_photos p
                LEFT JOIN team_members m ON m.member_id = p.member_id
                WHERE p.share_id = ? AND p.uploaded_at_ms > ?
                ORDER BY p.uploaded_at_ms DESC
                LIMIT ?
                """,
                (share_id, since_ms, limit),
            ).fetchall()
    else:
        # 기존 customer_phone 모드 (§25 기존 흐름 유지)
        customer_digits = "".join(ch for ch in customer_phone if ch.isdigit())
        suffix_8 = customer_digits[-8:] if len(customer_digits) >= 8 else customer_digits
        with db_conn() as con:
            rows = con.execute(
                """
                SELECT p.photo_id, p.member_id, p.label, p.image_data_url, p.image_path,
                       p.note, p.uploaded_at_ms, p.customer_phone, m.name
                FROM team_site_photos p
                LEFT JOIN team_members m ON m.member_id = p.member_id
                WHERE p.owner_phone = ?
                  AND p.uploaded_at_ms > ?
                  AND (
                    p.customer_phone = ?
                    OR REPLACE(REPLACE(REPLACE(REPLACE(IFNULL(p.customer_phone,''), '-', ''), ' ', ''), '+', ''), '_', '') LIKE ?
                  )
                ORDER BY p.uploaded_at_ms DESC
                LIMIT ?
                """,
                (
                    owner_phone,
                    since_ms,
                    customer_phone,
                    f"%{suffix_8}",
                    limit,
                ),
            ).fetchall()
    photos = []
    for r in rows:
        mid = r[1]
        is_owner = (mid == "OWNER") or (mid is None)
        photos.append(
            {
                "photo_id": r[0],
                "member_id": mid,
                "label": r[2],
                "image_data_url": r[3],
                "image_path": r[4],
                "note": r[5],
                "uploaded_at_ms": r[6],
                "customer_phone": r[7],
                "uploader_kind": "owner" if is_owner else "member",
                "uploader_name": "사장님" if is_owner else (r[8] or "팀원"),
            }
        )
    return {"photos": photos, "count": len(photos)}


# ─── URL HTML 화면 (프로토 openMemberView 1:1) ───
# 팀원 폰 브라우저에서 /team/member/{token} 열면 보는 화면.
# "🔗 링크로 열린 화면 (앱 설치 불필요) / 대표님이 배정한 일정만 보여요"

TEAM_MEMBER_HTML_TEMPLATE = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=yes,maximum-scale=5">
<meta name="theme-color" content="#3182F6">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="내 일정">
<link rel="manifest" href="/manifest/team-member.webmanifest?token={token_js}">
<link rel="apple-touch-icon" href="/manifest/team-member-icon.svg">
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

  /* 브랜드 헤더 */
  .appbar {{ display:flex; align-items:center; gap:9px; padding:13px 16px; background:#fff; border-bottom:1px solid var(--line); }}
  .brand {{ display:inline-flex; align-items:center; gap:8px; }}
  .brand-mark {{ width:26px; height:26px; border-radius:8px; background:linear-gradient(135deg,var(--blue),var(--blue-dark)); color:#fff; font-size:14px; font-weight:900; display:flex; align-items:center; justify-content:center; letter-spacing:-.5px; }}
  .brand-name {{ font-size:15px; font-weight:800; color:var(--t1); }}
  .brand-sub {{ font-size:10.5px; font-weight:700; color:var(--t3); margin-left:2px; }}
  .appbar .me {{ margin-left:auto; font-size:12px; font-weight:800; color:var(--t1); display:inline-flex; align-items:center; gap:5px; background:#E7F8EF; padding:5px 10px; border-radius:999px; }}
  .appbar .me .d {{ width:6px; height:6px; border-radius:50%; background:var(--success); }}

  .mv-note {{ background:#FFF8E1; color:#7A5A00; font-size:11.5px; padding:9px 16px; line-height:1.5; }}
  .mv-note b {{ color:var(--blue-dark); }}

  .scroll {{ flex:1; min-height:0; overflow-y:auto; padding:14px 16px 18px; }}
  .sec-sub {{ font-size:11.5px; font-weight:800; color:var(--t3); margin:16px 2px 8px; letter-spacing:.02em; }}
  .sec-sub:first-child {{ margin-top:2px; }}

  /* 오늘 날짜 헤더 */
  .day-head {{ display:flex; align-items:center; gap:8px; margin:2px 2px 9px; }}
  .day-badge {{ font-size:11.5px; font-weight:900; color:#fff; background:var(--error); padding:3px 9px; border-radius:7px; }}
  .day-date {{ font-size:13px; font-weight:800; color:var(--t2); }}

  .card {{ background:#fff; border-radius:16px; padding:16px; margin-bottom:10px; box-shadow:var(--shadow); }}
  .card .row {{ display:flex; align-items:center; gap:8px; }}
  .card .hd {{ width:8px; height:8px; border-radius:50%; background:var(--blue); flex-shrink:0; }}
  .card .hd.hot {{ background:var(--error); }}
  .card .name {{ font-size:16px; font-weight:800; color:var(--t1); }}
  .card .time {{ margin-left:auto; font-size:12.5px; font-weight:800; color:var(--blue); background:var(--blue-tint); padding:3px 9px; border-radius:8px; }}

  /* 대표님 전달사항 (노란 박스) */
  .owner-memo {{ margin-top:13px; background:#FFF8E1; border:1px solid #FFE7A3; border-radius:12px; padding:11px 13px; }}
  .owner-memo .om-t {{ font-size:11.5px; font-weight:800; color:#A66B00; }}
  .owner-memo .om-b {{ font-size:14px; font-weight:700; color:#5A3D00; margin-top:4px; line-height:1.5; word-break:keep-all; white-space:pre-wrap; }}

  /* 정보 줄 (아이콘 정렬) */
  .fl {{ margin-top:13px; display:flex; flex-direction:column; gap:9px; }}
  .fl-row {{ display:flex; align-items:flex-start; gap:9px; font-size:13.5px; }}
  .fl-ic {{ width:18px; text-align:center; flex-shrink:0; }}
  .fl-k {{ width:34px; flex-shrink:0; color:var(--t3); font-weight:700; font-size:12.5px; padding-top:1px; }}
  .fl-v {{ color:var(--t1); font-weight:600; line-height:1.5; flex:1; word-break:keep-all; }}
  .fl-v a {{ color:var(--blue); font-weight:800; text-decoration:none; }}

  /* 진행 단계바 */
  .stepper {{ display:flex; align-items:flex-start; margin:16px 2px 4px; }}
  .step {{ flex:1; display:flex; flex-direction:column; align-items:center; position:relative; }}
  .step .bar {{ position:absolute; top:11px; left:-50%; width:100%; height:3px; background:#E5E8EC; z-index:0; border-radius:2px; }}
  .step:first-child .bar {{ display:none; }}
  .step.on .bar {{ background:var(--success); }}
  .step .dot {{ width:24px; height:24px; border-radius:50%; background:#E5E8EC; color:#fff; display:flex; align-items:center; justify-content:center; font-size:12px; font-weight:900; z-index:1; }}
  .step.done .dot {{ background:var(--success); }}
  .step.cur .dot {{ background:var(--blue); box-shadow:0 0 0 4px var(--blue-tint); }}
  .step .lb {{ font-size:11px; color:var(--t3); margin-top:6px; font-weight:700; }}
  .step.done .lb {{ color:var(--success); }}
  .step.cur .lb {{ color:var(--blue); font-weight:800; }}

  .hbtn {{
    display:inline-flex; align-items:center; justify-content:center; gap:6px;
    background:var(--bg); color:var(--blue); border:0; border-radius:11px;
    padding:11px 14px; font-size:13.5px; font-weight:800; font-family:inherit;
    cursor:pointer; min-height:42px; width:100%;
  }}
  .navchips {{ display:flex; gap:7px; margin-top:9px; flex-wrap:wrap; }}
  .nav-chip {{
    background:#fff; border:1.5px solid var(--line); border-radius:999px;
    padding:8px 14px; font-size:12.5px; font-weight:700; color:var(--t1);
    font-family:inherit; cursor:pointer; min-height:38px;
  }}
  .nav-chip:active {{ background:var(--blue-tint); border-color:var(--blue); color:var(--blue); }}

  /* 사진 */
  .mv-photos {{ background:#fff; border-radius:16px; padding:16px; margin-top:10px; box-shadow:var(--shadow); }}
  .mv-ph-top {{ display:flex; align-items:center; gap:7px; font-size:14px; font-weight:800; color:var(--t1); }}
  .ph-count {{ margin-left:auto; font-size:12px; font-weight:700; color:var(--t3); }}
  .ph-help {{ font-size:11.5px; color:var(--t3); margin-top:6px; line-height:1.5; }}
  .ph-actions {{ display:flex; gap:8px; margin-top:12px; }}
  .ph-btn {{
    flex:1; display:inline-flex; align-items:center; justify-content:center; gap:6px;
    background:var(--blue-tint); color:var(--blue-dark); border:0; border-radius:12px;
    padding:13px; font-size:14px; font-weight:800; font-family:inherit; cursor:pointer; min-height:48px;
  }}
  .ph-btn:disabled {{ background:var(--bg); color:var(--t3); cursor:default; }}
  .photo-grid {{ display:grid; grid-template-columns:repeat(3, 1fr); gap:8px; margin-top:12px; }}
  .photo-grid:empty {{ display:none; }}
  .photo-thumb {{
    aspect-ratio:1; border-radius:10px; background:var(--bg);
    display:flex; flex-direction:column; align-items:center; justify-content:center;
    font-size:11px; color:var(--t3); position:relative; overflow:hidden;
  }}
  .photo-thumb.done {{ background:#E7F8EF; color:var(--success); font-weight:800; font-size:18px; }}
  .photo-thumb {{ cursor:pointer; }}
  .photo-thumb img {{ width:100%; height:100%; object-fit:cover; display:block; }}
  .photo-thumb .ov {{ position:absolute; inset:0; display:flex; align-items:center; justify-content:center; background:rgba(0,0,0,.35); color:#fff; font-size:11px; font-weight:800; }}
  .photo-thumb .bar-wrap {{ position:absolute; left:8px; right:8px; bottom:8px; height:5px; border-radius:3px; background:rgba(255,255,255,.5); overflow:hidden; }}
  .photo-thumb .bar-fill {{ height:100%; width:0%; background:var(--blue); transition:width .2s; }}
  .ph-del {{ position:absolute; top:4px; right:4px; width:22px; height:22px; border:0; border-radius:50%;
    background:rgba(0,0,0,.55); color:#fff; font-size:12px; font-weight:800; line-height:1;
    display:flex; align-items:center; justify-content:center; cursor:pointer; padding:0; }}

  /* 현장 메모 (직원→사장) */
  .mv-note-input {{ width:100%; margin-top:12px; border:1.5px solid var(--line); border-radius:12px;
    padding:12px; font-size:14px; font-family:inherit; color:var(--t1); min-height:72px; resize:vertical;
    background:var(--bg); box-sizing:border-box; }}
  .mv-note-input:focus {{ outline:none; border-color:var(--blue); background:#fff; }}
  .mv-note-list {{ margin-top:12px; display:flex; flex-direction:column; gap:8px; }}
  .mv-note-list:empty {{ display:none; }}
  .mv-note-row {{ background:var(--bg); border-radius:10px; padding:10px 12px; }}
  .mv-note-row .mn-t {{ font-size:11px; font-weight:700; color:var(--t3); margin-right:6px; }}
  .mv-note-row .mn-b {{ font-size:13.5px; color:var(--t1); line-height:1.5; }}
  .mv-note-row .mn-read {{ display:block; margin-top:5px; font-size:11px; font-weight:700; color:var(--success); }}
  .mv-note-row .mn-unread {{ display:block; margin-top:5px; font-size:11px; font-weight:700; color:var(--t3); }}
  .mv-note-row .mn-reply {{ margin-top:7px; padding:8px 10px; background:var(--blue-tint); border-radius:8px;
    font-size:13px; color:var(--blue-dark); font-weight:600; line-height:1.5; }}

  /* 다음 일정 — 탭하면 길찾기 펼침 */
  .next-card {{ cursor:pointer; }}
  .next-card:active {{ background:#FAFBFC; }}
  .next-chev {{ margin-left:8px; font-size:12px; color:var(--t3); }}

  /* 사진 크게 보기 (라이트박스) */
  .lightbox {{ position:fixed; inset:0; background:rgba(0,0,0,.92); display:none; align-items:center; justify-content:center; z-index:50; }}
  .lightbox.show {{ display:flex; }}
  .lightbox img {{ max-width:96%; max-height:90%; border-radius:8px; }}
  .lightbox .x {{ position:absolute; top:12px; right:18px; color:#fff; font-size:32px; font-weight:300; line-height:1; }}

  .empty {{ font-size:13px; color:var(--t3); text-align:center; padding:30px 16px; line-height:1.6; }}

  .foot-note {{ font-size:12px; color:var(--t3); text-align:center; margin-top:22px; line-height:1.6; }}
  .foot-link {{ font-size:11.5px; color:var(--t3); text-align:center; margin-top:12px; background:var(--bg); border-radius:10px; padding:9px; }}

  /* 추가29 (2026-06-15) — 월 캘린더 */
  .cal-card {{ background:var(--card); border-radius:14px; padding:14px 14px 12px; margin-bottom:14px; box-shadow:var(--shadow); }}
  .cal-head {{ display:flex; align-items:center; justify-content:space-between; margin-bottom:10px; }}
  .cal-head h3 {{ margin:0; font-size:15.5px; font-weight:800; color:var(--t1); }}
  .cal-nav {{ display:inline-flex; gap:4px; }}
  .cal-nav button {{ background:var(--bg); border:0; border-radius:8px; width:32px; height:32px; font-size:14px; font-weight:700; color:var(--t1); cursor:pointer; font-family:inherit; }}
  .cal-nav button:active {{ background:#E8EAEE; }}
  .cal-week {{ display:grid; grid-template-columns:repeat(7,1fr); gap:4px; margin-bottom:6px; font-size:10.5px; font-weight:700; color:var(--t3); text-align:center; }}
  .cal-week .sun {{ color:#E53E3E; }}
  .cal-week .sat {{ color:var(--blue-dark); }}
  .cal-grid {{ display:grid; grid-template-columns:repeat(7,1fr); gap:4px; }}
  .cal-day {{ aspect-ratio:1; display:flex; flex-direction:column; align-items:center; justify-content:center; border-radius:8px; font-size:13px; color:var(--t2); cursor:pointer; position:relative; padding-top:2px; }}
  .cal-day.out {{ color:#D6D9DE; }}
  .cal-day.has {{ color:var(--t1); font-weight:700; }}
  .cal-day.today {{ background:var(--blue-tint); color:var(--blue-dark); font-weight:800; }}
  .cal-day.sel {{ background:var(--blue); color:#fff; font-weight:800; }}
  .cal-day.sel .dot {{ background:#fff; }}
  .cal-day .dot {{ position:absolute; bottom:4px; width:5px; height:5px; border-radius:50%; background:var(--blue); }}
  .cal-day-list {{ margin-top:10px; padding-top:10px; border-top:1px solid var(--line); display:none; }}
  .cal-day-list.show {{ display:block; }}
  .cal-day-list .row {{ background:var(--bg); border-radius:10px; padding:10px 12px; margin-top:6px; }}
  .cal-day-list .row .t {{ font-size:12px; font-weight:800; color:var(--blue-dark); margin-bottom:3px; }}
  .cal-day-list .row .c {{ font-size:13.5px; font-weight:700; color:var(--t1); }}
  .cal-day-list .row .s {{ font-size:12px; color:var(--t2); margin-top:3px; }}
  .cal-day-list .row .m {{ font-size:12px; color:var(--blue-dark); margin-top:4px; padding:6px 8px; background:var(--blue-tint); border-radius:6px; }}
  .cal-day-list .empty-day {{ font-size:12px; color:var(--t3); text-align:center; padding:14px; }}

  /* 추가29 — PWA 홈 화면 추가 버튼 */
  .pwa-install {{ display:block; width:100%; margin:18px 0 4px; background:#fff; color:var(--blue-dark); border:1.5px solid var(--blue); border-radius:12px; padding:13px; font-size:14px; font-weight:800; cursor:pointer; font-family:inherit; }}
  .pwa-install:active {{ background:var(--blue-tint); }}
  .pwa-sheet {{ position:fixed; inset:0; background:rgba(0,0,0,.5); display:none; align-items:flex-end; justify-content:center; z-index:60; }}
  .pwa-sheet.show {{ display:flex; }}
  .pwa-sheet-card {{ width:100%; max-width:480px; background:#fff; border-radius:16px 16px 0 0; padding:22px 20px 28px; }}
  .pwa-sheet-card h3 {{ margin:0 0 10px; font-size:17px; font-weight:800; }}
  .pwa-sheet-card p {{ margin:0 0 18px; font-size:14px; color:var(--t2); line-height:1.6; }}
  .pwa-sheet-close {{ width:100%; background:var(--blue); color:#fff; border:0; border-radius:12px; padding:14px; font-size:15px; font-weight:800; font-family:inherit; cursor:pointer; }}

  /* 카드 안 진행 액션 버튼 (단계바 바로 아래 — 눈에 잘 띄게) */
  .card-action {{ margin-top:14px; }}
  .card-action:empty {{ display:none; }}
  .act-btn {{
    width:100%; background:var(--blue); color:#fff; border:0; border-radius:14px;
    padding:15px; font-size:16px; font-weight:800; font-family:inherit; cursor:pointer;
    display:inline-flex; align-items:center; justify-content:center; gap:8px; min-height:54px;
  }}
  .act-btn:disabled {{ cursor:default; }}
  .act-btn.done {{ background:var(--success); }}
</style>
</head>
<body>
<div class="wrap">

  <div class="appbar">
    <div class="brand">
      <span class="brand-mark">R</span>
      <span class="brand-name">{biz_header_html}</span>
      <span class="brand-sub">RING-GO</span>
    </div>
    <span class="me"><span class="d"></span>{member_name_html}</span>
  </div>

  <div class="mv-note">
    🔗 링크로 열린 화면 (앱 설치 불필요) · <b>{owner_label_html}</b>이(가) 배정한 일정만 보여요
  </div>

  <div class="scroll">
    {calendar_block}
    {today_block}
    {next_block}
    <button id="pwa-install-btn" class="pwa-install" type="button" onclick="onInstallClick()">📲 홈 화면에 추가</button>
    <div class="foot-note">상담·정산·통계·고객정보는 대표님만 봐요.<br>나는 내 현장만 깔끔하게 ✓</div>
    <div class="foot-link">🔗 이 링크는 계속 사용할 수 있어요</div>
  </div>

  <!-- PWA 설치 안내 시트 (삼성인터넷·iOS — beforeinstallprompt 없는 브라우저용) -->
  <div class="pwa-sheet" id="pwa-sheet" onclick="if(event.target.id==='pwa-sheet')closePwaSheet()">
    <div class="pwa-sheet-card">
      <h3>📲 홈 화면에 추가하기</h3>
      <p id="pwa-sheet-msg">브라우저 메뉴를 열고 "현재 페이지를 홈 화면에 추가" 를 눌러주세요.</p>
      <button class="pwa-sheet-close" type="button" onclick="closePwaSheet()">확인</button>
    </div>
  </div>

</div>

<div class="lightbox" id="lightbox" onclick="closeZoom()">
  <span class="x">×</span>
  <img id="lightbox-img" src="" alt="현장 사진">
</div>

<script>
  var TOKEN = "{token_js}";
  var STATUS = {status_js};
  var PHOTO_COUNT = {photo_count_js};
  var PHOTO_MAX = 20;
  var BUSY = false;

  // ── 추가29: 월 캘린더 ──
  var ITEMS_FOR_CAL = {items_for_cal_js};
  var CAL_VIEW = new Date();
  CAL_VIEW.setDate(1);
  var CAL_SEL_KEY = null;

  function _dKey(d) {{ return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0'); }}

  function buildScheduleMap() {{
    var map = {{}};
    for (var i=0; i<ITEMS_FOR_CAL.length; i++) {{
      var it = ITEMS_FOR_CAL[i];
      if (!it.scheduled_at_ms) continue;
      var start = new Date(it.scheduled_at_ms);
      var days = Math.max(1, it.days || 1);
      for (var d=0; d<days; d++) {{
        var dt = new Date(start.getTime() + d*86400000);
        var k = _dKey(dt);
        if (!map[k]) map[k] = [];
        map[k].push(it);
      }}
    }}
    return map;
  }}
  var SCHED_MAP = buildScheduleMap();

  function renderCalendar() {{
    var y = CAL_VIEW.getFullYear(), m = CAL_VIEW.getMonth();
    document.getElementById('cal-title').textContent = y + '년 ' + (m+1) + '월';
    var first = new Date(y, m, 1);
    var startDow = first.getDay();
    var lastDate = new Date(y, m+1, 0).getDate();
    var prevLast = new Date(y, m, 0).getDate();
    var today = new Date();
    var todayKey = _dKey(today);
    var html = '';
    var cells = 0;
    for (var i=startDow-1; i>=0; i--) {{
      html += '<div class="cal-day out">'+(prevLast-i)+'</div>';
      cells++;
    }}
    for (var d=1; d<=lastDate; d++) {{
      var dt = new Date(y, m, d);
      var k = _dKey(dt);
      var cls = 'cal-day';
      var has = SCHED_MAP[k] && SCHED_MAP[k].length > 0;
      if (has) cls += ' has';
      if (k === todayKey) cls += ' today';
      if (k === CAL_SEL_KEY) cls += ' sel';
      html += '<div class="'+cls+'" onclick="selectDay(\\''+k+'\\')">'+d;
      if (has) html += '<span class="dot"></span>';
      html += '</div>';
      cells++;
    }}
    var nextD = 1;
    while (cells < 42) {{
      html += '<div class="cal-day out">'+nextD+'</div>';
      nextD++; cells++;
    }}
    document.getElementById('cal-grid').innerHTML = html;
    renderSelectedDay();
  }}

  function renderSelectedDay() {{
    var box = document.getElementById('cal-day-list');
    if (!CAL_SEL_KEY) {{ box.classList.remove('show'); box.innerHTML=''; return; }}
    var rows = SCHED_MAP[CAL_SEL_KEY] || [];
    var parts = ['<div class="t">'+CAL_SEL_KEY+' 배정</div>'];
    if (rows.length === 0) {{
      parts.push('<div class="empty-day">이 날 배정 없음</div>');
    }} else {{
      for (var i=0; i<rows.length; i++) {{
        var it = rows[i];
        var p = '<div class="row">';
        if (it.time) p += '<div class="t">'+escapeHtml(it.time)+'</div>';
        p += '<div class="c">'+escapeHtml(it.customer_label||'현장')+'</div>';
        if (it.addr) p += '<div class="s">📍 '+escapeHtml(it.addr)+'</div>';
        if (it.work_summary) p += '<div class="s">🔨 '+escapeHtml(it.work_summary)+'</div>';
        if (it.team_memo) p += '<div class="m">📌 '+escapeHtml(it.team_memo)+'</div>';
        p += '</div>';
        parts.push(p);
      }}
    }}
    box.innerHTML = parts.join('');
    box.classList.add('show');
  }}

  function selectDay(k) {{ CAL_SEL_KEY = (CAL_SEL_KEY===k ? null : k); renderCalendar(); }}
  function calPrev() {{ CAL_VIEW.setMonth(CAL_VIEW.getMonth()-1); CAL_SEL_KEY=null; renderCalendar(); }}
  function calNext() {{ CAL_VIEW.setMonth(CAL_VIEW.getMonth()+1); CAL_SEL_KEY=null; renderCalendar(); }}
  function calToday() {{ CAL_VIEW=new Date(); CAL_VIEW.setDate(1); CAL_SEL_KEY=_dKey(new Date()); renderCalendar(); }}

  function escapeHtml(s) {{ s=String(s||''); return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }}

  // ── 추가29: PWA ──
  var deferredPrompt = null;
  window.addEventListener('beforeinstallprompt', function(e) {{ e.preventDefault(); deferredPrompt = e; }});
  function onInstallClick() {{
    if (deferredPrompt) {{
      deferredPrompt.prompt();
      deferredPrompt.userChoice.then(function(){{ deferredPrompt=null; }});
      return;
    }}
    var ua = navigator.userAgent.toLowerCase();
    var msg = '브라우저 메뉴를 열고 "현재 페이지를 홈 화면에 추가" 를 눌러주세요.';
    if (/iphone|ipad|ipod/.test(ua)) {{
      msg = '하단 공유 버튼 (▢↑) 을 누르고 "홈 화면에 추가" 를 선택해주세요.';
    }} else if (ua.indexOf('samsungbrowser') !== -1) {{
      msg = '하단 메뉴 (≡) → "현재 페이지를 홈 화면에 추가" 를 눌러주세요.';
    }}
    document.getElementById('pwa-sheet-msg').textContent = msg;
    document.getElementById('pwa-sheet').classList.add('show');
  }}
  function closePwaSheet() {{ document.getElementById('pwa-sheet').classList.remove('show'); }}

  CAL_SEL_KEY = _dKey(new Date());
  document.addEventListener('DOMContentLoaded', renderCalendar);

  // ── 진행 단계바 + 하단 액션 버튼 (STATUS 단일 소스로 둘 다 그림) ──
  var STAGES = [
    {{ key:'assigned',  lb:'배정' }},
    {{ key:'departed',  lb:'출발' }},
    {{ key:'arrived',   lb:'도착' }},
    {{ key:'completed', lb:'완료' }},
  ];
  // 다음에 누를 단계 (출발→도착→완료 순) — 없으면 모두 완료.
  function nextStage() {{
    if (!STATUS.departed)  return {{ stage:'depart',   ep:'depart',   key:'departed',  label:'🚗 출발했어요' }};
    if (!STATUS.arrived)   return {{ stage:'arrive',   ep:'arrive',   key:'arrived',   label:'📍 현장 도착' }};
    if (!STATUS.completed) return {{ stage:'complete', ep:'complete', key:'completed', label:'✅ 작업 완료' }};
    return null;
  }}
  function isDone(key) {{
    if (key === 'assigned') return true;
    return !!STATUS[key];
  }}
  function renderProgress() {{
    var stepEl = document.getElementById('mv-stepper');
    if (!stepEl) return;  // 오늘 현장 없음 → 단계바/액션바 둘 다 없음
    var nx = nextStage();
    var curKey = nx ? nx.key : null;
    var html = '';
    for (var i = 0; i < STAGES.length; i++) {{
      var s = STAGES[i];
      var done = isDone(s.key);
      var cur = (s.key === curKey);
      var cls = 'step' + (done ? ' done on' : '') + (cur ? ' cur' : '');
      // bar(이전 단계와 연결선)는 이 단계가 done/cur 이면 채움
      if (cur && !done) cls += ' on';
      var dotInner = done ? '✓' : String(i + 1);
      html += '<div class="' + cls + '"><span class="bar"></span>'
            + '<span class="dot">' + dotInner + '</span>'
            + '<span class="lb">' + s.lb + '</span></div>';
    }}
    stepEl.innerHTML = html;

    var ab = document.getElementById('mv-actionbar');
    if (!ab) return;
    ab.innerHTML = '';
    var b = document.createElement('button');
    if (!nx) {{
      b.className = 'act-btn done';
      b.disabled = true;
      b.textContent = '✓ 오늘 작업 완료';
    }} else {{
      b.className = 'act-btn';
      b.textContent = nx.label;
      b.onclick = function() {{ doStage(nx.stage); }};
    }}
    ab.appendChild(b);
  }}

  async function doStage(stage) {{
    if (BUSY) return;
    var map = {{
      depart:   {{ ep:'depart',   key:'departed',  ask:'출발 알림을 대표님께 보낼까요?' }},
      arrive:   {{ ep:'arrive',   key:'arrived',   ask:'현장 도착을 대표님께 알릴까요?' }},
      complete: {{ ep:'complete', key:'completed', ask:'작업 완료를 대표님께 알릴까요?' }},
    }};
    var m = map[stage]; if (!m) return;
    // 잘못 눌러도 한 번 더 확인(사장님 결정 2026-06-06) — 확인해야 전송.
    if (!window.confirm(m.ask)) return;
    BUSY = true;
    var ab = document.getElementById('mv-actionbar');
    if (ab) ab.innerHTML = '<button class="act-btn" disabled>전송 중…</button>';
    try {{
      var resp = await fetch('/api/team/event/' + m.ep, {{
        method:'POST',
        headers:{{'Content-Type':'application/json'}},
        body: JSON.stringify({{token: TOKEN}}),
      }});
      if (resp.ok) {{
        STATUS[m.key] = true;
      }} else {{
        var err = await resp.json().catch(function(){{return{{}};}});
        alert('실패: ' + (err.detail || resp.status));
      }}
    }} catch (e) {{
      alert('네트워크 오류 — 잠시 후 다시 시도해 주세요');
    }}
    BUSY = false;
    renderProgress();
  }}

  // 사진 크게 보기 — 썸네일 탭하면 원본(?w 없는 URL 또는 방금 올린 dataUrl)을 띄움.
  function zoom(url) {{
    var lb = document.getElementById('lightbox');
    var im = document.getElementById('lightbox-img');
    if (!lb || !im) return;
    im.src = url;
    lb.classList.add('show');
  }}
  function closeZoom() {{
    var lb = document.getElementById('lightbox');
    if (lb) lb.classList.remove('show');
  }}

  // 잘못 올린 사진 삭제 — 내가 올린 것만(서버가 한 번 더 검증). 확인 후 삭제.
  async function delPhoto(ev, pid) {{
    if (ev) ev.stopPropagation();
    if (!pid) return;
    if (!window.confirm('이 사진을 지울까요?')) return;
    try {{
      var resp = await fetch('/api/team/photo/' + pid + '?token=' + encodeURIComponent(TOKEN), {{ method:'DELETE' }});
      if (resp.ok) {{
        var tile = document.querySelector('.photo-thumb[data-pid="' + pid + '"]');
        if (tile && tile.parentNode) tile.parentNode.removeChild(tile);
        if (PHOTO_COUNT > 0) PHOTO_COUNT--;
        updatePhotoCount();
      }} else {{
        var err = await resp.json().catch(function(){{return{{}};}});
        alert(err.detail || '삭제 실패');
      }}
    }} catch (e) {{ alert('네트워크 오류'); }}
  }}

  // 현장 메모 보내기 (직원→사장) — 비어있으면 무시. 보내면 목록에 추가 + 입력칸 비움.
  async function sendNote() {{
    var ta = document.getElementById('mv-note-input');
    var btn = document.getElementById('mv-note-send');
    var text = ta ? (ta.value || '').trim() : '';
    if (!text) {{ alert('내용을 입력해 주세요'); return; }}
    if (btn) {{ btn.disabled = true; btn.textContent = '보내는 중…'; }}
    try {{
      var resp = await fetch('/api/team/event/note', {{
        method:'POST',
        headers:{{'Content-Type':'application/json'}},
        body: JSON.stringify({{token: TOKEN, text: text}}),
      }});
      if (resp.ok) {{
        var list = document.getElementById('mv-note-list');
        if (list) {{
          var now = new Date();
          var hh = ('0'+now.getHours()).slice(-2), mm = ('0'+now.getMinutes()).slice(-2);
          var row = document.createElement('div');
          row.className = 'mv-note-row';
          row.innerHTML = '<span class="mn-t"></span><span class="mn-b"></span><span class="mn-unread">아직 확인 전</span>';
          row.querySelector('.mn-t').textContent = hh + ':' + mm;
          row.querySelector('.mn-b').textContent = text;
          list.appendChild(row);
        }}
        if (ta) ta.value = '';
      }} else {{
        var err = await resp.json().catch(function(){{return{{}};}});
        alert(err.detail || '전송 실패');
      }}
    }} catch (e) {{ alert('네트워크 오류'); }}
    if (btn) {{ btn.disabled = false; btn.textContent = '📨 대표님께 보내기'; }}
  }}

  function addrText() {{
    var el = document.getElementById('today-addr');
    return el ? (el.textContent || '').trim() : '';
  }}
  function copyText(t) {{
    if (!t) return;
    if (navigator.clipboard) navigator.clipboard.writeText(t);
    alert('주소 복사됨\\n' + t);
  }}
  function openNavApp(app, addr) {{
    var a = encodeURIComponent(addr || '');
    var url = (app === '티맵') ? 'tmap://search?name=' + a : 'https://map.kakao.com/?q=' + a;
    window.location.href = url;
  }}
  function copyAddr() {{ copyText(addrText()); }}
  function openNav(app) {{ openNavApp(app, addrText()); }}
  // 다음 일정 카드 탭 → 그 현장 길찾기 펼침/접힘.
  function toggleNext(idx) {{
    var el = document.getElementById('next-act-' + idx);
    var ch = document.getElementById('next-chev-' + idx);
    if (!el) return;
    var open = el.style.display !== 'none';
    el.style.display = open ? 'none' : 'block';
    if (ch) ch.textContent = open ? '▾' : '▴';
  }}

  // ── 사진: 촬영(카메라) / 앨범(다중) — 같은 업로드 루틴 공유 ──
  function updatePhotoCount() {{
    var c = document.getElementById('ph-count');
    if (c) c.textContent = '올린 사진 ' + PHOTO_COUNT + '장 (최대 ' + PHOTO_MAX + ')';
    var full = PHOTO_COUNT >= PHOTO_MAX;
    ['ph-cam','ph-alb'].forEach(function(id) {{
      var b = document.getElementById(id);
      if (b) b.disabled = full;
    }});
  }}
  function pickCamera() {{ openPicker(false); }}
  function pickAlbum()  {{ openPicker(true); }}
  function openPicker(multiple) {{
    if (PHOTO_COUNT >= PHOTO_MAX) {{ alert('한 현장에 사진은 ' + PHOTO_MAX + '장까지예요'); return; }}
    var f = document.createElement('input');
    f.type = 'file'; f.accept = 'image/*';
    if (multiple) f.multiple = true; else f.capture = 'environment';
    f.onchange = function(e) {{ uploadFiles(e.target.files); }};
    f.click();
  }}
  async function uploadFiles(files) {{
    if (!files || !files.length) return;
    var grid = document.getElementById('mv-photo-grid');
    var ok = 0, fail = 0;
    for (var i = 0; i < files.length; i++) {{
      if (PHOTO_COUNT >= PHOTO_MAX) {{ alert('한 현장에 사진은 ' + PHOTO_MAX + '장까지예요'); break; }}
      var dataUrl = await resizeImage(files[i], 1024, 0.82);
      // 진행률 타일 미리 추가
      var tile = document.createElement('div');
      tile.className = 'photo-thumb';
      tile.style.backgroundImage = 'url(' + dataUrl + ')';
      tile.style.backgroundSize = 'cover';
      tile.style.backgroundPosition = 'center';
      tile.innerHTML = '<div class="bar-wrap"><div class="bar-fill" style="width:35%"></div></div>';
      if (grid) grid.appendChild(tile);
      try {{
        var resp = await fetch('/api/team/event/photo', {{
          method:'POST',
          headers:{{'Content-Type':'application/json'}},
          body: JSON.stringify({{token: TOKEN, image_data_url: dataUrl}}),
        }});
        if (resp.ok) {{
          ok++; PHOTO_COUNT++;
          var data = await resp.json().catch(function(){{return{{}};}});
          var pid = data.photo_id || 0;
          if (pid) tile.setAttribute('data-pid', pid);
          // 방금 올린 건 내가 올린 것 → ✕(삭제) 버튼 같이.
          tile.innerHTML = '<div class="ov">업로드 완료</div>'
            + (pid ? '<button class="ph-del" onclick="delPhoto(event,' + pid + ')" aria-label="삭제">✕</button>' : '');
          (function(u) {{ tile.onclick = function() {{ zoom(u); }}; }})(dataUrl);
          updatePhotoCount();
        }} else {{
          fail++;
          if (grid) grid.removeChild(tile);
          var err = await resp.json().catch(function(){{return{{}};}});
          if (err.detail) {{ alert(err.detail); break; }}
        }}
      }} catch (e) {{
        fail++;
        if (grid) grid.removeChild(tile);
      }}
    }}
    if (fail && ok) alert(ok + '장 올림 · ' + fail + '장 실패');
    else if (fail && !ok) alert('사진 전송 실패');
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

  renderProgress();
  updatePhotoCount();
</script>
</body>
</html>
"""


def _build_today_card_html(item: dict, date_label: str = "", photos: Optional[list[dict]] = None,
                           notes: Optional[list[dict]] = None) -> str:
    """오늘 현장 카드 HTML — 프로토 openMemberView 기반 + 전문성 보강(날짜·정보줄·진행단계·사진 X/20).

    진행 단계바(#mv-stepper)와 하단 액션바는 클라이언트 JS(renderProgress)가 STATUS 로 그린다.
    여기선 빈 컨테이너만 둔다. photos = [{id, url, own}] (own=내가 올린 것 → ✕ 삭제 가능).
    notes = 오늘 직원이 남긴 현장 메모 [{text, time}] (직원→사장 방향).
    """
    import html as _html
    photos = photos or []
    notes = notes or []
    photo_count = len(photos)
    if not item:
        return ('<div class="sec-sub">오늘 현장</div>'
                '<div class="card"><div class="empty">오늘 배정된 현장이 없어요</div></div>')
    name = _html.escape(str(item.get("customer_label") or "현장"))
    time = _html.escape(str(item.get("time") or "—"))
    addr = _html.escape(str(item.get("addr") or "주소 미입력"))
    work = _html.escape(str(item.get("work_summary") or ""))
    # team_memo = 사장님이 배정 시 직원에게 전달한 메모(고객 메모 아님 — 사생활 보호로 고객 메모는 안 보냄).
    team_memo = str(item.get("team_memo") or "").strip()
    cust_phone = str(item.get("customer_phone") or "").strip()
    date_html = f'<span class="day-date">{_html.escape(date_label)}</span>' if date_label else ""

    # 정보 줄 — 아이콘별 줄맞춤(시간/주소/작업/고객). 주소는 #today-addr(복사·내비용, 순수 텍스트).
    rows = [
        f'<div class="fl-row"><span class="fl-ic">🕘</span><span class="fl-k">시간</span><span class="fl-v">{time}</span></div>',
        f'<div class="fl-row"><span class="fl-ic">📍</span><span class="fl-k">주소</span><span class="fl-v" id="today-addr">{addr}</span></div>',
    ]
    if work:
        rows.append(f'<div class="fl-row"><span class="fl-ic">🔧</span><span class="fl-k">작업</span><span class="fl-v">{work}</span></div>')
    if cust_phone:
        # 고객 연락처 — 대표님이 배정 시 같이 보냄(사장님 요청 2026-06-05).
        cp = _html.escape(cust_phone)
        rows.append(f'<div class="fl-row"><span class="fl-ic">📞</span><span class="fl-k">고객</span><span class="fl-v"><a href="tel:{cp}">{cp}</a></span></div>')
    fl_html = '<div class="fl">' + "".join(rows) + '</div>'

    # 대표님 전달사항 — 있을 때만 눈에 띄는 노란 박스(고객 메모와 별개).
    memo_html = ""
    if team_memo:
        memo_safe = _html.escape(team_memo).replace("\n", "<br>")
        memo_html = f'<div class="owner-memo"><div class="om-t">📌 대표님 전달사항</div><div class="om-b">{memo_safe}</div></div>'

    # 이미 올린 사진 = 작은 썸네일(?w=400)로 다시 보여주고, 탭하면 원본(라이트박스).
    #   썸네일은 서버가 줄여 보내(가볍고 빠름), 원본은 탭할 때만 받음. 내가 올린 것엔 ✕(삭제).
    def _tile(p: dict) -> str:
        u = _html.escape(str(p.get("url") or ""), quote=True)
        pid = int(p.get("id") or 0)
        delbtn = (f'<button class="ph-del" onclick="delPhoto(event,{pid})" aria-label="삭제">✕</button>'
                  if p.get("own") else "")
        return (f'<div class="photo-thumb" data-pid="{pid}">'
                f'<img src="{u}&amp;w=400" loading="lazy" alt="" onclick="zoom(\'{u}\')">'
                f'{delbtn}</div>')
    done_tiles = "".join(_tile(p) for p in photos)

    # 직원이 오늘 남긴 현장 메모 목록(직원→사장) + 대표님 확인/답글 표시(양방향).
    def _note_row(n: dict) -> str:
        t = _html.escape(str(n.get("time") or ""))
        b = _html.escape(str(n.get("text") or "")).replace(chr(10), "<br>")
        read = n.get("read")
        reply = n.get("reply")
        status_html = (f'<span class="mn-read">✓ 대표님 확인 {_html.escape(str(read))}</span>'
                       if read else '<span class="mn-unread">아직 확인 전</span>')
        reply_html = (f'<div class="mn-reply">↳ 대표님 답글<br>{_html.escape(str(reply)).replace(chr(10), "<br>")}</div>'
                      if reply else "")
        return (f'<div class="mv-note-row"><span class="mn-t">{t}</span>'
                f'<span class="mn-b">{b}</span>{status_html}{reply_html}</div>')
    sent_notes = "".join(_note_row(n) for n in notes)
    return f'''
    <div class="day-head"><span class="day-badge">오늘</span>{date_html}</div>
    <div class="card">
      <div class="row">
        <span class="hd hot"></span>
        <span class="name">{name}</span>
        <span class="time">{time}</span>
      </div>
      {memo_html}
      {fl_html}
      <div class="stepper" id="mv-stepper"></div>
      <div class="card-action" id="mv-actionbar"></div>
      <div style="margin-top:8px"><button class="hbtn" onclick="copyAddr()">📋 주소 복사</button></div>
      <div class="navchips">
        <button class="nav-chip" onclick="openNav('카카오맵')">카카오맵</button>
        <button class="nav-chip" onclick="openNav('카카오내비')">카카오내비</button>
        <button class="nav-chip" onclick="openNav('티맵')">티맵</button>
      </div>
    </div>
    <div class="mv-photos">
      <div class="mv-ph-top">📷 현장 사진<span class="ph-count" id="ph-count">올린 사진 {int(photo_count)}장 (최대 20)</span></div>
      <div class="ph-help">촬영하거나 앨범에서 골라 올리면 대표님 앱에 자동으로 쌓여요. (한 현장 20장까지)</div>
      <div class="ph-actions">
        <button class="ph-btn" id="ph-cam" onclick="pickCamera()">📸 촬영</button>
        <button class="ph-btn" id="ph-alb" onclick="pickAlbum()">🖼️ 앨범</button>
      </div>
      <div class="photo-grid" id="mv-photo-grid">{done_tiles}</div>
    </div>
    <div class="mv-photos">
      <div class="mv-ph-top">✏️ 현장 메모</div>
      <div class="ph-help">현장에서 느낀 특이사항을 적어 보내면 대표님께 전달돼요. (예: 곰팡이 심함 · 자재 추가 필요 · 고객 부재)</div>
      <textarea id="mv-note-input" class="mv-note-input" placeholder="현장 특이사항을 적어주세요"></textarea>
      <button class="ph-btn" id="mv-note-send" onclick="sendNote()" style="margin-top:10px">📨 대표님께 보내기</button>
      <div class="mv-note-list" id="mv-note-list">{sent_notes}</div>
    </div>
    '''


def _build_next_block_html(items: list[dict]) -> str:
    """다음 일정 블록 (today 제외). 탭하면 그 현장 주소 복사·내비 펼침(미리 길 찾기)."""
    import html as _html
    upcoming = [it for it in (items or []) if not it.get("is_today")]
    if not upcoming:
        return ""
    rows = []
    for idx, it in enumerate(upcoming[:5]):
        name = _html.escape(str(it.get("customer_label") or "현장"))
        when = _html.escape(str(it.get("when") or ""))
        time = _html.escape(str(it.get("time") or ""))
        addr_raw = str(it.get("addr") or "").strip()
        addr = _html.escape(addr_raw)
        addr_js = _html.escape(addr_raw, quote=True).replace("'", "\\'")
        # 주소 있으면 탭 → 복사·내비 펼침. 없으면 그냥 정보 카드(주소 미정).
        if addr_raw:
            actions = (
                f'<div class="next-act" id="next-act-{idx}" style="display:none">'
                f'<div style="margin-top:8px"><button class="hbtn" onclick="event.stopPropagation();copyText(\'{addr_js}\')">📋 주소 복사</button></div>'
                f'<div class="navchips">'
                f'<button class="nav-chip" onclick="event.stopPropagation();openNavApp(\'카카오맵\',\'{addr_js}\')">카카오맵</button>'
                f'<button class="nav-chip" onclick="event.stopPropagation();openNavApp(\'카카오내비\',\'{addr_js}\')">카카오내비</button>'
                f'<button class="nav-chip" onclick="event.stopPropagation();openNavApp(\'티맵\',\'{addr_js}\')">티맵</button>'
                f'</div></div>'
            )
            chev = '<span class="next-chev" id="next-chev-' + str(idx) + '">▾</span>'
            rows.append(
                f'<div class="card next-card" onclick="toggleNext({idx})">'
                f'<div class="row"><span class="hd"></span><span class="name">{name}</span>'
                f'<span class="time">{when} {time}</span>{chev}</div>'
                f'<div class="preview">📍 {addr}</div>{actions}</div>'
            )
        else:
            rows.append(
                f'<div class="card">'
                f'<div class="row"><span class="hd"></span><span class="name">{name}</span>'
                f'<span class="time">{when} {time}</span></div>'
                f'<div class="preview" style="color:var(--t3)">📍 주소 미정</div></div>'
            )
    return '<div class="sec-sub">다음 일정 <span style="font-weight:600;color:var(--t3)">· 탭하면 길찾기</span></div>' + "".join(rows)


def _expiry_label(expires_at_ms: int) -> str:
    """epoch ms → '5/31 (일요일)' 형태 (만료 안내 표시용)."""
    import datetime
    dt = datetime.datetime.utcfromtimestamp(expires_at_ms / 1000) + datetime.timedelta(hours=9)
    wn = ["월","화","수","목","금","토","일"]
    return f"{dt.month}/{dt.day} ({wn[dt.weekday()]}요일)"


def _short_date_label(ms: int) -> str:
    """epoch ms → '6/5 (목)' 형태 (오늘 현장 날짜 헤더용, KST)."""
    import datetime
    dt = datetime.datetime.utcfromtimestamp(ms / 1000) + datetime.timedelta(hours=9)
    wn = ["월","화","수","목","금","토","일"]
    return f"{dt.month}/{dt.day} ({wn[dt.weekday()]})"


@app.get("/team/member/{token}", response_class=HTMLResponse)
async def team_member_page(token: str) -> HTMLResponse:
    """팀원 브라우저용 화면 (프로토 openMemberView 기반 + 사장님 승인 UX 보강 2026-06-05).

    보강(사장님 'D·전부 통합' 선택): 업체 브랜드 헤더 / 오늘 날짜·D-day / 정보 아이콘 줄맞춤 /
    배정→출발→도착→완료 진행 단계바(탭) + 하단 고정 액션바 / 사진 촬영·앨범 분리 + X/20 + 진행률.
    """
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

    # 오늘 진행 단계(출발/도착/완료) — 오늘 0시(KST) 이후 이벤트로 복원(새로고침해도 단계 유지).
    kst_day_start = ((now + 9 * 3600_000) // 86_400_000) * 86_400_000 - 9 * 3600_000
    status = {"departed": False, "arrived": False, "completed": False}
    notes: list = []
    ph_rows: list = []
    with db_conn() as con:
        ev_rows = con.execute(
            "SELECT event_type, payload_json, created_at_ms, read_at_ms, reply_text, reply_at_ms "
            "FROM team_member_events "
            "WHERE token = ? AND created_at_ms >= ? ORDER BY created_at_ms ASC",
            (token, kst_day_start),
        ).fetchall()
        for et, pj, cms, read_at, reply_text, reply_at in ev_rows:
            if et in status:
                status[et] = True
            elif et == "note":
                txt = ""
                try:
                    txt = (json.loads(pj) or {}).get("text", "") if pj else ""
                except json.JSONDecodeError:
                    txt = ""
                if txt:
                    _dtn = _dt.datetime.utcfromtimestamp(cms / 1000) + _dt.timedelta(hours=9)
                    read_label = None
                    if read_at:
                        _dr = _dt.datetime.utcfromtimestamp(read_at / 1000) + _dt.timedelta(hours=9)
                        read_label = f"{_dr.month}/{_dr.day} {_dr.hour:02d}:{_dr.minute:02d}"
                    notes.append({
                        "text": txt, "time": f"{_dtn.hour:02d}:{_dtn.minute:02d}",
                        "read": read_label, "reply": (reply_text or "").strip() or None,
                    })
        # 오늘 현장 사진 — 이미 올린 사진(고객 기준) id+업로더. 썸네일 + 내가 올린 것만 ✕(삭제).
        cp = str((today or {}).get("customer_phone") or "").strip()
        if cp:
            digits = "".join(ch for ch in cp if ch.isdigit())
            suffix_8 = digits[-8:] if len(digits) >= 8 else digits
            ph_rows = con.execute(
                """
                SELECT photo_id, member_id FROM team_site_photos
                WHERE owner_phone = ?
                  AND (
                    customer_phone = ?
                    OR REPLACE(REPLACE(REPLACE(REPLACE(IFNULL(customer_phone,''), '-', ''), ' ', ''), '+', ''), '_', '') LIKE ?
                  )
                ORDER BY uploaded_at_ms ASC
                """,
                (owner_phone, cp, f"%{suffix_8}"),
            ).fetchall()
        # last_accessed 기록
        con.execute("UPDATE team_member_links SET last_accessed_ms = ? WHERE token = ?", (now, token))
        con.commit()
    photo_count = len(ph_rows)
    tok_q = urllib.parse.quote(token, safe="")
    # 내가(이 토큰 팀원이) 올린 사진만 삭제 가능(own=True). 남·사장님 사진은 삭제 버튼 X.
    photos = [
        {"id": pid, "url": f"/api/team/photo/{pid}?token={tok_q}", "own": (mid == member_id)}
        for pid, mid in ph_rows
    ]

    owner_label = "대표님"
    biz = _fetch_owner_biz_name(owner_phone)
    if biz:
        owner_label = biz + " 대표님"
    biz_header = biz or "RING-GO"

    date_label = ""
    if today and today.get("scheduled_at_ms"):
        try:
            date_label = _short_date_label(int(today["scheduled_at_ms"]))
        except (TypeError, ValueError):
            date_label = ""

    # 추가29 (2026-06-15) — 캘린더용 안전 items (고객 phone·매출 제거)
    safe_items_for_cal = []
    for it in items:
        if not isinstance(it, dict):
            continue
        safe_items_for_cal.append({
            "scheduled_at_ms": it.get("scheduled_at_ms") or 0,
            "days": int(it.get("days") or 1),
            "time": (it.get("time") or "")[:8],
            "customer_label": (it.get("customer_label") or "")[:60],
            "addr": (it.get("addr") or "")[:120],
            "work_summary": (it.get("work_summary") or "")[:80],
            "team_memo": (it.get("team_memo") or "")[:200],
            "is_today": bool(it.get("is_today")),
        })
    calendar_block_html = (
        '<div class="cal-card">'
        '<div class="cal-head"><h3 id="cal-title">캘린더</h3>'
        '<div class="cal-nav">'
        '<button type="button" onclick="calPrev()">‹</button>'
        '<button type="button" onclick="calToday()">오늘</button>'
        '<button type="button" onclick="calNext()">›</button>'
        '</div></div>'
        '<div class="cal-week">'
        '<span class="sun">일</span><span>월</span><span>화</span><span>수</span><span>목</span><span>금</span><span class="sat">토</span>'
        '</div>'
        '<div class="cal-grid" id="cal-grid"></div>'
        '<div class="cal-day-list" id="cal-day-list"></div>'
        '</div>'
    )

    page = TEAM_MEMBER_HTML_TEMPLATE.format(
        member_name_html=_html.escape(member_name or "팀원"),
        owner_label_html=_html.escape(owner_label),
        biz_header_html=_html.escape(biz_header),
        calendar_block=calendar_block_html,
        today_block=_build_today_card_html(today, date_label, photos, notes),
        next_block=_build_next_block_html(items),
        token_js=_html.escape(token, quote=True),
        status_js=json.dumps(status),
        photo_count_js=str(int(photo_count)),
        items_for_cal_js=json.dumps(safe_items_for_cal, ensure_ascii=False),
    )
    return HTMLResponse(content=page)


# ─── 추가29 (2026-06-15) — PWA web app manifest + 아이콘 ───

@app.get("/manifest/team-member.webmanifest")
async def team_member_manifest(token: str):
    """PWA manifest — start_url 이 해당 팀원의 영구 링크. 사장님 상호로 name 동적 생성."""
    biz = None
    with db_conn() as con:
        row = con.execute(
            "SELECT owner_phone FROM team_member_links WHERE token = ?",
            (token,),
        ).fetchone()
    if row:
        biz = _fetch_owner_biz_name(row[0])
    name = (biz + " 일정") if biz else "내 일정"
    short_name = (biz[:8] + " 일정") if biz and len(biz) > 8 else (biz + " 일정" if biz else "내 일정")
    start = f"/team/member/{token}"
    manifest = {
        "name": name,
        "short_name": short_name,
        "start_url": start,
        "scope": start,
        "display": "standalone",
        "background_color": "#F4F5F7",
        "theme_color": "#3182F6",
        "icons": [
            {"src": "/manifest/team-member-icon.svg", "sizes": "any", "type": "image/svg+xml", "purpose": "any maskable"},
            {"src": "/manifest/team-member-icon.svg", "sizes": "192x192", "type": "image/svg+xml"},
            {"src": "/manifest/team-member-icon.svg", "sizes": "512x512", "type": "image/svg+xml"},
        ],
    }
    return JSONResponse(content=manifest, media_type="application/manifest+json")


@app.get("/manifest/team-member-icon.svg")
async def team_member_icon():
    svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">'
        '<rect width="512" height="512" rx="96" fill="#3182F6"/>'
        '<text x="256" y="320" text-anchor="middle" font-family="-apple-system,system-ui,sans-serif" '
        'font-size="280" font-weight="900" fill="#fff">📅</text>'
        '</svg>'
    )
    return Response(content=svg, media_type="image/svg+xml")


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


# ============================================================================
# 추가86 — 앱 회원가입: SMS 인증번호 + 자동 등업 cap
#   POST /api/auth/request-code {phone} → 6자리 인증번호 문자 발송 (SOLAPI)
#   POST /api/auth/verify-code {phone, code} → 검증 + 자동 등업:
#     - 이미 멤버(whitelist) → status="member"
#     - 신규 + 현원 < AUTO_ENROLL_CAP → whitelist 자동 등록 + 무료 60일 → status="enrolled"
#     - cap 초과 → beta_signups 대기열 → status="waitlisted" (사장님이 멤버 관리에서 등업)
# 어뷰징 방파제: 번호당 5회/일, 재발송 60초 간격, 전체 500회/일, 검증 5회 실패 시 폐기.
# ============================================================================

import hmac as _hmac  # noqa: E402
import secrets as _secrets_auth  # noqa: E402


async def _send_sms_solapi(to_phone: str, text: str) -> None:
    """SOLAPI 문자 발송. env (SOLAPI_API_KEY/SECRET/SENDER) 없으면 503."""
    if not (SOLAPI_API_KEY and SOLAPI_API_SECRET and SOLAPI_SENDER):
        raise HTTPException(
            503,
            "문자 발송 설정이 아직 안 됐습니다 (SOLAPI env 필요 — plist 에 "
            "SOLAPI_API_KEY / SOLAPI_API_SECRET / SOLAPI_SENDER 추가)"
        )
    date_iso = _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    salt = _secrets_auth.token_hex(16)
    signature = _hmac.new(
        SOLAPI_API_SECRET.encode(), (date_iso + salt).encode(), hashlib.sha256
    ).hexdigest()
    headers = {
        "Authorization": (
            f"HMAC-SHA256 apiKey={SOLAPI_API_KEY}, date={date_iso}, "
            f"salt={salt}, signature={signature}"
        ),
        "Content-Type": "application/json",
    }
    payload = {"message": {"to": to_phone, "from": SOLAPI_SENDER, "text": text}}
    async with httpx.AsyncClient(timeout=10.0) as client:
        r = await client.post(
            "https://api.solapi.com/messages/v4/send", json=payload, headers=headers
        )
        if r.status_code >= 400:
            print(f"[auth/sms] SOLAPI 실패 {r.status_code}: {r.text[:200]}")
            raise HTTPException(502, "인증 문자 발송에 실패했습니다. 잠시 후 다시 시도해주세요.")


class AuthCodeRequest(BaseModel):
    phone: str = ""


class AuthVerifyRequest(BaseModel):
    phone: str = ""
    code: str = ""


@app.post("/api/auth/request-code")
async def auth_request_code(req: AuthCodeRequest) -> dict:
    phone = _norm_phone(req.phone)
    if not phone or len(phone) < 10:
        raise HTTPException(400, "전화번호 형식 오류")
    now = _now_ms()
    today = _dt.datetime.fromtimestamp(now / 1000, tz=_KST).strftime("%Y-%m-%d")
    with db_conn() as con:
        # 전체 하루 발송 한도 (비용 방파제)
        total_today = con.execute(
            "SELECT COALESCE(SUM(sent_today),0) FROM auth_codes WHERE sent_day = ?",
            (today,),
        ).fetchone()[0]
        if total_today >= AUTH_CODE_GLOBAL_PER_DAY:
            print(f"[auth/request] GLOBAL CAP {total_today}")
            raise HTTPException(429, "오늘 인증 요청이 몰렸어요. 내일 다시 시도해주세요.")
        row = con.execute(
            "SELECT last_sent_ms, sent_today, sent_day FROM auth_codes WHERE phone = ?",
            (phone,),
        ).fetchone()
        sent_today = 0
        if row:
            last_sent, st, sd = row
            if now - last_sent < AUTH_CODE_MIN_INTERVAL_SEC * 1000:
                raise HTTPException(429, "잠시 후 다시 요청해주세요 (1분 간격)")
            sent_today = st if sd == today else 0
            if sent_today >= AUTH_CODE_MAX_PER_DAY:
                raise HTTPException(429, "오늘 이 번호의 인증 요청 한도를 넘었어요 (하루 5회)")
        code = f"{_secrets_auth.randbelow(1_000_000):06d}"
        con.execute(
            "INSERT OR REPLACE INTO auth_codes "
            "(phone, code, expires_at_ms, attempts, last_sent_ms, sent_today, sent_day) "
            "VALUES (?, ?, ?, 0, ?, ?, ?)",
            (phone, code, now + AUTH_CODE_TTL_SEC * 1000, now, sent_today + 1, today),
        )
        con.commit()
    # 추가86c — 브랜딩 문구 포함 (사장님 요청). 90바이트 이내 = 단문 SMS 요금 유지.
    await _send_sms_solapi(
        phone,
        f"[시공막내] 인증번호 [{code}]\n사장님의 막내 비서, 시공막내입니다. (5분 이내 입력)"
    )
    print(f"[auth/request] {phone} 발송 ({sent_today + 1}/{AUTH_CODE_MAX_PER_DAY})")
    return {"ok": True, "expiresInSec": AUTH_CODE_TTL_SEC}


@app.post("/api/auth/verify-code")
async def auth_verify_code(req: AuthVerifyRequest) -> dict:
    phone = _norm_phone(req.phone)
    code = (req.code or "").strip()
    if not phone or not code:
        raise HTTPException(400, "phone/code 필수")
    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            "SELECT code, expires_at_ms, attempts FROM auth_codes WHERE phone = ?",
            (phone,),
        ).fetchone()
        if not row:
            raise HTTPException(400, "인증번호를 먼저 요청해주세요")
        real_code, expires, attempts = row
        if now > expires:
            raise HTTPException(400, "인증번호가 만료됐어요. 다시 요청해주세요")
        if attempts >= 5:
            raise HTTPException(429, "시도 횟수 초과 — 인증번호를 다시 요청해주세요")
        if code != real_code:
            con.execute(
                "UPDATE auth_codes SET attempts = attempts + 1 WHERE phone = ?", (phone,))
            con.commit()
            raise HTTPException(400, "인증번호가 틀렸어요")
        # 성공 — 코드 폐기 (재사용 방지)
        con.execute("DELETE FROM auth_codes WHERE phone = ?", (phone,))

        # ── 자동 등업 로직 ──
        wl = con.execute(
            "SELECT free_until_ms FROM beta_whitelist WHERE phone = ?", (phone,)
        ).fetchone()
        if wl:
            con.commit()
            print(f"[auth/verify] {phone} → member (기존)")
            return {"ok": True, "status": "member", "freeUntilMs": wl[0]}
        current = con.execute("SELECT COUNT(*) FROM beta_whitelist").fetchone()[0]
        if current < AUTO_ENROLL_CAP:
            free_until = now + FREE_TRIAL_DAYS * 86_400_000
            con.execute(
                "INSERT INTO beta_whitelist "
                "(phone, name, memo, added_at_ms, use_count, free_until_ms) "
                "VALUES (?, NULL, '앱 자동가입', ?, 0, ?)",
                (phone, now, free_until),
            )
            con.commit()
            print(f"[auth/verify] {phone} → enrolled (자동 등업 {current + 1}/{AUTO_ENROLL_CAP})")
            return {"ok": True, "status": "enrolled", "freeUntilMs": free_until,
                    "freeDays": FREE_TRIAL_DAYS}
        # cap 초과 → 대기열 (beta_signups). 이미 신청돼 있으면 그대로.
        existing_sg = con.execute(
            "SELECT status FROM beta_signups WHERE phone = ?", (phone,)).fetchone()
        if not existing_sg:
            con.execute(
                """INSERT INTO beta_signups
                   (phone, industry, region, monthly_inquiries, note, agreed_at_ms,
                    source, ip, ua, status, created_at_ms, updated_at_ms, business_name)
                   VALUES (?, '', '', '', '', ?, 'app/auto-signup', '', '', 'waitlist', ?, ?, '')""",
                (phone, now, now, now),
            )
        con.commit()
        print(f"[auth/verify] {phone} → waitlisted (cap {AUTO_ENROLL_CAP} 초과)")
        return {"ok": True, "status": "waitlisted"}


# ============================================================================
# 추가77 — POST /extract-pricing (문자 기반 가격 추출)
# 명세: docs/SERVER_HANDOFF_extract_pricing.md (2026-07-02 안드로이드 핸드오프)
# ⚠️ 루트 경로 (/api/ 밑 아님) — 앱이 baseUrl/extract-pricing 로 호출.
# 계약:
#   - priceWon = '원 단위' 정수 (35만원 → 350000). 앱은 ×10000 안 함.
#   - unit = FLAT|PYEONG, category = NEW|OLD|COMMON (대문자). 이상값은 FLAT/COMMON 보정.
#   - basisText = 조건 명시된 것만, 없으면 null. confidence<0.5 버림.
#   - 캐시 키 = deviceId (summary_cache 재활용, endpoint='extract-pricing').
#     같은 deviceId + 더 새 문자 없음(max dateMs <= 캐시 기준) → 캐시 반환 (재과금 방지).
#   - 프라이버시: 문자 본문은 로그에 안 남김 (개수/latency 만 print). 캐시엔 items 만.
# ============================================================================

_EXTRACT_PRICING_SYSTEM = """너는 한국 인테리어/시공 사장님의 '보낸 문자'들을 읽고, 그 사장님의 가격표를 복원하는 추출기다.
입력은 사장님이 고객에게 보낸 문자 여러 개다. 다음을 지켜라:

1. 견적/단가 문자에서만 항목을 뽑아라. 아래는 '견적 아님' → 무시하라:
   - 예약금/계약금 안내(예: "예약금 100,000원"), 잔금 통보(예: "잔금 125만원입니다"), 입금확인,
     계좌 안내, 접수서 링크, 시공 완료 인사, 앱/서비스 홍보 문자.
   - 즉 '특정 시공 항목의 단가'가 아니면 버려라. 총액·예약금·잔금은 항목이 아니다.
2. 같은 항목이 여러 문자에 다른 가격으로 나오면: 가장 최근(dateMs 큰) 값을 우선. 신축/구축이 다르면 category 로 분리.
3. 가격 '기준'(조건부 가격)이 문자에 명시되면 basisText 한 줄로. 없으면 null. 지어내지 마라.
4. 확신 없으면 항목을 만들지 마라(confidence<0.5 는 버려라). 없는 항목/가격을 상상하지 마라.
5. 항목명은 초보 사장님이 고객에게 그대로 읽어줄 자연어. 6~15개 정도로 정리(중복 병합).
6. priceWon 은 원 단위 정수(만원 배수 권장). 35만원 → 350000.

출력은 아래 JSON 스키마만. 설명 문장 붙이지 마라:
{"items": [{"title": "샤워부스 벽 3면", "priceWon": 350000, "unit": "FLAT", "category": "COMMON", "basisText": null, "confidence": 0.9}]}
- unit: "FLAT"(정액/개당) | "PYEONG"(평당). 모르면 FLAT.
- category: "NEW"(신축) | "OLD"(구축) | "COMMON"(공통). 모르면 COMMON.
"""

_EXTRACT_PRICING_ENDPOINT = "extract-pricing"
_EXTRACT_PRICING_MAX_CANDIDATES = 200   # 명세: 보통 수십~150개
_EXTRACT_PRICING_BODY_CAP = 500         # 문자 1건 본문 컷 (토큰 예산)
_EXTRACT_PRICING_TOTAL_CHAR_CAP = 80_000
_EXTRACT_PRICING_MAX_ITEMS = 20
_EXTRACT_PRICING_BASIS_CAP = 40


class ExtractPricingCandidate(BaseModel):
    body: str = ""
    dateMs: Optional[int] = None


class ExtractPricingRequest(BaseModel):
    deviceId: str = ""
    ownerTrade: Optional[str] = None
    candidates: list = []  # [{body, dateMs}] — camelCase 그대로 (수동 파싱, 이상 행 skip)


def _extract_pricing_cache_get(device_id: str, max_date_ms: int) -> Optional[dict]:
    """summary_cache hit — 같은 deviceId 이고 캐시 생성 기준(latest_msg_ts)보다
    새 문자가 없으면 반환. 더 새 문자가 오면 miss (재추출 허용)."""
    with db_conn() as con:
        row = con.execute(
            "SELECT latest_msg_ts, response_json FROM summary_cache "
            "WHERE phone = ? AND endpoint = ? "
            "ORDER BY generated_at_ms DESC LIMIT 1",
            (device_id, _EXTRACT_PRICING_ENDPOINT),
        ).fetchone()
    if not row:
        return None
    cached_ts, response_json = row
    if max_date_ms > (cached_ts or 0):
        return None  # 캐시 이후 더 새 견적 문자 있음 → 재추출
    try:
        return json.loads(response_json)
    except Exception:
        return None


def _extract_pricing_cache_set(device_id: str, max_date_ms: int, resp: dict) -> None:
    now = _now_ms()
    with db_conn() as con:
        con.execute(
            "INSERT OR REPLACE INTO summary_cache "
            "(phone, endpoint, latest_msg_ts, response_json, generated_at_ms) "
            "VALUES (?, ?, ?, ?, ?)",
            (device_id, _EXTRACT_PRICING_ENDPOINT, max_date_ms,
             json.dumps(resp, ensure_ascii=False), now),
        )
        con.commit()


def _coerce_pricing_items(raw_items) -> list:
    """LLM 출력 → 계약 보정. priceWon 원 단위 검증이 최우선."""
    out = []
    if not isinstance(raw_items, list):
        return out
    for it in raw_items:
        if not isinstance(it, dict):
            continue
        title = str(it.get("title") or "").strip()[:60]
        if not title:
            continue
        # priceWon — 원 단위 정수. LLM 이 만원 단위로 미끄러진 경우(< 1만) 보정.
        try:
            price = int(round(float(it.get("priceWon"))))
        except (TypeError, ValueError):
            continue
        if price <= 0:
            continue
        if price < 10_000:
            # "35" / "35만" 식 만원 단위 실수 → 원 단위로 복원 (계약 방어)
            print(f"[extract-pricing] priceWon<1만 보정: {title} {price} → {price * 10000}")
            price = price * 10_000
        if price > 100_000_000:
            continue  # 1억 초과 단일 항목 = 오추출로 간주
        unit = str(it.get("unit") or "").strip().upper()
        if unit not in ("FLAT", "PYEONG"):
            unit = "FLAT"
        category = str(it.get("category") or "").strip().upper()
        if category not in ("NEW", "OLD", "COMMON"):
            category = "COMMON"
        basis = it.get("basisText")
        if basis is not None:
            basis = " ".join(str(basis).split()).strip()[:_EXTRACT_PRICING_BASIS_CAP]
            if not basis:
                basis = None
        try:
            confidence = float(it.get("confidence", 0.0))
        except (TypeError, ValueError):
            confidence = 0.0
        if confidence < 0.5:
            continue
        confidence = max(0.0, min(1.0, confidence))
        out.append({
            "title": title,
            "priceWon": price,
            "unit": unit,
            "category": category,
            "basisText": basis,
            "confidence": round(confidence, 2),
        })
        if len(out) >= _EXTRACT_PRICING_MAX_ITEMS:
            break
    return out


@app.post("/extract-pricing")
async def extract_pricing(req: ExtractPricingRequest) -> dict:
    """사장님이 보낸 견적 문자들 → Claude Sonnet 1콜 → 구조화 가격표 items."""
    device_id = (req.deviceId or "").strip()
    if not device_id:
        raise HTTPException(400, "deviceId 필수")

    # candidates 정리 (이상 행 skip, 본문 컷, 최신순 유지)
    cands = []
    for c in (req.candidates or [])[:_EXTRACT_PRICING_MAX_CANDIDATES]:
        if not isinstance(c, dict):
            continue
        body = str(c.get("body") or "").strip()
        if not body:
            continue
        try:
            date_ms = int(c.get("dateMs") or 0)
        except (TypeError, ValueError):
            date_ms = 0
        cands.append({"body": body[:_EXTRACT_PRICING_BODY_CAP], "dateMs": date_ms})
    if not cands:
        return {"status": "ready", "items": []}
    max_date_ms = max(c["dateMs"] for c in cands)

    # 캐시 (재과금 방지)
    cached = _extract_pricing_cache_get(device_id, max_date_ms)
    if cached is not None:
        print(f"[extract-pricing] cache hit device={device_id[:12]}… "
              f"items={len(cached.get('items') or [])}")
        return cached

    # user 메시지 — 본문은 여기서만 사용, 로그 금지 (프라이버시)
    trade = (req.ownerTrade or "").strip()[:30]
    lines = []
    total_chars = 0
    for c in cands:
        line = f"[{c['dateMs']}] {c['body']}"
        total_chars += len(line)
        if total_chars > _EXTRACT_PRICING_TOTAL_CHAR_CAP:
            break
        lines.append(line)
    user_msg = (
        (f"[업종 힌트 (참고만, 문자 내용이 우선)] {trade}\n\n" if trade else "")
        + "[사장님이 고객에게 보낸 문자 후보들 — 최신순, 형식: [dateMs] 본문]\n"
        + "\n---\n".join(lines)
    )

    t0 = time.time()
    try:
        parsed, response = await call_claude_json(
            system_prompt=_EXTRACT_PRICING_SYSTEM,
            user_msg=user_msg,
            max_tokens=2000,
            model=CLAUDE_MODEL,
        )
    except Exception as e:
        print(f"[extract-pricing] LLM 실패 device={device_id[:12]}… "
              f"{type(e).__name__}: {e}")
        raise HTTPException(502, "가격 추출에 실패했습니다. 잠시 후 다시 시도해주세요.")
    _log_llm_usage_from_response(_EXTRACT_PRICING_ENDPOINT, response)

    items = _coerce_pricing_items(parsed.get("items"))
    resp = {"status": "ready", "items": items}
    _extract_pricing_cache_set(device_id, max_date_ms, resp)
    print(f"[extract-pricing] ready device={device_id[:12]}… "
          f"candidates={len(cands)} items={len(items)} "
          f"latency={time.time() - t0:.1f}s")
    return resp


# ============================================================================
# 추가78 — POST /pricing/starter + GET /pricing/starter/{deviceId}
# 명세: docs/SERVER_HANDOFF_pricing_starter.md (2026-07-02 안드로이드 핸드오프)
# 용도: 온보딩 업종 스타터 가격표. 앱이 15개 업종 내장했으므로 실제로는
#       "직접 입력한 커스텀 업종" 롱테일만 여기로 옴.
# 계약: priceWon '원 단위' 정수 (만원 배수). unit=FLAT|PYEONG, category=NEW|OLD|COMMON.
# 줄눈 = LLM 안 부르고 하드코딩 18항목 (앱 DefaultPricingItems 와 동일, 비용 0).
# 캐시: summary_cache (phone=deviceId, endpoint='pricing-starter',
#       latest_msg_ts=crc32(trade)) — 같은 기기·같은 업종 재요청 = 캐시 (재과금 방지).
# ============================================================================

_PRICING_STARTER_ENDPOINT = "pricing-starter"

# 줄눈 하드코딩 (명세 그대로 — NEW 9 + OLD 9). confidence=0.95 (검증된 실측값).
_JULNUN_STARTER_ITEMS = [
    {"title": "욕조 있는 화장실 바닥 1곳", "priceWon": 400000, "unit": "FLAT", "category": "NEW", "confidence": 0.95},
    {"title": "샤워부스 있는 화장실 바닥 1곳", "priceWon": 450000, "unit": "FLAT", "category": "NEW", "confidence": 0.95},
    {"title": "샤워부스 벽 3면", "priceWon": 350000, "unit": "FLAT", "category": "NEW", "confidence": 0.95},
    {"title": "욕조벽 3면", "priceWon": 350000, "unit": "FLAT", "category": "NEW", "confidence": 0.95},
    {"title": "화장실 전체 벽 (추가 시)", "priceWon": 700000, "unit": "FLAT", "category": "NEW", "confidence": 0.95},
    {"title": "세탁실 (폴리우레아)", "priceWon": 150000, "unit": "FLAT", "category": "NEW", "confidence": 0.95},
    {"title": "베란다 (폴리우레아)", "priceWon": 150000, "unit": "FLAT", "category": "NEW", "confidence": 0.95},
    {"title": "현관", "priceWon": 50000, "unit": "FLAT", "category": "NEW", "confidence": 0.95},
    {"title": "거실 타일", "priceWon": 1500000, "unit": "FLAT", "category": "NEW", "confidence": 0.95},
    {"title": "욕조 있는 화장실 바닥 1곳", "priceWon": 500000, "unit": "FLAT", "category": "OLD", "confidence": 0.95},
    {"title": "샤워부스 있는 화장실 바닥 1곳", "priceWon": 550000, "unit": "FLAT", "category": "OLD", "confidence": 0.95},
    {"title": "샤워부스 벽 3면", "priceWon": 350000, "unit": "FLAT", "category": "OLD", "confidence": 0.95},
    {"title": "욕조벽 3면", "priceWon": 350000, "unit": "FLAT", "category": "OLD", "confidence": 0.95},
    {"title": "화장실 전체 벽 (추가 시)", "priceWon": 700000, "unit": "FLAT", "category": "OLD", "confidence": 0.95},
    {"title": "세탁실 (폴리우레아)", "priceWon": 150000, "unit": "FLAT", "category": "OLD", "confidence": 0.95},
    {"title": "베란다 (폴리우레아)", "priceWon": 150000, "unit": "FLAT", "category": "OLD", "confidence": 0.95},
    {"title": "현관", "priceWon": 100000, "unit": "FLAT", "category": "OLD", "confidence": 0.95},
    {"title": "거실 타일", "priceWon": 1500000, "unit": "FLAT", "category": "OLD", "confidence": 0.95},
]

_PRICING_STARTER_SYSTEM = """너는 한국 인테리어/시공 견적 데이터 생성기다.
항목명은 초보 사장님이 고객에게 그대로 읽어줄 수 있는 자연어.
가격은 2025~2026 한국(수도권) 시세의 '보수적 중앙값'을 원 단위 정수(만원 배수)로.
확신 없으면 항목을 만들지 마라. 없는 항목/가격을 지어내지 마라.

출력은 아래 JSON 스키마만. 설명 문장 붙이지 마라:
{"items": [{"title": "벽걸이 에어컨 설치", "priceWon": 100000, "unit": "FLAT", "category": "COMMON", "confidence": 0.7}]}
- priceWon: 원 단위 정수(만원 배수). 10만원 → 100000.
- unit: "FLAT"(정액/개당) | "PYEONG"(평당). 모르면 FLAT.
- category: "NEW"(신축) | "OLD"(구축) | "COMMON"(공통). 모르면 COMMON.
"""


class PricingStarterRequest(BaseModel):
    deviceId: str = ""
    ownerTrade: str = ""
    ownerRegions: list = []


def _coerce_starter_items(raw_items) -> list:
    """스타터용 보정 — 명세: confidence<0.4 버림, priceWon 만원 배수 반올림."""
    out = []
    if not isinstance(raw_items, list):
        return out
    for it in raw_items:
        if not isinstance(it, dict):
            continue
        title = str(it.get("title") or "").strip()[:60]
        if not title:
            continue
        try:
            price = int(round(float(it.get("priceWon"))))
        except (TypeError, ValueError):
            continue
        if price <= 0:
            continue
        if price < 10_000:
            print(f"[pricing/starter] priceWon<1만 보정: {title} {price} → {price * 10000}")
            price = price * 10_000
        if price > 100_000_000:
            continue
        price = int(round(price / 10_000)) * 10_000  # 만원 배수 반올림
        if price <= 0:
            continue
        unit = str(it.get("unit") or "").strip().upper()
        if unit not in ("FLAT", "PYEONG"):
            unit = "FLAT"
        category = str(it.get("category") or "").strip().upper()
        if category not in ("NEW", "OLD", "COMMON"):
            category = "COMMON"
        try:
            confidence = float(it.get("confidence", 0.0))
        except (TypeError, ValueError):
            confidence = 0.0
        if confidence < 0.4:
            continue
        out.append({
            "title": title,
            "priceWon": price,
            "unit": unit,
            "category": category,
            "confidence": round(max(0.0, min(1.0, confidence)), 2),
        })
        if len(out) >= 15:
            break
    return out


def _pricing_starter_cache_key(trade: str) -> int:
    return binascii.crc32(trade.encode("utf-8"))


@app.post("/pricing/starter")
async def pricing_starter(req: PricingStarterRequest) -> dict:
    """온보딩 업종 스타터 — 동기 반환 (명세 허용). 캐시 후 GET 으로도 조회 가능."""
    device_id = (req.deviceId or "").strip()
    if not device_id:
        raise HTTPException(400, "deviceId 필수")
    trade = (req.ownerTrade or "").strip()[:30]
    if not trade:
        raise HTTPException(400, "ownerTrade 필수")
    regions = [str(r).strip()[:20] for r in (req.ownerRegions or [])[:5] if str(r).strip()]
    trade_key = _pricing_starter_cache_key(trade)

    # 캐시 (같은 기기 + 같은 업종)
    with db_conn() as con:
        row = con.execute(
            "SELECT response_json FROM summary_cache "
            "WHERE phone = ? AND endpoint = ? AND latest_msg_ts = ? "
            "ORDER BY generated_at_ms DESC LIMIT 1",
            (device_id, _PRICING_STARTER_ENDPOINT, trade_key),
        ).fetchone()
    if row:
        try:
            cached = json.loads(row[0])
            print(f"[pricing/starter] cache hit device={device_id[:12]}… trade={trade}")
            return cached
        except Exception:
            pass

    # 줄눈 = 하드코딩 (LLM X, 비용 0)
    if "줄눈" in trade:
        resp = {"status": "ready", "items": list(_JULNUN_STARTER_ITEMS)}
    else:
        user_msg = (
            f"업종: {trade}"
            + (f", 지역: {', '.join(regions)}" if regions else "")
            + ". 이 업종에서 가장 흔한 견적 항목 6~12개를 위 JSON 스키마로."
        )
        t0 = time.time()
        try:
            parsed, response = await call_claude_json(
                system_prompt=_PRICING_STARTER_SYSTEM,
                user_msg=user_msg,
                max_tokens=1500,
                model=CLAUDE_MODEL,
            )
        except Exception as e:
            print(f"[pricing/starter] LLM 실패 trade={trade} {type(e).__name__}: {e}")
            raise HTTPException(502, "스타터 가격표 생성에 실패했습니다. 잠시 후 다시 시도해주세요.")
        _log_llm_usage_from_response(_PRICING_STARTER_ENDPOINT, response)
        items = _coerce_starter_items(parsed.get("items"))
        resp = {"status": "ready", "items": items}
        print(f"[pricing/starter] ready device={device_id[:12]}… trade={trade} "
              f"items={len(items)} latency={time.time() - t0:.1f}s")

    now = _now_ms()
    with db_conn() as con:
        con.execute(
            "INSERT OR REPLACE INTO summary_cache "
            "(phone, endpoint, latest_msg_ts, response_json, generated_at_ms) "
            "VALUES (?, ?, ?, ?, ?)",
            (device_id, _PRICING_STARTER_ENDPOINT, trade_key,
             json.dumps(resp, ensure_ascii=False), now),
        )
        con.commit()
    return resp


# ============================================================================
# 추가81 — POST /name-template (템플릿 제목 자동 작명)
# 명세: docs/SERVER_HANDOFF_name_template.md (2026-07-02 안드로이드 핸드오프)
# Haiku 4.5 (초저가). 캐시 불필요 (저장 시 1회). 실패해도 앱은 휴리스틱 fallback.
# ============================================================================

_NAME_TEMPLATE_SYSTEM = """너는 시공 사장님이 고객에게 보내는 문자에 짧은 제목을 붙이는 도우미다.
문자의 '용도'를 나타내는 한글 제목만 출력해라(4~10자, 절대 13자 넘기지 마라). 예: "예약금 안내", "입금 확인", "견적 안내", "시공 전 안내", "부재중 안내".
설명·따옴표·문장부호 없이 제목 텍스트만. 모르면 "안내 문자".
"""


class NameTemplateRequest(BaseModel):
    body: str = ""


@app.post("/name-template")
async def name_template(req: NameTemplateRequest) -> dict:
    """문자 본문 → 짧은 한글 제목 (Haiku 1콜)."""
    body = (req.body or "").strip()
    if not body:
        raise HTTPException(400, "body 필수")
    body = body[:2000]  # 토큰 예산 (제목 짓는 데 앞부분이면 충분)
    try:
        response = await claude_client.messages.create(
            model=HAIKU_MODEL,
            max_tokens=30,
            timeout=CLAUDE_TIMEOUT,
            system=[{"type": "text", "text": _NAME_TEMPLATE_SYSTEM,
                     "cache_control": {"type": "ephemeral"}}],
            messages=[{"role": "user", "content": f"문자: {body}"}],
        )
        _log_llm_usage_from_response("name-template", response)
        parts = [getattr(b, "text", "") for b in response.content
                 if getattr(b, "type", None) == "text"]
        raw = "".join(parts)
    except Exception as e:
        print(f"[name-template] Haiku 실패: {type(e).__name__}: {e}")
        return {"title": "안내 문자"}  # 앱 계약: 200 + title (앱이 휴리스틱 유지 판단)
    # 후처리 — 따옴표/개행/문장부호 제거.
    # 13자 컷 (사장님 보고 2026-07-02: 긴 제목이 앱 UI 에서 잘림 → 13자 제한).
    title = " ".join(raw.split()).strip().strip("\"'""''「」.·:;!?")
    title = title[:13].strip()
    if not title:
        title = "안내 문자"
    return {"title": title}


@app.get("/pricing/starter/{device_id}")
async def pricing_starter_get(device_id: str) -> dict:
    """폴링/조회용 — 그 기기의 가장 최근 스타터 결과. 없으면 pending."""
    device_id = (device_id or "").strip()
    if not device_id:
        raise HTTPException(400, "deviceId 필수")
    with db_conn() as con:
        row = con.execute(
            "SELECT response_json FROM summary_cache "
            "WHERE phone = ? AND endpoint = ? "
            "ORDER BY generated_at_ms DESC LIMIT 1",
            (device_id, _PRICING_STARTER_ENDPOINT),
        ).fetchone()
    if not row:
        return {"status": "pending", "items": []}
    try:
        return json.loads(row[0])
    except Exception:
        return {"status": "pending", "items": []}


# ============================================================================
# 추가119 (2026-07-13) — 본폰 "일정 미러 링크" (읽기전용 뷰어)
# ─────────────────────────────────────────────────────────────────────────────
# 핸드오프: docs/SERVER_HANDOFF_mirror.md
# 사장님 확정: ① 4자리 비번 1회 ② 전화·메모 포함(전부) ③ 범위 = 일정 + 돈 요약
#
#   POST /api/mirror/issue        업무폰A: 링크 발급(+비번) / 이미 있으면 재사용
#   POST /api/mirror/pair/code    업무폰A(또는 뷰어): 6자리 페어 코드 발급
#   POST /api/mirror/pair         업무폰B: 코드로 기존 링크에 합류
#   POST /api/mirror/snapshot     업무폰: 일정+돈 스냅샷 덮어쓰기
#   POST /api/mirror/revoke       링크 폐기 (본폰 분실 대비)
#   GET  /mirror/{token}          본폰: 비번 게이트 → 읽기전용 통합 캘린더 (PWA)
#   POST /mirror/{token}/unlock   비번 확인 → 쿠키 1회 기억
#   GET  /api/mirror/data/{token} 60초 자동 새로고침용 JSON (쿠키 필요)
# ============================================================================
MIRROR_TOKEN_LEN = 12
_MIRROR_PIN_FAILS: dict = {}          # {token: [실패횟수, 최근시각ms]} — 무차별 대입 방지
_MIRROR_PIN_MAX_FAILS = 10
_MIRROR_COOKIE_MAX_AGE = 180 * 24 * 3600   # 180일 — "1회 입력 후 그 폰에서는 기억"


def _mirror_secret() -> str:
    """뷰어 쿠키 서명용 서버 시크릿. 최초 1회 생성해 server_kv 에 보관."""
    import secrets
    with db_conn() as con:
        row = con.execute("SELECT v FROM server_kv WHERE k = 'mirror_secret'").fetchone()
        if row:
            return row[0]
        val = secrets.token_urlsafe(32)
        con.execute("INSERT OR REPLACE INTO server_kv (k, v) VALUES ('mirror_secret', ?)", (val,))
        con.commit()
    return val


def _generate_mirror_token() -> str:
    import secrets
    for _ in range(8):
        tok = "".join(secrets.choice(INTAKE_TOKEN_ALPHABET) for _ in range(MIRROR_TOKEN_LEN))
        with db_conn() as con:
            if not con.execute("SELECT 1 FROM mirror_links WHERE token = ?", (tok,)).fetchone():
                return tok
    raise HTTPException(500, "미러 토큰 생성 실패")


def _mirror_pin_hash(salt: str, pin: str) -> str:
    return hashlib.sha256((salt + "|" + pin).encode("utf-8")).hexdigest()


def _mirror_cookie_name(token: str) -> str:
    return "mv_" + token


def _mirror_cookie_value(token: str, pin_hash: str) -> str:
    import hmac
    return hmac.new(_mirror_secret().encode("utf-8"),
                    (token + "|" + (pin_hash or "")).encode("utf-8"),
                    hashlib.sha256).hexdigest()


def _mirror_unlocked(request: Request, token: str, pin_hash: Optional[str]) -> bool:
    """비번이 없으면 항상 통과. 있으면 쿠키 서명 일치해야 통과."""
    if not pin_hash:
        return True
    import hmac as _hmac
    got = request.cookies.get(_mirror_cookie_name(token)) or ""
    return _hmac.compare_digest(got, _mirror_cookie_value(token, pin_hash))


def _mirror_link_row(token: str) -> Optional[tuple]:
    with db_conn() as con:
        return con.execute(
            "SELECT token, main_key, pin_hash, pin_salt, revoked FROM mirror_links WHERE token = ?",
            (token,),
        ).fetchone()


def _mirror_payload(token: str) -> dict:
    """뷰어/새로고침 공통 — 사업장별 최신 스냅샷 + 돈 요약 합산."""
    with db_conn() as con:
        rows = con.execute(
            "SELECT owner_phone, label, tint, snapshot_json, money_json, updated_at_ms "
            "FROM mirror_sources WHERE token = ? ORDER BY updated_at_ms DESC",
            (token,),
        ).fetchall()
    sources = []
    total_today_in = 0
    total_unpaid = 0
    total_unpaid_cnt = 0
    for (ophone, label, tint, snap, money, upd) in rows:
        try:
            items = json.loads(snap) if snap else []
        except json.JSONDecodeError:
            items = []
        try:
            m = json.loads(money) if money else {}
        except json.JSONDecodeError:
            m = {}
        total_today_in += int(m.get("todayIn") or 0)
        total_unpaid += int(m.get("unpaid") or 0)
        total_unpaid_cnt += int(m.get("unpaidCount") or 0)
        sources.append({
            "ownerPhone": ophone,
            "label": (label or "사업장"),
            "tint": int(tint or 0),
            "items": items,
            "money": m,
            "updatedAtMs": upd,
        })
    return {
        "token": token,
        "sources": sources,
        "money": {"todayIn": total_today_in, "unpaid": total_unpaid,
                  "unpaidCount": total_unpaid_cnt},
        "serverNowMs": _now_ms(),
    }


class MirrorIssueRequest(BaseModel):
    owner_phone: str = ""
    label: str = ""
    pin: Optional[str] = None      # 4자리. 없으면 서버가 랜덤 발급해 응답에 1회만 내려줌
    tint: int = 0


class MirrorPairCodeRequest(BaseModel):
    owner_phone: str = ""          # 이미 등록된 업무폰A (그 폰의 token 을 찾는다)
    token: Optional[str] = None


class MirrorPairRequest(BaseModel):
    code: str = ""
    owner_phone: str = ""          # 합류할 업무폰B
    label: str = ""
    tint: int = 1


class MirrorSnapshotRequest(BaseModel):
    owner_phone: str = ""
    token: Optional[str] = None
    label: Optional[str] = None
    items: list = Field(default_factory=list)
    money: Optional[dict] = None


class MirrorRevokeRequest(BaseModel):
    owner_phone: str = ""
    token: str = ""


@app.post("/api/mirror/issue")
async def mirror_issue(req: MirrorIssueRequest) -> dict:
    """업무폰A — 본폰용 미러 링크 발급(없으면 생성). 그 사업장을 source 로 등록.

    pin 을 안 주면 서버가 4자리를 만들어 **이 응답에서 딱 한 번** 내려준다(해시만 저장).
    """
    import secrets
    ophone = _norm_phone(req.owner_phone)
    if not ophone:
        raise HTTPException(400, "owner_phone 필수")
    label = (req.label or "").strip()[:20] or "내 사업장"
    now = _now_ms()

    with db_conn() as con:
        # 이 업무폰이 이미 어떤 미러 링크에 속해 있으면 그걸 재사용
        row = con.execute(
            "SELECT s.token FROM mirror_sources s JOIN mirror_links l ON l.token = s.token "
            "WHERE s.owner_phone = ? AND l.revoked = 0 LIMIT 1",
            (ophone,),
        ).fetchone()
        pin_plain = None
        if row:
            token = row[0]
            con.execute(
                "UPDATE mirror_sources SET label = ?, tint = ?, updated_at_ms = ? "
                "WHERE token = ? AND owner_phone = ?",
                (label, int(req.tint or 0), now, token, ophone),
            )
        else:
            token = _generate_mirror_token()
            pin_plain = (req.pin or "").strip() or ("%04d" % secrets.randbelow(10000))
            if not (pin_plain.isdigit() and len(pin_plain) == 4):
                raise HTTPException(400, "pin 은 4자리 숫자")
            salt = secrets.token_hex(8)
            con.execute(
                "INSERT INTO mirror_links (token, main_key, pin_hash, pin_salt, created_at_ms, revoked) "
                "VALUES (?, ?, ?, ?, ?, 0)",
                (token, "mk_" + secrets.token_hex(6), _mirror_pin_hash(salt, pin_plain), salt, now),
            )
            con.execute(
                "INSERT INTO mirror_sources (token, owner_phone, label, tint, snapshot_json, "
                "money_json, updated_at_ms) VALUES (?, ?, ?, ?, NULL, NULL, ?)",
                (token, ophone, label, int(req.tint or 0), now),
            )
        con.commit()

    url = f"{INTAKE_PUBLIC_BASE_URL.rstrip('/')}/mirror/{token}"
    print(f"[mirror/issue] {ophone} ({label}) → token={token} new_pin={'Y' if pin_plain else 'N'}")
    out = {"ok": True, "token": token, "url": url, "label": label}
    if pin_plain:
        out["pin"] = pin_plain          # 최초 1회만. 앱이 사장님께 보여주고 저장 안 함.
        out["pinNotice"] = "본폰에서 처음 열 때 한 번 입력하면 그 폰에서는 계속 기억합니다."
    return out


@app.post("/api/mirror/pair/code")
async def mirror_pair_code(req: MirrorPairCodeRequest) -> dict:
    """두 번째 업무폰을 합치기 위한 6자리 코드 발급 (10분 유효)."""
    import secrets
    token = (req.token or "").strip()
    if not token:
        ophone = _norm_phone(req.owner_phone)
        if not ophone:
            raise HTTPException(400, "owner_phone 또는 token 필수")
        with db_conn() as con:
            row = con.execute(
                "SELECT s.token FROM mirror_sources s JOIN mirror_links l ON l.token = s.token "
                "WHERE s.owner_phone = ? AND l.revoked = 0 LIMIT 1",
                (ophone,),
            ).fetchone()
        if not row:
            raise HTTPException(404, "먼저 /api/mirror/issue 로 링크를 발급하세요")
        token = row[0]
    code = "%06d" % secrets.randbelow(1000000)
    expires = _now_ms() + 10 * 60 * 1000
    with db_conn() as con:
        con.execute(
            "INSERT OR REPLACE INTO mirror_pair_codes (code, token, expires_at_ms, used_at_ms) "
            "VALUES (?, ?, ?, NULL)",
            (code, token, expires),
        )
        con.commit()
    return {"ok": True, "code": code, "token": token, "expiresAtMs": expires,
            "validMinutes": 10}


@app.post("/api/mirror/pair")
async def mirror_pair(req: MirrorPairRequest) -> dict:
    """업무폰B — 6자리 코드로 기존 미러 링크에 합류(= source 추가)."""
    code = (req.code or "").strip()
    ophone = _norm_phone(req.owner_phone)
    if not code or not ophone:
        raise HTTPException(400, "code, owner_phone 필수")
    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            "SELECT token, expires_at_ms, used_at_ms FROM mirror_pair_codes WHERE code = ?",
            (code,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "코드가 올바르지 않습니다")
        token, expires, used = row
        if used is not None:
            raise HTTPException(410, "이미 사용된 코드입니다")
        if now > expires:
            raise HTTPException(410, "만료된 코드입니다 (10분)")
        label = (req.label or "").strip()[:20] or "사업장 2"
        con.execute(
            "INSERT OR REPLACE INTO mirror_sources (token, owner_phone, label, tint, "
            "snapshot_json, money_json, updated_at_ms) "
            "VALUES (?, ?, ?, ?, COALESCE((SELECT snapshot_json FROM mirror_sources "
            "WHERE token=? AND owner_phone=?), NULL), NULL, ?)",
            (token, ophone, label, int(req.tint or 1), token, ophone, now),
        )
        con.execute("UPDATE mirror_pair_codes SET used_at_ms = ? WHERE code = ?", (now, code))
        con.commit()
    url = f"{INTAKE_PUBLIC_BASE_URL.rstrip('/')}/mirror/{token}"
    print(f"[mirror/pair] {ophone} ({label}) → token={token}")
    return {"ok": True, "token": token, "url": url, "label": label}


@app.post("/api/mirror/snapshot")
async def mirror_snapshot(req: MirrorSnapshotRequest) -> dict:
    """업무폰 — 일정 items[] + 돈 요약 덮어쓰기 (팀원 schedule-snapshot 과 동일 컨셉).

    items: [{date:"2026-07-15", time:"09:00", days:1, name, address, phone, memo,
             total(원), completed}]
    money: {todayIn, unpaid, unpaidCount}

    추가122(v2): **owner_phone 만으로 동작** — v2 앱은 issue/pair 를 안 하므로 404 내면 안 됨.
      · 항상 mirror_snapshots(owner_phone PK) upsert  ← v2 본폰 /board 가 읽는 곳
      · v1 링크(mirror_sources)가 남아 있으면 거기도 같이 갱신 (구 배포본 하위호환)
    """
    ophone = _norm_phone(req.owner_phone)
    if not ophone:
        raise HTTPException(400, "owner_phone 필수")
    now = _now_ms()
    items_str = json.dumps(req.items or [], ensure_ascii=False)
    money_str = json.dumps(req.money or {}, ensure_ascii=False)
    label = (req.label or "").strip()[:20]
    v1_tokens = []
    with db_conn() as con:
        # ① v2 저장소 (항상)
        prev = con.execute(
            "SELECT label, tint FROM mirror_snapshots WHERE owner_phone = ?", (ophone,)
        ).fetchone()
        keep_label = label or (prev[0] if prev else None) or "내 사업장"
        keep_tint = prev[1] if prev else 0
        if not prev:
            # 코드 등록 시 정한 label/tint 가 있으면 그걸 우선
            crow = con.execute(
                "SELECT label, tint FROM mirror_codes WHERE owner_phone = ?", (ophone,)
            ).fetchone()
            if crow:
                keep_label = label or (crow[0] or keep_label)
                keep_tint = crow[1] or 0
        con.execute(
            "INSERT OR REPLACE INTO mirror_snapshots "
            "(owner_phone, label, tint, snapshot_json, money_json, updated_at_ms) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            (ophone, keep_label, keep_tint, items_str, money_str, now),
        )
        # ② v1 하위호환 — 기존 링크가 있으면 같이 갱신 (사장님 폰 구버전 뷰어용)
        rows = con.execute(
            "SELECT s.token FROM mirror_sources s JOIN mirror_links l ON l.token = s.token "
            "WHERE s.owner_phone = ? AND l.revoked = 0",
            (ophone,),
        ).fetchall()
        v1_tokens = [r[0] for r in rows]
        for tk in v1_tokens:
            sets = ["snapshot_json = ?", "money_json = ?", "updated_at_ms = ?"]
            vals = [items_str, money_str, now]
            if label:
                sets.append("label = ?")
                vals.append(label)
            vals.extend([tk, ophone])
            con.execute(
                f"UPDATE mirror_sources SET {', '.join(sets)} WHERE token = ? AND owner_phone = ?",
                vals,
            )
        con.commit()
    return {"ok": True, "ownerPhone": ophone, "itemsCount": len(req.items or []),
            "updatedAtMs": now, "v1TokensUpdated": len(v1_tokens),
            "token": (v1_tokens[0] if v1_tokens else None)}


@app.post("/api/mirror/revoke")
async def mirror_revoke(req: MirrorRevokeRequest) -> dict:
    """본폰 분실 등 — 링크 폐기. 이후 /mirror/{token} 은 410."""
    ophone = _norm_phone(req.owner_phone)
    token = (req.token or "").strip()
    if not ophone or not token:
        raise HTTPException(400, "owner_phone, token 필수")
    with db_conn() as con:
        own = con.execute(
            "SELECT 1 FROM mirror_sources WHERE token = ? AND owner_phone = ?",
            (token, ophone),
        ).fetchone()
        if not own:
            raise HTTPException(403, "이 링크의 사업장이 아닙니다")
        con.execute("UPDATE mirror_links SET revoked = 1 WHERE token = ?", (token,))
        con.commit()
    print(f"[mirror/revoke] {ophone} → token={token}")
    return {"ok": True, "token": token, "revoked": True}


def _mirror_gate_page(token: str, err: str = "") -> HTMLResponse:
    """4자리 비번 입력 화면 (본폰에서 최초 1회)."""
    import html as _html
    err_html = (f'<div class="err">{_html.escape(err)}</div>' if err else "")
    css = (
        "body{font-family:'Pretendard',-apple-system,system-ui,sans-serif;background:#FAFBFC;"
        "margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}"
        ".card{background:#fff;border:1px solid #EEF0F3;border-radius:20px;padding:34px 26px;"
        "max-width:340px;width:100%;text-align:center;box-shadow:0 2px 10px rgba(0,0,0,.05)}"
        ".ic{font-size:34px}h1{font-size:19px;font-weight:900;margin:12px 0 6px;color:#0B0F19}"
        "p{font-size:13.5px;color:#5A6472;margin:0 0 20px;line-height:1.6}"
        "input{width:100%;padding:15px;font-size:26px;letter-spacing:12px;text-align:center;"
        "border:1px solid #EEF0F3;border-radius:12px;font-weight:800;box-sizing:border-box}"
        "button{width:100%;margin-top:12px;padding:14px;border:0;border-radius:12px;background:#3182F6;"
        "color:#fff;font-size:15px;font-weight:800;cursor:pointer}"
        ".err{background:#FDECEA;color:#C8352B;font-size:12.5px;font-weight:700;border-radius:9px;"
        "padding:9px;margin-bottom:12px}"
        ".mut{font-size:11.5px;color:#9AA3AF;margin-top:14px}"
    )
    html = (
        '<!doctype html><html lang="ko"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">'
        '<meta name="robots" content="noindex"><title>일정 미러 · 잠금</title>'
        '<link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css" rel="stylesheet">'
        f'<style>{css}</style></head><body><div class="card">'
        '<div class="ic">🔒</div><h1>일정 미러</h1>'
        '<p>사장님만 보는 화면이에요.<br>4자리 비밀번호를 입력해 주세요.</p>'
        f'{err_html}'
        f'<form method="post" action="/mirror/{token}/unlock">'
        '<input name="pin" type="tel" inputmode="numeric" pattern="[0-9]*" maxlength="4" '
        'autocomplete="off" autofocus placeholder="····">'
        '<button type="submit">열기</button></form>'
        '<div class="mut">한 번 입력하면 이 폰에서는 계속 기억합니다.</div>'
        '</div></body></html>'
    )
    return HTMLResponse(content=html, status_code=200)


def _mirror_msg_page(icon: str, title: str, desc: str, status: int) -> HTMLResponse:
    import html as _html
    html = (
        '<!doctype html><html lang="ko"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">'
        '<meta name="robots" content="noindex">'
        '<link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css" rel="stylesheet">'
        "</head><body style=\"font-family:'Pretendard',sans-serif;padding:60px 24px;text-align:center;"
        'background:#FAFBFC;color:#0B0F19">'
        f'<div style="font-size:40px">{icon}</div>'
        f'<h2 style="font-size:19px;margin:14px 0 6px">{_html.escape(title)}</h2>'
        f'<p style="font-size:13.5px;color:#5A6472">{_html.escape(desc)}</p>'
        '</body></html>'
    )
    return HTMLResponse(content=html, status_code=status)


@app.post("/mirror/{token}/unlock", response_class=HTMLResponse)
async def mirror_unlock(token: str, pin: str = Form(default="")) -> Response:
    """비번 확인 → 쿠키 심고 뷰어로 리다이렉트."""
    row = _mirror_link_row(token)
    if not row:
        return _mirror_msg_page("❌", "유효하지 않은 링크", "업무폰에서 링크를 다시 받아 주세요.", 404)
    _tok, _mk, pin_hash, pin_salt, revoked = row
    if revoked:
        return _mirror_msg_page("🔒", "폐기된 링크", "업무폰에서 새 링크를 발급해 주세요.", 410)

    fails = _MIRROR_PIN_FAILS.get(token, [0, 0])
    if fails[0] >= _MIRROR_PIN_MAX_FAILS and (_now_ms() - fails[1]) < 10 * 60 * 1000:
        return _mirror_gate_page(token, "시도가 너무 많습니다. 10분 뒤 다시 시도해 주세요.")

    pin = (pin or "").strip()
    if not pin_hash or _mirror_pin_hash(pin_salt or "", pin) != pin_hash:
        _MIRROR_PIN_FAILS[token] = [fails[0] + 1, _now_ms()]
        return _mirror_gate_page(token, "비밀번호가 맞지 않습니다.")

    _MIRROR_PIN_FAILS.pop(token, None)
    resp = Response(status_code=303, headers={"Location": f"/mirror/{token}"})
    resp.set_cookie(
        key=_mirror_cookie_name(token),
        value=_mirror_cookie_value(token, pin_hash),
        max_age=_MIRROR_COOKIE_MAX_AGE, httponly=True, samesite="lax", path="/",
    )
    return resp


@app.get("/api/mirror/data/{token}")
async def mirror_data(token: str, request: Request) -> dict:
    """60초 자동 새로고침용. 비번 쿠키 없으면 401."""
    row = _mirror_link_row(token)
    if not row:
        raise HTTPException(404, "유효하지 않은 링크")
    _tok, _mk, pin_hash, _salt, revoked = row
    if revoked:
        raise HTTPException(410, "폐기된 링크")
    if not _mirror_unlocked(request, token, pin_hash):
        raise HTTPException(401, "잠김")
    with db_conn() as con:
        con.execute("UPDATE mirror_links SET last_accessed_ms = ? WHERE token = ?",
                    (_now_ms(), token))
        con.commit()
    return _mirror_payload(token)


@app.get("/mirror/{token}", response_class=HTMLResponse)
async def mirror_page(token: str, request: Request) -> HTMLResponse:
    """본폰 — 읽기전용 통합 캘린더 (수정 UI 없음). 사업장 N개 통합."""
    row = _mirror_link_row(token)
    if not row:
        return _mirror_msg_page("❌", "유효하지 않은 링크", "업무폰에서 링크를 다시 받아 주세요.", 404)
    _tok, _mk, pin_hash, _salt, revoked = row
    if revoked:
        return _mirror_msg_page("🔒", "폐기된 링크", "업무폰에서 새 링크를 발급해 주세요.", 410)
    if not _mirror_unlocked(request, token, pin_hash):
        return _mirror_gate_page(token)

    with db_conn() as con:
        con.execute("UPDATE mirror_links SET last_accessed_ms = ? WHERE token = ?",
                    (_now_ms(), token))
        con.commit()
    data = _mirror_payload(token)
    boot = json.dumps(data, ensure_ascii=False).replace("</", "<\\/")
    html = MIRROR_VIEW_HTML.replace("__TOKEN__", token).replace("__BOOT__", boot)
    return HTMLResponse(content=html)


@app.get("/manifest/mirror.webmanifest")
async def mirror_manifest():
    """본폰 홈화면 추가용 PWA manifest."""
    return JSONResponse(content={
        "name": "일정 미러 — 시공막내",
        "short_name": "일정 미러",
        "start_url": "/mirror",          # 추가127 ① — 홈화면 바로가기가 미러로 열리게 ("/"=시공인 홈 아님)
        "scope": "/mirror",
        "display": "standalone",
        "background_color": "#FAFBFC",
        "theme_color": "#3182F6",
        "icons": [
            {"src": "/manifest/admin-icon.svg", "sizes": "any", "type": "image/svg+xml",
             "purpose": "any maskable"},
            {"src": "/manifest/admin-icon.svg", "sizes": "192x192", "type": "image/svg+xml"},
            {"src": "/manifest/admin-icon.svg", "sizes": "512x512", "type": "image/svg+xml"},
        ],
    }, media_type="application/manifest+json")


# ============================================================================
# 추가122 (2026-07-14) — 미러 v2 "공유 신청/수락"  (docs/SERVER_HANDOFF_mirror_v2.md)
# ─────────────────────────────────────────────────────────────────────────────
# 한 줄: **업무폰이 고정 코드를 만들고, 본폰이 그 코드를 넣어 신청하고, 업무폰이 수락한다.**
# 코드가 유출돼도 수락 안 하면 무해 → v1 의 "링크+비번" 대신 "수락"이 게이트.
#
#   업무폰(앱):  POST /api/mirror/mycode      고정 코드 조회·생성 (idempotent)
#                GET  /api/mirror/shares      신청함/공유중 폴링
#                POST /api/mirror/respond     수락 / 거절
#                POST /api/mirror/disconnect  공유 해제
#                POST /api/mirror/snapshot    (위 — owner_phone 만으로 동작)
#   본폰(웹):    GET  /mirror                 고정 주소 · 빈 달력 (앱 불필요)
#                POST /mirror/identify        본폰 번호 1회 입력 → 서명 쿠키
#                POST /api/mirror/join        코드 넣어 공유 신청
#                GET  /api/mirror/board       수락된 사업장 합산 캘린더 (60초 새로고침)
#
# v1(issue/pair/revoke, /mirror/{token})은 사장님 폰 구버전 호환 위해 **존치**.
# ============================================================================
_MIRROR_HOME_COOKIE = "mh"
_MIRROR_JOIN_RATE: dict = {}          # {ip: [횟수, 최초시각ms]} — 10회/10분
_MIRROR_REJOIN_COOLDOWN_MS = 60_000   # 거절/해제 후 재신청 쿨다운 (영구차단 X — 재연결 보장)


_MIRROR_CODE_LEN = 8            # 사장님 우려("코드 겹치면?") → 8자리 = 1억 공간, 순차 아닌 랜덤


def _mirror_gen_code() -> str:
    """고정 공유 코드 — **전역 UNIQUE · 8자리 · 랜덤**(순차/예측 금지). 충돌 시 재추출."""
    import secrets
    for _ in range(30):
        code = str(secrets.randbelow(10 ** _MIRROR_CODE_LEN)).zfill(_MIRROR_CODE_LEN)
        with db_conn() as con:
            if not con.execute("SELECT 1 FROM mirror_codes WHERE code = ?", (code,)).fetchone():
                return code
    raise HTTPException(500, "공유 코드 생성 실패")


def _home_cookie_value(home_phone: str) -> str:
    import hmac
    return home_phone + "." + hmac.new(
        _mirror_secret().encode("utf-8"), home_phone.encode("utf-8"), hashlib.sha256
    ).hexdigest()[:24]


def _home_from_cookie(request: Request) -> Optional[str]:
    """서명 쿠키에서 본폰 번호 복원. 위조면 None."""
    import hmac as _hmac
    raw = request.cookies.get(_MIRROR_HOME_COOKIE) or ""
    if "." not in raw:
        return None
    phone, _sep, _sig = raw.partition(".")
    phone = _norm_phone(phone)
    if not phone:
        return None
    if not _hmac.compare_digest(raw, _home_cookie_value(phone)):
        return None
    return phone


class MirrorMyCodeRequest(BaseModel):
    owner_phone: str = ""
    label: str = ""
    tint: int = 0


class MirrorRespondRequest(BaseModel):
    owner_phone: str = ""
    share_id: int = 0
    accept: bool = False


class MirrorDisconnectRequest(BaseModel):
    owner_phone: str = ""
    share_id: int = 0


class MirrorJoinRequest(BaseModel):
    home_phone: str = ""
    code: str = ""
    k: Optional[str] = None        # QR 자동수락 시크릿 (손입력이면 없음)


class MirrorHomePinRequest(BaseModel):
    home_phone: str = ""
    pin: Optional[str] = None      # None/"" = 비번 해제


# ─── 업무폰(앱) ───

@app.post("/api/mirror/mycode")
async def mirror_mycode(req: MirrorMyCodeRequest) -> dict:
    """내 고정 공유 코드 + QR URL (없으면 생성, 있으면 그대로 + label/tint 갱신).

    추가123 — 앱이 이 qrUrl 로 QR 을 그린다. qrUrl 엔 **자동수락 시크릿(k)** 이 들어있어
    본폰이 QR 을 찍으면 수락 단계 없이 바로 연결된다(QR = 업무폰 화면을 봐야 찍음 = 물리적 승인).
    손으로 코드를 친 경우엔 k 가 없으므로 기존대로 업무폰 수락이 필요하다.
    """
    import secrets
    ophone = _norm_phone(req.owner_phone)
    if not ophone:
        raise HTTPException(400, "owner_phone 필수")
    label = (req.label or "").strip()[:20] or "내 사업장"
    tint = int(req.tint or 0)
    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            "SELECT code, auto_secret FROM mirror_codes WHERE owner_phone = ?", (ophone,)
        ).fetchone()
        if row:
            code, secret = row[0], row[1]
            # 구 6자리 코드가 남아 있으면 8자리로 재발급 (아직 배포 전이라 안전)
            if not code or len(code) < _MIRROR_CODE_LEN:
                code = _mirror_gen_code()
            if not secret:
                secret = secrets.token_urlsafe(16)
            con.execute(
                "UPDATE mirror_codes SET code = ?, auto_secret = ?, label = ?, tint = ?, "
                "updated_at_ms = ? WHERE owner_phone = ?",
                (code, secret, label, tint, now, ophone),
            )
        else:
            code = _mirror_gen_code()
            secret = secrets.token_urlsafe(16)
            con.execute(
                "INSERT INTO mirror_codes (owner_phone, code, auto_secret, label, tint, updated_at_ms) "
                "VALUES (?, ?, ?, ?, ?, ?)", (ophone, code, secret, label, tint, now),
            )
        con.commit()
    base = INTAKE_PUBLIC_BASE_URL.rstrip("/")
    return {
        "ok": True, "code": code, "label": label, "tint": tint,
        "homeUrl": f"{base}/mirror",
        # 앱은 이걸 QR 로 그림 → 본폰 카메라로 찍으면 자동수락
        "qrUrl": f"{base}/mirror?code={code}&k={urllib.parse.quote(secret)}",
    }


@app.get("/api/mirror/shares")
async def mirror_shares(owner_phone: str) -> dict:
    """수락 대기 신청 + 현재 공유중 목록. 앱이 폴링해서 알림 띄움."""
    ophone = _norm_phone(owner_phone)
    if not ophone:
        raise HTTPException(400, "owner_phone 필수")
    pending, accepted = [], []
    with db_conn() as con:
        rows = con.execute(
            "SELECT id, home_phone, status, created_at_ms, decided_at_ms "
            "FROM mirror_shares WHERE owner_phone = ? AND status IN ('pending','accepted') "
            "ORDER BY created_at_ms DESC", (ophone,),
        ).fetchall()
    for (sid, hphone, status, created, decided) in rows:
        item = {"id": sid, "home_phone": hphone,
                "home_phone_display": _fmt_phone_dashed(hphone),
                "created_at_ms": created}
        if status == "pending":
            pending.append(item)
        else:
            item["since_ms"] = decided or created
            accepted.append(item)
    return {"ok": True, "pending": pending, "accepted": accepted,
            "pendingCount": len(pending), "acceptedCount": len(accepted)}


@app.post("/api/mirror/respond")
async def mirror_respond(req: MirrorRespondRequest) -> dict:
    """업무폰이 수락/거절. **여기가 유일한 게이트** — 수락 전엔 본폰에 아무것도 안 보임."""
    ophone = _norm_phone(req.owner_phone)
    if not ophone or not req.share_id:
        raise HTTPException(400, "owner_phone, share_id 필수")
    now = _now_ms()
    with db_conn() as con:
        row = con.execute(
            "SELECT owner_phone, home_phone, status FROM mirror_shares WHERE id = ?",
            (req.share_id,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "신청 없음")
        if row[0] != ophone:
            raise HTTPException(403, "내 사업장의 신청이 아닙니다")
        new_status = "accepted" if req.accept else "rejected"
        con.execute(
            "UPDATE mirror_shares SET status = ?, decided_at_ms = ? WHERE id = ?",
            (new_status, now, req.share_id),
        )
        con.commit()
    print(f"[mirror/respond] {ophone} share={req.share_id} → {new_status}")
    return {"ok": True, "share_id": req.share_id, "status": new_status,
            "home_phone": row[1]}


@app.post("/api/mirror/disconnect")
async def mirror_disconnect(req: MirrorDisconnectRequest) -> dict:
    """공유 해제 — 그 본폰 달력에서 이 사업장이 빠짐."""
    ophone = _norm_phone(req.owner_phone)
    if not ophone or not req.share_id:
        raise HTTPException(400, "owner_phone, share_id 필수")
    with db_conn() as con:
        row = con.execute(
            "SELECT owner_phone FROM mirror_shares WHERE id = ?", (req.share_id,)
        ).fetchone()
        if not row:
            raise HTTPException(404, "공유 없음")
        if row[0] != ophone:
            raise HTTPException(403, "내 사업장의 공유가 아닙니다")
        con.execute(
            "UPDATE mirror_shares SET status = 'rejected', decided_at_ms = ? WHERE id = ?",
            (_now_ms(), req.share_id),
        )
        con.commit()
    print(f"[mirror/disconnect] {ophone} share={req.share_id}")
    return {"ok": True, "share_id": req.share_id, "status": "rejected"}


# ─── 본폰(웹) ───

@app.post("/api/mirror/join")
async def mirror_join(req: MirrorJoinRequest, request: Request) -> dict:
    """본폰이 코드를 넣어 공유 신청 → pending. 업무폰이 수락해야 보이기 시작.

    home_phone 은 **서명 쿠키 우선** (웹에서 임의 번호로 신청하는 것 방지).
    """
    hphone = _home_from_cookie(request) or _norm_phone(req.home_phone)
    code = (req.code or "").strip()
    if not hphone:
        raise HTTPException(401, "본폰 번호 확인 필요")
    if not code:
        raise HTTPException(400, "code 필수")

    # rate limit — 코드 무차별 대입 방지 (IP당 10회/10분)
    ip = request.headers.get("x-forwarded-for", "").split(",")[0].strip() or (
        request.client.host if request.client else "?")
    now = _now_ms()
    cnt, since = _MIRROR_JOIN_RATE.get(ip, [0, now])
    if now - since > 10 * 60 * 1000:
        cnt, since = 0, now
    if cnt >= 10:
        raise HTTPException(429, "시도가 너무 많습니다. 10분 뒤 다시 해주세요.")
    _MIRROR_JOIN_RATE[ip] = [cnt + 1, since]

    import hmac as _hmac
    with db_conn() as con:
        crow = con.execute(
            "SELECT owner_phone, label, auto_secret FROM mirror_codes WHERE code = ?", (code,)
        ).fetchone()
        if not crow:
            raise HTTPException(404, "코드가 올바르지 않습니다")
        ophone, label, secret = crow[0], (crow[1] or "사업장"), (crow[2] or "")
        if ophone == hphone:
            raise HTTPException(400, "본인 업무폰 번호로는 신청할 수 없어요")

        # 추가123 — QR 경로(유효한 k) = 자동수락. 손입력(k 없음) = 수락 필요.
        via_qr = bool(secret) and bool(req.k) and _hmac.compare_digest(str(req.k), secret)
        new_status = "accepted" if via_qr else "pending"

        srow = con.execute(
            "SELECT id, status, decided_at_ms FROM mirror_shares "
            "WHERE owner_phone = ? AND home_phone = ?", (ophone, hphone),
        ).fetchone()
        if srow:
            sid, status, decided = srow
            if status == "accepted":
                return {"ok": True, "status": "accepted", "label": label, "share_id": sid,
                        "viaQr": via_qr}
            if status == "pending" and not via_qr:
                return {"ok": True, "status": "pending", "label": label, "share_id": sid,
                        "viaQr": False}
            # 추가123 (버그 수정) — 거절/해제됐어도 **재연결 허용**. 영구차단 금지.
            #   (사장님이 실수로 거절했거나 나중에 다시 붙이고 싶을 때 막히면 안 됨.)
            #   도배 방지는 짧은 쿨다운으로만. QR 경로는 쿨다운도 면제(본인 물리 승인).
            if status == "rejected" and not via_qr:
                if decided and (now - decided) < _MIRROR_REJOIN_COOLDOWN_MS:
                    wait_s = int((_MIRROR_REJOIN_COOLDOWN_MS - (now - decided)) / 1000) + 1
                    raise HTTPException(429, f"방금 해제된 연결이에요. {wait_s}초 뒤 다시 신청해 주세요.")
            con.execute(
                "UPDATE mirror_shares SET status = ?, created_at_ms = ?, decided_at_ms = ? "
                "WHERE id = ?",
                (new_status, now, (now if via_qr else None), sid),
            )
            con.commit()
            print(f"[mirror/join] {hphone} → {ophone} ({label}) 재연결 {new_status} id={sid} qr={via_qr}")
            return {"ok": True, "status": new_status, "label": label, "share_id": sid,
                    "viaQr": via_qr}

        cur = con.execute(
            "INSERT INTO mirror_shares (owner_phone, home_phone, status, created_at_ms, decided_at_ms) "
            "VALUES (?, ?, ?, ?, ?)",
            (ophone, hphone, new_status, now, (now if via_qr else None)),
        )
        sid = cur.lastrowid
        con.commit()
    print(f"[mirror/join] {hphone} → {ophone} ({label}) {new_status} id={sid} qr={via_qr}")
    return {"ok": True, "status": new_status, "label": label, "share_id": sid, "viaQr": via_qr}


def _mirror_board_payload(home_phone: str) -> dict:
    """수락된 업무폰들의 스냅샷을 합산 — 본폰 통합 캘린더 데이터."""
    with db_conn() as con:
        shares = con.execute(
            "SELECT owner_phone, status FROM mirror_shares WHERE home_phone = ? "
            "AND status IN ('pending','accepted')", (home_phone,),
        ).fetchall()
        accepted = [s[0] for s in shares if s[1] == "accepted"]
        pending_owners = [s[0] for s in shares if s[1] == "pending"]
        pending = []
        for op in pending_owners:
            lab = con.execute(
                "SELECT label FROM mirror_codes WHERE owner_phone = ?", (op,)
            ).fetchone()
            pending.append({"label": (lab[0] if lab else "사업장")})

        sources = []
        t_in = t_unpaid = t_cnt = 0
        t_recv = []                       # 추가126 D — 미수 현장 목록 합산
        for op in accepted:
            row = con.execute(
                "SELECT label, tint, snapshot_json, money_json, updated_at_ms "
                "FROM mirror_snapshots WHERE owner_phone = ?", (op,),
            ).fetchone()
            if not row:
                lab = con.execute(
                    "SELECT label, tint FROM mirror_codes WHERE owner_phone = ?", (op,)
                ).fetchone()
                sources.append({"label": (lab[0] if lab else "사업장"),
                                "tint": (lab[1] if lab else 0),
                                "items": [], "money": {}, "updatedAtMs": None})
                continue
            label, tint, snap, money, upd = row
            try:
                items = json.loads(snap) if snap else []
            except json.JSONDecodeError:
                items = []
            try:
                m = json.loads(money) if money else {}
            except json.JSONDecodeError:
                m = {}
            t_in += int(m.get("todayIn") or 0)
            t_unpaid += int(m.get("unpaid") or 0)
            t_cnt += int(m.get("unpaidCount") or 0)
            for rv in (m.get("receivables") or []):
                if isinstance(rv, dict):
                    r2 = dict(rv)
                    if len(accepted) > 1:      # 사업장 여러 개면 어느 업체 미수인지 표시
                        r2["_biz"] = label or "사업장"
                    t_recv.append(r2)
            sources.append({"label": label or "사업장", "tint": int(tint or 0),
                            "items": items, "money": m, "updatedAtMs": upd})
    t_recv.sort(key=lambda r: -(int(r.get("amount") or 0)))    # 큰 금액순
    return {
        "homePhone": home_phone,
        "sources": sources,
        "pending": pending,
        "money": {"todayIn": t_in, "unpaid": t_unpaid, "unpaidCount": t_cnt,
                  "receivables": t_recv},
        "serverNowMs": _now_ms(),
    }


@app.get("/api/mirror/board")
async def mirror_board(request: Request) -> dict:
    """본폰 통합 캘린더 데이터 (60초 새로고침). 쿠키의 home_phone 만 신뢰."""
    hphone = _home_from_cookie(request)
    if not hphone:
        raise HTTPException(401, "본폰 번호 확인 필요")
    return _mirror_board_payload(hphone)


@app.post("/api/mirror/home-pin")
async def mirror_home_pin(req: MirrorHomePinRequest, request: Request) -> dict:
    """(선택) 본폰 열기 비번 설정/해제. 기본은 비번 없음."""
    import secrets
    hphone = _home_from_cookie(request) or _norm_phone(req.home_phone)
    if not hphone:
        raise HTTPException(400, "home_phone 필수")
    pin = (req.pin or "").strip()
    with db_conn() as con:
        if not pin:
            con.execute("DELETE FROM mirror_home WHERE home_phone = ?", (hphone,))
            con.commit()
            return {"ok": True, "pinSet": False}
        if not (pin.isdigit() and len(pin) == 4):
            raise HTTPException(400, "pin 은 4자리 숫자")
        salt = secrets.token_hex(8)
        con.execute(
            "INSERT OR REPLACE INTO mirror_home (home_phone, pin_hash, pin_salt) "
            "VALUES (?, ?, ?)", (hphone, _mirror_pin_hash(salt, pin), salt),
        )
        con.commit()
    return {"ok": True, "pinSet": True}


def _mirror_qs(code: str, k: str) -> str:
    """QR 로 들어온 code/k 를 번호 입력 단계 뒤에도 유지하기 위한 쿼리스트링."""
    parts = []
    if code:
        parts.append("code=" + urllib.parse.quote(code))
    if k:
        parts.append("k=" + urllib.parse.quote(k))
    return ("?" + "&".join(parts)) if parts else ""


@app.post("/mirror/identify")
async def mirror_identify(
    phone: str = Form(default=""),
    code: str = Form(default=""),
    k: str = Form(default=""),
) -> Response:
    """본폰 번호 1회 입력 → 서명 쿠키. QR 로 온 code/k 는 그대로 물고 넘어간다."""
    hphone = _norm_phone(phone)
    qs = _mirror_qs((code or "").strip(), (k or "").strip())
    if not hphone:
        sep = "&" if qs else "?"
        return Response(status_code=303, headers={"Location": f"/mirror{qs}{sep}e=1"})
    resp = Response(status_code=303, headers={"Location": f"/mirror{qs}"})
    resp.set_cookie(
        key=_MIRROR_HOME_COOKIE, value=_home_cookie_value(hphone),
        max_age=_MIRROR_COOKIE_MAX_AGE, httponly=True, samesite="lax", path="/",
    )
    return resp


@app.post("/mirror/forget")
async def mirror_forget() -> Response:
    """이 폰에서 로그아웃(번호 잊기)."""
    resp = Response(status_code=303, headers={"Location": "/mirror"})
    resp.delete_cookie(key=_MIRROR_HOME_COOKIE, path="/")
    return resp


@app.get("/mirror", response_class=HTMLResponse)
async def mirror_home_page(request: Request) -> HTMLResponse:
    """본폰 — 고정 주소. 앱 설치 불필요.

    추가123 — QR 진입 지원:
      · `?code=XXXXXXXX`      → 코드 입력칸 자동 채움 (손입력 대신. 40~50대 URL 타이핑 문제 해결)
      · `?code=..&k=<secret>` → **자동수락**. 서버가 즉시 연결하고 "연결됐어요" 표시.
    번호 쿠키가 없으면 번호 입력 화면을 먼저 띄우되 code/k 를 그대로 물고 넘어간다.
    """
    import html as _html
    import hmac as _hmac
    q_code = (request.query_params.get("code") or "").strip()[:32]
    q_k = (request.query_params.get("k") or "").strip()[:64]

    hphone = _home_from_cookie(request)
    if not hphone:
        err = "번호를 다시 확인해 주세요." if request.query_params.get("e") else ""
        return HTMLResponse(content=(
            MIRROR_IDENTIFY_HTML
            .replace("__ERR__", err)
            .replace("__CODE__", _html.escape(q_code, quote=True))
            .replace("__K__", _html.escape(q_k, quote=True))
        ))

    # ── QR(k 동봉) 로 들어왔으면 바로 연결 ──
    # 추가127 ②: 연결 성공 시 code·k·번호를 localStorage 에 저장하고 history.replaceState 로
    #   주소창의 ?code=&k= 를 지운다. → (a) 홈화면 바로가기·재방문 때 저장값으로 자동 복원,
    #   (b) 주소에 파라미터가 안 남으므로 새로고침 시 재수락 루프 없음(추가125 버그도 계속 예방).
    flash = ""
    save_js = ""
    precode = ""
    if q_code and q_k:
        now = _now_ms()
        with db_conn() as con:
            crow = con.execute(
                "SELECT owner_phone, label, auto_secret FROM mirror_codes WHERE code = ?",
                (q_code,),
            ).fetchone()
            if crow and crow[2] and _hmac.compare_digest(q_k, crow[2]) and crow[0] != hphone:
                ophone, label = crow[0], (crow[1] or "사업장")
                srow = con.execute(
                    "SELECT id, status FROM mirror_shares WHERE owner_phone = ? AND home_phone = ?",
                    (ophone, hphone),
                ).fetchone()
                if srow:
                    con.execute(
                        "UPDATE mirror_shares SET status = 'accepted', decided_at_ms = ? WHERE id = ?",
                        (now, srow[0]),
                    )
                else:
                    con.execute(
                        "INSERT INTO mirror_shares (owner_phone, home_phone, status, "
                        "created_at_ms, decided_at_ms) VALUES (?, ?, 'accepted', ?, ?)",
                        (ophone, hphone, now, now),
                    )
                con.commit()
                flash = f"✅ {label} 일정이 연결됐어요."
                print(f"[mirror/qr] {hphone} → {ophone} ({label}) 자동수락")
                _payload = json.dumps({"code": q_code, "k": q_k, "phone": hphone},
                                      ensure_ascii=False)
                save_js = ("try{localStorage.setItem('mirror_join',JSON.stringify(%s));"
                           "sessionStorage.removeItem('mv_auto');}catch(e){}"
                           "try{history.replaceState({},'','/mirror');}catch(e){}"
                           % _payload)
            else:
                flash = "⚠️ QR 정보가 올바르지 않아요. [+ 사업장 추가]에서 코드를 직접 넣어주세요."
                precode = q_code

    data = _mirror_board_payload(hphone)
    boot = json.dumps(data, ensure_ascii=False).replace("</", "<\\/")
    # 코드 입력칸은 기본 접힘([+ 사업장 추가]). 단 ①아직 아무것도 안 붙었거나 ②코드 오류로 재입력 유도.
    join_open = "on" if (precode or (not data.get("sources") and not data.get("pending"))) else ""
    html = (MIRROR_HOME_HTML
            .replace("__BOOT__", boot)
            .replace("__PHONE__", _html.escape(_fmt_phone_dashed(hphone)))
            .replace("__PHONEDIGITS__", _html.escape(hphone, quote=True))
            .replace("__PRECODE__", _html.escape(precode, quote=True))
            .replace("__JOINOPEN__", join_open)
            .replace("__RESTORE__", save_js)
            .replace("__FLASH__", _html.escape(flash)))
    return HTMLResponse(content=html)


MIRROR_IDENTIFY_HTML = r"""<!doctype html>
<html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>일정 미러 — 시공막내</title>
<link rel="manifest" href="/manifest/mirror.webmanifest">
<link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css" rel="stylesheet">
<style>
 body{font-family:'Pretendard',-apple-system,system-ui,sans-serif;background:#FAFBFC;margin:0;
      min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;color:#0B0F19}
 .c{background:#fff;border:1px solid #EEF0F3;border-radius:20px;padding:34px 26px;max-width:360px;
    width:100%;text-align:center;box-shadow:0 2px 10px rgba(0,0,0,.05)}
 .ic{font-size:36px} h1{font-size:20px;font-weight:900;margin:12px 0 6px}
 p{font-size:13.5px;color:#5A6472;margin:0 0 20px;line-height:1.6}
 input{width:100%;padding:14px;font-size:17px;text-align:center;border:1px solid #EEF0F3;
       border-radius:12px;font-weight:800;box-sizing:border-box;font-family:inherit}
 button{width:100%;margin-top:10px;padding:14px;border:0;border-radius:12px;background:#3182F6;
        color:#fff;font-size:15px;font-weight:800;cursor:pointer;font-family:inherit}
 .e{background:#FDECEA;color:#C8352B;font-size:12.5px;font-weight:700;border-radius:9px;padding:9px;margin-bottom:12px}
 .m{font-size:11.5px;color:#9AA3AF;margin-top:14px;line-height:1.6}
</style></head><body>
<div class="c">
  <div class="ic">📅</div>
  <h1>일정 미러</h1>
  <p>업무폰 일정을 이 폰에서 <b>보기만</b> 하는 화면이에요.<br>먼저 이 폰(본폰) 번호를 알려주세요.</p>
  <div id="err">__ERR__</div>
  <form method="post" action="/mirror/identify">
    <input name="phone" type="tel" inputmode="numeric" autocomplete="tel"
           placeholder="010-0000-0000" autofocus>
    <input type="hidden" name="code" value="__CODE__">
    <input type="hidden" name="k" value="__K__">
    <button type="submit">다음</button>
  </form>
  <div class="m">이 번호는 업무폰 사장님이 <b>"누가 신청했는지"</b> 알아보는 이름표예요.<br>
  일정은 업무폰에서 <b>수락해야만</b> 보입니다.</div>
</div>
<script>
 var e=document.getElementById('err');
 if(e.textContent.trim()) e.className='e'; else e.remove();
 // 추가127 ② — 저장된 번호(+code·k)로 자동 복원. 홈화면 바로가기(쿠키 없는 PWA) 대응.
 (function(){
   try{
     if(location.search.indexOf('e=1')>=0){ sessionStorage.setItem('mv_auto','1'); return; } // 실패 시 자동재시도 중단
     if(sessionStorage.getItem('mv_auto')) return;                                            // 한 번만
     var j=JSON.parse(localStorage.getItem('mirror_join')||'{}');
     if(j && j.phone){
       sessionStorage.setItem('mv_auto','1');
       var f=document.forms[0];
       f.phone.value=j.phone;
       if(j.code) f.code.value=j.code;
       if(j.k) f.k.value=j.k;
       f.submit();     // 번호 제출 → 쿠키 설정 → (code·k 있으면) 자동수락 → 일정 화면
     }
   }catch(_){}
 })();
</script>
</body></html>
"""


MIRROR_HOME_HTML = r"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="robots" content="noindex">
<title>일정 미러 — 시공막내</title>
<link rel="manifest" href="/manifest/mirror.webmanifest">
<meta name="theme-color" content="#3182F6">
<link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css" rel="stylesheet">
<style>
  :root{--blue:#3182F6;--blue-dark:#1B64DA;--tint:#EEF4FF;--bg:#FAFBFC;--t1:#0B0F19;
        --t2:#5A6472;--t3:#9AA3AF;--line:#EEF0F3;--green:#16C172;--red:#F0436A;}
  *{box-sizing:border-box;margin:0;padding:0;}
  body{font-family:'Pretendard',-apple-system,system-ui,sans-serif;background:var(--bg);
       color:var(--t1);line-height:1.6;padding-bottom:40px;}
  .top{position:sticky;top:0;z-index:9;background:rgba(255,255,255,.94);backdrop-filter:blur(8px);
       border-bottom:1px solid var(--line);padding:12px 16px 10px;}
  .ttl{font-size:16px;font-weight:900;letter-spacing:-.03em;display:flex;align-items:center;gap:6px;}
  .ro{font-size:10.5px;font-weight:800;color:var(--t3);background:#F1F3F5;border-radius:999px;padding:2px 8px;}
  .me{margin-left:auto;font-size:11px;color:var(--t3);font-weight:700;}
  .upd{font-size:11px;color:var(--t3);margin-top:3px;}
  .chips{display:flex;gap:6px;margin-top:9px;overflow-x:auto;-webkit-overflow-scrolling:touch;}
  .chip{flex:none;font-size:12.5px;font-weight:800;border:1px solid var(--line);background:#fff;
        color:var(--t2);border-radius:999px;padding:6px 12px;cursor:pointer;display:flex;align-items:center;gap:5px;}
  .chip.on{background:var(--tint);border-color:var(--blue);color:var(--blue-dark);}
  .dot{width:7px;height:7px;border-radius:50%;}
  .wrap{max-width:560px;margin:0 auto;padding:14px 16px;}
  /* 추가128 — 홈 화면에 추가 배너 (원탭 설치) */
  .installbar{display:none;align-items:center;gap:10px;
    background:linear-gradient(135deg,#EEF4FF,#fff);border:1px solid #CFE0FF;
    border-radius:14px;padding:11px 12px;margin-bottom:14px;}
  .installbar.on{display:flex;}
  .installbar .ib-ic{font-size:22px;flex:none;}
  .installbar .ib-t{font-size:12.8px;font-weight:800;color:var(--blue-dark);line-height:1.35;}
  .installbar .ib-t small{display:block;font-weight:600;color:var(--t2);font-size:11px;margin-top:1px;}
  .installbar .ib-go{margin-left:auto;flex:none;background:var(--blue);color:#fff;border:0;
    border-radius:10px;padding:9px 14px;font-size:13px;font-weight:800;cursor:pointer;
    font-family:inherit;white-space:nowrap;}
  .installbar .ib-x{flex:none;background:none;border:0;color:var(--t3);font-size:15px;
    cursor:pointer;padding:2px 4px;font-family:inherit;}
  .addbiz{width:100%;margin-bottom:12px;padding:12px;border:1px dashed var(--line);border-radius:14px;
          background:#fff;color:var(--t2);font-size:13px;font-weight:800;cursor:pointer;font-family:inherit;}
  .addbiz:active{background:var(--bg);}
  .join{display:none;background:#fff;border:1px solid var(--line);border-radius:16px;padding:16px;margin-bottom:14px;}
  .join.on{display:block;}
  .mcard.un.tapable{cursor:pointer;}
  .tapmore{font-size:11px;font-weight:800;color:var(--blue-dark);}
  .recv{display:none;background:#fff;border:1px solid var(--line);border-radius:14px;
        padding:4px 4px;margin:-4px 0 14px;}
  .recv.on{display:block;}
  .recv .rv{display:flex;align-items:center;gap:8px;padding:10px 12px;border-bottom:1px solid var(--line);}
  .recv .rv:last-child{border-bottom:0;}
  .recv .rv .rn{font-size:13.5px;font-weight:800;color:var(--t1);}
  .recv .rv .rs{font-size:11px;color:var(--t3);margin-top:1px;}
  .recv .rv .ra{margin-left:auto;font-size:14px;font-weight:900;color:var(--red);white-space:nowrap;}
  #cal{touch-action:pan-y;}
  .join h3{font-size:14px;font-weight:900;margin-bottom:4px;}
  .join p{font-size:12px;color:var(--t3);margin-bottom:10px;line-height:1.5;}
  .jrow{display:flex;gap:7px;}
  .jrow input{flex:1;padding:12px;border:1px solid var(--line);border-radius:11px;font-size:17px;
              font-weight:800;letter-spacing:5px;text-align:center;font-family:inherit;min-width:0;}
  .jrow button{flex:none;padding:12px 16px;border:0;border-radius:11px;background:var(--blue);
               color:#fff;font-weight:800;font-size:14px;cursor:pointer;font-family:inherit;}
  .msg{font-size:12.5px;font-weight:700;border-radius:9px;padding:9px 11px;margin-top:9px;display:none;}
  .msg.ok{background:#E7F8EF;color:#0B7A45;display:block;}
  .msg.no{background:#FDECEA;color:#C8352B;display:block;}
  .flash{display:none;background:#E7F8EF;color:#0B7A45;font-size:13px;font-weight:800;
         border-radius:12px;padding:12px 14px;margin-bottom:12px;}
  .pend{background:#FFF9E6;border:1px solid #FFE9A8;border-radius:12px;padding:11px 13px;
        font-size:12.5px;color:#8A6A00;font-weight:700;margin-bottom:12px;}
  .money{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-bottom:14px;}
  .mcard{background:#fff;border:1px solid var(--line);border-radius:14px;padding:13px 14px;}
  .mcard .k{font-size:11.5px;color:var(--t3);font-weight:700;}
  .mcard .v{font-size:17px;font-weight:900;margin-top:2px;letter-spacing:-.02em;}
  .mcard.in .v{color:var(--green);} .mcard.un .v{color:var(--red);}
  .mcard .s{font-size:11px;color:var(--t3);margin-top:1px;}
  .sec{font-size:13px;font-weight:900;margin:16px 0 8px;color:var(--t2);}
  .cal{background:#fff;border:1px solid var(--line);border-radius:16px;padding:12px 10px;}
  .calhd{display:flex;align-items:center;justify-content:space-between;padding:2px 6px 8px;}
  .calhd b{font-size:14px;font-weight:900;}
  .calhd button{border:0;background:#F1F3F5;color:var(--t2);border-radius:8px;width:30px;height:28px;
                font-size:14px;font-weight:800;cursor:pointer;}
  .grid{display:grid;grid-template-columns:repeat(7,1fr);gap:2px;}
  .dow{font-size:10.5px;color:var(--t3);text-align:center;font-weight:800;padding-bottom:4px;}
  .day{aspect-ratio:1;border-radius:9px;display:flex;flex-direction:column;align-items:center;
       justify-content:center;font-size:12.5px;font-weight:700;cursor:pointer;}
  .day.off{color:#D6DAE0;} .day.sel{background:var(--blue);color:#fff;}
  .day.today{outline:1.5px solid var(--blue);}
  .day .pips{display:flex;gap:2px;margin-top:2px;height:4px;}
  .day .pips i{width:4px;height:4px;border-radius:50%;display:block;}
  .day.sel .pips i{background:#fff !important;}
  .card{background:#fff;border:1px solid var(--line);border-radius:14px;padding:13px 14px;
        margin-bottom:8px;border-left:4px solid var(--blue);}
  .card.done{opacity:.6;}
  .c1{display:flex;align-items:center;gap:7px;flex-wrap:wrap;}
  .c1 .nm{font-size:14.5px;font-weight:900;}
  .c1 .tm{font-size:12px;font-weight:800;color:var(--blue-dark);background:var(--tint);border-radius:6px;padding:2px 7px;}
  .c1 .dd{font-size:11px;font-weight:800;color:var(--t3);}
  .c1 .ok{font-size:11px;font-weight:800;color:var(--green);margin-left:auto;}
  .tot{font-size:13px;font-weight:900;color:var(--t1);margin-top:5px;}
  .c3{font-size:12.5px;margin-top:5px;display:flex;gap:12px;flex-wrap:wrap;}
  .c3 a{color:var(--blue-dark);font-weight:700;text-decoration:none;}
  .memo{font-size:12px;color:var(--t2);background:#FFF9E6;border-radius:8px;padding:6px 9px;margin-top:6px;}
  .empty{text-align:center;color:var(--t3);font-size:13px;padding:26px 0;}
  .lbl{font-size:10.5px;font-weight:800;border-radius:5px;padding:1px 6px;}
  .foot{text-align:center;margin-top:24px;}
  .foot button{background:none;border:0;color:var(--t3);font-size:11.5px;text-decoration:underline;
               cursor:pointer;font-family:inherit;}
  /* 추가124 — 주소 탭 → 지도앱 3종 고르기 시트 (본폰에 깔린 걸로 열림) */
  .sheet-bg{display:none;position:fixed;inset:0;background:rgba(11,15,25,.45);z-index:50;}
  .sheet-bg.on{display:block;}
  .sheet{position:fixed;left:0;right:0;bottom:0;z-index:51;background:#fff;
         border-radius:20px 20px 0 0;padding:18px 16px calc(18px + env(safe-area-inset-bottom));
         transform:translateY(100%);transition:transform .18s ease-out;}
  .sheet.on{transform:translateY(0);}
  .sheet .sh-a{font-size:13px;font-weight:800;color:var(--t2);text-align:center;
               padding-bottom:12px;border-bottom:1px solid var(--line);margin-bottom:12px;
               word-break:keep-all;line-height:1.5;}
  .sheet .sh-b{display:grid;grid-template-columns:repeat(3,1fr);gap:9px;}
  .sheet .mapb{display:flex;flex-direction:column;align-items:center;gap:6px;padding:14px 6px;
               border:1px solid var(--line);border-radius:14px;background:var(--bg);
               font-size:12.5px;font-weight:800;color:var(--t1);cursor:pointer;font-family:inherit;}
  .sheet .mapb .mi{width:34px;height:34px;border-radius:10px;display:flex;align-items:center;
                   justify-content:center;font-size:15px;font-weight:900;color:#fff;}
  .sheet .cancel{width:100%;margin-top:10px;padding:13px;border:0;border-radius:12px;
                 background:#F1F3F5;color:var(--t2);font-size:14px;font-weight:800;
                 cursor:pointer;font-family:inherit;}
</style>
</head>
<body>
<div class="top">
  <div class="ttl">📅 일정 미러 <span class="ro">읽기 전용</span><span class="me">__PHONE__</span></div>
  <div class="upd" id="upd">불러오는 중…</div>
  <div class="chips" id="chips"></div>
</div>

<div class="wrap">
  <!-- 추가128 — 홈 화면에 추가 배너 (원탭 설치, 상단 노출) -->
  <div class="installbar" id="installBar">
    <span class="ib-ic">📲</span>
    <div class="ib-t">홈 화면에 추가해 두세요
      <small id="ibSub">누르면 매일 여기서 일정이 바로 열려요</small></div>
    <button class="ib-go" id="ibGo">추가하기</button>
    <button class="ib-x" id="ibX" aria-label="닫기">✕</button>
  </div>

  <div class="flash" id="flash">__FLASH__</div>

  <!-- 추가125 B — 코드 입력은 기본 접힘. 달력이 주인공. -->
  <button class="addbiz" id="addBizBtn">+ 사업장 추가</button>
  <div class="join __JOINOPEN__" id="joinBox">
    <h3>일정 공유 코드 입력</h3>
    <p>업무폰 앱의 <b>QR을 찍으면</b> 바로 연결돼요. 코드를 직접 넣으면 업무폰에서 <b>수락</b>해야 보입니다.</p>
    <div class="jrow">
      <input id="code" type="tel" inputmode="numeric" maxlength="8" placeholder="00000000"
             value="__PRECODE__">
      <button id="joinBtn">신청</button>
    </div>
    <div class="msg" id="jmsg"></div>
  </div>

  <div id="pendBox"></div>

  <div class="money">
    <div class="mcard in"><div class="k">오늘 입금</div><div class="v" id="mIn">-</div></div>
    <div class="mcard un" id="unCard"><div class="k">미수금 <span class="tapmore" id="unMore"></span></div>
      <div class="v" id="mUn">-</div><div class="s" id="mUc"></div></div>
  </div>
  <div class="recv" id="recvBox"></div>

  <div class="cal" id="cal">
    <div class="calhd"><button id="prev">‹</button><b id="ym"></b><button id="next">›</button></div>
    <div class="grid" id="dows"></div>
    <div class="grid" id="days"></div>
  </div>

  <div class="sec" id="secTitle">오늘 현장</div>
  <div id="cards"></div>

  <div class="foot">
    <form method="post" action="/mirror/forget"
          onsubmit="try{localStorage.removeItem('mirror_join');sessionStorage.removeItem('mv_auto');}catch(e){}">
      <button type="submit">이 폰에서 번호 잊기</button></form>
  </div>
</div>

<!-- 추가124 — 지도앱 고르기 시트 -->
<div class="sheet-bg" id="shbg"></div>
<div class="sheet" id="sheet">
  <div class="sh-a" id="shaddr"></div>
  <div class="sh-b">
    <button class="mapb" data-m="tmap"><span class="mi" style="background:#0D6EFD">T</span>T맵</button>
    <button class="mapb" data-m="naver"><span class="mi" style="background:#03C75A">N</span>네이버지도</button>
    <button class="mapb" data-m="kakao"><span class="mi" style="background:#FEE500;color:#3C1E1E">K</span>카카오맵</button>
  </div>
  <button class="cancel" id="shcancel">닫기</button>
</div>

<script>
// 추가127 ② — QR 연결 성공 시 code·k·번호 저장 + 주소창 정리 (서버가 심어줌; 없으면 빈 문자열)
__RESTORE__
// 매 방문마다 번호를 저장값에 갱신 → 홈화면 바로가기(쿠키 없는 PWA)에서 자동 복원에 사용
try{var _mj=JSON.parse(localStorage.getItem('mirror_join')||'{}');
  if("__PHONEDIGITS__") _mj.phone="__PHONEDIGITS__";
  localStorage.setItem('mirror_join',JSON.stringify(_mj));
  sessionStorage.removeItem('mv_auto');   // 여기까지 왔으면 복원 성공 → 재시도 가드 해제
}catch(e){}
let DATA=__BOOT__;
const TINTS=["#3182F6","#16C172","#F5A623","#9B51E0","#F0436A"];
let filter="all", sel=null, cur=null;

const won=n=>(n||0).toLocaleString("ko-KR")+"원";
const ymd=d=>d.getFullYear()+"-"+String(d.getMonth()+1).padStart(2,"0")+"-"+String(d.getDate()).padStart(2,"0");
function esc(s){const d=document.createElement("div");d.textContent=s==null?"":s;return d.innerHTML;}
function ago(ms){if(!ms)return"아직 없음";const s=Math.max(0,Math.floor((Date.now()-ms)/1000));
  if(s<60)return"방금";if(s<3600)return Math.floor(s/60)+"분 전";
  if(s<86400)return Math.floor(s/3600)+"시간 전";return Math.floor(s/86400)+"일 전";}

function allItems(){
  const out=[];
  (DATA.sources||[]).forEach((s,si)=>{
    if(filter!=="all" && filter!==String(si)) return;
    (s.items||[]).forEach(it=>{
      const days=Math.max(1,parseInt(it.days||1));
      const base=new Date((it.date||"")+"T00:00:00");
      if(isNaN(base)) return;
      for(let k=0;k<days;k++){
        const d=new Date(base); d.setDate(d.getDate()+k);
        out.push(Object.assign({},it,{_d:ymd(d),_si:si,_k:k,_days:days}));
      }
    });
  });
  return out;
}
function byDate(){const m={};allItems().forEach(it=>{(m[it._d]=m[it._d]||[]).push(it);});return m;}

function render(){
  const srcs=DATA.sources||[];
  document.getElementById("upd").textContent = srcs.length
    ? "마지막 업데이트: " + srcs.map(s=>s.label+" "+ago(s.updatedAtMs)).join(" · ")
    : "아직 공유중인 업무폰이 없어요 — 아래에 코드를 넣어주세요";

  const pb=document.getElementById("pendBox");
  pb.innerHTML=(DATA.pending||[]).length
    ? '<div class="pend">⏳ '+(DATA.pending||[]).map(p=>esc(p.label)).join(", ")+
      '에 공유를 신청했어요. 업무폰에서 수락하면 일정이 나타납니다.</div>' : "";

  const ch=document.getElementById("chips");
  ch.innerHTML = srcs.length ? ('<div class="chip'+(filter==="all"?" on":"")+'" data-f="all">전체</div>' +
    srcs.map((s,i)=>'<div class="chip'+(filter===String(i)?" on":"")+'" data-f="'+i+'">'+
      '<span class="dot" style="background:'+TINTS[s.tint%5]+'"></span>'+esc(s.label)+'</div>').join("")) : "";
  ch.querySelectorAll(".chip").forEach(c=>c.onclick=()=>{filter=c.dataset.f;render();});

  const M=DATA.money||{};
  document.getElementById("mIn").textContent=won(M.todayIn);
  document.getElementById("mUn").textContent=won(M.unpaid);
  document.getElementById("mUc").textContent=(M.unpaidCount||0)+"건";
  drawRecv(M);

  const today=new Date(); if(!sel) sel=ymd(today);
  if(!cur) cur=new Date(today.getFullYear(),today.getMonth(),1);
  drawCal(); drawCards();
}

// 추가126 D — 미수금 카드 탭 → 어느 현장이 얼마 미수인지 목록
let recvOpen=false;
function manwon(n){n=n||0; return n>=10000?(Math.round(n/1000)/10)+"만원":won(n);}
function drawRecv(M){
  const list=(M.receivables||[]), card=document.getElementById("unCard"),
        more=document.getElementById("unMore"), box=document.getElementById("recvBox");
  if(!list.length){ more.textContent=""; card.classList.remove("tapable");
    box.classList.remove("on"); box.innerHTML=""; card.onclick=null; return; }
  more.textContent = recvOpen? "▲" : "▼ 내역";
  card.classList.add("tapable");
  card.onclick=()=>{ recvOpen=!recvOpen; box.classList.toggle("on",recvOpen);
    more.textContent = recvOpen? "▲" : "▼ 내역"; };
  box.classList.toggle("on",recvOpen);
  box.innerHTML=list.map(r=>{
    const sub=[]; if(r._biz)sub.push(esc(r._biz));
    if(r.overdueDays!=null)sub.push(r.overdueDays+"일 경과");
    if(r.address)sub.push(esc(r.address));
    return '<div class="rv"><div><div class="rn">'+esc(r.name||"현장")+"</div>"+
      (sub.length?'<div class="rs">'+sub.join(" · ")+"</div>":"")+
      "</div><div class=\"ra\">"+manwon(r.amount)+"</div></div>";
  }).join("");
}

function drawCal(){
  document.getElementById("ym").textContent=cur.getFullYear()+"년 "+(cur.getMonth()+1)+"월";
  document.getElementById("dows").innerHTML=
    ["일","월","화","수","목","금","토"].map(d=>'<div class="dow">'+d+"</div>").join("");
  const map=byDate(), tstr=ymd(new Date());
  const first=new Date(cur.getFullYear(),cur.getMonth(),1);
  const start=new Date(first); start.setDate(1-first.getDay());
  let h="";
  for(let i=0;i<42;i++){
    const d=new Date(start); d.setDate(start.getDate()+i);
    const k=ymd(d), off=d.getMonth()!==cur.getMonth(), its=map[k]||[];
    const pips=[...new Set(its.map(x=>x._si))].slice(0,3)
      .map(si=>'<i style="background:'+TINTS[((DATA.sources[si]||{}).tint||0)%5]+'"></i>').join("");
    h+='<div class="day'+(off?" off":"")+(k===sel?" sel":"")+(k===tstr?" today":"")+'" data-d="'+k+'">'+
       d.getDate()+'<span class="pips">'+pips+'</span></div>';
  }
  const g=document.getElementById("days"); g.innerHTML=h;
  g.querySelectorAll(".day").forEach(e=>e.onclick=()=>{sel=e.dataset.d;drawCal();drawCards();});
}

function drawCards(){
  const map=byDate(), its=(map[sel]||[]).sort((a,b)=>(a.time||"").localeCompare(b.time||""));
  const tstr=ymd(new Date());
  document.getElementById("secTitle").textContent =
    (sel===tstr?"오늘":sel.slice(5).replace("-","월 ")+"일")+" 현장 "+(its.length?"("+its.length+")":"");
  const box=document.getElementById("cards");
  if(!its.length){box.innerHTML='<div class="empty">이 날은 잡힌 현장이 없어요</div>';return;}
  box.innerHTML=its.map(it=>{
    const s=DATA.sources[it._si]||{}, col=TINTS[(s.tint||0)%5];
    const dd=it._days>1?'<span class="dd">'+(it._k+1)+"/"+it._days+"일차</span>":"";
    const tm=it.time?'<span class="tm">'+esc(it.time)+"</span>":"";
    const ok=it.completed?'<span class="ok">✓ 완료</span>':"";
    const tot=(it.total?'<div class="tot">💰 '+won(it.total)+"</div>":"");
    // 추가124 — 주소 탭 → 지도앱 3종(T맵/네이버/카카오) 고르기 시트.
    //   본폰은 서버 웹이라 앱의 '기본 네비' 설정이 안 닿음 → 3개 다 주고 깔린 걸로 열게 함.
    const ad=it.address?'<a href="javascript:void(0)" data-addr="'+esc(it.address)+
      '" class="addr">📍 '+esc(it.address)+"</a>":"";
    const ph=it.phone?'<a href="tel:'+esc(String(it.phone).replace(/[^0-9+]/g,""))+'">📞 '+esc(it.phone)+"</a>":"";
    const mm=it.memo?'<div class="memo">📝 '+esc(it.memo)+"</div>":"";
    const lb=(DATA.sources.length>1)?'<span class="lbl" style="background:'+col+'22;color:'+col+'">'+esc(s.label)+"</span>":"";
    return '<div class="card'+(it.completed?" done":"")+'" style="border-left-color:'+col+'">'+
      '<div class="c1"><span class="nm">'+esc(it.name||"현장")+"</span>"+tm+dd+ok+"</div>"+
      tot+(ad?'<div class="c3">'+ad+"</div>":"")+(ph?'<div class="c3">'+ph+"</div>":"")+mm+
      (lb?'<div class="c3">'+lb+"</div>":"")+"</div>";
  }).join("");
  box.querySelectorAll("a.addr").forEach(a=>a.onclick=()=>openMapSheet(a.dataset.addr));
}

// ─── 추가124 — 지도앱 3종 시트 ───
let _mapAddr="";
function openMapSheet(addr){
  _mapAddr=addr||"";
  document.getElementById("shaddr").textContent="📍 "+_mapAddr;
  document.getElementById("shbg").classList.add("on");
  document.getElementById("sheet").classList.add("on");
}
function closeMapSheet(){
  document.getElementById("shbg").classList.remove("on");
  document.getElementById("sheet").classList.remove("on");
}
document.getElementById("shbg").onclick=closeMapSheet;
document.getElementById("shcancel").onclick=closeMapSheet;

document.querySelectorAll(".mapb").forEach(b=>b.onclick=()=>{
  const a=encodeURIComponent(_mapAddr||"");
  if(!a){closeMapSheet();return;}
  const m=b.dataset.m;
  if(m==="naver"){ window.open("https://map.naver.com/p/search/"+a,"_blank","noopener"); }
  else if(m==="kakao"){ window.open("https://map.kakao.com/?q="+a,"_blank","noopener"); }
  else if(m==="tmap"){
    // 앱 스킴 먼저, 안 열리면(=미설치) 웹 지도로 폴백
    const t=Date.now();
    location.href="tmap://search?name="+a;
    setTimeout(()=>{
      if(!document.hidden && Date.now()-t < 2000){
        window.open("https://map.naver.com/p/search/"+a,"_blank","noopener");
      }
    },1200);
  }
  closeMapSheet();
});

function moveMonth(d){cur=new Date(cur.getFullYear(),cur.getMonth()+d,1);drawCal();}
document.getElementById("prev").onclick=()=>moveMonth(-1);
document.getElementById("next").onclick=()=>moveMonth(1);

// 추가126 C — 달력 좌우 스와이프로 월 이동 (좌=다음 달 / 우=이전 달). 세로 스크롤과 충돌 방지.
(function(){
  const cal=document.getElementById("cal"); let sx=0,sy=0,tracking=false;
  cal.addEventListener("touchstart",e=>{const t=e.changedTouches[0];sx=t.clientX;sy=t.clientY;tracking=true;},{passive:true});
  cal.addEventListener("touchend",e=>{
    if(!tracking)return; tracking=false;
    const t=e.changedTouches[0], dx=t.clientX-sx, dy=t.clientY-sy;
    if(Math.abs(dx)>50 && Math.abs(dx)>Math.abs(dy)){ moveMonth(dx<0?1:-1); }  // board 재렌더만(추가 fetch X)
  },{passive:true});
})();

// QR 진입 시 서버가 심어준 안내 (연결됨 / QR 오류)
(function(){const f=document.getElementById("flash");
  if(f && f.textContent.trim()){f.style.display="block";
    if(f.textContent.indexOf("⚠️")===0){f.style.background="#FDECEA";f.style.color="#C8352B";}}
})();

// 추가128 — "홈 화면에 추가" 배너: ⋮ 찾을 필요 없이 [추가하기] 원탭 설치.
(function(){
  var bar=document.getElementById("installBar"), go=document.getElementById("ibGo"),
      x=document.getElementById("ibX"), sub=document.getElementById("ibSub");
  function standalone(){return window.matchMedia("(display-mode: standalone)").matches
      || window.navigator.standalone===true;}
  if(standalone()) return;                                   // 이미 홈화면 앱으로 열림
  try{ if(localStorage.getItem("mv_install_x")) return; }catch(e){}  // 사장님이 닫음
  var deferred=null;
  window.addEventListener("beforeinstallprompt",function(e){
    e.preventDefault(); deferred=e; bar.classList.add("on");  // 크롬: 원탭 설치 가능
  });
  go.onclick=async function(){
    if(deferred){ deferred.prompt(); try{await deferred.userChoice;}catch(e){}
      deferred=null; bar.classList.remove("on"); }
    else { sub.textContent='오른쪽 위 ⋮ 를 눌러 "홈 화면에 추가"를 선택하세요'; }
  };
  x.onclick=function(){ bar.classList.remove("on");
    try{localStorage.setItem("mv_install_x","1");}catch(e){} };
  // iOS 사파리는 beforeinstallprompt 가 없음 → 안내형 배너
  if(/iPhone|iPad|iPod/.test(navigator.userAgent||"") && !standalone()){
    sub.textContent='아래 공유 버튼(↑) → "홈 화면에 추가"를 눌러주세요';
    go.style.display="none"; bar.classList.add("on");
  }
})();

// 추가125 B — [+ 사업장 추가] 로 코드 입력칸 펼치기/접기 (기본 접힘, 달력을 크게)
(function(){
  const btn=document.getElementById("addBizBtn"), box=document.getElementById("joinBox");
  const sync=()=>{btn.textContent = box.classList.contains("on") ? "− 닫기" : "+ 사업장 추가";};
  btn.onclick=()=>{box.classList.toggle("on"); sync();
    if(box.classList.contains("on")) document.getElementById("code").focus();};
  sync();
})();

document.getElementById("joinBtn").onclick=async()=>{
  const code=(document.getElementById("code").value||"").trim();
  const m=document.getElementById("jmsg");
  if(code.length<6){m.className="msg no";m.textContent="코드를 정확히 넣어주세요.";return;}
  try{
    const r=await fetch("/api/mirror/join",{method:"POST",headers:{"Content-Type":"application/json"},
      body:JSON.stringify({home_phone:"",code:code})});
    const j=await r.json();
    if(!r.ok){m.className="msg no";m.textContent=j.detail||"신청에 실패했어요.";return;}
    m.className="msg ok";
    m.textContent = j.status==="accepted"
      ? (j.label+" — 연결됐어요.")
      : (j.label+"에 신청했어요. 업무폰에서 수락하면 일정이 보여요.");
    document.getElementById("code").value="";
    refresh();
  }catch(e){m.className="msg no";m.textContent="네트워크 오류예요.";}
};

async function refresh(){
  try{
    const r=await fetch("/api/mirror/board",{cache:"no-store"});
    if(r.status===401){location.reload();return;}
    if(!r.ok) return;
    DATA=await r.json(); render();
  }catch(e){}
}
setInterval(refresh,60000);
document.addEventListener("visibilitychange",()=>{if(!document.hidden)refresh();});
render();
</script>
</body>
</html>
"""


# ============================================================================
# 추가121 (2026-07-13) — 상담함/문자함 분류 Phase 2 (Haiku)
# ─────────────────────────────────────────────────────────────────────────────
# 핸드오프: docs/SERVER_HANDOFF_inbox_classify.md
# 앱이 로컬 1차 분류로 "애매(UNSURE)"라 둔 것만 넘어온다 → Haiku 가 2차 정정.
# precision 최우선: 시공 문의 가능성이 조금이라도 있으면 consult.
#   general 은 확신 ≥0.9 일 때만 (앱도 0.9 미만이면 강등 안 함).
#   POST /api/thread/classify        1건
#   POST /api/thread/classify-batch  최대 20건 (재접속/백필)
# 캐시: thread_classify_cache (key = suffix + 본문해시) → 같은 내용 재요청 0.
# ============================================================================
_CLASSIFY_ENDPOINT = "thread/classify"
_CLASSIFY_BATCH_MAX = 20

THREAD_CLASSIFY_SYSTEM = """너는 1인 시공 사장님(줄눈·타일·도배·방수 등)의 문자 분류기다.
문자 스레드 하나를 보고 "상담함(consult)" 인지 "문자함(general)" 인지 판정한다.

[상담함 consult]
- 시공 문의·상담·견적·일정·현장 관련 대화일 가능성이 조금이라도 있는 것.
- 개인(고객)이 보낸 것으로 보이는 문자.
- 애매하면 무조건 consult.

[문자함 general]
- 명백히 고객 상담이 아닌 것만: 광고·홍보·스팸, 인증번호(OTP), 은행·카드 알림,
  택배·배송 알림, 정부·공공 안내, 보험·대출 등 세일즈 콜, [Web발신] 대량문자.

[가장 중요한 원칙 — precision]
- 고객 문의를 문자함으로 잘못 보내면 사장님이 일감을 놓친다. 이건 최악이다.
- 광고를 상담함에 남기는 건 사소한 불편일 뿐이다.
- 따라서 **general 은 확신할 때만**(confidence ≥ 0.9). 조금이라도 헷갈리면 consult.

반드시 아래 JSON 만 출력한다:
{"bucket":"consult"|"general","confidence":0.0~1.0,"reason":"짧은 한국어 사유"}"""


def _classify_cache_key(suffix: str, messages: list) -> str:
    body = "\n".join(
        ((m.get("role") or "") + ":" + (m.get("body") or ""))
        for m in (messages or []) if isinstance(m, dict)
    )
    return hashlib.sha256(((suffix or "") + "|" + body).encode("utf-8")).hexdigest()


def _classify_cache_get(key: str) -> Optional[dict]:
    with db_conn() as con:
        row = con.execute(
            "SELECT result_json FROM thread_classify_cache WHERE cache_key = ?", (key,)
        ).fetchone()
    if not row:
        return None
    try:
        out = json.loads(row[0])
        out["cached"] = True
        return out
    except json.JSONDecodeError:
        return None


def _classify_cache_put(key: str, suffix: str, result: dict) -> None:
    with db_conn() as con:
        con.execute(
            "INSERT OR REPLACE INTO thread_classify_cache "
            "(cache_key, suffix, result_json, created_at_ms) VALUES (?, ?, ?, ?)",
            (key, (suffix or "")[:20],
             json.dumps(result, ensure_ascii=False), _now_ms()),
        )
        con.commit()


class ThreadClassifyMessage(BaseModel):
    role: str = "customer"          # customer | owner
    body: str = ""
    ts: int = 0


class ThreadClassifyRequest(BaseModel):
    device_phone: Optional[str] = None
    suffix: str = ""
    address: Optional[str] = None
    is_saved_customer: bool = False
    has_owner_reply: bool = False
    messages: list = Field(default_factory=list)   # 최근 ≤5, 시간순


class ThreadClassifyBatchRequest(BaseModel):
    device_phone: Optional[str] = None
    threads: list = Field(default_factory=list)    # ThreadClassifyRequest 형태 dict 들


def _classify_safe_result(bucket: str, conf: float, reason: str) -> dict:
    """general 은 confidence ≥0.9 일 때만 허용 — 그 외엔 consult 로 되돌림(precision)."""
    b = "general" if (bucket or "").strip().lower() == "general" else "consult"
    try:
        c = float(conf)
    except (TypeError, ValueError):
        c = 0.0
    c = max(0.0, min(1.0, c))
    if b == "general" and c < 0.9:
        return {"bucket": "consult", "confidence": c,
                "reason": f"확신 부족({c:.2f}) — 상담함 유지"}
    return {"bucket": b, "confidence": c, "reason": (reason or "")[:60]}


async def _classify_one(req: ThreadClassifyRequest) -> dict:
    # 안전장치 — 앱이 안 보내지만, 저장고객/답장이력이면 무조건 consult (AI 호출 X)
    if req.is_saved_customer or req.has_owner_reply:
        return {"bucket": "consult", "confidence": 1.0,
                "reason": "저장 고객 또는 답장 이력 있음", "cached": False}

    msgs = [m for m in (req.messages or []) if isinstance(m, dict)][-5:]
    if not msgs:
        return {"bucket": "consult", "confidence": 0.0,
                "reason": "본문 없음 — 상담함 유지", "cached": False}

    key = _classify_cache_key(req.suffix, msgs)
    hit = _classify_cache_get(key)
    if hit:
        return hit

    lines = []
    for m in msgs:
        who = "사장님" if (m.get("role") == "owner") else "상대"
        lines.append(f"[{who}] {(m.get('body') or '')[:300]}")
    user_msg = (
        f"발신번호: {(req.address or '알 수 없음')}\n"
        f"저장된 고객: {'예' if req.is_saved_customer else '아니오'}\n"
        f"사장님이 답장한 적: {'예' if req.has_owner_reply else '아니오'}\n"
        f"--- 최근 메시지 ---\n" + "\n".join(lines)
    )
    parsed, response = await call_claude_json(
        system_prompt=THREAD_CLASSIFY_SYSTEM,
        user_msg=user_msg,
        max_tokens=150,
        model=HAIKU_MODEL,
    )
    _log_llm_usage_from_response(_CLASSIFY_ENDPOINT, response)
    out = _classify_safe_result(
        parsed.get("bucket", "consult"),
        parsed.get("confidence", 0.0),
        parsed.get("reason", ""),
    )
    out["cached"] = False
    _classify_cache_put(key, req.suffix, out)
    return out


@app.post("/api/thread/classify")
async def thread_classify(req: ThreadClassifyRequest) -> dict:
    """애매(UNSURE) 스레드 1건 → consult / general 판정.

    실패 시에도 500 안 내고 consult(=안전) 로 응답 — 앱이 상담함 유지하면 되므로.
    """
    try:
        out = await _classify_one(req)
    except Exception as e:  # noqa: BLE001 — 분류 실패가 사장님 일감을 막으면 안 됨
        print(f"[thread/classify] 실패 → consult 유지: {type(e).__name__}: {e}")
        out = {"bucket": "consult", "confidence": 0.0,
               "reason": "분류 실패 — 상담함 유지", "cached": False}
    out["suffix"] = req.suffix
    return out


@app.post("/api/thread/classify-batch")
async def thread_classify_batch(req: ThreadClassifyBatchRequest) -> dict:
    """재접속/백필용 — 최대 20건. 개별 실패는 consult 로 떨어뜨리고 계속 진행."""
    threads = (req.threads or [])[:_CLASSIFY_BATCH_MAX]
    if not threads:
        return {"ok": True, "count": 0, "results": []}
    results = []
    for t in threads:
        if not isinstance(t, dict):
            continue
        try:
            one = ThreadClassifyRequest(**t)
        except Exception:  # noqa: BLE001
            continue
        try:
            out = await _classify_one(one)
        except Exception as e:  # noqa: BLE001
            print(f"[thread/classify-batch] {one.suffix} 실패 → consult: {type(e).__name__}: {e}")
            out = {"bucket": "consult", "confidence": 0.0,
                   "reason": "분류 실패 — 상담함 유지", "cached": False}
        out["suffix"] = one.suffix
        results.append(out)
    return {"ok": True, "count": len(results), "results": results}


# 뷰어 HTML — 서버는 boot JSON 만 박고, 달력/카드는 JS 가 그린다(60초 새로고침 재사용).
MIRROR_VIEW_HTML = r"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="robots" content="noindex">
<title>일정 미러 — 시공막내</title>
<link rel="manifest" href="/manifest/mirror.webmanifest">
<meta name="theme-color" content="#3182F6">
<link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css" rel="stylesheet">
<style>
  :root{--blue:#3182F6;--blue-dark:#1B64DA;--tint:#EEF4FF;--bg:#FAFBFC;--t1:#0B0F19;
        --t2:#5A6472;--t3:#9AA3AF;--line:#EEF0F3;--green:#16C172;--red:#F0436A;}
  *{box-sizing:border-box;margin:0;padding:0;}
  body{font-family:'Pretendard',-apple-system,system-ui,sans-serif;background:var(--bg);
       color:var(--t1);line-height:1.6;padding-bottom:40px;}
  .top{position:sticky;top:0;z-index:9;background:rgba(255,255,255,.94);backdrop-filter:blur(8px);
       border-bottom:1px solid var(--line);padding:12px 16px 10px;}
  .ttl{font-size:16px;font-weight:900;letter-spacing:-.03em;display:flex;align-items:center;gap:6px;}
  .ro{font-size:10.5px;font-weight:800;color:var(--t3);background:#F1F3F5;border-radius:999px;padding:2px 8px;}
  .upd{font-size:11px;color:var(--t3);margin-top:3px;}
  .chips{display:flex;gap:6px;margin-top:9px;overflow-x:auto;-webkit-overflow-scrolling:touch;}
  .chip{flex:none;font-size:12.5px;font-weight:800;border:1px solid var(--line);background:#fff;
        color:var(--t2);border-radius:999px;padding:6px 12px;cursor:pointer;display:flex;align-items:center;gap:5px;}
  .chip.on{background:var(--tint);border-color:var(--blue);color:var(--blue-dark);}
  .dot{width:7px;height:7px;border-radius:50%;}
  .wrap{max-width:560px;margin:0 auto;padding:14px 16px;}
  .money{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-bottom:14px;}
  .mcard{background:#fff;border:1px solid var(--line);border-radius:14px;padding:13px 14px;}
  .mcard .k{font-size:11.5px;color:var(--t3);font-weight:700;}
  .mcard .v{font-size:17px;font-weight:900;margin-top:2px;letter-spacing:-.02em;}
  .mcard.in .v{color:var(--green);} .mcard.un .v{color:var(--red);}
  .mcard .s{font-size:11px;color:var(--t3);margin-top:1px;}
  .sec{font-size:13px;font-weight:900;margin:16px 0 8px;color:var(--t2);}
  .cal{background:#fff;border:1px solid var(--line);border-radius:16px;padding:12px 10px;}
  .calhd{display:flex;align-items:center;justify-content:space-between;padding:2px 6px 8px;}
  .calhd b{font-size:14px;font-weight:900;}
  .calhd button{border:0;background:#F1F3F5;color:var(--t2);border-radius:8px;width:30px;height:28px;
                font-size:14px;font-weight:800;cursor:pointer;}
  .grid{display:grid;grid-template-columns:repeat(7,1fr);gap:2px;}
  .dow{font-size:10.5px;color:var(--t3);text-align:center;font-weight:800;padding-bottom:4px;}
  .day{aspect-ratio:1;border-radius:9px;display:flex;flex-direction:column;align-items:center;
       justify-content:center;font-size:12.5px;font-weight:700;color:var(--t1);cursor:pointer;position:relative;}
  .day.off{color:#D6DAE0;} .day.sel{background:var(--blue);color:#fff;}
  .day.today{outline:1.5px solid var(--blue);}
  .day .pips{display:flex;gap:2px;margin-top:2px;height:4px;}
  .day .pips i{width:4px;height:4px;border-radius:50%;display:block;}
  .day.sel .pips i{background:#fff !important;}
  .cards{margin-top:12px;}
  .card{background:#fff;border:1px solid var(--line);border-radius:14px;padding:13px 14px;
        margin-bottom:8px;border-left:4px solid var(--blue);}
  .card.done{opacity:.6;}
  .c1{display:flex;align-items:center;gap:7px;}
  .c1 .nm{font-size:14.5px;font-weight:900;}
  .c1 .tm{font-size:12px;font-weight:800;color:var(--blue-dark);background:var(--tint);
          border-radius:6px;padding:2px 7px;}
  .c1 .dd{font-size:11px;font-weight:800;color:var(--t3);}
  .c1 .ok{font-size:11px;font-weight:800;color:var(--green);margin-left:auto;}
  .c2{font-size:12.5px;color:var(--t2);margin-top:5px;}
  .c3{font-size:12px;color:var(--t3);margin-top:4px;display:flex;gap:10px;flex-wrap:wrap;}
  .c3 a{color:var(--blue-dark);font-weight:700;text-decoration:none;}
  .memo{font-size:12px;color:var(--t2);background:#FFF9E6;border-radius:8px;padding:6px 9px;margin-top:6px;}
  .empty{text-align:center;color:var(--t3);font-size:13px;padding:26px 0;}
  .lbl{font-size:10.5px;font-weight:800;border-radius:5px;padding:1px 6px;}
</style>
</head>
<body>
<div class="top">
  <div class="ttl">📅 일정 미러 <span class="ro">읽기 전용</span></div>
  <div class="upd" id="upd">불러오는 중…</div>
  <div class="chips" id="chips"></div>
</div>

<div class="wrap">
  <div class="money">
    <div class="mcard in"><div class="k">오늘 입금</div><div class="v" id="mIn">-</div></div>
    <div class="mcard un"><div class="k">미수금</div><div class="v" id="mUn">-</div><div class="s" id="mUc"></div></div>
  </div>

  <div class="cal">
    <div class="calhd">
      <button id="prev">‹</button><b id="ym"></b><button id="next">›</button>
    </div>
    <div class="grid" id="dows"></div>
    <div class="grid" id="days"></div>
  </div>

  <div class="sec" id="secTitle">오늘 현장</div>
  <div class="cards" id="cards"></div>
</div>

<script>
const TOKEN="__TOKEN__";
let DATA=__BOOT__;
const TINTS=["#3182F6","#16C172","#F5A623","#9B51E0","#F0436A"];
let filter="all", sel=null, cur=null;

const won=n=>(n||0).toLocaleString("ko-KR")+"원";
const ymd=d=>d.getFullYear()+"-"+String(d.getMonth()+1).padStart(2,"0")+"-"+String(d.getDate()).padStart(2,"0");
function ago(ms){const s=Math.max(0,Math.floor((Date.now()-ms)/1000));
  if(s<60)return"방금";if(s<3600)return Math.floor(s/60)+"분 전";
  if(s<86400)return Math.floor(s/3600)+"시간 전";return Math.floor(s/86400)+"일 전";}

// 소스별 items 를 하나로 합치고 sourceIdx 를 심는다 (N일짜리는 날짜 확장)
function allItems(){
  const out=[];
  (DATA.sources||[]).forEach((s,si)=>{
    if(filter!=="all" && filter!==String(si)) return;
    (s.items||[]).forEach(it=>{
      const days=Math.max(1,parseInt(it.days||1));
      const base=new Date((it.date||"")+"T00:00:00");
      if(isNaN(base)) return;
      for(let k=0;k<days;k++){
        const d=new Date(base); d.setDate(d.getDate()+k);
        out.push(Object.assign({},it,{_d:ymd(d),_si:si,_k:k,_days:days}));
      }
    });
  });
  return out;
}
function byDate(){const m={};allItems().forEach(it=>{(m[it._d]=m[it._d]||[]).push(it);});return m;}

function render(){
  const srcs=DATA.sources||[];
  document.getElementById("upd").textContent = srcs.length
    ? "마지막 업데이트: " + srcs.map(s=>s.label+" "+ago(s.updatedAtMs)).join(" · ")
    : "업무폰에서 아직 일정을 보내지 않았어요";

  const ch=document.getElementById("chips");
  ch.innerHTML = '<div class="chip'+(filter==="all"?" on":"")+'" data-f="all">전체</div>' +
    srcs.map((s,i)=>'<div class="chip'+(filter===String(i)?" on":"")+'" data-f="'+i+'">'+
      '<span class="dot" style="background:'+TINTS[s.tint%5]+'"></span>'+esc(s.label)+'</div>').join("");
  ch.querySelectorAll(".chip").forEach(c=>c.onclick=()=>{filter=c.dataset.f;render();});

  const M=DATA.money||{};
  document.getElementById("mIn").textContent=won(M.todayIn);
  document.getElementById("mUn").textContent=won(M.unpaid);
  document.getElementById("mUc").textContent=(M.unpaidCount||0)+"건";

  const today=new Date(); if(!sel) sel=ymd(today); if(!cur) cur=new Date(today.getFullYear(),today.getMonth(),1);
  drawCal(); drawCards();
}
function esc(s){const d=document.createElement("div");d.textContent=s==null?"":s;return d.innerHTML;}

function drawCal(){
  document.getElementById("ym").textContent=cur.getFullYear()+"년 "+(cur.getMonth()+1)+"월";
  const dw=document.getElementById("dows");
  dw.innerHTML=["일","월","화","수","목","금","토"].map(d=>'<div class="dow">'+d+"</div>").join("");
  const map=byDate(), tstr=ymd(new Date());
  const first=new Date(cur.getFullYear(),cur.getMonth(),1);
  const start=new Date(first); start.setDate(1-first.getDay());
  let h="";
  for(let i=0;i<42;i++){
    const d=new Date(start); d.setDate(start.getDate()+i);
    const k=ymd(d), off=d.getMonth()!==cur.getMonth();
    const its=map[k]||[];
    const pips=[...new Set(its.map(x=>x._si))].slice(0,3)
      .map(si=>'<i style="background:'+TINTS[(DATA.sources[si]||{}).tint%5||0]+'"></i>').join("");
    h+='<div class="day'+(off?" off":"")+(k===sel?" sel":"")+(k===tstr?" today":"")+'" data-d="'+k+'">'+
       d.getDate()+'<span class="pips">'+pips+'</span></div>';
  }
  const g=document.getElementById("days"); g.innerHTML=h;
  g.querySelectorAll(".day").forEach(e=>e.onclick=()=>{sel=e.dataset.d;drawCal();drawCards();});
}

function drawCards(){
  const map=byDate(), its=(map[sel]||[]).sort((a,b)=>(a.time||"").localeCompare(b.time||""));
  const tstr=ymd(new Date());
  document.getElementById("secTitle").textContent =
    (sel===tstr?"오늘":sel.slice(5).replace("-","월 ")+"일")+" 현장 "+(its.length?"("+its.length+")":"");
  const box=document.getElementById("cards");
  if(!its.length){box.innerHTML='<div class="empty">이 날은 잡힌 현장이 없어요</div>';return;}
  box.innerHTML=its.map(it=>{
    const s=DATA.sources[it._si]||{}, col=TINTS[(s.tint||0)%5];
    const dd=it._days>1?'<span class="dd">'+(it._k+1)+"/"+it._days+"일차</span>":"";
    const tm=it.time?'<span class="tm">'+esc(it.time)+"</span>":"";
    const ok=it.completed?'<span class="ok">✓ 완료</span>':"";
    const ph=it.phone?'<a href="tel:'+esc(it.phone)+'">📞 '+esc(it.phone)+"</a>":"";
    const ad=it.address?'<div class="c2">📍 '+esc(it.address)+"</div>":"";
    const mm=it.memo?'<div class="memo">📝 '+esc(it.memo)+"</div>":"";
    const lb=(DATA.sources.length>1)?'<span class="lbl" style="background:'+col+'22;color:'+col+'">'+esc(s.label)+"</span>":"";
    return '<div class="card'+(it.completed?" done":"")+'" style="border-left-color:'+col+'">'+
      '<div class="c1"><span class="nm">'+esc(it.name||"현장")+"</span>"+tm+dd+ok+"</div>"+
      ad+(ph?'<div class="c3">'+ph+"</div>":"")+mm+
      (lb?'<div class="c3">'+lb+"</div>":"")+"</div>";
  }).join("");
}

document.getElementById("prev").onclick=()=>{cur=new Date(cur.getFullYear(),cur.getMonth()-1,1);drawCal();};
document.getElementById("next").onclick=()=>{cur=new Date(cur.getFullYear(),cur.getMonth()+1,1);drawCal();};

async function refresh(){
  try{
    const r=await fetch("/api/mirror/data/"+TOKEN,{cache:"no-store"});
    if(r.status===401){location.reload();return;}
    if(!r.ok) return;
    DATA=await r.json(); render();
  }catch(e){}
}
setInterval(refresh,60000);
document.addEventListener("visibilitychange",()=>{if(!document.hidden)refresh();});
render();
</script>
</body>
</html>
"""
