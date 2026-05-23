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
import com.detailline.callfollowcrm.recording.AdotFolderScanner
import com.detailline.callfollowcrm.recording.AdotSummaryImporter
import com.detailline.callfollowcrm.recording.RecordingShareHandler

class MainActivity : ComponentActivity() {

    private val pendingIntentState = mutableStateOf<IncomingIntent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncoming(intent)

        // 앱 켤 때마다 에이닷 폴더 자동 스캔 (권한 받은 경우에만)
        val container = (application as CallFollowCrmApplication).container
        AdotFolderScanner.scanIfConnected(applicationContext, container)

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
    }

    companion object {
        const val ACTION_FOLLOW_UP = "com.detailline.callfollowcrm.ACTION_FOLLOW_UP"
        const val ACTION_CHAT = "com.detailline.callfollowcrm.ACTION_CHAT"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_RECORD_ID = "extra_call_record_id"
        const val EXTRA_TEMPLATE_ID = "extra_template_id"
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
    }
}
