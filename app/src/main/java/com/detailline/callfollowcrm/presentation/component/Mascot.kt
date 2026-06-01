package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 막내 비서 캐릭터 (2026-06-01) — 안전모 쓴 둥근 얼굴. Compose Canvas (이모지/이미지 아님).
 *   "쓸수록 사장님 닮아가는 막내 비서" 브랜드 모먼트. 출시 때 업종별 전문 일러스트로 교체 예정.
 */
@Composable
fun Mascot(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 96.dp,
    helmetColor: Color = Color(0xFFFFC107),
    faceColor: Color = Color(0xFFFFE0B2)
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val s = size.minDimension
        val cx = size.width / 2f

        val faceR = s * 0.34f
        val faceCy = size.height * 0.58f
        // 얼굴
        drawCircle(faceColor, faceR, Offset(cx, faceCy))

        // 안전모 돔 (위 절반 호)
        val helmetR = s * 0.42f
        val helmetCy = size.height * 0.46f
        drawArc(
            color = helmetColor,
            startAngle = 180f, sweepAngle = 180f, useCenter = true,
            topLeft = Offset(cx - helmetR, helmetCy - helmetR),
            size = Size(helmetR * 2, helmetR * 2)
        )
        // 안전모 챙
        drawRoundRect(
            color = Color(0xFFFFB300),
            topLeft = Offset(cx - helmetR * 1.08f, helmetCy - s * 0.02f),
            size = Size(helmetR * 2.16f, s * 0.07f),
            cornerRadius = CornerRadius(s * 0.035f, s * 0.035f)
        )
        // 안전모 중앙 능선
        drawRoundRect(
            color = Color(0xFFFFB300),
            topLeft = Offset(cx - s * 0.025f, helmetCy - helmetR),
            size = Size(s * 0.05f, helmetR),
            cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
        )

        // 눈
        val eyeR = s * 0.035f
        val eyeY = faceCy - faceR * 0.05f
        drawCircle(Color(0xFF3B3B3B), eyeR, Offset(cx - faceR * 0.36f, eyeY))
        drawCircle(Color(0xFF3B3B3B), eyeR, Offset(cx + faceR * 0.36f, eyeY))

        // 볼터치
        drawCircle(Color(0x33F0436A), s * 0.04f, Offset(cx - faceR * 0.55f, eyeY + faceR * 0.32f))
        drawCircle(Color(0x33F0436A), s * 0.04f, Offset(cx + faceR * 0.55f, eyeY + faceR * 0.32f))

        // 미소
        val smileR = faceR * 0.5f
        drawArc(
            color = Color(0xFF3B3B3B),
            startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(cx - smileR, eyeY + faceR * 0.1f),
            size = Size(smileR * 2, smileR * 2),
            style = Stroke(width = s * 0.025f, cap = StrokeCap.Round)
        )
    }
}
