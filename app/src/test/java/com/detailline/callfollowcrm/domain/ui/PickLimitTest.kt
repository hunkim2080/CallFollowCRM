package com.detailline.callfollowcrm.domain.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickLimitTest {

    @Test fun `남은 자리 - 한도에서 담긴 수를 뺀다`() {
        assertEquals(5, PickLimit.remaining(0))
        assertEquals(2, PickLimit.remaining(3))
        assertEquals(0, PickLimit.remaining(5))
    }

    @Test fun `남은 자리 - 한도를 넘겨 담겨 있어도 음수는 없다`() {
        assertEquals(0, PickLimit.remaining(7))
    }

    @Test fun `토글 - 처음 누르면 맨 뒤에 담긴다`() {
        assertEquals(listOf("a", "b"), PickLimit.toggle(listOf("a"), "b", room = 5))
    }

    @Test fun `토글 - 고른 걸 또 누르면 빠진다 (연타해도 두 번 안 담김)`() {
        val once = PickLimit.toggle(emptyList(), "a", room = 5)
        assertEquals(listOf("a"), once)
        assertEquals(emptyList<String>(), PickLimit.toggle(once, "a", room = 5))
    }

    @Test fun `토글 - 자리가 없으면 그대로`() {
        val full = listOf("a", "b")
        assertEquals(full, PickLimit.toggle(full, "c", room = 2))
        assertTrue(PickLimit.isFull(full, "c", room = 2))
    }

    @Test fun `토글 - 꽉 차도 고른 건 뺄 수 있다`() {
        val full = listOf("a", "b")
        assertEquals(listOf("b"), PickLimit.toggle(full, "a", room = 2))
        assertFalse(PickLimit.isFull(full, "a", room = 2))
    }

    @Test fun `토글 - 남은 자리 0 이면 아무것도 못 고른다`() {
        assertEquals(emptyList<String>(), PickLimit.toggle(emptyList(), "a", room = 0))
        assertTrue(PickLimit.isFull(emptyList(), "a", room = 0))
    }

    @Test fun `합치기 - 뒤에 붙는다`() {
        assertEquals(listOf("a", "b", "c"), PickLimit.merge(listOf("a"), listOf("b", "c")))
    }

    @Test fun `합치기 - 같은 사진은 한 번만`() {
        assertEquals(listOf("a", "b"), PickLimit.merge(listOf("a"), listOf("a", "b", "b")))
        assertEquals(0, PickLimit.dropped(listOf("a"), listOf("a", "b", "b")))
    }

    @Test fun `합치기 - 한도까지만 담고 못 담은 수를 센다`() {
        val attached = listOf("1", "2", "3", "4")
        assertEquals(listOf("1", "2", "3", "4", "x"), PickLimit.merge(attached, listOf("x", "y", "z")))
        assertEquals(2, PickLimit.dropped(attached, listOf("x", "y", "z")))
    }

    @Test fun `합치기 - 이미 한도를 넘겨 담긴 건 안 뺀다`() {
        val over = listOf("1", "2", "3", "4", "5", "6")
        assertEquals(over, PickLimit.merge(over, listOf("x")))
        assertEquals(1, PickLimit.dropped(over, listOf("x")))
    }

    @Test fun `합치기 - 빈 것을 붙이면 그대로`() {
        assertEquals(listOf("a"), PickLimit.merge(listOf("a"), emptyList()))
        assertEquals(0, PickLimit.dropped(listOf("a"), emptyList<String>()))
    }
}
