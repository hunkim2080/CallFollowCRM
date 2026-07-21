package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CustomerDao
import com.detailline.callfollowcrm.data.local.dao.JobDao
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking

/**
 * [JobRepository.archiveCompletedBeforeNewSchedule] 규칙 검증 (재방문/추가 시공, 2026-07-20).
 *   - 완료된 시공이면: 완료 건을 jobs 로 보관 + 고객 시공 필드 전부 리셋 → true
 *   - 완료 전(진행/신규)이면: 아무것도 안 함 → false (기존 동작 = 그 건 편집)
 */
class JobRepositoryArchiveTest {

    private fun customer(
        scheduledWorkDate: Long? = 1_000L,
        workCompletedAt: Long? = null
    ) = CustomerEntity(
        id = 1L,
        phoneNumber = "01012345678",
        name = "테스트",
        address = "강동구 천호동 래미안 101동 1502호",
        scheduledWorkDate = scheduledWorkDate,
        totalAmount = 400_000L,
        depositAmount = 100_000L,
        balanceAmount = 300_000L,
        workCompletedAt = workCompletedAt,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `완료된 시공이면 이력 보관하고 고객 필드를 리셋한다`() = runTest {
        val jobDao = mock<JobDao>()
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer(workCompletedAt = 5_000L)
        }
        val repo = JobRepository(jobDao, customerDao)

        val archived = repo.archiveCompletedBeforeNewSchedule(1L, now = 9_000L)

        assertTrue(archived)
        // 완료 건이 원래 데이터 그대로 jobs 로 보관됨
        argumentCaptor<JobEntity>().apply {
            verifyBlocking(jobDao) { insert(capture()) }
            assertEquals(1L, firstValue.customerId)
            assertEquals(1_000L, firstValue.scheduledWorkDate)
            assertEquals(5_000L, firstValue.workCompletedAt)
            assertEquals(400_000L, firstValue.totalAmount)
        }
        // 고객 시공 필드는 새 건용으로 전부 리셋됨
        argumentCaptor<CustomerEntity>().apply {
            verifyBlocking(customerDao) { update(capture()) }
            assertNull(firstValue.scheduledWorkDate)
            assertNull(firstValue.workCompletedAt)
            assertNull(firstValue.address)
            assertNull(firstValue.totalAmount)
            assertNull(firstValue.balanceAmount)
        }
    }

    @Test
    fun `완료 전이면 보관도 리셋도 안 한다`() = runTest {
        val jobDao = mock<JobDao>()
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer(workCompletedAt = null)
        }
        val repo = JobRepository(jobDao, customerDao)

        assertFalse(repo.archiveCompletedBeforeNewSchedule(1L, now = 9_000L))
        verifyBlocking(jobDao, never()) { insert(any()) }
        verifyBlocking(customerDao, never()) { update(any()) }
    }

    @Test
    fun `시공일이 없으면 보관 안 한다`() = runTest {
        val jobDao = mock<JobDao>()
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer(scheduledWorkDate = null, workCompletedAt = 5_000L)
        }
        val repo = JobRepository(jobDao, customerDao)

        assertFalse(repo.archiveCompletedBeforeNewSchedule(1L, now = 9_000L))
        verifyBlocking(jobDao, never()) { insert(any()) }
    }
}

