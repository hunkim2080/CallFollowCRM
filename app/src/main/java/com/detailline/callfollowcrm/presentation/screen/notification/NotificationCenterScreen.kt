package com.detailline.callfollowcrm.presentation.screen.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.AppNotificationEntity
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 🔔 알림 센터 — 새 활동 한눈에. (2026-10-13 사장님)
 *   시간순(최신 위). 탭하면 그 대화·현장·일정으로 이동하며 **그 줄 삭제**. [모두 지우기].
 *   푸시를 지워도 여기 남는다("뭐 왔었지" 해결).
 */
@Composable
fun NotificationCenterScreen(
    viewModel: NotificationCenterViewModel,
    onBack: () -> Unit,
    /** 탭 → 그 알림의 목적지로 이동(네비게이션은 호스트가). */
    onOpen: (AppNotificationEntity) -> Unit,
) {
    val items by viewModel.items.collectAsState()

    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
        Row(
            Modifier.fillMaxWidth().background(AppTheme.colors.surface)
                .statusBarsPadding()
                .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = AppTheme.colors.textSub, modifier = Modifier.size(22.dp)) }
            Text("알림", style = AppType.headline, color = AppTheme.colors.text)
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
                Text(
                    "모두 지우기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { viewModel.clearAll() }.padding(6.dp)
                )
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🤝", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("협업 현장 소식이 여기 쌓여요", style = AppType.body, color = AppTheme.colors.textSub)
                    Spacer(Modifier.height(3.dp))
                    Text("도착·댓글·사진·일정변경·입금 — 상담함에서 안 보이는 것들", style = AppType.caption, color = AppTheme.colors.textHint)
                }
            }
        } else {
            val now = System.currentTimeMillis()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                groupByTime(items, now).forEach { (section, rows) ->
                    item(key = "sec-$section") {
                        Text(
                            section, style = AppType.label, color = AppTheme.colors.textSub,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
                        )
                    }
                    items(rows, key = { it.id }) { n ->
                        NotiRow(n) {
                            onOpen(n)
                            viewModel.dismiss(n.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotiRow(n: AppNotificationEntity, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(AppTheme.colors.surface).clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(TossGrayBg),
            contentAlignment = Alignment.Center
        ) { Text(iconOf(n.type), fontSize = 17.sp) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!n.body.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(n.body, fontSize = 12.sp, color = AppTheme.colors.textSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(3.dp))
            Text(DateTimeUtils.formatShort(n.createdAt), fontSize = 11.sp, color = AppTheme.colors.textHint, fontWeight = FontWeight.Medium)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
}

/** 시간대별 묶음 — 오늘 / 어제 / 지난 7일 / 그 이전. (순수 함수) */
private fun groupByTime(items: List<AppNotificationEntity>, now: Long): List<Pair<String, List<AppNotificationEntity>>> {
    val today = DateTimeUtils.startOfDay(now)
    val yesterday = today - DateTimeUtils.DAY_MS
    val week = today - 6L * DateTimeUtils.DAY_MS
    val buckets = LinkedHashMap<String, MutableList<AppNotificationEntity>>()
    for (n in items) {
        val key = when {
            n.createdAt >= today -> "오늘"
            n.createdAt >= yesterday -> "어제"
            n.createdAt >= week -> "지난 7일"
            else -> "그 이전"
        }
        buckets.getOrPut(key) { mutableListOf() }.add(n)
    }
    return buckets.map { it.key to it.value }
}

private fun iconOf(type: String): String = when {
    type == "new_inquiry" || type == "reply" || type == "general_sms" -> "💬"
    type == "missed_call" -> "📞"
    type == "intake" -> "📋"
    type == "reminder" -> "🧾"
    type == "recurring" -> "🕐"
    type == "balance_due" -> "💰"
    type == "daily_brief" -> "📊"
    type == "install_d1" -> "🏗️"
    type == "call_summary" -> "🎧"
    type.startsWith("collab") -> "🤝"
    else -> "🔔"
}
