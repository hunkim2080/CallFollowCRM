package com.detailline.callfollowcrm.data.repository

import android.content.Context
import com.detailline.callfollowcrm.data.local.dao.CallRecordDao
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.domain.model.CallType
import com.detailline.callfollowcrm.domain.model.HandledStatus
import com.detailline.callfollowcrm.util.CallLogHelper
import kotlinx.coroutines.flow.Flow

class CallRecordRepository(private val dao: CallRecordDao) {

    fun observeRecent(limit: Int = 50): Flow<List<CallRecordEntity>> = dao.observeRecent(limit)
    fun observeBetween(from: Long, to: Long): Flow<List<CallRecordEntity>> = dao.observeBetween(from, to)
    fun observeByPhone(phone: String): Flow<List<CallRecordEntity>> = dao.observeByPhone(phone)
    fun countUnhandled(from: Long, to: Long): Flow<Int> = dao.countUnhandled(from, to)
    fun countHandled(from: Long, to: Long): Flow<Int> = dao.countHandled(from, to)

    suspend fun findById(id: Long): CallRecordEntity? = dao.findById(id)

    /** 이 번호로 통화 기록이 몇 번 있는지. 1 이면 방금 만든 게 처음. */
    suspend fun countByPhone(phone: String): Int = dao.countByPhone(phone)

    suspend fun create(
        phoneNumber: String,
        callType: CallType,
        duration: Long,
        startedAt: Long?,
        endedAt: Long,
        linkedCustomerId: Long? = null,
        handledStatus: HandledStatus = HandledStatus.UNHANDLED
    ): Long {
        val entity = CallRecordEntity(
            phoneNumber = phoneNumber,
            callType = callType.name,
            duration = duration,
            startedAt = startedAt,
            endedAt = endedAt,
            handledStatus = handledStatus.name,
            linkedCustomerId = linkedCustomerId
        )
        return dao.insert(entity)
    }

    suspend fun markHandled(id: Long, status: HandledStatus, linkedCustomerId: Long? = null) {
        val r = dao.findById(id) ?: return
        dao.update(r.copy(handledStatus = status.name, linkedCustomerId = linkedCustomerId ?: r.linkedCustomerId))
    }

    /**
     * 특정 번호의 [from~to] 윈도우 안 UNHANDLED 통화 일괄 SAVED 처리.
     * CustomerDetail 에서 사장님이 후속 액션(상태/메모/이름/예약일 변경)을 했을 때 호출.
     * @return 영향 받은 row 수
     */
    suspend fun markUnhandledByPhoneToday(
        phoneNumber: String,
        customerId: Long?,
        from: Long,
        to: Long
    ): Int = dao.markUnhandledByPhoneToday(phoneNumber, customerId, from, to)

    /**
     * 시스템 통화기록(CallLog)에서 해당 번호의 통화들을 우리 DB에 동기화한다.
     * BroadcastReceiver가 놓쳤거나 사용자가 알림을 못 눌렀어도, 번호만 알면 과거 기록을 채워넣을 수 있다.
     *
     * - 입력 phoneNumber 는 사용자가 입력한 그대로 (예: "010-1234-5678" 또는 "01012345678")
     * - 같은 번호 + 같은 startedAt 레코드가 이미 있으면 스킵 (중복 방지)
     * - linkedCustomerId 를 전달하면 매칭된 CallRecord 에 자동 연결
     *
     * @return 새로 insert된 레코드 개수
     */
    suspend fun syncFromCallLog(
        context: Context,
        phoneNumber: String,
        linkedCustomerId: Long? = null,
        handledStatus: HandledStatus = HandledStatus.SAVED
    ): Int {
        val entries = CallLogHelper.queryByPhone(context, phoneNumber)
        if (entries.isEmpty()) return 0

        var inserted = 0
        for (e in entries) {
            // startedAt 으로 dedup (CallLog DATE = 통화 시작 시각)
            if (dao.countByPhoneAndStarted(phoneNumber, e.date) > 0) continue
            dao.insert(
                CallRecordEntity(
                    phoneNumber = phoneNumber,
                    callType = e.type.name,
                    duration = e.duration,
                    startedAt = e.date,
                    endedAt = e.date + e.duration * 1000,
                    handledStatus = handledStatus.name,
                    linkedCustomerId = linkedCustomerId
                )
            )
            inserted++
        }
        return inserted
    }
}
