package com.detailline.callfollowcrm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "WAP_PUSH_DELIVER received — forwarding to MmsDownloadService")
        // 2026-05-30 사장님 ANR 보고 fix: startService 가 binder IPC 동기 호출이라 main thread block 위험.
        //   broadcast scope 의 main thread 부분을 최소화 — Intent build + startService 둘 다 background.
        val pending = goAsync()
        scope.launch {
            try {
                val service = Intent(context, MmsDownloadService::class.java).apply {
                    action = intent.action
                    intent.extras?.let { putExtras(it) }
                    intent.type?.let { type = it }
                }
                runCatching { context.startService(service) }
                    .onFailure { e ->
                        // Android O+ 에서 background service start 제약 — IntentService 라면 정상 동작이지만,
                        //   사용자가 doze/background 제한 강하게 걸어둔 경우 거부 가능.
                        Log.e(TAG, "startService(MmsDownloadService) failed", e)
                    }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "MmsReceiver"
    }
}
