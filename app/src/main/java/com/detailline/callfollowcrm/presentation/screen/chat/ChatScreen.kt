@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.detailline.callfollowcrm.presentation.screen.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import com.detailline.callfollowcrm.ai.ReplySuggestions
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.detailline.callfollowcrm.ai.NextAction
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.domain.model.TemplateCategory
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.launch

/**
 * 대시보드 → 번호 탭의 메인 진입 화면.
 *
 * 구조 (위→아래):
 *  1. TopAppBar — ← 뒤로 / 이름·번호 / 📞 전화 / ⓘ 고객카드
 *  2. 메시지 채팅 (LazyColumn, reverseLayout=true — 최신이 아래에 표시되는 카톡 스타일)
 *  3. 템플릿 알약 가로 스크롤 — 탭하면 입력칸에 본문 채워짐 (즉시 전송 X)
 *  4. composer pill — [입력칸][▶]
 *  5. 풀스크린 이미지 뷰어 (썸네일 탭 시)
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenCustomerDetail: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // 카톡 패턴: 키보드 떠있으면 뒤로가기 1번 = 키보드만 내림, 본문 유지. 한 번 더 = 화면 pop.
    //   ime bottom 픽셀 값으로 visibility 판정 (Compose 1.5 의 isImeVisible 없는 버전 호환).
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottomPx > 0
    androidx.activity.compose.BackHandler(enabled = imeVisible) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val customer by viewModel.customer.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val pricingItems by viewModel.pricingItems.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val starred by viewModel.starred.collectAsState()
    val polishing by viewModel.aiPolishing.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val suggestion by viewModel.effectiveSuggestions.collectAsState()
    val suggestionsStale by viewModel.suggestionsAreStale.collectAsState()
    val suggestionsLoading by viewModel.suggestionsLoading.collectAsState()
    // 별표된 메시지 식별 키 set — ChatBubble 의 isStarred 여부 빠르게 판정
    val starredKeys = remember(starred) {
        starred.map { it.messageDateMs to it.sent }.toHashSet()
    }
    var starredViewerOpen by remember { mutableStateOf(false) }
    // 말풍선 꾹 누름 → BottomSheet 띄울 메시지 (null = 닫힘).
    //   사장님 결정 2026-05-25: 꾹 누름 = 저장/복사 선택. 직접 토글 X.
    var bubbleActionTarget by remember {
        mutableStateOf<com.detailline.callfollowcrm.data.repository.SmsRepository.SmsMessage?>(null)
    }
    // 대화 요약 카드 사장님 명시 접기 — composer focus 자동 접힘과는 별개.
    //   사장님 피드백 2026-05-25: 카드 4-5줄에 말풍선이 가려져서 접기 필요.
    var summaryManualCollapsed by remember { mutableStateOf(false) }

    // Composer 임시저장 (2026-05-27 사장님 통점) — phone 별 in-memory draft 복원.
    //   init = ChatDraftStore.get(phone), 변경 시 자동 save, 전송 후 input="" = 자동 clear (set 이 empty 면 remove).
    var input by remember { mutableStateOf(viewModel.loadDraft()) }
    LaunchedEffect(input) { viewModel.saveDraft(input) }
    var fullscreenImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // 권한 요청 직후 자동 재시도용 — 입력 본문을 기억.
    var pendingSend by remember { mutableStateOf<String?>(null) }
    // 사진 첨부 — Photo Picker 로 선택된 URI 들. 발송 시 갤럭시 메시지로 본문+사진 같이 전달.
    var attachedPhotos by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    // AI 제안 박스의 [버튼] 액션 — null 이면 다이얼로그 안 떠 있는 상태.
    //   templatePickerCategory = "" 이면 전체 템플릿, 카테고리 이름이면 그 카테고리만.
    var templatePickerCategory by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    // confirm_schedule 액션 — 사장님이 고객에게 시공 가능 날짜 제안 흐름.
    //   캘린더로 날짜 선택 → 입력란에 "X월 X일 (요일) 괜찮으세요?" 자동 박힘 → 사장님 검토 후 전송.
    //   register_schedule (시공일 등록) 과 별개 — 고객 동의 후 register_schedule 로 따로 등록.
    var showProposalDatePicker by remember { mutableStateOf(false) }
    // P3 — 견적서 작성기 (사장님 결정 2026-05-24): send_estimate 액션 시 템플릿 picker 대신 띄움.
    var showEstimateBuilder by remember { mutableStateOf(false) }
    // P3 — 시공일 등록 직후 "이 일정으로 계약금 안내문도 만들어드릴까요?" 다이얼로그.
    //   null 이 아니면 표시 + 그 시공일 ts 보관. 사장님이 "네" 누르면 RESERVATION picker 띄움.
    var showDepositFollowupForMs by remember { mutableStateOf<Long?>(null) }
    // 다음 템플릿 picker.onPick 에서 본문 앞에 "예약 일정: $dateStr" 자동 prepend 할 ts.
    //   register_schedule 후속 흐름 또는 request_deposit (이미 시공일 확정된 경우) 에서 세팅.
    var depositPrefillScheduledMs by remember { mutableStateOf<Long?>(null) }
    // 2026-05-24 정보 위계: composer focus 여부 (BasicTextField 의 onFocusChanged 결과).
    //   focus = true → 위쪽 대화요약+AI제안 카드를 1줄 헤더로 압축. 메시지/composer 영역 확보.
    //   focus = false → 풀 카드 펼침.
    var composerFocused by remember { mutableStateOf(false) }
    // ▶ 보내기 확인 다이얼로그 — null 이면 안 떠 있음.
    //   사장님이 ▶ 탭하면 (body, photos) 스냅샷 저장 + 다이얼로그 표시. [보내기] 탭해야 진짜 발송.
    var sendConfirm by remember { mutableStateOf<Pair<String, List<android.net.Uri>>?>(null) }

    val pickPhotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) attachedPhotos = attachedPhotos + uris
    }

    val sendPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val body = pendingSend
        pendingSend = null
        if (granted && body != null) {
            viewModel.sendMessage(context, body) { ok -> if (ok) input = "" }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadMessages()
        viewModel.loadSuggestions()
        viewModel.loadFullSummary()
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    val displayName = customer?.name?.takeIf { it.isNotBlank() }
        ?: PhoneNumberFormatter.format(viewModel.phoneNumber)
    val displayPhone = PhoneNumberFormatter.format(viewModel.phoneNumber)

    // 실제 발송 — confirm 다이얼로그 [보내기] 또는 권한 요청 직후 호출.
    // 사진 첨부면 MMS (klinker → 갤럭시 메시지 fallback), 아니면 SMS.
    val performSend: (String, List<android.net.Uri>) -> Unit = { body, photos ->
        if (photos.isNotEmpty()) {
            if (!com.detailline.callfollowcrm.util.SmsSender.hasPermission(context)) {
                pendingSend = body
                sendPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            } else {
                viewModel.sendMessageWithPhotos(context, body, photos) { ok ->
                    if (ok) {
                        input = ""
                        attachedPhotos = emptyList()
                    } else {
                        // klinker 실패 → 갤럭시 메시지 fallback (사장님이 거기서 직접 ▶)
                        val result = com.detailline.callfollowcrm.util.SmsIntentHelper
                            .openSmsComposeWithAttachments(
                                context = context,
                                phoneNumber = viewModel.phoneNumber,
                                body = body,
                                attachmentUris = photos
                            )
                        if (result is com.detailline.callfollowcrm.util.SmsIntentHelper.Result.Opened) {
                            input = ""
                            attachedPhotos = emptyList()
                        }
                    }
                }
            }
        } else if (body.isNotEmpty()) {
            if (!com.detailline.callfollowcrm.util.SmsSender.hasPermission(context)) {
                pendingSend = body
                sendPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            } else {
                viewModel.sendMessage(context, body) { ok -> if (ok) input = "" }
            }
        }
    }

    Scaffold(
        containerColor = TossGrayBg,
        // ChatScreen 의 contentWindowInsets 에 ime 포함 → 키보드 뜨면 inner padding 이 자동
        // 늘어남 → composer 가 키보드 위. systemBars 와 union/add 가 받는 type 인식 못해서
        // ime 단독으로만 박음 (nav bar 영역과 겹쳐도 nav bar 가 작아서 시각 손해 미세).
        contentWindowInsets = WindowInsets.ime,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TossTextPrimary,
                            maxLines = 1
                        )
                        if (customer?.name?.isNotBlank() == true) {
                            // 이름이 따로 있으면 작게 번호 함께 (헷갈리지 않게)
                            Text(
                                displayPhone,
                                fontSize = 11.sp,
                                color = TossTextTertiary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                actions = {
                    // 저장된 메시지 모아보기 — 별이 아닌 북마크 아이콘 (즐겨찾기 오해 방지).
                    //   카운트 0 이면 outlined, 있으면 fill + 숫자 badge.
                    //   2026-05-25: 사장님 피드백 — 별 아이콘은 "고객 즐겨찾기" 와 분간 어려움 → 북마크 채택.
                    IconButton(onClick = { starredViewerOpen = true }) {
                        if (starred.isEmpty()) {
                            Icon(Icons.Outlined.BookmarkBorder, "저장된 메시지", tint = TossTextSecondary)
                        } else {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.TopEnd) {
                                Icon(Icons.Default.Bookmarks, "저장된 메시지 ${starred.size}건", tint = TossBlue)
                                // 카운트 badge — 우상단 작은 빨간 동그라미 + 숫자.
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = Color(0xFFEF4444),
                                    modifier = Modifier
                                        .offset(x = 6.dp, y = (-4).dp)
                                        .size(16.dp)
                                ) {
                                    androidx.compose.foundation.layout.Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            starred.size.toString().take(2),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    IconButton(onClick = { dialPhone(context, viewModel.phoneNumber) }) {
                        Icon(Icons.Default.Phone, "전화 걸기", tint = TossBlue)
                    }
                    IconButton(onClick = {
                        // Customer 없으면 upsert 후 진입 (ChatScreen 의 [ⓘ] 는 항상 진입 가능)
                        scope.launch {
                            val id = viewModel.ensureCustomerId()
                            onOpenCustomerDetail(id)
                        }
                    }) {
                        Icon(Icons.Default.Info, "고객 카드", tint = TossTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
            // imePadding() 제거 — Scaffold.contentWindowInsets 가 ime 처리 (inner 에 포함).
        ) {
            // P0/P1/P2 — 상단 AI 요약 박스 + AI 제안 박스 (서버 응답 있을 때만 표시).
            // 2026-05-24: composer focus (ime 떠있음) 시 두 박스를 한 줄 헤더로 축소.
            //   사장님이 입력 시작 = 위쪽 정보보다 메시지/composer 영역 확보가 우선.
            //   ime 풀리면 자동으로 풀 박스 복귀.
            val aiSummary by viewModel.aiSummary.collectAsState()
            // 2026-05-26 사장님 보고 fix:
            //   요약 안 된 채로 진입하면 카드 자체가 안 보여서 "그냥 비어있다" 느낌.
            //   → aiSummary == null 이고 메시지가 2건 이상 (요약할 거리 있음) 이면 진행 placeholder 표시.
            //   메시지 1건 이하는 요약할 게 없어 표시 안 함.
            val hasEnoughForSummary = messages.size >= 2
            if (aiSummary == null && hasEnoughForSummary) {
                SummaryLoadingPlaceholder(
                    collapsed = composerFocused || summaryManualCollapsed,
                    onToggleCollapsed = { summaryManualCollapsed = !summaryManualCollapsed }
                )
            }
            // 2026-05-27 사장님 결정: 템플릿 chip row 의 [액션] 토글 칩과 공유.
            //   action_type 별 분기 — RINGGO_SERVER_P0P1P2_UPGRADE.md §4 매칭 시나리오.
            //   AI 자동 추천 (next-action-suggest) + 사장님 수동 [액션] 토글 둘 다 같은 trigger 사용.
            val triggerActionByType: (String) -> Unit = { actionType ->
                when (actionType) {
                    "send_estimate" ->
                        showEstimateBuilder = true
                    "request_deposit" -> {
                        depositPrefillScheduledMs = customer?.scheduledWorkDate
                        templatePickerCategory = TemplateCategory.RESERVATION.name
                    }
                    "send_followup" ->
                        templatePickerCategory = ""
                    "confirm_schedule" ->
                        showProposalDatePicker = true
                    "register_schedule" ->
                        showDatePicker = true
                    else -> { /* unknown action_type — no-op */ }
                }
            }
            aiSummary?.let { s ->
                val action = remember(s.nextActionJson) { NextAction.parse(s.nextActionJson) }
                val onActionHandler: (NextAction) -> Unit = { a -> triggerActionByType(a.actionType) }
                val showCollapsed = composerFocused || summaryManualCollapsed
                val isSummaryRefreshing by viewModel.isSummaryRefreshing.collectAsState()
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                if (showCollapsed) {
                    CollapsedSummaryHeader(
                        summaryLineCount = com.detailline.callfollowcrm.ai.parseConversationLines(s.conversationSummaryJson).size,
                        nextActionTitle = action?.title,
                        isRefreshing = isSummaryRefreshing,
                        onExpand = {
                            // 2026-05-27 사장님 보고 fix: composer focus 일 때 헤더 탭해도 안 펼쳤음.
                            //   명시적으로 focus 해제 → showCollapsed = false → 펼침.
                            focusManager.clearFocus()
                            summaryManualCollapsed = false
                        }
                    )
                } else {
                    // 옵션 A — 대화 요약 + AI 제안 한 카드로 통합. 수직 공간 절반 절약.
                    UnifiedSummaryCard(
                        entity = s,
                        action = action,
                        isRefreshing = isSummaryRefreshing,
                        onAction = onActionHandler,
                        onCollapse = { summaryManualCollapsed = true }
                    )
                }
            }

            // 메시지 채팅 영역 — 풀 카톡 스타일. reverseLayout=true 라 newest 가 아래에 표시.
            // 그래서 messages 도 dateMs DESC 그대로 넘기면 됨 (LazyColumn 이 뒤집어 렌더).
            val listState = rememberLazyListState()
            // 2026-05-25 사장님 피드백: 진입 시 가장 최신 메시지가 즉시 화면에 잡혀야 함.
            //   메시지 첫 로드 + 새 메시지 도착 (size 변경) 시 items[0] (=최신) 로 강제 스크롤.
            //   사장님이 위로 옛 메시지 보다가 새 메시지 와도 자동 점프 — 약간 거슬릴 수도 있지만
            //   "옛 메시지가 앞에 보이지 않게" 가 더 중요 (사장님 명시 우선순위).
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.scrollToItem(0)
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                state = listState
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "주고받은 문자가 없어요",
                                style = MaterialTheme.typography.bodySmall,
                                color = TossTextTertiary
                            )
                        }
                    }
                } else {
                    // key 안 줌 — SMS id 와 MMS id 가 별도 테이블이라 같은 값일 수 있어 충돌 위험.
                    items(messages) { msg ->
                        ChatBubble(
                            body = msg.body,
                            timeMs = msg.dateMs,
                            sent = msg.sent,
                            imageUris = msg.imageUris,
                            isStarred = starredKeys.contains(msg.dateMs to msg.sent),
                            onImageTap = { fullscreenImageUri = it },
                            onLongPress = {
                                // 2026-05-25: 직접 toggleStar 호출 X → BottomSheet 띄워서 저장/복사 선택.
                                bubbleActionTarget = msg
                            }
                        )
                    }
                }
            }

            // 템플릿 알약 (가로 스크롤) — 탭하면 입력칸에 본문 채워짐. 즉시 전송 안 함.
            // 2026-05-24 시각 충돌 fix: 답변 추천 영역이 노출될 때 = 템플릿 알약 숨김.
            //   답변 추천 칩 / 템플릿 알약 둘 다 둥근 칩이라 인접 시 사장님 시선 혼란.
            //   답변 추천 = 사장님 톤 학습 기반 우선. 답변 추천 없을 때만 (서버 X 또는 stale) 템플릿 노출.
            //
            // 2026-05-27 사장님 결정: chip row 첫 자리에 [⚡ 액션] 토글.
            //   탭하면 기존 템플릿 휙 사라지고 액션 칩 5개 (견적/일정/시공등록/계약금/후속) 노출.
            //   다시 탭하면 템플릿 복귀. AI 자동 추천 액션 시스템과 같은 trigger 사용 (수동 진입점).
            val suggestionAreaVisible = messages.firstOrNull()?.sent == false
            var actionsMode by remember { mutableStateOf(false) }
            if (!suggestionAreaVisible && (templates.isNotEmpty() || actionsMode)) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // [⚡ 액션] 토글 — 항상 좌측 sticky.
                    item(key = "action-toggle") {
                        ActionToggleChip(
                            selected = actionsMode,
                            onTap = { actionsMode = !actionsMode }
                        )
                    }
                    if (actionsMode) {
                        // 액션 모드: 5개 액션 칩 노출.
                        items(QUICK_ACTIONS, key = { it.actionType }) { qa ->
                            QuickActionPill(
                                label = qa.label,
                                emoji = qa.emoji,
                                onTap = {
                                    triggerActionByType(qa.actionType)
                                    actionsMode = false  // 액션 실행 후 자동 복귀
                                }
                            )
                        }
                    } else {
                        // 기본 모드: 사장님 템플릿.
                        items(templates, key = { it.id }) { tpl ->
                            TemplatePill(
                                template = tpl,
                                onTap = { input = tpl.body }
                            )
                        }
                    }
                }
            }

            // AI 추천 답변 영역 — 가장 최신 메시지가 고객이 보낸 것일 때만 표시.
            // SmsReceiver 가 백그라운드에서 서버에 prepare 트리거 → ChatScreen 진입 시 fetch.
            // 없거나 stale 이면 사장님이 ↻ 로 재생성.
            //
            // 2026-05-25 자동 접힘: 사장님이 직접 타이핑 시작 (input.isNotBlank()) = 추천 안 보고 싶음
            //   → 1줄 헤더로 접힘. composer 비우면 자동 펼침. 또는 헤더 탭으로 수동 토글.
            val showSuggestionArea = messages.firstOrNull()?.sent == false
            if (showSuggestionArea) {
                var suggestionsExpanded by remember { mutableStateOf(true) }
                val inputNonBlank = input.isNotBlank()
                LaunchedEffect(inputNonBlank) {
                    suggestionsExpanded = !inputNonBlank
                }
                SuggestionArea(
                    suggestion = suggestion,
                    loading = suggestionsLoading,
                    expanded = suggestionsExpanded,
                    isStale = suggestionsStale,
                    onToggleExpand = { suggestionsExpanded = !suggestionsExpanded },
                    onPickSuggestion = { picked ->
                        input = picked
                        // 답변 추천 사용 직후 = 자동 접힘 (사장님이 보낼 본문에 집중)
                        suggestionsExpanded = false
                    },
                    onRegenerate = { viewModel.regenerateSuggestions() }
                )
            }

            // composer pill — 인스타 DM 스타일 ([✨][📷][입력][▶]) + 사진 첨부 미리보기
            Composer(
                input = input,
                onChange = { input = it },
                isPolishing = polishing,
                onAiPolish = {
                    viewModel.aiPolish(input) { polished -> input = polished }
                },
                onAttachPhoto = {
                    pickPhotos.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                attachments = attachedPhotos,
                onRemoveAttachment = { uri -> attachedPhotos = attachedPhotos - uri },
                onSend = {
                    // ▶ 탭 = 즉시 발송 X. 사장님 확인 다이얼로그 거침 (실수 발송 방지).
                    val body = input.trim()
                    if (body.isNotEmpty() || attachedPhotos.isNotEmpty()) {
                        sendConfirm = body to attachedPhotos
                    }
                },
                isSending = isSending,
                onFocusChange = { focused -> composerFocused = focused }
            )
        }
    }

    // ▶ 보내기 확인 다이얼로그 — 사장님이 실수로 보내는 거 방지.
    //   본문 미리보기 + 사진 첨부 개수 + 수신자 이름 보여주고 [보내기] 한 번 더 탭해야 진짜 발송.
    sendConfirm?.let { (body, photos) ->
        SendConfirmDialog(
            recipient = displayName,
            body = body,
            photoCount = photos.size,
            onCancel = { sendConfirm = null },
            onConfirm = {
                sendConfirm = null
                performSend(body, photos)
            }
        )
    }

    // 🔖 저장된 메시지 모아보기 다이얼로그 — 이 번호의 별표된 메시지만 시간순.
    if (starredViewerOpen) {
        AlertDialog(
            onDismissRequest = { starredViewerOpen = false },
            title = {
                Text(
                    if (starred.isEmpty()) "🔖 저장된 메시지"
                    else "🔖 저장된 메시지 ${starred.size}건",
                    color = TossTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (starred.isEmpty()) {
                    // 빈 상태 — 사장님이 처음 진입했을 때 친절한 안내.
                    //   2026-05-25: 사장님 피드백 — 빈 상태 안내가 약하면 "왜 눌렀는데 빈 화면?" 당황.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "아직 저장한 메시지가 없어요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TossTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "📌 사용 방법\n" +
                                "채팅 말풍선을 길~게 누르면 메뉴가 떠요.\n" +
                                "‘🔖 저장’ 누르면 여기에 모아 보여드려요.\n" +
                                "‘📋 복사’ 도 같이 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossTextSecondary,
                            lineHeight = 18.sp
                        )
                        Text(
                            "💡 언제 쓰면 좋나요\n" +
                                "약속 시각, 견적 금액, 분쟁 시 증거가 될 메시지 등 나중에 다시 찾고 싶은 내용.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossTextTertiary,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(starred) { msg ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (msg.sent) TossBlueSoft else TossGrayBg,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (msg.sent) "보냄" else "받음",
                                        color = if (msg.sent) TossBlue else TossTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        DateTimeUtils.formatShort(msg.messageDateMs),
                                        color = TossTextTertiary,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    msg.messageBody,
                                    color = TossTextPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { starredViewerOpen = false }) {
                    Text("닫기", color = TossTextSecondary)
                }
            },
            containerColor = Color.White
        )
    }

    // 말풍선 꾹 누름 → [🔖 저장 / 📋 복사] BottomSheet.
    //   사장님 결정 2026-05-25: 직접 토글 X → 사용자가 명확히 선택. 복사도 자주 필요.
    bubbleActionTarget?.let { msg ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val clipboard = LocalClipboardManager.current
        val alreadyStarred = starredKeys.contains(msg.dateMs to msg.sent)
        ModalBottomSheet(
            onDismissRequest = { bubbleActionTarget = null },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // 메시지 미리보기 — 어느 메시지 액션인지 한눈에.
                Text(
                    msg.body.take(60) + if (msg.body.length > 60) "…" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TossTextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Spacer(Modifier.height(4.dp))
                BubbleActionRow(
                    icon = Icons.Default.Bookmarks,
                    tint = TossBlue,
                    label = if (alreadyStarred) "🔖 저장 해제" else "🔖 저장",
                    subtitle = if (alreadyStarred) "북마크 목록에서 제거" else "분쟁/약속·금액·중요 메시지 보관",
                    onClick = {
                        viewModel.toggleStar(msg.body, msg.dateMs, msg.sent)
                        bubbleActionTarget = null
                    }
                )
                if (msg.body.isNotBlank()) {
                    BubbleActionRow(
                        icon = Icons.Default.Info,
                        tint = TossTextSecondary,
                        label = "📋 복사",
                        subtitle = "메시지 본문을 클립보드에",
                        onClick = {
                            clipboard.setText(AnnotatedString(msg.body))
                            bubbleActionTarget = null
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // 풀스크린 이미지 뷰어 (썸네일 탭 시)
    fullscreenImageUri?.let { uri ->
        Dialog(
            onDismissRequest = { fullscreenImageUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenImageUri = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "사진",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fullscreenImageUri = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, "닫기", tint = Color.White)
                }
            }
        }
    }

    // AI 제안 박스의 [견적 작성하기 / 계약금 안내 / 후기 요청] 액션 — 템플릿 선택 시트.
    // 카테고리 필터링된 템플릿 보여주고 탭하면 input 채우고 닫힘.
    templatePickerCategory?.let { category ->
        TemplatePickerDialog(
            category = category,
            templates = templates,
            onPick = { tpl ->
                // P3 — RESERVATION 흐름 (시공일 등록 직후 또는 request_deposit) 이면
                // 본문 앞에 "예약 일정: 5월 26일 (수)" 한 줄 자동 prepend.
                val ms = depositPrefillScheduledMs
                input = if (ms != null) prependScheduleNote(tpl.body, ms) else tpl.body
                depositPrefillScheduledMs = null
                templatePickerCategory = null
            },
            onDismiss = {
                depositPrefillScheduledMs = null
                templatePickerCategory = null
            }
        )
    }

    // AI 제안 박스의 [시공일 등록] 액션 — DatePicker → CustomerEntity.scheduledWorkDate.
    // 등록 후 자동으로 "계약금 안내문도 만들어드릴까요?" 다이얼로그 표시 (P3 통합 흐름).
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ts ->
                        viewModel.setScheduledWorkDate(ts)
                        showDepositFollowupForMs = ts
                    }
                    showDatePicker = false
                }) {
                    Text("등록", color = TossBlue, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("취소", color = TossTextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // AI 제안 박스의 [일정 잡기/협의] 액션 — 캘린더 → 자동 텍스트 박힘 흐름.
    //   register_schedule 과 별개. 이건 고객 동의 받기 전 단계 — scheduledWorkDate 안 박음.
    //   사장님 톤: "5월 28일 (수) 괜찮으세요?" 입력란 자동. 사장님 검토 후 전송.
    if (showProposalDatePicker) {
        // 2026-05-27 사장님 보고 재fix:
        //   직전 시도 (Material3 DatePickerDialog wrapper + verticalScroll + heightIn) 로도 작은 폰
        //   (갤S9 등) 에서 confirm/dismiss 버튼이 화면 밖. wrapper 가 contents+버튼 합산 사이즈를
        //   제어 못 함. 직접 Dialog+Surface 로 짜서 닫기 버튼을 fixed bottom 으로 빼고, DatePicker
        //   영역만 weight 로 남는 공간 채우면서 verticalScroll. 화면 height 의 88% 로 max 강제.
        //   - 우측 연필 아이콘 = showModeToggle=false 유지
        //   - 날짜 탭 = 즉시 진행 + 자동 닫힘 = LaunchedEffect 유지
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = null)
        LaunchedEffect(datePickerState.selectedDateMillis) {
            val ts = datePickerState.selectedDateMillis ?: return@LaunchedEffect
            val dateLabel = com.detailline.callfollowcrm.util.DateTimeUtils.formatScheduledDate(ts)
            val proposal = "$dateLabel 시공 가능하실까요? 괜찮으시면 그날로 잡아드릴게요."
            // 2026-05-28 사장님 보고 fix:
            //   날짜 잘못 눌렀다가 다시 누르면 이전 제안이 그대로 남고 새 제안이 아래에 추가됨 → 두 줄 다 보임.
            //   사장님 의도 = "잘못 누른 이전 제안은 교체". 다만 사장님이 직접 친 인사말 같은 다른 텍스트는 보존.
            //   해결: formatScheduledDate 출력 형식과 일치하는 정규식으로 input 안의 모든 기존 proposal 제거 후 append.
            //   - formatScheduledDate: 같은 해 = "M월 d일 (E)", 다른 해 = "yyyy년 M월 d일 (E)" → 패턴에 yyyy 옵션 포함.
            //   - 사장님이 proposal 텍스트 직접 수정한 경우 = 정규식 안 매칭 → 그 부분 그대로 남고 새 것 append (의도 보존).
            val proposalPattern = Regex(
                """(\d{4}년 )?\d{1,2}월 \d{1,2}일 \([일월화수목금토]\) 시공 가능하실까요\? 괜찮으시면 그날로 잡아드릴게요\."""
            )
            val cleaned = input.replace(proposalPattern, "").lines()
                .joinToString("\n") { it.trimEnd() }
                .trimEnd('\n', ' ')
            input = if (cleaned.isBlank()) proposal else "$cleaned\n$proposal"
            showProposalDatePicker = false
        }
        val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
        Dialog(
            onDismissRequest = { showProposalDatePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = screenHeightDp * 0.88f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 헤더
                    Text(
                        "고객한테 제안할 날짜",
                        style = MaterialTheme.typography.titleMedium,
                        color = TossTextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
                    )
                    Text(
                        "날짜를 탭하면 메시지 입력란에 자동으로 박혀요. 검토 후 ▶ 전송하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextSecondary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)
                    )
                    // 캘린더 영역 — 남는 공간 weight + scroll (작은 폰에서 캘린더 자체가 길어도 scroll)
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        DatePicker(state = datePickerState, showModeToggle = false)
                    }
                    // 닫기 버튼 — fixed bottom. 화면 height 와 무관하게 항상 보임.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showProposalDatePicker = false }) {
                            Text("닫기", color = TossTextSecondary)
                        }
                    }
                }
            }
        }
    }

    // 시공일 등록 직후 "계약금 안내문도 만들어드릴까요?" 후속 다이얼로그 (P3).
    showDepositFollowupForMs?.let { ts ->
        DepositFollowupDialog(
            scheduledMs = ts,
            onConfirm = {
                depositPrefillScheduledMs = ts
                templatePickerCategory = TemplateCategory.RESERVATION.name
                showDepositFollowupForMs = null
            },
            onDismiss = { showDepositFollowupForMs = null }
        )
    }

    // P3 — 견적서 작성기 (send_estimate 액션). 항목 체크 + 수량 + 자동 합산 → composer 본문 합성.
    if (showEstimateBuilder) {
        EstimateBuilderDialog(
            items = pricingItems,
            onConfirm = { body ->
                input = body
                showEstimateBuilder = false
            },
            onDismiss = { showEstimateBuilder = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    body: String,
    timeMs: Long,
    sent: Boolean,
    imageUris: List<android.net.Uri>,
    isStarred: Boolean,
    onImageTap: (android.net.Uri) -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (sent) Arrangement.End else Arrangement.Start
    ) {
        // 보낸 메시지면 별표가 왼쪽에 (말풍선 왼쪽 = 옆), 받은 메시지면 오른쪽에 (말풍선 오른쪽 = 옆).
        // 사장님이 한눈에 어느 게 중요 표시된 건지 보이도록.
        if (sent && isStarred) {
            Icon(
                Icons.Default.Bookmarks,
                contentDescription = "저장된 메시지",
                tint = TossBlue,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (sent) TossBlue else TossBlueSoft,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (imageUris.isNotEmpty()) {
                    // 한 줄에 최대 3장 썸네일. 탭하면 풀스크린.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        imageUris.chunked(3).forEach { rowUris ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rowUris.forEach { uri ->
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "첨부 사진",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(TossGrayBg)
                                            .clickable { onImageTap(uri) }
                                    )
                                }
                            }
                        }
                    }
                    if (body.isNotBlank()) Spacer(Modifier.height(6.dp))
                }
                if (body.isNotBlank()) {
                    Text(
                        body,
                        color = if (sent) Color.White else TossTextPrimary,
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    DateTimeUtils.formatShort(timeMs),
                    color = if (sent) Color.White.copy(alpha = 0.7f) else TossTextTertiary,
                    fontSize = 10.sp
                )
            }
        }
        // 받은 메시지는 별표가 오른쪽 (보낸 메시지는 왼쪽 — 위에서 처리됨)
        if (!sent && isStarred) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Bookmarks,
                contentDescription = "저장된 메시지",
                tint = TossBlue,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 고객 마지막 메시지에 대한 AI 추천 답변 영역.
 * 표시 조건: ChatScreen 진입 시 messages.firstOrNull()?.sent == false 일 때만 호출.
 * 칩 탭 → 입력칸 채워짐. ↻ → 서버에 재생성 요청 + 폴링.
 *
 * 2026-05-25 expanded/collapsed 토글:
 *   - expanded = true → 헤더 + 칩 row (큰 영역)
 *   - expanded = false → 헤더만 (1줄). 헤더 탭하면 펼침.
 *   - 사장님이 타이핑 시작 (input.isNotBlank) → 호출부에서 expanded=false 로 자동 접힘.
 */
