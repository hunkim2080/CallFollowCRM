package com.detailline.callfollowcrm.util

/**
 * 🔢 **한 손님의 시공이 몇 차인가** — 셈 규칙을 여기 한 군데만 둔다.
 *
 * 이 셈은 화면 세 군데(탭 줄·현장 메모 제목·사진 제목)가 같이 쓴다.
 * 각자 적었다가 **세 번 어긋났다**:
 *   · 2026-09-18 — 취소한 건이 차수를 차지해 「1차를 취소했더니 빈 자리가 2차·신규」
 *   · 2026-09-19 — 탭은 취소를 빼고 세는데 메모는 다 세서 「1차를 고르면 2차 메모가 나오네」
 *   · 2026-09-27 — 날짜 없는 건을 0L(1970년)로 봐서 **맨 앞 = 1차**를 차지.
 *     사장님: "1차 시공도 없는데 2차 시공 날짜가 잡히는 건 버그인가?"
 *
 * 규칙은 세 줄이다:
 *   1. **먼저 한 날이 1차** (시공일 오름차순)
 *   2. **날짜를 안 잡은 건은 맨 뒤** — 아직 안 한 시공이 이미 끝낸 시공보다 먼저일 수 없다
 *   3. 날짜가 같거나 둘 다 없으면 **먼저 만든 건**이 앞
 *
 * 취소한 건·빈 건을 빼는 건 **부르는 쪽 책임**이다 (그 판단은 화면이 안다).
 */
object JobOrder {

    /** 지금 건(대표) — 아직 id 로 가리킬 수 없을 때 쓰는 표식. 동점이면 맨 뒤. */
    const val CURRENT = Long.MAX_VALUE

    /** 정렬 키. 날짜 없는 건은 맨 뒤로 보낸다. */
    fun key(scheduledWorkDate: Long?, id: Long): Pair<Long, Long> =
        (scheduledWorkDate ?: Long.MAX_VALUE) to id

    /**
     * (시공일, id) 목록을 차수 순서로 세워 **id 만** 돌려준다.
     * 돌려받은 목록의 자리(0부터)가 곧 차수−1 이다.
     */
    fun order(items: List<Pair<Long?, Long>>): List<Long> =
        items.map { key(it.first, it.second) }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.second }

    /** 이 건이 몇 차인가(1부터). 목록에 없으면 맨 뒤 다음 번호. */
    fun nth(order: List<Long>, id: Long): Int =
        order.indexOf(id).let { if (it < 0) order.size + 1 else it + 1 }
}
