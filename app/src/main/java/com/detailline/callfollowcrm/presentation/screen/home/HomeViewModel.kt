package com.detailline.callfollowcrm.presentation.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.ai.HistoryMessage
import com.detailline.callfollowcrm.ai.SummaryContext
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.domain.model.CustomerStatus
import com.detailline.callfollowcrm.domain.model.HandledStatus
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val bounds = DateTimeUtils.todayBounds()
    private val todayStart = bounds.first
    private val todayEnd = bounds.second

    private val filter = MutableStateFlow(HomeFilter.ALL)
    val filterState = filter

    private val todayRecords = container.callRecordRepository.observeBetween(todayStart, todayEnd)
    private val customers = container.customerRepository.observeAll()

    // ────────────────────────────────────────────────────────
    // 4 KPI Flows
    // ────────────────────────────────────────────────────────

    /** 오늘 통화한 사람 중 customer.status == "신규 문의" 인 고객 수 (오늘 들어온 새 문의). */
    val todayNewInquiryCount = combine(todayRecords, customers) { records, custs ->
        val byPhone = custs.associateBy { it.phoneNumber }
        records
            .map { it.phoneNumber }
            .distinct()
            .count { phone -> byPhone[phone]?.status == CustomerStatus.NEW_INQUIRY.label }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 오늘 통화 중 후속 처리 안 한 건수. */
    val unhandledCount = container.callRecordRepository.countUnhandled(todayStart, todayEnd)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 오늘 ~ 오늘+6일(7일 윈도우) 시공 예약된 고객 수. */
    val thisWeekScheduledCount = customers.map { list ->
        val now = System.currentTimeMillis()
        val from = DateTimeUtils.startOfDay(now)
        val to = from + 7L * 24 * 60 * 60 * 1000
        list.count { c -> c.scheduledWorkDate?.let { it in from until to } == true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** customer.status == "견적 발송" 인 고객 누적 수 (답 기다리는 견적). */
    val estimateSentCount = customers.map { list ->
        list.count { it.status == CustomerStatus.ESTIMATE_SENT.label }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
     * SMS 만 주고받은 연락처도 HomeScreen 카드로 표시 (2026-05-24 사장님 요청).
     * 통화 기록 없는 SMS-only 번호도 갤럭시 메시지처럼 다 보여야 함.
     *
     * Flow 가 아님 — content provider 는 push 안 보냄. 화면 진입 시 [refreshSmsContacts] 호출.
     * SMS 수신 broadcast 받으면 SmsReceiver 가 이 함수 호출하도록 별도 hook 가능 (지금은 화면 진입만).
     */
    private val smsContactsState = MutableStateFlow<List<SmsRepository.SmsContact>>(emptyList())

    init {
        refreshSmsContacts()
    }

    fun refreshSmsContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = runCatching {
                if (container.smsRepository.hasReadPermission()) {
                    // scanLimit 10000 = 사장님처럼 SMS 17000+ 건 폰에서 옛 사람도 잡히도록 크게.
                    // contactLimit 500 = 고유 번호 최대 500명 표시.
                    container.smsRepository.queryRecentContacts(scanLimit = 10000, contactLimit = 500)
                } else emptyList()
            }.getOrDefault(emptyList())
            smsContactsState.value = list
        }
    }

    val timeline = combine(recentRecords, customers, filter, smsContactsState) { records, custs, f, smsContacts ->
        val byPhone = custs.associateBy { it.phoneNumber }
        val callPhonesNormalized = records.map { phoneSuffix(it.phoneNumber) }.toHashSet()

        // 1) CallRecord 기반 HomeItem (기존 로직 — 번호+날짜 묶음)
        val callItems = records
            .groupBy { it.phoneNumber to DateTimeUtils.startOfDay(it.endedAt) }
            .map { (key, list) ->
                val (phone, _) = key
                val sorted = list.sortedByDescending { it.endedAt }
                HomeItem(
                    record = sorted.first(),
                    customer = byPhone[phone],
                    callCount = list.size,
                    unhandledCount = list.count { it.handledStatus == HandledStatus.UNHANDLED.name }
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
                    handledStatus = HandledStatus.SAVED.name, // SMS-only 는 미처리 표시 안 함
                    linkedCustomerId = byPhone[sms.address]?.id
                )
                HomeItem(
                    record = fakeRecord,
                    customer = byPhone[sms.address]
                        ?: byPhone.values.firstOrNull { phoneSuffix(it.phoneNumber) == sms.normalizedSuffix },
                    callCount = 0,
                    unhandledCount = 0
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
     * HomeScreen 의 가시 카드 변경 알림.
     *  - SMS 캐시 백그라운드 prefetch (기존)
     *  - AI 카드 요약 백그라운드 ensure (P0 — server 미구현이면 silent fail, 캐시는 그대로)
     */
    fun onVisiblePhones(phoneNumbers: Collection<String>) {
        if (phoneNumbers.isEmpty()) return
        container.smsCachePrefetcher.prefetchForNumbers(phoneNumbers)

        viewModelScope.launch(Dispatchers.IO) {
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
            customerStatus = customer?.status,
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
                kotlinx.coroutines.flow.flowOf(emptyMap())
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
 *  - unhandledCount: 그 중 미처리(아직 후속 안 한) 건수
 */
data class HomeItem(
    val record: CallRecordEntity,
    val customer: CustomerEntity?,
    val callCount: Int = 1,
    val unhandledCount: Int = 0
) {
    val anyUnhandled: Boolean get() = unhandledCount > 0
}

/**
 * 홈 메인 리스트(오늘 통화)에 적용되는 필터. KPI 카드 탭으로 자동 설정되거나,
 * 사용자가 칩으로 직접 선택. 칩은 [전체]/[미처리]/[신규] 3개로 축소.
 */
enum class HomeFilter(val label: String) {
    ALL("전체"),
    UNHANDLED("미처리"),
    NEW_INQUIRY("신규");

    fun accept(item: HomeItem): Boolean = when (this) {
        ALL -> true
        UNHANDLED -> item.anyUnhandled
        NEW_INQUIRY -> item.customer?.status == CustomerStatus.NEW_INQUIRY.label
    }
}
