package com.detailline.callfollowcrm.presentation.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 하단 내비게이션 바 높이(dp).
 *
 * M3 ModalBottomSheet 가 일부 기기(갤S9/OneUI 등)에서 내비바 인셋을 0으로 주는 버그 때문에,
 *   바텀시트 안의 버튼/내용이 시스템 내비바(◁ ○ ▢)에 가린다. 인셋이 0이면 시스템 리소스의
 *   실제 내비바 높이로 fallback 해서 확실히 띄운다. (인셋이 정상이면 인셋 사용 = 제스처바도 정확)
 *
 * ⚠️ ModalBottomSheet/Dialog 등 "내비바 밑까지 그려지는 별도 창" 안에서만 써라.
 *    액티비티 창 콘텐츠(탭바·전체화면·인라인 오버레이)는 navigationBarsPadding() 이 정상 동작하므로
 *    이걸 쓰면 오히려 과다 여백이 생긴다.
 */
@Composable
fun navBarBottomDp(): Dp {
    val density = LocalDensity.current
    val context = LocalContext.current
    val resNavPx = remember {
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }
    val insetPx = WindowInsets.navigationBars.getBottom(density)
    return with(density) { (if (insetPx > 0) insetPx else resNavPx).toDp() }
}

/** ModalBottomSheet/Dialog 콘텐츠 하단을 내비바만큼 띄우는 안전 패딩(+선택 여백). */
fun Modifier.bottomBarClearance(extra: Dp = 8.dp): Modifier = composed {
    padding(bottom = navBarBottomDp() + extra)
}
