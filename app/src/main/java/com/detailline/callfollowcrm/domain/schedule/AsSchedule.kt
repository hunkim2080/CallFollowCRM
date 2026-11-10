package com.detailline.callfollowcrm.domain.schedule

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 🔧 **A/S 예약 날짜 셈** — 시공(scheduledWorkDate)과 **별개**다(무료·정산 무관, DB v43).
 *
 * "이 A/S 가 그 날을 덮는가" 를 **한 곳**에서 판정한다. 전엔 이 셈이 홈·일정·캘린더·알림에
 * 제각각 흩어져, **홈만 A/S 를 아예 안 봐서** 오늘 A/S 가 "오늘 일정"에 안 떴다. (2026-11-11 사장님)
 *
 * 입구:
 *   - 일정 탭: `ScheduleParts.asCoversDay` (이 함수를 부른다)
 *   - 홈 띠: `HomeViewModel.asToday`
 */
object AsSchedule {

    /** 이 A/S 예약이 dayStart(= startOfDay) 날을 포함하는가 — 여러 날 A/S(asScheduledDays) 고려. */
    fun coversDay(asScheduledDate: Long?, asScheduledDays: Int, dayStart: Long): Boolean {
        val start = asScheduledDate ?: return false
        val s = DateTimeUtils.startOfDay(start)
        val days = asScheduledDays.coerceAtLeast(1)
        val end = s + (days - 1) * DateTimeUtils.DAY_MS
        return dayStart in s..end
    }

    /** 고객 카드 버전 — 위 순수 셈에 위임. */
    fun coversDay(c: CustomerEntity, dayStart: Long): Boolean =
        coversDay(c.asScheduledDate, c.asScheduledDays, dayStart)
}
