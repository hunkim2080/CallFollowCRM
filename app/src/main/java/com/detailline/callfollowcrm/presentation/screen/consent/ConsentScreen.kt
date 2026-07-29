package com.detailline.callfollowcrm.presentation.screen.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.AppConfig
import com.detailline.callfollowcrm.presentation.component.Mascot
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary

private val Bg = Color(0xFFFBFCFE)
private val Ink = Color(0xFF0B0F19)
private val Blue = Color(0xFF3182F6)
private val Disabled = Color(0xFFCBD5E1)
private val Tag = Color(0xFF3A4250)
private val Sub = TossTextSecondary
private val LineBg = Color(0xFFF3F5F9)

/**
 * 개인정보 동의 게이트 (진입 1회) — docs/ANDROID_HANDOFF_consent.md. SKT AI메시지 동의 벤치마킹.
 *   (필수) 개인정보 수집·이용 + (선택) 품질 향상. 필수 체크해야 시작 가능.
 *   [>] = 서버 동의문/처리방침 웹뷰(브라우저). 동의 시 상위가 POST /api/consent + prefs 저장 + 다음 화면.
 */
@Composable
fun ConsentScreen(
    optionalPreChecked: Boolean,
    onOpenLink: (String) -> Unit,
    onAgree: (optionalAgreed: Boolean) -> Unit
) {
    var requiredChecked by remember { mutableStateOf(false) }
    var optionalChecked by remember { mutableStateOf(optionalPreChecked) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 26.dp).padding(top = 12.dp, bottom = 28.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Mascot(sizeDp = 74.dp)
                Spacer(Modifier.height(16.dp))
                Text("시공막내를 사용하기 위해", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Tag)
                Spacer(Modifier.height(2.dp))
                Text("동의해 주세요", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
                Spacer(Modifier.height(28.dp))
                ConsentRow(
                    checked = requiredChecked,
                    label = "(필수) 개인정보 수집·이용 동의",
                    onToggle = { requiredChecked = !requiredChecked },
                    onOpenLink = { onOpenLink(AppConfig.CONSENT_REQUIRED_URL) }
                )
                Spacer(Modifier.height(12.dp))
                ConsentRow(
                    checked = optionalChecked,
                    label = "(선택) 서비스 품질 향상을 위한 수집·이용 동의",
                    onToggle = { optionalChecked = !optionalChecked },
                    onOpenLink = { onOpenLink(AppConfig.CONSENT_OPTIONAL_URL) }
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "개인정보처리방침 보기",
                    fontSize = 12.5.sp, color = Blue, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onOpenLink(AppConfig.PRIVACY_POLICY_URL) }.padding(6.dp)
                )
            }
            Box(
                Modifier.fillMaxWidth().height(53.dp)
                    .background(if (requiredChecked) Blue else Disabled, RoundedCornerShape(16.dp))
                    .clickable(enabled = requiredChecked) { onAgree(optionalChecked) },
                contentAlignment = Alignment.Center
            ) {
                Text("동의하고 시작하기", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "필수 항목에 동의해야 서비스를 이용할 수 있어요. 선택은 안 해도 괜찮아요.",
                fontSize = 11.sp, color = Sub, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConsentRow(checked: Boolean, label: String, onToggle: () -> Unit, onOpenLink: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(LineBg).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 체크 동그라미 — 탭하면 토글.
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (checked) Blue else Color(0xFFDDE3EC))
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.size(11.dp))
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink,
            lineHeight = 18.sp, modifier = Modifier.weight(1f).clickable { onToggle() }
        )
        Spacer(Modifier.size(8.dp))
        // [>] 상세(웹뷰)
        Text("자세히 >", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Sub,
            modifier = Modifier.clickable { onOpenLink() }.padding(4.dp))
    }
}
