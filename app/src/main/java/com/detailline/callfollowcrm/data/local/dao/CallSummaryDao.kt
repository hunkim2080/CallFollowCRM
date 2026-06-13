package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallSummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: CallSummaryEntity): Long

    @Update
    suspend fun update(summary: CallSummaryEntity)

    @Query("SELECT * FROM call_summaries WHERE customerId = :customerId ORDER BY COALESCE(recordedAt, updatedAt) DESC")
    fun observeByCustomer(customerId: Long): Flow<List<CallSummaryEntity>>

    /** 채팅(번호 기준) 통화요약 — 저장 포맷 차이 흡수 위해 끝 8자리 suffix 매칭. */
    @Query("SELECT * FROM call_summaries WHERE phoneNumber LIKE '%' || :suffix ORDER BY COALESCE(recordedAt, updatedAt) DESC")
    fun observeByPhoneSuffix(suffix: String): Flow<List<CallSummaryEntity>>

    /** 상담함 한줄 미리보기용 — 전체 통화요약(최신순). 번호별 최신 1건을 화면에서 골라 씀. */
    @Query("SELECT * FROM call_summaries ORDER BY COALESCE(recordedAt, updatedAt) DESC")
    fun observeAll(): Flow<List<CallSummaryEntity>>

    @Query("SELECT * FROM call_summaries WHERE id = :id")
    suspend fun findById(id: Long): CallSummaryEntity?

    /**
     * 같은 번호 + 같은 통화 시각(±2분 윈도우) 의 요약이 이미 있는지 확인.
     * 에이닷 공유를 두 번 보내거나 같은 텍스트를 다시 붙여넣을 때 중복 방지.
     */
    @Query("""
        SELECT * FROM call_summaries
        WHERE phoneNumber = :phoneNumber
          AND recordedAt IS NOT NULL
          AND recordedAt BETWEEN :fromMs AND :toMs
        LIMIT 1
    """)
    suspend fun findByPhoneAndTime(phoneNumber: String, fromMs: Long, toMs: Long): CallSummaryEntity?

    /** 같은 번호의 orphan 요약(customerId IS NULL)을 새로 만들어진 Customer 에 일괄 연결. */
    @Query("UPDATE call_summaries SET customerId = :customerId WHERE customerId IS NULL AND phoneNumber = :phoneNumber")
    suspend fun linkOrphansToCustomer(phoneNumber: String, customerId: Long): Int
}
