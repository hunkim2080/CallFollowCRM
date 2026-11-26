package com.detailline.callfollowcrm.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.detailline.callfollowcrm.MainActivity
import com.detailline.callfollowcrm.R

/**
 * 📍 '오늘의 현장' 상시 알림 — **현장마다 한 건씩** 띄운다(삼성에선 '시공막내'로 묶임). (2026-11-26 사장님)
 *   한 알림에 다 넣으면 접혔을 때 첫 줄만 보여 "펼쳐야 보인다" → 현장별로 나눠 각각:
 *     · 왼쪽 청록 바 + **동호수 크게/밑줄/굵게** + 도로명 + 시각 (커스텀 배너, 기기별 실패 시 글자 폴백)
 *     · [길찾기] (지도앱) · [완료] (앱 안 열고 바로 내림 — [TodaySiteCompleteReceiver])
 *   글자색은 레이아웃의 알림 전용 TextAppearance 가 **테마(라이트/다크) 자동 적응**, 동호수만 여기서 강조.
 *
 *   알림 ID 는 FAM_TODAY_SITE 대역(현장 key 해시) — 다른 종류와 구조상 안 겹침. 요약(묶음 머리)은 TODAY_SITE_ID 재사용.
 */
object TodaySiteNotifier {

    private const val GROUP = "today_sites"

    /** 현장 하나 — 알림 한 건에 필요한 것 다. (내 현장이면 customerId, 협업이면 shareId) */
    data class Item(
        val key: String,              // "own:123" / "collab:abc" — 알림 ID·요청코드용
        val bigLabel: String,         // 아파트명+동호수
        val road: String?,            // 도로명·지역 (없으면 줄 숨김)
        val meta: String?,            // ⏰ 시각 (없으면 줄 숨김)
        val navName: String,          // 길찾기 목적지(주소)
        val openPhone: String?,       // 내 현장 채팅 열 번호(없으면 앱만 열기)
        val customerId: Long?,        // 완료(내 현장)
        val shareId: String?,         // 완료(협업)
        val summaryLine: CharSequence // 요약 알림 한 줄
    )

