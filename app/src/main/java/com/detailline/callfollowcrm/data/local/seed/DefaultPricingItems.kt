package com.detailline.callfollowcrm.data.local.seed

import com.detailline.callfollowcrm.data.local.entity.PricingItemEntity
import com.detailline.callfollowcrm.data.repository.PricingCategory
import com.detailline.callfollowcrm.data.repository.PricingItemRepository

/**
 * pricing.md (사장님이 서버 LLM 용으로 유지하는 가격표) 와 동일한 항목으로 초기 시드.
 * 사장님이 추가/수정/삭제 자유. 시드는 처음 한 번만 (count() == 0).
 */
object DefaultPricingItems {

    suspend fun seedIfEmpty(repo: PricingItemRepository) {
        if (repo.count() > 0) return

        val items: List<Triple<PricingCategory, String, Long>> = listOf(
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

        items.forEachIndexed { idx, (cat, title, price) ->
            repo.insert(
                title = title,
                price = price,
                category = cat.name,
                displayOrder = idx
            )
        }
    }
}
