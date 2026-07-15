package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 협업 현장 → 본폰 미러 아이템 변환 검증.
 *
 * 사장님 신고(2026-07-15): "협업현장으로 수락한 현장도 일정인데 그 현장은 노출이 안 되고 있어."
 *   원인 = 미러 스냅샷을 내 고객(customers)에서만 만들어서 협업 현장(SharedSite)이 아예 안 실림.
 *
 * 규칙 (사장님 지시 + SPEC_shared_sites_owner_to_owner.md):
 *   - 내가 **수락한**(accepted) + 날짜 잡힌 것만.
 *   - 금액 = **내 일당만** ("협업은 내가 받아야 할 돈(일당)만 보이면 되는거지"). dailyWage 는 만원 → 원(×10000).
 *   - 전화번호는 절대 없음(벽, SPEC §1).
 *   - 일정 화면에서 숨긴 협업은 미러에서도 숨김.
 */
class MirrorCollabMapTest {

    private fun at(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(y, mo - 1, d, h, mi, 0) }.timeInMillis

    private fun site(
        shareId: String = "s1",
        title: String = "강동 천호동 현장",
        addr: String? = "서울 강동구 천호동 1-2",
        scheduledAtMs: Long = at(2026, 7, 20, 9, 0),
        timeLabel: String? = null,
        dailyWage: Int? = null,
        memo: String? = null,
        status: String = "accepted",
        progress: SharedSiteRepository.Progress = SharedSiteRepository.Progress.ASSIGNED
    ) = SharedSiteRepository.SharedSite(
        shareId = shareId,
        ownerPhone = "01011112222",
        ownerName = "김사장",
        title = title,
        addr = addr,
        scheduledAtMs = scheduledAtMs,
        timeLabel = timeLabel,
        workSummary = null,
        dailyWage = dailyWage,
        memo = memo,
        status = status,
        progress = progress,
        createdAtMs = 0L
    )

    private fun map(vararg s: SharedSiteRepository.SharedSite, hidden: Set<String> = emptySet()) =
        MirrorSyncManager.mapCollabSites(s.toList(), hidden)

    // ── 금액: 일당(만원) → 원 ──

    @Test fun `일당 15만원은 150000원으로 나간다 - 단위 환산`() {
        // dailyWage 는 만원 단위(SharedSite 주석), MirrorItem.total 은 원. ×10000 안 하면 15원이 된다.
        val r = map(site(dailyWage = 15))
        assertEquals(1, r.size)
        assertEquals(150_000L, r[0].total)
    }

    @Test fun `일당 미입력이면 0 - 뷰어가 숨김`() {
        assertEquals(0L, map(site(dailyWage = null))[0].total)
        assertEquals(0L, map(site(dailyWage = 0))[0].total)
    }

    @Test fun `협업 금액은 남의 총금액이 아니라 내 일당 - collab 딱지가 붙는다`() {
        // 뷰어가 collab 을 보고 "총금액" 이 아니라 "일당" 으로 라벨을 그려야 하므로 반드시 true.
        assertTrue(map(site(dailyWage = 20))[0].collab)
    }

    // ── 벽(privacy) ──

    @Test fun `협업 현장은 전화번호를 절대 안 보낸다`() {
        assertNull(map(site())[0].phone)
    }

    // ── 필터 ──

    @Test fun `수락한 것만 나간다 - 대기·거절은 제외`() {
        val r = map(
            site(shareId = "a", status = "accepted"),
            site(shareId = "b", status = "pending"),
            site(shareId = "c", status = "declined"),
            site(shareId = "d", status = "ended")
        )
        assertEquals(1, r.size)
    }

    @Test fun `날짜 없는 협업은 제외 - 일정에 못 그림`() {
        assertTrue(map(site(scheduledAtMs = 0L)).isEmpty())
    }

    @Test fun `일정 화면에서 숨긴 협업은 미러에서도 숨긴다`() {
        val r = map(
            site(shareId = "hide-me", addr = "서울 강동구 숨긴현장 1"),
            site(shareId = "keep", addr = "서울 송파구 남길현장 2"),
            hidden = setOf("hide-me")
        )
        assertEquals(1, r.size)
        assertTrue("숨기지 않은 현장이 남아야 함", r[0].address!!.contains("남길현장"))
    }

    // ── 날짜/시각 ──

    @Test fun `날짜는 yyyy-MM-dd - 시각이 있어도 그날 자정 기준`() {
        val r = map(site(scheduledAtMs = at(2026, 7, 20, 9, 30)))
        assertEquals("2026-07-20", r[0].date)
        assertEquals("09:30", r[0].time)
    }

    @Test fun `자정이면 시각 미설정 - 서버 timeLabel 로 폴백`() {
        val r = map(site(scheduledAtMs = at(2026, 7, 20, 0, 0), timeLabel = "08:00"))
        assertEquals("08:00", r[0].time)
    }

    @Test fun `자정이고 timeLabel 도 없거나 0시면 시각 없음`() {
        assertNull(map(site(scheduledAtMs = at(2026, 7, 20), timeLabel = null))[0].time)
        assertNull(map(site(scheduledAtMs = at(2026, 7, 20), timeLabel = "00:00"))[0].time)
        assertNull(map(site(scheduledAtMs = at(2026, 7, 20), timeLabel = "0:00"))[0].time)
    }

    @Test fun `날짜는 그날의 시작으로 정규화된다`() {
        val ms = at(2026, 7, 20, 23, 59)
        val r = map(site(scheduledAtMs = ms))
        assertEquals("2026-07-20", r[0].date)
        assertTrue(DateTimeUtils.startOfDay(ms) <= ms)
    }

    // ── 표시 ──

    @Test fun `완료 표시는 progress 가 COMPLETED 일 때만`() {
        assertTrue(map(site(progress = SharedSiteRepository.Progress.COMPLETED))[0].completed)
        assertEquals(false, map(site(progress = SharedSiteRepository.Progress.ARRIVED))[0].completed)
    }

    @Test fun `이름은 주소 라벨 우선 - 일정 화면과 같은 규칙`() {
        // siteDisplayName: 주소 라벨 > 진짜 현장명 > 주소 원문 > "협업 현장"
        val r = map(site(title = "협업 현장", addr = "서울 강동구 천호동 1-2"))
        assertTrue("주소가 있으면 '협업 현장' 이라는 맹탕 라벨이 그대로 나오면 안 됨", r[0].name != "협업 현장")
    }

    @Test fun `기간은 1일 고정 - 협업엔 기간 개념이 아직 없음`() {
        assertEquals(1, map(site())[0].days)
    }
}
