package com.detailline.callfollowcrm.presentation.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.ai.HistoryMessage
import com.detailline.callfollowcrm.ai.SummaryContext
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.domain.model.HandledStatus
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val bounds = DateTimeUtils.todayBounds()
    private val todayStart = bounds.first
    private val todayEnd = bounds.second

    /** "미확인" 7일 윈도우 시작점 (사장님 결정 2026-05-24). 오늘 포함 7일 = 어제 -6일 + 오늘. */
    private val sevenDayWindowStart = todayStart - 6L * 24 * 60 * 60 * 1000

    private val filter = MutableStateFlow<HomeFilter>(HomeFilter.All)
    val filterState = filter

    /** 사장님 정의 카테고리 목록 — 필터 chip row 에 동적 표시. */
    val categories = container.categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val customers = container.customerRepository.observeAll()

    /**
     * SMS 만 주고받은 연락처도 HomeScreen 카드로 표시 (2026-05-24 사장님 요청).
     * 통화 기록 없는 SMS-only 번호도 갤럭시 메시지처럼 다 보여야 함.
     *
     * 2026-05-25: ContentObserver 기반 Flow 로 전환 — 갤메시지가 SMS provider 에 INSERT 하면
     *   즉시 새 데이터 emit → HomeScreen 자동 최신화. 화면 진입만으로는 reload 안 잡히던 문제 해결.
     * debounce 300ms — 여러 PDU 가 연속 도착해도 한 번만 재쿼리.
     */
    private val smsContactsState: StateFlow<List<SmsRepository.SmsContact>> = container.smsRepository
        .observeContacts(scanLimit = 10000, contactLimit = 500)
        .debounce(300)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 최근 7일 내 부재중 통화. 미확인 KPI 의 통화 측 입력. */
    private val missedRecent = container.callRecordRepository.observeMissedSince(sevenDayWindowStart)

    /**
     * 사장님이 미확인 카드 swipe 로 "광고/스팸" 마킹한 phone suffix set.
     *   미확인 판정 / KPI 카운트에서 제외 — 다른 탭 (전체/카테고리) 에는 그대로 표시.
     *   2026-05-25 사장님 결정.
     */
    private val spamSuffixes: StateFlow<Set<String>> = container.spamPhoneRepository.suffixes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 오늘 이전에 통화 기록이 있는 phone suffix set. "오늘 신규" 판정 negative side. */
    private val phonesWithCallsBeforeToday: StateFlow<Set<String>> = container.callRecordRepository
        .observeDistinctPhonesBefore(todayStart)
        .map { list -> list.map { phoneSuffix(it) }.toHashSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * 화면 진입 시 즉시 한 번 강제 갱신용 (Pull-to-refresh 등). ContentObserver 가 못 잡는
     *   edge case (앱 cold start 후 첫 진입) 보강. observeContacts 가 초기 emit 도 하니
     *   대부분 케이스에선 no-op 이지만 안전망.
     */
    fun refreshSmsContacts() {
        // observeContacts 가 초기 + onChange emit 책임. 별도 manual 갱신 필요 없음.
        // 호환을 위해 함수는 유지 — caller 들이 변경 없이 작동.
    }

    // ────────────────────────────────────────────────────────
    // 4 KPI Flows
    // ────────────────────────────────────────────────────────

    /**
     * 오늘 신규 KPI = 오늘 처음 연락온 번호 수 (당일 첫 문의).
     * 판정: SMS 의 lastDateMs ∈ today AND firstDateMsInScan ∈ today AND 그 phone 이 어제 이전 통화 기록 없음.
     * 또는 부재중 통화가 오늘이고 그 phone 의 어제 이전 통화/SMS 기록 없음.
     */
    val todayNewInquiryCount: StateFlow<Int> = combine(
        smsContactsState, missedRecent, phonesWithCallsBeforeToday
    ) { smsContacts, missed, callsBefore ->
        newTodaySuffixes(smsContacts, missed, callsBefore).size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * 미확인 KPI = 최근 7일 내 문의 (SMS 수신 또는 부재중 통화) 받았는데 사장님이 답장 한 번도 안 한 번호 수.
     * 사장님 결정 (2026-05-24) — 이전엔 CallRecord.handledStatus 기반이었으나 정의 변경.
     */
    val unhandledCount: StateFlow<Int> = combine(
        smsContactsState, missedRecent, spamSuffixes
    ) { smsContacts, missed, spam ->
        unconfirmedSuffixes(smsContacts, missed, spam).size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 오늘 ~ 오늘+6일(7일 윈도우) 시공 예약된 고객 수. */
    val thisWeekScheduledCount = customers.map { list ->
        val now = System.currentTimeMillis()
        val from = DateTimeUtils.startOfDay(now)
        val to = from + 7L * 24 * 60 * 60 * 1000
        list.count { c -> c.scheduledWorkDate?.let { it in from until to } == true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // 2026-05-25: estimateSentCount 제거 — KPI "견적 답대기" 카드 폐기.

    /**
     * "미확인" 판정 = phone suffix set.
     *   - SMS: 7일 윈도우 안 받은 SMS (lastSent=false 이고 lastDateMs ∈ 윈도우) + hasOwnerReply=false
     *   - 부재중 통화: 7일 윈도우 안 MISSED + 그 phone 의 sent SMS 가 1건도 없음
     */
    private fun unconfirmedSuffixes(
        smsContacts: List<SmsRepository.SmsContact>,
        missed: List<CallRecordEntity>,
        spam: Set<String> = emptySet()
    ): Set<String> {
        val bySuffix = smsContacts.associateBy { it.normalizedSuffix }
        val result = HashSet<String>()
        for (c in smsContacts) {
            if (c.normalizedSuffix in spam) continue
            if (!c.hasOwnerReply &&
                !c.lastSent &&
                c.lastDateMs >= sevenDayWindowStart
            ) {
                result += c.normalizedSuffix
            }
        }
        for (m in missed) {
            if (m.endedAt < sevenDayWindowStart) continue
            val suffix = phoneSuffix(m.phoneNumber)
            if (suffix in spam) continue
            val sms = bySuffix[suffix]
            if (sms == null || !sms.hasOwnerReply) result += suffix
        }
        return result
    }

    /**
     * "오늘 신규" 판정 = phone suffix set.
     *   - SMS: lastDateMs ∈ today AND firstDateMsInScan ∈ today (스캔 안 첫 등장 = 오늘) AND 어제 이전 통화 기록 없음
     *   - 부재중 통화: endedAt ∈ today AND 어제 이전 통화 기록 없음 AND SMS 도 어제 이전 기록 없음
     */
    private fun newTodaySuffixes(
        smsContacts: List<SmsRepository.SmsContact>,
        missed: List<CallRecordEntity>,
        callsBefore: Set<String>
    ): Set<String> {
        val bySuffix = smsContacts.associateBy { it.normalizedSuffix }
        val result = HashSet<String>()
        for (c in smsContacts) {
            if (c.lastDateMs in todayStart..todayEnd &&
                c.firstDateMsInScan >= todayStart &&
                c.normalizedSuffix !in callsBefore
            ) {
                result += c.normalizedSuffix
            }
        }
        for (m in missed) {
            if (m.endedAt !in todayStart..todayEnd) continue
            val suffix = phoneSuffix(m.phoneNumber)
            if (suffix in callsBefore) continue
            val sms = bySuffix[suffix]
            if (sms == null || sms.firstDateMsInScan >= todayStart) result += suffix
        }
        return result
    }

    // ────────────────────────────────────────────────────────
    // Today list (메인 리스트)
    // ────────────────────────────────────────────────────────

    /**
     * 페이지네이션 — 처음엔 [PAGE_SIZE] 개만 로드. 스크롤 내리면 [loadMore] 가 incrementing.
     * Room 의 Flow 는 query 인자 변하면 다시 emit 되므로 flatMapLatest 로 limit 갈아끼움.
     */
    private val recordsLimit = MutableStateFlow(PAGE_SIZE)
    val recordsLimitState = recordsLimit

    /** UI 가 "더 이상 위로 안 늘어남" 을 판단하려면 현재 row 수가 limit 에 닿았는지 비교. */
    fun loadMore() {
        recordsLimit.value = recordsLimit.value + PAGE_SIZE
    }

    /**
     * 메인 타임라인 — 페이지 크기에 맞춰 통화 기록을 (번호, 날짜) 단위로 묶어 그룹화.
     * 갤럭시 통화 기록처럼 위에서 아래로 오늘 → 어제 → ... 순서.
     */
    private val recentRecords = recordsLimit.flatMapLatest { lim ->
        container.callRecordRepository.observeRecent(limit = lim)
    }

    /**
     * 미확인/신규 plus 다른 타임라인 입력을 한 번에 묶기 위해 derived state 로 미리 묶음.
     * combine 의 인자 수 제한 회피 + 한 번 계산해 캐시.
     */
    private data class TimelineFlags(
        val unconfirmedSuffixes: Set<String>,
        val newTodaySuffixes: Set<String>
    )

    private val timelineFlags: StateFlow<TimelineFlags> = combine(
        smsContactsState, missedRecent, phonesWithCallsBeforeToday, spamSuffixes
    ) { smsContacts, missed, callsBefore, spam ->
        TimelineFlags(
            unconfirmedSuffixes = unconfirmedSuffixes(smsContacts, missed, spam),
            newTodaySuffixes = newTodaySuffixes(smsContacts, missed, callsBefore)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineFlags(emptySet(), emptySet()))

    val timeline = combine(
        recentRecords, customers, filter, smsContactsState, timelineFlags
    ) { records, custs, f, smsContacts, flags ->
        val byPhone = custs.associateBy { it.phoneNumber }
        val callPhonesNormalized = records.map { phoneSuffix(it.phoneNumber) }.toHashSet()

        // 1) CallRecord 기반 HomeItem (기존 로직 — 번호+날짜 묶음)
        val callItems = records
            .groupBy { it.phoneNumber to DateTimeUtils.startOfDay(it.endedAt) }
            .map { (key, list) ->
                val (phone, _) = key
                val suffix = phoneSuffix(phone)
                val sorted = list.sortedByDescending { it.endedAt }
                HomeItem(
                    record = sorted.first(),
                    customer = byPhone[phone],
                    callCount = list.size,
                    isUnconfirmed = suffix in flags.unconfirmedSuffixes,
                    isNewToday = suffix in flags.newTodaySuffixes
                )
            }

        // 2) SMS-only HomeItem — CallRecord 에 없는 SMS 연락처들. fake CallRecord 만들어 통일된 UI 사용.
        //    fake id 는 음수 (lastDateMs 부정) — Room insert 안 함, in-memory 표시용. 충돌 방지.
        val smsOnlyItems = smsContacts
            .filter { phoneSuffix(it.address) !in callPhonesNormalized }
            .map { sms ->
                val fakeRecord = CallRecordEntity(
                    id = -sms.lastDateMs,
                    phoneNumber = sms.address,
                    callType = CALL_TYPE_SMS_ONLY,
                    duration = 0L,
                    startedAt = null,
                    endedAt = sms.lastDateMs,
                    handledStatus = HandledStatus.SAVED.name,
                    linkedCustomerId = byPhone[sms.address]?.id
                )
                HomeItem(
                    record = fakeRecord,
                    customer = byPhone[sms.address]
                        ?: byPhone.values.firstOrNull { phoneSuffix(it.phoneNumber) == sms.normalizedSuffix },
                    callCount = 0,
                    isUnconfirmed = sms.normalizedSuffix in flags.unconfirmedSuffixes,
                    isNewToday = sms.normalizedSuffix in flags.newTodaySuffixes
                )
            }

        // 3) 합쳐서 필터 → 날짜 그룹화 → 정렬
        (callItems + smsOnlyItems)
            .filter { f.accept(it) }
            .groupBy { DateTimeUtils.startOfDay(it.record.endedAt) }
            .toSortedMap(compareByDescending { it })
            .map { (dayStart, items) ->
                DayGroup(dayStartMs = dayStart, items = items.sortedByDescending { it.record.endedAt })
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun phoneSuffix(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 8) digits.takeLast(8) else digits
    }

    fun setFilter(f: HomeFilter) { filter.value = f }

    /**
     * 미확인 카드 swipe → "광고/스팸" 으로 영구 마킹. 미확인 카테고리에서 제외.
     *   suffix 정규화는 ViewModel 안에서 — 호출자는 raw phone 만 넘김.
     *   Snackbar Undo 가 [unmarkSpam] 호출.
     */
    fun markSpam(phoneNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.spamPhoneRepository.mark(phoneSuffix(phoneNumber))
        }
    }

    fun unmarkSpam(phoneNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.spamPhoneRepository.unmark(phoneSuffix(phoneNumber))
        }
    }

    /**
     * [ⓘ 고객 카드] 진입용 — Customer entity 없으면 자동 생성하고 id 반환.
     *   사장님 결정 2026-05-25: 카드 펼침의 [ⓘ] 액션 항상 활성화. 빈 Customer 자동 정리는
     *   기존 deleteOrphans (이름/메모/메시지 기록 없는 고아 고객) 가 처리.
     */
    suspend fun ensureCustomerForPhone(phoneNumber: String): Long =
        withContext(Dispatchers.IO) {
            container.customerRepository.upsertByPhone(phoneNumber).id
        }

    /**
     * [📍 길찾기] 가 사용 — phone 에 묶인 목적지 destinationName 추출.
     *
     * 우선순위 (먼저 매칭되는 것 반환):
     *   1) customer.address (사장님 수동 등록, DB v15, 2026-05-28) — **신뢰 최우선**
     *   2) cached SMS 50건에서 AddressExtractor 정규식 매칭 → "서울 강서구 마곡동 740" 같은 풀 주소
     *   3) customer.memo 안의 주소 패턴
     *   4) 다 없음 → null (UI 가 "주소 정보 없음 + 등록 안내" 토스트 + AddressEditDialog 유도)
     *
     * customer.name 은 fallback 에서 **제외** (2026-05-28 결정):
     *   - "김철수" 같은 인명이 destinationName 으로 넘어가면 카카오맵/네이버지도에서 엉뚱한 곳 검색됨
     *   - 사장님이 길찾기 신뢰 잃으면 RING-GO 전체 평가 낮아짐
     *   - "엘테라스" 같은 현장명 케이스는 사장님이 수동 등록(#1) 로 흡수
     *
     * 현재는 좌표 없이 destinationName 만 반환 → NavApp 의 search 모드.
     * §13 (서버 아파트 주소 resolve) 끝나면 ResolvedDestination(name, lat?, lng?) 으로 확장 예정.
     */
    suspend fun resolveAddressForPhone(phoneNumber: String): String? =
        withContext(Dispatchers.IO) {
            val digits = phoneNumber.filter { it.isDigit() }
            if (digits.length < 7) return@withContext null
            val suffix = digits.takeLast(8)

            val customer = runCatching {
                container.customerRepository.findByPhone(phoneNumber)
            }.getOrNull()

            // 1) 사장님 수동 등록 — 최우선 (자동 추출이 부정확해도 사장님이 박은 게 정답)
            customer?.address?.takeIf { it.isNotBlank() }?.let { return@withContext it }

            // 2) cached 메시지에서 추출 — 최신순.
            val cached = runCatching {
                container.cachedMessageRepository.load(suffix, limit = 50)
            }.getOrDefault(emptyList())
            com.detailline.callfollowcrm.util.AddressExtractor.extractFromMessages(
                cached.sortedByDescending { it.dateMs }.map { it.body }
            )?.let { return@withContext it }

            // 3) memo 에서 추출
            customer?.memo?.takeIf { it.isNotBlank() }?.let { memo ->
                com.detailline.callfollowcrm.util.AddressExtractor.extractOne(memo)
                    ?.let { return@withContext it }
            }

            return@withContext null
        }

    /** + 버튼 다이얼로그에서 사장님이 카테고리 추가. 추가 후 자동 선택. */
    fun addCategory(name: String, emoji: String?) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val entity = runCatching {
                container.categoryRepository.upsert(name, emoji)
            }.getOrNull() ?: return@launch
            filter.value = HomeFilter.Category(entity.id, entity.name, entity.emoji)
        }
    }

    /**
     * HomeScreen 의 가시 카드 변경 알림.
     *  - SMS 캐시 백그라운드 prefetch (기존)
     *  - AI 카드 요약 백그라운드 ensure (P0 — server 미구현이면 silent fail, 캐시는 그대로)
     *
     * 마우스 휠 / 빠른 fling 으로 짧은 시간 안 폭주성 호출 방지:
     *  - 같은 set 으로 호출 시 skip (lastVisibleHash)
     *  - 직전 ensure job 이 아직 도는 중이면 cancel + 새 job
     */
    private var lastVisibleHash: Int = 0
    private var ensureJob: kotlinx.coroutines.Job? = null
    fun onVisiblePhones(phoneNumbers: Collection<String>) {
        if (phoneNumbers.isEmpty()) return
        val hash = phoneNumbers.toSet().hashCode()
        if (hash == lastVisibleHash) return
        lastVisibleHash = hash

        container.smsCachePrefetcher.prefetchForNumbers(phoneNumbers)

        ensureJob?.cancel()
        ensureJob = viewModelScope.launch(Dispatchers.IO) {
            for (phone in phoneNumbers) {
                val ctx = buildCardSummaryContext(phone) ?: continue
                runCatching { container.conversationAiRepository.ensureCardSummary(ctx) }
            }
        }
    }

    /**
     * AI 카드 요약 호출용 SummaryContext 구성. 시스템 SMS 캐시에서 최근 20건 + 사장님 톤 코퍼스.
     * 캐시에 메시지 1건도 없으면 null (서버 호출해도 의미 없음).
     */
    private suspend fun buildCardSummaryContext(phoneNumber: String): SummaryContext? {
        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.length < 7) return null
        val suffix = digits.takeLast(8)

        val cached = runCatching { container.cachedMessageRepository.load(suffix, limit = 20) }
            .getOrDefault(emptyList())
        if (cached.isEmpty()) return null

        val latestTs = cached.maxOf { it.dateMs }
        val customer = runCatching { container.customerRepository.findByPhone(phoneNumber) }.getOrNull()
        val tone = runCatching { container.smsRepository.querySentMessages(limit = 50) }.getOrDefault(emptyList())

        return SummaryContext(
            phone = phoneNumber,
            phoneSuffix = suffix,
            customerName = customer?.name,
            customerMemo = customer?.memo?.takeIf { it.isNotBlank() },
            leadHeat = customer?.leadHeat,
            depositPaid = (customer?.depositAmount ?: 0L) > 0L,
            scheduledWorkDate = customer?.scheduledWorkDate,
            recentMessages = cached.map { sms ->
                HistoryMessage(
                    role = if (sms.sent) "owner" else "customer",
                    body = sms.body,
                    timestampMs = sms.dateMs
                )
            }.reversed(),
            ownerToneSamples = tone,
            latestMessageTimestampMs = latestTs
        )
    }

    /**
     * HomeRow 가 카드별 AI 요약을 표시하려면 phone → cardSummary 매핑이 필요.
     * combine 으로 전체 카드의 summary 한 번에 구독 (Dao 가 List<Entity> 반환).
     * 실시간 — 서버 응답이 dao.upsert 되면 자동 emit.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val cardSummariesByPhoneSuffix: kotlinx.coroutines.flow.StateFlow<Map<String, String>> =
        timeline.flatMapLatest { dayGroups ->
            val suffixes = dayGroups.flatMap { it.items }.map { phoneSuffix(it.record.phoneNumber) }.distinct()
            if (suffixes.isEmpty()) {
                flowOf(emptyMap())
            } else {
                container.conversationAiRepository.observeMany(suffixes).map { list ->
                    list.mapNotNull { e -> e.cardSummary?.let { e.phoneSuffix to it } }.toMap()
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        /** 한 페이지 = 20개. 사장님 요청 (2026-05-24). */
        const val PAGE_SIZE = 20

        /** SMS 만 주고받은 가짜 CallRecord 의 callType 마커. HomeScreen 의 callTypeLabel 분기에 사용. */
        const val CALL_TYPE_SMS_ONLY = "SMS_ONLY"
    }
}

