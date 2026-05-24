package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 한 번호에 대한 AI 요약 캐시. P0+P1+P2 의 세 서버 endpoint 결과를 통합 저장.
 *
 * 키 = 끝 8자리 (한국 번호 unique 가정). Customer 가 없어도 (SMS-only 카드) 저장 가능.
 *
 * 무효화: latestMessageTimestampMs 가 새 메시지 들어오면 stale 판정 → 백그라운드 재호출.
 */
@Entity(tableName = "ai_summary_cache")
data class AiSummaryEntity(
    @PrimaryKey val phoneSuffix: String,

    /** P0: HomeScreen 카드 한 줄 요약 (15-25자). null = 아직 생성 안 됨. */
    val cardSummary: String? = null,

    /**
     * P1: ChatScreen 상단 요약 줄들 (이모지+텍스트, 3-5줄). JSON array string.
     * 예: ["📍 마곡 신축", "💰 견적 미발송", "📅 5/26 시공 의향"]
     */
    val conversationSummaryJson: String? = null,

    /** P1: current_stage enum (inquiry/photo_pending/.../done/as_needed). */
    val conversationStage: String? = null,

    /**
     * P2: AI 제안 박스. NextAction 객체 JSON.
     * action_type, title, subtitle, primary_action, urgency 포함.
     */
    val nextActionJson: String? = null,

    /** 무효화 기준 — 이 ts 이후 새 메시지 들어왔으면 stale. 0 = 한 번도 안 생성. */
    val latestMessageTimestampMs: Long = 0L,

    val generatedAtMs: Long = 0L
)
