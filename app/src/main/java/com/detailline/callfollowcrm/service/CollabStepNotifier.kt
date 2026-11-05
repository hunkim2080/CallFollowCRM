package com.detailline.callfollowcrm.service

import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.detailline.callfollowcrm.R
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.domain.collab.CollabProgressSteps
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 🤝 협업 진행 알림의 **쿠팡 배송추적 스텝** — 현장마다 알림 하나, 단계로만 앞으로. (2026-11-05 사장님 "프로토랑 디자인이 달라")
 *
 *   하는 일 둘:
 *    1) [bump] — 이 현장(shareId)이 어디까지 왔나(reached)·단계별 도달 시각(times)을 prefs 에 기억. 전진 때만 울리게.
 *    2) [buildView] — 펼친 알림 본문에 점·막대·✓·시각을 프로토(32vgR4Lz)대로 그린 커스텀 뷰.
 *
 *   NotificationHelper 가 2천 줄 넘어, 이 기능만 잎으로 떼어 둔다(§12-F). 부르는 곳은 NotificationHelper.showCollabEvent 하나.
 *   단계 이름은 [CollabProgressSteps.labels] 한 곳에서만(두 벌 금지 §12).
 */
internal object CollabStepNotifier {

    /** 같은 순간 FCM·폴링이 같은 현장을 올려도 읽고-쓰기가 한 번에(§13③ 연타). */
    private val lock = Any()

    /** 이 현장의 진행 상태 — 어디까지 왔나(reached)·이번에 전진했나(advanced)·단계별 도달 시각(times: order→ms). */
    data class Bump(val reached: Int, val advanced: Boolean, val times: Map<Int, Long>)

