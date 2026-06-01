package com.detailline.callfollowcrm.presentation.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn

/**
 * 검색 (2026-06-01 전면 리뉴얼, 프로토 s-search) — 이름·전화번호·메시지 검색.
 *   소스: 고객(이름/전화/메모) + SMS 연락처(전화/마지막 메시지). DB 변경 없음.
 *   suffix(끝 8자리)로 dedupe — 고객 정보 우선.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")
    val queryState: StateFlow<String> = query
    fun setQuery(q: String) { query.value = q }

    val results: StateFlow<List<SearchResult>> = combine(
        container.customerRepository.observeAll(),
        container.smsContactCacheRepository.observeAll(500),
        query.debounce(150)
    ) { customers, smsContacts, qRaw ->
        val q = qRaw.trim()
        if (q.isEmpty()) return@combine emptyList()
        val qLower = q.lowercase()
        val qDigits = q.filter { it.isDigit() }
        val out = LinkedHashMap<String, SearchResult>()

        for (c in customers) {
            val nameHit = c.name?.lowercase()?.contains(qLower) == true
            val phoneHit = qDigits.isNotEmpty() && c.phoneNumber.filter { it.isDigit() }.contains(qDigits)
            val memoHit = c.memo?.lowercase()?.contains(qLower) == true
            if (nameHit || phoneHit || memoHit) {
                out[suffixOf(c.phoneNumber)] = SearchResult(
                    phone = c.phoneNumber,
                    customerId = c.id,
                    name = c.name?.takeIf { it.isNotBlank() },
                    snippet = c.memo?.takeIf { it.isNotBlank() }
                )
            }
        }
        for (s in smsContacts) {
            val suf = s.normalizedSuffix
            if (out.containsKey(suf)) continue
            val phoneHit = qDigits.isNotEmpty() && s.address.filter { it.isDigit() }.contains(qDigits)
            val msgHit = s.lastBody.lowercase().contains(qLower)
            if (phoneHit || msgHit) {
                out[suf] = SearchResult(
                    phone = s.address,
                    customerId = null,
                    name = null,
                    snippet = s.lastBody.take(60)
                )
            }
        }
        out.values.take(50).toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun suffixOf(phone: String): String {
        val d = phone.filter { it.isDigit() }
        return if (d.length >= 8) d.takeLast(8) else d
    }
}

/** 검색 결과 한 줄. name 있으면 고객, 없으면 번호만 아는 연락처. */
data class SearchResult(
    val phone: String,
    val customerId: Long?,
    val name: String?,
    val snippet: String?
)
