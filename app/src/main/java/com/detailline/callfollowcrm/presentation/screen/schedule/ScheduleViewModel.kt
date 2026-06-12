package com.detailline.callfollowcrm.presentation.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.ai.TeamRepository
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.TeamAssignmentEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 시공 예약 화면의 데이터 묶음.
 *
 * 분류:
 *  - upcoming: 오늘 포함 미래 예약 (가까운 순)
 *  - past:     지난 예약 (최근 지난 것부터)
 *
 * upcoming 은 다시 월별 그룹화해서 헤더 표시.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<ScheduleUiState> = container.customerRepository.observeScheduled()
        .map { list -> buildState(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    private val ownerPhone: String get() = container.preferences.bizPhone.trim()

    /** 팀원 목록 (배정 시트용). 비즈니스 미설정/미가입이면 빈 리스트 → 배정 줄 숨김. */
    private val _teamMembers = MutableStateFlow<List<TeamRepository.TeamMember>>(emptyList())
    val teamMembers = _teamMembers.asStateFlow()

    /** 내가 수락한 협업 현장의 날짜(startOfDay) — 캘린더 보라점. (2026-06-08 #7) */
    private val _collabDayStarts = MutableStateFlow<Set<Long>>(emptySet())
    val collabDayStarts = _collabDayStarts.asStateFlow()

    /** 내가 수락한 협업 현장 목록 — 날짜 선택 시 카드로 보여줌. */
    private val _collabSites = MutableStateFlow<List<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite>>(emptyList())
    val collabSites = _collabSites.asStateFlow()

    /** 고객(현장)별 배정된 팀원 — 일정 카드 배정 줄이 구독. */
    val assignmentsByCustomer: StateFlow<Map<Long, List<TeamAssignmentEntity>>> =
        container.teamAssignmentRepository.observeAll()
            .map { list -> list.groupBy { it.customerId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 전에 협업한 사장님 자동 목록 — 협업 invite 시 수첩에 "협업" 태그로 쌓인 worker 들. (전문가 배정 시트의 협업 섹션) */
    val collabPartners: StateFlow<List<com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity>> =
        container.notebookRepository.observeWorkers()
            .map { list -> list.filter { it.tag.contains("협업") } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 협업 사장 배정(요청) — customerId → 이름들. 일정 카드 "🤝 이름"(로컬 기록, 서버 수락확정은 추후). (2026-06-13) */
    private val _collabAssignByCustomer = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    val collabAssignByCustomer = _collabAssignByCustomer.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    init { loadTeam(); loadCollab(); loadCollabAssignments() }

    /** 로컬 협업 배정 기록 로드 → customerId→이름들. ("customerId|이름" prefs Set 파싱) */
    private fun loadCollabAssignments() {
        val map = HashMap<Long, MutableList<String>>()
        for (e in container.preferences.collabAssignments) {
            val i = e.indexOf('|'); if (i <= 0) continue
            val id = e.substring(0, i).toLongOrNull() ?: continue
            val nm = e.substring(i + 1).takeIf { it.isNotBlank() } ?: continue
            val list = map.getOrPut(id) { mutableListOf() }
            if (nm !in list) list.add(nm)
        }
        _collabAssignByCustomer.value = map
    }

    fun loadTeam() {
        if (ownerPhone.isBlank()) return
        viewModelScope.launch {
            container.teamRepository.members(ownerPhone).onSuccess { _teamMembers.value = it }
        }
    }

    /** 내가 수락한 협업 현장 → 캘린더 보라점용 날짜 set. 서버 with-me. (2026-06-08 #7) */
    /** 마지막으로 서버에서 받은 수락 협업 현장(숨김 필터 전) — hide/undo 시 재요청 없이 재필터하려 보관. */
    private var allAcceptedCollab: List<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite> = emptyList()

    fun loadCollab() {
        if (ownerPhone.isBlank()) return
        viewModelScope.launch {
            container.sharedSiteRepository.withMe(ownerPhone).onSuccess { sites ->
                allAcceptedCollab = sites.filter { it.status == "accepted" && it.scheduledAtMs > 0L }
                applyCollabFilter()
            }
        }
    }

    /** 숨김 set 을 빼고 _collabSites/_collabDayStarts 갱신. loadCollab 결과 + hide/undo 양쪽이 공유. */
    private fun applyCollabFilter() {
        val hidden = container.preferences.hiddenCollabShareIds
        val visible = allAcceptedCollab.filter { it.shareId !in hidden }
        _collabSites.value = visible
        _collabDayStarts.value = visible.map { DateTimeUtils.startOfDay(it.scheduledAtMs) }.toSet()
    }

    /** 협업 카드 밀어서 삭제 — 서버에서 지우는 게 아니라 내 일정 뷰에서만 숨김(되돌리기 가능). */
    fun hideCollab(shareId: String) {
        container.preferences.hiddenCollabShareIds = container.preferences.hiddenCollabShareIds + shareId
        applyCollabFilter()
    }

    /** 숨김 되돌리기(토스트의 "되돌리기"). */
    fun unhideCollab(shareId: String) {
        container.preferences.hiddenCollabShareIds = container.preferences.hiddenCollabShareIds - shareId
        applyCollabFilter()
    }

    /** 일정 카드 밀어서 삭제 — 이 현장을 "일정에서만" 뺌(고객·대화·정산 기록은 보존). 되돌리기 가능. (2026-06-13 사장님) */
    fun unschedule(customer: CustomerEntity) {
        viewModelScope.launch { container.customerRepository.updateScheduledWorkDate(customer.id, null) }
    }
    /** 되돌리기 — 뺀 일정을 원래 날짜로 복구. */
    fun restoreSchedule(customerId: Long, scheduledAtMs: Long) {
        viewModelScope.launch { container.customerRepository.updateScheduledWorkDate(customerId, scheduledAtMs) }
    }

    /** 협업 현장 표시 라벨 — 주소(지역+아파트) 우선, 없으면 고객 이름. 번호/대화 절대 미포함. CustomerDetail CollabShareSheet 와 동일 규칙. */
    private fun collabTitleOf(c: CustomerEntity): String =
        com.detailline.callfollowcrm.util.AddressExtractor.siteLabel(c.address).takeIf { it.isNotBlank() }
            ?: c.name?.takeIf { it.isNotBlank() }?.let { "$it 현장" } ?: "협업 현장"

    /**
     * 전문가 배정 시트의 "협업 사장님" 선택 → 이 현장을 그 사장님께 협업 요청(/api/shared/invite).
     *   고객 번호/대화는 안 보냄(customer_label = 안전 라벨만) — CustomerDetail 공유 흐름과 동일.
     *   link 라우트면 onLink(번호, 문자본문) 로 화면이 SMS 작성창을 열게 함. inapp/실패는 토스트.
     */
    fun inviteCollabToSite(customer: CustomerEntity, partnerPhone: String, onLink: (String, String) -> Unit) {
        val owner = ownerPhone.filter { it.isDigit() }
        if (owner.length < 9) { _toast.value = "먼저 더보기 → 사업자 정보에서 내 전화번호를 등록해주세요"; return }
        val partner = partnerPhone.filter { it.isDigit() }
        if (partner.length < 9) { _toast.value = "협업 사장님 번호를 확인해주세요"; return }
        val title = collabTitleOf(customer)
        val partnerName = collabPartners.value
            .firstOrNull { it.phone.filter { ch -> ch.isDigit() }.takeLast(8) == partner.takeLast(8) }
            ?.name?.takeIf { it.isNotBlank() } ?: "협업 사장님"
        val addr = com.detailline.callfollowcrm.util.AddressExtractor.tidyAddress(customer.address).takeIf { it.isNotBlank() }
        viewModelScope.launch {
            container.sharedSiteRepository.invite(
                ownerPhone = owner, partnerPhone = partner, title = title,
                addr = addr, scheduledAtMs = customer.scheduledWorkDate ?: 0L,
                workSummary = null, memo = null, customerLabel = title
            ).onSuccess { r ->
                // 일정 카드 "🤝 이름" 표시용 로컬 배정 기록 (서버 수락 확정은 추후).
                container.preferences.collabAssignments = container.preferences.collabAssignments + "${customer.id}|$partnerName"
                loadCollabAssignments()
                if (r.route == "link" && !r.url.isNullOrBlank()) {
                    onLink(partner, r.smsDraft ?: "협업 현장 공유 — ${r.url}")
                } else {
                    _toast.value = "협업 요청을 보냈어요 — 상대 사장님이 수락하면 시작돼요"
                }
            }.onFailure { _toast.value = "공유 실패 — 잠시 후 다시 시도해주세요" }
        }
    }

    /**
     * 한 현장(고객)에 팀원 배정 저장 — 로컬 기록 교체 + 영향 받은 팀원들의 일정 snapshot 을 서버에 push.
     *   팀원 웹뷰(/team/member/{token})에 배정 일정이 바로 반영됨. 자동 SMS 발송 X.
     */
    fun assignTeam(customer: CustomerEntity, dayStartMs: Long, selectedMemberIds: List<String>, teamMemo: String? = null) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val members = _teamMembers.value
            val selected = members.filter { it.memberId in selectedMemberIds }
            val prev = container.teamAssignmentRepository.forCustomer(customer.id).map { it.memberId }.toSet()
            container.teamAssignmentRepository.replaceForCustomer(
                customer.id, dayStartMs, selected.map { it.memberId to it.name }, now, teamMemo
            )
            // 영향 받은 팀원 = 이전 배정 ∪ 새 배정 — 각자 snapshot 재구성 push.
            val affected = prev + selectedMemberIds
            var pushed = 0
            for (mid in affected) {
                if (pushSnapshotFor(mid)) pushed++
            }
            _toast.value = when {
                selected.isEmpty() -> "배정을 비웠어요"
                pushed > 0 -> "${selected.size}명 배정 · 현장 공유 📩"
                else -> "${selected.size}명 배정 (링크 발급 후 다시 시도하면 공유돼요)"
            }
        }
    }

    /** 한 팀원의 모든 배정을 모아 snapshot 으로 push. 활성 토큰 없으면(404) invite(reuse) 후 1회 재시도. */
    private suspend fun pushSnapshotFor(memberId: String): Boolean {
        val rows = container.teamAssignmentRepository.forMember(memberId)
        val all = state.value.all
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        val items = rows.mapNotNull { a ->
            val c = all.find { it.id == a.customerId } ?: return@mapNotNull null
            val sched = c.scheduledWorkDate ?: return@mapNotNull null
            val sStart = DateTimeUtils.startOfDay(sched)
            val isToday = sStart == todayStart
            TeamRepository.SnapshotItem(
                whenLabel = if (isToday) "오늘" else SimpleDateFormat("M/d", Locale.KOREA).format(java.util.Date(sStart)),
                customerLabel = c.name?.takeIf { it.isNotBlank() } ?: "현장",
                customerPhone = c.phoneNumber.takeIf { it.isNotBlank() },
                time = c.scheduledWorkMinutes?.let { DateTimeUtils.formatWorkMinutes(it) },
                addr = c.address?.takeIf { it.isNotBlank() },
                workSummary = null,
                // 직원 전달 메모 = 배정 시 사장님이 적은 것(고객 메모 아님 — 사생활 보호).
                teamMemo = a.teamMemo?.takeIf { it.isNotBlank() },
                days = c.scheduledWorkDays.coerceAtLeast(1),
                isToday = isToday,
                scheduledAtMs = sStart
            )
        }.sortedBy { it.scheduledAtMs }

        var res = container.teamRepository.pushScheduleSnapshot(memberId, items)
        if (res.isFailure && (res.exceptionOrNull()?.message?.contains("404") == true)) {
            // 활성 토큰 없음 → 재초대(reuse)로 토큰 발급 후 재시도.
            val m = _teamMembers.value.find { it.memberId == memberId }
            if (m != null && ownerPhone.isNotBlank()) {
                container.teamRepository.invite(ownerPhone, m.name, m.phone, m.role, m.tint)
                res = container.teamRepository.pushScheduleSnapshot(memberId, items)
            }
        }
        return res.isSuccess
    }

    /**
     * 사장님 요청 (2026-05-24): 캘린더 셀 탭 시 시공 카드에 "어떤 내용으로 예약 확정인지" 한 줄.
     * → AiSummaryEntity.cardSummary 재사용 (HomeScreen 카드의 ✨ 한 줄 요약과 같은 데이터).
     * Map<phoneSuffix, summary>. 서버 미구현이면 빈 맵 → UI 가 silent 숨김.
     */
    val cardSummariesByPhoneSuffix: StateFlow<Map<String, String>> = state
        .flatMapLatest { st ->
            val suffixes = st.all.map { phoneSuffix(it.phoneNumber) }.distinct().filter { it.length >= 7 }
            if (suffixes.isEmpty()) flowOf(emptyMap())
            else container.conversationAiRepository.observeMany(suffixes).map { list ->
                list.mapNotNull { e -> e.cardSummary?.let { e.phoneSuffix to it } }.toMap()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun phoneSuffix(phone: String): String =
        phone.filter { it.isDigit() }.takeLast(8)

    private fun buildState(list: List<CustomerEntity>): ScheduleUiState {
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        val (upcoming, past) = list.partition { (it.scheduledWorkDate ?: 0L) >= todayStart }

        // 월별 그룹 — 키는 (year, month). 정렬 유지를 위해 LinkedHashMap.
        val byMonth = LinkedHashMap<String, MutableList<CustomerEntity>>()
        for (c in upcoming) {
            val date = c.scheduledWorkDate ?: continue
            val header = DateTimeUtils.formatMonthHeader(date)
            byMonth.getOrPut(header) { mutableListOf() }.add(c)
        }
        val groups = byMonth.map { (header, members) ->
            ScheduleGroup(monthHeader = header, customers = members)
        }
        return ScheduleUiState(
            upcomingByMonth = groups,
            upcomingCount = upcoming.size,
            past = past.sortedByDescending { it.scheduledWorkDate ?: 0L },
            all = list
        )
    }

    /** 오늘이 어느 월인지 — UI 가 "이번 달" 표기에 사용. */
    fun currentMonthHeader(): String =
        DateTimeUtils.formatMonthHeader(System.currentTimeMillis())
}

data class ScheduleUiState(
    val upcomingByMonth: List<ScheduleGroup> = emptyList(),
    val upcomingCount: Int = 0,
    val past: List<CustomerEntity> = emptyList(),
    /** 전체 (과거 + 미래) 시공 예약 — 캘린더 그리드 그릴 때 사용. */
    val all: List<CustomerEntity> = emptyList()
)

data class ScheduleGroup(
    val monthHeader: String,
    val customers: List<CustomerEntity>
)
