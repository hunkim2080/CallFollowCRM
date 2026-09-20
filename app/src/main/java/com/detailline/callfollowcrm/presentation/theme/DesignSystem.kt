package com.detailline.callfollowcrm.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🎨 **디자인 시스템 — 이름 한 곳.** (2026-09-20 사장님)
 *
 * ## 왜 생겼나
 * 코드 330개 파일을 훑어보니 **정해둔 색 22개에 실제로 쓰는 색이 325가지**였다.
 * 그중 **303가지가 화면에서 손으로 적은 것**(704번). 같은 뜻인데 값이 갈려 있었다 —
 * 초록 7가지 · 검정 6가지 · 주황 6가지 · 진한 파랑 5가지 · 보라 3가지.
 * 코드에도 그 자국이 남아 있다: *"같은 「일당」이 홈에선 #6D5AE6, 기다려요 카드에선 #7C5CFC"*.
 *
 * ## 쓰는 법
 * 화면에서는 **이름으로만** 쓴다 — `AppTheme.colors.unpaidText` · `AppType.headline` ·
 * `AppShape.md` · `AppSpace.s16` · `AppSize.rowMin`.
 * @Composable 이 아닌 자리(top-level val 등)에서는 [LightColors] 를 직접 본다.
 *
 * ## 옛 이름
 * `TossBlue` 같은 옛 이름은 **아직 지우지 않는다.** 화면을 하나씩 옮기는 동안 둘이 같이 산다.
 * 값이 같은 자리부터 바꾸므로 **보이는 변화는 0** 이어야 한다.
 *
 * ## ⚠️ 아직 안 정한 것
 * `textHint`(#9AA3AF) 를 `textSub`(#5A6472) 로 올리자는 제안이 있다 — **야외 가독성**.
 * 그런데 **2026-07-30 에 같은 걸 했다가(#7A8494) 사장님이 되돌리라고 하셨다.**
 * 그래서 지금은 **옛 값 그대로** 둔다. 상담함 한 화면에서 눈으로 보신 뒤 정한다.
 *
 * 출처: Claude Design 이 만든 시스템 — artifact/D6xHxNsDf3dz9PnUFjCbNn
 */
@Immutable
data class AppColors(
    /** 화면 바탕. 모든 화면의 가장 아래 면. */
    val bg: Color,
    /** 카드·칩·검색창·시트·탭바의 면. bg 위에 얹는다. */
    val surface: Color,
    /** 보조 버튼, 사람 아이콘 원, 회색 딱지, 둘째 줄 칩의 바탕. */
    val surfaceMuted: Color,
    /** 눌림 — 흰 면·회색 면을 누르고 있는 동안. */
    val surfacePressed: Color,
    /** 목록 줄 사이 구분선(1dp). 장식용이라 뜻을 선 하나에만 싣지 말 것. */
    val line: Color,
    /** 카드 테두리 — 밝은 화면에선 안 보이고(그림자가 대신) 어두운 화면에서만. */
    val lineCard: Color,
    /** 눌림 덮개 — **색이 있는 면**(띠·연한 버튼)을 누르는 동안 그 위에 덮는다. */
    val pressOverlay: Color,
    /** 시트·대화상자 뒤를 덮는 막. */
    val scrim: Color,
    /** 본문 글자. 제목·이름·전화번호·금액. 검정 계열은 전부 이것으로 합친다. */
    val text: Color,
    /** 정보를 담은 보조 글자: 대화 미리보기, 메타 줄, 시각, 기본 칩 글자, 섹션 머리. */
    val textSub: Color,
    /** 읽지 않아도 되는 글자만: 입력 안내, 흐림, 도움말. **금액·날짜·이름에는 쓰지 않는다.** */
    val textHint: Color,
    /** primary 채움 위의 글자. 그 위 글자는 항상 14sp Bold 이상(대비가 빠듯하다). */
    val textOnPrimary: Color,
    /** 앱의 파랑. 선택된 칩, 주 버튼, 오늘 날짜 원, 안 읽음 점. **한 화면에 채움 버튼은 하나.** */
    val primary: Color,
    /** primary 채움을 누르고 있는 동안. */
    val primaryPressed: Color,
    /** 파란 **글자**와 아이콘. #3182F6 을 글자로 쓰지 않는다(대비 3.7:1). */
    val primaryText: Color,
    /** 연한 파랑 면: 안내 띠, 연한 버튼, 선택된 줄, 이름 첫 글자 원. */
    val primaryBg: Color,
    /** 완료·확정의 **채움**. 혼자서는 뜻을 못 전한다 — 항상 낱말과 같이. */
    val done: Color,
    /** 완료·확정의 **글자**: 계약완료·완료 딱지·받음·입금. */
    val doneText: Color,
    /** 완료 딱지·완료 띠의 연한 면. */
    val doneBg: Color,
    /** 주의·요청의 채움. */
    val caution: Color,
    /** 주의·요청의 글자: 요청 딱지·A/S. */
    val cautionText: Color,
    /** 주의 딱지·주의 띠의 연한 면. */
    val cautionBg: Color,
    /** 미수·경고의 채움: 재연락 점, 숫자 뱃지. **돈·위험 말고는 빨강을 쓰지 않는다.** */
    val unpaid: Color,
    /** 미수·경고의 글자: N일째(8일부터)·미수 금액·오류·지우기 버튼. */
    val unpaidText: Color,
    /** 미수 딱지·경고 띠·지우기 버튼의 연한 면. */
    val unpaidBg: Color,
    /** 분류의 채움. 상태가 아니라 '어떤 종류인가'를 말할 때만. */
    val category: Color,
    /** 분류의 글자: 협업·일당 딱지. */
    val categoryText: Color,
    /** 분류 딱지의 연한 면. */
    val categoryBg: Color,
    /** 지난 것·끝난 것의 채움. */
    val neutral: Color,
    /** 회색 딱지의 글자: N일째(7일까지)·종료·지난. */
    val neutralText: Color,
    /** 회색 딱지의 면. */
    val neutralBg: Color
)

/** 밝은 화면. @Composable 이 아닌 자리에서는 이걸 직접 본다. */
val LightColors = AppColors(
    bg = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEEF0F3),
    surfacePressed = Color(0xFFE9ECF0),
    line = Color(0xFFEEF0F3),
    lineCard = Color(0x00FFFFFF),
    pressOverlay = Color(0x0F0B0F19),
    scrim = Color(0x800B0F19),
    text = Color(0xFF0B0F19),
    textSub = Color(0xFF5A6472),
    textHint = Color(0xFF9AA3AF),
    textOnPrimary = Color(0xFFFFFFFF),
    primary = Color(0xFF3182F6),
    primaryPressed = Color(0xFF1B64DA),
    primaryText = Color(0xFF1B64DA),
    primaryBg = Color(0xFFEEF4FF),
    done = Color(0xFF16C172),
    doneText = Color(0xFF08803C),
    doneBg = Color(0xFFE7F8EF),
    caution = Color(0xFFF6A609),
    cautionText = Color(0xFF946000),
    cautionBg = Color(0xFFFFF5DC),
    unpaid = Color(0xFFF0436A),
    unpaidText = Color(0xFFCF2450),
    unpaidBg = Color(0xFFFFEAF0),
    category = Color(0xFF7C5CFC),
    categoryText = Color(0xFF6246EA),
    categoryBg = Color(0xFFF1EDFF),
    neutral = Color(0xFF9AA3AF),
    neutralText = Color(0xFF5A6472),
    neutralBg = Color(0xFFEEF0F3)
)

/**
 * 어두운 화면. ⚠️ **지금은 안 쓰인다** — AppRoot 가 `darkOverride = false` 로 밝은 화면을 고정한다.
 *   손으로 적은 색 303가지는 어두운 화면에서 안 바뀌던 색이다. 이름으로 바꾸는 순간
 *   그 자리가 어두운 화면을 따라가므로, 어두운 화면을 켜려면 화면마다 한 번씩 열어 봐야 한다.
 */
val DarkColors = AppColors(
    bg = Color(0xFF161616),
    surface = Color(0xFF212328),
    surfaceMuted = Color(0xFF2E3138),
    surfacePressed = Color(0xFF33373F),
    line = Color(0xFF2E3138),
    lineCard = Color(0xFF2E3138),
    pressOverlay = Color(0x14FFFFFF),
    scrim = Color(0x99000000),
    text = Color(0xFFF2F4F7),
    textSub = Color(0xFFA9B1BD),
    textHint = Color(0xFF858F9C),
    textOnPrimary = Color(0xFFFFFFFF),
    primary = Color(0xFF3182F6),
    primaryPressed = Color(0xFF1B64DA),
    primaryText = Color(0xFF6AA6FF),
    primaryBg = Color(0xFF16294A),
    done = Color(0xFF16C172),
    doneText = Color(0xFF3DDC91),
    doneBg = Color(0xFF10301F),
    caution = Color(0xFFF6A609),
    cautionText = Color(0xFFFFC24D),
    cautionBg = Color(0xFF3A2A08),
    unpaid = Color(0xFFF0436A),
    unpaidText = Color(0xFFFF7D9A),
    unpaidBg = Color(0xFF3F1623),
    category = Color(0xFF7C5CFC),
    categoryText = Color(0xFFA994FF),
    categoryBg = Color(0xFF2A2152),
    neutral = Color(0xFF6B7480),
    neutralText = Color(0xFFA9B1BD),
    neutralBg = Color(0xFF2E3138)
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

/** 화면에서 색을 꺼내는 곳. `AppTheme.colors.unpaidText` 처럼 쓴다. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

/**
 * 글자 8단계. 38가지이던 크기를 여기로 모은다.
 *   ⚠️ 가장 작은 것이 **12sp** — S9+ 야외에서 읽히는 하한. 이보다 작게 쓰지 않는다.
 *   숫자가 줄맞춤돼야 하는 자리(금액)는 `fontFeatureSettings = "tnum"` 이 걸려 있다.
 */
object AppType {
    /** 34 — 화면에 하나뿐인 핵심 숫자(이번 달 매출·정산 합계). 한 화면에 한 번. */
    val hero = TextStyle(
        fontFamily = Pretendard, fontSize = 34.sp, lineHeight = 42.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    )
    /** 26 — 탭 화면 큰 제목(상담함·일정·정산·통계·더보기). */
    val display = TextStyle(
        fontFamily = Pretendard, fontSize = 26.sp, lineHeight = 34.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp
    )
    /** 22 — 상단바 제목, 달력 연·월, 목록 위 합계 금액, 시트 제목. */
    val title = TextStyle(
        fontFamily = Pretendard, fontSize = 22.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp,
        fontFeatureSettings = "tnum"
    )
    /** 17 — 줄 제목(이름·단지명·전화번호), 카드 제목, 큰 버튼. 한 줄, 넘치면 말줄임. */
    val headline = TextStyle(
        fontFamily = Pretendard, fontSize = 17.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp
    )
    /** 15 — 읽는 글: 대화 미리보기, 말풍선, 입력, 설정 항목. */
    val body = TextStyle(
        fontFamily = Pretendard, fontSize = 15.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Medium
    )
    /** 14 — 누르는 것과 머리말: 칩, 작은 버튼, 띠 문구, 섹션 머리, 줄 오른쪽 금액. */
    val label = TextStyle(
        fontFamily = Pretendard, fontSize = 14.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"
    )
    /** 13 — 메타 줄: 잔금·날짜 요약, 시각, 도움말, 달력 범례. */
    val caption = TextStyle(
        fontFamily = Pretendard, fontSize = 13.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    )
    /** 12 — 가장 작은 글자: 딱지, 탭바 이름, 달력 요일, 숫자 뱃지. **하한**. */
    val micro = TextStyle(
        fontFamily = Pretendard, fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Bold
    )
}

/** 여백 8단계. 34가지이던 것을 여기로. **헷갈리면 s16**. */
object AppSpace {
    /** 글자와 글자 사이 미세 간격. */
    val s2 = 2.dp
    /** 딱지 사이, 아이콘과 글자 사이, 줄 안 글줄 사이. */
    val s4 = 4.dp
    /** 칩 사이, 카드 사이(같은 묶음), 딱지 좌우 안쪽. */
    val s8 = 8.dp
    /** 목록 한 줄 위아래, 원과 글자 사이, 띠 위아래, 섹션 머리와 카드 사이. */
    val s12 = 12.dp
    /** 화면 좌우, 카드 안쪽, 칩 좌우 안쪽, 띠 좌우 안쪽. **기본값**. */
    val s16 = 16.dp
    /** 큰 카드(달력·요약) 안쪽, 시트 안쪽. */
    val s20 = 20.dp
    /** 섹션과 섹션 사이. */
    val s24 = 24.dp
    /** 화면 제목 위, 빈 화면 안내의 위아래. */
    val s32 = 32.dp
}

/** 모서리 5단계. 25가지이던 것을 여기로. */
object AppShape {
    /** 8 — 딱지, 카드 안 작은 상자, 사진 썸네일. (6·7·9·10 → 8) */
    val sm = RoundedCornerShape(8.dp)
    /** 12 — 카드, 버튼, 띠, 입력창, 말풍선. **카드는 12 로 통일** (11·13·14·15 → 12). */
    val md = RoundedCornerShape(12.dp)
    /** 16 — 큰 카드(달력·요약), 대화상자. (16·18 → 16) */
    val lg = RoundedCornerShape(16.dp)
    /** 24 — 아래에서 올라오는 시트의 윗모서리. (20~28 → 24) */
    val xl = RoundedCornerShape(24.dp)
    /** 999 — 누르는 알약: 칩, 검색창, 둥근 아이콘 버튼. **딱지에는 쓰지 않는다.** */
    val pill = RoundedCornerShape(999.dp)
}

/** 크기. 장갑 낀 손 기준. */
object AppSize {
    /** 48 — 누르는 모든 것의 최소 크기. 보이는 모양이 더 작아도 누르는 영역은 48. */
    val touchMin = 48.dp
    /** 40 — 칩 높이. */
    val chipH = 40.dp
    /** 52 — 화면 너비를 채우는 큰 버튼. */
    val buttonH = 52.dp
    /** 40 — 줄 안의 작은 버튼(재연락·일정 추가). */
    val buttonSmH = 40.dp
    /** 40 — 목록 한 줄의 원. */
    val avatar = 40.dp
    /** 72 — 목록 한 줄의 최소 높이. */
    val rowMin = 72.dp
}
