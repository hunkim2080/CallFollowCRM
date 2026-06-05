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

    /** 고객(현장)별 배정된 팀원 — 일정 카드 배정 줄이 구독. */
    val assignmentsByCustomer: StateFlow<Map<Long, List<TeamAssignmentEntity>>> =
        container.teamAssignmentRepository.observeAll()
            .map { list -> list.groupBy { it.customerId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    init { loadTeam() }

    fun loadTeam() {
        if (ownerPhone.isBlank()) return
        viewModelScope.launch {
            container.teamRepository.members(ownerPhone).onSuccess { _teamMembers.value = it }
        }
    }

    /**
     * 한 현장(고객)에 팀원 배정 저장 — 로컬 기록 교체 + 영향 받은 팀원들의 일정 snapshot 을 서버에 push.
     *   팀원 웹뷰(/team/member/{token})에 배정 일정이 바로 반영됨. 자동 SMS 발송 X.
     */
    fun assignTeam(customer: CustomerEntity, dayStartMs: Long, selectedMemberIds: List<String>) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val members = _teamMembers.value
            val selected = members.filter { it.memberId in selectedMemberIds }
            val prev = container.teamAssignmentRepository.forCustomer(customer.id).map { it.memberId }.toSet()
            container.teamAssignmentRepository.replaceForCustomer(
                customer.id, dayStartMs, selected.map { it.memberId to it.name }, now
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
                memo = c.memo.takeIf { it.isNotBlank() },
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
