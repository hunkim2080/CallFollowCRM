package com.detailline.callfollowcrm.presentation.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import java.util.Calendar

/**
 * 시공 예약 화면 — 캘린더 그리드 기반 (2026-05-24 사장님 요청, 갤메 캘린더 패턴 벤치마킹).
 *
 * 구조:
 *   1. TopAppBar — 뒤로 + "시공 예약 · N건"
 *   2. 월 헤더 — [◀ YYYY년 M월 ▶] (탭으로 월 이동)
 *   3. 요일 헤더 — 일 월 화 수 목 금 토 (일=빨강, 토=파랑)
 *   4. 캘린더 그리드 — 7열 × 6행. 시공 있는 날 = 작은 점, 오늘 = soft 배경, 선택 = 채워진 원
 *   5. 선택된 날의 시공 카드 (없으면 안내)
 *
 * 과거 + 미래 시공 모두 캘린더로 표현 — 사장님 의도: "그동안 한 시공도, 앞으로 할 시공도 한 화면".
 *   기존 "지난 예약 펼치기" 섹션은 제거 (캘린더 ◀ 로 충분).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onAddSchedule: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val cardSummaries by viewModel.cardSummariesByPhoneSuffix.collectAsState()
    val nowMs = remember { System.currentTimeMillis() }
    val todayStart = remember(nowMs) { DateTimeUtils.startOfDay(nowMs) }

    // 보고 있는 달의 anchor (그 달 1일 startOfDay). 화살표로 이동.
    var viewedMonthAnchor by remember {
        mutableLongStateOf(monthAnchor(nowMs))
    }
    // 선택된 날 (null = 오늘). 셀 탭으로 변경.
    var selectedDayMs by remember { mutableStateOf<Long?>(todayStart) }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            // 프로토 일정 앱바 — "일정" + 오늘 날짜. 하단 탭이므로 back 화살표 제거.
            val todayLabel = remember {
                java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREAN).format(java.util.Date())
            }
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text("일정", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.6).sp)
                        Text(
                            "$todayLabel · 예정 ${state.upcomingCount}건",
                            fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onAddSchedule,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("일정 등록", fontWeight = FontWeight.SemiBold) },
                containerColor = TossBlue,
                contentColor = androidx.compose.ui.graphics.Color.White
            )
        }
    ) { inner ->
        // 월별 셀 6×7 + 시공 카운트 매핑. 매 월 이동시 재계산.
        val cells = remember(viewedMonthAnchor, state.all) {
            buildCalendarCells(viewedMonthAnchor, state.all, todayStart)
        }
        // 선택된 날의 시공 목록 — 여러 날 시공(scheduledWorkDays)은 기간 내 모든 날에 표시.
        val schedulesForSelected = remember(selectedDayMs, state.all) {
            val day = selectedDayMs ?: return@remember emptyList<CustomerEntity>()
            state.all.filter { jobCoversDay(it, day) }
                .sortedBy { it.scheduledWorkMinutes ?: Int.MAX_VALUE }
        }

        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 캘린더 영역 (월 헤더 + 요일 + 6주 그리드) — 한 item 으로 묶어 가로 swipe 적용.
            //   사장님 요청 (2026-05-24): "캘린더에서 스와이프하면 월이 넘어갔으면".
            //   pointerInput(detectHorizontalDragGestures) — LazyColumn 의 세로 스크롤과 충돌 없음.
            item(key = "calendar-block") {
                Column(
                    modifier = Modifier.pointerInput(Unit) {
                        var accumulatedX = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = { accumulatedX = 0f },
                            onDragCancel = { accumulatedX = 0f }
                        ) { _, dragAmount ->
                            accumulatedX += dragAmount
                            val threshold = 80f  // 손가락 ~20dp 이상 끌면 트리거
                            if (accumulatedX > threshold) {
                                viewedMonthAnchor = shiftMonth(viewedMonthAnchor, -1)
                                accumulatedX = 0f
                            } else if (accumulatedX < -threshold) {
                                viewedMonthAnchor = shiftMonth(viewedMonthAnchor, +1)
                                accumulatedX = 0f
                            }
                        }
                    }
                ) {
                    MonthHeader(
                        anchorMs = viewedMonthAnchor,
                        onPrev = { viewedMonthAnchor = shiftMonth(viewedMonthAnchor, -1) },
                        onNext = { viewedMonthAnchor = shiftMonth(viewedMonthAnchor, +1) },
                        onTapToday = {
                            viewedMonthAnchor = monthAnchor(System.currentTimeMillis())
                            selectedDayMs = todayStart
                        }
                    )
                    DowHeader()
                    repeat(6) { week ->
                        CalendarWeekRow(
                            cells = cells.subList(week * 7, week * 7 + 7),
                            selectedDayMs = selectedDayMs,
                            onSelect = { dayMs -> selectedDayMs = dayMs }
                        )
                    }
                }
            }
            // 4) 선택된 날 헤더 + 시공 카드
            item(key = "selected-header") {
                SelectedDayHeader(
                    dayMs = selectedDayMs,
                    count = schedulesForSelected.size,
                    isToday = selectedDayMs == todayStart
                )
            }
            if (schedulesForSelected.isEmpty()) {
                item(key = "no-schedules") {
                    EmptyDayMessage(
                        dayMs = selectedDayMs,
                        todayStart = todayStart,
                        hasAnyScheduled = state.all.isNotEmpty()
                    )
                }
            } else {
                items(schedulesForSelected, key = { "c-${it.id}" }) { c ->
                    val suffix = c.phoneNumber.filter { ch -> ch.isDigit() }.takeLast(8)
                    ScheduleCustomerCard(
                        customer = c,
                        cardSummary = cardSummaries[suffix],
                        onClick = { onOpenCustomer(c.id) }
                    )
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun MonthHeader(
    anchorMs: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTapToday: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, "이전 달", tint = TossTextSecondary)
        }
        Text(
            DateTimeUtils.formatMonthHeader(anchorMs),
            modifier = Modifier
                .weight(1f)
                .clickable { onTapToday() },
            style = MaterialTheme.typography.titleLarge,
            color = TossTextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, "다음 달", tint = TossTextSecondary)
        }
    }
}

@Composable
private fun DowHeader() {
    val labels = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { idx, label ->
            val color = when (idx) {
                0 -> TossError
                6 -> TossBlue
                else -> TossTextSecondary
            }
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalendarWeekRow(
    cells: List<CalendarCell>,
    selectedDayMs: Long?,
    onSelect: (Long) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            CalendarDay(
                cell = cell,
                isSelected = selectedDayMs == cell.dayStartMs,
                onClick = { onSelect(cell.dayStartMs) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CalendarDay(
    cell: CalendarCell,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isSelected -> TossBlue
        cell.isToday -> TossBlueSoft
        else -> Color.Transparent
    }
    val fgColor = when {
        isSelected -> Color.White
        !cell.isCurrentMonth -> TossTextTertiary
        cell.dayOfWeek == Calendar.SUNDAY -> TossError
        cell.dayOfWeek == Calendar.SATURDAY -> TossBlue
        else -> TossTextPrimary
    }
    val dotColor = when {
        isSelected -> Color.White
        cell.hasPastSchedule && !cell.hasUpcomingSchedule -> TossTextTertiary // 지난 시공만
        else -> TossSuccess // 다가올 시공 (또는 혼합)
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                cell.dayOfMonth.toString(),
                color = fgColor,
                fontSize = 14.sp,
                fontWeight = if (cell.isToday || isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (cell.scheduleCount > 0) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

@Composable
private fun SelectedDayHeader(dayMs: Long?, count: Int, isToday: Boolean) {
    val label = if (dayMs == null) "날짜를 선택하세요"
    else DateTimeUtils.formatKoreanDate(dayMs) + (if (isToday) " · 오늘" else "")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = TossTextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(TossBlueSoft)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "${count}건",
                    style = MaterialTheme.typography.labelMedium,
                    color = TossBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EmptyDayMessage(dayMs: Long?, todayStart: Long, hasAnyScheduled: Boolean) {
    val msg = when {
        dayMs == null -> "위 캘린더에서 날짜를 탭하세요"
        dayMs < todayStart -> "이 날 시공 없었어요"
        dayMs == todayStart -> "오늘 시공 없음 — 여유 있는 하루"
        else -> "이 날 비어있어요 — 새 예약 가능"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🗓", fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = TossTextTertiary)
            if (!hasAnyScheduled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "고객 상세 → 일정 → \"시공 예약일 설정\" 또는 ChatScreen → ✨ AI 제안 의 [시공일 등록]",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun ScheduleCustomerCard(
    customer: CustomerEntity,
    cardSummary: String?,
    onClick: () -> Unit
) {
    val scheduled = customer.scheduledWorkDate ?: return
    val isPast = scheduled < DateTimeUtils.startOfDay(System.currentTimeMillis())
    TossCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    customer.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(customer.phoneNumber),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPast) TossTextSecondary else TossTextPrimary,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    DateTimeUtils.dDayLabel(scheduled),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPast) TossTextTertiary else TossBlue,
                    fontWeight = FontWeight.Bold
                )
            }
            // 시공 시간 + 기간 (DB v24). 시간 미정이면 생략, 당일이면 기간 생략.
            val timeLabel = customer.scheduledWorkMinutes?.let { DateTimeUtils.formatWorkMinutes(it) }
            val days = customer.scheduledWorkDays.coerceAtLeast(1)
            if (timeLabel != null || days > 1) {
                Spacer(Modifier.height(4.dp))
                val rangeLabel = if (days > 1) {
                    val endMs = DateTimeUtils.startOfDay(scheduled) + (days - 1) * DateTimeUtils.DAY_MS
                    "${DateTimeUtils.formatDateOnly(scheduled)}~${DateTimeUtils.formatDateOnly(endMs)} · ${days}일간"
                } else null
                Text(
                    listOfNotNull(timeLabel?.let { "🕐 $it" }, rangeLabel).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPast) TossTextTertiary else TossTextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
            // ✨ AI 한 줄 요약 — 사장님 요청 (2026-05-24): "어떤 내용으로 예약 확정인지 간략하게".
            //   HomeScreen 카드 요약과 같은 데이터. 서버 응답 없으면 silent 숨김.
            if (!cardSummary.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "✨ $cardSummary",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPast) TossTextTertiary else TossBlue,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                PhoneNumberFormatter.format(customer.phoneNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = TossTextSecondary
            )
            if (customer.memo.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    customer.memo.lineSequence().firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary,
                    maxLines = 1
                )
            }
            // 2026-05-25: status 라인 제거 — CustomerStatus enum 폐기. 카테고리는 별도 표시 예정.
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 캘린더 데이터 모델 + 빌더
// ─────────────────────────────────────────────────────────────

private data class CalendarCell(
    val dayStartMs: Long,
    val dayOfMonth: Int,
    val dayOfWeek: Int, // Calendar.SUNDAY..SATURDAY
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val scheduleCount: Int,
    val hasPastSchedule: Boolean,
    val hasUpcomingSchedule: Boolean
)

/**
 * 이 시공이 dayStart 날을 포함하는가 — 여러 날 시공(scheduledWorkDays) 고려.
 *   기간 = [시공일, 시공일 + (days-1)일]. days 기본 1 = 당일만.
 */
