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
     * 🧹 **자동 카테고리 두 개를 지워낸다.** 1회. (2026-09-20 사장님 확정)
     *
     * ⚠️ **사장님이 직접 만든 분류는 한 개도 안 건드린다.**
     *    이름이 정확히 "시공 대기"·"시공 완료" 인 **그 두 줄**과, 거기 묶인 연결만 푼다.
     *    (사장님이 우연히 같은 이름으로 만드셨더라도 뜻이 같으니 지워도 손해가 없다.)
     *
     * 순서가 중요하다 — **연결을 먼저 풀고** 카테고리를 지운다. 반대로 하면
     * 고객이 없는 카테고리를 가리키는 채로 남아 이름이 안 뜨는 유령 딱지가 된다.
     *
     * @return 분류를 뗀 고객 수
     */
    suspend fun removeAutoCategories(): Int {
        val ids = listOfNotNull(
            categoryRepository.findByName(DefaultCategories.NAME_PENDING_WORK)?.id,
            categoryRepository.findByName(DefaultCategories.NAME_DONE_WORK)?.id
        )
        if (ids.isEmpty()) return 0
        var count = 0
        for (c in customerRepository.allOnce()) {
            if (c.categoryId in ids) {
                categoryRepository.assignCustomer(c.id, null)
                count++
            }
        }
        for (id in ids) categoryRepository.delete(id)
        return count
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
        // 모든 고객 순회 — 날짜 등록/입금 기준으로 시공대기·완료 자동분류 + 잘못 분류된 건 미분류로 정리.
        val all = customerRepository.allOnce()
        for (c in all) {
            val canAutoClassify = c.categoryId == null
                || c.categoryId == pendingId
                || c.categoryId == doneId
            if (!canAutoClassify) continue   // 사장님 수동 카테고리는 절대 안 건드림
            val depositPaid = (c.depositAmount ?: 0L) > 0L
            // 완료 판정은 단일 출처 isWorkDone(잔금 받음 balancePaidAt / 완료처리 workCompletedAt).
            //   balanceAmount(잔금 '액수' — 견적 넣으면 총액-계약금이 자동 기록)를 '받음'으로 착각하면
            //   계약금만 넣은 시공-예정 고객이 '시공 완료'로 오분류됨. (2026-08-31 사장님 신고)
            val done = c.isWorkDone
            val scheduled = (c.scheduledWorkDate ?: 0L) > 0L
            val target: Long? = when {
                done -> doneId
                scheduled || depositPaid -> pendingId
                c.categoryId == pendingId || c.categoryId == doneId -> null  // 자격 없는데 상태칸 → 미분류
                else -> c.categoryId
            }
            if (target != c.categoryId) {
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
        // ❌ **자동 분류 중단.** (2026-09-20) 두 카테고리가 사라졌으므로 찾아도 null 이고,
        //    아래 when 은 `else -> customer.categoryId` 로 떨어져 **아무것도 안 바꾼다.**
        //    코드를 남겨둔 건 되돌릴 때를 위해서다 — 지금은 사실상 no-op.
        val pendingId = categoryRepository.findByName(DefaultCategories.NAME_PENDING_WORK)?.id
        val doneId = categoryRepository.findByName(DefaultCategories.NAME_DONE_WORK)?.id

        // 사장님이 수동으로 다른 카테고리 지정했으면 자동 분류 X.
        val canAutoClassify = customer.categoryId == null
            || customer.categoryId == pendingId
            || customer.categoryId == doneId
        if (!canAutoClassify) return customer.categoryId

        val depositPaid = (customer.depositAmount ?: 0L) > 0L
        // 완료 = 단일 출처 isWorkDone(잔금 받음 balancePaidAt / 완료처리 workCompletedAt).
        //   balanceAmount(잔금 '액수')는 '받음'이 아님 → 계약금 고객이 완료로 오분류되던 버그 방지. (2026-08-31 사장님)
        val done = customer.isWorkDone
        // 2026-06-07 사장님 정의: 계약 = 시공일(날짜) 등록 → "시공 대기". (상담만 = 미분류)
        val scheduled = (customer.scheduledWorkDate ?: 0L) > 0L

        return when {
            done -> doneId ?: customer.categoryId          // 잔금 받음/완료처리 → 시공 완료
            scheduled || depositPaid -> pendingId ?: customer.categoryId  // 날짜 등록(계약) 또는 계약금 → 시공 대기
            // 자격 없음: 상태 카테고리에 잘못 들어가 있던 고객은 미분류로 되돌림. (상담만 한 고객 정리)
            customer.categoryId == pendingId || customer.categoryId == doneId -> null
            else -> customer.categoryId  // 원래 미분류 → 그대로
        }
    }
}
