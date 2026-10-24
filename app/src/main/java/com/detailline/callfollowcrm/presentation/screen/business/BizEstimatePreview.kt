package com.detailline.callfollowcrm.presentation.screen.business

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * 📄 **입력하면 이렇게 나와요 — 라이브 도장 견적서 미리보기.** (2026-10-24 사장님 "전환율 — 보상을 보여주자")
 *
 * 사업자 정보 폼 위에 올린다. 상호·대표·직인을 **치는 대로** 이 견적서가 바뀌고,
 *   미리보기가 **천천히 자동 스크롤**(머리말→항목→도장→합계)하며 **도장이 톡 찍히는** 움직임을 반복한다.
 *   "입력하세요"(귀찮음)를 "내 도장 견적서가 완성되는 걸 봄"(하고 싶음)으로. 실제 보상물은 QuoteDocScreen.
 */
@Composable
fun BizEstimatePreview(
    bizName: String,
    repName: String,
    phone: String,
    sealText: String,
    modifier: Modifier = Modifier
) {
    val co = bizName.ifBlank { "○○ 줄눈" }
    val rep = repName.ifBlank { "정민수" }
    val ph = phone.filter { it.isDigit() }.takeIf { it.isNotBlank() }
        ?.let { PhoneNumberFormatter.formatProgressive(it) } ?: "010-4805-2630"
    val stamp = sealText.ifBlank { co }

    // ── 움직임 — 9초 한 바퀴. 스크롤(머리말→도장)과 도장 찍힘을 한 시계로. ──
    val inf = rememberInfiniteTransition(label = "biz-preview")
    val scrollT by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 9000
                0f at 0 with LinearEasing
                0f at 1300 with FastOutSlowInEasing     // 머리말에서 잠깐 멈춤
                1f at 4500 with FastOutSlowInEasing      // 천천히 아래로(도장·합계)
                1f at 6000 with FastOutSlowInEasing      // 도장 보며 멈춤
                0f at 9000 with FastOutSlowInEasing      // 다시 위로
            },
            repeatMode = RepeatMode.Restart
        ), label = "scroll"
    )
    val stampP by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 9000
                0f at 0 with LinearEasing
                0f at 4000 with LinearEasing            // 아래로 다 내려올 때까지 숨어 있다가
                1f at 4800 with FastOutSlowInEasing      // 톡 찍힌다
                1f at 9000 with LinearEasing
            },
            repeatMode = RepeatMode.Restart
        ), label = "stamp"
    )

    val density = LocalDensity.current
    val winDp = 268.dp
    val winPx = with(density) { winDp.toPx() }
    var contentPx by remember { mutableIntStateOf(0) }
    val maxPan = (contentPx - winPx).coerceAtLeast(0f)

    Column(
        modifier.fillMaxWidth().clip(AppShape.lg).background(AppTheme.colors.primaryBg).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, bottom = 9.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(TossError))
            Spacer(Modifier.size(6.dp))
            Text("미리보기 — 저장하면 이 견적서가 나가요", style = AppType.caption, color = AppTheme.colors.primaryText, fontWeight = FontWeight.ExtraBold)
        }

        // 종이(흰 카드) 창 — 안의 내용은 더 길고, 천천히 위로 스크롤된다.
        Box(
            Modifier.fillMaxWidth().height(winDp).tossCardShadow(AppShape.md).clip(AppShape.md).background(Color.White)
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .onSizeChanged { contentPx = it.height }
                    .graphicsLayerTranslateY(-maxPan * scrollT)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                // ── 머리말 ──
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("견 적 서", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black, letterSpacing = 5.sp)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(co, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                        Text("대표 $rep", fontSize = 11.sp, color = TossTextSecondary)
                        Text(ph, fontSize = 11.sp, color = TossTextSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color.Black))
                Spacer(Modifier.height(10.dp))

                // ── 항목 ──
                DocItem("화장실 줄눈 시공", "2개소", "260,000")
                DocItem("주방 줄눈 시공", "1식", "150,000")
                DocItem("출장비", "", "서비스")
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("합계 (부가세 별도)", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                    Text("410,000원", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
                }

                // ── 서명란 + 직인 — 실제 견적서(QuoteDocScreen)와 같은 **빨간 네모 음각** 도장. 톡 찍힌다.
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text("위와 같이 견적합니다.", fontSize = 11.sp, color = TossTextSecondary, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.graphicsLayerStamp(stampP).size(62.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 빨강 꽉 찬 네모
                        Box(Modifier.matchParentSize().clip(AppShape.sm).background(TossError))
                        // 안쪽 흰 테두리 + 흰 글자(음각)
                        Box(
                            Modifier.size(52.dp).clip(AppShape.sm)
                                .border(1.2.dp, Color.White, AppShape.sm)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stamp.replace(" ", ""),
                                    fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black,
                                    color = Color.White, textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(2.dp))
                                Box(Modifier.size(30.dp, 1.dp).background(Color.White))
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "대표 $rep",
                                    fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold,
                                    color = Color.White, textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "↓ 아래를 채우면 위가 바뀌어요",
            style = AppType.caption, color = AppTheme.colors.primaryText, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DocItem(name: String, qty: String, price: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row {
            Text(name, fontSize = 12.sp, color = Color.Black)
            if (qty.isNotBlank()) {
                Spacer(Modifier.size(6.dp))
                Text(qty, fontSize = 11.sp, color = TossTextPrimary.copy(alpha = 0.45f))
            }
        }
        Text(price, fontSize = 12.sp, color = Color.Black)
    }
}

/** 세로 이동(스크롤) — graphicsLayer 로 리컴포지션 없이. */
private fun Modifier.graphicsLayerTranslateY(y: Float): Modifier =
    this.graphicsLayer { translationY = y }

/** 도장 찍힘 — 크고 흐리게 내려와(scale 2→1, alpha 0→1, 회전) 자리에 앉는다. */
private fun Modifier.graphicsLayerStamp(p: Float): Modifier =
    this.graphicsLayer {
        alpha = p
        val s = 2.0f + (1.0f - 2.0f) * p   // 2.0 → 1.0
        scaleX = s; scaleY = s
        rotationZ = 14f + (-8f - 14f) * p  // 14° → -8°(실제 도장 각도)
    }
