package com.detailline.callfollowcrm.presentation.screen.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    /** 일당별 함께한 현장 (workerId → [현장]). 일당 배정(JobCrew) + 고객 조인. */
    val sitesByWorker: StateFlow<Map<Long, List<SiteRow>>> =
        combine(
            container.jobCrewRepository.observeAll(),
            container.customerRepository.observeAll()
        ) { crew, customers ->
            val byId = customers.associateBy { it.id }
            crew.groupBy { it.workerId }.mapValues { (_, list) ->
                list.sortedByDescending { it.dayStartMs }.map { jc ->
                    val c = byId[jc.customerId]
                    val nm = c?.name?.takeIf { it.isNotBlank() }
                        ?: c?.let { PhoneNumberFormatter.format(it.phoneNumber) }
                        ?: "고객"
                    SiteRow(jc.dayStartMs, nm, jc.wage)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun add(kind: String, name: String, phone: String, tag: String, memo: String,
            wage: Long? = null, wageType: String = NotebookContactEntity.WAGE_DAILY) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        withContext(NonCancellable) { container.notebookRepository.add(kind, name, phone, tag, memo, wage, wageType) }
    }

    fun update(id: Long, name: String, phone: String, tag: String, memo: String,
               wage: Long? = null, wageType: String = NotebookContactEntity.WAGE_DAILY) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        withContext(NonCancellable) { container.notebookRepository.update(id, name, phone, tag, memo, wage, wageType) }
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
    WORKER("일당·알바", NotebookContactEntity.KIND_WORKER),
    VENDOR("거래처", NotebookContactEntity.KIND_VENDOR)
}

/** 일당이 함께한 현장 한 줄 (날짜·고객·일당). */
data class SiteRow(val dayStartMs: Long, val customerName: String, val wage: Long)
