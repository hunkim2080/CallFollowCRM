package com.detailline.callfollowcrm.util

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 🔊 **16비트 모노 PCM → AAC.** [ReelMusic] 이 지은 소리를 영상에 넣으려면 먼저 AAC 로 구워야 한다.
 *
 * 왜 미리 통째로 — [VideoMaker] 의 muxer 는 **트랙 포맷을 다 안 뒤에 start()** 해야 한다.
 *   그래서 소리를 먼저 다 구워 `format` + `packets` 로 들고 있다가, 영상 포맷이 정해지는 순간
 *   두 트랙(영상·소리)을 같이 등록한다. (영상 루프는 거의 그대로 둔다)
 *
 * 15초면 AAC 약 650조각(≈260KB) — 메모리에 들고 있어도 괜찮다.
 *   **실패하면 null** — 소리 없이라도 영상은 나가게(소리는 덤, 영상이 본체).
 */
object AacEncoder {
    private const val MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val TAG = "AacEncoder"

    /** 구워 둔 소리 한 벌 — muxer 에 그대로 넘긴다. */
    class Packet(val data: ByteArray, val ptsUs: Long)
    class Encoded(val format: MediaFormat, val packets: List<Packet>)

    fun encode(pcm: ShortArray, sampleRate: Int, bitRate: Int = 128_000): Encoded? {
        if (pcm.isEmpty()) return null
        var codec: MediaCodec? = null
        return runCatching {
            val fmt = MediaFormat.createAudioFormat(MIME, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val c = MediaCodec.createEncoderByType(MIME).also {
                it.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); it.start()
            }
            codec = c

            // PCM → 리틀엔디안 바이트
            val raw = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) raw.putShort(s)
            val total = pcm.size * 2

            val info = MediaCodec.BufferInfo()
            val packets = ArrayList<Packet>(pcm.size / 1024 + 8)
            var outFormat: MediaFormat? = null
            var inPos = 0
            var sentEos = false
            var done = false
            val chunk = 4096
            while (!done) {
                if (!sentEos) {
                    val inIdx = c.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val ib = c.getInputBuffer(inIdx)!!
                        ib.clear()
                        val n = minOf(chunk, ib.capacity(), total - inPos)
                        if (n > 0) { ib.put(raw.array(), inPos, n) }
                        // 샘플(=바이트/2) → 마이크로초
                        val ptsUs = (inPos.toLong() / 2L) * 1_000_000L / sampleRate
                        if (inPos + n >= total) {
                            c.queueInputBuffer(inIdx, 0, n, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sentEos = true
                        } else {
                            c.queueInputBuffer(inIdx, 0, n, ptsUs, 0)
                        }
                        inPos += n
                    }
                }
                val outIdx = c.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = c.outputFormat
                    outIdx >= 0 -> {
                        val ob = c.getOutputBuffer(outIdx)
                        if (ob != null && info.size > 0 &&
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            ob.position(info.offset); ob.limit(info.offset + info.size)
                            val d = ByteArray(info.size); ob.get(d)
                            packets.add(Packet(d, info.presentationTimeUs))
                        }
                        c.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) done = true
                    }
                }
            }
            // 포맷엔 csd-0(디코딩에 꼭 필요한 머리값)이 담겨 온다 — 꼭 outputFormat 을 쓴다.
            Encoded(outFormat ?: fmt, packets)
        }.getOrElse {
            android.util.Log.e(TAG, "AAC 실패: " + it.message, it)
            null
        }.also {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
        }
    }
}
