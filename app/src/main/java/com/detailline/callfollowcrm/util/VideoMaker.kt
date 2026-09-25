package com.detailline.callfollowcrm.util

import kotlinx.coroutines.ensureActive
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
     * **마지막 실패 이유.** 실패를 "못 만들었어요" 한 줄로 끝내면 아무것도 못 고친다.
     *   (2026-09-25 업무폰에서 실패했는데 이유를 알 길이 없었다)
     */
    @Volatile var lastError: String? = null
        private set

    private const val TAG = "VideoMaker"

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
        /** 쓸 인코더 이름. null 이면 폰이 고르는 대로(보통 하드웨어). */
        codecName: String? = null,
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
            codec = (
                if (codecName != null) MediaCodec.createByCodecName(codecName)
                else MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            ).also {
                it.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                it.start()
            }
            // 어떤 인코더로 어떤 색 모양을 쓰는지 남긴다 — 다음에 실패하면 바로 안다.
            android.util.Log.i(
                TAG,
                "인코더=" + codec!!.name + " 색=" +
                    runCatching { codec!!.inputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT) }.getOrDefault(-1) +
                    " " + w + "x" + h
            )
            outFile.parentFile?.mkdirs()
            if (outFile.exists()) outFile.delete()
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val info = MediaCodec.BufferInfo()
            var frame = 0
            var done = false
            while (!done) {
                // ⏹ **[취소] 를 보는 자리.** 없으면 취소를 눌러도 끝까지 만들고,
                //   진행률이 계속 올라가서 "취소가 안 된다" 가 된다. (2026-09-25 사장님)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                // ── 넣기 ──
                if (frame <= frames) {
                    val inIdx = codec!!.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val ptsUs = frame.toLong() * 1_000_000L / fps
                        if (frame == frames) {
                            codec!!.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            canvas.drawColor(android.graphics.Color.BLACK)
                            // **그림 그리는 부분만 따로.** 여기서 터지면 지도 코드 문제고,
                            //   바깥에서 터지면 영상 장치 문제다 — 고치는 데가 완전히 다르다.
                            try {
                                draw(canvas, frame.toFloat() / (frames - 1).toFloat())
                            } catch (de: Throwable) {
                                throw IllegalStateException(
                                    "그림(" + frame + "컷): " + de.javaClass.simpleName +
                                        " " + (de.message ?: ""), de
                                )
                            }
                            val wrote = fillInput(codec!!, inIdx, bmp, w, h, row)
                            codec!!.queueInputBuffer(inIdx, 0, wrote, ptsUs, 0)
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
        }.onFailure { e ->
            // ⏹ **[취소] 는 실패가 아니다.** 여기서 삼키면 취소했는데
            //   "영상을 만들지 못했어요" 가 뜬다. 그대로 올려보내 진짜로 끊기게 한다.
            if (e is kotlinx.coroutines.CancellationException) {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { if (started) muxer?.stop() }
                runCatching { muxer?.release() }
                runCatching { if (outFile.exists()) outFile.delete() }   // 만들다 만 파일은 남기지 않는다
                bmp.recycle()
                throw e
            }
            lastError = shortReason(e)
            android.util.Log.e(TAG, "영상 실패 " + e.javaClass.simpleName + ": " + e.message, e)
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

    /**
     * 사장님이 읽을 수 있는 짧은 이유 + **고칠 수 있을 만큼의 단서**.
     *   "IllegalArgumentException" 한 마디로는 어디를 고쳐야 할지 모른다(2026-09-25 실제로 그랬다).
     */
    private fun shortReason(e: Throwable): String {
        val msg = (e.message ?: "").take(90)
        return when {
            e.message?.startsWith("그림(") == true -> e.message!!.take(110)
            e is android.media.MediaCodec.CodecException ->
                "영상 장치를 다른 앱이 쓰는 중 (" + msg + ")"
            e is OutOfMemoryError -> "폰 메모리가 모자라요"
            e is java.io.IOException -> "저장 공간/파일 (" + msg + ")"
            else -> e.javaClass.simpleName + " " + msg
        }
    }

    /**
     * 그림(ARGB) → 인코더 칸(YUV420). 채운 바이트 수를 돌려준다.
     *
     * ⚠️ 인코더가 **그림 칸을 안 주는 폰**이 있다(갤S23U·안드로이드 16에서 실패).
     *   그때는 [fillRaw] 로 **손으로 채운다** — 안 그러면 빈 칸을 "다 채웠다" 하고 넘겨 터진다.
     */
    private fun fillInput(codec: MediaCodec, idx: Int, bmp: Bitmap, w: Int, h: Int, row: IntArray): Int {
        val img = runCatching { codec.getInputImage(idx) }.getOrNull()
            ?: return fillRaw(codec, idx, bmp, w, h, row)
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
        // 🐞 **여기가 갤S23U 실패의 진짜 원인.** (2026-09-25)
        //   색 칸 세 개의 크기를 그냥 더해서 넘겼는데, 요즘 폰(NV12)은
        //   U·V 칸이 **같은 자리를 겹쳐** 쓴다 → 더하면 실제 칸보다 큰 숫자가 된다.
        //   폰은 "그런 크기는 안 된다" 며 튕긴다(IllegalArgumentException).
        //   그래서 **진짜 칸 크기를 넘지 않게** 자른다.
        val planeSum = yBuf.capacity() + uBuf.capacity() + vBuf.capacity()
        val cap = runCatching { codec.getInputBuffer(idx)?.capacity() }.getOrNull()
        return if (cap != null && cap > 0) minOf(planeSum, cap) else planeSum
    }

    /**
     * 그림 칸을 안 주는 인코더용 — **색 모양을 물어보고 손으로 채운다.**
     *   I420(Planar) = Y 전부 → U 전부 → V 전부
     *   NV12/NV21(SemiPlanar) = Y 전부 → UV 섞어서
     */
    private fun fillRaw(codec: MediaCodec, idx: Int, bmp: Bitmap, w: Int, h: Int, row: IntArray): Int {
        val buf = codec.getInputBuffer(idx) ?: return 0
        val fmtColor = runCatching {
            codec.inputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)
        }.getOrDefault(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
        val semi = fmtColor == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar ||
            fmtColor == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar
        val ySize = w * h
        val need = ySize * 3 / 2
        if (buf.capacity() < need) return 0
        val out = ByteArray(need)
        var uvI = ySize
        for (j in 0 until h) {
            bmp.getPixels(row, 0, w, 0, j, w, 1)
            val yBase = j * w
            for (i in 0 until w) {
                val c = row[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                out[yBase + i] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
            }
            if ((j and 1) == 0) {
                var i = 0
                while (i < w) {
                    val c = row[i]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    if (semi) {
                        out[uvI] = u; out[uvI + 1] = v; uvI += 2
                    } else {
                        val half = ySize + (j / 2) * (w / 2) + i / 2
                        out[half] = u
                        out[half + ySize / 4] = v
                    }
                    i += 2
                }
            }
        }
        buf.clear()
        buf.put(out, 0, need)
        return need
    }

    /**
     * **소프트웨어 인코더** 이름. 하드웨어가 말썽일 때 쓴다 — 느리지만 어느 폰에서나 된다.
     *   못 찾으면 null(그냥 폰이 고르는 대로).
     */
    fun softwareEncoder(): String? = runCatching {
        val list = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
        list.codecInfos.firstOrNull { info ->
            info.isEncoder &&
                info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } &&
                (info.name.startsWith("c2.android.") || info.name.startsWith("OMX.google."))
        }?.name
    }.getOrNull()
}
