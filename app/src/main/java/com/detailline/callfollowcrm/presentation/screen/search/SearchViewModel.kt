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

    // ── 03 최근 검색 — 검색창이 매번 백지이던 것. (2026-09-19 사장님) ──
    private val _recent = MutableStateFlow(container.preferences.recentSearches)
    val recent: StateFlow<List<String>> = _recent

    /** 결과를 눌러 들어갈 때만 '쓸모 있던 검색' 으로 보고 저장한다. 치는 족족 쌓지 않는다. */
    fun rememberQuery(q: String) {
        container.preferences.pushRecentSearch(q)
        _recent.value = container.preferences.recentSearches
    }

    fun dropRecent(q: String) {
        container.preferences.removeRecentSearch(q)
        _recent.value = container.preferences.recentSearches
    }

    fun clearRecent() {
        container.preferences.clearRecentSearches()
        _recent.value = emptyList()
    }

    /**
     * 📍 **주소·아파트로 현장 찾기.** (2026-09-19 사장님 "제일 자주 쓰일 것 같은 건 주소나 아파트로 찾기")
     *
     * "동탄" 을 치면 그 글자가 든 문자만 나오던 것 → **동탄에서 했던 현장들**(언제·얼마)을 보여준다.
     *   · 자료는 이미 있다 — 건(jobs)의 주소·시공일·금액, 그리고 건이 없는 손님은 고객 카드 주소.
     *   · 최근 시공일 순. 같은 손님의 여러 건은 **건마다 한 줄**(1차·2차가 다른 현장일 수 있다).
     */
    val siteResults: StateFlow<List<SiteHit>> = combine(
        container.customerRepository.observeAll(),
        container.jobRepository.observeAll(),
        query.debounce(220)
    ) { customers, jobs, qRaw ->
        val q = qRaw.trim()
        if (q.length < 2) return@combine emptyList()
        val byId = customers.associateBy { it.id }
        val out = ArrayList<SiteHit>()
        val seen = HashSet<String>()

        for (j in jobs) {
            val addr = j.address?.trim().orEmpty()
            if (addr.isEmpty() || !addr.contains(q, ignoreCase = true)) continue
            val c = byId[j.customerId] ?: continue
            if (!seen.add("j" + j.id)) continue
            out.add(
                SiteHit(
                    phone = c.phoneNumber,
                    customerId = c.id,
                    name = c.name?.takeIf { it.isNotBlank() },
                    address = addr,
                    dayMs = j.scheduledWorkDate ?: j.workCompletedAt,
                    money = moneyLabel(j.totalAmount, j.balanceAmount, j.balancePaidAt, j.workCompletedAt)
                )
            )
        }
        // 건이 아직 없는 손님 — 고객 카드 주소로. (상담만 하고 일정은 안 잡은 현장)
        for (c in customers) {
            val addr = c.address?.trim().orEmpty()
            if (addr.isEmpty() || !addr.contains(q, ignoreCase = true)) continue
            if (jobs.any { it.customerId == c.id && !it.address.isNullOrBlank() }) continue
            if (!seen.add("c" + c.id)) continue
            out.add(
                SiteHit(
                    phone = c.phoneNumber,
                    customerId = c.id,
                    name = c.name?.takeIf { it.isNotBlank() },
                    address = addr,
                    dayMs = c.scheduledWorkDate ?: c.workCompletedAt,
                    money = moneyLabel(c.totalAmount, c.balanceAmount, c.balancePaidAt, c.workCompletedAt)
                )
            )
        }
        out.sortedByDescending { it.dayMs ?: 0L }.take(30)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 현장 한 줄 오른쪽에 붙는 돈 상태 — 완납 / 잔금 N만 / N만원 / (없으면 null). */
    private fun moneyLabel(total: Long?, balance: Long?, balancePaidAt: Long?, doneAt: Long?): String? {
        val man = { won: Long -> "${won / 10000}만" }
        return when {
            balancePaidAt != null -> "완납"
            (balance ?: 0L) > 0L -> "잔금 " + man(balance!!)
            (total ?: 0L) > 0L -> man(total!!) + "원"
            doneAt != null -> "완료"
            else -> null
        }
    }

    /** 오늘 통화한 손님 — 검색창을 열자마자 누를 게 있도록. (2026-09-19 사장님) */
    val todayCallers: StateFlow<List<SearchResult>> = combine(
        container.customerRepository.observeAll(),
        container.callRecordRepository.observeRecent(60)
    ) { customers, calls ->
        val todayStart = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(System.currentTimeMillis())
        val seen = LinkedHashSet<String>()
        val out = ArrayList<SearchResult>()
        for (c in calls.sortedByDescending { it.startedAt ?: 0L }) {
            if ((c.startedAt ?: 0L) < todayStart) break
            val suf = suffixOf(c.phoneNumber)
            if (suf.length < 7 || !seen.add(suf)) continue
            val cust = customers.firstOrNull { suffixOf(it.phoneNumber) == suf }
            out.add(
                SearchResult(
                    phone = cust?.phoneNumber ?: c.phoneNumber,
                    customerId = cust?.id,
                    name = cust?.name?.takeIf { it.isNotBlank() },
                    snippet = null,
                    source = SearchSource.CUSTOMER
                )
            )
            if (out.size >= 5) break
        }
        out.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        // 🔴 번호 매칭은 **4자리 이상**일 때만. (2026-09-19 실기에서 발견)
        //   "35" 를 치면 번호에 35 가 든 손님이 15명 우르르 올라와, 정작 찾던
        //   "35만원" 통화·문자를 아래로 밀어냈다. 두세 자리 숫자는 번호가 아니라 **금액**이다.
        //   번호 뒷자리로 찾을 땐 어차피 네 자리를 친다.
        val qDigits = q.filter { it.isDigit() }.takeIf { it.length >= 4 } ?: ""

        // 대화 전체 본문 검색(폰 SMS/MMS) — IO. suffix 별 '가장 최근 매칭' 한 건만(list 는 date DESC).
        val bodyHits = LinkedHashMap<String, SmsHit>()
        // 통화 내용 검색(요약·전문·태그) — "통화로만 말한 것"(예: 화장실 바닥 10만원)도 찾게. suffix 별 첫 매칭. (2026-09-02 사장님)
        val callHits = LinkedHashMap<String, CallHit>()
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
            val calls = withContext(Dispatchers.IO) {
                runCatching { container.callSummaryRepository.search(q) }.getOrDefault(emptyList())
            }
            for (cs in calls) {
                val phone = cs.phoneNumber?.takeIf { it.isNotBlank() }
                    ?: customers.firstOrNull { it.id == cs.customerId }?.phoneNumber ?: continue
                val suf = suffixOf(phone)
                if (suf.length < 7) continue
                if (!callHits.containsKey(suf)) {
                    // 🔴 **진짜 말(전사)을 맨 앞에.** (2026-09-19 사장님)
                    //   전엔 AI 요약(summaryText)을 먼저 봐서, 같은 말이 둘 다 있으면 **요약이 잡혔다.**
                    //   사장님이 찾는 건 "그때 실제로 뭐라 했나" 다.
                    val spoken = cs.transcriptText?.takeIf { it.contains(q, ignoreCase = true) }
                    val fromAi = if (spoken != null) null else listOfNotNull(
                        cs.summaryText, cs.customerNeed, cs.problem, cs.nextAction, cs.title
                    ).firstOrNull { it.contains(q, ignoreCase = true) }
                    val matched = spoken ?: fromAi
                        ?: cs.transcriptText ?: cs.summaryText ?: cs.tagsJson ?: ""
                    val (sents, more) = matchedSentences(matched, q)
                    callHits[suf] = CallHit(phone, sents.firstOrNull() ?: "", sents, more, fromSummary = spoken == null)
                }
            }
        }

        val out = LinkedHashMap<String, SearchResult>()

        for (c in customers) {
            val suf = suffixOf(c.phoneNumber)
            val nameHit = c.name?.lowercase()?.contains(qLower) == true
            val phoneHit = qDigits.isNotEmpty() && c.phoneNumber.filter { it.isDigit() }.contains(qDigits)
            val memoHit = c.memo?.lowercase()?.contains(qLower) == true
            val bodyHit = bodyHits[suf]
            val callHit = callHits[suf]
            if (nameHit || phoneHit || memoHit || bodyHit != null || callHit != null) {
                // 내용 매칭(문자>통화)을 우선 노출, 없으면 메모, 그래도 없으면 이름/전화만.
                val (snip, src) = when {
                    bodyHit != null -> snippetAround(bodyHit.body, q) to SearchSource.MESSAGE
                    callHit != null -> callHit.snippet to SearchSource.CALL
                    memoHit -> c.memo?.takeIf { it.isNotBlank() } to SearchSource.MEMO
                    else -> c.memo?.takeIf { it.isNotBlank() } to SearchSource.CUSTOMER
                }
                val (sents, more) = when {
                    bodyHit != null -> matchedSentences(bodyHit.body, q)
                    callHit != null -> callHit.sentences to callHit.moreCount
                    memoHit -> matchedSentences(c.memo.orEmpty(), q)
                    else -> emptyList<String>() to 0
                }
                out[suf] = SearchResult(
                    phone = c.phoneNumber,
                    customerId = c.id,
                    name = c.name?.takeIf { it.isNotBlank() },
                    snippet = snip,
                    source = src,
                    sentences = sents,
                    moreCount = more,
                    fromSummary = src == SearchSource.CALL && callHit?.fromSummary == true,
                    address = c.address?.takeIf { it.isNotBlank() }
                )
            }
        }
        for (s in smsContacts) {
            val suf = s.normalizedSuffix
            if (out.containsKey(suf)) continue
            val phoneHit = qDigits.isNotEmpty() && s.address.filter { it.isDigit() }.contains(qDigits)
            val lastBodyHit = s.lastBody.lowercase().contains(qLower)
            val bodyHit = bodyHits[suf]
            val callHit = callHits[suf]
            if (phoneHit || lastBodyHit || bodyHit != null || callHit != null) {
                val (snip, src) = when {
                    bodyHit != null -> snippetAround(bodyHit.body, q) to SearchSource.MESSAGE
                    callHit != null -> callHit.snippet to SearchSource.CALL
                    lastBodyHit -> s.lastBody.take(60) to SearchSource.MESSAGE
                    else -> s.lastBody.take(60) to SearchSource.CUSTOMER
                }
                val (sents, more) = when {
                    bodyHit != null -> matchedSentences(bodyHit.body, q)
                    callHit != null -> callHit.sentences to callHit.moreCount
                    lastBodyHit -> matchedSentences(s.lastBody, q)
                    else -> emptyList<String>() to 0
                }
                out[suf] = SearchResult(
                    phone = s.address,
                    customerId = null,
                    name = null,
                    snippet = snip,
                    source = src,
                    sentences = sents,
                    moreCount = more,
                    fromSummary = src == SearchSource.CALL && callHit?.fromSummary == true
                )
            }
        }
        // 고객/연락처 캐시(상위 500)엔 없지만 옛 대화 본문·통화에 걸린 번호 — 반드시 노출(이게 핵심 개선).
        for ((suf, hit) in bodyHits) {
            if (out.containsKey(suf)) continue
            val (sents, more) = matchedSentences(hit.body, q)
            out[suf] = SearchResult(
                phone = hit.address, customerId = null, name = null,
                snippet = snippetAround(hit.body, q), source = SearchSource.MESSAGE,
                sentences = sents, moreCount = more
            )
        }
        for ((suf, hit) in callHits) {
            if (out.containsKey(suf)) continue
            out[suf] = SearchResult(
                phone = hit.address, customerId = null, name = null,
                snippet = hit.snippet, source = SearchSource.CALL,
                sentences = hit.sentences, moreCount = hit.moreCount,
                fromSummary = hit.fromSummary
            )
        }

        out.values.take(50).toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun suffixOf(phone: String): String {
        val d = phone.filter { it.isDigit() }
        return if (d.length >= 8) d.takeLast(8) else d
    }

    /**
     * 긴 글에서 **찾는 말이 든 문장들**을 골라낸다. (2026-09-19 사장님)
     *   통화 전문은 마침표가 거의 없어서(받아쓰기라) 문장부호만으로는 안 쪼개진다.
     *   → 문장부호 + 줄바꿈으로 자르고, 그래도 너무 길면 매칭 둘레만 잘라 쓴다.
     *   @return 문장들(최대 limit) to 더 걸린 곳 수
     */
    private fun matchedSentences(
        body: String, q: String, limit: Int = MAX_SENTENCES
    ): Pair<List<String>, Int> {
        val flat = body.replace("\r", " ")
        val parts = flat.split(Regex("(?<=[.!?。\n])|(?<=요 )|(?<=죠 )|(?<=다 )"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val hits = parts.filter { it.contains(q, ignoreCase = true) }
        if (hits.isEmpty()) return listOf(snippetAround(flat, q)) to 0
        // 한 문장이 너무 길면(받아쓰기 덩어리) 매칭 둘레만 남긴다 — 화면이 안 터지게.
        val shown = hits.take(limit).map { if (it.length > 90) snippetAround(it, q, window = 32) else it }
        return shown to (hits.size - shown.size).coerceAtLeast(0)
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
    /** 통화 내용 매칭 한 건 — 번호 + 매칭 문장들 + 더 걸린 곳 수. */
    private data class CallHit(
        val address: String,
        val snippet: String,
        val sentences: List<String> = emptyList(),
        val moreCount: Int = 0,
        /** 진짜 말이 아니라 **AI 요약**에서 걸렸나 — 화면에 그렇다고 밝힌다. (2026-09-19 사장님) */
        val fromSummary: Boolean = false
    )
}

/** 검색 결과에서 '어디서 걸렸는지' — 문자/통화/메모 배지용. (2026-09-02 사장님) */
enum class SearchSource { CUSTOMER, MESSAGE, CALL, MEMO }

/**
 * 검색 결과 한 줄. name 있으면 고객, 없으면 번호만 아는 연락처. source = 매칭 위치.
 *
 * @param snippet   대표 한 줄 (옛 화면 호환 · 첫 문장과 같다)
 * @param sentences 걸린 문장들 — 최대 [MAX_SENTENCES] 개. 통화 안에서 여러 번 걸리면 다 보여준다.
 *                  (2026-09-19 사장님 "통화 안에 기록까지도 다 체크해주니까 좋다")
 * @param moreCount 그 통화/대화에서 더 걸린 곳 수 — "이 통화에서 N곳 더"
 */
data class SearchResult(
    val phone: String,
    val customerId: Long?,
    val name: String?,
    val snippet: String?,
    val source: SearchSource = SearchSource.CUSTOMER,
    val sentences: List<String> = emptyList(),
    val moreCount: Int = 0,
    /** 통화 결과가 **AI 요약**에서 걸렸으면 true — "📞 통화 요약" 으로 밝힌다. (2026-09-19 사장님) */
    val fromSummary: Boolean = false,
    /** 현장 주소 — 번호 밑에 작게. 번호는 누군지 못 알려준다. (2026-09-19 사장님) */
    val address: String? = null
)

/** 한 결과에서 펼칠 문장 수. 더 있으면 "N곳 더" 로 접는다. (2026-09-19 사장님 기본값) */
const val MAX_SENTENCES = 3

/**
 * 📍 주소로 찾은 **현장** 한 줄. (2026-09-19 사장님)
 *   말이 아니라 **장부**에서 나온 결과다 — 언제 했고 얼마였는지가 같이 붙는다.
 */
data class SiteHit(
    val phone: String,
    val customerId: Long?,
    val name: String?,
    val address: String,
    val dayMs: Long?,
    val money: String?
)
