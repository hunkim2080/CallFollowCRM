package com.detailline.callfollowcrm.domain.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ⌨️ **키보드 위 빈 칸** — 세 번 밟은 자리라 숫자로 박아 둔다.
 *
 *   사장님 (2026-10-02): "그때 키패드 위에 공백 생긴다고 한거있잖아. 아직도 개선 안 됌"
 *
 * 사장님 폰(S23U)은 **읽기 전용**이라 고친 빌드를 깔아서 눈으로 못 본다.
 * 그래서 **그 폰에서 실제로 잰 숫자**를 여기 박아, 폰 없이도 매 빌드가 검사하게 한다.
 */
class KeyboardFitTest {

    // ── 2026-10-02 S23U(SM-S918N · Android 16) 실측 ────────────────────
    //   창 높이 2316 · 키보드 윗선 1325 → 키보드 991
    //   하단 탭바가 있는 화면은 y=2000 에서 끝난다 → 아래로 316 남음
    private val IME = 991
    private val TAB_BAR = 316

    @Test
    fun `사장님이 본 그 화면 — 탭바가 있으면 탭바 높이는 빼야 한다`() {
        // 전엔 991 을 통째로 띄워 **313px 짜리 빈 띠**가 생겼다(실측).
        assertEquals(675, KeyboardFit.padPx(imePx = IME, shrunkPx = 0, belowPx = TAB_BAR))
    }

    @Test
    fun `띄운 뒤 내용 바닥이 키보드 윗선과 딱 맞는다`() {
        val windowPx = 2316
        val areaBottom = windowPx - TAB_BAR          // 2000 — 탭바 위에서 끝나는 화면
        val contentBottom = areaBottom - KeyboardFit.padPx(IME, 0, TAB_BAR)
        assertEquals("키보드 윗선(1325)과 같아야 한다", 1325, contentBottom)
    }

    @Test
    fun `탭바가 없는 화면은 그대로 키보드만큼`() {
        // 채팅처럼 화면이 창 바닥까지 닿는 곳 — 전에도 멀쩡했고 지금도 그대로여야 한다.
        assertEquals(IME, KeyboardFit.padPx(IME, shrunkPx = 0, belowPx = 0))
    }

    @Test
    fun `창이 이미 줄어든 폰은 그만큼 덜 띄운다`() {
        // adjustResize 가 먹는 폰(갤S9 등) — 시스템이 벌써 비워준 자리를 또 비우면 빈 칸.
        assertEquals(0, KeyboardFit.padPx(IME, shrunkPx = IME, belowPx = 0))
        assertEquals(200, KeyboardFit.padPx(1000, shrunkPx = 800, belowPx = 0))
    }

    @Test
    fun `창도 줄고 탭바도 있으면 둘 다 뺀다`() {
        assertEquals(84, KeyboardFit.padPx(IME, shrunkPx = 591, belowPx = TAB_BAR))
    }

    @Test
    fun `모자라면 0 — 음수로 끌어올리지 않는다`() {
        assertEquals(0, KeyboardFit.padPx(IME, shrunkPx = 900, belowPx = 400))
        assertEquals(0, KeyboardFit.padPx(0, shrunkPx = 0, belowPx = 0))
    }

    @Test
    fun `이상한 음수가 들어와도 버틴다`() {
        assertEquals(IME, KeyboardFit.padPx(IME, shrunkPx = -50, belowPx = -50))
    }
}
