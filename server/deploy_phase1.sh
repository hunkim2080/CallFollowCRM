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

# ─────────────────────────────────────────────────────────────────────────────
# (2026-09-23) 배포한 코드가 아프면 **사람 손 없이** 이전 코드로 돌아간다.
#   오늘 접수서 폴링이 2시간 500 이었다 — 배포 직후 눌러보고 되돌렸으면 1분이었다.
#   그리고 그동안 /healthz 대기 실패 시엔 exit 1 로 **깨진 코드를 그대로 두고** 끝났다(= 100% 먹통).
# ─────────────────────────────────────────────────────────────────────────────
alert_slack() {
    # 웹훅 주소는 서버의 ~/.ringgo_alert_webhook (또는 ALERT_WEBHOOK). 없으면 조용히 건너뜀. 주소는 절대 출력 안 함.
    local url="${ALERT_WEBHOOK:-}"
    if [ -z "$url" ] && [ -f "$HOME/.ringgo_alert_webhook" ]; then
        url="$(tr -d '[:space:]' < "$HOME/.ringgo_alert_webhook")"
    fi
    [ -z "$url" ] && return 0
    local payload
    payload="$(python3 -c 'import json,sys;print(json.dumps({"text":sys.argv[1]}, ensure_ascii=False))' "$1" 2>/dev/null)" || return 0
    curl -s -m 5 -X POST -H 'Content-Type: application/json; charset=utf-8' -d "$payload" "$url" >/dev/null 2>&1 || true
}

wait_healthz() {   # 최대 15초 기다림. 0 = 응답함
    local i
    for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
        if curl --max-time 2 -fsS http://localhost:8000/healthz >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    return 1
}

rollback_and_die() {
    local why="$1"
    fail "$why"
    if [ ! -f "$TARGET/main.py.prev" ]; then
        fail "되돌릴 main.py.prev 가 없습니다. 서버가 아플 수 있습니다 — 직접 확인 필요."
        alert_slack "🔴 배포 실패($why) — 되돌릴 사본이 없어 서버가 아플 수 있음. 직접 확인 필요."
        exit 1
    fi
    warn "이전 코드(main.py.prev)로 되돌립니다..."
    cp "$TARGET/main.py.prev" "$TARGET/main.py"
    launchctl unload "$PLIST" 2>/dev/null || true
    sleep 1
    launchctl load "$PLIST"
    if wait_healthz && bash "$SRC/smoke.sh" http://localhost:8000; then
        ok "되돌림 완료 — 서버는 **이전 코드**로 정상. 새 코드는 배포 안 됐습니다."
        alert_slack "🟠 배포 실패 → 이전 코드로 자동 되돌림. 서버 정상. 이유: $why"
    else
        fail "되돌리기도 실패했습니다! 서버가 지금 아픕니다. stderr.log 마지막 30줄:"
        tail -n 30 "$TARGET/stderr.log" 2>/dev/null || true
        alert_slack "🔴 배포 실패하고 되돌리기도 실패 — 서버 지금 아픔. 이유: $why"
    fi
    exit 1
}

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
step "0.5 새 main.py 를 라이브 파이썬(3.9)으로 먼저 읽어본다"
# -----------------------------------------------------------------------------
# (2026-09-23) 개발 PC 는 3.14 인데 라이브는 3.9 — 여기서만 되는 문법이 섞이면 서버가 아예 안 켜진다.
#   복사하기 **전에** 라이브 venv 파이썬으로 파싱해 본다. 실패하면 아무것도 안 건드리고 끝.
#   (`str | None` 같은 건 파싱은 되고 켤 때 죽는다 — 그건 아래 5.5 되돌리기가 받는다)
if ! "$TARGET/venv/bin/python" -c 'import ast,io,sys; ast.parse(io.open(sys.argv[1], encoding="utf-8").read())' "$SRC/main.py"; then
    fail "새 main.py 가 라이브 파이썬에서 안 읽힙니다. 배포 중단 (서버는 안 건드렸음)."
    exit 1
fi
ok "문법 OK (라이브 python $("$TARGET/venv/bin/python" -c 'import sys;print(sys.version.split()[0])'))"

