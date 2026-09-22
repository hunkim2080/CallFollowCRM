package com.detailline.callfollowcrm.presentation.screen.schedule

import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppType
import androidx.compose.material.icons.filled.AutoAwesome
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.LightColors
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.draw.shadow
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
import com.detailline.callfollowcrm.presentation.theme.TossWarning
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextInfo
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
    /** 일정 시공 카드 탭 → 그 고객 문자(채팅) 바로. 일정을 누르는 이유가 '그날 문자 보내려고'인 경우가 대부분이라. (2026-08-30 사장님) */
    onOpenChat: (String, Long) -> Unit = { _, _ -> },
    onAddSchedule: (Long?) -> Unit = {},
    onOpenSettle: () -> Unit = {},
    onOpenCollabSites: (String?) -> Unit = {},
    /** 구글 캘린더 — 연결됨 여부 + 동기화/연결 실행(일정 탭 상단 버튼). (2026-09-01 사장님) */
    calendarConnected: Boolean = false,
    onCalendarSync: () -> Unit = {},
    /** 동기화 도는 중 · 마지막으로 올린 시각(ms, 0=아직)·건수. 버튼 밑에 표시 — 계속 누르게 되지 않도록. (2026-09-15 사장님) */
    calendarSyncing: Boolean = false,
    calendarSyncedAtMs: Long = 0L,
    calendarSyncedCount: Int = 0,
    /** 진입 시 미리 선택할 날(ms). 홈 "다음 시공" 카드에서 그 시공일로. null/<=0 = 오늘. */
    initialSelectedDayMs: Long? = null
) {
    val state by viewModel.state.collectAsState()
    val asList by viewModel.asScheduled.collectAsState()           // A/S 예약 고객(시공과 별개 흐름). (DB v43)
    val asDays by viewModel.asDayStarts.collectAsState()           // 캘린더 A/S 주황 점
    val collabDays by viewModel.collabDayStarts.collectAsState()   // 캘린더 협업 보라 띠 (#7)
    val simpleEvents by viewModel.simpleEvents.collectAsState()    // 번호 없는 간단 일정 (2026-09-16)
    val simpleDays by viewModel.simpleDayStarts.collectAsState()   // 캘린더 회색 점
    val pendingCollabDays by viewModel.pendingCollabDayStarts.collectAsState()  // 응답 안 한 협업 요청 = 주황 마커 (2026-07-08 사장님)
    val pendingCollabSites by viewModel.pendingCollabSites.collectAsState()
    val collabAssign by viewModel.collabAssignByCustomer.collectAsState()   // 협업 사장 배정 → 카드 "이름"
    val collabSites by viewModel.collabSites.collectAsState()
    // 협업 현장에도 **주소가 있다**. 달력 칸에 지역명을 적으려고 날짜→지역명으로 바꿔둔다.
    //   (2026-09-22 사장님 "협업도 주소지가 있는데 왜 이렇게 하니" — 내가 없다고 잘못 알았다.)
    val collabRegions = remember(collabSites) {
        collabSites.filter { it.scheduledAtMs > 0L }.associate { site ->
            DateTimeUtils.startOfDay(site.scheduledAtMs) to
                (com.detailline.callfollowcrm.util.RegionName.shortRegion(site.addr) ?: "협업")
        }
    }
    val pendingRegions = remember(pendingCollabSites) {
        pendingCollabSites.filter { it.scheduledAtMs > 0L }.associate { site ->
            DateTimeUtils.startOfDay(site.scheduledAtMs) to
                (com.detailline.callfollowcrm.util.RegionName.shortRegion(site.addr) ?: "요청")
        }
    }
    val nowMs = remember { System.currentTimeMillis() }
    val todayStart = remember(nowMs) { DateTimeUtils.startOfDay(nowMs) }

    // [길찾기] — 그 날 카드에 붙은 버튼. 주소가 카드에 이미 있어서 번호 조회 없이 바로 연다.
    //   처음 한 번만 네비 앱을 고르고(prefs.defaultNavAppKey), 그 뒤로는 1탭. 홈과 같은 방식.
    val scheduleCtx = androidx.compose.ui.platform.LocalContext.current
    val navScope = androidx.compose.runtime.rememberCoroutineScope()
    val navPrefs = remember(scheduleCtx) {
        (scheduleCtx.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication)
            .container.preferences
    }
    var navDialogAddr by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }
    fun launchNavigationForAddr(addr: String?) {
        val navApp = com.detailline.callfollowcrm.util.NavApp.fromKey(navPrefs.defaultNavAppKey)
        if (navApp == null) navDialogAddr = addr
        else navScope.launch {
            com.detailline.callfollowcrm.util.NavLauncher.launch(scheduleCtx, navApp, addr)
        }
    }
    navDialogAddr?.let { pending ->
        com.detailline.callfollowcrm.presentation.component.NavAppPickerDialog(
            onPick = { picked ->
                navPrefs.defaultNavAppKey = picked.key
                navDialogAddr = null
                navScope.launch {
                    com.detailline.callfollowcrm.util.NavLauncher.launch(scheduleCtx, picked, pending)
                }
            },
            onDismiss = { navDialogAddr = null }
        )
    }
    // 협업(수락/요청) 실시간 반영 — 일정탭 보는 동안 주기 폴링. 탭 벗어나면(컴포지션 해제) 자동 정지, 돌아오면 재개.
    //   (다른 폰에서 방금 보낸 협업 요청도 화면 그대로 두고 ~12초 안에 주황 마커로 뜸.) (2026-07-08 사장님)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            viewModel.loadCollab()
            kotlinx.coroutines.delay(12_000)
        }
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
    val jobCrewByCustomer by viewModel.jobCrewByCustomer.collectAsState()   // 내가 부른 일당 배정
    val assignToast by viewModel.toast.collectAsState()
    val assignCtx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(assignToast) {
        assignToast?.let {
            android.widget.Toast.makeText(assignCtx, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }
    var assignTarget by remember { mutableStateOf<CustomerEntity?>(null) }
    // 협업 카드 밀어 "그만두기" 확인 대상(수락된 협업만 — 상대에 알림 감). null=닫힘. (2026-06-20 사장님)
    var confirmLeaveCollab by remember { mutableStateOf<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite?>(null) }
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
                // 일정은 제목이 2줄(제목+날짜)이라 TopAppBar 세로중앙정렬 시 1줄 탭보다 위로 붙음 → 상담함과 실측 정렬(top=22
                //   에서 제목 y가 상담함과 일치). (2026-07-02 사장님: 일정 글씨가 너무 위)
                windowInsets = WindowInsets.statusBars.add(WindowInsets(top = 22.dp)),
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
                    // 구글 캘린더 — **연결한 사람에게만**, 버튼이 아니라 '언제 올렸는지' 한 줄. (2026-09-16 사장님)
                    //   "이제 버튼 안 눌러도 되는 거면 동기화 날짜만 적히면 될 듯한데. 지금은 투박해서.
                    //    그리고 구글 캘린더를 연동한 사람만 나와야 하지 않을까?"
                    //   일정·메모가 바뀌면 CalendarAutoSync 가 알아서 올린다 → 누를 일이 없다.
                    //   미연결이면 아예 안 보인다(연결은 더보기 > 설정에서). 안 쓰는 사람 화면을 안 어지럽힌다.
                    //   탭은 살려둔다 — 자동이 늦을 때 직접 올릴 수 있는 뒷문(겉보기는 조용한 글씨).
                    if (calendarConnected) {
                        Text(
                            if (calendarSyncing) "올리는 중…"
                            else lastSyncLabel(calendarSyncedAtMs, calendarSyncedCount),
                            fontSize = 11.sp,
                            color = TossTextTertiary,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .clickable(enabled = !calendarSyncing) { onCalendarSync() }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color.White)
                            .clickable { onAddSchedule(selectedDayMs) },
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
        // 이 날 응답 안 한 협업 요청 — 주황 마커 탭 시 확인 카드로. (2026-07-08 사장님)
        val pendingForSelected = remember(selectedDayMs, pendingCollabSites) {
            val day = selectedDayMs ?: return@remember emptyList()
            pendingCollabSites.filter { DateTimeUtils.startOfDay(it.scheduledAtMs) == day }
        }
        // 이 날 간단 일정 — 번호도 돈도 없는 메모형. 시공·A/S 와 안 섞는다. (2026-09-16 사장님)
        val simpleForSelected = remember(selectedDayMs, simpleEvents) {
            val day = selectedDayMs ?: return@remember emptyList()
            simpleEvents.filter { it.dayStartMs == day }
                .sortedBy { it.minutes ?: -1 }   // 하루 종일이 맨 위
        }
        // 이 날 A/S 예약(무료) — 시공과 별개. A/S만 있는 고객도 여기 뜬다(시공 목록엔 안 뜸). (DB v43)
        val asForSelected = remember(selectedDayMs, asList) {
            val day = selectedDayMs ?: return@remember emptyList<CustomerEntity>()
            asList.filter { asCoversDay(it, day) }
                .sortedBy { it.asScheduledDate ?: 0L }
        }

        // 2026-06-08 사장님 통점(투명막 진범): LazyColumn 에 initialFirstVisibleItemIndex=1 을 주면 안 됨.
        //   index0(캘린더 블록)이 뷰포트보다 큰 단일 item 인데, 첫 컴포지션 땐 그 아래 데이터가 비어
        //   스크롤 범위가 없어 LazyList 측정이 깨짐 → 세로스크롤·가로스와이프·셀탭이 전부 입력만 먹고
        //   안 움직이는 "투명막"이 됨. state 인자 자체를 빼서 정상본과 동일한 기본(0-index)로 둔다.
        //   그 날 일정은 selectedDayMs = initialDay 로 캘린더 아래에 이미 렌더되므로 자동 스크롤 불필요.
        LazyColumn(
            modifier = Modifier
                .padding(top = inner.calculateTopPadding())
                .fillMaxSize()
                .background(TossGrayBg),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
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
                    //   프로토 box-shadow(0 2px 10px rgba(17,24,39,.05)) 복원 — 흰카드(#FFF)와 페이지(#F4F5F7)가
                    //   거의 같은 색이라 그림자 없으면 카드가 안 떠서 달력 전체가 '회색 뿌옇게' 보임(사장님 지적 2026-08-01).
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 7.dp,
                                shape = RoundedCornerShape(20.dp),
                                spotColor = Color(0xFF111827),
                                ambientColor = Color(0xFF111827)
                            )
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
                                        collabRegions = collabRegions,
                                        pendingRegions = pendingRegions,
                                        pendingCollabDays = pendingCollabDays,
                                        asDays = asDays,
                                        simpleDays = simpleDays,
                                        onSelect = { dayMs -> selectedDayMs = dayMs },
                                        onLongSelect = { dayMs -> selectedDayMs = dayMs; onAddSchedule(dayMs) }
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
                        Text("날짜를 ", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TossTextTertiary)
                        Text("길게 누르면", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                        Text(" 그 날 일정을 바로 등록해요", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TossTextTertiary)
                    }
                    // 캘린더 막대 색 범례 — 색만으론 뜻을 못 알아봄. 2026-07-30
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 색은 **다 다르게**, 모양은 **달력에 찍히는 그대로**(시공~A/S=막대, 간단=점).
                        //   전엔 요청과 A/S 가 똑같은 주황이라 🔧 이모지로 억지 구분했고, 간단도 회색이
                        //   겹쳐 📌 를 붙였다. 이모지로 때우는 건 **색이 틀렸다는 뜻**이다. (2026-09-20 사장님)
                        listOf(
                            Triple(TossSuccess, "시공", true),
                            Triple(TossTextTertiary, "지난", true),
                            Triple(AppTheme.colors.category, "협업", true),
                            Triple(AppTheme.colors.caution, "요청", true),
                            Triple(AppTheme.colors.primary, "A/S", true),
                            Triple(TossTextTertiary, "간단", false)
                        ).forEach { (col, lbl, isBar) ->
                            if (isBar) Box(
                                Modifier.padding(start = 9.dp).width(13.dp).height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)).background(col)
                            ) else Box(
                                Modifier.padding(start = 9.dp).size(7.dp).clip(CircleShape).background(col)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(lbl, fontSize = 10.5.sp, color = TossTextSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            // 4) 선택된 날 라벨 + 시공 카드 (프로토 cal-day-label + cal-day-jobs)
            item(key = "day-label") {
                DayLabel(dayMs = selectedDayMs, isToday = selectedDayMs == todayStart)
            }
            if (schedulesForSelected.isEmpty()) {
                if (collabForSelected.isEmpty() && asForSelected.isEmpty() && simpleForSelected.isEmpty()) {
                    item(key = "no-schedules") { DayEmpty(onAdd = { onAddSchedule(selectedDayMs) }) }
                    // 고른 날이 비었으면 **앞으로 뭐가 있는지**를 보여준다. 전엔 "없어요" 한 줄로 끝나서
                    //   다음 일정을 보려면 날짜를 하나씩 눌러봐야 했다. (2026-09-21 사장님)
                    item(key = "upcoming") {
                        UpcomingSection(
                            jobs = state.all,
                            collab = collabSites,
                            todayStart = todayStart,
                            onOpenDay = { dayMs -> selectedDayMs = dayMs }
                        )
                    }
                }
            } else {
                if (schedulesForSelected.size > 1) {
                    item(key = "day-count") { DayCount(schedulesForSelected.size) }
                }
                // 키 = (고객, 시공일) — 한 고객이 여러 날짜를 잡으면 id 만으론 키가 겹쳐 목록이 깨진다. (Stage A)
                items(schedulesForSelected, key = { laneKeyOf(it) }) { c ->
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
                            selectedDayMs = selectedDayMs,
                            todayStart = todayStart,
                            assignedMembers = assignmentsByCustomer[c.id].orEmpty(),
                            // 다일 공사: 이 협업자가 '일하는 날'에만 이름표 표시. days 비면=전체(하위호환). (2026-08-02 하루만 배정 버그 fix)
                            collabPartnerNames = collabAssign[c.id].orEmpty()
                                .filter { it.days.isEmpty() || selectedDayMs in it.days }
                                .map { it.name to it.accepted },
                            teamAvailable = teamMembers.isNotEmpty() || collabPartners.isNotEmpty(),
                            // 주소가 없어도 시트는 연다 — '내가 부른 일당' 배정은 아무것도 안 보내니 주소가 필요 없다.
                            //   협업 요청(서버로 나가는 것)만 주소가 필수인데, 그건 시트 안에서 주소를 받아 막는다
                            //   (needAddress + 📍현장 주소 입력칸). 2026-06-18 의 "주소 없는데 일당사장에게 알람이 갔음"은
                            //   그 시트 가드가 담당. 여기서 통째로 막으면 일당 배정까지 못 하게 된다. (2026-07-16 사장님)
                            onAssign = { assignTarget = c },
                            // 시공 카드 탭 = 그날 그 고객한테 문자 보내려는 경우가 대부분 → 고객정보 대신 문자(채팅)로 바로.
                            //   고객정보가 필요하면 채팅 헤더에서 열 수 있음(onOpenCustomerDetail). (2026-08-30 사장님)
                            onNavigate = { addr -> launchNavigationForAddr(addr) },
                            onCall = { phone -> dialFromSchedule(scheduleCtx, phone) },
                            onClick = { onOpenChat(c.phoneNumber, c.id) }
                        )
                    }
                }
            }
            if (collabForSelected.isNotEmpty()) {
                item(key = "collab-label") {
                    Text(
                        "이 날 협업 ${collabForSelected.size}곳",
                        fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.category,
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 11.dp)
                    )
                }
                items(collabForSelected, key = { "sh-${it.shareId}" }) { site ->
                    CollabSwipeBox(
                        onDelete = {
                            // 끝난 협업은 그냥 숨김(되돌리기). 진행 중(수락된) 협업은 확인 후 "그만두기"(상대에 알림). (2026-06-20 사장님)
                            if (site.progress == com.detailline.callfollowcrm.ai.SharedSiteRepository.Progress.COMPLETED) {
                                viewModel.hideCollab(site.shareId)
                                uiScope.launch {
                                    val r = snackbarHostState.showSnackbar(
                                        message = "끝난 협업을 숨겼어요",
                                        actionLabel = "되돌리기",
                                        duration = androidx.compose.material3.SnackbarDuration.Short
                                    )
                                    if (r == SnackbarResult.ActionPerformed) viewModel.unhideCollab(site.shareId)
                                }
                            } else {
                                confirmLeaveCollab = site
                            }
                        }
                    ) {
                        CollabDayCard(site = site, onClick = { onOpenCollabSites(site.shareId) })
                    }
                }
            }
            // 응답 안 한 협업 요청 — 주황 카드로 눈에 띄게 + 탭 시 협업 화면(수락/거절). 푸시 놓쳐도 여기서 catch. (2026-07-08 사장님)
            if (pendingForSelected.isNotEmpty()) {
                item(key = "pending-collab-label") {
                    Text(
                        "협업 요청 ${pendingForSelected.size}건 · 아직 응답 안 함",
                        fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB8780A),
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 11.dp)
                    )
                }
                items(pendingForSelected, key = { "pend-${it.shareId}" }) { site ->
                    PendingCollabDayCard(site = site, onClick = { onOpenCollabSites(site.shareId) })
                }
            }
            // 이 날 A/S(무료) — 시공과 별개. A/S만 있는 고객도 여기 뜬다(시공 목록엔 안 뜸). 탭 → 고객 상세. (DB v43)
            if (asForSelected.isNotEmpty()) {
                item(key = "as-label") {
                    Text(
                        "이 날 A/S ${asForSelected.size}곳 · 무료",
                        fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB8780A),
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 11.dp)
                    )
                }
                items(asForSelected, key = { "as-${it.id}" }) { c ->
                    // A/S 카드도 시공 카드와 동일 — 탭 = 그 고객 문자(채팅) 바로. (2026-08-30 사장님)
                    AsDayCard(customer = c, selectedDayMs = selectedDayMs, onClick = { onOpenChat(c.phoneNumber, c.id) })
                }
            }
            // 간단 일정 — 맨 아래. 돈·D-day 가 없으니 조용한 회색으로. (2026-09-16 사장님)
            if (simpleForSelected.isNotEmpty()) {
                item(key = "simple-label") {
                    Text(
                        "이 날 간단 일정 ${simpleForSelected.size}개",
                        fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary,
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 11.dp)
                    )
                }
                items(simpleForSelected, key = { "sm-${it.id}" }) { ev ->
                    SimpleEventCard(
                        event = ev,
                        onDelete = { viewModel.deleteSimpleEvent(ev.id) },
                        onSave = { title, dayMs, minutes, memo ->
                            viewModel.editSimpleEvent(ev.id, title, dayMs, minutes, memo)
                        }
                    )
                }
            }
            // "더 추가"는 이미 일정/협업/A-S 가 있을 때만. 아무것도 없으면 DayEmpty 의 "이 날 일정 등록"만 노출(중복 방지).
            if (schedulesForSelected.isNotEmpty() || collabForSelected.isNotEmpty() ||
                asForSelected.isNotEmpty() || simpleForSelected.isNotEmpty()
            ) {
                item(key = "day-add") { DayAddButton("이 날 일정 더 추가", { onAddSchedule(selectedDayMs) }) }
            }
            item { Spacer(Modifier.height(12.dp)) }
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
        // 다일 공사면 [시작일 … 시작일+(일수-1)] 각 날의 startOfDay 목록 → 시트 '일하는 날' 칩. 단일일이면 1개. (2026-08-02)
        val assignJobDayStarts = c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) }?.let { js ->
            (0 until c.scheduledWorkDays.coerceAtLeast(1)).map { js + it * DateTimeUtils.DAY_MS }
        } ?: emptyList()
        AssignTeamSheet(
            siteTitle = assignSiteTitle,
            siteAddress = c.address,
            members = teamMembers,
            collabPartners = collabPartners,
            assignedCollabPhones = collabAssign[c.id].orEmpty().map { it.phone }.toSet(),
            initiallySelected = assignedIds,
            initialMemo = existingMemo,
            defaultStartHour = viewModel.lastCollabStartHour,
            jobDayStarts = assignJobDayStarts,
            onAddTeamMember = { name, phone -> viewModel.addTeamMember(name, phone) },
            onAddWorker = { name, phone, wage -> viewModel.addCollabPartner(name, phone, wage) },
            onDismiss = { assignTarget = null },
            onSave = { selectedIds, memo ->
                val dayStart = DateTimeUtils.startOfDay(c.scheduledWorkDate ?: System.currentTimeMillis())
                viewModel.assignTeam(c, dayStart, selectedIds.toList(), memo)
                assignTarget = null
            },
            onInviteCollab = { phone, force, memo, wage, hour, address, days ->
                viewModel.inviteCollabToSite(c, phone, force, memo, wage, hour, address, workDayStarts = days) { partner, body ->
                    com.detailline.callfollowcrm.util.SmsIntentHelper.openSmsCompose(assignCtxLocal, partner, body)
                }
            },
            onCancelCollab = { phone -> viewModel.removeCollabAssignment(c.id, phone) },
            // 내가 부른 일당 — 이 시공(고객+시공일)에 이미 붙어있는 사람/금액.
            crewWagesByWorkerId = jobCrewByCustomer[c.id].orEmpty()
                .filter { it.dayStartMs == DateTimeUtils.startOfDay(c.scheduledWorkDate ?: 0L) }
                .associate { it.workerId to it.wage },
            onSaveCrew = { crew ->
                viewModel.setJobCrew(c, DateTimeUtils.startOfDay(c.scheduledWorkDate ?: System.currentTimeMillis()), crew)
            }
        )
    }

    // 협업 카드 밀어 "그만두기" 확인 — 수락된 협업은 상대(주인 A)에게 알림 가고 양쪽에서 빠짐. (2026-06-20 사장님)
    confirmLeaveCollab?.let { s ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLeaveCollab = null },
            title = { Text("협업을 그만할까요?", fontWeight = FontWeight.Bold) },
            text = { Text("${s.ownerName}님께 '협업을 그만뒀어요' 알림이 가요. 사진·기록은 그대로 남아요.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmLeaveCollab = null
                    viewModel.leaveCollabSite(s)
                }) { Text("그만하기", color = TossError, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmLeaveCollab = null }) {
                    Text("계속 함께", color = TossTextSecondary)
                }
            }
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
                Box(Modifier.size(9.dp).clip(CircleShape).background(AppTheme.colors.category))
                Spacer(Modifier.width(10.dp))
                Text(com.detailline.callfollowcrm.ai.siteDisplayName(site), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, modifier = Modifier.weight(1f))
                Text(
                    "협업",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppTheme.colors.category,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.categoryBg).padding(horizontal = 9.dp, vertical = 4.dp)
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

