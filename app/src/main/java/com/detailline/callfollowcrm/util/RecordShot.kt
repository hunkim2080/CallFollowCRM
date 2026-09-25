package com.detailline.callfollowcrm.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.detailline.callfollowcrm.R
import com.detailline.callfollowcrm.presentation.component.RegionDot
import com.detailline.callfollowcrm.presentation.component.drawRegionMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * **인증샷** — 「내 기록」을 한 장의 그림으로. (2026-09-24 사장님 "kyro처럼 인증샷 만드는 기능은 없나?")
 *
 * 왜 글이 아니라 그림인가: 사장님이 실제로 올리는 건 **사진**이다.
 *   짧은 텍스트는 갈 곳이 없다 — 단톡방엔 뜻이 없고, 블로그는 웹 스튜디오가 훨씬 잘한다.
 *
 * 두 가지로 뽑는다:
 *   ① **정사각 한 장** — 바로 올린다. 단계가 제일 적다.
 *   ② **투명 스티커** — 배경이 비어 있어 **내 현장 사진 위에 얹는다**(KYRO 방식).
 *      ⚠️ KYRO 블로그의 "복사해서 붙여넣기" 는 아이폰 이야기다.
 *         안드로이드는 **저장 → 인스타 스토리 → 스티커 → 사진** 으로 얹는다.
 *
 * 화면을 찍는 게(PixelCopy) 아니라 **새로 그린다** — 화면에 다 보이지 않아도 되고,
 * 폰마다 크기가 달라지지 않는다. 인스타 정사각 표준인 1080 으로 고정.
 */
object RecordShot {

    private const val S = 1080          // 정사각 한 변
    // 스티커는 **내용에 딱 맞게**. 높으면 사진 위에서 빈 자리만 크게 먹는다(2026-09-24 폰에서 확인).
    private const val STICKER_H = 560

    /** 그림에 들어가는 값 — **여기 없는 건 그림에 못 들어간다.** 손님 이름·번호·상세주소는 자리 자체가 없다. */
    /**
     * 인증샷 **모양** 세 가지. (2026-09-24)
     *   STICKER — 배경이 빈 스티커. 인스타 스토리에서 내 현장 사진 위에 얹는다(기본).
     *   MAP     — **지도가 주인공.** 지도가 꽉 차고 숫자를 그 위에 얹는다.
     *   CARD    — 정사각 한 장. 숫자가 주인공이고 지도는 가운데 네모.
     */
    enum class Shape {
        STICKER,
        MAP,
        CARD,
        /** B — 지도 인증샷 + 오른쪽 아래에 현장 사진 한 장. (2026-09-25 사장님) */
        MAP_PHOTO,
        /** C — **현장 사진이 바탕 전체**, 그 위에 숫자·작은 지도·간판. */
        PHOTO
    }

    /**
     * 인증샷 **비율**. 모양과 따로 고른다. (사장님 시안 「이미지 비율」)
     *   전엔 [정사각] 이 모양 갈래에 섞여 있어서, "지도 크게 + 정사각" 을 못 골랐다.
     *   FEED   — 인스타 피드에서 **세로로 가장 크게** 잡히는 비율. 그래서 기본.
     *   SQUARE — 어디에 올려도 안 잘린다.
     *   STORY  — 스토리·릴스 덮개.
     */
    enum class Ratio(val h: Int, val label: String, val suffix: String) {
        FEED(1350, "피드 · 4:5", "_4x5"),
        SQUARE(1080, "정사각 · 1:1", ""),
        STORY(1920, "스토리 · 9:16", "_9x16")
    }

