package com.detailline.callfollowcrm.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.screen.customer.PhotoTabToggle
import com.detailline.callfollowcrm.presentation.theme.CallFollowCrmTheme
import com.detailline.callfollowcrm.presentation.theme.TossBlue

/**
 * 디버그 전용 — 사진 섹션 **밑줄 탭 토글**(PhotoTabToggle)을 실제 헤더처럼(아이콘 + weight(1f) 토글 + 버튼)
 *   띄워 에뮬레이터/폰에서 캡처해 **밑줄이 글자 폭에만 붙는지** 눈으로 확인하는 하네스.
 *   adb shell am start -n com.detailline.callfollowcrm/com.detailline.callfollowcrm.debug.TogglePreviewActivity
 */
class TogglePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallFollowCrmTheme {
                Column(
                    Modifier.fillMaxSize().background(Color.White).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text("── '현장' 선택 ──", fontSize = 12.sp, color = Color.Gray)
                    HeaderRow(tab = "mine")
                    Text("── '협업' 선택 ──", fontSize = 12.sp, color = Color.Gray)
                    HeaderRow(tab = "collab")
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HeaderRow(tab: String) {
    var t by remember { mutableStateOf(tab) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).background(TossBlue.copy(alpha = 0.12f)))
        Spacer(Modifier.width(8.dp))
        PhotoTabToggle(
            mineLabel = "1차 현장 12",
            collabLabel = "협업 20",
            tab = t,
            onTab = { t = it },
            modifier = Modifier.weight(1f)
        )
        Text("전/후", fontSize = 12.sp, color = Color.Gray, modifier = Modifier
            .background(Color(0xFFF2F4F6)).padding(horizontal = 10.dp, vertical = 6.dp))
        Spacer(Modifier.width(6.dp))
        Text("선택 삭제", fontSize = 12.sp, color = Color.Gray, modifier = Modifier
            .background(Color(0xFFF2F4F6)).padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
