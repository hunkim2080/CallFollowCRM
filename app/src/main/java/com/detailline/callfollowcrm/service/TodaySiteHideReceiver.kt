package com.detailline.callfollowcrm.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 🙈 '오늘의 현장' 알림의 [숨기기] 버튼 — 그 현장 알림만 내리고 **오늘 하루 다시 안 뜨게** 한다. (2026-11-26 사장님)
 *   ⚠️ 작업 '완료'가 아니다 — 앱 데이터·협업 서버는 **안 건드린다**. 그냥 알림을 치우는 버튼.
 *   오늘 하루만(날 바뀌면 자동 무효) — 실제 완료는 앱(홈·채팅·협업 화면)에서 하면 알림이 영구히 내려간다.
 */
class TodaySiteHideReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val key = intent.getStringExtra(EXTRA_KEY)?.takeIf { it.isNotBlank() } ?: return
        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        val container = app.container
        // 누른 알림 즉시 내림(빠른 피드백). 남은 현장·요약은 아래 refresh 가 다시 그린다.
        if (notifId != -1) runCatching { NotificationManagerCompat.from(context).cancel(notifId) }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 오늘(0시 기준) 그 현장 숨김으로 기록. 지난 날 항목은 같이 치운다(set 안 커지게).
                val today = DateTimeUtils.startOfDay(System.currentTimeMillis())
                val prefix = "$today|"
                val kept = container.preferences.hiddenTodaySiteKeys
                    .filterTo(mutableSetOf()) { it.startsWith(prefix) }
                kept.add("$today|$key")
                container.preferences.hiddenTodaySiteKeys = kept
                runCatching { ReminderWorker.refreshTodaySites(context, container) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.detailline.callfollowcrm.TODAY_SITE_HIDE"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_KEY = "site_key"

        /** [숨기기] 버튼 PendingIntent. key = "own:<id>" / "collab:<shareId>". */
        fun intent(context: Context, key: String, notifId: Int): PendingIntent {
            val i = Intent(context, TodaySiteHideReceiver::class.java).apply {
                action = ACTION
                putExtra(EXTRA_NOTIF_ID, notifId)
                putExtra(EXTRA_KEY, key)
            }
            return PendingIntent.getBroadcast(
                context, "today_hide:$key".hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
