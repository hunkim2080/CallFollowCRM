package com.detailline.callfollowcrm.presentation.screen.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextInfo
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

private val Warn = Color(0xFFF6A609)
private fun manwon(won: Long): String {
    if (won <= 0L) return "0원"
    val man = won / 10_000L
    return if (man >= 1) "%,d만원".format(man) else "%,d원".format(won)
}

/**
 * 비즈니스 리포트 — 번 돈 → 못 받은 돈+누가 → 추천 채택률 → 상황별 → 전환 → 활동 → 종류/지역 → 개선.
 *   기존 데이터만으로 집계. 매출=입금일 기준, 미수=현재 시점. (2026-07-05 사장님 "깊이" 요청, 설계 워크플로우 합성)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (customerId: Long) -> Unit = {}
) {
    val s by viewModel.state.collectAsState()
    val period by viewModel.periodState.collectAsState()
    var overdueExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("비즈니스 리포트", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(inner).fillMaxSize().background(TossGrayBg),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // 기간 탭
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportPeriod.values().forEach { p ->
                        val sel = p == period
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (sel) TossTextPrimary else Color.White)
                                .clickable { viewModel.setPeriod(p) }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(p.label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (sel) Color.White else TossTextSecondary)
                        }
                    }
                }
            }

            // 1) 히어로 — 번 돈
            item {
                BigStat(
                    caption = "${s.periodLabel} 번 돈",
                    value = manwon(s.revenue),
                    valueColor = TossSuccess,
                    desc = if (s.revenue <= 0L) "이 기간엔 아직 들어온 돈이 없어요"
                    else buildString {
                        append("현장 ${s.jobs}곳에서 받았어요")
                        s.revenueDeltaPct?.let { append(" · 지난 기간 대비 ${if (it >= 0) "+" else ""}$it%") }
                    },
                    descColor = s.revenueDeltaPct?.let { if (it >= 0) TossSuccess else TossError } ?: TossTextTertiary
                )
            }

            // 2) 지금 못 받은 돈 + 누가 (현재 시점)
            item { SectionSub("지금 못 받은 돈", "기간과 무관, 오늘 기준") }
            if (s.outstandingNow <= 0L) {
                item { Panel { Text("안 받은 돈이 없어요. 다 받으셨네요 👏", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TossSuccess) } }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("못 받은 돈", manwon(s.outstandingNow), "${s.outstandingCust}곳이 아직 안 줬어요", TossError, Modifier.weight(1f))
                        val rate = if (s.moneyCust > 0) s.paidOffNow * 100 / s.moneyCust else 0
                        StatTile("완납률", "$rate%", "${s.moneyCust}명 중 ${s.paidOffNow}명 완납", TossBlue, Modifier.weight(1f))
                    }
                }
                val list = if (overdueExpanded) s.overdue else s.overdue.take(5)
                item {
                    Panel(padding = 0.dp) {
                        Column {
                            list.forEachIndexed { i, r ->
                                if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(TossDivider))
                                OverdueRowView(r, onClick = { onOpenCustomer(r.customerId) })
                            }
                            if (!overdueExpanded && s.overdue.size > 5) {
                                Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(TossDivider))
                                Text("더 보기 (${s.overdue.size - 5})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossBlue,
                                    modifier = Modifier.fillMaxWidth().clickable { overdueExpanded = true }.padding(vertical = 13.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // 3) 추천 답변 채택률
            item { SectionSub("추천 답변 채택률") }
            item {
                if (s.adoptionPct == null) {
                    BigStat("막내가 도운 정도", "—", TossTextTertiary, "아직 추천 답변을 보낸 기록이 없어요", TossTextTertiary)
                } else {
                    BigStat(
                        caption = "그대로 보낸 비율",
                        value = "${s.adoptionPct}%",
                        valueColor = TossSuccess,
                        desc = "${s.suggestionTotal}건 중 ${s.adoptedCount}건을 그대로 보냈어요 · 수정 평균 ${s.avgEdit}자",
                        descColor = TossTextTertiary
                    )
                }
            }

            // 4) 상황별 채택률
            if (s.scenarios.isNotEmpty()) {
                item { SectionSub("상황별 채택률") }
                item {
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            s.scenarios.forEach { sc ->
                                BarRow(
                                    label = sc.label + if (sc.total < 5) " · 5건 미만" else "",
                                    ratio = sc.rate / 100f,
                                    valueText = "${sc.rate}%",
                                    color = if (sc.needsImprove) Warn else TossBlue,
                                    faded = sc.total < 5
                                )
                            }
                        }
                    }
                }
            }

            // 5) 전환 (문의 → 시공)
            item { SectionSub("${s.periodLabel} 전환") }
            item {
                Panel {
                    if (s.inboundCount <= 0) {
                        Text("이 기간엔 걸려온 문의가 없어요", fontSize = 13.sp, color = TossTextInfo)
                    } else {
                        Column {
                            Text("문의 ${s.inboundCount}건 → 시공 ${s.jobs}건", fontSize = 13.sp, color = TossTextSecondary, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text("전환율 ${s.conversionPct ?: 0}%", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                                color = TossSuccess, letterSpacing = (-0.5).sp)
                        }
                    }
                }
            }

            // 6) 활동
            item { SectionSub("${s.periodLabel} 활동") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniTile("걸려온 문의", "${s.inboundCount}건", TossTextPrimary, Modifier.weight(1f))
                    MiniTile("보낸 답장", "${s.sentCount}건", TossTextPrimary, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniTile("견적 발송", "${s.estimateCount}건", TossTextPrimary, Modifier.weight(1f))
                    if (s.unhandledCount > 0) MiniTile("놓친 문의", "${s.unhandledCount}건", TossError, Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                }
            }

            // 7) 종류별 / 지역별
            if (s.categoryRows.isNotEmpty()) {
                item { SectionSub("시공 종류별") }
                item { GroupBars(s.categoryRows) }
            }
            if (s.regionRows.isNotEmpty()) {
                item { SectionSub("지역별") }
                item { GroupBars(s.regionRows) }
                item {
                    Text("주소는 문자에서 자동으로 읽어요. 빠진 곳이 있을 수 있어요.",
                        fontSize = 11.sp, color = TossTextInfo, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }

            // 8) 개선 포인트
            item { SectionSub("개선 포인트") }
            item {
                Panel {
                    val imp = s.improveScenario
                    Text(
                        if (imp != null) "💡 ‘${imp.label}’ 상황 채택률이 ${imp.rate}%로 낮아요.\n이 상황의 답변 문구를 다듬으면 계약률이 올라가요."
                        else "💡 이번 기간엔 딱히 손 갈 상황이 없었어요. 막내가 잘 돕고 있어요.",
                        fontSize = 13.5.sp, color = TossTextSecondary, lineHeight = 21.sp
                    )
                }
            }

            // 9) 꼬리말
            item {
                Spacer(Modifier.height(4.dp))
                Text("이 리포트는 KPI가 아니라 '내 장사 이해' 도구예요. 매출은 받은 돈 기준, 미수금은 지금 기준.",
                    fontSize = 11.5.sp, color = TossTextInfo, modifier = Modifier.padding(horizontal = 4.dp))
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BigStat(caption: String, value: String, valueColor: Color, desc: String, descColor: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(18.dp)) {
        Text(caption, fontSize = 12.5.sp, color = TossTextSecondary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, letterSpacing = (-0.6).sp)
        Spacer(Modifier.height(6.dp))
        Text(desc, fontSize = 12.5.sp, color = descColor, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(15.dp)) {
        Text(label, fontSize = 12.sp, color = TossTextSecondary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = accent)
        Spacer(Modifier.height(3.dp))
        Text(sub, fontSize = 11.sp, color = TossTextInfo, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 15.dp, vertical = 14.dp)) {
        Text(label, fontSize = 12.sp, color = TossTextSecondary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = accent)
    }
}

@Composable
private fun SectionSub(title: String, caption: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextInfo)
        caption?.let {
            Spacer(Modifier.width(6.dp))
            Text("· $it", fontSize = 11.sp, color = TossTextInfo, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Panel(padding: androidx.compose.ui.unit.Dp = 15.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(padding)) { content() }
}

@Composable
private fun BarRow(label: String, ratio: Float, valueText: String, color: Color, faded: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
            color = if (faded) TossTextTertiary else TossTextSecondary,
            modifier = Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(9.dp).clip(RoundedCornerShape(5.dp)).background(TossGrayBg)) {
            Box(Modifier.fillMaxWidth(ratio.coerceIn(0.02f, 1f)).height(9.dp).clip(RoundedCornerShape(5.dp)).background(color))
        }
        Spacer(Modifier.width(8.dp))
        Text(valueText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun GroupBars(rows: List<GroupRow>) {
    val max = (rows.maxOfOrNull { it.amount } ?: 1L).coerceAtLeast(1L)
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            rows.forEach { g ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(manwon(g.amount), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                        if (g.outstanding > 0L) {
                            Spacer(Modifier.width(6.dp))
                            Text("미수 ${manwon(g.outstanding)}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TossError)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(TossGrayBg)) {
                        Box(Modifier.fillMaxWidth((g.amount.toFloat() / max).coerceIn(0.02f, 1f)).height(8.dp)
                            .clip(RoundedCornerShape(4.dp)).background(TossBlue))
                    }
                    Text("${g.count}곳", fontSize = 10.5.sp, color = TossTextInfo, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun OverdueRowView(r: OverdueRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val overdue14 = (r.days ?: 0) >= 14
        if (overdue14) Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(TossError))
        if (overdue14) Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(buildString { append(r.name); if (r.site.isNotBlank()) append(" · ${r.site}") },
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                buildString { append(manwon(r.amount)); append(" · "); append(r.days?.let { "${it}일 지남" } ?: "날짜 미정") },
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = if (overdue14) TossError else TossTextTertiary
            )
        }
        Text("›", fontSize = 18.sp, color = TossTextTertiary)
    }
}
