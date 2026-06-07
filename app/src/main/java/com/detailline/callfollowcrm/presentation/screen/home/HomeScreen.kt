package com.detailline.callfollowcrm.presentation.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.presentation.component.TossBadge
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.TossChip
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
import com.detailline.callfollowcrm.presentation.theme.TossWarning
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import com.detailline.callfollowcrm.presentation.theme.CallFollowCrmTheme
import androidx.compose.ui.tooling.preview.Preview
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import com.detailline.callfollowcrm.util.MoneyFormatter
import androidx.compose.material.icons.filled.ChevronRight

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    /** 카드 탭 인라인 액션의 [💬 메시지] / [✨ AI] → ChatScreen. customerId 있으면 빠른 로드용. */
    onOpenChat: (phone: String, customerId: Long?) -> Unit,
    /** 카드 탭 인라인 액션의 [ⓘ 고객 카드] → CustomerDetail. */
    onOpenCustomerDetail: (customerId: Long) -> Unit,
    /** FAB "수동 입력" 전용 — 번호 직접 타이핑하는 FollowUp 화면. */
    onOpenManualEntry: () -> Unit,
    onOpenSchedule: () -> Unit,
    onAddSchedule: () -> Unit = {},
    onOpenTemplates: () -> Unit,
    onOpenAiMessage: () -> Unit,
    onOpenStyleLearning: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 상담함 앱바 🔍 → 검색 화면 (2026-06-01 전면 리뉴얼). */
    onOpenSearch: () -> Unit = {},
    /** 상담함 앱바 👤 → 고객 관리 목록 (2026-06-01 전면 리뉴얼). */
    onOpenCustomers: () -> Unit = {},
    onOpenNewLeads: () -> Unit = {},
    /** 홈 "미수금" 카드 탭 → 정산 화면. 정산 Phase 1 (2026-06-01). */
    onOpenSettlement: () -> Unit,
    /** 홈 "정기문자 보낼 때 됐어요" 카드 탭 → 보낼 정기문자 목록. (2026-06-01) */
    onOpenRecurringDue: () -> Unit = {},
    /** 홈 "시공 안내 문자" 카드 탭 → D-1/도착 안내 목록. (2026-06-01) */
    onOpenScheduleReminder: () -> Unit = {},
    /** 홈 "견적 회신 챙기기" 카드 탭 → 견적 회신 리마인드 목록. (2026-06-01) */
    onOpenEstimateFollowup: () -> Unit = {},
    /** 홈 "팀원 출발" 배너 탭 → 팀 관리(출발 현황). (2026-06-06) */
    onOpenTeam: () -> Unit = {},
    /** 상담함 "부재중 자동답장" 알림 길게누름 → 자동 문자 설정(부재중 응답 펼침). (2026-06-06) */
    onOpenAutoSmsSettings: () -> Unit = {}
) {
    val timeline by viewModel.timeline.collectAsState()
    val filter by viewModel.filterState.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val aiCardSummaries by viewModel.cardSummariesByPhoneSuffix.collectAsState()
    val waitingReplies by viewModel.waitingReplies.collectAsState()
    val categoriesById by viewModel.categories.collectAsState()
    val todayNew by viewModel.todayNewInquiryCount.collectAsState()
    val yesterdayNew by viewModel.yesterdayNewInquiryCount.collectAsState()
    val unhandled by viewModel.unhandledCount.collectAsState()
    val weekScheduled by viewModel.thisWeekScheduledCount.collectAsState()
    val outstandingTotal by viewModel.outstandingTotal.collectAsState()
    val outstandingCount by viewModel.outstandingCount.collectAsState()
    val todayJobs by viewModel.todayJobs.collectAsState()
    val nextJobs by viewModel.nextJobs.collectAsState()
    val autoReplies by viewModel.autoReplies.collectAsState()
    val teamUpdates by viewModel.teamUpdates.collectAsState()
    val recurringDueCount by viewModel.recurringDueCount.collectAsState()
    val scheduleReminders by viewModel.scheduleReminders.collectAsState()
    val estimateFollowupCount by viewModel.estimateFollowupCount.collectAsState()
    val estimateFollowupDismissed by viewModel.estimateFollowupDismissed.collectAsState()
    val recurringDueDismissed by viewModel.recurringDueDismissed.collectAsState()
    val isInitialSmsLoading by viewModel.isInitialSmsLoading.collectAsState()

    // 서버 상태 indicator — AppContainer 의 ServerHealthMonitor 를 직접 구독.
    // 30초마다 GET /health 호출 → 결과 반영. 사장님만 알아볼 작은 동그라미. tap = Toast 안내.
    val context = LocalContext.current
    val serverHealth = remember {
        (context.applicationContext as CallFollowCrmApplication).container.serverHealth
    }
    val serverAlive by serverHealth.alive.collectAsState()
    val lastOkAtMs by serverHealth.lastOkAtMs.collectAsState()

    // 화면 진입 시 SMS 연락처 새로고침 — Settings 토글 켜고 돌아왔거나 새 SMS 받았을 수 있어서.
    // + CallLog → Room sync (2026-05-28 사장님 통점):
    //   Android 12+ / OneUI 가 정적 BroadcastReceiver 누락하면 통화 종료 감지 못 함 → 진입 시 폴링으로 보완.
    LaunchedEffect(Unit) {
        viewModel.refreshSmsContacts()
        viewModel.syncRecentCallLog(context)
    }

    // 뒤로가기 UX (2026-05-25 사장님 결정):
    //   1) 필터 != 전체 → 전체로 복귀 (consume)
    //   2) 필터 == 전체 → "한 번 더 누르면 종료" Toast → 2초 안 두 번째 = 앱 종료
    val activity = remember(context) { context.findHomeActivityOrNull() }
    var lastBackAt by remember { mutableStateOf(0L) }
    BackHandler(enabled = filter !is HomeFilter.All) {
        viewModel.setFilter(HomeFilter.All)
    }
    BackHandler(enabled = filter is HomeFilter.All) {
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 2000) {
            activity?.finish()
        } else {
            lastBackAt = now
            android.widget.Toast.makeText(
                context, "한 번 더 누르면 종료됩니다", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 미확인 swipe-to-spam Snackbar Undo.
    val snackbarHostState = remember { SnackbarHostState() }

    // 오늘 시공 히어로 [완료] → 프로토 openComplete 팝업 (시공 완료 · 고생하셨습니다).
    var completeTarget by remember { mutableStateOf<com.detailline.callfollowcrm.data.local.entity.CustomerEntity?>(null) }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            // 프로토 .appbar — "상담함" + 오늘 날짜 + AI 배지(서버 상태 흡수).
            //   기존 달력/문서/설정 아이콘은 하단 5탭(일정·더보기)이 대체 → 프로토대로 제거.
            val todayLabel = remember {
                java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREAN)
                    .format(java.util.Date())
            }
            // 프로토 renderAiBadge: "{대표 업종} AI". 업종 미선택 시 "줄눈" fallback.
            val ownerTrade = remember {
                (context.applicationContext as CallFollowCrmApplication).container.preferences
                    .ownerTrades.firstOrNull()?.takeIf { it.isNotBlank() } ?: "줄눈"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TossGrayBg)
                    .statusBarsPadding()
                    // top 여백 ↑ — 엣지투엣지 OFF 라 statusBarsPadding=0, 제목이 상태바에 붙던 것 완화(2026-06-04).
                    .padding(start = 18.dp, end = 14.dp, top = 28.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("상담함", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.6).sp)
                    Text(todayLabel, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary)
                }
                AiBadge(
                    trade = ownerTrade,
                    alive = serverAlive,
                    onClick = {
                        val msg = when (serverAlive) {
                            true -> {
                                val secs = lastOkAtMs?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
                                "✨ $ownerTrade 전문 AI · 서버 연결 정상 (${secs}초 전)"
                            }
                            false -> "AI 서버 연결 실패 — Tailscale 확인하세요"
                            null -> "AI 서버 상태 체크 중..."
                        }
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(Modifier.width(6.dp))
                // 프로토 상담함 앱바 👤 고객 · 🔍 검색 — 흰 원형 아이콘 버튼.
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.White)
                        .clickable { onOpenCustomers() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, "고객", tint = TossTextSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.White)
                        .clickable { onOpenSearch() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Search, "검색", tint = TossTextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        },
        // 프로토 상담함엔 수동 입력 FAB 없음 — 2026-06-02 사장님 요청으로 제거.
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
        ) {
            // 2026-05-28 사장님 통점 fix: 앱 첫 진입 시 SMS 풀스캔 (10000건) 가 수 초 걸려
            //   "처음엔 옛 통화만, 잠시 후 SMS 카드 스르륵 추가" 깜빡임 인지.
            //   해결: 첫 풀스캔 emit 전까지 얇은 LinearProgressIndicator 표시. 풀스캔 끝나면 사라짐.
            //   scanLimit 자체는 NEXT_SESSION_TODO 🚫 룰로 줄이지 않음 (17000건 환경 검증).
            if (isInitialSmsLoading) {
                androidx.compose.material3.LinearProgressIndicator(
                    color = TossBlue,
                    trackColor = TossBlueSoft,
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                )
            }

            // 2026-06-01 프로토 1:1 — 프로토 상담함엔 필터칩(전체/미확인/카테고리)이 없음 → 제거.
            //   필터는 항상 전체. 미확인은 아래 "지금 답장 기다려요" 섹션이 담당.
            LaunchedEffect(Unit) {
                if (filter !is HomeFilter.All) viewModel.setFilter(HomeFilter.All)
            }

            // 메인 LazyColumn — KPI + 타임라인 모두 안쪽.
            //   휠 내리면 KPI 가 자연스럽게 사라짐 (사장님 2026-05-24 UX 요청, 갤메시지 패턴 벤치마킹).
            //   timeline 빈 상태도 LazyColumn item 으로 → KPI 항상 함께 보임.
            val flatItems = remember(timeline) {
                timeline.flatMap { it.items }
            }
            val listState = rememberLazyListState()

            // 카드 탭 인라인 액션 — 한 번에 하나만 펼침. key 포맷은 LazyColumn key 와 동일.
            // 회전/recompose 살아남게 rememberSaveable. null = 모두 접힘.
            var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }

            // [📍 길찾기] 첫 사용 시 네비 앱 선택 다이얼로그 (2026-05-27).
            //   non-null = 다이얼로그 떠 있는 상태. 어떤 phone 에 대해 띄웠는지 기억 → 선택 후 그 phone 의 주소 resolve + launch.
            //   사장님이 SettingsScreen 에서 미리 골랐으면 prefs.defaultNavAppKey != null → 다이얼로그 X, 즉시 launch.
            var navDialogPhone by remember { mutableStateOf<String?>(null) }
            val prefs = remember(context) {
                (context.applicationContext as CallFollowCrmApplication).container.preferences
            }
            fun launchNavigationFor(phone: String) {
                val navApp = com.detailline.callfollowcrm.util.NavApp.fromKey(prefs.defaultNavAppKey)
                if (navApp == null) {
                    navDialogPhone = phone
                } else {
                    scope.launch {
                        val addr = viewModel.resolveAddressForPhone(phone)
                        com.detailline.callfollowcrm.util.NavLauncher.launch(context, navApp, addr)
                    }
                }
            }
            // 다이얼로그 — 사장님이 [📍 길찾기] 첫 탭한 phone 에 대해서만 표시.
            //   선택 즉시 prefs 저장 + 그 phone 의 주소 resolve + launch.
            navDialogPhone?.let { pendingPhone ->
                NavAppPickerDialog(
                    onPick = { picked ->
                        prefs.defaultNavAppKey = picked.key
                        navDialogPhone = null
                        scope.launch {
                            val addr = viewModel.resolveAddressForPhone(pendingPhone)
                            com.detailline.callfollowcrm.util.NavLauncher.launch(context, picked, addr)
                        }
                    },
                    onDismiss = { navDialogPhone = null }
                )
            }

            // 대기/최근 공용 행 렌더러 — 프로토 waiting-card·recent-row 의 공통 기반(HomeRow).
            //   expandedKey/scope/콜백을 클로저로 캡처해 두 섹션에서 동일하게 호출.
            val homeItemRow: @Composable (HomeItem) -> Unit = { item ->
                val suffix = item.record.phoneNumber.filter { c -> c.isDigit() }.takeLast(8)
                val rowKey = "row-${item.record.id}-${item.record.phoneNumber}"
                val rowCategory = item.customer?.categoryId?.let { cid ->
                    categoriesById.firstOrNull { it.id == cid }
                }
                HomeRow(
                    item = item,
                    aiCardSummary = aiCardSummaries[suffix],
                    category = rowCategory,
                    expanded = expandedKey == rowKey,
                    onToggle = { expandedKey = if (expandedKey == rowKey) null else rowKey },
                    onOpenChat = { onOpenChat(item.record.phoneNumber, item.customer?.id) },
                    onOpenCustomerDetail = {
                        val existingId = item.customer?.id
                        if (existingId != null) {
                            onOpenCustomerDetail(existingId)
                        } else {
                            scope.launch {
                                val newId = viewModel.ensureCustomerForPhone(item.record.phoneNumber)
                                onOpenCustomerDetail(newId)
                            }
                        }
                    },
                    onOpenNavigation = { launchNavigationFor(item.record.phoneNumber) }
                )
            }

            // (1) 가시 카드 phone 추출 → ViewModel.onVisiblePhones. prefetcher 가 dedup 처리.
            //     key 포맷 = "row-{id}-{phone}". 다른 item (kpi/empty/spacer) 은 starts with "row-" X → 자동 필터.
            //     마우스 휠 / 빠른 fling 으로 가시 카드가 폭주성으로 토글될 때 onVisiblePhones 호출이
            //     쌓여서 ANR 가능 → debounce 250ms 로 안정화. 스크롤 멈춘 직후 한 번만 prefetch.
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            LaunchedEffect(listState, flatItems) {
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo
                        .mapNotNull { info ->
                            val key = info.key as? String ?: return@mapNotNull null
                            if (!key.startsWith("row-")) return@mapNotNull null
                            val rest = key.removePrefix("row-")
                            val dash = rest.indexOf('-')
                            if (dash < 0) return@mapNotNull null
                            rest.substring(dash + 1).takeIf { it.isNotBlank() }
                        }
                        .toSet()
                }
                    .debounce(250)
                    .distinctUntilChanged()
                    .collect { phones -> viewModel.onVisiblePhones(phones) }
            }

            // (2) 끝에서 5개 안쪽으로 보이면 loadMore. (KPI/Spacer 도 totalItemsCount 에 들어가지만 영향 미미)
            //     마우스 휠 / 빠른 fling 에서 짧은 시간에 여러 번 호출되지 않도록 lastLoadMs 가드 + 스크롤 멈춘 후 트리거.
            val shouldLoadMore by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
                    val total = info.totalItemsCount
                    total > 0 && lastVisible >= total - 5
                }
            }
            var lastLoadMoreMs by remember { mutableStateOf(0L) }
            LaunchedEffect(shouldLoadMore, flatItems.size) {
                if (!shouldLoadMore) return@LaunchedEffect
                // 스크롤 멈춘 후 한 박자 쉬고 → 짧은 시간 안 중복 호출 방지 (500ms throttle)
                kotlinx.coroutines.delay(150)
                if (!listState.isScrollInProgress) {
                    val now = System.currentTimeMillis()
                    if (now - lastLoadMoreMs > 500) {
                        lastLoadMoreMs = now
                        viewModel.loadMore()
                    }
                }
            }

            // 2026-05-26 사장님 보고 fix:
            //   "메인 화면에서 휠을 쭉 떙기면 모든 정보가 최신화" — pull-to-refresh 추가.
            //   Material3 1.2.x 패턴 (PullToRefreshContainer + nestedScrollConnection).
            val pullState = rememberPullToRefreshState()
            if (pullState.isRefreshing) {
                LaunchedEffect(Unit) {
                    runCatching { viewModel.refreshSmsContacts() }
                    // 2026-05-28: 통화 끝났는데 목록에 안 들어옴 통점 → CallLog 폴링 추가.
                    runCatching { viewModel.syncRecentCallLog(context) }
                    runCatching { serverHealth.refresh() }
                    // 시각 피드백 — 너무 빨리 끝나면 사장님이 "동작했나?" 헷갈림.
                    kotlinx.coroutines.delay(600)
                    pullState.endRefresh()
                }
            }
            // 2026-05-27 사장님 보고 fix:
            //   .fillMaxSize() 는 Column 의 chip row 영역까지 침범 → PullToRefreshContainer 의
            //   TopCenter indicator 가 chip row 와 겹쳐 회색 원처럼 보임 (디자인 깨짐).
            //   .weight(1f) + .fillMaxWidth() = Column 의 남은 공간만 차지 → indicator 가 KPI 위에 정상.
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(pullState.nestedScrollConnection)
            ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 프로토 today-new-slot — 맨 위. "오늘 신규 문의 N통 / 새 번호 기준 · 어제 M통" + ▲▼.
                item(key = "today-new") {
                    TodayNewCard(todayNew = todayNew, yesterdayNew = yesterdayNew, onClick = onOpenNewLeads)
                }

                // 오늘 시공 히어로 — 시공 당일이면 맨 위 다크 카드(주소+길찾기), 없으면 다음 시공 미리보기.
                item(key = "today-hero") {
                    TodayHeroCard(
                        todayJobs = todayJobs,
                        nextJobs = nextJobs,
                        onOpenCustomer = onOpenCustomerDetail,
                        onNavigate = { phone -> launchNavigationFor(phone) },
                        onCall = { phone -> dialHome(context, phone) },
                        onGoSchedule = onOpenSchedule,
                        onAddSchedule = onAddSchedule,
                        onComplete = { c -> completeTarget = c }
                    )
                }

                // 2026-06-01 프로토 1:1 — KPI 타일·미수금 카드는 프로토 상담함에 없음 → 제거.
                //   (미수금=정산 탭, 오늘신규=아래 today-new 카드, 미확인=지금 답장 기다려요 카운트)

                // 프로토 상담함 조건부 알림 카드 — 전부 'team-alert' 카드 언어(좌측 강조선 + 아이콘 + 제목/태그 + 부제 + go).
                //   순서 = 프로토 슬롯 순서: (pending) 견적회신 → (missed) 자동답장 → (recur) 정기문자 → (d1) 시공안내.
                //   quote/pending(접수서)·call(통화내용)·team-photo(팀)는 서버/팀 의존 → 데이터 생기면 노출(지금 숨김).

                // 견적 회신 챙기기 — 견적 보낸 지 N일 답 없는 고객 (프로토 pending 위치). 밀어서 정리=오늘 숨김.
                if (estimateFollowupCount > 0 && !estimateFollowupDismissed) {
                    item(key = "estimate-followup-card") {
                        DismissSwipeBox(onDismiss = { viewModel.dismissEstimateFollowup() }) {
                            InboxAlert(
                                accent = Color(0xFF7C5CFC), accentTint = Color(0xFFF1ECFF),
                                icon = Icons.Filled.Description,
                                title = "견적 회신 챙기기",
                                tagText = "미회신", tagBg = Color(0xFFFEF3E0), tagFg = Color(0xFFB8780A),
                                sub = "${estimateFollowupCount}곳 · 답 없는 견적",
                                goLabel = "보기",
                                onClick = onOpenEstimateFollowup
                            )
                        }
                    }
                }

                // 부재중 → 자동답장 (프로토 team-alert missed) — 한 건당 한 줄 카드.
                //   탭 → 대화 / 길게 → 자동 문자 설정(부재중 응답 펼침) / 밀어서 정리. (2026-06-06)
                autoReplies.forEach { ar ->
                    item(key = "auto-reply-${ar.id}") {
                        val arName = ar.customerName?.takeIf { it.isNotBlank() }
                            ?: PhoneNumberFormatter.format(ar.phone)
                        DismissSwipeBox(onDismiss = { viewModel.dismissAutoReply(ar.id) }) {
                            InboxAlert(
                                accent = if (ar.failed) TossError else TossBlue,
                                accentTint = if (ar.failed) Color(0xFFFDEAEF) else TossBlueSoft,
                                icon = Icons.Default.Call,
                                title = "부재중 전화에 자동 답장 보냄",
                                tagText = if (ar.failed) "실패" else null,
                                tagBg = Color(0xFFFDEAEF), tagFg = TossError,
                                sub = "$arName · " +
                                    (if (ar.failed) "발송 실패 — 직접 보내주세요" else "자동 인사 보냄") +
                                    " · " + DateTimeUtils.formatShort(ar.createdAt),
                                goLabel = "대화",
                                onClick = { onOpenChat(ar.phone, ar.customerId) },
                                onLongClick = onOpenAutoSmsSettings
                            )
                        }
                    }
                }

                // 팀원 진행 알림 (2026-06-06 사장님 요청) — 출발/도착/완료. 탭 → 팀 관리. 밀어서 정리.
                teamUpdates.forEach { up ->
                    item(key = "team-update-${up.eventId}") {
                        val (accent, tint, icon, title, verb) = teamUpdateStyle(up.kind)
                        val subText = if (up.kind == "note")
                            "${up.memberName}님 · ${up.timeLabel} · ${up.text.orEmpty()}"
                        else
                            "${up.memberName}님 · ${up.timeLabel} · ${up.place} $verb"
                        DismissSwipeBox(onDismiss = { viewModel.dismissTeamUpdate(up.eventId) }) {
                            InboxAlert(
                                accent = accent,
                                accentTint = tint,
                                icon = icon,
                                title = title,
                                tagText = null,
                                tagBg = tint, tagFg = accent,
                                sub = subText,
                                goLabel = "현장 보기",
                                // 탭 → 그 고객 카드(현장 정보: 메모·사진·일정). 고객 못 찾으면 팀 현황으로.
                                onClick = {
                                    val ph = up.customerPhone
                                    if (!ph.isNullOrBlank()) {
                                        scope.launch { onOpenCustomerDetail(viewModel.ensureCustomerForPhone(ph)) }
                                    } else {
                                        onOpenTeam()
                                    }
                                }
                            )
                        }
                    }
                }

                // 정기문자 보낼 때 됐어요 (프로토 recur). 탭 → 보낼 목록. 밀어서 정리=오늘 숨김.
                if (recurringDueCount > 0 && !recurringDueDismissed) {
                    item(key = "recurring-due-card") {
                        DismissSwipeBox(onDismiss = { viewModel.dismissRecurringDue() }) {
                            InboxAlert(
                                accent = Color(0xFFF6A609), accentTint = Color(0xFFFFF3DF),
                                icon = Icons.Filled.DateRange,
                                title = "오늘 보낼 정기 문자 ${recurringDueCount}건",
                                tagText = "확인 후 발송", tagBg = Color(0xFFFFF3DF), tagFg = Color(0xFFC9820B),
                                sub = "확인하고 한 명씩 보내기",
                                goLabel = "보기",
                                onClick = onOpenRecurringDue
                            )
                        }
                    }
                }

                // 시공 D-1 / 도착 안내 (프로토 remind-card) — 전체 문구 + [건너뛰기][문자 보낼까요?].
                scheduleReminders.forEach { rem ->
                    item(key = "reminder-${rem.item.kind}-${rem.item.customerId}") {
                        DismissSwipeBox(onDismiss = { viewModel.dismissReminder(rem.item) }) {
                        RemindCard(
                            reminder = rem,
                            onSkip = { viewModel.dismissReminder(rem.item) },
                            onSend = {
                                val ok = com.detailline.callfollowcrm.util.SmsSender
                                    .sendDirect(context, rem.item.phone, rem.body)
                                if (ok) {
                                    viewModel.markReminderSent(rem.item)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("${rem.name} 님께 보냈어요 📩", duration = SnackbarDuration.Short)
                                    }
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("문자 권한이 없어요 — 채팅에서 보내주세요", duration = SnackbarDuration.Short)
                                    }
                                    onOpenChat(rem.item.phone, rem.item.customerId)
                                }
                            }
                        )
                        }
                    }
                }

                // 프로토 상담함 본문 — "지금 답장 기다려요"(미확인) + "최근 대화"(나머지) 두 섹션.
                val waiting = flatItems.filter { it.isUnconfirmed }
                val recent = flatItems.filter { !it.isUnconfirmed }

                // 지금 답장 기다려요 — waiting-head(제목+카운트+밀어서 정리) + 카드(왼쪽 밀기=정리). 비면 막내.
                item(key = "waiting-head") { WaitingHeader(count = waiting.size) }
                if (waiting.isEmpty()) {
                    item(key = "waiting-empty") { WaitingEmptyMascot() }
                } else {
                    items(waiting, key = { "wait-${it.record.id}-${it.record.phoneNumber}" }) { item ->
                        val suffix = item.record.phoneNumber.filter { c -> c.isDigit() }.takeLast(8)
                        // 우→좌 swipe → SpamPhone 영구 마킹 + Snackbar Undo.
                        SpamSwipeBox(
                            onSpam = {
                                viewModel.markSpam(item.record.phoneNumber)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "확인함 — 미확인에서 제외돼요",
                                        actionLabel = "되돌리기",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.unmarkSpam(item.record.phoneNumber)
                                    }
                                }
                            },
                            content = {
                                val reply = waitingReplies[suffix]
                                val custName = item.customer?.name?.takeIf { it.isNotBlank() }
                                    ?: PhoneNumberFormatter.format(item.record.phoneNumber)
                                WaitingCard(
                                    item = item,
                                    aiSummary = aiCardSummaries[suffix],
                                    suggestedReply = reply,
                                    onOpenChat = { onOpenChat(item.record.phoneNumber, item.customer?.id) },
                                    onCall = { dialHome(context, item.record.phoneNumber) },
                                    onQuickSend = {
                                        val body = reply
                                        if (!body.isNullOrBlank()) {
                                            val ok = com.detailline.callfollowcrm.util.SmsSender
                                                .sendDirect(context, item.record.phoneNumber, body)
                                            if (ok) {
                                                viewModel.onWaitingReplySent(item.record.phoneNumber, body, item.customer?.id)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("$custName 님께 보냈어요 📩", duration = SnackbarDuration.Short)
                                                }
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("문자 권한이 없어요 — 채팅에서 보내주세요", duration = SnackbarDuration.Short)
                                                }
                                                onOpenChat(item.record.phoneNumber, item.customer?.id)
                                            }
                                        }
                                    }
                                )
                            }
                        )
                    }
                }

                // 최근 대화 — 프로토 recent-row: 한 흰 카드 안 줄들 + 구분선(낱개 카드 X).
                if (recent.isNotEmpty()) {
                    item(key = "recent-head") { SecSub("최근 대화") }
                    item(key = "recent-card") {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                        ) {
                            recent.forEachIndexed { index, rItem ->
                                if (index > 0) {
                                    Box(
                                        Modifier.fillMaxWidth().padding(start = 16.dp)
                                            .height(1.dp).background(TossDivider)
                                    )
                                }
                                val suffix = rItem.record.phoneNumber.filter { c -> c.isDigit() }.takeLast(8)
                                RecentRow(
                                    item = rItem,
                                    index = index,
                                    aiSummary = aiCardSummaries[suffix],
                                    onOpenChat = { onOpenChat(rItem.record.phoneNumber, rItem.customer?.id) }
                                )
                            }
                        }
                    }
                }

                item(key = "fab-spacer") { Spacer(Modifier.height(80.dp)) } // FAB 공간
            }
            // 2026-05-27 사장님 보고 fix:
            //   Material3 1.2.x PullToRefreshContainer 가 idle 일 때도 작은 회색 원으로 보이는 버그.
            //   chip row 와 겹쳐 디자인 깨짐 → 당기는 중/refreshing 일 때만 그림.
            if (pullState.isRefreshing || pullState.progress > 0f) {
                PullToRefreshContainer(
                    state = pullState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // 오늘 시공 [완료] 팝업 — 프로토 openComplete. 완료처리=닫기+스낵바, 요청=문자 발송.
            completeTarget?.let { c ->
                CompletionDialog(
                    customer = c,
                    onDismiss = { completeTarget = null },
                    onComplete = { name ->
                        completeTarget = null
                        scope.launch { snackbarHostState.showSnackbar("$name 시공을 완료 처리했어요 ✓", duration = SnackbarDuration.Short) }
                    },
                    onSend = { phone, name, body, kind ->
                        completeTarget = null
                        val ok = com.detailline.callfollowcrm.util.SmsSender.sendDirect(context, phone, body)
                        scope.launch {
                            if (ok) snackbarHostState.showSnackbar("$name 님께 $kind 발송 ✓", duration = SnackbarDuration.Short)
                            else snackbarHostState.showSnackbar("문자 권한이 없어요 — 채팅에서 보내주세요", duration = SnackbarDuration.Short)
                        }
                    }
                )
            }
            } // end Box(nestedScroll)
        }
    }
}

