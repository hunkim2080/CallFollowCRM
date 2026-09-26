package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.presentation.screen.stats.HEAD_LINES
import com.detailline.callfollowcrm.presentation.screen.stats.headOf
import com.detailline.callfollowcrm.presentation.screen.stats.monthNo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ✍️ **인증샷·영상 한 줄 문안** — 여섯 달에 한 바퀴. (2026-09-26 사장님 "6세트 준비하면 6달에 한번씩 반복되게")
 *
 * 한 달에 한 번만 눈에 띄는 글이라 **폰으로는 확인할 방법이 없다.**
 * 빠진 줄·중복·엉뚱한 달에 나오는 것 전부 여기서 본다.
 */
class HeadLineTest {

    @Test
    fun `숫자마다 여섯 줄이 다 있다`() {
        assertEquals("자랑할 숫자 여섯 가지가 다 있어야", 6, HEAD_LINES.size)
        HEAD_LINES.forEach { (k, v) ->
            assertEquals("「$k」 은 여섯 줄이어야 반년에 한 바퀴 돈다", 6, v.size)
            assertEquals("「$k」 에 같은 줄이 두 번 들어갔다", v.size, v.toSet().size)
            v.forEach { line ->
                assertTrue("「$k」 에 빈 줄이 있다", line.isNotBlank())
                assertTrue("「$k」 의 '$line' 이 너무 길다 — 그림에서 작아진다", line.length <= 20)
            }
        }
    }

    @Test
    fun `이번 달엔 사장님이 고르신 줄이 나온다`() {
        // 2026-09-26 프로토에서 고르신 그 문구.
        assertEquals("올해도, 현장에서.", headOf("올해", "2026년 9월"))
        assertEquals("이번 달도, 현장에서.", headOf("이번 달", "2026년 9월"))
        assertEquals("땀 흘린 만큼, 쌓인 매출.", headOf("번 돈", "2026년 9월"))
    }

    @Test
    fun `여섯 달에 한 바퀴 돈다`() {
        // 9월과 3월이 같은 줄, 그 사이 다섯 달은 전부 달라야 한다.
        val seen = (9..14).map { headOf("번 돈", "2026년 ${if (it > 12) it - 12 else it}월") }
        assertEquals("여섯 달 동안 같은 줄이 또 나왔다", 6, seen.toSet().size)
        assertEquals("반년 뒤엔 첫 줄로 돌아와야", headOf("번 돈", "2026년 9월"), headOf("번 돈", "2027년 3월"))
    }

    @Test
    fun `열두 달 모두 줄이 나온다`() {
        for (m in 1..12) {
            HEAD_LINES.keys.forEach { k ->
                assertTrue("$m 월 「$k」 가 비었다", headOf(k, "2026년 ${m}월").isNotBlank())
            }
        }
    }

    @Test
    fun `달을 못 읽어도 터지지 않는다`() {
        assertTrue(headOf("번 돈", "").isNotBlank())
        assertTrue(headOf("번 돈", "이번 달").isNotBlank())
        assertEquals(0, monthNo(""))
        assertEquals(9, monthNo("2026년 9월"))
    }

    @Test
    fun `모르는 숫자면 한 줄을 안 그린다`() {
        // 새 숫자를 넣고 문구를 안 적으면 **빈 줄 대신 아예 안 그려야** 한다.
        assertEquals("", headOf("없는거", "2026년 9월"))
    }
}
