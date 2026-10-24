package com.detailline.callfollowcrm.presentation.screen.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.DemoBlueDark
import com.detailline.callfollowcrm.presentation.theme.DemoBody
import com.detailline.callfollowcrm.presentation.theme.DemoBtn
import com.detailline.callfollowcrm.presentation.theme.DemoCal
import com.detailline.callfollowcrm.presentation.theme.DemoDarkBar
import com.detailline.callfollowcrm.presentation.theme.DemoInputBg
import com.detailline.callfollowcrm.presentation.theme.DemoInputFill
import com.detailline.callfollowcrm.presentation.theme.DemoLabel
import com.detailline.callfollowcrm.presentation.theme.DemoLineSoft
import com.detailline.callfollowcrm.presentation.theme.DemoMicro
import com.detailline.callfollowcrm.presentation.theme.DemoOk
import com.detailline.callfollowcrm.presentation.theme.DemoSoftBlue
import com.detailline.callfollowcrm.presentation.theme.DemoStageBg
import com.detailline.callfollowcrm.presentation.theme.DemoTiny

/**
 * 📅 **접수서 보내기 — "이렇게 써요" 움직이는 안내 카드.** (2026-10-24 사장님 「가」, 프로토 P3L5USe9)
 *
 * 대화 화면 맨 위에 **첫 사용 때만** 뜬다(FirstRunHint 로 게이트, ⓘ 로 다시). 9초 한 바퀴로 자동 재생:
 *   ① 고객에게 접수서 링크 → ② 고객이 **날짜·주소** 입력 → ③ 보내기 → ④ **자동으로 내 캘린더에 쏙**.
 *   "입력 귀찮음"을 "고객이 알아서 적어주고 일정까지 자동"으로 — 전환율 유도([[project_conversion_show_reward_on_setup]]).
 *
 * 그림 글자는 폰마다 다르게 그려지는 이모지 대신 **Material 아이콘**(달력·주소·접수서)으로. (brand_guard)
 */
@Composable
fun IntakeDemoCard(
    onSendIntake: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ── 9초 한 바퀴 ──
    val inf = rememberInfiniteTransition(label = "intake-demo")
    val p by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    // 등장(알파)·채움(0..1) 헬퍼 — 임계값을 넘으면 짧게 나타난다.
    fun vis(t: Float) = ((p - t) / 0.025f).coerceIn(0f, 1f)
    fun fill(a: Float, b: Float) = ((p - a) / (b - a)).coerceIn(0f, 1f)
    val stepIdx = when { p < 0.34f -> 0; p < 0.57f -> 1; p < 0.74f -> 2; else -> 3 }
    val caption = when (stepIdx) {
        0 -> "① 고객에게 접수서 링크를 톡/문자로 보내요"
        1 -> "② 고객이 시공 날짜와 주소를 직접 적어요"
        2 -> "③ ‘보내기’ 누르면 끝"
        else -> "④ 자동으로 내 캘린더에 쏙 — 날짜도 주소도"
    }

    Column(
        modifier
            .fillMaxWidth()
            .tossCardShadow(AppShape.lg)
            .clip(AppShape.lg)
            .background(AppTheme.colors.surface)
            .padding(16.dp)
    ) {
        // ── 머리줄: 제목 + ✕ ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(AppTheme.colors.primary))
            Spacer(Modifier.width(8.dp))
            Text("접수서, 이렇게 받아요", style = AppType.headline, color = AppTheme.colors.text, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "닫기", tint = AppTheme.colors.textHint, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "고객이 날짜와 주소를 직접 적어줘요. 보내면 자동으로 내 캘린더에 쏙 — 다시 안 물어도 돼요.",
            style = AppType.caption, color = AppTheme.colors.textSub
        )
        Spacer(Modifier.height(12.dp))

        // ── 무대: 미니 폰 2대 ──
        Row(
            Modifier.fillMaxWidth().clip(AppShape.md).background(DemoStageBg).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 사장님 폰
            DemoPhone(barColor = AppTheme.colors.primary, barLabel = "내 시공막내", modifier = Modifier.weight(1f)) {
                SentBubble()
                Spacer(Modifier.height(6.dp))
                Box(Modifier.alpha(vis(0.07f))) { LinkCard(mine = true) }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.alpha(vis(0.74f))) { ResultCalCard(dotOn = p >= 0.80f) }
            }
            // 고객 폰
            DemoPhone(barColor = DemoDarkBar, barLabel = "고객", modifier = Modifier.weight(1f)) {
                Box(Modifier.alpha(vis(0.27f))) { LinkCard(mine = false) }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.alpha(vis(0.34f))) {
                    Column {
                        FieldLabel(Icons.Default.DateRange, "시공 날짜")
                        DemoInput("10/16(목) 오전 9시", fill(0.41f, 0.47f))
                        Spacer(Modifier.height(5.dp))
                        FieldLabel(Icons.Default.Place, "주소")
                        DemoInput("서울 강남구 …로 123", fill(0.49f, 0.55f))
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(AppShape.sm)
                                .background(AppTheme.colors.primary.copy(alpha = if (p in 0.57f..0.64f) 1f else 0.7f))
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("보내기", fontSize = DemoBtn, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                    }
                }
            }
        }

        Spacer(Modifier.height(11.dp))
        // 단계 점
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(4) { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(if (i == stepIdx) 8.dp else 7.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (i == stepIdx) AppTheme.colors.primary else AppTheme.colors.line)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(caption, style = AppType.caption, color = AppTheme.colors.textSub, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        Spacer(Modifier.height(11.dp))
        // 주소만 받기 한 줄
        Row(
            Modifier.fillMaxWidth().clip(AppShape.md).background(DemoSoftBlue).padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Place, null, tint = DemoBlueDark, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("주소만 급할 땐 — ‘주소만 받기’로 주소 한 칸만", style = AppType.caption, color = DemoBlueDark, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))
        TossPrimaryButton(text = "접수서 보내기", onClick = onSendIntake, modifier = Modifier.fillMaxWidth())
    }
}

/** 미니 폰 틀 — 상단 바(색) + 본문. */
@Composable
private fun DemoPhone(
    barColor: Color,
    barLabel: String,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit
) {
    Column(
        modifier
            .height(238.dp)
            .clip(AppShape.lg)
            .background(Color.White)
            .border(1.dp, DemoLineSoft, AppShape.lg)
    ) {
        Row(
            Modifier.fillMaxWidth().background(barColor).padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.Center
        ) { Text(barLabel, fontSize = DemoMicro, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        Column(Modifier.padding(8.dp)) { body() }
    }
}

/** 사장님 폰 — 내가 보낸 말풍선. */
@Composable
private fun SentBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.clip(AppShape.sm).background(AppTheme.colors.primary).padding(horizontal = 7.dp, vertical = 5.dp)
        ) { Text("접수서 보내드릴게요", fontSize = DemoTiny, color = Color.White) }
    }
}