/**
 * KPI 4장 (2×2 그리드). LazyColumn 첫 item 으로 들어가서 스크롤 시 사라짐.
 * horizontal padding 은 LazyColumn 의 contentPadding 으로 들어가므로 안에서 X.
 */
/**
 * 오늘 시공 히어로 — 시공 당일이면 맨 위 다크 카드(고객·주소·길찾기).
 *   없으면 "다음 시공" 미리보기(1~3곳) 또는 "오늘 없음" 긍정 카드. 2026-06-01.
 */
@Composable
private fun TodayHeroCard(
    todayJobs: List<com.detailline.callfollowcrm.data.local.entity.CustomerEntity>,
    nextJobs: List<com.detailline.callfollowcrm.data.local.entity.CustomerEntity>,
    onOpenCustomer: (Long) -> Unit,
    onNavigate: (String) -> Unit,
    onCall: (String) -> Unit,
    onGoSchedule: () -> Unit,
    onAddSchedule: () -> Unit,
    onComplete: (com.detailline.callfollowcrm.data.local.entity.CustomerEntity) -> Unit
) {
    if (todayJobs.isNotEmpty()) {
        // 프로토 heroJobHtml — 다크 그라데이션 + 🟢 D-DAY + 이름·시간 + 📍주소 + [길찾기][전화][완료].
        val c = todayJobs.first()
        val name = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF272D3D), Color(0xFF14171F))))
                .clickable { onOpenCustomer(c.id) }
                .padding(20.dp)
        ) {
            // hero-top — 초록 점 + 라벨
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(TossSuccess))
                Spacer(Modifier.width(7.dp))
                Text(
                    if (todayJobs.size > 1) "오늘 시공 ${todayJobs.size}곳 · D-DAY" else "오늘 시공 · D-DAY",
                    color = Color.White.copy(alpha = 0.62f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp
                )
            }
            // hero-name — 이름 + 시간 예정
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 11.dp)) {
                Text(name, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
                c.scheduledWorkMinutes?.let { mins ->
                    Text(
                        " · ${DateTimeUtils.formatWorkMinutes(mins)} 예정",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
            // hero-addr
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 7.dp)) {
                Icon(Icons.Default.Place, null, tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    c.address?.takeIf { it.isNotBlank() } ?: "주소 미등록 — 탭해서 등록",
                    color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            // hero-btns — 길찾기(light) / 전화(ghost) / 완료(ghost)
            Row(modifier = Modifier.padding(top = 17.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroBtn("길찾기", Icons.Default.Navigation, light = true, modifier = Modifier.weight(1f)) { onNavigate(c.phoneNumber) }
                HeroBtn("전화", Icons.Default.Call, light = false, modifier = Modifier.weight(1f)) { onCall(c.phoneNumber) }
                HeroBtn("완료", Icons.Default.Check, light = false, modifier = Modifier.weight(1f)) { onComplete(c) }
            }
        }
    } else {
        // 프로토 heroEmptyHtml — 흰 카드(he-top "오늘 시공" + he-title + he-sub + he-next 회색박스 + he-add 버튼).
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(1.dp, TossDivider, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            // he-top — 📅 + "오늘 시공" (12px w800 t3)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, null, tint = TossTextTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("오늘 시공", color = TossTextTertiary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            // he-title (19px w800 t1)
            Text(
                "오늘은 예정된 시공이 없어요",
                color = TossTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp, modifier = Modifier.padding(top = 9.dp)
            )
            // he-sub (13px t2)
            Text(
                "밀린 상담·견적 챙기기 좋은 날이에요.",
                color = TossTextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
            // he-next — 회색 박스 (아이콘 + 다음 시공 정보 + chevron).
            //   2026-06-07 사장님 요청: 다음 시공 1곳이면 그 고객 카드로, 여러 곳이면 일정(캘린더)로.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TossGrayBg)
                    .clickable {
                        if (nextJobs.size == 1) onOpenCustomer(nextJobs.first().id) else onGoSchedule()
                    }
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // nx-ic (36x36 radius10 blue-tint)
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(TossBlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DateRange, null, tint = TossBlue, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    if (nextJobs.isNotEmpty()) {
                        val d = nextJobs.first().scheduledWorkDate ?: 0L
                        val word = relativeDayWord(d)
                        val n = nextJobs.size
                        if (n == 1) {
                            val j = nextJobs.first()
                            val time = j.scheduledWorkMinutes?.let { " " + DateTimeUtils.formatWorkMinutes(it) } ?: ""
                            // nx-when (11.5px w800 blue)
                            Text("다음 시공 · $word$time", color = TossBlue, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                            // nx-name (14px w700 t1)
                            Text(
                                (j.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(j.phoneNumber)) +
                                    " · " + shortAddr(j.address),
                                color = TossTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else {
                            Text(
                                "다음 시공 · $word · ${n}곳",
                                color = TossBlue, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold
                            )
                            nextJobs.forEach { j ->
                                // nx-line (13px w700) — nx-t 시간칩 + 이름·주소
                                Row(
                                    Modifier.padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    j.scheduledWorkMinutes?.let { mins ->
                                        Text(
                                            DateTimeUtils.formatWorkMinutes(mins),
                                            color = TossBlue, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp)).background(Color.White)
                                                .padding(horizontal = 7.dp, vertical = 1.dp)
                                        )
                                        Spacer(Modifier.width(7.dp))
                                    }
                                    Text(
                                        (j.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(j.phoneNumber)) +
                                            " · " + shortAddr(j.address),
                                        color = TossTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        Text("다음 예정된 시공이 없어요", color = TossBlue, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "+ 일정 직접 추가로 잡아보세요",
                            color = TossTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.ChevronRight, null, tint = TossTextTertiary, modifier = Modifier.size(18.dp))
            }
            // he-add — "+ 일정 직접 추가" (full width, bg gray, blue, 14px w800)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(TossGrayBg)
                    .clickable { onAddSchedule() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ 일정 직접 추가", color = TossBlue, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** 프로토 shortAddr — 주소에서 "구/시" + "동" 만 추려 짧게. 없으면 "주소 미입력". */
private fun shortAddr(a: String?): String {
    if (a.isNullOrBlank()) return "주소 미입력"
    val gu = Regex("([가-힣]+[구시])").find(a)?.value
    val dong = Regex("([가-힣]+동)").find(a)?.value
    val parts = listOfNotNull(gu, dong)
    return if (parts.isNotEmpty()) parts.joinToString(" ") else a
}

/** 프로토 word — 다음 시공일이 오늘 기준 내일/모레/그 외 "M/D". */
private fun relativeDayWord(epoch: Long, now: Long = System.currentTimeMillis()): String {
    val today = DateTimeUtils.startOfDay(now)
    val target = DateTimeUtils.startOfDay(epoch)
    val diff = ((target - today) / DateTimeUtils.DAY_MS).toInt()
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = target }
    val md = "${cal.get(java.util.Calendar.MONTH) + 1}/${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    return when (diff) {
        1 -> "내일($md)"
        2 -> "모레($md)"
        else -> md
    }
}

@Composable
private fun HeroBtn(label: String, icon: ImageVector, light: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (light) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (light) Color(0xFF14171F) else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (light) Color(0xFF14171F) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 시공 완료 팝업 (프로토 openComplete) — 오늘 시공 히어로 [완료] 탭 시.
 *   "🎉 시공 완료 · 고생하셨습니다!" + 잔금/후기 안내 문구 + [완료처리][요청 보내기].
 *   잔금 남았으면 잔금 요청 + "후기 요청도 함께", 정산 끝났으면 후기 요청. (계좌는 prefs 미보유 → 잔금액만)
 */
@Composable
private fun CompletionDialog(
    customer: com.detailline.callfollowcrm.data.local.entity.CustomerEntity,
    onDismiss: () -> Unit,
    onComplete: (name: String) -> Unit,
    onSend: (phone: String, name: String, body: String, kind: String) -> Unit
) {
    val name = customer.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(customer.phoneNumber)
    val total = customer.totalAmount ?: 0L
    val dep = customer.depositAmount ?: 0L
    val bal = customer.balanceAmount ?: (total - dep).coerceAtLeast(0L)
    val hasBal = customer.balancePaidAt == null && bal > 0L
    val won = "%,d".format(bal)
    val reviewMsg = "고객님, 오늘 시공 잘 마쳤습니다 😊 만족스러우셨다면 후기 한 줄 부탁드려요! 또 필요하시면 언제든 연락주세요 :)"
    val balanceMsg = "고객님, 오늘 시공 잘 마쳤습니다 😊\n잔금은 ${won}원입니다.\n맡겨주셔서 대단히 감사합니다!"
    val subtitle = if (hasBal) "$name · 잔금 ${won}원" else "$name · 정산 완료"

    // 프로토 .modal-card — left/right 18, top 50% translateY(-50%) = 좌우 18 여백 + 세로 정중앙.
    //   usePlatformDefaultWidth=false 로 좁은 기본 너비 해제 후, 전체화면 Box 로 명시적 가운데 정렬
    //   (안 그러면 세로 정렬이 풀려 바닥에 깔림).
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Box(
          Modifier.fillMaxSize().padding(horizontal = 18.dp),
          contentAlignment = Alignment.Center
      ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(20.dp)
        ) {
            Text("🎉 시공 완료 · 고생하셨습니다!", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossBlueDark)
            Spacer(Modifier.height(12.dp))
            Text(
                if (hasBal) balanceMsg else reviewMsg,
                fontSize = 13.5.sp, lineHeight = 21.sp, color = TossTextSecondary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(13.dp)
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                        .clickable { onComplete(name) }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text("완료처리", color = TossTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                val sendKind = if (hasBal) "잔금 요청" else "후기 요청"
                val sendBody = if (hasBal) balanceMsg else reviewMsg
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossBlue)
                        .clickable { onSend(customer.phoneNumber, name, sendBody, sendKind) }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text("$sendKind 보내기", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
            if (hasBal) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().clickable { onSend(customer.phoneNumber, name, reviewMsg, "후기 요청") }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("후기 요청도 함께 보내기", color = TossTextTertiary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
      } // end center Box
    }
}

/**
 * 홈 미수금 진입 카드 — "아직 못 받은 돈 OO원 · N곳" → 탭하면 정산 화면.
 *   미수 0 이면 긍정 프레이밍("모두 받았어요"). 정산 Phase 1 (2026-06-01).
 */
@Composable
private fun OutstandingCard(
    outstandingTotal: Long,
    outstandingCount: Int,
    onClick: () -> Unit
) {
    TossCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (outstandingCount > 0) TossError.copy(alpha = 0.10f) else TossSuccess.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("💰", fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (outstandingCount > 0) "아직 못 받은 돈" else "정산 · 받을 돈 정리",
                    style = MaterialTheme.typography.labelMedium,
                    color = TossTextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                if (outstandingCount > 0) {
                    Text(
                        MoneyFormatter.won(outstandingTotal),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossError
                    )
                    Text(
                        "미수 ${outstandingCount}곳",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextTertiary
                    )
                } else {
                    Text(
                        "못 받은 돈 없어요 👍",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossSuccess
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, "정산 열기", tint = TossTextTertiary)
        }
    }
}

/**
 * 홈 진입 카드 (정기문자 / 시공 안내 등) — 이모지 + 라벨 + 강조 값 + chevron. N>0 일 때만 노출. (2026-06-01)
 */
@Composable
private fun RemindCard(
    reminder: com.detailline.callfollowcrm.presentation.screen.home.HomeReminderUi,
    onSkip: () -> Unit,
    onSend: () -> Unit
) {
    // 프로토 .remind-card — 흰 카드 + 좌측 3px 앰버 inset + 라벨/이름/문구박스 + [건너뛰기][문자 보낼까요?].
    val amber = Color(0xFFF6A609)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(amber))
        Column(Modifier.weight(1f).padding(16.dp)) {
            // remind-label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, null, tint = Color(0xFFB8780A), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(reminder.kindLabel, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB8780A))
            }
            // remind-name
            Text(
                "${reminder.name} · ${reminder.whenLabel}",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                letterSpacing = (-0.3).sp, modifier = Modifier.padding(top = 8.dp)
            )
            // remind-msg
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TossGrayBg)
                    .padding(12.dp)
            ) {
                Text(reminder.body, fontSize = 13.5.sp, color = TossTextPrimary, lineHeight = 21.sp)
            }
            // remind-btns
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                        .clickable { onSkip() }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("건너뛰기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossBlue)
                        .clickable { onSend() }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("문자 보낼까요?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
        }
    }
}

/** 팀원 진행 배너 스타일 — kind(출발/도착/완료)별 색·아이콘·제목·동사. */
private data class TeamUpdateStyle(
    val accent: Color,
    val tint: Color,
    val icon: ImageVector,
    val title: String,
    val verb: String
)

private fun teamUpdateStyle(kind: String): TeamUpdateStyle = when (kind) {
    "arrived" -> TeamUpdateStyle(TossBlue, TossBlueSoft, Icons.Default.LocationOn, "팀원 현장 도착 📍", "도착")
    "completed" -> TeamUpdateStyle(Color(0xFF7C5CFC), Color(0xFFF1ECFE), Icons.Default.CheckCircle, "작업 완료 ✅", "작업 완료")
    "note" -> TeamUpdateStyle(Color(0xFFF6A609), Color(0xFFFFF3DF), Icons.Default.Edit, "현장 메모 📝", "")
    else -> TeamUpdateStyle(TossSuccess, Color(0xFFE5F8EE), Icons.Default.Navigation, "팀원 출발 🚗", "출발")
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun InboxAlert(
    accent: Color,
    accentTint: Color,
    icon: ImageVector,
    title: String,
    tagText: String?,
    tagBg: Color,
    tagFg: Color,
    sub: String,
    goLabel: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    // 프로토 .team-alert — 흰 카드 + 좌측 4px 강조선 + 아이콘박스 + 제목/태그 + 부제 + go 칩.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, TossDivider, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Row(
            modifier = Modifier.weight(1f).padding(start = 10.dp, top = 13.dp, end = 14.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(accentTint),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                    if (!tagText.isNullOrEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(tagBg).padding(horizontal = 7.dp, vertical = 2.dp)
                        ) { Text(tagText, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = tagFg) }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(sub, fontSize = 12.sp, color = TossTextSecondary, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(accentTint).padding(horizontal = 13.dp, vertical = 7.dp)
            ) { Text(goLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = accent) }
        }
    }
}

/**
 * 부재중 → 자동답장 카드 — 막내 비서가 사장님 대신 첫 인사를 보낸 기록 (최근 24h).
 *   프로토 'team-alert missed' 벤치마킹. 각 줄 탭 = 그 고객 대화로 진입.
 *   실패 건은 빨강 강조 ("직접 보내주세요"). 2026-06-01.
 */
@Composable
private fun AutoReplyCard(
    items: List<AutoReplyItem>,
    onOpenChat: (String, Long?) -> Unit
) {
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤖", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "막내가 자동 답장했어요",
                    style = MaterialTheme.typography.titleMedium,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "부재중 전화에 사장님 대신 첫 인사를 보냈어요",
                style = MaterialTheme.typography.bodySmall,
                color = TossTextTertiary
            )
            items.forEach { ar ->
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenChat(ar.phone, ar.customerId) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (ar.failed) TossError.copy(alpha = 0.10f)
                                else TossSuccess.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (ar.failed) Icons.Default.CallMissed else Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = if (ar.failed) TossError else TossSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            ar.customerName ?: PhoneNumberFormatter.format(ar.phone),
                            style = MaterialTheme.typography.titleSmall,
                            color = TossTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            buildString {
                                append(if (ar.failed) "⚠ 발송 실패 — 직접 보내주세요" else "✅ 보냄")
                                append(" · ")
                                append(DateTimeUtils.formatShort(ar.createdAt))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ar.failed) TossError else TossTextTertiary,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(TossBlueSoft)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "대화",
                            style = MaterialTheme.typography.labelMedium,
                            color = TossBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiSection(
    todayNew: Int,
    unhandled: Int,
    weekScheduled: Int,
    /** 2026-05-30 #12 — 🆕 카드 클릭 시 TodayNew 필터 적용. */
    onFilterTodayNew: () -> Unit,
    onFilterUnhandled: () -> Unit,
    onOpenSchedule: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 2026-05-25: 4장 → 3장으로 축소. "견적 답대기" 는 status enum 기반이라 폐기.
        // 2026-05-30 #12 통점 fix: 🆕 카드도 클릭 가능 (TodayNew 필터 적용).
        //   3 카드 모두 동일 패턴 — 클릭 시 해당 필터 / 화면 진입.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard("🆕", "오늘 신규", todayNew, TossBlue, Modifier.weight(1f), onFilterTodayNew)
            KpiCard("⚠️", "미확인", unhandled, TossError, Modifier.weight(1f), onFilterUnhandled)
            KpiCard("📅", "이번주 시공", weekScheduled, TossSuccess, Modifier.weight(1f), onOpenSchedule)
        }
    }
}

/**
 * 정사각형에 가까운 KPI 카드. 큰 숫자 + 라벨 + 좌상단 이모지.
 * 탭 동작은 호출부 정의 (필터 변경, 외부 화면 이동 등).
 */
@Composable
private fun KpiCard(
    emoji: String,
    label: String,
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val emphasized = count > 0
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = TossTextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                count.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (emphasized) accent else TossTextTertiary
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "건",
                style = MaterialTheme.typography.labelMedium,
                color = if (emphasized) accent else TossTextTertiary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun HomeRow(
    item: HomeItem,
    aiCardSummary: String?,
    category: com.detailline.callfollowcrm.data.local.entity.CategoryEntity?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenCustomerDetail: () -> Unit,
    /** [📍 길찾기] — phone 의 주소 resolve + 사장님 선택 네비 앱 launch. 2026-05-27 신규. */
    onOpenNavigation: () -> Unit
) {
    val isUnconfirmed = item.isUnconfirmed
    val context = LocalContext.current
    TossCard(onClick = onToggle) {
        Column {
            // 헤더: [타입 아이콘] 이름 + 카테고리 badge + 📞. 2026-05-25 갤메시지 벤치마킹.
            //   IconButton 은 자체 click 영역 — 카드 펼침 (onToggle) 과 충돌 X.
            //   좌측 라운드 아이콘 = 통화/문자/부재중 한 눈에 식별 (2026-05-25 사장님 피드백).
            Row(verticalAlignment = Alignment.CenterVertically) {
                CallTypeIndicator(callType = item.record.callType)
                Spacer(Modifier.width(10.dp))
                Text(
                    item.customer?.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(item.record.phoneNumber),
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (category != null) {
                    val display = if (category.emoji != null) "${category.emoji} ${category.name}" else category.name
                    TossBadge(display, color = TossBlue, background = TossBlueSoft)
                    Spacer(Modifier.width(6.dp))
                } else if (isUnconfirmed) {
                    TossBadge("미확인", color = TossError, background = Color(0xFFFEECEE))
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(
                    onClick = {
                        runCatching {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                data = android.net.Uri.parse("tel:${item.record.phoneNumber}")
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "전화 걸기",
                        tint = TossBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // AI 카드 요약 — 이름 바로 아래 (가장 중요한 정보). 에이닷 벤치마킹.
            //   2026-05-27 사장님 보고 fix: null + SMS 카드면 "요약 작성 중..." 진행감 표시.
            //   2026-05-30 사장님 #8 통점 fix: null 일 때만 "작성 중" 표시.
            //     ConversationAiRepository 가 빈 응답 시 "" sentinel 저장 — 시도했으나 응답 없음.
            //     null = 시도 안 함, "" = 시도 끝 (요약 거리 없음 / 114 같은 광고 등) → 표시 X.
            val isSmsCard = item.record.callType == HomeViewModel.CALL_TYPE_SMS_ONLY
            if (!aiCardSummary.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "✨ $aiCardSummary",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TossBlue,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            } else if (aiCardSummary == null && isSmsCard) {
                Spacer(Modifier.height(4.dp))
                com.detailline.callfollowcrm.presentation.theme.AnimatedDots(
                    text = "✨ 요약 작성 중",
                    color = TossBlue.copy(alpha = 0.7f)
                )
            }
            // aiCardSummary == "" (sentinel) = 표시 X — 시도 끝났는데 요약 거리 없음.
            // 2026-05-25: 번호 두 번 표시 제거 (사장님 피드백). 헤더가 이름 또는 번호이고,
            //   번호 확인은 우측 [📞] 다이얼러 또는 펼침 [ⓘ 고객 카드] 로.
            Spacer(Modifier.height(4.dp))
            // 시간 줄 — 타입은 좌측 라운드 아이콘이 이미 표현. 라벨 텍스트는 통화 횟수만.
            //   (이전엔 "발신/수신/문자만" 라벨 박혀있었으나 아이콘과 중복되어 제거.)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val timeLine = buildString {
                    append(DateTimeUtils.formatShort(item.record.endedAt))
                    if (item.callCount > 1) append(" · 오늘 ${item.callCount}통")
                }
                Text(
                    timeLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextTertiary,
                    modifier = Modifier.weight(1f)
                )
                // 카테고리 있는데 미확인이기도 한 경우 — 카드 헤더는 카테고리만 표시되니 보조로 표시.
                if (isUnconfirmed && category != null) {
                    Text(
                        "• 미확인",
                        style = MaterialTheme.typography.labelSmall,
                        color = TossError
                    )
                }
            }
            item.customer?.let { c ->
                if (c.memo.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        c.memo.lineSequence().firstOrNull().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TossTextSecondary,
                        maxLines = 1
                    )
                }
                c.scheduledWorkDate?.let { scheduled ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "🗓 ${DateTimeUtils.formatDateOnly(scheduled)} 시공 예약 · ${DateTimeUtils.dDayLabel(scheduled)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TossBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 인라인 액션 4개 — 에이닷 벤치마킹. 카드 탭으로 토글.
            // 순서 (2026-05-27 사장님 결정): [ⓘ 고객정보] [📞 전화] [📍 길찾기] [💬 메시지]
            //   = 정보 확인 → 전화/이동 → 소통. 시공자 워크플로우 순서.
            //   - 고객정보 → CustomerDetail (Customer 없으면 호출처가 자동 생성 후 진입)
            //   - 전화 → 시스템 다이얼러 (ACTION_DIAL — 권한 없이 사용자 한 번 더 탭하게)
            //   - 길찾기 → 사장님이 설정에서 고른 네비 앱으로 launch (NavLauncher). [✨ AI] 자리 대체.
            //   - 메시지 → ChatScreen
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFEEF1F4))
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        InlineActionButton(
                            icon = Icons.Default.Info,
                            label = "고객정보",
                            enabled = true,
                            onClick = onOpenCustomerDetail
                        )
                        InlineActionButton(
                            icon = Icons.Default.Call,
                            label = "전화",
                            enabled = true,
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_DIAL,
                                    android.net.Uri.parse("tel:${item.record.phoneNumber}")
                                )
                                runCatching { context.startActivity(intent) }
                                    .onFailure {
                                        android.widget.Toast.makeText(
                                            context, "다이얼러를 열 수 없어요", android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                        )
                        InlineActionButton(
                            icon = Icons.Default.Place,
                            label = "길찾기",
                            enabled = true,
                            onClick = onOpenNavigation
                        )
                        InlineActionButton(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            label = "메시지",
                            enabled = true,
                            onClick = onOpenChat
                        )
                    }
                }
            }
        }
    }
}

/**
 * 첫 [📍 길찾기] 탭 시 사장님이 어느 네비 앱 쓸지 고르는 다이얼로그.
 *   탭 = 즉시 선택 + dismiss + launch (확인 버튼 따로 X = 1탭).
 *   이후엔 prefs.defaultNavAppKey 가 박혀서 같은 화면 안 뜨고 바로 launch.
 *   설정 화면에서 언제든 변경 가능.
 */
@Composable
private fun NavAppPickerDialog(
    onPick: (com.detailline.callfollowcrm.util.NavApp) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "어느 네비 앱을 쓰세요?",
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "선택한 앱이 다음부터 1탭으로 열려요. 설정 → 기본 네비 앱 에서 언제든 변경 가능.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary
                )
                Spacer(Modifier.height(16.dp))
                com.detailline.callfollowcrm.util.NavApp.values().forEach { app ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEEF1F4))
                            .clickable { onPick(app) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            app.label,
                            color = TossTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("닫기", color = TossTextSecondary)
                }
            }
        }
    }
}

/**
 * 카드 펼침 영역의 액션 버튼 — 아이콘 + 라벨 세로 배치. 에이닷 벤치마킹.
 * disabled 면 회색 + 클릭 무시.
 */
@Composable
private fun InlineActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) TossBlue else TossTextTertiary
    val labelColor = if (enabled) TossTextPrimary else TossTextTertiary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 홈 리스트 카드 우측 상단의 영업 상태 알약. 4색 톤은 CustomerDetail 의 statusColors 와 일관.
 * 별도 파일로 분리하지 않은 이유: 작고, 변경 시 두 화면을 함께 보는 게 자연스러움.
 */
@Composable
private fun StatusBadgeSmall(label: String) {
    val (fg, bg) = statusColors(label)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun statusColors(label: String): Pair<Color, Color> {
    val blue = TossBlue to TossBlueSoft
    val green = TossSuccess to Color(0xFFE6F7EC)
    val gray = TossTextSecondary to Color(0xFFF1F3F5)
    val red = TossError to Color(0xFFFEEBEC)
    return when (label) {
        "신규 문의", "견적 대기", "견적 발송" -> blue
        "예약 대기", "예약 확정" -> green
        "시공 완료" -> gray
        "보류", "이탈" -> red
        else -> blue
    }
}

/**
 * 미확인 카드 우→좌 swipe → "광고/스팸" 영구 마킹.
 *   배경: 빨강 + 🚫 차단 아이콘 — swipe 중 드러나는 affordance.
 *   threshold 60% (Material 표준보다 살짝 높게) — 실수 swipe 방지.
 *   사장님 결정 2026-05-25: 좌→우 방향은 비활성 (다른 의미와 충돌 방지).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpamSwipeBox(onSpam: () -> Unit, content: @Composable () -> Unit) {
    // 2026-05-28 사장님 보고 버그 fix:
    //   기존 confirmValueChange { true } → dismiss 확정 → SwipeBox state stuck.
    //   unmarkSpam (undo) 후 같은 LazyColumn key 라 composable 재사용 → dismissed state 그대로 → 빈 칸 + "광고로 처리" 잔상.
    //   해결: false 반환 → SwipeBox 는 원위치 복귀 애니. spam 처리는 onSpam → markSpam 이
    //   unconfirmedSuffixes 에서 그 phone 을 제거 → 카드가 LazyColumn 에서 자연 사라짐.
    //   undo 시 카드가 다시 등장 → 새 SwipeBox = 정상 state.
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSpam()
            }
            false   // 항상 false — dismiss 안 시킴. 카드 제거는 데이터 흐름이 처리.
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.6f }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(TossError.copy(alpha = 0.12f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                // 2026-05-30 사장님 #11 통점 fix:
                //   사장님 결정 = 문구만 "확인함" 으로, 동작은 그대로 (spam DB 마킹 — 미확인 카테고리에서만 영구 제외).
                //   아이콘 의미 (Block) 와 "확인함" 텍스트는 살짝 어긋나나 사장님 요청대로 단순 문구 변경.
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "확인함",
                        tint = TossError,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "확인함",
                        color = TossError,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        content = { content() }
    )
}

/**
 * 정보성 배너(자동답장 보냄 등) 우→좌 swipe → 정리(dismiss). 회색 "정리" affordance.
 *   SpamSwipeBox 와 동일 패턴: confirmValueChange=false 로 원위치 복귀 + 데이터 흐름(dismissed id)이 카드 제거.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissSwipeBox(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDismiss()
            false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.5f }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(TossTextTertiary.copy(alpha = 0.12f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "정리",
                        tint = TossTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("정리", color = TossTextSecondary, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        content = { content() }
    )
}

/**
 * Compose LocalContext 는 ContextThemeWrapper 일 수 있어 직접 cast 실패.
 *   baseContext 따라가서 Activity 추출 — 뒤로가기 종료용.
 */
private fun android.content.Context.findHomeActivityOrNull(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private fun callTypeLabel(raw: String): String = when (raw) {
    "INCOMING" -> "수신"
    "OUTGOING" -> "발신"
    "MISSED" -> "부재중"
    "REJECTED" -> "거절"
    "MANUAL" -> "수동 등록"
    HomeViewModel.CALL_TYPE_SMS_ONLY -> "문자만"
    else -> "통화"
}

/**
 * 카드 좌측 36dp 라운드 아이콘 — 통화/문자/부재중 한 눈에 식별.
 *  - 문자만 = 파랑 배경 + 💬 (TossBlue)
 *  - 부재중/거절 = 빨강 배경 + 부재중 아이콘 (TossError)
 *  - 수신/발신/통화 = 회색 배경 + 방향 화살표
 *  - 수동 등록 = 회색 배경 + 편집 아이콘
 *
 * 토스 스타일: 절제된 컬러 + 단색 아이콘 + soft 배경.
 * 사장님 피드백 (2026-05-25): "전화랑 문자메세지랑 구분이 잘 안 가" → 본 indicator 도입.
 */
@Composable
private fun CallTypeIndicator(callType: String) {
    val (bg, fg, icon) = when (callType) {
        HomeViewModel.CALL_TYPE_SMS_ONLY ->
            Triple(TossBlueSoft, TossBlue, Icons.AutoMirrored.Filled.Chat)
        "MISSED", "REJECTED" ->
            Triple(Color(0xFFFEECEE), TossError, Icons.Default.CallMissed)
        "INCOMING" ->
            Triple(Color(0xFFEEF1F4), TossTextSecondary, Icons.Default.CallReceived)
        "OUTGOING" ->
            Triple(Color(0xFFEEF1F4), TossTextSecondary, Icons.Default.CallMade)
        "MANUAL" ->
            Triple(Color(0xFFEEF1F4), TossTextSecondary, Icons.Default.Edit)
        else ->
            Triple(Color(0xFFEEF1F4), TossTextSecondary, Icons.Default.Call)
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = callTypeLabel(callType),
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 갤메시지 식 "카테고리 추가" 다이얼로그.
 *  - 입력칸: 카테고리 이름 한 줄만.
 *  - placeholder 예시는 사장님 도메인 (AS 고객 / 일당 / 아르바이트 등) 기반.
 *
 * 2026-05-25: 이모지 입력란 제거 — 한글 단어로 충분히 구별됨. 사장님 인지 부담 X.
 *   CategoryEntity.emoji 필드는 유지 (legacy + 추후 AI 자동 매핑 여지).
 */
@Composable
private fun CategoryAddDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, emoji: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val fieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedBorderColor = TossBlue,
        unfocusedBorderColor = com.detailline.callfollowcrm.presentation.theme.TossDivider,
        focusedTextColor = TossTextPrimary,
        unfocusedTextColor = TossTextPrimary,
        cursorColor = TossBlue,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "카테고리 추가",
                color = TossTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "이름만 적으면 AI 가 대화 내용 보고 알아서 분류해드려요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary
                )
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("예: AS 고객, 일당, 아르바이트", color = TossTextTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    if (name.isNotBlank()) onAdd(name.trim(), null)
                }
            ) { Text("추가", color = TossBlue, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("취소", color = TossTextSecondary)
            }
        },
        containerColor = Color.White
    )
}

/**
 * 서버 살아있음 indicator — 작은 동그라미.
 * alive == null = 첫 체크 전(회색) / true = 초록 / false = 빨강.
 * tap 시 onClick (상위에서 다이얼로그 띄움).
 */
@Composable
private fun ServerStatusDot(alive: Boolean?, onClick: () -> Unit) {
    val color = when (alive) {
        true -> TossSuccess
        false -> TossError
        null -> TossTextTertiary
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color)
            .clickable { onClick() }
    )
}

/**
 * 프로토 .ai-badge (renderAiBadge) — 상담함 앱바 오른쪽 "{업종} AI" 알약.
 *   그라데이션 배경 + 초록 점 + ✨ + "{업종} AI" (예: "줄눈 AI"). 탭 = AI 설명/서버 상태.
 *   점 색으로 서버 연결 상태를 은근히 표시(정상=초록/끊김=빨강/확인중=노랑).
 */
@Composable
private fun AiBadge(trade: String, alive: Boolean?, onClick: () -> Unit) {
    val dot = when (alive) {
        true -> TossSuccess
        false -> TossError
        null -> TossWarning
    }
    val label = "$trade AI"
    Row(
        modifier = Modifier
            .background(
                Brush.linearGradient(listOf(Color(0xFFEAF2FF), Color(0xFFF1ECFF))),
                RoundedCornerShape(999.dp)
            )
            .border(1.dp, Color(0xFFE0E7FB), RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 프로토 ai-dot — 6px + box-shadow 0 0 0 3px rgba(.16) glow 링. (dot 색=서버상태 유지, glow 도 맞춤)
        Box(
            Modifier.size(12.dp).clip(CircleShape).background(dot.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        }
        Spacer(Modifier.width(5.dp))
        Icon(Icons.Default.AutoAwesome, null, tint = TossBlue, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossBlueDark)
    }
}

/**
 * 프로토 today-new-slot (renderTodayNew) 그대로 — "오늘 신규 문의 N통 / 새 번호 기준 · 어제 M통" + ▲▼.
 *   ▲(초록)=어제보다 늘어남, ▼(빨강)=줄어듦, -(회색)=같음.
 */
@Composable
private fun TodayNewCard(todayNew: Int, yesterdayNew: Int, onClick: () -> Unit) {
    val d = todayNew - yesterdayNew
    val deltaText = when { d > 0 -> "▲ $d"; d < 0 -> "▼ ${-d}"; else -> "-" }
    val deltaFg = when { d > 0 -> Color(0xFF0A8F44); d < 0 -> TossError; else -> TossTextTertiary }
    val deltaBg = when { d > 0 -> Color(0xFFE7F8EF); d < 0 -> Color(0xFFFDEAEF); else -> TossGrayBg }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, TossDivider, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).background(TossBlueSoft, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = TossBlue, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text("오늘 신규 문의 ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Text("${todayNew}통", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossBlue)
            }
            Spacer(Modifier.height(2.dp))
            Text("새 번호 기준 · 어제 ${yesterdayNew}통", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TossTextTertiary)
        }
        Box(
            Modifier.background(deltaBg, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(deltaText, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = deltaFg)
        }
    }
}

/** 프로토 waiting-head — "지금 답장 기다려요" + 카운트 알약 + "← 밀어서 정리". */
@Composable
private fun WaitingHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("지금 답장 기다려요", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
        Spacer(Modifier.width(8.dp))
        // 프로토 .count-pill — 빨강(bg #FDEAEF / fg error)
        Box(
            Modifier.background(Color(0xFFFDEAEF), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("$count", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossError)
        }
        Spacer(Modifier.weight(1f))
        // 프로토 .swipe-hint — 회색칩 배경
        Text("← 밀어서 정리", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TossTextTertiary,
            modifier = Modifier.background(TossGrayBg, RoundedCornerShape(999.dp)).padding(horizontal = 9.dp, vertical = 3.dp))
    }
}

/** 프로토 sec-sub — 작은 섹션 부제 ("최근 대화"). */
@Composable
private fun SecSub(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TossTextSecondary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
    )
}

private fun dialHome(context: android.content.Context, phone: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone"))
        )
    }
}

/**
 * 프로토 waitingCardHtml — 대기 고객 카드.
 *   heat 점 + 이름(+신규칩) + 시간 + 전화 + preview(AI 요약) + [답장하기].
 *   프로토의 "AI 추천 답변 quick-send" 는 앱에선 채팅의 추천 시스템이 담당 → 여기선 preparing 변형([답장하기]→채팅).
 */
@Composable
private fun WaitingCard(
    item: HomeItem,
    aiSummary: String?,
    suggestedReply: String?,
    onOpenChat: () -> Unit,
    onCall: () -> Unit,
    onQuickSend: () -> Unit
) {
    val isNew = item.isNewToday
    val heatColor = when {
        isNew -> TossBlue
        else -> when (item.customer?.leadHeat?.lowercase()) {
            "hot" -> TossError
            "warm" -> TossWarning
            "cold" -> Color(0xFFC2C9D2)
            else -> Color(0xFFC2C9D2)
        }
    }
    val name = item.customer?.name?.takeIf { it.isNotBlank() }
        ?: PhoneNumberFormatter.format(item.record.phoneNumber)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable { onOpenChat() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(androidx.compose.foundation.shape.CircleShape).background(heatColor))
            Spacer(Modifier.width(10.dp))
            Text(
                name,
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isNew) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.background(TossBlueSoft, RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text("신규", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(DateTimeUtils.formatShort(item.record.endedAt), fontSize = 12.sp, color = TossTextTertiary)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(TossGrayBg)
                    .clickable { onCall() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, "전화", tint = TossTextSecondary, modifier = Modifier.size(17.dp))
            }
        }
        // preview — 대화 요약 한 줄 (프로토 .preview).
        if (!aiSummary.isNullOrBlank()) {
            Spacer(Modifier.height(9.dp))
            Text(aiSummary, fontSize = 13.5.sp, color = TossTextSecondary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(11.dp))
        if (!suggestedReply.isNullOrBlank()) {
            // 프로토 sugbox — ✨ AI 추천 답변 + 본문 + 비행기(1탭 발송).
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEEF4FF))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = TossBlue, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("AI 추천 답변", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        suggestedReply, fontSize = 13.5.sp, color = TossTextSecondary,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(TossBlue)
                            .clickable { onQuickSend() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "보내기", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        } else {
            // 프로토 preparing — 추천 준비 전: "AI 답변 준비 중…" + [답장하기].
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = TossTextTertiary, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text("AI 답변 준비 중…", fontSize = 13.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .border(1.5.dp, Color(0xFFDCE7FB), RoundedCornerShape(999.dp))
                        .clickable { onOpenChat() }
                        .padding(horizontal = 15.dp, vertical = 8.dp)
                ) {
                    Text("답장하기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                }
            }
        }
    }
}

private val AV_TINTS = listOf(
    Color(0xFFEAF2FE) to TossBlue,
    Color(0xFFE5F8EE) to Color(0xFF0E9F56),
    Color(0xFFFEF3E0) to Color(0xFFB7791F),
    Color(0xFFF1ECFE) to Color(0xFF6B4FD8)
)

/** 프로토 avatarHtml — 이름 있으면 컬러 이니셜 원, 없으면 회색 사람 아이콘. */
@Composable
private fun Avatar(name: String?, index: Int) {
    if (name.isNullOrBlank()) {
        Box(
            Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(TossGrayBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, tint = TossTextTertiary, modifier = Modifier.size(20.dp))
        }
    } else {
        val (bg, fg) = AV_TINTS[index % AV_TINTS.size]
        Box(
            Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(name.trim().first().toString(), color = fg, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/**
 * 프로토 recentRowHtml — 최근 대화 행. 아바타 + 이름 + 태그 + 시간 + 요약 한 줄.
 *   (LazyColumn 간격상 흰 카드 형태로 — 프로토는 한 카드 안 연속행, 시각적으로 동등.)
 */
@Composable
private fun RecentRow(
    item: HomeItem,
    index: Int,
    aiSummary: String?,
    onOpenChat: () -> Unit
) {
    val name = item.customer?.name?.takeIf { it.isNotBlank() }
    val title = name ?: PhoneNumberFormatter.format(item.record.phoneNumber)
    val tag = recentStatusTag(item.customer)   // 프로토 .tag — 시공 D-N/계약금/완료
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenChat() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name, index)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (tag != null) {
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(tag.bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(tag.text, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = tag.fg)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(recentTimeLabel(item.lastActivityMs.takeIf { it > 0L } ?: item.record.endedAt), fontSize = 11.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
            }
            if (!aiSummary.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(aiSummary, fontSize = 13.sp, color = TossTextSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

private data class RecentTag(val text: String, val fg: Color, val bg: Color)

/**
 * 프로토 recent .tag — 고객 상태에서 파생. 시공일 잡힘=시공 D-N(파랑)/오늘=D-DAY/지남=완료(회색),
 *   잔금 받음=완료(회색), 계약금만=계약금(초록), 그 외 태그 없음. (견적 발송 amber 는 이력 필요 → 후속)
 */
private fun recentStatusTag(c: com.detailline.callfollowcrm.data.local.entity.CustomerEntity?): RecentTag? {
    if (c == null) return null
    val blueFg = TossBlue; val blueBg = Color(0xFFEAF2FE)
    val greenFg = Color(0xFF0E9F56); val greenBg = Color(0xFFE5F8EE)
    val grayFg = TossTextTertiary; val grayBg = TossGrayBg
    val sched = c.scheduledWorkDate
    if (sched != null && sched > 0L) {
        val days = ((DateTimeUtils.startOfDay(sched) - DateTimeUtils.startOfDay(System.currentTimeMillis())) / DateTimeUtils.DAY_MS).toInt()
        return when {
            days > 0 -> RecentTag("시공 D-$days", blueFg, blueBg)
            days == 0 -> RecentTag("시공 D-DAY", blueFg, blueBg)
            else -> RecentTag("완료", grayFg, grayBg)
        }
    }
    if (c.balancePaidAt != null) return RecentTag("완료", grayFg, grayBg)
    if (c.depositPaidAt != null) return RecentTag("계약금", greenFg, greenBg)
    return null
}

/** 프로토 recent 시각 — 오늘/어제/N일 전/M/D (절대시각 X). */
private fun recentTimeLabel(ms: Long): String {
    val today = DateTimeUtils.startOfDay(System.currentTimeMillis())
    return when (val days = ((today - DateTimeUtils.startOfDay(ms)) / DateTimeUtils.DAY_MS).toInt()) {
        0 -> "오늘"
        1 -> "어제"
        in 2..6 -> "${days}일 전"
        else -> java.text.SimpleDateFormat("M/d", java.util.Locale.KOREAN).format(java.util.Date(ms))
    }
}

/** 프로토 renderWaiting 빈 상태(.empty-mascot) — 막내 + 말풍선(em-speech) + 보조문구(em-sub). */
@Composable
private fun WaitingEmptyMascot() {
    Box(
        Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.detailline.callfollowcrm.presentation.component.Mascot(sizeDp = 80.dp)
            Spacer(Modifier.height(10.dp))
            // .em-speech — 파란 pill + 위쪽 삼각 꼬리(::after 11x11 rotate45 top:-6).
            //   배경 var(--blue-tint) #EEF4FF / 글자 var(--blue-dark) #1B64DA / weight 800 / 14sp.
            //   padding 10x16, border-radius 14.
            Box(contentAlignment = Alignment.TopCenter) {
                // 삼각 꼬리: 11dp 정사각 45° → 윗절반만 pill 위로 노출. 같은 색이라 ▲ 만 보임.
                Box(
                    Modifier
                        .offset(y = (-5).dp)
                        .size(11.dp)
                        .rotate(45f)
                        .background(TossBlueSoft)
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(TossBlueSoft)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        "사장님, 오늘 상담 다 끝냈어요! 👏",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TossBlueDark
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // .em-sub — 12.5sp / weight 600 / color t3 (#9AA3AF) / margin-top 12.
            Text(
                "새 문의가 오면 막내가 바로 알려드릴게요",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TossTextTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ============================================================
// @Preview — Android Studio 우측 패널 / split view 에서 빌드 없이 UI 확인.
//   사용법: Android Studio 에서 이 파일 열면 우측 상단 "Split" / "Design" 클릭.
//   코드 저장 시 1~3초 안에 자동 갱신. phone 빌드 안 해도 됨.
// ============================================================

@Preview(name = "KPI 카드 — 모두 0", showBackground = true, backgroundColor = 0xFFF7F8FA)
@Composable
private fun KpiSectionPreviewEmpty() {
    CallFollowCrmTheme {
        KpiSection(
            todayNew = 0,
            unhandled = 0,
            weekScheduled = 0,
            onFilterTodayNew = {},
            onFilterUnhandled = {},
            onOpenSchedule = {}
        )
    }
}

@Preview(name = "KPI 카드 — 일반 상황", showBackground = true, backgroundColor = 0xFFF7F8FA)
@Composable
private fun KpiSectionPreviewNormal() {
    CallFollowCrmTheme {
        KpiSection(
            todayNew = 2,
            unhandled = 5,
            weekScheduled = 3,
            onFilterTodayNew = {},
            onFilterUnhandled = {},
            onOpenSchedule = {}
        )
    }
}

@Preview(name = "KPI 카드 — 폭주", showBackground = true, backgroundColor = 0xFFF7F8FA)
@Composable
private fun KpiSectionPreviewBusy() {
    CallFollowCrmTheme {
        KpiSection(
            todayNew = 12,
            unhandled = 28,
            weekScheduled = 9,
            onFilterTodayNew = {},
            onFilterUnhandled = {},
            onOpenSchedule = {}
        )
    }
}

/** S9 사이즈 별도 확인 (사장님 실기기 360x740 dp). */
@Preview(
    name = "KPI — S9 360x740",
    showBackground = true,
    backgroundColor = 0xFFF7F8FA,
    widthDp = 360,
    heightDp = 200
)
@Composable
private fun KpiSectionPreviewS9() {
    CallFollowCrmTheme {
        KpiSection(
            todayNew = 1,
            unhandled = 4,
            weekScheduled = 2,
            onFilterTodayNew = {},
            onFilterUnhandled = {},
            onOpenSchedule = {}
        )
    }
}

/** 2026-05-30 다크모드 Preview — 다크 테마 적용 시 모양 확인용. */
@Preview(name = "KPI — 다크모드", showBackground = true, backgroundColor = 0xFF161616)
@Composable
private fun KpiSectionPreviewDark() {
    CallFollowCrmTheme(darkOverride = true) {
        KpiSection(
            todayNew = 2,
            unhandled = 5,
            weekScheduled = 3,
            onFilterTodayNew = {},
            onFilterUnhandled = {},
            onOpenSchedule = {}
        )
    }
}
