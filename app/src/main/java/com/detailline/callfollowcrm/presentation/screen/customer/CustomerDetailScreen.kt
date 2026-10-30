package com.detailline.callfollowcrm.presentation.screen.customer

import com.detailline.callfollowcrm.presentation.util.bottomBarClearance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.MoreHoriz
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppType
import androidx.compose.material.icons.filled.Check
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.LightColors
import com.detailline.callfollowcrm.util.copyToClip

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import com.detailline.callfollowcrm.presentation.util.AppSnackbarHost
import com.detailline.callfollowcrm.presentation.util.showBrief
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity
import com.detailline.callfollowcrm.presentation.component.CelebrationOverlay
import com.detailline.callfollowcrm.presentation.component.SectionLabel
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.component.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.detailline.callfollowcrm.presentation.component.vibrateCelebration
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import com.detailline.callfollowcrm.util.splitSiteAddress
import kotlinx.coroutines.launch
import com.detailline.callfollowcrm.presentation.util.keyboardPadding
import com.detailline.callfollowcrm.util.PhoneKey
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 메모를 저장해도 되는가 — 저장 경로가 두 곳(타이핑 debounce / 화면 나갈 때 flush)이라
 * 규칙을 한 군데로 모은다. 눈에 안 보이는 사고라 단위 테스트로 고정한다.
 *
 * 규칙: **사람이 직접 고친 적이 있고**, 저장된 값과 실제로 다를 때만 쓴다.
 *   dirty 가 빠지면 화면이 아직 빈 상태일 때의 ""가 저장으로 나가 메모를 덮어쓴다.
 */
