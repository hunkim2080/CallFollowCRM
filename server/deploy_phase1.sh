#!/usr/bin/env bash
# =============================================================================
#  RING-GO Phase 1 배포 스크립트 — Ollama → Claude Sonnet 4.6 교체
#  ─────────────────────────────────────────────────────────────────
#  1. main.py / requirements.txt / plist 를 ~/ringgo-server / LaunchAgents 로 복사
#  2. venv 의존성 갱신 (anthropic 설치)
#  3. launchctl 재기동
#  4. /healthz 응답 대기
#  5. §8.1 가격 시나리오 검증
#  6. §8.2 톤 코퍼스 검증
#  7. §8.3 답변 차별화 (8.2 결과 그대로 출력)
#  8. §8.4 /admin/usage 출력
#  사용법:  bash deploy_phase1.sh
# =============================================================================

set -e

BLUE='\033[1;34m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
RED='\033[1;31m'
NC='\033[0m'

step() { echo -e "\n${BLUE}==> $*${NC}"; }
ok()   { echo -e "${GREEN}✓ $*${NC}"; }
warn() { echo -e "${YELLOW}! $*${NC}"; }
fail() { echo -e "${RED}✗ $*${NC}"; }

SRC="$(cd "$(dirname "$0")" && pwd)"
TARGET="$HOME/ringgo-server"
PLIST="$HOME/Library/LaunchAgents/com.detailline.ringgo-server.plist"

# -----------------------------------------------------------------------------
step "0. 사전 점검"
# -----------------------------------------------------------------------------
if [ ! -d "$TARGET" ]; then
    fail "$TARGET 없습니다. 먼저 install.sh 로 초기 셋업이 끝나 있어야 합니다."
    exit 1
fi
if [ ! -d "$TARGET/venv" ]; then
    fail "$TARGET/venv 없습니다. install.sh 로 venv 생성 먼저."
    exit 1
fi
ok "기존 ~/ringgo-server 발견"

# -----------------------------------------------------------------------------
step "1. main.py / requirements.txt 갱신"
# -----------------------------------------------------------------------------
cp "$SRC/main.py"          "$TARGET/main.py"
cp "$SRC/requirements.txt" "$TARGET/requirements.txt"
ok "main.py + requirements.txt 복사"
# 2026-07-02 (추가82) — static (랜딩/설치/개인정보 페이지) 도 같이 배포
if [ -d "$SRC/static" ]; then
    mkdir -p "$TARGET/static"
    cp -R "$SRC/static/." "$TARGET/static/"
    ok "static/ 복사 (landing.html 등)"
fi

# -----------------------------------------------------------------------------
step "2. anthropic 패키지 설치"
# -----------------------------------------------------------------------------
"$TARGET/venv/bin/pip" install --quiet --upgrade pip
"$TARGET/venv/bin/pip" install --quiet -r "$TARGET/requirements.txt"
ANTHROPIC_VER="$("$TARGET/venv/bin/python" -c 'import anthropic;print(anthropic.__version__)')"
ok "anthropic ${ANTHROPIC_VER} 설치 완료"

# -----------------------------------------------------------------------------
step "3. launchd plist 갱신 (CLAUDE_API_KEY 포함)"
# -----------------------------------------------------------------------------
mkdir -p "$HOME/Library/LaunchAgents"
# 2026-07-02 fix: plist 는 gitignore 라 repo 이동/재클론 시 사본이 없을 수 있음.
# repo 에 없으면 이미 설치된 plist 를 그대로 사용 (env 변경 없는 일반 배포는 OK).
if [ -f "$SRC/com.detailline.ringgo-server.plist" ]; then
    cp "$SRC/com.detailline.ringgo-server.plist" "$PLIST"
    ok "plist 복사 (CLAUDE_API_KEY env 포함)"
elif [ -f "$PLIST" ]; then
    warn "repo 에 plist 사본 없음 → 설치된 plist 그대로 사용"
    warn "(env 바꿀 일 있으면: cp \"$PLIST\" \"$SRC/\" 후 수정해서 재배포)"
else
    fail "plist 가 repo 에도 LaunchAgents 에도 없습니다. 배포 중단."
    exit 1
fi

# 키가 진짜 박혔는지 확인
if /usr/libexec/PlistBuddy -c "Print :EnvironmentVariables:CLAUDE_API_KEY" "$PLIST" 2>/dev/null | grep -q "^sk-ant-"; then
    ok "CLAUDE_API_KEY env 확인됨 (sk-ant-...)"
