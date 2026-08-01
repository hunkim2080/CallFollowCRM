package com.detailline.callfollowcrm.presentation.screen.collab

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 협업 기록 로컬 폴백 집계(buildLocalMonthly) 검증.
 *
 * 사장님 신고(2026-08-01): "협업을 했다가 취소한 것이 그대로 등록되어 있네."
 *   원인 = 기록에 status!="declined" 만 걸러서 취소(ended)·대기(pending)가 남았음.
 *   fix = **수락(accepted)한 것만** 기록에 넣는다(취소·거절·대기 제외).
 */
class CollabRecordViewModelTest {

    private fun at(y: Int, mo: Int, d: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply { clear(); set(y, mo - 1, d, 9, 0, 0) }.timeInMillis

    private fun site(
        shareId: String = "s",
        ownerName: String = "김사장",
        ownerPhone: String = "01011112222",
        partnerName: String? = null,
        dailyWage: Int? = 30,
        status: String = "accepted",
        scheduledAtMs: Long = at(2026, 7, 10)
    ) = SharedSiteRepository.SharedSite(
        shareId = shareId,
        ownerPhone = ownerPhone,
        ownerName = ownerName,
        partnerName = partnerName,
        title = "현장",
        addr = null,
        scheduledAtMs = scheduledAtMs,
        timeLabel = null,
        workSummary = null,
        dailyWage = dailyWage,
        memo = null,
        status = status,
        progress = SharedSiteRepository.Progress.ASSIGNED,
        createdAtMs = 0L
    )

    @Test fun `취소(ended)·거절(declined)·대기(pending)는 제외 - 수락만 기록`() {
        val rcv = listOf(
            site(shareId = "a", status = "accepted", ownerName = "김사장", ownerPhone = "01000000001"),
            site(shareId = "b", status = "ended", ownerName = "박사장", ownerPhone = "01000000002"),    // 취소 — 제외
            site(shareId = "c", status = "declined", ownerName = "이사장", ownerPhone = "01000000003"),
            site(shareId = "d", status = "pending", ownerName = "최사장", ownerPhone = "01000000004")
        )
        val r = CollabRecordViewModel.buildLocalMonthly(rcv, emptyList(), "2026-07")
        assertEquals(1, r.received.count)
        assertEquals(1, r.received.partners.size)
        assertEquals("김사장", r.received.partners[0].partnerName)
    }

    @Test fun `방향 분리 - withMe는 받은(수입), byMe는 준(지출)`() {
        val rcv = listOf(site(shareId = "r", ownerName = "받은사장", dailyWage = 30))
        val giv = listOf(site(shareId = "g", partnerName = "준사장", dailyWage = 20))
        val r = CollabRecordViewModel.buildLocalMonthly(rcv, giv, "2026-07")
        assertEquals(30, r.received.totalWage)
        assertEquals(20, r.given.totalWage)
        assertEquals("받은사장", r.received.partners[0].partnerName)
        assertEquals("준사장", r.given.partners[0].partnerName)
    }

    @Test fun `월 필터 - 그 달만, availableMonths 는 최신순`() {
        val rcv = listOf(
            site(shareId = "jul", scheduledAtMs = at(2026, 7, 5), dailyWage = 30),
            site(shareId = "aug", scheduledAtMs = at(2026, 8, 5), dailyWage = 50)
        )
        val r = CollabRecordViewModel.buildLocalMonthly(rcv, emptyList(), "2026-07")
        assertEquals(30, r.received.totalWage)
        assertEquals(listOf("2026-08", "2026-07"), r.availableMonths)
    }

    @Test fun `한 사장님 여러 현장 합산`() {
        val rcv = listOf(
            site(shareId = "1", ownerName = "김사장", ownerPhone = "01011112222", dailyWage = 30),
            site(shareId = "2", ownerName = "김사장", ownerPhone = "01011112222", dailyWage = 25)
        )
        val r = CollabRecordViewModel.buildLocalMonthly(rcv, emptyList(), "2026-07")
        assertEquals(1, r.received.partners.size)
        assertEquals(2, r.received.partners[0].count)
        assertEquals(55, r.received.partners[0].totalWage)
    }
}
