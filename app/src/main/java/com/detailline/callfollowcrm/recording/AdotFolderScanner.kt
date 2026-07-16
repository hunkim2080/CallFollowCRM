package com.detailline.callfollowcrm.recording

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.model.RecordingSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 통화녹음(m4a) 자동 연결 — 두 경로:
 *
 *  ① 자동 찾기 (권장, 연세 있으신 분도 쉬움) — 오디오 권한 한 번만 허용하면
 *     [MediaStore] 에서 통화녹음 파일을 **앱이 알아서 찾는다**. 폴더를 직접 고를 필요 없음.
 *     기기/녹음앱(에이닷·T전화·삼성)에 상관없이 파일명이 [AdotFilenameParser] 로 해석되는 오디오만 추려서 본다.
 *  ② 폴더 직접 연결 (fallback) — 옛 방식. OpenDocumentTree 로 폴더 하나를 골라 그 안의 .m4a 를 본다.
 *     ①이 안 되는 기기/상황(MediaStore 미색인 등) 대비로 남겨둔다.
 *
 * 어느 경로든 [listCandidates] 로 통합돼서, 통화종료 자동요약(CallSummaryScanWorker)·통화카드 탭
 * (summarizeCallNow)·번호 백필(scanByPhone) 이 그대로 둘 다에서 동작한다.
 *
 * cutoff: [connectedAt] 이후 녹음만 자동 import/요약(과거 녹음 대량 재과금 방지). 사용자가 명시적으로
 *   끌어오는 summarizeCallNow/scanByPhone 은 cutoff 무시.
 */
object AdotFolderScanner {

    private const val PREFS = "adot_scanner"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_LAST_SCAN = "last_scan"
    private const val KEY_CONNECTED_AT = "connected_at"
    private const val KEY_MEDIASTORE = "mediastore_enabled"   // ① 자동 찾기 opt-in 플래그

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 통합 후보 파일 — SAF 폴더든 MediaStore든 동일하게 (열 수 있는 uri, 파일명) 으로 본다. */
    data class RecFile(val uriStr: String, val name: String)

    // ── 권한 ──────────────────────────────────────────────────────────────────
    /** 오디오 읽기 권한 (33+ READ_MEDIA_AUDIO, 그 이하 READ_EXTERNAL_STORAGE). */
    fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

