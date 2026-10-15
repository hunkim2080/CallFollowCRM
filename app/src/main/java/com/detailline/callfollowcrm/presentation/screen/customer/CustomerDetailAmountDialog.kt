package com.detailline.callfollowcrm.presentation.screen.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.detailline.callfollowcrm.domain.settlement.SettlementCalc
import com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.util.ForceDialogResize

/**
 * 💰 **금액 입력 창 — 안내형.** 프로토 artifact/32G2BkhhFkdE9J5Wj8u4JC 의 `#s_money`(두 칸 + 저장) 를
 *   사장님 지시(2026-10-15)대로 **계약금 → 총 금액 → 잔금 자동** 순서로.
 *   사장님: "계약금 받았으면 입력. 총 받아야 할 돈 얼마? 이렇게 해서 잔금은 그럼 얼마인가요? 해서 금액 계산되게."
 *
 *   · 전엔 「총금액」「계약금」을 **한 칸씩 따로** 여는 창이었다 → 한 화면에서 둘을 적고 잔금을 바로 본다.
 *   · 셈은 [SettlementCalc.planAmounts] **한 곳**(계약금>총액 막기 · 0원=지움 · 잔금=총액−계약금) — 카드의 잔금과 같은 자.
 *   · 만원 미만 절삭 방지([SettlementCalc.wonFromMan]) — 안 건드린 칸은 원래 원 값 그대로.
 *   · 적어둔 걸 **조용히 버리지 않는다** — 바꿔놓고 닫으면 한 번 되묻는다(2026-09-18 사장님 "금액 수정까지 했었거든? 근데 날아갔네").
 *   저장 경로는 호출부(setJobAmounts / setTotalAmount·setDepositAmount) 그대로 — 창은 숫자만 돌려준다.
 */
