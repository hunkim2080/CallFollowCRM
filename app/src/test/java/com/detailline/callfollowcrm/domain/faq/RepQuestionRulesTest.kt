package com.detailline.callfollowcrm.domain.faq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepQuestionRulesTest {

    @Test fun `질문 - 28자까지만 남는다`() {
        val raw = "가".repeat(35)
        assertEquals(28, RepQuestionRules.clampQuestion(raw).length)
    }

    @Test fun `질문 - 한도 안이면 그대로`() {
        assertEquals("주차 되나요?", RepQuestionRules.clampQuestion("주차 되나요?"))
    }

    @Test fun `질문 - 줄바꿈은 빈칸으로(한 줄)`() {
        assertEquals("주차 되나요?", RepQuestionRules.clampQuestion("주차\n되나요?"))
    }

    @Test fun `답변 - 500자까지만 남고 줄바꿈은 그대로`() {
        assertEquals(500, RepQuestionRules.clampAnswer("나".repeat(600)).length)
        assertEquals("네\n가능합니다", RepQuestionRules.clampAnswer("네\n가능합니다"))
    }

    @Test fun `자르는 자리에 이모지가 걸리면 반쪽을 남기지 않는다`() {
        // 27자 + 이모지(2칸) = 29칸 → 28칸에서 자르면 반쪽이 남는다. 통째로 뺀다.
        val raw = "가".repeat(27) + "😀"
        val cut = RepQuestionRules.clampQuestion(raw)
        assertEquals("가".repeat(27), cut)
        assertFalse(Character.isHighSurrogate(cut.last()))
    }

    @Test fun `이모지가 한도 안에 다 들어가면 그대로`() {
        val raw = "가".repeat(18) + "😀"
        assertEquals(raw, RepQuestionRules.clampQuestion(raw))
    }

    @Test fun `카운터 - 쓴 글자 수와 한도`() {
        assertEquals("0/28", RepQuestionRules.counter("", RepQuestionRules.QUESTION_MAX))
        assertEquals("6/500", RepQuestionRules.counter("네 됩니다.", RepQuestionRules.ANSWER_MAX))
    }

    @Test fun `등록 - 질문과 답 둘 다 있어야 한다`() {
        assertTrue(RepQuestionRules.canSave("주차 되나요?", "네"))
        assertFalse(RepQuestionRules.canSave("", "네"))
        assertFalse(RepQuestionRules.canSave("주차 되나요?", ""))
    }

    @Test fun `등록 - 빈칸만 있는 건 빈 것`() {
        assertFalse(RepQuestionRules.canSave("   ", "네"))
        assertFalse(RepQuestionRules.canSave("주차 되나요?", " \n "))
    }

    @Test fun `미리보기 한 줄 - 줄바꿈은 빈칸으로`() {
        assertEquals("네, 가능합니다. 좁으면 안내드려요.", RepQuestionRules.previewLine("네, 가능합니다.\n좁으면 안내드려요.", hasImage = false))
    }

    @Test fun `미리보기 한 줄 - 사진이 있으면 뒤에 (사진)`() {
        assertEquals("욕실 기준 평균 OO만원대입니다 (사진)", RepQuestionRules.previewLine("욕실 기준 평균 OO만원대입니다", hasImage = true))
    }

    @Test fun `분류 - 10자까지 남고 앞뒤 빈칸 정리`() {
        assertEquals(10, RepQuestionRules.clampCategory("가".repeat(15)).length)
        assertEquals("가격", RepQuestionRules.clampCategory("  가격  "))
    }

    @Test fun `분류 - 줄바꿈은 빈칸으로(한 줄)`() {
        assertEquals("예약 결제", RepQuestionRules.clampCategory("예약\n결제"))
    }

    @Test fun `분류 - 기본 분류 네 개`() {
        assertEquals(listOf("가격", "시공시간", "A/S", "관리법"), RepQuestionRules.DEFAULT_CATEGORIES)
    }
}
