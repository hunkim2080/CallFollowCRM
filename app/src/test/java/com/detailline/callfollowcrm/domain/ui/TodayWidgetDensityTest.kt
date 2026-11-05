package com.detailline.callfollowcrm.domain.ui

import org.junit.Assert.*
import org.junit.Test

class TodayWidgetDensityTest {
    @Test fun `all optional field combinations keep memo out of featured`() {
        // Name, unit, time and collaboration must not change the safe density branch.
        for (mask in 0 until 64) {
            val memo = if (mask and 1 != 0) "첫 줄\n둘째 줄" else ""
            val items = if (mask and 2 != 0) "욕실 · 현관" else ""
            val layout = TodayWidgetDensity.of(memo, items)
            assertEquals(memo.isEmpty(), layout.featured)
            assertEquals(if (items.isEmpty()) 2 else 1, layout.memoLines)
        }
    }
    @Test fun `whitespace and missing values do not reserve memo space`() {
        assertTrue(TodayWidgetDensity.of(null, null).featured)
        assertTrue(TodayWidgetDensity.of(" \n ", "욕실").featured)
        assertFalse(TodayWidgetDensity.of("비번", null).featured)
    }
}
