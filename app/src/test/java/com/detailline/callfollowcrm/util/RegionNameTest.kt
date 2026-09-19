package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 주소 → 동네 이름. (2026-09-19 사장님 "구 빼고 동탄 이렇게만. 수원이면 수원 이렇게.")
 * 칸이 다섯 글자라 붙는 말을 뗀다. 고르는 순서는 구 → 시·군 → 동·읍·면.
 */
class RegionNameTest {

    @Test
    fun `구가 있으면 구를 쓴다`() {
        assertEquals("강서", RegionName.shortRegion("서울특별시 강서구 화곡동 삼성아파트 101동"))
        assertEquals("분당", RegionName.shortRegion("경기도 성남시 분당구 서현동 시범삼성 9동"))
    }

    @Test
    fun `구가 없으면 시_군`() {
        assertEquals("화성", RegionName.shortRegion("경기도 화성시 동탄대로 250번길 18"))
        assertEquals("가평", RegionName.shortRegion("경기도 가평군 청평면 호반로"))
    }

    @Test
    fun `시도 없으면 동_읍_면`() {
        assertEquals("옥길", RegionName.shortRegion("옥길동 한신더휴 105동 1203호"))
        assertEquals("반송", RegionName.shortRegion("반송동 시범한빛 302동"))
    }

    /** "서울특별시" 를 "서울특별" 로 자르면 안 된다. 광역 단위는 동네가 아니라 아예 안 쓴다. */
    @Test
    fun `특별시_광역시_도는 동네로 안 쓴다`() {
        assertNull(RegionName.shortRegion("서울특별시"))
        assertNull(RegionName.shortRegion("부산광역시"))
        assertNull(RegionName.shortRegion("경기도"))
    }

    /** 떼면 한 글자만 남는 곳은 붙은 채로 — "중" 혼자는 못 읽는다. */
    @Test
    fun `한 글자 구는 붙은 채로 둔다`() {
        assertEquals("중구", RegionName.shortRegion("서울특별시 중구 을지로 100"))
    }

    @Test
    fun `도로명_번지_동호수에 안 걸린다`() {
        // "250번길", "102동1603호" 는 숫자가 섞여 동네로 안 잡힌다
        assertEquals("화성", RegionName.shortRegion("화성시 동탄대로 250번길 18"))
        assertEquals("강서", RegionName.shortRegion("강서구 마곡중앙로 102동1603호"))
    }

    /** 사장님 자료에 실제로 있던 주소 — 띄어쓰기가 하나도 없다. (2026-09-19 검산) */
    @Test
    fun `띄어쓰기 없는 주소도 붙은 글자에서 찾는다`() {
        assertEquals("가능", RegionName.shortRegion("가능동sk뷰아파트103동801호"))
    }

    /** 동·호수는 걸리면 안 된다 — 앞이 숫자라 한글 2~4자 조건에 안 맞는다. */
    @Test
    fun `동호수는 동네로 안 잡힌다`() {
        assertNull(RegionName.shortRegion("시범한빛302동1603호"))
        assertNull(RegionName.shortRegion("삼성래미안101동202호"))
    }

    /** 사장님 자료엔 "화성시 동탄구" 처럼 적혀 있어서 원하시는 "동탄" 이 그대로 나온다. */
    @Test
    fun `실제 자료의 동탄_병점_효행이 그대로 나온다`() {
        assertEquals("동탄", RegionName.shortRegion("경기 화성시 동탄구 동탄대로24길 199 475동 901호"))
        assertEquals("병점", RegionName.shortRegion("경기 화성시 병점구 병점노을로 31 (병점역 아이파크 캐슬) 103동 501호"))
        assertEquals("효행", RegionName.shortRegion("경기 화성시 효행구 봉담읍 와우로34번길 63 (신명아파트) 102동 603호"))
    }

    @Test
    fun `빈 주소는 null`() {
        assertNull(RegionName.shortRegion(null))
        assertNull(RegionName.shortRegion("   "))
        assertNull(RegionName.shortRegion("삼성래미안 101동 202호"))
        assertNull(RegionName.shortRegion("서울 테스트로 1 101동 1호"))
    }

    @Test
    fun `여러 주소 묶기`() {
        assertEquals("강서", RegionName.joinRegions(listOf("서울 강서구 화곡동")))
        assertEquals(
            "강서·영통",
            RegionName.joinRegions(listOf("서울 강서구 화곡동", "수원시 영통구 e편한세상"))
        )
        assertEquals(
            "강서 외 2",
            RegionName.joinRegions(
                listOf("서울 강서구", "서울 송파구", "서울 강동구")
            )
        )
        assertNull(RegionName.joinRegions(listOf(null, "", "101동 202호")))
    }

    /** 같은 동네가 두 번 나와도 한 번만. "강서·강서" 는 말이 안 된다. */
    @Test
    fun `같은 동네는 한 번만`() {
        assertEquals(
            "강서",
            RegionName.joinRegions(listOf("서울 강서구 화곡동", "서울 강서구 등촌동"))
        )
    }
}
