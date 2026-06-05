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

/**
 * 마감 브리핑 화면 — 프로토 brief 알림의 "오늘 정리 보기" 목적지.
 *   오늘 신규 / 오늘 입금 / 내일 시공 일정을 한 화면에. 탭하면 해당 목록·고객으로 이동.
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
            Text("오늘도 정말 고생하셨어요.", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(state.dateLabel, fontSize = 13.sp, color = TossTextTertiary)
            Spacer(Modifier.height(16.dp))

            // 오늘 신규 / 오늘 입금 — 2칸
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("오늘 신규 문의", "${state.newCount}건", "👋", Modifier.weight(1f)) { onOpenNewLeads() }
                StatTile(
                    "오늘 입금",
                    "${state.depositCount}건",
                    "💰",
                    Modifier.weight(1f),
                    sub = state.paidSumLabel
                ) { onOpenSettlement() }
            }
            Spacer(Modifier.height(18.dp))

            // 내일 시공
            Text("내일 시공", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
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
                            Text(job.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                            Text(job.timeLabel, fontSize = 12.5.sp, color = TossTextSecondary, modifier = Modifier.padding(top = 2.dp))
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
            Spacer(Modifier.height(20.dp))
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
            .padding(14.dp)
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, color = TossTextTertiary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 11.5.sp, color = TossBlue, fontWeight = FontWeight.Bold)
        }
    }
}
