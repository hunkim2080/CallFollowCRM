package com.detailline.callfollowcrm.presentation.screen.home

import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import com.detailline.callfollowcrm.presentation.util.bottomBarClearance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.detailline.callfollowcrm.presentation.component.NavAppPickerDialog
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextInfo
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.presentation.theme.TossWarning
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppSize
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.LightColors
import com.detailline.callfollowcrm.presentation.theme.CallFollowCrmTheme
import androidx.compose.ui.tooling.preview.Preview
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import com.detailline.callfollowcrm.util.MoneyFormatter
import androidx.compose.material.icons.filled.ChevronRight
import com.detailline.callfollowcrm.util.PhoneKey


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
    /** 홈 "다음 시공" 카드 → 일정 탭에서 그 날 선택해 진입. (2026-06-07) */
    onOpenScheduleAtDay: (Long) -> Unit = {},
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
    /** 홈 "협업 진행" 배너 탭 → 협업 현장. (2026-06-09) */
    onOpenCollabSites: () -> Unit = {},
    onOpenCollabSiteDetail: (String) -> Unit = {},   // 협업 카드 각 줄 → 그 현장 상세 (2026-06-21 사장님)
    /** 상담함 "부재중 자동답장" 알림 길게누름 → 자동 문자 설정(부재중 응답 펼침). (2026-06-06) */
    onOpenAutoSmsSettings: () -> Unit = {},
    /** 막내 팁 카드 → 해당 기능 라우트로 이동(제네릭). (2026-07-04 사장님) */
    onOpenRoute: (String) -> Unit = {},
    /** 홈 상단 [QR] → PC 웹 로그인 QR 스캔(빠른 접근). (2026-08-31 사장님) */
    onScanWebQr: () -> Unit = {},
    /** 📮 홈 맨 위 주황 띠 → 「아직 못 보낸 것」 목록. (2026-10-04 우체통 2단계) */
    onOpenOutboxTrouble: () -> Unit = {},
    /** "{업종} AI" 뱃지 탭 → 업종 선택(대표업종 1개). 업종 데이터 수집 → 시공 시장 빅데이터. (2026-09-01 사장님) */
    onOpenTradeSelect: () -> Unit = {}
) {
    val timeline by viewModel.timeline.collectAsState()
    // 상단 고정(핀) 거래처 suffix — 상담함 '고정' 칸 분리 + '답장 기다려요' 제외. (2026-08-24 사장님)
    val pinnedSuffixes by viewModel.pinnedSuffixes.collectAsState()
    val filter by viewModel.filterState.collectAsState()
    // 광고 자동감지 — "이건 광고 아냐" 예외 목록 + 광고함 펼침 상태. (2026-07-08 사장님)
    val adAllowlist by viewModel.adAllowlist.collectAsState()
    var adBoxExpanded by rememberSaveable { mutableStateOf(false) }
    // 🏷️ 상담함 칩 (2026-09-20 사장님 "거르기로 가자") — 탭 두 개를 칩 한 줄로.
    //   왼쪽은 오늘 할 일, 오른쪽은 사람 찾기. "msg" 만 본문이 문자함으로 바뀌고 나머지는 **같은 목록을 거른다.**
    var inboxChip by rememberSaveable { mutableStateOf("all") }
    /** [문자함] 안에서 보는 갈래 — "ad"(광고·인증) / "parcel"(택배). 합치면 문자함 전부. */
    var boxSub by rememberSaveable { mutableStateOf("ad") }
    // 🔵 **앱을 새로 켰으면 무조건 [전체].** (2026-09-24 사장님 "항상 전체가 떠야 해")
    //   칩이 남아 있으면 다음에 열 때 텅 빈 목록이 떠서 "문자가 다 날아갔다" 로 보인다.
    //   같은 실행 안의 이동(채팅 갔다 뒤로가기 등)에선 안 건드린다 — 훑던 칩이 풀리면 더 성가시다.
    LaunchedEffect(Unit) {
        if (homeChipFreshLaunch) {
            homeChipFreshLaunch = false
            inboxChip = "all"
            boxSub = "ad"
        }
    }

    // 없앤 칩([새 번호])을 고른 채로 앱을 닫았으면 그 값이 남아 **아무 칩도 안 켜진 화면**이 된다.
    LaunchedEffect(inboxChip) {
        // 없앤 칩([새 번호]·[답장 대기])을 고른 채 앱을 닫았으면 그 값이 남아
        //   **아무 칩도 안 켜진 화면**이 된다. [택배]·[광고] 는 [문자함] 으로 합쳐졌다.
        when (inboxChip) {
            "newnum", "unhandled" -> inboxChip = "all"
            "parcel" -> { inboxChip = "box"; boxSub = "parcel" }
            "ad" -> { inboxChip = "box"; boxSub = "ad" }
        }
    }

    // 하단 탭의 빨간 숫자를 누르면(이미 상담함일 때) 안 챙긴 것만 거른다. 칩은 없다 — 숫자가 곧 필터.
    val jumpTick by viewModel.jumpToUnhandled.collectAsState()
    // ⚠️ 이 신호는 **앱이 살아 있는 동안 값이 남는다**(누른 시각). 그래서 다른 탭 갔다가
    //   상담함으로 다시 들어오면 그 **옛날 신호**로 또 불려서, 누르지도 않았는데
    //   [안 챙긴 것] 으로 걸러진 채 열렸다. 답장을 다 해둔 뒤면 목록이 텅 빈다.
    //   (2026-09-24 사장님 "상담함을 누르면 전체 칩으로 가는 게 아니라.. 왜 비어있지?")
    //   → **들어온 뒤 새로 올라온 신호만** 듣는다.
    val jumpSeen = remember { jumpTick }
    LaunchedEffect(jumpTick) { if (jumpTick > jumpSeen) inboxChip = "pending" }

    // 상담함/문자함 전환 (2026-07-11 사장님) — 0=상담함, 1=문자함(고객 아님).
    val generalThreads by viewModel.generalThreads.collectAsState()
    val generalUnread by viewModel.generalUnreadCount.collectAsState()
    val consultUnread by viewModel.consultUnreadCount.collectAsState()
    var inboxTab by rememberSaveable { mutableStateOf(0) }
    // 📬 **상담함 탭을 누를 때마다** 전체로. 탭을 누르는 건 "처음부터 다시 보겠다"는 뜻이다.
    //   채팅 갔다 뒤로가기 같은 같은-화면 이동은 안 건드린다(훑던 칩이 풀리면 성가시다).
    val resetSignal by (LocalContext.current.applicationContext as CallFollowCrmApplication)
        .container.inboxResetFilter.collectAsState()
    // ⚠️ 마지막 본 신호를 기억 — 안 그러면 **챗 갔다 뒤로가기(재진입)마다 옛 신호 값으로 또 발동**해 칩이 전체로 풀렸다.
    //   (2026-10-05 사장님 "칩 안에서 문자 보고 뒤로가면 전체로 감". [[reference_appscoped_tick_launchedeffect_refires]] 패턴)
    //   rememberSaveable 라 뒤로가기(재진입)에도 값이 남아, 탭을 **새로 누를 때만**(신호 증가) 리셋된다.
    var lastResetSeen by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(resetSignal) {
        if (resetSignal > lastResetSeen) {
            lastResetSeen = resetSignal
            inboxChip = "all"; inboxTab = 0; boxSub = "ad"
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val aiCardSummaries by viewModel.cardSummariesByPhoneSuffix.collectAsState()
    // 카톡식 읽음 추적 (2026-06-08) — "최근 대화" 파란 점 계산. 채팅 열면 갱신 → 점 사라짐.
    val readStates by viewModel.readStates.collectAsState()
    val waitingReplies by viewModel.waitingReplies.collectAsState()
    val waitingReplyChoices by viewModel.waitingReplyChoices.collectAsState()
    val categoriesById by viewModel.categories.collectAsState()
    // 줄마다 firstOrNull 로 전체 카테고리를 훑던 것 → 맵 한 번(대화 200줄이면 200번 훑던 일). (2026-09-15)
    val categoryById = remember(categoriesById) { categoriesById.associateBy { it.id } }
    val todayNew by viewModel.todayNewInquiryCount.collectAsState()
    // 🔢 **카드 숫자는 목록에서 뽑는다.** (2026-09-24 사장님 "신규 연락이면 여기 연락이 있어야지")
    //   전엔 숫자와 목록이 다른 잣대였다 — 숫자는 문자·통화에서 바로 세고, 목록은 그 위에
    //   스팸·문자함·광고를 한 번 더 걸렀다. 그래서 "1통" 인데 목록은 비는 일이 생겼다.
    //   숫자와 목록이 서로 다른 말을 하면 둘 다 못 믿는다 → **화면에 뜰 줄만** 센다.
    val todayNewShown = remember(timeline) {
        timeline.flatMap { g -> g.items }
            .distinctBy { PhoneKey.of(it.record.phoneNumber) }
            .count { it.isNewToday }
    }
    // 숫자엔 잡혔는데 목록엔 없는 것 = **광고·문자함으로 간 것.** 조용히 지우지 말고 어디 갔는지 말한다.
    val todayNewElsewhere = (todayNew - todayNewShown).coerceAtLeast(0)
    val yesterdayNew by viewModel.yesterdayNewInquiryCount.collectAsState()
    val unhandled by viewModel.unhandledCount.collectAsState()
    val weekScheduled by viewModel.thisWeekScheduledCount.collectAsState()
    val outstandingTotal by viewModel.outstandingTotal.collectAsState()
    val outstandingCount by viewModel.outstandingCount.collectAsState()
    val todayJobs by viewModel.todayJobs.collectAsState()
    val nextJobs by viewModel.nextJobs.collectAsState()
    val autoReplies by viewModel.autoReplies.collectAsState()
    val teamUpdates by viewModel.teamUpdates.collectAsState()
    val collabUpdates by viewModel.collabUpdates.collectAsState()
    val pendingInvites by viewModel.pendingCollabInvites.collectAsState()
    val collabUpcoming by viewModel.collabUpcoming.collectAsState()
    // 띠가 쓸 '다음 협업' — 오늘 것 말고 앞으로 것 중 가장 빠른 하나. (2026-09-21 A안)
    val collabBandNext = remember(collabUpcoming) {
        val d0 = DateTimeUtils.startOfDay(System.currentTimeMillis())
        collabUpcoming.filter { it.scheduledAtMs > 0L && DateTimeUtils.startOfDay(it.scheduledAtMs) > d0 }
            .minByOrNull { it.scheduledAtMs }
    }
    val recurringDueCount by viewModel.recurringDueCount.collectAsState()
    val scheduleReminders by viewModel.scheduleReminders.collectAsState()
    val balanceDues by viewModel.balanceDues.collectAsState()
    // 🏷️ 칩에 붙는 숫자 — **할 일만** 센다. "시공 끝남" 이 40명이어도 빨갛게 쓰지 않는다(할 일이 아니니까).
    val inboxChipCounts = remember(timeline, balanceDues) {
        val dues = balanceDues.mapNotNull { it.customerId }.toHashSet()
        val all = timeline.flatMap { g -> g.items }
            .distinctBy { PhoneKey.of(it.record.phoneNumber) }
        // 🔴 **빨간 숫자는 뜻이 하나다 — "내가 손댈 게 몇 개인가".** (2026-09-20 사장님)
        //   전엔 "몇 명인가" 를 셌다. 그래서 시공이 열 건 잡히면 **잡혀 있다는 이유로** 빨간 10 이 늘 떠 있었다.
        //   사장님: *"잡혀있어서 뜨는게 아니라... 시공대기 목록 인원한테 문자가 오면 숫자가 뜨는 것"*
        //   → 오늘 신규·시공 대기도 **답 안 한 것만** 센다. 목록은 전부 보여주되
        //     안 챙긴 게 위로 오므로(waiting 섹션이 먼저) 숫자가 가리키는 줄이 바로 보인다.
        fun pending(it: HomeItem) = it.isUnconfirmed
        // 🔧 **AS 찍힌 손님은 다른 일 칩에서 모두 빠진다.** (2026-09-27 사장님)
        //   "as는 시공이 이미 끝나서 잔금까지 다 받은 사람인 경우가 많아"
        //   섞여 있으면 「시공 대기」·「잔금 대기」 숫자가 거짓말이 된다.
        fun isAs(it: HomeItem) = it.customer?.asPendingAt != null
        mapOf(
            "today" to all.count { it.isNewToday && pending(it) && !isAs(it) },
            "unhandled" to all.count { pending(it) },
            // 🔧 AS — 0명이면 칩 자체를 안 띄운다(아래 ChipRow).
            "as" to all.count { isAs(it) },
            "wait" to all.count {
                val c = it.customer
                // 목록과 **같은 규칙**이어야 한다 — 숫자가 3인데 열면 5줄이면 둘 다 못 믿는다.
                c != null && !isAs(it) &&
                    (c.scheduledWorkDate ?: 0L) >= DateTimeUtils.startOfDay(System.currentTimeMillis()) &&
                    !c.isWorkDone && pending(it)
            },
            // 미수는 연락이 와서가 아니라 **받을 돈이 남아서** 할 일이다 — 건수 그대로.
            "owe" to all.count { it.customer?.id in dues && it.customer?.asPendingAt == null }
        )
    }
    // 📦 각 칩에 **멤버가 있나** — 칩을 띄울지 판단(목록 필터와 같은 조건). 셈은 inboxChipMembers 한 곳. (2026-10-08 사장님)
    val chipHasMember = remember(timeline, balanceDues) {
        val all2 = timeline.flatMap { it.items }.distinctBy { PhoneKey.of(it.record.phoneNumber) }
        inboxChipMembers(all2, balanceDues.mapNotNull { it.customerId }.toHashSet(),
            DateTimeUtils.startOfDay(System.currentTimeMillis()))
    }
    val estimateFollowupCount by viewModel.estimateFollowupCount.collectAsState()
    val estimateFollowupDismissed by viewModel.estimateFollowupDismissed.collectAsState()
    // 🕐 예약한 문자 — 상담함 위 카드·예약함 시트. (2026-10-05 사장님)
    val scheduledPending by viewModel.scheduledPending.collectAsState()
    var showScheduledSheet by remember { mutableStateOf(false) }
    val recurringDueDismissed by viewModel.recurringDueDismissed.collectAsState()
    val isInitialSmsLoading by viewModel.isInitialSmsLoading.collectAsState()
    val updateAvailable by viewModel.updateAvailable.collectAsState()
    val updateDateLabel by viewModel.updateDateLabel.collectAsState()   // "8월 24일" — 배너 신뢰용. (2026-08-24 사장님)
    val outboxTroubleCount by viewModel.outboxTroubleCount.collectAsState()   // 📮 아직 못 보낸 것 수(주황 띠)
    // '새로워졌어요' 시트 제거 (2026-07-29 사장님 — Play 배포로 이관, 앱 팝업 불필요). 배너([지금 받기])만 유지.

    // 서버 상태 indicator — AppContainer 의 ServerHealthMonitor 를 직접 구독.
    // 30초마다 GET /health 호출 → 결과 반영. 사장님만 알아볼 작은 동그라미. tap = Toast 안내.
    val context = LocalContext.current
    val serverHealth = remember {
        (context.applicationContext as CallFollowCrmApplication).container.serverHealth
    }
    val prefs = remember { (context.applicationContext as CallFollowCrmApplication).container.preferences }
    // 📦 칩 '자라남' — 처음 생기면 계속 남고(0명이면 흐리게), 처음 생길 때 한 번 안내. (2026-10-08 사장님)
    var chipsSeen by remember { mutableStateOf(prefs.inboxChipsSeen) }
    var newChipKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(chipHasMember, isInitialSmsLoading) {
        if (!prefs.inboxChipsSeeded) {
            // 첫 실행: 자료 로드된 뒤, 이미 쓰던 사람(칩 있음)은 조용히 '본 것'으로 심어 안내가 안 쏟아지게.
            //   새로 깐 사람은 비어 있어 그대로 — 쓰면서 칩이 자라날 때 안내가 뜬다.
            if (!isInitialSmsLoading) {
                chipsSeen = chipHasMember; prefs.inboxChipsSeen = chipHasMember; prefs.inboxChipsSeeded = true
            }
            return@LaunchedEffect
        }
        val fresh = chipHasMember - chipsSeen
        if (fresh.isNotEmpty()) {
            chipsSeen = chipsSeen + fresh
            prefs.inboxChipsSeen = chipsSeen
            newChipKey = fresh.firstOrNull { inboxChipFirstNote(it) != null }
        }
    }
    val makneContainer = remember { (context.applicationContext as CallFollowCrmApplication).container }
    val serverAlive by serverHealth.alive.collectAsState()
    val lastOkAtMs by serverHealth.lastOkAtMs.collectAsState()

    // 화면 진입 + 돌아올 때마다(ON_RESUME) SMS/CallLog 동기화.
    // + CallLog → Room sync (2026-05-28 사장님 통점):
    //   Android 12+ / OneUI 가 정적 BroadcastReceiver 누락하면 통화 종료 감지 못 함 → 진입 시 폴링으로 보완.
    // 2026-06-18 사장님 통점("통화/문자 끝나면 즉시 상담함에 반영 안 됨"): LaunchedEffect(Unit)은 첫 진입 1회뿐 —
    //   통화 중 OEM(삼성)이 content observer 를 얼리면, 통화 끝나고 '이미 떠 있던' 홈으로 돌아와도 재동기 안 됨.
    //   → ON_RESUME 마다 동기 → 통화/문자 끝내고 앱 보는 순간 ~1초 안에 최신화(addObserver 가 첫 진입도 커버).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSmsContacts()
                viewModel.syncRecentCallLog(context)
                // 백그라운드 동안 멈춰 있던 동기화(새 문자·접수서·협업)를 복귀 즉시 1회 실행 → 껐다 켠 효과.
                //   (2026-06-28 사장님: 알림은 오는데 화면 미반영 → 60초/재시작 안 기다리고 바로 반영)
                (context.applicationContext as? com.detailline.callfollowcrm.CallFollowCrmApplication)?.requestSyncNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
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
                context, "한 번 더 누르면 앱이 꺼져요", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 미확인 swipe-to-spam Snackbar Undo.
    val snackbarHostState = remember { SnackbarHostState() }
    // 💬 ViewModel 이 보낸 한마디 → 스낵바. 고객 상세가 쓰는 그 방식 그대로. (2026-10-02)
    val vmToast by viewModel.toast.collectAsState()
    LaunchedEffect(vmToast) {
        vmToast?.let { msg -> snackbarHostState.showSnackbar(msg); viewModel.consumeToast() }
    }

    // 오늘 시공 히어로 [완료] → 프로토 openComplete 팝업 (시공 완료 · 고생하셨습니다).
    var completeTarget by remember { mutableStateOf<com.detailline.callfollowcrm.data.local.entity.CustomerEntity?>(null) }
    // 협업 완료 알림 [입금했어요] → 일당 지급 금액 입력 대상. (일당 마켓 Phase 1)
    var payTarget by remember { mutableStateOf<com.detailline.callfollowcrm.ai.CollabEventCenter.CollabUpdate?>(null) }
    // 대기 카드 꾹 누르면 뜨는 '스팸 등록 / 정리' 선택. null=닫힘. (2026-06-23 사장님)
    var spamTarget by remember { mutableStateOf<HomeItem?>(null) }
    // 방(최근 대화·고정) 꾹 누르면 뜨는 '맨 위에 고정 / 해제' 선택. null=닫힘. (2026-08-24 사장님)
    var pinTarget by remember { mutableStateOf<com.detailline.callfollowcrm.presentation.screen.home.HomeItem?>(null) }
    // 대기카드 비행기 → '확인 후 발송' 다이얼로그 대상 + 카드에서 고른 답변. (2026-07-02 사장님)
    var waitingSendTarget by remember { mutableStateOf<HomeItem?>(null) }
    var waitingSendReply by remember { mutableStateOf<String?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // 목록 스크롤 위치 — **앱바가 알아야** 검색창을 접을 수 있어 Scaffold 밖으로 올렸다. (2026-09-19)
    val listState = rememberLazyListState()
    /** 목록이 맨 위인가 — 검색창·칩 줄을 **내리면 접는** 기준. (사장님: "정리는 스크롤 자동 숨김으로") */
    val atTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40
        }
    }
    // 칩을 바꾸면 **맨 위부터** 보여준다. (2026-09-20 실기)
    //   목록만 갈리고 스크롤 위치가 남아서, [새 번호] 를 누르면 첫 줄이 잘린 중간부터 보였다.
    // 🔖 **칩을 바꿀 때만** 맨 위로 — 화면에 **다시 들어올 때**는 건드리지 않는다. (2026-10-04 사장님 완성도)
    //   LaunchedEffect 는 재진입마다 한 번 돈다. 전엔 그래서 대화 갔다 뒤로 오면
    //   훑던 자리가 **맨 위로 튕겼다**. (제미나이 완성도 ③: 뒤로가기 스크롤 복원)
    //   lastChip 은 remember(비-Saveable)라 재진입 때 **복원된 칩으로 다시 맞춰져** → 그 순간엔 안 튄다.
    var lastChipForScroll by remember { mutableStateOf(inboxChip) }
    LaunchedEffect(inboxChip) {
        if (inboxChip != lastChipForScroll) {
            lastChipForScroll = inboxChip
            runCatching { listState.scrollToItem(0) }
        }
    }

    // Scaffold 를 Box 로 감싸 그 위(홈 콘텐츠 전체를 덮는 z-레벨)에 업데이트 시트를 오버레이. (2026-07-18 사장님)
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            // 프로토 .appbar — "상담함" + 오늘 날짜 + AI 배지(서버 상태 흡수).
            //   기존 달력/문서/설정 아이콘은 하단 5탭(일정·더보기)이 대체 → 프로토대로 제거.
            val todayLabel = remember {
                java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREAN)
                    .format(java.util.Date())
            }
            // 프로토 renderAiBadge: "{대표 업종} AI".
            // 업종 미선택 fallback 을 "줄눈" → "시공" 으로. (2026-09-16 사장님)
            //   온보딩에서 업종을 안 묻기로 하면서 **대부분이 미선택 상태로 들어온다.**
            //   그때 타일·도배 사장님 폰에 "줄눈 AI" 가 떠 있으면 남의 앱처럼 보인다.
            //   모르면 모르는 대로 중립적으로 — 탭하면 업종 선택으로 간다(onOpenTradeSelect).
            val ownerTrade = remember {
                (context.applicationContext as CallFollowCrmApplication).container.preferences
                    .ownerTrades.firstOrNull()?.takeIf { it.isNotBlank() } ?: "시공"
            }
            Column(Modifier.fillMaxWidth().background(TossGrayBg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    // top 여백 ↑ — 엣지투엣지 OFF 라 statusBarsPadding=0, 제목이 상태바에 붙던 것 완화(2026-06-04).
                    .padding(start = 18.dp, end = 18.dp, top = 28.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    // 화면 제목 = display(26 ExtraBold) · 날짜 = caption(13 Medium, 보조 정보)
                    Text(if (inboxTab == 0) "상담함" else "문자함", style = AppType.display, color = AppTheme.colors.text)
                    Text(todayLabel, style = AppType.caption, color = AppTheme.colors.textHint)
                }
                AiBadge(
                    trade = ownerTrade,
                    alive = serverAlive,
                    // 탭 → 업종 선택(대표업종 1개). 업종 데이터 수집 → 시공 시장 빅데이터. 서버상태는 뱃지 점 색으로 표시. (2026-09-01 사장님)
                    onClick = onOpenTradeSelect
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
                // PC 웹 QR 빠른 로그인 — 어디서든 한 탭에 스캐너. (2026-08-31 사장님)
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.White)
                        .clickable { onScanWebQr() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.QrCodeScanner, "PC 웹 QR", tint = TossTextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            // 🔍 **검색창** — 돋보기 아이콘을 창으로 승격. (2026-09-19 사장님 · 프로토 Wb1zoMZT)
            //   창 안 글자가 곧 안내문이다. 에이닷이 잘한 게 그거였다.
            //   목록을 내리면 접는다 — 맨 위에서만 보인다. ("정리는 스크롤 자동 숨김으로")
            androidx.compose.animation.AnimatedVisibility(visible = atTop) {
                // 두 문장을 번갈아 — 한 줄에 다 넣으면 길어서 안 읽힌다.
                val hints = listOf("이름·주소·금액·통화 내용까지", "\"동탄\" · \"미수\" · \"9월\" 도 찾아져요")
                var hintIdx by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) {
                    while (true) { kotlinx.coroutines.delay(4000); hintIdx = (hintIdx + 1) % hints.size }
                }
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White)
                        .clickable { onOpenSearch() }
                        .padding(horizontal = 15.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = TossTextTertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    androidx.compose.animation.Crossfade(targetState = hintIdx, label = "hint") { i ->
                        Text(
                            hints[i], fontSize = 13.5.sp, color = TossTextTertiary,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
            }
        },
        // 프로토 상담함엔 수동 입력 FAB 없음 — 2026-06-02 사장님 요청으로 제거.
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        Column(
            Modifier
                // ⚠️ inner 전체를 넣으면 하단 내비바 인셋이 AppRoot 탭바 인셋과 '이중'으로 붙어
                //    상담함 맨 아래 큰 흰 공백이 생김(사장님 반복 신고). 상단(탑바/상태바)만 적용. (2026-07-02)
                .padding(top = inner.calculateTopPadding())
                .fillMaxSize()
                .background(TossGrayBg)
        ) {
            // 📮 아직 못 보낸 것 띠 — 눌러서 목록. dead/하루넘김일 때만. 조각은 presentation/component/OutboxBand.kt (size_guard §12-F)
            com.detailline.callfollowcrm.presentation.component.OutboxBand(outboxTroubleCount) { onOpenOutboxTrouble() }

            // 새 버전 배너 — 한 줄로 간결. [지금 받기] = 바로 다운로드. ('새로워졌어요' 시트는 2026-07-29 제거)
            if (updateAvailable) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(TossBlue)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.detailline.callfollowcrm.presentation.theme.AiMark(Color.White, 15.dp, 6.dp)
                    Text(
                        if (updateDateLabel.isNotBlank()) "${updateDateLabel} 새 버전이 나왔어요" else "새 버전이 나왔어요",
                        color = Color.White, fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    // 버튼은 둥근 네모(12). 알약 버튼을 만들지 않는다. (2026-09-20 사장님)
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
                            .clickable { openInstallPage(context) }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) { Text("업데이트", color = TossBlue, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp) }
                }
            }

            // 상담함 | 문자함 폴더 탭 (크롬 탭식 전환) — 2026-07-11 사장님.
            //   ⚠️ 본문 전환은 아래 Box(nestedScroll)를 if/else 로 감싼다. Composable 안에서 early return@Column 하면
            //      슬롯테이블 group 이 어긋나 recompose 시 크래시(Stack.pop AIOOBE) — 반드시 if/else 로. (2026-07-12)
            // 🏷️ 칩 한 줄 — 탭 두 개가 쓰던 자리. (2026-09-20 · 프로토 6qoXfXjd)
            //   ⚠️ 옛 InboxFolderTabs 는 안 지웠다. 되돌릴 땐 이 줄만 바꾸면 된다.
            // 📉 **내리면 접는다.** (2026-09-20 사장님 "칩이 많이 보이는게 거슬리는데")
            //   검색창이 이미 이렇게 돈다 — 새로 배울 게 없다.
            //   ⚠️ **필터를 켠 상태면 안 접는다.** 칩이 사라지면 지금 뭘 보는지 알 수가 없다.
            androidx.compose.animation.AnimatedVisibility(visible = atTop || inboxChip != "all") {
                InboxChips(
                    selected = inboxChip,
                    onSelect = { key ->
                        inboxChip = key
                        // [문자함] 만 비고객 본문을 쓴다. 나머지는 상담함 목록을 거른다.
                        inboxTab = if (key == "box") 1 else 0
                    },
                    counts = inboxChipCounts,
                    generalBadge = generalUnread,
                    seen = chipsSeen
                )
            }
            // 📦 칩이 처음 생겼을 때 그 밑에 딱 한 번 안내. (2026-10-08 사장님)
            newChipKey?.let { key ->
                val note = inboxChipFirstNote(key)
                if (note != null) NewChipNote(inboxChipLabel(key), note) { newChipKey = null }
                else newChipKey = null
            }

            // 📬 **전부 읽음으로** — 안 읽은 게 있을 때만 뜬다. (2026-10-01 사장님)
            //   사장님: "상담함 1이 안 사라진데.. 아마 광고문자 아닐까 싶어.
            //            전부 읽음으로 처리 있어야 하지 않을까"
            //   광고엔 답장을 할 수가 없다 → 답장으로만 지워지던 숫자는 지울 길이 없었다.
            //   🔒 평소엔 **아예 없다** — 화면을 더 어수선하게 만들지 않는다.
            //      지우는 건 빨간 숫자뿐이고 대화는 그대로라, 묻지 않고 바로 하되 **되돌리기**를 준다.
            val boxNow = inboxTab == 1
            val unreadNow = if (boxNow) generalUnread else consultUnread
            androidx.compose.animation.AnimatedVisibility(visible = unreadNow > 0) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = AppSpace.s8),
                    horizontalArrangement = Arrangement.End
                ) {
                    // 🖐️ **손가락이 닿는 크기.** (2026-10-01 사장님 "파란 점도 없어져야하는거아니야?")
                    //   처음엔 글자에 8·4dp 만 둘러 높이가 ~25dp 였다. 내 adb 탭은 정중앙을 찍어
                    //   들어갔지만 **손가락으로는 빗나간다** — 눌렀는데 아무 일도 안 일어난 것이다.
                    //   누르는 것은 48dp 는 돼야 한다.
                    Box(
                        Modifier.heightIn(min = 48.dp).clip(AppShape.sm)
                            .clickable {
                                // 📣 **눌렀다는 티를 낸다.** 조용히 사라지기만 하면 눌렸는지 알 수가 없다.
                                //   백 개를 한 번에 비우는 일이라 **되돌리기**도 같이 준다.
                                val n = viewModel.markAllRead(boxNow)
                                if (n > 0) scope.launch {
                                    val r = snackbarHostState.showSnackbar(
                                        "${n}개를 읽음으로 바꿨어요",
                                        actionLabel = "되돌리기",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (r == SnackbarResult.ActionPerformed) viewModel.undoMarkAllRead()
                                }
                            }
                            .padding(horizontal = AppSpace.s12),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✓ 전부 읽음으로",
                            style = AppType.label,
                            fontWeight = FontWeight.Bold,
                            color = TossBlue
                        )
                    }
                }
            }

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
            // 최근 대화 스크롤 버벅임 fix: 한 카드에 전체를 한 프레임에 그리면 끊김 → 기본 일부만, 나머지는 "더 보기".
            // rememberSaveable — 대화 다녀와도 '이전 대화 더 보기' 펼침·스크롤 위치 유지(리셋 방지). (2026-09-01 사장님)
            //
            // 2026-09-15 사장님 "이전 대화 더보기 눌렀는데 엄청 버벅이는 느낌":
            //   전엔 누르면 **남은 대화 전부**를 한 프레임에 그렸다(대화가 200개면 200줄을 한 번에) → 그 순간 뚝 끊김.
            //   최근 대화는 한 흰 카드 안(= 한 덩어리)이라 화면 밖 줄도 미리 다 그려야 해서 더 무겁다.
            //   → 한 번 누를 때 RECENT_MORE(30줄)씩만 늘린다. 한 번의 일감이 작아져 끊김이 안 느껴진다.
            var recentShown by rememberSaveable { mutableStateOf(RECENT_FIRST) }
            // 막내 팁 카드 — 눌러본(used)/닫은(dismissed) 건 prefs 로 영구 기억 + 화면 상태로 즉시 반영. (2026-07-04 사장님)
            var tipUsed by remember { mutableStateOf(prefs.makneTipUsed) }
            var tipDismissed by remember { mutableStateOf(prefs.makneTipDismissed) }
            var tipImpressions by remember { mutableStateOf(prefs.makneTipImpressions) }
            var guideTip by remember { mutableStateOf<com.detailline.callfollowcrm.presentation.component.MakneTip?>(null) }
            // "이미 쓰는 기능" 신호 — 이미 쓰는 건 광고 안 함(맥락 추천). 로컬 조회 1회. null=로딩중. (2026-07-05 사장님)
            var tipUsingSignals by remember { mutableStateOf<Set<String>?>(null) }
            LaunchedEffect(Unit) {
                val using = mutableSetOf<String>()
                runCatching { if (makneContainer.pricingItemRepository.observeActive().first().isNotEmpty()) using += "price" }
                runCatching { if (makneContainer.recurringMessageRepository.observeRules().first().isNotEmpty()) using += "recur" }
                runCatching { if (makneContainer.messageTemplateRepository.observeAll().first().any { !it.isDefault }) using += "tpl" }
                if (prefs.bizName.isNotBlank() && prefs.bizSeal.isNotBlank()) using += "quote"
                if (prefs.toneUploadConsented) using += "tone"
                tipUsingSignals = using
            }
            // 노출 대상: 안 눌러봄·안 닫음 + 노출캡(4회) 미만 + 이미 쓰는 기능 제외 + settle 은 미수금 있을 때만. 덜 본 것 먼저(로테이션). (2026-07-05 사장님)
            fun computeTipCandidates(): List<com.detailline.callfollowcrm.presentation.component.MakneTip> {
                val sig = tipUsingSignals
                if (!prefs.makneTipsEnabled || sig == null) return emptyList()
                return com.detailline.callfollowcrm.presentation.component.MAKNE_TIPS.filter { t ->
                    t.key !in tipUsed && t.key !in tipDismissed &&
                        (tipImpressions[t.key] ?: 0) < 4 &&
                        t.key !in sig &&
                        (t.key != "settle" || outstandingCount > 0)
                }.sortedBy { tipImpressions[it.key] ?: 0 }
            }
            // 세션당 노출 카드 고정(화면 내 재정렬 방지) + 노출 카운트 1회(스로틀 20분). 여러 번 봐도 안 누르면 캡에서 은퇴.
            var displayedTips by remember { mutableStateOf<List<com.detailline.callfollowcrm.presentation.component.MakneTip>>(emptyList()) }
            LaunchedEffect(tipUsingSignals) {
                if (tipUsingSignals == null) return@LaunchedEffect
                val picks = computeTipCandidates().take(3)
                displayedTips = picks
                val now = System.currentTimeMillis()
                if (picks.isNotEmpty() && now - prefs.lastTipImpressionMs >= 20 * 60 * 1000L) {
                    val m = prefs.makneTipImpressions.toMutableMap()
                    picks.forEach { m[it.key] = (m[it.key] ?: 0) + 1 }
                    prefs.makneTipImpressions = m; prefs.lastTipImpressionMs = now; tipImpressions = m
                }
            }
            // 눌렀거나 닫은 카드는 즉시 사라지게(세션 고정 목록에서 걸러 렌더).
            val shownTips = displayedTips.filter { it.key !in tipUsed && it.key !in tipDismissed }
            // 카드 탭 = 먼저 막내 안내판만(아직 이동 X). '해볼게요' 눌러야 기능으로 이동 + 안 써본 목록에서 뺌. (2026-07-04 사장님)
            fun onTipProceed(tip: com.detailline.callfollowcrm.presentation.component.MakneTip) {
                tipUsed = tipUsed + tip.key; prefs.makneTipUsed = tipUsed
                onOpenRoute(tip.route)
            }
            fun onTipDismiss(tip: com.detailline.callfollowcrm.presentation.component.MakneTip) {
                tipDismissed = tipDismissed + tip.key; prefs.makneTipDismissed = tipDismissed
            }
            // 막내 안내판 — 카드 누르면 뜸. '해볼게요'=이동 / '다음에 볼게요'·바깥탭=닫힘(안 들어감).
            guideTip?.let { tip ->
                com.detailline.callfollowcrm.presentation.component.MakneGuideOverlay(
                    tip = tip,
                    onProceed = { guideTip = null; onTipProceed(tip) },
                    onDismiss = { guideTip = null }
                )
            }

            // 카드 탭 인라인 액션 — 한 번에 하나만 펼침. key 포맷은 LazyColumn key 와 동일.
            // 회전/recompose 살아남게 rememberSaveable. null = 모두 접힘.
            var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }

            // [📍 길찾기] 첫 사용 시 네비 앱 선택 다이얼로그 (2026-05-27).
            //   non-null = 다이얼로그 떠 있는 상태. 어떤 phone 에 대해 띄웠는지 기억 → 선택 후 그 phone 의 주소 resolve + launch.
            //   사장님이 SettingsScreen 에서 미리 골랐으면 prefs.defaultNavAppKey != null → 다이얼로그 X, 즉시 launch.
            var navDialogPhone by remember { mutableStateOf<String?>(null) }
            var navDialogAddr by remember { mutableStateOf<String?>(null) }
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
            // 협업 현장 — 주소가 이미 있어서 phone lookup 없이 바로 길찾기.
            fun launchNavigationForAddr(addr: String?) {
                val navApp = com.detailline.callfollowcrm.util.NavApp.fromKey(prefs.defaultNavAppKey)
                if (navApp == null) {
                    navDialogAddr = addr
                } else {
                    scope.launch { com.detailline.callfollowcrm.util.NavLauncher.launch(context, navApp, addr) }
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
            // 협업 현장 길찾기 앱 선택 (주소 기반).
            navDialogAddr?.let { pendingAddr ->
                NavAppPickerDialog(
                    onPick = { picked ->
                        prefs.defaultNavAppKey = picked.key
                        navDialogAddr = null
                        scope.launch { com.detailline.callfollowcrm.util.NavLauncher.launch(context, picked, pendingAddr) }
                    },
                    onDismiss = { navDialogAddr = null }
                )
            }

            // 대기/최근 공용 행 렌더러 — 프로토 waiting-card·recent-row 의 공통 기반(HomeRow).
            //   expandedKey/scope/콜백을 클로저로 캡처해 두 섹션에서 동일하게 호출.
            val homeItemRow: @Composable (HomeItem) -> Unit = { item ->
                val suffix = PhoneKey.of(item.record.phoneNumber)
                val rowKey = "row-${item.record.id}-${item.record.phoneNumber}"
                val rowCategory = item.customer?.categoryId?.let { cid ->
                    categoryById[cid]
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
            // 상담함이면 아래 Box(리스트+다이얼로그), 문자함이면 MessageBoxSection. if/else 로 group 균형 유지. (2026-07-12)
            if (inboxTab == 0) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(pullState.nestedScrollConnection)
            ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 프로토 today-new-slot — "오늘 신규 문의 N통 / 어제 M통".
                //   2026-09-20: **[오늘 신규] 칩 안에서만** 보여준다. 전체 화면에선 칩에 숫자가 이미 있다.
                //   어제와 비교하는 말은 칩이 못 하니 카드로 남긴다.
                //   0 통이면 카드를 안 띄운다 — "오늘 신규 문의 0통" 아래 "여기 아무도 없어요" 가
                //   같은 말을 두 번 하고, 오른쪽 '-' 딱지는 뜻이 없었다. (2026-09-20 실기)
                if (inboxChip == "today" && todayNewShown > 0) {
                    item(key = "today-new") {
                        TodayNewCard(todayNew = todayNewShown, yesterdayNew = yesterdayNew, onClick = onOpenNewLeads)
                    }
                }
                // 🔎 걸러진 게 있으면 **어디로 갔는지** 말해준다. 조용히 사라지는 게 제일 나쁘다.
                if (inboxChip == "today" && todayNewElsewhere > 0) {
                    item(key = "today-new-elsewhere") {
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TossGrayBg)
                                .clickable { inboxChip = "box" }
                                .padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${todayNewElsewhere}통은 광고·문자함으로 보냈어요",
                                style = AppType.label, color = TossTextSecondary,
                                modifier = Modifier.weight(1f))
                            Text("보러 가기", style = AppType.label,
                                fontWeight = FontWeight.ExtraBold, color = TossBlue)
                        }
                    }
                }

                // 오늘 시공 히어로 — 시공 당일이면 맨 위 다크 카드(주소+길찾기), 없으면 다음 시공 미리보기.
                //   오늘 협업 현장도 여기에 보라색 카드로 함께 표시. (2026-06-24 사장님)
                // 🔨 띠는 **어느 칩을 보든** 뜬다. (2026-09-20 사장님 "그거 안보이네?")
                //   오늘 시공이 있는지는 미수를 보든 새 번호를 보든 알아야 하는 것이고,
                //   칩을 옮길 때마다 있다 없다 하면 화면이 흔들린다.
                //   (택배·광고는 문자함이라 이 목록 자체를 안 쓴다 → 저절로 안 뜬다.)
                // 걸러졌다는 것과 **나가는 길**을 한 줄로. 칩을 안 되살리는 대신 이게 있어야 한다.
                if (inboxChip == "pending") {
                    item(key = "pending-header") {
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppTheme.colors.primaryBg)
                                .clickable { inboxChip = "all" }
                                .padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("아직 답 안 한 문의만 보는 중", style = AppType.label,
                                color = AppTheme.colors.primary, modifier = Modifier.weight(1f))
                            Text("전체 보기", style = AppType.label, color = AppTheme.colors.primary)
                        }
                    }
                }
                item(key = "today-hero") {
                    val todayDayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
                    val collabTodaySites = collabUpcoming.filter {
                        it.scheduledAtMs > 0L && DateTimeUtils.startOfDay(it.scheduledAtMs) == todayDayStart
                    }
                    // 🔨 **오늘 시공 띠** — 카드 더미를 한 줄로. (2026-09-20 사장님 · 프로토 CitTfr44)
                    //   옛 TodayHeroCard 는 아래에 그대로 남겨뒀다 — 되돌릴 때 이 호출만 바꾸면 된다.
                    TodayBand(
                        todayJobs = todayJobs,
                        nextJobs = nextJobs,
                        collabTodayCount = collabTodaySites.size,
                        collabTodaySites = collabTodaySites,
                        collabNext = collabBandNext,
                        onOpenChat = { phone, cid -> onOpenChat(phone, cid) },
                        onNavigateAddr = { addr -> launchNavigationForAddr(addr) },
                        onComplete = { c -> completeTarget = c },
                        onAddSchedule = onAddSchedule,
                        onOpenSchedule = onOpenSchedule
                    )
                }

                // 내가 수락한 협업 현장(내일 이후) — 오늘 것은 이미 위 히어로에 표시됨. (2026-06-14 사장님)
                val todayDayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
                // 띠에 이미 올라간 협업은 카드에서 뺀다 — 같은 말을 두 번 하면 어수선하다. (2026-09-21)
                val collabFuture = collabUpcoming.filter {
                    (it.scheduledAtMs <= 0L || DateTimeUtils.startOfDay(it.scheduledAtMs) != todayDayStart) &&
                        it.shareId != collabBandNext?.shareId
                }
                if (collabFuture.isNotEmpty()) {
                    item(key = "collab-upcoming") {
                        CollabUpcomingCard(sites = collabFuture, onClick = onOpenCollabSites, onOpenSite = onOpenCollabSiteDetail)
                    }
                }

                // 2026-06-01 프로토 1:1 — KPI 타일·미수금 카드는 프로토 상담함에 없음 → 제거.
                //   (미수금=정산 탭, 오늘신규=아래 today-new 카드, 미확인=지금 답장 기다려요 카운트)

                // 프로토 상담함 조건부 알림 카드 — 전부 'team-alert' 카드 언어(좌측 강조선 + 아이콘 + 제목/태그 + 부제 + go).
                //   순서 = 프로토 슬롯 순서: (pending) 견적회신 → (missed) 자동답장 → (recur) 정기문자 → (d1) 시공안내.
                //   quote/pending(접수서)·call(통화내용)·team-photo(팀)는 서버/팀 의존 → 데이터 생기면 노출(지금 숨김).

                // 🕐 예약한 문자 — 상담함 맨 위. 대기 중인 예약이 있을 때만 보인다(없으면 조용히 숨김). (2026-10-05 사장님)
                if (scheduledPending.isNotEmpty()) {
                    item(key = "scheduled-sms-card") {
                        val next = scheduledPending.minByOrNull { it.sendAtMs }
                        val whenLabel = next?.let {
                            android.text.format.DateFormat.format(
                                "M월 d일 a h시",
                                java.util.Calendar.getInstance().apply { timeInMillis = it.sendAtMs }
                            ).toString()
                        } ?: ""
                        InboxAlert(
                            accent = TossBlue, accentTint = TossBlueSoft,
                            icon = Icons.Default.Schedule,
                            title = "예약한 문자",
                            tagText = "${scheduledPending.size}건", tagBg = AppTheme.colors.primaryBg, tagFg = TossBlue,
                            sub = if (whenLabel.isNotBlank()) "가장 빠른 건 $whenLabel" else "예약 시각에 자동으로 보내요",
                            goLabel = "보기",
                            onClick = { showScheduledSheet = true }
                        )
                    }
                }

                // 견적 회신 챙기기 — 견적 보낸 지 N일 답 없는 고객 (프로토 pending 위치). 밀어서 정리=오늘 숨김.
                if (estimateFollowupCount > 0 && !estimateFollowupDismissed) {
                    item(key = "estimate-followup-card") {
                        DismissSwipeBox(onDismiss = { viewModel.dismissEstimateFollowup() }) {
                            InboxAlert(
                                accent = AppTheme.colors.category, accentTint = AppTheme.colors.categoryBg,
                                icon = Icons.Filled.Description,
                                title = "견적 회신 챙기기",
                                tagText = "미회신", tagBg = AppTheme.colors.cautionBg, tagFg = Color(0xFF8A5300),  // 노랑 위 주황 대비부족 → 진갈색. (2026-08-12 접근성)
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
                                accentTint = if (ar.failed) AppTheme.colors.unpaidBg else TossBlueSoft,
                                icon = Icons.Default.Call,
                                title = "부재중 전화에 자동 답장 보냄",
                                tagText = if (ar.failed) "못 보냄" else null,
                                tagBg = AppTheme.colors.unpaidBg, tagFg = TossError,
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

                // 받은 협업 요청(수락 대기) — 푸시를 실수로 지워도 여기서 찾아 수락. 응답하면 자동으로 빠짐. (2026-06-14 사장님)
                pendingInvites.forEach { inv ->
                    item(key = "collab-invite-${inv.shareId}") {
                        InboxAlert(
                            accent = AppTheme.colors.category,
                            accentTint = AppTheme.colors.categoryBg,
                            icon = Icons.Default.Person,
                            title = "받은 협업 요청",
                            tagText = "수락 대기",
                            tagBg = AppTheme.colors.categoryBg, tagFg = AppTheme.colors.category,
                            // 주소는 짧게 — 이름·주소·일당이 한 줄에 다 들어가게 roughSite(대충 어디)로 축약.
                            //   (siteDisplayName=siteLabel 은 도로명주소를 못 줄여 2줄로 길게 나오던 것 fix. 2026-07-08 사장님)
                            sub = run {
                                val place = com.detailline.callfollowcrm.util.AddressExtractor.roughSite(inv.addr).takeIf { it.isNotBlank() }
                                    ?: com.detailline.callfollowcrm.ai.siteDisplayName(inv)
                                "${inv.ownerName}님 · $place" + (inv.dailyWage?.let { " · 일당 ${it}만원" } ?: "")
                            },
                            goLabel = "수락하러 가기",
                            onClick = onOpenCollabSites
                        )
                    }
                }

                // 협업 진행 알림 (2026-06-09) — 상대 사장 출발/도착/완료. 탭 → 협업 현장. 밀어서 정리.
                collabUpdates.forEach { up ->
                    item(key = "collab-update-${up.eventId}") {
                        val (accent, tint, icon, title, verb) = collabUpdateStyle(up.kind)
                        val subText = "${bossLabel(up.partnerName)} · ${up.timeLabel} · ${up.title} $verb" +
                            (up.accountText?.let { " · 계좌 $it" } ?: "")
                        // A(주인)는 '현장 보기' → 그날 시공한 고객 상세로(빈 협업화면 X). 못 찾으면 협업 현장으로 폴백. (2026-06-14 사장님)
                        val openCollab = {
                            val cid = viewModel.customerIdForShareId(up.shareId)
                            if (cid != null) onOpenCustomerDetail(cid) else onOpenCollabSites()
                        }
                        DismissSwipeBox(onDismiss = { viewModel.dismissCollabUpdate(up.eventId) }) {
                            if (up.kind == "completed" && !up.accountText.isNullOrBlank()) {
                                // 완료+계좌 → 정산 카드 하나로(금액·은행·계좌·예금주·버튼 모두 카드 안). (2026-06-14 사장님 디자인 통합)
                                CollabSettleCard(
                                    up = up,
                                    onOpen = openCollab,
                                    onCopy = {
                                        // 하이픈·공백 빼고 숫자만 복사 → 은행 앱에 바로 붙여넣기. (2026-07-01 사장님)
                                        val copyNo = up.accountNo?.takeIf { it.isNotBlank() }?.filter { it.isDigit() }
                                            ?: (up.accountText ?: "")
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(copyNo))
                                        android.widget.Toast.makeText(context, "계좌번호를 복사했어요", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onPaid = {
                                        // 보낼 금액이 카드에 이미 있으면 다이얼로그 없이 바로 기록(또 적을 필요 X).
                                        //   금액을 모를 때(dailyWage 없음)만 입력 다이얼로그. (2026-07-03 사장님)
                                        val wage = up.dailyWage?.toLong() ?: 0L
                                        if (wage > 0L) {
                                            viewModel.recordLaborPayment(up.partnerName, wage, up.eventId)
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "정산에 일당 지급을 기록했어요",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        } else {
                                            payTarget = up
                                        }
                                    }
                                )
                            } else {
                                InboxAlert(
                                    accent = accent,
                                    accentTint = tint,
                                    icon = icon,
                                    title = title,
                                    tagText = null,
                                    tagBg = tint, tagFg = accent,
                                    sub = subText,
                                    goLabel = "현장 보기",
                                    onClick = openCollab
                                )
                            }
                        }
                    }
                }

                // 정기문자 보낼 때 됐어요 (프로토 recur). 탭 → 보낼 목록. 밀어서 정리=오늘 숨김.
                if (recurringDueCount > 0 && !recurringDueDismissed) {
                    item(key = "recurring-due-card") {
                        DismissSwipeBox(onDismiss = { viewModel.dismissRecurringDue() }) {
                            InboxAlert(
                                accent = AppTheme.colors.caution, accentTint = AppTheme.colors.cautionBg,
                                icon = Icons.Filled.DateRange,
                                title = "오늘 보낼 정기 문자 ${recurringDueCount}건",
                                tagText = "확인 후 발송", tagBg = AppTheme.colors.cautionBg, tagFg = Color(0xFF8A5300),  // 노랑 위 주황 대비부족 → 진갈색. (2026-08-12 접근성)
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
                            onSend = { body ->
                                // 발송(교차프로세스 provider insert)을 IO 로 — 메인에서 직접 하면 provider 바쁠 때 화면 멈칫. (2026-08-11 성능/오프라인 감사)
                                scope.launch {
                                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.detailline.callfollowcrm.util.SmsSender.sendDirect(context, rem.item.phone, body)
                                    }
                                    if (ok) {
                                        viewModel.markReminderSent(rem.item)
                                        snackbarHostState.showSnackbar("${rem.name} 님께 보냈어요", duration = SnackbarDuration.Short)
                                    } else {
                                        onOpenChat(rem.item.phone, rem.item.customerId)
                                        snackbarHostState.showSnackbar("문자 권한이 없어요 — 채팅에서 보내주세요", duration = SnackbarDuration.Short)
                                    }
                                }
                            }
                        )
                        }
                    }
                }

                // 안 들어온 잔금(미수 1일+ 경과) — 협업 요청과 같은 InboxAlert 컴팩트 카드. 고객마다 1개. (2026-06-23 사장님)
                //   탭 = 그 고객 채팅(잔금 요청 보내러) · 꾹 누름 = 받음 처리(되돌리기 가능).
                // 2026-09-20: **[미수] 칩 안에서만.** 전에는 전체 화면에 넉 장씩 쌓여 목록을 밀어냈다.
                //   미수 칩에선 목록 대신 이 카드를 보여준다 — [잔금 요청] 버튼이 여기 있기 때문.
                // 프로토 상담함 본문 — "지금 답장 기다려요"(미확인) + "최근 대화"(나머지) 두 섹션.
                // 번호당 1줄만 (가장 최근). flatItems 는 최신순 → distinctBy 가 최신 1개 유지.
                //   (2026-06-08 #4: 고객이 연속 문자/통화 시 같은 번호가 2줄 차지하던 현상 방지.)
                val dedupItems = flatItems.distinctBy { PhoneKey.of(it.record.phoneNumber) }
                // 자동 광고 분류 — 매우 보수적(오탐 방지 3중 가드): 저장 안 된 낯선 번호 + 내가 답장 안 함 +
                //   "광고 아냐" 예외 아님 + 내용이 뻔한 광고(isLikelyAd). 하나라도 아니면 상담함에 그대로 남김. (2026-07-08 사장님)
                // 🏷️ **칩으로 한 번 거른다.** 이 아래 '지금 답장 기다려요'·'최근 대화' 는 손 안 댄다 —
                //   거른 목록을 그대로 받으니 렌더가 통째로 재사용된다. (2026-09-20)
                val dueIds = balanceDues.mapNotNull { it.customerId }.toHashSet()
                val todayStart0 = DateTimeUtils.startOfDay(System.currentTimeMillis())
                // 🔧 AS 찍힌 손님은 다른 일 칩에서 빠진다 — 숫자와 **같은 규칙**.
                val chipItems = when (inboxChip) {
                    "as" -> dedupItems.filter { it.customer?.asPendingAt != null }
                    "today" -> dedupItems.filter { it.isNewToday && it.customer?.asPendingAt == null }
                    // 🔨 **앞으로 할 시공만.** (2026-09-20 실기)
                    //   전엔 '예약일이 있고 완료 버튼을 안 누른 것' 이었다. 그래서 **이미 끝난 시공**이
                    //   (완료 버튼을 안 눌렀다는 이유로) 여기 들어와 초록 '완료' 딱지를 달고 앉아 있었다.
                    //   사장님: *"시공대기 칩은 시공 예약이 되어있는 고객군만"* → 예약일이 **오늘 이후**인 것만.
                    "wait" -> dedupItems.filter {
                        val c = it.customer
                        c != null && c.asPendingAt == null &&
                            (c.scheduledWorkDate ?: 0L) >= todayStart0 && !c.isWorkDone
                    }
                    "owe" -> dedupItems.filter { it.customer?.id in dueIds && it.customer?.asPendingAt == null }
                    // 빨간 숫자가 가리키는 것 = 답 안 한 것. 칩이 없고 **탭 숫자로만** 들어온다.
                    //   키를 "unhandled" 로 쓰면 안 된다 — 없앤 옛 칩 값이라 위 이사 코드가 되돌린다.
                    "pending" -> dedupItems.filter { it.isUnconfirmed }
                    // '끝났다' 는 앱에 이미 단일 출처가 있다 — CustomerEntity.isWorkDone
                    //   (완료 버튼 **또는** 잔금 받음. 사장님 2026-08-18 "잔금 받으면 = 완료").
                    //   칩만 다른 자를 쓰면 딱지와 목록이 서로 딴소리를 한다.
                    "done" -> dedupItems.filter {
                        it.customer?.isWorkDone == true && it.customer?.asPendingAt == null
                    }
                    else -> dedupItems
                }
                val chipOn = inboxChip != "all"

                val ads = chipItems.filter { row ->
                    val suffix = PhoneKey.of(row.record.phoneNumber)
                    row.customer == null && row.lastSent != true && suffix !in adAllowlist &&
                        com.detailline.callfollowcrm.util.isLikelyAd(row.lastBody ?: "", row.record.phoneNumber)
                }
                val adSuffixes = ads.mapTo(HashSet()) { PhoneKey.of(it.record.phoneNumber) }
                fun notAd(it: com.detailline.callfollowcrm.presentation.screen.home.HomeItem) =
                    PhoneKey.of(it.record.phoneNumber) !in adSuffixes
                val waiting = chipItems.filter { it.isUnconfirmed && notAd(it) }
                // 최근 대화 = 시간순 그대로(카톡식). 안 읽음은 순서 안 바꾸고 파란 점+굵게로만 표시.
                //   (사장님 2026-06-08 결정: "맨 위로 모으기" 빼고 시간순 유지 → 카톡과 더 동일.)
                // 고정 거래처는 최근 대화에서 빼서 '고정' 칸으로만 보여준다. 나머지는 그대로. (2026-08-24 사장님)
                val recent = chipItems.filter { !it.isUnconfirmed && notAd(it) && PhoneKey.of(it.record.phoneNumber) !in pinnedSuffixes }
                val pinned = chipItems.filter { notAd(it) && PhoneKey.of(it.record.phoneNumber) in pinnedSuffixes }

                // 지금 답장 기다려요 — waiting-head(제목+카운트+밀어서 정리) + 카드(왼쪽 밀기=정리). 비면 막내.
                // [미수] 칩은 위 잔금 카드가 본문이다. 아래 대화 목록까지 띄우면 **같은 사람이 두 번** 나온다.
                val hideThreads = inboxChip == "owe"

                if (inboxChip == "owe" && balanceDues.isNotEmpty()) {
                    // 💰 합계 먼저 — 돈은 "다 더하면 얼마인지" 가 첫 질문이다.
                    item(key = "owe-total") {
                        val sum = balanceDues.sumOf { it.outstandingWon }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                "못 받은 돈", fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, color = TossTextTertiary
                            )
                            Spacer(Modifier.weight(1f))
                            // 🍃 **금액은 사실이지 경고가 아니다.** 빨강은 한 화면에 하나만.
                            //   (2026-09-20 사장님 "텍스트 너무 강렬하고 너무 튀어")
                            Text(
                                MoneyFormatter.won(sum), fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold, color = TossTextPrimary
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "${balanceDues.size}건", fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, color = TossTextTertiary
                            )
                        }
                    }
                    // 다른 칩과 **같은 목록 모양** — 흰 카드 하나에 줄들. (2026-09-20 사장님 "언발란스")
                    item(key = "owe-list") {
                        Column(
                            Modifier.fillMaxWidth()
                                .tossCardShadow(RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                        ) {
                            // 같은 사람의 **대화 줄**을 찾아 붙인다 — 상담함은 문자함이다.
                            val bySuffix = dedupItems.associateBy {
                                PhoneKey.of(it.record.phoneNumber)
                            }
                            balanceDues.forEachIndexed { idx, due ->
                                if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                                val sfx = PhoneKey.of(due.phone)
                                val item = bySuffix[sfx]
                                OweRow(
                                    due = due,
                                    index = idx,
                                    lastBody = item?.lastBody?.takeIf { it.isNotBlank() }
                                        ?.let { (if (item.lastSent == true) "나: " else "") + it },
                                    summary = aiCardSummaries[sfx],
                                    onClick = { onOpenChat(due.phone, due.customerId) },
                                    onLongClick = {
                                        viewModel.markBalanceReceived(due.customerId, due.jobId)
                                        scope.launch {
                                            val r = snackbarHostState.showSnackbar(
                                                message = "${due.name} 잔금 받음 처리",
                                                actionLabel = "되돌리기",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (r == SnackbarResult.ActionPerformed) viewModel.undoBalanceReceived(due.customerId, due.jobId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    item(key = "owe-hint") {
                        Text(
                            "줄을 누르면 잔금 요청 문자를 쓸 수 있어요 · 꾹 누르면 '받음' 처리", fontSize = 11.5.sp,
                            color = TossTextTertiary,
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }

                // 칩을 켄 채 대기가 비면 머리글·막내를 안 띄운다 — "시공 끝남" 을 보는데
                //   "지금 답장 기다려요 0 / 다 챙기셨네요" 가 나오면 딴소리다. (2026-09-20)
                // 2026-09-20 사장님 "지금 답장 기다려요. 이거 없어져야 하는 거 아니야?"
                //   맞다. **칩의 [안 챙긴 N] 이 이미 같은 말**을 한다. 머리글이 또 세면 같은 숫자가 두 곳에.
                //   → 머리글은 아예 안 띄우고, 대기 줄은 목록 맨 위에 그대로 둔다(정보는 안 잃는다).
                //   막내 마스코트는 **[안 챙긴] 칩이 비었을 때만** — 거기선 "다 챙겼다"가 진짜 할 말이다.
                if (waiting.isEmpty() || hideThreads) {
                    // 막내 인사는 **아무것도 없는 첫 폰**에서만. (2026-09-20)
                    //   [답장 대기] 칩이 없어졌으니 "다 끝냈어요" 를 띄울 자리가 없고,
                    //   [전체] 목록 맨 위에 매일 띄우면 그게 또 어수선하다.
                    //   다 끝냈다는 신호는 **하단 탭 배지가 사라지는 것**이 대신한다.
                    if (dedupItems.isEmpty() && !chipOn) {
                        // '처음 오신 분' 인사(newUser)는 **대화가 정말 하나도 없을 때만.** (2026-09-20 실기)
                        //   거른 목록(recent)을 보면 [안 챙긴] 이 비었다는 이유로 몇 년 쓰신 분께도
                        //   "사장님, 잘 부탁드려요!" 가 떴다. 여기선 "다 챙기셨네요" 가 할 말이다.
                        item(key = "waiting-empty") {
                            Box(
                                Modifier.fillParentMaxHeight().padding(bottom = 64.dp),
                                contentAlignment = Alignment.Center
                            ) { WaitingEmptyMascot(newUser = dedupItems.isEmpty()) }
                        }
                    }
                } else {
                    items(waiting, key = { "wait-${it.record.id}-${it.record.phoneNumber}" }) { item ->
                        val suffix = PhoneKey.of(item.record.phoneNumber)
                        // 우→좌 swipe → [🚫 스팸][🧹 정리] 두 버튼. 스팸=영구 숨김, 정리=대기목록에서만. 둘 다 Snackbar Undo. (2026-06-23 사장님)
                        SpamSwipeBox(
                            onMarkSpam = {
                                viewModel.markSpam(item.record.phoneNumber, item.customer?.name?.takeIf { it.isNotBlank() })
                                scope.launch {
                                    val r = snackbarHostState.showSnackbar(
                                        message = "스팸으로 등록했어요 — 앞으로 안 보여요",
                                        actionLabel = "되돌리기",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (r == SnackbarResult.ActionPerformed) {
                                        viewModel.unmarkSpam(item.record.phoneNumber)
                                    }
                                }
                            },
                            onMarkPersonal = {
                                // 👥 「사생활」을 없애고 **지인으로 통일**. 상담함에서 빠지고
                                //   문자함 [지인] 에 모이면서, 시공막내도 안 건드린다. (2026-10-02 사장님)
                                viewModel.moveToFriend(
                                    item.record.phoneNumber,
                                    item.customer?.name?.takeIf { it.isNotBlank() }
                                )
                                scope.launch {
                                    val r = snackbarHostState.showSnackbar(
                                        message = "지인으로 옮겼어요 — 문자함 [지인] 에 있어요",
                                        actionLabel = "되돌리기",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (r == SnackbarResult.ActionPerformed) {
                                        viewModel.unmarkSpam(item.record.phoneNumber)
                                    }
                                }
                            },
                            onCleanup = {
                                viewModel.dismissUnconfirmed(item.record.phoneNumber)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "정리했어요 — 대기 목록에서만 빠져요(고객은 그대로)",
                                        actionLabel = "되돌리기",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDismissUnconfirmed(item.record.phoneNumber)
                                    }
                                }
                            },
                            content = {
                                val choices = waitingReplyChoices[suffix].orEmpty().ifEmpty {
                                    waitingReplies[suffix]?.let {
                                        listOf(com.detailline.callfollowcrm.ai.ReplyChoice(text = it))
                                    } ?: emptyList()
                                }
                                WaitingCard(
                                    item = item,
                                    aiSummary = aiCardSummaries[suffix],
                                    // // 자동 카테고리는 이제 없다(2026-09-20 제거) → 숨기던 코드 걷어냄.
                                    category = item.customer?.categoryId?.let { cid -> categoryById[cid] },
                                    replyChoices = choices,
                                    aiPrepEnabled = prefs.aiReplyPrepEnabled,
                                    onOpenChat = { onOpenChat(item.record.phoneNumber, item.customer?.id) },
                                    onCall = { dialHome(context, item.record.phoneNumber) },
                                    // 비행기 = 카드에서 지금 보고 있는 답변으로 '확인 후 발송' 다이얼로그. (2026-07-02 사장님)
                                    onQuickSend = { text -> waitingSendReply = text; waitingSendTarget = item },
                                    onLongPress = { spamTarget = item }
                                )
                            }
                        )
                    }
                }

                // 📌 고정 — 사장님이 맨 위에 고정한 거래처. 팁 없이 방들만, 밀기(스팸) 없이 꾹 눌러 해제. (2026-08-24 사장님)
                if (pinned.isNotEmpty()) {
                    item(key = "pinned-head") { SecSub("고정") }
                    item(key = "pinned-card") {
                        Column(
                            Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
                        ) {
                            pinned.forEachIndexed { j, rItem ->
                                if (j > 0) {
                                    Box(
                                        Modifier.fillMaxWidth().padding(start = 16.dp)
                                            .height(1.dp).background(TossDivider)
                                    )
                                }
                                val suffix = PhoneKey.of(rItem.record.phoneNumber)
                                val custMsgMs = rItem.lastActivityMs.takeIf { it > 0L } ?: rItem.record.endedAt
                                val unread = rItem.lastSent == false && (readStates[suffix] ?: 0L) < custMsgMs
                                Box(Modifier.fillMaxWidth().background(Color.White)) {
                                    RecentRow(
                                        item = rItem,
                                        index = j,
                                        unread = unread,
                                        aiSummary = aiCardSummaries[suffix],
                                        // // 자동 카테고리는 이제 없다(2026-09-20 제거) → 숨기던 코드 걷어냄.
                                        category = rItem.customer?.categoryId?.let { cid -> categoryById[cid] },
                                        filter = inboxChip,
                                        onOpenChat = { onOpenChat(rItem.record.phoneNumber, rItem.customer?.id) },
                                        onLongClick = { pinTarget = rItem }
                                    )
                                }
                            }
                        }
                    }
                }

                // 칩을 켰는데 보여줄 게 없다 — **왜 비었는지**를 말해준다. (2026-09-20 실기)
                //   [미수] 는 목록을 안 쓰고 잔금 카드가 본문이라, 잔금이 0이면 **글자 하나 없는 백지**였다.
                //   백지는 "앱이 죽었나?" 로 읽힌다. 그리고 [안 챙긴] 은 막내가 이미 말하니 **두 번 말하지 않는다.**
                val chipBodyEmpty =
                    if (hideThreads) balanceDues.isEmpty()
                    else waiting.isEmpty() && recent.isEmpty() && pinned.isEmpty()
                if (chipOn && chipBodyEmpty) {
                    item(key = "chip-empty") {
                        // 빈 문구를 위에 붙이면 아래가 통째로 비어 **덜 그려진 화면**처럼 보인다.
                        //   남은 자리 한가운데에 둔다(살짝 위 — 화면 정중앙보다 위가 눈에 편하다). (2026-09-20 사장님 "여백")
                        Box(
                            Modifier.fillParentMaxHeight().padding(bottom = 64.dp),
                            contentAlignment = Alignment.Center
                        ) { ChipEmpty(inboxChip) }
                    }
                }
                // 최근 대화 — 프로토 recent-row: 한 흰 카드 안 줄들 + 구분선(낱개 카드 X).
                //   머리글은 [전체] 에서만. 칩을 켰을 땐 **빈 머리글을 넣지 않는다** —
                //   빈 글자도 자리(위 16dp)를 먹어서 칩과 목록 사이에 설명 없는 틈이 생겼다. (2026-09-20 실기)
                if (recent.isNotEmpty() && !hideThreads) {
                    if (!chipOn) item(key = "recent-head") { SecSub("최근 대화") }
                    item(key = "recent-card") {
                        val shownRecent = recent.take(recentShown)
                        // 프로토 renderRecent 1:1 — 대화 3개마다 팁 하나. 단, 팁이 실제로 끼일 때만 카드를 끊고(flush),
                        //   팁이 없거나 다 떨어지면 남은 대화는 한 카드로 쭉 이어진다. (2026-07-08 사장님: 팁 없을 때
                        //   chunked(3) 가 무조건 쪼개 빈 단락/틈 = 촌스러운 구분이 생기던 것 → 프로토대로 tip 있을 때만 분할)
                        val recentBlocks = buildList<Any> {
                            var bucket = ArrayList<Pair<Int, HomeItem>>()
                            var ti = 0
                            shownRecent.forEachIndexed { idx, rItem ->
                                bucket.add(idx to rItem)
                                if ((idx + 1) % 3 == 0 && ti < shownTips.size) {
                                    add(bucket.toList()); bucket = ArrayList()
                                    add(shownTips[ti]); ti++
                                }
                            }
                            if (bucket.isNotEmpty()) add(bucket.toList())
                            if (ti < shownTips.size) add(shownTips[ti])   // 끝까지 내렸을 때 하나 더 (프로토 동일)
                        }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                            recentBlocks.forEach { block ->
                                when (block) {
                                    is com.detailline.callfollowcrm.presentation.component.MakneTip -> {
                                        com.detailline.callfollowcrm.presentation.component.MakneTipCard(
                                            tip = block,
                                            onGo = { guideTip = block },
                                            onDismiss = { onTipDismiss(block) }
                                        )
                                    }
                                    is List<*> -> {
                                        Column(
                                            Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
                                        ) {
                                            block.forEachIndexed { j, any ->
                                                @Suppress("UNCHECKED_CAST")
                                                val entry = any as Pair<Int, HomeItem>
                                                val index = entry.first
                                                val rItem = entry.second
                                                if (j > 0) {
                                                    Box(
                                                        Modifier.fillMaxWidth().padding(start = 16.dp)
                                                            .height(1.dp).background(TossDivider)
                                                    )
                                                }
                                                val suffix = PhoneKey.of(rItem.record.phoneNumber)
                                                // 카톡식 안 읽음: 고객이 마지막에 말함(lastSent==false) + 그 메시지가 마지막으로 읽은 시각보다 새것.
                                                val custMsgMs = rItem.lastActivityMs.takeIf { it > 0L } ?: rItem.record.endedAt
                                                val unread = rItem.lastSent == false && (readStates[suffix] ?: 0L) < custMsgMs
                                                // 최근 대화도 밀어서 [🚫 스팸][👤 사생활] — 스팸/개인일 수 있으니. (2026-06-23 사장님)
                                                val rName = rItem.customer?.name?.takeIf { it.isNotBlank() }
                                                com.detailline.callfollowcrm.presentation.component.SwipeRevealTwoBox(
                                                    onFirst = {
                                                        viewModel.markSpam(rItem.record.phoneNumber, rName, "spam")
                                                        scope.launch {
                                                            val r = snackbarHostState.showSnackbar("스팸으로 등록했어요 — 앞으로 안 보여요", actionLabel = "되돌리기", duration = SnackbarDuration.Short)
                                                            if (r == SnackbarResult.ActionPerformed) viewModel.unmarkSpam(rItem.record.phoneNumber)
                                                        }
                                                    },
                                                    onSecond = {
                                                        // 👥 지인으로 통일 (2026-10-02 사장님)
                                                        viewModel.moveToFriend(rItem.record.phoneNumber, rName)
                                                        scope.launch {
                                                            val r = snackbarHostState.showSnackbar("지인으로 옮겼어요 — 문자함 [지인] 에 있어요", actionLabel = "되돌리기", duration = SnackbarDuration.Short)
                                                            if (r == SnackbarResult.ActionPerformed) viewModel.unmarkSpam(rItem.record.phoneNumber)
                                                        }
                                                    },
                                                    firstLabel = "스팸",
                                                    secondLabel = "지인",
                                                    firstColor = TossError,
                                                    secondColor = AppTheme.colors.category,
                                                    firstIcon = Icons.Filled.Block,
                                                    secondIcon = Icons.Filled.Person,
                                                    shape = androidx.compose.ui.graphics.RectangleShape
                                                ) {
                                                    Box(Modifier.fillMaxWidth().background(Color.White)) {
                                                        RecentRow(
                                                            item = rItem,
                                                            index = index,
                                                            unread = unread,
                                                            aiSummary = aiCardSummaries[suffix],
                                                            // 그룹 태그 — 사장님이 만든 분류(일당 등)만. 자동 시스템 카테고리(시공 대기/완료)는
                                                            //   상태 태그와 중복이라 태그로 안 띄움(그럼 모든 행에 붙어 '일당' 이 안 도드라짐). (2026-08-04)
                                                            // 자동 카테고리는 이제 없다(2026-09-20 제거) → 숨기던 코드 걷어냄.
                                                            category = rItem.customer?.categoryId?.let { cid -> categoryById[cid] },
                                                            filter = inboxChip,
                                                            onOpenChat = { onOpenChat(rItem.record.phoneNumber, rItem.customer?.id) },
                                                            onLongClick = { pinTarget = rItem }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (recent.size > shownRecent.size) {
                                Box(
                                    Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
                                        .clickable { recentShown += RECENT_MORE }.padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        // 30개씩 — 남은 게 그보다 적으면 그 수만큼.
                                        "이전 대화 ${minOf(RECENT_MORE, recent.size - shownRecent.size)}개 더 보기" +
                                            (recent.size - shownRecent.size - RECENT_MORE).takeIf { it > 0 }
                                                ?.let { " · 남은 ${it + RECENT_MORE}개" } .orEmpty(),
                                        fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TossBlue,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // 📁 광고함 제거 (2026-07-11 사장님) — 걸러낸 광고는 이제 '문자함' 탭에 '광고' 딱지로 표시.
                //   상담함에서 광고를 빼는 렌더 필터(ads/adSuffixes)는 유지 → 상담함은 계속 깨끗.

                item(key = "fab-spacer") { Spacer(Modifier.height(16.dp)) } // 리스트 끝 여백(FAB 없음 → 80→16, 하단 흰 공백 제거. 2026-07-01 사장님)
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

            // 오늘 시공 [완료] 팝업 — 프로토 openComplete. 완료처리/요청 모두 그 현장을 완료 처리(히어로에서 빠짐). 2026-06-08 #2
            completeTarget?.let { c ->
                CompletionDialog(
                    customer = c,
                    onDismiss = { completeTarget = null },
                    onComplete = { name ->
                        val cid = c.id
                        completeTarget = null
                        viewModel.markJobCompleted(cid)   // 완료 반영 → todayJobs 에서 제외 → 히어로 갱신
                        scope.launch {
                            val res = snackbarHostState.showSnackbar(
                                "$name 시공을 완료 처리했어요",
                                actionLabel = "되돌리기", duration = SnackbarDuration.Short
                            )
                            if (res == SnackbarResult.ActionPerformed) viewModel.undoJobCompleted(cid)
                        }
                    },
                    onCompletePaid = { name ->
                        val cid = c.id
                        completeTarget = null
                        viewModel.markJobCompletedBalancePaid(cid)   // 완료 + 잔금 완납 처리(고객상세 '잔금 받음', 미수금에서 빠짐)
                        scope.launch {
                            val res = snackbarHostState.showSnackbar(
                                "$name 완료 · 잔금까지 다 받음 처리했어요",
                                actionLabel = "되돌리기", duration = SnackbarDuration.Short
                            )
                            if (res == SnackbarResult.ActionPerformed) viewModel.undoJobCompleted(cid)
                        }
                    },
                    onSend = { phone, name, body, kind ->
                        val cid = c.id
                        completeTarget = null
                        viewModel.markJobCompleted(cid)   // 잔금/후기 발송도 = 시공 완료 → 히어로에서 빠짐
                        scope.launch {
                            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.detailline.callfollowcrm.util.SmsSender.sendDirect(context, phone, body)   // 발송 IO — 메인 멈칫 방지
                            }
                            if (ok) snackbarHostState.showSnackbar("$name 님께 $kind 발송 · 완료 처리", duration = SnackbarDuration.Short)
                            else snackbarHostState.showSnackbar("문자 권한이 없어요 — 채팅에서 보내주세요", duration = SnackbarDuration.Short)
                        }
                    }
                )
            }

            // 협업 완료 [입금했어요] → 일당 지급 금액 입력 → 정산 자동 기록. (일당 마켓 Phase 1)
            payTarget?.let { up ->
                // 보낼 금액(dailyWage, 만원)을 미리 채워둠 → 다시 입력할 필요 없이 [기록]만. (2026-07-01 사장님)
                var manwon by remember(up.eventId) { mutableStateOf(up.dailyWage?.toString() ?: "") }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { payTarget = null },
                    title = { Text("일당 지급 기록", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                    text = {
                        Column {
                            com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                            Text(
                                "${up.partnerName}님께 보낸 일당을 정산에 기록해요." +
                                    (up.accountText?.let { "\n계좌: $it" } ?: ""),
                                fontSize = 13.sp, color = TossTextSecondary, lineHeight = 19.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = manwon,
                                onValueChange = { v -> manwon = v.filter { it.isDigit() } },
                                placeholder = { Text("금액 (만원)", color = TossTextTertiary) },
                                singleLine = true,
                                visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            viewModel.recordLaborPayment(up.partnerName, manwon.toLongOrNull() ?: 0L, up.eventId)
                            payTarget = null
                            scope.launch {
                                snackbarHostState.showSnackbar("정산에 일당 지급을 기록했어요", duration = SnackbarDuration.Short)
                            }
                        }) { Text("기록", color = TossBlue, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { payTarget = null }) {
                            Text("취소", color = TossTextSecondary)
                        }
                    },
                    containerColor = Color.White,
                    tonalElevation = 0.dp,   // 흰 창에 회색이 덧칠되는 것 끄기 (2026-09-21 사장님)
                )
            }

            // 대기 카드 꾹 누름 → 스팸 등록 / 대기목록 정리 선택. (2026-06-23 사장님: "정리도 있지만 스팸 등록도 있어야")
            spamTarget?.let { target ->
                val phone = target.record.phoneNumber
                val nm = target.customer?.name?.takeIf { n -> n.isNotBlank() } ?: PhoneNumberFormatter.format(phone)
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { spamTarget = null },
                    title = { Text(nm, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                    text = {
                        Column {
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.unpaidBg)
                                    .clickable {
                                        spamTarget = null
                                        viewModel.markSpam(phone, target.customer?.name?.takeIf { it.isNotBlank() })
                                        scope.launch {
                                            val r = snackbarHostState.showSnackbar("스팸으로 등록했어요 — 앞으로 안 보여요", actionLabel = "되돌리기", duration = SnackbarDuration.Short)
                                            if (r == SnackbarResult.ActionPerformed) viewModel.unmarkSpam(phone)
                                        }
                                    }
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text("스팸으로 등록", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossError)
                                    Text("앞으로 상담함·신규에서 안 보여요", fontSize = 12.sp, color = TossTextTertiary)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            // 👥 **지인으로** — 아는 사람이 손님 사이에 섞여 있을 때. (2026-10-02 사장님)
                            //   스팸도 아니고 그냥 정리도 아니다. 문자함 [지인] 칸으로 간다.
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                                    .clickable {
                                        spamTarget = null
                                        viewModel.moveToFriend(phone)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("지인으로 옮겼어요 — 문자함 [지인] 에 있어요")
                                        }
                                    }
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text("지인으로", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                                    Text("손님이 아니라 아는 사람 — 문자함 [지인] 으로 모아요",
                                        fontSize = 12.sp, color = TossTextTertiary)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                                    .clickable {
                                        spamTarget = null
                                        viewModel.dismissUnconfirmed(phone)
                                        scope.launch {
                                            val r = snackbarHostState.showSnackbar("정리했어요 — 대기 목록에서만 빠져요(고객은 그대로)", actionLabel = "되돌리기", duration = SnackbarDuration.Short)
                                            if (r == SnackbarResult.ActionPerformed) viewModel.undoDismissUnconfirmed(phone)
                                        }
                                    }
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text("대기목록에서 정리", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                                    Text("이 목록에서만 빼요 (고객·대화는 그대로)", fontSize = 12.sp, color = TossTextTertiary)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { spamTarget = null }) {
                            Text("취소", color = TossTextSecondary)
                        }
                    },
                    containerColor = Color.White,
                    tonalElevation = 0.dp,   // 흰 창에 회색이 덧칠되는 것 끄기 (2026-09-21 사장님)
                )
            }

            // 방 꾹 누름 → 맨 위에 고정 / 해제. (2026-08-24 사장님)
            pinTarget?.let { target ->
                val phone = target.record.phoneNumber
                val nm = target.customer?.name?.takeIf { n -> n.isNotBlank() } ?: PhoneNumberFormatter.format(phone)
                val isPinnedNow = PhoneKey.of(phone) in pinnedSuffixes
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { pinTarget = null },
                    title = { Text(nm, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                    text = {
                        Column {
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                                    .clickable {
                                        pinTarget = null
                                        val nowPinned = viewModel.togglePin(phone)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (nowPinned) "맨 위에 고정했어요" else "고정을 해제했어요",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text(
                                        if (isPinnedNow) "고정 해제" else "맨 위에 고정",
                                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary
                                    )
                                    Text(
                                        if (isPinnedNow) "고정 칸에서 빼요 (대화는 그대로)" else "상담함 맨 위 '고정' 칸에 둬요",
                                        fontSize = 12.sp, color = TossTextTertiary
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { pinTarget = null }) {
                            Text("취소", color = TossTextSecondary)
                        }
                    },
                    containerColor = Color.White,
                    tonalElevation = 0.dp,   // 흰 창에 회색이 덧칠되는 것 끄기 (2026-09-21 사장님)
                )
            }

            // '확인 후 발송' — 받은 문자 원문 + (카드에서 고른) 답변 전문 확인 후 발송/고쳐서/취소. (2026-07-02 사장님)
            waitingSendTarget?.let { target ->
                val phone = target.record.phoneNumber
                val suffix = PhoneKey.of(phone)
                val nm = target.customer?.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(phone)
                val incoming = target.lastBody?.takeIf { it.isNotBlank() }
                val reply = waitingSendReply
                    ?: waitingReplyChoices[suffix]?.firstOrNull()?.text
                    ?: waitingReplies[suffix]
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { waitingSendTarget = null },
                    title = { Text("$nm 님께 보낼까요?", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                    text = {
                        Column {
                            if (!incoming.isNullOrBlank()) {
                                Text("받은 문자", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary)
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossGrayBg).padding(11.dp)) {
                                    Text(incoming, fontSize = 13.5.sp, color = TossTextPrimary)
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                            Text("보낼 답변", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AppTheme.colors.primaryBg).padding(11.dp)) {
                                Text(
                                    reply ?: "추천 답변이 아직 없어요 — 고쳐서 보내기로 채팅에서 직접 보내주세요",
                                    fontSize = 13.5.sp, color = TossTextPrimary,
                                    maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    confirmButton = {
                        if (!reply.isNullOrBlank()) {
                            androidx.compose.material3.TextButton(onClick = {
                                waitingSendTarget = null
                                scope.launch {
                                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.detailline.callfollowcrm.util.SmsSender.sendDirect(context, phone, reply)   // 발송 IO — 메인 멈칫 방지
                                    }
                                    if (ok) {
                                        viewModel.onWaitingReplySent(phone, reply, target.customer?.id)
                                        snackbarHostState.showSnackbar("$nm 님께 보냈어요", duration = SnackbarDuration.Short)
                                    } else {
                                        onOpenChat(phone, target.customer?.id)
                                        snackbarHostState.showSnackbar("문자 권한이 없어요 — 채팅에서 보내주세요", duration = SnackbarDuration.Short)
                                    }
                                }
                            }) { Text("이대로 보내기", color = TossBlue, fontWeight = FontWeight.Bold) }
                        }
                    },
                    dismissButton = {
                        Row {
                            androidx.compose.material3.TextButton(onClick = { waitingSendTarget = null }) {
                                Text("취소", color = TossTextSecondary)
                            }
                            androidx.compose.material3.TextButton(onClick = {
                                waitingSendTarget = null
                                if (!reply.isNullOrBlank()) viewModel.prefillChatDraft(phone, reply)
                                onOpenChat(phone, target.customer?.id)
                            }) { Text("고쳐서 보내기", color = TossTextPrimary, fontWeight = FontWeight.Bold) }
                        }
                    },
                    containerColor = Color.White,
                    tonalElevation = 0.dp,   // 흰 창에 회색이 덧칠되는 것 끄기 (2026-09-21 사장님)
                )
            }
            } // end Box(nestedScroll)
            } else {
                // 문자함(고객 아님) — 삼성 기본 메시지식 단순 목록.
                // 📦 택배 / 광고·인증 가르기. (2026-09-20 사장님)
                //   발신번호 8자리는 "손님 아님" 까지만 말해준다 — 택배사도 8자리라 글자를 봐야 갈린다.
                //   여긴 이미 '손님 아님' 으로 걸러진 구역이라 글자를 봐도 안전하다(포워딩 걱정 없음).
                // 👥 **지인이 맨 먼저다.** 사장님이 **직접 찍은** 것이라 글자로 때려맞힌 것보다 세다.
                //   (2026-10-02 사장님 "광고 택배 옆에 지인 목록을 하나 만들어서")
                val friends = generalThreads.filter { it.isFriend }
                // 🚫 스팸(02·070 등 지역번호·인터넷전화) — 전엔 통째로 숨겼는데, OTP도 숨어서 여기 모아 보인다. (2026-10-06 사장님)
                val spam = generalThreads.filter { it.isSpam }
                val parcels = generalThreads.filter {
                    !it.isFriend && !it.isSpam &&
                        com.detailline.callfollowcrm.domain.inbox.ParcelHeuristics.isParcel(it.phone, it.lastBody)
                }
                val ads = generalThreads.filter {
                    !it.isFriend && !it.isSpam &&
                        !com.detailline.callfollowcrm.domain.inbox.ParcelHeuristics.isParcel(it.phone, it.lastBody)
                }
                val boxThreads = when (boxSub) {
                    "parcel" -> parcels
                    "friend" -> friends
                    "spam" -> spam
                    else -> ads
                }
                // 📨 가르기는 **문자함 안에서**. 둘이 합치면 문자함 전부라 빠지는 게 없다.
                Row(
                    Modifier.fillMaxWidth().background(Color.White)
                        .padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BoxSubChip("광고", ads.size, boxSub == "ad") { boxSub = "ad" }
                    BoxSubChip("택배", parcels.size, boxSub == "parcel") { boxSub = "parcel" }
                    // 👥 「사생활」은 숨기는 느낌이라 안 쓴다 — **지인**. (2026-10-02 사장님과 정함)
                    BoxSubChip("지인", friends.size, boxSub == "friend") { boxSub = "friend" }
                    // 🚫 스팸 — 전엔 완전히 숨겼던 지역번호·인터넷전화 문자. OTP 찾는 자리. (2026-10-06 사장님)
                    BoxSubChip("스팸", spam.size, boxSub == "spam") { boxSub = "spam" }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                if (boxSub == "spam") {
                    // 인증번호(OTP)도 여기 있을 수 있다고 알려준다. (2026-10-06 사장님)
                    Text(
                        "지역번호·인터넷전화로 자동 분류돼요. 은행·관공서·인증번호(OTP)도 여기 있을 수 있어요",
                        style = AppType.caption, color = AppTheme.colors.cautionText,
                        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.cautionBg)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                MessageBoxSection(
                    threads = boxThreads,
                    // 빈 화면은 **누른 칩 얘기**여야 한다. (2026-09-20 실기)
                    emptySpeech = when (boxSub) {
                        "parcel" -> "온 택배 문자가 없어요"
                        "friend" -> "지인으로 옮긴 사람이 없어요"
                        "spam" -> "스팸으로 분류된 문자가 없어요"
                        else -> "광고·인증 문자가 없어요"
                    },
                    emptySub = when (boxSub) {
                        "parcel" -> "운송장·배송 문자는 여기로 모여요"
                        // 👥 **어떻게 넣는지**를 빈 화면이 알려준다 — 안 그러면 쓸 줄을 모른다.
                        "friend" -> "상담함에서 카드를 밀어 [지인] 을 눌러요"
                        "spam" -> "지역번호·인터넷전화(02·070 등) 문자가 여기로 모여요"
                        else -> "인증번호·광고 문자는 여기로 모여요"
                    },
                    pinnedSuffixes = pinnedSuffixes,
                    onOpen = { phone -> onOpenChat(phone, null) },
                    onMoveToConsult = { phone ->
                        viewModel.moveToConsult(phone)
                        scope.launch { snackbarHostState.showSnackbar("상담함으로 옮겼어요") }
                    },
                    onMoveToSpam = { phone, name ->
                        viewModel.markSpam(phone, name)
                        scope.launch { snackbarHostState.showSnackbar("스팸으로 옮겼어요") }
                    },
                    onTogglePin = { phone ->
                        val nowPinned = viewModel.togglePin(phone)
                        scope.launch { snackbarHostState.showSnackbar(if (nowPinned) "맨 위에 고정했어요" else "고정을 해제했어요") }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

        // '새로워졌어요' 시트 제거됨 (2026-07-29 사장님 — Play 배포로 이관).

        // 🕐 예약함 — 상담함 위 카드 탭 시 전체화면 오버레이로. (2026-10-05 사장님)
        if (showScheduledSheet) {
            com.detailline.callfollowcrm.presentation.component.ScheduledSmsSheet(
                items = scheduledPending,
                onDismiss = { showScheduledSheet = false },
                onCancel = { id -> viewModel.cancelScheduled(id) },
                onReschedule = { id, ms -> viewModel.rescheduleScheduled(id, ms) }
            )
        }
    }
}

/**
 * KPI 4장 (2×2 그리드). LazyColumn 첫 item 으로 들어가서 스크롤 시 사라짐.
 * horizontal padding 은 LazyColumn 의 contentPadding 으로 들어가므로 안에서 X.
 */
/**
 * 오늘 시공이 2곳 이상일 때 — 카드를 꾹 눌러(롱프레스) 끌어서 순서 바꾸는 트렐로식 리스트.
 *   먼저 가야 할 현장을 위로 올려두면 헷갈리지 않음 (2026-06-11 사장님 요청).
 *   짧게 탭 = 그 현장 상세 열기(TodayHeroJobCard 의 clickable 유지). 길게 눌러야 드래그 시작.
 *   손 떼면 새 순서를 onReorder 로 저장. 카드 높이가 달라도 측정값으로 임계 swap.
 */
@Composable
private fun TodayHeroReorderableList(
    jobs: List<com.detailline.callfollowcrm.data.local.entity.CustomerEntity>,
    onReorder: (List<Long>) -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onNavigate: (String) -> Unit,
    onCall: (String) -> Unit,
    onComplete: (com.detailline.callfollowcrm.data.local.entity.CustomerEntity) -> Unit
) {
    // 로컬 순서 — 드래그 중 즉시 반영, 손 떼면 commit. key=현재 id 순서라 바깥 순서가 바뀌면 재동기화.
    val items = remember(jobs.map { it.id }) { mutableStateListOf(*jobs.toTypedArray()) }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Int, Int>() }
    val haptic = LocalHapticFeedback.current
    val spacing = 10.dp

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.forEachIndexed { index, c ->
            val isDragging = index == draggingIndex
            Box(
                Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else 0f
                        if (isDragging) {
                            scaleX = 1.02f; scaleY = 1.02f
                            shadowElevation = 24f
                            alpha = 0.97f
                        }
                    }
                    .onGloballyPositioned { heights[index] = it.size.height }
                    .pointerInput(c.id, items.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                draggingIndex = -1
                                dragOffset = 0f
                                onReorder(items.map { it.id })
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffset = 0f
                                onReorder(items.map { it.id })
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                dragOffset += drag.y
                                val cur = draggingIndex
                                if (cur < 0) return@detectDragGesturesAfterLongPress
                                val gap = spacing.toPx()
                                // 아래로 끌어 다음 카드 절반 넘으면 swap, 위로도 동일.
                                if (dragOffset > 0 && cur < items.lastIndex) {
                                    val nextH = (heights[cur + 1] ?: 0) + gap
                                    if (nextH > 0 && dragOffset > nextH / 2f) {
                                        items.add(cur + 1, items.removeAt(cur))
                                        draggingIndex = cur + 1
                                        dragOffset -= nextH
                                    }
                                } else if (dragOffset < 0 && cur > 0) {
                                    val prevH = (heights[cur - 1] ?: 0) + gap
                                    if (prevH > 0 && -dragOffset > prevH / 2f) {
                                        items.add(cur - 1, items.removeAt(cur))
                                        draggingIndex = cur - 1
                                        dragOffset += prevH
                                    }
                                }
                            }
                        )
                    }
            ) {
                TodayHeroJobCard(c, onOpenCustomer, onNavigate, onCall, onComplete)
            }
        }
    }
}

/** 오늘 시공 다크 히어로 한 장 (프로토 heroJobHtml). 2곳 이상이면 TodayHeroCard 가 현장 수만큼 반복. */
@Composable
private fun TodayHeroJobCard(
    c: com.detailline.callfollowcrm.data.local.entity.CustomerEntity,
    onOpenCustomer: (Long) -> Unit,
    onNavigate: (String) -> Unit,
    onCall: (String) -> Unit,
    onComplete: (com.detailline.callfollowcrm.data.local.entity.CustomerEntity) -> Unit
) {
    val name = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber)
    // 완료한 현장은 '완료한 그날'만 회색 작은 카드로 남김 — 다시 전화 가능, 다음날 사라짐. (2026-06-14 사장님)
    if (c.workCompletedAt != null) {
        CompletedHeroJobCard(c, onOpenCustomer, onCall)
        return
    }
    // 히어로 카드 "빛나는" 애니메이션 — 광택 한 줄기가 대각선으로 슥 지나가고, D-DAY 점이 은은히 숨 쉼.
    val shine = rememberInfiniteTransition(label = "heroShine")
    val shineX by shine.animateFloat(
        initialValue = -0.6f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "shineX"
    )
    val dotPulse by shine.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotPulse"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .tossCardShadow(RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF272D3D), Color(0xFF14171F))))
            .drawWithContent {
                drawContent()
                // 대각선 광택 — 카드 폭을 가로지르는 흰 띠. 양 끝에선 카드 밖이라 잠깐 쉼.
                val cx = shineX * size.width
                val band = size.width * 0.22f
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                        start = Offset(cx - band, 0f),
                        end = Offset(cx + band, size.height)
                    )
                )
            }
            .clickable { onOpenCustomer(c.id) }
            .padding(20.dp)
    ) {
        // hero-top — 초록 점(은은히 깜빡) + 라벨
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(TossSuccess.copy(alpha = dotPulse)))
            Spacer(Modifier.width(7.dp))
            Text(
                "오늘 시공 · D-DAY",
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
                c.address?.takeIf { it.isNotBlank() } ?: "주소 미등록 — 눌러서 등록",
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
}


/**
 * 협업 현장 오늘 히어로 카드 — 보라색 점·뱃지로 내 시공과 구분. 탭 → 협업 현장 상세. (2026-06-24 사장님)
 */
@Composable
private fun CollabHeroJobCard(
    s: com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite,
    onOpenSite: (String) -> Unit,
    onCall: (String) -> Unit,
    onNavigateAddr: (String?) -> Unit,
    onComplete: (com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite) -> Unit = {}
) {
    val purple = AppTheme.colors.category
    val addr = s.addr?.takeIf { it.isNotBlank() }
    // 히어로 카드 "빛나는" 애니메이션 — 일반 시공 카드와 동일하게 광택 한 줄기가 대각선으로 슥 지나가고
    //   점이 은은히 숨 쉼. (2026-06-26 사장님: 협업 카드만 이 애니메이션이 빠져있었음)
    val shine = rememberInfiniteTransition(label = "collabShine")
    val shineX by shine.animateFloat(
        initialValue = -0.6f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "shineX"
    )
    val dotPulse by shine.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotPulse"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .tossCardShadow(RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1C1730), Color(0xFF14171F))))
            .drawWithContent {
                drawContent()
                // 대각선 광택 — 카드 폭을 가로지르는 흰 띠. 양 끝에선 카드 밖이라 잠깐 쉼.
                val cx = shineX * size.width
                val band = size.width * 0.22f
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                        start = Offset(cx - band, 0f),
                        end = Offset(cx + band, size.height)
                    )
                )
            }
            .clickable { onOpenSite(s.shareId) }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(purple.copy(alpha = dotPulse)))
            Spacer(Modifier.width(7.dp))
            Text(
                "협업 현장 · 오늘",
                color = purple.copy(alpha = 0.9f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp
            )
        }
        val displayTitle = s.title.takeIf { it.isNotBlank() } ?: addr ?: "협업 현장"
        Text(
            displayTitle,
            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp, modifier = Modifier.padding(top = 11.dp),
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        val wageText = s.dailyWage?.let { " · 일당 ${it}만원" } ?: ""
        Text(
            "${bossLabel(s.ownerName)}$wageText",
            color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (addr != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 7.dp)) {
                Icon(Icons.Default.Place, null, tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    addr, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        val isCompleted = s.progress == com.detailline.callfollowcrm.ai.SharedSiteRepository.Progress.COMPLETED
        Row(modifier = Modifier.padding(top = 17.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (addr != null) {
                HeroBtn("길찾기", Icons.Default.Navigation, light = true, modifier = Modifier.weight(1f)) { onNavigateAddr(addr) }
            }
            HeroBtn("전화", Icons.Default.Call, light = false, modifier = Modifier.weight(1f)) { onCall(s.ownerPhone) }
            if (isCompleted) {
                HeroBtn("완료됨", Icons.Default.CheckCircle, light = false, modifier = Modifier.weight(1f)) {}
            } else {
                HeroBtn("완료", Icons.Default.CheckCircle, light = false, modifier = Modifier.weight(1f)) { onComplete(s) }
            }
        }
    }
}

/**
 * 🔨 **오늘 시공 띠** — 홈 맨 위 한 줄. (2026-09-20 사장님 · 프로토 artifact/CitTfr44fUSaY6W8avpQrF)
 *
 * 왜 띠인가: 오늘 어디 가는지는 **안 눌러도 알아야 하는 것**이라 칩(눌러야 보임)으로 만들면 안 된다.
 * 그런데 카드로 두면 160dp 를 먹어 목록을 화면 밖으로 밀어낸다. → **52dp 한 줄**.
 *
 * 규칙 (프로토 03):
 *  - 띠에 뜨는 건 **오늘 것 중 아직 완료 안 된 첫 번째**. 완료하면 다음 현장으로 바뀐다.
 *  - **시각이 지났다고 끝난 걸로 안 친다.** "(지났어요)" 라고 사실만 적고 버튼만 [완료] 로.
 *  - 색은 **일이 있을 때만**. 없는 날을 초록으로 칠하면 거짓말이 된다.
 *  - 두 곳 이상이면 이름 뒤에 **(1/2)** — 곳수는 절대 안 잘리게 **첫 줄**에 둔다.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TodayBand(
    todayJobs: List<com.detailline.callfollowcrm.data.local.entity.CustomerEntity>,
    nextJobs: List<com.detailline.callfollowcrm.data.local.entity.CustomerEntity>,
    collabTodayCount: Int,
    /**
     * 오늘 잡힌 협업 현장 **전부**. 내 시공이 없어도 **오늘 갈 데가 있으면** 띠가 말해야 한다.
     *   🕐 전엔 **한 곳만** 받아서, 협업이 둘이면 둘째를 그릴 수가 없었다. (2026-10-01)
     */
    collabTodaySites: List<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite> = emptyList(),
    /** 내일 이후 가장 빠른 협업 현장. '다음' 후보다 — 내 시공보다 빠를 수 있다. */
    collabNext: com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite? = null,
    onOpenChat: (phone: String, customerId: Long?) -> Unit,
    onNavigateAddr: (String?) -> Unit,
    onComplete: (com.detailline.callfollowcrm.data.local.entity.CustomerEntity) -> Unit,
    onAddSchedule: () -> Unit,
    /** 1쪽(오늘)을 누르면 갈 곳 — 일정 화면. 손님 대화가 아니다. */
    onOpenSchedule: () -> Unit
) {
    val now = System.currentTimeMillis()
    val dayStart = DateTimeUtils.startOfDay(now)
    // 🕐 **내 것·남의 것을 안 가리고 시간순.** (2026-10-01 사장님)
    //   "내 현장(시간 미정)이 첫 번째에 있고 9시 협업 현장이 두 번째에 있더라고.
    //    시간순으로 떠야 하지 않을까? 시간 미정이면 맨 마지막에 와야 하고 말야"
    //   전엔 **「내 시공 먼저, 그 뒤에 협업」**이었다 — 일정 탭은 2026-09-27 에 고쳤는데
    //   **홈은 안 고쳤다.** 같은 규칙을 두 곳에 적어놓았으니 한 곳만 고쳐진 것이다.
    //   🔒 세는 자는 [DayOrder] 한 곳이다. 아침에 보는 건 **먼저 가는 곳**이지 누구 것이 아니다.
    val ordered = remember(todayJobs, collabTodaySites) {
        val mine = todayJobs.map {
            com.detailline.callfollowcrm.domain.schedule.DayOrder
                .jobMinutes(it.scheduledWorkMinutes) to (it to null)
        }
        val theirs = collabTodaySites.map {
            com.detailline.callfollowcrm.domain.schedule.DayOrder
                .collabMinutes(it.timeLabel, it.scheduledAtMs) to
                (null to it)
        }
        (mine + theirs).sortedBy { it.first }.map { it.second }
    }
    val total = ordered.size
    val target = ordered.firstOrNull()
    val next = nextJobs.firstOrNull { (it.scheduledWorkDate ?: 0L) >= dayStart }
    // ⭐ 협업도 '내 다음 일정'이다. 전엔 내 시공만 봐서 **내일 협업 가는 날에도** "다음 · 9/28" 이라 했다.
    //   (2026-09-21 사장님 신고) 둘 중 **빠른 쪽**이 다음이다.
    val nextMineMs = next?.scheduledWorkDate ?: Long.MAX_VALUE
    val nextCollabMs = collabNext?.scheduledAtMs?.takeIf { it > 0L } ?: Long.MAX_VALUE
    val nextIsCollab = nextCollabMs < nextMineMs
    val hasNext = next != null || collabNext != null
    val doneToday = todayJobs.isEmpty() && nextJobs.any {
        (it.scheduledWorkDate ?: 0L) == dayStart && it.workCompletedAt != null
    }

    // 📄 **오늘 것을 한 쪽씩, 다 보고 나서 다음 시공.** (2026-09-28 사장님)
    //   전엔 1쪽=오늘 **첫 곳**, 2쪽=다음 시공이었다. 그런데 글자엔 「(1/2)」라 적혀 있어
    //   넘기면 오늘 2번째가 나올 줄 아셨다 — 실제로는 **내일**이 나왔다.
    //   한 몸짓에 두 가지 뜻이 섞여 있던 셈이다. 이제 (1/2) → (2/2) → 다음 시공.
    //   오늘 아무것도 없으면 그 자리는 안내 한 쪽(빈 날·끝난 날)으로 쓴다.
    //   셈과 「(1/2)」 글자는 [TodayBandPages] 한 곳에 있다 — 전엔 글자가 두 군데였다.
    val todaySlots = com.detailline.callfollowcrm.domain.schedule.TodayBandPages.todaySlots(total)
    val pageCount = com.detailline.callfollowcrm.domain.schedule.TodayBandPages
        .pageCount(total, hasNext)
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pageCount })

    Column(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.pager.HorizontalPager(state = pager) { page ->
            if (page < todaySlots) {
                // 이 쪽이 가리키는 오늘 현장. 내 시공을 먼저, 그 뒤에 협업 현장.
                val row = ordered.getOrNull(page)
                val slot = row?.first
                val collabHere = row?.second
                if (slot != null) {
                    val mins = slot.scheduledWorkMinutes
                    val timeText = mins?.let { DateTimeUtils.formatWorkMinutes(it) } ?: "시간 미정"
                    val passed = mins != null && now > dayStart + mins * 60_000L
                    val who = slot.name?.takeIf { it.isNotBlank() }
                        ?: com.detailline.callfollowcrm.util.PhoneNumberFormatter.format(slot.phoneNumber)
                    val addr = slot.address?.trim()?.takeIf { it.isNotBlank() }
                    BandShell(
                        bg = Color(0xFF0B7C5E), fg = Color.White, subFg = Color(0xFFA8E6CE),
                        // 🔨 → 앱이 그리는 아이콘. 이 띠의 다른 네 경우는 이미 iconVector 를 쓴다. (2026-09-22)
                        icon = "", iconVector = Icons.Default.HomeWork,
                        line1 = buildString {
                            append(timeText)
                            if (passed) append(" (지났어요)")
                            append(" · "); append(who)
                            // 몇 번째인지 — 넘긴 쪽과 **같은 숫자**여야 한다. (2026-09-28 사장님)
                            append(
                                com.detailline.callfollowcrm.domain.schedule.TodayBandPages
                                    .label(page, total)
                            )
                        },
                        line2 = addr ?: "주소 아직 없어요",
                        action = if (passed) "완료" else if (addr != null) "길찾기" else null,
                        onAction = { if (passed) onComplete(slot) else onNavigateAddr(addr) },
                        onTap = { onOpenChat(slot.phoneNumber, slot.id) }
                    )
                } else if (collabHere != null) {
                    // 협업은 **보라 결** — 일정 탭에서 쓰는 그 색이라 종류가 바로 갈린다.
                    val collabToday = collabHere
                    val cAddr = collabToday.addr?.trim()?.takeIf { it.isNotBlank() }
                    BandShell(
                        bg = AppTheme.colors.categoryBg, fg = AppTheme.colors.category,
                        subFg = AppTheme.colors.textSub,
                        icon = "", iconVector = Icons.Default.Handshake,
                        border = AppTheme.colors.categoryBg,
                        iconBg = Color.White, iconTint = AppTheme.colors.category,
                        line1 = "오늘 협업" +
                            (collabToday.timeLabel?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                            (collabToday.ownerName.takeIf { it.isNotBlank() }?.let { " · ${it}님" } ?: "") +
                            com.detailline.callfollowcrm.domain.schedule.TodayBandPages
                                .label(page, total),
                        line2 = cAddr ?: collabToday.title,
                        action = if (cAddr != null) "길찾기" else null,
                        onAction = { onNavigateAddr(cAddr) },
                        onTap = onOpenSchedule
                    )
                } else if (doneToday) {
                    BandShell(
                        bg = AppTheme.colors.doneBg, fg = Color(0xFF0B6B51), subFg = Color(0xFF3E8C74),
                        icon = "", iconVector = Icons.Default.CheckCircle, border = Color(0xFFA8E6C9),
                        line1 = "오늘 시공 끝났어요",
                        line2 = next?.let { nextLine(it) } ?: "다음 시공은 아직 없어요",
                        action = null, onAction = {}, onTap = onOpenSchedule
                    )
                } else {
                    BandShell(
                        bg = Color.White, fg = TossTextPrimary, subFg = TossTextTertiary,
                        // 📅 이모지는 삼성 글꼴에서 "JUL 17" 로 그려진다 → 날짜 없는 그림으로.
                        icon = "", iconVector = Icons.Default.DateRange, border = TossDivider,
                        // '시공' 이 아니라 '일정' — 협업도 세기 때문. (2026-09-21)
                        line1 = if (hasNext) "오늘은 일정이 없어요" else "잡힌 일정이 없어요",
                        line2 = when {
                            nextIsCollab && collabNext != null -> collabNextLine(collabNext)
                            next != null -> nextLine(next)
                            else -> "밀린 상담·견적 챙기기 좋은 날이에요"
                        },
                        action = "일정 추가", onAction = onAddSchedule,
                        // ⚠️ 전엔 **다음 시공 손님 채팅**이 열렸다. "오늘은 없어요" 를 눌렀는데
                        //   누군지도 모르는 대화창이 뜨니 어리둥절하다. (2026-09-20 사장님 지적)
                        //   다음 시공은 **2쪽**이 맡는다. 여기선 일정 화면으로.
                        onTap = onOpenSchedule
                    )
                }
            } else if (nextIsCollab && collabNext != null) {
                // 2쪽도 '다음'이 협업이면 협업을 보여준다. 안 그러면 1쪽과 2쪽이 딴소리를 한다.
                val cAddr = collabNext.addr?.trim()?.takeIf { it.isNotBlank() }
                val cDays = ((DateTimeUtils.startOfDay(collabNext.scheduledAtMs) - dayStart) / DateTimeUtils.DAY_MS).toInt()
                BandShell(
                    bg = AppTheme.colors.categoryBg, fg = AppTheme.colors.category,
                    subFg = AppTheme.colors.textSub,
                    icon = "", iconVector = Icons.Default.Handshake,
                    border = AppTheme.colors.categoryBg,
                    iconBg = Color.White, iconTint = AppTheme.colors.category,
                    line1 = "협업 · " + DateTimeUtils.formatScheduledDate(collabNext.scheduledAtMs) +
                        (collabNext.timeLabel?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "") +
                        (if (cDays > 0) "  (D-$cDays)" else ""),
                    line2 = (collabNext.ownerName.takeIf { it.isNotBlank() }?.let { "${it}님 · " } ?: "") +
                        (cAddr ?: collabNext.title),
                    action = if (cAddr != null) "길찾기" else null,
                    onAction = { onNavigateAddr(cAddr) },
                    onTap = onOpenSchedule
                )
            } else {
                val c = next!!
                val who = c.name?.takeIf { it.isNotBlank() }
                    ?: com.detailline.callfollowcrm.util.PhoneNumberFormatter.format(c.phoneNumber)
                val addr = c.address?.trim()?.takeIf { it.isNotBlank() }
                val d = c.scheduledWorkDate ?: 0L
                val days = ((DateTimeUtils.startOfDay(d) - dayStart) / DateTimeUtils.DAY_MS).toInt()
                val t = c.scheduledWorkMinutes?.let { " " + DateTimeUtils.formatWorkMinutes(it) } ?: ""
                BandShell(
                    // 예정은 **파란 결**로 — 오늘(초록)과 한눈에 갈린다.
                    bg = AppTheme.colors.primaryBg, fg = Color(0xFF1B5FC1), subFg = Color(0xFF6E92C9),
                    icon = "", iconVector = Icons.Default.DateRange, border = Color(0xFFD7E5FB),
                    // 아이콘 칸도 파란 결로 — 회색 네모만 혼자 튀면 또 '깨진 자리' 처럼 보인다.
                    iconBg = Color(0xFFDCE8FC), iconTint = Color(0xFF3B77D1),
                    line1 = DateTimeUtils.formatScheduledDate(d) + t +
                        (if (days > 0) "  (D-$days)" else ""),
                    line2 = who + " · " + (addr ?: "주소 아직 없어요"),
                    action = if (addr != null) "길찾기" else null,
                    onAction = { onNavigateAddr(addr) },
                    onTap = { onOpenChat(c.phoneNumber, c.id) }
                )
            }
        }
        // 넘길 수 있다는 걸 알려주는 유일한 표시 — 점이 없으면 아무도 안 넘겨본다.
        if (pageCount > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { i ->
                    val on = pager.currentPage == i
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (on) 6.dp else 5.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (on) TossTextTertiary else TossDivider)
                    )
                }
            }
        }
    }
}


/**
 * 오늘 시공 히어로 — 시공 당일이면 맨 위 다크 카드(고객·주소·길찾기).
 *   없으면 "다음 시공" 미리보기(1~3곳) 또는 "오늘 없음" 긍정 카드. 2026-06-01.
 *   ⚠️ 2026-09-20 부터 홈은 [TodayBand] 를 쓴다. 이건 되돌릴 때를 위해 남겨둔 것.
 */
/**
 * 🏷️ 상담함 칩 한 줄. (2026-09-20 사장님 "거르기로 가자" · 프로토 artifact/6qoXfXjd4uxrL4ZNpN3rHU)
 *
 * 카톡 칩은 **"지금 뭘 할 건가"**(안읽음)를 세고, 사장님이 말씀하신 칩은 **"이 사람이 누구인가"**를 나눈다.
 * 둘 다 필요해서 **한 줄 안에서 자리를 갈랐다** — 왼쪽은 오늘 할 일, 구분선, 오른쪽은 사람 찾기.
 * 아침엔 왼쪽만 보면 되고, 통화 중엔 오른쪽으로 민다.
 *
 * **빨간 숫자는 왼쪽에만.** "시공 끝남" 이 40명이어도 할 일이 아니라서 숫자를 안 쓴다.
 */
@Composable
private fun InboxChips(
    selected: String,
    onSelect: (String) -> Unit,
    counts: Map<String, Int>,
    generalBadge: Int,
    /** 📦 한 번이라도 생긴 적 있는 칩 키 — 이 안의 것만 보여준다(전체·문자함·A/S 는 예외). (2026-10-08 사장님) */
    seen: Set<String>
) {
    // 시공 대기 = **예약은 잡혔고 아직 안 끝난** 손님. (2026-09-20 사장님)
    //   예약일이 지났는데 완료 표시가 없는 건도 여기 담긴다 — 안 그러면 어디에도 안 떠서 잊어버린다.
    val work = listOf(
        // 왼쪽 넷은 **한 결로** — 답장 대기 → 시공 대기 → 잔금 대기.
        //   "아직 안 끝난 것들" 이 순서대로 읽힌다. (2026-09-20 사장님 확정)
        //   '미수' 는 장부 말투라 독촉처럼 들려서 뺐다.
        // ❌ [답장 대기] 뺐다. (2026-09-20 사장님 "숫자 하단 탭으로 옮기는거 좋은것같아")
        //   안 챙긴 줄은 [전체] 에서도 이미 맨 위에 모이고 파란 점이 붙는다 — 앱이 더해주는 게 없었다.
        //   숫자는 하단 [상담함] 탭 배지로 갔다(카톡·문자앱이 쓰는 그 자리).
        "today" to "오늘 신규",
        "wait" to "시공 대기", "owe" to "잔금 대기"
    ) + (
        // 🔧 AS — **0명이면 아예 안 띄운다.** (2026-09-27 사장님)
        //   다른 칩은 0이어도 흐리게 남긴다(2026-09-20 "생겼다 없어졌다 하면 버그인가 생각할 수도") —
        //   그건 **늘 있는 일**이라 자리가 고정돼야 하기 때문이다.
        //   AS 는 **있다가 없는 일**이라 없을 때 자리를 비워도 고장으로 안 보인다.
        if ((counts["as"] ?: 0) > 0) listOf("as" to "A/S") else emptyList()
    )
    // 지인 칩은 안 만든다 — 사장님: "보통 지인은 문자보다 카톡을 사용함". (2026-09-20)
    //   대신 문자함을 **택배 / 광고·인증** 둘로 가른다. 택배는 무조건 자동 SMS 로 온다.
    // ❌ [새 번호] 뺐다. (2026-09-20 사장님 "칩자체가 쓸모없는 느낌이야. 전체에 다 있는데..
    //    보지도 않을 칩만 길어져서 어수선한느낌")
    //    맞다 — 이 칩만의 할 일이 없었다. '고객 카드 없는 번호' 는 [전체] 에 다 있고,
    //    광고는 이미 [광고] 칩이 가져가서 여기엔 [국제발신] 같은 찌꺼기만 쌓였다.
    val who = listOf("done" to "종료 고객")
    androidx.compose.foundation.lazy.LazyRow(
        // 위 6 + 검색창 아래 10 = 16 / 아래 10 + 목록 위 8 = 18. 거의 같게 맞춘다.
        //   전엔 위가 12 뿐이라 칩이 검색창에 붙어 보였다. (2026-09-20 사장님 "여백 간격")
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item { ChipPill("전체", null, selected == "all") { onSelect("all") } }
        // 📦 **한 번이라도 생긴 적 있는(seen) 칩만** — 처음 깐 사람은 전체만, 쓸수록 자라난다. (2026-10-08 사장님)
        //   A/S 는 예외(있을 때만 뜨는 제 규칙). 한 번 seen 에 들면 0명이어도 흐리게 남는다(자리 안 깜빡임, 9/20 결정).
        val workShown = work.filter { it.first == "as" || it.first in seen }
        items(workShown.size) { i ->
            val (k, label) = workShown[i]
            val c = counts[k] ?: 0
            // 0 이면 **흐리게**(누르는 건 살려둠). "생겼다 없어졌다하면 버그인가 생각할수도" (2026-09-20)
            ChipPill(label, c.takeIf { it > 0 }, selected == k, dim = c == 0) { onSelect(k) }
        }
        val whoShown = who.filter { it.first in seen }
        // 할 일 / 사람 찾기 사이 — 얇은 금 (종료 고객이 보일 때만)
        if (whoShown.isNotEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(18.dp).background(TossDivider))
            }
        }
        items(whoShown.size) { i ->
            val (k, label) = whoShown[i]
            ChipPill(label, null, selected == k) { onSelect(k) }
        }
        // 📨 **택배·광고를 하나로.** (2026-09-20 사장님) 그 둘을 누르면 제목이 "문자함" 으로 바뀌고
        //   배경도 흰색이었다 — 이미 다른 화면이었다. 칩 하나로 묶어야 제목이 바뀌는 게 말이 된다.
        //   가르기는 그 안에서 [광고][택배] 로 한다.
        // 🔴 숫자는 **회색** — 광고·인증문자는 답장할 일이 없다. 빨강은 "내가 손댈 것" 에만.
        item { ChipPill("문자함", generalBadge.takeIf { it > 0 }, selected == "box", quiet = true) { onSelect("box") } }
    }
}


@Composable
private fun TodayHeroCard(
    todayJobs: List<com.detailline.callfollowcrm.data.local.entity.CustomerEntity>,
    nextJobs: List<com.detailline.callfollowcrm.data.local.entity.CustomerEntity>,
    collabTodaySites: List<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite> = emptyList(),
    onOpenCustomer: (Long) -> Unit,
    onNavigate: (String) -> Unit,
    onNavigateAddr: (String?) -> Unit = {},
    onCall: (String) -> Unit,
    onOpenCollabSite: (String) -> Unit = {},
    onGoSchedule: () -> Unit,
    onOpenScheduleAtDay: (Long) -> Unit,
    /** '다음 시공' 을 누르면 그 손님 문자로. (2026-09-18 사장님) */
    onOpenChat: (phone: String, customerId: Long?) -> Unit = { _, _ -> },
    onAddSchedule: () -> Unit,
    onComplete: (com.detailline.callfollowcrm.data.local.entity.CustomerEntity) -> Unit,
    onCompleteCollabSite: (com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite) -> Unit = {},
    /** 사장님이 카드를 꾹 눌러 끌어 순서를 바꿨을 때 — 새 순서의 고객 ID 리스트. */
    onReorder: (List<Long>) -> Unit = {}
) {
    if (todayJobs.isNotEmpty() || collabTodaySites.isNotEmpty()) {
        // 오늘 시공이 2곳 이상이면 현장마다 독립 다크 카드를 시간순으로 쌓는다 (2026-06-11 사장님 결정).
        // 프로토 heroJobHtml 은 1곳짜리 정적 데모 — 라벨/디자인은 그대로, 카드만 현장 수만큼 반복.
        // 2곳 이상이면 꾹 눌러 트렐로식으로 순서 변경 가능(먼저 갈 현장을 위로). 1곳이면 일반 카드.
        // 협업 현장은 순서 변경 없이 뒤에 이어 붙임. (2026-06-24 사장님)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (todayJobs.size > 1) {
                TodayHeroReorderableList(todayJobs, onReorder, onOpenCustomer, onNavigate, onCall, onComplete)
            } else {
                todayJobs.forEach { c ->
                    TodayHeroJobCard(c, onOpenCustomer, onNavigate, onCall, onComplete)
                }
            }
            collabTodaySites.forEach { s ->
                CollabHeroJobCard(s, onOpenCollabSite, onCall, onNavigateAddr, onCompleteCollabSite)
            }
        }
    } else {
        // 프로토 heroEmptyHtml — 흰 카드(he-top "오늘 시공" + he-title + he-sub + he-next 회색박스 + he-add 버튼).
        Column(
            Modifier
                .fillMaxWidth()
                .tossCardShadow(RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(1.dp, TossDivider, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            // he-top — 📅 + "오늘 시공" (12px w800 t3)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, null, tint = TossTextTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("오늘 시공", color = TossTextInfo, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
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
            //   2026-06-07 사장님 요청: 다음 시공 카드 → 일정 탭에서 "그 날"이 선택된 채로(그 시공 카드 보임).
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TossGrayBg)
                    .clickable {
                        // 한 곳이면 **그 손님 문자로**. 여러 곳이면 줄마다 따로 받으므로 일정 탭으로.
                        //   (2026-09-18 사장님 "클릭했을때는 고객 문자내용으로 넘어가게")
                        val one = nextJobs.singleOrNull()
                        val day = nextJobs.firstOrNull()?.scheduledWorkDate
                        when {
                            one != null -> onOpenChat(one.phoneNumber, one.id)
                            day != null -> onOpenScheduleAtDay(day)
                            else -> onGoSchedule()
                        }
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
                            // nx-name — **주소가 먼저다.** 번호는 어디로 가는지 못 알려준다.
                            //   (2026-09-18 사장님 "번호를 봐도 뭐 어쩌라고..? 이런 느낌")
                            //   이름이 있으면 주소 뒤에 작게 붙인다. 주소가 없을 때만 이름·번호로 대신한다.
                            Text(
                                nextJobHeadline(j),
                                color = TossTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            nextJobWho(j)?.let { who ->
                                Text(
                                    who, color = TossTextTertiary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        } else {
                            Text(
                                "다음 시공 · $word · ${n}곳",
                                color = TossBlue, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold
                            )
                            nextJobs.forEach { j ->
                                // nx-line (13px w700) — nx-t 시간칩 + 주소. 줄을 누르면 그 손님 문자로. (2026-09-18)
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenChat(j.phoneNumber, j.id) }
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    j.scheduledWorkMinutes?.let { mins ->
                                        Text(
                                            DateTimeUtils.formatWorkMinutes(mins),
                                            color = TossBlue, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp)).background(Color.White)
                                                .padding(horizontal = 7.dp, vertical = 1.dp)
                                        )
                                        Spacer(Modifier.width(7.dp))
                                    }
                                    Text(
                                        nextJobHeadline(j),
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


/** 팀원 진행 배너 스타일 — kind(출발/도착/완료)별 색·아이콘·제목·동사. */
private data class TeamUpdateStyle(
    val accent: Color,
    val tint: Color,
    val icon: ImageVector,
    val title: String,
    val verb: String
)

// ⚠️ @Composable 이 아니라 AppTheme.colors 를 못 읽는다 → 밝은 화면 값을 직접(앱은 밝은 화면 고정).
private fun teamUpdateStyle(kind: String): TeamUpdateStyle = when (kind) {
    // 제목 끝 이모지(📍 ✅ 📝 🚗)를 뺐다 — **바로 왼쪽 아이콘이 이미 하는 말**이라 같은 걸 두 번 했다.
    //   이모지는 폰마다 다르게 그려지기도 한다. (2026-09-22 사장님)
    "arrived" -> TeamUpdateStyle(TossBlue, TossBlueSoft, Icons.Default.LocationOn, "팀원 현장 도착", "도착")
    "completed" -> TeamUpdateStyle(LightColors.category, LightColors.categoryBg, Icons.Default.CheckCircle, "작업 완료", "작업 완료")
    "note" -> TeamUpdateStyle(LightColors.caution, LightColors.cautionBg, Icons.Default.Edit, "현장 메모", "")
    else -> TeamUpdateStyle(TossSuccess, LightColors.doneBg, Icons.Default.Navigation, "팀원 출발", "출발")
}

// ⚠️ 위와 같은 이유로 LightColors 직접.
private fun collabUpdateStyle(kind: String): TeamUpdateStyle = when (kind) {
    "arrived" -> TeamUpdateStyle(TossBlue, TossBlueSoft, Icons.Default.LocationOn, "협업 현장 도착", "도착")
    "completed" -> TeamUpdateStyle(LightColors.category, LightColors.categoryBg, Icons.Default.CheckCircle, "협업 작업 완료", "작업 완료")
    else -> TeamUpdateStyle(TossSuccess, LightColors.doneBg, Icons.Default.Navigation, "협업 현장 출발", "출발")
}


/*
 * (지움 2026-09-21) KPI 카드 3장(🆕 오늘 신규 · ⚠️ 미확인 · 📅 이번주 시공) —
 *   @Preview 에서만 불리던 **죽은 코드**였다. 2026-09-20 상담함 리디자인에서 그 자리를
 *   칩([전체][오늘 신규][시공 대기][잔금 대기])이 가져갔다. 되살릴 일이 생기면 git 에서.
 */

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
                    TossBadge("미확인", color = TossError, background = AppTheme.colors.unpaidBg)
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(
                    onClick = {
                        com.detailline.callfollowcrm.util.PhoneDialer.open(context, item.record.phoneNumber)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.detailline.callfollowcrm.presentation.theme.AiMark(TossBlue, 13.dp, 5.dp)
                    Text(
                        aiCardSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TossBlue,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            } else if (aiCardSummary == null && isSmsCard) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.detailline.callfollowcrm.presentation.theme.AiMark(TossBlue.copy(alpha = 0.7f), 13.dp, 5.dp)
                    com.detailline.callfollowcrm.presentation.theme.AnimatedDots(
                        text = "요약 작성 중",
                        color = TossBlue.copy(alpha = 0.7f)
                    )
                }
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
                        "${DateTimeUtils.formatDateOnly(scheduled)} 시공 예약 · ${DateTimeUtils.dDayLabel(scheduled)}",
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
                            .background(AppTheme.colors.surfaceMuted)
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
                                runCatching { com.detailline.callfollowcrm.util.PhoneDialer.open(context, item.record.phoneNumber) }
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
 * 미확인 카드 우→좌 swipe → "광고/스팸" 영구 마킹.
 *   배경: 빨강 + 🚫 차단 아이콘 — swipe 중 드러나는 affordance.
 *   threshold 60% (Material 표준보다 살짝 높게) — 실수 swipe 방지.
 *   사장님 결정 2026-05-25: 좌→우 방향은 비활성 (다른 의미와 충돌 방지).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpamSwipeBox(
    onMarkSpam: () -> Unit,
    onMarkPersonal: () -> Unit,
    onCleanup: () -> Unit,
    content: @Composable () -> Unit
) {
    // 밀면 [🚫 스팸][👤 사생활][🧹 정리] 세 버튼(2026-06-23 사장님). 버튼 눌러야 동작 = 실수 방지.
    //   스팸=광고/스팸 영구숨김 · 사생활=내 개인연락처 영구숨김(링고 제외) · 정리=대기목록에서만. 셋 다 Snackbar Undo.
    //   스팸·사생활은 더보기 목록(kind별)에서 [해제]로 복구. undo 시 같은 LazyColumn key 라 카드 자연 복귀.
    com.detailline.callfollowcrm.presentation.component.SwipeRevealThreeBox(
        onFirst = onMarkSpam,
        onSecond = onMarkPersonal,
        onThird = onCleanup,
        firstLabel = "스팸",
        secondLabel = "지인",
        thirdLabel = "정리",
        firstColor = TossError,
        secondColor = AppTheme.colors.category,
        thirdColor = TossBlue,
        firstIcon = Icons.Filled.Block,
        secondIcon = Icons.Filled.Person,
        thirdIcon = Icons.Filled.CleaningServices,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)   // WaitingCard(18dp)와 모서리 맞춤 — 테두리 비침 fix.
    ) { content() }
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


/**
 * 💰 미수 한 줄 — **돈이 주인공**이다. (2026-09-20 사장님 "언발란스하다")
 *
 * 전엔 협업 요청 알림(InboxAlert)을 그대로 썼다. 알림은 *목록 위에 하나 얹히는* 모양인데
 * [미수] 칩은 화면이 통째로 이것뿐이라 남의 옷을 입은 것처럼 보였다.
 * 그래서 다른 칩과 같은 **줄 모양**으로 맞추고, 오른쪽에 금액과 [잔금 요청] 을 세로로 둔다.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OweRow(
    due: com.detailline.callfollowcrm.presentation.screen.home.HomeBalanceDueUi,
    index: Int,
    /** 마지막으로 오간 문자. 상담함의 다른 줄과 **같은 자리, 같은 글씨**. */
    lastBody: String?,
    summary: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 다른 줄과 같은 아바타 — 이것만 빠져도 "여기만 다른 화면" 으로 보인다.
        Avatar(due.name.takeIf { !it.startsWith("0") }, index)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    due.whereLabel ?: due.name, fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                // 🔴 **빨강은 진짜 늦었을 때만.** 이틀 지난 것과 두 주 지난 것이 같은 빨강이면
                //   빨강이 아무 말도 안 하게 된다. 한 주 넘은 것만 빨갛게. (2026-09-20 사장님)
                val late = due.daysSince >= 7
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (late) AppTheme.colors.unpaidBg else TossGrayBg)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    // 금액과 일째를 **딱지 하나**로. 오른쪽에 큰 금액을 따로 두었더니
                    //   현장 이름이 "동탄 아..." 로 잘렸다. (2026-09-20 실기)
                    Text(
                        "${due.daysSince}일째", fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (late) TossError else TossTextSecondary
                    )
                }
                // 다른 줄은 이 자리에 **시각**이 온다. 미수에선 **금액**이 더 쓸모 있다 —
                //   언제 왔는지는 아래 ✨요약이 말해준다.
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text(
                    shortWon(due.outstandingWon), fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold, color = TossTextPrimary
                )
            }
            // 📩 **마지막 문자** — 다른 줄과 똑같이. 없으면 이름/상태로 채운다.
            Spacer(Modifier.height(3.dp))
            Text(
                lastBody ?: (if (due.whereLabel != null && due.whereLabel != due.name) due.name
                    else "시공 끝남 · 잔금 남음"),
                fontSize = 13.sp, color = TossTextSecondary,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    summary, fontSize = 11.5.sp, color = TossTextTertiary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        // ⚠️ [잔금 요청] 버튼을 **뺐다.** 눌러봐야 `onOpenChat` — **줄을 누르는 것과 똑같은 동작**이었다.
        //   하는 일이 없으면서 90dp 를 차지해 현장 이름이 "동탄 아..." 로 잘렸다. (2026-09-20 실기)
        //   대신 목록 아래 한 줄로 알려준다.
    }
}


/** 프로토 sec-sub — 작은 섹션 부제 ("최근 대화"). */
@Composable
private fun SecSub(text: String) {
    Text(
        text,
        style = AppType.label,
        color = AppTheme.colors.textHint,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
    )
}


/**
 * 프로토 waitingCardHtml — 대기 고객 카드.
 *   heat 점 + 이름(+신규칩) + 시간 + 전화 + preview(AI 요약) + [답장하기].
 *   프로토의 "AI 추천 답변 quick-send" 는 앱에선 채팅의 추천 시스템이 담당 → 여기선 preparing 변형([답장하기]→채팅).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WaitingCard(
    item: HomeItem,
    aiSummary: String?,
    category: com.detailline.callfollowcrm.data.local.entity.CategoryEntity? = null,
    replyChoices: List<com.detailline.callfollowcrm.ai.ReplyChoice>,
    aiPrepEnabled: Boolean = true,
    onOpenChat: () -> Unit,
    onCall: () -> Unit,
    onQuickSend: (String) -> Unit,
    onLongPress: () -> Unit = {}
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
            .tossCardShadow(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .combinedClickable(onClick = { onOpenChat() }, onLongClick = { onLongPress() })
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
            // 태그 두 종류 — 규칙은 공용 한 곳에만(CustomerStatusTag.kt). (2026-09-16 사장님)
            com.detailline.callfollowcrm.presentation.component.CustomerTags(item.customer, category)
            // 상태 태그 — 시공 D-N / D-DAY / 계약금 / 완료. (2026-09-16 사장님: "예약고객은 기다려요에 태그가 안붙나?")
            //   최근 대화 줄엔 이미 붙던 것인데 여기엔 없었다. 이 고객은 분류가 '시공 대기'(자동 분류)라
            //   분류 태그도 일부러 숨겨져(모두에게 붙어서 '일당' 같은 진짜 분류가 안 도드라짐) **아무것도 안 붙었다.**
            //   답장을 기다리는 사람이 '오늘 시공 가는 집'인지 아닌지는 답장 내용이 완전히 달라지는 정보다.

            if (isNew) {
                Spacer(Modifier.width(8.dp))
                // 신규 태그만. (같은 날 재통화도 '신규' 그대로 — '다시 전화옴' 태그는 뺐다. 한 번호=한 카드로
                //   이미 묶여 중복은 안 생긴다. 2026-10-10 사장님: "그냥 신규 태그만 있어야지")
                Box(Modifier.background(TossBlueSoft, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
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
        // 받은 문자 — 고객이 실제로 보낸 마지막 문자 원문. "무슨 문자에 대한 답인지" 바로 알게. (2026-07-02 사장님)
        //   원문 없으면(통화만 등) AI 대화요약으로 폴백.
        val incoming = item.lastBody?.takeIf { it.isNotBlank() } ?: aiSummary
        if (!incoming.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossGrayBg).padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.lastBody.isNullOrBlank()) com.detailline.callfollowcrm.presentation.theme.AiMark(TossTextTertiary, 12.dp, 4.dp)
                    Text(if (item.lastBody.isNullOrBlank()) "통화 요약" else "받은 문자", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    incoming, fontSize = 13.5.sp, color = TossTextPrimary,
                    maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        if (item.lastBody.isNullOrBlank() || item.lastActivityWasCall) {
            // 통화/부재중만 있거나, 문자 뒤 마지막이 '통화로 끝난' 손님 — 답장할 문자가 없는데
            //   "AI 답변 준비 중"이 떠 버그처럼 보임(2026-06-14, 2026-06-17 사장님). → 통화 안내 + [문자하기].
            val missed = item.record.callType == "MISSED"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Call, null, tint = TossTextTertiary, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    if (missed) "부재중 전화 · 문자로 이어가기" else "통화한 손님 · 문자로 이어가기",
                    fontSize = 13.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .border(1.5.dp, Color(0xFFDCE7FB), RoundedCornerShape(999.dp))
                        .clickable { onOpenChat() }
                        .padding(horizontal = 15.dp, vertical = 8.dp)
                ) {
                    Text("문자하기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                }
            }
        } else {
            // 프로토 preparing — 문자 받았고 추천 준비 전: "AI 답변 준비 중…" + [답장하기].
            //   'AI 답변 준비' OFF 면 준비 자체를 안 하므로 "준비 중"은 거짓 → 담백하게 "새 문자". (2026-07-16 사장님)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = TossTextTertiary, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text("답장하면 AI 추천도 받아요", fontSize = 13.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
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


/**
 * 프로토 recentRowHtml — 최근 대화 행. 아바타 + 이름 + 태그 + 시간 + 요약 한 줄.
 *   (LazyColumn 간격상 흰 카드 형태로 — 프로토는 한 카드 안 연속행, 시각적으로 동등.)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    item: HomeItem,
    index: Int,
    unread: Boolean,
    aiSummary: String?,
    category: com.detailline.callfollowcrm.data.local.entity.CategoryEntity? = null,
    /** 지금 고른 칩 — 뱃지가 '그 안에서 갈리는 것'을 보여주려고 필요하다. (2026-09-21) */
    filter: String = "all",
    onOpenChat: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val name = item.customer?.name?.takeIf { it.isNotBlank() }
    val title = name ?: PhoneNumberFormatter.format(item.record.phoneNumber)
    // A안 (2026-08-04 사장님): "최근 무슨 말 했는지"가 먼저 보이게 — 마지막 실제 문자를 주(위)로,
    //   ✨AI 요약은 보조(아래 회색)로. 문자 없이 통화만이면 요약을 주로.
    //   (이전엔 읽은 줄에서 요약이 최근 문자를 덮어써 "최근에 뭐라 했는지" 안 보인다는 신고. 2026-08-04)
    val lastMsg = item.lastBody?.takeIf { it.isNotBlank() }
        ?.let { (if (item.lastSent == true) "나: " else "") + it }
    // "AI 가 쓴 글" 표시 — 글자 ✨ 가 아니라 **앱이 그리는 아이콘**(아래 AiMark).
    //   갤럭시의 ✨ 는 남색 밤하늘 타일로 그려져 줄마다 까만 네모가 붙었다. (2026-09-20 사장님)
    //   서버 요약에서 이모지를 뺀 뒤로 이 줄이 **손님 말인지 AI 요약인지** 구분할 표시는 이것뿐이다.
    val summaryLine = aiSummary?.takeIf { it.isNotBlank() }
    val primaryText = lastMsg ?: summaryLine
    val secondaryText = if (lastMsg != null) summaryLine else null
    Row(
        Modifier
            .fillMaxWidth()
            // 🫁 **줄 키는 내용과 상관없이 같다.** (2026-09-20 사장님
            //   "요약이 있든 없든 여백이 똑같이 있어야 디자인 일관성이 같지 않나")
            //   맞다. 요약이 붙은 줄만 키가 커지면 목록이 들쭉날쭉해진다.
            //   **세 줄짜리 높이보다 넉넉하게 96** — 두 줄짜리는 가운데 정렬되어 위아래로 숨을 쉰다.
            //   (2026-09-20 사장님 "더 여유롭게 보고싶어" → 88 에서 96 으로)
            .heightIn(min = 96.dp)
            .combinedClickable(onClick = { onOpenChat() }, onLongClick = { onLongClick?.invoke() })
            .padding(horizontal = AppSpace.s16, vertical = AppSpace.s12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 안 읽음 점 거터 — 읽은 줄도 빈 14dp 자리 유지(아바타 세로 정렬 통일).
        Box(Modifier.width(14.dp), contentAlignment = Alignment.CenterStart) {
            if (unread) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(TossBlue))
            }
        }
        Avatar(name, index)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 이름+태그는 한 덩어리(가변폭 weight)로 묶고 시각은 그 밖에 → 시각이 이름 길이와 무관하게
                //   '항상 맨 오른쪽'에 정렬된다. (2026-08-05 사장님: 날짜가 이름 길이 따라 삐뚤어짐)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    // 🅰️ 줄 제목 = headline(17 Bold). 안 읽은 줄만 한 단계 더 굵게.
                    Text(
                        title,
                        style = AppType.headline.copy(
                            fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.Bold
                        ),
                        color = AppTheme.colors.text,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // 그룹 태그 (사장님 분류: '일당' 등) — 한눈에 어떤 묶음인지. (2026-08-04 사장님) 보라색으로 상태태그와 구분.
                    // 태그 두 종류 — 규칙은 공용 한 곳에만. (2026-09-16)
                    //   filter 를 넘기는 이유: 칩이 이미 말해준 건("종료 고객") 뱃지가 또 말하지 않는다.
                    com.detailline.callfollowcrm.presentation.component.CustomerTags(item.customer, category, filter = filter)

                }
                Spacer(Modifier.width(6.dp))
                // 시각은 **훑을 때 눈에 안 걸려야** 한다 → caption(13 Medium).
                Text(
                    recentTimeLabel(item.lastActivityMs.takeIf { it > 0L } ?: item.record.endedAt),
                    style = AppType.caption, color = AppTheme.colors.textHint
                )
            }
            if (!primaryText.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                // 📖 **읽는 글** = body(15 Medium). 안 읽은 줄만 색을 진하게(굵기 말고 색으로).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 문자가 없어 요약이 주(主)로 올라온 줄 — AI 가 쓴 글임을 표시.
                    if (lastMsg == null && summaryLine != null) AiMark()
                    Text(
                        primaryText, style = AppType.body,
                        color = if (unread) AppTheme.colors.text else AppTheme.colors.textSub,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            // ✨AI 요약 — 최근 문자 아래 회색 작게 (A안). 최근 문자가 있을 때만 보조로 노출.
            if (!secondaryText.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                // 메타 줄 = caption(13 Medium).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AiMark()
                    Text(
                        secondaryText, style = AppType.caption,
                        color = AppTheme.colors.textHint,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 최근 대화 처음 보여주는 줄 수. */
private const val RECENT_FIRST = 12
/** '이전 대화 더 보기' 한 번에 늘리는 줄 수 — 한 프레임 일감을 작게 유지. (2026-09-15 사장님) */
private const val RECENT_MORE = 30

private data class RecentTag(val text: String, val fg: Color, val bg: Color)


/** 프로토 recent 시각 — 오늘/어제/N일 전/M/D (절대시각 X). */
private fun recentTimeLabel(ms: Long): String {
    val today = DateTimeUtils.startOfDay(System.currentTimeMillis())
    return when (val days = ((today - DateTimeUtils.startOfDay(ms)) / DateTimeUtils.DAY_MS).toInt()) {
        0 -> "오늘"
        1 -> "어제"
        in 2..6 -> "${days}일 전"
        else -> RECENT_MD_FORMAT.format(java.util.Date(ms))
    }
}


// ============================================================
// @Preview — Android Studio 우측 패널 / split view 에서 빌드 없이 UI 확인.
//   사용법: Android Studio 에서 이 파일 열면 우측 상단 "Split" / "Design" 클릭.
//   코드 저장 시 1~3초 안에 자동 갱신. phone 빌드 안 해도 됨.
// ============================================================

/** S9 사이즈 별도 확인 (사장님 실기기 360x740 dp). */
/** 2026-05-30 다크모드 Preview — 다크 테마 적용 시 모양 확인용. */
