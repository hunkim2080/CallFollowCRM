package com.detailline.callfollowcrm.domain.schedule

import com.detailline.callfollowcrm.util.MoneyFormatter

/**
 * 💰 일정 달력 칸 「지역 [금액]」 — **금액 토글**을 켰을 때 띠에 붙는 글자의 **셈 한 곳**. (2026-10-19 사장님 프로토)
 *
 *   - 토글(`AppPreferences.scheduleShowMoney`)은 **기본 꺼둠** — 남이 폰을 볼 때 금액이 안 보이게.
 *   - 켜면 현장 줄마다 「동대문 [65]」 — 금액은 **그 건 총액**(jobs SoT, `JobCardRow.rowOf` 가 채운
 *     `totalAmount`)을 **만원 단위 숫자만**. 한 날 여러 현장이면 **줄마다 각각**(합치지 않는다).
 *   - 금액을 아직 안 적은 현장은 「동대문 [–]」 — 0 이 아니라 **「아직 안 적음」**. 날 카드의 딱지와 같은 규칙
 *     (`totalAmount > 0` 일 때만 돈으로 본다 — ScheduleScreen 의 moneyTag).
 *   - 만 단위 변환·콤마는 [MoneyFormatter] 한 곳(내림). 여기서 `/ 10000` 을 다시 적지 않는다.
 *
 *   그리는 쪽(ScheduleScreen `CalRegionBar`)은 [moneyOf] 가 준 글자를 괄호로 감싸 색만 입힌다 —
 *   금액 = primary 계열, 괄호·「–」 = 연한 회색(textHint).
 */
object CalendarMoneyLabel {
    /** 금액 없음 표시 — 「[–]」. 0 과 구분되는 en-dash. */
    const val NONE = "–"

    /** 만원 단위 숫자만 — 650,000 → 「65」 · 12,500,000 → 「1,250」. 없거나 0 이하 → null. */
    fun amountText(totalWon: Long?): String? =
        totalWon?.takeIf { it > 0L }?.let { MoneyFormatter.grouped(MoneyFormatter.manOf(it)) }

    /**
     * 띠에 붙일 금액 글자. 토글 끔 → null(= 지금과 완전히 같다). 켬 → 「65」 또는 「–」.
     *   여러 날 시공은 첫날 띠에만 글자가 오므로, 부르는 쪽이 label 과 같은 날에만 넘긴다.
     */
    fun moneyOf(totalWon: Long?, show: Boolean): String? =
        if (!show) null else amountText(totalWon) ?: NONE

    /**
     * 한 줄로 합친 글자 — 「동대문 [65]」 · 「동대문 [–]」 · 끔이면 「동대문」 · 지역을 못 뽑았으면 「[65]」.
     *   단위 테스트·접근성(contentDescription)용. 화면은 색을 따로 입히려고 조각을 직접 그린다.
     */
    fun plain(region: String?, money: String?): String? {
        val r = region?.takeIf { it.isNotBlank() }
        if (money == null) return r
        return if (r == null) "[$money]" else "$r [$money]"
    }
}
