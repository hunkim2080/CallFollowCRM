package com.detailline.callfollowcrm.presentation.screen.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.presentation.component.Mascot
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.presentation.theme.TossWarning

private val ObBg = Color(0xFFFBFCFE)
private val ObAccent = TossBlue
// 프로토 OB_ACCENTS — 인트로 캐러셀 슬라이드별 악센트(분홍·파랑·초록·앰버·보라·주황·파랑).
private val ObAccents = listOf(
    Color(0xFFF0436A), Color(0xFF3182F6), Color(0xFF16C172), Color(0xFFF6A609),
    Color(0xFF7C5CFC), Color(0xFFFF7847), Color(0xFF3182F6)
)

private val TRADES = listOf(
    "줄눈", "실리콘·코킹", "도배", "장판·마루", "타일", "욕실 리모델링",
    "페인트·도색", "인테리어 필름", "방충망·모기장", "중문·샷시",
    "에어컨 설치·청소", "입주·이사 청소", "누수·방수", "도어·현관", "조명·전기"
)
private val REGIONS = listOf(
    "전국", "서울", "경기", "인천", "강원", "충청", "대전",
    "전라", "광주", "경상", "대구", "부산", "울산", "제주"
)

private data class Slide(val kicker: String, val title: String, val sub: String, val visual: @Composable (active: Boolean) -> Unit)

/**
 * 온보딩 — 프로토타입 `.ob` 4단계 그대로.
 *   0) 스토리텔링 캐러셀(7장) → 1) 업종 선택 → 2) 상호·지역 → 3) 막내 비서 탄생.
 *   선택값은 prefs(ownerTrades / bizName / ownerRegions)에 저장 → AI 답장·가격표·길찾기에 사용.
 *   완료 시 onFinish() (호출부가 hasOnboarded=true 처리 + 권한/홈 이동).
 */
@Composable
fun OnboardingScreen(prefs: AppPreferences, onFinish: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val selectedTrades = remember { mutableStateListOf<String>() }
    val selectedRegions = remember { mutableStateListOf<String>() }
    var bizName by remember { mutableStateOf(prefs.bizName) }
    var storyPage by remember { mutableStateOf(0) }
    // 프로토 .ob::before — 슬라이드별 accent 로 0.5초에 걸쳐 색 전환(인트로 캐러셀 단계에서만).
    val bandAccent by animateColorAsState(
        targetValue = if (step == 0) ObAccents.getOrElse(storyPage) { ObAccent } else ObAccent,
        animationSpec = tween(500), label = "obBand"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(ObBg)
    ) {
        // 상단 액센트 밴드 (프로토 .ob::before — accent 12% → 투명, 슬라이드 따라 색 전환)
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(bandAccent.copy(alpha = 0.12f), Color(0x00FBFCFE))
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp)
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
            when (step) {
                0 -> StoryStep(onStart = { step = 1 }, onPageChanged = { storyPage = it })
                1 -> TradeStep(
                    selected = selectedTrades,
                    onBack = { step = 0 },
                    onNext = {
                        prefs.ownerTrades = selectedTrades.toList().take(3)
                        step = 2
                    }
                )
                2 -> ProfileStep(
                    name = bizName,
                    onName = { bizName = it },
                    selectedRegions = selectedRegions,
                    onBack = { step = 1 },
                    onDone = {
                        prefs.bizName = bizName
                        prefs.ownerRegions = selectedRegions.toList()
                        step = 3
                    }
                )
                else -> BornStep(
                    name = bizName,
                    trades = selectedTrades.toList(),
                    regions = selectedRegions.toList(),
                    onStart = {
                        prefs.hasOnboarded = true
                        onFinish()
                    }
                )
            }
        }
    }
}

