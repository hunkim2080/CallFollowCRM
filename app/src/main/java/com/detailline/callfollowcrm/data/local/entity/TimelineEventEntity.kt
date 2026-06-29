package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 챗 타임라인 "변경/처리 이력" 이벤트 (2026-06-30 사장님) — 그 번호와의 중요한 순간을 시각과 함께 카드로 남김.
 *   type: "schedule"(시공일정 변경) / "amount"(시공금액 변경) / "balance_paid"(잔금 받음 처리).
 *   고객 상세에서 사장님이 일정/금액/잔금을 바꾸면 CustomerDetailViewModel 이 기록 →
 *   ChatScreen 타임라인에 시스템 카드 + [고객에게 알리기](확인 후 발송).
 *   phoneSuffix = 끝 8자리 (ChatViewModel suffix 매칭과 동일 규칙).
 */
@Entity(
    tableName = "timeline_events",
    indices = [Index("phoneSuffix")]
)
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 고객 번호 끝 8자리 — ChatViewModel suffix 매칭과 동일. */
    val phoneSuffix: String,
    /** "schedule" | "amount" | "balance_paid" */
    val type: String,
    /** 변경 전 표시값 ("6/30(화)", "5만원"). 없으면 null. */
    val oldValue: String? = null,
    /** 변경 후 표시값 ("7/2(목)", "8만원"). */
    val newValue: String? = null,
    /** 금액 변경 이유(선택) — 사장님 본인 기억용. 고객 알림엔 안 실음. */
    val reason: String? = null,
    /** 처리/변경된 시점 = 타임라인 노출 시각. */
    val createdAt: Long,
    /** 사장님이 '고객에게 알리기' 눌러 문자 보낸 시각. null = 아직. */
    val notifiedAt: Long? = null
)