    /** 현장별 알림을 전부 (다시) 띄운다. 비면 모두 내린다. 끝난(목록에서 빠진) 현장 알림은 자동으로 치운다. */
    fun post(context: Context, items: List<Item>) {
        if (items.isEmpty()) { clear(context); return }
        val nmc = NotificationManagerCompat.from(context)
        val teal = ContextCompat.getColor(context, R.color.today_accent)
        val keep = ArrayList<Int>()

        for (it in items) {
            val id = idFor(it.key)
            keep.add(id)
            val (complex, unit) = splitComplexUnit(it.bigLabel)
            val b = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_TODAY_SITE)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(teal)
                .setContentTitle(complex)
                .setContentText(listOfNotNull(unit, it.meta).joinToString(" · "))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)            // 상단 고정(스와이프로 안 지워짐) — "계속 나오게"
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setGroup(GROUP)
                .setContentIntent(openIntent(context, it))
                .addAction(R.drawable.ic_notification, "길찾기", navIntent(context, it))
                .addAction(
                    R.drawable.ic_notification, "완료",
                    if (it.customerId != null) TodaySiteCompleteReceiver.own(context, it.customerId, id)
                    else TodaySiteCompleteReceiver.collab(context, it.shareId.orEmpty(), id)
                )
            val rv = buildView(context, it.bigLabel, it.road, it.meta)
            if (rv != null) {
                b.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(rv)
                    .setCustomBigContentView(rv)
            }
            runCatching { nmc.notify(id, b.build()) }
        }

        // 묶음 머리(요약) — 2곳 이상일 때만. 접혔을 때 '오늘의 현장 N곳', 펼치면 각 현장. 1곳이면 그 한 건만(요약 불필요).
        if (items.size >= 2) {
            val summary = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_TODAY_SITE)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(teal)
                .setContentTitle("오늘의 현장 ${items.size}곳")
                .setContentText("완료하면 자동으로 사라져요")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setStyle(NotificationCompat.InboxStyle().also { st ->
                    items.forEach { st.addLine(it.summaryLine) }
                    st.setSummaryText("완료하면 자동으로 사라져요")
                })
            runCatching { nmc.notify(NotificationHelper.TODAY_SITE_ID, summary.build()) }
            keep.add(NotificationHelper.TODAY_SITE_ID)
        } else {
            // 1곳 — 묶음 머리/옛 단일 알림 내림(그 한 건만 남긴다).
            runCatching { nmc.cancel(NotificationHelper.TODAY_SITE_ID) }
        }

        // 끝난 현장(이번 목록에 없는) 알림 치우기 — 같은 묶음(GROUP)에서 keep 아닌 것.
        cancelStale(context, keep.toSet())
    }

    /** 오늘 현장이 없을 때 — 묶음 전부 + 요약(옛 단일 알림 포함) 내린다. */
    fun clear(context: Context) {
        val nmc = NotificationManagerCompat.from(context)
        cancelStale(context, emptySet())
        runCatching { nmc.cancel(NotificationHelper.TODAY_SITE_ID) }
    }

    private fun cancelStale(context: Context, keep: Set<Int>) {
        val nmc = NotificationManagerCompat.from(context)
        runCatching {
            val raw = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            raw.activeNotifications
                .filter { it.notification.group == GROUP && it.id !in keep }
                .forEach { nmc.cancel(it.id) }
        }
    }

    private fun idFor(key: String): Int =
        NotificationHelper.famId(NotificationHelper.FAM_TODAY_SITE, ("site:$key").hashCode())

    /** 알림 탭 — 내 현장은 그 손님 채팅, 협업은 앱 홈. */
    private fun openIntent(context: Context, it: Item): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!it.openPhone.isNullOrBlank()) {
                action = MainActivity.ACTION_CHAT
                putExtra(MainActivity.EXTRA_PHONE_NUMBER, it.openPhone)
            }
        }
        return PendingIntent.getActivity(
            context, ("open:${it.key}").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** [길찾기] — geo: 로 폰의 지도앱(여러 개면 선택). 내 현장·협업 모두 주소로. 좌표 없어도 동작. */
    private fun navIntent(context: Context, it: Item): PendingIntent {
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(it.navName)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context, ("nav:${it.key}").hashCode(), geo,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 동호수 꼬리 — "718동 602호" / "602호" 가 맨 끝에 있을 때만(이 부분만 밑줄+색). */
    private val UNIT_TAIL = Regex("""(\d{1,4}동\s*)?\d{1,5}호\s*$""")

    /**
     * 긴 라벨을 **단지명 / 동호수** 로 가른다 — 분리 셈은 여기 한 곳(§12. 커스텀 배너·요약이 같이 씀).
     *   "래미안루센티아아파트 111동 1103호" → ("래미안루센티아아파트", "111동 1103호").
     *   앞에 단지명이 없으면(동호수뿐) 통째로 단지명 자리에 두고 동호수는 null(빈 제목 방지).
     */
    fun splitComplexUnit(label: String): Pair<String, String?> {
        val m = UNIT_TAIL.find(label) ?: return label.trim() to null
        if (m.range.first <= 0) return label.trim() to null
        val complex = label.substring(0, m.range.first).trim()
        val unit = m.value.trim()
        return if (complex.isBlank() || unit.isBlank()) label.trim() to null else complex to unit
    }

    /**
     * @param bigLabel 아파트명+동호수 (예: "동아 아파트 718동 602호"). 단지명/동호수 두 줄로 나눠 그린다.
     * @param roadLine 도로명·지역 (예: "경기 수원시 영통구 봉영로 1526"). 비면 줄 숨김.
     * @param metaLine 시각 (예: "⏰ 오전 9시"). 비면 줄 숨김.
     */
    fun buildView(context: Context, bigLabel: String, roadLine: String?, metaLine: String?): RemoteViews? =
        runCatching {
            val rv = RemoteViews(context.packageName, R.layout.notification_today_site)
            val (complex, unit) = splitComplexUnit(bigLabel)
            rv.setTextViewText(R.id.today_big, complex)
            setLineOrGone(rv, R.id.today_unit, unit?.let { emphasize(context, it) })
            setLineOrGone(rv, R.id.today_road, roadLine)
            setLineOrGone(rv, R.id.today_meta, metaLine)
            rv
        }.getOrNull()

    /** 동호수를 청록색 + 밑줄 + 굵게 — 요약/배너가 같이 쓴다. (굵게는 색을 테마가 덮는 기기에서도 두드러지게) */
    fun emphasize(context: Context, unit: String): CharSequence {
        val accent = ContextCompat.getColor(context, R.color.today_accent)
        return SpannableString(unit).apply {
            setSpan(ForegroundColorSpan(accent), 0, unit.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(), 0, unit.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, unit.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun setLineOrGone(rv: RemoteViews, id: Int, text: CharSequence?) {
        if (text.isNullOrBlank()) {
            rv.setViewVisibility(id, android.view.View.GONE)
        } else {
            rv.setViewVisibility(id, android.view.View.VISIBLE)
            rv.setTextViewText(id, text)
        }
    }
}
