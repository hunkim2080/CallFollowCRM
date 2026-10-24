package com.detailline.callfollowcrm.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * ✏️ **펜 표시 색·바탕 — 프로토 56.. 「사진에 펜 표시」 그대로.** (2026-10-24)
 *
 *   사진 위에서 또렷이 보여야 하는 **표시용 색**이라 상태색(TossError 등)과 뜻이 다르다 → 따로 둔다.
 *   theme/ 에 두는 이유: 색 hex 는 여기(값의 출처)에만 — 화면 파일에 박으면 style_guard 가 막는다.
 */
val PEN_COLORS = listOf(
    Color(0xFFFF3B30), // 빨강(기본) — 「여기 해주세요」
    Color(0xFFFFD400), // 노랑
    Color(0xFF3182F6), // 파랑
    Color(0xFFFFFFFF)  // 흰
)

/** 펜 화면 바탕(아주 어두운 회색) · 도구바(조금 밝은 어두움) — 프로토 --page / --bar. */
val PenStageBg = Color(0xFF0E0F12)
val PenBarBg = Color(0xFF1A1C20)
