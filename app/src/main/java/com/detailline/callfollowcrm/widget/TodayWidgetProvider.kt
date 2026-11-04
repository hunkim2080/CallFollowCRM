package com.detailline.callfollowcrm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
 * 🧱 '오늘 갈 현장' 홈 위젯 (Phase 1) — 2026-11-04 사장님.
 *
 *   앱을 안 열어도 홈 화면에서 **오늘 시공 현장**이 보이게. 값은 **로컬 일정(Room)** 에서 직접 읽어
 *   화면(ViewModel) 없이도 정확하다. 새 라이브러리(Glance) 안 쓰고 RemoteViews.
 *
 *   ⏱️ 자동 갱신은 30분(구글 최소 updatePeriodMillis). 그 사이는 앱이 [refresh] 로 깨운다
 *      (일정 바뀜·완료·자정). 협업 현장 + '답장 안 함' 뱃지는 Phase 2.
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
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun loadSnapshot(context: Context): Snapshot {
        val app = context as? CallFollowCrmApplication
            ?: context.applicationContext as? CallFollowCrmApplication
            ?: return Snapshot(emptyList())
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
        return Snapshot(rows)
    }

    private fun buildViews(context: Context, snap: Snapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        val open = openAppIntent(context)
        views.setOnClickPendingIntent(android.R.id.background, open)
        views.setOnClickPendingIntent(R.id.widget_open, open)

        if (snap.rows.isEmpty()) {
            views.setViewVisibility(R.id.widget_primary, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_count, "")
            return views
        }
        views.setViewVisibility(R.id.widget_primary, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
        views.setTextViewText(R.id.widget_count, "${snap.rows.size}곳")

        val first = snap.rows.first()
        views.setTextViewText(R.id.widget_name, first.name)
        views.setTextViewText(R.id.widget_region, first.region)
        // own job 은 정확한 시각 필드가 없어 v1 은 시각 칸을 비운다(협업·시각은 Phase 2).
        views.setViewVisibility(R.id.widget_time, android.view.View.GONE)

        val rest = snap.rows.drop(1)
        if (rest.isEmpty()) {
            views.setViewVisibility(R.id.widget_next, android.view.View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_next, android.view.View.VISIBLE)
            val label = rest.joinToString("  ·  ") { it.name }
            views.setTextViewText(R.id.widget_next, "다음  $label")
        }
        return views
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, launch, flags)
    }

    private data class Row(val name: String, val region: String)
    private data class Snapshot(val rows: List<Row>)

    companion object {
        /** 어디서든 위젯을 다시 그리게 — 일정 바뀜·완료·자정 등 이벤트에서 부른다(없으면 30분 자동). */
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching {
                mgr.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            }.getOrNull() ?: return
            if (ids.isEmpty()) return
            val intent = Intent(context, TodayWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
