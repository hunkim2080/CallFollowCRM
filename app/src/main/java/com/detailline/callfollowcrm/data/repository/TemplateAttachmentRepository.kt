package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.TemplateAttachmentDao
import com.detailline.callfollowcrm.data.local.entity.TemplateAttachmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TemplateAttachmentRepository(private val dao: TemplateAttachmentDao) {

    fun observeByTemplate(templateId: Long): Flow<List<TemplateAttachmentEntity>> =
        dao.observeByTemplate(templateId)

    /** 전체 첨부 — 문구 목록의 '사진 있음' 썸네일용. (2026-07-18) */
    fun observeAll(): Flow<List<TemplateAttachmentEntity>> = dao.observeAll()

    /**
     * 문구ID → 그 문구의 **첫(정렬 맨 앞) 사진 URI**. 썸네일 한 곳 — 채팅 피커와 문구 관리 화면이 **같이** 쓴다.
     *   (2026-10-12 사장님: 문구 관리 화면엔 등록한 사진이 안 보이던 것. 같은 셈을 두 벌로 두지 않으려고 공용. §12)
     */
    fun observeFirstImageUriByTemplate(): Flow<Map<Long, String>> =
        dao.observeAll().map { list ->
            list.filter { it.mimeType.startsWith("image/") }
                .groupBy { it.templateId }
                .mapValues { (_, atts) -> atts.minByOrNull { it.sortOrder }!!.fileUri }
        }

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
