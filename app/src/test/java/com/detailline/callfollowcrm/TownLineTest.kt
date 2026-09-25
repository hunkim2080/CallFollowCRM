package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.util.RecordShot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🧭 **동네 목록은 「다닌 순서」로 읽혀야 한다.** (2026-09-25 사장님 "왜 3을 안 거치는 느낌이지?")
 *
 * 점으로 나열하면 순서인지 그냥 목록인지 알 수가 없다.
 * 그리고 이 글줄을 만드는 코드가 **네 군데에 복사**돼 있었다(영상 1 · 그림 3) —
 * 한 곳으로 모았으니, 그 한 곳이 맞는지 여기서 본다.
 */
class TownLineTest {

    @Test
    fun `다닌 순서가 화살표로 보인다`() {
        assertEquals(
            "단원 → 관악 → 강서 → 분당 → 권선",
            RecordShot.townLine(listOf("단원", "관악", "강서", "분당", "권선"))
        )
    }

    @Test
    fun `한 곳만 다녔으면 화살표가 없다`() {
        assertEquals("동탄", RecordShot.townLine(listOf("동탄")))
        assertTrue(!RecordShot.townLine(listOf("동탄")).contains("→"))
    }

    @Test
    fun `여섯 곳까지 보여주고 나머지는 숫자로`() {
        // 줄이 길어지면 글자가 작아져 안 읽힌다. 일곱째부터는 "외 N곳".
        val many = listOf("가", "나", "다", "라", "마", "바", "사", "아")
        assertEquals("가 → 나 → 다 → 라 → 마 → 바 외 2곳", RecordShot.townLine(many))
    }

    @Test
    fun `여섯 곳이면 외 N곳이 안 붙는다`() {
        val six = listOf("가", "나", "다", "라", "마", "바")
        assertEquals("가 → 나 → 다 → 라 → 마 → 바", RecordShot.townLine(six))
    }

    @Test
    fun `아무 데도 안 갔으면 빈 줄`() {
        assertEquals("", RecordShot.townLine(emptyList()))
    }
}
