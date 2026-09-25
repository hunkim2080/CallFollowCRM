package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.util.RecordReel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🔢 **큰 숫자가 달린 만큼 올라간다.** (2026-09-25 사장님 "키로수도 후르륵 올라가는 느낌")
 *
 * 10초 영상 안에서 스쳐 지나가 눈으로는 못 보는 것들 —
 * 특히 **끝에 진짜 숫자가 찍히는지**. 광고로 나가는 그림이라 여기가 틀리면 안 된다.
 */
class RecordReelCountUpTest {

    @Test
    fun `끝엔 반드시 진짜 숫자다`() {
        assertEquals("약 671", RecordReel.countUp("약 671", 1f))
        assertEquals("1,250", RecordReel.countUp("1,250", 1f))
        assertEquals("26", RecordReel.countUp("26", 1.2f))
    }

    @Test
    fun `앞에 붙은 글자는 그대로 지킨다`() {
        // "약" 을 잃으면 **우리 거리가 정확하다고 말하는 게 된다** — 큰길만 있어 실제보다 작다.
        assertTrue(RecordReel.countUp("약 671", 0.5f).startsWith("약 "))
        assertEquals("약 336", RecordReel.countUp("약 671", 0.5f))
    }

    @Test
    fun `천 단위 콤마도 그대로`() {
        assertEquals("625", RecordReel.countUp("1,250", 0.5f))
        assertEquals("1,000", RecordReel.countUp("1,250", 0.8f))
    }

    @Test
    fun `처음엔 0에서 시작한다`() {
        assertEquals("0", RecordReel.countUp("26", 0f))
        assertEquals("약 0", RecordReel.countUp("약 671", 0f))
    }

    @Test
    fun `되감기지 않고 계속 오른다`() {
        var prev = -1L
        for (i in 0..100) {
            val now = RecordReel.countUp("1,250", i / 100f).filter { it.isDigit() }.toLong()
            assertTrue("숫자가 되감겼다: $prev → $now", now >= prev)
            prev = now
        }
        assertEquals(1250L, prev)
    }

    @Test
    fun `숫자가 없는 글자는 건드리지 않는다`() {
        assertEquals("첫 현장", RecordReel.countUp("첫 현장", 0.3f))
        assertEquals("", RecordReel.countUp("", 0.3f))
    }
}
