package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CallRecordEntity): Long

    @Update
    suspend fun update(record: CallRecordEntity)

    @Query("SELECT * FROM call_records WHERE id = :id")
    suspend fun findById(id: Long): CallRecordEntity?

    @Query("SELECT * FROM call_records WHERE phoneNumber = :phoneNumber ORDER BY endedAt DESC")
    fun observeByPhone(phoneNumber: String): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records ORDER BY endedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records WHERE endedAt BETWEEN :from AND :to ORDER BY endedAt DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<CallRecordEntity>>

    @Query("SELECT COUNT(*) FROM call_records WHERE endedAt BETWEEN :from AND :to AND handledStatus = 'UNHANDLED'")
    fun countUnhandled(from: Long, to: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_records WHERE endedAt BETWEEN :from AND :to AND handledStatus != 'UNHANDLED'")
    fun countHandled(from: Long, to: Long): Flow<Int>

    /** 동일 번호 + 동일 startedAt 레코드 개수. 시스템 CallLog 동기화 중복 방지용. */
    @Query("SELECT COUNT(*) FROM call_records WHERE phoneNumber = :phone AND startedAt = :startedAt")
    suspend fun countByPhoneAndStarted(phone: String, startedAt: Long): Int

    /**
     * 2026-05-30 #9 통점 fix — create() dedup 용.
     *   같은 (phone, startedAt) 이미 있으면 id 반환. 없으면 null.
     *   CallStateReceiver (정적) + Application.TelephonyCallback (동적) 동시 호출 시 중복 INSERT 차단.
     */
    @Query("SELECT id FROM call_records WHERE phoneNumber = :phone AND startedAt = :startedAt LIMIT 1")
    suspend fun findIdByPhoneAndStarted(phone: String, startedAt: Long): Long?

    /** 해당 번호의 통화 기록 총 개수. "첫 통화 감지"용 (== 1 이면 방금 만든 게 처음). */
    @Query("SELECT COUNT(*) FROM call_records WHERE phoneNumber = :phone")
    suspend fun countByPhone(phone: String): Int

    /**
     * 지정 시점 이후 부재중 통화. "미확인" KPI 의 통화 측 입력.
     * (수신/발신 = 이미 통화한 거라 미확인 아님. 거절도 사장님이 의식적으로 안 받은 거라 제외.)
     */
    @Query("SELECT * FROM call_records WHERE endedAt >= :from AND callType = 'MISSED' ORDER BY endedAt DESC")
    fun observeMissedSince(from: Long): Flow<List<CallRecordEntity>>

    /**
     * 지정 시점 이전에 통화 기록이 있는 phone 들 (distinct).
     * "오늘 신규" 판정용 — 오늘 통화/SMS 받은 번호 중 이 set 에 없으면 = 진짜 신규.
     */
    @Query("SELECT DISTINCT phoneNumber FROM call_records WHERE endedAt < :before")
    fun observeDistinctPhonesBefore(before: Long): Flow<List<String>>

    /**
     * 특정 번호의 [from~to] 윈도우 안의 UNHANDLED 통화들을 SAVED 로 일괄 표시.
     * 사용 시점: CustomerDetail 에서 상태/메모/이름/예약일을 변경했을 때.
     * 의미: 사장님이 그 고객에게 후속을 했음 → 통화별 알림 단위 처리도 클리어.
     * customerId 가 비어있던 row 는 함께 연결.
     */
    @Query("""
        UPDATE call_records
        SET handledStatus = 'SAVED',
            linkedCustomerId = COALESCE(linkedCustomerId, :customerId)
        WHERE phoneNumber = :phoneNumber
          AND handledStatus = 'UNHANDLED'
          AND endedAt BETWEEN :from AND :to
    """)
    suspend fun markUnhandledByPhoneToday(
        phoneNumber: String,
        customerId: Long?,
        from: Long,
        to: Long
    ): Int
}
