package com.detailline.callfollowcrm.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.domain.model.MessageStatus
import com.detailline.callfollowcrm.presentation.overlay.PostCallCard
import com.detailline.callfollowcrm.recording.AdotFolderScanner
import com.detailline.callfollowcrm.recording.AdotTextFolderScanner
import com.detailline.callfollowcrm.recording.CallSummaryProgress
import com.detailline.callfollowcrm.util.PermissionHelper
import com.detailline.callfollowcrm.util.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 통화 직후 띄우는 PostCallCard 오버레이 매니저.
 *
 * 정책:
 *  - 한 번에 하나만. 카드가 떠있는 동안 새 통화가 들어와도 새 카드는 안 띄움
 *    (기존 카드 닫혀야 다음 카드 가능).
 *  - 자동응답이 ON 이면 카드 안에 카운트다운 + 취소 버튼. 카운트다운 끝나면 발송 + 카드는
 *    "✓ 발송됨" 으로 변하지만 계속 열려 있음 (사장님이 leadHeat/메모 계속 입력 가능).
 *  - 자동응답이 OFF 이면 템플릿 알약 버튼 노출. 탭하면 3초 카운트다운 후 발송.
 *  - 30초간 인터랙션 없으면 자동 닫힘 (zombie 카드 방지).
 */
object PostCallOverlayManager {

    private const val AUTO_DISMISS_MS = 30_000L
    private const val AUTO_REPLY_COUNTDOWN_MS = 10_000L
    private const val MANUAL_SEND_COUNTDOWN_MS = 3_000L

    private val main = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentView: View? = null
    private var currentOwner: PostCallOverlayLifecycleOwner? = null
    private var currentArgs: OverlayArgs? = null
    private var countdownJob: Job? = null
    private var autoDismissJob: Job? = null
    private var summaryScanJob: Job? = null
    private var summaryObserveJob: Job? = null
    private var summaryTimeoutJob: Job? = null

    private val _state = MutableStateFlow<CardState?>(null)
    val state = _state.asStateFlow()

    fun isShowing(): Boolean = currentView != null

    /**
     * 카드 띄우기 시도. 권한 없거나 이미 다른 카드 떠있으면 false 반환 (호출자는 fallback 처리).
     */
    fun showOrIgnore(context: Context, args: OverlayArgs): Boolean {
        if (!PermissionHelper.hasOverlay(context)) return false
        if (currentView != null) return false
        main.post { actuallyShow(context.applicationContext, args) }
        return true
    }

    fun hide() {
        main.post { actuallyHide() }
    }

    // ----- internal state mutations called from card UI -----

    /** 후속 문자 초안 다듬기 — 입력 중 텍스트 갱신. */
    internal fun onEditDraft(text: String) {
        _state.update { it?.copy(draftText = text) }
        resetAutoDismiss()
    }

    /** [다듬기] 토글 — 읽기 ↔ 편집. */
    internal fun onToggleEditDraft() {
        _state.update { it?.copy(draftEditing = !(it.draftEditing)) }
        resetAutoDismiss()
    }

    /** [문자 보내기] — 현재 초안을 고객에게 발송. */
    internal fun onSendDraft() {
        val args = currentArgs ?: return
        val body = _state.value?.draftText?.trim().orEmpty()
        if (body.isBlank()) return
        resetAutoDismiss()
        ioScope.launch {
            val app = appOrNull() ?: return@launch
            val ctx = currentView?.context ?: return@launch
            val customer = runCatching {
                app.container.customerRepository.upsertByPhone(phoneNumber = args.phoneNumber)
            }.getOrNull()
            val ok = SmsSender.sendDirect(ctx.applicationContext, args.phoneNumber, body)
            app.container.messageHistoryRepository.recordAutoSend(
                phoneNumber = args.phoneNumber,
                customerId = customer?.id,
                templateId = null,
                body = body,
                status = if (ok) MessageStatus.INLINE_SENT else MessageStatus.INLINE_FAILED
            )
            _state.update { it?.copy(draftEditing = false, draftSent = ok, draftFailed = !ok) }
        }
    }

    internal fun onCancelAutoReply() {
        val args = currentArgs ?: return
        countdownJob?.cancel()
        // AutoReplyScheduler 가 이 callRecordId 로 schedule 했을 수도 있어 같이 취소 신호 (fallback 경로용).
        AutoReplyScheduler.cancel(args.callRecordId)
        // ON 모드를 OFF 모드(템플릿 칩 노출) 로 전환. 사장님이 다른 템플릿 골라 보낼 수 있게.
        _state.update { it?.copy(sendStatus = SendStatus.CANCELLED, mode = CardMode.MANUAL_CHOOSE) }
        resetAutoDismiss()
    }

