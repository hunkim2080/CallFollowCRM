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
        val sign: Boolean = true
    )

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
        photos: Map<String, android.graphics.Bitmap> = emptyMap()
    ) {
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

        // ── 위: 달 · 큰 숫자 · 이름 ──
        var y = pad + h * 0.045f
        c.drawText(d.monthLabel, pad, y, paint(bold, w * 0.036f, hint))
        y += h * 0.062f
        val bigP = fit(paint(xbold, w * 0.145f, blue), d.metricValue, w - pad * 2 - w * 0.22f, w * 0.145f, w * 0.085f)
        c.drawText(d.metricValue, pad, y, bigP)
        val numW = bigP.measureText(d.metricValue)
        if (d.metricUnit.isNotBlank()) {
            c.drawText(d.metricUnit, pad + numW + w * 0.014f, y, paint(bold, w * 0.058f, blue))
        }
        y += h * 0.031f
        c.drawText(d.metricLabel, pad, y, paint(bold, w * 0.040f, sub))

        // ── 가운데: 지도 ── 세로로 길게 준다. 릴스는 위아래가 넉넉하다.
        val mapTop = y + h * 0.028f
        val mapH = h * 0.545f
        // 지도가 **몇 번째 현장까지 왔는지** 알려준다 — 사진을 그걸로 고른다.
        var arrived = 0
        if (d.dots.isNotEmpty()) {
            c.save()
            c.translate(0f, mapTop)
            c.clipRect(0f, 0f, w.toFloat(), mapH)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(c), Size(w.toFloat(), mapH)
            ) {
                drawRegionMap(
                    spots = d.dots,
                    named = MapPalette.namedOf(d.dots),
                    measurer = null,
                    land = androidx.compose.ui.graphics.Color(MapPalette.LAND),
                    edge = androidx.compose.ui.graphics.Color(MapPalette.EDGE),
                    dot = androidx.compose.ui.graphics.Color(blue),
                    labelColor = androidx.compose.ui.graphics.Color(0xFF5A6472),
                    labelStyle = TextStyle(fontSize = 10.sp),
                    river = androidx.compose.ui.graphics.Color(MapPalette.RIVER),
                    progress = t,
                    geo = MapGeo.load(ctx),
                    trip = MapGeo.fullRoute(ctx, d.dots.sortedBy { it.order }.map { it.lon to it.lat }),
                    zoom = d.zoom, panX = d.panX, panY = d.panY,
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
            val line = d.towns.take(6).joinToString(" · ") +
                if (d.towns.size > 6) " 외 ${d.towns.size - 6}곳" else ""
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
    suspend fun make(
        ctx: Context,
        d: Data,
        seconds: Float = 10f,
        progress: VideoMaker.Progress? = null
    ): File? {
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
            outFile = out, width = W, height = H, fps = 24, seconds = seconds, progress = progress
        ) { canvas, t -> drawFrame(ctx, canvas, d, t, W, H, photos) }?.let { return it }
        // ② 안 되면 **소프트웨어 인코더**로. 느리지만 어느 폰에서나 된다.
        //   (갤S23U·안드로이드 16 에서 하드웨어가 말썽이었다 — 2026-09-25 사장님 폰)
        VideoMaker.softwareEncoder()?.let { sw ->
            VideoMaker.make(
                outFile = out, width = W, height = H, fps = 24, seconds = seconds,
                codecName = sw, progress = progress
            ) { canvas, t -> drawFrame(ctx, canvas, d, t, W, H, photos) }?.let { return it }
        }
        // ③ 그래도 안 되면 **작게** 한 번 더. 작으면 되는 경우가 많다.
        val w2 = 540
        val h2 = 960
        return VideoMaker.make(
            outFile = out, width = w2, height = h2, fps = 20, seconds = seconds,
            bitRate = 3_500_000, progress = progress
        ) { canvas, t -> drawFrame(ctx, canvas, d, t, w2, h2, photos) }
        } finally {
            // 읽어둔 사진은 반드시 놓아준다 — 그대로 두면 메모리를 잡아먹는다.
            for (b in photos.values) runCatching { b.recycle() }
        }
    }
}
