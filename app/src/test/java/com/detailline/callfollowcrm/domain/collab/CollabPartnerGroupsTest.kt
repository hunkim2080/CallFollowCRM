package com.detailline.callfollowcrm.domain.collab

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 🤝 **「업체별이 한 줄로 뭉치는 것」을 막는 시험.** (2026-10-16)
 *
 *   폰 실측: 해시줄눈·하우스픽·태그줄눈이 전부 「사장님」 한 줄(현장 32곳·325만원)로 나왔다.
 *   여러 사장님이 실제로 공유를 보내야 재현되는 일이라 시험이 대신 본다.
 */
class CollabPartnerGroupsTest {

    private val KST = ZoneId.of("Asia/Seoul")
    private fun at(y: Int, m: Int, d: Int, h: Int = 10): Long =
        ZonedDateTime.of(y, m, d, h, 0, 0, 0, KST).toInstant().toEpochMilli()

    private fun site(
        id: String, phone: String, name: String, ms: Long,
        wage: Int? = 10, done: Boolean = true
    ) = SharedSite(
        shareId = id, ownerPhone = phone, ownerName = name, title = "현장 $id", addr = null,
        scheduledAtMs = ms, timeLabel = null, workSummary = null, dailyWage = wage, memo = null,
        status = "accepted",
        progress = if (done) SharedSiteRepository.Progress.COMPLETED else SharedSiteRepository.Progress.ASSIGNED,
        createdAtMs = ms - 86_400_000L
    )

    private val 해시 = "010-2197-2496"
    private val 하우스픽 = "01080052080"

    // ── 업체 가르기 ─────────────────────────────────────────────

    @Test
    fun `번호가 다르면 다른 업체`() {
        val g = CollabPartnerGroups.group(listOf(
            site("a", 해시, "해시줄눈", at(2026, 10, 5)),
            site("b", 하우스픽, "하우스픽", at(2026, 10, 3)),
            site("c", 해시, "해시줄눈", at(2026, 9, 20))
        ))
        assertEquals(listOf("해시줄눈", "하우스픽"), g.map { it.name })   // 최근 현장이 위
        assertEquals(listOf(2, 1), g.map { it.count })
    }

    @Test
    fun `같은 번호라도 이름이 다르면 다른 업체 - 사장님 폰에서 한 줄로 뭉친 사고`() {
        val g = CollabPartnerGroups.group(listOf(
            site("a", 해시, "해시줄눈", at(2026, 10, 5)),
            site("b", 해시, "하우스픽", at(2026, 10, 3)),
            site("c", 해시, "태그줄눈", at(2026, 9, 20))
        ))
        assertEquals(listOf("해시줄눈", "하우스픽", "태그줄눈"), g.map { it.name })
        assertTrue("열쇠도 서로 달라야 드릴다운이 따로 열린다", g.map { it.key }.toSet().size == 3)
    }

    @Test
    fun `이름이 비면 번호 뒤 4자리로 구분한다`() {
        val g = CollabPartnerGroups.group(listOf(
            site("a", 해시, "", at(2026, 10, 5)),
            site("b", 하우스픽, "사장님", at(2026, 10, 3))   // 서버가 못 채운 빈 이름
        ))
        assertEquals(listOf("사장님(···2496)", "사장님(···2080)"), g.map { it.name })
        assertEquals("사장님", CollabPartnerGroups.displayName("", ""))
    }

    @Test
    fun `번호 모양이 달라도 같은 업체 - 하이픈과 숫자만`() {
        val g = CollabPartnerGroups.group(listOf(
            site("a", "010-2197-2496", "해시줄눈", at(2026, 10, 5)),
            site("b", "01021972496", "해시줄눈", at(2026, 9, 5))
        ))
        assertEquals(1, g.size)
        assertEquals(2, g.first().count)
    }

    @Test
    fun `받은 일당은 완료된 현장만 더한다`() {
        val g = CollabPartnerGroups.group(listOf(
            site("a", 해시, "해시줄눈", at(2026, 10, 5), wage = 15, done = true),
            site("b", 해시, "해시줄눈", at(2026, 10, 6), wage = 20, done = false),
            site("c", 해시, "해시줄눈", at(2026, 10, 7), wage = null, done = true)
        ))
        assertEquals(15, g.first().wageSum)
    }

    // ── 서버 §B 합계 보강 ───────────────────────────────────────

    private fun partner(phone: String, name: String, count: Int, wage: Int, lastMs: Long) =
        SharedSiteRepository.Partner(phone, name, count, wage, 0, lastMs)

    @Test
    fun `서버 합계는 번호가 한 업체에만 맞을 때 그 업체를 올린다`() {
        val local = CollabPartnerGroups.group(listOf(site("a", 해시, "해시줄눈", at(2026, 10, 5), wage = 10)))
        val merged = CollabPartnerGroups.merge(local, listOf(partner(해시, "사장님", 32, 325, at(2026, 10, 5))))
        assertEquals(1, merged.size)
        assertEquals("해시줄눈", merged.first().name)      // 이름은 로컬(공유에 적힌 상호)이 이긴다
        assertEquals(32, merged.first().count)            // 합계는 서버(옛 이력 포함)
        assertEquals(325, merged.first().wageSum)
        assertEquals(1, merged.first().sites.size)        // 열 수 있는 현장은 로컬 것
    }

    @Test
    fun `한 번호가 로컬 여러 업체면 서버 합계로 뭉치지 않는다`() {
        val local = CollabPartnerGroups.group(listOf(
            site("a", 해시, "해시줄눈", at(2026, 10, 5)),
            site("b", 해시, "하우스픽", at(2026, 10, 3))
        ))
        val merged = CollabPartnerGroups.merge(local, listOf(partner(해시, "사장님", 32, 325, at(2026, 10, 5))))
        assertEquals(listOf("해시줄눈", "하우스픽"), merged.map { it.name })
        assertEquals(listOf(1, 1), merged.map { it.count })
    }

