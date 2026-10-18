package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CustomerDao
import com.detailline.callfollowcrm.data.local.dao.JobDao
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import com.detailline.callfollowcrm.presentation.component.customerStatusOf
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking

/**
 * 🔒 **장부 검사** — 건 전표에 '받음/완료'를 찍으면 **고객 카드(요약)·배지도 같이** 바뀌는가.
 *   (2026-10-13 사장님 '완납인데 잔금미수로 뜸' 버그 + 뿌리뽑기)
 *
 *   뿌리: JobRepository 의 돈·완료 처리 함수(setBalancePaid/setDepositPaid/setWorkCompleted/updateMoney)가
 *   **안에서 recomputeMirror 로 고객 카드까지** 찍는다(= 도장 기계 한 대). 누가 그 미러 호출을 지우면
 *   **이 테스트가 깨진다** → 배포 전에 빌드가 멈춘다. (버튼마다 기억하는 관례로 돌아가면 또 샌다)
 *
 *   배지(customerStatusOf, 상담함·챗)와 고객정보(건)가 **같은 데이터로 같은 상태**를 말하는지까지 본다.
 */
class JobMoneyMirrorStatusTest {

    private val now = 1_760_000_000_000L
    private val today = DateTimeUtils.startOfDay(now)
    private val past = today - 2 * DateTimeUtils.DAY_MS      // 지난 시공일(완료 대상)
    private val future = today + 7 * DateTimeUtils.DAY_MS    // 아직 안 온 2차

    /** 1차 = 55만 / 계약금 10만 받음 / 잔금 45만 아직. 지난 날짜(=완료/잔금 받을 대상). */
    private fun job(
        id: Long = 10L,
        date: Long = past,
        balancePaidAt: Long? = null,
        workCompletedAt: Long? = null,
    ) = JobEntity(
        id = id, customerId = 1L, scheduledWorkDate = date, scheduledWorkDays = 1,
        totalAmount = 550_000L, depositAmount = 100_000L, depositPaidAt = now,
        balanceAmount = 450_000L, balancePaidAt = balancePaidAt, workCompletedAt = workCompletedAt,
        createdAt = 0L, updatedAt = 0L
    )

    /** 고객 카드(미러) — 아직 잔금 안 받음(balancePaidAt=null)·완료 아님. = 버그가 나던 '막힌' 상태의 씨앗. */
    private fun customer() = CustomerEntity(
        id = 1L, phoneNumber = "01066285179", name = "완납버그",
        scheduledWorkDate = past, totalAmount = 550_000L, depositAmount = 100_000L, depositPaidAt = now,
        balanceAmount = 450_000L, balancePaidAt = null, workCompletedAt = null,
        createdAt = 0L, updatedAt = 0L
    )

    private fun lastCustomerUpdate(customerDao: CustomerDao): CustomerEntity =
        argumentCaptor<CustomerEntity>().run {
            verifyBlocking(customerDao, atLeastOnce()) { update(capture()) }
            allValues.last()
        }

    @Test
    fun `잔금 받으면 고객 카드·배지가 완료가 된다`() = runTest {
        val jobDao = mock<JobDao> {
            onBlocking { findById(10L) } doReturn job()
            // setBalancePaid 가 건을 '받음+완료'로 바꾼 뒤의 모습(미러가 읽는다)
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(job(balancePaidAt = now, workCompletedAt = now))
            onBlocking { allOnce() } doReturn listOf(job(balancePaidAt = now, workCompletedAt = now))
        }
        val customerDao = mock<CustomerDao> { onBlocking { findById(1L) } doReturn customer() }
        val repo = JobRepository(jobDao, customerDao)

        repo.setBalancePaid(10L, now, now)

        val card = lastCustomerUpdate(customerDao)
        assertEquals("고객 카드에 잔금 받음이 찍혀야 한다", now, card.balancePaidAt)
        // 돈까지 받았으니 「완납」 — 「완료」≠「완납」 (2026-10-18 사장님 용어 확정). '잔금미수'로 남으면 버그.
        assertEquals("배지(상담함·챗)도 완납이어야 한다 — '잔금미수'로 남으면 버그", "완납", customerStatusOf(card, today, now))
    }

    @Test
    fun `완료 찍으면 다른 미완료 건이 있어도 그 손님 배지가 반영된다`() = runTest {
        // 1차(지난, 대표) + 2차(미래, 미완료) — 대표인 1차를 완료 처리.
        val completed1 = job(id = 10L, date = past, workCompletedAt = now)
        val jobDao = mock<JobDao> {
            onBlocking { findById(10L) } doReturn job(id = 10L, date = past)
            // 완료 뒤 상태: 대표(RepresentativeJob.pick)는 '오늘 이후 가장 가까운' 2차지만,
            //   1차를 완료했으면 그 사실이 카드에 닿아야 한다. (대표 규칙은 완료를 무시 → 2차가 대표)
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(completed1, job(id = 20L, date = future))
            onBlocking { allOnce() } doReturn listOf(completed1, job(id = 20L, date = future))
        }
        val customerDao = mock<CustomerDao> { onBlocking { findById(1L) } doReturn customer() }
        val repo = JobRepository(jobDao, customerDao)

        // setWorkCompleted 안에서 recomputeMirror 가 돌아 customerDao.update 가 적어도 한 번은 불려야 한다.
        repo.setWorkCompleted(10L, now, now)
        verifyBlocking(customerDao, atLeastOnce()) { update(org.mockito.kotlin.any()) }
    }

    @Test
    fun `계약금만 받으면 완료가 아니다`() = runTest {
        val jobDao = mock<JobDao> {
            onBlocking { findById(10L) } doReturn job(date = future)   // 아직 안 온 시공
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(job(date = future).copy(depositPaidAt = now))
            onBlocking { allOnce() } doReturn listOf(job(date = future).copy(depositPaidAt = now))
        }
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer().copy(scheduledWorkDate = future)
        }
        val repo = JobRepository(jobDao, customerDao)

        repo.setDepositPaid(10L, now, now)

        val card = lastCustomerUpdate(customerDao)
        // 미래 시공 + 계약금만 = 예약(완료 아님). 잔금 안 받았으니 완료로 뜨면 안 된다.
        assertEquals("예약", customerStatusOf(card, today, now))
    }
}
