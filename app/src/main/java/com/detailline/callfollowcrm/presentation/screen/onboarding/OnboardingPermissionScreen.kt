package com.detailline.callfollowcrm.presentation.screen.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossTextButton
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DefaultSmsAppHelper
import com.detailline.callfollowcrm.util.PermissionHelper

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingPermissionScreen(onContinue: () -> Unit) {
    val appCtx = LocalContext.current
    // ── Play 정책(기본 SMS 핸들러) 2026-07-11 ──────────────────────────────────────
    //   구글 거부 사유: "런타임 권한 다이얼로그보다 '기본 문자 앱으로 설정' 다이얼로그가 먼저 떠야 함".
    //   순서를 고정: ① ROLE_SMS(기본 문자 앱) 다이얼로그 → (수락/거부 무관) → ② 런타임 권한(통화기록·전화상태·알림).
    //   기본 문자 앱이 되면 SMS 그룹 권한은 시스템이 자동 부여(창 안 뜸). 거부하면 수동 모드.
    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onContinue()
    }
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // 기본 문자 앱 다이얼로그가 닫힌 뒤(동의/거부 무관) 런타임 권한을 요청한다.
        runtimeLauncher.launch(PermissionHelper.requiredPermissions())
    }
    fun startPermissionFlow() {
        val activity = appCtx as? android.app.Activity
        val roleIntent = activity?.let { DefaultSmsAppHelper.createRequestIntent(it) }
        if (roleIntent != null) {
            // 반드시 기본 핸들러 다이얼로그 먼저. 실패(구형/미지원)하면 런타임 권한으로 폴백.
            runCatching { roleLauncher.launch(roleIntent) }
                .onFailure { runtimeLauncher.launch(PermissionHelper.requiredPermissions()) }
        } else {
            // 이미 기본 문자 앱이거나(=SMS 권한 자동 보유) 미지원 기기 → 바로 런타임 권한.
            runtimeLauncher.launch(PermissionHelper.requiredPermissions())
        }
    }

    Scaffold(containerColor = TossGrayBg) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
        ) {
            // 스크롤 가능한 본문
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // 제목 — 권한이 왜 필요한지 한 줄로. 가치 소개 캐러셀은 온보딩 스토리 한 곳으로 단일화(중복 제거, 2026-07-29).
                Column {
                    Text(
                        "몇 가지 권한이 필요해요",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossTextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "전화·문자를 한 화면에서 챙기기 위해서예요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TossTextSecondary
                    )
                }

                TossCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        PermRow("📨", "기본 문자 앱", "고객 문자·사진을 시공막내에서 받아 한 화면에서 관리해요")
                        PermRow("📱", "전화 상태", "통화 종료를 감지하기 위해 필요해요")
                        PermRow("📋", "통화 기록", "방금 통화한 번호를 자동으로 채워줘요")
                        PermRow("💬", "주고받은 문자", "고객 대화를 한 화면에서 보고 답장 추천을 받기 위해 필요해요")
                        PermRow("🔔", "알림", "부재중 자동 응답·중요 알림을 표시해요")
                    }
                }

                TossCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "절대 하지 않는 것",
                            style = MaterialTheme.typography.titleSmall,
                            color = TossTextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        SubText("• 문자를 자동으로 보내지 않아요")
                        SubText("• 기본 문자앱을 열어드리고, 전송 버튼은 직접 누르세요")
                        SubText("• 통화 녹음을 직접 하지 않아요")
                    }
                }

                TossCard {
                    Column {
                        Text(
                            "권한 거부해도 괜찮아요",
                            style = MaterialTheme.typography.titleSmall,
                            color = TossTextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        SubText("거부하면 수동으로 전화번호를 입력해서 사용할 수 있어요")
                    }
                }
            }

            // 하단 고정 버튼
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                TossPrimaryButton(
                    text = "권한 허용하고 시작하기",
                    onClick = { startPermissionFlow() }
                )
                Spacer(Modifier.height(8.dp))
                TossTextButton(
                    text = "권한 없이 둘러볼게요",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PermRow(emoji: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = TossBlueSoft
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TossTextPrimary)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = TossTextTertiary)
        }
    }
}

@Composable
private fun SubText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = TossTextSecondary)
}
