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

// ── 기능 둘러보기 데모 공통(삼성 전화 화면·상태색) — 프로토 그대로. (2026-10-24) ──
val DemoCallTop = Color(0xFF2E3440)    // 삼성 통화화면 윗부분
val DemoCallBottom = Color(0xFF1C2028) // 아랫부분
val DemoCallGreen = Color(0xFF7FE0A8)  // 통화 중 시간 글자
val DemoRecRed = Color(0xFFFF5B52)     // 녹음 점
val DemoEndRed = Color(0xFFFF3B30)     // 통화 종료
val DemoCallBtn = Color(0x1FFFFFFF)    // 통화화면 버튼(흰 12%)
val DemoCallSub = Color(0xFFAEB6C2)    // 번호 등 옅은 글자
val DemoCallLabel = Color(0xFF8A93A0)  // "휴대전화" 라벨
val DemoOkSoft = Color(0xFFE7F7EF)     // 완료/수락 연초록
// 상태색(신규/예정/완료/기존) — 전화 미리보기·내 기록 등
val DemoStatusNew = Color(0xFFFF9F0A)
val DemoStatusSched = Color(0xFF12C06A)
val DemoStatusDone = Color(0xFFF0436A)
val DemoStatusExist = Color(0xFF3A86FF)
// 둘러보기용 글자 크기(큰 폰 한 대 — 미니 폰보다 조금 큼)
val DemoName = 13.sp    // 통화 상대 이름
val DemoNum = 10.sp     // 번호
val DemoTime = 11.sp    // 통화 중 2:47
val DemoSumTitle = 10.sp // 요약 카드 제목
val DemoNote = 7.5.sp   // AI 짐작 안내
val DemoHero = 27.sp    // 내 기록 큰 번호