@Composable
internal fun AmountInputDialog(
    initialTotalWon: Long?,
    initialDepositWon: Long?,
    /** 어느 칸에서 열었나 — true 면 계약금 칸에 커서, 아니면 총 금액 칸. */
    focusDeposit: Boolean,
    onSave: (totalWon: Long?, depositWon: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    fun manText(won: Long?): String = if ((won ?: 0L) > 0L) (won!! / 10_000L).toString() else ""
    var depositText by remember { mutableStateOf(manText(initialDepositWon)) }
    var totalText by remember { mutableStateOf(manText(initialTotalWon)) }
    val plan = SettlementCalc.planAmounts(
        totalWon = SettlementCalc.wonFromMan(totalText.toLongOrNull(), initialTotalWon),
        depositWon = SettlementCalc.wonFromMan(depositText.toLongOrNull(), initialDepositWon)
    )
    val changed = depositText != manText(initialDepositWon) || totalText != manText(initialTotalWon)
    var confirmDiscard by remember { mutableStateOf(false) }
    val tryDismiss = { if (changed) confirmDiscard = true else onDismiss() }
    val save = { if (plan.canSave) onSave(plan.totalWon, plan.depositWon) }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = AppTheme.colors.surface,
            tonalElevation = 0.dp,
            title = { Text("적어둔 금액을 버릴까요?", style = AppType.headline, color = AppTheme.colors.text) },
            text = {
                Text(
                    "저장하지 않고 닫으면 적어둔 금액이 사라져요.",
                    style = AppType.body, color = AppTheme.colors.textSub
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; save() }, enabled = plan.canSave) {
                    Text("저장할게요", style = AppType.label, color = AppTheme.colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false; onDismiss() }) {
                    Text("버리기", style = AppType.label, color = AppTheme.colors.unpaidText)
                }
            }
        )
    }

    val depositFocus = remember { FocusRequester() }
    val totalFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { if (focusDeposit) depositFocus.requestFocus() else totalFocus.requestFocus() }
    }

    Dialog(onDismissRequest = tryDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(AppShape.lg).background(AppTheme.colors.surface).padding(AppSpace.s20)
        ) {
            ForceDialogResize()
            Text("금액 입력", style = AppType.title, color = AppTheme.colors.text)
            Spacer(Modifier.height(AppSpace.s4))
            Text(
                "계약금 → 총 금액 → 잔금이 자동 계산돼요. 이 건의 돈만요.",
                style = AppType.body, color = AppTheme.colors.textSub
            )
            Spacer(Modifier.height(AppSpace.s16))
            // ① 계약금 — 받았으면 적는다. 없으면 비워둔다(0 = 안 적음).
            //   💵 적으면 **받은 걸로 기록**된다(2026-10-15 사장님 결정 Q3 — 규칙은 SettlementCalc.depositPaidAfterSave). 잔금은 별개.
            ManwonField(
                label = "계약금", text = depositText, hint = "받은 돈",
                onChange = { depositText = it }, focus = depositFocus
            )
            Spacer(Modifier.height(AppSpace.s4))
            Text(
                "계약금을 적으면 받은 걸로 기록돼요. 지우면 풀려요.",
                style = AppType.caption, color = AppTheme.colors.textHint,
                modifier = Modifier.padding(start = AppSpace.s4)
            )
            Spacer(Modifier.height(AppSpace.s8))
            // ② 총 받을 돈
            ManwonField(
                label = "총 금액", text = totalText, hint = "받을 돈 전부",
                onChange = { totalText = it }, focus = totalFocus
            )
            Spacer(Modifier.height(AppSpace.s8))
            // ③ 잔금 — 셈 결과만 보여준다(입력 아님). 새 시공 시트와 **같은 줄**(BalancePreviewRow).
            BalancePreviewRow(plan)
            Spacer(Modifier.height(AppSpace.s16))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.s8)) {
                TossSecondaryButton(text = "취소", onClick = tryDismiss, modifier = Modifier.weight(1f))
                TossPrimaryButton(text = "저장", onClick = save, enabled = plan.canSave, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * 잔금 미리보기 줄 — 금액 창과 새 시공 시트가 **같은 줄**을 쓴다(§12). 셈은 [SettlementCalc.planAmounts].
 *   계약금>총액이면 빨간 바탕 + 안내, 총액 없으면 「총 금액을 적으면 계산돼요」.
 */
@Composable
internal fun BalancePreviewRow(plan: com.detailline.callfollowcrm.domain.settlement.AmountPlan) {
    Row(
        Modifier.fillMaxWidth().clip(AppShape.md)
            .background(if (plan.depositOverTotal) AppTheme.colors.unpaidBg else AppTheme.colors.primaryBg)
            .padding(horizontal = AppSpace.s16, vertical = AppSpace.s12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("잔금", style = AppType.body, color = AppTheme.colors.textSub, modifier = Modifier.width(72.dp))
        when {
            plan.depositOverTotal -> Text(
                "계약금이 총 금액보다 커요", style = AppType.headline, color = AppTheme.colors.unpaidText,
                modifier = Modifier.weight(1f)
            )
            plan.balanceWon != null -> Text(
                manwonLabel(plan.balanceWon),
                style = AppType.headline, color = AppTheme.colors.primaryText, modifier = Modifier.weight(1f)
            )
            else -> Text(
                "총 금액을 적으면 계산돼요", style = AppType.body, color = AppTheme.colors.textHint,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 프로토 `.fld` — 회색 알약 한 줄: [라벨 72][숫자 입력][만원]. 숫자만, 천 단위 콤마. 금액 창·새 시공 시트 공용. */
@Composable
internal fun ManwonField(
    label: String,
    text: String,
    hint: String,
    onChange: (String) -> Unit,
    focus: FocusRequester? = null
) {
    Row(
        Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
            .padding(horizontal = AppSpace.s16, vertical = AppSpace.s12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = AppType.body, color = AppTheme.colors.textSub, modifier = Modifier.width(72.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(hint, style = AppType.headline, color = AppTheme.colors.textHint)
            BasicTextField(
                value = text,
                onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(9)) },
                singleLine = true,
                textStyle = AppType.headline.copy(color = AppTheme.colors.text),
                cursorBrush = SolidColor(AppTheme.colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = ThousandsCommaTransformation,
                modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            )
        }
        Spacer(Modifier.width(AppSpace.s8))
        Text("만원", style = AppType.label, color = AppTheme.colors.primaryText)
    }
}
