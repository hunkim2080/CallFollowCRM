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

    suspend fun allOnce(): List<JobEntity> = jobDao.allOnce()

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
                    cancelledAt = null,   // 날짜를 다시 잡았으면 취소가 아니다. (2026-09-18)
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

    /** 되돌리기 — 뺀 건의 시공일 복구. 날짜가 다시 생기면 '취소' 표시도 지운다. */
    suspend fun rescheduleJob(jobId: Long, dayMs: Long, now: Long) {
        val j = jobDao.findById(jobId) ?: return
        jobDao.update(j.copy(scheduledWorkDate = dayMs, cancelledAt = null, updatedAt = now))
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

    /**
     * 이 **건 하나만** 취소 — 날짜와 돈을 그 전표에서만 지운다. (2026-09-18)
     *
     * 🔴 왜 필요한가 (연결부 점검에서 발견)
     *   전엔 취소가 고객 카드의 돈을 지웠다. 그런데 날짜를 비우는 순간 미러가 **다음 건**을
     *   대표로 잡기 때문에, 그 "백지" 가 **다음 건(예: 1차) 전표에 찍혔다.**
     *   → 2차를 취소했는데 1차 잔금 기록이 사라지는 구조였다.
     *   그래서 취소는 **건을 지목해서** 해야 한다.
     *
     * 사장님 규칙(2026-08-28): "취소하면 금액도 다 없어져야" — 그 건의 돈만 백지로.
     */
    suspend fun cancelJob(jobId: Long, now: Long) {
        val j = jobDao.findById(jobId) ?: return
        jobDao.update(
            j.copy(
                scheduledWorkDate = null,
                totalAmount = null,
                depositAmount = null,
                depositPaidAt = null,
                balanceAmount = null,
                balancePaidAt = null,
                // 취소했다고 적어둔다 — 안 적으면 '아직 날짜를 안 정한 새 건'과 구별이 안 된다. (2026-09-18)
                cancelledAt = now,
                updatedAt = now
            )
        )
        recomputeMirror(j.customerId, now)
        syncMoneyFromRepresentative(j.customerId, now)
    }

    /**
     * 고객 카드의 돈을 **남은 대표 건**의 돈으로 맞춘다. 지금은 '건 취소' 직후에만 쓴다.
     *
     * 왜 recomputeMirror 안에 안 넣었나: 정산 **목록**이 아직 고객 카드(customers)를 읽는다.
     *   미러가 항상 돈을 덮으면, 금액이 안 적힌 2차가 대표가 되는 순간 **1차 미수가 정산 목록에서 사라진다.**
     *   (`JobRepositoryStageATest '미러는 돈을 건드리지 않는다 - 정산 보호'` 가 이걸 지키고 있다.)
     *   정산 목록을 건 단위로 바꾼 뒤에 recomputeMirror 로 합칠 것 — docs/PLAN_job_centric_migration.md §4.
     *
     * 취소 직후에만 필요한 이유: 취소하면 대표가 **다른 건으로 바뀌는데**, 고객 카드엔 방금 취소한
     *   건의 돈이 그대로 남아 "취소했는데 금액이 남아 있다" 가 된다. 남은 건이 없으면 손대지 않는다
     *   (호출부가 '완전 백지' 를 따로 처리한다).
     */
    /**
     * 고객 카드의 돈을 **대표 건 값으로** 맞춘다 (jobs → customers, 한 방향).
     *   반대 방향(customers → 대표 건)으로 쓰면 **엉뚱한 건의 돈을 덮는다.**
     *   실제 사고(2026-09-18): 일정 등록으로 2차(11/17·90만)를 넣었더니
     *   고객 카드에 90만이 써졌고, 그게 대표 건인 **1차(10/20)** 전표로 미러링돼
     *   1차 금액 50만이 90만으로 바뀌었다.
     */
    suspend fun syncMoneyFromRepresentative(customerId: Long, now: Long) {
        val c = customerDao.findById(customerId) ?: return
        val jobs = jobDao.scheduledByCustomerOnce(customerId)
        if (jobs.isEmpty()) return
        val today = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(now)
        val rep = jobs.firstOrNull { (it.scheduledWorkDate ?: 0L) >= today } ?: jobs.last()
        customerDao.update(
            c.copy(
                totalAmount = rep.totalAmount,
                depositAmount = rep.depositAmount,
                depositPaidAt = rep.depositPaidAt,
                balanceAmount = rep.balanceAmount,
                balancePaidAt = rep.balancePaidAt,
                updatedAt = now
            )
        )
    }

    /**
     * 이 **건 하나**의 돈을 고친다. (2026-09-18 · docs/PLAN_job_centric_migration.md Step 2)
     *   null 을 넘기면 "안 바꿈"이 아니라 **그 값으로 지정**이다(지우려면 null).
     *   고객 카드(미러)는 대표 건일 때만 따라간다 — recomputeMirror 가 일정만 옮기므로
     *   돈은 호출부가 필요할 때 CustomerRepository 로 같이 쓴다(Step 3 에서 한 방향으로 정리).
     */
    suspend fun updateMoney(
        jobId: Long,
        totalAmount: Long?,
        depositAmount: Long?,
        depositPaidAt: Long?,
        balanceAmount: Long?,
        balancePaidAt: Long?,
        now: Long = System.currentTimeMillis()
    ) {
        val j = jobDao.findById(jobId) ?: return
        jobDao.update(
            j.copy(
                totalAmount = totalAmount,
                depositAmount = depositAmount,
                depositPaidAt = depositPaidAt,
                balanceAmount = balanceAmount,
                balancePaidAt = balancePaidAt,
                updatedAt = now
            )
        )
    }

    /**
     * 이 **건 하나**를 완료 처리(또는 되돌리기). (2026-09-18)
     *   전엔 완료가 고객 카드에만 찍혀서, 건 탭의 "완료" 표시가 틀릴 수 있었다
     *   (jobs.workCompletedAt 은 마이그레이션·아카이브 때만 채워졌다).
     */
    suspend fun setWorkCompleted(jobId: Long, at: Long?, now: Long = System.currentTimeMillis()) {
        val j = jobDao.findById(jobId) ?: return
        jobDao.update(j.copy(workCompletedAt = at, updatedAt = now))
    }

    /** 이 **건 하나**의 현장 주소. 건마다 현장이 다르다(1차 수원 / 2차 강남). */
    /**
     * **주소만 든 새 건**(날짜 미정). (2026-09-18 확정 프로토 ⑤)
     *   마무리된 건 뒤에 문자에서 새 주소가 잡혔을 때 "새 시공으로 잡기" 가 부르는 것.
     *   날짜는 사장님이 나중에 잡는다 — 그래서 같은 날 중복 가드가 필요 없다.
     */
    suspend fun addDraftJob(customerId: Long, address: String?, now: Long = System.currentTimeMillis()): Long {
        val id = jobDao.insert(
            JobEntity(
                customerId = customerId,
                scheduledWorkDate = null,
                address = address?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now
            )
        )
        recomputeMirror(customerId, now)
        return id
    }

    suspend fun updateAddress(jobId: Long, address: String?, now: Long = System.currentTimeMillis()) {
        val j = jobDao.findById(jobId) ?: return
        jobDao.update(j.copy(address = address?.trim()?.takeIf { it.isNotBlank() }, updatedAt = now))
        recomputeMirror(j.customerId, now)
    }

    /** 이 **건 하나**의 메모. 고객 전체 메모(customers.memo)와 다른 것 — 주차·열쇠·자재처럼 현장 것. */
    suspend fun updateMemo(jobId: Long, memo: String, now: Long = System.currentTimeMillis()) {
        val j = jobDao.findById(jobId) ?: return
        jobDao.update(j.copy(memo = memo, updatedAt = now))
    }

    /** 이 건의 계약금 받음/안받음. (2026-09-18) */
    suspend fun setDepositPaid(jobId: Long, at: Long?, now: Long = System.currentTimeMillis()) {
        val j = jobDao.findById(jobId) ?: return
        jobDao.update(j.copy(depositPaidAt = at, updatedAt = now))
    }

    /** 이 건의 잔금 받음/안받음. 잔금 액수가 비어 있으면 (총액-계약금)으로 채운다. */
    suspend fun setBalancePaid(jobId: Long, at: Long?, now: Long = System.currentTimeMillis()) {
        val j = jobDao.findById(jobId) ?: return
        val filled = if (at != null && j.balanceAmount == null) {
            ((j.totalAmount ?: 0L) - (j.depositAmount ?: 0L)).coerceAtLeast(0L).takeIf { it > 0L }
        } else j.balanceAmount
        // **잔금을 받으면 그 건은 마무리.** (2026-09-18 사장님 확정 · 프로토)
        //   "잔금 받으면 완료 처리는 자동." 이미 완료일이 있으면 존중(안 덮음).
        //   되돌릴 땐(at=null) 완료는 안 건드린다 — 시공 완료는 따로 되돌리는 자리가 있다.
        val done = if (at != null) (j.workCompletedAt ?: at) else j.workCompletedAt
        jobDao.update(j.copy(balancePaidAt = at, balanceAmount = filled, workCompletedAt = done, updatedAt = now))
    }

    /** 그 고객의 **대표 건**(고객 카드가 지금 보여주는 건) id. 없으면 null. */
    suspend fun representativeJobId(customerId: Long, now: Long = System.currentTimeMillis()): Long? {
        val jobs = jobDao.scheduledByCustomerOnce(customerId)
        if (jobs.isEmpty()) return null
        val today = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(now)
        return (jobs.firstOrNull { (it.scheduledWorkDate ?: 0L) >= today } ?: jobs.last()).id
    }

    /** 이 고객에게 시공일이 잡힌 건이 하나라도 남아 있나. 취소 후 '고객 카드도 백지로 할지' 판단용. */
    suspend fun hasScheduledJob(customerId: Long): Boolean =
        jobDao.scheduledByCustomerOnce(customerId).isNotEmpty()
}
