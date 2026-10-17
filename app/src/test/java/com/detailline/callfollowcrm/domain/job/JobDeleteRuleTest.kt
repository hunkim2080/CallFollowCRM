package com.detailline.callfollowcrm.domain.job

import com.detailline.callfollowcrm.data.local.entity.JobEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🗑 어떤 건을 지워도 되나. (2026-10-17 사장님 실기 — 문자 주소로 잘못 생긴 2차를 지울 데가 없었다)
 */
class JobDeleteRuleTest {

    private fun job(
        date: Long? = null, address: String? = null, memo: String = "",
        total: Long? = null, deposit: Long? = null, balance: Long? = null,
        depositPaidAt: Long? = null, balancePaidAt: Long? = null,
        workCompletedAt: Long? = null, cancelledAt: Long? = null
    ) = JobEntity(
        id = 7L, customerId = 1L, scheduledWorkDate = date, scheduledWorkDays = 1,
        address = address, memo = memo,
        totalAmount = total, depositAmount = deposit, balanceAmount = balance,
        depositPaidAt = depositPaidAt, balancePaidAt = balancePaidAt,
        workCompletedAt = workCompletedAt, cancelledAt = cancelledAt,
        createdAt = 0L, updatedAt = 0L
    )

    @Test fun `사고 모양 - 주소만 든 날짜 미정 2차는 지울 수 있다`() {
        assertTrue(JobDeleteRule.canDelete(job(address = "안양시 박달동 한라 비발디 105동 1704호")))
    }

    @Test fun `완전히 빈 건·취소한 건은 지울 수 있다`() {
        assertTrue(JobDeleteRule.canDelete(job()))
        assertTrue(JobDeleteRule.canDelete(job(cancelledAt = 1L)))
        assertTrue(JobDeleteRule.canDelete(job(memo = "주차 2층")))
    }

    @Test fun `날짜가 있으면 못 지운다 - 취소부터`() {
        assertFalse(JobDeleteRule.canDelete(job(date = 1_000L)))
    }

    @Test fun `돈이 하나라도 적혀 있으면 못 지운다`() {
        assertFalse(JobDeleteRule.canDelete(job(total = 650_000L)))
        assertFalse(JobDeleteRule.canDelete(job(deposit = 100_000L)))
        assertFalse(JobDeleteRule.canDelete(job(balance = 550_000L)))
    }

    @Test fun `받음 도장이 있으면 못 지운다`() {
        assertFalse(JobDeleteRule.canDelete(job(depositPaidAt = 1L)))
        assertFalse(JobDeleteRule.canDelete(job(balancePaidAt = 1L)))
    }
}