    internal fun onPickTemplate(template: MessageTemplateEntity) {
        val args = currentArgs ?: return
        // 같은 패턴: 3초 카운트다운 + 취소 가능
        countdownJob?.cancel()
        _state.update { it?.copy(
            sendStatus = SendStatus.COUNTING_DOWN,
            pendingTemplateId = template.id,
            pendingTemplateBody = template.body,
            pendingTemplateTitle = template.title,
            countdownMs = MANUAL_SEND_COUNTDOWN_MS
        ) }
        countdownJob = ioScope.launch {
            tickdown(MANUAL_SEND_COUNTDOWN_MS)
            // 취소 안 됐으면 실제 발송
            if (_state.value?.sendStatus == SendStatus.COUNTING_DOWN) {
                actuallySend(args.phoneNumber, template.id, template.body)
            }
        }
        resetAutoDismiss()
    }

    internal fun onCancelManualSend() {
        countdownJob?.cancel()
        _state.update { it?.copy(
            sendStatus = SendStatus.IDLE,
            pendingTemplateId = null,
            pendingTemplateBody = null,
            pendingTemplateTitle = null,
            countdownMs = 0L
        ) }
        resetAutoDismiss()
    }

    internal fun onCloseTapped() {
        hide()
    }

    // ----- 통화 요약 스트리밍 (수신/발신 통화) -----

    /**
     * 카드가 떠 있는 동안 이 통화의 요약이 준비되면 카드에 채운다.
     *   - 즉시 + 몇 초 간격으로 폴더 스캔(에이닷 파일 쓰기 지연 대응) → 워커보다 빠르게.
     *   - callSummaryRepository(번호 suffix) + CallSummaryProgress 를 관찰해 READY/LOADING 갱신.
     *   - 75초 안에 못 가져오면 UNAVAILABLE(템플릿 폴백).
     */
    private fun startSummaryStreaming(phone: String, openedAtMs: Long) {
        val suffix = phone.filter { it.isDigit() }.takeLast(8)
        if (suffix.length < 7) { _state.update { it?.copy(summaryStatus = SummaryStatus.UNAVAILABLE) }; return }
        val app = appOrNull() ?: return
        val ctx = currentView?.context?.applicationContext ?: return

        // 빠른 결과를 위해 카드 쪽에서도 스캔을 몇 번 시도(중복은 dedup 으로 무해).
        summaryScanJob = ioScope.launch {
            for (waitMs in listOf(3_000L, 9_000L, 20_000L)) {
                delay(waitMs)
                if (_state.value?.summaryStatus != SummaryStatus.LOADING) return@launch
                runCatching { AdotTextFolderScanner.scanNow(ctx, app.container) }
                runCatching { AdotFolderScanner.scanAndSummarizeNow(ctx, app.container) }
            }
        }

        summaryObserveJob = ioScope.launch {
            combine(
                app.container.callSummaryRepository.observeByPhoneSuffix(suffix),
                CallSummaryProgress.inProgress
            ) { summaries, inProg -> summaries to inProg }.collect { (summaries, _) ->
                // 이 통화(카드 연 시점 ±30분)의 최신 요약.
                val match = summaries
                    .filter { (it.recordedAt ?: it.updatedAt) >= openedAtMs - 30 * 60 * 1000L }
                    .maxByOrNull { it.recordedAt ?: it.updatedAt }
                if (match != null && !match.summaryText.isNullOrBlank()) {
                    val bullets = summaryBulletsOf(match.title, match.summaryText)
                    val draft = match.recommendedMessage?.trim().orEmpty()
                    _state.update {
                        it?.copy(
                            summaryStatus = SummaryStatus.READY,
                            summaryBullets = bullets,
                            // 사용자가 이미 다듬는 중이면 덮어쓰지 않음.
                            draftText = if (it.draftEditing || it.draftSent) it.draftText else draft
                        )
                    }
                    resetAutoDismiss()
                    return@collect
                }
            }
        }

        summaryTimeoutJob = ioScope.launch {
            delay(75_000)
            if (_state.value?.summaryStatus == SummaryStatus.LOADING) {
                _state.update { it?.copy(summaryStatus = SummaryStatus.UNAVAILABLE) }
            }
        }
    }

