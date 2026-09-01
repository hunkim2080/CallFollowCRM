package com.detailline.callfollowcrm.presentation.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Computer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.presentation.component.Mascot
import com.detailline.callfollowcrm.presentation.component.SectionLabel
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.component.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 설정 화면 — 2026-05-24 사장님 다이어트.
 *
 * 구성:
 *   1. AI 서버 — 연결 상태 + (추후) 사용량/비용
 *   2. 통화 종료 후 동작 — AfterCallBehavior + 후속 알림 빠른 액션 + 처음 연락 자동 응답 (통합)
 *   3. 주고받은 문자 보기 (READ_SMS)
 *   4. 문자 템플릿 / 가격표 — 진입점 2개
 *   5. 사장님 톤 학습 — 보낸 메시지 N건이 AI 학습용으로 사용 중
 *   6. 앱 정보 — footer
 *
 * 제거 (사장님 결정 2026-05-24): 에이닷 폴더 연동 / 자동 import 정리 / 새 고객 기본 상태 /
 *   AI 요약 placeholder / 데이터 백업 placeholder / 문자 발송 정책 (설명만).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenPricingItems: () -> Unit,
    onOpenBusinessInfo: () -> Unit = {},
    onOpenNotebook: () -> Unit = {},
    onOpenTeam: () -> Unit = {},
    onOpenExpo: () -> Unit = {},
    onOpenCollabSites: () -> Unit = {},
    onOpenCollabRecord: () -> Unit = {},
    onOpenReport: () -> Unit = {},
    onOpenTradeSelect: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenPrinciples: () -> Unit = {},
    onOpenSpamList: () -> Unit = {},
    onOpenPersonalList: () -> Unit = {},
    onOpenSoundSettings: () -> Unit = {},
    onShowIntro: () -> Unit = {},
    /** 진입 시 바로 열 서브페이지 ("autosms" = 자동 문자, 부재중 응답 펼침). null = 일반 더보기. */
    initialSubPage: String? = null
) {
    val state by viewModel.state.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val serverAlive by viewModel.serverAlive.collectAsState()
    // 시작 체크(SetupCheckCard) 확장 — 마법사에서 "나중에" 누른 가격표 항목을 홈에서 재권유하기 위한 실시간 개수.
    val pricingItemsForSetup by container.pricingItemRepository.observeActive()
        .collectAsState(initial = emptyList())
    val toneSampleCount by viewModel.ownerToneSampleCount.collectAsState()
    val context = LocalContext.current

    val sendSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setAutoFirstReplyEnabled(true)
            Toast.makeText(context, "자동 응답 켜졌어요. 첫 통화 후 10초 카운트다운 뒤 발송돼요.", Toast.LENGTH_LONG).show()
        } else {
            viewModel.setAutoFirstReplyEnabled(false)
            Toast.makeText(context, "문자 보내기 권한을 허용해야 자동 응답을 켤 수 있어요", Toast.LENGTH_SHORT).show()
        }
    }

    // 현장 도착(지오펜싱) 위치 권한.
    val settingsScope = rememberCoroutineScope()
    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fine) {
            Toast.makeText(context, "위치 권한 OK. 백그라운드(앱 꺼져도)는 설정 → 권한 → 위치 → '항상 허용' 으로 켜주세요.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "위치 권한이 거부되어 도착 감지를 켤 수 없어요", Toast.LENGTH_SHORT).show()
        }
        settingsScope.launch {
            com.detailline.callfollowcrm.service.GeofenceManager.refresh(context)
        }
    }

    // 프로토 더보기 = 깔끔한 메뉴만. 진단/기능 카드는 메뉴 탭 시 서브페이지로(자체 라우트 없이 내부 전환).
    //   2026-06-02 사장님 결정("프로토처럼 완전 깔끔하게").
    var subPage by remember { mutableStateOf(initialSubPage) }
    // 문제 신고 / 진단 보내기 다이얼로그 (2026-07-22 사장님) — 크래시 아닌 '이상 동작' 수동 신고.
    var showDiagnostics by remember { mutableStateOf(false) }
    val subTitle = when (subPage) {
        "tone" -> "내 말투 학습"
        "autosms" -> "자동 문자"
        "nav" -> "기본 네비 앱"
        "smsapp" -> "기본 문자 앱"
        "noti" -> "고객 사진(문자) 받기"
        "server" -> "AI 서버 상태"
        "mirror" -> "구글 캘린더 연동"
        "web" -> "시공막내 웹 (PC)"
        else -> "더보기"
    }
    BackHandler(enabled = subPage != null) { subPage = null }

    if (showDiagnostics) {
        DiagnosticsDialog(
            onDismiss = { showDiagnostics = false },
            onSend = { note, shotUri ->
                showDiagnostics = false
                Toast.makeText(context, "진단을 보내는 중…", Toast.LENGTH_SHORT).show()
                settingsScope.launch {
                    val ok = com.detailline.callfollowcrm.util.DiagnosticsReporter
                        .sendToServer(context, container.preferences, note, shotUri)
                    if (ok) {
                        Toast.makeText(context, "진단을 보냈어요. 감사합니다! 🙏", Toast.LENGTH_LONG).show()
                    } else {
                        // 서버 전송 실패 → 공유 시트로 폴백(리포트 유실 방지)
                        Toast.makeText(context, "바로 전송이 안 돼 공유로 열었어요", Toast.LENGTH_LONG).show()
                        com.detailline.callfollowcrm.util.DiagnosticsReporter.share(
                            context,
                            com.detailline.callfollowcrm.util.DiagnosticsReporter.buildReport(container.preferences, note),
                            shotUri
                        )
                    }
                }
            }
        )
    }

    // ─────────── 내 데이터 내보내기/가져오기 배선 (데이터 안전 1단계, 2026-08-10 사장님) ───────────
    val backupBusy by viewModel.backupBusy.collectAsState()
    val lastBackupAt by viewModel.lastBackupAt.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()
    val shareRequest by viewModel.shareRequest.collectAsState()
    val restartNeeded by viewModel.restartNeeded.collectAsState()
    var showImportConfirm by remember { mutableStateOf(false) }
    var showServerRestoreConfirm by remember { mutableStateOf(false) }

    val backupImportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importData(it) } }

    LaunchedEffect(backupMessage) {
        backupMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeBackupMessage()
        }
    }
    LaunchedEffect(shareRequest) {
        shareRequest?.let { r ->
            runCatching {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(android.content.Intent.EXTRA_STREAM, r.uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, r.fileName)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    android.content.Intent.createChooser(send, "백업 파일 저장·보내기")
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
                Toast.makeText(context, "백업을 만들었어요 · 저장할 곳을 골라주세요", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "공유 앱을 열지 못했어요", Toast.LENGTH_SHORT).show()
            }
            viewModel.consumeShareRequest()
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("백업에서 가져오기", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "백업 파일 속 데이터를 지금 데이터에 합쳐요. 같은 고객은 백업 값으로 바뀌고, 지금 데이터가 지워지진 않아요.\n\n새 폰으로 옮길 때 쓰는 기능이에요.",
                    fontSize = 13.5.sp, color = TossTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    runCatching {
                        backupImportPicker.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*"))
                    }.onFailure { Toast.makeText(context, "파일 선택을 열지 못했어요", Toast.LENGTH_SHORT).show() }
                }) { Text("백업 고르기", color = TossBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showImportConfirm = false }) { Text("취소", color = TossTextTertiary) } }
        )
    }
    if (showServerRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showServerRestoreConfirm = false },
            title = { Text("서버에서 복원", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "서버에 저장된 최신 백업을 지금 데이터에 합쳐요. 같은 고객은 백업 값으로 바뀌고, 지금 데이터가 지워지진 않아요.\n\n새 폰·재설치 후 되살릴 때 쓰는 기능이에요.",
                    fontSize = 13.5.sp, color = TossTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showServerRestoreConfirm = false
                    viewModel.serverRestore()
                }) { Text("서버에서 복원", color = TossBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showServerRestoreConfirm = false }) { Text("취소", color = TossTextTertiary) } }
        )
    }
    if (restartNeeded) {
        AlertDialog(
            onDismissRequest = { viewModel.consumeRestartNeeded() },
            title = { Text("복원 완료!", fontWeight = FontWeight.Bold) },
            text = { Text("앱을 완전히 껐다 다시 켜면 되살린 데이터가 모두 보여요.", fontSize = 13.5.sp, color = TossTextSecondary) },
            confirmButton = { TextButton(onClick = { viewModel.consumeRestartNeeded() }) { Text("확인", color = TossBlue, fontWeight = FontWeight.Bold) } }
        )
    }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars.add(WindowInsets(top = 10.dp)),
                title = {
                    Text(
                        subTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (subPage != null) subPage = null else onBack() }) {
                        Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        // 메뉴와 서브탭이 스크롤 상태를 공유하면, 메뉴를 중간까지 내려 서브탭을 열 때 그 위치가 남아
        //   서브탭이 '중간부터' 보이는 버그(자동문자 등 모든 서브탭). → 메뉴/서브 스크롤 분리 + 서브 진입 시 맨 위로. (2026-08-02 사장님)
        val menuScroll = rememberScrollState()
        val subScroll = rememberScrollState()
        LaunchedEffect(subPage) { if (subPage != null) subScroll.scrollTo(0) }
        // 기본 문자 앱이면 '채팅+ 끄기' 안내는 불필요(기본앱 되면 채팅+가 꺼짐) → 그 항목 숨김. (2026-08-02 사장님)
        val isDefaultSmsApp = remember { com.detailline.callfollowcrm.util.DefaultSmsAppHelper.isCurrentDefault(context) }
        Column(
            Modifier
                .padding(top = inner.calculateTopPadding())
                .fillMaxSize()
                .background(TossGrayBg)
                .imePadding()
                .verticalScroll(if (subPage == null) menuScroll else subScroll)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (subPage == null) {
                // ══════════════ 프로토 s-more 메뉴 (1:1) ══════════════
                // 막내 비서 카드 (agent-card) — 레벨·말투%·상담/시공 = 실제 카운트.
                //   카드 탭 → '내 말투 학습'으로 이동(막내 비서 = 말투 학습 흐름). (2026-06-17 사장님)
                val agentCard by viewModel.agentCard.collectAsState()
                AgentMiniCard(card = agentCard, onClick = { subPage = "tone" })

                // 프로토 setup-check — 시작 체크 (실제 권한 상태). 다 되면 한 줄로 접힘.
                //   + 마법사에서 "나중에" 누른 연결 항목(녹음·가격표·답장)도 실시간 감지해 재권유. (2026-07-28)
                SetupCheckCard(
                    preferences = container.preferences,
                    templateCount = templates.size,
                    pricingCount = pricingItemsForSetup.size,
                    onOpenTemplates = onOpenTemplates,
                    onOpenPricingItems = onOpenPricingItems
                )

                // ⭐ 내 데이터 지키기 (데이터 안전 1단계, 2026-08-10) — 재설치·기기변경 시 통째 소실 방어.
                DataBackupSection(
                    lastBackupAt = lastBackupAt,
                    busy = backupBusy,
                    onExport = { viewModel.exportData() },
                    onImport = { showImportConfirm = true },
                    onServerBackup = { viewModel.serverBackup() },
                    onServerRestore = { showServerRestoreConfirm = true },
                    onRestoreCategories = { viewModel.serverRestoreCategoriesOnly() }
                )

                // ⭐ 2026-08-02 사장님 "더보기 뒤죽박죽 정리" — 항목/기능 그대로, 성격 맞는 그룹으로 재배치.
                //   (프로토 5그룹에 앱 기능 13개가 아무 데나 섞였던 것 → 성격별 6그룹으로. 새 그룹=기록·분석 / 알림·번호 관리.)
                // 인원 관리·수첩 제거(2026-08-31 사장님 "더보기 정리"). 관련 화면/nav 코드는 추후 청소.
                SettingsGroup("협업·박람회") {
                    LockRow(Icons.Filled.Group, Color(0xFFF1ECFE), Color(0xFF7C5CFC), "협업 현장",
                        "다른 사장님과 현장 하나만 같이 보기", tier = "비즈니스", onClick = onOpenCollabSites)
                    // 박람회 — 별세계(완전 분리) 진입. 카톡 스타일 전용 창구. (2026-07-21 사장님)
                    LockRow(Icons.Filled.Storefront, Color(0xFFFFF3C4), Color(0xFFC9A200), "박람회",
                        "박람회 팀 — 상담·계약·분배를 카톡처럼", onClick = onOpenExpo)
                }
                // 새 그룹 — 매출·협업을 '기록/세금' 성격으로 묶음(전엔 리포트=장사분석, 협업기록=사람그룹에 흩어짐).
                SettingsGroup("기록·분석") {
                    LockRow(Icons.Filled.BarChart, TossBlueSoft, TossBlue, "상세 리포트",
                        "매출·전환율·추천 채택률 분석", tier = "비즈니스", onClick = onOpenReport)
                    LockRow(Icons.Filled.Payments, Color(0xFFE7F8F0), Color(0xFF16C172), "협업 기록",
                        "협업 사장님별 · 월별로 얼마나 함께했나 (기록·세금용)", tier = "비즈니스", onClick = onOpenCollabRecord)
                }
                SettingsGroup("내 답장 재료") {
                    LockRow(Icons.AutoMirrored.Filled.Chat, TossBlueSoft, TossBlue, "문자 템플릿",
                        "자주 쓰는 문구 관리", onClick = onOpenTemplates)
                    LockRow(Icons.Filled.Payments, TossBlueSoft, TossBlue, "가격표",
                        "견적 작성에 쓰이는 항목", onClick = onOpenPricingItems)
                    LockRow(Icons.Filled.Description, TossBlueSoft, TossBlue, "견적서·사업자 정보",
                        "상호·대표·사업자번호·직인 · 견적서에 자동 표시", onClick = onOpenBusinessInfo)
                    LockRow(Icons.AutoMirrored.Filled.Send, TossBlueSoft, TossBlue, "자동 문자",
                        "부재중 응답 · 시공 D-1 · 도착 안내 · 정기 문자") { subPage = "autosms" }
                    LockRow(Icons.Filled.AutoAwesome, Color(0xFFF1ECFF), Color(0xFF7C5CFC), "내 말투 학습",
                        "나처럼 답하는 AI", tier = "프로") { subPage = "tone" }
                }
                // 새 그룹 — 흩어져 있던 알림/번호 설정 모음(알림소리=답장재료에서, 사진받기=도움말에서, 스팸·사생활=앱설정에서 이동).
                SettingsGroup("알림·번호 관리") {
                    LockRow(Icons.Filled.Notifications, TossBlueSoft, TossBlue, "알림 소리",
                        "알림 종류별 소리 고르기 · 미리듣기", onClick = onOpenSoundSettings)
                    // 기본 문자 앱이면 채팅+가 꺼져 있어 이 안내 불필요 → 숨김. 기본앱 아닌 사람만 노출(사진 놓침 방어). (2026-08-02 사장님)
                    if (!isDefaultSmsApp) {
                        LockRow(Icons.AutoMirrored.Filled.Chat, TossGrayBg, TossTextTertiary, "고객 사진(문자) 받기",
                            "채팅+ 꺼서 고객 사진 놓치지 않기") { subPage = "noti" }
                    }
                    LockRow(Icons.Filled.Block, Color(0xFFFFF1F3), Color(0xFFF0436A), "스팸 차단 번호",
                        "상담함에서 스팸 등록한 번호 · 잘못 넣었으면 여기서 풀기", onClick = onOpenSpamList)
                    LockRow(Icons.Filled.Person, Color(0xFFF1ECFE), Color(0xFF7C5CFC), "사생활 번호",
                        "내 개인 연락처 · 시공막내가 안 잡음 · 풀려면 여기서", onClick = onOpenPersonalList)
                }
                SettingsGroup("앱 설정") {
                    val navLabel = com.detailline.callfollowcrm.util.NavApp.values()
                        .find { it.key == state.defaultNavAppKey }?.label ?: "카카오내비"
                    LockRow(Icons.Filled.Navigation, TossGrayBg, TossTextTertiary, "기본 네비 앱",
                        navLabel) { subPage = "nav" }
                    LockRow(Icons.Filled.DateRange, TossBlueSoft, TossBlue, "구글 캘린더 연동",
                        "시공·A/S 일정을 구글 캘린더에 · 위젯·공유·폰교체 백업") { subPage = "mirror" }
                    LockRow(Icons.Filled.Computer, TossBlueSoft, TossBlue, "시공막내 웹 (PC 사진)",
                        "PC 브라우저서 시공 사진 보기·블로그용 다운 · QR 로그인 · 보기 전용") { subPage = "web" }
                }
                SettingsGroup("도움말") {
                    // 앱 소개 다시 보기 제거(2026-08-31 사장님 "더보기 정리").
                    // 문제 신고 / 진단 보내기 (2026-07-22 사장님) — 앱이 안 죽는 '이상 동작'을 직접 신고. Crashlytics(자동) 의 짝.
                    LockRow(Icons.Filled.BugReport, Color(0xFFFFF1F3), Color(0xFFF0436A), "문제 신고 / 진단 보내기",
                        "문자가 깨지는 등 이상하면 눌러서 알려주세요") { showDiagnostics = true }
                }

                // '비즈니스' 배지를 유료로 오해해 핵심 기능을 안 누르던 것 → 베타 무료 안내. 리스트 중간이 아니라 맨 아래 푸터로. (2026-08-02 정리)
                Text(
                    "'비즈니스'·'프로' 표시가 있어도 베타 기간엔 모두 무료로 열려 있어요.",
                    fontSize = 11.5.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                )
                AppFooter()
                Spacer(Modifier.height(16.dp))
            } else when (subPage) {
                // ══════════════ 내 말투 학습 (프로) — 프로토 renderTone 1:1 ══════════════
                //   2026-06-03 사장님 결정: 프로토 모양으로 통일 + Tone RAG 업로드는 살려서 녹임.
                //   채택률/AutoLearning 카드는 프로토 기준 "상세 리포트" 소관 → 여기서 제거.
                "tone" -> {
                    val agentCard by viewModel.agentCard.collectAsState()
                    val toneProfile by viewModel.toneProfile.collectAsState()
                    LaunchedEffect(Unit) { viewModel.loadToneProfile() }
                    // "지금 동기화" 결과 토스트 — 조용한 실패 제거(2026-06-30).
                    val toneSyncMsg by viewModel.toneSyncMessage.collectAsState()
                    val toneSyncCtx = LocalContext.current
                    LaunchedEffect(toneSyncMsg) {
                        toneSyncMsg?.let {
                            android.widget.Toast.makeText(toneSyncCtx, it, android.widget.Toast.LENGTH_LONG).show()
                            viewModel.consumeToneSyncMessage()
                        }
                    }
                    val toneRagConsented by viewModel.toneRagConsented.collectAsState()
                    val toneRagUploadedCount by viewModel.toneRagUploadedCount.collectAsState()
                    val toneRagAvailable by viewModel.toneRagAvailable.collectAsState()
                    val toneRagUploading by viewModel.toneRagUploading.collectAsState()
                    val toneRagProgress by viewModel.toneRagProgress.collectAsState()
                    val toneSyncedUpTo by viewModel.toneSyncedUpTo.collectAsState()
                    ToneLearnProtoSection(
                        container = container,
                        profile = toneProfile,
                        tonePct = toneProfile?.learnRatePct ?: agentCard.tonePct,
                        ragUploadedCount = toneRagUploadedCount,
                        ragAvailable = toneRagAvailable,
                        ragSyncedUpTo = toneSyncedUpTo,
                        ragConsented = toneRagConsented,
                        ragUploading = toneRagUploading,
                        ragProgress = toneRagProgress,
                        onConsentAndUpload = { viewModel.uploadOwnerTone(consentNow = true) },
                        onUpload = { viewModel.uploadOwnerTone(consentNow = false) }
                    )
                    Spacer(Modifier.height(14.dp))
                    // 막내가 알아낸 원칙 (판단 기준 = 3번째 학습 층). (2026-06-17)
                    LockRow(
                        Icons.Filled.AutoAwesome, Color(0xFFF1ECFF), Color(0xFF7C5CFC),
                        "막내가 알아낸 원칙",
                        "막내가 사장님 답변에서 찾은 판단 기준 · 수정/삭제",
                        onClick = onOpenPrinciples
                    )
                    Spacer(Modifier.height(16.dp))
                }
                // ══════════════ 자동 문자 (부재중·D-1·도착·정기) ══════════════
                "autosms" -> {
                    AutoSmsSection(
                        autoReplyOn = state.autoFirstReplyEnabled,
                        onAutoReplyToggle = { wantOn ->
                            if (wantOn) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.SEND_SMS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) viewModel.setAutoFirstReplyEnabled(true)
                                else sendSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            } else {
                                viewModel.setAutoFirstReplyEnabled(false)
                            }
                        },
                        incomingNotifyOn = state.incomingSmsNotifyEnabled,
                        onIncomingNotifyToggle = viewModel::setIncomingSmsNotifyEnabled,
                        onOpenRecurring = onOpenRecurring,
                        expandMissed = initialSubPage == "autosms",
                        onArrivalToggle = { on ->
                            if (on) {
                                val fineGranted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!fineGranted) locationPermLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                                ) else settingsScope.launch {
                                    com.detailline.callfollowcrm.service.GeofenceManager.refresh(context)
                                }
                            } else {
                                settingsScope.launch {
                                    com.detailline.callfollowcrm.service.GeofenceManager.refresh(context)
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    // 통화 후 문자 보내기 — 새 번호와 통화 끝나면 템플릿 3개 중 골라 보내기. (2026-07-12 사장님)
                    PostCallTemplateCard(prefs = container.preferences)
                    Spacer(Modifier.height(16.dp))
                }
                // ══════════════ 기본 네비 앱 ══════════════
                "nav" -> {
                    NavAppPreferenceCard(
                        selectedKey = state.defaultNavAppKey,
                        onSelect = viewModel::setDefaultNavApp
                    )
                    Spacer(Modifier.height(16.dp))
                }
                // ══════════════ 기본 문자 앱 ══════════════
                "smsapp" -> {
                    DefaultSmsAppCard(preferences = container.preferences)
                    Spacer(Modifier.height(16.dp))
                }
                // ══════════════ 알림 미리보기/진단 ══════════════
                "noti" -> {
                    NotificationDiagnosticCard()
                    Spacer(Modifier.height(16.dp))
                }
                // ══════════════ 본폰에서 일정 보기 (미러 링크) ══════════════
                "mirror" -> {
                    GoogleCalendarSection(container = container)
                    Spacer(Modifier.height(16.dp))
                }
                // ══════════════ 시공막내 웹 (PC 사진 캘린더) ══════════════
                "web" -> {
                    WebViewerSection(container = container)
                    Spacer(Modifier.height(16.dp))
                }
                // ══════════════ AI 서버 상태 + 토큰 ══════════════
                "server" -> {
                    ServerStatusCard(alive = serverAlive)
                    val usageStatsResult by viewModel.usageStats.collectAsState()
                    val usageLoading by viewModel.usageLoading.collectAsState()
                    UsageStatsCard(
                        result = usageStatsResult,
                        loading = usageLoading,
                        onRefresh = { period -> viewModel.loadUsageStats(period) }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * 토큰 사용량 카드 — 사장님이 LLM 비용 실측 (2026-05-27).
 *   서버 §12 endpoint 결과 표시. period 선택 (오늘/이번달/전체).
 *   서버 미구현 / 네트워크 실패 = "서버 모니터링 미구현" 안내.
 *
 * Period chip = chip row (전체/미확인 식). selected = 강조 색.
 */
@Composable
private fun UsageStatsCard(
    result: Result<com.detailline.callfollowcrm.ai.UsageStatsRepository.UsageStats>?,
    loading: Boolean,
    onRefresh: (com.detailline.callfollowcrm.ai.UsageStatsRepository.Period) -> Unit
) {
    var selectedPeriod by remember {
        mutableStateOf(com.detailline.callfollowcrm.ai.UsageStatsRepository.Period.TODAY)
    }
    val periodLabel = when (selectedPeriod) {
        com.detailline.callfollowcrm.ai.UsageStatsRepository.Period.TODAY -> "오늘"
        com.detailline.callfollowcrm.ai.UsageStatsRepository.Period.MONTH -> "이번 달"
        com.detailline.callfollowcrm.ai.UsageStatsRepository.Period.ALL -> "전체"
    }

    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📊", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "토큰 사용량 — $periodLabel",
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (loading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = TossBlue
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // Period chip — 토스 식 3개 선택지.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    com.detailline.callfollowcrm.ai.UsageStatsRepository.Period.TODAY to "오늘",
                    com.detailline.callfollowcrm.ai.UsageStatsRepository.Period.MONTH to "이번 달",
                    com.detailline.callfollowcrm.ai.UsageStatsRepository.Period.ALL to "전체"
                ).forEach { (p, label) ->
                    val selected = p == selectedPeriod
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) TossBlue else Color(0xFFEEF1F4))
                            .clickable {
                                selectedPeriod = p
                                onRefresh(p)
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.White else TossTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                result == null && loading -> {
                    Text(
                        "사용량 불러오는 중...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TossTextTertiary
                    )
                }
                result == null -> {
                    Text(
                        "사용량 불러오기 전",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TossTextTertiary
                    )
                }
                result.isFailure -> {
                    Text(
                        "지금은 사용량 정보를 불러올 수 없어요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TossError,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "잠시 후 다시 확인해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextTertiary
                    )
                }
                else -> {
                    val stats = result.getOrThrow()
                    // 큰 숫자 — 비용 강조.
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "₩${formatThousands(stats.totalCostKrw)}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TossBlue,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${stats.totalCalls}회 호출",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossTextTertiary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "토큰: ${formatThousands(stats.totalTokens)} " +
                            "(입력 ${formatThousands(stats.totalInputTokens)} · " +
                            "출력 ${formatThousands(stats.totalOutputTokens)} · " +
                            "캐시 ${formatThousands(stats.totalCacheReadTokens + stats.totalCacheCreateTokens)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextSecondary
                    )

                    if (stats.byEndpoint.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "endpoint 별 (비용 순)",
                            style = MaterialTheme.typography.labelMedium,
                            color = TossTextTertiary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        stats.byEndpoint.forEach { ep ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    ep.endpoint.removePrefix("/api/"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TossTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${ep.calls}회 · ₩${formatThousands(ep.costKrw)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TossTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatThousands(n: Long): String =
    if (n < 1000) n.toString()
    else "%,d".format(n)

private fun formatThousands(n: Int): String = formatThousands(n.toLong())

/**
 * 2026-05-29 Phase A 2단계 Day 5 — 시공막내 를 기본 메시지 앱으로 전환하는 카드.
 *
 * **활성화됨** (Day 1~4 의 자격 인프라 + klinker hook 다 박힘).
 * 토글 ON → RoleManager 다이얼로그 → 사장님 동의 → default. OFF → 시스템 default-apps Settings.
 *
 * 사장님 카피 변경 (Day 5):
 *   메인: "📱 시공막내를 기본 메시지 앱으로 사용하기"
 *   설명 (default 일 때): "✅ 시공막내 가 SMS/MMS 를 받고 있어요. 갤메시지 알림은 시스템 설정에서 끄세요."
 *   설명 (default 아닐 때): "SMS/MMS 수신을 시공막내 에서 관리합니다. 토글 켜면 시스템이 동의를 요청합니다."
 *   수동 입력 expander: "🔧 MMS 서버 수동 입력 (선택)" — 자동 추출 실패 시 안전망.
 */
/**
 * 통화 후 문자 보내기 — 새 번호와 통화가 끝나면 "문자 보낼까요?" 알림 + 템플릿 3개 중 선택. (2026-07-12 사장님)
 *   자동발송 아님(고르면 채팅에 채워짐 → ▶ 확인 발송). prefs 직접 읽기/쓰기(변경 즉시 저장).
 */
@Composable
private fun PostCallTemplateCard(prefs: com.detailline.callfollowcrm.data.preferences.AppPreferences) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(prefs.postCallPickerEnabled) }
    var t1 by remember { mutableStateOf(prefs.postCallTemplate1) }
    var t2 by remember { mutableStateOf(prefs.postCallTemplate2) }
    var t3 by remember { mutableStateOf(prefs.postCallTemplate3) }
    var ph1 by remember { mutableStateOf(prefs.postCallPhotos1) }
    var ph2 by remember { mutableStateOf(prefs.postCallPhotos2) }
    var ph3 by remember { mutableStateOf(prefs.postCallPhotos3) }
    var pickIndex by remember { mutableStateOf(0) }
    // 시스템 사진 선택기(권한 없음, Play 정책) — 여러 장 선택(템플릿당 5장까지). 지속 권한 확보. (2026-07-12 사장님)
    val photoLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { u -> runCatching { context.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            val added = uris.map { it.toString() }
            when (pickIndex) {
                1 -> { val m = (ph1 + added).distinct().take(5); ph1 = m; prefs.postCallPhotos1 = m }
                2 -> { val m = (ph2 + added).distinct().take(5); ph2 = m; prefs.postCallPhotos2 = m }
                3 -> { val m = (ph3 + added).distinct().take(5); ph3 = m; prefs.postCallPhotos3 = m }
            }
        }
    }
    fun addPhotos(idx: Int) {
        pickIndex = idx
        photoLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }
    fun removePhoto(idx: Int, uri: String) {
        when (idx) {
            1 -> { val m = ph1 - uri; ph1 = m; prefs.postCallPhotos1 = m }
            2 -> { val m = ph2 - uri; ph2 = m; prefs.postCallPhotos2 = m }
            3 -> { val m = ph3 - uri; ph3 = m; prefs.postCallPhotos3 = m }
        }
    }
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("📞 통화 후 문자 보내기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text("새 번호와 통화가 끝나면 ‘문자 보낼까요?’ 알림 → 3개 중 하나 누르면 바로 보내져요",
                        fontSize = 12.sp, color = TossTextTertiary, lineHeight = 16.sp)
                }
                androidx.compose.material3.Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it; prefs.postCallPickerEnabled = it }
                )
            }
            if (enabled) {
                Spacer(Modifier.height(12.dp))
                PostCallTemplateField("템플릿 1", t1, { t1 = it; prefs.postCallTemplate1 = it }, ph1, { addPhotos(1) }, { removePhoto(1, it) })
                Spacer(Modifier.height(10.dp))
                PostCallTemplateField("템플릿 2", t2, { t2 = it; prefs.postCallTemplate2 = it }, ph2, { addPhotos(2) }, { removePhoto(2, it) })
                Spacer(Modifier.height(10.dp))
                PostCallTemplateField("템플릿 3", t3, { t3 = it; prefs.postCallTemplate3 = it }, ph3, { addPhotos(3) }, { removePhoto(3, it) })
                Spacer(Modifier.height(12.dp))
                // 통화 없이 카드 미리보기 (사장님 2026-07-12: 매번 전화 걸어 테스트하지 않게). 미리보기는 눌러도 발송 X.
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8F1FE))
                        .clickable {
                            val items = prefs.postCallItems
                            if (items.isEmpty()) {
                                android.widget.Toast.makeText(context, "먼저 템플릿을 채워주세요", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                val shown = com.detailline.callfollowcrm.service.PostCallTemplateOverlay.show(
                                    context, "01000000000", "홍길동", items, preview = true
                                )
                                if (!shown) android.widget.Toast.makeText(context,
                                    "‘다른 앱 위에 표시’ 권한을 켜야 큰 카드로 미리보기가 떠요 (전화 미리보기 토글에서 켜기)",
                                    android.widget.Toast.LENGTH_LONG).show()
                            }
                        }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👀 통화 후 카드 미리보기", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                }
                Spacer(Modifier.height(8.dp))
                Text("비워둔 칸은 알림에 버튼으로 안 나와요. 사진만 넣어도 보낼 수 있어요. (한 템플릿 5장까지)", fontSize = 11.sp, color = TossTextTertiary)
            }
        }
    }
}

@Composable
private fun PostCallTemplateField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    photos: List<String>,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text("예: 안녕하세요 😊 방금 통화드린 OO입니다. 저희 시공 소개 보내드려요!", fontSize = 12.sp, color = TossTextTertiary) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 5,
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard, fontSize = 13.sp),
        shape = RoundedCornerShape(12.dp)
    )
    Spacer(Modifier.height(6.dp))
    // 첨부 사진 썸네일들(각 ✕) + [＋사진] 타일(5장 미만일 때). 가로 스크롤. (2026-07-12 사장님)
    val hScroll = androidx.compose.foundation.rememberScrollState()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(hScroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        photos.forEach { uri ->
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(TossGrayBg)) {
                coil.compose.AsyncImage(
                    model = android.net.Uri.parse(uri),
                    contentDescription = "첨부한 사진",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)).clickable { onRemovePhoto(uri) },
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (photos.size < 5) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(TossGrayBg)
                    .border(1.5.dp, Color(0xFFC8D3E2), RoundedCornerShape(10.dp))
                    .clickable { onAddPhoto() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("＋", fontSize = 18.sp, color = TossBlue, fontWeight = FontWeight.Bold)
                    Text("사진", fontSize = 10.sp, color = TossBlue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DefaultSmsAppCard(
    preferences: com.detailline.callfollowcrm.data.preferences.AppPreferences
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var isDefault by remember {
        mutableStateOf(
            com.detailline.callfollowcrm.util.DefaultSmsAppHelper.isCurrentDefault(context)
        )
    }
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // 결과 코드 무관 — 사용자가 동의했든 거부했든 재조회.
        isDefault = com.detailline.callfollowcrm.util.DefaultSmsAppHelper.isCurrentDefault(context)
    }

    var manualExpanded by remember { mutableStateOf(false) }
    var manualUrl by remember { mutableStateOf(preferences.manualMmscUrl.orEmpty()) }
    var manualProxy by remember { mutableStateOf(preferences.manualMmscProxy.orEmpty()) }
    var manualPort by remember { mutableStateOf(preferences.manualMmscPort.takeIf { it > 0 }?.toString().orEmpty()) }

    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "📱 시공막내를 기본 메시지 앱으로 사용하기",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossTextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isDefault)
                            "✅ 시공막내 가 SMS/MMS 를 받고 있어요. 갤메시지 알림은 시스템 설정에서 끄세요."
                        else
                            "SMS/MMS 수신을 시공막내 에서 관리합니다. 토글 켜면 시스템이 동의를 요청합니다.",
                        fontSize = 12.sp,
                        color = TossTextSecondary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = isDefault,
                    onCheckedChange = onCheck@{ wantOn ->
                        if (activity == null) {
                            Toast.makeText(context, "잠시 후 다시 시도해주세요", Toast.LENGTH_SHORT).show()
                            return@onCheck
                        }
                        if (wantOn && !isDefault) {
                            val intent = com.detailline.callfollowcrm.util.DefaultSmsAppHelper
                                .createRequestIntent(activity)
                            if (intent != null) {
                                runCatching { roleLauncher.launch(intent) }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            "기본 메시지 앱 다이얼로그를 열 수 없어요",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            } else {
                                Toast.makeText(
                                    context,
                                    "이 기기는 기본 SMS 앱 전환을 지원하지 않아요",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else if (!wantOn && isDefault) {
                            // 안드로이드는 직접 해제 API 없음 → 시스템 default-apps 화면으로.
                            val intent = com.detailline.callfollowcrm.util.DefaultSmsAppHelper
                                .createReleaseIntent(activity)
                            runCatching { roleLauncher.launch(intent) }
                        }
                    }
                )
            }

            // 수동 입력 expander — 자동 추출 실패 시 안전망. 14명 중 알뜰/특수 SIM 케이스용.
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { manualExpanded = !manualExpanded }
                    .background(TossGrayBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🔧 MMS 서버 수동 입력 (선택)",
                    fontSize = 12.sp,
                    color = TossTextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (manualExpanded) "▾" else "▸",
                    fontSize = 12.sp,
                    color = TossTextSecondary
                )
            }
            if (manualExpanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "보통은 자동으로 잡혀요. 알뜰폰 등 일부 SIM 에서 MMS 가 안 오면 직접 박으세요.\n빈 칸으로 두면 자동 사용.",
                    fontSize = 11.sp,
                    color = TossTextTertiary
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    label = { Text("MMSC URL") },
                    placeholder = { Text("예: http://mmsc.ktfwing.com:9082") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = manualProxy,
                    onValueChange = { manualProxy = it },
                    label = { Text("Proxy 호스트 (선택)") },
                    placeholder = { Text("예: smtmms.nate.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = manualPort,
                    onValueChange = { newValue -> manualPort = newValue.filter { it.isDigit() } },
                    label = { Text("Proxy 포트 (선택)") },
                    placeholder = { Text("예: 9093") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                TossPrimaryButton(
                    text = "저장",
                    onClick = {
                        preferences.manualMmscUrl = manualUrl.trim().takeIf { it.isNotBlank() }
                        preferences.manualMmscProxy = manualProxy.trim().takeIf { it.isNotBlank() }
                        preferences.manualMmscPort = manualPort.toIntOrNull() ?: 0
                        Toast.makeText(context, "저장됐어요", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

/**
 * 시공막내 웹 (PC 사진 캘린더, 2026-08-13) — docs/SERVER_HANDOFF_web_photo_calendar_SERVER_DONE.md.
 *   PC 브라우저서 si0in.kr/web 접속 → 뜬 QR을 이 폰으로 찍으면 로그인(폰=열쇠). 시공 사진을 큰 화면서 보고 블로그용 다운.
 *   웹은 보기 전용. 여기선 안내 + "이 계정 웹 로그아웃"(폰 원격 로그아웃)만.
 */
@Composable
private fun WebViewerSection(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = container.preferences
    val ownerPhone = prefs.bizPhone.trim()
    var active by remember { mutableStateOf(prefs.webViewerActive) }
    var busy by remember { mutableStateOf(false) }
    // 웹 로그인 인증(세션토큰) — 서버(90121cd)가 /api/web/authorize 에 토큰을 요구하는데 기존 유저는 토큰이 없어 401.
    //   여기서 한 번 문자 인증 → 토큰 저장 → 이후 QR 로그인 통과. (2026-08-15)
    var authed by remember { mutableStateOf(container.sessionTokenStore.hasValidToken(System.currentTimeMillis())) }
    var reauthOpen by remember { mutableStateOf(false) }
    var reauthCode by remember { mutableStateOf("") }
    var reauthBusy by remember { mutableStateOf(false) }
    var reauthSent by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // QR 로그인 승인 — ticket 으로 서버에 owner 증명. OK시 뷰어 켜고 피드 즉시 push. (딥링크 경로와 동일 로직)
    fun approve(ticket: String) {
        if (ownerPhone.filter { it.isDigit() }.length < 9) { toast("먼저 내 번호(로그인)가 필요해요"); return }
        busy = true
        scope.launch {
            val r = container.webFeedRepository.authorize(ticket, ownerPhone)
            busy = false
            when (r) {
                com.detailline.callfollowcrm.ai.WebFeedRepository.AuthResult.OK -> {
                    prefs.webViewerActive = true; active = true
                    runCatching { container.webFeedSyncManager.pushNow(force = true) }
                    container.ownerPhotoUploadManager.kick(scope)   // 폰 사진 서버로 백필
                    toast("PC 웹에 로그인됐어요 ✅ 사진도 웹으로 올라가요")
                }
                com.detailline.callfollowcrm.ai.WebFeedRepository.AuthResult.EXPIRED ->
                    toast("QR이 만료됐어요. 웹에서 새 QR을 띄워 다시 찍어주세요.")
                else -> toast("웹 로그인에 실패했어요. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    // 웹 로그인 인증(문자 OTP) — 토큰 없는 기존 유저용. 성공하면 sessionTokenStore 에 토큰 저장 → QR 로그인 통과.
    fun sendReauthCode() {
        if (ownerPhone.filter { it.isDigit() }.length < 9) { toast("먼저 내 번호(로그인)가 필요해요"); return }
        reauthBusy = true
        scope.launch {
            val since = System.currentTimeMillis() - 5000
            val r = container.authRepository.requestCode(ownerPhone)
            reauthBusy = false
            if (!r.isSuccess) {
                toast((r.exceptionOrNull() as? com.detailline.callfollowcrm.ai.AuthException)?.message ?: "발송 실패 — 잠시 후 다시")
                return@launch
            }
            reauthSent = true
            toast("인증문자 왔어요 — 자동으로 확인 중…")
            // 문자 자동 읽기 + 검증 (2초 x 25 = 50초). 못 읽으면 수동 입력 가능(다이얼로그 열려있음).
            for (i in 0 until 25) {
                kotlinx.coroutines.delay(2000)
                if (authed || !reauthOpen) return@launch
                val code = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readOtpFromInbox(context, since) } ?: continue
                reauthCode = code
                val vr = container.authRepository.verifyCode(ownerPhone, code)
                val v = vr.getOrNull()
                if (v != null && !v.sessionToken.isNullOrBlank()) {
                    container.sessionTokenStore.save(v.sessionToken, v.sessionTokenExpMs)
                    authed = true; reauthOpen = false; reauthSent = false
                    toast("인증 완료! 이제 QR로 로그인돼요 ✅")
                    return@launch
                }
            }
        }
    }
    fun verifyReauth() {
        if (reauthCode.length != 6) { toast("인증번호 6자리를 입력해주세요"); return }
        reauthBusy = true
        scope.launch {
            val r = container.authRepository.verifyCode(ownerPhone, reauthCode)
            reauthBusy = false
            r.onSuccess { v ->
                if (!v.sessionToken.isNullOrBlank()) {
                    container.sessionTokenStore.save(v.sessionToken, v.sessionTokenExpMs)
                    authed = true; reauthOpen = false; reauthSent = false; reauthCode = ""
                    toast("인증 완료! 이제 아래 QR로 로그인돼요 ✅")
                } else toast("인증은 됐는데 열쇠(토큰)가 안 왔어요. 관리자에게 알려주세요")
            }.onFailure { toast((it as? com.detailline.callfollowcrm.ai.AuthException)?.message ?: "인증 실패 — 다시 시도") }
        }
    }

    // 인앱 QR 스캐너(zxing-android-embedded). 결과 = 찍은 URL → t(티켓) 뽑아 승인. 취소면 contents=null.
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            val ticket = runCatching { android.net.Uri.parse(contents).getQueryParameter("t") }
                .getOrNull()?.takeIf { it.isNotBlank() }
            if (ticket == null) toast("시공막내 웹 로그인 QR이 아니에요") else approve(ticket)
        }
    }

    TossCard {
        Column(Modifier.padding(4.dp)) {
            Text(
                "시공막내 웹 (PC 사진 캘린더)",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "PC 큰 화면에서 시공 사진을 날짜별로 보고 블로그용으로 내려받아요. 딱 2단계예요:",
                fontSize = 13.sp, color = TossTextSecondary, lineHeight = 19.sp
            )
            Spacer(Modifier.height(12.dp))
            // ① 주소 — 크게 + 복사 (사장님이 PC 주소를 몰라 시작을 못 하던 문제. 2026-08-15)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEEF4FF)).border(1.dp, Color(0xFFD5E4FB), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text("①  PC 브라우저 주소창에 이렇게 치세요", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("si0in.kr/web", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(9.dp)).background(TossBlue)
                            .clickable {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("시공막내 웹 주소", "si0in.kr/web"))
                                toast("주소 복사했어요 — PC 주소창에 붙여넣기 하세요")
                            }.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) { Text("📋 복사", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold) }
                }
                Spacer(Modifier.height(4.dp))
                Text("주소를 치면 화면에 QR이 떠요.", fontSize = 11.5.sp, color = TossTextTertiary)
            }
            Spacer(Modifier.height(10.dp))
            Text("②  아래 버튼으로 그 QR을 찍으면 로그인돼요 (폰이 열쇠). 웹은 보기 전용이에요.",
                fontSize = 13.sp, color = TossTextSecondary, lineHeight = 19.sp)
            Spacer(Modifier.height(14.dp))
            // 웹 로그인 인증(세션토큰) 게이트 — 없으면 QR 로그인이 401로 거절되므로 먼저 인증. (2026-08-15)
            if (!authed) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFF7E6)).border(1.dp, Color(0xFFFFE2A8), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text("🔑  먼저 웹 로그인 인증 (한 번만)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A6100))
                    Spacer(Modifier.height(4.dp))
                    Text("보안 강화로 웹 로그인엔 인증이 한 번 필요해요. 인증 후 QR을 찍으면 로그인돼요.",
                        fontSize = 12.sp, color = TossTextSecondary, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    TossPrimaryButton(
                        text = if (reauthBusy) "인증 중…" else "웹 로그인 인증하기",
                        enabled = !reauthBusy,
                        onClick = { reauthCode = ""; reauthSent = false; reauthOpen = true; sendReauthCode() }
                    )
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Text("🔑 웹 로그인 인증됨 ✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                Spacer(Modifier.height(10.dp))
            }
            TossPrimaryButton(
                text = if (busy) "로그인 중…" else "PC 웹 로그인 (QR 찍기)",
                enabled = !busy,
                onClick = {
                    if (ownerPhone.filter { it.isDigit() }.length < 9) {
                        toast("먼저 내 번호(로그인)가 필요해요")
                    } else {
                        scanLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("PC 화면의 QR을 비춰주세요")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false)
                        )
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (active) "현재 이 계정으로 웹에 로그인돼 있어요." else "아직 웹에 로그인한 적 없어요.",
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = if (active) TossBlue else TossTextTertiary
            )
            Spacer(Modifier.height(8.dp))
            TossSecondaryButton(
                text = "이 계정 웹 로그아웃",
                enabled = !busy,
                onClick = {
                    if (ownerPhone.filter { it.isDigit() }.length < 9) {
                        toast("먼저 내 번호(로그인)가 필요해요")
                    } else {
                        busy = true
                        scope.launch {
                            val r = container.webFeedRepository.logoutAll(ownerPhone)
                            prefs.webViewerActive = false; active = false
                            busy = false
                            toast(if (r.isSuccess) "PC 웹에서 로그아웃했어요" else "로그아웃 요청 실패 — 잠시 후 다시")
                        }
                    }
                }
            )

            // 웹 로그인 인증 다이얼로그 (문자 6자리 OTP)
            if (reauthOpen) {
                AlertDialog(
                    onDismissRequest = { if (!reauthBusy) reauthOpen = false },
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    title = { Text("웹 로그인 인증", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                    text = {
                        Column {
                            Text(
                                if (reauthSent) "문자로 온 6자리 인증번호를 입력하세요." else "인증문자를 보내는 중…",
                                fontSize = 13.sp, color = TossTextSecondary
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = reauthCode,
                                onValueChange = { v -> reauthCode = v.filter { it.isDigit() }.take(6) },
                                placeholder = { Text("6자리") },
                                singleLine = true,
                                enabled = reauthSent && !reauthBusy,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                                )
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { verifyReauth() },
                            enabled = reauthSent && !reauthBusy && reauthCode.length == 6
                        ) { Text("인증 확인", color = TossBlue, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { if (!reauthBusy) reauthOpen = false }) {
                            Text("취소", color = TossTextTertiary)
                        }
                    }
                )
            }
        }
    }
}

/** 수신함에서 최근(sinceMs 이후) '인증번호'가 든 문자의 6자리 코드. READ_SMS 없으면/실패면 null. (웹 로그인 재인증 자동읽기) */
private fun readOtpFromInbox(ctx: android.content.Context, sinceMs: Long): String? = runCatching {
    ctx.contentResolver.query(
        android.provider.Telephony.Sms.Inbox.CONTENT_URI,
        arrayOf(android.provider.Telephony.Sms.BODY, android.provider.Telephony.Sms.DATE),
        "${android.provider.Telephony.Sms.DATE} >= ?",
        arrayOf(sinceMs.toString()),
        "${android.provider.Telephony.Sms.DATE} DESC"
    )?.use { c ->
        val bodyIdx = c.getColumnIndex(android.provider.Telephony.Sms.BODY)
        var scanned = 0
        while (c.moveToNext() && scanned < 8) {
            scanned++
            val body = if (bodyIdx >= 0) c.getString(bodyIdx) ?: "" else ""
            if (body.contains("인증번호") || body.contains("시공막내")) {
                Regex("(\\d{6})").find(body)?.groupValues?.get(1)?.let { return@use it }
            }
        }
        null
    }
}.getOrNull()

/**
 * 본폰에서 일정 보기 (미러 v2 "공유 신청/수락", 2026-07-14) — docs/SERVER_HANDOFF_mirror_v2.md.
 *   본폰(빈 달력, 웹)이 이 업무폰의 고정 공유 코드를 넣어 "공유 신청" → 여기서 수락하면 내 일정이 본폰에 읽기전용으로.
 *   규칙: "업무폰이 코드 만들고, 본폰이 넣는다." 협업 요청(수락/거절)과 동일 컨셉. 옵트인(기본 꺼짐).
 */
/**
 * 구글 캘린더 연동 (본폰 미러링 대체, 2026-08-31) — 시공/AS 일정을 구글 "시공막내" 캘린더에 올림.
 *   위젯·구글 캘린더 앱에서 보기 + 가족/직원 공유 + 폰 교체 백업. 참고: 동생 jeongsan/lib/calendar_sync.dart.
 */
@Composable
private fun GoogleCalendarSection(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = container.preferences

    var connected by remember { mutableStateOf(prefs.googleCalendarConnected) }
    var busy by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // 토큰 확보 후: 연결 플래그 ON + '시공막내' 캘린더 준비 + 기존 일정 전부 올리기
    fun finishConnect(token: String?) {
        if (token == null) { busy = false; toast("연결이 취소됐거나 실패했어요"); return }
        prefs.googleCalendarConnected = true; connected = true
        scope.launch {
            val n = runCatching { container.calendarSyncManager.syncAll() }.getOrDefault(-1)
            busy = false
            toast(if (n >= 0) "구글 캘린더에 연결됐어요 — 일정 ${n}건 올렸어요" else "연결됐어요 (동기화는 잠시 후 자동 재시도)")
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        finishConnect(container.googleCalendarConnection.tokenFromConsentResult(result.data))
    }

    fun connect() {
        busy = true
        scope.launch {
            when (val r = runCatching { container.googleCalendarConnection.authorize() }.getOrNull()) {
                is com.detailline.callfollowcrm.data.calendar.GoogleCalendarConnection.AuthResult.Success ->
                    finishConnect(r.accessToken)
                is com.detailline.callfollowcrm.data.calendar.GoogleCalendarConnection.AuthResult.NeedsConsent ->
                    consentLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(r.intentSender).build()
                    )
                else -> { busy = false; toast("구글 로그인을 시작할 수 없어요") }
            }
        }
    }

    fun disconnect() {
        prefs.googleCalendarConnected = false; connected = false
        prefs.googleCalendarId = null
        toast("연결을 껐어요 (이미 올라간 일정은 구글 캘린더에 그대로 남아요)")
    }

    fun syncNow() {
        busy = true
        scope.launch {
            val n = runCatching { container.calendarSyncManager.syncAll() }.getOrDefault(-1)
            busy = false
            toast(if (n >= 0) "동기화했어요 (일정 ${n}건)" else "먼저 연결이 필요해요")
        }
    }

    TossCard {
        Column {
            Text("📅 구글 캘린더 연동", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                "시공·A/S 일정이 구글 캘린더('시공막내')에 자동으로 올라가요. 폰 위젯·구글 캘린더 앱에서 보고, 가족·직원과 공유하거나, 폰을 바꿔도 그대로 남아요.",
                fontSize = 12.sp, color = TossTextTertiary, lineHeight = 16.sp
            )
            Spacer(Modifier.height(12.dp))
            if (!connected) {
                androidx.compose.material3.Button(
                    onClick = { if (!busy) connect() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "연결 중…" else "구글 계정 연결하기") }
            } else {
                Text("✓ 연결됨", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Spacer(Modifier.height(8.dp))
                Row {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { if (!busy) syncNow() },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (busy) "동기화 중…" else "지금 동기화") }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { if (!busy) disconnect() },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("연결 끄기") }
                }
            }
        }
    }
}

@Composable
private fun MirrorSection(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = container.preferences
    val ownerPhone = prefs.bizPhone.trim()

    var enabled by remember { mutableStateOf(prefs.mirrorEnabled) }
    var label by remember { mutableStateOf(prefs.mirrorLabel.ifBlank { prefs.bizName }) }
    var code by remember { mutableStateOf(prefs.mirrorCode) }
    var qrUrl by remember { mutableStateOf(prefs.mirrorQrUrl) }
    var busy by remember { mutableStateOf(false) }
    var addExpanded by remember { mutableStateOf(false) }   // 본폰 추가(QR) 펼침 — 연결 있으면 기본 접힘
    var pending by remember { mutableStateOf<List<com.detailline.callfollowcrm.ai.MirrorRepository.ShareRequest>>(emptyList()) }
    var accepted by remember { mutableStateOf<List<com.detailline.callfollowcrm.ai.MirrorRepository.Connection>>(emptyList()) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun fmtPhone(p: String) = com.detailline.callfollowcrm.util.PhoneNumberFormatter.format(p)

    suspend fun refreshShares() {
        if (!prefs.mirrorEnabled || ownerPhone.filter { it.isDigit() }.length < 9) return
        container.mirrorRepository.shares(ownerPhone).onSuccess {
            pending = it.pending; accepted = it.accepted
        }
    }

    fun enableMirror() {
        if (ownerPhone.filter { it.isDigit() }.length < 9) { toast("먼저 내 번호(로그인)가 필요해요"); return }
        val lbl = label.trim().ifBlank { "내 일정" }
        // 옵티미스틱 — 서버가 잠깐 안 돼도 '켜짐'은 유지하고, 코드는 준비되는 대로 채운다(LaunchedEffect 재시도).
        prefs.mirrorLabel = lbl; label = lbl
        prefs.mirrorEnabled = true; enabled = true
        busy = true
        scope.launch {
            val r = container.mirrorRepository.myCode(ownerPhone, lbl, tint = 0)
            busy = false
            r.onSuccess { mc ->
                prefs.mirrorCode = mc.code; code = mc.code
                prefs.mirrorQrUrl = mc.qrUrl; qrUrl = mc.qrUrl
                runCatching { container.mirrorSyncManager.pushNow(force = true) }
                refreshShares()
            }.onFailure { toast("코드는 곧 준비돼요 (서버 연결 중)") }
        }
    }

    fun respondReq(req: com.detailline.callfollowcrm.ai.MirrorRepository.ShareRequest, accept: Boolean) {
        busy = true
        scope.launch {
            // 서버 결과를 확인하고 토스트 — 실패해도 "됐어요"라 하던 거짓 피드백 제거. (2026-07-30)
            val r = container.mirrorRepository.respond(ownerPhone, req.id, accept)
            busy = false
            if (r.isSuccess) {
                prefs.mirrorSeenShareIds = prefs.mirrorSeenShareIds + req.id.toString()
                if (accept) runCatching { container.mirrorSyncManager.pushNow(force = true) }
            }
            refreshShares()
            toast(
                when {
                    r.isFailure -> "연결이 안 됐어요 — 잠시 후 다시 시도해주세요"
                    accept -> "수락했어요 — 본폰에 일정이 보여요"
                    else -> "거절했어요"
                }
            )
        }
    }

    fun disconnectConn(conn: com.detailline.callfollowcrm.ai.MirrorRepository.Connection) {
        busy = true
        scope.launch {
            val r = container.mirrorRepository.disconnect(ownerPhone, conn.id)
            busy = false
            refreshShares()
            toast(if (r.isFailure) "해제가 안 됐어요 — 잠시 후 다시 시도해주세요" else "공유를 해제했어요")
        }
    }

    // 화면 진입 시 코드·QR을 서버에서 최신화(옛 6자리→신규 8자리·qrUrl 반영), 켜져 있는 동안 8초마다 신청/연결 갱신.
    LaunchedEffect(enabled) {
        if (enabled && ownerPhone.filter { it.isDigit() }.length >= 9) {
            container.mirrorRepository.myCode(ownerPhone, label.trim().ifBlank { "내 일정" }, 0)
                .onSuccess { mc ->
                    prefs.mirrorCode = mc.code; code = mc.code
                    prefs.mirrorQrUrl = mc.qrUrl; qrUrl = mc.qrUrl
                }
        }
        while (enabled) {
            refreshShares()
            kotlinx.coroutines.delay(8_000)
        }
    }

    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("📱 본폰에서 일정 보기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text("본폰(빈 달력)에서 내 공유 코드를 넣고 신청하면, 여기서 수락해요. 수락하면 내 일정이 본폰에 읽기전용으로 보여요.",
                        fontSize = 12.sp, color = TossTextTertiary, lineHeight = 16.sp)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { want ->
                        if (want) enableMirror()
                        else { prefs.mirrorEnabled = false; enabled = false }
                    }
                )
            }

            if (enabled) {
                Spacer(Modifier.height(14.dp))
                // ── 공유중 (지금 상태 = 주 콘텐츠, 위로) ──
                Text("공유중", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                if (accepted.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("아직 공유 중인 본폰이 없어요. 아래 [본폰 연결하기]를 눌러 QR을 띄우고, 본폰 카메라로 찍으세요.",
                        fontSize = 11.sp, color = TossTextTertiary, lineHeight = 15.sp)
                } else {
                    accepted.forEach { conn ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📅 ${fmtPhone(conn.homePhone)}와 일정 공유중",
                                fontSize = 13.sp, color = TossTextPrimary, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f))
                            Text("공유 해제", fontSize = 12.sp, color = TossError, fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { disconnectConn(conn) }.padding(4.dp))
                        }
                    }
                }

                // ── 공유 신청(수락 대기) — 있을 때만 ──
                if (pending.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                    Spacer(Modifier.height(10.dp))
                    Text("공유 신청", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    pending.forEach { req ->
                        Spacer(Modifier.height(8.dp))
                        Text("📩 ${fmtPhone(req.homePhone)}가 일정 공유를 신청했어요",
                            fontSize = 13.sp, color = TossTextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { TossPrimaryButton(text = "수락", onClick = { respondReq(req, true) }) }
                            Box(
                                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, TossDivider, RoundedCornerShape(14.dp))
                                    .clickable { respondReq(req, false) },
                                contentAlignment = Alignment.Center
                            ) { Text("거절", fontSize = 14.sp, color = TossTextSecondary, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }

                // ── 본폰 추가/연결 (QR) — 처음(연결 0개)엔 펼침, 이후엔 [➕ 본폰 추가]로 접힘 ──
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                Spacer(Modifier.height(4.dp))
                // 기본 접힘 — 사장님 피드백("코드 칸이 자리 너무 차지"). 처음부터 버튼만, 눌러야 QR 펼침.
                val qrOpen = addExpanded
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { addExpanded = !addExpanded }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, null, tint = TossBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (accepted.isEmpty()) "본폰 연결하기 (QR 띄우기)" else "본폰 추가 (QR 띄우기)",
                        fontSize = 13.sp, color = TossBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(if (qrOpen) "▾" else "▸", fontSize = 13.sp, color = TossBlue)
                }
                if (qrOpen) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(14.dp)) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("본폰 카메라로 이 QR을 찍으세요", fontSize = 12.sp, color = TossTextSecondary, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(10.dp))
                            // QR = 서버가 준 qrUrl(자동수락 시크릿 포함) 우선, 없으면 homeUrl?code= 폴백.
                            val qrText = qrUrl ?: ("https://api.si0in.kr/mirror" + (code?.let { "?code=$it" } ?: ""))
                            val qr = remember(qrText) { com.detailline.callfollowcrm.util.QrGen.bitmap(qrText, 520) }
                            if (qr != null) {
                                Image(
                                    bitmap = qr.asImageBitmap(),
                                    contentDescription = "본폰 접속 QR",
                                    modifier = Modifier.size(170.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("내 공유 코드  ${code ?: "…"}", fontSize = 13.sp, color = TossTextSecondary, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("QR이 안 되면 본폰에서 api.si0in.kr/mirror 열고 위 코드를 넣어도 돼요.",
                                fontSize = 11.sp, color = TossTextTertiary, lineHeight = 15.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("마지막 전송: ${mirrorAgoLabel(prefs.mirrorLastPushMs)}", fontSize = 11.sp, color = TossTextSecondary)
            } else {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("이 폰 이름 (예: 디테일라인)", fontSize = 12.sp) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard, fontSize = 13.sp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("켜면 ‘내 공유 코드’가 생겨요. 본폰(개인폰)에서 그 코드를 넣고 신청하면 여기서 수락해요. 본폰엔 앱이 필요 없어요.",
                    fontSize = 11.sp, color = TossTextTertiary, lineHeight = 15.sp)
            }

            if (busy) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = TossBlue
                )
            }
        }
    }
}

private fun mirrorAgoLabel(ms: Long): String {
    if (ms <= 0L) return "아직 전송 전"
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000L -> "방금"
        diff < 3_600_000L -> "${diff / 60_000L}분 전"
        diff < 86_400_000L -> "${diff / 3_600_000L}시간 전"
        else -> "${diff / 86_400_000L}일 전"
    }
}

/**
 * 2026-05-29 킬러콘텐츠 3단계 후속 — 추천 답변 채택률 카드.
 *
 * 사장님이 "수정 거리 0" 향해 진화하는 모습 직접 확인. 데이터 쌓일수록 동기부여.
 *
 * 디자인:
 *   상단: 기간 (오늘 / 이번 주 / 이번 달) chip 선택
 *   채택률 % + (N건 중 M건 그대로)
 *   평균 수정 거리
 *   4가지 action 분포 (가로 막대)
 *   데이터 없으면 "아직 데이터 없어요" placeholder.
 */
@Composable
private fun SuggestionStatsCard(
    stats: com.detailline.callfollowcrm.data.repository.SuggestionEventRepository.Stats?,
    periodDays: Int,
    onPeriodChange: (Int) -> Unit
) {
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "💡 추천 답변 채택률",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TossTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "사장님이 그대로 보낸 비율. 100% 에 가까울수록 추천 품질 ↑",
                fontSize = 11.sp,
                color = TossTextSecondary
            )
            Spacer(Modifier.height(10.dp))
            // 기간 chip
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "오늘", 7 to "이번 주", 30 to "이번 달").forEach { (days, label) ->
                    val selected = days == periodDays
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) TossBlue else TossGrayBg,
                        modifier = Modifier.clickable { onPeriodChange(days) }
                    ) {
                        Text(
                            label,
                            fontSize = 11.sp,
                            color = if (selected) Color.White else TossTextSecondary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (stats == null || stats.total == 0) {
                // 데이터 없음 — 첫 사용 안내
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TossGrayBg)
                        .padding(16.dp)
                ) {
                    Text(
                        "아직 데이터가 없어요.\n채팅 화면에서 AI 추천 답변을 사용해보세요.",
                        fontSize = 12.sp,
                        color = TossTextTertiary
                    )
                }
            } else {
                // 채택률 — 큰 숫자
                val ratePct = (stats.adoptionRate * 100).toInt()
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$ratePct",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossBlue
                    )
                    Text(
                        "%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TossBlue,
                        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            "${stats.total}건 중 ${stats.adopted}건",
                            fontSize = 12.sp,
                            color = TossTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "그대로 보냈어요",
                            fontSize = 11.sp,
                            color = TossTextTertiary
                        )
                    }
                }
                if (stats.edited > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "✏️ 수정한 답변 평균 ${stats.averageEditDistance.toInt()}자 고침",
                        fontSize = 11.sp,
                        color = TossTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(14.dp))

                // 4가지 action 분포
                StatsBar(label = "✅ 그대로", count = stats.adopted, total = stats.total, color = TossSuccess)
                Spacer(Modifier.height(6.dp))
                StatsBar(label = "✏️ 수정", count = stats.edited, total = stats.total, color = TossBlue)
                Spacer(Modifier.height(6.dp))
                StatsBar(label = "🤷 무시", count = stats.ignored, total = stats.total, color = TossTextTertiary)
                Spacer(Modifier.height(6.dp))
                StatsBar(label = "👋 떠남", count = stats.dismissed, total = stats.total, color = TossTextTertiary)
            }
        }
    }
}

