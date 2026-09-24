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
    enum class Shape { STICKER, MAP, CARD }

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
        val bizName: String,
        val tradeName: String,
        /** 보고 전화하게. 비면 안 그린다. */
        val phone: String,
        /** 다니는 지역 한 줄 — "서울·경기". 광고에선 **어디까지 가는지**가 제일 궁금한 정보다. */
        val area: String
    )

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
    private fun renderMapHero(ctx: Context, d: Data): Bitmap {
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
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

        // ── 지도 — **여백 없이 꽉.** 아래 292 는 나머지 숫자 · 동네 이름 · 간판 자리.
        val mapH = S - 292f
        c.save()
        c.clipRect(0f, 0f, S.toFloat(), mapH)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(c), Size(S.toFloat(), mapH)
        ) {
            drawRegionMap(
                spots = d.dots,
                named = d.dots.sortedByDescending { it.count }.take(5).map { it.name }.toSet(),
                measurer = null,
                land = androidx.compose.ui.graphics.Color(0xFFE9ECF0),
                edge = androidx.compose.ui.graphics.Color(0xFFCFD5DD),
                dot = androidx.compose.ui.graphics.Color(0xFF3182F6),
                labelColor = androidx.compose.ui.graphics.Color(0xFF5A6472),
                labelStyle = TextStyle(fontSize = 10.sp),
                river = androidx.compose.ui.graphics.Color(0x4D3182F6),
                progress = 1f
            )
        }
        c.restore()

        // ── 큰 숫자를 지도 **위에** 얹는다. 흰 알약을 깔아야 지도 위에서도 읽힌다.
        val bigP = fit(paint(xbold, 118f, blue), d.bigValue, S - pad * 2 - 200f, 118f, 70f)
        val unitP = paint(bold, 46f, blue)
        val capP = paint(bold, 34f, sub)
        val monP = paint(bold, 30f, hint)
        val wNum = bigP.measureText(d.bigValue) +
            (if (d.bigUnit.isNotBlank()) unitP.measureText(d.bigUnit) + 12f else 0f)
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
        c.drawText(d.bigValue, pad, y, bigP)
        if (d.bigUnit.isNotBlank()) {
            c.drawText(d.bigUnit, pad + bigP.measureText(d.bigValue) + 12f, y, unitP)
        }
        y += 46f
        c.drawText(d.bigCaption, pad, y, capP)

        // ── 나머지 숫자 한 줄 — 조언 ②('누적 수치 강조'). 지도 아래, 동네 이름 위.
        var by = mapH + 46f
        if (d.subLine.isNotBlank()) {
            c.drawText(d.subLine, pad, by, fit(paint(bold, 30f, blue), d.subLine, S - pad * 2, 30f, 22f))
            by += 46f
        }
        if (d.towns.isNotEmpty()) {
            val line = d.towns.take(6).joinToString(" · ") +
                if (d.towns.size > 6) " 외 ${d.towns.size - 6}곳" else ""
            c.drawText(line, pad, by, fit(paint(med, 28f, sub), line, S - pad * 2, 28f, 21f))
        }

        drawSign(c, d, bold, xbold, med, ink, blue, hint, S.toFloat(), pad)
        return bmp
    }

    fun render(ctx: Context, d: Data, shape: Shape): Bitmap {
        // 갈 곳이 없으면(주소가 하나도 안 붙었으면) 지도 대신 한 장으로. 빈 지도는 자랑이 안 된다.
        val mode = if (shape == Shape.MAP && d.dots.isEmpty()) Shape.CARD else shape
        if (mode == Shape.MAP) return renderMapHero(ctx, d)
        val transparent = mode == Shape.STICKER
        val h = if (transparent) STICKER_H else S
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
        c.drawText(d.bigValue, pad, y, bigP)
        // 단위는 숫자 옆에 작게.
        val bigW = bigP.measureText(d.bigValue)
        if (d.bigUnit.isNotBlank()) {
            c.drawText(d.bigUnit, pad + bigW + 10f, y, paint(bold, 44f, blue))
        }
        y += 48f
        c.drawText(d.bigCaption, pad, y, paint(bold, 34f, sub))

        // ── 지도 ── 한 장일 때만. 스티커는 낮아서 지도까지 넣으면 답답하다.
        if (!transparent && d.dots.isNotEmpty()) {
            val mapTop = y + 40f
            val mapH = S - mapTop - 310f
            c.save()
            c.translate(pad, mapTop)
            c.clipRect(0f, 0f, S - pad * 2, mapH)
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr, Canvas(c), Size(S - pad * 2, mapH)
            ) {
                drawRegionMap(
                    spots = d.dots,
                    named = d.dots.sortedByDescending { it.count }.take(4).map { it.name }.toSet(),
                    measurer = null,
                    land = androidx.compose.ui.graphics.Color(0xFFEEF0F3),
                    edge = androidx.compose.ui.graphics.Color(0xFFD9D3C4),
                    dot = androidx.compose.ui.graphics.Color(0xFF3182F6),
                    labelColor = androidx.compose.ui.graphics.Color(0xFF5A6472),
                    labelStyle = TextStyle(fontSize = 10.sp),
                    river = androidx.compose.ui.graphics.Color(0x4D3182F6),
                    progress = 1f,
                    geo = MapGeo.load(ctx)
                )
            }
            c.restore()
        }

        // ── 동네 이름 ── (스티커는 바로 밑, 한 장은 아래쪽)
        val townY = if (transparent) y + 62f else h - 252f
        if (d.towns.isNotEmpty()) {
            val line = d.towns.take(6).joinToString(" · ") +
                if (d.towns.size > 6) " 외 ${d.towns.size - 6}곳" else ""
            c.drawText(line, pad, townY, fit(paint(bold, 34f, ink), line, S - pad * 2, 34f, 24f))
        }

        // ── 맨 아래 = **간판.** (2026-09-24 사장님 "광고야 광고")
        //   전엔 상호를 구석에 작게 박았다. SNS 에 올리는 건 결국 광고인데 작게 박으면 효과가 없다.
        //   보는 사람이 **누구한테 전화하면 되는지** 바로 알아야 한다.
        drawSign(c, d, bold, xbold, med, ink, blue, hint, h.toFloat(), pad)
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
            val uri = FileProvider.getUriForFile(ctx, "${'$'}{ctx.packageName}.fileprovider", file)
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
    suspend fun saveVideo(ctx: Context, file: File, name: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "${'$'}name.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/시공막내")
                    }
                }
                val uri = ctx.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@runCatching false
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                true
            }.getOrDefault(false)
        }

    /** 사진첩에 저장. */
    suspend fun save(ctx: Context, bmp: Bitmap, name: String): Boolean =
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
                ) ?: return@runCatching false
                ctx.contentResolver.openOutputStream(uri)?.use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                } ?: return@runCatching false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    ctx.contentResolver.update(uri, values, null, null)
                }
                true
            }.getOrDefault(false)
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
