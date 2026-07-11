package com.detailline.callfollowcrm.domain.inbox

/**
 * "이 발신번호는 개인(고객)이 아니다"를 오탐 0 을 목표로 아주 보수적으로 판정. (2026-07-11 사장님)
 *   AdHeuristics(isLikelyAd) 와 같은 철학: 조금이라도 개인 고객일 여지가 있으면 false(→ 상담함에 남김).
 *   여기서 true 인 것만 "확실한 비고객" 으로 문자함 직행.
 *
 * ⚠️ 본문이 아니라 '발신번호 형태'만 본다. 은행/인증/택배 '내용' 판정은 위험(고객이 포워딩 가능)하므로
 *    발신번호가 개인이 아닐 때만 의미가 있고, 그 경우 이 함수가 이미 true 라 별도 내용 판정이 불필요하다.
 */
object NonCustomerHeuristics {

    /** +82 → 0 정규화 후 숫자만. */
    private fun normalize(address: String): String {
        val raw = address.filter { it.isDigit() }
        return if (raw.startsWith("82")) "0" + raw.removePrefix("82") else raw
    }

    /**
     * 개인 휴대폰(한국)인가 — 010(및 구형 011~019) + 길이 10~11.
     *   개인폰이면 어떤 서비스알림 패턴도 적용 안 함(고객이 보냈을 수 있으니).
     */
    fun isPersonalMobile(address: String): Boolean {
        val d = normalize(address)
        return d.length in 10..11 && (d.startsWith("010") ||
            Regex("^01[16789]").containsMatchIn(d))
    }

    /**
     * 확실한 비개인 발신 = 문자함 직행 신호. 오탐 0 목표로 아래만 true:
     *   - 영숫자 발신자(발신처 이름에 알파벳 포함) / 이메일 게이트웨이 — 개인 휴대폰은 알파벳 발신 불가.
     *   - 대표번호 15xx/16xx/18xx.
     *   - 짧은 코드(숫자 4~8자리, 0으로 시작 안 함) — 1004/114/15881588 등.
     * 지역번호(02/031… = 0으로 시작하는 유선)는 가게 사장 고객 가능성 → false(애매로 남김).
     */
    fun isNonPersonalSender(address: String): Boolean {
        if (isPersonalMobile(address)) return false
        // 알파벳이 섞인 발신자(예: "NAVER", "Web발신처", 이메일) → 개인 아님.
        if (Regex("[A-Za-z]").containsMatchIn(address)) return true
        val d = normalize(address)
        if (d.isEmpty()) return false                       // 발신번호 불명 → 애매(상담함)
        // 대표번호 15/16/18 xxxx.
        if (Regex("^1[568]\\d{2}").containsMatchIn(d)) return true
        // 짧은 코드: 3~8자리이고 0으로 시작 안 함(114/1004/대표번호 등). 개인폰은 10~11자리라 배제됨.
        if (d.length in 3..8 && !d.startsWith("0")) return true
        return false
    }
}
