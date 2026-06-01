package com.detailline.callfollowcrm.presentation.screen.reminder

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
import androidx.compose.foundation.lazy.items
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
import com.detailline.callfollowcrm.domain.reminder.ReminderKind
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * 시공 D-1 / 도착 안내 리마인드 목록 (2026-06-01) — 홈 카드에서 진입.
 *   사장님 명시 요청(시공 전날 알림). [보내기]=채팅 본문 채워 진입(사장님 ▶), [넘기기]=제외.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleReminderScreen(
    viewModel: ScheduleReminderViewModel,
    onBack: () -> Unit,
    onOpenChat: (phone: String, customerId: Long?) -> Unit
) {
    val rows by viewModel.rows.collectAsState()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("시공 안내 문자 · ${rows.size}건", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        if (rows.isEmpty()) {
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 36.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("지금 보낼 시공 안내가 없어요", color = TossTextSecondary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text("내일·오늘 시공이 잡히면 여기서 안내 문자를 보낼 수 있어요", style = MaterialTheme.typography.bodySmall, color = TossTextTertiary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(inner).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rows, key = { "${it.item.kind}-${it.item.customerId}-${it.item.scheduledDayStartMs}" }) { row ->
                    ReminderCard(
                        row = row,
                        onSend = {
                            viewModel.prepareSend(row)
                            onOpenChat(row.item.phone, row.item.customerId.takeIf { it > 0 })
                        },
                        onDismiss = { viewModel.dismiss(row) }
                    )
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun ReminderCard(row: ReminderRow, onSend: () -> Unit, onDismiss: () -> Unit) {
    val isToday = row.item.kind == ReminderKind.ARRIVAL
    val (badgeText, badgeColor, badgeBg) =
        if (isToday) Triple("오늘 출발", TossSuccess, Color(0xFFE6F7EC))
        else Triple("내일 시공", TossBlue, TossBlueSoft)
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.item.customerName?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(row.item.phone),
                    style = MaterialTheme.typography.titleMedium, color = TossTextPrimary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(badgeBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(badgeText, style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(12.dp)) {
                Text(row.body, style = MaterialTheme.typography.bodyMedium, color = TossTextSecondary)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossBlue)
                        .clickable { onSend() }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("보내기", color = Color.White, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                        .clickable { onDismiss() }.padding(horizontal = 18.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("넘기기", color = TossTextSecondary, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
