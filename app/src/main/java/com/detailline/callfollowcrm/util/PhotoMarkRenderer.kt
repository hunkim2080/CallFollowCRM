package com.detailline.callfollowcrm.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
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
 *   - 원본은 **Coil 로 불러온다** → 화면에서 본 것(EXIF 로 세워진 방향)과 같아야 획이 맞다.
 *   - 획은 0..1 좌표([MarkStroke])라 **어떤 해상도에도** `점*가로`·`굵기*가로` 로 그대로 얹힌다.
 *   쓰는 곳: `CustomerDetailViewModel.addMarkedPhoto` 한 곳. 좌표 셈은 `domain/ui/PhotoFit.kt`.
 */
object PhotoMarkRenderer {

    /** 성공하면 표시가 얹힌 비트맵, 실패(로드 실패 등)면 null. */
    suspend fun render(context: Context, path: String, marks: List<MarkStroke>): Bitmap? {
        if (marks.isEmpty() || path.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(File(path))
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
}