    /**
     * 현장(shareId)을 방금 온 단계로 올린다 — **단계 전진 때만** advanced=true.
     *   advanced=false 면 부르는 쪽이 재알림을 멈춘다(뒤늦게 온 낮은 단계·같은 단계 재게시 → 두 번 안 울림).
     *   기억: `{shareId:{"r":단계,"t":{"1":ms,…}}}` (옛 int 형식도 읽음).
     */
    fun bump(prefs: AppPreferences, shareId: String, stepOrder: Int): Bump {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val root = runCatching { org.json.JSONObject(prefs.collabProgressJson.ifBlank { "{}" }) }
                .getOrDefault(org.json.JSONObject())
            val entry = root.optJSONObject(shareId)
            val old = entry?.optInt("r", 0) ?: root.optInt(shareId, 0)   // 옛 형식은 int
            val times = HashMap<Int, Long>()
            entry?.optJSONObject("t")?.let { t ->
                t.keys().forEach { k -> k.toIntOrNull()?.let { times[it] = t.optLong(k) } }
            }
            if (stepOrder <= old) return Bump(old, false, times)
            times[stepOrder] = now
            val tObj = org.json.JSONObject().apply { times.forEach { (k, v) -> put(k.toString(), v) } }
            val next = org.json.JSONObject().put("r", stepOrder).put("t", tObj)
            root.put(shareId, next)
            // 너무 커지지 않게(완료된 옛 현장 등) — 넘치면 통째로 비운다. 기억 리셋이라 재발 알림이 한 번 더 뜰 뿐 무해.
            prefs.collabProgressJson =
                if (root.length() > 400) org.json.JSONObject().put(shareId, next).toString() else root.toString()
            return Bump(stepOrder, true, times)
        }
    }

    /**
     * 읽기 전용 — 이 현장이 어디까지 왔나(reached)·단계별 시각(times). 인앱 알림센터 스텝 카드가 쓴다.
     *   bump 와 **같은 JSON 한 곳**(prefs.collabProgressJson)에서 읽는다 — 진행 기억은 한 벌(§12).
     *   기록이 없거나(아직/리셋) reached 0 이면 null.
     */
    fun progressOf(prefs: AppPreferences, shareId: String): Bump? = synchronized(lock) {
        val root = runCatching { org.json.JSONObject(prefs.collabProgressJson.ifBlank { "{}" }) }
            .getOrDefault(org.json.JSONObject())
        val entry = root.optJSONObject(shareId)
        val reached = entry?.optInt("r", 0) ?: root.optInt(shareId, 0)   // 옛 int 형식도 읽음
        if (reached <= 0) return null
        val times = HashMap<Int, Long>()
        entry?.optJSONObject("t")?.let { t ->
            t.keys().forEach { k -> k.toIntOrNull()?.let { times[it] = t.optLong(k) } }
        }
        return Bump(reached, false, times)
    }

    /** 스텝 카드 본문 한 줄 — 짧게(제목에 주소가 이미 있어 중복 피함). 프로토 32vgR4Lz 본문 그대로. 모르는 kind 면 fallback. */
    fun cardBody(kind: String, partnerName: String, auto: Boolean, fallback: String): String {
        val who = partnerName.ifBlank { "협업 사장" }
        return when (kind) {
            "accepted" -> "👥 ${who} 사장님이 수락했어요 — 함께 가요!"
            "departed" -> "🚗 ${who} 사장님이 현장으로 출발했어요"
            "arrived" -> if (auto) "📍 ${who} 사장님이 거의 도착했어요" else "📍 ${who} 사장님이 현장에 도착했어요"
            "completed" -> "✅ ${who} 사장님이 작업을 끝냈어요"
            else -> fallback
        }
    }

    /**
     * 펼친 알림 본문 — 쿠팡 스텝 그림. DecoratedCustomViewStyle 이 위에 앱 머리를 붙이므로 여기선 제목·스텝·본문 한 줄만.
     *   점: 지난 단계 초록✓ · 지금 단계 초록테두리 · 아직 회색. 완료(4)만 보라✓. 막대: 도달 구간 초록.
     *   그리기 실패(기기별 inflate 문제)면 null → 부르는 쪽이 글자 막대 폴백으로 간다(안 깨짐).
     */
    fun buildView(context: Context, title: String, body: String, reached: Int, times: Map<Int, Long>): RemoteViews? =
        runCatching {
            val rv = RemoteViews(context.packageName, R.layout.notification_collab_step)
            rv.setTextViewText(R.id.collab_step_title, title)
            rv.setTextViewText(R.id.collab_step_body, body)
            val green = ContextCompat.getColor(context, R.color.collab_step_green)
            val track = ContextCompat.getColor(context, R.color.collab_step_track)
            val labelOn = ContextCompat.getColor(context, R.color.collab_step_label_on)
            val labelOff = ContextCompat.getColor(context, R.color.collab_step_label_off)
            val purple = ContextCompat.getColor(context, R.color.collab_step_purple)
            val dotIds = intArrayOf(R.id.collab_dot0, R.id.collab_dot1, R.id.collab_dot2, R.id.collab_dot3)
            val lbIds = intArrayOf(R.id.collab_lb0, R.id.collab_lb1, R.id.collab_lb2, R.id.collab_lb3)
            val tmIds = intArrayOf(R.id.collab_tm0, R.id.collab_tm1, R.id.collab_tm2, R.id.collab_tm3)
            val barL = intArrayOf(R.id.collab_bar0L, R.id.collab_bar1L, R.id.collab_bar2L, R.id.collab_bar3L)
            val barR = intArrayOf(R.id.collab_bar0R, R.id.collab_bar1R, R.id.collab_bar2R, R.id.collab_bar3R)
            // 점·막대·라벨 상태는 **공용 셈** CollabProgressSteps.cells 한 곳에서(센터 Compose 와 같은 함수 — 두 벌 금지 §12).
            val cells = CollabProgressSteps.cells(reached)
            rv.setInt(barL[0], "setBackgroundColor", Color.TRANSPARENT)   // 양 끝 동강은 막대 없음
            rv.setInt(barR[3], "setBackgroundColor", Color.TRANSPARENT)
            for (i in 0..3) {
                val c = cells[i]
                val dotRes = when (c.dot) {
                    CollabProgressSteps.DotState.DONE -> R.drawable.noti_dot_done
                    CollabProgressSteps.DotState.DONE_FINAL -> R.drawable.noti_dot_done_purple
                    CollabProgressSteps.DotState.CURRENT -> R.drawable.noti_dot_current
                    CollabProgressSteps.DotState.PENDING -> R.drawable.noti_dot_pending
                }
                rv.setInt(dotIds[i], "setBackgroundResource", dotRes)
                rv.setTextViewText(dotIds[i], if (c.dot == CollabProgressSteps.DotState.DONE || c.dot == CollabProgressSteps.DotState.DONE_FINAL) "✓" else "")
                rv.setTextViewText(lbIds[i], c.label)
                rv.setTextColor(lbIds[i], when (c.dot) {
                    CollabProgressSteps.DotState.DONE_FINAL -> purple
                    CollabProgressSteps.DotState.PENDING -> labelOff
                    else -> labelOn
                })
                val t = times[i + 1]
                rv.setTextViewText(tmIds[i], if (t != null) DateTimeUtils.formatTime(t) else "")
                if (i > 0) rv.setInt(barL[i], "setBackgroundColor", if (c.barLeftOn) green else track)
                if (i < 3) rv.setInt(barR[i], "setBackgroundColor", if (c.barRightOn) green else track)
            }
            rv
        }.getOrNull()
}
