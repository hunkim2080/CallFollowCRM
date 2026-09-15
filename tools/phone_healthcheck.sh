#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════
#  시공막내 폰 건강검진 — 30초. (2026-09-15 사장님)
#
#  왜 만들었나:
#    "상담함에서 쭉 내리면 앱이 꺼진다"의 원인(문자 주소 조회 폭주)이
#    터지기 전부터 로그에 계속 쌓여 있었는데, 아무도 안 봤다.
#    사장님이 아파야만 알게 되는 구조를 바꾸려고 만든 계기판.
#
#  쓰는 법:  bash tools/phone_healthcheck.sh
#    - 폰을 USB 로 꽂고, 화면 잠금 풀어둘 것
#    - 앱을 자동으로 켜서 스크롤을 흉내 내고, 그동안의 숫자를 잰다
#    - 폰의 데이터는 건드리지 않는다(읽기만). 설치도 안 한다.
#
#  기준선: docs/PHONE_BASELINE.md
# ══════════════════════════════════════════════════════════════════
set -u

PKG="com.detailline.callfollowcrm"
FLINGS="${FLINGS:-20}"          # 스크롤 횟수
TMP="$(mktemp -d 2>/dev/null || echo /tmp)"
LOG="$TMP/healthcheck.log"

say() { printf '%s\n' "$*"; }
hr()  { say "────────────────────────────────────────────"; }

# ── 0. 폰 확인 ────────────────────────────────────────────────
DEV_COUNT=$(adb devices | grep -cw "device")
if [ "$DEV_COUNT" -eq 0 ]; then
  say "❌ 폰이 안 잡힙니다. USB 꽂고 화면 잠금을 풀어주세요."
  exit 1
fi

MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
ANDROID=$(adb shell getprop ro.build.version.release | tr -d '\r')
VER=$(adb shell dumpsys package "$PKG" 2>/dev/null | grep -m1 versionName | tr -d '\r' | sed 's/.*versionName=//')
if [ -z "$VER" ]; then
  say "❌ 시공막내가 이 폰에 없습니다."
  exit 1
fi

hr
say "🩺 시공막내 폰 건강검진"
say "   폰: $MODEL (안드로이드 $ANDROID)"
say "   앱: $VER"
say "   검사일: $(date '+%Y-%m-%d %H:%M')"
hr

# ── 1. 데이터 규모 (병은 데이터가 쌓여야 나타난다) ──────────────
SMS_N=$(adb shell "content query --uri content://sms --projection _id" 2>/dev/null | grep -c "_id=")
MMS_N=$(adb shell "content query --uri content://mms --projection _id" 2>/dev/null | grep -c "_id=")
say "📦 데이터 규모"
say "   문자(SMS)     : ${SMS_N}건"
say "   사진문자(MMS) : ${MMS_N}건"
say ""

# ── 2. 검사 전 메모리 ────────────────────────────────────────
#    ⚠️ dumpsys meminfo 는 그 앱에 강제 청소(GC)를 시킨다 →
#       검사 '중'에 부르면 결과가 오염된다. 앞뒤로만 잰다.
pss() { adb shell "dumpsys meminfo $PKG" 2>/dev/null | grep -E "^ +TOTAL " | head -1 | awk '{print $2}'; }

adb shell am force-stop "$PKG"
adb logcat -c >/dev/null 2>&1
adb logcat -v time > "$LOG" 2>&1 &
LOGPID=$!
sleep 1

adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 6
PSS_BEFORE=$(pss)

# ── 3. 스크롤 흉내 (사장님이 '쭉 내리는' 그 동작) ──────────────
say "🔄 상담함에서 ${FLINGS}번 쭉 내려봅니다..."
for _ in $(seq 1 "$FLINGS"); do
  adb shell input swipe 540 1800 540 350 60
done
sleep 4
PSS_AFTER=$(pss)
ALIVE=$(adb shell pidof "$PKG" | tr -d '\r')

