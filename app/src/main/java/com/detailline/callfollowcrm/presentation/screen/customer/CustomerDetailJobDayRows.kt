package com.detailline.callfollowcrm.presentation.screen.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import com.detailline.callfollowcrm.domain.job.JobDayCompletion
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 📅 **여러 날 시공의 날짜별 완료 줄** — 건 카드 안에 들어가는 1일차/2일차 … 각각 완료/완료하기. (2026-11-12 사장님)
 *   "1일차 완료했는데 2일차까지 완료돼버림" → 날마다 따로. **모든 날 완료돼야** 건 전체 완료([JobDayCompletion.allDone]).
 *   큰 파일(CustomerDetailJobCard) 비대 방지로 떼어냄(§12-F). 날짜 셈·판정은 전부 [JobDayCompletion] 한 곳.
 *   1일 건이면 아무것도 안 그린다(건 카드가 단일 「시공 완료」 버튼을 그린다).
 */
@Composable
internal fun JobDayCompleteRows(job: JobEntity, onToggleDay: (dayStart: Long) -> Unit) {
    val days = JobDayCompletion.dayStarts(job.scheduledWorkDate, job.scheduledWorkDays)
    if (days.size <= 1) return
    val doneSet = JobDayCompletion.completedSet(job.completedDayStarts)
    val today = DateTimeUtils.startOfDay(System.currentTimeMillis())
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .border(1.dp, AppTheme.colors.line, RoundedCornerShape(13.dp))
    ) {
        days.forEachIndexed { i, d ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.line))
            val isDone = d in doneSet
            val canComplete = d <= today
            val isToday = d == today
            Row(
                Modifier.fillMaxWidth()
                    .then(if (canComplete || isDone) Modifier.clickable { onToggleDay(d) } else Modifier)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${i + 1}일차",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.text, modifier = Modifier.width(48.dp)
                )
                Text(
                    DateTimeUtils.formatMonthDay(d) + (if (isToday) " · 오늘" else ""),
                    fontSize = 13.sp, color = TossTextSecondary, modifier = Modifier.weight(1f)
                )
                val pillBg = when { isDone -> AppTheme.colors.doneBg; canComplete -> AppTheme.colors.primaryBg; else -> Color.Transparent }
                val pillFg = when { isDone -> AppTheme.colors.doneText; canComplete -> AppTheme.colors.text; else -> TossTextTertiary }
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(pillBg).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        if (isDone) "✓ 완료" else if (canComplete) "완료하기" else "예정",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pillFg
                    )
                }
            }
        }
    }
}
