package com.detailline.callfollowcrm.presentation.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType

/**
 * 🎬 **기능 둘러보기 데모 카드 — 공통 틀.** (2026-10-24 사장님 「가」, 전환율 둘러보기)
 *
 * 「막내가 이런 것도 해요」 화면이 이 틀로 카드 여러 장을 쌓는다. 접수서 미리보기와 같은 결:
 *   제목 + 설명 → **자동 재생되는 그림(stage)** → 단계 자막·점 → 보너스 한 줄 → [해보기] 버튼.
 *   그림(stage)만 기능마다 다르게 끼운다 — 틀·자막·버튼·움직임 시계는 여기 한 곳. ([[project_conversion_show_reward_on_setup]])
 *
 * @param captionAt 자막이 바뀌는 지점(0..1, 오름차순·첫값 0). stage 움직임과 같은 한 바퀴를 나눈다.
 * @param stage 0..1 진행값 p 를 받아 그 순간을 그리는 그림. 자기 높이·바탕을 스스로 그린다.
 */
@Composable
fun ConversionDemoCard(
    title: String,
    subtitle: String,
    captions: List<String>,
    captionAt: List<Float>,
    altText: String,
    ctaText: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
    periodMs: Int = 8000,
    altIcon: ImageVector? = null,
    stage: @Composable (p: Float) -> Unit
) {
    val inf = rememberInfiniteTransition(label = "demo")
    val p by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "p"
    )
    val idx = captionAt.indexOfLast { p >= it }.coerceIn(0, captions.lastIndex)

    Column(
        modifier.fillMaxWidth().tossCardShadow(AppShape.lg).clip(AppShape.lg)
            .background(AppTheme.colors.surface).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(AppTheme.colors.primary))
            Spacer(Modifier.width(8.dp))
            Text(title, style = AppType.headline, color = AppTheme.colors.text)
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = AppType.caption, color = AppTheme.colors.textSub)
        Spacer(Modifier.height(12.dp))

        stage(p)

        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            captions.indices.forEach { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(if (i == idx) 8.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (i == idx) AppTheme.colors.primary else AppTheme.colors.line)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            captions[idx], style = AppType.caption, color = AppTheme.colors.textSub,
            fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(11.dp))
        Row(
            Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.primaryBg)
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (altIcon != null) {
                Icon(altIcon, null, tint = AppTheme.colors.primaryText, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(altText, style = AppType.caption, color = AppTheme.colors.primaryText, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))
        TossPrimaryButton(text = ctaText, onClick = onCta, modifier = Modifier.fillMaxWidth())
    }
}
