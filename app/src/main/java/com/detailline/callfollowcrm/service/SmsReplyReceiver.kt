package com.detailline.callfollowcrm.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.domain.model.MessageStatus
import com.detailline.callfollowcrm.util.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 알림창 빠른 답장 RemoteInput 수신 → SmsManager 직접 발송.
 *
 * 흐름:
 *   1. 사장님이 알림창 [답장] 인풋에 타이핑 + 보내기
 *   2. PendingIntent.getBroadcast 가 이 Receiver 호출 (extras = phone + RemoteInput key)
 *   3. RemoteInput.getResultsFromIntent 로 입력 텍스트 추출
 *   4. SmsSender.sendDirect 로 즉시 발송 (SEND_SMS 권한 필요)
 *   5. MessageHistory 기록 + 알림 cancel
 *
 * 권한 없으면 silent fail (사장님이 onboarding 에서 SEND_SMS 거부했을 수도).
 *   알림에서 답장 시도 시 권한 없음 = 알림 닫고 ChatScreen 열어서 거기서 발송 유도가 자연스러움.
 */
class SmsReplyReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        if (phone.isBlank()) {
            toast(context, "답장 실패: 수신 번호 없음")
            return
        }

        // RemoteInput 답장 vs AI 추천 답변 한 탭 발송 — extras 에 따라 분기.
        val body = when (intent.action) {
            ACTION_REPLY -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                if (remoteInput == null) {
                    toast(context, "답장 실패: 입력값 없음")
                    return
                }
                remoteInput.getCharSequence(KEY_REPLY_TEXT)?.toString()?.trim().orEmpty()
            }
            ACTION_SEND_SUGGESTION -> {
                intent.getStringExtra(EXTRA_SUGGESTION_BODY)?.trim().orEmpty()
            }
            else -> return
        }
        if (body.isBlank()) {
            toast(context, "답장 내용이 비어있어요")
            return
        }

        // SEND_SMS 권한 선검사 — 없으면 사장님에게 명확히 안내. (silent fail 금지)
        val hasSendPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasSendPerm) {
            toast(
                context,
                "SMS 발송 권한이 없어요. 설정 → 권한에서 'SMS 보내기' ON 해주세요."
            )
            return
        }

        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    SmsSender.sendDirect(context.applicationContext, phone, body)
                }
                runCatching {
                    val customer = app.container.customerRepository.findByPhone(phone)
                    app.container.messageHistoryRepository.recordAutoSend(
                        phoneNumber = phone,
                        customerId = customer?.id,
                        templateId = null,
                        body = body,
                        status = if (ok) MessageStatus.AUTO_SENT else MessageStatus.AUTO_FAILED
                    )
                }

                if (ok) {
                    // 시스템 sent box 에 직접 insert — 비기본 SMS 앱이라 자동 등록 X.
                    //   ChatScreen / 갤메시지에서 보낸 답장이 보이도록.
                    //   일부 OEM (Q+) 에서 막힐 수 있어 silent best-effort.
                    runCatching {
                        val values = ContentValues().apply {
                            put("address", phone)
                            put("body", body)
                            put("date", System.currentTimeMillis())
                            put("read", 1)
                            put("seen", 1)
                            put("type", 2) // MESSAGE_TYPE_SENT
                        }
                        context.applicationContext.contentResolver.insert(
                            Uri.parse("content://sms/sent"), values
                        )
                    }
                    toast(context, "답장 보냈어요")
                    // 발송 성공 시에만 알림 cancel.
                    runCatching {
                        NotificationManagerCompat.from(context.applicationContext)
                            .cancel(NotificationHelper.smsNotificationId(phone))
                    }
                } else {
                    // 실패 시 알림 유지 + Toast — 사장님이 ChatScreen 진입해서 재시도 가능.
                    toast(context, "답장 발송 실패 — 잠시 후 다시 시도해주세요")
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** BroadcastReceiver 컨텍스트에서 Toast — 메인 thread 점프 필수. */
    private fun toast(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.detailline.callfollowcrm.ACTION_SMS_REPLY"
        /** AI 추천 답변 칩 한 탭 = 즉시 발송 (편집 X). 사장님 톤이라 위험 낮음. */
        const val ACTION_SEND_SUGGESTION = "com.detailline.callfollowcrm.ACTION_SMS_SEND_SUGGESTION"
        const val EXTRA_PHONE = "extra_sms_reply_phone"
        const val EXTRA_SUGGESTION_BODY = "extra_suggestion_body"
        const val KEY_REPLY_TEXT = "key_reply_text"
    }
}
