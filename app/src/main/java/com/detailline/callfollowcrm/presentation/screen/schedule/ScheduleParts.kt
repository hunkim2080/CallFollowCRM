package com.detailline.callfollowcrm.presentation.screen.schedule

import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppType
import androidx.compose.material.icons.filled.AutoAwesome
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.LightColors
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.input.KeyboardType
import com.detailline.callfollowcrm.presentation.component.FormattedTextField
import com.detailline.callfollowcrm.presentation.component.SheetFieldLabel
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossWarning
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextInfo
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.launch
import java.util.Calendar
import com.detailline.callfollowcrm.presentation.util.keyboardOrNavPadding
import com.detailline.callfollowcrm.util.PhoneKey

// ======================================================================
//  🍃 일정 화면의 단독 조각 — 간단 일정 창·다가올 목록·요일 머리·A/S 카드
//
//  ScheduleScreen.kt 에서 **그대로 옮겨온** 조각들이다. (2026-10-02)
//  옮긴 기준: 그 파일의 **다른 private 이름을 하나도 안 쓰는** 잎(leaf) 선언.
//  같은 패키지라 `private` → `internal` 한 글자만 바뀌었고 **부르는 곳은 그대로**다.
//  (Fable 점검 ✋ — 큰 화면을 통째로 쪼개는 건 위험하니 **잎부터** 떼어낸다)
// ======================================================================

/**
 * 부른 사장님 이름표. 이름이 없으면 null.
 *
 * 🔴 전엔 이름이 그냥 "사장님" 일 때 "$it 사장님" 을 또 붙여 **"사장님 사장님"** 이 됐다.
 *   (2026-09-22 폰에서 확인)
 */
internal fun collabBossLabel(name: String): String? = when {
    name.isBlank() -> null
    name.contains("사장") -> name
    else -> "$name 사장님"
}

/**
 * 간단 일정 고치기 창 — 제목 · 날짜 · 시간 · 메모. (2026-09-18 사장님)
 *   등록 화면(ScheduleAddScreen)과 같은 순서·같은 말로 맞춘다. 지우기는 맨 아래 조용히.
 */
