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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.AppNotificationEntity
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 🔔 알림 센터 — 협업 현장 소식 한눈에. (2026-10-13 사장님)
 *   시간순(최신 위). 탭하면 그 현장으로 이동하며 **읽음(회색)으로 남긴다** — 삭제 안 함(까먹지 않게).
 *   [모두 읽기]. 🔔 배지 = 안 읽은 수. 푸시를 지워도 여기 남는다("뭐 왔었지" 해결).
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
            // 삭제가 아니라 **읽음** — 지우면 "뭐 왔었지" 하고 까먹어서. (2026-10-13 사장님)
            if (items.any { it.readAt == null }) {
                Text(
                    "모두 읽기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { viewModel.markAllRead() }.padding(6.dp)
                )
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 민낯 이모지 대신 협업 빈 화면과 같은 얼굴 — 둥근 타일 + 사람들 아이콘. (2026-10-17)
                    Box(
                        Modifier.size(46.dp).clip(AppShape.md).background(AppTheme.colors.primaryBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Groups, null, tint = AppTheme.colors.primary, modifier = Modifier.size(23.dp))
                    }
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
                            viewModel.markRead(n.id)   // 삭제 아님 — 읽음(회색)으로 남긴다
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotiRow(n: AppNotificationEntity, onTap: () -> Unit) {
    // 🤏 상담함보다 촘촘하게(2줄). **읽으면 회색**으로 남긴다 — 삭제 안 함(까먹지 않게). (2026-10-13 사장님)
    //   안 읽음/읽음은 **진한 글씨 ↔ 회색**으로 이미 갈려서 파란 점은 안 쓴다(투머치). (2026-10-13 사장님)
    val read = n.readAt != null
    Row(
        Modifier.fillMaxWidth().background(AppTheme.colors.surface).clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(TossGrayBg).alpha(if (read) 0.55f else 1f),
            contentAlignment = Alignment.Center
        ) { Text(iconOf(n.type), fontSize = 16.sp) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.title, fontSize = 13.5.sp,
                    fontWeight = if (read) FontWeight.Medium else FontWeight.Bold,
                    color = if (read) AppTheme.colors.textHint else AppTheme.colors.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text(DateTimeUtils.formatShort(n.createdAt), fontSize = 11.sp, color = AppTheme.colors.textHint, fontWeight = FontWeight.Medium)
            }
            if (!n.body.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(n.body, fontSize = 12.sp,
                    color = if (read) AppTheme.colors.textHint else AppTheme.colors.textSub,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
    type.startsWith("collab") -> "👥"   // 협업 표식 = 사람들. 푸시 제목 말머리(NotificationHelper.titleEmoji)와 같은 글자. (2026-10-17)
    else -> "🔔"
}
