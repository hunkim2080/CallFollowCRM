package com.detailline.callfollowcrm.presentation.screen.customer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity
import com.detailline.callfollowcrm.domain.model.CustomerStatus
import com.detailline.callfollowcrm.domain.model.LeadHeat
import com.detailline.callfollowcrm.presentation.component.CelebrationOverlay
import com.detailline.callfollowcrm.presentation.component.SectionLabel
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.detailline.callfollowcrm.presentation.component.vibrateCelebration
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
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
    var callsExpanded by remember(customer?.id) { mutableStateOf(false) }
    var statusDialogOpen by remember { mutableStateOf(false) }
    var orphanRecsExpanded by remember(customer?.id) { mutableStateOf(false) }
    var nameDialogOpen by remember { mutableStateOf(false) }
    var leadHeatDialogOpen by remember { mutableStateOf(false) }
    // MMS 사진 풀스크린 뷰어 — 썸네일 탭하면 set, 다이얼로그가 보여줌. null 이면 닫힘.
    var fullscreenImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var celebrationVisible by remember { mutableStateOf(false) }

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
                        "고객 상세",
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
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // Auto-save 채택 (2026-05-19) — '저장' 버튼 폐기. 모든 입력이 onChange 즉시 저장됨.
            // 하단 CTA 는 채팅 진입 하나만.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(TossGrayBg)
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                TossPrimaryButton(
                    text = "💬 문자 보내기",
                    onClick = { customer?.let { onOpenChat(it.phoneNumber, it.id) } },
                    enabled = customer != null
                )
            }
        }
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
            // 1. 기본 정보 카드 (전화번호 + 상태 알약 + 이름)
            TossCard {
                Column {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 번호 + 📞 를 한 덩어리로 묶음 (전화 액션은 번호에 귀속).
                        Text(
                            PhoneNumberFormatter.format(c.phoneNumber),
                            style = MaterialTheme.typography.headlineSmall,
                            color = TossTextPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        CallIconButton(phoneNumber = c.phoneNumber)
                        Spacer(Modifier.weight(1f))
                        // 상태는 멀리 우측에 분리 — 시선 충돌 방지.
                        StatusPill(
                            label = c.status,
                            onClick = { statusDialogOpen = true }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    NameRow(
                        currentName = c.name.orEmpty(),
                        onEdit = { nameDialogOpen = true }
                    )
                    // 리드 온도 배지 — status 와 다른 축. 통화 직후 카드에서 분류된 값 표시,
                    // 미분류면 작은 회색 배지로 노출 + 탭하면 변경 다이얼로그.
                    Spacer(Modifier.height(8.dp))
                    LeadHeatRow(
                        current = LeadHeat.fromNameOrNull(c.leadHeat),
                        onClick = { leadHeatDialogOpen = true }
                    )
                }
            }

            // 2. 일정 카드 — 첫 문의 / 최근 통화 / 시공 예약일 (메모보다 먼저, 액션 정보가 우선)
            TossCard {
                Column {
                    SectionLabel("일정")
                    Spacer(Modifier.height(10.dp))

                    val firstContact = records.minByOrNull { it.endedAt }?.endedAt ?: c.createdAt
                    val lastCall = records.maxByOrNull { it.endedAt }?.endedAt

                    ScheduleRow(
                        label = "첫 문의",
                        dateLabel = DateTimeUtils.formatKoreanDate(firstContact),
                        ddayLabel = DateTimeUtils.dDayLabel(firstContact)
                    )
                    if (lastCall != null && lastCall != firstContact) {
                        Spacer(Modifier.height(8.dp))
                        ScheduleRow(
                            label = "최근 통화",
                            dateLabel = DateTimeUtils.formatShort(lastCall),
                            ddayLabel = DateTimeUtils.dDayLabel(lastCall)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Divider(color = TossDivider)
                    Spacer(Modifier.height(12.dp))

                    val scheduled = c.scheduledWorkDate
                    if (scheduled == null) {
                        Text(
                            "시공 예약일",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TossTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "아직 예약 안 됨",
                            style = MaterialTheme.typography.labelMedium,
                            color = TossTextTertiary
                        )
                        Spacer(Modifier.height(10.dp))
                        TossSecondaryButton(
                            text = "📅 시공 예약일 설정",
                            onClick = { datePickerOpen = true }
                        )
                    } else {
                        ScheduleRow(
                            label = "시공 예약일",
                            dateLabel = DateTimeUtils.formatKoreanDate(scheduled),
                            ddayLabel = DateTimeUtils.dDayLabel(scheduled),
                            emphasize = true
                        )
                        Spacer(Modifier.height(14.dp))
                        // outline 버튼 두 개를 같은 너비로. 평범한 텍스트가 아니라 명확한 버튼 형태.
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { datePickerOpen = true },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TossBlue)
                            ) {
                                Text(
                                    "📅 날짜 변경",
                                    color = TossBlue,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.updateScheduledWorkDate(null) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TossDivider)
                            ) {
                                Text(
                                    "예약 취소",
                                    color = TossTextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // 3. 입금 카드 — 계약금/잔금 각각 체크 + 금액. 체크하면 받은 시각 자동 기록.
            //    영업 흐름상 메모보다 위에 두는 게 한눈에 들어옴.
            TossCard {
                Column {
                    SectionLabel("💰 입금")
                    Spacer(Modifier.height(10.dp))
                    PaymentRow(
                        label = "계약금",
                        amount = c.depositAmount,
                        paidAt = c.depositPaidAt,
                        onPaidChange = { viewModel.setDepositPaid(it) },
                        onAmountChange = { viewModel.setDepositAmount(it) },
                        onPaidAtChange = { viewModel.setDepositPaidAt(it) }
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.Divider(color = TossDivider)
                    Spacer(Modifier.height(10.dp))
                    PaymentRow(
                        label = "잔금",
                        amount = c.balanceAmount,
                        paidAt = c.balancePaidAt,
                        onPaidChange = { viewModel.setBalancePaid(it) },
                        onAmountChange = { viewModel.setBalanceAmount(it) },
                        onPaidAtChange = { viewModel.setBalancePaidAt(it) }
                    )
                }
            }

            // 4. 메모 카드
            TossCard {
                Column {
                    SectionLabel("메모")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = memoInput,
                        onValueChange = { memoInput = it },
                        placeholder = { Text("상담 내용을 적어두세요", color = TossTextTertiary) },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        colors = tossFieldColors()
                    )
                }
            }

            // 4. 문자 — 시스템 SMS/MMS(받음 + 보냄, 갤럭시 메시지/RING-GO 무관) 합쳐 시간순.
            //    여기는 정보 표시 전용. 대화/발송은 메인 ChatScreen 에서 (하단 "문자 보내기" 또는 대시보드).
            TossCard {
                Column {
                    SectionLabel(text = "문자")
                    Spacer(Modifier.height(8.dp))
                    val items = remember(systemSms) {
                        systemSms
                            .map { MessageRow(timeMs = it.dateMs, sent = it.sent, body = it.body, imageUris = it.imageUris) }
                            .sortedByDescending { it.timeMs }
                    }
                    if (items.isEmpty()) {
                        Text(
                            if (systemSms.isEmpty())
                                "문자 기록이 없어요. (설정에서 \"받은 문자 함께 보기\"를 켜야 보여요)"
                            else "문자 기록이 없어요",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossTextTertiary
                        )
                    } else items.take(80).forEach { m ->
                        MessageRowView(m, onImageTap = { fullscreenImageUri = it })
                        Spacer(Modifier.height(8.dp))
                    }

                    // composer 는 bottomBar 로 이동됨 (스크롤 영향 X). 여기엔 아무것도 없음.
                }
            }

            // 5. 통화 기록 — 헤더 탭으로 펼침/접힘. 기본 접힘 (공간 절약).
            //    펼친 상태에서 각 통화에 매칭된 녹음은 인라인 ▶ 버튼. 별도 "녹음 파일" 섹션 없음.
            //    매칭 안 된 녹음은 카드 하단에 또 한 단 접힘으로 분리.
            TossCard {
                Column {
                    val recsByCallId = remember(recordings) {
                        recordings.filter { it.callRecordId != null }.groupBy { it.callRecordId!! }
                    }
                    val orphanRecs = remember(recordings) {
                        recordings.filter { it.callRecordId == null }
                    }

                    // 클릭형 헤더
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = records.isNotEmpty()) {
                                callsExpanded = !callsExpanded
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        SectionLabel(
                            text = if (records.isEmpty()) "통화 기록" else "통화 기록 ${records.size}건",
                            modifier = Modifier.weight(1f)
                        )
                        if (records.isNotEmpty()) {
                            Text(
                                if (callsExpanded) "▾" else "▸",
                                color = TossTextTertiary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (records.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "통화 기록이 없어요",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossTextTertiary
                        )
                    } else if (callsExpanded) {
                        Spacer(Modifier.height(8.dp))
                        records.forEach { r ->
                            CallRecordRow(
                                line = "${DateTimeUtils.formatShort(r.endedAt)} · ${callTypeLabel(r.callType)} · ${DateTimeUtils.durationLabel(r.duration)}",
                                recordings = recsByCallId[r.id].orEmpty(),
                                onPlay = { rec -> playRecording(context, rec.fileUri) }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    if (orphanRecs.isNotEmpty() && callsExpanded) {
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.material3.Divider(color = TossDivider)
                        Spacer(Modifier.height(6.dp))
                        // 클릭형 헤더 한 줄 — 탭하면 목록 펼침/접힘.
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { orphanRecsExpanded = !orphanRecsExpanded }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                "통화에 매칭 안 된 녹음 ${orphanRecs.size}건",
                                style = MaterialTheme.typography.labelMedium,
                                color = TossTextSecondary,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (orphanRecsExpanded) "▾" else "▸",
                                color = TossTextTertiary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        if (orphanRecsExpanded) {
                            Spacer(Modifier.height(2.dp))
                            orphanRecs.forEach { rec ->
                                androidx.compose.foundation.layout.Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        formatRecordingTitle(rec),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TossTextSecondary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    androidx.compose.material3.TextButton(
                                        onClick = { playRecording(context, rec.fileUri) }
                                    ) {
                                        Text("▶ 재생", color = TossBlue, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. 에이닷 통화 요약 — 실제로 요약이 들어왔을 때만 노출.
            //    빈 상태 안내/붙여넣기 버튼은 숨김 ("준비 중" 톤). 에이닷 공유 인텐트는
            //    Activity 레벨(AdotSummaryImporter.importFromShare)에서 그대로 받으므로
            //    UI 없이도 자동 수집은 정상 동작.
            if (summaries.isNotEmpty()) {
                TossCard {
                    Column {
                        SectionLabel("에이닷 통화 요약")
                        Spacer(Modifier.height(8.dp))
                        summaries.forEach { s ->
                            SummaryItem(summary = s)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
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
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { datePickerOpen = false }) {
                                Text("취소", color = TossTextSecondary)
                            }
                            TextButton(
                                onClick = {
                                    val picked = datePickerState.selectedDateMillis
                                    if (picked != null) {
                                        // ViewModel 측에서 예약 확정으로 자동 전환됨. UI 측은 "새로 전환"되는
                                        // 케이스에서만 축하 (이미 예약 확정 상태에서 날짜만 바꾸는 케이스는 X).
                                        val wasAlreadyConfirmed = customer?.status == CustomerStatus.RESERVATION_CONFIRMED.label
                                        viewModel.updateScheduledWorkDate(picked)
                                        if (!wasAlreadyConfirmed) {
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

    if (leadHeatDialogOpen && customer != null) {
        val currentHeat = LeadHeat.fromNameOrNull(customer?.leadHeat)
        AlertDialog(
            onDismissRequest = { leadHeatDialogOpen = false },
            title = { Text("리드 온도", color = TossTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "이 고객의 전환 가능성",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextSecondary
                    )
                    LeadHeat.values().forEach { heat ->
                        val selected = currentHeat == heat
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateLeadHeat(heat)
                                    leadHeatDialogOpen = false
                                },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = if (selected) com.detailline.callfollowcrm.presentation.theme.TossBlueSoft else Color.White,
                            border = BorderStroke(1.dp, if (selected) TossBlue else TossDivider)
                        ) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(heat.emoji, fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    heat.label,
                                    color = if (selected) TossBlue else TossTextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    if (currentHeat != null) {
                        TextButton(onClick = {
                            viewModel.updateLeadHeat(null)
                            leadHeatDialogOpen = false
                        }) {
                            Text("분류 해제", color = TossTextSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { leadHeatDialogOpen = false }) {
                    Text("닫기", color = TossTextSecondary)
                }
            },
            containerColor = Color.White
        )
    }

    if (statusDialogOpen && customer != null) {
        val current = customer?.status.orEmpty()
        AlertDialog(
            onDismissRequest = { statusDialogOpen = false },
            title = { Text("상태 변경", color = TossTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                StatusDialogContent(
                    currentLabel = current,
                    onPick = { picked ->
                        // 예약 확정으로 새로 전환되는 순간에만 축하 (이미 예약 확정인 채로 재선택 시엔 X)
                        val justBookedConfirmed = picked == CustomerStatus.RESERVATION_CONFIRMED &&
                            current != CustomerStatus.RESERVATION_CONFIRMED.label
                        viewModel.updateStatus(picked)
                        statusDialogOpen = false
                        if (justBookedConfirmed) {
                            celebrationVisible = true
                            vibrateCelebration(context)
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { statusDialogOpen = false }) {
                    Text("닫기", color = TossTextSecondary)
                }
            },
            containerColor = Color.White
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

/** 영업 진행 흐름의 6단계 순서. 보류/이탈은 본 흐름 바깥(예외)으로 분리. */
private val PIPELINE_STAGES = listOf(
    CustomerStatus.NEW_INQUIRY,
    CustomerStatus.ESTIMATE_PENDING,
    CustomerStatus.ESTIMATE_SENT,
    CustomerStatus.RESERVATION_PENDING,
    CustomerStatus.RESERVATION_CONFIRMED,
    CustomerStatus.WORK_DONE
)
private val OFF_FLOW_STATUSES = listOf(CustomerStatus.ON_HOLD, CustomerStatus.LOST)

/** 현재 상태의 다음 단계. 본 흐름 안에서만 의미 있음. 시공 완료/보류/이탈에선 null. */
private fun nextStageOf(label: String): CustomerStatus? {
    val idx = PIPELINE_STAGES.indexOfFirst { it.label == label }
    if (idx < 0 || idx >= PIPELINE_STAGES.lastIndex) return null
    return PIPELINE_STAGES[idx + 1]
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusDialogContent(
    currentLabel: String,
    onPick: (CustomerStatus) -> Unit
) {
    val currentIdx = PIPELINE_STAGES.indexOfFirst { it.label == currentLabel }
    val next = nextStageOf(currentLabel)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 현재 상태 표시 (색 톤 일관)
        Column {
            Text(
                "현재 상태",
                style = MaterialTheme.typography.labelSmall,
                color = TossTextTertiary
            )
            Spacer(Modifier.height(4.dp))
            CurrentStatusBadge(currentLabel)
        }

        // 2. 진행 단계 시각화 (6단계 dot + line)
        if (currentIdx >= 0) {
            PipelineProgress(currentIdx)
        }

        // 3. 다음 단계 빠른 버튼
        if (next != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(TossBlue)
                    .clickable { onPick(next) }
                    .padding(vertical = 14.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    "다음 단계로 → ${next.label}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 4. 직접 선택 (진행 단계)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "직접 선택",
                style = MaterialTheme.typography.labelMedium,
                color = TossTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PIPELINE_STAGES.forEachIndexed { idx, s ->
                    StatusChoiceChip(
                        status = s,
                        state = when {
                            s.label == currentLabel -> ChoiceState.CURRENT
                            currentIdx >= 0 && idx < currentIdx -> ChoiceState.PAST
                            else -> ChoiceState.FUTURE
                        },
                        onClick = { onPick(s) }
                    )
                }
            }
        }

        // 5. 기타 (보류 / 이탈)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "기타",
                style = MaterialTheme.typography.labelMedium,
                color = TossTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OFF_FLOW_STATUSES.forEach { s ->
                    StatusChoiceChip(
                        status = s,
                        state = if (s.label == currentLabel) ChoiceState.CURRENT else ChoiceState.FUTURE,
                        onClick = { onPick(s) }
                    )
                }
            }
        }
    }
}

/** 다이얼로그 상단의 큰 현재 상태 표시. 색은 statusColors 와 일관. */
@Composable
private fun CurrentStatusBadge(label: String) {
    val (fg, bg) = statusColors(label)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 6단계 가로 stepper. 현재까지 채워진 progress bar + 단계별 dot + 라벨. */
@Composable
private fun PipelineProgress(currentIdx: Int) {
    Column {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            PIPELINE_STAGES.forEachIndexed { idx, _ ->
                val passed = idx <= currentIdx
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(if (idx == currentIdx) 14.dp else 10.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            if (passed) TossBlue
                            else com.detailline.callfollowcrm.presentation.theme.TossDivider
                        )
                )
                if (idx < PIPELINE_STAGES.lastIndex) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                if (idx < currentIdx) TossBlue
                                else com.detailline.callfollowcrm.presentation.theme.TossDivider
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
            PIPELINE_STAGES.forEachIndexed { idx, s ->
                val passed = idx <= currentIdx
                Text(
                    s.label.replace(" ", "\n"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (idx == currentIdx) TossBlue
                        else if (passed) TossTextSecondary
                        else TossTextTertiary,
                    fontWeight = if (idx == currentIdx) FontWeight.Bold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

private enum class ChoiceState { PAST, CURRENT, FUTURE }

/**
 * 다이얼로그 안의 상태 칩.
 *  - CURRENT: 색 채워진 강조 (statusColors 의 fg/bg)
 *  - PAST: ✓ 아이콘 + 회색 톤 (이미 지나간 단계)
 *  - FUTURE: 흰 배경 + 옅은 테두리 (선택 가능)
 */
@Composable
private fun StatusChoiceChip(
    status: CustomerStatus,
    state: ChoiceState,
    onClick: () -> Unit
) {
    val (fg, bg, border) = when (state) {
        ChoiceState.CURRENT -> {
            val (f, b) = statusColors(status.label)
            Triple(f, b, f)
        }
        ChoiceState.PAST -> Triple(
            TossTextSecondary,
            Color(0xFFF1F3F5),
            Color(0xFFE5E8EB)
        )
        ChoiceState.FUTURE -> Triple(
            TossTextPrimary,
            Color.White,
            com.detailline.callfollowcrm.presentation.theme.TossDivider
        )
    }
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = border,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (state == ChoiceState.PAST) {
            Text(
                "✓ ",
                color = fg,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            status.label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (state == ChoiceState.CURRENT) FontWeight.Bold else FontWeight.Medium
        )
    }
}

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
 *  - 체크박스: 받음/안 받음 토글. 받음 = paidAt 에 "지금" 자동 기록 (이후 날짜 칸 탭하면 수정 가능)
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
    onPaidAtChange: (Long) -> Unit
) {
    val initialAmountText = remember(amount) { amount?.toString().orEmpty() }
    var amountText by remember(amount) { mutableStateOf(initialAmountText) }
    val paid = paidAt != null
    var datePickerOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1행 — 라벨 + 받음 체크
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = TossTextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.Checkbox(
                checked = paid,
                onCheckedChange = { onPaidChange(it) },
                colors = androidx.compose.material3.CheckboxDefaults.colors(
                    checkedColor = com.detailline.callfollowcrm.presentation.theme.TossSuccess,
                    uncheckedColor = TossDivider
                )
            )
            Text(
                if (paid) "받음" else "안 받음",
                style = MaterialTheme.typography.labelMedium,
                color = if (paid) com.detailline.callfollowcrm.presentation.theme.TossSuccess else TossTextTertiary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(6.dp))

        // 2행 — 금액 입력 (천단위 콤마 visualtransform)
        OutlinedTextField(
            value = amountText,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(10)
                amountText = digits
                onAmountChange(digits.toLongOrNull())
            },
            placeholder = { Text("금액 (원)", color = TossTextTertiary, fontSize = 13.sp) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            visualTransformation = ThousandsSeparatorTransformation,
            modifier = Modifier.fillMaxWidth(),
            colors = tossFieldColors(),
            textStyle = TextStyle(fontSize = 15.sp, color = TossTextPrimary, fontWeight = FontWeight.Medium)
        )

        Spacer(Modifier.height(6.dp))

        // 3행 — 단위 칩 가산 + 지움
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AmountChip("+1만") { addToAmount(10_000L, amountText) { newText -> amountText = newText; onAmountChange(newText.toLongOrNull()) } }
            AmountChip("+5만") { addToAmount(50_000L, amountText) { newText -> amountText = newText; onAmountChange(newText.toLongOrNull()) } }
            AmountChip("+10만") { addToAmount(100_000L, amountText) { newText -> amountText = newText; onAmountChange(newText.toLongOrNull()) } }
            AmountChip("+100만") { addToAmount(1_000_000L, amountText) { newText -> amountText = newText; onAmountChange(newText.toLongOrNull()) } }
            Spacer(Modifier.weight(1f))
            AmountChip("지움", danger = true) {
                amountText = ""
                onAmountChange(null)
            }
        }

        // 4행 — 받은 날짜 (paid 상태에서만). 탭 → DatePicker
        if (paidAt != null) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { datePickerOpen = true }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    "📅 받은 날짜: ${DateTimeUtils.formatKoreanDate(paidAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = com.detailline.callfollowcrm.presentation.theme.TossSuccess,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "(탭해서 수정)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TossTextTertiary
                )
            }
        } else if (amount != null && amount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "금액만 정해두고 아직 안 받음",
                style = MaterialTheme.typography.labelSmall,
                color = TossTextTertiary
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

@Composable
private fun LeadHeatRow(current: LeadHeat?, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            "리드 온도",
            style = MaterialTheme.typography.labelMedium,
            color = TossTextSecondary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        val (label, fg, bg) = when (current) {
            LeadHeat.COLD -> Triple("${LeadHeat.COLD.emoji} ${LeadHeat.COLD.label}", TossBlue, com.detailline.callfollowcrm.presentation.theme.TossBlueSoft)
            LeadHeat.WARM -> Triple("${LeadHeat.WARM.emoji} ${LeadHeat.WARM.label}", Color(0xFFD03A1A), Color(0xFFFFE9E1))
            null -> Triple("미분류", TossTextTertiary, TossGrayBg)
        }
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier
                .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(label, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Text("▾", color = fg, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 작은 상태 알약. 현재 상태 라벨 + ▾ 화살표. 탭하면 변경 다이얼로그.
 * 색은 상태별 의미에 맞게 4가지 톤으로 그룹화 (영업 흐름 시각화).
 */
@Composable
private fun StatusPill(label: String, onClick: () -> Unit) {
    val (fg, bg) = statusColors(label)
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
        Spacer(Modifier.width(4.dp))
        Text("▾", color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

/** 상태 라벨 → (글자색, 배경색). 톤만 4가지로 묶어 직관성 ↑. */
private fun statusColors(label: String): Pair<Color, Color> {
    val blue = TossBlue to com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
    val green = com.detailline.callfollowcrm.presentation.theme.TossSuccess to Color(0xFFE6F7EC)
    val gray = com.detailline.callfollowcrm.presentation.theme.TossTextSecondary to Color(0xFFF1F3F5)
    val red = com.detailline.callfollowcrm.presentation.theme.TossError to Color(0xFFFEEBEC)
    return when (label) {
        "신규 문의", "견적 대기", "견적 발송" -> blue
        "예약 대기", "예약 확정" -> green
        "시공 완료" -> gray
        "보류", "이탈" -> red
        else -> blue
    }
}

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
