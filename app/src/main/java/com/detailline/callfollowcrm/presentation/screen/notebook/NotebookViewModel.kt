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
import kotlinx.coroutines.flow.map
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

    /** 앱에 저장된 사람 1명 — "시공막내에서 고르기" 피커용. */
    data class PickPerson(val name: String, val phone: String)

    /**
     * 시공막내에 **저장해둔 사람** = 일당·알바 추가 때 골라 담을 후보.
     *   (2026-07-15 사장님: "업무폰에서 저장을 안 해서 연락처에 데이터가 없네. 시공막내에 저장해둔
     *    사람들이 나오면 선택하기 편할 것 같은데" — 업무폰은 폰 연락처를 안 쓰니 앱 명부가 진짜 주소록.)
     *
     * **이름을 붙인 사람만** 넣는다 (사장님 2026-07-15: "저장 안 한 핸드폰번호들까지 막 뜨네"):
     *   고객 레코드는 전화/문자만 와도 자동 생성돼서, 이름 없는 껍데기 번호가 명부의 상당수다.
     *   그건 사장님이 '저장한 사람'이 아니라 그냥 스쳐간 번호 → 골라 담을 대상이 아니다.
     *   이름 칸에 번호가 박힌 것(looksLikePhone)도 같은 이유로 제외 — 이름인 척하는 번호다.
     *   같은 번호가 여러 레코드로 있으면(형식 차이 등) 최근 것 하나만.
     *   정렬은 최근에 손댄 순 — 방금 통화/문자한 사람을 바로 담는 게 흔한 흐름이라.
     */
    val savedPeople: StateFlow<List<PickPerson>> =
        container.customerRepository.observeAll()
            .map { list ->
                list.asSequence()
                    .filter { it.phoneNumber.isNotBlank() }
                    .mapNotNull { c ->
                        val nm = c.name?.trim().orEmpty()
                        if (nm.isBlank() || PhoneNumberFormatter.looksLikePhone(nm)) null
                        else c to nm
                    }
                    .sortedByDescending { it.first.updatedAt }
                    .distinctBy { it.first.phoneNumber.filter { ch -> ch.isDigit() } }
                    .map { (c, nm) -> PickPerson(name = nm, phone = c.phoneNumber) }
                    .toList()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
