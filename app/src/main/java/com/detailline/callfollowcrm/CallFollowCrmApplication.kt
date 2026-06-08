package com.detailline.callfollowcrm

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.seed.DefaultPricingItems
import com.detailline.callfollowcrm.data.local.seed.DefaultTemplates
import com.detailline.callfollowcrm.service.NotificationHelper
import com.detailline.callfollowcrm.util.CallLogHelper
import com.detailline.callfollowcrm.domain.model.HandledStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CallFollowCrmApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 앱 수명 동안 도는 IO 스코프 — SmsSender 등 컴포넌트의 fire-and-forget 보존 작업용. */
    val applicationScope: CoroutineScope get() = appScope

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannels(this)

        appScope.launch {
            DefaultTemplates.seedIfEmpty(container.messageTemplateRepository)
            DefaultPricingItems.seedIfEmpty(container.pricingItemRepository)
            // 2026-05-30 #7 — 기본 카테고리 seed + 옛 고객 1회 자동 분류.
            com.detailline.callfollowcrm.data.local.seed.DefaultCategories.seedIfMissing(
                container.categoryRepository
            )
            if (!container.preferences.autoCategoryBackfilled) {
                runCatching { container.autoCategoryClassifier.backfillAll() }
                container.preferences.autoCategoryBackfilled = true
            }
            // 2026-06-07 — 카테고리 규칙 수정(날짜 등록=시공대기, 상담만=미분류) 후 1회 재정리.
            if (!container.preferences.autoCategoryRebuiltV2) {
                runCatching { container.autoCategoryClassifier.backfillAll() }
                container.preferences.autoCategoryRebuiltV2 = true
            }
            // 2026-06-07 — 견적 기록 버그 수정 전(6/6 이전) 잘못 쌓인 "견적 회신 챙기기" 데이터 1회 정리.
            if (!container.preferences.estimateSentLegacyCleaned) {
                runCatching { container.messageHistoryRepository.deleteEstimateSentBefore(1780671600000L) }
                container.preferences.estimateSentLegacyCleaned = true
            }
            // 2026-06-07 — 발신 서명("직영팀만 시공 (외주/일당 절대 X)")이 분류 본문에 섞여 고객이 '일당'
            //   카테고리로 잘못 분류된 것 1회 해제(미분류로). '일당'은 수첩 개념이라 고객 카테고리에 있으면 안 됨.
            if (!container.preferences.dailyWageCategoryCleanedV1) {
                runCatching {
                    val cats = container.categoryRepository.observeAll().first()
                    val wageCatIds = cats.filter { it.name.trim() == "일당" }.map { it.id }.toHashSet()
                    if (wageCatIds.isNotEmpty()) {
                        container.customerRepository.observeAll().first().forEach { cust ->
                            if (cust.categoryId in wageCatIds) {
                                container.categoryRepository.assignCustomer(cust.id, null)
                            }
                        }
                    }
                }
                container.preferences.dailyWageCategoryCleanedV1 = true
            }
            // 2026-06-07 — 옛 '밀어서 정리=스팸' 버그로 잘못 스팸 처리된 번호들(답장 안 한 진짜 고객)을
            //   '정리됨'으로 이관(미확인에서만 숨김) + 스팸 해제. → 신규/목록에 정상 고객으로 복귀.
            if (!container.preferences.spamSweptToDismissedV1) {
                runCatching {
                    val sufs = container.spamPhoneRepository.suffixes.first()
                    if (sufs.isNotEmpty()) {
                        container.preferences.dismissedUnconfirmedSuffixes =
                            container.preferences.dismissedUnconfirmedSuffixes + sufs
                        sufs.forEach { container.spamPhoneRepository.unmark(it) }
                    }
                }
                container.preferences.spamSweptToDismissedV1 = true
            }
        }

        // SMS/MMS 캐시 prefetch — 최근 20개 번호. ChatScreen 첫 진입을 즉시 보이게 하는 토대.
        // READ_SMS 권한 없으면 silent skip.
        container.smsCachePrefetcher.prefetchRecentContacts(contactLimit = 20)

        // 2026-05-28 사장님 통점 fix: SMS 풀스캔 (17000건) 가 매번 느림.
        //   해결: sms_contacts_cache 테이블 (DB v16). 첫 실행 시 풀스캔 → 캐시 채움.
        //   그 후 HomeScreen 은 Room observe 만 → instant 갱신 (재시작 후에도 빠름).
        //   새 SMS 도착 = SmsReceiver 가 upsertOne 으로 incremental update.
        appScope.launch {
            // 캐시 비어있을 때만 풀스캔 (재시작 후엔 skip — 기존 데이터 유효).
            val cached = runCatching { container.smsContactCacheRepository.count() }.getOrDefault(0)
            if (cached == 0) {
                val contacts = runCatching {
                    container.smsRepository.queryContactsOnce(scanLimit = 10000, contactLimit = 500)
                }.getOrDefault(emptyList())
                if (contacts.isNotEmpty()) {
                    runCatching {
                        container.smsContactCacheRepository.rebuildFromFullScan(contacts)
                    }
                }
            }
        }

        // MMS(사진/첨부 문자) 감지 (2026-06-06) — 기본 문자앱이 아니라 브로드캐스트로 못 받아
        //   "오늘 신규"에서 누락됐음(SmsReceiver 는 SMS 만). 시작 시 1회 스캔 + content://mms 변경 감시로
        //   캐시에 머지 → MMS 로 처음 연락온 번호도 신규/목록에 잡힘. READ_SMS 로 읽기 가능(기본앱 전환 불필요).
        appScope.launch { syncMmsContacts() }
        // 2026-06-07 사장님 통점: 새 문자가 "오늘 신규"에 안 잡힘 — SmsReceiver 가 백그라운드/도즈에서
        //   누락되면 캐시가 갱신 안 되고, 풀스캔은 첫 설치 때만 돌아 보충이 없었음.
        //   해결: 앱 켤 때 + 60초마다 최근 SMS 를 캐시에 머지(self-heal) → 놓친 문자도 곧 잡힘.
        appScope.launch { syncSmsContacts() }
        runCatching {
            val mmsObserver = object : android.database.ContentObserver(
                android.os.Handler(android.os.Looper.getMainLooper())
            ) {
                override fun onChange(selfChange: Boolean) {
                    mmsSyncJob?.cancel()
                    mmsSyncJob = appScope.launch { delay(1500); syncMmsContacts() }
                }
            }
            contentResolver.registerContentObserver(
                android.net.Uri.parse("content://mms"), true, mmsObserver
            )
        }

        // 2026-05-28 사장님 통점 fix: 정적 BroadcastReceiver (CallStateReceiver) 가
        //   Android 12+ / OneUI 에서 누락되는 케이스 多 → 통화 종료 감지 실패.
        //   Application 에서 TelephonyCallback (Android 12+) / PhoneStateListener (이하) 동적 등록 →
        //   백그라운드 정책 영향 적고, BroadcastReceiver 와 이중 안전망.
        //   권한 없으면 silent skip.
        registerCallStateListener()

        // 시공접수서 제출 폴링 — 앱이 켜져 있는 동안 60초마다 새 제출 동기화 → 고객 카드 반영 + 알림.
        //   (완전 백그라운드(앱 종료 상태) 알림은 WorkManager/FCM 필요 — 추후.)
        appScope.launch {
            while (true) {
                runCatching { container.intakeSyncManager.sync(this@CallFollowCrmApplication) }
                // 팀원 출발 이벤트 — 새 출발이면 알림 + 상담함 배너 갱신 (사장님 요청 2026-06-06).
                runCatching { container.teamEventCenter.poll(this@CallFollowCrmApplication) }
                // 협업 현장 진행 이벤트 — 서버 owner-events 가 열리면 출발/도착/완료 알림 + 상담함 배너.
                runCatching { container.collabEventCenter.poll(this@CallFollowCrmApplication) }
                // 최근 SMS/MMS 캐시 self-heal — SmsReceiver 가 놓친 문자도 60초 내 "오늘 신규"에 반영.
                runCatching { syncSmsContacts() }
                runCatching { syncMmsContacts() }
                delay(60_000)
            }
        }

        // 시간 기반 알림(시공 D-1 등) — WorkManager 주기 실행(앱 종료 상태에서도).
        scheduleReminders()

        // 현장 도착 지오펜스 — 다가오는 시공 현장 5km 등록(권한·토글 있을 때만).
        appScope.launch {
            runCatching { com.detailline.callfollowcrm.service.GeofenceManager.refresh(this@CallFollowCrmApplication) }
        }
    }

    /** content://mms 변경 감시 debounce 용 잡. */
    private var mmsSyncJob: kotlinx.coroutines.Job? = null

    /** 최근 SMS 연락처를 캐시에 머지 — SmsReceiver 가 놓친 문자도 "오늘 신규"·목록에 잡히게. */
    private suspend fun syncSmsContacts() {
        val contacts = runCatching {
            container.smsRepository.queryRecentContacts(scanLimit = 2000, contactLimit = 200)
        }.getOrDefault(emptyList())
        for (c in contacts) runCatching { container.smsContactCacheRepository.upsertOne(c) }
    }

    /** 최근 MMS 연락처를 캐시에 머지 — MMS 로 처음 연락온 번호도 "오늘 신규"·목록에 잡히게. */
    private suspend fun syncMmsContacts() {
        val contacts = runCatching {
            container.smsRepository.queryRecentMmsContacts(mmsScanLimit = 120, contactLimit = 120)
        }.getOrDefault(emptyList())
        for (c in contacts) runCatching { container.smsContactCacheRepository.upsertOne(c) }
    }

    /** 시공 D-1 등 리마인더 — 주기 워커(~3시간) + 앱 켤 때 1회 즉시 점검. */
    private fun scheduleReminders() {
        runCatching {
            val wm = androidx.work.WorkManager.getInstance(this)
            val periodic = androidx.work.PeriodicWorkRequestBuilder<com.detailline.callfollowcrm.service.ReminderWorker>(
                3, java.util.concurrent.TimeUnit.HOURS
            ).build()
            wm.enqueueUniquePeriodicWork(
                "reminders", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, periodic
            )
            wm.enqueue(
                androidx.work.OneTimeWorkRequestBuilder<com.detailline.callfollowcrm.service.ReminderWorker>().build()
            )
        }
    }

    /**
     * OFFHOOK/RINGING → IDLE 전이 = 통화 종료 시그널.
     *   CallStateReceiver 와 동일 로직 (1.5초 대기 → CallLog 폴링 → Room INSERT).
     *   중복 INSERT 위험은 dao.countByPhoneAndStarted 가 차단.
     *
     * 권한: READ_PHONE_STATE (Manifest 박혀있음, 일반적으로 onboarding 시 grant).
     *   미부여 시 listen() 호출 자체는 안전하지만 idle 만 받음 → 의미 X. 그래서 권한 체크 후 등록.
     */
    private fun registerCallStateListener() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val tm = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ — TelephonyCallback (PhoneStateListener 는 deprecated).
            // ⚠️ TelephonyCallback 참조/생성은 반드시 SDK_INT >= S 인 별도 함수 안에서만.
            //   Application 클래스의 멤버 필드로 두면 Android 11 이하 ART verifier 가
            //   Application 인스턴스화 시점에 그 클래스를 미리 resolve 하다가
            //   NoClassDefFoundError 로 앱이 시작도 못 한다 (2026-05-28 S9/Android10 crash).
            registerTelephonyCallbackS(tm)
        } else {
            @Suppress("DEPRECATION")
            tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerTelephonyCallbackS(tm: TelephonyManager) {
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleCallState(state)
            }
        }
        try {
            tm.registerTelephonyCallback(mainExecutor, cb)
        } catch (_: SecurityException) {
            // 일부 OEM 정책 — silent skip
        }
    }

    // 직전 상태 — 전이 판정용. Volatile 안 써도 되지만 안전망.
    @Volatile private var lastCallState: Int = TelephonyManager.CALL_STATE_IDLE

    /** 통화 종료 시 호출 — CallStateReceiver 와 본질 동일. 중복 INSERT 는 dedup 이 차단. */
    private fun onCallEnded() {
        appScope.launch {
            // CallLog 가 비동기 작성 — 짧게 대기.
            delay(1500)
            val ctx = this@CallFollowCrmApplication
            val recent = CallLogHelper.queryLatest(ctx) ?: return@launch
            val phone = recent.phoneNumber.ifBlank { return@launch }
            // dedup — 이미 BroadcastReceiver 가 박았으면 syncFromCallLog 가 0 반환 (skip).
            // 그 외엔 새 row INSERT → Room observe 가 자동 emit → HomeScreen 갱신.
            runCatching { container.callRecordRepository.syncFromCallLog(ctx, phone) }
        }
    }

    @Suppress("DEPRECATION")
    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            handleCallState(state)
        }
    }

    private fun handleCallState(state: Int) {
        val prev = lastCallState
        lastCallState = state
        val ended = state == TelephonyManager.CALL_STATE_IDLE &&
            (prev == TelephonyManager.CALL_STATE_OFFHOOK || prev == TelephonyManager.CALL_STATE_RINGING)
        if (ended) onCallEnded()
    }
}
