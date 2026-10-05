package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CustomerDao
import com.detailline.callfollowcrm.data.local.dao.JobDao
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking

/**
 * 재방문 Phase2 **Stage A** 검증 (DB v49, 2026-09-11 사장님).
 *
 * 실전 케이스: 인테리어 업체가 한 번호로 **여러 현장**을 준다 —
 *   "일주일 뒤 여기도 해주세요" 하면 **첫 일정이 지워지지 않고** 두 일정이 같이 살아있어야 한다.
 *
 * 규칙:
 *  - addJob = 건을 **쌓는다**(덮어쓰기 X). 같은 고객·같은 날은 중복 생성 안 함.
 *  - CustomerEntity 시공필드 = **대표 건 미러**(오늘 이후 가장 가까운 건, 없으면 가장 최근 건).
 *  - Stage A 미러는 **일정 필드만** 건드린다 — 돈(총액·계약금·잔금)은 그대로(정산 보호). 건별 정산은 Stage B.
 */
class JobRepositoryStageATest {

    private val now = 1_700_000_000_000L
    private val today = DateTimeUtils.startOfDay(now)
    private val day1 = today + DateTimeUtils.DAY_MS          // 내일 (첫 현장)
    private val day7 = today + 7 * DateTimeUtils.DAY_MS      // 일주일 뒤 (추가 현장)

    private fun customer(
        scheduledWorkDate: Long? = day1,
        totalAmount: Long? = 400_000L
    ) = CustomerEntity(
        id = 1L,
        phoneNumber = "01012345678",
        name = "인테리어 업체",
        address = "강동구 천호동 래미안 101동 1502호",
        scheduledWorkDate = scheduledWorkDate,
        totalAmount = totalAmount,
        depositAmount = 100_000L,
        balanceAmount = 300_000L,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun job(id: Long, date: Long, total: Long? = null) = JobEntity(
        id = id,
        customerId = 1L,
        scheduledWorkDate = date,
        scheduledWorkDays = 1,
        totalAmount = total,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `일주일 뒤 현장을 추가해도 첫 일정이 안 지워지고 둘 다 남는다`() = runTest {
        val jobDao = mock<JobDao> {
            onBlocking { jobsAt(1L, day7) } doReturn emptyList()
            onBlocking { insert(any()) } doReturn 20L
            // 등록 후 이 고객의 '시공일 있는' 건 = 첫 현장 + 추가 현장 (오름차순)
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(job(10L, day1), job(20L, day7))
        }
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer()
        }
        val repo = JobRepository(jobDao, customerDao)

        val newId = repo.addJob(
            customerId = 1L,
            scheduledWorkDate = day7,
            scheduledWorkMinutes = null,
            scheduledWorkDays = 1,
            address = "송파구 잠실 현장",
            totalAmount = null,
            depositAmount = null,
            depositPaidAt = null,
            now = now
        )

        assertEquals(20L, newId)
        // 새 건이 '추가'됨 (첫 건은 그대로 — 덮어쓰기 아님)
        argumentCaptor<JobEntity>().apply {
            verifyBlocking(jobDao) { insert(capture()) }
            assertEquals(day7, firstValue.scheduledWorkDate)
            assertEquals(1L, firstValue.customerId)
        }
        // 대표 건 미러 = 오늘 이후 가장 가까운 건 = 첫 현장(내일)
        argumentCaptor<CustomerEntity>().apply {
            verifyBlocking(customerDao) { update(capture()) }
            assertEquals(day1, firstValue.scheduledWorkDate)
        }
    }

    @Test
    fun `같은 날 같은 현장을 또 넣으면 안 쌓인다`() = runTest {
        // 2026-09-28: 옛 규칙은 「같은 날이면 무조건 막음」이었다. 그래서 **하루 두 현장**이
        //   말없이 사라졌다(사장님: "인테리어 업체는 한 번호에 현장 여러 개").
        //   이제는 **같은 주소**일 때만 같은 현장으로 보고 막는다. 규칙 = SameDayGuard.
        val site = "수원시 영통구 1"
        val jobDao = mock<JobDao> {
            onBlocking { jobsAt(1L, day7) } doReturn listOf(job(10L, day7).copy(address = site))
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(job(10L, day7))
        }
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer()
        }
        val repo = JobRepository(jobDao, customerDao)

        val id = repo.addJob(
            customerId = 1L, scheduledWorkDate = day7,
            scheduledWorkMinutes = null, scheduledWorkDays = 1, address = site,
            totalAmount = null, depositAmount = null, depositPaidAt = null, now = now
        )

        assertEquals(0L, id)
        verifyBlocking(jobDao, never()) { insert(any()) }
    }

    @Test
    fun `같은 날이라도 다른 현장이면 둘 다 남는다`() = runTest {
        // 아침 한 집, 오후 한 집. 전엔 둘째가 사라지고도 「일정 등록 완료」라고 했다. (2026-09-28)
        val jobDao = mock<JobDao> {
            onBlocking { jobsAt(1L, day7) } doReturn
                listOf(job(10L, day7).copy(address = "수원시 영통구 1", createdAt = now - 60_000L))
            onBlocking { insert(any()) } doReturn 21L
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(job(10L, day7), job(21L, day7))
        }
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer()
        }
        val repo = JobRepository(jobDao, customerDao)

