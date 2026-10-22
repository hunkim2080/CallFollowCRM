package com.detailline.callfollowcrm.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.detailline.callfollowcrm.CallFollowCrmApplication

/**
 * 🔝 **이미 그 탭인데 또 누르면**(아이폰식 탭바) → 그 화면 목록을 맨 위로. (2026-10-22 사장님)
 *   신호는 `AppContainer.tabReselect`(route, tick). **내 route 일 때만**, **tick 이 늘었을 때만** 발동
 *   (재진입마다 옛 값으로 재발동하는 것 방지 — [[reference_appscoped_tick_launchedeffect_refires]]).
 *   각 탭 화면이 한 줄로 부른다: `TabReselectScrollTop(Destinations.X) { listState.animateScrollToItem(0) }`
 */
@Composable
fun TabReselectScrollTop(route: String, onScrollTop: suspend () -> Unit) {
    val reselect by (LocalContext.current.applicationContext as CallFollowCrmApplication)
        .container.tabReselect.collectAsState()
    var lastSeen by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(reselect) {
        if (reselect.first > lastSeen && reselect.second == route) {
            lastSeen = reselect.first
            onScrollTop()
        }
    }
}