@Composable
private fun StatsBar(label: String, count: Int, total: Int, color: Color) {
    val frac = if (total <= 0) 0f else (count.toFloat() / total).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 11.sp,
            color = TossTextSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(64.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(TossGrayBg)
        ) {
            if (frac > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color)
                )
            }
        }
        Text(
            "${count}건",
            fontSize = 11.sp,
            color = TossTextTertiary,
            modifier = Modifier
                .padding(start = 8.dp)
                .width(40.dp)
        )
    }
}

/**
 * 2026-05-29 킬러콘텐츠 6단계 — 자동 학습 루프 카드.
 *
 * 시나리오별 / intent_key 별 채택률 분석. 채택률 낮은 시나리오 자동 강조 (needsImprovement).
 * 추후 cowork 가 prompt 개선 endpoint 박으면 [✏️ 개선 제안] 버튼 활성화.
 *
 * 사장님 가치:
 *   - "어떤 상황에서 시공막내 가 잘 답하고, 어떤 상황에서 못 하는지" 한눈에.
 *   - 개선 우선순위 자동 — 사장님이 "Mac mini 한테 이 부분 개선해" 라고 말할 근거.
 */
@Composable
private fun AutoLearningCard(
    scenarios: List<com.detailline.callfollowcrm.data.repository.SuggestionEventRepository.ScenarioBreakdown>,
    intents: List<com.detailline.callfollowcrm.data.repository.SuggestionEventRepository.IntentBreakdown>
) {
    if (scenarios.isEmpty() && intents.isEmpty()) return  // 데이터 없으면 카드 자체 숨김 (위 SuggestionStatsCard 가 placeholder 책임)

    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔄", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "자동 학습 (시나리오별 분석)",
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "어떤 상황에서 사장님이 그대로 보내는지, 어떤 상황은 개선이 필요한지 시공막내 가 직접 분석합니다.",
                fontSize = 11.sp,
                color = TossTextSecondary
            )

            if (scenarios.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "📊 시나리오별 채택률",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TossTextPrimary
                )
                Spacer(Modifier.height(8.dp))
                scenarios.forEach { s ->
                    ScenarioRow(s)
                    Spacer(Modifier.height(8.dp))
                }
                // 개선 후보 (채택률 40% 미만, 5건 이상) 자동 강조.
                val needsImprovement = scenarios.filter { it.needsImprovement }
                if (needsImprovement.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3DF))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                "💡 개선 후보 ${needsImprovement.size}개 시나리오",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB8780A)
                            )
                            Spacer(Modifier.height(4.dp))
                            needsImprovement.forEach { s ->
                                Text(
                                    "• ${scenarioLabel(s.scenario)} — ${(s.adoptionRate * 100).toInt()}% (${s.total}건)",
                                    fontSize = 11.sp,
                                    color = Color(0xFFB8780A)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Mac mini 의 답변 prompt 개선 가능 — cowork 한테 'AutoLearning 후보 보고 prompt 개선해줘' 라고 시키세요.",
                                fontSize = 10.sp,
                                color = TossTextSecondary
                            )
                        }
                    }
                }
            }

            if (intents.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "🏷️ 의도별 채택 순위",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TossTextPrimary
                )
                Spacer(Modifier.height(8.dp))
                // top 5 intent (자주 쓰는 거)
                intents.take(5).forEach { i ->
                    IntentRow(i)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ScenarioRow(s: com.detailline.callfollowcrm.data.repository.SuggestionEventRepository.ScenarioBreakdown) {
    val pct = (s.adoptionRate * 100).toInt()
    val barColor = when {
        s.adoptionRate >= 0.7 -> TossSuccess
        s.adoptionRate >= 0.4 -> TossBlue
        else -> TossError
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                scenarioLabel(s.scenario),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TossTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$pct%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
            Text(
                " · ${s.total}건",
                fontSize = 11.sp,
                color = TossTextTertiary
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TossGrayBg)
        ) {
            if (s.adoptionRate > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(s.adoptionRate.toFloat())
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }
        }
    }
}

