package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.util.keyboardOrNavPadding

/** 하단 시트의 둥근 윗모서리(한 곳). */
private val SheetTopShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

/**
 * 🧱 **하단 시트 공용 틀** — 스크림 + 하단 흰 카드 + 그랩 바 + **키보드·네비바 여백·스크롤이 이미 내장**.
 *
 *   왜 공용인가(§12): 시트를 '맨땅에' 만들면 **매번 세 가지(키보드 여백·네비바 여백·스크롤)를 손으로
 *   붙여야** 하고, 하나라도 빠뜨리면 버그가 난다 — 예약 시트에서 스크롤·키보드·네비바가 **연달아 터졌다**
 *   (2026-10-05 사장님). 이 틀에 내용만 넣으면 그 셋을 **빠뜨릴 수가 없다.**
 *
 *   - 바깥 스크롤 **하나**만 둔다(내용이 길면 여기서 스크롤). 안에 또 verticalScroll 을 넣지 말 것 — 중첩 스크롤은
 *     ①드래그 안 먹힘 ②타자 시 커서 안 따라감을 부른다(그 버그를 이 틀이 막는다).
 *   - 바깥(스크림) 탭 = 닫기. 카드 탭 = 안 닫힘.
 *   - 키보드가 뜨면 그만큼, 없으면 네비바만큼 바닥 여백(공용 `keyboardOrNavPadding` = ime∪navigationBars).
 *
 *   새 시트는 전부 이걸 쓴다. (기존 시트도 점차 이걸로 옮긴다.)
 */
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    maxHeight: Int = 680,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(AppTheme.colors.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(SheetTopShape)
                .background(AppTheme.colors.surface)
                // 카드 탭은 스크림(닫기)으로 안 새게 소비.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                // ↓ 세 가지 내장: 네비바/키보드 여백 → 높이 상한 → 바깥 스크롤 하나.
                .keyboardOrNavPadding()
                .heightIn(max = maxHeight.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 18.dp)
        ) {
            // 그랩 바
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                    .clip(AppShape.pill).background(AppTheme.colors.line)
            )
            Spacer(Modifier.height(13.dp))
            content()
        }
    }
}
