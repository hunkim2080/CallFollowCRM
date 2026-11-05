package com.detailline.callfollowcrm.domain.ui

/** RemoteViews cannot measure its host. The v2 prototype's measured safe branches. */
object TodayWidgetDensity {
    /** featured=큰 파란 동호수(메모 없을 때만) · memoLines=메모 최대 줄수 · showMotif=아파트 그림으로 빈칸 채움. */
    data class Layout(val featured: Boolean, val memoLines: Int, val showMotif: Boolean)
    fun of(memo: String?, items: String?): Layout {
        val hasMemo = !memo.isNullOrBlank()
        val hasItems = !items.isNullOrBlank()
        // 아파트 그림은 **빈칸을 채우려고** 쓴다 — 내용이 꽉 찬 경우(메모+견적 둘 다)만 뺀다.
        //   그래서 메모만 있고 견적이 없는 현장(흔함)도 그림이 들어가 가운데가 안 빈다. (2026-11-05 사장님 "왜 텅 비어있냐")
        return Layout(featured = !hasMemo, memoLines = if (hasItems) 1 else 2, showMotif = !(hasMemo && hasItems))
    }
}
