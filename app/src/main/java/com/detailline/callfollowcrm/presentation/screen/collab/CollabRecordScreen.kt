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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.ai.SharedSiteRepository.DirectionAgg
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
private val GreenDeep = Color(0xFF0F9B5C)
private val RedBg = Color(0xFFFDECF0)
private val RedDeep = Color(0xFFD5325C)
private val WaitBg = Color(0xFFFFF3E0)
private val WaitFg = Color(0xFFE08600)

/**
 * 협업 기록 — 협업 사장별·월별(받은/준). 기록·세금용. 승인 목업: design-preview/collab_record_mockup.html + 탭 버전(2026-08-01 사장님).
 *   §0: 프로토에 없던 새 화면(사장님 승인). 통계 화면(프로토 고정)엔 손 안 대고 더보기에서 진입.
 *   받은/준을 **탭으로 분리**(토스식) — 한 번에 한 방향만 보여 처음 보는 사람도 안 헷갈리게. (사장님 피드백)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollabRecordScreen(viewModel: CollabRecordViewModel, onBack: () -> Unit) {
    val s by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    // 탭 선택 — 사용자가 안 누른 동안은 데이터 많은 쪽 자동 선택(빈 탭 먼저 안 보이게).
    var manualReceived by remember { mutableStateOf<Boolean?>(null) }
    val showReceived = manualReceived ?: (s.received.count >= s.given.count)
    val agg: DirectionAgg = if (showReceived) s.received else s.given

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
            item(key = "tabs") {
                DirectionTabs(
                    received = s.received, given = s.given, showReceived = showReceived,
                    onSelect = { manualReceived = it }
                )
                Spacer(Modifier.height(16.dp))
            }

            if (!s.loading && agg.partners.isEmpty()) {
                item(key = "empty") { EmptyState(showReceived = showReceived, hasMonths = s.availableMonths.isNotEmpty()) }
            } else {
                item(key = "secttl") {
                    Text(
                        (if (showReceived) "받은 협업 사장님" else "준 협업 사장님") + " (${agg.partners.size}명)",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }
                items(agg.partners, key = { it.partnerPhone + "|" + it.partnerName }) { p ->
                    PartnerCard(p, received = showReceived, paidUnknown = s.paidUnknown)
                    Spacer(Modifier.height(10.dp))
                }
                item(key = "export") {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)).background(Color.White)
                            .border(1.dp, TossDivider, RoundedCornerShape(14.dp))
                            .clickable {
                                val text = buildExportText(s, showReceived, agg)
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
                        Text("⬇ 이 달 '${if (showReceived) "받은" else "준"}' 내역 저장하기",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossBlue)
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

/* ─────────────── 받은/준 탭 ─────────────── */

@Composable
private fun DirectionTabs(received: DirectionAgg, given: DirectionAgg, showReceived: Boolean, onSelect: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DirTab(
            selected = showReceived, isReceived = true,
            amount = "+${received.totalWage}만원", count = received.count, people = received.partners.size,
            modifier = Modifier.weight(1f)
        ) { onSelect(true) }
        DirTab(
            selected = !showReceived, isReceived = false,
            amount = "−${given.totalWage}만원", count = given.count, people = given.partners.size,
            modifier = Modifier.weight(1f)
        ) { onSelect(false) }
    }
}

@Composable
private fun DirTab(selected: Boolean, isReceived: Boolean, amount: String, count: Int, people: Int, modifier: Modifier, onClick: () -> Unit) {
    val accent = if (isReceived) TossSuccess else TossError
    val accentDeep = if (isReceived) GreenDeep else RedDeep
    val bg = if (selected) (if (isReceived) GreenBg else RedBg) else Color.White
    val border = if (selected) accent else TossDivider
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp)).background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(999.dp)).background(accent))
            Spacer(Modifier.width(5.dp))
            Text(if (isReceived) "받은 (수입)" else "준 (지출)",
                fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                color = if (selected) accentDeep else TossTextSecondary)
        }
        Text(amount, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp,
            color = if (selected) accent else TossTextTertiary, modifier = Modifier.padding(top = 5.dp))
        Text("${count}건 · ${people}명", fontSize = 11.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 1.dp))
    }
}

/* ─────────────── 협업 사장님 카드 ─────────────── */

@Composable
private fun PartnerCard(p: PartnerMonth, received: Boolean, paidUnknown: Boolean) {
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
                    .background(if (received) GreenBg else RedBg),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (received) GreenDeep else RedDeep)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(p.partnerName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
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
        Column(modifier = Modifier.fillMaxWidth().padding(start = 62.dp, end = 14.dp)) {
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
private fun EmptyState(showReceived: Boolean, hasMonths: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🤝", fontSize = 40.sp)
        Text(
            when {
                !hasMonths -> "아직 협업 기록이 없어요"
                showReceived -> "이 달 받은 협업이 없어요"
                else -> "이 달 준 협업이 없어요"
            },
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            if (showReceived) "다른 사장님이 나를 부른 협업이 여기 모여요." else "내가 다른 사장님을 부른 협업이 여기 모여요.",
            fontSize = 13.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/* 최근 라벨은 "M/d" 만(요일 생략) — 좁은 카드용. */
private fun dayLabelShort(ms: Long): String = dayLabel(ms).substringBefore("(")

/* ─────────────── 텍스트 내보내기(저장/공유) — 선택 탭 기준 ─────────────── */

private fun buildExportText(s: CollabRecordViewModel.UiState, showReceived: Boolean, agg: DirectionAgg): String {
    val dir = if (showReceived) "받은" else "준"
    val sb = StringBuilder()
    sb.append("[협업 기록 · $dir] ${s.monthLabel}\n")
    sb.append("$dir 협업 ${agg.count}건 · ${agg.totalWage}만원\n")
    agg.partners.forEach { p ->
        sb.append("\n▸ ${p.partnerName} ${p.count}건 ${p.totalWage}만원\n")
        p.sites.forEach { site ->
            val pay = when (site.paid) {
                true -> if (showReceived) " (입금완료)" else " (지급완료)"
                false -> if (showReceived) " (받을 예정)" else " (지급 예정)"
                null -> ""
            }
            sb.append("  · ${dayLabel(site.atMs)} ${site.title} ${site.wage}만$pay\n")
        }
    }
    return sb.toString().trimEnd()
}
