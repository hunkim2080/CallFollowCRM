package com.detailline.callfollowcrm.service

import android.content.Intent
import android.util.Log
import com.klinker.android.send_message.MmsReceivedService

/**
 * 2026-05-29 Phase A 2단계 — MMS 다운로드 서비스 (klinker wrap + 우리 hook).
 *
 * 흐름:
 *   1. MmsReceiver.onReceive(WAP_PUSH_DELIVER) → 이 서비스로 forward (intent extras 통째 전달)
 *   2. super.onHandleIntent(intent) — klinker 가 알아서:
 *      a. PDU 파싱 (M-Notification.ind) — `com.google.android.mms.pdu_alt.PduParser`
 *      b. APN 자동 추출 — `com.android.mms.service_alt.MmsConfigManager`
 *      c. ConnectivityManager.requestNetwork(TYPE_MOBILE_MMS) — mobile data MMS 채널 강제 활성화
 *      d. MMSC HTTP GET — `com.android.mms.transaction.RetrieveTransaction.run()`
 *      e. M-Retrieve.conf 파싱 + content://mms 와 content://mms/part INSERT — `PduPersister`
 *   3. super 반환 후 우리 hook:
 *      - NotificationHelper.showIncomingMms() (다음 세션 — 본격 알림 UI)
 *      - PrepareContext 구성 + suggestionRepository.requestPrepare() (다음 세션)
 *
 * **현재 (Phase A 2단계 Day 1~3) — super 호출만 + 우리 hook 은 placeholder.**
 *   사장님이 default SMS 토글 켜기 전까지는 호출 자체 안 됨 (1단계 토글 disabled).
 *   다음 세션 (Day 4~5): 알림 / prepare-reply / 첨부 처리 본격.
 *
 * Manifest 등록: 일반 Service. exported=false (broadcast receiver 만 trigger).
 */
class MmsDownloadService : MmsReceivedService() {

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) {
            Log.w(TAG, "onHandleIntent: null intent — skipping")
            return
        }
        Log.i(TAG, "MMS download start — delegating to klinker MmsReceivedService")

        // klinker 가 전체 download + persist 처리.
        //   실패 시 super 가 throw 안 함 (silent return, 시스템 mms 테이블에 ERROR row 박힘).
        //   APN 자동 추출 + ConnectivityManager + RetrieveTransaction 전부 wrap.
        runCatching { super.onHandleIntent(intent) }
            .onFailure { e ->
                // klinker 가 던지는 경우 거의 없음 — 만약 던지면 로그만 남기고 swallow.
                //   사용자 입장에서 메시지 못 받았다는 거 알아채야 하지만 1단계 stub 이라 silent.
                Log.e(TAG, "klinker MmsReceivedService threw", e)
            }

        // ─────────────────────────────────────────────────────────────────────────
        // 다음 세션 (Day 4~5) — 우리 hook:
        //   1. content://mms/inbox 의 가장 최근 row 조회 → mms.id 추출
        //   2. content://mms/{id}/addr 에서 sender 추출 (type=137 = from)
        //   3. content://mms/part where mid={id} 에서 text 본문 + 첨부 파트 추출
        //   4. NotificationHelper.showIncomingMms(context, sender, displayName, body, attachments)
        //   5. PrepareContext 구성 (latestMessage = body + "[첨부 사진 N장]" 안내)
        //   6. suggestionRepository.requestPrepare(ctx)
        //   7. 향후 — 첨부 사진을 서버 Vision 으로 보내서 견적/주소/평수 자동 추출
        // ─────────────────────────────────────────────────────────────────────────
        Log.i(TAG, "MMS download complete (Phase A 2단계 — hook placeholder, 다음 세션에 본격 구현)")
    }

    companion object {
        private const val TAG = "MmsDownloadService"
    }
}
