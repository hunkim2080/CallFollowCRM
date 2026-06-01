package com.detailline.callfollowcrm.presentation.screen.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CategoryEntity
import com.detailline.callfollowcrm.data.local.entity.RecurringMessageEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 정기문자 규칙 관리 (설정 → 정기문자). 2026-06-01. */
class RecurringMessagesViewModel(private val container: AppContainer) : ViewModel() {

    val rules: StateFlow<List<RecurringMessageEntity>> =
        container.recurringMessageRepository.observeRules()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        container.categoryRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, targetCategoryId: Long?, intervalDays: Int, sendMinutes: Int, body: String) =
        viewModelScope.launch {
            if (name.isBlank() || body.isBlank() || intervalDays <= 0) return@launch
            withContext(NonCancellable) {
                container.recurringMessageRepository.add(name, targetCategoryId, intervalDays, sendMinutes, body)
            }
        }

    fun update(id: Long, name: String, targetCategoryId: Long?, intervalDays: Int, sendMinutes: Int, body: String, enabled: Boolean) =
        viewModelScope.launch {
            if (name.isBlank() || body.isBlank() || intervalDays <= 0) return@launch
            withContext(NonCancellable) {
                container.recurringMessageRepository.update(id, name, targetCategoryId, intervalDays, sendMinutes, body, enabled)
            }
        }

    fun setEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        withContext(NonCancellable) { container.recurringMessageRepository.setEnabled(id, enabled) }
    }

    fun delete(id: Long) = viewModelScope.launch {
        withContext(NonCancellable) { container.recurringMessageRepository.delete(id) }
    }
}
