package com.detailline.callfollowcrm.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction

/**
 * SMS 직접 발송 유틸. 정책상 발송이 허용된 경로 (첫 응대 자동 응답, 고객 상세 인라인 채팅)
 * 두 군데에서 공통 사용.
 *
 * - 본문이 길면 자동으로 multipart 분할
 * - throw 하지 않음 (Boolean 반환)
 * - 권한이 없으면 false
 */
object SmsSender {

    fun hasPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.SEND_SMS
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * @return 발송 호출 성공 여부 (= SmsManager 까지 안전하게 전달). 실제 통신사 전달 성공은
     *         별도 PendingIntent 콜백이 필요하지만, 이 앱은 UX 안 막기 위해 fire-and-forget.
     */
    fun sendDirect(context: Context, phoneNumber: String, body: String): Boolean {
        if (!hasPermission(context)) return false
        if (phoneNumber.isBlank() || body.isBlank()) return false

        return runCatching {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            } ?: return false

            val parts = sms.divideMessage(body)
            if (parts.size <= 1) {
                sms.sendTextMessage(phoneNumber, null, body, null, null)
            } else {
                sms.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            true
        }.getOrDefault(false)
    }

    /**
     * 사진 첨부 메시지(MMS) 를 우리 앱 안에서 직접 발송 — 현재 비활성 (2026-05-19 보류).
     *
     * 시도 이력:
     *  - klinker41/android-smsmms 5.2.5 통합 → sendNewMessage 가 fire-and-forget 이라 실제 발송 실패해도
     *    success 로 잘못 판정. S9/SKT 조합에서 실제 통신사 전달 안 됨 확인.
     *  - 결과 broadcast receiver 등록 + PendingIntent 콜백 처리 = 다음 세션 작업.
     *  - 또는 자체 PDU + SmsManager.sendMultimediaMessage 구현이 더 견고할 수 있음.
     *
     * 본 메서드는 항상 false → ChatScreen 이 자동으로 SmsIntentHelper 갤럭시 메시지 fallback 으로 빠짐
     * (수신인 + 본문 + 사진 채워서 갤럭시 메시지 열림 → 사장님이 거기서 ▶).
     */
    @Suppress("UNUSED_PARAMETER")
    fun sendMms(
        context: Context,
        phoneNumber: String,
        body: String,
        uris: List<Uri>
    ): Boolean = false
}
