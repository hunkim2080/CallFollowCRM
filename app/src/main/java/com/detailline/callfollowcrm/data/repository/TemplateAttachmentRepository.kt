package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.TemplateAttachmentDao
import com.detailline.callfollowcrm.data.local.entity.TemplateAttachmentEntity
import kotlinx.coroutines.flow.Flow

class TemplateAttachmentRepository(private val dao: TemplateAttachmentDao) {

    fun observeByTemplate(templateId: Long): Flow<List<TemplateAttachmentEntity>> =
        dao.observeByTemplate(templateId)

    suspend fun findByTemplate(templateId: Long): List<TemplateAttachmentEntity> =
        dao.findByTemplate(templateId)

    suspend fun add(
        templateId: Long,
        fileUri: String,
        displayName: String,
        mimeType: String
    ): Long {
        val existing = dao.findByTemplate(templateId)
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        return dao.insert(
            TemplateAttachmentEntity(
                templateId = templateId,
                fileUri = fileUri,
                displayName = displayName,
                mimeType = mimeType,
                sortOrder = nextOrder,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun remove(id: Long) = dao.deleteById(id)
}
