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
        /**
         * 🎥 들르는 곳들의 (lon, lat) — **구간을 화면에 담으려면** 양 끝이 어디인지 알아야 한다.
         *   비워두면 예전처럼 트럭을 정확히 따라간다(옛 동작 그대로).
         */
        stopsLL: List<Pair<Double, Double>> = emptyList(),
        /**
         * 얼마나 당겨서 따라붙을지.
         *   2.6 까지 당겼더니 먼 구간에서 트럭이 **1초에 화면 두 칸 반**을 지나가 휘딝거렸고,
         *   지도에 그려진 게 별로 없어 **벌판처럼** 보였다. (2026-09-25 단위 테스트가 잡음)
         */
        followZoom: Float = 2.0f,
        restZoom: Float = 1f,
        restPanX: Float = 0f,
        restPanY: Float = 0f
    ): Shot {
        // 마지막 현장에 닿기 **조금 전**부터 빠지기 시작한다 — 도착과 동시에 빠지면 도착이 안 보인다.
        val pullStart = (at.lastArriveFrac - 0.06f).coerceIn(0.35f, 0.96f)
        val g = if (t <= pullStart) 0f else ((t - pullStart) / (1f - pullStart)).coerceIn(0f, 1f)
        // 뒤로 갈수록 천천히 멈춘다(ease-out) — 뚝 서면 흔들린 것처럼 보인다.
        val e = 1f - (1f - g) * (1f - g) * (1f - g)

        // 🎥 **구간을 잡아놓고 지나가게 둔다.** (2026-10-01 사장님 "버벅이는데")
        //   전엔 트럭을 **정확히 가운데** 두려고 매 컷 지도를 밀었다 —
        //   지도엔 동네 이름과 도로선이 있어서, 매 컷 다시 그려지면 **글자가 떨린다.**
        //   컷을 늘려도 안 없어진다(24컷·누락 0 인데도 버벅여 보였다. 2026-09-30 실측).
        //   이제 한 구간을 가는 **내내 지도는 그 자리에 서고**, 현장에서 **쉬는 동안** 다음 화면으로 미끄러진다.
        val legShot = legFraming(b, at, stopsLL, followZoom)
        val zoom = legShot.zoom + (restZoom - legShot.zoom) * e
        val fx = legShot.panX
        val fy = legShot.panY
        // 📌 **끝까지 정가운데.** (2026-09-25 사장님 "확대를 해도 그 가운데가 유지되는거 맞지?")
        //   손가락으로 끌 땐 '지도를 잃지 말자'고 가장자리에서 잡아둔다.
        //   하지만 지도는 **전국이 다 그려져 있어** 더 밀어도 빈 데가 안 나온다.
        //   그래서 카메라는 안 잡는다 — 가장자리 동네(강서·동탄)에서도 트럭이 가운데다.
        return Shot(zoom, fx + (restPanX - fx) * e, fy + (restPanY - fy) * e)
    }

    /**
     * 🎥 **지금 구간을 화면에 담는 한 컷.** 구간이 바뀌는 순간에만 움직인다.
     *
     * 담는 것 = 앞 현장 · 다음 현장 **둘 다.** 그래야 「어디서 어디로 가는지」가 한 화면에 보인다.
     * 구간이 길면 저절로 덜 당겨지고(멀리서), 짧으면 더 당겨진다(가까이).
     *
     * ⚠️ **구간이 바뀌는 순간 뚝 끊기면 안 된다.** 현장에 닿아 쉬는 동안([MapRide.PAUSE])
     *    다음 화면으로 **미끄러져** 간다 — 그 사이에만 지도가 움직인다.
     * ⚠️ [stopsLL] 이 비면 예전처럼 트럭을 정확히 따라간다(옛 동작 보존).
     */
    private fun legFraming(
        b: Bounds, at: At, stopsLL: List<Pair<Double, Double>>, followZoom: Float
    ): Shot {
        if (stopsLL.size < 2) return trackTruck(b, at, followZoom)
        val i = at.arrived.coerceIn(0, stopsLL.size - 1)
        val j = (i + 1).coerceAtMost(stopsLL.size - 1)
        val now = frameOf(b, stopsLL[i], stopsLL[j], followZoom)
        // 막 도착했으면 **앞 구간 화면에서 지금 화면으로** 미끄러진다. 그 창이 쉬는 시간이다.
        val arriveT = at.arriveAt.getOrNull(i) ?: 0f
        val since = (at.nowT - arriveT).coerceAtLeast(0f)
        if (i == 0 || since >= PAUSE) return now
        val prev = frameOf(b, stopsLL[(i - 1).coerceAtLeast(0)], stopsLL[i], followZoom)
        val g = (since / PAUSE).coerceIn(0f, 1f)
        val e = 1f - (1f - g) * (1f - g) * (1f - g)   // 끝에서 천천히 — 뚝 서면 흔들린 것처럼 보인다
        return Shot(
            prev.zoom + (now.zoom - prev.zoom) * e,
            prev.panX + (now.panX - prev.panX) * e,
            prev.panY + (now.panY - prev.panY) * e
        )
    }

    /** 두 지점을 **둘 다** 담는 한 컷. */
    private fun frameOf(
        b: Bounds, a: Pair<Double, Double>, c: Pair<Double, Double>, followZoom: Float
    ): Shot {
        val midLon = (a.first + c.first) / 2
        val midLat = (a.second + c.second) / 2
        // 구간이 화면의 이만큼만 차지하게 — 가장자리에 붙으면 답답하다.
        val useLon = if (b.spanLon > 0) kotlin.math.abs(a.first - c.first) / b.spanLon else 0.0
        val useLat = if (b.spanLat > 0) kotlin.math.abs(a.second - c.second) / b.spanLat else 0.0
        val need = max(useLon, useLat) / 0.62
        // 너무 당기면 벌판처럼 보이고(2026-09-25), 너무 빠지면 트럭이 깨알이 된다.
        val zoom = if (need <= 0.0) followZoom else (1.0 / need).toFloat().coerceIn(1.0f, followZoom)
        val px = if (b.spanLon > 0) ((b.midLon - midLon) * zoom / b.spanLon).toFloat() else 0f
        val py = if (b.spanLat > 0) ((midLat - b.midLat) * zoom / b.spanLat).toFloat() else 0f
        return Shot(zoom, px, py)
    }

    /** 옛 동작 — 트럭을 정확히 가운데. [stopsLL] 을 안 줄 때만 쓴다. */
    private fun trackTruck(b: Bounds, at: At, followZoom: Float): Shot {
        val px = if (b.spanLon > 0) ((b.midLon - at.lon) * followZoom / b.spanLon).toFloat() else 0f
        val py = if (b.spanLat > 0) ((at.lat - b.midLat) * followZoom / b.spanLat).toFloat() else 0f
        return Shot(followZoom, px, py)
    }

    /** 당겨져 있을 땐 **동네 이름을 다 보여준다** — 안 그러면 지금 어디인지 알 수가 없다. */
    fun showAllNames(zoom: Float): Boolean = zoom > 1.6f
}
