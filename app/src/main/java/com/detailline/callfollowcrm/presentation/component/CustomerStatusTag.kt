package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import java.util.Calendar

/**
 * 고객 상태(예약/잔금미수/완료/신규/미전환) — 시공일·잔금 데이터로 자동 계산 + 딱지.
 *   여러 화면 공용(고객관리·상담함·고객상세)이라 '따라다니는 상태 딱지'가 일관됨. (2026-09-03 사장님)
 */

/** 오늘 0시(ms) — 상태 계산 기준. */
fun startOfTodayMs(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * 상태 계산 (단일 출처) — 미래 시공일=예약 · 시공 지남/완료처리/잔금받음 중 잔금 안받음=잔금미수·다받음=완료 ·
 *   그 외 14일 이내=신규, 지남=미전환.
 */
fun customerStatusOf(
    c: CustomerEntity,
    today0: Long = startOfTodayMs(),
    now: Long = System.currentTimeMillis()
): String {
    val wd = c.scheduledWorkDate
    if (wd != null && wd >= today0) return "예약"
    val worked = (wd != null && wd < today0) || c.workCompletedAt != null || c.balancePaidAt != null
    if (worked) {
        val bal = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(c).balanceAmount
        return if (c.balancePaidAt == null && bal > 0L) "잔금미수" else "완료"
    }
    val ageDays = (now - c.createdAt) / 86_400_000L
    return if (ageDays <= 14) "신규" else "미전환"
}

/** 상태별 딱지 색 (fg, bg). CustomersScreen custTag 와 동일 팔레트. */
private fun statusColors(s: String): Pair<Color, Color> = when (s) {
    "완료" -> Color(0xFF0E9F56) to Color(0xFFE5F8EE)   // green
    "신규" -> Color(0xFFB7791F) to Color(0xFFFEF3E0)   // amber
    "미전환" -> Color(0xFF9AA3AF) to Color(0xFFF4F5F7) // gray
    "잔금미수" -> Color(0xFFE0620D) to Color(0xFFFFF0E1) // orange (돈 받을 것)
    else -> Color(0xFF3182F6) to Color(0xFFEAF2FE)     // blue (예약)
}

/** 고객 상태 딱지 — 어느 화면에서나 같은 모양으로 따라다님. (2026-09-03 사장님) */
@Composable
fun CustomerStatusTag(status: String) {
    val (fg, bg) = statusColors(status)
    Box(
        Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(status, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}
