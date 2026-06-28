package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 시공접수서 제출 이벤트 (2026-06-05) — 고객이 접수서를 "작성 완료"한 사실을 채팅 타임라인에 띄우기 위한 기록.
 *   IntakeSyncManager 가 GET /api/quote/submissions 폴링으로 새 제출을 감지하면 알림과 함께 이 행을 남긴다.
 *   ChatViewModel 이 phoneSuffix 로 observe → 채팅 타임라인에 통화 카드(CallSegment)처럼 이벤트 카드로 표시.
 *   token unique → 같은 제출이 폴링마다 중복 기록되지 않음.
 */
@Entity(
    tableName = "intake_events",
    indices = [
        Index("phoneSuffix"),
        Index(value = ["token"], unique = true)
    ]
)
data class IntakeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 고객 번호 끝 8자리 — ChatViewModel 의 suffix 매칭과 동일 규칙. */
    val phoneSuffix: String,
    /** 서버 접수서 토큰 — 중복 기록 방지 키. */
    val token: String,
    val customerName: String,
    /** 고객이 접수서를 제출(작성 완료)한 시각 = 타임라인 노출 시각. */
    val submittedAtMs: Long,
    /** 입력된 현장 주소(동 포함). 없으면 null. */
    val address: String? = null,
    /** 확정 시공일 라벨 "M/d(E)". 없으면 null. */
    val dateLabel: String? = null,
    /** 견적 총액(만원). 0 이하면 null 로 저장. */
    val totalManwon: Int? = null,
    val createdAt: Long,
    /** 견적서 발급 때 사장님이 고른 시공 항목 이름들(", " 결합) — 확인 문자 '시공내용'. 없으면 null. (2026-06-28) */
    val itemsText: String? = null,
    /** 사장님이 '확인했어요' 눌러 고객에게 확인 문자를 보낸 시각. null = 아직 안 보냄. (2026-06-28) */
    val confirmedAt: Long? = null
)