// ───────────────────────── Step 0: 스토리텔링 캐러셀 ─────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StoryStep(onStart: () -> Unit, onPageChanged: (Int) -> Unit) {
    val slides = remember { storySlides() }
    val pager = rememberPagerState(pageCount = { slides.size })

    // 프로토 attachObCarousel — 4.2초마다 자동으로 다음 슬라이드. 사장님이 한 번 만지면(드래그) 정지.
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    var autoPaused by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) { if (dragged) autoPaused = true }
    LaunchedEffect(autoPaused) {
        if (autoPaused) return@LaunchedEffect
        while (true) {
            delay(4200)
            if (autoPaused) break
            val next = (pager.currentPage + 1) % slides.size
            pager.animateScrollToPage(next)
        }
    }

    // 슬라이드별 악센트(프로토 OB_ACCENTS) — 상단 밴드(부모)·키커칩·점에 0.5초 전환.
    LaunchedEffect(pager.currentPage) { onPageChanged(pager.currentPage) }
    val accent by animateColorAsState(
        targetValue = ObAccents.getOrElse(pager.currentPage) { ObAccent },
        animationSpec = tween(500), label = "obKicker"
    )

    Column(Modifier.fillMaxSize()) {
        Text("RING-GO", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.2).sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("곁에 오래 둘수록, 나다워지는 AI 비서", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            "쓸수록 사장님을 닮아갑니다.\n곁에 오래 둘수록, 완벽한 비서가 됩니다.",
            fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
            lineHeight = 22.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        // 프로토 .ob-carousel — 좌우로 옆 슬라이드가 살짝 엿보이는(peek) 카드 캐러셀.
        //   부모 26dp 패딩 밖으로 빼서(full-bleed) + contentPadding 26dp → 카드는 (화면-52), 양옆 26dp peek.
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .layout { measurable, constraints ->
                    // 부모 26dp 패딩 밖으로 확장(full-bleed)하되, 보고하는 폭은 원래(패딩 안) 폭 유지 → 좌우 대칭.
                    val pad = 26.dp.roundToPx()
                    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + pad * 2))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-pad, 0) }
                },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 26.dp),
            pageSpacing = 12.dp
        ) { i ->
            val s = slides[i]
            // 중심에서 멀수록 작아지고 흐려지는 depth — 넘길 때 카드가 팝업되는 느낌(프로토 obPop).
            val pageOffset = ((pager.currentPage - i) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            val active = pager.currentPage == i
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .graphicsLayer {
                        val sc = lerp(0.93f, 1f, 1f - pageOffset)
                        scaleX = sc; scaleY = sc
                        alpha = lerp(0.5f, 1f, 1f - pageOffset)
                    }
            ) {
                KickerChip(s.kicker, accent)
                Spacer(Modifier.height(15.dp))
                // 프로토 .ob-visual — 모든 슬라이드 동일한 흰 카드(고정 254dp, 내용 가운데) + 빛 스윕(obSheen).
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(254.dp)
                        .shadow(10.dp, RoundedCornerShape(24.dp), spotColor = Color(0x26141A1F))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFEDEFF3), RoundedCornerShape(24.dp))
                ) {
                    Box(Modifier.matchParentSize().padding(18.dp), contentAlignment = Alignment.Center) { s.visual(active) }
                    Sheen(active)
                }
                Spacer(Modifier.height(20.dp))
                Text(s.title, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.6).sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(s.sub, fontSize = 14.sp, color = TossTextSecondary, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.heightIn(min = 42.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            slides.indices.forEach { i ->
                val on = pager.currentPage == i
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .height(7.dp)
                        .width(if (on) 20.dp else 7.dp)
                        .background(if (on) accent else Color(0xFFE2E6EC), RoundedCornerShape(99.dp))
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ObCta("시작하기", onClick = onStart)
        Spacer(Modifier.height(13.dp))
        Text("← 옆으로 넘겨보세요 · 카드 등록 없이 14일 무료", fontSize = 12.sp, color = TossTextTertiary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

// ───────────────────────── Step 1: 업종 선택 ─────────────────────────

@Composable
private fun TradeStep(
    selected: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ObBack(onBack)
        ObDots(1)
        Text("어떤 시공을 하세요?", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.6).sp)
        Spacer(Modifier.height(8.dp))
        Text("하시는 걸 모두 골라주세요. 2~3개도 괜찮아요 (예: 줄눈 + 실리콘).", fontSize = 14.sp, color = TossTextSecondary, lineHeight = 21.sp)
        Spacer(Modifier.height(16.dp))
        // 2열 그리드
        TRADES.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { t ->
                    TradeCell(t, selected.contains(t), Modifier.weight(1f)) {
                        if (selected.contains(t)) selected.remove(t) else selected.add(t)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(9.dp))
        }
        // + 직접 입력 (프로토 addCustomTrade) — 목록에 없는 업종을 직접 추가.
        var showCustom by remember { mutableStateOf(false) }
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color(0xFFD8DEE6), RoundedCornerShape(14.dp))
                .clickable { showCustom = true }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("+ 직접 입력", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
        }
        if (showCustom) {
            var input by remember { mutableStateOf("") }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showCustom = false },
                title = { Text("업종 직접 입력") },
                text = {
                    com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                    androidx.compose.material3.OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        placeholder = { Text("예: 방수·우레탄") }
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        val v = input.trim()
                        if (v.isNotEmpty() && !selected.contains(v)) selected.add(v)
                        showCustom = false
                    }) { Text("추가") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showCustom = false }) { Text("취소") }
                }
            )
        }
        Spacer(Modifier.height(16.dp))
        ObCta(if (selected.isEmpty()) "다음" else "다음 (${selected.size}개)", enabled = selected.isNotEmpty(), onClick = onNext)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TradeCell(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .background(if (on) TossBlueSoft else Color.White, RoundedCornerShape(14.dp))
            .border(1.5.dp, if (on) TossBlue else Color(0xFFEEF0F3), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) TossBlueDark else TossTextPrimary, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(18.dp).background(if (on) TossBlue else Color.Transparent, RoundedCornerShape(6.dp))
                .border(2.dp, if (on) TossBlue else Color(0xFFEEF0F3), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

// ───────────────────────── Step 2: 상호 · 지역 ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileStep(
    name: String,
    onName: (String) -> Unit,
    selectedRegions: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ObBack(onBack)
        ObDots(2)
        Text("거의 다 됐어요", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.6).sp)
        Spacer(Modifier.height(8.dp))
        Text("상호와 활동 지역을 알려주세요. AI 답장·길찾기에 쓰여요.", fontSize = 14.sp, color = TossTextSecondary, lineHeight = 21.sp)
        Spacer(Modifier.height(16.dp))
        // 상호 입력
        Box(
            Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(13.dp))
                .border(1.5.dp, Color(0xFFEEF0F3), RoundedCornerShape(13.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (name.isEmpty()) Text("상호 (예: 디테일라인 줄눈)", fontSize = 15.sp, color = TossTextTertiary)
            BasicTextField(
                value = name,
                onValueChange = onName,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = TossTextPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(TossBlue),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("활동 지역 · 여러 곳 선택 가능", fontSize = 14.sp, color = TossTextSecondary)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            REGIONS.forEach { r ->
                val on = selectedRegions.contains(r)
                Box(
                    Modifier
                        .background(if (on) TossBlue else Color.White, RoundedCornerShape(999.dp))
                        .border(1.5.dp, if (on) TossBlue else Color(0xFFEEF0F3), RoundedCornerShape(999.dp))
                        .clickable { if (on) selectedRegions.remove(r) else selectedRegions.add(r) }
                        .padding(horizontal = 15.dp, vertical = 9.dp)
                ) {
                    Text(r, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else TossTextSecondary)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        ObCta("완료", onClick = onDone)
        Spacer(Modifier.height(12.dp))
    }
}

// ───────────────────────── Step 3: 막내 비서 탄생 ─────────────────────────

@Composable
private fun BornStep(name: String, trades: List<String>, regions: List<String>, onStart: () -> Unit) {
    val primary = trades.firstOrNull() ?: "시공"
    val extra = if (trades.size > 1) " 외 ${trades.size - 1}개" else ""
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.background(TossBlueSoft, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("[$primary] 막내 비서 탄생!", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossBlueDark)
        }
        Spacer(Modifier.height(18.dp))
        Mascot(sizeDp = 120.dp)
        Spacer(Modifier.height(20.dp))
        // 말풍선
        Box(
            Modifier.background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFEEF0F3), RoundedCornerShape(16.dp))
                .padding(horizontal = 17.dp, vertical = 11.dp)
        ) {
            Text("사장님, 옆에서 잘 배워서 똑똑해질게요!", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
        }
        Spacer(Modifier.height(18.dp))
        Text("이제 혼자가 아니에요", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.6).sp)
        Spacer(Modifier.height(8.dp))
        Text(
            buildAnnotatedString {
                append((name.ifBlank { "사장님" }) + " · ")
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("$primary$extra") }
                append(" 전문으로 세팅 완료\n가격표·답변 템플릿이 자동으로 만들어졌어요")
            },
            fontSize = 14.sp, color = TossTextSecondary, lineHeight = 21.sp, textAlign = TextAlign.Center
        )
        if (regions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("활동 지역 · ${regions.joinToString(", ")}", fontSize = 13.sp, color = TossTextTertiary, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        ObCta("14일 무료로 시작하기", onClick = onStart)
    }
}

// ───────────────────────── 공용 ─────────────────────────

@Composable
private fun ObCta(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (enabled) TossBlue else TossBlue.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ObBack(onBack: () -> Unit) {
    Text(
        "← 이전",
        fontSize = 13.sp, color = TossTextTertiary, fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable(onClick = onBack).padding(bottom = 8.dp)
    )
}

@Composable
private fun ObDots(step: Int) {
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..3).forEach { i ->
            val on = step >= i
            Box(
                Modifier
                    .height(7.dp)
                    .width(if (on) 20.dp else 7.dp)
                    .background(if (on) TossBlue else Color(0xFFE2E6EC), RoundedCornerShape(99.dp))
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun KickerChip(text: String, accent: Color = ObAccent) {
    Box(
        Modifier.background(Color(0xFFF2F4F7), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = accent, letterSpacing = 0.5.sp)
    }
}

// ── 슬라이드 비주얼 (프로토 OB_SLIDES 재현, 클린 버전) ──

private fun storySlides(): List<Slide> = listOf(
    Slide("이런 적, 있으시죠", "혼자라 다 떠안고 계시죠", "견적에 예약에 안내까지, 하루에도 수십 번") { active ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            RiseIn(active, 0) { CustomerBubble("\"견적 얼마예요? 24평 화장실 2개요\"") }
            Spacer(Modifier.height(9.dp))
            RiseIn(active, 130) { CustomerBubble("\"주말에 시공 가능한가요?\"") }
            Spacer(Modifier.height(9.dp))
            RiseIn(active, 260) { CustomerBubble("\"위치가 어디세요? 사진 보냈어요\"") }
            Spacer(Modifier.height(14.dp))
            RiseIn(active, 390) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.background(TossError, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                        Text("읽지 않음 +12", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(9.dp))
                    Text("밀린 상담…", fontSize = 12.sp, color = TossTextTertiary)
                }
            }
        }
    },
    Slide("01 · 답장", "사장님 말투 그대로 답장", "쓸수록 말투를 배워서, 점점 더 사장님처럼") { active ->
        Column(Modifier.fillMaxWidth()) {
            RiseIn(active, 0) { CustomerBubble("견적 얼마예요? 24평 화장실 2개요") }
            Spacer(Modifier.height(12.dp))
            RiseIn(active, 160) {
            Column(Modifier.fillMaxWidth().background(TossBlueSoft, RoundedCornerShape(14.dp)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨ AI 추천 답변", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.background(Color(0xFFE7F8EF), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("✓ 사장님 말투", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = TossSuccess)
                    }
                }
                Spacer(Modifier.height(6.dp))
                TypewriterText("안녕하세요! 24평 화장실 2개는 28~32만원선이에요. 사진 주시면 정확히 알려드릴게요 😊", active)
            }
            }
        }
    },
    Slide("02 · 안내", "내일 가요, 미리 알려드려요", "도착 30분 전 문자, 헛걸음 없게") { active ->
        Column(Modifier.fillMaxWidth()) {
            RiseIn(active, 0) {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).border(1.dp, Color(0xFFEEF0F3), RoundedCornerShape(14.dp)).padding(14.dp)) {
                Text("📍 현장 5km 진입 · 도착 30분 전", fontSize = 11.sp, color = TossSuccess, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("고객님, 30분 뒤 도착 예정입니다 😊", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            }
            }
            Spacer(Modifier.height(12.dp))
            RiseIn(active, 150) {
            Box(Modifier.background(Color(0xFFFEF3E0), RoundedCornerShape(8.dp)).padding(horizontal = 11.dp, vertical = 6.dp)) {
                Text("시공 하루 전 안내도 자동", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB8780A))
            }
            }
        }
    },
    Slide("03 · 돈", "누가 안 줬는지 한눈에", "계약금·잔금, 떼일 걱정 없게 자동 정리") { active ->
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF272D3D), Color(0xFF14171F))), RoundedCornerShape(16.dp)).padding(18.dp)) {
            Text("아직 받을 돈", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            val won by animateIntAsState(if (active) 105 else 0, tween(900), label = "won")
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)) { append("$won") }
                withStyle(SpanStyle(fontSize = 16.sp)) { append("만원") }
            }, color = Color.White)
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("강동 잔금 32만", "송파 잔금 28만").forEach {
                    Box(Modifier.background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(9.dp)).padding(horizontal = 11.dp, vertical = 6.dp)) {
                        Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    },
    Slide("04 · 요즘 경기", "나만 힘든 거 아니더라고요", "전국 현장 흐름으로 요즘 경기 한눈에") { active ->
        Column(Modifier.fillMaxWidth()) {
            Row {
                Text("이번 주 문의 ", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                Text("▼ 18%", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossError)
            }
            Spacer(Modifier.height(16.dp))
            // 프로토: 그룹마다 회색(bg) 막대 + 파란 막대 한 쌍, 둘 다 stagger 로 차오름.
            val barPairs = listOf(0.80f to 0.55f, 0.65f to 0.85f, 0.90f to 0.45f, 0.70f to 0.40f)
            Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                barPairs.forEachIndexed { idx, pair ->
                    val gBg by animateFloatAsState(if (active) pair.first else 0f, tween(600, delayMillis = idx * 100), label = "barBg")
                    val gCol by animateFloatAsState(if (active) pair.second else 0f, tween(600, delayMillis = idx * 100 + 70), label = "barCol")
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f).height((80 * gBg).dp).background(Color(0xFFDCE1EA), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                        Box(Modifier.weight(1f).height((80 * gCol).dp).background(TossBlue, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("전국 평균 -12% · 나만 그런 게 아니에요", fontSize = 11.sp, color = TossTextTertiary)
        }
    },
    Slide("05 · 홍보", "후기 글, AI가 대신 써줘요", "고객과 나눈 문자로 만든 진짜 후기,\n사장님은 사진만 넣으세요") { active ->
        Column(Modifier.fillMaxWidth()) {
            // 프로토: 사진 ＋ 채팅 → 후기 아이콘 줄
            RiseIn(active, 0) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp).background(Color(0xFFB6BECB), RoundedCornerShape(13.dp)), Alignment.Center) {
                    Text("사진", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Text("  ＋  ", fontSize = 13.sp, fontWeight = FontWeight.Black, color = TossTextTertiary)
                Box(Modifier.size(48.dp).background(TossGrayBg, RoundedCornerShape(13.dp)), Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, tint = TossTextSecondary, modifier = Modifier.size(22.dp))
                }
                Text("  →  ", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ObAccent)
                Box(Modifier.size(48.dp).background(Color(0xFFFFF1E9), RoundedCornerShape(13.dp)), Alignment.Center) {
                    Icon(Icons.Filled.Description, null, tint = Color(0xFFFF7847), modifier = Modifier.size(22.dp))
                }
            }
            }
            Spacer(Modifier.height(14.dp))
            RiseIn(active, 170) {
            Column(Modifier.fillMaxWidth().background(TossGrayBg, RoundedCornerShape(14.dp)).padding(13.dp)) {
                Text("[천호동] 화장실 줄눈 시공 후기", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("안녕하세요, 디테일라인 줄눈입니다 😊 욕실 2곳 줄눈+코킹 재시공을…", fontSize = 12.sp, color = TossTextPrimary, lineHeight = 19.sp)
                Spacer(Modifier.height(4.dp))
                Text("#천호동줄눈 #욕실줄눈 #디테일라인", fontSize = 12.sp, color = ObAccent, fontWeight = FontWeight.Bold)
            }
            }
        }
    },
    Slide("그래서 —", "함께할수록 완벽해져요", "오늘 시작하면, 내일 더 사장님다운 비서") { active ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("사장님 말투 학습", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(4.dp))
            val pct by animateIntAsState(if (active) 92 else 0, tween(1100), label = "pct")
            val barW by animateFloatAsState(if (active) 0.92f else 0f, tween(1100), label = "pctbar")
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)) { append("$pct") }
                withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)) { append("%") }
            })
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).background(TossGrayBg, RoundedCornerShape(99.dp))) {
                Box(Modifier.fillMaxWidth(barW.coerceIn(0.001f, 1f)).height(8.dp).background(TossBlue, RoundedCornerShape(99.dp)))
            }
            Spacer(Modifier.height(14.dp))
            Text("대화를 나눌수록, 점점 더 사장님처럼", fontSize = 12.5.sp, color = TossTextSecondary, fontWeight = FontWeight.SemiBold)
        }
    }
)

