package com.detailline.callfollowcrm.domain.job

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「완료」와 「완납」은 다른 말이다. (2026-10-18 사장님 용어 확정)
 *   · 완료 = **작업이 끝남** (돈은 남았을 수도)
 *   · 완납 = 작업도 끝나고 **돈까지 다 받음**
 *
 * 전엔 고객 목록 딱지만 「완료 = 돈까지」였고 건 카드·검색은 「완료 = 작업만」이라
 * 같은 손님이 카드엔 「완료」, 목록엔 「잔금미수」로 동시에 떠 "돈 받았나?"가 헷갈렸다.
 * 판정은 [Completion] 한 곳 — 이 시험이 그 규칙을 붙잡아 둔다.
 */
class CompletionTest {

    private val now = 1_758_000_000_000L

    private fun job(
        total: Long? = null, deposit: Long? = null,
        depositPaidAt: Long? = null, balancePaidAt: Long? = null, workCompletedAt: Long? = null
    ) = JobEntity(
        id = 1L, customerId = 1L, scheduledWorkDate = now - 86_400_000L,
        totalAmount = total, depositAmount = deposit, depositPaidAt = depositPaidAt,
        balanceAmount = null, balancePaidAt = balancePaidAt, workCompletedAt = workCompletedAt,
        createdAt = now, updatedAt = now
    )

    private fun customer(
        total: Long? = null, deposit: Long? = null,
        depositPaidAt: Long? = null, balancePaidAt: Long? = null, workCompletedAt: Long? = null
    ) = CustomerEntity(
        phoneNumber = "01011112222",
        totalAmount = total, depositAmount = deposit, depositPaidAt = depositPaidAt,
        balancePaidAt = balancePaidAt, workCompletedAt = workCompletedAt,
        createdAt = now, updatedAt = now
    )

    @Test
    fun `아무 도장도 없으면 진행 중`() {
        assertEquals(CompletionStage.IN_PROGRESS, Completion.stageOf(job(total = 1_000_000L)))
        assertFalse(Completion.isDone(job(total = 1_000_000L)))
    }

    @Test
    fun `완료 도장만 있고 잔금이 남았으면 완료 - 완납이 아니다`() {
        val j = job(total = 1_000_000L, deposit = 200_000L, depositPaidAt = now, workCompletedAt = now)
        assertEquals(CompletionStage.DONE, Completion.stageOf(j))
        assertEquals("완료", Completion.stageOf(j).label)
    }

    @Test
    fun `잔금까지 받았으면 완납 - 완료 도장이 없어도 끝난 것`() {
        // 잔금 받으면 = 완료 (2026-08-18 사장님). 돈까지 받았으니 완납.
        val j = job(total = 1_000_000L, deposit = 200_000L, balancePaidAt = now)
        assertEquals(CompletionStage.PAID_OFF, Completion.stageOf(j))
        assertEquals("완납", Completion.stageOf(j).label)
        assertTrue(Completion.stageOf(j).isDone)
    }

    @Test
    fun `돈이 하나도 없는 건의 잔금 도장은 완료로 안 친다 - 0원 완납은 없다`() {
        // 2026-10-15 사장님 "완납 안 눌렀는데 완료로 뜸" — 카드에서 번진 도장만으로 완료가 되던 길.
        val j = job(balancePaidAt = now)
        assertEquals(CompletionStage.IN_PROGRESS, Completion.stageOf(j))
    }

    @Test
    fun `완료 도장은 돈이 없어도 완료다 - 금액 미입력`() {
        assertEquals(CompletionStage.DONE, Completion.stageOf(job(workCompletedAt = now)))
    }

    @Test
    fun `계약금이 총액 전부였고 완료했으면 완납`() {
        val j = job(total = 500_000L, deposit = 500_000L, depositPaidAt = now, workCompletedAt = now)
        assertEquals(CompletionStage.PAID_OFF, Completion.stageOf(j))
    }

    @Test
    fun `전액을 미리 받았어도 작업이 안 끝났으면 진행 중`() {
        // 돈만 보고 「끝났다」고 하면 오늘의 현장·시공 대기에서 그 집이 사라진다.
        val j = job(total = 500_000L, deposit = 500_000L, depositPaidAt = now)
        assertEquals(CompletionStage.IN_PROGRESS, Completion.stageOf(j))
    }

    @Test
    fun `고객 카드와 건은 같은 자다`() {
        val stages = listOf(
            Completion.stageOf(customer(total = 1_000_000L, workCompletedAt = now)) to
                Completion.stageOf(job(total = 1_000_000L, workCompletedAt = now)),
            Completion.stageOf(customer(total = 1_000_000L, balancePaidAt = now)) to
                Completion.stageOf(job(total = 1_000_000L, balancePaidAt = now)),
            Completion.stageOf(customer(balancePaidAt = now)) to Completion.stageOf(job(balancePaidAt = now)),
            Completion.stageOf(customer()) to Completion.stageOf(job())
        )
        stages.forEach { (c, j) -> assertEquals(c, j) }
        // CustomerEntity.isWorkDone 도 이 자를 쓴다.
        assertTrue(customer(total = 1_000_000L, balancePaidAt = now).isWorkDone)
        assertFalse(customer(balancePaidAt = now).isWorkDone)
    }
}
