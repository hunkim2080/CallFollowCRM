package com.detailline.callfollowcrm.presentation.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.domain.settlement.SettlementCalc
import com.detailline.callfollowcrm.domain.ui.AvatarInitial
import com.detailline.callfollowcrm.presentation.component.AppSheet
import com.detailline.callfollowcrm.presentation.component.AvatarTints
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.MoneyFormatter
import com.detailline.callfollowcrm.util.PhoneDialer
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * **"/고객" 요약 카드** — 이 고객 한눈에 + 바로가기. (2026-10-24 · 프로토 artifact/5nhqznJqPqXLtyfeTaHULA 2단계)
 *   대화창에서 "/고객" 치면 뜬다. 전화·현장·시공일(D-day)·받을 돈을 보여주고
 *   [전화][일정][문구][접수서] 로 바로 넘어간다. 돈은 `SettlementCalc`(단일 출처), 아바타는 공용.
 */
@Composable
fun CustomerSummarySheet(
    customer: CustomerEntity,
    onDismiss: () -> Unit,
    onSchedule: () -> Unit,
    onTemplate: () -> Unit,
    onEstimate: () -> Unit,
) {
    val ctx = LocalContext.current
    val name = customer.name?.takeIf { it.isNotBlank() }
    val phone = PhoneNumberFormatter.format(customer.phoneNumber)
    val display = name ?: phone
    val (avBg, avFg) = AvatarTints.SINGLE[0]
    val outstanding = SettlementCalc.rowOf(customer).outstanding

    AppSheet(onDismiss = onDismiss) {
        // 머리 — 아바타 + 이름(또는 번호)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(avBg), contentAlignment = Alignment.Center) {
                Text(AvatarInitial.of(display), color = avFg, fontWeight = FontWeight.ExtraBold, style = AppType.headline)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(display, style = AppType.headline, color = TossTextPrimary, fontWeight = FontWeight.ExtraBold)
                if (name != null) Text(phone, style = AppType.caption, color = TossTextSecondary)
            }
        }
        Spacer(Modifier.height(14.dp))

        // 요약 줄
        customer.address?.takeIf { it.isNotBlank() }?.let { KvRow("현장", it) }
        customer.scheduledWorkDate?.let { ms ->
            val days = ((DateTimeUtils.startOfDay(ms) - DateTimeUtils.startOfDay(System.currentTimeMillis())) / DateTimeUtils.DAY_MS).toInt()
            val dday = when {
                days == 0 -> "D-DAY"
                days > 0 -> "D-$days"
                else -> "D+${-days}"
            }
            val span = if (customer.scheduledWorkDays > 1) " · ${customer.scheduledWorkDays}일" else ""
            KvRow("시공일", "${DateTimeUtils.formatShortKoreanDate(ms)} · $dday$span")
        }
        if (outstanding > 0L) KvRow("받을 돈", MoneyFormatter.won(outstanding), valueColor = AppTheme.colors.caution)

        Spacer(Modifier.height(16.dp))
        // 바로가기 — 전화·일정·문구·접수서
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            ActBtn("전화", Icons.Default.Phone, AppTheme.colors.primary) { onDismiss(); PhoneDialer.open(ctx, customer.phoneNumber) }
            ActBtn("일정", Icons.Default.DateRange, AppTheme.colors.category) { onDismiss(); onSchedule() }
            ActBtn("문구", Icons.AutoMirrored.Filled.Chat, AppTheme.colors.text) { onDismiss(); onTemplate() }
            ActBtn("접수서", Icons.Default.Assignment, AppTheme.colors.primary) { onDismiss(); onEstimate() }
        }
    }
}

@Composable
private fun ColumnScope.KvRow(label: String, value: String, valueColor: Color = TossTextPrimary) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(label, style = AppType.body, color = AppTheme.colors.textHint)
        Text(value, style = AppType.body, color = valueColor, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun RowScope.ActBtn(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
            .clickable { onClick() }.padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, style = AppType.caption, color = TossTextSecondary, fontWeight = FontWeight.Bold)
    }
}
