package com.detailline.callfollowcrm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 시공 현장 5km 진입 감지 → 도착 안내 알림 (2026-06-03).
 *   GeofenceManager 가 등록한 "site_{customerId}" 펜스 ENTER 시 호출.
 *   같은 고객·같은 날 1회만 (reminderNotifiedKeys "arrival:{id}:{date}").
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val ids = event.triggeringGeofences?.mapNotNull { it.requestId } ?: return
        if (ids.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? CallFollowCrmApplication ?: return@launch
                val container = app.container
                val prefs = container.preferences
                val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
                for (rid in ids) {
                    // §E 협업 3km 진입 — 서버에 arrived(auto) 발사 → A "거의 도착" + B "알려드렸어요"(서버 푸시).
                    if (rid.startsWith(GeofenceManager.COLLAB_PREFIX)) {
                        val shareId = rid.removePrefix(GeofenceManager.COLLAB_PREFIX)
                        val key = "collab_arrived:$shareId"
                        if (key in prefs.reminderNotifiedKeys) continue // 1회만
                        val myPhone = prefs.bizPhone.filter { it.isDigit() }
                        if (myPhone.length < 9) continue
                        val ok = container.sharedSiteRepository
                            .progress(shareId, myPhone, com.detailline.callfollowcrm.ai.SharedSiteRepository.Progress.ARRIVED, auto = true)
                            .isSuccess
                        if (ok) {
                            prefs.reminderNotifiedKeys = prefs.reminderNotifiedKeys + key
                            GeofenceManager.removeCollabArrival(context, shareId) // 다 왔으니 펜스 정리
                        }
                        continue
                    }
                    // 본인 현장 5km 도착 안내 — 토글 ON 일 때만.
                    if (!prefs.arrivalAutoEnabled) continue
                    val id = rid.removePrefix("site_").toLongOrNull() ?: continue
                    val key = "arrival:$id:$todayStart"
                    // 5km 진입 사실을 기록 → 홈 "도착 안내" 카드가 이때부터 노출 (토글 ON 전제).
                    prefs.arrivalEnteredKeys = prefs.arrivalEnteredKeys + key
                    // 알림은 같은 고객·같은 날 1회만.
                    if (key in prefs.reminderNotifiedKeys) continue
                    val c = container.customerRepository.findById(id) ?: continue
                    val nm = c.name?.takeIf { it.isNotBlank() } ?: c.phoneNumber
                    NotificationHelper.showArrival(context, id, c.phoneNumber, nm)
                    prefs.reminderNotifiedKeys = prefs.reminderNotifiedKeys + key
                }
            } finally {
                pending.finish()
            }
        }
    }
}
