package com.detailline.callfollowcrm.recording

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 에이닷 "통화 내용 텍스트 저장" 으로 떨어지는 .txt 통화요약 폴더(Download/A.phone)를
 * 한 번만 사용자에게 권한 받고, 이후 앱 켤 때마다 새 파일을 자동 import.
 *
 * 녹음(m4a) 자동 가져오기([AdotFolderScanner])와 같은 방식이지만 대상이 다르다:
 *  - 폴더: Download/A.phone  (녹음은 보통 Music/TPhoneCallRecords)
 *  - 파일: {전화번호}_{yyyyMMddHHmmss}.txt  (이름규칙은 녹음과 동일 → [AdotFilenameParser] 공용)
 *  - 처리: 본문(요약+녹취록)을 읽어 [AdotSummaryImporter] 로 CallSummary 저장 + 서버 요약
 *
 * 중요 포인트:
 *  1) **인코딩** — 에이닷 txt 는 UTF-8 이 아니라 CP949(MS949/EUC-KR)로 저장됨. 그대로 읽으면 다 깨진다.
 *     UTF-8 로 먼저 시도(미래 대비) → 실패하면 MS949 → EUC-KR 순으로 디코드.
 *  2) **중복 안 함(=재과금 방지)** — 파일명의 전화번호+시각으로 이미 저장된 요약이 있으면 스킵.
 *     (이게 없으면 스캔할 때마다 같은 파일을 서버로 다시 보내 비용 발생.)
 *  3) **cutoff 없음** — 녹음과 달리, txt 는 사용자가 에이닷에서 "텍스트 저장"을 명시적으로 누른 것만 생긴다.
 *     즉 폴더에 있는 모든 txt 는 의도된 것이므로 통화 날짜와 무관하게 전부 가져온다(중복만 거름).
 */
object AdotTextFolderScanner {

    private const val TAG = "AdotTextScan"
    private const val PREFS = "adot_text_scanner"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_LAST_SCAN = "last_scan"
    private const val KEY_CONNECTED_AT = "connected_at"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 동시 스캔 방지 — 서버 요약(~수초)이 "중복판정 ~ insert" 사이에 끼면, 겹친 두 스캔이 같은
    //   파일을 각각 insert 하는 race 발생(첫 연결 시 launcher 스캔 + onCreate 스캔이 겹침). 한 번에 하나만.
    @Volatile
    private var scanning = false

    /** Settings/채팅에서 OpenDocumentTree 결과로 얻은 URI 를 저장 + persistable 권한 take. */
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

    fun connectedAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_CONNECTED_AT, 0L)

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

    fun lastScanAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_SCAN, 0L)

    /**
     * 권한이 있으면 백그라운드에서 신규 txt import. UI 블록하지 않음.
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
        if (scanning) {            // 이미 스캔 중이면 중복 트리거 무시(race 방지).
            onComplete?.invoke(0)
            return
        }
        scanning = true
        val appCtx = context.applicationContext
        scope.launch {
            val count = try {
                runCatching { scanInternal(appCtx, container, treeUri) }
                    .onFailure { Log.w(TAG, "scan failed", it) }
                    .getOrDefault(0)
            } finally {
                scanning = false
            }
            withContext(Dispatchers.Main) { onComplete?.invoke(count) }
        }
    }

    private suspend fun scanInternal(
        context: Context,
        container: AppContainer,
        treeUri: Uri
    ): Int {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        if (!tree.isDirectory) return 0

        var imported = 0
        for (f in tree.listFiles()) {
            if (!f.isFile) continue
            val name = f.name ?: continue
            if (!name.endsWith(".txt", ignoreCase = true)) continue

            // 파일명에서 전화번호+시각 추출. 패턴 안 맞으면 스킵(중복판정 불가 → 재과금 위험).
            val parsed = AdotFilenameParser.parse(name) ?: continue

            // 이미 저장된 통화요약이면 스킵 — 서버 재호출(비용) 방지.
            if (container.callSummaryRepository.findExistingNear(parsed.phoneNumber, parsed.recordedAt) != null) continue

            val text = readTextKorean(context, f.uri) ?: continue
            if (text.isBlank()) continue

            runCatching {
                AdotSummaryImporter.importTextFile(
                    container = container,
                    text = text,
                    knownPhone = parsed.phoneNumber,
                    knownRecordedAt = parsed.recordedAt
                )
                imported++
            }.onFailure { Log.w(TAG, "import failed: $name", it) }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SCAN, System.currentTimeMillis())
            .apply()

        Log.d(TAG, "scan done: imported=$imported")
        return imported
    }

    /**
     * 에이닷 txt 읽기. CP949(MS949/EUC-KR)로 저장돼 있어 그대로 UTF-8 로 읽으면 깨진다.
     * UTF-8(엄격) 먼저 시도 → 실패하면 한글 코드페이지로 디코드.
     */
    private fun readTextKorean(context: Context, uri: Uri): String? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        if (bytes.isEmpty()) return ""

        // 1) UTF-8 엄격 디코드 — 깨진 바이트가 하나라도 있으면 예외 → 한글 코드페이지로.
        runCatching {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val s = decoder.decode(ByteBuffer.wrap(bytes)).toString()
            return s.removePrefix("﻿")  // BOM 제거
        }

        // 2) CP949 계열 fallback (에이닷 실제 케이스).
        for (cs in listOf("MS949", "EUC-KR", "x-windows-949")) {
            runCatching {
                return Charset.forName(cs).decode(ByteBuffer.wrap(bytes)).toString()
            }
        }
        // 3) 최후 — 플랫폼 기본.
        return String(bytes)
    }
}
