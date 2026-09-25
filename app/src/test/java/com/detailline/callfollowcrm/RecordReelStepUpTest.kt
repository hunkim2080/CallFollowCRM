package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.util.RecordReel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 💰 **돈은 집마다 다르게 들어온다.** (2026-09-26 사장님 "각 지역마다 금액이 다른데 그렇게 올라야지")
 *
 * 전엔 **달린 거리**에 맞춰 고르게 올랐다 — 200만원짜리를 지나든 100만원짜리를 지나든 똑같이.
 * 이제 도착할 때마다 **그 동네 몫만큼** 오른다. 10초 영상에서는 눈으로 세기 어려워 여기서 본다.
 */
class RecordReelStepUpTest {

    /** 9월 실제 비슷하게 — 단원 100 · 관악 200 · 강서 100 · 분당 100 · 권선 0(아직 안 적음). */
    private val money = listOf(100f, 200f, 100f, 100f, 0f)

    /** 도착하고 반 초 뒤 = 다 굴러간 뒤. */
    private fun after(i: Int) = RecordReel.stepUp(money, i, 0.6f, 0f)

    @Test
    fun `200만원짜리는 100만원짜리보다 두 배 뛴다`() {
        val jump1 = after(0)                 // 단원 100
        val jump2 = after(1) - after(0)      // 관악 200
        val jump3 = after(2) - after(1)      // 강서 100
        assertEquals("관악은 단원의 두 배여야", jump1 * 2, jump2, 1e-4f)
        assertEquals("강서는 단원과 같아야", jump1, jump3, 1e-4f)
    }

    @Test
    fun `마지막 집에서 정확히 다 찬다`() {
        assertEquals(1f, after(money.size - 1), 1e-4f)
    }

    @Test
    fun `도착 순간엔 아직 안 올라가고 반 초 동안 굴러간다`() {
        val atArrive = RecordReel.stepUp(money, 1, 0f, 0f)
        val mid = RecordReel.stepUp(money, 1, 0.275f, 0f)
        val done = RecordReel.stepUp(money, 1, 0.55f, 0f)
        assertEquals("도착 순간 = 직전 집까지의 몫", after(0), atArrive, 1e-4f)
        assertTrue("반쯤 굴러야", mid > atArrive && mid < done)
        assertEquals("반 초면 다 굴러야 — 트럭이 떠날 때 딱 멈춘다", after(1), done, 1e-4f)
    }

    @Test
    fun `돈을 안 적은 집에선 안 오른다`() {
        // 권선은 0원. 거기 도착해도 숫자가 안 변해야 거짓말이 안 된다.
        assertEquals(after(3), after(4), 1e-4f)
    }

    @Test
    fun `되감기지 않는다`() {
        var prev = -1f
        for (i in money.indices) for (s in 0..10) {
            val now = RecordReel.stepUp(money, i, s / 10f * 0.55f, 0f)
            assertTrue("되감김: $prev → $now", now >= prev - 1e-5f)
            prev = now
        }
    }

    @Test
    fun `몫이 없으면 달린 거리를 그대로 쓴다`() {
        // 거리(km)는 집마다 나뉘지 않는다 — 달린 만큼 이어서 올라야 한다.
        assertEquals(0.42f, RecordReel.stepUp(emptyList(), 2, 0.3f, 0.42f), 1e-5f)
        assertEquals(0.42f, RecordReel.stepUp(listOf(0f, 0f), 1, 0.3f, 0.42f), 1e-5f)
    }

    @Test
    fun `도착 번호가 범위를 넘어도 터지지 않는다`() {
        assertEquals(1f, RecordReel.stepUp(money, 99, 1f, 0f), 1e-4f)
        assertTrue(RecordReel.stepUp(money, -3, 1f, 0f) >= 0f)
    }
}
