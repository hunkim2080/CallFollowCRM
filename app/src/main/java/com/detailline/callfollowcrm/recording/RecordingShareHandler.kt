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
            var skipped = 0
            // 공유한 통화의 번호 — 처리 후 그 번호 채팅을 자동으로 연다(사장님 요청: 공유 후 찾기 번거로움).
            var openPhone: String? = null
            var openCustomerId: Long? = null
            val seenUris = mutableSetOf<String>()
            // 요약 대상(번호 인식된 것만) — 저장 먼저 끝내고, 토스트 후 백그라운드 요약.
            val toSummarize = mutableListOf<Pair<String, String>>()  // (uri, fileName)
            uris.forEach { uri ->
                runCatching {
                    val uriKey = uri.toString()
                    // 한 번의 공유 안에서 같은 URI 가 두 번 들어온 경우만 건너뛴다.
                    // 주의: DB 의 existsByUri 로 막지 않는다 — 에이닷 공유 URI 는 녹음마다 고유한
                    //   식별자가 아니라 캐시 파일명을 재사용할 수 있어서, 한 번 저장되면 이후 모든
                    //   공유가 "이미 저장됨"으로 잘못 막힌다(사장님 "갑자기 안 됨" 원인, 2026-06-10).
                    //   진짜 자동 import 중복은 AdotFolderScanner 가 안정적인 트리 URI 로 따로 거른다.
                    if (!seenUris.add(uriKey)) {
                        skipped++
                        return@runCatching
                    }
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
                        // 여러 개 공유 시 첫 번째 인식된 번호의 채팅을 연다.
                        if (openPhone == null) {
                            openPhone = result.phoneNumber
                            openCustomerId = result.customerId
                        }
                    }
                    if (result.customerId != null) linked++
                }
            }

            val message = buildString {
                val saved = uris.size - skipped
                append("녹음 ${saved}개 받았어요")
                if (linked > 0) append(" · 고객 통화방에 정리해뒀어요")
                if (skipped > 0) append(" · 이미 있는 ${skipped}개는 건너뛰었어요")
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(appCtx, message, Toast.LENGTH_LONG).show()
            }

            // 인식된 번호가 있으면 그 채팅을 바로 연다. 요약(아래)은 백그라운드로 이어지며,
            //   채팅 통화카드가 "통화 내용 요약 중…" 스피너로 진행 상태를 보여준다(CallSummaryProgress).
            openPhone?.let { container.navEvents.requestChat(it, openCustomerId) }

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
                withContext(Dispatchers.Main) {
                    if (summarized > 0) {
                        Toast.makeText(appCtx, "통화 요약 ${summarized}개 완료", Toast.LENGTH_LONG).show()
                    } else {
                        // 서버 요약 실패(예: 502) — 녹음은 이미 저장됨. 무반응이면 "안됨"으로 보이므로 안내.
                        Toast.makeText(
                            appCtx,
                            "통화 요약 서버 오류 — 녹음은 저장됐어요. 잠시 후 다시 시도해 주세요.",
                            Toast.LENGTH_LONG
                        ).show()
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
