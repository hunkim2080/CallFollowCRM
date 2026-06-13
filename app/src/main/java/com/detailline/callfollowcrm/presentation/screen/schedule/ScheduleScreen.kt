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
import com.detailline.callfollowcrm.presentation.component.FormattedTextField
import com.detailline.callfollowcrm.presentation.component.SheetFieldLabel
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
    onOpenCollabSites: (String?) -> Unit = {},
    /** 진입 시 미리 선택할 날(ms). 홈 "다음 시공" 카드에서 그 시공일로. null/<=0 = 오늘. */
    initialSelectedDayMs: Long? = null
) {
    val state by viewModel.state.collectAsState()
    val collabDays by viewModel.collabDayStarts.collectAsState()   // 캘린더 협업 보라점 (#7)
    val collabAssign by viewModel.collabAssignByCustomer.collectAsState()   // 협업 사장 배정 → 카드 "🤝 이름"
    val collabSites by viewModel.collabSites.collectAsState()
    val cardSummaries by viewModel.cardSummariesByPhoneSuffix.collectAsState()
    val nowMs = remember { System.currentTimeMillis() }
    val todayStart = remember(nowMs) { DateTimeUtils.startOfDay(nowMs) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadCollab()
    }
    // 홈 "다음 시공" 카드로 들어온 경우 그 시공일을 시작 선택값으로(자정 정규화). 아니면 오늘.
    val initialDay = remember(initialSelectedDayMs) {
        initialSelectedDayMs?.takeIf { it > 0L }?.let { DateTimeUtils.startOfDay(it) } ?: todayStart
    }

    // 월 전환 = HorizontalPager (손가락 1:1 따라옴, 놓으면 한 달 스냅). 2026-06-08 복원.
    //   투명막의 진짜 원인은 pager 가 아니라 LazyColumn 의 initialFirstVisibleItemIndex 였음(제거 완료).
    //   pager 단독(인덱스/자동스크롤 없음)은 fff6166 때 정상 동작 — 안전하게 복원.
    val pagerScope = androidx.compose.runtime.rememberCoroutineScope()
    val baseAnchor = remember(initialDay) { monthAnchor(initialDay) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = SCHEDULE_PAGER_CENTER) { SCHEDULE_PAGER_COUNT }
    val viewedMonthAnchor by remember {
        androidx.compose.runtime.derivedStateOf { shiftMonth(baseAnchor, pagerState.currentPage - SCHEDULE_PAGER_CENTER) }
    }
    // 선택된 날 (null = 오늘). 셀 탭으로 변경.
    //   rememberSaveable: 고객정보 갔다 오면 선택 날짜가 "오늘"로 풀리던 버그 fix(2026-06-04 사장님 보고).
    var selectedDayMs by rememberSaveable { mutableStateOf<Long?>(initialDay) }

    // 팀원 현장 배정 (2026-06-05) — 팀원 있을 때만 일정 카드에 배정 줄 노출.
    val teamMembers by viewModel.teamMembers.collectAsState()
    val collabPartners by viewModel.collabPartners.collectAsState()
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

    // 협업 카드 밀어서 숨김 → "되돌리기" 스낵바 (실수 스와이프 복구용).
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        containerColor = TossGrayBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        val collabForSelected = remember(selectedDayMs, collabSites) {
            val day = selectedDayMs ?: return@remember emptyList()
            collabSites.filter { DateTimeUtils.startOfDay(it.scheduledAtMs) == day }
                .sortedBy { it.timeLabel ?: "" }
        }

        // 2026-06-08 사장님 통점(투명막 진범): LazyColumn 에 initialFirstVisibleItemIndex=1 을 주면 안 됨.
        //   index0(캘린더 블록)이 뷰포트보다 큰 단일 item 인데, 첫 컴포지션 땐 그 아래 데이터가 비어
        //   스크롤 범위가 없어 LazyList 측정이 깨짐 → 세로스크롤·가로스와이프·셀탭이 전부 입력만 먹고
        //   안 움직이는 "투명막"이 됨. state 인자 자체를 빼서 정상본과 동일한 기본(0-index)로 둔다.
        //   그 날 일정은 selectedDayMs = initialDay 로 캘린더 아래에 이미 렌더되므로 자동 스크롤 불필요.
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 캘린더 영역 (월 헤더 + 요일 + 6주 그리드 페이저). HorizontalPager = 손가락 1:1 따라옴.
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
                        // 월 그리드 = HorizontalPager — 손가락 1:1 따라옴, 놓으면 한 달 스냅. (2026-06-08 복원)
                        //   투명막 진범은 LazyColumn initialFirstVisibleItemIndex 였음(제거됨). pager 단독은 안전.
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
                                        collabDays = collabDays,
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
                if (collabForSelected.isEmpty()) {
                    item(key = "no-schedules") { DayEmpty(onAdd = onAddSchedule) }
                }
            } else {
                if (schedulesForSelected.size > 1) {
                    item(key = "day-count") { DayCount(schedulesForSelected.size) }
                }
                items(schedulesForSelected, key = { "c-${it.id}" }) { c ->
                    val suffix = c.phoneNumber.filter { ch -> ch.isDigit() }.takeLast(8)
                    val originalDate = c.scheduledWorkDate ?: 0L
                    CollabSwipeBox(
                        onDelete = {
                            viewModel.unschedule(c)
                            uiScope.launch {
                                val r = snackbarHostState.showSnackbar(
                                    message = "일정에서 뺐어요 (고객·기록은 그대로)",
                                    actionLabel = "되돌리기",
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                                if (r == SnackbarResult.ActionPerformed) viewModel.restoreSchedule(c.id, originalDate)
                            }
                        }
                    ) {
                        DayJobCard(
                            customer = c,
                            cardSummary = cardSummaries[suffix],
                            selectedDayMs = selectedDayMs,
                            todayStart = todayStart,
                            assignedMembers = assignmentsByCustomer[c.id].orEmpty(),
                            collabPartnerNames = collabAssign[c.id].orEmpty().map { it.name },
                            teamAvailable = teamMembers.isNotEmpty() || collabPartners.isNotEmpty(),
                            onAssign = { assignTarget = c },
                            onClick = { onOpenCustomer(c.id) }
                        )
                    }
                }
            }
            if (collabForSelected.isNotEmpty()) {
                item(key = "collab-label") {
                    Text(
                        "이 날 협업 ${collabForSelected.size}곳",
                        fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7C5CFC),
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 11.dp)
                    )
                }
                items(collabForSelected, key = { "sh-${it.shareId}" }) { site ->
                    CollabSwipeBox(
                        onDelete = {
                            viewModel.hideCollab(site.shareId)
                            uiScope.launch {
                                val r = snackbarHostState.showSnackbar(
                                    message = "협업 현장을 숨겼어요",
                                    actionLabel = "되돌리기",
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                                if (r == SnackbarResult.ActionPerformed) viewModel.unhideCollab(site.shareId)
                            }
                        }
                    ) {
                        CollabDayCard(site = site, onClick = { onOpenCollabSites(site.shareId) })
                    }
                }
            }
            // "더 추가"는 이미 일정/협업이 있을 때만. 아무것도 없으면 DayEmpty 의 "이 날 일정 등록"만 노출(중복 방지).
            if (schedulesForSelected.isNotEmpty() || collabForSelected.isNotEmpty()) {
                item(key = "day-add") { DayAddButton("이 날 일정 더 추가", onAddSchedule) }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    // 전문가 배정 시트 — 팀원(칩 토글, 완료에 저장) + 협업 사장님(탭하면 이 현장 협업 요청).
    assignTarget?.let { c ->
        val rows = assignmentsByCustomer[c.id].orEmpty()
        val assignedIds = rows.map { it.memberId }.toSet()
        val existingMemo = rows.firstNotNullOfOrNull { it.teamMemo }.orEmpty()
        val assignCtxLocal = androidx.compose.ui.platform.LocalContext.current
        // 현장 제목 — 주소(지역+아파트/단독) "○○ 현장", 없으면 실제 이름, 전화번호는 안 씀.
        val assignSiteTitle = com.detailline.callfollowcrm.util.AddressExtractor.siteLabel(c.address).takeIf { it.isNotBlank() }?.let { "$it 현장" }
            ?: c.name?.takeIf { it.isNotBlank() && it.count { ch -> ch.isDigit() } < 9 }?.let { "$it 현장" }
            ?: "이 현장"
        AssignTeamSheet(
            siteTitle = assignSiteTitle,
            members = teamMembers,
            collabPartners = collabPartners,
            assignedCollabPhones = collabAssign[c.id].orEmpty().map { it.phone }.toSet(),
            initiallySelected = assignedIds,
            initialMemo = existingMemo,
            defaultStartHour = viewModel.lastCollabStartHour,
            onAddTeamMember = { name, phone -> viewModel.addTeamMember(name, phone) },
            onAddWorker = { name, phone, wage -> viewModel.addCollabPartner(name, phone, wage) },
            onDismiss = { assignTarget = null },
            onSave = { selectedIds, memo ->
                val dayStart = DateTimeUtils.startOfDay(c.scheduledWorkDate ?: System.currentTimeMillis())
                viewModel.assignTeam(c, dayStart, selectedIds.toList(), memo)
                assignTarget = null
            },
            onInviteCollab = { phone, force, memo, wage, hour ->
                viewModel.inviteCollabToSite(c, phone, force, memo, wage, hour) { partner, body ->
                    com.detailline.callfollowcrm.util.SmsIntentHelper.openSmsCompose(assignCtxLocal, partner, body)
                }
            },
            onCancelCollab = { phone -> viewModel.removeCollabAssignment(c.id, phone) }
        )
    }
}

