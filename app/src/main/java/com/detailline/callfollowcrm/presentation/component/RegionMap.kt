package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * **다녀온 동네 지도.** 점 하나 = 동네 하나, 점 크기 = 몇 번 갔나. (2026-09-24 사장님)
 *
 * 왜 우리가 직접 그리나(구글·카카오 지도 대신):
 *   · **열쇠도 결제도 인터넷도 필요 없다.** 비행기 모드에서도 그려진다.
 *   · 공유 그림에 **남의 회사 로고가 안 박힌다.**
 *   · 우리 색으로 칠해서, 매달 올려도 **한 시리즈로 보인다.**
 *
 * 왜 면을 안 칠하고 점을 찍나: 화성시는 강서구보다 **20배쯤 넓다.**
 *   면을 칠하면 **화성 3집이 강서 14집보다 커 보인다** — 그림이 거짓말을 한다.
 *   점은 크기를 우리가 정하니 **많이 간 동네가 제일 큰 점**이 되어 사실대로 읽힌다.
 *
 * 틀은 **다녀온 곳이 다 들어가는 만큼만** 잡는다. 동네 몇 곳만 다니는 사장님에게
 * 남한 전도를 보여주면 **왼쪽 위에 점이 뭉친 빈 지도**가 된다.
 */
@Composable
fun RegionMap(
    spots: List<RegionDot>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 190.dp
) {
    if (spots.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val line = AppTheme.colors.line
    val landC = AppTheme.colors.neutralBg
    val dotC = AppTheme.colors.primary
    val labelC = AppTheme.colors.textSub
    val riverC = AppTheme.colors.primary.copy(alpha = 0.30f)
    // 글자 크기도 디자인 시스템에서 가져온다(손으로 적지 않는다).
    val labelStyle = AppType.micro
    // 이름표는 많이 간 곳 넷만 — 여덟 개가 넘으면 지도가 아니라 표가 된다.
    val named = remember(spots) {
        spots.sortedByDescending { it.count }.take(4).map { it.name }.toSet()
    }
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            drawRegionMap(spots, named, measurer, landC, line, dotC, labelC, labelStyle, riverC)
        }
    }
}

/** 지도에 찍을 점 하나. */
data class RegionDot(val name: String, val lat: Double, val lon: Double, val count: Int)

/**
 * 남한 해안선 — 위경도 그대로. 점과 **같은 투영**을 쓰므로 어긋날 수 없다.
 *   (동해안은 곧고 남해안은 들쭉날쭉한 실제 모양. 섬은 제주만.)
 */
private val KOREA = doubleArrayOf(
    // 동해안 — 곧다
    128.35, 38.60, 128.50, 38.35, 128.60, 38.20, 128.72, 38.05, 128.85, 37.90,
    128.95, 37.75, 129.05, 37.62, 129.12, 37.52, 129.28, 37.25, 129.35, 37.10,
    129.40, 36.99, 129.43, 36.75, 129.42, 36.41, 129.47, 36.20, 129.57, 36.08,
    129.48, 35.85, 129.42, 35.65, 129.42, 35.50, 129.30, 35.30, 129.18, 35.17,
    129.10, 35.10,
    // 남해안 — 들쭉날쭉(리아스식)
    128.95, 35.05, 128.80, 35.00, 128.70, 34.95, 128.62, 34.88, 128.55, 34.82,
    128.42, 34.78, 128.25, 34.85, 128.10, 34.82, 127.92, 34.78, 127.85, 34.70,
    127.75, 34.62, 127.60, 34.72, 127.45, 34.60, 127.35, 34.48, 127.15, 34.55,
    126.95, 34.45, 126.75, 34.31, 126.60, 34.35, 126.52, 34.29,
    // 서해안 — 갯벌·만이 깊게 파인다
    126.45, 34.50, 126.38, 34.65, 126.39, 34.79, 126.30, 34.95, 126.40, 35.10,
    126.42, 35.28, 126.35, 35.45, 126.45, 35.62, 126.55, 35.75, 126.62, 35.98,
    126.65, 36.08, 126.55, 36.20, 126.50, 36.33, 126.30, 36.45, 126.20, 36.60,
    126.15, 36.75, 126.30, 36.85, 126.50, 36.90, 126.62, 36.98, 126.70, 37.08,
    126.62, 37.15, 126.75, 37.28, 126.65, 37.38, 126.55, 37.45, 126.48, 37.58,
    126.55, 37.70, 126.42, 37.80,
    // 휴전선
    126.68, 37.93, 126.90, 38.00, 127.10, 38.20, 127.30, 38.30, 127.60, 38.32,
    127.90, 38.32, 128.10, 38.40
)

