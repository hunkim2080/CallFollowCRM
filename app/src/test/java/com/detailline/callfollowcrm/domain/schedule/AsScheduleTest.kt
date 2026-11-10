package com.detailline.callfollowcrm.domain.schedule

import com.detailline.callfollowcrm.util.DateTimeUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🔧 A/S 날짜 셈 — 홈·일정·캘린더가 **같은 판정**을 쓰도록 한 곳으로 모았다. (2026-11-11)
 * 막는 것: 홈이 A/S 를 못 봐 "오늘 일정이 없어요" 라 하던 버그.
 */
class AsScheduleTest {

    private val day0 = DateTimeUtils.startOfDay(System.currentTimeMillis())
    private val day1 = day0 + DateTimeUtils.DAY_MS
    private val day2 = day0 + 2 * DateTimeUtils.DAY_MS

    @Test
    fun `A_S 없으면 어느 날도 안 덮는다`() {
        assertFalse(AsSchedule.coversDay(null, 1, day0))
    }

    @Test
    fun `하루짜리 A_S 는 그날만 덮는다`() {
        assertTrue(AsSchedule.coversDay(day0, 1, day0))
        assertFalse(AsSchedule.coversDay(day0, 1, day1))
    }

    @Test
    fun `여러 날 A_S 는 기간 전체를 덮는다`() {
        // 2일짜리: 시작일 + 다음날 O, 셋째날 X
        assertTrue(AsSchedule.coversDay(day0, 2, day0))
        assertTrue(AsSchedule.coversDay(day0, 2, day1))
        assertFalse(AsSchedule.coversDay(day0, 2, day2))
    }

    @Test
    fun `days 가 0이하라도 하루로 센다`() {
        assertTrue(AsSchedule.coversDay(day0, 0, day0))
        assertFalse(AsSchedule.coversDay(day0, 0, day1))
    }

    @Test
    fun `정규화 안 된 시각이 들어와도 그날로 덮는다`() {
        // asScheduledDate 가 startOfDay 가 아니라 한낮이어도 그날을 덮어야 한다
        val noon = day0 + 13 * 60 * 60 * 1000L
        assertTrue(AsSchedule.coversDay(noon, 1, day0))
    }
}
