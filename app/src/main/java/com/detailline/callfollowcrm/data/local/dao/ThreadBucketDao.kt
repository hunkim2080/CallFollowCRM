package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.ThreadBucketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadBucketDao {

    /** 전 분류 관찰 — HomeViewModel 이 map(suffix→entity)으로 가공해 상담함/문자함 갈라 쓴다. */
    @Query("SELECT * FROM thread_buckets")
    fun observeAll(): Flow<List<ThreadBucketEntity>>

    @Query("SELECT * FROM thread_buckets")
    suspend fun allOnce(): List<ThreadBucketEntity>

    @Query("SELECT * FROM thread_buckets WHERE suffix = :suffix LIMIT 1")
    suspend fun findBySuffix(suffix: String): ThreadBucketEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ThreadBucketEntity)

    @Query("DELETE FROM thread_buckets WHERE suffix = :suffix")
    suspend fun deleteBySuffix(suffix: String)
}