/**
 * **한강** — 양평에서 김포 앞바다까지. 지도를 '아는 그림'으로 만드는 한 줄.
 *   해안선만 있으면 수도권 배율에서 그냥 회색 덩어리다. 강이 가로지르면 바로 읽힌다.
 */
private val HAN = doubleArrayOf(
    127.62, 37.49, 127.45, 37.47, 127.32, 37.51, 127.22, 37.53, 127.12, 37.52,
    127.02, 37.53, 126.94, 37.55, 126.87, 37.57, 126.80, 37.60, 126.72, 37.64,
    126.62, 37.70, 126.52, 37.78
)

/** **서울 경계** — 점선으로. "여기가 서울, 저 밑이 경기" 가 한눈에 된다. */
private val SEOUL = doubleArrayOf(
    126.85, 37.70, 126.95, 37.69, 127.05, 37.70, 127.14, 37.68, 127.18, 37.62,
    127.19, 37.55, 127.14, 37.48, 127.06, 37.44, 126.98, 37.44, 126.90, 37.45,
    126.82, 37.48, 126.79, 37.53, 126.81, 37.60, 126.83, 37.66
)

/** 제주 — 작은 타원. */
private const val JEJU_LON = 126.53
private const val JEJU_LAT = 33.38

private fun DrawScope.drawRegionMap(
    spots: List<RegionDot>,
    named: Set<String>,
    measurer: TextMeasurer,
    land: Color,
    edge: Color,
    dot: Color,
    labelColor: Color,
    labelStyle: TextStyle,
    river: Color
) {
    // ── 보여줄 범위: 다녀온 곳 + 여유. 전국을 다니면 전국, 동네만 다니면 그 언저리. ──
    var minLon = spots.minOf { it.lon }; var maxLon = spots.maxOf { it.lon }
    var minLat = spots.minOf { it.lat }; var maxLat = spots.maxOf { it.lat }
    // 여유를 준다. 그리고 **너무 확대하지 않는다** — 확대가 심하면 해안선이 화면 밖으로 나가
    //   한국처럼 안 보이고 각진 회색 덩어리가 된다. (2026-09-24 폰에서 실제로 그랬다)
    val padLon = max((maxLon - minLon) * 0.35, 0.45)
    val padLat = max((maxLat - minLat) * 0.35, 0.35)
    minLon -= padLon; maxLon += padLon; minLat -= padLat; maxLat += padLat
    // 최소 폭 — 서해안과 동해안이 같이 보일 만큼은 남긴다.
    val MIN_LON = 2.4; val MIN_LAT = 2.0
    if (maxLon - minLon < MIN_LON) {
        val c = (maxLon + minLon) / 2; minLon = c - MIN_LON / 2; maxLon = c + MIN_LON / 2
    }
    if (maxLat - minLat < MIN_LAT) {
        val c = (maxLat + minLat) / 2; minLat = c - MIN_LAT / 2; maxLat = c + MIN_LAT / 2
    }

    // 경도는 위도에 따라 좁아진다(한국 ≈ cos36° ≈ 0.81) — 안 그러면 지도가 옆으로 늘어난다.
    val kx = cos(Math.toRadians(36.0))
    val spanX = (maxLon - minLon) * kx
    val spanY = (maxLat - minLat)
    // 가로·세로 중 빡빡한 쪽에 맞춘다(찌그러뜨리지 않는다).
    val scale = min(size.width / spanX, size.height / spanY).toFloat()
    val offX = ((size.width - spanX * scale) / 2).toFloat()
    val offY = ((size.height - spanY * scale) / 2).toFloat()

    fun px(lon: Double, lat: Double) = Offset(
        (offX + (lon - minLon) * kx * scale).toFloat(),
        (offY + (maxLat - lat) * scale).toFloat()
    )

    // ── 땅 ──
    val path = Path()
    var i = 0
    while (i < KOREA.size) {
        val p = px(KOREA[i], KOREA[i + 1])
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        i += 2
    }
    path.close()
    drawPath(path, land)
    drawPath(path, edge, style = Stroke(width = 1.2f))
    // ── 서울 경계(점선) + 한강 ── 지도를 '아는 그림' 으로 만든다.
    run {
        val sp = Path()
        var k = 0
        while (k < SEOUL.size) {
            val p = px(SEOUL[k], SEOUL[k + 1])
            if (k == 0) sp.moveTo(p.x, p.y) else sp.lineTo(p.x, p.y)
            k += 2
        }
        sp.close()
        // 땅 색과 같은 회색이면 **안 보인다**(폰에서 확인). 이름표 색으로 또렷하게.
        drawPath(sp, labelColor.copy(alpha = 0.45f), style = Stroke(
            width = 1.3f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7f, 5f))
        ))
        val hp = Path()
        k = 0
        while (k < HAN.size) {
            val p = px(HAN[k], HAN[k + 1])
            if (k == 0) hp.moveTo(p.x, p.y) else hp.lineTo(p.x, p.y)
            k += 2
        }
        drawPath(hp, river, style = Stroke(width = 3.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }

    val je = px(JEJU_LON, JEJU_LAT)
    val jr = (0.28 * kx * scale).toFloat()
    if (jr > 1f) {
        drawOval(land, topLeft = Offset(je.x - jr, je.y - jr * .55f),
            size = androidx.compose.ui.geometry.Size(jr * 2, jr * 1.1f))
    }

    // ── 점 ── (많이 간 동네가 큰 점)
    val maxCount = spots.maxOf { it.count }.coerceAtLeast(1)
    for (s in spots) {
        val p = px(s.lon, s.lat)
        val r = 4f + 6f * (s.count.toFloat() / maxCount)
        drawCircle(dot.copy(alpha = 0.18f), r + 5f, p)
        drawCircle(dot, r, p)
        drawCircle(Color.White, r, p, style = Stroke(width = 1.4f))
    }
    // ── 이름표 (많이 간 곳 넷만) — **겹치면 건너뛴다.** ──
    //   겹쳐 찍으면 글자가 뭉개져서 둘 다 못 읽는다(서대문·서초가 그랬다, 2026-09-24).
    val placed = ArrayList<FloatArray>()   // [left, top, right, bottom]
    for (s in spots.sortedByDescending { it.count }) {
        if (s.name !in named) continue
        val p = px(s.lon, s.lat)
        val t = measurer.measure(s.name, labelStyle.copy(color = labelColor))
        var x = p.x + 12f
        if (x + t.size.width > size.width) x = p.x - 12f - t.size.width
        x = x.coerceAtLeast(0f)
        val y = (p.y - t.size.height / 2f).coerceIn(0f, size.height - t.size.height)
        val box = floatArrayOf(x - 2f, y - 2f, x + t.size.width + 2f, y + t.size.height + 2f)
        val hit = placed.any { b ->
            box[0] < b[2] && box[2] > b[0] && box[1] < b[3] && box[3] > b[1]
        }
        if (hit) continue
        placed.add(box)
        drawText(t, topLeft = Offset(x, y))
    }
}
