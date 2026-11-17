package com.detailline.callfollowcrm.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HangulSearchTest {

    @Test fun initials_extractsChosung() {
        assertEquals("ㅅㄹㅋㅅㄱ", HangulSearch.initials("실리콘시공"))
        assertEquals("ㅈㄴ", HangulSearch.initials("줄눈"))
    }

    @Test fun initials_passesThroughNonHangul() {
        assertEquals("A/S 3", HangulSearch.initials("A/S 3"))
    }

    @Test fun matches_byChosungSingle() {
        assertTrue(HangulSearch.matches("실리콘 시공", "ㅅ"))
        assertTrue(HangulSearch.matches("세면대 교체", "ㅅ"))
    }

    @Test fun matches_byChosungMulti() {
        assertTrue(HangulSearch.matches("실리콘 시공", "ㅅㄹ"))
        assertTrue(HangulSearch.matches("줄눈 시공", "ㅈㄴ"))
    }

    @Test fun matches_bySubstring() {
        assertTrue(HangulSearch.matches("실리콘 시공", "실리"))
        assertTrue(HangulSearch.matches("벽면 줄눈", "줄눈"))
    }

    @Test fun matches_ignoresSpaces() {
        assertTrue(HangulSearch.matches("벽면 줄눈", "벽면줄눈"))
        assertTrue(HangulSearch.matches("벽면 줄눈", "ㅂㅁㅈㄴ"))
    }

    @Test fun matches_emptyQuery_true() {
        assertTrue(HangulSearch.matches("실리콘", ""))
        assertTrue(HangulSearch.matches("실리콘", "   "))
    }

    @Test fun matches_noHit_false() {
        assertFalse(HangulSearch.matches("실리콘 시공", "방수"))
        assertFalse(HangulSearch.matches("실리콘 시공", "ㅂㅅ"))
    }

    @Test fun matches_fullSyllableDoesNotFalseMatch() {
        // "시"(완성형)는 글자매칭으로만 걸려야 — 초성 'ㅅ' 들과 섞여선 안 됨.
        assertTrue(HangulSearch.matches("시공", "시"))
        assertFalse(HangulSearch.matches("방수 공사", "시"))
    }
}