@Composable
private fun CollabDayCard(
    site: com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite,
    onClick: () -> Unit
) {
    TossCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF7C5CFC)))
                Spacer(Modifier.width(10.dp))
                Text(site.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, modifier = Modifier.weight(1f))
                Text(
                    "협업",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF7C5CFC),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1ECFF)).padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(site.ownerName.takeIf { it.isNotBlank() }?.let { "$it 사장님" }, site.timeLabel, site.addr).joinToString(" · "),
                fontSize = 13.sp,
                color = TossTextSecondary,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            site.workSummary?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 13.sp, color = TossTextTertiary, maxLines = 2)
            }
        }
    }
}

/**
 * 협업 카드 우→좌 swipe → 숨김. 빨강 "삭제" affordance.
 *   confirmValueChange=false 로 원위치 복귀(데이터 흐름이 카드를 제거) + 스낵바 "되돌리기" 로 복구.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollabSwipeBox(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.5f }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFDEAEF))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, "삭제", tint = Color(0xFFF0436A), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("삭제", color = Color(0xFFF0436A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        },
        content = { content() }
    )
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
    collabDays: Set<Long>,
    onSelect: (Long) -> Unit,
    onLongSelect: (Long) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            CalendarDay(
                cell = cell,
                isSelected = selectedDayMs == cell.dayStartMs,
                isCollab = cell.dayStartMs in collabDays,
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
    isCollab: Boolean = false,
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
    // 프로토 jbar: 점 대신 일정 1건 = 막대 1줄(lane). 1건/2건/여러날이 한눈에 구분됨. (2026-06-11)
    //   선택칸 막대는 흰색, 지난 시공은 회색, 다가올 시공은 초록, 협업은 보라.
    val schedMaxLane = cell.bars.maxOfOrNull { it.lane } ?: -1
    val collabLane = if (isCollab) schedMaxLane + 1 else -1
    val lastLane = minOf(maxOf(schedMaxLane, collabLane), 2) // 최대 3줄(lane 0~2)
    Box(
        modifier = modifier
            .height(52.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 날짜 숫자 — 선택/오늘만 원형 배경(프로토 .num). 막대는 원 밖, 아래에.
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    cell.dayOfMonth.toString(),
                    color = fgColor,
                    fontSize = 14.sp,
                    fontWeight = if (cell.isToday || isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
            if (lastLane >= 0) {
                Spacer(Modifier.height(3.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                    for (lane in 0..lastLane) {
                        when {
                            // 막대는 날짜 동그라미 '밖(아래)' 흰 배경 위 → 선택돼도 흰색이면 안 보임(사장님 신고).
                            //   선택 여부와 무관하게 색 유지(협업=보라/지난=회색/다가올=초록).
                            lane == collabLane -> CalBar(BarSeg.SINGLE, Color(0xFF7C5CFC))
                            else -> {
                                val bar = cell.bars.firstOrNull { it.lane == lane }
                                if (bar != null) {
                                    val c = if (bar.past) TossTextTertiary else TossSuccess
                                    CalBar(bar.seg, c)
                                } else {
                                    // 빈 lane — 위 칸과 세로 위치를 맞춰 여러날 막대가 가로로 이어지게.
                                    Box(Modifier.fillMaxWidth().height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 캘린더 막대 한 줄 — SINGLE=가운데 16dp 알약, 여러날 START/MID/END=칸 가득(가로로 이어짐). */
