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
        ride: com.detailline.callfollowcrm.util.MapRide.At?,
        introP: Float
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
        // 🏷️ 단지명 — 지금 배경(그 현장)의 단지. (2026-10-28 사장님)
        ordered.getOrNull(ride?.arrived ?: 0)?.apartment?.takeIf { it.isNotBlank() }?.let { apt ->
            c.drawText(apt, pad, by, RecordShot.onPhoto(paint(bold, w * 0.036f, white)))
        }
        drawIntro(ctx, c, d, w, h, introP)
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
        val body = if (text.contains(',')) MoneyFormatter.grouped(now) else now.toString()
        return head + body
    }

    /**
     * 끝에서 쉬는 비율 — 10초짜리면 마지막 1.2초는 다 그려진 채로 머무른다.
     * 🔒 **미리보기도 이 값을 쓴다.** 한쪽에만 넣으면 미리보기와 저장본이 또 달라진다.
     */
    // 🎬 틀(목업 F, 2026-10-28 사장님 A안): [0,INTRO]=오프닝 · [INTRO,1-ENDCARD]=여정(핵심 멈춤 리빌) · [1-ENDCARD,1]=엔드카드.
    //   엔드카드가 마무리를 맡으니 옛 END_HOLD 는 0 (미리보기도 같은 값).
    const val END_HOLD = 0f
    /** 🎬 오프닝 비율 — 꽉 찼다 걷힘 + 큰 숫자. */
    const val INTRO = 0.16f
    /** 🎬 엔드카드 비율 — 결과 한 줄 + 큰 숫자 + 간판. */
    const val ENDCARD = 0.16f
    /** 🎬 핵심 현장 **한 곳**이 "멈춰서 크게" 차지하는 비율(여정 구간 안). 핵심 N곳이면 N×이만큼. */
    const val REVEAL_FRAC = 0.16f
    /** 🎬 크게 보여줄 핵심 현장 **최대 개수**. 많아도 이만큼만 "짠" 크게, 나머지는 점. (2026-10-28 사장님 "5곳만 제대로") */
    const val FEAT_MAX = 4

    /** 제일 짧은 영상. */
    const val MIN_SECONDS = 10f
    /** 제일 긴 영상 (사장님 "10-15초"). 새 틀도 15초 안에 들어간다(오프닝·여정·핵심 4곳 리빌·엔드카드). */
    const val MAX_SECONDS = 15f

    /**
     * 🎬 **몇 초짜리로 만들까** — 곳이 많으면 길게. (2026-09-30 사장님)
     *
     *   "현장에 따라 10-15초 나오게 해야 버벅이는 것처럼 안 보이지 않을까?"
     *
     * 전엔 **10초 고정**이었다. 3곳이든 12곳이든 10초 —
     * 많이 다닌 달일수록 트럭이 **한 곳당 1초도 못 쓰고** 튀듯 지나갔다.
     * 컷 수(24)는 멀쩡한데 **한 컷 사이에 움직이는 거리가 커서** 버벅이는 것처럼 보인다.
     *
     * 재는 것은 **들르는 횟수**(구간 수)다. 점 개수가 아니라 — 출발점은 달리는 구간이 아니다.
     * 끝에서 [END_HOLD] 만큼은 멈춰 있으니 그만큼 빼고 나눈다.
     *
     * @param stops 지도에 찍히는 점 개수(출발 포함)
     */
    fun secondsFor(stops: Int): Float {
        val legs = (stops - 1).coerceAtLeast(1)          // 달리는 구간 수
        val driving = legs * SEC_PER_LEG                 // 구간 하나에 이만큼은 쓰자
        val total = driving / (1f - END_HOLD)            // 끝에 멈춰 있는 시간까지 더해서
        return total.coerceIn(MIN_SECONDS, MAX_SECONDS)
    }

    /** 한 구간(앞 현장 → 다음 현장)에 주고 싶은 시간. 이보다 빠르면 눈이 못 따라간다. */
    private const val SEC_PER_LEG = 1.3f

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
     * 🎬 **오프닝 훅** — 맨 앞 잠깐, 밝은 막 + 제목(월·기분 한 줄·무슨 숫자인지)이 **떴다 걷히며** 여정으로. (2026-10-28 사장님: 바이럴 광택)
     *   숫자는 **일부러 안 보여준다** — "얼마나?" 궁금하게 두고, 달리면서 올라가는 걸로 푼다.
     *   [fade] 1→0 (INTRO 구간). 밝은 톤이라 흰 막 + 검은 글씨. 색은 argb(알파·10진 RGB)로 — 하드코딩 hex 안 늘린다.
     */
    private fun drawIntro(ctx: Context, c: Canvas, d: Data, w: Int, h: Int, introP: Float) {
        if (introP >= 1f) return
        // 앞 72% 는 **꽉 차게**(그동안 큰 숫자 0→최종 올라감), 뒤 28% 에서 걷힌다 — '금방 꺼져 흐리멍텅' 고침. (2026-10-28 사장님)
        val a = if (introP <= 0.72f) 1f else (1f - (introP - 0.72f) / 0.28f).coerceIn(0f, 1f)
        if (a <= 0.01f) return
        val xbold = font(ctx, R.font.pretendard_extrabold)
        val med = font(ctx, R.font.pretendard_medium)
        c.drawColor(android.graphics.Color.argb((a * 255f).toInt(), 247, 248, 250))   // 꽉 찬 밝은 막
        val cx = w / 2f
        var y = h * 0.32f
        c.drawText(
            d.monthLabel, cx, y,
            paint(med, w * 0.048f, android.graphics.Color.argb((a * 255f).toInt(), 90, 100, 114), Paint.Align.CENTER)
        )
        y += h * 0.058f
        val title = d.headline.ifBlank { "이번 달, 현장에서" }
        c.drawText(
            title, cx, y,
            fit(paint(xbold, w * 0.072f, android.graphics.Color.argb((a * 255f).toInt(), 11, 15, 25), Paint.Align.CENTER),
                title, w - w * 0.14f, w * 0.072f, w * 0.05f)
        )
        // 🔢 큰 숫자 — 앞 72% 동안 0→최종으로 후르륵(오프닝 주인공). 글자 자리는 최종값으로 고정.
        val cp = (introP / 0.72f).coerceIn(0f, 1f)
        val eased = 1f - (1f - cp) * (1f - cp)
        y += h * 0.115f
        val bigP = fit(
            paint(xbold, w * 0.170f, android.graphics.Color.argb((a * 255f).toInt(), 49, 130, 246), Paint.Align.CENTER),
            d.metricValue + d.metricUnit, w - w * 0.12f, w * 0.170f, w * 0.10f
        )
        c.drawText(countUp(d.metricValue, eased) + " " + d.metricUnit, cx, y, bigP)
        if (d.metricLabel.isNotBlank()) {
            y += h * 0.054f
            c.drawText(
                d.metricLabel, cx, y,
                paint(med, w * 0.044f, android.graphics.Color.argb((a * 255f).toInt(), 90, 100, 114), Paint.Align.CENTER)
            )
        }
    }

    // ═══════════ 틀 F 전용 조각들 (2026-10-28 사장님 A안: 풀스크린 지도 + 핵심 멈춤 리빌 + 엔드카드) ═══════════

    /** 핵심 현장 index(order 순, 최대 [FEAT_MAX]) — 전 사진 있는 곳 먼저, 모자라면 대표 사진 있는 곳. */
    private fun pickFeatured(ordered: List<com.detailline.callfollowcrm.presentation.component.RegionDot>): List<Int> {
        val withBefore = ordered.indices.filter { !ordered[it].beforePhotoPath.isNullOrBlank() }
        val withPhoto = ordered.indices.filter { !ordered[it].photoPath.isNullOrBlank() && it !in withBefore }
        return (withBefore + withPhoto).take(FEAT_MAX).sorted()
    }

    private class Warp(val driveT: Float, val revealIdx: Int, val revealProg: Float)

    /** 🎬 여정 진행도(0..1)를 (트럭 driveT, 리빌중인 핵심 순번, 리빌 진행)로. 핵심 현장마다 [rf] 만큼 **멈춘다**. [featDriveT] 오름차순. */
    private fun warpTime(frameT: Float, featDriveT: FloatArray, rf: Float): Warp {
        val m = featDriveT.size
        if (m == 0) return Warp(frameT.coerceIn(0f, 1f), -1, 0f)
        val driveTime = (1f - m * rf).coerceAtLeast(0.08f)
        var acc = 0f; var prev = 0f
        for (i in 0 until m) {
            val fdt = featDriveT[i].coerceIn(0f, 1f)
            val span = (fdt - prev) * driveTime
            if (span > 0f && frameT <= acc + span)
                return Warp((prev + (frameT - acc) / driveTime).coerceIn(0f, 1f), -1, 0f)
            acc += span
            if (frameT <= acc + rf) return Warp(fdt, i, ((frameT - acc) / rf).coerceIn(0f, 1f))
            acc += rf
            prev = fdt
        }
        return Warp((prev + (frameT - acc) / driveTime).coerceIn(0f, 1f), -1, 0f)
    }

    /** 바닥 가운데 알약 "현장 N · 동네". */
    private fun drawChip(c: Canvas, w: Int, h: Int, text: String, bold: Typeface?) {
        val tp = paint(bold, w * 0.040f, android.graphics.Color.argb(255, 11, 15, 25), Paint.Align.CENTER)
        val tw = tp.measureText(text)
        val cx = w / 2f; val cy = h - h * 0.050f
        val rw = tw / 2f + w * 0.045f; val rh = h * 0.026f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(240, 255, 255, 255); setShadowLayer(14f, 0f, 4f, 0x33000000)
        }
        c.drawRoundRect(cx - rw, cy - rh, cx + rw, cy + rh, rh, rh, bg)
        c.drawText(text, cx, cy + tp.textSize * 0.35f, tp)
    }

    /** 🖼️ 핵심 현장 **크게** — 어둑한 막 + 큰 카드(전→후 페이드) + 단지명·동네. [prog] 0..1. */
    private fun drawBigReveal(
        ctx: Context, c: Canvas, w: Int, h: Int,
        dot: com.detailline.callfollowcrm.presentation.component.RegionDot,
        after: android.graphics.Bitmap?, before: android.graphics.Bitmap?, prog: Float
    ) {
        if (after == null) return
        val xbold = font(ctx, R.font.pretendard_extrabold)
        val med = font(ctx, R.font.pretendard_medium)
        val pop = (prog * 5f).coerceIn(0f, 1f)
        val popE = 1f - (1f - pop) * (1f - pop)
        c.drawColor(android.graphics.Color.argb((pop * 150f).toInt(), 18, 26, 48))
        val cardW = w * 0.80f
        val frameH = cardW * 0.72f
        val cx = w / 2f
        val fx = cx - cardW / 2f
        val fy = h * 0.26f
        val sc = 0.9f + 0.1f * popE
        c.save()
        c.translate(cx, fy + frameH / 2f); c.scale(sc, sc); c.translate(-cx, -(fy + frameH / 2f))
        val pad = cardW * 0.04f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; setShadowLayer(24f, 0f, 9f, 0x55000000)
        }
        c.drawRoundRect(fx - pad, fy - pad, fx + cardW + pad, fy + frameH + h * 0.058f, 26f, 26f, bg)
        val box = android.graphics.RectF(fx, fy, fx + cardW, fy + frameH)
        val path = android.graphics.Path().apply { addRoundRect(box, 18f, 18f, android.graphics.Path.Direction.CW) }
        c.save(); c.clipPath(path)
        RecordShot.drawCover(c, after, box)
        if (before != null) {
            val bA = 1f - ((prog - 0.18f) / 0.5f).coerceIn(0f, 1f)
            if (bA > 0.01f) {
                val sv = c.saveLayerAlpha(box.left, box.top, box.right, box.bottom, (bA * 255f).toInt())
                RecordShot.drawCover(c, before, box)
                c.restoreToCount(sv)
            }
        }
        c.restore()
        val apt = dot.apartment?.takeIf { it.isNotBlank() } ?: dot.name
        c.drawText(apt, fx, box.bottom + h * 0.040f,
            fit(paint(xbold, w * 0.052f, android.graphics.Color.argb(255, 11, 15, 25)), apt, cardW, w * 0.052f, w * 0.034f))
        if (!dot.apartment.isNullOrBlank())
            c.drawText(dot.name, fx, box.bottom + h * 0.066f, paint(med, w * 0.034f, android.graphics.Color.argb(255, 90, 100, 114)))
        c.restore()
    }

    /** 🏁 엔드카드 — 결과 한 줄 + 큰 숫자 + 간판. [endP] 0..1. */
    private fun drawEnd(ctx: Context, c: Canvas, d: Data, w: Int, h: Int, endP: Float) {
        val a = (endP * 4f).coerceIn(0f, 1f)
        val xbold = font(ctx, R.font.pretendard_extrabold)
        val med = font(ctx, R.font.pretendard_medium)
        val bold = font(ctx, R.font.pretendard_bold)
        c.drawColor(android.graphics.Color.argb((a * 245f).toInt(), 244, 247, 251))
        val cx = w / 2f; var y = h * 0.33f
        val q = d.headline.ifBlank { "이번 달도, 현장에서" }
        c.drawText(q, cx, y,
            fit(paint(xbold, w * 0.074f, android.graphics.Color.argb((a * 255f).toInt(), 11, 15, 25), Paint.Align.CENTER), q, w - w * 0.14f, w * 0.074f, w * 0.05f))
        y += h * 0.085f
        val num = d.metricValue + " " + d.metricUnit
        c.drawText(num, cx, y,
            fit(paint(xbold, w * 0.150f, android.graphics.Color.argb((a * 255f).toInt(), 49, 130, 246), Paint.Align.CENTER), num, w - w * 0.12f, w * 0.150f, w * 0.09f))
        y += h * 0.048f
        if (d.metricLabel.isNotBlank())
            c.drawText(d.metricLabel, cx, y, paint(med, w * 0.044f, android.graphics.Color.argb((a * 255f).toInt(), 90, 100, 114), Paint.Align.CENTER))
        if (d.sign) {
            val name = d.bizName.trim()
            if (name.isNotBlank())
                c.drawText(name, cx, h - h * 0.085f,
                    fit(paint(xbold, w * 0.050f, android.graphics.Color.argb((a * 255f).toInt(), 11, 15, 25), Paint.Align.CENTER), name, w - w * 0.16f, w * 0.050f, w * 0.034f))
            val line2 = listOfNotNull(
                listOfNotNull(d.area.takeIf { it.isNotBlank() }, d.tradeName.takeIf { it.isNotBlank() }).joinToString(" ").takeIf { it.isNotBlank() },
                d.phone.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (line2.isNotBlank())
                c.drawText(line2, cx, h - h * 0.050f, paint(bold, w * 0.036f, android.graphics.Color.argb((a * 255f).toInt(), 49, 130, 246), Paint.Align.CENTER))
        }
        c.drawText("시공막내", cx, h - h * 0.022f, paint(med, w * 0.028f, android.graphics.Color.argb((a * 200f).toInt(), 90, 100, 114), Paint.Align.CENTER))
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
        /** 🕐 그 동네 대표 현장의 **시공 전 사진** — 동네 이름 → 사진. 있으면 도착 때 전→후로 와이프. (2026-10-28 사장님) */
        beforePhotos: Map<String, android.graphics.Bitmap> = emptyMap(),
        /**
         * 끝에서 **한 박자 쉬는** 비율. 0.12 면 88% 지점에서 다 달리고 남은 1.2초는 멈춰 있는다.
         *   전엔 마지막 현장 사진이 **0.6초만** 보이고 영상이 끝났다. (2026-09-25 사장님)
         *   다 그려진 지도가 잠깐 머무르는 게 마무리로도 낫다.
         */
        endHold: Float = 0f
    ) {
        val tE = if (endHold > 0f) (t / (1f - endHold)).coerceAtMost(1f) else t
        // 🎬 오프닝 훅 — 맨 앞 INTRO 동안 제목이 떴다 걷히며 여정 시작(기대감). 그동안 트럭은 출발선(t=0). (2026-10-28 사장님)
        val introP = (tE / INTRO).coerceIn(0f, 1f)
        @Suppress("NAME_SHADOWING")
        val t = ((tE - INTRO) / (1f - INTRO)).coerceIn(0f, 1f)
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
            drawPhotoFrame(ctx, c, d, t, w, h, photos, ride, introP)
            return
        }

        // ═══ 틀 F — 풀스크린 지도 + 오프닝 숫자 + 핵심 멈춤 리빌 + 엔드카드. (2026-10-28 사장님 A안) ═══
        val ordered = ordered0
        val tripStops = trip0?.stops ?: IntArray(ordered.size) { it }
        // 각 현장 도착 시각(arriveAt) — 멈춤 자리 잡는 데. driveT 와 무관하니 한 번만.
        val refRide = if (ordered.isEmpty()) null
            else com.detailline.callfollowcrm.util.MapRide.at(wayLL0, tripStops, 1f)
        val featIdx = pickFeatured(ordered)
        val featDriveT = FloatArray(featIdx.size) { refRide?.arriveAt?.getOrNull(featIdx[it]) ?: 0f }
        // 시간 축: [0,INTRO]=오프닝 · [INTRO,1-ENDCARD]=여정(핵심 멈춤) · [1-ENDCARD,1]=엔드카드. (END_HOLD=0 → tE=t)
        val jT = ((tE - INTRO) / (1f - INTRO - ENDCARD)).coerceIn(0f, 1f)
        val endP = ((tE - (1f - ENDCARD)) / ENDCARD).coerceIn(0f, 1f)
        val warp = warpTime(jT, featDriveT, REVEAL_FRAC)
        val driveT = warp.driveT
        val ride2 = if (ordered.isEmpty()) null
            else com.detailline.callfollowcrm.util.MapRide.at(wayLL0, tripStops, driveT)

        // ── 풀스크린 지도 ──
        var arrived = 0
        if (ordered.isNotEmpty() && ride2 != null) {
            val shot = com.detailline.callfollowcrm.util.MapRide.follow(
                b = com.detailline.callfollowcrm.util.MapRide.bounds(ordered.map { it.lon to it.lat }),
                at = ride2, t = driveT,
                stopsLL = ordered.map { it.lon to it.lat },
                restZoom = d.zoom, restPanX = d.panX, restPanY = d.panY
            )
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(c), Size(w.toFloat(), h.toFloat())
            ) {
                drawRegionMap(
                    spots = d.dots,
                    named = if (com.detailline.callfollowcrm.util.MapRide.showAllNames(shot.zoom))
                        d.dots.map { it.name }.toSet() else MapPalette.namedOf(d.dots),
                    measurer = null,
                    land = androidx.compose.ui.graphics.Color(MapPalette.LAND),
                    edge = androidx.compose.ui.graphics.Color(MapPalette.EDGE),
                    dot = androidx.compose.ui.graphics.Color(blue),
                    labelColor = androidx.compose.ui.graphics.Color(0xFF5A6472),
                    labelStyle = TextStyle(fontSize = 11.sp),
                    river = androidx.compose.ui.graphics.Color(MapPalette.RIVER),
                    progress = driveT,
                    geo = MapGeo.load(ctx),
                    trip = trip0,
                    zoom = shot.zoom, panX = shot.panX, panY = shot.panY,
                    onArrived = { arrived = it }
                )
            }
        }

        // ── HUD: 현장 칩 (여정 중, 리빌 아닐 때) ──
        if (jT > 0f && jT < 1f && warp.revealIdx < 0 && ordered.isNotEmpty()) {
            val nm = ordered.getOrNull(arrived)?.name ?: ""
            drawChip(c, w, h, "현장 ${(arrived + 1).coerceAtMost(ordered.size)} · $nm", bold)
        }

        // ── 핵심 현장 큰 리빌 (멈춤) ──
        if (warp.revealIdx in featIdx.indices) {
            val dot = ordered[featIdx[warp.revealIdx]]
            drawBigReveal(ctx, c, w, h, dot, photos[dot.name], beforePhotos[dot.name], warp.revealProg)
        }

        // ── 오프닝 ──
        drawIntro(ctx, c, d, w, h, introP)
        // ── 엔드카드 ──
        if (endP > 0f) drawEnd(ctx, c, d, w, h, endP)
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
        /** 안 주면 **곳 수에 맞춰** 정한다 — [secondsFor]. (2026-09-30 사장님) */
        seconds: Float = secondsFor(d.dots.size),
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
            RecordShot.loadPhoto(p, 400, 400)?.let { photos[dot.name] = it }
        }
        // 동네별 사진이 하나도 없으면 **그 달 대표 사진**이라도 쓴다(예전처럼 한 장).
        if (photos.isEmpty()) {
            RecordShot.loadPhoto(d.photoPath, 400, 400)?.let { b ->
                d.dots.minByOrNull { it.order }?.let { photos[it.name] = b }
            }
        }
        // 🕐 '시공 전' 사진도 **한 번만** 읽는다(지정된 현장만). 전→후 와이프용. (2026-10-28 사장님)
        val beforePhotos = LinkedHashMap<String, android.graphics.Bitmap>()
        for (dot in d.dots.sortedBy { it.order }.take(12)) {
            val bp = dot.beforePhotoPath ?: continue
            if (beforePhotos.containsKey(dot.name)) continue
            RecordShot.loadPhoto(bp, 400, 400)?.let { beforePhotos[dot.name] = it }
        }
        try {
        // ① 폰이 고르는 인코더(보통 하드웨어)로.
        VideoMaker.make(
            outFile = out, width = W, height = H, fps = 24, seconds = seconds, progress = forward
        ) { canvas, t -> drawFrame(ctx, canvas, d, t, W, H, photos, beforePhotos, END_HOLD) }
            ?.let { lastMadeSize = "$W × $H"; return it }
        retrying = true   // 여기부터는 "다른 방법으로 다시 만드는 중"
        // ② 안 되면 **소프트웨어 인코더**로. 느리지만 어느 폰에서나 된다.
        //   (갤S23U·안드로이드 16 에서 하드웨어가 말썽이었다 — 2026-09-25 사장님 폰)
        VideoMaker.softwareEncoder()?.let { sw ->
            VideoMaker.make(
                outFile = out, width = W, height = H, fps = 24, seconds = seconds,
                codecName = sw, progress = forward
            ) { canvas, t -> drawFrame(ctx, canvas, d, t, W, H, photos, beforePhotos, END_HOLD) }
                ?.let { lastMadeSize = "$W × $H"; return it }
        }
        // ③ 그래도 안 되면 **작게** 한 번 더. 작으면 되는 경우가 많다.
        //   ⚠️ 전엔 이 길만 **20컷**이었다 — 안 되는 건 **크기** 때문이지 컷 수 때문이 아니다.
        //   이 길로 떨어진 폰은 작긴 작은데 **뚝뚝 끊기까지** 했다.
        //   (2026-09-30 사장님 "영상이 … 약간 버벅이는데")
        //   크기만 줄이고 컷 수는 **24로 맞춘다.** 한 컷당 받는 양이 줄지 않게 양도 올렸다.
        val w2 = 540
        val h2 = 960
        return VideoMaker.make(
            outFile = out, width = w2, height = h2, fps = 24, seconds = seconds,
            bitRate = 4_200_000, progress = forward
        ) { canvas, t -> drawFrame(ctx, canvas, d, t, w2, h2, photos, beforePhotos, END_HOLD) }
            ?.also { lastMadeSize = "$w2 × $h2" }
        } finally {
            // 읽어둔 사진은 반드시 놓아준다 — 그대로 두면 메모리를 잡아먹는다.
            for (b in photos.values) runCatching { b.recycle() }
            for (b in beforePhotos.values) runCatching { b.recycle() }
        }
    }
}
