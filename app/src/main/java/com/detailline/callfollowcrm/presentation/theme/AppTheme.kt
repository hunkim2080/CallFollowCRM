package com.detailline.callfollowcrm.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.R

// Toss 팔레트 — Light
// 2026-06-01 전면 리뉴얼: 값을 프로토타입(design-preview/ringgo-redesign.html :root)과 정확히 일치시킴.
//   --blue #3182F6 / --blue-dark #1B64DA / --blue-tint #EEF4FF / --bg #F4F5F7
//   --t1 #0B0F19 / --t2 #5A6472 / --t3 #9AA3AF / --line #EEF0F3
//   --error #F0436A / --amber #F6A609 / --success #16C172 / --purple #7C5CFC
val TossBlue = Color(0xFF3182F6)
val TossBlueDark = Color(0xFF1B64DA)
val TossBlueSoft = Color(0xFFEEF4FF)
val TossGrayBg = Color(0xFFF4F5F7)
val TossSurface = Color.White
val TossTextPrimary = Color(0xFF0B0F19)
val TossTextSecondary = Color(0xFF5A6472)
val TossTextTertiary = Color(0xFF9AA3AF)   // 프로토 원본값(--t3). 2026-07-30 어르신용 #7A8494 로 올렸다가 사장님 지시로 프로토 복원.
val TossDivider = Color(0xFFEEF0F3)
val TossSegTrack = Color(0xFFE7EAEF)   // 세그먼트 스위치 트랙(회색). 활성 알약=흰색+그림자. (2026-08-02)
val TossError = Color(0xFFF0436A)
val TossWarning = Color(0xFFF6A609)
val TossSuccess = Color(0xFF16C172)
val TossPurple = Color(0xFF7C5CFC)
/** 프로토 하단 탭바 비활성 아이콘 색 (#AEB5BF). */
val TossTabInactive = Color(0xFFAEB5BF)

// 2026-05-30 사장님 디자인 보강 #1 — 다크 팔레트.
//   사용 상황: 시공 현장(어두운 욕실/창고), 차 안 야간, 늦은 밤 통화 후속.
//   원칙: TossBlue / Error / Warning / Success 는 동일 유지 (브랜드 일관 + 가독).
//        Background / Surface / Text 만 반전 + 색상 톤 조정.
val TossBgDark = Color(0xFF161616)         // 거의 검정 (OLED 친화 — S9 OLED 디스플레이)
val TossSurfaceDark = Color(0xFF1F2126)    // 카드 표면 (배경보다 한 톤 밝게)
val TossTextPrimaryDark = Color(0xFFF2F4F6)
val TossTextSecondaryDark = Color(0xFFB0B8C1)
val TossTextTertiaryDark = Color(0xFF6E7782)
val TossDividerDark = Color(0xFF2E3338)
val TossBlueSoftDark = Color(0xFF1A2B4A)   // 다크 배경에 어울리는 약한 청색 컨테이너

private val TossLightColors = lightColorScheme(
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
    // 프로토 = 평평한 흰 surface. M3 기본 surfaceTint(=primary 파랑)를 끄지 않으면
    //   elevation 있는 Dialog/Sheet/Card 가 퍼렇게 틴트됨 → 투명으로 전역 차단. (2026-06-03)
    surfaceTint = Color.Transparent,
    surfaceVariant = TossGrayBg,
    onSurfaceVariant = TossTextTertiary,
    outline = TossDivider,
    outlineVariant = TossDivider,
    error = TossError,
    onError = Color.White
)

private val TossDarkColors = darkColorScheme(
    primary = TossBlue,                    // 브랜드 색 그대로 — 다크에서도 시인성 충분
    onPrimary = Color.White,
    primaryContainer = TossBlueSoftDark,
    onPrimaryContainer = Color(0xFF9FC3FF),
    secondary = TossTextSecondaryDark,
    onSecondary = TossBgDark,
    background = TossBgDark,
    onBackground = TossTextPrimaryDark,
    surface = TossSurfaceDark,
    onSurface = TossTextPrimaryDark,
    surfaceTint = Color.Transparent,   // 다크도 동일하게 틴트 차단 (위 설명 참조)
    surfaceVariant = TossSurfaceDark,
    onSurfaceVariant = TossTextTertiaryDark,
    outline = TossDividerDark,
    outlineVariant = TossDividerDark,
    error = TossError,
    onError = Color.White
)

/**
 * Pretendard 한글 폰트 패밀리.
 *
 * 2026-05-30 사장님 디자인 보강 #2 — 기본 Roboto → Pretendard 로 전환.
 *   이유: Roboto 는 한글 합성 폰트(Noto Sans CJK fallback)로 표시돼 자간/굵기 위계가 어색.
 *        Pretendard 는 한글 전용 + 영문/숫자 동일 그리드 → 토스 스타일 일관 톤.
 *
 * 파일 위치: res/font/pretendard_regular.otf ~ pretendard_extrabold.otf (5개 두께)
 * 크기 영향: APK +7.5MB (1.5MB × 5). 사장님 결정 = 시각 품질 우선.
 * 2026-07-30 ExtraBold(800) 추가 — 프로토 시그니처 제목 굵기. 없을 땐 800/900 요청이 700으로 강등됐음(디자인 감사 #1).
 */
val Pretendard = FontFamily(   // 입력칸(BasicTextField) 등에서 명시적으로 걸 수 있게 공개 (LocalTextStyle 안 타는 곳 대비)
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold)
)

private val TossTypography = Typography(
    displayLarge = TextStyle(fontFamily = Pretendard, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Pretendard, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineLarge = TextStyle(fontFamily = Pretendard, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = Pretendard, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Pretendard, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Pretendard, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontFamily = Pretendard, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Pretendard, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Pretendard, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Pretendard, fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Pretendard, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = Pretendard, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = Pretendard, fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

private val TossShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * 앱 전체 테마.
 *
 * 2026-05-30 사장님 디자인 보강 #1 — 다크모드 진입.
 *   기본 = 시스템 설정 따라감 (안드로이드 설정 → 다크모드 ON 이면 자동 적용).
 *   사장님이 앱 안에서 명시적으로 라이트/다크 토글하고 싶으면 darkOverride 인자로 강제 가능.
 *
 * @param darkOverride null = 시스템 따라감, true = 강제 다크, false = 강제 라이트
 */
@Composable
fun CallFollowCrmTheme(
    darkOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val isDark = darkOverride ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (isDark) TossDarkColors else TossLightColors,
        typography = TossTypography,
        shapes = TossShapes
    ) {
        // 전역 기본 글꼴 = Pretendard. 이게 없으면 style=typography.X 를 안 쓴 raw Text(앱의 ~98%)가
        //   폰 기본 글꼴로 그려짐 → 의도한 Pretendard(프로토 스펙)가 거의 안 먹었음. 한 줄로 앱 전체 통일. (2026-08-02 사장님)
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides
                androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = Pretendard),
            content = content
        )
    }
}
