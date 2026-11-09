package com.detailline.callfollowcrm.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 🎵 **내 기록 영상에 깔리는 '어른' 소리 — 파형(PCM)으로 직접 짓는다.**
 *
 * 왜 여기서 새로 — 쇼룸 모형(claude.ai)은 웹오디오로 소리를 냈지만, **저장되는 mp4** 에 소리를 넣으려면
 *   파형을 직접 합성해 AAC 로 구워 영상과 합쳐야 한다([AacEncoder]·[VideoMaker]).
 *   멜로디·음색은 모형 그대로(벨·마림바·부드러운 베이스·따뜻한 화음), 장난감 티(스퀘어·하이햇)는 뺐다.
 *
 * **순수 함수다** — (길이·장면 시점 [Cues]) → 16비트 모노 PCM. 안드로이드도 네트워크도 안 쓴다 →
 *   단위 테스트가 대신 듣는다([ReelMusicTest]). 장면 시점은 [RecordReel] 이 영상과 **같은 타임라인**에서
 *   뽑아 넘긴다(소리·그림을 두 벌로 만들지 않는다).
 */
object ReelMusic {
    const val SAMPLE_RATE = 44100

    /** 소리를 얹을 자리 — 전부 '초' 단위. [RecordReel] 가 warp 타임라인에서 계산해 넘긴다. */
    data class Cues(
        /** 영상 전체 길이(초). */
        val seconds: Float,
        /** 오프닝 큰 숫자가 다 차오르는 순간 — 상승 벨이 여기서 안착. */
        val introClimax: Float,
        /** 여정(배경 멜로디) 시작 = INTRO×seconds. */
        val journeyStart: Float,
        /** 핵심 현장 '짠' 리빌이 시작되는 시각들(최대 4곳). 없을 수 있다. */
        val reveals: FloatArray,
        /** 엔드카드(성과 팡파레) 시작 = (1-ENDCARD)×seconds. */
        val endcard: Float
    )

    // ── 음표표: 쇼룸 모형 그대로(0 = 쉼). BPM 132 · 16분음표 · 4마디(64스텝) 루프. ──
    private const val BPM = 132f
    private val STEP16 = 60f / BPM / 4f
    private const val PAT = 64
    private val MEL = floatArrayOf(
        659.25f,0f,783.99f,0f,659.25f,0f,523.25f,659.25f,783.99f,0f,880.00f,0f,783.99f,0f,659.25f,0f,
        587.33f,0f,698.46f,0f,587.33f,0f,493.88f,0f,587.33f,0f,783.99f,0f,698.46f,0f,587.33f,0f,
        659.25f,0f,783.99f,0f,1046.50f,0f,783.99f,0f,880.00f,0f,1046.50f,0f,987.77f,0f,783.99f,0f,
        880.00f,0f,783.99f,0f,659.25f,0f,783.99f,0f,523.25f,587.33f,659.25f,0f,783.99f,0f,523.25f,0f
    )
    private val BASS = floatArrayOf(
        130.81f,0f,196.00f,0f,164.81f,0f,196.00f,0f,130.81f,0f,196.00f,0f,164.81f,0f,196.00f,0f,
        220.00f,0f,164.81f,0f,220.00f,0f,164.81f,0f,196.00f,0f,146.83f,0f,196.00f,0f,146.83f,0f,
        174.61f,0f,130.81f,0f,174.61f,0f,130.81f,0f,130.81f,0f,196.00f,0f,130.81f,0f,196.00f,0f,
        196.00f,0f,146.83f,0f,196.00f,0f,246.94f,0f,130.81f,0f,196.00f,0f,146.83f,0f,130.81f,0f
    )

