package com.detailline.callfollowcrm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.MainActivity
import com.detailline.callfollowcrm.R
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.ai.siteDisplayName
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import com.detailline.callfollowcrm.domain.job.Completion
import com.detailline.callfollowcrm.domain.schedule.DayOrder
import com.detailline.callfollowcrm.domain.schedule.TodayVisitOrder
import com.detailline.callfollowcrm.util.AddressExtractor
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.NavApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🧱 '오늘 갈 현장 + 답장 안 함' 홈 위젯 — 2026-11-05 사장님 개편(리스트형 컬렉션 위젯).
 *
 *   앱을 안 열어도 홈 화면에서 **오늘 시공 현장 전부(협업 포함)**와 **답장 안 함**을 본다.
 *   - 오늘 목록 = 일정 '이 날 시공'과 같은 소스: 내 건(customers+jobs) + 협업(sharedSiteRepository.withMe).
 *   - 줄마다 그 고객으로: 답장 안 함 → 그 대화(ACTION_CHAT) · 현장 줄 → 고객 상세(ACTION_CUSTOMER)/협업(ACTION_COLLAB_SITE).
 *   - 길찾기는 **'지금 갈 곳' 하나만**(정적 카드). 완료/다음 시간이면 다음 현장으로 자동 이동(TodayVisitOrder).
 *   - 맨 위 ⟳ 새로고침(ACTION_WIDGET_REFRESH 브로드캐스트) + 갱신 시각.
 *   '이따 갈 곳'은 컬렉션(ListView) — 데이터는 여기서 prefs(widgetTodayJson)에 적고 TodayWidgetService 가 읽는다.
 *
 *   ⚠️ 갱신은 AppWidgetManager.updateAppWidget 로 **직접** 그린다(ACTION_APPWIDGET_UPDATE 는 시스템 보호).
 *      리스트는 updateAppWidget 뒤 notifyAppWidgetViewDataChanged 로 다시 읽힌다.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snap = loadSnapshot(appCtx)
                val views = buildViews(appCtx, snap)
                ids.forEach { manager.updateAppWidget(it, views) }
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_today_list)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 🔧 ⟳ 새로고침 / 강제 갱신 — 앱 안에서 보낼 수 있는 커스텀 액션(보호 브로드캐스트 아님).
        if (intent.action == ACTION_WIDGET_REFRESH) refresh(context)
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.detailline.callfollowcrm.WIDGET_REFRESH"

        /** 위젯 현장 한 줄 — 내 건이면 customerId, 협업이면 shareId 로 딥링크. */
        private data class Site(
            val headline: String,
            val region: String,
            val unit: String,
            val memo: String,
            val timeLabel: String,
            val navAddress: String,
            val nameAbsent: Boolean,
            val minutes: Int?,       // 정렬용(시각 미정 null)
            val completed: Boolean,
            val isCollab: Boolean,
            val customerId: Long,    // 내 건 (협업=0)
            val shareId: String      // 협업 (내 건="")
        )

        private data class Snapshot(
            val today: List<Site>,       // 시간순(안 끝난 것 먼저). 지금 갈 곳 = today[currentIndex].
            val currentIndex: Int,       // -1 = 지금 갈 곳 없음(전부 완료/빈 날)
            val navApp: String = "",
            val updatedAtMs: Long = System.currentTimeMillis(),
            val unanswered: Int = 0,
            val upName: String = "",
            val upElapsed: String = "",
            val upMsg: String = "",
            val upPhone: String = "",
            val upCustomerId: Long = 0
        )

        /** 어디서든 위젯을 다시 그린다 — 일정 바뀜·완료·자정·⟳ 에서 부른다. (직접 updateAppWidget + 리스트 재로드) */
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
                runCatching { mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_today_list) }
            }
        }

        private suspend fun loadSnapshot(context: Context): Snapshot {
            val app = context.applicationContext as? CallFollowCrmApplication ?: return Snapshot(emptyList(), -1)
            val customers = runCatching { app.container.customerRepository.allOnce() }.getOrDefault(emptyList())
            // 🧱 현장 메모·시각·주소는 그 날짜의 건(job)이 진짜(§13④) — jobs.memo=현장 메모(📌비번 포함).
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

            // ① 내 건(own) — 오늘 날짜 고객
            val ownSites = customers
                .filter { c -> c.scheduledWorkDate?.let { it in todayStart until todayEnd } == true }
                .map { siteOf(it, jobForDate(it)) }

            // ② 협업(collab) — 일정 화면과 같은 소스(sharedSiteRepository.withMe). 네트워크·실패 시 빈 목록(graceful degrade).
            //    일정 화면과 같은 필터: accepted · 시각 있음 · 버린 것/숨긴 것 제외 · 오늘.
            val p = app.container.preferences
            val bizPhone = runCatching { p.bizPhone.trim() }.getOrDefault("")
            val trashed = runCatching { p.trashedSharedSiteIds }.getOrDefault(emptySet())
            val hidden = runCatching { p.hiddenCollabShareIds }.getOrDefault(emptySet())
            val collabSites: List<Site> = if (bizPhone.isBlank()) emptyList() else runCatching {
                app.container.sharedSiteRepository.withMe(bizPhone).getOrDefault(emptyList())
                    .filter { s ->
                        s.status == "accepted" && s.scheduledAtMs > 0L &&
                            s.shareId !in trashed && s.shareId !in hidden &&
                            DateTimeUtils.startOfDay(s.scheduledAtMs) == todayStart
                    }
                    .map { collabSiteOf(it) }
            }.getOrDefault(emptyList())

            // ③ 합쳐서 시간순 + 지금 갈 곳 고르기 (완료한 것은 아래로, 지금 갈 곳 = 안 끝난 것 중 가장 이른 것)
            val all = ownSites + collabSites
            val arranged = TodayVisitOrder.arrange(all.map { TodayVisitOrder.Keyed(it, it.minutes, it.completed) })

            // ④ '이따 갈 곳'(지금 갈 곳 제외)을 리스트용 캐시에 적는다 — TodayWidgetService 가 읽는다.
            val later = arranged.ordered.filterIndexed { i, _ -> i != arranged.currentIndex }
            writeTodayCache(app, later)

            return Snapshot(
                today = arranged.ordered,
                currentIndex = arranged.currentIndex,
                navApp = runCatching { p.navApp }.getOrDefault(""),
                updatedAtMs = System.currentTimeMillis(),
                unanswered = runCatching { p.widgetUnansweredCount }.getOrDefault(0),
                upName = runCatching { p.widgetUnansweredName }.getOrDefault(""),
                upElapsed = runCatching { p.widgetUnansweredElapsed }.getOrDefault(""),
                upMsg = runCatching { p.widgetUnansweredMsg }.getOrDefault(""),
                upPhone = runCatching { p.widgetUnansweredPhone }.getOrDefault(""),
                upCustomerId = runCatching { p.widgetUnansweredCustomerId }.getOrDefault(0L)
            )
        }

        /** '이따 갈 곳' 목록을 prefs JSON 으로 — 컬렉션 서비스(TodayWidgetService)가 읽을 유일한 자리. */
        private fun writeTodayCache(app: CallFollowCrmApplication, rows: List<Site>) {
            val arr = JSONArray()
            rows.forEach { s ->
                arr.put(JSONObject().apply {
                    put("t", s.timeLabel)
                    put("h", s.headline)
                    put("s", listOf(s.region, s.unit).filter { it.isNotBlank() }.joinToString(" · "))
                    put("d", if (s.completed) 1 else 0)
                    put("cl", if (s.isCollab) 1 else 0)
                    put("ci", s.customerId)
                    put("si", s.shareId)
                })
            }
            runCatching { app.container.preferences.widgetTodayJson = arr.toString() }
        }

        /**
         * 내 고객(+그 날짜의 건) → 위젯 현장 한 줄. 주소 쪼개기는 AddressExtractor(공용) 한 곳, 시각·날짜는 DateTimeUtils — 두 벌 금지(§12).
         */
        private fun siteOf(c: CustomerEntity, job: JobEntity?): Site {
            val addr = job?.address?.takeIf { it.isNotBlank() } ?: c.address
            val memoRaw = job?.memo?.takeIf { it.isNotBlank() } ?: c.memo
            val min = DayOrder.jobMinutes(job?.scheduledWorkMinutes ?: c.scheduledWorkMinutes)
            val complex = AddressExtractor.complexName(addr)
            val reg = regionPrefix(addr)
            val parts = AddressExtractor.splitDongHo(addr)
            val unit = unitOf(parts)
            val nm = c.name?.takeIf { s -> s.isNotBlank() && s.any { it.isLetter() } }
            val headline = when {
                nm != null -> "$nm 고객님"
                !complex.isNullOrBlank() -> complex
                else -> AddressExtractor.roughSite(addr).ifBlank { addr?.takeIf { it.isNotBlank() } ?: reg }
            }
            val region = if (nm != null) {
                listOfNotNull(complex?.takeIf { it.isNotBlank() }, reg.takeIf { it.isNotBlank() }).joinToString(" · ")
            } else reg
            return Site(
                headline = headline,
                region = region,
                unit = unit,
                memo = memoRaw.trim(),
                timeLabel = min.takeIf { it != DayOrder.UNKNOWN }?.let { DateTimeUtils.formatWorkMinutes(it) } ?: "",
                navAddress = AddressExtractor.tidyAddress(addr),
                nameAbsent = nm == null,
                minutes = min.takeIf { it != DayOrder.UNKNOWN },
                completed = job?.let { Completion.isDone(it) } ?: Completion.isDone(c),
                isCollab = false,
                customerId = c.id,
                shareId = ""
            )
        }

        /** 협업 현장(공유받음) → 위젯 현장 한 줄. 고객 번호 없음 — shareId 로만 딥링크(협업 현장 상세). */
        private fun collabSiteOf(s: SharedSiteRepository.SharedSite): Site {
            val min = DayOrder.collabMinutes(s.timeLabel, s.scheduledAtMs)
            val parts = AddressExtractor.splitDongHo(s.addr)
            return Site(
                headline = siteDisplayName(s),
                region = regionPrefix(s.addr),
                unit = unitOf(parts),
                memo = s.memo?.trim().orEmpty(),
                timeLabel = min.takeIf { it != DayOrder.UNKNOWN }?.let { DateTimeUtils.formatWorkMinutes(it) } ?: "",
                navAddress = AddressExtractor.tidyAddress(s.addr),
                nameAbsent = true,
                minutes = min.takeIf { it != DayOrder.UNKNOWN },
                completed = s.progress == SharedSiteRepository.Progress.COMPLETED,
                isCollab = true,
                customerId = 0L,
                shareId = s.shareId
            )
        }

        private fun unitOf(parts: AddressExtractor.Parts): String = when {
            parts.dong != null && parts.ho != null -> "${parts.dong}동 ${parts.ho}호"
            parts.ho != null -> "${parts.ho}호"
            parts.dong != null -> "${parts.dong}동"
            else -> ""
        }

        private fun buildViews(context: Context, snap: Snapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val open = openAppIntent(context)
            views.setOnClickPendingIntent(android.R.id.background, open)

            // ── 답장 안 함 뱃지 / 완료 ──
            views.setViewVisibility(R.id.widget_div1, View.VISIBLE)
            if (snap.unanswered > 0) {
                views.setViewVisibility(R.id.widget_badge_box, View.VISIBLE)
                views.setViewVisibility(R.id.widget_badge_done, View.GONE)
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
                // 👉 답장 안 함 탭 → 그 고객 대화(답장하러). 번호 없으면 앱 열기 폴백.
                views.setOnClickPendingIntent(R.id.widget_badge_box, chatPending(context, snap.upPhone, snap.upCustomerId) ?: open)
            } else {
                views.setViewVisibility(R.id.widget_badge_box, View.GONE)
                views.setViewVisibility(R.id.widget_badge_done, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_badge_done_sub2,
                    if (snap.today.isEmpty()) "한숨 돌려도 괜찮아요." else "이제 오늘 현장을 확인해보세요."
                )
                views.setOnClickPendingIntent(R.id.widget_badge_done, open)
            }

            // ── 머리줄: N곳 + 갱신 시각 + ⟳ ──
            views.setTextViewText(R.id.widget_count, if (snap.today.isEmpty()) "" else "· ${snap.today.size}곳")
            views.setTextViewText(R.id.widget_synced, DateTimeUtils.timeLabel(snap.updatedAtMs))
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending(context))

            val current = snap.currentIndex.takeIf { it >= 0 }?.let { snap.today.getOrNull(it) }

            // ── 지금 갈 곳 (정적 카드) ──
            if (current == null) {
                views.setViewVisibility(R.id.widget_primary, View.GONE)
                views.setViewVisibility(R.id.widget_empty, if (snap.today.isEmpty()) View.VISIBLE else View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_primary, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                renderCurrent(context, views, current)
                // 카드 탭 → 그 고객 상세(내 건)/협업 현장(협업)
                views.setOnClickPendingIntent(R.id.widget_site_card, detailPending(context, current))
                // 길찾기 → 이 현장 위치로 바로(설정한 네비). 미설치면 기본 지도.
                val navIntent = NavApps.bakedNavIntent(context, snap.navApp, current.navAddress)
                views.setOnClickPendingIntent(
                    R.id.widget_open,
                    if (navIntent != null) PendingIntent.getActivity(
                        context, 1, navIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ) else detailPending(context, current)
                )
            }

            // ── 이따 갈 곳 (스크롤 리스트) ──
            val laterCount = snap.today.size - (if (current != null) 1 else 0)
            if (laterCount > 0) {
                views.setViewVisibility(R.id.widget_sub_label, View.VISIBLE)
                views.setTextViewText(R.id.widget_sub_label, if (current != null) "이따 갈 곳" else "오늘 현장")
                views.setViewVisibility(R.id.widget_today_list, View.VISIBLE)
                val svc = Intent(context, TodayWidgetService::class.java)
                svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))  // 어댑터가 다른 RemoteViews 와 안 섞이게
                views.setRemoteAdapter(R.id.widget_today_list, svc)
                views.setEmptyView(R.id.widget_today_list, R.id.widget_empty_list)
                views.setPendingIntentTemplate(R.id.widget_today_list, listTemplate(context))
            } else {
                views.setViewVisibility(R.id.widget_sub_label, View.GONE)
                views.setViewVisibility(R.id.widget_today_list, View.GONE)
            }
            return views
        }

        /** '지금 갈 곳' 카드 그리기 — 칩·헤드라인·시각·지역·동호수·메모. 현재 현장은 연파랑 카드로 강조. */
        private fun renderCurrent(context: Context, views: RemoteViews, s: Site) {
            views.setViewVisibility(R.id.widget_today_chip, View.VISIBLE)
            views.setTextViewText(R.id.widget_name, s.headline)
            views.setTextViewTextSize(R.id.widget_name, TypedValue.COMPLEX_UNIT_SP, 17f)
            if (s.timeLabel.isBlank()) {
                views.setViewVisibility(R.id.widget_time, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_time, View.VISIBLE)
                views.setTextViewText(R.id.widget_time, s.timeLabel)
            }
            views.setTextViewText(R.id.widget_region, s.region)
            views.setViewVisibility(R.id.widget_region, if (s.region.isBlank()) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.widget_unit, s.unit)
            views.setViewVisibility(R.id.widget_unit, if (s.unit.isBlank()) View.GONE else View.VISIBLE)
            if (s.memo.isBlank()) {
                views.setViewVisibility(R.id.widget_memo, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_memo, View.VISIBLE)
                views.setTextViewText(R.id.widget_memo_text, s.memo)
            }
            views.setViewVisibility(R.id.widget_worktype, View.GONE)
            // 현재 현장은 연파랑 카드로 강조.
            views.setInt(R.id.widget_site_card, "setBackgroundResource", R.drawable.widget_site_card_bg)
            val padH = dp(context, 13)
            val padV = dp(context, 12)
            views.setViewPadding(R.id.widget_site_card, padH, padV, padH, padV)
        }

        /** 그 고객 상세(내 건)/협업 현장(협업)로 여는 PendingIntent. */
        private fun detailPending(context: Context, s: Site): PendingIntent {
            val i = Intent(context, MainActivity::class.java).apply {
                if (s.isCollab) {
                    action = MainActivity.ACTION_COLLAB_SITE
                    putExtra(MainActivity.EXTRA_SHARE_ID, s.shareId)
                } else {
                    action = MainActivity.ACTION_CUSTOMER
                    putExtra(MainActivity.EXTRA_CUSTOMER_ID, s.customerId)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val rc = if (s.isCollab) 300 + (s.shareId.hashCode() and 0xFFFF) else 200 + (s.customerId.toInt() and 0xFFFF)
            return PendingIntent.getActivity(
                context, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** 답장 안 함 → 그 고객 대화. 번호 없으면 null(폴백). */
        private fun chatPending(context: Context, phone: String, customerId: Long): PendingIntent? {
            if (phone.isBlank()) return null
            val i = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_CHAT
                putExtra(MainActivity.EXTRA_PHONE_NUMBER, phone)
                if (customerId > 0) putExtra(MainActivity.EXTRA_CUSTOMER_ID, customerId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context, 2, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** ⟳ 새로고침 — provider 로 커스텀 브로드캐스트. */
        private fun refreshPending(context: Context): PendingIntent {
            val i = Intent(context, TodayWidgetProvider::class.java).apply { action = ACTION_WIDGET_REFRESH }
            return PendingIntent.getBroadcast(
                context, 8, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** 리스트 줄 탭 템플릿 — action 없이(각 줄 fill-in 이 ACTION_CUSTOMER/COLLAB_SITE 를 채운다). FLAG_MUTABLE 필수. */
        private fun listTemplate(context: Context): PendingIntent {
            val i = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context, 7, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        private fun dp(context: Context, v: Int): Int =
            (v * context.resources.displayMetrics.density).toInt()

        private fun nameWithSuffix(n: String): String =
            if (n.isNotBlank() && n.any { it.isLetter() }) "$n 고객님" else n

        /** "수원 영통구 광교…" → "수원 영통"(시/도 + 시/군/구, 접미 떼고 앞 2토큰). */
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