@Composable
private fun IntentRow(i: com.detailline.callfollowcrm.data.repository.SuggestionEventRepository.IntentBreakdown) {
    val pct = (i.adoptionRate * 100).toInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            i.intentLabel ?: i.intentKey,
            fontSize = 11.sp,
            color = TossTextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$pct%",
            fontSize = 11.sp,
            color = TossBlue,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            " · ${i.total}건",
            fontSize = 10.sp,
            color = TossTextTertiary
        )
    }
}

/** scenario key → 한국어 라벨 매핑. 옛 fallback_default 등 unknown 키도 fallthrough. */
private fun scenarioLabel(scenario: String): String = when (scenario) {
    "initial_inquiry" -> "초기 문의"
    "price_inquiry" -> "가격 문의"
    "hesitation" -> "고객 망설임"
    "schedule" -> "일정 조율"
    "pre_booking" -> "예약 확정 전"
    "pre_service" -> "시공 전"
    "post_service" -> "시공 후"
    "fallback_default" -> "기타 / 분류 신뢰도 낮음"
    else -> scenario
}

/** AI 서버 연결 상태 — ● 색깔 + 사용량 placeholder. */
@Composable
private fun ServerStatusCard(alive: Boolean?) {
    val (dotColor, statusText, subtext) = when (alive) {
        true -> Triple(
            TossSuccess,
            "AI 서버 정상",
            "서버 연결됨. 채팅 답변 추천 / 견적 도움이 작동해요."
        )
        false -> Triple(
            TossError,
            "AI 서버 연결 안 됨",
            "인터넷 연결을 확인해 주세요. 잠시 후 자동으로 다시 연결돼요. 답변 추천이 안 떠도 메시지는 보낼 수 있어요."
        )
        null -> Triple(
            TossTextTertiary,
            "AI 서버 확인 중",
            "30초 안에 첫 응답이 옵니다."
        )
    }
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(dotColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                subtext,
                style = MaterialTheme.typography.bodySmall,
                color = TossTextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "이번 달 사용량은 추후 표시됩니다 (맥미니 서버 사용량 endpoint 구현 후).",
                style = MaterialTheme.typography.labelSmall,
                color = TossTextTertiary
            )
        }
    }
}

