package com.detailline.callfollowcrm.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * 📅 **접수서 데모 카드 전용 색·글자크기** — 프로토 「접수서 보내기 데모」(artifact P3L5USe9) 그대로. (2026-10-24)
 *
 *   미니 폰 2대 안의 아주 작은 글자(7~9sp)와 데모 전용 색(고객폰 어두운 바 등)은 디자인 토큰에 없다.
 *   화면 파일에 hex·sp 를 박으면 style_guard 가 막으니 **값의 출처인 theme/ 한 곳**에 모은다(PenPalette 와 같은 방식).
 */

// ── 색 ──
val DemoDarkBar = Color(0xFF30343B)   // 고객 폰 상단 바(사장님 폰은 AppTheme.colors.primary)
val DemoSoftBlue = Color(0xFFEAF2FE)  // 연한 파랑 바탕(링크카드·결과카드)
val DemoBlueDark = Color(0xFF1B64DA)  // 진한 파랑 글자
val DemoOk = Color(0xFF12B886)        // ✓ 완료 뱃지(초록)
val DemoStageBg = Color(0xFFF1F4F7)   // 데모 무대 바탕(연회색)
val DemoPhoneBg = Color(0xFFFFFFFF)   // 폰 바탕
val DemoInputBg = Color(0xFFF1F4F7)   // 입력칸 바탕
val DemoInputFill = Color(0xFFDCEAFE) // 입력칸 채워지는 색
val DemoLineSoft = Color(0xFFE7EBF0)  // 연한 테두리

// ── 글자 크기(미니 폰 안) ──
val DemoTiny = 7.sp     // 말풍선·세부
val DemoMicro = 6.5.sp  // 상태바·시각
val DemoLabel = 8.sp    // 입력 라벨·주소줄
val DemoBody = 8.5.sp   // 제목줄·링크카드 제목
val DemoBtn = 9.sp      // 보내기·작성하기
val DemoCal = 7.sp      // 달력 날짜
