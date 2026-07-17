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

    /** 전체 첨부(문구 목록에서 '사진 있음' 썸네일 표시용). templateId→첫 사진을 호출부가 골라 씀. (2026-07-18) */
    @Query("SELECT * FROM template_attachments ORDER BY templateId ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<TemplateAttachmentEntity>>
}
