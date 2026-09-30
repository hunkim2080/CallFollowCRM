package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🎬 **곳이 많은 달은 길게.** (2026-09-30 사장님)
 *
 *   "현장에 따라 10-15초 나오게 해야 버벅이는 것처럼 안 보이지 않을까?"
 *
 * 전엔 **10초 고정**이었다. 3곳이든 12곳이든 10초 —
 * 많이 다닌 달일수록 트럭이 한 곳당 1초도 못 쓰고 튀듯 지나갔다.
 *
 * 이 확인이 지키는 것 —
 *   ① 사장님이 정한 **10~15초 밖으로 안 나간다** (짧으면 못 보고, 길면 올리기 부담)
 *   ② 곳이 늘면 **짧아지지 않는다**
 *   ③ 9월처럼 많이 다닌 달은 **한 구간에 1초 넘게** 쓴다
 */
class RecordReelSecondsTest {

    private fun perLeg(stops: Int): Float {
        val s = RecordReel.secondsFor(stops)
        val legs = (stops - 1).coerceAtLeast(1)
        return s * (1f - RecordReel.END_HOLD) / legs
    }

    @Test
    fun `사장님이 정한 10~15초를 벗어나지 않는다`() {
        for (stops in 0..60) {
            val s = RecordReel.secondsFor(stops)
            assertTrue("$stops 곳 → ${s}초 — 10초보다 짧다", s >= RecordReel.MIN_SECONDS)
            assertTrue("$stops 곳 → ${s}초 — 15초보다 길다", s <= RecordReel.MAX_SECONDS)
        }
    }

    @Test
    fun `곳이 늘어도 영상이 짧아지지는 않는다`() {
        var prev = 0f
        for (stops in 0..60) {
            val s = RecordReel.secondsFor(stops)
            assertTrue("$stops 곳에서 거꾸로 짧아졌다 ($prev → $s)", s >= prev - 0.001f)
            prev = s
        }
    }

    @Test
    fun `몇 곳 안 되는 달은 10초 그대로 — 괜히 늘어지지 않는다`() {
        assertEquals(10f, RecordReel.secondsFor(3), 0.01f)
        assertEquals(10f, RecordReel.secondsFor(5), 0.01f)
    }

    @Test
    fun `많이 다닌 달은 길어진다 — 9월은 10초로 모자랐다`() {
        // 사장님 2026년 9월 = 동네 10곳 + 출발 1 = 점 11개
        val sep = RecordReel.secondsFor(11)
        assertTrue("9월이 10초 그대로면 고친 게 없다 (지금 ${sep}초)", sep > 12f)
        assertTrue("15초는 넘지 않는다", sep <= 15f)
    }

    @Test
    fun `한 구간에 1초는 준다 — 눈이 못 따라가면 버벅이는 것처럼 보인다`() {
        // 15초로도 안 되는 만큼 많이 다닌 달(12곳+)은 어쩔 수 없다 — 사장님이 15초를 상한으로 정했다.
        for (stops in 2..12) {
            assertTrue("$stops 곳 — 한 구간 ${perLeg(stops)}초는 너무 빠르다", perLeg(stops) >= 1.0f)
        }
    }

    @Test
    fun `곳이 없거나 하나여도 터지지 않는다`() {
        assertEquals(10f, RecordReel.secondsFor(0), 0.01f)
        assertEquals(10f, RecordReel.secondsFor(1), 0.01f)
        assertEquals(10f, RecordReel.secondsFor(-3), 0.01f)
    }

    @Test
    fun `아주 많이 다닌 달도 15초에서 멈춘다`() {
        assertEquals(15f, RecordReel.secondsFor(40), 0.01f)
        assertEquals(15f, RecordReel.secondsFor(200), 0.01f)
    }
}
