package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 끌어서 자리 바꾸기. (2026-09-27 사장님 "꾹 눌러서 순서변경하는거 그거 왜안된거야")
 *
 * 폰을 못 만져보는 동안 **이 셈이 맞는지는 여기서 본다.**
 * 특히 두 가지가 중요하다:
 *   ① 바꾼 만큼을 끌린 거리에서 **덜어내야** 한다 — 안 덜면 한 번 끌 때 우르르 넘어간다.
 *   ② 맨 위·맨 아래에서는 **더 안 넘어가야** 한다 — 넘어가면 목록 밖으로 사라진다.
 */
class DragReorderTest {

    private val abc = listOf("a", "b", "c")

    @Test
    fun `이웃 높이의 절반을 넘게 위로 끌면 한 칸 올라간다`() {
        val r = DragReorder.step(abc, "b", dy = -51f, upH = 100, dnH = 100)
        assertEquals(listOf("b", "a", "c"), r.order)
    }

    @Test
    fun `절반에 못 미치면 그대로 있는다`() {
        val r = DragReorder.step(abc, "b", dy = -49f, upH = 100, dnH = 100)
        assertEquals(abc, r.order)
        assertEquals(-49f, r.dy, 0.01f)
    }

    @Test
    fun `아래로도 같은 규칙`() {
        val r = DragReorder.step(abc, "b", dy = 51f, upH = 100, dnH = 100)
        assertEquals(listOf("a", "c", "b"), r.order)
    }

    @Test
    fun `한 칸 넘어간 만큼은 끌린 거리에서 덜어낸다`() {
        // 안 덜어내면 다음 번에도 조건이 계속 참이라 우르르 넘어간다.
        val r = DragReorder.step(abc, "b", dy = -51f, upH = 100, dnH = 100)
        assertEquals(49f, r.dy, 0.01f)
        // 덜어낸 값으로 한 번 더 돌려도 더는 안 넘어간다(맨 위라서).
        val r2 = DragReorder.step(r.order, "b", dy = r.dy, upH = 0, dnH = 100)
        assertEquals(listOf("b", "a", "c"), r2.order)
    }

    @Test
    fun `맨 위에서 더 위로 끌어도 안 넘어간다`() {
        val r = DragReorder.step(abc, "a", dy = -500f, upH = 0, dnH = 100)
        assertEquals(abc, r.order)
    }

    @Test
    fun `맨 아래에서 더 아래로 끌어도 안 넘어간다`() {
        val r = DragReorder.step(abc, "c", dy = 500f, upH = 100, dnH = 0)
        assertEquals(abc, r.order)
    }

    @Test
    fun `줄 높이가 서로 달라도 그 줄 높이로 잰다`() {
        // 위 줄이 40, 아래 줄이 200 이면 위로는 21 만 끌어도 넘어가고 아래로는 101 이 필요하다.
        assertEquals(listOf("b", "a", "c"), DragReorder.step(abc, "b", -21f, 40, 200).order)
        assertEquals(abc, DragReorder.step(abc, "b", 99f, 40, 200).order)
        assertEquals(listOf("a", "c", "b"), DragReorder.step(abc, "b", 101f, 40, 200).order)
    }

    @Test
    fun `목록에 없는 줄을 잡으면 아무 일도 없다`() {
        val r = DragReorder.step(abc, "zz", dy = -500f, upH = 100, dnH = 100)
        assertEquals(abc, r.order)
    }

    @Test
    fun `여러 칸을 이어서 끌면 차례대로 올라간다`() {
        // 손가락을 한 번에 위로 쭉 — 한 걸음씩 부르면 두 칸을 올라간다.
        var order = listOf("a", "b", "c", "d")
        var dy = -260f
        repeat(4) {
            val i = order.indexOf("d")
            val up = if (i > 0) 100 else 0
            val dn = if (i < order.lastIndex) 100 else 0
            val r = DragReorder.step(order, "d", dy, up, dn)
            order = r.order; dy = r.dy
        }
        assertEquals(listOf("d", "a", "b", "c"), order)
    }
}
