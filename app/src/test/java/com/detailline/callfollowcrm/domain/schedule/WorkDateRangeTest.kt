package com.detailline.callfollowcrm.domain.schedule

import com.detailline.callfollowcrm.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 🏗️ 시공 기간 ms 범위 — 「시공한 날 사진만」 선택창이 이 범위로 사진을 거른다. (2026-11-11) */
class WorkDateRangeTest {

    private val day0 = DateTimeUtils.startOfDay(System.currentTimeMillis())
    private val day1 = day0 + DateTimeUtils.DAY_MS

    @Test
    fun `시공일 없으면 범위 없음`() {
        assertNull(WorkDateRange.of(null, 1))
        assertFalse(WorkDateRange.covers(null, 1, day0))
    }

    @Test
    fun `하루 시공은 그날 자정부터 끝까지`() {
        val r = WorkDateRange.of(day0, 1)!!
        assertEquals(day0, r.first)
        assertEquals(day0 + DateTimeUtils.DAY_MS - 1, r.last)
        assertTrue(WorkDateRange.covers(day0, 1, day0))                       // 자정 포함
        assertTrue(WorkDateRange.covers(day0, 1, day0 + 13 * 3600_000L))      // 한낮 포함
        assertFalse(WorkDateRange.covers(day0, 1, day1))                      // 다음날 제외
    }

    @Test
    fun `여러 날 시공은 마지막 날 끝까지`() {
        assertTrue(WorkDateRange.covers(day0, 2, day1 + 12 * 3600_000L))      // 둘째날 포함
        assertFalse(WorkDateRange.covers(day0, 2, day0 + 2 * DateTimeUtils.DAY_MS)) // 셋째날 제외
    }

    @Test
    fun `정규화 안 된 시공일도 그날 자정으로`() {
        val noon = day0 + 13 * 3600_000L
        assertEquals(day0, WorkDateRange.of(noon, 1)!!.first)
    }
}
