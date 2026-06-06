package com.detailline.callfollowcrm.presentation.screen.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.util.AddressExtractor
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/**
 * 다녀온/다녀올 현장 목록 — 프로토 `s-visited`/openVisited 확장.
 *   통계 "다녀온 현장 N곳" 셀 탭 → 이번 달 시공(scheduledWorkDate) 현장 리스트.
 *   2026-06-06 사장님 통점:
 *     ① 주소가 미등록으로 뜨던 것 → 고객 카드와 동일하게 수동 주소 없으면 문자에서 추출(extractedAddress) 폴백.
 *     ② "다녀온 현장"에 아직 안 간(미래) 현장이 섞여있던 것 → 지난(다녀온) / 예정(다녀올) 분리 + 색 구분.
 */
class VisitedViewModel(container: AppContainer) : ViewModel() {

    private val nowMs = System.currentTimeMillis()
    private val todayStart = DateTimeUtils.startOfDay(nowMs)
    private val monthStart = monthStartOf(nowMs)
    private val monthEnd = shiftMonth(monthStart, +1)
    private val smsRepository = container.smsRepository

    val state: StateFlow<VisitedState> =
        container.customerRepository.observeAll()
            .map { build(it) }
            .flowOn(Dispatchers.IO)   // 주소 추출이 ContentResolver(SMS) 스캔 → IO 에서.
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitedState())

    private fun build(cs: List<CustomerEntity>): VisitedState {
        val jobs = cs.filter { it.scheduledWorkDate?.let { d -> d in monthStart until monthEnd } == true }

        fun toRow(c: CustomerEntity): VisitedRow {
            val manual = c.address?.takeIf { it.isNotBlank() }
            // 수동 주소 없으면 그 번호 문자에서 한국 주소 패턴 추출 (고객 카드 displayAddr 과 동일 규칙).
            val resolved = manual ?: runCatching {
                AddressExtractor.extractFromMessages(
                    smsRepository.queryByPhone(c.phoneNumber, scanLimit = 300)
                        .sortedByDescending { it.dateMs }
                        .map { it.body }
                )
            }.getOrNull()
            val day = c.scheduledWorkDate ?: 0L
            return VisitedRow(
                customerId = c.id,
                dateLabel = if (day > 0) dateMd(day) else "",
                name = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber),
                addr = resolved?.takeIf { it.isNotBlank() } ?: "주소 미등록",
                upcoming = day >= todayStart
            )
        }

        val visited = jobs.filter { (it.scheduledWorkDate ?: 0L) < todayStart }
            .sortedByDescending { it.scheduledWorkDate ?: 0L }
            .map { toRow(it) }
        val upcoming = jobs.filter { (it.scheduledWorkDate ?: 0L) >= todayStart }
            .sortedBy { it.scheduledWorkDate ?: 0L }
            .map { toRow(it) }

        // 매출 합계 = 다녀온(완료) 현장만. 총금액(있으면) 아니면 계약금+잔금.
        val revenueWon = jobs.filter { (it.scheduledWorkDate ?: 0L) < todayStart }.sumOf { c ->
            c.totalAmount ?: ((c.depositAmount ?: 0L) + (c.balanceAmount ?: 0L))
        }
        return VisitedState(
            monthLabel = "${monthOf(monthStart)}월",
            visitedRows = visited,
            upcomingRows = upcoming,
            revenueManwon = (revenueWon / 10_000L).toInt(),
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
    val addr: String,
    /** true = 아직 안 간 예정 현장(다녀올), false = 지난 현장(다녀온). */
    val upcoming: Boolean
)

data class VisitedState(
    val monthLabel: String = "",
    val visitedRows: List<VisitedRow> = emptyList(),
    val upcomingRows: List<VisitedRow> = emptyList(),
    val revenueManwon: Int = 0,
    val loaded: Boolean = false
) {
    val visitedCount: Int get() = visitedRows.size
    val upcomingCount: Int get() = upcomingRows.size
}
