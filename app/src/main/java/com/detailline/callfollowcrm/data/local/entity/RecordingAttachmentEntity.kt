package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recording_attachments",
    indices = [Index("customerId"), Index("callRecordId"), Index("phoneNumber")]
)
data class RecordingAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long? = null,
    val callRecordId: Long? = null,
    // 파일명에서 추출된 전화번호. Customer 가 나중에 만들어졌을 때 같은 번호의 orphan
    // 첨부를 자동 연결하기 위해 보관. 추출 실패 시 null.
    val phoneNumber: String? = null,
    val fileUri: String,
    val fileName: String,
    val duration: Long? = null,
    val sourceType: String,
    val transcriptStatus: String,
    val summaryStatus: String,
    val createdAt: Long
)
