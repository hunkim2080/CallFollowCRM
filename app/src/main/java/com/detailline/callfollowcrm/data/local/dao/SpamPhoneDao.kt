package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.SpamPhoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpamPhoneDao {

    @Query("SELECT phoneSuffix FROM spam_phones")
    fun observeSuffixes(): Flow<List<String>>

    @Query("SELECT phoneSuffix FROM spam_phones")
    suspend fun getAllSuffixes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpamPhoneEntity)

    @Query("DELETE FROM spam_phones WHERE phoneSuffix = :suffix")
    suspend fun deleteBySuffix(suffix: String)
}