@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@Composable
internal fun SimpleEventEditDialog(
    event: com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity,
    onSave: (title: String, dayMs: Long, minutes: Int?, memo: String, days: Int) -> Unit,
    onAskDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(event.title) }
    var memo by remember { mutableStateOf(event.memo) }
    var dayMs by remember { mutableLongStateOf(event.dayStartMs) }
    var days by remember { mutableIntStateOf(event.days.coerceAtLeast(1)) }
    var minutes by remember { mutableStateOf(event.minutes) }
    var datePickerOpen by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp)
        ) {
            com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
            Text("간단 일정 고치기", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(14.dp))

            Text("제목", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
            Spacer(Modifier.height(5.dp))
            androidx.compose.material3.OutlinedTextField(
                value = title, onValueChange = { title = it },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("예: 자재 받는 날") }
            )

            Spacer(Modifier.height(12.dp))
            Text("날짜", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
            Spacer(Modifier.height(5.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                    .clickable { datePickerOpen = true }.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    // \uC5EC\uB7EC \uB0A0\uC774\uBA74 "10\uC6D4 17\uC77C ~ 22\uC77C \u00B7 6\uC77C". \uD558\uB8E8\uBA74 \uB2E8\uC77C \uB0A0\uC9DC. (v64, 2026-10-08 \uC0AC\uC7A5\uB2D8)
                    DateTimeUtils.formatDayRange(dayMs, days),
                    fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text("\u203A", fontSize = 17.sp, color = TossTextTertiary)
            }

            Spacer(Modifier.height(12.dp))
            Text("시간", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
            Spacer(Modifier.height(5.dp))
            // 여러 날이면 시간은 '하루 종일' 고정 — 여행·휴가에 '오전 9시'는 안 맞는다. 등록 화면과 같은 규칙. (v64, 2026-10-08 사장님)
            if (days > 1) {
                Text(
                    "여러 날은 하루 종일로 저장돼요",
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TossTextTertiary,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TossGrayBg)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf<Pair<String, Int?>>(
                    "하루 종일" to null, "오전 8시" to 8 * 60, "오전 9시" to 9 * 60,
                    "오전 10시" to 10 * 60, "오후 1시" to 13 * 60, "오후 3시" to 15 * 60
                ).forEach { (label, m) ->
                    val on = minutes == m
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (on) TossBlue else TossGrayBg)
                            .clickable { minutes = m }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                            color = if (on) Color.White else TossTextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("메모 (선택)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
            Spacer(Modifier.height(5.dp))
            androidx.compose.material3.OutlinedTextField(
                value = memo, onValueChange = { memo = it },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
                placeholder = { Text("예: 케라폭시 20개") }
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                        .clickable { onDismiss() }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text("취소", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
                Box(
                    Modifier.weight(1.4f).clip(RoundedCornerShape(12.dp)).background(TossBlue)
                        .clickable {
                            val t = title.trim()
                            if (t.isBlank()) {
                                android.widget.Toast.makeText(ctx, "제목을 적어주세요", android.widget.Toast.LENGTH_SHORT).show()
                            } else onSave(t, dayMs, if (days > 1) null else minutes, memo.trim(), days)
                        }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text("저장", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "\uD83D\uDDD1 이 일정 지우기",
                fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                color = com.detailline.callfollowcrm.presentation.theme.TossError,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable { onAskDelete() }.padding(vertical = 8.dp)
            )
        }
    }

    if (datePickerOpen) {
        // 등록 화면과 같은 방식 — 시작·끝을 찍어 기간을 정한다. (v64, 2026-10-08 사장님)
        val toUtcMidnight = { ms: Long -> ms + java.util.TimeZone.getDefault().getOffset(ms) }
        val fromUtc = { ms: Long -> DateTimeUtils.startOfDay(ms - java.util.TimeZone.getDefault().getOffset(ms)) }
        val endMs = dayMs + (days.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS
        val rangeState = androidx.compose.material3.rememberDateRangePickerState(
            initialSelectedStartDateMillis = toUtcMidnight(dayMs),
            initialSelectedEndDateMillis = toUtcMidnight(endMs)
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = Color.White),
            tonalElevation = 0.dp,
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val s = rangeState.selectedStartDateMillis
                    if (s != null) {
                        val startLocal = fromUtc(s)
                        val endLocal = rangeState.selectedEndDateMillis?.let { fromUtc(it) } ?: startLocal
                        dayMs = startLocal
                        days = (((endLocal - startLocal) / DateTimeUtils.DAY_MS).toInt() + 1).coerceAtLeast(1)
                    }
                    datePickerOpen = false
                }) { Text("확인", color = TossBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { datePickerOpen = false }) { Text("취소") }
            }
        ) {
            androidx.compose.material3.DateRangePicker(
                state = rangeState,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = androidx.compose.material3.DatePickerDefaults.colors(
                    containerColor = Color.White,
                    selectedDayContainerColor = TossBlue,
                    selectedDayContentColor = Color.White,
                    dayInSelectionRangeContainerColor = TossBlue.copy(alpha = 0.18f),
                    dayInSelectionRangeContentColor = TossTextPrimary,
                    todayDateBorderColor = TossBlue,
                    todayContentColor = TossBlue
                ),
                modifier = Modifier.heightIn(max = 520.dp)
            )
        }
    }
}

@Composable
internal fun AsDayCard(
    customer: CustomerEntity,
    selectedDayMs: Long?,
    onClick: () -> Unit
) {
    val asStart = customer.asScheduledDate ?: return
    val s = DateTimeUtils.startOfDay(asStart)
    val totalDays = customer.asScheduledDays.coerceAtLeast(1)
    val dayN = selectedDayMs?.let { ((it - s) / DateTimeUtils.DAY_MS).toInt() + 1 }?.coerceIn(1, totalDays) ?: 1
    // A/S 색 = 파랑(범례와 같은 색). 전엔 '요청'과 똑같은 주황이라 🔧 이모지로 억지 구분했다. (2026-09-20 사장님)
    val orange = AppTheme.colors.primary; val orangeDeep = AppTheme.colors.primaryText; val orangeBg = AppTheme.colors.primaryBg
    TossCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(orange))
                Spacer(Modifier.width(10.dp))
                Text(
                    // 공구 이모지 대신 **왼쪽 점 색**(파랑)으로 A/S 임을 표시 — 범례와 같은 색. (2026-09-20)
                    customer.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(customer.phoneNumber),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (totalDays > 1) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(orangeBg).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("${totalDays}일 중 ${dayN}일차", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = orangeDeep)
                    }
                    Spacer(Modifier.width(7.dp))
                }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(orangeBg).padding(horizontal = 9.dp, vertical = 4.dp)) {
                    Text("A/S · 무료", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = orangeDeep)
                }
            }
            // 📍 주소 (있으면)
            com.detailline.callfollowcrm.util.AddressExtractor.tidyAddress(customer.address).takeIf { it.isNotBlank() }?.let { addr ->
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = TossTextTertiary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(addr, fontSize = 13.sp, color = TossTextSecondary, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * 협업 카드 우→좌 swipe → 숨김. 빨강 "삭제" affordance.
 *   confirmValueChange=false 로 원위치 복귀(데이터 흐름이 카드를 제거) + 스낵바 "되돌리기" 로 복구.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollabSwipeBox(onDelete: () -> Unit, content: @Composable () -> Unit) {
    // 밀면 바로가 아니라 → 드러나는 '삭제' 버튼을 눌러야 동작(2026-06-21 사장님). SwipeRevealBox 로 통일.
    // shape=16dp: 안에 든 TossCard(라운드 16dp)와 모서리를 맞춰야 빨간 버튼이 모서리로 삐져나오지 않음. (2026-06-23 사장님)
    com.detailline.callfollowcrm.presentation.component.SwipeRevealBox(
        onAction = onDelete,
        label = "삭제",
        shape = RoundedCornerShape(16.dp)
    ) { content() }
}

@Composable
internal fun CalNav(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, cd, tint = TossTextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun DowHeader() {
    val labels = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { idx, label ->
            val color = when (idx) {
                0 -> TossError
                6 -> TossBlue
                else -> TossTextSecondary
            }
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun DayCount(count: Int) {
    Text(
        "이 날 시공 ${count}곳",
        fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
        modifier = Modifier.padding(start = 2.dp, bottom = 11.dp)
    )
}

/**
 * 앞으로의 일정 — 고른 날이 비었을 때 그 자리에.
 *
 * 왜: 달력은 "언제 비었나" 를 답하고, 이 목록은 "뭐가 있나" 를 답한다.
 *     전엔 둘째 질문의 답이 **날짜를 눌러야만** 나왔다.
 * 시공과 협업을 **같이** 센다 — 협업만 있는 날에 "없어요" 라고 하면 거짓말이다.
 */
@Composable
internal fun UpcomingSection(
    jobs: List<CustomerEntity>,
    collab: List<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite>,
    todayStart: Long,
    onOpenDay: (Long) -> Unit
) {
    data class Up(val ms: Long, val who: String, val detail: String, val collabRow: Boolean)

    val rows = remember(jobs, collab, todayStart) {
        val out = ArrayList<Up>()
        jobs.forEach { c ->
            val d = c.scheduledWorkDate ?: return@forEach
            if (d < todayStart || c.isWorkDone) return@forEach
            val who = c.name?.takeIf { it.isNotBlank() }
                ?: com.detailline.callfollowcrm.util.PhoneNumberFormatter.format(c.phoneNumber)
            val t = c.scheduledWorkMinutes?.let { DateTimeUtils.formatWorkMinutes(it) }
            val addr = c.address?.trim()?.takeIf { it.isNotBlank() }
            out += Up(d, who + "님", listOfNotNull(t, addr).joinToString(" \u00b7 "), false)
        }
        collab.forEach { sct ->
            val d = sct.scheduledAtMs
            if (d <= 0L || DateTimeUtils.startOfDay(d) < todayStart) return@forEach
            val who = sct.ownerName.takeIf { it.isNotBlank() }?.let { "\ud611\uc5c5 \u00b7 $it\uc0ac\uc7a5\ub2d8" } ?: "\ud611\uc5c5 \ud604\uc7a5"
            val addr = sct.addr?.trim()?.takeIf { it.isNotBlank() } ?: sct.title
            out += Up(d, who, listOfNotNull(sct.timeLabel?.takeIf { it.isNotBlank() }, addr).joinToString(" \u00b7 "), true)
        }
        out.sortedBy { it.ms }.take(5)
    }
    if (rows.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            "\uc55e\uc73c\ub85c\uc758 \uc77c\uc815",
            style = AppType.label, color = TossTextSecondary,
            modifier = Modifier.padding(start = 2.dp, bottom = 9.dp)
        )
        rows.forEach { r ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(bottom = 8.dp)
                    // 앱의 다른 카드와 **같은 옷** — 그림자가 없으면 배경에 붕 떠서
                    //   "왜 밖으로 빠져있어?" 로 보인다. (2026-09-21 사장님)
                    .tossCardShadow(AppShape.lg)
                    .clip(AppShape.lg)
                    .background(Color.White)
                    .clickable { onOpenDay(DateTimeUtils.startOfDay(r.ms)) }
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.width(3.dp).height(30.dp).clip(AppShape.sm)
                        .background(if (r.collabRow) AppTheme.colors.category else TossSuccess)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.who, style = AppType.body, fontWeight = FontWeight.Bold, color = TossTextPrimary, maxLines = 1)
                    if (r.detail.isNotBlank()) {
                        Text(r.detail, style = AppType.caption, color = TossTextSecondary, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    DateTimeUtils.formatScheduledDate(r.ms),
                    style = AppType.caption, fontWeight = FontWeight.Bold, color = TossTextTertiary
                )
            }
        }
    }
}

@Composable
internal fun DayAddButton(label: String, onClick: () -> Unit) {
    // 프로토 .day-add — blue-tint 칩
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(TossBlueSoft)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, null, tint = TossBlueDark, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = TossBlueDark)
    }
}

/** 원 → 딱지용 짧은 금액. "120만" / "1,250만" (만원 미만은 원 그대로). */
internal fun manwonTag(won: Long): String =
    if (won >= 10_000L) {
        java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA)
            .format(Math.round(won / 10_000.0)) + "만"
    } else {
        java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(won) + "원"
    }

/** [길찾기] 파랑 채움 / [전화] 회색 채움. 둘 다 둥근 네모 — 알약은 '고르는 칩'에만. */
@Composable
internal fun GoBtn(label: String, primary: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
        color = if (primary) Color.White else TossTextSecondary,
        modifier = Modifier
            .clip(AppShape.sm)
            .background(if (primary) TossBlue else TossGrayBg)
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 8.dp)
    )
}