@Composable
private fun CalBar(seg: BarSeg, color: Color) {
    val shape = when (seg) {
        BarSeg.SINGLE -> RoundedCornerShape(3.dp)
        BarSeg.START -> RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)
        BarSeg.END -> RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
        BarSeg.MID -> RoundedCornerShape(0.dp)
    }
    if (seg == BarSeg.SINGLE) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(16.dp).height(4.dp).clip(shape).background(color))
        }
    } else {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(shape).background(color))
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
    collabPartnerNames: List<String> = emptyList(),
    teamAvailable: Boolean = false,
    onAssign: () -> Unit = {},
    onClick: () -> Unit
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
                // 연필 아이콘 제거(2026-06-11): 카드 전체 탭 = 연필 탭 = 고객 상세로, 기능 동일해 중복이었음.
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
            // "정산·현금흐름에서 보기" 링크 제거(2026-06-11): 카드 탭하면 고객 상세에 정산이 이미 다 있어 불필요했음.
            // 프로토 .assign-line — 전문가 배정. 항상 노출(팀원·일당사장 0명이어도) → 시트의 "+추가"로 바로 등록. (2026-06-14 사장님)
            run {
                @Suppress("UNUSED_EXPRESSION") teamAvailable  // (게이팅 제거 — 빈 상태에서도 배정/추가 진입 가능해야 함)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    if (assignedMembers.isEmpty() && collabPartnerNames.isEmpty()) {
                        Text("아직 배정 안 함", fontSize = 13.sp, color = TossTextTertiary, modifier = Modifier.weight(1f))
                        AssignBtn("전문가 배정", filled = true, onClick = onAssign)
                    } else {
                        if (assignedMembers.isNotEmpty()) {
                            AssignAvatars(assignedMembers)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            (assignedMembers.map { it.memberName } + collabPartnerNames.map { "🤝 $it" }).joinToString(", "),
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

/** 캘린더 막대 한 칸 — 일정 1건 = 막대 1줄(lane). 여러 날 시공은 START/MID/END 로 이어 그림. (프로토 jbar) */
private enum class BarSeg { SINGLE, START, MID, END }
private data class DayBar(val lane: Int, val seg: BarSeg, val past: Boolean)

private data class CalendarCell(
    val dayStartMs: Long,
    val dayOfMonth: Int,
    val dayOfWeek: Int, // Calendar.SUNDAY..SATURDAY
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val scheduleCount: Int,
    val hasPastSchedule: Boolean,
    val hasUpcomingSchedule: Boolean,
    val bars: List<DayBar> = emptyList()
)

/**
 * 한 달치 시공들에 lane(세로 칸) 배정 — 같은 시공은 며칠짜리든 매일 같은 lane 에 와야 막대가 가로로 이어진다.
 *   그리디 구간 패킹: 시작일 빠른 순 → 가장 위쪽 빈 lane(이전 시공 끝난 lane)에 배치. customerId→lane.
 */
private fun assignScheduleLanes(schedules: List<CustomerEntity>): Map<Long, Int> {
    val intervals = schedules.mapNotNull { c ->
        val s = c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } ?: return@mapNotNull null
        val days = c.scheduledWorkDays.coerceAtLeast(1)
        Triple(c.id, s, s + (days - 1) * DateTimeUtils.DAY_MS)
    }.sortedWith(compareBy({ it.second }, { -(it.third - it.second) }))
    val laneEnds = ArrayList<Long>() // lane -> 그 lane 에 마지막으로 들어간 시공의 끝 ms
    val map = HashMap<Long, Int>()
    for ((id, s, e) in intervals) {
        var lane = laneEnds.indexOfFirst { it < s }
        if (lane < 0) { laneEnds.add(e); lane = laneEnds.size - 1 } else laneEnds[lane] = e
        map[id] = lane
    }
    return map
}

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

    val laneMap = assignScheduleLanes(schedules)
    val cells = ArrayList<CalendarCell>(42)
    repeat(42) {
        val dayStart = DateTimeUtils.startOfDay(cal.timeInMillis)
        // 여러 날 시공은 기간 내 모든 날에 막대 표시 (scheduledWorkDays).
        val daySchedules = schedules.filter { jobCoversDay(it, dayStart) }
        val hasPast = daySchedules.isNotEmpty() && dayStart < todayStart
        val hasUp = daySchedules.isNotEmpty() && dayStart >= todayStart
        val bars = daySchedules.mapNotNull { c ->
            val s = c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } ?: return@mapNotNull null
            val e = s + (c.scheduledWorkDays.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS
            val seg = when {
                s == e -> BarSeg.SINGLE
                dayStart == s -> BarSeg.START
                dayStart == e -> BarSeg.END
                else -> BarSeg.MID
            }
            DayBar(lane = laneMap[c.id] ?: 0, seg = seg, past = dayStart < todayStart)
        }.sortedBy { it.lane }
        cells += CalendarCell(
            dayStartMs = dayStart,
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
            isCurrentMonth = cal.get(Calendar.MONTH) == targetMonth,
            isToday = dayStart == todayStart,
            scheduleCount = daySchedules.size,
            hasPastSchedule = hasPast,
            hasUpcomingSchedule = hasUp,
            bars = bars
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
    siteTitle: String,
    members: List<com.detailline.callfollowcrm.ai.TeamRepository.TeamMember>,
    collabPartners: List<com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity>,
    assignedCollabPhones: Set<String>,
    initiallySelected: Set<String>,
    initialMemo: String,
    defaultStartHour: Int,
    onAddTeamMember: (name: String, phone: String) -> Unit,
    onAddWorker: (name: String, phone: String, wageManwon: Int?) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Set<String>, String) -> Unit,
    onInviteCollab: (phone: String, force: Boolean, memo: String, dailyWage: Int?, startHour: Int) -> Unit,
    onCancelCollab: (phone: String) -> Unit
) {
    fun key(phone: String) = phone.filter { it.isDigit() }.takeLast(8)
    // 이미 요청한 일당사장 = 처음부터 '선택됨'으로 보여줌(요청함). 해제하면 취소.
    val reqKeys = remember(assignedCollabPhones) { assignedCollabPhones.map { key(it) }.filter { it.isNotEmpty() }.toSet() }

    var selectedWorkers by remember { mutableStateOf(initiallySelected) }
    var selectedPartners by remember { mutableStateOf(reqKeys) }   // 일당사장 phone last8 키
    // 일당사장별 일당(만원, 문자열) — 저장값 자동 채움, 사장님이 이 현장만 바꿀 수 있음.
    var partnerWages by remember {
        mutableStateOf(
            collabPartners.associate { p -> key(p.phone) to (p.wage?.takeIf { it > 0 }?.let { (it / 10000).toString() } ?: "") }
        )
    }
    var memo by remember { mutableStateOf(initialMemo) }
    var startHour by remember { mutableStateOf(defaultStartHour) }
    // 시트 안 "+추가" 인라인 폼 (한 번에 하나만 열림)
    var addTeamOpen by remember { mutableStateOf(false) }
    var addWorkerOpen by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newWage by remember { mutableStateOf("") }
    val noRipple = remember { MutableInteractionSource() }

    val anySelected = selectedWorkers.isNotEmpty() || selectedPartners.isNotEmpty()
    val totalCount = selectedWorkers.size + selectedPartners.size
    val purple = Color(0xFF7C5CFC); val purpleLight = Color(0xFFF1ECFF)

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
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // grip
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
                    .width(38.dp).height(4.dp).clip(RoundedCornerShape(999.dp)).background(TossDivider)
            )
            Text("전문가 배정", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("${siteTitle}에 누구를 부를까요?  탭하면 선택, 다시 탭하면 취소예요.",
                fontSize = 13.sp, color = TossTextTertiary, lineHeight = 19.sp)
            Spacer(Modifier.height(18.dp))

            // ── 👷 팀원 ── (토글 + 끝에 + 추가)
            Text("👷 팀원", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
                modifier = Modifier.padding(start = 2.dp, bottom = 9.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                members.forEach { m ->
                    val on = selectedWorkers.contains(m.memberId)
                    val roleLabel = if (m.role == "owner") "대표" else "팀원"
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (on) TossBlue else TossGrayBg)
                            .clickable { selectedWorkers = if (on) selectedWorkers - m.memberId else selectedWorkers + m.memberId }
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
                AddChip("팀원 추가", TossGrayBg, TossBlue) {
                    newName = ""; newPhone = ""; newWage = ""; addWorkerOpen = false; addTeamOpen = !addTeamOpen
                }
            }
            if (addTeamOpen) {
                QuickAddForm(
                    title = "새 팀원 추가", showWage = false,
                    name = newName, onName = { newName = it },
                    phone = newPhone, onPhone = { newPhone = it },
                    wage = "", onWage = {}, accent = TossBlue,
                    onCancel = { addTeamOpen = false },
                    onSubmit = { onAddTeamMember(newName, newPhone); addTeamOpen = false; newName = ""; newPhone = "" }
                )
            }

            // ── 🤝 일당사장 ── (토글 + 끝에 + 추가)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
            Spacer(Modifier.height(16.dp))
            Text("🤝 일당사장", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
            Text("탭하면 부를 사장님 선택, 다시 탭하면 취소. 고객 번호·대화는 안 보내요.",
                fontSize = 11.5.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                collabPartners.forEach { p ->
                    val k = key(p.phone)
                    val on = k in selectedPartners
                    val wasReq = k in reqKeys
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (on) purple else purpleLight)
                            .clickable { selectedPartners = if (on) selectedPartners - k else selectedPartners + k }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (on) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        else Text("🤝", fontSize = 12.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(p.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                            color = if (on) Color.White else purple)
                        if (on && wasReq) {
                            Spacer(Modifier.width(5.dp))
                            Text("요청함", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
                AddChip("일당사장 추가", purpleLight, purple) {
                    newName = ""; newPhone = ""; newWage = ""; addTeamOpen = false; addWorkerOpen = !addWorkerOpen
                }
            }
            if (collabPartners.isEmpty() && !addWorkerOpen) {
                Spacer(Modifier.height(8.dp))
                Text("아직 등록된 일당사장이 없어요. 위 ‘+ 일당사장 추가’로 등록해보세요.",
                    fontSize = 12.5.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp))
            }
            if (addWorkerOpen) {
                QuickAddForm(
                    title = "새 일당사장 추가", showWage = true,
                    name = newName, onName = { newName = it },
                    phone = newPhone, onPhone = { newPhone = it },
                    wage = newWage, onWage = { newWage = it.filter { c -> c.isDigit() }.take(4) },
                    accent = purple,
                    onCancel = { addWorkerOpen = false },
                    onSubmit = {
                        onAddWorker(newName, newPhone, newWage.toIntOrNull())
                        addWorkerOpen = false; newName = ""; newPhone = ""; newWage = ""
                    }
                )
            }

            // ── 공통 출근시간 · 전달 메모 · 선택한 일당사장별 일당 (선택된 사람 있을 때만) ──
            if (anySelected) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                Spacer(Modifier.height(14.dp))
                SheetFieldLabel("출근 시간 (선택)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(7, 8, 9, 10, 11, 13, 14).forEach { h ->
                        val on = startHour == h
                        val ampm = if (h < 12) "오전" else "오후"; val h12 = if (h % 12 == 0) 12 else h % 12
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(if (on) purple else TossGrayBg)
                                .clickable { startHour = if (on) -1 else h }
                                .padding(horizontal = 13.dp, vertical = 8.dp)
                        ) {
                            Text("$ampm ${h12}시", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                color = if (on) Color.White else TossTextSecondary, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SheetFieldLabel("전달 메모 (선택)")
                SheetTextField(
                    memo, { memo = it },
                    placeholder = "예: 현장 앞에서 만나요 · 사다리차 · 현관 비번 1234#",
                    singleLine = false, minHeightDp = 60
                )
                Text("선택한 모두에게 같이 전달돼요.",
                    fontSize = 11.5.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 6.dp, start = 2.dp))

                // 일당사장별 일당 — 제일 중요(자동 채움, 이 현장만 바꿀 수 있음).
                val selectedPartnerList = collabPartners.filter { key(it.phone) in selectedPartners }
                if (selectedPartnerList.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SheetFieldLabel("일당 (일당사장별, 만원)")
                    selectedPartnerList.forEach { p ->
                        val k = key(p.phone)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🤝 ${p.name}", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                                modifier = Modifier.weight(1f))
                            Box(Modifier.width(120.dp)) {
                                SheetTextField(
                                    partnerWages[k] ?: "",
                                    { v -> partnerWages = partnerWages + (k to v.filter { c -> c.isDigit() }.take(4)) },
                                    placeholder = "예: 25", keyboardType = KeyboardType.Number, singleLine = true,
                                    visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("만원", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                        }
                    }
                }
            }

            // ── 버튼 하나: ○명에게 보내기 (팀원 배정 + 일당사장 요청 동시) ──
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (anySelected) TossBlue else Color(0xFFE5E8EF))
                    .clickable(enabled = anySelected) {
                        // 팀원 배정 저장(있거나 원래 있었으면 — 비우기 포함). 메모 공통.
                        if (selectedWorkers.isNotEmpty() || initiallySelected.isNotEmpty()) onSave(selectedWorkers, memo)
                        // 새로 선택한 일당사장 → 협업 요청(공통 메모·시간 + 각자 일당).
                        selectedPartners.forEach { k ->
                            if (k !in reqKeys) collabPartners.firstOrNull { key(it.phone) == k }?.let { p ->
                                onInviteCollab(p.phone, false, memo, partnerWages[k]?.toIntOrNull(), startHour)
                            }
                        }
                        // 요청했었다가 해제한 일당사장 → 취소.
                        reqKeys.forEach { k ->
                            if (k !in selectedPartners) collabPartners.firstOrNull { key(it.phone) == k }?.let { p ->
                                onCancelCollab(p.phone)
                            }
                        }
                        onDismiss()
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (anySelected) "${totalCount}명에게 보내기" else "부를 사람을 골라주세요",
                    color = if (anySelected) Color.White else TossTextTertiary,
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/** 전문가 배정 시트 "+ 추가" 칩 — 채움 배경(보더 미사용)으로 토글식 인라인 추가 폼을 연다. */
@Composable
private fun AddChip(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

/** 전문가 배정 시트 안 인라인 "빠른 추가" 폼 — 이름+전화(+일당). 화면 이동 없이 바로 등록. (2026-06-14) */
@Composable
private fun QuickAddForm(
    title: String,
    showWage: Boolean,
    name: String, onName: (String) -> Unit,
    phone: String, onPhone: (String) -> Unit,
    wage: String, onWage: (String) -> Unit,
    accent: Color,
    onCancel: () -> Unit,
    onSubmit: () -> Unit
) {
    Spacer(Modifier.height(10.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF4F6F8)).padding(14.dp)
    ) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = TossTextPrimary)
        Spacer(Modifier.height(10.dp))
        SheetFieldLabel("이름")
        SheetTextField(name, onName, placeholder = "예: 박반장")
        Spacer(Modifier.height(10.dp))
        SheetFieldLabel("전화번호")
        FormattedTextField(
            value = phone, onValueChange = onPhone,
            format = PhoneNumberFormatter::formatProgressive,
            placeholder = "010-0000-0000", keyboardType = KeyboardType.Phone
        )
        if (showWage) {
            Spacer(Modifier.height(10.dp))
            SheetFieldLabel("그날 일당 (만원, 선택)")
            SheetTextField(wage, onWage, placeholder = "예: 25", keyboardType = KeyboardType.Number,
                visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .clickable { onCancel() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("취소", fontWeight = FontWeight.Bold, color = TossTextSecondary, fontSize = 14.sp) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(accent)
                    .clickable { onSubmit() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("추가", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 14.sp) }
        }
    }
}
