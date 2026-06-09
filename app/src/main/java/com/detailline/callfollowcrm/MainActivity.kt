package com.detailline.callfollowcrm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.detailline.callfollowcrm.presentation.AppRoot
import com.detailline.callfollowcrm.recording.AdotSummaryImporter
import com.detailline.callfollowcrm.recording.AdotTextFolderScanner
import com.detailline.callfollowcrm.recording.RecordingShareHandler

class MainActivity : ComponentActivity() {

    private val pendingIntentState = mutableStateOf<IncomingIntent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 2026-05-24: setDecorFitsSystemWindows(false) 박았다가 다른 화면들의 bottomBar 가
        // nav bar 영역에 잘리는 문제 발생 → 롤백. manifest 의 adjustResize 가 시스템 측에서
        // 키보드 처리. 각 화면은 Scaffold inner padding + 필요 시 imePadding 으로 대응.
        handleIncoming(intent)

        val container = (application as CallFollowCrmApplication).container

        // 에이닷 "통화 내용 텍스트 저장" 폴더(Download/A.phone)가 연결돼 있으면, 앱 켤 때마다
        //   새 txt 통화요약을 자동 import. 폴더 미연결이면 즉시 no-op. (연결은 채팅 통화카드에서 1회.)
        AdotTextFolderScanner.scanIfConnected(this, container)

