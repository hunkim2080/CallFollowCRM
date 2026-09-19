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

    /**
     * 💰 **못 받은 돈** — "미수 / 못받은 / 잔금 / 미수금" 중 뭘 쳐도 같은 목록. (2026-09-19 사장님)
     *   '미수' 는 단어가 아니라 **상태**다. 문자에 그 글자가 있는 것과 따로 묶어 위에 둔다.
     *   돈 계산은 정산 화면과 **같은 SettlementCalc** 을 쓴다 — 검색이 다른 숫자를 말하면 더 큰 사고다.
     */
    val unpaidResults: StateFlow<List<SiteHit>> = combine(
        container.customerRepository.observeAll(),
        container.jobRepository.observeAll(),
        query.debounce(220)
    ) { customers, jobs, qRaw ->
        val q = qRaw.trim()
        if (!UNPAID_WORDS.any { q.contains(it) }) return@combine emptyList()
        val byId = customers.associateBy { it.id }
        val out = ArrayList<SiteHit>()
        val covered = HashSet<Long>()
        for (j in jobs) {
            val row = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(j)
            if (row.outstanding <= 0L) continue
            val c = byId[j.customerId] ?: continue
            covered.add(c.id)
            out.add(hitOf(c, j.address ?: c.address, j.scheduledWorkDate ?: j.workCompletedAt,
                "미수 ${row.outstanding / 10000}만"))
        }
        // 건이 없는 옛 손님 — 고객 카드로.
        for (c in customers) {
            if (c.id in covered) continue
            if (jobs.any { it.customerId == c.id }) continue
            val row = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(c)
            if (row.outstanding <= 0L) continue
            out.add(hitOf(c, c.address, c.scheduledWorkDate ?: c.workCompletedAt,
                "미수 ${row.outstanding / 10000}만"))
        }
        out.sortedByDescending { it.dayMs ?: 0L }.take(40)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 📅 **그달 시공** — "9월", "9월달", "지난달", "이번달" 을 알아듣는다. (2026-09-19 사장님) */
    val periodResults: StateFlow<List<SiteHit>> = combine(
        container.customerRepository.observeAll(),
        container.jobRepository.observeAll(),
        query.debounce(220)
    ) { customers, jobs, qRaw ->
        val month = SearchQueryParse.monthOf(qRaw.trim()) ?: return@combine emptyList()
        val byId = customers.associateBy { it.id }
        val cal = java.util.Calendar.getInstance()
        fun inMonth(ms: Long?): Boolean {
            if (ms == null || ms <= 0L) return false
            cal.timeInMillis = ms
            return cal.get(java.util.Calendar.YEAR) == month.first &&
                cal.get(java.util.Calendar.MONTH) + 1 == month.second
        }
        val out = ArrayList<SiteHit>()
        for (j in jobs) {
            val day = j.scheduledWorkDate ?: j.workCompletedAt
            if (!inMonth(day)) continue
            val c = byId[j.customerId] ?: continue
            val row = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(j)
            out.add(hitOf(c, j.address ?: c.address, day,
                moneyLabel(j.totalAmount, row.outstanding, j.balancePaidAt, j.workCompletedAt)))
        }
        out.sortedByDescending { it.dayMs ?: 0L }.take(40)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun hitOf(
        c: com.detailline.callfollowcrm.data.local.entity.CustomerEntity,
        address: String?, dayMs: Long?, money: String?
    ) = SiteHit(
        phone = c.phoneNumber,
        customerId = c.id,
        name = c.name?.takeIf { it.isNotBlank() },
        address = address?.takeIf { it.isNotBlank() } ?: "주소 없음",
        dayMs = dayMs,
        money = money
    )

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

    /**
     * 🪝 **내 숫자로 만든 미끼 칩.** (2026-09-19 사장님 · 프로토 Wb1zoMZT)
     *   검색창을 열었을 때 "이렇게도 찾아요" 로 깔린다. 누르면 그 검색이 바로 돌아간다.
     *   설명("미수로 찾기")이 아니라 **내 숫자**("미수 4건")라야 누른다.
     *   0건이면 그 칩은 안 만든다 — 빈 약속을 하지 않는다.
     */
    val baitChips: StateFlow<List<BaitChip>> = combine(
        container.customerRepository.observeAll(),
        container.jobRepository.observeAll()
    ) { customers, jobs ->
        val out = ArrayList<BaitChip>()
        val byId = customers.associateBy { it.id }

        // ① 💰 못 받은 돈
        var unpaid = 0
        val covered = HashSet<Long>()
        for (j in jobs) {
            if (com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(j).outstanding <= 0L) continue
            if (byId[j.customerId] == null) continue
            covered.add(j.customerId); unpaid++
        }
        for (c in customers) {
            if (c.id in covered || jobs.any { it.customerId == c.id }) continue
            if (com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(c).outstanding > 0L) unpaid++
        }
        if (unpaid > 0) out.add(BaitChip("💰 미수 ${unpaid}건", "미수"))

        // ② 📅 이번 달 시공
        val cal = java.util.Calendar.getInstance()
        val thisY = cal.get(java.util.Calendar.YEAR)
        val thisM = cal.get(java.util.Calendar.MONTH) + 1
        val monthCount = jobs.count { j ->
            val d = j.scheduledWorkDate ?: j.workCompletedAt ?: return@count false
            cal.timeInMillis = d
            cal.get(java.util.Calendar.YEAR) == thisY && cal.get(java.util.Calendar.MONTH) + 1 == thisM
        }
        if (monthCount > 0) out.add(BaitChip("📅 ${thisM}월 시공 ${monthCount}곳", "${thisM}월"))

        // ③ 📍 제일 많이 일한 동네
        val region = topRegion(
            jobs.map { (it.address ?: "") to (it.scheduledWorkDate ?: it.workCompletedAt ?: 0L) } +
                customers.map { (it.address ?: "") to (it.scheduledWorkDate ?: it.workCompletedAt ?: 0L) }
        )
        if (region != null) out.add(BaitChip("📍 ${region.first} ${region.second}곳", region.first))

        out.take(3)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 주소들에서 **제일 많이 나온 동네**를 뽑는다. 두 곳 이상 걸릴 때만 — 한 곳이면 미끼가 안 된다.
     *   "…화성시 동탄구 동탄대로24길" → 동탄구 / "옥길동 한신더휴" → 옥길동
     */
    private fun topRegion(addressAndDay: List<Pair<String, Long>>): Pair<String, Int>? {
        val re = Regex("[가-힣]{2,4}(동|읍|면|구)")
        val count = HashMap<String, Int>()
        val latest = HashMap<String, Long>()
        for ((a, day) in addressAndDay) {
            if (a.isBlank()) continue
            val seen = HashSet<String>()
            for (m in re.findAll(a)) if (seen.add(m.value)) {
                count[m.value] = (count[m.value] ?: 0) + 1
                latest[m.value] = maxOf(latest[m.value] ?: 0L, day)
            }
        }
        // 🔴 **동점이 흔하다.** 사장님 자료에서 4곳짜리 동네가 여섯 군데였다(2026-09-19 검산).
        //   maxByOrNull 은 동점이면 아무거나 집어서 앱을 켤 때마다 다른 동네가 떴다.
        //   같으면 **최근에 일한 동네**로 — 지금 머릿속에 있는 현장이라야 누른다.
        val best = count.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenByDescending { latest[it.key] ?: 0L }
                .thenBy { it.key })
            .firstOrNull() ?: return null
        return if (best.value >= 2) best.key to best.value else null
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

/**
 * '못 받은 돈' 을 뜻하는 말들 — 뭘 쳐도 같은 목록이 나온다. (2026-09-19 사장님)
 *   사장님마다 부르는 말이 다르다: 미수 · 못받은 · 잔금 · 미수금.
 */
val UNPAID_WORDS = listOf("미수", "못받은", "못 받은", "잔금")

/** 한 결과에서 펼칠 문장 수. 더 있으면 "N곳 더" 로 접는다. (2026-09-19 사장님 기본값) */
const val MAX_SENTENCES = 3

/**
 * 🪝 미끼 칩 하나 — 보이는 글자와, 누르면 돌아갈 검색어. (2026-09-19 사장님)
 *   label 엔 **내 숫자**가 들어간다("💰 미수 4건"). 그래야 누른다.
 */
data class BaitChip(val label: String, val query: String)

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

/**
 * 검색어 해석 — **여기서만** 판단한다. 단위 테스트로 못 박는다.
 *   (2026-09-19 사장님 — "9월", "지난달" 같은 말을 알아듣게)
 *   한글은 adb 로 못 쳐서 폰에서 눌러볼 수 없다 → 테스트로 확인하는 게 유일한 검증이다.
 */
object SearchQueryParse {
    /** 검색어에서 '몇 년 몇 월' 을 읽는다. 못 읽으면 null. */
    fun monthOf(q: String, nowMs: Long = System.currentTimeMillis()): Pair<Int, Int>? {
        val now = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        val y = now.get(java.util.Calendar.YEAR)
        val m = now.get(java.util.Calendar.MONTH) + 1
        when {
            q.contains("지난달") || q.contains("저번달") ->
                return if (m == 1) (y - 1) to 12 else y to (m - 1)
            q.contains("이번달") || q.contains("이번 달") -> return y to m
            q.contains("다음달") -> return if (m == 12) (y + 1) to 1 else y to (m + 1)
        }
        // "9월", "09월", "9월달", "2026년 9월"
        val mm = Regex("(\\d{1,2})\\s*월").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (mm !in 1..12) return null
        val yy = Regex("(20\\d{2})\\s*년").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: y
        return yy to mm
    }
}
