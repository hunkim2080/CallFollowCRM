package com.detailline.callfollowcrm.presentation.screen.stats

import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
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
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.component.Mascot
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.component.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextInfo
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
    val rec by viewModel.myRecord.collectAsState()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars.add(WindowInsets(top = 10.dp)),
                title = {
                    // 부제로 헤더에 무게 → 무거운 파란 카드에 제목이 안 눌림. (2026-08-02 사장님 A안 승인)
                    Column {
                        Text("내 기록", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.6).sp)
                        Text("내가 다녀온 현장", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextInfo, letterSpacing = (-0.1).sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(top = inner.calculateTopPadding()).fillMaxSize().background(TossGrayBg),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp)  // top 14 = 헤더와 첫 카드 숨 쉬는 간격
        ) {
            // ── 「내 기록」 — 이 탭의 주인공. (2026-09-24 사장님, 프로토 artifact/EDcGwV4F)
            //   통계는 나만 보는 숫자지만 **기록은 남한테 보여줄 수 있는 것**이다.
            item(key = "myrecord") { MyRecordCard(rec, onOpenVisited); Spacer(Modifier.height(12.dp)) }
            // 지도 — 다녀온 동네. 점 크기 = 몇 번 갔나. 다녀온 곳이 없으면 아예 안 그린다.
            item(key = "map") {
                if (rec.dots.isNotEmpty()) { MyRecordMap(rec); Spacer(Modifier.height(12.dp)) }
            }
            // 현장 목록 — 번호가 붙어 쌓이는 곳.
            item(key = "rows") {
                if (rec.rows.isNotEmpty()) { MyRecordRows(rec, onOpenVisited); Spacer(Modifier.height(18.dp)) }
            }
            // ── 여기부터는 **나만 보는 숫자.** 기록 밑으로 내린다. ──
            item(key = "sec-num") {
                Text(
                    "숫자로 보기",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 11.dp)
                )
            }
            item(key = "grid") { StatGrid(s, onOpenVisited) }
            item(key = "sec-sub") {
                Text(
                    "문의 추이 · 전기간 비교",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextInfo,
                    modifier = Modifier.padding(start = 2.dp, top = 26.dp, bottom = 11.dp)
                )
            }
            item(key = "trend") { TrendSection(trend, onSelect = viewModel::setPeriod); Spacer(Modifier.height(18.dp)) }
            // 🗑 걷어낸 것 (2026-09-24 사장님 "싹 갈아엎어야지"):
            //   · StatsHero(파란 "9월, 잘 하고 계세요") — 위 기록 카드와 같은 말을 두 번 했다
            //   · StatsMascot("이번 달도 옆에서 챙길게요") — 자리만 먹고 아무 정보가 없다
            //   · StatTypes(시공 종류) — 사장님 확정 "상품명이 매번 달라져서 통계 잡을 이유가 없다"
            //   · 시장 비교 — 2년째 "모이는 중". 빈 약속은 치운다 (TrendSection 안에서 제거)
        }
    }
}

/* ─────────────── 내 기록 ─────────────── */

/**
 * 「내 기록」 카드 — **번호가 주인공.** (2026-09-24 사장님)
 *
 * "이번 달 12집" 은 이번 달로 끝나지만 **「현장 038」 은 039, 040 이 계속 생긴다.**
 * 처음엔 007 같은 작은 숫자라 별것 아닌데, 100 을 넘는 순간 그게 그대로 **경력**이 된다.
 *
 * 아직 한 곳도 안 한 사람에겐 **"기록이 없어요" 라고 하지 않는다** — 그건 내 탓처럼 들린다.
 * 대신 **001 자리를 비워 두고** 보여준다. 번호가 준비돼 있으면 채우고 싶어진다.
 *
 * ⚠️ 주소를 못 찾은 곳은 **숨기지 않고 적는다.** (테스트폰 실측: 9월 7곳 중 2곳)
 *    숫자를 부풀리면 기록이 아니다.
 */
