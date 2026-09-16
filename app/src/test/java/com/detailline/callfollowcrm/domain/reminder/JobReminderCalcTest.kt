package com.detailline.callfollowcrm.domain.reminder

import com.detailline.callfollowcrm.data.local.entity.JobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 알람을 **건(件)별로** 발사하는가. (2026-09-17 재방문 Stage B)
 *
 * 왜 테스트로 고정하나: "안 울린 것"은 눈에 안 보인다.
 * 한 고객이 두 날짜를 잡았을 때 두 번째 날짜의 D-1 이 안 울리면,
 * 사장님은 **현장을 통째로 놓치고 나서야** 안다. 화면 버그와 급이 다르다.
 */
class JobReminderCalcTest {

    private val DAY = 86_400_000L

    /** 테스트용 '자정' — 실제 앱은 한국 시간 자정을 넘겨준다. 여기선 DAY 단위로 단순화. */
    private val startOfDay: (Long) -> Long = { it - (it % DAY) }

    private fun job(
        id: Long,
        customerId: Long = 1L,
        day: Long? = 10 * DAY,
        minutes: Int? = 540,
        days: Int = 1,
        total: Long? = 1_000_000L,
        balance: Long? = null,
        balancePaidAt: Long? = null,
        address: String? = "서울 강서구",
    ) = JobEntity(
        id = id,
        customerId = customerId,
        scheduledWorkDate = day,
        scheduledWorkMinutes = minutes,
        scheduledWorkDays = days,
        address = address,
        totalAmount = total,
        balanceAmount = balance,
        balancePaidAt = balancePaidAt,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun d1(jobs: List<JobEntity>, tomorrow: Long, notified: Set<String> = emptySet()) =
        JobReminderCalc.d1Due(jobs, tomorrow, DAY, notified, startOfDay)

    // ── 핵심: 두 번째 날짜도 울려야 한다 ────────────────────────

    @Test
    fun `같은 고객의 두 날짜가 각각 울린다`() {
        // 인테리어 업체가 1차·2차를 한 번호로 준 경우 — 예전엔 대표 건 하나만 울렸다
        val jobs = listOf(
            job(id = 1, customerId = 7, day = 10 * DAY),
            job(id = 2, customerId = 7, day = 20 * DAY)
        )
        assertEquals(listOf(1L), d1(jobs, 10 * DAY).map { it.job.id })
        assertEquals("두 번째 날짜도 울려야 한다", listOf(2L), d1(jobs, 20 * DAY).map { it.job.id })
    }

    @Test
    fun `같은 날 두 현장이면 둘 다 울린다`() {
        val jobs = listOf(
            job(id = 1, customerId = 7, day = 10 * DAY, address = "1512동"),
            job(id = 2, customerId = 7, day = 10 * DAY, address = "1513동")
        )
        assertEquals(listOf(1L, 2L), d1(jobs, 10 * DAY).map { it.job.id })
    }

    // ── 두 번 울리면 안 된다 ────────────────────────────────────

    @Test
    fun `이미 알린 건은 다시 안 울린다`() {
        val jobs = listOf(job(id = 1, day = 10 * DAY))
        val once = d1(jobs, 10 * DAY)
        assertEquals(1, once.size)
        assertTrue(d1(jobs, 10 * DAY, notified = setOf(once.first().key)).isEmpty())
    }

    @Test
    fun `업데이트 직전 옛 방식으로 알린 건은 그날 밤 다시 안 울린다`() {
        // 옛 키는 고객 기준("d1:<고객>:<날>") — 이게 남아 있으면 같은 밤에 또 울리지 않는다
        val jobs = listOf(job(id = 1, customerId = 7, day = 10 * DAY))
        val legacy = JobReminderCalc.legacyD1Key(7L, 10 * DAY)
        assertTrue(d1(jobs, 10 * DAY, notified = setOf(legacy)).isEmpty())
    }

    // ── 엉뚱한 날에 울리면 안 된다 ──────────────────────────────

    @Test
    fun `내일이 아닌 건은 안 울린다`() {
        val jobs = listOf(
            job(id = 1, day = 9 * DAY),    // 오늘
            job(id = 2, day = 11 * DAY),   // 모레
            job(id = 3, day = null)        // 일정 없음
        )
        assertTrue(d1(jobs, 10 * DAY).isEmpty())
    }

    @Test
    fun `시공 시각이 자정 직후여도 그 날로 센다`() {
        // 한국 시간 새벽 시공(드물지만) — 자정 계산을 밖에서 받는 이유
        val jobs = listOf(job(id = 1, day = 10 * DAY + 1))
        assertEquals(listOf(1L), d1(jobs, 10 * DAY).map { it.job.id })
    }

    // ── 잔금 알람도 건별 ────────────────────────────────────────

    private fun bal(jobs: List<JobEntity>, now: Long, notified: Set<String> = emptySet()) =
        JobReminderCalc.balanceDue(jobs, now, DAY, afterDays = 1, notified = notified, startOfDay = startOfDay)

    @Test
    fun `건마다 잔금을 따로 센다`() {
        val jobs = listOf(
            job(id = 1, customerId = 7, day = 10 * DAY, balance = 900_000L),                        // 미수
            job(id = 2, customerId = 7, day = 10 * DAY, balance = 500_000L, balancePaidAt = 1L)     // 받음
        )
        assertEquals("받은 건은 빼고 미수만", listOf(1L), bal(jobs, now = 12 * DAY).map { it.job.id })
    }

    @Test
    fun `시공이 아직 안 끝났으면 잔금을 안 조른다`() {
        val jobs = listOf(job(id = 1, day = 10 * DAY, days = 3, balance = 900_000L))
        // 10·11·12일 3일 공사 → 12일 끝. +1일 = 13일부터
        assertTrue(bal(jobs, now = 12 * DAY).isEmpty())
        assertEquals(listOf(1L), bal(jobs, now = 13 * DAY).map { it.job.id })
    }

    @Test
    fun `받을 잔금이 없으면 안 울린다`() {
        val jobs = listOf(
            job(id = 1, day = 10 * DAY, balance = 0L),
            job(id = 2, day = 10 * DAY, balance = null)
        )
        assertTrue(bal(jobs, now = 20 * DAY).isEmpty())
    }

    @Test
    fun `건이 하나도 없어도 안전하다`() {
        assertTrue(d1(emptyList(), 10 * DAY).isEmpty())
        assertTrue(bal(emptyList(), now = 10 * DAY).isEmpty())
    }
}
