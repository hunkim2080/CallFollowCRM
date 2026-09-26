package com.detailline.callfollowcrm.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.detailline.callfollowcrm.R
import com.detailline.callfollowcrm.presentation.component.RegionDot
import com.detailline.callfollowcrm.presentation.component.drawRegionMap
import java.io.File

/**
 * **인스타 릴스 한 편.** (2026-09-25 사장님 "영상 저장 될 때는 인스타 릴스 사이즈여야 함")
 *
 * 9:16 세로 한 컷 안에 — 위에 달·큰 숫자, 가운데 지도(🚛 가 달린다), 아래 간판.
 * 정사각형일 이유가 없다(사장님 확인). 우리 화면에 보일 때도 이 비율이면 된다.
 *
 * 한 컷씩 그려서 [VideoMaker] 에 넘긴다 — 같은 그림을 정지로 뽑으면 마지막 장면이 곧 인증샷이다.
 */
object RecordReel {

    /** 릴스 기본 크기. 갤S9+(2018) 도 쓰시니 720×1280 — 큰 화면은 만드는 데 너무 오래 걸린다. */
    const val W = 720
    const val H = 1280

    /**
     * 영상에 담을 것. [RecordShot.Data] 와 같은 값에 **보여줄 숫자 고르기**가 더해진다.
     *
     * @param metricValue 크게 보여줄 숫자(예: "671"). @param metricUnit 단위("만원"/"집"/"km").
     * @param metricLabel 그 숫자 이름("이번 달 번 돈").
     */
    data class Data(
        val monthLabel: String,
        val metricValue: String,
        val metricUnit: String,
        val metricLabel: String,
        val towns: List<String>,
        val dots: List<RegionDot>,
        val bizName: String,
        val tradeName: String,
        val phone: String,
        val area: String,
        /** 손가락으로 맞춘 확대·이동 — 화면에 보이던 그대로 영상에 담는다. */
        val zoom: Float = 1f,
        val panX: Float = 0f,
        val panY: Float = 0f,
        /**
         * **대표 현장 사진** — 그림(인증샷)에 들어가는 그 사진. 영상에도 같이 들어가야
         *   사장님이 고른 모양과 **나온 결과가 같아진다.**
         *   (2026-09-25 사장님 "영상저장하면 저장안되네 이미지 우측상단에있는게")
         */
        val photoPath: String? = null,
        /** 맨 아래 간판(업체명·연락처)을 넣을지. 그림과 **같은 토글**을 따른다. (2026-09-25 사장님) */
        val sign: Boolean = true,
        /**
         * 💰 큰 숫자가 **도착할 때마다 오를 몫** — 동네 순서대로.
         *
         * 비어 있으면 **달린 거리**에 맞춰 이어서 오른다(거리처럼 이어지는 숫자).
         * 돈·집 수처럼 **집마다 다르게** 쌓이는 숫자는 여기에 동네별 몫을 넣는다.
         *   (2026-09-26 사장님 "각 지역마다 금액이 다른데 그렇게 올라야지")
         */
        val metricWeights: List<Float> = emptyList(),
        /**
         * 📸 **사진이 주인공인 영상**으로 만들지.
         *
         * 기본(false)은 지도가 주인공 — 한 달에 이만큼 다녔다를 보여준다.
         * true 면 **현장 사진이 배경**이고 지도는 오른쪽 위에서 작게 달린다.
         *   "시공 사례를 더 부각하고 싶은 사람" 용. (2026-09-26 사장님)
         * 사진이 하나도 없으면 조용히 지도 갈래로 내려간다 — 빈 검은 화면을 내놓지 않는다.
         */
        val photoHero: Boolean = false,
        /**
         * ✍️ **숫자 위에 얹는 기분 한 줄** — 「이번 달도, 현장에서.」
         *   비어 있으면 안 그린다. 달마다 바뀐다(고르는 쪽에서 정한다).
         *   (2026-09-26 사장님이 프로토에서 고르심)
         */
        val headline: String = "",
        /**
         * 💰 숫자 밑 **고지 한 줄** — 「기록한 매출 기준 · 순이익과 달라요」.
         *   500만원을 자랑하면 "그래서 얼마 남았냐"가 꼭 따라온다. 한 줄로 미리 막는다.
         */
        val metricNote: String = ""
    )

