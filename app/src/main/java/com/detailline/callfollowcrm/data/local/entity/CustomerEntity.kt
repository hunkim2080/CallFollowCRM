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
    val createdAt: Long,
    val updatedAt: Long
)
