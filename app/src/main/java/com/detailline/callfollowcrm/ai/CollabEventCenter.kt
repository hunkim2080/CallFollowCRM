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
        // 폭주 방지: 앱이 한동안 꺼져 있었거나 옛 이벤트가 쌓여 있으면 한 번에 수십 개가 터질 수 있음.
        //   → 새 이벤트는 한 폴당 최대 5개(최신순)만 알림, 나머지는 조용히 lastSeen 넘김.
        val newOnes = infos.filter { it.createdAtMs > lastSeen }.sortedBy { it.createdAtMs }
        for (u in newOnes.takeLast(5)) {
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

    /**
     * 받은 협업 요청(pending) 폴링 + 알림 — 상대 사장이 나에게 현장을 공유 요청하면 "수락하시겠어요?" 알림.
     *   서버 변경 불필요: with-me 응답에 이미 status="pending" 건이 들어옴(캘린더는 accepted 만 쓰지만 pending 도 옴).
     *   설치/업데이트 직후 옛 대기 건 블라스트 방지 위해 첫 폴은 조용히 시드만. (2026-06-12 사장님 요청)
     */
    suspend fun pollInvites(context: Context) {
        val owner = preferences.bizPhone.trim()
        if (owner.isBlank()) return
        val sites = sharedSiteRepository.withMe(owner).getOrNull() ?: return
        val pending = sites.filter { it.status == "pending" }
        val pendingIds = pending.map { it.shareId }.toSet()

        if (!preferences.collabInviteSeeded) {
            preferences.seenCollabInviteShareIds = pendingIds
            preferences.collabInviteSeeded = true
            return
        }
        val seen = preferences.seenCollabInviteShareIds
        for (s in pending.filter { it.shareId !in seen }) {
            NotificationHelper.showCollabInvite(context, s.shareId, s.ownerName, s.title)
        }
        // 현재 pending 전체로 갱신 — 수락/거절돼 사라진 건 빠지므로 재초대 시 다시 알림.
        preferences.seenCollabInviteShareIds = pendingIds
    }

    companion object {
        private val KINDS = setOf("departed", "arrived", "completed")
    }
}
