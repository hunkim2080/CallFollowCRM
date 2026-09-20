package com.detailline.callfollowcrm.util

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 깨진 사진 판정의 **계산 부분** 검산. (2026-09-20)
 * 실제 자료 실측: 깨진 것 54~55 / 정상 3.9~14.1 → 기준 40.
 */
class ImageNoiseCheckTest {

    private fun grid(size: Int, f: (Int, Int) -> Int) =
        IntArray(size * size) { f(it % size, it / size) }

    @Test
    fun `무작위 노이즈는 기준을 훌쩍 넘는다`() {
        val r = Random(7)
        val lum = grid(200) { _, _ -> r.nextInt(256) }
        val s = ImageNoiseCheck.scoreOf(lum, 200)
        assertTrue("노이즈 점수가 $s — 40 을 넘어야 한다", s >= ImageNoiseCheck.THRESHOLD)
    }

    @Test
    fun `부드러운 사진은 기준에 한참 못 미친다`() {
        val lum = grid(200) { x, y -> (x + y) / 2 }   // 완만한 그라데이션
        assertTrue(ImageNoiseCheck.scoreOf(lum, 200) < 10.0)
    }

    /**
     * 🔑 **줄눈 사진이 오판되면 안 된다.** 세로 줄이 촘촘해도 가로 방향은 완만하다.
     * 그래서 **작은 쪽**을 본다 — 한 방향만 튀는 무늬는 깨진 게 아니다.
     */
    @Test
    fun `세로 줄무늬는 깨진 걸로 보지 않는다`() {
        val lum = grid(200) { x, _ -> if (x % 2 == 0) 20 else 235 }
        val s = ImageNoiseCheck.scoreOf(lum, 200)
        assertTrue("줄무늬 점수가 $s — 기준 아래여야 한다", s < ImageNoiseCheck.THRESHOLD)
    }

    @Test
    fun `가로 줄무늬도 깨진 걸로 보지 않는다`() {
        val lum = grid(200) { _, y -> if (y % 2 == 0) 20 else 235 }
        assertTrue(ImageNoiseCheck.scoreOf(lum, 200) < ImageNoiseCheck.THRESHOLD)
    }

    @Test
    fun `격자무늬도 깨진 걸로 보지 않는다`() {
        // 타일 줄눈처럼 20픽셀마다 선이 지나가는 무늬
        val lum = grid(200) { x, y -> if (x % 20 == 0 || y % 20 == 0) 40 else 200 }
        val s = ImageNoiseCheck.scoreOf(lum, 200)
        assertTrue("격자 점수가 $s — 기준 아래여야 한다", s < ImageNoiseCheck.THRESHOLD)
    }

    @Test
    fun `이상한 입력은 0`() {
        assertTrue(ImageNoiseCheck.scoreOf(IntArray(0), 0) == 0.0)
        assertTrue(ImageNoiseCheck.scoreOf(IntArray(4), 100) == 0.0)
    }

    @Test
    fun `바이트가 너무 작으면 판정 안 한다`() {
        assertTrue(ImageNoiseCheck.scoreOfJpeg(ByteArray(10)) == null)
        assertTrue(!ImageNoiseCheck.isNoisy(null))
    }
}