    /** summaryText("한 줄\n불릿\n불릿") → 불릿 리스트(제목 줄 제외, 기호 제거). 비면 제목 한 줄. */
    private fun summaryBulletsOf(title: String?, summaryText: String): List<String> {
        val t = title?.trim()
        val lines = summaryText.split("\n")
            .map { it.trim().removePrefix("•").removePrefix("-").trim() }
            .filter { it.isNotBlank() }
        val bullets = lines.filter { it != t }
        return bullets.ifEmpty { lines }.ifEmpty { listOfNotNull(t) }
    }

    // ----- private impl -----

    @SuppressLint("InflateParams")
    private fun actuallyShow(appContext: Context, args: OverlayArgs) {
        val app = appContext as? CallFollowCrmApplication ?: return

        // 자동응답 ON 모드인지 판정 — 자동응답 정책 + 권한 + 발송할 본문이 있으면 OK.
        // 2026-06-03 fix: 기존엔 템플릿ID(>0)를 요구했는데, 자동문자 설정은 인라인 문구에만 저장 →
        //   부재중은 템플릿ID 가 늘 -1 이라 자동발송이 영영 안 됐음. 이제 본문 유무로 판정.
        val autoOn = run {
            if (!app.container.preferences.autoFirstReplyEnabled) return@run false
            if (!SmsSender.hasPermission(appContext)) return@run false
            !args.autoReplyTemplateBody.isNullOrBlank()
        }

        val initialMode = if (autoOn) CardMode.AUTO_REPLY else CardMode.MANUAL_CHOOSE
        currentArgs = args

        _state.value = CardState(
            phoneNumber = args.phoneNumber,
            isMissed = args.isMissed,
            mode = initialMode,
            sendStatus = if (autoOn) SendStatus.COUNTING_DOWN else SendStatus.IDLE,
            countdownMs = if (autoOn) AUTO_REPLY_COUNTDOWN_MS else 0L,
            autoReplyTemplateTitle = args.autoReplyTemplateTitle,
            manualTemplates = args.manualTemplates,
            customerId = null,
            // 수신/발신(대화 있음)만 요약 스트리밍. 부재중은 요약 안 함(섹션 미표시).
            summaryStatus = if (args.isMissed) SummaryStatus.UNAVAILABLE else SummaryStatus.LOADING
        )

        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val owner = PostCallOverlayLifecycleOwner().also { it.onCreate(); it.onStart(); it.onResume() }
        val composeView = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val cardState by state.collectAsState()
                cardState?.let {
                    PostCallCard(
                        state = it,
                        onCancelAutoReply = ::onCancelAutoReply,
                        onPickTemplate = ::onPickTemplate,
                        onCancelManualSend = ::onCancelManualSend,
                        onEditDraft = ::onEditDraft,
                        onToggleEditDraft = ::onToggleEditDraft,
                        onSendDraft = ::onSendDraft,
                        onClose = ::onCloseTapped
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_TOUCH_MODAL: 카드 밖 영역의 터치는 아래 앱으로 그대로 패스 (사장님이 다른 작업 가능)
            // FLAG_ALT_FOCUSABLE_IM: 메모 입력 시 IME 가 정상 표시되도록.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        runCatching { wm.addView(composeView, params) }
            .onSuccess {
                currentView = composeView
                currentOwner = owner
                startAutoDismiss()
                // 수신/발신(대화 있음) → 통화 요약을 스트리밍으로 카드에 채움.
                if (!args.isMissed) {
                    startSummaryStreaming(args.phoneNumber, System.currentTimeMillis())
                }
                if (autoOn) {
                    countdownJob = ioScope.launch {
                        tickdown(AUTO_REPLY_COUNTDOWN_MS)
                        if (_state.value?.sendStatus == SendStatus.COUNTING_DOWN) {
                            val body = args.autoReplyTemplateBody.orEmpty()
                            if (body.isBlank()) return@launch
                            // 템플릿ID 없이 인라인 문구만 있어도 발송 (actuallySend 는 tplId<=0 처리됨).
                            actuallySend(args.phoneNumber, args.autoReplyTemplateId ?: -1L, body)
                        }
                    }
                }
            }
            .onFailure {
                // 권한이 미묘하게 빠지거나 윈도우 추가 실패 시 안전하게 무시.
                currentArgs = null
                _state.value = null
            }
    }

    private fun actuallyHide() {
        countdownJob?.cancel()
        countdownJob = null
        autoDismissJob?.cancel()
        autoDismissJob = null
        summaryScanJob?.cancel(); summaryScanJob = null
        summaryObserveJob?.cancel(); summaryObserveJob = null
        summaryTimeoutJob?.cancel(); summaryTimeoutJob = null
        currentView?.let { v ->
            runCatching {
                val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeView(v)
            }
        }
        currentOwner?.onDestroy()
        currentOwner = null
        currentView = null
        currentArgs = null
        _state.value = null
    }

    private fun startAutoDismiss() {
        autoDismissJob?.cancel()
        autoDismissJob = ioScope.launch {
            delay(AUTO_DISMISS_MS)
            // 카운트다운 진행 중이거나 통화 요약을 아직 정리 중이면 카드 유지.
            val st = _state.value
            if (st?.sendStatus == SendStatus.COUNTING_DOWN) return@launch
            if (st?.summaryStatus == SummaryStatus.LOADING) return@launch
            withContext(Dispatchers.Main) { actuallyHide() }
        }
    }

    private fun resetAutoDismiss() {
        startAutoDismiss()
    }

    private suspend fun tickdown(totalMs: Long) {
        var remaining = totalMs
        val step = 250L
        while (remaining > 0) {
            _state.update { it?.copy(countdownMs = remaining) }
            delay(step)
            remaining -= step
        }
        _state.update { it?.copy(countdownMs = 0L) }
    }

    private suspend fun actuallySend(phone: String, templateId: Long, body: String) {
        val app = appOrNull() ?: return
        val ctx = currentView?.context ?: return

        // 2026-05-25: status 인자 제거 — 카테고리 시스템으로 통일.
        val customer = runCatching {
            app.container.customerRepository.upsertByPhone(phoneNumber = phone)
        }.getOrNull()

        val ok = SmsSender.sendDirect(ctx.applicationContext, phone, body)
        app.container.messageHistoryRepository.recordAutoSend(
            phoneNumber = phone,
            customerId = customer?.id,
            templateId = templateId.takeIf { it > 0 },
            body = body,
            status = if (ok) MessageStatus.AUTO_SENT else MessageStatus.AUTO_FAILED
        )
        _state.update { it?.copy(
            sendStatus = if (ok) SendStatus.SENT else SendStatus.FAILED,
            countdownMs = 0L
        ) }
    }

    private fun appOrNull(): CallFollowCrmApplication? =
        currentView?.context?.applicationContext as? CallFollowCrmApplication
}

// ----- data classes -----

data class OverlayArgs(
    val callRecordId: Long,
    val phoneNumber: String,
    val isMissed: Boolean,
    val autoReplyTemplateId: Long?,
    val autoReplyTemplateTitle: String?,
    val autoReplyTemplateBody: String?,
    val manualTemplates: List<MessageTemplateEntity>
)

enum class CardMode { AUTO_REPLY, MANUAL_CHOOSE }
enum class SendStatus { IDLE, COUNTING_DOWN, SENT, CANCELLED, FAILED }

/** 통화 요약 상태 — 정리 중 / 준비됨 / 못 가져옴(템플릿 폴백). */
enum class SummaryStatus { LOADING, READY, UNAVAILABLE }

data class CardState(
    val phoneNumber: String,
    val isMissed: Boolean,
    val mode: CardMode,
    val sendStatus: SendStatus,
    val countdownMs: Long,
    val autoReplyTemplateTitle: String?,
    val manualTemplates: List<MessageTemplateEntity>,
    val customerId: Long?,
    val pendingTemplateId: Long? = null,
    val pendingTemplateBody: String? = null,
    val pendingTemplateTitle: String? = null,
    // ── 통화 요약(수신/발신) ──
    val summaryStatus: SummaryStatus = SummaryStatus.LOADING,
    val summaryBullets: List<String> = emptyList(),
    val draftText: String = "",
    val draftEditing: Boolean = false,
    val draftSent: Boolean = false,
    val draftFailed: Boolean = false
)

// ----- custom lifecycle owner for the ComposeView in WindowManager -----

internal class PostCallOverlayLifecycleOwner :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() { lifecycleRegistry.currentState = Lifecycle.State.STARTED }
    fun onResume() { lifecycleRegistry.currentState = Lifecycle.State.RESUMED }
    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

