package com.detailline.callfollowcrm.presentation.screen.schedule

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var pastExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "시공 예약 · ${state.upcomingCount}건",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        if (state.upcomingByMonth.isEmpty() && state.past.isEmpty()) {
            Box(
                Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .background(TossGrayBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗓", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "아직 시공 예약된 고객이 없어요",
                        style = MaterialTheme.typography.titleMedium,
                        color = TossTextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "고객 상세 → 일정 → \"시공 예약일 설정\"에서 등록",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextTertiary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .background(TossGrayBg),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 다가올 예약 (월별 그룹)
                state.upcomingByMonth.forEach { group ->
                    item(key = "header-${group.monthHeader}") {
                        Text(
                            group.monthHeader,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = TossTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(group.customers, key = { "u-${it.id}" }) { c ->
                        ScheduleCustomerCard(customer = c, onClick = { onOpenCustomer(c.id) })
                    }
                }

                // 지난 예약 (접힘/펼침)
                if (state.past.isNotEmpty()) {
                    item(key = "past-header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "지난 예약 (${state.past.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = TossTextTertiary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { pastExpanded = !pastExpanded }) {
                                Text(
                                    if (pastExpanded) "접기" else "펼치기",
                                    color = TossBlue
                                )
                            }
                        }
                    }
                    if (pastExpanded) {
                        items(state.past, key = { "p-${it.id}" }) { c ->
                            ScheduleCustomerCard(
                                customer = c,
                                onClick = { onOpenCustomer(c.id) },
                                muted = true
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun ScheduleCustomerCard(
    customer: CustomerEntity,
    onClick: () -> Unit,
    muted: Boolean = false
) {
    val scheduled = customer.scheduledWorkDate ?: return
    TossCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DateTimeUtils.formatKoreanDate(scheduled),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (muted) TossTextTertiary else TossTextPrimary,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    DateTimeUtils.dDayLabel(scheduled),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (muted) TossTextTertiary else TossBlue,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                customer.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(customer.phoneNumber),
                style = MaterialTheme.typography.titleMedium,
                color = if (muted) TossTextTertiary else TossTextPrimary
            )
            Text(
                PhoneNumberFormatter.format(customer.phoneNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = TossTextSecondary
            )
            if (customer.memo.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    customer.memo.lineSequence().firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "상태: ${customer.status}",
                style = MaterialTheme.typography.labelSmall,
                color = TossTextTertiary
            )
        }
    }
}
