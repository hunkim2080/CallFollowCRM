package com.detailline.callfollowcrm.domain.settlement

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정산 미수 계산 검증 (정산 Phase 1).
 *
 * 규칙: total = totalAmount ?: (deposit + balance).
 *       balance = balanceAmount ?: (total − deposit).
 *       received = 받음 표시된 계약금/잔금 합. outstanding = total − received (>=0).
 */
class SettlementCalcTest {

    private fun customer(
        total: Long? = null,
        deposit: Long? = null,
        depositPaid: Boolean = false,
        balance: Long? = null,
        balancePaid: Boolean = false,
        scheduled: Long? = null,
        completedAt: Long? = null
    ) = CustomerEntity(
        id = 1,
        phoneNumber = "01012345678",
        depositAmount = deposit,
        depositPaidAt = if (depositPaid) 1_000L else null,
        balanceAmount = balance,
        balancePaidAt = if (balancePaid) 2_000L else null,
        totalAmount = total,
        scheduledWorkDate = scheduled,
        workCompletedAt = completedAt,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test fun `계약금만 받음 - 잔금 미수`() {
        val r = SettlementCalc.rowOf(
            customer(total = 1_000_000, deposit = 300_000, depositPaid = true, balance = 700_000)
        )
        assertEquals(1_000_000, r.total)
        assertEquals(300_000, r.received)
        assertEquals(700_000, r.outstanding)
        assertFalse(r.isPaidOff)
    }

    @Test fun `계약금 + 잔금 모두 받음 - 완납`() {
        val r = SettlementCalc.rowOf(
            customer(total = 1_000_000, deposit = 300_000, depositPaid = true, balance = 700_000, balancePaid = true)
        )
        assertEquals(1_000_000, r.received)
        assertEquals(0, r.outstanding)
        assertTrue(r.isPaidOff)
    }

    @Test fun `총액만 입력 - 아무것도 안받음`() {
        val r = SettlementCalc.rowOf(customer(total = 500_000))
        assertEquals(500_000, r.total)
        assertEquals(0, r.received)
        assertEquals(500_000, r.outstanding)
        assertFalse(r.isPaidOff)
    }

    @Test fun `잔금 금액 미입력시 총액-계약금으로 추정`() {
        val r = SettlementCalc.rowOf(customer(total = 1_000_000, deposit = 400_000))
        assertEquals(600_000, r.balanceAmount)
        assertEquals(1_000_000, r.total)
    }

    @Test fun `계약금만 박혀있고 받음 - 총액 없으면 그게 완납`() {
        val r = SettlementCalc.rowOf(customer(deposit = 200_000, depositPaid = true))
        assertEquals(200_000, r.total)
        assertEquals(200_000, r.received)
        assertEquals(0, r.outstanding)
        assertTrue(r.isPaidOff)
    }

    @Test fun `돈 정보 전혀 없으면 정산 대상 아님`() {
        assertFalse(SettlementCalc.hasMoney(customer()))
        assertTrue(SettlementCalc.hasMoney(customer(total = 100_000)))
        assertTrue(SettlementCalc.hasMoney(customer(deposit = 100_000)))
    }

    @Test fun `받은게 총액 초과해도 미수는 음수 안됨`() {
        // 총액 100k 인데 계약금 300k 받음으로 박힘 (이상 입력) → outstanding 0.
        val r = SettlementCalc.rowOf(
            customer(total = 100_000, deposit = 300_000, depositPaid = true)
        )
        assertEquals(0, r.outstanding)
        assertTrue(r.isPaidOff)
    }

    // ── 미수 경과일(상담함 카드용) — 사장님 결정 2026-06-23 ──
    private val DAY = com.detailline.callfollowcrm.util.DateTimeUtils.DAY_MS
    private val today = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(1_700_000_000_000L)

    @Test fun `미수 있고 시공 어제면 1일째`() {
        val c = customer(total = 1_000_000, deposit = 300_000, depositPaid = true, scheduled = today - DAY)
        assertEquals(1, SettlementCalc.overdueDays(c, today))
    }

    @Test fun `미수 있어도 시공 오늘이면 아직 안 지남`() {
        val c = customer(total = 1_000_000, deposit = 300_000, depositPaid = true, scheduled = today)
        assertEquals(null, SettlementCalc.overdueDays(c, today))
    }

    @Test fun `시공 미래면 null`() {
        val c = customer(total = 1_000_000, deposit = 300_000, depositPaid = true, scheduled = today + DAY)
        assertEquals(null, SettlementCalc.overdueDays(c, today))
    }

    @Test fun `완납이면 시공 지났어도 null`() {
        val c = customer(
            total = 1_000_000, deposit = 300_000, depositPaid = true,
            balance = 700_000, balancePaid = true, scheduled = today - 5 * DAY
        )
        assertEquals(null, SettlementCalc.overdueDays(c, today))
    }

    @Test fun `시공일도 완료일도 없으면 null`() {
        val c = customer(total = 1_000_000, deposit = 300_000, depositPaid = true)
        assertEquals(null, SettlementCalc.overdueDays(c, today))
    }

    @Test fun `완료일이 시공 예약일보다 우선`() {
        // 예약일은 10일 전이지만 실제 완료일이 2일 전 → 2일째로 계산.
        val c = customer(
            total = 1_000_000, deposit = 300_000, depositPaid = true,
            scheduled = today - 10 * DAY, completedAt = today - 2 * DAY
        )
        assertEquals(2, SettlementCalc.overdueDays(c, today))
    }
}
