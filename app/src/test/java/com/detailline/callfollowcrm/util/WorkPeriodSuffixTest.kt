package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * 여러 날 시공 기간 표기. (2026-09-15 사장님: "3일 중 1일차인데 시작 날짜만 보임")
 *   날짜 계산은 하루 경계·달 넘김에서 조용히 틀리기 쉬워 눈으로 못 잡는다 → 테스트로 고정.
 */
class WorkPeriodSuffixTest {

    private fun day(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply {
        clear(); set(y, m - 1, d, 0, 0, 0)
    }.timeInMillis

    @Test
    fun `하루짜리는 꼬리표가 없다`() {
        assertEquals("", DateTimeUtils.workPeriodSuffix(day(2026, 9, 15), 1))
    }

    @Test
    fun `0이나 음수로 들어와도 하루로 본다`() {
        assertEquals("", DateTimeUtils.workPeriodSuffix(day(2026, 9, 15), 0))
        assertEquals("", DateTimeUtils.workPeriodSuffix(day(2026, 9, 15), -3))
    }

    @Test
    fun `날짜가 없으면 빈 문자열`() {
        assertEquals("", DateTimeUtils.workPeriodSuffix(null, 5))
    }

    @Test
    fun `3일이면 시작일 포함해 이틀 뒤가 끝나는 날`() {
        // 9/15 시작 · 3일 = 15·16·17 → "~9/17" (시작일을 1일차로 센다)
        assertEquals(" · 3일 (~9/17)", DateTimeUtils.workPeriodSuffix(day(2026, 9, 15), 3))
    }

    @Test
    fun `달을 넘어가도 맞는다`() {
        // 9/30 시작 · 3일 = 9/30·10/1·10/2
        assertEquals(" · 3일 (~10/2)", DateTimeUtils.workPeriodSuffix(day(2026, 9, 30), 3))
    }

    @Test
    fun `해를 넘어가도 맞는다`() {
        // 12/31 시작 · 2일 = 12/31·1/1
        assertEquals(" · 2일 (~1/1)", DateTimeUtils.workPeriodSuffix(day(2026, 12, 31), 2))
    }

    @Test
    fun `시작 시각이 한낮이어도 날짜 기준으로 센다`() {
        val noon = day(2026, 9, 15) + 13 * 60 * 60 * 1000L
        assertEquals(" · 3일 (~9/17)", DateTimeUtils.workPeriodSuffix(noon, 3))
    }
}
