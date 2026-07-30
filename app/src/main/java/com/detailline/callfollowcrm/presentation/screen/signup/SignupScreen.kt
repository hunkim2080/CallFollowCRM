package com.detailline.callfollowcrm.presentation.screen.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.component.Mascot

private val Bg = Color(0xFFFBFCFE)
private val Ink = Color(0xFF0B0F19)
private val Blue = Color(0xFF3182F6)
private val Disabled = Color(0xFFE2E6EC)
private val Tag = Color(0xFF3A4250)
private val Sub = Color(0xFF8A93A2)

/**
 * 회원가입 (첫 화면) — 폰 인증번호. docs/ANDROID_HANDOFF_signup_auth.md.
 *   전화번호 → [인증번호 받기] → 6자리 → enrolled/member/waitlisted.
 *   enrolled/member 는 콜백으로 상위(AppNavHost)가 bizPhone 저장 + FCM + 화면 전환.
 */
@Composable
fun SignupScreen(
    viewModel: SignupViewModel,
    onEnrolled: (phone: String, freeUntilMs: Long?, freeDays: Int?) -> Unit,
    onMember: (phone: String, freeUntilMs: Long?) -> Unit
) {
    val s by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.outcomes.collect { o ->
            if (o.status == "enrolled") onEnrolled(o.phone, o.freeUntilMs, o.freeDays)
            else onMember(o.phone, o.freeUntilMs)
        }
    }
    LaunchedEffect(s.error) {
        s.error?.let {
            android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }
    LaunchedEffect(s.info) {
        s.info?.let {
            android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeInfo()
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFE7F0FF), Color(0x00FBFCFE)),
                    center = Offset(540f, -120f), radius = 1100f
                )
            )
        )
        Column(
            // 내비바+키보드를 더하면 빈 공간이 생김(키보드가 내비바를 덮음) → union. 2026-07-15
            Modifier.fillMaxSize().statusBarsPadding()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 28.dp).padding(bottom = 32.dp)
        ) {
            when (s.phase) {
                SignupViewModel.Phase.PHONE -> PhonePhase(viewModel, s)
                SignupViewModel.Phase.CODE -> CodePhase(viewModel, s)
                SignupViewModel.Phase.WAITLIST -> WaitlistPhase(viewModel, s)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PhonePhase(vm: SignupViewModel, s: SignupViewModel.UiState) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Mascot(sizeDp = 90.dp)
        Spacer(Modifier.height(18.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Ink)) { append("시공") }
                withStyle(SpanStyle(color = Blue)) { append("막내") }
            },
            fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(12.dp))
        Text("전화·문자 상담, 이제 혼자 하지 마세요", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Tag)
        Spacer(Modifier.height(6.dp))
        Text("휴대폰 번호로 간편하게 시작해요", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Sub)
    }
    Text(
        "휴대폰 번호를 입력하면 인증번호를 보내드려요",
        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tag,
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(10.dp))
    com.detailline.callfollowcrm.presentation.component.FormattedTextField(
        value = s.phone,
        onValueChange = { vm.onPhoneChange(it) },
        format = com.detailline.callfollowcrm.util.PhoneNumberFormatter::formatProgressive,
        placeholder = "010-0000-0000",
        keyboardType = KeyboardType.Phone
    )
    Spacer(Modifier.height(10.dp))
    PrimaryButton(label = "인증번호 받기", enabled = vm.phoneOk && !s.loading, loading = s.loading) { vm.requestCode() }
    Spacer(Modifier.height(18.dp))
    // 수집·이용 동의 / 처리방침 — 탭하면 앱 내 웹뷰로 열림(브라우저 없어도). '이용약관' 문서는 없어 실제 문서명으로 표기. (2026-07-13 사장님)
    val linkCtx = LocalContext.current
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
        Text("개인정보 수집·이용", fontSize = 11.sp, color = Blue, fontWeight = FontWeight.Bold,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            modifier = Modifier.clickable { openDoc(com.detailline.callfollowcrm.AppConfig.CONSENT_REQUIRED_URL) }.padding(4.dp))
        Text("·", fontSize = 11.sp, color = Color(0xFF9AA3AF), modifier = Modifier.padding(horizontal = 2.dp))
        Text("개인정보처리방침", fontSize = 11.sp, color = Blue, fontWeight = FontWeight.Bold,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            modifier = Modifier.clickable { openDoc(com.detailline.callfollowcrm.AppConfig.PRIVACY_POLICY_URL) }.padding(4.dp))
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "가입하면 위 내용에 동의하는 것으로 봐요",
        fontSize = 11.sp, color = Color(0xFF9AA3AF), fontWeight = FontWeight.Medium, lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
    )
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CodePhase(vm: SignupViewModel, s: SignupViewModel.UiState) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Mascot(sizeDp = 66.dp)
        Spacer(Modifier.height(16.dp))
        Text("인증번호를 입력해주세요", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ink, letterSpacing = (-0.4).sp)
        Spacer(Modifier.height(8.dp))
        Text("${s.phone} 로 보낸\n문자 속 6자리 숫자를 입력해요", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = Sub, lineHeight = 19.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        // 6자리 코드 입력 — 큰 글자, 가운데.
        Box(
            Modifier.fillMaxWidth().height(58.dp)
                .background(Color.White, RoundedCornerShape(15.dp))
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = s.code,
                onValueChange = { vm.onCodeChange(it) },
                textStyle = TextStyle(
                    fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard,
                    fontSize = 26.sp, fontWeight = FontWeight.Black, color = Ink,
                    textAlign = TextAlign.Center, letterSpacing = 8.sp
                ),
                singleLine = true,
                cursorBrush = SolidColor(Blue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (s.code.isEmpty()) {
                        Text("● ● ● ● ● ●", fontSize = 20.sp, color = Color(0xFFD3D9E2),
                            fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    inner()
                }
            )
        }
    }
    PrimaryButton(label = "확인", enabled = s.code.length == 6 && !s.loading, loading = s.loading) { vm.verify() }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        if (s.resendSec > 0) {
            Text("인증번호 다시 받기 (${s.resendSec}초)", fontSize = 13.sp, color = Sub, fontWeight = FontWeight.Bold)
        } else {
            Text("인증번호 다시 받기", fontSize = 13.sp, color = Blue, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clickable { vm.requestCode() })
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("번호 다시 입력", fontSize = 12.5.sp, color = Sub, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().clickable { vm.backToPhone() }.padding(6.dp), textAlign = TextAlign.Center)
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.WaitlistPhase(vm: SignupViewModel, s: SignupViewModel.UiState) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Mascot(sizeDp = 90.dp)
        Spacer(Modifier.height(18.dp))
        Text("조금만 기다려 주세요", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink, letterSpacing = (-0.4).sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "지금은 신청이 몰려 대기열이에요.\n자리가 나면 문자로 바로 알려드릴게요 🙂",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tag, lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )
    }
    PrimaryButton(label = "다시 확인", enabled = !s.loading, loading = s.loading) { vm.recheckWaitlist() }
    Spacer(Modifier.height(14.dp))
    Text("자리가 나면 문자 알림이 가요. 앱을 닫아도 괜찮아요.",
        fontSize = 11.5.sp, color = Sub, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(53.dp)
            .background(if (enabled || loading) Blue else Disabled, RoundedCornerShape(15.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
        }
    }
}
