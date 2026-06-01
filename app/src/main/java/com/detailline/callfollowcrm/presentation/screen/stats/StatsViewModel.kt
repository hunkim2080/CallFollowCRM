package com.detailline.callfollowcrm.presentation.screen.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CategoryEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/**
 * 통계 탭 — 프로토 `s-stats`(renderStats/renderStatTypes) 1:1.
 *
 * 사장님 결정(2026-06-02): **내 데이터만 1:1, 전국 데이터(상위 N% 페이스·전국 평균 비교)는 "모이는 중"** 으로 자리만.
 * 모든 수치는 로컬(고객·시공일·카테고리·발송기록) 집계 — 허위 숫자 없음.
 *   - 히어로/그리드/시공종류 = **이번 달** 고정 (프로토와 동일).
 *   - 문의 추이만 7일/30일 토글 (프로토 period-toggle).
 */
class StatsViewModel(container: AppContainer) : ViewModel() {

    private val nowMs = System.currentTimeMillis()
    private val monthStart = monthStartOf(nowMs)
    private val monthEnd = shiftMonth(monthStart, +1)
    private val lastMonthStart = shiftMonth(monthStart, -1)
    private val lastYearStart = shiftMonth(monthStart, -12)
    private val lastYearEnd = shiftMonth(monthEnd, -12)

    private val customers = container.customerRepository.observeAll()
    private val categories = container.categoryRepository.observeAll()
    private val sentThisMonth = container.messageHistoryRepository.observeSentCountBetween(monthStart, monthEnd)

    private val period = MutableStateFlow(StatPeriod.D7)
    val periodState: StateFlow<StatPeriod> = period
    fun setPeriod(p: StatPeriod) { period.value = p }

