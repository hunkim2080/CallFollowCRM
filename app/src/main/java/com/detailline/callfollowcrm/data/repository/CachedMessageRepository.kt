package com.detailline.callfollowcrm.data.repository

import android.net.Uri
import com.detailline.callfollowcrm.data.local.dao.CachedMessageDao
import com.detailline.callfollowcrm.data.local.entity.CachedMessageEntity

/**
 * 시스템 SMS/MMS 로컬 캐시 Repository.
 *
 * 사용 패턴 (ChatViewModel.loadMessages):
 *   1. queryBySuffix() 로 즉시 표시
 *   2. SmsRepository.queryByPhone() 백그라운드 호출
 *   3. replaceForSuffix() 로 캐시 교체
 *   4. StateFlow 다시 emit
 *
 * 단순함 우선: 번호별 캐시 통째로 교체 (diff 계산 X).
 */
class CachedMessageRepository(private val dao: CachedMessageDao) {

    suspend fun load(suffix: String, limit: Int = 500): List<SmsRepository.SmsMessage> =
        dao.queryBySuffix(suffix, limit).map { it.toSmsMessage() }

    suspend fun replaceForSuffix(suffix: String, fresh: List<SmsRepository.SmsMessage>) {
        dao.clearForSuffix(suffix)
        val now = System.currentTimeMillis()
        dao.insertAll(fresh.map { it.toCachedEntity(suffix, now) })
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

    private fun SmsRepository.SmsMessage.toCachedEntity(suffix: String, cachedAtMs: Long): CachedMessageEntity =
        CachedMessageEntity(
            systemId = id,
            isMms = imageUris.isNotEmpty(),
            phoneSuffix = suffix,
            address = address,
            body = body,
            dateMs = dateMs,
            sent = sent,
            imageUrisCsv = imageUris.joinToString("|") { it.toString() },
            cachedAtMs = cachedAtMs
        )
}
