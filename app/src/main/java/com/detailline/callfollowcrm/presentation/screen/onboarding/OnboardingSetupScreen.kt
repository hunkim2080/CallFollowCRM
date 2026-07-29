package com.detailline.callfollowcrm.presentation.screen.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.presentation.component.InlineDiagPrompt
import com.detailline.callfollowcrm.recording.AdotFolderScanner
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.util.DefaultSmsAppHelper
import com.detailline.callfollowcrm.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 연결 마법사 (온보딩 마지막 — 권한 화면 다음). 프로토 넘는 새 설계(docs/PLAN_onboarding_redesign.md, 2026-07-28 사장님 승인).
 * 어르신도 쉽게: 한 화면 = 한 가지 + 큰 버튼 하나 + 쉬운 말 + "나중에" 탈출구.
 * v1 = 통화녹음 연결(묻혀있던 핵심가치를 앞으로) + 녹음 0개 시 삼성 통화녹음 켜기 안내. (가격표·답장은 후속 증분)
 */
// 색은 디자인 토큰(프로토 :root)과 일치시킴 — 화면마다 다른 먹색/초록/회색 드리프트 제거(2026-07-29).
private val Blue = TossBlue                  // #3182F6
private val Green = TossSuccess              // #16C172 (기존 #12B886 드리프트 → 토큰)
private val Ink = TossTextPrimary            // #0B0F19 (기존 #191F28 드리프트 → 토큰)
private val Sub = TossTextSecondary          // #5A6472 (기존 #6B7684 드리프트 → 토큰)
private val SoftBlue = TossBlueSoft          // #EEF4FF
private val SoftGreen = Color(0xFFEAFBF2)    // 성공 틴트 — 전용 토큰 없어 유지

