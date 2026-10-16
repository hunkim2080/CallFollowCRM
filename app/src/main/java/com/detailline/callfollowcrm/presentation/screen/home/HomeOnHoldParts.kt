package com.detailline.callfollowcrm.presentation.screen.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.domain.customer.OnHold
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneKey
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * ⏸️ 상담함 [보류] 칩 본문 — 프로토 artifact/D4EwQSrXk4TFPzc9gjW6Cs 그대로. (2026-10-16 사장님)
 *
 *   「계약금은 받았는데 고객이 날짜를 미룬 손님」을 담는 자리. 전엔 이 손님이 어느 칸에도 없어 **잊혔다**.
 *   · 맨 위 **안전망 넛지 띠**(`.nudge`) — 3주째 조용한 사람 **한 명**만(OnHold.quietest). 어수선하지 않게.
 *   · 손님 카드(`.card`) — 아바타(주황 테두리)·「보류」 딱지·초록 「계약금 N 받음」·📝 이유 메모·「N주째 연락 없음」·[일정 잡기][안부 문자]
 *
 *   손으로 적은 값 0 — 색·글자·둥글기는 전부 토큰(AppTheme/AppType/AppShape). 판정·주 수는 OnHold 한 곳.
 */

/** 조용해진 시점 — 마지막 연락(없으면 통화 기록 시각)과 보류 시작 중 나중 것. 셈은 OnHold.quietSince. */
private fun quietSinceOf(it: HomeItem): Long = OnHold.quietSince(
    lastActivityMs = it.lastActivityMs.takeIf { ms -> ms > 0L } ?: it.record.endedAt,
    onHoldAt = it.customer?.onHoldAt ?: 0L
)

/**
 * [보류] 칩 본문 한 묶음 — 맨 위 안전망 띠(3주째 조용한 **한 명**, OnHold.quietest) + 보류 카드들.
 *   가장 오래 조용한 사람이 위 — 띠가 가리키는 사람이 바로 아래 보인다. HomeScreen 의 LazyColumn 이 부른다.
 */
internal fun LazyListScope.onHoldSection(
    holdItems: List<HomeItem>,
    nowMs: Long,
    onOpenChat: (phone: String, customerId: Long?) -> Unit,
    onSchedule: (customerId: Long) -> Unit
) {
    if (holdItems.isEmpty()) return
    val holdSorted = holdItems.sortedBy { quietSinceOf(it) }
    val nudge = OnHold.quietest(holdSorted, nowMs) { quietSinceOf(it) }
    if (nudge != null) {
        item(key = "onhold-nudge") {
            val nc = nudge.customer!!
            OnHoldNudgeBand(
                name = nc.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(nc.phoneNumber),
                quietWeeks = OnHold.quietWeeks(quietSinceOf(nudge), nowMs),
                onSms = { onOpenChat(nudge.record.phoneNumber, nc.id) }
            )
        }
    }
    items(holdSorted, key = { "onhold-" + PhoneKey.of(it.record.phoneNumber) }) { h ->
        val hc = h.customer!!
        OnHoldCard(
            c = hc, quietSinceMs = quietSinceOf(h), nowMs = nowMs,
            onSchedule = { onSchedule(hc.id) },
            onSms = { onOpenChat(h.record.phoneNumber, hc.id) }
        )
    }
}

/** 프로토 `.nudge` — "<b>김철수</b>님 <b>3주째</b> 조용해요. 안부 한번 물어볼까요? [안부 문자]" */
@Composable
internal fun OnHoldNudgeBand(name: String, quietWeeks: Int, onSms: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .clip(AppShape.lg)
            .background(c.cautionBg)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(name) }
                append("님 ")
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("${quietWeeks}주째") }
                append(" 조용해요. 안부 한번 물어볼까요?")
            },
            style = AppType.body, color = c.cautionText,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(11.dp))
        Text(
            "안부 문자",
            style = AppType.label, color = c.cautionText,
            modifier = Modifier
                .clip(AppShape.sm)
                .background(c.surface)
                .border(1.dp, c.caution, AppShape.sm)
                .clickable(onClick = onSms)
                .padding(horizontal = 13.dp, vertical = 8.dp)
        )
    }
}

