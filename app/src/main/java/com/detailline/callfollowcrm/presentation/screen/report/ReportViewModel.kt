package com.detailline.callfollowcrm.presentation.screen.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.settlement.SettlementCalc
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/**
 * 비즈니스 리포트 (2026-06-01) — 기존 데이터(고객·입금·시공일)만으로 집계. DB 변경 없음.
 *   기간(이번달/지난달/최근3개월)별: 매출(받은 돈)·시공 현장 수·신규 고객.
 *   미수금/완납은 "현재" 기준(기간 무관).
 */
class ReportViewModel(container: AppContainer) : ViewModel() {

    private val period = MutableStateFlow(ReportPeriod.THIS_MONTH)
    val periodState: StateFlow<ReportPeriod> = period
    fun setPeriod(p: ReportPeriod) { period.value = p }

    val state: StateFlow<ReportUiState> =
        combine(container.customerRepository.observeAll(), period) { customers, p ->
            val (from, to) = rangeOf(p)
            var revenue = 0L
            var jobs = 0
            var newCustomers = 0
            var outstandingNow = 0L
            var paidOffNow = 0
            for (c in customers) {
                val row = SettlementCalc.rowOf(c)
                c.depositPaidAt?.let { if (it in from until to) revenue += row.depositAmount }
                c.balancePaidAt?.let { if (it in from until to) revenue += row.balanceAmount }
                c.scheduledWorkDate?.let { if (it in from until to) jobs++ }
                if (c.createdAt in from until to) newCustomers++
                if (SettlementCalc.hasMoney(c)) {
                    outstandingNow += row.outstanding
                    if (row.isPaidOff) paidOffNow++
                }
            }
            ReportUiState(
                revenue = revenue,
                jobs = jobs,
                newCustomers = newCustomers,
                outstandingNow = outstandingNow,
                paidOffNow = paidOffNow
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())

    /** 기간 [from, to) epoch ms. now 기준. */
    private fun rangeOf(p: ReportPeriod): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startThisMonth = cal.timeInMillis
        return when (p) {
            ReportPeriod.THIS_MONTH -> {
                val next = (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
                startThisMonth to next
            }
            ReportPeriod.LAST_MONTH -> {
                val startLast = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.timeInMillis
                startLast to startThisMonth
            }
            ReportPeriod.LAST_3M -> {
                val start = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -2) }.timeInMillis
                val next = (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
                start to next
            }
        }
    }
}

enum class ReportPeriod(val label: String) {
    THIS_MONTH("이번 달"), LAST_MONTH("지난 달"), LAST_3M("최근 3개월")
}

data class ReportUiState(
    val revenue: Long = 0,
    val jobs: Int = 0,
    val newCustomers: Int = 0,
    val outstandingNow: Long = 0,
    val paidOffNow: Int = 0
)
