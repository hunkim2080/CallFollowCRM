package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
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
    height: androidx.compose.ui.unit.Dp = 190.dp,
    /** 손가락으로 바꾼 확대·이동을 밖에서 들고 있고 싶을 때(영상 저장에 그대로 쓴다). */
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
    onTransform: ((zoom: Float, panX: Float, panY: Float) -> Unit)? = null
) {
    if (spots.isEmpty()) return
    val measurer = rememberTextMeasurer()
    // 제스처 안에서 **지금 값**을 읽으려고 — 안 그러면 처음 값에 붙잡힌다.
    val zoomNow by androidx.compose.runtime.rememberUpdatedState(zoom)
    val panXNow by androidx.compose.runtime.rememberUpdatedState(panX)
    val panYNow by androidx.compose.runtime.rememberUpdatedState(panY)
    // 진짜 지도 좌표 — 지도를 처음 그릴 때 한 번만 읽는다.
    val ctxForGeo = androidx.compose.ui.platform.LocalContext.current
    val geoData = remember { com.detailline.callfollowcrm.util.MapGeo.load(ctxForGeo) }
    // 길을 타고 간 경로 — 확대와 무관하니 점이 바뀔 때만 다시 구한다.
    val tripData = remember(spots) {
        com.detailline.callfollowcrm.util.MapGeo.fullRoute(
            ctxForGeo, spots.sortedBy { it.order }.map { it.lon to it.lat }
        )
    }
    // 🚛 가 그 달 다닌 순서대로 달린다. 한 바퀴 14초.
    //   ⚠️ 폰 설정에서 '애니메이션 배율' 이 0이면 **안 움직인다** — 그건 폰 설정이지 버그가 아니다.
    //      그래서 움직임이 없어도 **길은 다 그려진 채**로 보이게 했다(멈춰도 빈 지도가 안 된다).
    // **한 번 달리고 멈춘다.** 전엔 14초마다 하드 리셋되는 무한 반복이라
    //   제일 좋은 장면(다 그려진 것)을 0.1초만 보여주고 지웠다. 지도를 누르면 다시 달린다.
    //   ⚠️ 폰 설정에서 '애니메이션 배율' 이 0이면 안 움직인다 — 그건 폰 설정이지 버그가 아니다.
    //      그래서 멈춰도 **길은 다 그려진 채**로 남는다(빈 지도가 안 된다).
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    var playTick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(spots, playTick) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(14000, easing = LinearEasing))
    }
    val progress = anim.value
    val line = AppTheme.colors.line
    // 땅 색도 그림·영상과 **같은 색**으로. 화면만 회색기가 돌면 "왜 다르지?" 가 된다. (2026-09-25 점검)
    val landC = Color(com.detailline.callfollowcrm.util.MapPalette.LAND)
    val dotC = AppTheme.colors.primary
    val labelC = AppTheme.colors.textSub
    val riverC = AppTheme.colors.primary.copy(alpha = 0.30f)
    // 글자 크기도 디자인 시스템에서 가져온다(손으로 적지 않는다).
    val labelStyle = AppType.micro
    // 이름표 개수는 **한 곳(MapPalette)** 에서 정한다 — 화면·그림·영상이 같아야 한다.
    val named = remember(spots) { com.detailline.callfollowcrm.util.MapPalette.namedOf(spots) }
    // 모서리를 둥글게 — 카드 안에 각진 네모가 들어있으면 붙이다 말은 것처럼 보인다. (프로토 canvas border-radius:12px)
    Box(modifier.fillMaxWidth().height(height).clip(com.detailline.callfollowcrm.presentation.theme.AppShape.md)) {
        // ⚠ Compose 캔버스는 기본으로 경계를 안 자른다.
        //   확대하면 땅이 카드 밖으로 넘쳐 위에 있는 달 표시를 덮었다(2026-09-24 폰에서 확인).
        Canvas(
            Modifier.fillMaxWidth().height(height).clipToBounds()
                // 지도인데 손가락으로 안 늘어나면 고장으로 느껴진다. (2026-09-25 사장님 "기본 UX")
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        // ⚠️ 바깥 값을 그냥 쓰면 **처음 값(1배)을 계속 붙잡는다** — 항상 지금 값을 읽는다.
                        //   (2026-09-25 "줌인 반응은 있는데 확대축소가 되다 말아")
                        val nz = (zoomNow * zoomChange).coerceIn(0.6f, 8f)
                        val ratio = if (zoomNow > 0f) nz / zoomNow else 1f
                        // 🔍 **집던 곳이 제자리에 있게** — 두 손가락 사이를 기준으로 커진다.
                        //   화면 한가운데 기준으로 커지면 확대할 때마다 보던 데를 놓친다.
                        val fx = (centroid.x - size.width / 2f) / size.width
                        val fy = (centroid.y - size.height / 2f) / size.height
                        var px = panXNow * ratio + fx * (1f - ratio)
                        var py = panYNow * ratio + fy * (1f - ratio)
                        // ✋ 끈 만큼 **그대로** 따라온다. 전엔 여기서 배수로 한 번 더 나눠서
                        //   4배로 확대하면 손가락이 간 거리의 16분의 1만 움직였다(뻑뻑함의 정체).
                        px += pan.x / size.width
                        py += pan.y / size.height
                        // 📐 움직일 수 있는 범위는 **확대한 만큼**이다 — 고정값이면 안 된다.
                        //   그리는 식: 보는 창의 반폭 = 1/(2z), 중심 이동 = panX/z (둘 다 전체 폭 기준).
                        //   창이 틀 밖으로 안 나가려면  |panX|/z + 1/(2z) ≤ 1/2  →  **|panX| ≤ (z-1)/2**.
                        //   전엔 ±3 고정이라 ① 1배에서도 지도를 화면 밖으로 밀어낼 수 있었고
                        //   ② 끝까지 확대하면(8배) 필요한 3.5 를 3 에서 잘라
                        //      **집고 있던 자리가 어긋나며 옆으로 한 번 꺾였다.**
                        //      (2026-09-25 사장님 "줌 인을 쭉 하면 마지막에 좌측으로 한번 꺾인다")
                        //   0.05 는 1배에서도 끈 게 보이라고 남겨둔 아주 작은 여유.
                        val lim = ((nz - 1f) / 2f).coerceAtLeast(0.05f)
                        onTransform?.invoke(nz, px.coerceIn(-lim, lim), py.coerceIn(-lim, lim))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        // 두 번 톡 치면 처음으로 — 손가락으로 헤맸을 때 돌아올 길이 있어야 한다.
                        onDoubleTap = { onTransform?.invoke(1f, 0f, 0f) },
                        // 한 번 톡 치면 **다시 달린다** — 멈춘 뒤 또 보고 싶을 때.
                        onTap = { playTick++ }
                    )
                }
        ) {
            drawRegionMap(spots, named, measurer, landC, line, dotC, labelC, labelStyle, riverC, progress,
                trip = tripData, zoom = zoom, panX = panX, panY = panY, geo = geoData)
        }
    }
}

