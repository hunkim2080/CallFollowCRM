package com.detailline.callfollowcrm.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.detailline.callfollowcrm.util.LogRedact
import com.klinker.android.send_message.Transaction

/**
 * MMS 직접 발송 결과 콜백.
 * SmsSender.sendMms()는 비동기 요청만 하므로 실제 성공/실패는 여기 로그로 확인한다.
 */
class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MMS_SENT) return

        val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        val imageCount = intent.getIntExtra(EXTRA_IMAGE_COUNT, 0)
        val error = intent.getStringExtra(Transaction.MMS_ERROR)
            ?: intent.extras?.keySet()?.joinToString { key -> "$key=${intent.extras?.get(key)}" }.orEmpty()

        if (resultCode == Activity.RESULT_OK) {
            Log.i(TAG, "MMS sent OK: to=${LogRedact.phone(phone)} images=$imageCount")
        } else {
            Log.e(TAG, "MMS sent FAILED: resultCode=$resultCode to=${LogRedact.phone(phone)} images=$imageCount error=$error")
            // 거짓 성공 방지(2026-08-11 오프라인 감사 rank1): 실패를 사장님에게 알려 신호 좋을 때 다시 보내게.
            //   예전엔 Log.e 만 찍어, 화면엔 '사진 보냈어요'로 뜨는데 고객은 못 받는 사고가 났음.
            if (phone.isNotBlank()) runCatching { NotificationHelper.showMmsSendFailed(context, phone) }
        }

        // 공식 API 경로가 넘긴 임시 PDU 파일 정리(성공/실패 무관).
        intent.getStringExtra(EXTRA_SEND_FILE)?.let { name ->
            runCatching { java.io.File(context.cacheDir, java.io.File(name).name).delete() }
        }
    }

    companion object {
        const val ACTION_MMS_SENT = "com.detailline.callfollowcrm.ACTION_MMS_SENT"
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_BODY_PREVIEW = "extra_body_preview"
        const val EXTRA_IMAGE_COUNT = "extra_image_count"
        const val EXTRA_SEND_FILE = "extra_send_file"
        private const val TAG = "MmsSentReceiver"
    }
}
