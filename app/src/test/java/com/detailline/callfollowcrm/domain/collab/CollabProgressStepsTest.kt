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

    @Test
    fun `스텝 칸 — 점 상태(지난 DONE·지금 CURRENT·아직 PENDING·마지막 도달 DONE_FINAL)`() {
        // reached=2(출발까지): 수락 DONE · 출발 CURRENT · 도착·완료 PENDING
        val c2 = CollabProgressSteps.cells(2)
        assertEquals(CollabProgressSteps.DotState.DONE, c2[0].dot)
        assertEquals(CollabProgressSteps.DotState.CURRENT, c2[1].dot)
        assertEquals(CollabProgressSteps.DotState.PENDING, c2[2].dot)
        assertEquals(CollabProgressSteps.DotState.PENDING, c2[3].dot)
        // reached=4(완료): 앞 셋 DONE · 마지막은 DONE_FINAL(보라)
        val c4 = CollabProgressSteps.cells(4)
        assertEquals(CollabProgressSteps.DotState.DONE, c4[0].dot)
        assertEquals(CollabProgressSteps.DotState.DONE, c4[2].dot)
        assertEquals(CollabProgressSteps.DotState.DONE_FINAL, c4[3].dot)
        // 라벨은 공용 이름 그대로
        assertEquals(listOf("수락", "출발", "도착", "완료"), CollabProgressSteps.cells(1).map { it.label })
    }

    @Test
    fun `스텝 칸 — 막대 켜짐(양 끝 동강은 꺼짐·도달 구간만 켜짐)`() {
        // reached=1: 어떤 연결 막대도 안 켜짐(수락만 찍힘)
        CollabProgressSteps.cells(1).forEach {
            assertEquals(false, it.barLeftOn); assertEquals(false, it.barRightOn)
        }
        // reached=3(도착까지): 수락→출발→도착 구간 켜짐, 도착→완료 꺼짐
        val c3 = CollabProgressSteps.cells(3)
        assertEquals(false, c3[0].barLeftOn)   // 왼쪽 끝 동강
        assertEquals(true, c3[0].barRightOn)   // 수락→출발
        assertEquals(true, c3[1].barLeftOn)
        assertEquals(true, c3[2].barLeftOn)    // 출발→도착
        assertEquals(false, c3[2].barRightOn)  // 도착→완료 아직
        assertEquals(false, c3[3].barLeftOn)   // 도착→완료 아직
        assertEquals(false, c3[3].barRightOn)  // 오른쪽 끝 동강
    }
}
