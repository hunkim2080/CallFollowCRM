package com.detailline.callfollowcrm.presentation.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * MainActivity 가 외부에서 받은 intent(deep link, 공유 등)를 Compose 트리에 전달할 때 사용.
 */
class NavEvents {
    private val channel = Channel<NavEvent>(Channel.BUFFERED)
    val events = channel.receiveAsFlow()

    fun requestFollowUp(phoneNumber: String, callRecordId: Long? = null, templateId: Long? = null) {
        channel.trySend(NavEvent.OpenFollowUp(phoneNumber, callRecordId, templateId))
    }

    fun requestChat(phoneNumber: String, customerId: Long? = null) {
        channel.trySend(NavEvent.OpenChat(phoneNumber, customerId))
    }

    fun requestCallSummary(phoneNumber: String, name: String? = null) {
        channel.trySend(NavEvent.OpenCallSummary(phoneNumber, name))
    }

    fun requestClosingBrief() {
        channel.trySend(NavEvent.OpenClosingBrief)
    }

    /** 협업 현장 공유 링크(App Link) 탭 → 협업 현장 화면 (shareId 있으면 그 현장 바로 열기).
     *   tab="shared" 면 "내가 공유한 현장" 탭부터 — 협업 수락/진행 알림은 주인(A)이 받는데, 그 현장은
     *   A 의 '받은 현장' 목록엔 없어서 받은목록을 열면 빈 화면이 뜸 → A 의 '내가 공유한 현장' 으로 보냄. (2026-06-20 사장님) */
    fun requestCollabSites(shareId: String? = null, tab: String? = null) {
        channel.trySend(NavEvent.OpenCollabSites(shareId, tab))
    }

    /** 특정 고객 상세로 바로 (협업 댓글/사진 알림 탭 → 주인은 늘 쓰는 고객정보 협업 탭으로). (2026-07-02 사장님) */
    fun requestCustomerDetail(customerId: Long) {
        channel.trySend(NavEvent.OpenCustomerDetail(customerId))
    }

    /** 팀원 진행 알림(출발/도착/완료) 탭 → 팀 관리(현황) 화면. (2026-08-13 알림 위치점검) */
    fun requestTeam() { channel.trySend(NavEvent.OpenTeam) }

    /** '정기문자 보낼 때 됐어요' 알림 탭 → 정기문자 검토 화면. (2026-08-13 알림 위치점검) */
    fun requestRecurringDue() { channel.trySend(NavEvent.OpenRecurringDue) }
}

sealed interface NavEvent {
    data class OpenFollowUp(
        val phoneNumber: String,
        val callRecordId: Long? = null,
        val templateId: Long? = null
    ) : NavEvent
    /** 알림(quiet 후속 안내) 탭 시 ChatScreen 으로 직진. */
    data class OpenChat(
        val phoneNumber: String,
        val customerId: Long? = null
    ) : NavEvent
    /** 통화 직후 카드 [통화 정리해서 보내기] → CallSummaryScreen. */
    data class OpenCallSummary(
        val phoneNumber: String,
        val name: String? = null
    ) : NavEvent
    /** "오늘 하루 마감 브리핑" 알림 탭 → 마감 브리핑 화면. */
    object OpenClosingBrief : NavEvent
    /** 협업 현장 공유 링크 → 협업 현장 화면. shareId 있으면 그 현장 상세 자동 열기. tab="shared" 면 내가 공유한 현장 탭부터. */
    data class OpenCollabSites(val shareId: String? = null, val tab: String? = null) : NavEvent
    /** 특정 고객 상세로 바로. (협업 댓글/사진 알림 탭 → 고객정보 협업 탭) */
    data class OpenCustomerDetail(val customerId: Long) : NavEvent
    /** 팀원 진행 알림 탭 → 팀 관리(현황). */
    object OpenTeam : NavEvent
    /** 정기문자 due 알림 탭 → 정기문자 검토. */
    object OpenRecurringDue : NavEvent
}
