package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.RecordingAttachmentDao
import com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity
import com.detailline.callfollowcrm.domain.model.RecordingSourceType
import com.detailline.callfollowcrm.domain.model.SummaryStatus
import com.detailline.callfollowcrm.domain.model.TranscriptStatus
import kotlinx.coroutines.flow.Flow

class RecordingRepository(private val dao: RecordingAttachmentDao) {

    fun observeByCustomer(customerId: Long): Flow<List<RecordingAttachmentEntity>> =
        dao.observeByCustomer(customerId)

    fun observeUnlinked(): Flow<List<RecordingAttachmentEntity>> = dao.observeUnlinked()

    /** 채팅 통화카드 재생 플레이어용 — 번호 suffix 매칭으로 녹음 첨부 관찰. */
    fun observeByPhoneSuffix(suffix: String): Flow<List<RecordingAttachmentEntity>> =
        dao.observeByPhoneSuffix(suffix)

    suspend fun existsByUri(uri: String): Boolean = dao.countByUri(uri) > 0

    suspend fun add(
        fileUri: String,
        fileName: String,
        sourceType: RecordingSourceType,
        customerId: Long? = null,
        callRecordId: Long? = null,
        phoneNumber: String? = null,
        duration: Long? = null
    ): Long {
        val entity = RecordingAttachmentEntity(
            customerId = customerId,
            callRecordId = callRecordId,
            phoneNumber = phoneNumber,
            fileUri = fileUri,
            fileName = fileName,
            duration = duration,
            sourceType = sourceType.name,
            transcriptStatus = TranscriptStatus.NONE.name,
            summaryStatus = SummaryStatus.NONE.name,
            createdAt = System.currentTimeMillis()
        )
        return dao.insert(entity)
    }

    suspend fun linkToCustomer(id: Long, customerId: Long, callRecordId: Long? = null) {
        val a = dao.findById(id) ?: return
        dao.update(a.copy(customerId = customerId, callRecordId = callRecordId ?: a.callRecordId))
    }

    /** 같은 번호의 orphan 첨부를 customerId 로 일괄 연결. 영향 받은 row 수 반환. */
    suspend fun linkOrphansToCustomer(phoneNumber: String, customerId: Long): Int =
        dao.linkOrphansToCustomer(phoneNumber, customerId)

    suspend fun deleteAllAdotImports(): Int =
        dao.deleteBySourceType(RecordingSourceType.SHARED_FROM_ADOT.name)

    suspend fun countAdotImports(): Int =
        dao.countBySourceType(RecordingSourceType.SHARED_FROM_ADOT.name)
}
