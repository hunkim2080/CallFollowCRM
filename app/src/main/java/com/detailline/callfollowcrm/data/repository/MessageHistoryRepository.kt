package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.MessageHistoryDao
import com.detailline.callfollowcrm.data.local.entity.MessageHistoryEntity
import com.detailline.callfollowcrm.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

class MessageHistoryRepository(private val dao: MessageHistoryDao) {

    fun observeByCustomer(customerId: Long): Flow<List<MessageHistoryEntity>> =
        dao.observeByCustomer(customerId)

    fun observeByPhone(phone: String): Flow<List<MessageHistoryEntity>> =
        dao.observeByPhone(phone)

    /** 이 번호에 의미 있는 발송/오픈 기록이 있는가. CallStateReceiver 의 unhandled 알림 분기에 사용. */
    suspend fun hasHandledRecord(phone: String): Boolean = dao.countHandledForPhone(phone) > 0

    suspend fun recordDraftOpened(
        phoneNumber: String,
        customerId: Long?,
        templateId: Long?,
        body: String
    ): Long {
        val entity = MessageHistoryEntity(
            phoneNumber = phoneNumber,
            customerId = customerId,
            templateId = templateId,
            messageBody = body,
            status = MessageStatus.DRAFT_OPENED.name,
            createdAt = System.currentTimeMillis()
        )
        return dao.insert(entity)
    }

    suspend fun markStatus(id: Long, status: MessageStatus) {
        val h = dao.findById(id) ?: return
        dao.update(h.copy(status = status.name))
    }

    /**
     * 자동 응답 발송 결과 기록. status 는 AUTO_SENT/AUTO_CANCELLED/AUTO_FAILED 중 하나.
     */
    suspend fun recordAutoSend(
        phoneNumber: String,
        customerId: Long?,
        templateId: Long?,
        body: String,
        status: MessageStatus
    ): Long {
        val entity = MessageHistoryEntity(
            phoneNumber = phoneNumber,
            customerId = customerId,
            templateId = templateId,
            messageBody = body,
            status = status.name,
            createdAt = System.currentTimeMillis()
        )
        return dao.insert(entity)
    }
}
