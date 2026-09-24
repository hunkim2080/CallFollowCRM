package com.detailline.callfollowcrm.util

import android.content.Context

/**
 * **진짜 지도 좌표.** (2026-09-25 사장님 "지금처럼 우리가 그리되, 도로 좌표를 진짜로 바꾸자")
 *
 * 전엔 해안선도 도로도 **손으로 눈대중 찍은 선**이었다. 사장님이 실제 타는 길과 다르면
 * "이거 아닌데" 가 된다.
 *
 * 원본: **Natural Earth**(naturalearthdata.com) — **퍼블릭 도메인**.
 *   출처 표기 의무도, 열쇠도, 쿼터도, 인터넷도 필요 없다. 그림에 남의 로고가 안 박힌다.
 *   (OpenStreetMap 은 ODbL 이라 "© OpenStreetMap contributors" 를 그림에 넣어야 해서 못 쓴다)
 *
 * 파일: `assets/map_geo.txt` — 한 줄이 조각 하나. `<갈래>|lon,lat,lon,lat,…`
 *   L=땅(섬 포함 52) · U=시가지(187) · R=큰길(207) · V=강(3) · A=시·도 경계(70). 합쳐 8,686점 · 143KB.
 *
 * ⚠ 동 경계는 안 넣었다(1.5MB). 한 도시 안에서만 보이는 선이라 값이 안 맞는다.
 *   동 **이름**은 [DongCoords] 가 쓰는 `dong_coords.txt` 로 찍는다.
 */
object MapGeo {

    /** 한 조각 — [lon, lat, lon, lat, …] 납작한 배열. 그리기가 제일 빠른 모양. */
    class Data(
        val land: List<FloatArray>,
        val urban: List<FloatArray>,
        val roads: List<FloatArray>,
        val rivers: List<FloatArray>,
        val admin: List<FloatArray>
    ) {
        val isEmpty: Boolean get() = land.isEmpty()
    }

    private val EMPTY = Data(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Volatile private var cached: Data? = null

    /**
     * 한 번만 읽는다. 앱 뜰 때가 아니라 **지도를 처음 그릴 때** 부른다 —
     * 지도를 안 보는 사람에게 143KB 를 읽히지 않는다.
     */
    fun load(ctx: Context): Data {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val land = ArrayList<FloatArray>(64)
            val urban = ArrayList<FloatArray>(192)
            val roads = ArrayList<FloatArray>(224)
            val rivers = ArrayList<FloatArray>(8)
            val admin = ArrayList<FloatArray>(80)
            runCatching {
                ctx.assets.open("map_geo.txt").bufferedReader().useLines { seq ->
                    for (line in seq) {
                        if (line.length < 4) continue
                        val bar = line.indexOf('|')
                        if (bar != 1) continue
                        val nums = line.substring(bar + 1).split(',')
                        if (nums.size < 4) continue
                        val arr = FloatArray(nums.size)
                        var ok = true
                        for (i in nums.indices) {
                            val v = nums[i].toFloatOrNull()
                            if (v == null) { ok = false; break }
                            arr[i] = v
                        }
                        if (!ok) continue
                        when (line[0]) {
                            'L' -> land.add(arr)
                            'U' -> urban.add(arr)
                            'R' -> roads.add(arr)
                            'V' -> rivers.add(arr)
                            'A' -> admin.add(arr)
                        }
                    }
                }
            }
            val d = if (land.isEmpty()) EMPTY else Data(land, urban, roads, rivers, admin)
            cached = d
            return d
        }
    }

    // ── 트럭이 달릴 **길 그물** ─────────────────────────────────────────
    //   큰길 꼭지점을 점으로, 이웃끼리 선으로 이어 그물을 만든다.
    //   가까운 점은 하나로 합쳐야 갈라진 길이 교차로에서 실제로 만난다.

    private const val SNAP = 0.010            // 이 안이면 같은 길목 (≈1km)
    private const val BRIDGE = 0.022          // 스치듯 가까운 길목은 이어준다
    private const val KX = 0.809017           // cos(36°) — 경도는 위도에 따라 좁아진다

    private class Net(val lon: FloatArray, val lat: FloatArray, val adj: Array<IntArray>)

    @Volatile private var net: Net? = null

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Double {
        val dx = (ax - bx) * KX
        val dy = (ay - by).toDouble()
        return Math.hypot(dx, dy)
    }

    private fun buildNet(d: Data): Net {
        val lon = ArrayList<Float>(1200)
        val lat = ArrayList<Float>(1200)
        val grid = HashMap<Long, Int>(2048)
        val edges = ArrayList<HashSet<Int>>(1200)
        fun nodeAt(x: Float, y: Float): Int {
            val key = (Math.round(x / SNAP).toLong() shl 32) or (Math.round(y / SNAP).toLong() and 0xFFFFFFFFL)
            grid[key]?.let { return it }
            val id = lon.size
            lon.add(x); lat.add(y); edges.add(HashSet()); grid[key] = id
            return id
        }
        for (r in d.roads) {
            var prev = -1
            var i = 0
            while (i + 1 < r.size) {
                val id = nodeAt(r[i], r[i + 1])
                if (prev >= 0 && prev != id) { edges[prev].add(id); edges[id].add(prev) }
                prev = id
                i += 2
            }
        }
        // 조각난 그물을 한 덩어리로 — 안 그러면 길을 못 찾아 전부 직선이 된다.
        val n = lon.size
        for (i in 0 until n) for (j in i + 1 until n) {
            if (dist(lon[i], lat[i], lon[j], lat[j]) < BRIDGE) { edges[i].add(j); edges[j].add(i) }
        }
        return Net(lon.toFloatArray(), lat.toFloatArray(), Array(n) { edges[it].toIntArray() })
    }

