package com.detailline.callfollowcrm.util

import android.content.Context

/**
 * **동(洞)까지 찍는 좌표.** (2026-09-25 사장님 "동. 까지 뭔가 측정 가능했으면 좋겠어")
 *
 * 왜 필요한가 — [RegionCoords] 는 **구까지만** 안다.
 *   수원만 다니는 사장님이 조원동·우만동·인계동을 다녀도
 *   *조원동=장안구 · 우만동=팔달구 · 인계동=팔달구* 라 점이 **둘**로 뭉치고,
 *   우만동과 인계동은 **같은 점**이 된다. 세 집을 다녀왔는데 지도는 두 곳만 안다.
 *
 * 표는 `assets/dong_coords.txt` — 통계청 행정동 경계에서 뽑은 **중심점 3,520개**(전국).
 *   한 줄: `시도|시군구|동이름|위도|경도`  (예: `경기|수원팔달|인계동|37.2687|127.0298`)
 *   144KB. **인터넷도 지도 열쇠도 안 쓴다** — 비행기 모드에서도 찍힌다.
 *
 * ⚠️ 주소는 **법정동**("청운동"), 표는 **행정동**("청운효자동") 이라 이름이 다를 때가 있다.
 *   그래서 ①딱 맞는 이름 → ②표의 이름이 주소 동으로 **시작**하는 것(조원동→조원1동)
 *   → ③번호만 다른 것 → 순으로 찾는다. 그래도 없으면 부르는 쪽이 구 좌표로 물러난다.
 */
object DongCoords {

    /** 표 한 줄. */
    data class Row(val sido: String, val sgg: String, val dong: String, val lat: Double, val lon: Double)

    @Volatile private var rows: List<Row>? = null
    /** 동 이름 앞자리(숫자 뗀 줄기) → 줄들. 찾을 때 전체를 훑지 않는다. */
    @Volatile private var byStem: Map<String, List<Row>> = emptyMap()

    /**
     * 표를 한 번만 읽는다. 앱 뜰 때가 아니라 **지도를 처음 그릴 때** 부른다 —
     * 안 쓰는 사람에게 144KB 를 읽히지 않는다.
     */
    fun ensure(ctx: Context) {
        if (rows != null) return
        synchronized(this) {
            if (rows != null) return
            val out = ArrayList<Row>(3600)
            runCatching {
                ctx.assets.open("dong_coords.txt").bufferedReader().useLines { seq ->
                    for (line in seq) {
                        val p = line.split('|')
                        if (p.size != 5) continue
                        val lat = p[3].toDoubleOrNull() ?: continue
                        val lon = p[4].toDoubleOrNull() ?: continue
                        out.add(Row(p[0], p[1], p[2], lat, lon))
                    }
                }
            }
            rows = out
            byStem = out.groupBy { stemOf(it.dong) }
        }
    }

    /**
     * 동 이름에서 **줄기**만. "조원1동"·"정자3동" → "조원"·"정자".
     *   주소엔 "조원동" 이라 적히는데 표엔 "조원1동"·"조원2동" 으로 갈려 있다.
     */
    internal fun stemOf(dong: String): String {
        // ⚠️ **끝 글자 하나만** 뗀다. 여러 개 떼면 「우면동」이 「우」가 되어
        //   부산 해운대 「우동」과 같은 줄기가 된다 — 서초 현장이 부산에 찍혔다.
        //   (2026-09-25 사장님 "우동이란곳도 없는데 왜 이렇게 나오는거지")
        val one = if (dong.isNotEmpty() && dong.last() in "동읍면가") dong.dropLast(1) else dong
        return one.trimEnd('0', '1', '2', '3', '4', '5', '6', '7', '8', '9').ifBlank { dong }
    }

    /**
     * 주소 → 동 좌표. 못 찾으면 null(부르는 쪽이 구 좌표로 물러난다).
     *
     * @param address 손님 주소 전체.
     */
    fun of(ctx: Context, address: String?): RegionCoords.Spot? {
        val a = address?.trim().orEmpty()
        if (a.isBlank()) return null
        ensure(ctx)
        val dong = RegionName.dongOf(a) ?: return null
        val cand = byStem[stemOf(dong)].orEmpty()
        if (cand.isEmpty()) return null
        // 같은 이름이 여러 시군구에 있으면(236개) **주소에 적힌 시·군·구**로 가른다.
        val narrowed = if (cand.size == 1) cand
            else cand.filter { sggMatches(a, it.sgg) }.ifEmpty { cand }
        // 번호로 갈린 동(조원1동·조원2동)은 **제일 앞 것**을 쓴다 — 어차피 같은 동네다.
        val best = narrowed.minByOrNull { it.dong } ?: return null
        return RegionCoords.Spot(name = displayOf(best.dong), sido = best.sido, lat = best.lat, lon = best.lon)
    }

    /** 표의 시군구("수원팔달")를 주소가 품고 있나 — "수원시 팔달구 인계동" 이면 수원·팔달 둘 다 나온다. */
    private fun sggMatches(address: String, sgg: String): Boolean {
        if (sgg.isBlank()) return false
        // 두 글자씩 끊어 전부 들어 있는지 본다("수원팔달" → "수원","팔달").
        var i = 0
        while (i + 2 <= sgg.length) {
            if (!address.contains(sgg.substring(i, i + 2))) return false
            i += 2
        }
        return true
    }

    /** 화면에 쓸 이름 — 번호는 뗀다. "조원1동" → "조원동". */
    private fun displayOf(dong: String): String {
        val stem = stemOf(dong)
        val tail = dong.lastOrNull()
        return if (tail != null && tail in "동읍면가") stem + tail else dong
    }
}