/** 접수서 링크 카드 — 진짜 톡 링크처럼. */
@Composable
private fun LinkCard(mine: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(AppShape.sm)
            .background(if (mine) DemoSoftBlue else Color.White)
            .border(1.dp, if (mine) DemoSoftBlue else DemoLineSoft, AppShape.sm)
            .padding(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Edit, null, tint = DemoBlueDark, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(3.dp))
            Text("시공 접수서", fontSize = DemoBody, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
        }
        Spacer(Modifier.height(2.dp))
        Text("날짜·주소만 적어주세요", fontSize = DemoCal, color = AppTheme.colors.textSub)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().clip(AppShape.sm).background(AppTheme.colors.primary).padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) { Text("작성하기", fontSize = DemoCal, fontWeight = FontWeight.ExtraBold, color = Color.White) }
    }
}

/** 입력 라벨(아이콘 + 글). */
@Composable
private fun FieldLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 3.dp)) {
        Icon(icon, null, tint = AppTheme.colors.textHint, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = DemoLabel, fontWeight = FontWeight.Bold, color = AppTheme.colors.textHint)
    }
}

/** 고객이 채우는 입력칸 — [fillFrac] 만큼 파랗게 차고, 다 차면 글자가 보인다. */
@Composable
private fun DemoInput(text: String, fillFrac: Float) {
    Box(
        Modifier.fillMaxWidth().height(17.dp).clip(AppShape.sm)
            .background(DemoInputBg).border(1.dp, DemoLineSoft, AppShape.sm),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth(fillFrac).height(17.dp).background(DemoInputFill))
        Text(
            text, fontSize = DemoCal, color = AppTheme.colors.text, maxLines = 1,
            modifier = Modifier.padding(start = 5.dp).alpha(((fillFrac - 0.75f) / 0.25f).coerceIn(0f, 1f))
        )
    }
}

/** 사장님 폰 도착 — 자동으로 캘린더에 등록. */
@Composable
private fun ResultCalCard(dotOn: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(AppShape.sm).background(DemoSoftBlue)
            .border(1.dp, DemoSoftBlue, AppShape.sm).padding(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DateRange, null, tint = DemoBlueDark, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(3.dp))
            Text("캘린더에 자동 등록", fontSize = DemoBody, fontWeight = FontWeight.ExtraBold, color = DemoBlueDark, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(AppShape.pill).background(DemoOk).padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(7.dp))
                Spacer(Modifier.width(1.dp))
                Text("완료", fontSize = DemoMicro, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text("10/16(목) 오전 9시", fontSize = DemoBody, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
        Spacer(Modifier.height(4.dp))
        // 미니 달력 한 주 — 16일에 파란 점.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val days = listOf(12, 13, 14, 15, 16, 17, 18)
            days.forEach { d ->
                val hi = d == 16 && dotOn
                Box(
                    Modifier.size(15.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (hi) AppTheme.colors.primary else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$d", fontSize = DemoCal, color = if (hi) Color.White else AppTheme.colors.textSub,
                        fontWeight = if (hi) FontWeight.ExtraBold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Place, null, tint = AppTheme.colors.textSub, modifier = Modifier.size(9.dp))
            Spacer(Modifier.width(2.dp))
            Text("서울 강남구 …로 123", fontSize = DemoLabel, color = AppTheme.colors.textSub)
        }
    }
}
