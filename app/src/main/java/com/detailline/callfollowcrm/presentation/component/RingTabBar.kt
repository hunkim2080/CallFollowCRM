package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.navigation.Destinations
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossTabInactive

/**
 * 하단 5탭 네비게이션 바 — 프로토타입(.tabbar) 그대로.
 *   상담함 · 일정 · 정산 · 통계 · 더보기
 *   흰 배경 + 1px 상단 보더 + 활성 파랑 / 비활성 회색(#AEB5BF) + 상담함 미확인 배지.
 */
private data class RingTab(val route: String, val label: String, val icon: ImageVector)

private val RING_TABS = listOf(
    RingTab(Destinations.HOME, "상담함", Icons.AutoMirrored.Filled.Chat),
    RingTab(Destinations.SCHEDULE, "일정", Icons.Filled.CalendarMonth),
    RingTab(Destinations.SETTLEMENT, "정산", Icons.Filled.AccountBalanceWallet),
    RingTab(Destinations.STATS, "통계", Icons.Filled.BarChart),
    RingTab(Destinations.SETTINGS, "더보기", Icons.Filled.GridView)
)

/** 하단 탭바가 보여야 하는 최상위 5개 라우트. */
val RING_TAB_ROUTES: Set<String> = RING_TABS.map { it.route }.toSet()

@Composable
fun RingTabBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    inboxBadge: Int? = null
) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 9.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RING_TABS.forEach { tab ->
                RingTabItem(
                    tab = tab,
                    selected = currentRoute == tab.route,
                    badge = if (tab.route == Destinations.HOME) inboxBadge else null,
                    onClick = { onSelect(tab.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RingTabItem(
    tab: RingTab,
    selected: Boolean,
    badge: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) TossBlue else TossTabInactive
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(23.dp))
            if (badge != null && badge > 0) {
                // 깔끔한 원형 뱃지 — 한 자리("1")도 찌그러지지 않게 최소 16dp 정사각 + 흰 테두리로 아이콘과 분리.
                Box(
                    modifier = Modifier
                        .offset(x = 10.dp, y = (-6).dp)
                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        .background(TossError, CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (badge > 99) "99+" else badge.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            tab.label,
            color = tint,
            fontSize = 12.sp,   // 10.5→12 어르신 가독성(하단 탭 라벨이 흐리게 작던 것). 2026-07-30
            fontWeight = FontWeight.SemiBold
        )
    }
}
