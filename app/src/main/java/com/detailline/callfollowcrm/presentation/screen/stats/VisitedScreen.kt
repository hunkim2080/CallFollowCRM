package com.detailline.callfollowcrm.presentation.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextInfo
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.component.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember

/**
 * 다녀온/다녀올 현장 — 프로토 `s-visited` 확장.
 *   상단 요약(다녀온 N곳 · 매출 + 다녀올 M곳) → "다녀올 현장"(파랑) 섹션 → "다녀온 현장"(초록) 섹션.
 *   행 = 날짜 · 이름/주소 · › → 탭 시 고객 카드.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitedScreen(
    viewModel: VisitedViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("다녀온 현장 · ${state.monthLabel}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 18.dp)
        ) {
            item(key = "sub") {
                val sub = buildString {
                    append("다녀온 ${state.visitedCount}곳 · 매출 합계 ${"%,d".format(state.revenueManwon)}만원")
                    if (state.upcomingCount > 0) append("  ·  다녀올 ${state.upcomingCount}곳")
                }
                Text(sub, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextInfo,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            }

            if (state.visitedRows.isEmpty() && state.upcomingRows.isEmpty()) {
                item(key = "empty") {
                    if (state.loaded) {
                        com.detailline.callfollowcrm.presentation.component.MascotEmptyState(
                            speech = "이번 달 현장이 아직 없어요"
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text("불러오는 중…", color = TossTextTertiary, fontSize = 13.sp)
                        }
                    }
                }
            }

            // 다녀온 현장 (완료) — 초록 · 먼저 표시
            if (state.visitedRows.isNotEmpty()) {
                item(key = "done-head") { SectionHead("다녀온 현장 (완료)", TossSuccess) }
                items(state.visitedRows, key = { "done-${it.customerId}" }) { v ->
                    VisitedRowItem(v, TossSuccess, "완료", onClick = { onOpenCustomer(v.customerId) })
                }
            }
            // 다녀올 현장 (예정) — 파랑 · 아래
            if (state.upcomingRows.isNotEmpty()) {
                item(key = "up-head") { SectionHead("다녀올 현장 (예정)", TossBlue) }
                items(state.upcomingRows, key = { "up-${it.customerId}" }) { v ->
                    VisitedRowItem(v, TossBlue, "예정", onClick = { onOpenCustomer(v.customerId) })
                }
            }
        }
    }
}

@Composable
private fun SectionHead(label: String, color: Color) {
    Row(
        Modifier.padding(top = 14.dp, bottom = 8.dp, start = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(color))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
    }
}

@Composable
private fun VisitedRowItem(v: VisitedRow, accent: Color, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 9.dp)
            .pressScale(interaction)
            .tossCardShadow(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(v.dateLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = accent,
            modifier = Modifier.width(44.dp))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) { Text(tag, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = accent) }
            }
            Text(v.addr, fontSize = 12.5.sp, color = TossTextInfo, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Default.ChevronRight, null, tint = TossDivider, modifier = Modifier.size(20.dp))
    }
}
