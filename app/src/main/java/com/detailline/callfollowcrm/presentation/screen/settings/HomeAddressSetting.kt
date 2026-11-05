package com.detailline.callfollowcrm.presentation.screen.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.detailline.callfollowcrm.widget.TodayWidgetProvider

/** One home address, alongside the existing navigation preference. */
@Composable
internal fun HomeAddressSetting(container: AppContainer) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(container.preferences.homeAddress) }
    var draft by remember { mutableStateOf(address) }
    TextButton(onClick = { draft = address; open = true }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text("집 주소")
            Text(address.ifBlank { "집으로 길찾기에 사용할 주소" }, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
    if (open) {
        AlertDialog(onDismissRequest = { open = false }, containerColor = Color.White, tonalElevation = 0.dp, title = { Text("집 주소") },
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
            }, dismissButton = { TextButton(onClick = { open = false }) { Text("취소") } })
    }
}
