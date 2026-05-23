package com.detailline.callfollowcrm.presentation.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.domain.model.CustomerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    // v1: 설정은 영속화 단순. 토글 상태는 AppPreferences 에서 즉시 읽고 쓰기.
    private val _state = MutableStateFlow(
        SettingsUiState(
            afterCallBehavior = AfterCallBehavior.NOTIFY,
            defaultStatus = CustomerStatus.NEW_INQUIRY,
            receivedSmsEnabled = container.preferences.receivedSmsEnabled,
            autoFirstReplyEnabled = container.preferences.autoFirstReplyEnabled,
            firstReplyIncomingTemplateId = container.preferences.firstReplyIncomingTemplateId,
            firstReplyMissedTemplateId = container.preferences.firstReplyMissedTemplateId,
            quickActionTemplateId1 = container.preferences.quickActionTemplateId1,
            quickActionTemplateId2 = container.preferences.quickActionTemplateId2,
            quickActionTemplateId3 = container.preferences.quickActionTemplateId3
        )
    )
    val state = _state.asStateFlow()

    /** 자동응답 템플릿 드롭다운에 보여줄 목록 (활성 템플릿만). */
    val templates = container.messageTemplateRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<MessageTemplateEntity>())

    fun setBehavior(b: AfterCallBehavior) { _state.value = _state.value.copy(afterCallBehavior = b) }
    fun setDefaultStatus(s: CustomerStatus) { _state.value = _state.value.copy(defaultStatus = s) }

    fun setReceivedSmsEnabled(enabled: Boolean) {
        container.preferences.receivedSmsEnabled = enabled
        _state.value = _state.value.copy(receivedSmsEnabled = enabled)
    }

    fun setAutoFirstReplyEnabled(enabled: Boolean) {
        container.preferences.autoFirstReplyEnabled = enabled
        _state.value = _state.value.copy(autoFirstReplyEnabled = enabled)
    }

    fun setIncomingTemplate(id: Long) {
        container.preferences.firstReplyIncomingTemplateId = id
        _state.value = _state.value.copy(firstReplyIncomingTemplateId = id)
    }

    fun setMissedTemplate(id: Long) {
        container.preferences.firstReplyMissedTemplateId = id
        _state.value = _state.value.copy(firstReplyMissedTemplateId = id)
    }

    fun setQuickAction(slot: Int, id: Long) {
        when (slot) {
            1 -> { container.preferences.quickActionTemplateId1 = id
                   _state.value = _state.value.copy(quickActionTemplateId1 = id) }
            2 -> { container.preferences.quickActionTemplateId2 = id
                   _state.value = _state.value.copy(quickActionTemplateId2 = id) }
            3 -> { container.preferences.quickActionTemplateId3 = id
                   _state.value = _state.value.copy(quickActionTemplateId3 = id) }
        }
    }
}

data class SettingsUiState(
    val afterCallBehavior: AfterCallBehavior,
    val defaultStatus: CustomerStatus,
    val receivedSmsEnabled: Boolean = false,
    val autoFirstReplyEnabled: Boolean = false,
    val firstReplyIncomingTemplateId: Long = -1L,
    val firstReplyMissedTemplateId: Long = -1L,
    val quickActionTemplateId1: Long = -1L,
    val quickActionTemplateId2: Long = -1L,
    val quickActionTemplateId3: Long = -1L
)

enum class AfterCallBehavior(val label: String) {
    NOTIFY("알림 표시"),
    NONE("아무것도 안 함")
}