@Composable
fun OnboardingSetupScreen(
    onFinish: () -> Unit,
    preferences: AppPreferences
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // 체크리스트를 실제 상태로 — 거짓 "완료 ✓" 금지(수동 모드로 건너뛰었으면 미완료로 정직하게 표시). 2026-07-29.
    val smsDefault = DefaultSmsAppHelper.isCurrentDefault(ctx)
    val permsOk = PermissionHelper.allMissingNonNotification(ctx).isEmpty()

    var step by remember { mutableStateOf(0) }            // 0 안내, 1 통화녹음, 2 완료
    var recResult by remember { mutableStateOf<Int?>(null) }   // null=미스캔, N=찾은 개수(0 포함)
    var scanning by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf<String?>(null) }     // 0개 안내: null=방식선택, "samsung"/"adot"
    // 가이드(켜기 안내)를 보고 "다시 찾기" 했는데도 또 0개면 = 우리 코드가 못 잡는 것 → 진단 보내기 노출. (2026-07-29 사장님)
    var guideRescanFailed by remember { mutableStateOf(false) }

    fun doScan() {
        scanning = true
        scope.launch {
            AdotFolderScanner.enableMediaStore(ctx)
            val n = withContext(Dispatchers.IO) {
                runCatching { AdotFolderScanner.countMediaStoreCandidates(ctx) }.getOrDefault(0)
            }
            recResult = n
            if (n == 0 && guide != null) guideRescanFailed = true   // 가이드 화면에서 재시도했는데 여전히 0개
            scanning = false
        }
    }

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doScan() else recResult = 0   // 거부 → 0개 안내(수동으로 켜게)
    }

    fun tryFind() {
        if (AdotFolderScanner.hasAudioPermission(ctx)) doScan()
        else audioLauncher.launch(AdotFolderScanner.audioPermission())
    }

    Column(
        Modifier.fillMaxSize().background(Color.White)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp)
    ) {
        when (step) {
            // ── ① 시작 안내 ──
            0 -> StepScaffold(
                icon = "🐣", iconBg = SoftBlue,
                title = "거의 다 됐어요!\n마지막으로 통화 녹음만 연결하면 끝",
                sub = "어렵지 않아요. 큰 버튼 하나만 누르면 돼요.",
                primaryText = "시작하기",
                onPrimary = { step = 1 }
            ) {
                Column(Modifier.align(Alignment.CenterHorizontally).padding(top = 26.dp)) {
                    ReadyRow(if (smsDefault) "✓" else "–", "기본 문자 앱", done = smsDefault)
                    ReadyRow(if (permsOk) "✓" else "–", "권한 허용", done = permsOk)
                    ReadyRow("3", "통화 녹음 연결", done = false)
                }
            }

            // ── ② 통화 녹음 연결 ──
            1 -> {
                val r = recResult
                when {
                    // 2-a. 찾기 전
                    r == null -> StepScaffold(
                        icon = "📞", iconBg = SoftBlue,
                        title = "통화 녹음을 연결할게요",
                        sub = "통화가 끝나면 막내가 자동으로 요약해드려요.\n녹음 파일은 막내가 알아서 찾아요 — 폴더 고를 필요 없어요.",
                        primaryText = if (scanning) "찾는 중…" else "자동으로 찾기",
                        onPrimary = { if (!scanning) tryFind() },
                        laterText = "나중에 할게요",
                        onLater = { step = 2 }
                    ) {
                        HintBar("에이닷·삼성·T전화 어디에 녹음돼도 찾아드려요.")
                    }
                    // 2-b. 찾음 (>0)
                    r > 0 -> StepScaffold(
                        icon = "🎉", iconBg = SoftGreen, big = true,
                        title = "녹음 ${r}개를 찾았어요!",
                        sub = "이제 통화가 끝나면 자동으로 요약이 만들어져요.",
                        primaryText = "다음",
                        primaryGreen = true,
                        onPrimary = { step = 2 }
                    )
                    // 2-c. 0개 → 방식 고르기 + 켜기 안내 (홍보 유입 첫 이탈 방어선)
                    else -> {
                        if (guide == null) {
                            RecMethodPicker(onPick = { guide = it }, onLater = { step = 2 })
                        } else {
                            RecGuide(
                                method = guide!!,
                                scanning = scanning,
                                onOpen = { if (guide == "adot") openAdot(ctx) else openSamsungDialer(ctx) },
                                onRescan = { if (!scanning) tryFind() },
                                onSwitch = { guide = if (guide == "adot") "samsung" else "adot" },
                                onLater = { step = 2 },
                                showDiag = guideRescanFailed,
                                preferences = preferences
                            )
                        }
                    }
                }
            }

            // ── ③ 완료 ──
            else -> StepScaffold(
                icon = "🐤", iconBg = SoftBlue,
                title = "이제 준비 끝!",
                sub = "막내가 옆에서 도와드릴게요.",
                primaryText = "시작하기",
                primaryGreen = true,
                onPrimary = onFinish
            ) {
                Column(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp)) {
                    ReadyRow(if (smsDefault) "✓" else "–", "기본 문자 앱", done = smsDefault)
                    ReadyRow(if (permsOk) "✓" else "–", "권한 허용", done = permsOk)
                    ReadyRow(
                        if ((recResult ?: 0) > 0) "✓" else "–",
                        recResult?.let { if (it > 0) "통화 녹음 (${it}개)" else "통화 녹음 (나중에)" } ?: "통화 녹음 (나중에)",
                        done = (recResult ?: 0) > 0
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "가격표·자주 쓰는 답장은 홈에서 언제든 자동으로 만들 수 있어요.",
                    fontSize = 13.sp, color = Sub, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.StepScaffold(
    icon: String,
    iconBg: Color,
    title: String,
    sub: String,
    primaryText: String,
    onPrimary: () -> Unit,
    big: Boolean = false,
    primaryGreen: Boolean = false,
    laterText: String? = null,
    onLater: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Spacer(Modifier.height(if (big) 56.dp else 44.dp))
    // 아이콘 — 항상 가운데
    if (big) {
        Text(icon, fontSize = 64.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    } else {
        Box(
            Modifier.align(Alignment.CenterHorizontally).size(92.dp).background(iconBg, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 46.sp) }
    }
    Spacer(Modifier.height(22.dp))
    Text(
        title, fontSize = 23.sp, fontWeight = FontWeight.Black, color = Ink,
        lineHeight = 31.sp, textAlign = TextAlign.Center, letterSpacing = (-0.5).sp,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    Text(
        sub, fontSize = 15.sp, color = Sub, lineHeight = 22.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    content?.invoke(this)
    Spacer(Modifier.weight(1f))
    Box(
        Modifier.fillMaxWidth()
            .background(if (primaryGreen) Green else Blue, RoundedCornerShape(16.dp))
            .clickable { onPrimary() }
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) { Text(primaryText, fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color.White) }
    if (laterText != null && onLater != null) {
        Box(
            Modifier.fillMaxWidth().clickable { onLater() }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text(laterText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8B95A1)) }
    } else {
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ReadyRow(mark: String, label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Box(
            Modifier.size(26.dp)
                .background(if (done) Green else Color(0xFFE7EDF3), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                mark, fontSize = 14.sp, fontWeight = FontWeight.Black,
                color = if (done) Color.White else Color(0xFF9AA3AF)
            )
        }
        Spacer(Modifier.width(11.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
private fun HintBar(text: String) {
    Box(
        Modifier.fillMaxWidth().padding(top = 16.dp)
            .background(SoftBlue, RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2F6FDB), lineHeight = 18.sp)
    }
}

// ── 0개 안내: 방식 고르기 ──
@Composable
private fun ColumnScope.RecMethodPicker(onPick: (String) -> Unit, onLater: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    Box(
        Modifier.align(Alignment.CenterHorizontally).size(92.dp).background(SoftBlue, RoundedCornerShape(26.dp)),
        contentAlignment = Alignment.Center
    ) { Text("🎙️", fontSize = 46.sp) }
    Spacer(Modifier.height(22.dp))
    Text(
        "통화 녹음을 먼저 켜주세요", fontSize = 23.sp, fontWeight = FontWeight.Black, color = Ink,
        textAlign = TextAlign.Center, letterSpacing = (-0.5).sp, modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "아직 녹음이 없어요. 딱 한 번만 켜두면\n통화가 끝날 때마다 자동으로 요약돼요.",
        fontSize = 15.sp, color = Sub, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(24.dp))
    Text("어떤 걸로 녹음하세요?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B95A1), modifier = Modifier.fillMaxWidth())
    MethodCard("🅣", "에이닷 전화", "에이닷 앱으로 녹음") { onPick("adot") }
    MethodCard("📞", "삼성 전화", "갤럭시 기본 녹음") { onPick("samsung") }
    MethodCard("🤔", "잘 모르겠어요", "둘 다 알려드릴게요") { onPick("samsung") }
    Spacer(Modifier.weight(1f))
    Box(Modifier.fillMaxWidth().clickable { onLater() }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text("나중에 할게요", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8B95A1))
    }
}

@Composable
private fun MethodCard(emoji: String, t1: String, t2: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp)
            .background(Color(0xFFF7F9FB), RoundedCornerShape(16.dp))
            .border(1.5.dp, Color(0xFFEAEEF2), RoundedCornerShape(16.dp))
            .clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).background(Color.White, RoundedCornerShape(13.dp))
                .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 24.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t1, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Ink)
            Text(t2, fontSize = 12.5.sp, color = Color(0xFF8B95A1))
        }
        Text("›", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFC4CCD4))
    }
}

// ── 0개 안내: 방식별 켜기 단계 (사장님 확인 실제 경로) ──
@Composable
private fun ColumnScope.RecGuide(
    method: String, scanning: Boolean,
    onOpen: () -> Unit, onRescan: () -> Unit, onSwitch: () -> Unit, onLater: () -> Unit,
    showDiag: Boolean, preferences: AppPreferences
) {
    val adot = method == "adot"
    Spacer(Modifier.height(36.dp))
    Box(
        Modifier.align(Alignment.CenterHorizontally).size(80.dp).background(SoftBlue, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) { Text(if (adot) "🅣" else "📞", fontSize = 40.sp) }
    Spacer(Modifier.height(18.dp))
    Text(
        if (adot) "에이닷 통화 녹음 켜기" else "삼성 통화 녹음 켜기",
        fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    Text(
        if (adot) "에이닷 앱에서 한 번만 설정하면 돼요." else "아래 순서대로 한 번만 해주세요.",
        fontSize = 14.sp, color = Sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
    if (adot) {
        RecStepRow(1, "에이닷 앱 → 아래 설정 탭 (없으면 설치)")
        RecStepRow(2, "통화 설정 → 통화녹음")
        RecStepRow(3, "자동 통화녹음 켜기 → 모든 통화 자동녹음")
    } else {
        RecStepRow(1, "전화 앱 → 오른쪽 위 ⋮ 누르기")
        RecStepRow(2, "통화 설정 → 통화 녹음")
        RecStepRow(3, "통화 자동 녹음 켜기")
    }
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier.fillMaxWidth().background(SoftGreen, RoundedCornerShape(12.dp)).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text("한 번만 켜두면 계속 자동이에요 👍", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Green) }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().clickable { onSwitch() }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(if (adot) "삼성 전화 쓰세요? 삼성 방법 보기" else "에이닷 쓰세요? 에이닷 방법 보기",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Blue)
    }
    Spacer(Modifier.weight(1f))
    Box(
        Modifier.fillMaxWidth().background(Blue, RoundedCornerShape(15.dp)).clickable { onOpen() }.padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(if (adot) "에이닷 열기" else "전화 설정 열기", fontSize = 16.5.sp, fontWeight = FontWeight.Black, color = Color.White) }
    Box(
        Modifier.fillMaxWidth().clickable(enabled = !scanning) { onRescan() }.padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) { Text(if (scanning) "찾는 중…" else "다 켰어요 · 다시 찾기 🔄", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B95A1)) }
    // 켜고 다시 찾아도 계속 0개 = 우리가 못 잡는 것 → 원인(파일없음/파서미스+가린 파일명) 자동 진단. (2026-07-29 사장님)
    if (showDiag) {
        val ctx = LocalContext.current
        // '다시 찾기'와 붙으면 오터치 → 명확히 띄우고 옅은 구분선 얹음.
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F2F5)))
        Spacer(Modifier.height(6.dp))
        InlineDiagPrompt(
            prefs = preferences,
            tag = "온보딩-녹음연결(0개)",
            prompt = "이렇게 해도 계속 안 잡히나요?",
            buildExtra = { AdotFolderScanner.recordingDiag(ctx) }
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun RecStepRow(n: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).background(Blue, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Text("$n", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.width(13.dp))
        Text(text, fontSize = 15.sp, color = Color(0xFF333D4B), fontWeight = FontWeight.Medium, lineHeight = 22.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 삼성 전화 앱 열기(사용자가 ⋮→통화 설정→통화 녹음). 없으면 다이얼러/설정 폴백. */
private fun openSamsungDialer(ctx: Context) {
    val launch = ctx.packageManager.getLaunchIntentForPackage("com.samsung.android.dialer")
    runCatching {
        ctx.startActivity((launch ?: Intent(Intent.ACTION_DIAL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** 에이닷 앱 열기. 없으면 Play 스토어 검색. */
private fun openAdot(ctx: Context) {
    for (p in listOf("com.skt.prod.dialer", "com.skt.aidot", "com.skt.tapp.adot")) {
        val i = ctx.packageManager.getLaunchIntentForPackage(p)
        if (i != null) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; return }
    }
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=에이닷 전화")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=에이닷")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