# 무엇을 배포하는지 지금 잡아둔다.
# ⚠️ 로컬 HEAD 는 cowork 의 push 방식(원격만 갱신) 탓에 옛날에 멈춰있을 수 있음 →
#    "실제로 배포되는 파일"의 진실은 (1) origin/main 최신 (2) main.py 안의 추가NNN 마커.
#    로컬 HEAD 대신 이 둘을 본다.
git -C "$SRC" fetch origin -q 2>/dev/null || true
DEPLOY_COMMIT="$(git -C "$SRC" rev-parse --short origin/main 2>/dev/null || echo '?')"
DEPLOY_SUBJECT="$(git -C "$SRC" log -1 --pretty=%s origin/main 2>/dev/null || echo '(git 정보 없음)')"
DEPLOY_CDATE="$(git -C "$SRC" log -1 --pretty=%cd --date=format:'%Y-%m-%d %H:%M' origin/main 2>/dev/null || echo '?')"
# 배포되는 실제 파일이 origin/main 과 같은가? (다르면 커밋 안 된 로컬 수정)
DEPLOY_DIRTY=""
if ! git -C "$SRC" diff --quiet origin/main -- main.py 2>/dev/null; then
    DEPLOY_DIRTY=" (origin 과 다른 로컬 수정 포함)"
fi
# 파일 자체에서 최신 코드 마커 추출 (git 상태와 무관하게 '진짜 들어있는 것')
CODE_MARKER_N="$(grep -oE '추가[0-9]{2,}' "$SRC/main.py" 2>/dev/null | grep -oE '[0-9]+' | sort -n | tail -1)"
CODE_MARKER="${CODE_MARKER_N:+추가${CODE_MARKER_N}}"
[ -z "$CODE_MARKER" ] && CODE_MARKER="(마커 없음)"
DEPLOY_STARTED="$(date '+%Y-%m-%d %H:%M:%S')"

# -----------------------------------------------------------------------------
step "1. main.py / requirements.txt 갱신"
# -----------------------------------------------------------------------------
# (2026-09-23) 덮어쓰기 전에 지금 돌고 있는 main.py 를 옆에 둔다 — 새 코드가 아프면 이걸로 되돌린다.
if [ -f "$TARGET/main.py" ]; then
    cp "$TARGET/main.py" "$TARGET/main.py.prev"
    ok "지금 도는 main.py → main.py.prev (되돌리기용 사본)"
fi
cp "$SRC/main.py"          "$TARGET/main.py"
cp "$SRC/requirements.txt" "$TARGET/requirements.txt"
# 서버가 켜질 때 슬랙에 "🟢 서버 켜짐 · <이 첫 줄>" 로 읽어주는 파일 — 그래서 재기동 **전에** 써 둔다.
#   (맨 끝에서 배포 완료 시각으로 한 번 더 덮어쓴다)
{
    echo "commit   : $DEPLOY_COMMIT  $DEPLOY_SUBJECT$DEPLOY_DIRTY"
    echo "committed: $DEPLOY_CDATE"
    echo "deployed : $DEPLOY_STARTED (배포 중)"
} > "$TARGET/DEPLOYED.txt" 2>/dev/null || true
# 통화 STT 서브프로세스 워커 — 이걸 안 올리면 세그먼트 시각(start_ms) 못 나와 탭재생 X (2026-08-15)
if [ -f "$SRC/whisper_worker.py" ]; then
    cp "$SRC/whisper_worker.py" "$TARGET/whisper_worker.py"
    ok "main.py + requirements.txt + whisper_worker.py 복사"
else
    ok "main.py + requirements.txt 복사 (whisper_worker.py 없음)"
fi
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
    rollback_and_die "새 코드로 서버가 15초 안에 안 켜짐"
fi
echo "--- /healthz ---"
curl -s http://localhost:8000/healthz; echo ""

# -----------------------------------------------------------------------------
step "5.5 손님이 쓰는 길 눌러보기 (smoke.sh) — 하나라도 깨지면 자동 되돌림"
# -----------------------------------------------------------------------------
# (2026-09-23) /healthz 200 은 "켜졌다"일 뿐이다. 오늘 접수서 폴링이 500 인 동안에도 200 이었다.
#   smoke.sh 는 /healthz/deep(서버 자가검진) + 앱·손님이 실제로 쓰는 길을 내용까지 본다.
if ! bash "$SRC/smoke.sh" http://localhost:8000; then
    rollback_and_die "배포 직후 점검(smoke.sh) 실패"
fi
ok "손님 길 전부 정상 (localhost)"
# 밖에서 들어오는 길(클라우드플레어 터널)도 한 번 — 여기 실패는 코드 탓이 아니라 터널 탓일 수 있어 경고만.
PUB_DEEP="$(curl -s -m 15 -o /dev/null -w '%{http_code}' https://api.si0in.kr/healthz/deep 2>/dev/null || echo 000)"
if [ "$PUB_DEEP" = "200" ]; then
    ok "바깥 길(api.si0in.kr/healthz/deep) 200"