/**
 * 프로토 obBubble/obUp — 슬라이드가 활성화되면 요소가 아래에서 위로 페이드인.
 *   delayMs 로 stagger(하나씩 차례로). 비활성(다른 슬라이드)일 땐 숨김 → 돌아오면 다시 재생.
 */
@Composable
private fun RiseIn(active: Boolean, delayMs: Int, content: @Composable () -> Unit) {
    val p by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 430, delayMillis = if (active) delayMs else 0),
        label = "rise"
    )
    Box(Modifier.graphicsLayer { alpha = p; translationY = (1f - p) * 14.dp.toPx() }) { content() }
}

/**
 * 프로토 .ob-visual::after obSheen — 슬라이드 활성화 시 카드 위로 빛이 한 번 스윽 지나감.
 */
@Composable
private fun BoxScope.Sheen(active: Boolean) {
    val p = remember(active) { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) { p.snapTo(0f); p.animateTo(1f, tween(durationMillis = 900, delayMillis = 280, easing = LinearEasing)) }
    }
    if (p.value > 0f && p.value < 1f) {
        Box(Modifier.matchParentSize().drawWithContent {
            drawContent()
            val band = size.width * 0.55f
            val cx = -band + (size.width + band * 2f) * p.value
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.42f),
                    1f to Color.Transparent,
                    startX = cx - band / 2f, endX = cx + band / 2f
                )
            )
        })
    }
}

/**
 * 프로토 data-type/ob-caret — 활성화 시 한 글자씩 타이핑(끝에 ▌ 커서).
 */
@Composable
private fun TypewriterText(text: String, active: Boolean) {
    var n by remember(active) { mutableStateOf(if (active) 0 else text.length) }
    LaunchedEffect(active) {
        if (active) {
            n = 0
            while (n < text.length) { kotlinx.coroutines.delay(22); n++ }
        } else n = text.length
    }
    Text(
        text.take(n) + if (n < text.length) "▌" else "",
        fontSize = 13.sp, color = TossTextPrimary, lineHeight = 19.sp
    )
}

@Composable
private fun CustomerBubble(text: String) {
    Box(
        Modifier.background(Color.White, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp))
            .border(1.dp, Color(0xFFEEF0F3), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Text(text, fontSize = 13.sp, color = TossTextPrimary)
    }
}
