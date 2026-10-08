package com.detailline.callfollowcrm.presentation.screen.schedule

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import java.util.Calendar

// 🍃 일정 달력 격자(月 7×6=42칸)의 **순수 계산** — 화면 파일(ScheduleScreen)에서 떼어냄. (2026-10-08, §12-F 잎 추출)
//   시공·간단 일정을 lane 에 배치하고 START/MID/END 막대 조각으로 만든다. Composable 없음(그리는 건 ScheduleScreen).

/** 달력 한 칸에 그리는 막대 줄 수 상한 (lane 0~1 = 최대 2줄). 칸 렌더러와 반드시 같은 값. */
internal const val CAL_MAX_LANE = 2

internal enum class BarSeg { SINGLE, START, MID, END }

/** 띠 종류 — 색을 가른다. 시공(초록/지난 회색) vs 간단 일정(청록). (2026-10-08 사장님) */
internal enum class BarKind { SCHEDULE, SIMPLE }

internal data class DayBar(
    val lane: Int,
    val seg: BarSeg,
    val past: Boolean,
    /** 칸에 적을 **지역명**(시공) 또는 **제목**(간단). 여러 날은 **첫날만** 채운다(날마다 반복하면 지저분). */
    val label: String? = null,
    val kind: BarKind = BarKind.SCHEDULE
)

internal data class CalendarCell(
    val dayStartMs: Long,
    val dayOfMonth: Int,
    val dayOfWeek: Int, // Calendar.SUNDAY..SATURDAY
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val scheduleCount: Int,
    val hasPastSchedule: Boolean,
    val hasUpcomingSchedule: Boolean,
    val bars: List<DayBar> = emptyList()
)

/**
 * 한 달치 일정에 lane(세로 칸) 배정 — 같은 일정은 며칠짜리든 매일 같은 lane 에 와야 막대가 가로로 이어진다.
 *   그리디 구간 패킹: 시작일 빠른 순 → 가장 위쪽 빈 lane(이전 일정 끝난 lane)에 배치. key→lane.
 */
internal fun packLanes(intervals: List<Triple<String, Long, Long>>): Map<String, Int> {
    val sorted = intervals.sortedWith(compareBy({ it.second }, { -(it.third - it.second) }))
    val laneEnds = ArrayList<Long>() // lane -> 그 lane 에 마지막으로 들어간 일정의 끝 ms
    val map = HashMap<String, Int>()
    for ((key, s, e) in sorted) {
        var lane = laneEnds.indexOfFirst { it < s }
        if (lane < 0) { laneEnds.add(e); lane = laneEnds.size - 1 } else laneEnds[lane] = e
        map[key] = lane
    }
    return map
}

/**
 * 보고 있는 달 anchor 기준 7×6=42 셀 생성.
 * 첫 셀 = 1일이 속한 주의 일요일. 마지막 셀 = 그로부터 +41일.
 * 다음 달 며칠 포함될 수 있음 → isCurrentMonth=false 로 회색 표시.
 */
