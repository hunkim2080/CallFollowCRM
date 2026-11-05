package com.detailline.callfollowcrm.domain.collab

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 협업 진행 단계 — 현장마다 하나로 묶어 단계로. (2026-11-05 사장님: 쿠팡 배송추적 느낌)
 *
 * 막는 것 —
 *   ① 거절을 단계로 세는 것(거절=별도)
 *   ② 진행 막대가 도달한 단계와 어긋나는 것
 */
class CollabProgressStepsTest {

    @Test
    fun `단계 순서 — 수락1 출발2 도착3 완료4, 거절은 0`() {
        assertEquals(1, CollabProgressSteps.orderOf("accepted"))
        assertEquals(2, CollabProgressSteps.orderOf("departed"))
        assertEquals(3, CollabProgressSteps.orderOf("arrived"))
        assertEquals(4, CollabProgressSteps.orderOf("completed"))
        assertEquals(0, CollabProgressSteps.orderOf("declined"))
        assertEquals(0, CollabProgressSteps.orderOf("뭔가"))
    }

    @Test
    fun `진행 막대 — 도달까지 채움`() {
        assertEquals("● 수락 ─ ○ 출발 ─ ○ 도착 ─ ○ 완료", CollabProgressSteps.line(1))
        assertEquals("● 수락 ─ ● 출발 ─ ○ 도착 ─ ○ 완료", CollabProgressSteps.line(2))
        assertEquals("● 수락 ─ ● 출발 ─ ● 도착 ─ ○ 완료", CollabProgressSteps.line(3))
        assertEquals("● 수락 ─ ● 출발 ─ ● 도착 ─ ● 완료", CollabProgressSteps.line(4))
    }

    @Test
    fun `단계 아니면 빈 줄`() {
        assertEquals("", CollabProgressSteps.line(0))
        assertEquals("", CollabProgressSteps.line(-1))
    }
}
