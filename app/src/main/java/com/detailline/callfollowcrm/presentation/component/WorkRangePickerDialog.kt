package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.detailline.callfollowcrm.domain.schedule.RangePick
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.presentation.util.bottomBarClearance
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * **공용 날짜 선택 창** — 흰 카드 + [WorkRangeCalendar] + 안내 한 줄 + [취소]/[확인] (+ 선택: 비우기).
 *
 * 시공 기간 · A/S 예약 · 시공일 등록 · 간단 일정 수정이 전부 이걸 쓴다
 *   (전엔 Material3 DateRangePicker 창이 화면마다 따로였다 — 2026-10-24 통일).
 * 저장 로직은 화면마다 다르므로 [onConfirm] 으로 넘긴다(시작 ms · 며칠). 여긴 **고르기**만.
 */
@Composable
fun WorkRangePickerDialog(
    title: String,
    initialStart: Long?,
    initialDays: Int,
    onConfirm: (startMs: Long, days: Int) -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "저장",
    accent: Color = TossBlue,
    rangeBg: Color = AppTheme.colors.primaryBg,
    titleColor: Color = TossTextSecondary,
    clearText: String? = null,
    onClear: () -> Unit = {}
) {
    var startMs by remember {
        mutableStateOf(initialStart?.let { DateTimeUtils.startOfDay(it) })
    }
    var endMs by remember {
        mutableStateOf(
            initialStart?.takeIf { initialDays > 1 }
                ?.let { DateTimeUtils.startOfDay(it) + (initialDays - 1) * DateTimeUtils.DAY_MS }
        )
    }
    val anchor = remember { initialStart ?: System.currentTimeMillis() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                // Dialog 는 별도 창이라 navigationBarsPadding 이 0 → 바닥이 내비바에 가림. 헬퍼로 띄움.
                .bottomBarClearance(extra = 20.dp)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 12.dp
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(title, style = AppType.body, color = titleColor, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
                    WorkRangeCalendar(
                        monthAnchor = anchor,
                        startMs = startMs,
                        endMs = endMs,
                        accent = accent,
                        rangeBg = rangeBg,
                        onPick = { day ->
                            val (s, e) = RangePick.tap(startMs, endMs, day)
                            startMs = s; endMs = e
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        workRangeHint(startMs, endMs),
                        style = AppType.caption, color = TossTextTertiary,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TossSecondaryButton(text = "취소", onClick = onDismiss, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(9.dp))
                        TossPrimaryButton(
                            text = confirmText,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                startMs?.let { onConfirm(it, RangePick.daysOf(it, endMs)) }
                                onDismiss()
                            }
                        )
                    }
                    if (clearText != null) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = { onClear(); onDismiss() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(clearText, style = AppType.body, color = TossError, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
