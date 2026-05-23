package com.detailline.callfollowcrm.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Toss 팔레트
val TossBlue = Color(0xFF3182F6)
val TossBlueDark = Color(0xFF1B64DA)
val TossBlueSoft = Color(0xFFE8F2FE)
val TossGrayBg = Color(0xFFF2F4F6)
val TossSurface = Color.White
val TossTextPrimary = Color(0xFF191F28)
val TossTextSecondary = Color(0xFF4E5968)
val TossTextTertiary = Color(0xFF8B95A1)
val TossDivider = Color(0xFFEAECEE)
val TossError = Color(0xFFF04452)
val TossWarning = Color(0xFFFFAA00)
val TossSuccess = Color(0xFF18BE5C)

private val TossColors = lightColorScheme(
    primary = TossBlue,
    onPrimary = Color.White,
    primaryContainer = TossBlueSoft,
    onPrimaryContainer = TossBlueDark,
    secondary = TossTextSecondary,
    onSecondary = Color.White,
    background = TossGrayBg,
    onBackground = TossTextPrimary,
    surface = TossSurface,
    onSurface = TossTextPrimary,
    surfaceVariant = TossGrayBg,
    onSurfaceVariant = TossTextTertiary,
    outline = TossDivider,
    outlineVariant = TossDivider,
    error = TossError,
    onError = Color.White
)

private val TossTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

private val TossShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun CallFollowCrmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TossColors,
        typography = TossTypography,
        shapes = TossShapes,
        content = content
    )
}
