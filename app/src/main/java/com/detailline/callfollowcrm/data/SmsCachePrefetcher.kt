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

/**
 * 시스템 SMS/MMS 캐시 백그라운드 prefetcher.
 *
 * 의도:
 *  - ChatViewModel 3-stage 로드는 "캐시가 있으면 즉시 표시" 가 전제.
 *  - 캐시가 비어있으면 사장님이 처음 들어갈 때 여전히 SMS 쿼리(~100ms) + MMS 쿼리(수초) 기다림.
 *  - 그래서 앱 시작 직후 / SMS 수신 직후 백그라운드에서 최근 N개 번호의 캐시를 미리 채움.
 *
 * 정책:
 *  - 권한 없으면 silent skip.
 *  - 같은 suffix 중복 prefetch 방지 — Mutex 로 직렬화.
 *  - 실패는 silent (다음 진입 때 어차피 ChatViewModel 가 다시 시도).
 */
class SmsCachePrefetcher(
    private val smsRepository: SmsRepository,
    private val cachedMessageRepository: CachedMessageRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /**
     * 앱 시작 시 1회. 최근 N개 연락처 (recent SMS contact 목록 기준) 의 캐시를 미리 채움.
     * 보통 사장님이 자주 보는 번호 = 최근 SMS 연락처 위쪽 = ChatScreen 진입 확률 높음.
     */
    fun prefetchRecentContacts(contactLimit: Int = 20): Job = scope.launch {
        if (!smsRepository.hasReadPermission()) return@launch
        val contacts = runCatching {
            smsRepository.queryRecentContacts(scanLimit = 500, contactLimit = contactLimit)
        }.getOrDefault(emptyList())
        for (contact in contacts) {
            prefetchForNumber(contact.address)
        }
    }

    /**
     * 한 번호의 캐시 갱신. SMS → MMS 순서 (가벼운 것 먼저).
     * SmsReceiver 가 새 SMS 받았을 때, 사용자가 ChatScreen 들어오기 전에 미리 채워두는 용도로도 호출.
     */
    fun prefetchForNumber(phoneNumber: String): Job = scope.launch {
        if (!smsRepository.hasReadPermission()) return@launch
        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.length < 7) return@launch
        val suffix = digits.takeLast(8)

        mutex.withLock {
            runCatching {
                val freshSms = smsRepository.querySmsOnly(phoneNumber)
                cachedMessageRepository.replaceSmsOnlyForSuffix(suffix, freshSms)
            }
            runCatching {
                val freshMms = smsRepository.queryMmsOnly(phoneNumber)
                cachedMessageRepository.replaceMmsOnlyForSuffix(suffix, freshMms)
            }
        }
    }
}
