package com.detailline.callfollowcrm.presentation.screen.outbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.OutboxEntity
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.presentation.util.OutboxWording
import org.json.JSONObject

/**
 * 📮 「아직 못 보낸 것」 — 홈 주황 띠를 누르면 온다. 설계 §7-A. 프로토 사장님 확정(2026-10-04 「ㄱㄱ」).
 *   줄마다: 무엇을·언제·(죽었으면)왜 + [다시 보내기] [그만 보내기].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboxTroubleScreen(
    viewModel: OutboxTroubleViewModel,
    onBack: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    val now = System.currentTimeMillis()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("아직 못 보낸 것", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp)
        ) {
            // 안심 줄 — 「실패」가 아니라 「보류」(§7).
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(13.dp)).background(TossBlueSoft)
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("✅", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        OutboxWording.reassure,
                        color = TossBlueDark, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp
                    )
                }
            }

            if (items.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text("못 보낸 게 없어요 👍", color = TossTextTertiary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            items(items, key = { it.id }) { item ->
                TroubleRow(
                    item = item,
                    dead = viewModel.isDead(item),
                    ageText = OutboxWording.ageText(item.createdAtMs, now),
                    onRetry = { viewModel.retry(item.id) },
                    onDismiss = { viewModel.dismiss(item) }
                )
            }
        }
    }
}

@Composable
private fun TroubleRow(
    item: OutboxEntity,
    dead: Boolean,
    ageText: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val kind = OutboxKind.fromWire(item.kind)
    val title = kind?.let { OutboxWording.label(it) } ?: "보낼 것"
    val hint = payloadHint(kind, item.payloadJson)

    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.dp, TossDivider, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(TossGrayBg),
                contentAlignment = Alignment.Center
            ) { Text(emojiOf(kind), fontSize = 15.sp) }
            Spacer(Modifier.width(9.dp))
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
        }

        val sub = listOfNotNull(hint, ageText).joinToString(" · ")
        if (sub.isNotBlank()) {
            Text(sub, color = TossTextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 39.dp, top = 5.dp))
        }

        // 상태 딱지
        Box(Modifier.padding(start = 39.dp, top = 10.dp)) {
            if (dead) {
                Pill("⚠ " + (item.deadReason ?: "서버가 거절했어요"), TossError, TossError.copy(alpha = 0.10f))
            } else {
                Pill("⏳ " + OutboxWording.stillSending, TossTextSecondary, TossGrayBg)
            }
        }

        // 죽은 것만 버튼 — 미뤄진(재시도 중) 건은 알아서 가니까 버튼 없음.
        if (dead) {
            Row(Modifier.padding(start = 39.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(11.dp)).background(TossBlue).clickable(onClick = onRetry)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) { Text("다시 보내기", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.clip(RoundedCornerShape(11.dp)).background(TossGrayBg).clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) { Text("그만 보내기", color = TossTextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(9.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun emojiOf(kind: OutboxKind?): String = when (kind) {
    OutboxKind.COLLAB_ADDRESS -> "🏠"
    OutboxKind.COLLAB_RESCHEDULE -> "📅"
    OutboxKind.COLLAB_END -> "🤝"
    OutboxKind.SITE_PHOTO -> "📷"
    null -> "📮"
}

/** payload 에서 보여줄 만한 짧은 단서(있을 때만). 없으면 null. */
private fun payloadHint(kind: OutboxKind?, payloadJson: String): String? = runCatching {
    val o = JSONObject(payloadJson)
    when (kind) {
        OutboxKind.COLLAB_ADDRESS -> o.optString("label").ifBlank { o.optString("addr") }.takeIf { it.isNotBlank() }
        OutboxKind.COLLAB_RESCHEDULE -> o.optString("timeLabel").takeIf { it.isNotBlank() }
        else -> null
    }
}.getOrNull()
