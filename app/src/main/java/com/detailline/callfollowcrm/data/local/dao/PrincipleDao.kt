package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.PrincipleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrincipleDao {

    @Query("SELECT * FROM principles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PrincipleEntity>>

    /** AI 주입용 — 켜진 것만, 오래된 순(원칙 생긴 순서). */
    @Query("SELECT * FROM principles WHERE enabled = 1 ORDER BY createdAt ASC")
    suspend fun listEnabled(): List<PrincipleEntity>

    @Query("SELECT * FROM principles WHERE id = :id")
    suspend fun findById(id: Long): PrincipleEntity?

    @Query("SELECT COUNT(*) FROM principles")
    suspend fun count(): Int

    @Insert
    suspend fun insert(p: PrincipleEntity): Long

    @Update
    suspend fun update(p: PrincipleEntity)

    @Query("DELETE FROM principles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
