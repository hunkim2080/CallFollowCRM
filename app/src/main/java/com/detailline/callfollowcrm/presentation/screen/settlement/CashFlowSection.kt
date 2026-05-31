package com.detailline.callfollowcrm.presentation.screen.settlement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.domain.settlement.CashDayAgg
import com.detailline.callfollowcrm.domain.settlement.CashFlowCalc
import com.detailline.callfollowcrm.domain.settlement.CashItem
import com.detailline.callfollowcrm.domain.settlement.CashRefType
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.MoneyFormatter
import java.util.Calendar

// 4색 (프로토 renderCashCal 기준)
private val CashIn = Color(0xFF1B64DA)    // 들어온 (확정 수입) 진파랑
private val CashInPlan = Color(0xFF86AEEE) // 들어올 (예정 수입) 연파랑
private val CashOut = Color(0xFFE0344F)    // 나간 (확정 지출) 빨강
private val CashOutPlan = Color(0xFFF0A0B0) // 나갈 (예정 지출) 연빨강

/**
 * 현금흐름 탭 — 4색 달력 + 월 순이익(확정/예상) + 선택일 상세 + 직접 기록 추가.
 * 정산 Phase 2 (2026-06-01).
 */
@Composable
fun CashFlowContent(
    viewModel: SettlementViewModel,
    onOpenCustomer: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.cashItems.collectAsState()
    val nowMs = remember { System.currentTimeMillis() }
    val todayStart = remember(nowMs) { DateTimeUtils.startOfDay(nowMs) }

    var monthAnchor by remember { mutableLongStateOf(monthAnchorOf(nowMs)) }
    var selectedDay by remember { mutableStateOf<Long?>(todayStart) }
    var showAddFor by remember { mutableStateOf<Long?>(null) }

    val byDay = remember(items) { CashFlowCalc.byDay(items) }
    val monthAgg = remember(items, monthAnchor) { monthAggregate(items, monthAnchor) }
    val dayItems = remember(selectedDay, byDay) {
        selectedDay?.let { byDay[it] }.orEmpty()
            .sortedWith(compareByDescending<CashItem> { it.isIncome }.thenByDescending { it.amount })
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TossGrayBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "month-summary") {
            MonthSummaryCard(monthAgg)
        }
        item(key = "calendar") {
            CashCalendar(
                monthAnchor = monthAnchor,
                byDay = byDay,
                todayStart = todayStart,
                selectedDay = selectedDay,
                onPrev = { monthAnchor = shiftMonthMs(monthAnchor, -1) },
                onNext = { monthAnchor = shiftMonthMs(monthAnchor, +1) },
                onSelect = { selectedDay = it }
            )
        }
        item(key = "legend") { CashLegend() }
        item(key = "day-header") {
            DayHeader(
                dayMs = selectedDay,
                isToday = selectedDay == todayStart,
                onAdd = { selectedDay?.let { showAddFor = it } }
            )
        }
        if (dayItems.isEmpty()) {
            item(key = "day-empty") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("이 날 기록된 돈이 없어요", color = TossTextTertiary,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(dayItems, key = { "${it.refType}-${it.refId}-${it.tag}" }) { ci ->
                CashItemRow(
                    item = ci,
                    onToggleDone = { viewModel.toggleManualDone(ci.refId, !ci.isDone) },
                    onDelete = { viewModel.deleteManualCash(ci.refId) },
                    onOpenCustomer = { onOpenCustomer(ci.refId) }
                )
            }
        }
        item(key = "tail") { Spacer(Modifier.height(40.dp)) }
    }

    showAddFor?.let { day ->
        AddCashDialog(
            dayMs = day,
            onAdd = { amount, isIncome, isDone, label ->
                viewModel.addManualCash(day, amount, isIncome, isDone, label)
                showAddFor = null
            },
            onDismiss = { showAddFor = null }
        )
    }
}

