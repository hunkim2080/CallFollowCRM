package com.detailline.callfollowcrm.presentation.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.domain.model.CustomerStatus
import com.detailline.callfollowcrm.domain.model.HandledStatus
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
     * 메인 타임라인 — 최근 N개 통화 기록을 (번호, 날짜) 단위로 묶어서 날짜별 그룹화.
     * 갤럭시 통화 기록처럼 위에서 아래로 오늘 → 어제 → ... 순서.
     *
     * 같은 번호로 오늘 1통, 어제 2통 통화한 경우 두 row 로 분리 (날짜별).
     */
    private val recentRecords = container.callRecordRepository.observeRecent(limit = 500)

    val timeline = combine(recentRecords, customers, filter) { records, custs, f ->
        val byPhone = custs.associateBy { it.phoneNumber }
        records
            // (번호, 그날 자정) 키로 묶기 → 같은 번호도 다른 날이면 row 분리
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
            .filter { f.accept(it) }
            // 날짜별 그룹화 (자정 normalize 키), 최신 날짜 먼저
            .groupBy { DateTimeUtils.startOfDay(it.record.endedAt) }
            .toSortedMap(compareByDescending { it })
            .map { (dayStart, items) ->
                DayGroup(dayStartMs = dayStart, items = items.sortedByDescending { it.record.endedAt })
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(f: HomeFilter) { filter.value = f }
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
