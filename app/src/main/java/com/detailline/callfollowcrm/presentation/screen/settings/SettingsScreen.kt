package com.detailline.callfollowcrm.presentation.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
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
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
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
    onOpenCollabSites: () -> Unit = {},
    onOpenReport: () -> Unit = {},
    onOpenTradeSelect: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenPrinciples: () -> Unit = {},
    onShowIntro: () -> Unit = {},
    /** 진입 시 바로 열 서브페이지 ("autosms" = 자동 문자, 부재중 응답 펼침). null = 일반 더보기. */
    initialSubPage: String? = null
) {
    val state by viewModel.state.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val serverAlive by viewModel.serverAlive.collectAsState()
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
            Toast.makeText(context, "SEND_SMS 권한이 거부되어 켤 수 없어요", Toast.LENGTH_SHORT).show()
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
    val subTitle = when (subPage) {
        "tone" -> "내 말투 학습"
        "autosms" -> "자동 문자"
        "nav" -> "기본 네비 앱"
        "smsapp" -> "기본 문자 앱"
        "noti" -> "알림 미리보기"
        "server" -> "AI 서버 상태"
        else -> "더보기"
    }
    BackHandler(enabled = subPage != null) { subPage = null }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                windowInsets = androidx.compose.foundation.layout.WindowInsets(top = 12.dp),
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
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (subPage == null) {
                // ══════════════ 프로토 s-more 메뉴 (1:1) ══════════════
                // 막내 비서 카드 (agent-card) — 레벨·말투%·상담/시공 = 실제 카운트.
                //   카드 탭 → '내 말투 학습'으로 이동(막내 비서 = 말투 학습 흐름). (2026-06-17 사장님)
                val agentCard by viewModel.agentCard.collectAsState()
                AgentMiniCard(card = agentCard, onClick = { subPage = "tone" })

                // 프로토 setup-check — 시작 체크 (실제 권한 상태). 다 되면 한 줄로 접힘.
                SetupCheckCard()

                SettingsGroup("함께 일하는 사람") {
                    LockRow(Icons.Filled.Group, TossBlueSoft, TossBlue, "인원 관리",
                        "팀원 · 일당사장 — 현장에 보낼 사람 관리", tier = "비즈니스", onClick = onOpenTeam)
                    LockRow(Icons.Filled.Group, Color(0xFFFFF3DF), Color(0xFFF6A609), "수첩",
                        "거래처 — 자재·협력·장비 자주 거래하는 곳", onClick = onOpenNotebook)
                    LockRow(Icons.Filled.Group, Color(0xFFF1ECFE), Color(0xFF7C5CFC), "협업 현장",
                        "다른 사장님과 현장 하나만 같이 보기", tier = "비즈니스", onClick = onOpenCollabSites)
                }
                SettingsGroup("장사 분석") {
                    LockRow(Icons.Filled.BarChart, TossBlueSoft, TossBlue, "상세 리포트",
                        "매출·전환율·추천 채택률 분석", tier = "비즈니스", onClick = onOpenReport)
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
                // 프로토 "앱 설정" = 기본 네비 앱 하나뿐 (사장님 결정 2026-06-03 — 프로토대로 정리).
                //   기본 문자 앱 설정은 위 setup-check 가 커버. 내 업종/AI 서버 상태는 프로토에 없어 제거.
                SettingsGroup("앱 설정") {
                    val navLabel = com.detailline.callfollowcrm.util.NavApp.values()
                        .find { it.key == state.defaultNavAppKey }?.label ?: "카카오내비"
                    LockRow(Icons.Filled.Navigation, TossGrayBg, TossTextTertiary, "기본 네비 앱",
                        navLabel) { subPage = "nav" }
                }
                SettingsGroup("도움말") {
                    LockRow(Icons.AutoMirrored.Filled.Chat, TossGrayBg, TossTextTertiary, "알림 미리보기",
                        "RING-GO 알림이 어떻게 오는지") { subPage = "noti" }
                    LockRow(Icons.Filled.AutoAwesome, TossGrayBg, TossTextTertiary, "앱 소개 다시 보기",
                        null, onClick = onShowIntro)
                }

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
                    val toneRagConsented by viewModel.toneRagConsented.collectAsState()
                    val toneRagUploadedCount by viewModel.toneRagUploadedCount.collectAsState()
                    val toneRagAvailable by viewModel.toneRagAvailable.collectAsState()
                    val toneRagUploading by viewModel.toneRagUploading.collectAsState()
                    val toneRagProgress by viewModel.toneRagProgress.collectAsState()
                    ToneLearnProtoSection(
                        container = container,
                        profile = toneProfile,
                        tonePct = toneProfile?.learnRatePct ?: agentCard.tonePct,
                        ragUploadedCount = toneRagUploadedCount,
                        ragAvailable = toneRagAvailable,
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
                        "서버 모니터링 미구현 또는 연결 실패",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TossError,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "맥미니 서버에 GET /api/usage-stats endpoint 추가 필요 " +
                            "(RINGGO_SERVER_P0P1P2_UPGRADE.md §12)",
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
 * 2026-05-29 Phase A 2단계 Day 5 — RING-GO 를 기본 메시지 앱으로 전환하는 카드.
 *
 * **활성화됨** (Day 1~4 의 자격 인프라 + klinker hook 다 박힘).
 * 토글 ON → RoleManager 다이얼로그 → 사장님 동의 → default. OFF → 시스템 default-apps Settings.
 *
 * 사장님 카피 변경 (Day 5):
 *   메인: "📱 RING-GO를 기본 메시지 앱으로 사용하기"
 *   설명 (default 일 때): "✅ RING-GO 가 SMS/MMS 를 받고 있어요. 갤메시지 알림은 시스템 설정에서 끄세요."
 *   설명 (default 아닐 때): "SMS/MMS 수신을 RING-GO 에서 관리합니다. 토글 켜면 시스템이 동의를 요청합니다."
 *   수동 입력 expander: "🔧 MMS 서버 수동 입력 (선택)" — 자동 추출 실패 시 안전망.
 */
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
                        "📱 RING-GO를 기본 메시지 앱으로 사용하기",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossTextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isDefault)
                            "✅ RING-GO 가 SMS/MMS 를 받고 있어요. 갤메시지 알림은 시스템 설정에서 끄세요."
                        else
                            "SMS/MMS 수신을 RING-GO 에서 관리합니다. 토글 켜면 시스템이 동의를 요청합니다.",
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
 *   - "어떤 상황에서 RING-GO 가 잘 답하고, 어떤 상황에서 못 하는지" 한눈에.
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
                "어떤 상황에서 사장님이 그대로 보내는지, 어떤 상황은 개선이 필요한지 RING-GO 가 직접 분석합니다.",
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
                            .background(Color(0xFFFFF8E1))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                "💡 개선 후보 ${needsImprovement.size}개 시나리오",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B6F00)
                            )
                            Spacer(Modifier.height(4.dp))
                            needsImprovement.forEach { s ->
                                Text(
                                    "• ${scenarioLabel(s.scenario)} — ${(s.adoptionRate * 100).toInt()}% (${s.total}건)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8B6F00)
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
        AutoDotLabel(Color(0xFF12B886), "다시 연락한 고객 (단골·기존)")
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

    // 에이닷 녹음(m4a) 폴더 연결 — 자동 녹음을 ↑ 없이 자동 요약하려면 필요. (2026-06-14 사장님 빈틈 보완)
    var recFolderConnected by remember { mutableStateOf(com.detailline.callfollowcrm.recording.AdotFolderScanner.isConnected(ctx)) }
    val recFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            com.detailline.callfollowcrm.recording.AdotFolderScanner.connectFolder(ctx, uri)
            recFolderConnected = true
            android.widget.Toast.makeText(
                ctx, "녹음 폴더 연결됐어요. 이제 통화 끝나면 자동으로 요약돼요.", android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    TossCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEDE9FE)),
                contentAlignment = Alignment.Center) { Text("🎙️", fontSize = 16.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("통화 녹음 폴더 ${if (recFolderConnected) "· 연결됨" else ""}", fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Text(
                    if (recFolderConnected) "통화 끝나면 녹음으로 자동 요약돼요 (↑ 안 눌러도 됨)"
                    else "통화 녹음을 ↑ 없이 자동 요약하려면 녹음 폴더를 한 번 연결하세요",
                    fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (recFolderConnected) Color(0xFFEEF0F3) else Color(0xFF3182F6))
                    .clickable { recFolderLauncher.launch(null) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (recFolderConnected) "다시" else "연결하기",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (recFolderConnected) TossTextSecondary else Color.White)
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

    // 받은 문자 알림 (보존)
    TossCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("받은 문자 알림", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Text("고객 문자가 오면 AI 추천 답변과 함께 알림", fontSize = 12.sp, color = TossTextTertiary)
            }
            Switch(checked = incomingNotifyOn, onCheckedChange = onIncomingNotifyToggle)
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
                            Text(p, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD64545))
                            Spacer(Modifier.width(6.dp))
                            Text("✕", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD64545).copy(alpha = 0.6f))
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
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.5.sp, color = TossTextPrimary)
        )
        Box(Modifier.padding(top = 5.dp, start = 2.dp)) {
            if (showSaved) {
                Text("✓ 저장됐어요", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12B886))
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
                    "통화 종료 알림(RING-GO 캐치)의 액션 버튼 3개. 탭하면 해당 템플릿 자동 선택된 채로 문자 화면 열림.",
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
 * 사장님 톤 학습 — RING-GO 의 정체성 카드.
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
                            .background(Color(0xFFFFF8E1))
                            .padding(12.dp)
                    ) {
                        Text(
                            "⚠️ 임베딩 검색 비활성 — Mac mini 에 'pip install FlagEmbedding sqlite-vec' 후 launchctl reload 필요. " +
                                "메시지는 저장되어 있어 install 후 자동 활성화됩니다.",
                            fontSize = 11.sp,
                            color = Color(0xFF8B6F00)
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
 * 수신 SMS 가 오면 RING-GO 자체 알림 + 빠른 답장 + AI 추천 답변 — 갤메시지 대체.
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
                "RING-GO 가 갤메시지보다 풍부한 알림을 띄워요. AI 추천 답변 칩 + 빠른 답장 + 한 탭 전화.",
                style = MaterialTheme.typography.bodySmall,
                color = TossTextSecondary
            )
            Spacer(Modifier.height(10.dp))
            // 갤메시지 알림 끄기 안내 — 안 끄면 알림 두 번 떠서 거슬림.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF8E1))  // 연한 노랑 — 안내 톤
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        "⚠️ 갤메시지 알림은 직접 꺼주세요",
                        color = Color(0xFF7A5C00),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "안 끄면 RING-GO + 갤메시지 둘 다 알림이 떠요. 시스템 설정 → 앱 → 메시지 → 알림 OFF.",
                        color = Color(0xFF7A5C00),
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
                            color = Color(0xFF7A5C00),
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
 * RING-GO 알림이 왜 안 뜨는지 사장님이 직접 진단 — 권한/채널 상태 한눈에.
 * 2026-05-25 사장님 보고: 갤메시지 알림 끄고 새 빌드 깔았는데도 안 뜸 → 진단 필요.
 */
@Composable
private fun NotificationDiagnosticCard() {
    val ctx = LocalContext.current
    // remember 가 아닌 매 composition 새로 계산 — 사장님이 권한 설정 다녀와도 즉시 반영.
    val appNotifEnabled = androidx.core.app.NotificationManagerCompat.from(ctx)
        .areNotificationsEnabled()
    val smsChannelOk = remember(appNotifEnabled) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(android.app.NotificationManager::class.java)
            val ch = mgr?.getNotificationChannel("incoming_sms")
            ch != null && ch.importance != android.app.NotificationManager.IMPORTANCE_NONE
        } else true
    }
    val receiveSmsOk = androidx.core.content.ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.RECEIVE_SMS
    ) == PackageManager.PERMISSION_GRANTED
    val readSmsOk = androidx.core.content.ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED
    val sendSmsOk = androidx.core.content.ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.SEND_SMS
    ) == PackageManager.PERMISSION_GRANTED
    val postNotifOk =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    // 2026-05-25 사장님 피드백 — "어느 영역이지?", "버튼 안 눌림":
    //   ACTION_APPLICATION_DETAILS_SETTINGS 로 보내면 사장님이 거기서 "알림" 메뉴로 잘못 빠짐.
    //   ActivityCompat.requestPermissions 는 Activity callback 미등록 시 무반응.
    //   → Compose-friendly rememberLauncherForActivityResult 로 권한 다이얼로그 직접 받기.
    val activity = remember(ctx) { ctx.findActivityOrNull() }

    val postNotifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(ctx, "알림 권한이 거부됐어요", Toast.LENGTH_SHORT).show()
    }
    val receiveSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(ctx, "SMS 수신 권한이 거부됐어요", Toast.LENGTH_SHORT).show()
    }
    val readSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(ctx, "SMS 읽기 권한이 거부됐어요", Toast.LENGTH_SHORT).show()
    }
    val sendSmsDiagLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) Toast.makeText(ctx, "SMS 발송 권한 허용됨 — 이제 알림 답장 가능", Toast.LENGTH_SHORT).show()
        else Toast.makeText(ctx, "SMS 발송 권한이 거부됐어요", Toast.LENGTH_SHORT).show()
    }

    fun requestOrSettings(
        permission: String,
        launcher: androidx.activity.result.ActivityResultLauncher<String>
    ) {
        // 영구 거부 (다시 묻지 않음) 감지 — Activity 가 있고, rationale 도 false 면서, 한 번 이상 요청 이력.
        //   첫 요청은 무조건 launcher.launch 로 다이얼로그 시도.
        val prefs = ctx.getSharedPreferences("perm_state", android.content.Context.MODE_PRIVATE)
        val askedBefore = prefs.getBoolean("asked_$permission", false)
        val permanentlyDenied = activity != null && askedBefore &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        if (permanentlyDenied) {
            Toast.makeText(
                ctx,
                "권한이 영구 거부 상태예요. 설정 → 권한에서 직접 켜주세요.",
                Toast.LENGTH_LONG
            ).show()
            openAppPermissionSettings(ctx)
        } else {
            prefs.edit().putBoolean("asked_$permission", true).apply()
            launcher.launch(permission)
        }
    }

    val allOk = appNotifEnabled && smsChannelOk && receiveSmsOk && readSmsOk && sendSmsOk && postNotifOk

    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (allOk) "🟢" else "🔍", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (allOk) "알림 정상" else "알림 진단",
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))

            DiagnosticRow(
                label = "앱 알림 허용",
                ok = appNotifEnabled,
                fixLabel = "→ 앱 알림 설정 열기",
                onFix = { openAppNotificationSettings(ctx) }
            )
            DiagnosticRow(
                label = "📩 새 문자 채널",
                ok = smsChannelOk,
                fixLabel = "→ 채널 설정 열기",
                onFix = { openChannelSettings(ctx, "incoming_sms") }
            )
            DiagnosticRow(
                label = "알림 게시 권한 (Android 13+)",
                ok = postNotifOk,
                fixLabel = "✓ 권한 허용하기",
                onFix = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        requestOrSettings(Manifest.permission.POST_NOTIFICATIONS, postNotifLauncher)
                    }
                }
            )
            DiagnosticRow(
                label = "SMS 수신 권한",
                ok = receiveSmsOk,
                fixLabel = "✓ 권한 허용하기",
                onFix = { requestOrSettings(Manifest.permission.RECEIVE_SMS, receiveSmsLauncher) }
            )
            DiagnosticRow(
                label = "SMS 읽기 권한 (히스토리)",
                ok = readSmsOk,
                fixLabel = "✓ 권한 허용하기",
                onFix = { requestOrSettings(Manifest.permission.READ_SMS, readSmsLauncher) }
            )
            DiagnosticRow(
                label = "SMS 발송 권한 (알림 답장)",
                ok = sendSmsOk,
                fixLabel = "✓ 권한 허용하기",
                onFix = { requestOrSettings(Manifest.permission.SEND_SMS, sendSmsDiagLauncher) }
            )

            // 채팅+ (Samsung RCS) 안내 — 자동 감지 불가, 항상 표시.
            //   채팅+ 가 켜져 있으면 SMS 가 IP 기반 RCS 로 라우팅되어 SMS_RECEIVED 가 안 뜬다.
            //   삼성/구글 RCS provider 는 외부 앱에 비공개라 흡수 불가 — 사장님이 직접 끄도록 안내.
            Spacer(Modifier.height(12.dp))
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
                        "채팅+ 가 켜져 있으면 문자가 갤메시지 RCS 로만 가서 RING-GO 가 못 받습니다. " +
                            "갤메시지 ≡ → 설정 → 채팅 기능 → 채팅+ OFF.",
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

            if (!allOk) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "❌ 표시된 항목 모두 ON + 채팅+ OFF — 이 두 가지가 충족되면 RING-GO 알림이 즉시 동작합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossError,
                    fontWeight = FontWeight.Medium
                )
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
    val ctx = LocalContext.current
    val version = remember(ctx) {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "RING-GO v$version",
            color = TossTextTertiary,
            fontSize = 11.sp
        )
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
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (locked) 0.6f else 1f }
            .background(Color.White, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
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
 * 프로토 renderSetupCheck — 시작 체크리스트. 실제 권한 상태로 채움 (가짜 done 없음).
 *   항목: 기본 메시지 앱 / 알림 권한 / 다른 앱 위에 표시. ON_RESUME 마다 재검사.
 *   다 되면 "시작 준비 다 됐어요" 한 줄로 접힘. (갤메시지 채팅+ 끄기는 자동 감지 불가라 제외)
 */
@Composable
private fun SetupCheckCard() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var refresh by remember { mutableStateOf(0) }

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

    data class SetupStep(val label: String, val done: Boolean, val action: () -> Unit)
    // 2026-06-18 사장님 결정: "기본 메시지 앱 설정" 권유 제거. 기본앱이 되면 MMS(사진) 직접 수신이
    //   통신사 한계로 실패해 고객 사진이 조용히 유실되는 치명적 위험 → 삼성 문자를 기본으로 두고
    //   RING-GO 는 옆에서 읽는 '동반자' 포지션. (기본앱 진입 카드/헬퍼 코드는 Phase B 대비 남겨둠)
    val steps = listOf(
        SetupStep("알림 권한", notiOn) {
            runCatching {
                val i = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }
        }
    )
    val doneN = steps.count { it.done }
    val total = steps.size
    val all = doneN == total
    var collapsed by remember { mutableStateOf(true) }

    if (all && collapsed) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAFBF2))
                .clickable { collapsed = false }.padding(horizontal = 14.dp, vertical = 12.dp),
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
                    if (!s.done) Text("설정", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        color = TossBlue, modifier = Modifier.clickable { s.action() })
                }
            }
        }
    }
}

@Composable
private fun CheckDot(done: Boolean) {
    Box(
        Modifier.size(20.dp).clip(RoundedCornerShape(50))
            .background(if (done) TossSuccess else Color(0xFFE5E8EB)),
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
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
                    if (ragAvailable > ragUploadedCount) {
                        Text("새 메시지 ${ragAvailable - ragUploadedCount}건 대기", fontSize = 11.sp, color = TossTextSecondary)
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)) {
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
            .clickable(onClick = onClick).padding(14.dp),
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

