package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 짧은 번호(114·0000)가 대화방에서 통째로 안 보이던 것을 고치며. (2026-09-23 사장님) */
class PhoneMatchTest {

    @Test
    fun `짧은 번호는 번호 전체가 같아야 같은 상대`() {
        assertTrue(PhoneMatch.same("114", "114"))
        assertTrue(PhoneMatch.same("0000", "0000"))
        // 이게 핵심 — 끝자리만 맞추면 딸려오던 남의 번호.
        assertFalse(PhoneMatch.same("114", "0107770114"))
        assertFalse(PhoneMatch.same("0000", "01012340000"))
        assertFalse(PhoneMatch.same("114", "1140"))
    }

    @Test
    fun `보통 번호는 저장 포맷이 달라도 같은 사람`() {
        assertTrue(PhoneMatch.same("010-3404-5247", "01034045247"))
        assertTrue(PhoneMatch.same("+821034045247", "01034045247"))
        assertTrue(PhoneMatch.same("01034045247", "1034045247"))
    }

    @Test
    fun `다른 사람은 다르다`() {
        assertFalse(PhoneMatch.same("01034045247", "01034045248"))
        assertFalse(PhoneMatch.same("01012345678", "01087654321"))
    }

    @Test
    fun `빈 값은 언제나 다름`() {
        assertFalse(PhoneMatch.same("", ""))
        assertFalse(PhoneMatch.same("114", ""))
        assertFalse(PhoneMatch.same("글자만", "114"))
    }

    @Test
    fun `열쇠 - 보통 번호는 끝 8자리, 짧은 번호는 그대로`() {
        assertEquals("34045247", PhoneMatch.keyOf("010-3404-5247"))
        assertEquals("34045247", PhoneMatch.keyOf("+821034045247"))
        assertEquals("114", PhoneMatch.keyOf("114"))
        assertEquals("0000", PhoneMatch.keyOf("0000"))
        assertEquals("16001522", PhoneMatch.keyOf("1600-1522"))
    }
}