/** 시스템 전화 앱을 연다. ACTION_DIAL 은 권한이 필요 없고, 저절로 걸리지도 않는다. */
internal fun dialFromSchedule(context: android.content.Context, phoneNumber: String) {
    // ☎️ 전화 거는 자리는 **한 곳**이다. (2026-09-30 사장님)
    com.detailline.callfollowcrm.util.PhoneDialer.open(context, phoneNumber)
}

/** 프로토 .assign-btn — blue-tint 알약(배정) / 회색 텍스트(변경). */
@Composable
internal fun AssignBtn(label: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
        color = if (filled) TossBlue else TossTextTertiary,
        modifier = Modifier
            // 버튼은 둥근 네모. 알약은 '고르는 칩'에만. (2026-09-20 사장님)
            .clip(RoundedCornerShape(10.dp))
            .then(if (filled) Modifier.background(TossBlueSoft) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = if (filled) 13.dp else 8.dp, vertical = 7.dp)
    )
}

internal val ASSIGN_TINTS = listOf(
    Color(0xFFE6EFFF) to LightColors.primary,
    LightColors.doneBg to Color(0xFF16A765),
    LightColors.unpaidBg to LightColors.unpaid,
    LightColors.categoryBg to LightColors.category,
    LightColors.cautionBg to Color(0xFFE0920C),
)

