package com.detailline.callfollowcrm.presentation.screen.customer

import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Add
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
    // 현장 사진 삭제 확인 — null 이면 닫힘, 값 = 삭제 대상 photo id.
    var photoToDelete by remember { mutableStateOf<Long?>(null) }
    // 팀원(서버) 사진 삭제 확인 — 사장님이 퇴사한 팀원 사진도 지울 수 있게. (2026-06-07)
    var teamPhotoToDelete by remember { mutableStateOf<Long?>(null) }
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
                    "고객 정보를 불러오는 중...",
                    modifier = Modifier.padding(24.dp),
                    color = TossTextTertiary
                )
            }
            return@Scaffold
        }

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
                .imePadding()
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
                        Text(if (hasName) "변경" else "이름 넣기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary)
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

            // 👤 이 손님 메모 — **현장이 바뀌어도 그대로인 것.** (2026-09-18 확정 프로토)
            //   "성향, 계좌, 통화 편한 시간". 현장별 메모(📍)는 건 안쪽에 따로 있다.
            val custMemoFocus = remember { FocusRequester() }
            TossCard {
                Column {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { runCatching { custMemoFocus.requestFocus() } }
                    ) {
                        CdTitleIcon(Icons.Filled.Person, "gray")
                        Spacer(Modifier.width(8.dp))
                        Text("이 손님 메모", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary)
                        Spacer(Modifier.weight(1f))
                        val savedMemo = c.memo.orEmpty()
                        val (st, stColor) = when {
                            shouldSaveMemo(memoDirty, memoInput, savedMemo) -> "저장 중…" to TossTextTertiary
                            memoInput.isNotBlank() -> "저장됨" to TossSuccess
                            else -> "자동 저장" to TossTextTertiary
                        }
                        Text(st, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = stColor)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("현장이 바뀌어도 그대로인 것 · 성향, 계좌, 통화 편한 시간",
                        fontSize = 11.5.sp, color = TossTextTertiary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = memoInput,
                        onValueChange = { memoInput = it; memoDirty = true },
                        placeholder = { Text("예) 계좌이체 선호, 오후 3시 이후 통화", color = TossTextTertiary) },
                        modifier = Modifier.fillMaxWidth().height(84.dp).focusRequester(custMemoFocus),
                        colors = tossFieldColors()
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
                        Text("메시지에서 자동 인식 · 눌러서 확정/수정", fontSize = 11.sp, color = TossTextTertiary)
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
                            Text("📍", fontSize = 18.sp)
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
                            Text("눌러서 직접 등록하거나, 고객 메시지에 주소가 있으면 자동 채워져요.",
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
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    detailTabs.forEachIndexed { i, label ->
                        val on = detailTab == i
                        androidx.compose.foundation.layout.Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                                .background(if (on) TossBlue else Color.Transparent)
                                .clickable { detailTab = i }
                                .padding(vertical = 10.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                color = if (on) Color.White else TossTextSecondary, maxLines = 1)
                        }
                    }
                }
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
                        }.distinctBy { it.first.filter { ch -> ch.isDigit() }.takeLast(8) }
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
                            customerId = c.id, onShared = { collabRefresh++ }, onDismiss = { showCollabShare.value = false }
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
                                    // 협업 해제(수락된 것도) — 서버 end → B 에게 알림 + 기록 보존 + 재요청 풀림. best-effort(서버 오면 동작).
                                    if (shareId.isNotBlank()) {
                                        val owner = container.preferences.bizPhone
                                        scope.launch { runCatching { container.sharedSiteRepository.endCollab(shareId, owner, asOwner = true) } }
                                    }
                                    container.preferences.collabAssignments = container.preferences.collabAssignments
                                        .filterNot { e ->
                                            val p = e.split("|")
                                            p.size >= 3 && p[0].toLongOrNull() == c.id &&
                                                p[1].filter { it.isDigit() }.takeLast(8) == partnerPhone.filter { it.isDigit() }.takeLast(8)
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
            val shownJobId = selectedPastJobId ?: repJobId
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
                        Text("📩 방금 문자에서 이런 주소를 봤어요",
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
                                    Text("›", fontSize = 18.sp, color = TossTextTertiary)
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
                val editJobId: Long? = selectedPastJob?.id
                val cShown = selectedPastJob?.let { j ->
                    c.copy(
                        scheduledWorkDate = j.scheduledWorkDate,
                        scheduledWorkMinutes = j.scheduledWorkMinutes,
                        scheduledWorkDays = j.scheduledWorkDays.coerceAtLeast(1),
                        totalAmount = j.totalAmount,
                        depositAmount = j.depositAmount,
                        depositPaidAt = j.depositPaidAt,
                        balanceAmount = j.balanceAmount,
                        balancePaidAt = j.balancePaidAt,
                        workCompletedAt = j.workCompletedAt
                    )
                } ?: c
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
                        if (scheduled != null || hasAmount) {
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
                            // A/S 예약 — 시공과 별개, 무료. (2026-08-01 사장님)
                            CdKv(
                                "A/S 예약",
                                if (c.asScheduledDate != null)
                                    DateTimeUtils.formatShortKoreanDate(c.asScheduledDate!!) + (if (c.asScheduledDays > 1) " · ${c.asScheduledDays}일" else "") + " · 무료"
                                else "아직 없음 · 눌러서 잡기",
                                valueColor = if (c.asScheduledDate != null) AppTheme.colors.text else TossTextTertiary,
                                trailing = if (c.asScheduledDate != null) Icons.Default.Edit else Icons.Default.Add,
                                onClick = { asPickerOpen = true }
                            )
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
                                TossSecondaryButton(text = "총금액 입력", onClick = { amountEditField = "total" })
                                Spacer(Modifier.height(8.dp))
                                TossSecondaryButton(text = "계약금 입력", onClick = { amountEditField = "deposit" })
                            }
                            // 되돌리는 것 둘을 **맨 아래 한 줄**로. (2026-09-20 사장님)
                            //   전엔 [완납 취소] 가 가로로 꽉 찬 큰 버튼이라 이 카드에서 제일 누르기 쉬웠다.
                            //   예약 취소는 카드에 보여야 한다(전엔 날짜 팝업 안에 숨어 못 찾음 — 2026-08-28).
                            if (allPaid || scheduled != null) {
                                Spacer(Modifier.height(10.dp))
                                androidx.compose.foundation.layout.Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    if (allPaid) {
                                        CdUndoChip("완납 취소", danger = false) {
                                            if (editJobId != null) viewModel.setJobBalancePaid(editJobId, false)
                                            else viewModel.setBalancePaid(false)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    if (scheduled != null) {
                                        CdUndoChip("시공 예약 취소", danger = true) { cancelBookingConfirm = true }
                                    }
                                }
                            }
                        } else {
                            // 2026-06-07 사장님 통점: 통화로 다 정해졌는데 일정 등록하려면 견적서 보내기밖에 없었음(고객이 또 입력).
                            //   → 이 자리에서 바로 "시공일 등록 / 총금액 입력". 고객 재입력 불필요. (견적서 경로도 보조로 유지)
                            Text(
                                "통화로 정해졌으면 여기서 바로 등록하세요. 고객에게 또 입력시키지 않아도 돼요.",
                                fontSize = 13.5.sp, color = TossTextSecondary, lineHeight = 21.sp
                            )
                            Spacer(Modifier.height(13.dp))
                            TossPrimaryButton(text = "시공일 등록", onClick = { datePickerOpen = true })
                            Spacer(Modifier.height(8.dp))
                            TossSecondaryButton(text = "💰 총금액 입력", onClick = { amountEditField = "total" })
                            Spacer(Modifier.height(8.dp))
                            TossSecondaryButton(text = "💵 계약금 입력", onClick = { amountEditField = "deposit" })
                            // "견적서로 보내기" 제거 (2026-06-10 사장님: 용도 불명확). 견적서는 채팅 [견적 작성] 으로.
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
            if (detailTab == 2 && issuedDocs.isNotEmpty()) {
                TossCard {
                    Column {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CdTitleIcon(Icons.Filled.ReceiptLong, "blue")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "발행 이력 ${issuedDocs.size}건",
                                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "보낸 견적서·시공접수서예요. 누르면 다시 볼 수 있어요.",
                            fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp
                        )
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
                                onEdit = if (doc.kind == "intake") ({ onOpenChatEditIssued(c.phoneNumber, c.id, doc.id) }) else null,
                                onDelete = { issuedDocToDelete = doc }
                            )
                        }
                    }
                }
            }

            // [시공접수서] 탭인데 발행 이력이 없을 때 안내. (2026-07-18 탭 재배치)
            if (detailTab == 2 && issuedDocs.isEmpty()) {
                DetailTabEmpty("아직 발행한 견적서·시공접수서가 없어요.\n채팅에서 견적서·시공접수서를 보내면 여기에 쌓여요.")
            }

            // 📍 이 현장 메모 — **그 건에서만.** 손님 메모(👤)와 분리. (2026-09-18 확정 프로토)
            //   "메모는 두 곳. 👤 이 손님 메모(현장이 바뀌어도 그대로인 것)와
            //    📍 이 현장 메모(그 건에서만). 이름 앞에 사람/장소 표시를 붙여 헷갈리지 않게."
            //   ⚠️ remember(key) 가 바뀔 때 빈 값이 저장되는 사고를 막으려고
            //     손님 메모와 **같은 dirty 가드**(shouldSaveMemo)를 쓴다.
            val memoFocus = remember { FocusRequester() }
            val jobMemoSaved = shownJob?.memo.orEmpty()
            var jobMemoInput by remember(shownJobId) { mutableStateOf(jobMemoSaved) }
            var jobMemoDirty by remember(shownJobId) { mutableStateOf(false) }
            LaunchedEffect(jobMemoInput, shownJobId) {
                val jid = shownJobId ?: return@LaunchedEffect
                if (!shouldSaveMemo(jobMemoDirty, jobMemoInput, jobMemoSaved)) return@LaunchedEffect
                kotlinx.coroutines.delay(600)
                viewModel.updateJobMemo(jid, jobMemoInput)
            }
            if (shownJobId != null) {
                TossCard {
                    Column {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { runCatching { memoFocus.requestFocus() } }
                        ) {
                            CdTitleIcon(Icons.Filled.EditNote, "gray")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (jobNthPrefix.isNotEmpty()) "${jobNthPrefix}현장 메모" else "이 현장 메모",
                                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary
                            )
                            Spacer(Modifier.weight(1f))
                            val (memoStatus, memoStatusColor) = when {
                                shouldSaveMemo(jobMemoDirty, jobMemoInput, jobMemoSaved) -> "저장 중…" to TossTextTertiary
                                jobMemoInput.isNotBlank() -> "저장됨" to TossSuccess
                                else -> "자동 저장" to TossTextTertiary
                            }
                            Text(memoStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = memoStatusColor)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("이 현장에서만 · 주차, 열쇠, 자재", fontSize = 11.5.sp, color = TossTextTertiary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = jobMemoInput,
                            onValueChange = { jobMemoInput = it; jobMemoDirty = true },
                            placeholder = { Text("이 현장에서 기억할 것", color = TossTextTertiary) },
                            modifier = Modifier.fillMaxWidth().height(120.dp).focusRequester(memoFocus),
                            colors = tossFieldColors()
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
            LaunchedEffect(showPhotoPicker) {
                if (showPhotoPicker) {
                    showPhotoPicker = false
                    photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            TossCard {
                Column {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CdTitleIcon(Icons.Filled.PhotoCamera, "blue")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            // 사진도 그 건 것만 보이므로 제목에 차수를 붙인다. (2026-09-19 사장님)
                            (if (jobNthPrefix.isNotEmpty()) "${jobNthPrefix}현장 사진" else "현장 사진") +
                                (if (photoTotal == 0) "" else " ${photoTotal}장 / ${photoMax}"),
                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "현장 사진을 올리면 팀원과 같이 봐요. 팀원이 올린 사진엔 파란 이름표가 붙어요. (한 현장 ${photoMax}장까지)",
                        fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    // photo-grid (3열) — 내 사진 + 팀 사진 + 맨 끝 [올리기] 타일(20장 미만일 때만).
                    val cells: List<Any> =
                        sitePhotos + teamPhotos + (if (photoTotal < photoMax) listOf("UPLOAD") else emptyList())
                    cells.chunked(3).forEachIndexed { rowIdx, row ->
                        if (rowIdx > 0) Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { cell ->
                                when (cell) {
                                    is com.detailline.callfollowcrm.data.local.entity.SitePhotoEntity -> {
                                        androidx.compose.foundation.layout.Box(
                                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                                        ) {
                                            coil.compose.AsyncImage(
                                                model = java.io.File(cell.filePath),
                                                contentDescription = "현장 사진",
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize().clickable {
                                                    fullscreenImageUri = android.net.Uri.fromFile(java.io.File(cell.filePath))
                                                }
                                            )
                                            // 삭제 ✕ 배지 (우상단) — 내가 올린 사진만 삭제 가능.
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(4.dp)
                                                    .size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                                                    .clickable { photoToDelete = cell.id },
                                                contentAlignment = androidx.compose.ui.Alignment.Center
                                            ) {
                                                androidx.compose.material3.Icon(
                                                    Icons.Default.Close, "삭제", tint = Color.White, modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                    is com.detailline.callfollowcrm.ai.SitePhotoServerRepository.RemotePhoto -> {
                                        androidx.compose.foundation.layout.Box(
                                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                                        ) {
                                            val bmp = cell.bitmap
                                            if (bmp != null) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = "현장 사진 (${cell.uploaderName})",
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize().clickable { fullscreenBitmap = bmp }
                                                )
                                            }
                                            // 업로더 이름표 — 팀원=파랑, 사장님=회색 (프로토: 팀원 사진 파란 이름표).
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(4.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (cell.isOwner) Color.Black.copy(alpha = 0.5f) else TossBlue)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(cell.uploaderName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                            // 삭제 ✕ 배지 — 사장님은 팀원(퇴사 포함) 사진도 삭제 가능. (2026-06-07)
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(4.dp)
                                                    .size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                                                    .clickable { teamPhotoToDelete = cell.photoId },
                                                contentAlignment = androidx.compose.ui.Alignment.Center
                                            ) {
                                                androidx.compose.material3.Icon(
                                                    Icons.Default.Close, "삭제", tint = Color.White, modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        // [올리기] 타일
                                        androidx.compose.foundation.layout.Box(
                                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                                                .background(TossGrayBg)
                                                .border(1.5.dp, Color(0xFFC8D3E2), RoundedCornerShape(12.dp))
                                                .clickable { launchPhotoPicker() },
                                            contentAlignment = androidx.compose.ui.Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                                androidx.compose.material3.Icon(
                                                    Icons.Default.PhotoCamera, null, tint = TossBlue, modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text("올리기", fontSize = 11.sp, color = TossBlue, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            // 마지막 줄 빈칸 채우기 (3열 정렬 유지)
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
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
                        android.widget.Toast.makeText(bottomCtx, "블로그 후기 글 만들기는 비즈니스 요금제 기능이에요. 곧 제공돼요!", android.widget.Toast.LENGTH_SHORT).show()
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
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 20.dp),
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
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            // 예약 취소(일정 비우기) — 기존 예약 있을 때만 노출. 고객이 시공 취소 시. (2026-06-08 #6)
                            if (customer?.scheduledWorkDate != null) {
                                TextButton(onClick = {
                                    viewModel.updateScheduledWorkDate(null)
                                    datePickerOpen = false
                                    android.widget.Toast.makeText(context, "시공 예약을 취소했어요", android.widget.Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("예약 취소", color = TossError, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                // 앱의 다른 창(금액 입력)과 같은 버튼 옷. 전엔 여기만 그냥 글씨였다. (2026-09-21 사장님)
                                com.detailline.callfollowcrm.presentation.component.TossSecondaryButton(
                                    text = "취소",
                                    onClick = { addingNewJob = false; datePickerOpen = false },
                                    modifier = Modifier.width(92.dp)
                                )
                                Spacer(Modifier.width(9.dp))
                                com.detailline.callfollowcrm.presentation.component.TossPrimaryButton(
                                    text = "저장",
                                    modifier = Modifier.width(112.dp),
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
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 20.dp),
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
                                    "🔧 A/S 예약 — 시작일 → 끝날 (하루면 시작일만) · 무료",
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
                                    modifier = Modifier.width(92.dp)
                                )
                                Spacer(Modifier.width(9.dp))
                                com.detailline.callfollowcrm.presentation.component.TossPrimaryButton(
                                    text = "저장",
                                    modifier = Modifier.width(112.dp),
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
            title = "예약 확정!",
            subtitle = "축하해요 🎉",
            onFinished = { celebrationVisible = false }
        )
    }

    // 풀스크린 이미지 뷰어 — 썸네일 탭 시 표시. 검은 배경 + X 닫기.
    fullscreenImageUri?.let { uri ->
        Dialog(
            onDismissRequest = { fullscreenImageUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenImageUri = null },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                coil.compose.AsyncImage(
                    model = uri,
                    contentDescription = "사진",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                IconButton(
                    onClick = { fullscreenImageUri = null },
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = Color.White
                    )
                }
            }
        }
    }

    // 팀/서버 현장사진(비트맵) 풀스크린 뷰어.
    fullscreenBitmap?.let { bmp ->
        Dialog(
            onDismissRequest = { fullscreenBitmap = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenBitmap = null },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "현장 사진",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                IconButton(
                    onClick = { fullscreenBitmap = null },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
                }
            }
        }
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
            containerColor = Color.White
        )
    }

    // 일정·정산 금액 편집 (총금액/계약금) — 만원 입력.
    amountEditField?.let { field ->
        AmountInputDialog(
            title = if (field == "total") "총금액" else "계약금",
            initialWon = if (field == "total") (customer?.totalAmount ?: 0L) else (customer?.depositAmount ?: 0L),
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

    // 현장 사진 삭제 확인
    photoToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("사진을 삭제할까요?", fontWeight = FontWeight.Bold) },
            text = { Text("이 현장 사진을 지웁니다. 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSitePhoto(id); photoToDelete = null }) {
                    Text("삭제", color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) { Text("취소", color = TossTextSecondary) }
            }
        )
    }

    // 팀원 사진 삭제 확인 (서버) — 사장님이 퇴사한 팀원 사진도 정리.
    teamPhotoToDelete?.let { pid ->
        AlertDialog(
            onDismissRequest = { teamPhotoToDelete = null },
            title = { Text("이 사진을 삭제할까요?", fontWeight = FontWeight.Bold) },
            text = { Text("팀원이 올린 현장 사진을 지웁니다. 팀원 화면에서도 사라지고 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTeamPhoto(pid); teamPhotoToDelete = null }) {
                    Text("삭제", color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { teamPhotoToDelete = null }) { Text("취소", color = TossTextSecondary) }
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
                com.detailline.callfollowcrm.presentation.screen.web.DocWebViewActivity.open(context, url, "시공접수서")
                intakeReviewDoc = null
            },
            onDismiss = { intakeReviewDoc = null }
        )
    }

    // 발행 이력 1건 삭제 확인.
    issuedDocToDelete?.let { doc ->
        AlertDialog(
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

/** 발행 이력 한 줄 — 견적서/시공접수서. 탭=다시 보기, 우측 ✕=이력에서 삭제. (2026-07-07 사장님) */
@Composable
private fun IssuedDocRow(
    doc: com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity,
    onOpen: () -> Unit,
    /** 접수서(intake)만 — "수정" 탭 시 채팅으로 이동해 편집기 재오픈. null=수정 버튼 숨김. (2026-07-10 사장님) */
    onEdit: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val isQuote = doc.kind == "quote"
    val icon = if (isQuote) "📜" else "📋"
    val kindLabel = if (isQuote) "견적서" else "시공접수서"
    val dateStr = remember(doc.issuedAtMs) {
        java.text.SimpleDateFormat("M월 d일 HH:mm", java.util.Locale.KOREA).format(java.util.Date(doc.issuedAtMs))
    }
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.bg)
            .clickable { onOpen() }.padding(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(kindLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                Spacer(Modifier.width(6.dp))
                Text(dateStr, fontSize = 11.sp, color = TossTextTertiary)
            }
            val summary = buildString {
                doc.itemsText?.takeIf { it.isNotBlank() }?.let { append(it) }
                if (doc.totalWon > 0L) {
                    if (isNotEmpty()) append(" · ")
                    append("${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(doc.totalWon)}원")
                }
            }
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(summary, fontSize = 12.sp, color = TossTextSecondary, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            doc.memo?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(1.dp))
                Text("· $it", fontSize = 11.5.sp, color = TossTextTertiary, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        // 이미 보낸 접수서 수정하기 — intake 만. 행 clickable(다시 보기) 에 안 먹히게 별도 clickable. (2026-07-10 사장님)
        if (onEdit != null) {
            Text(
                "수정", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onEdit() }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text("다시 보기", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossBlue)
        androidx.compose.foundation.layout.Box(
            Modifier.padding(start = 4.dp).size(26.dp).clip(CircleShape).clickable { onDelete() },
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(Icons.Default.Close, "삭제", tint = TossTextTertiary, modifier = Modifier.size(14.dp))
        }
    }
}

/** 시공접수서 발행 이력 다시 보기 — 보낸 내용 요약(확인 개념) + 링크 열기/복사. 고객이 채운 주소·상세는 링크(서버)에서. */
@Composable
private fun IntakeReviewDialog(
    doc: com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity,
    onCopyLink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val url = doc.url?.takeIf { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("시공접수서", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                val dateStr = doc.workDateMs
                    ?.let { java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREA).format(java.util.Date(it)) }
                    ?: "협의 후 확정"
                Text("· 시공 예정일 : $dateStr", fontSize = 13.sp, color = TossTextSecondary, lineHeight = 22.sp)
                doc.itemsText?.takeIf { it.isNotBlank() }?.let {
                    Text("· 견적 내용 : $it", fontSize = 13.sp, color = TossTextSecondary, lineHeight = 22.sp)
                }
                if (doc.totalWon > 0L) {
                    Text("· 금액 : ${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(doc.totalWon)}원",
                        fontSize = 13.sp, color = TossTextSecondary, lineHeight = 22.sp)
                }
                doc.memo?.takeIf { it.isNotBlank() }?.let {
                    Text("· 특이사항 : $it", fontSize = 13.sp, color = TossTextSecondary, lineHeight = 22.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("고객이 작성한 주소·상세는 아래 링크에서 확인돼요.", fontSize = 11.5.sp, color = TossTextTertiary, lineHeight = 16.sp)
            }
        },
        confirmButton = {
            if (url != null) TextButton(onClick = { onOpenLink(url) }) {
                Text("링크 열기", color = TossBlue, fontWeight = FontWeight.Bold)
            } else TextButton(onClick = onDismiss) { Text("닫기", color = TossTextSecondary) }
        },
        dismissButton = {
            if (url != null) TextButton(onClick = { onCopyLink(url) }) {
                Text("링크 복사", color = TossTextSecondary)
            }
        }
    )
}

/**
 * ModalBottomSheet 의 Dialog window 가 softInputMode 가 ADJUST_RESIZE 아닐 때 강제 설정.
 * 이게 없으면 dialog window 가 IME 응답 안 함 → WindowInsets.ime 도 0, visibleDisplayFrame 차이도 0.
 * Compose BOM 2024.06.00 + Samsung/Android 10 조합에서 ModalBottomSheet 가 이 상태로 시작하는 케이스 대응.
 */
@Composable
private fun ForceDialogAdjustResize() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.findDialogWindow()
        val oldSoftInputMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        )
        onDispose {
            if (oldSoftInputMode != null) {
                window.setSoftInputMode(oldSoftInputMode)
            }
        }
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

/**
 * 키보드 높이 측정 fallback.
 * sheet Dialog window 안에서 `WindowInsets.ime` 가 0 반환하는 케이스 대비.
 * `View.getWindowVisibleDisplayFrame()` 으로 root view 높이와 visible 높이 차이를 keyboard 높이로 추정.
 * 차이가 root 의 15% 이하면 키보드 없는 것으로 간주.
 */
@Composable
private fun rememberKeyboardHeightDp(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var keyboardHeightPx by remember { mutableIntStateOf(0) }

    DisposableEffect(view) {
        val rect = android.graphics.Rect()
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            view.rootView.getWindowVisibleDisplayFrame(rect)
            val rootHeight = view.rootView.height
            val visibleHeight = rect.height()
            val diff = rootHeight - visibleHeight
            keyboardHeightPx = if (diff > rootHeight * 0.15f) diff else 0
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    return with(density) { keyboardHeightPx.toDp() }
}

/**
 * Compose WindowInsets.navigationBars 가 0 을 반환하는 케이스를 대비한 fallback.
 * Android 내장 dimen `navigation_bar_height` 를 직접 읽어 px → dp 변환.
 * Samsung/Android 10 의 ModalBottomSheet Dialog window 에서 inset 이 0 으로 들어오는 환경 대응.
 */
@Composable
private fun navigationBarFallbackPadding(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = remember(context) {
        val resId = context.resources.getIdentifier(
            "navigation_bar_height",
            "dimen",
            "android"
        )
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }
    return with(density) { px.toDp() }
}

// ChatBottomSheet + ChatBubble 은 메인 ChatScreen 으로 대체되어 제거됨 (2026-05-19).
// 같은 SMS/MMS 표시 + composer 기능이 대시보드 진입 시 메인 뷰로 옮겨졌다.
// 이 화면(CustomerDetail)의 문자 섹션은 이제 정보 표시 전용 (MessageRowView).

/** 작은 썸네일 가로 나열. 한 줄에 최대 3장, 더 많으면 줄바꿈. 탭 = onImageTap. */
@Composable
private fun ImageThumbnailRow(uris: List<android.net.Uri>, onTap: (android.net.Uri) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        uris.chunked(3).forEach { rowUris ->
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowUris.forEach { uri ->
                    coil.compose.AsyncImage(
                        model = uri,
                        contentDescription = "첨부 사진",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(TossGrayBg)
                            .clickable { onTap(uri) }
                    )
                }
            }
        }
    }
}

/**
 * 2026-05-29 킬러콘텐츠 5단계 — 고객 페르소나 카드.
 *
 * cowork 의 prepare-reply 가 Haiku 4.5 로 자동 생성/24h 캐시. 안드는 GET 으로 표시만.
 * null 또는 isEmpty 면 호출처에서 숨김.
 *
 * 사장님 가치: 고객 다시 안 만나도 "어떤 사람" 한눈에 — 답변/응대 톤 맞추기 용이.
 */
/** 상단 탭에 내용이 없을 때 보여줄 담백한 안내 카드. (2026-07-18 탭 재배치) */
@Composable
private fun DetailTabEmpty(text: String) {
    TossCard {
        Text(
            text, fontSize = 13.sp, color = TossTextTertiary, lineHeight = 20.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

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

@Composable
private fun PersonaLine(emoji: String, text: String?) {
    if (text.isNullOrBlank()) return
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top
    ) {
        Text(
            emoji,
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 8.dp, top = 1.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = TossTextPrimary
        )
    }
}

@Composable
private fun SummaryItem(summary: com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity) {
    val title = summary.title
    val summaryBody = summary.summaryText
    val transcript = summary.transcriptText
    Column(Modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = TossTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
        }
        val metaParts = buildList {
            summary.recordedAt?.let { add(DateTimeUtils.formatShort(it)) }
            summary.phoneNumber?.let { add(it) }
            add(summaryBadge(summary.sourceType))
        }
        if (metaParts.isNotEmpty()) {
            Text(
                metaParts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = TossTextTertiary
            )
            Spacer(Modifier.height(6.dp))
        }
        if (!summaryBody.isNullOrBlank()) {
            Text(
                summaryBody,
                style = MaterialTheme.typography.bodyMedium,
                color = TossTextSecondary
            )
        }
        if (!transcript.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "녹음 내용",
                style = MaterialTheme.typography.labelMedium,
                color = TossTextTertiary
            )
            Text(
                transcript,
                style = MaterialTheme.typography.bodySmall,
                color = TossTextSecondary,
                maxLines = 12
            )
        }
    }
}

@Composable
private fun ScheduleRow(
    label: String,
    dateLabel: String,
    ddayLabel: String,
    emphasize: Boolean = false
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = TossTextTertiary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (emphasize) TossTextPrimary else TossTextSecondary,
                fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Text(
            ddayLabel,
            style = MaterialTheme.typography.titleSmall,
            color = if (emphasize) TossBlue else TossTextSecondary,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun summaryBadge(raw: String): String = when (raw) {
    "ADOT_SHARE" -> "통화 녹음"
    "MANUAL_PASTE" -> "직접 붙여넣음"
    "AI_SERVER" -> "AI 서버"
    else -> raw
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

/**
 * 기본 정보 카드 안의 이름 표시/편집 행.
 *  - 이름 있으면: 이름 텍스트 + 작은 ✏ 버튼 (탭 → 편집 다이얼로그)
 *  - 이름 없으면: "+ 이름 추가" 회색 텍스트 링크
 * 전엔 OutlinedTextField 가 항상 큰 면적을 차지했음. 이제 평소엔 한 줄만.
 */
@Composable
private fun NameRow(currentName: String, onEdit: () -> Unit) {
    if (currentName.isBlank()) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .clickable { onEdit() }
                .padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            Text(
                "+ 이름 추가",
                style = MaterialTheme.typography.bodyMedium,
                color = TossTextTertiary,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        // 이름 + ✏ 를 가까이 묶고, 우측은 빈 공간으로 채워서 클릭 영역은 row 전체.
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .clickable { onEdit() }
                .padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            Text(
                currentName,
                style = MaterialTheme.typography.titleMedium,
                color = TossTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "✏",
                style = MaterialTheme.typography.bodyMedium,
                color = TossTextTertiary
            )
        }
    }
}

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
        containerColor = Color.White
    )
}

/**
 * 전화번호 옆의 작은 원형 전화 아이콘 버튼. 탭하면 시스템 다이얼러를 열어
 * 번호가 자동 입력된 상태로 사용자가 직접 발신 버튼을 누름 (자동 발신 X, 권한 X).
 */
@Composable
private fun CallIconButton(phoneNumber: String) {
    val context = LocalContext.current
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(com.detailline.callfollowcrm.presentation.theme.TossBlueSoft)
            .clickable { dialPhone(context, phoneNumber) },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            Icons.Default.Phone,
            contentDescription = "전화 걸기",
            tint = TossBlue,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 프로토 .hd heat 점 색 — hot=빨강/warm=앰버/cold=회색/그 외(미분류=신규)=파랑. */
private fun heatDotColor(heat: String?): Color = when (heat?.uppercase()) {
    "HOT" -> LightColors.unpaid
    "WARM" -> LightColors.caution
    "COLD" -> Color(0xFFC2C9D2)
    else -> LightColors.primary
}

/**
 * 카드 제목 왼쪽 아이콘 칩. (2026-09-20 사장님)
 *   전엔 👤 📍 💰 📷 이모지였다. 이모지는 **폰마다 그림이 다르고**, 특히 📍 는 새빨간 압정이라
 *   담긴 내용("주소 없음")보다 이모지가 더 튀었다. 앱이 직접 그리면 어느 폰에서나 같다.
 */
@Composable
private fun CdTitleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, palette: String) {
    val (bg, fg) = when (palette) {
        "blue" -> AppTheme.colors.primaryBg to AppTheme.colors.primaryText
        "amber" -> AppTheme.colors.cautionBg to AppTheme.colors.cautionText
        "green" -> AppTheme.colors.doneBg to AppTheme.colors.doneText
        else -> AppTheme.colors.surfaceMuted to AppTheme.colors.textSub
    }
    androidx.compose.foundation.layout.Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(bg),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
    }
}

/**
 * 한 줄 = 이름(왼쪽) · 값(오른쪽) · 고치는 단추(같은 크기). (2026-09-20 사장님)
 *   예약·돈이 **같은 줄 모양**이라 눈이 오른쪽 끝을 따라 내려간다.
 */
@Composable
private fun CdKv(
    label: String,
    value: String,
    valueColor: Color,
    trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Column {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(vertical = 9.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = TossTextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = valueColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            if (trailing != null) {
                Spacer(Modifier.width(9.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.surfaceMuted),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        trailing, null, tint = AppTheme.colors.textSub, modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        if (divider) androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.line)
        )
    }
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

/** 원 → "N만원"(만 단위로 떨어지면) / "N원". */
private fun manwonLabel(won: Long): String =
    if (won % 10000L == 0L) "%,d만원".format(won / 10000L) else "%,d원".format(won)

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
            Text("💰 시공금액을 바꿨어요", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
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
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
            data = android.net.Uri.parse("tel:$phoneNumber")
        }
        context.startActivity(intent)
    }
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
                                "💸 입금 받았어요",
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
                                    if (isAutoCalculated) "💡 자동 계산 (수정 가능)" else "💵 약속됨",
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
                            Text("✅", fontSize = 22.sp)
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
                            Text("🚫", fontSize = 18.sp)
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
 * 2026-05-30 사장님 #4 통점 — 총금액 입력 영역.
 *
 * 사장님이 시공비 총액을 박으면 잔금 = 총금액 - 계약금 으로 자동 계산되어 잔금 PaymentRow 에 표시됨.
 * 사장님이 잔금을 직접 수정하면 그게 우선. 총금액 미입력이면 잔금 자동 계산 X (옛 동작).
 *
 * UI: EMPTY (총금액 미입력) = 작은 버튼, FILLED (입력됨) = 금액 + 수정.
 */
@Composable
private fun TotalAmountRow(
    totalAmount: Long?,
    depositAmount: Long?,
    balanceAmount: Long?,
    onTotalChange: (Long?) -> Unit
) {
    var editing by remember(totalAmount) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "총금액",
            style = MaterialTheme.typography.bodyMedium,
            color = TossTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        if (editing) {
            PaymentInlineEditor(
                initialAmount = totalAmount?.takeIf { it > 0L },
                onCancel = { editing = false },
                onSave = { newAmount ->
                    onTotalChange(newAmount)
                    editing = false
                }
            )
        } else if (totalAmount == null || totalAmount == 0L) {
            // EMPTY — 작은 버튼
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(TossGrayBg)
                    .clickable { editing = true }
                    .padding(vertical = 10.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    "💡 총금액 입력 → 잔금 자동 계산",
                    color = TossTextSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        } else {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    "₩${formatThousands(totalAmount)}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.TextButton(onClick = { editing = true }) {
                    Text("수정", color = TossTextSecondary, fontSize = 12.sp)
                }
                androidx.compose.material3.TextButton(onClick = { onTotalChange(null) }) {
                    Text("지움", color = TossTextTertiary, fontSize = 12.sp)
                }
            }
            // 사장님 참고 — 자동 계산 미리보기 (사장님이 안 박았어도)
            if (depositAmount != null && depositAmount > 0L && balanceAmount == null) {
                val auto = (totalAmount - depositAmount).coerceAtLeast(0L)
                Spacer(Modifier.height(4.dp))
                Text(
                    "= 잔금 자동 ₩${formatThousands(auto)} (총 - 계약금)",
                    color = TossBlue,
                    fontSize = 11.sp
                )
            }
        }
    }
}

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

/**
 * 숫자 입력 → 표시는 천단위 콤마. 커서 매핑은 단순(끝으로 고정)으로 처리.
 * 사장님이 직접 타이핑하기 보다 칩으로 가산하는 경우가 많아 단순 매핑이 적합.
 */
private val ThousandsSeparatorTransformation = androidx.compose.ui.text.input.VisualTransformation { text ->
    val raw = text.text
    if (raw.isEmpty()) return@VisualTransformation androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
    val number = raw.toLongOrNull() ?: return@VisualTransformation androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
    val formatted = java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(number)
    androidx.compose.ui.text.input.TransformedText(
        androidx.compose.ui.text.AnnotatedString(formatted),
        object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = formatted.length
            override fun transformedToOriginal(offset: Int): Int = raw.length
        }
    )
}

// 2026-05-25: StatusPill / statusColors 제거 — status pill UI 폐기.
//   카테고리 chip 으로 대체 예정 (Phase 2).

private fun callTypeLabel(raw: String): String = when (raw) {
    "INCOMING" -> "수신"
    "OUTGOING" -> "발신"
    "MISSED" -> "부재중"
    "REJECTED" -> "거절"
    "MANUAL" -> "수동 등록"
    else -> "통화"
}

/** 통화 한 줄 + 매칭된 녹음 ▶ 버튼 (여러 개면 가로로 나열, 1번/2번 표시). */
@Composable
private fun CallRecordRow(
    line: String,
    recordings: List<RecordingAttachmentEntity>,
    onPlay: (RecordingAttachmentEntity) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            line,
            style = MaterialTheme.typography.bodyMedium,
            color = TossTextSecondary,
            modifier = Modifier.weight(1f)
        )
        if (recordings.size == 1) {
            androidx.compose.material3.TextButton(
                onClick = { onPlay(recordings.first()) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
            ) {
                Text("▶ 녹음", color = TossBlue, fontWeight = FontWeight.SemiBold)
            }
        } else if (recordings.size > 1) {
            recordings.forEachIndexed { idx, rec ->
                androidx.compose.material3.TextButton(
                    onClick = { onPlay(rec) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Text("▶${idx + 1}", color = TossBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * 녹음 첨부 표시용 라벨. 파일명 그대로 보여주면 보기 흉하므로 파일명에서
 * 녹음 시각을 파싱해 "5/15 11:16 녹음" 형태로 정리. 패턴 안 맞으면 최후 fallback.
 */
private fun formatRecordingTitle(rec: RecordingAttachmentEntity): String {
    val parsed = com.detailline.callfollowcrm.recording.AdotFilenameParser.parse(rec.fileName)
    if (parsed != null) {
        return "${DateTimeUtils.formatShort(parsed.recordedAt)} 녹음"
    }
    // 패턴 미일치 (수동 선택 등) — 확장자만 떼고 보여줌
    val stem = rec.fileName.substringAfterLast('/').substringBeforeLast('.')
    return if (stem.length > 24) stem.take(22) + "…" else stem
}

private fun playRecording(context: android.content.Context, fileUri: String) {
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(android.net.Uri.parse(fileUri), "audio/*")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

/**
 * 카테고리 알약. 할당 = 파랑 톤, 미할당 = "+ 카테고리" 회색.
 * 탭하면 [CategoryPickerDialog].
 */
@Composable
private fun CategoryPill(label: String, assigned: Boolean, onClick: () -> Unit) {
    val fg = if (assigned) TossBlue else TossTextSecondary
    val bg = if (assigned) com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
        else AppTheme.colors.bg
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier
            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 분류 타일 한 칸 — 큰 이모지 + 이름(두 줄까지) + 몇 명. (2026-09-19 사장님) */
@Composable
private fun CategoryTile(
    emoji: String,
    name: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) TossBlueSoft else Color.White)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) TossBlue else TossDivider,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 13.dp, horizontal = 9.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 21.sp)
        Spacer(Modifier.height(5.dp))
        Text(
            name, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            color = if (selected) TossBlueDark else TossTextPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            // 두 줄까지 — "인테리어 업체" 가 잘리면 타일의 의미가 없다.
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            lineHeight = 17.sp
        )
        if (count > 0) {
            Spacer(Modifier.height(2.dp))
            Text("${count}명", fontSize = 11.sp, color = TossTextTertiary)
        }
    }
}

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
            Text("이 손님은 어디에 둘까요?", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
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
                    "이름만 적으면 AI 가 대화 내용 보고 알아서 분류해드려요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("예: AS 고객, 일당, 친구", color = TossTextTertiary) },
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
        containerColor = Color.White
    )
}

@Composable
private fun CategoryChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val fg = if (selected) Color.White else TossTextPrimary
    val bg = if (selected) TossBlue else Color.White
    val border = if (selected) TossBlue else com.detailline.callfollowcrm.presentation.theme.TossDivider
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * "📍 현장 주소" 카드 탭 시 뜨는 입력 다이얼로그 (2026-05-28, DB v15).
 *   - currentAddress: 현재 저장된 수동 주소 (있으면 초기값으로 prefill)
 *   - extractedSuggestion: 메시지 자동 추출 결과 (currentAddress 와 다르면 칩으로 제안 — 한 탭에 input 박힘)
 *   - onCopyExisting: 기존 표시 주소 복사 (옛 UX 보존, displayAddr 있을 때만)
 *   사장님 의도: 자동 추출이 부정확할 때 사장님이 직접 박을 수 있게. 신뢰 데이터는 사장님이.
 */
@Composable
private fun AddressEditDialog(
    currentAddress: String?,
    extractedSuggestion: String?,
    onSave: (String?) -> Unit,
    onCopyExisting: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    // 2026-05-28 사장님 통점: 다이얼로그 입력 도중 [뒤로]/홈/잠금/전화 → 입력 날아감.
    //   composer 임시저장과 같은 원칙. remember → rememberSaveable 로 변경 → Bundle 저장 → recompose/destroy 살아남음.
    //   currentAddress 가 바뀌면 (다른 고객의 다이얼로그) 시드 새로 = key 로 분리.
    // 저장된 주소를 도로명(base) + 동·호수(detail)로 분리해서 다시 채운다.
    //   (2026-06-11 사장님 통점: 동·호수만 고치려 해도 전체가 주소칸에 들어가 처음부터 재검색 + "11동 22동" 중복 누적.)
    val (initBase, initDetail) = remember(currentAddress) { splitSiteAddress(currentAddress.orEmpty()) }
    var text by androidx.compose.runtime.saveable.rememberSaveable(currentAddress) {
        mutableStateOf(initBase)
    }
    // 동·호수(상세주소) 별도 입력 — 주소 검색은 도로명/지번까지만 주므로 검색 후 여기에 이어 적는다.
    //   (2026-06-10 사장님 통점: 검색 후 동/호수 칸이 따로 없어 같은 칸에 우겨넣어야 해 불편.)
    var detail by androidx.compose.runtime.saveable.rememberSaveable(currentAddress) {
        mutableStateOf(initDetail)
    }
    val detailFocus = remember { FocusRequester() }
    var focusDetail by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(focusDetail) {
        if (focusDetail) {
            runCatching { detailFocus.requestFocus() }
            focusDetail = false
        }
    }
    // 주소 검색(Daum 우편번호 WebView) — 선택 시 도로명주소 채우고 동/호수 칸으로 자동 포커스.
    var showSearch by remember { mutableStateOf(false) }
    if (showSearch) {
        com.detailline.callfollowcrm.presentation.component.AddressSearchDialog(
            onPicked = { picked -> text = picked; showSearch = false; focusDetail = true },
            onDismiss = { showSearch = false }
        )
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                Text(
                    "현장 주소 등록",
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "직접 입력한 주소가 메시지 자동 인식보다 우선해요. 길찾기에도 이 주소를 써요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary
                )
                Spacer(Modifier.height(14.dp))
                // 주소는 직접 타이핑 대신 반드시 검색으로 — 정확한 정규화 도로명주소 확보.
                //   (2026-06-10 사장님: 자유입력 칸 없애고 무조건 주소검색 한 번 하게.) 동/호수만 수동.
                //   2026-06-11 UI 개선: 회색 박스 + 검색 버튼이 둘 다 "검색 열기"라 헷갈림 →
                //     비었을 땐 검색 버튼 하나만, 고르면 주소 카드 + [변경] + 동·호수 (단계식).
                if (text.isBlank()) {
                    // ① 주소 미선택 — 큰 검색 버튼 하나(유일한 동작). 헷갈릴 여지 없음.
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(TossBlueSoft)
                            .clickable { showSearch = true }
                            .padding(vertical = 15.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("🔍", fontSize = 15.sp)
                        Spacer(Modifier.width(7.dp))
                        Text("주소 검색", color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    // ② 주소 선택됨 — 도로명 주소(직접 편집 가능) + [다시 검색].
                    //    (2026-06-14 사장님: 읽기전용이라 주소에 낀 오타·잡텍스트("아직 뮥바음" 등)를 못 지우고
                    //     동호수만 고치려 해도 [변경]으로 전체 재검색해야 했음 → 도로명도 직접 고치게 편집칸으로.)
                    androidx.compose.material3.OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("도로명 주소") },
                        leadingIcon = { Text("📍", fontSize = 15.sp) },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "🔍 주소 다시 검색",
                        color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .clickable { showSearch = true }
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                    // 동·호수 — 도로명만 채워지니 여기에 이어 적음. 별도 칸이라 동호수만 따로 고칠 수 있음.
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = detail,
                        onValueChange = { detail = it },
                        label = { Text("동·호수 (선택)") },
                        placeholder = { Text("예: 101동 1502호", color = TossTextTertiary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(detailFocus)
                    )
                }
                // 자동 추출 후보 — 사장님 한 탭에 input 박힘.
                if (extractedSuggestion != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "메시지에서 인식된 주소",
                        style = MaterialTheme.typography.labelSmall,
                        color = TossTextTertiary
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .background(TossBlueSoft)
                            .clickable { text = extractedSuggestion }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "📩 $extractedSuggestion",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    if (onCopyExisting != null) {
                        androidx.compose.material3.TextButton(onClick = onCopyExisting) {
                            Text("📋 복사", color = TossTextSecondary)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("취소", color = TossTextSecondary)
                    }
                    if (!currentAddress.isNullOrBlank()) {
                        androidx.compose.material3.TextButton(onClick = { onSave(null) }) {
                            Text("삭제", color = TossTextSecondary)
                        }
                    }
                    val combined = (text.trim() + if (detail.isBlank()) "" else " " + detail.trim()).trim()
                    androidx.compose.material3.TextButton(
                        onClick = { onSave(combined.takeIf { it.isNotEmpty() }) },
                        enabled = combined != currentAddress.orEmpty().trim()
                    ) {
                        Text("저장", color = TossBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * 공유 후 카드 — collab-sites-proto `a-after` 1:1. A(주인)가 고객 정보에서 협업 진행을 봄.
 *   헤더(협업 중 + 이름)·일당·진행 stepper·영구보관 안내·해제. 진행/일당은 서버 owner-events(같은 현장 제목 매칭),
 *   서버 미가동/없으면 배정 단계만(graceful). 증거사진은 §F GET /api/shared/photos (A 는 보기만).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun CollabAfterCard(
    partnerName: String,
    siteTitle: String,
    shareId: String,
    onRelease: () -> Unit
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container }
    val purpleSoft = AppTheme.colors.categoryBg

    // 서버 진행 이벤트(있으면) — 같은 현장 제목으로 매칭. 없으면 배정 단계만(graceful).
    var step by remember(siteTitle, partnerName) { mutableStateOf<String?>(null) }
    var wage by remember(siteTitle, partnerName) { mutableStateOf<Int?>(null) }
    var photos by remember(shareId, siteTitle) { mutableStateOf(emptyList<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedPhoto>()) }
    // 수락 여부 — by-me 서버 status("pending"/"accepted"). 수락 전엔 '협업 중' 대신 작은 '수락 대기중' 카드. (2026-07-01 사장님)
    var status by remember(shareId, siteTitle, partnerName) { mutableStateOf<String?>(null) }
    var confirmRelease by remember { mutableStateOf(false) }
    // 사진 뷰어 — 열린 사진의 인덱스(null=닫힘). 좌우 스와이프로 다음/이전 사진. (2026-07-01 사장님)
    var viewerIdx by remember { mutableStateOf<Int?>(null) }
    // 협업 사장과 현장 한 줄 논의(댓글) — 고객정보 협업 탭에서도. resolvedSid=실제 share_id(옛기록은 byMe로 보충). (2026-07-01 사장님)
    var resolvedSid by remember(shareId, siteTitle) { mutableStateOf(shareId) }
    var comments by remember(shareId, siteTitle) { mutableStateOf(emptyList<com.detailline.callfollowcrm.ai.SharedSiteRepository.SiteComment>()) }
    var commentBusy by remember { mutableStateOf(false) }
    val commentScope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(siteTitle, partnerName, shareId) {
        val owner = container.preferences.bizPhone.filter { it.isDigit() }
        if (owner.length >= 9) {
            var sid = shareId
            container.sharedSiteRepository.ownerEvents(owner).onSuccess { events ->
                // 진행 매칭은 shareId(고유키) 우선 — 제목/상대이름 문자열 일치는 깨지기 쉬움.
                //   버그: 초대 때 굳은 title(그때 주소) vs 지금 주소로 다시 만든 siteTitle 이 다르거나,
                //   로컬 상대이름("디테일라인 사장")과 서버 partner_name("디테일라인")이 달라 이벤트가 하나도
                //   안 잡히면 → 완료를 눌러도 step=null → '배정'에 멈춰 '협업 중'으로 보임. (2026-07-06 사장님)
                //   shareId 없는 옛 기록만 제목+상대이름으로 폴백.
                val mine = (if (sid.isNotBlank()) events.filter { it.shareId == sid }
                            else events.filter { it.title == siteTitle && (partnerName == "협업 사장님" || it.partnerName == partnerName) })
                    .maxByOrNull { it.atMs }
                step = mine?.step
                wage = mine?.dailyWage
                if (sid.isBlank()) sid = mine?.shareId.orEmpty()  // 옛 기록(shareId 없음) → 이벤트에서 보충
            }
            // 증거사진 조회(§F) — A 는 보기만(프로토 a-after).
            if (sid.isNotBlank()) container.sharedSiteRepository.photos(sid, owner).onSuccess { photos = it }
            // 수락 여부(by-me status) — shareId 우선, 없으면 제목+상대이름 매칭.
            container.sharedSiteRepository.byMe(owner).onSuccess { mySites ->
                val m = (if (sid.isNotBlank()) mySites.firstOrNull { it.shareId == sid } else null)
                    ?: mySites.firstOrNull { it.title == siteTitle && (partnerName == "협업 사장님" || it.partnerName == partnerName) }
                if (m != null) status = m.status
            }
            resolvedSid = sid
            if (sid.isNotBlank()) container.sharedSiteRepository.comments(sid, owner).onSuccess { comments = it }
        }
    }
    // 자동 새로고침(폴링) — 카톡처럼 상대 댓글이 저절로 올라오게. 화면 열려있는 동안 4초 간격. (2026-07-01 사장님)
    androidx.compose.runtime.LaunchedEffect(resolvedSid) {
        val sid = resolvedSid
        val ownerP = container.preferences.bizPhone.filter { it.isDigit() }
        if (sid.isBlank() || ownerP.length < 9) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(4000)
            container.sharedSiteRepository.comments(sid, ownerP).onSuccess { comments = it }
        }
    }
    val curIdx = when (step?.lowercase()) {
        "departed" -> 1; "arrived" -> 2; "completed" -> 3; else -> 0
    }
    // 완료된 현장 = 헤더 '협업 완료'(초록) + 큰 stepper 접기. 끝난 현장인데 배정/출발/도착/완료가
    //   그대로 떠서 카드만 커 보이던 것 정리. (2026-07-04 사장님)
    val completed = curIdx >= 3
    // 확정된 accepted 이거나 진행 단계가 있으면 '협업 중' 전체 카드. 그 전(pending·확인 전)엔 작은 '수락 대기중'.
    //   수락 전인데 협업중처럼 큰 카드가 떠서 '이미 함께 일하는 줄' 착각하는 문제 방지. (2026-07-01 사장님)
    val collabActive = status == "accepted" || curIdx > 0
    if (!collabActive) {
        Column(
            Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
                .border(1.dp, Color(0xFFF0E4C8), RoundedCornerShape(16.dp)).padding(15.dp)
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("🤝 ${partnerName} 사장님", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(AppTheme.colors.cautionBg).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("수락 대기중", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB8780A))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("아직 상대 사장님이 수락 전이에요. 수락하면 진행·사진이 여기 바로 떠요.",
                fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp)
            Spacer(Modifier.height(10.dp))
            Text("요청 취소", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { confirmRelease = true }.padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (confirmRelease) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmRelease = false },
                title = { Text("요청을 취소할까요?", fontWeight = FontWeight.Bold) },
                text = { Text("${partnerName}님께 보낸 협업 요청을 취소해요. 나중에 다시 보낼 수 있어요.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmRelease = false; onRelease() }) {
                        Text("요청 취소", color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmRelease = false }) {
                        Text("그대로 두기", color = TossTextSecondary)
                    }
                }
            )
        }
        return
    }

    Column(
        Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.dp, Color(0xFFE2D8FB), RoundedCornerShape(16.dp)).padding(15.dp)
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            // 제목 = 업체명(협업 사장 이름) — 2명 이상일 때 한눈에 구분(사장님 2026-08-09). 상태는 오른쪽 알약에.
            Text("🤝 $partnerName", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(if (completed) AppTheme.colors.doneBg else purpleSoft).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(if (completed) "협업 완료" else "협업 중", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (completed) Color(0xFF0E9F56) else Color(0xFF6B4FD8))
            }
        }
        wage?.let {
            Spacer(Modifier.height(9.dp))
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("💰 그날 일당", fontSize = 13.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text("${it}만원", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (completed) {
            // 완료 = 배정~완료 다 끝남. 큰 4단 stepper 는 접고 초록 한 줄로. (2026-07-04 사장님)
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.doneBg)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(999.dp)).background(AppTheme.colors.done),
                    contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("✓", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Spacer(Modifier.width(9.dp))
                Text("모든 진행이 끝난 현장이에요", fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold, color = Color(0xFF0E9F56))
            }
        } else {
            Text("상대가 올린 진행·사진·메모가 여기 그대로 들어와요.", fontSize = 12.sp, color = TossTextTertiary)
            Spacer(Modifier.height(12.dp))
            // 진행 stepper (A가 보는 상대 진행)
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                listOf("배정", "출발", "도착", "완료").forEachIndexed { i, label ->
                    Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        val done = i < curIdx; val cur = i == curIdx
                        val bg = when { done -> AppTheme.colors.done; cur -> AppTheme.colors.primary; else -> TossGrayBg }
                        val fg = if (done || cur) Color.White else TossTextTertiary
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).background(bg), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(if (done) "✓" else "${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = fg)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (done || cur) TossTextPrimary else TossTextTertiary)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // 📸 협업 사장님이 올린 증거사진 (proto a-after) — A 는 보기만.
        Text("📸 ${partnerName}이 올린 현장 사진 · 증거용", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
        Spacer(Modifier.height(2.dp))
        Text("시공 전·작업 중 상태를 남겨둔 사진이에요. '원래 그랬어요' 증거 → 두 분 다 분쟁에서 보호돼요.",
            fontSize = 11.sp, color = TossTextTertiary, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        if (photos.isEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(vertical = 16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("🖼️", fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text("협업 사장님이 사진을 올리면 여기 보여요", fontSize = 11.5.sp, color = TossTextTertiary)
            }
        } else {
            // 스와이프 뷰어용 — null 아닌 비트맵만 모아 인덱스로 연다.
            val bmps = remember(photos) { photos.mapNotNull { it.bitmap } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                photos.forEach { p ->
                    val bmp = p.bitmap
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(92.dp).clip(RoundedCornerShape(11.dp)).background(AppTheme.colors.surfaceMuted)
                            .then(if (bmp != null) Modifier.clickable { viewerIdx = bmps.indexOf(bmp).coerceAtLeast(0) } else Modifier),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        if (bmp != null) androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(), contentDescription = p.label ?: "현장 사진",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        ) else Text("🖼️", fontSize = 18.sp)
                    }
                }
            }
        }
        // 협업 사장과 현장 한 줄 논의 — 자동 새로고침(폴링)으로 카톡처럼 올라옴. (2026-07-01 사장님)
        Spacer(Modifier.height(16.dp))
        com.detailline.callfollowcrm.presentation.component.CollabCommentSection(
            comments = comments,
            myPhone = container.preferences.bizPhone.filter { it.isDigit() },
            busy = commentBusy,
            onSend = { body, onResult ->
                val sid = resolvedSid
                val ownerP = container.preferences.bizPhone.filter { it.isDigit() }
                if (sid.isNotBlank() && ownerP.length >= 9) {
                    val myName = container.preferences.bizName.takeIf { it.isNotBlank() } ?: container.preferences.bizOwner
                    commentBusy = true
                    commentScope.launch {
                        val r = container.sharedSiteRepository.postComment(sid, ownerP, myName, body)
                        r.onSuccess { container.sharedSiteRepository.comments(sid, ownerP).onSuccess { comments = it } }
                            .onFailure {
                                // 실패해도 조용히 넘어가 쓴 글이 사라지던 것 → 안내. (2026-07-30)
                                android.widget.Toast.makeText(context, "한마디가 안 올라갔어요 — 잠시 후 다시 시도해주세요", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        commentBusy = false
                        onResult(r.isSuccess)   // 성공했을 때만 입력칸 비우기. (2026-08-12 오프라인 감사)
                    }
                } else onResult(false)   // 못 보냈으면 쓴 글 유지
            }
        )

        Spacer(Modifier.height(14.dp))
        // 영구보관 안내 (proto a-after verbatim)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossBlueSoft).padding(13.dp)) {
            Text("🗂 이 기록은 계속 남아요", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
            Spacer(Modifier.height(5.dp))
            Text("사진·메모·진행 기록은 이 고객 정보에 영구 보관돼요. 3개월 뒤 고객이 또 연락해도 이걸 바로 꺼내 보고 응대할 수 있어요. 협업을 해제해도 안 지워집니다.",
                fontSize = 12.sp, color = Color(0xFF3A4A66), lineHeight = 18.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text("협업 해제 (사진·메모는 그대로 보존)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { confirmRelease = true }.padding(vertical = 11.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
    if (confirmRelease) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRelease = false },
            title = { Text("협업 해제할까요?", fontWeight = FontWeight.Bold) },
            text = { Text("${partnerName}님께 '협업이 해제됐어요' 알림이 가요. 사진·메모·진행 기록은 그대로 남고, 나중에 다시 요청할 수 있어요.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRelease = false; onRelease() }) {
                    Text("해제", color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRelease = false }) {
                    Text("그대로 두기", color = TossTextSecondary)
                }
            }
        )
    }
    // 사진 스와이프 뷰어 — 전체화면 검정 + 좌우로 휙휙 넘김 + 페이지 표시 + 닫기(X). 카톡식. (2026-07-01 사장님)
    viewerIdx?.let { startIdx ->
        val bmps = remember(photos) { photos.mapNotNull { it.bitmap } }
        if (bmps.isNotEmpty()) {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = startIdx.coerceIn(0, bmps.size - 1)
            ) { bmps.size }
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { viewerIdx = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Black)) {
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState, modifier = Modifier.fillMaxSize(), pageSpacing = 12.dp
                    ) { page ->
                        androidx.compose.foundation.Image(
                            bitmap = bmps[page].asImageBitmap(),
                            contentDescription = "현장 사진 ${page + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                    androidx.compose.foundation.layout.Box(
                        Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = 18.dp)
                            .clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("${pagerState.currentPage + 1} / ${bmps.size}", color = Color.White,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    androidx.compose.foundation.layout.Box(
                        Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(14.dp).size(38.dp)
                            .clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.55f))
                            .clickable { viewerIdx = null },
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) { Text("✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/**
 * 협업 현장으로 공유 시트 — collab-sites-proto a-share 1:1.
 *   상대 사장 번호 입력 → /api/shared/invite. 가입 사장이면 인앱(상대 앱 "협업 현장"에 뜸),
 *   아니면 문자 링크(SmsIntentHelper). 자동발송 아님 — 상대 수락해야 시작.
 *   고객 전화번호/대화는 보내지 않음(customer_label = 안전 라벨만).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollabShareSheet(
    siteTitle: String,
    addr: String?,
    scheduledAtMs: Long?,
    customerId: Long,
    onShared: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = remember { (context.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container }
    val workers by container.notebookRepository.observeWorkers().collectAsState(initial = emptyList())
    val recentSmsContacts by container.smsContactCacheRepository.observeAll(40).collectAsState(initial = emptyList())
    var partnerPhone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var dailyWage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var startHour by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(-1) } // 출근 시간(24h). -1 = 미선택
    var sending by remember { mutableStateOf(false) }

    fun hourLabel(h: Int): String {
        val ampm = if (h < 12) "오전" else "오후"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$ampm ${h12}시"
    }

    val dateLabel = remember(scheduledAtMs) {
        if (scheduledAtMs == null || scheduledAtMs <= 0L) "날짜 미정"
        else java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREA).format(java.util.Date(scheduledAtMs))
    }

    fun send() {
        if (sending) return
        val owner = container.preferences.bizPhone.filter { it.isDigit() }
        if (owner.length < 9) {
            android.widget.Toast.makeText(context, "먼저 더보기 → 견적서·사업자 정보에서 내 전화번호를 등록해주세요", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val partner = partnerPhone.filter { it.isDigit() }
        if (partner.length < 9) {
            android.widget.Toast.makeText(context, "함께 할 사장님 번호를 확인해주세요", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        sending = true
        // 출근 시간 선택 시: 일정 날짜에 그 시각(정시)을 박아 scheduledAtMs 로 보냄 + time_label 도 함께.
        val baseMs = scheduledAtMs ?: 0L
        val effectiveMs = if (startHour in 0..23 && baseMs > 0L) {
            java.util.Calendar.getInstance().apply {
                timeInMillis = baseMs
                set(java.util.Calendar.HOUR_OF_DAY, startHour)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else baseMs
        val timeLabel = startHour.takeIf { it in 0..23 }?.let { hourLabel(it) }
        scope.launch {
            val res = container.sharedSiteRepository.invite(
                ownerPhone = owner, partnerPhone = partner, title = siteTitle,
                addr = addr, scheduledAtMs = effectiveMs,
                workSummary = null, memo = null, customerLabel = siteTitle,
                dailyWage = dailyWage.toIntOrNull(), timeLabel = timeLabel,
                ownerName = container.preferences.bizName
            )
            sending = false
            res.onSuccess { r ->
                val partnerName = workers.firstOrNull { it.phone.filter { ch -> ch.isDigit() }.takeLast(8) == partner.takeLast(8) }?.name
                    ?: partner
                val existsInNotebook = workers.any { it.phone.filter { ch -> ch.isDigit() }.takeLast(8) == partner.takeLast(8) }
                if (!existsInNotebook) {
                    runCatching {
                        container.notebookRepository.add(
                            kind = com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity.KIND_WORKER,
                            name = partner,
                            phone = partner,
                            tag = "협업",
                            memo = "협업 현장으로 함께 일한 사장님"
                        )
                    }
                }
                // 로컬 협업 기록(공유후카드 + 캘린더 🤝 표시용) — "customerId|phone|name". 번호 끝 8자리로 중복 방지.
                runCatching {
                    val key8 = partner.takeLast(8)
                    val already = container.preferences.collabAssignments.any { e ->
                        val p = e.split("|"); p.size >= 3 && p[0].toLongOrNull() == customerId && p[1].filter { it.isDigit() }.takeLast(8) == key8
                    }
                    if (!already) {
                        container.preferences.collabAssignments = container.preferences.collabAssignments + "$customerId|$partner|$partnerName|${r.shareId}"
                    }
                    onShared()
                }
                if (r.deduped) {
                    android.widget.Toast.makeText(context, "이미 이 현장으로 협업 중인 사장님이에요. (새 알림은 안 가요 — 다른 현장·사람은 새로 가요)", android.widget.Toast.LENGTH_LONG).show()
                } else if (r.route == "link" && !r.url.isNullOrBlank()) {
                    val body = r.smsDraft ?: "협업 현장 공유 — ${r.url}"
                    com.detailline.callfollowcrm.util.SmsIntentHelper.openSmsCompose(context, partner, body)
                    android.widget.Toast.makeText(context, "문자로 공유 링크를 보냈어요", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(context, "협업 요청을 보냈어요 — 상대 사장님이 수락하면 시작돼요", android.widget.Toast.LENGTH_LONG).show()
                }
                onDismiss()
            }.onFailure {
                android.widget.Toast.makeText(context, "공유 실패 — 잠시 후 다시 시도해주세요", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()
        ) {
            // 내용은 스크롤, "협업 요청 보내기" 버튼은 하단 고정 — 칩 많을 때 버튼이 화면 밖으로 잘리던 문제. (2026-06-12 사장님)
            Column(Modifier.heightIn(max = 600.dp).padding(20.dp)) {
                com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text("협업 현장으로 공유", style = MaterialTheme.typography.titleLarge, color = TossTextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                // 현장 카드
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(13.dp)) {
                    Text(siteTitle, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                    Spacer(Modifier.height(3.dp))
                    Text(dateLabel + (addr?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(14.dp))
                // 자주 부르는 사람 — 번호 치기 전에 먼저 (많이 부른 순). 수첩(협업/일당·알바) 등록자 자동 목록. (2026-08-28 사장님)
                run {
                    val freq = container.preferences.collabAssignments.mapNotNull {
                        it.split("|").getOrNull(1)?.filter { c -> c.isDigit() }?.takeLast(8)
                    }.groupingBy { it }.eachCount()
                    val picks = workers
                        .filter { it.phone.filter { c -> c.isDigit() }.length >= 9 }
                        .sortedByDescending { freq[it.phone.filter { c -> c.isDigit() }.takeLast(8)] ?: 0 }
                        .take(10)
                    if (picks.isNotEmpty()) {
                        Text("자주 부르는 사람 — 눌러서 선택", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                            modifier = Modifier.padding(bottom = 6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            picks.forEach { w ->
                                val n = freq[w.phone.filter { c -> c.isDigit() }.takeLast(8)] ?: 0
                                CollabPhoneChip(w.name + (if (n > 0) " ·$n" else "")) { partnerPhone = w.phone.filter { it.isDigit() } }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("목록에 없으면 아래에 번호를 직접 입력하세요.", fontSize = 11.sp, color = TossTextTertiary)
                        Spacer(Modifier.height(14.dp))
                    }
                }
                Text("함께 할 사장님 번호", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                    modifier = Modifier.padding(bottom = 6.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = partnerPhone,
                    onValueChange = { partnerPhone = it.filter { c -> c.isDigit() }.take(11) },
                    placeholder = { Text("010-0000-0000", color = TossTextTertiary) },
                    singleLine = true,
                    visualTransformation = com.detailline.callfollowcrm.presentation.component.PhoneHyphenTransformation,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("시공막내 쓰는 사장님이면 그 분 앱으로, 아니면 문자 링크로 가요.", fontSize = 11.sp, color = TossTextTertiary)

                // 그날 일당 — 번호 바로 밑(눈에 띄게). 불러오기 칩은 그 아래.
                Spacer(Modifier.height(14.dp))
                Text("그날 일당 (선택)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                    modifier = Modifier.padding(bottom = 6.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = dailyWage,
                    onValueChange = { dailyWage = it.filter { c -> c.isDigit() }.take(4) },
                    placeholder = { Text("25", color = TossTextTertiary) },
                    trailingIcon = { Text("만원", color = TossTextSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("합의한 일당을 적으면 상대 사장님 화면에 보라색 일당 태그로 떠요. 비워도 됩니다.", fontSize = 11.sp, color = TossTextTertiary)

                // 출근 시간 — 상대 사장님이 "몇 시까지 가면 되는지" 알게. 정시 칩으로 빠르게.
                Spacer(Modifier.height(14.dp))
                Text("출근 시간 (선택)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                    modifier = Modifier.padding(bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(7, 8, 9, 10, 11, 13, 14).forEach { h ->
                        val selected = startHour == h
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(if (selected) AppTheme.colors.category else TossGrayBg)
                                .clickable { startHour = if (selected) -1 else h }
                                .padding(horizontal = 13.dp, vertical = 8.dp)
                        ) {
                            Text(hourLabel(h), fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else TossTextSecondary, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (startHour in 0..23) "출발 2시간 전에 상대 사장님께 '오늘 ${hourLabel(startHour)} ○○ 현장' 알림이 가요."
                    else "정하면 상대 사장님께 시작 시간이 보여요. 안 정해도 됩니다.",
                    fontSize = 11.sp, color = TossTextTertiary
                )

                val workerCandidates = workers.filter { it.phone.filter { ch -> ch.isDigit() }.length >= 9 }.take(8)
                val smsCandidates = recentSmsContacts
                    .filter { it.address.filter { ch -> ch.isDigit() }.length >= 9 }
                    .filterNot { sms -> workerCandidates.any { it.phone.filter { ch -> ch.isDigit() }.takeLast(8) == sms.address.filter { ch -> ch.isDigit() }.takeLast(8) } }
                    .take(8)
                // (수첩 사람 목록은 위 '자주 부르는 사람'으로 올렸음 — 2026-08-28 사장님) 여기선 최근 문자만.
                if (smsCandidates.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("최근 문자에서 불러오기", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        smsCandidates.forEach { s ->
                            CollabPhoneChip(com.detailline.callfollowcrm.util.PhoneNumberFormatter.format(s.address)) { partnerPhone = s.address.filter { it.isDigit() } }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                // 벽 안내
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossBlueSoft).padding(13.dp)) {
                    Text("상대 사장님께 보이는 것", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                    Spacer(Modifier.height(6.dp))
                    Text("• 날짜·시간·주소·시공 범위\n• 전달 메모·사진·출발/도착/완료", fontSize = 12.5.sp, color = Color(0xFF3A4A66), lineHeight = 19.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("✕ 고객 전화번호·대화·다른 고객은 안 보여요", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.unpaid)
                }
                } // ── 스크롤 영역 끝, 아래(보내기·취소)는 하단 고정 ──
                Spacer(Modifier.height(16.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppTheme.colors.category)
                        .clickable(enabled = !sending) { send() }.padding(vertical = 15.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(if (sending) "보내는 중…" else "🤝 협업 요청 보내기", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(10.dp))
                Text("자동 발송 아님 · 상대가 수락해야 시작돼요", fontSize = 11.5.sp, color = TossTextTertiary,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("취소", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onDismiss() }.padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CollabPhoneChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(TossGrayBg)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary, maxLines = 1)
    }
}

/**
 * "📩 주고받은 문자" 접이식 섹션의 단일 메시지 행 (2026-05-27).
 *   - 발신 (sent=true) = 사장님이 보낸 = 파란 칩 + 우측 정렬 톤
 *   - 수신 (sent=false) = 고객이 보낸 = 회색 칩 + 좌측 정렬 톤
 *   - 본문 + 시각 (2줄 max truncate)
 * ChatScreen 의 ChatBubble 보다 간소화. 대화 흐름 빠르게 훑기 용도.
 */
@Composable
private fun MessagePreviewRow(msg: com.detailline.callfollowcrm.data.repository.SmsRepository.SmsMessage) {
    val sent = msg.sent
    val bgColor = if (sent) TossBlueSoft else AppTheme.colors.bg
    val labelText = if (sent) "보냄" else "받음"
    val labelColor = if (sent) TossBlue else TossTextSecondary

    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    labelText,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    com.detailline.callfollowcrm.util.DateTimeUtils.formatShort(msg.dateMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = TossTextTertiary
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                msg.body,
                style = MaterialTheme.typography.bodySmall,
                color = TossTextPrimary,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 건(件) 탭 — 한 고객의 시공을 크롬 새 탭처럼 옆으로. (2026-09-17 사장님 "B안이 괜찮다")
 *
 * 왜 탭인가: 한 번에 **한 건만** 보여주면 화면이 짧다.
 *   (같이 본 A안 = 카드를 위아래로 쌓는 목록형. 사장님이 B안을 골랐다.)
 *
 * 차수는 **오래된 것이 1차**다. 지난 건들 다음이 지금 건.
 * 건이 하나뿐이어도 그린다 — 「＋ 새 시공」이 이 줄에만 있어서, 안 그리면 2번째 시공을 잡을 길이 없다. (2026-09-17)
 */
/**
 * 이 건은 **마무리됐나** — 잔금을 받았으면 마무리. (2026-09-18 사장님 확정 · 프로토 `closed()`)
 *   잔금이 애초에 없는 건은 완료 표시만으로 마무리로 본다.
 */
private fun jobClosed(j: com.detailline.callfollowcrm.data.local.entity.JobEntity): Boolean =
    j.balancePaidAt != null || (j.workCompletedAt != null && (j.balanceAmount ?: 0L) <= 0L)

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
 *   · 날짜순, 날짜 없는 건은 맨 앞
 *
 * 🔴 2026-09-19 사장님: "1차를 고르면 2차 현장메모가 나오고 2차를 고르면 3차 메모가 나오네"
 *   탭은 취소·빈 건을 빼고 세는데 여기선 다 세고 있어서 **번호가 하나씩 밀렸다.**
 *   같은 규칙을 두 군데 따로 적으면 또 어긋난다 — 셈은 여기 하나로 모은다.
 */
private fun jobNthOf(
    all: List<com.detailline.callfollowcrm.data.local.entity.JobEntity>,
    job: com.detailline.callfollowcrm.data.local.entity.JobEntity
): Int {
    val ordered = all.filterNot { jobCancelled(it) }.sortedBy { it.scheduledWorkDate ?: 0L }
    val i = ordered.indexOfFirst { it.id == job.id }
    return if (i < 0) ordered.size + 1 else i + 1
}

/** '이 사람은 [고객 아님][고객]' 알약 하나. 고른 쪽만 파랗게. (2026-09-17) */
@Composable
private fun CustomerKindPill(text: String, on: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (on) TossBlue else TossGrayBg)
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 6.dp)
    ) {
        Text(
            text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
            color = if (on) Color.White else TossTextSecondary
        )
    }
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
    val slots: List<Pair<Long, com.detailline.callfollowcrm.data.local.entity.JobEntity?>> =
        remember(pastJobs, current.scheduledWorkDate, selectedPastJobId) {
            val xs = ArrayList<Pair<Long, com.detailline.callfollowcrm.data.local.entity.JobEntity?>>()
            for (j in pastJobs) {
                if (jobFolded(j) && j.id != selectedPastJobId) continue
                xs.add((j.scheduledWorkDate ?: 0L) to j)
            }
            xs.add((current.scheduledWorkDate ?: Long.MAX_VALUE) to null)
            xs.sortedBy { it.first }
        }
    // 차수는 **숨겨진 지난 건까지 포함한 날짜순**. 접었다 폈다 해도 "2차"가 "1차"로 바뀌지 않는다.
    //   단 **취소한 건은 차수를 차지하지 않는다.** (2026-09-18 실기에서 발견)
    //   1차를 취소했더니 빈 자리가 "2차 · 신규" 라고 떴다 — 한 번도 안 한 시공이 2차일 수는 없다.
    val allDays: List<Long> = remember(pastJobs, current.scheduledWorkDate) {
        (pastJobs.filterNot { jobCancelled(it) }.map { it.scheduledWorkDate ?: 0L } +
            (current.scheduledWorkDate ?: Long.MAX_VALUE)).sorted()
    }
    fun nthOf(j: com.detailline.callfollowcrm.data.local.entity.JobEntity): Int =
        allDays.indexOf(j.scheduledWorkDate ?: 0L).let { if (it < 0) 1 else it + 1 }
    val curNth = allDays.indexOf(current.scheduledWorkDate ?: Long.MAX_VALUE).let { if (it < 0) 1 else it + 1 }
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

@Composable
private fun JobTab(
    nth: String,
    sub: String,
    on: Boolean,
    dashed: Boolean = false,
    /** '지난 건' 묶음 — 진행 중인 건과 구분되게 회색으로. (2026-09-18 프로토 `.chip.past`) */
    muted: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
    Column(
        Modifier
            .clip(shape)
            .background(
                when {
                    muted && on -> Color(0xFF4E5968)
                    on -> Color.White
                    else -> TossGrayBg
                }
            )
            .then(
                if (on && !muted) Modifier.border(1.5.dp, TossBlue, shape)
                else if (dashed) Modifier.border(1.dp, TossBlue.copy(alpha = 0.45f), shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp)
    ) {
        Text(
            nth,
            fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
            color = when {
                muted && on -> Color.White
                dashed -> TossBlue
                on -> TossTextPrimary
                else -> TossTextTertiary
            }
        )
        Spacer(Modifier.height(2.dp))
        Text(
            sub, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
            color = if (muted && on) Color(0xFFD6DBE1) else TossTextTertiary
        )
    }
}

/**
 * 지난 건 하나의 기록 — 읽기 전용. (2026-09-17 B안)
 * 끝난 일이라 여기서 고칠 게 없다. 고칠 일이 생기면 그때 붙인다.
 */
@Composable
private fun PastJobPanel(job: com.detailline.callfollowcrm.data.local.entity.JobEntity) {
    val settle = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(job)
    TossCard {
        Column {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(if (job.workCompletedAt != null) "🧾" else "🔨", fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                // 예정 건도 이 칸에 들어온다 — '끝난 시공' 이라고 쓰면 거짓말. (2026-09-17)
                Text(
                    if (job.workCompletedAt != null) "끝난 시공" else "앞으로 잡힌 시공",
                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                job.scheduledWorkDate?.let {
                    DateTimeUtils.formatKoreanDate(it) +
                        DateTimeUtils.workPeriodSuffix(it, job.scheduledWorkDays)
                } ?: "날짜 미상",
                fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary
            )
            if (!job.address.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text("📍 ${job.address}", fontSize = 12.5.sp, color = TossTextTertiary, lineHeight = 17.sp)
            }
            if (settle.total > 0L) {
                Spacer(Modifier.height(12.dp))
                PastJobKv("총 금액", manwonLabel(settle.total), TossTextPrimary)
                if (settle.depositAmount > 0L) {
                    PastJobKv("계약금", manwonLabel(settle.depositAmount), TossTextSecondary)
                }
                PastJobKv(
                    if (settle.isPaidOff) "잔금" else "남은 돈",
                    if (settle.isPaidOff) "전액 완납" else manwonLabel(settle.outstanding),
                    if (settle.isPaidOff) TossSuccess else com.detailline.callfollowcrm.presentation.theme.TossError
                )
            }
            if (job.memo.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("메모", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                Spacer(Modifier.height(4.dp))
                Text(job.memo, fontSize = 13.sp, color = TossTextSecondary, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun PastJobKv(k: String, v: String, vColor: Color) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(k, fontSize = 13.5.sp, color = TossTextTertiary)
        Spacer(Modifier.weight(1f))
        Text(v, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = vColor)
    }
}

