package com.detailline.callfollowcrm.ai

import android.content.Context
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.service.NotificationHelper
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 팀원 진행 이벤트 폴링 + 알림 센터 (2026-06-06, 사장님 요청).
 *   "팀원이 [출발/도착/완료] 누르면 사장 앱에 누가·몇시·어디서 무엇을 했는지 알림 + 상담함에도 표시."
 *
 *   - 사장님 앱이 poll() 호출: 포그라운드 60초 루프(CallFollowCrmApplication) + 백그라운드 ReminderWorker.
 *   - 새 출발/도착/완료 이벤트 → 푸시 알림. 중복은 prefs.teamEventLastSeenMs 로 차단(모든 종류 공통 타임라인).
 *   - todayUpdates = 상담함(HomeScreen) 배너가 구독 (오늘 팀원 진행들).
 *   서버 team_member_events 가 단일 출처. 팀원은 앱 설치 X(링크 화면에서 버튼 누름).
 */
class TeamEventCenter(
    private val teamRepository: TeamRepository,
    private val preferences: AppPreferences
) {
    private val _todayUpdates = MutableStateFlow<List<TeamUpdate>>(emptyList())
    val todayUpdates: StateFlow<List<TeamUpdate>> = _todayUpdates.asStateFlow()

    /** kind = "departed" | "arrived" | "completed" | "note". note 면 text 에 메모 내용. */
    data class TeamUpdate(
        val eventId: Long,
        val kind: String,
        val memberName: String,
        val place: String,
        val timeLabel: String,
        val createdAtMs: Long,
        val text: String? = null
    )

    /** 서버에서 오늘 팀 이벤트를 받아 배너 갱신 + 새 이벤트는 알림. 실패는 조용히 무시. */
    suspend fun poll(context: Context) {
        val owner = preferences.bizPhone.trim()
        if (owner.isBlank()) return
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        val events = teamRepository.events(owner, sinceMs = todayStart, limit = 80).getOrNull() ?: return
        val tracked = events
            .filter { it.eventType in KINDS }
            .sortedBy { it.createdAtMs }

        val infos = tracked.map { e ->
            val place = e.payload?.optString("customer_label")?.takeIf { it.isNotBlank() && it != "null" }
                ?: e.payload?.optString("addr")?.takeIf { it.isNotBlank() && it != "null" }
                ?: "현장"
            val text = if (e.eventType == "note")
                e.payload?.optString("text")?.takeIf { it.isNotBlank() && it != "null" } else null
            TeamUpdate(
                eventId = e.eventId,
                kind = e.eventType,
                memberName = e.memberName ?: "팀원",
                place = place,
                timeLabel = DateTimeUtils.formatShort(e.createdAtMs),
                createdAtMs = e.createdAtMs,
                text = text
            )
        }
        _todayUpdates.value = infos

        val lastSeen = preferences.teamEventLastSeenMs
        val maxMs = tracked.maxOfOrNull { it.createdAtMs } ?: 0L
        if (lastSeen == 0L) {
            // 첫 폴링 — 오늘 과거 이벤트가 한꺼번에 알림으로 쏟아지는 것 방지. 배너만 채우고 기준점만 잡음.
            if (maxMs > 0L) preferences.teamEventLastSeenMs = maxMs
            return
        }
        for (u in infos.filter { it.createdAtMs > lastSeen }) {
            NotificationHelper.showTeamEvent(context, u.eventId, u.kind, u.memberName, u.timeLabel, u.place, u.text)
        }
        if (maxMs > lastSeen) preferences.teamEventLastSeenMs = maxMs
    }

    companion object {
        private val KINDS = setOf("departed", "arrived", "completed", "note")
    }
}
