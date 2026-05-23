package com.detailline.callfollowcrm.service

import android.content.BroadcastReceiver
import android.content.BroadcastReceiver.PendingResult
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.domain.model.CallType
import com.detailline.callfollowcrm.util.CallLogHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 통화 상태 변경 BroadcastReceiver.
 *
 * 동작 정책:
 *  - OFFHOOK(통화 중) → IDLE(종료) 전이를 통화 종료로 본다.
 *  - IDLE 직후 CallLog 가 비어있을 수 있어 1초 ~ 1.5초 정도 약하게 지연 후 조회한다.
 *  - 권한이 없거나 row 가 없으면 phoneNumber=null 알림으로 fallback (수동 입력).
 *
 * Android 버전 제약 (주석):
 *  - Android 10+ 부터 EXTRA_INCOMING_NUMBER 는 일반 앱에 차단됨 → 우리는 CallLog 만 사용.
 *  - Android 11+ 패키지 가시성 정책으로 일부 OEM은 PHONE_STATE 브로드캐스트 신호가 늦거나 누락.
 *  - 일부 OEM(샤오미/오포 등)은 백그라운드 자동시작 허용이 꺼져 있으면 정적 receiver 가 호출되지 않는다.
 *    → 그 경우 사용자에게 "자동시작 허용" 또는 "배터리 최적화 제외" 안내가 필요. (Settings 화면에 표기)
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        val prev = lastState
        lastState = state

        // 통화 종료 시점 판정:
        //  - OFFHOOK → IDLE: 일반 통화 종료 (수신 응답 후 끊음, 발신 후 끊음)
        //  - RINGING → IDLE: 부재중 (응답 안 함). 이 경우 CallLog type 이 MISSED 로 남는다.
        //    응답을 매우 빨리 해서 OFFHOOK 전이를 놓친 경우도 RINGING → IDLE 로 보이지만,
        //    CallLog 의 type 을 신뢰해서 INCOMING 으로 처리하면 됨.
        val callEnded = state == TelephonyManager.EXTRA_STATE_IDLE &&
            (prev == TelephonyManager.EXTRA_STATE_OFFHOOK || prev == TelephonyManager.EXTRA_STATE_RINGING)
        if (callEnded) {
            val app = context.applicationContext as? CallFollowCrmApplication ?: return
            // Kotlin은 슈퍼클래스의 nested type을 unqualified로 해석하지 않으므로 BroadcastReceiver.PendingResult 로 명시한다.
            val pendingResult: BroadcastReceiver.PendingResult? = try { goAsync() } catch (_: Throwable) { null }

            scope.launch {
                try {
                    // 일부 기기에서 CallLog row가 비동기로 작성되므로 짧게 대기.
                    delay(1500)
                    val recent = CallLogHelper.queryLatest(context)
                    val newRecordId: Long = if (recent != null) {
                        app.container.callRecordRepository.create(
                            phoneNumber = recent.phoneNumber.ifBlank { "(번호없음)" },
                            callType = recent.type,
                            duration = recent.duration,
                            startedAt = recent.date,
                            endedAt = System.currentTimeMillis()
                        )
                    } else {
                        // 권한 없거나 못 가져왔을 때도 끊김 자체는 기록한다.
                        app.container.callRecordRepository.create(
                            phoneNumber = "(번호없음)",
                            callType = CallType.UNKNOWN,
                            duration = 0,
                            startedAt = null,
                            endedAt = System.currentTimeMillis()
                        )
                    }
                    // 정책 (2026-05-19):
                    //  - 첫 통화: 카드/알림 강하게 (dispatchFirstCallUi)
                    //  - 2번째+ 통화: 사장님이 답장/분류 안 했으면 조용한 알림. 처리됐으면 X.
                    val phone = recent?.phoneNumber
                    val phoneOk = phone != null && phone.isNotBlank() && phone != "(번호없음)"
                    if (phoneOk) {
                        val callCount = app.container.callRecordRepository.countByPhone(phone!!)
                        if (callCount == 1) {
                            dispatchFirstCallUi(
                                context = context,
                                app = app,
                                newRecordId = newRecordId,
                                phoneNumber = phone,
                                callType = recent!!.type
                            )
                        } else {
                            dispatchRepeatCallUi(
                                context = context,
                                app = app,
                                newRecordId = newRecordId,
                                phoneNumber = phone,
                                callType = recent!!.type
                            )
                        }
                    }
                } catch (_: Throwable) {
                    // 어떠한 경우에도 절대 throw 하지 않는다.
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }

    /**
     * 사장님이 Settings 에서 지정한 후속 빠른 액션 템플릿 3개를 (라벨, id) 쌍 리스트로 반환.
     * 미지정(-1) 슬롯은 건너뜀. 알림 시스템 한도(3개)에 맞춰 최대 3개.
     */
    private suspend fun buildQuickActions(
        app: CallFollowCrmApplication
    ): List<Pair<String, Long>> {
        val ids = listOf(
            app.container.preferences.quickActionTemplateId1,
            app.container.preferences.quickActionTemplateId2,
            app.container.preferences.quickActionTemplateId3
        ).filter { it > 0 }
        if (ids.isEmpty()) return emptyList()
        return ids.mapNotNull { id ->
            val t = app.container.messageTemplateRepository.findById(id) ?: return@mapNotNull null
            t.title to t.id
        }.take(3)
    }

    /**
     * 첫 통화 UI 디스패치.
     *
     * 우선순위:
     *  1) SYSTEM_ALERT_WINDOW 권한 OK → PostCallCard 오버레이 (auto-reply ON/OFF 모드 자동 분기)
     *  2) 오버레이 권한 없음 + auto-reply 정책 + 권한 OK → 기존 AutoReplyScheduler 알림 (fallback)
     *  3) 둘 다 안 됨 → 기존 후속 처리 알림 (수동 처리용)
     *
     * 통화 타입 필터:
     *  - INCOMING / MISSED 만 카드/자동응답 대상. OUTGOING/REJECTED 는 카드는 안 띄우고 기록만 남김.
     */
    private suspend fun dispatchFirstCallUi(
        context: Context,
        app: CallFollowCrmApplication,
        newRecordId: Long,
        phoneNumber: String,
        callType: CallType
    ) {
        val isMissed = when (callType) {
            CallType.INCOMING -> false
            CallType.MISSED -> true
            else -> {
                // OUTGOING/REJECTED/UNKNOWN — 카드/자동응답 흐름엔 안 맞음.
                // 사장님이 본인이 건 전화라도 첫 접촉이면 후속 처리는 필요하므로 기존 알림은 띄움.
                val quickActions = buildQuickActions(app)
                NotificationHelper.showCallEndedNotification(
                    context = context,
                    phoneNumber = phoneNumber,
                    callRecordId = newRecordId,
                    isMissed = false,
                    quickActions = quickActions
                )
                return
            }
        }

        val prefs = app.container.preferences
        val autoOnPolicy = prefs.autoFirstReplyEnabled
        val autoTemplateId = if (isMissed) prefs.firstReplyMissedTemplateId else prefs.firstReplyIncomingTemplateId
        val autoTemplate = if (autoOnPolicy && autoTemplateId > 0) {
            app.container.messageTemplateRepository.findById(autoTemplateId)
        } else null

        // OverlayArgs 에 넣을 수동 템플릿: 사장님이 설정한 quickAction 슬롯 3개.
        val manualTemplates = listOf(
            prefs.quickActionTemplateId1,
            prefs.quickActionTemplateId2,
            prefs.quickActionTemplateId3
        )
            .filter { it > 0 }
            .mapNotNull { app.container.messageTemplateRepository.findById(it) }
            .take(3)

        val tryOverlay = PostCallOverlayManager.showOrIgnore(
            context = context,
            args = OverlayArgs(
                callRecordId = newRecordId,
                phoneNumber = phoneNumber,
                isMissed = isMissed,
                autoReplyTemplateId = autoTemplate?.id,
                autoReplyTemplateTitle = autoTemplate?.title,
                autoReplyTemplateBody = autoTemplate?.body,
                manualTemplates = manualTemplates
            )
        )
        if (tryOverlay) return

        // ----- fallback paths (오버레이 권한 없거나 띄우기 실패) -----
        if (autoOnPolicy && autoTemplate != null) {
            // 기존 AutoReplyScheduler 흐름 (알림 카운트다운)
            AutoReplyScheduler.schedule(
                context = context,
                callRecordId = newRecordId,
                phoneNumber = phoneNumber,
                isMissed = isMissed
            )
        } else {
            // 마지막 fallback — 수동 처리용 알림
            val quickActions = buildQuickActions(app)
            NotificationHelper.showCallEndedNotification(
                context = context,
                phoneNumber = phoneNumber,
                callRecordId = newRecordId,
                isMissed = isMissed,
                quickActions = quickActions
            )
        }
    }

    /**
     * 2번째 이후 통화의 UI 분기:
     *  - 사장님이 이미 답장(INLINE_SENT/AUTO_SENT/MANUAL_MARK_SENT/DRAFT_OPENED)을 했거나
     *    leadHeat 분류를 했으면 = "처리됨" → 아무 알림 X
     *  - 그렇지 않으면 = "미처리 재통화" → 조용한 알림 (헤드업 X, 알림함만)
     *
     * 통화 타입 필터: INCOMING/MISSED 만 (OUTGOING 은 사장님이 본인이 걸었으니 알림 의미 X)
     */
    private suspend fun dispatchRepeatCallUi(
        context: Context,
        app: CallFollowCrmApplication,
        newRecordId: Long,
        phoneNumber: String,
        callType: CallType
    ) {
        val isMissed = when (callType) {
            CallType.INCOMING -> false
            CallType.MISSED -> true
            else -> return
        }
        val customer = app.container.customerRepository.findByPhone(phoneNumber)
        val classified = customer?.leadHeat != null
        val replied = app.container.messageHistoryRepository.hasHandledRecord(phoneNumber)
        if (classified || replied) return  // 처리됨 — 알림 X

        NotificationHelper.showQuietFollowUpNotification(
            context = context,
            callRecordId = newRecordId,
            phoneNumber = phoneNumber,
            isMissed = isMissed,
            customerId = customer?.id
        )
    }

    companion object {
        // BroadcastReceiver 인스턴스는 매번 새로 생성되므로 static 으로 직전 상태를 유지.
        @Volatile private var lastState: String = TelephonyManager.EXTRA_STATE_IDLE
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
