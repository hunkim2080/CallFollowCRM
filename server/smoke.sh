#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# 배포하고 나서 **손님이 쓰는 길을 실제로 눌러보는** 검사.   (2026-09-23)
#
# 왜 생겼나 — 2026-09-23, 접수서에 칸을 3개 늘리면서 그 SELECT 를 잘라 쓰던 두 곳을
#   안 고쳤다. `/api/quote/submissions` 가 500 이 됐고, 그건 **앱이 새 접수서를
#   받아오는 바로 그 창구**라 약 2시간 동안 고객이 접수서를 내도 아무 앱에도 안 들어갔다.
#   그런데 배포 검증은 `/health` 가 200 인 것만 봤다. /health 는 무슨 일이 나도 200 이다.
#
#   → 이제 **진짜 길을 눌러본다.** 하나라도 실패하면 exit 1. 배포 끝났다고 말하지 않는다.
#
# 쓰는 법:  bash server/smoke.sh [https://api.si0in.kr]
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
BASE="${1:-https://api.si0in.kr}"
FAIL=0
PASS=0

# 실제 데이터가 있는 번호/토큰. 200 만 보는 게 아니라 **내용이 들어있는지**까지 본다.
OWNER="${SMOKE_OWNER:-01064610131}"
TOKEN="${SMOKE_TOKEN:-DjfcGMNv}"

# hit <이름> <기대코드> <URL> [반드시 들어있어야 할 글자]
hit() {
  local name="$1" want="$2" url="$3" must="${4:-}"
  local body code
  body=$(curl -s -m 20 -w $'\n%{http_code}' "$url" 2>/dev/null)
  code=$(printf '%s' "$body" | tail -n1)
  body=$(printf '%s' "$body" | sed '$d')
  if [ "$code" != "$want" ]; then
    printf '  ✗ %-28s %s (기대 %s)\n     %s\n' "$name" "$code" "$want" "$(printf '%s' "$body" | head -c 160)"
    FAIL=$((FAIL+1)); return
  fi
  if [ -n "$must" ] && ! printf '%s' "$body" | grep -q -- "$must"; then
    printf '  ✗ %-28s 200 인데 내용이 비었다 (없음: %s)\n     %s\n' "$name" "$must" "$(printf '%s' "$body" | head -c 160)"
    FAIL=$((FAIL+1)); return
  fi
  printf '  ✓ %-28s %s\n' "$name" "$code"
  PASS=$((PASS+1))
}

echo "── 손님이 쓰는 길 점검 · $BASE ──"
hit "서버 살아있나"          200 "$BASE/health"
hit "접수서 받아오기(앱)"    200 "$BASE/api/quote/submissions?devicePhone=$OWNER&sinceMs=0" '"items"'
hit "접수서 목록(앱)"        200 "$BASE/api/intake-form/list?owner_phone=$OWNER&limit=5"
hit "접수서 화면(고객)"        200 "$BASE/intake/$TOKEN"          "접수"
hit "견적 보기(고객)"        200 "$BASE/q/$TOKEN"
hit "견적 문서(고객)"        200 "$BASE/q/$TOKEN/doc"
hit "개인정보 안내(고객)"    200 "$BASE/q/$TOKEN/privacy"
hit "백업 칸(앱)"            200 "$BASE/api/app-backup/status?owner_phone=$OWNER"
hit "답변 추천(앱)"          200 "$BASE/suggestions/01034045247"

echo "──────────────────────────────────────────"
if [ "$FAIL" -gt 0 ]; then
  echo "❌ $FAIL 곳이 깨졌다. ($PASS 곳은 정상) — 배포를 되돌려라."
  exit 1
fi
echo "✅ $PASS 곳 전부 정상."
