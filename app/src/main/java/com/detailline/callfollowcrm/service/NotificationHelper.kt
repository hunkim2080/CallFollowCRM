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
    /** 2026-05-25 사장님 결정 — RING-GO 가 갤메시지보다 더 좋은 알림창. 풍부한 정보 + AI 추천 답변. */
    private const val CHANNEL_INCOMING_SMS = "incoming_sms"
    /** 고객이 시공접수서를 작성·제출했을 때 알림. */
    private const val CHANNEL_INTAKE = "intake_submitted"
    private const val INTAKE_ID_OFFSET = 8_000_000
    /** SMS 알림 ID = 발신번호 hash + offset. 같은 번호 새 SMS = 같은 알림 update. */
    private const val SMS_ID_OFFSET = 10_000_000

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
            // 신규 SMS 수신 — 갤메시지 대체 알림. 본문 + AI 추천 답변 + 빠른 답장 RemoteInput.
            if (manager.getNotificationChannel(CHANNEL_INCOMING_SMS) == null) {
                val channel = NotificationChannel(
                    CHANNEL_INCOMING_SMS,
                    "📩 새 문자",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "고객 SMS 가 오면 AI 추천 답변과 함께 표시 — 갤메시지 알림은 끄고 사용하세요"
                    setShowBadge(true)
                }
                manager.createNotificationChannel(channel)
            }
            if (manager.getNotificationChannel(CHANNEL_INTAKE) == null) {
                val channel = NotificationChannel(
                    CHANNEL_INTAKE, "📋 접수서 작성됨", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "고객이 시공접수서를 작성하면 알려줘요"
                    setShowBadge(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /** 프로토 PUSH.quote accent — 보라(전환 알림). small-icon 틴트 + 앱명 accent. */
    private val INTAKE_ACCENT_PURPLE = 0xFF7C5CFC.toInt()

    /**
     * 고객이 시공접수서를 작성·제출했을 때 알림 — 프로토 PUSH.quote 형식 1:1.
     *   제목(이모지) + 정보형 본문(희망일·금액) + 액션 버튼. 탭/버튼 = 그 고객 채팅.
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
        val bigText = buildString {
            append(msg)
            append("\n📍 $address")
            append("\n\n주소·시공일이 고객 카드에 자동 반영됐어요. 탭해서 확인하세요.")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_INTAKE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(INTAKE_ACCENT_PURPLE)
            .setColorized(true)
            .setContentTitle("시공접수서 회신 도착 🎉")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            // 프로토 btns[primary] "1탭으로 일정 확정" — 임포트가 이미 일정 반영 → 탭하면 그 고객 확인.
            .addAction(R.drawable.ic_notification, "일정 확인", pending)
        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) { /* POST_NOTIFICATIONS 없음 — 무시 */ }
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
        /** AI 추천 답변 — null 이면 칩 X. 준비되면 같은 알림 ID 로 update 호출. */
        suggestions: List<String>? = null
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

        val title = buildString {
            append(displayName?.takeIf { it.isNotBlank() } ?: formatPhone(phone))
            if (!categoryLabel.isNullOrBlank()) append(" · 🔖 $categoryLabel")
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

        // BigText = 받은 메시지 + AI 추천 답변 전체 (액션 라벨이 짧아 잘려서, 본문에서 풀로 노출).
        //   2026-05-26 사장님 보고 fix:
        //     - 액션 라벨 "✨ 신축 욕..." 처럼 짤려서 어떤 답변인지 모름
        //     - BigText 안에 답변 전체 + 번호 → 사장님이 펼쳐서 읽고 → 짧은 액션 "✨ 1번 보내기" 한 탭
        //   suggestions == null  → 첫 알림, polling 시작 전 ("준비 중..." 진행감)
        //   suggestions == []    → polling 끝났는데 응답 없음 ("서버 응답 없음 — 직접 답장")
        //   suggestions == [...] → 정상 답변 표시
        val bigText = buildString {
            append(body)
            when {
                suggestions == null -> {
                    append("\n\n✨ AI 추천 답변 준비 중...")
                }
                suggestions.isEmpty() -> {
                    // 2026-05-28 사장님 보고 fix: 2분째 "준비 중..." 멈춤 차단.
                    //   서버 죽음/Tailscale 끊김 가능성. 사장님이 [💬 직접 답장] 으로 즉시 답할 수 있게.
                    append("\n\n🔌 AI 서버 응답 없음 — 직접 답장하거나 RING-GO 에서 확인하세요")
                }
                else -> {
                    // 2026-05-26 사장님 보고 fix:
                    //   - 안내 문구 "(아래 ✨ 버튼 = 즉시 발송)" 제거 — 액션 라벨로 충분.
                    //   - 각 답변 사이 빈 줄 (\n\n) 추가 → 1·2·3 경계 명확.
                    append("\n\n✨ AI 추천 답변")
                    suggestions.take(3).forEachIndexed { idx, sug ->
                        val num = listOf("1️⃣", "2️⃣", "3️⃣")[idx]
                        append("\n\n$num $sug")
                    }
                }
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_INCOMING_SMS)
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
            // 같은 알림 id 의 후속 update (AI 추천 채워질 때) 가 소리/진동 두 번 울리지 않게.
            //   첫 알림만 사장님께 알리고, AI 추천은 조용히 보강.
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)

        // 액션 우선순위: AI 추천 (한 탭 발송) → 직접 답장 (RemoteInput).
        //   전화는 본체 탭 → ChatScreen 에서 가능 (알림 슬롯 낭비 X).
        //   Android 알림 액션 최대 3개 — 추천 3개면 답장 빠짐, 추천 2개면 답장 포함.
        val notifId = smsNotificationId(phone)
        val sugList = suggestions?.take(3).orEmpty()
        sugList.forEachIndexed { idx, sug ->
            val sendIntent = Intent(context, SmsReplyReceiver::class.java).apply {
                action = SmsReplyReceiver.ACTION_SEND_SUGGESTION
                putExtra(SmsReplyReceiver.EXTRA_PHONE, phone)
                putExtra(SmsReplyReceiver.EXTRA_SUGGESTION_BODY, sug)
            }
            val sendPending = PendingIntent.getBroadcast(
                context,
                // 같은 알림 안에서 칩별로 unique requestCode.
                notifId * 10 + idx + 1,
                sendIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // 라벨은 짧게 "✨ 1번 보내기" — 답변 본문은 BigText 에서 미리 읽기.
            //   2026-05-26 사장님 보고 fix: 라벨에 본문 넣으면 잘려서 어떤 답변인지 모름.
            val label = "✨ ${idx + 1}번 보내기"
            builder.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_notification, label, sendPending)
                    .setShowsUserInterface(false)
                    .build()
            )
        }
        // 액션 슬롯 남으면 직접 답장 추가. (추천 3개면 빠짐 — 본체 탭으로 직접 타이핑 유도.)
        if (sugList.size < 3) {
            builder.addAction(replyAction)
        }

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
