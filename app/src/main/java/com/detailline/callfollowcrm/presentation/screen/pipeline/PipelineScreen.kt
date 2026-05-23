package com.detailline.callfollowcrm.presentation.screen.pipeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun PipelineScreen(
    viewModel: PipelineViewModel,
    statusLabel: String,
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit
) {
    val customers by viewModel.customers.collectAsState()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "$statusLabel · ${customers.size}명",
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
        if (customers.isEmpty()) {
            Box(
                Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .background(TossGrayBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "이 상태의 고객이 아직 없어요",
                        style = MaterialTheme.typography.titleMedium,
                        color = TossTextPrimary
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
                items(customers) { c ->
                    TossCard(onClick = { onOpenCustomer(c.id) }) {
                        Column {
                            Text(
                                c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber),
                                style = MaterialTheme.typography.titleLarge,
                                color = TossTextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                PhoneNumberFormatter.format(c.phoneNumber),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TossTextSecondary
                            )
                            if (c.memo.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    c.memo.lineSequence().firstOrNull().orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TossTextSecondary,
                                    maxLines = 1
                                )
                            }
                            c.scheduledWorkDate?.let { scheduled ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "🗓 ${DateTimeUtils.formatDateOnly(scheduled)} 시공 예약 · ${DateTimeUtils.dDayLabel(scheduled)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TossBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "마지막 업데이트 · ${DateTimeUtils.formatShort(c.updatedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TossTextTertiary
                            )
                        }
                    }
                }
            }
        }
    }
}
