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
        //   SMS_RECEIVED : default 가 아닐 때. 시스템 갤메시지가 DB INSERT 책임.
        //   SMS_DELIVER  : default 일 때만. RING-GO 가 SMS provider DB INSERT 책임 + prepare-reply.
        val action = intent.action
        val isDeliver = action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION && !isDeliver) return
        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        val appCtx = context.applicationContext

        // 기본 문자앱이면 SMS_DELIVER 로만 처리한다. (2026-07-19 사장님 "알림 두 번·한박자 느림")
        //   기본앱은 SMS_DELIVER + SMS_RECEIVED 를 둘 다 받는데, 둘 다 처리하면 provider INSERT·알림이 2번 발생 = 중복.
        //   → RECEIVED 는 무시(DELIVER 가 담당). 기본앱 아닐 땐 애초에 DELIVER 안 오므로 RECEIVED 로 정상 처리.
        if (!isDeliver && com.detailline.callfollowcrm.util.DefaultSmsAppHelper.isCurrentDefault(appCtx)) return

        // 2026-07-09 ANR 긴급 fix (사장님 실측): getMessagesFromIntent(=PDU 파싱)이 내부적으로 telephony 서비스에
        //   동기 binder 호출(SmsMessage.parsePdu → SmsManager.getSmsSetting → ISms.getSmsSettingForSubscriber)을 한다.
        //   그 서비스가 느리면 main thread 가 그 자리(ioctl)에서 막혀 ANR. default SMS 앱이라 문자 올 때마다 이 경로 →
        //   다발 수신 시 브로드캐스트 큐가 밀려 ANR("응답하지 않음"). 2026-05-30 fix 는 INSERT 만 IO 로 옮기고 '파싱'은
        //   여전히 main 에 남아 있던 게 원인.
        //   → goAsync 를 먼저 잡고 파싱부터 전부 IO 코루틴에서 한다. onReceive 본체는 즉시 반환(빈 손).
        val pending = goAsync()
        scope.launch {
            var finished = false
            fun finishOnce() { if (!finished) { finished = true; runCatching { pending.finish() } } }
            try {
                // ── PDU 파싱 (여기가 binder 호출 지점 — 이제 IO 스레드라 main 안 막힘) ──
                val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
                    .onFailure { Log.e(TAG, "getMessagesFromIntent failed", it) }.getOrNull()
                if (messages.isNullOrEmpty()) return@launch
                val first = messages.first()
                val sender = first.originatingAddress
                val combinedBody = messages.joinToString("") { it.messageBody.orEmpty() }
                val receivedAtMs = first.timestampMillis
                if (sender.isNullOrBlank() || combinedBody.isBlank()) return@launch

                // default SMS 앱(SMS_DELIVER)일 때 provider DB INSERT 책임. (IO 라 안전)
                if (isDeliver) {
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
                        appCtx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                    }.onFailure { e -> Log.e(TAG, "SMS provider INSERT failed (default app responsibility)", e) }
                }

                // sms_contacts_cache (Room) 즉시 upsert → HomeScreen Room observe 자동 emit.
                val digits = sender.filter { it.isDigit() }
                val suffix = if (digits.length >= 8) digits.takeLast(8) else digits
                // '신규 문의' 알림음은 **최초 1회만**. 이 번호가 캐시에 이미 있으면(전에도 문자 왕래) 신규음 대신 일반 문자음.
                //   ⚠️ 반드시 아래 upsertOne **전에** 확인 — upsert 하면 방금 이 문자가 캐시에 들어가 항상 '있음'이 됨.
                //   (2026-08-02 사장님: 신규 한 명이 문자를 여러 번 보냈는데 매번 신규음 → "오늘 신규가 여러 명인가" 착각.)
                val seenBefore = runCatching {
                    app.container.smsContactCacheRepository.findBySuffix(suffix) != null
                }.getOrDefault(false)
                val newContact = com.detailline.callfollowcrm.data.repository.SmsRepository.SmsContact(
                    address = sender,
                    normalizedSuffix = suffix,
                    lastBody = combinedBody,
                    lastDateMs = receivedAtMs,
                    lastSent = false,
                    hasOwnerReply = false,
                    firstDateMsInScan = receivedAtMs
                )
                runCatching { app.container.smsContactCacheRepository.upsertOne(newContact) }

                val container = app.container
                // 스팸 앞자리(070 등) = 광고로 보고 알림·AI 준비 모두 건너뜀. (2026-06-07 사장님)
                val isSpam = com.detailline.callfollowcrm.util.SpamPrefix.isSpam(sender, container.preferences.spamPrefixes)
                val customer = runCatching { container.customerRepository.findByPhone(sender) }.getOrNull()
                val categoryLabel = customer?.categoryId?.let { cid ->
                    runCatching { container.categoryRepository.findById(cid)?.name }.getOrNull()
                }

                // 상담함/문자함 1차 분류 (2026-07-11 사장님). GENERAL=고객 아님(문자함) → 조용한 알림 + AI 준비 스킵.
                //   저장 고객이면 무조건 상담함. 애매하면 상담함(=UNSURE). 대표번호/광고 등 확실한 비고객만 GENERAL.
                val verdict = runCatching {
                    container.threadBucketRepository.classifyLocal(
                        phone = sender,
                        body = combinedBody,
                        isSavedCustomer = customer != null,
                        hasOwnerReply = false
                    )
                }.getOrDefault(com.detailline.callfollowcrm.domain.inbox.InboxClassifier.Verdict.UNSURE)
                val isGeneral = verdict == com.detailline.callfollowcrm.domain.inbox.InboxClassifier.Verdict.GENERAL

                val notifyEnabled = container.preferences.incomingSmsNotifyEnabled && !isSpam

                // 저장 이름 없으면 기기 연락처(삼성)에서 조회해 알림에 표시 — "저장돼 있으면 그대로 반영". (2026-07-21 사장님)
                val notifyName = customer?.name?.takeIf { it.isNotBlank() }
                    ?: com.detailline.callfollowcrm.util.ContactNameResolver.lookup(appCtx, sender)

                // 1) 알림 — 상담함은 수신 즉시 헤드업, 문자함(고객 아님)은 조용히(알림함+배지만). (2026-06-15 / 2026-07-11 사장님)
                if (notifyEnabled) {
                    runCatching {
                        if (isGeneral) {
                            NotificationHelper.showGeneralSms(
                                context = appCtx,
                                phone = sender,
                                displayName = notifyName,
                                body = combinedBody,
                                receivedAtMs = receivedAtMs,
                                customerId = customer?.id
                            )
                        } else {
                            NotificationHelper.showIncomingSms(
                                context = appCtx,
                                phone = sender,
                                displayName = notifyName,
                                body = combinedBody,
                                receivedAtMs = receivedAtMs,
                                categoryLabel = categoryLabel,
                                // 신규음 = 미등록 고객 **AND** 이 번호 최초 문자일 때만. 그담 문자부턴 일반 문자음.
                                isNewCustomer = customer == null && !seenBefore,
                                // 이미 찾은 고객 id 를 실어 보내 탭 시 '그 고객'으로 정확히 열기(번호 포맷 매칭 우회).
                                customerId = customer?.id
                            )
                        }
                    }
                }
                // 브로드캐스트 종료 — 무거운 prepare 는 이 scope(Application 수명) 에서 계속. pending 은 여기서 놓아줌.
                finishOnce()
                // 스팸이거나 문자함(고객 아님)이면 AI 답변 준비·프리페치 스킵(서버비 절감). (2026-07-11 사장님)
                if (isSpam || isGeneral) return@launch

                // 2) 무거운 작업 (히스토리·톤·prepare·prefetch) — broadcast 종료 후 계속.
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
                        .getOrDefault("").takeIf { it.isNotBlank() },
                    principles = runCatching { container.principleRepository.enabledTexts() }
                        .getOrDefault(emptyList()).takeIf { it.isNotEmpty() }
                )

                // 추천 답변은 미리 준비만 해둔다(문자방 진입 시 바로 보이게). fire-and-forget.
                //   'AI 답변 준비' OFF 면 미리 안 만듦 — 마스코트 로딩·서버 호출 없음. (2026-07-16 사장님)
                //   '고객 아님'(협업사장·거래처 등) 번호도 고객상담 추천 안 만듦. (2026-07-18 사장님) 통화요약은 별개라 유지.
                //   prefetch(문자·사진 캐시 데우기)는 AI 와 무관 → 위 OFF 여도 그대로(문자방 빨리 뜨게).
                if (container.preferences.aiReplyPrepEnabled && !container.preferences.isNonCustomer(sender)) {
                    container.suggestionRepository.requestPrepare(ctx)
                }
                container.smsCachePrefetcher.prefetchForNumber(sender)
            } catch (e: Throwable) {
                Log.e(TAG, "onReceive async failed", e)
            } finally {
                finishOnce()
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