    val state: StateFlow<StatsUiState> =
        combine(customers, categories, sentThisMonth) { cs, cats, sent ->
            buildState(cs, cats, sent)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    val trend: StateFlow<StatsTrendState> =
        combine(customers, period) { cs, p -> buildTrend(cs, p) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsTrendState())

    // ── 이번 달 고정 집계 ────────────────────────────────────────────
    private fun buildState(cs: List<CustomerEntity>, cats: List<CategoryEntity>, sent: Int): StatsUiState {
        val jobs = cs.count { it.scheduledWorkDate?.let { d -> d in monthStart until monthEnd } == true }
        val inquiries = cs.count { it.createdAt in monthStart until monthEnd }
        val conversion = if (inquiries > 0) Math.round(jobs * 100.0 / inquiries).toInt() else 0

        // 작년 동월 — 앱을 작년에 썼을 때만(그 시점 이전 데이터 존재) 비교 노출.
        val lyJobs = cs.count { it.scheduledWorkDate?.let { d -> d in lastYearStart until lastYearEnd } == true }
        val hasLastYear = cs.any { it.createdAt < lastYearEnd }
        val jobsVsLastYear = if (hasLastYear) jobs - lyJobs else null

        // 시공 종류 = 이번 달 시공(scheduledWorkDate)을 카테고리별로. prev = 지난 달.
        val nameById = cats.associate { it.id to it.name }
        fun typeKey(c: CustomerEntity): String =
            c.categoryId?.let { nameById[it] } ?: "기타 시공"
        val thisByType = cs.filter { it.scheduledWorkDate?.let { d -> d in monthStart until monthEnd } == true }
            .groupingBy { typeKey(it) }.eachCount()
        val lastByType = cs.filter { it.scheduledWorkDate?.let { d -> d in lastMonthStart until monthStart } == true }
            .groupingBy { typeKey(it) }.eachCount()
        val types = thisByType.entries
            .map { (name, cnt) -> StatTypeRow(name, cnt, cnt - (lastByType[name] ?: 0)) }
            .sortedByDescending { it.count }
        val topType = types.firstOrNull()

        return StatsUiState(
            greeting = greetingOf(nowMs),
            monthLabel = "${monthOf(monthStart)}월",
            jobs = jobs,
            jobsVsLastYear = jobsVsLastYear,
            inquiries = inquiries,
            conversionPct = conversion,
            sentReplies = sent,
            types = types,
            topType = topType
        )
    }

    // ── 문의 추이 (7일/30일) ─────────────────────────────────────────
    private fun buildTrend(cs: List<CustomerEntity>, p: StatPeriod): StatsTrendState {
        val createdDays = cs.map { DateTimeUtils.startOfDay(it.createdAt) }
        val bars = when (p) {
            StatPeriod.D7 -> {
                val monday = mondayOfWeek(nowMs)
                (0..6).map { i ->
                    val day = monday + i * DateTimeUtils.DAY_MS
                    val prevDay = day - 7 * DateTimeUtils.DAY_MS
                    TrendBar(
                        label = WEEKDAYS[i],
                        cur = createdDays.count { it == day },
                        prev = createdDays.count { it == prevDay }
                    )
                }
            }
            StatPeriod.D30 -> {
                val thisWeekMon = mondayOfWeek(nowMs)
                // 4주전~이번주 (각 1주 버킷), prev = 그 4주 앞 동일 버킷
                listOf(3, 2, 1, 0).mapIndexed { idx, weeksAgo ->
                    val wkStart = thisWeekMon - weeksAgo * 7 * DateTimeUtils.DAY_MS
                    val wkEnd = wkStart + 7 * DateTimeUtils.DAY_MS
                    val pStart = wkStart - 4 * 7 * DateTimeUtils.DAY_MS
                    val pEnd = pStart + 7 * DateTimeUtils.DAY_MS
                    TrendBar(
                        label = D30_LABELS[idx],
                        cur = createdDays.count { it in wkStart until wkEnd },
                        prev = createdDays.count { it in pStart until pEnd }
                    )
                }
            }
        }
        val cur = bars.sumOf { it.cur }
        val prev = bars.sumOf { it.prev }
        val delta = if (prev > 0) Math.round((cur - prev) * 100.0 / prev).toInt() else 0
        return StatsTrendState(
            period = p,
            curTotal = cur,
            prevTotal = prev,
            deltaPct = delta,
            bars = bars,
            prevLabel = if (p == StatPeriod.D7) "지난주" else "지난 30일",
            unitLabel = if (p == StatPeriod.D7) "이번 주" else "최근 30일"
        )
    }
}

enum class StatPeriod(val key: String, val label: String) { D7("7", "최근 7일"), D30("30", "최근 30일") }

data class StatTypeRow(val name: String, val count: Int, val delta: Int)

data class TrendBar(val label: String, val cur: Int, val prev: Int)

data class StatsUiState(
    val greeting: String = "이번 달, 잘 하고 계세요 👏",
    val monthLabel: String = "",
    val jobs: Int = 0,
    /** 작년 동월 대비 현장 수 차이. null = 작년 데이터 없음(문구 생략). */
    val jobsVsLastYear: Int? = null,
    val inquiries: Int = 0,
    val conversionPct: Int = 0,
    val sentReplies: Int = 0,
    val types: List<StatTypeRow> = emptyList(),
    val topType: StatTypeRow? = null
)

data class StatsTrendState(
    val period: StatPeriod = StatPeriod.D7,
    val curTotal: Int = 0,
    val prevTotal: Int = 0,
    val deltaPct: Int = 0,
    val bars: List<TrendBar> = emptyList(),
    val prevLabel: String = "지난주",
    val unitLabel: String = "이번 주"
)

// ── 날짜 헬퍼 ────────────────────────────────────────────────────
private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")
private val D30_LABELS = listOf("4주전", "3주전", "2주전", "이번주")

private fun monthStartOf(anyMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = anyMs
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun shiftMonth(anchorMs: Long, delta: Int): Long = Calendar.getInstance().apply {
    timeInMillis = anchorMs
    add(Calendar.MONTH, delta)
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun monthOf(anchorMs: Long): Int =
    Calendar.getInstance().apply { timeInMillis = anchorMs }.get(Calendar.MONTH) + 1

/** 그 주 월요일 00:00. */
private fun mondayOfWeek(anyMs: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = DateTimeUtils.startOfDay(anyMs) }
    // Calendar.MONDAY=2 … SUNDAY=1. 월요일까지 뒤로.
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    val back = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
    cal.add(Calendar.DAY_OF_MONTH, -back)
    return cal.timeInMillis
}

/** 프로토 renderStatsGreeting — 월말/월초/중간 인사. */
private fun greetingOf(nowMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val last = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val mo = cal.get(Calendar.MONTH) + 1
    return when {
        day >= last - 3 -> "${mo}월, 정말 고생하셨어요"
        day <= 10 -> "${mo}월, 좋은 출발이에요!"
        else -> "${mo}월, 잘 하고 계세요 👏"
    }
}
