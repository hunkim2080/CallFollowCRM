"""
RING-GO admin beta intake — FastAPI router
------------------------------------------
Endpoints
  GET  /admin/beta/intake        → HTML page (no server-side auth; auth is client-side)
  GET  /admin/beta/intake/data   → latest saved JSON (requires Authorization: Bearer <token>)
  POST /admin/beta/intake        → save / update intake (requires Authorization: Bearer <token>)

Env vars
  RINGGO_ADMIN_TOKEN  (required) — the admin token accepted for API calls
  RINGGO_DB_PATH      (optional, default "ringgo.db") — path to the SQLite database

Integration (cowork Mac mini)
  1. sqlite3 ringgo.db < migrations/001_beta_intake_responses.sql
  2. Copy routes/admin_beta_intake.py and routes/static/admin_beta_intake.html into your FastAPI project
  3. In your main app: app.include_router(router)  [from routes.admin_beta_intake import router]
  4. Set RINGGO_ADMIN_TOKEN env var before starting the server
"""
import json
import logging
import os
from datetime import datetime, timezone
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
import sqlite3

router = APIRouter()
log = logging.getLogger(__name__)

ADMIN_TOKEN: str = os.environ.get("RINGGO_ADMIN_TOKEN", "")
DB_PATH: str = os.environ.get("RINGGO_DB_PATH", "ringgo.db")

_STATIC = Path(__file__).parent / "static" / "admin_beta_intake.html"


# ── DB helpers ────────────────────────────────────────────────────────────────

def _conn() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


# ── Auth dependency ───────────────────────────────────────────────────────────

def _require_token(request: Request) -> str:
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Authorization: Bearer <token> 헤더 필요")
    tok = auth[7:].strip()
    if not ADMIN_TOKEN:
        log.error("RINGGO_ADMIN_TOKEN env var is not set")
        raise HTTPException(status_code=500, detail="서버 설정 오류: RINGGO_ADMIN_TOKEN 미설정")
    if tok != ADMIN_TOKEN:
        raise HTTPException(status_code=403, detail="토큰 불일치")
    return tok


# ── Request model ─────────────────────────────────────────────────────────────

class IntakePayload(BaseModel):
    draft: int = 1          # 1 = auto-save / draft,  0 = explicit submit
    response_json: dict     # full 10-category form data

    class Config:
        json_schema_extra = {
            "example": {
                "draft": 1,
                "response_json": {
                    "category_1_kpi": {"goal": "...", "kpi_weekly_active": "45"},
                    "category_8_privacy_matrix": {"data_8_1": "A"},
                }
            }
        }


# ── Routes ────────────────────────────────────────────────────────────────────

@router.get("/admin/beta/intake", response_class=HTMLResponse, include_in_schema=False)
async def intake_page():
    """Serve the intake SPA. No server-side auth — client handles token."""
    if not _STATIC.exists():
        raise HTTPException(
            status_code=500,
            detail=f"HTML not found at {_STATIC}. Check that admin_beta_intake.html is in routes/static/."
        )
    return _STATIC.read_text(encoding="utf-8")


@router.get("/admin/beta/intake/data")
async def intake_get_data(_: str = Depends(_require_token)):
    """Return the latest saved intake response as JSON."""
    with _conn() as conn:
        row = conn.execute(
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


@router.post("/admin/beta/intake")
async def intake_save(body: IntakePayload, _: str = Depends(_require_token)):
    """
    Save intake form data.
    Each call inserts a new revision row — the latest revision is the canonical state.
    draft=1 → auto-save;  draft=0 → explicit submission (sets submitted_at).
    """
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    submitted_at = now if body.draft == 0 else None

    with _conn() as conn:
        last = conn.execute(
            "SELECT revision FROM beta_intake_responses ORDER BY revision DESC LIMIT 1"
        ).fetchone()
        rev = (last["revision"] + 1) if last else 1

        conn.execute(
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

    log.info("intake saved rev=%d draft=%d", rev, body.draft)
    return {"ok": True, "revision": rev, "draft": body.draft, "saved_at": now}
