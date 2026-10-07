package com.detailline.callfollowcrm.presentation.screen.settings

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppShape
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Delete
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
import com.detailline.callfollowcrm.presentation.component.CallSummaryConsentDialog
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
import com.detailline.callfollowcrm.presentation.util.keyboardPadding

// ======================================================================
//  🍃 더보기의 단독 카드·시트 — 기본 문자앱·구글 캘린더·계정 삭제·꼬리말
//
//  SettingsScreen.kt 에서 **그대로 옮겨온** 조각들이다. (2026-10-02)
//  옮긴 기준: 그 파일의 **다른 private 이름을 하나도 안 쓰는** 잎(leaf) 선언.
//  같은 패키지라 `private` → `internal` 한 글자만 바뀌었고 **부르는 곳은 그대로**다.
//  (Fable 점검 ✋ — 큰 화면을 통째로 쪼개는 건 위험하니 **잎부터** 떼어낸다)
// ======================================================================

/**
 * 토큰 사용량 카드 — 사장님이 LLM 비용 실측 (2026-05-27).
 *   서버 §12 endpoint 결과 표시. period 선택 (오늘/이번달/전체).
 *   서버 미구현 / 네트워크 실패 = "서버 모니터링 미구현" 안내.
 *
 * Period chip = chip row (전체/미확인 식). selected = 강조 색.
 */
@Composable
internal fun UsageStatsCard(
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
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.BarChart, null, tint = TossTextSecondary, modifier = Modifier.size(18.dp))
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
                            .background(if (selected) TossBlue else AppTheme.colors.surfaceMuted)
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
                        "사용량 불러오는 중…",
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
                        "잠시 후 다시 확인해주세요.",
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
                            "쓰인 곳별 (돈 많이 든 순)",
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