kill "$LOGPID" >/dev/null 2>&1
sleep 1

# ── 4. 판독 ──────────────────────────────────────────────────
SKIP_MAX=$(grep -o "Skipped [0-9]* frames" "$LOG" | awk '{print $2}' | sort -rn | head -1)
SKIP_MAX=${SKIP_MAX:-0}
FREEZE_SEC=$(awk -v f="$SKIP_MAX" 'BEGIN{printf "%.1f", f/60}')
ADDR_N=$(grep -c "mms/.*/addr, match=13, calling pid" "$LOG")
# 같은 사진문자를 몇 번씩 다시 물어봤나(반복 배수). 1.0 = 한 건당 딱 한 번 = 이상적.
#   총 횟수만 보면 "사진문자가 많은 폰"과 "같은 걸 계속 되묻는 앱"을 구분 못 한다.
ADDR_UNIQ=$(grep -o "content://mms/[0-9]*/addr" "$LOG" | sort -u | wc -l | tr -d ' ')
if [ "${ADDR_UNIQ:-0}" -gt 0 ]; then
  ADDR_RATIO=$(awk -v t="$ADDR_N" -v u="$ADDR_UNIQ" 'BEGIN{printf "%.1f", t/u}')
else
  ADDR_RATIO="0.0"
fi
CRASH_N=$(grep -cE "FATAL EXCEPTION|am_anr" "$LOG")

say ""
hr
say "📋 검진 결과"
hr
say "  화면 멈춤(최대)   : ${FREEZE_SEC}초   (${SKIP_MAX}프레임)"
say "  문자창고 조회     : ${ADDR_N}회 (서로 다른 ${ADDR_UNIQ}건 × 반복 ${ADDR_RATIO}배)"
say "  메모리            : ${PSS_BEFORE}KB → ${PSS_AFTER}KB"
say "  앱 생존           : $([ -n "$ALIVE" ] && echo '살아있음 ✅' || echo '꺼짐 ❌')"
say "  크래시/응답없음   : ${CRASH_N}건"
say ""

# 기준: 안드로이드가 '응답 없음'으로 앱을 죽이는 선 = 5초.
WARN=0
awk -v s="$FREEZE_SEC" 'BEGIN{exit !(s>=5)}' && { say "  🔴 화면 멈춤 ${FREEZE_SEC}초 — 안드로이드가 앱을 죽이는 선(5초)을 넘었습니다."; WARN=1; }
awk -v s="$FREEZE_SEC" 'BEGIN{exit !(s>=1.5 && s<5)}' && { say "  🟡 화면 멈춤 ${FREEZE_SEC}초 — 사장님이 '버벅인다'고 느끼는 구간."; WARN=1; }
# 판정은 '총 횟수'가 아니라 '반복 배수'로 한다 — 사진문자가 많은 건 죄가 아니고,
#   같은 걸 계속 되묻는 게 죄다. (2026-09-15 실측: 수정 전 8.4배 → 캐시 후 2.5배 → 목표 1.0배)
awk -v r="$ADDR_RATIO" 'BEGIN{exit !(r>=4)}' && { say "  🔴 같은 사진문자를 ${ADDR_RATIO}번씩 다시 물어봅니다 — 폭주입니다."; WARN=1; }
awk -v r="$ADDR_RATIO" 'BEGIN{exit !(r>=1.5 && r<4)}' && { say "  🟡 같은 사진문자를 ${ADDR_RATIO}번씩 다시 물어봅니다 — 아직 낭비가 있습니다."; WARN=1; }
[ -z "$ALIVE" ] && { say "  🔴 검사 도중 앱이 꺼졌습니다."; WARN=1; }
[ "$CRASH_N" -gt 0 ] && { say "  🔴 크래시/응답없음 기록 ${CRASH_N}건 — 로그: $LOG"; WARN=1; }
[ "$WARN" -eq 0 ] && say "  ✅ 이상 없음."

say ""
say "  전체 로그: $LOG"
hr
