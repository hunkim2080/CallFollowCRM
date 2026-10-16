package com.detailline.callfollowcrm.presentation.screen.customer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * ⏸️ **일정 보류** 창 — 찍기(이유 입력) / 보류 중이면 메모 고치기·풀기. (2026-10-16 사장님, 프로토 artifact/D4EwQSrXk4TFPzc9gjW6Cs)
 *   「예약 취소」는 계약금까지 백지라, 계약금 받고 날짜만 미룬 손님은 여기로.
 *   되돌리기: 보류 중 창에 「보류 풀기」 — 방금 달력에서 뺀 날짜가 있으면 그 날짜로 되돌린다(기본 UX: 취소·되돌리기).
 *   창은 화면에 한 벌(CustomerDetailScreen 이 띄움) — 카드(JobDetailCard)는 host.onHold 로 열기만 한다(§12).
 */
@Composable
internal fun CustomerHoldDialog(c: CustomerEntity, viewModel: CustomerDetailViewModel, onClose: () -> Unit) {
    val onHoldNow = c.isOnHold
    var holdReason by remember(onHoldNow) { mutableStateOf(if (onHoldNow) c.onHoldReason.orEmpty() else "") }
    val restorable = if (onHoldNow) viewModel.holdRestorableDate() else null
    AlertDialog(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        onDismissRequest = onClose,
        title = {
            Text(
                if (onHoldNow) "보류 중인 손님이에요" else "일정을 보류할까요?",
                fontWeight = FontWeight.Bold, color = TossTextPrimary
            )
        },
        text = {
            Column {
                Text(
                    if (onHoldNow) "날짜를 잡으면 보류는 저절로 풀리고 [시공 대기]로 가요."
                    else "계약은 됐는데 날짜를 미룬 손님을 상담함 [보류] 칸에 모아둬요.\n" +
                        (if (c.scheduledWorkDate != null) "잡혀 있던 날짜는 달력에서 빠지고, 계약금은 그대로 남아요." else "계약금은 그대로 남아요."),
                    style = AppType.body, color = TossTextSecondary
                )
                Spacer(Modifier.height(AppSpace.s12))
                Text("왜 미뤘는지 적어두면 나중에 기억나요 (안 써도 돼요)", style = AppType.caption, color = TossTextTertiary)
                Spacer(Modifier.height(AppSpace.s8))
                OutlinedTextField(
                    value = holdReason,
                    onValueChange = { holdReason = it },
                    placeholder = { Text("예: 와이프 아파서 미룸 — 고객이 다시 연락하기로", color = TossTextTertiary) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tossFieldColors()
                )
                if (onHoldNow) {
                    Spacer(Modifier.height(AppSpace.s12))
                    // 보류 풀기 — 잘못 눌렀을 때. 방금 뺀 날짜가 있으면 되돌리고, 아니면 날짜 없이 풀린다.
                    Text(
                        if (restorable != null) "보류 풀기 — ${DateTimeUtils.formatShortKoreanDate(restorable)} 로 되돌리기"
                        else "보류 풀기 (날짜는 따로 잡기)",
                        style = AppType.label, color = TossError,
                        modifier = Modifier.fillMaxWidth().clip(AppShape.sm)
                            .clickable { onClose(); viewModel.releaseHold(restoreDate = restorable != null) }
                            .padding(vertical = AppSpace.s8),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onClose()
                if (onHoldNow) viewModel.updateHoldReason(holdReason) else viewModel.holdSchedule(holdReason)
            }) {
                Text(if (onHoldNow) "메모 저장" else "보류할게요", color = TossBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(if (onHoldNow) "닫기" else "취소", color = TossTextSecondary)
            }
        }
    )
}
