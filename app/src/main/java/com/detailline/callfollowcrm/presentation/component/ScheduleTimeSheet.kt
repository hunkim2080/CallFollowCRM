package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType

/**
 * 🕐 예약 시각 고르기 — 빠른 칩 4개 + 날짜·시간 직접. 고르면 onPick(ms). (2026-10-05 사장님, 프로토 56gF4zzgC9UYVBihJcsN9Z)
 *   **공용** — 보내기 창의 🕐 와 예약함의 「시간 수정」이 같은 이것을 쓴다(§12 두 벌 금지).
 *   지난 시각 칩은 흐리게(못 고름). 「직접 고르기」는 안드로이드 기본 날짜·시간 창 — 지난 시각은 막는다.
 */
@Composable
fun ScheduleTimeSheet(
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }

    fun at(dayOffset: Int, hour: Int, minute: Int): Long {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
        c.set(java.util.Calendar.HOUR_OF_DAY, hour)
        c.set(java.util.Calendar.MINUTE, minute)
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    val quickLabels = listOf("오늘 저녁 6시", "내일 오전 9시", "내일 오후 1시", "모레 오전 9시")
    val quickTimes = listOf(at(0, 18, 0), at(1, 9, 0), at(1, 13, 0), at(2, 9, 0))

    fun openManual() {
        val c = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(
            context,
            { _, y, mo, d ->
                val t = java.util.Calendar.getInstance()
                android.app.TimePickerDialog(
                    context,
                    { _, h, mi ->
                        val picked = java.util.Calendar.getInstance()
                        picked.set(y, mo, d, h, mi, 0); picked.set(java.util.Calendar.MILLISECOND, 0)
                        if (picked.timeInMillis <= System.currentTimeMillis()) {
                            android.widget.Toast.makeText(context, "지난 시각은 안 돼요", android.widget.Toast.LENGTH_SHORT).show()
                        } else onPick(picked.timeInMillis)
                    },
                    t.get(java.util.Calendar.HOUR_OF_DAY), t.get(java.util.Calendar.MINUTE), false
                ).show()
            },
            c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
    }

    Box(
        Modifier.fillMaxSize().background(AppTheme.colors.scrim).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().clip(AppShape.lg).background(AppTheme.colors.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp)
        ) {
            Text("언제 보낼까요?", style = AppType.headline, color = AppTheme.colors.text)
            Spacer(Modifier.height(4.dp))
            Text("고른 시각에 자동으로 보내드려요", style = AppType.caption, color = AppTheme.colors.textSub)
            Spacer(Modifier.height(16.dp))
            quickLabels.forEachIndexed { i, label ->
                val ms = quickTimes[i]
                val enabled = ms > now
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(AppShape.md)
                        .background(if (enabled) AppTheme.colors.primaryBg else AppTheme.colors.surfaceMuted)
                        .clickable(enabled = enabled) { onPick(ms) }
                        .padding(vertical = 14.dp, horizontal = 16.dp)
                ) {
                    Text(
                        label,
                        style = AppType.body,
                        color = if (enabled) AppTheme.colors.primaryText else AppTheme.colors.textHint,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
                    .clickable { openManual() }.padding(vertical = 14.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("날짜·시간 직접 고르기", style = AppType.body, color = AppTheme.colors.text, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("닫기", style = AppType.body, color = AppTheme.colors.textSub)
            }
        }
    }
}
