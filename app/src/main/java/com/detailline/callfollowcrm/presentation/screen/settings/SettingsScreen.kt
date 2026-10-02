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

    // 접수서 되찾기 — 두 번 눌러 두 번 도는 걸 막는다. (2026-09-23)
    var intakeResyncBusy by remember { mutableStateOf(false) }
    // 되찾은 접수서 목록 — 숫자만 말하면 사장님이 확인할 수 없다. (2026-09-23)
    var resyncFound by remember {
        mutableStateOf<List<com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity>?>(null)
    }
    // 갈라진 손님 합치기 — 미리보기를 먼저 띄운다. 이 값이 null 이면 창이 안 뜬다.
    var mergePlans by remember {
        mutableStateOf<List<com.detailline.callfollowcrm.data.repository.CustomerMergeManager.Plan>?>(null)
    }
    var mergeBusy by remember { mutableStateOf(false) }
    // 갈라진 손님이 **있을 때만** 줄을 그린다. 합치고 나면 다음부터 안 보인다. (2026-09-24 사장님)
    //   갈라지는 원인은 이미 막혀 있어(b53e9bcf) 한 번 쓰면 다시 쓸 일이 없는 버튼이다.
    var hasSplits by remember { mutableStateOf(false) }
    // 🔒 **합칠 건 없는데 번호 모양만 제각각인 줄** — 하이픈이 든 채로 저장된 것들. (2026-09-28 사장님)
    //   사장님: "번호가 두 갈래 세 갈래로 나뉘면 안 돼. 무조건 한 번호로 통일해야 흩어지지 않지."
    //   합칠 게 하나도 없어도 이게 있으면 줄을 그린다 — 정리할 게 남아 있다는 뜻이니까.
    var untidyPhones by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            hasSplits = runCatching { container.customerMergeManager.hasSplits() }.getOrDefault(false)
            untidyPhones = runCatching { container.customerMergeManager.untidyPhoneCount() }.getOrDefault(0)
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
    // 계정 삭제 안내 시트 (2026-09-17 플레이 요건)
    var showDeleteAccount by remember { mutableStateOf(false) }
    // 문제 신고 / 진단 보내기 다이얼로그 (2026-07-22 사장님) — 크래시 아닌 '이상 동작' 수동 신고.
    var showDiagnostics by remember { mutableStateOf(false) }
    val subTitle = when (subPage) {
        "tone" -> "내 말투 학습"
        "autosms" -> "자동으로 챙기기"
        "nav" -> "기본 네비 앱"
        "smsapp" -> "기본 문자 앱"
        "noti" -> "고객 사진(문자) 받기"
        "server" -> "AI 서버 상태"
        "mirror" -> "구글 캘린더 연동"
        "web" -> "시공막내 웹 (PC 사진)"
        else -> "더보기"
    }
    BackHandler(enabled = subPage != null) { subPage = null }

    // ⌨️ 키보드 숫자는 **화면 안에서** 재야 한다(코루틴 안에서는 못 잰다). (2026-09-29 사장님)
    val keyboardDiag = com.detailline.callfollowcrm.presentation.util.keyboardDiagLine()
    if (showDiagnostics) {
        DiagnosticsDialog(
            onDismiss = { showDiagnostics = false },
            onSend = { note, shotUri ->
                showDiagnostics = false
                Toast.makeText(context, "진단을 보내는 중…", Toast.LENGTH_SHORT).show()
                settingsScope.launch {
                    val ok = com.detailline.callfollowcrm.util.DiagnosticsReporter
                        .sendToServer(context, container.preferences, note, shotUri, keyboardDiag)
                    if (ok) {
                        Toast.makeText(context, "진단을 보냈어요. 고맙습니다.", Toast.LENGTH_LONG).show()
                    } else {
                        // 서버 전송 실패 → 공유 시트로 폴백(리포트 유실 방지)
                        Toast.makeText(context, "바로 전송이 안 돼 공유로 열었어요", Toast.LENGTH_LONG).show()
                        com.detailline.callfollowcrm.util.DiagnosticsReporter.share(
                            context,
                            com.detailline.callfollowcrm.util.DiagnosticsReporter.buildReport(
                                container.preferences, note, keyboardDiag
                            ),
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

    // 갈라진 손님 합치기 — **여기선 아직 아무것도 안 바뀐다.** 무엇이 어떻게 될지만 보여준다.
    mergePlans?.let { plans ->
        val lines = plans.joinToString("\n") { p ->
            val bits = ArrayList<String>()
            if (p.movingJobs > 0) bits += "일정 " + p.movingJobs + "건 옮김"
            if (p.clashingJobs > 0) bits += "⚠️ 같은 날 일정 " + p.clashingJobs + "건 겹침"
            if (p.droppedNames.isNotEmpty()) bits += "이름 '" + p.droppedNames.first() + "' 은 메모에 남김"
            "· " + p.displayPhone + "  (" + (p.loserIds.size + 1) + "명 → 1명)" +
                if (bits.isEmpty()) "" else "\n   " + bits.joinToString(" · ")
        }
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { if (!mergeBusy) mergePlans = null },
            title = {
                Text(
                    if (plans.isEmpty()) "번호 모양 " + untidyPhones + "개 정리"
                    else "갈라진 손님 " + plans.size + "쌍",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    (if (lines.isBlank()) "" else lines + "\n\n") +
                        // 🔒 번호를 한 모양으로 — **보이는 건 그대로 하이픈**이라는 걸 꼭 말해준다.
                        //   안 그러면 "내 번호 표시가 이상해지는 건가?" 하고 멈추신다. (2026-09-28)
                        (if (untidyPhones > 0)
                            "번호 " + untidyPhones + "개를 한 모양으로 맞춰요 " +
                                "(010-3404-5247 → 01034045247).\n" +
                                "화면에는 지금처럼 하이픈이 붙어 보여요 — 속에 적는 글자만 바꿔요.\n\n"
                         else "") +
                        "일정·문자·통화요약·사진은 전부 옮겨요. 적어둔 주소·금액은 안 덮어요." +
                        "\n시작 직전에 백업을 먼저 떠요 — 백업이 안 되면 안 해요." +
                        "\n\n되돌릴 수 없어요.",
                    fontSize = 13.5.sp, color = TossTextSecondary, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(enabled = !mergeBusy, onClick = {
                    mergeBusy = true
                    settingsScope.launch {
                        // 백업과 합치기를 **따로** 잡는다 — 한 덤어리로 묶으면
                        //   합치다 터져도 "백업이 안 돼서" 라고 **거짓말**을 하게 된다. (2026-09-23)
                        val backedUp = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching {
                                val bytes = com.detailline.callfollowcrm.util.DataBackup.serverBlobBytes(context)
                                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                container.backupRepository.push(b64)
                            }.getOrDefault(false)
                        }
                        val r = if (!backedUp) null else withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { container.customerMergeManager.merge(plans) }
                                .onFailure { android.util.Log.e("Merge", "합치기 실패", it) }
                                .getOrNull()
                        }
                        // 📅 합치면서 주인을 잃은 구글 일정을 지운다 — 안 지우면 달력에 유령이 남는다. (2026-09-28)
                        if (r != null && r.orphanedCalendarEventIds.isNotEmpty()) {
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    container.calendarSyncManager
                                        .deleteOrphanedEvents(r.orphanedCalendarEventIds)
                                }
                            }
                        }
                        mergeBusy = false
                        mergePlans = null
                        hasSplits = runCatching { container.customerMergeManager.hasSplits() }
                            .getOrDefault(false)   // 합쳤으면 줄이 바로 사라진다
                        untidyPhones = runCatching { container.customerMergeManager.untidyPhoneCount() }
                            .getOrDefault(0)
                        Toast.makeText(
                            context,
                            when {
                                !backedUp -> "백업이 안 돼서 합치지 않았어요 — 인터넷 확인하고 다시"
                                r == null -> "합치다 막혔어요 — 백업은 떠놓았으니 안전해요. 저한테 알려주세요"
                                r.clashingJobs > 0 ->
                                    r.mergedPairs.toString() + "쌍 합쳤어요. 같은 날 일정이 " +
                                        r.clashingJobs + "건 겹쳤으니 일정 탭에서 확인해주세요"
                                // 합친 게 없어도 모양을 고쳤으면 그걸 말해준다 — 아무 말 없으면 안 된 줄 아신다.
                                r.mergedPairs == 0 && r.tidiedPhones > 0 ->
                                    "번호 " + r.tidiedPhones + "개를 한 모양으로 맞췄어요"
                                r.tidiedPhones > 0 ->
                                    r.mergedPairs.toString() + "쌍 합치고 번호 " + r.tidiedPhones +
                                        "개를 한 모양으로 맞췄어요"
                                else -> r.mergedPairs.toString() + "쌍 합쳤어요 — 이력이 한곳에 모였어요"
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }) { Text(if (mergeBusy) "합치는 중…" else "합치기", color = TossBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !mergeBusy, onClick = { mergePlans = null }) {
                    Text("그만두기", color = TossTextTertiary)
                }
            }
        )
    }

    // 되찾은 접수서 — 누구 것인지 그대로 보여준다. 숫자만 주면 확인을 못 한다.
    resyncFound?.let { found ->
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { resyncFound = null },
            title = { Text("접수서 " + found.size + "건을 되찾았어요", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    found.joinToString("\n\n") { e ->
                        val suf = e.phoneSuffix
                        val tel = if (suf.length >= 8) suf.take(4) + "-" + suf.drop(4) else suf
                        val head = listOfNotNull(
                            tel,
                            e.dateLabel?.takeIf { it.isNotBlank() }?.let { it + " 시공" },
                            e.totalManwon?.takeIf { it > 0 }?.let { it.toString() + "만원" }
                        ).joinToString(" · ")
                        val body = listOfNotNull(
                            e.address?.takeIf { it.isNotBlank() },
                            e.itemsText?.takeIf { it.isNotBlank() }
                        ).joinToString("\n")
                        if (body.isBlank()) head else head + "\n" + body
                    } + "\n\n상담함에서 그 번호를 열면 접수서 카드가 보여요.",
                    fontSize = 13.5.sp, color = TossTextSecondary, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { resyncFound = null }) {
                    Text("확인", color = TossBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
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
            containerColor = Color.White,
            tonalElevation = 0.dp,
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
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { viewModel.consumeRestartNeeded() },
            title = { Text("다 되살렸어요", fontWeight = FontWeight.Bold) },
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
                .keyboardPadding()
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

                // ⭐ 2026-08-02 사장님 "더보기 뒤죽박죽 정리" — 항목/기능 그대로, 성격 맞는 그룹으로 재배치.
                //   (프로토 5그룹에 앱 기능 13개가 아무 데나 섞였던 것 → 성격별 6그룹으로. 새 그룹=기록·분석 / 알림·번호 관리.)
                // 인원 관리·수첩 제거(2026-08-31 사장님 "더보기 정리"). 관련 화면/nav 코드는 추후 청소.
                // ⭐ 2026-09-22 사장님 "순서도 중요도에 따라. 지금은 막 정리 없이 나열된 느낌이야"
                //   전엔 **성격별** 6묶음이었다(협업·박람회 / 기록·분석 / 내 답장 재료 / 알림·번호 관리 / 앱 설정).
                //   "재료"·"관리"·"설정"·"분석"은 **잣대가 섞인 이름**이라, 뭘 찾을 때 어느 묶음에
                //   있을지 짐작이 안 됐다. → 잣대를 하나로: **얼마나 자주 여는가.**
                //   항목은 하나도 안 없앴다. **자리만 옮겼다.**
                SettingsGroup("자주 쓰는 것") {
                    LockRow(Icons.AutoMirrored.Filled.Send, TossBlueSoft, TossBlue, "자동으로 챙기기",
                        "부재중 문자 · 시공 D-1 · 통화 요약 · 답변 준비", first = true) { subPage = "autosms" }
                    LockRow(Icons.AutoMirrored.Filled.Chat, TossBlueSoft, TossBlue, "문자 템플릿",
                        "자주 쓰는 문구 관리", onClick = onOpenTemplates)
                    LockRow(Icons.Filled.Payments, TossBlueSoft, TossBlue, "가격표",
                        "견적 만들 때 쓰이는 항목", onClick = onOpenPricingItems)
                }
                SettingsGroup("일이 생기면") {
                    LockRow(Icons.Filled.Group, AppTheme.colors.categoryBg, AppTheme.colors.category, "협업 현장",
                        "다른 사장님과 현장 하나만 같이 보기", tier = "비즈니스", first = true, onClick = onOpenCollabSites)
                    // 박람회 — 별세계(완전 분리) 진입. 카톡 스타일 전용 창구. (2026-07-21 사장님)
                    LockRow(Icons.Filled.Storefront, Color(0xFFFFF3C4), Color(0xFFC9A200), "박람회",
                        "박람회 팀 — 상담·계약·분배를 카톡처럼", onClick = onOpenExpo)
                    LockRow(Icons.Filled.BarChart, TossBlueSoft, TossBlue, "상세 리포트",
                        "매출·전환율·추천 채택률 분석", tier = "비즈니스", onClick = onOpenReport)
                    LockRow(Icons.Filled.Payments, AppTheme.colors.doneBg, AppTheme.colors.done, "협업 기록",
                        "협업 사장님별 · 월별 기록 (세금용)", tier = "비즈니스", onClick = onOpenCollabRecord)
                }
                SettingsGroup("한 번 해두면 끝") {
                    LockRow(Icons.Filled.Description, TossBlueSoft, TossBlue, "견적서·사업자 정보",
                        "상호·대표·사업자번호·직인 · 견적서에 자동 표시", first = true, onClick = onOpenBusinessInfo)
                    LockRow(Icons.Filled.DateRange, TossBlueSoft, TossBlue, "구글 캘린더 연동",
                        "시공·A/S 일정을 구글 캘린더에") { subPage = "mirror" }
                    LockRow(Icons.Filled.Notifications, TossBlueSoft, TossBlue, "알림 소리",
                        "알림 종류별 소리 고르기 · 미리듣기", onClick = onOpenSoundSettings)
                    val navLabel = com.detailline.callfollowcrm.util.NavApp.values()
                        .find { it.key == state.defaultNavAppKey }?.label ?: "카카오내비"
                    LockRow(Icons.Filled.Navigation, TossGrayBg, TossTextTertiary, "기본 네비 앱",
                        navLabel) { subPage = "nav" }
                    LockRow(Icons.Filled.Computer, TossBlueSoft, TossBlue, "시공막내 웹 (PC 사진)",
                        "PC에서 시공 사진 보기·내려받기") { subPage = "web" }
                    // 보안 설정 — 한 번 켜두면 끝이라 여기가 맞다. 전엔 자동 문자 화면 한가운데 있었다.
                    // 내 업종 — 2026-08-31 '더보기 정리' 때 같이 빠져서 **들어갈 길이 없어졌다.**
                    //   온보딩에서 한 번 놓치면 다시 고칠 방법이 없고, 그동안 AI 답변이 계속
                    //   '일반 시공 사장님' 톤으로 나간다. (2026-09-23 사장님 확인 후 되살림)
                    //   말투와 함께 AI 답변을 정하는 것이라 '내 말투 학습' 바로 위에 둔다.
                    LockRow(Icons.Filled.Category, AppTheme.colors.categoryBg, AppTheme.colors.category, "내 업종",
                        "골라두면 AI 답변이 내 업종 말로 나와요", onClick = onOpenTradeSelect)
                    LockRow(Icons.Filled.AutoAwesome, AppTheme.colors.categoryBg, AppTheme.colors.category, "내 말투 학습",
                        "나처럼 답하는 AI", tier = "프로") { subPage = "tone" }
                    // 기본 문자 앱이면 채팅+가 꺼져 있어 이 안내 불필요 → 숨김. (2026-08-02 사장님)
                    if (!isDefaultSmsApp) {
                        LockRow(Icons.AutoMirrored.Filled.Chat, TossGrayBg, TossTextTertiary, "고객 사진(문자) 받기",
                            "채팅+ 꺼서 고객 사진 놓치지 않기") { subPage = "noti" }
                    }
                }
                // ⭐ 내 데이터 지키기 — 자동으로 돌아서 거의 안 만진다. 전엔 3번째(맨 위쪽)였다. (2026-09-22 사장님)
                DataBackupSection(
                    lastBackupAt = lastBackupAt,
                    busy = backupBusy,
                    onExport = { viewModel.exportData() },
                    onImport = { showImportConfirm = true },
                    onServerBackup = { viewModel.serverBackup() },
                    onServerRestore = { showServerRestoreConfirm = true },
                    onRestoreCategories = { viewModel.serverRestoreCategoriesOnly() }
                )
                SettingsGroup("막히거나 이상하면") {
                    // 접수서 되찾기 — 서버는 접수서를 하나도 안 지운다. 앱에만 없을 때 여기서 다시 가져온다.
                    //   (2026-09-23 사장님: 복원한 폰에서 한 건이 조용히 빠져 고객 전화로 알게 됨)
                    // 같은 사람이 손님 둘로 갈라진 것 합치기 — 번호를 하이픈 있게/없게 적어서 생긴 자국이다.
                    //   (고침 b53e9bcf 로 새로 생기진 않지만 옛 것은 그대로 남아 이력이 쪼개져 쌓인다)
                    if (hasSplits || untidyPhones > 0) LockRow(
                        Icons.Filled.Merge, TossBlueSoft, TossBlue, "번호 한 줄로 모으기",
                        // 무엇이 몇 개인지 **누르기 전에** 보이게. (2026-09-28 사장님)
                        buildString {
                            if (hasSplits) append("둘로 갈라진 손님 합치기")
                            if (hasSplits && untidyPhones > 0) append(" · ")
                            if (untidyPhones > 0) append("번호 모양 ${untidyPhones}개 정리")
                        },
                        first = true
                    ) {
                        if (!mergeBusy) {
                            mergeBusy = true
                            settingsScope.launch {
                                val plans = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching { container.customerMergeManager.findPlans() }.getOrNull()
                                }
                                mergeBusy = false
                                when {
                                    plans == null -> Toast.makeText(context, "찾다가 막혔어요 — 잠시 후 다시", Toast.LENGTH_LONG).show()
                                    // 합칠 쌍이 없어도 **모양만 고칠 줄**이 있으면 그 길로 보낸다. (2026-09-28)
                                    plans.isEmpty() && untidyPhones > 0 -> mergePlans = emptyList()
                                    plans.isEmpty() -> Toast.makeText(context, "번호가 다 한 줄로 모여 있어요", Toast.LENGTH_LONG).show()
                                    else -> mergePlans = plans
                                }
                            }
                        }
                    }
                    LockRow(Icons.Filled.Refresh, TossBlueSoft, TossBlue, "접수서 다시 가져오기",
                        "고객이 낸 접수서가 안 보이면 눌러주세요") {
                        if (!intakeResyncBusy) {
                            intakeResyncBusy = true
                            settingsScope.launch {
                                val found = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching {
                                        com.detailline.callfollowcrm.ai.IntakeSyncManager(container)
                                            .resyncMissing(context)
                                    }.getOrNull()
                                }
                                intakeResyncBusy = false
                                // 들어온 게 있으면 **무엇이 들어왔는지** 창으로 보여준다.
                                if (found != null && found.isNotEmpty()) resyncFound = found
                                else Toast.makeText(
                                    context,
                                    if (found == null) "서버에 잠깐 연결이 안 돼요 — 잠시 후 다시"
                                    else "빠진 접수서가 없어요 — 다 들어와 있어요",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    LockRow(Icons.Filled.Block, AppTheme.colors.unpaidBg, AppTheme.colors.unpaid, "스팸 차단 번호",
                        "스팸 등록한 번호 · 여기서 풀기", onClick = onOpenSpamList)
                    LockRow(Icons.Filled.Person, AppTheme.colors.categoryBg, AppTheme.colors.category, "지인 번호",
                        "문자함 [지인] 에 모여요 · 시공막내가 안 잡음 · 풀려면 여기서", onClick = onOpenPersonalList)
                    // 문제 신고 / 진단 보내기 (2026-07-22 사장님) — 앱이 안 죽는 '이상 동작'을 직접 신고.
                    LockRow(Icons.Filled.BugReport, AppTheme.colors.unpaidBg, AppTheme.colors.unpaid, "문제 신고 / 진단 보내기",
                        "문자가 깨지는 등 이상하면 눌러서 알려주세요") { showDiagnostics = true }
                }

                // 배지를 숨기는 동안엔 이 안내도 설명할 대상이 없다. (2026-09-20 사장님)
                if (SHOW_TIER_BADGES) Text(
                    "'비즈니스'·'프로' 표시가 있어도 베타 기간엔 모두 무료로 열려 있어요.",
                    fontSize = 11.5.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                )
                // 계정 삭제 — '도움말' 안에 있었다. **지우는 일은 도움말이 아니다.**
                //   맨 아래 조용한 한 줄로 내린다(기능·경로 그대로). (2026-09-20 사장님)
                //   ⚠️ 없애면 안 된다 — 구글 정책상 **앱 안에 계정 삭제 경로**가 있어야 한다. (2026-09-17)
                Text(
                    "계정 삭제",
                    fontSize = 12.5.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDeleteAccount = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
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
                        Icons.Filled.AutoAwesome, AppTheme.colors.categoryBg, AppTheme.colors.category,
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

    // 인라인 오버레이는 Scaffold 뒤에 놓아야 위에 그려진다.
    //   앞에 두면 컴파일도 되고 상태도 바뀌는데 본문이 덮어서 '눌러도 아무 일 없음' 으로 보인다. (2026-09-17 실기)
    //   DiagnosticsDialog 는 Dialog(별도 창)라 위치와 무관 — 같이 두면 헷갈린다.
    if (showDeleteAccount) {
        DeleteAccountSheet(onDismiss = { showDeleteAccount = false })
    }
}


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
                    Text("통화 후 문자 보내기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
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
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.primaryBg)
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
                    Text("통화 후 카드 미리보기", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
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
            "인터넷 연결을 확인해주세요. 잠시 후 자동으로 다시 연결돼요. 답변 추천이 안 떠도 메시지는 보낼 수 있어요."
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
                "이번 달 사용량은 아직 안 나와요. 준비 중이에요.",
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
    // 🌙 마감 브리핑 — 새로 깐 분은 꺼진 채로 시작한다. (2026-09-30 사장님)
    var briefOn by remember { mutableStateOf(prefs.dailyBriefEnabled) }
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

    // 🔴 전엔 라벨이 "상황이 되면 자동으로" 하나뿐이라, 문자·통화·보안이 다 한 덩어리로 보였다.
    //   이름과 속을 맞춰 넷으로 갈랐다. (2026-09-22 사장님)
    Text("문자로 챙기기", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 6.dp))

    // 묶음 하나 = 카드 하나. 소제목이 이미 묶음을 말하니 줄마다 카드가 뜰 이유가 없다. (2026-09-22 사장님)
    AutoGroup {
        // ① 부재중 자동 응답
        //   줄 사이 간격은 [AutoGroup] 의 가는 선이 맡는다 — Spacer 를 넣지 말 것.
        AutoCard(Icons.Filled.Phone, TossBlueSoft, TossBlue, "부재중 자동 응답", "즉시 발송", "전화 못 받으면 자동으로 문자 발송",
            autoReplyOn, onAutoReplyToggle, initiallyExpanded = expandMissed, first = true) {
            AutoDotLabel(TossBlue, "처음 연락한 고객 (신규)")
            AutoTextArea(missedNew) { missedNew = it; prefs.autoMissedNewText = it }
            Spacer(Modifier.height(10.dp))
            AutoDotLabel(TossSuccess, "다시 연락한 고객 (단골·기존)")
            AutoTextArea(missedReturn) { missedReturn = it; prefs.autoMissedReturnText = it }
            AutoNote("전화를 못 받으면 자동으로 나가요. 보내기 전 10초 안에 취소할 수 있어요.\n같은 번호엔 하루 한 번만 — 하루 안에 이미 문자가 오갔으면 건너뛰어요.")
        }

        // ② 시공 하루 전 안내 (D-1)
        AutoCard(Icons.Filled.CalendarMonth, Color(0xFFFFF1E6), Color(0xFFB8780A), "시공 하루 전 안내 (D-1)", null,
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

        // 🌙 마감 브리핑 — 밤 9시에 그날 정리. **끌 수 있어야 한다.** (2026-09-30 사장님)
        //   "처음 가입하는 사람들은 깜짝 놀랄 수 있을 것 같은데. 기본값으로 꺼져 있게 해줘."
        //   전엔 스위치가 아예 없어서 끌 방법이 없었다.
        AutoCard(
            Icons.Filled.BarChart, Color(0xFFEDEBFB), Color(0xFF5A4A7A),
            "마감 브리핑", null, "밤 9시 · 그날 한 줄 정리",
            briefOn, { briefOn = it; prefs.dailyBriefEnabled = it }
        ) {
            AutoNote(
                "밤 9시에 오늘 새로 온 손님·받은 돈·내일 시공을 한 번에 알려줘요.\n" +
                    "고객에게 나가는 건 아무것도 없어요 — 사장님만 보는 알림이에요."
            )
        }

        // ③ 오늘 시공 도착 안내
        AutoCard(Icons.Filled.Place, AppTheme.colors.doneBg, AppTheme.colors.doneText, "오늘 시공 도착 안내", null, "상담함 오늘시공 섹션 · 보내기 전 확인",
            arrOn, { arrOn = it; prefs.arrivalAutoEnabled = it; onArrivalToggle(it) }) {
            AutoTextArea(arrText) { arrText = it; prefs.arrivalAutoText = it }
            AutoNote("상담함의 오늘시공 도착 안내와 같은 문구예요. 위치 감지는 준비 중이라 지금은 사장님 확인 후 보내는 안내로 사용해요.")
        }
    }

    SettingsSubLabel("통화 때 챙기기")


    // 묶음 하나 = 카드 하나. 여긴 손으로 짠 카드 세 장이었다 — 껍데기만 벗겨 줄로. (2026-09-22 사장님)
    AutoGroup {
        // ④ 통화 자동 요약 (2026-06-14 사장님) — 통화 끝나면 에이닷 녹음/텍스트를 자동 요약(공유 안 눌러도 됨).
        var autoSumOn by remember { mutableStateOf(prefs.autoSummaryEnabled) }
        // 🔴 녹음을 **서버로 보내는 것**은 고지 + 명시적 동의를 받은 뒤에만. (2026-09-17 플레이 정책 점검)
        //   전엔 이 토글이 기본 ON 이라, 아무 고지 없이 녹음 파일이 올라갔다. 구글 정책 위반이다:
        //     "Must be granted by the user **before** your app can begin to collect or access
        //      the personal and sensitive user data" (Play · User Data policy)
        //   토글만으로는 안 켜진다 — prefs.callSummaryAllowed = 토글 ON **그리고** 동의 완료.
        var consented by remember { mutableStateOf(prefs.callSummaryConsented) }
        var showConsent by remember { mutableStateOf(false) }
        AutoRow(first = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppTheme.colors.categoryBg),
                    contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = AppTheme.colors.category, modifier = Modifier.size(17.dp))
                    }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("통화 자동 요약", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Text("통화가 끝나면 통화 녹음을 자동으로 요약해 통화카드에 붙여요 (공유 버튼 안 눌러도 됨)",
                        fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp)
                    if (autoSumOn && !consented) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "아직 동의 전이라 요약이 안 돌아요 — 눌러서 내용을 확인해주세요",
                            fontSize = 11.5.sp, color = Color(0xFFB8780A), fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp,
                            modifier = Modifier.clickable { showConsent = true }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = autoSumOn && consented,
                    onCheckedChange = { want ->
                        if (want && !consented) {
                            // 켜려는데 아직 동의 전 → 고지부터. 여기서 바로 켜지 않는다.
                            showConsent = true
                        } else {
                            autoSumOn = want
                            prefs.autoSummaryEnabled = want
                        }
                    }
                )
            }
        }
        if (showConsent) {
            CallSummaryConsentDialog(
                onAgree = {
                    prefs.callSummaryConsented = true; consented = true
                    prefs.autoSummaryEnabled = true; autoSumOn = true
                    showConsent = false
                },
                onDecline = {
                    prefs.callSummaryConsented = false; consented = false
                    prefs.autoSummaryEnabled = false; autoSumOn = false
                    showConsent = false
                }
            )
        }

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
        AutoRow {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppTheme.colors.primaryBg),
                        contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Phone, null, tint = TossTextSecondary, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("전화 오는 사람 미리보기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                        Text("전화가 오면 손님 카드가 떠요 — 현장 주소·돈·지난 통화 요약·마지막 문자. 받은 뒤에도 통화 내내 남아요 (전화 화면은 안 가림 · 신규는 노란 띠)",
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
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AppTheme.colors.cautionBg)
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
                            Text("'다른 앱 위에 표시' 권한이 필요해요", fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, color = Color(0xFFB8780A))
                            Text("여기를 눌러 허용하면 전화 올 때 고객 카드가 떠요", fontSize = 12.sp, color = Color(0xFFB8780A))
                        }
                    }
                }
                // 미리보기 — 전화를 기다리지 않고 카드를 바로 확인. (2026-09-17)
                //   오버레이는 실제 전화가 와야만 볼 수 있어서 만들고도 확인이 어려웠다.
                if (callerCardOn && overlayGranted) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossBlueSoft)
                            .clickable {
                                com.detailline.callfollowcrm.service.IncomingCallOverlay.showPreview(ctx)
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("카드 미리보기", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                    }
                }
            }
        }

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
                    ctx, recLabel?.let { "연결됐어요 $it" } ?: "녹음 폴더 연결됐어요. 이제 통화 끝나면 자동으로 요약돼요.",
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
                        if (n > 0) "통화 녹음 ${n}개를 찾았어요. 이제 통화 끝나면 자동으로 요약돼요"
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
        AutoRow {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppTheme.colors.categoryBg),
                        contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Mic, null, tint = TossTextSecondary, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("통화 녹음 자동 찾기${if (recFolderConnected) " · 연결됨" else ""}", fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, color = TossTextPrimary)
                        Text(
                            if (recFolderConnected) (recLabel?.let { "$it" } ?: "통화 끝나면 녹음으로 자동 요약돼요 (↑ 안 눌러도 됨)")
                            else "버튼 한 번이면 통화 녹음을 앱이 알아서 찾아드려요. 폴더 안 찾아도 돼요.",
                            fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (recFolderConnected) AppTheme.colors.surfaceMuted else AppTheme.colors.primary)
                            .clickable {
                                if (recFolderConnected) {
                                    com.detailline.callfollowcrm.recording.AdotFolderScanner.scanIfConnected(ctx, recAppContainer) { }
                                    recLabel = com.detailline.callfollowcrm.recording.AdotFolderScanner.connectedLabel(ctx)
                                    android.widget.Toast.makeText(ctx,
                                        recLabel?.let { "확인했어요 $it" } ?: "통화 녹음을 보고 있어요",
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
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.primary,
                        modifier = Modifier.clickable { recFolderLauncher.launch(null) }.padding(vertical = 2.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    Text("정해둔 때마다", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))

    // ④ 정기 문자 예약 (링크)
    TossCard(onClick = onOpenRecurring) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppTheme.colors.doneBg),
                contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Autorenew, null, tint = TossTextSecondary, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("정기 문자 예약", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Text("방역·점검·안부 — 각 고객 날짜 기준 주기 발송", fontSize = 12.sp, color = TossTextTertiary)
            }
            Text("›", fontSize = 20.sp, color = TossTextTertiary)
        }
    }
    Spacer(Modifier.height(14.dp))

    SettingsSubLabel("문자가 오면")


    // 묶음 하나 = 카드 하나. 사이 Spacer 는 뺐다 — 가는 선이 대신한다. (2026-09-22 사장님)
    AutoGroup {
        // 받은 문자 알림 (보존) — 설명 명확화: 이건 '알림창'만 담당(AI 준비와 별개). (2026-07-16 사장님 혼동)
        AutoRow(first = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("받은 문자 알림", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Text("고객 문자가 오면 알림창을 띄워요 (알림만 — AI 준비는 아래 스위치)", fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp)
                }
                Switch(checked = incomingNotifyOn, onCheckedChange = onIncomingNotifyToggle)
            }
        }
        // AI 답변 준비 (2026-07-16 사장님) — 문자 오면 막내가 추천 답변을 미리 만들지 여부. '받은 문자 알림'과 별개.
        //   OFF = 마스코트 '답변 준비 중' 애니·홈 "AI 답변 준비 중"·서버 호출 전부 없음.
        AutoRow {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(AppTheme.colors.categoryBg),
                        contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.AutoAwesome, null, tint = AppTheme.colors.category, modifier = Modifier.size(15.dp)) }
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
        // 광고·스팸 번호 앞자리 — 비주얼 정리(2026-06-14 사장님: 디자인 개선). 기능 동일.
        AutoRow {
            Column {
                // 아이콘 헤더
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(AppTheme.colors.unpaidBg),
                        contentAlignment = Alignment.Center
                    ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Block, null, tint = TossTextSecondary, modifier = Modifier.size(17.dp)) }
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
                                Modifier.clip(RoundedCornerShape(999.dp)).background(AppTheme.colors.unpaidBg)
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
                                    android.widget.Toast.makeText(ctx, "‘$p’ 저장됐어요 — 위 ‘등록된 앞자리’에 추가됐어요", android.widget.Toast.LENGTH_SHORT).show()
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
}

/** 자동으로 챙기기 화면의 소제목 — 묶음 이름이 곧 "언제". (2026-09-22) */
@Composable
private fun SettingsSubLabel(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 2.dp)
    )
}

