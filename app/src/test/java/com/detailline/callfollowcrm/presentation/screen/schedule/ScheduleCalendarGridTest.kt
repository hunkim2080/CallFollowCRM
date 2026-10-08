package com.detailline.callfollowcrm.presentation.screen.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 달력 lane 패킹(packLanes) 검증 — 시공·간단 일정이 같은 공간을 나눠 쓰되,
 *   겹치면 다른 줄, 안 겹치면 같은 줄을 재사용. (v64, 2026-10-08)
 *   구간은 (key, 시작ms, 끝ms) — 끝은 **포함**(그 날까지 걸침).
 */
class ScheduleCalendarGridTest {

    private val DAY = 24L * 60 * 60 * 1000
    private fun day(n: Int) = n * DAY

    @Test fun `안 겹치면 같은 줄을 재사용한다`() {
        val map = packLanes(
            listOf(
                Triple("a", day(0), day(0)),
                Triple("b", day(2), day(2)),
                Triple("c", day(4), day(4))
            )
        )
        assertEquals(0, map["a"])
        assertEquals(0, map["b"])
        assertEquals(0, map["c"])
    }

    @Test fun `겹치면 다른 줄로 간다`() {
        val map = packLanes(
            listOf(
                Triple("long", day(0), day(3)), // 0~3 걸침
                Triple("mid", day(1), day(1))   // 1일 — long 과 겹침
            )
        )
        assertNotEquals("겹치는 둘은 다른 lane", map["long"], map["mid"])
    }

    @Test fun `같은 날 둘이면 다른 줄`() {
        val map = packLanes(
            listOf(
                Triple("x", day(5), day(5)),
                Triple("y", day(5), day(5))
            )
        )
        assertNotEquals(map["x"], map["y"])
    }

    @Test fun `긴 구간 뒤 끝나면 다음은 같은 줄`() {
        val map = packLanes(
            listOf(
                Triple("trip", day(0), day(5)), // 0~5
                Triple("after", day(7), day(7)) // 7일 — 끝난 뒤
            )
        )
        assertEquals(map["trip"], map["after"]) // 같은 줄 재사용
    }

    @Test fun `세 개가 전부 겹치면 lane 0 1 2`() {
        val map = packLanes(
            listOf(
                Triple("a", day(0), day(4)),
                Triple("b", day(0), day(4)),
                Triple("c", day(0), day(4))
            )
        )
        assertEquals(setOf(0, 1, 2), setOf(map["a"], map["b"], map["c"]))
    }
}
