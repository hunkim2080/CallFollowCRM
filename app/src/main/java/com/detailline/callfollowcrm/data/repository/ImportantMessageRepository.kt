package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.ImportantMessageDao
import com.detailline.callfollowcrm.data.local.entity.ImportantMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * ⭐ 중요 메시지 — 토글 + 조회.
 */
class ImportantMessageRepository(private val dao: ImportantMessageDao) {

    fun observeByPhone(phone: String): Flow<List<ImportantMessageEntity>> =
        dao.observeByPhone(phone)

    /**
     * 토글: 같은 키 (phone, dateMs, sent) 가 있으면 삭제, 없으면 insert.
     * 채팅 말풍선 길게 누름 시 호출.
     */
    suspend fun toggle(
        phone: String,
        customerId: Long?,
        messageBody: String,
        messageDateMs: Long,
        sent: Boolean
    ) {
        val deleted = dao.delete(phone, messageDateMs, sent)
        if (deleted == 0) {
            dao.insert(
                ImportantMessageEntity(
                    phoneNumber = phone,
                    customerId = customerId,
                    messageBody = messageBody,
                    messageDateMs = messageDateMs,
                    sent = sent,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }
}
