package com.detailline.callfollowcrm.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * 통화 스크리닝 서비스 — 새 안드로이드(10+)에서 벨 울릴 때 "수신번호"를 얻어 상대 정보 카드(오버레이)를 띄우기 위함.
 *   (2026-08-27 사장님)
 *
 * 왜 필요:
 *   - 일반 BroadcastReceiver(CallStateReceiver)는 Android 10+ 에서 EXTRA_INCOMING_NUMBER 가 null → 벨 중 번호를 못 얻는다.
 *   - CallScreeningService / 기본전화앱 만 벨 울리는 순간 번호에 접근 가능. 그래서 이 서비스로 번호를 확보한다.
 *
 * 동작(중요):
 *   - onScreenCall 에서 번호만 살짝 엿봐 [IncomingCallOverlay.onRinging] 으로 카드를 띄운다.
 *   - **전화는 그대로 통과(allow)** — 아무것도 막거나 무음처리하지 않는다. 스팸 차단은 계속 기본전화앱(T전화 등)이 담당.
 *     우리는 "정보만 얹는" 역할.
 *   - 옛 안드로이드(≤9)는 CallStateReceiver 가 이미 번호를 받으므로 이 서비스가 중복 호출돼도 onRinging 이 같은 번호는 무시(dedup).
 *
 * 호출 조건: 앱이 ROLE_CALL_SCREENING 을 보유해야 시스템이 이 서비스를 호출. 미보유면 호출되지 않음(무해).
 */
class IncomingCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // 수신 통화만 대상. (API 29+ 는 발신에도 호출되므로 방향 확인. API<29 는 수신만 호출됨.)
        val isIncoming = if (Build.VERSION.SDK_INT >= 29) {
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        } else true

        val number: String? = runCatching {
            callDetails.handle?.schemeSpecificPart   // tel:01012345678 → 01012345678
        }.getOrNull()

        if (isIncoming && !number.isNullOrBlank()) {
            Log.d(TAG, "onScreenCall incoming numLen=${number.length}")
            runCatching {
                // 즉시 테두리 표시 — onScreenCall 실행 중 = '특권' 순간이라 백그라운드여도 오버레이 허용됨.
                IncomingCallOverlay.onRinging(applicationContext, number)
                // T전화 통화화면이 뜰 시간을 주고(≈0.85s), onScreenCall 이 살아있는(특권) 동안 위로 재장착 → 안 가림.
                //   이 서비스는 바인더 스레드라 sleep 해도 앱 ANR 아님(스크리닝 응답만 잠깐 미룸). (2026-08-31 최신폰 대응)
                Thread.sleep(850)
                IncomingCallOverlay.bringToFront(applicationContext)
                Thread.sleep(150)   // 재장착(main) 완료 후 통화 통과
            }.onFailure { Log.w(TAG, "overlay fail", it) }
        }

        // 어떤 경우에도 전화를 막거나 무음/알림차단 하지 않는다 — 기본값(빈 응답) = 그대로 통과.
        runCatching { respondToCall(callDetails, CallResponse.Builder().build()) }
    }

    companion object { private const val TAG = "CallScreeningSvc" }
}
