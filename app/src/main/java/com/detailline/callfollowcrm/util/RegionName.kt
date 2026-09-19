package com.detailline.callfollowcrm.util

/**
 * 주소에서 **부를 때 쓰는 동네 이름** 한 덩어리를 뽑는다. (2026-09-19 사장님)
 *   *"구 빼고 동탄 이렇게만. 수원이면 수원 이렇게."*
 *
 * 전화 카드 2주 달력은 칸 하나가 47dp 라 **다섯 글자**가 한계다.
 * 그래서 붙는 말(시·군·구·동·읍·면)을 떼고 줄기만 쓴다.
 *
 *   "서울특별시 강서구 화곡동 …"       → 강서
 *   "경기도 화성시 동탄대로 250번길 18" → 화성
 *   "부천시 옥길동 한신더휴"            → 부천
 *
 * 고르는 순서는 **구 → 시·군 → 동·읍·면**. 구가 제일 '동네' 답다.
 * 도·특별시·광역시는 너무 넓어서 아예 안 쓴다 — "서울" 은 동네 이름이 아니다.
 */
object RegionName {

    /** 너무 넓어서 동네로 못 쓰는 말. "서울특별시" 를 "서울특별" 로 자르는 사고도 막는다. */
    private val TOO_WIDE = listOf("특별자치시", "특별자치도", "특별시", "광역시")

    /** 구 → 시·군 → 동·읍·면 순으로 찾는다. 앞쪽이 더 '동네' 답다. */
    private val TIERS = listOf(
        charArrayOf('구'),
        charArrayOf('시', '군'),
        charArrayOf('동', '읍', '면')
    )

    /**
     * 띄어쓰기를 아예 안 쓴 주소용 — 붙어 있는 글자 속에서 찾는다.
     *   "가능동sk뷰아파트103동801호" → 가능
     * 앞이 한글 2~4자여야 해서 "103동", "시범한빛302동" 같은 **동·호수는 안 걸린다**.
     */
    private val GLUED = listOf(
        Regex("[가-힣]{2,4}구"),
        Regex("[가-힣]{2,4}[시군]"),
        Regex("[가-힣]{2,4}[동읍면]")
    )

    fun shortRegion(address: String?): String? {
        val a = address?.trim().orEmpty()
        if (a.isBlank()) return null
        val tokens = a.split(' ', '\t', '\n', ',', ' ').filter { it.isNotBlank() }
        for (tier in TIERS) {
            for (t in tokens) stemOf(t, tier)?.let { return it }
        }
        // 띄어쓰기가 없어 토큰으로 못 자른 주소. 사장님 자료 62개 중 1건이 이랬다(2026-09-19 검산).
        for (re in GLUED) {
            val m = re.find(a) ?: continue
            if (TOO_WIDE.any { m.value.endsWith(it) }) continue
            val stem = m.value.dropLast(1)
            return if (stem.length >= 2) stem else m.value
        }
        return null
    }

    /**
     * 여러 주소 → 칸에 넣을 한 줄. 같은 동네는 한 번만.
     *   한 곳 "동탄" · 두 곳 "동탄·수원" · 세 곳 넘으면 "동탄 외 2"
     * 주소가 하나도 안 풀리면 null — 부르는 쪽에서 "시공" 같은 대체 글자를 쓴다.
     */
    fun joinRegions(addresses: List<String?>): String? {
        val seen = LinkedHashSet<String>()
        for (a in addresses) shortRegion(a)?.let { seen.add(it) }
        return when (seen.size) {
            0 -> null
            1 -> seen.first()
            2 -> seen.joinToString("·")
            else -> seen.first() + " 외 " + (seen.size - 1)
        }
    }

    private fun stemOf(token: String, suffixes: CharArray): String? {
        if (token.length !in 2..6) return null
        if (!token.all { it in '가'..'힣' }) return null
        if (TOO_WIDE.any { token.endsWith(it) }) return null
        if (suffixes.none { it == token.last() }) return null
        val stem = token.dropLast(1)
        // "중구" 처럼 떼면 한 글자만 남는 곳은 붙은 채로 둔다 — "중" 혼자는 못 읽는다.
        return if (stem.length >= 2) stem else token
    }
}
