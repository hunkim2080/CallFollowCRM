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
    val createdAt: Long,
    val updatedAt: Long
)
