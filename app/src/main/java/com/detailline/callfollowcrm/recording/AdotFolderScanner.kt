package com.detailline.callfollowcrm.recording

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.model.RecordingSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 에이닷 통화녹음 폴더(TPhoneCallRecords)를 한 번만 사용자에게 권한 받고,
 * 이후 앱 켤 때마다 새 파일을 자동 import.
 *
 * 흐름:
 *  1) 사용자가 Settings 에서 "에이닷 폴더 연결" 누름
 *  2) 시스템 OpenDocumentTree 다이얼로그 → TPhoneCallRecords 선택
 *  3) 우리는 그 treeUri 를 SharedPreferences 에 저장하고 persistable 권한 take
 *  4) 다음에 앱 켤 때 scanIfConnected() 가 폴더 안의 .m4a 파일들 중
 *     아직 우리 DB에 없는 것들을 RecordingMatcher 로 추가
 */
object AdotFolderScanner {

    private const val PREFS = "adot_scanner"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_LAST_SCAN = "last_scan"
    private const val KEY_CONNECTED_AT = "connected_at"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            .apply()
    }

    fun isConnected(context: Context): Boolean = getTreeUri(context) != null

    private fun getTreeUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    /**
     * 권한이 있으면 백그라운드에서 신규 파일 import. UI 블록하지 않음.
     * @param onComplete (importedCount) — 수동 스캔에서 결과 표시용. 자동 스캔은 null.
     */
    fun scanIfConnected(
        context: Context,
        container: AppContainer,
        onComplete: ((Int) -> Unit)? = null
    ) {
        val treeUri = getTreeUri(context)
        if (treeUri == null) {
            onComplete?.invoke(0)
            return
        }
        val appCtx = context.applicationContext
        scope.launch {
            val count = runCatching { scanInternal(appCtx, container, treeUri) }
                .getOrDefault(0)
            // Toast 등 UI 콜백은 반드시 Main 스레드에서 호출.
            withContext(Dispatchers.Main) {
                onComplete?.invoke(count)
            }
        }
    }

    private suspend fun scanInternal(
        context: Context,
        container: AppContainer,
        treeUri: Uri
    ): Int {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        if (!tree.isDirectory) return 0

        // 폴더 연결 시점 이후 녹음만 import. 그 전의 과거 녹음은 무시.
        // 기존 사용자(connectedAt 미저장)는 이번 스캔 시각을 기준으로 자동 설정 → 이후엔 신규만 import.
        var connectedAt = connectedAt(context)
        if (connectedAt == 0L) {
            connectedAt = System.currentTimeMillis()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_CONNECTED_AT, connectedAt)
                .apply()
        }

        val files = tree.listFiles()
        var imported = 0
        for (f in files) {
            if (!f.isFile) continue
            val name = f.name ?: continue
            if (!name.endsWith(".m4a", ignoreCase = true) &&
                !name.endsWith(".mp3", ignoreCase = true) &&
                !name.endsWith(".wav", ignoreCase = true)
            ) continue

            // 파일명에서 녹음 시각 추출. 패턴 안 맞으면 안전하게 스킵.
            val parsed = AdotFilenameParser.parse(name) ?: continue
            // 연결 시점보다 오래된 파일은 무시 (과거 통화 묶음 차단).
            if (parsed.recordedAt < connectedAt) continue

            val uriStr = f.uri.toString()

            // URI 기준 중복 체크 — 이미 import한 파일은 스킵
            if (container.recordingRepository.existsByUri(uriStr)) continue

            runCatching {
                RecordingMatcher.attach(
                    container = container,
                    fileUri = uriStr,
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
        val treeUri = getTreeUri(context) ?: return 0
        val appCtx = context.applicationContext
        val tree = DocumentFile.fromTreeUri(appCtx, treeUri) ?: return 0
        if (!tree.isDirectory) return 0

        var connectedAt = connectedAt(appCtx)
        if (connectedAt == 0L) {
            connectedAt = System.currentTimeMillis()
            appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_CONNECTED_AT, connectedAt).apply()
        }

        var summarized = 0
        for (f in tree.listFiles()) {
            if (!f.isFile) continue
            val name = f.name ?: continue
            if (!name.endsWith(".m4a", ignoreCase = true) &&
                !name.endsWith(".mp3", ignoreCase = true) &&
                !name.endsWith(".wav", ignoreCase = true)
            ) continue
            val parsed = AdotFilenameParser.parse(name) ?: continue
            if (parsed.recordedAt < connectedAt) continue   // 연결 전 옛 통화 무시

            val uriStr = f.uri.toString()
            // 녹음 첨부(없을 때만) — 기존 자동 import 와 동일하게 고객/통화기록 연결.
            if (!container.recordingRepository.existsByUri(uriStr)) {
                runCatching {
                    RecordingMatcher.attach(container, uriStr, name, RecordingSourceType.SHARED_FROM_ADOT)
                }
            }
            // 이미 요약 있으면 스킵(텍스트 경로가 먼저 요약했을 수 있음) — 비용 0.
            if (container.callSummaryRepository.findExistingNear(parsed.phoneNumber, parsed.recordedAt) != null) continue
            // 서버 받아쓰기+요약 (비대화형: 묻지 않음).
            val ok = runCatching {
                CallAudioSummarizer.summarizeAndSave(appCtx, container, uriStr, name, interactive = false, notifyOnComplete = true)
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
        val treeUri = getTreeUri(context)
        if (treeUri == null) {
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
                val tree = DocumentFile.fromTreeUri(appCtx, treeUri) ?: return@runCatching 0
                if (!tree.isDirectory) return@runCatching 0
                var imported = 0
                for (f in tree.listFiles()) {
                    if (!f.isFile) continue
                    val name = f.name ?: continue
                    if (!name.endsWith(".m4a", ignoreCase = true) &&
                        !name.endsWith(".mp3", ignoreCase = true) &&
                        !name.endsWith(".wav", ignoreCase = true)
                    ) continue
                    val parsed = AdotFilenameParser.parse(name) ?: continue
                    if (parsed.phoneNumber.takeLast(8) != target) continue

                    val uriStr = f.uri.toString()
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

    /** 탭-요약 결과. */
    enum class SummarizeResult { OK, ALREADY, NO_FILE, NO_FOLDER }

    /**
     * 특정 통화 한 건을 폴더에서 찾아 즉시 요약(채팅에서 통화 카드 탭). 연결 시점 cutoff 무시(사용자 명시 의도).
     *   매칭 = 파일명 번호 끝 8자리 일치 + 녹음시각이 통화시각 ±30분. 가장 가까운 파일을 요약.
     *   에이닷 들어가 '공유' 안 해도, 연결된 폴더에서 알아서 찾아 요약. (2026-06-14 사장님)
     */
    suspend fun summarizeCallNow(
        context: Context,
        container: AppContainer,
        phoneNumber: String,
        callAtMs: Long
    ): SummarizeResult {
        val treeUri = getTreeUri(context) ?: return SummarizeResult.NO_FOLDER
        val appCtx = context.applicationContext
        val tree = DocumentFile.fromTreeUri(appCtx, treeUri) ?: return SummarizeResult.NO_FOLDER
        if (!tree.isDirectory) return SummarizeResult.NO_FOLDER
        val target = phoneNumber.filter { it.isDigit() }.takeLast(8)
        if (target.length < 7) return SummarizeResult.NO_FILE
        val win = 30 * 60 * 1000L
        var bestUri: String? = null
        var bestName = ""
        var bestAt = 0L
        var bestDelta = Long.MAX_VALUE
        for (f in tree.listFiles()) {
            if (!f.isFile) continue
            val name = f.name ?: continue
            if (!name.endsWith(".m4a", true) && !name.endsWith(".mp3", true) && !name.endsWith(".wav", true)) continue
            val parsed = AdotFilenameParser.parse(name) ?: continue
            if (parsed.phoneNumber.takeLast(8) != target) continue
            val delta = kotlin.math.abs(parsed.recordedAt - callAtMs)
            if (delta <= win && delta < bestDelta) {
                bestUri = f.uri.toString(); bestName = name; bestAt = parsed.recordedAt; bestDelta = delta
            }
        }
        val uriStr = bestUri ?: return SummarizeResult.NO_FILE
        if (container.callSummaryRepository.findExistingNear(phoneNumber, bestAt) != null) return SummarizeResult.ALREADY
        if (!container.recordingRepository.existsByUri(uriStr)) {
            runCatching { RecordingMatcher.attach(container, uriStr, bestName, RecordingSourceType.SHARED_FROM_ADOT) }
        }
        val ok = runCatching {
            CallAudioSummarizer.summarizeAndSave(appCtx, container, uriStr, bestName, interactive = false)
        }.getOrDefault(false)
        return if (ok) SummarizeResult.OK else SummarizeResult.NO_FILE
    }
}
