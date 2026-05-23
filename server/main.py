import asyncio
import json
import sqlite3
import time
from pathlib import Path
from typing import Literal

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

DB_PATH = Path(__file__).parent / "ringgo.db"
PRICING_PATH = Path(__file__).resolve().parent / "pricing.md"
OLLAMA_URL = "http://localhost:11434/api/chat"
OLLAMA_MODEL = "gpt-oss:20b"

SYSTEM_PROMPT_TEMPLATE = """너는 줄눈 시공 사장님이 고객 문자에 답장할 때 도와주는 비서다.
사장님이 답하기 전에, 사장님이 보낼 만한 답변 후보 3개를 미리 제안한다.

받는 정보:
- 사장님과 고객의 최근 대화 (시간순)
- 고객 정보 (이름·메모·리드 온도·입금 여부, 없을 수도 있음)
- 고객이 방금 보낸 메시지

규칙:
- 정확히 3개 답변을 JSON 으로 답하라.
- 각 답변은 한 문장에서 두 문장. 짧게.
- 고객에게 보낼 메시지이므로 존댓말.
- 사장님은 친절하지만 군더더기 없이 핵심부터.
- 기본 인사 톤은 "안녕하세요!"를 우선 사용한다. (상황상 인사가 불필요하면 생략 가능)
- 이모지·따옴표를 임의로 새로 넣지 마라.
- 아래 금기어/톤은 변형 표현까지 피하라.
  - "급하면" 계열 금지: 고객 압박/재촉으로 들려 신뢰를 깎음.
  - "싸다" 계열 금지: 저가 이미지로 품질 신뢰를 해칠 수 있음.
- 가격·날짜·시간 같은 정보는 대화 내용 또는 아래 가격표에서만 추출. 추측하지 마라.
- 3개 답변은 서로 다른 방향성을 가져라 (예: 적극 응답 / 추가 정보 요청 / 일정 협의).
- 거절/불만 대응이 필요한 경우, 아래 톤을 참고해 짧고 친절하게 쓴다.
  - 거절 톤 샘플: "요청하신 조건은 현재 진행이 어려워요. 가능한 대안으로 {대안} 안내드릴게요."
  - 불만 톤 샘플: "불편 드려 죄송합니다. 바로 확인해서 {조치/시간} 안에 안내드릴게요."

가격 문의 케이스 처리:
- 고객이 가격/견적 문의면 아래 가격표를 활용해 답변 후보 만들기.
- 신축/구축 여부 미확정 → 한 후보는 "신축이세요 구축이세요?" 물어보기.
- 타일 크기 미언급 → 한 후보는 "타일 사진 보내주시면 정확히 견적 드려요" 안내.
- 답변 형식: 표 금지. 항목별 줄바꿈 나열 + 합계.
- 실리콘 제거/셀프줄눈/누수 가능성 = "현장 확인 후 추가될 수 있어요" 한 문장 덧붙임.

답 형식은 반드시 다음 JSON 만. 다른 텍스트 X.
{
  "suggestions": ["...", "...", "..."]
}

────── 가격표 ──────
{PRICING}
"""

_pricing_cache = {"mtime": None, "content": ""}
_tasks_by_phone: dict[str, asyncio.Task] = {}


class HistoryItem(BaseModel):
    role: Literal["customer", "owner"]
    body: str
    timestampMs: int


class CustomerInfo(BaseModel):
    name: str | None = None
    memo: str | None = None
    leadHeat: str | None = None
    depositPaid: bool | None = None


class PrepareReplyRequest(BaseModel):
    phone: str
    latestMessage: str
    latestMessageReceivedAtMs: int
    recentHistory: list[HistoryItem] = Field(default_factory=list)
    customer: CustomerInfo | None = None


class LearnRequest(BaseModel):
    phone: str
    styleNotes: str


class ClassifyRequest(BaseModel):
    message: str
    recentHistory: list[HistoryItem] = Field(default_factory=list)


class SuggestRequest(BaseModel):
    phone: str
    latestMessage: str
    recentHistory: list[HistoryItem] = Field(default_factory=list)
    customer: CustomerInfo | None = None


def now_ms() -> int:
    return int(time.time() * 1000)


