package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.MessageTemplateDao
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import kotlinx.coroutines.flow.Flow

class MessageTemplateRepository(private val dao: MessageTemplateDao) {
    fun observeAll(): Flow<List<MessageTemplateEntity>> = dao.observeAll()
    fun observeActive(): Flow<List<MessageTemplateEntity>> = dao.observeActive()
    fun observeById(id: Long): Flow<MessageTemplateEntity?> = dao.observeById(id)

    suspend fun count(): Int = dao.count()
    suspend fun findById(id: Long): MessageTemplateEntity? = dao.findById(id)

    suspend fun insert(template: MessageTemplateEntity): Long = dao.insert(template)

    suspend fun update(template: MessageTemplateEntity) =
        dao.update(template.copy(updatedAt = System.currentTimeMillis()))
}
