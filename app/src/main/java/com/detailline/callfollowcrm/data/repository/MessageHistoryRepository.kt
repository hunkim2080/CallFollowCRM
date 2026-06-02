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

    /** 최근 자동답장 기록 (홈 카드). AUTO_SENT/AUTO_FAILED, sinceMs 이후 최신순 limit 개. */
    fun observeRecentAutoReplies(sinceMs: Long, limit: Int = 5): Flow<List<MessageHistoryEntity>> =
        dao.observeRecentAutoReplies(sinceMs, limit)

    /** 견적 보낸 기록 observe (견적 회신 리마인드). status='ESTIMATE_SENT'. */
    fun observeEstimateSends(): Flow<List<MessageHistoryEntity>> = dao.observeEstimateSends()

    /** 통계 "보낸 답장" — 실제 발송 기록 수 (기간 [from, to)). */
    fun observeSentCountBetween(from: Long, to: Long): Flow<Int> = dao.observeSentCountBetween(from, to)

    /** 신규 재연락 목록 "답장함" 판정 — 사장님이 응대한 고객 id 집합. */
    fun observeRepliedCustomerIds(): Flow<List<Long>> = dao.observeRepliedCustomerIds()

    /**
     * 견적 보냄 기록 — 채팅 견적 작성/공유 시 호출. status='ESTIMATE_SENT' 마커.
     *   견적 회신 리마인드(N일째 답 없으면 한 번 더)의 기준 시각.
     */
    suspend fun recordEstimateSent(phoneNumber: String, customerId: Long?, body: String): Long {
        val entity = MessageHistoryEntity(
            phoneNumber = phoneNumber,
            customerId = customerId,
            templateId = null,
            messageBody = body.take(200),
            status = STATUS_ESTIMATE_SENT,
            createdAt = System.currentTimeMillis()
        )
        return dao.insert(entity)
    }

    companion object {
        const val STATUS_ESTIMATE_SENT = "ESTIMATE_SENT"
    }

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
