package com.detailline.callfollowcrm.presentation.screen.settlement

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.MoneyFormatter
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * 정산(미수금) 화면 — 정산 Phase 1.
 *
 *   1. 히어로 — "아직 못 받은 돈" 총액 + 미수 곳 수 + 받은 돈
 *   2. 필터 칩 — 전체 / 미수 / 완납
 *   3. 고객 카드 — 받을돈 + [계약금][잔금] 받음 토글 + 미수/완납 표시
 *      · 잔금 받음 켜기 = "전액 받았어요?" 한 번 확인(오탭 방지)
 *      · 완납 카드는 "완납 취소" 로 되돌리기
 *   4. 카드 탭 → 고객 상세 (금액 수정은 거기서)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    viewModel: SettlementViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val filter by viewModel.filterState.collectAsState()
    val tab by viewModel.tabState.collectAsState()

    // 잔금 받음 켤 때 띄우는 완납 확인 대상.
    var confirmPayOff by remember { mutableStateOf<SettleItem?>(null) }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            // 프로토 정산 앱바 — "정산". 하단 탭이므로 back 화살표 제거(시스템 뒤로는 동작).
            TopAppBar(
                title = {
                    Text("정산", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.6).sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
        ) {
            SettleTabBar(current = tab, onSelect = viewModel::setTab)
            when (tab) {
                SettleTab.LIST -> SettleListContent(
                    state = state,
                    filter = filter,
                    onSelectFilter = viewModel::setFilter,
                    onOpenCustomer = onOpenCustomer,
                    onToggleDeposit = { item -> viewModel.setDepositPaid(item.customerId, !item.calc.depositPaid) },
                    onToggleBalance = { item ->
                        if (item.calc.balancePaid) viewModel.setBalancePaid(item.customerId, false)
                        else confirmPayOff = item
                    },
                    modifier = Modifier.weight(1f)
                )
                SettleTab.CASHFLOW -> CashFlowContent(
                    viewModel = viewModel,
                    onOpenCustomer = onOpenCustomer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    confirmPayOff?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmPayOff = null },
            title = { Text("전액 받았어요?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${item.name ?: PhoneNumberFormatter.format(item.phone)} 님 잔금까지 받은 걸로 " +
                        "체크하면 완납 처리돼요. (받을 돈 ${MoneyFormatter.won(item.calc.total)})"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setBalancePaid(item.customerId, true)
                    confirmPayOff = null
                }) { Text("네, 완납", color = TossBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPayOff = null }) {
                    Text("취소", color = TossTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettleTabBar(current: SettleTab, onSelect: (SettleTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettleTab.values().forEach { t ->
            val selected = t == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) TossTextPrimary else Color.White)
                    .clickable { onSelect(t) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) Color.White else TossTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SettleListContent(
    state: SettlementUiState,
    filter: SettleFilter,
    onSelectFilter: (SettleFilter) -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onToggleDeposit: (SettleItem) -> Unit,
    onToggleBalance: (SettleItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TossGrayBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "hero") {
            SettleHero(
                outstandingTotal = state.outstandingTotal,
                outstandingCount = state.outstandingCount,
                receivedTotal = state.receivedTotal
            )
        }
        item(key = "filters") {
            FilterRow(
                current = filter,
                allCount = state.allCount,
                outstandingCount = state.outstandingCount,
                paidOffCount = state.paidOffCount,
                onSelect = onSelectFilter
            )
        }
        if (state.rows.isEmpty()) {
            item(key = "empty") { EmptyState(filter) }
        } else {
            items(state.rows, key = { "s-${it.customerId}" }) { item ->
                SettleRowCard(
                    item = item,
                    onOpenCustomer = { onOpenCustomer(item.customerId) },
                    onToggleDeposit = { onToggleDeposit(item) },
                    onToggleBalance = { onToggleBalance(item) }
                )
            }
        }
        item(key = "tail") { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun SettleHero(outstandingTotal: Long, outstandingCount: Int, receivedTotal: Long) {
    TossCard {
        Column {
            Text(
                if (outstandingCount > 0) "아직 못 받은 돈" else "받을 돈",
                style = MaterialTheme.typography.labelMedium,
                color = TossTextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            if (outstandingCount > 0) {
                Text(
                    MoneyFormatter.won(outstandingTotal),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = TossError
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "미수 ${outstandingCount}곳 · 받은 돈 ${MoneyFormatter.won(receivedTotal)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TossTextTertiary
                )
            } else {
                Text(
                    "받을 돈 모두 받았어요 👍",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TossSuccess
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "지금까지 받은 돈 ${MoneyFormatter.won(receivedTotal)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TossTextTertiary
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    current: SettleFilter,
    allCount: Int,
    outstandingCount: Int,
    paidOffCount: Int,
    onSelect: (SettleFilter) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip("전체 $allCount", current == SettleFilter.ALL) { onSelect(SettleFilter.ALL) }
        FilterChip("미수 $outstandingCount", current == SettleFilter.OUTSTANDING) { onSelect(SettleFilter.OUTSTANDING) }
        FilterChip("완납 $paidOffCount", current == SettleFilter.PAID_OFF) { onSelect(SettleFilter.PAID_OFF) }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) TossBlue else Color.White
    val fg = if (selected) Color.White else TossTextSecondary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettleRowCard(
    item: SettleItem,
    onOpenCustomer: () -> Unit,
    onToggleDeposit: () -> Unit,
    onToggleBalance: () -> Unit
) {
    val c = item.calc
    TossCard(onClick = onOpenCustomer) {
        Column {
            // 이름 + 미수/완납 금액
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name ?: PhoneNumberFormatter.format(item.phone),
                    style = MaterialTheme.typography.titleMedium,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (c.isPaidOff) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(TossSuccess.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("완납", color = TossSuccess, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("미수", style = MaterialTheme.typography.labelSmall, color = TossTextTertiary)
                        Text(
                            MoneyFormatter.won(c.outstanding),
                            style = MaterialTheme.typography.titleMedium,
                            color = TossError,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "받을 돈 ${MoneyFormatter.won(c.total)}" +
                    if (c.received > 0L) " · 받음 ${MoneyFormatter.won(c.received)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = TossTextSecondary
            )

            Spacer(Modifier.height(12.dp))
            // 받음 토글 2개
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaidToggle(
                    label = "계약금",
                    amount = c.depositAmount,
                    paid = c.depositPaid,
                    onClick = onToggleDeposit,
                    modifier = Modifier.weight(1f)
                )
                PaidToggle(
                    label = "잔금",
                    amount = c.balanceAmount,
                    paid = c.balancePaid,
                    onClick = onToggleBalance,
                    modifier = Modifier.weight(1f)
                )
            }

            if (c.isPaidOff) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "완납 취소",
                    style = MaterialTheme.typography.labelMedium,
                    color = TossTextTertiary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleBalance() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PaidToggle(
    label: String,
    amount: Long,
    paid: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (paid) TossBlueSoft else Color.White
    val fg = if (paid) TossBlue else TossTextSecondary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 체크 동그라미
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (paid) TossBlue else TossDivider),
            contentAlignment = Alignment.Center
        ) {
            if (paid) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                if (paid) "$label 받음" else "$label 받기",
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
            if (amount > 0L) {
                Text(
                    MoneyFormatter.won(amount),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (paid) TossBlue else TossTextTertiary
                )
            }
        }
    }
}

@Composable
private fun EmptyState(filter: SettleFilter) {
    val msg = when (filter) {
        SettleFilter.ALL -> "정산할 내역이 아직 없어요"
        SettleFilter.OUTSTANDING -> "못 받은 돈이 없어요 👍"
        SettleFilter.PAID_OFF -> "완납된 고객이 아직 없어요"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("💰", fontSize = 32.sp)
            Spacer(Modifier.height(10.dp))
            Text(msg, style = MaterialTheme.typography.titleMedium, color = TossTextSecondary)
            if (filter == SettleFilter.ALL) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "고객 상세 → 시공비(총액)·계약금·잔금 을 입력하면 여기 정산에 모여요",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextTertiary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
