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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
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
 * 현금흐름 카드 — 4색 달력 + 월 순이익(확정/예상) + 선택일 상세 + 직접 기록 추가.
 * 정산 Phase 2 (2026-06-01). 프로토 cashcal-slot 위치 = settle-top 아래, 미수 목록 위.
 * 자체 remember 상태(달 이동/선택일)를 들고 있어 정산 단일 스크롤에 item 하나로 끼워 넣는다.
 */
@Composable
fun CashFlowCard(
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 프로토 cc 카드 — 하나의 카드 안에: 달력(금액) → 점선 직접기록 → 범례 2×2 → 순이익 2줄 → 단위안내.
        CashCalendar(
            monthAnchor = monthAnchor,
            byDay = byDay,
            monthAgg = monthAgg,
            todayStart = todayStart,
            selectedDay = selectedDay,
            onPrev = { monthAnchor = shiftMonthMs(monthAnchor, -1) },
            onNext = { monthAnchor = shiftMonthMs(monthAnchor, +1) },
            onSelect = { selectedDay = it },
            onAddRecord = { showAddFor = selectedDay ?: todayStart }
        )
        // 선택한 날 상세 (카드 밖)
        Text(
            (selectedDay?.let { DateTimeUtils.formatKoreanDate(it) } ?: "날짜 선택") +
                (if (selectedDay == todayStart) " · 오늘" else ""),
            style = MaterialTheme.typography.titleMedium, color = TossTextPrimary,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, start = 2.dp)
        )
        if (dayItems.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("이 날 기록된 돈이 없어요", color = TossTextTertiary,
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            dayItems.forEach { ci ->
                CashItemRow(
                    item = ci,
                    onToggleDone = { viewModel.toggleManualDone(ci.refId, !ci.isDone) },
                    onDelete = { viewModel.deleteManualCash(ci.refId) },
                    onOpenCustomer = { onOpenCustomer(ci.refId) }
                )
            }
        }
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
private fun CashCalendar(
    monthAnchor: Long,
    byDay: Map<Long, List<CashItem>>,
    monthAgg: CashDayAgg,
    todayStart: Long,
    selectedDay: Long?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (Long) -> Unit,
    onAddRecord: () -> Unit
) {
    TossCard(contentPadding = PaddingValues(8.dp)) {
        Column {
            // 프로토 cc-head — "현금 흐름" + "날짜별 들어오고 나간 돈"
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text("현금 흐름", style = MaterialTheme.typography.titleSmall, color = TossTextPrimary,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("날짜별 들어오고 나간 돈", style = MaterialTheme.typography.labelSmall, color = TossTextTertiary)
            }
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
            // 프로토 cc-add — 점선 직접기록 버튼
            Spacer(Modifier.height(10.dp))
            DashedRecordButton(onClick = onAddRecord)
            // 프로토 cc-legend — 2×2 그리드
            Spacer(Modifier.height(12.dp))
            CashLegend()
            // 프로토 cc-foot — 순이익 2줄 (만원)
            Spacer(Modifier.height(14.dp))
            MonthSummaryRows(monthAgg)
            // 프로토 cc-unit
            Spacer(Modifier.height(10.dp))
            Text(
                "단위: 만원 · 날짜 탭=내역 보기 · 꾹 누르면 그 날 기록 추가",
                style = MaterialTheme.typography.labelSmall, color = TossTextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)
            )
        }
    }
}

/** 프로토 cc-add — 점선 테두리 "자재·장비 등 직접 기록" 버튼. */
@Composable
private fun DashedRecordButton(onClick: () -> Unit) {
    val border = TossDivider
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .drawBehind {
                drawRoundRect(
                    color = border,
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 8f), 0f)
                    ),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
            }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, null, tint = TossTextSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("자재·장비 등 들어온·나간 돈 직접 기록", color = TossTextSecondary,
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 프로토 cc-foot — 순이익 2줄(라벨+부제 왼쪽, 값 만원 오른쪽). */
@Composable
private fun MonthSummaryRows(agg: CashDayAgg) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryRow(
            "확정 순이익 (실현)", "이미 받은 돈 − 이미 낸 돈",
            signedManwon(agg.netDone), if (agg.netDone >= 0) CashIn else CashOut
        )
        SummaryRow(
            "예상 순이익 (추정)", "들어올·나갈 예정까지 반영",
            signedManwon(agg.netPlanned), TossTextSecondary
        )
    }
}

