package com.detailline.callfollowcrm.domain.model

// 2026-05-25: CustomerStatus enum 폐기 — 갤메시지 식 사장님 카테고리 시스템으로 통일.
//   DB 의 status 컬럼은 v11 까지 살아있고 다음 마이그레이션에서 정리. 새 코드는 절대 사용 X.

/**
 * "리드 온도" 축 — 통화 직후 사장님이 빠르게 분류하는 한 가지 차원.
 * 통화 직후 사장님이 빠르게 분류하는 한 가지 차원 — 사장님이 보낸 메시지의 강도,
 * 후속 follow-up 우선순위에 영향. null 이면 미분류.
 */
enum class LeadHeat(val label: String, val emoji: String) {
    COLD("단순 문의", "🌡️"),
    WARM("감도 있음", "🔥");

    companion object {
        fun fromNameOrNull(name: String?): LeadHeat? =
            name?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}

enum class HandledStatus { UNHANDLED, SAVED, MESSAGE_OPENED, SKIPPED }

enum class MessageStatus {
    DRAFT_OPENED, MANUAL_MARK_SENT, SKIPPED,
    AUTO_SENT, AUTO_CANCELLED, AUTO_FAILED,
    INLINE_SENT,    // 고객 상세 인라인 채팅으로 직접 발송
    INLINE_FAILED   // 인라인 발송 시도 실패
}

enum class CallType(val label: String) {
    INCOMING("수신"),
    OUTGOING("발신"),
    MISSED("부재중"),
    REJECTED("거절"),
    MANUAL("수동 등록"),
    UNKNOWN("알수없음")
}

enum class RecordingSourceType { SHARED_FROM_ADOT, MANUAL_PICK, AUTO_DETECTED }

enum class TranscriptStatus { NONE, PENDING, COMPLETED, FAILED }

enum class SummaryStatus { NONE, PENDING, COMPLETED, FAILED }

enum class SummarySourceType { ADOT_SHARE, MANUAL_PASTE, AI_SERVER }

enum class TemplateCategory(val label: String) {
    PHOTO_REQUEST("사진 요청"),
    ESTIMATE("견적 안내"),
    RESERVATION("예약 안내"),
    PRE_WORK("시공 전 안내"),
    MISSED_CALL("부재중 안내"),
    CUSTOM("직접 작성")
}
