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
}
