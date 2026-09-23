package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 정산 화면에서 번호가 제목이라 어느 현장인지 몰랐던 것. (2026-09-23 사장님) */
class SiteLabelTest {

    @Test
    fun `이름이 있으면 이름이 제목`() {
        assertEquals("봉담 18평 빌라 탄성",
            SiteLabel.of("봉담 18평 빌라 탄성", "경기도 화성시 봉담읍 와우로34번길 63", "01098702393"))
    }

    @Test
    fun `이름이 없으면 주소를 줄여서 제목 - 시도는 뗀다`() {
        assertEquals("시흥시 가마길 4", SiteLabel.of(null, "경기 시흥시 가마길 4 ㅓㅁ머머", "01080056674"))
        assertEquals("화성시 봉담읍 와우로34번길",
            SiteLabel.of(null, "경기도 화성시 봉담읍 와우로34번길 63 신명아파트 102동", "01098702393"))
    }

    @Test
    fun `동 호수 꼬리는 제목에서 뺀다`() {
        assertEquals("부평구 부평문화로37번길 1-1",
            SiteLabel.of(null, "인천 부평구 부평문화로37번길 1-1 13동 1304호", "01099284027"))
    }

    @Test
    fun `이름도 주소도 없으면 번호 - 마지막 수단`() {
        assertEquals("010-8005-6674", SiteLabel.of(null, null, "01080056674"))
        assertEquals("010-8005-6674", SiteLabel.of("  ", "   ", "01080056674"))
    }

    @Test
    fun `곁줄은 번호 - 제목이 이미 번호면 없음`() {
        assertEquals("010-9870-2393", SiteLabel.sub("봉담 18평 빌라 탄성", null, "01098702393"))
        assertEquals("010-8005-6674", SiteLabel.sub(null, "경기 시흥시 가마길 4", "01080056674"))
        assertNull(SiteLabel.sub(null, null, "01080056674"))
    }

    @Test
    fun `주소가 한 토막이어도 버티고, 빈 값은 null`() {
        assertEquals("신명아파트", SiteLabel.shortAddress("신명아파트"))
        assertNull(SiteLabel.shortAddress(null))
        assertNull(SiteLabel.shortAddress("   "))
    }
}