    @Test
    fun `로컬에 없는 번호는 옛 이력 업체로 더한다 - 열 현장은 없음`() {
        val local = CollabPartnerGroups.group(listOf(site("a", 해시, "해시줄눈", at(2026, 10, 5))))
        val merged = CollabPartnerGroups.merge(local, listOf(partner("01099990000", "", 3, 30, at(2025, 12, 1))))
        assertEquals(2, merged.size)
        val old = merged.last()
        assertEquals("사장님(···0000)", old.name)
        assertEquals(3, old.count)
        assertTrue(old.sites.isEmpty())
    }

    @Test
    fun `서버가 비면 로컬 그대로`() {
        val local = CollabPartnerGroups.group(listOf(site("a", 해시, "해시줄눈", at(2026, 10, 5))))
        assertEquals(local, CollabPartnerGroups.merge(local, emptyList()))
    }

    // ── 달별 드릴다운 ───────────────────────────────────────────

    @Test
    fun `한 업체 안은 달별로 갈리고 최신 달이 위`() {
        val months = CollabPartnerGroups.byMonth(listOf(
            site("a", 해시, "해시줄눈", at(2026, 9, 20), wage = 10),
            site("b", 해시, "해시줄눈", at(2026, 10, 3), wage = 15),
            site("c", 해시, "해시줄눈", at(2026, 10, 12), wage = 20, done = false),
            site("d", 해시, "해시줄눈", at(2026, 8, 1), wage = 5)
        ))
        assertEquals(listOf("2026-10", "2026-09", "2026-08"), months.map { it.ym })
        assertEquals(listOf(2, 1, 1), months.map { it.count })
        assertEquals(listOf(15, 10, 5), months.map { it.wageSum })        // 완료된 것만
        assertEquals(listOf("c", "b"), months.first().sites.map { it.shareId })   // 달 안도 최신이 위
    }

    // ── 내가 공유한 현장 → 내가 부른 사장님별 (이름만 있다) ─────────

    private fun mine(id: String, partner: String?, ms: Long, wage: Int? = 10, done: Boolean = true, status: String = "accepted") =
        SharedSite(
            shareId = id, ownerPhone = "010-0000-0000", ownerName = "나", partnerName = partner, title = "현장 $id", addr = null,
            scheduledAtMs = ms, timeLabel = null, workSummary = null, dailyWage = wage, memo = null,
            status = status,
            progress = if (done) SharedSiteRepository.Progress.COMPLETED else SharedSiteRepository.Progress.ASSIGNED,
            createdAtMs = ms - 86_400_000L
        )

    @Test
    fun `내가 공유한 현장은 상대 상호로 묶인다 - 번호가 없어도`() {
        val g = CollabPartnerGroups.groupByPartner(listOf(
            mine("a", "해시줄눈", at(2026, 10, 5)),
            mine("b", " 하우스픽 ", at(2026, 10, 3)),
            mine("c", "해시줄눈", at(2026, 9, 20))
        ))
        assertEquals(listOf("해시줄눈", "하우스픽"), g.map { it.name })   // 최근 현장이 위, 이름은 trim
        assertEquals(listOf(2, 1), g.map { it.count })
        assertEquals(listOf(false, false), g.map { it.pending })
        assertTrue("열쇠가 달라야 드릴다운이 따로 열린다", g.map { it.key }.toSet().size == 2)
    }

    @Test
    fun `수락 전(상호 없음)은 「수락 대기」 한 묶음으로 맨 위`() {
        val g = CollabPartnerGroups.groupByPartner(listOf(
            mine("a", "해시줄눈", at(2026, 10, 20)),
            mine("b", null, at(2026, 10, 3), status = "pending"),
            mine("c", "", at(2026, 9, 1), status = "pending")
        ))
        assertEquals(listOf(CollabPartnerGroups.PENDING, "해시줄눈"), g.map { it.name })
        assertTrue(g.first().pending)
        assertEquals(2, g.first().count)
        assertEquals(listOf("b", "c"), g.first().sites.map { it.shareId })   // 묶음 안도 최신이 위
    }

    @Test
    fun `최근 날짜와 현장 수 - 시공일과 만든 날 중 큰 쪽`() {
        val g = CollabPartnerGroups.groupByPartner(listOf(
            mine("a", "해시줄눈", at(2026, 10, 5)),
            mine("b", "해시줄눈", at(2026, 10, 14), done = false)
        ))
        assertEquals(1, g.size)
        assertEquals(2, g.single().count)
        assertEquals(at(2026, 10, 14), g.single().recentMs)
        assertEquals(10, g.single().wageSum)   // 완료된 것만 — 화면은 이걸 「받은 일당」이라 적지 않는다
    }

    @Test
    fun `내가 공유한 현장이 없으면 빈 목록`() {
        assertTrue(CollabPartnerGroups.groupByPartner(emptyList()).isEmpty())
    }

    @Test
    fun `자정 근처는 서울 시간으로 달을 가른다`() {
        // 9/30 23:30 KST 는 UTC 로는 9/30 14:30 — 어느 쪽이든 9월. 10/1 00:30 KST 는 UTC 로 9/30 15:30 인데 10월이어야 한다.
        val months = CollabPartnerGroups.byMonth(listOf(
            site("a", 해시, "해시줄눈", at(2026, 9, 30, 23)),
            site("b", 해시, "해시줄눈", at(2026, 10, 1, 0))
        ))
        assertEquals(listOf("2026-10", "2026-09"), months.map { it.ym })
    }
}
