package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 정기문자 규칙 (2026-06-01, DB v25) — 시공 후 주기적으로 보낼 안부/점검 문자 정의.
 *
 * 정책(project-ringo): SEND_SMS = 명시 액션. 자동 발송 X.
 *   이 규칙은 "보낼 때가 됐어요" 를 사장님께 알려주는 기준일 뿐, 실제 발송은 사장님이 확인 후.
 *   → WorkManager 백그라운드 발송 안 함(삼성 배터리 신뢰성 + 정책). 앱 열 때 포그라운드 계산.
 *
 * 대상 = 시공일(scheduledWorkDate)이 잡힌 고객 중 targetCategoryId 일치(null=전체).
 * 주기 = 시공일로부터 intervalDays 마다 (예: 30=한 달 점검, 90=분기 안부).
 */
@Entity(tableName = "recurring_messages")
data class RecurringMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 대상 카테고리. null = 시공일 잡힌 모든 고객. */
    val targetCategoryId: Long? = null,
    /** 시공일로부터 며칠마다. 30/60/90 등. <=0 이면 비활성 취급. */
    val intervalDays: Int,
    /** 권장 발송 시각(자정부터 분). 표시용 — 발송은 사장님 직접. 기본 600=오전 10시. */
    val sendMinutes: Int = 600,
    /** 본문 템플릿. "{고객명}" 자리표시자 → 발송 시 고객 이름으로 치환. */
    val bodyTemplate: String,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)
