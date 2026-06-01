package com.detailline.callfollowcrm.presentation.screen.settlement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.settlement.CashFlowCalc
import com.detailline.callfollowcrm.domain.settlement.CashItem
import com.detailline.callfollowcrm.domain.settlement.SettleRow
import com.detailline.callfollowcrm.domain.settlement.SettlementCalc
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 정산(미수금) 화면 — 정산 Phase 1 (2026-06-01).
 *
 * "누가 돈 안 줬나" = 사장님 핵심 통점. 새 저장공간(DB 마이그레이션) 없이
 * 기존 CustomerEntity 의 돈 필드(total/deposit/balance + paidAt)만으로 구성.
 *
 * 계산은 [SettlementCalc] 단일 출처 (홈 미수금 카드 / 테스트와 공유).
 */
class SettlementViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(SettleFilter.ALL)
    val filterState: StateFlow<SettleFilter> = filter

    private val customersFlow = container.customerRepository.observeAll()

    /** 돈 정보 있는 고객만 → 미수 큰 순 정렬. */
    private val rows: StateFlow<List<SettleItem>> =
        customersFlow
            .map { list ->
                list.filter { SettlementCalc.hasMoney(it) }
                    .map { c ->
                        SettleItem(
                            customerId = c.id,
                            name = c.name?.takeIf { it.isNotBlank() },
                            phone = c.phoneNumber,
                            calc = SettlementCalc.rowOf(c),
                            scheduledWorkDate = c.scheduledWorkDate,
                            address = c.address
                        )
                    }
                    .sortedWith(
                        compareByDescending<SettleItem> { it.calc.outstanding }
                            .thenByDescending { it.calc.total }
                    )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<SettlementUiState> = combine(rows, filter) { all, f ->
        val outstandingTotal = all.sumOf { it.calc.outstanding }
        val receivedTotal = all.sumOf { it.calc.received }
        val outstandingCount = all.count { it.calc.outstanding > 0 }
        val paidOffCount = all.count { it.calc.isPaidOff }
        val visible = when (f) {
            SettleFilter.ALL -> all
            SettleFilter.OUTSTANDING -> all.filter { it.calc.outstanding > 0 }
            SettleFilter.PAID_OFF -> all.filter { it.calc.isPaidOff }
        }
        SettlementUiState(
            rows = visible,
            outstandingTotal = outstandingTotal,
            receivedTotal = receivedTotal,
            allCount = all.size,
            outstandingCount = outstandingCount,
            paidOffCount = paidOffCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettlementUiState())

    // ── 월매출 대시보드 (프로토 settle-top) ──────────────────────────
    // 프로토 renderSettle: 월 이동 + "이번 달 받은 돈" + 전월 대비 + 목표 진행률 + 페이스.
    // 받은 돈 = 그 달에 paidAt 찍힌 계약금/잔금 합. 전월 대비 = 직전 달 받은돈과 비교.
    private val currentMonthAnchor = monthStartOf(System.currentTimeMillis())

    /** 다크카드가 보여줄 달(월초 epoch). 기본 = 이번 달. */
    private val monthAnchor = MutableStateFlow(currentMonthAnchor)

    /** 이번 달 목표 매출(만원). 프로토 settleGoal 기본 500. */
    private val goalManwon = MutableStateFlow(container.preferences.monthlyGoalManwon)
    fun setMonthlyGoal(manwon: Int) {
        if (manwon <= 0) return
        container.preferences.monthlyGoalManwon = manwon
        goalManwon.value = manwon
    }

    fun prevMonth() { monthAnchor.value = shiftMonth(monthAnchor.value, -1) }
    fun nextMonth() {
        val next = shiftMonth(monthAnchor.value, +1)
        if (next <= currentMonthAnchor) monthAnchor.value = next   // 미래 달로는 못 감
    }

    val settleTop: StateFlow<SettleTopState> =
        combine(customersFlow, monthAnchor, goalManwon, state) { customers, anchor, goal, st ->
            buildSettleTop(customers, anchor, goal, st)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettleTopState())

    private fun buildSettleTop(
        customers: List<com.detailline.callfollowcrm.data.local.entity.CustomerEntity>,
        anchor: Long,
        goalManwon: Int,
        st: SettlementUiState
    ): SettleTopState {
        val monthEnd = shiftMonth(anchor, +1)
        val prevStart = shiftMonth(anchor, -1)
        val received = receivedInMonth(customers, anchor, monthEnd)
        val prevReceived = receivedInMonth(customers, prevStart, anchor)
        val prevPct = when {
            prevReceived > 0L -> Math.round((received - prevReceived) * 100.0 / prevReceived).toInt()
            received > 0L -> 100
            else -> 0
        }
        val isLive = anchor == currentMonthAnchor
        val goalWon = goalManwon.toLong() * 10_000L
        val fillPct = if (goalWon > 0L) ((received * 100.0 / goalWon).toInt()).coerceIn(0, 100) else 0
        val reached = received >= goalWon && goalWon > 0L

        val m2Top: String
        val m2Next: String
        if (reached) {
            m2Top = "🎉 목표 ${comma(goalManwon)}만원 달성!"
            m2Next = "목표 초과 +${manwon(received - goalWon)}만원 · 최고예요 👑"
        } else {
            m2Top = "🎯 이번 달 목표 ${comma(goalManwon)}만원"
            m2Next = "목표까지 ${manwon(goalWon - received)}만원 남았어요 · $fillPct%"
        }

        // 페이스 (이번 달만): 시간 진행률 vs 목표 진행률
        var paceText: String? = null
        var paceAhead = false
        var daysLeft = 0
        if (isLive) {
            val cal = Calendar.getInstance()
            val dayN = cal.get(Calendar.DAY_OF_MONTH)
            val daysIn = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            daysLeft = daysIn - dayN
            val timePct = (dayN * 100.0 / daysIn).toInt().coerceIn(0, 100)
            val gap = fillPct - timePct
            when {
                reached -> { paceAhead = true; paceText = "🎉 목표 달성! 남은 ${daysLeft}일은 보너스예요" }
                gap >= -5 -> { paceAhead = true; paceText = "👍 시간보다 앞서가고 있어요 · 좋은 페이스!" }
                daysLeft <= 3 -> { paceAhead = false; paceText = "🔥 ${daysLeft}일 남았어요 · 막판 스퍼트!" }
                else -> { paceAhead = false; paceText = "💪 페이스를 조금만 올리면 따라잡아요" }
            }
        }

        return SettleTopState(
            monthLabel = monthLabelOf(anchor),
            isLiveMonth = isLive,
            receivedManwon = manwon(received),
            recvLabel = if (isLive) "이번 달 받은 돈" else "받은 돈",
            prevPct = prevPct,
            goalReached = reached,
            fillPct = fillPct,
            m2Top = m2Top,
            m2Next = m2Next,
            paceText = paceText,
            paceAhead = paceAhead,
            daysLeft = daysLeft,
            outstandingManwon = manwon(st.outstandingTotal),
            outstandingCount = st.outstandingCount,
            canGoNext = anchor < currentMonthAnchor
        )
    }

    /** 그 달에 받은 돈(원) = 계약금/잔금 중 paidAt 이 [start,end) 인 것 합. */
    private fun receivedInMonth(
        customers: List<com.detailline.callfollowcrm.data.local.entity.CustomerEntity>,
        start: Long,
        end: Long
    ): Long {
        var sum = 0L
        customers.forEach { c ->
            val row = SettlementCalc.rowOf(c)
            c.depositPaidAt?.let { if (it in start until end) sum += row.depositAmount }
            c.balancePaidAt?.let { if (it in start until end) sum += row.balanceAmount }
        }
        return sum
    }

    // ── 현금흐름 (Phase 2) ───────────────────────────────────────────
    /** settle 파생 수입 + 직접 기록 + 일당 배정(자동 지출) 합산. 달력/일별 상세가 구독. */
    val cashItems: StateFlow<List<CashItem>> =
        combine(
            customersFlow,
            container.manualCashRepository.observeAll(),
            container.jobCrewRepository.observeAll()
        ) { cs, ms, crew ->
            val today = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(System.currentTimeMillis())
            CashFlowCalc.buildItems(cs, ms, crew, today)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addManualCash(dayMs: Long, amount: Long, isIncome: Boolean, isDone: Boolean, label: String) =
        viewModelScope.launch {
            withContext(NonCancellable) {
                container.manualCashRepository.add(dayMs, amount, isIncome, isDone, label)
            }
        }

    fun toggleManualDone(id: Long, done: Boolean) = viewModelScope.launch {
        withContext(NonCancellable) { container.manualCashRepository.setDone(id, done) }
    }

    fun setManualAmount(id: Long, amount: Long) = viewModelScope.launch {
        withContext(NonCancellable) { container.manualCashRepository.setAmount(id, amount) }
    }

    fun deleteManualCash(id: Long) = viewModelScope.launch {
        withContext(NonCancellable) { container.manualCashRepository.delete(id) }
    }

    fun setFilter(f: SettleFilter) { filter.value = f }

    /** 계약금 받음/안받음 토글. "지금" 받은 시각으로 기록, 끄면 null. */
    fun setDepositPaid(customerId: Long, paid: Boolean) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateDepositPaidAt(
                customerId, if (paid) System.currentTimeMillis() else null
            )
            // 입금 변경 → 자동 카테고리 갱신 (시공 완료/대기 분류). 서버/분류기 없으면 silent.
            runCatching { container.autoCategoryClassifier.reclassify(customerId) }
        }
    }

    /** 잔금 받음/안받음 토글. 잔금까지 받으면 완납. 끄면 = 완납 취소. */
    fun setBalancePaid(customerId: Long, paid: Boolean) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateBalancePaidAt(
                customerId, if (paid) System.currentTimeMillis() else null
            )
            runCatching { container.autoCategoryClassifier.reclassify(customerId) }
        }
    }
}

