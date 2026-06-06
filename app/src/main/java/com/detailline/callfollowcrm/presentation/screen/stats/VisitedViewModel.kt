package com.detailline.callfollowcrm.presentation.screen.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.util.AddressExtractor
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 다녀온/다녀올 현장 목록 — 프로토 `s-visited` 확장.
 *   통계 "다녀온 현장 N곳" 셀 탭 → 이번 달 시공(scheduledWorkDate) 현장 리스트.
 *
 *   2026-06-06 사장님 통점:
 *     ① "불러오는 중…"이 오래 떴음 — 모든 현장의 문자(SMS)를 스캔해 주소 추출하는 걸 기다린 뒤 한꺼번에 표시했기 때문.
 *        → 목록은 **즉시** 표시(직접 입력 주소 먼저), 자동 인식 주소는 백그라운드에서 **하나씩 채움**("주소 확인 중…" → 주소).
 *     ② "다녀온"에 미래 현장이 섞여 보임 → 지난(다녀온·초록) / 예정(다녀올·파랑) 분리 + 색 구분.
 */
class VisitedViewModel(container: AppContainer) : ViewModel() {

    private val nowMs = System.currentTimeMillis()
    private val todayStart = DateTimeUtils.startOfDay(nowMs)
    private val monthStart = monthStartOf(nowMs)
    private val monthEnd = shiftMonth(monthStart, +1)
    private val smsRepository = container.smsRepository
    private val customers = container.customerRepository.observeAll()

    /** customerId → 문자에서 추출한 주소("" = 스캔했지만 못 찾음). 키 존재 = 스캔 완료. */
    private val extractedAddr = MutableStateFlow<Map<Long, String>>(emptyMap())

    val state: StateFlow<VisitedState> =
        combine(customers, extractedAddr) { cs, extra -> build(cs, extra) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitedState())

    init {
        // 직접 주소 없는 현장만 골라 백그라운드에서 한 번에 하나씩 주소 추출 → state 점진 갱신.
        viewModelScope.launch(Dispatchers.IO) {
            customers
                .map { cs -> monthJobs(cs).filter { it.address.isNullOrBlank() } }
                .distinctUntilChanged { a, b -> a.map { it.id }.toSet() == b.map { it.id }.toSet() }
                .collect { needAddr ->
                    for (c in needAddr) {
                        if (extractedAddr.value.containsKey(c.id)) continue   // 이미 스캔함
                        val found = runCatching {
                            AddressExtractor.extractFromMessages(
                                smsRepository.queryByPhone(c.phoneNumber, scanLimit = 200)
                                    .sortedByDescending { it.dateMs }
                                    .map { it.body }
                            )
                        }.getOrNull().orEmpty()
                        extractedAddr.value = extractedAddr.value + (c.id to found)
                    }
                }
        }
    }

    private fun monthJobs(cs: List<CustomerEntity>): List<CustomerEntity> =
        cs.filter { it.scheduledWorkDate?.let { d -> d in monthStart until monthEnd } == true }

    private fun build(cs: List<CustomerEntity>, extra: Map<Long, String>): VisitedState {
        val jobs = monthJobs(cs)

        fun toRow(c: CustomerEntity): VisitedRow {
            val manual = c.address?.takeIf { it.isNotBlank() }
            val addr = when {
                manual != null -> manual
                extra.containsKey(c.id) -> extra[c.id]?.takeIf { it.isNotBlank() } ?: "주소 미등록"
                else -> "주소 확인 중…"   // 아직 스캔 전
            }
            val day = c.scheduledWorkDate ?: 0L
            return VisitedRow(
                customerId = c.id,
                dateLabel = if (day > 0) dateMd(day) else "",
                name = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber),
                addr = addr,
                upcoming = day >= todayStart
            )
        }

        val visited = jobs.filter { (it.scheduledWorkDate ?: 0L) < todayStart }
            .sortedByDescending { it.scheduledWorkDate ?: 0L }.map { toRow(it) }
        val upcoming = jobs.filter { (it.scheduledWorkDate ?: 0L) >= todayStart }
            .sortedBy { it.scheduledWorkDate ?: 0L }.map { toRow(it) }

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
