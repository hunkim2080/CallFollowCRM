package com.detailline.callfollowcrm.domain.ui

/**
 * 📐 **ContentScale.Fit 로 사진이 화면 안에 그려지는 네모 + 화면좌표→사진 안 0..1.** (2026-10-24 펜 표시)
 *
 *   펜으로 그린 획을 **원본 해상도 사진에 정확히 얹으려면** — 화면에서 사진이 실제로 차지하는
 *   네모(레터박스 제외)와, 그 안에서의 상대위치(0..1)를 알아야 한다. 폰에서 눈으로 못 보는 셈이라
 *   **여기 순수 함수로 떼어 단위테스트로 받친다**(PhotoFitTest). 쓰는 곳: 펜 화면(PhotoViewer) 한 곳.
 */
data class FitBox(
    /** 사진이 실제로 그려지는 폭·높이(px) */
    val dispW: Float,
    val dispH: Float,
    /** 화면 좌상단에서 사진 네모까지의 여백(레터박스, px) */
    val offX: Float,
    val offY: Float
)

object PhotoFit {

    /** (bw×bh) 사진을 (stageW×stageH) 안에 **Fit**(비율유지·전체보이게) 로 넣었을 때 그려지는 네모. */
    fun of(bw: Int, bh: Int, stageW: Float, stageH: Float): FitBox {
        if (bw <= 0 || bh <= 0 || stageW <= 0f || stageH <= 0f) return FitBox(0f, 0f, 0f, 0f)
        val scale = minOf(stageW / bw, stageH / bh)
        val dw = bw * scale
        val dh = bh * scale
        return FitBox(dw, dh, (stageW - dw) / 2f, (stageH - dh) / 2f)
    }

    /**
     * 화면좌표(x,y) → 사진 안 상대위치 (0..1).
     *   사진 네모 **밖**이면 음수·1초과가 나온다(그대로 둔다 — 합성 때 비트맵 밖이라 안 그려진다).
     */
    fun toNorm(box: FitBox, x: Float, y: Float): Pair<Float, Float> {
        if (box.dispW <= 0f || box.dispH <= 0f) return 0f to 0f
        return ((x - box.offX) / box.dispW) to ((y - box.offY) / box.dispH)
    }
}
