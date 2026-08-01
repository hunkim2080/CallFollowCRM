package com.detailline.callfollowcrm.presentation.screen.collab

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.ai.SharedSiteRepository.PartnerMonth
import com.detailline.callfollowcrm.presentation.screen.collab.CollabRecordViewModel.Companion.dayLabel
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

private val GreenBg = Color(0xFFE7F8F0)
private val RedBg = Color(0xFFFDECF0)
private val WaitBg = Color(0xFFFFF3E0)
private val WaitFg = Color(0xFFE08600)

/** 협업 사장님 한 명 + 방향(받은/준). */
private data class PartnerLine(val p: PartnerMonth, val received: Boolean)

/**
 * 협업 기록 — 협업 사장별·월별(받은/준). 기록·세금용. 승인 목업: design-preview/collab_record_mockup.html (2026-08-01 사장님).
 *   §0: 프로토에 없던 새 화면(사장님 승인). 통계 화면(프로토 고정)엔 손 안 대고 더보기에서 진입.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollabRecordScreen(viewModel: CollabRecordViewModel, onBack: () -> Unit) {
    val s by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    val lines: List<PartnerLine> =
        (s.received.partners.map { PartnerLine(it, true) } + s.given.partners.map { PartnerLine(it, false) })
            .sortedByDescending { it.p.lastAtMs }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars.add(WindowInsets(top = 6.dp)),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                title = { Text("협업 기록", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.4).sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(top = inner.calculateTopPadding()).fillMaxSize().background(TossGrayBg),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            item(key = "month") {
                MonthSelector(s, viewModel)
                Spacer(Modifier.height(14.dp))
            }
            item(key = "summary") {
                SummaryRow(s)
                Spacer(Modifier.height(16.dp))
            }

            if (!s.loading && lines.isEmpty()) {
                item(key = "empty") { EmptyState(hasMonths = s.availableMonths.isNotEmpty()) }
            } else {
                item(key = "secttl") {
                    Text(
                        "협업 사장님 (${lines.size}명)",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }
                items(lines, key = { it.p.partnerPhone + "|" + it.p.partnerName + "|" + it.received }) { line ->
                    PartnerCard(line, s.paidUnknown)
                    Spacer(Modifier.height(10.dp))
                }
                item(key = "export") {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, TossDivider, RoundedCornerShape(14.dp))
                            .clickable {
                                val text = buildExportText(s, lines)
                                runCatching {
                                    ctx.startActivity(Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                                        }, "협업 기록 저장/보내기"
                                    ))
                                }
                            }
                            .padding(13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⬇ 이 달 내역 저장하기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                        Text(" (기록·세금용)", fontSize = 13.sp, color = TossTextTertiary)
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

/* ─────────────── 월 선택 ─────────────── */

@Composable
private fun MonthSelector(s: CollabRecordViewModel.UiState, vm: CollabRecordViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, TossDivider, RoundedCornerShape(14.dp))
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArrowBtn("‹", enabled = s.canPrev) { vm.prevMonth() }
        Text(
            s.monthLabel.ifBlank { "협업 기록" },
            fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
            letterSpacing = (-0.2).sp,
            modifier = Modifier.padding(horizontal = 20.dp).width(140.dp),
        )
        ArrowBtn("›", enabled = s.canNext) { vm.nextMonth() }
    }
}

@Composable
private fun ArrowBtn(sym: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(999.dp))
            .background(TossGrayBg)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(sym, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            color = if (enabled) TossTextSecondary else TossDivider)
    }
}

/* ─────────────── 받은/준 요약 ─────────────── */

@Composable
private fun SummaryRow(s: CollabRecordViewModel.UiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryCard(
            dot = TossSuccess, label = "받은 협업 (내가 받음)",
            amount = "+${s.received.totalWage}만원", amountColor = TossSuccess,
            sub = "${s.received.count}건 · ${s.received.partners.size}명 사장님",
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            dot = TossError, label = "준 협업 (내가 줌)",
            amount = "−${s.given.totalWage}만원", amountColor = TossError,
            sub = "${s.given.count}건 · ${s.given.partners.size}명 사장님",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCard(dot: Color, label: String, amount: String, amountColor: Color, sub: String, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.dp, TossDivider, RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(999.dp)).background(dot))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 11.5.sp, color = TossTextSecondary, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
        Text(amount, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = amountColor,
            letterSpacing = (-0.5).sp, modifier = Modifier.padding(top = 8.dp))
        Text(sub, fontSize = 12.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 1.dp))
    }
}

