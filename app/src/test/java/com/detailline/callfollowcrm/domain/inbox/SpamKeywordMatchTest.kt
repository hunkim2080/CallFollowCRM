package com.detailline.callfollowcrm.domain.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🔤 「이 단어가 있으면」 매칭. (2026-10-16 사장님)
 * 제일 중요한 건 **저장 고객·답장한 번호는 그 낱말을 써도 안 가리는 것** — 단골을 문자함으로 보내면 치명적.
 */
class SpamKeywordMatchTest {

    private val kws = setOf("손실 지급", "토토", "꽁머니", "무료수신거부")

    // ── 일치 ──
    @Test fun `낱말이 본문에 들어 있으면 걸린다(부분일치)`() {
        assertTrue(SpamKeywordMatch.matches("[광고] 오늘만 꽁머니 3만원 지급!", kws))
        assertEquals("꽁머니", SpamKeywordMatch.firstMatch("[광고] 오늘만 꽁머니 3만원 지급!", kws))
    }

    @Test fun `띄어쓰기가 달라도 같은 말로 본다`() {
        assertTrue(SpamKeywordMatch.matches("실시간 손실지급 보장", kws))          // 「손실 지급」 ↔ 「손실지급」
        assertTrue(SpamKeywordMatch.matches("무료 수신 거부 080-000-0000", kws)) // 「무료수신거부」 ↔ 「무료 수신 거부」
    }

    @Test fun `영문은 대소문자 안 가린다`() {
        val k = setOf("Free Bonus")
        assertTrue(SpamKeywordMatch.matches("GET YOUR FREE BONUS NOW", k))
        assertTrue(SpamKeywordMatch.matches("free bonus 드려요", k))
    }

    // ── 불일치 ──
    @Test fun `낱말이 없으면 안 걸린다`() {
        assertFalse(SpamKeywordMatch.matches("안녕하세요 줄눈 견적 문의드려요", kws))
        assertNull(SpamKeywordMatch.firstMatch("안녕하세요 줄눈 견적 문의드려요", kws))
    }

    @Test fun `낱말 일부만 있으면 안 걸린다`() {
        assertFalse(SpamKeywordMatch.matches("손실이 있었어요", kws))   // 「손실 지급」 전체가 있어야
    }

    // ── 빈 값·공백 ──
    @Test fun `빈 키워드와 공백만인 키워드는 없는 셈`() {
        assertFalse(SpamKeywordMatch.matches("아무 문자", emptySet()))
        assertFalse(SpamKeywordMatch.matches("아무 문자", setOf("", "   ")))
        assertFalse(SpamKeywordMatch.matches("", kws))
        assertFalse(SpamKeywordMatch.matches("   ", kws))
    }

    @Test fun `키워드 앞뒤 공백은 떼고 본다`() {
        assertTrue(SpamKeywordMatch.matches("토토 사이트 오픈", setOf("  토토  ")))
        assertEquals("토토", SpamKeywordMatch.normalize("  토토  "))
        assertNull(SpamKeywordMatch.normalize("   "))
        assertNull(SpamKeywordMatch.normalize(null))
    }

    // ── 🔴 핵심: 모르는 번호만 ──
    @Test fun `저장 고객은 그 낱말을 써도 안 걸린다`() {
        val body = "사장님 토토 얘기 아니고요, 지난번 줄눈 손실 지급 건 문의요"
        assertNull(SpamKeywordMatch.hit(body, kws, isSavedCustomer = true, hasOwnerReply = false))
    }

    @Test fun `내가 답장한 번호는 그 낱말을 써도 안 걸린다`() {
        val body = "꽁머니 얘기요"
        assertNull(SpamKeywordMatch.hit(body, kws, isSavedCustomer = false, hasOwnerReply = true))
    }

    @Test fun `모르는 번호면 걸린다`() {
        assertEquals("토토", SpamKeywordMatch.hit("토토 사이트 오픈", kws, isSavedCustomer = false, hasOwnerReply = false))
    }
}
