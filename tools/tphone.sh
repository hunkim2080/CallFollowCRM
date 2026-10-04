#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# 테스트폰 전원·배터리 관리 — **테스트폰(S9+)에서만** 돈다. 업무폰엔 절대 안 돈다.
#
# 왜: 사장님이 테스트폰을 꽂아두고 외출하신다. 그동안 Claude 가 원격(adb)으로
#     설치·테스트하고, 안 할 땐 배터리·발열을 줄여 재워둔다.
#   ⚠️ 테스트폰은 **진짜 앱 그대로** 돌아야 테스트가 믿을 만하다 → 앱 코드는 안 건드린다.
#      대신 **폰 바깥(설정·프로세스)** 으로만 조인다.
#
# 쓰는 법:
#   bash tools/tphone.sh quiet   — 재우기(테스트 끝날 때마다). 앱 멈춤·화면 끔·밝기 낮춤·배경 제한
#   bash tools/tphone.sh wake    — 깨우기(테스트 시작). 화면 켬·배경 제한 해제
#   bash tools/tphone.sh status  — 지금 상태(배터리·앱 떠있나)
#   bash tools/tphone.sh protect — 배터리 보호(85% 상한, 삼성) 켜기 — 늘 꽂아두니 수명 보호
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

TEST_SERIAL="23514638000c7ece"      # 테스트폰 S9+ (우리 키 직접 설치). 이 폰에서만 동작.
PKG="com.detailline.callfollowcrm"
export MSYS_NO_PATHCONV=1

# ── 안전장치: 붙어 있는 폰이 '진짜 테스트폰'인지 일련번호로 확인 ──
present="$(adb devices | awk 'NR>1 && $2=="device"{print $1}')"
if ! printf '%s\n' "$present" | grep -qx "$TEST_SERIAL"; then
  echo "⛔ 테스트폰($TEST_SERIAL)이 안 붙어 있습니다."
  echo "   지금 붙은 폰: ${present:-(없음)}"
  echo "   → 업무폰·다른 폰에는 이 스크립트를 **절대 안 돌립니다.**"
  exit 1
fi
# 혹시 모를 이중 안전 — 설치 출처가 'vending'(플레이=업무폰)이면 멈춘다
src="$(adb -s "$TEST_SERIAL" shell cmd package list packages -i "$PKG" 2>/dev/null | sed 's/.*installer=//' | tr -d '\r')"
if [ "$src" = "com.android.vending" ]; then
  echo "⛔ 이 폰은 플레이스토어 설치($src) = 업무폰일 수 있습니다. 중단."
  exit 1
fi

A() { adb -s "$TEST_SERIAL" shell "$@"; }

case "${1:-status}" in
  quiet)
    echo "😴 테스트폰 재우기…"
    A am force-stop "$PKG"                              # 앱 배경 작업 0
    A cmd appops set "$PKG" RUN_ANY_IN_BACKGROUND ignore 2>/dev/null   # 배경 실행 막기
    A dumpsys deviceidle whitelist -"$PKG" >/dev/null 2>&1 || true     # 화이트리스트에서 빼기
    A settings put system screen_brightness 20 2>/dev/null             # 밝기 최저(화면 켜질 때)
    A input keyevent 26 2>/dev/null                     # 화면 끄기(POWER)
    echo "   ✓ 앱 멈춤 · 배경 제한 · 밝기↓ · 화면 끔. (다음 wake 까지 유지)"
    ;;
  wake)
    echo "☀️ 테스트폰 깨우기…"
    A cmd appops set "$PKG" RUN_ANY_IN_BACKGROUND allow 2>/dev/null
    A input keyevent 224 2>/dev/null                    # 화면 켜기(WAKEUP)
    A input keyevent 82 2>/dev/null                     # 잠금 밀기(MENU — 핀 없을 때)
    echo "   ✓ 배경 제한 풀고 화면 켬. 이제 설치·테스트 가능."
    ;;
  protect)
    # 삼성 배터리 보호(85% 상한). 늘 꽂아두면 100% 유지가 배터리를 늙힌다.
    echo "🔋 배터리 보호(85% 상한) 켜기…"
    A settings put global settings_screen_lock_show_notification 0 >/dev/null 2>&1 || true
    if A settings put global protect_battery 1 2>/dev/null; then
      echo "   ✓ protect_battery=1 (삼성). 안 먹으면 설정>배터리>'배터리 보호' 수동."
    else
      echo "   ! 이 폰은 adb 로 안 됨 → 설정>디바이스케어>배터리>'배터리 보호' 수동 ON."
    fi
    ;;
  status|*)
    echo "── 테스트폰 상태 ──"
    A dumpsys battery 2>/dev/null | grep -iE "level|temperature|status" | sed 's/^/  /' | tr -d '\r'
    echo -n "  앱 떠있나: "
    if A pidof "$PKG" >/dev/null 2>&1; then echo "예(실행 중)"; else echo "아니오(멈춤)"; fi
    echo -n "  밝기: "; A settings get system screen_brightness 2>/dev/null | tr -d '\r'
    ;;
esac
