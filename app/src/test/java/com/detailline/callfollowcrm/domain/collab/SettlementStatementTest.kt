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
        assertNull(d.period)
        assertTrue(d.months.isEmpty())
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

    // ── 달 고르기 (2026-10-17 "어느 달 뽑을지") ─────────────────────

    private val 석달 = listOf(
        site("a", at(2026, 10, 3)),                    // 10월 완료 25
        site("b", at(2026, 10, 20), wage = 30),        // 10월 완료 30
        site("c", at(2026, 9, 14), wage = 40),         // 9월 완료 40
        site("d", at(2026, 9, 25), done = false),      // 9월 예정 ✘
        site("e", at(2026, 8, 2), wage = 20),          // 8월 완료 20
        site("f", at(2026, 7, 9), done = false),       // 7월 예정만 ✘ → 고를 수 없는 달
        site("g", at(2026, 6, 1), wage = null)         // 6월 완료지만 일당 없음 ✘ → 고를 수 없는 달
    )

    @Test
    fun `고를 수 있는 달 - 완료 건 있는 달만, 최신이 위, 숫자는 달 머리(byMonth)와 같다`() {
        val opts = SettlementStatement.pickableMonths(석달)
        assertEquals(listOf("2026-10", "2026-09", "2026-08"), opts.map { it.ym })
        assertEquals(listOf(2, 1, 1), opts.map { it.count })
        assertEquals(listOf(55, 40, 20), opts.map { it.wageMan })
        val byMonth = CollabPartnerGroups.byMonth(석달).associateBy { it.ym }
        opts.forEach { assertEquals("달 머리의 받은 일당과 같은 규칙", byMonth.getValue(it.ym).wageSum, it.wageMan) }
        assertEquals("2026년 10월", opts[0].label)
    }

    @Test
    fun `기본 선택은 가장 최근 달 하나`() {
        assertEquals(setOf("2026-10"), SettlementStatement.defaultPick(SettlementStatement.pickableMonths(석달)))
        assertEquals(emptySet<String>(), SettlementStatement.defaultPick(emptyList()))
    }

    @Test
    fun `한 달만 고르면 그 달 완료 건만 - 기간은 「2026년 10월」`() {
        val d = SettlementStatement.of("해시줄눈", 석달, "디테일라인", "", "", at(2026, 10, 17), months = setOf("2026-10"))
        assertEquals(2, d.count)
        assertEquals(550_000L, d.totalWon)
        assertEquals(listOf("2026-10"), d.months)
        assertEquals("2026년 10월", d.period)
        assertEquals("202610", d.periodTag)
    }

    @Test
    fun `여러 달 고르면 그 달들만 - 기간은 min~max 양 끝 달`() {
        val d = SettlementStatement.of("해시줄눈", 석달, "디테일라인", "", "", at(2026, 10, 17), months = setOf("2026-10", "2026-08"))
        assertEquals(3, d.count)                                      // 9월은 안 골랐으니 빠진다
        assertEquals(750_000L, d.totalWon)
        assertEquals(listOf(at(2026, 8, 2), at(2026, 10, 3), at(2026, 10, 20)), d.lines.map { it.dateMs })
        assertEquals("2026.08 – 2026.10", d.period)                   // 사이 달이 비어도 양 끝만
        assertEquals("202608-202610", d.periodTag)
    }

    @Test
    fun `달을 안 주면(null) 전체 - 기간은 줄이 있는 달들로`() {
        val d = SettlementStatement.of("해시줄눈", 석달, "디테일라인", "", "", at(2026, 10, 17))
        assertEquals(4, d.count)
        assertEquals(listOf("2026-08", "2026-09", "2026-10"), d.months)   // 7월·6월은 완료 건이 없어 기간에 안 들어간다
        assertEquals("2026.08 – 2026.10", d.period)
    }

    @Test
    fun `빈 선택이면 줄 0 - 기간 null, 파일 꼬리 null`() {
        val d = SettlementStatement.of("해시줄눈", 석달, "디테일라인", "", "", at(2026, 10, 17), months = emptySet())
        assertTrue(d.isEmpty)
        assertEquals(0L, d.totalWon)
        assertNull(d.period)
        assertNull(d.periodTag)
    }

    @Test
    fun `기간 글자 - 한 달·여러 달·연 걸침·빈 값`() {
        assertEquals("2026년 10월", SettlementStatement.periodLabel(setOf("2026-10")))
        assertEquals("2026.08 – 2026.10", SettlementStatement.periodLabel(listOf("2026-10", "2026-08", "2026-09")))
        assertEquals("2025.12 – 2026.01", SettlementStatement.periodLabel(setOf("2026-01", "2025-12")))
        assertNull(SettlementStatement.periodLabel(emptySet()))
        assertNull(SettlementStatement.periodLabel(listOf("", " ")))
        assertEquals("202512-202601", SettlementStatement.periodFileTag(setOf("2026-01", "2025-12")))
        assertEquals("202601", SettlementStatement.periodFileTag(setOf("2026-01")))
    }

    @Test
    fun `달 이름은 ymOf와 같은 자리 SharedSiteRepository - 한 벌`() {
        assertEquals("2026년 7월", SharedSiteRepository.monthLabel("2026-07"))
        assertEquals("", SharedSiteRepository.monthLabel(""))
        assertEquals("", SharedSiteRepository.monthLabel("2026-xx"))
        assertEquals(SharedSiteRepository.monthLabel(SharedSiteRepository.ymOf(at(2026, 10, 3))), "2026년 10월")
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
