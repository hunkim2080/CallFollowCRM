package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.ScheduledSmsEntity
import kotlinx.coroutines.flow.Flow

/** 🕐 예약 문자 표 접근. 읽고/쓰기만 — 스케줄링은 service/ScheduledSmsScheduler. */
@Dao
interface ScheduledSmsDao {

    @Insert
    suspend fun insert(row: ScheduledSmsEntity): Long

    @Query("SELECT * FROM scheduled_sms WHERE id = :id")
    suspend fun getById(id: Long): ScheduledSmsEntity?

    /** 대기 중인 예약 전부 — 앱 켤 때·부팅 때 다시 알람 걸려고. */
    @Query("SELECT * FROM scheduled_sms WHERE status = 'pending' ORDER BY sendAtMs ASC")
    suspend fun allPending(): List<ScheduledSmsEntity>

    /** 예약함 목록(대기만, 이른 시각 먼저). 시간 흘러도 반영되게 Flow. */
    @Query("SELECT * FROM scheduled_sms WHERE status = 'pending' ORDER BY sendAtMs ASC")
    fun observePending(): Flow<List<ScheduledSmsEntity>>

    /** 예약함 뱃지용 — 대기 개수. */
    @Query("SELECT COUNT(*) FROM scheduled_sms WHERE status = 'pending'")
    fun observePendingCount(): Flow<Int>

    /** 이 번호의 대기 예약 — 대화창에 '발송 대기' 말풍선으로 보여주려고. (이른 시각 먼저) */
    @Query("SELECT * FROM scheduled_sms WHERE status = 'pending' AND phoneNumber = :phone ORDER BY sendAtMs ASC")
    fun observePendingForPhone(phone: String): Flow<List<ScheduledSmsEntity>>

    /** 시각 수정(사장님이 예약함에서 시간 바꿈). pending 일 때만. */
    @Query("UPDATE scheduled_sms SET sendAtMs = :sendAtMs WHERE id = :id AND status = 'pending'")
    suspend fun updateSendAt(id: Long, sendAtMs: Long)

    /** 발송 결과 기록(보냄/실패 + 한 줄). */
    @Query("UPDATE scheduled_sms SET status = :status, resultNote = :note WHERE id = :id")
    suspend fun markResult(id: Long, status: String, note: String?)

    /** 취소(사장님) — 바로 지운다. */
    @Query("DELETE FROM scheduled_sms WHERE id = :id")
    suspend fun delete(id: Long)

    /** 오래된 보냄/실패/취소 기록 청소(표가 안 커지게). */
    @Query("DELETE FROM scheduled_sms WHERE status != 'pending' AND sendAtMs < :before")
    suspend fun pruneDoneBefore(before: Long)
}
