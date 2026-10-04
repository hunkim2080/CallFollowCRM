package com.detailline.callfollowcrm.domain.outbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 나이(§4-D·§7) — 언제 사장님께 보이고, 언제 dead 로 넘기나.
 *   24h 미만: 조용(연결되면 보냄). 24h~7d: 「N일째」. 7d 넘음: dead.
 */
class OutboxAgeTest {

    private val day = 24L * 3600_000
    private val created = 1_000_000_000L

    @Test fun `하루 안쪽은 조용`() {
        assertFalse(OutboxRules.isStale(created, created + 1000L))
        assertFalse(OutboxRules.isStale(created, created + day - 1))
    }

    @Test fun `하루 넘으면 사장님께 보인다`() {
        assertTrue(OutboxRules.isStale(created, created + day))
        assertTrue(OutboxRules.isStale(created, created + 3 * day))
    }

    @Test fun `이레 넘으면 만료(dead 로)`() {
        assertFalse(OutboxRules.isExpired(created, created + 6 * day))
        assertTrue(OutboxRules.isExpired(created, created + 7 * day))
        assertTrue(OutboxRules.isExpired(created, created + 30 * day))
    }

    @Test fun `며칠째 셈`() {
        assertEquals(0, OutboxRules.daysStuck(created, created + 1000L))
        assertEquals(1, OutboxRules.daysStuck(created, created + day))
        assertEquals(3, OutboxRules.daysStuck(created, created + 3 * day + 5000L))
        assertEquals(0, OutboxRules.daysStuck(created, created - 5000L))  // 시계 거꾸로여도 음수 아님
    }
}