@Composable
private fun AutoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    badge: String?,
    sub: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    initiallyExpanded: Boolean = false,
    /** 묶음의 **첫 줄**이면 위 구분선을 안 긋는다. (2026-09-22) */
    first: Boolean = false,
    body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    // 🔴 전엔 줄마다 자기 카드(그림자+흰 바탕)였다. 앱의 다른 목록은 다 '묶음 하나 = 카드 하나' 인데
    //   여기만 남아 있었다. (2026-09-22 사장님 "여기도 정리") → [AutoGroup] 안에 사는 줄이 된다.
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
            ) {
                // 이모지 대신 **앱이 그리는 아이콘** — 📅 는 갤럭시에서 "JUL 17" 달력으로 나왔다. (2026-09-21 사장님)
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(iconBg),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                        if (badge != null) {
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(TossBlueSoft)
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

/**
 * 자동으로 챙기기 — **묶음 하나에 카드 한 장.** 줄들은 이 안에 산다. (2026-09-22 사장님 "여기도 정리")
 *   더보기 목록·가격표·문자 템플릿·스팸·알림 소리와 같은 규칙.
 */
/** [AutoGroup] 안의 줄 하나 — 손으로 짠 카드에서 껍데기만 벗긴 것. (2026-09-22) */
@Composable
private fun AutoRow(first: Boolean = false, content: @Composable () -> Unit) {
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
        Box(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) { content() }
    }
}

@Composable
private fun AutoGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(AppShape.lg).background(Color.White),
        content = content
    )
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


