package com.detailline.callfollowcrm.service

import android.content.Intent
import android.util.Log
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.ai.CustomerHint
import com.detailline.callfollowcrm.ai.HistoryMessage
import com.detailline.callfollowcrm.ai.PrepareContext
import com.klinker.android.send_message.MmsReceivedService
import kotlinx.coroutines.runBlocking

/**
 * 2026-05-29 Phase A 2단계 — MMS 다운로드 서비스 (klinker wrap + 우리 hook).
 *
 * 흐름:
 *   1. MmsReceiver(WAP_PUSH_DELIVER) → 이 서비스로 forward
 *   2. super.onHandleIntent(intent) — klinker 가:
 *      a. PDU 파싱 (M-Notification.ind) — `com.google.android.mms.pdu_alt.PduParser`
 *      b. APN 자동 추출 — `com.android.mms.service_alt.MmsConfigManager`
 *      c. ConnectivityManager.requestNetwork(TYPE_MOBILE_MMS) — mobile data MMS 채널 활성화
 *      d. MMSC HTTP GET — `com.android.mms.transaction.RetrieveTransaction.run()`
 *      e. M-Retrieve.conf 파싱 + content://mms 와 content://mms/part INSERT — `PduPersister`
 *   3. 우리 hook (Day 4):
 *      a. SmsRepository.queryLatestInboxMms() → sender + body + 첨부 URI
 *      b. NotificationHelper.showIncomingSms — body 앞에 "📎 사진 N장" prefix 추가
 *      c. SmsContactCacheRepository.upsertOne — HomeScreen 즉시 갱신
 *      d. PrepareContext 구성 → suggestionRepository.requestPrepare (fire-and-forget)
 *
 * **AI 추천 답변 polling 은 이번 sprint 보류** — IntentService scope 가 onHandleIntent 끝나면 destroy.
 *   다음 sprint = polling 또는 별도 worker. 현재는 사장님이 ChatScreen 진입 후 자동 fetch (loadSuggestions).
 *
 * IntentService scope = worker thread 라 동기 작업 OK. suspend 함수는 runBlocking 으로.
 */
