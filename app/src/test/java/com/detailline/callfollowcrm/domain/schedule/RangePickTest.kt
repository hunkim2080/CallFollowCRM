package com.detailline.callfollowcrm.domain.schedule

import com.detailline.callfollowcrm.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/** 시작→끝 두 번 탭 셈 — 접수서·시공기간·A/S·간단일정이 같은 규칙을 쓴다. */
class RangePickTest {

    private val DAY = DateTimeUtils.DAY_MS
    private val d10 = DAY * 20000      // 아무 날(기준)
    private val d11 = d10 + DAY
    private val d12 = d10 + DAY * 2
    private val d9 = d10 - DAY

    @Test fun `처음 누르면 시작일, 끝은 비움`() {
        assertEquals(d10 to null, RangePick.tap(null, null, d10))
    }

    @Test fun `시작 뒤 더 나중 날 = 끝날`() {
        assertEquals(d10 to d12, RangePick.tap(d10, null, d12))
    }

    @Test fun `이미 기간 잡혔으면 다시 시작부터`() {
        assertEquals(d11 to null, RangePick.tap(d10, d12, d11))
    }

    @Test fun `더 이른 날 누르면 그 날이 새 시작`() {
        assertEquals(d9 to null, RangePick.tap(d10, null, d9))
    }

    @Test fun `같은 날 다시 누르면 시작만`() {
        assertEquals(d10 to null, RangePick.tap(d10, null, d10))
    }

    @Test fun `며칠 계산 — 없음0 당일1 사흘3`() {
        assertEquals(0, RangePick.daysOf(null, null))
        assertEquals(1, RangePick.daysOf(d10, null))
        assertEquals(1, RangePick.daysOf(d10, d10))
        assertEquals(3, RangePick.daysOf(d10, d12))
        assertEquals(1, RangePick.daysOf(d10, d9))   // 끝이 시작보다 이르면 당일
    }
}
