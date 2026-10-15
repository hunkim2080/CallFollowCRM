package com.detailline.callfollowcrm.domain.collab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🤝 협업 진행 알림 한 번만 — FCM 과 폴링이 **같은 이벤트**를 들고 와도 둘째는 막힌다. (2026-10-15 사장님 실기)
 *
 * 사장님 알림 센터에 「협업 현장 출발 🚗」이 22:35 · 22:36 두 줄로 찍혔다 —
 *   22:35 = FCM 즉시 푸시, 22:36 = 60초 폴링이 같은 event_id 를 다시 띄운 것.
 */
class CollabNotifyDedupTest {

    private val now = 1_760_540_100_000L   // 2026-10-15 22:35 쯤
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test fun `처음 본 이벤트는 띄운다`() {
        val v = CollabNotifyDedup.admit(emptySet(), CollabNotifyDedup.key("s1", "departed", "evt_a"), now)
        assertTrue(v.admit)
        assertEquals(1, v.next.size)
    }

    @Test fun `재현 - FCM 22시35분에 띄운 출발을 폴링이 22시36분에 또 들고 오면 막힌다`() {
        val key = CollabNotifyDedup.key("s1", "departed", "evt_a")
        val fcm = CollabNotifyDedup.admit(emptySet(), key, now)
        assertTrue(fcm.admit)
        val poll = CollabNotifyDedup.admit(fcm.next, key, now + minute)
        assertFalse(poll.admit)
        // 기억은 그대로(한 줄) — 둘째가 와도 늘어나지 않는다.
        assertEquals(1, poll.next.size)
    }

    @Test fun `같은 시각에 두 번 와도 둘째는 막힌다 - 14시00분 완료 두 줄`() {
        val key = CollabNotifyDedup.key("s1", "completed", "evt_c")
        val first = CollabNotifyDedup.admit(emptySet(), key, now)
        val second = CollabNotifyDedup.admit(first.next, key, now)
        assertTrue(first.admit)
        assertFalse(second.admit)
    }

    @Test fun `같은 현장이라도 단계가 다르면 각각 띄운다 - 출발 뒤 완료`() {
        val dep = CollabNotifyDedup.admit(emptySet(), CollabNotifyDedup.key("s1", "departed", "evt_a"), now)
        val done = CollabNotifyDedup.admit(dep.next, CollabNotifyDedup.key("s1", "completed", "evt_b"), now + minute)
        assertTrue(dep.admit)
        assertTrue(done.admit)
        assertEquals(2, done.next.size)
    }

    @Test fun `같은 단계라도 이벤트 id 가 다르면 새것 - 되돌렸다가 다시 출발`() {
        val a = CollabNotifyDedup.admit(emptySet(), CollabNotifyDedup.key("s1", "departed", "evt_a"), now)
        val b = CollabNotifyDedup.admit(a.next, CollabNotifyDedup.key("s1", "departed", "evt_z"), now + 5 * minute)
        assertTrue(b.admit)
    }

    @Test fun `24시간 지나면 같은 열쇠도 다시 띄우고 옛 기억은 지운다`() {
        val key = CollabNotifyDedup.key("s1", "accepted", "s1")   // 수락은 event_id 없이 shareId 폴백
        val first = CollabNotifyDedup.admit(emptySet(), key, now)
        val later = CollabNotifyDedup.admit(first.next, key, now + CollabNotifyDedup.WINDOW_MS + minute)
        assertTrue(later.admit)
        assertEquals(1, later.next.size)   // 옛 것은 버리고 새 것 하나
    }

    @Test fun `23시간 뒤는 아직 기억한다`() {
        val key = CollabNotifyDedup.key("s1", "arrived", "evt_r")
        val first = CollabNotifyDedup.admit(emptySet(), key, now)
        assertFalse(CollabNotifyDedup.admit(first.next, key, now + 23 * hour).admit)
    }

    @Test fun `상한을 넘으면 오래된 것부터 버린다`() {
        var seen = emptySet<String>()
        for (i in 0 until CollabNotifyDedup.CAP + 10) {
            seen = CollabNotifyDedup.admit(seen, CollabNotifyDedup.key("s", "departed", "evt_$i"), now + i).next
        }
        assertEquals(CollabNotifyDedup.CAP, seen.size)
        // 제일 오래된 0번은 밀려났다 → 다시 오면 띄운다. 제일 새것은 남아 있다 → 막힌다.
        assertTrue(CollabNotifyDedup.admit(seen, CollabNotifyDedup.key("s", "departed", "evt_0"), now + 1000).admit)
        val last = CollabNotifyDedup.CAP + 9
        assertFalse(CollabNotifyDedup.admit(seen, CollabNotifyDedup.key("s", "departed", "evt_$last"), now + 1000).admit)
    }

    @Test fun `깨진 기억 줄은 조용히 버린다`() {
        val seen = setOf("이상한줄", "열쇠@숫자아님", "@123")
        val v = CollabNotifyDedup.admit(seen, CollabNotifyDedup.key("s1", "departed", "evt_a"), now)
        assertTrue(v.admit)
        assertEquals(1, v.next.size)
    }

    @Test fun `열쇠는 앞뒤 공백을 다듬어 FCM 과 폴링이 같은 글자가 된다`() {
        assertEquals(
            CollabNotifyDedup.key("s1", "departed", "evt_a"),
            CollabNotifyDedup.key(" s1 ", "departed ", " evt_a")
        )
    }
}
