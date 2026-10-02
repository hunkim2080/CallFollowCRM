package com.detailline.callfollowcrm.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚫 **앞자리로 광고 걸러내기** — 시험이 없었다. (2026-10-03)
 *
 *   사장님이 등록한 앞자리(070·0507 …)로 시작하는 번호는 상담함에서 빠지고
 *   자동문자·AI 도 안 붙는다.
 *
 *   ⚠️ **틀리면 손님을 놓친다.** 걸러내는 쪽 실수는 「광고가 하나 보인다」로 끝나지만,
 *      **010 손님을 잘못 거르면 그 문의는 영영 안 보인다.** 그래서 아래 「010 은 절대 안 걸린다」가 핵심.
 */
class SpamPrefixTest {

    private val 등록 = setOf("070", "0507", "050")

    @Test
    fun `070 인터넷전화는 걸린다`() {
        assertTrue(SpamPrefix.isSpam("07012345678", 등록))
        assertTrue(SpamPrefix.isSpam("070-1234-5678", 등록))
    }

    @Test
    fun `010 손님은 절대 안 걸린다 — 여기서 틀리면 문의가 영영 안 보인다`() {
        assertFalse(SpamPrefix.isSpam("01012345678", 등록))
        assertFalse(SpamPrefix.isSpam("010-3404-5247", 등록))
        // 지역번호를 등록해도 010 은 그대로 와야 한다
        assertFalse(SpamPrefix.isSpam("010-3404-5247", setOf("02", "031", "070")))
    }

    @Test
    fun `하이픈이나 공백이 섞여도 같게 본다`() {
        assertTrue(SpamPrefix.isSpam("070 1234 5678", 등록))
        assertTrue(SpamPrefix.isSpam("(070)1234-5678", 등록))
    }

    @Test
    fun `국제표기 +82 도 국내 모양으로 보고 센다`() {
        // +82 70-1234-5678 = 070-1234-5678
        assertTrue(SpamPrefix.isSpam("+82 70-1234-5678", 등록))
        assertFalse(SpamPrefix.isSpam("+82 10-3404-5247", 등록))
    }

    @Test
    fun `등록한 앞자리가 없으면 아무것도 안 거른다`() {
        assertFalse(SpamPrefix.isSpam("07012345678", emptySet()))
    }

    @Test
    fun `번호에 숫자가 없으면 안 거른다`() {
        assertFalse(SpamPrefix.isSpam("", 등록))
        assertFalse(SpamPrefix.isSpam("없음", 등록))
    }

    @Test
    fun `등록한 앞자리에 하이픈이 섞여 있어도 먹는다`() {
        assertTrue(SpamPrefix.isSpam("07012345678", setOf("070-")))
    }

    @Test
    fun `050 을 등록하면 0507 도 같이 걸린다 — 앞자리라서 그렇다`() {
        assertTrue(SpamPrefix.isSpam("05071234567", setOf("050")))
    }

    @Test
    fun `0507 만 등록하면 050 번호는 안 걸린다`() {
        assertTrue(SpamPrefix.isSpam("05071234567", setOf("0507")))
        assertFalse(SpamPrefix.isSpam("05012345678", setOf("0507")))
    }

    @Test
    fun `추천 앞자리 목록에 070 과 0507 이 들어 있다`() {
        assertTrue("070" in SpamPrefix.SUGGESTED)
        assertTrue("0507" in SpamPrefix.SUGGESTED)
        // 010 이 추천에 들어가면 **모든 손님이 걸린다** — 절대 없어야 한다
        assertFalse("010 이 추천 앞자리에 있으면 안 된다", "010" in SpamPrefix.SUGGESTED)
    }
}
