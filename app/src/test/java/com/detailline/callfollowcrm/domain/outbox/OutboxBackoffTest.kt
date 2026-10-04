package com.detailline.callfollowcrm.domain.outbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백오프(다시 보내기 간격) 시험 — 폰에서 눈으로 못 보는 셈.
 *   계단: 0 → 30초 → 2분 → 10분 → 30분 → 1시간 → 3시간(상한). ±20% 지터.
 */
class OutboxBackoffTest {

    @Test fun `계단 값이 설계대로다`() {
        assertEquals(0L, OutboxRules.backoffMs(0))
        assertEquals(30_000L, OutboxRules.backoffMs(1))
        assertEquals(120_000L, OutboxRules.backoffMs(2))
        assertEquals(600_000L, OutboxRules.backoffMs(3))
        assertEquals(1_800_000L, OutboxRules.backoffMs(4))
        assertEquals(3_600_000L, OutboxRules.backoffMs(5))
        assertEquals(10_800_000L, OutboxRules.backoffMs(6))
    }

    @Test fun `상한을 넘지 않는다 - 많이 실패해도 3시간`() {
        assertEquals(10_800_000L, OutboxRules.backoffMs(7))
        assertEquals(10_800_000L, OutboxRules.backoffMs(99))
    }

    @Test fun `음수 시도는 즉시`() {
        assertEquals(0L, OutboxRules.backoffMs(-1))
    }

    @Test fun `지터는 0에서 0, 그 외엔 ±20퍼센트 안`() {
        assertEquals(0L, OutboxRules.jitter(0L, 0.5))
        val base = 600_000L
        for (r in listOf(0.0, 0.25, 0.5, 0.75, 0.99)) {
            val j = OutboxRules.jitter(base, r)
            assertTrue("지터 $j 가 0.8배 미만", j >= (base * 0.8).toLong())
            assertTrue("지터 $j 가 1.2배 초과", j <= (base * 1.2).toLong())
        }
    }

    @Test fun `지터는 음수가 안 된다`() {
        assertTrue(OutboxRules.jitter(30_000L, 0.0) >= 0L)
        assertTrue(OutboxRules.jitter(30_000L, 0.999) >= 0L)
    }

    @Test fun `다음 시도 시각은 지금 더하기 지터된 백오프`() {
        val now = 1_000_000L
        // attempts=0 → 즉시
        assertEquals(now, OutboxRules.nextAttemptAt(now, 0, 0.5))
        // attempts=1 → now + (30초 근처)
        val at = OutboxRules.nextAttemptAt(now, 1, 0.5)
        assertTrue(at in (now + 24_000L)..(now + 36_000L))
    }
}
