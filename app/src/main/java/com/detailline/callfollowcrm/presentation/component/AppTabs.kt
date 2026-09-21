package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType

/**
 * 화면 안에서 **내용을 바꾸는 탭** — 앱 어디서나 같은 모양. (2026-09-21 사장님)
 *
 * 왜 만들었나: 탭이 화면마다 달랐다 —
 *   고객 정보=흰 바탕+파란 둥근 네모 / 정산=파란 알약 / 리포트=**까만** 알약 /
 *   통계=파란 알약 / 협업 현장=회색 바탕+**흰** 알약. 다섯 화면이 다섯 모양이라
 *   **화면을 옮길 때마다 다른 앱처럼** 보였다.
 *
 * 왜 이 모양인가: **알약은 '고르는 칩'**(상담함의 [전체][오늘 신규]…)에 쓰기로 했다.
 *   탭은 성격이 달라 **둥근 네모**로 구분한다. 네모가 글자 길이에 덜 흔들리기도 한다.
 */
@Composable
fun AppTabs(
    tabs: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppShape.md)
            .background(AppTheme.colors.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tabs.forEachIndexed { i, label ->
            val on = selected == i
            Box(
                Modifier
                    .weight(1f)
                    .clip(AppShape.sm)
                    .background(if (on) AppTheme.colors.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = AppType.label,
                    color = if (on) AppTheme.colors.textOnPrimary else AppTheme.colors.textSub,
                    maxLines = 1
                )
            }
        }
    }
}
