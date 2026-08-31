package com.detailline.callfollowcrm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.detailline.callfollowcrm.R

/**
 * 전화 오는 순간 잠깐 도는 포그라운드 서비스 — 목적은 단 하나:
 *   앱을 '포그라운드 격'으로 올려 삼성의 **백그라운드 오버레이 차단**(addView 시 "permission denied for
 *   window type 2038")을 통과시키는 것. 이게 없으면 통화 중(앱이 뒤로 밀린 상태) 테두리를 못 얹는다.
 *   (2026-08-31 사장님 — 실통화 로그로 원인 확정 후 A안)
 *
 * 동작: startForeground(최소 알림) → [IncomingCallOverlay] 가 테두리를 얹음 → 통화 끝나면 stop.
 *   알림은 무음·최소(전화 확인 중). FGS 규칙상 알림 1개는 필수라 최소로.
 */
class CallOverlayService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 매니페스트에 foregroundServiceType(specialUse) 선언 → 2인자 startForeground 로 그 타입 적용.
        runCatching { startForeground(NOTIF_ID, buildNotification(this)) }
        // 백스톱 — 통화 끝 신호를 놓쳐도 12초 뒤 스스로 내려 알림이 안 남게.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { stopSelf() } }, 12_000L)
        return START_NOT_STICKY
    }

    private fun buildNotification(ctx: Context): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CH_ID, "전화 확인", NotificationManager.IMPORTANCE_MIN).apply {
                description = "전화 올 때 상대 상태를 잠깐 확인하는 동안만 떠요."
                setSound(null, null); enableVibration(false); setShowBadge(false)
            }
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(ctx, CH_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("전화 확인 중")
            .setContentText("전화 오는 사람 미리보기")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 88231
        private const val CH_ID = "call_overlay_fgs"

        /** 앱을 포그라운드 격으로 — 오버레이 차단 통과용. 벨 울릴 때 [IncomingCallOverlay.onRinging] 이 호출. */
        fun start(context: Context) {
            val i = Intent(context, CallOverlayService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            }
        }

        /** 통화 끝나면 내림. */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, CallOverlayService::class.java)) }
        }
    }
}
