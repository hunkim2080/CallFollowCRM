package com.detailline.callfollowcrm.domain.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🍞 확인 띠가 몇 초 떠 있나 — 사장님(2026-10-19) "4초나 떠서 다음 동작을 막는다".
 *   확인은 1.5초, 실패·되돌리기는 3초. 둘 다 옛 Material 기본(4초)보다 짧아야 한다.
 */
class SnackbarTimingTest {

    @Test
    fun `그냥 확인은 짧게 — 1500ms`() {
        assertEquals(1_500L, SnackbarTiming.snackbarDurationMs("상담함으로 옮겼어요"))
        assertEquals(1_500L, SnackbarTiming.snackbarDurationMs("요약을 새로 했어요"))
        assertEquals(1_500L, SnackbarTiming.snackbarDurationMs("맨 위에 고정했어요"))
    }

    @Test
    fun `실패 낱말이 하나라도 있으면 길게 — 3000ms`() {
        listOf(
            "사진을 못 올렸어요",
            "저장에 실패했어요",
            "지금은 안 돼요",
            "지금은 안돼요",
            "잠시 후 다시 시도해주세요",
            "오류가 났어요",
            "문자 권한이 없어요 — 채팅에서 보내주세요"
        ).forEach { msg ->
            assertTrue(msg, SnackbarTiming.isImportant(msg))
            assertEquals(msg, 3_000L, SnackbarTiming.snackbarDurationMs(msg))
        }
    }

    @Test
    fun `되돌리기가 붙으면 확인 말이라도 길게 — 손이 갈 시간`() {
        val msg = "스팸으로 등록했어요 — 앞으로 안 보여요"
        assertFalse(SnackbarTiming.isImportant(msg))
        assertEquals(3_000L, SnackbarTiming.snackbarDurationMs(msg, hasAction = true))
    }

    @Test
    fun `둘 다 옛 Material 기본 4초보다 짧다`() {
        assertTrue(SnackbarTiming.BRIEF_MS < 4_000L)
        assertTrue(SnackbarTiming.IMPORTANT_MS < 4_000L)
        assertTrue(SnackbarTiming.BRIEF_MS < SnackbarTiming.IMPORTANT_MS)
    }

    @Test
    fun `빈 말도 버틴다`() {
        assertEquals(1_500L, SnackbarTiming.snackbarDurationMs(""))
    }
}
