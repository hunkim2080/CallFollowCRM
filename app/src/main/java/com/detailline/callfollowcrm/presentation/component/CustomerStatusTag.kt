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

// ══════════════════════════════════════════════════════════════════════
//  태그는 **두 종류**다. (2026-09-16 사장님)
//    ① 분류 태그  — 사장님이 정한 묶음(일당·거래처·택배…)      → [CategoryTag]   보라
//    ② 날짜 태그  — 앱이 계산하는 진행 상태(시공 D-3·잔금미수…) → [ScheduleTag]   상태별 색
//  두 개는 **같이** 쓴다(서로 대체재가 아니다). 사장님 말 그대로:
//    "1종류는 정한 분류 태그… 1종류는 날짜 관련 태그들… 그래서 둘다 쓰면 되지 않나"
//
//  🔴 왜 여기 모았나 — 화면마다 제각각 색을 적어 넣어서 같은 「일당」이 홈에선 #6D5AE6,
//     기다려요 카드에선 #7C5CFC 로 **미묘하게 다른 보라**였다. 대화방에 넣을 땐 또 새로 적었다.
//     같은 뜻이면 같은 모양이어야 한다 → 이 파일이 유일한 출처다. 화면에서 색을 적지 말 것.
// ══════════════════════════════════════════════════════════════════════

/** 분류 태그 색 — 사장님이 만든 묶음(일당 등). 한 곳에서만 정한다. */
private val CATEGORY_FG = Color(0xFF6D5AE6)
private val CATEGORY_BG = Color(0xFFEFEBFF)

/**
 * ① 분류 태그 — 사장님이 직접 만든 묶음.
 *
 * @param includeAuto 자동 분류('시공 대기'·'시공 완료')도 보여줄지.
 *   기본 false — 그건 시공일이 잡히면 **모두에게 자동으로** 붙어서, 켜두면 거의 모든 줄에 달려
 *   정작 '일당' 같은 진짜 묶음이 파묻힌다. 게다가 옆의 날짜 태그가 이미 같은 말을 한다.
 *   (2026-08-04 에 제가 정한 규칙인데 말씀을 안 드려 사장님이 "그런게 있었어?" 하셨다 — 2026-09-16)
 */
@Composable
fun CategoryTag(
    category: com.detailline.callfollowcrm.data.local.entity.CategoryEntity?,
    includeAuto: Boolean = false
) {
    val cat = category ?: return
    if (!includeAuto) {
        val auto = com.detailline.callfollowcrm.data.local.seed.DefaultCategories
        if (cat.name == auto.NAME_PENDING_WORK || cat.name == auto.NAME_DONE_WORK) return
    }
    Box(
        Modifier.clip(RoundedCornerShape(7.dp)).background(CATEGORY_BG)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            (cat.emoji?.takeIf { it.isNotBlank() }?.let { "$it " } ?: "") + cat.name,
            fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = CATEGORY_FG, maxLines = 1
        )
    }
}

/**
 * 날짜 태그에 쓸 **표시 문구**. 계산은 [customerStatusOf] 하나만 쓴다(고객관리 필터와 같은 값).
 *   '예약'만 날짜를 보태 더 친절하게 바꾼다 — "예약" 보다 "시공 D-3" 이 훨씬 쓸모 있다.
 *   나머지(잔금미수·완료·신규·미전환)는 그대로.
 *   ⚠️ [customerStatusOf] 의 **반환값은 절대 바꾸지 말 것** — 고객관리 화면이 그 글자로 필터링한다.
 */
fun scheduleTagLabel(
    c: CustomerEntity,
    today0: Long = startOfTodayMs(),
    now: Long = System.currentTimeMillis()
): String {
    val status = customerStatusOf(c, today0, now)
    if (status != "예약") return status
    val wd = c.scheduledWorkDate ?: return status
    val days = ((wd - today0) / 86_400_000L).toInt()
    return if (days <= 0) "시공 D-DAY" else "시공 D-$days"
}

/**
 * ② 날짜 태그 — 시공이 며칠 남았나 / 돈 받을 게 남았나.
 *
 * @param hideDormant '미전환'(14일간 조용한 고객)은 기본으로 숨긴다 — 목록 대부분이 여기 해당해서
 *   켜두면 거의 모든 줄에 회색 딱지가 붙어 시선만 흐린다. 고객관리처럼 상태가 중요한 화면은 false.
 */
@Composable
fun ScheduleTag(c: CustomerEntity?, hideDormant: Boolean = true) {
    val customer = c ?: return
    val label = scheduleTagLabel(customer)
    if (hideDormant && label == "미전환") return
    // 색은 상태 기준(= D-N 도 '예약'의 파랑). 모든 화면에서 같은 색.
    val (fg, bg) = statusColors(if (label.startsWith("시공 D")) "예약" else label)
    Box(
        Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}
