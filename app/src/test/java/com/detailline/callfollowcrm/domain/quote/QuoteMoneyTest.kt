package com.detailline.callfollowcrm.domain.quote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🏷️ **「서비스」와 「할인」** — 돈이 걸린 자리라 숫자로 박아 둔다. (2026-10-02 사장님)
 *
 * 막는 것 —
 *   ① 할인이 **더해져서** 깎아주려다 올리는 것 (지금까지 그랬다)
 *   ② 0원이 「0원」으로 떠서 **서비스인지 안 적은 건지** 모르는 것
 *   ③ 할인이 커서 **합계가 마이너스**가 되는 것
 */
class QuoteMoneyTest {

    @Test
    fun `사장님이 본 그 화면 — 할인은 더하는 게 아니라 뺀다`() {
        val lines = listOf(
            "화장실 벽+바닥 전체 줄눈 시공(2곳)" to 2_000_000L,
            "현관(폴리)" to 10_000L,
            "할인" to 100_000L
        )
        // 전엔 211만원이 됐다. 깎아주려다 올린 것이다.
        assertEquals(1_910_000L, QuoteMoney.total(lines))
    }

    @Test
    fun `0원은 서비스 — 할인은 0원이어도 서비스가 아니다`() {
        assertEquals("서비스", QuoteMoney.label("베란다(폴리)", 0L))
        assertTrue(QuoteMoney.isService("베란다(폴리)", 0L))
        assertFalse("할인 0원은 서비스가 아니다", QuoteMoney.isService("할인", 0L))
    }

    @Test
    fun `할인 줄은 빼기로 보인다`() {
        assertEquals("−10만원", QuoteMoney.label("할인", 100_000L))
        assertEquals("−10만원", QuoteMoney.label("단골 할인", 100_000L))
    }

    @Test
    fun `양수로 적어도 빼진다 — 사장님은 마이너스를 안 적는다`() {
        assertEquals(-100_000L, QuoteMoney.signed("할인", 100_000L))
        assertEquals(-100_000L, QuoteMoney.signed("할인", -100_000L))
    }

    @Test
    fun `할인이 더 커도 합계는 0원에서 멈춘다`() {
        val lines = listOf("현관(폴리)" to 10_000L, "할인" to 500_000L)
        assertEquals(0L, QuoteMoney.total(lines))
        // 줄에는 적은 금액 그대로 보인다.
        assertEquals("−50만원", QuoteMoney.label("할인", 500_000L))
    }

    @Test
    fun `서비스는 합계를 안 건드린다`() {
        val a = listOf("화장실" to 500_000L)
        val b = listOf("화장실" to 500_000L, "베란다(폴리)" to 0L)
        assertEquals(QuoteMoney.total(a), QuoteMoney.total(b))
    }

    @Test
    fun `돈 적는 법은 그대로`() {
        assertEquals("40만원", QuoteMoney.money(400_000L))
        assertEquals("150만원", QuoteMoney.money(1_500_000L))
        // 만원으로 안 떨어지면 원 단위로 또박또박.
        assertEquals("1,505,000원", QuoteMoney.money(1_505_000L))
        assertEquals("0원", QuoteMoney.money(0L))
    }

    @Test
    fun `보통 품목은 아무것도 안 바뀐다`() {
        assertEquals("200만원", QuoteMoney.label("화장실 벽+바닥 전체 줄눈 시공(2곳)", 2_000_000L))
        assertFalse(QuoteMoney.isDiscount("화장실 바닥 줄눈 1곳"))
    }
}
