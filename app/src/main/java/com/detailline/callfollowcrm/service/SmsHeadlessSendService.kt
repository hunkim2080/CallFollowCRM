package com.detailline.callfollowcrm.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * 2026-05-29 Phase A 1단계 — RESPOND_VIA_MESSAGE 헤드리스 서비스 stub.
 *
 * Default SMS 앱 자격 요건 (3/4) 충족용. 시스템/자동차/스마트워치 가 "Can't talk, I'll text" 같은
 * 빠른 응답 보낼 때 호출. 사장님은 직접 RING-GO 안에서 답장하므로 1단계는 무동작.
 *
 * **이 서비스 자체는 1단계 동안 거의 호출 안 됨** — Settings 토글이 disabled 라 default 가 아님.
 * Default 가 됐어도 사장님이 자동차/시계 빠른 답장을 안 쓰면 호출 X.
 *
 * 추후 (선택) — 자동차/시계 빠른 답장에도 우리 SmsManager 발송 로직 연결 가능.
 *
 * Manifest: SEND_RESPOND_VIA_MESSAGE permission + RESPOND_VIA_MESSAGE action + sms/smsto/mms/mmsto schemes.
 */
class SmsHeadlessSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "RESPOND_VIA_MESSAGE invoked (Phase A 1단계 stub — no handler)")
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "SmsHeadlessSendService"
    }
}
