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

    /**
     * ❌ **더 이상 안 만든다.** (2026-09-20 사장님 "시공대기와 시공완료는 카테고리에서 없애는게 맞는거같아")
     *
     * 이 둘은 앱이 **일정·돈만 보면 알 수 있는 것**이라 상담함 칩과 상태 딱지가 이미 말하고 있었다.
     * 카테고리로 또 만들어 놓으니 같은 사람을 세 이름으로 부르게 됐고, 결국 화면 네 곳에서
     * **안 보이게 숨기는 코드**를 달아야 했다.
     *
     * 이름 상수는 남긴다 — 이미 깔린 폰에서 **지워내는 데** 쓴다([AutoCategoryClassifier.removeAutoCategories]).
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun seedIfMissing(repo: CategoryRepository) {
        // 의도적으로 아무것도 안 함.
    }
}
