package com.detailline.callfollowcrm.presentation.screen.schedule

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.input.KeyboardType
import com.detailline.callfollowcrm.presentation.component.SheetFieldLabel
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.launch
import java.util.Calendar

// 월 전환 Pager 풀 — 가운데(기준달) ± 1200달(±100년). 충분히 넓어 끝에 닿을 일 없음.
private const val SCHEDULE_PAGER_CENTER = 1200
private const val SCHEDULE_PAGER_COUNT = 2401

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
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onAddSchedule: () -> Unit = {},
    onOpenSettle: () -> Unit = {},
    /** 진입 시 미리 선택할 날(ms). 홈 "다음 시공" 카드에서 그 시공일로. null/<=0 = 오늘. */
    initialSelectedDayMs: Long? = null
) {
    val state by viewModel.state.collectAsState()
    val cardSummaries by viewModel.cardSummariesByPhoneSuffix.collectAsState()
    val nowMs = remember { System.currentTimeMillis() }
    val todayStart = remember(nowMs) { DateTimeUtils.startOfDay(nowMs) }
    // 홈 "다음 시공" 카드로 들어온 경우 그 시공일을 시작 선택값으로(자정 정규화). 아니면 오늘.
    val initialDay = remember(initialSelectedDayMs) {
        initialSelectedDayMs?.takeIf { it > 0L }?.let { DateTimeUtils.startOfDay(it) } ?: todayStart
    }

    // 월 전환 = HorizontalPager (손가락을 1:1 로 따라오고 놓으면 한 달 스냅). 2026-06-07 사장님 통점3:
    //   "터치하면 넘어갈게~ 가 아니라 손 따라오는 느낌" → detectDrag+AnimatedContent 폐기하고 Pager 로 교체.
    //   기준달(baseAnchor)을 가운데 페이지(INITIAL_PAGE)에 두고, page-INITIAL_PAGE 만큼 달을 민다.
    val pagerScope = androidx.compose.runtime.rememberCoroutineScope()
    val baseAnchor = remember(initialDay) { monthAnchor(initialDay) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = SCHEDULE_PAGER_CENTER
    ) { SCHEDULE_PAGER_COUNT }
    // 보고 있는 달 anchor (정착 페이지 기준) — 헤더/선택 로직이 참조. pagerState 가 rememberSaveable 라 복귀해도 유지.
    val viewedMonthAnchor by remember {
        androidx.compose.runtime.derivedStateOf { shiftMonth(baseAnchor, pagerState.currentPage - SCHEDULE_PAGER_CENTER) }
    }
    // 선택된 날 (null = 오늘). 셀 탭으로 변경.
    //   rememberSaveable: 고객정보 갔다 오면 선택 날짜가 "오늘"로 풀리던 버그 fix(2026-06-04 사장님 보고).
    var selectedDayMs by rememberSaveable { mutableStateOf<Long?>(initialDay) }

    // 팀원 현장 배정 (2026-06-05) — 팀원 있을 때만 일정 카드에 배정 줄 노출.
    val teamMembers by viewModel.teamMembers.collectAsState()
    val assignmentsByCustomer by viewModel.assignmentsByCustomer.collectAsState()
    val assignToast by viewModel.toast.collectAsState()
    val assignCtx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(assignToast) {
        assignToast?.let {
            android.widget.Toast.makeText(assignCtx, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }
    var assignTarget by remember { mutableStateOf<CustomerEntity?>(null) }
    // 배정 시트 열렸을 때 뒤로가기 = 시트 닫기 (앱 종료/화면 이탈 방지).
    androidx.activity.compose.BackHandler(enabled = assignTarget != null) { assignTarget = null }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            // 프로토 일정 앱바 — "일정" + 오늘 날짜 + 우측 [+] (openAddSchedule). 프로토엔 FAB 없음.
            val todayLabel = remember {
                java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREAN).format(java.util.Date())
            }
            TopAppBar(
                windowInsets = androidx.compose.foundation.layout.WindowInsets(top = 12.dp),
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text("일정", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.6).sp)
                        Text(
                            todayLabel,
                            fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color.White)
                            .clickable { onAddSchedule() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, "일정 등록", tint = TossBlue, modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        // 월별 셀은 AnimatedContent 안에서 anchor 별로 계산(월 전환 슬라이드용).
        // 선택된 날의 시공 목록 — 여러 날 시공(scheduledWorkDays)은 기간 내 모든 날에 표시.
        val schedulesForSelected = remember(selectedDayMs, state.all) {
            val day = selectedDayMs ?: return@remember emptyList<CustomerEntity>()
            state.all.filter { jobCoversDay(it, day) }
                .sortedBy { it.scheduledWorkMinutes ?: Int.MAX_VALUE }
        }

        // 홈 "다음 시공" 카드로 들어오면(initialSelectedDayMs) 달력 아래 그 날 시공 카드까지 자동 스크롤.
        //   2026-06-07 사장님: "다음시공 누르면 그 날 카드가 바로 보이게(스크롤 내린 상태)".
        val scheduleListState = androidx.compose.foundation.lazy.rememberLazyListState()
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (initialSelectedDayMs != null && initialSelectedDayMs > 0L) {
                kotlinx.coroutines.delay(280)  // 첫 레이아웃(달력) 그려진 뒤
                runCatching { scheduleListState.animateScrollToItem(1) }  // index1 = 그 날 라벨+카드
            }
        }

        LazyColumn(
            state = scheduleListState,
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
                Column {
                    MonthHeader(
                        anchorMs = viewedMonthAnchor,
                        onPrev = { pagerScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        onNext = { pagerScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        onTapToday = {
                            selectedDayMs = todayStart
                            val target = SCHEDULE_PAGER_CENTER + monthsBetween(baseAnchor, monthAnchor(System.currentTimeMillis()))
                            pagerScope.launch { pagerState.animateScrollToPage(target) }
                        }
                    )
                    // cal-card — 흰 카드 안에 요일 헤더 + 6주 그리드 (프로토 .cal-card)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 16.dp)
                    ) {
                        DowHeader()
                        // 월 그리드 = HorizontalPager. 손가락을 1:1 로 따라오고 놓으면 한 달씩 스냅(2026-06-07).
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pagerState,
                            verticalAlignment = Alignment.Top
                        ) { page ->
                            val anchor = shiftMonth(baseAnchor, page - SCHEDULE_PAGER_CENTER)
                            val monthCells = buildCalendarCells(anchor, state.all, todayStart)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                repeat(6) { week ->
                                    CalendarWeekRow(
                                        cells = monthCells.subList(week * 7, week * 7 + 7),
                                        selectedDayMs = selectedDayMs,
                                        onSelect = { dayMs -> selectedDayMs = dayMs },
                                        onLongSelect = { dayMs -> selectedDayMs = dayMs; onAddSchedule() }
                                    )
                                }
                            }
                        }
                    }
                    // cal-hint
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("📌 날짜를 ", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TossTextTertiary)
                        Text("길게 누르면", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                        Text(" 그 날 일정을 바로 등록해요", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TossTextTertiary)
                    }
                }
            }
            // 4) 선택된 날 라벨 + 시공 카드 (프로토 cal-day-label + cal-day-jobs)
            item(key = "day-label") {
                DayLabel(dayMs = selectedDayMs, isToday = selectedDayMs == todayStart)
            }
            if (schedulesForSelected.isEmpty()) {
                item(key = "no-schedules") { DayEmpty(onAdd = onAddSchedule) }
            } else {
                if (schedulesForSelected.size > 1) {
                    item(key = "day-count") { DayCount(schedulesForSelected.size) }
                }
                items(schedulesForSelected, key = { "c-${it.id}" }) { c ->
                    val suffix = c.phoneNumber.filter { ch -> ch.isDigit() }.takeLast(8)
                    DayJobCard(
                        customer = c,
                        cardSummary = cardSummaries[suffix],
                        selectedDayMs = selectedDayMs,
                        todayStart = todayStart,
                        assignedMembers = assignmentsByCustomer[c.id].orEmpty(),
                        teamAvailable = teamMembers.isNotEmpty(),
                        onAssign = { assignTarget = c },
                        onClick = { onOpenCustomer(c.id) },
                        onEdit = { onOpenCustomer(c.id) },
                        onOpenSettle = onOpenSettle
                    )
                }
                item(key = "day-add") { DayAddButton("이 날 일정 더 추가", onAddSchedule) }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    // 팀원 현장 배정 시트 — 프로토 openAssign(팀원 칩 토글).
    assignTarget?.let { c ->
        val rows = assignmentsByCustomer[c.id].orEmpty()
        val assignedIds = rows.map { it.memberId }.toSet()
        val existingMemo = rows.firstNotNullOfOrNull { it.teamMemo }.orEmpty()
        AssignTeamSheet(
            customerName = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber),
            members = teamMembers,
            initiallySelected = assignedIds,
            initialMemo = existingMemo,
            onDismiss = { assignTarget = null },
            onSave = { selectedIds, memo ->
                val dayStart = DateTimeUtils.startOfDay(c.scheduledWorkDate ?: System.currentTimeMillis())
                viewModel.assignTeam(c, dayStart, selectedIds.toList(), memo)
                assignTarget = null
            }
        )
    }
}

