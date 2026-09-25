package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.util.MapRide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚛🎥 **트럭 자리와 카메라** — 눈으로는 10초 영상 안에서 스쳐 지나가 못 보는 것들.
 *
 * 2026-09-25 사장님 "영상이 트럭을 중심으로 움직이면 어떨까".
 * 카메라가 트럭을 따라가는데, 트럭 자리를 **따로 계산하면 어긋난다**.
 * 그래서 셈은 [MapRide] 한 곳에 두고, 그게 맞는지는 여기서 본다.
 */
class MapRideTest {

    /** 9월 실제 동선 비슷하게 — 강서 → 영통 → 덕양 → 강동 → 동탄. */
    private val towns = listOf(
        126.849 to 37.551,   // 강서
        127.072 to 37.259,   // 영통
        126.832 to 37.658,   // 덕양
        127.147 to 37.530,   // 강동
        127.072 to 37.201    // 동탄
    )

    private fun way(): FloatArray =
        FloatArray(towns.size * 2) {
            if (it % 2 == 0) towns[it / 2].first.toFloat() else towns[it / 2].second.toFloat()
        }

    private fun stops() = IntArray(towns.size) { it }

    @Test
    fun `트럭은 뒤로 가지 않는다`() {
        var prev = -1f
        for (i in 0..200) {
            val at = MapRide.at(way(), stops(), i / 200f)
            assertTrue("t=${i / 200f} 에서 되감김: $prev → ${at.frac}", at.frac >= prev - 1e-4f)
            prev = at.frac
        }
        assertEquals("끝엔 길 끝까지", 1f, prev, 1e-3f)
    }

    @Test
    fun `도착은 순서대로 늘고 끝엔 마지막 현장까지`() {
        var prev = 0
        for (i in 0..200) {
            val at = MapRide.at(way(), stops(), i / 200f)
            assertTrue("도착이 거꾸로: $prev → ${at.arrived}", at.arrived >= prev)
            prev = at.arrived
        }
        assertEquals("마지막 현장까지 도착해야 사진이 그걸로 바뀐다", towns.size - 1, prev)
    }

    @Test
    fun `마지막 도착은 끝나기 전이어야 한다`() {
        val at = MapRide.at(way(), stops(), 1f)
        assertTrue("마지막 도착이 ${at.lastArriveFrac} — 너무 끝이라 사진이 스친다",
            at.lastArriveFrac in 0.5f..0.97f)
    }

    @Test
    fun `따라가는 동안 트럭이 화면 한가운데 있다`() {
        val b = MapRide.bounds(towns)
        // 카메라가 가장자리에 붙지 않는 중간 구간에서만 본다(가장자리는 일부러 잡아둔다).
        for (i in 30..60) {
            val t = i / 100f
            val at = MapRide.at(way(), stops(), t)
            val s = MapRide.follow(b, at, t)
            // 지도가 쓰는 식 그대로 되짚는다: cLon = mid - panX*span/zoom
            val cLon = b.midLon - s.panX * b.spanLon / s.zoom
            val cLat = b.midLat + s.panY * b.spanLat / s.zoom
            val offX = Math.abs(cLon - at.lon) / (b.spanLon / s.zoom)
            val offY = Math.abs(cLat - at.lat) / (b.spanLat / s.zoom)
            assertTrue("t=$t 에서 트럭이 화면 밖으로 밀렸다 (가로 ${"%.2f".format(offX)})", offX < 0.5)
            assertTrue("t=$t 에서 트럭이 화면 밖으로 밀렸다 (세로 ${"%.2f".format(offY)})", offY < 0.5)
        }
    }

    @Test
    fun `카메라는 지도 밖으로 나가지 않는다`() {
        val b = MapRide.bounds(towns)
        for (i in 0..100) {
            val t = i / 100f
            val s = MapRide.follow(b, MapRide.at(way(), stops(), t), t)
            val lim = (s.zoom - 1f) / 2f + 1e-3f
            assertTrue("t=$t panX=${s.panX} 가 한계 $lim 를 넘어 빈 데가 보인다",
                Math.abs(s.panX) <= lim)
            assertTrue("t=$t panY=${s.panY} 가 한계 $lim 를 넘어 빈 데가 보인다",
                Math.abs(s.panY) <= lim)
        }
    }

    @Test
    fun `끝엔 사장님이 맞춰둔 화면으로 돌아온다`() {
        val b = MapRide.bounds(towns)
        val s = MapRide.follow(b, MapRide.at(way(), stops(), 1f), 1f,
            restZoom = 1.4f, restPanX = 0.1f, restPanY = -0.05f)
        assertEquals(1.4f, s.zoom, 1e-3f)
        assertEquals(0.1f, s.panX, 1e-3f)
        assertEquals(-0.05f, s.panY, 1e-3f)
    }

    @Test
    fun `따라갈 땐 확대되어 있다`() {
        val b = MapRide.bounds(towns)
        val s = MapRide.follow(b, MapRide.at(way(), stops(), 0.3f), 0.3f)
        assertTrue("중간엔 당겨져 있어야 골목이 보인다 (zoom=${s.zoom})", s.zoom > 2f)
        assertTrue("당겨져 있으면 동네 이름을 다 보여준다", MapRide.showAllNames(s.zoom))
    }

    @Test
    fun `현장이 하나뿐이어도 터지지 않는다`() {
        val one = listOf(126.9 to 37.5)
        val w = FloatArray(2) { if (it == 0) 126.9f else 37.5f }
        val at = MapRide.at(w, IntArray(1) { 0 }, 0.5f)
        assertEquals(0, at.arrived)
        val s = MapRide.follow(MapRide.bounds(one), at, 0.5f)
        assertTrue(s.zoom > 0f)
    }
}
