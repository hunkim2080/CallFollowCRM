package com.detailline.callfollowcrm.domain.schedule

import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 달력에서 **시작일 → 끝날** 두 번 탭으로 기간을 고르는 셈. (프로토 .pick)
 *
 * 한 번 누르면 시작일, 더 **나중 날**을 한 번 더 누르면 끝날.
 * 그 외(이미 기간이 잡혔거나 · 더 이른/같은 날을 누름)엔 **다시 시작일**부터.
 *
 * 전엔 이 로직이 화면마다 복붙돼 있었다(접수서·시공기간·A/S·간단일정).
 * 공용 달력 [WorkRangeCalendar] 와 짝. 날짜는 모두 startOfDay(ms) 기준.
 */
object RangePick {

    /** 한 번 탭 → 새 (시작, 끝). */
    fun tap(start: Long?, end: Long?, day: Long): Pair<Long?, Long?> =
        if (start == null || end != null || day <= start) day to null
        else start to day

    /** 시작~끝이 며칠짜리인지. 시작 없으면 0, 끝 없거나 같으면 1. */
    fun daysOf(start: Long?, end: Long?): Int = when {
        start == null -> 0
        end == null || end <= start -> 1
        else -> ((end - start) / DateTimeUtils.DAY_MS).toInt() + 1
    }
}
