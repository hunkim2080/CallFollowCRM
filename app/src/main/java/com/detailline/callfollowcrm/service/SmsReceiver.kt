package com.detailline.callfollowcrm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
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
     * 알림 표시 후 서버 추천 답변 polling — 3초 간격 4번 (최대 12초).
     * READY 면 같은 알림 ID 로 update (suggestions 칩 3개 박힘).
     * 그 안에 안 오면 silent give up (사장님이 ChatScreen 에서 ↻ 가능).
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
        // 3 × 2.5초 = 7.5초. BroadcastReceiver goAsync 제한 (~10초) 안에 안전 마감.
        //   더 긴 polling 이 필요하면 ForegroundService 또는 WorkManager 로 분리 (다음 step).
        //
        // 2026-05-28 사장님 보고 fix:
        //   기존 = READY + isNotEmpty 면 첫 hit 으로 update → LLM 이 점진 생성하면 2개만 받기도.
        //   사장님 통점 = "3번이 안 보임". 알림에 2개, ChatScreen 들어가면 3개.
        //   새 정책: size >= 3 일 때만 update. 단 마지막 시도엔 부분이라도 update (답변 없음 알림 방지).
        var lastPartialSugs: List<String> = emptyList()
        repeat(3) { attempt ->
            kotlinx.coroutines.delay(2500L)
            val result = runCatching {
                container.suggestionRepository.fetch(phone).getOrNull()
            }.getOrNull()
            if (result?.status == com.detailline.callfollowcrm.ai.SuggestionStatus.READY) {
                val sugs = result.suggestions?.suggestions.orEmpty()
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
        // 마지막 시도까지 3개 못 받음 — 부분이라도 있으면 update.
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
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // 한 번에 여러 PDU 가 와도 보통 같은 sender. 첫 메시지에서 phone + ts 추출.
        val first = messages.first()
        val sender = first.originatingAddress ?: return
        val combinedBody = messages.joinToString("") { it.messageBody.orEmpty() }
        val receivedAtMs = first.timestampMillis
        if (sender.isBlank() || combinedBody.isBlank()) return

        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val container = app.container

                // 2026-05-25 사장님 보고 fix: 알림 표시를 prepare/prefetch 네트워크 호출 앞으로.
                //   서버 timeout (최대 ~9초) 이 길어지면 goAsync 시간 초과로 알림이 못 떠는 케이스 방지.
                //   사장님이 알림은 즉시 보고, AI 추천 답변은 후속 polling 으로 update.
                if (container.preferences.incomingSmsNotifyEnabled) {
                    val customerForNotif = runCatching {
                        container.customerRepository.findByPhone(sender)
                    }.getOrNull()
                    val categoryLabel = customerForNotif?.categoryId?.let { cid ->
                        runCatching { container.categoryRepository.findById(cid)?.name }.getOrNull()
                    }
                    NotificationHelper.showIncomingSms(
                        context = context.applicationContext,
                        phone = sender,
                        displayName = customerForNotif?.name,
                        body = combinedBody,
                        receivedAtMs = receivedAtMs,
                        categoryLabel = categoryLabel,
                        suggestions = null
                    )
                }

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
                // 서버가 시스템 프롬프트에 few-shot 으로 박아서 "사장님 톤" 학습.
                val ownerToneSamples = if (canReadSms) {
                    container.smsRepository.querySentMessages(limit = 50)
                } else {
                    emptyList()
                }

                val customer = runCatching {
                    container.customerRepository.findByPhone(sender)
                }.getOrNull()
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
                //   서버가 "토요일 가능?" 같은 일정 질문에 이걸 근거로 답변.
                val otherSchedules = runCatching {
                    container.customerRepository.getOtherUpcomingScheduleDates(sender)
                }.getOrDefault(emptyList())

                val ctx = PrepareContext(
                    phone = sender,
                    latestMessage = combinedBody,
                    latestMessageReceivedAtMs = receivedAtMs,
                    recentHistory = history,
                    customer = customerHint,
                    ownerToneSamples = ownerToneSamples,
                    otherUpcomingSchedulesMs = otherSchedules
                )

                container.suggestionRepository.requestPrepare(ctx)
                // 결과 무시 — fire-and-forget. 실패해도 ChatScreen ↻ fallback 있음.

                // 캐시 prefetch — 사장님이 알림 보고 들어가기 전에 미리 채움.
                // (방금 도착한 새 SMS 포함해서) 즉시 표시되도록.
                container.smsCachePrefetcher.prefetchForNumber(sender)

                // Step 3: AI 추천 답변 polling — 알림 토글 ON 일 때만.
                //   서버 prepare → LLM 호출 → 캐시 박힘 → fetch 가 ready 반환.
                //   안에 안 되면 포기 (사장님이 ChatScreen 진입해서 ↻ 가능).
                if (container.preferences.incomingSmsNotifyEnabled) {
                    val categoryLabel = customer?.categoryId?.let { cid ->
                        runCatching { container.categoryRepository.findById(cid)?.name }.getOrNull()
                    }
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
            } finally {
                pending.finish()
            }
        }
    }
}
