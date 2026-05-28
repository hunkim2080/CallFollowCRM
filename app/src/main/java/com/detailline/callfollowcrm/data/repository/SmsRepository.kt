package com.detailline.callfollowcrm.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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
    fun queryByPhone(phoneNumber: String, scanLimit: Int = 500): List<SmsMessage> {
        if (!hasReadPermission()) return emptyList()
        val targetDigits = phoneNumber.filter { it.isDigit() }
        if (targetDigits.length < 7) return emptyList()
        val targetSuffix = targetDigits.takeLast(8)

        val smsList = querySmsByPhone(targetSuffix, scanLimit)
        // MMS 는 사진 첨부 + 자동 변환된 긴 문자 모두 여기로 들어옴.
        // 각 MMS 마다 addr/parts 추가 쿼리가 들어가서 무거움. 사장님 MMS 8900건 폰에서 옛 사진 잡으려면 2000 으로 키움.
        val mmsList = runCatching { queryMmsByPhone(targetSuffix, 2000) }.getOrDefault(emptyList())
        return (smsList + mmsList).sortedByDescending { it.dateMs }
    }

    /**
     * SMS 만 빠르게 — ChatViewModel 의 stage 2 (가벼움).
     * 보통 100ms 내. MMS 풀스캔 없이 텍스트 대화를 먼저 띄울 때 사용.
     */
    fun querySmsOnly(phoneNumber: String, scanLimit: Int = 500): List<SmsMessage> {
        if (!hasReadPermission()) return emptyList()
        val targetDigits = phoneNumber.filter { it.isDigit() }
        if (targetDigits.length < 7) return emptyList()
        val targetSuffix = targetDigits.takeLast(8)
        return querySmsByPhone(targetSuffix, scanLimit).sortedByDescending { it.dateMs }
    }

    /**
     * MMS 만 — ChatViewModel 의 stage 3 (무거움, 백그라운드 마지막 단계).
     * 각 MMS 마다 addr/parts 부속 쿼리가 있어 시간 걸림. SMS 가 이미 표시된 상태에서 합쳐짐.
     * default 2000 = 사장님처럼 MMS 8900건 폰에서 옛 사진 잡히도록.
     */
    /**
     * 2026-05-29 Phase A 2단계 Day 4 — MmsDownloadService 가 klinker 위임 끝낸 직후 호출.
     * content://mms 의 가장 최근 INBOX row 를 반환 (sender + 본문 + 첨부 URI).
     *
     * 동시에 여러 MMS 들어오는 race 는 거의 없음 — 보통 1건 단위. 만약 race 가능성을
     * 차단하려면 super 호출 *전* max(_id) 저장 후 그 이후 row 만 선택하는 방식이 정공.
     * 여기선 단순화 — 사장님 검증 시 race 안 나오면 OK.
     *
     * @return 없거나 권한 없으면 null.
     */
    fun queryLatestInboxMms(): SmsMessage? {
        if (!hasReadPermission()) return null
        val mmsUri = Uri.parse("content://mms")
        val proj = arrayOf("_id", "date", "msg_box")
        val cursor = runCatching {
            context.contentResolver.query(mmsUri, proj, dateDescSortArgs(20), null)
        }.getOrNull() ?: return null
        return cursor.use { c ->
            val idIdx = c.getColumnIndex("_id")
            val dateIdx = c.getColumnIndex("date")
            val boxIdx = c.getColumnIndex("msg_box")
            if (idIdx < 0 || dateIdx < 0 || boxIdx < 0) return@use null
            while (c.moveToNext()) {
                if (c.getInt(boxIdx) != MMS_BOX_INBOX) continue
                val id = c.getLong(idIdx)
                val dateMs = c.getLong(dateIdx) * 1000L
                val addresses = runCatching { getMmsAddresses(id) }.getOrDefault(emptyList())
                val sender = pickRelevantAddress(addresses, MMS_BOX_INBOX) ?: continue
                val (text, imageUris) = runCatching { getMmsParts(id) }
                    .getOrDefault("" to emptyList())
                if (text.isBlank() && imageUris.isEmpty()) continue
                return@use SmsMessage(
                    id = id,
                    address = sender,
                    body = text,
                    dateMs = dateMs,
                    sent = false,
                    imageUris = imageUris
                )
            }
            null
        }
    }

    fun queryMmsOnly(phoneNumber: String, scanLimit: Int = 2000): List<SmsMessage> {
        if (!hasReadPermission()) return emptyList()
        val targetDigits = phoneNumber.filter { it.isDigit() }
        if (targetDigits.length < 7) return emptyList()
        val targetSuffix = targetDigits.takeLast(8)
        return runCatching { queryMmsByPhone(targetSuffix, scanLimit) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.dateMs }
    }

    private fun querySmsByPhone(targetSuffix: String, scanLimit: Int): List<SmsMessage> {
        val uri = Uri.parse("content://sms/")
        val projection = arrayOf(COL_ID, COL_ADDRESS, COL_BODY, COL_DATE, COL_TYPE)

        // SQL selection 으로 address 에 끝 8자리 포함된 row 만 받음.
        //   - 시스템 SMS 가 17,000 건 넘는 사장님 폰에서 scanLimit 으로 자르면 옛 메시지가 잘려나감 (2026-05-24 진단).
        //   - LIKE '%suffix%' 는 한국 번호 끝 8자리 unique 가정 → 노이즈 거의 없음.
        //   - 한 번 더 Kotlin 에서 정확 끝 8자리 매칭 + type 필터로 안전망.
        // sortOrder = date DESC. LIMIT 은 selection 으로 이미 좁아져서 굳이 안 줌 (한 사람당 보통 수십~수백건).
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "$COL_ADDRESS LIKE ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%$targetSuffix%"))
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(COL_DATE))
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
        }
        val cursor = runCatching {
            context.contentResolver.query(uri, projection, queryArgs, null)
        }.getOrNull() ?: return emptyList()

        return cursor.use { c ->
            val idIdx = c.getColumnIndex(COL_ID)
            val addrIdx = c.getColumnIndex(COL_ADDRESS)
            val bodyIdx = c.getColumnIndex(COL_BODY)
            val dateIdx = c.getColumnIndex(COL_DATE)
            val typeIdx = c.getColumnIndex(COL_TYPE)
            if (idIdx < 0 || addrIdx < 0 || bodyIdx < 0 || dateIdx < 0 || typeIdx < 0) return@use emptyList()

            val result = mutableListOf<SmsMessage>()
            while (c.moveToNext()) {
                val type = c.getInt(typeIdx)
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
        // thread_id 추가 — 사장님이 갤럭시 메시지 앱으로 보낸 MMS 는 OneUI 가 addr 테이블의
        //   type=151(TO) 행에 실제 번호 대신 "insert-address-token" placeholder 만 저장하는 경우가 흔하다.
        //   그러면 address 매칭만으론 sent MMS 가 통째로 누락됨 (사장님 2026-05-24 보고).
        //   → SMS 에서 같은 번호와 주고받은 thread_id 시드 + MMS 자체 inbox 매칭 thread_id 를 모아
        //     address 매칭 실패한 sent MMS 라도 같은 thread_id 면 포함시킨다.
        val proj = arrayOf("_id", "date", "msg_box", "thread_id")
        val cursor = runCatching {
            context.contentResolver.query(mmsUri, proj, dateDescSortArgs(scanLimit), null)
        }.getOrNull() ?: return emptyList()

        // 시드 = SMS 에서 같은 suffix 의 thread_id 들. 인덱스 LIKE 라 빠름.
        val smsSeedThreadIds = runCatching { collectSmsThreadIds(targetSuffix) }
            .getOrDefault(emptySet())

        data class MmsRow(val id: Long, val dateMs: Long, val box: Int, val threadId: Long)

        return cursor.use { c ->
            val idIdx = c.getColumnIndex("_id")
            val dateIdx = c.getColumnIndex("date")
            val boxIdx = c.getColumnIndex("msg_box")
            val threadIdx = c.getColumnIndex("thread_id")
            if (idIdx < 0 || dateIdx < 0 || boxIdx < 0) return@use emptyList()

            // Pass 1: 후보 row 메타데이터 수집 (INBOX / SENT 만)
            val rows = mutableListOf<MmsRow>()
            while (c.moveToNext() && rows.size < scanLimit) {
                val box = c.getInt(boxIdx)
                if (box != MMS_BOX_INBOX && box != MMS_BOX_SENT) continue
                rows += MmsRow(
                    id = c.getLong(idIdx),
                    dateMs = c.getLong(dateIdx) * 1000L,
                    box = box,
                    threadId = if (threadIdx >= 0) c.getLong(threadIdx) else -1L
                )
            }

            // addresses 캐시 — Pass 2 와 최종 변환에서 두 번 부르지 않게.
            val addressesById = HashMap<Long, List<Pair<String, Int>>>(rows.size)
            fun addressesOf(id: Long): List<Pair<String, Int>> = addressesById.getOrPut(id) {
                runCatching { getMmsAddresses(id) }.getOrDefault(emptyList())
            }

            // Pass 2: address 매칭 + 매칭된 row 의 thread_id 수집 (SMS 시드와 합쳐 확장)
            val pickedById = LinkedHashMap<Long, MmsRow>()
            val matchedThreadIds = HashSet<Long>(smsSeedThreadIds.size + 4).apply {
                addAll(smsSeedThreadIds)
            }
            for (row in rows) {
                val addresses = addressesOf(row.id)
                val matched = addresses.any { (addr, _) -> matchesSuffix(addr, targetSuffix) }
                if (matched) {
                    pickedById[row.id] = row
                    if (row.threadId > 0) matchedThreadIds += row.threadId
                }
            }

            // Pass 3: address 매칭 실패했지만 thread_id 가 매칭 set 에 있는 sent MMS 추가.
            //   (OneUI 의 sent MMS placeholder 케이스 + 같은 대화의 누락된 MMS 복구)
            for (row in rows) {
                if (pickedById.containsKey(row.id)) continue
                if (row.threadId > 0 && row.threadId in matchedThreadIds) {
                    pickedById[row.id] = row
                }
            }

            // 최종 SmsMessage 변환 — parts 가져와서 body/imageUris 채움.
            val result = mutableListOf<SmsMessage>()
            for (row in pickedById.values) {
                val (text, imageUris) = runCatching { getMmsParts(row.id) }
                    .getOrDefault("" to emptyList())
                if (text.isBlank() && imageUris.isEmpty()) continue
                val displayAddress = pickRelevantAddress(addressesOf(row.id), row.box) ?: ""
                result += SmsMessage(
                    id = row.id,
                    address = displayAddress,
                    body = text,
                    dateMs = row.dateMs,
                    sent = row.box == MMS_BOX_SENT,
                    imageUris = imageUris
                )
            }
            result
        }
    }

    /**
     * SMS LIKE selection 으로 해당 suffix 의 thread_id 들 빠르게 수집 (queryMmsByPhone 시드용).
     * 같은 번호로 한 번이라도 SMS 주고받았으면 그 thread_id 가 sent MMS 의 thread_id 와도 일치
     *   → address 매칭 실패한 sent MMS 를 복구하는 핵심 근거.
     */
    private fun collectSmsThreadIds(targetSuffix: String): Set<Long> {
        val uri = Uri.parse("content://sms/")
        val proj = arrayOf("thread_id", COL_ADDRESS)
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "$COL_ADDRESS LIKE ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%$targetSuffix%"))
        }
        val cursor = runCatching {
            context.contentResolver.query(uri, proj, queryArgs, null)
        }.getOrNull() ?: return emptySet()
        return cursor.use { c ->
            val tIdx = c.getColumnIndex("thread_id")
            val aIdx = c.getColumnIndex(COL_ADDRESS)
            if (tIdx < 0 || aIdx < 0) return@use emptySet()
            val out = HashSet<Long>()
            while (c.moveToNext()) {
                val addr = c.getString(aIdx).orEmpty()
                val addrDigits = addr.filter { it.isDigit() }
                val shortest = minOf(targetSuffix.length, addrDigits.length, 8)
                if (shortest < 7) continue
                if (addrDigits.takeLast(shortest) != targetSuffix.takeLast(shortest)) continue
                val t = c.getLong(tIdx)
                if (t > 0) out += t
            }
            out
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
        val lastSent: Boolean,
        /**
         * 스캔 범위 안에서 이 번호로 사장님이 보낸 sent SMS 가 1건이라도 있는지.
         * "미확인" 판정의 핵심 — 한 번이라도 답장했으면 미확인 아님.
         * scanLimit 밖 (옛날) 의 sent 는 false 로 잡힐 수 있는 한계 있음 (사장님 폰 17000건 환경에선 보통 OK).
         */
        val hasOwnerReply: Boolean,
        /**
         * 스캔 범위 안에서 이 번호의 가장 오래된 dateMs.
         * "오늘 신규" 판정용 — todayStart 이상이면 = 오늘 이전 기록 없음 = 진짜 신규 후보.
         */
        val firstDateMsInScan: Long
    )

    /**
     * 시스템 SMS 에서 최근 주고받은 사람들의 목록을 반환 (번호별 중복 제거, 최근 1통 미리보기).
     * "수동 입력 → 최근 문자에서 가져오기" + HomeScreen SMS-only 카드 통합용.
     *
     * scanLimit 가 작으면 사장님처럼 SMS 가 17000+ 건인 경우 옛 연락처 누락됨.
     * default 를 10000 으로 크게 잡음 — cursor streaming 이라 메모리 부담 적고, dedup 으로 contactLimit 까지만 보관.
     *
     * @param scanLimit  SMS 프로바이더에서 가져올 row 수 상한 (최신순). default 10000.
     * @param contactLimit 결과로 반환할 고유 연락처 수 상한.
     */
    fun queryRecentContacts(scanLimit: Int = 10000, contactLimit: Int = 30): List<SmsContact> {
        if (!hasReadPermission()) return emptyList()

        val seen = LinkedHashMap<String, SmsContact>()
        fillFromSms(seen, scanLimit, contactLimit)
        // 2026-05-26 사장님 보고 fix — MMS (사진/첨부 문자) 누락 해결.
        //   갤메시지의 "📎 첨부파일 N개" = MMS. queryRecentContacts 가 sms/ 만 쿼리해서 누락됐었음.
        //   MMS 의 phone 매핑은 mms/{id}/addr 별도 쿼리 필요해 mmsScanLimit 작게 (성능 보호).
        //   200건이면 사장님 폰의 최근 활동 충분히 커버.
        fillFromMms(seen, mmsScanLimit = 200, contactLimit)
        // SMS + MMS 합쳐서 최신순 재정렬 (LinkedHashMap 삽입 순서 깨짐).
        return seen.values
            .sortedByDescending { it.lastDateMs }
            .take(contactLimit)
    }

    private fun fillFromSms(
        seen: LinkedHashMap<String, SmsContact>,
        scanLimit: Int,
        contactLimit: Int
    ) {
        val uri = Uri.parse("content://sms/")
        val projection = arrayOf(COL_ADDRESS, COL_BODY, COL_DATE, COL_TYPE)
        val cursor = runCatching {
            context.contentResolver.query(uri, projection, dateDescSortArgs(scanLimit), null)
        }.getOrNull() ?: return

        cursor.use { c ->
            val addrIdx = c.getColumnIndex(COL_ADDRESS)
            val bodyIdx = c.getColumnIndex(COL_BODY)
            val dateIdx = c.getColumnIndex(COL_DATE)
            val typeIdx = c.getColumnIndex(COL_TYPE)
            if (addrIdx < 0 || bodyIdx < 0 || dateIdx < 0 || typeIdx < 0) return@use

            while (c.moveToNext()) {
                val type = c.getInt(typeIdx)
                if (type != TYPE_INBOX && type != TYPE_SENT) continue

                val address = c.getString(addrIdx).orEmpty()
                val addrDigits = address.filter { it.isDigit() }
                if (addrDigits.length < 7) continue
                val suffix = addrDigits.takeLast(8)
                val dateMs = c.getLong(dateIdx)
                val isSent = type == TYPE_SENT

                val existing = seen[suffix]
                if (existing == null) {
                    if (seen.size >= contactLimit) continue
                    seen[suffix] = SmsContact(
                        address = address,
                        normalizedSuffix = suffix,
                        lastBody = c.getString(bodyIdx).orEmpty(),
                        lastDateMs = dateMs,
                        lastSent = isSent,
                        hasOwnerReply = isSent,
                        firstDateMsInScan = dateMs
                    )
                } else {
                    // DESC 순회라 dateMs 는 항상 existing.firstDateMsInScan 이하 → 덮어쓰면 가장 오래된 값.
                    seen[suffix] = existing.copy(
                        hasOwnerReply = existing.hasOwnerReply || isSent,
                        firstDateMsInScan = dateMs
                    )
                }
            }
        }
    }

    /**
     * MMS row 를 훑어 SMS-only seen 맵에 머지.
     *   - MMS date 는 초 단위 → ms 변환 (× 1000)
     *   - MMS phone = addr 서브테이블 별도 조회 (137=from / 151=to). 비용 비싸 mmsScanLimit 으로 cap.
     *   - 본문은 "📎 사진/첨부 N장" placeholder — part 본문까지 읽으면 성능 더 떨어져 표시용으로만.
     *   - 같은 phone 이 SMS+MMS 둘 다 있으면 dateMs 더 큰 쪽이 lastBody/lastSent 결정.
     */
    private fun fillFromMms(
        seen: LinkedHashMap<String, SmsContact>,
        mmsScanLimit: Int,
        contactLimit: Int
    ) {
        val mmsUri = Uri.parse("content://mms")
        val proj = arrayOf("_id", "date", "msg_box")
        val cursor = runCatching {
            context.contentResolver.query(mmsUri, proj, dateDescSortArgs(mmsScanLimit), null)
        }.getOrNull() ?: return

        cursor.use { c ->
            val idIdx = c.getColumnIndex("_id")
            val dateIdx = c.getColumnIndex("date")
            val boxIdx = c.getColumnIndex("msg_box")
            if (idIdx < 0 || dateIdx < 0 || boxIdx < 0) return@use

            while (c.moveToNext()) {
                val mmsId = c.getLong(idIdx)
                val dateSec = c.getLong(dateIdx)
                val dateMs = dateSec * 1000L
                val box = c.getInt(boxIdx)
                if (box != MMS_BOX_INBOX && box != MMS_BOX_SENT) continue
                val isSent = box == MMS_BOX_SENT

                val addrs = runCatching { getMmsAddresses(mmsId) }.getOrDefault(emptyList())
                // 받은 MMS = type=137 (from), 보낸 MMS = type=151 (to). 다른 type 은 그룹/cc.
                val phone = if (isSent) {
                    addrs.firstOrNull { (_, type) -> type == MMS_ADDR_TO }?.first
                } else {
                    addrs.firstOrNull { (_, type) -> type == MMS_ADDR_FROM }?.first
                }?.takeIf { it.isNotBlank() } ?: continue

                val digits = phone.filter { it.isDigit() }
                if (digits.length < 7) continue
                val suffix = digits.takeLast(8)

                val existing = seen[suffix]
                if (existing == null) {
                    if (seen.size >= contactLimit) continue
                    seen[suffix] = SmsContact(
                        address = phone,
                        normalizedSuffix = suffix,
                        lastBody = "📎 사진/첨부 메시지",
                        lastDateMs = dateMs,
                        lastSent = isSent,
                        hasOwnerReply = isSent,
                        firstDateMsInScan = dateMs
                    )
                } else {
                    // MMS dateMs 가 SMS lastDateMs 보다 더 최신이면 본문/방향 갱신.
                    val isNewer = dateMs > existing.lastDateMs
                    seen[suffix] = existing.copy(
                        lastBody = if (isNewer) "📎 사진/첨부 메시지" else existing.lastBody,
                        lastDateMs = maxOf(existing.lastDateMs, dateMs),
                        lastSent = if (isNewer) isSent else existing.lastSent,
                        hasOwnerReply = existing.hasOwnerReply || isSent,
                        firstDateMsInScan = minOf(existing.firstDateMsInScan, dateMs)
                    )
                }
            }
        }
    }

    /**
     * 사장님이 보낸(sent=true) 메시지 본문 최근 N건. 톤 학습 코퍼스 용도.
     * type=2 selection 대신 content://sms/sent path 사용 — 더 빠르고 단순.
     * 너무 짧은 메시지 (5자 미만) 는 톤 학습 가치 낮아 제외.
     */
    fun querySentMessages(limit: Int = 50): List<String> {
        if (!hasReadPermission()) return emptyList()
        val uri = Uri.parse("content://sms/sent")
        val projection = arrayOf(COL_BODY)
        val cursor = runCatching {
            context.contentResolver.query(uri, projection, dateDescSortArgs(limit * 2), null)
        }.getOrNull() ?: return emptyList()
        return cursor.use { c ->
            val bodyIdx = c.getColumnIndex(COL_BODY)
            if (bodyIdx < 0) return@use emptyList()
            val out = mutableListOf<String>()
            while (c.moveToNext() && out.size < limit) {
                val body = c.getString(bodyIdx).orEmpty().trim()
                if (body.length >= 5) out += body
            }
            out
        }
    }

    /**
     * 2026-05-29 킬러콘텐츠 4단계 (Tone RAG) — 사장님 sent SMS 풀 batch upload 용.
     * 본문 + timestamp 함께 반환. 너무 짧은 (< 5자) 또는 너무 긴 (> 500자) 메시지 제외.
     */
    data class SentMessage(val body: String, val dateMs: Long)

    fun querySentMessagesWithTimestamp(limit: Int = 10000): List<SentMessage> {
        if (!hasReadPermission()) return emptyList()
        val uri = Uri.parse("content://sms/sent")
        val projection = arrayOf(COL_BODY, COL_DATE)
        val cursor = runCatching {
            context.contentResolver.query(uri, projection, dateDescSortArgs(limit), null)
        }.getOrNull() ?: return emptyList()
        return cursor.use { c ->
            val bodyIdx = c.getColumnIndex(COL_BODY)
            val dateIdx = c.getColumnIndex(COL_DATE)
            if (bodyIdx < 0 || dateIdx < 0) return@use emptyList()
            val out = mutableListOf<SentMessage>()
            while (c.moveToNext() && out.size < limit) {
                val body = c.getString(bodyIdx).orEmpty().trim()
                if (body.length < 5 || body.length > 500) continue
                out += SentMessage(body = body, dateMs = c.getLong(dateIdx))
            }
            out
        }
    }

    /**
     * 디버그 진단 — 시스템 DB 가 진짜 어떻게 보이는지 한 번에 측정.
     * 갤S24/OneUI 6.1 에서 query 가 의심될 때 화면에 표시.
     *
     * @param suffix 특정 끝 8자리가 시스템 SMS/MMS 의 address 에 등장하는지 검색용
     */
    data class SystemDiagnosis(
        val smsTotalRows: Int,
        val smsInboxRows: Int,
        val smsSentRows: Int,
        val mmsTotalRows: Int,
        val suffixInSms: Boolean,
        val suffixInMms: Boolean,
        val sampleSmsAddresses: List<String>
    )

    fun diagnose(suffix: String?): SystemDiagnosis {
        if (!hasReadPermission()) return SystemDiagnosis(0, 0, 0, 0, false, false, emptyList())

        // 카운트는 SELECT count(_id) row 만 받기 — cursor.count 가 거의 즉시.
        fun countRows(uri: Uri, where: String? = null, args: Array<String>? = null): Int = runCatching {
            context.contentResolver.query(uri, arrayOf("_id"), where, args, null)?.use { it.count } ?: 0
        }.getOrDefault(0)

        val smsTotal = countRows(Uri.parse("content://sms/"))
        val smsInbox = countRows(Uri.parse("content://sms/"), "type=?", arrayOf(TYPE_INBOX.toString()))
        val smsSent = countRows(Uri.parse("content://sms/"), "type=?", arrayOf(TYPE_SENT.toString()))
        val mmsTotal = countRows(Uri.parse("content://mms"))

        // 샘플 address — sortOrder Bundle 로 최신 10개만.
        val sampleAddrs = runCatching {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/"),
                arrayOf(COL_ADDRESS),
                dateDescSortArgs(10),
                null
            )
            cursor?.use { c ->
                val idx = c.getColumnIndex(COL_ADDRESS)
                val out = mutableListOf<String>()
                while (c.moveToNext() && out.size < 10) {
                    if (idx >= 0) {
                        val a = c.getString(idx).orEmpty()
                        if (a.isNotBlank()) out += a
                    }
                }
                out
            } ?: emptyList()
        }.getOrDefault(emptyList())

        // suffix 검색 — SQL LIKE 로 직접. address 가 다양한 포맷이라 끝 8자리 매칭이 SQL LIKE 로 안전.
        val foundSuffix = if (suffix != null) {
            countRows(Uri.parse("content://sms/"), "address LIKE ?", arrayOf("%$suffix%")) > 0
        } else false

        // MMS suffix 검색은 mms/addr 별도 테이블 — 최근 2000건만 보고 매칭. 8900건 폰에서도 1~2초.
        val foundSuffixMms = if (suffix != null && mmsTotal > 0) {
            runCatching {
                var hit = false
                val c = context.contentResolver.query(
                    Uri.parse("content://mms"),
                    arrayOf("_id"),
                    Bundle().apply {
                        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf("date"))
                        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                        putInt(ContentResolver.QUERY_ARG_LIMIT, 2000)
                    },
                    null
                )
                c?.use { cur ->
                    while (cur.moveToNext() && !hit) {
                        val mmsId = cur.getLong(0)
                        runCatching {
                            val addrs = getMmsAddresses(mmsId)
                            if (addrs.any { (a, _) -> matchesSuffix(a, suffix) }) hit = true
                        }
                    }
                }
                hit
            }.getOrDefault(false)
        } else false

        return SystemDiagnosis(
            smsTotalRows = smsTotal,
            smsInboxRows = smsInbox,
            smsSentRows = smsSent,
            mmsTotalRows = mmsTotal,
            suffixInSms = foundSuffix,
            suffixInMms = foundSuffixMms,
            sampleSmsAddresses = sampleAddrs
        )
    }

    /**
     * SMS/MMS provider 변경 시 emit 하는 Flow. 새 메시지 도착 / 발송 시 Android 가 자동 통지.
     *   - 최초 1회 즉시 emit (initial load)
     *   - onChange 마다 재쿼리 후 emit (debounce 책임은 호출자)
     *   - 본 Flow 는 ContentObserver 만 등록; 권한 체크는 호출자가 또는 [hasReadPermission] 으로.
     *
     * 갤메시지 (default SMS app) 가 SMS 받을 때 content://sms 에 INSERT → observer 트리거.
     * RING-GO 가 default 아니어도 통지 받음 — "갤메시지처럼 즉시 최신화" 가 가능해진 이유.
     */
    fun observeContacts(scanLimit: Int = 10000, contactLimit: Int = 500): Flow<List<SmsContact>> =
        callbackFlow {
            // 2026-05-30 사장님 ANR 보고 fix:
            //   기존 Handler(Looper.getMainLooper()) 이면 ContentObserver.onChange 가 main thread 에서 실행.
            //   그 안에서 safeQueryContacts (10000건 SMS+MMS 풀스캔) 호출 → main thread 5초+ block.
            //   Default SMS 앱 인수 후 provider 변경 폭주 → ANR 폭증.
            //   해결: HandlerThread (background) 로 observer 의 callback 격리. main thread 영향 0.
            //   추가 안전망: 짧은 시간 안 폭주하는 onChange 를 debounce — 마지막 호출 후 250ms 만 query.
            val ht = HandlerThread("SmsContentObserver").apply { start() }
            val bgHandler = Handler(ht.looper)
            val pendingRunnable = object : Runnable {
                override fun run() {
                    trySend(safeQueryContacts(scanLimit, contactLimit))
                }
            }
            val observer = object : ContentObserver(bgHandler) {
                override fun onChange(selfChange: Boolean) {
                    // debounce — 짧은 시간 안 다발 변경 (default SMS 앱일 때 폭주) 흡수.
                    bgHandler.removeCallbacks(pendingRunnable)
                    bgHandler.postDelayed(pendingRunnable, 250L)
                }
            }
            // content://sms 와 content://mms 둘 다 — MMS 도 갤럭시 메시지 목록에 섞임.
            //   notifyForDescendants=true 로 sub-uri (sent, inbox) 변경도 포착.
            val smsUri = Uri.parse("content://sms")
            val mmsUri = Uri.parse("content://mms")
            val cr = context.contentResolver
            runCatching { cr.registerContentObserver(smsUri, true, observer) }
            runCatching { cr.registerContentObserver(mmsUri, true, observer) }

            // 초기 emit — 화면 진입 즉시 데이터 채움. background thread 에서 query → 비차단.
            bgHandler.post { trySend(safeQueryContacts(scanLimit, contactLimit)) }

            awaitClose {
                runCatching { cr.unregisterContentObserver(observer) }
                runCatching { bgHandler.removeCallbacksAndMessages(null) }
                runCatching { ht.quitSafely() }
            }
        }

    private fun safeQueryContacts(scanLimit: Int, contactLimit: Int): List<SmsContact> =
        runCatching {
            if (hasReadPermission()) queryRecentContacts(scanLimit, contactLimit)
            else emptyList()
        }.getOrDefault(emptyList())

    /**
     * 풀스캔 1회 (2026-05-28) — Application 첫 실행 시 SmsContactCacheRepository.rebuildFromFullScan 용.
     *   Flow 가 아닌 suspend. 권한 없으면 emptyList. Dispatchers.IO 에서 실행.
     */
    suspend fun queryContactsOnce(scanLimit: Int = 10000, contactLimit: Int = 500): List<SmsContact> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            safeQueryContacts(scanLimit, contactLimit)
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
