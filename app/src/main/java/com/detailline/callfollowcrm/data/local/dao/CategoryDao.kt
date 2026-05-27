package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY displayOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CategoryEntity): Long

    @Update
    suspend fun update(entity: CategoryEntity)

    @Delete
    suspend fun delete(entity: CategoryEntity)

    @Query("SELECT COALESCE(MAX(displayOrder), -1) + 1 FROM categories")
    suspend fun nextDisplayOrder(): Int
}
