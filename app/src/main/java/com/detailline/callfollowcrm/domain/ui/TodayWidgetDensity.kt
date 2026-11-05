package com.detailline.callfollowcrm.domain.ui

/** RemoteViews cannot measure its host. The v2 prototype's measured safe branches. */
object TodayWidgetDensity {
    data class Layout(val featured: Boolean, val memoLines: Int)
    fun of(memo: String?, items: String?): Layout =
        Layout(featured = memo.isNullOrBlank(), memoLines = if (items.isNullOrBlank()) 2 else 1)
}
