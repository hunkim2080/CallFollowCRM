package com.detailline.callfollowcrm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.service.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 디버그 전용 — 마감 브리핑 알림을 지금 즉시 띄운다(시간·중복 게이트 무시). release 엔 포함 안 됨.
 *   사용: adb shell am broadcast -a com.detailline.callfollowcrm.debug.SHOW_BRIEF -n com.detailline.callfollowcrm/.debug.DebugBriefReceiver
 */
class DebugBriefReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW_BRIEF) return
        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val shown = ReminderWorker.showDailyBriefNow(
                    context.applicationContext, app.container, respectQuietDay = false
                )
                Log.i(TAG, "Debug daily brief shown=$shown")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SHOW_BRIEF = "com.detailline.callfollowcrm.debug.SHOW_BRIEF"
        private const val TAG = "DebugBriefReceiver"
    }
}
