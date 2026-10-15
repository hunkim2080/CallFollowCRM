package com.detailline.callfollowcrm.presentation.screen.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.domain.settlement.SettlementCalc
import com.detailline.callfollowcrm.presentation.component.AddressSearchDialog
import com.detailline.callfollowcrm.presentation.component.AppSheet
import com.detailline.callfollowcrm.presentation.component.SiteAddressField
import com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.joinDongHo

/**
 * ➕ **새 시공 잡기 시트** — 프로토 artifact/32G2BkhhFkdE9J5Wj8u4JC 의 `#s_newjob` 그대로:
 *   제목 「새 시공 잡기」 · 「이 고객(이름)에 **N차 시공**을 쌓아요. 기존 건은 안 건드려요.」
 *   · [시공일 · 눌러서 날짜 고르기 ＋] · [현장 주소 · 주소 찾기 ＋] · [총 금액 · 예: 90 · 만원] · [취소] [새 시공 등록]
 *
 *   (2026-10-15 사장님: "새 시공 잡기 눌러도 주소 입력하는 곳이 안 보임" — 전엔 날짜 달력만 열렸다)
 *
 *   · 시트 틀은 공용 [AppSheet](스크림·그랩·키보드/네비바 여백·스크롤 내장).
 *   · 시공일은 **화면의 기간 달력(datePickerOpen · addingNewJob)** 을 그대로 쓴다 — 달력을 또 만들지 않는다(§12).
 *     고른 날짜는 화면 상태(newJobStart/newJobDays)로 돌아와 이 시트에 보인다.
 *   · 주소 칸은 **앱 공용 [SiteAddressField]**(검색 + 동·호) — 주소 받는 창은 전부 이 부품(2026-09-23 사장님).
 *   · 저장은 호출부 `addNewJob(start, days, address, totalWon)` — 주소·총액이 **그 새 건**에 들어간다.
 */
@Composable
internal fun NewJobSheet(
    customerName: String?,
    /** 쌓일 차수 — 취소 안 한 건 수 + 1. */
    nth: Int,
    start: Long?,
    days: Int,
    onPickDate: () -> Unit,
    onRegister: (address: String?, totalWon: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var address by remember { mutableStateOf("") }
    var dong by remember { mutableStateOf("") }
    var ho by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = start != null || address.isNotBlank() || totalText.isNotBlank()
    val tryDismiss = { if (dirty) confirmDiscard = true else onDismiss() }
    val totalWon = SettlementCalc.wonFromMan(totalText.toLongOrNull(), null)

    if (showSearch) {
        AddressSearchDialog(
            onPicked = { picked -> address = picked; showSearch = false },
            onDismiss = { showSearch = false }
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = AppTheme.colors.surface,
            tonalElevation = 0.dp,
            title = { Text("적어둔 내용을 버릴까요?", style = AppType.headline, color = AppTheme.colors.text) },
            text = { Text("등록하지 않고 닫으면 날짜·주소·금액이 사라져요.", style = AppType.body, color = AppTheme.colors.textSub) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text("계속 적기", style = AppType.label, color = AppTheme.colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false; onDismiss() }) {
                    Text("버리기", style = AppType.label, color = AppTheme.colors.unpaidText)
                }
            }
        )
    }

    AppSheet(onDismiss = tryDismiss) {
        Text("새 시공 잡기", style = AppType.title, color = AppTheme.colors.text)
        Spacer(Modifier.height(AppSpace.s4))
        Text(
            buildAnnotatedString {
                append(if (customerName.isNullOrBlank()) "이 고객에 " else "이 고객(${customerName})에 ")
                withStyle(SpanStyle(fontWeight = AppType.headline.fontWeight, color = AppTheme.colors.text)) { append("${nth}차 시공") }
                append("을 쌓아요. 기존 건은 안 건드려요.")
            },
            style = AppType.body, color = AppTheme.colors.textSub
        )
        Spacer(Modifier.height(AppSpace.s16))
        // ① 시공일 — 누르면 화면의 기간 달력. 프로토 `.fld` 알약.
        Row(
            Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
                .clickable(onClick = onPickDate)
                .padding(horizontal = AppSpace.s16, vertical = AppSpace.s12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("시공일", style = AppType.body, color = AppTheme.colors.textSub, modifier = Modifier.width(72.dp))
            Text(
                if (start != null) DateTimeUtils.formatShortKoreanDate(start) + DateTimeUtils.workPeriodSuffix(start, days)
                else "눌러서 날짜 고르기",
                style = AppType.headline,
                color = if (start != null) AppTheme.colors.text else AppTheme.colors.textHint,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(AppSpace.s8))
            Text(if (start != null) "변경" else "＋", style = AppType.label, color = AppTheme.colors.primaryText)
        }
        Spacer(Modifier.height(AppSpace.s12))
        // ② 현장 주소 — 공용 주소 부품(검색 → 동·호).
        Text("현장 주소", style = AppType.label, color = AppTheme.colors.textSub, modifier = Modifier.padding(start = AppSpace.s4))
        Spacer(Modifier.height(AppSpace.s4))
        SiteAddressField(
            address = address, dong = dong, ho = ho,
            onSearch = { showSearch = true },
            onDong = { dong = it }, onHo = { ho = it }
        )
        Spacer(Modifier.height(AppSpace.s12))
        // ③ 총 금액 — 만원. 계약금·잔금은 등록 뒤 카드의 금액 창에서(안내형).
        Row(
            Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
                .padding(horizontal = AppSpace.s16, vertical = AppSpace.s12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("총 금액", style = AppType.body, color = AppTheme.colors.textSub, modifier = Modifier.width(72.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (totalText.isEmpty()) Text("예: 90", style = AppType.headline, color = AppTheme.colors.textHint)
                BasicTextField(
                    value = totalText,
                    onValueChange = { v -> totalText = v.filter { it.isDigit() }.take(9) },
                    singleLine = true,
                    textStyle = AppType.headline.copy(color = AppTheme.colors.text),
                    cursorBrush = SolidColor(AppTheme.colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandsCommaTransformation,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(AppSpace.s8))
            Text("만원", style = AppType.label, color = AppTheme.colors.primaryText)
        }
        if (start == null) {
            Spacer(Modifier.height(AppSpace.s8))
            Text("시공일을 고르면 등록할 수 있어요", style = AppType.caption, color = AppTheme.colors.textHint)
        }
        Spacer(Modifier.height(AppSpace.s16))
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.s8)) {
            TossSecondaryButton(text = "취소", onClick = tryDismiss, modifier = Modifier.weight(1f))
            TossPrimaryButton(
                text = "새 시공 등록",
                enabled = start != null,
                onClick = {
                    val tail = joinDongHo(dong, ho)
                    val full = (address.trim() + if (tail.isBlank()) "" else " $tail").trim()
                    onRegister(full.takeIf { it.isNotBlank() }, totalWon)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