    /**
     * 📸 **사진이 주인공인 한 컷.**
     *
     * 배경 = **지금 도착한 현장의 사진**(없으면 그 전 현장 것). 도착할 때마다 바뀌어
     * 한 달 시공 사례가 차례로 지나간다. 지도는 오른쪽 위에서 작게 달린다.
     * 글자는 전부 흰색 — 어떤 사진 위에서든 읽히게 위아래를 어둡게 깐다.
     * (2026-09-26 사장님 "시공사례를 더 부각하고 싶은 사람들은 이 메뉴를 선택할수있게")
     */
    private fun drawPhotoFrame(
        ctx: Context,
        c: Canvas,
        d: Data,
        t: Float,
        w: Int,
        h: Int,
        photos: Map<String, android.graphics.Bitmap>,
        ride: com.detailline.callfollowcrm.util.MapRide.At?
    ) {
        val bold = font(ctx, R.font.pretendard_bold)
        val xbold = font(ctx, R.font.pretendard_extrabold)
        val med = font(ctx, R.font.pretendard_medium)
        val white = 0xFFFFFFFF.toInt()
        val pad = w * 0.072f
        val ordered = d.dots.sortedBy { it.order }

        // ① 배경 — 도착한 데부터 거꾸로 훑어 **사진이 있는 제일 가까운 현장**.
        //   그 동네에 사진이 없다고 화면이 깜빡이면 오히려 어수선하다.
        val bg = run {
            var i = (ride?.arrived ?: 0).coerceIn(0, maxOf(0, ordered.size - 1))
            var found: android.graphics.Bitmap? = null
            while (i >= 0) {
                val nm = ordered.getOrNull(i)?.name
                val b = if (nm != null) photos[nm] else null
                if (b != null) { found = b; break }
                i--
            }
            found ?: photos.values.firstOrNull()
        }
        c.drawColor(0xFF101418.toInt())
        if (bg != null) RecordShot.drawCover(c, bg, android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()))

