package com.detailline.callfollowcrm.presentation.screen.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.AppNotificationEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 🔔 알림 센터 — 협업 소식을 시간순으로. 탭하면 이동하며 **읽음(회색)으로** 남긴다(삭제 아님 — 까먹지 않게). (2026-10-13 사장님) */
class NotificationCenterViewModel(private val container: AppContainer) : ViewModel() {

    val items: StateFlow<List<AppNotificationEntity>> =
        container.appNotificationRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 탭한 한 줄 → 읽음(이동은 화면이 먼저 하고 호출). 삭제 아님 — 회색으로 남는다. */
    fun markRead(id: Long) = viewModelScope.launch { container.appNotificationRepository.markRead(id) }

    /** [모두 읽기]. */
    fun markAllRead() = viewModelScope.launch { container.appNotificationRepository.markAllRead() }
}
