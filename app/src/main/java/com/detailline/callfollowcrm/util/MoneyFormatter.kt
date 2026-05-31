package com.detailline.callfollowcrm.util

/**
 * 금액 표시 — 사장님이 한눈에 읽도록 천 단위 콤마 + "원".
 *   1200000 → "1,200,000원"
 */
object MoneyFormatter {
    fun won(amount: Long): String = "%,d원".format(amount)
}
