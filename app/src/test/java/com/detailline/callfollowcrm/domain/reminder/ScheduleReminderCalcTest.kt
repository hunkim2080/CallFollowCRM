package com.detailline.callfollowcrm.domain.reminder

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시공 D-1 / 도착 안내 리마인드 계산 검증. */
class ScheduleReminderCalcTest {

    private val DAY = 24L * 60 * 60 * 1000
    private val today = 100_000L * DAY

    private fun customer(id: Long, scheduledDayOffset: Int?, name: String? = "박씨") =
        CustomerEntity(
            id = id, phoneNumber = "0102222$id", name = name,
            scheduledWorkDate = scheduledDayOffset?.let { today + it * DAY },
            createdAt = 0L, updatedAt = 0L
        )

    @Test fun `오늘 시공 - 도착 안내`() {
        val r = ScheduleReminderCalc.compute(listOf(customer(1, 0)), emptySet(), today)
        assertEquals(1, r.size)
        assertEquals(ReminderKind.ARRIVAL, r[0].kind)
    }

    @Test fun `내일 시공 - 전날 안내`() {
        val r = ScheduleReminderCalc.compute(listOf(customer(1, 1)), emptySet(), today)
        assertEquals(1, r.size)
        assertEquals(ReminderKind.D1, r[0].kind)
    }

    @Test fun `모레 시공 - 아직 아님`() {
        val r = ScheduleReminderCalc.compute(listOf(customer(1, 2)), emptySet(), today)
        assertTrue(r.isEmpty())
    }

    @Test fun `어제 시공 - 제외`() {
        val r = ScheduleReminderCalc.compute(listOf(customer(1, -1)), emptySet(), today)
        assertTrue(r.isEmpty())
    }

    @Test fun `이미 보낸 도착 안내는 제외`() {
        val c = customer(1, 0)
        val key = Triple(ScheduleReminderCalc.RULE_ARRIVAL, 1L, c.scheduledWorkDate!!)
        val r = ScheduleReminderCalc.compute(listOf(c), setOf(key), today)
        assertTrue(r.isEmpty())
    }

    @Test fun `D1 과 ARRIVAL 로그는 별개`() {
        // 오늘 시공 고객을 D1 로 로그해도 ARRIVAL 은 여전히 떠야 함 (sentinel 분리).
        val c = customer(1, 0)
        val d1key = Triple(ScheduleReminderCalc.RULE_D1, 1L, c.scheduledWorkDate!!)
        val r = ScheduleReminderCalc.compute(listOf(c), setOf(d1key), today)
        assertEquals(1, r.size)
        assertEquals(ReminderKind.ARRIVAL, r[0].kind)
    }

    @Test fun `오늘+내일 시공 - 도착 먼저, 전날 다음`() {
        val r = ScheduleReminderCalc.compute(
            listOf(customer(2, 1), customer(1, 0)), emptySet(), today
        )
        assertEquals(2, r.size)
        assertEquals(ReminderKind.ARRIVAL, r[0].kind)
        assertEquals(ReminderKind.D1, r[1].kind)
    }
}
