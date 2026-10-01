package com.detailline.callfollowcrm.domain.ui

/**
 * ⌨️ **키보드 때문에 이 자리에 얼마나 더 띄워야 하나** — 셈은 여기 한 곳.
 *
 * Android 의존성이 없어 **폰 없이도 매 빌드가 검사한다**([KeyboardFitTest]).
 * 사장님 폰(S23U)이 읽기 전용이라 새 빌드를 깔아 볼 수가 없다 —
 * 그래서 **실측한 숫자를 시험으로 박아** 둔다.
 *
 * ## 세 번 밟았다
 *   ① (2026-09-26) 창이 이미 줄었는데 키보드 높이를 **또** 더했다 → 빈 칸
 *   ② (2026-09-27) 같은 셈을 하던 12곳이 남아 하루 만에 다시 났다 → 한 곳으로 모음
 *   ③ (2026-10-02) **하단 탭바가 있는 화면**에서 다시 났다 ← 지금 고치는 것
 */
object KeyboardFit {

    /**
     * @param imePx    시스템이 말하는 키보드 높이. **창 바닥**에서 잰 값이다.
     * @param shrunkPx 창이 **이미 줄어든** 만큼(adjustResize 가 먹는 폰). 시스템이 벌써 비워준 자리.
     * @param belowPx  이 자리 **아래**로 창 바닥까지 남은 만큼. 하단 탭바가 있으면 그 높이.
     *
     * @return 이 자리 아래에 더 띄울 픽셀. 0 이면 아무것도 안 한다.
     *
     * 🔑 **[belowPx] 를 빼는 게 핵심이다.** 키보드 높이는 **창 바닥** 기준인데,
     *   탭바가 있는 화면은 그 **탭바 위에서 이미 끝난다.** 겹치는 건 그만큼 적다.
     *   안 빼면 **탭바 높이만큼 두 번** 빠져 그만큼 빈 띠가 생긴다.
     *   (2026-10-02 S23U 실측: 키보드 991 · 탭바 316 → 675 만 띄워야 하는데 991 을 띄웠다)
     */
    fun padPx(imePx: Int, shrunkPx: Int, belowPx: Int, navBarPx: Int = 0): Int {
        val below = belowPx.coerceAtLeast(0)
        val forIme = (imePx - shrunkPx.coerceAtLeast(0) - below).coerceAtLeast(0)
        // ⬇️ 키보드가 내려갔을 때 **내비바**만큼은 띄워야 하는 자리도 있다(바닥에 붙는 시트).
        //   더하지 않고 **큰 쪽**만 쓴다 — 키보드가 올라오면 키보드가 내비바를 이미 덮는다.
        //   더하면 내비바 높이만큼 빈 칸이 남는다. (2026-07-15 사장님 "여기도 빈공간있다")
        val forNav = (navBarPx.coerceAtLeast(0) - below).coerceAtLeast(0)
        return maxOf(forIme, forNav)
    }
}
