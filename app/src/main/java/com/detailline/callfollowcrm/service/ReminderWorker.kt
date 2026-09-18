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
import com.detailline.callfollowcrm.domain.reminder.JobReminderCalc
import com.detailline.callfollowcrm.util.DataBackup
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
        runCatching { checkAsToday(app.container) }
        runCatching { checkBalanceDue(app.container) }
        runCatching { checkDailyBrief(app.container) }
        runCatching { checkRecurringDue(app.container) }
        // ☁️ 하루 한 번 서버 백업 — 눌러야만 되던 걸 앱이 알아서. (2026-09-18 사장님)
        runCatching { checkAutoBackup(app.container) }
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

    /**
     * ☁️ 자동 서버 백업 — 하루 한 번. (2026-09-18 사장님)
     *
     * 왜 넣었나: 전엔 더보기에서 [서버에 백업]을 **눌러야만** 올라갔다.
     *   업무폰 앱이 통째로 지워진 날, 살아난 건 사장님이 오후에 눌러두신 덕분이었다.
     *   사람 손에 기대는 안전장치는 안전장치가 아니다.
     *
     * 지키는 것 세 가지:
     *   · 하루 한 번만 (20시간 간격 — 워커가 ~3시간마다 도니 하루에 한 번만 걸린다)
     *   · 로그인(사업자 번호) 돼 있을 때만
     *   · **빈 장부는 안 올린다** — 앱을 막 깐 빈 상태가 서버의 멀쩡한 백업을 덮으면 최악이다.
     *     (실제로 그럴 뻔했다.) 새로 시작한 사장님은 손님이 한 명 생기는 순간부터 올라간다.
     */
    private suspend fun checkAutoBackup(container: AppContainer) {
        val ctx = applicationContext
        val now = System.currentTimeMillis()
        val myLast = DataBackup.lastBackupAt(ctx)
        if (now - myLast < 20L * 60 * 60 * 1000) return
        if (container.preferences.bizPhone.filter { it.isDigit() }.length < 9) return
        if (DataBackup.isLedgerEmpty(ctx)) return

        // 🛡️ **같은 번호를 쓰는 다른 폰(테스트용 복사폰)이 주인이면 자동 백업은 비킨다.** (2026-09-18)
        //   서버의 백업 칸은 번호당 하나다. 전엔 [서버에 백업]을 눌러야만 덮어써서 사람이 조심하면 됐는데,
        //   자동으로 만드는 순간 복사폰이 **매일 조용히 업무폰 백업을 덮어쓰게** 된다.
        //   (서버에도 같은 경고가 적혀 있다 — 사장님 "내 업무폰에 피해가지 않도록 해줘" 2026-09-16)
        //   규칙: 서버 것이 내가 올린 것보다 새것이면 = 다른 폰이 쓰고 있다 → 자동은 쉰다.
        //         단 사흘 넘게 아무도 안 올렸으면 = 주인이 없다 → 내가 이어받는다(폰 바꿨을 때).
        //   [서버에 백업] 손버튼은 그대로 — 누르면 언제든 주인이 바뀐다.
        val remote = container.backupRepository.status()
        if (remote != null && remote.has && remote.updatedAtMs > myLast) {
            val quietFor = now - remote.updatedAtMs
            if (quietFor < 3L * 24 * 60 * 60 * 1000) {
                android.util.Log.i("AutoBackup", "다른 폰이 백업 주인 — 이번엔 건너뜀")
                return
            }
        }

        val bytes = try {
            DataBackup.serverBlobBytes(ctx)
        } catch (e: Throwable) {
            // OutOfMemoryError 까지 잡는다 — 백업 하나 때문에 앱이 꺼지면 안 된다. (2026-09-15 전례)
            android.util.Log.w("AutoBackup", "백업 만들기 실패", e)
            return
        }
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        if (container.backupRepository.push(b64)) {
            DataBackup.markBackedUpNow(ctx)
            android.util.Log.i("AutoBackup", "자동 백업 완료 ${bytes.size / 1024}KB")
        }
        // 실패하면 시각을 안 남긴다 → 3시간 뒤 워커가 다시 시도.
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
            NotificationHelper.showDailyBrief(context, newCustomers, deposits, tomorrowJobs.size, tomorrowLabel, briefDayMs = todayStart)
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

    /**
     * 오늘 A/S 예약이 있는 고객 → '오늘 A/S 있어요' 알림 (아침 창, 고객·날짜별 1회). (2026-08-01 사장님)
     *   A/S 는 무료라 깜빡 잊기 쉽고, 잊으면 신뢰가 크게 깎임 → 그날 아침에 짚어줌. 자동문자 없음(로컬 알림뿐).
     *   여러 날 A/S(asScheduledDays)면 기간 중 매일 아침 알림(각 날 dedup). 시공(scheduledWorkDate)과 별개 필드.
     */
    private suspend fun checkAsToday(container: AppContainer) {
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        if (hour < 8) return // 아침 8시 이후. 상한 없음(놓친 날도 그날 안엔 구제) + 아래 날짜키로 하루 1회.

        val todayStart = DateTimeUtils.startOfDay(now)
        val prefs = container.preferences
        val keys = prefs.reminderNotifiedKeys.toMutableSet()
        var changed = false

        val customers = runCatching { container.customerRepository.allOnce() }.getOrDefault(emptyList())
        for (c in customers) {
            val asStart = c.asScheduledDate ?: continue
            val s = DateTimeUtils.startOfDay(asStart)
            val days = c.asScheduledDays.coerceAtLeast(1)
            val end = s + (days - 1) * DateTimeUtils.DAY_MS
            if (todayStart < s || todayStart > end) continue // 오늘이 A/S 기간 밖
            val key = "as:${c.id}:$todayStart"
            if (key in keys) continue

            val nm = c.name?.takeIf { it.isNotBlank() } ?: c.phoneNumber
            val dayN = ((todayStart - s) / DateTimeUtils.DAY_MS).toInt() + 1
            val whenLabel = if (days > 1) "A/S ${days}일 중 ${dayN}일차" else "오늘 A/S"
            val address = c.address?.takeIf { it.isNotBlank() } ?: "주소 미입력"
            NotificationHelper.showAsToday(applicationContext, c.id, c.phoneNumber, nm, whenLabel, address)
            keys.add(key)
            changed = true
        }
        if (changed) prefs.reminderNotifiedKeys = keys
    }

    /** 시공 완료 3일 지났는데 잔금 미입금 → 잔금 미수 알림 (오전 창, 고객별 1회). */
    private suspend fun checkBalanceDue(container: AppContainer) {
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        // 하한만(오전 9시부터). 상한(||hour>=12) 제거 — 3시간 주기 워커가 Doze로 9~12시 창을 건너뛰면
        //   그날 잔금(돈) 미수 알림이 통째로 스킵되던 위상함정. 형제 알림(정기·D-1·A/S·브리핑)은 이미 하한만.
        //   dedup 키(settle:{id}:{시공일})가 하루 1회를 보장하므로 상한 불필요. 프로토 '오전 10시경'은 목표시각. (2026-08-11 알림 감사)
        if (hour < 9) return

        val threshold = DateTimeUtils.startOfDay(now) - 3 * DateTimeUtils.DAY_MS // 시공 후 3일 경과
        val prefs = container.preferences
        val keys = prefs.reminderNotifiedKeys.toMutableSet()
        var changed = false

        // 🔴 **건(件)별로** 본다. (2026-09-17 재방문 Stage B)
        //   전에는 고객 표(대표 건)만 봐서, 1차 잔금이 남았는데 2차를 새로 잡으면
        //   대표 건이 2차로 바뀌며 **1차 미수가 조용히 사라졌다.** 돈이 새는 쪽이다.
        //   금액 규칙은 그대로 SettlementCalc 단일 출처 — 건에도 같은 자를 쓴다(rowOf(job)).
        val jobs = runCatching { container.jobRepository.scheduledOnce() }.getOrDefault(emptyList())
        val customers = runCatching { container.customerRepository.allOnce() }.getOrDefault(emptyList())
        val byId = customers.associateBy { it.id }
        for (j in jobs) {
            if (j.balancePaidAt != null) continue // 완납
            val row = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(j)
            if (row.total <= 0L) continue
            val remaining = row.outstanding
            if (remaining <= 0L) continue
            val scheduled = j.scheduledWorkDate ?: continue
            // 여러 날 공사는 **끝나는 날** 기준 — 3일 공사 첫날부터 세면 사장님이 현장에 있는데 잔금 독촉이 간다.
            val lastDay = DateTimeUtils.startOfDay(scheduled) +
                (j.scheduledWorkDays.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS
            if (lastDay > threshold) continue // 아직 3일 안 지남(또는 미래)

            val c = byId[j.customerId] ?: continue
            // dedup 키를 **건 id** 로 — 고객+시공일 키는 같은 날 두 현장을 한 건으로 뭉쳤다.
            val key = "settlej:${j.id}"
            if (key in keys) continue
            val nm = c.name?.takeIf { it.isNotBlank() } ?: c.phoneNumber
            val daysSince = ((now - lastDay) / DateTimeUtils.DAY_MS).toInt().coerceAtLeast(0)
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

        // 🔴 **건(件)별로** 본다. (2026-09-17 재방문 Stage B)
        //   전에는 고객 표의 scheduledWorkDate = **대표 건 하나**만 봤다. 그래서 한 고객이
        //   두 날짜를 잡으면(인테리어 업체 1차·2차) **두 번째 날짜의 D-1 이 아예 안 울렸다.**
        //   판단은 JobReminderCalc(순수함수 + 단위테스트)가 하고, 여기선 알림만 띄운다.
        val jobs = runCatching { container.jobRepository.scheduledOnce() }.getOrDefault(emptyList())
        val customers = runCatching { container.customerRepository.allOnce() }.getOrDefault(emptyList())
        val byId = customers.associateBy { it.id }
        val due = JobReminderCalc.d1Due(
            jobs = jobs,
            tomorrowStart = tomorrowStart,
            dayMs = DateTimeUtils.DAY_MS,
            notified = keys,
            startOfDay = { DateTimeUtils.startOfDay(it) }
        )
        for (d in due) {
            val c = byId[d.job.customerId] ?: continue
            val scheduled = d.job.scheduledWorkDate ?: continue
            val nm = c.name?.takeIf { it.isNotBlank() } ?: c.phoneNumber
            val dateLabel = SimpleDateFormat("M/d(E)", Locale.KOREA).format(Date(scheduled))
            val timeLabel = d.job.scheduledWorkMinutes?.let { DateTimeUtils.formatWorkMinutes(it) }
            // 주소도 그 **건**의 것 — 같은 고객이라도 현장이 다를 수 있다.
            val address = d.job.address?.takeIf { it.isNotBlank() }
                ?: c.address?.takeIf { it.isNotBlank() } ?: "주소 미입력"
            NotificationHelper.showInstallD1(
                applicationContext, c.id, c.phoneNumber, nm, dateLabel, timeLabel, address
            )
            keys.add(d.key)
            changed = true
        }
        if (changed) prefs.reminderNotifiedKeys = keys
    }
}