    data class Data(
        /**
         * **큰 숫자** — 무엇을 자랑할지는 사람마다 다르다. (2026-09-24 사장님)
         *   "이번 달 3집" 하나뿐이면 한가한 달엔 못 올린다. 그래서 고르게 한다.
         */
        val bigValue: String,
        val bigUnit: String,
        /** 그 숫자가 뭔지 한 줄 — "올해 다녀온 집". */
        val bigCaption: String,
        val no: Int,
        val monthLabel: String,
        val sites: Int,
        val workDays: Int,
        val towns: List<String>,
        val dots: List<RegionDot>,
        /** 고른 숫자 말고 **나머지 숫자 한 줄** — "이번 달 7집 · 동네 38곳". 비면 안 그린다. */
        val subLine: String,
        /** 손가락으로 맞춘 확대·이동 — **보이는 그대로** 그림에 담는다. (2026-09-25 사장님) */
        val zoom: Float = 1f,
        val panX: Float = 0f,
        val panY: Float = 0f,
        val bizName: String,
        val tradeName: String,
        /** 보고 전화하게. 비면 안 그린다. */
        val phone: String,
        /** 다니는 지역 한 줄 — "서울·경기". 광고에선 **어디까지 가는지**가 제일 궁금한 정보다. */
        val area: String,
        /**
         * **대표 현장 사진** 파일 경로. null 이면 사진이 들어가는 갈래를 못 쓴다.
         *   대표 = 그 현장에 제일 먼저 올린 사진. (2026-09-25 사장님)
         */
        val photoPath: String? = null
    )

    /**
     * 사진 파일을 **필요한 크기만큼만** 읽는다. 폰 사진은 4000×3000 이라 그냥 읽으면 메모리가 터진다.
     *   못 읽으면 null — 그럼 사진 없는 갈래로 떨어진다(깨진 그림을 넣느니 안 넣는다).
     */
    /**
     * 🔢 **큰 숫자 + 그 옆 단위** — 그림도 영상도 여기로 그린다.
     *
     * 띄우기를 네 군데에 각자 적어놨더니(12f · 10f · 폭의 1.4%) 두 자리 숫자에서
     * `km` 이 숫자에 **닿았다.** (2026-09-25 사장님 "글자가 겹쳤어 km 있는곳 간격")
     * 띄우기는 **글자 크기에 비례**한다 — 긴 숫자라 글자가 줄면 간격도 같이 줄어야 한다.
     *
     * @return 숫자만의 폭. 칸 너비를 재는 데 쓴다.
     */
    /**
     * 🧭 **다녀온 동네를 다닌 순서대로** 한 줄로.
     *
     * 전엔 `강서 · 관악 · 단원` 처럼 점으로 나열해서 **순서인지 그냥 목록인지 알 수 없었다.**
     * 사장님이 순서로 읽으셨다가 영상 속 트럭과 안 맞아 "왜 3을 안 거치는 느낌이지?" 하셨다.
     * (2026-09-25) 화살표로 두면 **다닌 길**이라는 게 한눈에 보인다.
     *
     * ⚠️ 이 글줄을 만드는 코드가 네 군데에 복사돼 있었다(영상 1 · 그림 3).
     *    한 군데만 고치면 나머지 셋은 점으로 남는다 — 그래서 여기 한 곳에 둔다.
     */
    internal fun townLine(towns: List<String>): String =
        towns.take(6).joinToString(" → ") +
            if (towns.size > 6) " 외 ${towns.size - 6}곳" else ""

    internal fun drawBigNumber(
        c: android.graphics.Canvas, x: Float, y: Float,
        value: String, unit: String, numP: Paint, unitP: Paint
    ): Float {
        c.drawText(value, x, y, numP)
        val w = numP.measureText(value)
        if (unit.isNotBlank()) c.drawText(unit, x + w + unitGap(numP), y, unitP)
        return w
    }

    /** 숫자와 단위 사이. 숫자 글자 크기의 24% — 어떤 자릿수에서도 닿지 않는 선. */
    internal fun unitGap(numP: Paint): Float = numP.textSize * 0.24f

