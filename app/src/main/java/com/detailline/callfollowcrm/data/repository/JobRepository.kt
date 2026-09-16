package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CustomerDao
import com.detailline.callfollowcrm.data.local.dao.JobDao
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import kotlinx.coroutines.flow.Flow

/**
 * 시공 건(이력) 리포지토리. DB v42 (2026-07-20). [JobEntity] 참고.
 *
 * Phase 1 핵심: "재방문(추가 시공)" 대비. 같은 고객에 두 번째 시공을 잡을 때 첫 시공이 유실되지 않게,
 *   **완료된 현재 시공을 jobs 로 옮겨 담고 CustomerEntity 를 새 건용으로 리셋**한다.
 *   (캘린더·정산은 여전히 CustomerEntity 를 읽으므로 "현재 건"은 그대로, "지난 건"은 여기 쌓인다.)
 */
class JobRepository(
    private val jobDao: JobDao,
    private val customerDao: CustomerDao
) {

    /** 한 고객의 지난 시공 이력(최근순). */
    fun observeByCustomer(customerId: Long): Flow<List<JobEntity>> = jobDao.observeByCustomer(customerId)

    /** 지난 시공 N건(구독). */
    fun observeCountByCustomer(customerId: Long): Flow<Int> = jobDao.observeCountByCustomer(customerId)

    /** 모든 지난 시공(이력) 구독 — 매출 집계가 현재 건(CustomerEntity)과 함께 합산. (2026-08-11 돈감사 rank1) */
    fun observeAll(): Flow<List<JobEntity>> = jobDao.observeAll()

    suspend fun byCustomerOnce(customerId: Long): List<JobEntity> = jobDao.byCustomerOnce(customerId)

    /**
     * 새 시공 일정을 등록하기 **직전** 호출.
     *   현재 고객의 시공이 이미 **완료(workCompletedAt != null)** 상태면 → 그 완료 건을 jobs(이력)로 보관하고,
     *   CustomerEntity 의 시공 필드를 **전부 리셋**한다(이후 등록 폼이 새 건 값을 채움). → 첫 시공 유실 방지.
     *   완료 상태가 아니면(신규/진행 중) 아무것도 안 하고 false (기존 동작 그대로 = 그 건을 편집).
     *
     * @return 보관·리셋했으면 true (= 새 건 시작). 아니면 false.
     */
    suspend fun archiveCompletedBeforeNewSchedule(customerId: Long, now: Long): Boolean {
        val c = customerDao.findById(customerId) ?: return false
        // "첫 시공 끝난 뒤 또" 패턴만 처리 — 완료됐고 실제 시공일이 있던 건.
        if (c.workCompletedAt == null || c.scheduledWorkDate == null) return false

        // ⚠️ 미수(못 받은 돈)가 남은 완료 건은 아카이브 보류 — 아카이브하면 그 미수가 jobs(이력)로 옮겨져
        //   정산·미수금 목록(CustomerEntity 만 읽음)에서 조용히 사라져 사장님이 못 받고 놓칠 수 있음(돈 사고).
        //   완납(미수 0)일 때만 이력으로 정리. 미수 남으면 그대로 둬서 계속 미수금에 뜨게 함(받은 뒤 재방문 잡으면 정상 정리).
        //   (2026-07-30 버그감사 — 재방문 Phase2 전 최소 가드. SoT=docs/PLAN_repeat_jobs.md)
        if (com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(c).outstanding > 0L) return false

        jobDao.insert(
            JobEntity(
                customerId = c.id,
                scheduledWorkDate = c.scheduledWorkDate,
                scheduledWorkMinutes = c.scheduledWorkMinutes,
                scheduledWorkDays = c.scheduledWorkDays,
                address = c.address,
                totalAmount = c.totalAmount,
                depositAmount = c.depositAmount,
                depositPaidAt = c.depositPaidAt,
                balanceAmount = c.balanceAmount,
                balancePaidAt = c.balancePaidAt,
                workCompletedAt = c.workCompletedAt,
                createdAt = now,
                updatedAt = now
            )
        )
        // 새 건을 위해 시공 필드 전부 리셋 — 이후 등록 폼이 새 값을 덮어씀(주소/금액 안 넣으면 미입력으로 남음).
        customerDao.update(
            c.copy(
                scheduledWorkDate = null,
                scheduledWorkMinutes = null,
                scheduledWorkDays = 1,
                address = null,
                totalAmount = null,
                depositAmount = null,
                depositPaidAt = null,
                balanceAmount = null,
                balancePaidAt = null,
                workCompletedAt = null,
                updatedAt = now
            )
        )
        return true
    }

    // ── Phase 2 Stage A (DB v49) — jobs = 일정의 SoT, CustomerEntity 시공필드 = "대표 건" 미러.
    //    한 고객이 여러 날짜에 시공받아도 서로 안 덮어씀. (2026-09-11 사장님: 인테리어 업체는 한 번호에 현장 여러 개)

    /** 완료된 지난 시공만 — 고객상세 "지난 시공 N건". 예정 건이 jobs 에 들어와도 안 섞이게. */
    fun observeCompletedByCustomer(customerId: Long): Flow<List<JobEntity>> =
        jobDao.observeCompletedByCustomer(customerId)

    /** 시공일 잡힌 모든 건 — 캘린더/일정 화면의 SoT. */
    fun observeScheduled(): Flow<List<JobEntity>> = jobDao.observeScheduled()

    /** 알람용 1회 조회 — 시공일이 잡힌 모든 건. (2026-09-17 재방문 Stage B) */
    suspend fun scheduledOnce(): List<JobEntity> = jobDao.scheduledOnce()

    suspend fun findById(jobId: Long): JobEntity? = jobDao.findById(jobId)

    /** 그 고객·그 날의 건 — 일정 카드에서 '어느 건'을 뺄지 특정할 때. */
    suspend fun jobAt(customerId: Long, dayMs: Long): JobEntity? = jobDao.jobAt(customerId, dayMs)

    /**
     * 새 시공 건 등록 — 같은 고객의 기존 일정을 **덮지 않고** 건으로 쌓는다.
     *   같은 고객·같은 날이 이미 있으면 중복 생성 안 함(연타 가드).
     * @return 새 job id (중복이면 0)
     */
    suspend fun addJob(
        customerId: Long,
        scheduledWorkDate: Long,
        scheduledWorkMinutes: Int?,
        scheduledWorkDays: Int,
        address: String?,
        totalAmount: Long?,
        depositAmount: Long?,
        depositPaidAt: Long?,
        now: Long
    ): Long {
        if (jobDao.countByCustomerAndDate(customerId, scheduledWorkDate) > 0) {
            recomputeMirror(customerId, now)
            return 0L
        }
        val id = jobDao.insert(
            JobEntity(
                customerId = customerId,
                scheduledWorkDate = scheduledWorkDate,
                scheduledWorkMinutes = scheduledWorkMinutes,
                scheduledWorkDays = scheduledWorkDays.coerceAtLeast(1),
                address = address,
                totalAmount = totalAmount,
                depositAmount = depositAmount,
                depositPaidAt = depositPaidAt,
                createdAt = now,
                updatedAt = now
            )
        )
        recomputeMirror(customerId, now)
        return id
    }

    /**
     * 고객 카드의 시공 정보(날짜·시간·일수·주소)를 **대표 건**에 그대로 밀어넣는다.
     *   (예정 건이 없으면 만들고, 날짜가 비었으면 일정 취소)
     *
     * 🔴 왜 필요한가 (2026-09-15 사장님: "일정을 싹 바꿨는데 캘린더가 안 변해")
     *   Stage A(9/11)에서 일정 화면·달력의 출처를 jobs 로 옮겼는데,
     *   **고객 단위로 고치는 옛 경로들**(고객상세 날짜/시간/기간 · 채팅 날짜 링크 · 접수서 자동 등록)은
     *   여전히 customers 만 고치고 있었다.
     *   → 고객 카드는 바뀌는데 일정 탭·달력은 그대로 = "수정했는데도 안 바뀐다".
     *   날짜만이 아니라 **시간·기간·주소까지** 통째로 밀어넣어야 한다
     *   (사장님이 시공 기간 10일을 고쳤는데 달력이 그대로였던 것). 2026-09-15
     *
     * '건'을 새로 쌓는 addJob 과 다르다 — 이건 **기존 건의 날짜를 옮기는 것**이다.
     */
    suspend fun syncRepresentativeFromCustomer(customerId: Long, now: Long) {
        val c = customerDao.findById(customerId) ?: return
        val day = c.scheduledWorkDate?.let { com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(it) }
        val jobs = jobDao.scheduledByCustomerOnce(customerId)
        val today = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(now)
        val rep = jobs.firstOrNull { (it.scheduledWorkDate ?: 0L) >= today } ?: jobs.lastOrNull()
        when {
            day == null -> rep?.let { jobDao.update(it.copy(scheduledWorkDate = null, updatedAt = now)) }
            rep != null -> jobDao.update(
                rep.copy(
                    scheduledWorkDate = day,
                    scheduledWorkMinutes = c.scheduledWorkMinutes,
                    scheduledWorkDays = c.scheduledWorkDays.coerceAtLeast(1),
                    address = c.address?.takeIf { it.isNotBlank() } ?: rep.address,
                    updatedAt = now
                )
            )
            // 예정 건이 하나도 없는 고객 — 지금 값으로 새 건을 만든다(중복 가드 포함).
            jobDao.countByCustomerAndDate(customerId, day) == 0 -> jobDao.insert(
                JobEntity(
                    customerId = customerId,
                    scheduledWorkDate = day,
                    scheduledWorkMinutes = c.scheduledWorkMinutes,
                    scheduledWorkDays = c.scheduledWorkDays.coerceAtLeast(1),
                    address = c.address,
                    totalAmount = c.totalAmount,
                    depositAmount = c.depositAmount,
                    depositPaidAt = c.depositPaidAt,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        recomputeMirror(customerId, now)
    }

    /** 이 건을 일정에서만 뺌(고객·기록 보존) — 되돌리기 위해 삭제 대신 시공일만 비운다. */
    suspend fun unscheduleJob(jobId: Long, now: Long) {
        val j = jobDao.findById(jobId) ?: return
        jobDao.update(j.copy(scheduledWorkDate = null, updatedAt = now))
        recomputeMirror(j.customerId, now)
    }

    /** 되돌리기 — 뺀 건의 시공일 복구. */
    suspend fun rescheduleJob(jobId: Long, dayMs: Long, now: Long) {
        val j = jobDao.findById(jobId) ?: return
        jobDao.update(j.copy(scheduledWorkDate = dayMs, updatedAt = now))
        recomputeMirror(j.customerId, now)
    }

    /**
     * CustomerEntity 시공필드 = 그 고객의 **대표 건** 미러 재계산.
     *   대표 = 오늘 이후(미래) 중 가장 가까운 건, 없으면 가장 최근 건.
     *   → CustomerEntity 를 읽는 기존 화면들(홈 히어로·챗·통화전 카드·접수서·미러·브리핑…)이 무변경으로 계속 동작.
     *   ⚠️ Stage A 는 **일정 필드만** 미러링한다. 돈(총액·계약금·잔금)·완료처리는 기존처럼 고객 단위 유지 →
     *      정산·미수금 계산이 그대로 맞음. 건별 정산은 Stage B.
     */
    suspend fun recomputeMirror(customerId: Long, now: Long) {
        val c = customerDao.findById(customerId) ?: return
        val jobs = jobDao.scheduledByCustomerOnce(customerId)
        val today = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(now)
        val rep = jobs.firstOrNull { (it.scheduledWorkDate ?: 0L) >= today } ?: jobs.lastOrNull()
        customerDao.update(
            c.copy(
                scheduledWorkDate = rep?.scheduledWorkDate,
                scheduledWorkMinutes = rep?.scheduledWorkMinutes,
                scheduledWorkDays = (rep?.scheduledWorkDays ?: 1).coerceAtLeast(1),
                address = rep?.address?.takeIf { it.isNotBlank() } ?: c.address,
                updatedAt = now
            )
        )
    }
}
