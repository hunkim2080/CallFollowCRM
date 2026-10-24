package com.detailline.callfollowcrm.presentation.screen.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.component.ConversionDemoCard
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType

/**
 * 🎬 **막내가 이런 것도 해요 — 기능 둘러보기.** (2026-10-24 사장님 「가」, 전환율 둘러보기)
 *
 * 킬러 기능들을 **자동 재생 데모 카드**로 모아 "써보게" 만든다. 더보기(설정)에서 진입.
 *   지금 ① 통화 요약. 다음에 ② 부재중 자동문자 · ③ 전화 미리보기 · ④ 협업 · ⑤ 내 기록 같은 틀로 붙인다.
 */
@Composable
fun FeatureTourScreen(
    onBack: () -> Unit,
    onOpenRecordingSettings: () -> Unit
) {
    Scaffold(
        containerColor = AppTheme.colors.surfaceMuted,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = AppTheme.colors.text)
                }
                Text("막내가 이런 것도 해요", style = AppType.title, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
            }
        }
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text(
                "전화·접수·협업… 막내가 뒤에서 챙기는 것들을 움직이는 그림으로 보여드려요. 마음에 들면 바로 켜보세요.",
                style = AppType.caption, color = AppTheme.colors.textSub
            )
            Spacer(Modifier.height(14.dp))

            // ① 통화 요약
            ConversionDemoCard(
                title = "통화, 안 받아적어도 돼요",
                subtitle = "전화 끊는 순간 통화 내용이 시간순으로 자동 요약돼요. 주소·날짜·견적까지 — 다시 안 들어도 한눈에.",
                captions = listOf(
                    "① 전화가 와서 통화해요",
                    "② 끊는 순간 — 자동으로",
                    "③ 통화 내용이 시간순으로 요약돼요",
                    "④ 그 고객 기록에 남아요 — 다시 안 들어도 한눈에"
                ),
                captionAt = listOf(0f, 0.30f, 0.40f, 0.57f),
                altIcon = Icons.AutoMirrored.Filled.Message,
                altText = "통화 내용으로 — 고객에게 보낼 정리 문자도 만들어줘요",
                ctaText = "통화 요약 켜기",
                onCta = onOpenRecordingSettings
            ) { p -> CallSummaryDemoStage(p) }

            Spacer(Modifier.height(16.dp))
        }
    }
}
