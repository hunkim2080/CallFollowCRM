package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 직접 현금 기록 (정산 Phase 2, DB v20).
 *
 * 현장 밖에서 오간 돈 — 자재비/일당 현금/기타 입금처럼 고객 시공과 직접 안 묶이는 흐름을
 * 사장님이 손으로 적는 것. 현금흐름 달력에서 settle 파생 수입과 합쳐 보여줌.
 *
 *  - isIncome: true = 들어오는 돈(수입), false = 나가는 돈(지출).
 *  - isDone:   true = 이미 받음/냄(확정), false = 받을/낼 예정(추정).
 *  - dayStartMs: 이 돈이 잡히는 날 00:00 epoch ms.
 *  - amount: 항상 양수(원). 부호는 isIncome 으로 표현.
 */
@Entity(
    tableName = "manual_cash",
    indices = [Index(value = ["dayStartMs"], name = "idx_manual_cash_dayStartMs")]
)
data class ManualCashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayStartMs: Long,
    val amount: Long,
    val isIncome: Boolean,
    val isDone: Boolean,
    val label: String = "",
    val createdAt: Long,
    val updatedAt: Long
)
