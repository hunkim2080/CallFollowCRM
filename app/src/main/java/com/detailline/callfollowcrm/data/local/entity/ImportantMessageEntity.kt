package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 사장님이 ⭐ 표시한 중요 메시지.
 *
 * 시스템 SMS/MMS 자체는 갤럭시가 관리하고 id 가 시간 지나면 바뀔 수 있어서
 * (phoneNumber, messageDateMs, sent) 조합으로 식별. body 는 복원/표시용으로 보관.
 *
 * 같은 번호로 분쟁 시 사장님이 빠르게 찾아보는 용도.
 */
@Entity(
    tableName = "important_messages",
    indices = [
        Index(value = ["phoneNumber"]),
        Index(value = ["phoneNumber", "messageDateMs", "sent"], unique = true)
    ]
)
data class ImportantMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val customerId: Long? = null,
    /** 원본 메시지 본문 (시스템 SMS/MMS 사라져도 표시 가능하게). */
    val messageBody: String,
    /** 원본 메시지 시각 (ms). 검색 키. */
    val messageDateMs: Long,
    /** true = 보낸 메시지, false = 받은 메시지. 검색 키 일부. */
    val sent: Boolean,
    /** 사장님이 ⭐ 찍은 시각. */
    val createdAt: Long
)
