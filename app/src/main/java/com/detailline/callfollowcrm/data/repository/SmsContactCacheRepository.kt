package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.SmsContactCacheDao
import com.detailline.callfollowcrm.data.local.entity.SmsContactCacheEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SMS 연락처 캐시 Repository — HomeScreen 카드 풀스캔 통점 fix 의 토대 (2026-05-28).
 *
 * 변환 책임:
 *   - SmsRepository.SmsContact (풀스캔 결과) ↔ SmsContactCacheEntity (Room row)
 *   - HomeViewModel 은 SmsContact 그대로 사용 — entity 노출 안 함.
 */
class SmsContactCacheRepository(private val dao: SmsContactCacheDao) {

    fun observeAll(limit: Int = 500): Flow<List<SmsRepository.SmsContact>> =
        dao.observeAll(limit).map { entities -> entities.map { it.toContact() } }

    suspend fun findBySuffix(suffix: String): SmsRepository.SmsContact? =
        dao.findBySuffix(suffix)?.toContact()

    /** 풀스캔 결과로 통째 rebuild — 앱 첫 실행 또는 정기 정합성 체크. */
    suspend fun rebuildFromFullScan(contacts: List<SmsRepository.SmsContact>) {
        dao.deleteAll()
        if (contacts.isNotEmpty()) {
            dao.upsertAll(contacts.map { it.toEntity() })
        }
    }

    /**
     * 한 phone 만 incremental upsert (새 SMS 도착 / 사장님 발신 시).
     * 기존 row 있으면 lastBody/lastDateMs/lastSent 갱신, hasOwnerReply 는 OR (한 번 true 면 영구).
     * firstDateMsInScan 은 min (더 오래된 메시지가 발견되면 그것).
     */
    suspend fun upsertOne(contact: SmsRepository.SmsContact) {
        val existing = dao.findBySuffix(contact.normalizedSuffix)
        val merged = if (existing != null) {
            existing.copy(
                address = if (contact.lastDateMs >= existing.lastDateMs) contact.address else existing.address,
                lastBody = if (contact.lastDateMs >= existing.lastDateMs) contact.lastBody else existing.lastBody,
                lastDateMs = maxOf(existing.lastDateMs, contact.lastDateMs),
                lastSent = if (contact.lastDateMs >= existing.lastDateMs) contact.lastSent else existing.lastSent,
                hasOwnerReply = existing.hasOwnerReply || contact.hasOwnerReply,
                firstDateMsInScan = minOf(existing.firstDateMsInScan, contact.firstDateMsInScan),
                updatedAt = System.currentTimeMillis()
            )
        } else {
            contact.toEntity()
        }
        dao.upsert(merged)
    }

    suspend fun count(): Int = dao.count()

    private fun SmsContactCacheEntity.toContact(): SmsRepository.SmsContact =
        SmsRepository.SmsContact(
            address = address,
            normalizedSuffix = normalizedSuffix,
            lastBody = lastBody,
            lastDateMs = lastDateMs,
            lastSent = lastSent,
            hasOwnerReply = hasOwnerReply,
            firstDateMsInScan = firstDateMsInScan
        )

    private fun SmsRepository.SmsContact.toEntity(): SmsContactCacheEntity =
        SmsContactCacheEntity(
            normalizedSuffix = normalizedSuffix,
            address = address,
            lastBody = lastBody,
            lastDateMs = lastDateMs,
            lastSent = lastSent,
            hasOwnerReply = hasOwnerReply,
            firstDateMsInScan = firstDateMsInScan,
            updatedAt = System.currentTimeMillis()
        )
}