// ── 월 계산 헬퍼 ─────────────────────────────────────────────────
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

private fun monthLabelOf(anchorMs: Long): String = Calendar.getInstance().apply { timeInMillis = anchorMs }
    .let { "${it.get(Calendar.YEAR)}년 ${it.get(Calendar.MONTH) + 1}월" }

/** 원 → 만원(반올림 내림) 정수. 프로토 amt 가 만원 단위. */
private fun manwon(won: Long): Int = (won / 10_000L).toInt()
private fun comma(n: Int): String = "%,d".format(n)

enum class SettleFilter(val label: String) {
    ALL("전체"), OUTSTANDING("미수"), PAID_OFF("완납")
}

data class SettleItem(
    val customerId: Long,
    val name: String?,
    val phone: String,
    val calc: SettleRow,
    val scheduledWorkDate: Long? = null,
    val address: String? = null
)

data class SettlementUiState(
    val rows: List<SettleItem> = emptyList(),
    val outstandingTotal: Long = 0L,
    val receivedTotal: Long = 0L,
    val allCount: Int = 0,
    val outstandingCount: Int = 0,
    val paidOffCount: Int = 0
)

/** 정산 상단 다크카드(프로토 settle-top) 상태. 금액은 만원 단위 정수. */
data class SettleTopState(
    val monthLabel: String = "",
    val isLiveMonth: Boolean = true,
    val receivedManwon: Int = 0,
    val recvLabel: String = "이번 달 받은 돈",
    val prevPct: Int = 0,
    val goalReached: Boolean = false,
    val fillPct: Int = 0,
    val m2Top: String = "",
    val m2Next: String = "",
    val paceText: String? = null,
    val paceAhead: Boolean = false,
    val daysLeft: Int = 0,
    val outstandingManwon: Int = 0,
    val outstandingCount: Int = 0,
    val canGoNext: Boolean = false
)
