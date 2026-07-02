package com.detailline.callfollowcrm.presentation.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.ai.HistoryMessage
import com.detailline.callfollowcrm.ai.SummaryContext
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.domain.model.HandledStatus
import com.detailline.callfollowcrm.domain.settlement.SettlementCalc
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    // 자정 넘어 앱 재진입 시 날짜 재계산용 tick. refreshSmsContacts() 가 갱신 → todayJobs 재필터링.
    private val _todayTick = MutableStateFlow(System.currentTimeMillis())

    private val bounds = DateTimeUtils.todayBounds()
    private val todayStart = bounds.first
    private val todayEnd = bounds.second

    /** "미확인" 7일 윈도우 시작점 (사장님 결정 2026-05-24). 오늘 포함 7일 = 어제 -6일 + 오늘. */
    private val sevenDayWindowStart = todayStart - 6L * 24 * 60 * 60 * 1000

    /** 어제 0시 — 프로토 today-new 카드의 "어제 M통" 비교용. */
    private val yesterdayStart = todayStart - 24L * 60 * 60 * 1000

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
    // 2026-05-28 본격 fix: SmsRepository.observeContacts (매번 17000건 풀스캔) → Room 캐시 observe.
    //   첫 실행 시 Application 이 풀스캔 → sms_contacts_cache 채움. 그 후 instant Room observe.
    //   새 SMS = SmsReceiver 가 upsertOne 으로 즉시 캐시 update → HomeScreen 도 자동 emit.
    //   재시작 후엔 캐시 비어있지 않으면 풀스캔 skip → cold start 도 즉시 표시.
    //   pendingNewSmsContacts 는 그대로 두되 SmsReceiver 가 캐시 upsert 도 하므로 사실상 자연 dedup.
    private val smsContactsState: StateFlow<List<SmsRepository.SmsContact>> =
        container.smsContactCacheRepository
            .observeAll(limit = 500)
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 첫 캐시 emit 전까지 true → HomeScreen 이 상단에 작은 progress bar 표시.
     *   대부분 케이스에서 캐시는 채워져 있어 빠른 emit. 첫 실행 시만 짧게 보임.
     */
    val isInitialSmsLoading: StateFlow<Boolean> = container.smsContactCacheRepository
        .observeAll(limit = 500)
        .map { false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 최근 7일 내 부재중 통화. 미확인 KPI 의 통화 측 입력. */
    private val missedRecent = container.callRecordRepository.observeMissedSince(sevenDayWindowStart)

    /** 최근 7일 내 들어온 통화(수신·부재중·거절). "오늘/어제 신규 문의" 집계용 — 받은 전화도 포함. */
    private val inboundRecent = container.callRecordRepository.observeInboundSince(sevenDayWindowStart)

    /** 최근 7일 내 통화로 응대한 기록(수신 응답·발신). "답장 기다려요"에서 전화로 처리한 건 빼는 용도. (2026-07-02 사장님) */
    private val handledCallsRecent = container.callRecordRepository.observeHandledCallsSince(sevenDayWindowStart)

    /**
     * missed·inbound·(suffix→통화응대 최신시각) 묶음. 미확인=부재중(missed), 오늘신규=들어온통화(inbound),
     *   그리고 "통화로 응대함" 판정을 위한 handledCallMs 를 한 combine 안에서 함께 공급(combine 5개 한도 회피).
     *   OUTGOING(내가 콜백)은 missed/inbound 어디에도 없어 이 flow 가 있어야 콜백 시 목록이 재계산된다.
     */
    private val callsForFlags = combine(missedRecent, inboundRecent, handledCallsRecent) { m, i, handled ->
        val handledMs = HashMap<String, Long>()
        for (c in handled) {
            val suf = phoneSuffix(c.phoneNumber)
            if (c.endedAt > (handledMs[suf] ?: 0L)) handledMs[suf] = c.endedAt
        }
        Triple(m, i, handledMs as Map<String, Long>)
    }

    /**
     * 사장님이 미확인 카드 swipe 로 "광고/스팸" 마킹한 phone suffix set.
     *   미확인 판정 / KPI 카운트에서 제외 — 다른 탭 (전체/카테고리) 에는 그대로 표시.
     *   2026-05-25 사장님 결정.
     */
    private val spamSuffixes: StateFlow<Set<String>> = container.spamPhoneRepository.suffixes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    /** 스팸 앞자리(070 등) — 미확인/신규/목록에서 제외. (2026-06-07)
     *   prefs 는 비반응형 → StateFlow 로 들고 새로고침(refreshSmsContacts) 때 다시 읽어 즉시 반영. (2026-06-17 사장님) */
    private val spamPrefixesFlow = MutableStateFlow(container.preferences.spamPrefixes)

    /**
     * 스팸 판정 = swipe 마킹(suffix) ∪ 앞자리 등록(prefix). 둘 중 하나라도 걸리면 상담함 목록·집계에서 제외.
     *   2026-06-17 사장님: "스팸 등록되면 상담함에 들어오지 못함" — 들어오지 못하니 추천도 준비 안 함.
     */
    private val spamGate: StateFlow<SpamGate> =
        combine(spamSuffixes, spamPrefixesFlow) { suf, pre -> SpamGate(suf, pre) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpamGate(emptySet(), emptySet()))

    /** 오늘 이전에 통화 기록이 있는 phone suffix set. "오늘 신규" 판정 negative side. */
    private val phonesWithCallsBeforeToday: StateFlow<Set<String>> = container.callRecordRepository
        .observeDistinctPhonesBefore(todayStart)
        .map { list -> list.map { phoneSuffix(it) }.toHashSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * 2026-05-30 사장님 #3 통점 — 시공 일정 등록된 고객 phone suffix set.
     *   사장님 결정: 시공 일정 등록 = "상황 종료" → 미확인 카운트 / NextActionBox / 후속 알림 모두 제외.
     *   D-1 알림은 Phase B 에서 별도.
     */
    private val scheduledCustomerSuffixes: StateFlow<Set<String>> = customers
        .map { list ->
            list.filter { it.scheduledWorkDate != null && it.scheduledWorkDate > 0L }
                .map { phoneSuffix(it.phoneNumber) }
                .toHashSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * 화면 진입 시 즉시 한 번 강제 갱신용 (Pull-to-refresh 등). ContentObserver 가 못 잡는
     *   edge case (앱 cold start 후 첫 진입) 보강. observeContacts 가 초기 emit 도 하니
     *   대부분 케이스에선 no-op 이지만 안전망.
     */
    fun refreshSmsContacts() {
        // observeContacts 가 초기 + onChange emit 책임. 별도 manual 갱신 필요 없음.
        // 단, 스팸 앞자리(prefs)는 비반응형 → 새로고침 때 다시 읽어 방금 등록한 스팸을 목록에서 즉시 제외. (2026-06-17 사장님)
        spamPrefixesFlow.value = container.preferences.spamPrefixes
        // D-1 토글도 비반응형 prefs → 새로고침 때 재읽기(토글 끄면 홈 D-1 카드 사라지게). (2026-06-18 사장님)
        d1EnabledFlow.value = container.preferences.d1AutoEnabled
        // 자정 넘어 재진입 시 todayJobs 날짜 재계산 트리거.
        _todayTick.value = System.currentTimeMillis()
    }

    /**
     * 시스템 CallLog → Room sync (2026-05-28 사장님 통점):
     *   "통화 끝났는데 RING-GO 목록에 안 들어옴".
     *   원인 = Android 12+ / OneUI 의 정적 BroadcastReceiver 누락 (CallStateReceiver 미트리거).
     *   해결 = HomeScreen 진입 시 + Pull-to-refresh 시 CallLog 폴링 → 누락분 INSERT.
     *
     *   non-blocking + dedup. Room observe 가 자동 emit 하므로 화면 갱신 별도 처리 X.
     */
    fun syncRecentCallLog(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                container.callRecordRepository.syncRecentCallLog(context, limit = 30)
            }
        }
    }

    // ────────────────────────────────────────────────────────
    // 4 KPI Flows
    // ────────────────────────────────────────────────────────

    /**
     * 오늘 신규 KPI = 오늘 처음 연락온 번호 수 (당일 첫 문의, "새 번호 기준").
     * 판정: SMS 의 lastDateMs ∈ today AND firstDateMsInScan ∈ today AND 그 phone 이 어제 이전 통화 기록 없음.
     * 또는 들어온 통화(수신·부재중·거절)가 오늘이고 그 phone 의 어제 이전 통화/SMS 기록 없음.
     *   2026-06-02 fix: 통화 측을 부재중→들어온 통화 전체로 확장(받은 전화도 신규 문의로 집계).
     *   주의: "신규"=처음 연락온 번호. 전에 연락온 적 있는 번호는 제외(설계 = 부제 "새 번호 기준").
     */
    val todayNewInquiryCount: StateFlow<Int> = combine(
        smsContactsState, inboundRecent, phonesWithCallsBeforeToday, spamSuffixes, spamPrefixesFlow
    ) { smsContacts, inbound, callsBefore, spam, _ ->
        newTodaySuffixes(smsContacts, inbound, callsBefore, spam).size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 어제 이전에 통화 기록이 있는 phone suffix set — "어제 신규" 판정용. */
    private val phonesWithCallsBeforeYesterday: StateFlow<Set<String>> = container.callRecordRepository
        .observeDistinctPhonesBefore(yesterdayStart)
        .map { list -> list.map { phoneSuffix(it) }.toHashSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * 어제 신규 문의 수 (프로토 today-new 카드의 "어제 M통" + ▲▼ 비교용).
     *   판정: 스캔 안 첫 등장(firstDateMsInScan)이 어제 [yesterdayStart, todayStart) AND 어제 이전 통화 기록 없음.
     */
    val yesterdayNewInquiryCount: StateFlow<Int> = combine(
        smsContactsState, inboundRecent, phonesWithCallsBeforeYesterday, spamSuffixes, spamPrefixesFlow
    ) { smsContacts, inbound, callsBefore, spam, _ ->
        val bySuffix = smsContacts.associateBy { it.normalizedSuffix }
        val result = HashSet<String>()
        for (c in smsContacts) {
            if (c.normalizedSuffix in spam) continue
            if (com.detailline.callfollowcrm.util.SpamPrefix.isSpam(c.address, spamPrefixesFlow.value)) continue
            if (c.firstDateMsInScan in yesterdayStart until todayStart && c.normalizedSuffix !in callsBefore) {
                result += c.normalizedSuffix
            }
        }
        for (m in inbound) {
            if (m.endedAt !in yesterdayStart until todayStart) continue
            val suffix = phoneSuffix(m.phoneNumber)
            if (suffix in spam) continue
            if (com.detailline.callfollowcrm.util.SpamPrefix.isSpam(m.phoneNumber, spamPrefixesFlow.value)) continue
            if (suffix in callsBefore) continue
            val sms = bySuffix[suffix]
            if (sms == null || sms.firstDateMsInScan >= yesterdayStart) result += suffix
        }
        result.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * 미확인 KPI = 최근 7일 내 문의 (SMS 수신 또는 부재중 통화) 받았는데 사장님이 답장 한 번도 안 한 번호 수.
     * 사장님 결정 (2026-05-24) — 이전엔 CallRecord.handledStatus 기반이었으나 정의 변경.
     */
    val unhandledCount: StateFlow<Int> = combine(
        smsContactsState, callsForFlags, spamSuffixes, scheduledCustomerSuffixes, spamPrefixesFlow
    ) { smsContacts, calls, spam, scheduled, _ ->
        val (missed, _, handledMs) = calls
        unconfirmedSuffixes(smsContacts, missed, spam, scheduled, handledMs).size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 오늘 ~ 오늘+6일(7일 윈도우) 시공 예약된 고객 수. */
    val thisWeekScheduledCount = customers.map { list ->
        val now = System.currentTimeMillis()
        val from = DateTimeUtils.startOfDay(now)
        val to = from + 7L * 24 * 60 * 60 * 1000
        list.count { c -> c.scheduledWorkDate?.let { it in from until to } == true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // 2026-05-25: estimateSentCount 제거 — KPI "견적 답대기" 카드 폐기.

    // ────────────────────────────────────────────────────────
    // 정산 — 홈 미수금 진입 카드 (정산 Phase 1, 2026-06-01)
    //   계산은 SettlementScreen 과 동일한 SettlementCalc 단일 출처.
    // ────────────────────────────────────────────────────────

    /** 아직 못 받은 돈 총액 (돈 정보 있는 고객의 미수 합). */
    val outstandingTotal: StateFlow<Long> = customers.map { list ->
        list.filter { SettlementCalc.hasMoney(it) }.sumOf { SettlementCalc.rowOf(it).outstanding }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 미수(못 받은 돈 > 0)인 고객 수. */
    val outstandingCount: StateFlow<Int> = customers.map { list ->
        list.filter { SettlementCalc.hasMoney(it) }.count { SettlementCalc.rowOf(it).outstanding > 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ────────────────────────────────────────────────────────
    // 부재중 → 자동답장 카드 (상담함, 2026-06-01)
    //   AutoReplyScheduler 가 첫 통화(부재중/수신)에 자동 발송한 기록을 홈 상단에 노출.
    //   "막내 비서가 사장님 대신 첫 인사를 보냈어요" — 투명성/신뢰. 24시간 윈도우 → 자동 만료.
    //   AUTO_SENT(보냄)/AUTO_FAILED(실패)만. CANCELLED 는 사장님 본인 취소라 안 보여줌.
    // ────────────────────────────────────────────────────────
    private val autoReplySinceMs = System.currentTimeMillis() - 24L * 60 * 60 * 1000

    /** 밀어서 정리한 자동답장 배너 id (prefs 영속). 다시 안 뜨게. */
    private val dismissedAutoReplyIds = MutableStateFlow(
        container.preferences.dismissedAutoReplyIds.mapNotNull { it.toLongOrNull() }.toSet()
    )

    /** 홈 배너 "부재중 자동답장 보냄" 밀어서 정리. */
    fun dismissAutoReply(id: Long) {
        val next = dismissedAutoReplyIds.value + id
        dismissedAutoReplyIds.value = next
        container.preferences.dismissedAutoReplyIds = next.map { it.toString() }.toSet()
    }

    // 카운트 배너(견적 회신·정기문자) 밀어서 정리 = 오늘 하루 숨김(다음날 다시).
    private val estimateFollowupDismissedDay = MutableStateFlow(container.preferences.estimateFollowupDismissedDay)
    val estimateFollowupDismissed: StateFlow<Boolean> = estimateFollowupDismissedDay
        .map { it == todayStart }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            container.preferences.estimateFollowupDismissedDay == todayStart)
    fun dismissEstimateFollowup() {
        estimateFollowupDismissedDay.value = todayStart
        container.preferences.estimateFollowupDismissedDay = todayStart
    }

    private val recurringDueDismissedDay = MutableStateFlow(container.preferences.recurringDueDismissedDay)
    val recurringDueDismissed: StateFlow<Boolean> = recurringDueDismissedDay
        .map { it == todayStart }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            container.preferences.recurringDueDismissedDay == todayStart)
    fun dismissRecurringDue() {
        recurringDueDismissedDay.value = todayStart
        container.preferences.recurringDueDismissedDay = todayStart
    }

    val autoReplies: StateFlow<List<AutoReplyItem>> = combine(
        container.messageHistoryRepository.observeRecentAutoReplies(autoReplySinceMs, limit = 5),
        customers,
        dismissedAutoReplyIds
    ) { histories, custs, dismissed ->
        val bySuffix = custs.associateBy { phoneSuffix(it.phoneNumber) }
        histories.filter { it.id !in dismissed }.map { h ->
            val c = h.customerId?.let { id -> custs.firstOrNull { it.id == id } }
                ?: bySuffix[phoneSuffix(h.phoneNumber)]
            AutoReplyItem(
                id = h.id,
                phone = h.phoneNumber,
                customerId = c?.id,
                customerName = c?.name?.takeIf { it.isNotBlank() },
                body = h.messageBody,
                failed = h.status == "AUTO_FAILED",
                createdAt = h.createdAt
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ────────────────────────────────────────────────────────
    // 팀원 진행 알림 (상담함, 2026-06-06 사장님 요청)
    //   TeamEventCenter 가 서버 폴링으로 채운 오늘 출발/도착/완료를 배너로. 밀어서 정리(event_id 영속).
    // ────────────────────────────────────────────────────────
    private val dismissedTeamDepartIds = MutableStateFlow(
        container.preferences.dismissedTeamDepartIds.mapNotNull { it.toLongOrNull() }.toSet()
    )

    /** 상담함 "팀원 진행" 배너 밀어서 정리. */
    fun dismissTeamUpdate(eventId: Long) {
        val next = dismissedTeamDepartIds.value + eventId
        dismissedTeamDepartIds.value = next
        container.preferences.dismissedTeamDepartIds = next.map { it.toString() }.toSet()
    }

    val teamUpdates: StateFlow<List<com.detailline.callfollowcrm.ai.TeamEventCenter.TeamUpdate>> =
        combine(container.teamEventCenter.todayUpdates, dismissedTeamDepartIds) { list, dismissed ->
            list.filter { it.eventId !in dismissed }.sortedByDescending { it.createdAtMs }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ────────────────────────────────────────────────────────
    // 협업 진행 알림 (상담함, 2026-06-09)
    //   CollabEventCenter 가 서버 owner-events 로 채운 오늘 출발/도착/완료를 배너로.
    // ────────────────────────────────────────────────────────
    private val dismissedCollabEventIds = MutableStateFlow(container.preferences.dismissedCollabEventIds)

    fun dismissCollabUpdate(eventId: String) {
        val next = dismissedCollabEventIds.value + eventId
        dismissedCollabEventIds.value = next
        container.preferences.dismissedCollabEventIds = next
    }

    /**
     * 일당/협업 완료 알림 → "입금했어요" 시 정산에 일당 지급(지출) 자동 기록 + 알림 정리.
     *   (일당 마켓 Phase 1 — 완료·계좌 = 정산 스위치. docs/PLAN_labor_market.md)
     *   manwon = 만원 단위. 0 이면 금액 없이 알림만 정리.
     */
    fun recordLaborPayment(partnerName: String, manwon: Long, eventId: String) = viewModelScope.launch {
        if (manwon > 0L) {
            runCatching {
                container.manualCashRepository.add(
                    dayMs = System.currentTimeMillis(),
                    amount = manwon * 10_000L,
                    isIncome = false,
                    isDone = true,
                    label = "일당 지급 · ${partnerName.ifBlank { "협업" }}"
                )
            }
        }
        dismissCollabUpdate(eventId)
    }

    val collabUpdates: StateFlow<List<com.detailline.callfollowcrm.ai.CollabEventCenter.CollabUpdate>> =
        combine(container.collabEventCenter.todayUpdates, dismissedCollabEventIds) { list, dismissed ->
            list.filter { it.eventId !in dismissed }.sortedByDescending { it.createdAtMs }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 받은 협업 요청(수락 대기) — 상담함 카드. 푸시를 실수로 지워도 여기서 찾아 수락. 응답하면 자동으로 빠짐. (2026-06-14) */
    val pendingCollabInvites: StateFlow<List<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite>> =
        container.collabEventCenter.pendingInvites
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 수락한 협업 현장 — 홈 상단 카드. 오늘·내일 것만 보여줌(모레+ 는 홈에서 빼고 '협업 현장' 탭에서만).
     *   (2026-06-21 사장님: 모레·먼 날짜까지 홈에 계속 떠서 "이게 왜 떠있지" 혼란 → D-1 안내처럼 임박한 것만.)
     */
    val collabUpcoming: StateFlow<List<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite>> =
        container.collabEventCenter.acceptedUpcoming
            .map { list ->
                val until = todayStart + 2L * 24 * 60 * 60 * 1000  // 모레 0시 = 오늘·내일까지만
                list.filter { it.scheduledAtMs <= 0L || it.scheduledAtMs < until }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 홈 협업 카드 완료 버튼 — 결과 토스트를 one-shot 으로 HomeScreen 에 전달.
    private val _collabCompleteToast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val collabCompleteToast: kotlinx.coroutines.flow.SharedFlow<String> = _collabCompleteToast

    /** 홈 협업 현장 카드에서 바로 "완료" 누를 때 — 계좌 없으면 토스트만. 있으면 서버에 COMPLETED 전송. */
    fun completeCollabSite(site: com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite) {
        val accountNo = container.preferences.bizAccountNo
        if (accountNo.isBlank()) {
            _collabCompleteToast.tryEmit("계좌를 먼저 등록해야 완료 알림을 보낼 수 있어요 → 더보기 > 내 정보")
            return
        }
        viewModelScope.launch {
            val myPhone = container.preferences.bizPhone.filter { it.isDigit() }
            val myName = container.preferences.bizName.takeIf { it.isNotBlank() }
                ?: container.preferences.bizOwner
            container.sharedSiteRepository.progress(
                shareId = site.shareId,
                partnerPhone = myPhone,
                step = com.detailline.callfollowcrm.ai.SharedSiteRepository.Progress.COMPLETED,
                bank = container.preferences.bizBank.takeIf { it.isNotBlank() },
                accountNo = accountNo.takeIf { it.isNotBlank() },
                holder = (container.preferences.bizAccountHolder.takeIf { it.isNotBlank() }
                    ?: container.preferences.bizOwner.takeIf { it.isNotBlank() }),
                partnerName = myName
            ).onSuccess {
                container.collabEventCenter.updateLocalProgress(
                    site.shareId,
                    com.detailline.callfollowcrm.ai.SharedSiteRepository.Progress.COMPLETED
                )
                _collabCompleteToast.tryEmit("완료 알렸어요 — 주인 사장님께 계좌가 전달돼요")
            }.onFailure {
                _collabCompleteToast.tryEmit("전송 실패 — 잠시 후 다시 시도해주세요")
            }
        }
    }

    /** 협업 진행 알림의 shareId → 내가 그 현장을 공유한 고객 id. A(주인)는 '현장 보기'를 그 고객 상세로 보냄. (2026-06-14) */
    fun customerIdForShareId(shareId: String): Long? {
        if (shareId.isBlank()) return null
        for (e in container.preferences.collabAssignments) {
            val p = e.split('|')
            if (p.size >= 4 && p[3].trim() == shareId) return p[0].trim().toLongOrNull()
        }
        return null
    }

    // ────────────────────────────────────────────────────────
    // 정기문자 "보낼 때 됐어요" 카운트 (상담함, 2026-06-01)
    //   포그라운드 계산 (앱 열 때). 자동발송 X — 사장님이 목록에서 확인 후 보냄.
    // ────────────────────────────────────────────────────────
    val recurringDueCount: StateFlow<Int> = combine(
        container.recurringMessageRepository.observeEnabledRules(),
        customers,
        container.recurringMessageRepository.observeLogs()
    ) { rules, custs, logs ->
        val keys = logs.map { Triple(it.ruleId, it.customerId, it.occurrenceDayStartMs) }.toSet()
        com.detailline.callfollowcrm.domain.recurring.RecurringDueCalc
            .computeDue(rules, custs, keys, todayStart).size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * 시공 D-1 / 도착 안내 리마인드 카운트 (상담함, 2026-06-01) — 사장님 명시 요청.
     *   내일/오늘 시공 고객에게 "안내 문자 보낼까요?". 정기문자와 같은 로그(음수 sentinel) 재사용.
     */
    /** D-1 안내 토글 — 비반응형 prefs 를 flow 로(새로고침 때 재읽기). OFF 면 홈 "내일 시공 안내" 카드 숨김. (2026-06-18 사장님) */
    private val d1EnabledFlow = MutableStateFlow(container.preferences.d1AutoEnabled)

    /** 설정한 D-1 안내 시각(d1SendHour, 예 오후 6시)이 됐는지 — 그 전엔 홈 D-1 카드 숨김(자정 갑툭 방지). emit 때마다 현재 시각 재평가. (2026-06-18 사장님) */
    private fun d1TimeReachedNow(): Boolean =
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= container.preferences.d1SendHour

    val scheduleReminderCount: StateFlow<Int> = combine(
        customers,
        container.recurringMessageRepository.observeLogs(),
        d1EnabledFlow
    ) { custs, logs, d1On ->
        val keys = logs.map { Triple(it.ruleId, it.customerId, it.occurrenceDayStartMs) }.toSet()
        com.detailline.callfollowcrm.domain.reminder.ScheduleReminderCalc
            .compute(
                custs, keys, todayStart,
                arrivalEnabled = container.preferences.arrivalAutoEnabled,
                arrivalEnteredCustomerIds = arrivalEnteredIdsToday(),
                d1Enabled = d1On,
                d1TimeReached = d1TimeReachedNow()
            ).size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * 시공 안내 리마인드 상세 (프로토 remind-card) — 이름·시점·문구까지. 홈 상단에 1건 카드로.
     *   문구는 [ScheduleReminderViewModel.renderBody] 와 동일 규칙. 발송/건너뛰기 = 로그 재사용.
     */
    val scheduleReminders: StateFlow<List<HomeReminderUi>> = combine(
        customers,
        container.recurringMessageRepository.observeLogs(),
        d1EnabledFlow
    ) { custs, logs, d1On ->
        val keys = logs.map { Triple(it.ruleId, it.customerId, it.occurrenceDayStartMs) }.toSet()
        val byId = custs.associateBy { it.id }
        com.detailline.callfollowcrm.domain.reminder.ScheduleReminderCalc
            .compute(
                custs, keys, todayStart,
                arrivalEnabled = container.preferences.arrivalAutoEnabled,
                arrivalEnteredCustomerIds = arrivalEnteredIdsToday(),
                d1Enabled = d1On,
                d1TimeReached = d1TimeReachedNow()
            ).map { item ->
                val c = byId[item.customerId]
                val md = DateTimeUtils.formatDateOnly(item.scheduledDayStartMs)
                val timeStr = c?.scheduledWorkMinutes?.let { " " + DateTimeUtils.formatWorkMinutes(it) } ?: ""
                val d1 = item.kind == com.detailline.callfollowcrm.domain.reminder.ReminderKind.D1
                HomeReminderUi(
                    item = item,
                    body = renderReminderBody(item),
                    name = item.customerName?.takeIf { it.isNotBlank() } ?: item.phone,
                    whenLabel = (if (d1) "내일" else "오늘") + "($md)$timeStr",
                    kindLabel = if (d1) "내일 시공 안내 · D-1" else "오늘 시공 · 도착 안내",
                    addressLabel = com.detailline.callfollowcrm.util.AddressExtractor.siteLabel(c?.address)
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 안 들어온 잔금(미수) — 받을 돈 남았고 시공 후 1일+ 지난 고객. 상담함에 고객마다 카드. (사장님 2026-06-23)
     *   기준/경과일 = [SettlementCalc.overdueDays]. 오래 지난 것부터 위로. "받았어요" 하면 미수 0 → 사라짐.
     */
    val balanceDues: StateFlow<List<HomeBalanceDueUi>> = customers.map { list ->
        list.filter { SettlementCalc.hasMoney(it) }
            .mapNotNull { c ->
                val days = SettlementCalc.overdueDays(c, todayStart) ?: return@mapNotNull null
                val won = SettlementCalc.rowOf(c).outstanding
                val realName = c.name?.takeIf { it.isNotBlank() }
                // 카드엔 번호 대신 아주 짧은 현장("수원 대동아파트"). 없으면 이름, 그것도 없으면 잔금만. (사장님 2026-06-23)
                val shortAddr = com.detailline.callfollowcrm.util.AddressExtractor.roughSite(c.address)
                    .takeIf { it.isNotBlank() }
                HomeBalanceDueUi(
                    customerId = c.id,
                    phone = c.phoneNumber,
                    name = realName ?: PhoneNumberFormatter.format(c.phoneNumber),
                    whereLabel = shortAddr ?: realName,
                    outstandingWon = won,
                    daysSince = days,
                    body = balanceRequestBody(realName, won)
                )
            }
            .sortedByDescending { it.daysSince }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 잔금 요청 문자 본문 — 이름 + 미수액. (계좌는 prefs 미보유 → 금액만, 완료 흐름과 동일) */
    private fun balanceRequestBody(name: String?, outstandingWon: Long): String {
        val who = name ?: "고객"
        return "${who}님 안녕하세요 😊 지난 시공 잔금 ${"%,d".format(outstandingWon)}원 입금 부탁드립니다. 감사합니다!"
    }

    /** 오늘 5km 진입한 고객 ID — 지오펜스가 적립한 "arrival:{id}:{오늘자정}" 키에서 파싱. */
    private fun arrivalEnteredIdsToday(): Set<Long> {
        val suffix = ":$todayStart"
        return container.preferences.arrivalEnteredKeys.mapNotNull { k ->
            if (!k.startsWith("arrival:") || !k.endsWith(suffix)) return@mapNotNull null
            k.removePrefix("arrival:").removeSuffix(suffix).toLongOrNull()
        }.toSet()
    }

    private fun renderReminderBody(item: com.detailline.callfollowcrm.domain.reminder.ReminderItem): String {
        val name = item.customerName?.takeIf { it.isNotBlank() } ?: "고객"
        val biz = container.preferences.bizName.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        val fallback = when (item.kind) {
            com.detailline.callfollowcrm.domain.reminder.ReminderKind.D1 -> {
                val date = DateTimeUtils.formatKoreanDate(item.scheduledDayStartMs)
                "${name}님, 안녕하세요$biz 😊 내일 $date 시공 예정입니다. 시간 맞춰 방문드릴게요. 변동 있으시면 편히 말씀 주세요!"
            }
            com.detailline.callfollowcrm.domain.reminder.ReminderKind.ARRIVAL ->
                "${name}님, 오늘 시공 위해 곧 출발합니다!$biz 도착 전 미리 연락드릴게요 😊"
        }
        val raw = when (item.kind) {
            com.detailline.callfollowcrm.domain.reminder.ReminderKind.D1 -> container.preferences.d1AutoText
            com.detailline.callfollowcrm.domain.reminder.ReminderKind.ARRIVAL -> container.preferences.arrivalAutoText
        }.ifBlank { fallback }
        return raw
            .replace("{고객명}", name)
            .replace("{이름}", name)
            .replace("{상호}", container.preferences.bizName)
            .replace("{시공일}", DateTimeUtils.formatKoreanDate(item.scheduledDayStartMs))
    }

    /** 시공 안내 리마인드 — 보냄 처리(로그 SENT → 카드에서 제외). 실제 발송은 화면이 SmsSender 로. */
    fun markReminderSent(item: com.detailline.callfollowcrm.domain.reminder.ReminderItem) =
        logReminder(item, com.detailline.callfollowcrm.data.local.entity.RecurringLogEntity.STATUS_SENT)

    /** 시공 안내 리마인드 — 건너뛰기(로그 DISMISSED). */
    fun dismissReminder(item: com.detailline.callfollowcrm.domain.reminder.ReminderItem) =
        logReminder(item, com.detailline.callfollowcrm.data.local.entity.RecurringLogEntity.STATUS_DISMISSED)

    private fun logReminder(item: com.detailline.callfollowcrm.domain.reminder.ReminderItem, status: String) =
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                container.recurringMessageRepository.logOccurrence(
                    ruleId = com.detailline.callfollowcrm.domain.reminder.ScheduleReminderCalc.ruleIdOf(item.kind),
                    customerId = item.customerId,
                    occurrenceDayStartMs = item.scheduledDayStartMs,
                    status = status
                )
            }
        }

    /**
     * 견적 회신 리마인드 카운트 (상담함, 2026-06-01) — 견적 보낸 지 N일 됐는데 시공일 미등록.
     */
    val estimateFollowupCount: StateFlow<Int> = combine(
        container.messageHistoryRepository.observeEstimateSends(),
        customers,
        container.recurringMessageRepository.observeLogs()
    ) { sends, custs, logs ->
        val estDays = sends.filter { it.customerId != null }
            .groupBy { it.customerId!! }
            .mapValues { (_, list) -> DateTimeUtils.startOfDay(list.maxOf { it.createdAt }) }
        val keys = logs.map { Triple(it.ruleId, it.customerId, it.occurrenceDayStartMs) }.toSet()
        com.detailline.callfollowcrm.domain.estimate.EstimateFollowupCalc
            .compute(estDays, custs, keys, todayStart).size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ────────────────────────────────────────────────────────
    // 오늘 시공 히어로 + 다음 시공 (상담함 최상단, 2026-06-01)
    // ────────────────────────────────────────────────────────

    /** 사장님이 꾹 눌러 바꾼 오늘 히어로 카드 수동 순서(고객 ID). 비면 시간순 기본. */
    private val _heroOrder = MutableStateFlow(container.preferences.todayHeroOrder)

    /**
     * 오늘 예약된 시공들. 기본은 시간순이되, 사장님이 트렐로식으로 끌어 바꾼 [_heroOrder] 가 있으면 그 순서 우선.
     * 여러 날 시공(scheduledWorkDays)이 오늘 진행 중이면 포함. 다크 히어로.
     */
    val todayJobs: StateFlow<List<CustomerEntity>> = combine(customers, _heroOrder, _todayTick) { list, order, _ ->
        // todayStart 는 ViewModel 생성 시 캐시돼 자정 넘어도 갱신 안 됨 → 필터 내에서 동적 계산. (2026-06-24 버그픽스)
        val nowTodayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        val base = list.filter { c ->
            val s = c.scheduledWorkDate ?: return@filter false
            val start = DateTimeUtils.startOfDay(s)
            val end = start + (c.scheduledWorkDays.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS
            if (nowTodayStart !in start..end) return@filter false
            // 완료한 현장도 '완료한 그날'은 회색 카드로 남김(다시 전화할 수도 있으니) → 다음날 자동 사라짐. (2026-06-14 사장님)
            val done = c.workCompletedAt
            if (done != null && DateTimeUtils.startOfDay(done) != nowTodayStart) return@filter false
            true
        }.sortedWith(compareBy({ it.workCompletedAt != null }, { it.scheduledWorkMinutes ?: Int.MAX_VALUE }))
        applyHeroOrder(base, order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 저장된 수동 순서를 적용. 순서에 있는 현장 먼저(그 순서대로), 새로 생긴 현장은 뒤에 시간순. */
    private fun applyHeroOrder(base: List<CustomerEntity>, order: List<Long>): List<CustomerEntity> {
        if (order.isEmpty() || base.size <= 1) return base
        val byId = base.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }
        val rest = base.filter { it.id !in order }
        return ordered + rest
    }

    /** 사장님이 히어로 카드를 끌어 순서를 바꿈 → 저장(영속). 화면 즉시 반영. */
    fun reorderTodayJobs(orderedIds: List<Long>) {
        _heroOrder.value = orderedIds
        container.preferences.todayHeroOrder = orderedIds
    }

    /** 다음 시공 = 오늘 이후 가장 가까운 날의 시공들 (1~3곳). 오늘 시공 없을 때 미리보기. */
    val nextJobs: StateFlow<List<CustomerEntity>> = customers.map { list ->
        val future = list.filter { (it.scheduledWorkDate ?: 0L) > todayEnd }
        val minDay = future.mapNotNull { it.scheduledWorkDate }
            .map { DateTimeUtils.startOfDay(it) }.minOrNull() ?: return@map emptyList()
        future.filter { DateTimeUtils.startOfDay(it.scheduledWorkDate ?: 0L) == minDay }
            .sortedBy { it.scheduledWorkMinutes ?: Int.MAX_VALUE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * "미확인" 판정 = phone suffix set.
     *
     * 정의 (2026-05-28 사장님 결정):
     *   - SMS: lastSent=false (= 고객 메시지가 마지막) + lastDateMs 7일 윈도우 안 = 미확인
     *     → **답장 보낸 적 있는 phone 도, 그 후 새 메시지가 오면 다시 미확인**.
     *     hasOwnerReply 조건 제거: 사장님이 "새 메시지 올 때마다 알 수 있어야" 한다는 의도.
     *   - 부재중 통화: 7일 윈도우 안 MISSED + 그 phone 의 sent SMS 가 1건도 없음
     *     → 답장한 적 있는 단골 고객의 부재중은 미확인 X (전화 한 통은 알림으로 충분).
     *     필요 시 사장님 추가 보고 받아 SMS 와 동일한 정책으로 일관시킬 수 있음.
     *
     * 스팸 마킹된 phone 은 swipe-to-spam 결과로 영구 제외.
     */
    private fun unconfirmedSuffixes(
        smsContacts: List<SmsRepository.SmsContact>,
        missed: List<CallRecordEntity>,
        spam: Set<String> = emptySet(),
        scheduled: Set<String> = emptySet(),
        handledCallMs: Map<String, Long> = emptyMap()
    ): Set<String> {
        val bySuffix = smsContacts.associateBy { it.normalizedSuffix }
        val result = HashSet<String>()
        for (c in smsContacts) {
            if (c.normalizedSuffix in spam) continue
            if (com.detailline.callfollowcrm.util.SpamPrefix.isSpam(c.address, spamPrefixesFlow.value)) continue  // 스팸 앞자리
            // 시공일정 잡힌 고객도 '새 메시지가 오면' 답장 기다려요에 다시 뜨게. (2026-07-01 사장님 — 새 문의가 묻히지 않게)
            //   일정만 있고 새 메시지 없으면 아래 freshIncoming 조건이 false 라 자동 제외(= 일정 카드로만 관리). 옛 #scheduled 무조건 제외 제거.
            // 사장님 의도: 마지막 메시지가 고객 수신이면 미확인. 이전에 답장 보낸 적은 무관.
            if (!c.lastSent && c.lastDateMs >= sevenDayWindowStart) {
                // 그 문자 이후 전화로 응대(받은전화 응답/내가 콜백)했으면 처리된 것 → 제외. (2026-07-02 사장님)
                if ((handledCallMs[c.normalizedSuffix] ?: 0L) >= c.lastDateMs) continue
                result += c.normalizedSuffix
            }
        }
        for (m in missed) {
            if (m.endedAt < sevenDayWindowStart) continue
            val suffix = phoneSuffix(m.phoneNumber)
            if (suffix in spam) continue
            if (com.detailline.callfollowcrm.util.SpamPrefix.isSpam(m.phoneNumber, spamPrefixesFlow.value)) continue  // 스팸 앞자리
            if (suffix in scheduled) continue  // 2026-05-30 #3
            // 부재중 이후 전화로 응대(응답/콜백)했으면 처리됨 → 제외. (문자 답장 hasOwnerReply 와 동일 취지) (2026-07-02 사장님)
            if ((handledCallMs[suffix] ?: 0L) >= m.endedAt) continue
            val sms = bySuffix[suffix]
            if (sms == null || !sms.hasOwnerReply) result += suffix
        }
        return result
    }

    /**
     * "오늘 신규" 판정 = phone suffix set.
     *   - SMS: lastDateMs ∈ today AND firstDateMsInScan ∈ today (스캔 안 첫 등장 = 오늘) AND 어제 이전 통화 기록 없음
     *   - 들어온 통화(수신·부재중·거절): endedAt ∈ today AND 어제 이전 통화 기록 없음 AND SMS 도 어제 이전 기록 없음
     *     (2026-06-02 fix: 받은 전화도 신규 문의에 포함. 전엔 부재중만 세서 받은 전화가 누락됐음.)
     */
    private fun newTodaySuffixes(
        smsContacts: List<SmsRepository.SmsContact>,
        inbound: List<CallRecordEntity>,
        callsBefore: Set<String>,
        spam: Set<String> = emptySet()
    ): Set<String> {
        val bySuffix = smsContacts.associateBy { it.normalizedSuffix }
        val result = HashSet<String>()
        for (c in smsContacts) {
            if (c.normalizedSuffix in spam) continue
            if (com.detailline.callfollowcrm.util.SpamPrefix.isSpam(c.address, spamPrefixesFlow.value)) continue  // 스팸 앞자리(070 등)
            if (c.lastDateMs in todayStart..todayEnd &&
                c.firstDateMsInScan >= todayStart &&
                c.normalizedSuffix !in callsBefore
            ) {
                result += c.normalizedSuffix
            }
        }
        for (m in inbound) {
            if (m.endedAt !in todayStart..todayEnd) continue
            val suffix = phoneSuffix(m.phoneNumber)
            if (suffix in spam) continue
            if (com.detailline.callfollowcrm.util.SpamPrefix.isSpam(m.phoneNumber, spamPrefixesFlow.value)) continue  // 스팸 앞자리(070 등)
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

    /** 스팸 판정 묶음 — swipe 마킹(suffix) + 앞자리 등록(prefix). 상담함 목록 필터에 사용. (2026-06-17) */
    private data class SpamGate(val suffixes: Set<String>, val prefixes: Set<String>) {
        fun isSpam(phone: String, suffix: String): Boolean =
            suffix in suffixes ||
            com.detailline.callfollowcrm.util.SpamPrefix.isSpam(phone, prefixes)
    }

    // ── 답장 기다려요 '제외'는 처리 시각과 함께 (2026-07-02 사장님) ─────────────────────────
    //   답장했거나(1탭 발송) 밀어서 정리한 대화에 '새 고객 메시지'가 오면 다시 답장 기다려요로 올라와야 한다.
    //   예전엔 제외가 영구(suffix만 저장)라 새 메시지(사진 등)가 와도 안 풀려 "최근 대화"에 굵게만 떴음.
    //   → suffix→처리시각 으로 저장하고, contact.lastDateMs 가 그 시각보다 최신이면(=새 메시지) 제외 해제.
    /** VM 생성 시각 — 옛 형식(시각 없는) 정리 항목의 기준. 이 이후 새 메시지에만 재노출. */
    private val suppressionLoadMs = System.currentTimeMillis()

    /** 홈 1탭 발송 직후 임시 제외 (suffix → 발송 시각). 이후 새 고객 메시지 오면 자동 해제. */
    private val _repliedAt = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** "지금 답장 기다려요"에서 밀어서 정리한 suffix → 정리 시각 (영속). 이후 새 고객 메시지 오면 자동 재노출. */
    private val _dismissedAt = MutableStateFlow(parseSuppressed(container.preferences.dismissedUnconfirmedSuffixes))

    /** 정리 항목 파싱 — "suffix|ts". 옛 형식(suffix만)은 VM 생성 시각을 처리 시각으로 본다. */
    private fun parseSuppressed(raw: Set<String>): Map<String, Long> =
        raw.associate { e ->
            val i = e.indexOf('|')
            if (i > 0) e.substring(0, i) to (e.substring(i + 1).toLongOrNull() ?: suppressionLoadMs)
            else e to suppressionLoadMs
        }

    private fun encodeSuppressed(m: Map<String, Long>): Set<String> =
        m.map { "${it.key}|${it.value}" }.toSet()

    /**
     * 미확인에서 뺄 suffix = 스팸(영구) ∪ '아직 유효한' 제외(답장/정리).
     *   제외는 처리 시각 이후 새 고객 메시지(contact.lastDateMs 가 더 최신)가 없을 때만 유효.
     *   → 새 메시지 오면 자동으로 다시 "답장 기다려요"에 뜬다.
     */
    private val excludedForUnconfirmed: StateFlow<Set<String>> =
        combine(spamSuffixes, _repliedAt, _dismissedAt, smsContactsState) { spam, replied, dismissed, contacts ->
            val lastBySuffix = contacts.associate { it.normalizedSuffix to it.lastDateMs }
            val out = HashSet<String>(spam)
            fun addStillHandled(m: Map<String, Long>) {
                for ((suf, handledMs) in m) if ((lastBySuffix[suf] ?: 0L) <= handledMs) out += suf
            }
            addStillHandled(replied)
            addStillHandled(dismissed)
            out
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // callsForFlags(missed·inbound·handledMs 묶음)는 위(inboundRecent 아래)에서 선언 — KPI(unhandledCount)와
    //   여기 timelineFlags 둘 다 쓰므로 앞으로 올림.

    private val timelineFlags: StateFlow<TimelineFlags> = combine(
        smsContactsState, callsForFlags, phonesWithCallsBeforeToday, excludedForUnconfirmed, scheduledCustomerSuffixes
    ) { smsContacts, calls, callsBefore, spam, scheduled ->
        val (missed, inbound, handledMs) = calls
        TimelineFlags(
            unconfirmedSuffixes = unconfirmedSuffixes(smsContacts, missed, spam, scheduled, handledMs),
            newTodaySuffixes = newTodaySuffixes(smsContacts, inbound, callsBefore, spam)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineFlags(emptySet(), emptySet()))

    /** timelineFlags + 스팸 게이트 — combine 5개 한도 안에서 목록에 스팸 필터를 함께 적용. (2026-06-17) */
    private val timelineFlagsAndSpam = combine(timelineFlags, spamGate) { flags, spam -> flags to spam }

    val timeline = combine(
        recentRecords, customers, filter, smsContactsState, timelineFlagsAndSpam
    ) { records, custs, f, smsContacts, flagsAndSpam ->
        val (flags, spam) = flagsAndSpam
        val byPhone = custs.associateBy { it.phoneNumber }
        val callPhonesNormalized = records.map { phoneSuffix(it.phoneNumber) }.toHashSet()
        // 안 A (2026-06-08): 통화 있는 번호도 SMS 의 lastSent/lastBody 가 필요 → suffix 로 조회.
        val smsBySuffix = smsContacts.associateBy { it.normalizedSuffix }

        // 1) CallRecord 기반 HomeItem (기존 로직 — 번호+날짜 묶음)
        val callItems = records
            .groupBy { it.phoneNumber to DateTimeUtils.startOfDay(it.endedAt) }
            .map { (key, list) ->
                val (phone, _) = key
                val suffix = phoneSuffix(phone)
                val sorted = list.sortedByDescending { it.endedAt }
                val sms = smsBySuffix[suffix]
                HomeItem(
                    record = sorted.first(),
                    customer = byPhone[phone],
                    callCount = list.size,
                    isUnconfirmed = suffix in flags.unconfirmedSuffixes,
                    isNewToday = suffix in flags.newTodaySuffixes,
                    lastSent = sms?.lastSent,
                    lastBody = sms?.lastBody
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
                    isNewToday = sms.normalizedSuffix in flags.newTodaySuffixes,
                    lastSent = sms.lastSent,
                    lastBody = sms.lastBody
                )
            }

        // 3) 합쳐서 필터 → 날짜 그룹화 → 정렬
        // 2026-06-03 fix(사장님 통점 "문자 와도 순서가 늦게 바뀜"):
        //   기존엔 통화 시각(record.endedAt)으로만 정렬 → 통화 이력 있는 번호는 새 SMS 가 와도
        //   위로 안 올라옴(SMS-only 만 즉시 반영). 정렬·그룹 기준을 "마지막 활동 = max(통화, SMS)"로.
        //   단 SMS bump 는 그 번호의 '가장 최근 통화 아이템'에만 적용(과거 날짜 그룹 오염 방지).
        val smsLatestBySuffix = smsContacts.associate { it.normalizedSuffix to it.lastDateMs }
        val latestCallMsBySuffix = callItems
            .groupBy { phoneSuffix(it.record.phoneNumber) }
            .mapValues { (_, items) -> items.maxOf { it.record.endedAt } }
        fun activityMs(item: HomeItem): Long {
            val suffix = phoneSuffix(item.record.phoneNumber)
            val isLatestCallItem = item.record.endedAt == latestCallMsBySuffix[suffix]
            return if (isLatestCallItem) maxOf(item.record.endedAt, smsLatestBySuffix[suffix] ?: 0L)
            else item.record.endedAt
        }
        (callItems + smsOnlyItems)
            // 스팸 등록(앞자리/마킹) 번호는 상담함 목록에서 제외 — 들어오지도, 추천 준비도 안 함. (2026-06-17 사장님)
            .filterNot { spam.isSpam(it.record.phoneNumber, phoneSuffix(it.record.phoneNumber)) }
            .filter { f.accept(it) }
            .map { item ->
                val suffix = phoneSuffix(item.record.phoneNumber)
                // 마지막 활동이 통화인가 = 통화가 있고, 그 통화가 마지막 SMS 보다 최신(또는 같음). (2026-06-17 사장님)
                val wasCall = item.callCount > 0 && item.record.endedAt >= (smsLatestBySuffix[suffix] ?: 0L)
                item.copy(lastActivityMs = activityMs(item), lastActivityWasCall = wasCall)
            }
            .groupBy { DateTimeUtils.startOfDay(it.lastActivityMs) }
            .toSortedMap(compareByDescending { it })
            .map { (dayStart, items) ->
                DayGroup(dayStartMs = dayStart, items = items.sortedByDescending { it.lastActivityMs })
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 카톡식 읽음 추적 (2026-06-08) — "최근 대화" 파란 점 계산용.
     *   suffix → 마지막으로 그 대화를 연 시각(ms). 채팅 열면 갱신 → 점 사라짐.
     *   HomeScreen 이 collect 해서 row 별 안 읽음 = (lastSent==false && 고객 마지막 메시지 > 읽은 시각) 계산.
     */
    val readStates: StateFlow<Map<String, Long>> = container.readStateStore.readStates

    private fun phoneSuffix(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 8) digits.takeLast(8) else digits
    }

    fun setFilter(f: HomeFilter) { filter.value = f }

    /** 오늘 시공 히어로 [완료] → 완료처리. 그 현장을 오늘 히어로에서 제외(완료 반영). (2026-06-08 #2) */
    fun markJobCompleted(customerId: Long) = viewModelScope.launch {
        runCatching { container.customerRepository.updateWorkCompletedAt(customerId, System.currentTimeMillis()) }
    }
    /** 완료 처리 되돌리기 (스낵바 '되돌리기'). */
    fun undoJobCompleted(customerId: Long) = viewModelScope.launch {
        runCatching { container.customerRepository.updateWorkCompletedAt(customerId, null) }
    }

    /**
     * 완료 + 잔금 완납 처리 — 완료 팝업에서 "잔금 다 받았어요" 선택 시. (2026-06-15 사장님)
     *   balanceAmount 비어 있으면 (총액-계약금)으로 채우고 balancePaidAt=now → 고객상세 '잔금 받음' + 미수금에서 빠짐.
     */
    fun markJobCompletedBalancePaid(customerId: Long) = viewModelScope.launch {
        runCatching {
            val now = System.currentTimeMillis()
            val c = container.customerRepository.findById(customerId)
            container.customerRepository.updateWorkCompletedAt(customerId, now)
            if (c != null) {
                val bal = c.balanceAmount ?: ((c.totalAmount ?: 0L) - (c.depositAmount ?: 0L)).coerceAtLeast(0L)
                if (c.balanceAmount == null && bal > 0L) container.customerRepository.updateBalanceAmount(customerId, bal)
                container.customerRepository.updateBalancePaidAt(customerId, now)
            }
        }
    }

    /**
     * 상담함 미수 카드 [받았어요] — 잔금 받음 처리. 완료일(workCompletedAt)은 안 건드림(이미 끝난 시공).
     *   balanceAmount 비어 있으면 (총액-계약금)으로 채우고 balancePaidAt=now → 미수 0 → 카드 사라짐. (2026-06-23 사장님)
     */
    fun markBalanceReceived(customerId: Long) = viewModelScope.launch {
        runCatching {
            val now = System.currentTimeMillis()
            val c = container.customerRepository.findById(customerId) ?: return@runCatching
            val bal = c.balanceAmount ?: ((c.totalAmount ?: 0L) - (c.depositAmount ?: 0L)).coerceAtLeast(0L)
            if (c.balanceAmount == null && bal > 0L) container.customerRepository.updateBalanceAmount(customerId, bal)
            container.customerRepository.updateBalancePaidAt(customerId, now)
        }
    }

    /** 미수 카드 받음 처리 되돌리기(스낵바 '되돌리기') — 잔금 다시 미수로. */
    fun undoBalanceReceived(customerId: Long) = viewModelScope.launch {
        runCatching { container.customerRepository.updateBalancePaidAt(customerId, null) }
    }

    /**
     * 미확인 카드 swipe → "광고/스팸" 으로 영구 마킹. 미확인 카테고리에서 제외.
     *   suffix 정규화는 ViewModel 안에서 — 호출자는 raw phone 만 넘김.
     *   Snackbar Undo 가 [unmarkSpam] 호출.
     */
    fun markSpam(phoneNumber: String, displayName: String? = null, kind: String = "spam") {
        viewModelScope.launch(Dispatchers.IO) {
            // 전체 번호+이름도 저장 → 더보기 '스팸/사생활 목록'에서 알아보고 복구(해제) 가능. kind=spam/personal. (2026-06-23 사장님)
            container.spamPhoneRepository.mark(phoneSuffix(phoneNumber), phoneNumber, displayName, kind)
        }
    }

    fun unmarkSpam(phoneNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.spamPhoneRepository.unmark(phoneSuffix(phoneNumber))
        }
    }

    /**
     * "지금 답장 기다려요"에서 밀어서 정리 — 미확인 목록에서만 숨김(스팸 아님).
     *   2026-06-07 사장님 통점: 옛날엔 이 밀기가 '스팸 마킹'이라 답장 안 한 진짜 고객이 스팸으로 빠졌음.
     *   이제 정상 고객으로 두고 대기 목록에서만 치움. 신규/최근대화엔 그대로 보임.
     */
    fun dismissUnconfirmed(phoneNumber: String) {
        val suf = phoneSuffix(phoneNumber)
        if (suf.isBlank()) return
        // 정리 시각 기록 — 이 이후 새 고객 메시지가 오면 다시 대기목록에 뜬다.
        val next = _dismissedAt.value + (suf to System.currentTimeMillis())
        _dismissedAt.value = next
        container.preferences.dismissedUnconfirmedSuffixes = encodeSuppressed(next)
    }

    /** 정리 취소(Undo). */
    fun undoDismissUnconfirmed(phoneNumber: String) {
        val suf = phoneSuffix(phoneNumber)
        if (suf.isBlank()) return
        val next = _dismissedAt.value - suf
        _dismissedAt.value = next
        container.preferences.dismissedUnconfirmedSuffixes = encodeSuppressed(next)
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
                // SMS 대화 AI 요약 + 통화요약을 합침. 문자만/통화만 어느 쪽이든 상담함에 한 줄이 뜨게.
                //   (2026-06-14 사장님: 통화만 한 고객은 한줄기록이 비어 있었음 → 통화요약을 한 줄로)
                kotlinx.coroutines.flow.combine(
                    container.conversationAiRepository.observeMany(suffixes),
                    container.callSummaryRepository.observeAll()
                ) { convList, callList ->
                    val smsMap = convList.mapNotNull { e -> e.cardSummary?.let { e.phoneSuffix to it } }.toMap()
                    // 번호별 최신 통화요약 한 줄 — SMS 요약 없는 번호(통화만)만 채움.
                    val callMap = callList
                        .groupBy { phoneSuffix(it.phoneNumber ?: "") }
                        .mapNotNull { (suf, rows) ->
                            if (suf.isBlank() || suf in smsMap) return@mapNotNull null
                            val latest = rows.maxByOrNull { it.recordedAt ?: it.updatedAt }
                            val line = latest?.let { callSummaryOneLine(it) }
                            if (line.isNullOrBlank()) null else suf to ("📞 $line")
                        }.toMap()
                    callMap + smsMap   // SMS 요약 우선
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 통화요약을 상담함 한 줄로 — 제목 우선, 없으면 요약 본문 첫 줄(불릿 기호 제거). */
    private fun callSummaryOneLine(e: com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity): String? {
        e.title?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        return e.summaryText
            ?.lineSequence()
            ?.map { it.trim().removePrefix("•").removePrefix("-").trim() }
            ?.firstOrNull { it.isNotBlank() }
    }

    // ── 대기 카드 AI 추천 답변 (프로토 sugbox + 홈 1탭 발송) ──────────────
    /** suffix → 서버가 준비한 추천 답변 1순위 텍스트. 있으면 대기 카드에 미리보기 + 비행기 버튼. */
    private val _waitingReplies = MutableStateFlow<Map<String, String>>(emptyMap())
    val waitingReplies: StateFlow<Map<String, String>> = _waitingReplies

    /** 대기 카드 추천답변 3개 후보(전략 라벨 포함) — '확인 후 발송' 다이얼로그에서 옆으로 넘겨 고르기. (2026-07-02 사장님) */
    private val _waitingReplyChoices = MutableStateFlow<Map<String, List<com.detailline.callfollowcrm.ai.ReplyChoice>>>(emptyMap())
    val waitingReplyChoices: StateFlow<Map<String, List<com.detailline.callfollowcrm.ai.ReplyChoice>>> = _waitingReplyChoices

    /** 새 버전 배너 — 하루 1회 체크 결과(서버 mtime_ms vs BUILD_TIMESTAMP). (2026-06-16) */
    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable
    /** 서버가 알려준 최신 versionCode (0 = 모름). 배너에 "현재→최신" 표시용. */
    private val _latestVersionCode = MutableStateFlow(0)
    val latestVersionCode: StateFlow<Int> = _latestVersionCode

    /** 이미 fetch 시도한 suffix (중복 호출 방지). */
    private val fetchedReplySuffixes = java.util.Collections.synchronizedSet(HashSet<String>())

    init {
        // 미확인(대기) 고객마다 서버 추천 답변을 한 번씩 조회 → 준비돼 있으면 카드에 노출.
        //   서버는 SMS 수신 시 이미 prepare 해두므로 보통 READY. MISSING 이면 조용히 스킵(채팅에서 ↻).
        viewModelScope.launch {
            timeline.collect { groups ->
                val unconfirmed = groups.flatMap { it.items }.filter { it.isUnconfirmed }
                for (item in unconfirmed) {
                    val phone = item.record.phoneNumber
                    val suffix = phoneSuffix(phone)
                    if (!fetchedReplySuffixes.add(suffix)) continue
                    launch(Dispatchers.IO) {
                        val choices = runCatching {
                            container.suggestionRepository.fetch(phone).getOrNull()
                                ?.suggestions?.suggestions
                        }.getOrNull().orEmpty().filter { it.text.isNotBlank() }.take(3)
                        if (choices.isNotEmpty()) {
                            _waitingReplies.value = _waitingReplies.value + (suffix to choices.first().text)
                            _waitingReplyChoices.value = _waitingReplyChoices.value + (suffix to choices)
                        }
                    }
                }
            }
        }

        // 새 버전 체크 — 앱 열 때마다(홈 진입) 체크하되 10분 연타만 방지. (2026-06-21 사장님: 베타 빨리 받게)
        //   서버 mtime_ms > BUILD_TIMESTAMP(+여유) 면 배너 ON. (기존 24시간 throttle → 테스터가 하루 늦게 받던 것)
        viewModelScope.launch {
            val prefs = container.preferences
            val now = System.currentTimeMillis()
            if (now - prefs.lastUpdateCheckMs > 10L * 60 * 1000) {
                prefs.lastUpdateCheckMs = now
                val info = com.detailline.callfollowcrm.util.UpdateChecker.checkUpdate()
                prefs.updateAvailable = info.available
                if (info.latestCode > 0) prefs.latestVersionCode = info.latestCode
            }
            _updateAvailable.value = prefs.updateAvailable
            _latestVersionCode.value = prefs.latestVersionCode
        }
    }

    /**
     * 대기 카드에서 추천 답변을 홈에서 바로 발송한 직후 호출 (발송 자체는 화면이 SmsSender 로).
     *   발송 기록 남기고, 그 카드를 미확인에서 즉시 제외 + 추천 미리보기 제거.
     */
    fun onWaitingReplySent(phone: String, body: String, customerId: Long?) {
        container.journeyEventRepository.track("button_click", screen = "home", target = "quick_reply")
        val suffix = phoneSuffix(phone)
        _repliedAt.value = _repliedAt.value + (suffix to System.currentTimeMillis())
        _waitingReplies.value = _waitingReplies.value - suffix
        _waitingReplyChoices.value = _waitingReplyChoices.value - suffix
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                container.messageHistoryRepository.recordAutoSend(
                    phoneNumber = phone,
                    customerId = customerId,
                    templateId = null,
                    body = body,
                    status = com.detailline.callfollowcrm.domain.model.MessageStatus.INLINE_SENT
                )
            }
        }
    }

    /** 대기카드 '고쳐서 보내기' — 추천답변을 채팅 입력창 초안으로 넣어두고 채팅 열기. (2026-07-02 사장님) */
    fun prefillChatDraft(phone: String, text: String) {
        if (text.isBlank()) return
        container.chatDraftStore.set(phone, text)
    }

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
    val isNewToday: Boolean = false,
    /** 마지막 활동 시각 = max(최근 통화, 최근 SMS). 정렬·행 시각 표시에 사용. 0L = 미설정(record.endedAt 사용). */
    val lastActivityMs: Long = 0L,
    /**
     * 이 번호의 마지막 SMS 를 사장님이 보냈는지(true)·고객이 보냈는지(false). null = SMS 없음(통화만).
     * "최근 대화" 안 읽음 표시(안 A, 2026-06-08): false = 고객이 마지막 = 새 메시지(파란 점 + 굵게 + 위로).
     */
    val lastSent: Boolean? = null,
    /** 이 번호의 마지막 SMS 본문(표시용). 안 읽음 줄은 요약 대신 이 "실제 한 말"을 보여준다. */
    val lastBody: String? = null,
    /**
     * 마지막 활동이 '통화'인가 (통화가 마지막 SMS 보다 최신) — 통화로 끝난 대화 판정.
     *   true 면 답할 문자가 없으므로 대기 카드가 "AI 답변 준비 중" 대신 통화 안내를 보여준다. (2026-06-17 사장님)
     */
    val lastActivityWasCall: Boolean = false
)

/** 홈 시공 안내 remind-card 표시 모델 (프로토 remind-card). */
data class HomeReminderUi(
    val item: com.detailline.callfollowcrm.domain.reminder.ReminderItem,
    val body: String,
    val name: String,
    val whenLabel: String,
    val kindLabel: String,
    /** 간략 주소(지역+건물) — "이 번호가 내일 고객 맞나?" 눈으로 확인용. 주소 없으면 "". (2026-06-21 사장님) */
    val addressLabel: String
)

/** 상담함 "안 들어온 잔금" 미수 카드 모델 — 프로토 settle 푸시를 카드화. (2026-06-23 사장님) */
data class HomeBalanceDueUi(
    val customerId: Long,
    val phone: String,
    /** 스낵바용 — 이름 없으면 하이픈 번호. */
    val name: String,
    /** 카드 표시용 = 짧은 현장주소 ?: 이름. 둘 다 없으면 null → 카드엔 잔금만(번호 X). */
    val whereLabel: String?,
    val outstandingWon: Long,
    val daysSince: Int,
    val body: String
)

/**
 * 부재중 → 자동답장 카드 한 줄 (2026-06-01).
 *   AutoReplyScheduler 가 자동 발송한 한 건. failed=true 면 발송 실패(사장님이 직접 보내야 함).
 */
data class AutoReplyItem(
    val id: Long,
    val phone: String,
    val customerId: Long?,
    val customerName: String?,
    val body: String,
    val failed: Boolean,
    val createdAt: Long
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
    /** 2026-05-30 사장님 #12 통점 — 오늘 신규 KPI 카드 클릭 시 적용. */
    object TodayNew : HomeFilter("오늘 신규")
    data class Category(val id: Long, val name: String, val emoji: String?) : HomeFilter(name)

    fun accept(item: HomeItem): Boolean = when (this) {
        is All -> true
        is Unconfirmed -> item.isUnconfirmed
        is TodayNew -> item.isNewToday   // 2026-05-30 #12
        is Category -> item.customer?.categoryId == id
    }
}
