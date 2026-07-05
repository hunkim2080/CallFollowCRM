package com.detailline.callfollowcrm.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.PduParser
import java.io.File

/**
 * 2026-07-05 — 기본 문자앱일 때 MMS(사진) 수신 1단계: WAP_PUSH 알림 → **안드로이드 공식 다운로드** 시작.
 *
 * ⚠️ 왜 klinker MmsReceivedReceiver 를 안 쓰나 (로그로 확진 2026-07-05):
 *    klinker 5.2.5 는 자체 HTTP(APN) 로 다운로드 후 완료 콜백에서 `file_path` 가 null → `Log.v(null)` NPE 크래시.
 *    (사장님이 "통신사 한계" 로 오인해 기본앱 철회했던 진짜 원인 = 라이브러리 버그.)
 * ✅ 대신 `SmsManager.downloadMultimediaMessage` — **폰(삼성) 자체 MMS 스택**으로 다운로드
 *    (klinker 재구현 HTTP 보다 한국 통신사에서 안정적). 다운로드 완료본(PDU)은 우리 임시 provider 파일에 저장되고
 *    → MmsDownloadedReceiver 가 파싱·persist. (구글 pdu_alt 클래스 직접 사용)
 *
 * 흐름: WAP_PUSH_DELIVER(data=알림PDU) → PduParser → NotificationInd.contentLocation(다운로드 URL)
 *       → downloadMultimediaMessage(url, contentUri) → 완료 시 MmsDownloadedReceiver 로 PendingIntent.
 */
class MmsReceived : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WAP_PUSH_DELIVER) return
        val pushData = intent.getByteArrayExtra("data")
        if (pushData == null) {
            Log.w(TAG, "WAP_PUSH with no 'data' PDU — abort"); notifyFail(context); return
        }

        val notif = runCatching { PduParser(pushData).parse() as? NotificationInd }.getOrNull()
        val locationBytes = notif?.contentLocation
        if (notif == null || locationBytes == null) {
            Log.w(TAG, "notification PDU parse failed / no content-location"); notifyFail(context); return
        }
        val locationUrl = String(locationBytes).trim()
        Log.i(TAG, "WAP_PUSH received — MMS pending at [$locationUrl], starting platform download")

        // 다운로드 결과(PDU)를 받을 임시 파일 + content URI. 자체 provider 라 SmsManager 가 앱 프로세스에서
        //   FD 를 열어 phone 서비스에 넘김 → cross-process 권한 문제 없음(AOSP Messaging 패턴).
        val fileName = "mmsdl_${System.nanoTime()}.pdu"
        runCatching { File(context.cacheDir, fileName).delete() }
        val contentUri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(context.packageName + PROVIDER_SUFFIX)
            .path(fileName)
            .build()

        val subId = defaultSubId()
        val completion = Intent(context, MmsDownloadedReceiver::class.java).apply {
            putExtra(MmsDownloadedReceiver.EXTRA_FILE_NAME, fileName)
            putExtra(MmsDownloadedReceiver.EXTRA_LOCATION_URL, locationUrl)
            putExtra(MmsDownloadedReceiver.EXTRA_SUB_ID, subId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getBroadcast(
            context, (System.nanoTime() and 0x7fffffffL).toInt(), completion, flags
        )

        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        if (sms == null) { Log.e(TAG, "SmsManager null"); notifyFail(context); return }

        runCatching {
            sms.downloadMultimediaMessage(context, locationUrl, contentUri, null, pi)
            Log.i(TAG, "downloadMultimediaMessage dispatched (subId=$subId)")
        }.onFailure {
            Log.e(TAG, "downloadMultimediaMessage failed to start", it); notifyFail(context)
        }
    }

    private fun defaultSubId(): Int = runCatching {
        @Suppress("DEPRECATION")
        SmsManager.getDefaultSmsSubscriptionId()
    }.getOrDefault(-1)

    private fun notifyFail(context: Context) {
        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        if (app.container.preferences.incomingSmsNotifyEnabled) {
            runCatching { NotificationHelper.showMmsReceiveFailed(app, senderHint = null) }
        }
    }

    companion object {
        private const val TAG = "MmsReceived"
        const val WAP_PUSH_DELIVER = "android.provider.Telephony.WAP_PUSH_DELIVER"
        /** MmsDownloadFileProvider authority = "${'$'}{applicationId}.mmsdownload" */
        const val PROVIDER_SUFFIX = ".mmsdownload"
    }
}