@Composable
private fun MonthHeader(
    anchorMs: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTapToday: () -> Unit
) {
    // cal-head — 가운데 정렬 + 원형 흰 nav 버튼 (프로토 .cal-head)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CalNav(Icons.Default.ChevronLeft, "이전 달", onPrev)
        Text(
            DateTimeUtils.formatMonthHeader(anchorMs),
            modifier = Modifier.padding(horizontal = 20.dp).clickable { onTapToday() },
            fontSize = 18.sp,
            color = TossTextPrimary,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.36).sp
        )
        CalNav(Icons.Default.ChevronRight, "다음 달", onNext)
    }
}

@Composable
private fun CalNav(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, cd, tint = TossTextSecondary, modifier = Modifier.size(18.dp))
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
    onSelect: (Long) -> Unit,
    onLongSelect: (Long) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            CalendarDay(
                cell = cell,
                isSelected = selectedDayMs == cell.dayStartMs,
                onClick = { onSelect(cell.dayStartMs) },
                onLongClick = { onLongSelect(cell.dayStartMs) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CalendarDay(
    cell: CalendarCell,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
private fun DayLabel(dayMs: Long?, isToday: Boolean) {
    // 프로토 cal-day-label "5월 29일 (금) · 오늘" (연도 없음)
    val label = if (dayMs == null) "날짜를 선택하세요"
    else koreanMonthDay(dayMs) + (if (isToday) " · 오늘" else "")
    Text(
        label,
        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
        modifier = Modifier.padding(start = 2.dp, top = 20.dp, bottom = 11.dp)
    )
}

@Composable
private fun DayCount(count: Int) {
    Text(
        "이 날 시공 ${count}곳",
        fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
        modifier = Modifier.padding(start = 2.dp, bottom = 11.dp)
    )
}

@Composable
private fun DayEmpty(onAdd: () -> Unit) {
    // 프로토 day-empty
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("이 날은 시공 예약이 없어요", fontSize = 13.sp, color = TossTextTertiary)
        Spacer(Modifier.height(14.dp))
        DayAddButton("이 날 일정 등록", onAdd)
    }
}

@Composable
private fun DayAddButton(label: String, onClick: () -> Unit) {
    // 프로토 .day-add — blue-tint 칩
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(TossBlueSoft)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, null, tint = TossBlueDark, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = TossBlueDark)
    }
}

