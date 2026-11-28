package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.presentation.component.isRealCallTodo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 통화요약 '꼭 챙겨야 할 일'에 LLM 이 스키마 필드명/영어 조각을 토해내던 버그 가드.
 * 잣대: **진짜 할 일은 늘 한글이 들어간다.** (2026-11-28 사장님 — `suggested_followup_sms formulation notice…` 가 떴다)
 */
class CallTodoFilterTest {

    @Test fun realKoreanTodos_pass() {
        assertTrue(isRealCallTodo("오후에 세입자와 통화하기"))
        assertTrue(isRealCallTodo("목요일까지 시공 일정 잡기"))
        assertTrue(isRealCallTodo("일정 확정 후 고객에게 문자 보내기"))
        assertTrue(isRealCallTodo("15만원 견적 문자 보내기"))   // 숫자·단위 섞여도 한글 있으면 OK
        assertTrue(isRealCallTodo("A/S 방문 안내"))             // 영문 약어 섞여도 한글 있으면 OK
    }

    @Test fun leakedSchemaFieldNames_dropped() {
        assertFalse(isRealCallTodo("suggested_followup_sms"))                 // 순수 snake_case
        assertFalse(isRealCallTodo("tags"))
        assertFalse(isRealCallTodo("suggested_followup_sms formulation notice")) // 필드명+영어(공백) — 옛 필터가 놓치던 모양
        assertFalse(isRealCallTodo("one_line"))
    }

    @Test fun blankOrAsciiOnly_dropped() {
        assertFalse(isRealCallTodo(""))
        assertFalse(isRealCallTodo("   "))
        assertFalse(isRealCallTodo("send follow up SMS"))   // 한글 0 = 할 일 아님
    }
}
