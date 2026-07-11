package com.detailline.callfollowcrm.domain.inbox

import com.detailline.callfollowcrm.util.isLikelyAd

/**
 * 수신 문자 1차(로컬) 분류 — 상담함 / 문자함 / 애매. (2026-07-11 사장님)
 *   원칙(=AdHeuristics 와 동일): 진짜 고객을 문자함으로 보내면 치명적 → 애매하면 무조건 상담함(CONSULT).
 *   확실한 비고객만 GENERAL. 나머지는 UNSURE 로 상담함에 두고, Phase 2 에서 Haiku 가 정정.
 *
 *   순수 함수(부작용 없음) → 단위테스트로 적대 케이스 검증. Haiku 없이도 이걸로 체감 대부분 커버.
 */
object InboxClassifier {

    enum class Verdict { CONSULT, GENERAL, UNSURE }

    /**
     * @param body            문자 본문
     * @param address         발신번호(원본 — 하이픈/+82 섞여도 됨)
     * @param isSavedCustomer 저장된 고객인가(가장 강한 고객 신호)
     * @param hasOwnerReply   사장님이 이 번호에 답장한 적 있는가(=내가 상대한 번호)
     */
    fun classify(
        body: String,
        address: String,
        isSavedCustomer: Boolean,
        hasOwnerReply: Boolean
    ): Verdict {
        // ① 고객 확실 → 상담함. (강등 절대 금지 신호 — 저장고객/답장이력)
        if (isSavedCustomer || hasOwnerReply) return Verdict.CONSULT

        // ② 비고객 확실 → 문자함. 오탐 0 신호만.
        if (NonCustomerHeuristics.isNonPersonalSender(address)) return Verdict.GENERAL
        if (isLikelyAd(body, address)) return Verdict.GENERAL

        // ③ 애매 → 상담함 유지 + Haiku 정정 대상(Phase 2).
        return Verdict.UNSURE
    }

    /** GENERAL 판정 근거 라벨(복구 UI·디버그용). */
    fun generalReason(body: String, address: String): String = when {
        NonCustomerHeuristics.isNonPersonalSender(address) -> {
            val d = address.filter { it.isDigit() }.take(4)
            if (d.isNotBlank()) "대표·서비스번호($d)" else "비개인 발신"
        }
        isLikelyAd(body, address) -> "광고"
        else -> "고객 아님"
    }
}
