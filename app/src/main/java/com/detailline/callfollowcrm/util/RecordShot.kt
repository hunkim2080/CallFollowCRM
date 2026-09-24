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
    private const val STICKER_H = 440

    /** 그림에 들어가는 값 — **여기 없는 건 그림에 못 들어간다.** 손님 이름·번호·상세주소는 자리 자체가 없다. */
    data class Data(
        val no: Int,
        val monthLabel: String,
        val sites: Int,
        val workDays: Int,
        val towns: List<String>,
        val dots: List<RegionDot>,
        val bizName: String,
        val tradeName: String
    )

    private fun font(ctx: Context, id: Int): Typeface? = runCatching {
        ResourcesCompat.getFont(ctx, id)
    }.getOrNull()

    private fun paint(tf: Typeface?, sizePx: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = sizePx; this.color = color; textAlign = align
        }

    /**
     * 한 장 그린다.
     * @param transparent true = 배경 없는 스티커(내 사진 위에 얹는 용)
     */
    fun render(ctx: Context, d: Data, transparent: Boolean): Bitmap {
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

        var y = pad + 56f
        c.drawText("내 기록", pad, y, paint(bold, 34f, hint))
        y += 86f
        c.drawText("현장 %03d".format(d.no), pad, y, paint(xbold, 96f, blue))
        y += 52f
        c.drawText(
            buildString {
                append(d.monthLabel).append(" · ").append(d.sites).append("곳")
                if (d.workDays > d.sites) append(" · 현장 ").append(d.workDays).append("일")
            },
            pad, y, paint(bold, 36f, sub)
        )

        // ── 지도 ── 한 장일 때만. 스티커는 낮아서 지도까지 넣으면 답답하다.
        if (!transparent && d.dots.isNotEmpty()) {
            val mapTop = y + 40f
            val mapH = S - mapTop - 190f
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
                    progress = 1f
                )
            }
            c.restore()
        }

        // ── 동네 이름 ── (스티커는 바로 밑, 한 장은 아래쪽)
        val townY = if (transparent) y + 76f else h - 132f
        if (d.towns.isNotEmpty()) {
            val line = d.towns.take(6).joinToString(" · ") +
                if (d.towns.size > 6) " 외 ${d.towns.size - 6}곳" else ""
            c.drawText(line, pad, townY, paint(bold, 34f, ink))
        }

        // ── 맨 아래 — 상호. 크게 넣지 않는다(광고로 보이면 안 올린다). ──
        val footY = h - pad - 8f
        val who = listOfNotNull(
            d.bizName.takeIf { it.isNotBlank() },
            d.tradeName.takeIf { it.isNotBlank() }
        ).joinToString(" ")
        c.drawText(
            (if (who.isNotBlank()) "$who · " else "") + "시공막내로 만든 기록",
            pad, footY, paint(med, 28f, hint)
        )
        return bmp
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
