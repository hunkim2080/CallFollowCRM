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
    // 통화 키워드 태그(JSON 배열 문자열, 예 ["화장실","누수"]). 통화카드 해시태그. 서버 tags[]. (2026-08-17)
    val tagsJson: String? = null,
    // 통화 분류(견적 문의/시공 상담/일정/수금·입금/A/S/개인·안부/기타). 서버 category. (2026-11-18)
    val category: String? = null,
    // 꼭 챙겨야 할 일 JSON 배열 문자열(예 ["욕실 사진 받기","견적 문자 보내기"]). 서버 todos[]. (2026-11-18)
    val todosJson: String? = null,
    // 완료한 할 일(취소선 표시용) JSON 배열 문자열. 삭제가 아니라 '완료 표시' — 다시 탭하면 해제. (2026-11-19)
    val todosDoneJson: String? = null,
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
