package com.detailline.callfollowcrm.util

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * 📳 **카톡식 '문자 오면 화면 켜기'** — 꺼진 화면을 잠깐(3.5초) 켠다. (2026-11-06 사장님)
 *
 * 전화·알람이 쓰는 **full-screen intent 가 아니다**(그건 Play 가 전화·알람 앱에만 허용 — 문자에 쓰면 반려 위험).
 *   메신저 앱들이 쓰는 **화면 깨우기 잠금(wakelock)** 이다. `WAKE_LOCK` 은 일반 권한이라 Play 안전.
 *
 * 켜는 자리는 **`NotificationHelper.showIncomingSms` 한 곳** — 진짜 문의·답장이 **실제로 울릴 때만**.
 *   (토글 off · 야간 방해금지 · 이미 화면 켜짐 · 광고/스팸 은 부르는 쪽에서 걸러 여기까지 안 온다.)
 */
object ScreenWaker {

    private const val HOLD_MS = 3_500L

    /** 꺼진 화면이면 3.5초 켠다. 이미 켜져 있으면 아무것도 안 한다. 실패해도 조용히 넘어간다(화면만 못 켠 것). */
    fun wake(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (pm.isInteractive) return   // 이미 화면 켜져 있음 — 깨울 필요 없음
        runCatching {
            @Suppress("DEPRECATION")   // SCREEN_BRIGHT_WAKE_LOCK 는 deprecated 지만 '잠깐 화면 켜기'엔 이게 정석(메신저 앱 방식)
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "detailline:sms-screen-wake"
            )
            wl.setReferenceCounted(false)
            wl.acquire(HOLD_MS)   // 타임아웃 자동 해제 — 켜둔 채 못 놓는 사고 방지
        }.onFailure { Log.d("ScreenWaker", "wake skipped: ${it.message}") }
    }
}
