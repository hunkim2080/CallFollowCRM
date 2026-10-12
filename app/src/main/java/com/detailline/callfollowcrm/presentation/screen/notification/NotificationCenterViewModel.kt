package com.detailline.callfollowcrm.presentation.screen.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.AppNotificationEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 🔔 알림 센터 — 쌓인 알림을 시간순으로. 탭하면 이동하며 그 줄 삭제(사장님: "클릭 한번 하면 없어져야지"). */
class NotificationCenterViewModel(private val container: AppContainer) : ViewModel() {

    val items: StateFlow<List<AppNotificationEntity>> =
        container.appNotificationRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 탭한 한 줄 삭제(이동은 화면이 먼저 하고 호출). */
    fun dismiss(id: Long) = viewModelScope.launch { container.appNotificationRepository.delete(id) }

    fun clearAll() = viewModelScope.launch { container.appNotificationRepository.clearAll() }
}
