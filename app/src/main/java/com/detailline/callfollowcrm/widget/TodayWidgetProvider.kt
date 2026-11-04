package com.detailline.callfollowcrm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.MainActivity
import com.detailline.callfollowcrm.R
import com.detailline.callfollowcrm.util.AddressExtractor
import com.detailline.callfollowcrm.util.DateTimeUtils
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

        private data class Row(val name: String, val region: String)
        private data class Snapshot(
            val rows: List<Row>,
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
            val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
            val todayEnd = todayStart + DateTimeUtils.DAY_MS
            val rows = customers
                .filter { c -> c.scheduledWorkDate?.let { it in todayStart until todayEnd } == true }
                .sortedWith(compareBy({ it.scheduledWorkDate }, { it.name ?: it.phoneNumber }))
                .map { c ->
                    val two = AddressExtractor.twoLine(c.address)
                    val region = two.first.takeIf { it.isNotBlank() } ?: (c.address ?: "")
                    Row(name = c.name?.takeIf { it.isNotBlank() } ?: c.phoneNumber, region = region)
                }
            // 답장 안 함 수 + 대표 미답 고객 미리보기 — 홈 화면이 센 마지막 값(저장). 위젯은 다시 안 센다(두 벌 방지).
            val p = app.container.preferences
            return Snapshot(
                rows,
                runCatching { p.widgetUnansweredCount }.getOrDefault(0),
                runCatching { p.widgetUnansweredName }.getOrDefault(""),
                runCatching { p.widgetUnansweredElapsed }.getOrDefault(""),
                runCatching { p.widgetUnansweredMsg }.getOrDefault("")
            )
        }

        private fun buildViews(context: Context, snap: Snapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val open = openAppIntent(context)
            views.setOnClickPendingIntent(android.R.id.background, open)
            views.setOnClickPendingIntent(R.id.widget_open, open)

            // 답장 안 함 뱃지(프로토) — N건 + 고객님 + 「문자 · 」 메시지. 뱃지 있으면 구분선1도.
            if (snap.unanswered > 0) {
                views.setViewVisibility(R.id.widget_badge_box, View.VISIBLE)
                views.setViewVisibility(R.id.widget_div1, View.VISIBLE)
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
                views.setViewVisibility(R.id.widget_div1, View.GONE)
            }

            if (snap.rows.isEmpty()) {
                views.setViewVisibility(R.id.widget_primary, View.GONE)
                views.setViewVisibility(R.id.widget_next_box, View.GONE)
                views.setViewVisibility(R.id.widget_div2, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_count, "")
                return views
            }
            views.setViewVisibility(R.id.widget_primary, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            views.setTextViewText(R.id.widget_count, "다음 일정 · 총 ${snap.rows.size}곳")

            val first = snap.rows.first()
            views.setTextViewText(R.id.widget_name, nameWithSuffix(first.name))
            views.setTextViewText(R.id.widget_region, first.region)
            views.setViewVisibility(R.id.widget_region, if (first.region.isBlank()) View.GONE else View.VISIBLE)
            // 내 현장은 시각·시공부위 데이터가 없어 숨긴다(협업·건 데이터 붙으면 참).
            views.setViewVisibility(R.id.widget_time, View.GONE)
            views.setViewVisibility(R.id.widget_worktype, View.GONE)

            val next = snap.rows.drop(1).firstOrNull()
            if (next == null) {
                views.setViewVisibility(R.id.widget_next_box, View.GONE)
                views.setViewVisibility(R.id.widget_div2, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_div2, View.VISIBLE)
                views.setViewVisibility(R.id.widget_next_box, View.VISIBLE)
                views.setTextViewText(R.id.widget_next_name, nameWithSuffix(next.name))
                views.setTextViewText(R.id.widget_next_region, next.region)
                views.setViewVisibility(R.id.widget_next_region, if (next.region.isBlank()) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.widget_next_time, View.GONE)
            }
            return views
        }

        /** 이름이면 「고객님」을 붙이고(프로토), 번호면 그대로. */
        private fun nameWithSuffix(n: String): String =
            if (n.isNotBlank() && n.any { it.isLetter() }) "$n 고객님" else n

        private fun openAppIntent(context: Context): PendingIntent {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(context, 0, launch, flags)
        }
    }
}
