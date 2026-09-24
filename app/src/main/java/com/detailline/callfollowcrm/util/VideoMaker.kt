package com.detailline.callfollowcrm.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * **그림을 이어 붙여 영상(mp4)으로.** (2026-09-25 사장님 "영상으로 저장할 수 있는 기능")
 *
 * 왜 필요한가 — 움직이는 지도를 이만큼 만들어놨는데 **저장되는 건 정지 그림 한 장뿐**이었다.
 * 사장님이 SNS 에 올리려면 화면 녹화를 직접 해야 했고, 그러면 상단바·시간·배터리가 다 찍힌다.
 *
 * 크기는 **인스타 릴스(9:16)** 로 고정한다. 정사각형일 이유가 없다(사장님 확인).
 *
 * 어떻게 — 한 장씩 [Canvas] 에 그려서 안드로이드 기본 인코더(H.264)에 밀어 넣는다.
 *   · 인코더가 원하는 색 모양은 기기마다 다르다 → `getInputImage` 로 **인코더가 주는 칸**에
 *     직접 채운다(줄 간격·픽셀 간격을 그대로 따른다). 손으로 YUV 를 짜 맞추면 기기마다 깨진다.
 *   · 갤S9+(2018) 도 쓰신다 → 기본 720×1280·24컷/초. 큰 화면은 만드는 데 너무 오래 걸린다.
 *
 * 인터넷도, 열쇠도, 바깥 라이브러리도 안 쓴다.
 */
object VideoMaker {

    /** 만드는 동안 진행률(0~1)을 알려준다 — 10초짜리라도 폰에선 시간이 걸린다. */
    fun interface Progress { fun onStep(done: Float) }

    /**
     * @param width  가로(짝수). 릴스는 9:16 이라 720×1280 이 기본.
     * @param fps    1초에 몇 컷. 24 면 충분히 부드럽고 만드는 시간이 짧다.
     * @param seconds 몇 초짜리.
     * @param draw   컷마다 그리는 함수. `t` 는 0~1 로 흐르는 시각.
     * @return 만들어진 파일. 실패하면 null.
     */
    suspend fun make(
        outFile: File,
        width: Int = 720,
        height: Int = 1280,
        fps: Int = 24,
        seconds: Float = 10f,
        bitRate: Int = 6_000_000,
        progress: Progress? = null,
        draw: (canvas: Canvas, t: Float) -> Unit
    ): File? = withContext(Dispatchers.Default) {
        val w = width / 2 * 2
        val h = height / 2 * 2
        val frames = (fps * seconds).toInt().coerceAtLeast(2)
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var track = -1
        var started = false
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val row = IntArray(w)
        runCatching {
            val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
                it.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                it.start()
            }
            outFile.parentFile?.mkdirs()
            if (outFile.exists()) outFile.delete()
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val info = MediaCodec.BufferInfo()
            var frame = 0
            var done = false
            while (!done) {
                // ── 넣기 ──
                if (frame <= frames) {
                    val inIdx = codec!!.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val ptsUs = frame.toLong() * 1_000_000L / fps
                        if (frame == frames) {
                            codec!!.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            canvas.drawColor(android.graphics.Color.BLACK)
                            draw(canvas, frame.toFloat() / (frames - 1).toFloat())
                            fillInput(codec!!, inIdx, bmp, w, h, row)
                            codec!!.queueInputBuffer(inIdx, 0, inputSize(codec!!, inIdx, w, h), ptsUs, 0)
                            progress?.onStep(frame.toFloat() / frames)
                        }
                        frame++
                    }
                }
                // ── 빼기 ──
                val outIdx = codec!!.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer!!.addTrack(codec!!.outputFormat)
                        muxer!!.start(); started = true
                    }
                    outIdx >= 0 -> {
                        val buf: ByteBuffer? = codec!!.getOutputBuffer(outIdx)
                        if (buf != null && started && info.size > 0 &&
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer!!.writeSampleData(track, buf, info)
                        }
                        codec!!.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) done = true
                    }
                }
            }
        }.onFailure {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
            bmp.recycle()
            return@withContext null
        }
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { if (started) muxer?.stop() }
        runCatching { muxer?.release() }
        bmp.recycle()
        progress?.onStep(1f)
        if (outFile.exists() && outFile.length() > 1000) outFile else null
    }

    /** 인코더가 준 칸의 실제 크기 — 줄 간격이 기기마다 달라 직접 물어봐야 한다. */
    private fun inputSize(codec: MediaCodec, idx: Int, w: Int, h: Int): Int =
        runCatching {
            val img = codec.getInputImage(idx) ?: return w * h * 3 / 2
            val y = img.planes[0]
            val u = img.planes[1]
            val v = img.planes[2]
            y.buffer.capacity() + u.buffer.capacity() + v.buffer.capacity()
        }.getOrDefault(w * h * 3 / 2)

    /**
     * 그림(ARGB) → 인코더 칸(YUV420). **인코더가 알려준 줄 간격·픽셀 간격을 그대로 따른다** —
     * 손으로 짜 맞추면 기기마다 색이 밀리거나 초록 화면이 된다.
     */
    private fun fillInput(codec: MediaCodec, idx: Int, bmp: Bitmap, w: Int, h: Int, row: IntArray) {
        val img = codec.getInputImage(idx) ?: return
        val yP = img.planes[0]
        val uP = img.planes[1]
        val vP = img.planes[2]
        val yBuf = yP.buffer
        val uBuf = uP.buffer
        val vBuf = vP.buffer
        val yStride = yP.rowStride
        val uStride = uP.rowStride
        val vStride = vP.rowStride
        val uPix = uP.pixelStride
        val vPix = vP.pixelStride

        for (j in 0 until h) {
            bmp.getPixels(row, 0, w, 0, j, w, 1)
            val yBase = j * yStride
            val half = j / 2
            for (i in 0 until w) {
                val c = row[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                // BT.601 — 안드로이드 인코더가 쓰는 그 식.
                val yv = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(yBase + i, yv.coerceIn(0, 255).toByte())
                if ((j and 1) == 0 && (i and 1) == 0) {
                    val uv = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val vv = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val hi = i / 2
                    uBuf.put(half * uStride + hi * uPix, uv.coerceIn(0, 255).toByte())
                    vBuf.put(half * vStride + hi * vPix, vv.coerceIn(0, 255).toByte())
                }
            }
        }
    }
}
