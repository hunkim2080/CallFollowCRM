package com.detailline.callfollowcrm.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.ai.CollabProgressSubmit
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ✅ '오늘의 현장' 알림의 [완료] 버튼 처리 — 앱 안 열고 바로 끝낸 현장을 내린다. (2026-11-26 사장님)
 *   · 내 현장  : [WorkCompletionManager.setCompleted] (카드+건 같이 · onChanged 가 오늘의 현장 알림 다시 그림)
 *   · 협업 현장: [CollabProgressSubmit] 로 '완료' 전송(화면 완료 버튼과 **같은 경로** §12) → 주인 사장에게도 반영.
 *   누른 알림은 즉시 내려 빠른 피드백. 연타/오프라인은 각 경로(Mutex·Outbox)가 막는다.
 */
class TodaySiteCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        val container = app.container
        // 누른 그 현장 알림은 즉시 내림(빠른 피드백). 나머지는 아래 refresh 가 다시 그린다.
        if (notifId != -1) runCatching { NotificationManagerCompat.from(context).cancel(notifId) }

        val customerId = intent.getLongExtra(EXTRA_CUSTOMER_ID, -1L)
        val shareId = intent.getStringExtra(EXTRA_SHARE_ID)?.takeIf { it.isNotBlank() }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (customerId > 0L) {
                    // 내 현장 — 완료 찍기(카드+건). onChanged() 가 오늘의 현장 알림을 바로 다시 그린다.
                    container.workCompletionManager.setCompleted(customerId, System.currentTimeMillis())
                } else if (shareId != null) {
                    // 협업 현장 — 서버에 '완료'. 성공해도 refresh 는 직접(협업은 onChanged 없음).
                    CollabProgressSubmit.submit(container, shareId, SharedSiteRepository.Progress.COMPLETED)
                    runCatching { ReminderWorker.refreshTodaySites(context, container) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.detailline.callfollowcrm.TODAY_SITE_COMPLETE"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_CUSTOMER_ID = "customer_id"
        const val EXTRA_SHARE_ID = "share_id"

        /** 내 현장 [완료] 버튼 PendingIntent. */
        fun own(context: Context, customerId: Long, notifId: Int): PendingIntent =
            build(context, notifId, "own:$customerId") { putExtra(EXTRA_CUSTOMER_ID, customerId) }

        /** 협업 현장 [완료] 버튼 PendingIntent. */
        fun collab(context: Context, shareId: String, notifId: Int): PendingIntent =
            build(context, notifId, "collab:$shareId") { putExtra(EXTRA_SHARE_ID, shareId) }

        private inline fun build(
            context: Context, notifId: Int, reqKey: String, extras: Intent.() -> Unit
        ): PendingIntent {
            val i = Intent(context, TodaySiteCompleteReceiver::class.java).apply {
                action = ACTION
                putExtra(EXTRA_NOTIF_ID, notifId)
                extras()
            }
            return PendingIntent.getBroadcast(
                context, "today_complete:$reqKey".hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
