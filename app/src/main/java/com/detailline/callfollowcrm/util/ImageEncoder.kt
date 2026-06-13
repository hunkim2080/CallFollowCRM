package com.detailline.callfollowcrm.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * 갤러리 URI → 압축 JPEG base64(raw, no-wrap). 서버 업로드용(협업 증거사진 §F 등).
 *   - 큰 변(maxDim) 기준 다운스케일 + JPEG quality 압축 → payload 작게(서버 ~1MB 컷 대비).
 *   - 실패 시 null (호출부 graceful).
 */
object ImageEncoder {
    fun uriToJpegBase64(context: Context, uri: Uri, maxDim: Int = 1280, quality: Int = 72): String? =
        runCatching {
            val src = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            val longest = maxOf(src.width, src.height).coerceAtLeast(1)
            val scale = (maxDim.toFloat() / longest).coerceAtMost(1f)
            val bmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1),
                    (src.height * scale).toInt().coerceAtLeast(1), true)
            } else src
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
}
