package com.detailline.callfollowcrm.presentation.screen.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.detailline.callfollowcrm.recording.AdotFolderScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 연결 마법사 (온보딩 마지막 — 권한 화면 다음). 프로토 넘는 새 설계(docs/PLAN_onboarding_redesign.md, 2026-07-28 사장님 승인).
 * 어르신도 쉽게: 한 화면 = 한 가지 + 큰 버튼 하나 + 쉬운 말 + "나중에" 탈출구.
 * v1 = 통화녹음 연결(묻혀있던 핵심가치를 앞으로) + 녹음 0개 시 삼성 통화녹음 켜기 안내. (가격표·답장은 후속 증분)
 */
private val Blue = Color(0xFF3182F6)
private val Green = Color(0xFF12B886)
private val Ink = Color(0xFF191F28)
private val Sub = Color(0xFF6B7684)
private val SoftBlue = Color(0xFFEEF4FF)
private val SoftGreen = Color(0xFFEAFBF2)

@Composable
fun OnboardingSetupScreen(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(0) }            // 0 안내, 1 통화녹음, 2 완료
    var recResult by remember { mutableStateOf<Int?>(null) }   // null=미스캔, N=찾은 개수(0 포함)
    var scanning by remember { mutableStateOf(false) }

    fun doScan() {
        scanning = true
        scope.launch {
            AdotFolderScanner.enableMediaStore(ctx)
            val n = withContext(Dispatchers.IO) {
                runCatching { AdotFolderScanner.countMediaStoreCandidates(ctx) }.getOrDefault(0)
            }
            recResult = n
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
                    ReadyRow("✓", "기본 문자 앱", done = true)
                    ReadyRow("✓", "권한 허용", done = true)
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
                    // 2-c. 0개 → 삼성 통화녹음 켜기 안내
                    else -> StepScaffold(
                        icon = "🎙️", iconBg = SoftBlue,
                        title = "통화 녹음을 먼저 켜주세요",
                        sub = "아직 녹음 파일이 없어요.\n전화 앱에서 통화 녹음을 켜면 막내가 자동으로 요약해드려요.",
                        primaryText = "통화 녹음 설정 열기",
                        onPrimary = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        laterText = "나중에 할게요",
                        onLater = { step = 2 }
                    ) {
                        Column(Modifier.padding(top = 4.dp)) {
                            HintBar("삼성 전화 앱 → 설정 → 통화 녹음 → '자동 녹음' 켜기")
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier.fillMaxWidth().background(SoftBlue, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !scanning) { tryFind() }
                                    .padding(vertical = 15.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (scanning) "찾는 중…" else "🔄 다시 찾기",
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Blue
                                )
                            }
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
                    ReadyRow("✓", "기본 문자 앱", done = true)
                    ReadyRow("✓", "권한 허용", done = true)
                    ReadyRow(
                        "✓",
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
