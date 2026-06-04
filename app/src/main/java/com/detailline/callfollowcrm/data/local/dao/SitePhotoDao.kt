package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.SitePhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SitePhotoDao {
    @Query("SELECT * FROM site_photos WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observeByCustomer(customerId: Long): Flow<List<SitePhotoEntity>>

    @Insert
    suspend fun insert(photo: SitePhotoEntity): Long

    @Query("SELECT filePath FROM site_photos WHERE id = :id")
    suspend fun filePathOf(id: Long): String?

    @Query("DELETE FROM site_photos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