private fun jobCoversDay(c: CustomerEntity, dayStart: Long): Boolean {
    val start = c.scheduledWorkDate ?: return false
    val s = DateTimeUtils.startOfDay(start)
    val days = c.scheduledWorkDays.coerceAtLeast(1)
    val end = s + (days - 1) * DateTimeUtils.DAY_MS
    return dayStart in s..end
}

/** 어떤 ms 가 들어와도 그 달 1일의 startOfDay 로 정규화. */
private fun monthAnchor(anyMs: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = anyMs
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun shiftMonth(anchorMs: Long, delta: Int): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = anchorMs
        add(Calendar.MONTH, delta)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return cal.timeInMillis
}

/**
 * 보고 있는 달 anchor 기준 7×6=42 셀 생성.
 * 첫 셀 = 1일이 속한 주의 일요일. 마지막 셀 = 그로부터 +41일.
 * 다음 달 며칠 포함될 수 있음 → isCurrentMonth=false 로 회색 표시.
 */
private fun buildCalendarCells(
    monthAnchor: Long,
    schedules: List<CustomerEntity>,
    todayStart: Long
): List<CalendarCell> {
    val cal = Calendar.getInstance().apply { timeInMillis = monthAnchor }
    val targetMonth = cal.get(Calendar.MONTH)
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) // 1=SUN..7=SAT
    cal.add(Calendar.DAY_OF_MONTH, -(firstDow - 1)) // 그 주 일요일로

    val cells = ArrayList<CalendarCell>(42)
    repeat(42) {
        val dayStart = DateTimeUtils.startOfDay(cal.timeInMillis)
        // 여러 날 시공은 기간 내 모든 날에 점 표시 (scheduledWorkDays).
        val daySchedules = schedules.filter { jobCoversDay(it, dayStart) }
        val hasPast = daySchedules.isNotEmpty() && dayStart < todayStart
        val hasUp = daySchedules.isNotEmpty() && dayStart >= todayStart
        cells += CalendarCell(
            dayStartMs = dayStart,
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
            isCurrentMonth = cal.get(Calendar.MONTH) == targetMonth,
            isToday = dayStart == todayStart,
            scheduleCount = daySchedules.size,
            hasPastSchedule = hasPast,
            hasUpcomingSchedule = hasUp
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return cells
}