internal fun buildCalendarCells(
    monthAnchor: Long,
    schedules: List<CustomerEntity>,
    simples: List<com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity>,
    todayStart: Long
): List<CalendarCell> {
    val cal = Calendar.getInstance().apply { timeInMillis = monthAnchor }
    val targetMonth = cal.get(Calendar.MONTH)
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) // 1=SUN..7=SAT
    cal.add(Calendar.DAY_OF_MONTH, -(firstDow - 1)) // 그 주 일요일로

    // ⚠️ lane 은 '보이는 42칸과 겹치는 시공'만으로 배정한다. (2026-09-14 사장님 신고)
    //   전체 이력(state.all)으로 배정하면 건이 쌓일수록 lane 번호가 계속 커지는데,
    //   칸 렌더러는 2줄(lane 0~1)까지만 그린다 → 그 위 lane 이 걸린 날은 막대가 통째로 사라졌다.
    //   창 단위로 배정하면 번호가 작게 유지되고, 42칸 × 전체목록 필터링도 안 하게 되어 더 가볍다.
    val windowStart = DateTimeUtils.startOfDay(cal.timeInMillis)
    val windowEnd = windowStart + 41 * DateTimeUtils.DAY_MS
    val visible = schedules.filter { c ->
        val s = c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } ?: return@filter false
        val e = s + (c.scheduledWorkDays.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS
        s <= windowEnd && e >= windowStart
    }
    // 간단 일정도 같은 42칸 창과 겹치는 것만. 여러 날이면 기간 전체로 겹침 판단. (v64, 2026-10-08)
    val visibleSimples = simples.filter { e ->
        val s = DateTimeUtils.startOfDay(e.dayStartMs)
        val end = s + (e.days.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS
        s <= windowEnd && end >= windowStart
    }
    // 시공+간단을 **한 lane 공간**에 함께 패킹 — 서로 안 겹치면 같은 줄을 나눠 쓰고, 여러 날은 가로로 이어진다.
    val intervals = ArrayList<Triple<String, Long, Long>>()
    for (c in visible) {
        val s = c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } ?: continue
        intervals += Triple(laneKeyOf(c), s, s + (c.scheduledWorkDays.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS)
    }
    for (e in visibleSimples) {
        val s = DateTimeUtils.startOfDay(e.dayStartMs)
        intervals += Triple("sm-${e.id}", s, s + (e.days.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS)
    }
    val laneMap = packLanes(intervals)
    val cells = ArrayList<CalendarCell>(42)
    repeat(42) {
        val dayStart = DateTimeUtils.startOfDay(cal.timeInMillis)
        // 여러 날 시공은 기간 내 모든 날에 막대 표시 (scheduledWorkDays).
        val daySchedules = visible.filter { jobCoversDay(it, dayStart) }
        val hasPast = daySchedules.isNotEmpty() && dayStart < todayStart
        val hasUp = daySchedules.isNotEmpty() && dayStart >= todayStart
        val bars = daySchedules.mapNotNull { c ->
            val s = c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } ?: return@mapNotNull null
            val e = s + (c.scheduledWorkDays.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS
            val seg = when {
                s == e -> BarSeg.SINGLE
                dayStart == s -> BarSeg.START
                dayStart == e -> BarSeg.END
                else -> BarSeg.MID
            }
            DayBar(
                lane = (laneMap[laneKeyOf(c)] ?: 0).coerceAtMost(CAL_MAX_LANE),
                seg = seg,
                past = dayStart < todayStart,
                // 글자는 첫날에만. 홈 띠에서 "동대문" 뽑을 때 쓰는 그 함수를 그대로 쓴다.
                label = if (seg == BarSeg.SINGLE || seg == BarSeg.START)
                    com.detailline.callfollowcrm.util.RegionName.shortRegion(c.address) else null
            )
        }
        // 간단 일정 띠 — 시공과 같은 lane 공간을 나눠 쓴다. 여러 날이면 START/MID/END 로 이어지고 첫날에 제목. (v64)
        val daySimpleList = visibleSimples.filter { e ->
            val s = DateTimeUtils.startOfDay(e.dayStartMs)
            val end = s + (e.days.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS
            dayStart in s..end
        }
        val simpleBars = daySimpleList.map { e ->
            val s = DateTimeUtils.startOfDay(e.dayStartMs)
            val end = s + (e.days.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS
            val seg = when {
                s == end -> BarSeg.SINGLE
                dayStart == s -> BarSeg.START
                dayStart == end -> BarSeg.END
                else -> BarSeg.MID
            }
            DayBar(
                lane = (laneMap["sm-${e.id}"] ?: 0).coerceAtMost(CAL_MAX_LANE),
                seg = seg,
                past = dayStart < todayStart,
                label = if (seg == BarSeg.SINGLE || seg == BarSeg.START) e.title else null,
                kind = BarKind.SIMPLE
            )
        }
        cells += CalendarCell(
            dayStartMs = dayStart,
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
            isCurrentMonth = cal.get(Calendar.MONTH) == targetMonth,
            isToday = dayStart == todayStart,
            scheduleCount = daySchedules.size,
            hasPastSchedule = hasPast,
            hasUpcomingSchedule = hasUp,
            bars = (bars + simpleBars).sortedBy { it.lane }
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return cells
}
