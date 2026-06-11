package com.detailline.callfollowcrm.util

/**
 * 금액 표시 — 사장님이 한눈에 읽도록 천 단위 콤마 + "원".
 *   1200000 → "1,200,000원"
 */
object MoneyFormatter {
    fun won(amount: Long): String = "%,d원".format(amount)

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