/** 응답 안 한 협업 요청 카드 — 주황(눈에 띄게) + '확인하기'. 탭 시 협업 화면(수락/거절). 푸시 놓쳐도 일정에서 catch. (2026-07-08 사장님) */
@Composable
private fun PendingCollabDayCard(
    site: com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite,
    onClick: () -> Unit
) {
    TossCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(TossWarning))
                Spacer(Modifier.width(10.dp))
                Text(com.detailline.callfollowcrm.ai.siteDisplayName(site), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, modifier = Modifier.weight(1f))
                Text(
                    "요청 · 확인하기",
                    fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB8780A),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.cautionBg).padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(site.ownerName.takeIf { it.isNotBlank() }?.let { "$it 사장님이 요청" }, site.timeLabel, site.addr).joinToString(" · "),
                fontSize = 13.sp, color = TossTextSecondary, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text("눌러서 수락/거절하기 →", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8780A))
        }
    }
}

/** A/S 예약 카드 — 무료. 시공과 별개(주황). 여러 날 A/S면 'N일 중 M일차'. 탭 → 고객 상세. (DB v43, 2026-08-01 사장님) */
/**
 * 간단 일정 카드 — 번호도 돈도 없는 메모형. (2026-09-16 사장님)
 *
 * 시공 카드와 **일부러 다르게 조용하다**: 금액·D-day 태그가 없고 색도 회색이다.
 * 사장님이 한눈에 "이건 돈 버는 일이 아니라 그냥 내 메모" 라고 알아보게.
 */
