package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.seed.DefaultCategories

/**
 * 2026-05-30 사장님 #7 통점 — 입금 상태 기반 고객 자동 카테고리 분류.
 *
 * 사장님 결정 룰:
 *   - balanceAmount > 0 → "시공 완료" 카테고리
 *   - depositAmount > 0 && (balanceAmount == null || balanceAmount == 0) → "시공 대기" 카테고리
 *   - 둘 다 0/null → 자동 분류 X (기존 categoryId 그대로)
 *
 * 수동 우선:
 *   - 고객 의 현재 categoryId 가 null / 시공 대기 ID / 시공 완료 ID 일 때만 자동 분류
 *   - 사장님이 다른 카테고리 (예: "VIP", "AS 진행") 지정했으면 자동 분류 절대 X
 */
class AutoCategoryClassifier(
    private val categoryRepository: CategoryRepository,
    private val customerRepository: CustomerRepository
) {

    /**
     * 사장님이 입금 정보 변경했을 때 호출. 자동 분류 결과대로 categoryId 갱신.
     * 변경 없으면 no-op (DB 비용 절약).
     */
    suspend fun reclassify(customerId: Long) {
        val customer = customerRepository.findById(customerId) ?: return
        val newId = resolveCategoryId(customer)
        if (newId != customer.categoryId) {
            categoryRepository.assignCustomer(customerId, newId)
        }
    }

    /**
     * Application 첫 진입 시 사장님 옛 고객들 일괄 분류 (이미 입금 됐던 케이스).
     * preferences flag (autoCategorySeeded) 로 1회만 실행.
     * @return 영향 받은 고객 수
     */
    suspend fun backfillAll(): Int {
        val pendingId = categoryRepository.findByName(DefaultCategories.NAME_PENDING_WORK)?.id
        val doneId = categoryRepository.findByName(DefaultCategories.NAME_DONE_WORK)?.id
        var count = 0
        // 모든 고객 순회 — 입금된 케이스만 자동 분류.
        val all = customerRepository.allOnce()
        for (c in all) {
            val depositPaid = (c.depositAmount ?: 0L) > 0L
            val balancePaid = (c.balanceAmount ?: 0L) > 0L
            if (!depositPaid && !balancePaid) continue
            val canAutoClassify = c.categoryId == null
                || c.categoryId == pendingId
                || c.categoryId == doneId
            if (!canAutoClassify) continue
            val target = if (balancePaid) doneId else pendingId
            if (target != null && target != c.categoryId) {
                categoryRepository.assignCustomer(c.id, target)
                count++
            }
        }
        return count
    }

    /**
     * 다음 categoryId 결정. customer 의 현재 상태 + categoryId 보고 자동 분류 적용.
     * @return 새 categoryId (null = 미분류). 변경 안 함 결정 시 = 현재 customer.categoryId 반환.
     */
    suspend fun resolveCategoryId(customer: CustomerEntity): Long? {
        val pendingId = categoryRepository.findByName(DefaultCategories.NAME_PENDING_WORK)?.id
        val doneId = categoryRepository.findByName(DefaultCategories.NAME_DONE_WORK)?.id

        // 사장님이 수동으로 다른 카테고리 지정했으면 자동 분류 X.
        val canAutoClassify = customer.categoryId == null
            || customer.categoryId == pendingId
            || customer.categoryId == doneId
        if (!canAutoClassify) return customer.categoryId

        val depositPaid = (customer.depositAmount ?: 0L) > 0L
        val balancePaid = (customer.balanceAmount ?: 0L) > 0L

        return when {
            balancePaid -> doneId ?: customer.categoryId
            depositPaid -> pendingId ?: customer.categoryId
            else -> customer.categoryId  // 둘 다 미입금 — 자동 분류 X (기존 그대로)
        }
    }
}