/**
 * 통화 녹음 서버 전송 — **눈에 띄는 고지와 동의**. (2026-09-17 플레이 정책 점검)
 *
 * 구글 요건(Play · User Data policy — prominent disclosure & consent)을 그대로 따른다:
 *   · "Must be within the app itself, not only … on a website"      → 앱 안에서 띄운다
 *   · "Must describe the data being accessed or collected"          → 녹음 파일이라고 적는다
 *   · "Must explain how the data will be used and/or shared"        → 서버 전송·받아쓰기·AI·파기까지
 *   · "Must require affirmative user action"                        → [동의하고 켜기] 를 눌러야만 켜진다
 *   · "Must not interpret navigation away … as consent"             → 바깥 탭·뒤로가기로 안 닫힌다
 *   · "Must not use auto-dismissing … messages"                     → 저절로 사라지지 않는다
 *
 * ⚠️ 문구를 고칠 때는 si0in.kr/consent/required 와 처리방침도 같이 맞출 것.
 *    셋 중 하나만 달라지면 그게 '신고 내용과 실제가 다름' 이 된다.
 */


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
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Explore, null, tint = TossTextSecondary, modifier = Modifier.size(18.dp))
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
                            .background(if (selected) TossBlue else AppTheme.colors.surfaceMuted)
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
                    "아직 미선택 — 첫 길찾기 누르면 같은 선택지가 떠요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TossTextTertiary
                )
            }
        }
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


