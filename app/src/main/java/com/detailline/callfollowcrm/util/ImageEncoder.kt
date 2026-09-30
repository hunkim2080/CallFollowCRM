package com.detailline.callfollowcrm.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 갤러리 URI → 압축 JPEG base64(raw, no-wrap). 서버 업로드용(협업 증거사진 §F 등).
 *
 * 회전(orientation) 처리 — 세로사진이 가로로 눕는 문제. (2026-07-01 사장님)
 *   - **API 28+: ImageDecoder 사용** → OS 가 EXIF 회전을 **자동 적용**(JPEG·HEIF·삼성 포맷 전부 일관).
 *     (BitmapFactory 는 회전을 안 보고, 재인코딩 시 태그가 사라져 눕음. 수동 EXIF 는 HEIF 등에서 종종 실패.)
 *   - API 26~27: BitmapFactory + 수동 EXIF 회전 fallback.
 *   - 큰 변(maxDim) 기준 다운스케일 + JPEG quality 압축 → payload 작게(서버 ~1MB 컷 대비).
 *
 * 🔍 **얼마나 줄일것인가** (2026-09-30 사장님
 *   "현장사진들 왜 클릭해서 보면 너무 화질이 떨어져보이거든? 선명도도 떨어지는건 좀 아닌거같아")
 *   전엔 **1280픽셀 · 품질 72** 였다. 그 사진을 폰으로 되살리면 **그게 원본이 된다.**
 *   줄눈은 **가는 선**이 주인공이라 품질 72 에서 제일 먼저 뭉개진다.
 *   서버 한도는 **base64 1,400,000자(≈ 1MB)** 인데 지금 **평균 150KB** 였다 — **9배 여유.**
 *   → 2048 · 86 으로 올린다. 보통 400~600KB 로, 한도엔 한챐 모자란다.
 *   - 실패 시 null (호출부 graceful).
 */
object ImageEncoder {
    /** 큰 변 픽셀 수. 서버 한도(base64 1.4M ≈ 1MB) 안에서 최대한 선명하게. (2026-09-30) */
    const val MAX_DIM = 2048
    /** JPEG 품질. 줄눈 같은 **가는 선**은 낮은 품질에서 제일 먼저 뭉개진다. (2026-09-30) */
    const val QUALITY = 86
    /** 서버가 거절하는 선 — base64 글자 수(≈ 1MB). 서버와 **같은 숫자**여야 한다. */
    const val CAP_CHARS = 1_400_000

    fun uriToJpegBase64(context: Context, uri: Uri, maxDim: Int = MAX_DIM, quality: Int = QUALITY): String? =
        runCatching {
            val src: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeUprightWithImageDecoder(context, uri, maxDim)
            } else {
                decodeUprightLegacy(context, uri, maxDim)
            } ?: return null

            encodeUnderCap(src, quality)
        }.getOrNull()

    /**
     * 로컬 파일(내부 저장소 현장사진) → 압축 JPEG base64. 웹 뷰어 백필 업로드용(2026-08-13).
     *   URI 경로와 동일: API28+ ImageDecoder(EXIF 자동회전)·이하 수동 EXIF · maxDim 다운스케일 · JPEG 압축.
     */
    fun fileToJpegBase64(file: java.io.File, maxDim: Int = MAX_DIM, quality: Int = QUALITY): String? =
        runCatching {
            val src: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                    var sample = 1
                    while (longest / (sample * 2) >= maxDim) sample *= 2
                    if (sample > 1) decoder.setTargetSampleSize(sample)
                }
                downscaleIfNeeded(decoded, maxDim)
            } else {
                val bytes = file.readBytes()
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                val orientation = runCatching {
                    ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                downscaleIfNeeded(applyExifOrientation(decoded, orientation), maxDim)
            }
            encodeUnderCap(src, quality)
        }.getOrNull()

    /**
     * 서버가 받아주는 만큼으로 **줄여가며** 넣는다. (2026-09-30)
     *
     *   서버는 base64 [CAP_CHARS] 자를 넘으면 **거절**한다.
     *   품질을 86 으로 올렸으니, 드물게 넘치는 사진이 생길 수 있다 —
     *   그때 **조용히 안 올라가면** 사장님은 올라간 줄 알고 있다.
     *   품질을 한 칸씩 내리면서 들어갈 때까지 해본다. 끝까지 안 되면 그때서야 크기를 줄인다.
     */
    private fun encodeUnderCap(src: Bitmap, quality: Int): String? {
        var bmp = src
        var q = quality
        var shrunk = false
        while (true) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (b64.length <= CAP_CHARS) {
                if (shrunk && bmp !== src) runCatching { bmp.recycle() }
                return b64
            }
            when {
                q > 60 -> q -= 8
                !shrunk -> {
                    // 품질을 더 내리면 보기 싫어진다 — 차라리 **크기**를 반으로.
                    val half = downscaleIfNeeded(bmp, maxOf(bmp.width, bmp.height) / 2)
                    if (half === bmp) return null   // 더 줄일 수 없으면 포기(호출부가 graceful)
                    bmp = half; q = quality; shrunk = true
                }
                else -> return null   // 두 번까지 했는데도 크면 안 올린다(호출부가 graceful)
            }
        }
    }

    /** 최장변이 maxDim 초과면 비율 유지 축소, 아니면 그대로. */
    private fun downscaleIfNeeded(bm: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bm.width, bm.height).coerceAtLeast(1)
        if (longest <= maxDim) return bm
        val s = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bm, (bm.width * s).toInt().coerceAtLeast(1), (bm.height * s).toInt().coerceAtLeast(1), true
        )
    }

    /** API 28+: ImageDecoder — EXIF 회전 OS 자동 적용 + 다운스케일 + 소프트웨어 비트맵(압축 가능). */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun decodeUprightWithImageDecoder(context: Context, uri: Uri, maxDim: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE   // compress() 하려면 소프트웨어 비트맵 필요
            decoder.isMutableRequired = false
            // ⚠️ 다운스케일은 sampleSize(2의 거듭제곱)로 — setTargetSize(가로세로 지정)는 EXIF 회전과 충돌해
            //    세로사진을 강제로 가로 캔버스에 눕히는 버그가 있음. sampleSize 는 aspect·회전을 안 건드림. (2026-07-01 재수정)
            val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            var sample = 1
            while (longest / (sample * 2) >= maxDim) sample *= 2
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
        // 이 시점 decoded 는 EXIF 회전이 적용된 '똑바로 선' 비트맵. maxDim 초과면 그 상태로 최종 스케일(회전 유지).
        val longest = maxOf(decoded.width, decoded.height).coerceAtLeast(1)
        if (longest <= maxDim) return decoded
        val s = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(decoded,
            (decoded.width * s).toInt().coerceAtLeast(1), (decoded.height * s).toInt().coerceAtLeast(1), true)
    }

    /** API 26~27: 수동 EXIF 회전. */
    private fun decodeUprightLegacy(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val upright = applyExifOrientation(decoded, orientation)
        val longest = maxOf(upright.width, upright.height).coerceAtLeast(1)
        val scale = (maxDim.toFloat() / longest).coerceAtMost(1f)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(upright, (upright.width * scale).toInt().coerceAtLeast(1),
                (upright.height * scale).toInt().coerceAtLeast(1), true)
        } else upright
    }

    /** EXIF orientation → 픽셀 회전/반전 적용. 정방향이면 원본 그대로. */
    private fun applyExifOrientation(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        return runCatching {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }.getOrDefault(src)
    }
}
