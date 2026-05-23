package com.detailline.callfollowcrm.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["phoneNumber"], unique = true),
        Index("scheduledWorkDate")
    ]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val name: String? = null,
    @ColumnInfo(name = "status") val status: String,
    val memo: String = "",
    /** 시공 예약 날짜. 그 날 00:00 시점의 epoch ms. 미정이면 null. */
    val scheduledWorkDate: Long? = null,
    /**
     * 리드 온도 (LeadHeat enum name). 통화 직후 사장님이 빠르게 분류.
     * status 와는 다른 축 (status = funnel 위치, leadHeat = 전환 가능성).
     * null = 미분류.
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
