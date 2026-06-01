package com.detailline.callfollowcrm.presentation.screen.estimate

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
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * 견적 회신 리마인드 목록 (2026-06-01) — 홈 카드에서 진입.
 *   견적 보낸 지 N일 됐는데 시공일 미등록인 고객. [보내기]=채팅 본문 prefill / [넘기기]=제외.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateFollowupScreen(
    viewModel: EstimateFollowupViewModel,
    onBack: () -> Unit,
    onOpenChat: (phone: String, customerId: Long?) -> Unit
) {
    val rows by viewModel.rows.collectAsState()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("견적 회신 챙기기 · ${rows.size}건", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
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
                    Text("답 기다리는 견적이 없어요", color = TossTextSecondary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text("채팅에서 견적을 보내면 며칠 뒤 여기서 챙겨드려요", style = MaterialTheme.typography.bodySmall, color = TossTextTertiary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(inner).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rows, key = { "${it.item.customerId}-${it.item.estimateDayStartMs}" }) { row ->
                    FollowupCard(
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
private fun FollowupCard(row: FollowupRow, onSend: () -> Unit, onDismiss: () -> Unit) {
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.item.customerName?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(row.item.phone),
                    style = MaterialTheme.typography.titleMedium, color = TossTextPrimary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(TossBlueSoft)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("견적 ${row.item.daysSince}일째", style = MaterialTheme.typography.labelSmall, color = TossBlue, fontWeight = FontWeight.SemiBold)
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