@Composable
private fun MyRecordCard(rec: MyRecordState, onOpenVisited: () -> Unit) {
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)
    ) {
        // ⚠️ Composable 안에서 early return@Column 절대 금지 —
        //    빈→로드 전환 때 슬롯테이블이 어긋나 화면이 통째로 안 그려진다.
        //    바로 오늘 통계 탭이 그것 때문에 꺼졌고, 고치면서 또 같은 짓을 했다.
        if (rec.lastNo <= 0) {
            // 아직 한 곳도 없음 — 약속이지 변명이 아니다.
            Text("내 기록", style = AppType.label, color = TossTextTertiary)
            Spacer(Modifier.height(AppSpace.s8))
            Text("첫 현장을 기다리고 있어요", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                color = TossTextPrimary, letterSpacing = (-0.4).sp)
            Spacer(Modifier.height(AppSpace.s4))
            Text("시공을 끝내고 [완료] 를 누르면\n여기에 현장 001 부터 번호가 붙어 쌓여요.",
                style = AppType.body, color = TossTextInfo, lineHeight = 19.sp)
        } else {
        Text("내 기록", style = AppType.label, color = TossTextTertiary)
        Spacer(Modifier.height(AppSpace.s4))
        Text("현장 %03d".format(rec.lastNo), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            color = TossBlue, letterSpacing = (-1.0).sp)
        Spacer(Modifier.height(AppSpace.s4))
        Text(
            buildString {
                append("이번 달 ").append(rec.monthSites).append("곳")
                if (rec.towns.isNotEmpty()) append(" · 동네 ").append(rec.towns.size).append("곳")
            },
            style = AppType.body, color = TossTextInfo, fontWeight = FontWeight.Bold
        )
        if (rec.towns.isNotEmpty()) {
            Spacer(Modifier.height(AppSpace.s12))
            Text(rec.towns.joinToString(" · "), style = AppType.label, color = TossTextSecondary,
                lineHeight = 18.sp)
        }
        if (rec.notDoneCount > 0) {
            // 다녀왔는데 완료를 안 누른 곳 — **번호가 안 붙는다.** 안내가 아니라 할 일이다.
            Spacer(Modifier.height(AppSpace.s8))
            Text(
                "완료를 안 누른 ${rec.notDoneCount}곳이 있어요 — 누르면 번호가 붙어요 ›",
                style = AppType.caption, color = TossBlue, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onOpenVisited() }.padding(vertical = 2.dp)
            )
        }
        if (rec.noAddrCount > 0) {
            // 숨기지 않는다. 그리고 **누르면 채우러 갈 수 있게** 한다.
            Spacer(Modifier.height(AppSpace.s8))
            Text(
                "주소 못 찾은 ${rec.noAddrCount}곳은 동네에 안 들어가요 — 채우러 가기 ›",
                style = AppType.caption, color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onOpenVisited() }.padding(vertical = 2.dp)
            )
        }
        if (rec.pasteText.isNotBlank()) {
            Spacer(Modifier.height(AppSpace.s16))
            Box(
                Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.primaryBg)
                    .clickable {
                        clip.setText(androidx.compose.ui.text.AnnotatedString(rec.pasteText))
                        android.widget.Toast.makeText(ctx, "글을 복사했어요 — 붙여넣으면 돼요",
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("글로 복사하기", style = AppType.headline, fontWeight = FontWeight.ExtraBold,
                    color = TossBlue)
            }
            Spacer(Modifier.height(AppSpace.s8))
            Text("사진 없이 카톡·밴드·당근에 그대로 붙일 수 있어요",
                style = AppType.caption, color = TossTextTertiary)
        }
        }   // ── if (아직 없음) … else 끝
    }
}

/**
 * 다녀온 동네 지도. 점 하나 = 동네 하나, **점 크기 = 몇 번 갔나.** (2026-09-24 사장님)
 *   면을 안 칠하는 이유는 [RegionMap] 주석 참고 — 화성시가 강서구보다 20배 넓어서 그림이 거짓말을 한다.
 */
