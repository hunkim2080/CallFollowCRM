package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CustomerDao
import com.detailline.callfollowcrm.data.local.dao.JobDao
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking

/**
 * 건(件) 단위 전환 — **돈이 건마다 따로 있는지**. (2026-09-18)
 *
 * 폰에서 직접 눌러보려 했으나 테스트폰에 깔린 사본이 플레이 서명이라 덮어쓸 수 없었다.
 *   → 사장님 "경우의 수 시험표"(artifact/SR839W5X6ciEKZWDGp12BX) 중 **돈이 걸린 세 경우**를
 *      코드로 못 박는다. 세 경우 모두 예전 구조에서 실제로 사고가 나던 자리다.
 *
 *  - CASE 2-2 : 2차 금액을 고쳐도 1차 금액은 그대로
 *  - CASE 4-1 : 2차를 취소해도 1차 돈은 그대로  ← 예전엔 1차 돈이 백지가 됐다
 *  - CASE 2-6 : 1차가 안 끝났는데 2차를 **더 앞 날짜**로 먼저 잡아도 서로 안 섞인다
 */
class JobMoneyPerJobTest {

    private val now = 1_700_000_000_000L
    private val today = DateTimeUtils.startOfDay(now)
    private val day1 = today + DateTimeUtils.DAY_MS           // 내일 — 1차
    private val day7 = today + 7 * DateTimeUtils.DAY_MS       // 일주일 뒤 — 2차
    private val past2 = today - 2 * DateTimeUtils.DAY_MS      // 그저께

    /** 1차 = 100만 / 계약금 30만 / 잔금 70만 (아직 안 받음) */
    private fun firstJob() = JobEntity(
        id = 10L,
        customerId = 1L,
        scheduledWorkDate = day1,
        scheduledWorkDays = 1,
        totalAmount = 1_000_000L,
        depositAmount = 300_000L,
        depositPaidAt = now,
        balanceAmount = 700_000L,
        balancePaidAt = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    /** 2차 = 80만, 아직 아무것도 안 받음 */
    private fun secondJob(date: Long = day7) = JobEntity(
        id = 20L,
        customerId = 1L,
        scheduledWorkDate = date,
        scheduledWorkDays = 1,
        totalAmount = 800_000L,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun customer() = CustomerEntity(
        id = 1L,
        phoneNumber = "01012345678",
        name = "인테리어 업체",
        scheduledWorkDate = day1,
        totalAmount = 1_000_000L,
        depositAmount = 300_000L,
        depositPaidAt = now,
        balanceAmount = 700_000L,
        createdAt = 0L,
        updatedAt = 0L
    )

    // ── CASE 2-2 ────────────────────────────────────────────────────────────────
    @Test
    fun `2차 총액을 90만으로 고쳐도 1차는 100만 그대로`() = runTest {
        val jobDao = mock<JobDao> {
            onBlocking { findById(20L) } doReturn secondJob()
        }
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer()
        }
        val repo = JobRepository(jobDao, customerDao)

        repo.updateMoney(
            jobId = 20L,
            totalAmount = 900_000L,
            depositAmount = null,
            depositPaidAt = null,
            balanceAmount = null,
            balancePaidAt = null,
            now = now
        )

        argumentCaptor<JobEntity>().apply {
            verifyBlocking(jobDao) { update(capture()) }
            // 손댄 전표는 2차 하나뿐 — 1차(10L)는 아예 안 건드린다
            assertTrue(allValues.all { it.id == 20L })
            assertEquals(900_000L, firstValue.totalAmount)
        }
    }

    // ── CASE 4-1 ────────────────────────────────────────────────────────────────
    @Test
    fun `2차를 취소해도 1차 돈은 그대로 남는다`() = runTest {
        val jobDao = mock<JobDao> {
            onBlocking { findById(20L) } doReturn secondJob()
            // 취소 뒤 '시공일 있는 건' = 1차 하나 (2차는 날짜가 비었으므로 빠진다)
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(firstJob())
        }
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer()
        }
        val repo = JobRepository(jobDao, customerDao)

        repo.cancelJob(20L, now)

        // ① 전표는 2차만 백지가 된다
        argumentCaptor<JobEntity>().apply {
            verifyBlocking(jobDao) { update(capture()) }
            assertTrue(allValues.all { it.id == 20L })
            assertNull(firstValue.scheduledWorkDate)
            assertNull(firstValue.totalAmount)
        }
        // ② 고객 카드(대표 건 미러)엔 **1차 돈**이 남는다 — 예전엔 여기까지 백지가 됐다
        argumentCaptor<CustomerEntity>().apply {
            // 취소 경로는 고객 카드를 두 번 쓴다(일정 미러 → 돈 미러). 결론은 마지막 값.
            verifyBlocking(customerDao, atLeastOnce()) { update(capture()) }
            val last = allValues.last()
            assertEquals(1_000_000L, last.totalAmount)
            assertEquals(300_000L, last.depositAmount)
            assertEquals(700_000L, last.balanceAmount)
            assertEquals(day1, last.scheduledWorkDate)
        }
    }