/**
 * 프로토 `.card` — 보류 손님 한 장.
 *   @param quietSinceMs 조용해진 시점(OnHold.quietSince) — 「N주째 연락 없음」 줄은 3주 넘었을 때만(프로토: 1번 카드만 있다).
 */
@Composable
internal fun OnHoldCard(
    c: CustomerEntity,
    quietSinceMs: Long,
    nowMs: Long,
    onSchedule: () -> Unit,
    onSms: () -> Unit
) {
    val col = AppTheme.colors
    val name = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber)
    val heldOn = c.onHoldAt?.let { DateTimeUtils.formatMonthDay(it) + " 보류" }
    val when_ = listOfNotNull(shortAddr(c.address).takeIf { !c.address.isNullOrBlank() }, heldOn).joinToString(" · ")
    val depositWon = c.depositAmount ?: 0L
    val reason = c.onHoldReason?.takeIf { it.isNotBlank() }
    val quietWeeks = OnHold.quietWeeks(quietSinceMs, nowMs)
    TossCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpace.s16)) {
        Column {
            // 머리 — 아바타(주황 테두리 = WAITING 색) + 이름/언제 + 「보류」 딱지
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name = c.name, index = 0, status = InboxAvatarStatus.WAITING)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = col.text, maxLines = 1)
                    if (when_.isNotBlank()) {
                        Text(when_, style = AppType.caption, color = col.textHint)
                    }
                }
                Text(
                    "보류",
                    style = AppType.micro, color = col.cautionText,
                    modifier = Modifier.clip(AppShape.pill).background(col.cautionBg).padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.height(11.dp))
            // 「계약금 30만원 받음」 초록 — 돈 묶인 손님이 한눈에. · 일정 미정
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    depositWon > 0L && c.depositPaidAt != null ->
                        Text("계약금 ${shortWon(depositWon)} 받음", style = AppType.body, fontWeight = FontWeight.ExtraBold, color = col.doneText)
                    depositWon > 0L ->
                        Text("계약금 ${shortWon(depositWon)} 약속", style = AppType.body, fontWeight = FontWeight.ExtraBold, color = col.cautionText)
                    else ->
                        Text("계약금 미입력", style = AppType.body, fontWeight = FontWeight.Bold, color = col.textHint)
                }
                Text(" · 일정 미정", style = AppType.body, color = col.textSub)
            }
            // 이유 메모 — 적었을 때만. 프로토의 글머리 메모 이모지는 **앱이 그리는 아이콘**으로(브랜드 북: 폰마다 다르게 그려진다).
            if (reason != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(AppShape.md).background(col.surfaceMuted).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.EditNote, contentDescription = null, tint = col.textHint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(reason, style = AppType.body, color = col.textSub, modifier = Modifier.weight(1f))
                }
            }
            // · 3주째 연락 없음 — 3주 넘었을 때만(안전망과 같은 잣대)
            if (OnHold.needsNudge(quietSinceMs, nowMs)) {
                Spacer(Modifier.height(9.dp))
                Text("· ${quietWeeks}주째 연락 없음", style = AppType.caption, fontWeight = FontWeight.Bold, color = col.unpaidText)
            }
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.s8)) {
                Box(
                    Modifier.weight(1f).clip(AppShape.md).background(col.primary).clickable(onClick = onSchedule).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("일정 잡기", style = AppType.headline, color = col.textOnPrimary, textAlign = TextAlign.Center) }
                Box(
                    Modifier.weight(1f).clip(AppShape.md).background(col.surface).border(1.dp, col.line, AppShape.md)
                        .clickable(onClick = onSms).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("안부 문자", style = AppType.headline, color = col.textSub, textAlign = TextAlign.Center) }
            }
        }
    }
}
