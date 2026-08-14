package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_summaries",
    indices = [
        Index("customerId"),
        Index("callRecordId"),
        Index("recordingAttachmentId"),
        Index("phoneNumber"),
        Index("recordedAt")
    ]
)
data class CallSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long?,
    val callRecordId: Long? = null,
    val recordingAttachmentId: Long? = null,
    val phoneNumber: String? = null,
    val recordedAt: Long? = null,
    val title: String? = null,
    val transcriptText: String? = null,
    // 통화 전문 '카톡 말풍선'용 화자분리 세그먼트 JSON = [{"speaker":"나"|"손님"|"?","text":"..."}]. null=평문 폴백. (2026-08-14)
    val transcriptSegmentsJson: String? = null,
    val summaryText: String? = null,
    val customerNeed: String? = null,
    val space: String? = null,
    val problem: String? = null,
    val material: String? = null,
    val region: String? = null,
    val schedule: String? = null,
    val priceReaction: String? = null,
    val nextAction: String? = null,
    val recommendedStatus: String? = null,
    val temperatureScore: Int? = null,
    val recommendedMessage: String? = null,
    val missingInfo: String? = null,
    val sourceType: String,
    val rawText: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
