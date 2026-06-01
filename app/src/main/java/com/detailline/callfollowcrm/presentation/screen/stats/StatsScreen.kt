package com.detailline.callfollowcrm.presentation.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.screen.report.ReportPeriod
import com.detailline.callfollowcrm.presentation.screen.report.ReportViewModel
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.MoneyFormatter

/**
 * 통계 탭 — 프로토타입 "통계" 화면(stats-hero + stat-grid) 구조.
 *   집계는 기존 ReportViewModel(고객·입금·시공일) 재사용 → DB 변경 없음.
 *   실제 보유 데이터만 표시(허위 숫자 X): 다녀온 현장 / 새 고객 / 매출 / 미수금.
 */
@Composable
fun StatsScreen(viewModel: ReportViewModel) {
    val state by viewModel.state.collectAsState()
    val period by viewModel.periodState.collectAsState()

    Column(Modifier.fillMaxSize().background(Color(0xFFF4F5F7))) {
        // 앱바 (프로토: 제목만)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF4F5F7))
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("통계", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 기간 칩
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportPeriod.values().forEach { p ->
                        TossChip(text = p.label, selected = period == p, onClick = { viewModel.setPeriod(p) })
                    }
                }
            }

            // 파란 히어로
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(TossBlue, TossBlueDark)),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(22.dp)
                ) {
                    Text("👏", fontSize = 26.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${period.label}, 정말 고생하셨어요",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "이번 기간 현장 ${state.jobs}곳을 다녀오셨어요.",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 2x2 통계 그리드 (보유 데이터)
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCell("다녀온 현장", "${state.jobs}", "곳", TossTextPrimary, Modifier.weight(1f))
                    StatCell("새 고객", "${state.newCustomers}", "명", TossBlue, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCellMoney("받은 돈(매출)", state.revenue, TossSuccess, Modifier.weight(1f))
                    StatCellMoney("아직 못 받은 돈", state.outstandingNow, TossError, Modifier.weight(1f))
                }
            }

            // 전월 대비
            state.revenueDeltaPct?.let { delta ->
                item {
                    val up = delta >= 0
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Text("직전 기간 대비 매출", fontSize = 13.sp, color = TossTextTertiary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${if (up) "▲" else "▼"} ${kotlin.math.abs(delta)}%",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (up) TossSuccess else TossError
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, unit: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
            Spacer(Modifier.height(0.dp))
            Text(unit, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.5.sp, color = TossTextTertiary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatCellMoney(label: String, amount: Long, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text(MoneyFormatter.won(amount), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.5.sp, color = TossTextTertiary, fontWeight = FontWeight.SemiBold)
    }
}
