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
        // 알림 빌드 중 어떤 예외든(OEM PendingIntent/채널 등) FCM 콜백 죽지 않게 통째 가드. (2026-06-30 안정성 점검)
        runCatching {
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
                        accountText = accountText,
                        reason = data["decline_reason"]?.takeIf { it.isNotBlank() },   // 거절 사유(서버가 실어주면 표시) (2026-07-08)
                        auto = data["auto"] == "true"   // §E 3km 자동 도착 → "거의 도착해가요"
                    )
                }
            }
            // §E: B 가 3km 자동 도착 → "사장님께 알려드렸어요" 확인(받는 쪽 = 협업 사장 B).
            "collab_arrived_confirm" -> {
                val shareId = data["share_id"].orEmpty()
                if (shareId.isNotBlank()) {
                    NotificationHelper.showCollabArrivedConfirm(this, shareId, data["title"].orEmpty())
                }
            }
            "collab_paid" -> {
                val shareId = data["share_id"].orEmpty()
                if (shareId.isNotBlank()) {
                    NotificationHelper.showCollabPaid(this, shareId, data["title"].orEmpty())
                }
            }
            // 협업 해제됨 — 상대가 끝냄(A 해제 / B 그만하기). 받는 쪽에 알림.
            "collab_ended" -> {
                val shareId = data["share_id"].orEmpty()
                if (shareId.isNotBlank()) {
                    NotificationHelper.showCollabEnded(this, shareId, data["by_name"].orEmpty(), data["title"].orEmpty())
                }
            }
            // 협업 현장 새 댓글 — 상대 사장이 한 줄 댓글을 달면 받는 쪽에 즉시 알림(폴링 안 기다리고). (2026-07-02 사장님)
            //   서버는 댓글 작성자 본인은 빼고 '상대 참여자' 번호로만 push. site_id = share_id.
            "collab_comment" -> {
                val siteId = (data["site_id"] ?: data["share_id"]).orEmpty()
                if (siteId.isNotBlank()) {
                    NotificationHelper.showCollabComment(
                        context = this,
                        siteId = siteId,
                        authorName = data["author_name"].orEmpty(),
                        siteTitle = data["title"].orEmpty(),
                        body = data["body"].orEmpty()
                    )
                }
            }
            // 협업 현장 새 사진 — 상대 사장이 현장 증거사진을 올리면 받는 쪽에 알림. (2026-07-02 사장님)
            //   서버는 업로더 본인 빼고 상대 참여자에게만 push. site_id = share_id.
            "collab_photo" -> {
                val siteId = (data["site_id"] ?: data["share_id"]).orEmpty()
                if (siteId.isNotBlank()) {
                    NotificationHelper.showCollabPhoto(
                        context = this,
                        siteId = siteId,
                        uploaderName = data["uploader_name"].orEmpty(),
                        siteTitle = data["title"].orEmpty()
                    )
                }
            }
            // 시공접수서 제출 — 고객이 작성 완료하는 즉시(폴링 60초 안 기다리고) 동기화.
            //   서버가 제출 저장 직후 data push(type=intake_submitted)를 사장님 번호로 보냄 → 여기서 바로 sync().
            //   sync() 가 GET /api/quote/submissions 로 새 건을 가져와 고객 카드 반영 + 알림 + 채팅 타임라인 카드까지
            //   처리(폴링과 동일 경로). token 중복 가드가 있어 폴링과 겹쳐도 이중 알림 없음. 폴링은 안전망으로 유지.
            "intake_submitted" -> {
                val app = applicationContext as? CallFollowCrmApplication
                if (app != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching { app.container.intakeSyncManager.sync(applicationContext) }
                    }
                }
            }
        }
        }
    }
}
