package com.detailline.callfollowcrm.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.detailline.callfollowcrm.AppConfig
import com.detailline.callfollowcrm.MainActivity
import com.detailline.callfollowcrm.R

object NotificationHelper {

    private const val CHANNEL_FOLLOW_UP = "call_follow_up"
    private const val CHANNEL_FOLLOW_UP_QUIET = "call_follow_up_quiet"
    private const val CHANNEL_AUTO_REPLY = "auto_reply_2"
    /** 2026-05-25 사장님 결정 — RING-GO 가 갤메시지보다 더 좋은 알림창. 풍부한 정보 + AI 추천 답변. */
    private const val CHANNEL_INCOMING_SMS = "incoming_sms_2"
    /** 처음 연락온 신규 고객 — 별도 소리로 구분. */
    private const val CHANNEL_INCOMING_SMS_NEW = "incoming_sms_new"
    /** 고객이 시공접수서를 작성·제출했을 때 알림. */
    private const val CHANNEL_INTAKE = "intake_submitted_2"
    private const val INTAKE_ID_OFFSET = 8_000_000
    /** 시간 기반 리마인더(시공 D-1·잔금 미수·마감 브리핑). */
    private const val CHANNEL_REMINDER = "reminder_2"
    private const val D1_ID_OFFSET = 9_000_000
    private const val SETTLE_ID_OFFSET = 9_500_000
    private const val BRIEF_ID = 9_700_000
    private const val RECUR_ID = 9_800_000
    private const val ARRIVAL_ID_OFFSET = 9_900_000
    private const val DEPART_ID_OFFSET = 9_600_000
    private const val COLLAB_ID_OFFSET = 9_400_000
    private const val COLLAB_INVITE_ID_OFFSET = 9_450_000
    /** 협업 현장 새 댓글 알림 — site_id hash 기준(현장당 한 스레드 알림, 새 댓글이면 update). (2026-07-02) */
    private const val COLLAB_COMMENT_ID_OFFSET = 9_350_000
    /** 협업 현장 새 사진 알림 — 상대가 현장 증거사진 올리면. (2026-07-02) */
    private const val COLLAB_PHOTO_ID_OFFSET = 9_360_000
    /** SMS 알림 ID = 발신번호 hash + offset. 같은 번호 새 SMS = 같은 알림 update. */
    private const val SMS_ID_OFFSET = 10_000_000
    private const val MMS_FAIL_ID = 9_300_000

    // 알림 배너 배경 — 파스텔 블루 (Material Blue 100).
    // setColorized(true) 와 함께 쓰면 OneUI 등 일부 시스템이 배너 전체 배경으로 사용.
    // 시스템이 colorized 를 무시해도 setColor 는 항상 small-icon 틴트 + 앱명 accent 로 동작.
    private val NOTIFICATION_BG_COLOR = 0xFFBBDEFB.toInt()
    /** callRecordId 없을 때만 쓰는 fallback. 정상 흐름은 항상 callRecordId 기반 unique ID. */
    private const val FALLBACK_NOTIFICATION_ID = 1001
    /** AutoReply 알림은 callRecordId 기반 + offset 으로 후속 알림과 분리. */
    private const val AUTO_REPLY_ID_OFFSET = 5_000_000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val audioAttrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            fun snd(resId: Int) = Uri.parse("android.resource://${context.packageName}/$resId")

