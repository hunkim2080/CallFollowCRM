package com.detailline.callfollowcrm.ai

import android.content.Context
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.service.NotificationHelper
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 팀원 출발 이벤트 폴링 + 알림 센터 (2026-06-06, 사장님 요청).
 *   "팀원이 [출발했어요] 누르면 사장 앱에 누가·몇시·어디로 출발했는지 알림 + 상담함에도 표시."
 *
 *   - 사장님 앱이 poll() 호출: 포그라운드 60초 루프(CallFollowCrmApplication) + 백그라운드 ReminderWorker.
 *   - 새 '출발' 이벤트 → 푸시 알림. 중복은 prefs.teamDepartLastSeenMs 로 차단.
 *   - todayDepartures = 상담함(HomeScreen) 배너가 구독 (오늘 출발한 팀원들).
 *   서버 team_member_events 가 단일 출처. 팀원은 앱 설치 X(링크 화면에서 [출발] 누름).
 */
class TeamEventCenter(
    private val teamRepository: TeamRepository,
    private val preferences: AppPreferences
) {
    private val _todayDepartures = MutableStateFlow<List<DepartureInfo>>(emptyList())
    val todayDepartures: StateFlow<List<DepartureInfo>> = _todayDepartures.asStateFlow()

    data class DepartureInfo(
        val eventId: Long,
        val memberName: String,
        val place: String,
        val timeLabel: String,
        val createdAtMs: Long
    )

    /** 서버에서 오늘 팀 이벤트를 받아 배너 갱신 + 새 출발은 알림. 어디서 호출해도 안전(실패는 조용히 무시). */
    suspend fun poll(context: Context) {
        val owner = preferences.bizPhone.trim()
        if (owner.isBlank()) return
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        val events = teamRepository.events(owner, sinceMs = todayStart, limit = 50).getOrNull() ?: return
        val departs = events.filter { it.eventType == "departed" }.sortedBy { it.createdAtMs }

        val infos = departs.map { e ->
            val place = e.payload?.optString("customer_label")?.takeIf { it.isNotBlank() && it != "null" }
                ?: e.payload?.optString("addr")?.takeIf { it.isNotBlank() && it != "null" }
                ?: "현장"
            DepartureInfo(
                eventId = e.eventId,
                memberName = e.memberName ?: "팀원",
                place = place,
                timeLabel = DateTimeUtils.formatShort(e.createdAtMs),
                createdAtMs = e.createdAtMs
            )
        }
        _todayDepartures.value = infos

        val lastSeen = preferences.teamDepartLastSeenMs
        val maxMs = departs.maxOfOrNull { it.createdAtMs } ?: 0L
        if (lastSeen == 0L) {
            // 첫 폴링 — 오늘 과거 출발들이 한꺼번에 알림으로 쏟아지는 것 방지. 배너만 채우고 기준점만 잡음.
            if (maxMs > 0L) preferences.teamDepartLastSeenMs = maxMs
            return
        }
        for (d in infos.filter { it.createdAtMs > lastSeen }) {
            NotificationHelper.showTeamDeparture(context, d.eventId, d.memberName, d.timeLabel, d.place)
        }
        if (maxMs > lastSeen) preferences.teamDepartLastSeenMs = maxMs
    }
}
