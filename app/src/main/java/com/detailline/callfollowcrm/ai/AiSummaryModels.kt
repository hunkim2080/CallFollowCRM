package com.detailline.callfollowcrm.ai

/**
 * 미래에 서버에서 받을 AI 요약 결과 모델.
 * JSON 예시 형태에 맞춰 1:1 로 정의해두고, v1 에서는 인터페이스만 사용한다.
 */
data class AiSummaryResult(
    val summary: String,
    val customerNeed: List<String>,
    val space: List<String>,
    val problem: List<String>,
    val material: List<String>,
    val region: String,
    val schedule: String,
    val priceReaction: String,
    val nextAction: List<String>,
    val recommendedStatus: String,
    val temperatureScore: Int,
    val temperatureReason: List<String>,
    val recommendedMessage: String,
    val missingInfo: List<String>
)

data class AiSummaryRequest(
    val customerId: Long,
    val callRecordId: Long?,
    val recordingAttachmentId: Long?,
    /** 서버에 업로드된 녹음 ID. null 이면 텍스트만으로 요약 시도. */
    val serverRecordingId: String? = null,
    val transcriptText: String? = null
)