/**
 * 프로토 day-job 카드 — hd 점 + 이름 + N일차 + 시간 + D-day/완료 태그 + 수정,
 *   📍 주소, 입금 상태(읽기), "정산·현금흐름에서 보기", 팀원 배정 줄(팀원 있을 때).
 */
@Composable
private fun DayJobCard(
    customer: CustomerEntity,
    cardSummary: String?,
    selectedDayMs: Long?,
    todayStart: Long,
    assignedMembers: List<com.detailline.callfollowcrm.data.local.entity.TeamAssignmentEntity> = emptyList(),
    teamAvailable: Boolean = false,
    onAssign: () -> Unit = {},
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onOpenSettle: () -> Unit
) {
    val scheduled = customer.scheduledWorkDate ?: return
    val s = DateTimeUtils.startOfDay(scheduled)
    val isPast = s < todayStart
    val totalDays = customer.scheduledWorkDays.coerceAtLeast(1)
    val dayN = selectedDayMs?.let { ((it - s) / DateTimeUtils.DAY_MS).toInt() + 1 }?.coerceIn(1, totalDays) ?: 1
    val row = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(customer)
    val hasMoney = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.hasMoney(customer)

    TossCard(onClick = onClick) {
        Column {
            // 1행: hd 점 + 이름 + N일차 + 시간 + 태그 + 수정
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(9.dp).clip(CircleShape)
                        .background(if (isPast) Color(0xFFC2C9D2) else TossError)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    customer.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(customer.phoneNumber),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (isPast) TossTextSecondary else TossTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (totalDays > 1) {
                    Box(
                        Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xFFF1ECFF)).padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("${totalDays}일 중 ${dayN}일차", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7C5CFC))
                    }
                    Spacer(Modifier.width(7.dp))
                }
                customer.scheduledWorkMinutes?.let {
                    Text(DateTimeUtils.formatWorkMinutes(it), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary,
                        modifier = Modifier.padding(end = 8.dp))
                }
                // 태그 (완료 / D-day)
                val tagText = if (isPast) "완료" else DateTimeUtils.dDayLabel(scheduled)
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (isPast) TossGrayBg else Color(0xFFE5F8EE))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(tagText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (isPast) TossTextTertiary else Color(0xFF0E9F56))
                }
                Box(
                    Modifier.padding(start = 7.dp).size(30.dp).clip(RoundedCornerShape(9.dp)).clickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Edit, "수정", tint = TossTextTertiary, modifier = Modifier.size(16.dp)) }
            }
            // 📍 주소
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = TossTextTertiary, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    com.detailline.callfollowcrm.util.AddressExtractor.tidyAddress(customer.address).takeIf { it.isNotBlank() } ?: "주소 미입력",
                    fontSize = 13.sp, color = TossTextSecondary, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            // ✨ AI 요약
            if (!cardSummary.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("✨ $cardSummary", fontSize = 13.sp, color = if (isPast) TossTextTertiary else TossBlue, fontWeight = FontWeight.Medium, maxLines = 2)
            }
            // 입금 상태 (읽기 전용)
            if (hasMoney) {
                Spacer(Modifier.height(10.dp))
                PayStatusReadOnly(row)
            }
            // 정산·현금흐름에서 보기
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onOpenSettle() }.padding(top = 10.dp)
            ) {
                Text("💰", fontSize = 13.sp)
                Spacer(Modifier.width(5.dp))
                Text("정산·현금흐름에서 보기", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = TossBlue.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
            // 프로토 .assign-line — 팀원 현장 배정 (팀원 있을 때만 노출).
            if (teamAvailable) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    if (assignedMembers.isEmpty()) {
                        Text("아직 배정 안 함", fontSize = 13.sp, color = TossTextTertiary, modifier = Modifier.weight(1f))
                        AssignBtn("팀원 배정", filled = true, onClick = onAssign)
                    } else {
                        AssignAvatars(assignedMembers)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            assignedMembers.joinToString(", ") { it.memberName },
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        AssignBtn("변경", filled = false, onClick = onAssign)
                    }
                }
            }
        }
    }
}

