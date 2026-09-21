package com.detailline.callfollowcrm.presentation.theme

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "AI 가 한 것" 표시 — **글자 ✨ 가 아니라 앱이 그리는 아이콘.** (2026-09-21 사장님)
 *
 * 왜: 갤럭시에서 ✨ 는 **남색 밤하늘 타일**로 그려진다. 파란 글씨 옆에 까만 네모가 붙고,
 * 목록에선 줄마다 붙어 화면이 어두워졌다. 이모지는 폰마다 그림이 다르다는 그 문제다.
 *
 * 뒤에 글자가 오므로 [gap] 만큼 간격도 같이 넣는다. Row 안에서 쓴다.
 */
@Composable
fun AiMark(tint: Color, size: Dp = 15.dp, gap: Dp = 6.dp) {
    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    Spacer(Modifier.width(gap))
}