@Composable
private fun MyRecordMap(rec: MyRecordState) {
    Column(
        modifier = Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp)
    ) {
        com.detailline.callfollowcrm.presentation.component.RegionMap(rec.dots)
        Spacer(Modifier.height(AppSpace.s8))
        val sorted = rec.dots.sortedByDescending { it.count }
        Text(
            buildString {
                append(sorted.take(8).joinToString(" · ") { it.name })
                if (sorted.size > 8) append(" 외 ").append(sorted.size - 8).append("곳")
            },
            style = AppType.label, color = TossTextSecondary, lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Spacer(Modifier.height(AppSpace.s4))
        Text(
            buildString {
                append("이번 달 다닌 곳")
                if (rec.yearTownCount > rec.dots.size) append(" · 올해는 ").append(rec.yearTownCount).append("개 동네")
            },
            style = AppType.caption, color = TossTextTertiary,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

/**
 * 현장 목록 — **번호가 붙어 쌓인다.**
 *   맨 위는 '다음 현장'(예정). 다음 번호가 눈에 보이면 채우고 싶어진다.
 *   번호가 없는 줄 = 다녀왔는데 **완료를 안 누른** 것. 눌러서 채우러 간다.
 */
@Composable
private fun MyRecordRows(rec: MyRecordState, onOpenVisited: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        rec.rows.forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
            Row(
                Modifier.fillMaxWidth().clickable { onOpenVisited() }.padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(AppShape.md)
                        .background(if (r.upcoming) TossGrayBg else AppTheme.colors.primaryBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        r.no ?: "—",
                        style = AppType.label, fontWeight = FontWeight.Black,
                        color = if (r.upcoming) TossTextTertiary else AppTheme.colors.primaryText
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (r.upcoming) "다음 현장" else (r.town ?: "주소 미등록"),
                        style = AppType.body, fontWeight = FontWeight.ExtraBold,
                        color = if (r.town == null && !r.upcoming) AppTheme.colors.unpaid else TossTextPrimary
                    )
                    Text(r.date, style = AppType.caption, color = TossTextTertiary)
                }
                Text(
                    when {
                        r.upcoming -> "예정"
                        r.no == null -> "완료 누르기 ›"
                        else -> "완료"
                    },
                    style = AppType.caption, fontWeight = FontWeight.ExtraBold,
                    color = when {
                        r.upcoming -> TossTextTertiary
                        r.no == null -> TossBlue
                        else -> AppTheme.colors.done
                    }
                )
            }
        }
    }
}

/* ─────────────── stats-hero ─────────────── */

@Composable
private fun StatsHero(s: StatsUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tossCardShadow(RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(TossBlue, TossBlueDark)))
            .padding(22.dp)
    ) {
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
        // 전국 비교는 **아직 안 되는 기능**이다. 파란 카드 한가운데서 자리를 먹지 않게 뺐다.
        //   아래 '시장 비교' 카드에 한 줄로 접어 뒀다가, 데이터가 모이면 그때 편다. (2026-09-21 사장님)
    }
}

/* ─────────────── stats-mascot ─────────────── */

@Composable
private fun StatsMascot() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tossCardShadow(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, TossDivider, RoundedCornerShape(18.dp))
            .padding(start = 6.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Mascot(sizeDp = 40.dp) }
        Spacer(Modifier.width(6.dp))
        // 돈 화면에서 막내는 재롱 떨지 않는다 (브랜드 북 v5 §13). 한 일만 담백하게.
        Text("이번 달도 옆에서 챙길게요", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
    }
}

/* ─────────────── stat-grid ─────────────── */

