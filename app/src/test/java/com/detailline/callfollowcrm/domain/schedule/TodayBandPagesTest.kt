package com.detailline.callfollowcrm.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 📄 **홈 띠 쪽 넘기기** — 사장님이 직접 신고한 자리다. (2026-09-28)
 *
 *   *"글자엔 (1/2) 라 적혀 있는데 넘기면 내일이 나온다"* —
 *   **한 몸짓에 두 가지 뜻**이 섞여 있었다.
 *
 *   ⚠️ 폰에서는 「오늘 0곳」만 아무 때나 볼 수 있다. 오늘 1곳·2곳인 날은
 *      **그런 날이 되어야** 보이니 시험이 대신 본다.
 */
class TodayBandPagesTest {

    // ── 오늘 두 곳 — 사장님이 신고한 그 상황 ──────────────────────

    @Test
    fun `오늘 2곳 + 다음 있음 — 세 쪽이다`() {
        assertEquals(3, TodayBandPages.pageCount(todayCount = 2, hasNext = true))
    }

    @Test
    fun `오늘 2곳이면 1 of 2 다음 2 of 2 로 적힌다`() {
        assertEquals("  (1/2)", TodayBandPages.label(page = 0, todayCount = 2))
        assertEquals("  (2/2)", TodayBandPages.label(page = 1, todayCount = 2))
    }

    @Test
    fun `오늘 것을 다 넘긴 뒤에야 다음 시공 쪽이다`() {
        // 🔴 전엔 **2쪽이 곧 내일**이었다 — 오늘 둘째 곳을 건너뛰었다
        assertFalse(TodayBandPages.isNextPage(page = 0, todayCount = 2))
        assertFalse(TodayBandPages.isNextPage(page = 1, todayCount = 2))
        assertTrue(TodayBandPages.isNextPage(page = 2, todayCount = 2))
    }

    @Test
    fun `다음 시공 쪽에는 곳수를 안 적는다`() {
        assertEquals("", TodayBandPages.label(page = 2, todayCount = 2))
    }

    // ── 오늘 한 곳 ───────────────────────────────────────────────

    @Test
    fun `오늘 1곳이면 곳수를 아예 안 적는다`() {
        // 「(1/1)」은 아무 말도 안 한다 — 적으면 시끄럽기만 하다
        assertEquals("", TodayBandPages.label(page = 0, todayCount = 1))
    }

    @Test
    fun `오늘 1곳 + 다음 있음 — 두 쪽`() {
        assertEquals(2, TodayBandPages.pageCount(todayCount = 1, hasNext = true))
        assertTrue(TodayBandPages.isNextPage(page = 1, todayCount = 1))
    }

    @Test
    fun `오늘 1곳 + 다음 없음 — 한 쪽`() {
        assertEquals(1, TodayBandPages.pageCount(todayCount = 1, hasNext = false))
    }

    // ── 오늘 아무것도 없는 날 ─────────────────────────────────────

    @Test
    fun `오늘 0곳이어도 안내 한 쪽은 남는다`() {
        // 🔴 0쪽이 되면 띠가 **아예 사라진다** — 「오늘은 일정이 없어요」를 못 보여준다
        assertEquals(1, TodayBandPages.todaySlots(0))
        assertEquals(1, TodayBandPages.pageCount(todayCount = 0, hasNext = false))
        assertEquals(2, TodayBandPages.pageCount(todayCount = 0, hasNext = true))
    }

    @Test
    fun `오늘 0곳이면 곳수를 안 적는다`() {
        assertEquals("", TodayBandPages.label(page = 0, todayCount = 0))
    }

    @Test
    fun `오늘 0곳일 때 둘째 쪽은 다음 시공이다`() {
        assertFalse(TodayBandPages.isNextPage(page = 0, todayCount = 0))
        assertTrue(TodayBandPages.isNextPage(page = 1, todayCount = 0))
    }

    // ── 많을 때 · 이상한 값 ──────────────────────────────────────

    @Test
    fun `오늘 네 곳이면 1 of 4 부터 4 of 4 까지`() {
        assertEquals(5, TodayBandPages.pageCount(todayCount = 4, hasNext = true))
        (0..3).forEach { assertEquals("  (${it + 1}/4)", TodayBandPages.label(it, 4)) }
        assertEquals("", TodayBandPages.label(4, 4))
    }

    @Test
    fun `쪽수는 아무리 해도 0 이 안 된다`() {
        assertEquals(1, TodayBandPages.todaySlots(-3))
        assertEquals(1, TodayBandPages.pageCount(todayCount = -3, hasNext = false))
    }

    @Test
    fun `적은 숫자와 넘기는 쪽수가 늘 맞는다`() {
        // 적힌 「N/전체」 의 전체는 **오늘 쪽 수**와 같아야 한다 (한 몸짓 한 뜻)
        for (todayCount in 2..6) {
            val slots = TodayBandPages.todaySlots(todayCount)
            assertEquals(todayCount, slots)
            for (page in 0 until slots) {
                assertEquals("  (${page + 1}/$todayCount)", TodayBandPages.label(page, todayCount))
            }
        }
    }
}
