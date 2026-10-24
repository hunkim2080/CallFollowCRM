package com.detailline.callfollowcrm.presentation.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.domain.chat.SlashCommand
import com.detailline.callfollowcrm.domain.chat.SlashMenu
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 대화창 **"/" 명령 드롭다운** — 입력이 "/접" 처럼 명령 꼴이면 입력줄 위에 뜬다.
 *   누르면 입력을 비우고 **바로 그 기능**(접수서·견적·일정·문구·사진)을 연다. (2026-10-24 · 프로토 artifact/5nhqznJqPqXLtyfeTaHULA)
 *   셈은 [SlashMenu]. ⊕ 메뉴([ComposerActionMenu])와 **같은 기능을 가리키는 다른 입구**다(두 벌 아님).
 */
@Composable
fun SlashCommandMenu(
    input: String,
    onOpenEstimate: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenTemplate: () -> Unit,
    onAttachPhoto: () -> Unit,
    onClearInput: () -> Unit,
) {
    // ⚠️ Composable 안 early return 금지(그룹 꼬여 크래시) — if 로 감싼다.
    val query = SlashMenu.queryOf(input)
    val cmds = if (query == null) emptyList() else SlashMenu.match(query)
    if (cmds.isNotEmpty()) Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .clip(AppShape.lg)
            .background(Color.White)
            .border(1.dp, TossDivider, AppShape.lg)
            .padding(vertical = 4.dp)
    ) {
        Text(
            "명령 — \"/\" 뒤에 이어 치면 걸러져요",
            style = AppType.caption, color = TossTextTertiary,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 4.dp)
        )
        cmds.forEach { cmd ->
            SlashRow(cmd) {
                onClearInput()
                when (cmd) {
                    // 접수서는 견적 작성 안 두 번째 탭이라 둘 다 같은 입구. (ComposerActionMenu 와 동일)
                    SlashCommand.INTAKE, SlashCommand.QUOTE -> onOpenEstimate()
                    SlashCommand.SCHEDULE -> onOpenSchedule()
                    SlashCommand.TEMPLATE -> onOpenTemplate()
                    SlashCommand.PHOTO -> onAttachPhoto()
                }
            }
        }
    }
}

@Composable
private fun SlashRow(cmd: SlashCommand, onClick: () -> Unit) {
    // ⊕ 메뉴와 같은 색(견적=파랑·일정=보라·문구=초록·사진=주황).
    val tint = when (cmd) {
        SlashCommand.INTAKE, SlashCommand.QUOTE -> AppTheme.colors.primary
        SlashCommand.SCHEDULE -> AppTheme.colors.category
        SlashCommand.TEMPLATE -> TossSuccess
        SlashCommand.PHOTO -> AppTheme.colors.caution
    }
    val icon = when (cmd) {
        SlashCommand.INTAKE -> Icons.Default.Assignment
        SlashCommand.QUOTE -> Icons.Default.Description
        SlashCommand.SCHEDULE -> Icons.Default.DateRange
        SlashCommand.TEMPLATE -> Icons.AutoMirrored.Filled.Chat
        SlashCommand.PHOTO -> Icons.Default.Image
    }
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(AppShape.sm).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, cmd.key, tint = tint, modifier = Modifier.size(18.dp)) }
        Column(Modifier.padding(start = 11.dp)) {
            Row {
                Text("/", style = AppType.body, color = TossBlue, fontWeight = FontWeight.ExtraBold)
                Text(cmd.key, style = AppType.body, color = TossTextPrimary, fontWeight = FontWeight.ExtraBold)
            }
            Text(cmd.desc, style = AppType.caption, color = TossTextSecondary)
        }
    }
}
