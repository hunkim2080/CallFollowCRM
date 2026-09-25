package com.detailline.callfollowcrm.util

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * 🚛 **트럭이 지금 어디쯤인가** — 지도와 카메라가 **같이 보는 한 벌**.
 *
 * 전엔 이 셈이 [com.detailline.callfollowcrm.presentation.component.drawRegionMap] 안에만 있었다.
 * 영상 카메라가 트럭을 따라가려면 **그리기 전에** 트럭 자리를 알아야 하는데,
 * 밖에서 또 계산하면 카메라와 트럭이 어긋난다 — 사장님이 여러 번 짚은 "같은 걸 두 벌" 이다.
 * 그래서 화면도 좌표도 모르는 **순수한 셈**만 여기로 뺐다 (단위 테스트가 대신 봐준다).
 */
object MapRide {

    /** 경도는 위도에 따라 좁아진다(한국 ≈ cos36°). 지도 그리는 쪽과 **같은 값**이어야 한다. */
    private val KX = cos(Math.toRadians(36.0)).toFloat()

    /** 현장마다 **반 초쯤 멈춘다** — 등속으로 흐르면 '날아가는' 느낌이라 일하는 것처럼 안 보인다. */
    private const val PAUSE = 0.55f

    /** 보여줄 범위. 확대·이동을 **먹이기 전**의 저절로 맞춘 틀. */
    class Bounds(val minLon: Double, val maxLon: Double, val minLat: Double, val maxLat: Double) {
        val spanLon: Double get() = maxLon - minLon
        val spanLat: Double get() = maxLat - minLat
        val midLon: Double get() = (minLon + maxLon) / 2
        val midLat: Double get() = (minLat + maxLat) / 2
    }

    /**
     * 다녀온 곳 + 여유. 전국을 다니면 전국, 동네만 다니면 그 언저리.
     *   ⚠️ 지도가 쓰는 틀과 **같아야** 카메라가 엉뚱한 데를 비추지 않는다 —
     *      그래서 지도도 이 함수를 부른다.
     */
    fun bounds(pts: List<Pair<Double, Double>>): Bounds {
        if (pts.isEmpty()) return Bounds(126.5, 127.5, 37.0, 38.0)
        var minLon = pts.minOf { it.first }; var maxLon = pts.maxOf { it.first }
        var minLat = pts.minOf { it.second }; var maxLat = pts.maxOf { it.second }
        // 여유. 그리고 **너무 확대하지 않는다** — 확대가 심하면 해안선이 화면 밖으로 나가
        //   한국처럼 안 보이고 각진 회색 덩어리가 된다.
        val padLon = max((maxLon - minLon) * 0.35, 0.03)
        val padLat = max((maxLat - minLat) * 0.35, 0.024)
        minLon -= padLon; maxLon += padLon; minLat -= padLat; maxLat += padLat
        // 최소 폭 — 점 하나가 화면을 다 먹지 않게.
        val minLonSpan = 0.15; val minLatSpan = 0.126
        if (maxLon - minLon < minLonSpan) {
            val c = (maxLon + minLon) / 2; minLon = c - minLonSpan / 2; maxLon = c + minLonSpan / 2
        }
        if (maxLat - minLat < minLatSpan) {
            val c = (maxLat + minLat) / 2; minLat = c - minLatSpan / 2; maxLat = c + minLatSpan / 2
        }
        return Bounds(minLon, maxLon, minLat, maxLat)
    }

    /**
     * 그 순간의 주행 상태.
     *
     * @param frac 전체 길 중 **몇 %까지 왔나**(0~1). 화면 길이로 곱하면 픽셀이 된다
     *             — 화면 크기·확대와 무관하게 같은 값이라 여기서 셀 수 있다.
     * @param arrived 지금 **몇 번째 현장까지** 왔나(0부터).
     * @param nowT 속 시계(초). 점이 튀고 지폐가 올라오는 걸 이걸로 맞춘다.
     * @param arriveAt 현장마다 **도착 시각**(초).
     * @param lastArriveFrac 마지막 현장 도착이 **전체의 몇 지점**인가(0~1). 카메라가 빠질 때를 정한다.
     */
    class At(
        val frac: Float,
        val arrived: Int,
        val nowT: Float,
        val arriveAt: FloatArray,
        val lastArriveFrac: Float,
        val lon: Float,
        val lat: Float
    )

