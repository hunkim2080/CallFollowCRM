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
# (2026-09-23 밤, 운영 자문) 덧붙인 것
#   · /healthz/deep — 서버가 **스스로** 접수서 읽기 길을 실제 최신 행으로 돌려본 결과.
#     503 이면 그 이유("problems")가 본문에 온다. DB 잠김·디스크·죽은 일꾼도 여기서.
#   · 접수서 토큰은 **그때그때 목록에서 뽑는다** (제출된 것 우선) — 박아둔 토큰이
#     지워지거나 만료되면 검사가 거짓말(가짜 실패)을 하고, 그러면 사람이 검사를 안 믿게 된다.
#   · 접수서 폴링은 "items" 글자만이 아니라 **한 건 이상 들어있는지**("token") 본다. 빈 배열도 200 이다.
#   · 모든 요청에 `X-Ringgo-Smoke: 1` — 서버가 이걸 보면 손님 열람 기록(open_count)을 안 남긴다.
#     (전엔 배포할 때마다 진짜 손님 접수서에 "열어봤음 +1" 이 찍혔다)
#
# 쓰는 법:  bash server/smoke.sh [http://localhost:8000]     (기본 https://api.si0in.kr)
#   deploy_phase1.sh 가 배포 직후 localhost 로 부르고, 실패하면 이전 코드로 자동 되돌린다.
#   SMOKE_OWNER / SMOKE_TOKEN 환경변수로 번호·예비 토큰을 바꿀 수 있다.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
BASE="${1:-https://api.si0in.kr}"
FAIL=0
PASS=0

# 실제 데이터가 있는 번호. 200 만 보는 게 아니라 **내용이 들어있는지**까지 본다.
OWNER="${SMOKE_OWNER:-01064610131}"
TOKEN_FALLBACK="${SMOKE_TOKEN:-DjfcGMNv}"   # 목록에서 못 뽑았을 때만 쓰는 예비
HDR="X-Ringgo-Smoke: 1"

# hit <이름> <기대코드> <URL> [반드시 들어있어야 할 글자]
hit() {
  local name="$1" want="$2" url="$3" must="${4:-}"
  local body code
  body=$(curl -s -m 20 -H "$HDR" -w $'\n%{http_code}' "$url" 2>/dev/null)
  code=$(printf '%s' "$body" | tail -n1)
  body=$(printf '%s' "$body" | sed '$d')
  if [ "$code" != "$want" ]; then
    printf '  ✗ %-28s %s (기대 %s)\n     %s\n' "$name" "$code" "$want" "$(printf '%s' "$body" | head -c 300)"
    FAIL=$((FAIL+1)); return
  fi
  if [ -n "$must" ] && ! printf '%s' "$body" | grep -q -- "$must"; then
    printf '  ✗ %-28s %s 인데 내용이 비었다 (없음: %s)\n     %s\n' "$name" "$code" "$must" "$(printf '%s' "$body" | head -c 300)"
    FAIL=$((FAIL+1)); return
  fi
  printf '  ✓ %-28s %s\n' "$name" "$code"
  PASS=$((PASS+1))
}

echo "── 손님이 쓰는 길 점검 · $BASE ──"
hit "서버 살아있나"          200 "$BASE/health"
hit "깊은 건강검진(서버 자가)" 200 "$BASE/healthz/deep"   '"ok":true'
hit "접수서 받아오기(앱)"    200 "$BASE/api/quote/submissions?devicePhone=$OWNER&sinceMs=0" '"token"'
hit "접수서 목록(앱)"        200 "$BASE/api/intake-form/list?owner_phone=$OWNER&limit=5" '"token"'

# 고객 화면용 토큰 — 방금 그 목록에서 **제출된 것** 우선으로 뽑는다 (미제출은 7일 지나면 410 이라 가짜 실패).
TOKEN=$(curl -s -m 20 -H "$HDR" "$BASE/api/intake-form/list?owner_phone=$OWNER&limit=30" 2>/dev/null | python3 -c '
import json, sys
try:
    items = json.load(sys.stdin).get("items") or []
except Exception:
    items = []
sub = [i for i in items if i.get("submitted_at_ms")]
print((sub or items or [{}])[0].get("token") or "")
' 2>/dev/null)
if [ -z "$TOKEN" ]; then
  echo "  ! 목록에서 토큰을 못 뽑아 예비 토큰($TOKEN_FALLBACK)으로 봅니다"
  TOKEN="$TOKEN_FALLBACK"
fi
hit "접수서 화면(고객)"      200 "$BASE/intake/$TOKEN"          "접수"
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