        val id = repo.addJob(
            customerId = 1L, scheduledWorkDate = day7,
            scheduledWorkMinutes = null, scheduledWorkDays = 1, address = "용인시 기흥구 2",
            totalAmount = null, depositAmount = null, depositPaidAt = null, now = now
        )

        assertEquals("둘째 현장도 만들어져야 한다", 21L, id)
    }

    @Test
    fun `예정 건이 없으면 가장 최근 지난 건이 대표가 된다`() = runTest {
        val past7 = today - 7 * DateTimeUtils.DAY_MS
        val past1 = today - DateTimeUtils.DAY_MS
        val jobDao = mock<JobDao> {
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(job(10L, past7), job(11L, past1))
        }
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer()
        }
        val repo = JobRepository(jobDao, customerDao)

        repo.recomputeMirror(1L, now)

        argumentCaptor<CustomerEntity>().apply {
            verifyBlocking(customerDao) { update(capture()) }
            assertEquals(past1, firstValue.scheduledWorkDate)
        }
    }

    @Test
    fun `미러는 대표 건의 돈을 고객 카드로 옮긴다`() = runTest {
        // 2026-10-05: 「미러는 돈을 안 옮긴다(정산 보호)」를 **반전**했다.
        //   정산·미수가 customers 를 읽던 시절의 보호였는데, 지금은 전부 SettlementCalc.book(jobs) 를 읽는다.
        //   그 규칙만 남아 카드를 거짓말하게(2차에 1차 돈) 만들어서, 이제 카드 = 대표 건(한 방향)으로 맞춘다.
        val jobDao = mock<JobDao> {
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(job(10L, day1, total = 999_000L))
        }
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer(totalAmount = 400_000L)
        }
        val repo = JobRepository(jobDao, customerDao)

        repo.recomputeMirror(1L, now)

        argumentCaptor<CustomerEntity>().apply {
            verifyBlocking(customerDao) { update(capture()) }
            assertEquals(999_000L, firstValue.totalAmount)   // 카드가 건 돈으로
            assertNull(firstValue.depositAmount)             // 건엔 계약금 없음 → 카드도 비움
            assertNull(firstValue.balanceAmount)
        }
    }

    @Test
    fun `1차 완납·완료(지남) + 2차 블랭크(미래)가 대표면 - 카드 돈·완료가 비워진다`() = runTest {
        // 사장님 신고(2026-10-05): 1차 끝내고 잔금받은 뒤 2차 잡으면 2차에 1차 돈·완납이 묻어나던 것.
        val past = today - DateTimeUtils.DAY_MS
        val firstPaid = JobEntity(
            id = 10L, customerId = 1L, scheduledWorkDate = past, scheduledWorkDays = 1,
            totalAmount = 1_000_000L, depositAmount = 300_000L, depositPaidAt = now,
            balanceAmount = 700_000L, balancePaidAt = now, workCompletedAt = now,
            createdAt = 0L, updatedAt = 0L
        )
        val secondBlank = job(20L, day7)   // 미래, 돈 없음
        val jobDao = mock<JobDao> {
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(firstPaid, secondBlank)
        }
        val customerDao = mock<CustomerDao> {
            // 카드엔 아직 1차 돈·완납·완료가 남아 있다(미러가 돈을 안 옮기던 시절)
            onBlocking { findById(1L) } doReturn customer(totalAmount = 1_000_000L).copy(
                balancePaidAt = now, workCompletedAt = now
            )
        }
        val repo = JobRepository(jobDao, customerDao)

        repo.recomputeMirror(1L, now)

        argumentCaptor<CustomerEntity>().apply {
            verifyBlocking(customerDao) { update(capture()) }
            assertEquals(day7, firstValue.scheduledWorkDate)
            assertNull("2차가 대표 → 카드 총금액 비움", firstValue.totalAmount)
            assertNull("완납 비움", firstValue.balancePaidAt)
            assertNull("완료 비움", firstValue.workCompletedAt)
        }
    }

    @Test
    fun `한 건만 일정에서 빼도 나머지 건은 남는다`() = runTest {
        val jobDao = mock<JobDao> {
            onBlocking { findById(20L) } doReturn job(20L, day7)
            // 뺀 뒤엔 첫 현장만 남음
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(job(10L, day1))
        }
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer()
        }
        val repo = JobRepository(jobDao, customerDao)

        repo.unscheduleJob(20L, now)

        // 뺀 건은 시공일만 비워짐(기록 보존 — 되돌리기 가능)
        argumentCaptor<JobEntity>().apply {
            verifyBlocking(jobDao) { update(capture()) }
            assertEquals(20L, firstValue.id)
            assertNull(firstValue.scheduledWorkDate)
        }
        // 미러는 남은 첫 현장으로 재계산
        argumentCaptor<CustomerEntity>().apply {
            verifyBlocking(customerDao) { update(capture()) }
            assertEquals(day1, firstValue.scheduledWorkDate)
        }
    }
}
