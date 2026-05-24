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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiSummaryEntity)
}
