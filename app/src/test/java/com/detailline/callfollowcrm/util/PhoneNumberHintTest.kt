package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 구글이 주는 번호를 우리 형식으로 바꾸기. (2026-09-16 사장님 "당근처럼 자동입력")
 *
 * 여기서 틀리면 **엉뚱한 번호로 가입**된다 — 앞자리가 통째로 달라지면
 * 인증 문자가 남에게 가거나, 아예 안 온다. 눈으로 확인하기 번거로워 테스트로 고정한다.
 */
class PhoneNumberHintTest {

    private fun conv(s: String?) = PhoneNumberHint.toLocalKorean(s)

    @Test
    fun `국제 형식을 국내 형식으로`() {
        assertEquals("01064610131", conv("+821064610131"))
        assertEquals("01012345678", conv("+82 10 1234 5678"))
        assertEquals("01012345678", conv("+82-10-1234-5678"))
    }

    @Test
    fun `0 을 안 뗀 채로 와도 처리한다`() {
        // 통신사·기기에 따라 +82 뒤에 0 이 그대로 붙어 오기도 한다
        assertEquals("01064610131", conv("+8201064610131"))
    }

    @Test
    fun `이미 국내 형식이면 그대로`() {
        assertEquals("01064610131", conv("01064610131"))
        assertEquals("01064610131", conv("010-6461-0131"))
    }

    @Test
    fun `옛 번호대도 받는다`() {
        assertEquals("0111234567", conv("+82111234567"))
        assertEquals("01712345678", conv("01712345678"))
    }

    @Test
    fun `한국 번호가 아니면 안 채운다`() {
        // 잘못 채우느니 사용자가 직접 치는 게 낫다
        assertNull(conv("+14155552671"))
        assertNull(conv("+819012345678"))
    }

    @Test
    fun `빈 값도 안전하다`() {
        assertNull(conv(null))
        assertNull(conv(""))
        assertNull(conv("   "))
        assertNull(conv("전화번호없음"))
    }

    @Test
    fun `길이가 안 맞으면 안 채운다`() {
        assertNull(conv("010123"))
        assertNull(conv("010123456789012"))
    }
}
