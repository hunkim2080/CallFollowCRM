package com.detailline.callfollowcrm.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 🤝 협업 요청 알림 글자 조립(표준 알림) — 제목·본문이 사장님 결정대로 나오나. (2026-11-25)
 *   본문엔 **마감 글자 없음**(막판 1시간만 제목 옆 카운트다운) · 시각엔 "작업시작" 라벨.
 *   날짜 라벨의 요일(괄호)은 실제 달력 의존이라 "11/27" 포함으로만 본다.
 */
class CollabInviteNotifierTest {

    private fun ms(y: Int, mo: Int, d: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, mo - 1, d, 0, 0, 0) }.timeInMillis

    @Test
    fun `제목 = 날짜 · 지역 · 지원요청`() {
        val l = CollabInviteNotifier.lines(
            scheduledAtMs = ms(2026, 11, 27), addr = "경기 용인시 기흥구 두산위브", title = "용인 현장",
            timeLabel = "14:00", ownerName = "하우스픽", dailyWage = 25
        )
        assertTrue(l.title.contains("11/27"))
        assertTrue(l.title.contains("[용인]"))
        assertTrue(l.title.endsWith("지원 요청"))
    }

    @Test
    fun `본문 = 작업시작 시각 · 일당 · 업체 (마감 글자 없음)`() {
        val l = CollabInviteNotifier.lines(
            scheduledAtMs = ms(2026, 11, 27), addr = "경기 용인시 기흥구 두산위브", title = "용인 현장",
            timeLabel = "14:00", ownerName = "하우스픽", dailyWage = 25
        )
        assertEquals("작업시작 오후 2시 · 일당 25만 · 하우스픽", l.body)
        assertFalse(l.body.contains("마감"))
    }

    @Test
    fun `날짜·지역 없으면 제목은 협업 지원 요청 · 업체 없으면 다른 사장님`() {
        val l = CollabInviteNotifier.lines(
            scheduledAtMs = 0L, addr = null, title = "", timeLabel = null,
            ownerName = "", dailyWage = null
        )
        assertEquals("협업 지원 요청", l.title)
        assertEquals("다른 업체", l.body)
    }

    @Test
    fun `시각 미지정(00시)·일당 0 이면 생략`() {
        val l = CollabInviteNotifier.lines(
            scheduledAtMs = 0L, addr = "경기 수원시 영통구", title = "수원 현장", timeLabel = "00:00",
            ownerName = "김", dailyWage = 0
        )
        assertEquals("[수원] 지원 요청", l.title)
        assertEquals("김", l.body)
    }
}
