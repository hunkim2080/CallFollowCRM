package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 🤝 협업 진행 4단계 가로 스텝 — 배정·출발·도착·완료. **앱 안 두 곳이 쓰는 한 곳**(두 벌 금지 §12):
 *   ① 공유받은 현장 상세(SharedSiteScreen) ② 고객 상세에서 상대 진행 보기(CustomerCollabParts).
 *
 *   ✅ **끝낸 단계 = 초록 ✓**, 그다음 할 단계 = 파란 번호(지금). (2026-11-24 사장님 "출발했는데 출발이 체크 안 돼")
 *     [reachedIdx] = 지금까지 도달한 마지막 단계(배정 0 · 출발 1 · 도착 2 · 완료 3).
 *     그 칸까지 ✓, 바로 다음 칸이 '지금'. → '도착 알리기' 버튼과 눈금이 어긋나지 않는다
 *     (출발하면 출발 ✓ · 도착이 '지금'). 예전엔 도달 단계를 '지금'(번호)으로 그려
 *     출발을 눌러도 출발이 체크 안 된 것처럼 보였다.
 *
 *   쿠팡식 **푸시·알림센터 트래커**([com.detailline.callfollowcrm.domain.collab.CollabProgressSteps])는
 *   역할이 달라(도달점을 '지금'으로 깜빡이는 배송추적) 따로 둔다 — §12-I.
 */
@Composable
fun CollabStepper(
    reachedIdx: Int,
    modifier: Modifier = Modifier,
    dotSize: Dp = 30.dp,
    numSp: TextUnit = 13.sp,
    labelSp: TextUnit = 11.sp,
) {
    val steps = listOf("배정", "출발", "도착", "완료")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, label ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val done = i <= reachedIdx          // 끝낸 단계 = ✓
                val cur = i == reachedIdx + 1       // 그다음 할 단계 = 지금(번호)
                val bg = when { done -> AppTheme.colors.done; cur -> AppTheme.colors.primary; else -> TossGrayBg }
                val fg = if (done || cur) Color.White else TossTextTertiary
                Box(Modifier.size(dotSize).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                    Text(if (done) "✓" else "${i + 1}", fontSize = numSp, fontWeight = FontWeight.ExtraBold, color = fg)
                }
                Spacer(Modifier.height(5.dp))
                Text(label, fontSize = labelSp, fontWeight = FontWeight.Bold,
                    color = if (done || cur) TossTextPrimary else TossTextTertiary)
            }
        }
    }
}
