package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 주소의 **위치**(파란 밑줄 칠 자리)와 **동/호수 분리**(등록창 자동 기입).
 * (2026-09-16 사장님: "상대방이 동호수를 적어서 보냈다면 그 주소가 자동으로 기입되도록")
 *
 * 위치가 한 글자라도 어긋나면 밑줄이 엉뚱한 데 그어지고, 동/호수를 잘못 떼면
 * 등록창에 이상한 값이 미리 채워진다 — 둘 다 눈으로는 알아채기 어려워 테스트로 고정한다.
 */
class AddressDongHoTest {

    // ── 위치(밑줄 칠 자리) ────────────────────────────────────────

    @Test
    fun `밑줄 자리가 본문의 주소 부분과 정확히 일치한다`() {
        val body = "안녕하세요 천호동 래미안 101동 1502호로 와주세요"
        val f = AddressExtractor.findOne(body)
        assertNotNull(f)
        assertEquals("천호동 래미안 101동 1502호", f!!.text)
        // 잘라낸 구간이 곧 표시할 글자와 같아야 밑줄이 정확히 그 위에 그어진다
        assertEquals(f.text, body.substring(f.start, f.end))
    }

    @Test
    fun `동호수를 이어붙인 경우에도 끝 위치가 맞는다`() {
        // 패턴이 "송파구 잠실로 88" 까지 잡고 뒤의 "101동 1503호" 를 이어붙이는 경로
        val body = "송파구 잠실로 88 101동 1503호 입니다"
        val f = AddressExtractor.findOne(body)
        assertNotNull(f)
        assertEquals(f!!.text, body.substring(f.start, f.end))
        org.junit.Assert.assertTrue("동호수까지 밑줄에 들어가야 함: ${f.text}",
            f.text.contains("101동") && f.text.contains("1503호"))
    }

    @Test
    fun `주소가 없으면 위치도 없다`() {
        assertNull(AddressExtractor.findOne("견적 250만원으로 진행할게요"))
    }

    // ── 동/호수 분리 (등록창 자동 기입) ──────────────────────────

    @Test
    fun `고객이 적어 보낸 동 호수를 뽑는다`() {
        val p = AddressExtractor.splitDongHo("천호동 래미안 101동 1502호")
        assertEquals("101", p.dong)
        assertEquals("1502", p.ho)
        assertEquals("천호동 래미안", p.base)
    }

    @Test
    fun `호만 적어 보냈으면 호만 채운다`() {
        val p = AddressExtractor.splitDongHo("힐스테이트 1502호")
        assertNull(p.dong)
        assertEquals("1502", p.ho)
        assertEquals("힐스테이트", p.base)
    }

    @Test
    fun `숫자 없는 행정동은 동으로 치지 않는다`() {
        // "천호동"의 '동'을 동호수로 오해하면 등록창에 엉뚱한 값이 채워진다
        val p = AddressExtractor.splitDongHo("서울 강동구 천호동 393-5")
        assertNull("행정동을 동호수로 잘못 뽑음: ${p.dong}", p.dong)
        assertNull(p.ho)
        assertEquals("서울 강동구 천호동 393-5", p.base)
    }

    @Test
    fun `안 적어 보냈으면 빈 칸으로 둔다`() {
        val p = AddressExtractor.splitDongHo("서울 강서구 마곡동 740")
        assertNull(p.dong)
        assertNull(p.ho)
    }

    @Test
    fun `빈 주소도 안전하다`() {
        val p = AddressExtractor.splitDongHo(null)
        assertEquals("", p.base)
        assertNull(p.dong)
        assertNull(p.ho)
    }
}