/** "5월 29일 (금)" — 프로토 cal-day-label 포맷 (연도 없음). */
internal fun koreanMonthDay(ms: Long): String =
    java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREAN).format(java.util.Date(ms))

/** 달력 칸 지역명 글자 크기 — 46dp 칸에 2~3글자가 들어가는 한계값. 여기 한 곳에서만 정한다. */
internal val CAL_REGION_TEXT_SP = 8.5.sp

/** "방금 · 35건" / "오후 2:10 · 35건" / "어제 · 35건" — 버튼 밑 한 줄. (2026-09-15 사장님) */
internal fun lastSyncLabel(atMs: Long, count: Int): String {
    // 아직 한 번도 안 올렸으면 시각이 없다 — "방금" 이라고 거짓말하지 않는다. (2026-09-16)
    if (atMs <= 0L) return "아직"
    val diff = System.currentTimeMillis() - atMs
    val when_ = when {
        diff < 60_000L -> "방금"
        diff < 60 * 60_000L -> "${diff / 60_000L}분 전"
        DateTimeUtils.startOfDay(atMs) == DateTimeUtils.startOfDay(System.currentTimeMillis()) ->
            java.text.SimpleDateFormat("a h:mm", java.util.Locale.KOREA).format(java.util.Date(atMs))
        else -> java.text.SimpleDateFormat("M/d", java.util.Locale.KOREA).format(java.util.Date(atMs))
    }
    return if (count > 0) "$when_ · ${count}건" else when_
}

