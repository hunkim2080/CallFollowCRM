package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.SmsContactCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsContactCacheDao {

    /** HomeScreen 이 구독 — 최신순. limit 으로 contactLimit 동일 제약. */
    @Query("SELECT * FROM sms_contacts_cache ORDER BY lastDateMs DESC LIMIT :limit")
    fun observeAll(limit: Int = 500): Flow<List<SmsContactCacheEntity>>

    @Query("SELECT * FROM sms_contacts_cache WHERE normalizedSuffix = :suffix LIMIT 1")
    suspend fun findBySuffix(suffix: String): SmsContactCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SmsContactCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SmsContactCacheEntity>)

    /** 전체 rebuild (풀스캔 결과 적용) 직전에 호출. */
    @Query("DELETE FROM sms_contacts_cache")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM sms_contacts_cache")
    suspend fun count(): Int
}