@Composable
private fun SuggestionArea(
    suggestion: ReplySuggestions?,
    loading: Boolean,
    expanded: Boolean,
    isStale: Boolean,
    onToggleExpand: () -> Unit,
    onPickSuggestion: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    val hasSuggestions = suggestion != null && suggestion.suggestions.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (expanded) "✨ AI 추천 답변" else "✨ AI 추천 답변 (탭하여 펼치기)",
                color = TossBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            // 접힘/펼침 표시 + 새로고침 버튼
            if (hasSuggestions) {
                Text(
                    if (expanded) "▾" else "▸",
                    color = TossTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            IconButton(
                onClick = onRegenerate,
                enabled = !loading,
                modifier = Modifier.size(28.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = TossBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "재생성",
                        tint = TossTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        // stale 안내 — 새 메시지 도착했으나 추천은 옛 메시지 기준 (2026-05-28).
        //   chip 은 그대로 보임. 사장님이 보고 직접 ↻ 누를지 옛 답변 보낼지 결정.
        if (isStale && hasSuggestions && expanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                "📨 새 메시지가 왔어요 — ↻ 누르면 새 답변 받아요",
                color = TossTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        // 칩 영역 — expanded 일 때만 표시
        if (expanded) {
            when {
                hasSuggestions -> {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        itemsIndexed(suggestion!!.suggestions) { idx, choice ->
                            SuggestionChip(
                                index = idx + 1,
                                label = choice.label,
                                text = choice.text,
                                onTap = { onPickSuggestion(choice.text) }
                            )
                        }
                    }
                }
                loading -> {
                    // 2026-05-27 사장님 보고 fix:
                    //   답변 추천 polling 중 칩 자리가 비어있어 "뭐 받는 중이지?" 헷갈림.
                    //   shimmer chip 3개 + dots 텍스트 — 곧 채워질 자리임을 시각화.
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        items(3) {
                            com.detailline.callfollowcrm.presentation.theme.ShimmerLine(
                                modifier = Modifier.width(140.dp),
                                height = 32.dp,
                                cornerRadius = 16.dp
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    com.detailline.callfollowcrm.presentation.theme.AnimatedDots(
                        text = "답변 추천 준비 중",
                        color = TossTextTertiary
                    )
                }
                else -> {
                    Text(
                        "↻ 눌러서 답변 추천 받기",
                        color = TossTextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 2026-05-28 v2 (킬러 콘텐츠 1단계 — 의도 분화):
 *   label != null 이면 카드 상단에 작은 intent 라벨 줄 ("💰 견적 안내") 노출.
 *   label == null (옛 스키마 fallback) 이면 기존 모양 그대로 (번호 + 본문).
 *
 * 사장님 결정 — 답변 카드 안 상단에 이모지 + 짧은 라벨. 본문 읽기 전에 어떤 전략의 답변인지 0.5초 안에 파악.
 */
@Composable
private fun SuggestionChip(index: Int, label: String?, text: String, onTap: () -> Unit) {
    Surface(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clickable { onTap() },
        shape = RoundedCornerShape(16.dp),
        color = TossBlue
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (!label.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$index",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        label,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$index",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplatePill(template: MessageTemplateEntity, onTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        onClick = onTap
    ) {
        Box(modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                template.title,
                color = TossBlue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 2026-05-27 사장님 결정 — Composer 위 chip row 의 [⚡ 액션] 토글 + 5개 액션 칩.
 *   사장님 시나리오: 신규 고객 전화 문의 → 답장 작성. 가격/일정 빠른 접근 필요.
 *   [⚡ 액션] 탭 → 기존 템플릿 휙 사라지고 액션 칩 5개 노출. 다시 탭 → 템플릿 복귀.
 *
 * AI 자동 추천 (next-action-suggest) 와 같은 triggerActionByType 사용 — UX 일관성.
 */
private data class QuickAction(
    val actionType: String,
    val emoji: String,
    val label: String
)

private val QUICK_ACTIONS = listOf(
    QuickAction("send_estimate",     "💰", "견적 작성"),
    QuickAction("confirm_schedule",  "📅", "일정 잡기"),
    QuickAction("register_schedule", "📌", "시공일 등록"),
    QuickAction("request_deposit",   "💳", "계약금 안내"),
    QuickAction("send_followup",     "✉️", "후속 문자")
)

@Composable
private fun ActionToggleChip(selected: Boolean, onTap: () -> Unit) {
    // selected = 액션 모드 — 강조 (파란 배경 + 흰 글자). off = 흰 배경 + 파란 글자 (다른 chip 과 구분).
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) TossBlue else Color.White,
        onClick = onTap
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                if (selected) "✕ 닫기" else "⚡ 액션",
                color = if (selected) Color.White else TossBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun QuickActionPill(label: String, emoji: String, onTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = TossBlueSoft,
        onClick = onTap
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                "$emoji $label",
                color = TossBlue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onChange: (String) -> Unit,
    isPolishing: Boolean,
    onAiPolish: () -> Unit,
    onAttachPhoto: () -> Unit,
    attachments: List<android.net.Uri>,
    onRemoveAttachment: (android.net.Uri) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // 첨부된 사진 썸네일 영역 (있을 때만)
        if (attachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(attachments) { uri ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.size(64.dp)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "첨부 사진",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(TossGrayBg)
                        )
                        // 우측 상단 X 버튼 — 첨부 제거
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(20.dp)
                                .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
                                .clickable { onRemoveAttachment(uri) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "제거",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
        // composer = 카톡 메모장 패턴 (사장님 결정 2026-05-24):
        //   [네모 박스: 입력 + 우측 📷 ✨] + 외부 우측 [▶ 전송]
        //   단일 Row + verticalAlignment.Bottom — 한 줄 입력 시 텍스트와 아이콘이 같은 라인,
        //   여러 줄 입력 시 텍스트는 위로 늘어나고 아이콘은 박스 bottom 고정.
        val canSend = input.isNotBlank() || attachments.isNotEmpty()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            // 중앙 = 입력 박스 (네모 + radius). 박스 안 단일 Row 로 입력 + 아이콘 묶음.
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, TossDivider)
            ) {
                Row(
                    // 2026-05-28 사장님 보고: 텍스트가 박스에 빡빡하게 붙음 → 토스/카톡 톤으로 padding 보강.
                    //   vertical 2dp → 8dp (텍스트 위·아래 숨 트임), end 2dp → 6dp (텍스트와 아이콘 간격).
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    // CenterVertically: 한 줄 입력에서 텍스트와 아이콘이 같은 baseline. (Bottom 은 ~3dp 어긋남.)
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = onChange,
                        textStyle = TextStyle(
                            color = TossTextPrimary,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(TossBlue),
                        // 카톡 패턴: 최대 5줄까지 보이고 그 이상 입력 시 박스 내부 자동 스크롤.
                        // 견적서 본문처럼 긴 메시지 입력해도 composer 가 화면 절반 차지하는 일 X.
                        maxLines = 5,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { state -> onFocusChange(state.isFocused) },
                        decorationBox = { inner ->
                            if (input.isEmpty()) {
                                Text(
                                    "메시지 입력",
                                    color = TossTextTertiary,
                                    fontSize = 15.sp
                                )
                            }
                            inner()
                        }
                    )
                    // 📷 사진 첨부 (이모지 자리)
                    IconButton(
                        onClick = onAttachPhoto,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "사진 첨부",
                            tint = TossTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // ✨ AI 다듬기 — polishing 중엔 회전 인디케이터.
                    IconButton(
                        onClick = onAiPolish,
                        enabled = !isPolishing,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isPolishing) {
                            CircularProgressIndicator(
                                color = TossBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "AI 다듬기",
                                tint = TossBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // 외부 우측 = ▶ 전송 둥근 버튼. 외부 Row 의 Bottom alignment 로 박스와 같은 라인 정렬.
            //   2026-05-27 진행감 fix: 발송 중엔 spinner 로 교체 + 재탭 방지 (canSend && !isSending).
            Surface(
                modifier = Modifier.size(44.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = if (canSend && !isSending) TossBlue else TossDivider,
                onClick = { if (canSend && !isSending) onSend() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSending) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "보내기",
                            tint = if (canSend) Color.White else TossTextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun dialPhone(context: android.content.Context, phoneNumber: String) {
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
            data = android.net.Uri.parse("tel:$phoneNumber")
        }
        context.startActivity(intent)
    }
}

/**
 * aiSummary 가 null 일 때 placeholder 카드.
 *   2026-05-26 사장님 보고 fix: "통화요약 안 됐을 때 작성 중 표시 + 접혀있는 느낌".
 *   - 헤더 라인: ✨ 대화 요약 작성 중...  (AnimatedDots — 점이 순환)
 *   - collapsed=true 면 한 줄 헤더만 (composer focus / 사장님 접음)
 *   - collapsed=false 면 헤더 + ShimmerLine 2줄 (요약 본문이 곧 올 자리)
 *   - 어느 상태든 헤더 영역 탭 = 토글 (UnifiedSummaryCard 와 일관)
 */
@Composable
private fun SummaryLoadingPlaceholder(
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit
) {
    if (collapsed) {
        // CollapsedSummaryHeader 와 동일한 시각 톤 — 한 줄 작은 칩.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .clickable { onToggleCollapsed() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✨", fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
            com.detailline.callfollowcrm.presentation.theme.AnimatedDots(
                text = "대화 요약 작성 중",
                color = TossBlue,
                modifier = Modifier.weight(1f)
            )
            Text(
                "▼",
                color = TossTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onToggleCollapsed() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.detailline.callfollowcrm.presentation.theme.AnimatedDots(
                    text = "✨ 대화 요약 작성 중",
                    color = TossBlue,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "▲",
                    color = TossTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            // 본문 자리 — shimmer 2줄로 "여기 곧 채워집니다" affordance.
            com.detailline.callfollowcrm.presentation.theme.ShimmerLine(
                modifier = Modifier.fillMaxWidth(0.85f)
            )
            Spacer(Modifier.height(6.dp))
            com.detailline.callfollowcrm.presentation.theme.ShimmerLine(
                modifier = Modifier.fillMaxWidth(0.6f)
            )
        }
    }
}

/**
 * 2026-05-24 — composer focus (ime 떠있음) 시 대화 요약 + AI 제안 박스를 한 줄로 압축.
 * 사장님이 입력 시작 = 위쪽 정보 압박 줄이고 메시지/composer 영역 확보.
 *   - "✨ 대화 요약 4줄 · {AI 제안 title}" 형식
 *   - ime 풀리면 호출부에서 자동으로 풀 박스로 복귀
 */
@Composable
private fun CollapsedSummaryHeader(
    summaryLineCount: Int,
    nextActionTitle: String?,
    isRefreshing: Boolean = false,
    onExpand: () -> Unit
) {
    val parts = buildList {
        if (summaryLineCount > 0) add("대화 요약 ${summaryLineCount}줄")
        if (!nextActionTitle.isNullOrBlank()) add(nextActionTitle)
    }
    if (parts.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable { onExpand() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "✨",
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            parts.joinToString(" · "),
            color = TossBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 2026-05-27 사장님 보고 fix: 갱신 중 spinner — "로딩 중인지 안 보임" 문제 해결.
        if (isRefreshing) {
            CircularProgressIndicator(
                color = TossBlue,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(12.dp).padding(end = 6.dp)
            )
        }
        // 펼치기 신호 — 영역 어디든 탭하면 풀 박스. 화살표만으로 affordance.
        //   2026-05-26 사장님 보고 fix: 펼침 상태와 일관성. "글 안 읽어도 토글 알 수 있게".
        Text(
            "▼",
            color = TossTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 2026-05-24 옵션 A — 대화 요약 + AI 제안 통합 카드 (한 카드로 두 박스 합침).
 * 사장님이 짚은 "정보 영역이 너무 많아 화면 답답" 문제 fix.
 *
 * 구조:
 *  - 상단: ✨ 대화 요약 4줄
 *  - 구분선
 *  - 하단: 다음 액션 (작은 좌측 title/subtitle + 우측 작은 칩 버튼)
 *
 * focus 되면 호출부에서 CollapsedSummaryHeader 로 전환.
 */
@Composable
private fun UnifiedSummaryCard(
    entity: com.detailline.callfollowcrm.data.local.entity.AiSummaryEntity,
    action: NextAction?,
    isRefreshing: Boolean = false,
    onAction: (NextAction) -> Unit,
    onCollapse: () -> Unit
) {
    val lines = com.detailline.callfollowcrm.ai.parseConversationLines(entity.conversationSummaryJson)
    val hasSummary = lines.isNotEmpty()
    val hasAction = action != null
    if (!hasSummary && !hasAction) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(14.dp)
    ) {
        if (hasSummary) {
            // 2026-05-26 사장님 보고 fix:
            //   헤더 영역 전체 탭 = 토글 (이전엔 우측 "접기" 버튼만 동작 → 사장님이 영역 탭해도 안 닫힘).
            //   우측은 ▲ 화살표만 — 글자 없어도 affordance 충분. 헤더 전체 clickable.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onCollapse() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "✨ 대화 요약",
                    style = MaterialTheme.typography.labelMedium,
                    color = TossBlue,
                    fontWeight = FontWeight.SemiBold
                )
                if (entity.latestMessageTimestampMs > 0) {
                    Text(
                        " · ${com.detailline.callfollowcrm.util.DateTimeUtils.formatShort(entity.latestMessageTimestampMs)} 까지",
                        style = MaterialTheme.typography.labelSmall,
                        color = TossTextTertiary
                    )
                }
                Spacer(Modifier.weight(1f))
                // 2026-05-27 사장님 보고 fix: 갱신 중 spinner — 헤더에 작은 indicator.
                if (isRefreshing) {
                    CircularProgressIndicator(
                        color = TossBlue,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(12.dp).padding(end = 6.dp)
                    )
                }
                // ▲ 화살표만 — 토글 affordance. 헤더 전체가 clickable 이라 화살표 별도 클릭 X.
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "▲",
                        color = TossTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TossTextPrimary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        if (hasSummary && hasAction) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.Divider(color = TossDivider, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))
        }

        if (hasAction) {
            val a = action!!
            val accent = when (a.urgency) {
                "high" -> Color(0xFFEF4444)
                "medium" -> Color(0xFFF59E0B)
                else -> TossBlue
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "다음 액션",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        a.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TossTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    a.subtitle?.let { sub ->
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = TossTextSecondary
                        )
                    }
                }
                a.primaryLabel?.let { label ->
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accent,
                        onClick = { onAction(a) }
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text(
                                label,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * P1 — ChatScreen 상단 대화 요약 박스. 에이닷 벤치마킹. (UnifiedSummaryCard 로 대체됨, 호환 위해 보존)
 * conversationSummaryJson (List<String>) 표시. 박스 없으면 null 받아서 호출 측이 안 그림.
 */
@Composable
private fun ConversationSummaryBox(
    entity: com.detailline.callfollowcrm.data.local.entity.AiSummaryEntity
) {
    val lines = com.detailline.callfollowcrm.ai.parseConversationLines(entity.conversationSummaryJson)
    if (lines.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(14.dp)
    ) {
        Text(
            "✨ 대화 요약",
            style = MaterialTheme.typography.labelMedium,
            color = TossBlue,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        lines.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = TossTextPrimary,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

/**
 * P2 — AI 제안 박스. nextActionJson 파싱해서 표시. 박스 없으면 안 그림.
 * urgency 별 색상: high=빨강 / medium=노랑 / low=파랑.
 * [버튼] 탭 = onAction(action) — 호출부에서 action_type 별 분기.
 */
@Composable
private fun NextActionBox(json: String?, onAction: (NextAction) -> Unit) {
    val action = NextAction.parse(json) ?: return
    val accent = when (action.urgency) {
        "high" -> Color(0xFFEF4444)
        "medium" -> Color(0xFFF59E0B)
        else -> TossBlue
    }
    val bg = when (action.urgency) {
        "high" -> Color(0xFFFEF2F2)
        "medium" -> Color(0xFFFFF7ED)
        else -> Color(0xFFEEF4FF)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "✨ AI 제안",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                action.title,
                style = MaterialTheme.typography.titleMedium,
                color = TossTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            action.subtitle?.let { sub ->
                Spacer(Modifier.height(2.dp))
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary
                )
            }
        }
        action.primaryLabel?.let { label ->
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent,
                onClick = { onAction(action) }
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        label,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * AI 제안 박스의 템플릿 선택 시트.
 * category = "" 이면 전체. 카테고리에 매칭되는 템플릿 없으면 전체로 fallback (사장님이 직접 고름).
 * 탭 = onPick(tpl) → composer input 채워짐. 자동 발송 X.
 */
@Composable
private fun TemplatePickerDialog(
    category: String,
    templates: List<MessageTemplateEntity>,
    onPick: (MessageTemplateEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val filtered = remember(templates, category) {
        val byCategory = if (category.isBlank()) templates
        else templates.filter { it.category == category }
        // 카테고리 비어있으면 사장님이 직접 고르도록 전체로 fallback
        byCategory.ifEmpty { templates }
    }
    val title = if (category.isBlank()) "템플릿 선택"
    else "${categoryLabel(category)} 템플릿"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, color = TossTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            if (filtered.isEmpty()) {
                Text(
                    "등록된 템플릿이 없어요.\n설정 → 템플릿에서 추가할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextTertiary
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { tpl ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TossGrayBg)
                                .clickable { onPick(tpl) }
                                .padding(12.dp)
                        ) {
                            Text(
                                tpl.title,
                                color = TossBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tpl.body,
                                color = TossTextSecondary,
                                fontSize = 13.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", color = TossTextSecondary)
            }
        },
        containerColor = Color.White
    )
}

private fun categoryLabel(categoryName: String): String =
    runCatching { TemplateCategory.valueOf(categoryName).label }.getOrDefault("템플릿")

/**
 * P3 — 계약금 안내문 본문 앞에 시공일 한 줄 prepend.
 * 사장님이 보낼 메시지에서 일정 = 가장 중요한 정보. 템플릿 본문 그대로면 시공일이 빠질 위험.
 */
private fun prependScheduleNote(body: String, scheduledMs: Long): String {
    val dateStr = DateTimeUtils.formatScheduledDate(scheduledMs)
    return "예약 일정: $dateStr\n\n$body"
}

/**
 * P3 — 시공일 등록 직후 표시. "이 일정으로 계약금 안내문도 만들어드릴까요?".
 * [네, 만들기] = RESERVATION 템플릿 picker 띄우고 본문에 시공일 자동 prepend.
 * [등록만 하기] = 닫기. 사장님이 나중에 직접 [계약금 안내] 액션 누를 수 있음.
 */
@Composable
private fun DepositFollowupDialog(
    scheduledMs: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateStr = DateTimeUtils.formatScheduledDate(scheduledMs)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "시공일을 등록했어요",
                color = TossTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TossBlueSoft
                ) {
                    Text(
                        "예약 일정: $dateStr",
                        color = TossBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "이 일정으로 계약금 안내문도 만들어드릴까요?",
                    color = TossTextSecondary,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("네, 만들기", color = TossBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("등록만 하기", color = TossTextSecondary)
            }
        },
        containerColor = Color.White
    )
}

/**
 * ▶ 보내기 확인 다이얼로그 — 사장님이 실수로 즉시 발송하는 거 방지.
 * 본문 미리보기 + 사진 첨부 개수 + 수신자 이름 보여주고 [보내기] 한 번 더 탭해야 진짜 발송.
 */
@Composable
private fun SendConfirmDialog(
    recipient: String,
    body: String,
    photoCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                "$recipient 에게 보낼까요?",
                color = TossTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column {
                if (body.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TossBlueSoft
                    ) {
                        Text(
                            body,
                            color = TossTextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                if (photoCount > 0) {
                    if (body.isNotBlank()) Spacer(Modifier.height(8.dp))
                    Text(
                        "📷 사진 ${photoCount}장 첨부",
                        color = TossTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("보내기", color = TossBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("취소", color = TossTextSecondary)
            }
        },
        containerColor = Color.White
    )
}

/**
 * P3 — 견적서 작성기 다이얼로그.
 *
 * 사장님 결정 (2026-05-24): 견적은 부위/평수/타일종류가 매번 달라 템플릿으로 통일 어려움.
 * → 가격표 항목 체크 + 수량 + 자동 합산 + 본문 자동 합성.
 *
 * 흐름:
 *  1) 신축/구축 토글 — 항목 필터링 (COMMON 은 둘 다)
 *  2) 항목별 체크박스 + 체크 시 수량 stepper (- 1 +)
 *  3) 합계 자동 표시
 *  4) [견적서 만들기] → composer 본문에 합성된 문구 입력. 사장님이 composer 에서 비고/수정 후 ▶.
 *
 * 비고 input 은 일부러 다이얼로그 안에 두지 않음 (2026-05-24 버그 fix) —
 * AlertDialog 는 중앙 고정이라 키보드 뜨면 BasicTextField 가 가려짐.
 * 견적서 본문이 composer 에 들어가면 사장님이 거기서 비고 자유롭게 추가.
 */
@Composable
private fun EstimateBuilderDialog(
    items: List<com.detailline.callfollowcrm.data.local.entity.PricingItemEntity>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 신축 (NEW) 기본. 구축 (OLD) 토글 가능. COMMON 항목은 둘 다.
    var buildingType by remember {
        mutableStateOf(com.detailline.callfollowcrm.data.repository.PricingCategory.NEW)
    }
    // 항목 id → 수량. 0 또는 미존재 = 미선택.
    val selectedQty = remember { mutableStateMapOf<Long, Int>() }

    val visibleItems = remember(items, buildingType) {
        items.filter { it.category == buildingType.name || it.category == "COMMON" }
            .sortedBy { it.displayOrder }
    }
    val totalSum = remember(selectedQty.toMap(), visibleItems) {
        visibleItems.sumOf { item ->
            val qty = selectedQty[item.id] ?: 0
            item.price * qty
        }
    }
    val anySelected = selectedQty.values.any { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "✨ 견적서 작성",
                color = TossTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp)) {
                // 신축/구축 토글
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BuildingTypeChip(
                        label = "신축",
                        selected = buildingType == com.detailline.callfollowcrm.data.repository.PricingCategory.NEW,
                        onClick = { buildingType = com.detailline.callfollowcrm.data.repository.PricingCategory.NEW },
                        modifier = Modifier.weight(1f)
                    )
                    BuildingTypeChip(
                        label = "구축",
                        selected = buildingType == com.detailline.callfollowcrm.data.repository.PricingCategory.OLD,
                        onClick = { buildingType = com.detailline.callfollowcrm.data.repository.PricingCategory.OLD },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                // 항목 리스트
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visibleItems, key = { it.id }) { item ->
                        EstimateItemRow(
                            title = item.title,
                            price = item.price,
                            quantity = selectedQty[item.id] ?: 0,
                            onToggle = {
                                val cur = selectedQty[item.id] ?: 0
                                if (cur > 0) selectedQty.remove(item.id) else selectedQty[item.id] = 1
                            },
                            onIncrement = { selectedQty[item.id] = (selectedQty[item.id] ?: 0) + 1 },
                            onDecrement = {
                                val cur = selectedQty[item.id] ?: 0
                                if (cur > 1) selectedQty[item.id] = cur - 1
                                else if (cur == 1) selectedQty.remove(item.id)
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 합계
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TossBlueSoft, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("합계", color = TossTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatWon(totalSum),
                        color = TossBlue,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val body = buildEstimateBody(
                        buildingType = buildingType,
                        items = visibleItems,
                        quantities = selectedQty.toMap(),
                        totalSum = totalSum
                    )
                    onConfirm(body)
                },
                enabled = anySelected
            ) {
                Text(
                    "견적서 만들기",
                    color = if (anySelected) TossBlue else TossTextTertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TossTextSecondary)
            }
        },
        containerColor = Color.White
    )
}

@Composable
private fun BuildingTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) TossBlue else TossGrayBg,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (selected) Color.White else TossTextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EstimateItemRow(
    title: String,
    price: Long,
    quantity: Int,
    onToggle: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val checked = quantity > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) TossBlueSoft else Color.White)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 체크박스
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) TossBlue else Color.White)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .padding(1.dp)
                        .background(TossDivider, RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TossTextPrimary, fontSize = 13.sp)
            Text(formatWon(price), color = TossTextSecondary, fontSize = 11.sp)
        }
        // 수량 stepper (체크된 경우에만 노출)
        if (checked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton("−", onClick = onDecrement)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${quantity}",
                    color = TossTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 16.dp)
                )
                Spacer(Modifier.width(6.dp))
                StepperButton("+", onClick = onIncrement)
            }
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, TossDivider),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = TossBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 원 단위를 "40만원" / "1,500,000원" 형식으로. */
private fun formatWon(amount: Long): String {
    if (amount == 0L) return "0원"
    return if (amount >= 10_000L && amount % 10_000L == 0L) {
        "${amount / 10_000L}만원"
    } else {
        "${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(amount)}원"
    }
}

/**
 * 견적서 본문 합성. pricing.md §답변 형식 가이드 따름 (표 X, 짧은 줄별 나열, 합계, 추가 가능 안내).
 * 비고는 사장님이 composer 에서 직접 추가 (다이얼로그 안 input 은 키보드 가림 이슈로 제거).
 */
private fun buildEstimateBody(
    buildingType: com.detailline.callfollowcrm.data.repository.PricingCategory,
    items: List<com.detailline.callfollowcrm.data.local.entity.PricingItemEntity>,
    quantities: Map<Long, Int>,
    totalSum: Long
): String = buildString {
    append("${buildingType.label} 기준 견적입니다.\n")
    for (item in items) {
        val qty = quantities[item.id] ?: 0
        if (qty <= 0) continue
        append("- ${item.title} ${formatWon(item.price)}")
        if (qty > 1) append(" × ${qty}")
        append("\n")
    }
    append("합계 ${formatWon(totalSum)}\n")
    append("\n실리콘 제거나 셀프줄눈 흔적 있으면 현장 확인 후 추가될 수 있어요.")
}

/**
 * 말풍선 꾹 누름 BottomSheet 의 액션 한 줄. 좌측 아이콘 + 텍스트 (라벨 + 부제).
 * 사장님 손가락 도달성 위해 vertical padding 넉넉히.
 */
@Composable
private fun BubbleActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = TossTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TossTextTertiary
            )
        }
    }
}
