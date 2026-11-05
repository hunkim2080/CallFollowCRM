package com.detailline.callfollowcrm.presentation.screen.settings

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.widget.TodayWidgetProvider

/**
 * 집 주소 설정 — 다른 설정과 **같은 줄 카드**(LockRow)로. (2026-11-05 사장님 "생각없이 바닥에 링크로 박지 말고")
 *   위젯 '집으로 길찾기'를 **주소 미등록 상태**로 누르면 설정이 열리며 **입력창이 바로** 뜬다(openHomeAddressInput 플래그).
 */
@Composable
internal fun HomeAddressSetting(container: AppContainer) {
    val context = LocalContext.current
    var address by remember { mutableStateOf(container.preferences.homeAddress) }
    var draft by remember { mutableStateOf(address) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (container.preferences.openHomeAddressInput) {
            container.preferences.openHomeAddressInput = false
            draft = address; open = true
        }
    }
    LockRow(Icons.Filled.Home, TossGrayBg, TossTextTertiary, "집 주소",
        address.ifBlank { "집으로 길찾기에 사용할 주소" }) { draft = address; open = true }
    if (open) {
        AlertDialog(onDismissRequest = { open = false }, containerColor = Color.White, tonalElevation = 0.dp,
            title = { Text("집 주소") },
            text = { SheetTextField(draft, { draft = it }, "집 주소", Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        container.preferences.homeAddress = draft
                        address = draft.trim()
                        TodayWidgetProvider.refresh(context)
                        open = false
                    } catch (_: Exception) {
                        Toast.makeText(context, "집 주소를 저장하지 못했어요. 다시 눌러주세요.", Toast.LENGTH_LONG).show()
                    }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("취소") } })
    }
}
