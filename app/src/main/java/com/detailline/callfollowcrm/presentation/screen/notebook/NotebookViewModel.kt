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

    // ── 자주 쓰는 문구 (일당/거래처 따로, prefs 저장) ──────────────────
    private val prefs = container.preferences
    private val _workerPhrases = MutableStateFlow(prefs.workerSmsPhrases)
    private val _vendorPhrases = MutableStateFlow(prefs.vendorSmsPhrases)
    val workerPhrases: StateFlow<List<String>> = _workerPhrases
    val vendorPhrases: StateFlow<List<String>> = _vendorPhrases

    fun addPhrase(kind: String, text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        if (kind == NotebookContactEntity.KIND_WORKER) {
            val l = _workerPhrases.value + t; prefs.workerSmsPhrases = l; _workerPhrases.value = l
        } else {
            val l = _vendorPhrases.value + t; prefs.vendorSmsPhrases = l; _vendorPhrases.value = l
        }
    }

    fun deletePhrase(kind: String, index: Int) {
        if (kind == NotebookContactEntity.KIND_WORKER) {
            val l = _workerPhrases.value.filterIndexed { i, _ -> i != index }
            prefs.workerSmsPhrases = l; _workerPhrases.value = l
        } else {
            val l = _vendorPhrases.value.filterIndexed { i, _ -> i != index }
            prefs.vendorSmsPhrases = l; _vendorPhrases.value = l
        }
    }
}

enum class NotebookTab(val label: String, val kind: String) {
    WORKER("일당", NotebookContactEntity.KIND_WORKER),
    VENDOR("거래처", NotebookContactEntity.KIND_VENDOR)
}
