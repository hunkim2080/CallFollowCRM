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
import org.mockito.kotlin.verifyBlocking

/**
 * 재설계 1단계 — 시공 날짜·시간을 **그 건(건별)** 에 저장하는지 검증. (2026-10-14 사장님: "2차 날짜 선택했는데 등록 안 됨")
 *   돈은 건별인데 날짜만 고객(대표 건) 경로여서 2차를 보며 잡아도 1차로 갔다.
 *   핵심: setJobDateAndDays/setJobTiming 은 **넘긴 jobId 그 건**을 update 해야 한다(대표 건이 아니라).
 */
class JobDateRoutingTest {

    private val now = 1_700_000_000_000L
    private val today = DateTimeUtils.startOfDay(now)
    private val day1 = today - 10 * DateTimeUtils.DAY_MS   // 1차 (지난 건, 완납)
    private val newDay2 = today + 20 * DateTimeUtils.DAY_MS // 2차에 새로 잡는 날

    private fun customer() = CustomerEntity(
        id = 1L, phoneNumber = "01012345678", name = "인테리어",
        scheduledWorkDate = day1, createdAt = 0L, updatedAt = 0L
    )

    @Test
    fun `setJobDateAndDays 는 대표가 아닌 그 2차 건에 날짜를 쓴다`() = runTest {
        val job1 = JobEntity(id = 11L, customerId = 1L, scheduledWorkDate = day1, workCompletedAt = day1, createdAt = 0L, updatedAt = 0L)
        val job2 = JobEntity(id = 12L, customerId = 1L, scheduledWorkDate = null, createdAt = 0L, updatedAt = 0L)  // 2차 — 아직 날짜 없음
        val jobDao = mock<JobDao> {
            onBlocking { findById(12L) } doReturn job2
            // update 후 미러 재계산에서 읽는 목록 — 2차가 이제 날짜를 가졌다
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(job1, job2.copy(scheduledWorkDate = newDay2))
        }
        val customerDao = mock<CustomerDao> { onBlocking { findById(1L) } doReturn customer() }
        val repo = JobRepository(jobDao, customerDao)

        repo.setJobDateAndDays(jobId = 12L, dayMs = newDay2, days = 1, now = now)

        // 🔴 그 2차 건(12) 이 날짜를 받는다 — 1차(11) 가 아니라.
        argumentCaptor<JobEntity>().apply {
            verifyBlocking(jobDao) { update(capture()) }
            assertEquals("업데이트 대상은 2차(12)", 12L, firstValue.id)
            assertEquals("2차에 새 날짜가 들어간다", newDay2, firstValue.scheduledWorkDate)
            assertNull("취소 표시는 지워진다(다시 날짜 잡음)", firstValue.cancelledAt)
        }
        // 미러는 '오늘 이후 가장 가까운 건' = 2차(newDay2) 로 맞춰진다
        argumentCaptor<CustomerEntity>().apply {
            verifyBlocking(customerDao) { update(capture()) }
            assertEquals(newDay2, firstValue.scheduledWorkDate)
        }
    }

    @Test
    fun `setJobTiming 은 그 건의 시간·기간만 바꾸고 날짜는 안 건드린다`() = runTest {
        val job2 = JobEntity(id = 12L, customerId = 1L, scheduledWorkDate = newDay2, createdAt = 0L, updatedAt = 0L)
        val jobDao = mock<JobDao> {
            onBlocking { findById(12L) } doReturn job2
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(job2.copy(scheduledWorkMinutes = 9 * 60, scheduledWorkDays = 2))
        }
        val customerDao = mock<CustomerDao> { onBlocking { findById(1L) } doReturn customer() }
        val repo = JobRepository(jobDao, customerDao)

        repo.setJobTiming(jobId = 12L, minutes = 9 * 60, days = 2, now = now)

        argumentCaptor<JobEntity>().apply {
            verifyBlocking(jobDao) { update(capture()) }
            assertEquals(12L, firstValue.id)
            assertEquals(9 * 60, firstValue.scheduledWorkMinutes)
            assertEquals(2, firstValue.scheduledWorkDays)
            assertEquals("날짜는 그대로", newDay2, firstValue.scheduledWorkDate)
        }
    }

    @Test
    fun `없는 건이면 아무것도 안 한다`() = runTest {
        val jobDao = mock<JobDao> { onBlocking { findById(99L) } doReturn null }
        val customerDao = mock<CustomerDao>()
        val repo = JobRepository(jobDao, customerDao)

        repo.setJobDateAndDays(jobId = 99L, dayMs = newDay2, days = 1, now = now)

        verifyBlocking(jobDao, org.mockito.kotlin.never()) { update(any()) }
    }
}