/** 통화 종료 후 동작 — AfterCallBehavior + 후속 알림 빠른 액션 + 처음 연락 자동 응답 통합. */
/**
 * 프로토 openAutoSms 1:1 — 자동 문자 4카드 (부재중 신규/단골 · D-1 · 도착 · 정기).
 *   인라인 텍스트는 AppPreferences 에 즉시 저장(프로토 "저장" 없이 자동). 부재중 토글=autoFirstReplyEnabled.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AutoSmsSection(
    autoReplyOn: Boolean,
    onAutoReplyToggle: (Boolean) -> Unit,
    incomingNotifyOn: Boolean,
    onIncomingNotifyToggle: (Boolean) -> Unit,
    onOpenRecurring: () -> Unit,
    onArrivalToggle: (Boolean) -> Unit = {},
    /** true = 부재중 자동 응답 카드를 펼친 상태로 시작 (상담함 알림 길게누름 진입). */
    expandMissed: Boolean = false
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        (ctx.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container.preferences
    }
    var missedNew by remember { mutableStateOf(prefs.autoMissedNewText) }
    var missedReturn by remember { mutableStateOf(prefs.autoMissedReturnText) }
    var d1On by remember { mutableStateOf(prefs.d1AutoEnabled) }
    var d1Hour by remember { mutableStateOf(prefs.d1SendHour) }
    var d1Text by remember { mutableStateOf(prefs.d1AutoText) }
    var arrOn by remember { mutableStateOf(prefs.arrivalAutoEnabled) }
    var aiPrepOn by remember { mutableStateOf(prefs.aiReplyPrepEnabled) }
    var arrText by remember { mutableStateOf(prefs.arrivalAutoText) }
    var spamPrefixes by remember { mutableStateOf(prefs.spamPrefixes) }
    var newSpamPrefix by remember { mutableStateOf("") }

    fun hourLabel(h: Int): String = when {
        h == 0 -> "오전 12시"; h < 12 -> "오전 ${h}시"; h == 12 -> "오후 12시"; else -> "오후 ${h - 12}시"
    }

    Text("전화·시공으로 바쁠 때 고객을 놓치지 않게 자동으로 챙겨요.",
        fontSize = 13.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))

    Text("상황이 되면 자동으로", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 6.dp))

    // ① 부재중 자동 응답
    AutoCard("📞", TossBlueSoft, "부재중 자동 응답", "즉시 발송", "전화 못 받으면 자동으로 문자 발송",
        autoReplyOn, onAutoReplyToggle, initiallyExpanded = expandMissed) {
        AutoDotLabel(TossBlue, "처음 연락한 고객 (신규)")
        AutoTextArea(missedNew) { missedNew = it; prefs.autoMissedNewText = it }
        Spacer(Modifier.height(10.dp))
        AutoDotLabel(TossSuccess, "다시 연락한 고객 (단골·기존)")
        AutoTextArea(missedReturn) { missedReturn = it; prefs.autoMissedReturnText = it }
        AutoNote("신규·단골 모두 자동으로 나가요. 보내기 직전 10초 안에 취소할 수 있어요. 같은 번호엔 하루 1번만 — 최근 24시간 안에 보낸 문자(이미 답장했거나 방금 자동발송)가 있으면 건너뛰어요.")
    }
    Spacer(Modifier.height(10.dp))

    // ② 시공 하루 전 안내 (D-1)
    AutoCard("📅", Color(0xFFFFF1E6), "시공 하루 전 안내 (D-1)", null,
        "시공 전날 ${hourLabel(d1Hour)} · 보내기 전 확인",
        d1On, { d1On = it; prefs.d1AutoEnabled = it }) {
        Text("전날 몇 시에 물어볼까요", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
            modifier = Modifier.padding(bottom = 6.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(8, 9, 10, 11, 18, 19).forEach { h ->
                AutoChip(hourLabel(h), d1Hour == h) { d1Hour = h; prefs.d1SendHour = h }
            }
        }
        Spacer(Modifier.height(8.dp))
        AutoTextArea(d1Text) { d1Text = it; prefs.d1AutoText = it }
        AutoNote("전날 이 시각에 막내가 “보낼까요?” 하고 먼저 물어봐요. 사장님이 확인 눌러야 고객에게 나가요 — 무음 자동발송이 아니에요.")
    }
    Spacer(Modifier.height(10.dp))

    // ③ 오늘 시공 도착 안내
    AutoCard("📍", Color(0xFFE6F7EE), "오늘 시공 도착 안내", null, "상담함 오늘시공 섹션 · 보내기 전 확인",
        arrOn, { arrOn = it; prefs.arrivalAutoEnabled = it; onArrivalToggle(it) }) {
        AutoTextArea(arrText) { arrText = it; prefs.arrivalAutoText = it }
        AutoNote("상담함의 오늘시공 도착 안내와 같은 문구예요. 위치 감지는 준비 중이라 지금은 사장님 확인 후 보내는 안내로 사용해요.")
    }
    Spacer(Modifier.height(10.dp))

    // ④ 통화 자동 요약 (2026-06-14 사장님) — 통화 끝나면 에이닷 녹음/텍스트를 자동 요약(공유 안 눌러도 됨).
    var autoSumOn by remember { mutableStateOf(prefs.autoSummaryEnabled) }
    TossCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEDE9FE)),
                contentAlignment = Alignment.Center) { Text("🤖", fontSize = 16.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("통화 자동 요약", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Text("통화가 끝나면 통화 녹음을 자동으로 요약해 통화카드에 붙여요 (공유 버튼 안 눌러도 됨)",
                    fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = autoSumOn, onCheckedChange = { autoSumOn = it; prefs.autoSummaryEnabled = it })
        }
    }
    Spacer(Modifier.height(8.dp))

    // ④-b 화면 캡처 막기 (2026-08-20 사장님) — 기본 OFF(베타 버그 캡처 위해). 켜면 릴리스에서 스샷/녹화 차단. live-apply.
    val capCtx = LocalContext.current
    var blockCapOn by remember { mutableStateOf(prefs.blockScreenCapture) }
    TossCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFDE8E8)),
                contentAlignment = Alignment.Center) { Text("🔒", fontSize = 16.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("화면 캡처 막기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Text("켜면 고객 정보·통화·돈 화면의 스크린샷·화면 녹화를 막아요 (보안). 지금은 버그 캡처를 위해 꺼둠",
                    fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = blockCapOn, onCheckedChange = { want ->
                blockCapOn = want; prefs.blockScreenCapture = want
                (capCtx as? android.app.Activity)?.window?.let { w ->
                    if (want) w.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    ) else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            })
        }
    }
    Spacer(Modifier.height(8.dp))

    // ④-2 전화 오는 사람 미리보기 (2026-07-01 사장님) — 벨 울릴 때 화면 '테두리'에 상태색을 둘러 신규/예정/기존/완료를 한눈에. (2026-08-31 카드→테두리)
    //   실제로 뜨려면 "다른 앱 위에 표시"(SYSTEM_ALERT_WINDOW) 특수 권한 필요 → 켰는데 없으면 안내+허용 버튼.
    var callerCardOn by remember { mutableStateOf(prefs.incomingCallerCardEnabled) }
    var overlayGranted by remember { mutableStateOf(com.detailline.callfollowcrm.util.PermissionHelper.hasOverlay(ctx)) }
    val overlayPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { overlayGranted = com.detailline.callfollowcrm.util.PermissionHelper.hasOverlay(ctx) }
    // 통화 스크리닝 역할 — 새 안드로이드(10+)서 벨 중 수신번호를 잡으려면 필요(전화 안 막고 테두리만 얹음). (2026-08-31 사장님)
    val screeningRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE8F1FE)),
                    contentAlignment = Alignment.Center) { Text("📞", fontSize = 16.sp) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("전화 오는 사람 미리보기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Text("전화가 오면 화면 테두리에 색이 둘러져, 받기 전에 신규·시공예정·기존·완료를 한눈에 (전화 화면은 그대로 · 신규=노랑)",
                        fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp)
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = callerCardOn, onCheckedChange = { want ->
                    callerCardOn = want; prefs.incomingCallerCardEnabled = want
                    // 켜는데 '다른 앱 위에 표시' 권한 없으면 바로 승인 창으로. (2026-07-12 사장님)
                    if (want && !overlayGranted) {
                        runCatching {
                            overlayPermLauncher.launch(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${ctx.packageName}")
                                )
                            )
                        }
                    }
                    // 통화 스크리닝 역할도 요청 — 있어야 벨 중 수신번호를 잡아 테두리가 뜬다. 이미 있으면 스킵(무해). (2026-08-31 사장님)
                    if (want && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        runCatching {
                            val rm = ctx.getSystemService(android.app.role.RoleManager::class.java)
                            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING)
                                && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
                                screeningRoleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING))
                            }
                        }
                    }
                })
            }
            if (callerCardOn && !overlayGranted) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF3DF))
                        .clickable {
                            runCatching {
                                overlayPermLauncher.launch(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${ctx.packageName}")
                                    )
                                )
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column {
                        Text("⚠️ '다른 앱 위에 표시' 권한이 필요해요", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = Color(0xFFB8780A))
                        Text("여기를 눌러 허용하면 전화 올 때 테두리 색이 떠요", fontSize = 12.sp, color = Color(0xFFB8780A))
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    // 통화 녹음 자동 찾기 — 오디오 권한 한 번이면 MediaStore 에서 통화녹음(에이닷·T전화·삼성)을 앱이 알아서 찾는다.
    //   폴더를 직접 고를 필요 X (연세 있으신 분 배려, 2026-06-30). 폴더 직접 고르기는 fallback 으로 남김.
    val recAppContainer = (ctx.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container
    var recFolderConnected by remember { mutableStateOf(com.detailline.callfollowcrm.recording.AdotFolderScanner.isConnected(ctx)) }
    // 무엇이 연결됐는지 사람이 읽는 한 줄(폴더 이름/자동찾기 + 녹음 개수) — 사장님이 확인 가능하게. (2026-07-12 사장님)
    var recLabel by remember { mutableStateOf(com.detailline.callfollowcrm.recording.AdotFolderScanner.connectedLabel(ctx)) }
    val recScope = androidx.compose.runtime.rememberCoroutineScope()
    val recFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            com.detailline.callfollowcrm.recording.AdotFolderScanner.connectFolder(ctx, uri)
            recFolderConnected = true
            recLabel = com.detailline.callfollowcrm.recording.AdotFolderScanner.connectedLabel(ctx)
            android.widget.Toast.makeText(
                ctx, recLabel?.let { "연결됐어요 ✓  $it" } ?: "녹음 폴더 연결됐어요. 이제 통화 끝나면 자동으로 요약돼요.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    val recAudioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            com.detailline.callfollowcrm.recording.AdotFolderScanner.enableMediaStore(ctx)
            recFolderConnected = true
            recScope.launch {
                val label = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.detailline.callfollowcrm.recording.AdotFolderScanner.connectedLabel(ctx)
                }
                recLabel = label
                val n = label?.substringAfter("녹음 ", "")?.substringBefore("개")?.toIntOrNull() ?: 0
                android.widget.Toast.makeText(
                    ctx,
                    if (n > 0) "통화 녹음 ${n}개를 찾았어요! 이제 통화 끝나면 자동으로 요약돼요 ✨"
                    else "연결됐어요. 이제 통화 끝나면 녹음을 자동으로 요약해요.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } else {
            android.widget.Toast.makeText(
                ctx, "오디오 권한을 허용해야 통화 녹음을 찾을 수 있어요.", android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEDE9FE)),
                    contentAlignment = Alignment.Center) { Text("🎙️", fontSize = 16.sp) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("통화 녹음 자동 찾기${if (recFolderConnected) " · 연결됨" else ""}", fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Text(
                        if (recFolderConnected) (recLabel?.let { "✅ $it" } ?: "통화 끝나면 녹음으로 자동 요약돼요 (↑ 안 눌러도 됨)")
                        else "버튼 한 번이면 통화 녹음을 앱이 알아서 찾아드려요. 폴더 안 찾아도 돼요.",
                        fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (recFolderConnected) Color(0xFFEEF0F3) else Color(0xFF3182F6))
                        .clickable {
                            if (recFolderConnected) {
                                com.detailline.callfollowcrm.recording.AdotFolderScanner.scanIfConnected(ctx, recAppContainer) { }
                                recLabel = com.detailline.callfollowcrm.recording.AdotFolderScanner.connectedLabel(ctx)
                                android.widget.Toast.makeText(ctx,
                                    recLabel?.let { "확인했어요 ✓  $it" } ?: "통화 녹음을 보고 있어요 ✓",
                                    android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                recAudioPermLauncher.launch(
                                    com.detailline.callfollowcrm.recording.AdotFolderScanner.audioPermission()
                                )
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (recFolderConnected) "다시 확인" else "자동으로 찾기",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (recFolderConnected) TossTextSecondary else Color.White)
                }
            }
            // fallback — 자동으로 안 잡히는 기기/상황엔 폴더 직접 고르기.
            if (!recFolderConnected) {
                Spacer(Modifier.height(8.dp))
                Text("자동으로 안 되면 → 폴더 직접 고르기",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3182F6),
                    modifier = Modifier.clickable { recFolderLauncher.launch(null) }.padding(vertical = 2.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    Text("정해둔 주기로", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))

    // ④ 정기 문자 예약 (링크)
    TossCard(onClick = onOpenRecurring) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE2F7F3)),
                contentAlignment = Alignment.Center) { Text("🔁", fontSize = 16.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("정기 문자 예약", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Text("방역·점검·안부 — 각 고객 날짜 기준 주기 발송", fontSize = 12.sp, color = TossTextTertiary)
            }
            Text("›", fontSize = 20.sp, color = TossTextTertiary)
        }
    }
    Spacer(Modifier.height(14.dp))

    // 받은 문자 알림 (보존) — 설명 명확화: 이건 '알림창'만 담당(AI 준비와 별개). (2026-07-16 사장님 혼동)
    TossCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("받은 문자 알림", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Text("고객 문자가 오면 알림창을 띄워요 (알림만 — AI 준비는 아래 스위치)", fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp)
            }
            Switch(checked = incomingNotifyOn, onCheckedChange = onIncomingNotifyToggle)
        }
    }
    Spacer(Modifier.height(14.dp))

    // AI 답변 준비 (2026-07-16 사장님) — 문자 오면 막내가 추천 답변을 미리 만들지 여부. '받은 문자 알림'과 별개.
    //   OFF = 마스코트 '답변 준비 중' 애니·홈 "AI 답변 준비 중"·서버 호출 전부 없음.
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFEDE9FE)),
                    contentAlignment = Alignment.Center) { Text("✨", fontSize = 15.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI 답변 준비", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Text(
                        if (aiPrepOn) "문자가 오면 막내가 추천 답변을 미리 만들어둬요 (문자방 열면 바로 보여요)"
                        else "꺼짐 — 추천을 안 만들어요. 마스코트 '준비 중' 애니도 안 떠요",
                        fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = aiPrepOn, onCheckedChange = {
                    aiPrepOn = it
                    prefs.aiReplyPrepEnabled = it
                })
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    // 광고·스팸 번호 앞자리 — 비주얼 정리(2026-06-14 사장님: 디자인 개선). 기능 동일.
    TossCard {
        Column {
            // 아이콘 헤더
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFFDECEC)),
                    contentAlignment = Alignment.Center
                ) { Text("🚫", fontSize = 15.sp) }
                Spacer(Modifier.width(10.dp))
                Text("광고·스팸 번호 앞자리", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            }
            Text(
                "이 앞자리로 시작하는 번호는 자동답장·AI 추천을 안 하고 신규 목록에서도 빼요.",
                fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(14.dp))

            // ── 등록된 앞자리 ──
            Text("등록된 앞자리", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
            Spacer(Modifier.height(8.dp))
            if (spamPrefixes.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("아직 등록한 앞자리가 없어요", fontSize = 12.5.sp, color = TossTextTertiary) }
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    spamPrefixes.sorted().forEach { p ->
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFFDECEC))
                                .border(1.dp, Color(0xFFF6C9C9), RoundedCornerShape(999.dp))
                                .clickable { spamPrefixes = spamPrefixes - p; prefs.spamPrefixes = spamPrefixes }
                                .padding(start = 13.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossError)
                            Spacer(Modifier.width(6.dp))
                            Text("✕", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TossError.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            // ── 추천 앞자리 ──
            val suggested = com.detailline.callfollowcrm.util.SpamPrefix.SUGGESTED.filter { it !in spamPrefixes }
            if (suggested.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("추천 앞자리 · 눌러서 추가", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggested.forEach { p ->
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
                                .border(1.dp, TossDivider, RoundedCornerShape(999.dp))
                                .clickable { spamPrefixes = spamPrefixes + p; prefs.spamPrefixes = spamPrefixes }
                                .padding(start = 10.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("＋", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                            Spacer(Modifier.width(5.dp))
                            Text(p, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                        }
                    }
                }
            }

            // ── 직접 입력 ──
            Spacer(Modifier.height(16.dp))
            Text("직접 입력", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    com.detailline.callfollowcrm.presentation.component.SheetTextField(
                        value = newSpamPrefix,
                        onValueChange = { newSpamPrefix = it.filter { c -> c.isDigit() }.take(6) },
                        placeholder = "예: 070",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (newSpamPrefix.isNotBlank()) TossBlue else TossGrayBg)
                        .clickable(enabled = newSpamPrefix.isNotBlank()) {
                            val p = newSpamPrefix
                            // 저장 됐는지 사장님이 헷갈리던 통점(2026-06-16): 추가 결과를 토스트로 분명히 알림 + 중복 안내.
                            if (p in spamPrefixes) {
                                android.widget.Toast.makeText(ctx, "‘$p’ 는 이미 등록돼 있어요", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                spamPrefixes = spamPrefixes + p
                                prefs.spamPrefixes = spamPrefixes   // .commit() = 즉시 저장
                                android.widget.Toast.makeText(ctx, "‘$p’ 저장됐어요 ✓ — 위 ‘등록된 앞자리’에 추가됐어요", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            newSpamPrefix = ""
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("추가", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (newSpamPrefix.isNotBlank()) Color.White else TossTextTertiary)
                }
            }
        }
    }
}

@Composable
private fun AutoCard(
    emoji: String,
    iconBg: Color,
    title: String,
    badge: String?,
    sub: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    initiallyExpanded: Boolean = false,
    body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    TossCard {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(iconBg),
                    contentAlignment = Alignment.Center) { Text(emoji, fontSize = 16.sp) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                        if (badge != null) {
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(TossBlueSoft)
                                .padding(horizontal = 6.dp, vertical = 1.dp)) {
                                Text(badge, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                            }
                        }
                    }
                    Text(sub, fontSize = 12.sp, color = TossTextTertiary)
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) { body() }
            }
        }
    }
}

