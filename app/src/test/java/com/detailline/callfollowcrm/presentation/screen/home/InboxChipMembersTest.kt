package com.detailline.callfollowcrm.presentation.screen.home

import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.domain.model.HandledStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ⏸️ 보류 손님은 **[보류] 한 칸에만** — 오늘 신규·시공 대기·잔금 대기에 겹쳐 뜨지 않는다. (2026-10-16 사장님)
 *   A/S 명단과 같은 규칙. 칩이 생기는 조건(inboxChipMembers)과 숫자·목록(HomeScreen)이 같은 잣대라야
 *   「칩은 떴는데 열면 빈 목록」이 안 난다(2026-10-14 실제).
 */
class InboxChipMembersTest {

    private val now = 1_760_500_000_000L
    private val 하루 = 86_400_000L
    private val today = now - (now % 하루)

    private fun item(c: CustomerEntity, newToday: Boolean = false) = HomeItem(
        record = CallRecordEntity(
            id = c.id, phoneNumber = c.phoneNumber, callType = HomeViewModel.CALL_TYPE_SMS_ONLY,
            duration = 0L, startedAt = null, endedAt = now,
            handledStatus = HandledStatus.SAVED.name, linkedCustomerId = c.id
        ),
        customer = c, isNewToday = newToday
    )

    private fun cust(
        id: Long, scheduled: Long? = null, onHoldAt: Long? = null, done: Long? = null
    ) = CustomerEntity(
        id = id, phoneNumber = "0101111${id.toString().padStart(4, '0')}", name = "손님$id",
        scheduledWorkDate = scheduled, onHoldAt = onHoldAt, workCompletedAt = done,
        depositAmount = 300_000L, depositPaidAt = now, createdAt = now, updatedAt = now
    )

    @Test
    fun `보류 손님은 보류 칩에만 — 오늘신규·시공대기·잔금대기에 안 겹친다`() {
        val hold = cust(1, scheduled = today + 하루 * 3, onHoldAt = now)
        val members = inboxChipMembers(listOf(item(hold, newToday = true)), dueIds = setOf(1L), todayStart = today)
        assertEquals(setOf("onhold"), members)
    }

    @Test
    fun `보류 아닌 예약 손님은 시공 대기`() {
        val wait = cust(2, scheduled = today + 하루 * 3)
        assertEquals(setOf("wait"), inboxChipMembers(listOf(item(wait)), emptySet(), today))
    }

    @Test
    fun `보류 찍혀도 끝난 손님은 보류가 아니다 — 종료 고객`() {
        val doneButFlagged = cust(3, onHoldAt = now - 하루 * 30, done = now)
        assertEquals(setOf("done"), inboxChipMembers(listOf(item(doneButFlagged)), emptySet(), today))
    }

    @Test
    fun `보류 손님이 없으면 보류 칩이 안 생긴다`() {
        val members = inboxChipMembers(listOf(item(cust(4, scheduled = today))), emptySet(), today)
        assertEquals(false, "onhold" in members)
    }
}
