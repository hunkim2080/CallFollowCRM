package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 정기문자 처리 로그 (2026-06-01, DB v25) — 한 occurrence(규칙×고객×해당 회차일)를
 *   사장님이 보냈거나(SENT) 넘겼으면(DISMISSED) 기록 → "보낼 때 된" 목록에서 제외(중복 방지).
 *   다음 회차(occurrenceDayStartMs 가 다른 날)가 오면 새 키라 다시 노출.
 */
@Entity(
    tableName = "recurring_message_log",
    indices = [
        Index("ruleId"),
        Index(value = ["ruleId", "customerId", "occurrenceDayStartMs"], unique = true)
    ]
)
data class RecurringLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val customerId: Long,
    /** 이 회차의 기준 날(자정 ms) — 시공일 + k×intervalDays. */
    val occurrenceDayStartMs: Long,
    /** SENT(보냄) / DISMISSED(넘김). */
    val status: String,
    val createdAt: Long
) {
    companion object {
        const val STATUS_SENT = "SENT"
        const val STATUS_DISMISSED = "DISMISSED"
    }
}
