package com.detailline.callfollowcrm.presentation.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

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
class ScheduleViewModel(container: AppContainer) : ViewModel() {

    val state: StateFlow<ScheduleUiState> = container.customerRepository.observeScheduled()
        .map { list -> buildState(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

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