@Composable
private fun SimpleEventCard(
    event: com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity,
    onDelete: () -> Unit,
    /** 제목·날짜·시간·메모를 고쳐 저장. (2026-09-18 사장님) */
    onSave: (title: String, dayMs: Long, minutes: Int?, memo: String) -> Unit = { _, _, _, _ -> }
) {
    // 카드를 누르면 **고치기** 창. 전엔 "지울까요?" 만 물어서 고칠 길이 아예 없었다. (2026-09-18 사장님)
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    TossCard(onClick = { editing = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(TossTextTertiary))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    event.title,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                val sub = buildString {
                    append(event.minutes?.let { DateTimeUtils.formatWorkMinutes(it) } ?: "하루 종일")
                    if (event.memo.isNotBlank()) append(" · ").append(event.memo)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    sub, fontSize = 12.5.sp, color = TossTextTertiary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
    if (editing) {
        SimpleEventEditDialog(
            event = event,
            onSave = { t, d, m, memo -> editing = false; onSave(t, d, m, memo) },
            onAskDelete = { editing = false; confirmDelete = true },
            onDismiss = { editing = false }
        )
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color.White,
            tonalElevation = 0.dp,
            title = { Text(event.title) },
            text = { Text("이 간단 일정을 지울까요?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("삭제", color = com.detailline.callfollowcrm.presentation.theme.TossError)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("닫기") }
            }
        )
    }
}

/**
 * 간단 일정 고치기 창 — 제목 · 날짜 · 시간 · 메모. (2026-09-18 사장님)
 *   등록 화면(ScheduleAddScreen)과 같은 순서·같은 말로 맞춘다. 지우기는 맨 아래 조용히.
 */
@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@Composable
private fun SimpleEventEditDialog(
    event: com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity,
    onSave: (title: String, dayMs: Long, minutes: Int?, memo: String) -> Unit,
    onAskDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(event.title) }
    var memo by remember { mutableStateOf(event.memo) }
    var dayMs by remember { mutableLongStateOf(event.dayStartMs) }
    var minutes by remember { mutableStateOf(event.minutes) }
    var datePickerOpen by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp)
        ) {
            com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
            Text("간단 일정 고치기", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(14.dp))

            Text("제목", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
            Spacer(Modifier.height(5.dp))
            androidx.compose.material3.OutlinedTextField(
                value = title, onValueChange = { title = it },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("예: 자재 받는 날") }
            )

            Spacer(Modifier.height(12.dp))
            Text("날짜", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
            Spacer(Modifier.height(5.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                    .clickable { datePickerOpen = true }.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    DateTimeUtils.formatScheduledDate(dayMs),
                    fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text("\u203A", fontSize = 17.sp, color = TossTextTertiary)
            }

            Spacer(Modifier.height(12.dp))
            Text("시간", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
            Spacer(Modifier.height(5.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf<Pair<String, Int?>>(
                    "하루 종일" to null, "오전 8시" to 8 * 60, "오전 9시" to 9 * 60,
                    "오전 10시" to 10 * 60, "오후 1시" to 13 * 60, "오후 3시" to 15 * 60
                ).forEach { (label, m) ->
                    val on = minutes == m
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (on) TossBlue else TossGrayBg)
                            .clickable { minutes = m }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                            color = if (on) Color.White else TossTextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("메모 (선택)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
            Spacer(Modifier.height(5.dp))
            androidx.compose.material3.OutlinedTextField(
                value = memo, onValueChange = { memo = it },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
                placeholder = { Text("예: 케라폭시 20개") }
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                        .clickable { onDismiss() }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text("취소", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
                Box(
                    Modifier.weight(1.4f).clip(RoundedCornerShape(12.dp)).background(TossBlue)
                        .clickable {
                            val t = title.trim()
                            if (t.isBlank()) {
                                android.widget.Toast.makeText(ctx, "제목을 적어주세요", android.widget.Toast.LENGTH_SHORT).show()
                            } else onSave(t, dayMs, minutes, memo.trim())
                        }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text("저장", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "\uD83D\uDDD1 이 일정 지우기",
                fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                color = com.detailline.callfollowcrm.presentation.theme.TossError,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable { onAskDelete() }.padding(vertical = 8.dp)
            )
        }
    }

    if (datePickerOpen) {
        val toUtcMidnight = { ms: Long -> ms + java.util.TimeZone.getDefault().getOffset(ms) }
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = toUtcMidnight(dayMs)
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = Color.White),
            tonalElevation = 0.dp,
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { dayMs = DateTimeUtils.startOfDay(it - java.util.TimeZone.getDefault().getOffset(it)) }
                    datePickerOpen = false
                }) { Text("확인", color = TossBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { datePickerOpen = false }) { Text("취소") }
            }
        ) {
            androidx.compose.material3.DatePicker(
                state = state,
                colors = androidx.compose.material3.DatePickerDefaults.colors(
                    containerColor = Color.White,
                    selectedDayContainerColor = TossBlue,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = TossBlue,
                    todayContentColor = TossBlue
                )
            )
        }
    }
}

@Composable
private fun AsDayCard(
    customer: CustomerEntity,
    selectedDayMs: Long?,
    onClick: () -> Unit
) {
    val asStart = customer.asScheduledDate ?: return
    val s = DateTimeUtils.startOfDay(asStart)
    val totalDays = customer.asScheduledDays.coerceAtLeast(1)
    val dayN = selectedDayMs?.let { ((it - s) / DateTimeUtils.DAY_MS).toInt() + 1 }?.coerceIn(1, totalDays) ?: 1
    // A/S 색 = 파랑(범례와 같은 색). 전엔 '요청'과 똑같은 주황이라 🔧 이모지로 억지 구분했다. (2026-09-20 사장님)
    val orange = AppTheme.colors.primary; val orangeDeep = AppTheme.colors.primaryText; val orangeBg = AppTheme.colors.primaryBg
    TossCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(orange))
                Spacer(Modifier.width(10.dp))
                Text(
                    // 공구 이모지 대신 **왼쪽 점 색**(파랑)으로 A/S 임을 표시 — 범례와 같은 색. (2026-09-20)
                    customer.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(customer.phoneNumber),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (totalDays > 1) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(orangeBg).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("${totalDays}일 중 ${dayN}일차", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = orangeDeep)
                    }
                    Spacer(Modifier.width(7.dp))
                }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(orangeBg).padding(horizontal = 9.dp, vertical = 4.dp)) {
                    Text("A/S · 무료", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = orangeDeep)
                }
            }
            // 📍 주소 (있으면)
            com.detailline.callfollowcrm.util.AddressExtractor.tidyAddress(customer.address).takeIf { it.isNotBlank() }?.let { addr ->
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = TossTextTertiary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(addr, fontSize = 13.sp, color = TossTextSecondary, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
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
    // 밀면 바로가 아니라 → 드러나는 '삭제' 버튼을 눌러야 동작(2026-06-21 사장님). SwipeRevealBox 로 통일.
    // shape=16dp: 안에 든 TossCard(라운드 16dp)와 모서리를 맞춰야 빨간 버튼이 모서리로 삐져나오지 않음. (2026-06-23 사장님)
    com.detailline.callfollowcrm.presentation.component.SwipeRevealBox(
        onAction = onDelete,
        label = "삭제",
        shape = RoundedCornerShape(16.dp)
    ) { content() }
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
    collabRegions: Map<Long, String> = emptyMap(),
    pendingRegions: Map<Long, String> = emptyMap(),
    pendingCollabDays: Set<Long>,
    asDays: Set<Long>,
    simpleDays: Set<Long>,
    onSelect: (Long) -> Unit,
    onLongSelect: (Long) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            CalendarDay(
                cell = cell,
                isSelected = selectedDayMs == cell.dayStartMs,
                isCollab = cell.dayStartMs in collabDays,
                collabRegion = collabRegions[cell.dayStartMs],
                pendingRegion = pendingRegions[cell.dayStartMs],
                isPendingCollab = cell.dayStartMs in pendingCollabDays,
                isAs = cell.dayStartMs in asDays,
                isSimple = cell.dayStartMs in simpleDays,
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
    /** 협업 현장 지역명 — 없으면 "협업". */
    collabRegion: String? = null,
    /** 아직 응답 안 한 협업 요청의 지역명 — 없으면 "요청". */
    pendingRegion: String? = null,
    isPendingCollab: Boolean = false,
    isAs: Boolean = false,
    /** 간단 일정(번호 없는 메모형)이 있는 날 — 회색 점. (2026-09-16 사장님) */
    isSimple: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 날짜는 **작게 왼쪽 위**. 오늘만 작은 동그라미, 고른 날은 칸 전체를 연하게.
    //   전엔 32dp 동그라미가 칸 한가운데를 다 먹어서 띠가 두 줄밖에 못 들어갔다. (2026-09-22 사장님, 레퍼런스)
    val fgColor = when {
        cell.isToday -> Color.White
        !cell.isCurrentMonth -> TossTextTertiary
        cell.dayOfWeek == Calendar.SUNDAY -> TossError
        cell.dayOfWeek == Calendar.SATURDAY -> TossBlue
        else -> TossTextSecondary
    }
    // 프로토 jbar: 점 대신 일정 1건 = 막대 1줄(lane). 1건/2건/여러날이 한눈에 구분됨. (2026-06-11)
    //   선택칸 막대는 흰색, 지난 시공은 회색, 다가올 시공은 초록, 협업은 보라.
    val schedMaxLane = cell.bars.maxOfOrNull { it.lane } ?: -1
    val collabLane = if (isCollab) schedMaxLane + 1 else -1
    // 응답 안 한 협업 요청 = 주황 막대(초록 일정·보라 협업과 확실히 구분). 푸시 놓쳐도 일정 보다 눈에 띄게. (2026-07-08 사장님)
    val pendingLane = if (isPendingCollab) maxOf(schedMaxLane, collabLane) + 1 else -1
    // A/S 도 띠로. 점은 **어디인지를 못 적는다.** (2026-09-22, 레퍼런스 그림)
    val asLane = if (isAs) maxOf(maxOf(schedMaxLane, collabLane), pendingLane) + 1 else -1
    val lastLane = minOf(maxOf(maxOf(maxOf(schedMaxLane, collabLane), pendingLane), asLane), CAL_MAX_LANE)
    Box(
        modifier = modifier
            // 날짜 15dp + 사이 2 + 띠 세 줄(13×3 + 1.5×2) = 59, 여유 3 = 62dp.
            //   ⚠️ 종류마다 띠 높이를 다르게 하지 말 것 — 층이 어긋나 **깨져 보인다**. (2026-09-22 사장님)
            .height(62.dp)
            .padding(horizontal = 1.dp)
            .clip(AppShape.sm)
            .background(if (isSelected) AppTheme.colors.primaryBg else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            // 날짜 — 작게, 왼쪽 위. 오늘만 동그라미.
            Box(
                Modifier.padding(start = 3.dp).size(15.dp).clip(CircleShape)
                    .background(if (cell.isToday) TossBlue else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    cell.dayOfMonth.toString(),
                    color = fgColor,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,   // 테마 lineHeight 를 물려받으면 동그라미 안에서 글자가 내려앉는다
                    fontWeight = FontWeight.Bold
                )
            }
            if (lastLane >= 0) {
                Spacer(Modifier.height(2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.5.dp), modifier = Modifier.fillMaxWidth()) {
                    for (lane in 0..lastLane) {
                        when {
                            // 종류는 **색**으로만 가른다. 연한 바탕 + 진한 글자 — 세 줄이 쌓여도 안 답답하다.
                            lane == asLane ->
                                CalRegionBar(
                                    BarSeg.SINGLE, AppTheme.colors.primaryBg, AppTheme.colors.primaryText, "A/S"
                                )
                            lane == pendingLane -> PendingCalBar(pendingRegion ?: "요청")
                            lane == collabLane ->
                                CalRegionBar(
                                    BarSeg.SINGLE, AppTheme.colors.categoryBg, AppTheme.colors.categoryText,
                                    collabRegion ?: "협업"
                                )
                            else -> {
                                val bar = cell.bars.firstOrNull { it.lane == lane }
                                if (bar != null) {
                                    val bg = if (bar.past) AppTheme.colors.surfaceMuted else AppTheme.colors.doneBg
                                    val fg = if (bar.past) TossTextSecondary else AppTheme.colors.doneText
                                    CalRegionBar(bar.seg, bg, fg, bar.label)
                                } else {
                                    // 빈 lane — 위 칸과 세로 위치를 맞춰 여러날 띠가 가로로 이어지게.
                                    Box(Modifier.fillMaxWidth().height(13.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        // 간단 일정(번호 없는 메모형)은 점 그대로 — 적을 지역명이 없어서 띠로 만들 게 없다.
        if (isSimple) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp)
                    .size(5.dp).clip(CircleShape).background(TossTextTertiary)
            )
        }
    }
}

/** 응답 대기 협업 막대 — 주황 + 은은한 깜빡임(알파 0.3↔1.0). "뭐지?" 하고 눈길 가서 탭하게. (2026-07-08 사장님)
 *   pending 칸에서만 렌더 → 애니메이션도 그 칸만 (전체 42칸 부하 없음). */
@Composable
private fun PendingCalBar(label: String) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "pendingCollabPulse")
    val alpha by pulse.animateFloat(
        // 연한 바탕이라 0.3 까지 내리면 아예 안 보인다. 0.45 부터.
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(750),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pendingCollabAlpha"
    )
    CalRegionBar(BarSeg.SINGLE, AppTheme.colors.cautionBg.copy(alpha = alpha), AppTheme.colors.cautionText, label)
}

/** 캘린더 막대 한 줄 — SINGLE=가운데 16dp 알약, 여러날 START/MID/END=칸 가득(가로로 이어짐). */
@Composable
private fun CalBar(seg: BarSeg, color: Color) {
    val shape = calBarShape(seg)
    if (seg == BarSeg.SINGLE) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(16.dp).height(4.dp).clip(shape).background(color))
        }
    } else {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(shape).background(color))
    }
}

/**
 * 달력 칸 띠 한 줄 — **색=종류, 글자=어디**. (2026-09-22 사장님 "지역명 정도")
 *   연한 바탕 + 진한 글자. 진한 띠가 세 줄 쌓이면 달력이 답답해지고 글자도 덜 읽힌다(레퍼런스).
 *   여러 날 시공은 첫날에만 글자가 오고(label != null) 이어지는 날은 같은 높이 빈 띠라
 *   막대가 가로로 끊기지 않는다.
 */
@Composable
private fun CalRegionBar(seg: BarSeg, bg: Color, fg: Color, label: String?) {
    val shape = calBarShape(seg)
    Box(
        Modifier.fillMaxWidth().height(13.dp).clip(shape).background(bg),
        contentAlignment = Alignment.CenterStart
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                label,
                color = fg,
                fontSize = CAL_REGION_TEXT_SP,
                lineHeight = CAL_REGION_TEXT_SP,   // 테마 lineHeight(24sp)를 물려받으면 띠 안에서 글자가 잘린다
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 3.dp)
            )
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

/**
 * 앞으로의 일정 — 고른 날이 비었을 때 그 자리에.
 *
 * 왜: 달력은 "언제 비었나" 를 답하고, 이 목록은 "뭐가 있나" 를 답한다.
 *     전엔 둘째 질문의 답이 **날짜를 눌러야만** 나왔다.
 * 시공과 협업을 **같이** 센다 — 협업만 있는 날에 "없어요" 라고 하면 거짓말이다.
 */
@Composable
private fun UpcomingSection(
    jobs: List<CustomerEntity>,
    collab: List<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite>,
    todayStart: Long,
    onOpenDay: (Long) -> Unit
) {
    data class Up(val ms: Long, val who: String, val detail: String, val collabRow: Boolean)

    val rows = remember(jobs, collab, todayStart) {
        val out = ArrayList<Up>()
        jobs.forEach { c ->
            val d = c.scheduledWorkDate ?: return@forEach
            if (d < todayStart || c.isWorkDone) return@forEach
            val who = c.name?.takeIf { it.isNotBlank() }
                ?: com.detailline.callfollowcrm.util.PhoneNumberFormatter.format(c.phoneNumber)
            val t = c.scheduledWorkMinutes?.let { DateTimeUtils.formatWorkMinutes(it) }
            val addr = c.address?.trim()?.takeIf { it.isNotBlank() }
            out += Up(d, who + "님", listOfNotNull(t, addr).joinToString(" \u00b7 "), false)
        }
        collab.forEach { sct ->
            val d = sct.scheduledAtMs
            if (d <= 0L || DateTimeUtils.startOfDay(d) < todayStart) return@forEach
            val who = sct.ownerName.takeIf { it.isNotBlank() }?.let { "\ud611\uc5c5 \u00b7 ${'$'}it\uc0ac\uc7a5\ub2d8" } ?: "\ud611\uc5c5 \ud604\uc7a5"
            val addr = sct.addr?.trim()?.takeIf { it.isNotBlank() } ?: sct.title
            out += Up(d, who, listOfNotNull(sct.timeLabel?.takeIf { it.isNotBlank() }, addr).joinToString(" \u00b7 "), true)
        }
        out.sortedBy { it.ms }.take(5)
    }
    if (rows.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            "\uc55e\uc73c\ub85c\uc758 \uc77c\uc815",
            style = AppType.label, color = TossTextSecondary,
            modifier = Modifier.padding(start = 2.dp, bottom = 9.dp)
        )
        rows.forEach { r ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(bottom = 8.dp)
                    // 앱의 다른 카드와 **같은 옷** — 그림자가 없으면 배경에 붕 떠서
                    //   "왜 밖으로 빠져있어?" 로 보인다. (2026-09-21 사장님)
                    .tossCardShadow(AppShape.lg)
                    .clip(AppShape.lg)
                    .background(Color.White)
                    .clickable { onOpenDay(DateTimeUtils.startOfDay(r.ms)) }
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.width(3.dp).height(30.dp).clip(AppShape.sm)
                        .background(if (r.collabRow) AppTheme.colors.category else TossSuccess)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.who, style = AppType.body, fontWeight = FontWeight.Bold, color = TossTextPrimary, maxLines = 1)
                    if (r.detail.isNotBlank()) {
                        Text(r.detail, style = AppType.caption, color = TossTextSecondary, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    DateTimeUtils.formatScheduledDate(r.ms),
                    style = AppType.caption, fontWeight = FontWeight.Bold, color = TossTextTertiary
                )
            }
        }
    }
}

