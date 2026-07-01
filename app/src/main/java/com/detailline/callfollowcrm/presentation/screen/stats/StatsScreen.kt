package com.detailline.callfollowcrm.presentation.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.component.Mascot
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 통계 탭 — 프로토 `s-stats` 1:1 (2026-06-02 재구성).
 *
 * 순서(프로토 markup 그대로):
 *   stats-hero(인사·현장 N곳·작년 대비·페이스 배지[모이는 중]) → stats-mascot →
 *   stat-grid(다녀온 현장/받은 문의/시공 전환율/보낸 답장) → sec-sub "문의 추이 · 전기간 비교" →
 *   period-toggle + 추이 panel + 시장 비교[모이는 중] → "이번 달 시공 종류" → 타입 panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel, onOpenVisited: () -> Unit = {}) {
    val s by viewModel.state.collectAsState()
    val trend by viewModel.trend.collectAsState()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars.add(WindowInsets(top = 10.dp)),
                title = { Text("통계", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.6).sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(top = inner.calculateTopPadding()).fillMaxSize().background(TossGrayBg),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item(key = "hero") { StatsHero(s); Spacer(Modifier.height(14.dp)) }
            item(key = "mascot") { StatsMascot(); Spacer(Modifier.height(14.dp)) }
            item(key = "grid") { StatGrid(s, onOpenVisited) }
            item(key = "sec-sub") {
                Text(
                    "문의 추이 · 전기간 비교",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                    modifier = Modifier.padding(start = 2.dp, top = 26.dp, bottom = 11.dp)
                )
            }
            item(key = "trend") { TrendSection(trend, onSelect = viewModel::setPeriod) }
            item(key = "types-sec") {
                Text(
                    "이번 달 시공 종류",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                    modifier = Modifier.padding(start = 2.dp, top = 26.dp, bottom = 11.dp)
                )
            }
            item(key = "types") { StatTypes(s); Spacer(Modifier.height(18.dp)) }
        }
    }
}

/* ─────────────── stats-hero ─────────────── */

@Composable
private fun StatsHero(s: StatsUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(TossBlue, TossBlueDark)))
            .padding(22.dp)
    ) {
        Text("👏", fontSize = 26.sp)
        Text(s.greeting, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.6).sp, modifier = Modifier.padding(top = 6.dp))
        // h2: 현장 N곳 + (작년 대비)
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text("이번 달 현장 ", fontSize = 13.sp, color = Color.White.copy(alpha = 0.82f))
            Text("${s.jobs}곳", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("을 다녀오셨어요.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.82f))
        }
        s.jobsVsLastYear?.takeIf { it != 0 }?.let { d ->
            Row(modifier = Modifier.padding(top = 2.dp)) {
                Text("작년 같은 달보다 ", fontSize = 13.sp, color = Color.White.copy(alpha = 0.82f))
                Text(if (d > 0) "$d 곳 더!" else "${-d}곳 적게", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
        // rank-badge → 전국 데이터 모이는 중
        Box(
            modifier = Modifier
                .padding(top = 11.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text("🔥 전국 페이스는 데이터가 모이는 중", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}

/* ─────────────── stats-mascot ─────────────── */

@Composable
private fun StatsMascot() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, TossDivider, RoundedCornerShape(18.dp))
            .padding(start = 6.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Mascot(sizeDp = 40.dp) }
        Spacer(Modifier.width(6.dp))
        Row {
            Text("사장님 옆에서 저도 ", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Text("부쩍 자랐어요", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TossBlue)
            Text(". 다음 달도 잘 부탁드려요!", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
        }
    }
}

/* ─────────────── stat-grid ─────────────── */

@Composable
private fun StatGrid(s: StatsUiState, onOpenVisited: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 프로토 openVisited — "다녀온 현장 ›" 셀만 탭 가능 → 현장 목록.
            StatCell("다녀온 현장 ›", "${s.jobs}", "곳", TossTextPrimary, Modifier.weight(1f), onClick = onOpenVisited)
            StatCell("받은 문의", "${s.inquiries}", "건", TossBlue, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell("시공 전환율", "${s.conversionPct}", "%", TossSuccess, Modifier.weight(1f))
            StatCell("보낸 답장", "${s.sentReplies}", "건", TossTextPrimary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, unit: String, valueColor: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(Color.White)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, letterSpacing = (-0.9).sp)
            Text(unit, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, modifier = Modifier.padding(start = 1.dp, bottom = 3.dp))
        }
        Text(label, fontSize = 12.5.sp, color = TossTextSecondary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
    }
}

/* ─────────────── 문의 추이 ─────────────── */

@Composable
private fun TrendSection(t: StatsTrendState, onSelect: (StatPeriod) -> Unit) {
    Column {
        // period-toggle
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            StatPeriod.values().forEach { p ->
                val on = t.period == p
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) TossBlue else Color.White)
                        .clickable { onSelect(p) }
                        .padding(horizontal = 15.dp, vertical = 8.dp)
                ) {
                    Text(p.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else TossTextSecondary)
                }
            }
        }
        // panel
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${t.curTotal}건", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.9).sp)
                val up = t.deltaPct >= 0
                Text(
                    "${if (up) "▲" else "▼"} ${kotlin.math.abs(t.deltaPct)}%",
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (up) TossSuccess else TossError,
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
                )
            }
            Text(
                "${t.prevLabel} ${t.prevTotal}건 → ${t.unitLabel} ${t.curTotal}건",
                fontSize = 13.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 4.dp)
            )
            // gbars — 프로토 .gbars(height)+.pair(flex:1): 막대 영역이 위 숫자·아래 요일 빼고 남는 높이에 비례.
            val max = (t.bars.maxOfOrNull { maxOf(it.cur, it.prev) } ?: 1).coerceAtLeast(1)
            Row(
                modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                t.bars.forEach { b ->
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${b.cur}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        // 막대 영역 = 남는 높이(weight). 각 막대는 이 높이의 비율로 채움.
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            GBar(b.prev, max, color = TossDivider)
                            GBar(b.cur, max, brush = Brush.verticalGradient(listOf(Color(0xFF5BA0FF), TossBlue)))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(b.label, fontSize = 11.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
            // legend
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                LegendItem(TossDivider, t.prevLabel)
                Spacer(Modifier.width(16.dp))
                LegendItem(TossBlue, t.unitLabel)
            }
        }
        // 시장 비교 (전국 = 모이는 중)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)
        ) {
            Text("📊 시장 비교", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary, modifier = Modifier.padding(bottom = 11.dp))
            MkRow("내 문의", "${if (t.deltaPct >= 0) "+" else ""}${t.deltaPct}%", if (t.deltaPct >= 0) TossSuccess else TossError)
            MkRow("시공막내 전국 평균", "모이는 중", TossTextTertiary)
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(12.dp)
            ) {
                Text(
                    "전국 시공자 평균은 데이터가 모이는 중이에요. 곧 \"나만 그런 건지\"까지 한눈에 보여드릴게요.",
                    fontSize = 13.sp, color = TossTextSecondary
                )
            }
        }
    }
}

