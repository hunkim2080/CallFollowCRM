package com.detailline.callfollowcrm.presentation.screen.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/**
 * 다녀온 현장 목록 — 프로토 `s-visited`/openVisited 1:1.
 *   통계 "다녀온 현장 N곳" 셀 탭 → 이번 달 시공(scheduledWorkDate) 현장 리스트(날짜·이름·주소) + 매출 합계.
 *   카운트는 통계 stat-grid "다녀온 현장" 과 동일 정의(scheduledWorkDate 가 이번 달) → 숫자 일치.
 */
class VisitedViewModel(container: AppContainer) : ViewModel() {

    private val nowMs = System.currentTimeMillis()
    private val monthStart = monthStartOf(nowMs)
    private val monthEnd = shiftMonth(monthStart, +1)

    val state: StateFlow<VisitedState> =
        container.customerRepository.observeAll()
            .map { build(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitedState())

    private fun build(cs: List<CustomerEntity>): VisitedState {
        val jobs = cs.filter { it.scheduledWorkDate?.let { d -> d in monthStart until monthEnd } == true }
            .sortedByDescending { it.scheduledWorkDate ?: 0L }

        val rows = jobs.map { c ->
            VisitedRow(
                customerId = c.id,
                dateLabel = c.scheduledWorkDate?.let { dateMd(it) }.orEmpty(),
                name = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber),
                addr = c.address?.takeIf { it.isNotBlank() } ?: "주소 미등록"
            )
        }
        // 매출 = 총금액(있으면) 아니면 계약금+잔금. 없는 건 0.
        val revenueWon = jobs.sumOf { c ->
            c.totalAmount ?: ((c.depositAmount ?: 0L) + (c.balanceAmount ?: 0L))
        }
        return VisitedState(
            monthLabel = "${monthOf(monthStart)}월",
            count = jobs.size,
            revenueManwon = (revenueWon / 10_000L).toInt(),
            rows = rows,
            loaded = true
        )
    }

    private fun dateMd(ms: Long): String = Calendar.getInstance().apply { timeInMillis = ms }
        .let { "${it.get(Calendar.MONTH) + 1}/${it.get(Calendar.DAY_OF_MONTH)}" }

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
}

data class VisitedRow(
    val customerId: Long,
    val dateLabel: String,
    val name: String,
    val addr: String
)

data class VisitedState(
    val monthLabel: String = "",
    val count: Int = 0,
    val revenueManwon: Int = 0,
    val rows: List<VisitedRow> = emptyList(),
    val loaded: Boolean = false
)
