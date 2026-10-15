package com.detailline.callfollowcrm.data.repository

import android.util.Log
import com.detailline.callfollowcrm.ai.ThreadClassifyServerRepository
import com.detailline.callfollowcrm.domain.inbox.InboxClassifier
import com.detailline.callfollowcrm.domain.inbox.ServerClassifyGate
import com.detailline.callfollowcrm.util.LogRedact
import com.detailline.callfollowcrm.util.PhoneKey
import java.util.concurrent.ConcurrentHashMap

/**
 * 상담함/문자함 **서버 정정 — 부르는 자리는 여기 한 곳.** (2026-10-15 Phase 2 앱 연결)
 *
 *   로컬 1차(ThreadBucketRepository.classifyLocal)가 **애매(UNSURE)** 라고 한 수신 문자 1건을
 *   서버(Haiku)에 물어, general(확신 ≥0.9)이면 문자함으로 옮긴다. 그 외(consult·실패·거절)는 상담함 유지.
 *
 *   비용 가드(통화요약 신규설치 burst 교훈):
 *   - 「물어볼까」는 ServerClassifyGate.decide 가 정한다 — UNSURE 만 · 행 없을 때만 · 답 정해진 번호 제외 · 쿨다운.
 *   - 부르는 곳은 **수신 훅 둘**(SmsReceiver · MmsDownloadedReceiver)뿐. 스캔/백필은 로컬만 돌린다.
 *   - 같은 번호 동시 2건(연타, §13③)은 inFlight 로 한 번만.
 *
 *   결과 반영은 ThreadBucketRepository.applyServerGeneral — BucketPolicy(SRC_HAIKU) 를 지나므로
 *   사장님(OWNER) 고정은 못 덮고, 나중에 사장님이 답장하면 자동 CONSULT 승격이 이 행을 지운다.
 *   화면(HomeViewModel/ChatViewModel/CustomerDetail)은 observeBuckets/isGeneral 로 읽어 **자동으로** 따라온다.
 */
class ThreadServerClassifier(
    private val smsRepository: SmsRepository,
    private val threadBucketRepository: ThreadBucketRepository,
    private val server: ThreadClassifyServerRepository,
    /** 사장님이 「고객 아님」 으로 찍은 번호인가 — AppPreferences.isNonCustomer. */
    private val ownerMarkedNonCustomer: (String) -> Boolean
) {
    /** suffix → 마지막으로 물어본 시각. 앱 수명 동안만(재시작하면 비움 — 서버 캐시가 같은 내용은 0원으로 막는다). */
    private val lastAskedAt = ConcurrentHashMap<String, Long>()
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * 수신 1건 뒤에 부른다. 로컬 판정이 UNSURE 가 아니면 아무것도 안 한다(0원).
     * @return 서버 판정으로 **문자함으로 옮겼으면** true. 나머지(상담함 유지·실패·건너뜀)는 false.
     */
    suspend fun refineIfUnsure(
        phone: String,
        body: String,
        localVerdict: InboxClassifier.Verdict,
        isSavedCustomer: Boolean,
        hasOwnerReply: Boolean,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (localVerdict != InboxClassifier.Verdict.UNSURE) return false
        val suffix = PhoneKey.of(phone)
        if (suffix.isBlank()) return false

        val existing = threadBucketRepository.findRow(phone)
        val recent = runCatching { smsRepository.querySmsOnly(phone, scanLimit = RECENT_SCAN) }
            .getOrDefault(emptyList())
            .map { ServerClassifyGate.Msg(fromOwner = it.sent, body = it.body, ts = it.dateMs) }
        val messages = ServerClassifyGate.payloadMessages(
            recent = recent,
            current = ServerClassifyGate.Msg(fromOwner = false, body = body, ts = now)
        )
        val decision = ServerClassifyGate.decide(
            local = localVerdict,
            existing = existing,
            isSavedCustomer = isSavedCustomer,
            hasOwnerReply = hasOwnerReply,
            ownerMarkedNonCustomer = runCatching { ownerMarkedNonCustomer(phone) }.getOrDefault(false),
            messages = messages,
            lastAskedAt = lastAskedAt[suffix],
            now = now
        )
        if (decision != ServerClassifyGate.Decision.ASK) {
            Log.d(TAG, "${LogRedact.phone(phone)} 서버 안 물음: $decision")
            return false
        }
        if (!inFlight.add(suffix)) {
            Log.d(TAG, "${LogRedact.phone(phone)} 이미 묻는 중 — 건너뜀")
            return false
        }
        try {
            lastAskedAt[suffix] = now
            val verdict = server.classify(
                ThreadClassifyServerRepository.Input(
                    suffix = suffix,
                    address = phone,
                    isSavedCustomer = isSavedCustomer,
                    hasOwnerReply = hasOwnerReply || messages.any { it.fromOwner },
                    messages = messages
                )
            ).getOrElse { e ->
                // 분류 보정 실패 = 상담함 유지(안전). 사장님 입력이 아니라 화면에 말하진 않는다.
                Log.w(TAG, "${LogRedact.phone(phone)} 서버 분류 실패 → 상담함 유지: ${e.javaClass.simpleName}: ${e.message}")
                return false
            }
            Log.i(TAG, "${LogRedact.phone(phone)} 서버 판정 ${verdict.bucket} ${"%.2f".format(verdict.confidence)} (${verdict.reason})")
            if (!verdict.isGeneral) return false
            return threadBucketRepository.applyServerGeneral(phone, body, verdict, now)
        } finally {
            inFlight.remove(suffix)
        }
    }

    companion object {
        private const val TAG = "ThreadServerClassify"
        /** 최근 문자 몇 줄만 훑나 — 5개만 보내니 깊이 볼 필요 없다. */
        private const val RECENT_SCAN = 60
    }
}
