package com.detailline.callfollowcrm.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.detailline.callfollowcrm.MainActivity
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PermissionHelper
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 전화 오는 순간(벨) 통화화면 위에 "상대 정보 카드"를 띄우는 오버레이 매니저. (2026-07-01 사장님)
 *
 * 목적: 전화를 받기 전에 "이 사람 누구/무슨 얘기 오갔는지"를 알아차리게. (시나리오: 일정 잡힌 고객·문자만 한 고객이 전화 오는데 번호로는 못 알아봄.)
 *
 * 동작:
 *  - CallStateReceiver 가 RINGING 을 잡으면 [onRinging] 호출 → 번호로 고객·시공일정·최근 대화를 즉시 조회해 카드 표시.
 *  - 통화 응답(OFFHOOK) 또는 종료(IDLE) 되면 [onCallGone] → 카드 제거.
 *  - "다른 앱 위에 표시"(SYSTEM_ALERT_WINDOW) 권한 + 설정 토글 ON 일 때만 뜬다. 아니면 조용히 무시.
 *  - 모든 전화에 뜸(모르는 번호 포함). 기록 없으면 "처음 보는 번호" 로 표시(사장님 2026-07-01 선택).
 *
 * 재활용: WindowManager + ComposeView + 커스텀 LifecycleOwner 플럼빙은 예전 PostCallOverlay(제거됨) 패턴을 그대로 따름.
 */
object IncomingCallOverlay {

    private const val SAFETY_TIMEOUT_MS = 60_000L   // 혹시 IDLE 을 놓쳐도 좀비 카드 방지.
    private const val MAX_MESSAGES = 3

    private val main = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentView: View? = null
    private var currentOwner: OverlayLifecycleOwner? = null
    @Volatile private var currentNumber: String? = null
    private var loadJob: Job? = null
    private var safetyJob: Job? = null

    private val _state = MutableStateFlow<CallerState?>(null)
    val state = _state.asStateFlow()

    private const val TAG = "IncomingCallCard"

    /** 벨 울림 — 이 번호의 상대 정보 카드를 띄운다. 권한/토글 없으면 조용히 무시. */
    fun onRinging(context: Context, rawNumber: String?) {
        val appCtx = context.applicationContext
        val app = appCtx as? CallFollowCrmApplication ?: return
        val enabled = app.container.preferences.incomingCallerCardEnabled
        val overlay = PermissionHelper.hasOverlay(appCtx)
        val number = rawNumber?.trim().orEmpty()
        android.util.Log.d(TAG, "onRinging: numLen=${number.length} enabled=$enabled overlay=$overlay")
        if (!enabled) return
        if (!overlay) return
        if (number.isBlank()) return                 // OEM 이 번호를 가렸으면 할 수 있는 게 없음.
        if (currentView != null && currentNumber == number) return  // 같은 통화 중복 방지.
        android.util.Log.d(TAG, "onRinging: showing card")

        currentNumber = number
        // 우선 번호만으로 즉시 카드 표시(로딩) → 뒤이어 고객/일정/대화 채움.
        _state.value = CallerState(
            phoneNumber = number,
            displayName = PhoneNumberFormatter.format(number),
            isKnown = false,
            scheduleLabel = null,
            address = null,
            moneyLabel = null,
            messages = emptyList(),
            customerId = null,
            loading = true
        )
        main.post { if (currentView == null) actuallyShow(appCtx) }
        startSafetyTimeout()

        loadJob?.cancel()
        loadJob = ioScope.launch {
            val container = app.container
            val digits = number.filter { it.isDigit() }
            val national = if (digits.startsWith("82")) "0" + digits.removePrefix("82") else digits

            val customer = runCatching {
                container.customerRepository.findByPhone(number)
                    ?: container.customerRepository.findByPhone(digits)
                    ?: container.customerRepository.findByPhone(national)
            }.getOrNull()

            val schedule = scheduleLabelOf(customer)
            val addr = customer?.address?.trim()?.takeIf { it.isNotBlank() }
            val money = moneyLabelOf(customer)

            // 최근 대화(문자) — suffix 매칭이라 하이픈/포맷 달라도 잡힘. 최신 3개, 대화처럼 오래된→최신 순.
            val msgs = runCatching {
                container.smsRepository.querySmsOnly(number)
                    .take(MAX_MESSAGES)
                    .map { MsgPreview(body = it.body.trim().replace("\n", " "), sent = it.sent) }
                    .reversed()
            }.getOrDefault(emptyList())

            // 저장 이름 없으면 기기 연락처(삼성)에서 조회 — "저장돼 있으면 그대로 반영". (2026-07-21 사장님)
            val name = customer?.name?.takeIf { it.isNotBlank() }
                ?: com.detailline.callfollowcrm.util.ContactNameResolver.lookup(container.appContext, number)
                ?: PhoneNumberFormatter.format(number)
            val known = customer != null || msgs.isNotEmpty()
            // 상태 = 색 결정. 완료(빨강) > 예정(초록) > 신규(노랑) > 그 외 기존(파랑).
            val status = when {
                !known -> CallerStatus.NEW
                customer?.workCompletedAt != null -> CallerStatus.COMPLETED
                (customer?.scheduledWorkDate ?: 0L) > 0L -> CallerStatus.SCHEDULED
                else -> CallerStatus.EXISTING
            }

            // 벨이 이미 끝났으면(카드 사라짐) 무시.
            if (currentNumber != number) return@launch
            _state.update {
                it?.copy(
                    displayName = name,
                    isKnown = known,
                    scheduleLabel = schedule,
                    address = addr,
                    moneyLabel = money,
                    messages = msgs,
                    customerId = customer?.id,
                    loading = false,
                    status = status
                )
            }
        }
    }

