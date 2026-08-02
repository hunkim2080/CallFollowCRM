package com.detailline.callfollowcrm.presentation.screen.settlement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * 정산 화면 — 프로토 `s-settle` 1:1 (2026-06-02 재구성).
 *
 * 단일 스크롤 (프로토 markup 순서 그대로):
 *   1. settle-top   — 다크 월매출 대시보드 (월 이동 · 받은 돈 · 전월 대비 · 목표 진행률 + 페이스 · 미수 요약)
 *   2. cashcal      — 현금흐름 달력 ([CashFlowCard])
 *   3. sec-sub      — "받을 돈 · 미수 관리"
 *   4. fchips       — 전체 / 미수금 / 완료
 *   5. settle-list  — 미완납 srow + "완납 N건" + 완납 srow-done
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    viewModel: SettlementViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onOpenScheduleAtDay: (Long) -> Unit = {}   // srow-go "이 시공 일정 보기" → 그 시공일의 일정 화면 (프로토 .srow-go)
) {
    val state by viewModel.state.collectAsState()
    val filter by viewModel.filterState.collectAsState()
    val top by viewModel.settleTop.collectAsState()

    // 돈 기록 결과(받음 ✓ / 저장 실패) 알림 — 실패가 더 이상 조용히 묻히지 않게. TeamScreen 패턴.
    val snackbar = remember { SnackbarHostState() }
    val toast by viewModel.toast.collectAsState()
    LaunchedEffect(toast) {
        toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() }
    }

    // 잔금/전액 "확인" 누를 때 완납 확인 (오탭 방지).
    var confirmPayOff by remember { mutableStateOf<SettleItem?>(null) }
    var confirmDeposit by remember { mutableStateOf<SettleItem?>(null) }   // 계약금도 잔금처럼 한 번 묻기(실수 즉시확정 방지). 2026-07-30
    var showGoalEditor by remember { mutableStateOf(false) }

    val active = remember(state.rows) { state.rows.filter { !it.calc.isPaidOff } }
    val done = remember(state.rows) { state.rows.filter { it.calc.isPaidOff } }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = TossGrayBg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars.add(WindowInsets(top = 10.dp)),
                title = {
                    // 부제 = 보고 있는 달(달 이동하면 그 달). 헤더가 '구역'을 갖게 → 무거운 카드에 안 눌림. (2026-08-02 사장님 A안 승인)
                    Column {
                        Text("정산", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.6).sp)
                        Text(top.monthLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary, letterSpacing = (-0.1).sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(top = inner.calculateTopPadding())
                .fillMaxSize()
                .background(TossGrayBg),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp)  // top 14 = 헤더와 첫 카드 숨 쉬는 간격
        ) {
            // 1. settle-top
            item(key = "settle-top") {
                SettleTopCard(
                    top = top,
                    onPrev = viewModel::prevMonth,
                    onNext = viewModel::nextMonth,
                    onEditGoal = { showGoalEditor = true }
                )
                Spacer(Modifier.height(12.dp))
            }
            // 2. 현금흐름 달력
            item(key = "cashcal") {
                CashFlowCard(viewModel = viewModel, onOpenCustomer = onOpenCustomer)
                Spacer(Modifier.height(26.dp))
            }
            // 3. sec-sub
            item(key = "sec-sub") {
                Text(
                    "받을 돈 · 미수 관리",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                    letterSpacing = (-0.13).sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 11.dp)
                )
            }
            // 4. fchips
            item(key = "fchips") {
                FChips(current = filter, onSelect = viewModel::setFilter)
                Spacer(Modifier.height(13.dp))
            }
            // 5. settle-list
            settleList(
                filter = filter,
                active = active,
                done = done,
                onOpenCustomer = onOpenCustomer,
                onOpenScheduleAtDay = onOpenScheduleAtDay,
                onConfirmDeposit = { confirmDeposit = it },
                onConfirmBalance = { confirmPayOff = it },
                onUndoPaid = { viewModel.setBalancePaid(it.customerId, false) }
            )
            item(key = "tail") { Spacer(Modifier.height(12.dp)) }
        }
    }

    // 완납 확인
    confirmPayOff?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmPayOff = null },
            title = { Text("전액 받았어요?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${item.name ?: PhoneNumberFormatter.format(item.phone)} 님 잔금까지 받은 걸로 " +
                        "체크하면 완납 처리돼요. (총 ${manwonText(item.calc.total)})"
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

    // 계약금 받음 확인 (잔금과 동일 — 실수로 즉시 '받음' 처리되던 것 방지). 2026-07-30
    confirmDeposit?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDeposit = null },
            title = { Text("계약금 받았어요?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${item.name ?: PhoneNumberFormatter.format(item.phone)} 님 계약금을 받은 걸로 " +
                        "체크할까요? (계약금 ${manwonText(item.calc.depositAmount)})"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDepositPaid(item.customerId, true)
                    confirmDeposit = null
                }) { Text("네, 받았어요", color = TossBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeposit = null }) {
                    Text("취소", color = TossTextSecondary)
                }
            }
        )
    }

    // 목표 수정 — 인라인 바텀 오버레이(액티비티 윈도우라 키보드가 안 가림). 2026-06-07
    if (showGoalEditor) {
        GoalEditSheet(
            initialManwon = goalManwonHint(top),
            onConfirm = { viewModel.setMonthlyGoal(it); showGoalEditor = false },
            onDismiss = { showGoalEditor = false }
        )
    }
    }
}

