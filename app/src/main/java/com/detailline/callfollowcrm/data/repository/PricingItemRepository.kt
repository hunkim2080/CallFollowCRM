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
            val won = if (it.price >= 10000 && it.price % 10000L == 0L) "${it.price / 10000}만원"
                      else "${"%,d".format(it.price)}원"
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
        unit: String = PricingItemEntity.UNIT_FLAT
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
                updatedAt = now
            )
        )
    }

    suspend fun update(entity: PricingItemEntity) {
        dao.update(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun setActive(id: Long, active: Boolean) {
        val item = dao.findById(id) ?: return
        dao.update(item.copy(isActive = active, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }
}

/** PricingItemEntity.category 의 enum 값 — DB 에 string 으로 저장. */
enum class PricingCategory(val label: String) {
    NEW("신축"),
    OLD("구축"),
    COMMON("공통")
}
