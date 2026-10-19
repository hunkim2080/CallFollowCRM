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

    // ── 「금액」 토글 — 칸 띠에 붙는 money 글자 (2026-10-19 사장님 프로토) ──

    private fun job(id: Long, dayMs: Long, addr: String?, total: Long?, days: Int = 1) =
        com.detailline.callfollowcrm.data.local.entity.CustomerEntity(
            id = id, phoneNumber = "0100000000$id", address = addr,
            scheduledWorkDate = dayMs, scheduledWorkDays = days, totalAmount = total,
            createdAt = 0L, updatedAt = 0L
        )

    /** 보고 있는 달의 15일(자정) — 42칸 창 안에 확실히 든다. */
    private val anchor: Long = java.util.Calendar.getInstance().apply {
        set(2026, java.util.Calendar.OCTOBER, 1, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val mid: Long = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(anchor + 14 * DAY)
    private fun cellOf(cells: List<CalendarCell>, dayMs: Long) = cells.first { it.dayStartMs == dayMs }

    @Test fun `토글 끔이면 money 가 없다 - 지금과 똑같이`() {
        val cells = buildCalendarCells(anchor, listOf(job(1, mid, "서울 동대문구 장안동 1", 650_000L)), emptyList(), anchor)
        val bar = cellOf(cells, mid).bars.single()
        assertEquals("동대문", bar.label)
        assertEquals(null, bar.money)
    }

    @Test fun `토글 켬 - 한 날 두 현장이면 줄마다 각각`() {
        val cells = buildCalendarCells(
            anchor,
            listOf(
                job(1, mid, "서울 동대문구 장안동 1", 1_000_000L),
                job(2, mid, "서울 서대문구 홍은동 2", 900_000L)
            ),
            emptyList(), anchor, showMoney = true
        )
        val bars = cellOf(cells, mid).bars
        assertEquals(setOf("동대문 [100]", "서대문 [90]"), bars.map { plainOf(it) }.toSet())
    }

    @Test fun `토글 켬 - 금액 안 적은 현장은 대시`() {
        val cells = buildCalendarCells(anchor, listOf(job(1, mid, "서울 동대문구 장안동 1", null)), emptyList(), anchor, showMoney = true)
        assertEquals("동대문 [–]", plainOf(cellOf(cells, mid).bars.single()))
    }

    @Test fun `여러 날 시공은 첫날에만 금액`() {
        val cells = buildCalendarCells(anchor, listOf(job(1, mid, "서울 동대문구 장안동 1", 650_000L, days = 3)), emptyList(), anchor, showMoney = true)
        assertEquals("65", cellOf(cells, mid).bars.single().money)
        assertEquals(null, cellOf(cells, mid + DAY).bars.single().money)
        assertEquals(null, cellOf(cells, mid + 2 * DAY).bars.single().money)
    }

    private fun plainOf(bar: DayBar) =
        com.detailline.callfollowcrm.domain.schedule.CalendarMoneyLabel.plain(bar.label, bar.money)
}
