package com.detailline.callfollowcrm.presentation.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.component.Mascot

private val LoginBg = Color(0xFFFBFCFE)
private val LogoInk = Color(0xFF0B0F19)
private val LoginBlue = Color(0xFF3182F6)
private val TagColor = Color(0xFF3A4250)
private val SubColor = Color(0xFF8A93A2)

/**
 * 로그인 (첫 화면) — 프로토타입 `.login` 그대로.
 *   막내 비서 캐릭터 + RINGGO 로고 + 태그라인 + 소셜 3종 + 둘러보기.
 *   소셜 OAuth 실제 연동은 서버 작업으로 별도. 지금은 화면 + 진입 흐름만:
 *   어떤 버튼/둘러보기든 누르면 onProceed() → 권한/홈으로.
 */
@Composable
fun LoginScreen(onProceed: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(LoginBg)
    ) {
        // 상단 은은한 라디얼 글로우 (#E7F0FF)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFE7F0FF), Color(0x00FBFCFE)),
                        center = Offset(540f, -120f),
                        radius = 1100f
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
                .padding(bottom = 32.dp)
        ) {
            // 상단: 캐릭터 + 로고 + 카피 (가운데 정렬, 남는 공간 차지)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Mascot(sizeDp = 96.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = LogoInk)) { append("RING") }
                        withStyle(SpanStyle(color = LoginBlue)) { append("GO") }
                    },
                    fontSize = 33.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.4).sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "곁에 오래 둘수록, 나다워지는 AI 비서",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TagColor
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "전화·문자 상담, 이제 혼자 하지 마세요",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SubColor
                )
            }

            // 소셜 로그인 버튼 3종
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LoginButton(
                    bg = Color(0xFFFEE500),
                    fg = Color(0xFF191600),
                    label = "카카오로 시작하기",
                    chip = "3초 만에 시작",
                    onClick = onProceed
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color(0xFF191600), modifier = Modifier.size(20.dp))
                }
                LoginButton(
                    bg = Color(0xFF03C75A),
                    fg = Color.White,
                    label = "네이버로 시작하기",
                    onClick = onProceed
                ) {
                    Text("N", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                LoginButton(
                    bg = Color.White,
                    fg = Color(0xFF1F2937),
                    label = "Google로 시작하기",
                    borderColor = Color(0xFFE5E7EB),
                    onClick = onProceed
                ) {
                    Text("G", color = Color(0xFF4285F4), fontSize = 19.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "로그인하면 이용약관 · 개인정보처리방침에\n동의하는 것으로 봐요",
                fontSize = 11.sp,
                color = Color(0xFF9AA3AF),
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(13.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Lock, null, tint = SubColor, modifier = Modifier.size(13.dp))
                Spacer(Modifier.size(5.dp))
                Text("사장님 한 분을 위한 계정이에요", fontSize = 11.5.sp, color = SubColor, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "로그인 없이 둘러보기",
                fontSize = 12.5.sp,
                color = Color(0xFF9AA3AF),
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onProceed
                    )
            )
        }
    }
}

@Composable
private fun LoginButton(
    bg: Color,
    fg: Color,
    label: String,
    onClick: () -> Unit,
    chip: String? = null,
    borderColor: Color? = null,
    icon: @Composable () -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        val base = Modifier
            .fillMaxWidth()
            .height(53.dp)
            .background(bg, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
        val withBorder = if (borderColor != null) {
            base.then(
                Modifier.border(1.dp, borderColor, RoundedCornerShape(15.dp))
            )
        } else base
        Box(withBorder, contentAlignment = Alignment.Center) {
            // 아이콘은 왼쪽 고정, 라벨은 가운데 (프로토 .lbtn .lico 절대배치 재현)
            Box(Modifier.fillMaxWidth().padding(start = 18.dp), contentAlignment = Alignment.CenterStart) {
                icon()
            }
            Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
        }
        if (chip != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .offset(y = (-9).dp)
                    .background(LoginBlue, RoundedCornerShape(99.dp))
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            ) {
                Text(chip, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