/** 프로토 .assign-btn — blue-tint 알약(배정) / 회색 텍스트(변경). */
@Composable
private fun AssignBtn(label: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
        color = if (filled) TossBlue else TossTextTertiary,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(if (filled) Modifier.background(TossBlueSoft) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = if (filled) 13.dp else 8.dp, vertical = 7.dp)
    )
}

/** 프로토 .crew-avs — 겹친 작은 이니셜 아바타. */
@Composable
private fun AssignAvatars(members: List<com.detailline.callfollowcrm.data.local.entity.TeamAssignmentEntity>) {
    Row {
        members.take(4).forEachIndexed { idx, m ->
            val (bg, fg) = ASSIGN_TINTS[((idx) % ASSIGN_TINTS.size)]
            Box(
                Modifier
                    .then(if (idx == 0) Modifier else Modifier.offset(x = (-7 * idx).dp))
                    .size(28.dp).clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
            ) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                    Text(
                        m.memberName.replace(Regex("[\\s()]"), "").take(1).ifBlank { "?" },
                        fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = fg
                    )
                }
            }
        }
    }
}

private val ASSIGN_TINTS = listOf(
    Color(0xFFE6EFFF) to Color(0xFF3182F6),
    Color(0xFFE7F8EE) to Color(0xFF16A765),
    Color(0xFFFDEAEF) to Color(0xFFF0436A),
    Color(0xFFF1ECFE) to Color(0xFF7C5CFC),
    Color(0xFFFEF3E0) to Color(0xFFE0920C),
)

@Composable
private fun PayStatusReadOnly(row: com.detailline.callfollowcrm.domain.settlement.SettleRow) {
    fun manwon(won: Long) = (won / 10_000L).toInt()
    val total = "총 %,d만원".format(manwon(row.total))
    Column {
        Text(total, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
        Spacer(Modifier.height(3.dp))
        val hasDeposit = row.depositAmount > 0L
        val (plain, emphasis, emColor) = when {
            row.isPaidOff -> Triple("", "전액 완납 ✓", TossSuccess)
            hasDeposit && !row.depositPaid -> Triple("계약금 ${manwon(row.depositAmount)}만 · 잔금 ${manwon(row.balanceAmount)}만 ", "미수", TossError)
            hasDeposit -> Triple("계약금 ${manwon(row.depositAmount)}만 받음 · ", "잔금 ${manwon(row.balanceAmount)}만 남음", TossError)
            else -> Triple("계약금 없음 · ", "전액 ${manwon(row.total)}만 미수", TossError)
        }
        Row {
            if (plain.isNotEmpty()) Text(plain, fontSize = 12.5.sp, color = TossTextTertiary)
            Text(emphasis, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = emColor)
        }
    }
}

/** "5월 29일 (금)" — 프로토 cal-day-label 포맷 (연도 없음). */
private fun koreanMonthDay(ms: Long): String =
    java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREAN).format(java.util.Date(ms))

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

