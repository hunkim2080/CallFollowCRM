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
 *   · **달을 골라 뽑는다**(2026-10-17 사장님 "어느 달 뽑을지") — [of] 에 `months`(yyyy-MM 집합)를 주면 그 달의 완료 건만.
 *     고를 수 있는 달은 [pickableMonths] — `CollabPartnerGroups.byMonth` 위에서 **완료 건이 있는 달만**(합계도 그 규칙 그대로).
 *   · 기간 = **고른 달 기준**([periodLabel]) — 한 달이면 「2026년 10월」, 여러 달이면 「2026.08 – 2026.10」(min~max).
 *     달을 안 고르면(전체) 줄이 있는 달들로 같은 규칙. 줄이 없으면 null. (전엔 완료건 최초~최종 **날짜**였다 — 이제 달 단위로 한 곳)
 *   · **여러 장**(2026-10-17 사장님 "A4처럼 페이지 나누기") — 줄이 [ROWS_PER_PAGE] 를 넘으면 [paginate] 가 장으로 가른다.
 *     머리(업체명·기간·상호)는 매 장 반복, **합계는 마지막 장에만**([Page.isLast]). 세무·거래처에 보내는 서류라 장마다 「i / N」.
 */
object SettlementStatement {

    private const val MAN_WON = 10_000L

    /** 고를 수 있는 달 한 줄 — 「2026년 10월  2곳 · 50만원」. [count] 는 서류에 들어갈 건 수([eligible]), [wageMan] 은 그 달 완료 일당 합(만원). */
    data class MonthOption(val ym: String, val count: Int, val wageMan: Int) {
        val label: String get() = SharedSiteRepository.monthLabel(ym)
    }

    /**
     * 「어느 달 뽑을까요?」 목록 — **완료(일당 있는) 건이 있는 달만**, 최신 달이 위.
     * 달 묶기·합계는 [CollabPartnerGroups.byMonth] 그대로(새로 안 센다) — 업체 내역 달 머리의 「받은 일당 N만원」과 같은 숫자.
     */
    fun pickableMonths(sites: List<SharedSite>): List<MonthOption> =
        CollabPartnerGroups.byMonth(sites)
            .map { m -> MonthOption(ym = m.ym, count = eligible(m.sites).size, wageMan = m.wageSum) }
            .filter { it.count > 0 }

    /** 기본 선택 = 가장 최근 달 하나. 달이 없으면 빈 집합. */
    fun defaultPick(options: List<MonthOption>): Set<String> =
        options.maxOfOrNull { it.ym }?.let { setOf(it) } ?: emptySet()

    /**
     * 기간 글자 — 고른 달 기준. 한 달 「2026년 10월」, 여러 달 「2026.08 – 2026.10」(min~max, 사이 달이 비어도 양 끝만). 비면 null.
     * 연을 걸치면 「2025.12 – 2026.01」.
     */
    fun periodLabel(yms: Collection<String>): String? {
        val sorted = yms.filter { it.isNotBlank() }.toSortedSet()
        if (sorted.isEmpty()) return null
        if (sorted.size == 1) return SharedSiteRepository.monthLabel(sorted.first())
        return dotted(sorted.first()) + " – " + dotted(sorted.last())
    }

    /** 파일 이름 꼬리 — 「202610」 / 「202608-202610」. 비면 null(부르는 쪽이 발행일로 대신). */
    fun periodFileTag(yms: Collection<String>): String? {
        val sorted = yms.filter { it.isNotBlank() }.toSortedSet()
        if (sorted.isEmpty()) return null
        val a = sorted.first().replace("-", "")
        val b = sorted.last().replace("-", "")
        return if (a == b) a else "$a-$b"
    }

    private fun dotted(ym: String) = ym.replace("-", ".")

    /**
     * 한 장에 넣는 줄 수. 2줄짜리 줄(단지명+도로명) 104px × 18 = 1,872px + 머리·꼬리 ≈ 2,700px — 1080 폭에서 A4 비율(1:1.41)보다
     * 조금 길지만 폰 화면·카톡 미리보기에서 글자가 읽히는 선. ⚠️ 폰에서 실측 후 조정 가능(여기 숫자 하나만).
     */
    const val ROWS_PER_PAGE = 18

