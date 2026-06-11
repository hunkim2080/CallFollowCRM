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
import com.detailline.callfollowcrm.presentation.util.bottomBarClearance
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
    onDismiss: () -> Unit,
    // 선택한 날짜 카드를 탭하면 그 날짜 문자열("2026년 6월 22일 (월)")을 대화창에 넣는다.
    //   (사장님 플로우: "시공 언제 되냐" 문의 → 내 일정에서 빈 날 찾아 그 날짜를 채팅으로 제안)
    onPickDate: (String) -> Unit = {}
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
        containerColor = Color.White,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .bottomBarClearance(extra = 24.dp)  // 하단 내비바 가림 방지(M3 시트 인셋 0 버그). 2026-06-11
        ) {
            Text("내 시공 일정", style = MaterialTheme.typography.titleLarge,
                color = TossTextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                "점 있는 날을 누르면 바로 아래에 그 날 시공·주소가 떠요. 가까운 현장이면 같이 묶기 좋아요.",
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

            // 프로토 범례 — ● 시공 있는 날 · 눌러서 주소 확인
            Spacer(Modifier.height(11.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(TossBlue))
                Spacer(Modifier.width(7.dp))
                Text("시공 있는 날 · 눌러서 주소 확인", fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold, color = TossTextTertiary)
            }

            Spacer(Modifier.height(14.dp))
            // 선택된 날 상세 — 깔끔한 카드 (2026-06-04 시각 정리). 기능/문구는 그대로.
            val day = selectedDayMs
            if (day != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF5F7FA))
                        .clickable { onPickDate(DateTimeUtils.formatKoreanDate(day)) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        DateTimeUtils.formatKoreanDate(day) +
                            (if (day == todayStart) " · 오늘" else "") +
                            (if (jobsForSelected.isNotEmpty()) " · 시공 ${jobsForSelected.size}곳" else ""),
                        style = MaterialTheme.typography.titleSmall,
                        color = TossTextPrimary, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(if (jobsForSelected.isEmpty()) 7.dp else 12.dp))
                    if (jobsForSelected.isEmpty()) {
                        Text(
                            if (day < todayStart) "이 날은 시공이 없었어요" else "이 날 비어있어요 — 약속 잡기 좋아요 👍",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (day < todayStart) TossTextTertiary else TossSuccess,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        jobsForSelected.forEachIndexed { i, c ->
                            if (i > 0) Spacer(Modifier.height(12.dp))
                            MiniJobRow(c)
                        }
                    }
                    // 탭하면 이 날짜가 대화창에 들어간다는 안내.
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "👆 눌러서 이 날짜를 대화창에 넣기",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossBlue, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
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
                    .background(if (isSelected) Color.White else TossBlue))
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
            // 주소가 주인공 — 가까운 현장끼리 묶어보려고 보는 화면이라 주소를 크게 위로. (2026-06-04 사장님)
            val addr = com.detailline.callfollowcrm.util.AddressExtractor.tidyAddress(c.address).takeIf { it.isNotBlank() }
            Text(
                if (addr != null) "📍 $addr" else "📍 주소 미입력",
                style = MaterialTheme.typography.bodyLarge,
                color = if (addr != null) TossTextPrimary else TossTextTertiary,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp,
                maxLines = 2
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber),
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary
                )
                c.scheduledWorkMinutes?.let { mins ->
                    Spacer(Modifier.width(8.dp))
                    Text("🕐 " + DateTimeUtils.formatWorkMinutes(mins),
                        style = MaterialTheme.typography.bodySmall, color = TossBlue,
                        fontWeight = FontWeight.Medium)
                }
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
