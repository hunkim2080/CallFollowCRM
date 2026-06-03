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
        runCatching { checkBalanceDue(app.container) }
        return Result.success()
    }

    /** 시공 완료 3일 지났는데 잔금 미입금 → 잔금 미수 알림 (오전 창, 고객별 1회). */
    private suspend fun checkBalanceDue(container: AppContainer) {
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        if (hour < 9 || hour >= 12) return // 프로토 settle = 오전 10시경.

        val threshold = DateTimeUtils.startOfDay(now) - 3 * DateTimeUtils.DAY_MS // 시공 후 3일 경과
        val prefs = container.preferences
        val keys = prefs.reminderNotifiedKeys.toMutableSet()
        var changed = false

        val customers = runCatching { container.customerRepository.allOnce() }.getOrDefault(emptyList())
        for (c in customers) {
            val total = c.totalAmount ?: 0L
            if (total <= 0L) continue
            if (c.balancePaidAt != null) continue // 완납
            val deposit = c.depositAmount ?: 0L
            val remaining = c.balanceAmount ?: (total - deposit).coerceAtLeast(0L)
            if (remaining <= 0L) continue
            val scheduled = c.scheduledWorkDate ?: continue
            if (DateTimeUtils.startOfDay(scheduled) > threshold) continue // 아직 3일 안 지남(또는 미래)

            val key = "settle:${c.id}"
            if (key in keys) continue
            val nm = c.name?.takeIf { it.isNotBlank() } ?: c.phoneNumber
            val daysSince = ((now - scheduled) / DateTimeUtils.DAY_MS).toInt().coerceAtLeast(0)
            NotificationHelper.showBalanceDue(
                applicationContext, c.id, c.phoneNumber, nm, remaining / 10_000L, daysSince
            )
            keys.add(key)
            changed = true
        }
        if (changed) prefs.reminderNotifiedKeys = keys
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
