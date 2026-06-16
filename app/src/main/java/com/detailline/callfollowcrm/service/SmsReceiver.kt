package com.detailline.callfollowcrm.service

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.ai.CustomerHint
import com.detailline.callfollowcrm.ai.HistoryMessage
import com.detailline.callfollowcrm.ai.PrepareContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 고객 SMS 수신 시 맥미니 서버에 답변 추천 준비 요청 (fire-and-forget).
 *
 * 흐름:
 *   1. SMS_RECEIVED → 발신번호 추출
 *   2. SmsRepository 로 최근 대화 히스토리 조회 (READ_SMS 권한 있을 때만)
 *   3. CustomerRepository 로 고객 메모/leadHeat 조회
 *   4. PrepareContext 구성 → suggestionRepository.requestPrepare()
 *
 * 실패는 silent — 사장님이 ChatScreen 에서 ↻ 누르면 재시도 가능.
 * goAsync 로 main thread 점유 최소화. 본 작업은 IO 코루틴에서 진행.
 */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // (2026-06-15) pollAndUpdateSuggestions 제거 — 알림은 수신 즉시 1번만(추천 미포함). 추천은 문자방에서.

    override fun onReceive(context: Context, intent: Intent) {
        // 2026-05-29 Phase A 1단계 — 두 액션 분기:
        //   SMS_RECEIVED : default 가 아닐 때 (현재 상태). 시스템 갤메시지가 DB INSERT 책임.
        //   SMS_DELIVER  : default 일 때만. RING-GO 가 SMS provider DB INSERT 책임 + 기존 prepare-reply.
        // 두 액션 다 메시지 본문 추출은 동일 — getMessagesFromIntent 가 양쪽 다 처리.
        val action = intent.action
        val isDeliver = action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION && !isDeliver) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // 한 번에 여러 PDU 가 와도 보통 같은 sender. 첫 메시지에서 phone + ts 추출.
        val first = messages.first()
        val sender = first.originatingAddress ?: return
        val combinedBody = messages.joinToString("") { it.messageBody.orEmpty() }
        val receivedAtMs = first.timestampMillis
        if (sender.isBlank() || combinedBody.isBlank()) return

        val app = context.applicationContext as? CallFollowCrmApplication ?: return

        // 2026-05-30 사장님 ANR 보고 fix: SMS provider INSERT 를 main thread 에서 호출하면 binder IPC
        //   동기 대기로 ANR 위험. default SMS 앱 인수 후 SMS 빈도 ↑ → 누적 영향.
        //   해결: scope.launch (IO) 안으로 이동. broadcast onReceive 자체는 빠르게 끝남.
        //   주의: INSERT 가 후속 작업과 race 가능하나 우리 cache upsert / prepare-reply 와 무관.
        if (isDeliver) {
            scope.launch {
                runCatching {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, sender)
                        put(Telephony.Sms.BODY, combinedBody)
                        put(Telephony.Sms.DATE, receivedAtMs)
                        put(Telephony.Sms.DATE_SENT, receivedAtMs)
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.SEEN, 0)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    }
                    context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                }.onFailure { e ->
                    Log.e(TAG, "SMS provider INSERT failed (default app responsibility)", e)
                }
            }
        }

        // 2026-05-28 본격 fix: sms_contacts_cache (Room) 즉시 upsert → HomeScreen Room observe 자동 emit.
        //   pendingNewSmsContacts in-memory 는 더 이상 필요 X (Room upsert → emit 이 ms 단위).
        //   재시작 후에도 데이터 영속 → cold start 도 instant.
        val digits = sender.filter { it.isDigit() }
        val suffix = if (digits.length >= 8) digits.takeLast(8) else digits
        val newContact = com.detailline.callfollowcrm.data.repository.SmsRepository.SmsContact(
            address = sender,
            normalizedSuffix = suffix,
            lastBody = combinedBody,
            lastDateMs = receivedAtMs,
            lastSent = false,
            hasOwnerReply = false,
            firstDateMsInScan = receivedAtMs
        )
        // 비동기 upsert — onReceive 가 빨리 끝나야 PHONE_STATE 다음 broadcast 안 막힘.
        //   주의: 이 launch 는 goAsync 와 별개 scope 라 BroadcastReceiver 생명주기 무관 — Application scope.
        scope.launch {
            runCatching { app.container.smsContactCacheRepository.upsertOne(newContact) }
        }

        val pending = goAsync()
        scope.launch {
            val container = app.container
            // 스팸 앞자리(070 등) = 광고로 보고 알림·AI 준비 모두 건너뜀. (2026-06-07 사장님 요청)
            val isSpam = com.detailline.callfollowcrm.util.SpamPrefix.isSpam(sender, container.preferences.spamPrefixes)
            val notifyEnabled = container.preferences.incomingSmsNotifyEnabled && !isSpam
            // 고객/카테고리 = 초기 알림 + 이후 prepare 둘 다 사용 → 한 번만 조회 (기존엔 findByPhone 2번).
            val customer = runCatching { container.customerRepository.findByPhone(sender) }.getOrNull()
            val categoryLabel = customer?.categoryId?.let { cid ->
                runCatching { container.categoryRepository.findById(cid)?.name }.getOrNull()
            }

            // 1) 알림을 '수신 즉시' 띄운다 — 카톡처럼 바로 헤드업. (2026-06-15 사장님)
            //   2026-06-09 엔 갤 기본알림 중복을 피하려 AI 폴링(최대 30초) 끝나야 띄웠는데, 그게 "늦게/안 울림"의
            //   원인이었음. 이제 추천을 알림에 안 넣으니(깔끔) 기다릴 게 없어 즉시 1번 띄운다. 추천은 탭→문자방에서.
            //   (비-기본 SMS 앱이면 갤 메시지 알림과 겹칠 수 있어 '갤 메시지 알림 끄기' 안내 — 채널 설명.)
            //   ANR: 알림 즉시 후 pending.finish() — 무거운 작업(prepare)은 Application scope 에서 계속.
            try {
                if (notifyEnabled) {
                    NotificationHelper.showIncomingSms(
                        context = context.applicationContext,
                        phone = sender,
                        displayName = customer?.name,
                        body = combinedBody,
                        receivedAtMs = receivedAtMs,
                        categoryLabel = categoryLabel
                    )
                }
            } finally {
                pending.finish()
            }

            // 스팸 앞자리면 AI 추천 준비·폴링 전부 건너뜀(자동메세지 준비 X).
            if (isSpam) return@launch

            // 2) 무거운 작업 (히스토리·톤·prepare·prefetch·polling) — broadcast 종료 후 계속.
            try {
                val canReadSms = container.smsRepository.hasReadPermission()
                val history = if (canReadSms) {
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
                } else {
                    emptyList()
                }
                // 사장님 톤 코퍼스 — 다른 고객에게 보낸 최근 메시지 50건.
                val ownerToneSamples = if (canReadSms) {
                    container.smsRepository.querySentMessages(limit = 50)
                } else {
                    emptyList()
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

                // P3 — 사장님의 다른 시공 일정 (현재 sender 제외, 14일 내).
                val otherSchedules = runCatching {
                    container.customerRepository.getOtherUpcomingScheduleDates(sender)
                }.getOrDefault(emptyList())

                // MMS 분할 / 짧은 SMS 다발 → "마지막 사장님 발신 이후 모든 고객 수신 메시지" 묶음.
                val mergedLatest = com.detailline.callfollowcrm.ai.PrepareContextHelpers
                    .joinCustomerStreakAfterLastOwner(history, newIncomingBody = combinedBody)
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
                    priceList = runCatching { container.pricingItemRepository.priceListText() }
                        .getOrDefault("").takeIf { it.isNotBlank() }
                )

                // 추천 답변은 미리 준비만 해둔다(문자방 진입 시 바로 보이게). 알림엔 더 이상 안 넣음 → 폴링 제거.
                container.suggestionRepository.requestPrepare(ctx)  // fire-and-forget
                container.smsCachePrefetcher.prefetchForNumber(sender)
            } catch (e: Throwable) {
                // 알림은 위에서 이미 즉시 띄웠으므로 여기선 로그만 (prepare 실패해도 알림/문자방은 정상).
                Log.e(TAG, "prepare failed (broadcast already finished)", e)
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