/**
 * 지도에 찍을 점 하나.
 * @param order 그 달 안에서 **몇 번째로 간 곳인가**(0부터). 🚛 가 이 순서로 달린다.
 */
data class RegionDot(
    val name: String, val lat: Double, val lon: Double, val count: Int, val order: Int = 0,
    /** 그 동네에서 번 돈(만원). 도착할 때 **지폐가 몇 장 올라올지**를 정한다. 0 이면 안 올라온다. */
    val amountManwon: Int = 0,
    /**
     * 그 동네 현장의 **대표 사진**(제일 먼저 올린 것). 영상에서 **거기 도착하면 이 사진으로 바뀐다.**
     *   (2026-09-25 사장님 "그 현장에 도착할때마다 바껴야하는데") null 이면 안 바꾼다.
     */
    val photoPath: String? = null
)

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

/**
 * **주요 고속도로** — 확대해도 허전하지 않게. 길이 지나가면 '아는 그림' 이 된다.
 *   한강·서울 경계만으론 수도권 배율에서 여백이 크다.
 *   경부 · 서해안 · 영동 · 중부. 정확한 노선이 아니라 **대략의 줄기**다(지도 읽기용).
 */
private val ROADS = arrayOf(
    // 경부 — 서울에서 부산까지 나라를 세로로 가른다
    doubleArrayOf(
        127.02, 37.48, 127.05, 37.28, 127.07, 37.15, 127.10, 36.99, 127.15, 36.81,
        127.28, 36.60, 127.40, 36.35, 127.70, 36.10, 128.10, 35.95, 128.55, 35.88,
        128.80, 35.60, 129.05, 35.15
    ),
    // 서해안 — 갯벌을 따라 남쪽으로
    doubleArrayOf(
        126.90, 37.47, 126.83, 37.30, 126.80, 37.15, 126.72, 37.00, 126.65, 36.90,
        126.50, 36.78, 126.55, 36.45, 126.70, 35.98, 126.60, 35.50, 126.45, 34.85
    ),
    // 영동 — 인천에서 강릉까지 가로로
    doubleArrayOf(
        126.68, 37.45, 126.85, 37.32, 127.03, 37.28, 127.15, 37.25, 127.44, 37.28,
        127.70, 37.32, 127.92, 37.35, 128.40, 37.55, 128.88, 37.75
    ),
    // 중부 — 서울 동쪽에서 남으로
    doubleArrayOf(
        127.10, 37.52, 127.21, 37.50, 127.25, 37.35, 127.35, 37.10, 127.45, 36.85,
        127.45, 36.60, 127.42, 36.40
    )
)

/** 산 — 옅은 초록 얼룩 하나. r 은 도 단위 반지름. */
private class Hill(val lon: Double, val lat: Double, val r: Double)

