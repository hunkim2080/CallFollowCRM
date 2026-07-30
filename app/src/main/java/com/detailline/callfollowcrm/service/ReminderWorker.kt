package com.detailline.callfollowcrm.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.first
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
        // 마감 브리핑 정시 실행(밤 9시 타겟, 매 실행 후 스스로 다음날 재등록). 주기워커의 '우연히 21~23시에 걸려야 발사'
        //   위상함정·랜덤시각을 해결. inputData 로 이 전용 실행을 구분. (2026-07-30 버그감사 — 사장님 "마감 시각 부정확")
        if (inputData.getBoolean(KEY_BRIEF_ONLY, false)) {
            runCatching { checkDailyBrief(app.container, ignoreHourGate = true) }
            scheduleDailyBrief(applicationContext)
            return Result.success()
        }
        runCatching { checkInstallD1(app.container) }
        runCatching { checkBalanceDue(app.container) }
        runCatching { checkDailyBrief(app.container) }
        runCatching { checkRecurringDue(app.container) }
        // 팀원 출발 이벤트 — 앱 꺼져 있어도 주기 워커가 새 출발을 잡아 알림 (사장님 요청 2026-06-06).
        runCatching { app.container.teamEventCenter.poll(applicationContext) }
        // 협업 현장 진행 이벤트 — 서버 owner-events 준비 후 앱 종료 상태에서도 알림.
        runCatching { app.container.collabEventCenter.poll(applicationContext) }
        // 받은 협업 요청(pending) — 앱 꺼져 있어도 주기 워커가 새 요청 잡아 "수락하시겠어요?" 알림.
        runCatching { app.container.collabEventCenter.pollInvites(applicationContext) }
        runCatching { GeofenceManager.refresh(applicationContext) }
        // 오늘의 현장 상시 알림 갱신 (2026-07-10 사장님) — 오늘 시공 현장 주소를 상단에 계속.
        runCatching { refreshTodaySites(applicationContext, app.container) }
        // 본폰 미러 링크 백업 전송 (2026-07-13) — 앱 꺼진 동안 놓친 변경/실패분을 주기(~3h)로 재전송. 옵트인 아니면 skip.
        runCatching { app.container.mirrorSyncManager.pushNow(force = false) }
        // 본폰 미러 v2 — 새 공유 신청 폴 → 알림(앱 꺼져 있어도). (2026-07-14)
        runCatching { app.container.mirrorSyncManager.pollShareRequests(applicationContext) }
        return Result.success()
    }

    /** 마감 브리핑 — 저녁 9시경, 하루 1회. 확실한 데이터(새 고객·입금·내일 시공). */
    private suspend fun checkDailyBrief(container: AppContainer, ignoreHourGate: Boolean = false) {
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        // 전용 워커(정시)면 게이트 무시(Doze 로 좀 늦어도 그날 밤 발사). 주기워커=백스톱이면 21시 이후 아무 때나
        //   (상한 23시 제거 — 정시 워커가 놓친 날/늦은 밤도 그날 안에 구제). 중복은 아래 brief:날짜 키로 방지. (2026-07-30)
        if (!ignoreHourGate && hour < 21) return // 프로토 brief = 오후 9시.

        val todayStart = DateTimeUtils.startOfDay(now)
        val prefs = container.preferences
        val keys = prefs.reminderNotifiedKeys.toMutableSet()
        val key = "brief:$todayStart"
        if (key in keys) return

        if (showDailyBriefNow(applicationContext, container, respectQuietDay = true)) {
            keys.add(key)
            prefs.reminderNotifiedKeys = keys
        }
    }

    companion object {
        const val KEY_BRIEF_ONLY = "brief_only"

        /**
         * 마감 브리핑을 '다음 밤 9시'에 정확히 1회 실행하도록 예약(전용 유니크 워커, 매 실행 후 재등록).
         *   기존 3시간 주기 워커가 hour∈[21,23) 에 우연히 걸려야만 발사하던 위상함정(며칠씩 안 옴)·랜덤시각을 해결.
         *   WorkManager 라 앱 종료·재부팅에도 예약 보존. Doze 로 약간 늦어도 그날 밤 안엔 발사. (2026-07-30 버그감사)
         */
        fun scheduleDailyBrief(context: Context) {
            runCatching {
                val now = System.currentTimeMillis()
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 1) // 오늘 9시 지났으면 내일 9시
                val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(cal.timeInMillis - now, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_BRIEF_ONLY to true))
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "dailyBrief", ExistingWorkPolicy.REPLACE, req
                )
            }
        }

        /**
         * 게이트(시간·중복) 없이 마감 브리핑을 계산·표시. 표시했으면 true. checkDailyBrief 와 디버그 트리거가 공유.
         *   respectQuietDay=false 면 성과 0인 조용한 날도 강제로 띄움(디버그 미리보기용).
         */
        suspend fun showDailyBriefNow(
            context: Context,
            container: AppContainer,
            respectQuietDay: Boolean
        ): Boolean {
            val now = System.currentTimeMillis()
            val todayStart = DateTimeUtils.startOfDay(now)
            val todayEnd = todayStart + DateTimeUtils.DAY_MS
            val tomorrowStart = todayEnd
            val tomorrowEnd = tomorrowStart + DateTimeUtils.DAY_MS
            val customers = runCatching { container.customerRepository.allOnce() }.getOrDefault(emptyList())
            val newCustomers = customers.count { it.createdAt in todayStart until todayEnd }
            val deposits = customers.count { c ->
                (c.depositPaidAt?.let { it in todayStart until todayEnd } == true) ||
                    (c.balancePaidAt?.let { it in todayStart until todayEnd } == true)
            }
            val tomorrowJobs = customers.filter {
                it.scheduledWorkDate?.let { s -> DateTimeUtils.startOfDay(s) in tomorrowStart until tomorrowEnd } == true
            }
            if (respectQuietDay && newCustomers == 0 && deposits == 0 && tomorrowJobs.isEmpty()) return false

            val firstJob = tomorrowJobs.minByOrNull { it.scheduledWorkDate ?: Long.MAX_VALUE }
            val tomorrowLabel = firstJob?.let { c ->
                val nm = c.name?.takeIf { it.isNotBlank() } ?: c.phoneNumber
                val t = c.scheduledWorkMinutes?.let { DateTimeUtils.formatWorkMinutes(it) }
                nm + (t?.let { " $it" } ?: "")
            }
            NotificationHelper.showDailyBrief(context, newCustomers, deposits, tomorrowJobs.size, tomorrowLabel)
            return true
        }

        /**
         * 오늘의 현장 상시 알림 갱신 (2026-07-10 사장님) — 오늘 시공(주소 있는) 현장을 무음·상단고정 알림으로.
         *   현장에서 "몇동 몇호였지?" 를 앱 안 열고 상단에서 바로 확인. 오늘 현장 없으면 알림 내림.
         *   호출: ReminderWorker(주기 ~3h) + 앱 시작(Application). 시각순 정렬, 주소는 동/호까지 그대로(tidyAddress).
         */
        suspend fun refreshTodaySites(context: Context, container: AppContainer) {
            val now = System.currentTimeMillis()
            val todayStart = DateTimeUtils.startOfDay(now)
            val todayEnd = todayStart + DateTimeUtils.DAY_MS
            val customers = runCatching { container.customerRepository.allOnce() }.getOrDefault(emptyList())
            val today = customers.filter { c ->
                val s = c.scheduledWorkDate ?: return@filter false
                if (DateTimeUtils.startOfDay(s) !in todayStart until todayEnd || c.address.isNullOrBlank()) return@filter false
                // 잔금까지 다 받은(완납) 현장은 제외 — 마무리된 곳은 오늘의 현장에서 내림. (2026-07-15 사장님)
                !(com.detailline.callfollowcrm.domain.settlement.SettlementCalc.hasMoney(c) &&
                    com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(c).isPaidOff)
            }.sortedBy { it.scheduledWorkMinutes ?: 0 }
            if (today.isEmpty()) { NotificationHelper.clearTodaySites(context); return }
            val lines = today.map { c ->
                // 전화번호는 표시 안 함(탭하면 어차피 문자로 감) — "주소만 딱 잘 보이게". (2026-07-15 사장님)
                //   ⚠️ 이름 칸에 번호가 들어있는 고객이 많다(이름 없이 저장되면 번호가 이름이 됨).
                //   그래서 c.phoneNumber 만 빼는 걸론 부족 — 이름이 '번호 모양'이면 그것도 뺀다.
                //   (사장님 신고 2026-07-15: 번호 뺐다는데 알림에 "…24 A동 1호 01054790582" 가 계속 떴음)
                val nm = c.name?.takeIf {
                    it.isNotBlank() && !com.detailline.callfollowcrm.util.PhoneNumberFormatter.looksLikePhone(it)
                } ?: ""
                val t = c.scheduledWorkMinutes?.let { DateTimeUtils.formatWorkMinutes(it) } ?: ""
                val addr = com.detailline.callfollowcrm.util.AddressExtractor.tidyAddress(c.address)
                val second = listOf(nm, t).filter { it.isNotBlank() }.joinToString(" ")
                if (second.isNotBlank()) "📍 $addr\n$second" else "📍 $addr"
            }
            NotificationHelper.showTodaySites(context, today.size, lines, today.first().phoneNumber)
        }
    }

    /** 정기 문자 발송 대상 — 시공일 + 주기 배수 = 오늘. 오전 9시경, 하루 1회 집계 알림. */
    private suspend fun checkRecurringDue(container: AppContainer) {
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        if (hour < 9) return // 프로토 recur = 오전 9시. 상한(11시) 제거 — 3h주기×2h창 위상함정(특정 위상이면 며칠 안 옴) 방지. 하루 1회 dedup. (2026-07-30 버그감사)

        val todayStart = DateTimeUtils.startOfDay(now)
        val prefs = container.preferences
        val keys = prefs.reminderNotifiedKeys.toMutableSet()
        val key = "recur:$todayStart"
        if (key in keys) return

        val rules = runCatching {
            container.recurringMessageRepository.observeEnabledRules().first()
        }.getOrDefault(emptyList())
        if (rules.isEmpty()) return
        val customers = runCatching { container.customerRepository.allOnce() }.getOrDefault(emptyList())

        var due = 0
        val ruleNames = LinkedHashSet<String>()
        for (rule in rules) {
            val interval = rule.intervalDays
            if (interval <= 0) continue
            val intervalMs = interval * DateTimeUtils.DAY_MS
            for (c in customers) {
                if (rule.targetCategoryId != null && c.categoryId != rule.targetCategoryId) continue
                val s = c.scheduledWorkDate ?: continue
                val diff = todayStart - DateTimeUtils.startOfDay(s)
                if (diff > 0 && diff % intervalMs == 0L) {
                    due++
                    ruleNames.add(rule.name)
                }
            }
        }
        if (due <= 0) return
        NotificationHelper.showRecurringDue(applicationContext, due, ruleNames.joinToString(" · "))
        keys.add(key)
        prefs.reminderNotifiedKeys = keys
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
            // 미수 금액은 정산 단일 출처(SettlementCalc)로 — 정산탭/홈/알림 금액 불일치 제거. (2026-07-30)
            val remaining = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(c).outstanding
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
        if (!container.preferences.d1AutoEnabled) return // 자동문자 D-1 토글 OFF
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        if (hour < container.preferences.d1SendHour) return // 설정한 시각 이후에만

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