internal fun shouldSaveMemo(dirty: Boolean, input: String, saved: String?): Boolean =
    dirty && input != saved.orEmpty()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CustomerDetailScreen(
    viewModel: CustomerDetailViewModel,
    onBack: () -> Unit,
    /** "💬 문자 보내기" 탭 시 ChatScreen 으로 이동. customerId 알고 있으므로 같이 전달. */
    onOpenChat: (phone: String, customerId: Long) -> Unit,
    /** 발행 이력 "수정" 탭 시 채팅으로 이동하며 그 접수서를 편집기로 재오픈. (2026-07-10 사장님) */
    onOpenChatEditIssued: (phone: String, customerId: Long, issuedId: Long) -> Unit = { _, _, _ -> },
    /** ⏸️ 상담함 [보류] 카드 「일정 잡기」로 들어왔다 — 열리자마자 날짜 창을 띄운다(날짜 없는 그 건에). (2026-10-16) */
    autoOpenDatePicker: Boolean = false
) {
    val customer by viewModel.customer.collectAsState()
    val records by viewModel.callRecords.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val systemSms by viewModel.systemSms.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val markedUndo by viewModel.markedUndo.collectAsState()   // 🔙 펜으로 바꾼 뒤 되돌리기 스낵바
    val context = LocalContext.current
    val container = remember { (context.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var memoInput by remember(customer?.id) { mutableStateOf(customer?.memo.orEmpty()) }
    // 사용자가 이 고객의 메모를 **실제로 고쳤는지**. 저장은 이게 true 일 때만 한다. (2026-09-16)
    //   이게 없으면 화면이 아직 빈 상태(고객 로딩 전)일 때의 빈 글자가 저장으로 나가 메모를 덮어쓴다.
    //   아래 DisposableEffect 설명 참고.
    var memoDirty by remember(customer?.id) { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    /** 날짜 고르기가 '새 건 만들기' 로 열렸나. false = 지금 건의 날짜를 고치는 것. (2026-09-17) */
    var addingNewJob by remember { mutableStateOf(false) }
    /** ➕ 새 시공 시트(프로토 `s_newjob`: 시공일+주소+총액) — 열림 · 시트에서 고른 날짜/기간. 달력(위 datePickerOpen)이 돌려준다. (2026-10-15) */
    var newJobSheetOpen by remember { mutableStateOf(false) }
    var newJobStart by remember { mutableStateOf<Long?>(null) }
    var newJobDays by remember { mutableStateOf(1) }
    /** 날짜 고치기가 **어느 건**을 고치는지. null = 고객(대표) 경로. (2026-10-14 사장님: 2차 날짜 등록 안 됨)
     *   돈(amountEditJobId)처럼 날짜도 건별로 — 2차를 보며 날짜를 잡으면 그 2차에 쓴다. */
    var dateEditJobId by remember { mutableStateOf<Long?>(null) }
    var dateEditStart by remember { mutableStateOf<Long?>(null) }   // 그 건의 현재 예약일(달력 미리채움·축하 판단용)
    var dateEditDays by remember { mutableStateOf(1) }              // 그 건의 현재 기간
    var dateEditMinutes by remember { mutableStateOf<Int?>(null) }  // 그 건의 현재 시간(시간창 미리채움용)
    // (selectedPastJobId 제거 — 건 탭에서 하나를 고르던 상태. 이제 건마다 자기 카드가 다 보인다. 2026-10-15)
    // 빈 메모 카드를 폈나 — 빈 공백 줄이려 접어두고, 누르면 펼친다. (2026-10-07 사장님 "허전해")
    var memoExpanded by remember(customer?.id) { mutableStateOf(false) }
    // 🤝 이 고객이 협업 사장이면 전용 화면으로 분기. 세 상태(Loading/Partner/NotPartner)로 둬서,
    //   서버 답을 기다리는 동안 보통 고객 화면을 깜빡 보였다 홱 바뀌는 걸 막는다. (2026-10-27 사장님 "갑자기 전환")
    val collabUi by viewModel.collabUi.collectAsState()
    val collabPartner = (collabUi as? CollabPartnerUi.Partner)?.view
    // 전에 협업 사장으로 확인된 번호(기억) 이거나 내가 그 사람한테 공유한 적(collabAssignments) 있으면 → 로딩 중에도 보통 화면 대신 "불러오는 중".
    val knownCollabCtx = androidx.compose.ui.platform.LocalContext.current
    val knownCollab = remember(customer?.id) {
        val ph = customer?.phoneNumber
        if (ph.isNullOrBlank()) false else {
            val kprefs = (knownCollabCtx.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container.preferences
            val key = com.detailline.callfollowcrm.util.PhoneKey.of(ph)
            key.isNotBlank() && (kprefs.knownCollabPartners.contains(key) ||
                kprefs.collabAssignments.any { com.detailline.callfollowcrm.domain.collab.CollabAssignmentLine.parse(it)?.let { l -> com.detailline.callfollowcrm.util.PhoneKey.of(l.phone) == key } == true })
        }
    }
    val showCollabLoading = collabUi is CollabPartnerUi.Loading && knownCollab
    // 🗑 취소/빈 '지난 건' 삭제 확인 대상. (2026-10-06 사장님)
    var deletePastJobConfirm by remember { mutableStateOf<com.detailline.callfollowcrm.data.local.entity.JobEntity?>(null) }
    // 공유 후/해제 시 로컬 협업 기록 다시 읽게 하는 트리거(prefs 는 비반응형).
    var collabRefresh by remember(customer?.id) { mutableStateOf(0) }
    var callsExpanded by remember(customer?.id) { mutableStateOf(false) }
    var orphanRecsExpanded by remember(customer?.id) { mutableStateOf(false) }
    var nameDialogOpen by remember { mutableStateOf(false) }
    var categoryDialogOpen by remember { mutableStateOf(false) }
    // 일정·정산 카드 금액 편집 다이얼로그 — "total"(총금액) / "deposit"(계약금) / null(닫힘).
    var amountEditField by remember { mutableStateOf<String?>(null) }
    /** 금액 수정 창이 **어느 건**을 고치는지. null = 지금 건(고객 카드). (2026-09-18) */
    var amountEditJobId by remember { mutableStateOf<Long?>(null) }
    var cancelBookingConfirm by remember { mutableStateOf(false) }  // 예약 취소 확인창 (2026-08-28 사장님)
    // 시공금액 변경 시 이유 입력 다이얼로그 (oldWon, newWon). null = 닫힘. (2026-06-30 사장님)
    var amountChangeReason by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    // 📷 현장 사진 풀스크린 뷰어 — 썸네일 탭하면 **그 건의 사진 전부**를 넘겨 좌우로 넘긴다(한 장만 넘기면 스와이프할 게 없다). (2026-10-15 사장님 "스와이프가 안 돼")
    //   내 사진(OfFile)·팀 사진(OfBitmap)이 한 리스트에 섞여 들어온다 — 뷰어가 둘 다 넘기고 내 사진만 돌려 저장. null 이면 닫힘.
    var fullscreenPhotos by remember { mutableStateOf<List<com.detailline.callfollowcrm.presentation.component.ViewerPhoto>?>(null) }
    var fullscreenStart by remember { mutableStateOf(0) }
    // 팀원+사장님이 서버에 올린 현장 사진(§25). 고객 전화 알게 되면 가져옴.
    val teamPhotos by viewModel.teamPhotos.collectAsState()
    val teamNotes by viewModel.teamNotes.collectAsState()
    val collabPendingCreatedAt by viewModel.collabPendingCreatedAt.collectAsState()
    androidx.compose.runtime.LaunchedEffect(customer?.phoneNumber) {
        if (!customer?.phoneNumber.isNullOrBlank()) {
            viewModel.refreshTeamPhotos()
            viewModel.refreshTeamNotes()
        }
    }
    var celebrationVisible by remember { mutableStateOf(false) }
    // 날짜 저장 후 "시공 시간" 선택 + (첫 등록이면) 축하 보류 플래그. (2026-06-23 사장님)
    var workTimePickerOpen by remember { mutableStateOf(false) }
    var pendingCelebrate by remember { mutableStateOf(false) }
    // 날짜 범위선택(항공권식)에서 정한 시공 기간(며칠) — 시간 다이얼로그로 넘겨 함께 저장. (2026-08-01 사장님)
    var pendingWorkDays by remember { mutableStateOf(1) }
    // A/S 예약(시공과 별개, 무료) 범위선택 다이얼로그. (2026-08-01 사장님)
    var asPickerOpen by remember { mutableStateOf(false) }
    // 고른 사진 여러 장 지우기 확인 — (내 사진 id들, 팀원 사진 id들). null 이면 닫힘. (2026-09-22)
    var picksToDelete by remember { mutableStateOf<Pair<Set<Long>, Set<Long>>?>(null) }
    // 발행 이력(견적서/접수서) 다시 열람·삭제 상태. (2026-07-07 사장님)
    var reviewQuoteDoc by remember { mutableStateOf<com.detailline.callfollowcrm.presentation.screen.chat.QuoteDocData?>(null) }
    var intakeReviewDoc by remember { mutableStateOf<com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity?>(null) }
    var issuedDocToDelete by remember { mutableStateOf<com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity?>(null) }
    // 🕐 전/후 사진 고르기 시트 요청(사진 섹션 [전/후] 버튼). null = 닫힘. (2026-10-28 사장님)
    var beforeAfterReq by remember { mutableStateOf<BeforeAfterReq?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // 고객 정보 상단 탭 (2026-07-18 사장님 "크롬 탭식") — 0 일정·정산 / 1 시공접수서 / 2 블로그(준비중, 탭 숨김).
    //   이름·전화·주소는 탭 위, 메모·현장사진은 탭 내용 아래(짝) — 주소 바로 밑에 탭이 오게. (2026-09-15 사장님)
    var detailTab by remember(customer?.id) { mutableStateOf(0) }

    // composer 는 bottomBar 로 이동됨. 스크롤 영향 안 받아 bringIntoView 등 복잡한 로직 불필요.
    val scrollState = rememberScrollState()

    // ViewModel 측 toast (인라인 발송 결과 등) → 스낵바로 노출
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showBrief(it)
            viewModel.consumeToast()
        }
    }
    // 🔙 펜으로 현장 사진을 바꾼 직후 — "되돌리기" 스낵바. 누르면 원본(.orig)으로 복구. (2026-10-25 사장님 "되돌리기 있어야해")
    LaunchedEffect(markedUndo) {
        markedUndo?.let { id ->
            val r = snackbar.showBrief("표시한 사진으로 바꿨어요 · 갤러리에도 저장", actionLabel = "되돌리기")
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.undoMarkedPhoto(id)
            viewModel.clearMarkedUndo()
        }
    }

    // memoInput 초기화는 위 remember(customer?.id) 가 담당. customer 흐름으로 매번 덮어쓰면
    //   다른 필드 갱신·60초 폴링으로 customer 가 재방출될 때 입력/커서/포커스가 흔들릴 수 있어 제거. (2026-06-23 사장님)

    // memo auto-save — 사용자가 타이핑 멈춘 뒤 400ms 후 저장. 자동 debounce 패턴.
    // memoInput 이 바뀔 때마다 이전 effect 가 cancel 되므로 마지막 변경만 저장됨.
    LaunchedEffect(memoInput, customer?.id) {
        val c = customer ?: return@LaunchedEffect
        if (!shouldSaveMemo(memoDirty, memoInput, c.memo)) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        viewModel.updateMemo(memoInput)
    }
    // 화면 떠날 때 마지막 변경분이 아직 debounce 중이면 flush — 저장 못 한 채 닫히지 않게.
    //
    // 주의 — memoDirty 가드가 없으면 여기서 **메모가 지워진다.** (2026-09-16 실기에서 발견)
    //   화면이 열릴 때 customer 는 잠시 null 이라 memoInput 은 "" 로 시작한다.
    //   고객이 도착하면 remember(customer?.id) 의 키가 null -> id 로 바뀌면서
    //   **이전 DisposableEffect 가 버려지고 onDispose 가 터진다.**
    //   그 순간 c.memo = 진짜 메모 / memoInput = ""(옛 상자) 이라 서로 다르다고 판단해
    //   **빈 글자를 저장**해버렸다. 화면엔 글이 남아 있어서 눈치채기 어렵다.
    //   (나갈 때 다시 써줘서 대개 복구되지만, 그 사이 앱이 꺼지거나 백업/캘린더 동기화가
    //    돌면 빈 메모가 진짜가 된다.)
    //   -> 사람이 직접 고친 적이 있을 때만 저장한다.
    DisposableEffect(customer?.id) {
        onDispose {
            val c = customer
            if (c != null && shouldSaveMemo(memoDirty, memoInput, c.memo)) {
                viewModel.updateMemo(memoInput)
            }
        }
    }

    // 화면 열릴 때 시스템 통화기록/녹음 폴더에서 이 번호의 데이터를 자동 백필
    LaunchedEffect(customer?.id) {
        if (customer != null) viewModel.backfillFromSystem(context)
    }

    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.attachManualRecording(context, it) }
    }

    // 시트(새 시공 잡기)는 화면 위 인라인 오버레이 — 다른 시트(AppSheet)와 같은 방식. Box 가 그 자리를 준다.
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (collabPartner != null || showCollabLoading) "협업 사장" else "고객 정보",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) }
        // 하단 고정 "💬 문자 보내기" 버튼 제거 — 프로토 openCustomer 엔 없음(맨 아래 "지난 문자 보기"
        //   링크만). 2026-06-04 사장님 결정 "프로토대로".
    ) { inner ->
        val c = customer
        if (c == null) {
            Column(
                Modifier
                    .padding(top = inner.calculateTopPadding())
                    .fillMaxSize()
                    .background(TossGrayBg),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "고객 정보를 불러오는 중…",
                    modifier = Modifier.padding(24.dp),
                    color = TossTextTertiary
                )
            }
        } else if (collabPartner != null) {
            // 🤝 협업 사장 — 보통 고객 화면 대신 전용 화면(전화·메모·함께한 현장). (2026-10-27 사장님 ②)
            //   메모 편집 창(NoteEditDialog)도 이 분기가 들고 있다 — 보통 화면 것과 같은 공용 컴포넌트.
            val collabNotes by viewModel.notes.collectAsState()
            var collabEditing by remember { mutableStateOf<com.detailline.callfollowcrm.data.local.entity.CustomerNoteEntity?>(null) }
            collabEditing?.let { en ->
                com.detailline.callfollowcrm.presentation.component.NoteEditDialog(
                    note = en,
                    onClose = { collabEditing = null },
                    onSave = { t -> viewModel.editNote(en.id, t); collabEditing = null }
                )
            }
            CustomerCollabScreen(
                customer = c,
                view = collabPartner,
                topPadding = inner.calculateTopPadding(),
                notes = collabNotes,
                memoExpanded = memoExpanded,
                onToggleMemo = { memoExpanded = !memoExpanded },
                onAddNote = { viewModel.addNote(it) },
                onEditNote = { collabEditing = it },
                onTogglePin = { viewModel.togglePinNote(it.id) },
                onDeleteNote = { viewModel.deleteNote(it.id) },
                onEditName = { nameDialogOpen = true }
            )
        } else if (showCollabLoading) {
            // 🤝 전에 협업 사장으로 확인된 사람 — 서버 답(collabUi)이 오기 전에 보통 고객 화면을 깜빡 보이지 않게
            //   "불러오는 중"만 보인다. 답 오면 위 Partner 분기(또는 아니면 아래 보통 화면)로. (2026-10-27 사장님 "갑자기 전환")
            Column(
                Modifier.padding(top = inner.calculateTopPadding()).fillMaxSize().background(TossGrayBg),
                verticalArrangement = Arrangement.Center
            ) {
                Text("협업 현장 불러오는 중…", modifier = Modifier.padding(24.dp), color = TossTextTertiary)
            }
        } else {
        // ⚠️ 여기 있던 return@Scaffold 를 뺐다 — Composable 안에서 빠져나가면
        //    빈→로드 전환 때 슬롯테이블이 어긋나 화면이 꺼지거나 빈 화면이 된다.
        //    통계 탭이 그것 때문에 꺼졌다(2026-09-24). if/else 로 감싼다. 내용은 그대로.

        Column(
            Modifier
                // ⬇️ top 만 받는다. inner 를 통짜로 쓰면 **내비게이션 바 자리를 두 번 비운다**.
                //   이 화면은 하단 탭바(RingTabBar) 위에 여렸고, 그 탭바가 이미 navigationBarsPadding 을 가지고 있다.
                //   근데 여기 Scaffold 는 bottomBar 가 없어 기본값으로 systemBars 하단 인셋(3버튼 바 ≈ 48dp)을
                //   또 넘겨줘서, 마지막 카드 밑에 **빈 회색 띄**가 생겼다.
                //   (2026-09-16 사장님 "여기 여백을 이렇게 남긴 이유가뭐야~?")
                //   홈·일정·정산·통계 탭은 원래부터 top 만 받고 있었다 — 이 화면만 혼자 달람다.
                .padding(top = inner.calculateTopPadding())
                .fillMaxSize()
                // imePadding() 을 verticalScroll 전에 둬서, 키보드가 올라오면 스크롤 영역이
                // 자동으로 축소 → 포커스된 인라인 composer 가 키보드 위로 자동 정렬됨.
                .keyboardPadding()
                .background(TossGrayBg)
                .verticalScroll(scrollState)
                // bottom 을 크게 둬서 키보드 위로 입력칸이 바짝 붙지 않고 숨 쉴 공간 확보.
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 20.dp),
            // 카드 간 여백 12→10 — 허전함 줄이기. (2026-10-07 사장님)
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. 프로토 cd-card 헤더 — heat 점 + 이름(크게) + [변경] / 전화번호 + [분류 ›] + 📞.
            val categories by viewModel.categories.collectAsState()
            val currentCat = categories.firstOrNull { it.id == c.categoryId }
            // 이름을 모르는 손님이면 번호가 이름 자리에 들어가는데, 그 자리가 좁아 "010-484…" 로 잘리고
            //   **바로 아랫줄에 같은 번호가 또** 나왔다. → 이름 없으면 제목에 번호를 온전히, 아랫줄은 뺀다.
            //   (2026-09-20 사장님)
            val hasName = !c.name.isNullOrBlank()
            val headerName = if (hasName) c.name!! else PhoneNumberFormatter.format(c.phoneNumber)
            val headerCtx = androidx.compose.ui.platform.LocalContext.current
            // ── 이 사람은 고객인가 — 채팅에서 물어본 그 답을 **여기서 바꾼다**. (2026-09-17 사장님 C안)
            //   prefs·상태를 이름줄보다 먼저 계산해 둔다 — 이미 '고객'으로 확정된 사람이면
            //   이름 옆 작은 초록 배지로 접기 때문(큰 토글 줄을 안 그린다). (2026-10-07 사장님)
            val headerPrefs = remember(headerCtx) {
                (headerCtx.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication)
                    .container.preferences
            }
            var nonCustomer by remember(c.id) { mutableStateOf(headerPrefs.isNonCustomer(c.phoneNumber)) }
            // '고객?'에 답했나 — prefs 는 반응형이 아니라 **눌러도 바로 안 접혔다**. state 로 들고 와 즉시 반영. (2026-10-07)
            var kindAnswered by remember(c.id) { mutableStateOf(headerPrefs.isCustomerAsked(c.phoneNumber)) }
            // '고객?'에 이미 답해 **고객으로 확정**된 사람이면 큰 토글 줄 → 작은 배지로.
            val answeredCustomer = kindAnswered && !nonCustomer
            Column(
                Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(17.dp)
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    // 🔧 이름+「고객」배지 = **왼쪽 한 그룹**(weight 1)으로 묶는다. 이 그룹이 남는 공간을 전부 먹어
                    //   상태 딱지·수정 버튼은 항상 **오른쪽 끝**에 붙는다. (2026-10-15 사장님 "텍스트 정렬 깨진거")
                    //   ❌ 전엔 이름 Text 와 그 뒤 Spacer 에 **weight 를 둘 다** 줘서 남는 공간을 반반씩 나눴다 →
                    //      이름이 짧으면 배지가 가운데로 뜨고 오른쪽 딱지가 끝에 안 붙었다(정렬 깨짐).
                    androidx.compose.foundation.layout.Row(
                        Modifier.weight(1f),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        // 고객온도 점 제거 (2026-06-14 사장님: 온도 더 안 씀 — 통화 후 카드에서 뺀 것과 일관).
                        Text(
                            headerName, fontSize = if (hasName) 22.sp else 20.sp,
                            fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                            letterSpacing = (-0.6).sp, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            // 이름이 길면 줄여서(…) — 그룹 안에서 배지를 밀어내지 않게 fill=false.
                            //   (2026-09-20 사장님 / 2026-10-07 이름 옆 「고객」 배지 추가)
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // 기본이 '고객' — 이름 옆 작은 초록 배지. 비고객일 때만 숨긴다(깔끔하게). (2026-10-15 사장님)
                        if (!nonCustomer) {
                            Spacer(Modifier.width(6.dp))
                            com.detailline.callfollowcrm.presentation.screen.customer.ConfirmedCustomerBadge()
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // 상태 딱지(예약/잔금미수/완료 등) — 고객관리와 같은 계산, 어디서나 따라다니게. (2026-09-03 사장님)
                    com.detailline.callfollowcrm.presentation.component.CustomerStatusTag(
                        com.detailline.callfollowcrm.presentation.component.customerStatusOf(c)
                    )
                    Spacer(Modifier.width(8.dp))
                    // 버튼은 둥근 네모. 알약은 '고르는 것'(칩)에만. (2026-09-20 사장님)
                    androidx.compose.foundation.layout.Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(TossGrayBg)
                            .clickable { nameDialogOpen = true }.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Edit, null, tint = TossTextTertiary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (hasName) "수정" else "이름 넣기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    // 제목이 이미 번호면 여기서 또 보여주지 않는다 (같은 번호 두 번).
                    Text(
                        if (hasName) PhoneNumberFormatter.format(c.phoneNumber)
                        else if (currentCat == null) "분류 없음" else "",
                        fontSize = 14.sp, color = if (hasName) TossTextSecondary else TossTextTertiary
                    )
                    Spacer(Modifier.weight(1f))
                    androidx.compose.foundation.layout.Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(TossGrayBg)
                            .clickable { categoryDialogOpen = true }.padding(horizontal = 13.dp, vertical = 6.dp)
                    ) {
                        Text(
                            currentCat?.let { (it.emoji?.let { e -> "$e " } ?: "") + it.name + " ›" } ?: "분류 ›",
                            fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(TossGrayBg)
                            .clickable { dialPhone(headerCtx, c.phoneNumber) },
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Phone, "전화", tint = TossTextSecondary, modifier = Modifier.size(17.dp))
                    }
                }

                // ── 이 사람은 고객인가 — 채팅에서 물어본 그 답을 **여기서 바꾼다**. (2026-09-17 사장님 C안)
                //   왜 대화방이 아니라 여기인가: 대화방에 상태 띠를 계속 띄우면 화면만 먹는다
                //   ("계속 고객아님으로 해두셨어요가 나오면 사용성과 ui를 헤치는거아닌가").
                //   분류·주소·금액이 다 모인 이 카드가 '이 사람 설정' 자리다.
                //   전엔 한 번 [고객 아님] 을 누르면 질문이 다시 안 떠서 **바꿀 방법이 아예 없었다.**
                //   (prefs·nonCustomer 는 이름줄 위에서 미리 계산 — 2026-10-07. UI 는 CustomerKindSection 한 곳)
                CustomerKindSection(answeredCustomer = answeredCustomer, nonCustomer = nonCustomer) { isNon ->
                    nonCustomer = isNon
                    kindAnswered = true   // 눌렀으니 '답함' — 다음 리컴포지션에서 바로 작은 배지로 접힌다.
                    headerPrefs.answerCustomerAsk(c.phoneNumber, isNon)
                }
            }

            // 1.2 고객 페르소나 — 숨김 (2026-09-01 사장님 "많이 안 씀, 일단 숨김"). 재노출하려면 아래 2줄 주석 해제.
            //   cowork prepare-reply 가 자동 생성(Haiku 4.5, 24h cache). 안드는 cache 조회만, 없으면 숨김.
            // val persona by viewModel.persona.collectAsState()
            // persona?.let { p -> if (!p.isEmpty || p.stale) PersonaCard(p) }

            // 1.3 현장 주소 — 표시 우선순위 (2026-05-28 사장님 결정):
            //   1) customer.address (사장님 수동 등록, DB v15) — 신뢰 최우선
            //   2) extractedAddress (메시지 자동 추출) — fallback
            //   3) 빈 상태 — "눌러서 등록" 안내
            //   탭 동작: 어느 상태든 AddressEditDialog 띄움 (입력/수정 가능).
            //   탭 길게 누름 = 복사 (기존 UX 보존) — 추후 BottomSheet 로 전환 가능.
            // 🧾 지금 보고 있는 '건'을 여기서 한 번만 계산 — 주소 카드·일정정산 카드가 같은 건을 본다.
            //   (2026-10-07 2차 주소 분리) 돈과 같은 규칙: 건이 있으면 반드시 그 건(대표 건)을 가리킨다.
            val allJobsForTabs by viewModel.allJobs.collectAsState()
            val repDay = c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) }
            val repJobId = allJobsForTabs.firstOrNull { j ->
                j.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } == repDay && repDay != null
            }?.id
            // 대표(기본) 건 — 전엔 탭에서 안 고르면 이 건의 카드 한 장이 떴다. 이제 모든 건이 카드로 다 보이고,
            //   **고객 단위**인 것(팀원 사진·메모·서버 사진 되살리기·A/S 줄·옛 사진)만 이 건 카드에 붙는다. (2026-10-15)
            val primaryJobId = repJobId
                ?: com.detailline.callfollowcrm.domain.job.RepresentativeJob.pick(
                    allJobsForTabs.sortedBy { it.scheduledWorkDate ?: 0L },
                    DateTimeUtils.startOfDay(System.currentTimeMillis())
                )?.id
            val hasJobs = allJobsForTabs.isNotEmpty()

            // ⏸️ 상담함 [보류] 카드 「일정 잡기」로 들어왔으면 날짜 창부터 — **딱 한 번**(rememberSaveable: 회전해도 다시 안 뜸).
            //   🔴 건은 DB 에서 한 번 읽는다(allJobsOnce) — allJobs 의 첫 값은 빈 목록이라 그걸로 고르면 '건 없음' 경로(고객 카드)로
            //   가서 새 건이 하나 더 생기고 **계약금이 두 건에 찍힌다**(§13④). 보류하며 날짜만 뺀 그 건(날짜 없음·돈 있음)에 써야 한다.
            var autoOpened by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(autoOpenDatePicker, c.id) {
                if (!autoOpenDatePicker || autoOpened) return@LaunchedEffect
                autoOpened = true
                val jobs = runCatching { viewModel.allJobsOnce() }.getOrDefault(emptyList())
                val target = jobs.firstOrNull { it.scheduledWorkDate == null && !jobFolded(it) }
                dateEditJobId = target?.id; dateEditStart = null
                dateEditDays = (target?.scheduledWorkDays ?: 1).coerceAtLeast(1); dateEditMinutes = target?.scheduledWorkMinutes
                addingNewJob = false; datePickerOpen = true
            }

            // 발행 문서(견적서·접수서) — 탭을 없애고 **각 차수 카드 안**으로. (2026-10-14 사장님)
            //   시공접수서는 고객 번호에만 묶여 있어 차수 꼬리표가 없다 → 시공예정일(workDateMs)로 그 건에 맞춘다.
            val issuedDocs by viewModel.issuedDocs.collectAsState()
            val intakeByToken by viewModel.intakeByToken.collectAsState()

            // 💰 상단 요약 스트립 — 받을 돈(크게·빨강) + 시공 N건/합계. (2026-10-14 사장님 재설계 3단계)
            //   "받을 돈"은 모든 건의 미수 합 → 홈·정산과 **같은 계산**(SettlementCalc.book)으로 숫자가 어긋나지 않는다(§12).
            run {
                val summaryBook = com.detailline.callfollowcrm.domain.settlement.SettlementCalc
                    .book(listOf(c), allJobsForTabs).filter { it.customerId == c.id }
                val receivable = summaryBook.sumOf { it.calc.outstanding }
                val totalSum = summaryBook.sumOf { it.calc.total }
                val activeJobs = allJobsForTabs.count { !jobCancelled(it) && it.scheduledWorkDate != null }
                val jobCount = if (activeJobs > 0) activeJobs else (if (c.scheduledWorkDate != null) 1 else 0)
                // 돈이 하나도 안 적힌 고객(상담만)은 숨긴다 — 허전/어수선 방지. (사장님 "중요한 것만")
                //   🔽 시공이 **2건 이상일 때만** 띄운다. 1건이면 바로 아래 건 카드가 같은 금액(잔금)을 또 보여줘
                //      중복·지저분해진다(2026-10-15 사장님). 띠의 쓸모 = 여러 건 잔금을 합친 '총 받을 돈'은 여기만 있다.
                if (totalSum > 0L && jobCount >= 2) {
                    Spacer(Modifier.height(10.dp))
                    TossCard {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (receivable > 0L) "아직 받을 돈" else "받을 돈",
                                    fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TossTextTertiary
                                )
                                Spacer(Modifier.height(2.dp))
                                if (receivable > 0L) {
                                    Text(
                                        manwonLabel(receivable),
                                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                                        color = AppTheme.colors.unpaidText, letterSpacing = (-0.5).sp
                                    )
                                } else {
                                    Text(
                                        "다 받았어요",
                                        fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                                        color = AppTheme.colors.doneText, letterSpacing = (-0.5).sp
                                    )
                                }
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text("시공 ${jobCount}건", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                                Spacer(Modifier.height(3.dp))
                                Text("합계 " + manwonLabel(totalSum), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                            }
                        }
                    }
                }
            }

            val extractedAddress by viewModel.extractedAddress.collectAsState()
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            val ctx = LocalContext.current
            // 🔴 건이 있으면 주소는 **그 건 카드 안**(JobDetailCard 가 job.address 를 본다). 여기(독립 주소 카드)는
            //   건이 없는 상담 단계만 그리므로 고객 주소다. (2026-10-07 건별 주소 / 2026-10-15 건마다 카드)
            val manualAddress = c.address?.takeIf { it.isNotBlank() }
            // 🔴 표시는 **사장님이 확인해 저장한 주소만**. (2026-09-16 사장님 "끄기로 해줘")
            //   예전엔 `manualAddress ?: extractedAddress` 였고, 아래 LaunchedEffect 가 감지된 주소를
            //   **묻지도 않고 저장**까지 했다. 협업 중이면 그 주소가 상대 사장님 폰에까지 전파됐다.
            //   주소를 잘못 잡으면 사장님이 엉뚱한 현장으로 간다 → 확인 없는 저장은 위험하다.
            //
            //   ⚠️ 그렇다고 그냥 지우면 옛 문제가 되살아난다(2026-06-18: 고객상세엔 주소가 보이는데
            //      일정엔 "주소 미입력" — 감지만 하고 저장은 안 돼 생긴 불일치).
            //   → 감지된 주소는 **제안 카드**로 보여주고, 사장님이 [이 주소로 등록]을 눌러야 저장된다.
            //      그러면 보이는 것과 저장된 것이 항상 같다.
            val displayAddr = manualAddress
            // 제안을 이번 화면에서 닫았는지(고객별). "아니에요" 누르면 이 화면에선 다시 안 보인다.
            // "아니에요" 는 **폰에 적어둔다.** 화면 안에서만 기억하면 채팅 갔다 오는 순간 잊어버려
            //   같은 주소가 또 뜬다. (2026-09-17 사장님 보고)
            val detailPrefs = remember(context) {
                (context.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication)
                    .container.preferences
            }
            var addrSuggestDismissed by remember(c.id, extractedAddress) {
                mutableStateOf(
                    extractedAddress?.let { detailPrefs.isAddressSuggestDismissed(c.id, it) } ?: false
                )
            }
            // 주소 추천은 **고객한테만.** (2026-09-17 사장님:
            //   "첫문자때 사용자한테 묻잖아 이사람이 고객이냐 아니냐. 고객이면 발동하라는거야")
            //   첫 문자 때 이미 상담함(고객) / 문자함(택배·광고·알림)으로 갈라둔 게 있으니 그걸 쓴다.
            //   택배 문자에서 뽑은 배송지를 시공 현장으로 물어보던 게 이걸로 없어진다.
            //   카테고리(택배·일당 같은 이름표)로 판단하지 않는다 — 새 손님은 아직 이름표가 없다.
            var isGeneralThread by remember(c.id) { mutableStateOf(false) }
            LaunchedEffect(c.id, c.phoneNumber) {
                val app0 = context.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication
                isGeneralThread = runCatching {
                    // ① 사장님이 채팅에서 "고객 아님" 이라고 **답한** 번호 (사장님이 말한 바로 그것)
                    app0.container.preferences.isNonCustomer(c.phoneNumber) ||
                        // ② 문자함(택배·광고·알림)으로 갈린 번호
                        app0.container.threadBucketRepository.isGeneral(c.phoneNumber)
                }.getOrDefault(false)
            }
            var showAddressDialog by remember { mutableStateOf(false) }
            /** 주소 수정 창이 **어느 건**을 고치는지. null = 고객(상담 단계) 경로. 돈·날짜처럼 주소도 건별로. (2026-10-15) */
            var addressEditJobId by remember { mutableStateOf<Long?>(null) }
            /**
             * 🔧 **눌러봐도 아무 일 안 생기게.** (2026-09-27 사장님)
             *   "처음 보면 뭔지 나처럼 다 눌러볼 것 같아. 아는 사람에겐 직관적이지만
             *    처음 하는 사람한테는 나랑 같은 경험을 할 것 같은데?"
             *   처음 보는 사람은 **뜻을 알려고 누른다.** 누르는 즉시 데이터가 바뀌면 그건 함정이다.
             *   A/S 찍기는 가끔 있는 일이라, 한 번 더 누르는 값이 싸다.
             */
            var asPendingConfirm by remember { mutableStateOf(false) }
            if (asPendingConfirm) {
                androidx.compose.material3.AlertDialog(
                    tonalElevation = 0.dp,
                    onDismissRequest = { asPendingConfirm = false },
                    title = { Text("A/S 명단에 올릴까요?", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                    text = {
                        Text(
                            "상담함 맨 위에 [A/S] 칸이 생기고 이 손님이 거기 들어가요.\n날짜를 못 잡아도 잊지 않으려고 올려두는 명단이에요.\n\n나중에 다녀오시면 A/S 줄을 눌러 「다녀왔어요」 하시면 돼요.",
                            style = AppType.body, color = TossTextSecondary
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { asPendingConfirm = false; viewModel.markAsPending() }) {
                            Text("올릴게요", color = TossBlue, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { asPendingConfirm = false }) {
                            Text("그냥 둘게요", color = TossTextSecondary)
                        }
                    },
                    containerColor = Color.White
                )
            }
            // 🔧 [A/S 다녀왔어요] 확인 — 누르면 태그가 사라지고 쪽지 한 장이 남는다. (2026-09-27)
            var asDoneConfirm by remember { mutableStateOf(false) }
            if (asDoneConfirm) {
                androidx.compose.material3.AlertDialog(
                    tonalElevation = 0.dp,
                    onDismissRequest = { asDoneConfirm = false },
                    title = { Text("A/S 다녀오셨나요?", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                    text = {
                        Column {
                            Text(
                                "명단에서 빠지고, 고객 메모에 「… A/S 처리 완료」가 남아요.",
                                fontSize = 13.5.sp, color = TossTextSecondary, lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            // 🔧 **잘못 누른 길.** (2026-09-27 사장님 "as해드릴곳을 눌렀는데 취소할수가없네")
                            //   이게 없으면 잘못 누른 분이 「다녀왔어요」를 눌러야 빠져나가고,
                            //   그러면 **가지도 않은 A/S 가 다녀온 것으로 메모에 적힌다.**
                            Text(
                                "잘못 눌렀어요 · 기록 없이 명단에서만 빼기",
                                style = AppType.label, color = TossTextTertiary,
                                modifier = Modifier.fillMaxWidth()
                                    .clip(AppShape.sm)
                                    .clickable { asDoneConfirm = false; viewModel.clearAsPending() }
                                    .padding(vertical = AppSpace.s8),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { asDoneConfirm = false; viewModel.markAsDone() }) {
                            Text("다녀왔어요", color = TossBlue, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { asDoneConfirm = false }) {
                            Text("아직이에요", color = TossTextSecondary)
                        }
                    },
                    containerColor = Color.White
                )
            }
            // ⏸️ 일정 보류 창 — CustomerHoldDialog 한 벌(찍기·메모 고치기·풀기). 카드는 host.onHold 로 열기만. (2026-10-16)
            var holdDialog by remember { mutableStateOf(false) }
            if (holdDialog) CustomerHoldDialog(c = c, viewModel = viewModel, onClose = { holdDialog = false })
            // 📝 쪽지를 꼬 눌러 「고쳐 적기」를 고를면 여기에 들어온다. (2026-09-27)
            var editingNote by remember {
                mutableStateOf<com.detailline.callfollowcrm.data.local.entity.CustomerNoteEntity?>(null)
            }
            editingNote?.let { en ->
                com.detailline.callfollowcrm.presentation.component.NoteEditDialog(
                    note = en,
                    onClose = { editingNote = null },
                    onSave = { t -> viewModel.editNote(en.id, t); editingNote = null }
                )
            }

            // 👤 이 손님 메모 — **쪽지로 쌓인다.** (2026-09-27 사장님)
            //   전엔 한 덩어리 글이라 **언제 적었는지 알 수 없었다.**
            //   사장님이 적는 건 사건이다 — 「오늘 2시로 바뀜」. 사건은 시각이 반이다.
            //   📌 계좌·비번처럼 늘 봐야 하는 건 맨 위에 못 박는다.
            val custNotes by viewModel.notes.collectAsState()
            // 프로토 `.memo` — **항상 한 줄**(고정/최근 메모 미리보기 + 개수 + ›). 탭하면 펼쳐 쪽지 목록·입력.
            //   전엔 메모가 하나라도 있으면 목록 전체 + 안내문이 늘 펼쳐져 있었다. (2026-10-15 Fable §4-6, 사장님 "쭉 적용")
            run {
                val openMemo: (() -> Unit)? = if (memoExpanded) null else ({ memoExpanded = true })
                TossCard(onClick = openMemo) {
                    Column {
                        CdFoldRow(
                            Icons.Filled.Person, "이 고객 메모", custNotes.size, memoExpanded,
                            emptyAction = if (custNotes.isEmpty()) "＋ 적기" else null
                        ) { memoExpanded = !memoExpanded }
                        // 📌 고정 메모가 있으면 그것, 없으면 맨 위(최근) 메모 한 줄. 공용(§12) — 현장 메모와 같은 모양.
                        if (!memoExpanded) MemoPreviewLine(custNotes)
                        if (memoExpanded) {
                            Spacer(Modifier.height(8.dp))
                            com.detailline.callfollowcrm.presentation.component.NoteList(
                                notes = custNotes,
                                onAdd = { viewModel.addNote(it) },
                                onEdit = { editingNote = it },
                                onTogglePin = { viewModel.togglePinNote(it.id) },
                                onDelete = { viewModel.deleteNote(it.id) },
                                placeholder = "예) 계좌이체 선호, 오후 3시 이후 통화"
                            )
                        }
                    }
                }
            }


            // ②안: 건이 있으면 주소는 **그 건 카드 안**에만 있는다("한 건=한 카드"). (2026-10-07 사장님)
            //   ⚠️ 전엔 `detailTab == 0 && shownJob != null` 이라, 협업·접수서 탭을 누르면 이 옛 독립
            //      주소카드가 위로 **튀어나와** UI 가 확 바뀌었다(2026-10-08 사장님 "협업 누르면 예전 ui로 바뀌네").
            //      → 건이 있으면 **어느 탭에서도** 독립카드를 안 띄운다(주소는 일정·정산 건 카드에만).
            //      상담 단계(건 없음)에선 그대로 이 카드가 유일한 주소 표시 — 탭 바꿔도 안 변한다.
            val addrInJobCard = hasJobs
            if (addrInJobCard) {
                // 주소는 '일정·정산' 그 건 카드 안에 그린다(아래). 여기선 안 그림(상담 단계만 여기).
            } else if (displayAddr != null) {
                // 프로토 .addr-card — 그라데이션 + 주소 + [길찾기 시작] 큰 파란 버튼.
                val addrInteraction = remember { MutableInteractionSource() }
                Column(
                    Modifier.fillMaxWidth()
                        .pressScale(addrInteraction)
                        .tossCardShadow(RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(AppTheme.colors.primaryBg, Color.White)))
                        .border(1.5.dp, Color(0xFFE2EDFD), RoundedCornerShape(18.dp))
                        .clickable(interactionSource = addrInteraction, indication = null) { addressEditJobId = null; showAddressDialog = true }
                        .padding(17.dp)
                ) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CdTitleIcon(Icons.Filled.Place, "blue")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "현장 주소" + (if (c.scheduledWorkDate != null) " · 예약 고객" else ""),
                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary
                        )
                        Spacer(Modifier.weight(1f))
                        // 주소 원탭 복사 — 지도·문자에 붙이기 편하게. (2026-09-01 사장님)
                        if (displayAddr.isNotBlank()) {
                            Text("복사", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossBlue,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { ctx.copyToClip("주소", displayAddr) }
                                    .padding(horizontal = 9.dp, vertical = 4.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        androidx.compose.material3.Icon(
                            if (manualAddress != null) Icons.Default.Edit else Icons.Default.Add, null,
                            tint = TossBlue, modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(displayAddr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, lineHeight = 22.sp)
                    if (manualAddress == null) {
                        Spacer(Modifier.height(3.dp))
                        com.detailline.callfollowcrm.presentation.component.HelpText("문자에서 자동 인식 · 눌러서 확정/수정")
                    }
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(13.dp))
                            .background(TossBlue).clickable { startNavToAddress(ctx, displayAddr) }.padding(vertical = 13.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(Icons.Default.Navigation, null, tint = Color.White, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("길찾기 시작", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (!extractedAddress.isNullOrBlank() && !addrSuggestDismissed && !isGeneralThread) {
                // 문자에서 주소를 봤을 때 — **제안만** 한다. 누르기 전엔 저장 안 됨. (2026-09-16 사장님)
                TossCard {
                    Column {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Place, null, tint = TossTextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("문자에서 이런 주소를 봤어요", fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(extractedAddress!!, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = TossTextPrimary, lineHeight = 21.sp)
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(TossGrayBg)
                                    .clickable {
                                        addrSuggestDismissed = true
                                        extractedAddress?.let { detailPrefs.dismissAddressSuggest(c.id, it) }
                                    }.padding(vertical = 11.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) { Text("아니에요", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
                            Spacer(Modifier.width(9.dp))
                            androidx.compose.foundation.layout.Box(
                                Modifier.weight(2f).clip(RoundedCornerShape(11.dp)).background(TossBlue)
                                    .clickable {
                                        if (primaryJobId != null) viewModel.setJobAddress(primaryJobId, extractedAddress)
                                        else viewModel.updateManualAddress(extractedAddress)
                                    }
                                    .padding(vertical = 11.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) { Text("이 주소로 등록", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("직접 고치려면 여기를 누르세요", fontSize = 11.sp, color = TossTextTertiary,
                            modifier = Modifier.clickable { addressEditJobId = null; showAddressDialog = true })
                    }
                }
            } else {
                TossCard(onClick = { addressEditJobId = null; showAddressDialog = true }) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        // 전엔 새빨간 압정 이모지(22sp)가 카드에서 제일 튀었다 — 담긴 건 "주소 없음" 인데.
                        CdTitleIcon(Icons.Filled.Place, "blue")
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("현장 주소", style = MaterialTheme.typography.labelSmall, color = TossTextTertiary)
                            Spacer(Modifier.height(2.dp))
                            Text("아직 주소가 없어요 · 상담 단계예요", style = MaterialTheme.typography.bodyMedium, color = TossTextSecondary)
                            Spacer(Modifier.height(2.dp))
                            Text("눌러서 직접 등록하거나, 고객 문자에 주소가 있으면 자동 채워져요.",
                                style = MaterialTheme.typography.labelSmall, color = TossTextTertiary)
                        }
                        Text("＋", fontSize = 16.sp, color = TossBlue)
                    }
                }
            }

            if (showAddressDialog) {
                // 창은 화면에 **한 벌** — 어느 건 카드에서 열었든 addressEditJobId 가 가리키는 그 건의 주소를 보여주고 거기에 쓴다.
                val addrJob = addressEditJobId?.let { id -> allJobsForTabs.firstOrNull { it.id == id } }
                val dlgManual = (addrJob?.address?.takeIf { it.isNotBlank() } ?: c.address)?.takeIf { it.isNotBlank() }
                AddressEditDialog(
                    currentAddress = dlgManual,
                    extractedSuggestion = extractedAddress?.takeIf { it != dlgManual },
                    onSave = { addr ->
                        // 🔴 주소도 **그 건에만** 저장 — 2차 주소가 1차/고객 공유칸(→미러)으로 새지 않게. (2026-10-07)
                        val jid = addressEditJobId
                        if (jid != null) viewModel.setJobAddress(jid, addr)
                        else viewModel.updateManualAddress(addr)
                        showAddressDialog = false
                    },
                    onCopyExisting = dlgManual?.let { existing ->
                        {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(existing))
                            android.widget.Toast.makeText(
                                ctx, "주소가 복사됐어요", android.widget.Toast.LENGTH_SHORT
                            ).show()
                            showAddressDialog = false
                        }
                    },
                    onDismiss = { showAddressDialog = false }
                )
            }

            // ── 상단 탭 제거 (2026-10-14 사장님: "시공접수서 탭 굳이 필요할까") ──
            //   협업 탭(2026-10-14)·접수서 탭을 차례로 없애 '일정·정산' 하나만 남았다 → 탭 줄 자체를 뺀다.
            //   접수서(발행 문서)는 아래 각 차수 카드 안으로. detailTab 은 0 고정(==1 블록은 아래에서 제거).
            //   (블로그 ==2 lockcard 블록은 원래부터 도달 불가 — 코드만 유지.)


            // 1.5 (제거) AI 대화 요약 — 챗스크린에 이미 있어 중복이라 고객 상세에선 뺌. (2026-07-18 사장님)

            // 2-0. 건(件) 여러 개 — 한 고객의 시공이 여러 번일 때. (2026-09-17 B안 탭 → 2026-10-15 건마다 카드)
            //   이 고객의 **모든 건**을 다 그린다 — 지난 것도, 앞으로 잡힌 것도. (2026-09-17)
            // (allJobsForTabs·repDay·repJobId·primaryJobId 는 위 주소 카드 앞에서 한 번만 계산 — 같은 건을 본다.)
            val otherJobs = allJobsForTabs.filter { it.id != repJobId }
            // 「시공 건」 머리·＋새 시공·"N차" 꼬리표는 **시공이 둘 이상일 때만**. (2026-09-18 확정 프로토 artifact/4ZvDfUfxDAQU8uNNvQQ1h1)
            //   "보통 손님은 시공을 한 번만 받는다. 그런 손님 화면에 '1차'라는 말과 탭 줄을 넣으면
            //    100명 중 95명한테 쓸데없는 줄 하나를 얹는 것" — 그게 '지저분하다'의 정체.
            //   1건일 때의 두 번째 시공 입구는 **맨 아래 조용한 링크**(＋ 시공 하나 더 잡기)로 옮겼다.
            val showJobBar = otherJobs.isNotEmpty()
            // (차수 꼬리표 "2차 " 는 카드 안에서 jobNthOf 로 — 메모·사진 제목이 같이 쓴다. 2026-09-19 사장님 / 2026-10-15 이동)

            // ⑤ **마무리 뒤에 문자에서 주소가 잡히면 다음 현장 것으로 묻는다.** (2026-09-18 확정 프로토 `.catch`)
            //   "1차 현장이 잔금 확인이 됐으면 마무리로 봄. 그 이후 대화에서 주소가 나오면
            //    2차 현장의 주소로 캐치 묻기" — 사장님 9/17.
            //   · 주소가 **빈 진행 중 건**이 있으면 그 건 주소인지 묻고
            //   · 없으면 "새 현장 주소인가요?" → 수락하면 **새 건**이 생기며 주소가 들어간다.
            //   이미 어느 건이 그 주소를 갖고 있으면 묻지 않는다(중복 질문 방지).
            run {
                val caught = extractedAddress?.takeIf { it.isNotBlank() }
                val closedJobs = allJobsForTabs.filter { jobClosed(it) }
                val alreadyUsed = caught != null && allJobsForTabs.any {
                    it.address?.trim() == caught.trim()
                } || (caught != null && c.address?.trim() == caught.trim())
                // 주소가 빈 건이 있으면 — **완료(접힌)됐어도** — 새 시공 말고 그 건을 먼저 채운다. 열린 건을 우선. (2026-10-28 사장님)
                //   "1차에 주소가 안 적혀 있으면 새시공 아니라 1차 빈 주소에 들어가야 함."
                val emptyAddrJobs = allJobsForTabs.filter { it.address.isNullOrBlank() && !jobCancelled(it) }
                val fillTarget = (emptyAddrJobs.filter { !jobFolded(it) }.ifEmpty { emptyAddrJobs })
                    .minByOrNull { it.scheduledWorkDate ?: Long.MAX_VALUE }
                val show = detailTab == 0 && caught != null && !addrSuggestDismissed &&
                    !isGeneralThread && closedJobs.isNotEmpty() && !alreadyUsed
                if (show && caught != null) {
                    val whyText = if (showJobBar)
                        closedJobs.joinToString("·") { "${jobNthOf(allJobsForTabs, it)}차" } + "는 잔금까지 받아서 마무리됐어요."
                    else "이번 시공은 잔금까지 받아서 마무리됐어요."
                    val askText = if (fillTarget != null)
                        "${jobNthOf(allJobsForTabs, fillTarget)}차 현장 주소인가요? (주소가 비어 있어요)"
                    else "새 현장 주소인가요?"
                    val yesText = if (fillTarget != null)
                        "${jobNthOf(allJobsForTabs, fillTarget)}차 주소로 등록" else "새 시공으로 잡기"
                    Column(
                        Modifier.fillMaxWidth()
                            .tossCardShadow(RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(1.5.dp, TossBlue, RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Text("방금 문자에서 이런 주소를 봤어요",
                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                        Spacer(Modifier.height(6.dp))
                        Text(caught, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = TossTextPrimary, lineHeight = 21.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("$whyText $askText", fontSize = 13.sp, color = TossTextSecondary, lineHeight = 19.sp)
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(TossGrayBg)
                                    .clickable {
                                        addrSuggestDismissed = true
                                        detailPrefs.dismissAddressSuggest(c.id, caught)
                                    }.padding(vertical = 11.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) { Text("아니에요", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
                            Spacer(Modifier.width(9.dp))
                            androidx.compose.foundation.layout.Box(
                                Modifier.weight(1.4f).clip(RoundedCornerShape(11.dp)).background(TossBlue)
                                    .clickable {
                                        // (고른 건으로 옮겨 가던 selectedPastJobId 는 없어졌다 — 모든 건이 카드로 다 보인다. 2026-10-15)
                                        if (fillTarget != null) {
                                            viewModel.setJobAddress(fillTarget.id, caught)
                                        } else {
                                            viewModel.addJobWithAddress(caught)
                                        }
                                    }.padding(vertical = 11.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) { Text(yesText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            // 2. 🏗️ 시공 건 — **건마다 자기 카드**(프로토 artifact/32G2BkhhFkdE9J5Wj8u4JC). (2026-10-15 사장님)
            //   전엔 작은 탭 줄(JobTabsRow)에서 하나를 고르면 그 아래 상세 카드 **한 장**만 보였다 →
            //   '현재 카드'가 완료 건 탭보다 아래로 밀렸다. 이제 탭 줄 없이:
            //     · 진행 중인 건 = **펼쳐진 카드**, 최신(높은 차수)이 맨 위
            //     · 완료·취소한 건 = **접힌 줄**, 맨 아래 — 누르면 그 자리에서 같은 상세가 펼쳐진다
            //   카드 한 장(머리+주소·길찾기·예약·잔금·메모·사진·foot)은 CustomerDetailJobCard.kt 의 JobDetailCard **하나**.
            //   창(날짜·금액·주소·A/S·취소·삭제·사진)은 이 화면에 한 벌 — 카드는 어느 건인지만 콜백으로 넘긴다(§12).
            // 🔎 맨 처음 건(1차) — 날짜 없는/안 맞는 떠돌이 문서(견적서 등)를 이 건 카드에 모은다. (2026-10-15 사장님)
            //   차수와 같은 규칙(JobOrder·취소/빈 건 제외)으로 날짜가 가장 이른 건. 없으면(건 없음/전부 취소) null → '이 고객 문서'로.
            val earliestJobId = remember(allJobsForTabs) {
                com.detailline.callfollowcrm.util.JobOrder.order(
                    allJobsForTabs.filterNot { jobCancelled(it) }.map { it.scheduledWorkDate to it.id }
                ).firstOrNull()
            }
            val jobHost = JobCardHost(
                viewModel = viewModel, container = container, customer = c,
                allJobs = allJobsForTabs, repJobId = repJobId,
                earliestJobId = earliestJobId, showNth = showJobBar,
                issuedDocs = issuedDocs, intakeByToken = intakeByToken,
                collabRefresh = collabRefresh, onCollabRefresh = { collabRefresh++; viewModel.loadCollabPending() },
                collabPendingCreatedAt = collabPendingCreatedAt,
                onEditDate = { jid, start, days, minutes ->
                    // 🔴 **그 건**을 고친다 — 돈처럼 날짜도 건별로. jid null(아직 건 없음)이면 창이 고객(대표) 경로로 폴백. (2026-10-14)
                    dateEditJobId = jid; dateEditStart = start; dateEditDays = days; dateEditMinutes = minutes
                    datePickerOpen = true
                },
                onEditAmount = { jid, field -> amountEditJobId = jid; amountEditField = field },
                onEditAddress = { jid -> addressEditJobId = jid; showAddressDialog = true },
                onAsDone = { asDoneConfirm = true },
                onAsPending = { asPendingConfirm = true },
                onAsPicker = { asPickerOpen = true },
                onHold = { holdDialog = true },
                onCancelBooking = { cancelBookingConfirm = true },
                onDeleteJob = { deletePastJobConfirm = it },
                onEditNote = { editingNote = it },
                onOpenPhoto = { photos, start -> fullscreenPhotos = photos; fullscreenStart = start },
                onOpenBeforeAfter = { beforeAfterReq = it },
                onPicksToDelete = { picksToDelete = it },
                onOpenQuoteDoc = { reviewQuoteDoc = it },
                onOpenIntakeDoc = { intakeReviewDoc = it },
                onEditIssued = { docId -> onOpenChatEditIssued(c.phoneNumber, c.id, docId) },
                onRequestDeleteDoc = { issuedDocToDelete = it },
                onOpenChat = { onOpenChat(c.phoneNumber, c.id) }
            )
            // 🧩 고객상세는 **한 레이아웃**으로 — 1건이어도 2건처럼 '시공 건' 섹션(머리+AS+새 시공). (2026-10-23 사장님 🅐)
            //   전엔 1건=간소(작은 '시공 하나 더' 링크)·2건+=전체라 화면이 둘이고 AS 자리도 달랐다.
            if (detailTab == 0 && (showJobBar || c.scheduledWorkDate != null)) {
                // 시공 건 · 최신부터 — 프로토 `.slab` 라벨. 이모지 대신 **벡터**(안전모 쓴 사람 = 브랜드 「노란 안전모」,
                //   마감 브리핑·일정 추가와 같은 아이콘 — 세 번째 벌 안 만듦 §12). (2026-10-15 사장님 #13)
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Filled.Engineering, null, tint = TossTextTertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    // '가까운 일정부터'는 건이 여럿일 때만 뜻이 있다 — 1건이면 '시공 건'만.
                    Text("시공 건" + if (showJobBar) " · 가까운 일정부터" else "", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                    Spacer(Modifier.weight(1f))
                    // 🛠️ A/S 일정 잡기 — 완료 건 카드를 **안 거치고** 바로. 예약 있으면 '보기'. (2026-10-22 사장님)
                    Text(
                        if (c.asScheduledDate != null) "A/S 예약 보기" else "A/S 일정 잡기",
                        color = TossBlue, fontWeight = FontWeight.Bold,
                        style = com.detailline.callfollowcrm.presentation.theme.AppType.label,
                        modifier = Modifier.clip(com.detailline.callfollowcrm.presentation.theme.AppShape.sm)
                            .clickable { asPickerOpen = true }.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                // ＋ 새 시공 잡기 — **건 카드들 위**(프로토 `.addnew`: 점선 + 연파랑 바탕). 한 줄 점선 버튼. (2026-10-15 사장님)
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.colors.primaryBg)
                        .border(1.dp, TossBlue.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable {
                            // ＋ 는 **새 건을 만든다.** 전엔 날짜 고르기만 열고 그 날짜를 지금 건에 덮어썼다. (2026-09-17)
                            // ② 날짜를 아직 안 정한 건이 있으면 새로 만들지 않는다. (2026-09-18 사장님)
                            //    그 건에 날짜부터 넣게 한다 — 날짜 없는 건이 줄줄이 생기는 걸 막는다.
                            //    ※ 1차에 날짜가 있는데 2차를 미리 잡는 건 그대로 열어둔다.
                            //   '지금 자리'가 이미 날짜 미정이면 그것도 빈 자리다 — 취소 직후가 그렇다. (2026-09-18 실기)
                            val pending = allJobsForTabs.firstOrNull {
                                it.scheduledWorkDate == null && !jobFolded(it)
                            }
                            val currentDateless = customer?.scheduledWorkDate == null
                            if (pending != null || currentDateless) {
                                android.widget.Toast.makeText(
                                    ctx, "날짜를 아직 안 정한 시공이 있어요. 그 날짜부터 넣어주세요",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                // 시트(시공일·주소·총액)부터 — 전엔 달력만 열려 주소 적을 곳이 안 보였다. (2026-10-15 사장님)
                                newJobStart = null; newJobDays = 1; newJobSheetOpen = true
                            }
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text("＋ 새 시공 잡기", color = TossBlue, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
                // (건들의 합 '시공 N건·합계·미수' 줄 제거 — 헤더 아래 상단 요약 스트립이 같은 걸 보여준다. 2026-10-14 재설계 3·4단계)
            }
            if (detailTab == 0) {
                if (allJobsForTabs.isEmpty()) {
                    // 건이 하나도 없는 고객(상담 단계·옛 데이터) — 고객 카드(c) 값으로 카드 한 장(옛 editJobId=null 경로).
                    JobDetailCard(host = jobHost, job = null, teamPhotos = teamPhotos, teamNotes = teamNotes, isPrimary = true)
                } else {
                    // 차수는 **날짜순**(JobOrder — 날짜 없는 건은 맨 뒤, 동점은 먼저 만든 건).
                    // 🔽 진행 중인 건은 **가까운 일정부터(오름차순)** — 다음에 할 일이 위. 완료·취소한 건은 맨 아래(접힘).
                    //   (2026-10-22 사장님: 먼 3차보다 가까운 2차가 먼저 보여야. 전엔 '최신 날짜부터'라 반대였다)
                    //   완료 묶음은 최근 완료가 위로(reversed). partition 은 순서를 지킨다.
                    val stackOrder = remember(allJobsForTabs) {
                        val seq = com.detailline.callfollowcrm.util.JobOrder.order(
                            allJobsForTabs.map { it.scheduledWorkDate to it.id }
                        )
                        val byDateAsc = allJobsForTabs.sortedBy { seq.indexOf(it.id) }   // 가까운(이른) 날짜부터
                        val (openJobs, closedJobs) = byDateAsc.partition { !jobFolded(it) }
                        openJobs + closedJobs.reversed()
                    }
                    stackOrder.forEach { j ->
                        // 건별 펼침 상태(메모·사진·협업·접수서·더보기·고르기)는 카드 안 remember — key 로 건마다 안정.
                        androidx.compose.runtime.key(j.id) {
                            // 팀원 사진·메모·서버 사진 되살리기·A/S 줄은 **고객 단위** → 대표(기본) 건 카드에만(전과 같은 거동).
                            val primary = j.id == primaryJobId
                            JobDetailCard(
                                host = jobHost, job = j,
                                teamPhotos = if (primary) teamPhotos else emptyList(),
                                teamNotes = if (primary) teamNotes else emptyList(),
                                isPrimary = primary
                            )
                        }
                    }
                }
            }

            // 🤝 어느 건인지 모르는 협업 — **날짜 꼬리표 없는 협업 줄**(옛 빌드 invite·서버에 날짜 없어 자동 백필 안 된 것).
            //   어느 건 카드에도 자동으로 안 붙는다(2026-10-15 사장님 결정 — 전엔 대표 건으로 몰려 1차 협업이 2차에 가 보였다).
            //   사라진 게 아니라고 **눈에 보이게** 하고, 사장님이 「이 건」을 고르면 그 건 날짜를 그 줄에 적는다(CollabAssignmentLine.assignDays).
            //   셈(떠돌이 고르기·붙이기·건 날짜)은 전부 CollabAssignmentLine 한 곳. 접힘 머리는 CdFoldRow(§12).
            CollabOrphanCard(
                customerId = c.id, allJobs = allJobsForTabs, container = container,
                collabRefresh = collabRefresh, onAssigned = { collabRefresh++ }
            )

            // 📄 이 고객 문서 — **맨 처음 건(1차)이 아예 없을 때만**(건이 없거나 전부 취소). (2026-10-15 사장님: 날짜 없는 문서는 1차로)
            //   1차가 있으면 떠돌이 문서는 그 1차 카드 안에 모이므로(JobDetailCard) 여기선 안 띄운다 — 위계: 고객 > 건 > 문서.
            run {
                val matchDays = com.detailline.callfollowcrm.domain.customer.IssuedDocAssign.jobDaySet(allJobsForTabs)
                val unmatchedDocs = com.detailline.callfollowcrm.domain.customer.IssuedDocAssign.unmatched(issuedDocs, matchDays)
                if (unmatchedDocs.isNotEmpty() && earliestJobId == null) {
                    // ② 접힘 — 고객 메모와 같은 옷(CdFoldRow). 드문 경우라 평소엔 한 줄. (2026-10-15 Fable §2)
                    var docsExpanded by remember(c.id) { mutableStateOf(false) }
                    val openDocs: (() -> Unit)? = if (docsExpanded) null else ({ docsExpanded = true })
                    TossCard(onClick = openDocs) {
                        Column {
                            CdFoldRow(Icons.Filled.ReceiptLong, "이 고객 문서", unmatchedDocs.size, docsExpanded) { docsExpanded = !docsExpanded }
                            if (docsExpanded) {
                            Spacer(Modifier.height(10.dp))
                            IssuedDocList(
                                docs = unmatchedDocs,
                                phone = c.phoneNumber,
                                intakeByToken = intakeByToken,
                                onOpenQuoteDoc = { reviewQuoteDoc = it },
                                onOpenIntakeDoc = { intakeReviewDoc = it },
                                onEditIssued = { docId -> onOpenChatEditIssued(c.phoneNumber, c.id, docId) },
                                onRequestDelete = { issuedDocToDelete = it },
                                onNewDoc = { onOpenChat(c.phoneNumber, c.id) },
                            )
                            }   // if (docsExpanded)
                        }
                    }
                }
            }

            // 7. 하단 — 블로그 후기 lockcard([블로그] 탭) + "지난 문자 보기" 링크(항상 표시). (2026-07-18 탭 재배치)
            val bottomCtx = androidx.compose.ui.platform.LocalContext.current
            if (detailTab == 2) androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White)
                    .clickable {
                        android.widget.Toast.makeText(bottomCtx, "블로그 후기 글 만들기는 비즈니스 요금제 기능이에요. 곧 열려요", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(AppTheme.colors.categoryBg),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.AutoAwesome, null, tint = AppTheme.colors.category, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("블로그 후기 글 만들기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Spacer(Modifier.height(2.dp))
                    com.detailline.callfollowcrm.presentation.component.HelpText("대화+사진으로 포스팅 글 자동 작성")
                }
                Text(
                    "비즈니스", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(AppTheme.colors.category).padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // (1건 전용 '시공 하나 더 잡기' 작은 링크 + 그 옆 AS 제거 — 위 '시공 건' 섹션(새 시공·AS)으로 통일. 2026-10-23 사장님 🅐)

            val lastMsgInteraction = remember { MutableInteractionSource() }
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth()
                    .pressScale(lastMsgInteraction)
                    .tossCardShadow(RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)).background(Color.White)
                    .clickable(interactionSource = lastMsgInteraction, indication = null) { onOpenChat(c.phoneNumber, c.id) }.padding(15.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.Chat, null, tint = TossBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("지난 문자 보기", color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(8.dp))
        }
        }   // ── if (c == null) … else 끝
    }
    // 🕐 전/후 사진 고르기 시트 — 고객상세 사진 섹션 [전/후] 버튼이 연다. 선택은 바로 저장된다. (2026-10-28 사장님)
    beforeAfterReq?.let { req ->
        val ids = req.photos.map { it.id }
        BeforeAfterPickerSheet(
            photos = req.photos,
            repPhotoId = req.repId,
            beforePhotoId = req.beforeId,
            onPickRep = { viewModel.setRepresentativePhoto(it, ids) },
            onPickBefore = { viewModel.setBeforePhoto(it, ids) },
            onClearBefore = { viewModel.setBeforePhoto(null, ids) },
            onDone = { beforeAfterReq = null }
        )
    }
    // ➕ 새 시공 잡기 시트 — 프로토 `s_newjob`(시공일·현장 주소·총 금액). 시공일 칸은 아래 기간 달력(addingNewJob)을 연다.
    //   저장은 addNewJob(start, days, address, totalWon) — 주소·총액이 **그 새 건**에 들어간다. (2026-10-15 사장님)
    if (newJobSheetOpen && customer != null) {
        val allJobsForNew by viewModel.allJobs.collectAsState()
        NewJobSheet(
            customerName = customer?.name?.takeIf { it.isNotBlank() && it.count { ch -> ch.isDigit() } < 9 },
            nth = allJobsForNew.count { !jobCancelled(it) } + 1,
            initialAddress = customer?.address,   // 같은 집 재방문 흔함 → 미리 채워 보여줌(보이고 편집 가능). (2026-10-27)
            start = newJobStart, days = newJobDays,
            onPickDate = { dateEditJobId = null; addingNewJob = true; datePickerOpen = true },
            onRegister = { address, totalWon, depositWon ->
                val start = newJobStart
                if (start != null) {
                    viewModel.addNewJob(start, newJobDays, address, totalWon, depositWon) { id ->
                        android.widget.Toast.makeText(
                            context,
                            if (id > 0L) "새 시공을 추가했어요" else "그 날짜엔 이미 시공이 있어요",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                newJobSheetOpen = false; newJobStart = null; newJobDays = 1
            },
            onDismiss = { newJobSheetOpen = false; newJobStart = null; newJobDays = 1 }
        )
    }
    }   // Box(시트 오버레이 자리)

    if (datePickerOpen && customer != null) {
        // 🔴 특정 건을 고치는 중이면 **그 건** 날짜로 미리 채운다. 「＋ 새 시공」이면 빈 달력(새 날짜). (2026-10-14)
        //   앱 공용 달력(WorkRangePickerDialog) — 전엔 여기만 Material3 범위선택이라 모양이 달랐다. (2026-10-24 통일)
        val initStart = if (addingNewJob) newJobStart else (dateEditJobId?.let { dateEditStart } ?: customer?.scheduledWorkDate)
        val initDays = if (addingNewJob) newJobDays.coerceAtLeast(1) else (if (dateEditJobId != null) dateEditDays else (customer?.scheduledWorkDays ?: 1)).coerceAtLeast(1)
        com.detailline.callfollowcrm.presentation.component.WorkRangePickerDialog(
            // 새 건을 잡는 중이면 그렇다고 말해준다 — 지금 건을 고치는 걸로 오해하지 않게. (2026-09-18)
            title = if (addingNewJob) "새 시공 — 시작일 → 끝날 (하루면 시작일만)"
                    else "시공 기간 — 시작일 → 끝날 (하루면 시작일만)",
            initialStart = initStart,
            initialDays = initDays,
            // 예약 취소(일정 비우기) — '새 시공 추가' 중엔 숨긴다(원래 잡힌 시공이 지워짐). (2026-09-21)
            //   건 카드에서 왔으면 그 건에 날짜가 있을 때, 아니면 고객 대표 날짜가 있을 때 보인다.
            clearText = if (!addingNewJob && (if (dateEditJobId != null) dateEditStart != null else customer?.scheduledWorkDate != null)) "예약 취소" else null,
            onClear = {
                // 🔴 건 카드에서 왔으면 **그 건**(dateEditJobId)을 직접 지운다 — 고객 날짜로 되찾다 어긋나 건 날짜가 잔존하던 것. (2026-10-28 사장님)
                viewModel.updateScheduledWorkDate(null, jobId = dateEditJobId)
                android.widget.Toast.makeText(context, "시공 예약을 취소했어요", android.widget.Toast.LENGTH_SHORT).show()
            },
            onConfirm = { start, days ->
                if (addingNewJob) {
                    // 새 건 — 날짜는 시트로 돌려주기만. 저장은 시트의 [새 시공 등록]. (2026-10-15)
                    newJobStart = start; newJobDays = days
                } else {
                    // 날짜(시작)+기간 저장 → "시공 시간" 선택 → (첫 등록이면) 축하. (2026-06-23 / 2026-08-01)
                    pendingWorkDays = days.coerceAtLeast(1)
                    val jid = dateEditJobId
                    if (jid != null) {
                        // 🔴 **그 건**에 저장 — 2차를 보며 잡으면 2차에 들어간다. (2026-10-14 사장님)
                        pendingCelebrate = dateEditStart == null
                        viewModel.setJobSchedule(jid, start, days)
                    } else {
                        pendingCelebrate = customer?.scheduledWorkDate == null
                        viewModel.updateScheduledWorkDate(start)
                    }
                    workTimePickerOpen = true
                }
                addingNewJob = false
            },
            onDismiss = { addingNewJob = false; datePickerOpen = false }
        )
    }

    // A/S 예약(시공과 별개, 무료) — 공용 달력, A/S 는 **주황** 강조로 시공과 구분. (2026-08-01 · 통일 2026-10-24)
    if (asPickerOpen && customer != null) {
        com.detailline.callfollowcrm.presentation.component.WorkRangePickerDialog(
            title = "A/S 예약 — 시작일 → 끝날 (하루면 시작일만) · 무료",
            initialStart = customer?.asScheduledDate,
            initialDays = (customer?.asScheduledDays ?: 1).coerceAtLeast(1),
            // A/S 주황 강조(2026-08-02). 모양은 시공 달력과 같고 색만 다르다.
            accent = Color(0xFFF5920B),
            rangeBg = AppTheme.colors.cautionBg,
            titleColor = Color(0xFFF5920B),
            clearText = if (customer?.asScheduledDate != null) "A/S 취소" else null,
            onClear = {
                viewModel.updateAsSchedule(null, 1)
                android.widget.Toast.makeText(context, "A/S 예약을 취소했어요", android.widget.Toast.LENGTH_SHORT).show()
            },
            onConfirm = { start, days -> viewModel.updateAsSchedule(start, days.coerceAtLeast(1)) },
            onDismiss = { asPickerOpen = false }
        )
    }

    // 시공일 저장 직후 "시공 시간" 선택. 정하면(또는 닫으면) 첫 등록이면 축하. (2026-06-23 사장님)
    if (workTimePickerOpen && customer != null) {
        com.detailline.callfollowcrm.presentation.component.WorkTimePickerDialog(
            initialMinutes = if (dateEditJobId != null) dateEditMinutes else customer?.scheduledWorkMinutes,
            initialDays = pendingWorkDays,
            onPick = { mins, days ->
                // 🔴 날짜와 같은 건에 시간도 저장. (2026-10-14 사장님)
                val jid = dateEditJobId
                if (jid != null) viewModel.setJobTiming(jid, mins, days)
                else viewModel.updateScheduledWorkTiming(mins, days)
                workTimePickerOpen = false
                if (pendingCelebrate) { celebrationVisible = true; vibrateCelebration(context); pendingCelebrate = false }
            },
            onDismiss = {
                workTimePickerOpen = false
                if (pendingCelebrate) { celebrationVisible = true; vibrateCelebration(context); pendingCelebrate = false }
            }
        )
    }

    // 예약 확정 축하 오버레이 — Scaffold 위에 떠서 콘페티 + 메시지 표시. 2.5초 뒤 자동 닫힘.
    if (celebrationVisible) {
        CelebrationOverlay(
            title = "예약 잡았어요",
            subtitle = "축하해요 🎉",
            onFinished = { celebrationVisible = false }
        )
    }

    // 📷 풀스크린 이미지 뷰어 — 썸네일 탭 시 표시. 검은 배경 + X 닫기. **좌우로 넘겨 그 건 사진 전부** 본다.
    //   내 사진(OfFile)은 돌리면 저장되고, 팀 사진(OfBitmap)은 돌려 보기만 된다 — 한 뷰어에서 둘 다. (공용 뷰어, 2026-10-01)
    fullscreenPhotos?.let { photos ->
        com.detailline.callfollowcrm.presentation.component.FullscreenPhotoViewer(
            photos = photos,
            startIndex = fullscreenStart,
            onDismiss = { fullscreenPhotos = null },
            onSaveRotation = { id, deg -> viewModel.rotatePhoto(id, deg) },
            // 🗑 크게 보면서 바로 지우기 — 내 사진만(팀·협업 사진은 OfBitmap 이라 삭제 버튼이 안 뜬다). (2026-10-24 사장님)
            onDelete = { id -> viewModel.deleteSitePhoto(id) },
            // ✏️ 펜으로 그려 → 고객에게 보내기 (내 사진만). (2026-10-24 사장님 "펜 완료=고객에게 보내기만")
            onSaveMarked = { id, marks -> viewModel.saveMarkedPhoto(id, marks) }
        )
    }

    if (nameDialogOpen && customer != null) {
        NameEditDialog(
            initial = customer?.name.orEmpty(),
            onSave = { name ->
                viewModel.updateName(name)
                nameDialogOpen = false
            },
            onDismiss = { nameDialogOpen = false }
        )
    }

    if (categoryDialogOpen && customer != null) {
        val categories by viewModel.categories.collectAsState()
        val categoryCounts by viewModel.categoryCounts.collectAsState()
        CategoryPickerDialog(
            categories = categories,
            counts = categoryCounts,
            selectedId = customer?.categoryId,
            onPick = { id ->
                viewModel.setCategory(id)
                categoryDialogOpen = false
            },
            onAddNew = { name, emoji ->
                viewModel.addCategoryAndAssign(name, emoji)
                categoryDialogOpen = false
            },
            onDismiss = { categoryDialogOpen = false }
        )
    }

    // 예약 취소 확인 — 카드 '🗑 시공 예약 취소' → 여기. 취소 시 금액 전부 삭제(완전 백지). (2026-08-28 사장님)
    if (cancelBookingConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { cancelBookingConfirm = false },
            title = { Text("시공 예약 취소", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
            text = {
                Text(
                    "이 시공 예약을 취소할까요?\n\n예약일과 함께 금액(총액·계약금·잔금)도 모두 지워지고, 정산에서도 빠져요.",
                    fontSize = 13.5.sp, color = TossTextSecondary, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateScheduledWorkDate(null)
                    cancelBookingConfirm = false
                    android.widget.Toast.makeText(context, "시공 예약을 취소했어요 (금액도 정리됨)", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("예약 취소", color = TossError, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { cancelBookingConfirm = false }) { Text("그대로 두기", color = TossTextSecondary) }
            },
            containerColor = Color.White,
            tonalElevation = 0.dp,   // 흰 창에 회색이 덧칠되는 것 끄기 (2026-09-21 사장님)
        )
    }

    // 일정·정산 금액 편집 (총금액/계약금) — 만원 입력.
    val allJobsForAmt by viewModel.allJobs.collectAsState()
    amountEditField?.let { field ->
        // 🔴 초기값은 **고른 건(job)**의 값으로 — 전엔 고객 값이라 2차 열어도 1차/대표 값이 떴다. (2026-10-07)
        // 💰 한 창에서 계약금 → 총 금액 → 잔금 자동(안내형, CustomerDetailAmountDialog). field 는 어느 칸에 커서를 둘지만 정한다. (2026-10-15)
        val amtJob = amountEditJobId?.let { id -> allJobsForAmt.firstOrNull { it.id == id } }
        val initTotal = if (amtJob != null) amtJob.totalAmount else customer?.totalAmount
        val initDeposit = if (amtJob != null) amtJob.depositAmount else customer?.depositAmount
        AmountInputDialog(
            initialTotalWon = initTotal,
            initialDepositWon = initDeposit,
            focusDeposit = field == "deposit",
            onSave = { totalWon, depositWon ->
                val jid = amountEditJobId
                if (jid != null) {
                    // 총액·계약금을 **한 번에** 그 건에 — 따로 두 번 쓰면 읽고-쓰기가 겹쳐 한쪽이 지워질 수 있다.
                    viewModel.setJobAmounts(jid, totalWon, depositWon)
                } else {
                    val old = customer?.totalAmount ?: 0L
                    // 건 없는 고객 — 둘을 한 번에(계약금 적으면 받음 자동, 건 경로와 같은 규칙).
                    viewModel.setCustomerAmounts(totalWon, depositWon)
                    // 시공금액이 "바뀌면"(첫 설정 제외) 이유 입력 띄움. 변경 이력 카드는 거기서 기록. (2026-06-30 사장님)
                    val newTotal = totalWon ?: 0L
                    if (newTotal != old && old > 0L) amountChangeReason = old to newTotal
                }
                amountEditJobId = null
                amountEditField = null
            },
            onDismiss = { amountEditJobId = null; amountEditField = null }
        )
    }
    // 시공금액 변경 이유(선택) — 기록/그냥넘어가기/바깥탭 모두 변경 이력 카드 남김. 이유는 사장님 기억용. (2026-06-30 사장님)
    amountChangeReason?.let { (oldWon, newWon) ->
        AmountChangeReasonDialog(
            oldWon = oldWon,
            newWon = newWon,
            onRecord = { reason -> viewModel.recordAmountChange(oldWon, newWon, reason); amountChangeReason = null },
            onSkip = { viewModel.recordAmountChange(oldWon, newWon, null); amountChangeReason = null }
        )
    }

    // 고른 사진 여러 장 삭제 확인 — 한 장씩 ✕ → 확인 을 반복하던 걸 한 번으로. (2026-09-22 사장님)
    picksToDelete?.let { (mine, team) ->
        val n = mine.size + team.size
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { picksToDelete = null },
            title = { Text("사진 ${n}장을 지울까요?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (team.isEmpty()) "고른 현장 사진을 지웁니다. 되돌릴 수 없어요."
                    else "고른 현장 사진을 지웁니다. 팀원이 올린 사진은 팀원 화면에서도 사라져요. 되돌릴 수 없어요."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    mine.forEach { viewModel.deleteSitePhoto(it) }
                    team.forEach { viewModel.deleteTeamPhoto(it) }
                    picksToDelete = null
                }) {
                    Text("삭제", color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { picksToDelete = null }) { Text("취소", color = TossTextSecondary) }
            }
        )
    }

    // 발행 이력 → 견적서 다시 보기(직인 문서 재렌더, 재공유 가능). (2026-07-07 사장님)
    reviewQuoteDoc?.let { data ->
        com.detailline.callfollowcrm.presentation.screen.chat.QuoteDocScreen(
            data = data,
            onClose = { reviewQuoteDoc = null },
            // onShared 생략 — 다시 보기는 이력에 이미 있어 재기록 안 함(재발송은 됨).
            onSendImage = { uri ->
                // 📮 이 고객에게 바로 사진문자(MMS) 재발송. 앱이 기본 문자앱이라 앱 안에서 나간다. (2026-10-30 사장님)
                val phone = customer?.phoneNumber
                reviewQuoteDoc = null
                if (phone.isNullOrBlank()) {
                    android.widget.Toast.makeText(context, "고객 번호가 없어요", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.detailline.callfollowcrm.util.SmsSender.sendMms(context, phone, "", listOf(uri))
                        }
                        android.widget.Toast.makeText(
                            context,
                            if (ok) "견적서를 보냈어요" else "견적서를 보내지 못했어요 — 다시 시도해주세요",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    // 발행 이력 → 시공접수서 요약(확인 개념) + 링크 열기/복사.
    intakeReviewDoc?.let { doc ->
        IntakeReviewDialog(
            doc = doc,
            onCopyLink = { url ->
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(url))
                android.widget.Toast.makeText(context, "링크를 복사했어요", android.widget.Toast.LENGTH_SHORT).show()
                intakeReviewDoc = null
            },
            onOpenLink = { url ->
                // 앱 내 웹뷰로 열기 — 브라우저 없어도 항상 열림. (2026-07-13 사장님: 크롬 의존 제거)
                // 📌 챗에서 여는 것과 **똑같이** 고칠 수 있어야 한다 — 한쪽만 되면 그게 더 헷갈린다.
                com.detailline.callfollowcrm.presentation.screen.web.DocWebViewActivity.open(
                    context, url, "시공접수서",
                    memoToken = doc.token, memoText = doc.memo.orEmpty()
                )
                intakeReviewDoc = null
            },
            onDismiss = { intakeReviewDoc = null }
        )
    }

    // 🗑 취소/빈 '지난 건' 삭제 확인. (2026-10-06 사장님 "지난 건이 왜 안 지워지지")
    deletePastJobConfirm?.let { j ->
        AlertDialog(
            onDismissRequest = { deletePastJobConfirm = null },
            containerColor = Color.White,
            title = { Text("이 건을 지울까요?", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
            // 날짜·금액이 없는 건만 여기 온다(JobDeleteRule) — 취소한 건·빈 건·주소만 든 날짜 미정 건. (2026-10-17)
            text = { Text("날짜도 금액도 없는 건이에요. 완전히 지워집니다.", color = TossTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePastJob(j.id)
                    deletePastJobConfirm = null
                }) { Text("삭제", color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deletePastJobConfirm = null }) { Text("닫기", color = TossTextSecondary) }
            }
        )
    }

    // 발행 이력 1건 삭제 확인.
    issuedDocToDelete?.let { doc ->
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { issuedDocToDelete = null },
            title = { Text("발행 이력에서 지울까요?", fontWeight = FontWeight.Bold) },
            text = { Text("이 발행 기록을 목록에서 지워요. 고객에게 이미 보낸 문서는 영향 없어요.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteIssuedDoc(doc.id); issuedDocToDelete = null }) {
                    Text("삭제", color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { issuedDocToDelete = null }) { Text("취소", color = TossTextSecondary) }
            }
        )
    }

}


private fun android.view.View.findDialogWindow(): android.view.Window? {
    var p: android.view.ViewParent? = parent
    while (p != null) {
        if (p is DialogWindowProvider) return p.window
        p = p.parent
    }
    return null
}


// ChatBottomSheet + ChatBubble 은 메인 ChatScreen 으로 대체되어 제거됨 (2026-05-19).
// 같은 SMS/MMS 표시 + composer 기능이 대시보드 진입 시 메인 뷰로 옮겨졌다.
// 이 화면(CustomerDetail)의 문자 섹션은 이제 정보 표시 전용 (MessageRowView).


@Composable
private fun PersonaCard(persona: com.detailline.callfollowcrm.ai.CustomerPersona) {
    // 프로토 .persona-card — 연한 파란 그라데이션 + 테두리 + ✨ 고객 페르소나 + [AI 분석] 칩.
    Column(
        Modifier.fillMaxWidth()
            .tossCardShadow(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(AppTheme.colors.primaryBg, Color.White)))
            .border(1.dp, Color(0xFFE6EEFB), RoundedCornerShape(18.dp))
            .padding(17.dp)
    ) {
        run {
            // 방어: 혹시라도 "null" 문자열이 새어들어와도 빈 것으로 취급(파싱에서 1차로 막지만 belt & suspenders).
            val personaText = persona.personaText?.takeIf { it.isNotBlank() && !it.equals("null", true) }
            val hasFields = !persona.communicationStyle.isNullOrBlank() || !persona.budgetSignal.isNullOrBlank() ||
                !persona.location.isNullOrBlank() || !persona.schedulePattern.isNullOrBlank() || !persona.ownerMemo.isNullOrBlank()
            val hasContent = personaText != null || hasFields
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                com.detailline.callfollowcrm.presentation.theme.AiMark(TossTextTertiary, 13.dp, 6.dp)
                Text("고객 페르소나", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                // "갱신 중"은 기존 내용이 있을 때만(=다시 다듬는 중). 내용이 아예 없을 땐 아래 안내가 설명하므로 숨김.
                if (persona.stale && hasContent) {
                    Spacer(Modifier.width(6.dp))
                    Text("· 갱신 중", fontSize = 11.sp, color = TossBlue)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "AI 분석", color = TossBlue, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(AppTheme.colors.primaryBg).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            when {
                personaText != null -> {
                    // 2026-05-29 cowork §17 — 한 줄 자유 텍스트 우선 표시.
                    Text(
                        personaText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TossTextPrimary,
                        lineHeight = 22.sp
                    )
                }
                hasFields -> {
                    // 옛 5 필드 fallback (cowork 미래 분리 모드 도입 시 자동 활성화).
                    PersonaLine("💬", persona.communicationStyle)
                    PersonaLine("💰", persona.budgetSignal)
                    PersonaLine("🏠", persona.location)
                    PersonaLine("⏰", persona.schedulePattern)
                    PersonaLine("📝", persona.ownerMemo)
                }
                else -> {
                    // 내용 없음 — "분석 중"(곧 뜰 것처럼)은 과장. 대화가 부족하면 영영 안 뜰 수 있으니
                    //   "대화가 부족하다"는 사실을 정직하게. 쌓이면 자동으로 채워진다고 안심까지.
                    Text(
                        "고객 성향을 정리하기엔 아직 대화가 부족해요.\n대화가 더 쌓이면 자동으로 분석해드려요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TossTextTertiary,
                        lineHeight = 22.sp
                    )
                }
            }
            if (persona.sourceMessageCount > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${persona.sourceMessageCount}건의 대화 분석 기반",
                    style = MaterialTheme.typography.labelSmall,
                    color = TossTextTertiary
                )
            }
        }
    }
}


private data class MessageRow(
    val timeMs: Long,
    val sent: Boolean,
    val body: String,
    val imageUris: List<android.net.Uri> = emptyList()
)

@Composable
private fun MessageRowView(m: MessageRow, onImageTap: (android.net.Uri) -> Unit = {}) {
    val badgeText = if (m.sent) "보냄" else "받음"
    val badgeColor = if (m.sent) TossBlue else com.detailline.callfollowcrm.presentation.theme.TossSuccess
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .background(
                    color = badgeColor.copy(alpha = 0.12f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                DateTimeUtils.formatShort(m.timeMs),
                style = MaterialTheme.typography.labelSmall,
                color = TossTextTertiary
            )
            Spacer(Modifier.height(2.dp))
            if (m.body.isNotBlank()) {
                Text(
                    m.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary,
                    maxLines = 6
                )
            }
            if (m.imageUris.isNotEmpty()) {
                if (m.body.isNotBlank()) Spacer(Modifier.height(6.dp))
                ImageThumbnailRow(m.imageUris, onImageTap)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun tossFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TossBlue,
    unfocusedBorderColor = TossDivider,
    focusedTextColor = TossTextPrimary,
    unfocusedTextColor = TossTextPrimary,
    cursorColor = TossBlue,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White
)

// 2026-05-25: status 다이얼로그/pill/단계 시각화 함수 모두 제거 —
//   PIPELINE_STAGES, OFF_FLOW_STATUSES, nextStageOf, StatusDialogContent,
//   CurrentStatusBadge, PipelineProgress, ChoiceState, StatusChoiceChip
//   갤메시지 식 사장님 카테고리 시스템으로 통일 (P2 카테고리 시스템에서 대체).


/** 이름 편집 다이얼로그. 작은 input + 저장/취소. 저장 시 즉시 DB 커밋. */
@Composable
private fun NameEditDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("고객명", color = TossTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("이름을 입력하세요", color = TossTextTertiary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = tossFieldColors()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.trim()) }) {
                Text("저장", color = TossBlue, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TossTextSecondary)
            }
        },
        containerColor = Color.White,
        tonalElevation = 0.dp,   // 흰 창에 회색이 덧칠되는 것 끄기 (2026-09-21 사장님)
    )
}


/**
 * 되돌리는 단추 — 작게, 오른쪽 끝에. 빨강은 '취소' 하나에만. (2026-09-20 사장님)
 *   🔎 (2026-10-15 사장님 "계약금 넣기·협업 부르기·더보기가 보이지도 않고 익숙하지 않은 UX")
 *   foot 의 행동들도 이 칩 **한 벌**로 — 아이콘(선택)을 앞에 붙이고 글자는 AppType.label(12.5·ExtraBold).
 *   회색 글자만 덩그러니 있던 것 → 알약 바탕 + 아이콘이라 「누르는 것」으로 보인다.
 */
@Composable
internal fun CdUndoChip(
    label: String, danger: Boolean,
    /** 채운 회색 칩 — 잔금 옆 「받았어요」처럼 **평소에 보이는 작은 행동**. 테두리만(기본)은 ⋯ 더보기 안 되돌리기용. (2026-10-15) */
    filled: Boolean = false,
    /** 앞에 붙는 작은 아이콘(14dp). foot 의 계약금·협업·더보기가 쓴다. */
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /** 글자·아이콘 색을 바꿀 때(협업=보라, 접수서=파랑). null = 기본(회색). */
    tint: Color? = null,
    onClick: () -> Unit
) {
    val fg = tint ?: if (danger) TossError else if (filled) AppTheme.colors.textSub else AppTheme.colors.textHint
    androidx.compose.foundation.layout.Row(
        Modifier.clip(AppShape.sm)
            .then(
                if (filled) Modifier.background(AppTheme.colors.surfaceMuted)
                else Modifier.border(1.dp, if (danger) AppTheme.colors.unpaidBg else AppTheme.colors.surfaceMuted, AppShape.sm)
            )
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(AppSpace.s4))
        }
        Text(label, style = AppType.label, color = fg, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** 원 → "N만원"(만 단위로 떨어지면) / "N원". 규칙은 MoneyFormatter 한 곳에. (2026-09-28) */
internal fun manwonLabel(won: Long): String =
    com.detailline.callfollowcrm.util.MoneyFormatter.manwonOrWon(won)

// (payStatusLabel / MoneyEditPill 제거 — 돈도 CdKv 한 줄로 바뀌면서 쓸 곳이 없어졌다. 2026-09-20)

// (금액 입력 창은 CustomerDetailAmountDialog.kt — 계약금 → 총 금액 → 잔금 자동. 2026-10-15 사장님)

/** 시공금액 변경 시 이유 입력(선택) — 적으면 변경 이력 카드에 남고, 안 적어도(그냥 넘어가기) 변경 자체는 기록. (2026-06-30 사장님) */
@Composable
private fun AmountChangeReasonDialog(oldWon: Long, newWon: Long, onRecord: (String) -> Unit, onSkip: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    val arrow = if (oldWon > 0L) "${manwonLabel(oldWon)}  →  ${manwonLabel(newWon)}"
                else "${manwonLabel(newWon)}으로 정함"
    androidx.compose.ui.window.Dialog(onDismissRequest = onSkip) {   // 바깥/뒤로 = 그냥 넘어가기(이력은 남김)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp)) {
            com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
            Text("시공금액을 바꿨어요", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(arrow, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossBlue)
            Spacer(Modifier.height(12.dp))
            Text("왜 바꿨는지 적어두면 나중에 기억나요 (안 써도 돼요)", fontSize = 12.5.sp, color = TossTextTertiary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                placeholder = { Text("예: 곰팡이 심해 부위 추가") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = tossFieldColors()
            )
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TossSecondaryButton(text = "그냥 넘어가기", onClick = onSkip, modifier = Modifier.weight(1f))
                TossPrimaryButton(text = "기록하기", onClick = { onRecord(reason) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 사진 카드가 화면을 통째로 먹지 않게 — 두 줄까지만 보이고 나머지는 "+N장" 으로 접는다. */
private const val PHOTO_COLLAPSED = 6

/** 사진 카드 제목 줄의 작은 버튼 — [올리기] 파랑 채움 / [고르기]·[취소] 회색. */
@Composable
internal fun PhotoHeadBtn(label: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
        color = if (filled) TossBlue else TossTextSecondary,
        modifier = Modifier
            .clip(AppShape.sm)
            .background(if (filled) TossBlueSoft else TossGrayBg)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    )
}

/** 주소로 지도 앱 길찾기 — geo: 쿼리. 설치된 지도 앱(카카오내비/구글지도 등) 선택. */
internal fun startNavToAddress(context: android.content.Context, address: String) {
    runCatching {
        val uri = android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(address))
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
    }.onFailure {
        android.widget.Toast.makeText(context, "지도 앱을 열 수 없어요", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** 시스템 다이얼러를 연다. ACTION_DIAL 은 권한 불필요, 자동 발신도 안 함. */
private fun dialPhone(context: android.content.Context, phoneNumber: String) {
    // ☎️ 전화 거는 자리는 **한 곳**이다 — 거는 번호를 기억해 두어야
    //   통화가 시작될 때 그 손님 카드를 띄운다. (2026-09-30 사장님)
    com.detailline.callfollowcrm.util.PhoneDialer.open(context, phoneNumber)
}

/**
 * 입금 한 줄 — 라벨 + 받음 체크 + 금액 + 단위 칩 + 받은 날짜(DatePicker).
 *
 *  - 받음 확정 = 받은 날짜를 바로 고르게 DatePicker 를 띄움(기본=오늘). 9일에 받은 걸 10일에
 *    입력해도 실제 받은 날(9일)로 들어가게. (2026-06-10 사장님 통점: 받은 날이 누른 날로 박힘)
 *  - 금액: 직접 타이핑 또는 [+1만][+5만][+10만][+100만] 칩 가산 / [지움]
 *  - 천 단위 콤마는 VisualTransformation 으로 표시 (raw 값은 숫자만)
 *  - 받은 날짜: 체크된 상태에서 탭 → DatePicker 다이얼로그
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentRow(
    label: String,
    amount: Long?,
    paidAt: Long?,
    onPaidChange: (Boolean) -> Unit,
    onAmountChange: (Long?) -> Unit,
    onPaidAtChange: (Long) -> Unit,
    /**
     * 2026-05-30 사장님 #4 통점 — 자동 계산값 표시 여부.
     * true 면 PROMISED 상태에 "💡 자동 (수정 가능)" 배지 노출. 사장님 수동 수정 시 자동 X.
     */
    isAutoCalculated: Boolean = false
) {
    // 2026-05-28 UI 개편 (사장님 "초보 기획자 느낌" 보고):
    //   4가지 상태 시각 분리 + 인플레이스 펼침. 빈 상태 = 큰 액션 1개, 완료 상태 = ✅ 자랑.
    //   상태:
    //     EMPTY    : amount == null && paidAt == null         → [💸 받았어요] 버튼 + [건너뛰기] 링크
    //     PROMISED : amount > 0 && paidAt == null              → 💵 약속됨 + [받음 확정] / [수정] / [지움]
    //     RECEIVED : amount > 0 && paidAt != null              → ✅ 큰 금액 + 날짜 + [수정]
    //     SKIPPED  : amount == 0 && paidAt != null             → 🚫 안 받는 거래 + [되돌리기]
    //   편집 모드는 AnimatedVisibility 로 그 자리에 펼침 — 다이얼로그 X.
    val state = when {
        paidAt != null && amount == 0L -> PaymentState.SKIPPED
        paidAt != null -> PaymentState.RECEIVED
        amount != null && amount > 0L -> PaymentState.PROMISED
        else -> PaymentState.EMPTY
    }
    var editing by remember(amount, paidAt) { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 라벨 — 모든 상태 공통
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TossTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        // 상태별 표시 — 편집 중이 아닐 때만
        androidx.compose.animation.AnimatedVisibility(
            visible = !editing,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            Column {
                when (state) {
                    PaymentState.EMPTY -> {
                        // 빈 상태 — 큰 액션 1개. "받았어요" 가 메인. 안 받는 거래면 회색 링크.
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .background(com.detailline.callfollowcrm.presentation.theme.TossBlueSoft)
                                .clickable { editing = true }
                                .padding(vertical = 14.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(
                                "입금 받았어요",
                                color = TossBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    // SKIPPED 처리: amount=0 + paidAt=now. "안 받기로 한 거래" 의 영구 표현.
                                    onAmountChange(0L)
                                    onPaidChange(true)
                                }
                            ) {
                                Text(
                                    "이 거래는 $label 없음",
                                    color = TossTextTertiary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    PaymentState.PROMISED -> {
                        // 금액만 정해진 상태 — "약속됨". 받은 즉시 [확정] 으로 RECEIVED 전환.
                        // 2026-05-30 #4 — 자동 계산값이면 "💡 자동" 배지 표시.
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isAutoCalculated) "자동 계산 (수정 가능)" else "약속됨",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isAutoCalculated) TossBlue else TossTextSecondary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "₩${formatThousands(amount ?: 0L)}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = TossTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                    .background(com.detailline.callfollowcrm.presentation.theme.TossSuccess)
                                    // 받음 확정 = 실제 받은 날짜를 고르게 DatePicker(기본=오늘). 확인 시 그 날로 paidAt 기록.
                                    .clickable { datePickerOpen = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    "✓ 받음 확정",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            androidx.compose.material3.TextButton(onClick = { editing = true }) {
                                Text("금액 수정", color = TossTextSecondary, fontSize = 12.sp)
                            }
                            androidx.compose.material3.TextButton(
                                onClick = { onAmountChange(null); onPaidChange(false) }
                            ) {
                                Text("지움", color = TossTextTertiary, fontSize = 12.sp)
                            }
                        }
                    }
                    PaymentState.RECEIVED -> {
                        // 완료 — ✅ 큰 금액 + 날짜 자랑. 수정 작게.
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.CheckCircle, null, tint = TossTextSecondary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "₩${formatThousands(amount ?: 0L)}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = TossTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                androidx.compose.foundation.layout.Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { datePickerOpen = true }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        "${paidAt?.let { DateTimeUtils.formatKoreanDate(it) } ?: ""}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = com.detailline.callfollowcrm.presentation.theme.TossSuccess,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "눌러서 수정",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TossTextTertiary
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            androidx.compose.material3.TextButton(onClick = { editing = true }) {
                                Text("금액 수정", color = TossTextSecondary, fontSize = 12.sp)
                            }
                            androidx.compose.material3.TextButton(
                                onClick = { onAmountChange(null); onPaidChange(false) }
                            ) {
                                Text("지움", color = TossTextTertiary, fontSize = 12.sp)
                            }
                        }
                    }
                    PaymentState.SKIPPED -> {
                        // 안 받기로 한 거래 — 회색 처리. 되돌리기.
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Block, null, tint = TossTextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "이 거래엔 $label 없음",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TossTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.material3.TextButton(
                                onClick = { onAmountChange(null); onPaidChange(false) }
                            ) {
                                Text("되돌리기", color = TossBlue, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 편집 모드 — 같은 자리에 입력란 + 칩 펼침
        androidx.compose.animation.AnimatedVisibility(
            visible = editing,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            PaymentInlineEditor(
                initialAmount = amount?.takeIf { it > 0L },
                onCancel = { editing = false },
                onSave = { newAmount ->
                    onAmountChange(newAmount)
                    editing = false
                    // 금액 입력 후 받음 처리도 실제 받은 날짜를 고르게 DatePicker(기본=오늘).
                    //   취소하면 금액만 정해진 약속됨(PROMISED) 으로 남음.
                    if (paidAt == null) datePickerOpen = true
                }
            )
        }
    }

    if (datePickerOpen) {
        // 시간대 보정 — DatePicker 는 UTC 해석이라 아침(KST)엔 하루 일찍 하이라이트됨. 로컬 오프셋 더해 보정. (2026-08-01 사장님)
        val initial = (paidAt ?: System.currentTimeMillis()).let { it + java.util.TimeZone.getDefault().getOffset(it) }
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = Color.White),
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPaidAtChange(it) }
                    datePickerOpen = false
                }) { Text("확인", color = TossBlue, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) {
                    Text("취소", color = TossTextSecondary)
                }
            }
        ) {
            // 창 회색 제거(테마 surfaceContainerHigh 기본값) → 흰색. (2026-08-02 사장님)
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

/** PaymentRow 의 4가지 상태 — 시각 분리용 enum. */
private enum class PaymentState { EMPTY, PROMISED, RECEIVED, SKIPPED }


/**
 * 인플레이스 입력 모드 — 펼침 시 카드 안 같은 자리에 등장 (다이얼로그 X).
 *   초기값은 기존 금액 (있으면) 시드. +1만/+5만/+10만/+100만 가산 + [취소][저장].
 *   저장 시 trim 후 0 이상 Long. 빈 입력 = onSave(null) 안 호출 — 저장 비활성.
 */
@Composable
private fun PaymentInlineEditor(
    initialAmount: Long?,
    onCancel: () -> Unit,
    onSave: (Long) -> Unit
) {
    var amountText by androidx.compose.runtime.saveable.rememberSaveable(initialAmount) {
        mutableStateOf(initialAmount?.toString().orEmpty())
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = amountText,
            onValueChange = { raw ->
                amountText = raw.filter { it.isDigit() }.take(10)
            },
            placeholder = { Text("금액 (원)", color = TossTextTertiary, fontSize = 13.sp) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            visualTransformation = ThousandsSeparatorTransformation,
            modifier = Modifier.fillMaxWidth(),
            colors = tossFieldColors(),
            textStyle = TextStyle(fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard, fontSize = 17.sp, color = TossTextPrimary, fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AmountChip("+1만") { addToAmount(10_000L, amountText) { amountText = it } }
            AmountChip("+5만") { addToAmount(50_000L, amountText) { amountText = it } }
            AmountChip("+10만") { addToAmount(100_000L, amountText) { amountText = it } }
            AmountChip("+100만") { addToAmount(1_000_000L, amountText) { amountText = it } }
        }
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            androidx.compose.material3.TextButton(onClick = onCancel) {
                Text("취소", color = TossTextSecondary)
            }
            // 2026-05-30 사장님 #4 통점 fix — 간헐적 저장 안 됨:
            //   기존 `enabled = ... > 0L` 가 recomposition race 로 사장님 클릭 직전 false 상태일 때
            //   클릭 무시 → "첫 번째는 저장 X, 두 번째는 O" 통점.
            //   해결: enabled 항상 true. onClick 안에서 검사 → 빈 입력이면 silent no-op.
            //   visual 약간 거짓 hint 줄지만 race 0 — 사장님이 한 번 누르면 무조건 시도.
            androidx.compose.material3.TextButton(
                onClick = {
                    val n = amountText.toLongOrNull()
                    if (n != null && n > 0L) onSave(n)
                }
            ) {
                val hasInput = (amountText.toLongOrNull() ?: 0L) > 0L
                Text(
                    "저장",
                    color = if (hasInput) TossBlue else TossTextTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** 천단위 콤마 포맷. ViewModel/UI 양쪽 헬퍼. */
private fun formatThousands(n: Long): String = com.detailline.callfollowcrm.util.MoneyFormatter.grouped(n)

/** 가산 칩 — 현재 금액 텍스트에 amount 만큼 더해 새 텍스트 반환. */
private fun addToAmount(delta: Long, current: String, set: (String) -> Unit) {
    val curr = current.toLongOrNull() ?: 0L
    val next = (curr + delta).coerceAtLeast(0L)
    set(next.toString())
}

@Composable
private fun AmountChip(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val fg = if (danger) TossTextSecondary else TossBlue
    val bg = if (danger) TossGrayBg else com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}


// 2026-05-25: StatusPill / statusColors 제거 — status pill UI 폐기.
//   카테고리 chip 으로 대체 예정 (Phase 2).


/**
 * 이 손님을 어느 **분류**에 둘지 — 큰 타일 2열. (2026-09-19 사장님 · 프로토 3mV1wfJu 의 '다')
 *
 * 전엔 알약 칩이 두 줄로 흐르고 [닫기]를 또 눌러야 했다. 사장님 손님들은 손가락이 거칠고
 * 연세도 있으셔서 **격자로 크게** 잡는 편이 누르기 쉽다.
 *   · 고르면 **바로 닫힌다** (실수해도 다시 열어 바꾸면 된다)
 *   · 이모지는 [CategoryEmoji] 가 이름 보고 붙인다 — 사장님이 고른 건 그대로 둔다
 *   · **몇 명인지** 같이 보여준다
 *   · ⚠️ 이름은 **두 줄까지** — "인테리어 업체" 가 잘리면 타일의 의미가 없다
 */
@Composable
private fun CategoryPickerDialog(
    categories: List<com.detailline.callfollowcrm.data.local.entity.CategoryEntity>,
    counts: Map<Long?, Int>,
    selectedId: Long?,
    onPick: (Long?) -> Unit,
    onAddNew: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var addDialogOpen by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp)
        ) {
            com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
            val grpHint = com.detailline.callfollowcrm.presentation.component.rememberFirstRunHint("customer_group_sheet")
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("이 고객은 어디에 둘까요?", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                com.detailline.callfollowcrm.presentation.component.HintInfoDot(grpHint)
            }
            Spacer(Modifier.height(4.dp))
            com.detailline.callfollowcrm.presentation.component.FirstRunHintText(grpHint, "나중에 묶어 보거나 찾을 때 써요")
            Spacer(Modifier.height(14.dp))

            // 미분류 + 사장님 분류들. 2열 격자 — 홀수면 마지막 칸은 비운다(줄이 안 깨지게).
            val cells: List<Pair<Long?, String>> =
                listOf<Pair<Long?, String>>(null to "미분류") + categories.map { it.id as Long? to it.name }
            cells.chunked(2).forEach { row ->
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    row.forEach { (id, name) ->
                        val emoji = if (id == null) com.detailline.callfollowcrm.util.CategoryEmoji.forName("미분류")
                        else categories.firstOrNull { it.id == id }?.emoji
                            ?: com.detailline.callfollowcrm.util.CategoryEmoji.forName(name)
                        CategoryTile(
                            emoji = emoji,
                            name = name,
                            count = counts[id] ?: 0,
                            selected = selectedId == id,
                            modifier = Modifier.weight(1f)
                        ) { onPick(id) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(3.dp))
            // ＋ 만들기는 타일 **밖**으로 — 고르는 것과 만드는 것은 다른 일이다.
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .border(1.dp, TossDivider, RoundedCornerShape(13.dp))
                    .clickable { addDialogOpen = true }
                    .padding(vertical = 13.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("＋ 새 분류 만들기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossBlue)
            }
        }
    }
    if (addDialogOpen) {
        CategoryNameInputDialog(
            onDismiss = { addDialogOpen = false },
            onConfirm = { name, emoji ->
                onAddNew(name, emoji)
                addDialogOpen = false
            }
        )
    }
}

/**
 * 카테고리 이름만 받는 input 다이얼로그. HomeScreen 의 CategoryAddDialog 와 동일 톤.
 *   이모지 입력란 X (사장님 결정 2026-05-25 — 한글 단어로 충분).
 */
@Composable
private fun CategoryNameInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    // 이름을 치면 앱이 이모지를 하나 골라준다. 마음에 안 들면 옆에서 바꾼다. (2026-09-19 사장님)
    //   "틀려도 손해가 없고, 맞으면 한 손 덜어진다" — 그래서 AI 대신 앱 안 표로 한다.
    var pickedEmoji by remember { mutableStateOf<String?>(null) }
    val suggested = remember(name) { com.detailline.callfollowcrm.util.CategoryEmoji.forName(name) }
    val emoji = pickedEmoji ?: suggested
    val candidates = remember(name) { com.detailline.callfollowcrm.util.CategoryEmoji.candidatesFor(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 분류 만들기", color = TossTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                Text(
                    "이름만 적으면 막내가 대화 내용 보고 알아서 나눠드려요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("예: AS 고객, 협업 사장, 친구", color = TossTextTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tossFieldColors()
                )
                if (name.isNotBlank()) {
                    Text("그림", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary)
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        candidates.forEach { e ->
                            val on = e == emoji
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (on) TossBlueSoft else TossGrayBg)
                                    .border(
                                        if (on) 1.5.dp else 0.dp,
                                        if (on) TossBlue else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { pickedEmoji = e },
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) { Text(e, fontSize = 19.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onConfirm(name.trim(), emoji)
            }) { Text("추가", color = TossBlue, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TossTextSecondary)
            }
        },
        containerColor = Color.White,
        tonalElevation = 0.dp,   // 흰 창에 회색이 덧칠되는 것 끄기 (2026-09-21 사장님)
    )
}


/**
 * **빈 건** — 날짜도 금액도 주소도 없는 껍데기. (2026-09-18 사장님)
 *   "1차가 날짜도 없고 금액도 없는데 2차 3차가 있다는 게 말이 안 돼. 만들어지지도 말아야지"
 *   예약을 취소하거나 날짜를 풀면 이런 껍데기가 남는데, 그게 앞줄에서 **차수를 차지**하는 바람에
 *   [1차 날짜미상][2차 날짜미상][3차 신규] 처럼 빈 칸이 줄줄이 늘어섰다.
 *   돈이나 주소가 하나라도 적혀 있으면 빈 건이 아니다 — 그건 살려둬야 할 기록이다.
 */
internal fun jobBlank(j: com.detailline.callfollowcrm.data.local.entity.JobEntity): Boolean =
    j.scheduledWorkDate == null && j.workCompletedAt == null &&
        j.totalAmount == null && j.depositAmount == null && j.balanceAmount == null &&
        j.depositPaidAt == null && j.balancePaidAt == null && j.address.isNullOrBlank()

/**
 * **취소한 건 / 빈 건.** (2026-09-18 사장님 "1차 시공이 잡히지도 않았는데 2차 3차 등록도 가능하네")
 *   취소는 기록을 남기려고 날짜만 비운다 → 그 건이 '날짜 미정'으로 탭에 남아 차수를 차지했다.
 *   `cancelledAt` 이 안 찍힌 옛 껍데기(일정만 푼 건)도 같이 접는다 — 사장님 눈엔 똑같은 빈 칸이다.
 *   앞줄에서 빼고 '지난 건'으로 접는다. 지우는 게 아니라 접는 거라 되살릴 수 있다.
 */
internal fun jobCancelled(j: com.detailline.callfollowcrm.data.local.entity.JobEntity): Boolean =
    j.cancelledAt != null || jobBlank(j)

/** 앞줄(탭)에서 빼고 접어둘 건 — 마무리됐거나 취소한 것. */
internal fun jobFolded(j: com.detailline.callfollowcrm.data.local.entity.JobEntity): Boolean =
    jobClosed(j) || jobCancelled(j)

/**
 * 이 건이 몇 차인가 — **날짜순**. 탭 줄(JobTabsRow)과 같은 규칙이라 화면 어디서나 숫자가 같다.
 *   (2026-09-18: 메모·사진 제목의 "N차" 가 탭 숫자와 어긋나면 안 된다)
 */
/**
 * 이 건이 **몇 차**인가 — 탭이 매기는 차수와 **똑같은 규칙**이어야 한다.
 *   · 취소한 건·빈 건은 세지 않는다 (탭에서 뺐으니 번호도 안 준다)
 *   · 날짜순, **날짜 없는 건은 맨 뒤**
 *
 * 🔴 2026-09-27 사장님: "1차 시공도 없는데 2차 시공 날짜가 잡히는 건 버그인가?"
 *   날짜 없는 건을 0L(1970년)로 봐서 **맨 앞 = 1차**를 차지했다.
 *   아직 날짜도 안 잡은 시공이 **이미 끝낸 시공보다 먼저**일 수는 없다. → 맨 뒤로.
 *   (지금 건은 원래 맨 뒤 규칙이었다 — 지난 건만 반대여서 어긋났다)
 *
 * 🔴 2026-09-19 사장님: "1차를 고르면 2차 현장메모가 나오고 2차를 고르면 3차 메모가 나오네"
 *   탭은 취소·빈 건을 빼고 세는데 여기선 다 세고 있어서 **번호가 하나씩 밀렸다.**
 *   같은 규칙을 두 군데 따로 적으면 또 어긋난다 — 셈은 여기 하나로 모은다.
 */
internal fun jobNthOf(
    all: List<com.detailline.callfollowcrm.data.local.entity.JobEntity>,
    job: com.detailline.callfollowcrm.data.local.entity.JobEntity
): Int {
    val order = com.detailline.callfollowcrm.util.JobOrder.order(
        all.filterNot { jobCancelled(it) }.map { it.scheduledWorkDate to it.id }
    )
    return com.detailline.callfollowcrm.util.JobOrder.nth(order, job.id)
}




