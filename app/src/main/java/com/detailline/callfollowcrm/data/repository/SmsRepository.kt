package com.detailline.callfollowcrm.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * 갤럭시 메시지(또는 다른 기본 SMS 앱)가 시스템 SMS 프로바이더에 적재한
 * 받은 문자만 읽어온다. SEND/WRITE 는 절대 하지 않는다.
 *
 * 권한:
 *  - [Manifest.permission.READ_SMS] 가 있어야만 동작. 없으면 빈 리스트.
 *  - 권한 요청은 SettingsScreen 의 토글에서 사용자 명시 액션으로만.
 *
 * 매칭 전략:
 *  - SMS 프로바이더의 address 컬럼은 표기가 다양("010-1234-5678", "+82 10-1234-5678", "01012345678").
 *  - SQL 단의 LIKE 만으로는 신뢰가 어려워, 최근 N건을 가져와 Kotlin 에서 숫자만 추출 후
 *    끝 8자리 일치를 본다 (Korean 휴대폰은 끝 8자리가 사실상 unique).
 *  - 한 고객 상세 화면에서만 호출되므로 비용 미미.
 */
class SmsRepository(private val context: Context) {

    /**
     * 시스템 SMS / MMS 한 건. direction 으로 송/수신 구분.
     * MMS 의 경우 body 는 텍스트 파트 모두 합친 것, imageUris 는 첨부 이미지 part URI 들.
     * SMS 는 imageUris = empty.
     */
    data class SmsMessage(
        val id: Long,
        val address: String?,
        val body: String,
        val dateMs: Long,
        val sent: Boolean,
        val imageUris: List<Uri> = emptyList()
    )

    fun hasReadPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * 특정 전화번호와 주고받은 시스템 SMS 를 모두 가져온다 (수신 + 발신).
     *
     * 통합 URI `content://sms/` 를 쿼리하고 `type` 컬럼으로 방향 구분:
     *  - 1 = inbox (받음)
     *  - 2 = sent  (보냄)
     *  - 그 외(초안/실패 등)는 무시.
     *
     * @param phoneNumber 매칭할 전화번호. 숫자/하이픈 무관, 끝 8자리로 매칭.
     * @param scanLimit 가져올 최대 row 수 (최신순). 기본 1000.
     * @return 매칭된 문자 목록 (date DESC).
     */
    fun queryByPhone(phoneNumber: String, scanLimit: Int = 1000): List<SmsMessage> {
        if (!hasReadPermission()) return emptyList()
        val targetDigits = phoneNumber.filter { it.isDigit() }
        if (targetDigits.length < 7) return emptyList()
        val targetSuffix = targetDigits.takeLast(8)

        val smsList = querySmsByPhone(targetSuffix, scanLimit)
        // MMS 는 사진 첨부 + 자동 변환된 긴 문자 모두 여기로 들어옴.
        val mmsList = runCatching { queryMmsByPhone(targetSuffix, scanLimit) }.getOrDefault(emptyList())
        return (smsList + mmsList).sortedByDescending { it.dateMs }
    }

    private fun querySmsByPhone(targetSuffix: String, scanLimit: Int): List<SmsMessage> {
        val uri = Uri.parse("content://sms/")
        val projection = arrayOf(COL_ID, COL_ADDRESS, COL_BODY, COL_DATE, COL_TYPE)
        // Galaxy S24(OneUI 6.1) 등 일부 단말은 sortOrder 에 "LIMIT" 끼우면 쿼리가 무시된다.
        // API 26+ 표준 Bundle 인자로 전달.
        val cursor = runCatching {
            context.contentResolver.query(uri, projection, dateDescSortArgs(scanLimit), null)
        }.getOrNull() ?: return emptyList()

        return cursor.use { c ->
            val idIdx = c.getColumnIndex(COL_ID)
            val addrIdx = c.getColumnIndex(COL_ADDRESS)
            val bodyIdx = c.getColumnIndex(COL_BODY)
            val dateIdx = c.getColumnIndex(COL_DATE)
            val typeIdx = c.getColumnIndex(COL_TYPE)
            if (idIdx < 0 || addrIdx < 0 || bodyIdx < 0 || dateIdx < 0 || typeIdx < 0) return@use emptyList()

            val result = mutableListOf<SmsMessage>()
            var scanned = 0
            while (c.moveToNext() && scanned < scanLimit) {
                scanned++
                val type = c.getInt(typeIdx)
                // inbox(1) / sent(2) 만 표시 대상. 초안/실패/대기 등은 노이즈라 제외.
                if (type != TYPE_INBOX && type != TYPE_SENT) continue

                val address = c.getString(addrIdx).orEmpty()
                val addrDigits = address.filter { it.isDigit() }
                if (addrDigits.isEmpty()) continue
                val shortest = minOf(targetSuffix.length, addrDigits.length, 8)
                if (shortest < 7) continue
                if (addrDigits.takeLast(shortest) != targetSuffix.takeLast(shortest)) continue

                result += SmsMessage(
                    id = c.getLong(idIdx),
                    address = address,
                    body = c.getString(bodyIdx).orEmpty(),
                    dateMs = c.getLong(dateIdx),
                    sent = type == TYPE_SENT
                )
            }
            result
        }
    }

