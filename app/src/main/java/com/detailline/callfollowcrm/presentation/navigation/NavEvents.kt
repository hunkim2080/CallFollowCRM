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
}
