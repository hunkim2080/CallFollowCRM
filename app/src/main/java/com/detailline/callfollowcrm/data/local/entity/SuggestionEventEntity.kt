package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 2026-05-29 킬러콘텐츠 3단계 — 채택/수정 데이터 수집.
 *
 * 사장님의 행동 시그널 (chip 보고 → 어떻게 처리했는지) 을 phone × suggestion 단위로 기록.
 * "수정 거리 0" 이 product 목표 — 사장님이 그대로 보낼수록 우리 추천 품질 ↑.
 *
 * 사용:
 *   - 분석: 시나리오별 / intent_key 별 채택률 → 어떤 시나리오에서 사장님이 가장 만족하나
 *   - 학습 데이터: EDITED 의 (suggestion vs edited) 페어 → 사장님 톤 RAG (4단계) 의 high-quality sample
 *   - 4/5/6단계 기반
 *
 * 서버 보고는 batch — 다음 sprint 의 cowork 작업. 일단 클라이언트에 쌓아두고 추후 POST.
 */
@Entity(
    tableName = "suggestion_events",
    indices = [
        Index("createdAtMs", name = "idx_suggestion_events_createdAtMs"),
        Index("reportedToServer", name = "idx_suggestion_events_reportedToServer")
    ]
)
data class SuggestionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** 정규화된 phone (digit-only suffix 8자리). 익명화는 서버 보고 시점에. */
    val phoneSuffix: String,

    /** v2 응답의 scenario (e.g., "price_inquiry"). null = 옛 v1 응답이었거나 scenario 없음. */
    val scenario: String?,

    /** v2 응답의 scenario_confidence. null = 옛 v1. */
    val scenarioConfidence: Double?,

    /** 답변 의도 key (e.g., "quote", "info"). null = 옛 v1 또는 chip 안 누름 (IGNORED). */
    val intentKey: String?,

    /** 답변 라벨 (e.g., "💰 견적 안내"). null = 옛 v1. */
    val intentLabel: String?,

    /** chip 의 원본 text. SENT_AS_IS / EDITED / REFINED_THEN_SENT 일 때 의미 있음. */
    val suggestionText: String?,

    /**
     * 사장님 행동:
     *   - SENT_AS_IS         : chip 탭 → 그대로 전송 (✨ 다듬기 없음)
     *   - EDITED             : chip 탭 → 사장님이 input 수정 → 전송
     *   - REFINED_THEN_SENT  : chip 탭 → ✨ 다듬기 → 전송
     *   - IGNORED            : suggestions 보였지만 chip 안 탭 + 직접 typing → 전송
     *   - DISMISSED          : suggestions 보였지만 chip 안 탭 + 화면 떠남 (답 안 보냄)
     */
    val action: String,

    /** EDITED / REFINED_THEN_SENT 일 때 사장님 최종 보낸 text. */
    val finalSentText: String?,

    /** EDITED 의 (suggestion vs finalSent) Levenshtein 거리. UI 채택률 표시 + 학습용. */
    val editDistance: Int?,

    val createdAtMs: Long,

    /** 서버 batch 보고 완료 여부. false = pending, true = 보고 완료. */
    val reportedToServer: Boolean = false
)

/**
 * action enum 의 string 상수. Entity 가 sql column 이라 enum 직접 못 쓰고 string.
 */
object SuggestionEventAction {
    const val SENT_AS_IS = "SENT_AS_IS"
    const val EDITED = "EDITED"
    const val REFINED_THEN_SENT = "REFINED_THEN_SENT"
    const val IGNORED = "IGNORED"
    const val DISMISSED = "DISMISSED"
}