@Composable
private fun DayEmpty(onAdd: () -> Unit) {
    // 프로토 day-empty
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("이 날은 시공 예약이 없어요", fontSize = 13.sp, color = TossTextInfo)
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
    selectedDayMs: Long?,
    todayStart: Long,
    assignedMembers: List<com.detailline.callfollowcrm.data.local.entity.TeamAssignmentEntity> = emptyList(),
    /** (이름, 수락됨) — 수락 안 된(pending) 협업은 "요청 중"으로 표시. (2026-07-09) */
    collabPartnerNames: List<Pair<String, Boolean>> = emptyList(),
    teamAvailable: Boolean = false,
    onAssign: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onCall: (String) -> Unit = {},
    onClick: () -> Unit
) {
    val scheduled = customer.scheduledWorkDate ?: return
    val s = DateTimeUtils.startOfDay(scheduled)
    val isPast = s < todayStart
    val totalDays = customer.scheduledWorkDays.coerceAtLeast(1)
    val dayN = selectedDayMs?.let { ((it - s) / DateTimeUtils.DAY_MS).toInt() + 1 }?.coerceIn(1, totalDays) ?: 1
    val addr = com.detailline.callfollowcrm.util.AddressExtractor.tidyAddress(customer.address)
    // 이름·일차는 주소 밑 작은 줄.
    //   ⚠️ 이름이 없으면 **번호**라도 적는다. 안 그러면 주소도 이름도 없는 손님은 카드에
    //      "주소 미입력" 한 줄만 남아 **누군지 알 수가 없다.** (2026-09-22 폰에서 확인)
    //      번호를 제목 자리에서 뺀 것과 다른 얘기다 — 여기는 작은 줄이다.
    val who = listOfNotNull(
        customer.name?.takeIf { it.isNotBlank() }
            ?: PhoneNumberFormatter.format(customer.phoneNumber).takeIf { it.isNotBlank() },
        if (totalDays > 1) "${totalDays}일 중 ${dayN}일차" else null
    ).joinToString(" · ")
    // 초록 = 끝난 것 · 회색 = 그냥 지나간 것 · 파랑 = 앞으로 올 것. (2026-09-20 사장님)
    //   며칠 남았는지는 날짜 칸이 말해주지만 **끝낸 건지 아닌지는 카드만 안다** → 딱지는 남긴다.
    val isDone = customer.workCompletedAt != null
    val tagText = if (isDone) "완료" else if (isPast) "지남" else DateTimeUtils.dDayLabel(scheduled)
    val tagBg = when {
        isDone -> AppTheme.colors.doneBg
        isPast -> TossGrayBg
        else -> AppTheme.colors.primaryBg
    }
    val tagFg = when {
        isDone -> AppTheme.colors.doneText
        isPast -> TossTextTertiary
        else -> AppTheme.colors.primaryText
    }

    TossCard(onClick = onClick) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // 몇 시에 — 시각을 안 정해둔 시공이면 이 칸은 빈 채로 둔다. (끝 시각은 앱에 없다)
                Column(Modifier.width(56.dp)) {
                    customer.scheduledWorkMinutes?.let {
                        Text(
                            DateTimeUtils.formatWorkMinutes(it),
                            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (isPast) TossTextTertiary else TossTextPrimary
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                // 달력 막대와 **같은 색**. 지난 건 회색.
                Box(
                    Modifier.width(3.dp).fillMaxHeight().clip(AppShape.sm)
                        .background(if (isPast) Color(0xFFC2C9D2) else AppTheme.colors.done)
                )
                Spacer(Modifier.width(11.dp))
                // 주소가 주인공 — 일정 탭에서 묻는 건 "어디로 몇 시에 가지?" 하나다. (2026-09-22 사장님)
                Column(Modifier.weight(1f)) {
                    Text(
                        addr.takeIf { it.isNotBlank() } ?: "주소 미입력",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (isPast) TossTextSecondary else TossTextPrimary,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (who.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            who, fontSize = 12.5.sp, color = TossTextTertiary, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    // 주소를 보는 가장 큰 이유가 "가야 해서"인데 갈 방법이 없었다. (2026-09-22 사장님)
                    Spacer(Modifier.height(11.dp))
                    Row {
                        if (addr.isNotBlank()) {
                            GoBtn("길찾기", primary = true) { onNavigate(addr) }
                            Spacer(Modifier.width(6.dp))
                        }
                        // 카드를 누르면 **문자**, 이 버튼은 **전화** — 둘이 겹치지 않는다.
                        if (customer.phoneNumber.isNotBlank()) {
                            GoBtn("전화", primary = false) { onCall(customer.phoneNumber) }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(AppShape.sm).background(tagBg)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(tagText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = tagFg)
                }
            }
            // 🗑 뺀 것 (2026-09-22 사장님): 번호 제목줄 · ✨ 문자 요약 · 총액/계약금/잔금.
            //    번호는 눌러서 문자로 가니 제목 자리 값이 아니고, 요약은 대화방에, 돈은 정산 탭에 있다.
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
                        AssignBtn("팀원·일당 배정", filled = true, onClick = onAssign)
                    } else {
                        if (assignedMembers.isNotEmpty()) {
                            AssignAvatars(assignedMembers)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            // 수락된 협업 = "🤝 이름", 아직 수락 안 된(pending) = "🤝 이름 · 요청 중". (2026-07-09 사장님)
                            (assignedMembers.map { it.memberName } +
                                collabPartnerNames.map { (nm, acc) -> if (acc) "$nm" else "$nm · 요청 중" }).joinToString(", "),
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

/** [길찾기] 파랑 채움 / [전화] 회색 채움. 둘 다 둥근 네모 — 알약은 '고르는 칩'에만. */
@Composable
private fun GoBtn(label: String, primary: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
        color = if (primary) Color.White else TossTextSecondary,
        modifier = Modifier
            .clip(AppShape.sm)
            .background(if (primary) TossBlue else TossGrayBg)
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 8.dp)
    )
}

/** 시스템 전화 앱을 연다. ACTION_DIAL 은 권한이 필요 없고, 저절로 걸리지도 않는다. */
private fun dialFromSchedule(context: android.content.Context, phoneNumber: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_DIAL,
                android.net.Uri.parse("tel:$phoneNumber")
            )
        )
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
            // 버튼은 둥근 네모. 알약은 '고르는 칩'에만. (2026-09-20 사장님)
            .clip(RoundedCornerShape(10.dp))
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
    Color(0xFFE6EFFF) to LightColors.primary,
    LightColors.doneBg to Color(0xFF16A765),
    LightColors.unpaidBg to LightColors.unpaid,
    LightColors.categoryBg to LightColors.category,
    LightColors.cautionBg to Color(0xFFE0920C),
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
            row.isPaidOff -> Triple("", "전액 완납", TossSuccess)
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
/** 달력 한 칸에 그리는 막대 줄 수 상한 (lane 0~2 = 최대 3줄). 칸 렌더러와 반드시 같은 값. */
// 막대가 13dp 띠가 되면서 3줄은 칸이 너무 커진다 → 2줄(lane 0~1).
private const val CAL_MAX_LANE = 2

/** 달력 막대 모서리 — 여러 날 시공이 가로로 이어져 보이게 끝만 둥글린다. CalBar/CalRegionBar 공용. */
private fun calBarShape(seg: BarSeg) = when (seg) {
    BarSeg.SINGLE -> RoundedCornerShape(3.dp)
    BarSeg.START -> RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)
    BarSeg.END -> RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
    BarSeg.MID -> RoundedCornerShape(0.dp)
}

/** 달력 칸 지역명 글자 크기 — 46dp 칸에 2~3글자가 들어가는 한계값. 여기 한 곳에서만 정한다. */
private val CAL_REGION_TEXT_SP = 8.5.sp

/** "방금 · 35건" / "오후 2:10 · 35건" / "어제 · 35건" — 버튼 밑 한 줄. (2026-09-15 사장님) */
private fun lastSyncLabel(atMs: Long, count: Int): String {
    // 아직 한 번도 안 올렸으면 시각이 없다 — "방금" 이라고 거짓말하지 않는다. (2026-09-16)
    if (atMs <= 0L) return "아직"
    val diff = System.currentTimeMillis() - atMs
    val when_ = when {
        diff < 60_000L -> "방금"
        diff < 60 * 60_000L -> "${diff / 60_000L}분 전"
        DateTimeUtils.startOfDay(atMs) == DateTimeUtils.startOfDay(System.currentTimeMillis()) ->
            java.text.SimpleDateFormat("a h:mm", java.util.Locale.KOREA).format(java.util.Date(atMs))
        else -> java.text.SimpleDateFormat("M/d", java.util.Locale.KOREA).format(java.util.Date(atMs))
    }
    return if (count > 0) "$when_ · ${count}건" else when_
}

private enum class BarSeg { SINGLE, START, MID, END }
private data class DayBar(
    val lane: Int,
    val seg: BarSeg,
    val past: Boolean,
    /** 칸에 적을 **지역명**. 여러 날 시공은 **첫날만** 채운다(날마다 반복하면 지저분). */
    val label: String? = null
)

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
private fun assignScheduleLanes(schedules: List<CustomerEntity>): Map<String, Int> {
    val intervals = schedules.mapNotNull { c ->
        val s = c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } ?: return@mapNotNull null
        val days = c.scheduledWorkDays.coerceAtLeast(1)
        Triple(laneKeyOf(c), s, s + (days - 1) * DateTimeUtils.DAY_MS)
    }.sortedWith(compareBy({ it.second }, { -(it.third - it.second) }))
    val laneEnds = ArrayList<Long>() // lane -> 그 lane 에 마지막으로 들어간 시공의 끝 ms
    val map = HashMap<String, Int>()
    for ((key, s, e) in intervals) {
        var lane = laneEnds.indexOfFirst { it < s }
        if (lane < 0) { laneEnds.add(e); lane = laneEnds.size - 1 } else laneEnds[lane] = e
        map[key] = lane
    }
    return map
}

/**
 * 시공 '건'의 키 = (고객, 시공일). 한 고객이 여러 날짜를 잡을 수 있으므로(재방문 Phase2 Stage A, DB v49)
 *   고객 id 만으론 서로 다른 건이 구분되지 않는다 — 목록 key·달력 lane 모두 이 키를 쓴다. (2026-09-11 사장님)
 */
private fun laneKeyOf(c: CustomerEntity): String =
    "${c.id}-${c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } ?: 0L}"

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

/** 이 A/S 예약이 dayStart 날을 포함하는가 — 여러 날 A/S(asScheduledDays) 고려. 시공과 별개. (DB v43) */
private fun asCoversDay(c: CustomerEntity, dayStart: Long): Boolean {
    val start = c.asScheduledDate ?: return false
    val s = DateTimeUtils.startOfDay(start)
    val days = c.asScheduledDays.coerceAtLeast(1)
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

    // ⚠️ lane 은 '보이는 42칸과 겹치는 시공'만으로 배정한다. (2026-09-14 사장님 신고)
    //   전체 이력(state.all)으로 배정하면 건이 쌓일수록 lane 번호가 계속 커지는데,
    //   칸 렌더러는 3줄(lane 0~2)까지만 그린다 → lane 3 이상이 걸린 날은 막대가 통째로 사라졌다.
    //   창 단위로 배정하면 번호가 작게 유지되고, 42칸 × 전체목록 필터링도 안 하게 되어 더 가볍다.
    val windowStart = DateTimeUtils.startOfDay(cal.timeInMillis)
    val windowEnd = windowStart + 41 * DateTimeUtils.DAY_MS
    val visible = schedules.filter { c ->
        val s = c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } ?: return@filter false
        val e = s + (c.scheduledWorkDays.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS
        s <= windowEnd && e >= windowStart
    }
    val laneMap = assignScheduleLanes(visible)
    val cells = ArrayList<CalendarCell>(42)
    repeat(42) {
        val dayStart = DateTimeUtils.startOfDay(cal.timeInMillis)
        // 여러 날 시공은 기간 내 모든 날에 막대 표시 (scheduledWorkDays).
        val daySchedules = visible.filter { jobCoversDay(it, dayStart) }
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
            // 그래도 한 날에 4건 이상 겹치면 마지막 줄에 눌러 담는다 — 안 보이는 것보다 낫다.
            DayBar(
                lane = (laneMap[laneKeyOf(c)] ?: 0).coerceAtMost(CAL_MAX_LANE),
                seg = seg,
                past = dayStart < todayStart,
                // 글자는 첫날에만. 홈 띠에서 "동대문" 뽑을 때 쓰는 그 함수를 그대로 쓴다.
                label = if (seg == BarSeg.SINGLE || seg == BarSeg.START)
                    com.detailline.callfollowcrm.util.RegionName.shortRegion(c.address) else null
            )
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
    siteAddress: String?,
    members: List<com.detailline.callfollowcrm.ai.TeamRepository.TeamMember>,
    collabPartners: List<com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity>,
    assignedCollabPhones: Set<String>,
    initiallySelected: Set<String>,
    initialMemo: String,
    defaultStartHour: Int,
    /** 공사가 걸친 날들(startOfDay). 2개 이상이면 '일하는 날' 칩 노출. 협업자별로 오는 날 선택. (2026-08-02) */
    jobDayStarts: List<Long> = emptyList(),
    onAddTeamMember: (name: String, phone: String) -> Unit,
    onAddWorker: (name: String, phone: String, wageManwon: Int?) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Set<String>, String) -> Unit,
    onInviteCollab: (phone: String, force: Boolean, memo: String, dailyWage: Int?, startHour: Int, address: String?, days: List<Long>) -> Unit,
    onCancelCollab: (phone: String) -> Unit,
    /** 이 시공에 이미 배정된 '내가 부른 일당' (workerId → 일당 원). */
    crewWagesByWorkerId: Map<Long, Long> = emptyMap(),
    /** 일당 배정 통째 저장 (workerId → 일당 원). 고른 사람만 넘김 = 나머지는 해제. */
    onSaveCrew: (Map<Long, Long>) -> Unit = {}
) {
    fun key(phone: String) = phone.filter { it.isDigit() }.takeLast(8)
    // 이미 요청한 일당사장 = 처음부터 '선택됨'으로 보여줌(요청함). 해제하면 취소.
    val reqKeys = remember(assignedCollabPhones) { assignedCollabPhones.map { key(it) }.filter { it.isNotEmpty() }.toSet() }
    var selectedWorkers by remember { mutableStateOf(initiallySelected) }
    // 협업만 남김 (2026-07-17 사장님 "내가 부른 일당 안 씀 → 협업만"). 이미 요청 보낸 사람만 처음부터 선택.
    //   ⚠️ '내가 부른 일당'(JobCrew) 데이터는 지우지 않고 그대로 둔다(정산 이력 보존·되돌리기 가능) — 이 시트에서
    //      만들지/건드리지 않을 뿐. 그래서 crewKeys 프리선택·onSaveCrew 호출을 뺐다.
    var selectedPartners by remember { mutableStateOf(reqKeys) }   // 협업 사장 phone last8 키
    // 일당사장별 일당(만원, 문자열) — 저장값 자동 채움, 사장님이 이 현장만 바꿀 수 있음.
    var partnerWages by remember {
        mutableStateOf(
            collabPartners.associate { p -> key(p.phone) to (p.wage?.takeIf { it > 0 }?.let { (it / 10000).toString() } ?: "") }
        )
    }
    var memo by remember { mutableStateOf(initialMemo) }
    var startHour by remember { mutableStateOf(defaultStartHour) }
    // '일하는 날' — 협업자가 오는 날(startOfDay). 기본 전체 켜짐(기존 동작 유지). 다일 공사에서만 UI 노출. (2026-08-02)
    var selectedDays by remember { mutableStateOf(jobDayStarts.toSet()) }
    val multiDayJob = jobDayStarts.size > 1
    // 시트 안 "+추가" 인라인 폼 (한 번에 하나만 열림)
    var addTeamOpen by remember { mutableStateOf(false) }
    var addWorkerOpen by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newWage by remember { mutableStateOf("") }
    // 협업 보낼 때 현장 주소가 없으면 여기서 입력 → 고객에 저장 + 공유에 사용. (2026-06-20 사장님)
    var siteAddrInput by remember { mutableStateOf("") }
    val noRipple = remember { MutableInteractionSource() }
    val assignSheetCtx = androidx.compose.ui.platform.LocalContext.current

    val anySelected = selectedWorkers.isNotEmpty() || selectedPartners.isNotEmpty()
    val totalCount = selectedWorkers.size + selectedPartners.size
    // 협업을 새로 보낼 땐 현장 주소가 꼭 있어야 함 — 없으면 상대가 못 찾고 현장명이 "협업 현장"으로 떠서. (2026-06-20 사장님)
    val invitingNewCollab = selectedPartners.any { it !in reqKeys }
    val needAddress = invitingNewCollab && siteAddress.isNullOrBlank() && siteAddrInput.isBlank()
    val purple = AppTheme.colors.category; val purpleLight = AppTheme.colors.categoryBg

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
                // 내비바+키보드를 따로 더하면(navigationBarsPadding+imePadding) 키보드 뜰 때 내비바 높이만큼
                //   이중 여백이 생김(S23U에서 입력칸과 키보드 사이 큰 틈). 둘 중 큰 값만 적용해 제거. (2026-07-01)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
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
            Text("${siteTitle}에 누구를 부를까요?  누르면 선택, 다시 누르면 취소예요.",
                fontSize = 13.sp, color = TossTextTertiary, lineHeight = 19.sp)
            Spacer(Modifier.height(18.dp))

            // ── 👷 팀원 ── (토글 + 끝에 + 추가) — 숨김(부활 가능). (2026-07-18 사장님)
            if (com.detailline.callfollowcrm.presentation.FeatureFlags.SHOW_TEAM_MEMBERS) {
            Text("팀원", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
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
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
            Spacer(Modifier.height(16.dp))
            }

            // ── 🤝 일당사장(= 협업 사장) ──
            Text("일당사장", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
            Text("누르면 부를 사장님 선택, 다시 누르면 취소. 고객 번호·대화는 안 보내요.",
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
                        // 이름/번호 유효할 때만 닫고 비우기 — 검증 실패 시 입력이 증발하던 것 방지. (2026-07-30)
                        if (newName.trim().isNotBlank() && newPhone.filter { c -> c.isDigit() }.length >= 9) {
                            addWorkerOpen = false; newName = ""; newPhone = ""; newWage = ""
                        }
                    }
                )
            }

            // ── 공통 출근시간 · 전달 메모 · 선택한 일당사장별 일당 (선택된 사람 있을 때만) ──
            if (anySelected) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                Spacer(Modifier.height(14.dp))
                // 현장 주소 — 협업 보낼 땐 필수. 고객에 주소 없을 때만 노출(있으면 자동 사용). (2026-06-20 사장님)
                if (invitingNewCollab && siteAddress.isNullOrBlank()) {
                    SheetFieldLabel("현장 주소 (협업엔 꼭 필요해요)")
                    SheetTextField(
                        siteAddrInput, { siteAddrInput = it },
                        placeholder = "예: 인천 미추홀구 매소홀로 137", singleLine = false, minHeightDp = 50
                    )
                    Text("주소가 있어야 상대 사장님이 길찾기로 찾아가고, 현장 이름도 주소로 떠요. 한 번 넣으면 이 고객에 저장돼요.",
                        fontSize = 11.5.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                    Spacer(Modifier.height(14.dp))
                }
                // '일하는 날' — 다일 공사에서만. 협업자가 오는 날만 켜기(기본 전체). 그 날에만 🤝 + 상대에게 그 날짜로. (2026-08-02)
                if (multiDayJob) {
                    SheetFieldLabel("일하는 날")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        jobDayStarts.forEach { dayMs ->
                            val on = dayMs in selectedDays
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = dayMs }
                            val md = "${cal.get(java.util.Calendar.MONTH) + 1}/${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
                            val dow = arrayOf("일", "월", "화", "수", "목", "금", "토")[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp))
                                    .background(if (on) purple else TossGrayBg)
                                    // 마지막 1개는 못 끄게(최소 하루) — 0일 배정 방지.
                                    .clickable { selectedDays = if (on) (selectedDays - dayMs).ifEmpty { selectedDays } else selectedDays + dayMs }
                                    .padding(horizontal = 13.dp, vertical = 8.dp)
                            ) {
                                Text((if (on) "✓ " else "") + "$md($dow)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                    color = if (on) Color.White else TossTextSecondary, maxLines = 1)
                            }
                        }
                    }
                    Text("이 사장님이 오는 날만 켜두세요. (기본은 전체)",
                        fontSize = 11.5.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                    Spacer(Modifier.height(14.dp))
                }
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

                // 협업 사장별 — 전달할 일당(만원). 협업만 남겨 모드 선택은 없앴다. (2026-07-17 사장님)
                val selectedPartnerList = collabPartners.filter { key(it.phone) in selectedPartners }
                if (selectedPartnerList.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SheetFieldLabel("협업 사장님 (일당 전달용)")
                    selectedPartnerList.forEach { p ->
                        val k = key(p.phone)
                        val wasReq = k in reqKeys
                        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Text(p.name, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (wasReq) "이미 요청 보냈어요. 상대 사장님이 수락하면 협업 현장이 돼요."
                                else "상대 사장님께 협업 요청을 보내요. 수락해야 성립하고, 정산엔 안 잡혀요.",
                                fontSize = 11.5.sp, color = TossTextTertiary, lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("일당 (전달용)",
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
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
            }

            // ── 버튼 하나: ○명에게 보내기 / (원래 배정 있었는데 다 빼면) 배정 모두 취소 ──
            //   (2026-06-14 사장님: 둘 다 빼고 저장하려는데 버튼이 비활성이라 취소 저장이 안 됐음.)
            val hadInitial = initiallySelected.isNotEmpty() || reqKeys.isNotEmpty()
            val isCancelAll = !anySelected && hadInitial   // 원래 있던 걸 다 뺀 상태 = 취소 저장
            val canSubmit = anySelected || isCancelAll
            // 보낸 협업을 빼는(취소) 게 있으면 저장 전에 "정말 취소?" 한 번 물어봄. (2026-06-20 사장님)
            val cancelling = reqKeys.any { it !in selectedPartners }
            var confirmCancel by remember { mutableStateOf(false) }
            val submit: () -> Unit = {
                val addrToSend = siteAddrInput.trim().takeIf { it.isNotBlank() }
                // 팀원 배정 저장(있거나 원래 있었으면 — 비우기 포함). 메모 공통.
                if (selectedWorkers.isNotEmpty() || initiallySelected.isNotEmpty()) onSave(selectedWorkers, memo)
                // 고른 사람은 전부 협업 요청(서버 초대, 상대 수락 필요). '내가 부른 일당'은 없앴다. (2026-07-17 사장님)
                //   ⚠️ onSaveCrew 는 호출하지 않는다 — 기존 JobCrew(일당) 데이터를 건드리지 않고 그대로 보존.
                // 일하는 날 — 전체 선택(또는 단일일 공사)이면 빈 리스트(=전체, 하위호환), 일부만이면 그 날들만. (2026-08-02)
                val daysToSend = if (!multiDayJob || selectedDays.size >= jobDayStarts.size) emptyList()
                                 else selectedDays.toList().sorted()
                selectedPartners.forEach { k ->
                    if (k in reqKeys) return@forEach                       // 이미 보낸 요청은 그대로
                    val p = collabPartners.firstOrNull { key(it.phone) == k } ?: return@forEach
                    val manwon = partnerWages[k]?.toIntOrNull() ?: 0
                    onInviteCollab(p.phone, false, memo, manwon.takeIf { it > 0 }, startHour, addrToSend, daysToSend)
                }
                // 보냈던 요청을 뺐으면 협업 요청 취소(서버에도 알림).
                reqKeys.forEach { k ->
                    if (k !in selectedPartners) collabPartners.firstOrNull { key(it.phone) == k }?.let { p ->
                        onCancelCollab(p.phone)
                    }
                }
                onDismiss()
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            anySelected -> TossBlue
                            isCancelAll -> AppTheme.colors.unpaidBg   // 취소(빼기) — 연한 빨강
                            else -> Color(0xFFE2E6EC)
                        }
                    )
                    .clickable(enabled = canSubmit) {
                        // 협업 보내는데 주소가 없으면 막고 안내(아래 주소칸 입력 유도). (2026-06-20 사장님)
                        if (needAddress) {
                            android.widget.Toast.makeText(assignSheetCtx, "현장 주소를 먼저 넣어주세요 — 협업엔 주소가 꼭 필요해요", android.widget.Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        // 보낸 협업을 빼는 게 있으면 "정말 취소?" 먼저, 없으면 바로 저장. (2026-06-20 사장님)
                        if (cancelling) confirmCancel = true else submit()
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        anySelected -> "${totalCount}명에게 보내기"
                        isCancelAll -> "배정 모두 취소"
                        else -> "부를 사람을 골라주세요"
                    },
                    color = when {
                        anySelected -> Color.White
                        isCancelAll -> TossError
                        else -> TossTextTertiary
                    },
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
                )
            }

            // 보낸 협업 취소 확인 — "정말 취소?" (상대에 알림 감). (2026-06-20 사장님)
            if (confirmCancel) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmCancel = false },
                    title = { Text("보낸 협업을 취소할까요?", fontWeight = FontWeight.Bold) },
                    text = { Text("요청을 뺀 사장님께 '협업이 취소됐어요' 알림이 가요. (아직 수락 전이면 조용히 빠져요)") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmCancel = false; submit() }) {
                            Text("취소하기", color = TossError, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmCancel = false }) {
                            Text("그대로 둘게요", color = TossTextSecondary)
                        }
                    }
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppTheme.colors.bg).padding(14.dp)
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
