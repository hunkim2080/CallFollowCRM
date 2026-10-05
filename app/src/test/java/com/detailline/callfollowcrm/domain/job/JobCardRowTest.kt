package com.detailline.callfollowcrm.domain.job

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JobCardRow — 한 건을 '그 건의 얼굴' 카드 사본으로. 돈·완료는 **반드시 그 건** 것.
 *   (2026-10-05 사장님 버그: 1차 완납 후 2차 잡으면 2차에 1차 돈·완납이 묻어남.)
 */
class JobCardRowTest {

    private val day1 = 1_700_000_000_000L
    private val day7 = day1 + 7L * 24 * 60 * 60 * 1000

    /** 고객 카드엔 1차 돈·완납·완료가 남아 있다(미러가 돈을 안 옮기므로). */
    private fun cardWith1stMoney() = CustomerEntity(
        id = 1L,
        phoneNumber = "01012345678",
        name = "김사장",
        address = "서울시 강남구 1차주소",
        scheduledWorkDate = day7,          // 미러가 날짜만 2차로 바꿔둔 상태
        totalAmount = 1_000_000L,
        depositAmount = 300_000L,
        depositPaidAt = day1,
        balanceAmount = 700_000L,
        balancePaidAt = day1,              // 1차 전액완납
        workCompletedAt = day1,            // 1차 완료
        createdAt = 0L,
        updatedAt = 0L
    )

    /** 2차 = 아직 아무 돈도 안 적음, 완료도 아님, 미래 날짜. */
    private fun blankSecondJob() = JobEntity(
        id = 20L,
        customerId = 1L,
        scheduledWorkDate = day7,
        scheduledWorkDays = 1,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `돈과 완료는 그 건 것 - 카드의 1차 돈이 묻어나지 않는다`() {
        val row = JobCardRow.rowOf(cardWith1stMoney(), blankSecondJob())
        assertNull("2차 총금액은 비어야 함", row.totalAmount)
        assertNull("2차 계약금은 비어야 함", row.depositAmount)
        assertNull("2차 계약금 받은시각 비어야 함", row.depositPaidAt)
        assertNull("2차 잔금 비어야 함", row.balanceAmount)
        assertNull("2차 잔금 받은시각(완납) 비어야 함", row.balancePaidAt)
        assertNull("2차 완료시각 비어야 함", row.workCompletedAt)
        assertEquals("날짜는 2차", day7, row.scheduledWorkDate)
    }

    @Test
    fun `건에 돈이 있으면 그 금액을 싣는다`() {
        val job = blankSecondJob().copy(totalAmount = 800_000L, depositAmount = 200_000L)
        val row = JobCardRow.rowOf(cardWith1stMoney(), job)
        assertEquals(800_000L, row.totalAmount)
        assertEquals(200_000L, row.depositAmount)
    }

    @Test
    fun `주소는 건에 없으면 카드로 폴백, 있으면 건 주소`() {
        val noAddr = JobCardRow.rowOf(cardWith1stMoney(), blankSecondJob())
        assertEquals("서울시 강남구 1차주소", noAddr.address)
        val withAddr = JobCardRow.rowOf(cardWith1stMoney(), blankSecondJob().copy(address = "분당구 2차주소"))
        assertEquals("분당구 2차주소", withAddr.address)
    }

    @Test
    fun `시공 일수는 최소 1`() {
        val row = JobCardRow.rowOf(cardWith1stMoney(), blankSecondJob().copy(scheduledWorkDays = 0))
        assertEquals(1, row.scheduledWorkDays)
    }
}
