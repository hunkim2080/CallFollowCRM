package com.detailline.callfollowcrm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.detailline.callfollowcrm.service.NotificationHelper

/**
 * 디버그 전용 — 협업 요청 알림(라이브 카운트다운 칩)을 샘플로 즉시 띄운다. release 엔 없음.
 *   커스텀 알림(카운트다운 Chronometer)이 다크/라이트에서 멀쩡히 그려지나 **에뮬 눈 확인**용. (2026-11-25)
 *   사용: adb shell am broadcast -a com.detailline.callfollowcrm.debug.COLLAB_INVITE -n com.detailline.callfollowcrm/.debug.DebugCollabInviteReceiver
 */
class DebugCollabInviteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val deadline = System.currentTimeMillis() + (3 * 3600 + 21 * 60 + 5) * 1000L  // ~03:21:05 남음
        NotificationHelper.showCollabInvite(
            context.applicationContext,
            shareId = "debug_invite_1",
            ownerName = "하우스픽",
            title = "용인 두산위브 현장",
            silent = false,
            deadlineMs = deadline,
            dailyWage = 25
        )
        Log.i("DebugCollabInvite", "협업 요청 카운트다운 알림 띄움 (deadline=$deadline)")
    }

    companion object {
        const val ACTION = "com.detailline.callfollowcrm.debug.COLLAB_INVITE"
    }
}