    /**
     * 어른 음색 악보를 16비트 PCM 으로 합성한다. 되돌려주는 배열 길이 = seconds×sampleRate.
     *   모든 소리를 double 믹스 버퍼에 더한 뒤 **소프트 리밋**(tanh)으로 깎아 깨짐을 막는다.
     */
    fun build(cues: Cues, sampleRate: Int = SAMPLE_RATE): ShortArray {
        val sr = sampleRate
        val n = (cues.seconds * sr).toInt().coerceAtLeast(1)
        val buf = DoubleArray(n)

        // 🔔 벨/마림바(또르르): 사인 + 2배음, 6ms 어택 후 빠른 지수 감쇠
        fun mallet(f: Float, t0: Float, dur: Float, vol: Float) {
            if (f <= 0f) return
            val start = (t0 * sr).toInt(); if (start >= n) return
            val tau = maxOf(0.12f, minOf(dur, 0.5f))
            val len = (minOf(dur + 0.1f, tau * 3f + 0.1f) * sr).toInt()
            val w = 2.0 * PI * f / sr
            val atk = 0.006f * sr
            var i = if (start < 0) -start else 0   // 시작이 0 이전이면 들리는 부분부터 (음수 인덱스 방지)
            while (i < len && start + i < n) {
                val x = i / sr.toFloat()
                val a = minOf(i / atk, 1f) * exp(-x / tau)
                buf[start + i] += vol * a * (sin(w * i) + 0.22 * sin(2.0 * w * i))
                i++
            }
        }
        // 🎻 부드러운 사인 베이스 — 30ms 어택, 끝으로 서서히 줄어듦
        fun softBass(f: Float, t0: Float, dur: Float, vol: Float) {
            if (f <= 0f) return
            val start = (t0 * sr).toInt(); if (start >= n) return
            val len = (dur * 1.3f * sr).toInt().coerceAtLeast(1)
            val w = 2.0 * PI * f / sr
            val atk = 0.03f * sr
            var i = if (start < 0) -start else 0   // 시작이 0 이전이면 들리는 부분부터 (음수 인덱스 방지)
            while (i < len && start + i < n) {
                val a = minOf(i / atk, 1f) * (1f - i / len.toFloat())
                buf[start + i] += vol * a * sin(w * i)
                i++
            }
        }
        // ☁️ 따뜻한 패드 — 100ms 어택, 60%까지 유지 후 릴리즈 (성과 스웰용)
        fun pad(f: Float, t0: Float, dur: Float, vol: Float) {
            if (f <= 0f) return
            val start = (t0 * sr).toInt(); if (start >= n) return
            val len = (dur * sr).toInt().coerceAtLeast(1)
            val w = 2.0 * PI * f / sr
            val atk = 0.1f * sr
            val rel = len * 0.6f
            var i = if (start < 0) -start else 0   // 시작이 0 이전이면 들리는 부분부터 (음수 인덱스 방지)
            while (i < len && start + i < n) {
                val a = when {
                    i < atk -> i / atk
                    i < rel -> 1f
                    else -> (1f - (i - rel) / (len - rel)).coerceIn(0f, 1f)
                }
                buf[start + i] += vol * a * sin(w * i)
                i++
            }
        }
        fun chord(fs: FloatArray, t0: Float, dur: Float, vol: Float) { for (f in fs) pad(f, t0, dur, vol) }
        // 🥁 아주 여린 저음 펄스 — 140→48Hz, 짧게 (하이햇은 뺐다)
        fun softKick(t0: Float, vol: Float) {
            val start = (t0 * sr).toInt(); if (start >= n) return
            val len = (0.16f * sr).toInt()
            var i = if (start < 0) -start else 0   // 시작이 0 이전이면 들리는 부분부터 (음수 인덱스 방지)
            while (i < len && start + i < n) {
                val x = i / sr.toFloat()
                val fr = 140.0 * exp(-x / 0.03) + 48.0
                buf[start + i] += vol * exp(-x / 0.05) * sin(2.0 * PI * fr * x)
                i++
            }
        }

        // ── 오프닝: 따뜻한 패드 바닥 + 숫자 차오르며 상승 벨(introClimax 에서 안착) ──
        val js = cues.journeyStart.coerceAtLeast(0f)
        chord(floatArrayOf(261.63f, 329.63f, 392.00f), 0f, maxOf(js, 0.4f), 0.07f)
        run {
            val rise = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f)
            val span = maxOf(cues.introClimax, 0.3f)
            for (k in rise.indices) mallet(rise[k], span * k / rise.size, 0.4f, 0.13f)
        }

        // ── 여정: 벨 멜로디 + 부드러운 베이스, 장면 따라 볼륨 빌드업. endcard 에서 멈춤 ──
        val end = cues.endcard.coerceIn(js, cues.seconds)
        var step = 0
        var t = js
        while (t < end - 0.001f) {
            val s = step % PAT
            val prog = ((t - js) / maxOf(0.001f, end - js)).coerceIn(0f, 1f)
            val inten = 0.3f + 0.65f * prog
            val sw = if (s % 2 == 1) STEP16 * 0.12f else 0f
            if (MEL[s] > 0f) mallet(MEL[s], t + sw, STEP16 * 1.7f, 0.07f + 0.065f * inten)
            if (BASS[s] > 0f) softBass(BASS[s], t + sw, STEP16 * 1.7f, 0.10f + 0.05f * inten)
            if (s % 8 == 0) softKick(t, 0.05f + 0.05f * inten)
            step++; t += STEP16
        }

        // ── 핵심 현장 '짠' 리빌: 따뜻한 벨 두 점 + 짧은 패드(배경음 위로 살짝) ──
        for (rt in cues.reveals) {
            mallet(1046.50f, rt, 0.3f, 0.15f)
            mallet(1567.98f, rt + 0.06f, 0.3f, 0.13f)
            chord(floatArrayOf(392.00f, 523.25f), rt, 0.5f, 0.05f)
        }

        // ── 엔드카드: 성과 팡파레(벨 + 부드러운 베이스 + 따뜻한 화음 스웰) ──
        run {
            val mel = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f, 783.99f, 1046.50f, 1318.51f)
            for (k in mel.indices) mallet(mel[k], end + k * 0.12f, 0.3f, 0.16f)
            val bs = floatArrayOf(130.81f, 164.81f, 196.00f, 261.63f)
            for (k in bs.indices) softBass(bs[k], end + k * 0.21f, 0.4f, 0.14f)
            mallet(1567.98f, end + 0.84f, 0.5f, 0.17f)
            val padDur = (cues.seconds - (end + 0.84f)).coerceIn(0.3f, 1.3f)
            chord(floatArrayOf(261.63f, 329.63f, 392.00f, 523.25f), end + 0.84f, padDur, 0.08f)
        }

        // ── 소프트 리밋 후 16비트로 ──
        var peak = 0.0
        for (v in buf) { val a = abs(v); if (a > peak) peak = a }
        val norm = if (peak > 1.0) 1.0 / peak else 1.0
        val k = 1.2
        val kn = tanh(k)
        val out = ShortArray(n)
        for (i in 0 until n) {
            val v = tanh(buf[i] * norm * k) / kn
            out[i] = (v * 32767.0 * 0.92).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}
