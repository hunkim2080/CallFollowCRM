package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.launch

/**
 * 시공일 달력 — **일정 탭 달력의 축소판.** (2026-09-24 사장님)
 *   "우리 달력 디자인 일정에 나오는 그 달력이 이쁜데 그 달력 축소판이 깔끔하지 않을까?"
 *
 * **앱 어디서나 같은 달력.** 접수서·시공 기간·A/S 예약·일정 추가·간단 일정이 전부 이걸 쓴다.
 *   (전엔 Material3 DateRangePicker · 이 축소판 · 회색 미니달력 **세 종류**가 섞여 있었다 —
 *    2026-10-24 사장님 "왜 캘린더 종류가 2개야?")
 *
 * 흰 바탕 카드 · 일=빨강/토=파랑 · 날짜는 칸 **가운데 작은 원** 안에 · 오늘은 [accent] 원 ·
 *   고른 기간(시작~끝)은 연한 [rangeBg] 한 덩어리. 색만 [accent]/[rangeBg] 로 바꿔
 *   A/S(주황) 같은 맥락을 구분한다 — 모양은 그대로.
 *
 * 기간은 [com.detailline.callfollowcrm.domain.schedule.RangePick] 로 받는다
 *   (한 번=시작일, 더 나중 날 한 번 더=끝날). 달 전환은 옆으로 쓸기(일정 탭과 같음).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkRangeCalendar(
    monthAnchor: Long,
    startMs: Long?,
    endMs: Long?,
    onPick: (Long) -> Unit,
    accent: Color = TossBlue,
    rangeBg: Color = AppTheme.colors.primaryBg
) {
    val base = remember { monthAnchor }
    val pagerState = rememberPagerState(initialPage = CAL_PAGER_CENTER) { CAL_PAGER_COUNT }
    val scope = rememberCoroutineScope()
    val viewed by remember {
        derivedStateOf { DateTimeUtils.shiftMonth(base, pagerState.currentPage - CAL_PAGER_CENTER) }
    }
    Column(
        Modifier.fillMaxWidth().clip(AppShape.lg)
            .background(Color.White)
            .border(1.dp, TossDivider, AppShape.lg)
            .padding(horizontal = 10.dp, vertical = AppSpace.s12)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color.White)
                .clickable { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                contentAlignment = Alignment.Center) { Text("‹", fontSize = 16.sp, color = TossTextSecondary, fontWeight = FontWeight.Bold) }
            Text(DateTimeUtils.formatMonthHeader(viewed), modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color.White)
                .clickable { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                contentAlignment = Alignment.Center) { Text("›", fontSize = 16.sp, color = TossTextSecondary, fontWeight = FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                Text(d, modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    textAlign = TextAlign.Center, fontSize = 11.sp,
                    color = when (i) { 0 -> TossError; 6 -> TossBlue; else -> TossTextSecondary }, fontWeight = FontWeight.SemiBold)
            }
        }
        val s0 = startMs?.let { DateTimeUtils.startOfDay(it) }
        val e0 = endMs?.let { DateTimeUtils.startOfDay(it) }
        HorizontalPager(state = pagerState, verticalAlignment = Alignment.Top) { page ->
            val cells = buildWorkRangeCells(DateTimeUtils.shiftMonth(base, page - CAL_PAGER_CENTER))
            Column(Modifier.fillMaxWidth()) {
                repeat(6) { w ->
                    Row(Modifier.fillMaxWidth()) {
                        cells.subList(w * 7, w * 7 + 7).forEach { cell ->
                            // 고른 날 = 시작일, 끝날, 그리고 그 사이 모든 날. 셋 다 같은 연한 띠 — 프로토 .pick.
                            val picked = s0 != null && (
                                cell.dayMs == s0 || (e0 != null && cell.dayMs > s0 && cell.dayMs <= e0)
                            )
                            val fg = when {
                                cell.isToday -> Color.White
                                picked -> accent
                                !cell.inMonth || cell.isPast -> TossTextTertiary
                                cell.dow == java.util.Calendar.SUNDAY -> TossError
                                cell.dow == java.util.Calendar.SATURDAY -> TossBlue
                                else -> TossTextSecondary
                            }
                            Box(
                                Modifier.weight(1f).height(34.dp).padding(horizontal = 1.dp)
                                    .clip(AppShape.sm)
                                    .background(if (picked) rangeBg else Color.Transparent)
                                    .clickable { onPick(cell.dayMs) },
                                // 날짜는 칸 **가운데**. 일정 막대가 없는 고르기 달력이라 가운데가 균형. (2026-10-24 사장님)
                                contentAlignment = Alignment.Center
                            ) {
                                // 오늘만 accent 원(가운데).
                                Box(
                                    Modifier.size(20.dp).clip(CircleShape)
                                        .background(if (cell.isToday) accent else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        cell.dom.toString(), color = fg,
                                        fontSize = 11.sp,
                                        lineHeight = 11.sp,   // 테마 lineHeight 물려받으면 원 안에서 글자가 내려앉는다
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 달력 밑 안내 한 줄 — 상태에 따라 "하루면 한 번 / 끝날 한 번 더 / N일 공사". */
fun workRangeHint(startMs: Long?, endMs: Long?): String = when {
    startMs == null -> "하루면 한 번, 여러 날이면 끝날을 한 번 더 눌러주세요"
    endMs == null -> "당일 공사예요 · 끝날을 한 번 더 누르면 기간이 돼요"
    else -> "${com.detailline.callfollowcrm.domain.schedule.RangePick.daysOf(startMs, endMs)}일 공사로 잡혔어요 · 다시 고르려면 시작일을 눌러요"
}

private data class WorkRangeCell(val dayMs: Long, val dom: Int, val dow: Int, val inMonth: Boolean, val isToday: Boolean, val isPast: Boolean)

private const val CAL_PAGER_CENTER = 1200
private const val CAL_PAGER_COUNT = 2400   // ±100개월

private fun buildWorkRangeCells(anchor: Long): List<WorkRangeCell> {
    val today = DateTimeUtils.startOfDay(System.currentTimeMillis())
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = anchor }
    val month = cal.get(java.util.Calendar.MONTH)
    val firstDow = cal.get(java.util.Calendar.DAY_OF_WEEK)
    cal.add(java.util.Calendar.DAY_OF_MONTH, -(firstDow - 1))
    return (0 until 42).map {
        val dayStart = DateTimeUtils.startOfDay(cal.timeInMillis)
        val cell = WorkRangeCell(
            dayStart, cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.DAY_OF_WEEK),
            cal.get(java.util.Calendar.MONTH) == month, dayStart == today, dayStart < today
        )
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        cell
    }
}
