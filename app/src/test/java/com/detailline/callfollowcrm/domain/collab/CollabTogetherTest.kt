package com.detailline.callfollowcrm.domain.collab

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🤝 협업 사장 함께한 현장 양방향 묶기 — 폰에서 못 보는 걸 시험이 본다. (2026-10-27)
 */
class CollabTogetherTest {

    private fun ms(y: Int, m: Int, d: Int): Long =
        java.time.LocalDate.of(y, m, d)
            .atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
            .toInstant().toEpochMilli()

    private fun site(
        id: String,
        atMs: Long,
        wage: Int?,
        status: String = "accepted",
        progress: SharedSiteRepository.Progress = SharedSiteRepository.Progress.COMPLETED
    ) = SharedSite(
        shareId = id, ownerPhone = "01012345678", ownerName = "탄성 파인",
        partnerName = null, title = "현장 $id", addr = null,
        scheduledAtMs = atMs, timeLabel = null, workSummary = null,
        dailyWage = wage, memo = null, status = status, progress = progress, createdAtMs = atMs
    )

    private val r1 = site("r1", ms(2026, 10, 20), 18)
    private val r2 = site("r2", ms(2026, 10, 10), 15)
    private val r3 = site("r3", ms(2026, 9, 5), 20)
    private val rPending = site("rp", ms(2026, 10, 1), 10, status = "pending")
    private val g1 = site("g1", ms(2026, 10, 15), 16)
    private val g2 = site("g2", ms(2026, 8, 20), 20, progress = SharedSiteRepository.Progress.ASSIGNED)

    private val received = listOf(r1, r2, r3, rPending)
    private val given = listOf(g1, g2)

    @Test fun `수락된 것만 세고 받은+준을 합친다`() {
        val v = CollabTogether.build(received, given)
        assertEquals(5, v.total)            // rPending 빠짐
        assertEquals(3, v.receivedCount)    // r1,r2,r3
        assertEquals(2, v.givenCount)       // g1,g2
    }

    @Test fun `일당 합계는 완료 건만 — 방향별로 안 섞는다`() {
        val v = CollabTogether.build(received, given)
        assertEquals(18 + 15 + 20, v.receivedWageMan)  // 53
        assertEquals(16, v.givenWageMan)               // g2 는 미완료라 빠짐
    }

    @Test fun `방향 꼬리표와 최근 순서`() {
        val v = CollabTogether.build(received, given)
        assertEquals(ms(2026, 10, 20), v.recentMs)
        // 최근이 위: r1(10/20) · g1(10/15) · r2(10/10) · r3(9/5) · g2(8/20)
        assertEquals(listOf("r1", "g1", "r2", "r3", "g2"), v.entries.map { it.site.shareId })
        assertEquals(CollabTogether.Dir.RECEIVED, v.entries.first().dir)
        assertEquals(CollabTogether.Dir.GIVEN, v.entries[1].dir)
    }

    @Test fun `달별 묶기 — 최신 달이 위`() {
        val v = CollabTogether.build(received, given)
        val months = CollabTogether.monthsOf(v.entries)
        assertEquals(listOf("2026-10", "2026-09", "2026-08"), months.map { it.ym })
        assertEquals(3, months.first().entries.size)   // 10월: r1,g1,r2
    }

    @Test fun `필터한 entries 로도 달별 묶기가 된다`() {
        val v = CollabTogether.build(received, given)
        val onlyReceived = v.entries.filter { it.dir == CollabTogether.Dir.RECEIVED }
        val months = CollabTogether.monthsOf(onlyReceived)
        assertEquals(listOf("2026-10", "2026-09"), months.map { it.ym })
        assertEquals(2, months.first().entries.size)   // 10월 받은: r1,r2
    }

    @Test fun `협업 사장 판정 — 수락 하나라도 있으면 true`() {
        assertTrue(CollabTogether.isPartner(received, given))
        assertTrue(CollabTogether.isPartner(listOf(r1), emptyList()))
        assertTrue(CollabTogether.isPartner(emptyList(), listOf(g1)))
    }

    @Test fun `협업 사장 아님 — 비었거나 대기뿐이면 false`() {
        assertFalse(CollabTogether.isPartner(emptyList(), emptyList()))
        assertFalse(CollabTogether.isPartner(listOf(rPending), emptyList()))
    }
}
