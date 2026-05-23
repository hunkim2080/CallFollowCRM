package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: MessageTemplateEntity): Long

    @Update
    suspend fun update(template: MessageTemplateEntity)

    @Query("SELECT * FROM message_templates WHERE id = :id")
    suspend fun findById(id: Long): MessageTemplateEntity?

    @Query("SELECT * FROM message_templates WHERE id = :id")
    fun observeById(id: Long): Flow<MessageTemplateEntity?>

    @Query("SELECT * FROM message_templates ORDER BY isActive DESC, isDefault DESC, id ASC")
    fun observeAll(): Flow<List<MessageTemplateEntity>>

    @Query("SELECT * FROM message_templates WHERE isActive = 1 ORDER BY isDefault DESC, id ASC")
    fun observeActive(): Flow<List<MessageTemplateEntity>>

    @Query("SELECT COUNT(*) FROM message_templates")
    suspend fun count(): Int
}
