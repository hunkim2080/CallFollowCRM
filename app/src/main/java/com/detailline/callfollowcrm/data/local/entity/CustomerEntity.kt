package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["phoneNumber"], unique = true),
        Index("scheduledWorkDate"),
        Index("categoryId")
    ]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val name: String? = null,
    /** 사장님 정의 카테고리 (1:1). null = 미분류. AI 자동 분류 + 사장님 수동. */
    val categoryId: Long? = null,
    val memo: String = "",
    /**
     * 현장 주소 — 사장님이 직접 등록 (2026-05-28). DB v15.
     *   메시지 자동 추출 (AddressExtractor) 보다 우선. null = 미등록 (자동 추출이 fallback).
     *   카드 펼침 [📍 길찾기] 가 1순위로 활용. §13 끝나면 좌표(lat/lng) 까지 같이 저장 예정.
     */
    val address: String? = null,
    /** 시공 예약 날짜. 그 날 00:00 시점의 epoch ms. 미정이면 null. */
    val scheduledWorkDate: Long? = null,
    /**
     * 리드 온도 (LeadHeat enum name). 통화 직후 사장님이 빠르게 분류.
     * categoryId 와 직교 (categoryId = 카테고리, leadHeat = 전환 가능성). null = 미분류.
     */
    val leadHeat: String? = null,
    /**
     * 입금 정보. amount 단위 = 원. paidAt = 받은 시각(ms).
     * null = 아직 안 받음. amount 만 있고 paidAt null 도 가능 (= 금액 정해졌지만 미수금).
     */
    val depositAmount: Long? = null,
    val depositPaidAt: Long? = null,
    val balanceAmount: Long? = null,
    val balancePaidAt: Long? = null,
    /**
     * 2026-05-30 사장님 #4 통점 — 총금액 (시공비 총액).
     * 입금 카드의 잔금 자동 계산 기준: balance = totalAmount - depositAmount.
     * null = 사장님 미입력. balanceAmount 가 직접 박혀있으면 그게 우선 (수동 우선).
     */
    val totalAmount: Long? = null,
    /**
     * 시공 시작 시각 — 자정부터의 분(0~1439). null = 시간 미정/종일. DB v24 (2026-06-01).
     * scheduledWorkDate(날짜, startOfDay 정규화) 와 분리 — 날짜 로직은 그대로 두고 시간만 별도 저장.
     */
    val scheduledWorkMinutes: Int? = null,
    /** 시공 기간(며칠). 기본 1 = 당일. 여러 날 현장이면 2+. DB v24 (2026-06-01). */
    val scheduledWorkDays: Int = 1,
    /** A/S 예약일 — 시공 예약(scheduledWorkDate)과 **별개**. null = A/S 없음. 무료(정산 무관). DB v43 (2026-08-01 사장님). */
    val asScheduledDate: Long? = null,
    /** A/S 기간(며칠). 기본 1. 여러 날 A/S면 2+. DB v43. */
    val asScheduledDays: Int = 1,
    /**
     * 시공 완료 처리 시각(ms). null = 미완료. DB v31 (2026-06-08 사장님 #2).
     *   오늘 시공 히어로 [완료]→완료처리 시 set → 그 현장이 히어로에서 빠진다(완료 반영).
     */
    val workCompletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    /**
     * '시공 완료' 판정 — 완료처리([workCompletedAt])가 됐거나 **잔금([balancePaidAt])을 받았으면** 완료.
     * 사장님 지시(2026-08-18): "잔금 받으면 = 완료" 통일. 앱 고객목록(CustomersScreen)이 이미 쓰는 기준과 동일.
     * Room 은 생성자 밖 계산 프로퍼티를 컬럼으로 안 만든다 → DB 마이그레이션 불필요(안전).
     */
    val isWorkDone: Boolean get() = workCompletedAt != null || balancePaidAt != null

    /** 완료로 볼 시각 — 완료처리 시각 우선, 없으면 잔금 받은 시각. 정렬·날짜 귀속용. */
    val doneAtMs: Long? get() = workCompletedAt ?: balancePaidAt
}
