package com.detailline.callfollowcrm.domain.collab

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 🧾 일당 정산서 내용 — 완료 건만 · 합계 · 기간 · 빈 목록. (2026-10-17)
 *   그림은 폰에서만 그려지니(Canvas) **무엇을 적는지**는 여기서 본다.
 */
class SettlementStatementTest {

    private val KST = ZoneId.of("Asia/Seoul")
    private fun at(y: Int, m: Int, d: Int): Long =
        ZonedDateTime.of(y, m, d, 10, 0, 0, 0, KST).toInstant().toEpochMilli()

    private fun site(
        id: String, ms: Long, wage: Int? = 25, done: Boolean = true,
        addr: String? = "인천 연수구 컨벤시아대로 42번길 7, 더샵 센트럴시티 101동 1203호", title: String = "현장 $id"
    ) = SharedSite(
        shareId = id, ownerPhone = "010-2197-2496", ownerName = "해시줄눈", title = title, addr = addr,
        scheduledAtMs = ms, timeLabel = null, workSummary = null, dailyWage = wage, memo = null,
        status = "accepted",
        progress = if (done) SharedSiteRepository.Progress.COMPLETED else SharedSiteRepository.Progress.ASSIGNED,
        createdAtMs = ms - 86_400_000L
    )

    private fun doc(sites: List<SharedSite>) =
        SettlementStatement.of("해시줄눈", sites, " 디테일라인 ", "홍길동", "010-1234-5678", at(2026, 10, 17))

    @Test
    fun `완료 + 일당 있는 건만 들어간다`() {
        val d = doc(listOf(
            site("a", at(2026, 10, 3)),                      // 완료 25만 ✔
            site("b", at(2026, 10, 5), done = false),         // 예정 ✘
            site("c", at(2026, 10, 7), wage = null),          // 완료지만 일당 없음 ✘
            site("d", at(2026, 9, 20), wage = 30)             // 완료 30만 ✔
        ))
        assertEquals(2, d.count)
        assertEquals(550_000L, d.totalWon)
    }

    @Test
    fun `합계는 업체별 화면의 받은 일당(CollabPartnerGroups)과 같은 규칙`() {
        val sites = listOf(
            site("a", at(2026, 10, 3)),
            site("b", at(2026, 10, 5), done = false),
            site("c", at(2026, 10, 7), wage = null),
            site("d", at(2026, 9, 20), wage = 30)
        )
        val wageMan = CollabPartnerGroups.group(sites).single().wageSum
        assertEquals(wageMan * 10_000L, doc(sites).totalWon)
    }

    @Test
    fun `날짜 오름차순 - 서류는 위에서 아래로 읽는다`() {
        val d = doc(listOf(
            site("a", at(2026, 10, 14)),
            site("b", at(2026, 9, 3)),
            site("c", at(2026, 10, 1))
        ))
        assertEquals(listOf(at(2026, 9, 3), at(2026, 10, 1), at(2026, 10, 14)), d.lines.map { it.dateMs })
        assertEquals(at(2026, 9, 3), d.fromMs)
        assertEquals(at(2026, 10, 14), d.toMs)
    }

    @Test
    fun `현장 글자는 업체 내역 줄과 같다 - 단지명 한 줄 + 도로명·동호수`() {
        val d = doc(listOf(site("a", at(2026, 10, 3))))
        assertEquals("더샵 센트럴시티", d.lines[0].site)
        assertEquals("인천 연수구 컨벤시아대로 42번길 7 · 101동 1203호", d.lines[0].siteSub)
        assertEquals(250_000L, d.lines[0].wageWon)
    }

    @Test
    fun `주소가 없으면 현장 표시명으로 내려간다`() {
        val d = doc(listOf(site("a", at(2026, 10, 3), addr = null, title = "강동 천호동 현장")))
        assertEquals("강동 천호동 현장", d.lines[0].site)
        assertNull(d.lines[0].siteSub)
    }

    @Test
    fun `빈 목록 - 줄 0, 합계 0, 기간 null`() {
        val d = doc(listOf(site("b", at(2026, 10, 5), done = false)))
        assertTrue(d.isEmpty)
        assertEquals(0, d.count)
        assertEquals(0L, d.totalWon)
        assertNull(d.fromMs)
        assertNull(d.toMs)
    }

    // ── 장 나누기 (2026-10-17 "A4처럼") ─────────────────────────

    private fun lines(n: Int) = (1..n).map { i ->
        SettlementStatement.Line(dateMs = at(2026, 9, 1) + i * 86_400_000L, site = "현장 $i", siteSub = null, wageWon = 250_000L)
    }

    @Test
    fun `18줄은 한 장`() {
        val p = SettlementStatement.paginate(lines(18))
        assertEquals(1, p.size)
        assertEquals(18, p[0].lines.size)
        assertEquals(1, p[0].index); assertEquals(1, p[0].total)
        assertTrue("한 장이면 그 장이 마지막 — 합계가 들어간다", p[0].isLast)
    }

    @Test
    fun `19줄은 두 장 - 둘째 장에 1줄, 합계는 둘째 장에만`() {
        val p = SettlementStatement.paginate(lines(19))
        assertEquals(2, p.size)
        assertEquals(listOf(18, 1), p.map { it.lines.size })
        assertEquals(listOf(1, 2), p.map { it.index })
        assertEquals(listOf(2, 2), p.map { it.total })
        assertEquals(listOf(false, true), p.map { it.isLast })
        assertEquals("현장 19", p[1].lines.single().site)
    }

    @Test
    fun `36줄은 딱 두 장 - 빈 셋째 장이 생기지 않는다`() {
        val p = SettlementStatement.paginate(lines(36))
        assertEquals(2, p.size)
        assertEquals(listOf(18, 18), p.map { it.lines.size })
        assertTrue(p.last().isLast)
    }

    @Test
    fun `빈 목록도 한 장 - 빈 서류지만 서류다`() {
        val p = SettlementStatement.paginate(emptyList())
        assertEquals(1, p.size)
        assertTrue(p[0].lines.isEmpty())
        assertTrue(p[0].isLast)
        assertEquals(1, p[0].total)
    }

    @Test
    fun `Doc의 pages는 paginate와 같은 규칙 - 줄 순서가 보존된다`() {
        val d = doc((1..20).map { i -> site("s$i", at(2026, 9, 1) + i * 86_400_000L) })
        val p = d.pages
        assertEquals(2, p.size)
        assertEquals(d.lines, p.flatMap { it.lines })
    }

    @Test
    fun `장 크기를 바꾸면 그대로 따라간다`() {
        val p = SettlementStatement.paginate(lines(7), perPage = 3)
        assertEquals(listOf(3, 3, 1), p.map { it.lines.size })
        assertEquals(3, p.last().total)
    }

    @Test
    fun `발행인 - 상호는 trim, 대표자·번호 그대로`() {
        val d = doc(emptyList())
        assertEquals("디테일라인", d.issuerName)
        assertEquals("홍길동", d.issuerOwner)
        assertEquals("010-1234-5678", d.issuerPhone)
        assertEquals("해시줄눈", d.partnerName)
    }
}
