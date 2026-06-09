package com.detailline.callfollowcrm.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.klinker.android.send_message.Transaction

/**
 * MMS 직접 발송 결과 콜백.
 * SmsSender.sendMms()는 비동기 요청만 하므로 실제 성공/실패는 여기 로그로 확인한다.
 */
class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MMS_SENT) return

        val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        val preview = intent.getStringExtra(EXTRA_BODY_PREVIEW).orEmpty()
        val imageCount = intent.getIntExtra(EXTRA_IMAGE_COUNT, 0)
        val error = intent.getStringExtra(Transaction.MMS_ERROR)
            ?: intent.extras?.keySet()?.joinToString { key -> "$key=${intent.extras?.get(key)}" }.orEmpty()

        if (resultCode == Activity.RESULT_OK) {
            Log.i(TAG, "MMS sent OK: to=$phone images=$imageCount body='$preview'")
        } else {
            Log.e(TAG, "MMS sent FAILED: resultCode=$resultCode to=$phone images=$imageCount error=$error")
        }
    }

    companion object {
        const val ACTION_MMS_SENT = "com.detailline.callfollowcrm.ACTION_MMS_SENT"
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_BODY_PREVIEW = "extra_body_preview"
        const val EXTRA_IMAGE_COUNT = "extra_image_count"
        private const val TAG = "MmsSentReceiver"
    }
}
