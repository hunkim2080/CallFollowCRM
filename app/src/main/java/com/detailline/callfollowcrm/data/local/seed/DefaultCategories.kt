package com.detailline.callfollowcrm.data.local.seed

import com.detailline.callfollowcrm.data.repository.CategoryRepository

/**
 * 2026-05-30 사장님 #7 통점 — 기본 카테고리 seed.
 *
 * 사장님 결정 정의:
 *   - "시공 대기 고객" = 계약금 넣었거나 시공일 잡힌 고객 (아직 완료 전)
 *   - "시공 완료 고객" = 잔금 받았거나(balancePaidAt) 완료 처리한(workCompletedAt) 고객 = CustomerEntity.isWorkDone
 *     ⚠️ balanceAmount(잔금 '액수')는 '받음'이 아님 — 견적/총액 넣으면 (총액-계약금)이 자동 기록되므로
 *        완료 판정에 쓰면 계약금만 넣은 시공-예정 고객이 완료로 오분류됨. (2026-08-31 사장님 신고 fix)
 *
 * Application.onCreate 의 appScope.launch 에서 seedIfMissing 호출.
 * idempotent — 이미 있으면 skip (CategoryRepository.upsert 가 findByName 우선).
 */
object DefaultCategories {

    /** 카테고리 이름과 이모지. AutoCategoryClassifier 에서 같은 상수 참조. */
    const val NAME_PENDING_WORK = "시공 대기"
    const val EMOJI_PENDING_WORK = "🔨"

    const val NAME_DONE_WORK = "시공 완료"
    const val EMOJI_DONE_WORK = "✅"

    suspend fun seedIfMissing(repo: CategoryRepository) {
        // CategoryRepository.upsert 가 같은 이름 존재 시 기존 반환 (idempotent).
        repo.upsert(name = NAME_PENDING_WORK, emoji = EMOJI_PENDING_WORK)
        repo.upsert(name = NAME_DONE_WORK, emoji = EMOJI_DONE_WORK)
    }
}
