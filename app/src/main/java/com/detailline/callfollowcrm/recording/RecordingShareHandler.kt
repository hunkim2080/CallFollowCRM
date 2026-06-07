package com.detailline.callfollowcrm.recording

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.model.RecordingSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android share intent / 수동 파일 선택으로 들어온 audio 파일 URI 를 RecordingAttachment 로 저장한다.
 *
 * 자동 매칭:
 *  - 파일명이 에이닷 패턴(전화번호_시간.m4a)이면 고객 자동 연결
 *  - 매칭 실패해도 unlinked 상태로 저장됨 (앱이 죽지 않음)
 */
object RecordingShareHandler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handleShared(
        context: Context,
        container: AppContainer,
        uris: List<Uri>,
        displayName: String?
    ) {
        if (uris.isEmpty()) return
        val appCtx = context.applicationContext
        scope.launch {
            var matched = 0
            var linked = 0
            // 요약 대상(번호 인식된 것만) — 저장 먼저 끝내고, 토스트 후 백그라운드 요약.
            val toSummarize = mutableListOf<Pair<String, String>>()  // (uri, fileName)
            uris.forEach { uri ->
                runCatching {
                    val name = displayName
                        ?: queryDisplayName(appCtx, uri)
                        ?: uri.lastPathSegment.orEmpty()
                    val result = RecordingMatcher.attach(
                        container = container,
                        fileUri = uri.toString(),
                        displayName = name,
                        sourceType = RecordingSourceType.SHARED_FROM_ADOT
                    )
                    if (result.phoneNumber != null) {
                        matched++
                        toSummarize.add(uri.toString() to name)
                    }
                    if (result.customerId != null) linked++
                }
            }

            val message = buildString {
                append("녹음 ${uris.size}개 저장")
                if (matched > 0) append(" · 번호 인식 ${matched}건")
                if (linked > 0) append(" · 기존 고객 연결 ${linked}건")
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(appCtx, message, Toast.LENGTH_LONG).show()
            }

            // 녹음 → 맥미니 §26 (로컬 Whisper 받아쓰기 + 요약) → CallSummary. 통화카드 "AI 요약됨" 으로 표시.
            //   STT 가 통화 길이에 비례(수~수십초)라 저장 토스트 후 백그라운드로 진행.
            if (toSummarize.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appCtx, "통화 내용 요약 중… (잠시 후 통화카드에 떠요)", Toast.LENGTH_SHORT).show()
                }
                var summarized = 0
                toSummarize.forEach { (u, n) ->
                    runCatching {
                        if (CallAudioSummarizer.summarizeAndSave(appCtx, container, u, n)) summarized++
                    }
                }
                if (summarized > 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appCtx, "통화 요약 ${summarized}개 완료", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun handleManualPick(
        container: AppContainer,
        context: Context,
        uri: Uri,
        customerId: Long? = null
    ) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { /* persistable 아님 */ }

        val appCtx = context.applicationContext
        scope.launch {
            val name = queryDisplayName(appCtx, uri) ?: uri.lastPathSegment.orEmpty()
            if (customerId != null) {
                container.recordingRepository.add(
                    fileUri = uri.toString(),
                    fileName = name,
                    sourceType = RecordingSourceType.MANUAL_PICK,
                    customerId = customerId
                )
            } else {
                RecordingMatcher.attach(
                    container = container,
                    fileUri = uri.toString(),
                    displayName = name,
                    sourceType = RecordingSourceType.MANUAL_PICK
                )
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }
}
