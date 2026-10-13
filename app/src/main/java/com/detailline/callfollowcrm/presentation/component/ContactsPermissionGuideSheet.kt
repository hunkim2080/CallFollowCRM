package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType

/**
 * 연락처 권한 안내 시트 — "켜면 저장한 이름이 보여요". (2026-10-13 사장님)
 *   "다들 자기가 저장한 사람이 안 보인다" 의 진짜 원인 = READ_CONTACTS 꺼짐.
 *   전엔 시스템 권한창을 **말없이** 띄워 뭔지 모르고 넘어가던 것 → 왜 필요한지 먼저 알려주고 켜게 한다.
 *   틀(스크림·그랩·네비바 여백)은 공용 [AppSheet]. 강요 아님 — [나중에] 로 닫을 수 있다.
 */
@Composable
fun ContactsPermissionGuideSheet(
    onEnable: () -> Unit,
    onLater: () -> Unit
) {
    AppSheet(onDismiss = onLater) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).size(64.dp)
                .clip(CircleShape).background(AppTheme.colors.primaryBg),
            contentAlignment = Alignment.Center
        ) { Text("📇", style = AppType.display) }
        Spacer(Modifier.height(16.dp))
        Text(
            "연락처 권한을 켜면\n저장한 이름이 보여요",
            color = AppTheme.colors.text, style = AppType.title,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "지금은 번호로만 떠요. 권한을 켜면 폰에 저장해 둔 이름(구글·삼성·폰 전부)이 상담함·통화에 바로 나타나요. 번호나 대화 내용은 밖으로 나가지 않아요.",
            color = AppTheme.colors.textSub, style = AppType.body,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        TossPrimaryButton("연락처 권한 켜기", onClick = onEnable)
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TossTextButton("나중에 할게요", onClick = onLater)
        }
    }
}
