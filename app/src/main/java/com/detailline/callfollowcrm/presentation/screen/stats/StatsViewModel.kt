package com.detailline.callfollowcrm.presentation.screen.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CategoryEntity
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(container: AppContainer) : ViewModel() {

    // ⚠️ '이번 달/이번 주' 경계를 필드로 굳히지 않는다 — 앱을 달/주 넘겨 켜두면 지난 달/주를 보여주던 버그.
    //   buildState/buildTrend 에서 매번 현재 기준으로 계산하고, 월 집계 쿼리도 반응형으로. (2026-08-13 stale fix)
    private val customers = container.customerRepository.observeAll()
    private val categories = container.categoryRepository.observeAll()
    /**
     * 시공 **건**들 — '현장 수'는 건으로 센다. (2026-09-18)
     *   전엔 고객 표만 봐서 한 손님에게 1·2·3차를 해도 '1곳'이었다.
     *   ⚠️ **전환율은 손님 기준 그대로** — 문의 한 건이 시공으로 이어졌나를 보는 값이라
     *      건으로 세면 100%를 넘는다.
     */
    private val jobsFlow = container.jobRepository.observeAll()
    private val sentThisMonth = customers.flatMapLatest {
        val ms = monthStartOf(System.currentTimeMillis())
        container.messageHistoryRepository.observeSentCountBetween(ms, shiftMonth(ms, +1))
    }

    // 문의 추이용 실제 문의 소스 — 받은 문자/MMS 캐시 + 받은 전화. (고객 카드 createdAt 아님)
    private val smsContacts = container.smsContactCacheRepository.observeAll(limit = 500)
    private val inbound = container.callRecordRepository
        .observeInboundSince(System.currentTimeMillis() - 365L * DateTimeUtils.DAY_MS)

    private val period = MutableStateFlow(StatPeriod.D7)
    val periodState: StateFlow<StatPeriod> = period
    fun setPeriod(p: StatPeriod) { period.value = p }

    val state: StateFlow<StatsUiState> =
        combine(customers, categories, sentThisMonth, jobsFlow) { cs, cats, sent, js ->
            buildState(cs, cats, sent, js)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    /**
     * 「내 기록」 — 번호가 붙어 쌓이는 현장. (2026-09-24 사장님, 프로토 artifact/EDcGwV4F)
     *   통계는 나만 보는 숫자지만 **기록은 남한테 보여줄 수 있는 것**이라 따로 뽑는다.
     */
    val myRecord: StateFlow<MyRecordState> =
        combine(customers, jobsFlow) { cs, js -> buildMyRecord(cs, js) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyRecordState())

    private val bizNameForRecord = container.preferences.bizName

    private fun buildMyRecord(
        cs: List<CustomerEntity>,
        js: List<com.detailline.callfollowcrm.data.local.entity.JobEntity>
    ): MyRecordState {
        val now = System.currentTimeMillis()
        val monthStart = monthStartOf(now)
        val monthEnd = shiftMonth(monthStart, +1)
        val addrOf = cs.associate { it.id to it.address }
        val todayStart = DateTimeUtils.startOfDay(now)
        // **다녀온 현장** = 시공일이 지난 건. 「다녀온 현장」 화면과 **같은 기준**이라야 숫자가 안 엇갈린다.
        //   (2026-09-24 폰에서 7곳 vs 4곳으로 엇갈렸다 — 완료를 안 누른 3곳 때문)
        //   ⚠️ '완료를 눌렀나' 로 세지 않는다. 완료는 며칠 뒤에 누르기도 하고 안 누르기도 한다.
        val done = js.filter { (it.scheduledWorkDate ?: 0L) in 1 until todayStart }
            .sortedByDescending { it.scheduledWorkDate ?: 0L }
        val month = done.filter { (it.scheduledWorkDate ?: 0L) in monthStart until monthEnd }
        // 다녀왔는데 **완료를 안 누른** 곳 — 번호가 안 붙는다. 그래서 할 일로 알려준다.
        val notDone = month.count { it.workCompletedAt == null }
        val towns = LinkedHashSet<String>()
        var noAddr = 0
        for (j in month) {
            val a = j.address?.takeIf { it.isNotBlank() } ?: addrOf[j.customerId]
            val t = com.detailline.callfollowcrm.util.RegionName.shortRegion(a)
            if (t == null) noAddr++ else towns.add(t)
        }
        val lastNo = js.mapNotNull { it.recordNo }.maxOrNull() ?: 0
        val top = month.firstOrNull() ?: done.firstOrNull()
        val topTown = top?.let {
            com.detailline.callfollowcrm.util.RegionName.shortRegion(
                it.address?.takeIf { a -> a.isNotBlank() } ?: addrOf[it.customerId]
            )
        }
        return MyRecordState(
            lastNo = lastNo,
            monthSites = month.size,
            towns = towns.toList(),
            noAddrCount = noAddr,
            notDoneCount = notDone,
            // 글에 적히는 날짜도 **시공한 날**. 완료를 언제 눌렀는지는 손님한테 아무 뜻이 없다.
            pasteText = buildPaste(
                top?.scheduledWorkDate ?: top?.workCompletedAt,
                topTown, top?.recordNo, month.size, towns.size
            )
        )
    }

    /**
     * 카톡·밴드·당근에 **그대로 붙이는 글**. 사진도 그림도 필요 없다. (2026-09-24 사장님)
     *   "문의 주세요" 는 안 쓴다 — 광고글이 되면 부담스러워서 안 올린다.
     *   그냥 **오늘 뭘 했는지 남기는 글**이라야 매일 올리게 되고, 매일 올라오는 게 제일 센 영업이다.
     */
    private fun buildPaste(
        doneAt: Long?, town: String?, no: Int?, monthSites: Int, townCount: Int
    ): String {
        if (doneAt == null) return ""
        val d = java.util.Calendar.getInstance().apply { timeInMillis = doneAt }
        val date = "%d.%02d.%02d".format(
            d.get(java.util.Calendar.YEAR),
            d.get(java.util.Calendar.MONTH) + 1,
            d.get(java.util.Calendar.DAY_OF_MONTH)
        )
        val sb = StringBuilder()
        sb.append("오늘의 시공 기록").append("\n\n")
        sb.append(date).append("\n")
        sb.append(if (town != null) "${town}에서 한 집을 마쳤습니다." else "한 집을 마쳤습니다.").append("\n\n")
        if (no != null) sb.append("· 올해 ").append(no).append("번째 현장").append("\n")
        if (monthSites > 0) sb.append("· 이번 달 ").append(monthSites).append("곳").append("\n")
        if (townCount > 0) sb.append("· 다녀온 동네 ").append(townCount).append("곳").append("\n")
        sb.append("\n오늘도 한 집을 끝냈습니다.")
        val biz = bizNameForRecord
        if (biz.isNotBlank()) sb.append("\n\n— ").append(biz)
        return sb.toString()
    }

    val trend: StateFlow<StatsTrendState> =
        combine(smsContacts, inbound, period) { sms, calls, p -> buildTrend(sms, calls, p) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsTrendState())

    // ── 이번 달 고정 집계 ────────────────────────────────────────────
    private fun buildState(
        cs: List<CustomerEntity>,
        cats: List<CategoryEntity>,
        sent: Int,
        js: List<com.detailline.callfollowcrm.data.local.entity.JobEntity> = emptyList()
    ): StatsUiState {
        val now = System.currentTimeMillis()   // 매번 현재 기준 (stale-month fix)
        val monthStart = monthStartOf(now)
        val monthEnd = shiftMonth(monthStart, +1)
        val lastMonthStart = shiftMonth(monthStart, -1)
        val lastYearStart = shiftMonth(monthStart, -12)
        val lastYearEnd = shiftMonth(monthEnd, -12)
        // 현장 수 = **건** 기준. 건이 하나도 없는 옛 고객만 고객 표로 센다.
        val hasAnyJob = js.map { it.customerId }.toHashSet()
        fun inMonth(d: Long?) = d != null && d >= monthStart && d < monthEnd
        val jobs = js.count { inMonth(it.scheduledWorkDate) } +
            cs.count { it.id !in hasAnyJob && inMonth(it.scheduledWorkDate) }
        val inquiries = cs.count { it.createdAt in monthStart until monthEnd }
        // 전환율은 **손님** 기준 — 문의한 사람 중 몇 명이 시공으로 이어졌나. 건으로 세면 100%를 넘는다.
        val convertedCustomers = (
            js.filter { inMonth(it.scheduledWorkDate) }.map { it.customerId }.toSet() +
                cs.filter { it.id !in hasAnyJob && inMonth(it.scheduledWorkDate) }.map { it.id }
            ).size
        val conversion = if (inquiries > 0) Math.round(convertedCustomers * 100.0 / inquiries).toInt() else 0

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
            greeting = greetingOf(now),
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
    // 2026-06-06 버그 수정: 기존엔 고객 카드 createdAt(=레코드 생성·가져오기 시각)으로 집계 →
    //   밤늦게 온 문의가 다음날로, 수동/가져온 고객도 "문의"로 잡혀 엉뚱한 날에 막대가 섰음.
    //   이제 "그 번호의 가장 처음 연락(받은 문자 최초 + 받은 전화 최초)" = 실제 신규 문의 시각으로 집계.
    private fun buildTrend(
        sms: List<SmsRepository.SmsContact>,
        calls: List<CallRecordEntity>,
        p: StatPeriod
    ): StatsTrendState {
        val now = System.currentTimeMillis()   // 매번 현재 기준 (stale-week fix)
        val firstDayBySuffix = HashMap<String, Long>()
        fun mark(suffix: String, ms: Long) {
            if (suffix.isBlank() || ms <= 0L) return
            val day = DateTimeUtils.startOfDay(ms)
            val cur = firstDayBySuffix[suffix]
            if (cur == null || day < cur) firstDayBySuffix[suffix] = day
        }
        for (s in sms) mark(s.normalizedSuffix.ifBlank { phoneSuffix(s.address) }, s.firstDateMsInScan)
        for (c in calls) mark(phoneSuffix(c.phoneNumber), c.startedAt ?: c.endedAt)
        val createdDays = firstDayBySuffix.values.toList()
        val bars = when (p) {
            StatPeriod.D7 -> {
                val monday = mondayOfWeek(now)
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
                val thisWeekMon = mondayOfWeek(now)
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
    val greeting: String = "이번 달, 잘 하고 계세요",
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

/**
 * 「내 기록」 한 덩어리. 번호가 주인공이고, **못 찾은 주소도 숨기지 않는다.**
 *   (테스트폰 실측 2026-09-24: 9월 7곳 중 2곳이 주소 미등록이었다. 숫자를 부풀리면 기록이 아니다.)
 */
data class MyRecordState(
    /** 지금까지 준 가장 큰 현장 번호. 0 = 아직 없음(= 방금 깐 사람). */
    val lastNo: Int = 0,
    val monthSites: Int = 0,
    val towns: List<String> = emptyList(),
    /** 이번 달 다녀온 곳 중 **주소를 못 찾은** 곳 수. 지도·동네 수에 안 들어간다. */
    val noAddrCount: Int = 0,
    /**
     * 다녀왔는데 **완료를 안 누른** 곳 수. 번호가 안 붙어 기록이 빈다.
     *   (2026-09-24 폰 실측: 9월 7곳 중 3곳이 그랬다)
     */
    val notDoneCount: Int = 0,
    /** 카톡·밴드·당근에 그대로 붙이는 글. 빈 문자열 = 아직 완료한 현장이 없음. */
    val pasteText: String = ""
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

/** 전화번호 끝 8자리 = 같은 사람 판정 키 (SMS 캐시 normalizedSuffix 와 동일 규칙). */
private fun phoneSuffix(phone: String): String {
    val d = phone.filter { it.isDigit() }
    return if (d.length >= 8) d.takeLast(8) else d
}

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
        else -> "${mo}월, 잘 하고 계세요"
    }
}
