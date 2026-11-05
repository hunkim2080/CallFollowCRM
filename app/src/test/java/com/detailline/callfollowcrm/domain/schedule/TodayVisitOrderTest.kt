package com.detailline.callfollowcrm.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 오늘 갈 현장 '지금 갈 곳' 고르기. (2026-11-05 사장님: 길찾기 하나만, 완료/30분 전에 다음으로 자동 이동)
 *
 * 막는 것 —
 *   ① 끝낸 현장이 '지금 갈 곳'에 남아 길찾기가 안 넘어가는 것
 *   ② 시각 미정이 맨 앞에 서는 것
 *   ③ 같은 시각 현장 순서가 뒤집히는 것(안정 정렬)
 */
class TodayVisitOrderTest {

    private fun k(id: String, min: Int?, done: Boolean = false) =
        TodayVisitOrder.Keyed(id, min, done)

    @Test
    fun `안 끝난 것 시간순 먼저, 지금 갈 곳은 맨 앞`() {
        val r = TodayVisitOrder.arrange(listOf(k("c", 13 * 60), k("a", 9 * 60), k("b", 10 * 60)))
        assertEquals(listOf("a", "b", "c"), r.ordered)
        assertEquals(0, r.currentIndex)   // 9시 a 가 지금 갈 곳
    }

    @Test
    fun `완료 찍으면 그게 아래로, 다음이 지금 갈 곳`() {
        val r = TodayVisitOrder.arrange(listOf(k("a", 9 * 60, done = true), k("b", 10 * 60)))
        assertEquals(listOf("b", "a"), r.ordered)   // 끝낸 a 는 맨 아래
        assertEquals(0, r.currentIndex)             // 지금 갈 곳 = b
    }

    @Test
    fun `전부 완료면 지금 갈 곳 없음`() {
        val r = TodayVisitOrder.arrange(listOf(k("a", 9 * 60, done = true), k("b", 10 * 60, done = true)))
        assertEquals(-1, r.currentIndex)
    }

    @Test
    fun `시각 미정은 맨 뒤`() {
        val r = TodayVisitOrder.arrange(listOf(k("late", null), k("nine", 9 * 60)))
        assertEquals(listOf("nine", "late"), r.ordered)
        assertEquals(0, r.currentIndex)
    }

    @Test
    fun `같은 시각은 넣은 순서 유지 (안정 정렬)`() {
        val r = TodayVisitOrder.arrange(listOf(k("first", 9 * 60), k("second", 9 * 60), k("third", 9 * 60)))
        assertEquals(listOf("first", "second", "third"), r.ordered)
    }

    @Test
    fun `30분 전부터 지금 갈 때 — 9시 현장은 8시 30분부터 true`() {
        assertFalse(TodayVisitOrder.isDue(9 * 60, 8 * 60 + 29))   // 8:29 아직
        assertTrue(TodayVisitOrder.isDue(9 * 60, 8 * 60 + 30))    // 8:30 승격
        assertTrue(TodayVisitOrder.isDue(9 * 60, 10 * 60))        // 지났으면 당연히
        assertTrue(TodayVisitOrder.isDue(null, 0))                // 시각 미정은 항상
    }
}
