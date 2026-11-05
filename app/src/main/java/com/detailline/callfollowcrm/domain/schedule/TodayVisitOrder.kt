package com.detailline.callfollowcrm.domain.schedule

/**
 * 오늘 갈 현장 — '지금 갈 곳' 고르기 + 순서. 홈 위젯 리스트용. (2026-11-05 사장님)
 *
 * 사장님 지시: 길찾기 버튼은 **'지금 갈 곳' 하나만**, 끝내거나(완료) 다음 현장 시간이 되면
 *   그 하나가 **다음 현장으로 자동 이동**한다. 나머지는 '이따 갈 곳' 목록(스크롤).
 *
 * 순수 셈 — 데이터 출처(내 건·협업)는 부르는 쪽(TodayWidgetProvider)이 모은다. 테스트: TodayVisitOrderTest.
 */
object TodayVisitOrder {

    /** 다음 현장 시작 몇 분 전부터 '지금'으로 올리나 (사장님: 30분 전 승격). */
    const val DUE_LEAD_MIN = 30

    /** 한 현장 — minutes=하루 분(0~1439, null=시각 미정 맨 뒤), completed=완료됨. item=부르는 쪽 원본. */
    data class Keyed<T>(val item: T, val minutes: Int?, val completed: Boolean)

    /** ordered=표시 순서(안 끝난 것 시간순 먼저 → 끝난 것 시간순 맨 아래), currentIndex=지금 갈 곳(없으면 -1). */
    data class Result<T>(val ordered: List<T>, val currentIndex: Int)

    /**
     * 순서: **안 끝난 현장**을 시간순(시각 없으면 뒤)으로 먼저, 그 뒤에 **끝낸 현장**을 시간순으로.
     * 지금 갈 곳 = 안 끝난 것 중 가장 이른 것(= ordered[0]). 완료 찍으면 그게 아래로 빠져 다음이 자동으로 '지금'.
     *   안 끝난 게 하나도 없으면 currentIndex = -1 (전부 완료/빈 날).
     * (Kotlin sortedBy 는 안정 정렬이라 같은 시각은 넣은 순서 유지.)
     */
    fun <T> arrange(items: List<Keyed<T>>): Result<T> {
        val notDone = items.filter { !it.completed }.sortedBy { it.minutes ?: Int.MAX_VALUE }
        val done = items.filter { it.completed }.sortedBy { it.minutes ?: Int.MAX_VALUE }
        val ordered = (notDone + done).map { it.item }
        return Result(ordered, if (notDone.isEmpty()) -1 else 0)
    }

    /** 이 현장이 지금 '갈 때가 됐나' — 시각 미정이면 지금 갈 곳이면 바로 true. 아니면 시작 30분 전부터 true. */
    fun isDue(minutes: Int?, nowMinutes: Int): Boolean =
        minutes == null || nowMinutes >= minutes - DUE_LEAD_MIN
}
