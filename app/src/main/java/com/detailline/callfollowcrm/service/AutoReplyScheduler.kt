package com.detailline.callfollowcrm.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * "처음 연락온 고객" 자동 응답 SMS 발송 스케줄러.
 *
 * 흐름:
 *  1. CallStateReceiver 가 첫 통화 감지 → schedule() 호출
 *  2. 10초 카운트다운 알림 표시 (사용자가 "취소" 누를 수 있음)
 *  3. 10초 뒤 cancelled 플래그 확인
 *  4. 안 취소됐으면 SmsManager 로 발송 + MessageHistory 기록 + Customer 자동 생성
 *  5. 결과 알림으로 갱신 (✅ 보냄 / ✖ 취소됨 / ⚠ 실패)
 *
 * 정책 메모: SEND_SMS 권한 + Settings 토글 모두 켜져 있어야만 동작.
 * 부재중/수신 별 템플릿 ID 는 AppPreferences 에서 가져옴. 미지정 케이스는 발송 안 함.
 */
object AutoReplyScheduler {

    private val pending = ConcurrentHashMap<Long, Boolean>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    const val COUNTDOWN_MS: Long = 10_000L

    /** AutoReplyCancelReceiver 에서 호출. 해당 callRecordId 의 자동 발송을 취소. */
    fun cancel(callRecordId: Long) {
        if (pending.containsKey(callRecordId)) {
            pending[callRecordId] = true
        }
    }

    /**
     * 자동 응답 발송 스케줄. 10초 후 SMS 발송 (취소 안 됐을 시).
     *
     * @param isMissed true 면 부재중 템플릿, false 면 수신 템플릿 사용
     */
    fun schedule(
        context: Context,
        callRecordId: Long,
        phoneNumber: String,
        isMissed: Boolean
    ) {
        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        val container = app.container

        // 권한·정책 한 번 더 점검 (스케줄러 호출 직전엔 race condition 가능)
        if (!container.preferences.autoFirstReplyEnabled) return
        if (!hasSendSmsPermission(context)) return

        val templateId = if (isMissed) {
            container.preferences.firstReplyMissedTemplateId
        } else {
            container.preferences.firstReplyIncomingTemplateId
        }
        if (templateId <= 0) return

        pending[callRecordId] = false
        NotificationHelper.showAutoReplyPending(
            context = context,
            callRecordId = callRecordId,
            phoneNumber = phoneNumber,
            countdownMs = COUNTDOWN_MS
        )

        scope.launch {
            delay(COUNTDOWN_MS)

            val cancelled = pending[callRecordId] == true
            pending.remove(callRecordId)

            val template = container.messageTemplateRepository.findById(templateId)
            val body = template?.body.orEmpty()

            if (cancelled || body.isBlank()) {
                container.messageHistoryRepository.recordAutoSend(
                    phoneNumber = phoneNumber,
                    customerId = null,
                    templateId = templateId.takeIf { it > 0 },
                    body = body,
                    status = MessageStatus.AUTO_CANCELLED
                )
                NotificationHelper.showAutoReplyCancelled(context, callRecordId)
                return@launch
            }

            // 발송 — 정책상 첫 응대는 Customer 도 자동 생성 (영업 시작 신호).
            // 2026-05-25: status 박는 부분 제거 — 카테고리 시스템으로 통일.
            val customer = runCatching {
                container.customerRepository.upsertByPhone(phoneNumber = phoneNumber)
            }.getOrNull()

            val sendOk = sendSmsSafely(context, phoneNumber, body)
            container.messageHistoryRepository.recordAutoSend(
                phoneNumber = phoneNumber,
                customerId = customer?.id,
                templateId = templateId,
                body = body,
                status = if (sendOk) MessageStatus.AUTO_SENT else MessageStatus.AUTO_FAILED
            )

            if (sendOk) {
                NotificationHelper.showAutoReplySent(context, callRecordId, phoneNumber)
            } else {
                NotificationHelper.showAutoReplyFailed(context, callRecordId, phoneNumber)
            }
        }
    }

    private fun hasSendSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * SmsManager 로 SMS 발송. 본문이 길면 multipart 로 자동 분할.
     * 실패해도 throw 안 함 — 결과는 boolean 반환.
     */
    private fun sendSmsSafely(context: Context, phone: String, body: String): Boolean = runCatching {
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        } ?: return false

        val parts = sms.divideMessage(body)
        if (parts.size <= 1) {
            sms.sendTextMessage(phone, null, body, null, null)
        } else {
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
        }
        true
    }.getOrDefault(false)
}
