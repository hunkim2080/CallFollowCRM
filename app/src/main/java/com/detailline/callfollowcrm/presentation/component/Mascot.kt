package com.detailline.callfollowcrm.presentation.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

// 막내 비서 마스코트 — 프로토(design-preview/ringgo-redesign.html .mascot, line 1002~) 1:1 이식.
//   2026-06-02 안전모 일러스트 (구버전) → 프로토 오렌지 모자 캐릭터로 교체.
//   기준 viewport = 120 x 120. 모든 좌표/크기는 비율 s = canvasWidth / 120 로 스케일.
//   원본 CSS 의 face / hat / brim / button / eye / cheek / smile 7요소를 그대로 옮김.

private val MascotEyeColor = Color(0xFF2B3243)
private val MascotCheekColor = Color(0xFFFFB3C1)
private val MascotHatLight = Color(0xFFFFCB5B)
private val MascotHatDark = Color(0xFFF6A609)
private val MascotHatBrimDark = Color(0xFFE8910A)
private val MascotFaceLight = Color(0xFFEAF2FF)
private val MascotFaceDark = Color(0xFFCFE0FF)

/**
 * 막내 비서 마스코트 (프로토 1:1).
 *
 * @param sizeDp 그릴 크기. 프로토 기본 = 120dp (.mascot), 빈상태 = 80dp(.mascot-80 ≈ 96·0.78),
 *               agent-card = 56dp(.mascot-mini), think-row/통계 = 44dp(.mascot-xs).
 * @param animateBob 둥실둥실 (mBob 3s ease-in-out infinite, ±6px @ 120 base). 프로토 .m-inner 기본 동작.
 *                   끄고 싶을 때만 false (예: 푸시 알림 아이콘처럼 정적 표현).
 */
@Composable
fun Mascot(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 96.dp,
    animateBob: Boolean = true
) {
    val bobOffsetPx: Float = if (animateBob) {
        val transition = rememberInfiniteTransition(label = "mBob")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "mBob-v"
        )
        // 프로토: 6px @ 120 base
        -v * 6f * (sizeDp.value / 120f)
    } else 0f

    Canvas(
        modifier = modifier
            .size(sizeDp)
            .graphicsLayer { translationY = bobOffsetPx * density }
    ) {
        val s = this.size.width / 120f

        // 0) 얼굴 그림자 (프로토 .m-face box-shadow: 0 12px 26px rgba(49,130,246,.22))
        //    BlurMaskFilter 로 실제 블러 처리. y+12, blur 13 (CSS 26의 절반 ≈ Gaussian σ).
        drawIntoCanvas { canvas ->
            val shadowPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(56, 49, 130, 246) // .22 alpha
                maskFilter = android.graphics.BlurMaskFilter(
                    13f * s,
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
            canvas.nativeCanvas.drawCircle(
                60f * s,
                74f * s + 12f * s,
                46f * s,
                shadowPaint
            )
        }

        // 1) 얼굴: 92x92 원, left:14 bottom:0 → 중심(60, 74), 반지름 46
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(MascotFaceLight, MascotFaceDark),
                start = Offset(100f * s, 30f * s),
                end = Offset(20f * s, 118f * s)
            ),
            radius = 46f * s,
            center = Offset(60f * s, 74f * s)
        )

        // 2) 모자 챙(brim): 100x14, hat 의 bottom:-4 위치 → y=44..58, x=10..110
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(MascotHatDark, MascotHatBrimDark),
                start = Offset(0f, 44f * s),
                end = Offset(0f, 58f * s)
            ),
            topLeft = Offset(10f * s, 44f * s),
            size = Size(100f * s, 14f * s),
            cornerRadius = CornerRadius(7f * s)
        )

        // 3) 모자 본체: 78x42, left=21 top=6 → x=21..99 y=6..48
        //    border-radius: 39 39 6 6 → 위는 반원, 아래는 살짝
        val hatPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(21f * s, 6f * s, 99f * s, 48f * s),
                    topLeft = CornerRadius(39f * s),
                    topRight = CornerRadius(39f * s),
                    bottomRight = CornerRadius(6f * s),
                    bottomLeft = CornerRadius(6f * s)
                )
            )
        }
        drawPath(
            path = hatPath,
            brush = Brush.linearGradient(
                colors = listOf(MascotHatLight, MascotHatDark),
                start = Offset(0f, 6f * s),
                end = Offset(0f, 48f * s)
            )
        )

        // 4) 모자 꼭지: 14x10, top:-7 → y=-1..9, x=53..67
        drawRoundRect(
            color = MascotHatDark,
            topLeft = Offset(53f * s, -1f * s),
            size = Size(14f * s, 10f * s),
            cornerRadius = CornerRadius(5f * s)
        )

        // 5) 눈: 9x9 원, top:58, left:46 / left:65 → 중심(50.5,62.5), (69.5,62.5), 반지름 4.5
        drawCircle(MascotEyeColor, radius = 4.5f * s, center = Offset(50.5f * s, 62.5f * s))
        drawCircle(MascotEyeColor, radius = 4.5f * s, center = Offset(69.5f * s, 62.5f * s))

        // 6) 볼: 11x7 타원, top:66, left:39 / left:70, alpha .7
        val cheek = MascotCheekColor.copy(alpha = 0.7f)
        drawOval(cheek, topLeft = Offset(39f * s, 66f * s), size = Size(11f * s, 7f * s))
        drawOval(cheek, topLeft = Offset(70f * s, 66f * s), size = Size(11f * s, 7f * s))

        // 7) 입(미소): 20x11 박스 안 D자 호 — 타원 (50,56) 크기 20x22 의 하반부 (0°→180°).
        val smileStroke = 2.5f * s
        drawArc(
            color = MascotEyeColor,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(50f * s, 56f * s),
            size = Size(20f * s, 22f * s),
            style = Stroke(width = smileStroke)
        )
    }
}

/**
 * 채팅/답변 "AI 생각 중" 행. 프로토 .think-row (line 1060) + .think-bubble (line 1056) 1:1.
 *
 *   [막내(44dp)]  [회색 말풍선: 파란 점 3개 통통]   "막내가 답변을 준비 중이에요!"
 */
@Composable
fun MascotThinkingRow(
    modifier: Modifier = Modifier,
    text: String = "막내가 답변을 준비 중이에요!"
) {
    Row(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Mascot(sizeDp = 44.dp)
        ThinkBubble()
        Text(
            text = text,
            color = TossTextTertiary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * .think-bubble — 회색 말풍선 안에 파란 점 3개가 timing 16% 간격으로 통통.
 *   배경: var(--bg) #F4F5F7, 라운드 14, padding 11x15.
 *   점: 6x6, color var(--blue) #3182F6, opacity 0.3→1→0.3, translateY 0→-4→0, 1s cycle.
 */
@Composable
private fun ThinkBubble() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TossGrayBg)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ThinkDot(delayMs = 0)
        ThinkDot(delayMs = 160)
        ThinkDot(delayMs = 320)
    }
}

@Composable
private fun ThinkDot(delayMs: Int) {
    val transition = rememberInfiniteTransition(label = "thinkD-$delayMs")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing, delayMillis = delayMs),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkD-phase-$delayMs"
    )
    val bounce = kotlin.math.sin((phase * Math.PI).toFloat())
    val translate = -4f * bounce
    val a = 0.3f + 0.7f * bounce

    Box(
        modifier = Modifier
            .size(6.dp)
            .graphicsLayer {
                translationY = translate * density
                alpha = a
            }
            .clip(CircleShape)
            .background(TossBlue)
    )
}
