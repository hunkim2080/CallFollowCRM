package com.detailline.callfollowcrm.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 시간 기반 알림 스케줄러 (2026-06-03) — WorkManager 주기 실행(~3시간).
 *   현재: 시공 D-1 (프로토 PUSH.d1). 잔금 미수·마감 브리핑은 추후 같은 패턴으로 추가.
 *   앱이 꺼져 있어도 WorkManager 가 깨워서 실행 → 중복은 prefs 키로 방지.
 */
class ReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CallFollowCrmApplication ?: return Result.success()
        runCatching { checkInstallD1(app.container) }
        return Result.success()
    }

    /** 내일 시공 예약 고객 → D-1 안내 알림 (저녁 창에서만, 고객별 1회). */
    private suspend fun checkInstallD1(container: AppContainer) {
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        if (hour < 17) return // 프로토 d1 = 오후 6시경. 저녁 창에서만 발송.

        val tomorrowStart = DateTimeUtils.startOfDay(now) + DateTimeUtils.DAY_MS
        val tomorrowEnd = tomorrowStart + DateTimeUtils.DAY_MS
        val prefs = container.preferences
        val keys = prefs.reminderNotifiedKeys.toMutableSet()
        var changed = false

        val customers = runCatching { container.customerRepository.allOnce() }.getOrDefault(emptyList())
        for (c in customers) {
            val scheduled = c.scheduledWorkDate ?: continue
            val day = DateTimeUtils.startOfDay(scheduled)
            if (day < tomorrowStart || day >= tomorrowEnd) continue
            val key = "d1:${c.id}:$tomorrowStart"
            if (key in keys) continue

            val nm = c.name?.takeIf { it.isNotBlank() } ?: c.phoneNumber
            val dateLabel = SimpleDateFormat("M/d(E)", Locale.KOREA).format(Date(scheduled))
            val timeLabel = c.scheduledWorkMinutes?.let { DateTimeUtils.formatWorkMinutes(it) }
            val address = c.address?.takeIf { it.isNotBlank() } ?: "주소 미입력"
            NotificationHelper.showInstallD1(
                applicationContext, c.id, c.phoneNumber, nm, dateLabel, timeLabel, address
            )
            keys.add(key)
            changed = true
        }
        if (changed) prefs.reminderNotifiedKeys = keys
    }
}
