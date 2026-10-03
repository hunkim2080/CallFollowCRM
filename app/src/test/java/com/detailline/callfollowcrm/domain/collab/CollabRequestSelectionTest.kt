package com.detailline.callfollowcrm.domain.collab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🤝 **「보낸 협업이 조용히 취소되는 것」을 막는 시험.** (2026-10-03)
 *
 *   폰에서는 이걸 눈으로 못 본다 — 재현하려면 **진짜 협업 요청을 쏴야** 하고,
 *   그건 상대 사장님께 실제로 문자·알림이 가는 일이다. 그래서 시험이 대신 본다.
 *
 *   두 번 터진 자리다(둘 다 2026-09-27):
 *     ① 협업 자료가 **시트가 그려진 뒤에** 와서 이미 요청한 사장님이 안 골라진 채로 남았다
 *        → [보내기] 가 그걸 「뺐다」로 읽어 「보낸 협업을 취소할까요?」를 띄웠다
 *     ② 명부에 **줄이 없는** 번호로 보낸 요청은 사장님이 **누를 수가 없는데도**
 *        「안 골랐다」로 세어져 영영 「빼는 중」이 됐다
 */
class CollabRequestSelectionTest {

    private val 해시 = "21972496"
    private val 디테일라인 = "80056674"
    private val 명부에없는사장님 = "99998888"

    // ── ① 늦게 오는 자료 채우기 ──────────────────────────────────

    @Test
    fun `자료가 늦게 와도 이미 요청한 사장님은 골라진다`() {
        // 시트가 그려질 때는 비어 있었다
        val (처음, 채웠나) = CollabRequestSelection.seed(emptySet(), emptySet(), alreadySeeded = false)
        assertEquals(emptySet<String>(), 처음)
        assertFalse("자료가 안 왔으면 채운 걸로 치지 않는다", 채웠나)

        // 잠시 뒤 자료가 왔다
        val (다음, 이제채웠나) = CollabRequestSelection.seed(처음, setOf(해시), alreadySeeded = 채웠나)
        assertEquals(setOf(해시), 다음)
        assertTrue(이제채웠나)
    }

    @Test
    fun `한 번 채운 뒤에는 일부러 뺀 사람이 도로 안 들어온다`() {
        // 🔴 매번 채우면 사장님이 **취소를 할 수가 없다**
        val (뺀뒤, 여전히채운상태) = CollabRequestSelection.seed(emptySet(), setOf(해시), alreadySeeded = true)
        assertEquals("도로 들어왔다", emptySet<String>(), 뺀뒤)
        assertTrue(여전히채운상태)
    }

    @Test
    fun `이미 고른 사람은 그대로 두고 요청만 더한다`() {
        val (합쳐짐, _) = CollabRequestSelection.seed(setOf(디테일라인), setOf(해시), alreadySeeded = false)
        assertEquals(setOf(디테일라인, 해시), 합쳐짐)
    }

    @Test
    fun `같은 사람이 양쪽에 있어도 한 번만 들어간다`() {
        val (합쳐짐, _) = CollabRequestSelection.seed(setOf(해시), setOf(해시), alreadySeeded = false)
        assertEquals(setOf(해시), 합쳐짐)
    }

    // ── ② 취소할 수 있는 요청 ────────────────────────────────────

    @Test
    fun `명부에 줄이 없는 요청은 취소 대상이 아니다`() {
        // 🔴 사장님이 **누를 수가 없는** 줄이다 — 안 골랐다고 뺀 게 아니다
        val 취소가능 = CollabRequestSelection.cancelable(
            requested = setOf(해시, 명부에없는사장님),
            listed = setOf(해시, 디테일라인)
        )
        assertEquals(setOf(해시), 취소가능)
    }

    @Test
    fun `명부가 비어 있으면 취소할 수 있는 게 없다`() {
        assertEquals(
            emptySet<String>(),
            CollabRequestSelection.cancelable(setOf(해시), emptySet())
        )
    }

    // ── ③ 「보낸 협업을 취소할까요?」를 띄울 때 ─────────────────────

    @Test
    fun `이미 요청한 사장님이 그대로 골라져 있으면 취소창이 안 뜬다`() {
        // 2026-09-27 사장님이 겪은 그 자리 — 한 명 더 부르려는데 취소창이 떴다
        assertFalse(
            CollabRequestSelection.isCancelling(
                requested = setOf(해시),
                listed = setOf(해시, 디테일라인),
                selected = setOf(해시)
            )
        )
    }

    @Test
    fun `한 명을 더 골라도 취소창이 안 뜬다`() {
        assertFalse(
            CollabRequestSelection.isCancelling(
                requested = setOf(해시),
                listed = setOf(해시, 디테일라인),
                selected = setOf(해시, 디테일라인)
            )
        )
    }

    @Test
    fun `일부러 체크를 풀었을 때만 취소창이 뜬다`() {
        assertTrue(
            CollabRequestSelection.isCancelling(
                requested = setOf(해시),
                listed = setOf(해시, 디테일라인),
                selected = setOf(디테일라인)
            )
        )
    }

    @Test
    fun `명부에 없는 요청만 안 골라진 상태로는 취소창이 안 뜬다`() {
        // 🔴 이게 뜨면 사장님은 **누를 수 없는 줄 때문에** 매번 취소 창을 본다
        assertFalse(
            CollabRequestSelection.isCancelling(
                requested = setOf(해시, 명부에없는사장님),
                listed = setOf(해시),
                selected = setOf(해시)
            )
        )
    }

    @Test
    fun `보낸 요청이 아예 없으면 취소창이 안 뜬다`() {
        assertFalse(
            CollabRequestSelection.isCancelling(emptySet(), setOf(해시), setOf(해시))
        )
    }

    @Test
    fun `둘 다 요청했는데 하나만 풀면 취소창이 뜬다`() {
        assertTrue(
            CollabRequestSelection.isCancelling(
                requested = setOf(해시, 디테일라인),
                listed = setOf(해시, 디테일라인),
                selected = setOf(해시)
            )
        )
    }

    @Test
    fun `아무도 안 골랐으면 보낸 요청 전부가 취소다`() {
        assertTrue(
            CollabRequestSelection.isCancelling(
                requested = setOf(해시, 디테일라인),
                listed = setOf(해시, 디테일라인),
                selected = emptySet()
            )
        )
    }
}
