package com.detailline.callfollowcrm.util

/**
 * 스팸/광고 번호 앞자리(접두) 판정 (2026-06-07 사장님 요청).
 *   070(인터넷전화)·0507(안심)·050·02/031 같은 지역번호 등, 사장님이 등록한 앞자리로 시작하는 번호는
 *   "스팸"으로 보고 자동답장·AI 추천 준비를 건너뛰고 목록에서도 제외한다.
 *   휴대폰(010)으로 오는 진짜 고객은 영향 없음.
 */
object SpamPrefix {

    /** 추천 앞자리 — 설정에서 칩으로 빠르게 추가. */
    val SUGGESTED = listOf("070", "0507", "050", "02", "031", "032", "033", "041", "051", "053", "062", "064")

    /** phone 이 등록된 스팸 앞자리 중 하나로 시작하면 true. */
    fun isSpam(phone: String, prefixes: Set<String>): Boolean {
        if (prefixes.isEmpty()) return false
        val d = normalize(phone)
        if (d.isBlank()) return false
        return prefixes.any { p ->
            val pp = p.filter { it.isDigit() }
            pp.isNotEmpty() && d.startsWith(pp)
        }
    }

    /** 숫자만 추출 + 국제표기(+82) → 0 으로 정규화. */
    private fun normalize(phone: String): String {
        var d = phone.filter { it.isDigit() }
        if (d.startsWith("82")) d = "0" + d.removePrefix("82")
        return d
    }
}
