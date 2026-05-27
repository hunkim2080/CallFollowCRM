package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CategoryDao
import com.detailline.callfollowcrm.data.local.dao.CustomerDao
import com.detailline.callfollowcrm.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 갤메시지 식 카테고리 CRUD + 고객 ↔ 카테고리 연결.
 * AI 자동 분류는 ChatViewModel 이 conversation-summary 결과로 호출 (별도 hook).
 */
class CategoryRepository(
    private val dao: CategoryDao,
    private val customerDao: CustomerDao
) {
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()

    suspend fun findById(id: Long): CategoryEntity? = dao.findById(id)
    suspend fun findByName(name: String): CategoryEntity? = dao.findByName(name)

    /**
     * 카테고리 생성. name 중복 시 기존 entity 반환 (idempotent).
     * AI 가 같은 이름 제안하는 케이스에서도 안전.
     */
    suspend fun upsert(name: String, emoji: String? = null): CategoryEntity {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "카테고리 이름이 비어있어요" }
        dao.findByName(trimmed)?.let { return it }
        val now = System.currentTimeMillis()
        val entity = CategoryEntity(
            name = trimmed,
            emoji = emoji,
            displayOrder = dao.nextDisplayOrder(),
            createdAt = now,
            updatedAt = now
        )
        val id = dao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun rename(id: Long, newName: String) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(name = newName.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun setEmoji(id: Long, emoji: String?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(emoji = emoji, updatedAt = System.currentTimeMillis()))
    }

    /** 카테고리 삭제 + 그 카테고리에 속해있던 고객들을 미분류로 되돌림. */
    suspend fun delete(id: Long) {
        val c = dao.findById(id) ?: return
        customerDao.clearCategoryAssignment(id, System.currentTimeMillis())
        dao.delete(c)
    }

    /** 고객을 카테고리에 박기. categoryId = null 이면 미분류로 되돌림. */
    suspend fun assignCustomer(customerId: Long, categoryId: Long?) {
        customerDao.setCategoryId(customerId, categoryId, System.currentTimeMillis())
    }
}
