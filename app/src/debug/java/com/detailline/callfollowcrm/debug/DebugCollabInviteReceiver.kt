package com.detailline.callfollowcrm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.detailline.callfollowcrm.service.NotificationHelper

/**
 * 디버그 전용 — 협업 요청 알림(표준 글자 + [거절]/[수락] 버튼)을 샘플로 즉시 띄운다. release 엔 없음.
 *   표준 알림이 다크/라이트·접힌 배너에서 안 잘리나 + 막판 라이브 카운트다운 **에뮬 눈 확인**용. (2026-11-25)
 *   사용: adb shell am broadcast -a com.detailline.callfollowcrm.debug.COLLAB_INVITE -n com.detailline.callfollowcrm/.debug.DebugCollabInviteReceiver
 */
class DebugCollabInviteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val now = System.currentTimeMillis()
        // ① 11시간 남음 → 본문 "마감 11시간"(글자, 정적)
        NotificationHelper.showCollabInvite(
            context.applicationContext, shareId = "debug_invite_long",
            ownerName = "하우스픽", title = "용인 두산위브 현장",
            silent = false, deadlineMs = now + 11 * 3_600_000L, dailyWage = 25,
            scheduledAtMs = now + 2 * 86_400_000L, timeLabel = "14:00",
            addr = "경기 용인시 기흥구 행정타운두산위브1단지"
        )
        // ② 40분 남음 → 제목 옆 라이브 카운트다운(표준 Chronometer)
        NotificationHelper.showCollabInvite(
            context.applicationContext, shareId = "debug_invite_soon",
            ownerName = "탄성코트", title = "수지 현장",
            silent = false, deadlineMs = now + 40 * 60_000L, dailyWage = 30,
            scheduledAtMs = now + 3 * 86_400_000L, timeLabel = "09:00",
            addr = "경기 용인시 수지구 성복동"
        )
        Log.i("DebugCollabInvite", "협업 요청 알림 2종(11h·40m) 띄움")
    }

    companion object {
        const val ACTION = "com.detailline.callfollowcrm.debug.COLLAB_INVITE"
    }
}
