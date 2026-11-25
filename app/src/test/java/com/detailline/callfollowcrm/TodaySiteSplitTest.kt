package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.service.TodaySiteNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 오늘의 현장 알림 — 단지명 / 동호수 분리. (2026-11-26 사장님: 긴 단지명이 제목에서 접히던 것) */
class TodaySiteSplitTest {
    @Test fun complexWithDongHo() {
        val (c, u) = TodaySiteNotifier.splitComplexUnit("래미안루센티아아파트 111동 1103호")
        assertEquals("래미안루센티아아파트", c)
        assertEquals("111동 1103호", u)
    }

    @Test fun complexWithHoOnly() {
        val (c, u) = TodaySiteNotifier.splitComplexUnit("동아 아파트 602호")
        assertEquals("동아 아파트", c)
        assertEquals("602호", u)
    }

    @Test fun complexWithoutUnit() {
        val (c, u) = TodaySiteNotifier.splitComplexUnit("래미안루센티아아파트")
        assertEquals("래미안루센티아아파트", c)
        assertNull(u)
    }

    /** 동호수만 있으면 통째로 제목에 두고 둘째 줄은 숨긴다(빈 제목 방지). */
    @Test fun unitOnlyStaysWhole() {
        val (c, u) = TodaySiteNotifier.splitComplexUnit("1103호")
        assertEquals("1103호", c)
        assertNull(u)
    }

    /** 도로명 주소는 동호수 꼬리가 없으니 통째로. */
    @Test fun roadAddressHasNoUnit() {
        val (c, u) = TodaySiteNotifier.splitComplexUnit("서울 서대문구 거북골로 100")
        assertEquals("서울 서대문구 거북골로 100", c)
        assertNull(u)
    }
}
