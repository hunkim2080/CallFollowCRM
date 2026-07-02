package com.detailline.callfollowcrm.data.local.seed

import com.detailline.callfollowcrm.data.local.entity.PricingItemEntity
import com.detailline.callfollowcrm.data.repository.PricingCategory
import com.detailline.callfollowcrm.data.repository.PricingItemRepository

/**
 * pricing.md (사장님이 서버 LLM 용으로 유지하는 가격표) 와 동일한 항목으로 초기 시드.
 * 사장님이 추가/수정/삭제 자유. 시드는 처음 한 번만 (count() == 0).
 */
object DefaultPricingItems {

    /**
     * 업종 스타터(가격 온보딩, 2026-07-02 사장님) — 가격표가 비어있고 업종이 줄눈이면 18항목을 '추정값'으로 자동 시드.
     *   isEstimated=true → 가격표 화면에 "추정" 배지 + 사장님이 값 고치면 해제. 타 업종은 서버 /pricing/starter(cowork) 대기.
     *   빈 표로 시작해 이탈하던 온보딩을 "이미 채워진 표"로 시작하게 함. docs/PLAN_price_onboarding.md
     */
    suspend fun seedEstimatedIfEmpty(repo: PricingItemRepository, trade: String?) {
        if (repo.count() > 0) return
        // 온보딩 15개 업종 전부 DefaultTradeTemplates 로 커버(줄눈 포함). 여기 없는 커스텀 업종은 빈 리스트 → 서버 대기.
        val items = DefaultTradeTemplates.forTrade(trade)
        items.forEachIndexed { idx, it ->
            repo.insert(
                title = it.title, price = it.priceWon, category = it.category.name,
                displayOrder = idx, unit = it.unit, isEstimated = true
            )
        }
    }

    suspend fun seedIfEmpty(repo: PricingItemRepository) {
        if (repo.count() > 0) return
        ZULNUN_ITEMS.forEachIndexed { idx, (cat, title, price) ->
            repo.insert(title = title, price = price, category = cat.name, displayOrder = idx)
        }
    }

    private val ZULNUN_ITEMS: List<Triple<PricingCategory, String, Long>> by lazy {
        listOf(
            // 신축
            Triple(PricingCategory.NEW, "욕조 있는 화장실 바닥 1곳", 400_000L),
            Triple(PricingCategory.NEW, "샤워부스 있는 화장실 바닥 1곳", 450_000L),
            Triple(PricingCategory.NEW, "샤워부스 벽 3면", 350_000L),
            Triple(PricingCategory.NEW, "욕조벽 3면", 350_000L),
            Triple(PricingCategory.NEW, "화장실 전체 벽 (추가 시)", 700_000L),
            Triple(PricingCategory.NEW, "세탁실 (폴리우레아)", 150_000L),
            Triple(PricingCategory.NEW, "베란다 (폴리우레아)", 150_000L),
            Triple(PricingCategory.NEW, "현관", 50_000L),
            Triple(PricingCategory.NEW, "거실 타일", 1_500_000L),
            // 구축
            Triple(PricingCategory.OLD, "욕조 있는 화장실 바닥 1곳", 500_000L),
            Triple(PricingCategory.OLD, "샤워부스 있는 화장실 바닥 1곳", 550_000L),
            Triple(PricingCategory.OLD, "샤워부스 벽 3면", 350_000L),
            Triple(PricingCategory.OLD, "욕조벽 3면", 350_000L),
            Triple(PricingCategory.OLD, "화장실 전체 벽 (추가 시)", 700_000L),
            Triple(PricingCategory.OLD, "세탁실 (폴리우레아)", 150_000L),
            Triple(PricingCategory.OLD, "베란다 (폴리우레아)", 150_000L),
            Triple(PricingCategory.OLD, "현관", 100_000L),
            Triple(PricingCategory.OLD, "거실 타일", 1_500_000L)
        )
    }
}