private val HILLS = listOf(
    Hill(126.98, 37.66, 0.055), Hill(127.04, 37.70, 0.045), Hill(126.96, 37.44, 0.045),
    Hill(127.05, 37.42, 0.035), Hill(127.03, 37.33, 0.040), Hill(127.19, 37.48, 0.045),
    Hill(127.25, 37.53, 0.040), Hill(126.90, 37.37, 0.035), Hill(126.72, 37.59, 0.035),
    Hill(127.28, 37.66, 0.050), Hill(127.55, 37.55, 0.060), Hill(126.97, 37.94, 0.050),
    Hill(127.42, 37.90, 0.070), Hill(128.10, 37.70, 0.130), Hill(128.50, 37.90, 0.150),
    Hill(128.80, 37.40, 0.140), Hill(128.90, 36.90, 0.150), Hill(128.60, 36.40, 0.130),
    Hill(127.80, 36.20, 0.110), Hill(127.50, 35.60, 0.120), Hill(127.70, 35.30, 0.110),
    Hill(128.30, 35.50, 0.110), Hill(126.60, 35.30, 0.070)
)

/** 지도에 이름을 얹을 도시. rank 3=광역시 · 2=큰 시 · 1=수도권 시(확대했을 때만). */
private class City(val name: String, val lat: Double, val lon: Double, val rank: Int)

private val CITIES = listOf(
    City("서울", 37.566, 126.978, 3), City("인천", 37.456, 126.705, 3),
    City("대전", 36.351, 127.385, 3), City("대구", 35.872, 128.601, 3),
    City("부산", 35.180, 129.075, 3), City("광주", 35.160, 126.851, 3),
    City("울산", 35.538, 129.311, 3),
    City("수원", 37.263, 127.029, 2), City("성남", 37.420, 127.127, 2),
    City("고양", 37.658, 126.832, 2), City("용인", 37.241, 127.178, 2),
    City("청주", 36.642, 127.489, 2), City("천안", 36.815, 127.114, 2),
    City("전주", 35.824, 127.148, 2), City("춘천", 37.881, 127.730, 2),
    City("강릉", 37.752, 128.876, 2), City("포항", 36.019, 129.343, 2),
    City("창원", 35.228, 128.681, 2), City("제주", 33.499, 126.531, 2),
    City("부천", 37.503, 126.766, 1), City("안산", 37.322, 126.831, 1),
    City("안양", 37.394, 126.957, 1), City("시흥", 37.380, 126.803, 1),
    City("김포", 37.615, 126.716, 1), City("파주", 37.760, 126.780, 1),
    City("의정부", 37.738, 127.034, 1), City("남양주", 37.636, 127.216, 1),
    City("하남", 37.539, 127.215, 1), City("오산", 37.150, 127.077, 1),
    City("평택", 36.992, 127.113, 1), City("이천", 37.272, 127.435, 1),
    City("화성", 37.199, 126.831, 1)
)

/** 제주 — 작은 타원. */
private const val JEJU_LON = 126.53
private const val JEJU_LAT = 33.38

