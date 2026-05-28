package com.detailline.callfollowcrm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 2026-05-29 Phase A 2단계 — MMS WAP_PUSH receiver.
 *
 * Default SMS 앱 자격 (4/4) 충족 + MMS 수신 트리거.
 * **사장님이 Settings 토글 켜야 호출됨** — 1단계 토글 disabled 상태에서는 호출 X (안전).
 *
 * 흐름:
 *   1. 통신사 → SMSC → 폰: M-Notification.ind (WAP_PUSH) 도착
 *   2. 시스템이 WAP_PUSH_DELIVER broadcast 발사 (default SMS 앱만 받음)
 *   3. 이 receiver 가 받음 → MmsDownloadService 에 intent forward (extras 통째)
 *   4. MmsDownloadService 가 klinker MmsReceivedService 위임 → PDU parse + MMSC HTTP GET + DB persist
 *   5. (다음 세션) MmsDownloadService 가 알림 + prepare-reply 트리거
 *
 * **broadcast scope 안에서 download 안 함** — MMSC HTTP GET 이 수 초~수십 초 걸려서 goAsync 한도 초과.
 *   IntentService 로 위임 = 안드로이드 표준 패턴.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "WAP_PUSH_DELIVER received — forwarding to MmsDownloadService")
        // intent 의 byteArray("data") 가 WAP_PUSH PDU 본체. klinker 가 알아서 파싱.
        // setClass + putExtras 로 전부 전달.
        val service = Intent(context, MmsDownloadService::class.java).apply {
            // klinker MmsReceivedService 가 intent.action 도 검사할 수 있어 보존.
            action = intent.action
            // EXTRA_DATA (raw PDU bytes), EXTRA_SUBSCRIPTION (SIM 슬롯) 등.
            intent.extras?.let { putExtras(it) }
            // mimeType 보존 — klinker 가 application/vnd.wap.mms-message 검증할 수 있음.
            intent.type?.let { type = it }
        }
        runCatching { context.startService(service) }
            .onFailure { e ->
                // Android O+ 에서 background service start 제약 — IntentService 라면 정상 동작이지만,
                //   사용자가 doze/background 제한 강하게 걸어둔 경우 거부 가능.
                //   foreground service 로 격상해야 할 수도 — 다음 세션에 안정성 확인.
                Log.e(TAG, "startService(MmsDownloadService) failed", e)
            }
    }

    companion object {
        private const val TAG = "MmsReceiver"
    }
}
