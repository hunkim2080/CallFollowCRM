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

    /**
     * 알림 표시 후 서버 추천 답변 polling — 2.5초 간격 12번 (최대 30초).
     * READY 면 같은 알림 ID 로 update (suggestions 칩 3개 박힘).
     * 그 안에 안 오면 silent give up (사장님이 ChatScreen 에서 ↻ 가능).
     *   2026-06-03: 서버 LLM 이 실제 ~20초 걸려 기존 7.5초 폴링은 거의 항상 실패였음(실기기 확인).
     *   broadcast 는 이미 pending.finish() 됐으므로 이 polling 은 ANR 무관(Application scope).
     */
    private suspend fun pollAndUpdateSuggestions(
        context: Context,
        container: com.detailline.callfollowcrm.data.AppContainer,
        phone: String,
        displayName: String?,
        body: String,
        receivedAtMs: Long,
        categoryLabel: String?
    ) {
        // 2.5초 × 12 = 30초. broadcast 종료 후 Application scope 에서 도므로 ANR 무관.
        //   더 견고하게(프로세스 kill 대비) 하려면 ForegroundService/WorkManager 로 분리 (다음 step).
        //
        // 2026-05-28 사장님 보고 fix:
        //   기존 = READY + isNotEmpty 면 첫 hit 으로 update → LLM 이 점진 생성하면 2개만 받기도.
        //   사장님 통점 = "3번이 안 보임". 알림에 2개, ChatScreen 들어가면 3개.
        //   새 정책: size >= 3 일 때만 update. 단 마지막 시도엔 부분이라도 update (답변 없음 알림 방지).
        // 2026-05-28 v2: suggestions = List<ReplyChoice> → 알림 본문엔 text 만 추출해서 전달.
        //   NotificationHelper 시그니처 (List<String>?) 는 그대로 — BigText/액션엔 라벨 없이 답변 본문만.
        //   알림에는 chip UI 가 없으므로 ChatScreen 처럼 라벨 노출은 불필요.
        var lastPartialSugs: List<String> = emptyList()
        // 2026-06-03 fix(사장님 "추천 1% 성공"): 폴링이 7.5초에 포기 → 서버 LLM(Sonnet)이 실제 ~20초
        //   걸려 아직 GENERATING 이라 거의 매번 실패(실기기 logcat 확인). broadcast 는 이미
        //   pending.finish() 로 끝났으므로 여기서 더 기다려도 ANR 없음. 30초(2.5s×12)로 확장.
        repeat(12) { _ ->
            kotlinx.coroutines.delay(2500L)
            val result = runCatching {
                container.suggestionRepository.fetch(phone).getOrNull()
            }.getOrNull()
            if (result?.status == com.detailline.callfollowcrm.ai.SuggestionStatus.READY) {
                val sugs = result.suggestions?.suggestions.orEmpty().map { it.text }
                if (sugs.size >= 3) {
                    // 3개 다 받음 — 즉시 update + 종료.
                    NotificationHelper.showIncomingSms(
                        context = context,
                        phone = phone,
                        displayName = displayName,
                        body = body,
                        receivedAtMs = receivedAtMs,
                        categoryLabel = categoryLabel,
                        suggestions = sugs
                    )
                    return
                }
                // 2개 이하 — 더 기다림. 마지막 attempt 면 부분이라도 보냄 (아래 처리).
                if (sugs.isNotEmpty()) lastPartialSugs = sugs
            }
        }
        // 2026-05-28 사장님 보고 fix: polling 끝났는데 응답 없으면 알림이 "준비 중..." 영원히 멈춤.
        //   서버 죽었거나 Tailscale 끊겼을 가능성. 사장님이 ChatScreen 안 들어가면 영영 모름.
        //   해결: 부분이라도 있으면 update / 없으면 "서버 응답 없음" 상태로 update → "준비 중..." 정리.
        if (lastPartialSugs.isNotEmpty()) {
            NotificationHelper.showIncomingSms(
                context = context,
                phone = phone,
                displayName = displayName,
                body = body,
                receivedAtMs = receivedAtMs,
                categoryLabel = categoryLabel,
                suggestions = lastPartialSugs
            )
        } else {
            // 빈 list 전달 → NotificationHelper 가 "서버 응답 없음 — 직접 답장" 분기로 처리.
            //   "준비 중..." placeholder 잔존 차단 + 사장님이 [💬 직접 답장] 으로 즉시 답 가능.
            NotificationHelper.showIncomingSms(
                context = context,
                phone = phone,
                displayName = displayName,
                body = body,
                receivedAtMs = receivedAtMs,
                categoryLabel = categoryLabel,
                suggestions = emptyList()
            )
        }
    }

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
            val notifyEnabled = container.preferences.incomingSmsNotifyEnabled
            // 고객/카테고리 = 초기 알림 + 이후 prepare 둘 다 사용 → 한 번만 조회 (기존엔 findByPhone 2번).
            val customer = runCatching { container.customerRepository.findByPhone(sender) }.getOrNull()
            val categoryLabel = customer?.categoryId?.let { cid ->
                runCatching { container.categoryRepository.findById(cid)?.name }.getOrNull()
            }

            // 1) 초기 알림만 띄우고 broadcast 를 즉시 종료한다.
            //   2026-06-03 ANR fix: SMS_DELIVER 는 foreground broadcast(수신자 제한 ~10초). 기존엔 goAsync 를
            //   prepare(최대 ~9초) + polling(7.5초) 끝까지 들고 있어 합산 >10초 → "RING-GO 응답 없음" ANR 빈발
            //   (+ ANR 중 뒤 화면이 회색으로 굳어 다이얼로그가 미완성처럼 보임). 해결: 알림 후 pending.finish() 즉시.
            //   이후 무거운 작업은 Application scope 에서 broadcast 수명과 무관하게 계속.
            try {
                if (notifyEnabled) {
                    NotificationHelper.showIncomingSms(
                        context = context.applicationContext,
                        phone = sender,
                        displayName = customer?.name,
                        body = combinedBody,
                        receivedAtMs = receivedAtMs,
                        categoryLabel = categoryLabel,
                        suggestions = null
                    )
                }
            } finally {
                pending.finish()
            }

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
                    otherUpcomingSchedulesMs = otherSchedules
                )

                container.suggestionRepository.requestPrepare(ctx)  // fire-and-forget
                container.smsCachePrefetcher.prefetchForNumber(sender)

                // AI 추천 답변 polling — 알림 토글 ON 일 때만. broadcast 는 이미 종료됨.
                if (notifyEnabled) {
                    pollAndUpdateSuggestions(
                        context = context.applicationContext,
                        container = container,
                        phone = sender,
                        displayName = customer?.name,
                        body = combinedBody,
                        receivedAtMs = receivedAtMs,
                        categoryLabel = categoryLabel
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "prepare/poll failed (broadcast already finished)", e)
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
