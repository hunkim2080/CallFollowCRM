package com.detailline.callfollowcrm.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 달력 「지역 [금액]」 셈 — 토글·만원 변환·없음 표시. (2026-10-19 사장님 프로토)
 */
class CalendarMoneyLabelTest {

    @Test fun `총액 65만원이면 65`() {
        assertEquals("65", CalendarMoneyLabel.amountText(650_000L))
    }

    @Test fun `천 단위 콤마 - 1250만원이면 1,250`() {
        assertEquals("1,250", CalendarMoneyLabel.amountText(12_500_000L))
    }

    @Test fun `만원 미만은 내림 - 37만5천은 37`() {
        // MoneyFormatter 규칙(내림)을 그대로 따른다 — 돈은 적게 말하는 쪽이 안전.
        assertEquals("37", CalendarMoneyLabel.amountText(375_000L))
    }

    @Test fun `안 적음이나 0 이면 금액 없음`() {
        assertNull(CalendarMoneyLabel.amountText(null))
        assertNull(CalendarMoneyLabel.amountText(0L))
        assertNull(CalendarMoneyLabel.amountText(-1L))
    }

    @Test fun `토글 끔이면 null - 지금과 똑같이`() {
        assertNull(CalendarMoneyLabel.moneyOf(650_000L, show = false))
        assertNull(CalendarMoneyLabel.moneyOf(null, show = false))
    }

    @Test fun `토글 켬 - 있으면 숫자, 없으면 대시`() {
        assertEquals("65", CalendarMoneyLabel.moneyOf(650_000L, show = true))
        assertEquals(CalendarMoneyLabel.NONE, CalendarMoneyLabel.moneyOf(null, show = true))
        assertEquals("–", CalendarMoneyLabel.moneyOf(0L, show = true))
    }

    @Test fun `한 줄 글자 - 지역 대괄호 금액`() {
        assertEquals("동대문 [65]", CalendarMoneyLabel.plain("동대문", "65"))
        assertEquals("동대문 [–]", CalendarMoneyLabel.plain("동대문", "–"))
        assertEquals("동대문", CalendarMoneyLabel.plain("동대문", null))
        assertEquals("[65]", CalendarMoneyLabel.plain(null, "65"))
        assertNull(CalendarMoneyLabel.plain(null, null))
    }
}