@Composable
private fun AutoDotLabel(dotColor: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dotColor))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
    }
}

@Composable
private fun AutoTextArea(value: String, onChange: (String) -> Unit) {
    // 입력 즉시 prefs 에 자동 저장됨(별도 저장 버튼 없음). 저장된 줄 몰라 불안하다는 통점 → "✓ 저장됨" 잠깐 표시. (2026-06-18 사장님)
    var editTick by remember { mutableStateOf(0) }
    var showSaved by remember { mutableStateOf(false) }
    LaunchedEffect(editTick) {
        if (editTick == 0) return@LaunchedEffect
        showSaved = true
        kotlinx.coroutines.delay(1600)
        showSaved = false
    }
    Column {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); editTick++ },
            modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard, fontSize = 13.5.sp, color = TossTextPrimary)
        )
        Box(Modifier.padding(top = 5.dp, start = 2.dp)) {
            if (showSaved) {
                Text("✓ 저장됐어요", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossSuccess)
            } else {
                Text("입력하면 자동으로 저장돼요", fontSize = 11.5.sp, color = TossTextTertiary)
            }
        }
    }
}

@Composable
private fun AutoNote(text: String) {
    Text(text, fontSize = 11.5.sp, color = TossTextTertiary, lineHeight = 16.sp,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AutoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) TossBlue else TossGrayBg)
            .clickable { onClick() }.padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else TossTextSecondary)
    }
}

