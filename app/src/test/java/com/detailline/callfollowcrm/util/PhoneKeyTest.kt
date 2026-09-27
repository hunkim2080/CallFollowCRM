package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 번호 맞추기. (2026-09-27 사장님 "as 명단 올렸지만 칩이 안생김. 어떤이유?")
 *
 * 그날 터진 게 정확히 이것이었다 — 상담함은 글자 그대로 비교해서
 * `010-4805-2630` 과 `01048052630` 을 딴 사람으로 봤고, 그 손님은 화면에서 통째로 빠졌다.
 * 같은 계산이 36개 파일에 흩어져 있었으므로, 한 곳으로 모으고 **여기서 못 박는다.**
 */
class PhoneKeyTest {

    @Test
    fun `하이픈이 있든 없든 같은 사람`() {
        assertTrue(PhoneKey.same("010-4805-2630", "01048052630"))
        assertEquals(PhoneKey.of("010-4805-2630"), PhoneKey.of("01048052630"))
    }

    @Test
    fun `나라번호가 붙어도 같은 사람`() {
        assertTrue(PhoneKey.same("+82 10-4805-2630", "01048052630"))
        assertTrue(PhoneKey.same("821048052630", "010-4805-2630"))
    }

    @Test
    fun `글자가 섞여도 숫자만 본다`() {
        assertTrue(PhoneKey.same("010 4805 2630 (집)", "01048052630"))
    }

    @Test
    fun `앞 네 자리만 같은 건 딴 사람`() {
        assertFalse(PhoneKey.same("010-4805-2630", "010-4805-9999"))
    }

    @Test
    fun `짧은 번호는 자르지 않는다`() {
        // 114·1588 같은 번호는 8자리가 안 된다 — 자를 게 없으니 있는 그대로.
        assertEquals("114", PhoneKey.of("114"))
        assertTrue(PhoneKey.same("114", "114"))
        assertFalse(PhoneKey.same("114", "1141"))
    }

    @Test
    fun `빈 값끼리는 같다고 하지 않는다`() {
        // 🔴 여기가 제일 위험하다 — 빈 값을 같다고 하면 **번호 없는 손님들이 전부 한 사람**이 된다.
        assertFalse(PhoneKey.same(null, null))
        assertFalse(PhoneKey.same("", ""))
        assertFalse(PhoneKey.same("이름만 있음", "다른 이름"))
        assertEquals("", PhoneKey.of(null))
        assertEquals("", PhoneKey.of("   "))
    }

    @Test
    fun `지도로 찾으면 형식이 달라도 걸린다`() {
        data class P(val name: String, val phone: String)
        val list = listOf(P("해시", "010-2197-2496"), P("디테일라인", "01080056674"))
        val byKey = PhoneKey.mapBy(list) { it.phone }
        assertEquals("해시", byKey[PhoneKey.of("+821021972496")]?.name)
        assertEquals("디테일라인", byKey[PhoneKey.of("010-8005-6674")]?.name)
    }

    @Test
    fun `같은 열쇠가 겹치면 먼저 온 것이 이긴다`() {
        data class P(val name: String, val phone: String)
        val list = listOf(P("먼저", "01048052630"), P("나중", "010-4805-2630"))
        assertEquals("먼저", PhoneKey.mapBy(list) { it.phone }[PhoneKey.of("01048052630")]?.name)
    }

    @Test
    fun `찾기 — 없으면 null`() {
        data class P(val phone: String)
        val list = listOf(P("010-1111-2222"))
        assertNull(PhoneKey.find(list, "010-3333-4444") { it.phone })
        assertNull(PhoneKey.find(list, null) { it.phone })
        assertNull(PhoneKey.find(list, "") { it.phone })
    }
}
