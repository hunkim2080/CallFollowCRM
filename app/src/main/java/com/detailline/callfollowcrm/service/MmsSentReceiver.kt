package com.detailline.callfollowcrm.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.util.LogRedact
import com.klinker.android.send_message.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * MMS·LMS 직접 발송 결과 콜백. (비동기 요청이라 실제 성공/실패는 여기서 확인)
 *
 * 두 종류가 온다 — EXTRA_IMAGE_COUNT 로 가른다:
 *   · **사진 MMS**(imageCount ≥ 1): 실패면 사장님께 알림(거짓 성공 방지).
 *   · **텍스트 LMS**(imageCount == 0, 장문 문자): SMS 와 **같은 안전망** — 실패면 기록 되돌리고([TextSendFailure]) 알림.
 */
class MmsSentReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MMS_SENT) return

        val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        val imageCount = intent.getIntExtra(EXTRA_IMAGE_COUNT, 0)

        // 공식 API 경로가 넘긴 임시 PDU 파일 정리(성공/실패·종류 무관).
        val cleanup = {
            intent.getStringExtra(EXTRA_SEND_FILE)?.let { name ->
                runCatching { java.io.File(context.cacheDir, java.io.File(name).name).delete() }
            }
            Unit
        }

        // 📧 텍스트 LMS(장문 문자) — SMS 와 같은 안전망. (SmsSentReceiver 와 같은 [TextSendFailure] 한 곳 씀)
        if (imageCount == 0) {
            if (resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "LMS sent OK: to=${LogRedact.phone(phone)}")
                cleanup(); return
            }
            Log.e(TAG, "LMS sent FAILED: resultCode=$resultCode to=${LogRedact.phone(phone)}")
            val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
            val sentAt = intent.getLongExtra(EXTRA_SENT_AT, 0L)
            val app = context.applicationContext as? CallFollowCrmApplication
            val pending = goAsync()
            scope.launch {
                try {
                    if (phone.isNotBlank()) {
                        val flipped = TextSendFailure.rollbackLastSend(app, phone, sentAt, body)
                        Log.i(TAG, "기록 되돌림=$flipped to=${LogRedact.phone(phone)}")
                        runCatching { NotificationHelper.showSmsSendFailed(context, phone, "") }
                    }
                } finally { cleanup(); pending.finish() }
            }
            return
        }

        // 🖼 사진 MMS — 실패면 사장님께 알림(2026-08-11 오프라인 감사: '사진 보냈어요'인데 못 받는 거짓 성공 방지).
        val error = intent.getStringExtra(Transaction.MMS_ERROR)
            ?: intent.extras?.keySet()?.joinToString { key -> "$key=${intent.extras?.get(key)}" }.orEmpty()
        if (resultCode == Activity.RESULT_OK) {
            Log.i(TAG, "MMS sent OK: to=${LogRedact.phone(phone)} images=$imageCount")
        } else {
            Log.e(TAG, "MMS sent FAILED: resultCode=$resultCode to=${LogRedact.phone(phone)} images=$imageCount error=$error")
            if (phone.isNotBlank()) runCatching { NotificationHelper.showMmsSendFailed(context, phone) }
        }
        cleanup()
    }

    companion object {
        const val ACTION_MMS_SENT = "com.detailline.callfollowcrm.ACTION_MMS_SENT"
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_BODY_PREVIEW = "extra_body_preview"
        const val EXTRA_BODY = "extra_body"               // 텍스트 LMS 본문(실패 시 기록 되돌림용)
        const val EXTRA_SENT_AT = "extra_sent_at"         // 텍스트 LMS 발송 시각(실패 시 기록 되돌림용)
        const val EXTRA_IMAGE_COUNT = "extra_image_count"
        const val EXTRA_SEND_FILE = "extra_send_file"
        private const val TAG = "MmsSentReceiver"
    }
}
