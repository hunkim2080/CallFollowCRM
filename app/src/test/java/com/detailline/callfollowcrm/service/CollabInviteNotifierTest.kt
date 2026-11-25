package com.detailline.callfollowcrm.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 🤝 협업 요청 알림 글자 조립(표준 알림) — 제목·본문이 프로토대로 나오나. (2026-11-25 사장님)
 *   순수 함수라 폰 없이 검증. 날짜 라벨의 요일(괄호)은 실제 달력 의존이라 "11/27" 포함으로만 본다.
 */
class CollabInviteNotifierTest {

    private fun ms(y: Int, mo: Int, d: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, mo - 1, d, 0, 0, 0) }.timeInMillis

    @Test
    fun `제목 = 날짜 · 지역 · 지원요청`() {
        val now = ms(2026, 11, 25)
        val l = CollabInviteNotifier.lines(
            scheduledAtMs = ms(2026, 11, 27), addr = "경기 용인시 기흥구 두산위브", title = "용인 현장",
            timeLabel = "14:00", ownerName = "하우스픽", dailyWage = 25, deadlineMs = now + 11 * 3_600_000L, now = now
        )
        assertTrue(l.title.contains("11/27"))
        assertTrue(l.title.contains("[용인]"))
        assertTrue(l.title.endsWith("지원 요청"))
    }

    @Test
    fun `본문 = 마감 · 시각 · 일당 · 업체 순서`() {
        val now = ms(2026, 11, 25)
        val l = CollabInviteNotifier.lines(
            scheduledAtMs = ms(2026, 11, 27), addr = "경기 용인시 기흥구 두산위브", title = "용인 현장",
            timeLabel = "14:00", ownerName = "하우스픽", dailyWage = 25, deadlineMs = now + 11 * 3_600_000L, now = now
        )
        assertEquals("마감 11시간 · 오후 2시 · 일당 25만 · 하우스픽 사장님", l.body)
    }

    @Test
    fun `마감 지남 — 본문서 마감 생략 · 업체는 남음`() {
        val now = ms(2026, 11, 25)
        val l = CollabInviteNotifier.lines(
            scheduledAtMs = ms(2026, 11, 27), addr = "경기 용인시 기흥구", title = "용인 현장",
            timeLabel = "14:00", ownerName = "하우스픽", dailyWage = 25, deadlineMs = now, now = now
        )
        assertFalse(l.body.contains("마감"))
        assertEquals("오후 2시 · 일당 25만 · 하우스픽 사장님", l.body)
    }

    @Test
    fun `날짜·지역 없으면 제목은 협업 지원 요청 · 업체 없으면 다른 사장님`() {
        val now = ms(2026, 11, 25)
        val l = CollabInviteNotifier.lines(
            scheduledAtMs = 0L, addr = null, title = "", timeLabel = null,
            ownerName = "", dailyWage = null, deadlineMs = now + 5 * 3_600_000L, now = now
        )
        assertEquals("협업 지원 요청", l.title)
        assertEquals("마감 5시간 · 다른 사장님", l.body)
    }

    @Test
    fun `시각 미지정(00시)·일당 0 이면 생략`() {
        val now = ms(2026, 11, 25)
        val l = CollabInviteNotifier.lines(
            scheduledAtMs = 0L, addr = "경기 수원시 영통구", title = "수원 현장", timeLabel = "00:00",
            ownerName = "김", dailyWage = 0, deadlineMs = now + 3 * 3_600_000L, now = now
        )
        assertEquals("[수원] 지원 요청", l.title)
        assertEquals("마감 3시간 · 김 사장님", l.body)
    }
}