@Composable
private fun StatGrid(s: StatsUiState, onOpenVisited: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 프로토 openVisited — "다녀온 현장 ›" 셀만 탭 가능 → 현장 목록.
            StatCell("다녀온 현장 ›", "${s.jobs}", "곳", TossTextPrimary, Modifier.weight(1f), onClick = onOpenVisited)
            // 숫자는 검정 — 파랑·초록에 뜻이 없었다(좋다/나쁘다가 아니라 그냥 숫자). (2026-09-21 사장님)
            StatCell("받은 문의", "${s.inquiries}", "건", TossTextPrimary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell("시공 전환율", "${s.conversionPct}", "%", TossTextPrimary, Modifier.weight(1f))
            StatCell("보낸 답장", "${s.sentReplies}", "건", TossTextPrimary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, unit: String, valueColor: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .tossCardShadow(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)).background(Color.White)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() } else Modifier)
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
        // 탭은 앱 어디서나 같은 모양(AppTabs). 전엔 화면마다 달랐다. (2026-09-21 사장님)
        com.detailline.callfollowcrm.presentation.component.AppTabs(
            tabs = StatPeriod.values().map { it.label },
            selected = StatPeriod.values().indexOf(t.period),
            modifier = Modifier.padding(bottom = 12.dp),
            onSelect = { onSelect(StatPeriod.values()[it]) }
        )
        // panel
        Column(
            modifier = Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)
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
                fontSize = 13.sp, color = TossTextInfo, modifier = Modifier.padding(top = 4.dp)
            )
            // 양쪽 다 0이면 **빈 그래프**다 — 막대 하나 없는 140dp 판이 화면 한가운데를 먹는다.
            //   그릴 게 없으면 한 줄로 접는다. 데이터가 생기면 그때 편다. (2026-09-23 화면 점검)
            //   '시장 비교'를 한 줄로 접어 둔 것(2026-09-21 사장님)과 같은 방식.
            val hasBars = t.bars.any { it.cur > 0 || it.prev > 0 }
            // ⚠️ 여기서 early return@Column 을 하면 **앱이 꺼진다.**
            //   빈→로드 전환 때 슬롯테이블 그룹이 어긋나 AIOOBE(index=-5). 아래 StatTypes 주석과 같은 사고.
            //   2026-09-23 에 이 한 줄을 넣으면서 return 을 같이 넣었고, 다음 날 테스트폰에서
            //   **통계 탭을 누르면 바로 재현**됐다(문의 0건 구간이라 늘 이 가지를 탄다).
            //   → return 금지. if/else 로 감싼다. (2026-09-24)
            if (!hasBars) {
                Text(
                    "아직 그릴 게 없어요 — 문의가 들어오면 여기 쌓여요",
                    style = AppType.caption, color = TossTextTertiary,
                    modifier = Modifier.padding(top = 14.dp)
                )
            } else {
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
                        // 0 은 강조할 값이 아니다 — 파랑은 **있는 값**에만. (2026-09-21 사장님)
                        Text("${b.cur}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (b.cur > 0) TossBlue else TossTextTertiary, maxLines = 1)
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
                        Text(b.label, fontSize = 11.sp, color = TossTextInfo, fontWeight = FontWeight.Medium, maxLines = 1)
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
            }   // ── if (!hasBars) … else 끝
        }
        // 🗑 시장 비교 제거 (2026-09-24 사장님) — 2년째 "모이는 중" 이었다. 빈 약속은 치운다.
        //   전국 시공자 데이터가 실제로 모이면 그때 다시 만든다.
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
        Text(label, fontSize = 11.sp, color = TossTextInfo)
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
        modifier = Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)
    ) {
        // early return@Column 은 빈→로드 전환 시 슬롯테이블 그룹 어긋나 AIOOBE 크래시(홈서 고친 패턴). if/else 로 감쌈. (2026-07-30 버그감사)
        if (s.types.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Text("이번 달 시공 기록이 아직 없어요", fontSize = 13.sp, color = TossTextInfo, fontWeight = FontWeight.Medium)
            }
        } else {
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
                Text("가장 많이 한 시공", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.85f))
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
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (up) AppTheme.colors.doneBg else AppTheme.colors.unpaidBg)
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
