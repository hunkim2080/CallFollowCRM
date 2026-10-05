package com.detailline.callfollowcrm.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.detailline.callfollowcrm.data.local.AppDatabase
import com.detailline.callfollowcrm.data.local.entity.ScheduledSmsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🕐 예약 문자 **알람 걸기/풀기** — 시각이 되면 [ScheduledSmsReceiver] 가 깨어나 [ScheduledSmsSender] 로 보낸다.
 *
 *   `setAndAllowWhileIdle` 을 쓴다 — **특별한 권한 없이** Doze(절전) 중에도 거의 그 시각에 발사된다.
 *   (정밀 알람 `setExactAndAllowWhileIdle` 은 안드12+ `SCHEDULE_EXACT_ALARM` 권한·플레이 정책 마찰이 있어 보류.
 *    예약 문자는 분 단위 정밀이 꼭 필요한 건 아니라 이 선택이 맞다.)
 *
 *   알람은 **재부팅·앱 업데이트 때 사라진다** → [rearmAll] 로 앱 켤 때·부팅 때 다시 건다.
 *   그새 지나버린 건(폰이 꺼져 있었음) 즉시 보낸다.
 */
object ScheduledSmsScheduler {

    private const val TAG = "ScheduledSmsScheduler"
    private const val REQ_BASE = 920000  // 다른 PendingIntent 요청코드와 안 겹치게

    /** 한 건 예약 — row.sendAtMs 에 발사. */
    fun schedule(context: Context, row: ScheduledSmsEntity) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, row.sendAtMs, pendingIntent(context, row.id))
        }.onFailure { Log.w(TAG, "schedule failed id=${row.id}", it) }
    }

    /** 예약 취소(사장님) — 알람 풀기. 행 삭제는 호출자가 DAO 로. */
    fun cancel(context: Context, rowId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(pendingIntent(context, rowId)) }
    }

    private fun pendingIntent(context: Context, rowId: Long): PendingIntent {
        val intent = Intent(context.applicationContext, ScheduledSmsReceiver::class.java).apply {
            action = ScheduledSmsReceiver.ACTION_FIRE
            putExtra(ScheduledSmsReceiver.EXTRA_ROW_ID, rowId)
            // row 마다 다른 PendingIntent 가 되도록 data 로 구분(안 그러면 요청코드 같을 때 덮어씀).
            data = Uri.parse("sms-schedule://row/$rowId")
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context.applicationContext, REQ_BASE + rowId.toInt(), intent, flags)
    }

    /** 앱 켤 때·부팅 때 — 대기 중 예약을 다시 걸고, 지나버린 건 즉시 보낸다. */
    suspend fun rearmAll(context: Context) {
        val app = context.applicationContext
        val dao = AppDatabase.getInstance(app).scheduledSmsDao()
        val now = System.currentTimeMillis()
        val pending = withContext(Dispatchers.IO) { dao.allPending() }
        pending.forEach { row ->
            if (row.sendAtMs <= now) {
                runCatching { ScheduledSmsSender.sendNow(app, row.id) }
                    .onFailure { Log.w(TAG, "catch-up send failed id=${row.id}", it) }
            } else {
                schedule(app, row)
            }
        }
    }
}
