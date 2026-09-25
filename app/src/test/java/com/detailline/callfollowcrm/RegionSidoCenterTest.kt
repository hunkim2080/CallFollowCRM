package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.util.RegionCoords
import com.detailline.callfollowcrm.util.RegionName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **동네 줄에 적힌 곳은 지도에도 찍혀야 한다.**
 *
 * 왜 (2026-09-25 사장님 "지도는 왜그러지.."):
 *   인증샷에 동네 줄은 「권선 · 분당 · 강서 · 관악 · **대구**」 였는데 **지도엔 대구가 없었다.**
 *   좌표표엔 대구의 구(중구·수성·달서…)만 있고 "대구" 라는 이름 자체가 없어서,
 *   주소가 구 없이 넓게 적힌 경우 이름은 "대구" 로 뽑히는데 자리를 못 찾았다.
 *   **글과 지도가 다른 말을 하면 둘 다 못 믿는다.**
 */
class RegionSidoCenterTest {

    @Test
    fun `구 없이 시·도만 적힌 주소도 자리를 찾는다`() {
        val spot = RegionCoords.of("대구시 어딘가")
        assertNotNull("대구만 적혀도 지도에 찍혀야 한다", spot)
        assertEquals("대구", spot!!.sido)
        // 대구 어딘가(중심)면 된다 — 동네까지는 몰라도 "대구에 갔다"는 맞다.
        assertTrue("위도가 대구 근처", spot.lat in 35.5..36.5)
        assertTrue("경도가 대구 근처", spot.lon in 128.0..129.2)
    }

    @Test
    fun `이름이 시·도로 뽑히는지부터 확인 — 이게 사고의 출발점이었다`() {
        // "대구광역시" 는 너무 넓어서 동네 이름으로 안 뽑는다(그건 그대로 옳다).
        assertEquals(null, RegionName.shortRegion("대구광역시"))
        // 문제가 된 모양은 이것 — 이름은 "대구" 로 뽑히는데 표엔 그 이름이 없었다.
        assertEquals("대구", RegionName.shortRegion("대구시 어딘가"))
    }

    @Test
    fun `구까지 있으면 예전처럼 그 구를 쓴다 — 시·도 중심으로 뭉개지 않는다`() {
        val spot = RegionCoords.of("대구광역시 수성구 범어동")
        assertNotNull(spot)
        assertEquals("수성", spot!!.name)
    }

    @Test
    fun `다른 광역시도 마찬가지`() {
        // ⚠ "광주시" 는 **경기도 광주시**가 맞다 — 여기 넣으면 안 된다(실제로 그렇게 잡힌다).
        listOf("부산시 어딘가" to "부산", "울산시 어딘가" to "울산", "대전시 어딘가" to "대전").forEach { (addr, sido) ->
            val s = RegionCoords.of(addr)
            assertNotNull("$addr 가 자리를 못 찾았다", s)
            assertEquals(sido, s!!.sido)
        }
    }

    @Test
    fun `엉뚱한 글자는 여전히 못 찾는다 — 아무 데나 찍지 않는다`() {
        assertEquals(null, RegionCoords.of(""))
        assertEquals(null, RegionCoords.of(null))
    }

    @Test
    fun `수도권은 예전 그대로`() {
        assertEquals("권선", RegionCoords.of("경기도 수원시 권선구")?.name)
        assertEquals("강서", RegionCoords.of("서울특별시 강서구 화곡동")?.name)
        assertEquals("분당", RegionCoords.of("경기도 성남시 분당구")?.name)
    }
}