    /** 통화 응답/종료 — 카드 제거. */
    fun onCallGone(@Suppress("UNUSED_PARAMETER") context: Context) {
        currentNumber = null
        main.post { actuallyHide() }
    }

    /**
     * 시공 일정/이력 한 줄 — "시공일 + D-day" 로 빠른 파악. (2026-07-01 사장님)
     *   남았으면 D-3, 오늘이면 오늘, 지났으면 D+5 (관례). 완료 처리됐으면 ✅.
     *   예) "🔨 3월 15일 시공 · D-3" / "🔨 3월 15일 시공 · 오늘" / "✅ 3월 15일 시공 완료 · D+5"
     */
    private fun scheduleLabelOf(c: com.detailline.callfollowcrm.data.local.entity.CustomerEntity?): String? {
        c ?: return null
        val date = c.scheduledWorkDate?.takeIf { it > 0L } ?: c.workCompletedAt?.takeIf { it > 0L } ?: return null
        val dday = DateTimeUtils.dDayLabel(date)   // "오늘" / "D-3" / "D+5"
        return if (c.workCompletedAt != null) "✅ ${monthDay(date)} 시공 완료 · $dday"
        else "🔨 ${monthDay(date)} 시공 · $dday"
    }

    /** 돈 한 줄 — 받은 돈 우선(사장님: "얼마 냈는지"). 없으면 견적/계약금. 단위 원. */
    private fun moneyLabelOf(c: com.detailline.callfollowcrm.data.local.entity.CustomerEntity?): String? {
        c ?: return null
        val received = (if (c.depositPaidAt != null) c.depositAmount ?: 0L else 0L) +
            (if (c.balancePaidAt != null) c.balanceAmount ?: 0L else 0L)
        return when {
            received > 0L -> "받은 돈 ${wonText(received)}"
            (c.totalAmount ?: 0L) > 0L -> "견적 ${wonText(c.totalAmount!!)}"
            (c.depositAmount ?: 0L) > 0L -> "계약금 ${wonText(c.depositAmount!!)}"
            else -> null
        }
    }

    private fun wonText(won: Long): String =
        if (won >= 10_000L && won % 10_000L == 0L) "${won / 10_000L}만원" else "%,d원".format(won)

