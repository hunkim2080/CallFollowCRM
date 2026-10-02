package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 💰 **돈 적는 법** — 사장님이 매일 보는 숫자인데 **시험이 없었다**(2026-10-03 발견).
 *
 *   규칙이 둘이고, **섞으면 사고**다:
 *     · [MoneyFormatter.manwonShort]  — **내림.** 좁은 자리(딱지·캘린더·브리핑 한 줄)에만
 *     · [MoneyFormatter.manwonOrWon]  — **정확히.** 손님이 읽는 글(견적서·잔금 안내·문자)에
 *
 *   왜 내림인가(2026-09-28 사장님 결정): **돈은 적게 말하는 쪽이 안전하다.**
 *   올리면 손님에겐 **더 받을 것처럼**, 사장님에겐 **더 번 것처럼** 보인다.
 */
class MoneyFormatterTest {

    // ── 원 단위 그대로 ──────────────────────────────────────────────

    @Test
    fun `천 단위 콤마와 원`() {
        assertEquals("1,200,000원", MoneyFormatter.won(1_200_000))
        assertEquals("0원", MoneyFormatter.won(0))
        assertEquals("9,000원", MoneyFormatter.won(9_000))
    }

    // ── 줄여 적기(내림) — 좁은 자리용 ────────────────────────────────

    @Test
    fun `딱 떨어지면 그대로`() {
        assertEquals("95만원", MoneyFormatter.manwonShort(950_000))
    }

    @Test
    fun `내림이다 — 반올림도 올림도 아니다`() {
        // 🔴 375,000 은 37.5 지만 **37만원**이다. 38 로 올리면 더 번 것처럼 보인다
        assertEquals("37만원", MoneyFormatter.manwonShort(375_000))
        assertEquals("37만원", MoneyFormatter.manwonShort(379_999))
        assertEquals("38만원", MoneyFormatter.manwonShort(380_000))
    }

    @Test
    fun `만원이 안 되면 0만원이 된다 — 그래서 좁은 자리에만 쓴다`() {
        assertEquals("0만원", MoneyFormatter.manwonShort(9_999))
        assertEquals("0만원", MoneyFormatter.manwonShort(0))
    }

    @Test
    fun `자리가 더 좁으면 꼬리말을 바꿔 쓴다`() {
        assertEquals("120만", MoneyFormatter.manwonShort(1_200_000, "만"))
    }

    // ── 정확히 적기 — 손님이 읽는 글 ─────────────────────────────────

    @Test
    fun `만 단위로 딱 떨어지면 만원으로`() {
        assertEquals("95만원", MoneyFormatter.manwonOrWon(950_000))
        assertEquals("1,200만원", MoneyFormatter.manwonOrWon(12_000_000))
    }

    @Test
    fun `딱 안 떨어지면 한 푼도 안 깎는다`() {
        // 🔴 여기서 내림하면 줄여 적는 게 아니라 **금액을 틀리게 적는 것**이다
        assertEquals("1,234,500원", MoneyFormatter.manwonOrWon(1_234_500))
        assertEquals("375,000원", MoneyFormatter.manwonOrWon(375_000))
    }

    @Test
    fun `만원이 안 되는 금액은 원으로 — 0만원은 말이 안 된다`() {
        assertEquals("9,000원", MoneyFormatter.manwonOrWon(9_000))
        assertEquals("0원", MoneyFormatter.manwonOrWon(0))
    }

    @Test
    fun `두 규칙이 갈리는 자리 — 같은 금액이 다르게 적힌다`() {
        val 금액 = 375_000L
        assertEquals("37만원", MoneyFormatter.manwonShort(금액))      // 좁은 자리
        assertEquals("375,000원", MoneyFormatter.manwonOrWon(금액))   // 손님이 읽는 글
    }

    // ── 입력칸 콤마 ────────────────────────────────────────────────

    @Test
    fun `입력칸은 숫자만 추려 콤마를 넣는다`() {
        assertEquals("2,500,000", MoneyFormatter.grouped("2500000"))
        assertEquals("2,500,000", MoneyFormatter.grouped("2,500,000"))
        assertEquals("1,000", MoneyFormatter.grouped("1000원"))
    }

    @Test
    fun `앞의 0 은 떨군다 — 0120 을 치면 120`() {
        assertEquals("120", MoneyFormatter.grouped("0120"))
    }

    @Test
    fun `빈 칸은 빈 칸으로 둔다 — 0 을 멋대로 넣지 않는다`() {
        assertEquals("", MoneyFormatter.grouped(""))
        assertEquals("", MoneyFormatter.grouped("원"))
    }

    @Test
    fun `0 만 쳤으면 0 이다`() {
        assertEquals("0", MoneyFormatter.grouped("0"))
        assertEquals("0", MoneyFormatter.grouped("000"))
    }
}
