package com.detailline.callfollowcrm.data.repository

import android.content.Context
import com.detailline.callfollowcrm.data.local.dao.ScheduledSmsDao
import com.detailline.callfollowcrm.data.local.entity.ScheduledSmsEntity
import com.detailline.callfollowcrm.service.ScheduledSmsScheduler
import kotlinx.coroutines.flow.Flow

/**
 * 🕐 예약 문자 저장 + 알람 걸기를 한곳에서. 화면/VM 은 여기만 부른다.
 *   (발송 자체는 [com.detailline.callfollowcrm.service.ScheduledSmsSender], 알람은 [ScheduledSmsScheduler].)
 */
class ScheduledSmsRepository(
    private val appContext: Context,
    private val dao: ScheduledSmsDao
) {
    fun observePending(): Flow<List<ScheduledSmsEntity>> = dao.observePending()
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()
    /** 이 번호의 대기 예약 — 대화창 '발송 대기' 말풍선용. */
    fun observePendingForPhone(phone: String): Flow<List<ScheduledSmsEntity>> = dao.observePendingForPhone(phone)

    suspend fun getById(id: Long): ScheduledSmsEntity? = dao.getById(id)

    /** 새 예약 — 저장하고 알람을 건다. 반환 = 행 id. */
    suspend fun schedule(
        phoneNumber: String,
        recipientName: String,
        body: String,
        photoUris: List<String>,
        sendAtMs: Long
    ): Long {
        val row = ScheduledSmsEntity(
            phoneNumber = phoneNumber,
            recipientName = recipientName,
            body = body,
            photoUris = ScheduledSmsEntity.joinUris(photoUris),
            sendAtMs = sendAtMs,
            status = ScheduledSmsEntity.STATUS_PENDING,
            createdAtMs = System.currentTimeMillis()
        )
        val id = dao.insert(row)
        ScheduledSmsScheduler.schedule(appContext, row.copy(id = id))
        return id
    }

    /** 시각 바꾸기 — 옛 알람 풀고 새로 건다. */
    suspend fun reschedule(id: Long, newSendAtMs: Long) {
        dao.updateSendAt(id, newSendAtMs)
        ScheduledSmsScheduler.cancel(appContext, id)
        dao.getById(id)?.let { if (it.status == ScheduledSmsEntity.STATUS_PENDING) ScheduledSmsScheduler.schedule(appContext, it) }
    }

    /** 사장님 취소 — 알람 풀고 행 삭제. */
    suspend fun cancel(id: Long) {
        ScheduledSmsScheduler.cancel(appContext, id)
        dao.delete(id)
    }

    /** 오래된 보냄/실패 기록 청소(30일 지난 것). */
    suspend fun pruneOld() {
        dao.pruneDoneBefore(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
    }
}
