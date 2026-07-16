package com.detailline.callfollowcrm.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/**
 * 화면 안 동작에 대한 **로컬 확인 신호**(소리) — 알림(notification)이 아니라, 사장님이 방금 누른 버튼이
 *   "됐다"를 그 자리에서 들려주는 용도. (2026-07-16 사장님: 협업 완료를 누른 본인 폰에서도 확인음이 나야)
 *
 * NotificationHelper 의 채널 사운드와 별개다 — 그건 '상대가 보낸 알림'이 올 때 울리는 것이고,
 *   이건 '내가 방금 한 동작'의 즉시 피드백이다. 짧게 한 번 재생하고 스스로 정리(release).
 *   실패해도 조용히 무시(소리는 부가 피드백이라 동작 자체를 막으면 안 됨).
 */
object LocalCue {

    private const val TAG = "LocalCue"

    /** raw 리소스 사운드를 한 번 재생. UI 스레드에서 불러도 안전(재생/해제는 콜백에서). */
    fun play(context: Context, rawResId: Int) {
        runCatching {
            val mp = MediaPlayer.create(context.applicationContext, rawResId) ?: return
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)  // 알림음 아님 = '동작 확인음'
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setOnCompletionListener { it.release() }
            mp.setOnErrorListener { p, _, _ -> p.release(); true }
            mp.start()
        }.onFailure { Log.w(TAG, "cue play failed res=$rawResId", it) }
    }
}
