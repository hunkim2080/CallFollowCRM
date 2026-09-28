package com.detailline.callfollowcrm.util

/**
 * 금액 표시 — 사장님이 한눈에 읽도록 천 단위 콤마 + "원".
 *   1200000 → "1,200,000원"
 */
object MoneyFormatter {
    fun won(amount: Long): String = "%,d원".format(amount)

    private const val MAN = 10_000L

    /**
     * 💰 **만 단위로 줄여 적기 — 내림.** (2026-09-28 사장님 결정)
     *   375,000원 → 「37만원」. 37.5 도 38 도 아니다.
     *
     *   왜 내림인가: **돈은 적게 말하는 쪽이 안전하다.**
     *   올리거나 반올림하면 손님에게 **더 받을 것처럼** 보이고, 사장님에겐 **더 번 것처럼** 보인다.
     *   덜 적어서 나는 사고는 「어? 좀 더 들어왔네」로 끝나지만, 더 적으면 말이 달라진다.
     *
     *   ⚠️ **좁은 자리에만 쓴다** — 딱지, 캘린더 제목, 브리핑 한 줄처럼 자리가 없는 곳.
     *   손님이 읽는 글(견적서·잔금 안내·문자)에는 [manwonOrWon] 을 쓴다.
     *   거기서 내림하면 그건 줄여 적는 게 아니라 **금액을 틀리게 적는 것**이다.
     *
     * @param suffix 뒤에 붙일 말. 자리가 더 좁으면 "만" 만 쓰기도 한다.
     */
    fun manwonShort(won: Long, suffix: String = "만원"): String = "${won / MAN}$suffix"

    /**
     * 💰 **정확히 적기.** 만 단위로 딱 떨어지면 「95만원」, 아니면 「1,234,500원」.
     *   손님이 읽는 글과 문서는 전부 이것. 한 푼도 안 깎는다.
     *   만원이 안 되는 금액(9,000원)도 그대로 원으로 적는다 — 「0만원」은 말이 안 된다.
     */
    fun manwonOrWon(won: Long): String =
        if (won >= MAN && won % MAN == 0L) "%,d만원".format(won / MAN) else won(won)

    /**
     * 입력칸용 천 단위 콤마 — 숫자만 추려 그룹핑. "원"/단위는 안 붙임.
     *   "2500000" → "2,500,000", "" → "". FormattedTextField 의 format 인자로 그대로 사용.
     */
    fun grouped(raw: String): String {
        val digits = raw.filter { it.isDigit() }.trimStart('0')
        if (digits.isEmpty()) return if (raw.any { it.isDigit() }) "0" else ""
        return "%,d".format(digits.toLong())
    }
}
