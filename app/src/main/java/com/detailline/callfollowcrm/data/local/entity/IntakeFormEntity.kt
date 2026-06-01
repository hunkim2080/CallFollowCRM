package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 시공접수서 (맥미니 §19) 로컬 캐시. DB v26 (2026-06-02).
 *
 * 발급 시점에 token + 기본 정보 박음 → 홈 진입 시 phone 별로 [GET /api/intake-form/status] 폴링하여
 * submitted_at_ms / payload / 견적 데이터까지 동기화. 사장님이 [1탭 확정] 누르면 settledAtMs 박힘.
 */
@Entity(
    tableName = "intake_forms",
    indices = [
        Index(value = ["token"], unique = true),
        Index("phoneSuffix"),
        Index("submittedAtMs"),
        Index("expiresAtMs")
    ]
)
data class IntakeFormEntity(
    @PrimaryKey val token: String,
    /** 고객 phone 의 끝 8자리 (Customer 매칭용). 발급 시 phone 에서 추출. */
    val phoneSuffix: String,
    /** 발급 시점의 phone 원본 (status() 폴 호출용 — 서버는 정규화된 phone 으로 저장). */
    val phone: String,
    val customerName: String? = null,
    /** 폼 URL (서버 발급, SMS 본문에 prefill). */
    val url: String,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
    /** null = 미작성. 값 박히면 고객이 폼 제출 완료. */
    val submittedAtMs: Long? = null,
    /** 제출 payload JSON 원문 — { contact_phone, road_address, building_detail, memo, source } */
    val payloadJson: String? = null,

    // ── 사장님 발급 시 박은 견적 데이터 (status 응답에도 들어있음 — 동기화) ──
    val scheduledAtMs: Long = 0L,
    val scheduledDays: Int = 1,
    val totalMan: Int = 0,
    val depositAmountKrw: Long = 0L,
    val depositMode: String = "none",
    val depositRatioPct: Int? = null,
    val bizName: String? = null,
    /** 견적 항목 JSON 배열 — [{name, price_man, unit?, area?}] */
    val estimateItemsJson: String? = null,

    /** 사장님이 [1탭 확정] 누른 시각. null = 미처리 (홈 상단 "들어왔어요" 카드 표시). */
    val settledAtMs: Long? = null,

    val lastSyncedAtMs: Long = 0L,
    val createdAt: Long
)