    // ── ① 자동 찾기 (MediaStore) ────────────────────────────────────────────────
    fun isMediaStoreEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MEDIASTORE, false)

    /**
     * 사용자가 "자동으로 찾기" → 권한 허용까지 마쳤을 때 호출. 이후 이 폰은 MediaStore 에서 통화녹음을
     * 자동으로 본다. connectedAt 을 지금으로 박아 **앞으로의 통화만** 자동 요약(과거 대량 처리 방지).
     */
    fun enableMediaStore(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_MEDIASTORE, true)
            .putLong(KEY_CONNECTED_AT, System.currentTimeMillis())
            .apply()
    }

    /** 지금 MediaStore 에서 보이는 통화녹음 후보 개수 — "N개 찾았어요" 안내용. */
    fun countMediaStoreCandidates(context: Context): Int =
        if (hasAudioPermission(context)) listFromMediaStore(context).size else 0

    private fun listFromMediaStore(context: Context): List<RecFile> {
        val out = ArrayList<RecFile>()
        val col = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
        runCatching {
            // 통화녹음만: 파일명이 AdotFilenameParser 로 해석되는 것만(음악·알림음 등 제외). 폴더 경로는 기기마다
            //   달라서 가정하지 않고, 파일명 패턴으로 거른다 → 에이닷/T전화/삼성 어디 저장돼도 잡힌다.
            // 2026-07-16: parse(번호 필수) → parseLoose(시각만 있어도 통과). 연락처에 저장된 사람이면 번호 대신
            //   이름을 넣는 폰(`통화 남이편_260716_112558.m4a`)의 녹음이 통째로 안 보이던 것 fix. 번호는 나중에
            //   통화기록으로 되찾는다(findCallAtTime). 개수 표시("녹음 N개 발견")도 이 기준이라 이제 안 속인다.
            context.contentResolver.query(col, proj, null, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val name = c.getString(nameCol) ?: continue
                    if (!isAudioName(name)) continue
                    if (AdotFilenameParser.parseLoose(name) == null) continue
                    out.add(RecFile(ContentUris.withAppendedId(col, c.getLong(idCol)).toString(), name))
                }
            }
        }
        return out
    }

    // ── ② 폴더 직접 연결 (SAF, fallback) ─────────────────────────────────────────
    /** Settings 에서 OpenDocumentTree 결과로 얻은 URI 를 저장. 연결 시각도 함께 저장. */
    fun connectFolder(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TREE_URI, treeUri.toString())
            .putLong(KEY_CONNECTED_AT, System.currentTimeMillis())
            .apply()
    }

    /** 폴더 연결 시점. 이 시각 이후에 녹음된 파일만 import 대상. */
    fun connectedAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_CONNECTED_AT, 0L)

    /** "지금부터 다시" — 정리 후 깨끗하게 시작할 때. 옛 파일은 다시 들어오지 않음. */
    fun resetConnectedAtToNow(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_CONNECTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun disconnect(context: Context) {
        getTreeUri(context)?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_TREE_URI)
            .putBoolean(KEY_MEDIASTORE, false)
            .apply()
    }

    /** 연결됨 = 폴더 직접 연결했거나, 자동 찾기(권한+opt-in) 가 켜져 있음. */
    fun isConnected(context: Context): Boolean =
        getTreeUri(context) != null || (isMediaStoreEnabled(context) && hasAudioPermission(context))

    /**
     * 무엇이 연결됐는지 사람이 읽을 수 있는 한 줄 — 사장님이 "제대로 연결됐나" 확인용. (2026-07-12 사장님)
     *   SAF 폴더면 폴더 이름 + 발견 개수, 자동찾기면 "자동 찾기 · 녹음 N개 발견". 미연결이면 null.
     */
    fun connectedLabel(context: Context): String? {
        val tree = getTreeUri(context)
        if (tree != null) {
            val name = runCatching { DocumentFile.fromTreeUri(context, tree)?.name }.getOrNull()
            // 2026-07-16 fix: 예전엔 폴더 안 **파일 전부**를 세서(오디오인지·해석되는지 안 봄) "N개 발견"이
            //   거짓말이었다 → "찾았다면서 왜 못 찾냐". 실제로 쓸 수 있는 녹음만 센다(요약이 쓰는 기준과 동일).
            val n = runCatching { listCandidates(context).count { isUsableRecording(it.name) } }.getOrDefault(0)
            return "폴더: ${name ?: "직접 연결한 폴더"} · 녹음 ${n}개 발견"
        }
        if (isMediaStoreEnabled(context) && hasAudioPermission(context)) {
            val n = runCatching { countMediaStoreCandidates(context) }.getOrDefault(0)
            return "자동 찾기 · 녹음 ${n}개 발견"
        }
        return null
    }

    private fun getTreeUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    /**
     * 통합 후보 목록 — 폴더가 직접 연결돼 있으면 그 폴더, 아니면 (자동 찾기 켜졌고 권한 있으면) MediaStore.
     *   둘 다 아니면 빈 목록. 호출부(스캔 루프)는 이 목록만 돌면 된다.
     */
    private fun listCandidates(context: Context): List<RecFile> {
        val fromTree = listFromTree(context)
        // 2026-07-16 fix: 예전엔 폴더가 연결돼 있으면 **무조건** 그 폴더만 봤다 → 한 번 잘못/빈 폴더를 고르면
        //   나중에 "자동으로 찾기"를 켜도 MediaStore 를 영영 안 봐서 계속 "녹음 못 찾음". 폴더에 쓸 녹음이
        //   하나도 없으면 자동 찾기로 폴백한다(폴더 우선은 유지 — 사장님이 직접 고른 게 1순위).
        if (fromTree.any { isUsableRecording(it.name) }) return fromTree
        if (isMediaStoreEnabled(context) && hasAudioPermission(context)) {
            return listFromMediaStore(context).ifEmpty { fromTree }
        }
        return fromTree
    }

    private fun listFromTree(context: Context): List<RecFile> {
        val treeUri = getTreeUri(context) ?: return emptyList()
        val tree = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull() ?: return emptyList()
        if (!tree.isDirectory) return emptyList()
        return runCatching {
            tree.listFiles().mapNotNull { f ->
                if (!f.isFile) return@mapNotNull null
                val n = f.name ?: return@mapNotNull null
                RecFile(f.uri.toString(), n)
            }
        }.getOrDefault(emptyList())
    }

    /** 요약에 실제로 쓸 수 있는 녹음인가 = 오디오 + 파일명에서 최소한 '시각'은 나온다. 개수 표시도 이 기준. */
    private fun isUsableRecording(name: String): Boolean =
        isAudioName(name) && AdotFilenameParser.parseLoose(name) != null

    private fun isAudioName(name: String): Boolean =
        name.endsWith(".m4a", true) || name.endsWith(".mp3", true) || name.endsWith(".wav", true)

    /**
     * 권한/연결이 있으면 백그라운드에서 신규 파일 import. UI 블록하지 않음.
     * @param onComplete (importedCount) — 수동 스캔에서 결과 표시용. 자동 스캔은 null.
     */
    fun scanIfConnected(
        context: Context,
        container: AppContainer,
        onComplete: ((Int) -> Unit)? = null
    ) {
        if (!isConnected(context)) {
            onComplete?.invoke(0)
            return
        }
        val appCtx = context.applicationContext
        scope.launch {
            val count = runCatching { scanInternal(appCtx, container) }.getOrDefault(0)
            // Toast 등 UI 콜백은 반드시 Main 스레드에서 호출.
            withContext(Dispatchers.Main) {
                onComplete?.invoke(count)
            }
        }
    }

    private suspend fun scanInternal(
        context: Context,
        container: AppContainer
    ): Int {
        // 폴더 연결 시점 이후 녹음만 import. 그 전의 과거 녹음은 무시.
        // 기존 사용자(connectedAt 미저장)는 이번 스캔 시각을 기준으로 자동 설정 → 이후엔 신규만 import.
        var connectedAt = connectedAt(context)
        if (connectedAt == 0L) {
            connectedAt = System.currentTimeMillis()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_CONNECTED_AT, connectedAt)
                .apply()
        }

        var imported = 0
        for (rf in listCandidates(context)) {
            val name = rf.name
            if (!isAudioName(name)) continue

            // 파일명에서 녹음 시각 추출. 패턴 안 맞으면 안전하게 스킵.
            //   (2026-07-16) 번호가 없어도 시각만 나오면 통과 — 번호는 RecordingMatcher 가 통화기록으로 되찾는다.
            val parsed = AdotFilenameParser.parseLoose(name) ?: continue
            // 연결 시점보다 오래된 파일은 무시 (과거 통화 묶음 차단).
            if (parsed.recordedAt < connectedAt) continue

            // URI 기준 중복 체크 — 이미 import한 파일은 스킵
            if (container.recordingRepository.existsByUri(rf.uriStr)) continue

            runCatching {
                RecordingMatcher.attach(
                    container = container,
                    fileUri = rf.uriStr,
                    displayName = name,
                    sourceType = RecordingSourceType.SHARED_FROM_ADOT
                )
                imported++
            }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SCAN, System.currentTimeMillis())
            .apply()

        return imported
    }

    fun lastScanAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_SCAN, 0L)

    /**
     * 통화종료 자동 스캔용 — 연결 시점 이후의 새 녹음을 import + **서버 STT+요약(비대화형)**.
     *   scanInternal(=import만) 과 달리 요약까지 한다. 이미 요약 있으면(텍스트 경로 등) 스킵 → 재과금 방지.
     *   결과를 await(suspend). @return 새로 요약한 건수.
     */
    suspend fun scanAndSummarizeNow(context: Context, container: AppContainer): Int {
        if (!isConnected(context)) return 0
        val appCtx = context.applicationContext

        var connectedAt = connectedAt(appCtx)
        if (connectedAt == 0L) {
            connectedAt = System.currentTimeMillis()
            appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_CONNECTED_AT, connectedAt).apply()
        }

        var summarized = 0
        for (rf in listCandidates(appCtx)) {
            val name = rf.name
            if (!isAudioName(name)) continue
            val loose = AdotFilenameParser.parseLoose(name) ?: continue
            if (loose.recordedAt < connectedAt) continue   // 연결 전 옛 통화 무시

            // 번호가 파일명에 없으면(연락처 이름만 든 녹음) 그 시각에 하던 통화에서 번호를 되찾는다.
            //   못 찾거나 애매하면 건너뜀 — 엉뚱한 고객에 붙이느니 안 붙인다. (2026-07-16 사장님 현장)
            val phone = loose.phoneNumber
                ?: container.callRecordRepository.findCallAtTime(loose.recordedAt)?.phoneNumber
                ?: continue

            val uriStr = rf.uriStr
            // 녹음 첨부(없을 때만) — 기존 자동 import 와 동일하게 고객/통화기록 연결.
            if (!container.recordingRepository.existsByUri(uriStr)) {
                runCatching {
                    RecordingMatcher.attach(container, uriStr, name, RecordingSourceType.SHARED_FROM_ADOT)
                }
            }
            // 이미 요약 있으면 스킵(텍스트 경로가 먼저 요약했을 수 있음) — 비용 0.
            if (container.callSummaryRepository.findExistingNear(phone, loose.recordedAt) != null) continue
            // 서버 받아쓰기+요약 (비대화형: 묻지 않음). 번호 없는 파일은 되찾은 번호·시각을 넘긴다.
            val ok = runCatching {
                CallAudioSummarizer.summarizeAndSave(
                    appCtx, container, uriStr, name, interactive = false, notifyOnComplete = true,
                    phoneOverride = if (loose.phoneNumber == null) phone else null,
                    recordedAtOverride = if (loose.phoneNumber == null) loose.recordedAt else null
                )
            }.getOrDefault(false)
            if (ok) summarized++
        }

        appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SCAN, System.currentTimeMillis()).apply()
        return summarized
    }

    /**
     * 특정 번호의 과거 녹음을 백필.
     * scanInternal 과 달리 cutoff(연결 시점) 필터를 적용하지 않는다 — 사용자가 명시적으로
     * 그 번호의 통화 이력을 끌어오는 의도이므로 과거 데이터도 가져온다.
     *
     * 매칭: 파일명의 전화번호 끝 8자리가 입력 번호 끝 8자리와 일치하면 OK.
     */
    fun scanByPhone(
        context: Context,
        container: AppContainer,
        phoneNumber: String,
        customerId: Long,
        onComplete: ((Int) -> Unit)? = null
    ) {
        if (!isConnected(context)) {
            onComplete?.invoke(0)
            return
        }
        val target = phoneNumber.filter { it.isDigit() }.takeLast(8)
        if (target.length < 7) {
            onComplete?.invoke(0)
            return
        }
        val appCtx = context.applicationContext
        scope.launch {
            val count = runCatching {
                var imported = 0
                for (rf in listCandidates(appCtx)) {
                    val name = rf.name
                    if (!isAudioName(name)) continue
                    val parsed = AdotFilenameParser.parse(name) ?: continue
                    if (parsed.phoneNumber.takeLast(8) != target) continue

                    val uriStr = rf.uriStr
                    if (container.recordingRepository.existsByUri(uriStr)) continue

                    container.recordingRepository.add(
                        fileUri = uriStr,
                        fileName = name,
                        sourceType = RecordingSourceType.SHARED_FROM_ADOT,
                        customerId = customerId,
                        phoneNumber = parsed.phoneNumber
                    )
                    imported++
                }
                imported
            }.getOrDefault(0)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(count)
            }
        }
    }

    /** 탭-요약 결과. NO_FILE=폴더에 매칭 녹음 자체가 없음 / FAILED=파일은 찾았으나 요약 실패(서버·네트워크·오디오읽기). */
    enum class SummarizeResult { OK, ALREADY, NO_FILE, NO_FOLDER, FAILED }

    /**
     * 특정 통화 한 건을 폴더/MediaStore 에서 찾아 즉시 요약(채팅에서 통화 카드 탭). 연결 시점 cutoff 무시(사용자 명시 의도).
     *   매칭 = 파일명 번호 끝 8자리 일치 + 녹음시각이 통화시각 ±30분. 가장 가까운 파일을 요약.
     *   에이닷 들어가 '공유' 안 해도, 연결된 소스에서 알아서 찾아 요약. (2026-06-14 사장님)
     *
     * 2단계 매칭 (2026-07-16 사장님 현장 — `통화 남이편_260716_112558.m4a`):
     *  ① 이름에 **번호**가 있는 파일 → 예전 그대로(번호 끝 8자리 + ±30분).
     *  ② ①이 없으면 이름에 **번호가 없는**(연락처 이름만 든) 파일 → [CallRecordRepository.findCallAtTime] 으로
     *     "그 시각에 하던 통화"가 **바로 이 통화**일 때만 쓴다. 폰은 한 번에 한 통화만 하므로 시각이 곧 신원.
     */
    suspend fun summarizeCallNow(
        context: Context,
        container: AppContainer,
        phoneNumber: String,
        callAtMs: Long,
        callRecordId: Long? = null
    ): SummarizeResult {
        if (!isConnected(context)) return SummarizeResult.NO_FOLDER
        val appCtx = context.applicationContext
        val target = phoneNumber.filter { it.isDigit() }.takeLast(8)
        if (target.length < 7) return SummarizeResult.NO_FILE
        val win = 30 * 60 * 1000L
        var bestUri: String? = null
        var bestName = ""
        var bestAt = 0L
        var bestDelta = Long.MAX_VALUE
        val candidates = listCandidates(appCtx).filter { isAudioName(it.name) }
        for (rf in candidates) {
            val parsed = AdotFilenameParser.parse(rf.name) ?: continue
            if (parsed.phoneNumber.takeLast(8) != target) continue
            val delta = kotlin.math.abs(parsed.recordedAt - callAtMs)
            if (delta <= win && delta < bestDelta) {
                bestUri = rf.uriStr; bestName = rf.name; bestAt = parsed.recordedAt; bestDelta = delta
            }
        }
        // ② 번호 없는 녹음 — 시각으로 되찾기. ①로 찾았으면 건너뜀(번호가 확실한 쪽이 항상 우선).
        var looseMatch = false
        if (bestUri == null) {
            for (rf in candidates) {
                val loose = AdotFilenameParser.parseLoose(rf.name) ?: continue
                if (loose.phoneNumber != null) continue          // 번호 있는 건 ①에서 이미 판정 끝
                val delta = kotlin.math.abs(loose.recordedAt - callAtMs)
                if (delta > win || delta >= bestDelta) continue
                // 이 시각의 통화가 '탭한 그 통화'가 맞는지 통화기록으로 확인 — 아니면 안 붙인다(엉뚱한 고객 방지).
                val owner = container.callRecordRepository.findCallAtTime(loose.recordedAt) ?: continue
                val sameCall = if (callRecordId != null) owner.id == callRecordId
                else owner.phoneNumber.filter { it.isDigit() }.takeLast(8) == target
                if (!sameCall) continue
                bestUri = rf.uriStr; bestName = rf.name; bestAt = loose.recordedAt; bestDelta = delta
                looseMatch = true
            }
        }
        val uriStr = bestUri ?: return SummarizeResult.NO_FILE
        // 이미 요약돼 있으면 재과금 없이 스킵. 단, 시각 드리프트로 '탭한 통화카드'엔 안 붙어 있을 수 있어
        //   사용자가 직접 탭한 그 통화기록(callRecordId)에 강제 연결 → 카드에 즉시 표시.
        //   (2026-06-18 버그: "이미 요약돼 있어요" 토스트인데 화면엔 요약이 안 보임.
        //    원인 = ALREADY 판정은 녹음시각 ±2분, 채팅 표시는 통화시각 ±10분 페어링이라 창이 어긋남.)
        container.callSummaryRepository.findExistingNear(phoneNumber, bestAt)?.let { existing ->
            linkToCall(container, phoneNumber, existing, callRecordId)
            return SummarizeResult.ALREADY
        }
        if (!container.recordingRepository.existsByUri(uriStr)) {
            runCatching { RecordingMatcher.attach(container, uriStr, bestName, RecordingSourceType.SHARED_FROM_ADOT) }
        }
        val ok = runCatching {
            // notifyOnComplete=true — 탭으로 요약해도 끝나면 "막내가 OO님 통화 요약했어요!" 푸시(앱 켜져 있어도). (2026-06-18 사장님)
            // ②(번호 없는 파일)일 때만 번호·시각을 넘긴다 — ①은 기존 동작 그대로 두려고 override 안 씀.
            CallAudioSummarizer.summarizeAndSave(
                appCtx, container, uriStr, bestName, interactive = false, notifyOnComplete = true,
                phoneOverride = if (looseMatch) phoneNumber else null,
                recordedAtOverride = if (looseMatch) bestAt else null
            )
        }.getOrDefault(false)
        // 파일은 찾았으나 요약 실패 = 서버(/api/call-audio-summary, 맥미니 Whisper STT) 오류·네트워크·오디오 읽기 실패 등.
        //   이걸 NO_FILE("녹음 못 찾음")로 뭉뚱그리면 진짜 원인(주로 서버)을 가려 오진됨
        //   (2026-06-20 하우스픽 테스터: NO_FILE 토스트였지만 실은 서버 audio-summary 실패 의심). → FAILED 로 분리.
        if (!ok) return SummarizeResult.FAILED
        // 새로 저장한 요약도 같은 이유(시각 드리프트)로 탭한 카드에 확실히 붙도록 직접 연결.
        container.callSummaryRepository.findExistingNear(phoneNumber, bestAt)?.let { saved ->
            linkToCall(container, phoneNumber, saved, callRecordId)
        }
        return SummarizeResult.OK
    }

    /**
     * 탭으로 요약/확인한 통화카드에 요약을 확실히 표시 — 그 통화기록 id 로 직접 연결.
     *   채팅 화면은 callRecordId 연결을 1순위로 페어링하므로, 시각이 ±10분을 벗어나도 카드에 바로 붙는다.
     *
     * ★ 단, 같은 번호 통화가 여러 건이면(예: 5:49 통화 + 0:11 통화) 이 요약(recordedAt)에 '가장 가까운'
     *   통화가 탭한 통화일 때만 연결한다. 안 그러면 탭할 때마다 요약이 엉뚱한 카드로 끌려가 서로 뒤바뀜.
     *   (2026-06-18 사장님: 5:49 요약하니 11초짜리 내용이 들어오고, 11초 누르면 그게 위로 올라가 뒤바뀜)
     *   이미 같은 통화에 연결돼 있거나 callRecordId 가 없으면 아무 것도 안 함.
     */
    private suspend fun linkToCall(
        container: AppContainer,
        phoneNumber: String,
        summary: com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity,
        callRecordId: Long?
    ) {
        if (callRecordId == null || summary.callRecordId == callRecordId) return
        // 이 요약 시각에 가장 가까운 통화가 '탭한 통화'가 아니면, 그 요약은 다른 통화 것 → 옮기지 않음.
        val sRec = summary.recordedAt
        if (sRec != null) {
            val calls = runCatching {
                container.callRecordRepository.observeByPhone(phoneNumber).first()
            }.getOrDefault(emptyList())
            val closest = calls.minByOrNull { kotlin.math.abs((it.startedAt ?: it.endedAt) - sRec) }
            if (closest != null && closest.id != callRecordId) return
        }
        runCatching {
            container.callSummaryRepository.update(
                summary.copy(callRecordId = callRecordId, updatedAt = System.currentTimeMillis())
            )
        }
    }
}
