package com.detailline.callfollowcrm.domain.intake

/**
 * 🔎 **유입경로 모아보기** — 접수서에 고객이 "어디서 보셨어요?" 로 답한 것을 **경로별로 센다.** (2026-10-23 사장님)
 *
 *   저장된 라벨 모양은 `"네이버 · 블로그 · '줄눈'"`(경로 · 세부) 또는 `"인스타"`(세부 없음).
 *   그래서 **첫 토막(" · " 앞) = 경로**, 나머지 = 세부. (IntakeFormRepository 가 만든 그 글자.)
 *
 *   "네이버가 제일 많네 → 광고 거기 더" 를 한눈에. 플랫폼을 누르면 세부(블로그·키워드)가 펼쳐진다.
 *   DB·안드로이드에 안 기댄다 — 단위 테스트가 전부 본다.
 */
data class InflowDetail(val label: String, val count: Int)

data class InflowGroup(
    /** 경로 이름 — 네이버/인스타/당근/구글/기타 등. */
    val source: String,
    /** 그 경로로 온 손님 수. */
    val count: Int,
    /** 그 경로 안 세부(블로그·키워드) — 많은 순. 세부 없는 응답은 안 들어간다. */
    val details: List<InflowDetail>,
)

object InflowStats {

    /** 접수서 유입경로 라벨 목록 → 경로별 집계(많은 순). 각 경로 안 세부도 집계. */
    fun aggregate(labels: List<String>): List<InflowGroup> =
        labels.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .groupBy { it.substringBefore(" · ").trim().ifEmpty { "기타" } }
            .map { (src, items) ->
                val details = items
                    .map { it.substringAfter(" · ", "").trim() }
                    .filter { it.isNotEmpty() }
                    .groupingBy { it }.eachCount()
                    .map { (d, c) -> InflowDetail(d, c) }
                    .sortedByDescending { it.count }
                InflowGroup(source = src, count = items.size, details = details)
            }
            .sortedByDescending { it.count }

    /** 전체 손님 수 — 카드 머리 "총 N명" 용. */
    fun total(groups: List<InflowGroup>): Int = groups.sumOf { it.count }
}