@Composable
private fun AfterCallCard(
    state: SettingsUiState,
    templates: List<MessageTemplateEntity>,
    onBehaviorChange: (AfterCallBehavior) -> Unit,
    onQuickActionChange: (Int, Long) -> Unit,
    onIncomingTemplateChange: (Long) -> Unit,
    onMissedTemplateChange: (Long) -> Unit,
    onAutoReplyToggle: (Boolean) -> Unit
) {
    TossCard {
        Column {
            SectionLabel("통화 종료 후 동작")
            Spacer(Modifier.height(10.dp))

            // 5-1. behavior 칩
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AfterCallBehavior.values().toList()) { b ->
                    TossChip(
                        text = b.label,
                        selected = state.afterCallBehavior == b,
                        onClick = { onBehaviorChange(b) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "기본값: 알림 표시. 전체화면 팝업은 사용하지 않아요.",
                style = MaterialTheme.typography.labelSmall,
                color = TossTextTertiary
            )

            if (state.afterCallBehavior == AfterCallBehavior.NOTIFY) {
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Divider(color = TossDivider)
                Spacer(Modifier.height(14.dp))

                // 5-2. 후속 알림 빠른 액션 (템플릿 3개)
                Text(
                    "후속 알림 빠른 액션",
                    style = MaterialTheme.typography.titleMedium,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "통화 종료 알림(시공막내 캐치)의 액션 버튼 3개. 탭하면 해당 템플릿 자동 선택된 채로 문자 화면 열림.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary
                )
                Spacer(Modifier.height(10.dp))
                if (templates.isEmpty()) {
                    Text(
                        "먼저 템플릿을 만들어주세요 (위의 \"문자 템플릿 → 템플릿 보기/편집\").",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossError
                    )
                } else {
                    TemplateDropdown(
                        label = "버튼 1",
                        templates = templates,
                        selectedId = state.quickActionTemplateId1,
                        onSelect = { onQuickActionChange(1, it) }
                    )
                    Spacer(Modifier.height(10.dp))
                    TemplateDropdown(
                        label = "버튼 2",
                        templates = templates,
                        selectedId = state.quickActionTemplateId2,
                        onSelect = { onQuickActionChange(2, it) }
                    )
                    Spacer(Modifier.height(10.dp))
                    TemplateDropdown(
                        label = "버튼 3",
                        templates = templates,
                        selectedId = state.quickActionTemplateId3,
                        onSelect = { onQuickActionChange(3, it) }
                    )
                }

                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Divider(color = TossDivider)
                Spacer(Modifier.height(14.dp))

                // 5-3. 처음 연락 자동 응답
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "처음 연락온 고객 자동 응답",
                            style = MaterialTheme.typography.titleMedium,
                            color = TossTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "첫 통화 종료 10초 뒤 자동 응대 문자 발송. 알림에서 취소 가능.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossTextSecondary
                        )
                    }
                    Switch(
                        checked = state.autoFirstReplyEnabled,
                        onCheckedChange = onAutoReplyToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TossBlue,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = TossTextTertiary
                        )
                    )
                }
                if (state.autoFirstReplyEnabled) {
                    Spacer(Modifier.height(12.dp))
                    if (templates.isEmpty()) {
                        Text(
                            "먼저 템플릿을 만들어주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossError
                        )
                    } else {
                        TemplateDropdown(
                            label = "수신 통화 첫 응대 템플릿",
                            templates = templates,
                            selectedId = state.firstReplyIncomingTemplateId,
                            onSelect = onIncomingTemplateChange
                        )
                        Spacer(Modifier.height(10.dp))
                        TemplateDropdown(
                            label = "부재중 통화 첫 응대 템플릿",
                            templates = templates,
                            selectedId = state.firstReplyMissedTemplateId,
                            onSelect = onMissedTemplateChange
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "주의: 자동 발송 SMS 는 통신사 요금이 부과될 수 있어요. 잘못된 번호 발송 위험 있으니 신중히.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TossError
                    )
                }
            }
        }
    }
}

/**
 * 사장님 톤 학습 — 시공막내 의 정체성 카드.
 * 숫자는 의도적으로 노출 안 함 (사장님 결정 2026-05-24) — 한계처럼 보이지 않게.
 * 본질 = "사장님 말투 그대로" 강조.
 */
@Composable
private fun OwnerToneCard(sampleCount: Int) {
    // sampleCount > 0 이면 "학습 중", 0 이면 "준비 중" 상태 표시
    val activated = sampleCount > 0
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "내 톤 학습",
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            // 상태 배지 — 숫자 대신 본질 한 줄.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (activated) TossBlueSoft else TossGrayBg)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (activated) TossSuccess else TossTextTertiary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (activated) "사장님 말투 학습 중" else "보낸 메시지가 쌓이면 자동 시작",
                        color = if (activated) TossBlue else TossTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "AI 가 답변 추천을 만들 때 사장님이 평소 보낸 메시지를 함께 참고해서, 봇 답변이 아니라 사장님 본인이 쓴 것처럼 자연스러운 답을 만들어요.",
                style = MaterialTheme.typography.bodySmall,
                color = TossTextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "서버 측 톤 학습 본격 동작은 맥미니 업그레이드 적용 후.",
                style = MaterialTheme.typography.labelSmall,
                color = TossTextTertiary
            )
        }
    }
}

/**
 * 2026-05-29 킬러콘텐츠 4단계 (Tone RAG) — 사장님 sent SMS 풀 batch upload 카드.
 *
 * 동의 → batch upload → 진행 표시 → 완료 후 "학습됨 N건" 표시 + 마지막 동기화 시각.
 * Mac mini 가 RAG endpoint 박혀야 실제 검색 활성화 — 그 전에 upload 만 해도 데이터 누적은 안전.
 *
 * 사장님 카피:
 *   메인: "✨ 깊이 학습 (Tone RAG)"
 *   설명: "사장님이 평소 보낸 모든 메시지를 자체 서버에 임베딩해서, AI 가 비슷한 상황의 사장님 말투를 직접 찾아 흉내냅니다."
 *   동의 안 함: "[동의 후 시작]" 버튼
 *   동의 했으나 안 함: "[지금 동기화] N건 대기"
 *   진행 중: progress bar + "x / y 건 학습 중"
 *   완료: "✅ 학습됨 N건 (오늘 14:30)"
 */
@Composable
private fun OwnerToneRagCard(
    consented: Boolean,
    uploadedCount: Int,
    lastUploadedAtMs: Long,
    available: Int,
    inProgress: Boolean,
    progress: Pair<Int, Int>?,
    /** cowork §16 — bge-m3 + sqlite-vec install 여부. null = 한 번도 upload 안 함, false = 설치 필요. */
    embeddingsAvailable: Boolean?,
    onConsentAndUpload: () -> Unit,
    onUpload: () -> Unit
) {
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧠", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "깊이 학습 (Tone RAG)",
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "사장님이 평소 보낸 모든 메시지를 자체 서버에 임베딩해서, AI 가 비슷한 상황의 사장님 말투를 직접 찾아 흉내냅니다. 더 사장님답게.",
                style = MaterialTheme.typography.bodySmall,
                color = TossTextSecondary
            )
            Spacer(Modifier.height(12.dp))

            if (inProgress) {
                // 진행 중 — progress bar + "x / y"
                val (sent, total) = progress ?: (0 to 0)
                val frac = if (total <= 0) 0f else (sent.toFloat() / total).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TossGrayBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(frac)
                            .clip(RoundedCornerShape(4.dp))
                            .background(TossBlue)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "$sent / $total 건 학습 중...",
                    fontSize = 11.sp,
                    color = TossTextSecondary
                )
            } else if (uploadedCount > 0) {
                // 완료 — 학습됨 표시
                val lastTime = if (lastUploadedAtMs > 0) {
                    val sdf = java.text.SimpleDateFormat("M월 d일 HH:mm", java.util.Locale.KOREAN)
                    sdf.format(java.util.Date(lastUploadedAtMs))
                } else "—"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TossBlueSoft)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            "✅ 학습됨 ${formatThousands(uploadedCount)}건",
                            color = TossBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "마지막 동기화 $lastTime",
                            color = TossTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                if (embeddingsAvailable == false) {
                    // cowork §16 — Mac mini 에 의존성 install 안 됐을 때 사장님 안내.
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3DF))
                            .padding(12.dp)
                    ) {
                        Text(
                            "⚠️ 임베딩 검색 비활성 — Mac mini 에 'pip install FlagEmbedding sqlite-vec' 후 launchctl reload 필요. " +
                                "메시지는 저장되어 있어 install 후 자동 활성화됩니다.",
                            fontSize = 11.sp,
                            color = Color(0xFFB8780A)
                        )
                    }
                }
                if (available > uploadedCount) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "새 메시지 ${available - uploadedCount}건 대기",
                        fontSize = 11.sp,
                        color = TossTextSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    TossPrimaryButton(
                        text = "지금 동기화",
                        onClick = onUpload
                    )
                }
            } else if (consented) {
                // 동의 했지만 한 번도 안 함
                Text(
                    "${formatThousands(available)}건 학습 대기 중",
                    fontSize = 12.sp,
                    color = TossTextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                TossPrimaryButton(
                    text = "지금 학습 시작",
                    onClick = onUpload
                )
            } else {
                // 동의 전 — 첫 진입
                Text(
                    "동의하면 사장님이 보낸 메시지 ${formatThousands(available)}건이 자체 Mac mini 서버 (사장님 본인 데이터, 외부 전송 X) 로 전송돼요.",
                    fontSize = 11.sp,
                    color = TossTextTertiary
                )
                Spacer(Modifier.height(10.dp))
                TossPrimaryButton(
                    text = "동의하고 학습 시작",
                    onClick = onConsentAndUpload
                )
            }
        }
    }
}

/**
 * 수신 SMS 가 오면 시공막내 자체 알림 + 빠른 답장 + AI 추천 답변 — 갤메시지 대체.
 * 사장님 결정 2026-05-25: 옵션 A. 갤메시지 알림은 시스템 설정에서 직접 꺼야.
 */
@Composable
private fun IncomingSmsNotifyCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📩", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "수신 문자 알림",
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "시공막내 가 갤메시지보다 풍부한 알림을 띄워요. AI 추천 답변 칩 + 빠른 답장 + 한 탭 전화.",
                style = MaterialTheme.typography.bodySmall,
                color = TossTextSecondary
            )
            Spacer(Modifier.height(10.dp))
            // 갤메시지 알림 끄기 안내 — 안 끄면 알림 두 번 떠서 거슬림.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF3DF))  // 연한 노랑 — 안내 톤
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        "⚠️ 갤메시지 알림은 직접 꺼주세요",
                        color = Color(0xFFB8780A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "안 끄면 시공막내 + 갤메시지 둘 다 알림이 떠요. 시스템 설정 → 앱 → 메시지 → 알림 OFF.",
                        color = Color(0xFFB8780A),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.TextButton(
                        onClick = {
                            runCatching {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                ).apply {
                                    putExtra(
                                        android.provider.Settings.EXTRA_APP_PACKAGE,
                                        "com.samsung.android.messaging"
                                    )
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                ctx.startActivity(intent)
                            }.onFailure {
                                // 갤메시지가 패키지명 다를 수 있음 — 일반 알림 설정으로 fallback
                                runCatching {
                                    val fallback = android.content.Intent(
                                        android.provider.Settings.ACTION_SETTINGS
                                    ).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    ctx.startActivity(fallback)
                                }
                            }
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text(
                            "→ 갤메시지 알림 설정 열기",
                            color = Color(0xFFB8780A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 기본 네비 앱 선택 — 카드 펼침 [📍 길찾기] 가 1탭으로 launch 할 외부 앱.
 * 2026-05-27 사장님 결정: 사용자마다 손에 익은 네비가 다르므로 3개 옵션 (카카오내비/네이버지도/티맵).
 * 미선택 상태로 두면 첫 길찾기 탭 시 동일 다이얼로그가 자동으로 뜸.
 */
@Composable
private fun NavAppPreferenceCard(
    selectedKey: String?,
    onSelect: (String?) -> Unit
) {
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧭", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "기본 네비 앱",
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "고객 카드를 펼치면 [📍 길찾기] 가 보여요. 누르면 여기서 고른 앱으로 바로 안내가 시작돼요.",
                style = MaterialTheme.typography.bodySmall,
                color = TossTextSecondary
            )
            Spacer(Modifier.height(12.dp))

            // 3개 옵션 가로 chip — 토스 식 선택.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.detailline.callfollowcrm.util.NavApp.values().forEach { app ->
                    val selected = selectedKey == app.key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) TossBlue else Color(0xFFEEF1F4))
                            .clickable { onSelect(app.key) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            app.label,
                            color = if (selected) Color.White else TossTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (selectedKey == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "아직 미선택 — 첫 길찾기 탭하면 같은 선택지가 떠요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TossTextTertiary
                )
            }
        }
    }
}

/**
 * 시공막내 알림이 왜 안 뜨는지 사장님이 직접 진단 — 권한/채널 상태 한눈에.
 * 2026-05-25 사장님 보고: 갤메시지 알림 끄고 새 빌드 깔았는데도 안 뜸 → 진단 필요.
 */