/* ─────────────── 협업 사장님 카드 ─────────────── */

@Composable
private fun PartnerCard(line: PartnerLine, paidUnknown: Boolean) {
    val p = line.p
    val received = line.received
    val initial = p.partnerName.trim().take(1).ifBlank { "협" }
    val remaining = p.totalWage - p.paidTotal
    val subRight = when {
        paidUnknown -> null
        remaining <= 0 -> if (received) "정산 끝" else "줄 돈 끝"
        else -> if (received) "받을 ${remaining}만 남음" else "줄 ${remaining}만 남음"
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.dp, TossDivider, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (received) Color(0xFFEAF2FE) else RedBg),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (received) TossBlue else TossError)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.partnerName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Spacer(Modifier.width(6.dp))
                    Badge(if (received) "받은" else "준", received)
                }
                Text("${p.count}건 · 최근 ${dayLabelShort(p.lastAtMs)}",
                    fontSize = 12.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${p.totalWage}만원", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                subRight?.let {
                    Text(it, fontSize = 11.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 1.dp))
                }
            }
        }
        // 현장별
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 62.dp, end = 14.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
            p.sites.forEach { site ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(dayLabel(site.atMs), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        color = TossTextSecondary, modifier = Modifier.width(58.dp))
                    Text(site.title, fontSize = 13.sp, color = TossTextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("${site.wage}만", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                        modifier = Modifier.padding(end = 8.dp))
                    site.paid?.let { paid -> PayBadge(paid, received) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Badge(text: String, received: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(if (received) GreenBg else RedBg)
            .padding(horizontal = 7.dp, vertical = 1.5.dp)
    ) {
        Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold,
            color = if (received) TossSuccess else TossError)
    }
}

@Composable
private fun PayBadge(paid: Boolean, received: Boolean) {
    val (bg, fg, label) = when {
        paid && received -> Triple(GreenBg, TossSuccess, "입금완료")
        paid && !received -> Triple(GreenBg, TossSuccess, "지급완료")
        !paid && received -> Triple(WaitBg, WaitFg, "받을 예정")
        else -> Triple(WaitBg, WaitFg, "지급 예정")
    }
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

/* ─────────────── 빈 상태 ─────────────── */

@Composable
private fun EmptyState(hasMonths: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🤝", fontSize = 40.sp)
        Text(
            if (hasMonths) "이 달은 협업 기록이 없어요" else "아직 협업 기록이 없어요",
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "협업 현장을 함께하면 여기에 사장님별·월별로 모여요.",
            fontSize = 13.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/* 최근 라벨은 "M/d" 만(요일 생략) — 좁은 카드용. */
private fun dayLabelShort(ms: Long): String = dayLabel(ms).substringBefore("(")

/* ─────────────── 텍스트 내보내기(저장/공유) ─────────────── */

private fun buildExportText(s: CollabRecordViewModel.UiState, lines: List<PartnerLine>): String {
    val sb = StringBuilder()
    sb.append("[협업 기록] ${s.monthLabel}\n")
    sb.append("받은 협업 ${s.received.count}건 · ${s.received.totalWage}만원\n")
    sb.append("준 협업 ${s.given.count}건 · ${s.given.totalWage}만원\n")
    lines.forEach { line ->
        val p = line.p
        sb.append("\n▸ ${p.partnerName} (${if (line.received) "받은" else "준"}) ${p.count}건 ${p.totalWage}만원\n")
        p.sites.forEach { site ->
            val pay = when (site.paid) {
                true -> if (line.received) " (입금완료)" else " (지급완료)"
                false -> if (line.received) " (받을 예정)" else " (지급 예정)"
                null -> ""
            }
            sb.append("  · ${dayLabel(site.atMs)} ${site.title} ${site.wage}만$pay\n")
        }
    }
    return sb.toString().trimEnd()
}
