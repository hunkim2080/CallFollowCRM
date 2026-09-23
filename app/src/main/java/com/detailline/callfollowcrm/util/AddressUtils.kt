package com.detailline.callfollowcrm.util

/**
 * 저장된 한 줄 주소를 도로명(base) + 동·호수(detail)로 분리. (CustomerDetail / ScheduleAdd 공용)
 *   동·호수는 보통 끝에 "(반)지하?숫자+동/호/층"으로 붙는다(아파트 동은 숫자+동, 법정동은 숫자 없음 → 오인 X).
 *   첫 위치에서 자른다. 못 찾으면 전체가 도로명(detail 없음).
 *   한계: 숫자 없는 "A동/B동"은 분리 못 함(드묾).
 */
fun splitSiteAddress(full: String): Pair<String, String> {
    val s = full.trim()
    if (s.isEmpty()) return "" to ""
    val m = Regex("""(반?지하\s*)?\d+\s*(동|호|층)""").find(s) ?: return s to ""
    val idx = m.range.first
    if (idx <= 0) return s to ""
    val base = s.substring(0, idx).trim()
    val detail = s.substring(idx).trim()
    return if (base.isEmpty()) s to "" else base to detail
}

/** 동·호수 한 칸이 "동"/"호" 두 칸으로 갈라질 수 있는 모양인가. */
private val UNIT_TOKEN = Regex("""^[0-9A-Za-z가-힣]{1,8}$""")

/**
 * 저장된 동·호수 한 줄 → (동, 호). (2026-09-23 사장님 "동호수도 통일되서 들어올 수 있게")
 *
 * 빈 칸 하나로 받으면 "103동 1103호" / "103-1103" / "103/1103" / "1103" 이 제각각 들어온다.
 * 두 칸으로 갈라 받으면 저장이 늘 한 모양이 되고, 나중에 같은 아파트·같은 동끼리 묶어볼 수 있다.
 *
 * ⚠️ **못 알아본 글자는 버리지 않는다** — 이미 저장된 옛 주소를 열었을 때 내용이 사라지면 안 된다.
 *    해석 못 하면 통째로 '호' 칸에 넣고, [[joinDongHo]] 가 그대로 돌려준다.
 */
fun splitDongHo(detail: String): Pair<String, String> {
    val s = detail.trim()
    if (s.isEmpty()) return "" to ""
    Regex("""^(\S+?)\s*동\s*(\S+?)\s*호?$""").find(s)?.let {
        return it.groupValues[1] to it.groupValues[2]
    }
    Regex("""^(\S+?)\s*호$""").find(s)?.let { return "" to it.groupValues[1] }
    Regex("""^(\S+?)\s*동$""").find(s)?.let { return it.groupValues[1] to "" }
    Regex("""^([0-9A-Za-z가-힣]+)\s*[-/]\s*([0-9A-Za-z가-힣]+)$""").find(s)?.let {
        return it.groupValues[1] to it.groupValues[2]
    }
    if (UNIT_TOKEN.matches(s)) return "" to s      // 숫자만 오면 호로 본다 (빌라·단독)
    return "" to s                                  // 못 알아봄 — 원문 보존
}

/**
 * (동, 호) → 저장할 한 줄. 늘 "103동 1103호" 모양. (2026-09-23)
 *
 * 동이 없는 빌라·단독은 "1103호". 둘 다 비면 빈 문자열.
 * 호 칸이 동호수처럼 안 생겼으면(옛 자유입력) **'호'를 붙이지 않고 그대로** 돌려준다.
 */
fun joinDongHo(dong: String, ho: String): String {
    val d = dong.trim().trimEnd('동').trim()
    val h = ho.trim().trimEnd('호').trim()
    if (d.isEmpty() && h.isEmpty()) return ""
    if (h.isNotEmpty() && !UNIT_TOKEN.matches(h)) {
        return if (d.isEmpty()) h else "${d}동 $h"
    }
    return listOfNotNull(
        d.takeIf { it.isNotEmpty() }?.let { "${it}동" },
        h.takeIf { it.isNotEmpty() }?.let { "${it}호" },
    ).joinToString(" ")
}
