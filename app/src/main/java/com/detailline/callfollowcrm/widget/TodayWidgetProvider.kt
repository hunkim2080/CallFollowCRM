package com.detailline.callfollowcrm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.MainActivity
import com.detailline.callfollowcrm.R
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import com.detailline.callfollowcrm.util.AddressExtractor
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.NavApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 🧱 '오늘 갈 현장 + 답장 안 함' 홈 위젯 — 2026-11-04 사장님.
 *
 *   앱을 안 열어도 홈 화면에서 **오늘 시공 현장**(로컬 일정)과 **답장 안 함 수**(홈이 센 마지막 값)를 본다.
 *   새 라이브러리(Glance) 안 쓰고 RemoteViews. 값은 화면이 이미 한 계산을 재활용(두 벌 0).
 *
 *   ⚠️ 갱신은 `AppWidgetManager.updateAppWidget` 로 **직접** 그린다 — `ACTION_APPWIDGET_UPDATE` 브로드캐스트는
 *      **시스템 보호**라 앱이 못 보낸다(전엔 그걸 보내 조용히 실패했다). [refresh] 가 일정 바뀜·완료 때 호출된다.
 *      그 외엔 30분(updatePeriodMillis) 자동. 시각·협업은 다음 단계.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = buildViews(appCtx, loadSnapshot(appCtx))
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 🔧 테스트/강제 갱신 — 앱 안에서 보낼 수 있는 커스텀 액션(보호 브로드캐스트 아님).
        if (intent.action == ACTION_WIDGET_REFRESH) refresh(context)
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.detailline.callfollowcrm.WIDGET_REFRESH"

        private data class Site(
            val headline: String,   // 이름 있으면 "홍길동 고객님", 없으면 단지명(없으면 주소 한 줄)
            val region: String,     // 작은 회색 줄 (지역 · 단지)
            val unit: String,       // "102동 1203호" — 없으면 ""
            val memo: String,       // 현장 메모(건 memo, 📌비번 포함) — 없으면 ""
            val timeLabel: String,  // "오전 9시" — 시각 없으면 ""
            val navAddress: String, // 길찾기 목적지 주소
            val nameAbsent: Boolean // 이름 없는 현장 → '오늘 시공' 칩 + 파란 동호수
        )
        /** 맨 아래 칸 — 오늘 2번째 현장(이어서) 또는 다음 날짜 현장(내일/11/7(금)). */
        private data class NextSite(
            val title: String, val headline: String, val region: String,
            val unit: String, val timeLabel: String
        )
        private data class Snapshot(
            val today: List<Site>,
            val next: NextSite? = null,
            val navApp: String = "",
            val unanswered: Int = 0,
            val upName: String = "",
            val upElapsed: String = "",
            val upMsg: String = ""
        )

        /** 어디서든 위젯을 다시 그린다 — 일정 바뀜·완료·자정 등에서 부른다. (직접 updateAppWidget) */
        fun refresh(context: Context) {
            val appCtx = context.applicationContext
            val mgr = AppWidgetManager.getInstance(appCtx) ?: return
            val ids = runCatching {
                mgr.getAppWidgetIds(ComponentName(appCtx, TodayWidgetProvider::class.java))
            }.getOrNull() ?: return
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                val views = buildViews(appCtx, loadSnapshot(appCtx))
                ids.forEach { runCatching { mgr.updateAppWidget(it, views) } }
            }
        }

        private suspend fun loadSnapshot(context: Context): Snapshot {
            val app = context.applicationContext as? CallFollowCrmApplication ?: return Snapshot(emptyList())
            val customers = runCatching { app.container.customerRepository.allOnce() }.getOrDefault(emptyList())
            // 🧱 현장 메모·시각·주소는 **그 날짜의 건(job)**이 진짜(§13④) — jobs.memo=현장 메모(📌비번 포함).
            val allJobs = runCatching { app.container.jobRepository.allOnce() }.getOrDefault(emptyList())
            val jobsByCustomer = allJobs.groupBy { it.customerId }
            fun jobForDate(c: CustomerEntity): JobEntity? {
                val ds = c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } ?: return null
                return jobsByCustomer[c.id]?.firstOrNull { j ->
                    j.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) == ds } == true
                }
            }
            val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
            val todayEnd = todayStart + DateTimeUtils.DAY_MS
            val todayCustomers = customers
                .filter { c -> c.scheduledWorkDate?.let { it in todayStart until todayEnd } == true }
                .sortedWith(compareBy(
                    { it.scheduledWorkDate },
                    { it.scheduledWorkMinutes ?: Int.MAX_VALUE },
                    { it.name ?: it.phoneNumber }
                ))
            val todaySites = todayCustomers.map { siteOf(it, jobForDate(it)) }

            // 맨 아래 칸: 오늘 2번째 현장이 있으면 "이어서", 없으면 가장 가까운 다음 날짜 현장을 "내일/날짜"로.
            val next: NextSite? = if (todayCustomers.size >= 2) {
                val c = todayCustomers[1]
                val s = siteOf(c, jobForDate(c))
                NextSite("이어서", s.headline, s.region, s.unit, s.timeLabel)
            } else {
                val fut = customers
                    .filter { c -> (c.scheduledWorkDate ?: -1L) >= todayEnd }
                    .minByOrNull { it.scheduledWorkDate ?: Long.MAX_VALUE }
                fut?.let { c ->
                    val s = siteOf(c, jobForDate(c))
                    NextSite(nextDayLabel(c.scheduledWorkDate!!, todayStart), s.headline, s.region, s.unit, s.timeLabel)
                }
            }

            // 답장 안 함 수 + 대표 미답 고객 미리보기 — 홈 화면이 센 마지막 값(저장). 위젯은 다시 안 센다(두 벌 방지).
            val p = app.container.preferences
            return Snapshot(
                today = todaySites,
                next = next,
                navApp = runCatching { p.navApp }.getOrDefault(""),
                unanswered = runCatching { p.widgetUnansweredCount }.getOrDefault(0),
                upName = runCatching { p.widgetUnansweredName }.getOrDefault(""),
                upElapsed = runCatching { p.widgetUnansweredElapsed }.getOrDefault(""),
                upMsg = runCatching { p.widgetUnansweredMsg }.getOrDefault("")
            )
        }

        /**
         * 한 고객(+그 날짜의 건) → 위젯 현장 한 줄. 건(job)이 있으면 그 건의 주소·시각·현장 메모를 우선.
         *   주소 쪼개기는 AddressExtractor(공용) 한 곳, 시각 라벨은 DateTimeUtils 한 곳 — 두 벌 금지(§12).
         */
        private fun siteOf(c: CustomerEntity, job: JobEntity?): Site {
            val addr = job?.address?.takeIf { it.isNotBlank() } ?: c.address
            val minutes = job?.scheduledWorkMinutes ?: c.scheduledWorkMinutes
            val memoRaw = job?.memo?.takeIf { it.isNotBlank() } ?: c.memo  // 현장 메모(건) 우선, 없으면 손님 메모
            val complex = AddressExtractor.complexName(addr)
            val reg = regionPrefix(addr)
            val parts = AddressExtractor.splitDongHo(addr)
            val unit = when {
                parts.dong != null && parts.ho != null -> "${parts.dong}동 ${parts.ho}호"
                parts.ho != null -> "${parts.ho}호"
                parts.dong != null -> "${parts.dong}동"
                else -> ""
            }
            val nm = c.name?.takeIf { s -> s.isNotBlank() && s.any { it.isLetter() } }
            val headline = when {
                nm != null -> "$nm 고객님"
                !complex.isNullOrBlank() -> complex
                else -> AddressExtractor.roughSite(addr).ifBlank { addr?.takeIf { it.isNotBlank() } ?: reg }
            }
            val region = if (nm != null) {
                listOfNotNull(complex?.takeIf { it.isNotBlank() }, reg.takeIf { it.isNotBlank() }).joinToString(" · ")
            } else {
                reg // 이름 없으면 단지명이 헤드라인으로 올라가니 지역만
            }
            val timeLabel = minutes?.let { DateTimeUtils.formatWorkMinutes(it) } ?: ""
            return Site(
                headline = headline,
                region = region,
                unit = unit,
                memo = memoRaw.trim(),
                timeLabel = timeLabel,
                navAddress = AddressExtractor.tidyAddress(addr),
                nameAbsent = nm == null
            )
        }

        /** 다음 날짜 라벨 — 바로 다음 날이면 "내일", 그 외엔 "11/7(금)"(실제 날짜·요일). */
        private fun nextDayLabel(targetMs: Long, todayStart: Long): String {
            val diff = ((DateTimeUtils.startOfDay(targetMs) - todayStart) / DateTimeUtils.DAY_MS).toInt()
            return if (diff == 1) "내일" else DateTimeUtils.formatDateLabel(targetMs)
        }

        private fun buildViews(context: Context, snap: Snapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val open = openAppIntent(context)
            views.setOnClickPendingIntent(android.R.id.background, open)
            views.setOnClickPendingIntent(R.id.widget_open, open)

            // 답장 영역 — 수>0이면 빨간 뱃지, 0이면 '답장할 고객 없음'(초록 완료). 위는 늘 뭔가 있으니 구분선1도 늘 보임.
            views.setViewVisibility(R.id.widget_div1, View.VISIBLE)
            if (snap.unanswered > 0) {
                views.setViewVisibility(R.id.widget_badge_box, View.VISIBLE)
                views.setViewVisibility(R.id.widget_badge_done, View.GONE)
                // 프로토처럼 숫자는 크게, 「건」은 작게.
                val cnt = "${snap.unanswered}"
                val cs = android.text.SpannableString("${cnt}건")
                cs.setSpan(android.text.style.RelativeSizeSpan(1.4f), 0, cnt.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                cs.setSpan(android.text.style.RelativeSizeSpan(0.7f), cnt.length, cs.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                views.setTextViewText(R.id.widget_unanswered_count, cs)
                views.setTextViewText(R.id.widget_unanswered_name, nameWithSuffix(snap.upName))
                views.setTextViewText(R.id.widget_unanswered_elapsed, snap.upElapsed)
                val msg = if (snap.upMsg.isBlank()) "" else "문자 · ${snap.upMsg}"
                views.setTextViewText(R.id.widget_unanswered_msg, msg)
                views.setViewVisibility(R.id.widget_unanswered_msg, if (msg.isBlank()) View.GONE else View.VISIBLE)
                views.setOnClickPendingIntent(R.id.widget_badge_box, open)
            } else {
                views.setViewVisibility(R.id.widget_badge_box, View.GONE)
                views.setViewVisibility(R.id.widget_badge_done, View.VISIBLE)
                // 둘째 줄: 오늘 현장 있으면 그걸 확인하라고, 없으면 쉬어도 된다고.
                views.setTextViewText(
                    R.id.widget_badge_done_sub2,
                    if (snap.today.isEmpty()) "한숨 돌려도 괜찮아요." else "이제 오늘 현장을 확인해보세요."
                )
                views.setOnClickPendingIntent(R.id.widget_badge_done, open)
            }

            // ── 오늘 갈 현장 본문 ──
            if (snap.today.isEmpty()) {
                views.setViewVisibility(R.id.widget_primary, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_count, "")
            } else {
                views.setViewVisibility(R.id.widget_primary, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                views.setTextViewText(R.id.widget_count, "오늘 예정 · ${snap.today.size}곳")
                val first = snap.today.first()
                renderSite(context, views, first)
                // 길찾기 → 이 현장 위치로 **바로**(설정한 네비). 앱 열기 아님. 미설치면 기본 지도로 구워 둠.
                val navIntent = NavApps.bakedNavIntent(context, snap.navApp, first.navAddress)
                if (navIntent != null) {
                    views.setOnClickPendingIntent(
                        R.id.widget_open,
                        PendingIntent.getActivity(
                            context, 1, navIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                } else {
                    views.setOnClickPendingIntent(R.id.widget_open, open)
                }
            }

            // ── 맨 아래: 이어서(오늘 다음) 또는 내일/다음 날짜 현장 ──
            val next = snap.next
            if (next == null) {
                views.setViewVisibility(R.id.widget_next_box, View.GONE)
                views.setViewVisibility(R.id.widget_div2, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_div2, View.VISIBLE)
                views.setViewVisibility(R.id.widget_next_box, View.VISIBLE)
                views.setTextViewText(R.id.widget_next_title, next.title)
                views.setTextViewText(R.id.widget_next_name, next.headline)
                views.setTextViewText(R.id.widget_next_region, next.region)
                views.setViewVisibility(R.id.widget_next_region, if (next.region.isBlank()) View.GONE else View.VISIBLE)
                views.setTextViewText(R.id.widget_next_unit, next.unit)
                views.setViewVisibility(R.id.widget_next_unit, if (next.unit.isBlank()) View.GONE else View.VISIBLE)
                if (next.timeLabel.isBlank()) {
                    views.setViewVisibility(R.id.widget_next_time, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_next_time, View.VISIBLE)
                    views.setTextViewText(R.id.widget_next_time, next.timeLabel)
                }
            }
            return views
        }

        /** 본문 현장 한 건을 카드에 그린다 — 칩·헤드라인·지역·동호수·메모 + 이름 없는 최소 현장이면 연파랑 카드. */
        private fun renderSite(context: Context, views: RemoteViews, s: Site) {
            // '오늘 시공' 칩 — 이름 없는 현장에서만
            views.setViewVisibility(R.id.widget_today_chip, if (s.nameAbsent) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.widget_name, s.headline)
            views.setTextViewTextSize(R.id.widget_name, TypedValue.COMPLEX_UNIT_SP, if (s.nameAbsent) 19f else 17f)
            // 시각(있을 때만) — "오전 9시"
            if (s.timeLabel.isBlank()) {
                views.setViewVisibility(R.id.widget_time, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_time, View.VISIBLE)
                views.setTextViewText(R.id.widget_time, s.timeLabel)
            }
            views.setTextViewText(R.id.widget_region, s.region)
            views.setViewVisibility(R.id.widget_region, if (s.region.isBlank()) View.GONE else View.VISIBLE)
            // 동·호수(있을 때만, 파랗게)
            views.setTextViewText(R.id.widget_unit, s.unit)
            views.setViewVisibility(R.id.widget_unit, if (s.unit.isBlank()) View.GONE else View.VISIBLE)
            // 현장 메모(있을 때만) — 건 memo 원문(📌비번 포함), 2줄 미리보기
            if (s.memo.isBlank()) {
                views.setViewVisibility(R.id.widget_memo, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_memo, View.VISIBLE)
                views.setTextViewText(R.id.widget_memo_text, s.memo)
            }
            // 시공부위(견적 itemsText)는 다음 단계.
            views.setViewVisibility(R.id.widget_worktype, View.GONE)
            // 이름 없고 메모도 없는 '최소 현장' → 연파랑 카드로 강조(프로토). 그 외엔 흰 바탕.
            val minimal = s.nameAbsent && s.memo.isBlank()
            if (minimal) {
                views.setInt(R.id.widget_site_card, "setBackgroundResource", R.drawable.widget_site_card_bg)
                val padH = dp(context, 13)
                val padV = dp(context, 12)
                views.setViewPadding(R.id.widget_site_card, padH, padV, padH, padV)
            } else {
                views.setInt(R.id.widget_site_card, "setBackgroundResource", 0)
                views.setViewPadding(R.id.widget_site_card, 0, 0, 0, 0)
            }
        }

        private fun dp(context: Context, v: Int): Int =
            (v * context.resources.displayMetrics.density).toInt()

        /** 이름이면 「고객님」을 붙이고(프로토), 번호면 그대로. */
        private fun nameWithSuffix(n: String): String =
            if (n.isNotBlank() && n.any { it.isLetter() }) "$n 고객님" else n

        /** "수원 영통구 광교…" → "수원 영통"(시/도 + 시/군/구, 접미 떼고 앞 2토큰). 프로토 지역 접두. */
        private fun regionPrefix(addr: String?): String {
            val toks = (addr ?: "").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (toks.isEmpty()) return ""
            val out = ArrayList<String>(2)
            out += toks[0].replace(Regex("(시|도)$"), "")
            if (toks.size > 1 && Regex(".*(시|군|구|읍|면|동|리)$").matches(toks[1])) {
                out += toks[1].replace(Regex("(시|군|구|읍|면|동|리)$"), "")
            }
            return out.joinToString(" ").trim()
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(context, 0, launch, flags)
        }
    }
}
