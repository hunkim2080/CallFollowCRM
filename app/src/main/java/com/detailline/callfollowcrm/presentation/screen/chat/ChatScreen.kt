@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.detailline.callfollowcrm.presentation.screen.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Description
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.union
import com.detailline.callfollowcrm.presentation.util.bottomBarClearance
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
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
    // 통화 구간 — 메시지와 시간순 병합해 타임라인에 통화 카드로 표시 (loadMessages 무손상, 렌더 레이어 병합).
    val callRecords by viewModel.callRecords.collectAsState()
    // 시공접수서 제출 이벤트 — 통화처럼 타임라인에 카드로 병합.
    val intakeEvents by viewModel.intakeEvents.collectAsState()
    // 통화요약 — 통화 카드와 시각으로 짝지어 "AI 요약됨" 상태(불릿+후속문자) 표시.
    val callSummaries by viewModel.callSummaries.collectAsState()
    // 서버에서 요약 중인 통화 시각 — 통화카드가 "요약 중…" 스피너 표시.
    val summarizingTimes by viewModel.summarizingRecordedAt.collectAsState()
    // 자동요약 ON + 녹음폴더 연결이면, 방금 끝난 통화 카드에 미리 "요약 중…"을 띄운다(워커가 ~15~40초 뒤 돌아서
    //   그 전엔 정적 '요약하기' 버튼만 보이던 문제). 요약이 도착하면 그걸로 바뀌고, 안 오면 창이 지나 버튼으로 복귀. (2026-06-20 사장님)
    val autoSummaryActive = remember { viewModel.autoSummaryActive }
    val nowTick = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(20_000); nowTick.value = System.currentTimeMillis() } }
    val timelineItems = remember(messages, callRecords, intakeEvents) {
        buildChatTimeline(messages, callRecords, intakeEvents)
    }
    // 통화 1건 ↔ 요약 1건 1:1 배정 (2026-06-15 버그: 가까운 두 통화가 같은 요약 하나를 공유 표시).
    //   ① callRecordId 로 명시 연결된 요약 먼저  ② 남은 통화엔 ±10분 내 '가장 가까운 미사용' 요약 1개.
    //   firstOrNull(겹침 허용) → 1:1 그리디 매칭으로 교체. 한 요약은 한 통화에만.
    val recordSummary: Map<Long, com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity> =
        remember(callRecords, callSummaries) {
            val win = 10 * 60 * 1000L
            val out = HashMap<Long, com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity>()
            val usedSummaryIds = HashSet<Long>()
            val recById = callRecords.associateBy { it.id }
            for (s in callSummaries) {
                val rid = s.callRecordId ?: continue
                if (recById.containsKey(rid) && !out.containsKey(rid) && s.id !in usedSummaryIds) {
                    out[rid] = s; usedSummaryIds.add(s.id)
                }
            }
            for (rec in callRecords.sortedByDescending { it.endedAt }) {
                if (out.containsKey(rec.id)) continue
                val callStart = rec.startedAt ?: rec.endedAt
                val cand = callSummaries
                    .filter { it.id !in usedSummaryIds && it.recordedAt != null }
                    .filter { val r = it.recordedAt!!; r >= callStart - win && r <= rec.endedAt + win }
                    .minByOrNull { kotlin.math.abs(it.recordedAt!! - callStart) }
                if (cand != null) { out[rec.id] = cand; usedSummaryIds.add(cand.id) }
            }
            out
        }
    // 통화 녹음 — 통화카드 재생 플레이어용. 통화 1건 ↔ 녹음 1건 1:1 배정(요약과 동일 로직: callRecordId 먼저, 없으면 fileName 시각 ±10분).
    val recordings by viewModel.recordings.collectAsState()
    val recordingFor: Map<Long, com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity> =
        remember(callRecords, recordings) {
            val win = 10 * 60 * 1000L
            val out = HashMap<Long, com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity>()
            val used = HashSet<Long>()
            val recById = callRecords.associateBy { it.id }
            for (r in recordings) {
                val rid = r.callRecordId ?: continue
                if (recById.containsKey(rid) && !out.containsKey(rid) && r.id !in used) {
                    out[rid] = r; used.add(r.id)
                }
            }
            for (rec in callRecords.sortedByDescending { it.endedAt }) {
                if (out.containsKey(rec.id)) continue
                val callStart = rec.startedAt ?: rec.endedAt
                val cand = recordings
                    .filter { it.id !in used }
                    .mapNotNull { rr ->
                        val at = com.detailline.callfollowcrm.recording.AdotFilenameParser.parse(rr.fileName)?.recordedAt
                        if (at != null) rr to at else null
                    }
                    .filter { (_, at) -> at >= callStart - win && at <= rec.endedAt + win }
                    .minByOrNull { (_, at) -> kotlin.math.abs(at - callStart) }
                    ?.first
                if (cand != null) { out[rec.id] = cand; used.add(cand.id) }
            }
            out
        }
    val templates by viewModel.templates.collectAsState()
    val pricingItems by viewModel.pricingItems.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val starred by viewModel.starred.collectAsState()
    val polishing by viewModel.aiPolishing.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val suggestion by viewModel.effectiveSuggestions.collectAsState()
    val suggestionsStale by viewModel.suggestionsAreStale.collectAsState()
    val suggestionsLoading by viewModel.suggestionsLoading.collectAsState()
    // 통화로 끝난 대화면 추천 준비 X — "준비 중" 대신 "문자 오면 준비" 안내. (2026-06-17 사장님)
    val endedWithCall by viewModel.lastActivityIsCall.collectAsState()
    // (Phase 2) 원칙 발견 카드 — 추천과 다르게 보냈을 때 막내가 "이게 원칙이에요?" 물음. (2026-06-17 사장님)
    val principleDiscovery by viewModel.principleDiscovery.collectAsState()
    // 별표된 메시지 식별 키 set — ChatBubble 의 isStarred 여부 빠르게 판정
    val starredKeys = remember(starred) {
        starred.map { it.messageDateMs to it.sent }.toHashSet()
    }
    var starredViewerOpen by remember { mutableStateOf(false) }
    // 내 일정 확인 시트 (2026-06-01) — 대화 중 빈 날/시공일 미니 달력으로 확인.
    var myScheduleOpen by remember { mutableStateOf(false) }
    val scheduledJobs by viewModel.scheduledJobs.collectAsState()
    // 말풍선 꾹 누름 → BottomSheet 띄울 메시지 (null = 닫힘).
    //   사장님 결정 2026-05-25: 꾹 누름 = 저장/복사 선택. 직접 토글 X.
    var bubbleActionTarget by remember {
        mutableStateOf<com.detailline.callfollowcrm.data.repository.SmsRepository.SmsMessage?>(null)
    }
    // 대화 요약 카드 사장님 명시 접기 — composer focus 자동 접힘과는 별개.
    //   사장님 피드백 2026-05-25: 카드 4-5줄에 말풍선이 가려져서 접기 필요.
    //   2026-06-02 사장님 결정(프로토 1:1): 평소엔 프로토 chat-summary 한 줄 바(접힘),
    //     누르면 펼쳐서 풍부한 UnifiedSummaryCard. → 기본 접힘으로 시작.
    var summaryManualCollapsed by remember { mutableStateOf(true) }

    // 통화 카드 "에이닷 통화 내용 요약 받기 ↑" → 붙여넣기 다이얼로그.
    var adotPasteOpen by remember { mutableStateOf(false) }

    // Composer 임시저장 (2026-05-27 사장님 통점) — phone 별 in-memory draft 복원.
    //   init = ChatDraftStore.get(phone), 변경 시 자동 save, 전송 후 input="" = 자동 clear (set 이 empty 면 remove).
    var input by remember { mutableStateOf(viewModel.loadDraft()) }
    LaunchedEffect(input) { viewModel.saveDraft(input) }
    // 입력창 커서 위치 — 캘린더/템플릿 등 프로그램적 삽입을 "끝"이 아니라 정확한 위치에 넣기 위함. (2026-06-16 사장님)
    var inputSelection by remember { mutableStateOf(TextRange(input.length)) }
    // 본문을 통째로 바꾸는 프로그램적 채우기(템플릿·다듬기·발송클리어 등)는 커서를 항상 끝으로.
    val setInput: (String) -> Unit = { s -> input = s; inputSelection = TextRange(s.length) }
    var fullscreenImages by remember { mutableStateOf<List<android.net.Uri>?>(null) }
    var fullscreenStart by remember { mutableStateOf(0) }
    // 권한 요청 직후 자동 재시도용 — 입력 본문을 기억.
    var pendingSend by remember { mutableStateOf<String?>(null) }
    // 견적 회신 리마인드 기준 — 견적 빌더로 채운 본문. **실제 발송 성공 시에만** ESTIMATE_SENT 기록
    //   (전엔 composer 채우기만 해도 기록돼 안 보낸 사람이 견적회신에 떴음. 2026-06-06 fix).
    var estimateBody by remember { mutableStateOf<String?>(null) }
    // 사진 첨부 — Photo Picker 로 선택된 URI 들. 발송 시 갤럭시 메시지로 본문+사진 같이 전달.
    var attachedPhotos by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    // 카톡식 자체 사진 피커(아래서 올라오는 바텀시트) 표시 여부. 2026-06-11 사장님 요청.
    var showPhotoPicker by remember { mutableStateOf(false) }
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
    // 견적 만들기 상태 — ChatScreen 보관(미리보기 닫고 와도 선택 유지). (2026-06-08 #5)
    val estimateDraft = remember { EstimateDraft(estMonthAnchor(System.currentTimeMillis())) }
    // 견적서(직인) 미리보기 — null 아니면 QuoteDocScreen 오버레이 표시 (견적 2단계).
    var quoteDocData by remember { mutableStateOf<QuoteDocData?>(null) }
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
    // 프로토 chat-actions [문구 넣기] → 템플릿 picker 시트.
    var tplPickerOpen by remember { mutableStateOf(false) }
    // ▶ 보내기 확인 다이얼로그 — null 이면 안 떠 있음.
    //   사장님이 ▶ 탭하면 (body, photos) 스냅샷 저장 + 다이얼로그 표시. [보내기] 탭해야 진짜 발송.
    var sendConfirm by remember { mutableStateOf<Pair<String, List<android.net.Uri>>?>(null) }

    val pickPhotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) attachedPhotos = attachedPhotos + uris
    }

    // 에이닷 통화요약 txt 폴더(Download/A.phone) 연결 — 한 번만. 연결되면 앱 켤 때마다 자동 import.
    val adotFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val appContainer = (context.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container
            com.detailline.callfollowcrm.recording.AdotTextFolderScanner.connectFolder(context, uri)
            com.detailline.callfollowcrm.recording.AdotTextFolderScanner.scanIfConnected(context, appContainer) { n ->
                android.widget.Toast.makeText(
                    context,
                    if (n > 0) "통화 요약 ${n}개를 가져왔어요"
                    else "폴더 연결됐어요. 이제 통화 녹음 앱에서 통화 내용을 텍스트로 저장하면 자동으로 들어와요.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            adotPasteOpen = false
        }
    }
    // 통화카드 "에이닷 통화 내용 요약 받기 ↑" 탭 동작:
    //   폴더 연결돼 있으면 → 바로 폴더 스캔(새 txt 자동 가져오기). 미연결이면 → 안내/붙여넣기 다이얼로그.
    val requestAdotSummary: () -> Unit = {
        if (com.detailline.callfollowcrm.recording.AdotTextFolderScanner.isConnected(context)) {
            val appContainer = (context.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container
            com.detailline.callfollowcrm.recording.AdotTextFolderScanner.scanIfConnected(context, appContainer) { n ->
                android.widget.Toast.makeText(
                    context,
                    if (n > 0) "통화 요약 ${n}개를 가져왔어요"
                    else "새로 들어온 통화 요약이 없어요. 통화 녹음 앱에서 통화 내용을 텍스트로 저장하면 자동으로 들어와요.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } else {
            adotPasteOpen = true
        }
    }

    // 발송 성공한 본문이 '준비한 견적'과 같으면 그때만 ESTIMATE_SENT 기록(견적 회신 리마인드 기준).
    val markIfEstimate: (String) -> Unit = { sentBody ->
        if (estimateBody != null && sentBody == estimateBody) {
            viewModel.recordEstimateSent(sentBody)
            estimateBody = null
        }
    }

    val sendPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val body = pendingSend
        pendingSend = null
        if (granted && body != null) {
            viewModel.sendMessage(context, body) { ok -> if (ok) { setInput(""); markIfEstimate(body) } }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadMessages()
        viewModel.loadSuggestions()
        viewModel.loadFullSummary()
    }

    // 카톡식 실시간 반영 (2026-06-10 사장님 통점: 고객이 보낸 문자가 채팅에 바로 안 올라옴):
    //   화면이 떠있는 동안 SMS/MMS provider 변화를 관찰 → 새 문자 즉시 reload. (기본 문자앱이 아니어도
    //   READ_SMS 권한이면 삼성 문자앱의 수신 기록 write 에 발동.) 300ms 디바운스로 연속 변화 합침.
    androidx.compose.runtime.DisposableEffect(Unit) {
        val resolver = context.contentResolver
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val reload = Runnable { viewModel.loadMessages() }
        val observer = object : android.database.ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(reload)
                handler.postDelayed(reload, 300)
            }
        }
        runCatching {
            resolver.registerContentObserver(android.net.Uri.parse("content://sms"), true, observer)
            resolver.registerContentObserver(android.net.Uri.parse("content://mms"), true, observer)
        }
        onDispose {
            handler.removeCallbacks(reload)
            runCatching { resolver.unregisterContentObserver(observer) }
        }
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
                        setInput("")
                        attachedPhotos = emptyList()
                        markIfEstimate(body)
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
                            setInput("")
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
                viewModel.sendMessage(context, body) { ok -> if (ok) { setInput(""); markIfEstimate(body) } }
            }
        }
    }

    Scaffold(
        containerColor = TossGrayBg,
        // contentWindowInsets = ime ∪ navigationBars:
        //   키보드 뜨면 ime 만큼(composer 가 키보드 위), 키보드 내려가면 navbar 만큼 하단 여백 확보.
        //   예전엔 ime 단독 → 갤S23U 등에서 제스처/3버튼 내비바(홈버튼)가 입력창을 가림.
        //   (S9 는 액티비티창 navbar inset 0 라 영향 없음 → S9 유지, S23U 해결.) (2026-06-22 사장님)
        contentWindowInsets = WindowInsets.ime.union(WindowInsets.navigationBars),
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
                    // 프로토 s-chat 앱바 = ← / 이름 / 📞 / ⓘ 만. (접수서·북마크는 프로토에 없어 제거 2026-06-03)
                    //   접수서 링크는 향후 견적 작성기 3모드(문자견적/시공접수서/견적서)로 이동 예정.
                    //   저장된 메시지(북마크) 기능 코드는 남겨둠(starredViewerOpen) — 앱바 진입만 제거.
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
            // 2026-05-30 사장님 #8 통점 fix:
            //   sentinel 처리 — ConversationAiRepository 가 빈 응답이면 cache 에 빈 sentinel ("/[]/{}") 저장.
            //   aiSummary != null + 본문 모두 비어있음 = "시도했으나 응답 없음" → placeholder X + UnifiedSummaryCard X.
            //   기존엔 aiSummary 영영 null → SummaryLoadingPlaceholder 무한 표시 (114 광고 메시지).
            val hasEnoughForSummary = messages.size >= 2
            val isEmptySentinel = aiSummary?.let {
                com.detailline.callfollowcrm.ai.parseConversationLines(it.conversationSummaryJson).isEmpty() &&
                    it.cardSummary.isNullOrBlank() &&
                    NextAction.parse(it.nextActionJson) == null
            } ?: false
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
            // 2026-05-30 #8 통점 fix: 빈 sentinel = 표시 X (114 같은 광고 메시지에서 빈 카드 방지).
            aiSummary?.takeUnless { isEmptySentinel }?.let { s ->
                // 2026-05-30 사장님 #3 통점 — 시공일정 등록된 고객 = "상황 종료" → NextActionBox 표시 X.
                //   사장님 결정: 일정 잡혔으니 다음 액션 권유 불필요.
                // 2026-06-06 사장님 통점 — 잔금 입금까지 끝났는데 "일정 답장하기" 가 계속 뜨던 버그.
                //   상황 종료 판정 확장: ① 시공일 등록  ② 잔금 입금 체크(balancePaidAt)
                //   ③ 고객이 "입금/잔금/완납/송금 했다" 는 문자를 보냄(본문 감지). 셋 중 하나면 액션 숨김.
                val balancePaidByMsg = remember(messages) {
                    fun paidLike(b: String): Boolean {
                        val done = b.contains("했") || b.contains("완료") || b.contains("됐") ||
                            b.contains("보냈") || b.contains("드렸") || b.contains("이체")
                        return b.contains("완납") || ((b.contains("입금") || b.contains("잔금") || b.contains("송금")) && done)
                    }
                    messages.any { !it.sent && paidLike(it.body) }
                }
                val settled = (customer?.scheduledWorkDate ?: 0L) > 0L ||
                    customer?.balancePaidAt != null ||
                    balancePaidByMsg
                val action = remember(s.nextActionJson, settled) {
                    if (settled) null else NextAction.parse(s.nextActionJson)
                }
                val onActionHandler: (NextAction) -> Unit = { a -> triggerActionByType(a.actionType) }
                val showCollapsed = composerFocused || summaryManualCollapsed
                val isSummaryRefreshing by viewModel.isSummaryRefreshing.collectAsState()
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                if (showCollapsed) {
                    val collapsedLines = com.detailline.callfollowcrm.ai.parseConversationLines(s.conversationSummaryJson)
                    CollapsedSummaryHeader(
                        summaryLine = s.cardSummary?.takeIf { it.isNotBlank() } ?: collapsedLines.firstOrNull().orEmpty(),
                        summaryLineCount = collapsedLines.size,
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
            LaunchedEffect(timelineItems.size) {
                if (timelineItems.isNotEmpty()) {
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
                if (timelineItems.isEmpty()) {
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
                    // key 안 줌 — SMS/MMS id 가 별도 테이블, 통화 id 도 별도라 충돌 위험. 인덱스 기반 렌더.
                    //   프로토 chat-date — 날짜 경계마다 회색 알약 구분선 삽입.
                    val renderRows = withDateDividers(timelineItems)
                    items(renderRows) { ti ->
                        when (ti) {
                            is ChatTimelineItem.Msg -> {
                                val msg = ti.message
                                ChatBubble(
                                    body = msg.body,
                                    timeMs = msg.dateMs,
                                    sent = msg.sent,
                                    imageUris = msg.imageUris,
                                    isStarred = starredKeys.contains(msg.dateMs to msg.sent),
                                    onImageTap = { uris, idx -> fullscreenImages = uris; fullscreenStart = idx },
                                    onLongPress = {
                                        // 2026-05-25: 직접 toggleStar 호출 X → BottomSheet 띄워서 저장/복사 선택.
                                        bubbleActionTarget = msg
                                    }
                                )
                            }
                            is ChatTimelineItem.Call -> {
                                // 통화↔요약 1:1 배정 결과(recordSummary)에서 이 통화의 요약을 가져온다.
                                //   (이전엔 firstOrNull 로 ±10분 겹침 매칭 → 가까운 두 통화가 같은 요약 공유 표시 버그.)
                                val win = 10 * 60 * 1000L
                                val callStart = ti.record.startedAt ?: ti.record.endedAt
                                val matched = recordSummary[ti.record.id]
                                // 서버 요약 진행 중인 녹음이 이 통화 시간대와 겹치면 스피너.
                                //   + 방금 끝난(≤4분) 요약 가능한 통화는 자동요약 워커가 곧 도므로 미리 스피너(정적 '요약하기' 대신). (2026-06-20 사장님)
                                val recType = runCatching { com.detailline.callfollowcrm.domain.model.CallType.valueOf(ti.record.callType) }.getOrNull()
                                val recSummarizable = recType != com.detailline.callfollowcrm.domain.model.CallType.MISSED &&
                                    recType != com.detailline.callfollowcrm.domain.model.CallType.REJECTED &&
                                    ti.record.duration > 0
                                val autoPending = autoSummaryActive && recSummarizable &&
                                    (nowTick.value - ti.record.endedAt) in 0..(4 * 60 * 1000L)
                                val summarizing = matched == null && (autoPending || summarizingTimes.any { r ->
                                    r >= callStart - win && r <= ti.record.endedAt + win
                                })
                                val callRec = recordingFor[ti.record.id]
                                CallSegment(
                                    record = ti.record,
                                    summary = matched,
                                    isSummarizing = summarizing,
                                    audioUri = callRec?.fileUri,
                                    audioDurationMs = callRec?.duration,
                                    onUseAsDraft = { msg ->
                                        setInput(if (input.isBlank()) msg else input + "\n" + msg)
                                    },
                                    onSummarizeCall = { viewModel.summarizeCall(ti.record, context) }
                                )
                            }
                            is ChatTimelineItem.Intake -> IntakeSegment(ti.event)
                            is ChatTimelineItem.DateDivider -> ChatDateDivider(chatDateLabel(ti.dayStart))
                        }
                    }
                }
            }

            // 템플릿 알약 (가로 스크롤) — 탭하면 입력칸에 본문 채워짐. 즉시 전송 안 함.
            // 2026-05-24 시각 충돌 fix: 답변 추천 영역이 노출될 때 = 템플릿 알약 숨김.
            //   답변 추천 칩 / 템플릿 알약 둘 다 둥근 칩이라 인접 시 사장님 시선 혼란.
            //   답변 추천 = 사장님 톤 학습 기반 우선. 답변 추천 없을 때만 (서버 X 또는 stale) 템플릿 노출.
            //
            // 프로토 chat-actions — 항상 보이는 고정 3칩 [견적 작성][내 일정 확인][문구 넣기].
            //   (사장님 2026-06-02 결정: 무조건 프로토 1:1 → ⚡토글/템플릿 인라인 제거)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActChip(Icons.Default.Description, "견적 작성") { triggerActionByType("send_estimate") }
                ActChip(Icons.Default.DateRange, "내 일정 확인") { myScheduleOpen = true }
                ActChip(Icons.AutoMirrored.Filled.Chat, "문구 넣기") { tplPickerOpen = true }
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
                    endedWithCall = endedWithCall,
                    expanded = suggestionsExpanded,
                    isStale = suggestionsStale,
                    onToggleExpand = { suggestionsExpanded = !suggestionsExpanded },
                    onPickChoice = { picked ->
                        // 2026-05-29 킬러콘텐츠 3단계 — chip 탭 시그널 capture.
                        //   ViewModel 이 picked snapshot 보관 → send 후 SENT_AS_IS/EDITED/REFINED 판정.
                        viewModel.onSuggestionTapped(picked)
                        setInput(picked.text)
                        // 답변 추천 사용 직후 = 자동 접힘 (사장님이 보낼 본문에 집중)
                        suggestionsExpanded = false
                    },
                    onRegenerate = { viewModel.regenerateSuggestions() }
                )
            }

            // (Phase 2) 원칙 발견 카드 — 추천과 다르게 보냈을 때 막내가 "이게 원칙이에요?" ⭕/❌/나중에. (2026-06-17 사장님)
            principleDiscovery?.let { disc ->
                PrincipleDiscoveryCard(
                    discovery = disc,
                    onAccept = { viewModel.acceptPrinciple() },
                    onReject = { viewModel.rejectPrinciple() },
                    onLater = { viewModel.laterPrinciple() },
                    onDismiss = { viewModel.clearPrincipleDiscovery() }
                )
            }

            // composer pill — 인스타 DM 스타일 ([✨][📷][입력][▶]) + 사진 첨부 미리보기
            Composer(
                input = input,
                selection = inputSelection,
                onChange = { input = it },
                onSelectionChange = { inputSelection = it },
                isPolishing = polishing,
                onAiPolish = {
                    viewModel.aiPolish(input) { polished -> setInput(polished) }
                },
                onAttachPhoto = { showPhotoPicker = true },
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
                    .bottomBarClearance()  // 시스템 하단 내비바 가림 방지(M3 시트 인셋 0 버그 우회). 2026-06-11
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

    // 내 일정 확인 시트 (2026-06-01) — 대화 중 시공 일정 미니 달력.
    if (myScheduleOpen) {
        MyScheduleSheet(
            jobs = scheduledJobs,
            onDismiss = { myScheduleOpen = false },
            onPickDate = { dateText ->
                // 선택한 날짜를 "커서 위치"에 넣는다 (끝이 아니라). 사장님이 문장 중간에 끼워넣을 수 있게. (2026-06-16)
                val start = inputSelection.start.coerceIn(0, input.length)
                val end = inputSelection.end.coerceIn(0, input.length)
                val before = input.substring(0, start)
                val after = input.substring(end)
                // 앞/뒤 글자가 공백이 아니면 한 칸씩 띄워 단어가 붙지 않게(중간 삽입 대비).
                val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
                val trail = if (after.isNotEmpty() && !after.first().isWhitespace()) " " else ""
                val piece = lead + dateText + trail
                input = before + piece + after
                inputSelection = TextRange(start + piece.length)  // 커서를 넣은 날짜(+뒤 공백) 다음으로.
                myScheduleOpen = false
            }
        )
    }

    // "이미 처리된 통화내용입니다. 다시 요약?" 프롬프트 (녹음 재공유 / 서버 캐시 응답 시).
    //   백그라운드 요약기가 CallSummaryReprompt.ask 로 켜고, 이 채팅 번호와 맞으면 예/아니오를 띄움.
    val reprompt by com.detailline.callfollowcrm.recording.CallSummaryReprompt.pending.collectAsState()
    reprompt?.let { p ->
        val mySuffix = viewModel.phoneNumber.filter { it.isDigit() }.takeLast(8)
        if (p.phone.filter { it.isDigit() }.takeLast(8) == mySuffix) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { com.detailline.callfollowcrm.recording.CallSummaryReprompt.answer(false) },
                title = { Text("이미 처리된 통화내용입니다") },
                text = { Text("다시 요약해드릴까요?") },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { com.detailline.callfollowcrm.recording.CallSummaryReprompt.answer(true) }
                    ) { Text("다시 요약", fontWeight = FontWeight.Bold, color = TossBlue) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { com.detailline.callfollowcrm.recording.CallSummaryReprompt.answer(false) }
                    ) { Text("아니오", color = TossTextSecondary) }
                }
            )
        }
    }

    // 프로토 [문구 넣기] → 템플릿 picker (기존 TemplatePickerDialog 재사용, 전체 카테고리).
    if (tplPickerOpen) {
        TemplatePickerDialog(
            category = "",
            templates = templates,
            onPick = { tpl -> setInput(if (input.isBlank()) tpl.body else input + "\n" + tpl.body); tplPickerOpen = false },
            onDelete = { id -> viewModel.deleteTemplate(id) },
            onSaveCurrent = { viewModel.saveTextAsTemplate(input) },
            canSaveCurrent = input.isNotBlank(),
            onDismiss = { tplPickerOpen = false }
        )
    }

    // 에이닷 통화 요약 붙여넣기 (통화 카드 "요약 받기 ↑" → 에이닷에서 복사한 요약을 붙여넣어 저장).
    if (adotPasteOpen) {
        val pasteCtx = LocalContext.current
        val clip = androidx.compose.ui.platform.LocalClipboardManager.current
        var pasteText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { adotPasteOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp)) {
                    com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                    Text("통화 내용 요약 받기", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "한 번만 폴더를 연결하면, 이후엔 통화 내용을 텍스트로 저장할 때마다 자동으로 들어와요.",
                        fontSize = 12.5.sp, color = TossTextSecondary, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    // 자동 받기 (권장) — Download/A.phone 폴더 한 번 연결.
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossBlue)
                            .clickable {
                                val initialUri = android.net.Uri.parse(
                                    "content://com.android.externalstorage.documents/document/primary%3ADownload%2FA.phone"
                                )
                                runCatching { adotFolderLauncher.launch(initialUri) }
                                    .onFailure { runCatching { adotFolderLauncher.launch(null) } }
                            }.padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("📁 자동으로 받기 — 폴더 한 번만 연결", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "열린 폴더에서 그대로 ‘이 폴더 사용’을 눌러주세요.",
                        fontSize = 11.sp, color = TossTextTertiary, lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("또는 직접 붙여넣기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary)
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = pasteText, onValueChange = { pasteText = it },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TossTextPrimary),
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp))
                            .background(TossGrayBg).padding(12.dp),
                        decorationBox = { inner ->
                            if (pasteText.isEmpty()) Text("여기에 통화 요약을 붙여넣기…", color = TossTextTertiary, fontSize = 13.sp)
                            inner()
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(TossGrayBg)
                            .clickable { clip.getText()?.let { pasteText = it.text } }.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) { Text("📋 클립보드에서 붙여넣기", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                                .clickable { adotPasteOpen = false }.padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("취소", color = TossTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossBlue)
                                .clickable {
                                    val container = (pasteCtx.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container
                                    val cid = customer?.id
                                    if (cid != null) com.detailline.callfollowcrm.recording.AdotSummaryImporter.importPasted(pasteCtx, container, pasteText, cid)
                                    else com.detailline.callfollowcrm.recording.AdotSummaryImporter.importFromShare(pasteCtx, container, pasteText)
                                    adotPasteOpen = false
                                }.padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("저장", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    // 카톡식 자체 사진 피커 — 아래서 올라오는 바텀시트 갤러리. "파일에서"는 기존 시스템 피커로 fallback.
    if (showPhotoPicker) {
        com.detailline.callfollowcrm.presentation.component.PhotoPickerSheet(
            maxSelectable = (5 - attachedPhotos.size).coerceAtLeast(0),
            onConfirm = { uris ->
                attachedPhotos = (attachedPhotos + uris).distinct().take(5)
                showPhotoPicker = false
            },
            onDismiss = { showPhotoPicker = false },
            onOpenFiles = {
                showPhotoPicker = false
                pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
    }

    // 풀스크린 이미지 뷰어 — 한 번에 온 사진 전부 좌우 스와이프 + 핀치/더블탭 줌. (2026-06-16 사장님)
    fullscreenImages?.let { imgs ->
        if (imgs.isNotEmpty()) Dialog(
            onDismissRequest = { fullscreenImages = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val pagerState = rememberPagerState(
                initialPage = fullscreenStart.coerceIn(0, imgs.size - 1)
            ) { imgs.size }
            var curZoomed by remember { mutableStateOf(false) }
            LaunchedEffect(pagerState.currentPage) { curZoomed = false }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !curZoomed,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    ZoomableAsyncImage(
                        uri = imgs[page],
                        isCurrentPage = page == pagerState.currentPage,
                        onZoomedChange = { z -> if (page == pagerState.currentPage) curZoomed = z },
                        onTap = { fullscreenImages = null }
                    )
                }
                IconButton(
                    onClick = { fullscreenImages = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, "닫기", tint = Color.White)
                }
                // 사진 저장(다운로드) — 지금 보고 있는 사진을 휴대폰 갤러리에 저장. (2026-06-23 사장님)
                IconButton(
                    onClick = {
                        imgs.getOrNull(pagerState.currentPage)?.let { uri ->
                            scope.launch {
                                val ok = saveImageToGallery(context, uri)
                                android.widget.Toast.makeText(
                                    context,
                                    if (ok) "사진을 저장했어요 (갤러리)" else "저장하지 못했어요",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                ) {
                    Icon(Icons.Default.FileDownload, "사진 저장", tint = Color.White)
                }
                if (imgs.size > 1) {
                    Text(
                        "${pagerState.currentPage + 1} / ${imgs.size}",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
                            .clip(RoundedCornerShape(999.dp)).background(Color(0x66000000))
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    )
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
                setInput(if (ms != null) prependScheduleNote(tpl.body, ms) else tpl.body)
                depositPrefillScheduledMs = null
                templatePickerCategory = null
            },
            onDelete = { id -> viewModel.deleteTemplate(id) },
            onSaveCurrent = { viewModel.saveTextAsTemplate(input) },
            canSaveCurrent = input.isNotBlank(),
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
            setInput(if (cleaned.isBlank()) proposal else "$cleaned\n$proposal")
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
        val estCtx = androidx.compose.ui.platform.LocalContext.current
        val estPrefs = remember {
            (estCtx.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container.preferences
        }
        // 미리보기로 갈 때만 sheet 닫고 draft 유지. 발송/취소 등 '종료' 시에만 draft 초기화. (2026-06-08 #5)
        fun resetEstimateDraft() = estimateDraft.reset(estMonthAnchor(System.currentTimeMillis()))
        EstimateBuilderDialog(
            items = pricingItems,
            draft = estimateDraft,
            bizName = estPrefs.bizName,
            bizOwner = estPrefs.bizOwner,
            bizNo = estPrefs.bizNo,
            bizPhone = estPrefs.bizPhone,
            validDays = estPrefs.bizQuoteValidDays,
            onConfirm = { body ->
                setInput(body)
                // composer 채우기만 — 아직 발송 아님. 실제 발송 성공 시 markIfEstimate 가 기록.
                estimateBody = body
                showEstimateBuilder = false
                resetEstimateDraft()
            },
            onShare = { body ->
                runCatching {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, body)
                    estCtx.startActivity(android.content.Intent.createChooser(send, "견적서 공유"))
                }
                viewModel.recordEstimateSent(body)
                showEstimateBuilder = false
                resetEstimateDraft()
            },
            onQuoteDoc = { data ->
                // 미리보기로 — sheet 만 닫고 draft 는 유지(닫기 시 복귀 위해).
                quoteDocData = data
                showEstimateBuilder = false
            },
            onIssueIntake = { issItems, total, y, mo, d, days, dm, dv ->
                viewModel.issueQuoteIntake(issItems, total, y, mo, d, days, dm, dv) { result ->
                    result.onSuccess { draftLink ->
                        setInput(draftLink)
                        showEstimateBuilder = false
                        resetEstimateDraft()
                        android.widget.Toast.makeText(estCtx, "접수서 링크를 문자에 넣었어요 · ▶ 눌러 보내세요", android.widget.Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        android.widget.Toast.makeText(estCtx, "서버 연결 실패 — 잠시 후 다시 시도해주세요", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showEstimateBuilder = false; resetEstimateDraft() }
        )
    }

    // 견적서(직인) 미리보기 오버레이.
    quoteDocData?.let { data ->
        // '미리보기 닫기' → 채팅이 아니라 견적 편집기로 복귀(선택 유지). (2026-06-08 #5)
        QuoteDocScreen(data = data, onClose = { quoteDocData = null; showEstimateBuilder = true })
    }
}

/** 통화 구간 카드 색 — 문자(파랑)와 구분되는 청록. 프로토 .chat-call (#0E9E90/#EAF4F1). */
private val CallTeal = Color(0xFF0E9E90)
private val CallTealSoft = Color(0xFFEAF4F1)

/**
 * 채팅 타임라인 한 항목 — 문자(Msg) 또는 통화(Call). 2026-06-01.
 *   reverseLayout LazyColumn 에 시간 DESC 로 넘김. timeMs 기준 정렬.
 */
sealed interface ChatTimelineItem {
    val timeMs: Long
    data class Msg(val message: com.detailline.callfollowcrm.data.repository.SmsRepository.SmsMessage) : ChatTimelineItem {
        override val timeMs: Long get() = message.dateMs
    }
    data class Call(val record: com.detailline.callfollowcrm.data.local.entity.CallRecordEntity) : ChatTimelineItem {
        override val timeMs: Long get() = record.endedAt
    }
    /** 시공접수서 제출 이벤트 (2026-06-05) — 고객 작성 완료를 타임라인에 카드로. */
    data class Intake(val event: com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity) : ChatTimelineItem {
        override val timeMs: Long get() = event.submittedAtMs
    }
    /** 프로토 chat-date — 날짜 경계 구분선. 렌더 직전에만 끼워넣음(buildChatTimeline 은 생성 X). */
    data class DateDivider(val dayStart: Long) : ChatTimelineItem {
        override val timeMs: Long get() = dayStart
    }
}

/**
 * 타임라인(시간 DESC)에 날짜 경계 구분선을 끼워넣은 렌더 행 목록.
 *   reverseLayout=true 라 list[0]=화면 맨 아래(최신). 오름차순으로 "날짜 바뀌면 그 위에 구분선" 을
 *   넣은 뒤 다시 뒤집어 DESC 로 돌려준다 → 각 날짜 그룹 위에 구분선이 뜸.
 */
private fun withDateDividers(items: List<ChatTimelineItem>): List<ChatTimelineItem> {
    if (items.isEmpty()) return items
    val asc = items.asReversed() // 오래된→최신
    val out = ArrayList<ChatTimelineItem>(asc.size + 8)
    var lastDay = Long.MIN_VALUE
    for (ti in asc) {
        val day = DateTimeUtils.startOfDay(ti.timeMs)
        if (day != lastDay) {
            out.add(ChatTimelineItem.DateDivider(day))
            lastDay = day
        }
        out.add(ti)
    }
    return out.asReversed() // 다시 DESC
}

/** 프로토 chat-date 라벨 — 오늘/어제/그 외 "M월 D일 (요일)". */
private fun chatDateLabel(dayStart: Long): String {
    val today = DateTimeUtils.startOfDay(System.currentTimeMillis())
    return when (((today - dayStart) / DateTimeUtils.DAY_MS).toInt()) {
        0 -> "오늘"
        1 -> "어제"
        else -> java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREAN).format(java.util.Date(dayStart))
    }
}

/**
 * 문자 + 통화를 시간 DESC 로 병합. 메시지 로딩(loadMessages) 은 그대로 두고 렌더 직전에만 합침.
 *   같은 시각이면 통화를 먼저(아래쪽=reverseLayout) — 통화 후 문자 흐름이 자연스럽게 보이도록.
 */
private fun buildChatTimeline(
    messages: List<com.detailline.callfollowcrm.data.repository.SmsRepository.SmsMessage>,
    calls: List<com.detailline.callfollowcrm.data.local.entity.CallRecordEntity>,
    intakeEvents: List<com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity>
): List<ChatTimelineItem> {
    if (calls.isEmpty() && intakeEvents.isEmpty()) return messages.map { ChatTimelineItem.Msg(it) }
    val merged = ArrayList<ChatTimelineItem>(messages.size + calls.size + intakeEvents.size)
    messages.forEach { merged += ChatTimelineItem.Msg(it) }
    calls.forEach { merged += ChatTimelineItem.Call(it) }
    intakeEvents.forEach { merged += ChatTimelineItem.Intake(it) }
    return merged.sortedWith(compareByDescending<ChatTimelineItem> { it.timeMs }.thenBy { it is ChatTimelineItem.Msg })
}

/** 프로토 .chat-date — 가운데 회색 알약 날짜 구분선. */
@Composable
private fun ChatDateDivider(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = TossTextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(TossTextPrimary.copy(alpha = 0.06f))
                .padding(horizontal = 13.dp, vertical = 5.dp)
        )
    }
}

/**
 * 채팅 안 통화 구간 카드 (2026-06-01) — 프로토 .chat-call 벤치마킹.
 *   가운데 정렬 청록 카드: [📞] 통화 유형 + 길이 + 시각. 문자 말풍선과 시각적으로 구분.
 *   요약 가져오기(에이닷/서버)는 별개 단계 — 여기선 통화 발생 자체를 타임라인에 표시.
 */
@Composable
private fun CallSegment(
    record: com.detailline.callfollowcrm.data.local.entity.CallRecordEntity,
    summary: com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity? = null,
    isSummarizing: Boolean = false,
    audioUri: String? = null,
    audioDurationMs: Long? = null,
    onUseAsDraft: (String) -> Unit = {},
    onSummarizeCall: () -> Unit = {}
) {
    val type = runCatching {
        com.detailline.callfollowcrm.domain.model.CallType.valueOf(record.callType)
    }.getOrNull()
    val (label, accent) = when (type) {
        com.detailline.callfollowcrm.domain.model.CallType.INCOMING -> "수신 통화" to Color(0xFF0E9E90)
        com.detailline.callfollowcrm.domain.model.CallType.OUTGOING -> "발신 통화" to Color(0xFF0E9E90)
        com.detailline.callfollowcrm.domain.model.CallType.MISSED -> "부재중 전화" to TossError
        com.detailline.callfollowcrm.domain.model.CallType.REJECTED -> "거절한 전화" to TossTextSecondary
        com.detailline.callfollowcrm.domain.model.CallType.MANUAL -> "수동 기록 통화" to Color(0xFF0E9E90)
        else -> "통화" to Color(0xFF0E9E90)
    }
    val durLabel = formatCallDuration(record.duration)
    // 프로토 .chat-call — 전체폭 teal 카드 + cc-ch(아이콘·유형·시각) + 에이닷 요약 버튼.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFEAF4F1))
            .border(1.dp, Color(0xFFCDE8E0), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildString { append(label); if (durLabel != null) { append(" · "); append(durLabel) } },
                    fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0A7D72)
                )
                Text(
                    // 프로토 callCardHtml: 요약되면 "· AI 요약됨", 아니면 "· 문자하다 통화함".
                    DateTimeUtils.formatShort(record.endedAt) + if (summary != null) " · AI 요약됨" else " · 문자하다 통화함",
                    fontSize = 11.sp, color = TossTextTertiary, fontWeight = FontWeight.Bold
                )
            }
        }

        // 프로토 callCardHtml(m,i): m.summarized 여부로 분기.
        val bullets = summary?.summaryText
            ?.split("\n")
            ?.map { it.trim().trimStart('•', '·', '-', '*', ' ') }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        // 부재중·거절·통화시간 0초는 요약할 내용(녹음/대화)이 없음 → 요약 버튼 숨김.
        val summarizable = type != com.detailline.callfollowcrm.domain.model.CallType.MISSED &&
            type != com.detailline.callfollowcrm.domain.model.CallType.REJECTED &&
            record.duration > 0

        if (bullets.isEmpty()) {
            when {
                // 서버에서 받아쓰기+요약 중 (녹음 공유 직후 ~10~30초) → 스피너.
                isSummarizing -> {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFDFF1ED)).padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = Color(0xFF0E9E90), strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("통화 내용 요약 중…", color = Color(0xFF0A7D72), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                // 미요약 + 요약 가능한 통화 → 탭하면 연결된 녹음 폴더에서 찾아 바로 요약(에이닷 공유 불필요). (2026-06-14 사장님)
                summarizable -> {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color.White).border(1.dp, Color(0xFFBCE0D8), RoundedCornerShape(10.dp))
                            .clickable { onSummarizeCall() }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✨ 이 통화 요약하기", color = Color(0xFF0A7D72), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        } else {
            // 요약됨 → cc-bul(불릿) + ghost 버튼 "이 통화 내용으로 후속 문자 쓰기"
            Column(Modifier.padding(top = 10.dp)) {
                bullets.forEach { line ->
                    Row(Modifier.padding(bottom = 4.dp)) {
                        Text("• ", fontSize = 12.5.sp, color = Color(0xFF0A7D72), fontWeight = FontWeight.Bold)
                        Text(line, fontSize = 12.5.sp, color = TossTextSecondary, lineHeight = 19.sp)
                    }
                }
            }
            val draft = summary?.recommendedMessage?.takeIf { it.isNotBlank() }
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color.White).border(1.dp, Color(0xFFBCE0D8), RoundedCornerShape(10.dp))
                    .clickable { if (draft != null) onUseAsDraft(draft) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("이 통화 내용으로 후속 문자 쓰기", color = Color(0xFF0A7D72), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        // 녹음 재생 플레이어 — 이 통화의 녹음 파일이 있으면 카드 맨 아래에 표시(에이닷 안 들어가고 바로 듣기). (2026-06-16 사장님)
        if (audioUri != null) {
            CallRecordingPlayer(audioUri = audioUri, durationHintMs = audioDurationMs)
        }
    }
}

/**
 * 통화 녹음 재생 플레이어 (2026-06-16 사장님) — 에이닷 안 들어가고 통화카드에서 바로 듣기.
 *   ▶/⏸ · 진행 슬라이더(드래그 탐색) · 0:00/총길이 · ±5초 · 배속(1.0→1.5→2.0). MediaPlayer 백엔드.
 *   재생 누를 때 lazy 로 준비(prepareAsync), 카드 사라지면 release. content:// (SAF 녹음 폴더) 재생.
 */
@Composable
private fun CallRecordingPlayer(audioUri: String, durationHintMs: Long? = null) {
    val context = LocalContext.current
    var player by remember(audioUri) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var prepared by remember(audioUri) { mutableStateOf(false) }
    var loading by remember(audioUri) { mutableStateOf(false) }
    var isPlaying by remember(audioUri) { mutableStateOf(false) }
    var pendingPlay by remember(audioUri) { mutableStateOf(false) }
    var durationMs by remember(audioUri) { mutableStateOf((durationHintMs ?: 0L).toInt()) }
    var positionMs by remember(audioUri) { mutableStateOf(0) }
    var dragMs by remember(audioUri) { mutableStateOf<Int?>(null) }
    var speed by remember(audioUri) { mutableStateOf(1.0f) }
    var error by remember(audioUri) { mutableStateOf(false) }

    fun applySpeed(p: android.media.MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
    }
    fun create() {
        loading = true
        runCatching {
            android.media.MediaPlayer().apply {
                setDataSource(context, android.net.Uri.parse(audioUri))
                setOnPreparedListener { mp ->
                    prepared = true; loading = false; durationMs = mp.duration
                    if (pendingPlay) {
                        applySpeed(mp); runCatching { mp.start() }
                        isPlaying = mp.isPlaying; pendingPlay = false
                    }
                }
                setOnCompletionListener { isPlaying = false; positionMs = durationMs }
                setOnErrorListener { _, _, _ -> error = true; loading = false; isPlaying = false; true }
                prepareAsync()
            }.also { player = it }
        }.onFailure { error = true; loading = false }
    }
    fun togglePlay() {
        val p = player
        when {
            error -> {}
            p == null -> { pendingPlay = true; create() }
            !prepared -> { pendingPlay = true }
            p.isPlaying -> { p.pause(); isPlaying = false }
            else -> {
                if (durationMs in 1..positionMs) { runCatching { p.seekTo(0) }; positionMs = 0 }
                applySpeed(p); runCatching { p.start() }; isPlaying = p.isPlaying
            }
        }
    }
    fun setFast(on: Boolean) {
        speed = if (on) 1.5f else 1.0f
        val p = player
        if (p != null && p.isPlaying) applySpeed(p)
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val p = player
            if (p != null && dragMs == null) runCatching { positionMs = p.currentPosition }
            kotlinx.coroutines.delay(250)
        }
    }
    androidx.compose.runtime.DisposableEffect(audioUri) {
        onDispose { runCatching { player?.release() }; player = null }
    }

    val shownPos = dragMs ?: positionMs
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp))
            .background(Color.White).border(1.dp, Color(0xFFCDE8E0), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (error) {
            Text("녹음을 재생할 수 없어요", color = TossTextTertiary, fontSize = 12.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(Color(0xFF0E9E90))
                        .clickable { togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    else Text(if (isPlaying) "⏸" else "▶", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                androidx.compose.material3.Slider(
                    value = if (durationMs > 0) (shownPos.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                    onValueChange = { frac -> dragMs = (frac * durationMs).toInt() },
                    onValueChangeFinished = {
                        val d = dragMs
                        if (d != null) { runCatching { player?.seekTo(d) }; positionMs = d }
                        dragMs = null
                    },
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Color(0xFF0E9E90),
                        activeTrackColor = Color(0xFF0E9E90)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("${playerClock(shownPos)} / ${playerClock(durationMs)}",
                    fontSize = 11.sp, color = TossTextTertiary, fontWeight = FontWeight.Bold)
            }
            // 5초 점프 대신 1.5배속 빨리듣기 토글 (2026-06-16 사장님: 통화는 빨리듣기가 더 유용).
            val fast = speed >= 1.5f
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (fast) Color(0xFF0E9E90) else Color(0xFFEAF4F1))
                        .clickable { setFast(!fast) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (fast) "⚡ 1.5배속으로 빨리 듣는 중" else "⚡ 1.5배속으로 빨리 듣기",
                        fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (fast) Color.White else Color(0xFF0A7D72)
                    )
                }
            }
        }
    }
}

private fun playerClock(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/**
 * 풀스크린 사진 한 장 — 핀치 줌(1~5배) + 줌 상태에서 이동(pan) + 더블탭 줌/복원, 단일탭 닫기. (2026-06-16 사장님)
 *   줌 안 된 1배 상태의 한 손가락 드래그는 소비하지 않아 HorizontalPager 가 페이지 넘김을 받는다.
 *   (멀티터치=핀치이거나 이미 줌된 상태일 때만 제스처 소비 → 스와이프와 줌이 충돌하지 않음.)
 */
@Composable
private fun ZoomableAsyncImage(
    uri: android.net.Uri,
    isCurrentPage: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onTap: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 다른 페이지로 넘어가면 줌/이동 리셋.
    LaunchedEffect(isCurrentPage) { if (!isCurrentPage) { scale = 1f; offset = Offset.Zero } }
    LaunchedEffect(scale) { onZoomedChange(scale > 1.02f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } >= 2
                        if (scale > 1.02f || multiTouch) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            offset = if (newScale > 1f) offset + pan else Offset.Zero
                            event.changes.forEach { it.consume() }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (scale <= 1.02f) onTap() },
                    onDoubleTap = {
                        if (scale > 1.02f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "사진",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
        )
    }
}

/**
 * 채팅 안 시공접수서 제출 이벤트 카드 (2026-06-05) — 고객이 접수서를 작성 완료한 사실을 타임라인에 표시.
 *   통화 카드(CallSegment)와 같은 전체폭 이벤트 카드 형태(파란 accent). 문자 말풍선과 구분.
 *   내용: 📋 접수서 작성 완료 + 제출 시각, 그리고 (있으면) 📅 시공일 · 💰 만원 · 📍 주소.
 *   프로토 team-alert quote 의 카피(시공일·금액·주소 묶음)를 채팅 이벤트로 옮김.
 */
@Composable
private fun IntakeSegment(
    event: com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TossBlueSoft)
            .border(1.dp, TossBlue.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(TossBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Assignment, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "📋 접수서 작성을 완료했어요",
                    fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = TossBlueDark
                )
                Text(
                    DateTimeUtils.formatShort(event.submittedAtMs) + " · 고객이 직접 작성",
                    fontSize = 11.sp, color = TossTextTertiary, fontWeight = FontWeight.Bold
                )
            }
        }
        // 접수 내용 요약 — 프로토 team-alert quote 의 시공일·금액·주소 묶음.
        val detailLines = buildList {
            val sched = buildString {
                event.dateLabel?.let { append("📅 시공일 $it") }
                event.totalManwon?.let {
                    if (isNotEmpty()) append("  ·  ")
                    append("💰 ${it}만원")
                }
            }
            if (sched.isNotEmpty()) add(sched)
            event.address?.let { add("📍 $it") }
        }
        if (detailLines.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            detailLines.forEach { line ->
                Text(
                    line,
                    fontSize = 12.5.sp,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** 통화 길이(초)를 "3분 12초"/"45초" 로. 0 이하(부재중/거절)면 null = 길이 생략. */
private fun formatCallDuration(seconds: Long): String? {
    if (seconds <= 0L) return null
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}분 ${s}초" else "${s}초"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    body: String,
    timeMs: Long,
    sent: Boolean,
    imageUris: List<android.net.Uri>,
    isStarred: Boolean,
    onImageTap: (List<android.net.Uri>, Int) -> Unit,
    onLongPress: () -> Unit
) {
    // 프로토 .brow/.bubble — 시각(btime)은 말풍선 밖(옆 아래), 별표는 바깥쪽.
    //   me=파랑/우측 꼬리, cust=흰색+그림자/좌측 꼬리. radius19 + 꼬리 6.
    val bubbleShape = if (sent) {
        RoundedCornerShape(topStart = 19.dp, topEnd = 19.dp, bottomStart = 19.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 19.dp, topEnd = 19.dp, bottomStart = 6.dp, bottomEnd = 19.dp)
    }
    val timeText: @Composable () -> Unit = {
        Text(
            DateTimeUtils.formatShort(timeMs),
            color = TossTextTertiary,
            fontSize = 10.5.sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
    val star: @Composable () -> Unit = {
        Icon(Icons.Default.Bookmarks, "저장된 메시지", tint = TossBlue, modifier = Modifier.size(14.dp))
    }
    // 본문 속 링크(URL) — 밑줄+색으로 표시하고, 말풍선 탭하면 첫 링크를 연다.
    //   (Compose 1.6.8 = LinkAnnotation 미지원, ClickableText 는 텍스트 길게누르기를 먹어서 복사/저장이 깨짐 →
    //    길게누르기는 그대로 두고 탭=링크 열기 방식. 접수서 문자는 보통 링크 1개라 정확.) (2026-06-14 사장님)
    val uriHandler = LocalUriHandler.current
    val firstUrl = remember(body) { firstUrlIn(body) }
    val linkColor = if (sent) Color.White else TossBlue
    val styledBody = remember(body, sent) { linkifyBody(body, linkColor) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = if (sent) Arrangement.End else Arrangement.Start
    ) {
        // me: (별표) 시각 말풍선  /  cust: 말풍선 시각 (별표)
        if (sent) {
            if (isStarred) { star(); Spacer(Modifier.width(4.dp)) }
            timeText(); Spacer(Modifier.width(6.dp))
        }
        Surface(
            shape = bubbleShape,
            color = if (sent) TossBlue else Color.White,
            shadowElevation = if (sent) 0.dp else 1.dp,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = { firstUrl?.let { runCatching { uriHandler.openUri(it) } } },
                    onLongClick = onLongPress
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                if (imageUris.isNotEmpty()) {
                    // 한 줄에 최대 3장 썸네일. 탭하면 풀스크린.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        imageUris.indices.chunked(3).forEach { rowIdx ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rowIdx.forEach { idx ->
                                    AsyncImage(
                                        model = imageUris[idx],
                                        contentDescription = "첨부 사진",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(TossGrayBg)
                                            .clickable { onImageTap(imageUris, idx) }
                                    )
                                }
                            }
                        }
                    }
                    if (body.isNotBlank()) Spacer(Modifier.height(6.dp))
                }
                if (body.isNotBlank()) {
                    Text(
                        styledBody,
                        color = if (sent) Color.White else TossTextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
        if (!sent) {
            Spacer(Modifier.width(6.dp)); timeText()
            if (isStarred) { Spacer(Modifier.width(4.dp)); star() }
        }
    }
}

/** 본문에서 첫 URL 추출 — 스킴 없으면 https:// 보정해 반환. 없으면 null. */
private fun firstUrlIn(body: String): String? {
    val m = android.util.Patterns.WEB_URL.matcher(body)
    if (!m.find()) return null
    val raw = m.group()
    return if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"
}

/** 본문 속 모든 URL 을 밑줄+색으로 표시한 AnnotatedString (탭 동작은 말풍선 onClick 이 처리). */
private fun linkifyBody(body: String, linkColor: Color): AnnotatedString {
    val m = android.util.Patterns.WEB_URL.matcher(body)
    if (!m.find()) return AnnotatedString(body)
    m.reset()
    return buildAnnotatedString {
        var last = 0
        while (m.find()) {
            val s = m.start(); val e = m.end()
            if (s > last) append(body.substring(last, s))
            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                append(body.substring(s, e))
            }
            last = e
        }
        if (last < body.length) append(body.substring(last))
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
    endedWithCall: Boolean,
    expanded: Boolean,
    isStale: Boolean,
    onToggleExpand: () -> Unit,
    onPickChoice: (com.detailline.callfollowcrm.ai.ReplyChoice) -> Unit,
    onRegenerate: () -> Unit
) {
    // 통화로 끝난 대화면 추천/스피너 숨기고 "문자 오면 준비" 안내만. (2026-06-17 사장님)
    val hasSuggestions = suggestion != null && suggestion.suggestions.isNotEmpty() && !endedWithCall
    val effectiveLoading = loading && !endedWithCall
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
                "✨ 이렇게 답해보세요",
                color = TossBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.weight(1f))
            // 접기/펼치기 — 글자 라벨 pill 로 키워서 ↻(새로고침)과 헷갈리지 않게. (2026-06-11 사장님: 접으려다 새로고침 눌림)
            if (hasSuggestions) {
                Text(
                    if (expanded) "접기 ▾" else "펼치기 ▸",
                    color = TossTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleExpand)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            // 새로고침은 펼친 상태에서만 노출 — 접힌 상태에선 누를 일이 없고, 접기 버튼과 충분히 떨어뜨림.
            if (expanded) {
                Spacer(Modifier.width(8.dp))
                // 2026-05-30 사장님 디자인 보강 #4 — 시공 사장님 손가락 배려: 28dp → 40dp.
                IconButton(
                    onClick = onRegenerate,
                    enabled = !effectiveLoading,
                    modifier = Modifier.size(40.dp)
                ) {
                    if (effectiveLoading) {
                        CircularProgressIndicator(
                            color = TossBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "재생성",
                            tint = TossTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
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
                endedWithCall -> {
                    // 통화로 끝난 대화 — 답할 문자가 없으니 준비 안 함. 문자 오면 그때 준비. (2026-06-17 사장님)
                    Text(
                        "📞 통화로 끝난 대화예요 — 고객이 문자를 보내면 막내가 추천 답변을 준비할게요",
                        color = TossTextTertiary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
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
                                onTap = { onPickChoice(choice) }
                            )
                        }
                    }
                }
                effectiveLoading -> {
                    // 프로토 design-preview/ringgo-redesign.html :2810 (.think-row) 1:1.
                    //   [막내 마스코트 44dp] [회색 말풍선 안에 파란 점 3개 통통] "막내가 답변을 준비 중이에요!"
                    com.detailline.callfollowcrm.presentation.component.MascotThinkingRow(
                        modifier = Modifier.padding(top = 4.dp)
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
    // 2026-05-30 사장님 디자인 보강 #4 — 시공 사장님 손가락 배려.
    //   sizeIn(minHeight=48dp) 으로 단일 라인 케이스도 최소 터치 보장.
    //   vertical padding 10dp → 12dp 로 시각적 여유.
    // 프로토 .sug-chip — 흰 카드(238px) + .cl(✨ 파란 라벨) + .ct(검은 본문).
    Surface(
        modifier = Modifier
            .width(238.dp)
            .clickable { onTap() },
        shape = RoundedCornerShape(15.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = TossBlue, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    label?.takeIf { it.isNotBlank() } ?: "추천 $index",
                    color = TossBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text,
                color = TossTextPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * (Phase 2) 원칙 발견 카드 — design-preview/proto-principle-discovery.html 의 .disc / .resolved 1:1.
 *   추천과 다른 답을 보냈을 때 막내가 "이게 사장님 원칙이에요?"를 ⭕/❌/나중에 로 묻는다.
 *   ⭕ → 저장 + "기억했어요 🌱" / ❌ → "잊을게요" / 나중에 → "다음에 또". 결과 보여준 뒤 자동 사라짐.
 */
@Composable
private fun PrincipleDiscoveryCard(
    discovery: PrincipleDiscovery,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit
) {
    val blueDeep = Color(0xFF1B64DA)
    val blueSoft = Color(0xFFE8F1FE)
    val lineColor = Color(0xFFEAEDF0)

    if (discovery.resolved != null) {
        // 결과 메시지 — 잠깐 보여주고 자동 해제. (프로토 .resolved)
        LaunchedEffect(discovery.resolved) {
            kotlinx.coroutines.delay(2600)
            onDismiss()
        }
        val style = when (discovery.resolved) {
            PrincipleResolved.OK -> ResolvedStyle("✅", "기억했어요! 막내가 사장님을 하나 더 알게 됐어요 🌱",
                Color(0xFFE7F5F3), Color(0xFF0A7D72), Color(0xFFBFE7E1))
            PrincipleResolved.NO -> ResolvedStyle("🙇", "알겠어요, 잊을게요. 더 지켜보고 다시 배울게요.",
                Color(0xFFF7F8FA), TossTextSecondary, lineColor)
            PrincipleResolved.LATER -> ResolvedStyle("⏭️", "다음에 또 보이면 그때 여쭤볼게요.",
                Color(0xFFF7F8FA), TossTextSecondary, lineColor)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(style.bg)
                .border(1.dp, style.border, RoundedCornerShape(18.dp))
                .padding(15.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(style.emoji, fontSize = 18.sp)
            Spacer(Modifier.width(9.dp))
            Text(style.msg, color = style.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
        }
        return
    }

    // 질문 카드 (프로토 .disc) — 서버가 question 을 주면 그대로, 없으면 원칙을 감싼 템플릿.
    //   연세 있는 사장님 문해력 배려(2026-06-20) — "응대/원칙" 같은 딱딱한 말 빼고 쉬운 일상말로.
    val q = discovery.question?.takeIf { it.isNotBlank() }
        ?: "방금 보니까, 사장님은 이렇게 답하시네요:\n\n\"${discovery.principle}\"\n\n앞으로도 이렇게 하면 될까요?"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.5.dp, blueSoft, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(blueSoft)
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text("💡 막내가 하나 배웠어요", color = blueDeep, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(9.dp))
        Text(q, color = TossTextPrimary, fontSize = 14.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(
            "맞으면 앞으로 막내가 이렇게 답해요. (아니면 ❌ 눌러요)",
            color = TossTextTertiary, fontSize = 11.5.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TossBlue)
                    .clickable(onClick = onAccept)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("⭕ 맞아요", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, lineColor, RoundedCornerShape(12.dp))
                    .clickable(onClick = onReject)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("❌ 아니에요", color = TossTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
            Box(
                Modifier
                    .weight(0.7f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, lineColor, RoundedCornerShape(12.dp))
                    .clickable(onClick = onLater)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("나중에", color = TossTextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

private data class ResolvedStyle(
    val emoji: String,
    val msg: String,
    val bg: Color,
    val fg: Color,
    val border: Color
)

/** 프로토 .act-chip — 흰 알약 + 파란 아이콘 + 라벨 (견적 작성 / 내 일정 확인 / 문구 넣기). */
@Composable
private fun ActChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .border(1.dp, com.detailline.callfollowcrm.presentation.theme.TossDivider, RoundedCornerShape(999.dp))
            .clickable { onTap() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TossBlue, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
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
    selection: TextRange,
    onChange: (String) -> Unit,
    onSelectionChange: (TextRange) -> Unit,
    isPolishing: Boolean,
    onAiPolish: () -> Unit,
    onAttachPhoto: () -> Unit,
    attachments: List<android.net.Uri>,
    onRemoveAttachment: (android.net.Uri) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {}
) {
    // 프로토 .composer — 흰 바 + 상단 테두리 + padding 9/14/16.
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color.White)
            .drawBehind {
                val s = 1.dp.toPx()
                drawLine(TossDivider, androidx.compose.ui.geometry.Offset(0f, s / 2), androidx.compose.ui.geometry.Offset(size.width, s / 2), s)
            }
            .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 16.dp)
    ) {
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
        // 프로토 .composer — 회색 알약 field [✨ 왼쪽][textarea][📷 오른쪽] + 40px 파란 발송 원(항상 파랑).
        val canSend = input.isNotBlank() || attachments.isNotEmpty()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // field — 회색 알약(radius22)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(TossGrayBg)
                    .padding(horizontal = 15.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // ✨ AI 다듬기 (왼쪽)
                if (isPolishing) {
                    CircularProgressIndicator(color = TossBlue, strokeWidth = 2.dp, modifier = Modifier.size(19.dp))
                } else {
                    Icon(
                        Icons.Default.AutoAwesome, "AI 다듬기", tint = TossBlue,
                        modifier = Modifier.size(19.dp).clickable(enabled = !isPolishing) { onAiPolish() }
                    )
                }
                // 한글 조합(composition) 보존 — value 를 매 입력마다 새 TextFieldValue 로 만들면 조합영역이 끊겨
                //   "ㄱㅣㄷㅏㄹㅕ"처럼 자모가 분리됨. IME 가 준 TextFieldValue 를 그대로 들고 있어야 합쳐진다. (2026-06-17 사장님 버그)
                //   외부(템플릿·다듬기·캘린더·발송클리어)에서 input 이 바뀐 경우에만 다시 채택(그땐 커서 지정 위치/끝).
                var tfv by remember { mutableStateOf(TextFieldValue(input, TextRange(input.length))) }
                if (input != tfv.text) {
                    tfv = TextFieldValue(
                        text = input,
                        selection = TextRange(
                            selection.start.coerceIn(0, input.length),
                            selection.end.coerceIn(0, input.length)
                        )
                    )
                }
                BasicTextField(
                    value = tfv,
                    onValueChange = { v ->
                        tfv = v                       // IME 조합영역 보존(핵심) — 한글 자모가 합쳐짐
                        onChange(v.text)
                        onSelectionChange(v.selection)
                    },
                    textStyle = TextStyle(color = TossTextPrimary, fontSize = 14.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(TossBlue),
                    maxLines = 5,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state -> onFocusChange(state.isFocused) },
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text("메시지 입력...", color = TossTextTertiary, fontSize = 14.sp)
                        }
                        inner()
                    }
                )
                // 📷 사진 첨부 (오른쪽)
                Icon(
                    Icons.Default.Image, "사진 첨부", tint = TossTextTertiary,
                    modifier = Modifier.size(19.dp).clickable { onAttachPhoto() }
                )
            }
            // 40px 파란 발송 원 — 프로토 .snd: 항상 파랑 + 흰 아이콘 + 파란 glow.
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(8.dp, androidx.compose.foundation.shape.CircleShape, ambientColor = TossBlue, spotColor = TossBlue),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = TossBlue,
                onClick = { if (canSend && !isSending) onSend() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSending) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send, "보내기",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
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
    summaryLine: String,
    summaryLineCount: Int,
    nextActionTitle: String?,
    isRefreshing: Boolean = false,
    onExpand: () -> Unit
) {
    // 2026-06-02 사장님 결정(프로토 1:1) — 접힘 상태 = 프로토 chat-summary 바.
    //   흰 전체 바 + ✨ + 한 줄 요약(#1B64DA, 12.5sp w600) + 아래 테두리. 탭→펼침.
    val line = summaryLine.takeIf { it.isNotBlank() }
        ?: nextActionTitle?.takeIf { it.isNotBlank() }
        ?: (if (summaryLineCount > 0) "지난 대화를 정리했어요" else return)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .drawBehind {
                val s = 1.dp.toPx()
                drawLine(TossDivider, androidx.compose.ui.geometry.Offset(0f, size.height - s / 2),
                    androidx.compose.ui.geometry.Offset(size.width, size.height - s / 2), s)
            }
            .clickable { onExpand() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("✨", fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
        Text(
            "요약: $line",
            color = TossBlueDark,
            fontSize = 12.5.sp,
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
                modifier = Modifier.size(12.dp).padding(start = 6.dp, end = 4.dp)
            )
        }
        // 펼치기 affordance — 탭하면 풍부한 요약 카드. (프로토 한 줄 바 + 펼침 = 사장님 결정)
        Text("▾", color = TossTextTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp))
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
    onDelete: (Long) -> Unit = {},
    onSaveCurrent: () -> Unit = {},
    canSaveCurrent: Boolean = false,
    onDismiss: () -> Unit
) {
    val filtered = remember(templates, category) {
        val byCategory = if (category.isBlank()) templates
        else templates.filter { it.category == category }
        // 카테고리 비어있으면 사장님이 직접 고르도록 전체로 fallback
        byCategory.ifEmpty { templates }
    }
    val title = if (category.isBlank()) "문구 넣기"
    else "${categoryLabel(category)} 문구"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, color = TossTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // ＋ 새 문구 — 입력창에 쓴 글을 그대로 문구로 저장(키보드 없이, 채팅 흐름 그대로).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TossBlueSoft)
                        .clickable { onSaveCurrent() }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("＋", color = TossBlue, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (canSaveCurrent) "지금 입력창에 쓴 글을 문구로 저장" else "입력창에 글을 쓴 뒤 누르면 문구로 저장돼요",
                        color = TossBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text(
                        "아직 저장된 문구가 없어요. 위 ＋ 로 바로 추가할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextTertiary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.id }) { tpl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TossGrayBg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).clickable { onPick(tpl) }.padding(12.dp)
                                ) {
                                    Text(
                                        tpl.title, color = TossBlue,
                                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        tpl.body, color = TossTextSecondary, fontSize = 13.sp,
                                        maxLines = 3, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                // ✕ 삭제 — 그 자리에서 바로. (확인창 없이 toast — 다시 ＋ 로 복구 가능)
                                Box(
                                    modifier = Modifier.clickable { onDelete(tpl.id) }
                                        .padding(horizontal = 14.dp, vertical = 14.dp)
                                ) {
                                    Text("✕", color = TossTextTertiary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun SendConfirmDialog(
    recipient: String,
    body: String,
    photoCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    // 프로토엔 발송 확인이 없지만(바로 전송), 실제 문자라 안전 확인은 유지.
    //   2026-06-03: 가운데 AlertDialog(진한 막) → 프로토식 바텀시트(그립+미리보기+보내기/취소)로 교체.
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onCancel,
        containerColor = Color.White,
        tonalElevation = 0.dp
    ) {
        Column(
            // 내비바/제스처바와 [취소][보내기] 버튼 겹침 방지(M3 시트 인셋 0 버그 우회). 2026-06-11
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).bottomBarClearance(extra = 16.dp)
        ) {
            Text(
                "$recipient 에게 보낼까요?",
                color = TossTextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp
            )
            Spacer(Modifier.height(14.dp))
            if (body.isNotBlank()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                        .background(TossBlueSoft).padding(14.dp)
                ) {
                    Text(body, color = TossTextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            if (photoCount > 0) {
                if (body.isNotBlank()) Spacer(Modifier.height(8.dp))
                Text("📷 사진 ${photoCount}장 첨부", color = TossTextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(18.dp))
            // sheet-cta 보내기
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TossBlue)
                    .clickable { onConfirm() }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) { Text("보내기", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TossGrayBg)
                    .clickable { onCancel() }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) { Text("취소", color = TossTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/**
 * 견적 만들기 상태 — '미리보기 닫기' 후 편집기로 돌아와도 선택이 유지되도록 ChatScreen 레벨에서 보관. (2026-06-08 #5)
 *   (편집기=ModalBottomSheet 는 별도 윈도우라 미리보기 띄울 때 닫혀야 함 → 상태를 밖에 둬야 보존됨.)
 */
private class EstimateDraft(initialCalMonth: Long) {
    val mode = androidx.compose.runtime.mutableStateOf("text")
    val depMode = androidx.compose.runtime.mutableStateOf("ratio")
    val depVal = androidx.compose.runtime.mutableStateOf("30")
    val depCustom = androidx.compose.runtime.mutableStateOf(false)
    val workDateMs = androidx.compose.runtime.mutableStateOf<Long?>(null)
    val workDays = androidx.compose.runtime.mutableStateOf(1)
    val estCalMonth = androidx.compose.runtime.mutableStateOf(initialCalMonth)
    val selectedQty = androidx.compose.runtime.mutableStateMapOf<Long, Int>()
    val customItems = androidx.compose.runtime.mutableStateListOf<EstCustomLine>()
    /** 견적을 보냈거나(또는 진짜 닫았을 때) 다음을 위해 초기화. 미리보기 왕복 때는 호출 안 함. */
    fun reset(initialCalMonth: Long) {
        mode.value = "text"; depMode.value = "ratio"; depVal.value = "30"; depCustom.value = false
        workDateMs.value = null; workDays.value = 1; estCalMonth.value = initialCalMonth
        selectedQty.clear(); customItems.clear()
    }
}

/** 받은/보낸 문자 사진을 휴대폰 갤러리(사진/RING-GO)에 저장. 성공 true. (2026-06-23 사장님) */
private suspend fun saveImageToGallery(context: android.content.Context, uri: android.net.Uri): Boolean =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "RINGGO_" + System.currentTimeMillis() + ".jpg")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/RING-GO")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val dest = resolver.insert(collection, values) ?: return@runCatching false
            val ok = resolver.openOutputStream(dest)?.use { out ->
                resolver.openInputStream(uri)?.use { input -> input.copyTo(out); true } ?: false
            } ?: false
            if (!ok) return@runCatching false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                values.clear()
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(dest, values, null, null)
            }
            true
        }.getOrDefault(false)
    }

@Composable
private fun EstimateBuilderDialog(
    items: List<com.detailline.callfollowcrm.data.local.entity.PricingItemEntity>,
    draft: EstimateDraft,
    onConfirm: (String) -> Unit,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit,
    onQuoteDoc: (QuoteDocData) -> Unit = {},
    onIssueIntake: (
        items: List<com.detailline.callfollowcrm.ai.IntakeFormRepository.QuoteIssueItem>,
        total: Int, workYear: Int, workMonth: Int, workDay: Int, workDays: Int,
        depMode: String, depVal: Int
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    bizName: String = "",
    bizOwner: String = "",
    bizNo: String = "",
    bizPhone: String = "",
    validDays: Int = 0
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val noRipple = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // 프로토 seg — 보내는 방식: text(문자 견적) / accept(시공접수서) / quote(견적서)
    // 상태는 draft(ChatScreen 보관)로 위임 — 미리보기 닫고 와도 유지. 사용처는 그대로. (2026-06-08 #5)
    var mode by draft.mode
    // 계약금 — 프로토 depMode: ratio(%)/fixed(만원)/none.
    var depMode by draft.depMode
    var depVal by draft.depVal
    var depCustom by draft.depCustom // 비율 '기타' 직접입력 여부
    // 시공일 (접수서/견적서) — null=미정.
    var workDateMs by draft.workDateMs
    var workDays by draft.workDays
    var estCalMonth by draft.estCalMonth
    // 항목 id → 수량(평당=평수, 정액=1). 0/미존재 = 미선택.
    val selectedQty = draft.selectedQty
    // 가격표에 없는 즉석 항목(예: 실리콘) — 견적 만들기에서 바로 직접 추가. (2026-06-07 사장님 요청)
    val customItems = draft.customItems
    // 프로토: 카테고리 없는 평탄 리스트.
    val visibleItems = remember(items) { items.sortedBy { it.displayOrder } }
    val totalSum by remember {
        androidx.compose.runtime.derivedStateOf {
            visibleItems.sumOf { (selectedQty[it.id] ?: 0) * it.price } +
                customItems.sumOf { (it.manwon.toIntOrNull() ?: 0) * 10_000L }
        }
    }
    val anySelected = selectedQty.values.any { it > 0 } ||
        customItems.any { it.name.isNotBlank() && (it.manwon.toIntOrNull() ?: 0) > 0 }
    val help = when (mode) {
        "accept" -> "예약금 받은 고객에게 — 정해진 시공일이 맞는지 확인하고 주소를 입력받는 셀프 접수서 링크예요."
        "quote" -> "고객이 보고용으로 쓰는 직인 찍힌 정식 견적서를 만들어 보내요."
        else -> "링크 없이 견적 내용만 문자로 보내요. (가볍게 견적만 물어볼 때)"
    }
    fun composeBody() = buildEstimateBody(visibleItems, selectedQty.toMap(), totalSum, bizName, customItems.toList())
    fun toast(msg: String) = android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
    fun quoteDocData(): QuoteDocData {
        val lines = visibleItems.mapNotNull { item ->
            val qty = selectedQty[item.id] ?: 0
            if (qty <= 0) return@mapNotNull null
            val isPyeong = item.unit == com.detailline.callfollowcrm.data.local.entity.PricingItemEntity.UNIT_PYEONG
            val spec = if (isPyeong) "${item.price / 10_000L}만원/평 × ${qty}평" else "1식"
            val amount = if (isPyeong) item.price * qty else item.price
            QuoteLine(item.title, spec, amount)
        }
        val customLines = customItems.mapNotNull { c ->
            val won = (c.manwon.toIntOrNull() ?: 0) * 10_000L
            if (c.name.isBlank() || won <= 0) null else QuoteLine(c.name.trim(), "1식", won)
        }
        return QuoteDocData(lines + customLines, totalSum, depMode, depVal.toIntOrNull() ?: 0)
    }

    // 인라인 오버레이(액티비티 창) — ModalBottomSheet 는 별도 윈도우라 키보드가 입력칸(직접항목·계약금)을
    //   가린다(갤S9/안드10). 액티비티 창 + imePadding 으로 키보드 위로 올린다. (팀원배정/정산목표 시트와 동일 패턴)
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
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(top = 12.dp, bottom = 22.dp)
        ) {
            Text("견적 만들기", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                color = TossTextPrimary, letterSpacing = (-0.4).sp)
            Spacer(Modifier.height(4.dp))
            Text("항목을 고르고, 어떻게 보낼지 정하세요", fontSize = 13.sp, color = TossTextTertiary)
            Spacer(Modifier.height(12.dp))
            // 프로토 .seg 탭
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                EstSegTab("문자 견적", mode == "text", Modifier.weight(1f)) { mode = "text" }
                EstSegTab("시공접수서", mode == "accept", Modifier.weight(1f)) { mode = "accept" }
                EstSegTab("견적서", mode == "quote", Modifier.weight(1f)) { mode = "quote" }
            }
            Spacer(Modifier.height(9.dp))
            Text(help, fontSize = 12.sp, color = TossTextTertiary, lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 2.dp))
            // 시공일 (시공접수서/견적서 탭) — 프로토 q-datefield + 달력
            if (mode != "text") {
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("시공일", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary,
                        modifier = Modifier.padding(start = 2.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        workDateMs?.let { DateTimeUtils.formatKoreanDate(it) } ?: "미정 · 날짜를 골라주세요",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (workDateMs != null) TossBlue else TossTextTertiary
                    )
                }
                Spacer(Modifier.height(6.dp))
                EstInlineCalendar(estCalMonth, workDateMs,
                    onShiftMonth = { estCalMonth = estShiftMonth(estCalMonth, it) },
                    onSelect = { workDateMs = it })
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf("당일" to 1, "2일" to 2, "3일" to 3, "4일" to 4, "5일" to 5, "일주일" to 7).forEach { (lbl, d) ->
                        EstSmallChip(lbl, workDays == d) { workDays = d }
                    }
                }
            }
            // 계약금 설정 (시공접수서/견적서 탭) — 프로토 depMode
            if (mode != "text") {
                Spacer(Modifier.height(11.dp))
                Text("계약금", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary,
                    modifier = Modifier.padding(start = 2.dp))
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    EstSegTab("비율(%)", depMode == "ratio", Modifier.weight(1f)) { depMode = "ratio" }
                    EstSegTab("정액", depMode == "fixed", Modifier.weight(1f)) { depMode = "fixed" }
                    EstSegTab("없음", depMode == "none", Modifier.weight(1f)) { depMode = "none" }
                }
                if (depMode == "ratio") {
                    // 비율 — 숫자패드 대신 알약(10/20/30/기타). 기타만 직접입력.
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        listOf("10", "20", "30").forEach { v ->
                            EstSmallChip("$v%", !depCustom && depVal == v) { depVal = v; depCustom = false }
                        }
                        EstSmallChip("기타", depCustom) { depCustom = true }
                    }
                    if (depCustom) {
                        Spacer(Modifier.height(8.dp))
                        com.detailline.callfollowcrm.presentation.component.SheetTextField(
                            depVal, { depVal = it.filter { c -> c.isDigit() } },
                            placeholder = "예: 25 (%)", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    }
                    val depW = totalSum * (depVal.toIntOrNull() ?: 0) / 100
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "계약금 ${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(depW)}원 (합계의 ${depVal.ifBlank { "0" }}%)",
                        fontSize = 12.5.sp, color = TossBlue, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                } else if (depMode == "fixed") {
                    Spacer(Modifier.height(8.dp))
                    com.detailline.callfollowcrm.presentation.component.SheetTextField(
                        depVal, { depVal = it.filter { c -> c.isDigit() } },
                        placeholder = "만원 (예: 10)", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation
                    )
                    val depW = (depVal.toIntOrNull() ?: 0) * 10_000L
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "계약금 ${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(depW)}원 (정액)",
                        fontSize = 12.5.sp, color = TossBlue, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            // 항목 리스트 (프로토 est-row + 평당 est-area)
            // 전체 시트가 한 번에 스크롤되도록 항목은 일반 Column(내부 LazyColumn 제거).
            Column(Modifier.fillMaxWidth()) {
                visibleItems.forEachIndexed { idx, item ->
                    if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                    val isPyeong = item.unit == com.detailline.callfollowcrm.data.local.entity.PricingItemEntity.UNIT_PYEONG
                    EstimateItemRow(
                        title = item.title,
                        price = item.price,
                        unit = item.unit,
                        quantity = selectedQty[item.id] ?: 0,
                        onToggle = {
                            val cur = selectedQty[item.id] ?: 0
                            if (cur > 0) selectedQty.remove(item.id)
                            else selectedQty[item.id] = if (isPyeong) 24 else 1
                        },
                        onIncrement = { selectedQty[item.id] = (selectedQty[item.id] ?: 0) + 1 },
                        onDecrement = {
                            val cur = selectedQty[item.id] ?: 0
                            if (cur > 1) selectedQty[item.id] = cur - 1
                        }
                    )
                }
            }
            // 직접 추가 항목 — 가격표에 없는 즉석 견적(예: "실리콘 시공"). (2026-06-07 사장님 요청)
            customItems.forEachIndexed { idx, c ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        com.detailline.callfollowcrm.presentation.component.SheetTextField(
                            c.name, { c.name = it }, placeholder = "항목명 (예: 실리콘 시공)"
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.width(96.dp)) {
                        com.detailline.callfollowcrm.presentation.component.SheetTextField(
                            c.manwon, { c.manwon = it.filter { ch -> ch.isDigit() } },
                            placeholder = "만원",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation
                        )
                    }
                    Text(
                        "✕", fontSize = 17.sp, color = TossTextTertiary, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp).clickable { customItems.removeAt(idx) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, TossDivider, RoundedCornerShape(12.dp))
                    .clickable { customItems.add(EstCustomLine()) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("＋ 직접 항목 추가", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossBlue)
            }
            // 프로토 .est-total
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(TossDivider))
            Row(Modifier.fillMaxWidth().padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("합계", fontSize = 15.sp, color = TossTextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(formatWon(totalSum), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
            }
            Spacer(Modifier.height(17.dp))
            // 프로토 .sheet-cta — 탭별 라벨/동작
            val ctaText = when (mode) {
                "accept" -> "시공접수서 링크 보내기"
                "quote" -> "📜 견적서(직인) 보내기"
                else -> "문자에 견적 넣기"
            }
            EstSheetCta(ctaText, enabled = anySelected, filled = true) {
                if (!anySelected) { toast("항목을 한 개 이상 골라주세요"); return@EstSheetCta }
                when (mode) {
                    "text" -> onConfirm(composeBody())
                    "quote" -> onQuoteDoc(quoteDocData())
                    else -> {
                        // 시공접수서 — 서버 발급 → smsDraft prefill (호출부에서 처리)
                        val cal = workDateMs?.let { java.util.Calendar.getInstance().apply { timeInMillis = it } }
                        val issItems = visibleItems.mapNotNull { item ->
                            val q = selectedQty[item.id] ?: 0
                            if (q <= 0) return@mapNotNull null
                            val isP = item.unit == com.detailline.callfollowcrm.data.local.entity.PricingItemEntity.UNIT_PYEONG
                            com.detailline.callfollowcrm.ai.IntakeFormRepository.QuoteIssueItem(
                                name = item.title,
                                price = (item.price / 10_000L).toInt(),
                                unit = if (isP) "pyeong" else "flat",
                                area = if (isP) q.toDouble() else null
                            )
                        }
                        val customIss = customItems.mapNotNull { c ->
                            val m = c.manwon.toIntOrNull() ?: 0
                            if (c.name.isBlank() || m <= 0) null
                            else com.detailline.callfollowcrm.ai.IntakeFormRepository.QuoteIssueItem(
                                name = c.name.trim(), price = m, unit = "flat", area = null
                            )
                        }
                        onIssueIntake(
                            issItems + customIss, (totalSum / 10_000L).toInt(),
                            cal?.get(java.util.Calendar.YEAR) ?: 0,
                            cal?.let { it.get(java.util.Calendar.MONTH) + 1 } ?: 0,
                            cal?.get(java.util.Calendar.DAY_OF_MONTH) ?: 0,
                            workDays, depMode, depVal.toIntOrNull() ?: 0
                        )
                    }
                }
            }
            if (mode != "text") {
                Spacer(Modifier.height(9.dp))
                EstSheetCta("문자로 붙여넣기", enabled = anySelected, filled = false) {
                    if (anySelected) onConfirm(composeBody()) else toast("항목을 한 개 이상 골라주세요")
                }
            }
        }
    }
}

/** 프로토 .seg .sg — 보내는 방식 탭. */
@Composable
private fun EstSegTab(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (on) Color.White else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
            color = if (on) TossBlue else TossTextSecondary)
    }
}

/** 프로토 .sheet-cta — 가득 찬 파란 버튼(filled) / 회색 보조 버튼. */
@Composable
private fun EstSheetCta(text: String, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) (if (enabled) TossBlue else TossDivider) else TossGrayBg
    val fg = if (filled) Color.White else TossBlue
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable { onClick() }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EstimateItemRow(
    title: String,
    price: Long,
    unit: String,
    quantity: Int,
    onToggle: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val checked = quantity > 0
    val isPyeong = unit == com.detailline.callfollowcrm.data.local.entity.PricingItemEntity.UNIT_PYEONG
    Column(Modifier.fillMaxWidth()) {
        // 프로토 .est-item — [체크박스] 이름(flex) 가격(우측)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 프로토 .est-box — 24dp rounded8, on=파랑 채움+체크
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (checked) TossBlue else Color.White)
                    .border(2.dp, if (checked) TossBlue else TossDivider, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (checked) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = TossTextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text(
                if (isPyeong) "${formatWon(price)}/평" else formatWon(price),
                color = TossTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
        }
        // 프로토 .est-area — 평당 항목 선택 시 평수 조절
        if (checked && isPyeong) {
            Row(
                Modifier.fillMaxWidth().padding(start = 36.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("평수", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                Spacer(Modifier.width(8.dp))
                StepperButton("−", onClick = onDecrement)
                Spacer(Modifier.width(8.dp))
                Text("${quantity}평", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                    modifier = Modifier.widthIn(min = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                StepperButton("+", onClick = onIncrement)
                Spacer(Modifier.weight(1f))
                Text("= ${formatWon(price * quantity)}", fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold, color = TossBlueDark)
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

@Composable
private fun EstSmallChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) TossBlue else TossGrayBg)
            .clickable { onClick() }.padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else TossTextSecondary)
    }
}

private data class EstCell(val dayMs: Long, val dom: Int, val dow: Int, val inMonth: Boolean, val isToday: Boolean, val isPast: Boolean)

private fun estMonthAnchor(anyMs: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = anyMs
    set(java.util.Calendar.DAY_OF_MONTH, 1)
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

private fun estShiftMonth(anchor: Long, delta: Int): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = anchor; add(java.util.Calendar.MONTH, delta); set(java.util.Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private fun buildEstCells(anchor: Long): List<EstCell> {
    val today = DateTimeUtils.startOfDay(System.currentTimeMillis())
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = anchor }
    val month = cal.get(java.util.Calendar.MONTH)
    val firstDow = cal.get(java.util.Calendar.DAY_OF_WEEK)
    cal.add(java.util.Calendar.DAY_OF_MONTH, -(firstDow - 1))
    return (0 until 42).map {
        val dayStart = DateTimeUtils.startOfDay(cal.timeInMillis)
        val cell = EstCell(
            dayStart, cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.DAY_OF_WEEK),
            cal.get(java.util.Calendar.MONTH) == month, dayStart == today, dayStart < today
        )
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        cell
    }
}

/** 견적 시트 시공일 선택용 인라인 월 달력. */
@Composable
private fun EstInlineCalendar(monthAnchor: Long, selectedMs: Long?, onShiftMonth: (Int) -> Unit, onSelect: (Long) -> Unit) {
    val cells = remember(monthAnchor) { buildEstCells(monthAnchor) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color.White).clickable { onShiftMonth(-1) },
                contentAlignment = Alignment.Center) { Text("‹", fontSize = 16.sp, color = TossTextSecondary, fontWeight = FontWeight.Bold) }
            Text(DateTimeUtils.formatMonthHeader(monthAnchor), modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color.White).clickable { onShiftMonth(1) },
                contentAlignment = Alignment.Center) { Text("›", fontSize = 16.sp, color = TossTextSecondary, fontWeight = FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                Text(d, modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 11.sp,
                    color = when (i) { 0 -> TossError; 6 -> TossBlue; else -> TossTextSecondary }, fontWeight = FontWeight.SemiBold)
            }
        }
        repeat(6) { w ->
            Row(Modifier.fillMaxWidth()) {
                cells.subList(w * 7, w * 7 + 7).forEach { cell ->
                    val isSel = selectedMs?.let { DateTimeUtils.startOfDay(it) == cell.dayMs } == true
                    val bg = when { isSel -> TossBlue; cell.isToday -> TossBlueSoft; else -> Color.Transparent }
                    val fg = when {
                        isSel -> Color.White
                        !cell.inMonth || cell.isPast -> TossTextTertiary
                        cell.dow == java.util.Calendar.SUNDAY -> TossError
                        cell.dow == java.util.Calendar.SATURDAY -> TossBlue
                        else -> TossTextPrimary
                    }
                    Box(
                        Modifier.weight(1f).height(34.dp).padding(2.dp).clip(RoundedCornerShape(8.dp))
                            .background(bg).clickable { onSelect(cell.dayMs) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(cell.dom.toString(), color = fg, fontSize = 12.sp,
                            fontWeight = if (isSel || cell.isToday) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
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
    items: List<com.detailline.callfollowcrm.data.local.entity.PricingItemEntity>,
    quantities: Map<Long, Int>,
    totalSum: Long,
    bizName: String = "",
    custom: List<EstCustomLine> = emptyList()
): String = buildString {
    // 프로토 makeEstimate — 친근한 인사 + 항목 나열 + 합계(부가세 별도) + 방문 제안.
    if (bizName.isNotBlank()) append("안녕하세요, ${bizName}입니다 😊\n")
    else append("안녕하세요 😊\n")
    append("요청주신 견적 안내드려요.\n")
    for (item in items) {
        val qty = quantities[item.id] ?: 0
        if (qty <= 0) continue
        val isPyeong = item.unit == com.detailline.callfollowcrm.data.local.entity.PricingItemEntity.UNIT_PYEONG
        if (isPyeong) {
            append("· ${item.title} ${formatWon(item.price)}/평 × ${qty}평 = ${formatWon(item.price * qty)}\n")
        } else {
            append("· ${item.title} ${formatWon(item.price)}\n")
        }
    }
    // 직접 추가한 즉석 항목.
    for (c in custom) {
        val won = (c.manwon.toIntOrNull() ?: 0) * 10_000L
        if (c.name.isBlank() || won <= 0) continue
        append("· ${c.name.trim()} ${formatWon(won)}\n")
    }
    append("합계 ${formatWon(totalSum)} (부가세 별도)\n")
    append("\n방문 일정 잡아드릴까요? 😊")
}

/** 견적 만들기에서 직접 추가하는 즉석 항목 (가격표에 없는 것 — 예: 실리콘). name + manwon(만원). */
private class EstCustomLine(name: String = "", manwon: String = "") {
    var name by androidx.compose.runtime.mutableStateOf(name)
    var manwon by androidx.compose.runtime.mutableStateOf(manwon)
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
