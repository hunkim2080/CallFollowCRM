package com.detailline.callfollowcrm.domain.collab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 협업 배정 줄 파싱 + "그 건(날짜)에만 붙는다" 판단 검증. (2026-10-09 사장님 — 1차/2차 분리)
 */
class CollabAssignmentLineTest {

    private val DAY = 24L * 60 * 60 * 1000
    private fun day(n: Int) = n * DAY

    @Test fun `5토큰 파싱 — days 포함`() {
        val l = CollabAssignmentLine.parse("7|01012345678|하우스픽|sh_abc|${day(10)},${day(11)}")!!
        assertEquals(7L, l.customerId)
        assertEquals("01012345678", l.phone)
        assertEquals("하우스픽", l.name)
        assertEquals("sh_abc", l.shareId)
        assertEquals(setOf(day(10), day(11)), l.days)
    }

    @Test fun `4토큰(구버전 days 없음) 파싱`() {
        val l = CollabAssignmentLine.parse("7|01012345678|하우스픽|sh_abc")!!
        assertEquals("sh_abc", l.shareId)
        assertTrue(l.days.isEmpty())
    }

    @Test fun `2토큰(아주 옛날 id name) 파싱`() {
        val l = CollabAssignmentLine.parse("7|하우스픽")!!
        assertEquals(7L, l.customerId)
        assertEquals("하우스픽", l.name)
        assertEquals("", l.shareId)
        assertTrue(l.days.isEmpty())
    }

    @Test fun `customerId 없으면 null`() {
        assertNull(CollabAssignmentLine.parse("|a|b"))
        assertNull(CollabAssignmentLine.parse("abc|a|b"))
    }

    @Test fun `format 왕복 — days 정렬`() {
        val l = CollabAssignmentLine(7, "01012345678", "하우스픽", "sh_abc", setOf(day(11), day(10)))
        assertEquals("7|01012345678|하우스픽|sh_abc|${day(10)},${day(11)}", l.format())
        assertEquals(l.copy(), CollabAssignmentLine.parse(l.format()))
    }

    // ---- coversDay: 핵심 — 그 건(날짜)에만 ----

    @Test fun `days 있으면 그 날에만 — 1차 협업자가 2차에 안 뜸`() {
        val h = CollabAssignmentLine(7, "p", "하우스픽", "sh1", setOf(day(30)))      // 1차 9/30
        assertTrue(h.coversDay(day(30), representativeDay = day(30)))
        assertFalse("2차 날짜엔 안 떠야", h.coversDay(day(45), representativeDay = day(30)))  // 2차(10/15)
    }

    @Test fun `days 비면 대표 건 날짜 하나로만 (전체 X)`() {
        val legacy = CollabAssignmentLine(7, "p", "태그줄눈", "sh2", emptySet())
        assertTrue(legacy.coversDay(day(45), representativeDay = day(45)))
        assertFalse("대표일 아니면 안 떠야", legacy.coversDay(day(30), representativeDay = day(45)))
    }

    @Test fun `days 비고 대표일 null 이면 안 뜸 (옛날처럼 전체로 안 퍼짐)`() {
        val legacy = CollabAssignmentLine(7, "p", "태그줄눈", "sh2", emptySet())
        assertFalse(legacy.coversDay(day(30), representativeDay = null))
        assertFalse(legacy.coversDay(day(45), representativeDay = null))
    }

    @Test fun `withDays — 일정 변경 시 날짜 갈아끼우기`() {
        val before = CollabAssignmentLine(7, "p", "하우스픽", "sh1", setOf(day(30)))
        val after = before.withDays(setOf(day(45)))
        assertEquals(setOf(day(45)), after.days)
        assertFalse(after.coversDay(day(30), representativeDay = day(45)))
        assertTrue(after.coversDay(day(45), representativeDay = day(45)))
    }
}
