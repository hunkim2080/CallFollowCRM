package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 시스템 SMS/MMS 의 로컬 캐시.
 *
 * 의도: ChatScreen 진입 시 시스템 ContentResolver 쿼리가 무거워 (MMS 1000건 + addr/parts 추가 호출)
 *      20초+ 대기 발생. → 캐시 즉시 표시 + 백그라운드 동기화로 체감 즉시.
 *
 * 키 디자인:
 * - localId = Room autoIncrement
 * - (systemId, isMms) = 시스템 DB 의 원본 row 식별. sms id 와 mms id 가 서로 다른 도메인이라
 *   isMms 와 함께 묶어 unique index.
 * - phoneSuffix = 끝 8자리. ChatScreen 쿼리 키.
 */
@Entity(
    tableName = "cached_messages",
    indices = [
        Index(value = ["phoneSuffix"]),
        Index(value = ["systemId", "isMms"], unique = true)
    ]
)
data class CachedMessageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val systemId: Long,
    val isMms: Boolean,
    val phoneSuffix: String,
    val address: String?,
    val body: String,
    val dateMs: Long,
    val sent: Boolean,
    /** MMS image URI 들을 "|" 로 join 한 CSV. 빈 문자열 = 첨부 없음. */
    val imageUrisCsv: String,
    /** MMS 동영상 URI 들을 "|" 로 join 한 CSV. 빈 문자열 = 동영상 없음. (2026-07-13 사장님) */
    val videoUrisCsv: String = "",
    val cachedAtMs: Long
)
