package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.util.CallSummaryLines

/**
 * 통화 요약 본문 — **시각 · 화자 점 · 문장.**
 *
 * 🔒 **통화 요약을 그리는 곳은 여기 하나다. 새 화면에서도 반드시 이걸 쓴다.**
 *
 * 왜 이 파일이 생겼나 (2026-09-25 사장님: "항상 프로토로 잘 만들고 적용할 때 되면 다 빼먹는거 같네")
 *   2026-09-24 에 「시간 구간으로 요약」을 넣으면서 **통화요약 화면만** 고쳤다.
 *   채팅 통화카드는 `.map { it.text }` 로 **시각과 화자를 버리고** 문장만 그리고 있었다.
 *   읽는 코드([CallSummaryLines])는 한 군데였는데 **그리는 코드가 두 벌**이라,
 *   한쪽만 새 모양이 됐고 사장님이 매일 보는 쪽이 옛 모양으로 남았다.
 *   → 그리는 것도 한 군데로 모은다. **한 군데뿐이면 빼먹을 수가 없다.**
 *
 * @param bullets 서버가 준 줄 목록. `0:00-0:46|나|문장` 또는 옛 모양(`고객: 문장`) 둘 다 받는다.
 * @param skipText 제목과 똑같은 줄은 뺀다(같은 말 두 번 방지). 없으면 다 그린다.
 */
@Composable
fun CallSummaryBody(
    bullets: List<String>,
    textColor: Color,
    timeColor: Color,
    /** `나` 가 말한 줄의 점 색. `손님` 은 [customerDot]. */
    ownerDot: Color,
    customerDot: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 20.sp,
    timeFontSize: TextUnit = 11.5.sp,
    timeWidth: Dp = 66.dp,
    rowGap: Dp = 7.dp,
    skipText: String? = null
) {
    val rows = bullets.mapNotNull { CallSummaryLines.parseOne(it) }
        .filter { skipText == null || it.text != skipText }
    Column(modifier) {
        rows.forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(rowGap))
            Row(verticalAlignment = Alignment.Top) {
                if (row.time.isBlank() && row.speaker.isBlank()) {
                    // 시각을 모르는 옛 요약 — 점 하나만 찍어 준다.
                    Text("· ", fontSize = fontSize, color = textColor, fontWeight = FontWeight.Bold)
                } else {
                    if (row.time.isNotBlank()) {
                        Text(
                            row.time, fontSize = timeFontSize, color = timeColor,
                            fontWeight = FontWeight.SemiBold, lineHeight = lineHeight, maxLines = 1,
                            modifier = Modifier.width(timeWidth)
                        )
                    }
                    if (row.speaker.isNotBlank()) {
                        Box(
                            Modifier.padding(top = 6.dp, end = 7.dp).size(6.dp).clip(CircleShape)
                                .background(if (row.speaker == "손님") customerDot else ownerDot)
                        )
                    }
                }
                Text(
                    row.text, fontSize = fontSize, color = textColor, lineHeight = lineHeight,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 이 요약에 시각·화자가 들어 있나 — "손님/나 는 AI 짐작" 안내를 띄울지 판단할 때. */
fun hasSpeakerGuess(bullets: List<String>): Boolean =
    bullets.any { it.count { c -> c == '|' } >= 2 }
