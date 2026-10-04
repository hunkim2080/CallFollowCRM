package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 📮 **우체통** — 오프라인에서 서버로 보내다 만 「명령」을 모아두고, 길이 뚫리면 마저 보낸다.
 *   설계: docs/DESIGN_offline_outbox.md §3. DB v62 신설.
 *
 *   ⚠️ **보낼 목록은 이 표 하나다.** 사진용 따로·협업용 따로 만들지 않는다(§12 두 벌 금지).
 *      사진은 바이트를 넣지 않고 **참조(photoId)**만 넣는다 — 보낼 때 파일을 읽는다.
 *      보낸 행은 **지운다**(표가 안 커진다). dead 만 사장님이 치울 때까지 남는다.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index("status", "nextAttemptAtMs", name = "idx_outbox_status_next"),
        Index("kind", "targetKey", name = "idx_outbox_kind_target"),
        Index("opKey", name = "idx_outbox_opkey", unique = true)
    ]
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** 종류. OutboxKind.wire 값. "collab_reschedule" | "collab_address" | "collab_end" | "site_photo" | … */
    val kind: String,

    /** 순서·합치기의 단위. shareId / photoId. */
    val targetKey: String,

    /** 편지 일련번호 = 멱등키(UUID). 재시도해도 같은 키. 2단계부터 헤더 X-Op-Key 로 보냄. */
    val opKey: String,

    /** 넣을 때 로그인 번호(숫자만). 다른 번호로 로그인하면 안 보낸다. */
    val ownerPhone: String,

    /** 그 kind 의 handler 만 읽는다. 넣는 순간의 값. 사진은 {"photoId":123} 참조만. */
    val payloadJson: String,

    /** 처음 넣은 시각. 합쳐져도 유지 → 「며칠째」의 기준. */
    val createdAtMs: Long,

    val attempts: Int = 0,

    /** 백오프. 0 = 지금 바로. */
    val nextAttemptAtMs: Long = 0,

    /** "HTTP 503" / "offline" — 진단·목록에 그대로 보여준다. */
    val lastError: String? = null,

    /** 'pending' | 'dead'. 보낸 것은 지운다. */
    val status: String = STATUS_PENDING,

    /** 사장님 말로("서버가 이 현장을 모른다고 했어요"). */
    val deadReason: String? = null
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_DEAD = "dead"
    }
}
