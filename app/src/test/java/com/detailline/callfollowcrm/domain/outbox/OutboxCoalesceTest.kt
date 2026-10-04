package com.detailline.callfollowcrm.domain.outbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 합치기(§3) — 같은 현장에 명령이 겹칠 때.
 *   LATEST_WINS: 같은 (kind,target) pending 이 있으면 그 행 재사용(payload 만 교체).
 *   APPEND: 늘 새 행.
 *   TERMINAL: 같은 target 의 앞선 pending 전부 삭제 + 새 행.
 */
class OutboxCoalesceTest {

    private fun row(id: Long, kind: OutboxKind, target: String = "sh_1") =
        OutboxRow(id, kind.wire, target, "01080056674", 0, 1000L)

    @Test fun `주소 두 번 - 같은 pending 행 재사용(알림 한 번)`() {
        val existing = listOf(row(5, OutboxKind.COLLAB_ADDRESS))
        val plan = OutboxRules.coalescePlan(OutboxKind.COLLAB_ADDRESS, existing)
        assertEquals(5L, plan.reuseId)       // 새 행 안 만들고 5번 payload 갈아끼움
        assertTrue(plan.deleteIds.isEmpty())
    }

    @Test fun `일정은 주소 pending 을 건드리지 않는다(종류가 다름)`() {
        val existing = listOf(row(5, OutboxKind.COLLAB_ADDRESS))
        val plan = OutboxRules.coalescePlan(OutboxKind.COLLAB_RESCHEDULE, existing)
        assertNull(plan.reuseId)             // 주소(5번)와 종류가 달라 새 행
        assertTrue(plan.deleteIds.isEmpty())
    }

    @Test fun `처음 넣는 LATEST_WINS 는 새 행`() {
        val plan = OutboxRules.coalescePlan(OutboxKind.COLLAB_RESCHEDULE, emptyList())
        assertNull(plan.reuseId)
        assertTrue(plan.deleteIds.isEmpty())
    }

    @Test fun `해제(TERMINAL)는 같은 현장 앞선 pending 전부 지운다`() {
        val existing = listOf(
            row(5, OutboxKind.COLLAB_RESCHEDULE),
            row(6, OutboxKind.COLLAB_ADDRESS)
        )
        val plan = OutboxRules.coalescePlan(OutboxKind.COLLAB_END, existing)
        assertNull(plan.reuseId)                          // 해제는 늘 새 행
        assertEquals(listOf(5L, 6L), plan.deleteIds)      // 일정·주소 명령은 지운다
    }
}