    /** 서류 한 장 — [index] 는 1부터. 줄이 하나도 없어도 한 장은 있다(빈 서류도 서류다). */
    data class Page(val index: Int, val total: Int, val lines: List<Line>) {
        val isLast: Boolean get() = index == total
    }

    /**
     * 줄을 장으로 가른다. 18줄 → 1장, 19줄 → 2장, 36줄 → 2장, 0줄 → 빈 1장.
     * 순수 함수 — 그림([SettlementShot])과 저장·공유(장마다 파일 하나)가 같은 장 나누기를 본다.
     */
    fun paginate(lines: List<Line>, perPage: Int = ROWS_PER_PAGE): List<Page> {
        require(perPage > 0) { "perPage must be > 0" }
        if (lines.isEmpty()) return listOf(Page(index = 1, total = 1, lines = emptyList()))
        val chunks = lines.chunked(perPage)
        return chunks.mapIndexed { i, chunk -> Page(index = i + 1, total = chunks.size, lines = chunk) }
    }

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
        val issuedAtMs: Long,
        /** 서류가 다루는 달(yyyy-MM, 오름차순) — 고른 달, 안 골랐으면 줄이 있는 달들. 기간 글자·파일 이름이 여기서 나온다. */
        val months: List<String> = emptyList()
    ) {
        val count: Int get() = lines.size
        val isEmpty: Boolean get() = lines.isEmpty()
        /** 장 나누기 — [paginate] 한 곳. */
        val pages: List<Page> get() = paginate(lines)
        /** 머리에 적는 기간 — [periodLabel] 한 곳. 비면 null(그리는 쪽이 「-」). */
        val period: String? get() = periodLabel(months)
        /** 파일 이름 꼬리 — [periodFileTag] 한 곳. */
        val periodTag: String? get() = periodFileTag(months)
    }

    /** 서류에 들어갈 현장만 — 완료 + 일당 있음. */
    fun eligible(sites: List<SharedSite>): List<SharedSite> =
        sites.filter { it.progress == SharedSiteRepository.Progress.COMPLETED && it.dailyWage != null }

    /**
     * @param months 뽑을 달(yyyy-MM) — null 이면 전체(옛 동작), 빈 집합이면 줄 0(아무 달도 안 고른 것).
     *   달은 [SharedSiteRepository.ymOf](서울 시간) 로 가른다 — 화면의 달 머리와 같은 경계.
     */
    fun of(
        partnerName: String,
        sites: List<SharedSite>,
        issuerName: String,
        issuerOwner: String,
        issuerPhone: String,
        issuedAtMs: Long,
        months: Set<String>? = null
    ): Doc {
        val picked = eligible(sites).filter { months == null || SharedSiteRepository.ymOf(it.scheduledAtMs) in months }
        val lines = picked
            .sortedBy { it.scheduledAtMs }
            .map { s ->
                val two = AddressExtractor.twoLine(s.addr)
                val (site, sub) = if (two.first.isNotBlank()) two else siteDisplayName(s) to null
                Line(dateMs = s.scheduledAtMs, site = site, siteSub = sub, wageWon = (s.dailyWage ?: 0) * MAN_WON)
            }
        // 기간은 고른 달 기준. 안 골랐으면 줄이 실제로 있는 달들(완료 건 없는 달은 기간에 안 들어간다).
        val docMonths = (months ?: picked.map { SharedSiteRepository.ymOf(it.scheduledAtMs) }.toSet()).toSortedSet().toList()
        return Doc(
            partnerName = partnerName,
            lines = lines,
            totalWon = lines.sumOf { it.wageWon },
            fromMs = lines.firstOrNull()?.dateMs,
            toMs = lines.lastOrNull()?.dateMs,
            months = docMonths,
            issuerName = issuerName.trim(),
            issuerOwner = issuerOwner.trim(),
            issuerPhone = issuerPhone.trim(),
            issuedAtMs = issuedAtMs
        )
    }
}