@Composable
private fun MonthSummaryCard(agg: CashDayAgg) {
    TossCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("확정 순이익 (실현)", style = MaterialTheme.typography.labelMedium, color = TossTextSecondary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        signedWon(agg.netDone),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = if (agg.netDone >= 0) CashIn else CashOut
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("예상 순이익 (추정)", style = MaterialTheme.typography.labelMedium, color = TossTextSecondary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        signedWon(agg.netPlanned),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = TossTextTertiary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "들어온 ${MoneyFormatter.won(agg.inDone)} · 나간 ${MoneyFormatter.won(agg.outDone)}",
                style = MaterialTheme.typography.bodySmall, color = TossTextTertiary
            )
        }
    }
}

@Composable
private fun CashCalendar(
    monthAnchor: Long,
    byDay: Map<Long, List<CashItem>>,
    todayStart: Long,
    selectedDay: Long?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (Long) -> Unit
) {
    TossCard(contentPadding = PaddingValues(8.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "이전 달", tint = TossTextSecondary) }
                Text(
                    DateTimeUtils.formatMonthHeader(monthAnchor),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = TossTextPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "다음 달", tint = TossTextSecondary) }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                    Text(
                        d, modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (i) { 0 -> TossError; 6 -> TossBlue; else -> TossTextSecondary },
                        textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold
                    )
                }
            }
            val cells = remember(monthAnchor, byDay) { buildCashCells(monthAnchor, byDay, todayStart) }
            repeat(6) { w ->
                Row(Modifier.fillMaxWidth()) {
                    cells.subList(w * 7, w * 7 + 7).forEach { cell ->
                        CashDayCell(
                            cell = cell,
                            isSelected = selectedDay == cell.dayStartMs,
                            onClick = { onSelect(cell.dayStartMs) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CashDayCell(cell: CashCell, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = when {
        isSelected -> TossBlueSoft
        cell.isToday -> TossGrayBg
        else -> Color.Transparent
    }
    val fg = when {
        !cell.isCurrentMonth -> TossTextTertiary
        cell.isPast -> TossTextTertiary   // 지난날 회색 (프로토 cc-past)
        cell.dayOfWeek == Calendar.SUNDAY -> TossError
        cell.dayOfWeek == Calendar.SATURDAY -> TossBlue
        else -> TossTextPrimary
    }
    Box(
        modifier = modifier
            .aspectRatio(0.92f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                cell.dayOfMonth.toString(),
                color = fg, fontSize = 13.sp,
                fontWeight = if (cell.isToday || isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            // 수입 바 (있으면) — 확정 우선 진파랑, 예정만 있으면 연파랑
            CashBar(
                show = cell.agg.inDone > 0 || cell.agg.inPlan > 0,
                color = if (cell.agg.inDone > 0) CashIn else CashInPlan
            )
            Spacer(Modifier.height(1.dp))
            CashBar(
                show = cell.agg.outDone > 0 || cell.agg.outPlan > 0,
                color = if (cell.agg.outDone > 0) CashOut else CashOutPlan
            )
        }
    }
}

@Composable
private fun CashBar(show: Boolean, color: Color) {
    if (!show) { Spacer(Modifier.height(4.dp)); return }
    Box(
        Modifier
            .width(16.dp).height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

@Composable
private fun CashLegend() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LegendDot(CashIn, "들어온")
        LegendDot(CashInPlan, "들어올")
        LegendDot(CashOut, "나간")
        LegendDot(CashOutPlan, "나갈")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TossTextSecondary)
    }
}

@Composable
private fun DayHeader(dayMs: Long?, isToday: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (dayMs?.let { DateTimeUtils.formatKoreanDate(it) } ?: "날짜 선택") + (if (isToday) " · 오늘" else ""),
            style = MaterialTheme.typography.titleMedium, color = TossTextPrimary,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(TossBlue)
                .clickable { onAdd() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("직접 기록", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CashItemRow(
    item: CashItem,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
    onOpenCustomer: () -> Unit
) {
    val isManual = item.refType == CashRefType.MANUAL
    val amtColor = when {
        item.isIncome && item.isDone -> CashIn
        item.isIncome -> CashInPlan
        item.isDone -> CashOut
        else -> CashOutPlan
    }
    TossCard(
        contentPadding = PaddingValues(14.dp),
        onClick = if (isManual) null else onOpenCustomer
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall,
                        color = TossTextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(TossGrayBg).padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(item.tag, style = MaterialTheme.typography.labelSmall, color = TossTextSecondary)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    (if (item.isIncome) "+" else "−") + MoneyFormatter.won(item.amount) +
                        (if (item.isDone) "" else " (예정)"),
                    style = MaterialTheme.typography.titleSmall, color = amtColor, fontWeight = FontWeight.Bold
                )
            }
            if (isManual) {
                // 예정↔완료 토글 + 삭제
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (item.isDone) TossBlueSoft else TossGrayBg)
                            .clickable { onToggleDone() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(if (item.isDone) "완료" else "예정",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.isDone) TossBlue else TossTextSecondary,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("삭제", style = MaterialTheme.typography.labelSmall, color = TossTextTertiary,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onDelete() }
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun AddCashDialog(
    dayMs: Long,
    onAdd: (amount: Long, isIncome: Boolean, isDone: Boolean, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(true) }
    var label by remember { mutableStateOf("") }
    val amount = amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("직접 기록 · ${DateTimeUtils.formatKoreanDate(dayMs)}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // 수입/지출
                TwoToggle(
                    leftLabel = "들어온 돈", rightLabel = "나간 돈",
                    leftSelected = isIncome, onLeft = { isIncome = true }, onRight = { isIncome = false },
                    leftColor = CashIn, rightColor = CashOut
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("금액 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("메모 (예: 자재비)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                // 완료/예정
                TwoToggle(
                    leftLabel = "이미 오감", rightLabel = "예정",
                    leftSelected = isDone, onLeft = { isDone = true }, onRight = { isDone = false },
                    leftColor = TossBlue, rightColor = TossTextTertiary
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (amount > 0) onAdd(amount, isIncome, isDone, label) },
                enabled = amount > 0
            ) { Text("추가", color = if (amount > 0) TossBlue else TossTextTertiary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = TossTextSecondary) } }
    )
}

@Composable
private fun TwoToggle(
    leftLabel: String, rightLabel: String,
    leftSelected: Boolean,
    onLeft: () -> Unit, onRight: () -> Unit,
    leftColor: Color, rightColor: Color
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleHalf(leftLabel, leftSelected, leftColor, onLeft, Modifier.weight(1f))
        ToggleHalf(rightLabel, !leftSelected, rightColor, onRight, Modifier.weight(1f))
    }
}

@Composable
private fun ToggleHalf(label: String, selected: Boolean, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color.copy(alpha = 0.12f) else TossGrayBg)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = if (selected) color else TossTextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

private fun signedWon(v: Long): String = (if (v >= 0) "+" else "−") + MoneyFormatter.won(kotlin.math.abs(v))

// ── 달력 데이터 ──────────────────────────────────────────────
private data class CashCell(
    val dayStartMs: Long,
    val dayOfMonth: Int,
    val dayOfWeek: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isPast: Boolean,
    val agg: CashDayAgg
)

private fun monthAnchorOf(anyMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = anyMs
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun shiftMonthMs(anchorMs: Long, delta: Int): Long = Calendar.getInstance().apply {
    timeInMillis = anchorMs
    add(Calendar.MONTH, delta)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private fun buildCashCells(monthAnchor: Long, byDay: Map<Long, List<CashItem>>, todayStart: Long): List<CashCell> {
    val cal = Calendar.getInstance().apply { timeInMillis = monthAnchor }
    val targetMonth = cal.get(Calendar.MONTH)
    val firstDow = cal.get(Calendar.DAY_OF_WEEK)
    cal.add(Calendar.DAY_OF_MONTH, -(firstDow - 1))
    val cells = ArrayList<CashCell>(42)
    repeat(42) {
        val dayStart = DateTimeUtils.startOfDay(cal.timeInMillis)
        val agg = CashFlowCalc.aggOf(byDay[dayStart].orEmpty())
        cells += CashCell(
            dayStartMs = dayStart,
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
            isCurrentMonth = cal.get(Calendar.MONTH) == targetMonth,
            isToday = dayStart == todayStart,
            isPast = dayStart < todayStart,
            agg = agg
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return cells
}

private fun monthAggregate(items: List<CashItem>, monthAnchor: Long): CashDayAgg {
    val next = shiftMonthMs(monthAnchor, +1)
    return CashFlowCalc.aggOf(items.filter { it.dayStartMs in monthAnchor until next })
}
