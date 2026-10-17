package com.detailline.callfollowcrm.domain.collab

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite
import com.detailline.callfollowcrm.ai.siteDisplayName
import com.detailline.callfollowcrm.util.AddressExtractor

/**
 * 🧾 **일당 정산서에 들어갈 내용 — 셈은 여기 한 곳.** (2026-10-17 사장님 "정산서 이미지로 저장·공유")
 *
 *   협업 「업체 내역」 화면에서 그 업체와 한 **완료 현장**을 한 장의 서류로 만든다.
 *   그림을 그리는 건 `util/SettlementShot`, **무엇을 적을지**는 여기서 정한다(그림 코드에 셈을 섞지 않는다).
 *
 *   규칙:
 *   · 줄 = **완료된 현장(progress=COMPLETED) 중 일당이 적힌 것**만. 예정·지남·일당 없는 건은 서류에 안 들어간다.
 *     → 합계는 `CollabPartnerGroups.wageSum`(서버 §B total_wage) 과 **같은 규칙**이라 화면 숫자와 어긋나지 않는다.
 *   · 날짜 오름차순(서류는 위에서 아래로 읽는다 — 화면 목록은 최신이 위지만 서류는 반대).
 *   · 현장 = `AddressExtractor.twoLine`(단지명 / 도로명 · 동호수) — 업체 내역 줄과 **같은 글자**. 단지명이 없으면 `siteDisplayName`.
 *   · 돈은 **원 단위**(일당은 만원으로 들어오니 ×10,000). 콤마는 그리는 쪽에서 `MoneyFormatter` 로.
 *   · 기간 = 줄들의 최초~최종 날짜. 줄이 없으면 null.
 */
object SettlementStatement {

    private const val MAN_WON = 10_000L

    /** 서류 한 줄. */
    data class Line(
        val dateMs: Long,
        /** 1번째 줄 — 단지명(없으면 현장 표시명). */
        val site: String,
        /** 2번째 줄 — 도로명 · 동호수. 없으면 null. */
        val siteSub: String?,
        val wageWon: Long
    )

    /** 서류 전체. */
    data class Doc(
        val partnerName: String,
        val lines: List<Line>,
        val totalWon: Long,
        val fromMs: Long?,
        val toMs: Long?,
        /** 발행인 — 상호 · 대표자(없으면 빈 문자열) · 보여줄 번호. */
        val issuerName: String,
        val issuerOwner: String,
        val issuerPhone: String,
        val issuedAtMs: Long
    ) {
        val count: Int get() = lines.size
        val isEmpty: Boolean get() = lines.isEmpty()
    }

    /** 서류에 들어갈 현장만 — 완료 + 일당 있음. */
    fun eligible(sites: List<SharedSite>): List<SharedSite> =
        sites.filter { it.progress == SharedSiteRepository.Progress.COMPLETED && it.dailyWage != null }

    fun of(
        partnerName: String,
        sites: List<SharedSite>,
        issuerName: String,
        issuerOwner: String,
        issuerPhone: String,
        issuedAtMs: Long
    ): Doc {
        val lines = eligible(sites)
            .sortedBy { it.scheduledAtMs }
            .map { s ->
                val two = AddressExtractor.twoLine(s.addr)
                val (site, sub) = if (two.first.isNotBlank()) two else siteDisplayName(s) to null
                Line(dateMs = s.scheduledAtMs, site = site, siteSub = sub, wageWon = (s.dailyWage ?: 0) * MAN_WON)
            }
        return Doc(
            partnerName = partnerName,
            lines = lines,
            totalWon = lines.sumOf { it.wageWon },
            fromMs = lines.firstOrNull()?.dateMs,
            toMs = lines.lastOrNull()?.dateMs,
            issuerName = issuerName.trim(),
            issuerOwner = issuerOwner.trim(),
            issuerPhone = issuerPhone.trim(),
            issuedAtMs = issuedAtMs
        )
    }
}
