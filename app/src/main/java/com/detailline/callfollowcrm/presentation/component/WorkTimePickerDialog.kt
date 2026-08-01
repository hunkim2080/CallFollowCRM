package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils

/** 시공 방문 시간 빠른 선택(자정부터 분). 채팅·고객정보에서 날짜 잡은 직후 띄움. (2026-06-23 사장님) */
private val WORK_TIME_CHIPS: List<Pair<String, Int>> = listOf(
    "오전 8시" to 8 * 60, "오전 9시" to 9 * 60, "오전 10시" to 10 * 60, "오전 11시" to 11 * 60,
    "낮 12시" to 12 * 60, "오후 1시" to 13 * 60, "오후 2시" to 14 * 60, "오후 3시" to 15 * 60,
    "오후 4시" to 16 * 60, "오후 5시" to 17 * 60, "오후 6시" to 18 * 60
)

/**
 * "시공 시간" 선택 다이얼로그.
 *   - 시공 기간(며칠): 칩 탭 = 위쪽에서 선택(강조). 기본 당일(1). 여러 날 현장이면 눌러서 바꿈.
 *   - 시공 시간: 칩 탭 = 선택된 기간과 함께 바로 저장+닫힘. "직접"=시:분 입력, "시간 미정"=null.
 *   날짜만 잡던 채팅·고객정보 흐름에 시간+기간을 더해 → 캘린더가 연속 막대로 그림. (2026-06-23 시간 / 2026-08-01 기간, 사장님)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkTimePickerDialog(
    initialMinutes: Int?,
    initialDays: Int = 1,
    onPick: (Int?, Int) -> Unit,   // (분, 며칠). 분 null = 시간 미정
    onDismiss: () -> Unit
) {
    var showClock by remember { mutableStateOf(false) }
    // 기간(며칠)은 날짜 범위선택(DateRangePicker)에서 이미 정해져 넘어옴 — 여기선 그대로 실어보내기만.
    val days = initialDays.coerceAtLeast(1)

    if (showClock) {
        val base = initialMinutes ?: 9 * 60
        val tpState = rememberTimePickerState(initialHour = base / 60, initialMinute = base % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showClock = false },
            confirmButton = {
                TextButton(onClick = { onPick(tpState.hour * 60 + tpState.minute, days) }) {
                    Text("확인", color = TossBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClock = false }) { Text("취소", color = TossTextSecondary) }
            },
            title = { Text("시간 직접 입력", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
            text = {
                Column {
                    com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                    TimeInput(state = tpState)
                }
            }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                // ── 시공 시간 ── (기간은 날짜 범위선택에서 이미 정해져 옴. 칩 탭 = 기간과 함께 저장+닫힘)
                Text("시공 시간", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "시간을 정하면 상담함·오늘 시공 카드가 시간순으로 쌓여요",
                    fontSize = 12.5.sp, color = TossTextTertiary
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WORK_TIME_CHIPS.forEach { (label, mins) ->
                        PickChip(label, initialMinutes == mins) { onPick(mins, days) }
                    }
                    PickChip("시간 미정", initialMinutes == null) { onPick(null, days) }
                    val isCustom = initialMinutes != null && WORK_TIME_CHIPS.none { it.second == initialMinutes }
                    PickChip(
                        if (isCustom) DateTimeUtils.formatWorkMinutes(initialMinutes!!) else "직접",
                        isCustom
                    ) { showClock = true }
                }
            }
        }
    }
}

@Composable
private fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp, fontWeight = FontWeight.Bold,
        color = if (selected) Color.White else TossTextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) TossBlue else TossGrayBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}
