package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 동/호수 두 칸 갈라 받기. (2026-09-23 사장님 "동호수도 통일되서 들어올 수 있게")
 *
 * 제일 중요한 건 **왕복해도 안 깨지는 것** — 이미 저장된 주소를 열어 고치고 다시 저장할 때
 * 글자가 사라지거나 "103동 1103호 호" 처럼 늘어붙으면 안 된다.
 */
class DongHoTest {

    private fun round(raw: String): String {
        val (d, h) = splitDongHo(raw)
        return joinDongHo(d, h)
    }

    @Test
    fun `제각각 들어온 모양이 한 가지로 통일된다`() {
        assertEquals("103동 1103호", round("103동 1103호"))
        assertEquals("103동 1103호", round("103-1103"))
        assertEquals("103동 1103호", round("103/1103"))
        assertEquals("103동 1103호", round("103동1103호"))
        assertEquals("103동 1103호", round(" 103 동  1103 호 "))
    }

    @Test
    fun `동 없는 빌라·단독은 호만`() {
        assertEquals("1103호", round("1103호"))
        assertEquals("1103호", round("1103"))
        assertEquals("", round(""))
        assertEquals("", round("   "))
    }

    @Test
    fun `한글·영문 동도 갈라진다`() {
        assertEquals("가동 101호", round("가동 101호"))
        assertEquals("A동 101호", round("A동 101호"))
        assertEquals("B동", round("B동"))
    }

    @Test
    fun `못 알아본 글자는 버리지 않는다`() {
        // 옛 자유입력 — 해석 못 해도 원문이 그대로 남아야 한다.
        assertEquals("지하 주차장 옆 계단", round("지하 주차장 옆 계단"))
        assertEquals("후문쪽 상가 2층", round("후문쪽 상가 2층"))
    }

    @Test
    fun `두 번 저장해도 호가 겹쳐 붙지 않는다`() {
        assertEquals("103동 1103호", round(round("103동 1103호")))
        assertEquals("1103호", round(round("1103")))
    }

    @Test
    fun `사용자가 단위까지 적어도 한 번만 붙는다`() {
        assertEquals("103동 1103호", joinDongHo("103동", "1103호"))
        assertEquals("103동 1103호", joinDongHo("103", "1103"))
        assertEquals("1103호", joinDongHo("", "1103호"))
        assertEquals("103동", joinDongHo("103동", ""))
    }
}
