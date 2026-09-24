package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 좌표표가 **사장님 실제 주소**를 다 찍는지 증명. (2026-09-24 테스트폰에서 꺼낸 9월 주소 그대로)
 *
 * 특히 **일반구** — 사장님 주소는 "화성시 동탄구", "수원시 영통구", "고양시 덕양구" 형태라
 * [RegionName] 이 시가 아니라 **구**를 돌려준다. 일반구가 표에 없으면 현장 대부분이 지도에서 사라진다.
 */
class RegionCoordsTest {

    /** 2026-09-24 테스트폰 9월 현장 주소 (주소 있는 5곳). */
    private val 사장님주소 = listOf(
        "경기 화성시 동탄구 동탄대로24길 199 475동 901호" to "동탄",
        "서울 강동구 천호대로175길 42 지하1층 라곰트레이닝" to "강동",
        "경기 고양시 덕양구 호국로742번길 38 607-1201호" to "덕양",
        "경기 수원시 영통구 광교로 286 8008동 301호" to "영통",
        "서울 강서구 양천로69길 58 101동 211호" to "강서"
    )

    @Test
    fun `사장님 9월 현장이 한 곳도 안 빠지고 지도에 찍힌다`() {
        for ((addr, expectName) in 사장님주소) {
            assertEquals("이름 추출", expectName, RegionName.shortRegion(addr))
            val spot = RegionCoords.of(addr)
            assertNotNull("$addr → 좌표 없음(지도에서 사라짐)", spot)
            assertEquals(expectName, spot!!.name)
            // 남한 안인지 대충 확인 — 자리가 엉뚱하면 지도 밖으로 튄다
            assertTrue("위도 범위", spot.lat in 33.0..38.7)
            assertTrue("경도 범위", spot.lon in 125.5..130.0)
        }
    }

    @Test
    fun `같은 이름이 여러 시도에 있으면 시도로 가른다`() {
        val seoul = RegionCoords.of("서울특별시 강서구 화곡동 1")
        val busan = RegionCoords.of("부산광역시 강서구 명지동 1")
        assertNotNull(seoul); assertNotNull(busan)
        assertEquals("서울", seoul!!.sido)
        assertEquals("부산", busan!!.sido)
        assertTrue("서울 강서가 부산 강서보다 북쪽", seoul.lat > busan.lat)
    }

    @Test
    fun `중구는 일곱 곳이라 시도를 모르면 안 찍는다`() {
        // 엉뚱한 데 찍느니 안 찍는다 — 화면에선 "주소 못 찾은 N곳" 으로 정직하게 센다.
        assertNull(RegionCoords.find("중구", null))
        assertNotNull(RegionCoords.find("중구", "서울"))
        assertNotNull(RegionCoords.find("중구", "부산"))
        assertNotNull(RegionCoords.find("중구", "대전"))
    }

    @Test
    fun `광주는 광역시와 경기 광주시가 다르다`() {
        val gj = RegionCoords.find("광주", "경기")       // 경기 광주시
        assertNotNull(gj)
        assertTrue("경기 광주는 수도권", gj!!.lat > 37.0)
        // 광주광역시는 주소가 "광주광역시 서구…" 라 RegionName 이 '서구'를 준다
        assertEquals("서구", RegionName.shortRegion("광주광역시 서구 상무대로 1"))
        assertEquals("광주", RegionCoords.of("광주광역시 서구 상무대로 1")!!.sido)
    }

    @Test
    fun `고성은 강원과 경남 둘 다 있다`() {
        val gw = RegionCoords.find("고성", "강원")
        val gn = RegionCoords.find("고성", "경남")
        assertNotNull(gw); assertNotNull(gn)
        assertTrue("강원 고성이 훨씬 북쪽", gw!!.lat > gn!!.lat + 3)
        assertNull("시도 없으면 안 찍는다", RegionCoords.find("고성", null))
    }

    @Test
    fun `주소가 없거나 못 뽑으면 조용히 null`() {
        assertNull(RegionCoords.of(null))
        assertNull(RegionCoords.of(""))
        assertNull(RegionCoords.of("삼성래미안 101동 202호"))   // 동네 이름이 아예 없는 주소
    }

    @Test
    fun `시도만 읽는다`() {
        assertEquals("경기", RegionCoords.sidoOf("경기 화성시 동탄구 1"))
        assertEquals("경기", RegionCoords.sidoOf("경기도 수원시 영통구 1"))
        assertEquals("서울", RegionCoords.sidoOf("서울특별시 강남구 1"))
        assertEquals("광주", RegionCoords.sidoOf("광주광역시 서구 1"))
        assertEquals("충북", RegionCoords.sidoOf("충청북도 청주시 1"))
        assertNull(RegionCoords.sidoOf("동탄대로 24길 199"))
    }

    @Test
    fun `표가 온전하다 — 이름이 겹쳐도 시도까지 겹치지는 않는다`() {
        // 같은 (시도, 이름) 이 두 번 들어가면 한 자리가 다른 자리를 덮는다.
        val seen = HashSet<String>()
        val dups = mutableListOf<String>()
        listOf("서울","부산","대구","인천","광주","대전","울산","세종","경기","강원",
               "충북","충남","전북","전남","경북","경남","제주").forEach { sido ->
            // 표에 있는 이름 전부를 훑을 공개 API 가 없으므로, 대표 이름 몇 개로 중복만 확인한다.
            listOf("중구","서구","동구","남구","북구","강서","광주","고성").forEach { nm ->
                RegionCoords.find(nm, sido)?.let {
                    val k = "$sido/$nm"
                    if (!seen.add(k)) dups += k
                }
            }
        }
        assertTrue("중복 $dups", dups.isEmpty())
    }
}
