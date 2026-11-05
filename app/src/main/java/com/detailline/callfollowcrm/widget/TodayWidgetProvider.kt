package com.detailline.callfollowcrm.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.detailline.callfollowcrm.domain.ui.TodayWidgetDensity
import com.detailline.callfollowcrm.util.AddressExtractor
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Fixed v2 RemoteViews. Prototype: widget_v2_upgraded.html .wg / render(). API 29 safe. */
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        receiveRefresh(context, force = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        receiveRefresh(context, force = false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_REFRESH -> receiveRefresh(context, force = true)
            ACTION_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_BOOT_COMPLETED -> receiveRefresh(context, force = false, tick = true)
        }
    }

    private fun receiveRefresh(context: Context, force: Boolean, tick: Boolean = false) {
        val pending = goAsync()
        val app = context.applicationContext as CallFollowCrmApplication
        app.applicationScope.launch {
            try { update(app, force, tick) }
            catch (e: Exception) { android.util.Log.w("TodayWidget", "Refresh failed", e) }
            finally { pending.finish() }
        }
    }

    override fun onDisabled(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(tickPending(context))
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.detailline.callfollowcrm.WIDGET_REFRESH"
        private const val ACTION_TICK = "com.detailline.callfollowcrm.WIDGET_TICK"
        private val mutex = Mutex()
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        private data class Site(
            val headline: String, val region: String, val complex: String, val unit: String,
            val memo: String, val items: String, val timeLabel: String, val navAddress: String,
            val minutes: Int?, val day: Long, val completed: Boolean,
            val customerId: Long = 0, val shareId: String = "", val partner: String = ""
        )

        fun refresh(context: Context) {
            val app = context.applicationContext as? CallFollowCrmApplication ?: return
            app.applicationScope.launch {
                try { update(app, force = false, tick = false) }
                catch (e: Exception) { android.util.Log.w("TodayWidget", "Refresh failed", e) }
            }
        }

        private fun tickPending(context: Context) = PendingIntent.getBroadcast(context, 9,
            Intent(context, TodayWidgetProvider::class.java).setAction(ACTION_TICK), FLAGS)

        private suspend fun update(app: CallFollowCrmApplication, force: Boolean, tick: Boolean) = mutex.withLock {
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, TodayWidgetProvider::class.java))
            if (ids.isEmpty()) return@withLock
            val now = System.currentTimeMillis()
            val p = app.container.preferences
            val customers = app.container.customerRepository.allOnce()
            val jobs = app.container.jobRepository.allOnce().groupBy { it.customerId }
            val today = DateTimeUtils.startOfDay(now)
            val all = customers.flatMap { c ->
                val ownJobs = jobs[c.id].orEmpty()
                // Jobs are authoritative; a customer mirror is only a legacy fallback with no jobs.
                if (ownJobs.isEmpty()) listOf(siteOf(c, null))
                else ownJobs.filter { it.cancelledAt == null }.map { siteOf(c, it) }
            }.filter { it.day >= today }.toMutableList()

            // 협업 현장 = 일정 화면과 같은 소스(withMe)를 **매 갱신 직접** 당긴다(옛 위젯과 같은 방식).
            //   이 앱은 org.json 만 쓰고 Gson 의존성이 없어, SharedSite 를 JSON 캐시하지 않는다(§13 환각 패키지 방지).
            var collab = emptyList<SharedSiteRepository.SharedSite>()
            var networkOk = true
            if (p.bizPhone.isNotBlank()) {
                app.container.sharedSiteRepository.withMe(p.bizPhone, sinceMs = today, limit = 200)
                    .onSuccess { collab = it }
                    .onFailure { networkOk = false }
            }
            // Optimistic progress from the existing outbox path wins until the next server poll.
            val local = app.container.collabEventCenter.acceptedUpcoming.value.associateBy { it.shareId }
            collab = collab.map { s -> local[s.shareId]?.let { s.copy(progress = it.progress) } ?: s }
            all += collab.filter {
                it.status == "accepted" && it.scheduledAtMs >= today &&
                    it.shareId !in p.trashedSharedSiteIds && it.shareId !in p.hiddenCollabShareIds
            }.map { collabSiteOf(it) }
            val todaySites = all.filter { it.day == today }
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
            val arranged = TodayVisitOrder.arrange(todaySites.map { TodayVisitOrder.Keyed(it, it.minutes, it.completed) },
                cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE))
            var current = arranged.ordered.getOrNull(arranged.currentIndex)
            if (current != null && current.customerId > 0) {
                val docs = app.container.issuedDocRepository.observeByCustomer(current.customerId).first()
                val date = current.day
                val quote = docs.filter { it.kind == "quote" && it.workDateMs?.let(DateTimeUtils::startOfDay) == date }
                    .maxByOrNull { it.issuedAtMs }
                current = current.copy(items = quote?.itemsText.orEmpty())
            }
            val next = arranged.ordered.drop((arranged.currentIndex + 1).coerceAtLeast(0)).firstOrNull { !it.completed }
                ?: all.filter { it.day > today && !it.completed }.sortedWith(compareBy<Site> { it.day }.thenBy { it.minutes ?: Int.MAX_VALUE }).firstOrNull()
            if (!tick && networkOk) p.widgetUpdatedAt = now
            val v = buildViews(app, todaySites.size, current, next, today, now)
            ids.forEach { manager.updateAppWidget(it, v) }
            // Non-wakeup/inexact: no exact-alarm permission, no API 31 dependency. Doze can defer it.
            (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setInexactRepeating(
                AlarmManager.RTC, now + 60_000L, 60_000L, tickPending(app))
        }

        private fun siteOf(c: CustomerEntity, j: JobEntity?): Site {
            val addr = j?.address?.takeIf { it.isNotBlank() } ?: c.address
            val name = c.name?.takeIf { it.isNotBlank() && it.any(Char::isLetter) }
            val complex = AddressExtractor.complexName(addr).orEmpty()
            val minutes = DayOrder.jobMinutes(if (j != null) j.scheduledWorkMinutes else c.scheduledWorkMinutes).takeIf { it != DayOrder.UNKNOWN }
            return Site(
                headline = name?.let { "$it 고객님" } ?: complex.ifBlank { AddressExtractor.roughSite(addr).ifBlank { "현장" } },
                region = regionPrefix(addr), complex = if (name != null) complex else "",
                unit = unitOf(AddressExtractor.splitDongHo(addr)), memo = (j?.memo ?: c.memo).trim(), items = "",
                timeLabel = minutes?.let(DateTimeUtils::formatWorkMinutes).orEmpty(), navAddress = AddressExtractor.tidyAddress(addr),
                minutes = minutes, day = (if (j != null) j.scheduledWorkDate else c.scheduledWorkDate)?.let(DateTimeUtils::startOfDay) ?: 0,
                completed = j?.let(Completion::isDone) ?: Completion.isDone(c), customerId = c.id)
        }

        private fun collabSiteOf(s: SharedSiteRepository.SharedSite): Site {
            val minutes = DayOrder.collabMinutes(s.timeLabel, s.scheduledAtMs).takeIf { it != DayOrder.UNKNOWN }
            return Site(siteDisplayName(s), regionPrefix(s.addr), "", unitOf(AddressExtractor.splitDongHo(s.addr)),
                s.memo.orEmpty().trim(), s.workSummary.orEmpty(), minutes?.let(DateTimeUtils::formatWorkMinutes).orEmpty(),
                AddressExtractor.tidyAddress(s.addr), minutes, DateTimeUtils.startOfDay(s.scheduledAtMs),
                s.progress == SharedSiteRepository.Progress.COMPLETED, shareId = s.shareId, partner = s.ownerName)
        }

        private fun buildViews(app: CallFollowCrmApplication, count: Int, current: Site?, next: Site?, today: Long, now: Long): RemoteViews {
            val p = app.container.preferences
            val v = RemoteViews(app.packageName, R.layout.widget_today)
            fun show(id: Int, yes: Boolean) = v.setViewVisibility(id, if (yes) View.VISIBLE else View.GONE)
            fun txt(id: Int, value: String) = v.setTextViewText(id, value)
            val reply = p.widgetUnansweredCount > 0
            show(R.id.widget_reply_check, !reply); show(R.id.widget_unanswered_count, reply)
            show(R.id.widget_reply_preview, reply); show(R.id.widget_reply_done, !reply)
            txt(R.id.widget_reply_title, if (reply) "답장 안 함" else "답장할 고객 없음")
            val n = p.widgetUnansweredCount.toString()
            val badge = android.text.SpannableString("${n}건").apply {
                setSpan(android.text.style.AbsoluteSizeSpan(10, true), n.length, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            v.setTextViewText(R.id.widget_unanswered_count, badge)
            txt(R.id.widget_unanswered_name, p.widgetUnansweredName.takeIf { it.any(Char::isLetter) }?.let { "$it 고객님" } ?: "이름 미등록 고객")
            txt(R.id.widget_unanswered_elapsed, p.widgetUnansweredElapsed)
            txt(R.id.widget_unanswered_msg, p.widgetUnansweredMsg.takeIf { it.isNotBlank() }?.let { "문자 · $it" }.orEmpty())
            txt(R.id.widget_reply_done_sub, if (current == null) "한숨 돌려도 괜찮아요." else "이제 오늘 현장을 확인해보세요.")
            // 답장 안 함 탭 → **그 고객 대화로 바로**(답장하러). 번호 없으면 상담함 폴백. (2026-11-05 사장님 "눌러서 바로 답장")
            v.setOnClickPendingIntent(R.id.widget_reply,
                if (p.widgetUnansweredPhone.isNotBlank()) chatPending(app, p.widgetUnansweredPhone, p.widgetUnansweredCustomerId)
                else routePending(app, "inbox"))
            txt(R.id.widget_count, if (count == 0) "" else "· ${count}곳")
            txt(R.id.widget_synced, if (p.widgetUpdatedAt == 0L) "" else {
                val mins = ((now - p.widgetUpdatedAt) / 60_000L).coerceAtLeast(0)
                if (mins == 0L) "방금" else "${mins}분 전"
            })
            v.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(app, 8,
                Intent(app, TodayWidgetProvider::class.java).setAction(ACTION_WIDGET_REFRESH), FLAGS))
            show(R.id.widget_primary, current != null); show(R.id.widget_empty, current == null)
            txt(R.id.widget_empty_sub, if (reply) "기다리는 고객에게\n여유 있게 답장해보세요." else "오늘은 여유 있게,\n다음 일정을 확인해보세요.")
            v.setOnClickPendingIntent(R.id.widget_schedule, routePending(app, "schedule"))
            if (current != null) {
                val density = TodayWidgetDensity.of(current.memo, current.items)
                txt(R.id.widget_name, current.headline); txt(R.id.widget_time, current.timeLabel)
                show(R.id.widget_time, current.timeLabel.isNotBlank())
                txt(R.id.widget_region, current.region)
                txt(R.id.widget_collab_chip, "🤝 협업 · ${current.partner}")
                show(R.id.widget_collab_chip, current.shareId.isNotBlank())
                show(R.id.widget_featured_address, density.featured && (current.unit.isNotBlank() || current.complex.isNotBlank()))
                show(R.id.widget_compact_address, !density.featured && (current.unit.isNotBlank() || current.complex.isNotBlank()))
                txt(R.id.widget_complex, current.complex); show(R.id.widget_complex, current.complex.isNotBlank())
                txt(R.id.widget_unit, current.unit); show(R.id.widget_unit, current.unit.isNotBlank())
                txt(R.id.widget_compact_complex, current.complex); show(R.id.widget_compact_complex, current.complex.isNotBlank())
                txt(R.id.widget_compact_unit, current.unit); show(R.id.widget_compact_unit, current.unit.isNotBlank())
                show(R.id.widget_memo, !density.featured); txt(R.id.widget_memo_text, current.memo)
                v.setInt(R.id.widget_memo_text, "setMaxLines", density.memoLines)
                txt(R.id.widget_items_text, current.items); show(R.id.widget_items, current.items.isNotBlank())
                show(R.id.widget_motif, density.featured)
                v.setInt(R.id.widget_site_card, "setBackgroundResource", if (density.featured) R.drawable.widget_site_card_bg else android.R.color.transparent)
                val h = if (density.featured) dp(app, 13) else 0
                val pad = if (density.featured) dp(app, 11) else 0
                v.setViewPadding(R.id.widget_card_content, h, pad, h, pad)
                v.setViewPadding(R.id.widget_items, 0, 0, if (density.featured) dp(app, 62) else 0, 0)
                v.setOnClickPendingIntent(R.id.widget_site_card, detailPending(app, current))
                txt(R.id.widget_open, if (current.shareId.isBlank()) "길찾기" else "길찾기 — 누르면 출발")
                v.setOnClickPendingIntent(R.id.widget_open, navigationPending(app, current.navAddress, current.shareId))
            }
            txt(R.id.widget_next_label, when {
                next == null || next.day == today -> "이어서"
                next.day == DateTimeUtils.startOfDay(today + 36 * 60 * 60_000L) -> "내일"
                else -> DateTimeUtils.formatShort(next.day)
            })
            txt(R.id.widget_next_name, next?.headline ?: "예정된 현장이 없어요")
            txt(R.id.widget_next_sub, next?.let { listOf(if (it.day > today) DateTimeUtils.formatShort(it.day) else "", it.region, it.unit).filter(String::isNotBlank).joinToString(" · ") }.orEmpty())
            txt(R.id.widget_next_time, next?.timeLabel.orEmpty())
            show(R.id.widget_next_collab, next?.shareId?.isNotBlank() == true)
            v.setOnClickPendingIntent(R.id.widget_next, next?.let { detailPending(app, it) } ?: routePending(app, "schedule"))
            // 🏠 집으로 길찾기 — 주소 있으면 바로 네비, 없으면 **집 주소 입력창으로 바로** 보낸다. (2026-11-05 사장님)
            v.setOnClickPendingIntent(R.id.widget_home, if (p.homeAddress.isBlank()) routePending(app, "home_address") else navigationPending(app, p.homeAddress))
            return v
        }

        private fun detailPending(context: Context, s: Site): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = if (s.shareId.isNotBlank()) MainActivity.ACTION_COLLAB_SITE else MainActivity.ACTION_CUSTOMER
                putExtra(MainActivity.EXTRA_SHARE_ID, s.shareId); putExtra(MainActivity.EXTRA_CUSTOMER_ID, s.customerId)
                data = android.net.Uri.parse("sgmwidget://detail/${s.customerId}/${android.net.Uri.encode(s.shareId)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(context, 20, intent, FLAGS)
        }

        /** 🏠 설정 '홈 화면에 위젯 추가' → 시스템 위젯 추가창을 바로 띄운다(한 탭 설치). 런처가 미지원이면 안내만. (2026-11-05 사장님: "위젯? 하고 설치해보게") */
        fun requestPin(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val comp = ComponentName(context, TodayWidgetProvider::class.java)
            val supported = android.os.Build.VERSION.SDK_INT >= 26 && runCatching { mgr.isRequestPinAppWidgetSupported }.getOrDefault(false)
            if (supported) runCatching { mgr.requestPinAppWidget(comp, null, null) }
                .onFailure { android.widget.Toast.makeText(context, "홈 화면을 길게 눌러 위젯에서 '시공막내'를 추가해주세요.", android.widget.Toast.LENGTH_LONG).show() }
            else android.widget.Toast.makeText(context, "홈 화면을 길게 눌러 위젯에서 '시공막내'를 추가해주세요.", android.widget.Toast.LENGTH_LONG).show()
        }

        /** 답장 안 함 → 그 고객 대화(ACTION_CHAT). 번호별로 다른 PendingIntent 가 되게 data 로 구분. */
        private fun chatPending(context: Context, phone: String, customerId: Long) = PendingIntent.getActivity(context, 22,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_CHAT
                putExtra(MainActivity.EXTRA_PHONE_NUMBER, phone)
                if (customerId > 0) putExtra(MainActivity.EXTRA_CUSTOMER_ID, customerId)
                data = android.net.Uri.parse("sgmwidget://chat/${android.net.Uri.encode(phone)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, FLAGS)

        private fun routePending(context: Context, route: String) = PendingIntent.getActivity(context, route.hashCode(),
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_WIDGET_ROUTE).putExtra("widget_route", route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), FLAGS)

        private fun navigationPending(context: Context, address: String, shareId: String = "") = PendingIntent.getActivity(context, 21,
            Intent(context, WidgetNavigationActivity::class.java).apply {
                data = android.net.Uri.parse("sgmwidget://nav/${android.net.Uri.encode(address)}/${android.net.Uri.encode(shareId)}")
                putExtra("address", address); putExtra("share_id", shareId)
            }, FLAGS)

        private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
        private fun unitOf(parts: AddressExtractor.Parts): String = when {
            parts.dong != null && parts.ho != null -> "${parts.dong}동 ${parts.ho}호"
            parts.ho != null -> "${parts.ho}호"
            parts.dong != null -> "${parts.dong}동"
            else -> ""
        }

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

    }
}
