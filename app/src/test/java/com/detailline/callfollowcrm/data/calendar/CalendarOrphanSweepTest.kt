package com.detailline.callfollowcrm.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🧹 고아 청소 규칙. (2026-10-06 사장님 "한번 생성되면 제거가 안되고 계속 구글에 남아있는 느낌")
 *
 * 배경 둘:
 *   ① 간단 일정을 앱에서 지워도 구글엔 남았고,
 *   ② 2026-09-18 '건' 전환 때 열쇠가 바뀌며 같은 시공이 **쌍둥이**로 두 개 남았다.
 *
 * ⚠️ **같은 구글 계정을 폰 둘이 쓰면 달력이 한 개**다(일상폰 2080 + 업무폰 0131).
 *   "내가 안 가리킨다"만으로 지우면 **보기 전용 폰에서 청소를 누를 때 다른 폰 일정을 전부 지운다.**
 *   그래서 두 겹으로 가린다:
 *     · 도장(deviceId) 있는 일정 → **내 폰 도장일 때만** 지운다.
 *     · 도장 없는 **옛 일정** → **내가 그 고객을 가질 때만** 지운다.
 *   그리고 tracked 가 비면(=DB 못 읽음) **아무것도 안 지운다**(전체삭제 참사 방지).
 */
class CalendarOrphanSweepTest {

    private fun ev(id: String, device: String = "", customer: String = "") =
        CalendarApi.AppCalEvent(id = id, deviceId = device, customerId = customer)

    private fun orphans(
        events: List<CalendarApi.AppCalEvent>,
        myDevice: String,
        tracked: Set<String>,
        owned: Set<String>
    ) = CalendarSyncManager.orphanEventIds(events, myDevice, tracked, owned)

    @Test
    fun `내 폰이 만든 고아만 지운다`() {
        val events = listOf(ev("a", device = "ME"), ev("b", device = "ME"))
        // a 는 내가 가리킴(tracked), b 는 고아 → b 만.
        assertEquals(listOf("b"), orphans(events, "ME", setOf("a"), emptySet()))
    }

    @Test
    fun `다른 폰이 만든 건 고아여도 안 지운다 - 같은 달력 공유 안전핀`() {
        // 보기 전용 폰(ME)이 청소. 0131 폰이 올린 일정들(device=PHONE0131)은 내가 안 가리키지만 건드리면 안 된다.
        val events = listOf(ev("x", device = "PHONE0131"), ev("y", device = "PHONE0131"))
        // tracked 는 이 폰이 가진 아무 이벤트(비어있지 않게) — 안전핀 우회 조건.
        assertTrue(orphans(events, "ME", setOf("something"), emptySet()).isEmpty())
    }

    @Test
    fun `도장 없는 옛 일정 - 내 고객 것이면 지운다(쌍둥이 청소)`() {
        // 옛 이벤트(도장 없음) 두 개가 같은 고객 42 의 쌍둥이. 앱은 new 만 가리킨다.
        val events = listOf(ev("old", customer = "42"), ev("new", customer = "42"))
        assertEquals(listOf("old"), orphans(events, "ME", setOf("new"), owned = setOf("42")))
    }

    @Test
    fun `도장 없는 옛 일정 - 내 고객이 아니면 안 지운다(보기 전용 폰)`() {
        // 보기 전용 폰은 고객이 없다(owned 비어있음) → 다른 폰이 옛날에 올린 것도 안 지운다.
        val events = listOf(ev("old", customer = "42"))
        assertTrue(orphans(events, "ME", setOf("something"), owned = emptySet()).isEmpty())
    }

    @Test
    fun `추적이 비면 아무것도 안 지운다 - 전체삭제 안전핀`() {
        val events = listOf(ev("a", device = "ME"), ev("b", customer = "42"))
        assertTrue(orphans(events, "ME", emptySet(), setOf("42")).isEmpty())
    }

    @Test
    fun `내가 가리키는 건 내 폰 것이라도 안 지운다`() {
        val events = listOf(ev("a", device = "ME"))
        assertTrue(orphans(events, "ME", setOf("a"), emptySet()).isEmpty())
    }
}