/** fromAnchor → toAnchor 사이의 달 수(부호 있음). page 인덱스 계산용. */
private fun monthsBetween(fromAnchor: Long, toAnchor: Long): Int {
    val a = Calendar.getInstance().apply { timeInMillis = fromAnchor }
    val b = Calendar.getInstance().apply { timeInMillis = toAnchor }
    return (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 12 + (b.get(Calendar.MONTH) - a.get(Calendar.MONTH))
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

/**
 * 팀원 현장 배정 시트 — 프로토 openAssign/renderAssign 1:1 (팀원 칩 토글).
 *   저장 시 ScheduleViewModel.assignTeam → 로컬 기록 + 서버 schedule-snapshot push.
 */
/**
 * 현장 배정 시트 — 팀원 칩 토글 + 직원 전달 메모.
 *   ModalBottomSheet(별도 윈도우)는 갤S9/안드10 에서 키보드가 입력칸을 가림(reference_modalbottomsheet_keyboard).
 *   메모 입력칸이 생겼으므로 액티비티 윈도우 안 인라인 오버레이로 그림(adjustResize → 키보드 뜨면 카드가 위로).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AssignTeamSheet(
    customerName: String,
    members: List<com.detailline.callfollowcrm.ai.TeamRepository.TeamMember>,
    initiallySelected: Set<String>,
    initialMemo: String,
    onDismiss: () -> Unit,
    onSave: (Set<String>, String) -> Unit
) {
    var selected by remember { mutableStateOf(initiallySelected) }
    var memo by remember { mutableStateOf(initialMemo) }
    val noRipple = remember { MutableInteractionSource() }

    // 스크림(탭 시 닫힘) + 하단 정렬 카드.
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = noRipple, indication = null) { onDismiss() }
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Color.White)
                .clickable(interactionSource = noRipple, indication = null) { /* 카드 탭은 닫지 않음 */ }
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp)
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // grip
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
                    .width(38.dp).height(4.dp).clip(RoundedCornerShape(999.dp)).background(TossDivider)
            )
            Text("현장 배정", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("${customerName} 현장에 보낼 팀원을 고르세요. 배정하면 팀원 화면에 일정·주소가 떠요.",
                fontSize = 13.sp, color = TossTextTertiary, lineHeight = 19.sp)
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                members.forEach { m ->
                    val on = selected.contains(m.memberId)
                    val roleLabel = if (m.role == "owner") "대표" else "팀원"
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (on) TossBlue else TossGrayBg)
                            .clickable {
                                selected = if (on) selected - m.memberId else selected + m.memberId
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (on) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(m.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                            color = if (on) Color.White else TossTextPrimary)
                        Spacer(Modifier.width(5.dp))
                        Text(roleLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = if (on) Color.White.copy(alpha = 0.8f) else TossTextTertiary)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            // 직원 전달 메모 — 고객 메모와 별개. 팀원 화면에 '대표님 전달사항'으로 뜸(고객 메모는 안 보임).
            SheetFieldLabel("직원에게 전달 (선택)")
            SheetTextField(
                memo, { memo = it },
                placeholder = "예: 현관 비번 1234# · 사다리차 필요 · 주차는 뒷편",
                singleLine = false, minHeightDp = 64
            )
            Text("고객 메모와 별개예요. 여기 적은 내용만 팀원 화면에 보여요.",
                fontSize = 11.5.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
            Text("· 팀원 칩을 한 번 더 누르면 그 사람만 빠져요.",
                fontSize = 11.5.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 3.dp, start = 2.dp))
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TossBlue)
                    .clickable { onSave(selected, memo) }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (selected.isNotEmpty()) "${selected.size}명 배정 완료" else "완료",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
                )
            }
            // 이미 배정된 현장이면 "배정 전체 해제" 버튼 노출 — 칩 하나씩 끄지 않아도 한 번에 비움.
            //   해제 = 빈 배정 저장 → 서버 snapshot 도 갱신되어 팀원 화면에서도 사라짐.
            if (initiallySelected.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFDEAEF))
                        .clickable { onSave(emptySet(), "") }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("이 현장 배정 전체 해제", color = Color(0xFFF0436A), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
