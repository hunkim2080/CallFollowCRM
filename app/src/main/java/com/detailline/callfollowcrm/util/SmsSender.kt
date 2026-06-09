package com.detailline.callfollowcrm.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.service.MmsSentReceiver
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import kotlinx.coroutines.launch

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

        val sent = runCatching {
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

        if (sent) {
            // 2026-05-30 사장님 #1 통점 fix:
            //   default SMS 앱이 되면 시스템 SMS provider 의 content://sms/sent 에 INSERT 책임이 우리한테.
            //   옛날엔 갤메시지 (기본 앱) 가 했음. 우리가 default 인데 INSERT 안 하면:
            //     - ChatScreen.loadMessages 가 querySmsOnly 로 시스템 provider query → 안 보임
            //     - cached_messages 도 빈 query 결과 저장 → 영영 사라짐
            //     - 갤메시지/다른 SMS 앱도 못 봄 (모두 시스템 provider 읽음)
            //   해결: 발송 성공 직후 Sent 테이블 INSERT.
            //   default 아닐 때는 WRITE_SMS 권한 없어 silent fail (RuntimeException) — runCatching 안전망.
            val insertedToProvider = insertIntoSentProvider(context, phoneNumber, body)

            // 2026-06-04 사장님 "보낸 문자가 간혹 안 보임" fix:
            //   RING-GO 가 기본 문자앱이 아니면 위 provider INSERT 가 실패(= 시스템 문자함에 기록 안 됨).
            //   그러면 채팅 재진입 시 loadMessages 가 provider 만 읽어 그 발신이 사라진다.
            //   → 기록 실패 시 우리 로컬 캐시(cached_messages, systemId<0)에 보존해 화면에서 유지.
            //   (기본앱이면 insertedToProvider=true → provider 가 정본이라 로컬 보존 불필요 → 중복 방지.)
            if (!insertedToProvider) {
                persistToLocalCache(context, phoneNumber, body)
            }
        }

        return sent
    }

    /**
     * 시스템 SMS provider (content://sms/sent) 에 사장님 발송 기록 INSERT.
     * Default SMS 앱일 때만 성공. 갤메시지가 default 면 갤메시지가 INSERT 책임 — silent fail OK.
     * @return 실제로 row 가 INSERT 됐으면 true (= 기본 문자앱). 실패/비기본앱이면 false.
     */
    private fun insertIntoSentProvider(context: Context, phoneNumber: String, body: String): Boolean {
        return runCatching {
            val nowMs = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, phoneNumber)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, nowMs)
                put(Telephony.Sms.DATE_SENT, nowMs)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values) != null
        }.getOrElse { e ->
            // default 아니거나 WRITE_SMS 거부 — 정상 케이스 (갤메시지가 default 책임).
            Log.w("SmsSender", "Sent provider INSERT failed (likely not default SMS app)", e)
            false
        }
    }

    /**
     * 발송한 메시지를 앱 로컬 캐시에 보존 (기본 문자앱이 아니라 시스템 문자함에 기록 못 했을 때).
     * fire-and-forget — 앱 수명 IO 스코프에서 비동기 저장. 모든 sendDirect 경로가 공통으로 탄다.
     */
    private fun persistToLocalCache(context: Context, phoneNumber: String, body: String) {
        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        val digits = phoneNumber.filter { it.isDigit() }
        val suffix = if (digits.length >= 8) digits.takeLast(8) else digits
        if (suffix.length < 7) return
        val nowMs = System.currentTimeMillis()
        val msg = com.detailline.callfollowcrm.data.repository.SmsRepository.SmsMessage(
            id = -nowMs,          // 음수 = 로컬 보존(시스템 provider 미반영) 표식
            address = phoneNumber,
            body = body,
            dateMs = nowMs,
            sent = true
        )
        app.applicationScope.launch {
            runCatching { app.container.cachedMessageRepository.persistLocalSent(suffix, msg) }
        }
    }

    /**
     * 사진 첨부 메시지(MMS)를 우리 앱 안에서 직접 발송한다.
     *
     * 주의: Transaction.sendNewMessage 는 비동기라 이 반환값은 "발송 요청을 시스템/라이브러리에 넘겼다"는 뜻이다.
     * 실제 성공/실패는 MmsSentReceiver 로그(ACTION_MMS_SENT)로 확인한다. 실패하면 ChatScreen 이 기존처럼
     * 삼성 문자앱 작성 화면으로 fallback 한다.
     */
    fun sendMms(
        context: Context,
        phoneNumber: String,
        body: String,
        uris: List<Uri>
    ): Boolean {
        if (!hasPermission(context)) {
            Log.w(TAG, "sendMms blocked: SEND_SMS permission missing")
            return false
        }
        if (!isDefaultSmsApp(context)) {
            Log.w(TAG, "sendMms blocked: RING-GO is not the default SMS app")
            return false
        }
        if (phoneNumber.isBlank() || uris.isEmpty()) return false

        val app = context.applicationContext as? CallFollowCrmApplication
        val bitmaps = uris.mapNotNull { uri ->
            runCatching { decodeMmsBitmap(context, uri) }
                .onFailure { Log.e(TAG, "MMS image decode failed: $uri", it) }
                .getOrNull()
        }
        if (bitmaps.isEmpty()) return false

        return runCatching {
            Settings.setDebugLogging(true, TAG)
            val settings = Settings().apply {
                setUseSystemSending(true)
                setDeliveryReports(true)
                app?.container?.preferences?.manualMmscUrl?.let { setMmsc(it) }
                app?.container?.preferences?.manualMmscProxy?.let { setProxy(it) }
                app?.container?.preferences?.manualMmscPort
                    ?.takeIf { it > 0 }
                    ?.let { setPort(it.toString()) }
            }

            val message = Message(body, arrayOf(phoneNumber), bitmaps.toTypedArray()).apply {
                setSave(true)
                setImageNames(Array(bitmaps.size) { i -> "ringgo_${System.currentTimeMillis()}_$i.jpg" })
            }

            val sentIntent = Intent(context, MmsSentReceiver::class.java).apply {
                action = MmsSentReceiver.ACTION_MMS_SENT
                putExtra(MmsSentReceiver.EXTRA_PHONE, phoneNumber)
                putExtra(MmsSentReceiver.EXTRA_BODY_PREVIEW, body.take(80))
                putExtra(MmsSentReceiver.EXTRA_IMAGE_COUNT, bitmaps.size)
            }

            Transaction(context.applicationContext, settings)
                .setExplicitBroadcastForSentMms(sentIntent)
                .sendNewMessage(message, Transaction.NO_THREAD_ID)

            Log.i(TAG, "MMS send requested: to=$phoneNumber images=${bitmaps.size} body=${body.length}")
            true
        }.getOrElse { e ->
            Log.e(TAG, "MMS send request failed", e)
            false
        }
    }

    fun isDefaultSmsApp(context: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    private fun decodeMmsBitmap(context: Context, uri: Uri): Bitmap {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input) ?: error("BitmapFactory returned null")
            }
        }
        val maxSide = 1280
        val width = raw.width
        val height = raw.height
        val longer = maxOf(width, height)
        if (longer <= maxSide) return raw
        val scale = maxSide.toFloat() / longer.toFloat()
        return Bitmap.createScaledBitmap(
            raw,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true
        ).also {
            if (it !== raw) raw.recycle()
        }
    }

    private const val TAG = "SmsSender"
}