    /**
     * MMS 조회 — 사진 첨부 메시지 + 한국어 ~67자 / 영어 153자 초과로 자동 변환된 긴 텍스트 메시지.
     *
     * 구조:
     *  - `content://mms` row: id, date(초!), msg_box(1=inbox, 2=sent)
     *  - `content://mms/{id}/addr` row: address, type(137=from, 151=to, 130=bcc, 129=cc)
     *  - `content://mms/part` where mid={id}: text + 첨부 part. ct(content type) 으로 구분.
     *    - "text/plain" → text 컬럼에서 본문 (null 이면 _data 파일 fallback)
     *    - "image/..." (image/jpeg, image/png 등) → 이미지 첨부. content://mms/part/{partId} 가 데이터 URI (Coil 로 로드 가능)
     */
    private fun queryMmsByPhone(targetSuffix: String, scanLimit: Int): List<SmsMessage> {
        val mmsUri = Uri.parse("content://mms")
        val proj = arrayOf("_id", "date", "msg_box")
        val cursor = runCatching {
            context.contentResolver.query(mmsUri, proj, dateDescSortArgs(scanLimit), null)
        }.getOrNull() ?: return emptyList()

        return cursor.use { c ->
            val idIdx = c.getColumnIndex("_id")
            val dateIdx = c.getColumnIndex("date")
            val boxIdx = c.getColumnIndex("msg_box")
            if (idIdx < 0 || dateIdx < 0 || boxIdx < 0) return@use emptyList()

            val result = mutableListOf<SmsMessage>()
            while (c.moveToNext() && result.size < scanLimit) {
                val mmsId = c.getLong(idIdx)
                val box = c.getInt(boxIdx)
                if (box != MMS_BOX_INBOX && box != MMS_BOX_SENT) continue
                val dateSeconds = c.getLong(dateIdx)
                val dateMs = dateSeconds * 1000L

                val addresses = runCatching { getMmsAddresses(mmsId) }.getOrDefault(emptyList())
                val matched = addresses.any { (addr, _) -> matchesSuffix(addr, targetSuffix) }
                if (!matched) continue

                val (text, imageUris) = runCatching { getMmsParts(mmsId) }.getOrDefault("" to emptyList())
                if (text.isBlank() && imageUris.isEmpty()) continue

                val displayAddress = pickRelevantAddress(addresses, box) ?: ""

                result += SmsMessage(
                    id = mmsId,
                    address = displayAddress,
                    body = text,
                    dateMs = dateMs,
                    sent = box == MMS_BOX_SENT,
                    imageUris = imageUris
                )
            }
            result
        }
    }

    private fun matchesSuffix(address: String?, targetSuffix: String): Boolean {
        val addrDigits = address?.filter { it.isDigit() }.orEmpty()
        if (addrDigits.isEmpty()) return false
        val shortest = minOf(targetSuffix.length, addrDigits.length, 8)
        if (shortest < 7) return false
        return addrDigits.takeLast(shortest) == targetSuffix.takeLast(shortest)
    }

    private fun getMmsAddresses(mmsId: Long): List<Pair<String, Int>> {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        val proj = arrayOf("address", "type")
        val cursor = context.contentResolver.query(uri, proj, null, null, null) ?: return emptyList()
        return cursor.use { c ->
            val addrIdx = c.getColumnIndex("address")
            val typeIdx = c.getColumnIndex("type")
            if (addrIdx < 0 || typeIdx < 0) return@use emptyList()
            val out = mutableListOf<Pair<String, Int>>()
            while (c.moveToNext()) {
                val addr = c.getString(addrIdx).orEmpty()
                if (addr.isBlank() || addr == "insert-address-token") continue
                out += addr to c.getInt(typeIdx)
            }
            out
        }
    }

    /** inbox 면 from(137), sent 면 to(151) 우선. 없으면 아무 거나. */
    private fun pickRelevantAddress(addresses: List<Pair<String, Int>>, box: Int): String? {
        val preferred = if (box == MMS_BOX_INBOX) MMS_ADDR_FROM else MMS_ADDR_TO
        return addresses.firstOrNull { it.second == preferred }?.first
            ?: addresses.firstOrNull()?.first
    }

