package com.detailline.callfollowcrm.presentation.screen.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 수첩 (일당/거래처) — 2026-06-01. */
class NotebookViewModel(private val container: AppContainer) : ViewModel() {

    private val tab = MutableStateFlow(NotebookTab.WORKER)
    val tabState: StateFlow<NotebookTab> = tab
    fun setTab(t: NotebookTab) { tab.value = t }

    val workers: StateFlow<List<NotebookContactEntity>> =
        container.notebookRepository.observeWorkers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vendors: StateFlow<List<NotebookContactEntity>> =
        container.notebookRepository.observeVendors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(kind: String, name: String, phone: String, tag: String, memo: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        withContext(NonCancellable) { container.notebookRepository.add(kind, name, phone, tag, memo) }
    }

    fun update(id: Long, name: String, phone: String, tag: String, memo: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        withContext(NonCancellable) { container.notebookRepository.update(id, name, phone, tag, memo) }
    }

    fun delete(id: Long) = viewModelScope.launch {
        withContext(NonCancellable) { container.notebookRepository.delete(id) }
    }
}

enum class NotebookTab(val label: String, val kind: String) {
    WORKER("일당", NotebookContactEntity.KIND_WORKER),
    VENDOR("거래처", NotebookContactEntity.KIND_VENDOR)
}
