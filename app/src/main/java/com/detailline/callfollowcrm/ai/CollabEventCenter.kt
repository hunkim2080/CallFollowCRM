package com.detailline.callfollowcrm.ai

import android.content.Context
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.service.NotificationHelper
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 협업 현장 진행 이벤트 폴링 + 알림 센터.
 * 서버 `GET /api/shared/owner-events` 가 열리면 A(현장 주인) 앱에 출발/도착/완료를 알려준다.
 */
class CollabEventCenter(
    private val sharedSiteRepository: SharedSiteRepository,
    private val preferences: AppPreferences
) {
    private val _todayUpdates = MutableStateFlow<List<CollabUpdate>>(emptyList())
    val todayUpdates: StateFlow<List<CollabUpdate>> = _todayUpdates.asStateFlow()

    data class CollabUpdate(
        val eventId: String,
        val shareId: String,
        val kind: String,
        val partnerName: String,
        val title: String,
        val timeLabel: String,
        val createdAtMs: Long,
        val accountText: String? = null
    )

    suspend fun poll(context: Context) {
        val owner = preferences.bizPhone.trim()
        if (owner.isBlank()) return
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        val events = sharedSiteRepository.ownerEvents(owner, sinceMs = todayStart, limit = 80).getOrNull() ?: return
        val tracked = events
            .filter { it.step in KINDS && it.atMs > 0L }
            .sortedBy { it.atMs }

        val infos = tracked.map { e ->
            val accountText = e.account?.let { a ->
                val bank = a.optString("bank").takeIf { it.isNotBlank() && it != "null" }
                val no = a.optString("account_no").takeIf { it.isNotBlank() && it != "null" }
                val holder = a.optString("holder").takeIf { it.isNotBlank() && it != "null" }
                listOfNotNull(bank, no, holder).joinToString(" ").takeIf { it.isNotBlank() }
            }
            CollabUpdate(
                eventId = e.eventId,
                shareId = e.shareId,
                kind = e.step,
                partnerName = e.partnerName,
                title = e.title,
                timeLabel = DateTimeUtils.formatShort(e.atMs),
                createdAtMs = e.atMs,
                accountText = accountText
            )
        }
        _todayUpdates.value = infos

        val lastSeen = preferences.collabEventLastSeenMs
        val maxMs = tracked.maxOfOrNull { it.atMs } ?: 0L
        if (lastSeen == 0L) {
            if (maxMs > 0L) preferences.collabEventLastSeenMs = maxMs
            return
        }
        for (u in infos.filter { it.createdAtMs > lastSeen }) {
            NotificationHelper.showCollabEvent(
                context = context,
                eventId = u.eventId,
                shareId = u.shareId,
                kind = u.kind,
                partnerName = u.partnerName,
                timeLabel = u.timeLabel,
                title = u.title,
                accountText = u.accountText
            )
        }
        if (maxMs > lastSeen) preferences.collabEventLastSeenMs = maxMs
    }

    companion object {
        private val KINDS = setOf("departed", "arrived", "completed")
    }
}
