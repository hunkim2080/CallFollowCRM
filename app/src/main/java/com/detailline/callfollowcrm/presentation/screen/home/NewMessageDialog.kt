package com.detailline.callfollowcrm.presentation.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.component.AppSheet
import com.detailline.callfollowcrm.presentation.component.PhoneHyphenTransformation
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType

/**
 * ✏️ 새 메시지 — 번호를 직접 입력해 바로 문자. 확인하면 그 번호 채팅으로 이동(onSend). (2026-10-31 사장님)
 *   공용 [AppSheet](흰 카드·스크림·키보드/네비바 여백 내장)로 — Material 기본 다이얼로그의 **색조 틴트(회색빛)** 를 안 쓴다.
 *   (2026-10-31 사장님 "회색톤이야" → 앱 표준 흰 시트로 통일.)
 *   전화번호 입력은 공용 [PhoneHyphenTransformation](숫자만 저장·화면엔 010-0000-0000).
 *   HomeScreen 이 거대해지지 않게 별도 파일(§12-F).
 */
@Composable
internal fun NewMessageDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var num by remember { mutableStateOf("") }
    AppSheet(onDismiss = onDismiss) {
        androidx.compose.material3.Text(
            "새 메시지", style = AppType.title,
            color = AppTheme.colors.text, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text(
            "문자 보낼 번호를 입력하세요.", style = AppType.caption, color = AppTheme.colors.textHint
        )
        Spacer(Modifier.height(14.dp))
        SheetTextField(
            value = num,
            onValueChange = { input -> num = input.filter { it.isDigit() }.take(11) },
            placeholder = "010-0000-0000",
            keyboardType = KeyboardType.Phone,
            visualTransformation = PhoneHyphenTransformation,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TossSecondaryButton("취소", onClick = onDismiss, modifier = Modifier.weight(1f))
            TossPrimaryButton(
                "문자 쓰기",
                onClick = { if (num.length >= 9) onSend(num) },
                modifier = Modifier.weight(1f),
                enabled = num.length >= 9
            )
        }
    }
}
