package com.detailline.callfollowcrm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.detailline.callfollowcrm.service.NotificationHelper

/**
 * 디버그 전용 — '오늘의 현장' 커스텀 배너 알림을 샘플 데이터로 즉시 띄운다(DB 시드 불필요). release 엔 없음.
 *   커스텀 알림(청록 바 + 동호수 밑줄)이 다크/라이트에서 멀쩡히 그려지나 **에뮬에서 눈으로** 확인용. (2026-11-25)
 *   사용: adb shell am broadcast -a com.detailline.callfollowcrm.debug.TODAY_NOTI -n com.detailline.callfollowcrm/.debug.DebugTodayNotiReceiver
 */
class DebugTodayNotiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        // 사장님 실제 긴-이름 현장으로 — 괄호 안 쉼표 케이스까지 그대로.
        NotificationHelper.showTodaySites(
            context.applicationContext, count = 1,
            lines = listOf("📍 동아 아파트 718동 602호\n오전 9시"),
            openPhone = null,
            bigTitle = "동아 아파트 718동 602호",
            subText = "오늘의 현장 · 오전 9시",
            bigBody = "📍 경기 수원시 영통구 봉영로 1526 (살구골 진덕,서광,성지,동아 아파트) 718동 602호\n오전 9시",
            bannerRoad = "경기 수원시 영통구 봉영로 1526",
            bannerMeta = "⏰ 오전 9시 · 탭하면 길찾기"
        )
        Log.i("DebugTodayNoti", "오늘의 현장 커스텀 배너 띄움")
    }

    companion object {
        const val ACTION = "com.detailline.callfollowcrm.debug.TODAY_NOTI"
    }
}
