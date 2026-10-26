package com.detailline.callfollowcrm.domain.job

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import com.detailline.callfollowcrm.domain.settlement.SettlementCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🩹 카드 → 대표 건 돈 치유 규칙. (2026-10-15 사장님 "완납버튼 누르지 않았는데도 완료처리 되는 건 버그인듯?")
 *   실제 모양: 고객 카드 = 200만 · 완납, 건 = 돈 없음(완료만) → 카드가 「금액 아직 입력 안 함」 + 「완료」.
 */
class JobMoneyBackfillTest {

    private val paidAt = 1_760_000_000_000L

    private fun job(
        total: Long? = null, deposit: Long? = null, balance: Long? = null,
        depositPaidAt: Long? = null, balancePaidAt: Long? = null, workCompletedAt: Long? = null
    ) = JobEntity(
        id = 10L, customerId = 1L, scheduledWorkDate = 1_000L, scheduledWorkDays = 1,
        totalAmount = total, depositAmount = deposit, balanceAmount = balance,
        depositPaidAt = depositPaidAt, balancePaidAt = balancePaidAt, workCompletedAt = workCompletedAt,
        createdAt = 0L, updatedAt = 0L
    )

    private fun card(
        total: Long? = null, deposit: Long? = null, balance: Long? = null,
        depositPaidAt: Long? = null, balancePaidAt: Long? = null, workCompletedAt: Long? = null
    ) = CustomerEntity(
        id = 1L, phoneNumber = "01012345678",
        totalAmount = total, depositAmount = deposit, balanceAmount = balance,
        depositPaidAt = depositPaidAt, balancePaidAt = balancePaidAt, workCompletedAt = workCompletedAt,
        createdAt = 0L, updatedAt = 0L
    )

    @Test fun `사장님 신고 모양 - 카드 200만·완납, 건은 완료만 → 총액까지 건으로 와서 모순이 없다`() {
        val out = JobMoneyBackfill.merge(
            rep = job(workCompletedAt = paidAt),
            c = card(total = 2_000_000, balancePaidAt = paidAt, workCompletedAt = paidAt)
        )
        assertNotNull(out)
        assertEquals(2_000_000L, out!!.totalAmount)       // ← 전엔 안 끌어와 「금액 미입력」이었다
        assertEquals(paidAt, out.balancePaidAt)
        val settle = SettlementCalc.rowOf(out)
        assertTrue("건 기준으로도 완납이어야 한다", settle.isPaidOff)
        assertEquals(2_000_000L, settle.total)
    }

    @Test fun `건이 비어 있으면 카드 것을 통째로`() {
        val out = JobMoneyBackfill.merge(
            rep = job(),
            c = card(total = 550_000, deposit = 100_000, depositPaidAt = paidAt, balance = 450_000)
        )
        assertNotNull(out)
        assertEquals(550_000L, out!!.totalAmount)
        assertEquals(100_000L, out.depositAmount)
        assertEquals(paidAt, out.depositPaidAt)
        assertNull(out.balancePaidAt)
        assertNull(out.workCompletedAt)
    }

    @Test fun `전액 완납 = 완료 - 완료일 없으면 잔금 받은 날로`() {
        val out = JobMoneyBackfill.merge(
            rep = job(total = 550_000, deposit = 100_000, balance = 450_000),
            c = card(total = 550_000, deposit = 100_000, balance = 450_000, balancePaidAt = paidAt)
        )
        assertNotNull(out)
        assertEquals(paidAt, out!!.balancePaidAt)
        assertEquals(paidAt, out.workCompletedAt)
    }

    @Test fun `스테일 가드 - 건 총액과 카드 총액이 다르면 아무것도 안 끌어온다`() {
        val out = JobMoneyBackfill.merge(
            rep = job(total = 1_500_000),
            c = card(total = 2_000_000, balancePaidAt = paidAt)
        )
        assertNull("다른 건(또는 옛) 돈의 완납이 이 건에 번지면 안 된다", out)
    }

    @Test fun `0원 완납 금지 - 돈이 어디에도 없으면 받음은 안 만들고 완료만 옮긴다`() {
        val out = JobMoneyBackfill.merge(
            rep = job(),
            c = card(balancePaidAt = paidAt, workCompletedAt = paidAt)
        )
        assertNotNull(out)
        assertNull("돈 없는 받음 도장은 뜻이 없다", out!!.balancePaidAt)
        assertEquals(paidAt, out.workCompletedAt)
        // 건 기준 완납 아님 → 카드가 「전액 완납」을 그리지 않는다.
        assertTrue(!SettlementCalc.rowOf(out).isPaidOff)
    }

    @Test fun `건에 이미 다 있으면 손대지 않는다(멱등)`() {
        val full = job(total = 550_000, deposit = 100_000, balance = 450_000, depositPaidAt = paidAt, balancePaidAt = paidAt, workCompletedAt = paidAt)
        assertNull(JobMoneyBackfill.merge(full, card(total = 550_000, balancePaidAt = paidAt)))
        assertNull(JobMoneyBackfill.merge(job(total = 550_000), card(total = 550_000)))   // 받음이 카드에 없으면 안 건드림
        assertNull(JobMoneyBackfill.merge(job(), card()))                                  // 둘 다 빈 카드
    }

    @Test fun `건에 돈은 있고 받음만 카드에 - 받음·빈 액수만 채운다(2026-10-12 사장님 동남)`() {
        val out = JobMoneyBackfill.merge(
            rep = job(total = 550_000, deposit = 100_000),
            c = card(total = 550_000, deposit = 100_000, balance = 450_000, depositPaidAt = paidAt)
        )
        assertNotNull(out)
        assertEquals(paidAt, out!!.depositPaidAt)
        assertEquals(450_000L, out.balanceAmount)
        assertNull("계약금만 받은 건 완료가 아니다", out.workCompletedAt)
        assertNull(out.balancePaidAt)
    }

    @Test fun `B1 - 대표 건이 비었는데 다른 건에 돈이 있으면 카드 돈을 안 끌어온다`() {
        // 1차(50만·완납·완료) 지나고 빈 2차가 대표가 됐다. 카드엔 아직 1차 돈이 스테일로 남았다.
        //   othersHaveMoney=true → 그 돈을 빈 2차로 끌어올리면 미래가 완납·완료로 뜬다. 막는다.
        val out = JobMoneyBackfill.merge(
            rep = job(),
            c = card(total = 500_000, deposit = 100_000, balance = 400_000, balancePaidAt = paidAt, workCompletedAt = paidAt),
            othersHaveMoney = true
        )
        assertNull("지난 1차 돈·완납이 빈 미래 2차로 묻어나면 안 된다 (B1)", out)
    }

    @Test fun `B1 - 다른 건에 돈이 없으면(진짜 카드전용) 그대로 끌어온다`() {
        // 레거시: 카드에만 돈이 있고 어느 건에도 없다 → 빈 대표 건으로 올려 보호(기존 동작 유지).
        val out = JobMoneyBackfill.merge(
            rep = job(),
            c = card(total = 500_000, deposit = 100_000, balance = 400_000, balancePaidAt = paidAt, workCompletedAt = paidAt),
            othersHaveMoney = false
        )
        assertNotNull("카드전용 돈은 여전히 대표 건으로 올려야 한다", out)
        assertEquals(500_000L, out!!.totalAmount)
        assertEquals(paidAt, out.balancePaidAt)
    }
}