/**
 * 시공 '건'의 키 = (고객, 시공일). 한 고객이 여러 날짜를 잡을 수 있으므로(재방문 Phase2 Stage A, DB v49)
 *   고객 id 만으론 서로 다른 건이 구분되지 않는다 — 목록 key·달력 lane 모두 이 키를 쓴다. (2026-09-11 사장님)
 */
internal fun laneKeyOf(c: CustomerEntity): String =
    "${c.id}-${c.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) } ?: 0L}"

/**
 * 이 시공이 dayStart 날을 포함하는가 — 여러 날 시공(scheduledWorkDays) 고려.
 *   기간 = [시공일, 시공일 + (days-1)일]. days 기본 1 = 당일만.
 */
internal fun jobCoversDay(c: CustomerEntity, dayStart: Long): Boolean {
    val start = c.scheduledWorkDate ?: return false
    val s = DateTimeUtils.startOfDay(start)
    val days = c.scheduledWorkDays.coerceAtLeast(1)
    val end = s + (days - 1) * DateTimeUtils.DAY_MS
    return dayStart in s..end
}

/** 이 A/S 예약이 dayStart 날을 포함하는가 — 여러 날 A/S(asScheduledDays) 고려. 시공과 별개. (DB v43) */
internal fun asCoversDay(c: CustomerEntity, dayStart: Long): Boolean {
    val start = c.asScheduledDate ?: return false
    val s = DateTimeUtils.startOfDay(start)
    val days = c.asScheduledDays.coerceAtLeast(1)
    val end = s + (days - 1) * DateTimeUtils.DAY_MS
    return dayStart in s..end
}

