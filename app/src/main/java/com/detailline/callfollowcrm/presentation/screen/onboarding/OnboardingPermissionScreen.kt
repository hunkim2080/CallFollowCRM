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
import com.detailline.callfollowcrm.util.PermissionHelper

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingPermissionScreen(onContinue: () -> Unit) {
    val appCtx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onContinue()
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
                Spacer(Modifier.height(24.dp))

                // 스토리텔링 인트로 — 막내 비서 + 핵심 가치 4장 (swipe). 2026-06-01.
                val storyPager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 4 })
                androidx.compose.foundation.pager.HorizontalPager(
                    state = storyPager,
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                ) { page -> StorySlide(page) }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(4) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == storyPager.currentPage) 8.dp else 6.dp)
                                .background(
                                    if (i == storyPager.currentPage) TossBlue else TossTextTertiary.copy(alpha = 0.4f),
                                    CircleShape
                                )
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 통화 후속 처리 권한 — RING-GO 의 부가 기능. 작은 sub-header 로 연결.
                Text(
                    "통화 후속도 함께 챙겨요",
                    style = MaterialTheme.typography.titleSmall,
                    color = TossTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp)
                )

                TossCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        PermRow("📱", "전화 상태", "통화 종료를 감지하기 위해 필요해요")
                        PermRow("📋", "통화 기록", "방금 통화한 번호를 자동으로 채워줘요")
                        PermRow("💬", "주고받은 문자", "고객 대화를 한 화면에서 보고 답장 추천을 받기 위해 필요해요")
                        PermRow("🔔", "알림", "통화 후 후속 안내 알림을 표시해요")
                    }
                }

                // 별도 안내 — 일반 권한 다이얼로그로 못 받는 SYSTEM_ALERT_WINDOW.
                // 사용자가 시스템 설정 화면 직접 가야 토글 가능. 강력 권장 톤.
                TossCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "✨ 다른 앱 위에 표시 (강력 권장)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TossTextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        SubText("켜면 통화가 끝나는 순간 바로 후속 카드가 떠서, 다른 화면에서도 한 번에 처리할 수 있어요.")
                        SubText("끄면 알림으로만 안내됩니다 (한 단계 더 누르셔야 해요).")
                        Spacer(Modifier.height(6.dp))
                        TossTextButton(
                            text = "→ 설정 열기",
                            onClick = {
                                runCatching {
                                    val ctx = appCtx
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${ctx.packageName}")
                                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    ctx.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
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
                    onClick = { launcher.launch(PermissionHelper.requiredPermissions()) }
                )
                Spacer(Modifier.height(8.dp))
                TossTextButton(
                    text = "수동 모드로 시작",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StorySlide(page: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
        if (page == 0) {
            Box(
                Modifier.size(112.dp).background(TossBlueSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) { com.detailline.callfollowcrm.presentation.component.Mascot(sizeDp = 96.dp) }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.background(TossBlue, androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text("막내 비서 탄생!", color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            val emoji = when (page) { 1 -> "💬"; 2 -> "💰"; else -> "📅" }
            Box(
                Modifier.size(96.dp).background(TossBlueSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 44.sp) }
        }
        Spacer(Modifier.height(16.dp))
        val (title, body) = when (page) {
            0 -> "딸깍의 시대" to "사장님 말투를 배우는 막내 비서가\n가장 자연스러운 답장을 미리 만들어요."
            1 -> "답장, 고민 끝" to "고객 문자에 딱 맞는 답장을\nAI가 미리 만들어둬요. 탭 한 번."
            2 -> "누가 돈 안 줬지?" to "미수금을 자동으로 추적.\n계약금·잔금 받음만 체크하면 끝."
            else -> "일정도 현금흐름도" to "오늘 시공·받을 돈·나갈 돈을\n달력 한 장에서 한눈에."
        }
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TossTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
