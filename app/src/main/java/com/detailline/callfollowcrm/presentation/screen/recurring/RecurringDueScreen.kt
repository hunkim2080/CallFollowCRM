package com.detailline.callfollowcrm.presentation.screen.recurring

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
import com.detailline.callfollowcrm.domain.recurring.DueItem
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * "정기문자 보낼 때 됐어요" 목록 (2026-06-01) — 홈 카드에서 진입.
 *   [보내기] = 채팅에 본문 채워서 진입(사장님이 ▶). [넘기기] = 이번 회차 제외.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringDueScreen(
    viewModel: RecurringDueViewModel,
    onBack: () -> Unit,
    onOpenChat: (phone: String, customerId: Long?) -> Unit
) {
    val items by viewModel.dueItems.collectAsState()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("보낼 정기문자 · ${items.size}건", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        if (items.isEmpty()) {
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 36.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("지금 보낼 정기문자가 없어요", color = TossTextSecondary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text("규칙은 설정 → 정기문자 에서 관리해요", style = MaterialTheme.typography.bodySmall, color = TossTextTertiary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(inner).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { "${it.ruleId}-${it.customerId}-${it.occurrenceDayStartMs}" }) { item ->
                    DueCard(
                        item = item,
                        onSend = {
                            viewModel.prepareSend(item)
                            onOpenChat(item.phone, item.customerId.takeIf { it > 0 })
                        },
                        onDismiss = { viewModel.dismiss(item) }
                    )
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun DueCard(item: DueItem, onSend: () -> Unit, onDismiss: () -> Unit) {
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.customerName?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(item.phone),
                    style = MaterialTheme.typography.titleMedium, color = TossTextPrimary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(TossBlueSoft)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(item.ruleName, style = MaterialTheme.typography.labelSmall, color = TossBlue, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "시공 후 ${item.intervalDays}일 · ${DateTimeUtils.formatDateOnly(item.occurrenceDayStartMs)} 회차",
                style = MaterialTheme.typography.labelSmall, color = TossTextTertiary
            )
            Spacer(Modifier.height(8.dp))
            // 보낼 문구 미리보기
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(12.dp)) {
                Text(item.renderedBody, style = MaterialTheme.typography.bodyMedium, color = TossTextSecondary)
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
