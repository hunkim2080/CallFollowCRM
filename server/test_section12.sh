#!/usr/bin/env bash
# =============================================================================
# RINGGO_SERVER_P0P1P2_UPGRADE.md §12.5 검증 4단계 자동 채점
# -----------------------------------------------------------------------------
# 1. /api/usage-stats?period=all  → llm_usage_log 비어 있으면 stats 0
# 2. POST /prepare-reply 한 번 → 다시 /api/usage-stats?period=all
#    → total.calls +1, total.cost_krw > 0
# 3. period=today / month / all 비교 (since_ms 단조 + 호출수 동일하거나 줄어듦)
# 4. 회귀: 기존 5개 endpoint 가 다 200 OK
# =============================================================================
set -u
URL=http://localhost:8000

ok()   { printf "  \033[32m✓\033[0m %s\n" "$*"; }
fail() { printf "  \033[31m✗\033[0m %s\n" "$*"; FAIL_COUNT=$((FAIL_COUNT+1)); }
step() { printf "\n\033[36m── %s ──\033[0m\n" "$*"; }
FAIL_COUNT=0

# 필요한 도구 확인
for tool in curl jq python3; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "[skip] '$tool' 없음 — section12 검증 스킵"
        exit 0
    fi
done

# -----------------------------------------------------------------------------
step "§12.5-1: /api/usage-stats?period=all → 초기 스냅샷"
# -----------------------------------------------------------------------------
SNAP0=$(curl -s "$URL/api/usage-stats?period=all")
echo "$SNAP0" | python3 -m json.tool || { fail "비유효 JSON"; }
CALLS_BEFORE=$(echo "$SNAP0" | jq -r '.total.calls // -1')
COST_BEFORE=$(echo "$SNAP0"  | jq -r '.total.cost_krw // -1')
PERIOD=$(echo "$SNAP0"       | jq -r '.period // ""')
SINCE_MS=$(echo "$SNAP0"     | jq -r '.since_ms // -1')

if [ "$PERIOD" = "all" ] && [ "$SINCE_MS" = "0" ] && [ -n "$CALLS_BEFORE" ]; then
    ok "stats 응답 정상 (period=all, since_ms=0, calls=$CALLS_BEFORE)"
else
    fail "stats 형식 오류 (period=$PERIOD since_ms=$SINCE_MS calls=$CALLS_BEFORE)"
fi

# -----------------------------------------------------------------------------
step "§12.5-2: /prepare-reply 한 번 호출 → calls 증가 + cost_krw 박힘"
# -----------------------------------------------------------------------------
NOW_MS=$(python3 -c 'import time;print(int(time.time()*1000))')
PHONE_TEST="01099999912"
curl -s -X POST "$URL/prepare-reply" \
    -H 'Content-Type: application/json' \
    -d "{
        \"phone\": \"$PHONE_TEST\",
        \"latestMessage\": \"내일 시공 가능한가요? §12 검증\",
        \"latestMessageReceivedAtMs\": $NOW_MS,
        \"recentHistory\": [],
        \"customer\": {\"name\":\"테스트12\",\"depositPaid\":false},
        \"ownerToneSamples\": [\"네 가능합니다\",\"내일 오전 가요\"]
    }" >/dev/null

# 백그라운드 generate_and_cache 가 끝날 때까지 폴링 (최대 45초)
for i in $(seq 1 45); do
    R=$(curl -s "$URL/suggestions/$PHONE_TEST")
    STATUS=$(echo "$R" | jq -r '.status // ""')
    if [ "$STATUS" = "ready" ] || [ "$STATUS" = "missing" ]; then
        ok "prepare-reply 완료 (status=$STATUS, ${i}초 만)"
        break
    fi
    sleep 1
done
if [ "$STATUS" != "ready" ] && [ "$STATUS" != "missing" ]; then
    fail "prepare-reply 완료 안 됨 (마지막 status=$STATUS)"
fi

# stats 다시 조회
SNAP1=$(curl -s "$URL/api/usage-stats?period=all")
CALLS_AFTER=$(echo "$SNAP1" | jq -r '.total.calls // -1')
COST_AFTER=$(echo "$SNAP1"  | jq -r '.total.cost_krw // -1')
PR_CALLS=$(echo "$SNAP1"    | jq -r '.by_endpoint["prepare-reply"].calls // 0')
PR_COST=$(echo "$SNAP1"     | jq -r '.by_endpoint["prepare-reply"].cost_krw // 0')

echo "  total.calls:    $CALLS_BEFORE → $CALLS_AFTER"
echo "  total.cost_krw: $COST_BEFORE → $COST_AFTER"
echo "  prepare-reply.calls=$PR_CALLS  cost_krw=$PR_COST"

# 자동 채점: status=ready 였으면 calls 가 정확히 +1, cost_krw > 0 이어야 함
if [ "$STATUS" = "ready" ]; then
    DIFF=$((CALLS_AFTER - CALLS_BEFORE))
    if [ "$DIFF" -ge 1 ]; then
        ok "calls +$DIFF (≥1 OK)"
    else
        fail "calls 증가 안 됨 ($CALLS_BEFORE → $CALLS_AFTER)"
    fi
    COST_GT0=$(python3 -c "print(float('$COST_AFTER') > float('$COST_BEFORE'))")
    if [ "$COST_GT0" = "True" ]; then
        ok "cost_krw 증가 ($COST_BEFORE → $COST_AFTER)"
    else
        fail "cost_krw 증가 안 됨 ($COST_BEFORE → $COST_AFTER)"
    fi
    PR_GT0=$(python3 -c "print(float('$PR_COST') > 0)")
    if [ "$PR_GT0" = "True" ]; then
        ok "by_endpoint.prepare-reply.cost_krw > 0 ($PR_COST 원)"
    else
        fail "by_endpoint.prepare-reply.cost_krw = 0 ($PR_COST)"
    fi
