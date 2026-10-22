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

    @Test fun 미래예정있으면_과거완료여도_초록() {
        // 반복 고객(1차 완료 + 2차 미래 예정) — 과거 완료로 isWorkDone=true 여도 **미래 예정이 있으면 시공대기(초록)**.
        //   (2026-10-22 사장님: 수원 금아타일 D-4 가 시공대기에 없던 것 — 과거 완료가 미래 예정을 가림)
        assertEquals(
            InboxAvatarStatus.SCHEDULED,
            inboxAvatarStatus(unanswered = false, lastActivityMs = recent, nowMs = now, unread = false, scheduledWorkDate = future, isWorkDone = true)
        )
    }

    // 🔨 isWaitlistSchedule 경계값 — 미래면 무조건, 오늘이면 아직 안 끝났을 때만, 과거면 아님. (순수 함수, 시간무관)
    @Test fun 시공대기_판정_경계값() {
        val today = 1_000_000L
        val day = 24L * 60 * 60 * 1000
        assertEquals(true, isWaitlistSchedule(today + day, isWorkDone = true, todayStart = today))   // 미래+완료 → 대기(새 예정)
        assertEquals(true, isWaitlistSchedule(today + day, isWorkDone = false, todayStart = today))  // 미래+미완 → 대기
        assertEquals(true, isWaitlistSchedule(today, isWorkDone = false, todayStart = today))        // 오늘+미완 → 대기
        assertEquals(false, isWaitlistSchedule(today, isWorkDone = true, todayStart = today))        // 오늘+완료 → 아님
        assertEquals(false, isWaitlistSchedule(today - day, isWorkDone = false, todayStart = today)) // 과거 → 아님
        assertEquals(false, isWaitlistSchedule(null, isWorkDone = false, todayStart = today))        // 없음 → 아님
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
