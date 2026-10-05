package com.detailline.callfollowcrm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 🕐 예약 시각에 깨어나 한 건을 보낸다. (알람은 [ScheduledSmsScheduler] 가 걸었다)
 *   onReceive 는 금방 끝나야 하므로 `goAsync` 로 잠깐 시간을 벌어 IO 에서 발송한다.
 */
class ScheduledSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val rowId = intent.getLongExtra(EXTRA_ROW_ID, -1L)
        if (rowId < 0) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduledSmsSender.sendNow(app, rowId)
            } catch (t: Throwable) {
                Log.w(TAG, "scheduled send failed id=$rowId", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScheduledSmsReceiver"
        const val ACTION_FIRE = "com.detailline.callfollowcrm.SCHEDULED_SMS_FIRE"
        const val EXTRA_ROW_ID = "row_id"
    }
}
