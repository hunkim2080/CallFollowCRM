package com.detailline.callfollowcrm.presentation.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.AppContainer
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
    onOpenPricingItems: () -> Unit
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

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "설정",
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
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 0. 기본 메시지 앱 — 2026-05-29 Phase A 2단계 Day 5 enable.
            //    1단계 (자격 인프라) + 2단계 Day 1~4 (klinker 수신 + hook) 다 박혀서 이제 활성화 OK.
            //    토글 ON → RoleManager 다이얼로그 → 사장님 동의 → default 됨.
            //    수동 입력 (선택) = 자동 추출 실패 시 안전망.
            DefaultSmsAppCard(preferences = container.preferences)

            // 1. AI 서버 상태
            ServerStatusCard(alive = serverAlive)

            // 1.5 토큰 사용량 — 2026-05-27 사장님 결정: 진짜 토큰 낭비 파악용 모니터링.
            //   서버 §12 endpoint 미구현 시 graceful fallback ("서버 모니터링 미구현" 표시).
            val usageStatsResult by viewModel.usageStats.collectAsState()
            val usageLoading by viewModel.usageLoading.collectAsState()
            UsageStatsCard(
                result = usageStatsResult,
                loading = usageLoading,
                onRefresh = { period ->
                    viewModel.loadUsageStats(period)
                }
            )

            // 2. 사장님 톤 학습 — RING-GO 정체성이라 상단 노출 (2026-05-25 사장님 결정).
            OwnerToneCard(sampleCount = toneSampleCount)

            // 2.1 추천 답변 채택률 — 2026-05-29 킬러콘텐츠 3단계 후속.
            //   "수정 거리 0" 목표를 사장님이 직접 확인. 데이터 쌓일수록 RING-GO 가 진화하는 모습 시각화.
            val suggestionStats by viewModel.suggestionStats.collectAsState()
            val suggestionStatsPeriodDays by viewModel.suggestionStatsPeriodDays.collectAsState()
            SuggestionStatsCard(
                stats = suggestionStats,
                periodDays = suggestionStatsPeriodDays,
                onPeriodChange = { viewModel.loadSuggestionStats(it) }
            )

            // 2.5 수신 SMS 알림 — RING-GO 가 갤메시지 대체 (옵션 A, 2026-05-25)
            IncomingSmsNotifyCard(
                enabled = state.incomingSmsNotifyEnabled,
                onToggle = viewModel::setIncomingSmsNotifyEnabled
            )

            // 2.55 기본 네비 앱 — 카드 펼침 [📍 길찾기] 가 사용 (2026-05-27).
            //   카카오내비/네이버지도/티맵 3개. 첫 길찾기 탭 시 자동 다이얼로그도 띄움.
            NavAppPreferenceCard(
                selectedKey = state.defaultNavAppKey,
                onSelect = viewModel::setDefaultNavApp
            )

            // 2.6 알림 진단 — 사장님 보고 (2026-05-25): RING-GO 알림이 안 뜬다.
            //   권한/채널 상태 한눈에 + Fix 버튼.
            NotificationDiagnosticCard()

            // 3. 통화 종료 후 동작 — AfterCallBehavior + 후속 알림 빠른 액션 + 자동 응답 통합
            AfterCallCard(
                state = state,
                templates = templates,
                onBehaviorChange = viewModel::setBehavior,
                onQuickActionChange = viewModel::setQuickAction,
                onIncomingTemplateChange = viewModel::setIncomingTemplate,
                onMissedTemplateChange = viewModel::setMissedTemplate,
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
                }
            )

            // 4. 문자 템플릿 / 가격표 — 진입점 2개
            TossCard {
                Column {
                    SectionLabel("문자 템플릿")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "고객에게 자주 보내는 문구 묶음. 채팅 화면 가로 알약 + AI 제안의 [후기 요청] 등에서 사용.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    TossPrimaryButton(text = "템플릿 보기 / 편집", onClick = onOpenTemplates)
                }
            }

            TossCard {
                Column {
                    SectionLabel("가격표 관리")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AI 제안의 [견적 작성하기] 가 이 항목들을 체크리스트로 띄워요. 신축/구축 별로 관리.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    TossPrimaryButton(text = "가격표 보기 / 편집", onClick = onOpenPricingItems)
                }
            }

            // 앱 정보 footer
            AppFooter()

            Spacer(Modifier.height(16.dp))
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

/** AI 서버 연결 상태 — ● 색깔 + 사용량 placeholder. */
@Composable
private fun ServerStatusCard(alive: Boolean?) {
    val (dotColor, statusText, subtext) = when (alive) {
        true -> Triple(
            TossSuccess,
            "AI 서버 정상",
            "Tailscale 연결됨. 채팅 답변 추천 / 견적 도움이 작동해요."
        )
        false -> Triple(
            TossError,
            "AI 서버 연결 안 됨",
            "Tailscale 이 켜져있는지 확인하세요. 답변 추천이 안 떠도 메시지는 보낼 수 있어요."
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
        Spacer(Modifier.height(2.dp))
        Text(
            "디테일라인 줄눈",
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

