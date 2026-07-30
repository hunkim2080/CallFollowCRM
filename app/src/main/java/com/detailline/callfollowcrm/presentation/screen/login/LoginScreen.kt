package com.detailline.callfollowcrm.presentation.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary

private val LoginBg = Color(0xFFFBFCFE)
private val LogoInk = Color(0xFF0B0F19)
private val LoginBlue = Color(0xFF3182F6)
private val TagColor = Color(0xFF3A4250)
private val SubColor = TossTextSecondary

/**
 * 로그인 (첫 화면) — 테스터는 할당받은 본인 핸드폰 번호로 시작.
 *   번호 입력 → onLoginPhone(phone): bizPhone 저장 + FCM 등록 → 권한/홈.
 *   (둘러보기 제거 2026-06-30: 번호 없이 진입하면 bizPhone 미설정 → 푸시·협업 먹통이라.)
 *   onProceed 는 현재 미사용(스킵 훅 보존).
 */
@Composable
fun LoginScreen(onLoginPhone: (String) -> Unit, onProceed: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var showBeta by remember { mutableStateOf(false) }   // 베타 테스터 신청 창
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
                // 키보드 올라오면 입력칸·버튼이 키보드 위로 (안 그러면 가림). 2026-06-30
                //   내비바+키보드를 더하면 키보드가 내비바를 덮는 만큼 빈 공간이 생김 → union(둘 중 큰 쪽). 2026-07-15
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
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
                    .background(if (phoneOk) LoginBlue else Color(0xFFE2E6EC), RoundedCornerShape(15.dp))
                    .clickable(enabled = phoneOk) { onLoginPhone(phone) },
                contentAlignment = Alignment.Center
            ) {
                // 비활성(번호 입력 전)엔 흰 글자면 연한 회색 배경에 안 보임 → 회색 글자로. (2026-07-30 로그인 검토)
                Text("이 번호로 시작하기", color = if (phoneOk) Color.White else Color(0xFF8B95A3), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
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

            // 베타 신청 — 화이트리스트에 없는 사람이 신청 문자를 보내는 경로 (2026-07-25 사장님).
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEF1F5)))
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clickable { showBeta = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("아직 테스터가 아니세요?", fontSize = 12.sp, color = SubColor, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(5.dp))
                Text(
                    "베타 신청하기", fontSize = 12.5.sp, color = LoginBlue, fontWeight = FontWeight.Black,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                )
            }
        }

        if (showBeta) BetaApplySheet(onClose = { showBeta = false })
    }
}

@Composable
private fun BetaFieldLabel(t: String) {
    Text(
        t, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF5A6472),
        modifier = Modifier.padding(top = 15.dp, bottom = 6.dp)
    )
}

/** 베타 테스터 신청 창 — 이름·연락처·업종을 적어 '문자 보내기'하면 사장님 번호로 SMS 앱이 열림. */
@Composable
private fun BoxScope.BetaApplySheet(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var trade by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    val trades = listOf("줄눈", "청소", "필름", "도배", "탄성", "기타")
    val canSend = name.trim().isNotEmpty() && phone.filter { it.isDigit() }.length >= 10

    fun send() {
        val body = buildString {
            append("[시공막내 베타 신청]\n")
            append("이름/상호: ").append(name.trim()).append("\n")
            append("연락처: ").append(phone.trim())
            if (trade.isNotBlank()) append("\n업종: ").append(trade)
            if (memo.trim().isNotEmpty()) append("\n한마디: ").append(memo.trim())
        }
        val intent = android.content.Intent(
            android.content.Intent.ACTION_SENDTO,
            android.net.Uri.parse("smsto:" + com.detailline.callfollowcrm.AppConfig.BETA_APPLY_PHONE)
        ).apply { putExtra("sms_body", body) }
        runCatching { ctx.startActivity(intent) }
            .onFailure { android.widget.Toast.makeText(ctx, "문자 앱을 열 수 없어요", android.widget.Toast.LENGTH_SHORT).show() }
        onClose()
    }

    // 스크림 (탭하면 닫힘)
    Box(
        Modifier.fillMaxSize().background(Color(0x730B0F19))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClose() }
    )
    // 시트 (하단, 키보드 위로 밀림)
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color.White)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { /* 시트 내부 탭은 스크림으로 전달 안 함 */ }
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = 22.dp)
            .padding(top = 22.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("베타 테스터 신청 🐣", fontSize = 19.sp, fontWeight = FontWeight.Black, color = LogoInk, letterSpacing = (-0.4).sp)
        Spacer(Modifier.height(5.dp))
        Text(
            "아직 초대받은 분만 쓰는 베타예요. 아래를 적어 신청하면 사장님이 확인하고 등록해 드려요.",
            fontSize = 12.5.sp, color = SubColor, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp
        )

        BetaFieldLabel("이름 / 상호")
        com.detailline.callfollowcrm.presentation.component.SheetTextField(
            value = name, onValueChange = { name = it }, placeholder = "예: 홍길동 · 디테일라인"
        )

        BetaFieldLabel("연락처 (문자 받을 번호)")
        com.detailline.callfollowcrm.presentation.component.FormattedTextField(
            value = phone, onValueChange = { phone = it },
            format = com.detailline.callfollowcrm.util.PhoneNumberFormatter::formatProgressive,
            placeholder = "010-0000-0000",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
        )

        BetaFieldLabel("업종")
        trades.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                rowItems.forEach { tr ->
                    val on = trade == tr
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (on) Color(0xFFEAF1FF) else Color(0xFFF1F4F8))
                            .border(1.4.dp, if (on) Color(0xFFB9D0FF) else Color(0xFFF1F4F8), RoundedCornerShape(10.dp))
                            .clickable { trade = if (on) "" else tr }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(tr, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (on) Color(0xFF1B64DA) else Color(0xFF5A6472)) }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        BetaFieldLabel("한마디 (선택)")
        com.detailline.callfollowcrm.presentation.component.SheetTextField(
            value = memo, onValueChange = { memo = it }, placeholder = "예: 인스타 보고 왔어요"
        )

        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().height(52.dp)
                .background(if (canSend) LoginBlue else Color(0xFFE2E6EC), RoundedCornerShape(14.dp))
                .clickable(enabled = canSend) { send() },
            contentAlignment = Alignment.Center
        ) { Text("💬 신청 문자 보내기", color = if (canSend) Color.White else Color(0xFF8B95A3), fontSize = 15.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(10.dp))
        Text(
            "누르면 문자 앱이 열리고, 적으신 내용이 사장님께 문자로 전송돼요",
            fontSize = 11.sp, color = Color(0xFFB0B8C4), fontWeight = FontWeight.Medium, lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "닫기", fontSize = 13.sp, color = Color(0xFF9AA3AF), fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable { onClose() }.padding(6.dp)
        )
    }
}

