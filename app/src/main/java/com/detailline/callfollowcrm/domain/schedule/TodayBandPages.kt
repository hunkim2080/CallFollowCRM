package com.detailline.callfollowcrm.domain.schedule

/**
 * 📄 **홈 맨 위 띠를 몇 쪽으로 넘기나, 그리고 몇 번째라고 적나.**
 *
 *   사장님(2026-09-28): 전엔 1쪽 = 오늘 **첫 곳**, 2쪽 = **다음 시공**이었다.
 *   그런데 글자엔 「(1/2)」라 적혀 있어 **넘기면 오늘 2번째가 나올 줄 아셨다** —
 *   실제로는 **내일**이 나왔다. **한 몸짓에 두 가지 뜻**이 섞여 있었다.
 *
 *   지금 규칙: **오늘 것을 한 쪽씩 다 보고, 그 뒤에 다음 시공.**
 *     오늘 2곳 + 다음 있음 → (1/2) · (2/2) · 다음  = 3쪽
 *     오늘 1곳             → **곳수는 안 적는다** (「(1/1)」은 아무 말도 안 한다)
 *     오늘 0곳             → 그 자리를 **안내 한 쪽**으로 쓴다 (빈 날 · 끝난 날)
 *
 *   ⚠️ 적는 글자가 **두 군데**(내 시공 띠 · 협업 띠)였고 **쓰는 모양이 서로 달랐다** —
 *      한쪽을 고치면 다른 쪽이 남는 자리였다. 그래서 셈과 글자를 **여기 한 곳**에 뒀다.
 *
 *   ⚠️ 폰에서는 「오늘 0곳」만 아무 때나 볼 수 있다 — 1곳·2곳은 **그런 날이어야** 보인다.
 *      그래서 시험이 대신 본다 (CLAUDE.md §12-E②).
 */
object TodayBandPages {

    /** 오늘 자리 수 — 오늘이 비어도 **안내 한 쪽**은 있어야 한다. */
    fun todaySlots(todayCount: Int): Int = maxOf(todayCount, 0).coerceAtLeast(1)

    /** 띠 전체 쪽수 = 오늘 자리 + (다음 시공이 있으면 한 쪽). */
    fun pageCount(todayCount: Int, hasNext: Boolean): Int =
        todaySlots(todayCount) + (if (hasNext) 1 else 0)

    /** 이 쪽이 **다음 시공** 쪽인가 (= 오늘 자리를 다 넘긴 뒤). */
    fun isNextPage(page: Int, todayCount: Int): Boolean = page >= todaySlots(todayCount)

    /**
     * 「  (2/3)」 — 넘긴 쪽과 **같은 숫자**여야 한다.
     * 오늘이 한 곳뿐이면 **빈 글자**를 준다. 앞의 빈칸 두 개까지 포함한다(붙여 쓰는 자리라서).
     */
    fun label(page: Int, todayCount: Int): String =
        if (todayCount > 1 && page < todayCount) "  (${page + 1}/$todayCount)" else ""
}
