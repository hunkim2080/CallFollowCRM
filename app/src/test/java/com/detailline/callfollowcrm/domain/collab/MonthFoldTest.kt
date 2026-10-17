package com.detailline.callfollowcrm.domain.collab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 📅 달 접기 — 기본은 최근 달만 펼침, 머리를 누르면 그 달만 뒤집힘. (2026-10-17) */
class MonthFoldTest {

    private val months = listOf("2026-10", "2026-09", "2026-08")

    @Test
    fun `최근 달 = 문자열 최대값, 비면 null`() {
        assertEquals("2026-10", MonthFold.latestOf(months))
        assertEquals("2026-10", MonthFold.latestOf(months.reversed()))
        assertNull(MonthFold.latestOf(emptyList()))
    }

    @Test
    fun `기본값 - 최근 달만 펼침, 나머지 접힘`() {
        val latest = MonthFold.latestOf(months)
        assertTrue(MonthFold.isOpen("2026-10", latest, emptySet()))
        assertFalse(MonthFold.isOpen("2026-09", latest, emptySet()))
        assertFalse(MonthFold.isOpen("2026-08", latest, emptySet()))
    }

    @Test
    fun `머리를 누르면 그 달만 뒤집힌다 - 두 번 누르면 원래대로`() {
        val latest = "2026-10"
        var t = emptySet<String>()
        t = MonthFold.toggle(t, "2026-09")
        assertTrue(MonthFold.isOpen("2026-09", latest, t))
        assertTrue("다른 달은 안 건드린다", MonthFold.isOpen("2026-10", latest, t))
        assertFalse(MonthFold.isOpen("2026-08", latest, t))
        t = MonthFold.toggle(t, "2026-09")
        assertFalse(MonthFold.isOpen("2026-09", latest, t))
        assertEquals(emptySet<String>(), t)
    }

    @Test
    fun `최근 달도 누르면 접힌다`() {
        val t = MonthFold.toggle(emptySet(), "2026-10")
        assertFalse(MonthFold.isOpen("2026-10", "2026-10", t))
    }

    @Test
    fun `새 달이 생기면 새 최근 달이 저절로 펼쳐지고 전에 뒤집은 건 그대로`() {
        val t = MonthFold.toggle(emptySet(), "2026-09")     // 9월을 펼쳐 뒀다
        // 11월 데이터가 새로 왔다
        val latest = MonthFold.latestOf(months + "2026-11")
        assertTrue(MonthFold.isOpen("2026-11", latest, t))
        assertFalse("옛 최근 달(10월)은 이제 기본 접힘", MonthFold.isOpen("2026-10", latest, t))
        assertTrue("뒤집어 둔 9월은 그대로 펼침", MonthFold.isOpen("2026-09", latest, t))
    }

    @Test
    fun `달이 하나뿐이면 그게 펼쳐진다`() {
        val latest = MonthFold.latestOf(listOf("2026-10"))
        assertTrue(MonthFold.isOpen("2026-10", latest, emptySet()))
    }
}
