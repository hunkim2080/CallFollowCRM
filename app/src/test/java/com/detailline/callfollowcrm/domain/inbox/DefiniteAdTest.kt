package com.detailline.callfollowcrm.domain.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🔕 「광고·스팸은 알림 안 울리기」 판정. (2026-10-16 사장님 A안)
 * 제일 중요한 건 **인증번호(OTP)·은행·택배·손님 문자는 절대 안 끄는 것** — 인증문자를 끄면 로그인·결제를 못 한다.
 */
class DefiniteAdTest {

    private val prefixes = setOf("070", "02", "1588")   // 02·1588 도 넣어 둔다 — 앞자리만으로 인증문자를 끄면 안 되니까
    // 「특가세일」 은 AdHeuristics 가 모르는 낱말 — 낱말 신호만으로 걸리는 걸 보려고 일부러 고름.
    private val keywords = setOf("손실 지급", "특가세일")

    private fun ad(body: String, address: String = "010-1234-5678", saved: Boolean = false) =
        DefiniteAd.isDefiniteAd(body, address, saved, prefixes, keywords)

    // ── 🔴 안전선: 절대 안 끈다 ──
    @Test fun `인증번호 문자는 안 끈다`() {
        assertFalse(ad("인증번호 582910"))
        assertFalse(ad("[Web발신] 인증번호 [582910] 타인에게 알려주지 마세요", "15990000"))
        assertFalse(ad("[카카오] 인증 코드 123456", "1644-0000"))
        assertFalse(ad("Your OTP is 884422", "+8210000"))
    }

    @Test fun `인증번호는 앞자리가 스팸이어도 안 끈다`() {
        assertFalse(ad("인증번호 582910", "070-1234-5678"))        // 070 = 스팸 앞자리인데도
        assertFalse(ad("[Web발신] 승인번호 4455 입력하세요", "02-123-4567"))
    }

    @Test fun `인증번호는 사장님이 적은 낱말이 섞여도 안 끈다`() {
        assertFalse(ad("특가세일 인증번호 1234", "070-0000-0000"))
    }

    @Test fun `은행 입금 문자는 안 끈다`() {
        assertFalse(ad("[Web발신] KB 입금 500,000원"))
        assertFalse(ad("[Web발신] KB국민은행 입금 500,000원 잔액 1,230,000원", "15881234"))   // 1588 앞자리여도
        assertFalse(ad("입금완료 30만원 확인바랍니다", "02-000-0000"))
    }

    @Test fun `택배 문자는 안 끈다`() {
        assertFalse(ad("택배 도착"))
        assertFalse(ad("[Web발신] CJ대한통운 택배가 오늘 도착 예정입니다 조회 https://cj.kr/x", "1588-1255"))
        assertFalse(ad("배송 출발했어요 문 앞에 둘게요", "070-7777-7777"))
    }

    @Test fun `저장 고객은 무엇을 보내도 안 끈다`() {
        assertFalse(ad("[Web발신](광고) 수신거부 080-123-4567", "070-1111-2222", saved = true))
        assertFalse(ad("특가세일 얘기요", "070-1111-2222", saved = true))
        assertFalse(ad("안녕하세요", "070-1111-2222", saved = true))
    }

    @Test fun `보통 손님 문자는 안 끈다`() {
        assertFalse(ad("화장실 줄눈 시공 얼마예요?"))
        assertFalse(ad("광고 보고 연락드려요 줄눈 시공 견적 궁금해서요"))
        assertFalse(ad("대출 갚느라 이번달은 좀 빠듯한데 시공 할부 되나요?"))
    }

    @Test fun `빈 본문은 안 끈다(앞자리가 아니면)`() {
        assertFalse(ad("", "010-1234-5678"))
        assertFalse(ad("   ", "010-1234-5678"))
    }

    // ── 확실한 광고: 끈다 ──
    @Test fun `법정 수신거부 표기 광고는 끈다`() {
        assertTrue(ad("[Web발신](광고) 신규 카드 발급 시 연회비 캐시백! 수신거부 080-123-4567", "1588-1588"))
        assertEquals(DefiniteAd.REASON_BODY, DefiniteAd.reason("(광고) 오늘만 50% 할인 무료거부 0808001234", "15881588", false, emptySet(), emptySet()))
    }

    @Test fun `스팸 앞자리 모르는 번호는 끈다`() {
        assertTrue(ad("안녕하세요 좋은 상품 소개드려요", "070-1234-5678"))
        assertEquals(DefiniteAd.REASON_PREFIX, DefiniteAd.reason("", "070-1234-5678", false, prefixes, emptySet()))
    }

    @Test fun `사장님이 적은 낱말이 든 모르는 번호는 끈다`() {
        assertTrue(ad("실시간 손실지급 보장 최대 77만원", "010-9999-0000"))
        assertEquals(DefiniteAd.REASON_KEYWORD, DefiniteAd.reason("오늘만 특가 세일 3만원", "010-9999-0000", false, emptySet(), keywords))
    }

    @Test fun `도박 광고는 앞자리·낱말 없이도 끈다`() {
        assertTrue(ad("카지노 첫충 100% 바카라 토토", "010-5555-6666"))
    }

    @Test fun `신호가 하나도 없으면 null`() {
        assertNull(DefiniteAd.reason("언제 가능하세요?", "010-1111-2222", false, prefixes, keywords))
    }
}