else
    fail "plist 에 CLAUDE_API_KEY 가 박혀있지 않습니다. 배포 중단."
    exit 1
fi

# -----------------------------------------------------------------------------
step "4. 서비스 reload"
# -----------------------------------------------------------------------------
launchctl unload "$PLIST" 2>/dev/null || true
sleep 1
launchctl load   "$PLIST"
ok "launchctl reload 완료"

# -----------------------------------------------------------------------------
step "5. /healthz 응답 대기 (최대 15초)"
# -----------------------------------------------------------------------------
HEALTHZ_OK=0
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
    if curl --max-time 2 -fsS http://localhost:8000/healthz >/dev/null 2>&1; then
        ok "서버 응답 (${i}초 후)"
        HEALTHZ_OK=1
        break
    fi
    sleep 1
done
if [ "$HEALTHZ_OK" -ne 1 ]; then
    fail "서버가 응답하지 않습니다. stderr.log 마지막 30줄:"
    tail -n 30 "$TARGET/stderr.log" 2>/dev/null || true
    exit 1
fi
echo "--- /healthz ---"
curl -s http://localhost:8000/healthz; echo ""

# =============================================================================
# §8 검증 시나리오
# =============================================================================
# 폴링 헬퍼: phone 인자, 최대 45초 폴링. ready 면 0, 아니면 1.
# 에러는 stdout.log 의 [failed] 라인을 보여준다 (Python print 가 stdout 로 가므로).
poll_until_ready() {
    local phone="$1"
    for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 \
             16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 \
             31 32 33 34 35 36 37 38 39 40 41 42 43 44 45; do
        sleep 1
        local status
        status="$(curl -s http://localhost:8000/suggestions/$phone \
            | python3 -c 'import sys,json;print(json.load(sys.stdin).get("status",""))' 2>/dev/null || echo "?")"
        if [ "$status" = "ready" ]; then
            echo "   ✓ ready (${i}초)"
            return 0
        fi
        if [ "$status" = "missing" ]; then
            echo "   ✗ missing (${i}초). stdout.log 의 [failed]/[rate-limit] 마지막 5줄:"
            grep -E '^\[failed\]|^\[rate-limit\]' "$TARGET/stdout.log" | tail -n 5
            return 1
        fi
    done
    echo "   ✗ 45초 안에 ready 못 봄. 마지막 status=$status"
    echo "   stdout.log 마지막 10줄:"
    tail -n 10 "$TARGET/stdout.log"
    return 1
}

# -----------------------------------------------------------------------------
step "§8.1  가격 시나리오 검증"
# -----------------------------------------------------------------------------
curl -s -X POST http://localhost:8000/prepare-reply \
    -H "Content-Type: application/json" \
    -d '{
        "phone": "01099999991",
        "latestMessage": "신축인데 욕조 있는 화장실 바닥 줄눈 견적 얼마예요?",
        "latestMessageReceivedAtMs": 1716234567000,
        "recentHistory": [],
        "customer": null,
        "ownerToneSamples": []
    }' >/dev/null
echo "   prepare-reply POST 완료. 폴링..."
poll_until_ready "01099999991" || true

RES1="$(curl -s http://localhost:8000/suggestions/01099999991)"
echo "--- §8.1 결과 ---"
echo "$RES1" | python3 -m json.tool

# 자동 채점
if echo "$RES1" | grep -q '40'; then
    ok "✅ §8.1 자동채점: '40' 포함 — pricing.md 가 시스템 프롬프트에 박혔습니다."
else
    warn "§8.1 자동채점: '40' 안 보임 — 가격표 미반영 또는 LLM 누락 가능."
fi

# -----------------------------------------------------------------------------
step "§8.2  톤 코퍼스 검증"
# -----------------------------------------------------------------------------
curl -s -X POST http://localhost:8000/prepare-reply \
    -H "Content-Type: application/json" \
    -d '{
        "phone": "01099999992",
        "latestMessage": "내일 가능하세요?",
        "latestMessageReceivedAtMs": 1716234567000,
        "recentHistory": [],
        "customer": null,
        "ownerToneSamples": [
            "내일 오전 10시에 가요",
            "주소 알려주세요",
            "잔금 부탁드립니다"
        ]
    }' >/dev/null
echo "   prepare-reply POST 완료. 폴링..."
poll_until_ready "01099999992" || true

