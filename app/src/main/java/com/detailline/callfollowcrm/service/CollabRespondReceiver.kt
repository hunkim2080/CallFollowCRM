package com.detailline.callfollowcrm.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 🤝 협업 요청 알림의 [거절]/[수락] 버튼 처리 — 앱 안 열고 뒤에서 서버로. (2026-11-25 사장님)
 *   ① 즉시 카드·배지·떠있던 알림·벨 내림(markResponded, 낙관) → ② Outbox 로 /api/shared/respond 전송.
 *   오프라인·느린 네트워크·연타 안전: Outbox 가 enqueueMutex + TERMINAL 합치기로 한 줄만 보낸다(§13③).
 *   실수로 눌러도 수락 후 앱(협업 현장)에서 되돌릴 수 있다(안전망).
 */
class CollabRespondReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val shareId = intent.getStringExtra(EXTRA_SHARE_ID)?.takeIf { it.isNotBlank() } ?: return
        val accept = intent.getBooleanExtra(EXTRA_ACCEPT, false)
        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        val container = app.container
        // ① 낙관적으로 즉시 내림 — 상담함 카드·하단 배지·떠 있던 ongoing 알림 제거(다음 폴 안 기다림).
        container.collabEventCenter.markResponded(context, shareId)
        // ② 서버 전송은 Outbox 로(오프라인이면 보관했다 길 뚫리면 마저) + 벨 읽음 처리.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.outbox.enqueue(
                    kind = OutboxKind.COLLAB_RESPOND,
                    targetKey = shareId,
                    payloadJson = JSONObject().put("accept", accept).toString()
                )
                container.outbox.tryNow()
                runCatching { container.appNotificationRepository.markReadByShareId(shareId) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.detailline.callfollowcrm.COLLAB_RESPOND"
        const val EXTRA_SHARE_ID = "share_id"
        const val EXTRA_ACCEPT = "accept"

        /** 알림 [거절]/[수락] 버튼이 쓸 PendingIntent — 수락/거절별 요청코드를 갈라 둘이 안 덮어쓴다. */
        fun intent(context: Context, shareId: String, accept: Boolean): PendingIntent {
            val i = Intent(context, CollabRespondReceiver::class.java).apply {
                action = ACTION
                putExtra(EXTRA_SHARE_ID, shareId)
                putExtra(EXTRA_ACCEPT, accept)
            }
            return PendingIntent.getBroadcast(
                context, "collab_respond:$shareId:$accept".hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
