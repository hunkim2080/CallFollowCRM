package com.detailline.callfollowcrm.presentation.screen.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.DemoBody
import com.detailline.callfollowcrm.presentation.theme.DemoCal
import com.detailline.callfollowcrm.presentation.theme.DemoCallBottom
import com.detailline.callfollowcrm.presentation.theme.DemoCallBtn
import com.detailline.callfollowcrm.presentation.theme.DemoCallGreen
import com.detailline.callfollowcrm.presentation.theme.DemoCallLabel
import com.detailline.callfollowcrm.presentation.theme.DemoCallSub
import com.detailline.callfollowcrm.presentation.theme.DemoCallTop
import com.detailline.callfollowcrm.presentation.theme.DemoEndRed
import com.detailline.callfollowcrm.presentation.theme.DemoLabel
import com.detailline.callfollowcrm.presentation.theme.DemoLineSoft
import com.detailline.callfollowcrm.presentation.theme.DemoMicro
import com.detailline.callfollowcrm.presentation.theme.DemoName
import com.detailline.callfollowcrm.presentation.theme.DemoNote
import com.detailline.callfollowcrm.presentation.theme.DemoNum
import com.detailline.callfollowcrm.presentation.theme.DemoOk
import com.detailline.callfollowcrm.presentation.theme.DemoRecRed
import com.detailline.callfollowcrm.presentation.theme.DemoStageBg
import com.detailline.callfollowcrm.presentation.theme.DemoSumTitle
import com.detailline.callfollowcrm.presentation.theme.DemoTime
import com.detailline.callfollowcrm.presentation.theme.DemoTiny

/** 작은 상태바(9:41 + 신호 + 배터리). dark=글자 흰색. */
@Composable
internal fun DemoStatusBar(barColor: Color) {
    Row(
        Modifier.fillMaxWidth().background(barColor).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("9:41", fontSize = DemoMicro, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(Modifier.weight(1f))
        repeat(3) { Box(Modifier.padding(start = 2.dp).size(3.dp).clip(CircleShape).background(Color.White)) }
        Box(
            Modifier.padding(start = 3.dp).size(11.dp, 6.dp).border(1.dp, Color.White, AppShape.sm),
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(6.dp, 3.dp).background(Color.White)) }
    }
}

/**
 * 🎧 **통화 요약 데모 그림** — 삼성 통화 → (끊음) → 요약 중 → 시간순 요약 카드. (프로토 5tQjyBuib)
 *   지금 실제 동작: 요약 = 시각·화자 점·문장 / AI 짐작 / 시각 누르면 그 대목부터. 사용자가 못 고침.
 */
@Composable
fun CallSummaryDemoStage(p: Float) {
    val callA = ((0.30f - p) / 0.03f).coerceIn(0f, 1f)
    val sumA = minOf((p - 0.34f) / 0.03f, (0.57f - p) / 0.03f).coerceIn(0f, 1f)
    val resA = ((p - 0.57f) / 0.03f).coerceIn(0f, 1f)

    Box(
        Modifier.fillMaxWidth().height(354.dp).clip(AppShape.md).background(DemoStageBg),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.width(200.dp).height(320.dp).clip(AppShape.lg)
                .background(Color.White).border(1.dp, DemoLineSoft, AppShape.lg)
        ) {
            // ── 삼성 통화 화면 ──
            if (callA > 0.01f) Column(
                Modifier.fillMaxSize().alpha(callA)
                    .background(Brush.verticalGradient(listOf(DemoCallTop, DemoCallBottom)))
            ) {
                DemoStatusBar(Color.Transparent)
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("휴대전화", fontSize = DemoTiny, fontWeight = FontWeight.Bold, color = DemoCallLabel)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.size(54.dp).clip(CircleShape).background(DemoCallBtn), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("새 고객", fontSize = DemoName, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("010-1234-5678", fontSize = DemoNum, color = DemoCallSub)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(DemoRecRed))
                        Spacer(Modifier.width(5.dp))
                        Text("통화 중  2:47", fontSize = DemoTime, fontWeight = FontWeight.ExtraBold, color = DemoCallGreen)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CallChip("음소거"); CallChip("키패드"); CallChip("스피커")
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(DemoEndRed), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(20.dp).rotate(135f))
                    }
                    Spacer(Modifier.height(5.dp))
                    Text("통화 종료", fontSize = DemoMicro, color = DemoCallSub)
                }
            }

            // ── 요약 중 ──
            if (sumA > 0.01f) Column(
                Modifier.fillMaxSize().alpha(sumA).background(Color.White),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = AppTheme.colors.primary, strokeWidth = 3.dp)
                Spacer(Modifier.height(11.dp))
                Text("통화 내용 요약하는 중…", fontSize = DemoBody, fontWeight = FontWeight.Bold, color = AppTheme.colors.textSub)
            }

            // ── 요약 결과(앱 화면) ──
            if (resA > 0.01f) Column(Modifier.fillMaxSize().alpha(resA).background(Color.White)) {
                DemoStatusBar(AppTheme.colors.text)
                Row(
                    Modifier.fillMaxWidth().background(AppTheme.colors.primary).padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center
                ) { Text("내 시공막내", fontSize = DemoBody, fontWeight = FontWeight.ExtraBold, color = Color.White) }

                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(AppTheme.colors.primaryBg), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = AppTheme.colors.primary, modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("새 고객", fontSize = DemoBody, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
                            Text("010-1234-5678 · 방금", fontSize = DemoMicro, color = AppTheme.colors.textSub)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.primaryBg).padding(9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Call, null, tint = AppTheme.colors.primaryText, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("통화 요약", fontSize = DemoSumTitle, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.primaryText, modifier = Modifier.weight(1f))
                            Text("2분 47초", fontSize = DemoMicro, color = AppTheme.colors.textSub, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(5.dp))
                            Box(Modifier.clip(AppShape.pill).background(DemoOk).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("자동", fontSize = DemoMicro, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        SumSeg(true, "0:00", "화장실 줄눈 시공 문의")
                        SumSeg(true, "1:10", "주소 — 강남구 대왕판교로 123")
                        SumSeg(true, "2:05", "시공 희망일 10/16(목)")
                        SumSeg(false, "2:40", "견적 10만원 안내 · 계약금 1만")
                        Spacer(Modifier.height(4.dp))
                        Text("AI가 짐작한 내용이에요 · 시각을 누르면 그 대목부터 들어요", fontSize = DemoNote, color = AppTheme.colors.textHint)
                    }
                }
            }
        }
    }
}

/** 통화화면 버튼 한 개(동그라미 + 라벨). */
@Composable
private fun CallChip(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(DemoCallBtn))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = DemoTiny, color = DemoCallSub, fontWeight = FontWeight.Medium)
    }
}

/** 요약 한 줄(화자 점 + 시각 칩 + 문장). 고객=파랑 점, 나=회색 점. */
@Composable
private fun SumSeg(customer: Boolean, time: String, text: String) {
    Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 4.dp).size(5.dp).clip(CircleShape)
                .background(if (customer) AppTheme.colors.primary else AppTheme.colors.textHint)
        )
        Spacer(Modifier.width(5.dp))
        Box(
            Modifier.clip(AppShape.sm).background(AppTheme.colors.surface).padding(horizontal = 4.dp, vertical = 1.dp)
        ) { Text(time, fontSize = DemoCal, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.primaryText) }
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = DemoLabel, color = AppTheme.colors.text, lineHeight = DemoTime)
    }
}
