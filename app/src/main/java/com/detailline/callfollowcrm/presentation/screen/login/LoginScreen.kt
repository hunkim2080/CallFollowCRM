package com.detailline.callfollowcrm.presentation.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * 로그인 (첫 화면) — 테스터는 할당받은 본인 핸드폰 번호로 시작.
 *   번호 입력 → onLoginPhone(phone): bizPhone 저장 + FCM 등록 → 권한/홈.
 *   (둘러보기 제거 2026-06-30: 번호 없이 진입하면 bizPhone 미설정 → 푸시·협업 먹통이라.)
 *   onProceed 는 현재 미사용(스킵 훅 보존).
 */
@Composable
fun LoginScreen(onLoginPhone: (String) -> Unit, onProceed: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    // 키보드 올라오면 hero(마스코트/태그라인)를 컴팩트하게 — 안 그러면 위 공간이 줄어 로고가 짓눌려 겹침. 2026-06-30
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
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
                .imePadding()   // 키보드 올라오면 입력칸·버튼이 키보드 위로 (안 그러면 가림). 2026-06-30
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
                Mascot(sizeDp = if (imeVisible) 60.dp else 96.dp)
                Spacer(Modifier.height(if (imeVisible) 10.dp else 20.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = LogoInk)) { append("시공") }
                        withStyle(SpanStyle(color = LoginBlue)) { append("막내") }
                    },
                    fontSize = if (imeVisible) 28.sp else 33.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
                // 키보드 올라오면 태그라인 숨겨 hero 압축(겹침 방지). 키보드 내리면 다시 보임.
                if (!imeVisible) {
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
            }

            // 테스터: 할당받은 본인 핸드폰 번호로 시작. (2026-06-14 사장님)
            Text(
                "할당받은 본인 핸드폰 번호로 시작하세요",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TagColor,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            com.detailline.callfollowcrm.presentation.component.FormattedTextField(
                value = phone,
                onValueChange = { phone = it },
                format = com.detailline.callfollowcrm.util.PhoneNumberFormatter::formatProgressive,
                placeholder = "010-0000-0000",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
            )
            Spacer(Modifier.height(10.dp))
            val phoneOk = phone.filter { it.isDigit() }.length >= 10
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(53.dp)
                    .background(if (phoneOk) LoginBlue else Color(0xFFCBD5E1), RoundedCornerShape(15.dp))
                    .clickable(enabled = phoneOk) { onLoginPhone(phone) },
                contentAlignment = Alignment.Center
            ) {
                Text("이 번호로 시작하기", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
            }

            Spacer(Modifier.height(20.dp))
            // 수집·이용 동의 / 처리방침 — 탭하면 앱 내 웹뷰로 열림(브라우저 없어도). '이용약관' 문서는 없어 실제 문서명으로 표기. (2026-07-13 사장님)
            val linkCtx = androidx.compose.ui.platform.LocalContext.current
            val openDoc: (String) -> Unit = { url ->
                com.detailline.callfollowcrm.presentation.screen.web.DocWebViewActivity.open(
                    linkCtx, url, com.detailline.callfollowcrm.presentation.screen.web.DocWebViewActivity.titleFor(url)
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("개인정보 수집·이용", fontSize = 11.sp, color = LoginBlue, fontWeight = FontWeight.Bold,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier.clickable { openDoc(com.detailline.callfollowcrm.AppConfig.CONSENT_REQUIRED_URL) }.padding(4.dp))
                Text("·", fontSize = 11.sp, color = Color(0xFF9AA3AF), modifier = Modifier.padding(horizontal = 2.dp))
                Text("개인정보처리방침", fontSize = 11.sp, color = LoginBlue, fontWeight = FontWeight.Bold,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier.clickable { openDoc(com.detailline.callfollowcrm.AppConfig.PRIVACY_POLICY_URL) }.padding(4.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "로그인하면 위 내용에 동의하는 것으로 봐요",
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
            // "로그인 없이 둘러보기" 제거 (2026-06-30 사장님): 번호 없이 들어가면 bizPhone 미설정 →
            //   푸시·협업·팀 기능 먹통 + hasSeenLogin=true 로 영구 진입. 테스터는 할당 번호로만 시작.
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
