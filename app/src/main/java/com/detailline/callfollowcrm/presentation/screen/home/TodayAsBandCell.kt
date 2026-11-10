package com.detailline.callfollowcrm.presentation.screen.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * 🔧 홈 띠 — **오늘 A/S 한 쪽**(무료·시간 미정·**파랑**, 일정 탭 A/S 와 같은 색). (2026-11-11 사장님)
 *
 * 전엔 홈이 A/S(asScheduledDate)를 아예 안 봐서 오늘 A/S 가 있어도 "오늘은 일정이 없어요" 라 떴다.
 * HomeScreen 이 더 커지지 않게 같은 패키지 다른 파일로 뗀다(§12-F). 색은 토큰만(style_guard).
 */
@Composable
internal fun AsTodayBandCell(
    asC: CustomerEntity,
    page: Int,
    total: Int,
    dayStart: Long,
    onNavigateAddr: (String?) -> Unit,
    onOpenChat: (String, Long?) -> Unit
) {
    val who = asC.name?.takeIf { it.isNotBlank() }
        ?: PhoneNumberFormatter.format(asC.phoneNumber)
    val addr = asC.address?.trim()?.takeIf { it.isNotBlank() }
    val totalDays = asC.asScheduledDays.coerceAtLeast(1)
    val dayN = asC.asScheduledDate?.let {
        ((dayStart - DateTimeUtils.startOfDay(it)) / DateTimeUtils.DAY_MS).toInt() + 1
    }?.coerceIn(1, totalDays) ?: 1
    BandShell(
        bg = AppTheme.colors.primaryBg, fg = AppTheme.colors.primaryText,
        subFg = AppTheme.colors.textSub,
        icon = "", iconVector = Icons.Default.Build, border = AppTheme.colors.primaryBg,
        iconBg = Color.White, iconTint = AppTheme.colors.primary,
        line1 = "오늘 A/S · 무료" +
            (if (totalDays > 1) " · ${totalDays}일 중 ${dayN}일차" else "") +
            com.detailline.callfollowcrm.domain.schedule.TodayBandPages.label(page, total),
        line2 = who + " · " + (addr ?: "주소 아직 없어요"),
        action = if (addr != null) "길찾기" else null,
        onAction = { onNavigateAddr(addr) },
        onTap = { onOpenChat(asC.phoneNumber, asC.id) }
    )
}
