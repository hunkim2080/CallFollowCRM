package com.detailline.callfollowcrm.presentation.screen.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.service.NotificationHelper

/**
 * 더보기 → 알림 소리 (2026-07-10 사장님) — 알림 종류별로 소리를 고르고 미리 들어보기.
 *   소리는 NotificationChannel 에 붙고 채널은 생성 후 불변이라, 바꿀 때 delete+recreate 로 즉시 적용.
 *   슬롯/옵션 데이터는 NotificationHelper 가 소유(화면·적용 공용).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SoundSettingsScreen(prefs: AppPreferences, onBack: () -> Unit) {
    val context = LocalContext.current

    // 화면 나갈 때 미리듣기 정지.
    DisposableEffect(Unit) { onDispose { NotificationHelper.stopPreview() } }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("알림 소리", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        // 어느 슬롯이 지금 어떤 소리인지 — 로컬 상태로 즉시 반영(prefs 저장 + 화면 갱신).
        val selections = remember {
            androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
                NotificationHelper.SOUND_SLOTS.forEach { put(it.key, prefs.notificationSound(it.key, it.defaultRes)) }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "알림 종류마다 소리를 고르고 미리 들어봐요. 소리를 바꾸면 바로 적용돼요.",
                    fontSize = 13.sp, color = TossTextSecondary
                )
            }
            items(NotificationHelper.SOUND_SLOTS, key = { it.key }) { slot ->
                val current = selections[slot.key] ?: slot.defaultRes
                val currentLabel = NotificationHelper.SOUND_OPTIONS.find { it.first == current }?.second ?: "무음"
                SoundSlotCard(
                    slotLabel = slot.label,
                    currentLabel = currentLabel,
                    current = current,
                    onPreview = { res -> NotificationHelper.previewSound(context, res) },
                    onSelect = { res ->
                        selections[slot.key] = res
                        prefs.setNotificationSound(slot.key, res)
                        // 버전 올려 '새 채널'로 재생성 → 새 소리가 실제로 적용됨(옛 채널 버그 우회).
                        NotificationHelper.applySlotSound(context, slot.key)
                        Toast.makeText(context, "소리를 바꿨어요", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundSlotCard(
    slotLabel: String,
    currentLabel: String,
    current: String,
    onPreview: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(slotLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, modifier = Modifier.weight(1f))
            Text(currentLabel, fontSize = 12.sp, color = TossTextTertiary)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotificationHelper.SOUND_OPTIONS.forEach { (res, label) ->
                val selected = res == current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) TossBlue else TossGrayBg)
                        .then(
                            if (selected) Modifier
                            else Modifier.border(1.dp, Color(0xFFE5E8EB), RoundedCornerShape(999.dp))
                        )
                        .clickable { onSelect(res) }
                        .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    // ▶ 미리듣기 — 칩 선택과 별개로 소리만 재생.
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onPreview(res) }
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            "미리듣기",
                            tint = if (selected) Color.White else TossTextSecondary,
                            modifier = Modifier.width(18.dp).height(18.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else TossTextPrimary
                    )
                }
            }
        }
    }
}
