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

    @Test fun `수지 시공과 수지 처인 협업을 각각 세 줄로 표시한다`() {
        val bar = DayBar(0, BarSeg.SINGLE, false, "수지", money = "65",
            moneyStage = com.detailline.callfollowcrm.domain.job.CompletionStage.DONE)
        assertEquals(
            listOf(CalendarDayItem.Bar(bar), CalendarDayItem.Collab("수지"), CalendarDayItem.Collab("처인")),
            calendarDayItems(listOf(bar), collabRegions = listOf("수지", "처인"))
        )
    }

    @Test fun `같은 지역 협업도 현장 수만큼 표시하고 네 건부터 나머지를 센다`() {
        for (count in 0..8) {
            val items = calendarDayItems(emptyList(), collabRegions = List(count) { "수지" })
            val expected = List(if (count <= 3) count else 2) { CalendarDayItem.Collab("수지") } +
                if (count > 3) listOf(CalendarDayItem.More(count - 2)) else emptyList()
            assertEquals("count=$count", expected, items)
        }
    }

    @Test fun `협업 AS 요청 순서로 합치며 종류와 관계없이 더보기 수를 센다`() {
        assertEquals(
            listOf(CalendarDayItem.Collab("수지"), CalendarDayItem.As("처인"), CalendarDayItem.Pending("강서")),
            calendarDayItems(emptyList(), listOf("수지"), listOf("처인"), listOf("강서"))
        )
        assertEquals(
            listOf(CalendarDayItem.As("수지"), CalendarDayItem.As("처인"), CalendarDayItem.More(2)),
            calendarDayItems(emptyList(), asRegions = listOf("수지", "처인"), pendingRegions = listOf("강서", "요청"))
        )
    }

    @Test fun `앞줄 일정이 끝나도 여러 날 시공의 lane과 구간을 유지한다`() {
        val cells = buildCalendarCells(anchor, listOf(
            job(1, mid, "서울 강서구", null, days = 2),
            job(2, mid + DAY, "서울 서대문구", 650_000L, days = 3)
        ), emptyList(), anchor, showMoney = true)
        val start = cellOf(cells, mid + DAY).bars.first { it.lane == 1 }
        assertEquals(BarSeg.START, start.seg)
        for ((offset, seg) in listOf(2 to BarSeg.MID, 3 to BarSeg.END)) {
            val bars = cellOf(cells, mid + offset * DAY).bars
            val items = calendarDayItems(bars)
            assertEquals(null, items[0])
            assertEquals(CalendarDayItem.Bar(bars.single()), items[1])
            assertEquals(seg, bars.single().seg)
            assertEquals(CalendarDayItem.Collab("수지"), calendarDayItems(bars, listOf("수지"))[0])
        }
    }

    @Test fun `겹치는 시공 lane을 합치지 않고 숨긴 건수를 표시한다`() {
        val cells = buildCalendarCells(anchor, (1L..5L).map {
            job(it, mid, "서울 강서구", null, days = 2)
        }, emptyList(), anchor)
        for (dayMs in listOf(mid, mid + DAY)) {
            val bars = cellOf(cells, dayMs).bars
            assertEquals(listOf(0, 1, 2, 3, 4), bars.map { it.lane })
            assertEquals(listOf(CalendarDayItem.Bar(bars[0]), CalendarDayItem.Bar(bars[1]), CalendarDayItem.More(3)),
                calendarDayItems(bars))
        }
        val highBar = DayBar(3, BarSeg.MID, false)
        assertEquals(listOf(null, null, CalendarDayItem.More(1)), calendarDayItems(listOf(highBar)))
    }

    @Test fun `AS는 겹치는 모든 현장을 기간 전체에 모으고 주소가 없어도 센다`() {
        val customers = listOf(
            job(1, mid, "서울 강서구", null).copy(asScheduledDate = mid, asScheduledDays = 2),
            job(2, mid, "서울 강서구", null).copy(asScheduledDate = mid + DAY, asScheduledDays = 1),
            job(3, mid, null, null).copy(asScheduledDate = mid + DAY, asScheduledDays = 0),
            job(4, mid, "서울 서대문구", null)
        )
        assertEquals(mapOf(mid to listOf("강서"), mid + DAY to listOf("강서", "강서", "A/S")), asCalendarRegions(customers))
    }

    private fun plainOf(bar: DayBar) =
        com.detailline.callfollowcrm.domain.schedule.CalendarMoneyLabel.plain(bar.label, bar.money)
}
