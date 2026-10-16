package com.detailline.callfollowcrm.presentation.screen.spam

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.SpamPhoneEntity
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSize
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.util.PhoneKey
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 밀어서 찍은 번호(스팸·지인) 목록의 **줄들 — 그리는 곳은 여기 한 곳.** (2026-10-16, CLAUDE.md §12)
 *   「스팸·광고 거르기」([SpamHubScreen] ③ 차단한 번호) 와 「지인 번호」([SpamListScreen]) 가 같이 쓴다.
 *   풀기(unmark) + 지인이면 바구니도 같이 풀기 + 토스트까지 여기서 — 화면마다 다시 짜지 않는다.
 *
 *   줄 사이는 가는 선, 카드(흰 바탕)는 **부르는 쪽**이 그린다(묶음 하나 = 카드 하나, 2026-09-22 규칙).
 *
 * @param numberFirst   true = 번호가 큰 글자, 이름은 그 아래(프로토 「차단한 번호」 모양). false = 이름 먼저(지인 목록 옛 모양 그대로).
 * @param outlinedAction true = 테두리만 있는 회색 버튼(프로토 「풀기」). false = 연한 파랑 알약(지인 목록 옛 모양 그대로).
 */
@Composable
internal fun SpamPhoneRows(
    container: AppContainer,
    entries: List<SpamPhoneEntity>,
    actionLabel: String,
    undoToast: String,
    numberFirst: Boolean,
    outlinedAction: Boolean
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        entries.forEachIndexed { idx, entry ->
            if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.line))
            SpamPhoneRow(
                entry = entry,
                actionLabel = actionLabel,
                numberFirst = numberFirst,
                outlinedAction = outlinedAction,
                onUnmark = {
                    scope.launch {
                        container.spamPhoneRepository.unmark(entry.phoneSuffix)
                        // 👥 지인은 바구니도 같이 푼다 — 안 풀면 문자함 [광고] 로 간다. (2026-10-02)
                        if (entry.kind == "personal") {
                            container.threadBucketRepository.releaseFriend(entry.phoneSuffix)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, undoToast, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SpamPhoneRow(
    entry: SpamPhoneEntity,
    actionLabel: String,
    numberFirst: Boolean,
    outlinedAction: Boolean,
    onUnmark: () -> Unit
) {
    val isPersonal = entry.kind == "personal"
    val numberText = when {
        entry.phoneNumber.isNotBlank() -> PhoneNumberFormatter.format(entry.phoneNumber)
        entry.phoneSuffix.length >= 8 ->
            PhoneKey.of(entry.phoneSuffix).let { "끝 ${it.substring(0, 4)}-${it.substring(4)}" }
        else -> "끝 ${entry.phoneSuffix}"
    }
    val name = entry.displayName?.takeIf { it.isNotBlank() }
    val primary = if (numberFirst) numberText else (name ?: numberText)
    val secondary = if (numberFirst) name else name?.let { numberText }
    val iconBg = if (isPersonal) AppTheme.colors.categoryBg else AppTheme.colors.unpaidBg
    val iconTint = if (isPersonal) AppTheme.colors.category else AppTheme.colors.unpaid

    Row(
        Modifier.fillMaxWidth().padding(horizontal = AppSpace.s12, vertical = AppSpace.s12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(AppSize.avatar).background(iconBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPersonal) Icons.Filled.Person else Icons.Filled.Block,
                null, tint = iconTint, modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(AppSpace.s12))
        Column(Modifier.weight(1f)) {
            Text(primary, style = AppType.headline, fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
            if (secondary != null) {
                Spacer(Modifier.height(AppSpace.s2))
                Text(secondary, style = AppType.label, color = AppTheme.colors.textSub)
            }
        }
        if (outlinedAction) {
            Text(
                actionLabel,
                style = AppType.label, fontWeight = FontWeight.Bold, color = AppTheme.colors.textSub,
                modifier = Modifier
                    .clip(AppShape.sm)
                    .border(1.dp, AppTheme.colors.line, AppShape.sm)
                    .clickable(onClick = onUnmark)
                    .padding(horizontal = AppSpace.s12, vertical = AppSpace.s8)
            )
        } else {
            Text(
                actionLabel,
                style = AppType.label, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryText,
                modifier = Modifier
                    .clip(AppShape.pill)
                    .background(AppTheme.colors.primaryBg)
                    .clickable(onClick = onUnmark)
                    .padding(horizontal = AppSpace.s16, vertical = AppSpace.s8)
            )
        }
    }
}
