package com.detailline.callfollowcrm.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.MainActivity
import com.detailline.callfollowcrm.R
import org.json.JSONArray

/**
 * '이따 갈 곳' 스크롤 리스트의 데이터 공급자(컬렉션 위젯). (2026-11-05 사장님)
 *
 *   줄마다 그 고객으로 딥링크: 내 건 → ACTION_CUSTOMER(고객 상세), 협업 → ACTION_COLLAB_SITE(협업 현장).
 *   목록은 TodayWidgetProvider 가 로드해 prefs(widgetTodayJson)에 적어둔 걸 **읽기만** 한다 — 네트워크 중복 호출 0.
 *   (지금 갈 곳 하나는 provider 의 정적 카드에 있고, 여기엔 그 뒤의 현장만 온다.)
 */
class TodayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodayWidgetFactory(applicationContext)
}

private class TodayWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private data class Row(
        val time: String, val title: String, val sub: String,
        val done: Boolean, val collab: Boolean, val customerId: Long, val shareId: String
    )

    private var rows: List<Row> = emptyList()

    override fun onCreate() {}
    override fun onDataSetChanged() { rows = load() }
    override fun onDestroy() { rows = emptyList() }
    override fun getCount(): Int = rows.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_today_item)
        val r = rows.getOrNull(position) ?: return v
        v.setTextViewText(R.id.widget_item_time, r.time)
        v.setViewVisibility(R.id.widget_item_time, if (r.time.isBlank()) View.INVISIBLE else View.VISIBLE)
        v.setTextViewText(R.id.widget_item_title, r.title)
        v.setTextColor(R.id.widget_item_title, if (r.done) 0xFF9AA3B0.toInt() else 0xFF202E42.toInt())
        v.setTextViewText(R.id.widget_item_sub, r.sub)
        v.setViewVisibility(R.id.widget_item_sub, if (r.sub.isBlank()) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.widget_item_collab, if (r.collab) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.widget_item_done, if (r.done) View.VISIBLE else View.GONE)
        // 탭 → 그 고객 상세(내 건) 또는 협업 현장(협업). 템플릿(provider.setPendingIntentTemplate)에 action 이 없어 fill-in 이 채운다.
        val fill = Intent().apply {
            if (r.collab) {
                action = MainActivity.ACTION_COLLAB_SITE
                putExtra(MainActivity.EXTRA_SHARE_ID, r.shareId)
            } else {
                action = MainActivity.ACTION_CUSTOMER
                putExtra(MainActivity.EXTRA_CUSTOMER_ID, r.customerId)
            }
        }
        v.setOnClickFillInIntent(R.id.widget_item_root, fill)
        return v
    }

    private fun load(): List<Row> {
        val app = context.applicationContext as? CallFollowCrmApplication ?: return emptyList()
        val json = runCatching { app.container.preferences.widgetTodayJson }.getOrDefault("")
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Row(
                    time = o.optString("t"),
                    title = o.optString("h"),
                    sub = o.optString("s"),
                    done = o.optInt("d") == 1,
                    collab = o.optInt("cl") == 1,
                    customerId = o.optLong("ci"),
                    shareId = o.optString("si")
                )
            }
        }.getOrDefault(emptyList())
    }
}
