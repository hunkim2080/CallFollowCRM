package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.TemplateAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateAttachmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: TemplateAttachmentEntity): Long

    @Delete
    suspend fun delete(attachment: TemplateAttachmentEntity)

    @Query("DELETE FROM template_attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM template_attachments WHERE templateId = :templateId")
    suspend fun deleteByTemplateId(templateId: Long)

    @Query("SELECT * FROM template_attachments WHERE templateId = :templateId ORDER BY sortOrder ASC, id ASC")
    fun observeByTemplate(templateId: Long): Flow<List<TemplateAttachmentEntity>>

    @Query("SELECT * FROM template_attachments WHERE templateId = :templateId ORDER BY sortOrder ASC, id ASC")
    suspend fun findByTemplate(templateId: Long): List<TemplateAttachmentEntity>
}
