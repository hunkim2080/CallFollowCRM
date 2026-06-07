package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CallSummaryDao
import com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity
import kotlinx.coroutines.flow.Flow

class CallSummaryRepository(private val dao: CallSummaryDao) {

    fun observeByCustomer(customerId: Long): Flow<List<CallSummaryEntity>> =
        dao.observeByCustomer(customerId)

    /** 채팅 화면용 — 번호 끝 8자리로 통화요약 관찰(통화기록과 같은 suffix 매칭). */
    fun observeByPhoneSuffix(suffix: String): Flow<List<CallSummaryEntity>> =
        dao.observeByPhoneSuffix(suffix)

    suspend fun upsert(summary: CallSummaryEntity): Long = dao.insert(summary)

    suspend fun update(summary: CallSummaryEntity) = dao.update(summary)

    suspend fun findById(id: Long): CallSummaryEntity? = dao.findById(id)

    /**
     * 같은 번호 + 같은 통화 시각(±2분) 의 요약이 이미 있으면 그 row 를 반환.
     * 에이닷에서 같은 통화의 요약을 두 번 공유했을 때 중복 저장을 막는다.
     */
    suspend fun findExistingNear(phoneNumber: String, recordedAt: Long): CallSummaryEntity? {
        val window = 2 * 60 * 1000L
        return dao.findByPhoneAndTime(phoneNumber, recordedAt - window, recordedAt + window)
    }

    /** 같은 번호의 orphan 요약을 customerId 로 일괄 연결. */
    suspend fun linkOrphansToCustomer(phoneNumber: String, customerId: Long): Int =
        dao.linkOrphansToCustomer(phoneNumber, customerId)
}
