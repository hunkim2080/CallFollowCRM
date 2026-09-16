package com.detailline.callfollowcrm.domain.settlement

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정산을 **건(件)별로** 셀 수 있는가. (2026-09-17 재방문 Stage B)
 *
 * 한 고객이 1차·2차·3차를 주면 잔금도 건마다 따로 남는다.
 * 고객 기준으로만 세면 "2차는 다 받았는데 1차 잔금이 남은" 상황을 못 본다 —
 * 대표 건이 2차로 바뀌는 순간 **1차 미수가 조용히 사라진다.** 돈이 새는 쪽이라 테스트로 고정한다.
 *
 * 그리고 규칙이 두 벌이 되면 화면과 알림의 금액이 갈라진다 —
 * 그래서 고객이든 건이든 **같은 답**이 나와야 한다는 것도 같이 못 박는다.
 */
class JobSettlementTest {

    private fun job(
        id: Long = 1L,
        customerId: Long = 7L,
        total: Long? = 1_000_000L,
        deposit: Long? = 100_000L,
        depositPaidAt: Long? = 1L,
        balance: Long? = null,
        balancePaidAt: Long? = null,
    ) = JobEntity(
        id = id, customerId = customerId,
        scheduledWorkDate = 10L, totalAmount = total,
        depositAmount = deposit, depositPaidAt = depositPaidAt,
        balanceAmount = balance, balancePaidAt = balancePaidAt,
        createdAt = 1L, updatedAt = 1L
    )

    private fun customer(
        total: Long? = 1_000_000L,
        deposit: Long? = 100_000L,
        depositPaidAt: Long? = 1L,
        balance: Long? = null,
        balancePaidAt: Long? = null,
    ) = CustomerEntity(
        id = 7L, phoneNumber = "01011112222",
        totalAmount = total, depositAmount = deposit, depositPaidAt = depositPaidAt,
        balanceAmount = balance, balancePaidAt = balancePaidAt,
        createdAt = 1L, updatedAt = 1L
    )

    @Test
    fun `건 하나의 미수를 센다`() {
        val r = SettlementCalc.rowOf(job())
        assertEquals(1_000_000L, r.total)
        assertEquals(100_000L, r.received)      // 계약금만 받음
        assertEquals(900_000L, r.outstanding)
        assertFalse(r.isPaidOff)
    }

    @Test
    fun `잔금까지 받으면 완납`() {
        val r = SettlementCalc.rowOf(job(balancePaidAt = 2L))
        assertEquals(1_000_000L, r.received)
        assertEquals(0L, r.outstanding)
        assertTrue(r.isPaidOff)
    }

    @Test
    fun `1차 잔금이 남아 있으면 2차를 다 받아도 남아 있다`() {
        // 예전엔 대표 건이 2차로 바뀌면서 1차 미수가 통째로 사라졌다
        val first = SettlementCalc.rowOf(job(id = 1, total = 1_000_000L))                      // 잔금 90만 미수
        val second = SettlementCalc.rowOf(job(id = 2, total = 500_000L, balancePaidAt = 3L))   // 완납
        assertEquals(900_000L, first.outstanding)
        assertEquals(0L, second.outstanding)
        assertEquals("두 건을 합친 미수", 900_000L, first.outstanding + second.outstanding)
    }

    @Test
    fun `건과 고객이 같은 답을 낸다`() {
        // 규칙이 두 벌이 되면 화면과 알림의 미수 금액이 갈라진다
        val cases = listOf(
            Triple(1_000_000L, 100_000L, null as Long?),
            Triple(150_000L, 350_000L, null),          // 계약금이 총액보다 큰 옛 데이터
            Triple(0L, 0L, null)
        )
        for ((total, deposit, balPaid) in cases) {
            val j = SettlementCalc.rowOf(job(total = total, deposit = deposit, balancePaidAt = balPaid))
            val c = SettlementCalc.rowOf(customer(total = total, deposit = deposit, balancePaidAt = balPaid))
            assertEquals("총액", c.total, j.total)
            assertEquals("받은 돈", c.received, j.received)
            assertEquals("미수", c.outstanding, j.outstanding)
        }
    }

    @Test
    fun `총액이 없으면 저장된 잔금을 쓴다`() {
        // 총액 없이 계약금·잔금만 박힌 옛 데이터
        val r = SettlementCalc.rowOf(job(total = null, deposit = 100_000L, balance = 400_000L))
        assertEquals(500_000L, r.total)
        assertEquals(100_000L, r.received)
        assertEquals(400_000L, r.outstanding)
    }

    @Test
    fun `계약금이 총액보다 커도 잔금이 음수가 되지 않는다`() {
        val r = SettlementCalc.rowOf(job(total = 150_000L, deposit = 350_000L))
        assertEquals(0L, r.balanceAmount)
        assertTrue(r.outstanding >= 0L)
    }
}
