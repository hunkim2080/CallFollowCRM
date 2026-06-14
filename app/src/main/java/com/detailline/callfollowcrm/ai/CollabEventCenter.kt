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

    /** 받은 협업 요청(pending) — 상담함 "받은 협업 요청" 카드용. pollInvites 가 채움.
     *   (2026-06-14 사장님: 푸시 알림을 실수로 밀어 지워도 여기서 다시 찾아 수락하게) */
    private val _pendingInvites = MutableStateFlow<List<SharedSiteRepository.SharedSite>>(emptyList())
    val pendingInvites: StateFlow<List<SharedSiteRepository.SharedSite>> = _pendingInvites.asStateFlow()

    /** 수락한(accepted) 협업 현장 중 오늘 이후 = 내 '다음 일'. 홈에 협업 현장 카드로 노출. (2026-06-14 사장님) */
    private val _acceptedUpcoming = MutableStateFlow<List<SharedSiteRepository.SharedSite>>(emptyList())
    val acceptedUpcoming: StateFlow<List<SharedSiteRepository.SharedSite>> = _acceptedUpcoming.asStateFlow()

    data class CollabUpdate(
        val eventId: String,
        val shareId: String,
        val kind: String,
        val partnerName: String,
        val title: String,
        val timeLabel: String,
        val createdAtMs: Long,
        val accountText: String? = null,
        val accountNo: String? = null,   // 계좌번호만 (복사용)
        val accountBank: String? = null, // 은행명
        val accountHolder: String? = null, // 예금주
        val dailyWage: Int? = null       // 보낼 금액(만원)
    )

    suspend fun poll(context: Context) {
        val owner = preferences.bizPhone.trim()
        if (owner.isBlank()) return
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        val events = sharedSiteRepository.ownerEvents(owner, sinceMs = todayStart, limit = 80).getOrNull() ?: return
        val tracked = events
            .filter { it.step in KINDS && it.atMs > 0L }
            .sortedBy { it.atMs }

        // 내가 초대할 때 저장한 로컬 배정(customerId|phone|name|shareId)에서 shareId→상대 이름 매핑.
        //   서버 partner_name 이 일반값("협업 사장")이어도, A 는 누구한테 보냈는지 알므로 로컬 이름으로 특정. (2026-06-14 사장님)
        val nameByShareId = HashMap<String, String>()
        for (entry in preferences.collabAssignments) {
            val p = entry.split('|')
            if (p.size >= 4) {
                val nm = p[2].trim(); val sid = p[3].trim()
                if (sid.isNotEmpty() && nm.isNotEmpty()) nameByShareId[sid] = nm
            }
        }
        val infos = tracked.map { e ->
            val acc = e.account
            val accBank = acc?.optString("bank")?.takeIf { it.isNotBlank() && it != "null" }
            val accountNoOnly = acc?.optString("account_no")?.takeIf { it.isNotBlank() && it != "null" }
            val accHolder = acc?.optString("holder")?.takeIf { it.isNotBlank() && it != "null" }
            val accountText = listOfNotNull(accBank, accountNoOnly, accHolder).joinToString(" ").takeIf { it.isNotBlank() }
            CollabUpdate(
                eventId = e.eventId,
                shareId = e.shareId,
                kind = e.step,
                partnerName = nameByShareId[e.shareId]?.takeIf { it.isNotBlank() } ?: e.partnerName,
                title = e.title,
                timeLabel = DateTimeUtils.formatShort(e.atMs),
                createdAtMs = e.atMs,
                accountText = accountText,
                accountNo = accountNoOnly,
                accountBank = accBank,
                accountHolder = accHolder,
                dailyWage = e.dailyWage
            )
        }
        _todayUpdates.value = infos

        val lastSeen = preferences.collabEventLastSeenMs
        val maxMs = tracked.maxOfOrNull { it.atMs } ?: 0L
        if (lastSeen == 0L) {
            if (maxMs > 0L) preferences.collabEventLastSeenMs = maxMs
            return
        }
        // 폭주·묵은알림 방지:
        //   ① 6시간 지난 옛 진행 이벤트는 알림 X(이미 지난 출발/도착은 알려도 의미 없음 — 옛 테스트 이벤트 surface 차단).
        //   ② 앱이 한동안 꺼졌어도 한 폴당 최대 5개(최신순)만. 나머지는 조용히 lastSeen 넘김.
        val recentCutoff = System.currentTimeMillis() - 6 * 60 * 60 * 1000L
        val newOnes = infos.filter { it.createdAtMs > lastSeen && it.createdAtMs >= recentCutoff }.sortedBy { it.createdAtMs }
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
        _pendingInvites.value = pending   // 상담함 "받은 협업 요청" 카드 — 응답하면 다음 폴에서 자동으로 빠짐
        // 수락한 협업 중 오늘 이후(날짜 있는 것) = 내 '다음 일' → 홈 협업 카드.
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        _acceptedUpcoming.value = sites
            .filter { it.status == "accepted" && it.scheduledAtMs >= todayStart }
            .sortedBy { it.scheduledAtMs }
        val pendingIds = pending.map { it.shareId }.toSet()
        // 수락 유효시간(12h) 앵커: 처음 본 시각 기록(서버 created_at_ms 폴백). 응답해 사라진 건 정리.
        preferences.syncCollabInviteFirstSeen(pendingIds, System.currentTimeMillis())

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

    /**
     * B 가 수락/거절하면 즉시 호출 — 다음 폴(주기)을 기다리지 않고 상담함 카드·하단 뱃지에서 그 초대를 빼고
     *   떠 있던 "수락하러 가기" 알림도 지운다. seen 유지로 재알림 방지. (2026-06-14 버그: 수락해도 카드/알림 남음)
     */
    fun markResponded(context: Context, shareId: String) {
        _pendingInvites.value = _pendingInvites.value.filter { it.shareId != shareId }
        preferences.seenCollabInviteShareIds = preferences.seenCollabInviteShareIds + shareId
        NotificationHelper.cancelCollabInvite(context, shareId)
    }

    companion object {
        private val KINDS = setOf("departed", "arrived", "completed")
    }
}
