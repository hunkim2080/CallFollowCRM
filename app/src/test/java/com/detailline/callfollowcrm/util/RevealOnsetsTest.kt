package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🎵 **벨 '짠' 소리가 그림 리빌과 같은 시점에 떨어지는지** — [RecordReel.revealOnsets].
 *   [RecordReel.warpTime] 과 같은 셈이어야 소리·그림이 안 어긋난다(두 벌 방지).
 */
class RevealOnsetsTest {

    @Test
    fun `핵심 현장이 없으면 빈 배열 — 터지지 않는다`() {
        assertEquals(0, RecordReel.revealOnsets(FloatArray(0), 0.16f).size)
    }

    @Test
    fun `리빌 시점은 순서대로 늘어난다 — 뒤 현장이 먼저 울리면 안 된다`() {
        val feat = floatArrayOf(0.2f, 0.5f, 0.8f)
        val on = RecordReel.revealOnsets(feat, 0.16f)
        assertEquals(3, on.size)
        var prev = -1f
        for (v in on) {
            assertTrue("리빌 시점이 거꾸로 갔다 ($prev → $v)", v > prev)
            assertTrue("여정 진행도 0..1 밖이다 ($v)", v in -0.0001f..1.0001f)
            prev = v
        }
    }

    @Test
    fun `warpTime 과 같은 셈 — 리빌 시작에서 warp 가 그 현장을 가리킨다`() {
        // revealOnsets 가 주는 '리빌 시작' jT 를 warpTime 에 넣으면 그 순번 리빌이 시작(revealProg≈0)되어야 한다.
        val feat = floatArrayOf(0.25f, 0.6f, 0.9f)
        val rf = RecordReel.REVEAL_FRAC
        val on = RecordReel.revealOnsets(feat, rf)
        for (i in feat.indices) {
            val w = RecordReel.warpTime(on[i] + 0.0005f, feat, rf)
            assertEquals("리빌 $i 시작인데 warp 가 다른 순번을 가리킨다", i, w.revealIdx)
        }
    }

    @Test
    fun `리빌 하나뿐이어도 괜찮다`() {
        val on = RecordReel.revealOnsets(floatArrayOf(0.5f), 0.16f)
        assertEquals(1, on.size)
        assertTrue(on[0] in 0f..1f)
    }
}
