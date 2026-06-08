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

    /** 협업 현장 공유 링크(App Link) 탭 → 협업 현장 화면 (shareId 있으면 그 현장 바로 열기). */
    fun requestCollabSites(shareId: String? = null) {
        channel.trySend(NavEvent.OpenCollabSites(shareId))
    }
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
    /** 협업 현장 공유 링크 → 협업 현장 화면. shareId 있으면 그 현장 상세 자동 열기. */
    data class OpenCollabSites(val shareId: String? = null) : NavEvent
}
