package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId")]
)
data class TemplateAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val fileUri: String,
    val displayName: String,
    val mimeType: String,
    val sortOrder: Int,
    val createdAt: Long
)