@Composable
private fun NotificationDiagnosticCard() {
    val ctx = LocalContext.current
    // 간소화(2026-07-02 사장님) — 기술적 권한 체크리스트 제거(온보딩 권한 흐름과 중복). 정말 중요한 채팅+ 안내만.
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📷", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "고객 사진(문자) 잘 받기",
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))

            // 채팅+ (Samsung RCS) 안내 — 자동 감지 불가, 항상 표시.
            //   채팅+ 가 켜져 있으면 문자·사진이 IP 기반 RCS 로 라우팅되어 앱이 못 받는다(고객 사진 유실).
            //   삼성/구글 RCS provider 는 외부 앱에 비공개라 흡수 불가 — 사장님이 직접 끄도록 안내.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TossError.copy(alpha = 0.08f))
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "갤메시지 \"채팅+\" 끄기 (필수)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TossError,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "채팅+ 가 켜져 있으면 고객이 보낸 문자·사진이 갤메시지로만 가서 시공막내가 못 받아요. " +
                            "갤메시지 ≡ → 설정 → 채팅 기능 → 채팅+ 끄기.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextPrimary
                    )
                    androidx.compose.material3.TextButton(
                        onClick = { openSamsungMessagesApp(ctx) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text(
                            "→ 갤메시지 열기",
                            color = TossBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, ok: Boolean, fixLabel: String, onFix: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(if (ok) "✅" else "❌", fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) TossTextPrimary else TossError,
                fontWeight = if (ok) FontWeight.Medium else FontWeight.SemiBold
            )
            if (!ok) {
                androidx.compose.material3.TextButton(
                    onClick = onFix,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(fixLabel, color = TossBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun openAppNotificationSettings(ctx: android.content.Context) {
    runCatching {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
        ).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.startActivity(intent)
    }
}

private fun openChannelSettings(ctx: android.content.Context, channelId: String) {
    runCatching {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
        ).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.startActivity(intent)
    }.onFailure {
        openAppNotificationSettings(ctx)
    }
}

private fun openAppPermissionSettings(ctx: android.content.Context) {
    runCatching {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${ctx.packageName}")
        ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
        ctx.startActivity(intent)
    }
}

/**
 * 갤메시지 앱을 launch 한다. 채팅+ 설정 화면은 외부 앱에서 직접 deep link 불가 —
 *   사장님이 메시지 앱 ≡ → 설정 → 채팅 기능 → 채팅+ 끄도록 안내.
 * 갤메시지 없으면 구글 메시지 fallback. 둘 다 없으면 (이론상 거의 없음) 무동작.
 */
/**
 * Compose 의 LocalContext 가 ContextThemeWrapper 로 감싸진 경우가 있어 직접 cast 실패.
 *   baseContext 를 따라 내려가며 Activity 를 찾아야 안전.
 */
private fun android.content.Context.findActivityOrNull(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private fun openSamsungMessagesApp(ctx: android.content.Context) {
    runCatching {
        val pm = ctx.packageManager
        val intent = pm.getLaunchIntentForPackage("com.samsung.android.messaging")
            ?: pm.getLaunchIntentForPackage("com.google.android.apps.messaging")
        if (intent != null) {
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
        }
    }
}

@Composable
private fun AppFooter() {
    // 버전 + '빌드 날짜' — 사장님이 "지금 깐 게 최신인지" 한눈에 비교용. (2026-06-21 사장님)
    //   versionName 은 "0.2.{빌드번호}" 라 빌드마다 바뀜. 그래도 숫자라 와닿게 빌드 날짜·시각도 같이 표기.
    val builtAt = remember {
        runCatching {
            java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.KOREA)
                .format(java.util.Date(com.detailline.callfollowcrm.BuildConfig.BUILD_TIMESTAMP))
        }.getOrDefault("")
    }
    val linkCtx = androidx.compose.ui.platform.LocalContext.current
    // 동의문·처리방침은 앱 내 웹뷰로 — 크롬 없는 기기에서도 항상 열림. (2026-07-11)
    val openLink: (String) -> Unit = { url ->
        com.detailline.callfollowcrm.presentation.screen.web.DocWebViewActivity.open(
            linkCtx, url, com.detailline.callfollowcrm.presentation.screen.web.DocWebViewActivity.titleFor(url)
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 동의·정책 링크 3종 — 필수 동의문 / 선택 동의문 / 처리방침. (추가97 2026-07-06 cowork)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("개인정보 수집·이용", color = TossTextTertiary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { openLink(com.detailline.callfollowcrm.AppConfig.CONSENT_REQUIRED_URL) }.padding(4.dp))
            Text("·", color = TossTextTertiary, fontSize = 11.5.sp, modifier = Modifier.padding(horizontal = 2.dp))
            Text("품질 향상 동의", color = TossTextTertiary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { openLink(com.detailline.callfollowcrm.AppConfig.CONSENT_OPTIONAL_URL) }.padding(4.dp))
            Text("·", color = TossTextTertiary, fontSize = 11.5.sp, modifier = Modifier.padding(horizontal = 2.dp))
            Text("처리방침", color = TossTextTertiary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { openLink(com.detailline.callfollowcrm.AppConfig.PRIVACY_POLICY_URL) }.padding(4.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "시공막내 버전 ${com.detailline.callfollowcrm.BuildConfig.VERSION_NAME}",
            color = TossTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        if (builtAt.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                "$builtAt 빌드",
                color = TossTextTertiary,
                fontSize = 11.5.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "이 빌드 날짜가 '최신'과 같으면 업데이트된 거예요",
                color = TossTextTertiary,
                fontSize = 10.5.sp
            )
        }
    }
}

/**
 * 템플릿 선택 드롭다운. 라벨 + 현재 선택 라벨 + 펼치면 활성 템플릿 리스트.
 * 미선택 (-1L) 상태도 허용 — "선택 안 함" 옵션 포함.
 */
@Composable
private fun TemplateDropdown(
    label: String,
    templates: List<MessageTemplateEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = templates.firstOrNull { it.id == selectedId }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = TossTextSecondary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(BorderStroke(1.dp, TossDivider), RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                selected?.title ?: "선택 안 함",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected != null) TossTextPrimary else TossTextTertiary,
                fontWeight = if (selected != null) FontWeight.Medium else FontWeight.Normal
            )
            Text("▾", color = TossTextTertiary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "선택 안 함 (이 케이스는 자동 발송 X)",
                        color = TossTextTertiary
                    )
                },
                onClick = {
                    onSelect(-1L)
                    expanded = false
                }
            )
            templates.forEach { t ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(t.title, color = TossTextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                t.body.lineSequence().firstOrNull().orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = TossTextSecondary,
                                maxLines = 1
                            )
                        }
                    },
                    onClick = {
                        onSelect(t.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 더보기 상단 막내 비서 카드 (프로토 .agent-card 간소화).
 *   캐릭터(Mascot) + 이름 + 학습 안내. 학습 수치는 실제 보유값(사장님 톤 샘플 수).
 */
/** 10레벨 구간마다 캐릭터 '변신' 엠블럼 (정식 그림 전, 우선 배지로 표시). (2026-06-14) */
private val AGENT_EMBLEMS = listOf("🌱", "🐣", "🔧", "⭐", "🔥", "💪", "🏅", "👑", "💎", "🚀")

@Composable
private fun AgentMiniCard(card: AgentCardState, onClick: (() -> Unit)? = null) {
    // 프로토 agent-card — 그라데이션 카드 + mascot + 레벨칩 + 말투 진행바 + stats.
    //   2026-06-17 사장님: 카드를 누르면 '내 말투 학습'으로 들어가게(흐름 자연스럽게).
    val agentInteraction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(agentInteraction) else Modifier)
            .tossCardShadow(RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .then(if (onClick != null) Modifier.clickable(interactionSource = agentInteraction, indication = null, onClick = onClick) else Modifier)
            .background(Brush.linearGradient(listOf(Color(0xFFEAF2FF), Color(0xFFF1ECFF))))
            .border(1.dp, Color(0xFFE6EAFB), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 막내 + 단계 변신 엠블럼(우하단 배지)
            Box(contentAlignment = Alignment.Center) {
                Mascot(sizeDp = 56.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(999.dp)).background(Color.White)
                        .border(1.dp, Color(0xFFE6EAFB), RoundedCornerShape(999.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(AGENT_EMBLEMS[card.tier.coerceIn(0, AGENT_EMBLEMS.lastIndex)], fontSize = 14.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // agent-name + lv 칩
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("막내 비서", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                    Spacer(Modifier.width(7.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF3182F6), Color(0xFF7C5CFC))))
                            .padding(horizontal = 9.dp, vertical = 2.dp)
                    ) {
                        Text("Lv.${card.level} · ${card.title}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text("\"${card.line}\"", fontSize = 13.sp, color = TossTextSecondary, fontWeight = FontWeight.SemiBold)
                // grow-row (말투 진행바)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 11.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color.White)
                    ) {
                        if (card.tonePct > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth((card.tonePct / 100f).coerceIn(0.03f, 1f))
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF3182F6), Color(0xFF7C5CFC))))
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("말투 ${card.tonePct}%", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                }
            }
        }
        Text(
            "함께한 상담 ${card.consultCount}건 · 시공 완료 ${card.doneJobs}건" +
                if (card.toNextLevel > 0) " · 다음 레벨까지 ${card.toNextLevel} XP" else " · 최고 레벨 🎉",
            fontSize = 11.5.sp, color = TossTextTertiary, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 9.dp)
        )
    }
}

/** 프로토 더보기 섹션 (라벨 + lockcard 묶음). */
@Composable
private fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    Column {
        SectionLabel(label)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** 내 데이터 지키기 — 내보내기(백업) + 가져오기(복원) 섹션. (데이터 안전 1단계, 2026-08-10 사장님) */
@Composable
private fun DataBackupSection(
    lastBackupAt: Long,
    busy: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onServerBackup: () -> Unit,
    onServerRestore: () -> Unit,
    onRestoreCategories: () -> Unit
) {
    val lastLabel = remember(lastBackupAt) {
        if (lastBackupAt <= 0L) "아직 없음"
        else {
            // '오늘/어제'만으론 아침에 백업하고 낮에 고객·일정 더 쌓인 걸 놓침("오늘 했으니 안전" 착각).
            //   → 정확한 날짜+시각까지 보여줘 '언제까지' 백업됐는지 분명히. (2026-08-30 사장님)
            val d = java.util.Date(lastBackupAt)
            val day = (System.currentTimeMillis() - lastBackupAt) / (24L * 60 * 60 * 1000)
            val datePart = when {
                day <= 0L -> "오늘"
                day == 1L -> "어제"
                else -> java.text.SimpleDateFormat("M월 d일", java.util.Locale.KOREA).format(d)
            }
            val timePart = java.text.SimpleDateFormat("a h:mm", java.util.Locale.KOREA).format(d)
            "$datePart $timePart"
        }
    }
    val recent = lastBackupAt > 0L && System.currentTimeMillis() - lastBackupAt < 14L * 24 * 60 * 60 * 1000

    Column {
        SectionLabel("내 데이터 지키기")
        Spacer(Modifier.height(8.dp))

        // 경고(백업 없음/오래됨) 또는 안심(최근 백업) 배너
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (recent) Color(0xFFE7F8F0) else Color(0xFFFFF3DF))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(if (recent) "✅" else "⚠️", fontSize = 15.sp)
            Text(
                if (recent) "최근에 서버에 백업했어요. 폰을 바꾸거나 앱을 지워도 되살릴 수 있어요."
                else "고객·돈 장부는 이 폰에만 저장돼요. 폰을 바꾸거나 앱을 지우면 되살릴 수 없어요. 가끔 아래 [서버에 백업]을 눌러두세요.",
                color = if (recent) Color(0xFF0E9F56) else Color(0xFFB8780A),
                fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(9.dp))

        // ☁️ 서버에 백업 — 파일 없이 서버에 안전 보관 → 폰 바꿔도/지워도 복원.
        //   (2026-08-21 사장님 · 로컬 파일 내보내기/가져오기는 제거 2026-08-31 사장님 "서버 백업만")
        Column(
            Modifier.fillMaxWidth()
                .tossCardShadow(RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF1E6E6A))
                    .clickable(enabled = !busy) { onServerBackup() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                if (busy) androidx.compose.material3.CircularProgressIndicator(
                    color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)
                ) else Text("☁️  서버에 백업하기", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("마지막 백업: $lastLabel", fontSize = 12.5.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
                Text(
                    "서버에서 복원하기",
                    fontSize = 13.sp, color = Color(0xFF1E6E6A), fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !busy) { onServerRestore() }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                )
            }
            Text(
                "파일 없이 서버에 안전하게 보관해요. 폰을 바꾸거나 앱을 지워도, 새 폰에서 '서버에서 복원'으로 되살려요. (사진 제외)",
                fontSize = 11.5.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium, lineHeight = 16.sp
            )
            // 카테고리·태그만 복원 — 일당 등 카테고리가 사라졌을 때, 다른 데이터는 안 되돌리고 태그만. (2026-09-01 사장님)
            Text(
                "🏷️ 카테고리·태그만 복원 (일당 등)",
                fontSize = 12.5.sp, color = TossBlue, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !busy) { onRestoreCategories() }
                    .padding(horizontal = 8.dp, vertical = 9.dp)
            )
        }
    }
}

/**
 * 프로토 .lockcard — 아이콘 박스(42·radius13) + 제목·부제 + 꺾쇠/티어태그.
 *   tier: null=꺾쇠 / "프로"(파랑) / "비즈니스"(보라). locked=true → opacity .6.
 */
@Composable
private fun LockRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    tier: String? = null,
    locked: Boolean = false,
    onClick: () -> Unit
) {
    val rowInteraction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(rowInteraction)
            .graphicsLayer { alpha = if (locked) 0.6f else 1f }
            .tossCardShadow(RoundedCornerShape(18.dp))
            .background(Color.White, RoundedCornerShape(18.dp))
            .clickable(interactionSource = rowInteraction, indication = null, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).background(iconBg, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = TossTextTertiary)
            }
        }
        when (tier) {
            "프로" -> TierTag("프로", TossBlue)
            "비즈니스" -> TierTag("비즈니스", Color(0xFF7C5CFC))
            else -> Icon(Icons.Filled.ChevronRight, null, tint = TossTextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

/** 프로토 .tier-tag — 11px w800, padding 4x10, radius999. 프로=파랑/비즈니스=보라, 글자 흰색. */
@Composable
private fun TierTag(label: String, bg: Color) {
    Text(
        label,
        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp)).background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// ════════════════════════ 프로토 setup-check (시작 체크) ════════════════════════
/**
 * 프로토 renderSetupCheck — 시작 체크리스트. 실제 상태로 채움 (가짜 done 없음). ON_RESUME 마다 재검사.
 *   필수: 기본 메시지 앱 / 알림 권한.
 *   추천(2026-07-28 확장): 통화 녹음 연결 / 가격표 / 자주 쓰는 답장 — 온보딩 마법사에서 "나중에" 누른 항목을
 *     홈에서 다시 권유. done 감지 = AdotFolderScanner.isConnected / pricingCount>0 / templateCount>0.
 *   다 되면 "시작 준비 다 됐어요" 한 줄로 접힘.
 */
@Composable
private fun SetupCheckCard(
    preferences: com.detailline.callfollowcrm.data.preferences.AppPreferences,
    templateCount: Int,
    pricingCount: Int,
    onOpenTemplates: () -> Unit,
    onOpenPricingItems: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var refresh by remember { mutableStateOf(0) }
    // 녹음 "연결" 눌러 스캔했는데 0개면 = 우리가 못 잡는 것 → 진단 보내기 노출. (2026-07-29 사장님)
    var recScanFailed by remember { mutableStateOf(false) }

    // 설정 다녀오면 반영 — ON_RESUME 마다 재검사.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val smsDefault = remember(refresh) {
        runCatching { com.detailline.callfollowcrm.util.DefaultSmsAppHelper.isCurrentDefault(context) }.getOrDefault(false)
    }
    val notiOn = remember(refresh) {
        runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(false)
    }
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    // 통화 녹음 연결 상태 — 폴더 직접 연결 or 자동찾기(권한+opt-in). ON_RESUME/스캔 후 재검사.
    val recConnected = remember(refresh) {
        runCatching { com.detailline.callfollowcrm.recording.AdotFolderScanner.isConnected(context) }.getOrDefault(false)
    }
    // "녹음 연결" 액션 = 온보딩 마법사 doScan 과 동일: 오디오 권한 → MediaStore 자동찾기 켜기 → 개수 토스트.
    val scanner = com.detailline.callfollowcrm.recording.AdotFolderScanner
    val runRecordingScan: () -> Unit = {
        scanner.enableMediaStore(context)
        val n = runCatching { scanner.countMediaStoreCandidates(context) }.getOrDefault(0)
        refresh++
        recScanFailed = n == 0
        if (n > 0) Toast.makeText(context, "녹음 ${n}개를 찾았어요 🎉 통화가 끝나면 자동 요약돼요", Toast.LENGTH_LONG).show()
        else Toast.makeText(context, "아직 녹음이 없어요. 통화 녹음을 먼저 켜주세요 (전화 설정)", Toast.LENGTH_LONG).show()
    }
    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) runRecordingScan() else Toast.makeText(context, "녹음 접근 권한이 필요해요", Toast.LENGTH_SHORT).show() }
    val connectRecording: () -> Unit = {
        if (scanner.hasAudioPermission(context)) runRecordingScan()
        else runCatching { audioLauncher.launch(scanner.audioPermission()) }
    }

    data class SetupStep(val label: String, val done: Boolean, val actionLabel: String = "설정", val action: () -> Unit)
    // 2026-07-05 "기본 문자 앱 지정" 권장 단계 부활. (2026-06-18 엔 기본앱=MMS유실을 "통신사 한계"로 오인해
    //   제거했으나, 실은 klinker 수신 버그였고 안드로이드 공식 API 로 해결됨 — commit 01136a2, S23U/KT 검증.)
    //   Play 는 문자 읽기/보내기 권한에 "기본 SMS 핸들러" 자격을 요구 → 이 단계로 사용자·구글 심사자가 지정 가능.
    //   건너뛰기 가능(권장일 뿐, done 안 돼도 다른 기능은 동작).
    val steps = listOf(
        SetupStep("기본 문자 앱 지정 (자동문자·사진 받기)", smsDefault) {
            val act = activity
            if (act == null) {
                Toast.makeText(context, "잠시 후 다시 시도해주세요", Toast.LENGTH_SHORT).show()
            } else {
                val intent = com.detailline.callfollowcrm.util.DefaultSmsAppHelper.createRequestIntent(act)
                if (intent != null) {
                    runCatching { roleLauncher.launch(intent) }
                        .onFailure { Toast.makeText(context, "기본 문자앱 다이얼로그를 열 수 없어요", Toast.LENGTH_SHORT).show() }
                } else {
                    Toast.makeText(context, "이 기기는 기본 문자앱 전환을 지원하지 않아요", Toast.LENGTH_LONG).show()
                }
            }
        },
        SetupStep("알림 권한", notiOn) {
            runCatching {
                val i = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }
        },
        // 추천 연결 항목 — 마법사에서 "나중에" 눌러도 여기서 재권유. 실제 데이터 감지되면 초록 체크로 사라짐.
        SetupStep("통화 녹음 연결 · 통화 끝나면 자동 요약", recConnected, actionLabel = "연결") { connectRecording() },
        SetupStep("가격표 만들기 · 견적 자동 완성", pricingCount > 0, actionLabel = "만들기") { onOpenPricingItems() },
        SetupStep("자주 쓰는 답장 만들기", templateCount > 0, actionLabel = "만들기") { onOpenTemplates() }
    )
    val doneN = steps.count { it.done }
    val total = steps.size
    val all = doneN == total
    var collapsed by remember { mutableStateOf(true) }

    if (all && collapsed) {
        val checkInteraction = remember { MutableInteractionSource() }
        Row(
            Modifier.fillMaxWidth()
                .pressScale(checkInteraction)
                .tossCardShadow(RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAFBF2))
                .clickable(interactionSource = checkInteraction, indication = null) { collapsed = false }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckDot(true)
            Spacer(Modifier.width(8.dp))
            Text("시작 준비 다 됐어요 ($doneN/$total)", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = TossSuccess, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null, tint = TossTextTertiary, modifier = Modifier.size(18.dp))
        }
        return
    }
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("시작 체크 ($doneN/$total)", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = TossTextSecondary, modifier = Modifier.weight(1f))
                if (all) Text("접기 ▲", fontSize = 12.sp, color = TossTextTertiary,
                    modifier = Modifier.clickable { collapsed = true })
            }
            Spacer(Modifier.height(10.dp))
            steps.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                    CheckDot(s.done)
                    Spacer(Modifier.width(9.dp))
                    Text(s.label, fontSize = 14.sp, color = TossTextPrimary, modifier = Modifier.weight(1f))
                    if (!s.done) Text(s.actionLabel, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        color = TossBlue, modifier = Modifier.clickable { s.action() })
                }
            }
            // 녹음 "연결" 눌러도 계속 0개면 원인 자동 진단(파일없음/파서미스 + 가린 파일명). (2026-07-29 사장님)
            if (recScanFailed) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F2F5)))
                com.detailline.callfollowcrm.presentation.component.InlineDiagPrompt(
                    prefs = preferences,
                    tag = "홈-녹음연결(0개)",
                    prompt = "녹음이 계속 안 잡히나요?",
                    buildExtra = { com.detailline.callfollowcrm.recording.AdotFolderScanner.recordingDiag(context) }
                )
            }
        }
    }
}

