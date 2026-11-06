package com.detailline.callfollowcrm.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSnippetTest {

    @Test fun around_middle_hasEllipsisBothSides_andKeepsQuery() {
        val body = "어제 수원 영통동 아파트 베란다 줄눈 견적 이야기를 길게 나눴습니다"
        val s = SearchSnippet.around(body, "견적", window = 6)
        assertTrue("질의를 품어야", s.contains("견적"))
        assertTrue("앞이 잘렸으면 …", s.startsWith("…"))
        assertTrue("뒤가 잘렸으면 …", s.endsWith("…"))
    }

    @Test fun around_nearStart_noLeadingEllipsis() {
        val body = "수원에서 전화로 견적 이야기함"
        val s = SearchSnippet.around(body, "수원", window = 22)
        assertTrue("앞에서 걸리면 머리 … 없음", !s.startsWith("…"))
        assertTrue(s.contains("수원"))
    }

    @Test fun around_noMatch_takes60() {
        val body = "가".repeat(100)
        val s = SearchSnippet.around(body, "없는말")
        assertEquals(60, s.length)
    }

    @Test fun around_flattensNewlines() {
        val s = SearchSnippet.around("앞줄\n수원\n뒷줄", "수원", window = 5)
        assertTrue("줄바꿈은 공백으로", !s.contains("\n"))
    }

    @Test fun firstHit_picksFirstFieldContaining() {
        val fields = listOf<String?>(null, "   ", "전사: 수원 견적 통화", "요약: 수원 방문")
        val s = SearchSnippet.firstHit(fields, "견적")
        assertTrue("전사(앞쪽)에서 걸려야", s!!.contains("견적"))
    }

    @Test fun firstHit_skipsBlankAndNull() {
        val fields = listOf<String?>(null, "", "여기엔 없음", "여기 수원 있음")
        val s = SearchSnippet.firstHit(fields, "수원")
        assertTrue(s!!.contains("수원"))
    }

    @Test fun firstHit_noneMatches_null() {
        assertNull(SearchSnippet.firstHit(listOf("가", "나", null), "수원"))
    }

    @Test fun firstHit_blankQuery_null() {
        assertNull(SearchSnippet.firstHit(listOf("수원 견적"), "   "))
    }
}
