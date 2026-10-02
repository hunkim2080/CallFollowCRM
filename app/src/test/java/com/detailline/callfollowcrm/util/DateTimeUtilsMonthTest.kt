package com.detailline.callfollowcrm.util

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 📅 **달 경계** 셈 — `startOfMonth` · `shiftMonth`. (2026-10-02)
 *
 *   왜 테스트가 필요한가: 이 셈이 **글자 여섯 가지로 흩어져** 있었다
 *   (`monthStartOf` · `monthAnchorOf` · `monthAnchor` · `shiftMonth` · `shiftMonthMs` ·
 *    손으로 적은 `set(DAY_OF_MONTH, 1)`). 정산·통계·내기록·일정이 **각자** 세고 있었으니
 *   한쪽만 고치면 **같은 달인데 화면마다 합계가 달라진다.**
 *   한 곳으로 합쳤으니 **그 한 곳이 맞는지**는 테스트가 지킨다.
 *
 *   ⚠️ 폰의 시간대(KST)를 그대로 쓴다 — 특정 시간대를 가정하지 않고, **들어온 값과 나온 값의
 *      관계**만 본다(1일인가 · 0시인가 · 달이 하나 옮겨졌나). 그래야 어느 폰에서도 참이다.
 */
class DateTimeUtilsMonthTest {

    private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

    private fun ms(year: Int, month1to12: Int, day: Int, hour: Int = 0, min: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month1to12 - 1, day, hour, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ── startOfMonth ────────────────────────────────────────────────

    @Test
    fun `달 중간을 주면 그 달 1일 0시로 온다`() {
        val c = cal(DateTimeUtils.startOfMonth(ms(2026, 10, 17, 14, 35)))
        assertEquals(2026, c.get(Calendar.YEAR))
        assertEquals(10, c.get(Calendar.MONTH) + 1)
        assertEquals(1, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, c.get(Calendar.MINUTE))
        assertEquals(0, c.get(Calendar.SECOND))
        assertEquals(0, c.get(Calendar.MILLISECOND))
    }

    @Test
    fun `이미 1일 0시면 그대로다`() {
        val start = DateTimeUtils.startOfMonth(ms(2026, 3, 1))
        assertEquals(start, DateTimeUtils.startOfMonth(start))
    }

    @Test
    fun `말일 밤 늦게도 같은 달 1일로 온다`() {
        // 🔴 23시 59분이 **다음 달**로 넘어가면 그 달 매출이 사라진다
        val a = DateTimeUtils.startOfMonth(ms(2026, 1, 31, 23, 59))
        val b = DateTimeUtils.startOfMonth(ms(2026, 1, 1, 0, 0))
        assertEquals(b, a)
    }

    @Test
    fun `같은 달 아무 날이나 같은 값을 준다`() {
        val days = listOf(1, 2, 13, 28, 30)
        val got = days.map { DateTimeUtils.startOfMonth(ms(2026, 4, it, 9, 7)) }.distinct()
        assertEquals("같은 달인데 값이 갈렸다: $got", 1, got.size)
    }

    // ── shiftMonth ──────────────────────────────────────────────────

    @Test
    fun `지난달과 다음달`() {
        val now = ms(2026, 10, 17, 14, 0)
        val prev = cal(DateTimeUtils.shiftMonth(now, -1))
        val next = cal(DateTimeUtils.shiftMonth(now, +1))
        assertEquals(9, prev.get(Calendar.MONTH) + 1)
        assertEquals(11, next.get(Calendar.MONTH) + 1)
        assertEquals(1, prev.get(Calendar.DAY_OF_MONTH))
        assertEquals(1, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, next.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `12월에서 다음달은 다음 해 1월`() {
        // 🔴 연말에 해가 안 넘어가면 **1월 정산이 작년으로** 들어간다
        val c = cal(DateTimeUtils.shiftMonth(ms(2026, 12, 15), +1))
        assertEquals(2027, c.get(Calendar.YEAR))
        assertEquals(1, c.get(Calendar.MONTH) + 1)
    }

    @Test
    fun `1월에서 지난달은 작년 12월`() {
        val c = cal(DateTimeUtils.shiftMonth(ms(2026, 1, 5), -1))
        assertEquals(2025, c.get(Calendar.YEAR))
        assertEquals(12, c.get(Calendar.MONTH) + 1)
    }

    @Test
    fun `1월 31일에서 다음달은 2월 1일 - 말일이 없어도 안 튄다`() {
        // 🔴 Calendar 에 31일을 그대로 들고 add 하면 3월로 튀는 함정이 있다
        val c = cal(DateTimeUtils.shiftMonth(ms(2026, 1, 31), +1))
        assertEquals(2, c.get(Calendar.MONTH) + 1)
        assertEquals(1, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `0 을 주면 그 달의 시작과 같다`() {
        val any = ms(2026, 7, 22, 18, 40)
        assertEquals(DateTimeUtils.startOfMonth(any), DateTimeUtils.shiftMonth(any, 0))
    }

    @Test
    fun `열두 번 넘기면 한 해가 지난다`() {
        var t = DateTimeUtils.startOfMonth(ms(2026, 5, 9))
        repeat(12) { t = DateTimeUtils.shiftMonth(t, +1) }
        val c = cal(t)
        assertEquals(2027, c.get(Calendar.YEAR))
        assertEquals(5, c.get(Calendar.MONTH) + 1)
    }

    @Test
    fun `앞으로 갔다 뒤로 오면 제자리다`() {
        val base = DateTimeUtils.startOfMonth(ms(2026, 2, 14))
        for (d in 1..18) {
            assertEquals(
                "+$d 갔다 -$d 왔는데 제자리가 아니다",
                base, DateTimeUtils.shiftMonth(DateTimeUtils.shiftMonth(base, d), -d)
            )
        }
    }

    @Test
    fun `지난달 시작은 이번달 시작보다 작다`() {
        val now = ms(2026, 10, 2, 11, 11)
        assertTrue(DateTimeUtils.shiftMonth(now, -1) < DateTimeUtils.startOfMonth(now))
        assertTrue(DateTimeUtils.startOfMonth(now) < DateTimeUtils.shiftMonth(now, +1))
    }

    @Test
    fun `달의 시작은 하루의 시작이기도 하다`() {
        val m = DateTimeUtils.startOfMonth(ms(2026, 6, 21, 23, 58))
        assertEquals(m, DateTimeUtils.startOfDay(m))
    }
}