    private fun monthDay(epoch: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epoch }
        return "${cal.get(java.util.Calendar.MONTH) + 1}월 ${cal.get(java.util.Calendar.DAY_OF_MONTH)}일"
    }

    // ----- internal -----

    private fun onOpenRecord() {
        val st = _state.value ?: return
        val ctx = currentView?.context?.applicationContext ?: return
        runCatching {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                action = MainActivity.ACTION_CHAT
                putExtra(MainActivity.EXTRA_PHONE_NUMBER, st.phoneNumber)
                st.customerId?.let { putExtra(MainActivity.EXTRA_CUSTOMER_ID, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            ctx.startActivity(intent)
        }
        currentNumber = null
        main.post { actuallyHide() }
    }

    private fun onCloseTapped() {
        currentNumber = null
        main.post { actuallyHide() }
    }

    @SuppressLint("InflateParams")
    private fun actuallyShow(appContext: Context) {
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val owner = OverlayLifecycleOwner().also { it.onCreate(); it.onStart(); it.onResume() }
        val composeView = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val st by state.collectAsState()
                st?.let {
                    IncomingCallCard(
                        state = it,
                        onOpen = ::onOpenRecord,
                        onClose = ::onCloseTapped
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 포커스 안 뺏음(전화 받기 방해 X) + 카드 밖 터치 통과 + 잠금화면 위에도 표시.
            //   FLAG_SHOW_WHEN_LOCKED: 화면 꺼짐/잠금 상태로 전화 와도 카드가 뜨게(deprecated 지만 오버레이엔 유효).
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            // 상단은 삼성 자체 통화 배너와 겹쳐 가려짐 → 화면 위쪽 28% 지점(상단배너 아래, 답변버튼 위)에 띄움.
            //   (로그상 카드 창은 정상 생성·drawing 됐으나 삼성 InCallUI 에 가려 안 보였음. 2026-07-01)
            gravity = Gravity.TOP
            y = (appContext.resources.displayMetrics.heightPixels * 0.28f).toInt()
        }

        runCatching { wm.addView(composeView, params) }
            .onSuccess {
                currentView = composeView; currentOwner = owner
                android.util.Log.d(TAG, "actuallyShow: addView OK (y=${params.y})")
            }
            .onFailure {
                android.util.Log.w(TAG, "actuallyShow: addView FAILED", it)
                _state.value = null; currentNumber = null
            }
    }

    private fun actuallyHide() {
        loadJob?.cancel(); loadJob = null
        safetyJob?.cancel(); safetyJob = null
        currentView?.let { v ->
            runCatching {
                val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeView(v)
            }
        }
        currentOwner?.onDestroy()
        currentOwner = null
        currentView = null
        _state.value = null
    }

    private fun startSafetyTimeout() {
        safetyJob?.cancel()
        safetyJob = ioScope.launch {
            delay(SAFETY_TIMEOUT_MS)
            currentNumber = null
            main.post { actuallyHide() }
        }
    }

    // ----- data -----

    /** 카드 색·라벨을 정하는 고객 상태(2026-07-02 사장님). 멀리서도 알아보게 상태별 색. */
    enum class CallerStatus { NEW, SCHEDULED, COMPLETED, EXISTING }

    data class CallerState(
        val phoneNumber: String,
        val displayName: String,
        val isKnown: Boolean,
        val scheduleLabel: String?,   // "🔨 시공 D-3" / "✅ 3월 15일 시공 완료"
        val address: String?,         // 현장 주소
        val moneyLabel: String?,      // "받은 돈 200만원" / "견적 150만원"
        val messages: List<MsgPreview>,
        val customerId: Long?,
        val loading: Boolean,
        val status: CallerStatus = CallerStatus.EXISTING
    )

    data class MsgPreview(val body: String, val sent: Boolean)
}

// ----- 카드 UI -----

private val CardBlue = Color(0xFF3182F6)
private val CardBlueSoft = Color(0xFFE8F1FE)
private val TextPrimary = Color(0xFF191F28)
private val TextSecondary = Color(0xFF4E5968)
private val TextTertiary = Color(0xFF8B95A1)
private val GrayBg = Color(0xFFF2F4F6)

// 상태별 카드 색(2026-07-02 사장님) — 벨 울릴 때 멀리서도 알아보게. 부드러운 배경 + 굵은 강조·라벨.
//   완료=빨강 / 예정=초록 / 신규=노랑 / 그 외 기존=파랑(중립). 촌스럽지 않게 톤다운.
private data class CardPalette(val bg: Color, val soft: Color, val accent: Color, val label: String)

private val NeutralPalette = CardPalette(Color.White, CardBlueSoft, CardBlue, "전화 오는 중")
private val NewPalette = CardPalette(Color(0xFFFFF3B0), Color(0xFFFCE588), Color(0xFFB7791F), "🆕 처음 오는 전화")
private val ScheduledPalette = CardPalette(Color(0xFFDBF4E3), Color(0xFFAEE9C3), Color(0xFF128A50), "📅 시공 예정 고객")
private val CompletedPalette = CardPalette(Color(0xFFFBDEDE), Color(0xFFF5C4C6), Color(0xFFD83A40), "✅ 시공했던 고객")

private fun paletteFor(status: IncomingCallOverlay.CallerStatus): CardPalette = when (status) {
    IncomingCallOverlay.CallerStatus.NEW -> NewPalette
    IncomingCallOverlay.CallerStatus.SCHEDULED -> ScheduledPalette
    IncomingCallOverlay.CallerStatus.COMPLETED -> CompletedPalette
    IncomingCallOverlay.CallerStatus.EXISTING -> NeutralPalette
}

@Composable
private fun IncomingCallCard(
    state: IncomingCallOverlay.CallerState,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    // 로딩 중엔 중립(흰/파랑) → 값 확정되면 상태색으로 전환(깜빡임 방지). 완료=빨강·예정=초록·신규=노랑.
    val pal = if (state.loading) NeutralPalette else paletteFor(state.status)
    val isNew = !state.loading && state.status == IncomingCallOverlay.CallerStatus.NEW
    val cardBg = pal.bg
    val accent = pal.accent
    val accentSoft = pal.soft
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(26.dp), clip = false)
                .clip(RoundedCornerShape(26.dp))
                .background(cardBg)
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(accentSoft),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Call, "전화", tint = accent, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pal.label,
                        fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = accent
                    )
                    Text(
                        state.displayName, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(GrayBg).clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, "닫기", tint = TextTertiary, modifier = Modifier.size(22.dp)) }
            }

            state.scheduleLabel?.let { label ->
                Spacer(Modifier.height(14.dp))
                Text(
                    label,
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = accent,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(accentSoft)
                        .padding(horizontal = 15.dp, vertical = 9.dp)
                )
            }

            state.address?.let { addr ->
                Spacer(Modifier.height(12.dp))
                Text(
                    "📍 $addr", fontSize = 16.sp, color = TextSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }

            state.moneyLabel?.let { money ->
                Spacer(Modifier.height(8.dp))
                Text("💰 $money", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }

            when {
                state.messages.isNotEmpty() -> {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(GrayBg))
                    Spacer(Modifier.height(13.dp))
                    Text("최근 대화", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
                    Spacer(Modifier.height(8.dp))
                    state.messages.forEach { m ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(
                                if (m.sent) "나 " else "고객 ",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = if (m.sent) accent else TextTertiary
                            )
                            Text(
                                m.body, fontSize = 15.sp, color = TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                state.loading -> {
                    Spacer(Modifier.height(14.dp))
                    Text("정보 불러오는 중…", fontSize = 15.sp, color = TextTertiary)
                }
                !state.isKnown -> {
                    Spacer(Modifier.height(14.dp))
                    Text("처음 보는 번호예요", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Text("저장·문자 기록이 없어요", fontSize = 14.sp, color = TextTertiary)
                }
                else -> {
                    Spacer(Modifier.height(14.dp))
                    Text("아직 나눈 대화가 없어요", fontSize = 15.sp, color = TextTertiary)
                }
            }

            Spacer(Modifier.height(18.dp))
            // 신규면 열 '기록'이 없으니 "신규 전화예요!" 로. 앰버 하이라이트(카드색과 통일). 눌러도 대화는 열림. (2026-07-02 사장님)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (isNew) accentSoft else GrayBg)
                    .clickable { onOpen() }.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isNew) "신규 전화예요!" else "기록 열기",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = if (isNew) accent else TextSecondary
                )
            }
        }
    }
}

// ----- WindowManager 안 ComposeView 용 커스텀 LifecycleOwner (PostCallOverlay 패턴 재사용) -----

private class OverlayLifecycleOwner :
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
