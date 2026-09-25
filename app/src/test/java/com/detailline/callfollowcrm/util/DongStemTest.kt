package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 동 이름 **줄기** 뽑기. (2026-09-25 사장님 "우동이란곳도 없는데 왜 이렇게 나오는거지")
 *
 * 끝 글자를 여러 개 떼면 「우면동」이 「우」가 되어 부산 해운대 「우동」과 같아진다.
 * 그러면 **서초 현장이 부산에 찍힌다** — 지도가 통째로 거짓말이 된다.
 */
class DongStemTest {

    @Test
    fun `끝 글자 하나만 뗀다`() {
        assertEquals("우면", DongCoords.stemOf("우면동"))
        assertEquals("우", DongCoords.stemOf("우동"))
    }

    /** 이게 실제로 난 사고 — 서초 우면동이 부산 우동으로 둔갑했다. */
    @Test
    fun `우면동과 우동은 다른 곳이다`() {
        assertNotEquals(DongCoords.stemOf("우면동"), DongCoords.stemOf("우동"))
    }

    @Test
    fun `번호로 갈린 동은 번호를 뗀다`() {
        assertEquals("조원", DongCoords.stemOf("조원1동"))
        assertEquals("조원", DongCoords.stemOf("조원2동"))
        assertEquals("정자", DongCoords.stemOf("정자3동"))
    }

    @Test
    fun `읍 면도 한 글자만`() {
        assertEquals("오포", DongCoords.stemOf("오포읍"))
        assertEquals("남", DongCoords.stemOf("남면"))
    }

    @Test
    fun `보통 동네는 그대로`() {
        assertEquals("화곡", DongCoords.stemOf("화곡동"))
        assertEquals("마곡", DongCoords.stemOf("마곡동"))
        assertEquals("인계", DongCoords.stemOf("인계동"))
        assertEquals("매탄", DongCoords.stemOf("매탄동"))
    }
}
