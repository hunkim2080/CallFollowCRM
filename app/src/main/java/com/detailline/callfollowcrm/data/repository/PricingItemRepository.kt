package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.PricingItemDao
import com.detailline.callfollowcrm.data.local.entity.PricingItemEntity
import kotlinx.coroutines.flow.Flow

class PricingItemRepository(private val dao: PricingItemDao) {

    fun observeActive(): Flow<List<PricingItemEntity>> = dao.observeActive()
    fun observeAll(): Flow<List<PricingItemEntity>> = dao.observeAll()

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
