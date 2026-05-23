package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/** Toss 스타일 큰 메인 버튼: 56dp 높이, full width, 굵은 글씨. */
@Composable
fun TossPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TossBlue,
            contentColor = Color.White,
            disabledContainerColor = TossDivider,
            disabledContentColor = TossTextTertiary
        )
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Toss 스타일 보조 버튼: 흰색 fill + 얇은 회색 border.
 * 하단 바 배경(TossGrayBg) 위에 올라가도 또렷하게 보이도록 흰색으로 변경.
 * (이전 회색 fill 은 같은 색 배경 위에서 사라져 보였음)
 */
@Composable
fun TossSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, TossDivider),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = TossTextPrimary,
            disabledContainerColor = Color.White,
            disabledContentColor = TossTextTertiary
        )
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Toss 스타일 텍스트 버튼 (서브 액션용). */
@Composable
fun TossTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = TossTextSecondary)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** 흰색 카드 + 16dp 라운드 + 그림자 없음. 토스 시그니처 섹션. */
@Composable
fun TossCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .background(Color.White, RoundedCornerShape(16.dp))
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(modifier = clickable.padding(contentPadding)) {
        content()
    }
}

/** 큰 숫자 + 라벨 (홈 상단 통계 카드). */
@Composable
fun TossStatCard(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    valueColor: Color = TossTextPrimary
) {
    TossCard(modifier = modifier, contentPadding = PaddingValues(20.dp)) {
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = TossTextTertiary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${value}건",
                style = MaterialTheme.typography.headlineMedium,
                color = valueColor
            )
        }
    }
}

/** 토스 칩: 둥근 알약 모양. */
@Composable
fun TossChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) TossBlueSoft else Color.White
    val fg = if (selected) TossBlue else TossTextSecondary
    val border = if (selected) TossBlue else TossDivider
    Surface(
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = fg,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

/** 작은 상태 배지 (미처리/처리완료 등). */
@Composable
fun TossBadge(
    text: String,
    color: Color = TossBlue,
    background: Color = TossBlueSoft,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = background
    ) {
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** 섹션 헤더 (작은 카테고리 라벨용). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = TossTextSecondary
    )
}