@Composable
private fun CheckDot(done: Boolean) {
    Box(
        Modifier.size(20.dp).clip(RoundedCornerShape(50))
            .background(if (done) TossSuccess else Color(0xFFE2E6EC)),
        contentAlignment = Alignment.Center
    ) {
        if (done) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
        else Icon(Icons.Filled.Add, null, tint = TossTextTertiary, modifier = Modifier.size(13.dp))
    }
}

// ════════════════════════ 프로토 renderTone (내 말투 학습) ════════════════════════
/**
 * 프로토 renderTone 1:1 — hero·토글·분석·비교·재료·가르치기·개인정보.
 *   tonePct/uploadedCount = 실제 파생값 (가짜 % 없음). 분석/비교는 서버(/api/tone/profile) 연결 전까지 placeholder.
 *   Tone RAG 업로드는 "무엇으로 배우나요 → 내가 보낸 문자" 에 녹여 동작 유지.
 */
@Composable
private fun ToneLearnProtoSection(
    container: AppContainer,
    profile: com.detailline.callfollowcrm.ai.ToneProfile?,
    tonePct: Int,
    ragUploadedCount: Int,
    ragAvailable: Int,
    ragSyncedUpTo: Int,
    ragConsented: Boolean,
    ragUploading: Boolean,
    ragProgress: Pair<Int, Int>?,
    onConsentAndUpload: () -> Unit,
    onUpload: () -> Unit
) {
    val prefs = container.preferences
    val learnedCount = profile?.sampleCount ?: ragUploadedCount
    var toneOn by remember { mutableStateOf(prefs.toneLearnEnabled) }
    var examplesCount by remember { mutableStateOf(prefs.toneExamples.size) }
    var signature by remember { mutableStateOf(prefs.toneSignature) }
    var showExampleDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    val pct = if (toneOn) tonePct else 0

    // ── tone-hero (보라 그라데이션) ──
    Column(
        Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7C5CFC), Color(0xFF5B3FE0))))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(sizeDp = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$pct", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("% 학습", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                        modifier = Modifier.padding(start = 1.dp, bottom = 3.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (toneOn) "막내 비서가 사장님 말투를 ${tonePct}% 따라 해요"
                    else "학습이 꺼져 있어요. 켜면 다시 배우기 시작해요",
                    fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(15.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth((pct / 100f).coerceIn(0f, 1f)).height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFFFD479), Color(0xFFFFB43E)))))
        }
        Spacer(Modifier.height(9.dp))
        Text("내가 보낸 문자 ${formatThousands(learnedCount)}개 학습",
            fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.72f), fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(12.dp))

    // ── 말투 학습 켜짐/꺼짐 (tone-on) ──
    TossCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFF1ECFF)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFF7C5CFC), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("말투 학습 ${if (toneOn) "켜짐" else "꺼짐"}", fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (toneOn) "추천 답변이 사장님 말투로 만들어지고, 채팅에 ✨내 말투 배지가 붙어요"
                    else "추천 답변이 일반 AI 말투로 나와요",
                    fontSize = 12.sp, color = TossTextTertiary
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = toneOn,
                onCheckedChange = { toneOn = it; prefs.toneLearnEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF7C5CFC)
                )
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    // ── AI가 분석한 사장님 말투 (서버 §21 traits) ──
    ToneSecSub("AI가 분석한 사장님 말투")
    val traits = profile?.traits.orEmpty()
    if (profile?.analyzed == true && traits.isNotEmpty()) {
        ToneTraits(traits)
    } else {
        TonePlaceholder(
            if (profile != null) "보낸 문자가 더 쌓이면 사장님 말끝·이모티콘·길이·호칭을 분석해 보여드려요."
            else "서버에서 말투 분석을 불러오는 중이에요…"
        )
    }
    Spacer(Modifier.height(16.dp))

    // ── 같은 질문, 이렇게 달라져요 (서버 §21 example) ──
    ToneSecSub("같은 질문, 이렇게 달라져요")
    val ex = profile?.example
    if (ex != null) {
        ToneBeforeAfter(ex)
    } else {
        TonePlaceholder(
            if (profile != null) "보낸 문자가 더 쌓이면 일반 AI 답변과 사장님 말투를 비교해 보여드려요."
            else "서버에서 비교 예시를 불러오는 중이에요…"
        )
    }
    Spacer(Modifier.height(16.dp))

    // ── 무엇으로 배우나요 (sources) + RAG 녹이기 ──
    ToneSecSub("무엇으로 배우나요")
    TossCard {
        Column {
            ToneSourceRow(Icons.AutoMirrored.Filled.Send, "내가 보낸 문자",
                "실제 고객에게 보낸 답장에서 말투를 배워요", formatThousands(ragUploadedCount))
            Spacer(Modifier.height(10.dp))
            when {
                ragUploading -> {
                    val (sent, totalN) = ragProgress ?: (0 to 0)
                    val frac = if (totalN <= 0) 0f else (sent.toFloat() / totalN).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(TossGrayBg)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(4.dp)).background(Color(0xFF7C5CFC)))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("$sent / $totalN 건 학습 중...", fontSize = 11.sp, color = TossTextSecondary)
                }
                ragUploadedCount == 0 && !ragConsented -> {
                    Text("동의하면 보낸 메시지 ${formatThousands(ragAvailable)}건이 사장님 전용 서버로 올라가 말투를 배워요. (외부 전송 X)",
                        fontSize = 11.sp, color = TossTextTertiary)
                    Spacer(Modifier.height(8.dp))
                    TossPrimaryButton(text = "동의하고 학습 시작", onClick = onConsentAndUpload)
                }
                ragUploadedCount == 0 && ragConsented -> {
                    Text("${formatThousands(ragAvailable)}건 학습 대기 중", fontSize = 12.sp, color = TossTextSecondary)
                    Spacer(Modifier.height(8.dp))
                    TossPrimaryButton(text = "지금 학습 시작", onClick = onUpload)
                }
                else -> {
                    // "대기" = 마지막 동기화 이후 폰에 새로 쌓인 보낸문자. (available − 동기화완료선)
                    //   서버가 빈/중복 문자를 걸러 uploadedCount 가 안 늘어도, 한 번 동기화하면 대기가 0이 된다.
                    //   (옛 버그: available − uploadedCount 라 걸러진 만큼 "N건 대기"가 영영 안 닫혀 "동기화해도 변동 없음".)
                    val pending = (ragAvailable - maxOf(ragUploadedCount, ragSyncedUpTo)).coerceAtLeast(0)
                    if (pending > 0) {
                        Text("새 메시지 ${pending}건 대기", fontSize = 11.sp, color = TossTextSecondary)
                        Spacer(Modifier.height(6.dp))
                        Text("지금 동기화", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C5CFC), modifier = Modifier.clickable { onUpload() })
                    } else {
                        Text("✅ 최신 상태로 학습됨", fontSize = 12.sp, color = TossSuccess, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
            Spacer(Modifier.height(14.dp))
            ToneSourceRow(Icons.Filled.Description, "직접 가르친 예문",
                "\"이렇게 답해줘\" 하고 알려준 문장", "$examplesCount")
        }
    }
    Spacer(Modifier.height(16.dp))

    // ── 직접 가르치기 (teach) ──
    ToneSecSub("직접 가르치기")
    ToneTeachButton(Icons.Filled.Add, "예문 추가하기", "\"이런 상황엔 이렇게 답해줘\" 알려주기") { showExampleDialog = true }
    Spacer(Modifier.height(8.dp))
    ToneTeachButton(Icons.AutoMirrored.Filled.Chat, "말투 세부 설정",
        if (signature.isBlank()) "꼭 쓰는 인사말 등록" else "시그니처: $signature") { showSignatureDialog = true }
    Spacer(Modifier.height(14.dp))

    // ── info-note (개인정보) ──
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("🔒", fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text("내 문자는 사장님 계정에서 말투 학습에만 쓰이고, 다른 곳에 공유되지 않아요.",
            fontSize = 12.sp, color = TossTextSecondary, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(18.dp))

    if (showExampleDialog) {
        ToneInputDialog(
            title = "예문 추가",
            sub = "이런 상황엔 이렇게 답한다 — 막내가 그대로 배워요.",
            placeholder = "예: 계좌 물어보면 → \"국민 123-45 ○○이에요 😊 입금 확인되면 바로 문자드릴게요!\"",
            initial = "",
            onDismiss = { showExampleDialog = false },
            onConfirm = { v ->
                val t = v.trim()
                if (t.isNotBlank()) { prefs.toneExamples = prefs.toneExamples + t; examplesCount = prefs.toneExamples.size }
                showExampleDialog = false
            }
        )
    }
    if (showSignatureDialog) {
        ToneInputDialog(
            title = "꼭 쓰는 인사말",
            sub = "답장 끝에 자동으로 붙일 시그니처예요.",
            placeholder = "편하게 문의주세요!",
            initial = signature,
            onDismiss = { showSignatureDialog = false },
            onConfirm = { v -> signature = v.trim(); prefs.toneSignature = v.trim(); showSignatureDialog = false }
        )
    }
}

@Composable
private fun ToneSecSub(text: String) {
    Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
        modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun TonePlaceholder(text: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(14.dp)) {
        Text(text, fontSize = 12.5.sp, color = TossTextTertiary)
    }
}

// 프로토 .traits / .trait / .tk — 흰 칩 + 회색 키.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToneTraits(traits: List<com.detailline.callfollowcrm.ai.ToneTrait>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        traits.forEach { t ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .border(1.dp, TossDivider, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (t.k.isNotBlank()) {
                    Text(t.k, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                    Spacer(Modifier.width(7.dp))
                }
                Text(t.v, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            }
        }
    }
}

// 프로토 .tone-ba — 질문 + [일반 AI] / [내 말투] 두 답 비교.
@Composable
private fun ToneBeforeAfter(ex: com.detailline.callfollowcrm.ai.ToneExample) {
    Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White)) {
        // ba-q
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 14.dp, bottom = 4.dp)
        ) {
            Text("💬", fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text("\"${ex.question}\"", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary)
        }
        // gen row
        ToneBaRow(
            tag = "일반 AI", tagBg = TossGrayBg, tagColor = TossTextTertiary, mark = null,
            msg = ex.plain, msgBg = TossGrayBg, msgBorder = null
        )
        Box(Modifier.padding(horizontal = 15.dp).fillMaxWidth().height(1.dp).background(TossDivider))
        // mine row
        ToneBaRow(
            tag = "내 말투", tagBg = Color(0xFFF1ECFF), tagColor = Color(0xFF7C5CFC), mark = "✨ 사장님처럼",
            msg = ex.mine, msgBg = Color(0xFFF6F3FF), msgBorder = Color(0xFFECE5FF)
        )
    }
}

@Composable
private fun ToneBaRow(
    tag: String, tagBg: Color, tagColor: Color, mark: String?,
    msg: String, msgBg: Color, msgBorder: Color?
) {
    Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 9.dp, bottom = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tag, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = tagColor,
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(tagBg).padding(horizontal = 8.dp, vertical = 2.dp)
            )
            if (mark != null) {
                Spacer(Modifier.weight(1f))
                Text(mark, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7C5CFC))
            }
        }
        Spacer(Modifier.height(8.dp))
        val msgMod = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(msgBg)
            .let { if (msgBorder != null) it.border(1.dp, msgBorder, RoundedCornerShape(13.dp)) else it }
            .padding(horizontal = 13.dp, vertical = 11.dp)
        Box(msgMod) {
            Text(msg, fontSize = 13.sp, color = TossTextPrimary, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun ToneSourceRow(icon: ImageVector, title: String, desc: String, count: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1ECFF)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFF7C5CFC), modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Text(desc, fontSize = 11.5.sp, color = TossTextTertiary)
        }
        Text(count, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7C5CFC))
    }
}

@Composable
private fun ToneTeachButton(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    val teachInteraction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth()
            .pressScale(teachInteraction)
            .tossCardShadow(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)).background(Color.White)
            .clickable(interactionSource = teachInteraction, indication = null, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1ECFF)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFF7C5CFC), modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Text(desc, fontSize = 11.5.sp, color = TossTextTertiary)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TossTextTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ToneInputDialog(
    title: String, sub: String, placeholder: String, initial: String,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
        text = {
            Column {
                com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                Text(sub, fontSize = 13.sp, color = TossTextSecondary)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text(placeholder, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("저장", color = TossBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = TossTextSecondary) } },
        containerColor = Color.White
    )
}

/** 문제 신고 / 진단 보내기 (2026-07-22 사장님). 메모(선택) + 스크린샷 첨부(선택) + '보내기' → 공유 시트로 전송. */
@Composable
private fun DiagnosticsDialog(
    onDismiss: () -> Unit, onSend: (String, android.net.Uri?) -> Unit
) {
    var note by remember { mutableStateOf("") }
    var shot by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) shot = uri }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("문제 신고 / 진단 보내기", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
        text = {
            Column {
                com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                Text(
                    "어떤 문제인지 간단히 적어주세요. '보내기'를 누르면 앱 버전·기기·자동문자 설정값이 함께 전송돼요. (고객 이름·번호·대화 내용은 포함되지 않아요.)",
                    fontSize = 13.sp, color = TossTextSecondary
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    placeholder = { Text("예: D-1 안내 문자가 깨져서 나와요", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                // 스크린샷 첨부(선택) — 깨진 화면을 직접 보여주기. (2026-07-22 사장님)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        picker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }) {
                        Text(if (shot == null) "🖼  스크린샷 첨부" else "🖼  스크린샷 첨부됨 ✓",
                            color = TossBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    if (shot != null) {
                        TextButton(onClick = { shot = null }) {
                            Text("빼기", color = TossTextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(note, shot) }) {
                Text("보내기", color = TossBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = TossTextSecondary) } },
        containerColor = Color.White
    )
}

