package com.detailline.callfollowcrm.presentation.screen.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * 검색어에서 '몇 월' 읽기. (2026-09-19 사장님 "9월" · "지난달" 도 알아듣게)
 *
 * 왜 테스트로 확인하나: **한글은 adb 로 못 쳐서** 폰에서 눌러볼 수가 없다.
 *   폰 확인이 불가능한 자리라, 여기서 못 박는 게 유일한 검증이다.
 */
class SearchQueryParseTest {

    /** 기준: 2026년 9월 19일 */
    private val now: Long = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 19, 12, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 기준: 2026년 1월 5일 — 해를 넘기는 '지난달' 확인용 */
    private val january: Long = Calendar.getInstance().apply {
        set(2026, Calendar.JANUARY, 5, 12, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun of(q: String, at: Long = now) = SearchQueryParse.monthOf(q, at)

    @Test
    fun `달을 여러 가지 말로 써도 읽는다`() {
        assertEquals(2026 to 9, of("9월"))
        assertEquals(2026 to 9, of("09월"))
        assertEquals(2026 to 9, of("9월달"))
        assertEquals(2026 to 9, of("9 월"))
        assertEquals(2026 to 12, of("12월"))
    }

    @Test
    fun `해를 같이 쓰면 그 해로 읽는다`() {
        assertEquals(2025 to 3, of("2025년 3월"))
        assertEquals(2025 to 3, of("2025년3월"))
        // 해를 안 쓰면 올해
        assertEquals(2026 to 3, of("3월"))
    }

    @Test
    fun `지난달 이번달 다음달`() {
        assertEquals(2026 to 8, of("지난달"))
        assertEquals(2026 to 8, of("저번달"))
        assertEquals(2026 to 9, of("이번달"))
        assertEquals(2026 to 9, of("이번 달"))
        assertEquals(2026 to 10, of("다음달"))
    }

    @Test
    fun `1월에 지난달을 찾으면 작년 12월`() {
        assertEquals(2025 to 12, of("지난달", january))
    }

    @Test
    fun `달이 아닌 말은 안 읽는다`() {
        assertNull(of("동탄"))
        assertNull(of("35만원"))
        assertNull(of(""))
        assertNull(of("13월"))          // 없는 달
        assertNull(of("010-3509-9706")) // 번호
    }

    @Test
    fun `말 속에 섞여 있어도 읽는다`() {
        // 사장님이 "9월 시공" 처럼 붙여 칠 수 있다.
        assertEquals(2026 to 9, of("9월 시공"))
        assertEquals(2026 to 8, of("지난달 현장"))
    }
}