            if (manager.getNotificationChannel(CHANNEL_FOLLOW_UP) == null) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_FOLLOW_UP, "통화 후속 안내", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "방금 끝난 통화에 대한 후속 문자 안내" })
            }
            if (manager.getNotificationChannel(CHANNEL_AUTO_REPLY) == null) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_AUTO_REPLY, "자동 응답 문자", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "처음 연락온 고객 자동 응답 발송 안내 (취소 가능)"
                    setSound(snd(R.raw.sound_auto_reply), audioAttrs)
                })
            }
            // quiet 후속 안내 — 2번째 이후 통화이지만 사장님이 아직 답장/분류 안 한 경우.
            // 배너 X (heads-up 없음), 사운드 X, 알림함에만 조용히 쌓임.
            if (manager.getNotificationChannel(CHANNEL_FOLLOW_UP_QUIET) == null) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_FOLLOW_UP_QUIET, "후속 미처리 안내", NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "답장 못 한 손님이 또 연락 왔을 때 조용히 알려줘요"
                    setShowBadge(true)
                })
            }
            // 기존 고객 답장 — 갤메시지 대체 알림.
            if (manager.getNotificationChannel(CHANNEL_INCOMING_SMS) == null) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_INCOMING_SMS, "📩 새 문자", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "고객 SMS 가 오면 AI 추천 답변과 함께 표시 — 갤메시지 알림은 끄고 사용하세요"
                    setSound(snd(R.raw.sound_reply), audioAttrs)
                    setShowBadge(true)
                })
            }
            // 신규 문의 — 처음 연락온 고객, 별도 소리로 구분.
            if (manager.getNotificationChannel(CHANNEL_INCOMING_SMS_NEW) == null) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_INCOMING_SMS_NEW, "📩 신규 문의", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "처음 연락온 신규 고객 문자 — 바로 답장해요"
                    setSound(snd(R.raw.sound_new_inquiry), audioAttrs)
                    setShowBadge(true)
                })
            }
            if (manager.getNotificationChannel(CHANNEL_INTAKE) == null) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_INTAKE, "📋 접수서 작성됨", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "고객이 시공접수서를 작성하면 알려줘요"
                    setSound(snd(R.raw.sound_intake), audioAttrs)
                    setShowBadge(true)
                })
            }
            if (manager.getNotificationChannel(CHANNEL_REMINDER) == null) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_REMINDER, "⏰ 시공·정산 리마인더", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "내일 시공 안내·잔금 미수·마감 브리핑을 제때 알려줘요"
                    setSound(snd(R.raw.sound_reminder), audioAttrs)
                    setShowBadge(true)
                })
            }
        }
    }

    /**
     * 시공 D-1 안내 알림 — 프로토 PUSH.d1 형식 1:1 (주황).
     *   무음 자동발송 X — 탭/버튼 = 그 고객 채팅에서 사장님이 확인 후 발송.
     */
    fun showInstallD1(
        context: Context,
        customerId: Long,
        phone: String,
        name: String,
        dateLabel: String,
        timeLabel: String?,
        address: String
    ) {
        val notifId = D1_ID_OFFSET + (customerId.toInt() and 0x7FFFFF)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, phone)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val msg = "${name}님 · $dateLabel${timeLabel?.let { " $it" } ?: ""} · $address"
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, ACCENT_AMBER,
            title = "내일 시공 — 안내 문자 보낼까요?",
            msg = msg,
            note = "무음 자동발송 안 해요 · 사장님이 확인하면 보내요",
            contentIntent = pending,
            actions = listOf(PushAction("안내 보내기", pending))
        )
    }

    /**
     * 잔금 미수 리마인더 — 프로토 PUSH.settle 형식(분홍).
     *   탭/버튼 = 그 고객 채팅(잔금 요청 문자 작성).
     */
    fun showBalanceDue(
        context: Context,
        customerId: Long,
        phone: String,
        name: String,
        balanceManwon: Long,
        daysSince: Int
    ) {
        val notifId = SETTLE_ID_OFFSET + (customerId.toInt() and 0x7FFFFF)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, phone)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, ACCENT_PINK,
            title = "아직 안 들어온 잔금이 있어요",
            msg = "${name}님 · 잔금 ${balanceManwon}만원 · 시공 완료 후 ${daysSince}일째 미입금",
            contentIntent = pending,
            actions = listOf(PushAction("잔금 요청 보내기", pending))
        )
    }

    private fun appOpenPending(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 현장 도착 안내 — 프로토 PUSH.arrival(초록). 5km 진입 시. 무음 자동발송 X. */
    fun showArrival(context: Context, customerId: Long, phone: String, name: String) {
        val notifId = ARRIVAL_ID_OFFSET + (customerId.toInt() and 0x7FFFFF)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, phone)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, ACCENT_GREEN,
            title = "현장 도착 — 안내 문자 보낼까요?",
            msg = "${name}님 현장 5km 안에 들어왔어요",
            note = "확인 후 보내요 · 무음 자동발송 아니에요",
            contentIntent = pending,
            actions = listOf(PushAction("도착 안내 보내기", pending))
        )
    }

    /**
     * 팀원 진행 알림 — 팀원이 링크 화면에서 [출발/도착/완료] 누르면. 누가·몇시·어디서. 탭 → 앱(팀 현황).
     *   kind = "departed" | "arrived" | "completed".
     */
    fun showTeamEvent(
        context: Context,
        eventId: Long,
        kind: String,
        memberName: String,
        timeLabel: String,
        place: String,
        text: String? = null
    ) {
        val notifId = DEPART_ID_OFFSET + (eventId.toInt() and 0x7FFFFF)
        val pending = appOpenPending(context, notifId)
        val (title, msg, accent) = when (kind) {
            "arrived" -> Triple(
                "팀원 현장 도착 📍",
                "${memberName}님이 $timeLabel · ${place}에 도착했어요",
                ACCENT_BLUE
            )
            "completed" -> Triple(
                "작업 완료 ✅",
                "${memberName}님이 $timeLabel · ${place} 작업을 끝냈어요",
                ACCENT_PURPLE
            )
            "note" -> Triple(
                "현장 메모 📝",
                "${memberName}님 (${place}): ${text ?: ""}",
                ACCENT_AMBER
            )
            else -> Triple(
                "팀원 출발 🚗",
                "${memberName}님이 $timeLabel · ${place}으로 출발했어요",
                ACCENT_GREEN
            )
        }
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, accent,
            title = title,
            msg = msg,
            contentIntent = pending,
            actions = listOf(PushAction("팀 현황 보기", pending))
        )
    }

    /** 협업 진행 알림 — 다른 사장님이 공유 현장에서 출발/도착/완료를 누르면. */
    fun showCollabEvent(
        context: Context,
        eventId: String,
        shareId: String,
        kind: String,
        partnerName: String,
        timeLabel: String,
        title: String,
        accountText: String? = null,
        auto: Boolean = false
    ) {
        val notifId = COLLAB_ID_OFFSET + (eventId.hashCode() and 0x7FFFFF)
        // 협업 진행 알림(수락/출발/도착/완료)은 전부 '주인(A)'이 받음. A 는 '받은 협업현장' 목록에
        //   이 현장이 없어 /shared/{id} 로 보내면 "공유받은 현장이 없어요"가 떠 버림(2026-06-14 버그).
        //   기존엔 그래서 action 없이 앱만 열어 → 탭해도 아무 반응 없음(2026-06-20 사장님 신고).
        //   → A 의 "내가 공유한 현장" 탭으로 보냄(거기서 수락/진행 확인). ACTION_COLLAB_MINE.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_COLLAB_MINE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val (pushTitle, msg, accent) = when (kind) {
            "arrived" -> if (auto) Triple(
                "거의 도착 📍",
                "${partnerName}님이 거의 도착했어요 · ${title} 3km 진입",
                ACCENT_GREEN
            ) else Triple(
                "협업 현장 도착 📍",
                "${partnerName}님이 $timeLabel · ${title}에 도착했어요",
                ACCENT_BLUE
            )
            "completed" -> Triple(
                "협업 작업 완료 ✅",
                "${partnerName}님이 $timeLabel · ${title} 작업을 끝냈어요",
                ACCENT_PURPLE
            )
            "accepted" -> Triple(
                "협업 수락 🤝",
                "${partnerName}님이 '${title}' 협업을 수락했어요 — 함께 가요!",
                ACCENT_PURPLE
            )
            "declined" -> Triple(
                "협업 요청 거절",
                "${partnerName}님이 '${title}' 협업을 거절했어요.",
                ACCENT_PINK
            )
            "departed" -> Triple(
                "협업 현장 출발 🚗",
                "${partnerName}님이 $timeLabel · ${title}으로 출발했어요",
                ACCENT_GREEN
            )
            // 모르는 step 은 "출발"로 잘못 표시하지 말고 무시. (2026-06-20 버그 fix: B가 거절(declined)하면 A에 "출발" 푸시가 뜨던 것 — else 가 출발로 떨어져서)
            else -> return
        }
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, accent,
            title = pushTitle,
            msg = accountText?.let { "$msg · 계좌 $it" } ?: msg,
            contentIntent = pending,
            actions = listOf(PushAction("협업 현장 보기", pending))
        )
    }

    /** 협업 현장 새 댓글 알림 — 상대 사장이 현장에 한 줄 댓글을 달면. 탭 → 협업 현장(내가 공유한 탭). (2026-07-02 사장님) */
    fun showCollabComment(
        context: Context,
        siteId: String,
        authorName: String,
        siteTitle: String,
        body: String
    ) {
        if (siteId.isBlank()) return
        val notifId = COLLAB_COMMENT_ID_OFFSET + (siteId.hashCode() and 0x7FFFFF)
        // 탭 → 그 현장 상세(댓글)로 바로. ACTION_COLLAB_SITE + shareId → SharedSiteScreen 이 초기 shareId 로 상세 자동 오픈
        //   (받은현장 B·내가공유한현장 A 둘 다 매칭). (2026-07-02 사장님 "댓글로 가야하는데 목록으로 감")
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_COLLAB_SITE
            putExtra(MainActivity.EXTRA_SHARE_ID, siteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val who = authorName.ifBlank { "협업 사장님" }
        val where = siteTitle.ifBlank { "협업 현장" }
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, ACCENT_PURPLE,
            title = "💬 협업 현장 새 댓글",
            msg = "${who}님 · ${where}: ${body.ifBlank { "(내용 없음)" }}",
            contentIntent = pending,
            actions = listOf(PushAction("댓글 보기", pending))
        )
    }

    /** 협업 현장 새 사진 알림 — 상대 사장이 현장 증거사진을 올리면. 탭 → 그 현장 상세(사진). (2026-07-02 사장님) */
    fun showCollabPhoto(
        context: Context,
        siteId: String,
        uploaderName: String,
        siteTitle: String
    ) {
        if (siteId.isBlank()) return
        val notifId = COLLAB_PHOTO_ID_OFFSET + (siteId.hashCode() and 0x7FFFFF)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_COLLAB_SITE
            putExtra(MainActivity.EXTRA_SHARE_ID, siteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val who = uploaderName.ifBlank { "협업 사장님" }
        val where = siteTitle.ifBlank { "협업 현장" }
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, ACCENT_BLUE,
            title = "📸 협업 현장 새 사진",
            msg = "${who}님이 '${where}'에 현장 사진을 올렸어요",
            contentIntent = pending,
            actions = listOf(PushAction("사진 보기", pending))
        )
    }

    /**
     * 협업 요청이 왔어요(받는 쪽) — 상대 사장이 나에게 현장을 공유 요청(status=pending)했을 때 알림.
     *   탭하면 협업 현장 화면(/shared/{shareId})이 열려 수락/거절 가능. (2026-06-12 사장님 요청)
     *   고객 번호/대화는 안 들어옴(벽) — 보내는 사장 이름 + 현장 라벨만.
     */
    fun showCollabInvite(context: Context, shareId: String, ownerName: String, title: String) {
        val notifId = COLLAB_INVITE_ID_OFFSET + (shareId.hashCode() and 0x7FFFFF)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("${AppConfig.BASE_URL.trimEnd('/')}/shared/$shareId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val who = ownerName.takeIf { it.isNotBlank() }?.let { "$it 사장님" } ?: "다른 사장님"
        val site = title.takeIf { it.isNotBlank() } ?: "현장"
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, ACCENT_PURPLE,
            title = "🤝 협업 요청이 왔어요",
            msg = "${who}이 '${site}' 협업을 요청했어요. 눌러서 수락하기",
            contentIntent = pending,
            actions = listOf(PushAction("수락하러 가기", pending))
        )
    }

    /** 협업 초대 알림 지우기 — B 가 수락/거절하면 호출. showCollabInvite 와 같은 notifId 공식. (2026-06-14) */
    fun cancelCollabInvite(context: Context, shareId: String) {
        val notifId = COLLAB_INVITE_ID_OFFSET + (shareId.hashCode() and 0x7FFFFF)
        NotificationManagerCompat.from(context).cancel(notifId)
    }

    /**
     * 3km 자동 도착 확인(협업 사장 B 가 받음) — geofence 로 "거의 도착"이 주인께 자동 전송됐음을 B 에게 확인. (FCM collab_arrived_confirm)
     *   프로토 b-remind 아래 푸시: "사장님께 '거의 다 왔어요'를 알려드렸어요!"
     */
    fun showCollabArrivedConfirm(context: Context, shareId: String, title: String) {
        val notifId = COLLAB_INVITE_ID_OFFSET + ("arrconf:$shareId".hashCode() and 0x7FFFFF)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("${AppConfig.BASE_URL.trimEnd('/')}/shared/$shareId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val site = title.takeIf { it.isNotBlank() } ?: "협업 현장"
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, ACCENT_GREEN,
            title = "📍 사장님께 알려드렸어요",
            msg = "${site} 3km 진입 · 자동으로 전송됐어요. 도착 버튼은 안 눌러도 돼요 😊",
            contentIntent = pending,
            actions = listOf(PushAction("협업 현장 보기", pending))
        )
    }

    /** 협업 해제됨 — 상대(A 또는 B)가 협업을 끝냄. 받는 쪽에 알림 + 기록은 보존. (FCM collab_ended) */
    fun showCollabEnded(context: Context, shareId: String, byName: String, title: String) {
        val notifId = COLLAB_INVITE_ID_OFFSET + ("ended:$shareId".hashCode() and 0x7FFFFF)
        // 탭하면 "무엇을/누가 해제했는지" 토스트 + 협업 현장 목록. (전엔 /shared/{id} 로 갔는데 해제된 현장은 목록서
        //   빠져 상세가 안 열리고 빈 목록만 떴음 — "뭐가 해제됐는지 안 보임" fix). (2026-06-21 사장님)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_COLLAB_ENDED
            putExtra(MainActivity.EXTRA_COLLAB_TITLE, title)
            putExtra(MainActivity.EXTRA_COLLAB_BY, byName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val who = byName.takeIf { it.isNotBlank() } ?: "상대 사장님"
        val site = title.takeIf { it.isNotBlank() } ?: "협업 현장"
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, ACCENT_PURPLE,
            title = "협업이 해제됐어요",
            msg = "${who}이 '${site}' 협업을 해제했어요 — 기록(사진·메모)은 남아있어요",
            contentIntent = pending,
            actions = listOf(PushAction("협업 현장 보기", pending))
        )
    }

    /** 협업 입금 완료(받는 쪽) — 주인(A)이 입금완료 표시 → 협업한 사장 B 에게 알림. (FCM collab_paid) */
    fun showCollabPaid(context: Context, shareId: String, title: String) {
        val notifId = COLLAB_INVITE_ID_OFFSET + ("paid:$shareId".hashCode() and 0x7FFFFF)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("${AppConfig.BASE_URL.trimEnd('/')}/shared/$shareId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val site = title.takeIf { it.isNotBlank() } ?: "협업 현장"
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, ACCENT_GREEN,
            title = "💰 입금 완료",
            msg = "'${site}' 정산 입금이 완료됐어요",
            contentIntent = pending,
            actions = listOf(PushAction("협업 현장 보기", pending))
        )
    }

    /** 마감 브리핑 — 프로토 PUSH.brief 형식(파랑, 저녁 9시). 확실한 데이터만. */
    fun showDailyBrief(context: Context, newCustomers: Int, deposits: Int, tomorrowJobs: Int, tomorrowLabel: String?) {
        val parts = buildList {
            if (newCustomers > 0) add("새 고객 ${newCustomers}명")
            if (deposits > 0) add("입금 ${deposits}건")
        }
        val msg = if (parts.isEmpty()) "오늘 하루도 고생하셨어요" else "오늘 " + parts.joinToString(" · ")
        val note = if (tomorrowJobs > 0)
            "내일 시공 ${tomorrowJobs}곳" + (tomorrowLabel?.let { " — $it" } ?: "")
        else null
        // 2026-06-06: 탭하면 마감 브리핑 화면으로 (기존엔 홈만 열림).
        val briefIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_DAILY_BRIEF
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, BRIEF_ID, briefIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        showProtoPush(
            context, BRIEF_ID, CHANNEL_REMINDER, ACCENT_BLUE,
            title = "오늘 하루 마감 브리핑 🌙",
            msg = msg, note = note,
            contentIntent = pending,
            actions = listOf(PushAction("오늘 정리 보기", pending))
        )
    }

    /** 정기 문자 발송 전 확인 — 프로토 PUSH.recur 형식(청록, 오전 9시). */
    fun showRecurringDue(context: Context, count: Int, ruleNames: String) {
        val pending = appOpenPending(context, RECUR_ID)
        val prefix = if (ruleNames.isNotBlank()) "$ruleNames · " else ""
        showProtoPush(
            context, RECUR_ID, CHANNEL_REMINDER, ACCENT_TEAL,
            title = "오늘 정기 문자 보낼 고객 ${count}명",
            msg = "${prefix}오늘 ${count}명 · {고객명} 자동 채움 · 보내기 전에 한 번 봐주세요",
            contentIntent = pending,
            actions = listOf(PushAction("검토하고 보내기", pending))
        )
    }

    // 프로토 PUSH accent 색 — 종류별 (var PUSH 의 accent).
    private val ACCENT_BLUE = 0xFF3182F6.toInt()
    private val ACCENT_GREEN = 0xFF16C172.toInt()
    private val ACCENT_PURPLE = 0xFF7C5CFC.toInt()
    private val ACCENT_AMBER = 0xFFF6A609.toInt()
    private val ACCENT_PINK = 0xFFF0436A.toInt()
    private val ACCENT_TEAL = 0xFF0E9E90.toInt()

    /** 프로토 PUSH 알림의 액션 버튼. */
    data class PushAction(val label: String, val intent: PendingIntent)

    /**
     * 프로토 PUSH 형식 공통 알림 빌더 — 모든 알림을 한 형식으로 통일.
     *   accent 색(small-icon 틴트+앱명) + 이모지 제목 + 정보형 본문(+선택 note) + 액션 버튼.
     */
    fun showProtoPush(
        context: Context,
        id: Int,
        channelId: String,
        accent: Int,
        title: String,
        msg: String,
        note: String? = null,
        contentIntent: PendingIntent? = null,
        actions: List<PushAction> = emptyList(),
        timeoutMs: Long? = null
    ) {
        val bigText = buildString {
            append(msg)
            if (!note.isNullOrBlank()) append("\n$note")
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(accent)
            .setColorized(true)
            .setContentTitle(title)
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            // 같은 알림 id(같은 shareId 등)를 FCM·폴링이 각각 띄워도 소리/진동은 한 번만.
            //   협업 초대가 2~3번 울리던 문제 fix (FCM + 앱루프폴링 + ReminderWorker폴링이 같은 건을 재게시).
            .setOnlyAlertOnce(true)
        contentIntent?.let { builder.setContentIntent(it) }
        timeoutMs?.let { builder.setTimeoutAfter(it) }
        actions.forEach { builder.addAction(R.drawable.ic_notification, it.label, it.intent) }
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) { /* POST_NOTIFICATIONS 없음 — 무시 */ }
    }

    /**
     * MMS(사진) 수신 실패 알림 (2026-06-17 사장님).
     *   RING-GO 가 기본 문자앱일 때 통신사 MMS 다운로드가 실패하면(klinker auto-APN 한계) 사진이
     *   조용히 사라지던 문제 → 이제는 알림으로 알려 사장님이 대처(삼성 메시지 확인/기본앱 전환)하게.
     *   ⚠️ 근본 해결 = 삼성 메시지를 기본 문자앱으로 두기(그 사진은 RING-GO 가 그대로 읽음).
     */
    fun showMmsReceiveFailed(context: Context, senderHint: String?) {
        val who = senderHint?.takeIf { it.isNotBlank() }?.let { "$it 님" } ?: "고객"
        showProtoPush(
            context = context,
            id = MMS_FAIL_ID,
            channelId = CHANNEL_INCOMING_SMS,
            accent = ACCENT_AMBER,
            title = "📷 사진을 못 받았어요",
            msg = "${who}이 보낸 사진(MMS)을 받지 못했어요.",
            note = "삼성 메시지 앱을 '기본 문자앱'으로 두면 사진이 잘 들어와요. 시공막내가 그 사진을 그대로 보여줘요."
        )
    }

    /**
     * 고객이 시공접수서를 작성·제출했을 때 알림 — 프로토 PUSH.quote 형식 1:1.
     */
    fun showIntakeSubmitted(
        context: Context,
        token: String,
        phone: String,
        name: String,
        address: String,
        dateLabel: String? = null,
        totalManwon: Int = 0
    ) {
        val notifId = INTAKE_ID_OFFSET + (token.hashCode() and 0x7FFFFF)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, phone)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 프로토 PUSH.quote.msg 구조: "{이름}님이 ... · 시공 희망 {날짜} · {금액}만원"
        val msg = buildString {
            append("${name}님이 접수서를 작성했어요")
            if (!dateLabel.isNullOrBlank()) append(" · 시공 희망 $dateLabel")
            if (totalManwon > 0) append(" · ${totalManwon}만원")
        }
        showProtoPush(
            context, notifId, CHANNEL_INTAKE, ACCENT_PURPLE,
            title = "시공접수서 회신 도착 🎉",
            msg = msg,
            note = "📍 $address\n주소·시공일이 고객 카드에 자동 반영됐어요.",
            contentIntent = pending,
            actions = listOf(PushAction("일정 확인", pending))
        )
    }

    /** 같은 번호의 알림은 같은 ID 로 update — 새 메시지 도착 시 같은 자리 갱신. */
    fun smsNotificationId(phone: String): Int =
        SMS_ID_OFFSET + (phone.filter { it.isDigit() }.takeLast(8).hashCode() and 0x7FFFFFF)

    /**
     * 갤메시지 대체 풍부한 SMS 수신 알림. Step 1 — 기본 표시.
     *   - 헤더: 이름(있으면) 또는 포맷팅된 번호 + 카테고리
     *   - 본문: BigText 확장형
     *   - 탭 = ChatScreen 진입
     *   - 액션은 후속 Step 에서 (RemoteInput / AI 추천 답변 / 전화)
     */
    fun showIncomingSms(
        context: Context,
        phone: String,
        displayName: String?,
        body: String,
        receivedAtMs: Long,
        categoryLabel: String? = null,
        isNewCustomer: Boolean = false
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, phone)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            phone.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 프로토 PUSH.sms 제목 = "{이름/번호} · 새 문자". (카테고리·🔖 이모지 안 붙임 — OneUI 에서 이모지가
        //   엉뚱하게 렌더되고 프로토와도 달라서 제거. 2026-06-03 사장님 지적.)
        val title = buildString {
            append(displayName?.takeIf { it.isNotBlank() } ?: formatPhone(phone))
            append(" · 새 문자")
        }

        // 빠른 답장 RemoteInput — 사장님이 알림창에서 직접 타이핑 → SmsManager 발송.
        //   AI 추천이 안 맞을 때 fallback. 추천 슬롯이 다 차면 빠짐 (본체 탭 → ChatScreen).
        val remoteInput = androidx.core.app.RemoteInput.Builder(SmsReplyReceiver.KEY_REPLY_TEXT)
            .setLabel("답장 보내기")
            .build()
        val replyIntent = Intent(context, SmsReplyReceiver::class.java).apply {
            action = SmsReplyReceiver.ACTION_REPLY
            putExtra(SmsReplyReceiver.EXTRA_PHONE, phone)
        }
        val replyPending = PendingIntent.getBroadcast(
            context,
            smsNotificationId(phone),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "💬 직접 답장",
            replyPending
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        // 알림은 '받은 메시지'만 깔끔하게. AI 추천 답변은 알림에 안 넣고 탭해서 들어간 문자방에서 본다.
        //   (2026-06-15 사장님: 알림창엔 추천이 안 보이는 게 더 깔끔. 수신 즉시 카톡처럼 헤드업.)
        val bigText = body

        val smsChannel = if (isNewCustomer) CHANNEL_INCOMING_SMS_NEW else CHANNEL_INCOMING_SMS
        val builder = NotificationCompat.Builder(context, smsChannel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.take(60))
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setColor(NOTIFICATION_BG_COLOR)
            .setColorized(true)
            .setWhen(receivedAtMs)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openPending)
            // 카톡처럼 즉시 헤드업 — 채널이 IMPORTANCE_HIGH 라도 priority 명시로 더 일관되게.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // 알림엔 추천 칩 없음(깔끔). 빠른 답장(RemoteInput) 하나만 — 추천은 탭해서 문자방에서.
        builder.addAction(replyAction)

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(smsNotificationId(phone), builder.build())
        }
    }

    /** "010-1234-5678" 식 포맷 — 알림 제목용 단순 포맷터. */
    private fun formatPhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when (digits.length) {
            11 -> "${digits.substring(0,3)}-${digits.substring(3,7)}-${digits.substring(7)}"
            10 -> "${digits.substring(0,3)}-${digits.substring(3,6)}-${digits.substring(6)}"
            else -> raw
        }
    }

    /**
     * "조용한" 후속 안내 알림 — 2번째 이후 통화이고 사장님이 아직 답 안 보낸 경우.
     * 정책 (사장님 결정):
     *  - 헤드업 X (배너 안 뜸), 사운드 X
     *  - 알림함에만 쌓여서 사장님이 의식적으로 봤을 때 처리 가능
     *  - 탭하면 ChatScreen 으로 직진 (메인 대화 채널)
     *
     * 같은 번호의 같은 통화에 대해 중복 알림 방지를 위해 unique id 는 callRecordId 기반.
     */
    fun showQuietFollowUpNotification(
        context: Context,
        callRecordId: Long?,
        phoneNumber: String,
        isMissed: Boolean,
        customerId: Long? = null
    ) {
        val notifId = (callRecordId?.toInt()?.and(0x7FFFFFFF) ?: 0) + QUIET_ID_OFFSET
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, phoneNumber)
            customerId?.let { putExtra(MainActivity.EXTRA_CUSTOMER_ID, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isMissed) "📵 답장 안 한 손님 부재중 통화" else "📞 답장 안 한 손님 재통화"
        val text = "$phoneNumber · 메시지 확인하기"

        val builder = NotificationCompat.Builder(context, CHANNEL_FOLLOW_UP_QUIET)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NOTIFICATION_BG_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(if (isMissed) NotificationCompat.CATEGORY_MISSED_CALL else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) { /* POST_NOTIFICATIONS 없음 — 무시 */ }
    }

    /**
     * 통화 요약이 준비되면 잠깐 떴다 사라지는 알림 (2026-06-16 사장님).
     *   "고객과의 통화 내용을 요약했어요!" 톤 — 앱 들어가서 확인하란 식. setTimeoutAfter 로 잠깐 뒤 자동 소멸.
     *   통화 끝 후속 카드/"RING-GO 캐치!" 알림(제거됨) 대신, 요약이 진짜 다 됐을 때만 가볍게 알림. 탭 → 그 번호 채팅방.
     *   자동 통화요약(통화 끝→워커) 경로에서만 호출 — 수동 "이 통화 요약하기"/백필엔 안 뜸.
     */
    fun showSummaryReadyNotification(
        context: Context,
        phoneNumber: String,
        displayName: String? = null,
        callRecordId: Long? = null,
        customerId: Long? = null
    ) {
        val notifId = (callRecordId?.toInt()?.and(0x7FFFFFFF) ?: 0) + SUMMARY_READY_ID_OFFSET
        // customerId 가 있으면 같이 실어 보냄 → 채팅이 번호 포맷 매칭이 아니라 '그 고객'으로 정확히 열림.
        //   (2026-06-18 사장님 버그: 요약 알림 탭하면 관련없는 곳으로 이동 — 녹음 번호 포맷이 달라 빈/엉뚱한 대화가 열렸을 수 있음)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, phoneNumber)
            customerId?.takeIf { it > 0 }?.let { putExtra(MainActivity.EXTRA_CUSTOMER_ID, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 이름 없으면 번호 끝 4자리로 — "막내가 9114님의 통화 내용을 요약했어요!" (사장님 2026-06-18, 반갑게)
        val who = displayName?.takeIf { it.isNotBlank() }
            ?: phoneNumber.filter { it.isDigit() }.takeLast(4).takeIf { it.isNotBlank() }
            ?: "고객"
        val title = "✨ 막내가 ${who}님의 통화 내용을 요약했어요!"
        val text = "탭하면 바로 확인할 수 있어요"
        val builder = NotificationCompat.Builder(context, CHANNEL_FOLLOW_UP)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NOTIFICATION_BG_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(4000L)   // 잠깐(약 4초) 떴다 자동으로 사라짐
            .setContentIntent(pending)
        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) { /* POST_NOTIFICATIONS 없음 — 무시 */ }
    }

    /** 요약 완료 알림 ID offset — 다른 알림(AUTO_REPLY 5M / QUIET 7M)과 분리. */
    private const val SUMMARY_READY_ID_OFFSET = 8_000_000

    /** quiet 알림 ID 와 일반 후속 ID 충돌 방지 offset (AUTO_REPLY_ID_OFFSET 와도 분리). */
    private const val QUIET_ID_OFFSET = 7_000_000

    /** 해당 통화 후속 처리가 끝나면 호출 — 그 통화의 알림만 정리. */
    fun cancelFor(context: Context, callRecordId: Long?) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationIdFor(callRecordId))
        }
    }

    /**
     * callRecordId → notification ID 매핑.
     * Long → Int 변환은 .toInt() 로 하면 충돌 가능성이 작긴 하지만, 정수 오버플로 안전성을 위해
     * 음수가 되지 않도록 absoluteValue 처리. fallback 영역(<1000)과 충돌 방지 위해 +10000 offset.
     */
    private fun notificationIdFor(callRecordId: Long?): Int {
        if (callRecordId == null || callRecordId <= 0) return FALLBACK_NOTIFICATION_ID
        return (callRecordId.toInt() and 0x7FFFFFFF) + 10000
    }

    private fun autoReplyIdFor(callRecordId: Long): Int =
        (callRecordId.toInt() and 0x7FFFFFFF) + AUTO_REPLY_ID_OFFSET

    /** 자동 응답 대기 중 알림 (10초 카운트다운). 취소 액션 포함. */
    fun showAutoReplyPending(
        context: Context,
        callRecordId: Long,
        phoneNumber: String,
        countdownMs: Long
    ) {
        val id = autoReplyIdFor(callRecordId)
        val secs = (countdownMs / 1000).toInt()

        val cancelIntent = android.content.Intent(context, AutoReplyCancelReceiver::class.java).apply {
            action = AutoReplyCancelReceiver.ACTION_CANCEL
            putExtra(AutoReplyCancelReceiver.EXTRA_CALL_RECORD_ID, callRecordId)
        }
        val cancelPending = PendingIntent.getBroadcast(
            context, id, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_AUTO_REPLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NOTIFICATION_BG_COLOR)
            .setColorized(true)
            .setContentTitle("10초 뒤 자동문자 보낼게요")
            .setContentText("$phoneNumber · 취소하지 않으면 자동 발송")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$phoneNumber 에게 설정해둔 자동문자를 ${secs}초 뒤 보냅니다. 보내지 않으려면 '취소'를 누르세요."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis() + countdownMs)
            .setChronometerCountDown(true)
            .addAction(0, "취소", cancelPending)

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) { /* POST_NOTIFICATIONS 없음 — 무시 */ }
    }

    fun showAutoReplySent(context: Context, callRecordId: Long, phoneNumber: String) {
        // 프로토 PUSH.missed (초록) — 부재중 → 막내가 대신 답장.
        showProtoPush(
            context, autoReplyIdFor(callRecordId), CHANNEL_AUTO_REPLY, ACCENT_GREEN,
            title = "부재중 전화 — 막내가 대신 답장했어요",
            msg = "$phoneNumber 님께 인사 + 상호 안내를 자동으로 보냈어요.",
            note = "전화 못 받아도 놓치지 않았어요.",
            timeoutMs = 8_000L
        )
    }

    fun showAutoReplyCancelled(context: Context, callRecordId: Long) {
        showProtoPush(
            context, autoReplyIdFor(callRecordId), CHANNEL_AUTO_REPLY, ACCENT_AMBER,
            title = "자동 응답 취소됨",
            msg = "문자는 발송되지 않았어요.",
            timeoutMs = 5_000L
        )
    }

    fun showAutoReplyFailed(context: Context, callRecordId: Long, phoneNumber: String) {
        showProtoPush(
            context, autoReplyIdFor(callRecordId), CHANNEL_AUTO_REPLY, ACCENT_PINK,
            title = "⚠️ 자동 응답 발송 실패",
            msg = "$phoneNumber — 수동으로 다시 보내주세요."
        )
    }
}
