@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.detailline.callfollowcrm.presentation.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
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
import com.detailline.callfollowcrm.service.SummaryStatus
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * 통화 직후 WindowManager 에 띄우는 카드. (프로토 triggerPostCall → openCallSummary)
 *
 * - 수신/발신(대화 있음): 통화 요약(✨ 불릿) + AI 후속 문자 초안 + [다듬기][문자 보내기].
 *     요약은 통화 끝나고 몇 초~십몇 초 걸리므로 "정리 중…" → 준비되면 자동으로 채워짐(스트리밍).
 *     (에이닷이 녹음/통화내용 저장 → 자동 스캔 → 서버 요약. 공유 버튼 불필요.)
 * - 부재중(대화 없음): 기존 자동 응답 카운트다운 + 템플릿.
 */
@Composable
fun PostCallCard(
    state: CardState,
    onCancelAutoReply: () -> Unit,
    onPickTemplate: (MessageTemplateEntity) -> Unit,
    onCancelManualSend: () -> Unit,
    onEditDraft: (String) -> Unit,
    onToggleEditDraft: () -> Unit,
    onSendDraft: () -> Unit,
    onAppendTemplate: (MessageTemplateEntity) -> Unit,
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
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HeaderRow(state, onClose)

                    if (state.isMissed) {
                        // 부재중 — 대화 없음 → 자동 응답 / 템플릿 (기존 흐름 유지).
                        if (state.mode == CardMode.AUTO_REPLY || state.sendStatus == SendStatus.SENT || state.sendStatus == SendStatus.FAILED) {
                            AutoReplyBanner(state, onCancel = onCancelAutoReply)
                        }
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
                    } else {
                        // 수신/발신 — 통화 요약 + 후속 문자.
                        CallSummarySection(
                            state = state,
                            onEditDraft = onEditDraft,
                            onToggleEditDraft = onToggleEditDraft,
                            onSendDraft = onSendDraft,
                            onPickTemplate = onPickTemplate,
                            onCancelManualSend = onCancelManualSend,
                            onAppendTemplate = onAppendTemplate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallSummarySection(
    state: CardState,
    onEditDraft: (String) -> Unit,
    onToggleEditDraft: () -> Unit,
    onSendDraft: () -> Unit,
    onPickTemplate: (MessageTemplateEntity) -> Unit,
    onCancelManualSend: () -> Unit,
    onAppendTemplate: (MessageTemplateEntity) -> Unit
) {
    when (state.summaryStatus) {
        SummaryStatus.LOADING -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = TossBlueSoft
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = TossBlue)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("✨ 통화 정리 중…", color = TossBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        // "어쩌라는 거냐" 방지: 안 기다려도 되고, 닫아도 결과는 통화방(채팅)에 저장됨을 명시.
                        Text("기다리지 않으셔도 돼요 — 위 ✕ 로 닫으셔도 요약은 통화방(채팅)에 저장돼요", color = TossTextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }

        SummaryStatus.READY -> {
            // ✨ 통화 요약 불릿
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = TossBlueSoft
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                    Text("✨ 통화에서 이런 얘기가 오갔어요", color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Spacer(Modifier.height(8.dp))
                    state.summaryBullets.forEach { b ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("•  ", color = TossTextSecondary, fontSize = 13.sp)
                            Text(b, color = TossTextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
                        }
                    }
                }
            }
            Text("📞 에이닷 통화요약을 바탕으로 막내가 정리했어요", color = TossTextTertiary, fontSize = 11.sp)

            // 고객에게 보낼 후속 문자 — 초안 있으면 다듬기/보내기, 없으면 템플릿 폴백.
            if (state.draftText.isNotBlank() || state.draftEditing) {
                Text("고객에게 보낼 후속 문자", color = TossTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (state.draftEditing) {
                    OutlinedTextField(
                        value = state.draftText,
                        onValueChange = onEditDraft,
                        singleLine = false,
                        keyboardOptions = KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
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
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = TossGrayBg
                    ) {
                        Text(
                            state.draftText,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                            color = TossTextPrimary, fontSize = 13.5.sp, lineHeight = 20.sp
                        )
                    }
                }
                when {
                    state.draftSent -> Text("✓ 보냈어요", color = TossSuccess, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    state.draftFailed -> Text("⚠ 발송 실패 — 다시 시도해주세요", color = TossError, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .border(1.dp, TossDivider, RoundedCornerShape(12.dp))
                                .clickable(onClick = onToggleEditDraft).padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(if (state.draftEditing) "수정 완료" else "다듬기", color = TossTextSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(TossBlue).clickable(onClick = onSendDraft).padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("문자 보내기", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
                    }
                }
                // 빠른 템플릿 — 눌러서 후속 문자 하단에 붙이기. (2026-06-14 사장님)
                if (!state.draftSent) {
                    if (state.manualTemplates.isNotEmpty()) {
                        Text("빠른 템플릿 (눌러서 붙이기)", fontSize = 12.sp, color = TossTextTertiary, fontWeight = FontWeight.SemiBold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.manualTemplates.chunked(2).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowItems.forEach { tpl ->
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = TossBlueSoft,
                                            onClick = { onAppendTemplate(tpl) }
                                        ) {
                                            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                                                Text(tpl.title, color = TossBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            "설정 → 후속 빠른 액션에서 템플릿을 골라두면 여기서 눌러 붙일 수 있어요",
                            fontSize = 11.sp, color = TossTextTertiary, lineHeight = 16.sp
                        )
                    }
                }
            } else {
                // 요약은 됐는데 후속 문자 초안이 없는 경우 → 템플릿에서 골라 보내기.
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

        SummaryStatus.UNAVAILABLE -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = TossGrayBg
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("통화 요약을 못 가져왔어요", color = TossTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "에이닷 자동 저장·폴더 연결을 확인해주세요. 아래에서 직접 골라 보낼 수 있어요.",
                        color = TossTextTertiary, fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
            }
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

@Composable
private fun HeaderRow(state: CardState, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = PhoneNumberFormatter.format(state.phoneNumber),
                style = MaterialTheme.typography.titleLarge,
                color = TossTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (state.isMissed) "부재중 통화 · 첫 연락" else "방금 통화 종료",
                style = MaterialTheme.typography.labelMedium,
                color = TossTextTertiary
            )
        }
        // 닫기 — 작은 X 아이콘만 있으면 "눌리는지" 헷갈림(사장님). 글자 라벨 단 회색 알약으로 명확하게.
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = TossGrayBg,
            onClick = onClose
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Close, contentDescription = "닫기", tint = TossTextSecondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("닫기", color = TossTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
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
