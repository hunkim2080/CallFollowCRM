package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CallSummaryDao
import com.detailline.callfollowcrm.data.local.dao.CustomerDao
import com.detailline.callfollowcrm.data.local.dao.RecordingAttachmentDao
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.domain.model.LeadHeat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CustomerRepository(
    private val dao: CustomerDao,
    // 새 Customer 가 만들어질 때 같은 번호의 orphan 첨부/요약을 자동 연결하기 위해 주입.
    // 테스트 편의를 위해 nullable 로 두고, 운영 코드(AppContainer)에서는 항상 채워준다.
    private val recordingDao: RecordingAttachmentDao? = null,
    private val callSummaryDao: CallSummaryDao? = null
) {

    fun observeAll(): Flow<List<CustomerEntity>> = dao.observeAll()
    fun observeById(id: Long): Flow<CustomerEntity?> = dao.observeById(id)

    // 2026-05-25: observeByStatusLabel 제거 — PipelineScreen 폐기 + status enum 폐기.

    /** 시공 예약일이 설정된 모든 고객을 예약일 오름차순으로. */
    fun observeScheduled(): Flow<List<CustomerEntity>> = dao.observeScheduled()

    /**
     * P3 — 사장님의 다른 시공 일정 (현재 고객 제외, 오늘부터 N일 내) 만 epoch ms 로.
     * AI 답변 추천 / 대화 요약의 일정 응답 근거.
     * 다른 고객 이름은 leak 금지 → ms 만 반환. 서버가 요일/오전오후로 가공.
     */
    suspend fun getOtherUpcomingScheduleDates(
        excludePhone: String,
        daysAhead: Int = 14
    ): List<Long> {
        val now = System.currentTimeMillis()
        val cutoff = now + daysAhead * 24L * 3600L * 1000L
        return runCatching {
            observeScheduled().first()
                .asSequence()
                .filter { it.phoneNumber != excludePhone }
                .mapNotNull { it.scheduledWorkDate }
                .filter { it in now..cutoff }
                .sorted()
                .toList()
        }.getOrDefault(emptyList())
    }

    suspend fun findByPhone(phoneNumber: String): CustomerEntity? = dao.findByPhone(phoneNumber)
    suspend fun findById(id: Long): CustomerEntity? = dao.findById(id)

    /**
     * 같은 전화번호로 들어오면 기존 Customer를 재사용한다.
     * 2026-05-25: status 컬럼 v13 마이그레이션에서 drop — 카테고리 시스템으로 통일.
     */
    suspend fun upsertByPhone(
        phoneNumber: String,
        name: String? = null,
        memo: String? = null,
        leadHeat: LeadHeat? = null
    ): CustomerEntity {
        val now = System.currentTimeMillis()
        val existing = dao.findByPhone(phoneNumber)
        return if (existing == null) {
            val entity = CustomerEntity(
                phoneNumber = phoneNumber,
                name = name,
                memo = memo.orEmpty(),
                leadHeat = leadHeat?.name,
                createdAt = now,
                updatedAt = now
            )
            val id = dao.insert(entity)
            // 같은 번호로 먼저 들어와 있던 orphan 녹음/요약을 이 고객으로 자동 연결.
            // (정책: 녹음/요약 import 는 Customer 를 자동 생성하지 않는다.)
            recordingDao?.linkOrphansToCustomer(phoneNumber, id)
            callSummaryDao?.linkOrphansToCustomer(phoneNumber, id)
            entity.copy(id = id)
        } else {
            val mergedMemo = when {
                memo.isNullOrBlank() -> existing.memo
                existing.memo.isBlank() -> memo
                else -> existing.memo + "\n---\n" + memo
            }
            val updated = existing.copy(
                name = name ?: existing.name,
                memo = mergedMemo,
                leadHeat = leadHeat?.name ?: existing.leadHeat,
                updatedAt = now
            )
            dao.update(updated)
            updated
        }
    }

    // 2026-05-25: updateStatus 제거 — 카테고리 시스템으로 통일.

    suspend fun updateMemo(id: Long, memo: String) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(memo = memo, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateName(id: Long, name: String?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    /** 리드 온도 설정/변경/해제(null). 통화 직후 카드에서 optimistic 저장 호출. */
    suspend fun updateLeadHeat(id: Long, leadHeat: LeadHeat?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(leadHeat = leadHeat?.name, updatedAt = System.currentTimeMillis()))
    }

    /**
     * 계약금 정보 갱신. amount 또는 paidAt 둘 중 하나만 변경하고 싶을 때 다른 인자에
     * `Unchanged` 를 넘기면 보존 (Kotlin 의 null 은 "값 지움" 으로 쓰이기 때문).
     */
    suspend fun updateDepositAmount(id: Long, amount: Long?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(depositAmount = amount, updatedAt = System.currentTimeMillis()))
    }

    /** paidAt = null 이면 "안 받음" 상태로 되돌리기. */
    suspend fun updateDepositPaidAt(id: Long, paidAt: Long?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(depositPaidAt = paidAt, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateBalanceAmount(id: Long, amount: Long?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(balanceAmount = amount, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateBalancePaidAt(id: Long, paidAt: Long?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(balancePaidAt = paidAt, updatedAt = System.currentTimeMillis()))
    }

    /** 시공 예약일 설정/변경/취소(null). 자정 epoch ms 권장. */
    suspend fun updateScheduledWorkDate(id: Long, scheduledWorkDate: Long?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(scheduledWorkDate = scheduledWorkDate, updatedAt = System.currentTimeMillis()))
    }

    /**
     * 현장 주소 설정/변경/지움(null) — 사장님 수동 등록 (2026-05-28, DB v15).
     *   AddressExtractor 자동 추출보다 우선. 길찾기/§13 가 이 값을 1순위로 활용.
     */
    suspend fun updateAddress(id: Long, address: String?) {
        val c = dao.findById(id) ?: return
        val trimmed = address?.trim()?.takeIf { it.isNotEmpty() }
        dao.update(c.copy(address = trimmed, updatedAt = System.currentTimeMillis()))
    }

    /** 자동 생성된 미사용 고객만 정리 (이름/메모/문자기록 없음). */
    suspend fun deleteOrphans(): Int = dao.deleteOrphans()
}