else
    warn "바깥 길 응답 $PUB_DEEP — 터널(cloudflared) 확인. 코드는 localhost 에서 정상."
fi

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
step "무엇이 배포됐나 (배포 확인용)"
# -----------------------------------------------------------------------------
# 사장님이 개발을 많이 해서 "방금 배포가 먹었나?" 헷갈림 → 여기서 딱 보여줌.
DEPLOY_FINISHED="$(date '+%Y-%m-%d %H:%M:%S')"

# (a) 복사가 실제로 됐는지 = SRC 와 TARGET 의 main.py 가 같은지 확인
SRC_SUM="$(md5 -q "$SRC/main.py" 2>/dev/null || md5sum "$SRC/main.py" 2>/dev/null | awk '{print $1}')"
TGT_SUM="$(md5 -q "$TARGET/main.py" 2>/dev/null || md5sum "$TARGET/main.py" 2>/dev/null | awk '{print $1}')"
if [ -n "$SRC_SUM" ] && [ "$SRC_SUM" = "$TGT_SUM" ]; then
    COPY_STATE="✓ 서버의 main.py = 방금 코드와 일치 (${SRC_SUM:0:8})"
else
    COPY_STATE="✗ 불일치! 복사가 안 먹었을 수 있음 (src ${SRC_SUM:0:8} / live ${TGT_SUM:0:8})"
fi

# (b) 서버가 지금 살아있는 시각(부팅 후 healthz 응답 = 방금 재기동된 것)
LIVE_STATE="$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8000/healthz 2>/dev/null || echo '000')"

# (c) 배포 기록을 파일로도 남김 (나중에 cat 으로 확인 가능)
{
    echo "commit   : $DEPLOY_COMMIT  $DEPLOY_SUBJECT$DEPLOY_DIRTY"
    echo "committed: $DEPLOY_CDATE"
    echo "deployed : $DEPLOY_FINISHED"
    echo "main.py  : ${SRC_SUM:0:12}"
} > "$TARGET/DEPLOYED.txt" 2>/dev/null || true

# -----------------------------------------------------------------------------
step "끝"
# -----------------------------------------------------------------------------
cat <<EOF

┌──────────────────────────────────────────────────────────────────────┐
│ RING-GO 백엔드 — 배포 완료                                          │
├──────────────────────────────────────────────────────────────────────┤
│ ▶ 방금 배포한 코드                                                   │
│   $DEPLOY_COMMIT  $DEPLOY_SUBJECT$DEPLOY_DIRTY
│   (원격 최신 커밋 $DEPLOY_CDATE · 파일 마커 $CODE_MARKER)
│
│ ▶ 배포 검증                                                          │
│   $COPY_STATE
│   서버 응답(/healthz) : HTTP $LIVE_STATE   (200 이면 재기동 정상)
│   배포 완료 시각      : $DEPLOY_FINISHED
├──────────────────────────────────────────────────────────────────────┤
│ 디렉터리          : $TARGET
│ 로컬 URL          : http://localhost:8000
│ Tailnet URL (폰용): http://100.86.114.49:8000
│ launchd Label     : com.detailline.ringgo-server
│ 로그              : $TARGET/stdout.log · stderr.log
│ 가격표 편집       : $TARGET/pricing.md  (수정 즉시 반영)
│ 배포 기록 다시보기: cat $TARGET/DEPLOYED.txt
└──────────────────────────────────────────────────────────────────────┘

최근 커밋 5개 (origin/main 기준 · 맨 위 = 방금 배포된 것):
$(git -C "$SRC" log -5 --pretty='  %h  %cd  %s' --date=format:'%m-%d %H:%M' origin/main 2>/dev/null || echo '  (git 로그 없음)')

다음 할 일:
  1. '배포 검증'에 ✓(main.py 일치) 와 HTTP 200 둘 다면 → 확실히 배포됨. (이게 진짜 신호)
  2. 위 '방금 배포한 코드' 커밋/마커가 내가 방금 작업한 그거면 최신까지 반영된 것.
  3. §8 검증 결과(위쪽)도 통과면 기능도 정상.
  4. 5.5 손님 길 점검이 실패했으면 이미 **이전 코드로 되돌아가 있다** — 새 코드를 고쳐서 다시 배포.
     (되돌리기 사본: $TARGET/main.py.prev · 아무 때나 손으로: bash server/smoke.sh)
EOF
