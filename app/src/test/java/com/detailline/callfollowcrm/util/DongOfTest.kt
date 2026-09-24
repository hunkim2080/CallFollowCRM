package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 주소에서 **동 이름 뽑기**. (2026-09-25 사장님 "동. 까지 뭔가 측정 가능했으면 좋겠어")
 *
 * 제일 무서운 건 **아파트 동호수를 동네로 잘못 읽는 것**이다 —
 * "103동 801호" 를 동네로 세면 「동네 40곳」 같은 거짓말이 된다.
 */
class DongOfTest {

    @Test
    fun `보통 주소`() {
        assertEquals("인계동", RegionName.dongOf("경기도 수원시 팔달구 인계동 1122"))
        assertEquals("우만동", RegionName.dongOf("수원시 팔달구 우만동 삼성아파트"))
        assertEquals("조원동", RegionName.dongOf("경기 수원시 장안구 조원동 111-2"))
    }

    @Test
    fun `번호가 붙은 동`() {
        assertEquals("동탄4동", RegionName.dongOf("화성시 동탄4동 롯데캐슬"))
    }

    @Test
    fun `아파트 동호수는 동네가 아니다`() {
        assertEquals("가능동", RegionName.dongOf("의정부시 가능동 103동 801호"))
        assertEquals("화곡동", RegionName.dongOf("서울 강서구 화곡동 우성아파트 105동 302호"))
    }

    @Test
    fun `띄어쓰기 없는 주소`() {
        assertEquals("가능동", RegionName.dongOf("가능동sk뷰아파트103동801호"))
    }

    @Test
    fun `읍 면도 잡는다`() {
        assertEquals("오포읍", RegionName.dongOf("경기도 광주시 오포읍 능평리"))
        assertEquals("남면", RegionName.dongOf("연천군 남면 상수리"))
    }

    @Test
    fun `도로명 주소는 동이 없다`() {
        assertNull(RegionName.dongOf("경기도 화성시 동탄대로 250번길 18"))
    }

    @Test
    fun `빈 주소`() {
        assertNull(RegionName.dongOf(null))
        assertNull(RegionName.dongOf("   "))
    }

    /** 구·시는 동이 아니다 — 여기 걸리면 「동네 수」가 부풀어 오른다. */
    @Test
    fun `구나 시를 동으로 읽지 않는다`() {
        assertNull(RegionName.dongOf("서울특별시 강서구"))
        assertNull(RegionName.dongOf("경기도 수원시 팔달구"))
    }
}
