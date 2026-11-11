package com.detailline.callfollowcrm.domain.schedule

import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 🏗️ **시공 기간을 ms 범위로** — [시작 날 자정 .. 마지막 날 끝(23:59:59.999)].
 *
 * 「시공한 날 사진만」 선택창(MediaStore DATE_TAKEN 쿼리)이 이 범위로 사진을 거른다.
 * 「그 날을 덮나」(jobCoversDay)와 **같은 기간 셈** 이 한 곳에서 나온다(두 벌 금지 §12).
 */
object WorkDateRange {

    /** scheduledWorkDate(시공일) ~ + (days)일. null = 시공일 없음 → 거를 날이 없다. */
    fun of(scheduledWorkDate: Long?, scheduledWorkDays: Int): LongRange? {
        val start = scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } ?: return null
        val days = scheduledWorkDays.coerceAtLeast(1)
        val endInclusive = start + days * DateTimeUtils.DAY_MS - 1   // 마지막 날 23:59:59.999
        return start..endInclusive
    }

    /** 이 ms(촬영 시각)가 시공 기간 안인가. 범위 없으면(시공일 없음) 항상 false. */
    fun covers(scheduledWorkDate: Long?, scheduledWorkDays: Int, ms: Long): Boolean =
        of(scheduledWorkDate, scheduledWorkDays)?.contains(ms) == true
}
