package com.detailline.callfollowcrm.domain.quote

/**
 * 🏷️ **견적 한 줄의 돈을 어떻게 읽을까 — 여기 한 곳.** (2026-10-02 사장님)
 *
 *   사장님: *"0만원으로 입력하면 그 항목이 「서비스」로 표기되면 좋겠어.
 *            그리고 「할인」이라는 품목에 금액을 입력하면 빨간 글씨로 표기되고
 *            그 금액이 마이너스 처리되면 좋겠다. 고객한테도 얼마 할인해주는지 딱 보이고 말야"*
 *
 * ## 규칙 — 딱 두 가지
 *   · **0원이면 「서비스」** — 「0원」은 서비스인지 값을 안 적은 건지 알 수가 없다.
 *   · **이름에 「할인」이 들어가면 빼기** — 빨간 글씨에 `−10만원`.
 *     새 설정칸을 만들지 않는다. 가격표에 「할인」 품목 하나 만들어 두고
 *     견적 만들 때 **그 자리에서 금액만** 바꿔 쓰면 된다(사장님이 쓰시던 그대로).
 *
 * ⚠️ **서버(접수서·견적서 HTML)도 같은 규칙을 쓴다.** 코드는 못 나누니 규칙을 양쪽에 적어둔다 —
 *    한쪽만 고치면 **앱에선 빠지는데 고객 문서엔 더해지는** 일이 난다.
 *
 * Android 의존성 없음 → 폰 없이도 매 빌드가 검사한다([QuoteMoneyTest]).
 */
object QuoteMoney {

    /** 깎아주는 줄인가. 이름으로만 가른다. */
    fun isDiscount(title: String): Boolean = title.contains("할인")

    /** 공짜로 해주는 줄인가. 할인은 0원이어도 서비스가 아니다. */
    fun isService(title: String, amount: Long): Boolean = !isDiscount(title) && amount == 0L

    /**
     * 합계에 들어갈 값. 할인은 **음수**로 들어간다.
     *   ⚠️ 할인 줄의 금액은 **양수로 적는다**(사장님은 「10만원 할인」이라고 적지 「-10」이라 안 적는다).
     */
    fun signed(title: String, amount: Long): Long =
        if (isDiscount(title)) -kotlin.math.abs(amount) else amount

    /**
     * 그 줄 금액을 사람 말로. 「서비스」 / 「−10만원」 / 「200만원」.
     * @param won 원 단위. 할인도 **양수**로 넘긴다.
     */
    fun label(title: String, won: Long): String = when {
        isService(title, won) -> "서비스"
        isDiscount(title) -> "−" + money(kotlin.math.abs(won))
        else -> money(won)
    }

    /**
     * 여러 줄의 합계. **0원 밑으로는 안 내려간다** — 마이너스 청구서는 없다.
     *   (할인 줄에는 사장님이 적은 금액이 그대로 보인다. 합계만 0에서 멈춘다.)
     */
    fun total(lines: List<Pair<String, Long>>): Long =
        lines.sumOf { (title, won) -> signed(title, won) }.coerceAtLeast(0L)

    /** 원 → "40만원" / "1,500,000원". 0 은 "0원"(합계 자리에 쓴다 — 거긴 서비스가 아니다). */
    fun money(won: Long): String {
        if (won == 0L) return "0원"
        return if (won >= 10_000L && won % 10_000L == 0L) "${won / 10_000L}만원"
        else java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(won) + "원"
    }
}
