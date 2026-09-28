package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 💰 **만원으로 줄여 적는 법은 하나다.** (2026-09-28 사장님 결정: 내림)
 *
 * 전엔 같은 물음에 코드가 **여섯 군데서 각자** 답했다. 대부분 `won / 10_000` 을 그냥 써서
 * 조용히 내림했는데, 그게 의도였는지 실수였는지 어디에도 안 적혀 있었다.
 * 누가 「반올림이 맞지 않나」 하고 한 곳만 고치면 화면마다 금액이 갈린다.
 *
 * 이 테스트가 그 결정을 붙잡아 둔다.
 */
class MoneyManwonTest {

    // ── 좁은 자리: 내림 ─────────────────────────────────────────────
    @Test fun `딱 떨어지면 그대로`() {
        assertEquals("95만원", MoneyFormatter.manwonShort(950_000L))
    }

    @Test fun `안 떨어지면 내린다 - 37만5천은 37만`() {
        // 사장님 결정. 38만(올림)도 37.5만(반올림 표기)도 아니다.
        assertEquals("37만원", MoneyFormatter.manwonShort(375_000L))
    }

    @Test fun `거의 다 찼어도 내린다`() {
        assertEquals("37만원", MoneyFormatter.manwonShort(379_999L))
    }

    @Test fun `만원이 안 되면 0만원`() {
        // 좁은 자리에서만 쓰는 함수라 이렇게 나온다. 손님에게 나가는 글엔 manwonOrWon 을 쓸 것.
        assertEquals("0만원", MoneyFormatter.manwonShort(9_000L))
    }

    @Test fun `자리가 더 좁으면 만 만 붙인다`() {
        assertEquals("120만", MoneyFormatter.manwonShort(1_200_000L, "만"))
        assertEquals("37", MoneyFormatter.manwonShort(375_000L, ""))
    }

    // ── 손님이 읽는 글: 한 푼도 안 깎는다 ──────────────────────────
    @Test fun `딱 떨어지면 만원으로`() {
        assertEquals("95만원", MoneyFormatter.manwonOrWon(950_000L))
    }

    @Test fun `안 떨어지면 원 그대로 - 깎지 않는다`() {
        // 🔴 여기서 내리면 줄여 적는 게 아니라 **금액을 틀리게 적는 것**이다.
        assertEquals("375,000원", MoneyFormatter.manwonOrWon(375_000L))
    }

    @Test fun `만원 미만은 원으로`() {
        // 「0만원」은 말이 안 된다.
        assertEquals("9,000원", MoneyFormatter.manwonOrWon(9_000L))
    }

    @Test fun `백만 단위도 콤마를 찍는다`() {
        assertEquals("1,200만원", MoneyFormatter.manwonOrWon(12_000_000L))
    }

    @Test fun `0원`() {
        assertEquals("0원", MoneyFormatter.manwonOrWon(0L))
    }
}
