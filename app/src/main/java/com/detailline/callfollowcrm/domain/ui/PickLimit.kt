package com.detailline.callfollowcrm.domain.ui

/**
 * 🖐️ **「몇 장까지 고를 수 있나」 셈 — 한 곳.** (2026-11-21 카톡식 사진 첨부)
 *
 *   사진을 골라 담는 자리(채팅 사진 첨부 시트 · 현장 사진 선택창)가 **같은 셈**을 쓴다:
 *   고른 걸 또 누르면 빠지고, 자리가 없으면 안 담기고, 같은 사진은 두 번 안 담긴다.
 *   화면마다 `when { in selected -> … size >= max -> … }` 를 새로 적지 않는다(§12).
 *
 *   순수 함수 — 시험은 `PickLimitTest`.
 */
object PickLimit {

    /** 채팅 한 번에 담는 사진 한도(시스템 사진 선택기 maxItems 와 같은 값). */
    const val CHAT_MAX = 5

    /** 이미 [attached] 장 담겼을 때 **더 담을 수 있는 수**. 음수는 없다. */
    fun remaining(attached: Int, max: Int = CHAT_MAX): Int = (max - attached).coerceAtLeast(0)

    /**
     * 고르기 토글 — 이미 고른 거면 **뺀다**, 아니면 자리([room])가 있을 때만 **맨 뒤에 넣는다**.
     *   자리가 없으면 그대로 돌려준다 → 부른 쪽이 [isFull] 로 이유를 말한다(조용히 무시 금지).
     */
    fun <T> toggle(selected: List<T>, item: T, room: Int): List<T> = when {
        item in selected -> selected - item
        selected.size >= room -> selected
        else -> selected + item
    }

    /** [item] 을 더 고를 자리가 없나 — 이미 고른 건 빼는 동작이라 「꽉 참」이 아니다. */
    fun <T> isFull(selected: List<T>, item: T, room: Int): Boolean =
        item !in selected && selected.size >= room

    /**
     * 담긴 것([attached]) 뒤에 [add] 를 붙인다 — **같은 건 한 번만**, 전체가 [max] 를 안 넘게.
     *   이미 담긴 건 절대 안 뺀다(한도를 넘겨 담겨 있었어도 그대로). 못 담은 수는 [dropped] 로 센다.
     */
    fun <T> merge(attached: List<T>, add: List<T>, max: Int = CHAT_MAX): List<T> {
        val fresh = add.distinct().filter { it !in attached }
        return attached + fresh.take(remaining(attached.size, max))
    }

    /** [merge] 했을 때 **자리가 없어 못 담기는 수**(겹쳐서 빠진 건 안 센다). 0 보다 크면 화면이 말한다. */
    fun <T> dropped(attached: List<T>, add: List<T>, max: Int = CHAT_MAX): Int {
        val fresh = add.distinct().filter { it !in attached }
        return (fresh.size - remaining(attached.size, max)).coerceAtLeast(0)
    }
}
