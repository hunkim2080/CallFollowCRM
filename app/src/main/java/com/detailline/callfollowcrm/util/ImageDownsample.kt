package com.detailline.callfollowcrm.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * base64/바이트 이미지를 '목표 크기 근처로 축소' 디코딩.
 *
 * 배경(2026-08-11 오프라인 감사 rank5): 협업/팀 현장 사진(서버가 1280px로 줌)을 썸네일(92~96dp)로만 보여주면서
 *   inSampleSize 없이 원본 해상도로 통째 decodeByteArray 했다. 1280×960 ARGB_8888 한 장 ≈ 4.9MB → 한 현장 20장이면
 *   약 98MB 힙 동시 상주 → 구형폰(S9, largeHeap 미설정) GC 스래싱·프레임드랍·OOM 크래시.
 *   inJustDecodeBounds 로 크기만 먼저 재고 2배수 inSampleSize 로 축소 디코딩하면 힙이 수 MB로 떨어진다.
 *   (로컬 사진은 이미 Coil AsyncImage 가 자동 다운샘플 — 여기선 서버 base64 경로만 보강.)
 */
object ImageDownsample {

    /** 바이트 이미지를 최장변이 대략 targetPx 이하가 되도록 2배수 축소 디코딩. 실패 시 null. */
    fun decodeBytes(bytes: ByteArray, targetPx: Int = 512): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longer = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longer > 0 && longer / sample > targetPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()

    /** "data:image/...;base64,XXXX" 또는 raw base64 → 축소 Bitmap. 실패 시 null. */
    fun decodeDataUrl(dataUrl: String?, targetPx: Int = 512): Bitmap? {
        if (dataUrl.isNullOrBlank()) return null
        return runCatching {
            val b64 = dataUrl.substringAfter(",", dataUrl)
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            decodeBytes(bytes, targetPx)
        }.getOrNull()
    }
}