RES2="$(curl -s http://localhost:8000/suggestions/01099999992)"
echo "--- §8.2 결과 (사장님이 직접 톤 평가) ---"
echo "$RES2" | python3 -m json.tool

# -----------------------------------------------------------------------------
step "§8.3  답변 차별화 (위 §8.2 의 3개 후보가 명확히 다른 방향인지 사장님이 평가)"
# -----------------------------------------------------------------------------
echo "(§8.2 결과를 그대로 사용)"
echo "$RES2" | python3 -c '
import sys, json
data = json.load(sys.stdin)
suggs = data.get("suggestions", [])
for i, s in enumerate(suggs, 1):
    print(f"  {i}번: {s}")
'

# -----------------------------------------------------------------------------
step "§8.4  /admin/usage  (prepare-reply 만 집계됨)"
# -----------------------------------------------------------------------------
curl -s http://localhost:8000/admin/usage | python3 -m json.tool

# -----------------------------------------------------------------------------
step "§8 P0+P1+P2  3개 endpoint 검증 (test_p0p1p2.sh)"
# -----------------------------------------------------------------------------
if [ -f "$SRC/test_p0p1p2.sh" ]; then
    bash "$SRC/test_p0p1p2.sh"
else
    warn "test_p0p1p2.sh 없음 — 스킵"
fi

# -----------------------------------------------------------------------------
step "§12.5 토큰 모니터링 endpoint 검증 (test_section12.sh)"
# -----------------------------------------------------------------------------
if [ -f "$SRC/test_section12.sh" ]; then
    bash "$SRC/test_section12.sh"
else
    warn "test_section12.sh 없음 — 스킵"
fi

# -----------------------------------------------------------------------------
step "§청소  테스트 합성번호 사용기록 제거 (대시보드 오염 방지)"
# -----------------------------------------------------------------------------
# 추가112 — 위 회귀 테스트(991/992/912~914 등)가 api_usage 에 남긴 흔적을 삭제.
# 실 고객 데이터는 안 건드림. cache.db 직접 접근(이 스크립트는 서버 호스트에서 실행).
TEST_PH="'01099999912','01099999913','01099999914','01099999991','01099999992','01099999999','01099998866','01000000000'"
if command -v sqlite3 >/dev/null 2>&1 && [ -f "$TARGET/cache.db" ]; then
    BEFORE=$(sqlite3 "$TARGET/cache.db" "SELECT COUNT(*) FROM api_usage WHERE phone IN ($TEST_PH);" 2>/dev/null || echo 0)
    sqlite3 "$TARGET/cache.db" "DELETE FROM api_usage WHERE phone IN ($TEST_PH);" 2>/dev/null || true
    sqlite3 "$TARGET/cache.db" "DELETE FROM llm_usage_log WHERE phone IN ($TEST_PH);" 2>/dev/null || true
    ok "테스트 사용기록 $BEFORE 건 삭제 (배포 자가 청소)"
else
    warn "sqlite3 없거나 cache.db 못 찾음 — 청소 스킵 (대시보드 /admin 에서 수동 청소 가능)"
fi

# -----------------------------------------------------------------------------
step "끝"
# -----------------------------------------------------------------------------
cat <<EOF

┌──────────────────────────────────────────────────────────────────────┐
│ RING-GO 백엔드 — Phase 1 (Claude Sonnet 4.6) 배포 완료              │
├──────────────────────────────────────────────────────────────────────┤
│ 디렉터리          : $TARGET
│ 로컬 URL          : http://localhost:8000
│ Tailnet URL (폰용): http://100.86.114.49:8000
│ launchd Label     : com.detailline.ringgo-server
│ 로그              : $TARGET/stdout.log
│                    $TARGET/stderr.log
│ 가격표 편집       : $TARGET/pricing.md  (수정 즉시 반영)
│ 사용량 확인       : curl http://localhost:8000/admin/usage
└──────────────────────────────────────────────────────────────────────┘

다음 할 일:
  1. §8.1 결과: '40' 보이면 가격표 OK.
  2. §8.2 결과: 사장님이 직접 보고 "내가 쓴 거 같다" 라고 느껴야 통과.
  3. §8.3: 1번 짧은 답 / 2번 친절 / 3번 전환 유도 — 방향 차이 보이면 통과.
  4. 폰에서 '추천답변 받기' 다시 눌러서 실제 사용해보기.
EOF
