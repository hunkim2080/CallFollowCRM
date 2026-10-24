package com.detailline.callfollowcrm.domain.ui

/**
 * ✏️ **펜으로 그린 획 한 줄 — 사진 크기와 무관한 0..1 좌표로.** (2026-10-24 펜 표시)
 *
 *   화면(PhotoViewer)에서 그릴 땐 화면 px 로 받지만, [PhotoFit.toNorm] 으로 **0..1** 로 바꿔 담는다.
 *   그래야 ViewModel 이 **어떤 해상도의 원본에도** `점*가로`, `굵기*가로` 로 그대로 얹을 수 있다.
 *   - [colorArgb] : 안드로이드 색 int (Compose Color.toArgb())
 *   - [normWidth] : 획 굵기 ÷ 사진 가로 (0..1)
 *   - [pts]       : (x,y) 상대위치 목록 (0..1)
 */
data class MarkStroke(
    val colorArgb: Int,
    val normWidth: Float,
    val pts: List<Pair<Float, Float>>
)
