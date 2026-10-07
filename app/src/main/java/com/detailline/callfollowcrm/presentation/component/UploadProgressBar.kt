package com.detailline.callfollowcrm.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 📊 **올리는 중 진행바 — 앱 전체 업로드가 같이 쓰는 한 곳.** (2026-10-08 사장님)
 *   "올리는 중…" 글자만 있으면 멈춘 줄 안다 → **채워지는 바 + 「몇 / 몇」** 으로 진행이 눈에 보이게.
 *   채워지는 폭은 부드럽게 애니메이션. [done]==[total] 뒤 잠깐 더 도는 정리 단계는 바가 끝까지 찬 상태.
 *
 * @param done 올린 개수, [total] 전체 개수(0 이면 아직 셈 전 — 바는 빈 채로).
 * @param label 바 위 왼쪽 글자(기본 "올리는 중…").
 * @param sub   바 아래 작은 안내(선택).
 */
@Composable
fun UploadProgressBar(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    label: String = "올리는 중…",
    sub: String? = null,
) {
    val target = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val frac by animateFloatAsState(targetValue = target, label = "uploadFrac")
    Column(
        modifier.fillMaxWidth().clip(AppShape.md).background(TossGrayBg).padding(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = AppType.label, color = TossTextSecondary)
            if (total > 0) Text("$done / $total", style = AppType.label, color = TossBlue)
        }
        Spacer(Modifier.height(9.dp))
        // 트랙(흰 바탕) + 채워지는 파란 바.
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(AppShape.pill).background(AppTheme.colors.surface),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier.fillMaxWidth(frac).height(8.dp).clip(AppShape.pill).background(TossBlue)
            )
        }
        if (sub != null) {
            Spacer(Modifier.height(8.dp))
            Text(sub, style = AppType.caption, color = TossTextTertiary)
        }
    }
}
