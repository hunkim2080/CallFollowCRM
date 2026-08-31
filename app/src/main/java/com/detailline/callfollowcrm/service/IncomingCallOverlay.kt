package com.detailline.callfollowcrm.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
    private var colorJob: Job? = null   // 테두리 색을 _state(loading→확정) 따라 갱신

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
        // 즉시 얹는다 — 스크리닝(onScreenCall)이 살아있는 '특권' 순간이라 백그라운드여도 오버레이가 허용된다(로그 확인).
        //   T전화가 나중에 떠서 덮는 건, 스크리닝 서비스가 onScreenCall 안에서 bringToFront 로 위로 재장착해 해결. (2026-08-31 사장님)
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
            // 잠금화면 위에선 돈·문자 숨김 — 옆 사람 노출 방지. 누가 전화왔는지(이름)만, 폰 열면 다 보임. (2026-08-15 사장님 #11)
            val locked = runCatching {
                (container.appContext.getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isKeyguardLocked == true
            }.getOrDefault(false)
            _state.update {
                it?.copy(
                    displayName = name,
                    isKnown = known,
                    scheduleLabel = schedule,
                    address = addr,
                    moneyLabel = if (locked) null else money,
                    messages = if (locked) emptyList() else msgs,
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
     * 테두리를 '위로' 재장착 — 상태/잡은 유지하고 창만 제거→다시 add (나중에 add 된 오버레이가 위 z-order).
     *   T전화 통화화면이 우리보다 나중에 떠서 덮은 걸 다시 위로 올린다. 스크리닝(onScreenCall)이 살아있는
     *   '특권' 동안 호출해야 삼성 백그라운드 오버레이 차단을 통과함. (2026-08-31 사장님 — 최신폰 대응)
     */
    fun bringToFront(context: Context) {
        val appCtx = context.applicationContext
        main.post {
            val v = currentView ?: return@post run { if (_state.value != null) actuallyShow(appCtx) }
            runCatching {
                (appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.removeView(v)
            }
            currentOwner?.onDestroy()
            currentView = null
            currentOwner = null
            actuallyShow(appCtx)   // _state 그대로 → 같은 테두리를 위로 다시
        }
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
        // ⚠️ Compose 오버레이는 이 창(수동 lifecycle)에서 렌더가 안 됐음 — 창은 맨 위(z-order #7)인데
        //   화면캡처 결과 테두리가 하나도 안 그려짐(2026-08-31 실측). → 그냥 커스텀 View 로 Canvas 에 직접
        //   테두리를 그린다(뷰 시스템이 onDraw 를 확실히 호출). (사장님 — 최신폰 대응)
        val view = EdgeOverlayView(appContext).apply {
            state.value?.let { setStatus(it.status, it.loading) }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,   // 전체화면 — 테두리를 화면 가장자리에 두름
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 테두리만 얹고 아무것도 안 막는다: 모든 터치 통과(NOT_TOUCHABLE — 받기/거절 그대로) +
            //   포커스 안 뺏음 + 잠금화면 위에도 + 화면 끝(상태바·내비바)까지 그려 진짜 가장자리에.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            // ⚠️ FLAG_LAYOUT_NO_LIMITS 는 넣지 말 것 — 크기제약이 무제한이 돼 fillMaxSize()가 0×0 으로
            //   측정되어 아무것도 안 그려진다(2026-08-31 로그로 확인). IN_SCREEN(전체화면 바운드)만.
            PixelFormat.TRANSLUCENT
        ).apply {
            // 전체화면이라 y offset 불필요. 테두리는 가장자리라 삼성 InCallUI(중앙)와 덜 겹침.
            gravity = Gravity.TOP
        }

        runCatching { wm.addView(view, params) }
            .onSuccess {
                currentView = view
                android.util.Log.d(TAG, "actuallyShow: addView OK")
                // 상태(loading→확정) 반영 — _state 관찰해 색 갱신.
                colorJob?.cancel()
                colorJob = ioScope.launch {
                    state.collect { s -> if (s != null) main.post { (currentView as? EdgeOverlayView)?.setStatus(s.status, s.loading) } }
                }
            }
            .onFailure {
                android.util.Log.w(TAG, "actuallyShow: addView FAILED", it)
                _state.value = null; currentNumber = null
            }
    }

    private fun actuallyHide() {
        loadJob?.cancel(); loadJob = null
        safetyJob?.cancel(); safetyJob = null
        colorJob?.cancel(); colorJob = null
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

// 테두리 상태색 (프로토 확정값, 2026-08-31 사장님) — 신규=노랑(뛰어가 받기)·예정=초록·완료=빨강·기존=파랑.
/**
 * 전화 오는 순간 화면 '테두리'에 상태색만 — 바깥 진하고 안쪽으로 연해지는 그라데이션.
 *   Compose 대신 커스텀 View 로 Canvas 에 직접 그린다 — 수동 lifecycle 오버레이 창에선 Compose 가
 *   렌더되지 않았음(창은 맨 위였는데 화면캡처에 테두리가 아예 안 나옴, 2026-08-31 실측). View.onDraw 는 확실히 호출됨.
 *   전화화면은 안 가림(창이 전체화면 투명+터치통과). 신규=노랑·예정=초록·완료=빨강·기존=파랑. (사장님 — 최신폰 대응)
 */
private class EdgeOverlayView(context: Context) : View(context) {
    private var argb: Int = 0xFFAEB6C2.toInt()   // 조회 전 중립 흰빛
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * context.resources.displayMetrics.density
    }

    fun setStatus(status: IncomingCallOverlay.CallerStatus, loading: Boolean) {
        argb = if (loading) 0xFFAEB6C2.toInt() else when (status) {
            IncomingCallOverlay.CallerStatus.NEW -> 0xFFFF9F0A.toInt()
            IncomingCallOverlay.CallerStatus.SCHEDULED -> 0xFF12C06A.toInt()
            IncomingCallOverlay.CallerStatus.COMPLETED -> 0xFFF0436A.toInt()
            IncomingCallOverlay.CallerStatus.EXISTING -> 0xFF3A86FF.toInt()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val depth = minOf(w, h) * 0.17f
        val edge = (argb and 0x00FFFFFF) or (0xD9 shl 24)   // 바깥 진하게(~85%)
        val clear = argb and 0x00FFFFFF                      // 안쪽 투명(0%)
        // 위/아래/왼/오 네 가장자리 그라데이션 (바깥 진함 → 안쪽 투명)
        fill.shader = LinearGradient(0f, 0f, 0f, depth, edge, clear, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, depth, fill)
        fill.shader = LinearGradient(0f, h - depth, 0f, h, clear, edge, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h - depth, w, h, fill)
        fill.shader = LinearGradient(0f, 0f, depth, 0f, edge, clear, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, depth, h, fill)
        fill.shader = LinearGradient(w - depth, 0f, w, 0f, clear, edge, Shader.TileMode.CLAMP)
        canvas.drawRect(w - depth, 0f, w, h, fill)
        // 바깥 또렷한 선
        stroke.shader = null
        stroke.color = (argb and 0x00FFFFFF) or (0xFF shl 24)
        canvas.drawRect(0f, 0f, w, h, stroke)
    }
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