class MmsDownloadService : MmsReceivedService() {

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) {
            Log.w(TAG, "onHandleIntent: null intent — skipping")
            return
        }
        Log.i(TAG, "MMS download start — delegating to klinker MmsReceivedService")

        // klinker 가 전체 download + persist 처리.
        //   APN 자동 추출 + ConnectivityManager + RetrieveTransaction 전부 wrap.
        //   실패 시 super 가 throw 안 함 (silent, 시스템 mms 테이블에 ERROR row 박힘).
        runCatching { super.onHandleIntent(intent) }
            .onFailure { e -> Log.e(TAG, "klinker MmsReceivedService threw", e) }

        // ─── 우리 hook ───────────────────────────────────────────────────────────
        val app = applicationContext as? CallFollowCrmApplication ?: run {
            Log.e(TAG, "Application cast failed")
            return
        }
        val container = app.container

        // 1) 방금 INSERT 된 INBOX MMS 추출. klinker 가 비동기로 INSERT 하므로 잠시 대기 안전망.
        //   IntentService onHandleIntent 는 worker thread 라 sleep OK.
        val mms = pollLatestInboxMms(container, maxAttempts = 3, delayMs = 500L)
        if (mms == null) {
            Log.w(TAG, "queryLatestInboxMms returned null after retries — abort hook")
            return
        }
        val sender = mms.address.orEmpty()
        if (sender.isBlank()) {
            Log.w(TAG, "MMS sender blank — abort hook")
            return
        }
        val rawBody = mms.body
        val attachCount = mms.imageUris.size
        val displayBody = when {
            attachCount > 0 && rawBody.isNotBlank() -> "📎 사진 ${attachCount}장\n\n$rawBody"
            attachCount > 0 -> "📎 사진 ${attachCount}장"
            else -> rawBody
        }
        val receivedAtMs = mms.dateMs

        // 2) 알림 — SmsReceiver 의 showIncomingSms 재사용. 같은 채널 + 같은 ID = 통합 UX.
        if (container.preferences.incomingSmsNotifyEnabled) {
            val customerForNotif = runCatching {
                runBlocking { container.customerRepository.findByPhone(sender) }
            }.getOrNull()
            val categoryLabel = customerForNotif?.categoryId?.let { cid ->
                runCatching {
                    runBlocking { container.categoryRepository.findById(cid)?.name }
                }.getOrNull()
            }
            NotificationHelper.showIncomingSms(
                context = applicationContext,
                phone = sender,
                displayName = customerForNotif?.name,
                body = displayBody,
                receivedAtMs = receivedAtMs,
                categoryLabel = categoryLabel
            )
        }

        // 3) SmsContact 캐시 upsert — HomeScreen Room observe 즉시 emit.
        val digits = sender.filter { it.isDigit() }
        val suffix = if (digits.length >= 8) digits.takeLast(8) else digits
        val newContact = com.detailline.callfollowcrm.data.repository.SmsRepository.SmsContact(
            address = sender,
            normalizedSuffix = suffix,
            lastBody = displayBody,
            lastDateMs = receivedAtMs,
            lastSent = false,
            hasOwnerReply = false,
            firstDateMsInScan = receivedAtMs
        )
        runCatching {
            runBlocking { container.smsContactCacheRepository.upsertOne(newContact) }
        }.onFailure { e -> Log.e(TAG, "cache upsert failed", e) }

        // 4) PrepareContext + prepare-reply (fire-and-forget).
        //   body 는 AI 가 시나리오 분류 + 답변 생성에 사용. 첨부 메타는 다음 sprint (서버 Vision).
        runCatching {
            val customer = runBlocking { container.customerRepository.findByPhone(sender) }
            val history = runBlocking {
                runCatching {
                    container.smsRepository.queryByPhone(sender, scanLimit = 100)
                        .take(20)
                        .map { sms ->
                            HistoryMessage(
                                role = if (sms.sent) "owner" else "customer",
                                body = sms.body,
                                timestampMs = sms.dateMs
                            )
                        }
                        .reversed()
                }.getOrDefault(emptyList())
            }
            val ownerToneSamples = runCatching {
                container.smsRepository.querySentMessages(limit = 50)
            }.getOrDefault(emptyList())
            val otherSchedules = runBlocking {
                runCatching {
                    container.customerRepository.getOtherUpcomingScheduleDates(sender)
                }.getOrDefault(emptyList())
            }
            val customerHint = customer?.let {
                CustomerHint(
                    name = it.name,
                    memo = it.memo.takeIf { m -> m.isNotBlank() },
                    leadHeat = it.leadHeat,
                    depositPaid = (it.depositAmount ?: 0L) > 0L,
                    scheduledWorkDateMs = it.scheduledWorkDate
                )
            }
            // 2026-05-30 사장님 #2 통점 fix: MMS 분할 시 모든 customer streak 묶음.
            val mergedLatest = com.detailline.callfollowcrm.ai.PrepareContextHelpers
                .joinCustomerStreakAfterLastOwner(history, newIncomingBody = displayBody)
            val ctx = PrepareContext(
                phone = sender,
                latestMessage = mergedLatest,
                latestMessageReceivedAtMs = receivedAtMs,
                recentHistory = history,
                customer = customerHint,
                ownerToneSamples = ownerToneSamples,
                otherUpcomingSchedulesMs = otherSchedules,
                deviceId = container.preferences.deviceId,
                ownerTrade = container.preferences.ownerTrades.firstOrNull(),
                priceList = runBlocking {
                    runCatching { container.pricingItemRepository.priceListText() }.getOrDefault("")
                }.takeIf { it.isNotBlank() }
            )
            runBlocking { container.suggestionRepository.requestPrepare(ctx) }
            // 캐시 prefetch — 사장님이 알림 → ChatScreen 진입 시 즉시 표시.
            container.smsCachePrefetcher.prefetchForNumber(sender)
        }.onFailure { e -> Log.e(TAG, "prepare-reply hook failed", e) }

        Log.i(
            TAG,
            "MMS hook complete — sender=$sender attach=$attachCount body.len=${rawBody.length}"
        )
    }

    /**
     * klinker 가 mms row INSERT 하는 데 약간 시간 걸릴 수 있어 짧게 polling.
     * onHandleIntent 가 worker thread 라 Thread.sleep 안전.
     */
    private fun pollLatestInboxMms(
        container: com.detailline.callfollowcrm.data.AppContainer,
        maxAttempts: Int,
        delayMs: Long
    ): com.detailline.callfollowcrm.data.repository.SmsRepository.SmsMessage? {
        repeat(maxAttempts) {
            val result = runCatching { container.smsRepository.queryLatestInboxMms() }.getOrNull()
            if (result != null) return result
            Thread.sleep(delayMs)
        }
        return null
    }

    companion object {
        private const val TAG = "MmsDownloadService"
    }
}
