package com.detailline.callfollowcrm.util

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import java.io.ByteArrayInputStream

/**
 * 📷 **받은 사진이 깨졌는지** 알아본다. (2026-09-20 사장님 실기 신고)
 *
 * 손님이 보낸 현장 사진 3장이 **전부 지지직**으로 왔다. 파일은 JPEG 구조가 멀쩡해서
 * (머리 FFD8 · 꼬리 FFD9 · 크기 240×320) 열리기는 하는데 **내용만 노이즈**였다.
 * 그래서 앱은 잘 받은 줄 알고 그대로 보여줬고, 사장님은 한참 들여다보셨다.
 *
 * ## 어떻게 아나
 * 노이즈는 **옆 점끼리 밝기가 마구 튄다.** 실제 자료로 재보니 확실히 갈렸다 —
 *   깨진 것 **54~55** · 정상 사진 **3.9~14.1** (사장님 시험 사진 5장 + 깨진 것 3장)
 * 그래서 기준을 **40** 으로 둔다. 사이가 넓어서 애매한 게 거의 없다.
 *
 * ⚠️ **작게 줄여서 재면 안 된다.** 노이즈는 점 단위라 줄이면 평균으로 뭉개진다
 *   (64×64 로 줄여 쟀더니 깨진 게 9.8, 정상이 12.8 로 **뒤집혔다**).
 *   그래서 **원본 해상도 그대로** 가운데 [PATCH]×[PATCH] 만 떼서 본다 — 큰 사진도 가볍다.
 *
 * ⚠️ **가로·세로 둘 다** 본다. 줄눈 사진은 **격자무늬**라 한 방향만 튈 수 있다.
 *   노이즈는 어느 방향이든 똑같이 튀므로 **작은 쪽**이 기준을 넘어야 깨진 걸로 친다.
 */
object ImageNoiseCheck {

    /** 가운데에서 떼어 볼 조각 크기(원본 해상도 기준). */
    const val PATCH = 200

    /** 이 값을 넘으면 깨진 사진. 실측: 깨진 것 54~55 / 정상 최고 14.1. */
    const val THRESHOLD = 40.0

    /**
     * 밝기 배열에서 **가로·세로 평균 차이 중 작은 쪽**. 순수 계산이라 테스트로 검산한다.
     * @param lum 밝기 0~255, size*size 개
     */
    fun scoreOf(lum: IntArray, size: Int): Double {
        if (size < 2 || lum.size < size * size) return 0.0
        var h = 0L
        var v = 0L
        for (y in 0 until size) {
            for (x in 1 until size) h += kotlin.math.abs(lum[y * size + x] - lum[y * size + x - 1])
        }
        for (y in 1 until size) {
            for (x in 0 until size) v += kotlin.math.abs(lum[y * size + x] - lum[(y - 1) * size + x])
        }
        val n = (size * (size - 1)).toDouble()
        return minOf(h / n, v / n)
    }

    /** 깨진 사진이면 true. 판정 못 하면 false — **애매하면 멀쩡한 걸로 본다**(멀쩡한 걸 버리면 안 되니까). */
    fun isNoisy(bytes: ByteArray?): Boolean {
        val s = scoreOfJpeg(bytes) ?: return false
        return s >= THRESHOLD
    }

    /** 점수. 못 읽으면 null. */
    fun scoreOfJpeg(bytes: ByteArray?): Double? {
        if (bytes == null || bytes.size < 1000) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            val side = minOf(PATCH, w, h)
            if (side < 16) return null

            // 원본 해상도 그대로 가운데 조각만. 큰 사진도 이 조각만 읽으니 가볍다.
            val left = (w - side) / 2
            val top = (h - side) / 2
            @Suppress("DEPRECATION")
            val dec = BitmapRegionDecoder.newInstance(ByteArrayInputStream(bytes), false) ?: return null
            val bmp = dec.decodeRegion(Rect(left, top, left + side, top + side), null) ?: return null
            runCatching { dec.recycle() }

            val px = IntArray(side * side)
            bmp.getPixels(px, 0, side, 0, 0, side, side)
            runCatching { bmp.recycle() }

            val lum = IntArray(px.size) {
                val c = px[it]
                // 사람 눈 기준 밝기 — 정수만 써서 빠르게
                ((c shr 16 and 0xFF) * 77 + (c shr 8 and 0xFF) * 151 + (c and 0xFF) * 28) shr 8
            }
            scoreOf(lum, side)
        }.getOrNull()
    }
}
