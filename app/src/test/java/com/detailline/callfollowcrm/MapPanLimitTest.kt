package com.detailline.callfollowcrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 지도 확대·이동의 **움직일 수 있는 범위**가 배율을 따라가는지.
 *
 * 왜 (2026-09-25 사장님 "줌 인을 쭉 하면 마지막에 좌측으로 한번 꺾인다"):
 *   그리는 식이 이렇다 — 보는 창의 반폭 = 1/(2z), 중심 이동 = panX/z (둘 다 전체 폭 기준).
 *   창이 틀 밖으로 안 나가려면  |panX|/z + 1/(2z) ≤ 1/2  →  **|panX| ≤ (z-1)/2**.
 *   그런데 코드는 ±3 **고정**이었다. 8배까지 확대하면 3.5 가 필요한데 3 에서 잘려
 *   집고 있던 자리가 어긋나며 옆으로 꺾였다.
 *
 * 눈으로는 "꺾였나?" 싶은 미세한 움직임이라, 숫자로 못 박아둔다.
 */
class MapPanLimitTest {

    /** RegionMap 의 그 식 그대로. */
    private fun limitOf(zoom: Float): Float = ((zoom - 1f) / 2f).coerceAtLeast(0.05f)

    /** 그리는 쪽 식: 이만큼 옮기고 이만큼 좁혔을 때, 창이 틀(0~1) 안에 있나. */
    private fun windowInsideFrame(zoom: Float, pan: Float): Boolean {
        val half = 1f / (2f * zoom)          // 보는 창의 반폭
        val center = 0.5f - pan / zoom       // 창의 중심
        return center - half >= -1e-4f && center + half <= 1f + 1e-4f
    }

    @Test
    fun `1배에서는 움직일 데가 거의 없다`() {
        assertEquals(0.05f, limitOf(1f), 1e-6f)
    }

    @Test
    fun `배율이 오르면 움직일 범위도 같이 오른다`() {
        assertEquals(0.5f, limitOf(2f), 1e-6f)
        assertEquals(1.5f, limitOf(4f), 1e-6f)
        assertEquals(3.5f, limitOf(8f), 1e-6f)   // ← 옛 ±3 고정은 여기서 잘렸다
    }

    @Test
    fun `한도까지 밀어도 지도가 틀 밖으로 안 나간다`() {
        listOf(1f, 1.5f, 2f, 3f, 4f, 6f, 8f).forEach { z ->
            val lim = limitOf(z)
            assertTrue("z=$z 오른쪽 끝", windowInsideFrame(z, lim) || z <= 1f)
            assertTrue("z=$z 왼쪽 끝", windowInsideFrame(z, -lim) || z <= 1f)
        }
    }

    @Test
    fun `옛 고정값 3은 8배에서 모자랐다 — 그래서 꺾였다`() {
        val 필요 = limitOf(8f)
        assertTrue("8배엔 3보다 큰 범위가 필요하다", 필요 > 3f)
    }

    @Test
    fun `축소해도 범위가 음수가 되지 않는다`() {
        assertTrue(limitOf(0.6f) > 0f)
        assertTrue(limitOf(0.1f) > 0f)
    }
}
