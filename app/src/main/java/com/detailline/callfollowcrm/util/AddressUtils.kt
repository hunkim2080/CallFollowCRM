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