        // ② 위아래를 어둡게 — 가운데(시공 자리)는 안 건드린다.
        c.drawRect(0f, 0f, w.toFloat(), h * 0.38f, Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, h * 0.38f,
                intArrayOf(0xB3000000.toInt(), 0x00000000), null,
                android.graphics.Shader.TileMode.CLAMP
            )
        })
        c.drawRect(0f, h * 0.62f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, h * 0.62f, 0f, h.toFloat(),
                intArrayOf(0x00000000, 0xC4000000.toInt()), null,
                android.graphics.Shader.TileMode.CLAMP
            )
        })

        // ③ 큰 숫자 — 왼쪽 위. 지도 갈래와 **같은 셈**으로 오른다.
        var y = pad + h * 0.045f
        val monP = RecordShot.onPhoto(paint(bold, w * 0.036f, 0xCCFFFFFF.toInt()))
        c.drawText(d.monthLabel, pad, y, monP)
        val bigP = RecordShot.onPhoto(fit(paint(xbold, w * 0.145f, white), d.metricValue,
            w - pad * 2 - w * 0.40f, w * 0.145f, w * 0.075f))
        if (d.headline.isNotBlank()) {
            val hp = RecordShot.onPhoto(fit(paint(xbold, w * 0.052f, white), d.headline,
                w - pad * 2 - w * 0.34f, w * 0.052f, w * 0.038f))
            y += RecordShot.bigTop(monP, hp, h * 0.008f)
            c.drawText(d.headline, pad, y, hp)
            y += RecordShot.bigTop(hp, bigP, h * 0.008f)
        } else {
            y += RecordShot.bigTop(monP, bigP, h * 0.010f)
        }
        val p2 = if (ride == null) 1f else stepUp(
            d.metricWeights, ride.arrived,
            ride.nowT - (ride.arriveAt.getOrNull(ride.arrived) ?: 0f), ride.frac
        )
        RecordShot.drawBigNumber(
            c, pad, y, countUp(d.metricValue, p2), d.metricUnit, bigP,
            RecordShot.onPhoto(paint(bold, w * 0.058f, white))
        )
        y += h * 0.031f
        c.drawText(d.metricLabel, pad, y,
            RecordShot.onPhoto(paint(bold, w * 0.040f, 0xE6FFFFFF.toInt())))
        if (d.metricNote.isNotBlank()) {
            y += h * 0.026f
            c.drawText(d.metricNote, pad, y,
                RecordShot.onPhoto(paint(med, w * 0.030f, 0xCCFFFFFF.toInt())))
        }

        // ④ 작은 지도 — 오른쪽 위에서 **트럭이 달린다.** 한 뼘짜리라 이름은 안 넣는다(겹쳐서 못 읽는다).
        if (d.dots.isNotEmpty()) {
            val side = w * 0.30f
            val box = android.graphics.RectF(w - pad - side, pad, w - pad, pad + side)
            c.drawRoundRect(box.left - 8f, box.top - 8f, box.right + 8f, box.bottom + 8f, 20f, 20f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xF2FFFFFF.toInt(); setShadowLayer(14f, 0f, 4f, 0x40000000)
                })
            c.save()
            c.clipRect(box)
            c.translate(box.left, box.top)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(c), Size(side, side)
            ) {
                drawRegionMap(
                    spots = d.dots,
                    named = emptySet(),
                    measurer = null,
                    land = androidx.compose.ui.graphics.Color(MapPalette.LAND),
                    edge = androidx.compose.ui.graphics.Color(MapPalette.EDGE),
                    dot = androidx.compose.ui.graphics.Color(0xFF3182F6),
                    labelColor = androidx.compose.ui.graphics.Color(0xFF5A6472),
                    labelStyle = TextStyle(fontSize = 8.sp),
                    river = androidx.compose.ui.graphics.Color(MapPalette.RIVER),
                    // 한 뼘짜리라 **따라가지 않는다** — 작은 창에서 카메라까지 움직이면 멀미한다.
                    //   전체를 보여주고 그 안에서 트럭만 달린다.
                    progress = t,
                    geo = MapGeo.load(ctx),
                    trip = MapGeo.fullRoute(ctx, ordered.map { it.lon to it.lat }),
                    zoom = d.zoom, panX = d.panX, panY = d.panY
                )
            }
            c.restore()
        }

        // ⑤ 동네 줄 + 간판 — 아래. 사진 위라 전부 흰 글씨.
        var by = h - pad - (if (d.sign) h * 0.105f else h * 0.006f)
        if (d.towns.isNotEmpty()) {
            val line = RecordShot.townLine(d.towns)
            c.drawText(line, pad, by, RecordShot.onPhoto(
                fit(paint(med, w * 0.040f, 0xD9FFFFFF.toInt()), line, w - pad * 2, w * 0.040f, w * 0.028f)))
            by += h * 0.034f
        }
        if (!d.sign) return
        val name = d.bizName.trim()
        if (name.isNotBlank()) {
            c.drawText(name, pad, h - pad - h * 0.046f, RecordShot.onPhoto(
                fit(paint(xbold, w * 0.062f, white), name, w - pad * 2, w * 0.062f, w * 0.036f)))
        }
        val line2 = listOfNotNull(
            listOfNotNull(
                d.area.takeIf { it.isNotBlank() },
                d.tradeName.takeIf { it.isNotBlank() }
            ).joinToString(" ").takeIf { it.isNotBlank() },
            d.phone.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (line2.isNotBlank()) {
            c.drawText(line2, pad, h - pad - h * 0.010f,
                fit(paint(bold, w * 0.038f, 0xF2FFFFFF.toInt()), line2,
                    w - pad * 2 - w * 0.20f, w * 0.038f, w * 0.028f))
        }
        c.drawText("시공막내", w - pad, h - pad - h * 0.010f,
            paint(med, w * 0.028f, 0x99FFFFFF.toInt(), Paint.Align.RIGHT))
    }

    /**
     * 🔢 `"약 1,250"` → 달린 만큼 올라간 `"약 730"`.
     *   앞에 붙은 글자(`약 `)와 천 단위 콤마는 그대로 지킨다.
     *   [p] 가 1이면 원래 글자를 **그대로** 돌려준다 — 끝에는 반드시 진짜 숫자여야 한다.
     */
    /**
     * 💰 **도착할 때마다 그 동네 몫만큼** 오른 비율(0~1).
     *
     * 거리는 달린 만큼 이어서 오르지만, 돈은 **집에 도착해야** 들어온다.
     * 도착하고 반 초 동안 굴러오른 뒤 멈춘다 — 지폐가 올라오는 그 순간과 같이.
     * 몫이 없거나 수가 안 맞으면 [fallback](달린 거리)을 그대로 쓴다.
     */
    internal fun stepUp(weights: List<Float>, arrived: Int, sinceArrive: Float, fallback: Float): Float {
        val total = weights.sum()
        if (weights.isEmpty() || total <= 0f) return fallback
        val i = arrived.coerceIn(0, weights.size - 1)
        var before = 0f
        for (k in 0 until i) before += weights[k]
        val now = before + weights[i]
        // 반 초 동안 굴러간다. 트럭이 서 있는 시간과 같아 **떠날 때 딱 멈춘다.**
        val roll = (sinceArrive / 0.55f).coerceIn(0f, 1f)
        return ((before + (now - before) * roll) / total).coerceIn(0f, 1f)
    }

    internal fun countUp(text: String, p: Float): String {
        if (p >= 1f) return text
        val digits = text.filter { it.isDigit() }
        val target = digits.toLongOrNull() ?: return text
        val now = Math.round(target * p.coerceIn(0f, 1f).toDouble())
        val head = text.takeWhile { !it.isDigit() }
        val body = if (text.contains(',')) {
            java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(now)
        } else now.toString()
        return head + body
    }

    /**
     * 끝에서 쉬는 비율 — 10초짜리면 마지막 1.2초는 다 그려진 채로 머무른다.
     * 🔒 **미리보기도 이 값을 쓴다.** 한쪽에만 넣으면 미리보기와 저장본이 또 달라진다.
     */
    const val END_HOLD = 0.12f

    private fun font(ctx: Context, id: Int): Typeface? =
        runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull()

    private fun paint(tf: Typeface?, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = size; this.color = color; textAlign = align
        }

    private fun fit(p: Paint, text: String, maxW: Float, from: Float, min: Float): Paint {
        var s = from
        p.textSize = s
        while (s > min && p.measureText(text) > maxW) { s -= 2f; p.textSize = s }
        return p
    }

    /**
     * 한 컷 그리기.
     * @param t 0~1 로 흐르는 시각. 1 이면 다 달린 마지막 장면(= 인증샷으로 쓸 그림).
     */
    fun drawFrame(
        ctx: Context, c: Canvas, d: Data, t: Float, w: Int = W, h: Int = H,
        /**
         * 미리 읽어둔 현장 사진들 — **동네 이름 → 사진.** 240컷마다 파일을 새로 읽으면 한참 걸린다.
         *   트럭이 그 동네에 **도착하면 그 사진으로 바뀐다.** (2026-09-25 사장님)
         */
        photos: Map<String, android.graphics.Bitmap> = emptyMap(),
        /**
         * 끝에서 **한 박자 쉬는** 비율. 0.12 면 88% 지점에서 다 달리고 남은 1.2초는 멈춰 있는다.
         *   전엔 마지막 현장 사진이 **0.6초만** 보이고 영상이 끝났다. (2026-09-25 사장님)
         *   다 그려진 지도가 잠깐 머무르는 게 마무리로도 낫다.
         */
        endHold: Float = 0f
    ) {
        @Suppress("NAME_SHADOWING")
        val t = if (endHold > 0f) (t / (1f - endHold)).coerceAtMost(1f) else t
        val bold = font(ctx, R.font.pretendard_bold)
        val xbold = font(ctx, R.font.pretendard_extrabold)
        val med = font(ctx, R.font.pretendard_medium)

        val blue = 0xFF3182F6.toInt()
        val ink = 0xFF0B0F19.toInt()
        val sub = 0xFF5A6472.toInt()
        val hint = 0xFF9AA3AF.toInt()
        val land = 0xFFF8F7F3.toInt()
        val pad = w * 0.072f

        c.drawColor(0xFFF7F8FA.toInt())

        // 🚛 지금 어디까지 달렸나 — **숫자와 카메라가 같이 본다.**
        //   따로 구하면 숫자는 다 올랐는데 트럭은 아직 가는 중인 일이 생긴다.
        val ordered0 = d.dots.sortedBy { it.order }
        val trip0 = if (ordered0.size >= 2) MapGeo.fullRoute(ctx, ordered0.map { it.lon to it.lat }) else null
        val wayLL0 = trip0?.pts
            ?: FloatArray(ordered0.size * 2) {
                if (it % 2 == 0) ordered0[it / 2].lon.toFloat() else ordered0[it / 2].lat.toFloat()
            }
        val ride = if (ordered0.isEmpty()) null else com.detailline.callfollowcrm.util.MapRide.at(
            wayLL0, trip0?.stops ?: IntArray(ordered0.size) { it }, t
        )

        // 📸 사진이 주인공인 갈래로. 사진이 하나도 없으면 지도 갈래 그대로 간다.
        if (d.photoHero && photos.isNotEmpty()) {
            drawPhotoFrame(ctx, c, d, t, w, h, photos, ride)
            return
        }

        // ── 위: 달 · 큰 숫자 · 이름 ──
        var y = pad + h * 0.045f
        val monP = paint(bold, w * 0.036f, hint)
        c.drawText(d.monthLabel, pad, y, monP)
        // 🔢 **달린 만큼 올라간다.** (2026-09-25 사장님 "키로수도 후르륵 올라가는 느낌")
        //   글자 크기와 'km' 자리는 **최종 숫자**로 정해둔다 — 자릿수가 늘 때마다
        //   단위가 옆으로 밀리고 글자가 커졌다 작아졌다 하면 싸구려로 보인다.
        val bigP = fit(paint(xbold, w * 0.145f, blue), d.metricValue, w - pad * 2 - w * 0.22f, w * 0.145f, w * 0.085f)
        // 📐 달 라벨과 겹치지 않게 — 「약」 같은 한글은 숫자보다 위로 더 솔는다.
        if (d.headline.isNotBlank()) {
            val hp = fit(paint(xbold, w * 0.052f, ink), d.headline, w - pad * 2, w * 0.052f, w * 0.038f)
            y += RecordShot.bigTop(monP, hp, h * 0.008f)
            c.drawText(d.headline, pad, y, hp)
            y += RecordShot.bigTop(hp, bigP, h * 0.008f)
        } else {
            y += RecordShot.bigTop(monP, bigP, h * 0.010f)
        }
        // 단위는 **지금 숫자**에 붙어 따라온다(글자 크기만 최종값으로 고정) —
        //   마지막 자릿수로 자리를 박아놓았더니 "약 52      km" 처럼 멀찍이 떨어져 보였다.
        //   🔒 띄우기는 [RecordShot.drawBigNumber] 한 곳에 있다 — 각자 적어놓았더니 닿았다.
        //   (2026-09-25 사장님 "글자가 겹쳤어 km 있는곳 간격")
        // 거리는 **달린 만큼** 이어서, 돈·집 수는 **도착할 때마다 그 몫만큼**.
        val bigP2 = if (ride == null) 1f else stepUp(
            d.metricWeights, ride.arrived,
            ride.nowT - (ride.arriveAt.getOrNull(ride.arrived) ?: 0f), ride.frac
        )
        val shownBig = countUp(d.metricValue, bigP2)
        val numW = RecordShot.drawBigNumber(
            c, pad, y, shownBig, d.metricUnit, bigP, paint(bold, w * 0.058f, blue)
        )
        y += h * 0.031f
        c.drawText(d.metricLabel, pad, y, paint(bold, w * 0.040f, sub))
        if (d.metricNote.isNotBlank()) {
            y += h * 0.026f
            c.drawText(d.metricNote, pad, y, paint(med, w * 0.030f, hint))
        }

        // ── 가운데: 지도 ── 세로로 길게 준다. 릴스는 위아래가 넉넉하다.
        val mapTop = y + h * 0.028f
        val mapH = h * 0.545f
        // 지도가 **몇 번째 현장까지 왔는지** 알려준다 — 사진을 그걸로 고른다.
        var arrived = 0
        if (d.dots.isNotEmpty()) {
            c.save()
            c.translate(0f, mapTop)
            c.clipRect(0f, 0f, w.toFloat(), mapH)
            // 🎥 **카메라가 트럭을 따라간다.** (2026-09-25 사장님)
            //   전엔 수도권 전체를 멀리서 보여줘서 트럭이 깨알만 했다 — 안 움직이는 것처럼 보인다.
            //   트럭 자리는 지도가 쓰는 **그 셈 그대로**(MapRide) 구한다. 따로 구하면 어긋난다.
            //   마지막 현장에 닿으면 쭉 빠지면서, 사장님이 맞춰둔 그 화면으로 한 달 전체를 보여준다.
            val shot = com.detailline.callfollowcrm.util.MapRide.follow(
                b = com.detailline.callfollowcrm.util.MapRide.bounds(ordered0.map { it.lon to it.lat }),
                at = ride!!,
                t = t,
                restZoom = d.zoom, restPanX = d.panX, restPanY = d.panY
            )
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(c), Size(w.toFloat(), mapH)
            ) {
                drawRegionMap(
                    spots = d.dots,
                    // 당겨져 있을 땐 **동네 이름을 다 보여준다** — 안 그러면 지금 어디인지 알 수가 없다.
                    named = if (com.detailline.callfollowcrm.util.MapRide.showAllNames(shot.zoom))
                        d.dots.map { it.name }.toSet() else MapPalette.namedOf(d.dots),
                    measurer = null,
                    land = androidx.compose.ui.graphics.Color(MapPalette.LAND),
                    edge = androidx.compose.ui.graphics.Color(MapPalette.EDGE),
                    dot = androidx.compose.ui.graphics.Color(blue),
                    labelColor = androidx.compose.ui.graphics.Color(0xFF5A6472),
                    labelStyle = TextStyle(fontSize = 10.sp),
                    river = androidx.compose.ui.graphics.Color(MapPalette.RIVER),
                    progress = t,
                    geo = MapGeo.load(ctx),
                    trip = trip0,
                    zoom = shot.zoom, panX = shot.panX, panY = shot.panY,
                    onArrived = { arrived = it }
                )
            }
            c.restore()
        }

        // ── 지도 오른쪽 위에 **지금 그 현장의 사진** ── 그림(인증샷)과 같은 자리.
        //   왼쪽 위는 큰 숫자가 쓰고 있어서 오른쪽 위가 빈다.
        //   도착한 데부터 거꾸로 훑어 **사진이 있는 제일 가까운 현장**을 쓴다
        //   — 그 동네에 사진이 없다고 화면이 깜빡이면 오히려 어수선하다.
        val ordered = d.dots.sortedBy { it.order }
        val photo = run {
            var i = arrived.coerceIn(0, maxOf(0, ordered.size - 1))
            var found: android.graphics.Bitmap? = null
            while (i >= 0) {
                val nm = ordered.getOrNull(i)?.name
                val b = if (nm != null) photos[nm] else null
                if (b != null) { found = b; break }
                i--
            }
            found
        }
        if (photo != null) {
            val side = w * 0.30f
            val bx = w - pad - side
            val by0 = mapTop + h * 0.012f
            val box = android.graphics.RectF(bx, by0, bx + side, by0 + side)
            val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt(); setShadowLayer(14f, 0f, 5f, 0x33000000)
            }
            c.drawRoundRect(box.left - 9f, box.top - 9f, box.right + 9f, box.bottom + 9f, 20f, 20f, frame)
            val path = android.graphics.Path().apply {
                addRoundRect(box, 13f, 13f, android.graphics.Path.Direction.CW)
            }
            c.save(); c.clipPath(path); RecordShot.drawCover(c, photo, box); c.restore()
        }

        // ── 아래: 동네 이름 ──
        var by = mapTop + mapH + h * 0.045f
        if (d.towns.isNotEmpty()) {
            val line = RecordShot.townLine(d.towns)
            c.drawText(line, pad, by, fit(paint(bold, w * 0.040f, ink), line, w - pad * 2, w * 0.040f, w * 0.028f))
        }

        // ── 맨 아래: 간판 ── 상호 크게 · 지역·업종·번호. SNS 에 올리는 건 결국 광고다.
        //   끄면 담백하게 — 그림과 같은 토글을 따른다.
        if (!d.sign) return
        val name = d.bizName.trim()
        if (name.isNotBlank()) {
            c.drawText(name, pad, h - pad - h * 0.052f,
                fit(paint(xbold, w * 0.062f, ink), name, w - pad * 2, w * 0.062f, w * 0.036f))
        }
        val line2 = listOfNotNull(
            listOfNotNull(
                d.area.takeIf { it.isNotBlank() },
                d.tradeName.takeIf { it.isNotBlank() }
            ).joinToString(" ").takeIf { it.isNotBlank() },
            d.phone.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (line2.isNotBlank()) {
            c.drawText(line2, pad, h - pad - h * 0.014f,
                fit(paint(bold, w * 0.038f, blue), line2, w - pad * 2 - w * 0.20f, w * 0.038f, w * 0.028f))
        }
        c.drawText("시공막내", w - pad, h - pad - h * 0.014f,
            paint(med, w * 0.028f, hint, Paint.Align.RIGHT))
    }

    /** 영상 한 편 만들기. 만든 파일을 돌려준다(실패하면 null). */
    /**
     * 마지막으로 만든 영상의 **실제 크기** — "720 × 1280" 처럼. 아직 안 만들었으면 null.
     *   ③(작게) 로 떨어지면 540×960 인데 창엔 720×1280 이라 적혀 있었다. (2026-09-25 점검)
     */
    @Volatile
    var lastMadeSize: String? = null
        private set

    /** 다른 방법으로 넘어갔나 — 화면이 "다른 방법으로 다시 만드는 중…" 이라고 말해준다. */
    @Volatile
    var retrying: Boolean = false
        private set

    suspend fun make(
        ctx: Context,
        d: Data,
        seconds: Float = 10f,
        progress: VideoMaker.Progress? = null
    ): File? {
        lastMadeSize = null
        retrying = false
        // 📊 막대는 **뒤로 가지 않는다.** 시도마다 0 부터 다시 부르는데, 그대로 보여주면
        //   40% 에서 0% 로 뚝 떨어져 고장난 것처럼 보인다. 지금까지 간 데를 기억한다.
        var best = 0f
        val forward = VideoMaker.Progress { p ->
            if (p > best) { best = p; progress?.onStep(p) }
        }
        val out = File(File(ctx.cacheDir, "shared").apply { mkdirs() }, "shigongmagne_reel.mp4")
        // 사진은 **한 번만** 읽는다 — 240컷마다 파일을 열면 영상 하나에 몇 분이 더 걸린다.
        //   화면에 들어갈 칸이 폭의 30%(≈216px) 라 320 이면 넉넉하다. 크게 읽으면 메모리만 먹는다.
        val photos = LinkedHashMap<String, android.graphics.Bitmap>()
        for (dot in d.dots.sortedBy { it.order }.take(12)) {
            val p = dot.photoPath ?: continue
            if (photos.containsKey(dot.name)) continue
            RecordShot.loadPhoto(p, 320, 320)?.let { photos[dot.name] = it }
        }
        // 동네별 사진이 하나도 없으면 **그 달 대표 사진**이라도 쓴다(예전처럼 한 장).
        if (photos.isEmpty()) {
            RecordShot.loadPhoto(d.photoPath, 320, 320)?.let { b ->
                d.dots.minByOrNull { it.order }?.let { photos[it.name] = b }
            }
        }
        try {
        // ① 폰이 고르는 인코더(보통 하드웨어)로.
        VideoMaker.make(
            outFile = out, width = W, height = H, fps = 24, seconds = seconds, progress = forward
        ) { canvas, t -> drawFrame(ctx, canvas, d, t, W, H, photos, END_HOLD) }
            ?.let { lastMadeSize = "$W × $H"; return it }
        retrying = true   // 여기부터는 "다른 방법으로 다시 만드는 중"
        // ② 안 되면 **소프트웨어 인코더**로. 느리지만 어느 폰에서나 된다.
        //   (갤S23U·안드로이드 16 에서 하드웨어가 말썽이었다 — 2026-09-25 사장님 폰)
        VideoMaker.softwareEncoder()?.let { sw ->
            VideoMaker.make(
                outFile = out, width = W, height = H, fps = 24, seconds = seconds,
                codecName = sw, progress = forward
            ) { canvas, t -> drawFrame(ctx, canvas, d, t, W, H, photos, END_HOLD) }
                ?.let { lastMadeSize = "$W × $H"; return it }
        }
        // ③ 그래도 안 되면 **작게** 한 번 더. 작으면 되는 경우가 많다.
        val w2 = 540
        val h2 = 960
        return VideoMaker.make(
            outFile = out, width = w2, height = h2, fps = 20, seconds = seconds,
            bitRate = 3_500_000, progress = forward
        ) { canvas, t -> drawFrame(ctx, canvas, d, t, w2, h2, photos, END_HOLD) }
            ?.also { lastMadeSize = "$w2 × $h2" }
        } finally {
            // 읽어둔 사진은 반드시 놓아준다 — 그대로 두면 메모리를 잡아먹는다.
            for (b in photos.values) runCatching { b.recycle() }
        }
    }
}
