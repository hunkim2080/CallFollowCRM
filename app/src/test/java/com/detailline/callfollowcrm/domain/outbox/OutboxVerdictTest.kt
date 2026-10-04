package com.detailline.callfollowcrm.domain.outbox

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 응답 코드 → 판정(§4-D). **코드로만** 판단한다(글자 아님).
 *   핵심 예외 둘: `collab_end` 의 409 = DONE · `site_photo` 의 403 = RETRY.
 */
class OutboxVerdictTest {

    private val resched = OutboxKind.COLLAB_RESCHEDULE
    private val end = OutboxKind.COLLAB_END
    private val photo = OutboxKind.SITE_PHOTO

    @Test fun `2xx 는 완료`() {
        assertEquals(Verdict.DONE, OutboxRules.verdict(resched, 200))
        assertEquals(Verdict.DONE, OutboxRules.verdict(resched, 204))
    }

    @Test fun `끊김 타임아웃 오프라인(코드 없음)은 재시도`() {
        assertEquals(Verdict.RETRY, OutboxRules.verdict(resched, null))
        assertEquals(Verdict.RETRY, OutboxRules.verdict(end, null))
        assertEquals(Verdict.RETRY, OutboxRules.verdict(photo, null))
    }

    @Test fun `5xx 와 429 408 401 은 재시도`() {
        for (c in listOf(500, 502, 503, 429, 408, 401)) {
            assertEquals("코드 $c", Verdict.RETRY, OutboxRules.verdict(resched, c))
        }
    }

    @Test fun `404 는 죽음(그만 보냄)`() {
        assertEquals(Verdict.DEAD, OutboxRules.verdict(resched, 404))
        assertEquals(Verdict.DEAD, OutboxRules.verdict(end, 404))
        assertEquals(Verdict.DEAD, OutboxRules.verdict(photo, 404))
    }

    @Test fun `400 413 422 는 죽음`() {
        for (c in listOf(400, 413, 422)) {
            assertEquals("코드 $c", Verdict.DEAD, OutboxRules.verdict(resched, c))
        }
    }

    @Test fun `협업 해제의 409는 완료(이미 종료 = 끝난 것)`() {
        assertEquals(Verdict.DONE, OutboxRules.verdict(end, 409))
        // 다른 종류의 409 는 죽음
        assertEquals(Verdict.DEAD, OutboxRules.verdict(resched, 409))
        assertEquals(Verdict.DEAD, OutboxRules.verdict(photo, 409))
    }

    @Test fun `사진의 403은 재시도(티어 게이트), 협업의 403은 죽음`() {
        assertEquals(Verdict.RETRY, OutboxRules.verdict(photo, 403))
        assertEquals(Verdict.DEAD, OutboxRules.verdict(resched, 403))
        assertEquals(Verdict.DEAD, OutboxRules.verdict(end, 403))
    }

    @Test fun `dead 사유는 사장님 말로`() {
        assertEquals("본인 현장이 아니라고 했어요", OutboxRules.deadReason(403))
        assertEquals("서버가 이 현장을 모른다고 했어요", OutboxRules.deadReason(404))
        assertEquals("내용이 잘못됐다고 했어요", OutboxRules.deadReason(400))
    }
}
