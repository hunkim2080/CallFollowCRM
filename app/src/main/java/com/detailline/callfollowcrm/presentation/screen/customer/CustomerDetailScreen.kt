package com.detailline.callfollowcrm.presentation.screen.customer

import com.detailline.callfollowcrm.presentation.util.bottomBarClearance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
    onOpenChatEditIssued: (phone: String, customerId: Long, issuedId: Long) -> Unit = { _, _, _ -> }
) {
    val customer by viewModel.customer.collectAsState()
    val records by viewModel.callRecords.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val systemSms by viewModel.systemSms.collectAsState()
    val toast by viewModel.toast.collectAsState()
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
    /** '지난 건' 묶음을 폈는지. (2026-09-18 프로토) */
    var pastOpen by remember { mutableStateOf(false) }
    // 건(件) 탭에서 고른 지난 시공. null = 지금 건(대표 건)을 보는 중. (2026-09-17 B안)
    var selectedPastJobId by remember(customer?.id) { mutableStateOf<Long?>(null) }
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
    // MMS 사진 풀스크린 뷰어 — 썸네일 탭하면 set, 다이얼로그가 보여줌. null 이면 닫힘.
    var fullscreenImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    /** 🔄 크게 보는 그 사진의 id — 돌려서 저장하려면 어느 줄인지 알아야 한다. (2026-09-30) */
    var fullscreenPhotoId by remember { mutableStateOf(0L) }
    // 팀/서버 현장사진(비트맵) 풀스크린 — base64 디코드본이라 Uri 가 아닌 Bitmap.
    var fullscreenBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // 팀원+사장님이 서버에 올린 현장 사진(§25). 고객 전화 알게 되면 가져옴.
    val teamPhotos by viewModel.teamPhotos.collectAsState()
    val teamNotes by viewModel.teamNotes.collectAsState()
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
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // 고객 정보 상단 탭 (2026-07-18 사장님 "크롬 탭식") — 0 일정·정산 / 1 협업 / 2 시공접수서 / 3 블로그(준비중).
    //   이름·전화·주소는 탭 위, 메모·현장사진은 탭 내용 아래(짝) — 주소 바로 밑에 탭이 오게. (2026-09-15 사장님)
    var detailTab by remember(customer?.id) { mutableStateOf(0) }

    // composer 는 bottomBar 로 이동됨. 스크롤 영향 안 받아 bringIntoView 등 복잡한 로직 불필요.
    val scrollState = rememberScrollState()

    // ViewModel 측 toast (인라인 발송 결과 등) → 스낵바로 노출
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeToast()
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

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "고객 정보",
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
        snackbarHost = { SnackbarHost(snackbar) }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
            Column(
                Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(17.dp)
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    // 고객온도 점 제거 (2026-06-14 사장님: 온도 더 안 씀 — 통화 후 카드에서 뺀 것과 일관).
                    Text(
                        headerName, fontSize = if (hasName) 22.sp else 20.sp,
                        fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                        letterSpacing = (-0.6).sp, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        // 남는 자리를 **이름 칸이 다 먹는다**. 전엔 뒤쪽 Spacer 와 자리를 나눠 갖느라
                        //   번호가 "010-484…" 로 잘렸다. (2026-09-20 사장님)
                        modifier = Modifier.weight(1f)
                    )
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
                val headerPrefs = remember(headerCtx) {
                    (headerCtx.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication)
                        .container.preferences
                }
                var nonCustomer by remember(c.id) { mutableStateOf(headerPrefs.isNonCustomer(c.phoneNumber)) }
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(top = 11.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("이 사람은", fontSize = 12.5.sp, color = TossTextTertiary, modifier = Modifier.weight(1f))
                    CustomerKindPill("고객 아님", on = nonCustomer) {
                        nonCustomer = true
                        headerPrefs.answerCustomerAsk(c.phoneNumber, true)
                    }
                    Spacer(Modifier.width(6.dp))
                    CustomerKindPill("고객", on = !nonCustomer) {
                        nonCustomer = false
                        headerPrefs.answerCustomerAsk(c.phoneNumber, false)
                    }
                }
                if (nonCustomer) {
                    Text(
                        "추천 답변·고객 분석·주소 물어보기를 안 해요 (통화 요약은 그대로)",
                        fontSize = 11.sp, color = TossTextTertiary, lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
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
            val extractedAddress by viewModel.extractedAddress.collectAsState()
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            val ctx = LocalContext.current
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
            TossCard {
                Column {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CdTitleIcon(Icons.Filled.Person, "gray")
                        Spacer(Modifier.width(8.dp))
                        Text("이 고객 메모", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary)
                        if (custNotes.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text("${custNotes.size}", fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, color = TossTextTertiary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("적으면 시각이 저절로 붙어요 · 늘 봐야 할 건 꾹 눌러 고정",
                        fontSize = 11.5.sp, color = TossTextTertiary)
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


            if (displayAddr != null) {
                // 프로토 .addr-card — 그라데이션 + 주소 + [길찾기 시작] 큰 파란 버튼.
                val addrInteraction = remember { MutableInteractionSource() }
                Column(
                    Modifier.fillMaxWidth()
                        .pressScale(addrInteraction)
                        .tossCardShadow(RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(AppTheme.colors.primaryBg, Color.White)))
                        .border(1.5.dp, Color(0xFFE2EDFD), RoundedCornerShape(18.dp))
                        .clickable(interactionSource = addrInteraction, indication = null) { showAddressDialog = true }
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
                        Text(if (manualAddress != null) "✏️" else "＋", fontSize = 14.sp, color = TossBlue)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(displayAddr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, lineHeight = 22.sp)
                    if (manualAddress == null) {
                        Spacer(Modifier.height(3.dp))
                        Text("문자에서 자동 인식 · 눌러서 확정/수정", fontSize = 11.sp, color = TossTextTertiary)
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
                                    .clickable { viewModel.updateManualAddress(extractedAddress) }
                                    .padding(vertical = 11.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) { Text("이 주소로 등록", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("직접 고치려면 여기를 누르세요", fontSize = 11.sp, color = TossTextTertiary,
                            modifier = Modifier.clickable { showAddressDialog = true })
                    }
                }
            } else {
                TossCard(onClick = { showAddressDialog = true }) {
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
                AddressEditDialog(
                    currentAddress = manualAddress,
                    extractedSuggestion = extractedAddress?.takeIf { it != manualAddress },
                    onSave = { addr ->
                        viewModel.updateManualAddress(addr)
                        showAddressDialog = false
                    },
                    onCopyExisting = displayAddr?.let { existing ->
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

            // ── 상단 탭 (2026-07-18 사장님 "크롬 탭식") — 위 정보는 항상, 아래 4개 섹션만 탭으로 전환 ──
            run {
                // "블로그"(비즈니스 요금제 예정) 탭은 출시 전까지 숨김 — 눌러도 "곧 제공" 토스트만 뜨는 데드엔드였음.
                //   아래 detailTab==3 블로그 lockcard 블록은 탭이 없어 자동으로 도달 불가(코드는 유지). 2026-07-29.
                val detailTabs = listOf("일정·정산", "협업", "시공접수서")
                // 이 모양이 앱의 탭 기준이 됐다 → 공용 부품으로 옮김(AppTabs). (2026-09-21 사장님)
                com.detailline.callfollowcrm.presentation.component.AppTabs(tabs = detailTabs, selected = detailTab, onSelect = { detailTab = it })
            }

            // 1.4 협업 현장으로 공유 (collab-sites-proto a-card) — 다른 사장님과 이 현장 하나만 같이.
            //   예약(시공일)이 잡힌 고객만 = 진짜 "현장". 상담 단계(날짜 없음)는 공유할 현장이 없으니 섹션 숨김.
            //   (2026-06-11 사장님 요청) · [협업] 탭. (2026-07-18 탭 재배치)
            if (detailTab == 1) {
                if (c.scheduledWorkDate == null) {
                    DetailTabEmpty("시공일이 잡히면 이 현장을 다른 사장님과 협업할 수 있어요.\n먼저 ‘일정·정산’ 탭에서 시공일을 잡아주세요.")
                } else {
                    val siteTitle = com.detailline.callfollowcrm.util.AddressExtractor.siteLabel(displayAddr).takeIf { it.isNotBlank() }?.let { "$it 현장" }
                        ?: c.name?.takeIf { it.isNotBlank() && it.count { ch -> ch.isDigit() } < 9 }?.let { "$it 현장" }
                        ?: "이 현장"
                    // 이 고객으로 공유한 협업 사장님(로컬 기록) — Triple(phone, name, shareId). 번호 끝 8자리 dedup.
                    val collabPartners = remember(c.id, collabRefresh) {
                        container.preferences.collabAssignments.mapNotNull { e ->
                            val p = e.split("|")
                            if (p.size >= 3 && p[0].toLongOrNull() == c.id) Triple(p[1], p[2], p.getOrNull(3).orEmpty()) else null
                        }.distinctBy { PhoneKey.of(it.first) }
                    }
                    // 협업 사장님 부르기 — 예전엔 일정→전문가배정으로만(2026-06-13). 사장님 요청(2026-08-09)으로 협업 탭에서도 바로.
                    //   협업 사장 전용 간단 시트(CollabShareSheet, 이미 구현됨) 재연결. 팀원 배정은 일정에 그대로.
                    val showCollabShare = remember(c.id) { mutableStateOf(false) }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppTheme.colors.categoryBg)
                            .clickable { showCollabShare.value = true }.padding(vertical = 13.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
                                Icons.Filled.Handshake, null, tint = AppTheme.colors.categoryText,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("협업 사장님 부르기", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.categoryText)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (showCollabShare.value) {
                        CollabShareSheet(
                            siteTitle = siteTitle, addr = displayAddr, scheduledAtMs = c.scheduledWorkDate,
                            customerId = c.id,
                            // 이미 부른 사장님 — 줄에 「요청함」을 붙이기 위해.
                            requestedPhones = collabPartners.map { it.first },
                            onShared = { collabRefresh++ }, onDismiss = { showCollabShare.value = false }
                        )
                    }
                    // 여기선 협업 중인 사장님 진행 표시.
                    if (collabPartners.isNotEmpty()) {
                        Text("협업 중", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
                        collabPartners.forEach { (partnerPhone, partnerName, shareId) ->
                            CollabAfterCard(
                                partnerName = partnerName.ifBlank { "협업 사장님" },
                                siteTitle = siteTitle,
                                shareId = shareId,
                                onRelease = {
                                    // 📮 협업 해제 → 우체통. B 에게 알림은 연결되면 간다(옛날엔 runCatching 에 삼켜져 말도 없었다). (설계 §1-A #3·§7-B)
                                    if (shareId.isNotBlank()) {
                                        container.applicationScope.launch {
                                            runCatching {
                                                container.outbox.enqueue(
                                                    com.detailline.callfollowcrm.domain.outbox.OutboxKind.COLLAB_END,
                                                    shareId,
                                                    org.json.JSONObject().put("mode", "end_owner").toString()
                                                )
                                            }
                                            container.outbox.tryNow()
                                        }
                                    }
                                    container.preferences.collabAssignments = container.preferences.collabAssignments
                                        .filterNot { e ->
                                            val p = e.split("|")
                                            p.size >= 3 && p[0].toLongOrNull() == c.id &&
                                                PhoneKey.of(p[1]) == PhoneKey.of(partnerPhone)
                                        }.toSet()
                                    collabRefresh++
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    } else {
                        DetailTabEmpty("아직 협업 중인 현장이 없어요.\n위 ‘협업 사장님 부르기’로 불러보세요.")
                    }
                }
            }

            // 1.5 (제거) AI 대화 요약 — 챗스크린에 이미 있어 중복이라 고객 상세에선 뺌. (2026-07-18 사장님)

            // 2-0. 건(件) 탭 — 한 고객의 시공이 여러 번일 때. (2026-09-17 사장님 "B안이 괜찮다")
            //   프로토 artifact/KKMyncpHy1Ni7GK6dygMRY 의 B안(탭형). 크롬 새 탭처럼 건을 옆으로 늘어놓는다.
            //   한 번에 **한 건만** 보여준다 → 화면이 짧다. `＋` 는 이 고객으로 새 시공 잡기.
            //   건이 하나뿐이면 탭을 안 띄운다 — 있으나 마나 한 줄이 자리만 먹는다.
            // 탭은 이 고객의 **모든 건**을 보여준다 — 지난 것도, 앞으로 잡힌 것도. (2026-09-17)
            //   전엔 완료된 건만 봐서 예정 건을 둘 잡아도 탭이 하나였다.
            val allJobsForTabs by viewModel.allJobs.collectAsState()
            val repDay = c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) }
            // 대표 건 = 고객 카드가 지금 보여주고 있는 그 건(같은 날짜). 이건 '지금 건' 탭으로 따로 그린다.
            val repJobId = allJobsForTabs.firstOrNull { j ->
                j.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } == repDay && repDay != null
            }?.id
            val otherJobs = allJobsForTabs.filter { it.id != repJobId }
            val selectedPastJob = otherJobs.firstOrNull { it.id == selectedPastJobId }
            // 지금 보고 있는 건 — 메모·사진이 이걸 따라간다. (2026-09-18 프로토)
            // 🔴 **건이 있으면 반드시 그 건을 가리킨다.** repJobId 가 날짜 불일치로 null 이어도
            //   대표 건으로 메꾼다 → 돈·주소 편집이 고객 공유칸(→대표건 미러로 1차 덮임)으로 새지 않는다.
            //   (2026-10-07 사장님 "2차 총금액 고치면 1차가 같이 묶여 바뀐다")
            val shownJobId = selectedPastJobId ?: repJobId
                ?: com.detailline.callfollowcrm.domain.job.RepresentativeJob.pick(
                    allJobsForTabs.sortedBy { it.scheduledWorkDate ?: 0L },
                    DateTimeUtils.startOfDay(System.currentTimeMillis())
                )?.id
            val shownJob = allJobsForTabs.firstOrNull { it.id == shownJobId }
            // 건 줄은 **시공이 둘 이상일 때만** 띄운다. (2026-09-18 확정 프로토 artifact/4ZvDfUfxDAQU8uNNvQQ1h1)
            //   "보통 손님은 시공을 한 번만 받는다. 그런 손님 화면에 '1차'라는 말과 탭 줄을 넣으면
            //    100명 중 95명한테 쓸데없는 줄 하나를 얹는 것" — 그게 '지저분하다'의 정체.
            //   1건일 때의 두 번째 시공 입구는 **맨 아래 조용한 링크**(＋ 시공 하나 더 잡기)로 옮겼다.
            val showJobBar = otherJobs.isNotEmpty()
            // 지금 고른 건의 **차수 꼬리표**("2차 "). 시공이 하나뿐이면 빈 문자열.
            //   메모·사진 제목이 각자 만들다가 번호가 어긋났다 → 한 곳에서 만든다. (2026-09-19 사장님)
            val jobNthPrefix = if (showJobBar && shownJob != null)
                "${jobNthOf(allJobsForTabs, shownJob)}차 " else ""

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
                val fillTarget = allJobsForTabs
                    .filter { !jobFolded(it) && it.address.isNullOrBlank() }
                    .minByOrNull { it.scheduledWorkDate ?: Long.MAX_VALUE }
                val show = detailTab == 0 && caught != null && !addrSuggestDismissed &&
                    !isGeneralThread && closedJobs.isNotEmpty() && !alreadyUsed
                if (show && caught != null) {
                    val whyText = if (showJobBar)
                        closedJobs.joinToString("·") { "${jobNthOf(allJobsForTabs, it)}차" } + "는 잔금까지 받아서 마무리됐어요."
                    else "이번 시공은 잔금까지 받아서 마무리됐어요."
                    val askText = if (fillTarget != null)
                        "진행 중인 ${jobNthOf(allJobsForTabs, fillTarget)}차 현장 주소인가요?"
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
                                        if (fillTarget != null) {
                                            viewModel.setJobAddress(fillTarget.id, caught)
                                            selectedPastJobId = fillTarget.id
                                        } else {
                                            viewModel.addJobWithAddress(caught) { id ->
                                                if (id > 0L) selectedPastJobId = id
                                            }
                                        }
                                    }.padding(vertical = 11.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) { Text(yesText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (detailTab == 0 && showJobBar) {
                JobTabsRow(
                    pastJobs = otherJobs,
                    current = c,
                    selectedPastJobId = selectedPastJobId,
                    closedCount = otherJobs.count { jobFolded(it) },
                    pastOpen = pastOpen,
                    onTogglePast = { pastOpen = !pastOpen },
                    onSelect = { selectedPastJobId = it },
                    // ＋ 는 **새 건을 만든다.** 전엔 날짜 고르기만 열고 그 날짜를 지금 건에 덮어썼다. (2026-09-17)
                    onAddNew = {
                        // ② 날짜를 아직 안 정한 건이 있으면 새로 만들지 않는다. (2026-09-18 사장님)
                        //    그 건으로 데려가 날짜부터 넣게 한다 — 날짜 없는 건이 줄줄이 생기는 걸 막는다.
                        //    ※ 1차에 날짜가 있는데 2차를 미리 잡는 건 그대로 열어둔다.
                        //   '지금 자리'가 이미 날짜 미정이면 그것도 빈 자리다 — 취소 직후가 그렇다. (2026-09-18 실기)
                        val pending = allJobsForTabs.firstOrNull {
                            it.scheduledWorkDate == null && !jobFolded(it)
                        }
                        val currentDateless = customer?.scheduledWorkDate == null
                        if (pending != null || currentDateless) {
                            selectedPastJobId = pending?.id?.takeIf { it != repJobId }
                            android.widget.Toast.makeText(
                                ctx, "날짜를 아직 안 정한 시공이 있어요. 그 날짜부터 넣어주세요",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            selectedPastJobId = null; addingNewJob = true; datePickerOpen = true
                        }
                    }
                )
            }
            // 2-0-b. '지난 건'을 펼쳤을 때 — 마무리된 건 목록. (2026-09-18 프로토 `.past-list`)
            //   "펼치면 목록이 나오고, 고르면 그 건이 열려요."
            if (detailTab == 0 && showJobBar && pastOpen) {
                val closedJobs = remember(otherJobs) {
                    otherJobs.filter { jobFolded(it) }.sortedBy { it.scheduledWorkDate ?: 0L }
                }
                if (closedJobs.isNotEmpty()) {
                    TossCard {
                        Column {
                            Text(
                                "마무리·취소한 건 · 고르면 열려요",
                                fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary
                            )
                            Spacer(Modifier.height(4.dp))
                            closedJobs.forEach { j ->
                                androidx.compose.foundation.layout.Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { selectedPastJobId = j.id }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            (if (jobCancelled(j)) (if (j.cancelledAt != null) "취소한 건" else "빈 건")
                                             else "${jobNthOf(allJobsForTabs, j)}차") + " · " +
                                                (j.scheduledWorkDate?.let { DateTimeUtils.formatDateLabel(it) } ?: "날짜 미정"),
                                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                            color = if (selectedPastJobId == j.id) TossBlue else TossTextPrimary
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            listOfNotNull(
                                                j.address?.takeIf { it.isNotBlank() } ?: "주소 없음",
                                                j.totalAmount?.let { com.detailline.callfollowcrm.util.MoneyFormatter.won(it) },
                                                when {
                                                    j.cancelledAt != null -> "예약 취소함"
                                                    jobBlank(j) -> "비어 있던 건"
                                                    j.balancePaidAt != null -> "잔금 받음"
                                                    else -> "완료"
                                                }
                                            ).joinToString(" · "),
                                            fontSize = 11.5.sp, color = TossTextTertiary, maxLines = 1
                                        )
                                    }
                                    // 취소/빈 건만 삭제 — 살아있는 건(금액·일정 있는)은 실수 삭제 방지. (2026-10-06 사장님)
                                    if (jobCancelled(j) || jobBlank(j)) {
                                        Text(
                                            "삭제", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                            color = AppTheme.colors.unpaid,
                                            modifier = Modifier.clip(AppShape.sm)
                                                .clickable { deletePastJobConfirm = j }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    } else {
                                        Text("›", fontSize = 18.sp, color = TossTextTertiary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2-1. 지난 건을 고른 상태 — 그 건의 기록만 보여준다(읽기 전용).
            //   지난 건은 이미 끝난 일이라 여기서 고칠 게 없다. 고칠 일이 생기면 그때 붙인다.
            // (제거) 지난 건 읽기 전용 패널 — 이제 아래 정산 카드가 **고른 건 값으로** 바뀌고
            //   그 건에 바로 쓴다. 따로 읽기 전용 카드를 겹쳐 보여줄 이유가 없다. (2026-09-18 프로토 ④)

            // 2. 프로토 "일정 · 정산" 카드 (사장님 결정 2026-06-02: 프로토 단순화). · [일정·정산] 탭 (2026-07-18)
            //    데이터(예약일·금액·계약금/잔금)는 그대로, UI 만 프로토 단순형(시공예약+총금액+계약금/잔금 상태+확인).
            //    지난 건을 보는 중이면 숨긴다 — 탭은 **한 번에 한 건**이 규칙이다.
            if (detailTab == 0) run {
                // 어느 건을 골랐든 **그 건의 값**을 보여주고, 그 건에 쓴다. (2026-09-18 확정 프로토 ④)
                //   전엔 지난 건을 고르면 이 카드를 숨기고 읽기 전용 패널만 보여줬다.
                //   editJobId = null 이면 '지금 건'(고객 카드 경로), 아니면 그 건에 직접 쓴다.
                // 🔴 지난 건뿐 아니라 **지금 보는 건(대표 포함)**을 그 건의 값으로 — shownJob + 공용 JobCardRow.
                //   전엔 대표 건을 고객 카드(c)로 그려, 2차가 대표가 되면 1차 돈·완납이 묻어났다. (2026-10-05 Fable 감사)
                //   건이 하나도 없는 고객만 null → 고객 카드 경로(옛 데이터·날짜 미정).
                val editJobId: Long? = shownJobId
                val cShown = shownJob?.let { com.detailline.callfollowcrm.domain.job.JobCardRow.rowOf(c, it) } ?: c
                val scheduled = cShown.scheduledWorkDate
                val totalWon = cShown.totalAmount ?: 0L
                val depositWon = cShown.depositAmount ?: 0L
                // 총금액 또는 계약금 중 하나라도 입력되면 정산 영역을 펼친다(계약금만 따로 넣는 경우 포함).
                val hasAmount = totalWon > 0L || depositWon > 0L
                // 잔금·완납은 정산 단일 출처(SettlementCalc)로 — 화면마다 잔금이 다르던 버그 통일. (2026-07-30)
                val settle = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(cShown)
                val balanceWon = settle.balanceAmount
                val depPaid = cShown.depositPaidAt != null
                val balPaid = cShown.balancePaidAt != null
                val allPaid = settle.isPaidOff
                TossCard {
                    Column {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CdTitleIcon(Icons.Filled.Payments, "amber")
                            Spacer(Modifier.width(8.dp))
                            Text("일정 · 정산", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                        }
                        Spacer(Modifier.height(10.dp))
                        // 화면을 둘로 가르지 않는다 — 표가 이미 "아직 예약 안 됨" 같은 빈 값을 말할 줄 안다.
                        //   전엔 예약·금액이 하나도 없으면 버튼만 있는 **딴 화면**이 떴다. (2026-09-22 사장님)
                        if (scheduled == null && !hasAmount) {
                            // 2026-06-07 사장님 통점: 통화로 다 정해졌는데 일정 등록하려면 견적서 보내기밖에 없었음.
                            //   그 안내는 아무것도 없을 때만 한 줄로 남긴다.
                            Text(
                                "통화로 정해졌으면 여기서 바로 등록하세요. 고객에게 또 입력시키지 않아도 돼요.",
                                fontSize = 13.sp, color = TossTextSecondary, lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        CdKv(
                                "시공 예약",
                                // 여러 날 시공이면 기간까지 — 전엔 시작 날짜만 보여 "3일 중 1일차"인 걸 알 수 없었다. (2026-09-15 사장님)
                                //   날짜는 짧게("9/16(수)") — 올해 일이면 연도는 정보가 아니다. (2026-09-20 사장님)
                                if (scheduled != null)
                                    DateTimeUtils.formatShortKoreanDate(scheduled) +
                                        (c.scheduledWorkMinutes?.let { " " + DateTimeUtils.formatWorkMinutes(it) } ?: "") +
                                        DateTimeUtils.workPeriodSuffix(scheduled, c.scheduledWorkDays)
                                else "아직 예약 안 됨 · 눌러서 설정",
                                valueColor = if (scheduled != null) AppTheme.colors.text else TossTextTertiary,
                                trailing = if (scheduled != null) Icons.Default.Edit else Icons.Default.Add,
                                onClick = { datePickerOpen = true }
                            )
                            // 🔧 **A/S 해줘야 하는 손님** — 날짜를 못 잡았어도 여기 뜬다. (2026-09-27 사장님)
                            //   "일정이 꽉 차 있어서 못 잡는 거야. 그래서 as 대기 고객으로 명단을 올려두고"
                            if (c.asPendingAt != null) {
                                CdKv(
                                    "A/S",
                                    if (c.asScheduledDate != null) "예약 잡힘 · 무료"
                                    else "해드릴 곳 · 날짜 미정",
                                    valueColor = AppTheme.colors.cautionText,
                                    trailing = Icons.Default.Check,
                                    onClick = { asDoneConfirm = true }
                                )
                            }
                            // 🔧 A/S 예약 — 시공과 별개, 무료. (2026-08-01 사장님)
                            //   **잡혀 있을 때만** 줄로 보여준다. A/S 는 가끔 있는 일인데
                            //   늘 「아직 없음」이 한 줄을 먹고 있었다 — 정보가 아니라 빈 줄이다.
                            //   안 잡혔을 땐 카드 맨 아래 작은 「A/S 잡기」로 물러난다. (2026-09-26 사장님)
                            if (c.asScheduledDate != null) {
                                CdKv(
                                    "A/S 예약",
                                    DateTimeUtils.formatShortKoreanDate(c.asScheduledDate!!) +
                                        (if (c.asScheduledDays > 1) " · ${c.asScheduledDays}일" else "") + " · 무료",
                                    valueColor = AppTheme.colors.text,
                                    trailing = Icons.Default.Edit,
                                    onClick = { asPickerOpen = true }
                                )
                            }
                            if (hasAmount) {
                                // 돈도 예약과 **같은 줄 모양**으로 — 이름 왼쪽 · 값 오른쪽 · 연필은 같은 크기.
                                //   전엔 금액이 왼쪽에 크게 있고 버튼이 오른쪽이라 두 줄의 끝이 들쭉날쭉했다. (2026-09-20 사장님)
                                CdKv(
                                    "총 금액",
                                    if (totalWon > 0L) manwonLabel(totalWon) else "미입력",
                                    valueColor = if (totalWon > 0L) AppTheme.colors.text else TossTextTertiary,
                                    trailing = if (totalWon > 0L) Icons.Default.Edit else Icons.Default.Add,
                                    onClick = { amountEditJobId = editJobId; amountEditField = "total" }
                                )
                                // 계약금 — 총금액과 별개로 따로 입력/수정 (2026-06-11 사장님: 계약금을 따로 넣어야 함).
                                CdKv(
                                    "계약금",
                                    if (depositWon > 0L) manwonLabel(depositWon) + (if (depPaid) " · 받음" else "") else "미설정",
                                    valueColor = if (depositWon > 0L) AppTheme.colors.text else TossTextTertiary,
                                    trailing = if (depositWon > 0L) Icons.Default.Edit else Icons.Default.Add,
                                    onClick = { amountEditJobId = editJobId; amountEditField = "deposit" }
                                )
                                CdKv(
                                    "잔금",
                                    if (allPaid) "전액 완납" else manwonLabel(balanceWon) + " 남음",
                                    valueColor = if (allPaid) AppTheme.colors.doneText else AppTheme.colors.text,
                                    divider = false
                                )
                                Spacer(Modifier.height(12.dp))
                                if (allPaid) {
                                    // 되돌리는 것(완납 취소·예약 취소)은 맨 아래 한 줄로 모은다 — 아래 CdUndoRow.
                                } else if (depositWon > 0L && !depPaid) {
                                    TossPrimaryButton(text = "계약금 확인", onClick = {
                                        if (editJobId != null) viewModel.setJobDepositPaid(editJobId, true)
                                        else viewModel.setDepositPaid(true)
                                    })
                                } else if (depositWon > 0L) {
                                    TossPrimaryButton(text = "잔금 확인", onClick = {
                                        if (editJobId != null) viewModel.setJobBalancePaid(editJobId, true)
                                        else {
                                            if (c.balanceAmount == null && balanceWon > 0L) viewModel.setBalanceAmount(balanceWon)
                                            viewModel.setBalancePaid(true)
                                        }
                                    })
                                } else {
                                    TossPrimaryButton(text = "전액 확인", onClick = {
                                        if (editJobId != null) viewModel.setJobBalancePaid(editJobId, true)
                                        else {
                                            if (c.balanceAmount == null && totalWon > 0L) viewModel.setBalanceAmount(totalWon)
                                            viewModel.setBalancePaid(true)
                                        }
                                    })
                                }
                            } else {
                                Spacer(Modifier.height(12.dp))
                                TossSecondaryButton(text = "총금액 입력", onClick = { amountEditJobId = editJobId; amountEditField = "total" })
                                Spacer(Modifier.height(8.dp))
                                TossSecondaryButton(text = "계약금 입력", onClick = { amountEditJobId = editJobId; amountEditField = "deposit" })
                            }
                            // 되돌리는 것 둘을 **맨 아래 한 줄**로. (2026-09-20 사장님)
                            //   전엔 [완납 취소] 가 가로로 꽉 찬 큰 버튼이라 이 카드에서 제일 누르기 쉬웠다.
                            //   예약 취소는 카드에 보여야 한다(전엔 날짜 팝업 안에 숨어 못 찾음 — 2026-08-28).
                            if (allPaid || scheduled != null) {
                                Spacer(Modifier.height(10.dp))
                                // 📐 **넘치면 다음 줄로.** (2026-09-28 사장님 "디자인이.. 왜저래")
                                //   Row 에 칩을 넷 넣었더니 마지막 칩이 폭이 모자라
                                //   「시공/예약/취소」로 **글자가 세로로 쪼개졌다.**
                                FlowRow(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp,
                                        androidx.compose.ui.Alignment.End),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 🔧 A/S 가 아직 없을 때의 **조용한 길**. 잡히면 위에 줄로 올라간다.
                                    //   시공 예약이 있을 때만 — A/S 는 시공 **뒤**의 일이다.
                                    if (scheduled != null && c.asScheduledDate == null) {
                                        CdUndoChip("A/S 잡기", danger = false) { asPickerOpen = true }
                                    }
                                    // 🔧 날짜를 못 잡아도 **명단에는 올려둔다.** (2026-09-27 사장님)
                                    if (c.asPendingAt == null) {
                                        // 이름이 **하는 일**을 말하게. (2026-09-27 사장님)
                                        //   「A/S 해드릴 곳」은 이름표라 누르면 뭐가 되는지 알 수 없었다.
                                        CdUndoChip("A/S 명단에 올리기", danger = false) { asPendingConfirm = true }
                                    }
                                    if (allPaid) {
                                        // 💰 **돈은 물어보고 뒤집는다.** (2026-09-27 사장님)
                                        //   옆의 [시공 예약 취소]엔 확인 창이 있는데 이것만 없었다.
                                        //   처음 보는 분이 뜻을 알려고 누르면 **받은 돈이 안 받은 돈이 되고**
                                        //   상담함 [잔금 대기] 에 없던 손님이 올라온다.
                                        var unpaidConfirm by remember { mutableStateOf(false) }
                                        if (unpaidConfirm) {
                                            androidx.compose.material3.AlertDialog(
                                                tonalElevation = 0.dp,
                                                onDismissRequest = { unpaidConfirm = false },
                                                title = {
                                                    Text("잔금을 아직 못 받으신 걸로 되돌릴까요?",
                                                        fontWeight = FontWeight.Bold, color = TossTextPrimary)
                                                },
                                                text = {
                                                    Text(
                                                        "이 손님이 상담함 [잔금 대기] 로 다시 올라오고, " +
                                                            "정산에서도 못 받은 돈으로 잡혀요.",
                                                        style = AppType.body, color = TossTextSecondary
                                                    )
                                                },
                                                confirmButton = {
                                                    TextButton(onClick = {
                                                        unpaidConfirm = false
                                                        if (editJobId != null) viewModel.setJobBalancePaid(editJobId, false)
                                                        else viewModel.setBalancePaid(false)
                                                    }) {
                                                        Text("되돌릴게요", color = TossBlue, fontWeight = FontWeight.Bold)
                                                    }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = { unpaidConfirm = false }) {
                                                        Text("그냥 둘게요", color = TossTextSecondary)
                                                    }
                                                },
                                                containerColor = Color.White
                                            )
                                        }
                                        CdUndoChip("완납 취소", danger = false) { unpaidConfirm = true }
                                    }
                                    // 🚫 **잔금까지 받은 일은 「예약 취소」가 말이 안 된다.** (2026-09-28 사장님)
                                    //   "잔금까지 다 받았는데 시공예약 취소가 떠야 할까?"
                                    //   끝난 일을 되돌리려면 **[완납 취소]가 먼저**다 — 순서가 있는 일이라
                                    //   둘을 나란히 놔두면 돈 기록을 건너뛰고 예약만 지울 수 있다.
                                    if (scheduled != null && !allPaid) {
                                        CdUndoChip("시공 예약 취소", danger = true) { cancelBookingConfirm = true }
                                    }
                                }
                            }
                    }
                }
            }

            // (입금 카드는 위 "일정 · 정산" 카드로 통합됨 — 2026-06-02 사장님 결정.)
            // (메모 카드는 프로토 순서대로 "일정 · 정산" 위로 이동 — 2026-06-03.)

            // 4. 옛 "문자" 카드 제거 (2026-05-28 사장님 보고):
            //    "📩 주고받은 문자" 접이식 섹션이 대화요약 아래로 올라옴 → 여기 중복.
            //    MessageRow/MessageRowView 는 dead code 가능성 → 다른 사용처 없으면 추후 정리.

            // 5·6. (제거) "통화 기록" + "에이닷 통화 요약" 카드 — 프로토 openCustomer 엔 없음.
            //   2026-06-04 사장님 결정 "프로토 100%". 통화기록/녹음/에이닷 요약 데이터 수집(backfill·
            //   AdotSummaryImporter)은 그대로 동작 — 고객정보 화면에서 카드로 노출만 안 함.

            // 6.55 발행 이력 (2026-07-07 사장님) — 이 고객에게 발행한 견적서/시공접수서. · [시공접수서] 탭 (2026-07-18)
            //   위치 일관성(2026-09-01 사장님): 다른 탭처럼 '탭 내용'이 현장사진 위. 시공접수서 내용(발행이력)을 현장사진 앞으로.
            val issuedDocs by viewModel.issuedDocs.collectAsState()
            val intakeByToken by viewModel.intakeByToken.collectAsState()
            if (detailTab == 2 && issuedDocs.isNotEmpty()) {
                TossCard {
                    Column {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CdTitleIcon(Icons.Filled.ReceiptLong, "blue")
                            Spacer(Modifier.width(8.dp))
                            // 「발행 이력」은 장부 말이다. (2026-09-27 사장님 "보낸문서 좋아")
                            Text(
                                "보낸 문서 ${issuedDocs.size}건",
                                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary
                            )
                        }
                        // 안내문은 지운다 — 카드의 꺾쇠와 버튼이 이미 같은 말을 한다.
                        Spacer(Modifier.height(10.dp))
                        issuedDocs.forEachIndexed { idx, doc ->
                            if (idx > 0) Spacer(Modifier.height(8.dp))
                            IssuedDocRow(
                                doc = doc,
                                onOpen = {
                                    if (doc.kind == "quote") {
                                        com.detailline.callfollowcrm.presentation.screen.chat.quoteDocDataFromIssuedJson(doc.docJson)
                                            ?.let { reviewQuoteDoc = it }
                                            ?: run { intakeReviewDoc = doc }  // JSON 깨졌으면 요약으로 폴백
                                    } else {
                                        intakeReviewDoc = doc
                                    }
                                },
                                // 이미 보낸 접수서 수정하기 — intake 만. 채팅으로 이동하며 그 접수서 편집기 재오픈. (2026-07-10 사장님)
                                phone = c.phoneNumber,
                                // 🔒 **손님이 쓴 접수서는 고칠 수 없다.** (2026-09-27 사장님)
                                //   "손님이 작성한 경우에는 고쳐서 다시 보낼수없어. 그럼 견적을 바꿔버린다는거잖아."
                                //   손님이 **보고 동의해서 보낸** 것을 나중에 고치면, 그 동의가 가리키는 게 바뀐다.
                                onEdit = if (doc.kind == "intake" && doc.token?.let { tk -> intakeByToken[tk] } == null)
                                    ({ onOpenChatEditIssued(c.phoneNumber, c.id, doc.id) }) else null,
                                // 🔒 **손님이 쓴 접수서는 지울 수도 없다.** (2026-09-27 사장님)
                                //   "분쟁이 있을때 이 접수서를 보고 이야기할수있어야하거든"
                                //   고치기를 막는 이유와 같다 — 통째로 없어지면 증거 자체가 사라진다.
                                onDelete = if (doc.token?.let { tk -> intakeByToken[tk] } == null)
                                    ({ issuedDocToDelete = doc }) else null,
                                // 바꿀 게 있으면 **새로 보낸다** — 옛것은 그대로 기록으로 남는다.
                                onNewDoc = { onOpenChat(c.phoneNumber, c.id) },
                                // 📋 고객이 채워 보낸 게 있으면 같은 토큰으로 엮어 붙인다.
                                filled = doc.token?.let { tk -> intakeByToken[tk] }
                            )
                        }
                    }
                }
            }

            // [시공접수서] 탭인데 발행 이력이 없을 때 안내. (2026-07-18 탭 재배치)
            if (detailTab == 2 && issuedDocs.isEmpty()) {
                DetailTabEmpty("아직 발행한 견적서·시공접수서가 없어요.\n채팅에서 견적서·시공접수서를 보내면 여기에 쌓여요.")
            }

            // 📍 이 현장 메모 — **그 건에서만.** 손님 메모(👤)와 **같은 얼굴**(쪽지)로. (2026-09-27 사장님)
            //   "메모는 두 곳. 👤 이 손님 메모(현장이 바뀌어도 그대로인 것)와
            //    📍 이 현장 메모(그 건에서만)."
            //   둘 다 NoteList 하나를 쓴다 — 따로 만들면 또 어긋난다.
            if (shownJobId != null) {
                val jid = shownJobId
                val jobNotes by remember(jid) { viewModel.jobNotes(jid) }
                    .collectAsState(initial = emptyList())
                TossCard {
                    Column {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CdTitleIcon(Icons.Filled.EditNote, "gray")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (jobNthPrefix.isNotEmpty()) "${jobNthPrefix}현장 메모" else "이 현장 메모",
                                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary
                            )
                            if (jobNotes.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Text("${jobNotes.size}", fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold, color = TossTextTertiary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("이 현장에서만 · 주차, 열쇠, 자재", fontSize = 11.5.sp, color = TossTextTertiary)
                        Spacer(Modifier.height(8.dp))
                        com.detailline.callfollowcrm.presentation.component.NoteList(
                            notes = jobNotes,
                            onAdd = { viewModel.addJobNote(jid, it) },
                            onEdit = { editingNote = it },
                            onTogglePin = { viewModel.togglePinNote(it.id) },
                            onDelete = { viewModel.deleteNote(it.id) },
                            placeholder = "이 현장에서 기억할 것"
                        )
                    }
                }
            }

            // 6.5 현장 사진 (프로토 openCustomer) — 사장님이 갤러리에서 골라 올림(로컬 저장). 2026-06-04 활성화.
            //   ※ 팀원↔사장님 공유는 서버(team_site_photos) 보강 후 별도 연동 — docs/SERVER_HANDOFF 참조.
            val allSitePhotos by viewModel.sitePhotos.collectAsState()
            // 🔴 사진도 **그 건 것만.** 1차 사진이 2차 탭에 보이면 안 된다.
            //   (2026-09-18 사장님: "현장사진도 1차 2차 개별로 들어가야해")
            //   어느 건인지 안 붙은 옛 사진(jobId=null)은 **대표 건**에 붙여 보여준다 — 안 그러면
            //   지금까지 올린 사진이 통째로 안 보이게 된다.
            val sitePhotos = remember(allSitePhotos, shownJobId, repJobId) {
                allSitePhotos.filter { p ->
                    p.jobId == shownJobId || (p.jobId == null && shownJobId == repJobId)
                }
            }
            // 카톡식 사진 첨부 시트(아래→위) — 기존 시스템 "내 파일" 피커는 권한 거부/구형 fallback 으로만 유지. (2026-06-11)
            var showPhotoPicker by remember { mutableStateOf(false) }
            // 사진 첨부 = 시스템 Photo Picker(권한 없음, Play 정책 + 사진 우선 화면).
            //   기존 GetMultipleContents(ACTION_GET_CONTENT)는 삼성 "항목 선택" 폴더 탐색기라 연세 있으신 분들이 헤맴
            //   → PickMultipleVisualMedia(깔끔한 사진 그리드)로 교체. (2026-07-06 사장님)
            val photoPicker = rememberLauncherForActivityResult(
                // 2026-08-24 사장님: 본인 현장 사진도 한 번에 10 → 20장 (협업과 통일, 총량 sitePhotoMax=20).
                ActivityResultContracts.PickMultipleVisualMedia(20)
            ) { uris -> if (uris.isNotEmpty()) viewModel.addSitePhotos(uris, selectedPastJobId ?: repJobId) }
            val launchPhotoPicker = { showPhotoPicker = true }
            val photoMax = viewModel.sitePhotoMax
            val photoTotal = sitePhotos.size + teamPhotos.size
            // 🏅 **대표 사진** — 그 현장에 제일 먼저 올린 사진. 인증샷에 이게 들어간다. (2026-09-25 사장님)
            //   목록은 최신이 먼저라 대표는 보통 **맨 끝 칸**에 있다 → 위치로는 알 수 없으니 딱지를 붙인다.
            // 🔒 인증샷과 **같은 규칙**으로 고른다(두 벌이면 서로 다른 사진을 가리킨다 — 2026-09-25 점검).
            //   observe 는 최신순이라, 규칙이 요구하는 '올린 순서'로 뒤집어 넘긴다.
            val repCustomerId = customer?.id ?: 0L
            val repPhotoId = remember(sitePhotos, shownJobId, repCustomerId) {
                com.detailline.callfollowcrm.data.repository.SitePhotoRepository.representativeOf(
                    sitePhotos.sortedBy { it.createdAt }, repCustomerId, shownJobId
                )?.id
            }
            LaunchedEffect(showPhotoPicker) {
                if (showPhotoPicker) {
                    showPhotoPicker = false
                    photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            // 사진이 많아도 카드가 화면을 통째로 먹지 않게 — 두 줄까지만 보이고 나머지는 접는다.
            var photosExpanded by remember(shownJobId) { mutableStateOf(false) }
            // [고르기] 를 눌렀을 때만 동그라미가 뜬다. 평소엔 사진을 눌러 크게 본다. (2026-09-22 사장님)
            var pickMode by remember(shownJobId) { mutableStateOf(false) }
            var pickedMine by remember(shownJobId) { mutableStateOf(setOf<Long>()) }
            var pickedTeam by remember(shownJobId) { mutableStateOf(setOf<Long>()) }
            val pickedCount = pickedMine.size + pickedTeam.size
            fun exitPickMode() {
                pickMode = false; pickedMine = emptySet(); pickedTeam = emptySet()
            }
            // 🛟 **서버에만 남은 내 사진** — 폰을 재설치하면 이렇게 된다. (2026-09-28 사장님)
            //   사장님: "그럼 직원폰에 그대로 보이게 해줄수 있는거아니야?"
            //   맞다 — 서버에 살아 있으니 내려받으면 된다. 보기만 하면 반쪽이라
            //   **되살려서** 내 기록·영상·백업에도 다시 들어가게 한다.
            val restorable by viewModel.restorable.collectAsState()
            if (restorable.isNotEmpty()) {
                TossCard {
                    Column(Modifier.padding(2.dp)) {
                        Text("서버에 이 현장 사진 ${restorable.size}장이 있어요",
                            style = AppType.headline, color = TossTextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("폰을 다시 깔면서 사진이 빠졌어요. 되살리면 내 기록·영상에도 다시 들어가요.",
                            style = AppType.caption, color = TossTextTertiary)
                        Spacer(Modifier.height(11.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(AppShape.md).background(TossBlue)
                                .clickable { viewModel.restorePhotosFromServer() }
                                .padding(vertical = 13.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text("사진 ${restorable.size}장 되살리기",
                                style = AppType.label, color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            TossCard {
                Column {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CdTitleIcon(Icons.Filled.PhotoCamera, "blue")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            // 사진도 그 건 것만 보이므로 제목에 차수를 붙인다. (2026-09-19 사장님)
                            if (pickMode) "지울 사진 고르는 중"
                            else (if (jobNthPrefix.isNotEmpty()) "${jobNthPrefix}현장 사진" else "현장 사진") +
                                (if (photoTotal == 0) "" else " ${photoTotal}장 / ${photoMax}"),
                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary,
                            modifier = Modifier.weight(1f)
                        )
                        // 버튼은 **제목 줄에**. 전엔 사진이 늘수록 아래로 도망갔고, 20장이 차면
                        //   [올리기] 타일이 아예 사라져서 왜 못 올리는지도 안 보였다. (2026-09-22 사장님)
                        if (pickMode) {
                            PhotoHeadBtn("취소", filled = false) { exitPickMode() }
                        } else {
                            if (photoTotal > 0) {
                                // 🏷 **「고르기」는 뭘 고르는지를 안 말한다.** (2026-09-30 사장님)
                                //   "뭘 고르라는 거지? 라는 생각이 들더라"
                                //   그렇다고 「지우기」로만 하면 **전부 다 지워질 것처럼** 들린다(사장님 지적).
                                //   「골라 지우기」 — 고른다는 것과 지운다는 것이 둘 다 들어간다.
                                //   ⚠️ 색은 **중립 그대로**. 이 버튼은 아무것도 안 지운다(고르는 화면으로 갈 뿐).
                                //     빨강은 진짜 지우는 버튼에만 — 겁나는 버튼은 안 쓰게 된다.
                                PhotoHeadBtn("골라 지우기", filled = false) { pickMode = true }
                                Spacer(Modifier.width(4.dp))
                            }
                            if (photoTotal < photoMax) {
                                PhotoHeadBtn("올리기", filled = true) { launchPhotoPicker() }
                            } else {
                                Text(
                                    "${photoMax}장 다 찼어요",
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary
                                )
                            }
                        }
                    }
                    // 안내문은 **처음 한 번만** — 파란 이름표는 파란 이름표를 보면 알게 된다.
                    if (photoTotal == 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "현장 사진을 올리면 팀원과 같이 봐요. 팀원이 올린 사진엔 파란 이름표가 붙어요. (한 현장 ${photoMax}장까지)",
                            fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "맨 처음 올린 사진이 그 현장의 「대표」가 돼요.",
                            fontSize = 12.sp, color = TossBlue, fontWeight = FontWeight.Bold, lineHeight = 17.sp
                        )
                    } else if (repPhotoId != null) {
                        // 사진이 있을 땐 **딱지만으로 충분**하다 — 긴 안내를 계속 두면 카드가 시끄러워진다.
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "「대표」 = 맨 처음 올린 사진 · 그 달 마지막 현장이면 인증샷·영상에 이 사진이 들어가요",
                            fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp
                        )
                    }
                    val cells: List<Any> = sitePhotos + teamPhotos
                    // 두 줄(6장)까지만. 더 있으면 마지막 칸이 "+N장" 이 되고, 누르면 다 펴진다.
                    val collapsed = !photosExpanded && cells.size > PHOTO_COLLAPSED
                    val shown = if (collapsed) cells.take(PHOTO_COLLAPSED) else cells
                    val hiddenCount = cells.size - shown.size
                    if (shown.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        shown.chunked(3).forEachIndexed { rowIdx, row ->
                            if (rowIdx > 0) Spacer(Modifier.height(8.dp))
                            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEachIndexed { colIdx, cell ->
                                    val flatIdx = rowIdx * 3 + colIdx
                                    val isLastShown = flatIdx == shown.lastIndex
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.weight(1f).aspectRatio(1f).clip(AppShape.md).background(TossGrayBg)
                                    ) {
                                        val picked: Boolean
                                        when (cell) {
                                            is com.detailline.callfollowcrm.data.local.entity.SitePhotoEntity -> {
                                                picked = cell.id in pickedMine
                                                // 🖼️ 열쇠에 **파일이 바뀐 시각**을 붙인다 — 안 그러면 돌려도
                                                //   **여기 작은 사진만 옛 그림**으로 남는다. (2026-10-02 사장님)
                                                coil.compose.AsyncImage(
                                                    model = com.detailline.callfollowcrm.util.PhotoModel
                                                        .of(LocalContext.current, cell.filePath),
                                                    contentDescription = "현장 사진",
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize().clickable {
                                                        if (pickMode) {
                                                            pickedMine =
                                                                if (picked) pickedMine - cell.id else pickedMine + cell.id
                                                        } else {
                                                            fullscreenPhotoId = cell.id
                                                            fullscreenImageUri =
                                                                android.net.Uri.fromFile(java.io.File(cell.filePath))
                                                        }
                                                    }
                                                )
                                                // 🏅 이게 인증샷에 들어가는 그 사진이다.
                                                if (cell.id == repPhotoId) {
                                                    androidx.compose.foundation.layout.Box(
                                                        Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(5.dp)
                                                            .clip(AppShape.pill).background(TossBlue)
                                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("대표", fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold, color = Color.White)
                                                    }
                                                }
                                            }
                                            is com.detailline.callfollowcrm.ai.SitePhotoServerRepository.RemotePhoto -> {
                                                picked = cell.photoId in pickedTeam
                                                val bmp = cell.bitmap
                                                if (bmp != null) {
                                                    androidx.compose.foundation.Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = "현장 사진 (${cell.uploaderName})",
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize().clickable {
                                                            if (pickMode) {
                                                                pickedTeam =
                                                                    if (picked) pickedTeam - cell.photoId
                                                                    else pickedTeam + cell.photoId
                                                            } else fullscreenBitmap = bmp
                                                        }
                                                    )
                                                }
                                                // 업로더 이름표 — 팀원=파랑, 사장님=회색. 그대로 둔다.
                                                androidx.compose.foundation.layout.Box(
                                                    Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(4.dp)
                                                        .clip(AppShape.sm)
                                                        .background(if (cell.isOwner) Color.Black.copy(alpha = 0.5f) else TossBlue)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        cell.uploaderName, fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold, color = Color.White
                                                    )
                                                }
                                            }
                                            else -> picked = false
                                        }
                                        // 고르는 중일 때만 동그라미. 평소엔 사진 위에 아무것도 안 얹는다.
                                        if (pickMode) {
                                            if (picked) {
                                                androidx.compose.foundation.layout.Box(
                                                    Modifier.fillMaxSize().background(TossBlue.copy(alpha = 0.22f))
                                                )
                                            }
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(5.dp)
                                                    .size(19.dp).clip(CircleShape)
                                                    .background(if (picked) TossBlue else Color.Black.copy(alpha = 0.3f))
                                                    .border(2.dp, Color.White, CircleShape),
                                                contentAlignment = androidx.compose.ui.Alignment.Center
                                            ) {
                                                if (picked) androidx.compose.material3.Icon(
                                                    Icons.Default.Check, null, tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                        // 마지막 칸 "+N장" — 누르면 나머지가 다 펴진다.
                                        if (collapsed && isLastShown && hiddenCount > 0) {
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                                                    .clickable { photosExpanded = true },
                                                contentAlignment = androidx.compose.ui.Alignment.Center
                                            ) {
                                                Text(
                                                    "+${hiddenCount}장", fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold, color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                                // 마지막 줄 빈칸 채우기 (3열 정렬 유지)
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    // 고른 장수 + [지우기] — 여러 장을 한 번에. 전엔 ✕ → 확인 을 장마다 반복해야 했다.
                    if (pickMode) {
                        Spacer(Modifier.height(11.dp))
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                // 「고른 것만 지워요」 — 전체가 날아갈까 걱정 안 하시게. (2026-09-30 사장님)
                                if (pickedCount == 0) "지울 사진을 눌러 고르세요 · 고른 것만 지워요"
                                else "${pickedCount}장 고름 · 고른 것만 지워요",
                                fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                color = if (pickedCount == 0) TossTextTertiary else TossTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            if (pickedCount > 0) {
                                Text(
                                    "지우기",
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    modifier = Modifier.clip(AppShape.sm).background(AppTheme.colors.unpaid)
                                        .clickable { picksToDelete = pickedMine to pickedTeam }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 6.52 지난 시공 이력 — **건 탭(2-0)으로 옮겼다.** (2026-09-17 B안)
            //   전에는 맨 아래에 "지난 시공 N건" 목록이 따로 있었는데,
            //   그러면 '지금 건'과 '지난 건'이 화면의 다른 층에 흩어져 한 고객의 이력이 안 보였다.

            // 6.6 팀원 현장 메모 — 직원이 링크 화면에서 보낸 특이사항(2026-06-06). 있을 때만 카드.
            if (teamNotes.isNotEmpty()) {
                TossCard {
                    Column {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("✏️", fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "팀원 현장 메모 ${teamNotes.size}개",
                                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        teamNotes.forEachIndexed { idx, note ->
                            if (idx > 0) Spacer(Modifier.height(8.dp))
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(AppTheme.colors.cautionBg).padding(12.dp)
                            ) {
                                Column {
                                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text(note.memberName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8780A))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            com.detailline.callfollowcrm.util.DateTimeUtils.formatShort(note.createdAtMs),
                                            fontSize = 11.sp, color = TossTextTertiary
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(note.text, fontSize = 13.5.sp, color = Color(0xFF5A3D00), lineHeight = 19.sp)

                                    // 답글 편집 상태 — 평소엔 닫힘(버튼만), 누르면 입력칸 열림.
                                    var editing by remember(note.eventId) { mutableStateOf(false) }
                                    var reply by remember(note.eventId) { mutableStateOf("") }
                                    val hasReply = !note.replyText.isNullOrBlank()

                                    // 내 답글(있으면) — 팀원 링크 화면에도 같이 보임. 편집 중엔 숨김.
                                    if (hasReply && !editing) {
                                        Spacer(Modifier.height(8.dp))
                                        androidx.compose.foundation.layout.Box(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                                .background(TossBlueSoft).padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Column {
                                                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                    Text("↳ 내 답글", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                                                    Spacer(Modifier.weight(1f))
                                                    Text("수정", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                                                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                                            .clickable { reply = note.replyText.orEmpty(); editing = true }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                Text(note.replyText!!, fontSize = 13.sp, color = TossBlueDark, lineHeight = 18.sp)
                                            }
                                        }
                                    }

                                    // 답글 없고 편집도 아니면 → "답글 달기" 버튼만(접힘).
                                    if (!hasReply && !editing) {
                                        Spacer(Modifier.height(8.dp))
                                        androidx.compose.foundation.layout.Row(
                                            Modifier.clip(RoundedCornerShape(8.dp))
                                                .clickable { reply = ""; editing = true }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.Reply, null,
                                                tint = Color(0xFFB8780A), modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text("답글 달기", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8780A))
                                        }
                                    }

                                    // 편집 중 → 입력칸 + 보내기/취소.
                                    if (editing) {
                                        Spacer(Modifier.height(8.dp))
                                        androidx.compose.foundation.layout.Box(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color.White)
                                                .border(1.dp, Color(0xFFEAD9A8), RoundedCornerShape(9.dp))
                                                .padding(horizontal = 10.dp, vertical = 9.dp)
                                        ) {
                                            androidx.compose.foundation.text.BasicTextField(
                                                value = reply,
                                                onValueChange = { reply = it },
                                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard, fontSize = 13.sp, color = TossTextPrimary),
                                                cursorBrush = androidx.compose.ui.graphics.SolidColor(TossBlue),
                                                modifier = Modifier.fillMaxWidth(),
                                                decorationBox = { inner ->
                                                    if (reply.isEmpty()) Text("팀원에게 답글…", fontSize = 13.sp, color = TossTextTertiary)
                                                    inner()
                                                }
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        androidx.compose.foundation.layout.Row(
                                            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text("취소", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                                    .clickable { editing = false; reply = "" }
                                                    .padding(horizontal = 14.dp, vertical = 9.dp))
                                            Spacer(Modifier.width(4.dp))
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.clip(RoundedCornerShape(9.dp))
                                                    .background(if (reply.isBlank()) Color(0xFFE2E6EC) else TossBlue)
                                                    .clickable(enabled = reply.isNotBlank()) {
                                                        viewModel.replyToTeamNote(note.eventId, reply.trim())
                                                        editing = false
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                                            ) {
                                                Text("보내기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. 하단 — 블로그 후기 lockcard([블로그] 탭) + "지난 문자 보기" 링크(항상 표시). (2026-07-18 탭 재배치)
            val bottomCtx = androidx.compose.ui.platform.LocalContext.current
            if (detailTab == 3) androidx.compose.foundation.layout.Row(
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
                    Text("대화+사진으로 포스팅 글 자동 작성", fontSize = 12.sp, color = TossTextTertiary)
                }
                Text(
                    "비즈니스", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(AppTheme.colors.category).padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // 1건일 때의 **조용한 입구** — 프로토 `.more-job` (artifact/4ZvDfUfxDAQU8uNNvQQ1h1)
            //   "1건일 때 큰 '새 시공' 버튼을 두면 보통 손님 화면이 또 어수선해져요.
            //    그래서 맨 아래 '지난 문자 보기' 위에 작은 글씨로 뒀어요."
            //   프로토 값: 회색(#8B95A1) · 14sp · 밑줄 · 높이 44 · 가운데.
            if (detailTab == 0 && !showJobBar && c.scheduledWorkDate != null) {
                Text(
                    "＋ 시공 하나 더 잡기",
                    color = Color(0xFF8B95A1),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { selectedPastJobId = null; addingNewJob = true; datePickerOpen = true }
                        .padding(vertical = 12.dp)
                )
                Spacer(Modifier.height(6.dp))
            }

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

    if (datePickerOpen && customer != null) {
        // 항공권식 기간 선택 — 시작일 → 끝날 탭하면 기간. 하루면 시작일만. (2026-08-01 사장님)
        // DateRangePicker 는 밀리초를 UTC 로 해석 → 저장된 KST 자정을 그대로 주면 하루 일찍 보임. 로컬 오프셋 더해 보정. (2026-08-01)
        val toUtcMidnight = { ms: Long -> ms + java.util.TimeZone.getDefault().getOffset(ms) }
        // 「＋ 새 시공」으로 연 창은 **빈 달력**으로 시작한다. (2026-09-18 실기에서 발견)
        //   전엔 지금 건 날짜가 미리 찍힌 채로 열려서, 다른 날을 누르면 그게 '끝날'이 되어
        //   기간(10/6~10/20)이 잡혔다. 그대로 저장하면 시작일이 여전히 옛 날짜 → 같은 날 중복 가드에 걸려
        //   "그 날짜엔 이미 시공이 있어요" 만 뜨고 **2차가 아예 안 만들어졌다.**
        val initStart = if (addingNewJob) null else customer?.scheduledWorkDate
        val initDays = if (addingNewJob) 1 else (customer?.scheduledWorkDays ?: 1).coerceAtLeast(1)
        val initEnd = initStart?.takeIf { initDays > 1 }?.let { it + (initDays - 1) * DateTimeUtils.DAY_MS }
        val rangeState = androidx.compose.material3.rememberDateRangePickerState(
            initialSelectedStartDateMillis = initStart?.let(toUtcMidnight),
            initialSelectedEndDateMillis = initEnd?.let(toUtcMidnight)
        )
        Dialog(
            onDismissRequest = { addingNewJob = false; datePickerOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize()
                    // Dialog 는 별도 창이라 navigationBarsPadding 이 0 이다 → 창 바닥이 내비바 밑으로 들어간다.
                    //   실측 2026-09-21: [예약 취소] 글씨 아래 절반이 내비바에 가렸다. (SystemBars.kt 헬퍼로 띄움)
                    .bottomBarClearance(extra = 20.dp)
                    .padding(horizontal = 12.dp, vertical = 20.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    color = Color.White,
                    tonalElevation = 0.dp,   // 6dp 면 흰 배경에 회색 톤이 덧입혀져 창이 칙칙해짐 → 0. (2026-08-02 사장님 '회색')
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxHeight(0.92f)
                ) {
                    Column {
                        androidx.compose.material3.DateRangePicker(
                            state = rangeState,
                            modifier = Modifier.weight(1f),
                            showModeToggle = false,
                            // 기본 containerColor = 테마 surfaceContainerHigh(회색) → 창 전체가 회색으로 뜸. 흰색으로. (2026-08-02 사장님)
                            colors = androidx.compose.material3.DatePickerDefaults.colors(
                                containerColor = Color.White,
                                selectedDayContainerColor = TossBlue,
                                selectedDayContentColor = Color.White,
                                todayDateBorderColor = TossBlue,
                                todayContentColor = TossBlue,
                                dayInSelectionRangeContainerColor = TossBlueSoft,
                                dayInSelectionRangeContentColor = TossTextPrimary
                            ),
                            title = {
                                Text(
                                    // 새 건을 잡는 중이면 그렇다고 말해준다 — 지금 건을 고치는 걸로 오해하지 않게. (2026-09-18)
                                    if (addingNewJob) "새 시공 — 시작일 → 끝날 (하루면 시작일만)"
                                    else "시공 기간 — 시작일 → 끝날 (하루면 시작일만)",
                                    fontSize = 13.sp, color = TossTextSecondary, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 12.dp)
                                )
                            }
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            // 버튼 폭을 고정했더니 [예약 취소] 까지 한 줄에 안 들어가 **잘렸다**(2026-09-21 사장님).
                            //   → 두 버튼은 **반반(weight)**, [예약 취소] 는 **아래 한 줄**로. 큰 글씨에서도 안 잘린다.
                            androidx.compose.foundation.layout.Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                // 앱의 다른 창(금액 입력)과 같은 버튼 옷.
                                com.detailline.callfollowcrm.presentation.component.TossSecondaryButton(
                                    text = "취소",
                                    onClick = { addingNewJob = false; datePickerOpen = false },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(9.dp))
                                com.detailline.callfollowcrm.presentation.component.TossPrimaryButton(
                                    text = "저장",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val start = rangeState.selectedStartDateMillis
                                        if (start != null) {
                                            val end = rangeState.selectedEndDateMillis
                                            val days = if (end != null && end > start)
                                                ((end - start) / DateTimeUtils.DAY_MS).toInt() + 1 else 1
                                            if (addingNewJob) {
                                                // 새 건 — 지금 건은 **손대지 않는다.** 한 줄 더 쌓는다. (2026-09-17)
                                                viewModel.addNewJob(start, days) { id ->
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        if (id > 0L) "새 시공을 추가했어요" else "그 날짜엔 이미 시공이 있어요",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } else {
                                                // 날짜(시작) + 기간(며칠) 저장 → "시공 시간" 선택 → (첫 등록이면) 축하. (2026-06-23 / 2026-08-01)
                                                pendingCelebrate = customer?.scheduledWorkDate == null
                                                pendingWorkDays = days.coerceAtLeast(1)
                                                viewModel.updateScheduledWorkDate(start)
                                                workTimePickerOpen = true
                                            }
                                        }
                                        addingNewJob = false
                                        datePickerOpen = false
                                    }
                                )
                            }
                            // 예약 취소(일정 비우기) — 되돌리는 일이라 **아래 한 줄**, 조용한 빨간 글씨.
                            //   (2026-06-08 #6 · 자리 옮김 2026-09-21)
                            // ⚠️ '새 시공 추가' 중엔 숨긴다 — 그 창에서 누르면 지금 추가하려던 게 아니라
                            //    **원래 잡혀 있던 시공**이 지워진다. (2026-09-21 사장님 신고에서 발견)
                            if (!addingNewJob && customer?.scheduledWorkDate != null) {
                                Spacer(Modifier.height(4.dp))
                                TextButton(
                                    onClick = {
                                        viewModel.updateScheduledWorkDate(null)
                                        datePickerOpen = false
                                        android.widget.Toast.makeText(context, "시공 예약을 취소했어요", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("예약 취소", color = TossError, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // A/S 예약(시공과 별개, 무료) — 항공권식 기간 범위선택. 시간 없음(A/S 는 날짜/기간만). (2026-08-01 사장님)
    if (asPickerOpen && customer != null) {
        val toUtcMidnightAs = { ms: Long -> ms + java.util.TimeZone.getDefault().getOffset(ms) }
        val asStart = customer?.asScheduledDate
        val asDays = (customer?.asScheduledDays ?: 1).coerceAtLeast(1)
        val asEnd = asStart?.takeIf { asDays > 1 }?.let { it + (asDays - 1) * DateTimeUtils.DAY_MS }
        val asRangeState = androidx.compose.material3.rememberDateRangePickerState(
            initialSelectedStartDateMillis = asStart?.let(toUtcMidnightAs),
            initialSelectedEndDateMillis = asEnd?.let(toUtcMidnightAs)
        )
        Dialog(
            onDismissRequest = { asPickerOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize()
                    // Dialog 는 별도 창이라 navigationBarsPadding 이 0 이다 → 창 바닥이 내비바 밑으로 들어간다.
                    //   실측 2026-09-21: [예약 취소] 글씨 아래 절반이 내비바에 가렸다. (SystemBars.kt 헬퍼로 띄움)
                    .bottomBarClearance(extra = 20.dp)
                    .padding(horizontal = 12.dp, vertical = 20.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    color = Color.White, tonalElevation = 0.dp, shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxHeight(0.92f)
                ) {
                    Column {
                        androidx.compose.material3.DateRangePicker(
                            state = asRangeState,
                            modifier = Modifier.weight(1f),
                            showModeToggle = false,
                            // 창 회색 제거 + A/S 주황 강조. (2026-08-02 사장님)
                            colors = androidx.compose.material3.DatePickerDefaults.colors(
                                containerColor = Color.White,
                                selectedDayContainerColor = Color(0xFFF5920B),
                                selectedDayContentColor = Color.White,
                                todayDateBorderColor = Color(0xFFF5920B),
                                todayContentColor = Color(0xFFF5920B),
                                dayInSelectionRangeContainerColor = AppTheme.colors.cautionBg,
                                dayInSelectionRangeContentColor = TossTextPrimary
                            ),
                            title = {
                                Text(
                                    "A/S 예약 — 시작일 → 끝날 (하루면 시작일만) · 무료",
                                    fontSize = 13.sp, color = Color(0xFFF5920B), fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 12.dp)
                                )
                            }
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            if (customer?.asScheduledDate != null) {
                                TextButton(onClick = {
                                    viewModel.updateAsSchedule(null, 1)
                                    asPickerOpen = false
                                    android.widget.Toast.makeText(context, "A/S 예약을 취소했어요", android.widget.Toast.LENGTH_SHORT).show()
                                }) { Text("A/S 취소", color = TossError, fontWeight = FontWeight.SemiBold) }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                // 날짜 창 버튼 옷 통일 — 앱의 다른 창(금액 입력)과 같게. (2026-09-21 사장님)
                                com.detailline.callfollowcrm.presentation.component.TossSecondaryButton(
                                    text = "취소",
                                    onClick = { asPickerOpen = false },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(9.dp))
                                com.detailline.callfollowcrm.presentation.component.TossPrimaryButton(
                                    text = "저장",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val start = asRangeState.selectedStartDateMillis
                                        if (start != null) {
                                            val end = asRangeState.selectedEndDateMillis
                                            val days = if (end != null && end > start)
                                                ((end - start) / DateTimeUtils.DAY_MS).toInt() + 1 else 1
                                            viewModel.updateAsSchedule(start, days.coerceAtLeast(1))
                                        }
                                        asPickerOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 시공일 저장 직후 "시공 시간" 선택. 정하면(또는 닫으면) 첫 등록이면 축하. (2026-06-23 사장님)
    if (workTimePickerOpen && customer != null) {
        com.detailline.callfollowcrm.presentation.component.WorkTimePickerDialog(
            initialMinutes = customer?.scheduledWorkMinutes,
            initialDays = pendingWorkDays,
            onPick = { mins, days ->
                viewModel.updateScheduledWorkTiming(mins, days)
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

    // 풀스크린 이미지 뷰어 — 썸네일 탭 시 표시. 검은 배경 + X 닫기.
    // 📷 내가 올린 현장 사진 — **돌리면 저장된다.** (공용 뷰어, 2026-10-01)
    fullscreenImageUri?.let { uri ->
        com.detailline.callfollowcrm.presentation.component.FullscreenPhotoViewer(
            photos = listOf(
                com.detailline.callfollowcrm.presentation.component.ViewerPhoto
                    .OfFile(uri, fullscreenPhotoId)
            ),
            onDismiss = { fullscreenImageUri = null; fullscreenPhotoId = 0L },
            onSaveRotation = { id, deg -> viewModel.rotatePhoto(id, deg) }
        )
    }

    // 📷 팀원·서버에서 받은 현장 사진 — **돌려 보기만** 된다(내 파일이 아니다).
    //   2026-10-01 전엔 여기선 돌리기가 아예 없었다. 사장님 "사진 회전기능 왜 추가안됐니"
    fullscreenBitmap?.let { bmp ->
        com.detailline.callfollowcrm.presentation.component.FullscreenPhotoViewer(
            photos = listOf(
                com.detailline.callfollowcrm.presentation.component.ViewerPhoto.OfBitmap(bmp)
            ),
            onDismiss = { fullscreenBitmap = null }
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
        val amtJob = amountEditJobId?.let { id -> allJobsForAmt.firstOrNull { it.id == id } }
        AmountInputDialog(
            title = if (field == "total") "총금액" else "계약금",
            initialWon = if (field == "total") (amtJob?.totalAmount ?: customer?.totalAmount ?: 0L)
                         else (amtJob?.depositAmount ?: customer?.depositAmount ?: 0L),
            onSave = { won ->
                val jid = amountEditJobId
                if (field == "total") {
                    val old = customer?.totalAmount ?: 0L
                    if (jid != null) viewModel.setJobTotalAmount(jid, won) else viewModel.setTotalAmount(won)
                    // 시공금액이 "바뀌면"(첫 설정 제외) 이유 입력 띄움. 변경 이력 카드는 거기서 기록. (2026-06-30 사장님)
                    if (jid == null && won != old && old > 0L) amountChangeReason = old to won
                } else {
                    if (jid != null) viewModel.setJobDepositAmount(jid, won) else viewModel.setDepositAmount(won)
                }
                amountEditJobId = null
                amountEditField = null
            },
            onDismiss = { amountEditField = null }
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
            onClose = { reviewQuoteDoc = null }
            // onShared 생략 — 다시 보기는 이력에 이미 있어 재기록 안 함(재발송은 됨).
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
            text = { Text("취소했거나 비어 있는 지난 건이에요. 완전히 지워집니다.", color = TossTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePastJob(j.id)
                    if (selectedPastJobId == j.id) selectedPastJobId = null
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

/**
 * 보낸 문서 한 장. 탭 = 여는 것, 꾹 누름 = 지우기(잠김 아닐 때만).
 *   제목은 시공일이 맨 앞, 그 아래 「내가 보냄 / 손님이 씀」 두 줄. (2026-09-27 사장님)
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun IssuedDocRow(
    doc: com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity,
    /** 링크를 다시 보낼 고객 번호. */
    phone: String,
    onOpen: () -> Unit,
    /** 접수서(intake)이고 **손님이 아직 안 썼을 때만.** null = 고치기 잠김. (2026-09-27 사장님) */
    onEdit: (() -> Unit)? = null,
    /** 손님이 **아직 안 썼을 때만.** null = 지우기 잠김 — 분쟁 때 봐야 하는 증거다. */
    onDelete: (() -> Unit)? = null,
    /** 바꿀 게 있으면 새로 보낸다. 옛것은 그대로 기록으로 남는다. */
    onNewDoc: () -> Unit = {},
    /** 고객이 채워 보낸 것. null = 아직 안 채움. */
    filled: com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity? = null
) {
    val isQuote = doc.kind == "quote"
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 🗓️ **시공일이 맨 앞.** (2026-09-27 사장님)
    //   "1차 시공 2차시공 뭐 이렇게 있다보니까 차라리 시공일을 맨앞에.. 언제짜인지 한눈에 알아볼수있진않을까"
    //   한 손님에게 1차·2차가 있으면 「시공접수서」라는 말만으로는 어느 건인지 알 수 없었다.
    //   ⚠️ 시공일 없는 접수서는 이제 안 만들어진다("시공일이 없을땐 접수서가 만들어지질 않아").
    //      옛 자료엔 있을 수 있어, 그럴 땐 날짜만 생략하고 그대로 보여준다.
    val headDate = doc.workDateMs?.takeIf { it > 0L }?.let { DateTimeUtils.formatKoreanMonthDay(it) }
    fun stamp(ms: Long): String =
        DateTimeUtils.formatKoreanMonthDay(ms) + " " + DateTimeUtils.formatTime(ms)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(AppTheme.colors.bg)
            // 지우기는 꾹 눌러서 — 글 옆에 떠 있던 ✕ 단추가 사장님 눈에 「버튼이 겹쳐있는 디자인」이었다.
            .combinedClickable(onLongClick = onDelete, onClick = onOpen).padding(13.dp)
    ) {
        // 제목 줄 — 무슨 문서인가. 꺾쇠 하나로 **누르면 열린다**를 말한다.
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(if (isQuote) "📜" else "📋", fontSize = 17.sp)
            Spacer(Modifier.width(8.dp))
            if (isQuote) {
                Text("견적서", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                // 견적서는 **금액이 알맹이**다. 접수서는 눌러야 내용이 나온다.
                if (doc.totalWon > 0L) {
                    Text(" · ", fontSize = 14.sp, color = TossTextTertiary)
                    Text(
                        java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA)
                            .format(doc.totalWon) + "원",
                        fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue
                    )
                }
                Spacer(Modifier.weight(1f))
            } else {
                if (headDate != null) {
                    Text(headDate, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    "시공접수서", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    color = TossTextPrimary, modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Text("›", fontSize = 16.sp, color = TossTextTertiary)
        }
        Spacer(Modifier.height(7.dp))

        // 두 줄 — **누가 무엇을 했나.** (2026-09-27 사장님이 고른 말투: "내가 보냄 / 손님이 씀")
        IssuedKv("내가 보냄", stamp(doc.issuedAtMs), TossTextPrimary)
        if (!isQuote) {
            if (filled != null) IssuedKv("손님이 씀", stamp(filled.submittedAtMs), TossSuccess)
            else IssuedKv("손님이 씀", "아직", TossTextTertiary)
        }

        // 손님 메모만 남긴다 — 주소·시공일은 이 화면 위쪽에 이미 있고, 메모는 여기밖에 없다.
        filled?.customerMemo?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(AppTheme.colors.doneBg).padding(horizontal = 11.dp, vertical = 9.dp)
            ) {
                Text("손님 메모", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TossSuccess)
                Spacer(Modifier.height(2.dp))
                Text(it, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, lineHeight = 19.sp)
            }
        }

        // 그때그때 **할 일 하나**. 「다시 보기」는 카드를 누르면 되니 안 만든다.
        //   (사장님 "내용보기랑 다시보기랑 뭐가달라?" — 같은 것이었다)
        if (!isQuote) {
            if (filled != null) {
                Spacer(Modifier.height(11.dp))
                Text(
                    "손님이 확인해서 보낸 것이라 고치거나 지울 수 없어요",
                    fontSize = 11.5.sp, color = TossTextTertiary, lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                IssuedBtn("새 접수서 보내기", primary = false) { onNewDoc() }
            } else {
                Spacer(Modifier.height(11.dp))
                val url = doc.url?.takeIf { it.isNotBlank() }
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (url != null) {
                        Box(Modifier.weight(1f)) {
                            IssuedBtn("링크 다시 보내기", primary = true) {
                                // 🔴 전엔 손으로 Intent 를 만들고 **조용히 삼켰다** — 못 열려도
                                //   사장님은 「다시 보냈다」고 생각하는데 손님은 아무것도 못 받는다.
                                com.detailline.callfollowcrm.util.SmsIntentHelper.openSmsCompose(
                                    ctx, phone, "시공접수서예요. 눌러서 작성해 주세요\n" + url
                                )
                            }
                        }
                    }
                    if (onEdit != null) {
                        Box(Modifier.weight(1f)) {
                            IssuedBtn("고쳐서 보내기", primary = url == null) { onEdit() }
                        }
                    }
                }
            }
        }
    }
    // 지우기를 숨기지 않고 **어떻게 지우는지를 적는다.** 모르게 두면 없는 기능이다.
    if (onDelete != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            "꾹 누르면 지울 수 있어요",
            fontSize = 11.sp, color = TossTextTertiary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
private fun tossFieldColors() = OutlinedTextFieldDefaults.colors(
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


/** 되돌리는 단추 — 작게, 오른쪽 끝에. 빨강은 '취소' 하나에만. (2026-09-20 사장님) */
@Composable
private fun CdUndoChip(label: String, danger: Boolean, onClick: () -> Unit) {
    val fg = if (danger) TossError else AppTheme.colors.textHint
    androidx.compose.foundation.layout.Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .border(1.dp, if (danger) AppTheme.colors.unpaidBg else AppTheme.colors.surfaceMuted, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

/** 원 → "N만원"(만 단위로 떨어지면) / "N원". 규칙은 MoneyFormatter 한 곳에. (2026-09-28) */
private fun manwonLabel(won: Long): String =
    com.detailline.callfollowcrm.util.MoneyFormatter.manwonOrWon(won)

// (payStatusLabel / MoneyEditPill 제거 — 돈도 CdKv 한 줄로 바뀌면서 쓸 곳이 없어졌다. 2026-09-20)

/** 금액(총금액/계약금) 입력 다이얼로그 — 만원 단위. */
@Composable
private fun AmountInputDialog(title: String, initialWon: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (initialWon > 0L) (initialWon / 10000L).toString() else "") }
    // 🔴 적어둔 금액을 **조용히 버리지 않는다.** (2026-09-18 사장님 "금액 수정까지 했었거든? 근데 날아갔네")
    //   숫자를 치면 키보드가 올라오면서 [저장] 버튼이 위로 밀린다. 원래 자리를 누르면 키보드가 눌리고,
    //   바깥을 누르거나 뒤로 가면 **입력한 값이 그냥 사라졌다**(안내도 없음). 돈에서 이러면 안 된다.
    //   → 값을 바꿔놓고 닫으려 하면 한 번 되묻는다.
    val changed = (text.toLongOrNull() ?: 0L) != (if (initialWon > 0L) initialWon / 10000L else 0L)
    var confirmDiscard by remember { mutableStateOf(false) }
    val tryDismiss = { if (changed) confirmDiscard = true else onDismiss() }
    if (confirmDiscard) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = Color.White,
            tonalElevation = 0.dp,
            title = { Text("적어둔 금액을 버릴까요?", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
            text = {
                Text(
                    "${text.ifBlank { "0" }}만원을 적어두셨어요. 저장하지 않고 닫으면 사라져요.",
                    fontSize = 13.5.sp, color = TossTextSecondary, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val enteredMan = text.toLongOrNull() ?: 0L
                    onSave(if (enteredMan == initialWon / 10000L) initialWon else enteredMan * 10000L)
                    confirmDiscard = false
                }) { Text("저장할게요", color = TossBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false; onDismiss() }) {
                    Text("버리기", color = TossError, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = tryDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp)) {
            com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
            Text("$title 입력", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() } },
                suffix = { Text("만원") },
                visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = tossFieldColors()
            )
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TossSecondaryButton(text = "취소", onClick = tryDismiss, modifier = Modifier.weight(1f))
                TossPrimaryButton(text = "저장", onClick = {
                    val enteredMan = text.toLongOrNull() ?: 0L
                    // 표시 만원을 안 바꿨으면 원본 '원' 값 그대로 유지(만원 미만 절삭 방지: 375,000원을 안 건드려도 370,000 되던 것).
                    //   바꿨을 때만 만원→원. (2026-08-11 돈 정확성 감사 rank2)
                    onSave(if (enteredMan == initialWon / 10000L) initialWon else enteredMan * 10000L)
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 시공금액 변경 시 이유 입력(선택) — 적으면 변경 이력 카드에 남고, 안 적어도(그냥 넘어가기) 변경 자체는 기록. (2026-06-30 사장님) */
@Composable
private fun AmountChangeReasonDialog(oldWon: Long, newWon: Long, onRecord: (String) -> Unit, onSkip: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    val arrow = if (oldWon > 0L) "${oldWon / 10000L}만원  →  ${newWon / 10000L}만원"
                else "${newWon / 10000L}만원으로 정함"
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
private fun PhotoHeadBtn(label: String, filled: Boolean, onClick: () -> Unit) {
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
private fun startNavToAddress(context: android.content.Context, address: String) {
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
private fun formatThousands(n: Long): String = "%,d".format(n)

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
            Text("이 고객은 어디에 둘까요?", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("나중에 묶어 보거나 찾을 때 써요", fontSize = 12.sp, color = TossTextTertiary)
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
private fun jobBlank(j: com.detailline.callfollowcrm.data.local.entity.JobEntity): Boolean =
    j.scheduledWorkDate == null && j.workCompletedAt == null &&
        j.totalAmount == null && j.depositAmount == null && j.balanceAmount == null &&
        j.depositPaidAt == null && j.balancePaidAt == null && j.address.isNullOrBlank()

/**
 * **취소한 건 / 빈 건.** (2026-09-18 사장님 "1차 시공이 잡히지도 않았는데 2차 3차 등록도 가능하네")
 *   취소는 기록을 남기려고 날짜만 비운다 → 그 건이 '날짜 미정'으로 탭에 남아 차수를 차지했다.
 *   `cancelledAt` 이 안 찍힌 옛 껍데기(일정만 푼 건)도 같이 접는다 — 사장님 눈엔 똑같은 빈 칸이다.
 *   앞줄에서 빼고 '지난 건'으로 접는다. 지우는 게 아니라 접는 거라 되살릴 수 있다.
 */
private fun jobCancelled(j: com.detailline.callfollowcrm.data.local.entity.JobEntity): Boolean =
    j.cancelledAt != null || jobBlank(j)

/** 앞줄(탭)에서 빼고 접어둘 건 — 마무리됐거나 취소한 것. */
private fun jobFolded(j: com.detailline.callfollowcrm.data.local.entity.JobEntity): Boolean =
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
private fun jobNthOf(
    all: List<com.detailline.callfollowcrm.data.local.entity.JobEntity>,
    job: com.detailline.callfollowcrm.data.local.entity.JobEntity
): Int {
    val order = com.detailline.callfollowcrm.util.JobOrder.order(
        all.filterNot { jobCancelled(it) }.map { it.scheduledWorkDate to it.id }
    )
    return com.detailline.callfollowcrm.util.JobOrder.nth(order, job.id)
}


@Composable
private fun JobTabsRow(
    pastJobs: List<com.detailline.callfollowcrm.data.local.entity.JobEntity>,
    current: com.detailline.callfollowcrm.data.local.entity.CustomerEntity,
    selectedPastJobId: Long?,
    onSelect: (Long?) -> Unit,
    onAddNew: () -> Unit,
    /** 마무리(잔금 받음)된 건 수 — 탭에서 빠져 '지난 건'으로 묶인다. (2026-09-18) */
    closedCount: Int = 0,
    pastOpen: Boolean = false,
    onTogglePast: () -> Unit = {}
) {
    // 차수는 **날짜순**이다 — 먼저 한 날이 1차. (2026-09-18 실기에서 발견해 고침)
    //   전엔 '지난 건들' 을 먼저 그리고 '지금 건' 을 **항상 맨 뒤**에 붙였다.
    //   그런데 '지금 건'(대표) = 오늘 이후 **가장 가까운** 건이라, 2차를 더 뒤 날짜로 잡으면
    //   [1차 11/10][2차 10/27] 처럼 **순서와 차수가 뒤집혀** 보였다.
    //   지금 건도 날짜를 가진 한 칸으로 같이 줄 세운다. 날짜 없는 지금 건은 맨 뒤.
    // 마무리(잔금 받음)된 건은 탭에서 뺀다 — 아래 '지난 건'으로 묶인다. (2026-09-18 프로토)
    //   다만 **지금 고른 건**은 마무리됐어도 남겨야 화면이 비지 않는다.
    // 🔴 **날짜 없는 건은 맨 뒤.** (2026-09-27 사장님 "1차 시공도 없는데 2차 날짜가 잡히는 건 버그인가?")
    //   전엔 지난 건의 빈 날짜를 0L(1970년)로 봐서 **맨 앞 = 1차**를 차지했다.
    //   아직 날짜도 안 잡은 시공이 이미 끝낸 시공보다 먼저일 수는 없다.
    //   지금 건은 원래 맨 뒤(Long.MAX_VALUE)였다 — **지난 건만 반대**여서 어긋났다.
    //   날짜가 없어 동점이면 **먼저 만든 건**이 앞. 지금 건(대표)은 그중 맨 뒤.
    val CUR = com.detailline.callfollowcrm.util.JobOrder.CURRENT
    val slots: List<Pair<Long?, com.detailline.callfollowcrm.data.local.entity.JobEntity?>> =
        remember(pastJobs, current.scheduledWorkDate, selectedPastJobId) {
            val xs = ArrayList<Triple<Long?, Long, com.detailline.callfollowcrm.data.local.entity.JobEntity?>>()
            for (j in pastJobs) {
                if (jobFolded(j) && j.id != selectedPastJobId) continue
                xs.add(Triple(j.scheduledWorkDate, j.id, j))
            }
            xs.add(Triple(current.scheduledWorkDate, CUR, null))
            val seq = com.detailline.callfollowcrm.util.JobOrder.order(xs.map { it.first to it.second })
            xs.sortedBy { seq.indexOf(it.second) }.map { it.first to it.third }
        }
    // 차수는 **숨겨진 지난 건까지 포함한 날짜순**. 접었다 폈다 해도 "2차"가 "1차"로 바뀌지 않는다.
    //   단 **취소한 건은 차수를 차지하지 않는다.** (2026-09-18 실기에서 발견)
    //   1차를 취소했더니 빈 자리가 "2차 · 신규" 라고 떴다 — 한 번도 안 한 시공이 2차일 수는 없다.
    val order: List<Long> = remember(pastJobs, current.scheduledWorkDate) {
        com.detailline.callfollowcrm.util.JobOrder.order(
            pastJobs.filterNot { jobCancelled(it) }.map { it.scheduledWorkDate to it.id } +
                listOf(current.scheduledWorkDate to CUR)
        )
    }
    fun nthOf(j: com.detailline.callfollowcrm.data.local.entity.JobEntity): Int =
        com.detailline.callfollowcrm.util.JobOrder.nth(order, j.id)
    val curNth = com.detailline.callfollowcrm.util.JobOrder.nth(order, CUR)
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        @Suppress("UNUSED_PARAMETER")
        slots.forEachIndexed { idx, (_, job) ->
            if (job != null) {
                // 완료된 건만 '완료'. 앞으로 잡힌 건은 D-day 로 — 예정인데 '완료'라고 쓰면 거짓말이다. (2026-09-17)
                val done = job.workCompletedAt != null
                val dd = job.scheduledWorkDate?.let { DateTimeUtils.dDayLabel(it) }
                JobTab(
                    // 취소한 건은 차수를 안 준다 — 안 한 시공에 번호를 붙이면 뒤가 다 밀린다. (2026-09-18)
                    nth = if (jobCancelled(job)) (if (job.cancelledAt != null) "취소한 건" else "빈 건")
                          else "${nthOf(job)}차 · " + if (done) "완료" else (dd ?: "예정"),
                    sub = job.scheduledWorkDate?.let { DateTimeUtils.formatDateLabel(it) }
                        ?: if (job.cancelledAt != null) "예약 취소함" else "날짜 미정",
                    on = selectedPastJobId == job.id,
                    onClick = { onSelect(job.id) }
                )
            } else {
                // 지금 건 — 예약이 남았으면 D-day, 아니면 '진행 중'.
                val label: String? = com.detailline.callfollowcrm.presentation.component.scheduleTagLabel(current)
                val nowLabel = label?.removePrefix("시공 ")?.takeIf { t -> t.isNotBlank() } ?: "진행"
                JobTab(
                    nth = "${curNth}차 · " + nowLabel,
                    sub = current.scheduledWorkDate?.let { DateTimeUtils.formatDateLabel(it) } ?: "날짜 미정",
                    on = selectedPastJobId == null,
                    onClick = { onSelect(null) }
                )
            }
        }
        JobTab(nth = "＋", sub = "새 시공", on = false, dashed = true, onClick = onAddNew)
        // 마무리된 건은 탭에서 빼고 여기 묶는다. (2026-09-18 확정 프로토 `.chip.past`)
        //   "잔금 받으면 그 건은 마무리. 마무리된 건은 탭에서 빠지고 **지난 건 (N)** 으로 접혀요."
        if (closedCount > 0) {
            JobTab(
                nth = "지난 건", sub = "${closedCount}건",
                on = pastOpen, muted = true, onClick = onTogglePast
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}


