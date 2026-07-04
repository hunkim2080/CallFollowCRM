package com.detailline.callfollowcrm.presentation.screen.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.repository.SuggestionEventRepository
import com.detailline.callfollowcrm.domain.settlement.SettlementCalc
import com.detailline.callfollowcrm.util.AddressExtractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * 비즈니스 리포트 — "얕은 숫자 5개"에서 깊이 있는 장사 이해 도구로 재설계. (2026-07-05 사장님)
 *   기존 데이터(고객·입금·통화·문자·추천이벤트)만으로 집계, DB 변경 없음.
 *   섹션: 번 돈 → 못 받은 돈+누가 → 추천 채택률 → 상황별 → 전환 → 활동 → 종류/지역 → 개선 포인트.
 *   매출 귀속 = 입금일 기준(depositPaidAt/balancePaidAt ∈ 기간). 미수는 항상 '현재 시점'.
 */
class ReportViewModel(private val container: AppContainer) : ViewModel() {

    private val period = MutableStateFlow(ReportPeriod.THIS_MONTH)
    val periodState: StateFlow<ReportPeriod> = period
    fun setPeriod(p: ReportPeriod) { period.value = p }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReportUiState> =
        period.flatMapLatest { p ->
            val (from, to) = rangeOf(p)
            val (prevFrom, prevTo) = prevRangeOf(p)
            // 추천 통계는 suspend 1회성 → flow 로 감싸 combine.
            val sugFlow = flow {
                emit(container.suggestionEventRepository.statsSince(from) to
                    container.suggestionEventRepository.scenarioBreakdown(from))
            }
            combine(
                container.customerRepository.observeAll(),
                container.callRecordRepository.observeInboundSince(from),
                container.messageHistoryRepository.observeSentCountBetween(from, to),
                container.messageHistoryRepository.observeEstimateSends(),
                container.callRecordRepository.countUnhandled(from, to)
            ) { customers, inbound, sentCount, estSends, unhandled ->
                Act(customers, inbound, sentCount, estSends, unhandled)
            }
                .combine(sugFlow) { act, sug -> act to sug }
                .combine(container.categoryRepository.observeAll()) { (act, sug), cats ->
                    buildState(p, from, to, prevFrom, prevTo, act, sug, cats.associate { it.id to it.name })
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())

    private class Act(
        val customers: List<CustomerEntity>,
        val inbound: List<com.detailline.callfollowcrm.data.local.entity.CallRecordEntity>,
        val sentCount: Int,
        val estSends: List<com.detailline.callfollowcrm.data.local.entity.MessageHistoryEntity>,
        val unhandled: Int
    )

    private fun buildState(
        p: ReportPeriod, from: Long, to: Long, prevFrom: Long, prevTo: Long,
        act: Act,
        sug: Pair<SuggestionEventRepository.Stats, List<SuggestionEventRepository.ScenarioBreakdown>>,
        catNames: Map<Long, String>
    ): ReportUiState {
        val customers = act.customers
        val todayStart = ReportCalc.todayStart()

        // 1) 번 돈 (입금일 기준) + 직전 대비 + 현장 수 + 신규.
        var revenue = 0L; var prevRevenue = 0L; var jobs = 0; var newCustomers = 0
        // 2) 미수 (현재 시점).
        var outstandingNow = 0L; var moneyCust = 0; var paidOffNow = 0
        for (c in customers) {
            val row = SettlementCalc.rowOf(c)
            c.depositPaidAt?.let {
                if (it in from until to) revenue += row.depositAmount
                if (it in prevFrom until prevTo) prevRevenue += row.depositAmount
            }
            c.balancePaidAt?.let {
                if (it in from until to) revenue += row.balanceAmount
                if (it in prevFrom until prevTo) prevRevenue += row.balanceAmount
            }
            c.scheduledWorkDate?.let { if (it in from until to) jobs++ }
            if (c.createdAt in from until to) newCustomers++
            if (SettlementCalc.hasMoney(c)) {
                outstandingNow += row.outstanding
                if (row.total > 0L) moneyCust++
                if (row.isPaidOff) paidOffNow++
            }
        }
        val revenueDeltaPct = if (prevRevenue > 0L) (((revenue - prevRevenue) * 100.0) / prevRevenue).roundToInt() else null
        val overdue = ReportCalc.overdueRows(customers, todayStart)

        // 3) 추천 채택률.
        val stats = sug.first
        val adoptionPct = if (stats.total > 0) (stats.adoptionRate * 100).roundToInt() else null

        // 4) 상황별 채택률 (낮은 것 위 — repo 가 이미 오름차순).
        val scenarios = sug.second.map {
            ScenarioRow(
                label = scenarioLabel(it.scenario),
                rate = (it.adoptionRate * 100).roundToInt(),
                total = it.total,
                needsImprove = it.needsImprovement
            )
        }

        // 5) 전환: 문의(inbound, to 상한 필터) → 시공(jobs).
        val inboundCount = act.inbound.count { (it.startedAt ?: it.endedAt) < to }
        val conversionPct = if (inboundCount > 0) (jobs * 100.0 / inboundCount).roundToInt() else null

        // 6) 활동.
        val estimateCount = act.estSends.count { it.createdAt in from until to }

        // 7) 종류별 / 지역별 (기간 귀속 = 입금이 기간에 들어온 금액 기준).
        val categoryRows = ReportCalc.groupByCategory(customers, from, to, catNames)
        val regionRows = ReportCalc.groupByRegion(customers, from, to)

        // 8) 개선 포인트.
        val improve = scenarios.firstOrNull { it.needsImprove }

        return ReportUiState(
            periodLabel = p.label,
            revenue = revenue, prevRevenue = prevRevenue, revenueDeltaPct = revenueDeltaPct, jobs = jobs,
            outstandingNow = outstandingNow, outstandingCust = overdue.size, paidOffNow = paidOffNow, moneyCust = moneyCust,
            overdue = overdue,
            adoptionPct = adoptionPct, adoptedCount = stats.adopted, suggestionTotal = stats.total,
            avgEdit = stats.averageEditDistance.roundToInt(),
            scenarios = scenarios,
            inboundCount = inboundCount, conversionPct = conversionPct,
            sentCount = act.sentCount, estimateCount = estimateCount, unhandledCount = act.unhandled,
            categoryRows = categoryRows, regionRows = regionRows,
            improveScenario = improve,
            newCustomers = newCustomers
        )
    }

    /** 직전 동일 길이 기간 [from, to). */
    private fun prevRangeOf(p: ReportPeriod): Pair<Long, Long> {
        val (from, _) = rangeOf(p)
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        val months = when (p) { ReportPeriod.LAST_3M -> 3; else -> 1 }
        val prevFrom = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -months) }.timeInMillis
        return prevFrom to from
    }

    /** 기간 [from, to) epoch ms. now 기준. */
    private fun rangeOf(p: ReportPeriod): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startThisMonth = cal.timeInMillis
        return when (p) {
            ReportPeriod.THIS_MONTH -> startThisMonth to (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
            ReportPeriod.LAST_MONTH -> (cal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.timeInMillis to startThisMonth
            ReportPeriod.LAST_3M -> (cal.clone() as Calendar).apply { add(Calendar.MONTH, -2) }.timeInMillis to
                (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
        }
    }
}

/** 시나리오 key → 한국어 라벨 (SettingsScreen 과 동일). */
internal fun scenarioLabel(scenario: String): String = when (scenario) {
    "initial_inquiry" -> "초기 문의"
    "price_inquiry" -> "가격 문의"
    "hesitation" -> "고객 망설임"
    "schedule" -> "일정 조율"
    "pre_booking" -> "예약 확정 전"
    "pre_service" -> "시공 전"
    "post_service" -> "시공 후"
    "fallback_default" -> "기타 / 분류 신뢰도 낮음"
    else -> scenario
}

/** 리포트 순수 계산 — Android 비의존, 단위테스트 대상. */
object ReportCalc {
    fun todayStart(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 미수(현재) 상위 목록 — 경과일 desc, 동률 금액 desc. */
    fun overdueRows(customers: List<CustomerEntity>, todayStart: Long): List<OverdueRow> =
        customers.mapNotNull { c ->
            val row = SettlementCalc.rowOf(c)
            if (!SettlementCalc.hasMoney(c) || row.outstanding <= 0L) return@mapNotNull null
            OverdueRow(
                customerId = c.id,
                name = c.name?.takeIf { it.isNotBlank() } ?: "이름 없음",
                site = AddressExtractor.roughSite(c.address).orEmpty(),
                amount = row.outstanding,
                days = SettlementCalc.overdueDays(c, todayStart)
            )
        }.sortedWith(compareByDescending<OverdueRow> { it.days ?: -1 }.thenByDescending { it.amount })

    /** 입금이 [from,to) 에 들어온 금액을 카테고리별로. 매출 desc, 상위 5 + 기타. */
    fun groupByCategory(customers: List<CustomerEntity>, from: Long, to: Long, catNames: Map<Long, String>): List<GroupRow> =
        group(customers, from, to) { c -> c.categoryId?.let { catNames[it] } ?: "기타 시공" }

    /** 지역(roughSite)별. 주소 없음은 맨 아래. */
    fun groupByRegion(customers: List<CustomerEntity>, from: Long, to: Long): List<GroupRow> {
        val rows = group(customers, from, to) { c ->
            AddressExtractor.roughSite(c.address).orEmpty().takeIf { it.isNotBlank() }
                ?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "주소 없음"
        }
        // "주소 없음" 은 항상 맨 아래.
        val (noAddr, has) = rows.partition { it.name == "주소 없음" }
        return has + noAddr
    }

    private fun group(
        customers: List<CustomerEntity>, from: Long, to: Long, keyOf: (CustomerEntity) -> String
    ): List<GroupRow> {
        data class Acc(var amount: Long = 0L, var count: Int = 0, var outstanding: Long = 0L)
        val map = LinkedHashMap<String, Acc>()
        for (c in customers) {
            val row = SettlementCalc.rowOf(c)
            var received = 0L
            c.depositPaidAt?.let { if (it in from until to) received += row.depositAmount }
            c.balancePaidAt?.let { if (it in from until to) received += row.balanceAmount }
            if (received <= 0L) continue
            val acc = map.getOrPut(keyOf(c)) { Acc() }
            acc.amount += received; acc.count += 1; acc.outstanding += row.outstanding
        }
        val all = map.map { (k, v) -> GroupRow(k, v.amount, v.count, v.outstanding) }.sortedByDescending { it.amount }
        return if (all.size <= 5) all
        else all.take(5) + GroupRow("그 외", all.drop(5).sumOf { it.amount }, all.drop(5).sumOf { it.count }, all.drop(5).sumOf { it.outstanding })
    }
}

enum class ReportPeriod(val label: String) {
    THIS_MONTH("이번 달"), LAST_MONTH("지난 달"), LAST_3M("최근 3개월")
}

data class OverdueRow(val customerId: Long, val name: String, val site: String, val amount: Long, val days: Int?)
data class ScenarioRow(val label: String, val rate: Int, val total: Int, val needsImprove: Boolean)
data class GroupRow(val name: String, val amount: Long, val count: Int, val outstanding: Long)

data class ReportUiState(
    val periodLabel: String = "이번 달",
    val revenue: Long = 0, val prevRevenue: Long = 0, val revenueDeltaPct: Int? = null, val jobs: Int = 0,
    val outstandingNow: Long = 0, val outstandingCust: Int = 0, val paidOffNow: Int = 0, val moneyCust: Int = 0,
    val overdue: List<OverdueRow> = emptyList(),
    val adoptionPct: Int? = null, val adoptedCount: Int = 0, val suggestionTotal: Int = 0, val avgEdit: Int = 0,
    val scenarios: List<ScenarioRow> = emptyList(),
    val inboundCount: Int = 0, val conversionPct: Int? = null,
    val sentCount: Int = 0, val estimateCount: Int = 0, val unhandledCount: Int = 0,
    val categoryRows: List<GroupRow> = emptyList(), val regionRows: List<GroupRow> = emptyList(),
    val improveScenario: ScenarioRow? = null,
    val newCustomers: Int = 0
)