    private fun netOf(ctx: Context): Net {
        net?.let { return it }
        synchronized(this) {
            net?.let { return it }
            val n = buildNet(load(ctx))
            net = n
            return n
        }
    }

    /**
     * 현장 → 현장 **길 위 경로**. [lon, lat, …] 로 돌려준다(양 끝 현장 포함).
     *
     * ⚠ 길이 없는 동네(도시 안)에서 큰길까지 나갔다 오는 그림이 나오면 안 된다.
     *   그래서 **집 → 큰길 → 큰길 → 집** 을 전부 재서, 곧게 잰 거리의 1.45배를 넘으면
     *   그 구간은 **곧게 잇는다**. (2026-09-25 사장님 "안 다닌 길도 왜 갔다가 다른 동으로 가는 거지?")
     */
    fun routeBetween(ctx: Context, aLon: Double, aLat: Double, bLon: Double, bLat: Double): FloatArray? {
        val g = netOf(ctx)
        if (g.lon.isEmpty()) return null
        val from = nearest(g, aLon, aLat)
        val to = nearest(g, bLon, bLat)
        if (from < 0 || to < 0 || from == to) return null
        val path = shortest(g, from, to) ?: return null
        if (path.size < 2) return null
        var onRoad = 0.0
        for (i in 0 until path.size - 1) {
            onRoad += dist(g.lon[path[i]], g.lat[path[i]], g.lon[path[i + 1]], g.lat[path[i + 1]])
        }
        val full = dist(aLon.toFloat(), aLat.toFloat(), g.lon[path.first()], g.lat[path.first()]) +
            onRoad +
            dist(g.lon[path.last()], g.lat[path.last()], bLon.toFloat(), bLat.toFloat())
        val straight = dist(aLon.toFloat(), aLat.toFloat(), bLon.toFloat(), bLat.toFloat())
        if (straight <= 0.0001 || full > straight * 1.45) return null
        val out = FloatArray(path.size * 2)
        for (i in path.indices) { out[i * 2] = g.lon[path[i]]; out[i * 2 + 1] = g.lat[path[i]] }
        return out
    }

    private fun nearest(g: Net, lon: Double, lat: Double): Int {
        var best = -1
        var bd = Double.MAX_VALUE
        for (i in g.lon.indices) {
            val d = dist(g.lon[i], g.lat[i], lon.toFloat(), lat.toFloat())
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    /** 제일 짧은 길 (다익스트라). 못 찾으면 null. */
    private fun shortest(g: Net, from: Int, to: Int): IntArray? {
        val n = g.lon.size
        val dist0 = DoubleArray(n) { Double.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        val seen = BooleanArray(n)
        dist0[from] = 0.0
        while (true) {
            var u = -1
            var bd = Double.MAX_VALUE
            for (i in 0 until n) if (!seen[i] && dist0[i] < bd) { bd = dist0[i]; u = i }
            if (u < 0 || u == to) break
            seen[u] = true
            for (v in g.adj[u]) {
                val w = dist0[u] + dist(g.lon[u], g.lat[u], g.lon[v], g.lat[v])
                if (w < dist0[v]) { dist0[v] = w; prev[v] = u }
            }
        }
        if (dist0[to] == Double.MAX_VALUE) return null
        val rev = ArrayList<Int>(32)
        var cur = to
        while (cur >= 0) { rev.add(cur); cur = prev[cur] }
        rev.reverse()
        return rev.toIntArray()
    }
}

/**
 * **지도 색 한 벌.** (2026-09-25)
 *
 * 앱 화면·인증샷·영상이 **같은 색**을 써야 "매달 올려도 한 시리즈" 가 된다.
 * 전엔 세 곳이 각자 다른 회색을 썼다(앱 #EEF0F3 / 인증샷 #D9D3C4 / 프로토 #FBFAF7).
 * 지도 고유의 색이라 화면 디자인 토큰이 아니라 **지도 옆에** 둔다.
 */
object MapPalette {
    /** 바다 — 이게 있어야 육지가 육지로 보인다. */
    const val SEA = 0xFFD9E4EF
    /** 땅 */
    const val LAND = 0xFFF8F7F3
    /** 해안선·경계 */
    const val EDGE = 0xFFB9C4D0
    /** 해안 바깥 후광 */
    const val GLOW = 0x3896ACC2
    /** 시가지 얼룩 */
    const val BUILT = 0x33A89F86
    /** 큰길 테두리 / 속 */
    const val ROAD_EDGE = 0xFFD9BE72
    const val ROAD_FILL = 0xFFF0CE7E
    /** 강 */
    const val RIVER = 0xFFA6C4E6
}