internal fun DrawScope.drawRegionMap(
    spots: List<RegionDot>,
    named: Set<String>,
    /** null = 그림으로 뽑는 중(글자 재는 도구가 없다) → 이름표는 건너뛴다. 동네는 글로 따로 적힌다. */
    measurer: TextMeasurer?,
    land: Color,
    edge: Color,
    dot: Color,
    labelColor: Color,
    labelStyle: TextStyle,
    river: Color,
    progress: Float,
    /**
     * **길을 타고 간 경로.** null 이면 동네끼리 곧게 잇는다.
     *   [com.detailline.callfollowcrm.util.MapGeo.fullRoute] 로 미리 구해서 넘긴다
     *   (여기선 Context 를 못 쓴다).
     */
    trip: com.detailline.callfollowcrm.util.MapGeo.Trip? = null,
    /** 손가락으로 벌린 만큼. 1 = 저절로 맞춘 크기. */
    zoom: Float = 1f,
    /** 손가락으로 끈 만큼 — 화면 폭·높이에 대한 비율. */
    panX: Float = 0f,
    panY: Float = 0f,
    /**
     * **지금 몇 번째 현장까지 왔나**(0부터)를 알려준다. 영상이 그 현장 사진으로 바꾸는 데 쓴다.
     *   ⚠️ 밖에서 따로 계산하면 트럭과 사진이 어긋난다 — **같은 셈을 두 번 하지 않는다.**
     */
    onArrived: ((Int) -> Unit)? = null,
    /**
     * **진짜 지도 좌표**(Natural Earth). null 이면 예전 손그림으로 그린다.
     *   부르는 쪽에서 `MapGeo.load(context)` 로 한 번 읽어 넘긴다 — 여기선 Context 를 못 쓴다.
     */
    geo: com.detailline.callfollowcrm.util.MapGeo.Data? = null,
    /** 바다 색. 바다가 있어야 육지가 육지로 보인다. */
    sea: Color = Color(com.detailline.callfollowcrm.util.MapPalette.SEA)
) {
    // ── 보여줄 범위: 다녀온 곳 + 여유. 전국을 다니면 전국, 동네만 다니면 그 언저리. ──
    var minLon = spots.minOf { it.lon }; var maxLon = spots.maxOf { it.lon }
    var minLat = spots.minOf { it.lat }; var maxLat = spots.maxOf { it.lat }
    // 여유를 준다. 그리고 **너무 확대하지 않는다** — 확대가 심하면 해안선이 화면 밖으로 나가
    //   한국처럼 안 보이고 각진 회색 덩어리가 된다. (2026-09-24 폰에서 실제로 그랬다)
    // 여유 최소값이 **한쪽에 0.45°(≈45km)** 라 다닌 길이 화면의 4분의 1이었다.
    //   확대 한도(MIN_LON)를 낮춰도 이 값이 더 커서 한 번도 안 걸렸다 — 여기가 진짜였다.
    //   프로토에서 사장님과 고른 값으로. (2026-09-25)
    val padLon = max((maxLon - minLon) * 0.35, 0.03)
    val padLat = max((maxLat - minLat) * 0.35, 0.024)
    minLon -= padLon; maxLon += padLon; minLat -= padLat; maxLat += padLat
    // 최소 폭 — **좁게 다녔으면 그만큼 확대한다.** (2026-09-24 사장님)
    //   전엔 2.4°(≈240km)로 막아놔서 수도권만 다녀도 전국이 나왔다.
    //   그러면 점이 다닥다닥 붙어 **가만히 있는 것처럼** 보인다.
    //   0.5°(≈50km)까지 당기면 같은 거리를 가도 화면에서 훨씬 멀리 가는 것처럼 보인다.
    //   (그 아래로는 안 당긴다 — 한 동네만 다녔을 때 점 하나가 화면을 다 먹는다)
    // 한 동네만 다녔을 때의 **바닥** — 이만큼은 보여준다(점 하나가 화면을 다 먹지 않게).
    val MIN_LON = 0.15; val MIN_LAT = 0.126
    if (maxLon - minLon < MIN_LON) {
        val c = (maxLon + minLon) / 2; minLon = c - MIN_LON / 2; maxLon = c + MIN_LON / 2
    }
    if (maxLat - minLat < MIN_LAT) {
        val c = (maxLat + minLat) / 2; minLat = c - MIN_LAT / 2; maxLat = c + MIN_LAT / 2
    }

    // ── 손가락 확대·이동 ── 저절로 맞춘 틀을 **그만큼 좁히고 옮긴다**.
    //   글자 크기는 그대로 두려고 화면을 늘리는 대신 **보는 범위**를 줄인다.
    if (zoom != 1f || panX != 0f || panY != 0f) {
        val z = zoom.coerceIn(0.5f, 10f)
        val cLon = (minLon + maxLon) / 2 - panX * (maxLon - minLon) / z
        val cLat = (minLat + maxLat) / 2 + panY * (maxLat - minLat) / z
        val hw = (maxLon - minLon) / 2 / z
        val hh = (maxLat - minLat) / 2 / z
        minLon = cLon - hw; maxLon = cLon + hw
        minLat = cLat - hh; maxLat = cLat + hh
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

    /** 납작한 [lon,lat,…] 배열 → 화면 위 선. */
    fun pathOf(a: FloatArray, close: Boolean): Path {
        val p = Path()
        val n = a.size / 2
        if (n < 3) {
            var k2 = 0
            while (k2 + 1 < a.size) {
                val q = px(a[k2].toDouble(), a[k2 + 1].toDouble())
                if (k2 == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y)
                k2 += 2
            }
            if (close) p.close()
            return p
        }
        // **중점을 지나는 곡선** — 각진 선은 뇌가 '손으로 그린 것'으로 읽는다. 점을 더 쓰지 않는다.
        val q0 = px(a[0].toDouble(), a[1].toDouble())
        p.moveTo(q0.x, q0.y)
        for (i in 1 until n - 1) {
            val c1 = px(a[i * 2].toDouble(), a[i * 2 + 1].toDouble())
            val c2 = px(a[(i + 1) * 2].toDouble(), a[(i + 1) * 2 + 1].toDouble())
            p.quadraticBezierTo(c1.x, c1.y, (c1.x + c2.x) / 2f, (c1.y + c2.y) / 2f)
        }
        val qn = px(a[(n - 1) * 2].toDouble(), a[(n - 1) * 2 + 1].toDouble())
        p.lineTo(qn.x, qn.y)
        if (close) p.close()
        return p
    }

    // 굵기 기준 — 작은 화면(≈380px)에서 정한 값을 큰 그림(인증샷 1080·영상 720)에 그대로 쓰면
    //   선이 실핀이 된다. 화면 크기에 맞춰 같이 키운다. (2026-09-25 사장님 "인증샷도 지도 이미지가..")
    val k = (size.minDimension / 380f).coerceIn(1f, 3.2f)
    // 글자 크기는 **폭에 비례 + 최소 11dp**. 픽셀로만 정하면 해상도 높은 폰에서 개미 글씨가 된다.
    //   (2026-09-25 사장님 "확대하면 글자가 작아져" — 전엔 5.5dp 짜리였다)
    // 사장님 "글씨가 너무 작아ㅠ" (2026-09-25 폰에서 확인) — 한 번 더 키운다.
    //   지도 글자는 **읽으라고** 있는 것이지 장식이 아니다.
    val tPx = maxOf(size.width * 0.052f, 15.dp.toPx())

    if (geo != null && !geo.isEmpty) {
        // ── 바다 ── 이게 있어야 육지가 육지로 보인다.
        drawRect(sea)
        // ── 해안 후광 ── 땅을 칠하기 전에 굵게 긋고 덮으면 **바깥 절반만** 남는다.
        val glow = Color(com.detailline.callfollowcrm.util.MapPalette.GLOW)
        for (r in geo.land) {
            val p = pathOf(r, true)
            drawPath(p, glow, style = Stroke(width = 14f * k))
            drawPath(p, glow, style = Stroke(width = 5f * k))
        }
        // ── 땅 ──
        for (r in geo.land) {
            val p = pathOf(r, true)
            drawPath(p, land)
            drawPath(p, edge, style = Stroke(width = 1f * k))
        }
        // ── 산 ── 한국 땅의 70%가 산인데 초록이 없으면 평야 나라로 보인다.
        //   Natural Earth 에 산 면이 없어서 우리가 찍은 자리에 옅은 원으로 깐다.
        val hill = Color(com.detailline.callfollowcrm.util.MapPalette.HILL)
        for (h in HILLS) {
            val c0 = px(h.lon, h.lat)
            val e0 = px(h.lon + h.r, h.lat)
            val rr = Math.abs(e0.x - c0.x)
            if (rr < 2f) continue
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to hill, 0.62f to hill.copy(alpha = hill.alpha * 0.5f),
                    1f to hill.copy(alpha = 0f),
                    center = c0, radius = rr
                ),
                radius = rr, center = c0
            )
        }
        // ── 시가지 ── 동그란 얼룩이 아니라 **진짜 서울 모양**.
        val built = Color(com.detailline.callfollowcrm.util.MapPalette.BUILT)
        for (r in geo.urban) drawPath(pathOf(r, true), built)
        // ── 시·도 경계 ──
        for (r in geo.admin) {
            drawPath(pathOf(r, false), labelColor.copy(alpha = 0.22f), style = Stroke(
                width = 1f * k,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f * k, 5f * k))
            ))
        }
        // ── 강 ──
        for (r in geo.rivers) {
            drawPath(pathOf(r, false), river, style = Stroke(
                width = 3f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round
            ))
        }
        // ── 길 ── 테두리 **전부** → 속 **전부**. 순서가 곧 교차로 품질이다.
        val roadEdge = Color(com.detailline.callfollowcrm.util.MapPalette.ROAD_EDGE)
        val roadFill = Color(com.detailline.callfollowcrm.util.MapPalette.ROAD_FILL)
        for (r in geo.roads) {
            drawPath(pathOf(r, false), roadEdge, style = Stroke(
                width = 5.4f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            ))
        }
        for (r in geo.roads) {
            drawPath(pathOf(r, false), roadFill, style = Stroke(
                width = 3.2f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            ))
        }
    } else {

    // ── (물러나기) 예전 손그림 ──
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
    // ── 고속도로 ── 확대해도 허전하지 않게. 땅보다 살짝 진한 선.
    for (road in ROADS) {
        val rp = Path()
        var k = 0
        while (k < road.size) {
            val p = px(road[k], road[k + 1])
            if (k == 0) rp.moveTo(p.x, p.y) else rp.lineTo(p.x, p.y)
            k += 2
        }
        drawPath(rp, edge, style = Stroke(
            width = 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        ))
    }

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
    }   // ── 진짜 좌표 / 손그림 갈림 끝 ──

    // ── 다닌 길 + 🚛 ── 날짜 순서대로 이은 선. 지나온 만큼 진하게 그어진다.
    val route = spots.sortedBy { it.order }
    /** 화면 위 길. 길을 못 받았으면 동네끼리 곧게. */
    val way: List<Offset> = if (trip != null && trip.pts.size >= 4) {
        ArrayList<Offset>(trip.pts.size / 2).apply {
            var i = 0
            while (i + 1 < trip.pts.size) { add(px(trip.pts[i].toDouble(), trip.pts[i + 1].toDouble())); i += 2 }
        }
    } else route.map { px(it.lon, it.lat) }
    /** 현장이 길 위 몇 번째 점인가. */
    val stops: IntArray = trip?.stops ?: IntArray(route.size) { it }

    // ── 시간표 ── 구간마다 길이에 맞춰 시간을 주고 **현장마다 반 초쯤 멈춘다**.
    //   등속으로 흐르면 '날아가는' 느낌이라 일하는 것처럼 안 보인다. (2026-09-25 프로토 확정)
    var travelled = 0f
    var arrivedUpTo = 0
    val arriveAt = FloatArray(route.size)
    var nowT = 0f
    val segLen = FloatArray(maxOf(0, way.size - 1))
    var totalLen = 0f
    for (i in 0 until way.size - 1) {
        val d = (way[i + 1] - way[i]).getDistance()
        segLen[i] = d; totalLen += d
    }
    if (way.size >= 2 && stops.size >= 2) {
        val legs = FloatArray(stops.size - 1)
        var maxLeg = 1f
        for (s2 in 0 until stops.size - 1) {
            var L = 0f
            for (i in stops[s2] until minOf(stops[s2 + 1], segLen.size)) L += segLen[i]
            legs[s2] = L; if (L > maxLeg) maxLeg = L
        }
        val pause = 0.55f
        val durs = FloatArray(legs.size) { 0.8f + 1.4f * (legs[it] / maxLeg) }
        var totalT = pause * legs.size
        for (d in durs) totalT += d
        var acc = 0f
        for (s2 in 0 until legs.size) {
            acc += durs[s2]
            arriveAt[s2 + 1] = acc
            acc += pause
        }
        nowT = totalT * progress.coerceIn(0f, 1f)
        // 지금 어느 구간인가 + 그 안에서 얼마나 왔나
        var cur = legs.size
        var u = 0f
        var t0 = 0f
        for (s2 in 0 until legs.size) {
            if (nowT < t0 + durs[s2]) { cur = s2; u = (nowT - t0) / durs[s2]; break }
            t0 += durs[s2]
            if (nowT < t0 + pause) { cur = s2; u = 1f; break }
            t0 += pause
        }
        val e = if (u < .5f) 2f * u * u else 1f - Math.pow((-2f * u + 2f).toDouble(), 3.0).toFloat() / 2f
        // **길이 하나로** 트럭 자리와 지나온 선을 같이 구한다(따로 구하면 어긋난다).
        var base = 0f
        for (i in 0 until minOf(stops[minOf(cur, stops.size - 1)], segLen.size)) base += segLen[i]
        var legLen = 0f
        if (cur < legs.size) legLen = legs[cur]
        travelled = if (cur >= legs.size) totalLen else base + legLen * e
        arrivedUpTo = 0
        for (i in route.indices) if (nowT >= arriveAt[i]) arrivedUpTo = i
    } else {
        travelled = totalLen * progress.coerceIn(0f, 1f)
        arrivedUpTo = route.size - 1
    }
    onArrived?.invoke(arrivedUpTo)

    // ── 지나온 길 ── 앞길은 안 보여준다(결말을 미리 알려주면 도착이 시시하다).
    if (way.size >= 2) {
        val rp = Path()
        rp.moveTo(way[0].x, way[0].y)
        var acc2 = 0f
        var head = Offset(1f, 0f)
        var tip = way[way.size - 1]
        for (i in 0 until way.size - 1) {
            if (acc2 + segLen[i] >= travelled) {
                val f = if (segLen[i] > 0f) (travelled - acc2) / segLen[i] else 0f
                tip = Offset(
                    way[i].x + (way[i + 1].x - way[i].x) * f,
                    way[i].y + (way[i + 1].y - way[i].y) * f
                )
                head = way[i + 1] - way[i]
                rp.lineTo(tip.x, tip.y)
                break
            }
            acc2 += segLen[i]
            rp.lineTo(way[i + 1].x, way[i + 1].y)
            if (i == way.size - 2) { tip = way[i + 1]; head = way[i + 1] - way[i] }
        }
        drawPath(rp, Color.White.copy(alpha = 0.85f), style = Stroke(
            width = 5.2f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        ))
        drawPath(rp, dot, style = Stroke(
            width = 2.9f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        ))
        // 🚛 — 지금 자리. 가는 쪽을 본다.
        val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tPx * 1.05f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val nv0 = drawContext.canvas.nativeCanvas
        nv0.save()
        nv0.translate(tip.x, tip.y)
        if (head.x > 0f) nv0.scale(-1f, 1f)     // 🚛 는 왼쪽을 본다 — 오른쪽으로 가면 뒤집는다
        nv0.drawText("🚛", 0f, -tp.textSize * 0.28f, tp)
        nv0.restore()
    }

    // ── 점 ── 처음엔 **속 빈 회색**, 트럭이 닿으면 파랗게 **차오르며 튄다**.
    val maxCount = spots.maxOf { it.count }.coerceAtLeast(1)
    route.forEachIndexed { idx, s ->
        val p = px(s.lon, s.lat)
        val base = tPx * (0.16f + 0.11f * (s.count.toFloat() / maxCount))
        val came = idx <= arrivedUpTo
        if (!came) {
            drawCircle(land, base * 0.62f, p)
            drawCircle(labelColor.copy(alpha = 0.45f), base * 0.62f, p, style = Stroke(width = 1.4f * k))
            return@forEachIndexed
        }
        val since = nowT - arriveAt[idx]
        val pop = if (since in 0f..0.35f) 1f + 0.25f * Math.sin(Math.PI * (since / 0.35f)).toFloat() else 1f
        val r = base * pop
        if (since in 0f..0.6f) {
            val g = since / 0.6f
            drawCircle(dot.copy(alpha = 0.5f * (1f - g)), r + r * 2f * g, p)
        }
        drawCircle(dot.copy(alpha = 0.18f), r + 5f * k, p)
        drawCircle(dot, r, p)
        drawCircle(Color.White, r, p, style = Stroke(width = 1.4f * k))
        // 💵 도착할 때 **돈이 올라온다** — 다녀왔다가 아니라 **벌었다**가 된다.
        val notes = (s.amountManwon / 50).coerceIn(0, 4)
        if (notes > 0 && since in 0f..1.6f) {
            val nv1 = drawContext.canvas.nativeCanvas
            for (q in 0 until notes) {
                val e2 = since - q * 0.085f
                if (e2 < 0f || e2 > 1.35f) continue
                val up = 74f * e2 - 0.5f * 52f * e2 * e2
                if (up < 0f) continue
                val dx = (if (q % 2 == 0) -1f else 1f) * (9f + (q * 13 % 11)) * e2 * 2.4f
                val a = if (e2 < 0.7f) 1f else (1f - (e2 - 0.7f) / 0.25f).coerceAtLeast(0f)
                val bw = tPx * 0.95f
                val bh = tPx * 0.52f
                val flap = Math.abs(Math.cos(e2 * Math.PI * 3.2)).toFloat().coerceAtLeast(0.22f)
                nv1.save()
                nv1.translate(p.x + dx * k, p.y - r - 6f * k - up * k)
                nv1.rotate(Math.sin(e2 * Math.PI * 2.4 + q).toFloat() * 22f)
                nv1.scale(flap, 1f)
                val bp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb((a * 255).toInt(), 0x8F, 0xD3, 0xA4)
                }
                nv1.drawRoundRect(-bw / 2, -bh / 2, bw / 2, bh / 2, 2f * k, 2f * k, bp)
                bp.style = android.graphics.Paint.Style.STROKE
                bp.strokeWidth = 1f * k
                bp.color = android.graphics.Color.argb((a * 160).toInt(), 0x28, 0x6E, 0x42)
                nv1.drawRoundRect(-bw / 2, -bh / 2, bw / 2, bh / 2, 2f * k, 2f * k, bp)
                nv1.restore()
            }
        }
    }
    // 🚩 첫 현장엔 깃발 — 멈춘 그림에서도 **어디서 시작했는지** 보여야 한다.
    if (route.isNotEmpty()) {
        val p0 = px(route[0].lon, route[0].lat)
        val base0 = tPx * (0.16f + 0.11f * (route[0].count.toFloat() / maxCount))
        val fh = tPx * 0.7f
        val fx = p0.x + base0 * 0.8f
        val fy = p0.y - base0 * 0.8f
        drawLine(labelColor.copy(alpha = 0.7f), Offset(fx, fy), Offset(fx, fy - fh), strokeWidth = 1.6f * k)
        val fp = Path().apply {
            moveTo(fx, fy - fh)
            lineTo(fx + fh * 0.62f, fy - fh + fh * 0.2f)
            lineTo(fx, fy - fh + fh * 0.4f)
            close()
        }
        drawPath(fp, dot)
    }
    // ── 이름표 ──
    //   ⚠️ 전엔 Compose 글자 재는 도구로 그렸는데, **인증샷·영상엔 그 도구가 없어서**
    //     이름표를 통째로 건너뛰고 있었다 — 저장된 그림엔 동네 이름이 하나도 없었다.
    //     (2026-09-25 사장님 "인증샷도 지도 이미지가..")
    //     기본 글자 그리기로 바꾸면 화면·그림·영상에서 **똑같이** 나온다.
    //   겹치면 글자가 뭉개져 둘 다 못 읽으니, **네 자리를 차례로 시도**한다.
    //     오른쪽 한 자리만 보고 포기해서 관악 옆 강서가 사라졌었다.
    val nv = drawContext.canvas.nativeCanvas
    val lp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tPx
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        color = android.graphics.Color.argb(
            (labelColor.alpha * 255).toInt(), (labelColor.red * 255).toInt(),
            (labelColor.green * 255).toInt(), (labelColor.blue * 255).toInt()
        )
    }
    val hp2 = android.graphics.Paint(lp).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = tPx * 0.26f
        color = android.graphics.Color.argb(
            (land.alpha * 255).toInt(), (land.red * 255).toInt(),
            (land.green * 255).toInt(), (land.blue * 255).toInt()
        )
    }
    val placed = ArrayList<FloatArray>()
    fun free(b: FloatArray) = placed.none { q -> b[0] < q[2] && b[2] > q[0] && b[1] < q[3] && b[3] > q[1] }
    for (s in spots.sortedByDescending { it.count }) {
        if (s.name !in named) continue
        val p = px(s.lon, s.lat)
        val w = lp.measureText(s.name)
        val h = lp.textSize
        val gap = tPx * 0.62f
        // 오른쪽 → 왼쪽 → 위 → 아래
        val cands = arrayOf(
            floatArrayOf(p.x + gap, p.y + h * 0.35f),
            floatArrayOf(p.x - gap - w, p.y + h * 0.35f),
            floatArrayOf(p.x - w / 2f, p.y - gap),
            floatArrayOf(p.x - w / 2f, p.y + gap + h * 0.8f)
        )
        var put: FloatArray? = null
        for (c in cands) {
            if (c[0] < 2f || c[0] + w > size.width - 2f) continue
            if (c[1] - h < 2f || c[1] > size.height - 2f) continue
            val box = floatArrayOf(c[0] - 2f, c[1] - h - 2f, c[0] + w + 2f, c[1] + 3f)
            if (free(box)) { placed.add(box); put = c; break }
        }
        val at = put ?: continue
        nv.drawText(s.name, at[0], at[1], hp2)
        nv.drawText(s.name, at[0], at[1], lp)
    }

    // ── 도시 이름 ── 배경이다. 이름이 있어야 "어디"인지 읽힌다. 현장 이름보다 작고 연하게.
    run {
        val cp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tPx * 0.72f
            color = android.graphics.Color.argb(
                190, (labelColor.red * 255).toInt(),
                (labelColor.green * 255).toInt(), (labelColor.blue * 255).toInt()
            )
        }
        val ch = android.graphics.Paint(cp).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = tPx * 0.22f
            color = android.graphics.Color.argb(
                255, (land.red * 255).toInt(), (land.green * 255).toInt(), (land.blue * 255).toInt()
            )
        }
        val nvc = drawContext.canvas.nativeCanvas
        val span = maxLon - minLon
        for (c in CITIES) {
            if (c.rank == 1 && span > 1.0) continue
            if (c.rank == 2 && span > 3.0) continue
            val cpx = px(c.lon, c.lat)
            if (cpx.x < 4f || cpx.x > size.width - 4f || cpx.y < 8f || cpx.y > size.height - 4f) continue
            // 현장 점 가까이 있는 도시 이름은 생략 — 겹쳐 읽으면 둘 다 못 읽는다.
            if (spots.any { (px(it.lon, it.lat) - cpx).getDistance() < 26f * k }) continue
            // **동네 이름이 먼저** — 그 자리를 침범하면 도시 이름은 포기한다.
            //   전엔 도시를 먼저 그려서 「서울」이 「강서」 뒤에 깔려 "울" 만 삐져나왔다.
            val cw = cp.measureText(c.name)
            val cbox = floatArrayOf(
                cpx.x + 4f * k - 2f, cpx.y + 3f * k - cp.textSize - 2f,
                cpx.x + 4f * k + cw + 2f, cpx.y + 3f * k + 3f
            )
            if (!free(cbox)) continue
            placed.add(cbox)
            nvc.drawText(c.name, cpx.x + 4f * k, cpx.y + 3f * k, ch)
            nvc.drawText(c.name, cpx.x + 4f * k, cpx.y + 3f * k, cp)
            if (c.rank >= 2) drawCircle(labelColor.copy(alpha = 0.55f), 1.6f * k, cpx)
        }
    }

    // ── 축척 막대 ── "이 그림은 재어진 것" 이라는 증거.
    run {
        val pxPerKm = (scale / 111.0).toFloat()
        var pick = 10
        for (o in intArrayOf(1, 2, 5, 10, 20, 50, 100, 200)) {
            val w0 = o * pxPerKm
            if (w0 >= size.width * 0.10f && w0 <= size.width * 0.24f) { pick = o; break }
        }
        val wBar = pick * pxPerKm
        val x0 = 8f * k
        val y0 = size.height - 8f * k
        val c2 = labelColor.copy(alpha = 0.7f)
        drawLine(c2, Offset(x0, y0), Offset(x0 + wBar, y0), strokeWidth = 1.2f * k)
        drawLine(c2, Offset(x0, y0 - 3f * k), Offset(x0, y0 + 3f * k), strokeWidth = 1.2f * k)
        drawLine(c2, Offset(x0 + wBar, y0 - 3f * k), Offset(x0 + wBar, y0 + 3f * k), strokeWidth = 1.2f * k)
        val sp2 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tPx * 0.7f
            color = android.graphics.Color.argb(
                200, (labelColor.red * 255).toInt(),
                (labelColor.green * 255).toInt(), (labelColor.blue * 255).toInt()
            )
        }
        drawContext.canvas.nativeCanvas.drawText(pick.toString() + " km", x0, y0 - 5f * k, sp2)
    }
}
