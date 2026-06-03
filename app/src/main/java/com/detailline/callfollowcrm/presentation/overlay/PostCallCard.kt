@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.detailline.callfollowcrm.presentation.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.domain.model.LeadHeat
import com.detailline.callfollowcrm.presentation.theme.CallFollowCrmTheme
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.service.CardMode
import com.detailline.callfollowcrm.service.CardState
import com.detailline.callfollowcrm.service.SendStatus
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * 통화 직후 WindowManager 에 띄우는 카드. 두 모드 분기.
 *
 * - AUTO_REPLY 모드: 상단에 자동 발송 카운트다운 영역 + 취소 버튼. 발송되거나 취소되면 영역만
 *   상태로 바뀌고, leadHeat/메모 입력은 계속 가능.
 * - MANUAL_CHOOSE 모드: leadHeat + 메모 + 템플릿 알약 칩. 칩 탭 = 3초 카운트다운 후 발송.
 */
@Composable
fun PostCallCard(
    state: CardState,
    onPickLeadHeat: (LeadHeat) -> Unit,
    onMemoChange: (String) -> Unit,
    onCancelAutoReply: () -> Unit,
    onPickTemplate: (MessageTemplateEntity) -> Unit,
    onCancelManualSend: () -> Unit,
    onSummarize: () -> Unit,
    onClose: () -> Unit
) {
    // 오버레이는 자체 Compose 컨텍스트라 테마를 직접 감싸야 토스 색/폰트 적용된다.
    CallFollowCrmTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 24.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HeaderRow(state, onClose)

                    // 받은 통화(대화 있음) → "통화 정리해서 보내기" (A안). 부재중은 대화 없으니 미노출.
                    if (!state.isMissed) {
                        SummarizeButton(onSummarize)
                    }

                    // 자동 발송 영역 — AUTO_REPLY 모드에서만, 그리고 발송이 끝나기 전까진 카운트다운 진행 중
                    if (state.mode == CardMode.AUTO_REPLY || state.sendStatus == SendStatus.SENT || state.sendStatus == SendStatus.FAILED) {
                        AutoReplyBanner(state, onCancel = onCancelAutoReply)
                    }

                    // 리드 온도 — 항상 노출. 분류된 직후엔 선택된 칩이 강조됨.
                    LeadHeatPicker(state.leadHeat, onPickLeadHeat)

                    // 메모 — 항상 노출. 자동 발송 진행 중에도 입력 가능 (카드는 안 닫히므로).
                    MemoField(state.memo, onMemoChange)

                    // 수동 템플릿 영역 — MANUAL_CHOOSE 모드 일 때만.
                    if (state.mode == CardMode.MANUAL_CHOOSE) {
                        ManualTemplateArea(
                            templates = state.manualTemplates,
                            sendStatus = state.sendStatus,
                            countdownMs = state.countdownMs,
                            pendingTemplateTitle = state.pendingTemplateTitle,
                            onPick = onPickTemplate,
                            onCancel = onCancelManualSend
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummarizeButton(onSummarize: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF7C5CFC))
            .clickable(onClick = onSummarize)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📝 통화 정리해서 보내기", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeaderRow(state: CardState, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier
            .weight(1f)) {
            Text(
                text = PhoneNumberFormatter.format(state.phoneNumber),
                style = MaterialTheme.typography.titleLarge,
                color = TossTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (state.isMissed) "부재중 통화 · 첫 연락" else "수신 통화 · 첫 연락",
                style = MaterialTheme.typography.labelMedium,
                color = TossTextTertiary
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "닫기", tint = TossTextSecondary)
        }
    }
}

@Composable
private fun AutoReplyBanner(state: CardState, onCancel: () -> Unit) {
    val (bg, fg, label, body) = when (state.sendStatus) {
        SendStatus.COUNTING_DOWN -> {
            val secs = (state.countdownMs / 1000.0 + 0.5).toInt().coerceAtLeast(0)
            BannerStyle(
                bg = TossBlueSoft, fg = TossBlue,
                label = "⏱ ${secs}초 뒤 자동 발송",
                body = "\"${state.autoReplyTemplateTitle ?: "기본 응답"}\" 으로 보내요"
            )
        }
        SendStatus.SENT -> BannerStyle(
            bg = Color(0xFFE6F7EE), fg = TossSuccess,
            label = "✓ 자동 응답 발송 완료",
            body = "\"${state.autoReplyTemplateTitle ?: "기본 응답"}\" 메시지가 발송됐어요"
        )
        SendStatus.CANCELLED -> BannerStyle(
            bg = TossGrayBg, fg = TossTextSecondary,
            label = "자동 응답 취소됨",
            body = "아래 템플릿에서 직접 골라 보낼 수 있어요"
        )
        SendStatus.FAILED -> BannerStyle(
            bg = Color(0xFFFDEBEE), fg = TossError,
            label = "⚠ 발송 실패",
            body = "수동으로 다시 시도해주세요"
        )
        SendStatus.IDLE -> return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(body, color = TossTextSecondary, fontSize = 12.sp)
                }
                if (state.sendStatus == SendStatus.COUNTING_DOWN) {
                    TextButton(onClick = onCancel) {
                        Text("취소", color = fg, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (state.sendStatus == SendStatus.COUNTING_DOWN) {
                Spacer(Modifier.height(8.dp))
                val total = 10_000f
                val progress = (state.countdownMs.coerceAtLeast(0L).toFloat() / total).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = fg,
                    trackColor = Color.White
                )
            }
        }
    }
}