    // ── 일정 등록 경로 (2026-09-18 실기에서 터진 자리) ──────────────────────────
    @Test
    fun `대표 건이 아닌 새 건을 등록해도 대표 건 돈은 그대로다`() = runTest {
        // 1차(내일·100만)가 대표. 2차를 일주일 뒤로 80만에 등록한 직후 상태.
        val jobDao = mock<JobDao> {
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(firstJob(), secondJob())
        }
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer()
        }
        val repo = JobRepository(jobDao, customerDao)

        // 등록 화면이 하는 일 = 고객 카드를 **대표 건 값으로** 맞추기 (반대 방향 금지)
        repo.syncMoneyFromRepresentative(1L, now)

        argumentCaptor<CustomerEntity>().apply {
            verifyBlocking(customerDao, atLeastOnce()) { update(capture()) }
            val last = allValues.last()
            assertEquals("고객 카드는 대표 건(1차) 금액이어야 한다", 1_000_000L, last.totalAmount)
        }
        // 전표는 아무것도 안 건드린다 — 2차 금액이 1차로 흘러들면 안 된다
        verifyBlocking(jobDao, never()) { update(any()) }
    }

    // ── CASE 2-6 ────────────────────────────────────────────────────────────────
    @Test
    fun `1차보다 앞 날짜로 현장을 더 잡아도 1차 전표는 안 건드린다`() = runTest {
        val jobDao = mock<JobDao> {
            onBlocking { countByCustomerAndDate(1L, past2) } doReturn 0
            onBlocking { insert(any()) } doReturn 30L
            // 등록 후: 그저께 건 + 내일 건 (오름차순)
            onBlocking { scheduledByCustomerOnce(1L) } doReturn
                listOf(secondJob(date = past2).copy(id = 30L), firstJob())
        }
        val customerDao = mock<CustomerDao> {
            onBlocking { findById(1L) } doReturn customer()
        }
        val repo = JobRepository(jobDao, customerDao)

        val newId = repo.addJob(
            customerId = 1L,
            scheduledWorkDate = past2,
            scheduledWorkMinutes = null,
            scheduledWorkDays = 1,
            address = "먼저 잡아둔 현장",
            totalAmount = 500_000L,
            depositAmount = null,
            depositPaidAt = null,
            now = now
        )

        assertEquals(30L, newId)
        // 새 건은 '추가'만 — 기존 전표를 고치지 않는다
        verifyBlocking(jobDao, never()) { update(any()) }
        // 대표는 오늘 이후 가장 가까운 건 = 1차(내일). 지난 건이 대표를 뺏지 않는다.
        argumentCaptor<CustomerEntity>().apply {
            verifyBlocking(customerDao) { update(capture()) }
            assertEquals(day1, allValues.last().scheduledWorkDate)
        }
    }

    // ── 취소한 건 규칙 (2026-09-18 사장님 "1차가 잡히지도 않았는데 2차 3차 등록도 가능하네") ──
    //   취소는 기록을 남기려고 날짜만 비운다 → 날짜 없는 건이 '아직 날짜 안 정한 새 건'과
    //   구별이 안 돼 탭에서 차수를 차지했다. cancelledAt 으로 가른다.
    @Test
    fun `취소하면 취소한 시각이 찍힌다`() = runTest {
        val jobDao = mock<JobDao> {
            onBlocking { findById(20L) } doReturn secondJob()
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(firstJob())
        }
        val customerDao = mock<CustomerDao> { onBlocking { findById(1L) } doReturn customer() }
        val repo = JobRepository(jobDao, customerDao)

        repo.cancelJob(20L, now)

        argumentCaptor<JobEntity>().apply {
            verifyBlocking(jobDao) { update(capture()) }
            assertEquals(now, firstValue.cancelledAt)
        }
    }

    @Test
    fun `날짜를 다시 잡으면 취소 표시가 지워진다`() = runTest {
        val cancelled = secondJob().copy(scheduledWorkDate = null, cancelledAt = now - 1000L)
        val jobDao = mock<JobDao> {
            onBlocking { findById(20L) } doReturn cancelled
            onBlocking { scheduledByCustomerOnce(1L) } doReturn listOf(firstJob())
        }
        val customerDao = mock<CustomerDao> { onBlocking { findById(1L) } doReturn customer() }
        val repo = JobRepository(jobDao, customerDao)

        repo.rescheduleJob(20L, day7, now)

        argumentCaptor<JobEntity>().apply {
            verifyBlocking(jobDao) { update(capture()) }
            assertEquals(day7, firstValue.scheduledWorkDate)
            assertNull("되살렸으면 취소 표시는 없어야 한다", firstValue.cancelledAt)
        }
    }
}
