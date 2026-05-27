package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.PricingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PricingItemDao {

    @Query("SELECT * FROM pricing_items WHERE isActive = 1 ORDER BY category ASC, displayOrder ASC, id ASC")
    fun observeActive(): Flow<List<PricingItemEntity>>

    @Query("SELECT * FROM pricing_items ORDER BY category ASC, displayOrder ASC, id ASC")
    fun observeAll(): Flow<List<PricingItemEntity>>

    @Query("SELECT * FROM pricing_items WHERE id = :id")
    suspend fun findById(id: Long): PricingItemEntity?

    @Query("SELECT COUNT(*) FROM pricing_items")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PricingItemEntity): Long

    @Update
    suspend fun update(entity: PricingItemEntity)

    @Delete
    suspend fun delete(entity: PricingItemEntity)

    @Query("DELETE FROM pricing_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
