package com.detailline.callfollowcrm.domain.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⏸️ 보류 손님 — 판정·풀림·안부 넛지는 **OnHold 한 곳**. (2026-10-16 사장님)
 *   상담함 칩 숫자·칩 목록·고객 상세 줄·달력 미러가 전부 이 규칙을 본다 — 여기서 틀리면 네 곳이 같이 틀린다.
 */
class OnHoldTest {

    private val 하루 = 86_400_000L
    private val 오늘 = 1_760_500_000_000L

    @Test
    fun `찍혀 있고 안 끝났으면 보류다`() {
        assertTrue(OnHold.isOnHold(onHoldAt = 오늘, isWorkDone = false))
    }

    @Test
    fun `안 찍혀 있으면 보류가 아니다`() {
        assertFalse(OnHold.isOnHold(onHoldAt = null, isWorkDone = false))
    }

    @Test
    fun `찍혀 있어도 끝난 손님은 보류가 아니다 — 잔금 받음·완료가 이긴다`() {
        assertFalse(OnHold.isOnHold(onHoldAt = 오늘, isWorkDone = true))
    }

    @Test
    fun `오늘 이후 날짜가 잡히면 보류가 풀린다`() {
        assertNull(OnHold.afterDateSet(onHoldAt = 오늘 - 하루 * 5, scheduledWorkDate = 오늘 + 하루 * 3, todayStart = 오늘))
        assertNull(OnHold.afterDateSet(onHoldAt = 오늘 - 하루 * 5, scheduledWorkDate = 오늘, todayStart = 오늘))
    }

    @Test
    fun `지난 날짜로는 안 풀린다 — 옛 건이 대표로 잡힌 것뿐`() {
        assertEquals(오늘 - 하루 * 5, OnHold.afterDateSet(onHoldAt = 오늘 - 하루 * 5, scheduledWorkDate = 오늘 - 하루 * 30, todayStart = 오늘))
    }

    @Test
    fun `날짜가 없으면 그대로다`() {
        assertEquals(오늘, OnHold.afterDateSet(onHoldAt = 오늘, scheduledWorkDate = null, todayStart = 오늘))
        assertNull(OnHold.afterDateSet(onHoldAt = null, scheduledWorkDate = 오늘 + 하루, todayStart = 오늘))
    }

    @Test
    fun `조용한 날 수 — 21일이면 3주`() {
        assertEquals(21, OnHold.quietDays(오늘 - 하루 * 21, 오늘))
        assertEquals(3, OnHold.quietWeeks(오늘 - 하루 * 21, 오늘))
        assertEquals(2, OnHold.quietWeeks(오늘 - 하루 * 20, 오늘))
        assertEquals(0, OnHold.quietDays(오늘 + 하루, 오늘))   // 미래 시각이 와도 음수는 안 된다
    }

    @Test
    fun `조용해진 시점은 마지막 연락과 보류 시작 중 나중 것`() {
        assertEquals(오늘, OnHold.quietSince(lastActivityMs = 오늘 - 하루 * 10, onHoldAt = 오늘))
        assertEquals(오늘, OnHold.quietSince(lastActivityMs = 오늘, onHoldAt = 오늘 - 하루 * 10))
        assertEquals(오늘, OnHold.quietSince(lastActivityMs = 0L, onHoldAt = 오늘))   // 연락 기록 없음 = 보류 시작부터
    }

    @Test
    fun `3주 미만이면 넛지 안 한다`() {
        assertFalse(OnHold.needsNudge(오늘 - 하루 * 20, 오늘))
        assertTrue(OnHold.needsNudge(오늘 - 하루 * 21, 오늘))
    }

    @Test
    fun `넛지는 가장 오래 조용한 한 명만`() {
        data class P(val name: String, val since: Long)
        val 김 = P("김철수", 오늘 - 하루 * 25)
        val 이 = P("이영희", 오늘 - 하루 * 40)
        val 박 = P("박민수", 오늘 - 하루 * 3)   // 아직 조용하지 않다
        assertEquals(이, OnHold.quietest(listOf(김, 이, 박), 오늘) { it.since })
    }

    @Test
    fun `아무도 3주를 안 넘겼으면 띠가 없다`() {
        data class P(val since: Long)
        assertNull(OnHold.quietest(listOf(P(오늘 - 하루 * 5), P(오늘)), 오늘) { it.since })
        assertNull(OnHold.quietest(emptyList<P>(), 오늘) { it.since })
    }
}
