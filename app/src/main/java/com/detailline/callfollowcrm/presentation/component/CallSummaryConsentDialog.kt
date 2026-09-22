package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 통화 요약 고지·동의 창. (2026-09-17 플레이 정책 점검)
 *
 * 녹음을 **서버로 보내기 전에** 반드시 보여야 한다:
 *   "Must be granted by the user **before** your app can begin to collect or access
 *    the personal and sensitive user data" (Play · User Data policy)
 *
 * 설정 화면(토글)뿐 아니라 **채팅의 '이 통화 요약하기'** 에서도 뜬다. (2026-09-17 사장님:
 *   "이 통화요약 하기 눌러도 그 동의 창이 나왔으면 더 전환이 빠르겠다")
 *   → 요약이 필요한 바로 그 순간에 묻는 게 설정까지 찾아가게 하는 것보다 훨씬 빠르다.
 *   그래서 설정 화면 전용이던 것을 공용으로 뺐다(한 벌만 유지 — 두 벌이면 한쪽만 고쳐지는 사고가 난다).
 */
@Composable
fun CallSummaryConsentDialog(onAgree: () -> Unit, onDecline: () -> Unit) {
    Dialog(
        onDismissRequest = { /* 바깥 탭·뒤로가기로 닫히면 안 된다 — 그건 동의가 아니다 */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White)
                .padding(22.dp)
        ) {
            Text(
                "통화 요약을 켜면", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                color = TossTextPrimary, letterSpacing = (-0.4).sp
            )
            Spacer(Modifier.height(14.dp))
            ConsentLine("폰에 저장된 ", "통화 녹음 파일이 시공막내 서버로 전송", "됩니다.")
            ConsentLine("서버가 ", "받아쓰기", "한 뒤, 그 글을 AI(Anthropic·Google, 미국)가 요약합니다.")
            ConsentLine("", "녹음 파일은 받아쓰기 후 바로 지워집니다", " — 서버에 남지 않아요.")
            ConsentLine("요약에는 ", "고객 이름·주소·금액", "이 들어갈 수 있습니다.")
            ConsentLine("", "언제든 설정에서 끌 수 있습니다", ".")
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossBlue)
                    .clickable { onAgree() }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("동의하고 켜기", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                    .clickable { onDecline() }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("안 할래요", color = TossTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "안 하셔도 문자·일정·정산은 그대로 쓰실 수 있어요.",
                fontSize = 11.5.sp, color = TossTextTertiary, lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConsentLine(pre: String, bold: String, post: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
        Text("·", fontSize = 14.sp, color = TossTextTertiary)
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                append(pre)
                pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold))
                append(bold)
                pop()
                append(post)
            },
            fontSize = 14.sp, color = TossTextSecondary, lineHeight = 21.sp
        )
    }
}
