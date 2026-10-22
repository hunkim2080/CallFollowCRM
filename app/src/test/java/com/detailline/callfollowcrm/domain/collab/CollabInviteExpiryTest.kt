package com.detailline.callfollowcrm.domain.collab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 협업 요청 24h 만료 경계값. (2026-10-20 사장님) */
class CollabInviteExpiryTest {

    private val H = 3_600_000L
    private val created = 1_000_000_000_000L

    @Test fun `24h 전은 안 만료`() {
        assertFalse(CollabInviteExpiry.isExpired(created, created + 23 * H))
        assertFalse(CollabInviteExpiry.isExpired(created, created))
    }

    @Test fun `24h 딱 지나면 만료`() {
        assertTrue(CollabInviteExpiry.isExpired(created, created + 24 * H))
        assertTrue(CollabInviteExpiry.isExpired(created, created + 25 * H))
    }

    @Test fun `createdAt 모르면 안 만료`() {
        assertFalse(CollabInviteExpiry.isExpired(0L, created + 100 * H))
    }

    @Test fun `남은 시간은 올림`() {
        assertEquals(24, CollabInviteExpiry.remainingHours(created, created))
        assertEquals(3, CollabInviteExpiry.remainingHours(created, created + 21 * H))
        // 2시간 30분 남음 → 3으로 올림
        assertEquals(3, CollabInviteExpiry.remainingHours(created, created + 21 * H + 30 * 60_000L))
        // 1분 남음 → 1
        assertEquals(1, CollabInviteExpiry.remainingHours(created, created + 24 * H - 60_000L))
    }

    @Test fun `만료됐으면 남은 시간 0`() {
        assertEquals(0, CollabInviteExpiry.remainingHours(created, created + 24 * H))
        assertEquals(0, CollabInviteExpiry.remainingHours(created, created + 30 * H))
    }

    // ── 앵커(서버 공유시각 or 첫 관측) 폴백 ─────────────────────────
    @Test fun `앵커는 서버시각 우선 없으면 첫관측`() {
        assertEquals(created, CollabInviteExpiry.anchorOf(created, 999L))      // createdAt 있으면 그걸
        assertEquals(555L, CollabInviteExpiry.anchorOf(0L, 555L))              // 없으면 첫 관측
        assertEquals(0L, CollabInviteExpiry.anchorOf(0L, 0L))                  // 둘 다 없으면 0
    }

    @Test fun `앵커 만료 - 서버시각 0이어도 첫관측으로 판정`() {
        // createdAt 0, 첫 관측 created → created+24h 에 만료
        assertFalse(CollabInviteExpiry.isExpiredWithAnchor(0L, created, created + 23 * H))
        assertTrue(CollabInviteExpiry.isExpiredWithAnchor(0L, created, created + 24 * H))
        // 서버시각이 있으면 그 기준
        assertTrue(CollabInviteExpiry.isExpiredWithAnchor(created, 0L, created + 24 * H))
        // 둘 다 모르면 안 만료(안전)
        assertFalse(CollabInviteExpiry.isExpiredWithAnchor(0L, 0L, created + 100 * H))
    }

    @Test fun `앵커 남은 시간 - 모르면 null`() {
        assertEquals(3, CollabInviteExpiry.remainingHoursWithAnchor(0L, created, created + 21 * H))
        assertEquals(null, CollabInviteExpiry.remainingHoursWithAnchor(0L, 0L, created))
    }
}