    internal fun loadPhoto(path: String?, reqW: Int, reqH: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
                sample *= 2
            }
            android.graphics.BitmapFactory.decodeFile(
                path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }.getOrNull()
    }

    /**
     * 사진을 네모에 **꽉 채워** 그린다(가운데 기준으로 잘라서). 찌그러뜨리지 않는다 —
     *   현장 사진이 늘어나 보이면 일한 게 우스워 보인다.
     */
    internal fun drawCover(c: android.graphics.Canvas, bmp: Bitmap, dst: android.graphics.RectF) {
        val sw = bmp.width.toFloat(); val sh = bmp.height.toFloat()
        val scale = maxOf(dst.width() / sw, dst.height() / sh)
        val w = sw * scale; val h = sh * scale
        val left = dst.centerX() - w / 2; val top = dst.centerY() - h / 2
        c.save()
        c.clipRect(dst)
        c.drawBitmap(bmp, null, android.graphics.RectF(left, top, left + w, top + h),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        c.restore()
    }

    private fun font(ctx: Context, id: Int): Typeface? = runCatching {
        ResourcesCompat.getFont(ctx, id)
    }.getOrNull()

    private fun paint(tf: Typeface?, sizePx: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = sizePx; this.color = color; textAlign = align
        }

    /**
     * **폭에 맞을 때까지 글자를 줄인다.**
     *   사장님 상호가 "줄눈시공 탄성코트의 시작, 하우스픽" 처럼 길 수 있다.
     *   크기를 고정해두면 그림 밖으로 넘친다(2026-09-24 미리보기에서 확인).
     */
    private fun fit(p: Paint, text: String, maxW: Float, from: Float, min: Float): Paint {
        var sz = from
        p.textSize = sz
        while (sz > min && p.measureText(text) > maxW) {
            sz -= 2f
            p.textSize = sz
        }
        return p
    }

    /**
     * 한 장 그린다.
     * @param transparent true = 배경 없는 스티커(내 사진 위에 얹는 용)
     */
    /**
     * **지도가 주인공인 한 장.** (2026-09-24 사장님이 가져온 조언)
     *   지도가 위에서 아래까지 꽉 차고, 큰 숫자를 그 위에 얹는다.
     *   "수도권을 이만큼 돌았다" 가 글이 아니라 **그림 한 장**으로 읽히게 하는 게 목적이다.
     */
    private fun renderMapHero(
        ctx: Context, d: Data, ratio: Ratio, sign: Boolean, withPhoto: Boolean = false
    ): Bitmap {
        val H = ratio.h
        val bmp = Bitmap.createBitmap(S, H, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)

        val bold = font(ctx, R.font.pretendard_bold)
        val xbold = font(ctx, R.font.pretendard_extrabold)
        val med = font(ctx, R.font.pretendard_medium)

        val blue = 0xFF3182F6.toInt()
        val ink = 0xFF0B0F19.toInt()
        val sub = 0xFF5A6472.toInt()
        val hint = 0xFF9AA3AF.toInt()
        val pad = 64f

        c.drawColor(0xFFF7F8FA.toInt())

        // ── 지도 — **여백 없이 꽉.** 아래는 나머지 숫자 · 동네 이름 · (켜져 있으면) 간판 자리.
        //   간판을 끄면 그만큼 **지도가 더 커진다** — 빈자리를 남기지 않는다.
        val mapH = H - (if (sign) 292f else 150f)
        c.save()
        c.clipRect(0f, 0f, S.toFloat(), mapH)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(c), Size(S.toFloat(), mapH)
        ) {
            drawRegionMap(
                spots = d.dots,
                named = MapPalette.namedOf(d.dots),
                measurer = null,
                land = androidx.compose.ui.graphics.Color(MapPalette.LAND),
                edge = androidx.compose.ui.graphics.Color(MapPalette.EDGE),
                dot = androidx.compose.ui.graphics.Color(0xFF3182F6),
                labelColor = androidx.compose.ui.graphics.Color(0xFF5A6472),
                labelStyle = TextStyle(fontSize = 10.sp),
                river = androidx.compose.ui.graphics.Color(MapPalette.RIVER),
                progress = 1f,
                // 🔴 2026-09-25 사장님 "다운하기 전엔 이렇게, 다운하면 이렇게" —
                //   여기만 **진짜 지도를 안 넘겨서** 옆 손그림(바다·길 없는)으로 그렸다.
                //   영상은 넘겼기 때문에 **미리보기와 다운받은 게 달랐다.**
                //   보이는 것과 나오는 게 다르면 미리보기가 쓸모가 없다.
                geo = MapGeo.load(ctx),
                trip = MapGeo.fullRoute(ctx, d.dots.sortedBy { it.order }.map { it.lon to it.lat }),
                zoom = d.zoom, panX = d.panX, panY = d.panY
            )
        }
        c.restore()

        // ── 큰 숫자를 지도 **위에** 얹는다. 흰 알약을 깔아야 지도 위에서도 읽힌다.
        val bigP = fit(paint(xbold, 118f, blue), d.bigValue, S - pad * 2 - 200f, 118f, 70f)
        val unitP = paint(bold, 46f, blue)
        val capP = paint(bold, 34f, sub)
        val monP = paint(bold, 30f, hint)
        val wNum = bigP.measureText(d.bigValue) +
            (if (d.bigUnit.isNotBlank()) unitP.measureText(d.bigUnit) + unitGap(bigP) else 0f)
        val boxW = maxOf(wNum, capP.measureText(d.bigCaption), monP.measureText(d.monthLabel)) + 64f
        val boxTop = 52f
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xF2FFFFFF.toInt()
            setShadowLayer(20f, 0f, 6f, 0x1F000000)
        }
        c.drawRoundRect(pad / 2, boxTop, pad / 2 + boxW, boxTop + 254f, 34f, 34f, plate)

        var y = boxTop + 58f
        c.drawText(d.monthLabel, pad, y, monP)
        y += 96f
        drawBigNumber(c, pad, y, d.bigValue, d.bigUnit, bigP, unitP)
        y += 46f
        c.drawText(d.bigCaption, pad, y, capP)

        // ── 나머지 숫자 한 줄 — 조언 ②('누적 수치 강조'). 지도 아래, 동네 이름 위.
        var by = mapH + 46f
        if (d.subLine.isNotBlank()) {
            c.drawText(d.subLine, pad, by, fit(paint(bold, 30f, blue), d.subLine, S - pad * 2, 30f, 22f))
            by += 46f
        }
        if (d.towns.isNotEmpty()) {
            val line = townLine(d.towns)
            c.drawText(line, pad, by, fit(paint(med, 28f, sub), line, S - pad * 2, 28f, 21f))
        }

        // ── B — 오른쪽 아래에 **현장 사진 한 장.** 흰 테를 둘러 지도 위에서 떠 보이게.
        //   "다녀왔다" 위에 "이렇게 해놨다" 를 한 장 얹는 것. (2026-09-25 사장님)
        if (withPhoto) {
            val ph = loadPhoto(d.photoPath, 420, 420)
            if (ph != null) {
                // 오른쪽 **위**에 둔다 — 큰 숫자 알약이 왼쪽 위라 자리가 비고,
                //   다닌 길(점)은 가운데~아래에 몰리는 편이라 덜 가린다.
                //   (2026-09-25 폰 확인: 오른쪽 아래에 뒀더니 영통·동탄 점을 덮었다)
                val side = 272f
                val right = S - pad
                val top2 = pad + 14f
                val box = android.graphics.RectF(right - side, top2, right, top2 + side)
                val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    setShadowLayer(16f, 0f, 5f, 0x33000000)
                }
                c.drawRoundRect(
                    box.left - 10f, box.top - 10f, box.right + 10f, box.bottom + 10f, 22f, 22f, frame
                )
                val path = android.graphics.Path().apply { addRoundRect(box, 14f, 14f, android.graphics.Path.Direction.CW) }
                c.save(); c.clipPath(path); drawCover(c, ph, box); c.restore()
                ph.recycle()
            }
        }

        if (sign) drawSign(c, d, bold, xbold, med, ink, blue, hint, H.toFloat(), pad)
        return bmp
    }

    /**
     * **C — 현장 사진이 바탕 전체.** 그 위에 큰 숫자 · 작은 지도 · 간판.
     *
     * 왜: 지금 [사진 위에] 스티커는 사장님이 **인스타 스토리에서 손으로 얹어야** 한다
     *   (저장 → 스토리 → 스티커 → 사진, 네 단계). 그 합성을 **앱이 대신 한다.**
     *   사진을 못 읽으면 null — 부르는 쪽이 지도 갈래로 내린다.
     */
    private fun renderPhotoHero(ctx: Context, d: Data, ratio: Ratio, sign: Boolean): Bitmap? {
        val H = ratio.h
        val photo = loadPhoto(d.photoPath, S, H) ?: return null
        val bmp = Bitmap.createBitmap(S, H, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)

        val bold = font(ctx, R.font.pretendard_bold)
        val xbold = font(ctx, R.font.pretendard_extrabold)
        val med = font(ctx, R.font.pretendard_medium)
        val pad = 64f
        val white = 0xFFFFFFFF.toInt()

        // ① 사진을 꽉 채워 깐다.
        drawCover(c, photo, android.graphics.RectF(0f, 0f, S.toFloat(), H.toFloat()))
        photo.recycle()

        // ② 위아래로 어둡게 — 어떤 사진 위에서든 흰 글자가 읽히게. 가운데(시공 자리)는 안 건드린다.
        val top = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, H * 0.38f,
                intArrayOf(0xB3000000.toInt(), 0x00000000), null,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, S.toFloat(), H * 0.38f, top)
        val bot = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, H * 0.62f, 0f, H.toFloat(),
                intArrayOf(0x00000000, 0xC4000000.toInt()), null,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, H * 0.62f, S.toFloat(), H.toFloat(), bot)

        // ③ 큰 숫자 — 왼쪽 위.
        var y = pad + 46f
        c.drawText(d.monthLabel, pad, y, paint(bold, 30f, 0xCCFFFFFF.toInt()))
        y += 104f
        val bigP = fit(paint(xbold, 126f, white), d.bigValue, S - pad * 2 - 260f, 126f, 72f)
        drawBigNumber(c, pad, y, d.bigValue, d.bigUnit, bigP, paint(bold, 48f, white))
        y += 48f
        c.drawText(d.bigCaption, pad, y, paint(bold, 34f, 0xE6FFFFFF.toInt()))

        // ④ 작은 지도 — 오른쪽 위. "어디어디 다녔다"를 사진 위에 한 뼘으로.
        if (d.dots.isNotEmpty()) {
            val side = 260f
            val box = android.graphics.RectF(S - pad - side, pad, S - pad, pad + side)
            val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xF2FFFFFF.toInt(); setShadowLayer(14f, 0f, 4f, 0x40000000)
            }
            c.drawRoundRect(box.left - 8f, box.top - 8f, box.right + 8f, box.bottom + 8f, 20f, 20f, frame)
            c.save()
            c.clipRect(box)
            c.translate(box.left, box.top)
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr, Canvas(c), Size(side, side)
            ) {
                drawRegionMap(
                    spots = d.dots,
                    named = emptySet(),          // 한 뼘짜리라 이름은 안 넣는다(겹쳐서 못 읽는다)
                    measurer = null,
                    land = androidx.compose.ui.graphics.Color(MapPalette.LAND),
                    edge = androidx.compose.ui.graphics.Color(MapPalette.EDGE),
                    dot = androidx.compose.ui.graphics.Color(0xFF3182F6),
                    labelColor = androidx.compose.ui.graphics.Color(0xFF5A6472),
                    labelStyle = TextStyle(fontSize = 8.sp),
                    river = androidx.compose.ui.graphics.Color(MapPalette.RIVER),
                    progress = 1f,
                    geo = MapGeo.load(ctx),
                    trip = MapGeo.fullRoute(ctx, d.dots.sortedBy { it.order }.map { it.lon to it.lat }),
                    zoom = d.zoom, panX = d.panX, panY = d.panY
                )
            }
            c.restore()
        }

        // ⑤ 동네 이름 + 간판 — 아래. 사진 위라 전부 흰 글씨.
        // ⚠️ 상호(50px)가 H-pad-62 에 앉는다. 118 이면 6px 밖에 안 떨어져 **붙어 보인다**
        //   (2026-09-25 폰 확인) → 한 줄 만큼 더 띄운다.
        var by = H - pad - (if (sign) 142f else 8f)
        if (d.towns.isNotEmpty()) {
            val line = townLine(d.towns)
            c.drawText(line, pad, by, fit(paint(med, 28f, 0xD9FFFFFF.toInt()), line, S - pad * 2, 28f, 21f))
            by += 46f
        }
        if (sign) {
            val name = d.bizName.trim()
            if (name.isNotBlank()) {
                c.drawText(name, pad, H - pad - 62f, fit(paint(xbold, 50f, white), name, S - pad * 2, 50f, 30f))
            }
            val line2 = listOfNotNull(
                listOfNotNull(
                    d.area.takeIf { it.isNotBlank() }, d.tradeName.takeIf { it.isNotBlank() }
                ).joinToString(" ").takeIf { it.isNotBlank() },
                d.phone.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (line2.isNotBlank()) {
                c.drawText(line2, pad, H - pad - 14f,
                    fit(paint(bold, 32f, 0xF2FFFFFF.toInt()), line2, S - pad * 2 - 150f, 32f, 24f))
            }
            c.drawText("시공막내", S - pad, H - pad - 14f,
                paint(med, 22f, 0x99FFFFFF.toInt(), Paint.Align.RIGHT))
        }
        return bmp
    }

    /**
     * @param ratio 그림 비율. 스티커는 **내용에 딱 맞는 높이**라 비율을 안 쓴다.
     * @param sign  맨 아래 간판(업체명·연락처)을 넣을지. 끄면 그만큼 그림이 더 시원해진다.
     *              (사장님 시안: "개인 기록은 담백하게, 홍보할 때는 연락처를 더해요")
     */
    fun render(ctx: Context, d: Data, shape: Shape, ratio: Ratio = Ratio.FEED, sign: Boolean = true): Bitmap {
        // 사진이 없으면(또는 못 읽으면) 사진 갈래를 지도 갈래로 내린다 — 빈 네모를 내놓지 않는다.
        var mode = shape
        if ((mode == Shape.PHOTO || mode == Shape.MAP_PHOTO) && d.photoPath.isNullOrBlank()) {
            mode = Shape.MAP
        }
        // 갈 곳이 없으면(주소가 하나도 안 붙었으면) 지도 대신 한 장으로. 빈 지도는 자랑이 안 된다.
        if (mode == Shape.MAP && d.dots.isEmpty()) mode = Shape.CARD
        if (mode == Shape.PHOTO) return renderPhotoHero(ctx, d, ratio, sign) ?: renderMapHero(ctx, d, ratio, sign)
        if (mode == Shape.MAP || mode == Shape.MAP_PHOTO) {
            return renderMapHero(ctx, d, ratio, sign, withPhoto = mode == Shape.MAP_PHOTO)
        }
        val transparent = mode == Shape.STICKER
        // 스티커는 **사진 위에 얹는 것**이라 비율이 뜻이 없다 — 내용에 딱 맞게. 간판을 끄면 더 납작해진다.
        val h = if (transparent) (if (sign) STICKER_H else 400) else ratio.h
        val bmp = Bitmap.createBitmap(S, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)

        val bold = font(ctx, R.font.pretendard_bold)
        val xbold = font(ctx, R.font.pretendard_extrabold)
        val med = font(ctx, R.font.pretendard_medium)

        val blue = 0xFF3182F6.toInt()
        val ink = 0xFF0B0F19.toInt()
        val sub = 0xFF5A6472.toInt()
        val hint = 0xFF9AA3AF.toInt()

        // ── 바탕 ── 스티커는 **비워 둔다**(사진이 그대로 비친다). 한 장은 흰 카드.
        val pad = 64f
        if (!transparent) {
            c.drawColor(0xFFF7F8FA.toInt())
            val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
            c.drawRoundRect(pad / 2, pad / 2, S - pad / 2, h - pad / 2, 44f, 44f, card)
        } else {
            // 스티커도 글자가 어떤 사진 위에서든 읽히게 **흰 카드**는 그린다(사진을 다 덮진 않는다).
            val card = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                setShadowLayer(18f, 0f, 6f, 0x33000000)
            }
            c.drawRoundRect(pad / 2, pad / 2, S - pad / 2, h - pad / 2, 44f, 44f, card)
        }

        var y = pad + 54f
        // 맨 위 = 언제 것인지. 그 밑이 **고른 큰 숫자**.
        c.drawText(d.monthLabel, pad, y, paint(bold, 32f, hint))
        y += 96f
        val bigP = fit(paint(xbold, 112f, blue), d.bigValue, S - pad * 2 - 160f, 112f, 64f)
        // 단위는 숫자 옆에 작게.
        val bigW = drawBigNumber(c, pad, y, d.bigValue, d.bigUnit, bigP, paint(bold, 44f, blue))
        y += 48f
        c.drawText(d.bigCaption, pad, y, paint(bold, 34f, sub))

        // ── 지도 ── 한 장일 때만. 스티커는 낮아서 지도까지 넣으면 답답하다.
        if (!transparent && d.dots.isNotEmpty()) {
            val mapTop = y + 40f
            val mapH = h - mapTop - (if (sign) 310f else 170f)
            c.save()
            c.translate(pad, mapTop)
            c.clipRect(0f, 0f, S - pad * 2, mapH)
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr, Canvas(c), Size(S - pad * 2, mapH)
            ) {
                drawRegionMap(
                    spots = d.dots,
                    named = MapPalette.namedOf(d.dots),
                    measurer = null,
                    land = androidx.compose.ui.graphics.Color(MapPalette.LAND),
                    edge = androidx.compose.ui.graphics.Color(MapPalette.EDGE),
                    dot = androidx.compose.ui.graphics.Color(0xFF3182F6),
                    labelColor = androidx.compose.ui.graphics.Color(0xFF5A6472),
                    labelStyle = TextStyle(fontSize = 10.sp),
                    river = androidx.compose.ui.graphics.Color(MapPalette.RIVER),
                    progress = 1f,
                    geo = MapGeo.load(ctx),
                    trip = MapGeo.fullRoute(ctx, d.dots.sortedBy { it.order }.map { it.lon to it.lat }),
                    zoom = d.zoom, panX = d.panX, panY = d.panY
                )
            }
            c.restore()
        }

        // ── 동네 이름 ── (스티커는 바로 밑, 한 장은 아래쪽)
        val townY = if (transparent) y + 62f else h - (if (sign) 252f else 110f)
        if (d.towns.isNotEmpty()) {
            val line = townLine(d.towns)
            c.drawText(line, pad, townY, fit(paint(bold, 34f, ink), line, S - pad * 2, 34f, 24f))
        }

        // ── 맨 아래 = **간판.** (2026-09-24 사장님 "광고야 광고")
        //   전엔 상호를 구석에 작게 박았다. SNS 에 올리는 건 결국 광고인데 작게 박으면 효과가 없다.
        //   보는 사람이 **누구한테 전화하면 되는지** 바로 알아야 한다.
        if (sign) drawSign(c, d, bold, xbold, med, ink, blue, hint, h.toFloat(), pad)
        return bmp
    }

    /**
     * 맨 아래 **간판** — 상호 · 지역·업종·번호 · 구석에 작게 우리 이름. (2026-09-24 사장님 "광고야 광고")
     *   SNS 에 올리는 건 결국 광고다. 보는 사람이 **누구한테 전화하면 되는지** 바로 알아야 한다.
     *   세 모양(스티커·지도·한 장)이 같은 간판을 쓴다.
     */
    private fun drawSign(
        c: android.graphics.Canvas, d: Data,
        bold: Typeface?, xbold: Typeface?, med: Typeface?,
        ink: Int, blue: Int, hint: Int, h: Float, pad: Float
    ) {
        val name = d.bizName.trim()
        if (name.isNotBlank()) {
            // 상호 — 제일 크게. 길면 줄인다(넘치면 그림 밖으로 나간다).
            c.drawText(name, pad, h - pad - 70f, fit(paint(xbold, 52f, ink), name, S - pad * 2, 52f, 30f))
        }
        // 업종 + 지역 + 번호 한 줄. "서울·경기 줄눈시공 · 010-0000-0000"
        val line2 = listOfNotNull(
            listOfNotNull(
                d.area.takeIf { it.isNotBlank() },
                d.tradeName.takeIf { it.isNotBlank() }
            ).joinToString(" ").takeIf { it.isNotBlank() },
            d.phone.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (line2.isNotBlank()) {
            // 우리 이름 자리(오른쪽 끝)를 빼고 잰다 — 겹치면 둘 다 못 읽는다.
            c.drawText(line2, pad, h - pad - 16f,
                fit(paint(bold, 32f, blue), line2, S - pad * 2 - 150f, 32f, 24f))
        }
        // 우리 이름은 **아주 작게 구석에**. 사장님 광고지 우리 광고가 아니다.
        c.drawText("시공막내", S - pad, h - pad - 16f, paint(med, 22f, hint, Paint.Align.RIGHT))
    }

    /** 영상 보내기 — 인스타·카톡으로. 그림과 같은 길(FileProvider)을 쓴다. */
    fun shareVideo(ctx: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "영상 올리기").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    /** 영상을 **사진첩에** 저장 — 나중에 인스타에서 골라 올릴 수 있게. */
    suspend fun saveVideo(ctx: Context, file: File, name: String): android.net.Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/시공막내")
                    }
                }
                val uri = ctx.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@runCatching null
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                uri
            }.getOrNull()
        }

    /** 방금 저장한 것을 **열어본다** — 어디 갔는지 볼 길이 있어야 한다. (2026-09-25 기본 UX) */
    fun openSaved(ctx: Context, uri: android.net.Uri, mime: String) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    /** 사진첩에 저장 — **어디에 저장됐는지**(주소)를 돌려준다. 실패하면 null. */
    suspend fun save(ctx: Context, bmp: Bitmap, name: String): android.net.Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/시공막내")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = ctx.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@runCatching null
                ctx.contentResolver.openOutputStream(uri)?.use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                } ?: return@runCatching null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    ctx.contentResolver.update(uri, values, null, null)
                }
                uri
            }.getOrNull()
        }

    /** 다른 앱으로 보내기(인스타·카톡 등). 견적서 발행과 **같은 길**(cacheDir/shared + FileProvider). */
    fun share(ctx: Context, bmp: Bitmap, name: String) {
        runCatching {
            val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
            val f = File(dir, "$name.png")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "어디에 올릴까요?").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
