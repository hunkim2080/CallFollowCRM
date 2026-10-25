package com.detailline.callfollowcrm.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.Coil
import coil.request.ImageRequest
import coil.size.Size
import com.detailline.callfollowcrm.domain.ui.MarkStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ✏️ **펜으로 그린 획을 원본 사진에 얹어 새 비트맵을 만든다.** (2026-10-24 사장님 "사진에 펜으로 체크")
 *
 *   무거운 비트맵 작업이라 ViewModel 에서 떼어 여기 둔다(화면 파일이 더 안 커지게 — size_guard).
 *   - 원본은 **Coil 로 불러온다** → 화면에서 본 것(EXIF 로 세워진 방향)과 같아야 획이 맞다. 파일 경로든 content URI 든.
 *   - 획은 0..1 좌표([MarkStroke])라 **어떤 해상도에도** `점*가로`·`굵기*가로` 로 그대로 얹힌다.
 *   쓰는 곳:
 *     · 현장 사진 교체 — `CustomerDetailViewModel.saveMarkedPhoto`(파일 경로 → [render]).
 *     · 보낼 사진 표시 — 채팅 붙여둔 사진(content URI → [renderToFileUri], 그 자리 교체).
 *   좌표 셈은 `domain/ui/PhotoFit.kt`.
 */
object PhotoMarkRenderer {

    /** 파일 경로 사진에 획을 얹은 비트맵. 실패면 null. */
    suspend fun render(context: Context, path: String, marks: List<MarkStroke>): Bitmap? {
        if (path.isBlank()) return null
        return renderData(context, File(path), marks)
    }

    /** content:// 등 URI 사진에 획을 얹은 비트맵(갤러리에서 고른 첨부). 실패면 null. */
    suspend fun render(context: Context, uri: Uri, marks: List<MarkStroke>): Bitmap? =
        renderData(context, uri, marks)

    /** 획 얹기 공통 — Coil 로 불러와(파일/URI 둘 다) 복사 후 그 위에 그린다. */
    private suspend fun renderData(context: Context, data: Any, marks: List<MarkStroke>): Bitmap? {
        if (marks.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(data)
                    .allowHardware(false)   // 소프트웨어 비트맵이어야 복사·그리기가 된다
                    .size(Size.ORIGINAL)    // 원본 해상도 그대로
                    .build()
                val drawable = Coil.imageLoader(context).execute(req).drawable
                val loaded = (drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
                val bmp = loaded.copy(Bitmap.Config.ARGB_8888, true) ?: return@runCatching null
                val w = bmp.width
                val h = bmp.height
                val canvas = Canvas(bmp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                for (m in marks) {
                    paint.color = m.colorArgb
                    paint.strokeWidth = (m.normWidth * w).coerceAtLeast(2f)
                    val p = Path()
                    m.pts.forEachIndexed { i, pt ->
                        val x = pt.first * w
                        val y = pt.second * h
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    canvas.drawPath(p, paint)
                }
                bmp
            }.getOrNull()
        }
    }

    /**
     * ✏️ **보낼 사진에 획을 얹어 — 보낼 수 있는 파일 URI 로.** (2026-10-25 사장님 "보낼 때도 그리기")
     *   견적서 발행과 같은 길(cacheDir/shared + FileProvider). 붙여둔 사진을 이걸로 **그 자리 교체**해서 보낸다.
     *   실패면 null → 화면이 말한다. 갤러리 원본은 안 건드린다(새 파일만 만든다).
     */
    suspend fun renderToFileUri(context: Context, source: Uri, marks: List<MarkStroke>): Uri? {
        val bmp = render(context, source, marks) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                val f = File(dir, "mark_${System.currentTimeMillis()}.jpg")
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                if (f.length() == 0L) return@runCatching null
                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", f
                )
            }.getOrNull()
        }
    }
}
