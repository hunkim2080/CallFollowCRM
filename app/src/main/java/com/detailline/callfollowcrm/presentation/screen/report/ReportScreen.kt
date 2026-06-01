package com.detailline.callfollowcrm.presentation.screen.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.MoneyFormatter

/**
 * 비즈니스 리포트 (2026-06-01) — "한 달 고생했다" 저널 톤. 기간별 매출·현장·신규 + 현재 미수.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val period by viewModel.periodState.collectAsState()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("비즈니스 리포트", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(inner).fillMaxSize().background(TossGrayBg),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                            Text(p.label, style = MaterialTheme.typography.labelLarge,
                                color = if (sel) Color.White else TossTextSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                // 매출 hero
                TossCard {
                    Column {
                        Text("${period.label} 받은 돈 (매출)", style = MaterialTheme.typography.labelMedium,
                            color = TossTextSecondary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text(MoneyFormatter.won(state.revenue), fontSize = 30.sp,
                            fontWeight = FontWeight.Bold, color = TossSuccess)
                        state.revenueDeltaPct?.let { d ->
                            Spacer(Modifier.height(6.dp))
                            val up = d >= 0
                            Text(
                                (if (up) "▲ " else "▼ ") + "직전 대비 ${if (up) "+" else ""}$d% " +
                                    "(${MoneyFormatter.won(state.prevRevenue)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (up) TossSuccess else TossError,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("시공 현장", "${state.jobs}곳", TossBlue, Modifier.weight(1f))
                    StatCard("새 고객", "${state.newCustomers}명", TossBlue, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("현재 미수금", MoneyFormatter.won(state.outstandingNow),
                        if (state.outstandingNow > 0) TossError else TossSuccess, Modifier.weight(1f))
                    StatCard("완납 고객", "${state.paidOffNow}명", TossSuccess, Modifier.weight(1f))
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "이 리포트는 KPI 가 아니라 '한 달 고생 기록'이에요. 매출은 받은 돈 기준, 미수금은 현재 시점 기준.",
                    style = MaterialTheme.typography.bodySmall, color = TossTextTertiary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color.White)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TossTextSecondary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}
