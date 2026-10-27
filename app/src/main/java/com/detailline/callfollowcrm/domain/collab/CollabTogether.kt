package com.detailline.callfollowcrm.domain.collab

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite

/**
 * 🤝 **협업 사장 한 명과 「함께한 현장」을 양방향으로 묶는 셈 — 여기 한 곳.** (2026-10-27 사장님)
 *
 * 왜:
 *   협업 사장(= 시공막내로 한 번이라도 같이 일한 사람)의 고객 상세는 **보통 고객 화면이 아니라**
 *   전화·메모·함께한 현장 중심의 전용 화면으로 간다(사장님 "협업사장은 보통 고객기능 하나도 안 쓴다").
 *   그 화면이 보여줄 「함께한 현장」은 **받은 일**(그 사장이 나한테 공유)과 **내가 준 일**(내가 그 사장한테 공유)
 *   **양쪽 다**여야 한다(사장님 "양방향 다").
 *
 * 규칙:
 *   · **수락된 것만** 센다 — [SharedSiteRepository.countedForHistory](= accepted & 날짜 있음). 협업 기록 세는 규칙과 같다.
 *   · 달 묶기·일당 합계는 [CollabPartnerGroups](= `ymOf` · 완료 건 일당 합)를 **재사용** — 여기서 새로 안 센다(§12).
 *   · 받은 일당 = 받은 현장 완료분 합, 준 일당 = 내가 준 현장 완료분 합 — 섞지 않는다.
 *
 * ⚠️ **판정도 여기서**([isPartner]) — 화면이 "이 사람 협업 사장인가"를 직접 날짜 비교로 판단하지 않는다.
 * ⚠️ **폰에서 눈으로 못 본다** — 실제 협업이 있어야 재현된다. 시험이 대신 본다(§12-E②, [CollabTogetherTest]).
 */
object CollabTogether {

    /** 한 현장의 방향 — 내가 받은 일(RECEIVED) · 내가 준 일(GIVEN). */
    enum class Dir { RECEIVED, GIVEN }

    /** 함께한 현장 한 줄 — 현장 + 방향. */
    data class Entry(val site: SharedSite, val dir: Dir)

    /** 한 달치 묶음 — [ym] 은 "yyyy-MM". */
    data class MonthEntries(val ym: String, val entries: List<Entry>)

    /** 이 협업 사장과 함께한 현장 전체 모습(수락된 것만). */
    data class View(
        val entries: List<Entry>,            // 받은 + 준, 최근이 위
        val receivedSites: List<SharedSite>, // 수락된 받은 현장 (정산서·그룹 만들 때)
        val givenSites: List<SharedSite>,    // 수락된 준 현장
        val total: Int,
        val receivedCount: Int,
        val givenCount: Int,
        val receivedWageMan: Int,            // 받은 일당 합(완료분, 만원)
        val givenWageMan: Int,               // 준 일당 합(완료분, 만원)
        val recentMs: Long
    ) {
        val isPartner: Boolean get() = total > 0
    }

    private fun accepted(sites: List<SharedSite>): List<SharedSite> =
        sites.filter { SharedSiteRepository.countedForHistory(it) }

    /** 받은/준 현장 → 하나로 묶은 [View]. 수락된 것만, 최근이 위. */
    fun build(received: List<SharedSite>, given: List<SharedSite>): View {
        val r = accepted(received)
        val g = accepted(given)
        val entries = (r.map { Entry(it, Dir.RECEIVED) } + g.map { Entry(it, Dir.GIVEN) })
            .sortedByDescending { it.site.scheduledAtMs }
        return View(
            entries = entries,
            receivedSites = r,
            givenSites = g,
            total = entries.size,
            receivedCount = r.size,
            givenCount = g.size,
            // 일당 합계는 CollabPartnerGroups.byMonth(= wageOf, 완료분만)로 — 두 벌 안 만든다.
            receivedWageMan = CollabPartnerGroups.byMonth(r).sumOf { it.wageSum },
            givenWageMan = CollabPartnerGroups.byMonth(g).sumOf { it.wageSum },
            recentMs = entries.firstOrNull()?.site?.scheduledAtMs ?: 0L
        )
    }

    /** 달별로 묶기(최신 달 위, 달 안도 최신 위) — 필터 적용된 [entries] 로도 부른다. */
    fun monthsOf(entries: List<Entry>): List<MonthEntries> =
        entries.groupBy { SharedSiteRepository.ymOf(it.site.scheduledAtMs) }
            .map { (ym, list) -> MonthEntries(ym, list.sortedByDescending { it.site.scheduledAtMs }) }
            .sortedByDescending { it.ym }

    /** 이 사람이 협업 사장인가 — 받은/준 수락 현장이 하나라도 있나. */
    fun isPartner(received: List<SharedSite>, given: List<SharedSite>): Boolean =
        accepted(received).isNotEmpty() || accepted(given).isNotEmpty()
}
