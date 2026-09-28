package com.detailline.callfollowcrm.service

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.util.LogRedact
import com.detailline.callfollowcrm.util.PhoneKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 📮 **문자가 진짜 나갔는지** 통신사가 알려주는 것을 받는 곳. (2026-09-28)
 *
 * 왜 생겼나 —
 *   전엔 [com.detailline.callfollowcrm.util.SmsSender] 가 결과를 안 받았다.
 *   `sendTextMessage` 가 예외만 안 내면 「보냈다」였다. 그래서 전파가 약한 지하 현장이나
 *   통신사 거절에서 **손님은 아무것도 못 받았는데** 앱은 「자동문자 보냈어요」를 띄우고,
 *   기록에 AUTO_SENT 를 박고, 그 기록이 **24시간 쿨다운**을 걸어 다시 보내지도 않았다.
 *   첫 응대가 통째로 사라지는 길이었다.
 *
 * 하는 일 (실패일 때만) —
 *   ① 사장님께 알린다 — 알아야 전화라도 한 통 하신다.
 *   ② 기록을 「보냄 → 실패」로 되돌린다 → 쿨다운이 풀려 **다음 통화에 다시 보낸다.**
 *
 * 성공은 조용히 넘어간다. 잘 된 일로 알림을 울리면 진짜 실패가 묻힌다.
 *
 * MMS 는 진작 이렇게 하고 있었다([MmsSentReceiver]) — 문자만 빠져 있던 두 벌이다.
 */
class SmsSentReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT) return
        val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        if (phone.isBlank()) return
        // 어떤 문자였는지 — 번호만으로는 **몇 분 전에 보낸 다른 문자**를 잡는다. (2026-09-28 테스트폰 실측)
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val sentAt = intent.getLongExtra(EXTRA_SENT_AT, 0L)

        if (resultCode == Activity.RESULT_OK) {
            Log.i(TAG, "SMS sent OK: to=${LogRedact.phone(phone)}")
            return
        }

        val why = reasonOf(resultCode)
        Log.e(TAG, "SMS sent FAILED: to=${LogRedact.phone(phone)} code=$resultCode ($why)")

        val app = context.applicationContext as? CallFollowCrmApplication
        val pending = goAsync()
        scope.launch {
            try {
                // ⏱ **실패 통보가 기록보다 먼저 올 수 있다.** (2026-09-28 테스트폰 실측)
                //   비행기 모드처럼 즉시 실패하면 통신사 통보가 밀리초 안에 온다. 그런데 「보냄」 기록은
                //   보낸 **다음에** 저장된다 — 한 번만 찾아보면 아직 없어서 **못 되돌린다.**
                //   그러면 알림만 뜨고 쿨다운은 그대로 걸려, 다음 통화에도 안 보내는 옛 병이 남는다.
                //   그래서 잠깐씩 쉬며 몇 번 더 본다(goAsync 가 주는 10초 안에서).
                var flipped = false
                repeat(FLIP_TRIES) { attempt ->
                    if (flipped) return@repeat
                    if (attempt > 0) delay(FLIP_WAIT_MS)
                    flipped = runCatching {
                        app?.container?.messageHistoryRepository?.markLastSendFailed(
                            phone, sentAt - CLOCK_SLACK_MS, body
                        )
                    }.getOrNull() == true
                }
                Log.i(TAG, "기록 되돌림=$flipped to=${LogRedact.phone(phone)}")
                runCatching { NotificationHelper.showSmsSendFailed(context, phone, why) }
            } finally {
                pending.finish()
            }
        }
    }

    /** 통신사가 준 번호를 사장님이 읽을 수 있는 말로. 모르는 번호는 비워둔다(억지 설명 금지). */
    private fun reasonOf(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "전파 없음"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "비행기 모드"
        SmsManager.RESULT_ERROR_NULL_PDU -> "문자 내용 오류"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "통신사 거절"
        else -> ""
    }

    companion object {
        const val ACTION_SMS_SENT = "com.detailline.callfollowcrm.ACTION_SMS_SENT"
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_SENT_AT = "extra_sent_at"
        private const val TAG = "SmsSentReceiver"

        /** 기록은 발송 **뒤**에 적힌다. 시계가 살짝 어긋나도 놓치지 않을 만큼만 앞을 본다. */
        private const val CLOCK_SLACK_MS = 5_000L

        /** 기록이 아직 안 저장됐을 수 있어 몇 번 더 본다. 3회 × 1.5초 = 3초 — goAsync 10초 안. */
        private const val FLIP_TRIES = 3
        private const val FLIP_WAIT_MS = 1500L

        /**
         * 보낼 때 같이 줄 「결과 알려줘」 쪽지.
         *   requestCode 를 **번호마다 다르게** 줘야 한 번호의 결과가 다른 번호 것을 덮지 않는다.
         *   FLAG_IMMUTABLE — 통신사가 붙이는 결과 코드는 extras 가 아니라 resultCode 로 온다.
         */
        fun pendingIntent(context: Context, phone: String, body: String): PendingIntent {
            val intent = Intent(context, SmsSentReceiver::class.java).apply {
                action = ACTION_SMS_SENT
                putExtra(EXTRA_PHONE, phone)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_SENT_AT, System.currentTimeMillis())
            }
            val req = (PhoneKey.of(phone) + "|" + body.take(40)).hashCode()
            return PendingIntent.getBroadcast(
                context.applicationContext, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
