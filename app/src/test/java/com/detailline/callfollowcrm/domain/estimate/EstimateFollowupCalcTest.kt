package com.detailline.callfollowcrm.domain.estimate

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 견적 회신 리마인드 계산 검증. */
class EstimateFollowupCalcTest {

    private val DAY = 24L * 60 * 60 * 1000
    private val today = 100_000L * DAY

    private fun customer(id: Long, scheduled: Boolean = false, name: String? = "이씨") =
        CustomerEntity(
            id = id, phoneNumber = "0103333$id", name = name,
            scheduledWorkDate = if (scheduled) today + 5 * DAY else null,
            createdAt = 0L, updatedAt = 0L
        )

    @Test fun `견적 3일 전 - 시공일 없음 - 리마인드`() {
        val r = EstimateFollowupCalc.compute(
            mapOf(1L to today - 3 * DAY), listOf(customer(1)), emptySet(), today
        )
        assertEquals(1, r.size)
        assertEquals(3, r[0].daysSince)
    }

    @Test fun `견적 1일 전 - 아직 아님(기본 2일)`() {
        val r = EstimateFollowupCalc.compute(
            mapOf(1L to today - 1 * DAY), listOf(customer(1)), emptySet(), today
        )
        assertTrue(r.isEmpty())
    }

    @Test fun `시공일 잡힌 고객 - 제외`() {
        val r = EstimateFollowupCalc.compute(
            mapOf(1L to today - 5 * DAY), listOf(customer(1, scheduled = true)), emptySet(), today
        )
        assertTrue(r.isEmpty())
    }

    @Test fun `이미 처리한 회차 - 제외`() {
        val estDay = today - 3 * DAY
        val key = Triple(EstimateFollowupCalc.RULE_ESTIMATE, 1L, estDay)
        val r = EstimateFollowupCalc.compute(
            mapOf(1L to estDay), listOf(customer(1)), setOf(key), today
        )
        assertTrue(r.isEmpty())
    }

    @Test fun `고객 없는 견적 기록 - 무시`() {
        val r = EstimateFollowupCalc.compute(
            mapOf(99L to today - 3 * DAY), listOf(customer(1)), emptySet(), today
        )
        assertTrue(r.isEmpty())
    }

    @Test fun `여러 명 - 오래된 순 정렬`() {
        val r = EstimateFollowupCalc.compute(
            mapOf(1L to today - 2 * DAY, 2L to today - 6 * DAY),
            listOf(customer(1), customer(2)), emptySet(), today
        )
        assertEquals(2, r.size)
        assertEquals(2L, r[0].customerId)  // 6일 전이 먼저
        assertEquals(1L, r[1].customerId)
    }
}
