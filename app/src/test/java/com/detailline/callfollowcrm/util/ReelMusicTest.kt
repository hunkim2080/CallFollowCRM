package com.detailline.callfollowcrm.util

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🎵 **내 기록 영상 '어른' 소리 — 폰에서 못 들으니 테스트가 대신 듣는다.**
 *
 * 지키는 것 —
 *   ① 길이가 영상(초)과 **정확히** 맞는다(안 맞으면 끝에서 소리가 잘리거나 남는다)
 *   ② **소프트 리밋**이 걸려 full-scale 로 깨지지 않는다(벨인데 지지직이면 더 유치하다)
 *   ③ 핵심 현장이 **없든 넷이든·10초든 15초든** 터지지 않고 소리가 난다
 *   ④ 같은 입력 → **같은 소리**(만들 때마다 달라지면 비교가 안 된다)
 */
class ReelMusicTest {

    private val SR = ReelMusic.SAMPLE_RATE

    /** RecordReel 타임라인과 같은 방식으로 실제 cue 를 만든다(그림·소리 한 곳에서 나온 값). */
    private fun cuesFor(stops: Int, featured: Int): ReelMusic.Cues {
        val sec = RecordReel.secondsFor(stops)
        val js = RecordReel.INTRO * sec
        val end = (1f - RecordReel.ENDCARD) * sec
        val reveals = FloatArray(featured) { js + (end - js) * (it + 1) / (featured + 1) }
        return ReelMusic.Cues(
            seconds = sec, introClimax = 0.72f * js, journeyStart = js, reveals = reveals, endcard = end
        )
    }

    private fun maxAbs(pcm: ShortArray): Int {
        var m = 0
        for (v in pcm) { val a = abs(v.toInt()); if (a > m) m = a }
        return m
    }

    @Test
    fun `길이가 영상 초와 정확히 맞는다`() {
        for (stops in intArrayOf(0, 1, 3, 8, 11, 40)) {
            val sec = RecordReel.secondsFor(stops)
            val pcm = ReelMusic.build(cuesFor(stops, 3))
            assertEquals("$stops 곳 — PCM 길이가 ${sec}초와 안 맞는다", (sec * SR).toInt(), pcm.size)
        }
    }

    @Test
    fun `소리가 실제로 난다 — 무음이 아니다`() {
        val pcm = ReelMusic.build(cuesFor(8, 3))
        assertTrue("합성했는데 거의 무음이다", maxAbs(pcm) > 3000)
    }

    @Test
    fun `소프트 리밋으로 깨지지 않는다 — 92퍼센트 풀스케일 넘지 않음`() {
        // tanh × 0.92 × 32767 ≈ 30145 이 상한. 이걸 넘으면 지지직거린다.
        for (stops in intArrayOf(3, 8, 11, 40)) {
            val pcm = ReelMusic.build(cuesFor(stops, 4))
            assertTrue("$stops 곳 — 소리가 풀스케일로 깨진다 (${maxAbs(pcm)})", maxAbs(pcm) <= 30146)
        }
    }

    @Test
    fun `핵심 현장이 없어도·넷이어도 터지지 않고 소리가 난다`() {
        for (featured in 0..4) {
            val pcm = ReelMusic.build(cuesFor(11, featured))
            assertTrue("핵심 $featured 곳 — 소리가 안 난다", maxAbs(pcm) > 2000)
        }
    }

    @Test
    fun `같은 입력은 같은 소리 — 만들 때마다 달라지지 않는다`() {
        val a = ReelMusic.build(cuesFor(8, 3))
        val b = ReelMusic.build(cuesFor(8, 3))
        assertTrue("같은 cue 인데 소리가 다르다", a.contentEquals(b))
    }

    @Test
    fun `cue 가 범위를 벗어나도 터지지 않는다 — 리빌이 끝 너머·엔드카드가 앞`() {
        val sec = 12f
        val weird = ReelMusic.Cues(
            seconds = sec,
            introClimax = -1f,                       // 음수
            journeyStart = sec * 0.5f,
            reveals = floatArrayOf(sec + 5f, -2f),   // 하나는 끝 너머, 하나는 음수
            endcard = sec * 0.2f                      // 여정 시작보다 앞(뒤집힘)
        )
        val pcm = ReelMusic.build(weird)             // 안 터지면 통과
        assertEquals((sec * SR).toInt(), pcm.size)
    }
}