@Composable
private fun SummaryRow(title: String, sub: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = TossTextSecondary, fontWeight = FontWeight.SemiBold)
            Text(sub, fontSize = 11.sp, color = TossTextTertiary)
        }
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
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
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() }
            .heightIn(min = 44.dp)
            .padding(top = 4.dp, bottom = 3.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                cell.dayOfMonth.toString(),
                color = fg, fontSize = 13.sp,
                fontWeight = if (cell.isToday || isSelected) FontWeight.Bold else FontWeight.Medium
            )
            // 프로토 cc-in/inp/out/outp — 만원 금액 (확정/예정, 색)
            val a = cell.agg
            if (a.inDone > 0) CashAmt("+${man(a.inDone)}", CashIn)
            if (a.inPlan > 0) CashAmt("+${man(a.inPlan)}", CashInPlan)
            if (a.outDone > 0) CashAmt("−${man(a.outDone)}", CashOut)
            if (a.outPlan > 0) CashAmt("−${man(a.outPlan)}", CashOutPlan)
        }
    }
}

@Composable
private fun CashAmt(text: String, color: Color) {
    Text(text, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
        maxLines = 1, modifier = Modifier.padding(top = 1.dp))
}

/** 원 → 만원(반올림) 문자열 (달력 셀·순이익 단위 표시용). */
private fun man(won: Long): String = Math.round(won / 10000.0).toString()

@Composable
private fun CashLegend() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            LegendDot(CashIn, "들어온(받음)", Modifier.weight(1f))
            LegendDot(CashInPlan, "들어올(예정)", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            LegendDot(CashOut, "나간(냄)", Modifier.weight(1f))
            LegendDot(CashOutPlan, "나갈(예정)", Modifier.weight(1f))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TossTextSecondary)
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
        onClick = if (item.refType == CashRefType.CUSTOMER) onOpenCustomer else null
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

    val accent = if (isIncome) CashIn else CashOut

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("직접 기록", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                Text(DateTimeUtils.formatKoreanDate(dayMs), fontSize = 12.sp, color = TossTextTertiary)
            }
        },
        text = {
            Column {
                com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                // 수입/지출
                TwoToggle(
                    leftLabel = "들어온 돈", rightLabel = "나간 돈",
                    leftSelected = isIncome, onLeft = { isIncome = true }, onRight = { isIncome = false },
                    leftColor = CashIn, rightColor = CashOut
                )
                Spacer(Modifier.height(16.dp))
                // 금액 — 화면의 주인공: 큰 글씨 + 오른쪽 정렬 + "원" + 콤마 미리보기.
                CashFieldLabel("금액")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() }.take(12) },
                    placeholder = { Text("0", fontSize = 22.sp, color = TossTextTertiary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                    textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End, color = accent),
                    suffix = { Text("원", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (amount > 0) {
                    Spacer(Modifier.height(5.dp))
                    Text("= %,d원".format(amount), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = accent,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                }
                Spacer(Modifier.height(14.dp))
                CashFieldLabel("메모")
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("예: 자재비", color = TossTextTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                // 완료/예정
                CashFieldLabel("상태")
                TwoToggle(
                    leftLabel = "이미 오감", rightLabel = "예정",
                    leftSelected = isDone, onLeft = { isDone = true }, onRight = { isDone = false },
                    leftColor = TossBlue, rightColor = TossTextTertiary
                )
                Spacer(Modifier.height(20.dp))
                // 취소 / 추가 — 큰 풀폭 버튼. 추가는 수입=파랑·지출=빨강으로 물들임.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                            .clickable { onDismiss() }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("취소", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (amount > 0) accent else TossDivider)
                            .clickable(enabled = amount > 0) { onAdd(amount, isIncome, isDone, label) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("추가", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

/** 정산 직접기록 입력칸 라벨 — 12sp 굵게 t3, 살짝 들여쓰기. */
@Composable
private fun CashFieldLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
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

private fun signedManwon(v: Long): String = (if (v >= 0) "+" else "−") + man(kotlin.math.abs(v)) + "만원"

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
