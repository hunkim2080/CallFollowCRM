package com.detailline.callfollowcrm.presentation.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * 검색 (2026-06-01 전면 리뉴얼, 프로토 s-search) — 이름·전화번호·**대화 전체 내용** 검색.
 *   소스: 고객(이름/전화/메모) + SMS 연락처(전화/마지막 메시지) + **폰에 쌓인 SMS/MMS 본문 전체**.
 *   suffix(끝 8자리)로 dedupe — 고객 정보 우선, 본문 매칭이면 그 문장을 스니펫으로.
 *
 * 2026-08-02 — 대화 전체 검색 추가. 기존엔 각 대화 '마지막 문자 한 줄'(lastBody)만 봐서 옛 문자 속 단어를
 *   못 찾았음(사장님: AS 약속 고객을 키워드로 검색해도 안 나옴). searchMessages 로 전체 본문을 뒤진다.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")
    val queryState: StateFlow<String> = query
    fun setQuery(q: String) { query.value = q }

    val results: StateFlow<List<SearchResult>> = combine(
        container.customerRepository.observeAll(),
        container.smsContactCacheRepository.observeAll(500),
        query.debounce(220)
    ) { customers, smsContacts, qRaw ->
        Triple(customers, smsContacts, qRaw)
    }.mapLatest { (customers, smsContacts, qRaw) ->
        val q = qRaw.trim()
        if (q.isEmpty()) return@mapLatest emptyList()
        val qLower = q.lowercase()
        val qDigits = q.filter { it.isDigit() }

        // 대화 전체 본문 검색(폰 SMS/MMS) — IO. suffix 별 '가장 최근 매칭' 한 건만(list 는 date DESC).
        val bodyHits = LinkedHashMap<String, SmsHit>()
        if (q.length >= 2) {
            val hits = withContext(Dispatchers.IO) {
                runCatching { container.smsRepository.searchMessages(q) }.getOrDefault(emptyList())
            }
            for (m in hits) {
                val addr = m.address ?: continue
                val suf = suffixOf(addr)
                if (suf.length < 7) continue
                if (!bodyHits.containsKey(suf)) bodyHits[suf] = SmsHit(addr, m.body)
            }
        }

        val out = LinkedHashMap<String, SearchResult>()

        for (c in customers) {
            val suf = suffixOf(c.phoneNumber)
            val nameHit = c.name?.lowercase()?.contains(qLower) == true
            val phoneHit = qDigits.isNotEmpty() && c.phoneNumber.filter { it.isDigit() }.contains(qDigits)
            val memoHit = c.memo?.lowercase()?.contains(qLower) == true
            val bodyHit = bodyHits[suf]
            if (nameHit || phoneHit || memoHit || bodyHit != null) {
                out[suf] = SearchResult(
                    phone = c.phoneNumber,
                    customerId = c.id,
                    name = c.name?.takeIf { it.isNotBlank() },
                    // 본문 매칭이면 그 문장(맥락)을, 아니면 메모.
                    snippet = if (bodyHit != null) snippetAround(bodyHit.body, q)
                              else c.memo?.takeIf { it.isNotBlank() }
                )
            }
        }
        for (s in smsContacts) {
            val suf = s.normalizedSuffix
            if (out.containsKey(suf)) continue
            val phoneHit = qDigits.isNotEmpty() && s.address.filter { it.isDigit() }.contains(qDigits)
            val lastBodyHit = s.lastBody.lowercase().contains(qLower)
            val bodyHit = bodyHits[suf]
            if (phoneHit || lastBodyHit || bodyHit != null) {
                out[suf] = SearchResult(
                    phone = s.address,
                    customerId = null,
                    name = null,
                    snippet = if (bodyHit != null) snippetAround(bodyHit.body, q) else s.lastBody.take(60)
                )
            }
        }
        // 고객/연락처 캐시(상위 500)엔 없지만 옛 대화 본문에 걸린 번호 — 반드시 노출(이게 핵심 개선).
        for ((suf, hit) in bodyHits) {
            if (out.containsKey(suf)) continue
            out[suf] = SearchResult(
                phone = hit.address,
                customerId = null,
                name = null,
                snippet = snippetAround(hit.body, q)
            )
        }

        out.values.take(50).toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun suffixOf(phone: String): String {
        val d = phone.filter { it.isDigit() }
        return if (d.length >= 8) d.takeLast(8) else d
    }

    /** 매칭 단어 주변을 잘라 스니펫으로(…앞뒤…). 어느 문장에서 걸렸는지 사장님이 알아보게. */
    private fun snippetAround(body: String, q: String, window: Int = 22): String {
        val flat = body.replace("\n", " ").trim()
        val idx = flat.indexOf(q, ignoreCase = true)
        if (idx < 0) return flat.take(60)
        val start = (idx - window).coerceAtLeast(0)
        val end = (idx + q.length + window).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }

    /** 본문 매칭 한 건 — 대화 식별용 번호 + 매칭된 문장. */
    private data class SmsHit(val address: String, val body: String)
}

/** 검색 결과 한 줄. name 있으면 고객, 없으면 번호만 아는 연락처. */
data class SearchResult(
    val phone: String,
    val customerId: Long?,
    val name: String?,
    val snippet: String?
)
