package com.detailline.callfollowcrm.domain.recurring

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.RecurringMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정기문자 "보낼 때 됐어요" 계산 검증 (DB v25).
 *   회차 = 시공일 + k×intervalDays 중 today 이하 최근 1건. 로그 있으면 제외.
 */
class RecurringDueCalcTest {

    private val DAY = 24L * 60 * 60 * 1000
    private val today = 100_000L * DAY  // 임의 자정 정렬 기준값

    private fun customer(id: Long, scheduledDaysAgo: Int?, categoryId: Long? = null, name: String? = "김씨") =
        CustomerEntity(
            id = id,
            phoneNumber = "0101111$id",
            name = name,
            categoryId = categoryId,
            scheduledWorkDate = scheduledDaysAgo?.let { today - it * DAY },
            createdAt = 0L, updatedAt = 0L
        )

    private fun rule(id: Long, interval: Int, categoryId: Long? = null, enabled: Boolean = true) =
        RecurringMessageEntity(
            id = id, name = "한 달 점검", targetCategoryId = categoryId,
            intervalDays = interval, sendMinutes = 600,
            bodyTemplate = "{고객명}님 점검 도와드릴까요?", enabled = enabled,
            createdAt = 0L, updatedAt = 0L
        )

    @Test fun `시공 30일 후 - 30일 규칙 due`() {
        val due = RecurringDueCalc.computeDue(
            rules = listOf(rule(1, 30)),
            customers = listOf(customer(1, scheduledDaysAgo = 30)),
            loggedKeys = emptySet(),
            todayStartMs = today
        )
        assertEquals(1, due.size)
        assertEquals("김씨님 점검 도와드릴까요?", due[0].renderedBody)
    }

    @Test fun `시공 20일 후 - 30일 규칙 아직 아님`() {
        val due = RecurringDueCalc.computeDue(
            listOf(rule(1, 30)), listOf(customer(1, scheduledDaysAgo = 20)), emptySet(), today
        )
        assertTrue(due.isEmpty())
    }

    @Test fun `시공 65일 후 - 30일 규칙은 최근 회차(60일) 1건만`() {
        val due = RecurringDueCalc.computeDue(
            listOf(rule(1, 30)), listOf(customer(1, scheduledDaysAgo = 65)), emptySet(), today
        )
        assertEquals(1, due.size)
        assertEquals(today - 5 * DAY, due[0].occurrenceDayStartMs)  // 60일째 = 오늘-5일
    }

    @Test fun `이미 보낸 회차는 제외`() {
        val cust = customer(1, scheduledDaysAgo = 30)
        val occ = cust.scheduledWorkDate!! + 30 * DAY
        val due = RecurringDueCalc.computeDue(
            listOf(rule(1, 30)), listOf(cust),
            loggedKeys = setOf(Triple(1L, 1L, occ)),
            todayStartMs = today
        )
        assertTrue(due.isEmpty())
    }

    @Test fun `카테고리 타겟 - 불일치 고객 제외`() {
        val due = RecurringDueCalc.computeDue(
            listOf(rule(1, 30, categoryId = 7)),
            listOf(customer(1, scheduledDaysAgo = 30, categoryId = 9)),
            emptySet(), today
        )
        assertTrue(due.isEmpty())
    }

    @Test fun `비활성 규칙 제외`() {
        val due = RecurringDueCalc.computeDue(
            listOf(rule(1, 30, enabled = false)),
            listOf(customer(1, scheduledDaysAgo = 30)), emptySet(), today
        )
        assertTrue(due.isEmpty())
    }

    @Test fun `이름 없으면 고객님으로 치환`() {
        val due = RecurringDueCalc.computeDue(
            listOf(rule(1, 30)), listOf(customer(1, scheduledDaysAgo = 30, name = null)),
            emptySet(), today
        )
        assertEquals("고객님 점검 도와드릴까요?", due[0].renderedBody)
    }
}
