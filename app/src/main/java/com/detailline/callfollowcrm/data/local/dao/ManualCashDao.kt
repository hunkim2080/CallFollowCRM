package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.ManualCashEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualCashDao {
    @Query("SELECT * FROM manual_cash ORDER BY dayStartMs DESC, id DESC")
    fun observeAll(): Flow<List<ManualCashEntity>>

    @Query("SELECT * FROM manual_cash WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ManualCashEntity?

    @Insert
    suspend fun insert(entity: ManualCashEntity): Long

    @Update
    suspend fun update(entity: ManualCashEntity)

    @Query("DELETE FROM manual_cash WHERE id = :id")
    suspend fun deleteById(id: Long)
}
