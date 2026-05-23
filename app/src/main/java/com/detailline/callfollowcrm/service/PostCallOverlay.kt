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
import com.detailline.callfollowcrm.domain.model.CustomerStatus
import com.detailline.callfollowcrm.domain.model.LeadHeat
import com.detailline.callfollowcrm.domain.model.MessageStatus
import com.detailline.callfollowcrm.presentation.overlay.PostCallCard
import com.detailline.callfollowcrm.util.PermissionHelper
import com.detailline.callfollowcrm.util.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    internal fun onLeadHeatPicked(heat: LeadHeat) {
        val args = currentArgs ?: return
        _state.update { it?.copy(leadHeat = heat) }
        resetAutoDismiss()
        ioScope.launch {
            val app = appOrNull() ?: return@launch
            // status 인자 = null → 기존 status 가 있으면 보존, 신규 생성 시엔 NEW_INQUIRY 가 기본.
            // (드물게 동일 번호의 customer 가 이미 다른 단계 status 일 수 있어 강등 방지.)
            val customer = app.container.customerRepository.upsertByPhone(
                phoneNumber = args.phoneNumber,
                leadHeat = heat
            )
            _state.update { it?.copy(customerId = customer.id) }
        }
    }

    internal fun onMemoChanged(text: String) {
        _state.update { it?.copy(memo = text) }
        resetAutoDismiss()
        // 메모는 debounce 가 필요하지만 간단히 매 변경 시 저장 (텍스트 짧고, 위험 없음).
        val args = currentArgs ?: return
        ioScope.launch {
            val app = appOrNull() ?: return@launch
            // 첫 메모 입력 시 Customer 가 아직 없을 수 있으므로 upsert. status 보존.
            val customer = app.container.customerRepository.upsertByPhone(
                phoneNumber = args.phoneNumber
            )
            app.container.customerRepository.updateMemo(customer.id, text)
            _state.update { it?.copy(customerId = customer.id) }
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

    // ----- private impl -----

    @SuppressLint("InflateParams")
    private fun actuallyShow(appContext: Context, args: OverlayArgs) {
        val app = appContext as? CallFollowCrmApplication ?: return

        // 자동응답 ON 모드인지 판정 — 자동응답 정책 + 권한 + 해당 케이스 템플릿 모두 OK 여야.
        val autoOn = run {
            if (!app.container.preferences.autoFirstReplyEnabled) return@run false
            if (!SmsSender.hasPermission(appContext)) return@run false
            args.autoReplyTemplateId != null && args.autoReplyTemplateId > 0
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
            leadHeat = null,
            memo = ""
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
                        onPickLeadHeat = ::onLeadHeatPicked,
                        onMemoChange = ::onMemoChanged,
                        onCancelAutoReply = ::onCancelAutoReply,
                        onPickTemplate = ::onPickTemplate,
                        onCancelManualSend = ::onCancelManualSend,
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
                if (autoOn) {
                    countdownJob = ioScope.launch {
                        tickdown(AUTO_REPLY_COUNTDOWN_MS)
                        if (_state.value?.sendStatus == SendStatus.COUNTING_DOWN) {
                            val tplId = args.autoReplyTemplateId ?: return@launch
                            actuallySend(args.phoneNumber, tplId, args.autoReplyTemplateBody.orEmpty())
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
            // 카운트다운이 진행 중이면 카드 유지 (발송이 끝날 때까지)
            val st = _state.value
            if (st?.sendStatus == SendStatus.COUNTING_DOWN) return@launch
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

        val customer = runCatching {
            app.container.customerRepository.upsertByPhone(
                phoneNumber = phone,
                status = CustomerStatus.NEW_INQUIRY
            )
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

data class CardState(
    val phoneNumber: String,
    val isMissed: Boolean,
    val mode: CardMode,
    val sendStatus: SendStatus,
    val countdownMs: Long,
    val autoReplyTemplateTitle: String?,
    val manualTemplates: List<MessageTemplateEntity>,
    val customerId: Long?,
    val leadHeat: LeadHeat?,
    val memo: String,
    val pendingTemplateId: Long? = null,
    val pendingTemplateBody: String? = null,
    val pendingTemplateTitle: String? = null
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