/* ─────────────── 1. settle-top 다크 월매출 대시보드 ─────────────── */

private val SettleTopTop = Color(0xFF272D3D)
private val SettleTopBottom = Color(0xFF14171F)
private val PrevUpFg = Color(0xFF7CF0B0)
private val PrevUpBg = Color(0x3316C172)   // rgba(22,193,114,.2)
private val PrevDownFg = Color(0xFFFFAFC0)
private val PrevDownBg = Color(0x33F0436A)  // rgba(240,67,106,.2)
private val GoalGold = Color(0xFFFFD479)
private val PaceAhead = Color(0xFF7BE0A8)
private val PaceBehind = Color(0xFFFFC658)

@Composable
private fun SettleTopCard(
    top: SettleTopState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEditGoal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tossCardShadow(RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(SettleTopTop, SettleTopBottom)))
            .padding(20.dp)
    ) {
        // smonth — 월 이동
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SNav(Icons.Default.ChevronLeft, "이전 달", onPrev, enabled = true)
            Text(
                top.monthLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            SNav(Icons.Default.ChevronRight, "다음 달", onNext, enabled = top.canGoNext)
        }
        // lbl + amt + prev
        Text(top.recvLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                top.receivedManwon.toCommaString(),
                fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                letterSpacing = (-1.0).sp
            )
            Text("만원", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(start = 2.dp, bottom = 5.dp))
        }
        // prev pill
        val up = top.prevPct >= 0
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (up) PrevUpBg else PrevDownBg)
                .padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            Text(
                "전월 대비 ${if (up) "+" else ""}${top.prevPct}%",
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (up) PrevUpFg else PrevDownFg
            )
        }

        // mile2 — 목표 진행률 + 페이스
        Column(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable { onEditGoal() }
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    top.m2Top, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (top.goalReached) GoalGold else Color.White,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("목표 수정", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.65f))
                }
            }
            // m2-bar
            Box(
                modifier = Modifier
                    .padding(top = 11.dp)
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                if (top.fillPct > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((top.fillPct / 100f).coerceIn(0.02f, 1f))
                            .height(7.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                Brush.horizontalGradient(
                                    if (top.goalReached) listOf(Color(0xFFFFC658), Color(0xFFF6A609))
                                    else listOf(Color(0xFF5BA0FF), Color(0xFF3182F6))
                                )
                            )
                    )
                }
            }
            Text(
                top.m2Next, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(top = 8.dp)
            )
            top.paceText?.let { pace ->
                Text(
                    "$pace · ${top.daysLeft}일 남음",
                    fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (top.paceAhead) PaceAhead else PaceBehind,
                    modifier = Modifier.padding(top = 9.dp)
                )
            }
        }

        // sdiv
        Box(
            Modifier
                .padding(vertical = 15.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.13f))
        )
        // sout
        if (top.isLiveMonth) {
            Row {
                Text("전체 아직 받을 돈 ", fontSize = 13.sp, color = Color.White.copy(alpha = 0.82f))
                Text("${top.outstandingManwon.toCommaString()}만원", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(" · 미수 ", fontSize = 13.sp, color = Color.White.copy(alpha = 0.82f))
                Text("${top.outstandingCount}건", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        } else {
            Text("지난 달 정산 내역이에요", fontSize = 13.sp, color = Color.White.copy(alpha = 0.82f))
        }
    }
}

@Composable
private fun SNav(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.10f else 0.04f))
            .let { if (enabled) it.clickable { onClick() } else it },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, cd, tint = Color.White.copy(alpha = if (enabled) 0.8f else 0.3f), modifier = Modifier.size(16.dp))
    }
}

/* ─────────────── 4. fchips ─────────────── */

@Composable
private fun FChips(current: SettleFilter, onSelect: (SettleFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FChip("전체", current == SettleFilter.ALL) { onSelect(SettleFilter.ALL) }
        FChip("미수금", current == SettleFilter.OUTSTANDING) { onSelect(SettleFilter.OUTSTANDING) }
        FChip("완료", current == SettleFilter.PAID_OFF) { onSelect(SettleFilter.PAID_OFF) }
    }
}

