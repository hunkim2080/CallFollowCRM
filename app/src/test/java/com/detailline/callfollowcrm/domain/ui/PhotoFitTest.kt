package com.detailline.callfollowcrm.domain.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 📐 펜 좌표 변환 — 폰에서 눈으로 못 보는 셈이라 **여기서 대신 본다.**
 *   실측 느낌의 값을 박아둔다: S23U 세로 화면(1080×2316)에 가로사진·세로사진을 넣었을 때.
 */
class PhotoFitTest {

    private fun assertClose(expected: Float, actual: Float, eps: Float = 0.001f) {
        assertEquals(expected.toDouble(), actual.toDouble(), eps.toDouble())
    }

    @Test fun `정사각 사진을 세로 화면에 넣으면 위아래 레터박스`() {
        // 1000x1000 을 500x1000 안에 → scale 0_5, 500x500, 가로여백0 세로여백250
        val b = PhotoFit.of(1000, 1000, 500f, 1000f)
        assertClose(500f, b.dispW); assertClose(500f, b.dispH)
        assertClose(0f, b.offX); assertClose(250f, b.offY)
    }

    @Test fun `가로 사진을 세로 화면에 넣으면 좌우 꽉 위아래 레터박스`() {
        // 2000x1000(가로 2대1) 을 1080x2316 안에 → scale 0_54 → 1080x540
        val b = PhotoFit.of(2000, 1000, 1080f, 2316f)
        assertClose(1080f, b.dispW); assertClose(540f, b.dispH)
        assertClose(0f, b.offX); assertClose((2316f - 540f) / 2f, b.offY)
    }

    @Test fun `세로 사진을 세로 화면에 넣으면 세로 꽉 좌우 레터박스`() {
        // 1000x2000 을 1080x2316 안에 → scale 1_08 → 1080x2160
        val b = PhotoFit.of(1000, 2000, 1080f, 2316f)
        assertClose(1080f, b.dispW); assertClose(2160f, b.dispH)
        assertClose(0f, b.offX); assertClose((2316f - 2160f) / 2f, b.offY)
    }

    @Test fun `화면 한가운데 탭은 사진 한가운데 절반절반`() {
        val b = PhotoFit.of(1000, 1000, 500f, 1000f) // offY 250
        val (nx, ny) = PhotoFit.toNorm(b, 250f, 500f)
        assertClose(0.5f, nx); assertClose(0.5f, ny)
    }

    @Test fun `사진 좌상단 모서리는 영영`() {
        val b = PhotoFit.of(2000, 1000, 1080f, 2316f) // offY 888 dispW 1080 dispH 540
        val (nx, ny) = PhotoFit.toNorm(b, 0f, b.offY)
        assertClose(0f, nx); assertClose(0f, ny)
    }

    @Test fun `레터박스 바깥을 찍으면 범위를 벗어난다`() {
        val b = PhotoFit.of(2000, 1000, 1080f, 2316f) // 위 레터박스는 y 가 offY(888) 보다 작다
        val (_, ny) = PhotoFit.toNorm(b, 540f, 0f)
        assert(ny < 0f) { "레터박스는 음수여야 하는데 $ny" }
    }

    @Test fun `빈 값이면 영 네모`() {
        val b = PhotoFit.of(0, 0, 500f, 1000f)
        assertClose(0f, b.dispW); assertClose(0f, b.dispH)
        val (nx, ny) = PhotoFit.toNorm(b, 10f, 10f)
        assertClose(0f, nx); assertClose(0f, ny)
    }
}
