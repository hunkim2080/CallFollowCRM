package com.detailline.callfollowcrm.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ☎️ **내가 건 전화에도 통화카드.** (2026-09-30 사장님)
 *
 *   "전화를 걸어도 이게 똑같이 떴으면 좋겠어… 내가 전화하면서도 메모나 일정 같은 걸 볼 수 있잖아"
 *
 * 거는 전화엔 **벨이 없어서** 안드로이드가 번호를 안 준다.
 * 그래서 앱에서 다이얼러를 열 때 번호를 적어두고, 통화가 시작되면 그걸 쓴다.
 *
 * 이 확인이 막는 것 — **엉뚱한 손님 카드.**
 *   적어둔 번호를 안 지우거나 오래된 걸 쓰면, 한참 뒤 딴 사람에게 건 전화에
 *   **엉뚱한 손님의 주소·잔금이 뜬다.** 통화하면서 그걸 보고 말하면 사고다.
 */
class PhoneDialerTest {

    @After fun tidy() = PhoneDialer.clear()

    @Test
    fun `적어둔 번호를 꺼내 쓴다`() {
        PhoneDialer.remember("010-1234-5678")
        assertEquals("010-1234-5678", PhoneDialer.take())
    }

    @Test
    fun `한 번 쓰면 사라진다 — 다음 통화에 새어 나가면 안 된다`() {
        PhoneDialer.remember("01012345678")
        assertEquals("01012345678", PhoneDialer.take())
        assertNull("두 번째 통화에 또 나오면 엉뚱한 카드가 뜬다", PhoneDialer.take())
    }

    @Test
    fun `적어둔 게 없으면 아무 말도 안 한다`() {
        PhoneDialer.clear()
        assertNull(PhoneDialer.take())
    }

    @Test
    fun `빈 번호는 기억하지 않는다`() {
        PhoneDialer.clear()
        PhoneDialer.remember("")
        PhoneDialer.remember("   ")
        PhoneDialer.remember(null)
        assertNull(PhoneDialer.take())
    }

    @Test
    fun `다이얼러만 열고 안 걸었으면 잊는다 — 3분이 지나면`() {
        PhoneDialer.remember("01099998888")
        // 적어둔 시각을 4분 전으로 되돌린다(실제로 4분을 기다릴 수는 없다).
        val f = PhoneDialer::class.java.getDeclaredField("pendingAtMs")
        f.isAccessible = true
        f.setLong(PhoneDialer, System.currentTimeMillis() - 4 * 60 * 1000L)
        assertNull("오래된 기억으로 카드를 띄우면 엉뚱한 손님이 뜬다", PhoneDialer.take())
    }

    @Test
    fun `3분 안이면 쓴다`() {
        PhoneDialer.remember("01077776666")
        val f = PhoneDialer::class.java.getDeclaredField("pendingAtMs")
        f.isAccessible = true
        f.setLong(PhoneDialer, System.currentTimeMillis() - 60 * 1000L)
        assertEquals("01077776666", PhoneDialer.take())
    }

    @Test
    fun `통화가 끝나면 지운다`() {
        PhoneDialer.remember("01055554444")
        PhoneDialer.clear()
        assertNull(PhoneDialer.take())
    }
}
