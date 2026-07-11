package com.detailline.callfollowcrm.domain.inbox

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 상담함/문자함 1차 분류 — precision 최우선 검증. 진짜 고객이 GENERAL 로 새면 안 된다(치명적).
 */
class InboxClassifierTest {

    private fun v(body: String, addr: String, saved: Boolean = false, replied: Boolean = false) =
        InboxClassifier.classify(body, addr, saved, replied)

    // ── 확실한 비고객 → 문자함(GENERAL) ──
    @Test fun `대표번호 서비스알림은 문자함`() {
        // 실제 사장님 사례: [HANPASS] 거래 알림, 발신 1522-0767
        assertEquals(InboxClassifier.Verdict.GENERAL,
            v("[Web발신][HANPASS] Your transaction has been processed", "1522-0767"))
    }

    @Test fun `1588 대표번호는 문자함`() {
        assertEquals(InboxClassifier.Verdict.GENERAL, v("고객님 결제가 완료되었습니다", "15881588"))
    }

    @Test fun `영숫자 발신자는 문자함`() {
        assertEquals(InboxClassifier.Verdict.GENERAL, v("네이버 로그인 알림", "NAVER"))
    }

    @Test fun `짧은 코드(114)는 문자함`() {
        assertEquals(InboxClassifier.Verdict.GENERAL, v("안내 말씀", "114"))
    }

    @Test fun `뻔한 광고(수신거부)는 개인번호여도 문자함`() {
        // isLikelyAd 확정 신호(무료수신거부)면 010 이라도 광고.
        assertEquals(InboxClassifier.Verdict.GENERAL,
            v("★대박 할인★ 지금 신청하세요 무료수신거부 0808001234", "01099998888"))
    }

    // ── 고객일 여지 있으면 상담함(CONSULT/UNSURE) ──
    @Test fun `저장 고객은 무조건 상담함`() {
        assertEquals(InboxClassifier.Verdict.CONSULT,
            v("★★★ 이벤트 무료수신거부", "15881588", saved = true))
    }

    @Test fun `답장한 적 있는 번호는 무조건 상담함`() {
        assertEquals(InboxClassifier.Verdict.CONSULT, v("사장님 저 왔어요", "01012345678", replied = true))
    }

    @Test fun `개인 휴대폰의 평범한 문의는 애매(상담함 유지)`() {
        assertEquals(InboxClassifier.Verdict.UNSURE, v("줄눈 시공 문의드려요 얼마인가요?", "010-2222-3333"))
    }

    @Test fun `개인폰이 포워딩한 입금알림은 애매(상담함)`() {
        // 발신이 010 이면 서비스알림 내용이어도 고객 포워딩 가능 → 상담함.
        assertEquals(InboxClassifier.Verdict.UNSURE, v("[Web발신] 입금 50,000원 잔액 120,000원", "01055551234"))
    }

    @Test fun `지역번호(유선) 발신은 애매 - 가게 사장 고객 가능`() {
        assertEquals(InboxClassifier.Verdict.UNSURE, v("견적 문의합니다", "0312345678"))
    }

    @Test fun `국제발신 텍스트만은 애매 - 교포 고객 실존`() {
        assertEquals(InboxClassifier.Verdict.UNSURE, v("[국제발신] 안녕하세요 시공 문의요", "01077778888"))
    }

    @Test fun `본문 없는(사진) 낯선 개인번호는 애매 - 견적 사진 가능`() {
        assertEquals(InboxClassifier.Verdict.UNSURE, v("", "01033334444"))
    }
}
