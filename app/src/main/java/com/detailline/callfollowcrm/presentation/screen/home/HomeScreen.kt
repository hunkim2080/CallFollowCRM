package com.detailline.callfollowcrm.presentation.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.draw.clip
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
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

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
    onOpenTemplates: () -> Unit,
    onOpenAiMessage: () -> Unit,
    onOpenStyleLearning: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val timeline by viewModel.timeline.collectAsState()
    val filter by viewModel.filterState.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val aiCardSummaries by viewModel.cardSummariesByPhoneSuffix.collectAsState()
    val categoriesById by viewModel.categories.collectAsState()
    val todayNew by viewModel.todayNewInquiryCount.collectAsState()
    val unhandled by viewModel.unhandledCount.collectAsState()
    val weekScheduled by viewModel.thisWeekScheduledCount.collectAsState()
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

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "RING-GO",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TossTextPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        ServerStatusDot(
                            alive = serverAlive,
                            onClick = {
                                val msg = when (serverAlive) {
                                    true -> {
                                        val secs = lastOkAtMs?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
                                        "서버 연결 정상 (${secs}초 전)"
                                    }
                                    false -> "서버 연결 실패 — Tailscale 확인하세요"
                                    null -> "서버 상태 체크 중..."
                                }
                                android.widget.Toast.makeText(
                                    context, msg, android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSchedule) {
                        Icon(Icons.Default.DateRange, "시공 예약", tint = TossTextSecondary)
                    }
                    IconButton(onClick = onOpenTemplates) {
                        Icon(Icons.Default.Description, "템플릿", tint = TossTextSecondary)
                    }
                    // AI 문자함 — 2026-05-24 사장님 요청으로 일단 숨김 (사용법 모호 + 거슬림).
                    //   네비/ViewModel/Screen 은 살아있음 (Destinations.AI_MESSAGE). 다음 reactivation 시
                    //   여기 IconButton 한 줄만 복원하면 됨.
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "설정", tint = TossTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenManualEntry() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("수동 입력", fontWeight = FontWeight.SemiBold) },
                containerColor = TossBlue,
                contentColor = Color.White
            )
        },
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

            // 필터 칩 — 항상 위에 고정 (사장님이 언제든 필터 변경 가능).
            // KPI 와 달리 필터칩은 사용 빈도 높아 스크롤 의존 X.
            //   "내 말투 학습" 칩 = 2026-05-24 사장님 요청으로 일단 숨김.
            //   ViewModel/Screen 코드 살아있음. 다음 reactivation 시 onOpenStyleLearning 칩 한 줄 복원.
            // 2026-05-25: 갤메시지 식 카테고리 chip row.
            //   [전체][미확인] + 사장님 정의 카테고리들 + [+]
            //   + 누르면 다이얼로그 (직접 추가 + AI 제안 — TODO).
            val categories by viewModel.categories.collectAsState()
            var addDialogOpen by remember { mutableStateOf(false) }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            ) {
                item(key = "all") {
                    TossChip(
                        text = HomeFilter.All.label,
                        selected = filter is HomeFilter.All,
                        onClick = { viewModel.setFilter(HomeFilter.All) }
                    )
                }
                item(key = "unconfirmed") {
                    TossChip(
                        text = HomeFilter.Unconfirmed.label,
                        selected = filter is HomeFilter.Unconfirmed,
                        onClick = { viewModel.setFilter(HomeFilter.Unconfirmed) }
                    )
                }
                // 2026-05-30 #12 — TodayNew chip 추가. KPI 카드 클릭 ↔ chip 클릭 둘 다 가능 (시각 일관성).
                item(key = "today-new") {
                    TossChip(
                        text = HomeFilter.TodayNew.label,
                        selected = filter is HomeFilter.TodayNew,
                        onClick = { viewModel.setFilter(HomeFilter.TodayNew) }
                    )
                }
                items(categories, key = { "cat-${it.id}" }) { cat ->
                    val display = if (cat.emoji != null) "${cat.emoji} ${cat.name}" else cat.name
                    TossChip(
                        text = display,
                        selected = (filter as? HomeFilter.Category)?.id == cat.id,
                        onClick = {
                            viewModel.setFilter(HomeFilter.Category(cat.id, cat.name, cat.emoji))
                        }
                    )
                }
                item(key = "add") {
                    TossChip(
                        text = "+",
                        selected = false,
                        onClick = { addDialogOpen = true }
                    )
                }
            }

            if (addDialogOpen) {
                CategoryAddDialog(
                    onDismiss = { addDialogOpen = false },
                    onAdd = { name, emoji ->
                        viewModel.addCategory(name, emoji)
                        addDialogOpen = false
                    }
                )
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
                // KPI 3장 — 첫 item. 스크롤 시 위로 사라짐 (갤메시지 알림 박스 패턴).
                //   2026-05-25: "견적 답대기" 카드 제거 — CustomerStatus enum 폐기 후 의미 X.
                item(key = "kpi-section") {
                    KpiSection(
                        todayNew = todayNew,
                        unhandled = unhandled,
                        weekScheduled = weekScheduled,
                        onFilterTodayNew = { viewModel.setFilter(HomeFilter.TodayNew) },
                        onFilterUnhandled = { viewModel.setFilter(HomeFilter.Unconfirmed) },
                        onOpenSchedule = onOpenSchedule
                    )
                }

                if (timeline.isEmpty()) {
                    item(key = "empty-state") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 60.dp, bottom = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📭", fontSize = 40.sp)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    when (val f = filter) {
                                        is HomeFilter.All -> "기록된 통화가 없어요"
                                        is HomeFilter.Unconfirmed -> "미확인 없음 — 7일 내 문의 모두 답장 완료"
                                        is HomeFilter.TodayNew -> "오늘 신규 없음 — 새로 연락온 고객이 아직 없어요"
                                        is HomeFilter.Category -> "‘${f.name}’ 카테고리에 고객이 아직 없어요"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TossTextPrimary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "옛 고객은 우측 하단 ‘+ 수동 입력’ 으로 첫 만난 날짜와 함께 등록할 수 있어요",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TossTextTertiary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                } else {
                    timeline.forEach { group ->
                        // 2026-05-24: 마우스 휠 fling crash 의심 후보 — stickyHeader 를 일반 item 으로 변경.
                        // Compose 의 stickyHeader 는 매우 빠른 fling 에서 일부 환경 crash 알려져 있음.
                        // 사장님 입장 차이 = 헤더가 스크롤 시 함께 위로 흘러감 (sticky 안 됨). UX 손해 작음.
                        item(key = "day-${group.dayStartMs}") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TossGrayBg)
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    DateTimeUtils.dayGroupLabel(group.dayStartMs),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TossTextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        items(
                            group.items,
                            // key = id + phone — SMS-only 가짜 record 는 id=-lastDateMs 라 같은 시각에 두 번호 들어오면
                            //   id 만으론 충돌 가능. phone 까지 묶어 unique 보장.
                            key = { "row-${it.record.id}-${it.record.phoneNumber}" }
                        ) { item ->
                            val rowKey = "row-${item.record.id}-${item.record.phoneNumber}"
                            val suffix = item.record.phoneNumber
                                .filter { c -> c.isDigit() }
                                .takeLast(8)
                            val rowCategory = item.customer?.categoryId?.let { cid ->
                                categoriesById.firstOrNull { it.id == cid }
                            }
                            val rowContent: @Composable () -> Unit = {
                                HomeRow(
                                    item = item,
                                    aiCardSummary = aiCardSummaries[suffix],
                                    category = rowCategory,
                                    expanded = expandedKey == rowKey,
                                    onToggle = {
                                        expandedKey = if (expandedKey == rowKey) null else rowKey
                                    },
                                    onOpenChat = {
                                        onOpenChat(item.record.phoneNumber, item.customer?.id)
                                    },
                                    onOpenCustomerDetail = {
                                        // 2026-05-25 사장님 결정: [ⓘ] 항상 활성화. Customer 없으면 자동 생성 후 진입.
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
                                    onOpenNavigation = {
                                        // [📍 길찾기] — 설정 안 됐으면 선택 다이얼로그, 됐으면 즉시 launch.
                                        launchNavigationFor(item.record.phoneNumber)
                                    }
                                )
                            }

                            // 미확인 필터에서만 swipe-to-spam 활성. 다른 탭에선 단순 HomeRow.
                            //   사장님 의도: 광고 번호를 미확인 카테고리에서만 영구 제외.
                            //   우→좌 swipe → SpamPhone 영구 마킹 + Snackbar Undo (5초).
                            if (filter is HomeFilter.Unconfirmed) {
                                SpamSwipeBox(
                                    onSpam = {
                                        // 2026-05-30 #11 — Snackbar 메시지도 "확인함" 으로 통일.
                                        //   동작은 그대로 spam 마킹 (미확인 카테고리에서 영구 제외).
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
                                    content = rowContent
                                )
                            } else {
                                rowContent()
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
            } // end Box(nestedScroll)
        }
    }
}

/**
 * KPI 4장 (2×2 그리드). LazyColumn 첫 item 으로 들어가서 스크롤 시 사라짐.
 * horizontal padding 은 LazyColumn 의 contentPadding 으로 들어가므로 안에서 X.
 */
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
