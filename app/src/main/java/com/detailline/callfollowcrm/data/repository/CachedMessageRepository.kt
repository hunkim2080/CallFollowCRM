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

    /** 로컬 보존된 발신(앱에서 보냈지만 시스템 문자함엔 없는, systemId<0) 메시지만. */
    suspend fun loadLocalSent(suffix: String, limit: Int = 200): List<SmsRepository.SmsMessage> =
        dao.queryLocalSentBySuffix(suffix, limit).map { it.toSmsMessage() }

    /** SMS 만 교체. MMS 캐시는 유지. 로컬 발신 보존본(systemId<0)도 유지(provider 출신만 교체). */
    suspend fun replaceSmsOnlyForSuffix(suffix: String, freshSms: List<SmsRepository.SmsMessage>) {
        dao.clearProviderSmsForSuffix(suffix)
        val now = System.currentTimeMillis()
        dao.insertAll(freshSms.map { it.toCachedEntity(suffix, isMms = false, cachedAtMs = now) })
    }

    /**
     * 앱에서 보낸 메시지를 로컬에 영구 보존.
     * 기본 문자앱이 아니면 발신이 시스템 문자함(content://sms/sent)에 안 적혀 재진입 시 사라짐 →
     * 우리 캐시에 systemId<0(음수) 행으로 남겨 화면에서 유지되게 한다.
     */
    suspend fun persistLocalSent(suffix: String, msg: SmsRepository.SmsMessage) {
        dao.insertAll(
            listOf(msg.toCachedEntity(suffix, isMms = false, cachedAtMs = System.currentTimeMillis()))
        )
    }

    /** MMS 만 교체(전체 스냅샷). 챗 열 때 stage-3(2000 스캔) authoritative 경로에서만 사용 — 삭제도 반영. */
    suspend fun replaceMmsOnlyForSuffix(suffix: String, freshMms: List<SmsRepository.SmsMessage>) {
        dao.clearForSuffixByType(suffix, isMms = true)
        val now = System.currentTimeMillis()
        dao.insertAll(freshMms.map { it.toCachedEntity(suffix, isMms = true, cachedAtMs = now) })
    }

    /**
     * MMS 를 캐시에 '누적'(merge) — 기존 것을 지우지 않고 새 MMS 만 upsert.
     *   ⚠️ prefetch(부분 스캔, scanLimit 작음)가 replace(clear+insert)로 기존 완전한 캐시를 지워, 사장님이 사진을
     *      계속 보내 전역 MMS 가 빠르게 쌓이면 이 대화의 예전 사진이 '최근 N(전역)' 밖으로 밀려 캐시가 쪼그라들고
     *      "진입 시 예전 사진 안 보임" 버그가 났다(2026-07-02 사장님). merge 는 절대 캐시를 줄이지 않는다.
     *   (systemId, isMms) unique index 라 insertAll(REPLACE)가 같은 원본 row 는 갱신, 새 row 는 추가,
     *   안 스캔된 옛 row 는 유지. 삭제 반영/전체 스냅샷은 챗 열 때 replaceMmsOnlyForSuffix 가 담당.
     */
    suspend fun mergeMmsForSuffix(suffix: String, freshMms: List<SmsRepository.SmsMessage>) {
        if (freshMms.isEmpty()) return
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
