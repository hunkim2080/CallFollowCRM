package com.detailline.callfollowcrm.presentation.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import java.util.Calendar

/**
 * 채팅 중 "내 일정 확인" 바텀시트 (2026-06-01) — 프로토 openMySchedule 벤치마킹.
 *   고객과 대화하면서 빈 날/시공 있는 날을 미니 달력으로 즉시 확인 → 약속 잡기·가까운 현장 묶기.
 *   읽기 전용 (등록은 일정 화면 FAB). 여러 날 시공(scheduledWorkDays)도 기간 내 모든 날에 점.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScheduleSheet(
    jobs: List<CustomerEntity>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nowMs = remember { System.currentTimeMillis() }
    val todayStart = remember(nowMs) { DateTimeUtils.startOfDay(nowMs) }
    var viewedMonthAnchor by remember { mutableLongStateOf(miniMonthAnchor(nowMs)) }
    var selectedDayMs by remember { mutableStateOf<Long?>(todayStart) }

    val cells = remember(viewedMonthAnchor, jobs) {
        buildMiniCells(viewedMonthAnchor, jobs, todayStart)
    }
    val jobsForSelected = remember(selectedDayMs, jobs) {
        val day = selectedDayMs ?: return@remember emptyList<CustomerEntity>()
        jobs.filter { miniJobCoversDay(it, day) }
            .sortedBy { it.scheduledWorkMinutes ?: Int.MAX_VALUE }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("내 시공 일정", style = MaterialTheme.typography.titleLarge,
                color = TossTextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                "점 있는 날 = 시공. 빈 날을 골라 약속 잡기 좋아요.",
                style = MaterialTheme.typography.bodySmall, color = TossTextTertiary
            )
            Spacer(Modifier.height(12.dp))

            // 월 헤더
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { viewedMonthAnchor = miniShiftMonth(viewedMonthAnchor, -1) }) {
                    Icon(Icons.Default.ChevronLeft, "이전 달", tint = TossTextSecondary)
                }
                Text(
                    DateTimeUtils.formatMonthHeader(viewedMonthAnchor),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = TossTextPrimary, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { viewedMonthAnchor = miniShiftMonth(viewedMonthAnchor, +1) }) {
                    Icon(Icons.Default.ChevronRight, "다음 달", tint = TossTextSecondary)
                }
            }

            // 요일 헤더
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, label ->
                    Text(
                        label, modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (i) { 0 -> TossError; 6 -> TossBlue; else -> TossTextSecondary },
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                    )
                }
            }

            // 6주 그리드
            repeat(6) { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    cells.subList(week * 7, week * 7 + 7).forEach { cell ->
                        MiniDay(
                            cell = cell,
                            isSelected = selectedDayMs == cell.dayStartMs,
                            onClick = { selectedDayMs = cell.dayStartMs },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // 선택된 날 상세
            val day = selectedDayMs
            if (day != null) {
                Text(
                    DateTimeUtils.formatKoreanDate(day) + (if (day == todayStart) " · 오늘" else ""),
                    style = MaterialTheme.typography.titleSmall,
                    color = TossTextPrimary, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                if (jobsForSelected.isEmpty()) {
                    Text(
                        if (day < todayStart) "이 날은 시공이 없었어요" else "이 날 비어있어요 — 약속 잡기 좋아요 👍",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (day < todayStart) TossTextTertiary else TossSuccess,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    jobsForSelected.forEach { c -> MiniJobRow(c) }
                }
            }
        }
    }
}

@Composable
private fun MiniDay(
    cell: MiniCell,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = when {
        isSelected -> TossBlue
        cell.isToday -> TossBlueSoft
        else -> Color.Transparent
    }
    val fg = when {
        isSelected -> Color.White
        !cell.inMonth -> TossTextTertiary
        cell.dow == Calendar.SUNDAY -> TossError
        cell.dow == Calendar.SATURDAY -> TossBlue
        else -> TossTextPrimary
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(cell.dom.toString(), color = fg, fontSize = 13.sp,
                fontWeight = if (cell.isToday || isSelected) FontWeight.Bold else FontWeight.Medium)
            if (cell.busy) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.size(4.dp).clip(CircleShape)
                    .background(if (isSelected) Color.White else TossSuccess))
            }
        }
    }
}

@Composable
private fun MiniJobRow(c: CustomerEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.padding(top = 4.dp).size(6.dp).clip(CircleShape).background(TossBlue)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TossTextPrimary, fontWeight = FontWeight.SemiBold
                )
                c.scheduledWorkMinutes?.let { mins ->
                    Spacer(Modifier.width(8.dp))
                    Text("🕐 " + DateTimeUtils.formatWorkMinutes(mins),
                        style = MaterialTheme.typography.bodySmall, color = TossBlue,
                        fontWeight = FontWeight.Medium)
                }
            }
            val addr = c.address?.takeIf { it.isNotBlank() }
            if (addr != null) {
                Spacer(Modifier.height(2.dp))
                Text("📍 $addr", style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary, maxLines = 1)
            }
        }
    }
}

// ───────────────────────── 미니 달력 데이터/빌더 (파일 한정) ─────────────────────────

private data class MiniCell(
    val dayStartMs: Long,
    val dom: Int,
    val dow: Int,
    val inMonth: Boolean,
    val isToday: Boolean,
    val busy: Boolean
)

/** 여러 날 시공(scheduledWorkDays) 고려해 이 시공이 dayStart 를 포함하는가. */
private fun miniJobCoversDay(c: CustomerEntity, dayStart: Long): Boolean {
    val start = c.scheduledWorkDate ?: return false
    val s = DateTimeUtils.startOfDay(start)
    val days = c.scheduledWorkDays.coerceAtLeast(1)
    val end = s + (days - 1) * DateTimeUtils.DAY_MS
    return dayStart in s..end
}

private fun miniMonthAnchor(anyMs: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = anyMs
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun miniShiftMonth(anchorMs: Long, delta: Int): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = anchorMs
        add(Calendar.MONTH, delta)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return cal.timeInMillis
}

private fun buildMiniCells(monthAnchor: Long, jobs: List<CustomerEntity>, todayStart: Long): List<MiniCell> {
    val cal = Calendar.getInstance().apply { timeInMillis = monthAnchor }
    val targetMonth = cal.get(Calendar.MONTH)
    val firstDow = cal.get(Calendar.DAY_OF_WEEK)
    cal.add(Calendar.DAY_OF_MONTH, -(firstDow - 1))
    val cells = ArrayList<MiniCell>(42)
    repeat(42) {
        val dayStart = DateTimeUtils.startOfDay(cal.timeInMillis)
        cells += MiniCell(
            dayStartMs = dayStart,
            dom = cal.get(Calendar.DAY_OF_MONTH),
            dow = cal.get(Calendar.DAY_OF_WEEK),
            inMonth = cal.get(Calendar.MONTH) == targetMonth,
            isToday = dayStart == todayStart,
            busy = jobs.any { miniJobCoversDay(it, dayStart) }
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return cells
}