/** fromAnchor → toAnchor 사이의 달 수(부호 있음). page 인덱스 계산용. */
internal fun monthsBetween(fromAnchor: Long, toAnchor: Long): Int {
    val a = Calendar.getInstance().apply { timeInMillis = fromAnchor }
    val b = Calendar.getInstance().apply { timeInMillis = toAnchor }
    return (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 12 + (b.get(Calendar.MONTH) - a.get(Calendar.MONTH))
}

/** 전문가 배정 시트 "+ 추가" 칩 — 채움 배경(보더 미사용)으로 토글식 인라인 추가 폼을 연다. */
@Composable
internal fun AddChip(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

/** 전문가 배정 시트 안 인라인 "빠른 추가" 폼 — 이름+전화(+일당). 화면 이동 없이 바로 등록. (2026-06-14) */
@Composable
internal fun QuickAddForm(
    title: String,
    showWage: Boolean,
    name: String, onName: (String) -> Unit,
    phone: String, onPhone: (String) -> Unit,
    wage: String, onWage: (String) -> Unit,
    accent: Color,
    /** 이 폼이 들어앉은 시트의 스크롤 — 칸에 손이 닿으면 맨 아래까지 내려간다. (2026-09-22 사장님) */
    scrollState: androidx.compose.foundation.ScrollState,
    onCancel: () -> Unit,
    onSubmit: () -> Unit
) {
    val formScope = androidx.compose.runtime.rememberCoroutineScope()
    Spacer(Modifier.height(10.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppTheme.colors.bg).padding(14.dp)
            // 🔴 전엔 [취소]·[추가]가 키보드에 덮여 **누를 수가 없었다.** (2026-09-22 사장님)
            //   어느 칸이든 손이 닿으면 맨 아래까지 내려간다. 키보드가 다 올라온 뒤에 재야 해서 잠깐 기다린다.
            .onFocusChanged { st ->
                if (st.hasFocus) formScope.launch {
                    kotlinx.coroutines.delay(280)
                    runCatching { scrollState.animateScrollTo(scrollState.maxValue) }
                }
            }
    ) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = TossTextPrimary)
        Spacer(Modifier.height(10.dp))
        SheetFieldLabel("이름")
        SheetTextField(name, onName, placeholder = "예: 박반장")
        Spacer(Modifier.height(10.dp))
        SheetFieldLabel("전화번호")
        FormattedTextField(
            value = phone, onValueChange = onPhone,
            format = PhoneNumberFormatter::formatProgressive,
            placeholder = "010-0000-0000", keyboardType = KeyboardType.Phone
        )
        if (showWage) {
            Spacer(Modifier.height(10.dp))
            SheetFieldLabel("그날 일당 · 만원 (선택)")
            SheetTextField(wage, onWage, placeholder = "예: 25", keyboardType = KeyboardType.Number,
                visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .clickable { onCancel() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("취소", fontWeight = FontWeight.Bold, color = TossTextSecondary, fontSize = 14.sp) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(accent)
                    .clickable { onSubmit() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("추가", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 14.sp) }
        }
    }
}

/**
 * 협업 현장의 시각을 **분**으로. 내 현장과 한 줄로 줄 세우려면 같은 자로 재야 한다.
 *   timeLabel 은 "09:00" 같은 글자다. 없거나 이상하면 맨 뒤로 보낸다.
 */
internal fun collabMinutes(
    site: com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite
): Int = com.detailline.callfollowcrm.domain.schedule.DayOrder
    .collabMinutes(site.timeLabel, site.scheduledAtMs)

internal val NEWLINE: String = String(charArrayOf(0x0A.toChar()))

/**
 * 「방금 · N건」을 누르면 뜨는 — 구글 캘린더에 올린 일정 목록. (2026-10-05 사장님
 *   "몇 건이 어떤 건인지·제대로 올라간 건지 의심스럽다 — 그 자리 누르면 보이게")
 *   어느 계정·어느 캘린더에 무엇이 올라갔는지 한눈에. 지금 다시 올리기로 수동 재동기화도.
 */
@androidx.compose.runtime.Composable
internal fun CalendarSyncSheet(
    accountEmail: String?,
    syncedAtMs: Long,
    syncedCount: Int,
    syncing: Boolean,
    syncDone: Int = 0,
    syncTotal: Int = 0,
    work: List<com.detailline.callfollowcrm.data.local.entity.CustomerEntity>,
    asList: List<com.detailline.callfollowcrm.data.local.entity.CustomerEntity>,
    simples: List<com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity>,
    onResync: () -> Unit,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val noRipple = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    data class CalRow(val emoji: String, val day: Long, val title: String, val sub: String?)
    val rows = androidx.compose.runtime.remember(work, asList, simples) {
        val out = ArrayList<CalRow>()
        fun who(c: com.detailline.callfollowcrm.data.local.entity.CustomerEntity): String =
            c.name?.takeIf { it.isNotBlank() } ?: c.address?.takeIf { it.isNotBlank() } ?: c.phoneNumber
        for (c in work) { val d = c.scheduledWorkDate ?: continue; out.add(CalRow("🏗️", d, who(c), "시공")) }
        for (c in asList) { val d = c.asScheduledDate ?: continue; out.add(CalRow("🔧", d, who(c), "A/S")) }
        for (e in simples) out.add(
            CalRow("📌", e.dayStartMs, e.title, if (e.days.coerceAtLeast(1) > 1) "간단 일정 · ${e.days}일" else "간단 일정")
        )
        out.sortedBy { it.day }
    }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = noRipple, indication = null) { onClose() }
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(AppShape.xl)
                .background(AppTheme.colors.surface)
                .clickable(interactionSource = noRipple, indication = null) { }
                .heightIn(max = 620.dp)
                // 🧷 바닥 버튼이 네비바에 짤리던 것 — 고정 22dp 대신 공용 여백으로. (2026-10-06 사장님 "또 짤림")
                .padding(horizontal = 20.dp).padding(top = 12.dp)
                .keyboardOrNavPadding()
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
                    .width(38.dp).height(4.dp).clip(AppShape.pill).background(TossDivider)
            )
            Text("구글 캘린더에 올린 일정", style = AppType.title, color = TossTextPrimary)
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().clip(AppShape.lg).background(TossBlueSoft).padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text("시공막내 캘린더", style = AppType.body, color = TossTextPrimary)
                    Text(accountEmail ?: "연결된 계정 — 설정에서 '다시 연결'하면 보여요",
                        style = AppType.caption, color = TossTextSecondary, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text("✓ 연결됨", style = AppType.label, color = TossBlue)
            }
            Spacer(Modifier.height(10.dp))
            Text("마지막 올림 · " + lastSyncLabel(syncedAtMs, syncedCount) + " · 자동",
                style = AppType.caption, color = TossTextTertiary)
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.Column(
                Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                if (rows.isEmpty()) {
                    Text("올라간 일정이 아직 없어요.", style = AppType.body, color = TossTextTertiary,
                        modifier = Modifier.padding(vertical = 14.dp))
                } else rows.forEach { r ->
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(r.emoji, style = AppType.body)
                        Spacer(Modifier.width(11.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(r.title, style = AppType.body, color = TossTextPrimary, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(koreanMonthDay(r.day) + (r.sub?.let { " · " + it } ?: ""),
                                style = AppType.caption, color = TossTextTertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().clip(AppShape.md).background(TossBlue)
                    .clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://calendar.google.com"))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("구글 캘린더 열기", style = AppType.headline, color = AppTheme.colors.textOnPrimary) }
            Spacer(Modifier.height(8.dp))
            // 올리는 중이면 '지금 다시 올리기' 버튼 자리에 **진행바**(채워지는 느낌 + 몇/몇). (2026-10-08 사장님)
            //   백그라운드라 이 시트를 닫거나 뒤로 가도 계속 올라간다 — sub 안내로 안심시킨다.
            if (syncing) {
                com.detailline.callfollowcrm.presentation.component.UploadProgressBar(
                    done = syncDone, total = syncTotal,
                    sub = "뒤로 가도 계속 올라가요 · 끝나면 정리까지 자동"
                )
            } else {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().clip(AppShape.md).background(TossGrayBg)
                        .clickable { onResync() }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("지금 다시 올리기", style = AppType.headline, color = TossTextSecondary) }
            }
        }
    }
}