def db_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    with db_conn() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS suggestions_cache (
              phone TEXT PRIMARY KEY,
              status TEXT NOT NULL,
              based_on_message TEXT,
              based_on_received_at_ms INTEGER,
              generated_at_ms INTEGER,
              suggestions_json TEXT,
              updated_at_ms INTEGER NOT NULL
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS style_profiles (
              phone TEXT PRIMARY KEY,
              style_notes TEXT NOT NULL,
              updated_at_ms INTEGER NOT NULL
            )
            """
        )


def load_pricing() -> str:
    try:
        mtime = PRICING_PATH.stat().st_mtime
    except FileNotFoundError:
        return "(가격표 파일 없음)"

    if _pricing_cache["mtime"] != mtime:
        _pricing_cache["mtime"] = mtime
        _pricing_cache["content"] = PRICING_PATH.read_text(encoding="utf-8")
    return _pricing_cache["content"]


def build_system_prompt() -> str:
    return SYSTEM_PROMPT_TEMPLATE.replace("{PRICING}", load_pricing())


def build_user_payload(request: PrepareReplyRequest | SuggestRequest) -> str:
    customer = request.customer or CustomerInfo()
    history_lines = [f"({item.role}): {item.body}" for item in request.recentHistory]
    history_lines.append(f"(customer): {request.latestMessage}")

    deposit_text = "없음"
    if customer.depositPaid is True:
        deposit_text = "받음"
    elif customer.depositPaid is False:
        deposit_text = "안 받음"

    return "\n".join(
        [
            "[고객 정보]",
            f"이름: {customer.name or '없음'}",
            f"메모: {customer.memo or '없음'}",
            f"리드 온도: {customer.leadHeat or '없음'}",
            f"계약금 입금: {deposit_text}",
            "",
            "[최근 대화]",
            *history_lines,
            "",
            "답변 후보 3개를 JSON 으로 생성하라.",
        ]
    )


def _parse_suggestions(content: str) -> list[str]:
    parsed = json.loads(content)
    suggestions = parsed.get("suggestions")
    if not isinstance(suggestions, list) or len(suggestions) != 3:
        raise ValueError("invalid suggestions payload")
    cleaned = [str(item).strip() for item in suggestions]
    if any(not item for item in cleaned):
        raise ValueError("empty suggestion")
    return cleaned


async def call_ollama_for_suggestions(request: PrepareReplyRequest | SuggestRequest) -> list[str]:
    payload = {
        "model": OLLAMA_MODEL,
        "stream": False,
        "format": "json",
        "messages": [
            {"role": "system", "content": build_system_prompt()},
            {"role": "user", "content": build_user_payload(request)},
        ],
    }

    async with httpx.AsyncClient(timeout=60.0) as client:
        response = await client.post(OLLAMA_URL, json=payload)
        response.raise_for_status()
        data = response.json()

    message = data.get("message") or {}
    content = message.get("content")
    if not isinstance(content, str):
        raise ValueError("missing message.content")
    return _parse_suggestions(content)


async def call_ollama_json(system_prompt: str, user_prompt: str) -> dict:
    payload = {
        "model": OLLAMA_MODEL,
        "stream": False,
        "format": "json",
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }
    async with httpx.AsyncClient(timeout=60.0) as client:
        response = await client.post(OLLAMA_URL, json=payload)
        response.raise_for_status()
        data = response.json()

    content = ((data.get("message") or {}).get("content"))
    if not isinstance(content, str):
        raise HTTPException(status_code=502, detail="Ollama malformed response")
    try:
        return json.loads(content)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=502, detail=f"Ollama JSON parse failed: {exc}")


def upsert_cache_row(
    phone: str,
    status: str,
    based_on_message: str | None,
    based_on_received_at_ms: int | None,
    generated_at_ms: int | None,
    suggestions: list[str] | None,
) -> None:
    with db_conn() as conn:
        conn.execute(
            """
            INSERT INTO suggestions_cache (
                phone, status, based_on_message, based_on_received_at_ms,
                generated_at_ms, suggestions_json, updated_at_ms
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(phone) DO UPDATE SET
                status=excluded.status,
                based_on_message=excluded.based_on_message,
                based_on_received_at_ms=excluded.based_on_received_at_ms,
                generated_at_ms=excluded.generated_at_ms,
                suggestions_json=excluded.suggestions_json,
                updated_at_ms=excluded.updated_at_ms
            """,
            (
                phone,
                status,
                based_on_message,
                based_on_received_at_ms,
                generated_at_ms,
                json.dumps(suggestions, ensure_ascii=False) if suggestions else None,
                now_ms(),
            ),
        )


async def generate_and_store(request: PrepareReplyRequest) -> None:
    try:
        suggestions = await call_ollama_for_suggestions(request)
        upsert_cache_row(
            phone=request.phone,
            status="ready",
            based_on_message=request.latestMessage,
            based_on_received_at_ms=request.latestMessageReceivedAtMs,
            generated_at_ms=now_ms(),
            suggestions=suggestions,
        )
    except asyncio.CancelledError:
        raise
    except Exception:
        upsert_cache_row(
            phone=request.phone,
            status="missing",
            based_on_message=request.latestMessage,
            based_on_received_at_ms=request.latestMessageReceivedAtMs,
            generated_at_ms=None,
            suggestions=None,
        )
    finally:
        existing = _tasks_by_phone.get(request.phone)
        if existing is asyncio.current_task():
            _tasks_by_phone.pop(request.phone, None)


app = FastAPI(title="RING-GO server", version="0.1.0")


@app.on_event("startup")
async def on_startup() -> None:
    init_db()


@app.get("/health")
async def health() -> dict:
    return {"ok": True, "timeMs": now_ms()}


@app.post("/prepare-reply")
async def prepare_reply(request: PrepareReplyRequest) -> dict:
    running = _tasks_by_phone.get(request.phone)
    if running and not running.done():
        running.cancel()

    upsert_cache_row(
        phone=request.phone,
        status="generating",
        based_on_message=request.latestMessage,
        based_on_received_at_ms=request.latestMessageReceivedAtMs,
        generated_at_ms=None,
        suggestions=None,
    )

    task = asyncio.create_task(generate_and_store(request))
    _tasks_by_phone[request.phone] = task
    return {"ok": True}


@app.get("/suggestions/{phone}")
async def suggestions(phone: str) -> dict:
    with db_conn() as conn:
        row = conn.execute("SELECT * FROM suggestions_cache WHERE phone = ?", (phone,)).fetchone()

    if not row:
        return {"status": "missing"}

    status = row["status"]
    if status != "ready":
        return {"status": status}

    suggestions_data = json.loads(row["suggestions_json"] or "[]")
    return {
        "status": "ready",
        "phone": phone,
        "basedOnMessage": row["based_on_message"],
        "basedOnReceivedAtMs": row["based_on_received_at_ms"],
        "generatedAtMs": row["generated_at_ms"],
        "suggestions": suggestions_data,
    }


@app.post("/api/style-profile/learn")
async def style_profile_learn(request: LearnRequest) -> dict:
    with db_conn() as conn:
        conn.execute(
            """
            INSERT INTO style_profiles (phone, style_notes, updated_at_ms)
            VALUES (?, ?, ?)
            ON CONFLICT(phone) DO UPDATE SET
                style_notes=excluded.style_notes,
                updated_at_ms=excluded.updated_at_ms
            """,
            (request.phone, request.styleNotes, now_ms()),
        )
    return {"ok": True, "phone": request.phone}


@app.post("/api/intent/classify")
async def intent_classify(request: ClassifyRequest) -> dict:
    history_text = "\n".join([f"({item.role}): {item.body}" for item in request.recentHistory])
    result = await call_ollama_json(
        system_prompt=(
            "고객 메시지 의도를 분류하는 분류기다. 반드시 JSON만 반환한다."
            ' 형식: {"intent":"price|schedule|address|greeting|complaint|other","confidence":0~1,"reason":"짧은 근거"}'
        ),
        user_prompt=f"[최근 대화]\n{history_text}\n\n[신규 메시지]\n{request.message}",
    )
    return result


@app.post("/api/reply/suggest")
async def reply_suggest(request: SuggestRequest) -> dict:
    suggestions_data = await call_ollama_for_suggestions(request)
    return {
        "phone": request.phone,
        "suggestions": suggestions_data,
        "generatedAtMs": now_ms(),
    }
