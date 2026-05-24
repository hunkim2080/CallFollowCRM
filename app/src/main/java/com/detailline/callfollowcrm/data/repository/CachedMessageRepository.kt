package com.detailline.callfollowcrm.data.repository

import android.net.Uri
import com.detailline.callfollowcrm.data.local.dao.CachedMessageDao
import com.detailline.callfollowcrm.data.local.entity.CachedMessageEntity

/**
 * 시스템 SMS/MMS 로컬 캐시 Repository.
 *
 * 사용 패턴 (ChatViewModel.loadMessages — 3-stage):
 *   stage 1: load(suffix) — 캐시 즉시 표시
 *   stage 2: SmsRepository.querySmsOnly() → replaceSmsOnlyForSuffix() — 텍스트 빠르게 갱신
 *   stage 3: SmsRepository.queryMmsOnly() → replaceMmsOnlyForSuffix() — 사진 백그라운드 갱신
 *
 * isMms 식별:
 *  - SMS 와 MMS 는 시스템 DB 의 ID 공간이 다르고, 본 entity 의 unique index 는 (systemId, isMms).
 *  - 텍스트만 있는 MMS (긴 문자 자동 변환) 도 isMms=true 로 저장돼야 SMS id 와 충돌 없음.
 *  - 따라서 imageUris 유무로 추정하지 않고, 호출 측이 명시적으로 알려준다 (replaceSmsOnly / replaceMmsOnly).
 */
class CachedMessageRepository(private val dao: CachedMessageDao) {

    suspend fun load(suffix: String, limit: Int = 500): List<SmsRepository.SmsMessage> =
        dao.queryBySuffix(suffix, limit).map { it.toSmsMessage() }

    suspend fun loadMmsOnly(suffix: String, limit: Int = 500): List<SmsRepository.SmsMessage> =
        dao.queryBySuffixAndType(suffix, isMms = true, limit = limit).map { it.toSmsMessage() }

    /** SMS 만 교체. MMS 캐시는 유지. */
    suspend fun replaceSmsOnlyForSuffix(suffix: String, freshSms: List<SmsRepository.SmsMessage>) {
        dao.clearForSuffixByType(suffix, isMms = false)
        val now = System.currentTimeMillis()
        dao.insertAll(freshSms.map { it.toCachedEntity(suffix, isMms = false, cachedAtMs = now) })
    }

    /** MMS 만 교체. SMS 캐시는 유지. */
    suspend fun replaceMmsOnlyForSuffix(suffix: String, freshMms: List<SmsRepository.SmsMessage>) {
        dao.clearForSuffixByType(suffix, isMms = true)
        val now = System.currentTimeMillis()
        dao.insertAll(freshMms.map { it.toCachedEntity(suffix, isMms = true, cachedAtMs = now) })
    }

    private fun CachedMessageEntity.toSmsMessage(): SmsRepository.SmsMessage {
        val uris = if (imageUrisCsv.isBlank()) emptyList()
        else imageUrisCsv.split("|").mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        return SmsRepository.SmsMessage(
            id = systemId,
            address = address,
            body = body,
            dateMs = dateMs,
            sent = sent,
            imageUris = uris
        )
    }

    private fun SmsRepository.SmsMessage.toCachedEntity(
        suffix: String,
        isMms: Boolean,
        cachedAtMs: Long
    ): CachedMessageEntity = CachedMessageEntity(
        systemId = id,
        isMms = isMms,
        phoneSuffix = suffix,
        address = address,
        body = body,
        dateMs = dateMs,
        sent = sent,
        imageUrisCsv = imageUris.joinToString("|") { it.toString() },
        cachedAtMs = cachedAtMs
    )
}
