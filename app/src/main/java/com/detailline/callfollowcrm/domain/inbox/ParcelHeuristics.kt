package com.detailline.callfollowcrm.domain.inbox

/**
 * 📦 **택배 문자인가** — 문자함을 `택배` 와 `광고·인증` 으로 가른다. (2026-09-20 사장님)
 *
 * 사장님: *"택배는 무조건 자동 sms 시스템이라 문자로 오고, 우리가 그걸 알아서 자동분류 해주고
 *   광고 또는 인증문자는 한 칩에 넣어주면 될듯."*
 *
 * ## 왜 발신번호만으론 안 되나
 * 사장님이 말한 **8자리 대표번호** 규칙은 *"손님이냐 아니냐"* 를 가르는 덴 정확하고,
 * 그건 [NonCustomerHeuristics] 가 이미 하고 있다. 그런데 **택배사도 8자리**를 쓴다
 * (CJ 1588-1255 · 롯데 1588-2121 · 한진 1588-0011 …). 광고·인증과 **번호 모양이 같다.**
 * → 둘을 가르려면 **글자**를 봐야 한다.
 *
 * ## 글자를 봐도 되는 이유
 * [NonCustomerHeuristics] 주석에 *"택배 '내용' 판정은 위험(고객이 포워딩 가능)"* 이라고 적혀 있다.
 * 손님이 택배 문자를 사장님께 전달하면 그게 택배로 잡히기 때문이다.
 * **그런데 이 함수는 이미 '손님 아님' 으로 걸러진 뒤에만 부른다.** 그 구역엔 포워딩이 없다.
 *
 * ## 판정
 * 강한 낱말이 하나라도 있으면 택배. "배송" 같은 약한 말은 안 쓴다 — 광고에도 흔하다("무료배송").
 */
object ParcelHeuristics {

    /**
     * 택배사 대표번호(숫자만). **보조 단서**다 — 목록이 틀리거나 바뀔 수 있으니
     * 이것만으로 판정하지 않고 낱말과 함께 쓴다.
     */
    private val CARRIER_NUMBERS = setOf(
        "15881255",   // CJ대한통운
        "15882121",   // 롯데택배
        "15880011",   // 한진택배
        "15881300",   // 우체국택배
        "15889988",   // 로젠택배
        "15777011"    // 쿠팡
    )

    /**
     * 이 말이 하나라도 있으면 택배. **광고 문자엔 잘 안 나오는 말**만 골랐다.
     *   ("배송"·"수령"·"발송" 은 광고에도 흔해서 뺐다 — "무료배송", "사은품 발송")
     */
    private val STRONG = listOf(
        "운송장", "송장", "집화", "택배",
        "배송완료", "배송출발", "배송중", "배송예정", "도착예정", "배달완료", "배달예정",
        "상품이 출고", "출고되었습니다", "수령하실", "문앞", "무인함", "안전장소"
    )

    private fun digits(address: String): String {
        val raw = address.filter { it.isDigit() }
        return if (raw.startsWith("82")) "0" + raw.removePrefix("82") else raw
    }

    /**
     * @param address 발신번호
     * @param body 문자 본문
     * @return 택배 문자로 보이면 true. **[NonCustomerHeuristics] 로 '손님 아님' 이 확인된 것에만** 쓴다.
     */
    fun isParcel(address: String, body: String): Boolean {
        val d = digits(address)
        val fromCarrier = d in CARRIER_NUMBERS
        val hasWord = STRONG.any { body.contains(it) }
        // 택배사 번호면 낱말이 없어도 택배로 본다(발송 알림 형식이 제각각이라).
        // 그 외 번호는 낱말이 있어야 한다 — 8자리라는 것만으론 광고와 못 가른다.
        return fromCarrier || hasWord
    }
}
