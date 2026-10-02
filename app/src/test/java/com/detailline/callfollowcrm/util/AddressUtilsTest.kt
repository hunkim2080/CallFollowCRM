package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 🏠 **주소를 도로명 / 동·호수로 가르기** — 시험이 없었다. (2026-10-03)
 *
 *   사장님(2026-09-23): *"동호수도 통일돼서 들어올 수 있게"*
 *   빈 칸 하나로 받으면 「103동 1103호」·「103-1103」·「1103」이 제각각 들어온다.
 *   두 칸으로 갈라 받아야 저장이 **늘 한 모양**이 되고, 나중에 같은 아파트끼리 묶어볼 수 있다.
 *
 *   ⚠️ 제일 중요한 성질: **못 알아본 글자는 버리지 않는다.**
 *      이미 저장된 옛 주소를 열었을 때 내용이 사라지면 **사장님이 적어둔 게 날아간다.**
 *      그래서 아래 「왕복해도 그대로」 시험이 핵심이다.
 */
class AddressUtilsTest {

    // ── 도로명 / 동·호수 가르기 ──────────────────────────────────────

    @Test
    fun `아파트 주소를 도로명과 동호수로 가른다`() {
        val (base, detail) = splitSiteAddress("서울 강서구 마곡중앙로 36 1512동 1204호")
        assertEquals("서울 강서구 마곡중앙로 36", base)
        assertEquals("1512동 1204호", detail)
    }

    @Test
    fun `길 이름에 숫자가 붙어 있어도 안 속는다`() {
        // 🔴 「동탄대로24길」 의 24 를 동으로 오인하면 주소가 토막난다 (실제 손님 주소)
        val (base, detail) =
            splitSiteAddress("경기 화성시 동탄구 동탄대로24길 199 475동 901호")
        assertEquals("경기 화성시 동탄구 동탄대로24길 199", base)
        assertEquals("475동 901호", detail)
    }

    @Test
    fun `동호수가 없으면 전부 도로명이다`() {
        val (base, detail) = splitSiteAddress("서울시 강서구 공항대로63길 16")
        assertEquals("서울시 강서구 공항대로63길 16", base)
        assertEquals("", detail)
    }

    @Test
    fun `반지하도 동호수로 본다`() {
        val (base, detail) = splitSiteAddress("서울 관악구 어딘가로 10 반지하 1층")
        assertEquals("서울 관악구 어딘가로 10", base)
        assertEquals("반지하 1층", detail)
    }

    @Test
    fun `빈 주소는 빈 짝을 준다`() {
        assertEquals("" to "", splitSiteAddress(""))
        assertEquals("" to "", splitSiteAddress("   "))
    }

    // ── 동·호수 한 줄 → 두 칸 ────────────────────────────────────────

    @Test
    fun `제대로 적힌 동호수`() {
        assertEquals("103" to "1103", splitDongHo("103동 1103호"))
    }

    @Test
    fun `하이픈이나 빗금으로 적어도 알아본다`() {
        assertEquals("103" to "1103", splitDongHo("103-1103"))
        assertEquals("103" to "1103", splitDongHo("103/1103"))
    }

    @Test
    fun `숫자만 오면 호로 본다 — 빌라나 단독은 동이 없다`() {
        assertEquals("" to "1103", splitDongHo("1103"))
    }

    @Test
    fun `동만 적힌 것과 호만 적힌 것`() {
        assertEquals("가" to "", splitDongHo("가동"))
        assertEquals("" to "201", splitDongHo("201호"))
    }

    @Test
    fun `글자 동도 알아본다 — 나동 201호`() {
        assertEquals("나" to "201", splitDongHo("나동 201호"))
    }

    @Test
    fun `못 알아보면 통째로 호 칸에 넣는다 — 버리지 않는다`() {
        assertEquals("" to "경비실 옆집", splitDongHo("경비실 옆집"))
    }

    // ── 두 칸 → 저장할 한 줄 ────────────────────────────────────────

    @Test
    fun `늘 같은 모양으로 합친다`() {
        assertEquals("103동 1103호", joinDongHo("103", "1103"))
    }

    @Test
    fun `이미 동호가 붙어 있어도 두 번 안 붙인다`() {
        assertEquals("103동 1103호", joinDongHo("103동", "1103호"))
    }

    @Test
    fun `동이 없으면 호만`() {
        assertEquals("1103호", joinDongHo("", "1103"))
    }

    @Test
    fun `둘 다 비면 빈 문자열`() {
        assertEquals("", joinDongHo("", ""))
        assertEquals("", joinDongHo("  ", "  "))
    }

    @Test
    fun `동호수처럼 안 생긴 글에는 호를 안 붙인다`() {
        // 「경비실 옆집호」 가 되면 안 된다
        assertEquals("경비실 옆집", joinDongHo("", "경비실 옆집"))
        assertEquals("103동 경비실 옆집", joinDongHo("103", "경비실 옆집"))
    }

    // ── 🔑 왕복해도 그대로 — 사장님이 적어둔 게 날아가면 안 된다 ──────

    @Test
    fun `갈랐다 합쳐도 뜻이 안 바뀐다`() {
        listOf(
            "103동 1103호" to "103동 1103호",
            "103-1103" to "103동 1103호",     // 모양은 통일되지만 뜻은 같다
            "1103" to "1103호",
            "201호" to "201호",
            "나동 201호" to "나동 201호",
            "경비실 옆집" to "경비실 옆집"      // 못 알아봐도 **글자가 살아 있다**
        ).forEach { (적은것, 저장될것) ->
            val (d, h) = splitDongHo(적은것)
            assertEquals("「$적은것」 을 갈랐다 합치니 달라졌다", 저장될것, joinDongHo(d, h))
        }
    }

    @Test
    fun `저장된 모양을 다시 열어도 그대로다 — 두 번 왕복`() {
        val 처음 = "103동 1103호"
        val (d1, h1) = splitDongHo(처음)
        val 저장 = joinDongHo(d1, h1)
        val (d2, h2) = splitDongHo(저장)
        assertEquals(저장, joinDongHo(d2, h2))
        assertEquals(d1 to h1, d2 to h2)
    }
}
