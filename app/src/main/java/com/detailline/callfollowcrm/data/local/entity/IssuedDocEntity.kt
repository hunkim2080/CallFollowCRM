package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 발행 문서 이력 (2026-07-07 사장님) — 사장님이 고객에게 "발행"한 견적서/시공접수서 스냅샷.
 *   발행 순간(견적서 이미지 보내기 / 접수서 링크 발급)에 1건 기록 →
 *   고객 상세 "발행 이력" 리스트 + (다음) 채팅 타임라인 카드로 다시 열람(확인 개념).
 *   docJson = QuoteDocData 직렬화(견적서 재열람용). intake 는 url/token 추가.
 *   phoneSuffix = 끝 8자리(챗 매칭 규칙 동일), customerId = 고객상세 필터.
 */
@Entity(
    tableName = "issued_docs",
    indices = [Index("phoneSuffix"), Index("customerId")]
)
data class IssuedDocEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 고객 번호 끝 8자리 — ChatViewModel suffix 매칭과 동일. */
    val phoneSuffix: String,
    /** 고객 id — 고객상세 발행 이력 필터. 저장 시점에 ensureCustomerId 로 채움. */
    val customerId: Long? = null,
    /** "quote"(견적서 이미지) | "intake"(시공접수서 링크) */
    val kind: String,
    /** 받는 분 표시명(고객명). */
    val recipient: String? = null,
    /** 견적 총액(원). 리스트 요약 표시용(부가세 포함 총액). */
    val totalWon: Long = 0,
    /** 시공 예정일(ms). null=협의 후 확정. */
    val workDateMs: Long? = null,
    /** 시공 내용(품목명 ", " 결합) — 리스트 한 줄 요약. */
    val itemsText: String? = null,
    /** 특이사항 메모. */
    val memo: String? = null,
    /** QuoteDocData 전체 JSON — 견적서 다시 보기(재열람)용. intake 는 요약만 있으면 되어 최소 JSON. */
    val docJson: String,
    /** 시공접수서 링크 URL(intake 만). */
    val url: String? = null,
    /** 서버 접수서 토큰(intake 만). */
    val token: String? = null,
    /** 발행 시각 = 이력 정렬·표시 시각. */
    val issuedAtMs: Long
)
