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
        val area: String
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
    fun drawFrame(ctx: Context, c: Canvas, d: Data, t: Float, w: Int = W, h: Int = H) {
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
                    named = d.dots.sortedByDescending { it.count }.take(5).map { it.name }.toSet(),
                    measurer = null,
                    land = androidx.compose.ui.graphics.Color(MapPalette.LAND),
                    edge = androidx.compose.ui.graphics.Color(MapPalette.EDGE),
                    dot = androidx.compose.ui.graphics.Color(blue),
                    labelColor = androidx.compose.ui.graphics.Color(0xFF5A6472),
                    labelStyle = TextStyle(fontSize = 10.sp),
                    river = androidx.compose.ui.graphics.Color(MapPalette.RIVER),
                    progress = t,
                    geo = MapGeo.load(ctx)
                )
            }
            c.restore()
        }

        // ── 아래: 동네 이름 ──
        var by = mapTop + mapH + h * 0.045f
        if (d.towns.isNotEmpty()) {
            val line = d.towns.take(6).joinToString(" · ") +
                if (d.towns.size > 6) " 외 ${d.towns.size - 6}곳" else ""
            c.drawText(line, pad, by, fit(paint(bold, w * 0.040f, ink), line, w - pad * 2, w * 0.040f, w * 0.028f))
        }

        // ── 맨 아래: 간판 ── 상호 크게 · 지역·업종·번호. SNS 에 올리는 건 결국 광고다.
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
        return VideoMaker.make(
            outFile = out, width = W, height = H, fps = 24, seconds = seconds, progress = progress
        ) { canvas, t -> drawFrame(ctx, canvas, d, t) }
    }
}