else
    fail "prepare-reply 가 ready 가 아니라 채점 스킵 (status=$STATUS)"
fi

# -----------------------------------------------------------------------------
step "§12.5-3: period=today / month / all 비교"
# -----------------------------------------------------------------------------
TODAY=$(curl -s "$URL/api/usage-stats?period=today")
MONTH=$(curl -s "$URL/api/usage-stats?period=month")
ALLP=$(curl -s "$URL/api/usage-stats?period=all")

T_CALLS=$(echo "$TODAY"  | jq -r '.total.calls // 0');  T_SINCE=$(echo "$TODAY" | jq -r '.since_ms')
M_CALLS=$(echo "$MONTH" | jq -r '.total.calls // 0');   M_SINCE=$(echo "$MONTH" | jq -r '.since_ms')
A_CALLS=$(echo "$ALLP"  | jq -r '.total.calls // 0');   A_SINCE=$(echo "$ALLP"  | jq -r '.since_ms')

echo "  today:  calls=$T_CALLS  since_ms=$T_SINCE"
echo "  month:  calls=$M_CALLS  since_ms=$M_SINCE"
echo "  all:    calls=$A_CALLS  since_ms=$A_SINCE"

# all >= month >= today (호출수)
if [ "$A_CALLS" -ge "$M_CALLS" ] && [ "$M_CALLS" -ge "$T_CALLS" ]; then
    ok "calls 단조: all($A_CALLS) ≥ month($M_CALLS) ≥ today($T_CALLS)"
else
    fail "calls 단조 깨짐"
fi

# since_ms 단조: all(0) <= month <= today
if [ "$A_SINCE" = "0" ] && [ "$M_SINCE" -le "$T_SINCE" ]; then
    ok "since_ms 단조: 0=all ≤ month($M_SINCE) ≤ today($T_SINCE)"
else
    fail "since_ms 단조 깨짐 (all=$A_SINCE month=$M_SINCE today=$T_SINCE)"
fi

# -----------------------------------------------------------------------------
step "§12.5-4: 회귀 — 기존 endpoint 5개 다 200 OK"
# -----------------------------------------------------------------------------
# 1) /prepare-reply  — 위에서 이미 호출했으므로 status=200/202 확인
HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL/prepare-reply" \
    -H 'Content-Type: application/json' \
    -d "{\"phone\":\"01099999913\",\"latestMessage\":\"테스트\",\"latestMessageReceivedAtMs\":$NOW_MS,\"recentHistory\":[],\"ownerToneSamples\":[]}")
[ "$HTTP" = "200" ] && ok "/prepare-reply HTTP $HTTP" || fail "/prepare-reply HTTP $HTTP"

# 공통 ConversationContext payload
COMMON_CTX=$(cat <<JSON
{
  "phone":"01099999914","customer_name":"회귀테스트","customer_status":"문의",
  "deposit_paid":false,
  "recent_messages":[{"role":"customer","body":"안녕하세요 견적 문의","timestamp_ms":$NOW_MS}],
  "call_summaries":[],
  "owner_tone_samples":["네 가능합니다"]
}
JSON
)

# 2) /api/card-summary
HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL/api/card-summary" -H 'Content-Type: application/json' -d "$COMMON_CTX")
[ "$HTTP" = "200" ] && ok "/api/card-summary HTTP $HTTP" || fail "/api/card-summary HTTP $HTTP"

# 3) /api/conversation-summary
HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL/api/conversation-summary" -H 'Content-Type: application/json' -d "$COMMON_CTX")
[ "$HTTP" = "200" ] && ok "/api/conversation-summary HTTP $HTTP" || fail "/api/conversation-summary HTTP $HTTP"

# 4) /api/next-action-suggest
HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL/api/next-action-suggest" -H 'Content-Type: application/json' -d "$COMMON_CTX")
[ "$HTTP" = "200" ] && ok "/api/next-action-suggest HTTP $HTTP" || fail "/api/next-action-suggest HTTP $HTTP"

# 5) category-classify — main.py 에 없음. /api/intent/classify (현 stub) 으로 대체.
#    추후 category-classify 가 별도로 추가되면 여기를 갈아끼우세요.
HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL/api/intent/classify" -H 'Content-Type: application/json' -d '{}')
[ "$HTTP" = "200" ] && ok "/api/intent/classify (category-classify 대체 stub) HTTP $HTTP" || fail "/api/intent/classify HTTP $HTTP"

# -----------------------------------------------------------------------------
step "§12.5 결과 요약"
# -----------------------------------------------------------------------------
if [ "$FAIL_COUNT" -eq 0 ]; then
    printf "\033[32m\nALL PASS — §12 토큰 모니터링 검증 통과\033[0m\n"
    echo ""
    echo "최종 /api/usage-stats?period=all:"
    curl -s "$URL/api/usage-stats?period=all" | python3 -m json.tool
    exit 0
else
    printf "\033[31m\n$FAIL_COUNT 개 실패 — 로그(stdout.log/stderr.log) 확인 필요\033[0m\n"
    exit 1
fi
