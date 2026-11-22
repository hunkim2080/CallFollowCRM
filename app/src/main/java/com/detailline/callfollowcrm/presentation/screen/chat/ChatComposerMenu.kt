package com.detailline.callfollowcrm.presentation.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg

/**
 * ⊕ 첨부·메뉴 버튼 — 입력줄 왼쪽. 누르면 **카카오식 첨부 바텀시트**(최근 사진 줄 + 둥근 아이콘 그리드)를 연다.
 *   (2026-11-22 사장님 "카카오 스타일로" · 프로토 Fgc3bbn24D1doxDop2JiDz)
 *
 *   전엔 여기서 **풍선 팝업**(견적/일정/문구/대표질문)을 직접 그렸는데, 사진 선택 시트와 **두 벌**이었다.
 *   이제 둘을 한 시트로 합쳤다 — 이 버튼은 그 시트를 여는 입구 하나뿐(셈·목록은 [ChatPhotoPickerHost] 한 곳). §12
 */
@Composable
internal fun ComposerPlusButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)   // 거친 손가락도 — 44dp 터치
            .clip(CircleShape)
            .background(TossGrayBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Add, "첨부·메뉴 열기", tint = TossBlue, modifier = Modifier.size(24.dp))
    }
}
