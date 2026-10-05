package com.detailline.callfollowcrm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 재부팅 뒤 예약 알람 다시 걸기 — 알람은 부팅 때 전부 사라지기 때문.
 *   폰이 꺼져 있던 동안 지나버린 예약은 [ScheduledSmsScheduler.rearmAll] 이 즉시 보낸다.
 */
class ScheduledSmsBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduledSmsScheduler.rearmAll(app)
            } catch (t: Throwable) {
                Log.w("ScheduledSmsBoot", "rearm failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
