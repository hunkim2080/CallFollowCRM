package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 🕐 **예약 문자** — 지금 쓰고 **예약한 시각에 자동 발송**되는 한 통.
 *   (2026-10-05 사장님 요청. 프로토 56gF4zzgC9UYVBihJcsN9Z)
 *
 *   발송은 [com.detailline.callfollowcrm.service.ScheduledSmsReceiver] 가 시각에 꺼내
 *   [com.detailline.callfollowcrm.util.SmsSender] 로 보낸다. 글은 항상 보내지고(기본앱 아니어도),
 *   사진(MMS)은 **기본 문자앱일 때만** 가능 → 못 가면 `resultNote` 에 남기고 알린다(§13① 조용히 안 삼킴).
 */
@Entity(
    tableName = "scheduled_sms",
    indices = [Index("status", "sendAtMs", name = "idx_scheduled_sms_status_at")]
)
data class ScheduledSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** 받는 사람 번호(발송에 쓰는 진짜 값). */
    val phoneNumber: String,

    /** 예약함 목록에 보일 이름 — 넣는 순간의 스냅샷(없으면 번호). */
    val recipientName: String,

    /** 보낼 글. */
    val body: String,

    /** 첨부 사진 uri 들. 줄바꿈(\n)으로 이어 붙임. 없으면 "". (쉼표는 uri 에 섞일 수 있어 피함) */
    val photoUris: String = "",

    /** 예약 시각(epoch ms). */
    val sendAtMs: Long,

    /** 'pending'(대기) | 'sent'(보냄) | 'failed'(못 보냄) | 'canceled'(사장님 취소). */
    val status: String = STATUS_PENDING,

    /** 넣은 시각. */
    val createdAtMs: Long,

    /** 결과 한 줄 — "사진 못 갔어요" 등. 사장님께 그대로 보여준다. */
    val resultNote: String? = null
) {
    /** 첨부 사진 uri 리스트로. */
    fun photoUriList(): List<String> = if (photoUris.isBlank()) emptyList() else photoUris.split("\n").filter { it.isNotBlank() }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SENT = "sent"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELED = "canceled"

        /** uri 리스트 → 저장용 문자열. */
        fun joinUris(uris: List<String>): String = uris.filter { it.isNotBlank() }.joinToString("\n")
    }
}
