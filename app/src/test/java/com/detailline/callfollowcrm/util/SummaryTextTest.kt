package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ✨ **요약 맨 앞 이모지 떼기** — 시험이 없었다. (2026-10-03)
 *
 *   사장님(2026-09-20): *"이모지 빼"*
 *   서버가 예전엔 요약 앞에 이모지를 붙여 보냈고(📋 문의 접수 대기 중…),
 *   앱은 그 앞에 **✨**(AI 가 쓴 글)를 붙여서 **이모지가 둘 겹쳤다.**
 *   서버는 고쳤지만 **이미 저장된 옛 요약**이 남아 있어 보여줄 때 한 번 더 걸러낸다.
 *
 *   ⚠️ **맨 앞만 뗀다** — 문장 한가운데 글자는 건드리지 않는다.
 *      요약은 사장님이 통화 내용을 떠올리는 글이라 **말이 깎이면 안 된다.**
 */
class SummaryTextTest {

    @Test
    fun `맨 앞 이모지를 뗀다`() {
        assertEquals("문의 접수 대기 중", SummaryText.stripLeadingEmoji("📋 문의 접수 대기 중"))
    }

    @Test
    fun `이모지가 여러 개 붙어 있어도 다 뗀다`() {
        assertEquals("예약 확정", SummaryText.stripLeadingEmoji("✨ 📋 예약 확정"))
    }

    @Test
    fun `문장 한가운데 이모지는 그대로 둔다 — 말이 깎이면 안 된다`() {
        assertEquals(
            "욕조 줄눈 👷 7월 13일",
            SummaryText.stripLeadingEmoji("욕조 줄눈 👷 7월 13일")
        )
    }

    @Test
    fun `이모지가 없으면 그대로`() {
        assertEquals("잔금 입금 완료", SummaryText.stripLeadingEmoji("잔금 입금 완료"))
    }

    @Test
    fun `앞의 빈칸도 같이 뗀다`() {
        assertEquals("문의", SummaryText.stripLeadingEmoji("   문의"))
    }

    @Test
    fun `전부 이모지면 원본을 그대로 둔다 — 지우면 빈 줄이 된다`() {
        assertEquals("📋", SummaryText.stripLeadingEmoji("📋"))
        assertEquals("✨ 📋", SummaryText.stripLeadingEmoji("✨ 📋"))
    }

    @Test
    fun `없는 값은 없는 값으로`() {
        assertNull(SummaryText.stripLeadingEmoji(null))
    }

    @Test
    fun `빈 글자는 빈 글자로`() {
        assertEquals("", SummaryText.stripLeadingEmoji(""))
    }

    @Test
    fun `화살표나 기호도 앞에 있으면 뗀다`() {
        assertEquals("다음 단계", SummaryText.stripLeadingEmoji("→ 다음 단계"))
        assertEquals("확인함", SummaryText.stripLeadingEmoji("✓ 확인함"))
    }

    @Test
    fun `숫자나 한글로 시작하면 한 글자도 안 깎는다`() {
        assertEquals("9/19 시공 확정", SummaryText.stripLeadingEmoji("9/19 시공 확정"))
        assertEquals("가격 문의", SummaryText.stripLeadingEmoji("가격 문의"))
    }
}