        setContent {
            val pending = remember { pendingIntentState }
            LaunchedEffect(pending.value) {
                pending.value?.let { incoming ->
                    when (incoming) {
                        is IncomingIntent.FollowUp -> container.navEvents.requestFollowUp(
                            incoming.phoneNumber,
                            incoming.callRecordId,
                            incoming.templateId
                        )
                        is IncomingIntent.Chat -> container.navEvents.requestChat(
                            incoming.phoneNumber,
                            incoming.customerId
                        )
                        is IncomingIntent.CallSummary -> container.navEvents.requestCallSummary(
                            incoming.phoneNumber,
                            incoming.displayName
                        )
                        is IncomingIntent.ClosingBrief -> container.navEvents.requestClosingBrief()
                        is IncomingIntent.SharedAudio -> RecordingShareHandler.handleShared(
                            context = this@MainActivity,
                            container = container,
                            uris = incoming.uris,
                            displayName = incoming.displayName
                        )
                        is IncomingIntent.SharedText -> AdotSummaryImporter.importFromShare(
                            context = this@MainActivity,
                            container = container,
                            text = incoming.text
                        )
                        is IncomingIntent.SharedSite -> container.navEvents.requestCollabSites(incoming.shareId)
                    }
                    pending.value = null
                }
            }
            AppRoot(container = container)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            ACTION_FOLLOW_UP -> {
                val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty()
                val callRecordId = intent.getLongExtra(EXTRA_CALL_RECORD_ID, -1L).takeIf { it > 0 }
                val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, -1L).takeIf { it > 0 }
                pendingIntentState.value = IncomingIntent.FollowUp(phone, callRecordId, templateId)
            }
            ACTION_CHAT -> {
                val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty()
                val customerId = intent.getLongExtra(EXTRA_CUSTOMER_ID, -1L).takeIf { it > 0 }
                pendingIntentState.value = IncomingIntent.Chat(phone, customerId)
            }
            ACTION_CALL_SUMMARY -> {
                val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty()
                val name = intent.getStringExtra(EXTRA_DISPLAY_NAME)?.takeIf { it.isNotBlank() }
                if (phone.isNotBlank()) pendingIntentState.value = IncomingIntent.CallSummary(phone, name)
            }
            ACTION_DAILY_BRIEF -> {
                pendingIntentState.value = IncomingIntent.ClosingBrief
            }
            Intent.ACTION_SEND -> {
                val mime = intent.type.orEmpty()
                if (mime.startsWith("audio/")) {
                    @Suppress("DEPRECATION")
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) {
                        pendingIntentState.value = IncomingIntent.SharedAudio(
                            listOf(uri),
                            intent.getStringExtra(Intent.EXTRA_TITLE)
                        )
                    }
                } else if (mime.startsWith("text/")) {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrBlank()) {
                        pendingIntentState.value = IncomingIntent.SharedText(text)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) {
                    pendingIntentState.value = IncomingIntent.SharedAudio(uris.toList(), null)
                }
            }
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                // 2026-05-29 Phase A 1단계 — Default SMS 자격 4/4: 외부 앱이 "sms:번호" 로 launch.
                //   예: 연락처 앱 → 문자 보내기 → RING-GO 선택 → 여기로 옴.
                //   data uri: sms:01012345678[?body=hi], smsto:..., mms:..., mmsto:...
                //   phone 만 추출해 ChatScreen 으로 trampoline. body 가 있고 draft 비어있으면 prefill.
                val uri = intent.data ?: return
                val scheme = uri.scheme?.lowercase()
                // 협업 현장 공유 App Link: https://api.si0in.kr/shared/{share_id} (또는 si0in.kr)
                //   → 협업 현장 화면(그 현장 자동 열기). share_id = 경로 마지막 조각.
                if (intent.action == Intent.ACTION_VIEW && scheme == "https" &&
                    (uri.host == "api.si0in.kr" || uri.host == "si0in.kr") &&
                    uri.path?.startsWith("/shared/") == true
                ) {
                    val shareId = uri.lastPathSegment?.takeIf { it.isNotBlank() }
                    pendingIntentState.value = IncomingIntent.SharedSite(shareId)
                    return
                }
                // 협업 링크 HTML fallback: shigongmagne://shared/{share_id}
                if (intent.action == Intent.ACTION_VIEW && scheme == "shigongmagne" && uri.host == "shared") {
                    val shareId = uri.lastPathSegment?.takeIf { it.isNotBlank() }
                    pendingIntentState.value = IncomingIntent.SharedSite(shareId)
                    return
                }
                if (scheme !in setOf("sms", "smsto", "mms", "mmsto")) return
                val phone = uri.schemeSpecificPart
                    ?.substringBefore('?')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() } ?: return
                // body: query param "body" 또는 EXTRA_TEXT (앱마다 다름).
                val bodyFromQuery = runCatching { uri.getQueryParameter("body") }.getOrNull()
                val bodyFromExtra = intent.getStringExtra(Intent.EXTRA_TEXT)
                val body = (bodyFromQuery ?: bodyFromExtra)?.takeIf { it.isNotBlank() }
                if (body != null) {
                    val container = (application as CallFollowCrmApplication).container
                    // 사장님 작성 draft 안 덮어쓰기 — 빈 draft 일 때만 prefill.
                    if (container.chatDraftStore.get(phone).isEmpty()) {
                        container.chatDraftStore.set(phone, body)
                    }
                }
                pendingIntentState.value = IncomingIntent.Chat(phone, null)
            }
        }
    }

    private sealed interface IncomingIntent {
        data class FollowUp(
            val phoneNumber: String,
            val callRecordId: Long?,
            val templateId: Long? = null
        ) : IncomingIntent
        data class Chat(
            val phoneNumber: String,
            val customerId: Long?
        ) : IncomingIntent
        data class SharedAudio(val uris: List<Uri>, val displayName: String?) : IncomingIntent
        data class SharedText(val text: String) : IncomingIntent
        data class CallSummary(val phoneNumber: String, val displayName: String?) : IncomingIntent
        object ClosingBrief : IncomingIntent
        /** 협업 현장 공유 App Link. shareId = /shared/{share_id} 의 마지막 조각(없으면 목록). */
        data class SharedSite(val shareId: String?) : IncomingIntent
    }

    companion object {
        const val ACTION_FOLLOW_UP = "com.detailline.callfollowcrm.ACTION_FOLLOW_UP"
        const val ACTION_CHAT = "com.detailline.callfollowcrm.ACTION_CHAT"
        const val ACTION_CALL_SUMMARY = "com.detailline.callfollowcrm.ACTION_CALL_SUMMARY"
        const val ACTION_DAILY_BRIEF = "com.detailline.callfollowcrm.ACTION_DAILY_BRIEF"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_RECORD_ID = "extra_call_record_id"
        const val EXTRA_TEMPLATE_ID = "extra_template_id"
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
    }
}
