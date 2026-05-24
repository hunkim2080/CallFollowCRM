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
                        depositPaid = (it.depositAmount ?: 0L) > 0L
                    )
                }

                val ctx = PrepareContext(
                    phone = sender,
                    latestMessage = combinedBody,
                    latestMessageReceivedAtMs = receivedAtMs,
                    recentHistory = history,
                    customer = customerHint,
                    ownerToneSamples = ownerToneSamples
                )

                container.suggestionRepository.requestPrepare(ctx)
                // 결과 무시 — fire-and-forget. 실패해도 ChatScreen ↻ fallback 있음.
            } finally {
                pending.finish()
            }
        }
    }
}
