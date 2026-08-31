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
    /** 문자함(고객 아님) 새 문자 — 조용히(소리·헤드업 X) 알림함에만 + 배지. (2026-07-11 사장님) */
    private const val CHANNEL_GENERAL_SMS = "general_sms_box"
    /** 통화 후 문자 보내기 — 새 번호 통화 끝나면 "문자 보낼까요?" + 템플릿 선택. (2026-07-12 사장님) */
    private const val CHANNEL_POSTCALL = "postcall_picker"
    private const val POSTCALL_ID_BASE = 9_300_000
    /** 고객이 시공접수서를 작성·제출했을 때 알림. */
    private const val CHANNEL_INTAKE = "intake_submitted_2"
    private const val INTAKE_ID_OFFSET = 8_000_000
    /** 시간 기반 리마인더(시공 D-1·잔금 미수·마감 브리핑). */
    private const val CHANNEL_REMINDER = "reminder_2"
    private const val D1_ID_OFFSET = 9_000_000
    private const val SETTLE_ID_OFFSET = 9_500_000
    private const val AS_ID_OFFSET = 9_250_000            // A/S 그날 알림 (DB v43)
    private const val BRIEF_ID = 9_700_000
    private const val RECUR_ID = 9_800_000
    private const val ARRIVAL_ID_OFFSET = 9_900_000
    private const val DEPART_ID_OFFSET = 9_600_000
    private const val COLLAB_ID_OFFSET = 9_400_000
    private const val COLLAB_INVITE_ID_OFFSET = 9_450_000
    /** 본폰 미러 v2 — 새 공유 신청 알림(한 스레드, 갱신). (2026-07-14) */
    private const val MIRROR_SHARE_ID = 9_910_000
    /** 협업 현장 새 댓글 알림 — site_id hash 기준(현장당 한 스레드 알림, 새 댓글이면 update). (2026-07-02) */
    private const val COLLAB_COMMENT_ID_OFFSET = 9_350_000
    /** 협업 현장 새 사진 알림 — 상대가 현장 증거사진 올리면. (2026-07-02) */
    private const val COLLAB_PHOTO_ID_OFFSET = 9_360_000
    /** 박람회 시공자 배정 알림 — 방(room_id) hash 기준(방당 한 스레드, 배분 여러 건이면 update). (2026-07-27) */
    private const val EXPO_ASSIGN_ID_OFFSET = 9_700_000
    /** SMS 알림 ID = 발신번호 hash + offset. 같은 번호 새 SMS = 같은 알림 update. */
    private const val SMS_ID_OFFSET = 10_000_000
    private const val MMS_FAIL_ID = 9_300_000
    /** 오늘의 현장 상시 알림 — 현장에서 주소(동/호) 계속 확인용. 무음·상단고정(ongoing), 하루 1건. (2026-07-10 사장님) */
    private const val CHANNEL_TODAY_SITE = "today_site"
    private const val TODAY_SITE_ID = 9_200_000
    // 전용 소리 슬롯 채널 (2026-07-13 사장님) — 통화요약/협업수락/협업거절 각각 소리 고르게. 새 채널 id 라 첫 생성부터 소리 적용.
    private const val CHANNEL_CALL_SUMMARY = "call_summary_snd"
    private const val CHANNEL_COLLAB_ACCEPTED = "collab_accepted_snd"
    private const val CHANNEL_COLLAB_DECLINED = "collab_declined_snd"
    /**
     * 협업 현장 — 상황마다 소리를 따로 고르게 세분화. (2026-07-15 사장님 지시)
     *   처음엔 "협업 현장 소식" 한 칸이었는데("댓글인데 리마인더 소리" fix), 사장님이 8가지 상황을
     *   구분해 듣고 싶다고 해서 7칸으로 쪼갬. 8번째(내가 3km 도착 → 막내가 대신 알림)는 성격이
     *   "막내가 나 대신 해줬다" = 자동응답과 같아서 **CHANNEL_AUTO_REPLY 재사용**(새 소리 안 만듦).
     *   수락/거절은 위 전용 채널 그대로.
     */
    private const val CHANNEL_COLLAB_COMMENT = "collab_comment_snd"      // 댓글 + 사진
    private const val CHANNEL_COLLAB_INVITE = "collab_invite_snd"        // 협업 요청 옴
    private const val CHANNEL_COLLAB_DEPARTED = "collab_departed_snd"    // 상대 출발
    private const val CHANNEL_COLLAB_ARRIVED = "collab_arrived_snd"      // 상대 도착
    private const val CHANNEL_COLLAB_COMPLETED = "collab_completed_snd"  // 작업 완료
    private const val CHANNEL_COLLAB_ENDED = "collab_ended_snd"          // 협업 해제
    private const val CHANNEL_COLLAB_PAID = "collab_paid_snd"            // 입금 완료
    /**
     * 리마인더에서 분리한 전용 소리 채널. (2026-07-15 사장님 지시)
     *   "시공·정산 리마인더" 한 칸이 D-1·잔금·5km도착·브리핑·정기문자·팀원·본폰공유신청 7가지를 다 울려서
     *   무슨 일인지 소리로 구분이 안 됐다 → 사장님이 고른 3가지를 먼저 분리.
     *   (팀원 소식 분리는 "사장 버전 끝내고" — 사장님 보류 지시.)
     */
    private const val CHANNEL_INSTALL_D1 = "install_d1_snd"              // 내일 시공 안내
    private const val CHANNEL_DAILY_BRIEF = "daily_brief_snd"            // 마감 브리핑
    private const val CHANNEL_RECURRING = "recurring_sms_snd"            // 정기문자

    // 알림 배너 배경 — 파스텔 블루 (Material Blue 100).
    // setColorized(true) 와 함께 쓰면 OneUI 등 일부 시스템이 배너 전체 배경으로 사용.
    // 시스템이 colorized 를 무시해도 setColor 는 항상 small-icon 틴트 + 앱명 accent 로 동작.
    private val NOTIFICATION_BG_COLOR = 0xFFBBDEFB.toInt()
    /** callRecordId 없을 때만 쓰는 fallback. 정상 흐름은 항상 callRecordId 기반 unique ID. */
    private const val FALLBACK_NOTIFICATION_ID = 1001
    /** AutoReply 알림은 callRecordId 기반 + offset 으로 후속 알림과 분리. */
    private const val AUTO_REPLY_ID_OFFSET = 5_000_000

    // ══════════════ 알림 종류별 소리 (더보기 → 알림 소리, 2026-07-10 사장님) ══════════════
    /**
     * 소리를 고를 수 있는 알림 슬롯. key=prefs 저장 키, label=설정화면 목록용, defaultRes=기본 raw 리소스명,
     * channelName/channelDesc = 안드로이드 시스템 알림설정에 보이는 이름·설명.
     */
    data class SoundSlot(
        val key: String,
        val label: String,
        val defaultRes: String,
        val channelName: String,
        val channelDesc: String
    )
    // ⚠️ defaultRes = **사장님이 직접 고른 값**(2026-07-15, 사장님 폰 설정을 그대로 읽어서 박음).
    //    "베타테스터가 받으면 처음부터 이 상태로" — 사장님 지시. 새로 깔면 아무것도 안 골라도 이 소리가 난다.
    //    (이미 쓰던 사람은 저장된 선택이 우선이라 영향 없음: notificationSound(key, default) 는 저장값 우선.)
    //    바꿀 땐 res/raw 에 파일이 실제로 있어야 함 — SoundSlotDefaultsTest 가 오타·누락을 잡는다.
    val SOUND_SLOTS = listOf(
        SoundSlot("new_inquiry", "신규 문의 문자", "sound_new_inquiry_2",
            "📩 신규 문의", "처음 연락온 신규 고객 문자 — 바로 답장해요"),
        SoundSlot("reply", "고객 답장 문자", "sound_reply_sabu",
            "📩 새 문자", "고객 SMS 가 오면 AI 추천 답변과 함께 표시 — 갤메시지 알림은 끄고 사용하세요"),
        SoundSlot("auto_reply", "자동 응답 발송", "sound_auto_reply",
            "자동 응답 문자", "처음 연락온 고객 자동 응답 발송 안내 (취소 가능)"),
        SoundSlot("intake", "접수서 작성 완료", "sound_intake_arrived",
            "📋 접수서 작성됨", "고객이 시공접수서를 작성하면 알려줘요"),
        // 남은 리마인더 = 잔금 미수 · 현장 5km 도착 안내 · 본폰 일정공유 신청 · 팀원 소식(분리 예정).
        //   기본값 sound_auto_reply 는 사장님이 직접 고른 값 그대로.
        SoundSlot("reminder", "시공·정산 리마인더", "sound_auto_reply",
            "⏰ 시공·정산 리마인더", "잔금 미수·현장 도착 안내 등을 제때 알려줘요"),
        // ── 리마인더에서 분리 (2026-07-15 사장님). 내일시공/마감브리핑은 사장님 전용 소리(내일시공은 2안).
        //    정기문자는 아직 소리 없음 → 기본 리마인더 소리 유지(사장님 "정기문자 빼고").
        SoundSlot("install_d1", "내일 시공 안내", "sound_install_d1",
            "📅 내일 시공 안내", "시공 하루 전, 고객에게 안내 문자를 보낼지 알려줘요"),
        SoundSlot("daily_brief", "마감 브리핑", "sound_daily_brief",
            "🌙 마감 브리핑", "저녁에 오늘 하루를 정리해서 알려줘요"),
        SoundSlot("recurring", "정기문자", "sound_reminder",
            "🔁 정기문자", "정기문자 보낼 때가 되면 알려줘요"),
        SoundSlot("call_summary", "통화 요약 완료", "sound_call_summary_2",
            "✨ 통화 요약 완료", "통화 내용 요약이 준비되면 알려줘요"),
        SoundSlot("collab_accepted", "협업 수락", "sound_collab_accepted",
            "🤝 협업 수락", "상대 사장님이 협업을 수락하면 알려줘요"),
        SoundSlot("collab_declined", "협업 거절", "sound_collab_declined_ppaenji",
            "협업 거절", "상대 사장님이 협업을 거절하면 알려줘요"),
        // ── 협업 현장 세분화 (2026-07-15 사장님) — 사장님이 만든 전용 소리가 기본값.
        //    요청/출발/완료는 2안씩 만드셔서 기본은 첫 안, 나머지는 목록(SOUND_OPTIONS)에서 고르면 됨.
        SoundSlot("collab_comment", "협업 현장 댓글·사진", "sound_collab_comment",
            "💬 협업 현장 댓글·사진", "협업 사장님이 현장에 댓글을 달거나 사진을 올리면 알려줘요"),
        SoundSlot("collab_invite", "협업 요청 옴", "sound_collab_invite",
            "🤝 협업 요청", "다른 사장님이 나에게 협업을 요청하면 알려줘요"),
        SoundSlot("collab_departed", "협업 상대 출발", "sound_collab_departed",
            "🚗 협업 상대 출발", "협업 사장님이 현장으로 출발하면 알려줘요"),
        SoundSlot("collab_arrived", "협업 상대 도착", "sound_collab_arrived",
            "📍 협업 상대 도착", "협업 사장님이 현장에 도착(또는 거의 도착)하면 알려줘요"),
        SoundSlot("collab_completed", "협업 작업 완료", "sound_collab_completed",
            "✅ 협업 작업 완료", "협업 사장님이 현장 작업을 끝내면 알려줘요"),
        SoundSlot("collab_ended", "협업 해제", "sound_collab_ended",
            "협업 해제", "상대가 협업을 해제하면 알려줘요 (기록은 남아요)"),
        SoundSlot("collab_paid", "협업 입금 완료", "sound_collab_paid",
            "💰 협업 입금 완료", "협업 현장 정산 입금이 완료되면 알려줘요"),
    )
    /** 고를 수 있는 소리(값=raw 리소스명, "silent"=무음). */
    val SOUND_OPTIONS = listOf(
        // ── 신규 문의 ──
        "sound_new_inquiry" to "신규문의 (기본)",
        "sound_new_inquiry_2" to "신규문의 - 신규고객 문자 문의",
        "sound_new_inquiry_3" to "신규문의 - 신규고객 문의",
        "sound_new_inquiry_matjip" to "신규문의 - 시공맛집",
        "sound_new_inquiry_received" to "신규문의 - 신규문의 접수",
        "sound_new_inquiry_wow" to "신규문의 - 우와 신규다",
        // ── 고객 답장 ──
        "sound_reply" to "답장 (기본)",
        "sound_reply_sabu" to "답장 - 싸부",
        "sound_reply_ddallang" to "답장 - 딸랑딸랑",
        "sound_reply_hyungnim" to "답장 - 행님아",
        "sound_reply_customer" to "답장 - 손님답장이요",
        // ── 접수서 ──
        "sound_intake" to "접수서 (기본)",
        "sound_intake_arrived" to "접수서 도착",
        // ── 리마인더 · 자동응답 ──
        "sound_reminder" to "리마인더",
        "sound_install_d1" to "리마인더 - 내일시공",
        "sound_install_d1_1" to "리마인더 - 내일시공1",
        "sound_daily_brief" to "리마인더 - 마감브리핑",
        "sound_auto_reply" to "자동응답",
        // ── 통화 요약 ──
        "sound_call_summary" to "통화요약 완료",
        "sound_call_summary_1" to "통화요약 - 1",
        "sound_call_summary_2" to "통화요약 - 2",
        // ── 협업 수락/거절 ──
        "sound_collab_accepted" to "협업 수락",
        "sound_collab_accepted_alliance" to "협업수락 - 동맹체결",
        "sound_collab_declined" to "협업 거절",
        "sound_collab_declined_ppaenji" to "협업거절 - 뺀찌",
        "sound_collab_declined_sogeun" to "협업거절 - 소근소근",
        // ── 협업 현장 (2026-07-15 사장님이 새로 만든 10개 — 요청·출발·완료는 2안씩) ──
        "sound_collab_comment" to "협업 - 댓글사진",
        "sound_collab_invite" to "협업 - 요청",
        "sound_collab_invite_1" to "협업 - 요청1",
        "sound_collab_departed" to "협업 - 출발",
        "sound_collab_departed_1" to "협업 - 출발1",
        "sound_collab_arrived" to "협업 - 도착",
        "sound_collab_completed" to "협업 - 완료",
        "sound_collab_completed_1" to "협업 - 완료1",
        "sound_collab_ended" to "협업 - 해제",
        "sound_collab_paid" to "협업 - 입금완료",
        "silent" to "무음",
    )
    private val SLOT_CHANNEL = mapOf(
        "new_inquiry" to CHANNEL_INCOMING_SMS_NEW, "reply" to CHANNEL_INCOMING_SMS,
        "auto_reply" to CHANNEL_AUTO_REPLY, "intake" to CHANNEL_INTAKE, "reminder" to CHANNEL_REMINDER,
        "call_summary" to CHANNEL_CALL_SUMMARY,
        "collab_accepted" to CHANNEL_COLLAB_ACCEPTED, "collab_declined" to CHANNEL_COLLAB_DECLINED,
        "collab_comment" to CHANNEL_COLLAB_COMMENT, "collab_invite" to CHANNEL_COLLAB_INVITE,
        "collab_departed" to CHANNEL_COLLAB_DEPARTED, "collab_arrived" to CHANNEL_COLLAB_ARRIVED,
        "collab_completed" to CHANNEL_COLLAB_COMPLETED, "collab_ended" to CHANNEL_COLLAB_ENDED,
        "collab_paid" to CHANNEL_COLLAB_PAID,
        "install_d1" to CHANNEL_INSTALL_D1, "daily_brief" to CHANNEL_DAILY_BRIEF,
        "recurring" to CHANNEL_RECURRING,
    )

    /** slot 이 지금 울려야 할 raw 리소스 ID. 0 = 무음. 고른 소리가 없어졌으면 기본으로 폴백. */
    private fun soundResId(context: Context, key: String, defaultRes: String): Int {
        val prefs = prefsOf(context)
        val choice = prefs?.notificationSound(key, defaultRes) ?: defaultRes
        if (choice == "silent") return 0
        val id = context.resources.getIdentifier(choice, "raw", context.packageName)
        return if (id > 0) id else context.resources.getIdentifier(defaultRes, "raw", context.packageName)
    }

    /** slot 선택값(앱 prefs)을 소리 Uri 로. "silent"=null. */
    private fun chosenSound(context: Context, key: String, defaultRes: String): android.net.Uri? {
        val id = soundResId(context, key, defaultRes)
        if (id <= 0) return null
        return Uri.parse("android.resource://${context.packageName}/$id")
    }

    // ══════════════ 소리 슬롯 채널 — id 에 '소리 번호'를 박는다 (2026-07-15 사장님) ══════════════
    //
    // 안드로이드 제약 2가지가 겹쳐서 "고른 소리가 안 울린다 / 답장인데 협업 소리" 가 났다:
    //  (1) NotificationChannel 은 **만든 뒤 소리를 못 바꾼다.** 같은 id 로 지웠다 다시 만들면 옛 설정이 그대로 부활(공식 동작).
    //  (2) 채널이 저장하는 소리 URI 는 `android.resource://pkg/<숫자 리소스 ID>` 인데, raw 리소스 ID 는
    //      **알파벳순 자동 부여**라 소리 파일을 하나만 추가해도 그 뒤 번호가 전부 밀린다.
    //      채널은 앱 업데이트 후에도 살아남으므로 → 옛 채널이 든 옛 번호가 **다른 파일**을 가리키게 된다.
    //      실제 사고: 소리 12개 추가 → `sound_reply`(알파벳 뒤)의 옛 번호가 `sound_collab_*`(앞) 자리로 떨어져
    //      **"고객 답장 문자인데 협업 거절 소리"**. (2026-07-15 사장님)
    //
    // 해법: **채널 id = "${base}_s${지금 빌드의 소리 리소스 번호}"**.
    //   → 채널 id 와 그 안의 URI 번호가 **항상 한 몸**이라 어긋날 수가 없다(불변식).
    //   → 소리를 바꾸거나(번호 다름) 빌드에서 번호가 밀리면 **id 가 저절로 달라져 새 채널이 생기고**,
    //      그 순간 '지금 빌드의 올바른 번호'로 URI 가 박힌다. 별도 마이그레이션·버전 카운터가 필요 없다.
    //   → 예전 소리로 되돌리면 옛 `_s{번호}` 채널이 부활(un-delete)하는데, 그 채널의 URI 도 같은 번호라 정확하다.
    //   URI 형식은 지금까지 실제로 울리던 '숫자' 형식 그대로 유지(검증 안 된 이름 URI 로 바꾸지 않는다).

    private val prefsOf: (Context) -> com.detailline.callfollowcrm.data.preferences.AppPreferences? = { ctx ->
        (ctx.applicationContext as? com.detailline.callfollowcrm.CallFollowCrmApplication)?.container?.preferences
    }

    /** slot 의 현재 소리가 반영된 채널 id (없으면 생성). 소리 발사 직전 항상 이걸로 채널을 고른다. */
    fun channelForSlot(context: Context, slotKey: String): String {
        val base = SLOT_CHANNEL[slotKey] ?: return CHANNEL_REMINDER
        val slot = SOUND_SLOTS.firstOrNull { it.key == slotKey } ?: return base
        val id = "${base}_s${soundResId(context, slotKey, slot.defaultRes)}"   // 0 = 무음
        ensureSlotChannel(context, slotKey, id)
        return id
    }

    /**
     * 없어진 슬롯의 옛 채널 — 시스템 알림설정에 유령으로 남지 않게 정리.
     *   collab_news_snd: "협업 현장 소식" 한 칸이었다가 사장님 지시로 7칸으로 쪼개면서 폐기. (2026-07-15)
     */
    private val RETIRED_CHANNEL_BASES = listOf("collab_news_snd")

    private fun pruneRetiredChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val m = context.getSystemService(NotificationManager::class.java) ?: return
        m.notificationChannels.forEach { ch ->
            val cid = ch.id
            if (RETIRED_CHANNEL_BASES.any { cid == it || cid.startsWith("${it}_") }) {
                runCatching { m.deleteNotificationChannel(cid) }
            }
        }
    }

    /** 이 slot 의 옛 채널(숫자 URI 가 어긋난 base·_v·다른 _s) 정리 — 시스템 알림설정에 유령이 안 남게. */
    private fun pruneOldSlotChannels(context: Context, slotKey: String, keepId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val m = context.getSystemService(NotificationManager::class.java) ?: return
        val base = SLOT_CHANNEL[slotKey] ?: return
        m.notificationChannels.forEach { ch ->
            val cid = ch.id
            if (cid != keepId && (cid == base || cid.startsWith("${base}_"))) {
                runCatching { m.deleteNotificationChannel(cid) }
            }
        }
    }

    /** 넘어온 channelId 가 소리 슬롯의 base 면 → 현재 버전 채널로 치환. 아니면 그대로(비-슬롯 채널). */
    private fun resolveChannel(context: Context, channelId: String): String {
        // 방해금지 시간대면 소리·진동 없는 야간 채널로 몰아준다(알림은 그대로 오되 조용히). (2026-08-04 사장님)
        if (isQuietNow(context)) { ensureNightQuietChannel(context); return CHANNEL_NIGHT_QUIET }
        val slotKey = SLOT_CHANNEL.entries.firstOrNull { it.value == channelId }?.key ?: return channelId
        return channelForSlot(context, slotKey)
    }

    /** 지금이 사장님이 정한 방해금지 시간대인가. 꺼져있으면 false. 자정 넘김(예 22시~7시) 처리. */
    fun isQuietNow(context: Context): Boolean {
        val prefs = prefsOf(context) ?: return false
        if (!prefs.quietHoursEnabled) return false
        val start = prefs.quietStartHour
        val end = prefs.quietEndHour
        if (start == end) return false
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (start < end) h in start until end else (h >= start || h < end)
    }

    /** 야간 방해금지 채널(소리·진동·헤드업 없음, IMPORTANCE_LOW). 알림은 알림창엔 조용히 남는다. */
    const val CHANNEL_NIGHT_QUIET = "night_quiet"
    private fun ensureNightQuietChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val m = context.getSystemService(NotificationManager::class.java) ?: return
        if (m.getNotificationChannel(CHANNEL_NIGHT_QUIET) != null) return
        m.createNotificationChannel(
            NotificationChannel(CHANNEL_NIGHT_QUIET, "방해금지 시간(야간)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "방해금지 시간대에는 소리·진동 없이 조용히 알림이 와요."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }
        )
    }

    /** slot 채널 하나를 (없을 때만) 현재 고른 소리로 생성. */
    private fun ensureSlotChannel(context: Context, slotKey: String, channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val m = context.getSystemService(NotificationManager::class.java) ?: return
        if (m.getNotificationChannel(channelId) != null) return
        val slot = SOUND_SLOTS.firstOrNull { it.key == slotKey } ?: return
        val audioAttrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        val u = chosenSound(context, slotKey, slot.defaultRes)
        // 무음(소리 없음)을 고른 채널은 IMPORTANCE_LOW 로 만들어 헤드업 배너도 안 뜨게 — '무음=조용히' 기대와 일치. (2026-08-11 알림 감사)
        val importance = if (u == null) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
        m.createNotificationChannel(NotificationChannel(channelId, slot.channelName, importance).apply {
            description = slot.channelDesc
            if (u != null) setSound(u, audioAttrs) else setSound(null, null)
            setShowBadge(true)
        })
    }

    /**
     * 디버그 빌드 전용 — "어떤 알림이 어떤 소리로 잡혀있나"를 실제 채널에서 읽어 찍는다.
     *   소리 매칭 사고(2026-07-15)를 눈으로 확인하려고 추가. `adb logcat -s NTFSND` 로 확인.
     *   채널이 든 소리 URI 의 숫자를 **지금 빌드의 리소스 이름으로 되짚어** 찍으므로 어긋나면 바로 보인다.
     */
    private fun logSlotChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val m = context.getSystemService(NotificationManager::class.java) ?: return
        SOUND_SLOTS.forEach { slot ->
            val id = channelForSlot(context, slot.key)
            val ch = m.getNotificationChannel(id)
            val uri = ch?.sound
            val resName = uri?.lastPathSegment?.toIntOrNull()?.let { rid ->
                runCatching { context.resources.getResourceEntryName(rid) }.getOrNull()
            } ?: "(무음/알수없음)"
            val want = prefsOf(context)?.notificationSound(slot.key, slot.defaultRes) ?: slot.defaultRes
            val ok = if (want == "silent") uri == null else resName == want
            android.util.Log.d("NTFSND", "${if (ok) "OK " else "MISMATCH"} slot=${slot.key} ch=$id 원함=$want 실제=$resName uri=$uri")
        }
    }

    /**
     * 소리 변경 적용 (설정 화면에서 호출) — 고른 소리의 번호로 '새 id' 채널을 만들고 옛 채널은 정리.
     *   채널은 만든 뒤 소리를 못 바꾸므로 '새 id 로 새로 만들기'가 유일한 방법 (위 불변식 참고).
     *   별도 마이그레이션 불필요: id 가 소리 번호를 품고 있어 앱 업데이트로 번호가 밀려도 저절로 새 채널이 선다.
     */
    fun applySlotSound(context: Context, slotKey: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val id = channelForSlot(context, slotKey)   // 없으면 지금 소리로 생성
        pruneOldSlotChannels(context, slotKey, keepId = id)
    }

    // ── 미리듣기 (화면에서 [▶] 탭 시) ──
    private var previewPlayer: android.media.MediaPlayer? = null
    fun previewSound(context: Context, res: String) {
        stopPreview()
        if (res == "silent") return
        val id = context.resources.getIdentifier(res, "raw", context.packageName)
        if (id <= 0) return
        previewPlayer = android.media.MediaPlayer.create(context, id)?.apply {
            setOnCompletionListener { runCatching { it.release() }; if (previewPlayer === it) previewPlayer = null }
            start()
        }
    }
    fun stopPreview() { runCatching { previewPlayer?.release() }; previewPlayer = null }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            // 소리 있는 채널은 전부 SOUND_SLOTS + ensureSlotChannel 담당 (아래) — 여기선 무음/기본 채널만.
            if (manager.getNotificationChannel(CHANNEL_FOLLOW_UP) == null) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_FOLLOW_UP, "통화 후속 안내", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "방금 끝난 통화에 대한 후속 문자 안내" })
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
            // 문자함(고객 아님) 새 문자 — 조용히: 헤드업 X, 소리 X, 알림함에만 + 배지. (2026-07-11 사장님)
            if (manager.getNotificationChannel(CHANNEL_GENERAL_SMS) == null) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_GENERAL_SMS, "🗂️ 문자함", NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "고객이 아닌 문자(광고·인증·알림) — 조용히 알림함에만 표시"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(true)
                })
            }
            // 통화 후 문자 보내기 — 새 번호 통화 끝나면 헤드업으로 "문자 보낼까요?" + 템플릿 선택. (2026-07-12 사장님)
            if (manager.getNotificationChannel(CHANNEL_POSTCALL) == null) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_POSTCALL, "통화 후 문자", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "새 번호와 통화가 끝나면 보낼 문자 템플릿을 고르라고 알려줘요" })
            }
            // 오늘의 현장 — 무음·낮은 중요도(상단 조용히 고정). 소리/배지 없음. (2026-07-10 사장님)
            if (manager.getNotificationChannel(CHANNEL_TODAY_SITE) == null) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_TODAY_SITE, "오늘의 현장", NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "오늘 시공 현장 주소(동·호)를 상단에 계속 띄워 현장에서 바로 확인해요"
                    setShowBadge(false)
                    setSound(null, null)
                })
            }
            // ── 소리 고를 수 있는 채널들 (SOUND_SLOTS 가 유일한 정의) ──
            //   여기서 base id 로 직접 만들지 않는다 — 소리 번호가 어긋난 옛 채널을 되살릴 뿐이다.
            //   channelForSlot 이 '지금 소리 번호'를 품은 id 로 만들고, prune 이 옛 채널(base·_v·옛 _s)을 치운다.
            //   앱을 열 때마다 돌아서, 업데이트로 번호가 밀려도 그 자리에서 스스로 바로잡힌다. (2026-07-15)
            SOUND_SLOTS.forEach { slot ->
                runCatching { pruneOldSlotChannels(context, slot.key, keepId = channelForSlot(context, slot.key)) }
            }
            runCatching { pruneRetiredChannels(context) }
            if (com.detailline.callfollowcrm.BuildConfig.DEBUG) runCatching { logSlotChannels(context) }
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
            if (customerId > 0) putExtra(MainActivity.EXTRA_CUSTOMER_ID, customerId)  // 그 고객으로 정확히 열기 (번호 포맷 매칭 우회). (2026-08-13)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val msg = "${name}님 · $dateLabel${timeLabel?.let { " $it" } ?: ""} · $address"
        showProtoPush(
            context, notifId, CHANNEL_INSTALL_D1, ACCENT_AMBER,
            title = "내일 시공 — 안내 문자 보낼까요?",
            msg = msg,
            note = "무음 자동발송 안 해요 · 사장님이 확인하면 보내요",
            contentIntent = pending,
            actions = listOf(PushAction("안내 보내기", pending))
        )
    }

    /**
     * A/S 그날 알림 (2026-08-01 사장님) — 오늘 A/S 예약이 있는 고객을 아침에 알림(주황).
     *   A/S 는 무료라 깜빡 잊기 쉬운데 잊으면 신뢰가 크게 깎임 → 그날 아침에 짚어줌.
     *   자동발송 없음(문자 안 나감). 탭 = 그 고객 채팅.
     */
    fun showAsToday(
        context: Context,
        customerId: Long,
        phone: String,
        name: String,
        whenLabel: String,
        address: String
    ) {
        val notifId = AS_ID_OFFSET + (customerId.toInt() and 0x7FFFFF)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, phone)
            if (customerId > 0) putExtra(MainActivity.EXTRA_CUSTOMER_ID, customerId)  // 그 고객으로 정확히 열기. (2026-08-13)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, ACCENT_AMBER,
            title = "오늘 A/S 있어요 🔧",
            msg = "${name}님 · $whenLabel · $address · 무료",
            note = "시공과 별개인 A/S 예약이에요 (무료)",
            contentIntent = pending,
            actions = listOf(PushAction("고객 열기", pending))
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
            if (customerId > 0) putExtra(MainActivity.EXTRA_CUSTOMER_ID, customerId)  // 그 고객으로 정확히 열기. (2026-08-13)
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

    private fun appOpenPending(context: Context, id: Int, action: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action?.let { this.action = it }   // 있으면 그 딥링크 화면으로 (없으면 앱 홈). (2026-08-13)
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
            if (customerId > 0) putExtra(MainActivity.EXTRA_CUSTOMER_ID, customerId)  // 그 고객으로 정확히 열기. (2026-08-13)
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
        val pending = appOpenPending(context, notifId, MainActivity.ACTION_TEAM)  // 팀 현황으로 (HOME 아님). (2026-08-13)
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
        reason: String? = null,   // 거절 사유(declined) — A 에게 "왜 거절했는지" 표시. (2026-07-08 사장님)
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
                "${partnerName}님이 '${title}' 협업을 거절했어요." + (reason?.trim()?.takeIf { it.isNotBlank() }?.let { " — \"$it\"" } ?: ""),
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
        // 상황마다 전용 소리 채널 (2026-07-15 사장님 세분화). "거의 도착"(3km auto)도 상대 도착으로 묶임.
        val channel = when (kind) {
            "accepted" -> CHANNEL_COLLAB_ACCEPTED
            "declined" -> CHANNEL_COLLAB_DECLINED
            "departed" -> CHANNEL_COLLAB_DEPARTED
            "arrived" -> CHANNEL_COLLAB_ARRIVED
            "completed" -> CHANNEL_COLLAB_COMPLETED
            else -> CHANNEL_REMINDER   // 여기 안 옴(위 when 에서 return) — 방어용
        }
        showProtoPush(
            context, notifId, channel, accent,
            title = pushTitle,
            msg = accountText?.let { "$msg · 계좌 $it" } ?: msg,
            contentIntent = pending,
            actions = listOf(PushAction("협업 현장 보기", pending))
        )
    }

    /** 본폰 미러 v2 — 새 '일정 공유 신청' 알림. 탭 → 앱 열기(더보기 → 본폰에서 일정 보기에서 수락). (2026-07-14 사장님) */
    fun showMirrorShareRequest(context: Context, homePhoneLabel: String, count: Int) {
        val notifId = MIRROR_SHARE_ID
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val msg = if (count > 1)
            "${homePhoneLabel} 외 ${count - 1}건이 일정 공유를 신청했어요. 더보기 → 본폰에서 일정 보기에서 수락하세요."
        else
            "${homePhoneLabel}가 일정 공유를 신청했어요. 더보기 → 본폰에서 일정 보기에서 수락하세요."
        showProtoPush(
            context, notifId, CHANNEL_REMINDER, ACCENT_BLUE,
            title = "📩 일정 공유 신청",
            msg = msg,
            contentIntent = pending,
            actions = listOf(PushAction("확인", pending))
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
            context, notifId, CHANNEL_COLLAB_COMMENT, ACCENT_PURPLE,
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
            context, notifId, CHANNEL_COLLAB_COMMENT, ACCENT_BLUE,   // 댓글과 한 칸 (사장님 지시)
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
            context, notifId, CHANNEL_COLLAB_INVITE, ACCENT_PURPLE,
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
            // 소리 = 자동응답과 같은 칸 (사장님 지시 2026-07-15): 둘 다 "막내가 나 대신 해줬어요" 성격이라
            //   새 소리를 따로 만들지 않고 CHANNEL_AUTO_REPLY 를 그대로 쓴다.
            context, notifId, CHANNEL_AUTO_REPLY, ACCENT_GREEN,
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
            context, notifId, CHANNEL_COLLAB_ENDED, ACCENT_PURPLE,
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
            context, notifId, CHANNEL_COLLAB_PAID, ACCENT_GREEN,
            title = "💰 입금 완료",
            msg = "'${site}' 정산 입금이 완료됐어요",
            contentIntent = pending,
            actions = listOf(PushAction("협업 현장 보기", pending))
        )
    }

    /** 박람회 계약 시공자 배정 — 방장이 나눠 배정하면 배정된 시공자에게 "새 시공 배정" 알림. (2026-07-27)
     *  서버 FCM(type=expo_assigned)로 옴. 배분은 건별로 여러 번 오므로 방(room_id) 기준 같은 ID 로 update(한 개로 합쳐짐).
     *  탭 = 앱 열기(박람회 > 내 접수서함에서 확인). 소리는 '협업 요청' 채널 재사용(전용 분리는 추후). */
    fun showExpoAssigned(context: Context, roomId: String, roomName: String) {
        val notifId = EXPO_ASSIGN_ID_OFFSET + (roomId.hashCode() and 0x7FFFFF)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val room = roomName.takeIf { it.isNotBlank() } ?: "박람회"
        showProtoPush(
            context, notifId, CHANNEL_COLLAB_INVITE, ACCENT_PURPLE,
            title = "🔨 시공 배정",
            msg = "'${room}'에서 시공이 배정됐어요",
            note = "박람회 > 내 접수서함에서 확인하세요.",
            contentIntent = pending,
            actions = listOf(PushAction("확인", pending))
        )
    }

    /**
     * A(현장 주인)가 시공일정을 바꿈 → 협업 사장(B)에게 "일정 변경: 옛→새" 알림. (2026-07-16 사장님)
     *   서버 FCM(type=collab_reschedule)로 옴. 소리는 우선 '협업 현장 소식'(comment) 채널 재사용 —
     *   전용 '일정 변경' 소리 분리는 사장님 확인 후(§SYNC). 탭 = 그 협업 현장.
     * @param oldLabel/newLabel "6/21(수)" 같은 라벨(서버가 못 주면 at_ms 로 앱이 포맷).
     */
    fun showCollabReschedule(
        context: Context,
        shareId: String,
        title: String,
        oldLabel: String?,
        newLabel: String?,
        timeLabel: String?
    ) {
        val notifId = COLLAB_ID_OFFSET + ("reschedule:$shareId".hashCode() and 0x7FFFFF)
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
        val newWithTime = listOfNotNull(newLabel?.takeIf { it.isNotBlank() }, timeLabel?.takeIf { it.isNotBlank() })
            .joinToString(" ").takeIf { it.isNotBlank() }
        val msg = when {
            !oldLabel.isNullOrBlank() && newWithTime != null -> "'${site}' 일정이 ${oldLabel} → ${newWithTime} 로 바뀌었어요"
            newWithTime != null -> "'${site}' 일정이 ${newWithTime} 로 바뀌었어요"
            else -> "'${site}' 시공 일정이 바뀌었어요 — 확인해 주세요"
        }
        showProtoPush(
            context, notifId, CHANNEL_COLLAB_COMMENT, ACCENT_PURPLE,
            title = "📅 협업 현장 일정 변경",
            msg = msg,
            contentIntent = pending,
            actions = listOf(PushAction("협업 현장 보기", pending))
        )
    }

    /** 마감 브리핑 — 프로토 PUSH.brief 형식(파랑, 저녁 9시). 확실한 데이터만. */
    fun showDailyBrief(context: Context, newCustomers: Int, deposits: Int, tomorrowJobs: Int, tomorrowLabel: String?, briefDayMs: Long = 0L) {
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
            if (briefDayMs > 0L) putExtra(MainActivity.EXTRA_BRIEF_DAY, briefDayMs) // 자정 넘겨 탭해도 그날 브리핑 보이게
        }
        val pending = PendingIntent.getActivity(
            context, BRIEF_ID, briefIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 늦게(밤 10시 넘겨) 뜨는 마감 브리핑은 소리·헤드업 없이 조용히 — 자다/쉬다 깜짝 놀라지 않게. (2026-08-29 사장님)
        //   원인: 브리핑은 밤 9시 타겟인데 폰이 절전(Doze)이면 밀려서, 앱을 켜는 순간(10시·11시 등) 뒤늦게 발사된다.
        //   그때까지 소리 억제는 '앱 방해금지 토글(quietHours)'에만 의존 → 토글이 꺼져 있으면 늦은 밤에도 소리로 튀었다.
        //   브리핑은 급하지 않은 하루 요약이므로, 정시(9시대)를 넘긴 늦은 발사는 무음 야간 채널로 조용히 보낸다.
        //   (9시대 정시 발사는 그대로 소리 — 프로토 brief=오후 9시. resolveChannel 이 앱 방해금지도 계속 존중.)
        val briefHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val briefChannel = if (briefHour >= 22 || briefHour < 8) {
            ensureNightQuietChannel(context); CHANNEL_NIGHT_QUIET
        } else CHANNEL_DAILY_BRIEF
        showProtoPush(
            context, BRIEF_ID, briefChannel, ACCENT_BLUE,
            title = "오늘 하루 마감 브리핑 🌙",
            msg = msg, note = note,
            contentIntent = pending,
            actions = listOf(PushAction("오늘 정리 보기", pending))
        )
    }

    /** 정기 문자 발송 전 확인 — 프로토 PUSH.recur 형식(청록, 오전 9시). */
    fun showRecurringDue(context: Context, count: Int, ruleNames: String) {
        val pending = appOpenPending(context, RECUR_ID, MainActivity.ACTION_RECURRING_DUE)  // 정기문자 검토 화면으로 (HOME 아님). (2026-08-13)
        val prefix = if (ruleNames.isNotBlank()) "$ruleNames · " else ""
        showProtoPush(
            context, RECUR_ID, CHANNEL_RECURRING, ACCENT_TEAL,
            title = "오늘 정기 문자 보낼 고객 ${count}명",
            msg = "${prefix}오늘 ${count}명 · 고객 이름은 자동으로 채워드려요 · 보내기 전에 한 번 봐주세요",
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
        val builder = NotificationCompat.Builder(context, resolveChannel(context, channelId))
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
     * 오늘의 현장 상시 알림 (2026-07-10 사장님) — 오늘 시공(주소 있는) 현장 주소(동·호)를 상단에 계속 띄워
     *   현장에서 "몇동 몇호였지?" 바로 확인. 무음·상단고정(ongoing)·배지 없음. 탭 = 첫 현장 채팅.
     *   갱신/철거는 ReminderWorker.refreshTodaySites 가 담당(주기+앱시작).
     */
    fun showTodaySites(context: Context, count: Int, lines: List<String>, openPhone: String?) {
        if (lines.isEmpty()) { clearTodaySites(context); return }
        val title = if (count <= 1) "오늘의 현장" else "오늘의 현장 ${count}곳"
        // 왜 안 지워지는지 + 언제 사라지는지 알려준다 — 상시(ongoing) 알림이라 스와이프로 안 지워져서
        //   안내가 없으면 "이거 왜 계속 있지?" 가 된다. (2026-07-15 사장님 "안내가 어디 있으면 좋을듯")
        val body = lines.joinToString("\n") + "\n\n잔금까지 받으면 자동으로 사라져요"
        val pending = openPhone?.takeIf { it.isNotBlank() }?.let {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_CHAT
                putExtra(MainActivity.EXTRA_PHONE_NUMBER, it)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(
                context, TODAY_SITE_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_TODAY_SITE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT_TEAL)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)        // 상단 고정(스와이프로 안 지워짐) — "계속 나오게"
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
        pending?.let { builder.setContentIntent(it) }
        try {
            NotificationManagerCompat.from(context).notify(TODAY_SITE_ID, builder.build())
        } catch (_: SecurityException) { }
    }

    /** 오늘의 현장 상시 알림 내리기 — 오늘 현장이 없거나 날짜가 지났을 때. */
    fun clearTodaySites(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(TODAY_SITE_ID) }
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
    fun smsNotificationId(phone: String): Int {
        val digits = phone.filter { it.isDigit() }
        // 숫자 4개 미만(영문 브랜드 발신자 등)이면 뒷8자리가 "" → "".hashCode()==0 로 전부 같은 id 가 되어
        //   서로 다른 발신자 알림이 덮어써졌음 → 발신자 원문 전체로 유니크화. (2026-08-08 stale 감사)
        val base = if (digits.length >= 4) digits.takeLast(8).hashCode() else phone.trim().hashCode()
        return SMS_ID_OFFSET + (base and 0x7FFFFFF)
    }

    /**
     * 갤메시지 대체 풍부한 SMS 수신 알림. Step 1 — 기본 표시.
     *   - 헤더: 이름(있으면) 또는 포맷팅된 번호 + 카테고리
     *   - 본문: BigText 확장형
     *   - 탭 = ChatScreen 진입
     *   - 액션은 후속 Step 에서 (RemoteInput / AI 추천 답변 / 전화)
     */
    /** 문자 본문에서 인증번호(OTP) 추출 — '인증/코드/OTP' 문맥 뒤 4~8자리만(전화번호 오탐 방지). null=없음. (2026-09-01 사장님) */
    private fun detectOtpCode(body: String): String? =
        Regex(
            "(?:인증번호|인증코드|승인번호|인증|코드|OTP|verification code|code)[^0-9]{0,8}(\\d{4,8})",
            RegexOption.IGNORE_CASE
        ).find(body)?.groupValues?.getOrNull(1)

    fun showIncomingSms(
        context: Context,
        phone: String,
        displayName: String?,
        body: String,
        receivedAtMs: Long,
        categoryLabel: String? = null,
        isNewCustomer: Boolean = false,
        // 수신 처리에서 이미 찾은 고객 id. 있으면 실어 보내 채팅이 '번호 포맷 매칭' 대신 '그 고객'으로 정확히 열림
        //   → 저장번호 포맷이 다를 때 '미등록'으로 잘못 열리거나 중복 고객이 생기던 것 방지. (2026-08-11 알림 감사)
        customerId: Long? = null
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, phone)
            customerId?.takeIf { it > 0 }?.let { putExtra(MainActivity.EXTRA_CUSTOMER_ID, it) }
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
        val builder = NotificationCompat.Builder(context, resolveChannel(context, smsChannel))
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)  // 보안감사(cowork): 잠금화면 PII(번호·문자) 가림
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

        // 인증번호(OTP) 감지 시 '복사' 액션 추가 — 은행·택배 인증번호 원탭 복사. (2026-09-01 사장님)
        detectOtpCode(body)?.let { code ->
            val copyIntent = Intent(context, CopyToClipboardReceiver::class.java).apply {
                action = CopyToClipboardReceiver.ACTION_COPY
                putExtra(CopyToClipboardReceiver.EXTRA_TEXT, code)
                putExtra(CopyToClipboardReceiver.EXTRA_LABEL, "인증번호")
            }
            val copyPending = PendingIntent.getBroadcast(
                context, ("copy" + phone + code).hashCode(), copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_notification, "📋 $code 복사", copyPending).build()
            )
        }

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(smsNotificationId(phone), builder.build())
        }
    }

    /**
     * 문자함(고객 아님) 새 문자 — 조용한 알림. (2026-07-11 사장님 결정: "조용히 알림 + 배지")
     *   헤드업·소리·진동 없음. 알림함에만 쌓이고 탭하면 그 대화로. 인증번호 등 바로 봐야 할 때 대비해 알림은 남김.
     */
    fun showGeneralSms(
        context: Context,
        phone: String,
        displayName: String?,
        body: String,
        receivedAtMs: Long,
        customerId: Long? = null   // 있으면 그 고객으로 정확히 열기 (번호 포맷 매칭 우회). (2026-08-11 알림 감사)
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, phone)
            customerId?.takeIf { it > 0 }?.let { putExtra(MainActivity.EXTRA_CUSTOMER_ID, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, phone.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = (displayName?.takeIf { it.isNotBlank() } ?: formatPhone(phone)) + " · 문자함"
        val builder = NotificationCompat.Builder(context, CHANNEL_GENERAL_SMS)
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)  // 보안감사(cowork): 잠금화면 PII 가림
            .setContentTitle(title)
            .setContentText(body.take(60))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setWhen(receivedAtMs)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
        runCatching {
            NotificationManagerCompat.from(context).notify(smsNotificationId(phone), builder.build())
        }
    }

    /**
     * 통화 후 문자 보내기 — 새 번호와 통화 끝나면 "○○님께 문자 보낼까요?" + 템플릿 최대 3개 버튼. (2026-07-12 사장님)
     *   버튼 탭 = 그 템플릿을 입력창에 채운 채 채팅 열림(자동발송 X, 사장님이 ▶ 확인 발송).
     */
    fun showPostCallTemplatePicker(
        context: Context,
        phone: String,
        displayName: String?,
        items: List<Pair<String, List<String>>>   // (본문, 사진 URI 목록 최대 5) — 사진만 있는 템플릿도 허용
    ) {
        if (items.isEmpty()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val who = displayName?.takeIf { it.isNotBlank() } ?: formatPhone(phone)
        val nid = POSTCALL_ID_BASE + (phone.hashCode() and 0xFFFF)
        // resolveChannel: 방해금지 시간엔 야간(무음) 채널로 몰아 야간에도 소리·헤드업으로 튀던 것 방지(다른 알림과 동일 게이트).
        //   비-슬롯 채널이라 평소엔 CHANNEL_POSTCALL 그대로. (2026-08-11 알림 감사)
        val builder = NotificationCompat.Builder(context, resolveChannel(context, CHANNEL_POSTCALL))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${who}님께 문자 보낼까요?")
            .setContentText("보낼 문자를 고르면 확인 후 보낼 수 있어요")
            .setColor(NOTIFICATION_BG_COLOR)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        items.take(3).forEachIndexed { i, (tpl, photos) ->
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_CHAT
                putExtra(MainActivity.EXTRA_PHONE_NUMBER, phone)
                if (tpl.isNotBlank()) putExtra(MainActivity.EXTRA_PREFILL_BODY, tpl)
                if (photos.isNotEmpty()) putExtra(MainActivity.EXTRA_PREFILL_PHOTO, photos.joinToString("\n"))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, nid + i + 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // 버튼 라벨 = "1. {미리보기}" (+📷 사진 있으면). 어떤 문구인지 바로 알아보게.
            val preview = tpl.replace("\n", " ").trim().take(12).ifBlank { "사진 ${photos.size}장" }
            val label = "${i + 1}. " + (if (photos.isNotEmpty()) "📷 " else "") + preview
            builder.addAction(R.drawable.ic_notification, label, pending)
        }
        // 본체 탭 = 그냥 채팅 열기(템플릿 없이).
        val plainOpen = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, phone)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        builder.setContentIntent(
            PendingIntent.getActivity(context, nid, plainOpen,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        runCatching { NotificationManagerCompat.from(context).notify(nid, builder.build()) }
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
    /** 그 번호의 채팅(ChatScreen)으로 여는 PendingIntent. 자동문자 성공/실패 알림 등에서 재사용(데드엔드 방지). */
    private fun chatPending(context: Context, phoneNumber: String, reqId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, phoneNumber)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, reqId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

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

        val title = if (isMissed) "📵 아직 답장 못 한 고객이 부재중 전화했어요" else "📞 아직 답장 못 한 고객이 다시 전화했어요"
        val text = "${formatPhone(phoneNumber)} · 메시지 확인하기"

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
        customerId: Long? = null,
        preview: String? = null
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
        // 에이닷 벤치마킹 — 깔끔하게: 짧은 제목 + 본문에 요약 한 줄 · 누구. (2026-07-06 사장님)
        //   기존 제목("막내가 X님 통화를 요약했어요")이 너무 길어 지저분 + 요약이 접힌 알림에서 잘림.
        //   에이닷처럼 제목은 짧게("통화요약 완료!"), 요약 주제는 본문 한 줄로. (앱 상단에 이미 '시공막내' 표시됨)
        val title = "✨ 통화요약 완료!"
        val summaryLine = preview?.trim()?.replace("\n", " ")?.takeIf { it.isNotBlank() }
        val body = if (summaryLine != null) "$summaryLine · ${who}님"
                   else "${who}님 통화 요약이 준비됐어요 · 탭해서 확인"
        val builder = NotificationCompat.Builder(context, resolveChannel(context, CHANNEL_CALL_SUMMARY))
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)  // 보안감사(cowork): 잠금화면 통화요약 가림
            .setColor(NOTIFICATION_BG_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            // 에이닷처럼 안 사라지고 알림창에 남게 — 4초 자동소멸(setTimeoutAfter) 제거. 탭하면 사라짐. (2026-07-03 사장님)
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

        val builder = NotificationCompat.Builder(context, resolveChannel(context, CHANNEL_AUTO_REPLY))
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)  // 보안감사(cowork): 잠금화면 PII 가림
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
            msg = "${formatPhone(phoneNumber)} 님께 자동으로 답장을 보냈어요.",
            note = "탭하면 보낸 내용을 볼 수 있어요.",
            contentIntent = chatPending(context, phoneNumber, autoReplyIdFor(callRecordId)),
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
            msg = "${formatPhone(phoneNumber)} — 탭해서 직접 보내주세요.",
            contentIntent = chatPending(context, phoneNumber, autoReplyIdFor(callRecordId))
        )
    }

    private const val MMS_FAIL_ID_BASE = 970_000

    /**
     * MMS(사진) 직접 발송 실패 알림 — 지하·약신호 현장에서 사진이 실제론 못 나갔는데 '사진 보냈어요'로 뜨던
     *   '거짓 성공'을 막는다. 사장님이 실패를 인지하고 신호 좋을 때 탭해서 다시 보내게. (2026-08-11 오프라인 감사 rank1)
     */
    fun showMmsSendFailed(context: Context, phoneNumber: String) {
        val id = MMS_FAIL_ID_BASE + (phoneNumber.filter { it.isDigit() }.takeLast(8).hashCode() and 0x7FFFFFF)
        showProtoPush(
            context, id, CHANNEL_AUTO_REPLY, ACCENT_PINK,
            title = "⚠️ 사진이 안 보내졌어요",
            msg = "${formatPhone(phoneNumber)} — 신호 확인 후 탭해서 다시 보내주세요.",
            contentIntent = chatPending(context, phoneNumber, id)
        )
    }
}
