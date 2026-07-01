package com.detailline.callfollowcrm.data

import com.detailline.callfollowcrm.data.repository.CachedMessageRepository
import com.detailline.callfollowcrm.data.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 시스템 SMS/MMS 캐시 백그라운드 prefetcher.
 *
 * 의도:
 *  - ChatViewModel 3-stage 로드는 "캐시가 있으면 즉시 표시" 가 전제.
 *  - 캐시가 비어있으면 첫 진입 시 SMS 쿼리(~100ms) + MMS 쿼리(수초) 기다림.
 *  - 그래서 (a) 앱 시작 직후, (b) SMS 수신 직후, (c) HomeScreen 가시 카드 노출 시 백그라운드로 캐시 채움.
 *
 * 정책:
 *  - 권한 없으면 silent skip.
 *  - 같은 suffix 중복 prefetch 방지 — Mutex + TTL dedup.
 *  - 실패는 silent (다음 진입 때 어차피 ChatViewModel 가 다시 시도).
 *
 * Dedup:
 *  - 같은 번호 prefetch 가 [DEDUP_WINDOW_MS] 내에 또 요청되면 skip. HomeScreen 스크롤할 때
 *    같은 카드가 여러 번 visible/invisible 토글돼도 반복 IO 방지.
 *  - 새 SMS 수신은 dedup 우회 (forceRefresh=true) — 방금 도착한 메시지 즉시 반영해야 하므로.
 */
class SmsCachePrefetcher(
    private val smsRepository: SmsRepository,
    private val cachedMessageRepository: CachedMessageRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val lastPrefetchedAtMs = ConcurrentHashMap<String, Long>()

    /**
     * 앱 시작 시 1회. 최근 N개 SMS 연락처 의 캐시 미리 채움.
     * HomeScreen prefetch 가 메인이지만 deep-link 진입 (알림에서 ChatScreen 바로) 케이스 보장용.
     */
    fun prefetchRecentContacts(contactLimit: Int = 20): Job = scope.launch {
        if (!smsRepository.hasReadPermission()) return@launch
        val contacts = runCatching {
            smsRepository.queryRecentContacts(scanLimit = 500, contactLimit = contactLimit)
        }.getOrDefault(emptyList())
        for (contact in contacts) {
            prefetchOne(contact.address, forceRefresh = false)
        }
    }

    /** HomeScreen 등에서 한 번에 여러 번호 prefetch. dedup 적용. */
    fun prefetchForNumbers(phoneNumbers: Collection<String>): Job = scope.launch {
        if (!smsRepository.hasReadPermission()) return@launch
        for (phone in phoneNumbers) {
            prefetchOne(phone, forceRefresh = false)
        }
    }

    /**
     * 한 번호 prefetch. SmsReceiver 가 새 SMS 받았을 때 사용 (forceRefresh=true 강제).
     */
    fun prefetchForNumber(phoneNumber: String, forceRefresh: Boolean = true): Job = scope.launch {
        if (!smsRepository.hasReadPermission()) return@launch
        prefetchOne(phoneNumber, forceRefresh)
    }

    private suspend fun prefetchOne(phoneNumber: String, forceRefresh: Boolean) {
        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.length < 7) return
        val suffix = digits.takeLast(8)

        if (!forceRefresh) {
            val last = lastPrefetchedAtMs[suffix] ?: 0L
            if (System.currentTimeMillis() - last < DEDUP_WINDOW_MS) return
        }

        mutex.withLock {
            runCatching {
                val freshSms = smsRepository.querySmsOnly(phoneNumber)
                cachedMessageRepository.replaceSmsOnlyForSuffix(suffix, freshSms)
            }
            runCatching {
                // 백그라운드 prefetch 는 최근 MMS 만 가볍게(scanLimit 축소) — 20연락처×2000 스캔이 MMS provider 를
                //   40,000행 훑어 앱 전체를 느리게 했음. 최근 것만 캐시 → 활성 대화는 즉시 표시.
                //   옛 사진 전체(2000)는 사용자가 실제로 그 대화 열 때 ChatViewModel stage 3 가 on-demand 로 채움. (2026-07-02 사장님)
                val freshMms = smsRepository.queryMmsOnly(phoneNumber, scanLimit = PREFETCH_MMS_SCAN_LIMIT)
                cachedMessageRepository.replaceMmsOnlyForSuffix(suffix, freshMms)
            }
            lastPrefetchedAtMs[suffix] = System.currentTimeMillis()
        }
    }

    companion object {
        /** 같은 번호 재요청 무시 윈도우 (60초). HomeScreen 스크롤로 인한 중복 호출 방지. */
        private const val DEDUP_WINDOW_MS = 60_000L
        /**
         * 백그라운드 prefetch 의 MMS 스캔 상한. on-demand(ChatViewModel)는 2000 그대로 —
         * 여기선 최근 것만 가볍게 캐시해 앱 시작 시 MMS provider 부하(20연락처×2000)를 크게 줄임. (2026-07-02)
         */
        private const val PREFETCH_MMS_SCAN_LIMIT = 500
    }
}
