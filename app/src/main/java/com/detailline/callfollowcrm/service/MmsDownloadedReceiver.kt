package com.detailline.callfollowcrm.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.detailline.callfollowcrm.util.LogRedact
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.ai.CustomerHint
import com.detailline.callfollowcrm.ai.HistoryMessage
import com.detailline.callfollowcrm.ai.PrepareContext
import com.google.android.mms.pdu_alt.PduParser
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.pdu_alt.RetrieveConf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 2026-07-05 — MMS 다운로드 완료 수신: 다운로드된 PDU 를 파싱 → content://mms persist → 후속 훅.
 *
 * MmsReceived 가 SmsManager.downloadMultimediaMessage 로 다운로드를 시작하면서 완료 PendingIntent 로 이 리시버 지정.
 *   완료 시 플랫폼(phone 서비스)이 임시 provider 파일에 PDU 를 write 하고 이 리시버를 결과코드와 함께 발사.
 * 우리는: 파일 → PduParser(RetrieveConf) → PduPersister.persist(INBOX) → 알림/홈캐시/추천준비.
 *
 * (explicit PendingIntent 라 intent-filter 불필요, exported=false.)
 */
class MmsDownloadedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ok = resultCode == Activity.RESULT_OK
        val code = resultCode
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
        val subId = intent.getIntExtra(EXTRA_SUB_ID, -1)
        val locationUrl = intent.getStringExtra(EXTRA_LOCATION_URL)
        // 이번이 '다시 받기' 인가. 무한 반복을 막는 유일한 장치라 반드시 본다.
        val isRetry = intent.getBooleanExtra(EXTRA_RETRY, false)
        val badUri = intent.getStringExtra(EXTRA_BAD_URI)
        val app = context.applicationContext as? CallFollowCrmApplication
        val pending = goAsync()
        scope.launch {
            try {
                if (!ok) {
                    Log.e(TAG, "MMS download NOT ok (resultCode=$code) — notifying"); notifyFail(app); return@launch
                }
                if (app == null || fileName.isNullOrBlank()) {
                    Log.e(TAG, "app/fileName missing"); notifyFail(app); return@launch
                }
                val file = File(context.cacheDir, File(fileName).name)
                if (!file.exists() || file.length() == 0L) {
                    Log.e(TAG, "downloaded PDU file missing/empty: ${file.absolutePath}"); notifyFail(app); return@launch
                }
                val bytes = runCatching { file.readBytes() }.getOrNull()
                if (bytes == null || bytes.isEmpty()) { Log.e(TAG, "PDU read failed"); notifyFail(app); return@launch }

                // content-disposition 지원 여부가 통신사마다 달라 두 방식 다 시도.
                val retrieve = runCatching { PduParser(bytes, true).parse() as? RetrieveConf }.getOrNull()
                    ?: runCatching { PduParser(bytes, false).parse() as? RetrieveConf }.getOrNull()
                if (retrieve == null) { Log.e(TAG, "RetrieveConf parse failed"); notifyFail(app); return@launch }

                // 📷 **깨진 사진인가** — 저장하기 전에 본다. (2026-09-20 사장님 "왜 우리 어플만 그래?")
                //   삼성 메시지는 자기 엔진이 실패하면 다시 받는다. 우린 확인조차 안 하고 있었다.
                val imgs = imageBytesOf(retrieve)
                val badCount = imgs.count { com.detailline.callfollowcrm.util.ImageNoiseCheck.isNoisy(it) }
                if (badCount > 0) {
                    Log.w(TAG, "깨진 사진 $badCount/${imgs.size} 장 (retry=$isRetry)")
                }

                // 다시 받았는데도 깨졌으면 **새 걸 버리고 옛 걸 그대로 둔다** — 바꿔서 나아질 게 없다.
                if (isRetry && badCount > 0) {
                    Log.w(TAG, "다시 받아도 깨짐 — 옛 것 유지하고 알림")
                    keepBadPdu(context, file)
                    notifyBroken(app)
                    return@launch
                }

                val msgUri = runCatching {
                    PduPersister.getPduPersister(context)
                        .persist(retrieve, Telephony.Mms.Inbox.CONTENT_URI, true, true, null, subId)
                }.onFailure { Log.e(TAG, "PduPersister.persist failed", it) }.getOrNull()
                Log.i(TAG, "MMS persisted → $msgUri (subId=$subId)")

                // 다시 받은 게 멀쩡하다 → 깨졌던 옛 문자를 지운다(같은 문자가 두 번 남지 않게).
                if (isRetry && badCount == 0 && !badUri.isNullOrBlank()) {
                    runCatching {
                        context.contentResolver.delete(android.net.Uri.parse(badUri), null, null)
                        Log.i(TAG, "깨졌던 옛 문자 지움 → $badUri")
                    }.onFailure { Log.e(TAG, "옛 문자 지우기 실패", it) }
                }

                // 처음 받았는데 깨졌다 → PDU 를 남기고 **한 번만** 다시 받는다.
                //   ⚠️ 저장은 이미 했다. 다시 받기가 실패해도 문자를 잃지 않는다.
                if (!isRetry && badCount > 0 && !locationUrl.isNullOrBlank()) {
                    keepBadPdu(context, file)
                    runCatching { file.delete() }
                    // ⚠️ 알림은 **여기서도** 울려야 한다. 문자는 이미 저장됐는데 다시 받기가
                    //   안 돌아오면 사장님은 문자가 온 줄도 모른다. (다시 받기가 성공하면 그때 또 울리지만
                    //   같은 id 라 덮어쓴다)
                    if (msgUri != null) runCatching { runMmsHook(app) }
                        .onFailure { Log.e(TAG, "hook failed", it) }
                    redownload(context, locationUrl, subId, msgUri?.toString())
                    return@launch
                }
                runCatching { file.delete() }

                if (msgUri == null) { notifyFail(app); return@launch }

                // 후속 훅 (알림/홈캐시/추천준비). persist 직후라 queryLatestInboxMms 로 방금 것 잡힘.
                runCatching { runMmsHook(app) }.onFailure { Log.e(TAG, "hook failed", it) }
            } finally {
                pending.finish()
            }
        }
    }

    /** RetrieveConf 안의 이미지 조각들. 저장하기 전에 바로 볼 수 있다. */
    internal fun imageBytesOf(retrieve: RetrieveConf): List<ByteArray> {
        val body = runCatching { retrieve.body }.getOrNull() ?: return emptyList()
        val out = ArrayList<ByteArray>()
        for (i in 0 until runCatching { body.partsNum }.getOrDefault(0)) {
            val part = runCatching { body.getPart(i) }.getOrNull() ?: continue
            val ct = runCatching { String(part.contentType ?: ByteArray(0)) }.getOrDefault("")
            if (!ct.startsWith("image/", ignoreCase = true)) continue
            runCatching { part.data }.getOrNull()?.let { out.add(it) }
        }
        return out
    }

    /**
     * 깨진 PDU 원본을 남긴다 — **다음에 또 생기면 이걸 뜯어봐야** 통신사가 준 게 이미 깨졌는지,
     * 우리가 뜯다 깨뜨렸는지 갈린다. 최근 3개만 두고 지운다(용량).
     */
    private fun keepBadPdu(context: Context, file: File) {
        runCatching {
            val dir = File(context.filesDir, "mms_bad").apply { mkdirs() }
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
            file.copyTo(File(dir, "bad_${System.currentTimeMillis()}.pdu"), overwrite = true)
            Log.i(TAG, "깨진 PDU 보관: ${dir.absolutePath}")
        }.onFailure { Log.e(TAG, "PDU 보관 실패", it) }
    }

    /** 한 번만 다시 받는다 — 삼성 메시지가 하는 것. */
    private fun redownload(context: Context, locationUrl: String, subId: Int, badUri: String?) {
        runCatching {
            val fileName = "mmsretry_${System.nanoTime()}.pdu"
            File(context.cacheDir, fileName).delete()
            val contentUri = android.net.Uri.Builder()
                .scheme(android.content.ContentResolver.SCHEME_CONTENT)
                .authority(context.packageName + MmsReceived.PROVIDER_SUFFIX)
                .path(fileName)
                .build()
            val completion = Intent(context, MmsDownloadedReceiver::class.java).apply {
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_LOCATION_URL, locationUrl)
                putExtra(EXTRA_SUB_ID, subId)
                putExtra(EXTRA_RETRY, true)
                putExtra(EXTRA_BAD_URI, badUri)
            }
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                    android.app.PendingIntent.FLAG_MUTABLE else 0)
            val pi = android.app.PendingIntent.getBroadcast(
                context, (System.nanoTime() and 0x7fffffffL).toInt(), completion, flags
            )
            val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                context.getSystemService(android.telephony.SmsManager::class.java)
            else @Suppress("DEPRECATION") android.telephony.SmsManager.getDefault()
            sms?.downloadMultimediaMessage(context, locationUrl, contentUri, null, pi)
            Log.i(TAG, "사진이 깨져서 한 번 다시 받는다 (subId=$subId)")
        }.onFailure { Log.e(TAG, "다시 받기 시작 실패", it) }
    }

    /** 두 번 다 깨졌을 때 — 사장님이 "왜 이러지?" 하며 시간 쓰지 않게 말해준다. */
    private fun notifyBroken(app: CallFollowCrmApplication?) {
        app ?: return
        if (!app.container.preferences.incomingSmsNotifyEnabled) return
        runCatching { NotificationHelper.showMmsBroken(app) }
    }

    private fun runMmsHook(app: CallFollowCrmApplication) {
        val container = app.container
        val mms = pollLatestInboxMms(container, maxAttempts = 8, delayMs = 150L)
        if (mms == null) {
            Log.w(TAG, "persisted but no inbox MMS row found — notify (no silent loss)"); notifyFail(app); return
        }
        val sender = mms.address.orEmpty()
        if (sender.isBlank()) { Log.w(TAG, "MMS sender blank — abort hook"); return }
        val rawBody = mms.body
        val imageCount = mms.imageUris.size
        val videoCount = mms.videoUris.size
        val attachLabel = buildString {
            if (imageCount > 0) append("사진 ${imageCount}장")
            if (videoCount > 0) { if (isNotEmpty()) append(" · "); append("동영상 ${videoCount}개") }
        }
        val displayBody = when {
            attachLabel.isNotEmpty() && rawBody.isNotBlank() -> "$attachLabel\n\n$rawBody"
            attachLabel.isNotEmpty() -> attachLabel
            else -> rawBody
        }
        val receivedAtMs = mms.dateMs
        Log.i(TAG, "MMS hook — sender=${LogRedact.phone(sender)} img=$imageCount vid=$videoCount body.len=${rawBody.length}")

        // 1) 알림 (SmsReceiver 와 같은 채널/빌더).
        if (container.preferences.incomingSmsNotifyEnabled) {
            val customerForNotif = runCatching { runBlocking { container.customerRepository.findByPhone(sender) } }.getOrNull()
            val categoryLabel = customerForNotif?.categoryId?.let { cid ->
                runCatching { runBlocking { container.categoryRepository.findById(cid)?.name } }.getOrNull()
            }
            NotificationHelper.showIncomingSms(
                context = app,
                phone = sender,
                displayName = customerForNotif?.name,
                body = displayBody,
                receivedAtMs = receivedAtMs,
                categoryLabel = categoryLabel,
                customerId = customerForNotif?.id   // 탭 시 그 고객으로 정확히 열기. (2026-08-11 알림 감사)
            )
            // 🔴 여기서 알렸다는 걸 **기록**해야 한다. (2026-09-15 사장님: "시간 지나고 또 신규문자 왔다고 2번 울려")
            //   MMS 알림 경로가 둘이다 — ① 다운로드 완료(여기, 즉시) ② 주기 폴링(notifyNewInboxMms).
            //   ①이 기록을 안 남기니 ②가 같은 문자를 '새 문자'로 보고 한 번 더 울렸다.
            //   → 방금 알린 그 문자의 mmsId 를 notifiedMmsIds 에 넣고 마커도 전진시킨다.
            runCatching {
                val prefs = container.preferences
                val sameNum = sender.filter { it.isDigit() }.takeLast(8)
                val ids = container.smsRepository
                    .queryInboxMmsSince(receivedAtMs - 3_000L, limit = 5)
                    .filter {
                        it.dateMs <= receivedAtMs + 3_000L &&
                            it.sender.filter { c -> c.isDigit() }.takeLast(8) == sameNum
                    }
                    .map { it.mmsId.toString() }
                if (ids.isNotEmpty()) {
                    prefs.notifiedMmsIds = (prefs.notifiedMmsIds + ids).toList().takeLast(80).toSet()
                }
                if (prefs.lastNotifiedMmsMs in 1 until receivedAtMs) {
                    prefs.lastNotifiedMmsMs = receivedAtMs
                }
                Log.i(TAG, "알림 기록 — mmsIds=${ids.size} marker→$receivedAtMs")
            }
        }

        // 2) 홈 상담함 캐시 upsert.
        val digits = sender.filter { it.isDigit() }
        val suffix = if (digits.length >= 8) digits.takeLast(8) else digits
        val newContact = com.detailline.callfollowcrm.data.repository.SmsRepository.SmsContact(
            address = sender,
            normalizedSuffix = suffix,
            lastBody = displayBody,
            lastDateMs = receivedAtMs,
            lastSent = false,
            hasOwnerReply = false,
            firstDateMsInScan = receivedAtMs
        )
        runCatching { runBlocking { container.smsContactCacheRepository.upsertOne(newContact) } }
            .onFailure { e -> Log.e(TAG, "cache upsert failed", e) }

        // 2-b) 방금 받은 MMS 를 **채팅 메시지 캐시에 즉시** 병합(merge) → 문자방 열면 바로 사진 표시.
        //   이걸 안 하면 아래 prefetch(전역 500 스캔, 8,900건 폰에서 수 초)가 끝나야 캐시에 들어가 "좀 있다 뜸" 지연 발생.
        //   우리는 방금 받은 mms(이미지 URI 포함)를 손에 쥐고 있으니 스캔 없이 바로 캐시.
        runCatching { runBlocking { container.cachedMessageRepository.mergeMmsForSuffix(suffix, listOf(mms)) } }
            .onFailure { e -> Log.e(TAG, "direct MMS cache merge failed", e) }

        // 3) prepare-reply (fire-and-forget). 'AI 답변 준비' OFF 또는 '고객 아님' 번호면 스킵. (2026-07-16/18 사장님)
        //   문자·사진 캐시(위 2번 mergeMms)는 이미 끝났으니 여긴 AI 준비만 담당 → 통째로 return 안전. 통화요약은 별개라 유지.
        if (!container.preferences.aiReplyPrepEnabled || container.preferences.isNonCustomer(sender)) return
        // SMS 경로(isSpam||isGeneral)와 동일하게 스팸 앞자리/문자함(GENERAL) MMS 엔 AI 준비 스킵 —
        //   광고·대표번호 MMS 에 서버비(Sonnet prepare) 낭비 방지. (2026-08-02 비용감사 — SMS 트윈과 필터 일치)
        run {
            val isSpam = com.detailline.callfollowcrm.util.SpamPrefix.isSpam(sender, container.preferences.spamPrefixes)
            val isGeneral = runCatching {
                runBlocking {
                    val cust = container.customerRepository.findByPhone(sender)
                    container.threadBucketRepository.classifyLocal(sender, displayBody, cust != null, false)
                } == com.detailline.callfollowcrm.domain.inbox.InboxClassifier.Verdict.GENERAL
            }.getOrDefault(false)
            if (isSpam || isGeneral) return
        }
        // ⚠️ (2026-08-14 사장님) 답변 추천 '미리 생성(requestPrepare)' 제거 → '볼 때만 생성' 전환(비용 68% 절감).
        //   사진(MMS) 수신 시에도 미리 만들지 않음. 문자방에서 '✨ AI 답변 추천받기' 탭 시 생성.
        //   prefetch(문자·사진 캐시 데우기)만 유지 — AI 와 무관, 문자방 빨리 뜨게.
        runCatching {
            container.smsCachePrefetcher.prefetchForNumber(sender)
        }.onFailure { e -> Log.e(TAG, "prefetch hook failed", e) }
    }

    private fun notifyFail(app: CallFollowCrmApplication?) {
        if (app == null) return
        if (app.container.preferences.incomingSmsNotifyEnabled) {
            runCatching { NotificationHelper.showMmsReceiveFailed(app, senderHint = null) }
        }
    }

    private fun pollLatestInboxMms(
        container: com.detailline.callfollowcrm.data.AppContainer,
        maxAttempts: Int,
        delayMs: Long
    ): com.detailline.callfollowcrm.data.repository.SmsRepository.SmsMessage? {
        repeat(maxAttempts) {
            val result = runCatching { container.smsRepository.queryLatestInboxMms() }.getOrNull()
            if (result != null) return result
            Thread.sleep(delayMs)
        }
        return null
    }

    companion object {
        private const val TAG = "MmsDownloaded"
        const val EXTRA_FILE_NAME = "mms_file_name"
        const val EXTRA_LOCATION_URL = "location_url"
        const val EXTRA_SUB_ID = "sub_id"
        /** 이번이 '다시 받기' 인가 — 무한 반복을 막는 유일한 장치. */
        const val EXTRA_RETRY = "is_retry"
        /** 다시 받기가 성공하면 지울 '깨졌던 문자' 주소. */
        const val EXTRA_BAD_URI = "bad_uri"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
