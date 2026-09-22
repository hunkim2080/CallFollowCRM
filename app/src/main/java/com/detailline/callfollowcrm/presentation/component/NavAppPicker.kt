package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.util.NavApp

/**
 * 첫 [길찾기] 탭 때 어느 네비 앱 쓸지 고르는 다이얼로그.
 *   탭 = 즉시 선택 + 닫기 + 길찾기 시작 (확인 버튼 따로 X = 1탭).
 *   이후엔 prefs.defaultNavAppKey 가 박혀서 같은 화면 안 뜨고 바로 열린다. 설정에서 언제든 변경.
 *
 * 홈에만 있던 것을 공용으로 옮겼다 (2026-09-22) — 일정 탭 그 날 카드에도 [길찾기] 가 생겨서.
 */
@Composable
fun NavAppPickerDialog(
    onPick: (NavApp) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AppShape.xl,
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "어느 네비 앱을 쓰세요?",
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "선택한 앱이 다음부터 한 번에 열려요. 설정 → 기본 네비 앱 에서 언제든 변경 가능.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary
                )
                Spacer(Modifier.height(16.dp))
                NavApp.values().forEach { app ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShape.md)
                            .background(AppTheme.colors.surfaceMuted)
                            .clickable { onPick(app) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            app.label,
                            color = TossTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = AppType.headline
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("닫기", color = TossTextSecondary)
                }
            }
        }
    }
}
