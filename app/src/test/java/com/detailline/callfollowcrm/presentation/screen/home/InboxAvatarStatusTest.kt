package com.detailline.callfollowcrm.presentation.screen.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 상담함 아이콘 상태색 셈 — 급함>대기>새것>조율>조용 순이 지켜지는지. (2026-10-15 사장님)
 *   폰에서 색을 눈으로 못 보니 여기서 대신 본다(§12-E). 시간대 무관하게 통과하도록
 *   일정은 '먼 미래(+10일)' / '없음(0)'으로만 가른다.
 */
class InboxAvatarStatusTest {
    private val now = 1_760_000_000_000L   // 고정 기준시각
    private val dayMs = 24L * 60 * 60 * 1000
    private val future = now + 10 * dayMs  // 분명한 미래 일정
    private val old = now - 3 * dayMs      // 하루 넘게 방치
    private val recent = now - 60 * 1000   // 방금

    @Test fun 미답_하루넘음_빨강() {
        assertEquals(
            InboxAvatarStatus.URGENT,
            inboxAvatarStatus(unanswered = true, lastActivityMs = old, nowMs = now, unread = true, scheduledWorkDate = future, isWorkDone = false)
        )
    }

    @Test fun 미답_오늘_노랑() {
        assertEquals(
            InboxAvatarStatus.WAITING,
            inboxAvatarStatus(unanswered = true, lastActivityMs = recent, nowMs = now, unread = true, scheduledWorkDate = null, isWorkDone = false)
        )
    }

    @Test fun 미답아님_안읽음_파랑() {
        assertEquals(
            InboxAvatarStatus.UNREAD,
            inboxAvatarStatus(unanswered = false, lastActivityMs = recent, nowMs = now, unread = true, scheduledWorkDate = future, isWorkDone = false)
        )
    }

    @Test fun 읽음_일정있음_초록() {
        assertEquals(
            InboxAvatarStatus.SCHEDULED,
            inboxAvatarStatus(unanswered = false, lastActivityMs = recent, nowMs = now, unread = false, scheduledWorkDate = future, isWorkDone = false)
        )
    }

    @Test fun 완료된_일정은_초록아님_회색() {
        assertEquals(
            InboxAvatarStatus.QUIET,
            inboxAvatarStatus(unanswered = false, lastActivityMs = recent, nowMs = now, unread = false, scheduledWorkDate = future, isWorkDone = true)
        )
    }

    @Test fun 할일없음_회색() {
        assertEquals(
            InboxAvatarStatus.QUIET,
            inboxAvatarStatus(unanswered = false, lastActivityMs = recent, nowMs = now, unread = false, scheduledWorkDate = 0L, isWorkDone = false)
        )
    }

    @Test fun 미답이_일정보다_우선() {
        // 일정도 잡혀 있지만 답을 안 했으면 '답할 사람'이 먼저다.
        assertEquals(
            InboxAvatarStatus.WAITING,
            inboxAvatarStatus(unanswered = true, lastActivityMs = recent, nowMs = now, unread = false, scheduledWorkDate = future, isWorkDone = false)
        )
    }
}