@Composable
private fun LeadHeatPicker(selected: LeadHeat?, onPick: (LeadHeat) -> Unit) {
    Column {
        Text(
            "이 고객 어땠어?",
            style = MaterialTheme.typography.labelLarge,
            color = TossTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeatChip(LeadHeat.COLD, selected, onPick)
            HeatChip(LeadHeat.WARM, selected, onPick)
        }
    }
}

@Composable
private fun HeatChip(heat: LeadHeat, selected: LeadHeat?, onPick: (LeadHeat) -> Unit) {
    val isSelected = selected == heat
    val bg = if (isSelected) TossBlueSoft else Color.White
    val border = if (isSelected) TossBlue else TossDivider
    val fg = if (isSelected) TossBlue else TossTextSecondary
    Surface(
        modifier = Modifier
            .border(width = if (isSelected) 1.5.dp else 1.dp, color = border, shape = RoundedCornerShape(999.dp))
            .background(bg, RoundedCornerShape(999.dp)),
        shape = RoundedCornerShape(999.dp),
        color = bg,
        onClick = { onPick(heat) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(heat.emoji, fontSize = 16.sp)
            Spacer(Modifier.size(6.dp))
            Text(heat.label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MemoField(memo: String, onChange: (String) -> Unit) {
    Column {
        Text(
            "메모 (선택)",
            style = MaterialTheme.typography.labelLarge,
            color = TossTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = memo,
            onValueChange = onChange,
            placeholder = { Text("이 고객 한 줄 메모", color = TossTextTertiary) },
            singleLine = false,
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TossBlue,
                unfocusedBorderColor = TossDivider,
                focusedTextColor = TossTextPrimary,
                unfocusedTextColor = TossTextPrimary,
                cursorColor = TossBlue,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
    }
}

@Composable
private fun ManualTemplateArea(
    templates: List<MessageTemplateEntity>,
    sendStatus: SendStatus,
    countdownMs: Long,
    pendingTemplateTitle: String?,
    onPick: (MessageTemplateEntity) -> Unit,
    onCancel: () -> Unit
) {
    Column {
        Text(
            "바로 보내기",
            style = MaterialTheme.typography.labelLarge,
            color = TossTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        when (sendStatus) {
            SendStatus.COUNTING_DOWN -> {
                val secs = (countdownMs / 1000.0 + 0.5).toInt().coerceAtLeast(0)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = TossBlueSoft
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "⏱ ${secs}초 뒤 발송",
                                color = TossBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "\"${pendingTemplateTitle ?: ""}\" 보내요",
                                color = TossTextSecondary, fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = onCancel) {
                            Text("취소", color = TossBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            SendStatus.SENT -> {
                Text("✓ \"${pendingTemplateTitle ?: ""}\" 발송 완료", color = TossSuccess, fontWeight = FontWeight.SemiBold)
            }
            SendStatus.FAILED -> {
                Text("⚠ 발송 실패", color = TossError, fontWeight = FontWeight.SemiBold)
            }
            else -> {
                if (templates.isEmpty()) {
                    Text(
                        "Settings → 후속 빠른 액션에서 템플릿을 골라주세요",
                        color = TossTextTertiary,
                        fontSize = 12.sp
                    )
                } else {
                    // 칩들을 두 줄까지 자연스럽게 배치.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { tpl ->
                                    TemplatePill(tpl, onPick)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplatePill(template: MessageTemplateEntity, onPick: (MessageTemplateEntity) -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = TossBlue,
        onClick = { onPick(template) }
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(template.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

private data class BannerStyle(
    val bg: Color,
    val fg: Color,
    val label: String,
    val body: String
)
