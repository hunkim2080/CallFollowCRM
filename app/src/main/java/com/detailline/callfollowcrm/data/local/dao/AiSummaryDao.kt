package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.AiSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiSummaryDao {
    @Query("SELECT * FROM ai_summary_cache WHERE phoneSuffix = :suffix")
    suspend fun get(suffix: String): AiSummaryEntity?

    @Query("SELECT * FROM ai_summary_cache WHERE phoneSuffix = :suffix")
    fun observe(suffix: String): Flow<AiSummaryEntity?>

    @Query("SELECT * FROM ai_summary_cache WHERE phoneSuffix IN (:suffixes)")
    fun observeMany(suffixes: List<String>): Flow<List<AiSummaryEntity>>

    /** 전체 요약(신규문의 목록 한줄 표시용 — 테이블은 연락처당 1행이라 가벼움). */
    @Query("SELECT * FROM ai_summary_cache")
    fun observeAll(): Flow<List<AiSummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiSummaryEntity)
}
