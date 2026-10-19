package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 문자 본문 URL 찾기 — **진짜 URL만**. 한글+마침표 스팸 문장이 링크가 되면 안 된다. (2026-10-16 사장님)
 */
class MessageUrlTest {

    // ── 링크가 되면 안 되는 것(한글+마침표·전화·숫자) ──
    @Test fun 한글문장_마침표_링크아님() {
        // 사장님 실제 스팸: 마침표 때문에 "명성입니다.변함없는성원" 이 주소로 오인됐던 것
        assertNull(MessageUrl.first("(광고)명성입니다.변함없는성원에감사드리며풍성한한가위되세요"))
        assertTrue(MessageUrl.find("명성입니다.변함없는성원에감사").isEmpty())
    }

    @Test fun 전화번호_금액_링크아님() {
        assertNull(MessageUrl.first("(광고)자동3연동89만원(시공비부가세포함)문의031-353-0352"))
        assertNull(MessageUrl.first("10월까지!싱글형여닫이3만원할인이벤트!"))
        assertNull(MessageUrl.first("무료거부 0808880062 인증80528"))
    }

    @Test fun 보통_문의_링크아님() {
        assertNull(MessageUrl.first("안녕하세요 화장실 줄눈 시공 견적 문의드려요"))
        assertNull(MessageUrl.first("원가25만원→이벤트가12만원"))
    }

    // ── 진짜 URL은 링크 ──
    @Test fun 스킴_URL은_링크() {
        assertEquals("http://x.co/y", MessageUrl.first("여기요 http://x.co/y 확인"))
        assertEquals("https://naver.me/xAbc12", MessageUrl.first("위치 https://naver.me/xAbc12 도착하면 전화주세요"))
    }

    @Test fun 스킴있는_한글도메인은_통째로_링크() {
        // 사장님 실제: https:// 뒤 한글 도메인이 ".eable.kr" 부터만 링크됐다 (2026-10-19)
        assertEquals("https://스마트홈인테리어.eable.kr", MessageUrl.first("견적서 https://스마트홈인테리어.eable.kr 확인하세요"))
        assertEquals("https://스마트홈인테리어.eable.kr", MessageUrl.first("https://스마트홈인테리어.eable.kr"))
    }

    @Test fun www_와_TLD호스트는_링크_스킴보정() {
        assertEquals("https://www.map.link", MessageUrl.first("www.map.link 확인 부탁드려요"))
        assertEquals("https://naver.me/abc", MessageUrl.first("naver.me/abc 로 보냈어요"))
        assertEquals("https://bit.ly/xyz", MessageUrl.first("할인 bit.ly/xyz"))
        assertEquals("https://cafe.naver.com/room", MessageUrl.first("cafe.naver.com/room 참고요"))
    }

    @Test fun 본문에_여러_URL이면_다_찾는다() {
        val hits = MessageUrl.find("http://a.com 그리고 b.kr/x 봐요")
        assertEquals(2, hits.size)
    }
}
