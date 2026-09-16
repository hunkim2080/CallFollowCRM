package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SimpleEventDao {
    /** 날짜 오름차순 — 달력 점·그날 목록 모두 이걸 본다. */
    @Query("SELECT * FROM simple_events ORDER BY dayStartMs ASC, minutes IS NULL DESC, minutes ASC, id ASC")
    fun observeAll(): Flow<List<SimpleEventEntity>>

    @Query("SELECT * FROM simple_events ORDER BY dayStartMs ASC, id ASC")
    suspend fun allOnce(): List<SimpleEventEntity>

    @Query("SELECT * FROM simple_events WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SimpleEventEntity?

    @Insert
    suspend fun insert(entity: SimpleEventEntity): Long

    @Update
    suspend fun update(entity: SimpleEventEntity)

    @Query("DELETE FROM simple_events WHERE id = :id")
    suspend fun deleteById(id: Long)
}