    /**
     * [way] 는 납작한 `[lon,lat,lon,lat,…]`, [stops] 는 **현장이 그 길 위 몇 번째 점인지**.
     *   ([com.detailline.callfollowcrm.util.MapGeo.Trip] 이 그대로 주는 모양)
     */
    fun at(way: FloatArray, stops: IntArray, progress: Float): At {
        val n = way.size / 2
        if (n < 1) return At(0f, 0, 0f, FloatArray(0), 1f, 0f, 0f)
        val p = progress.coerceIn(0f, 1f)

        val segLen = FloatArray(max(0, n - 1))
        var totalLen = 0f
        for (i in 0 until n - 1) {
            val dx = (way[(i + 1) * 2] - way[i * 2]) * KX
            val dy = way[(i + 1) * 2 + 1] - way[i * 2 + 1]
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            segLen[i] = d; totalLen += d
        }

        val arriveAt = FloatArray(stops.size)
        var frac: Float
        var arrived: Int
        var nowT = 0f
        var lastArriveFrac = 1f

        if (n >= 2 && stops.size >= 2) {
            // 구간마다 길이에 맞춰 시간을 준다 — 먼 길은 오래, 가까운 길은 짧게.
            val legs = FloatArray(stops.size - 1)
            var maxLeg = 1f
            for (s in 0 until stops.size - 1) {
                var L = 0f
                for (i in stops[s] until min(stops[s + 1], segLen.size)) L += segLen[i]
                legs[s] = L; if (L > maxLeg) maxLeg = L
            }
            val durs = FloatArray(legs.size) { 0.8f + 1.4f * (legs[it] / maxLeg) }
            var totalT = PAUSE * legs.size
            for (d in durs) totalT += d
            var acc = 0f
            for (s in legs.indices) { acc += durs[s]; arriveAt[s + 1] = acc; acc += PAUSE }
            lastArriveFrac = if (totalT > 0f) arriveAt[arriveAt.size - 1] / totalT else 1f
            nowT = totalT * p

            var cur = legs.size
            var u = 0f
            var t0 = 0f
            for (s in legs.indices) {
                if (nowT < t0 + durs[s]) { cur = s; u = (nowT - t0) / durs[s]; break }
                t0 += durs[s]
                if (nowT < t0 + PAUSE) { cur = s; u = 1f; break }
                t0 += PAUSE
            }
            val e = if (u < .5f) 2f * u * u
                else 1f - Math.pow((-2f * u + 2f).toDouble(), 3.0).toFloat() / 2f
            var base = 0f
            for (i in 0 until min(stops[min(cur, stops.size - 1)], segLen.size)) base += segLen[i]
            val legLen = if (cur < legs.size) legs[cur] else 0f
            val travelled = if (cur >= legs.size) totalLen else base + legLen * e
            frac = if (totalLen > 0f) (travelled / totalLen).coerceIn(0f, 1f) else 1f
            arrived = 0
            for (i in stops.indices) if (nowT >= arriveAt[i]) arrived = i
        } else {
            frac = p
            arrived = max(0, stops.size - 1)
        }

        // 트럭 자리 — 길 위를 [frac] 만큼 걸어간 지점.
        var want = totalLen * frac
        var lon = way[0]; var lat = way[1]
        for (i in 0 until n - 1) {
            if (want <= segLen[i] || i == n - 2) {
                val f = if (segLen[i] > 0f) (want / segLen[i]).coerceIn(0f, 1f) else 0f
                lon = way[i * 2] + (way[(i + 1) * 2] - way[i * 2]) * f
                lat = way[i * 2 + 1] + (way[(i + 1) * 2 + 1] - way[i * 2 + 1]) * f
                break
            }
            want -= segLen[i]
        }
        return At(frac, arrived, nowT, arriveAt, lastArriveFrac, lon, lat)
    }

    /** 카메라 한 컷 — 얼마나 당기고(zoom) 어디로 옮길지(panX/panY). */
    class Shot(val zoom: Float, val panX: Float, val panY: Float)

    /**
     * 🎥 **트럭을 따라가다 끝에 쭉 빠진다.** (2026-09-25 사장님이 고른 안)
     *
     * 수도권 전체를 멀리서 보여주면 트럭이 깨알만 해서 안 움직이는 것처럼 보인다.
     * 따라붙으면 골목이 보이고 속도가 느껴진다. 대신 **"한 달에 이만큼 다녔다"** 는
     * 전체 그림이 이 영상의 핵심이라, 마지막 현장에 닿으면 카메라가 빠지면서 다 보여준다.
     *
     * 빠진 뒤 자리는 [restZoom]/[restPanX]/[restPanY] — **사장님이 지도에서 손가락으로 맞춰둔 그 화면**이다.
     * (따라가기가 그 맞춰둔 틀을 잡아먹지 않게)
     */
    fun follow(
        b: Bounds,
        at: At,
        t: Float,
        followZoom: Float = 2.6f,
        restZoom: Float = 1f,
        restPanX: Float = 0f,
        restPanY: Float = 0f
    ): Shot {
        // 마지막 현장에 닿기 **조금 전**부터 빠지기 시작한다 — 도착과 동시에 빠지면 도착이 안 보인다.
        val pullStart = (at.lastArriveFrac - 0.06f).coerceIn(0.35f, 0.96f)
        val g = if (t <= pullStart) 0f else ((t - pullStart) / (1f - pullStart)).coerceIn(0f, 1f)
        // 뒤로 갈수록 천천히 멈춘다(ease-out) — 뚝 서면 흔들린 것처럼 보인다.
        val e = 1f - (1f - g) * (1f - g) * (1f - g)

        val zoom = followZoom + (restZoom - followZoom) * e
        // 트럭을 화면 한가운데로: cLon = mid - panX*span/zoom  →  panX = (mid - lon)*zoom/span
        val fx = if (b.spanLon > 0) ((b.midLon - at.lon) * zoom / b.spanLon).toFloat() else 0f
        val fy = if (b.spanLat > 0) ((at.lat - b.midLat) * zoom / b.spanLat).toFloat() else 0f
        // 가장자리 밖(바다·여백)까지 따라가지 않는다 — 화면에 빈 데가 생긴다.
        val lim = ((zoom - 1f) / 2f).coerceAtLeast(0f)
        val cx = fx.coerceIn(-lim, lim)
        val cy = fy.coerceIn(-lim, lim)
        return Shot(zoom, cx + (restPanX - cx) * e, cy + (restPanY - cy) * e)
    }

    /** 당겨져 있을 땐 **동네 이름을 다 보여준다** — 안 그러면 지금 어디인지 알 수가 없다. */
    fun showAllNames(zoom: Float): Boolean = zoom > 1.6f
}
