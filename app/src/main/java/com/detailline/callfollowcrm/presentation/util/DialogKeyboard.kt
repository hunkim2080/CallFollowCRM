package com.detailline.callfollowcrm.presentation.util

import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Dialog / AlertDialog 의 윈도우를 IME(키보드)에 맞춰 ADJUST_RESIZE 로 강제한다.
 *
 * 갤S9/안드10 에서 Compose 의 Dialog 윈도우는 기본적으로 키보드에 맞춰 줄지 않아, 중앙 다이얼로그의
 * 입력칸이 키보드에 가린다. ADJUST_RESIZE 로 바꾸면 윈도우가 키보드 높이만큼 줄고 → 중앙 다이얼로그가
 * 그 위로 재배치된다. (바텀시트는 하단 고정이라 이 방법이 안 통해서 인라인 오버레이를 쓴다.)
 *
 * 사용: 다이얼로그 content 안 아무 곳에서나 [ForceDialogResize] 한 번 호출.
 */
@Composable
fun ForceDialogResize() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.findDialogWindowOrNull()
        val old = window?.attributes?.softInputMode
        window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        )
        onDispose { if (old != null) window.setSoftInputMode(old) }
    }
}

private fun View.findDialogWindowOrNull(): Window? {
    var p = parent
    while (p != null) {
        if (p is DialogWindowProvider) return p.window
        p = p.parent
    }
    return null
}
