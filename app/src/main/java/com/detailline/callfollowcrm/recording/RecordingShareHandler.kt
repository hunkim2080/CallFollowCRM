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
                    if (result.phoneNumber != null) matched++
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