@Composable
private fun FChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) TossBlue else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else TossTextSecondary)
    }
}

/* ─────────────── 5. settle-list ─────────────── */

private fun androidx.compose.foundation.lazy.LazyListScope.settleList(
    filter: SettleFilter,
    active: List<SettleItem>,
    done: List<SettleItem>,
    onOpenCustomer: (Long) -> Unit,
    onOpenScheduleAtDay: (Long) -> Unit,
    onConfirmDeposit: (SettleItem) -> Unit,
    onConfirmBalance: (SettleItem) -> Unit,
    onUndoPaid: (SettleItem) -> Unit
) {
    val showActive = filter != SettleFilter.PAID_OFF
    val showDone = filter != SettleFilter.OUTSTANDING
    val activeShown = if (showActive) active else emptyList()
    val doneShown = if (showDone) done else emptyList()

    if (activeShown.isEmpty() && doneShown.isEmpty()) {
        item(key = "empty") { EmptyMini(filter) }
        return
    }

    activeShown.forEachIndexed { idx, item ->
        item(key = "active-${item.customerId}") {
            SettleRow(
                item = item, index = idx,
                onOpenCustomer = { onOpenCustomer(item.customerId) },
                onOpenSchedule = { onOpenScheduleAtDay(item.scheduledWorkDate ?: 0L) },
                onConfirmDeposit = { onConfirmDeposit(item) },
                onConfirmBalance = { onConfirmBalance(item) },
                onUndoPaid = { onUndoPaid(item) }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
    if (doneShown.isNotEmpty()) {
        if (filter == SettleFilter.ALL) {
            item(key = "done-sec") {
                Text(
                    "완납 ${doneShown.size}건",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                    modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 11.dp)
                )
            }
        }
        doneShown.forEachIndexed { idx, item ->
            item(key = "done-${item.customerId}") {
                SettleDoneRow(item = item, index = idx)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SettleRow(
    item: SettleItem,
    index: Int,
    onOpenCustomer: () -> Unit,
    onOpenSchedule: () -> Unit,
    onConfirmDeposit: () -> Unit,
    onConfirmBalance: () -> Unit,
    onUndoPaid: () -> Unit
) {
    val c = item.calc
    val name = item.name ?: PhoneNumberFormatter.format(item.phone)
    TossCard(onClick = onOpenCustomer, contentPadding = PaddingValues(16.dp)) {
        Column {
            // 상단: 아바타 + 이름 + 시공일
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(item.name, index)
                Spacer(Modifier.width(13.dp))
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Spacer(Modifier.weight(1f))
                item.scheduledWorkDate?.let {
                    Text("시공 ${DateTimeUtils.formatDateOnly(it)}", fontSize = 12.sp, color = TossTextTertiary, fontWeight = FontWeight.SemiBold)
                }
            }
            // 주소
            item.address?.takeIf { it.isNotBlank() }?.let { addr ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 9.dp, bottom = 3.dp)
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = TossTextTertiary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(addr, fontSize = 12.5.sp, color = TossTextTertiary, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // .pay 블록
            Spacer(Modifier.height(12.dp))
            PayBlock(c, onConfirmDeposit, onConfirmBalance, onUndoPaid)
            // srow-go
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TossDivider)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSchedule() }
                    .padding(top = 10.dp)
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = TossBlue, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text("이 시공 일정 보기", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = TossBlue.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PayBlock(
    c: com.detailline.callfollowcrm.domain.settlement.SettleRow,
    onConfirmDeposit: () -> Unit,
    onConfirmBalance: () -> Unit,
    onUndoPaid: () -> Unit
) {
    val hasDeposit = c.depositAmount > 0L
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("총 ${manwonText(c.total)}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.2).sp)
            Spacer(Modifier.height(4.dp))
            // pay-stat
            when {
                c.isPaidOff -> Text("전액 완납 ✓", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossSuccess)
                hasDeposit && !c.depositPaid -> PayStat(
                    "계약금 ${manwon(c.depositAmount)}만 · 잔금 ${manwon(c.balanceAmount)}만 ", "미수")
                hasDeposit -> PayStat(
                    "계약금 ${manwon(c.depositAmount)}만 받음 · ", "잔금 ${manwon(c.balanceAmount)}만 남음")
                else -> PayStat("계약금 없음 · ", "전액 ${manwon(c.total)}만 미수")
            }
        }
        Spacer(Modifier.width(12.dp))
        // 버튼 / 완납 취소
        when {
            c.isPaidOff -> Text(
                "완납 취소", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onUndoPaid() }.padding(horizontal = 6.dp, vertical = 6.dp)
            )
            hasDeposit && !c.depositPaid -> PayAct("계약금 확인", onConfirmDeposit)
            hasDeposit -> PayAct("잔금 확인", onConfirmBalance)
            else -> PayAct("전액 확인", onConfirmBalance)
        }
    }
}

@Composable
private fun PayStat(plain: String, emphasis: String) {
    Row {
        Text(plain, fontSize = 12.5.sp, color = TossTextTertiary)
        Text(emphasis, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossError)
    }
}

@Composable
private fun PayAct(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TossBlue)
            .clickable { onClick() }
            .padding(horizontal = 17.dp, vertical = 11.dp)
    ) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun SettleDoneRow(item: SettleItem, index: Int) {
    val name = item.name ?: PhoneNumberFormatter.format(item.phone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tossCardShadow(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(item.name, index, small = true)
        Spacer(Modifier.width(11.dp))
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
        Spacer(Modifier.weight(1f))
        item.calc.let {
            Text("${manwon(it.total)}만원", fontSize = 13.sp, color = TossTextTertiary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 10.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(TossSuccess.copy(alpha = 0.12f))
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text("✓ 완납", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossSuccess)
        }
    }
}

@Composable
private fun EmptyMini(filter: SettleFilter) {
    val msg = when (filter) {
        SettleFilter.ALL -> "정산할 내역이 아직 없어요"
        SettleFilter.OUTSTANDING -> "못 받은 돈이 없어요 👍"
        SettleFilter.PAID_OFF -> "완납된 고객이 아직 없어요"
    }
    com.detailline.callfollowcrm.presentation.component.MascotEmptyState(speech = msg)
}

/* ─────────────── 아바타 (프로토 AV_TINTS) ─────────────── */

private val AV_TINTS = listOf(
    Color(0xFFE6EFFF) to Color(0xFF3182F6),
    Color(0xFFE7F8EE) to Color(0xFF16A765),
    Color(0xFFFDEAEF) to Color(0xFFF0436A),
    Color(0xFFF1ECFE) to Color(0xFF7C5CFC),
    Color(0xFFFEF3E0) to Color(0xFFE0920C),
)

@Composable
private fun Avatar(name: String?, index: Int, small: Boolean = false) {
    val sz = if (small) 38.dp else 44.dp
    if (name.isNullOrBlank()) {
        Box(
            Modifier.size(sz).clip(CircleShape).background(TossGrayBg),
            contentAlignment = Alignment.Center
        ) { Text("👤", fontSize = if (small) 14.sp else 16.sp) }
        return
    }
    val (bg, fg) = AV_TINTS[index % AV_TINTS.size]
    val initial = name.replace(Regex("[\\s()]"), "").take(1)
    Box(Modifier.size(sz).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(initial, fontSize = if (small) 14.sp else 16.sp, fontWeight = FontWeight.ExtraBold, color = fg)
    }
}

/* ─────────────── 목표 수정 다이얼로그 ─────────────── */

@Composable
private fun GoalEditSheet(
    initialManwon: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 프로토 inputSheet 모양(아래에서 올라오는 시트)이되, ModalBottomSheet(별도 윈도우)는 갤럭시에서
    //   키보드가 시트를 가려버림 → 액티비티 윈도우 인라인 오버레이로 구현해 imePadding 으로 키보드 위에 띄움.
    var text by remember { mutableStateOf(if (initialManwon > 0) initialManwon.toString() else "") }
    val n = text.filter { it.isDigit() }.toIntOrNull() ?: 0
    val cardClick = remember { MutableInteractionSource() }
    val scrimClick = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = scrimClick, indication = null) { onDismiss() }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color.White)
                .clickable(interactionSource = cardClick, indication = null) { /* 카드 탭은 닫힘 막기 */ }
                // 더하면 안 됨 — 키보드가 내비바를 덮으므로 둘 중 큰 쪽만(union). (2026-07-15 사장님)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 18.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 14.dp)
                    .width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TossDivider)
            )
            Text("이번 달 목표 매출", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(6.dp))
            Text("직접 정한 목표로 진행률을 보여드려요.", fontSize = 13.sp, color = TossTextSecondary)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text("목표 (만원)") },
                visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("취소", color = TossTextSecondary) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { if (n > 0) onConfirm(n) }, enabled = n > 0) {
                    Text("저장", color = if (n > 0) TossBlue else TossTextTertiary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/* ─────────────── 포맷 헬퍼 ─────────────── */

private fun manwon(won: Long): Int = (won / 10_000L).toInt()
private fun manwonText(won: Long): String = "%,d만원".format(manwon(won))
private fun Int.toCommaString(): String = "%,d".format(this)
// 목표 힌트: fillPct 와 받은돈으로 역산하기 어려우니 m2Top 문자열에서 숫자 추출.
private fun goalManwonHint(top: SettleTopState): Int =
    Regex("([0-9,]+)만원").find(top.m2Top)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 500
