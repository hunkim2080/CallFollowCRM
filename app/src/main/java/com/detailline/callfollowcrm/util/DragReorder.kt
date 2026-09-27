package com.detailline.callfollowcrm.util

/**
 * ↕️ **끌어서 자리 바꾸기 — 셈은 여기 한 곳에만.** (2026-09-27)
 *
 * 같은 셈이 두 군데에 복사돼 있었다 — 가격표 화면과 접수서 시트.
 * 한쪽만 고치면 두 화면이 다르게 움직인다(이 앱에서 여러 번 겪은 병).
 *
 * 규칙은 하나다: **이웃 줄 높이의 절반을 넘게 끌면** 그 줄과 자리를 바꾸고,
 * 바꾼 만큼을 끌린 거리에서 **덜어낸다**. 덜어내지 않으면 한 번 끌 때 우르르 넘어간다.
 *
 * 화면(Compose)을 안 쓰므로 그대로 시험해 볼 수 있다 — [DragReorderTest].
 */
object DragReorder {

    /** 한 번 움직인 결과. [order] 는 바뀐 차례, [dy] 는 남은 끌린 거리. */
    data class Step<T>(val order: List<T>, val dy: Float)

    /**
     * @param order 지금 차례
     * @param id    잡고 있는 줄
     * @param dy    잡은 뒤로 끌린 거리(위로 끌면 음수)
     * @param upH   바로 **위** 줄의 높이(없으면 0)
     * @param dnH   바로 **아래** 줄의 높이(없으면 0)
     */
    fun <T> step(order: List<T>, id: T, dy: Float, upH: Int, dnH: Int): Step<T> {
        val i = order.indexOf(id)
        if (i < 0) return Step(order, dy)
        // 위로 — 맨 위에서는 더 갈 데가 없다.
        if (i > 0 && upH > 0 && dy < -upH / 2f) {
            return Step(order.toMutableList().apply { add(i - 1, removeAt(i)) }, dy + upH)
        }
        // 아래로 — 맨 아래에서는 더 갈 데가 없다.
        if (i < order.lastIndex && dnH > 0 && dy > dnH / 2f) {
            return Step(order.toMutableList().apply { add(i + 1, removeAt(i)) }, dy - dnH)
        }
        return Step(order, dy)
    }
}
