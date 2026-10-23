package com.detailline.callfollowcrm.presentation.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.domain.intake.InflowGroup
import com.detailline.callfollowcrm.domain.intake.InflowStats
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType

/**
 * 🔎 **유입경로 모아보기** 카드 — 「내 기록」 맨 아래. (2026-10-23 사장님)
 *   접수서 "저희를 어디서 보셨어요?" 를 **경로별로 전체 누적** 집계(InflowStats). 경로를 누르면 세부(블로그·키워드) 펼침.
 *   "네이버가 제일 많네 → 광고 거기 더" 를 한눈에. 글자·색은 토큰(AppType/AppTheme) — 화면마다 제각각 안 되게.
 */
@Composable
fun InflowCard(groups: List<InflowGroup>) {
    if (groups.isEmpty()) return
    val total = remember(groups) { InflowStats.total(groups) }
    val maxCount = remember(groups) { groups.maxOf { it.count }.coerceAtLeast(1) }
    var expanded by remember { mutableStateOf<String?>(null) }
    TossCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("손님이 어디서 왔나", style = AppType.headline, color = AppTheme.colors.text)
            Spacer(Modifier.height(2.dp))
            Text("접수서 쓴 손님 전체 · 총 ${total}명", style = AppType.caption, color = AppTheme.colors.textHint)
            Spacer(Modifier.height(12.dp))
            groups.forEach { g ->
                val on = expanded == g.source
                Column(
                    Modifier.fillMaxWidth().clip(AppShape.sm)
                        .clickable(enabled = g.details.isNotEmpty()) { expanded = if (on) null else g.source }
                        .padding(vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.source, style = AppType.label, color = AppTheme.colors.text, modifier = Modifier.width(68.dp))
                        Box(
                            Modifier.weight(1f).height(10.dp).clip(AppShape.pill).background(AppTheme.colors.surfaceMuted)
                        ) {
                            Box(
                                Modifier.fillMaxWidth((g.count.toFloat() / maxCount).coerceIn(0.06f, 1f))
                                    .fillMaxHeight().clip(AppShape.pill).background(AppTheme.colors.primary)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("${g.count}명", style = AppType.label, color = AppTheme.colors.text)
                        if (g.details.isNotEmpty()) {
                            Spacer(Modifier.width(4.dp))
                            Text(if (on) "▾" else "▸", style = AppType.caption, color = AppTheme.colors.textHint)
                        }
                    }
                    if (on) {
                        g.details.forEach { d ->
                            Text(
                                "· ${d.label}  ${d.count}", style = AppType.caption, color = AppTheme.colors.textSub,
                                modifier = Modifier.padding(start = 68.dp, top = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
}
