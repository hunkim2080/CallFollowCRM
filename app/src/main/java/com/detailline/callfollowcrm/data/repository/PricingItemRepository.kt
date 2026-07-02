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
            "- $cat${it.title}: $won$unit"
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
        isEstimated: Boolean = false
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
                isEstimated = isEstimated
            )
        )
    }

    /** 사장님이 값을 고치면 '추정' 해제(= 내가 확인한 값). update 가 항상 isEstimated=false 로 커밋. (2026-07-02 사장님) */
    suspend fun update(entity: PricingItemEntity) {
        dao.update(entity.copy(isEstimated = false, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setActive(id: Long, active: Boolean) {
        val item = dao.findById(id) ?: return
        dao.update(item.copy(isActive = active, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
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
    }
}

/** PricingItemEntity.category 의 enum 값 — DB 에 string 으로 저장. */
enum class PricingCategory(val label: String) {
    NEW("신축"),
    OLD("구축"),
    COMMON("공통")
}
