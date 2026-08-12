package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossTextInfo

/**
 * 빈 화면 공용 — 막내 캐릭터 + 파란 말풍선(제목) + 회색 부제. (2026-08-02 사장님 "빈 화면 다정하게")
 *   홈 `WaitingEmptyMascot`(프로토 .em-speech/.em-sub) 스타일을 그대로 부품화 → 모든 빈 화면 통일.
 *   ⚠️ 문구(speech/sub)는 각 화면의 기존 문구를 그대로 넣는다(§0 — 지어내기 X, 마스코트 '틀'만 통일).
 *
 * 전체 화면 빈 상태면 호출부에서 Box(fillMaxSize, Center){ MascotEmptyState(...) } 로 감싸 세로 중앙정렬.
 */
@Composable
fun MascotEmptyState(
    speech: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
    mascotSize: Dp = 80.dp
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Mascot(sizeDp = mascotSize)
        Spacer(Modifier.height(10.dp))
        // em-speech — 파란 pill + 위쪽 삼각 꼬리
        Box(contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.offset(y = (-5).dp).size(11.dp).rotate(45f).background(TossBlueSoft)
            )
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(TossBlueSoft)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    speech,
                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossBlueDark,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (!sub.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                sub,
                fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TossTextInfo,  // 빈 화면 안내는 정보성 → t3보다 진하게. (2026-08-12 접근성)
                textAlign = TextAlign.Center
            )
        }
    }
}
