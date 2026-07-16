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

    /**
     * 채팅 통화 구간 표시용 — 끝 8자리(suffix)로 매칭 (저장 포맷 차이 흡수).
     *   call_records 는 작은 테이블이라 replace+LIKE 풀스캔 비용 무시 가능.
     */
    @Query("""
        SELECT * FROM call_records
        WHERE replace(replace(phoneNumber, '-', ''), ' ', '') LIKE '%' || :suffix
        ORDER BY endedAt DESC
    """)
    fun observeByPhoneSuffix(suffix: String): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records ORDER BY endedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<CallRecordEntity>>

    /**
     * [from~to] 사이에 끝난 통화 (일회성 조회). 번호 없이 **이름만** 든 녹음 파일을
     *   "그 시각에 하던 통화" 로 되찾을 때 씀. (2026-07-16, CallRecordRepository.findCallAtTime)
     */
    @Query("SELECT * FROM call_records WHERE endedAt BETWEEN :from AND :to ORDER BY endedAt DESC")
    suspend fun findEndedBetween(from: Long, to: Long): List<CallRecordEntity>

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

    /**
     * 2026-06-10 발신 통화 2개 중복 fix — 같은 시작시각의 기록들.
     *   번호 형식(원본 "010..." / 고객저장 "010-..." / "+82...")이 경로마다 달라 phone 정확일치 dedup 이
     *   빗나가 같은 통화가 2 row 로 들어감. → startedAt 으로 모아 끝 8자리 숫자로 비교(repo)해서 dedup.
     */
    @Query("SELECT * FROM call_records WHERE startedAt = :startedAt")
    suspend fun findByStartedAt(startedAt: Long): List<CallRecordEntity>

    /** 유령 중복 정리용 — startedAt 있는 통화기록 전체(형식무시 그룹핑은 Kotlin 에서). (2026-07-09) */
    @Query("SELECT * FROM call_records WHERE startedAt IS NOT NULL")
    suspend fun allWithStartedAt(): List<CallRecordEntity>

    /** 요약(call_summaries)이 연결된 통화기록 id 들 — 유령(=데이터 없음) 판정용. */
    @Query("SELECT DISTINCT callRecordId FROM call_summaries WHERE callRecordId IS NOT NULL")
    suspend fun summaryLinkedRecordIds(): List<Long>

    /** 녹음(recording_attachments)이 연결된 통화기록 id 들 — 유령 판정용. */
    @Query("SELECT DISTINCT callRecordId FROM recording_attachments WHERE callRecordId IS NOT NULL")
    suspend fun recordingLinkedRecordIds(): List<Long>

    /** 유령 중복 통화기록 일괄 삭제(id 목록). 삭제 대상 판정은 repository(Kotlin)에서. */
    @Query("DELETE FROM call_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

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
     * 지정 시점 이후 "들어온" 통화 — 수신(받음)·부재중·거절. "오늘 신규 문의" 집계용.
     *   발신(OUTGOING)은 제외(사장님이 건 전화 = 문의 아님). 부재중만 세던 것 → 받은 전화도 포함.
     */
    @Query("SELECT * FROM call_records WHERE endedAt >= :from AND callType IN ('INCOMING','MISSED','REJECTED') ORDER BY endedAt DESC")
    fun observeInboundSince(from: Long): Flow<List<CallRecordEntity>>

    /**
     * 지정 시점 이후 "통화로 응대한" 기록 — 받은전화 응답(INCOMING)·내가 건 전화(OUTGOING).
     *   부재중/미답문자 이후 이 통화가 있으면 = 전화로 처리한 것 → "답장 기다려요"에서 제외. (2026-07-02 사장님)
     */
    @Query("SELECT * FROM call_records WHERE endedAt >= :from AND callType IN ('INCOMING','OUTGOING') ORDER BY endedAt DESC")
    fun observeHandledCallsSince(from: Long): Flow<List<CallRecordEntity>>

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
