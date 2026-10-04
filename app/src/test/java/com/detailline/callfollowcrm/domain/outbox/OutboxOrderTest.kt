package com.detailline.callfollowcrm.domain.outbox

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 보내는 순서·블록(§4-C) — simulateRound.
 *   실제 drain 이 **바로 이 함수**를 쓴다(테스트가 보는 로직 = 도는 로직).
 *     · 같은 현장 앞이 실패(RETRY)면 그 뒤 같은 현장은 이번 라운드 건너뜀(순서 보존)
 *     · 다른 현장은 서로 안 기다린다
 *     · DEAD 는 그 현장을 막지 않는다
 *     · 다른 번호로 로그인하면 옛 번호 행은 건너뜀
 */
class OutboxOrderTest {

    private val owner = "01080056674"
    private fun row(id: Long, target: String, owner: String = this.owner, attempts: Int = 0, created: Long = 1000L) =
        OutboxRow(id, OutboxKind.COLLAB_RESCHEDULE.wire, target, owner, attempts, created)

    private fun run(
        rows: List<OutboxRow>,
        activeOwner: String = owner,
        now: Long = 2000L,
        send: suspend (OutboxRow) -> Pair<Verdict, Int?>
    ) = runBlocking {
        OutboxRules.simulateRound(rows, activeOwner, now, { 0.5 }, send)
    }

    @Test fun `같은 현장 - 앞이 실패하면 뒤는 건너뜀`() {
        val rows = listOf(row(1, "A"), row(2, "A"))
        val actions = run(rows) { Verdict.RETRY to null }
        assertTrue(actions[0] is RoundAction.Retry)     // 1번 시도→실패
        assertTrue(actions[1] is RoundAction.Skipped)   // 2번(같은 현장)은 건너뜀
    }

    @Test fun `다른 현장은 서로 안 기다린다`() {
        val rows = listOf(row(1, "A"), row(2, "B"))
        var sentIds = mutableListOf<Long>()
        val actions = run(rows) { r -> sentIds += r.id; if (r.targetKey == "A") Verdict.RETRY to null else Verdict.DONE to 200 }
        assertTrue(actions[0] is RoundAction.Retry)      // A 실패
        assertTrue(actions[1] is RoundAction.Done)       // B 는 그래도 보냄
        assertEquals(listOf(1L, 2L), sentIds)
    }

    @Test fun `DEAD 는 같은 현장 뒤엣것을 막지 않는다`() {
        val rows = listOf(row(1, "A"), row(2, "A"))
        val actions = run(rows) { r -> if (r.id == 1L) Verdict.DEAD to 404 else Verdict.DONE to 200 }
        assertTrue(actions[0] is RoundAction.Dead)
        assertTrue(actions[1] is RoundAction.Done)       // 앞이 죽었어도 뒤는 간다
    }

    @Test fun `다른 번호로 로그인하면 옛 번호 행은 건너뜀`() {
        val rows = listOf(row(1, "A", owner = "01099990000"), row(2, "B"))
        var sent = 0
        val actions = run(rows) { sent++; Verdict.DONE to 200 }
        assertTrue(actions[0] is RoundAction.Skipped)    // 옛 번호
        assertTrue(actions[1] is RoundAction.Done)
        assertEquals(1, sent)                            // 옛 번호는 네트워크도 안 씀
    }

    @Test fun `빈 번호(사진)는 아무 주인이나 - 번호 바뀌어도 보낸다`() {
        val photo = OutboxRow(1, OutboxKind.SITE_PHOTO.wire, "photo:1", "", 0, 1000L)
        val actions = run(listOf(photo), activeOwner = "01080056674") { Verdict.DONE to 200 }
        assertTrue(actions[0] is RoundAction.Done)       // 빈 ownerPhone = 지금 주인 것 → 보냄
    }

    @Test fun `7일 넘은 pending 은 보내보기 전에 죽는다`() {
        val now = 10L * 24 * 3600_000
        val rows = listOf(row(1, "A", created = 0L))     // 10일 전
        var sent = 0
        val actions = runBlocking {
            OutboxRules.simulateRound(rows, owner, now, { 0.5 }) { sent++; Verdict.DONE to 200 }
        }
        assertTrue(actions[0] is RoundAction.Dead)
        assertEquals(0, sent)                            // 네트워크 안 씀
    }

    @Test fun `라운드 상한 50 - 넘는 건 다음 라운드로`() {
        val rows = (1..60L).map { row(it, "T$it") }      // 전부 다른 현장
        var sent = 0
        val actions = run(rows) { sent++; Verdict.DONE to 200 }
        assertEquals(OutboxRules.MAX_ROWS_PER_ROUND, sent)
        assertEquals(10, actions.count { it is RoundAction.Skipped })
    }

    @Test fun `사진은 협업 명령보다 뒤에 보낸다 - 첫날 사진 백필에 명령이 안 밀리게`() {
        // 사진(id 1, 먼저 넣음)과 협업 명령(id 2, 나중). id 순이면 사진이 먼저지만, 협업이 먼저여야 한다.
        val photo = OutboxRow(1, OutboxKind.SITE_PHOTO.wire, "p1", owner, 0, 1000L)
        val collab = OutboxRow(2, OutboxKind.COLLAB_RESCHEDULE.wire, "A", owner, 0, 1000L)
        val order = mutableListOf<Long>()
        run(listOf(photo, collab)) { r -> order += r.id; Verdict.DONE to 200 }
        assertEquals(listOf(2L, 1L), order)   // 협업(2) 먼저, 사진(1) 나중 (Fable #10)
    }

    @Test fun `재시도 행의 다음 시각은 미래다`() {
        val rows = listOf(row(1, "A", attempts = 1))
        val actions = run(rows, now = 5000L) { Verdict.RETRY to 503 }
        val retry = actions[0] as RoundAction.Retry
        assertEquals(2, retry.attempts)
        assertTrue(retry.nextAttemptAtMs > 5000L)
        assertEquals("HTTP 503", retry.error)
    }
}