    /** 한 MMS 의 모든 파트 읽어 텍스트 합치고 이미지 URI 모음. */
    private fun getMmsParts(mmsId: Long): Pair<String, List<Uri>> {
        // content://mms/part 는 모든 파트가 통합돼있어서 mid 로 필터해야 함.
        val partsUri = Uri.parse("content://mms/part")
        val proj = arrayOf("_id", "ct", "text")
        val cursor = context.contentResolver.query(
            partsUri, proj, "mid=?", arrayOf(mmsId.toString()), null
        ) ?: return "" to emptyList()
        return cursor.use { c ->
            val pidIdx = c.getColumnIndex("_id")
            val ctIdx = c.getColumnIndex("ct")
            val textIdx = c.getColumnIndex("text")
            if (pidIdx < 0 || ctIdx < 0) return@use "" to emptyList()
            val textParts = mutableListOf<String>()
            val imageUris = mutableListOf<Uri>()
            while (c.moveToNext()) {
                val ct = c.getString(ctIdx).orEmpty()
                val partId = c.getLong(pidIdx)
                when {
                    ct == "text/plain" -> {
                        val t = if (textIdx >= 0) c.getString(textIdx).orEmpty() else ""
                        if (t.isNotBlank()) textParts += t
                        else {
                            // text 컬럼이 비면 part 파일에서 읽기 (드물게 발생)
                            runCatching {
                                val partUri = Uri.parse("content://mms/part/$partId")
                                context.contentResolver.openInputStream(partUri)?.use { stream ->
                                    val body = stream.readBytes().toString(Charsets.UTF_8)
                                    if (body.isNotBlank()) textParts += body
                                }
                            }
                        }
                    }
                    ct.startsWith("image/") -> {
                        imageUris += Uri.parse("content://mms/part/$partId")
                    }
                    // 그 외(application/smil, application/vnd.*) 는 무시.
                }
            }
            textParts.joinToString("\n") to imageUris
        }
    }

    /** 최근 SMS 연락처 한 명. 같은 번호로 여러 통이 있으면 가장 최근 한 통의 정보만 갖는다. */
    data class SmsContact(
        val address: String,
        /** 끝 8자리 (dedup 키). */
        val normalizedSuffix: String,
        val lastBody: String,
        val lastDateMs: Long,
        val lastSent: Boolean
    )

    /**
     * 시스템 SMS 에서 최근 주고받은 사람들의 목록을 반환 (번호별 중복 제거, 최근 1통 미리보기).
     * "수동 입력 → 최근 문자에서 가져오기" 다이얼로그용.
     *
     * @param scanLimit  SMS 프로바이더에서 가져올 row 수 상한 (최신순).
     * @param contactLimit 결과로 반환할 고유 연락처 수 상한.
     */
    fun queryRecentContacts(scanLimit: Int = 500, contactLimit: Int = 30): List<SmsContact> {
        if (!hasReadPermission()) return emptyList()

        val uri = Uri.parse("content://sms/")
        val projection = arrayOf(COL_ADDRESS, COL_BODY, COL_DATE, COL_TYPE)
        val cursor = runCatching {
            context.contentResolver.query(uri, projection, dateDescSortArgs(scanLimit), null)
        }.getOrNull() ?: return emptyList()

        return cursor.use { c ->
            val addrIdx = c.getColumnIndex(COL_ADDRESS)
            val bodyIdx = c.getColumnIndex(COL_BODY)
            val dateIdx = c.getColumnIndex(COL_DATE)
            val typeIdx = c.getColumnIndex(COL_TYPE)
            if (addrIdx < 0 || bodyIdx < 0 || dateIdx < 0 || typeIdx < 0) return@use emptyList()

            // LinkedHashMap 으로 삽입 순서(=최신순) 유지.
            val seen = LinkedHashMap<String, SmsContact>()
            while (c.moveToNext() && seen.size < contactLimit) {
                val type = c.getInt(typeIdx)
                if (type != TYPE_INBOX && type != TYPE_SENT) continue

                val address = c.getString(addrIdx).orEmpty()
                val addrDigits = address.filter { it.isDigit() }
                if (addrDigits.length < 7) continue
                val suffix = addrDigits.takeLast(8)
                if (seen.containsKey(suffix)) continue

                seen[suffix] = SmsContact(
                    address = address,
                    normalizedSuffix = suffix,
                    lastBody = c.getString(bodyIdx).orEmpty(),
                    lastDateMs = c.getLong(dateIdx),
                    lastSent = type == TYPE_SENT
                )
            }
            seen.values.toList()
        }
    }

    private fun dateDescSortArgs(limit: Int): Bundle = Bundle().apply {
        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(COL_DATE))
        putInt(
            ContentResolver.QUERY_ARG_SORT_DIRECTION,
            ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
        )
        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
    }

    companion object {
        private const val COL_ID = "_id"
        private const val COL_ADDRESS = "address"
        private const val COL_BODY = "body"
        private const val COL_DATE = "date"
        private const val COL_TYPE = "type"
        private const val TYPE_INBOX = 1
        private const val TYPE_SENT = 2

        // MMS msg_box 값
        private const val MMS_BOX_INBOX = 1
        private const val MMS_BOX_SENT = 2
        // MMS addr type 값
        private const val MMS_ADDR_FROM = 137
        private const val MMS_ADDR_TO = 151
    }
}
