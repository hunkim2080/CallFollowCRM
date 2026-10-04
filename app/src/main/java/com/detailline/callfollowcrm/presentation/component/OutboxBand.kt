package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.theme.TossWarning
import com.detailline.callfollowcrm.presentation.util.OutboxWording

/**
 * 📮 아직 못 보낸 것 띠 — 홈 맨 위. 업데이트 배너와 **같은 자리·같은 모양**, 색만 주황(알림). (우체통 2단계)
 *   dead(서버 거절) 또는 하루 넘게 못 간 게 있을 때만 뜬다(count>0). 눌러서 목록으로.
 *   프로토 사장님 확정(2026-10-04 「ㄱㄱ」).
 */
@Composable
fun OutboxBand(count: Int, onClick: () -> Unit) {
    if (count <= 0) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(TossWarning)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📮", fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            OutboxWording.bandText(count),
            color = Color.White, fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f)
        )
        Text("›", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}
