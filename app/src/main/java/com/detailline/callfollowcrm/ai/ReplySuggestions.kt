package com.detailline.callfollowcrm.ai

/**
 * 고객이 마지막으로 보낸 메시지에 대해 맥미니 LLM 이 미리 준비한 답변 후보.
 *
 * 신선도 판정용: basedOnReceivedAtMs == 화면이 보고 있는 최신 수신 메시지 timestamp 여야 유효.
 * 그보다 더 늦게 도착한 메시지가 있으면 stale → 사장님이 ↻ 눌러서 재생성해야 함.
 */
data class ReplySuggestions(
    val phone: String,
    val basedOnMessage: String,
    val basedOnReceivedAtMs: Long,
    val generatedAtMs: Long,
    val suggestions: List<String>
)

enum class SuggestionStatus {
    /** 캐시에 준비된 답변 있음. suggestions != null. */
    READY,
    /** 서버가 LLM 호출 중. suggestions == null. */
    GENERATING,
    /** 캐시에 없음 (서버가 트리거 못 받았거나 캐시 만료). 사장님 ↻ 로 재시도. */
    MISSING
}

data class SuggestionFetchResult(
    val status: SuggestionStatus,
    val suggestions: ReplySuggestions?
)

/** 서버에 보내는 prepare 컨텍스트. */
data class PrepareContext(
    val phone: String,
    val latestMessage: String,
    val latestMessageReceivedAtMs: Long,
    val recentHistory: List<HistoryMessage>,
    val customer: CustomerHint?
)

data class HistoryMessage(
    /** "customer" 또는 "owner" */
    val role: String,
    val body: String,
    val timestampMs: Long
)

data class CustomerHint(
    val name: String?,
    val memo: String?,
    /** "HOT" | "WARM" | "COLD" 등 leadHeat enum name */
    val leadHeat: String?,
    val depositPaid: Boolean
)