/**
 * 더보기 상단 막내 비서 카드 (프로토 .agent-card 간소화).
 *   캐릭터(Mascot) + 이름 + 학습 안내. 학습 수치는 실제 보유값(사장님 톤 샘플 수).
 */
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
            .background(Brush.linearGradient(listOf(AppTheme.colors.primaryBg, AppTheme.colors.categoryBg)))
            .border(1.dp, Color(0xFFE6EAFB), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 막내 (단계는 이름 옆 칩으로만 — 이모지 배지는 폰마다 다르게 그려져서 뺐다)
            Mascot(sizeDp = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // agent-name + lv 칩
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("우리 막내", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                    Spacer(Modifier.width(7.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.horizontalGradient(listOf(AppTheme.colors.primary, AppTheme.colors.category)))
                            .padding(horizontal = 9.dp, vertical = 2.dp)
                    ) {
                        Text(card.stageLabel, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
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
                                    .background(Brush.horizontalGradient(listOf(AppTheme.colors.primary, AppTheme.colors.category)))
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("말투 ${card.tonePct}%", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                }
            }
        }
        Text(
            (if (card.togetherMonths > 0) "사장님이랑 손발 맞춘 지 ${card.togetherMonths}개월 · " else "") +
                "함께한 상담 ${card.consultCount}건 · 시공 ${card.doneJobs}건",
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
        // 🔴 묶음 하나 = **카드 한 장**. (2026-09-22 사장님 "정리 없이 나열된 느낌")
        //   전엔 **줄마다 카드**라 17개가 각자 떠 있었다. 묶음 이름만 붙었을 뿐
        //   줄끼리 묶여 보이지 않았다. 그림자도 17개였다.
        Column(
            Modifier.fillMaxWidth()
                .tossCardShadow(RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
        ) { content() }
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

        // 🔴 전엔 **같은 말을 두 번** 했다 — 위 띠에 "알아서 저장하고 있어요", 바로 아래 카드에 또
        //   "파일 없이 서버에 안전하게 보관해요…". 한 덩어리로 합쳤다. (2026-09-22 사장님)
        //   그리고 [서버에 백업하기]가 화면에서 **제일 큰 버튼**이었다. 바로 위에서 "알아서 되고 있다"고
        //   해놓고 손으로 누르는 버튼이 제일 크면 앞뒤가 안 맞는다 → 작은 버튼으로.
        //   색도 앱에 뜻이 없던 짙은 청록(#1E6E6A) 대신 **초록** — 잘 되고 있다는 뜻이다.
        Column(
            Modifier.fillMaxWidth()
                .tossCardShadow(RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(15.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    if (recent) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                    tint = if (recent) AppTheme.colors.done else AppTheme.colors.caution,
                    modifier = Modifier.size(18.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        if (recent) "알아서 지키고 있어요" else "아직 서버에 저장된 게 없어요",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (recent) AppTheme.colors.doneText else AppTheme.colors.cautionText
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (recent) "하루 한 번 서버에 저장해요. 폰을 바꾸거나 앱을 지워도 되살릴 수 있어요. (사진 제외)"
                        else "하루 한 번 알아서 올라가지만, 지금 바로 챙기려면 [지금 백업]을 눌러주세요.",
                        fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "마지막 백업 · $lastLabel",
                    fontSize = 12.sp, color = TossTextTertiary, modifier = Modifier.weight(1f)
                )
                if (busy) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = AppTheme.colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp)
                    )
                } else {
                    BackupBtn("지금 백업") { onServerBackup() }
                    Spacer(Modifier.width(6.dp))
                    BackupBtn("되살리기") { onServerRestore() }
                }
            }
            Spacer(Modifier.height(10.dp))
            // 카테고리·태그만 — 카테고리가 사라졌을 때만 쓰는 드문 일이라 **조용하게**.
            Text(
                "카테고리·태그만 되살리기 (일당 등)",
                fontSize = 11.5.sp, color = TossTextTertiary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !busy) { onRestoreCategories() }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
    }
}


/** 백업 카드의 작은 버튼 — 자주 하는 일이 아니라 크게 둘 이유가 없다. (2026-09-22) */
@Composable
private fun BackupBtn(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryText,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(AppTheme.colors.primaryBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp)
    )
}

/**
 * **베타 동안 [비즈니스]·[프로] 배지를 숨긴다.** (2026-09-20 사장님 "응 숨기자")
 *   지금은 전부 무료라 배지에 **뜻이 없는데** 줄에서 제일 진한 색이었다. 그래서 사장님이
 *   유료인 줄 알고 **핵심 기능을 안 누르신 적**이 있다(아래 LockRow 옛 주석).
 *   요금을 받기 시작하면 이 값만 true 로 되돌리면 배지가 그대로 살아난다.
 */
private const val SHOW_TIER_BADGES = false

/**
 * 프로토 .lockcard — 아이콘 박스(42·radius13) + 제목·부제 + 꺾쇠/티어태그.
 *   tier: null=꺾쇠 / "프로"(파랑) / "비즈니스"(보라). locked=true → opacity .6.
 *   ⚠️ 배지 표시 여부는 [SHOW_TIER_BADGES].
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
    /** 묶음의 **첫 줄**이면 위 구분선을 안 긋는다. (2026-09-22) */
    first: Boolean = false,
    onClick: () -> Unit
) {
    val rowInteraction = remember { MutableInteractionSource() }
    // 자기 카드(그림자+흰 바탕)를 버렸다 — 이제 묶음 카드 **안에 사는 줄**이다.
    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(rowInteraction)
            .graphicsLayer { alpha = if (locked) 0.6f else 1f }
            .clickable(interactionSource = rowInteraction, indication = null, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).background(iconBg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = TossTextTertiary)
            }
        }
        when {
            !SHOW_TIER_BADGES ->
                Icon(Icons.Filled.ChevronRight, null, tint = TossTextTertiary, modifier = Modifier.size(18.dp))
            tier == "프로" -> TierTag("프로", TossBlue)
            tier == "비즈니스" -> TierTag("비즈니스", AppTheme.colors.category)
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
        if (n > 0) Toast.makeText(context, "녹음 ${n}개를 찾았어요 통화가 끝나면 자동 요약돼요", Toast.LENGTH_LONG).show()
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

    // 다 끝나면 **아예 안 띄운다.** (2026-09-22 사장님)
    //   전엔 "시작 준비 다 됐어요 (5/5)" 한 줄이 남았는데, **할 일이 없는 줄**이 화면 위쪽에서
    //   카드 하나를 계속 먹었다. 여기 있던 항목은 전부 설정 안에서 따로 열 수 있다.
    if (all && collapsed) return
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
                Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.surfaceMuted))
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
            .background(Brush.linearGradient(listOf(AppTheme.colors.category, Color(0xFF5B3FE0))))
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
                    if (toneOn) "막내가 사장님 말투를 ${tonePct}% 따라 해요"
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
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(AppTheme.colors.categoryBg),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, null, tint = AppTheme.colors.category, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("말투 학습 ${if (toneOn) "켜짐" else "꺼짐"}", fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (toneOn) "추천 답변이 사장님 말투로 만들어지고, 채팅에 ‘내 말투’ 배지가 붙어요"
                    else "추천 답변이 일반 AI 말투로 나와요",
                    fontSize = 12.sp, color = TossTextTertiary
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = toneOn,
                onCheckedChange = { toneOn = it; prefs.toneLearnEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = AppTheme.colors.category
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
                        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(4.dp)).background(AppTheme.colors.category))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("$sent / $totalN 건 학습 중…", fontSize = 11.sp, color = TossTextSecondary)
                }
                ragUploadedCount == 0 && !ragConsented -> {
                    Text("동의하면 보낸 문자 ${formatThousands(ragAvailable)}건을 사장님 전용 서버에 올려 말투를 배워요. 밖으로는 안 나가요.",
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
                        Text("새 문자 ${pending}건 대기", fontSize = 11.sp, color = TossTextSecondary)
                        Spacer(Modifier.height(6.dp))
                        Text("지금 동기화", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.category, modifier = Modifier.clickable { onUpload() })
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = TossSuccess, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("최신 상태로 학습됨", fontSize = 12.sp, color = TossSuccess, fontWeight = FontWeight.Bold)
                        }
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
    ToneTeachButton(Icons.AutoMirrored.Filled.Chat, "말투 세부 설정",
        if (signature.isBlank()) "꼭 쓰는 인사말 등록" else "시그니처: $signature") { showSignatureDialog = true }
    Spacer(Modifier.height(14.dp))

    // ── info-note (개인정보) ──
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Filled.Lock, null, tint = TossTextTertiary, modifier = Modifier.size(14.dp))
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
            placeholder = "편하게 물어보세요",
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
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Chat, null, tint = TossTextSecondary, modifier = Modifier.size(14.dp))
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
            tag = "내 말투", tagBg = AppTheme.colors.categoryBg, tagColor = AppTheme.colors.category, mark = "사장님처럼",
            msg = ex.mine, msgBg = AppTheme.colors.primaryBg, msgBorder = Color(0xFFECE5FF)
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
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tagBg).padding(horizontal = 8.dp, vertical = 2.dp)
            )
            if (mark != null) {
                Spacer(Modifier.weight(1f))
                Text(mark, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.category)
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
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppTheme.colors.categoryBg),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AppTheme.colors.category, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Text(desc, fontSize = 11.5.sp, color = TossTextTertiary)
        }
        Text(count, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.category)
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
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppTheme.colors.categoryBg),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AppTheme.colors.category, modifier = Modifier.size(17.dp))
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
        // 글을 적어 저장하는 창은 **금액 입력 창과 같은 버튼 옷**. (2026-09-21 사장님)
        //   전엔 여기만 그냥 글씨라 창마다 달라 보였다.
        confirmButton = {
            Row(Modifier.fillMaxWidth()) {
                com.detailline.callfollowcrm.presentation.component.TossSecondaryButton(text = "취소", onClick = onDismiss, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(9.dp))
                com.detailline.callfollowcrm.presentation.component.TossPrimaryButton(text = "저장", onClick = { onConfirm(text) }, modifier = Modifier.weight(1f))
            }
        },
        dismissButton = null,
        containerColor = Color.White,
        tonalElevation = 0.dp,   // 흰 창에 회색이 덧칠되는 것 끄기 (2026-09-21 사장님)
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
                        Text(if (shot == null) "스크린샷 첨부" else "스크린샷 첨부됨 ✓",
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
        containerColor = Color.White,
        tonalElevation = 0.dp,   // 흰 창에 회색이 덧칠되는 것 끄기 (2026-09-21 사장님)
    )
}

