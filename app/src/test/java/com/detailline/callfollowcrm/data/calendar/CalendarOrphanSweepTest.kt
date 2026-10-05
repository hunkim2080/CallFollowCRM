package com.detailline.callfollowcrm.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🧹 고아 청소 규칙. (2026-10-06 사장님 "한번 생성되면 제거가 안되고 계속 구글에 남아있는 느낌")
 *
 * 배경: ① 간단 일정을 앱에서 지워도 구글엔 남았고, ② 2026-09-18 '건' 전환 때 열쇠가 바뀌며
 *   같은 시공이 **쌍둥이**로 두 개 남았다. 앱이 가리키는 id 집합에 없는 우리 앱 이벤트 = 고아.
 *
 * ⚠️ 이건 **사장님 실제 달력을 지우는** 판단이라 특히 못 박는다 —
 *   tracked 가 비면(=DB 를 못 읽음) **아무것도 지우면 안 된다**(전체삭제 참사 방지).
 */
class CalendarOrphanSweepTest {

    private fun orphans(app: List<String>, tracked: Set<String>) =
        CalendarSyncManager.orphanEventIds(app, tracked)

    @Test
    fun `추적 안 하는 것만 고아로 고른다`() {
        val app = listOf("a", "b", "c", "d")
        val tracked = setOf("a", "c")
        assertEquals(listOf("b", "d"), orphans(app, tracked))
    }

    @Test
    fun `중복 쌍둥이 - 추적하는 새것은 남기고 옛것만 지운다`() {
        // 같은 시공의 옛 이벤트(old)·새 이벤트(new). 앱은 new 만 가리킨다.
        val app = listOf("evt_old_0914", "evt_new_0923")
        val tracked = setOf("evt_new_0923")
        assertEquals(listOf("evt_old_0914"), orphans(app, tracked))
    }

    @Test
    fun `모두 추적 중이면 지울 게 없다`() {
        val app = listOf("a", "b")
        assertTrue(orphans(app, setOf("a", "b")).isEmpty())
    }

    @Test
    fun `추적이 비면 아무것도 안 지운다 - 전체삭제 안전핀`() {
        val app = listOf("a", "b", "c")
        assertTrue(orphans(app, emptySet()).isEmpty())
    }

    @Test
    fun `구글이 비면 지울 게 없다`() {
        assertTrue(orphans(emptyList(), setOf("a")).isEmpty())
    }
}
