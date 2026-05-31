package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookContactDao {
    @Query("SELECT * FROM notebook_contacts WHERE kind = :kind ORDER BY name ASC")
    fun observeByKind(kind: String): Flow<List<NotebookContactEntity>>

    @Query("SELECT * FROM notebook_contacts WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): NotebookContactEntity?

    @Insert
    suspend fun insert(entity: NotebookContactEntity): Long

    @Update
    suspend fun update(entity: NotebookContactEntity)

    @Query("DELETE FROM notebook_contacts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
