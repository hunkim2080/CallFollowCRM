package com.detailline.callfollowcrm.presentation.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.service.NotificationHelper

/**
 * 더보기 → 알림 소리 (2026-07-15 사장님 재설계 — 당근 스타일 2단계).
 *   1단계: 알림 종류 목록 (오른쪽에 지금 고른 소리) → 탭
 *   2단계: 그 종류의 소리 고르기 (체크 표시 + 탭하면 바로 미리듣기 + 적용)
 *   소리는 NotificationChannel 에 붙고 채널은 생성 후 불변 → applySlotSound 가 '새 버전 채널'로 재생성해 즉시 적용.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundSettingsScreen(prefs: AppPreferences, onBack: () -> Unit) {
    val context = LocalContext.current

    DisposableEffect(Unit) { onDispose { NotificationHelper.stopPreview() } }

    val selections = remember {
        mutableStateMapOf<String, String>().apply {
            NotificationHelper.SOUND_SLOTS.forEach { put(it.key, prefs.notificationSound(it.key, it.defaultRes)) }
        }
    }
    var picking by remember { mutableStateOf<NotificationHelper.SoundSlot?>(null) }

    // 방해금지 시간대 (밤엔 소리·진동 없이 조용히). prefs 에 바로 저장. (2026-08-04 사장님)
    var quietOn by remember { mutableStateOf(prefs.quietHoursEnabled) }
    var quietStart by remember { mutableStateOf(prefs.quietStartHour) }
    var quietEnd by remember { mutableStateOf(prefs.quietEndHour) }
    fun pickHour(initial: Int, onPick: (Int) -> Unit) {
        android.app.TimePickerDialog(context, { _, h, _ -> onPick(h) }, initial, 0, false).show()
    }

    fun closePicker() { picking = null; NotificationHelper.stopPreview() }
    BackHandler(enabled = picking != null) { closePicker() }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(picking?.label ?: "알림 소리", fontWeight = FontWeight.Bold, color = TossTextPrimary, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = { if (picking != null) closePicker() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        val slot = picking
        if (slot == null) {
            // ── 1단계: 알림 종류 목록 ──
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    QuietHoursSection(
                        enabled = quietOn, startHour = quietStart, endHour = quietEnd,
                        onToggle = { quietOn = it; prefs.quietHoursEnabled = it },
                        onPickStart = { pickHour(quietStart) { h -> quietStart = h; prefs.quietStartHour = h } },
                        onPickEnd = { pickHour(quietEnd) { h -> quietEnd = h; prefs.quietEndHour = h } }
                    )
                }
                item {
                    Text(
                        "알림 종류를 눌러서 소리를 고르세요.",
                        fontSize = 13.sp, color = TossTextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                item { SectionDivider() }
                items(NotificationHelper.SOUND_SLOTS, key = { it.key }) { s ->
                    val cur = selections[s.key] ?: s.defaultRes
                    val curLabel = NotificationHelper.SOUND_OPTIONS.find { it.first == cur }?.second ?: "무음"
                    Row(
                        Modifier.fillMaxWidth().background(Color.White)
                            .clickable { picking = s }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.label, fontSize = 15.sp, color = TossTextPrimary, modifier = Modifier.weight(1f))
                        Text(curLabel, fontSize = 14.sp, color = TossBlue, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.ChevronRight, null, tint = TossTextTertiary, modifier = Modifier.size(18.dp))
                    }
                    RowDivider()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        } else {
            // ── 2단계: 소리 고르기 (탭 = 바로 적용 + 미리듣기) ──
            val current = selections[slot.key] ?: slot.defaultRes
            fun choose(res: String) {
                selections[slot.key] = res
                prefs.setNotificationSound(slot.key, res)
                NotificationHelper.applySlotSound(context, slot.key)   // 새 버전 채널로 재생성 = 즉시 적용
                NotificationHelper.previewSound(context, res)
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item { GroupHeader("알림음") }
                items(NotificationHelper.SOUND_OPTIONS.filter { it.first != "silent" }, key = { it.first }) { (res, label) ->
                    SoundRow(label = label, selected = res == current, onClick = { choose(res) })
                    RowDivider()
                }
                item { GroupHeader("기타") }
                item {
                    SoundRow(label = "무음", selected = current == "silent", onClick = { choose("silent") })
                    RowDivider()
                }
                item {
                    Text(
                        "누르면 바로 들어보고 적용돼요.",
                        fontSize = 12.sp, color = TossTextTertiary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}

/** 방해금지 시간대 카드 — 스위치 + 시작/끝 시각. 밤엔 소리·진동 없이 조용히. (2026-08-04 사장님) */
@Composable
private fun QuietHoursSection(
    enabled: Boolean,
    startHour: Int,
    endHour: Int,
    onToggle: (Boolean) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🌙 방해금지 시간", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(
                    "이 시간엔 소리·진동 없이 조용히 알림만 와요. (놓치지 않아요)",
                    fontSize = 12.5.sp, color = TossTextTertiary, lineHeight = 17.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = enabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = TossBlue,
                    uncheckedThumbColor = Color.White, uncheckedTrackColor = TossTextTertiary
                )
            )
        }
        if (enabled) {
            Spacer(Modifier.height(14.dp))
            QuietTimeRow("시작", quietHourLabel(startHour), onPickStart)
            Spacer(Modifier.height(8.dp))
            QuietTimeRow("끝", quietHourLabel(endHour), onPickEnd)
        }
    }
}

@Composable
private fun QuietTimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(TossGrayBg)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TossTextSecondary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = TossBlue, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.ChevronRight, null, tint = TossTextTertiary, modifier = Modifier.size(18.dp))
    }
}

/** 0~23시 → "오전/오후 N시". */
private fun quietHourLabel(h: Int): String {
    val ampm = if (h < 12) "오전" else "오후"
    val hh = if (h % 12 == 0) 12 else h % 12
    return "$ampm ${hh}시"
}

@Composable
private fun SoundRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.PlayArrow, null,
            tint = if (selected) TossBlue else TossTextTertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label, fontSize = 15.sp,
            color = if (selected) TossBlue else TossTextPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) Icon(Icons.Filled.Check, "선택됨", tint = TossBlue, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().background(Color.White).padding(start = 20.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
    }
}

@Composable
private fun SectionDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
}