@Composable
private fun GBar(value: Int, max: Int, color: Color? = null, brush: Brush? = null) {
    // 프로토 .bp — 막대 영역(부모 Row, 높이 고정)의 비율로 채움. min 3% 로 0 값도 stub 보이게(프로토 min-height:3px).
    val frac = (value.toFloat() / max.toFloat()).coerceIn(0.03f, 1f)
    Box(
        modifier = Modifier
            .width(10.dp)
            .fillMaxHeight(frac)
            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(color ?: TossDivider))
    )
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = TossTextTertiary)
    }
}

@Composable
private fun MkRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = TossTextPrimary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
    }
}

/* ─────────────── 시공 종류 ─────────────── */

@Composable
private fun StatTypes(s: StatsUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)
    ) {
        if (s.types.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Text("이번 달 시공 기록이 아직 없어요", fontSize = 13.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
            }
            return@Column
        }
        // wt-hero
        s.topType?.let { top ->
            val heroSub = when {
                top.delta > 0 -> "지난달보다 ${top.delta}번 더 했어요!"
                top.delta < 0 -> "지난달보다 ${-top.delta}번 줄었어요"
                else -> "지난달과 비슷해요"
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(TossBlue, TossBlueDark)))
                    .padding(horizontal = 17.dp, vertical = 16.dp)
            ) {
                Text("🎉 가장 많이 한 시공", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.85f))
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
                    Text(top.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = (-0.4).sp)
                    Spacer(Modifier.weight(1f))
                    Text("${top.count}회", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Text(heroSub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 8.dp))
            }
        }
        val max = (s.types.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
        s.types.forEachIndexed { i, t ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
            WtRow(t, max)
        }
    }
}

@Composable
private fun WtRow(t: StatTypeRow, max: Int) {
    Column(Modifier.padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TossTextPrimary, modifier = Modifier.weight(1f))
            Text("${t.count}회", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
            if (t.delta != 0) {
                val up = t.delta > 0
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (up) Color(0xFFE5F8EE) else Color(0xFFFDEAEF))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${if (up) "▲" else "▼"} ${kotlin.math.abs(t.delta)}",
                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (up) Color(0xFF0A8F44) else TossError
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).background(TossGrayBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((t.count.toFloat() / max).coerceIn(0.03f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF7CB2FF), TossBlue)))
            )
        }
    }
}
