#!/usr/bin/env bash
# =============================================================================
# RING-GO 매일 아침 8시 자동 보고 (launchd 가 호출)
# -----------------------------------------------------------------------------
# - /api/admin/business-stats + /api/usage-stats?period=today 호출
# - 어제 vs 평일 평균 대비 cost spike 감지
# - Zapier MCP 가 아니라 직접 Gmail SMTP 도 가능하지만, 일단 stdout 로그만 남기고
#   사장님이 다음 Cowork 세션에서 Zapier 자동 보고로 메일 전송하는 방식.
# - 또는 사장님이 환경변수 GMAIL_APP_PASSWORD 박으면 직접 SMTP 발송 가능.
# =============================================================================
set -u

LOG_DIR="$HOME/ringgo-server"
LOG_FILE="$LOG_DIR/daily_report.log"
mkdir -p "$LOG_DIR"

# 환경변수 ADMIN_TOKEN 필요 (plist 에서 inherit 받거나, 여기서 직접 박을 수도)
ADMIN_TOKEN="${ADMIN_TOKEN:-}"
SERVER_URL="${SERVER_URL:-http://localhost:8000}"

echo "─────────────────────────────────────" >> "$LOG_FILE"
date >> "$LOG_FILE"
echo "─────────────────────────────────────" >> "$LOG_FILE"

if [ -z "$ADMIN_TOKEN" ]; then
    echo "[ERROR] ADMIN_TOKEN 미설정 — 보고 스킵" >> "$LOG_FILE"
    exit 0
fi

# 1) 어제 비용 (서버 stats endpoint 호출)
TODAY_STATS=$(curl -s "$SERVER_URL/api/usage-stats?period=today")
BIZ_STATS=$(curl -sH "X-Admin-Token: $ADMIN_TOKEN" "$SERVER_URL/api/admin/business-stats")

# 2) cost / margin 추출 (간단한 grep — jq 있으면 더 깔끔)
if command -v jq >/dev/null 2>&1; then
    TODAY_COST=$(echo "$TODAY_STATS" | jq -r '.total.cost_krw // 0')
    TODAY_CALLS=$(echo "$TODAY_STATS" | jq -r '.total.calls // 0')
    MRR=$(echo "$BIZ_STATS" | jq -r '.mrr_krw // 0')
    COGS=$(echo "$BIZ_STATS" | jq -r '.month_cogs_krw // 0')
    MARGIN=$(echo "$BIZ_STATS" | jq -r '.gross_margin_pct // "N/A"')
    ACTIVE=$(echo "$BIZ_STATS" | jq -r '.active_subscribers // 0')
else
    TODAY_COST=$(echo "$TODAY_STATS" | grep -oE '"cost_krw":[0-9.]+' | head -1 | cut -d: -f2)
    TODAY_CALLS=$(echo "$TODAY_STATS" | grep -oE '"calls":[0-9]+' | head -1 | cut -d: -f2)
    MRR=$(echo "$BIZ_STATS" | grep -oE '"mrr_krw":[0-9]+' | head -1 | cut -d: -f2)
    COGS=$(echo "$BIZ_STATS" | grep -oE '"month_cogs_krw":[0-9.]+' | head -1 | cut -d: -f2)
    MARGIN=$(echo "$BIZ_STATS" | grep -oE '"gross_margin_pct":[0-9.]+' | head -1 | cut -d: -f2)
    ACTIVE=$(echo "$BIZ_STATS" | grep -oE '"active_subscribers":[0-9]+' | head -1 | cut -d: -f2)
fi

cat <<EOF >> "$LOG_FILE"
오늘 비용:       ₩${TODAY_COST:-0}
오늘 호출수:     ${TODAY_CALLS:-0}건
이번 달 MRR:     ₩${MRR:-0}
이번 달 COGS:    ₩${COGS:-0}
Gross Margin:    ${MARGIN:-N/A}%
활성 사용자:     ${ACTIVE:-0}명
EOF

# 3) 응답 raw 도 함께 저장 (디버그 용)
echo "" >> "$LOG_FILE"
echo "── today raw ──" >> "$LOG_FILE"
echo "$TODAY_STATS" >> "$LOG_FILE"
echo "── biz raw ──" >> "$LOG_FILE"
echo "$BIZ_STATS" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

# 4) Cost spike 알림 (어제 비용 > ₩5,000 시 anomaly 마킹)
# TODO: 평일 평균 대비 비교는 다음 sprint 에서. 일단 단순 threshold.
SPIKE_THRESHOLD=5000
if [ "${TODAY_COST%.*}" -gt "$SPIKE_THRESHOLD" ] 2>/dev/null; then
    echo "[ALERT] Cost spike: ₩${TODAY_COST} (threshold ₩${SPIKE_THRESHOLD})" >> "$LOG_FILE"
    # TODO: Gmail 자동 발송 (SMTP 또는 webhook)
fi

echo "[ok] daily report logged" >> "$LOG_FILE"