/** 날짜별 그룹 — 헤더 라벨용 dayStartMs + 그 날 통화 묶음 items. */
data class DayGroup(
    val dayStartMs: Long,
    val items: List<HomeItem>
)

/**
 * 홈 리스트의 한 행. 같은 번호의 오늘자 통화를 묶어서 보여준다.
 *
 *  - record: 가장 최근 통화 (시간/타입 표시용)
 *  - callCount: 묶인 통화 건수
 *  - isUnconfirmed: 미확인 (7일 내 문의 + 답장 0) — 사장님 결정 2026-05-24
 *  - isNewToday: 오늘 신규 (당일 첫 문의 + 어제 이전 기록 없음)
 */
data class HomeItem(
    val record: CallRecordEntity,
    val customer: CustomerEntity?,
    val callCount: Int = 1,
    val isUnconfirmed: Boolean = false,
    val isNewToday: Boolean = false
)

/**
 * 홈 메인 리스트에 적용되는 필터.
 *
 * 2026-05-25: 갤메시지 식 — 시스템 필터 (전체/미확인) + 사장님 정의 카테고리.
 *   미확인 = 7일 내 처음 연락 + 답장 X (자동 계산, 사장님이 못 지움).
 *   카테고리 = AI 자동 분류 + 수동.
 */
sealed class HomeFilter(val label: String) {
    object All : HomeFilter("전체")
    object Unconfirmed : HomeFilter("미확인")
    data class Category(val id: Long, val name: String, val emoji: String?) : HomeFilter(name)

    fun accept(item: HomeItem): Boolean = when (this) {
        is All -> true
        is Unconfirmed -> item.isUnconfirmed
        is Category -> item.customer?.categoryId == id
    }
}
