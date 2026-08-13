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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

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
                        is IncomingIntent.OpenCustomer -> container.navEvents.requestCustomerDetail(incoming.customerId)
                        is IncomingIntent.CollabMine -> container.navEvents.requestCollabSites(tab = "shared")
                        is IncomingIntent.OpenTeam -> container.navEvents.requestTeam()
                        is IncomingIntent.OpenRecurringDue -> container.navEvents.requestRecurringDue()
                        is IncomingIntent.CollabEnded -> {
                            // 무엇이 해제됐는지 명확히 — 해제된 현장은 목록서 빠지므로 토스트로 알려줌 + 협업 현장 목록 열기. (2026-06-21 사장님)
                            val site = incoming.title.takeIf { it.isNotBlank() } ?: "협업 현장"
                            val who = incoming.byName.takeIf { it.isNotBlank() }?.let { "$it 님이 " } ?: ""
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "${who}「$site」 협업을 해제했어요. 사진·기록은 남아있어요.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            container.navEvents.requestCollabSites()
                        }
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

    override fun onResume() {
        super.onResume()
        // 앱 진입(포그라운드)마다 베타 사용 핑 — 서버가 use_count++/last_seen 갱신 → admin '최근 앱 실행/사용 수' 실시간.
        //   캐싱 없이 매번 호출(cowork 요청 2026-06-21). 통계용이라 결과로 진입 막지 않음(fail-open). bizPhone 없으면 skip.
        val container = (application as? CallFollowCrmApplication)?.container ?: return
        val phone = container.preferences.bizPhone
        if (phone.filter { it.isDigit() }.length >= 9) {
            lifecycleScope.launch { runCatching { container.betaCheckRepository.check(phone) } }
            // 앱이 앞으로 올 때 협업 진행(완료+계좌) 즉시 1회 폴 — 60초 주기 안 기다리고 바로 받기. (2026-06-23 사장님: 완료 후 계좌 늦게 옴)
            lifecycleScope.launch {
                runCatching { container.collabEventCenter.poll(applicationContext) }
                runCatching { container.collabEventCenter.pollInvites(applicationContext) }
            }
        }
    }

    // 마우스 hover(커서가 떠 있는 상태) 이벤트를 Compose 로 보내지 않음 — Compose 1.6.x 의
    //   "ACTION_HOVER_EXIT event was not cleared" 크래시 회피(삼성 + 마우스/미러링/DeX 에서만 발생).
    //   우린 터치 앱이라 hover 효과 안 쓰고, 휠 스크롤(ACTION_SCROLL)은 그대로 통과 → 스크롤은 정상. (2026-06-23 사장님)
    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_HOVER_ENTER,
            android.view.MotionEvent.ACTION_HOVER_MOVE,
            android.view.MotionEvent.ACTION_HOVER_EXIT -> return true
        }
        return super.dispatchGenericMotionEvent(ev)
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
                // 통화 후 템플릿 선택 등 — 본문 prefill(빈 draft 일 때만). 채팅 열리며 입력창에 채워짐, 확인 후 발송. (2026-07-12 사장님)
                val prefill = intent.getStringExtra(EXTRA_PREFILL_BODY)?.takeIf { it.isNotBlank() }
                // 사진은 "\n" 조인 목록(최대 5). (2026-07-12 사장님)
                val prefillPhotos = intent.getStringExtra(EXTRA_PREFILL_PHOTO)
                    ?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
                if (phone.isNotBlank() && (prefill != null || prefillPhotos.isNotEmpty())) {
                    val container = (application as CallFollowCrmApplication).container
                    if (prefill != null && container.chatDraftStore.get(phone).isEmpty()) container.chatDraftStore.set(phone, prefill)
                    if (prefillPhotos.isNotEmpty()) container.chatDraftStore.setPhotos(phone, prefillPhotos)
                }
                pendingIntentState.value = IncomingIntent.Chat(phone, customerId)
            }
            ACTION_CALL_SUMMARY -> {
                val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty()
                val name = intent.getStringExtra(EXTRA_DISPLAY_NAME)?.takeIf { it.isNotBlank() }
                if (phone.isNotBlank()) pendingIntentState.value = IncomingIntent.CallSummary(phone, name)
            }
            ACTION_DAILY_BRIEF -> {
                val bDay = intent.getLongExtra(EXTRA_BRIEF_DAY, 0L)
                if (bDay > 0L) (application as CallFollowCrmApplication).container.preferences.pendingBriefDayMs = bDay // 자정 넘겨 탭해도 그날 브리핑 (VM 이 읽고 지움)
                pendingIntentState.value = IncomingIntent.ClosingBrief
            }
            ACTION_TEAM -> { pendingIntentState.value = IncomingIntent.OpenTeam }
            ACTION_RECURRING_DUE -> { pendingIntentState.value = IncomingIntent.OpenRecurringDue }
            ACTION_COLLAB_MINE -> {
                // 협업 수락/진행 알림(주인 A가 받음) 탭 → 협업 현장 "내가 공유한 현장" 탭. (2026-06-20 사장님)
                pendingIntentState.value = IncomingIntent.CollabMine
            }
            ACTION_COLLAB_SITE -> {
                // 협업 댓글/사진 알림 탭 → shareId 가 내 고객(내가 공유한 현장)이면 **고객정보 협업 탭**으로 바로(사장님이 늘 쓰는 자리).
                //   아니면(내가 협업자 B) 협업 현장 상세로. (2026-07-02 사장님)
                val sid = intent.getStringExtra(EXTRA_SHARE_ID).orEmpty()
                val cid = customerIdForShareId(sid)
                pendingIntentState.value =
                    if (cid != null) IncomingIntent.OpenCustomer(cid)
                    else IncomingIntent.SharedSite(sid.takeIf { it.isNotBlank() })
            }
            ACTION_COLLAB_ENDED -> {
                // 협업 해제 알림 탭 → 무엇이 해제됐는지 토스트 + 목록. (2026-06-21 사장님)
                pendingIntentState.value = IncomingIntent.CollabEnded(
                    intent.getStringExtra(EXTRA_COLLAB_TITLE).orEmpty(),
                    intent.getStringExtra(EXTRA_COLLAB_BY).orEmpty()
                )
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
        /** 협업 댓글/사진 알림 탭 → 그 현장이 내 고객이면 고객정보로 바로. (2026-07-02 사장님) */
        data class OpenCustomer(val customerId: Long) : IncomingIntent
        /** 협업 수락/진행 알림(주인 A가 받음) 탭 → 협업 현장 "내가 공유한 현장" 탭. (2026-06-20 사장님) */
        object CollabMine : IncomingIntent
        /** 협업 해제 알림 탭 → 무엇이/누가 해제했는지 안내 + 목록. (2026-06-21 사장님) */
        data class CollabEnded(val title: String, val byName: String) : IncomingIntent
        /** 팀원 진행 알림 탭 → 팀 관리(현황). (2026-08-13) */
        object OpenTeam : IncomingIntent
        /** 정기문자 due 알림 탭 → 정기문자 검토. (2026-08-13) */
        object OpenRecurringDue : IncomingIntent
    }

    /** shareId → 내 고객 id. collabAssignments("customerId|phone|name|shareId")에 있으면 내가 주인 → 그 고객 반환. 없으면 null(=협업자 B → 협업현장). */
    private fun customerIdForShareId(shareId: String): Long? {
        if (shareId.isBlank()) return null
        val app = application as? CallFollowCrmApplication ?: return null
        return app.container.preferences.collabAssignments.firstNotNullOfOrNull { e ->
            val parts = e.split("|")
            if (parts.size >= 4 && parts[3] == shareId) parts[0].toLongOrNull() else null
        }
    }

    companion object {
        const val ACTION_FOLLOW_UP = "com.detailline.callfollowcrm.ACTION_FOLLOW_UP"
        const val ACTION_CHAT = "com.detailline.callfollowcrm.ACTION_CHAT"
        const val ACTION_CALL_SUMMARY = "com.detailline.callfollowcrm.ACTION_CALL_SUMMARY"
        /** 팀원 진행 알림 탭 → 팀 관리(현황). (2026-08-13 알림 위치점검) */
        const val ACTION_TEAM = "com.detailline.callfollowcrm.ACTION_TEAM"
        /** 정기문자 due 알림 탭 → 정기문자 검토. (2026-08-13 알림 위치점검) */
        const val ACTION_RECURRING_DUE = "com.detailline.callfollowcrm.ACTION_RECURRING_DUE"
        const val ACTION_DAILY_BRIEF = "com.detailline.callfollowcrm.ACTION_DAILY_BRIEF"
        const val EXTRA_BRIEF_DAY = "brief_day_ms"
        const val ACTION_COLLAB_MINE = "com.detailline.callfollowcrm.ACTION_COLLAB_MINE"
        const val ACTION_COLLAB_ENDED = "com.detailline.callfollowcrm.ACTION_COLLAB_ENDED"
        /** 협업 현장 특정 shareId 로 바로 상세 열기(댓글 등). EXTRA_SHARE_ID 로 shareId 전달. (2026-07-02 사장님) */
        const val ACTION_COLLAB_SITE = "com.detailline.callfollowcrm.ACTION_COLLAB_SITE"
        const val EXTRA_SHARE_ID = "extra_share_id"
        const val EXTRA_COLLAB_TITLE = "extra_collab_title"
        const val EXTRA_COLLAB_BY = "extra_collab_by"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_RECORD_ID = "extra_call_record_id"
        const val EXTRA_TEMPLATE_ID = "extra_template_id"
        /** 채팅 열면서 입력창에 미리 채울 본문(통화 후 템플릿 선택 등). (2026-07-12) */
        const val EXTRA_PREFILL_BODY = "extra_prefill_body"
        /** 채팅 열면서 미리 첨부할 사진 URI(통화 후 템플릿 사진). (2026-07-12) */
        const val EXTRA_PREFILL_PHOTO = "extra_prefill_photo"
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
    }
}
