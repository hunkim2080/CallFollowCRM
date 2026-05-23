package com.detailline.callfollowcrm.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.detailline.callfollowcrm.MainActivity
import com.detailline.callfollowcrm.R

object NotificationHelper {

    private const val CHANNEL_FOLLOW_UP = "call_follow_up"
    private const val CHANNEL_FOLLOW_UP_QUIET = "call_follow_up_quiet"
    private const val CHANNEL_AUTO_REPLY = "auto_reply"

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
            if (manager.getNotificationChannel(CHANNEL_FOLLOW_UP) == null) {
                val channel = NotificationChannel(
                    CHANNEL_FOLLOW_UP,
                    "통화 후속 안내",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "방금 끝난 통화에 대한 후속 문자 안내"
                }
                manager.createNotificationChannel(channel)
            }
            if (manager.getNotificationChannel(CHANNEL_AUTO_REPLY) == null) {
                val channel = NotificationChannel(
                    CHANNEL_AUTO_REPLY,
                    "자동 응답 문자",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "처음 연락온 고객 자동 응답 발송 안내 (취소 가능)"
                }
                manager.createNotificationChannel(channel)
            }
            // quiet 후속 안내 — 2번째 이후 통화이지만 사장님이 아직 답장/분류 안 한 경우.
            // 배너 X (heads-up 없음), 사운드 X, 알림함에만 조용히 쌓임.
            if (manager.getNotificationChannel(CHANNEL_FOLLOW_UP_QUIET) == null) {
                val channel = NotificationChannel(
                    CHANNEL_FOLLOW_UP_QUIET,
                    "후속 미처리 안내",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "답장 못 한 손님이 또 연락 왔을 때 조용히 알려줘요"
                    setShowBadge(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * 통화 종료 후 알림을 표시한다.
     * - POST_NOTIFICATIONS 권한이 없으면 조용히 무시한다(앱이 죽지 않게).
     * - callRecordId 가 있으면 그걸 기반으로 unique notification ID 를 만들어 같은 ID 알림을
     *   덮어쓰지 않는다. 연속 통화에서도 첫 통화 알림이 살아남도록.
     */
    /**
     * 후속 처리 안내 알림.
     * @param quickActions (라벨, 템플릿 id) 쌍 최대 3개. 라벨이 액션 버튼에 표시됨.
     *                     템플릿 id 는 FollowUp 진입 시 자동 선택용 EXTRA 로 전달.
     */
    fun showCallEndedNotification(
        context: Context,
        phoneNumber: String?,
        callRecordId: Long? = null,
        isMissed: Boolean = false,
        quickActions: List<Pair<String, Long>> = emptyList()
    ) {
        val safePhone = phoneNumber?.takeIf { it.isNotBlank() } ?: ""
        val title = if (isMissed)
            "RING-GO 캐치! · 부재중"
        else
            "RING-GO 캐치!"
        val bodyHeader = if (safePhone.isBlank())
            "번호 미확인 · 수동으로 처리할 수 있어요"
        else
            "$safePhone · 고객에게 보낼 빠른 메시지를 선택하세요"
        val text = bodyHeader

        val notificationId = notificationIdFor(callRecordId)

        fun followUpPending(templateId: Long?): PendingIntent {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_FOLLOW_UP
                putExtra(MainActivity.EXTRA_PHONE_NUMBER, safePhone)
                callRecordId?.let { putExtra(MainActivity.EXTRA_CALL_RECORD_ID, it) }
                templateId?.takeIf { it > 0 }?.let { putExtra(MainActivity.EXTRA_TEMPLATE_ID, it) }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            // 같은 requestCode + 다른 extras 면 시스템이 첫 PendingIntent extras 로 굳어짐 (extras 무시).
            // 그래서 requestCode 에 templateId 도 섞어 unique 화. -1 은 0 으로 매핑.
            val tplPart = (templateId?.toInt() ?: 0) and 0xFFFF
            val req = (notificationId shl 16) or tplPart
            return PendingIntent.getActivity(
                context, req, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        // 알림 본체 탭 시 — 템플릿 미지정으로 열기.
        val pending = followUpPending(null)

        // small icon 은 안드로이드 정책상 흰색+알파 단색만 허용된다.
        // 알림 펼침 영역에서 컬러 앱 아이콘을 함께 보여주려고 large icon 을 같이 세팅.
        val largeIcon = runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull()

        // OneUI 의 setColorized 무시 회피용 — 커스텀 RemoteViews 로 배경을 직접 칠해야
        // 파스텔 블루가 실제로 보인다. DecoratedCustomViewStyle 로 시스템 헤더/액션은 유지.
        val bigText = if (quickActions.isEmpty())
            "$text\n(빠른 액션 설정 → 설정 → 후속 빠른 액션 템플릿)"
        else
            "$text\n빠른 액션: ${quickActions.joinToString(" / ") { it.first }}"
        val collapsedView = RemoteViews(context.packageName, R.layout.notification_ringo_collapsed).apply {
            setTextViewText(R.id.notification_title, title)
            setTextViewText(R.id.notification_text, text)
        }
        val expandedView = RemoteViews(context.packageName, R.layout.notification_ringo_expanded).apply {
            setTextViewText(R.id.notification_title, title)
            setTextViewText(R.id.notification_text, bigText)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_FOLLOW_UP)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NOTIFICATION_BG_COLOR)
            .apply { largeIcon?.let { setLargeIcon(it) } }
            // setContentTitle/Text 는 접근성/구버전 fallback. 보이는 표시는 customView 가 담당.
            .setContentTitle(title)
            .setContentText(text)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isMissed) NotificationCompat.CATEGORY_MISSED_CALL else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            // 액션 버튼은 사장님이 Settings 에서 지정한 템플릿 최대 3개.
            // 각 액션은 자기 템플릿 ID 를 EXTRA 로 들고 FollowUp 으로 진입 → 자동 선택.
            .apply {
                quickActions.take(3).forEach { (label, tplId) ->
                    addAction(0, label, followUpPending(tplId))
                }
            }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Android 13+에서 권한 없으면 발생 가능. 무시.
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
            .setContentTitle("자동 응답 문자 보낼게요")
            .setContentText("$phoneNumber · ${secs}초 후 발송 (취소 가능)")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$phoneNumber 에게 ${secs}초 후 첫 응대 문자를 자동 발송합니다. 잘못 보낼 것 같으면 아래 '취소' 누르세요."
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
        val id = autoReplyIdFor(callRecordId)
        val builder = NotificationCompat.Builder(context, CHANNEL_AUTO_REPLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NOTIFICATION_BG_COLOR)
            .setColorized(true)
            .setContentTitle("RING-GO! $phoneNumber 캐치 완료!")
            .setContentText("메시지가 정상적으로 전송됐어요")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "RING-GO! $phoneNumber\n캐치 완료! 메시지가 정상적으로 전송됐어요"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(8_000L)
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) { /* 무시 */ }
    }

    fun showAutoReplyCancelled(context: Context, callRecordId: Long) {
        val id = autoReplyIdFor(callRecordId)
        val builder = NotificationCompat.Builder(context, CHANNEL_AUTO_REPLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NOTIFICATION_BG_COLOR)
            .setColorized(true)
            .setContentTitle("자동 응답 취소됨")
            .setContentText("문자는 발송되지 않았어요")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(5_000L)
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) { /* 무시 */ }
    }

    fun showAutoReplyFailed(context: Context, callRecordId: Long, phoneNumber: String) {
        val id = autoReplyIdFor(callRecordId)
        val builder = NotificationCompat.Builder(context, CHANNEL_AUTO_REPLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NOTIFICATION_BG_COLOR)
            .setColorized(true)
            .setContentTitle("⚠ 자동 응답 발송 실패")
            .setContentText("$phoneNumber — 수동으로 다시 보내주세요")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) { /* 무시 */ }
    }
}
