package com.detailline.callfollowcrm.service

import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FCM 수신 — 서버가 보낸 data 메시지를 받아 즉시 알림(앱 꺼져 있어도). docs/SERVER_HANDOFF_fcm_push.md
 *   ※ 서버는 반드시 notification 블록 없이 data 만 보냄 → 여기서 한국어 문구/탭 동작을 직접 띄움.
 *   폴링(CollabEventCenter)은 그대로 두는 안전망 — FCM 못 와도 곧 폴링이 잡음.
 */
class RingGoFcmService : FirebaseMessagingService() {

    /** 토큰 갱신(설치/복원/주기 회전) 시 서버 재등록. bizPhone 없으면 보류(앱 시작 시 재시도). */
    override fun onNewToken(token: String) {
        val app = applicationContext as? CallFollowCrmApplication ?: return
        val phone = app.container.preferences.bizPhone.trim()
        if (phone.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { app.container.pushRegisterRepository.register(phone, token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "collab_invite" -> {
                val shareId = data["share_id"].orEmpty()
                if (shareId.isNotBlank()) {
                    NotificationHelper.showCollabInvite(
                        context = this,
                        shareId = shareId,
                        ownerName = data["owner_name"].orEmpty(),
                        title = data["title"].orEmpty()
                    )
                    // FCM이 이미 띄웠으니 폴링이 같은 초대를 또 띄우지 않게 seen 에 추가(이중 알림 방지).
                    (applicationContext as? CallFollowCrmApplication)?.container?.preferences?.let { p ->
                        p.seenCollabInviteShareIds = p.seenCollabInviteShareIds + shareId
                    }
                }
            }
            "collab_event" -> {
                val shareId = data["share_id"].orEmpty()
                if (shareId.isNotBlank()) {
                    // 서버는 완료 시 bank/account_no/holder 를 따로 보냄 → 한 줄로 합침. time_label 은 안 보냄 → "방금".
                    val accountText = listOfNotNull(
                        data["bank"]?.takeIf { it.isNotBlank() },
                        data["account_no"]?.takeIf { it.isNotBlank() },
                        data["holder"]?.takeIf { it.isNotBlank() }
                    ).joinToString(" ").takeIf { it.isNotBlank() }
                    NotificationHelper.showCollabEvent(
                        context = this,
                        eventId = data["event_id"]?.takeIf { it.isNotBlank() } ?: shareId,
                        shareId = shareId,
                        kind = data["step"].orEmpty(),
                        partnerName = data["partner_name"].orEmpty(),
                        timeLabel = data["time_label"]?.takeIf { it.isNotBlank() } ?: "방금",
                        title = data["title"].orEmpty(),
                        accountText = accountText
                    )
                }
            }
            "collab_paid" -> {
                val shareId = data["share_id"].orEmpty()
                if (shareId.isNotBlank()) {
                    NotificationHelper.showCollabPaid(this, shareId, data["title"].orEmpty())
                }
            }
        }
    }
}
