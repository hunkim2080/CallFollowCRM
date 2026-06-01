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

    /** 미수금 목록 / 현금흐름 달력 전환. */
    private val tab = MutableStateFlow(SettleTab.LIST)
    val tabState: StateFlow<SettleTab> = tab
    fun setTab(t: SettleTab) { tab.value = t }

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
                            calc = SettlementCalc.rowOf(c)
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

enum class SettleTab(val label: String) { LIST("미수금"), CASHFLOW("현금흐름") }

enum class SettleFilter(val label: String) {
    ALL("전체"), OUTSTANDING("미수"), PAID_OFF("완납")
}

data class SettleItem(
    val customerId: Long,
    val name: String?,
    val phone: String,
    val calc: SettleRow
)

data class SettlementUiState(
    val rows: List<SettleItem> = emptyList(),
    val outstandingTotal: Long = 0L,
    val receivedTotal: Long = 0L,
    val allCount: Int = 0,
    val outstandingCount: Int = 0,
    val paidOffCount: Int = 0
)
