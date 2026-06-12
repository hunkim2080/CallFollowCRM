package com.detailline.callfollowcrm.presentation.screen.customer

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
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.detailline.callfollowcrm.presentation.component.vibrateCelebration
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import com.detailline.callfollowcrm.util.splitSiteAddress
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CustomerDetailScreen(
    viewModel: CustomerDetailViewModel,
    onBack: () -> Unit,
    /** "💬 문자 보내기" 탭 시 ChatScreen 으로 이동. customerId 알고 있으므로 같이 전달. */
    onOpenChat: (phone: String, customerId: Long) -> Unit
) {
    val customer by viewModel.customer.collectAsState()
    val records by viewModel.callRecords.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val systemSms by viewModel.systemSms.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var memoInput by remember(customer?.id) { mutableStateOf(customer?.memo.orEmpty()) }
    var datePickerOpen by remember { mutableStateOf(false) }
    // 협업 현장으로 공유 (collab-sites-proto a-card/a-share) — 다른 사장님께 이 현장 하나만 공유.
    var collabShareOpen by remember(customer?.id) { mutableStateOf(false) }
    var callsExpanded by remember(customer?.id) { mutableStateOf(false) }
    var orphanRecsExpanded by remember(customer?.id) { mutableStateOf(false) }
    var nameDialogOpen by remember { mutableStateOf(false) }
    var categoryDialogOpen by remember { mutableStateOf(false) }
    // 일정·정산 카드 금액 편집 다이얼로그 — "total"(총금액) / "deposit"(계약금) / null(닫힘).
    var amountEditField by remember { mutableStateOf<String?>(null) }
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
    // 현장 사진 삭제 확인 — null 이면 닫힘, 값 = 삭제 대상 photo id.
    var photoToDelete by remember { mutableStateOf<Long?>(null) }
    // 팀원(서버) 사진 삭제 확인 — 사장님이 퇴사한 팀원 사진도 지울 수 있게. (2026-06-07)
    var teamPhotoToDelete by remember { mutableStateOf<Long?>(null) }

    // composer 는 bottomBar 로 이동됨. 스크롤 영향 안 받아 bringIntoView 등 복잡한 로직 불필요.
    val scrollState = rememberScrollState()

    // ViewModel 측 toast (인라인 발송 결과 등) → 스낵바로 노출
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    LaunchedEffect(customer?.id) {
        customer?.let {
            memoInput = it.memo
        }
    }

    // memo auto-save — 사용자가 타이핑 멈춘 뒤 400ms 후 저장. 자동 debounce 패턴.
    // memoInput 이 바뀔 때마다 이전 effect 가 cancel 되므로 마지막 변경만 저장됨.
    LaunchedEffect(memoInput, customer?.id) {
        val c = customer ?: return@LaunchedEffect
        if (memoInput == c.memo) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        viewModel.updateMemo(memoInput)
    }
    // 화면 떠날 때 마지막 변경분이 아직 debounce 중이면 flush — 저장 못 한 채 닫히지 않게.
    DisposableEffect(customer?.id) {
        onDispose {
            val c = customer
            if (c != null && memoInput != c.memo) {
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
                    .padding(inner)
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
                .padding(inner)
                .fillMaxSize()
                // imePadding() 을 verticalScroll 전에 둬서, 키보드가 올라오면 스크롤 영역이
                // 자동으로 축소 → 포커스된 인라인 composer 가 키보드 위로 자동 정렬됨.
                .imePadding()
                .background(TossGrayBg)
                .verticalScroll(scrollState)
                // bottom 을 크게 둬서 키보드 위로 입력칸이 바짝 붙지 않고 숨 쉴 공간 확보.
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 프로토 cd-card 헤더 — heat 점 + 이름(크게) + [변경] / 전화번호 + [분류 ›] + 📞.
            val categories by viewModel.categories.collectAsState()
            val currentCat = categories.firstOrNull { it.id == c.categoryId }
            val headerName = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber)
            val headerCtx = androidx.compose.ui.platform.LocalContext.current
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(17.dp)
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(9.dp).clip(CircleShape).background(heatDotColor(c.leadHeat))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        headerName, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                        letterSpacing = (-0.6).sp, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.weight(1f))
                    androidx.compose.foundation.layout.Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(TossGrayBg)
                            .clickable { nameDialogOpen = true }.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Edit, null, tint = TossTextTertiary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("변경", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(PhoneNumberFormatter.format(c.phoneNumber), fontSize = 14.sp, color = TossTextSecondary)
                    Spacer(Modifier.weight(1f))
                    androidx.compose.foundation.layout.Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(TossGrayBg)
                            .clickable { categoryDialogOpen = true }.padding(horizontal = 13.dp, vertical = 6.dp)
                    ) {
                        Text(
                            currentCat?.let { (it.emoji?.let { e -> "$e " } ?: "") + it.name + " ›" } ?: "분류 ›",
                            fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(34.dp).clip(CircleShape).background(TossGrayBg)
                            .clickable { dialPhone(headerCtx, c.phoneNumber) },
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Phone, "전화", tint = TossTextSecondary, modifier = Modifier.size(17.dp))
                    }
                }
            }

            // 1.2 고객 페르소나 (프로토 openCustomer #2 — 헤더 바로 아래). 킬러콘텐츠 5단계.
            //   cowork prepare-reply 가 자동 생성(Haiku 4.5, 24h cache). 안드는 cache 조회만, 없으면 숨김.
            val persona by viewModel.persona.collectAsState()
            // 내용 있으면 보여주고, 내용은 없어도 생성 중(stale)이면 "분석 중" 카드로 자연스럽게. 둘 다 아니면 숨김.
            persona?.let { p -> if (!p.isEmpty || p.stale) PersonaCard(p) }

            // 1.3 현장 주소 — 표시 우선순위 (2026-05-28 사장님 결정):
            //   1) customer.address (사장님 수동 등록, DB v15) — 신뢰 최우선
            //   2) extractedAddress (메시지 자동 추출) — fallback
            //   3) 빈 상태 — "탭해서 등록" 안내
            //   탭 동작: 어느 상태든 AddressEditDialog 띄움 (입력/수정 가능).
            //   탭 길게 누름 = 복사 (기존 UX 보존) — 추후 BottomSheet 로 전환 가능.
            val extractedAddress by viewModel.extractedAddress.collectAsState()
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            val ctx = LocalContext.current
            val manualAddress = c.address?.takeIf { it.isNotBlank() }
            val displayAddr = manualAddress ?: extractedAddress
            var showAddressDialog by remember { mutableStateOf(false) }

            if (displayAddr != null) {
                // 프로토 .addr-card — 그라데이션 + 주소 + [길찾기 시작] 큰 파란 버튼.
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFF5F9FF), Color.White)))
                        .border(1.5.dp, Color(0xFFE2EDFD), RoundedCornerShape(18.dp))
                        .clickable { showAddressDialog = true }
                        .padding(17.dp)
                ) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("📍", fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "현장 주소" + (if (c.scheduledWorkDate != null) " · 예약 고객" else ""),
                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary
                        )
                        Spacer(Modifier.weight(1f))
                        Text(if (manualAddress != null) "✏️" else "＋", fontSize = 14.sp, color = TossBlue)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(displayAddr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, lineHeight = 22.sp)
                    if (manualAddress == null) {
                        Spacer(Modifier.height(3.dp))
                        Text("메시지에서 자동 인식 · 탭해서 확정/수정", fontSize = 11.sp, color = TossTextTertiary)
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
            } else {
                TossCard(onClick = { showAddressDialog = true }) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("📍", fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("현장 주소", style = MaterialTheme.typography.labelSmall, color = TossTextTertiary)
                            Spacer(Modifier.height(2.dp))
                            Text("아직 주소가 없어요 · 상담 단계예요", style = MaterialTheme.typography.bodyMedium, color = TossTextSecondary)
                            Spacer(Modifier.height(2.dp))
                            Text("탭해서 직접 등록하거나, 고객 메시지에 주소가 있으면 자동 채워져요.",
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

            // 1.4 협업 현장으로 공유 (collab-sites-proto a-card) — 다른 사장님과 이 현장 하나만 같이.
            //   예약(시공일)이 잡힌 고객만 = 진짜 "현장". 상담 단계(날짜 없음)는 공유할 현장이 없으니 섹션 숨김.
            //   (2026-06-11 사장님 요청)
            if (c.scheduledWorkDate != null) {
                Spacer(Modifier.height(12.dp))
                Text("이 현장 함께 하기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, Color(0xFFC8D3E2), RoundedCornerShape(16.dp))
                        .clickable { collabShareOpen = true }
                        .padding(16.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text("🤝 다른 사장님과 협업 현장으로 공유", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                    Spacer(Modifier.height(5.dp))
                    Text("같이 하기로 한 사장님께 이 현장 하나만 공유해요", fontSize = 12.5.sp, color = TossTextTertiary)
                }

                if (collabShareOpen) {
                    CollabShareSheet(
                        siteTitle = com.detailline.callfollowcrm.util.AddressExtractor.siteLabel(displayAddr).takeIf { it.isNotBlank() }
                            ?: c.name?.takeIf { it.isNotBlank() }?.let { "$it 현장" } ?: "협업 현장",
                        addr = displayAddr,
                        scheduledAtMs = c.scheduledWorkDate,
                        onDismiss = { collabShareOpen = false }
                    )
                }
            }

            // 1.5 AI 대화 요약 — 사장님 요청 (2026-05-24): 고객상세에서 문자 다시 검토 안 해도 흐름 파악.
            //   ChatScreen 의 ConversationSummaryBox 와 같은 데이터 (AiSummaryEntity.conversationSummaryJson).
            //   서버 미구현 또는 데이터 없으면 silent 숨김.
            val aiSummary by viewModel.aiSummary.collectAsState()
            aiSummary?.let { s ->
                val lines = com.detailline.callfollowcrm.ai.parseConversationLines(s.conversationSummaryJson)
                if (lines.isNotEmpty()) {
                    TossCard {
                        Column {
                            // 헤더 + 어디까지의 메시지를 본 요약인지 — 사장님이 최신화 여부 판단.
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("💬 대화 요약", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                                if (s.latestMessageTimestampMs > 0) {
                                    Text(
                                        " · ${com.detailline.callfollowcrm.util.DateTimeUtils.formatShort(s.latestMessageTimestampMs)} 까지",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TossTextTertiary
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            // 프로토 .sum-li — 파란 5px 점(sum-dot) + 본문(14sp, t2, line 1.7). 2026-06-04.
                            lines.forEach { line ->
                                androidx.compose.foundation.layout.Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.Top,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(TossBlue)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        line,
                                        fontSize = 14.sp,
                                        color = TossTextSecondary,
                                        lineHeight = 24.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1.7 (제거) "주고받은 문자" 접이식 섹션 — 프로토엔 없음(하단 "지난 문자 보기" 링크만).
            //   2026-06-03 사장님 결정 "프로토대로 제거".

            // 메모 카드 — 프로토 순서: 대화요약 → 메모 → 일정 · 정산 (2026-06-03 프로토대로 이동).
            //   라벨 = 프로토 cd-label "📝 메모"(이모지+12sp w800 t3) — 형제 카드와 통일(2026-06-04).
            TossCard {
                Column {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("📝", fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("메모", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                        Spacer(Modifier.weight(1f))
                        // 저장 버튼이 없어 "어떻게 저장하지?" 헷갈리던 문제 → 자동저장 상태를 항상 표시. (2026-06-11)
                        val savedMemo = c.memo.orEmpty()
                        val (memoStatus, memoStatusColor) = when {
                            memoInput != savedMemo -> "저장 중…" to TossTextTertiary
                            memoInput.isNotBlank() -> "저장됨 ✓" to Color(0xFF16A765)
                            else -> "자동으로 저장돼요" to TossTextTertiary
                        }
                        Text(memoStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = memoStatusColor)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = memoInput,
                        onValueChange = { memoInput = it },
                        placeholder = { Text("현관 비번·주의사항·고객 특징 등을 메모해두세요", color = TossTextTertiary) },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        colors = tossFieldColors()
                    )
                }
            }

            // 2. 프로토 "일정 · 정산" 카드 (사장님 결정 2026-06-02: 프로토 단순화).
            //    데이터(예약일·금액·계약금/잔금)는 그대로, UI 만 프로토 단순형(시공예약+총금액+계약금/잔금 상태+확인).
            run {
                val scheduled = c.scheduledWorkDate
                val totalWon = c.totalAmount ?: 0L
                val depositWon = c.depositAmount ?: 0L
                // 총금액 또는 계약금 중 하나라도 입력되면 정산 영역을 펼친다(계약금만 따로 넣는 경우 포함).
                val hasAmount = totalWon > 0L || depositWon > 0L
                val balanceWon = c.balanceAmount ?: (totalWon - depositWon).coerceAtLeast(0L)
                val depPaid = c.depositPaidAt != null
                val balPaid = c.balancePaidAt != null
                val allPaid = balPaid || (depPaid && balanceWon <= 0L)
                TossCard {
                    Column {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("💰", fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("일정 · 정산", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                        }
                        Spacer(Modifier.height(10.dp))
                        if (scheduled != null || hasAmount) {
                            CdKv(
                                "시공 예약",
                                if (scheduled != null) DateTimeUtils.formatKoreanDate(scheduled) + (c.scheduledWorkMinutes?.let { " " + DateTimeUtils.formatWorkMinutes(it) } ?: "") else "아직 예약 안 됨 · 탭해서 설정",
                                valueColor = if (scheduled != null) TossBlue else TossTextTertiary,
                                onClick = { datePickerOpen = true }
                            )
                            if (hasAmount) {
                                Spacer(Modifier.height(12.dp))
                                // 총금액 행 — 있으면 금액+[수정], 없으면 [총금액 입력]. (추가금/정정 대비 항상 수정 가능)
                                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    if (totalWon > 0L)
                                        Text("총 ${manwonLabel(totalWon)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                                    else
                                        Text("총금액 미입력", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary)
                                    Spacer(Modifier.weight(1f))
                                    MoneyEditPill(if (totalWon > 0L) "금액 수정" else "총금액 입력") { amountEditField = "total" }
                                }
                                Spacer(Modifier.height(8.dp))
                                // 계약금 행 — 총금액과 별개로 따로 입력/수정 (2026-06-11 사장님: 계약금을 따로 넣어야 함).
                                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    if (depositWon > 0L)
                                        Text("계약금 ${manwonLabel(depositWon)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TossTextSecondary)
                                    else
                                        Text("계약금 미설정", fontSize = 14.sp, color = TossTextTertiary)
                                    Spacer(Modifier.weight(1f))
                                    MoneyEditPill(if (depositWon > 0L) "계약금 수정" else "계약금 입력") { amountEditField = "deposit" }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    payStatusLabel(allPaid, depositWon, balanceWon, depPaid),
                                    fontSize = 13.sp, color = TossTextSecondary, lineHeight = 19.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                if (allPaid) {
                                    TossSecondaryButton(text = "완납 취소", onClick = { viewModel.setBalancePaid(false) })
                                } else if (depositWon > 0L && !depPaid) {
                                    TossPrimaryButton(text = "계약금 확인", onClick = { viewModel.setDepositPaid(true) })
                                } else if (depositWon > 0L) {
                                    TossPrimaryButton(text = "잔금 확인", onClick = {
                                        if (c.balanceAmount == null && balanceWon > 0L) viewModel.setBalanceAmount(balanceWon)
                                        viewModel.setBalancePaid(true)
                                    })
                                } else {
                                    TossPrimaryButton(text = "전액 확인", onClick = {
                                        if (c.balanceAmount == null && totalWon > 0L) viewModel.setBalanceAmount(totalWon)
                                        viewModel.setBalancePaid(true)
                                    })
                                }
                            } else {
                                Spacer(Modifier.height(12.dp))
                                TossSecondaryButton(text = "💰 총금액 입력", onClick = { amountEditField = "total" })
                                Spacer(Modifier.height(8.dp))
                                TossSecondaryButton(text = "💵 계약금 입력", onClick = { amountEditField = "deposit" })
                            }
                        } else {
                            // 2026-06-07 사장님 통점: 통화로 다 정해졌는데 일정 등록하려면 견적서 보내기밖에 없었음(고객이 또 입력).
                            //   → 이 자리에서 바로 "시공일 등록 / 총금액 입력". 고객 재입력 불필요. (견적서 경로도 보조로 유지)
                            Text(
                                "통화로 정해졌으면 여기서 바로 등록하세요. 고객에게 또 입력시키지 않아도 돼요.",
                                fontSize = 13.5.sp, color = TossTextSecondary, lineHeight = 21.sp
                            )
                            Spacer(Modifier.height(13.dp))
                            TossPrimaryButton(text = "📅 시공일 등록", onClick = { datePickerOpen = true })
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

            // 6.5 현장 사진 (프로토 openCustomer) — 사장님이 갤러리에서 골라 올림(로컬 저장). 2026-06-04 활성화.
            //   ※ 팀원↔사장님 공유는 서버(team_site_photos) 보강 후 별도 연동 — docs/SERVER_HANDOFF 참조.
            val sitePhotos by viewModel.sitePhotos.collectAsState()
            // 카톡식 사진 첨부 시트(아래→위) — 기존 시스템 "내 파일" 피커는 권한 거부/구형 fallback 으로만 유지. (2026-06-11)
            var showPhotoPicker by remember { mutableStateOf(false) }
            val photoPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetMultipleContents()
            ) { uris -> if (uris.isNotEmpty()) viewModel.addSitePhotos(uris) }
            val launchPhotoPicker = { showPhotoPicker = true }
            val photoMax = viewModel.sitePhotoMax
            val photoTotal = sitePhotos.size + teamPhotos.size
            if (showPhotoPicker) {
                com.detailline.callfollowcrm.presentation.component.PhotoPickerSheet(
                    maxSelectable = (photoMax - photoTotal).coerceAtLeast(0),
                    onConfirm = { uris ->
                        if (uris.isNotEmpty()) viewModel.addSitePhotos(uris)
                        showPhotoPicker = false
                    },
                    onDismiss = { showPhotoPicker = false },
                    onOpenFiles = { showPhotoPicker = false; photoPicker.launch("image/*") }
                )
            }
            TossCard {
                Column {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("📷", fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (photoTotal == 0) "현장 사진" else "현장 사진 ${photoTotal}장 / ${photoMax}",
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
                                                    .clip(RoundedCornerShape(6.dp))
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
                                    .background(Color(0xFFFFF8E1)).padding(12.dp)
                            ) {
                                Column {
                                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text(note.memberName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA66B00))
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
                                                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
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
                                                tint = Color(0xFFA66B00), modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text("답글 달기", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA66B00))
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
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TossTextPrimary),
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
                                                    .background(if (reply.isBlank()) Color(0xFFB7C2D0) else TossBlue)
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

            // 7. 프로토 openCustomer 하단 — 블로그 후기 lockcard(비즈니스 잠금) + "지난 문자 보기" 링크.
            val bottomCtx = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                    .clickable {
                        android.widget.Toast.makeText(bottomCtx, "블로그 후기 글 만들기는 비즈니스 요금제 기능이에요. 곧 제공돼요!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFF1ECFF)),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFF7C5CFC), modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("블로그 후기 글 만들기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text("대화+사진으로 포스팅 글 자동 작성", fontSize = 12.sp, color = TossTextTertiary)
                }
                Text(
                    "비즈니스", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFF7C5CFC)).padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                    .clickable { onOpenChat(c.phoneNumber, c.id) }.padding(15.dp),
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
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = customer?.scheduledWorkDate ?: System.currentTimeMillis()
        )
        // DatePickerDialog 기본 동작이 화면 위쪽에 붙어 보이는 이슈가 있어
        // Dialog 로 직접 감싸 vertical center 정렬. usePlatformDefaultWidth=false 로
        // 시스템 dialog 마진을 무시하고 Box 안에서 중앙 정렬.
        Dialog(
            onDismissRequest = { datePickerOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    color = Color.White,
                    tonalElevation = 6.dp
                ) {
                    Column {
                        DatePicker(
                            state = datePickerState,
                            modifier = Modifier.padding(top = 8.dp)
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
                                    Text("예약 취소", color = Color(0xFFE5484D), fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { datePickerOpen = false }) {
                                    Text("취소", color = TossTextSecondary)
                                }
                                TextButton(
                                    onClick = {
                                        val picked = datePickerState.selectedDateMillis
                                        if (picked != null) {
                                            // 일정 신규 등록/변경 모두 축하. 자동 status 전환은 폐기됨 (카테고리 시스템).
                                            val wasAlreadyScheduled = customer?.scheduledWorkDate != null
                                            viewModel.updateScheduledWorkDate(picked)
                                            if (!wasAlreadyScheduled) {
                                                celebrationVisible = true
                                                vibrateCelebration(context)
                                            }
                                        }
                                        datePickerOpen = false
                                    }
                                ) { Text("저장", color = TossBlue, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
            }
        }
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
        CategoryPickerDialog(
            categories = categories,
            selectedId = customer?.categoryId,
            onPick = { id ->
                viewModel.setCategory(id)
                categoryDialogOpen = false
            },
            onAddNew = { name ->
                viewModel.addCategoryAndAssign(name)
                categoryDialogOpen = false
            },
            onDismiss = { categoryDialogOpen = false }
        )
    }

    // 일정·정산 금액 편집 (총금액/계약금) — 만원 입력.
    amountEditField?.let { field ->
        AmountInputDialog(
            title = if (field == "total") "총금액" else "계약금",
            initialWon = if (field == "total") (customer?.totalAmount ?: 0L) else (customer?.depositAmount ?: 0L),
            onSave = { won ->
                if (field == "total") viewModel.setTotalAmount(won) else viewModel.setDepositAmount(won)
                amountEditField = null
            },
            onDismiss = { amountEditField = null }
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
                    Text("삭제", color = Color(0xFFF0436A), fontWeight = FontWeight.Bold)
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
                    Text("삭제", color = Color(0xFFF0436A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { teamPhotoToDelete = null }) { Text("취소", color = TossTextSecondary) }
            }
        )
    }

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
@Composable
private fun PersonaCard(persona: com.detailline.callfollowcrm.ai.CustomerPersona) {
    // 프로토 .persona-card — 연한 파란 그라데이션 + 테두리 + ✨ 고객 페르소나 + [AI 분석] 칩.
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF5F9FF), Color.White)))
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
                Text("✨", fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text("고객 페르소나", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                // "갱신 중"은 기존 내용이 있을 때만(=다시 다듬는 중). 내용이 아예 없을 땐 아래 안내가 설명하므로 숨김.
                if (persona.stale && hasContent) {
                    Spacer(Modifier.width(6.dp))
                    Text("· 갱신 중", fontSize = 11.sp, color = TossBlue)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "AI 분석", color = TossBlue, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFEEF4FF)).padding(horizontal = 8.dp, vertical = 2.dp)
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
    "ADOT_SHARE" -> "에이닷 공유"
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
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
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
    "HOT" -> Color(0xFFF0436A)
    "WARM" -> Color(0xFFF6A609)
    "COLD" -> Color(0xFFC2C9D2)
    else -> Color(0xFF3182F6)
}

/** 프로토 .kv — 라벨(왼쪽 t2) + 값(오른쪽 w700). 탭 가능. */
@Composable
private fun CdKv(label: String, value: String, valueColor: Color, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 9.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TossTextSecondary)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

/** 원 → "N만원"(만 단위로 떨어지면) / "N원". */
private fun manwonLabel(won: Long): String =
    if (won % 10000L == 0L) "%,d만원".format(won / 10000L) else "%,d원".format(won)

/** 프로토 payChipsHtml 상태 문구. */
private fun payStatusLabel(allPaid: Boolean, deposit: Long, balance: Long, depPaid: Boolean): String = when {
    allPaid -> "전액 완납 ✓"
    deposit > 0L && !depPaid -> "계약금 ${manwonLabel(deposit)} · 잔금 ${manwonLabel(balance)} 미수"
    deposit > 0L -> "계약금 ${manwonLabel(deposit)} 받음 · 잔금 ${manwonLabel(balance)} 남음"
    else -> "계약금 없음 · 전액 ${manwonLabel(balance)} 미수"
}

/** 금액 행 오른쪽 파란 알약 — [금액 수정]/[계약금 입력] 등. 탭 → 해당 입력 다이얼로그. */
@Composable
private fun MoneyEditPill(label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(TossBlueSoft)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(Icons.Default.Edit, null, tint = TossBlue, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossBlue)
    }
}

/** 금액(총금액/계약금) 입력 다이얼로그 — 만원 단위. */
@Composable
private fun AmountInputDialog(title: String, initialWon: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (initialWon > 0L) (initialWon / 10000L).toString() else "") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
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
                TossSecondaryButton(text = "취소", onClick = onDismiss, modifier = Modifier.weight(1f))
                TossPrimaryButton(text = "저장", onClick = { onSave((text.toLongOrNull() ?: 0L) * 10000L) }, modifier = Modifier.weight(1f))
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
                                        "📅 ${paidAt?.let { DateTimeUtils.formatKoreanDate(it) } ?: ""}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = com.detailline.callfollowcrm.presentation.theme.TossSuccess,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "탭해서 수정",
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
        val initial = paidAt ?: System.currentTimeMillis()
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
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
            androidx.compose.material3.DatePicker(state = state)
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
            textStyle = TextStyle(fontSize = 17.sp, color = TossTextPrimary, fontWeight = FontWeight.SemiBold)
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
        else Color(0xFFF1F3F5)
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

/**
 * 사장님 정의 카테고리 중 1개 선택. 빈 상태든 아니든 [+ 새 카테고리] 칩 항상 존재.
 * 2026-05-25: "홈에서 추가하세요" 안내문 제거 — 사장님이 여기서 바로 추가 가능.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickerDialog(
    categories: List<com.detailline.callfollowcrm.data.local.entity.CategoryEntity>,
    selectedId: Long?,
    onPick: (Long?) -> Unit,
    onAddNew: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var addDialogOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("카테고리 선택", color = TossTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryChoiceChip(
                        label = "미분류",
                        selected = selectedId == null,
                        onClick = { onPick(null) }
                    )
                    categories.forEach { c ->
                        val txt = if (c.emoji != null) "${c.emoji} ${c.name}" else c.name
                        CategoryChoiceChip(
                            label = txt,
                            selected = selectedId == c.id,
                            onClick = { onPick(c.id) }
                        )
                    }
                    // 항상 표시 — 빈 상태든 카테고리 N개든 사장님이 여기서 바로 추가.
                    CategoryChoiceChip(
                        label = "+ 새 카테고리",
                        selected = false,
                        onClick = { addDialogOpen = true }
                    )
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
    if (addDialogOpen) {
        CategoryNameInputDialog(
            onDismiss = { addDialogOpen = false },
            onConfirm = { name ->
                onAddNew(name)
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
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("카테고리 추가", color = TossTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "이름만 적으면 AI 가 대화 내용 보고 알아서 분류해드려요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("예: AS 고객, 일당, 아르바이트", color = TossTextTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tossFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onConfirm(name.trim())
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
                    // ② 주소 선택됨 — 선택된 주소 + [변경](재검색). 박스는 읽기용(누르면 안 바뀜 오해 방지).
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(Color(0xFFF5F7FA))
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("📍", fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TossTextPrimary, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "변경",
                            color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .clickable { showSearch = true }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                    // 동·호수 — 주소 고른 뒤에만 노출(검색 후 자동 포커스). 도로명만 채워지니 여기에 이어 적음.
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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = remember { (context.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container }
    val workers by container.notebookRepository.observeWorkers().collectAsState(initial = emptyList())
    val recentSmsContacts by container.smsContactCacheRepository.observeAll(40).collectAsState(initial = emptyList())
    var partnerPhone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var dailyWage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    val dateLabel = remember(scheduledAtMs) {
        if (scheduledAtMs == null || scheduledAtMs <= 0L) "날짜 미정"
        else java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREA).format(java.util.Date(scheduledAtMs))
    }

    fun send() {
        if (sending) return
        val owner = container.preferences.bizPhone.filter { it.isDigit() }
        if (owner.length < 9) {
            android.widget.Toast.makeText(context, "먼저 더보기 → 사업자 정보에서 내 전화번호를 등록해주세요", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val partner = partnerPhone.filter { it.isDigit() }
        if (partner.length < 9) {
            android.widget.Toast.makeText(context, "함께 할 사장님 번호를 확인해주세요", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        sending = true
        scope.launch {
            val res = container.sharedSiteRepository.invite(
                ownerPhone = owner, partnerPhone = partner, title = siteTitle,
                addr = addr, scheduledAtMs = scheduledAtMs ?: 0L,
                workSummary = null, memo = null, customerLabel = siteTitle,
                dailyWage = dailyWage.toIntOrNull()
            )
            sending = false
            res.onSuccess { r ->
                val existsInNotebook = workers.any { it.phone.filter { ch -> ch.isDigit() }.takeLast(8) == partner.takeLast(8) }
                if (!existsInNotebook) {
                    runCatching {
                        container.notebookRepository.add(
                            kind = com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity.KIND_WORKER,
                            name = "협업 사장님",
                            phone = partner,
                            tag = "협업",
                            memo = "협업 현장으로 함께 일한 사장님"
                        )
                    }
                }
                if (r.route == "link" && !r.url.isNullOrBlank()) {
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
                Text("RING-GO 쓰는 사장님이면 그 분 앱으로, 아니면 문자 링크로 가요.", fontSize = 11.sp, color = TossTextTertiary)
                val workerCandidates = workers.filter { it.phone.filter { ch -> ch.isDigit() }.length >= 9 }.take(8)
                val smsCandidates = recentSmsContacts
                    .filter { it.address.filter { ch -> ch.isDigit() }.length >= 9 }
                    .filterNot { sms -> workerCandidates.any { it.phone.filter { ch -> ch.isDigit() }.takeLast(8) == sms.address.filter { ch -> ch.isDigit() }.takeLast(8) } }
                    .take(8)
                if (workerCandidates.isNotEmpty() || smsCandidates.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    if (workerCandidates.isNotEmpty()) {
                        Text("수첩 일당·알바에서 불러오기", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            workerCandidates.forEach { w ->
                                CollabPhoneChip(w.name) { partnerPhone = w.phone.filter { it.isDigit() } }
                            }
                        }
                    }
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
                }
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
                Spacer(Modifier.height(14.dp))
                // 벽 안내
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossBlueSoft).padding(13.dp)) {
                    Text("상대 사장님께 보이는 것", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                    Spacer(Modifier.height(6.dp))
                    Text("• 날짜·시간·주소·시공 범위\n• 전달 메모·사진·출발/도착/완료", fontSize = 12.5.sp, color = Color(0xFF3A4A66), lineHeight = 19.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("✕ 고객 전화번호·대화·다른 고객은 안 보여요", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF0436A))
                }
                } // ── 스크롤 영역 끝, 아래(보내기·취소)는 하단 고정 ──
                Spacer(Modifier.height(16.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF7C5CFC))
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
    val bgColor = if (sent) TossBlueSoft else Color(0xFFF3F4F6)
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
