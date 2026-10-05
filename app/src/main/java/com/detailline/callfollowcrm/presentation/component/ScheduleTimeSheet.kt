@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType

private val WEEKDAYS = listOf("일", "월", "화", "수", "목", "금", "토")

private fun cal(dayOffset: Int, hour: Int, minute: Int): Long {
    val c = java.util.Calendar.getInstance()
    c.add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
    c.set(java.util.Calendar.HOUR_OF_DAY, hour)
    c.set(java.util.Calendar.MINUTE, minute)
    c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun mdLabel(y: Int, mo: Int, d: Int): String {
    val c = java.util.Calendar.getInstance(); c.set(y, mo, d, 0, 0, 0)
    val w = WEEKDAYS[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]
    return "${mo + 1}/$d ($w)"
}

private fun hmLabel(h: Int, mi: Int): String {
    val ampm = if (h < 12) "오전" else "오후"
    val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
    return "$ampm $h12:${mi.toString().padStart(2, '0')}"
}

/**
 * 🕐 예약 시각 고르기 — 프로토 56gF4zzgC9UYVBihJcsN9Z STEP 2 를 1:1 로. (2026-10-05 사장님)
 *   빠른 칩(가로 흐름, 하나 선택) + 「직접 고르기」(날짜 칩·시간 칩) → [이 시간에 예약].
 *   **공용** — 보내기 창의 🕐 와 예약함 「시간 수정」이 같은 이것을 쓴다(§12 두 벌 금지).
 *   시트 틀은 공용 [AppSheet] — 키보드·네비바 여백·스크롤 내장(빠뜨릴 수 없음).
 *   이모지(📅·🕐)는 brand_guard 때문에 Icon 으로(프로토의 "아이콘 자리" 그대로).
 */
@Composable
fun ScheduleTimeSheet(
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }

    val quickLabels = listOf("오늘 저녁 6시", "내일 오전 9시", "내일 오후 1시", "모레 오전 9시")
    val quickTimes = listOf(cal(0, 18, 0), cal(1, 9, 0), cal(1, 13, 0), cal(2, 9, 0))

    var selectedChip by remember { mutableStateOf(-1) }
    var manualYmd by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var manualHm by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun manualMs(): Long? {
        val d = manualYmd ?: return null
        val t = manualHm ?: return null
        val c = java.util.Calendar.getInstance()
        c.set(d.first, d.second, d.third, t.first, t.second, 0); c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
    val selectedMs: Long? = if (selectedChip >= 0) quickTimes[selectedChip] else manualMs()
    val canReserve = selectedMs != null && selectedMs > now

    fun pickDate() {
        val base = manualYmd?.let { java.util.Calendar.getInstance().apply { set(it.first, it.second, it.third) } }
            ?: java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        android.app.DatePickerDialog(
            context,
            { _, y, mo, d -> manualYmd = Triple(y, mo, d); selectedChip = -1 },
            base.get(java.util.Calendar.YEAR), base.get(java.util.Calendar.MONTH), base.get(java.util.Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
    }
    fun pickTime() {
        val h = manualHm?.first ?: 9
        val mi = manualHm?.second ?: 0
        android.app.TimePickerDialog(
            context,
            { _, hh, mm -> manualHm = hh to mm; selectedChip = -1 },
            h, mi, false
        ).show()
    }

    AppSheet(onDismiss = onDismiss) {
        Text("언제 보낼까요?", style = AppType.title, color = AppTheme.colors.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(11.dp))

        // 빠른 칩 — 가로로 흐르고, 하나만 선택(파랑).
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quickLabels.forEachIndexed { i, label ->
                val enabled = quickTimes[i] > now
                val on = selectedChip == i
                Box(
                    Modifier.clip(AppShape.sm)
                        .background(if (on) AppTheme.colors.primary else AppTheme.colors.surfaceMuted)
                        .clickable(enabled = enabled) { selectedChip = i; manualYmd = null; manualHm = null }
                        .padding(horizontal = 13.dp, vertical = 10.dp)
                ) {
                    Text(
                        label,
                        style = AppType.body,
                        color = when { on -> AppTheme.colors.textOnPrimary; enabled -> AppTheme.colors.textSub; else -> AppTheme.colors.textHint },
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("직접 고르기", style = AppType.caption, color = AppTheme.colors.textHint, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val dateOn = manualYmd != null
            Box(
                Modifier.weight(1f).clip(AppShape.sm)
                    .background(if (dateOn) AppTheme.colors.primaryBg else AppTheme.colors.surfaceMuted)
                    .clickable { pickDate() }.padding(horizontal = 13.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = if (dateOn) AppTheme.colors.primary else AppTheme.colors.textSub, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        manualYmd?.let { mdLabel(it.first, it.second, it.third) } ?: "날짜",
                        style = AppType.body, color = if (dateOn) AppTheme.colors.primary else AppTheme.colors.textSub,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            val timeOn = manualHm != null
            Box(
                Modifier.weight(1f).clip(AppShape.sm)
                    .background(if (timeOn) AppTheme.colors.primaryBg else AppTheme.colors.surfaceMuted)
                    .clickable { pickTime() }.padding(horizontal = 13.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = if (timeOn) AppTheme.colors.primary else AppTheme.colors.textSub, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        manualHm?.let { hmLabel(it.first, it.second) } ?: "시간",
                        style = AppType.body, color = if (timeOn) AppTheme.colors.primary else AppTheme.colors.textSub,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(AppShape.md)
                .background(if (canReserve) AppTheme.colors.primary else AppTheme.colors.surfaceMuted)
                .clickable(enabled = canReserve) { selectedMs?.let { onPick(it) } }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "이 시간에 예약",
                style = AppType.body,
                color = if (canReserve) AppTheme.colors.textOnPrimary else AppTheme.colors.textHint,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
