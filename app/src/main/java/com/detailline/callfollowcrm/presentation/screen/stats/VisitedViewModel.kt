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
/**
 * @param monthDelta 「내 기록」에서 고른 달. 0 = 이번 달, -1 = 지난달 …
 *   ⚠️ 전엔 **늘 이번 달**이라, 8월 할 일을 누르면 9월 목록이 열렸다. (2026-09-25 점검)
 */
class VisitedViewModel(container: AppContainer, private val monthDelta: Int = 0) : ViewModel() {

    // '이번 달/오늘' 경계는 필드로 굳히지 않고 monthJobs/build 에서 매번 계산 — 달/자정 넘겨 켜둬도 정확. (2026-08-13 stale fix)
    private val smsRepository = container.smsRepository
    private val customers = container.customerRepository.observeAll()
    /**
     * 시공 **건**들. (2026-09-18 실기에서 발견해 고침)
     *   전엔 고객 표만 봐서 **손님당 한 곳**만 셌다 — 한 손님에게 1·2·3차를 해도
     *   '다녀온 현장 1곳', 매출도 대표 건 금액 하나뿐이었다.
     */
    private val jobsFlow = container.jobRepository.observeAll()

    /** customerId → 문자에서 추출한 주소("" = 스캔했지만 못 찾음). 키 존재 = 스캔 완료. */
    private val extractedAddr = MutableStateFlow<Map<Long, String>>(emptyMap())

    val state: StateFlow<VisitedState> =
        combine(customers, jobsFlow, extractedAddr) { cs, js, extra -> build(cs, js, extra) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitedState())

    init {
        // 직접 주소 없는 현장만 골라 백그라운드에서 한 번에 하나씩 주소 추출 → state 점진 갱신.
        viewModelScope.launch(Dispatchers.IO) {
            combine(customers, jobsFlow) { cs, js -> monthUnits(cs, js) }
                .map { units -> units.filter { it.address.isNullOrBlank() } }
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

    /**
     * 이번 달 **현장 한 곳 = 건 하나**. 건이 하나도 없는 옛 고객만 고객 표로 센다.
     *   건 값을 채운 CustomerEntity 복사본을 돌려준다 — 아래 계산이 그대로 돌아간다.
     */
    private fun monthUnits(
        cs: List<CustomerEntity>,
        js: List<com.detailline.callfollowcrm.data.local.entity.JobEntity>
    ): List<CustomerEntity> {
        val ms = shiftMonth(monthStartOf(System.currentTimeMillis()), monthDelta)
        val me = shiftMonth(ms, +1)
        val byId = cs.associateBy { it.id }
        val fromJobs = js.mapNotNull { j ->
            val d = j.scheduledWorkDate ?: return@mapNotNull null
            if (d < ms || d >= me) return@mapNotNull null
            val c = byId[j.customerId] ?: return@mapNotNull null
            c.copy(
                scheduledWorkDate = d,
                scheduledWorkMinutes = j.scheduledWorkMinutes,
                address = j.address?.takeIf { it.isNotBlank() } ?: c.address,
                totalAmount = j.totalAmount,
                depositAmount = j.depositAmount,
                depositPaidAt = j.depositPaidAt,
                balanceAmount = j.balanceAmount,
                balancePaidAt = j.balancePaidAt,
                workCompletedAt = j.workCompletedAt
            )
        }
        val hasAnyJob = js.map { it.customerId }.toHashSet()
        val legacy = cs.filter {
            it.id !in hasAnyJob && it.scheduledWorkDate?.let { d -> d in ms until me } == true
        }
        return fromJobs + legacy
    }

    private fun build(
        cs: List<CustomerEntity>,
        js: List<com.detailline.callfollowcrm.data.local.entity.JobEntity>,
        extra: Map<Long, String>
    ): VisitedState {
        val now = System.currentTimeMillis()   // 매번 현재 기준 (stale fix)
        val todayStart = DateTimeUtils.startOfDay(now)
        val monthStart = shiftMonth(monthStartOf(now), monthDelta)
        val jobs = monthUnits(cs, js)

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
                upcoming = day >= todayStart,
                done = c.workCompletedAt != null,
                hasAddr = manual != null || extra[c.id]?.isNotBlank() == true,
                amountManwon = (
                    (c.totalAmount ?: ((c.depositAmount ?: 0L) + (c.balanceAmount ?: 0L))) / 10_000L
                    ).toInt()
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
    val upcoming: Boolean,
    /** 완료를 눌렀나. 안 눌렀으면 번호가 안 붙는다 — **할 일**이다. */
    val done: Boolean = true,
    /** 주소를 찾았나. 못 찾으면 지도 동네에 안 들어간다 — **할 일**이다. */
    val hasAddr: Boolean = true,
    /**
     * 그 현장 금액(만원). **걸러서 볼 때 합계를 다시 더하려고** 줄이 제 금액을 들고 다닌다.
     *   (2026-09-25: 2곳만 걸러 놓고 매출은 그 달 전체가 남아 "이 2곳이 475만원" 으로 읽혔다)
     */
    val amountManwon: Int = 0
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
