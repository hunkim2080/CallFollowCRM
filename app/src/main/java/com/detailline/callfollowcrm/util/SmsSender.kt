package com.detailline.callfollowcrm.util

import android.Manifest
import android.app.PendingIntent
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
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.pdu_alt.SendReq
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

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
    fun sendDirect(context: Context, phoneNumber: String, body: String, persistLocalOnFail: Boolean = true): Boolean {
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
            //   persistLocalOnFail=false 면 호출부(채팅 VM)가 보존을 *동기*로 직접 함 → 비동기 보존 race·중복 방지.
            if (!insertedToProvider && persistLocalOnFail) {
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
     * 사진 첨부 메시지(MMS)를 우리 앱 안에서 직접 발송한다 — 안드로이드 **공식 API** 경로.
     *   (2026-07-08) 예전 klinker Transaction 경로가 기본앱 실전에서 사진 유실(6/17) → 수신 fix 와 동일 철학으로 재구현.
     *
     * 흐름: 이미지 JPEG 압축 → SendReq PDU 작성(com.google.android.mms.pdu_alt) → PduComposer 로 직렬화 →
     *   캐시파일 → content://…mmsdownload/…(읽기) → SmsManager.sendMultimediaMessage(완료 PendingIntent=MmsSentReceiver).
     *   추가로 발신함(Telephony.Mms.Sent)에 PduPersister 로 저장(best-effort) → 재실행 후에도 스레드에 남음("발신 사라짐" 방지).
     *
     * 반환값은 "발송 요청을 시스템에 넘겼다"는 뜻(비동기). 실제 성공/실패는 MmsSentReceiver(resultCode)로 확인.
     * 요청 자체가 실패하면 false → ChatScreen 이 기존처럼 삼성 문자앱 작성 화면으로 fallback.
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

        // 이미지 → 축소(1280) → JPEG 압축(장당 ~300KB 이하). 하나도 못 만들면 실패.
        val jpegs = uris.mapNotNull { uri ->
            runCatching { compressJpeg(decodeMmsBitmap(context, uri)) }
                .onFailure { Log.e(TAG, "MMS image decode/compress failed: $uri", it) }
                .getOrNull()
        }
        if (jpegs.isEmpty()) return false

        return runCatching {
            val sendReq = buildSendReq(phoneNumber, body, jpegs)
            val pdu = PduComposer(context, sendReq).make()
            if (pdu == null || pdu.isEmpty()) { Log.e(TAG, "PduComposer produced empty PDU"); return@runCatching false }

            // PDU 를 캐시파일에 쓰고 mmsdownload provider(읽기 모드)로 시스템에 넘긴다(AOSP MmsFileProvider 패턴).
            val fileName = "mms_send_${System.currentTimeMillis()}.pdu"
            File(context.cacheDir, fileName).writeBytes(pdu)
            val contentUri = Uri.parse("content://${context.packageName}.mmsdownload/$fileName")

            val sentIntent = Intent(context, MmsSentReceiver::class.java).apply {
                action = MmsSentReceiver.ACTION_MMS_SENT
                putExtra(MmsSentReceiver.EXTRA_PHONE, phoneNumber)
                putExtra(MmsSentReceiver.EXTRA_BODY_PREVIEW, body.take(80))
                putExtra(MmsSentReceiver.EXTRA_IMAGE_COUNT, jpegs.size)
                putExtra(MmsSentReceiver.EXTRA_SEND_FILE, fileName)   // 완료 후 임시 PDU 삭제용
            }
            val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val sentPI = PendingIntent.getBroadcast(
                context, (System.currentTimeMillis() and 0xffffff).toInt(), sentIntent, piFlags
            )

            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            smsManager.sendMultimediaMessage(context, contentUri, null, null, sentPI)

            // best-effort: 발신함에 저장(스레드 표시·durable). 실패해도 발송엔 영향 없음.
            runCatching {
                val subId = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) SmsManager.getDefaultSmsSubscriptionId() else -1
                } catch (e: Exception) { -1 }
                PduPersister.getPduPersister(context)
                    .persist(sendReq, Telephony.Mms.Sent.CONTENT_URI, true, true, null, subId)
            }.onFailure { Log.w(TAG, "sent-box persist failed (non-fatal)", it) }

            Log.i(TAG, "MMS (official) send requested: to=${LogRedact.phone(phoneNumber)} images=${jpegs.size} body=${body.length}")
            true
        }.getOrElse { e ->
            Log.e(TAG, "MMS (official) send request failed", e)
            false
        }
    }

    /** 발신 MMS 의 SendReq PDU 구성 — 수신자 + (본문 텍스트 파트) + 이미지 파트들. */
    private fun buildSendReq(phone: String, body: String, jpegs: List<ByteArray>): SendReq {
        val req = SendReq()
        req.addTo(EncodedStringValue(phone))
        req.date = System.currentTimeMillis() / 1000L
        val pduBody = PduBody()
        if (body.isNotBlank()) {
            val part = PduPart()
            part.charset = CharacterSets.UTF_8
            part.contentType = "text/plain".toByteArray()
            part.contentLocation = "text_0.txt".toByteArray()
            part.contentId = "text_0".toByteArray()
            part.data = body.toByteArray(Charsets.UTF_8)
            pduBody.addPart(part)
        }
        jpegs.forEachIndexed { i, bytes ->
            val part = PduPart()
            part.contentType = "image/jpeg".toByteArray()
            part.contentLocation = "image_$i.jpg".toByteArray()
            part.contentId = "image_$i".toByteArray()
            part.data = bytes
            pduBody.addPart(part)
        }
        req.body = pduBody
        return req
    }

    /** 비트맵 → JPEG 바이트. maxBytes 초과 시 품질을 낮춰 재압축(통신사 MMS 용량 한계 대응). */
    private fun compressJpeg(bitmap: Bitmap, maxBytes: Int = 300 * 1024): ByteArray {
        var quality = 85
        var out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > maxBytes && quality > 40) {
            quality -= 15
            out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return out.toByteArray()
    }

    // 기본 SMS 앱 판정은 RoleManager(ROLE_SMS) 기준 — Android 10+ 의 권위 소스.
    //   삼성 등 일부 기기는 ROLE_SMS 는 우리 앱인데도 legacy getDefaultSmsPackage()/secure 설정이 null 을
    //   반환해, 그걸로 검사하면 "기본앱 아님" 오판 → MMS 발송이 스스로 막히는 버그. (2026-07-08 S23U 확인)
    fun isDefaultSmsApp(context: Context): Boolean =
        DefaultSmsAppHelper.isCurrentDefault(context)

    private const val MMS_MAX_SIDE = 1280

    /** 2의 배수 sampleSize — 원본 긴 변이 목표의 2배 이상이면 절반씩 줄여 디코딩 부담↓. */
    private fun sampleSizeFor(longer: Int): Int {
        var s = 1
        while (longer > 0 && longer / (s * 2) >= MMS_MAX_SIDE) s *= 2
        return s
    }

    /**
     * MMS 첨부용 비트맵 로드 — **디코딩 단계에서 바로 다운샘플링**해 원본 해상도를 통째로 메모리에 안 올린다.
     *   (예전엔 4000×3000 원본을 ~48MB 로 먼저 디코딩 후 축소 → 저사양 폰 OOM 위험. Play 콘솔 '비트맵 최적화' 권장.)
     *   sampleSize(2배수) 로 대략 축소 후, 남은 초과분만 createScaledBitmap 으로 정확히 MMS_MAX_SIDE 에 맞춘다.
     */
    private fun decodeMmsBitmap(context: Context, uri: Uri): Bitmap {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = sampleSizeFor(maxOf(info.size.width, info.size.height))
                if (s > 1) decoder.setTargetSampleSize(s)
            }
        } else {
            // API 26~27 — 크기만 먼저 읽어(inJustDecodeBounds) inSampleSize 로 축소 디코딩.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight)) }
            context.contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input, null, opts) ?: error("BitmapFactory returned null")
            }
        }
        val longer = maxOf(raw.width, raw.height)
        if (longer <= MMS_MAX_SIDE) return raw
        val scale = MMS_MAX_SIDE.toFloat() / longer.toFloat()
        return Bitmap.createScaledBitmap(
            raw,
            (raw.width * scale).toInt().coerceAtLeast(1),
            (raw.height * scale).toInt().coerceAtLeast(1),
            true
        ).also {
            if (it !== raw) raw.recycle()
        }
    }

    private const val TAG = "SmsSender"
}
