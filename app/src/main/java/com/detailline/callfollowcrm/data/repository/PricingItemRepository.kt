package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.PricingItemDao
import com.detailline.callfollowcrm.data.local.entity.PricingItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PricingItemRepository(private val dao: PricingItemDao) {

    fun observeActive(): Flow<List<PricingItemEntity>> = dao.observeActive()
    fun observeAll(): Flow<List<PricingItemEntity>> = dao.observeAll()

    /**
     * 활성 가격 항목을 서버 AI 프롬프트용 텍스트로. 비면 "" (서버가 "가격표 없음"으로 처리). (2026-06-17)
     *   업종 무관 — 사장님이 직접 입력한 항목을 그대로. 전역 줄눈 pricing.md 대신 이걸 보냄.
     */
    suspend fun priceListText(): String {
        val items = runCatching { dao.observeActive().first() }.getOrDefault(emptyList())
            .filter { it.title.isNotBlank() }
            .sortedWith(compareBy({ it.category }, { it.displayOrder }))
        if (items.isEmpty()) return ""
        return items.joinToString("\n") { it ->
            val won = formatWon(it.price)
            val unit = if (it.unit == PricingItemEntity.UNIT_PYEONG) " (평당)" else ""
            val cat = when (it.category) { "NEW" -> "[신축] "; "OLD" -> "[구축] "; else -> "" }
            // 가격 기준(basisText)이 있으면 뒤에 괄호로 붙여 "왜 그 가격인지"까지 AI 가 참고. (2026-07-02 문자 기반 추출)
            val basis = it.basisText?.trim().takeUnless { s -> s.isNullOrBlank() }?.let { s -> " ($s)" } ?: ""
            "- $cat${it.title}: $won$unit$basis"
        }
    }

    suspend fun findById(id: Long): PricingItemEntity? = dao.findById(id)
    suspend fun count(): Int = dao.count()

    suspend fun insert(
        title: String,
        price: Long,
        category: String,
        displayOrder: Int = 0,
        unit: String = PricingItemEntity.UNIT_FLAT,
        isEstimated: Boolean = false,
        basisText: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            PricingItemEntity(
                title = title.trim(),
                price = price,
                unit = unit,
                category = category,
                displayOrder = displayOrder,
                isActive = true,
                createdAt = now,
                updatedAt = now,
                isEstimated = isEstimated,
                basisText = basisText?.trim().takeUnless { it.isNullOrBlank() }
            )
        )
    }

    /** 사장님이 값을 고치면 '추정' 해제(= 내가 확인한 값). update 가 항상 isEstimated=false 로 커밋. (2026-07-02 사장님) */
    suspend fun update(entity: PricingItemEntity) {
        dao.update(entity.copy(isEstimated = false, updatedAt = System.currentTimeMillis()))
    }

    /** 문자 추출 결과 한 건 (upsert 입력). ai.PricingExtractRepository.ExtractedItem 을 이 계층 타입으로 변환해 넘김. */
    data class ExtractedPricing(
        val title: String,
        val priceWon: Long,
        val unit: String = PricingItemEntity.UNIT_FLAT,
        val category: String = "COMMON",
        val basisText: String? = null
    )

    /**
     * 문자 기반 추출 결과를 가격표에 반영(2026-07-02). 같은 항목(제목+카테고리)이 있으면 덮어쓰고 없으면 추가.
     *   - 사장님이 이미 확인한(!isEstimated) 항목은 절대 덮지 않음 — 손댄 진짜 값 보호.
     *   - 새로 넣거나 추정값 위에 덮는 건 isEstimated=true → 화면에 "추정" 배지 유지(사장님이 고치면 해제).
     * 스타터 시드(seedEstimatedIfEmpty)와 title 이 겹치면 여기서 실가격/기준으로 갱신된다.
     */
    suspend fun upsertEstimated(items: List<ExtractedPricing>) {
        if (items.isEmpty()) return
        val existing = runCatching { dao.observeAll().first() }.getOrDefault(emptyList())
        val byKey = existing.associateBy { pricingKey(it.title, it.category) }
        var order = (existing.maxOfOrNull { it.displayOrder } ?: -1) + 1
        val now = System.currentTimeMillis()
        for (it in items) {
            if (it.title.isBlank() || it.priceWon <= 0) continue
            val match = byKey[pricingKey(it.title, it.category)]
            if (match != null) {
                if (!match.isEstimated) continue  // 사장님이 확인한 값 보호
                dao.update(
                    match.copy(
                        price = it.priceWon,
                        unit = it.unit,
                        basisText = it.basisText?.trim().takeUnless { s -> s.isNullOrBlank() },
                        isEstimated = true,
                        updatedAt = now
                    )
                )
            } else {
                insert(
                    title = it.title, price = it.priceWon, category = it.category,
                    displayOrder = order++, unit = it.unit, isEstimated = true, basisText = it.basisText
                )
            }
        }
    }

    suspend fun setActive(id: Long, active: Boolean) {
        val item = dao.findById(id) ?: return
        dao.update(item.copy(isActive = active, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    /** 선택한 여러 항목을 한 번에 삭제(가격표 선택 삭제). 빈 목록이면 no-op. (2026-08-04 사장님) */
    suspend fun deleteByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
    }

    companion object {
        /**
         * 원 단위 가격 → 표시 문자열. 만원 배수면 "N만원", 아니면 "N,NNN원".
         *   ⚠️ price 는 항상 '원 단위'라는 계약을 검증하는 순수함수(단위테스트). 서버/시드가 원 단위로 주고
         *   앱은 ×10000 을 두 번 하지 않는다는 것을 여기 테스트로 못박음. (2026-07-02 가격 온보딩)
         */
        fun formatWon(price: Long): String =
            if (price >= 10000 && price % 10000L == 0L) "${price / 10000}만원"
            else "${"%,d".format(price)}원"

        /**
         * 항목 dedup 키 — 공백 제거 + 소문자 제목 + 카테고리. 문자 추출 upsert 매칭용. (2026-07-02)
         *   "샤워부스 벽 3면"/COMMON 과 "샤워부스벽3면"/COMMON 을 같은 항목으로 본다.
         */
        fun pricingKey(title: String, category: String): String =
            title.replace(Regex("\\s+"), "").lowercase() + "|" + category.uppercase()
    }
}

/** PricingItemEntity.category 의 enum 값 — DB 에 string 으로 저장. */
enum class PricingCategory(val label: String) {
    NEW("신축"),
    OLD("구축"),
    COMMON("공통")
}
