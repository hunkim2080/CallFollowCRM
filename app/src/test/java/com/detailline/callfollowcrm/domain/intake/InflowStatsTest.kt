package com.detailline.callfollowcrm.domain.intake

import org.junit.Assert.assertEquals
import org.junit.Test

/** 🔎 유입경로 모아보기 — 경로별 집계 + 세부(블로그·키워드)까지. (2026-10-23 사장님) */
class InflowStatsTest {

    @Test
    fun `경로별로 세고 많은 순으로 준다`() {
        val labels = listOf(
            "네이버 · 블로그 · '줄눈'",
            "네이버 · 카페",
            "인스타",
            "네이버 · 블로그 · '화장실 줄눈'",
            "당근",
        )
        val g = InflowStats.aggregate(labels)
        assertEquals(listOf("네이버", "인스타", "당근"), g.map { it.source })  // 네이버 3 > 인스타 1 = 당근 1
        assertEquals(3, g[0].count)
        assertEquals(5, InflowStats.total(g))
    }

    @Test
    fun `세부 없는 경로는 세부 목록이 비어 있다`() {
        val g = InflowStats.aggregate(listOf("인스타", "인스타"))
        assertEquals(1, g.size)
        assertEquals("인스타", g[0].source)
        assertEquals(2, g[0].count)
        assertEquals(emptyList<InflowDetail>(), g[0].details)
    }

    @Test
    fun `같은 세부는 합쳐서 세고 많은 순`() {
        val g = InflowStats.aggregate(
            listOf("네이버 · 블로그", "네이버 · 블로그", "네이버 · 카페")
        )
        assertEquals(1, g.size)
        assertEquals(3, g[0].count)
        assertEquals(listOf("블로그" to 2, "카페" to 1), g[0].details.map { it.label to it.count })
    }

    @Test
    fun `빈 값과 공백은 무시한다`() {
        val g = InflowStats.aggregate(listOf("", "  ", "네이버"))
        assertEquals(1, g.size)
        assertEquals("네이버", g[0].source)
        assertEquals(1, g[0].count)
    }

    @Test
    fun `아무것도 없으면 빈 목록`() {
        assertEquals(emptyList<InflowGroup>(), InflowStats.aggregate(emptyList()))
        assertEquals(0, InflowStats.total(emptyList()))
    }
}
