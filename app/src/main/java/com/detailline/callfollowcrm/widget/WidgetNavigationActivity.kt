package com.detailline.callfollowcrm.widget

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.util.NavApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Activity PendingIntent avoids a background broadcast -> activity trampoline on newer phones. */
class WidgetNavigationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CallFollowCrmApplication
        val address = intent.getStringExtra("address").orEmpty()
        val shareId = intent.getStringExtra("share_id").orEmpty()
        app.applicationScope.launch {
            val departure = runCatching {
                if (shareId.isNotBlank()) depart(app, shareId)
            }
            withContext(Dispatchers.Main) {
                if (departure.isFailure) Toast.makeText(app, "출발을 저장하지 못했어요. 다시 눌러주세요.", Toast.LENGTH_LONG).show()
                val nav = NavApps.bakedNavIntent(app, app.container.preferences.navApp, address)
                val opened = nav != null && runCatching { startActivity(nav) }.isSuccess
                if (!opened) Toast.makeText(app, "길찾기를 열 수 없어요. 주소와 지도 앱을 확인해주세요.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    companion object {
        private val departureMutex = Mutex()
        private suspend fun depart(app: CallFollowCrmApplication, shareId: String) = departureMutex.withLock {
            val c = app.container
            // 메모리(acceptedUpcoming) 우선, 없으면(프로세스 재시작 등) withMe 로 그 현장만 찾는다. Gson 캐시 안 씀(org.json 앱, §13).
            val site = c.collabEventCenter.acceptedUpcoming.value.firstOrNull { it.shareId == shareId }
                ?: c.sharedSiteRepository.withMe(c.preferences.bizPhone).getOrNull()?.firstOrNull { it.shareId == shareId }
                ?: error("현장을 찾을 수 없음")
            // Repeated navigation never moves ARRIVED/COMPLETED backwards or resends departure.
            if (site.progress != SharedSiteRepository.Progress.ASSIGNED || site.status != "accepted") return@withLock
            val payload = JSONObject().apply {
                put("step", "departed"); put("partnerPhone", c.preferences.bizPhone)
                put("partnerName", c.preferences.bizName)
            }
            c.outbox.enqueue(OutboxKind.COLLAB_PROGRESS, shareId, payload.toString())
            c.collabEventCenter.updateLocalProgress(shareId, SharedSiteRepository.Progress.DEPARTED)
            c.outbox.tryNow()
            TodayWidgetProvider.refresh(app)
        }
    }
}