@Composable
internal fun DefaultSmsAppCard(
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
                        "시공막내를 기본 메시지 앱으로 사용하기",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossTextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // 영어·상호 뒤 조사는 **붙여 쓴다**("시공막내 가" → "시공막내가"). 앱 전체가 해요체다.
                        if (isDefault)
                            "문자·사진을 시공막내가 받고 있어요. 갤럭시 메시지 알림은 폰 설정에서 끄세요."
                        else
                            "문자·사진 받는 걸 시공막내가 맡아요. 켜면 폰이 한 번 물어봐요.",
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
                    "MMS 서버 수동 입력 (선택)",
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
internal fun WebViewerSection(container: AppContainer) {
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
                    toast("인증했어요. 이제 QR로 로그인돼요")
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
                    toast("인증했어요. 이제 아래 QR로 로그인돼요")
                } else toast("인증은 됐는데 열쇠(토큰)가 안 왔어요. 관리자에게 알려주세요")
            }.onFailure { toast((it as? com.detailline.callfollowcrm.ai.AuthException)?.message ?: "인증하지 못했어요 — 다시 해주세요") }
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
            // 제목은 바로 위 앱바가 이미 말하고 있다. 같은 말을 두 번 하지 않는다. (2026-09-22 사장님)
            Text(
                "PC 큰 화면에서 시공 사진을 날짜별로 보고 블로그용으로 내려받아요. 딱 2단계예요:",
                fontSize = 13.sp, color = TossTextSecondary, lineHeight = 19.sp
            )
            Spacer(Modifier.height(12.dp))
            // ① 주소 — 크게 + 복사 (사장님이 PC 주소를 몰라 시작을 못 하던 문제. 2026-08-15)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.colors.primaryBg).border(1.dp, Color(0xFFD5E4FB), RoundedCornerShape(12.dp))
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
                    ) { Text("복사", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold) }
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
                        .background(AppTheme.colors.cautionBg).border(1.dp, Color(0xFFFFE2A8), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text("먼저 웹 로그인 인증 (한 번만)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A6100))
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
                Text("웹 로그인 인증됨 ✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossBlue)
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
                                // 세로 고정 화면. 기본 화면은 가로로 박혀 있다. (2026-09-21 사장님)
                                .setCaptureActivity(com.detailline.callfollowcrm.presentation.qr.PortraitCaptureActivity::class.java)
                                .setOrientationLocked(true)
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
internal fun GoogleCalendarSection(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = container.preferences

    var connected by remember { mutableStateOf(prefs.googleCalendarConnected) }
    var busy by remember { mutableStateOf(false) }
    // 올리는 중 — 매니저가 백그라운드로 돌리므로(화면 나가도 계속) 매니저 flow 를 관찰. (2026-10-08 사장님)
    val mgrSyncing = container.calendarSyncManager.syncing.collectAsState().value
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // 토큰 확보 후: 연결 플래그 ON + '시공막내' 캘린더 준비 + 기존 일정 전부 올리기(백그라운드)
    fun finishConnect(token: String?) {
        if (token == null) { busy = false; toast("연결이 취소됐거나 실패했어요"); return }
        prefs.googleCalendarConnected = true; connected = true
        busy = false
        // 올리기는 백그라운드로 — 이 화면을 나가도 끝까지(고아청소까지) 돈다. (2026-10-08 사장님)
        container.calendarSyncManager.startSyncAll(container.applicationScope) { n ->
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
        prefs.googleCalendarAccountEmail = null
        toast("연결을 껐어요 (이미 올라간 일정은 구글 캘린더에 그대로 남아요)")
    }

    fun syncNow() {
        // 백그라운드로 — 설정 화면을 나가도 끝까지 올라간다(고아청소까지). (2026-10-08 사장님)
        container.calendarSyncManager.startSyncAll(container.applicationScope) { n ->
            toast(if (n >= 0) "동기화했어요 (일정 ${n}건)" else "먼저 연결이 필요해요")
        }
    }

    TossCard {
        Column {
            // 제목은 바로 위 앱바가 이미 말하고 있다. 같은 말을 두 번 하지 않는다. (2026-09-22 사장님)
            Text(
                "시공·A/S 일정이 구글 캘린더에 자동으로 올라가요.\n폰 위젯에서 보거나, 가족·직원과 나눠 보거나, 폰을 바꿔도 그대로 남아요.",
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
                // '어느 계정인지' 를 연결됨 옆에 같이 보여준다. (2026-10-05 사장님 "무슨 아이디로 로그인했나 볼 곳이 없었어")
                val acctEmail = prefs.googleCalendarAccountEmail
                Text(
                    "✓ 연결됨" + (acctEmail?.let { " · $it" } ?: ""),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary
                )
                if (acctEmail == null) {
                    // 옛 연결(이메일 권한 전) — 한 번 다시 연결하면 계정이 나온다.
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { if (!busy) connect() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("어느 계정인지 보려면 · 다시 연결") }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { if (!busy && !mgrSyncing) syncNow() },
                        enabled = !busy && !mgrSyncing,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (mgrSyncing) "올리는 중…" else "지금 동기화") }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { if (!busy && !mgrSyncing) disconnect() },
                        enabled = !busy && !mgrSyncing,
                        modifier = Modifier.weight(1f)
                    ) { Text("연결 끄기") }
                }
            }
        }
    }
}

/**
 * 계정 삭제 — **앱 안 경로**. (2026-09-17 플레이 정책 점검)
 *
 * 왜 앱에서 바로 안 지우고 페이지로 보내나:
 *   삭제는 되돌릴 수 없고 **본인 확인(문자 인증)** 을 거쳐야 한다. 그 흐름이 이미 웹에 있고,
 *   앱에 또 만들면 두 벌이 되어 한쪽만 고쳐지는 사고가 난다.
 *   구글 요건은 "앱 안에 **경로**(path)가 있을 것" 이므로, 앱에서 눌러 바로 갈 수 있으면 된다.
 *   (support.google.com/googleplay/android-developer/answer/13327111)
 *
 * 무엇이 지워지는지 **먼저 보여주고** 누르게 한다 — 실수로 눌러 날리는 일이 없게.
 */
@Composable
internal fun DeleteAccountSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val noRipple = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    androidx.activity.compose.BackHandler { onDismiss() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = noRipple, indication = null) { onDismiss() }
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Color.White)
                .clickable(interactionSource = noRipple, indication = null) { }
                // 내비바에 버튼이 붙지 않게 아래를 넉넉히. (이 파일엔 인셋 import 가 없어 고정값)
                .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 44.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp)
                .clip(RoundedCornerShape(999.dp)).background(Color(0xFFE2E6EC)))
            Spacer(Modifier.height(14.dp))
            Text("계정 삭제", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(AppTheme.colors.unpaidBg).padding(14.dp)) {
                Text(
                    "지우면 되돌릴 수 없어요.\n먼저 [데이터 내보내기]로 백업해 두세요.",
                    fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F4E), lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("이런 것들이 지워져요", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
            Spacer(Modifier.height(7.dp))
            listOf(
                "회원 정보 — 전화번호·인증 기록·사업자 정보",
                "서버에 있는 통화 요약과 AI 답변 기록",
                "서버 백업본·현장 사진·일정 공유 기록",
                "팀·협업 연결, 웹 로그인 기록"
            ).forEach {
                Row(Modifier.padding(bottom = 5.dp)) {
                    Text("·", fontSize = 13.5.sp, color = TossTextTertiary)
                    Spacer(Modifier.width(7.dp))
                    Text(it, fontSize = 13.5.sp, color = TossTextSecondary, lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            // ⚠️ "앱만 지우면 끝" 이라는 오해를 반드시 깬다. (2026-09-17, 에이닷 처리방침 참고)
            //   SKT: "앱만을 삭제할 경우 개인정보가 파기되지 않으므로 반드시 별도로 동의를 철회하여야 합니다"
            //   앞 문장만 두면 "앱 지우면 되겠네" 로 읽힌다 — 서버 데이터는 그대로 남는다.
            Text(
                "앱만 지우면 서버에 있는 내 데이터는 그대로 남아요.\n" +
                    "지우려면 여기서 계정 삭제를 해주세요.\n" +
                    "폰 안에 있는 고객·일정은 앱을 지우면 함께 사라져요.",
                fontSize = 11.5.sp, color = TossTextTertiary, lineHeight = 17.sp
            )
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFD32F4E))
                    .clickable {
                        // 본인 확인(문자 인증)은 웹 페이지가 담당 — 흐름을 한 벌로 유지.
                        com.detailline.callfollowcrm.presentation.screen.web.DocWebViewActivity.open(
                            ctx, com.detailline.callfollowcrm.AppConfig.ACCOUNT_DELETE_URL, "계정 삭제"
                        )
                        onDismiss()
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("계정 삭제하러 가기", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                    .clickable { onDismiss() }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("닫기", color = TossTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 시공막내 알림이 왜 안 뜨는지 사장님이 직접 진단 — 권한/채널 상태 한눈에.
 * 2026-05-25 사장님 보고: 갤메시지 알림 끄고 새 빌드 깔았는데도 안 뜸 → 진단 필요.
 */
@Composable
internal fun NotificationDiagnosticCard() {
    val ctx = LocalContext.current
    // 간소화(2026-07-02 사장님) — 기술적 권한 체크리스트 제거(온보딩 권한 흐름과 중복). 정말 중요한 채팅+ 안내만.
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.PhotoCamera, null, tint = TossTextSecondary, modifier = Modifier.size(18.dp))
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
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Warning, null, tint = TossTextSecondary, modifier = Modifier.size(16.dp))
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
internal fun AppFooter() {
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
        // 📰 **버전을 누르면 「뭐가 달라졌나」를 보여준다.** (2026-10-01 사장님)
        //   "시공막내 버전 000000 이 영역을 클릭하면 이번 업데이트 내용?
        //    이 간략하게라도 정리되서 보였으면 좋겠어 내가 파악하기 위함임"
        //
        //   ⚠️ **소식을 앱에 또 적지 않는다.** 홈페이지 주간 소식(si0in.kr/updates)이
        //   이미 **그 한 곳**이다. 앱에 또 적으면 두 벌이 되고, 한쪽만 고치게 된다.
        //   그래서 **그 페이지를 앱 안에서 여는다**(브라우저 없어도 열린다).
        // ➕ 딱지를 뻐다 — **숫자만 누르면 들어간다.** (2026-10-01 사장님)
        //   "뭐가달라졌나..? 보다 그냥 나만 알아보는거야
        //    내가 개발하고 까먹어서. 그래서 그냥 버전 숫자누르면 들어가지게"
        //   쓰는 분들에겐 필요 없는 글자다 — 발바닥에 파란 글자가 하나 더 있을 이유가 없다.
        Text(
            "시공막내 버전 ${com.detailline.callfollowcrm.BuildConfig.VERSION_NAME}",
            color = TossTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(AppShape.sm)
                .clickable {
                    com.detailline.callfollowcrm.presentation.screen.web.DocWebViewActivity.open(
                        linkCtx,
                        // ?app=1 — 홈페이지 메뉴·「무료로 시작하기」를 벗고 **소식만** 보여준다.
                        //   이미 쓰고 계신 분께 가입 버튼을 내민 꼴이고,
                        //   폰 폭에선 메뉴가 두 줄로 깨졌다. (2026-10-01 폰에서 발견)
                        com.detailline.callfollowcrm.AppConfig.BASE_URL + "/updates?app=1",
                        "이번 업데이트"
                    )
                }
                .padding(horizontal = 6.dp, vertical = 4.dp)
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
