package com.detailline.callfollowcrm.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🤝 **같은 현장이 두 벌로 오면 한 벌로.** (2026-09-30 사장님)
 *
 *   "상률로32 이현장 왜2개로 표시되지"
 *
 * 실제로 있었던 일 — 하우스픽 사장님이 같은 현장을 9/25 와 9/30 **두 번** 보냈고,
 * 두 번째 건엔 일당도 시간도 없었다. 그래서 일정에 「오후 2시 · 25만」과
 * 「00:00 · 금액 없음」이 **나란히** 떴다.
 *
 * 이 확인이 지키는 것 —
 *   ① 두 벌이면 **한 벌만** 남는다
 *   ② 남는 쪽은 **내용이 더 찬 쪽** (그냥 최신을 남기면 25만을 잃는다)
 *   ③ **진짜 다른 현장은 안 사라진다** ← 이게 제일 중요하다.
 *      합치다가 남의 현장을 지우면 그날 못 간다.
 */
class SharedSiteMergeTest {

    private val repo = SharedSiteRepository()

    private fun site(
        id: String,
        owner: String = "01064610131",
        addr: String? = "경기 수원시 장안구 상률로 32 103/1103",
        at: Long = 1790694000000L,
        wage: Int? = null,
        time: String? = null,
        summary: String? = null
    ) = SharedSiteRepository.SharedSite(
        shareId = id,
        ownerPhone = owner,
        ownerName = "줄눈시공 탄성코트의 시작, 하우스픽",
        partnerName = "디테일라인",
        title = "경기 수원시 장안구 상률로 32 103/1103 현장",
        addr = addr,
        scheduledAtMs = at,
        timeLabel = time,
        workSummary = summary,
        dailyWage = wage,
        memo = null,
        status = "accepted",
        progress = SharedSiteRepository.Progress.ASSIGNED,
        createdAtMs = 0L
    )

    @Test
    fun `같은 현장 두 벌이면 한 벌만 남는다`() {
        val full = site("sh_AWkLwXtY34", wage = 25, time = "오후 2시")
        val empty = site("sh_rgLqoFag2T")   // 나중에 온 것 — 일당·시간 비어 있음
        val merged = repo.mergeSameSite(listOf(full, empty))
        assertEquals("두 벌이 한 벌로 합쳐져야 한다", 1, merged.size)
    }

    @Test
    fun `남는 쪽은 내용이 더 찬 쪽 — 나중 것이 비었다고 25만을 잃으면 안 된다`() {
        val full = site("sh_AWkLwXtY34", wage = 25, time = "오후 2시")
        val empty = site("sh_rgLqoFag2T")
        // 순서를 바꿔도 결과가 같아야 한다 — 서버가 어떤 순서로 주든.
        for (list in listOf(listOf(full, empty), listOf(empty, full))) {
            val merged = repo.mergeSameSite(list)
            assertEquals(1, merged.size)
            assertEquals("일당이 살아남아야 한다", 25, merged[0].dailyWage)
            assertEquals("시간도 살아남아야 한다", "오후 2시", merged[0].timeLabel)
        }
    }

    @Test
    fun `시각이 다르면 다른 건이다 — 오전 오후 두 번 가는 날을 합치면 안 된다`() {
        val morning = site("sh_a", at = 1790694000000L, time = "오전 9시")
        val afternoon = site("sh_b", at = 1790694000000L + 5 * 3600_000L, time = "오후 2시")
        val merged = repo.mergeSameSite(listOf(morning, afternoon))
        assertEquals("둘 다 남아야 한다", 2, merged.size)
    }

    @Test
    fun `주소가 다르면 다른 현장이다`() {
        val a = site("sh_a", addr = "경기 수원시 장안구 상률로 32 103/1103")
        val b = site("sh_b", addr = "서울 서초구 서초대로 50 122동 2201호")
        assertEquals(2, repo.mergeSameSite(listOf(a, b)).size)
    }

    @Test
    fun `부른 사장이 다르면 다른 현장이다 — 같은 주소라도`() {
        val a = site("sh_a", owner = "01064610131")
        val b = site("sh_b", owner = "01047262496")
        assertEquals(2, repo.mergeSameSite(listOf(a, b)).size)
    }

    @Test
    fun `주소가 없는 현장은 절대 합치지 않는다 — 합치면 남의 현장이 사라진다`() {
        val a = site("sh_a", addr = null)
        val b = site("sh_b", addr = null)
        val c = site("sh_c", addr = "")
        val merged = repo.mergeSameSite(listOf(a, b, c))
        assertEquals("주소 없는 것끼리는 손대지 않는다", 3, merged.size)
    }

    @Test
    fun `날짜가 없는 현장도 합치지 않는다`() {
        val a = site("sh_a", at = 0L)
        val b = site("sh_b", at = 0L)
        assertEquals(2, repo.mergeSameSite(listOf(a, b)).size)
    }

    @Test
    fun `띄어쓰기만 다른 주소는 같은 현장으로 본다`() {
        val a = site("sh_a", addr = "경기 수원시 장안구 상률로 32 103/1103", wage = 25)
        val b = site("sh_b", addr = "경기수원시 장안구상률로 32 103/1103")
        val merged = repo.mergeSameSite(listOf(a, b))
        assertEquals(1, merged.size)
        assertEquals(25, merged[0].dailyWage)
    }

    @Test
    fun `한 벌이면 그대로 둔다`() {
        val one = listOf(site("sh_a"))
        assertEquals(one, repo.mergeSameSite(one))
        assertTrue(repo.mergeSameSite(emptyList()).isEmpty())
    }

    @Test
    fun `합쳐도 그날 갈 곳은 하나도 안 없어진다`() {
        // 사장님 9월 30일 화면 그대로 — 상률로 두 벌 + 서초대로 한 벌.
        val list = listOf(
            site("sh_rgLqoFag2T"),
            site("sh_AWkLwXtY34", wage = 25, time = "오후 2시"),
            site("sh_seocho", owner = "01080056674",
                addr = "서울 서초구 서초대로 50 122동 2201호", wage = 220)
        )
        val merged = repo.mergeSameSite(list)
        assertEquals("두 곳이어야 한다", 2, merged.size)
        assertNotNull("상률로가 남아야 한다",
            merged.firstOrNull { it.addr?.contains("상률로") == true })
        assertNotNull("서초대로가 남아야 한다",
            merged.firstOrNull { it.addr?.contains("서초대로") == true })
    }
}
