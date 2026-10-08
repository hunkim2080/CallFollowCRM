package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * DateTimeUtils 핵심 함수 검증.
 *
 * 주의: SimpleDateFormat 은 OS/Locale 영향 받으므로 epoch 계산 함수 위주로 테스트.
 *      (format* 함수는 표시 형식이라 회귀 위험 낮음)
 */
class DateTimeUtilsTest {

    // ---------- dDayLabel ----------

    @Test fun `오늘 = 오늘 라벨`() {
        val now = epochAt(2026, 5, 30, 12, 0)
        val target = epochAt(2026, 5, 30, 8, 0) // 같은 날 다른 시간
        assertEquals("오늘", DateTimeUtils.dDayLabel(target, now))
    }

    @Test fun `내일 = D-1`() {
        val now = epochAt(2026, 5, 30, 12, 0)
        val target = epochAt(2026, 5, 31, 0, 0)
        assertEquals("D-1", DateTimeUtils.dDayLabel(target, now))
    }

    @Test fun `3일 후 = D-3`() {
        val now = epochAt(2026, 5, 30, 12, 0)
        val target = epochAt(2026, 6, 2, 0, 0)
        assertEquals("D-3", DateTimeUtils.dDayLabel(target, now))
    }

    @Test fun `어제 = D+1`() {
        val now = epochAt(2026, 5, 30, 12, 0)
        val target = epochAt(2026, 5, 29, 0, 0)
        assertEquals("D+1", DateTimeUtils.dDayLabel(target, now))
    }

    @Test fun `1주일 전 = D+7`() {
        val now = epochAt(2026, 5, 30, 12, 0)
        val target = epochAt(2026, 5, 23, 0, 0)
        assertEquals("D+7", DateTimeUtils.dDayLabel(target, now))
    }

    // ---------- startOfDay ----------

    @Test fun `자정 정규화 — 오전 8시 = 그날 0시`() {
        val input = epochAt(2026, 5, 30, 8, 30)
        val expected = epochAt(2026, 5, 30, 0, 0)
        assertEquals(expected, DateTimeUtils.startOfDay(input))
    }

    @Test fun `자정 정규화 — 23시 59분도 같은 날 0시로`() {
        val input = epochAt(2026, 5, 30, 23, 59)
        val expected = epochAt(2026, 5, 30, 0, 0)
        assertEquals(expected, DateTimeUtils.startOfDay(input))
    }

    @Test fun `자정 정규화 — 이미 자정인 값은 그대로`() {
        val midnight = epochAt(2026, 5, 30, 0, 0)
        assertEquals(midnight, DateTimeUtils.startOfDay(midnight))
    }

    // ---------- todayBounds ----------

    @Test fun `오늘 범위 — 24시간`() {
        val now = epochAt(2026, 5, 30, 15, 0)
        val (start, end) = DateTimeUtils.todayBounds(now)
        assertEquals(epochAt(2026, 5, 30, 0, 0), start)
        // end = 자정 + 24시간 - 1ms
        assertEquals(start + 24L * 60 * 60 * 1000 - 1, end)
    }

    @Test fun `오늘 범위 — start 가 end 보다 작음`() {
        val now = System.currentTimeMillis()
        val (start, end) = DateTimeUtils.todayBounds(now)
        assertTrue(start < end)
    }

    // ---------- durationLabel ----------

    @Test fun `0초 = 0초`() {
        assertEquals("0초", DateTimeUtils.durationLabel(0))
    }

    @Test fun `음수 = 0초`() {
        assertEquals("0초", DateTimeUtils.durationLabel(-5))
    }

    @Test fun `45초`() {
        assertEquals("45초", DateTimeUtils.durationLabel(45))
    }

    @Test fun `1분 0초`() {
        assertEquals("1분 0초", DateTimeUtils.durationLabel(60))
    }

    @Test fun `2분 30초`() {
        assertEquals("2분 30초", DateTimeUtils.durationLabel(150))
    }

    @Test fun `10분 통화`() {
        assertEquals("10분 0초", DateTimeUtils.durationLabel(600))
    }

    // ---------- formatDayRange (간단 일정 기간 라벨) ----------

    @Test fun `하루면 범위 아님 (물결 없음)`() {
        val d = epochAt(2026, 10, 17, 9, 0)
        val s = DateTimeUtils.formatDayRange(d, 1)
        assertTrue("하루는 '~'가 없어야", !s.contains("~"))
    }

    @Test fun `여러 날이면 물결과 N일`() {
        val d = epochAt(2026, 10, 17, 9, 0)
        val s = DateTimeUtils.formatDayRange(d, 6) // 10/17 ~ 10/22
        assertTrue("범위는 '~' 포함: $s", s.contains("~"))
        assertTrue("'6일' 포함: $s", s.contains("6일"))
        assertTrue("시작 '17일' 포함: $s", s.contains("17일"))
        assertTrue("끝 '22일' 포함: $s", s.contains("22일"))
    }

    @Test fun `같은 달이면 끝날짜에 월 반복 안 함`() {
        val d = epochAt(2026, 10, 17, 9, 0)
        val s = DateTimeUtils.formatDayRange(d, 6) // 같은 10월
        // "10월 17일 ~ 22일 · 6일" — '월'은 시작에만 한 번
        assertEquals("월이 한 번만: $s", 1, Regex("월").findAll(s).count())
    }

    @Test fun `달을 넘기면 끝날짜에도 월 표기`() {
        val d = epochAt(2026, 10, 29, 9, 0)
        val s = DateTimeUtils.formatDayRange(d, 5) // 10/29 ~ 11/2
        assertTrue("달 넘김이면 '11월' 포함: $s", s.contains("11월"))
        assertTrue("'5일' 포함: $s", s.contains("5일"))
    }

    @Test fun `days 0이하는 하루로 취급`() {
        val d = epochAt(2026, 10, 17, 9, 0)
        assertTrue(!DateTimeUtils.formatDayRange(d, 0).contains("~"))
    }

    // ---------- 헬퍼 ----------

    /**
     * 특정 시각의 epoch ms 반환 (시스템 default timezone 기준).
     * @param month 1=1월
     */
    private fun epochAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
