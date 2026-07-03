package com.detailline.callfollowcrm.presentation.screen.brief

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

private val GoalCardBg = Color(0xFF202632)
private val WarnBg = Color(0xFFFFF7E6)
private val WarnText = Color(0xFF8A5A00)
private val WarnSub = Color(0xFFB07A1E)

/**
 * 마감 브리핑 화면 (2026-07-03 의미있게 재구성) — 오늘 성취 + 이번 달 목표 진행 + 내일 준비 + 못 받은 돈 챙김.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosingBriefScreen(
    viewModel: ClosingBriefViewModel,
    onBack: () -> Unit,
    onOpenChat: (phone: String, customerId: Long?) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenNewLeads: () -> Unit,
    onOpenSettlement: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("오늘 하루 마감 브리핑 🌙", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // ── 인사 + 오늘 성취 한 줄 ──
            Text("오늘도 정말 고생하셨어요.", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(3.dp))
            Text(state.dateLabel, fontSize = 13.sp, color = TossTextTertiary)
            if (state.achievementLine != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.achievementLine!!, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
            } else if (state.loaded) {
                Spacer(Modifier.height(8.dp))
                Text("조용한 날도 사장님 하루예요. 푹 쉬세요 🌙", fontSize = 13.sp, color = TossTextTertiary)
            }
            Spacer(Modifier.height(16.dp))

            // ── 오늘의 성취 3칸 ──
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("오늘 새 문의", "${state.newCount}건", "👋", Modifier.weight(1f)) { onOpenNewLeads() }
                StatTile("오늘 마무리", "${state.completedCount}건", "✅", Modifier.weight(1f)) { onOpenSchedule() }
                StatTile("오늘 입금", "${state.depositCount}건", "💰", Modifier.weight(1f), sub = state.paidSumLabel) { onOpenSettlement() }
            }
            Spacer(Modifier.height(18.dp))

            // ── 이번 달 목표 진행 (다크 히어로 카드) ──
            if (state.goalLabel.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GoalCardBg)
                        .clickable { onOpenSettlement() }.padding(18.dp)
                ) {
                    Text("이번 달, 여기까지 왔어요", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(state.monthPaidLabel, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("/ 목표 ${state.goalLabel}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.15f))) {
                        Box(Modifier.fillMaxWidth(state.progressFraction).height(8.dp).clip(RoundedCornerShape(999.dp)).background(TossBlue))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${state.progressPct}%" + (state.progressLabel?.let { " · $it" } ?: ""),
                            fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f)
                        )
                        state.todayContribLabel?.let {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp)).background(TossBlue.copy(alpha = 0.30f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) { Text(it, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            // ── 내일 준비 ──
            Text("내일 준비", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            if (state.jobs.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                        .border(1.dp, TossDivider, RoundedCornerShape(14.dp)).padding(vertical = 26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.loaded) "내일은 시공 일정이 없어요. 푹 쉬세요 🌙" else "불러오는 중…",
                        color = TossTextTertiary, fontSize = 13.sp
                    )
                }
            } else {
                state.jobs.forEach { job ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 9.dp)
                            .clip(RoundedCornerShape(14.dp)).background(Color.White)
                            .border(1.dp, TossDivider, RoundedCornerShape(14.dp))
                            .clickable { onOpenChat(job.phone, job.customerId.takeIf { it > 0 }) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(TossBlueSoft),
                            contentAlignment = Alignment.Center) { Text("🔧", fontSize = 16.sp) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(job.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                                Spacer(Modifier.width(7.dp))
                                Text(job.timeLabel, fontSize = 12.5.sp, color = TossTextSecondary)
                            }
                            job.addressLabel?.let {
                                Text("📍 $it", fontSize = 12.sp, color = TossTextTertiary, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                            }
                            job.receivableLabel?.let {
                                Spacer(Modifier.height(5.dp))
                                Box(
                                    Modifier.clip(RoundedCornerShape(999.dp)).background(TossBlueSoft)
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                ) { Text(it, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossBlue) }
                            }
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(TossBlueSoft)
                                .padding(horizontal = 13.dp, vertical = 7.dp)
                        ) { Text("대화", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossBlueSoft)
                        .clickable { onOpenSchedule() }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text("전체 일정 보기", color = TossBlueDark, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }

            // ── 자기 전에 챙길 것: 못 받은 돈 ──
            if (state.outstandingSumLabel != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WarnBg)
                        .clickable { onOpenSettlement() }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💡", fontSize = 15.sp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "자기 전에 · 못 받은 돈 ${state.outstandingCount}건 · ${state.outstandingSumLabel}",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WarnText
                        )
                        state.outstandingExample?.let {
                            Text("$it 아직 안 들어왔어요", fontSize = 11.5.sp, color = WarnSub, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    Text("정산 →", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = WarnText)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    emoji: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, TossDivider, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 11.dp)
    ) {
        Text(emoji, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 11.5.sp, color = TossTextTertiary, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 11.sp, color = TossBlue, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
