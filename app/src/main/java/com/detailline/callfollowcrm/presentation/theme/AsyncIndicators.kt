package com.detailline.callfollowcrm.presentation.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 진행감 (progress feedback) 공통 컴포넌트.
 *   사장님 피드백 (2026-05-25): "그냥 없었다가 한번에 툭! 생겨버려. 미완성된 느낌이야."
 *   비동기 작업 (LLM 호출, 서버 응답, 캐시 prefetch) 의 빈 시간을 채워서 "작동 중" 표현.
 */

/**
 * "텍스트" + "." → ".." → "..." → "." 순환 — 작업 진행 중 가벼운 표시.
 *   AI 요약 / 답변 생성 / 분류 등 1~10초짜리 작업에 적합.
 *   사이클 1200ms — 너무 빠르지도 느리지도 않게.
 */
@Composable
fun AnimatedDots(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Medium,
    maxDots: Int = 3
) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (maxDots + 1).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots-phase"
    )
    val count = phase.toInt().coerceIn(0, maxDots)
    val dots = ".".repeat(count)
    Text(
        text = "$text$dots",
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

/**
 * 회색 → 밝은 회색 → 회색 gradient 가 좌→우 흐르는 skeleton placeholder.
 *   카드/텍스트 줄 자리에 박아서 "여기에 콘텐츠가 곧 옵니다" 표현.
 *   광원 효과로 정적 placeholder 보다 살아있는 느낌.
 */
@Composable
fun ShimmerLine(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    cornerRadius: Dp = 6.dp,
    baseColor: Color = Color(0xFFEEF1F4),
    highlightColor: Color = Color(0xFFF8F9FA)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-translate"
    )
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = androidx.compose.ui.geometry.Offset(translate * 600f - 300f, 0f),
        end = androidx.compose.ui.geometry.Offset(translate * 600f + 300f, 0f)
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

/**
 * Shimmer 한 줄 + 보조 짧은 줄 — 카드 본문/요약 자리에 자연스럽게.
 *   2줄 placeholder = "여기에 한 단락 옵니다" 느낌.
 */
@Composable
fun ShimmerTwoLines(
    modifier: Modifier = Modifier,
    firstWidthFraction: Float = 0.85f,
    secondWidthFraction: Float = 0.6f
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        ShimmerLine(modifier = Modifier.fillMaxWidth(firstWidthFraction))
        ShimmerLine(modifier = Modifier.fillMaxWidth(secondWidthFraction))
    }
}
