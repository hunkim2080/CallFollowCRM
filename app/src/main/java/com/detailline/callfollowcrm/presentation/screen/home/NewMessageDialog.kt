package com.detailline.callfollowcrm.presentation.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.component.PhoneHyphenTransformation
import com.detailline.callfollowcrm.presentation.theme.AppTheme

/**
 * ✏️ 새 메시지 — 번호를 직접 입력해 바로 문자. 확인하면 그 번호 채팅으로 이동(onSend). (2026-10-31 사장님)
 *   전화번호 입력은 공용 [PhoneHyphenTransformation](숫자만 저장·화면엔 010-0000-0000).
 *   HomeScreen 이 거대해지지 않게 별도 파일(§12-F).
 */
@Composable
internal fun NewMessageDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var num by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        tonalElevation = 0.dp,   // 색조 틴트 끔 — 없으면 흰색이 회색빛으로 뜸(예전 "회색창")
        title = { Text("새 메시지", fontWeight = FontWeight.Bold, color = AppTheme.colors.text) },
        text = {
            Column {
                Text("문자 보낼 번호를 입력하세요.", fontSize = 13.sp, color = AppTheme.colors.textHint)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = num,
                    onValueChange = { input -> num = input.filter { it.isDigit() }.take(11) },
                    placeholder = { Text("010-0000-0000") },
                    visualTransformation = PhoneHyphenTransformation,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (num.length >= 9) onSend(num) },
                enabled = num.length >= 9
            ) { Text("문자 쓰기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
