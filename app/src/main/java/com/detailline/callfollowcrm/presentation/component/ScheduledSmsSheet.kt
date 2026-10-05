package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.data.local.entity.ScheduledSmsEntity
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.util.keyboardOrNavPadding

/** 예약 시각을 사람 말로 — "10월 6일 오전 9시 30분". */
private fun scheduledWhen(ms: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return android.text.format.DateFormat.format("M월 d일 a h시 m분", c).toString()
}

/**
 * 🕐 예약함 — 예약한 문자 모아보기·시간 수정·취소. (2026-10-05 사장님, 프로토 56gF4zzgC9UYVBihJcsN9Z)
 *   상담함 위 「예약함」에서 연다. 시간 수정은 보내기 창과 같은 공용 [ScheduleTimeSheet].
 */
@Composable
fun ScheduledSmsSheet(
    items: List<ScheduledSmsEntity>,
    onDismiss: () -> Unit,
    onCancel: (Long) -> Unit,
    onReschedule: (Long, Long) -> Unit
) {
    var editId by remember { mutableStateOf<Long?>(null) }
    var confirmCancelId by remember { mutableStateOf<Long?>(null) }

    Box(
        Modifier.fillMaxSize().background(AppTheme.colors.scrim).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().clip(AppShape.lg).background(AppTheme.colors.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                // 시스템 네비바에 하단이 가리지 않게 — 공용 '표준' 여백. (2026-10-05 사장님)
                .keyboardOrNavPadding()
                .padding(20.dp)
        ) {
            Text("예약한 문자", style = AppType.headline, color = AppTheme.colors.text)
            Spacer(Modifier.height(4.dp))
            Text("예약한 시각에 자동으로 보내드려요", style = AppType.caption, color = AppTheme.colors.textSub)
            Spacer(Modifier.height(16.dp))

            if (items.isEmpty()) {
                Text("예약한 문자가 없어요", style = AppType.body, color = AppTheme.colors.textSub)
            } else {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items.forEach { row ->
                        val photoN = row.photoUriList().size
                        Column(
                            Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.surfaceMuted).padding(14.dp)
                        ) {
                            Text(scheduledWhen(row.sendAtMs), style = AppType.body, color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(row.recipientName, style = AppType.caption, color = AppTheme.colors.textSub)
                            if (row.body.isNotBlank()) {
                                Spacer(Modifier.height(5.dp))
                                Text(row.body, style = AppType.body, color = AppTheme.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (photoN > 0) {
                                Spacer(Modifier.height(5.dp))
                                Text("사진 ${photoN}장", style = AppType.caption, color = AppTheme.colors.textSub)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row {
                                Box(
                                    Modifier.clip(AppShape.sm).background(AppTheme.colors.surface)
                                        .clickable { editId = row.id }.padding(vertical = 8.dp, horizontal = 14.dp)
                                ) { Text("시간 수정", style = AppType.caption, color = AppTheme.colors.text, fontWeight = FontWeight.SemiBold) }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier.clip(AppShape.sm).background(AppTheme.colors.surface)
                                        .clickable { confirmCancelId = row.id }.padding(vertical = 8.dp, horizontal = 14.dp)
                                ) { Text("취소", style = AppType.caption, color = AppTheme.colors.unpaid, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("닫기", style = AppType.body, color = AppTheme.colors.textSub)
            }
        }
    }

    // 시간 수정 — 보내기 창과 같은 공용 시트.
    editId?.let { id ->
        ScheduleTimeSheet(
            onDismiss = { editId = null },
            onPick = { ms -> onReschedule(id, ms); editId = null }
        )
    }

    // 취소 확인 — 실수로 예약이 사라지지 않게 한 번 더 묻는다.
    confirmCancelId?.let { id ->
        AlertDialog(
            tonalElevation = 0.dp,
            containerColor = AppTheme.colors.surface,
            onDismissRequest = { confirmCancelId = null },
            title = { Text("이 예약을 취소할까요?", color = AppTheme.colors.text, fontWeight = FontWeight.Bold) },
            text = { Text("취소하면 이 문자는 보내지지 않아요.", color = AppTheme.colors.textSub) },
            confirmButton = {
                TextButton(onClick = { onCancel(id); confirmCancelId = null }) {
                    Text("예약 취소", color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelId = null }) {
                    Text("그대로 두기", color = AppTheme.colors.textSub)
                }
            }
        )
    }
}
