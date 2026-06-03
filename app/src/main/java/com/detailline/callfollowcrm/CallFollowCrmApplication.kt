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
import kotlinx.coroutines.launch

class CallFollowCrmApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                delay(60_000)
            }
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
