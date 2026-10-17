package com.detailline.callfollowcrm.domain.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 아바타 글자·색 번호 셈 — 여섯 화면이 제각각이던 것을 한 곳으로 모으며 박아 둔 시험. (2026-10-17) */
class AvatarInitialTest {

    @Test
    fun `of - 보통 이름은 첫 글자`() {
        assertEquals("김", AvatarInitial.of("김손님"))
        assertEquals("H", AvatarInitial.of("HousePick"))
    }

    @Test
    fun `of - 이모지로 시작하면 건너뛰고 읽을 글자`() {
        assertEquals("사", AvatarInitial.of("💖사랑하는 김손님"))
    }

    @Test
    fun `of - 괄호·공백은 지운다`() {
        assertEquals("주", AvatarInitial.of("(주) 디테일라인"))
    }

    @Test
    fun `of - 읽을 글자가 없으면 빈 값(호출부가 사람 아이콘)`() {
        assertEquals("", AvatarInitial.of("😀"))
        assertEquals("", AvatarInitial.of(null))
        assertEquals("", AvatarInitial.of("   "))
    }

    @Test
    fun `brand - 쉼표가 있으면 마지막 쉼표 뒤 상호의 첫 글자`() {
        assertEquals("하", AvatarInitial.brand("김사장, 하우스픽"))
        assertEquals("태", AvatarInitial.brand("박사장,태그줄눈"))
        assertEquals("해", AvatarInitial.brand("이사장, 디테일, 해시줄눈"))
    }

    @Test
    fun `brand - 쉼표 없으면 전체 첫 글자`() {
        assertEquals("해", AvatarInitial.brand("해시줄눈"))
    }

    @Test
    fun `brand - 쉼표 뒤가 비면 전체로 돌아간다`() {
        assertEquals("태", AvatarInitial.brand("태그줄눈, "))
        assertEquals("태", AvatarInitial.brand("태그줄눈,"))
    }

    @Test
    fun `brand - 사장님(···1234) 같은 빈 이름 대체도 글자가 나온다`() {
        assertEquals("사", AvatarInitial.brand("사장님(···1234)"))
    }

    @Test
    fun `tintIndex - 같은 이름은 늘 같은 번호, 범위 안`() {
        val a = AvatarInitial.tintIndex("하우스픽", 5)
        val b = AvatarInitial.tintIndex("하우스픽", 5)
        assertEquals(a, b)
        assertTrue(a in 0 until 5)
        // 앞뒤 공백은 같은 이름
        assertEquals(a, AvatarInitial.tintIndex("  하우스픽 ", 5))
    }

    @Test
    fun `tintIndex - 빈 이름·0 크기도 안 터진다`() {
        assertEquals(0, AvatarInitial.tintIndex(null, 5))
        assertEquals(0, AvatarInitial.tintIndex("아무개", 0))
    }

    @Test
    fun `tintIndex - 이름이 다르면 대체로 다른 색(다섯 이름 중 둘 이상은 갈린다)`() {
        val idx = listOf("해시줄눈", "하우스픽", "태그줄눈", "디테일라인", "김사장").map { AvatarInitial.tintIndex(it, 5) }.toSet()
        assertTrue("전부 같은 색이면 해시가 죽은 것: $idx", idx.size >= 2)
    }
}
