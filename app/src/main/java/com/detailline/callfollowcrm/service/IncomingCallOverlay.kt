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
import kotlinx.coroutines.flow.first
import android.widget.LinearLayout
import android.widget.TextView
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

    /**
     * 미리보기 — 전화를 기다리지 않고 카드를 지금 띄운다. (2026-09-17)
     *   오버레이는 실제 통화에서만 보여서, 만들고도 눈으로 확인할 방법이 없었다.
     *   **가장 최근에 시공일이 잡힌 손님**의 진짜 정보로 띄운다(가짜 데이터로 보면 의미가 없다).
     *   8초 뒤 알아서 사라진다.
     */
    fun showPreview(context: Context) {
        val appCtx = context.applicationContext
        val app = appCtx as? CallFollowCrmApplication ?: return
        if (!PermissionHelper.hasOverlay(appCtx)) return
        ioScope.launch {
            // 가장 **가까운 다음 시공** 손님으로 — 정보가 제일 많이 차 있는 게 그 손님이라
            //   미리보기에서 카드가 어떻게 보이는지 제대로 확인된다. (옛 손님을 고르면 빈 카드가 뜬다)
            val c = runCatching {
                val all = app.container.customerRepository.observeScheduled().first()
                val today = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(System.currentTimeMillis())
                all.firstOrNull { (it.scheduledWorkDate ?: 0L) >= today }
                    ?: all.maxByOrNull { it.scheduledWorkDate ?: 0L }
            }.getOrNull()
            val number = c?.phoneNumber?.takeIf { it.isNotBlank() } ?: "010-0000-0000"
            main.post {
                currentNumber = number
                if (currentView == null) actuallyShow(appCtx)
            }
            onRinging(appCtx, number)
        }
    }

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

            // 지난 통화 요약 — 카드에서 "지난번에 뭐라 했더라"를 바로 풀어준다. (2026-09-17 사장님)
            val lastSum = runCatching {
                container.callSummaryRepository
                    .observeByPhoneSuffix(digits.takeLast(8)).first().firstOrNull()
            }.getOrNull()
            val lastSumText = lastSum?.summaryText?.trim()?.takeIf { it.isNotBlank() }
                ?: lastSum?.title?.trim()?.takeIf { it.isNotBlank() }
            val lastSumWhen = lastSum?.recordedAt?.takeIf { it > 0L }?.let { monthDay(it) }

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
                    status = status,
                    lastSummary = if (locked) null else lastSumText,
                    lastSummaryWhen = if (locked) null else lastSumWhen
                )
            }
        }
    }

    /**
     * 전화를 **받았을 때** — 카드를 내리지 않는다. (2026-09-17 사장님: "통화내내 사라지지않았으면")
     *   통화하면서 주소·잔금을 보고 말해야 하기 때문. 표시만 '통화 중'으로 바꾼다.
     *   ⚠️ 삼성 최신 폰은 통화 중 오버레이를 막을 수 있다(Auto Blocker). 막히면 그냥 안 보일 뿐,
     *     앱이 깨지지는 않는다 — 실기로 확인하고 안 되면 '벨 울릴 때만'으로 되돌린다.
     */
    fun onAnswered(@Suppress("UNUSED_PARAMETER") context: Context) {
        safetyJob?.cancel(); safetyJob = null   // 통화 중엔 좀비 타이머로 사라지면 안 된다
        _state.update { it?.copy(talking = true) }
    }

    /** 통화 종료 — 카드 제거. */
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
        val view = CallerCardView(appContext) { onOpenRecord() }.apply {
            state.value?.let { bind(it) }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,   // 카드 높이만 — 아래(받기·거절)는 아예 안 덮는다
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 카드만 만질 수 있고(탭하면 그 손님 대화로), 창이 카드 높이뿐이라 받기·거절은 원래대로 눌린다.
            //   NOT_TOUCHABLE 을 빼는 게 위험했던 건 '전체화면' 창일 때다 — 지금은 위쪽 띠만 차지한다.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = (appContext.resources.displayMetrics.density * 34f).toInt()   // 상태바 아래
        }

        runCatching { wm.addView(view, params) }
            .onSuccess {
                currentView = view
                android.util.Log.d(TAG, "actuallyShow: addView OK")
                // 상태(loading→확정) 반영 — _state 관찰해 색 갱신.
                colorJob?.cancel()
                colorJob = ioScope.launch {
                    state.collect { s -> if (s != null) main.post { (currentView as? CallerCardView)?.bind(s) } }
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
        val status: CallerStatus = CallerStatus.EXISTING,
        /** 지난 통화 요약 한 줄 — "지난번에 뭐라 했더라"를 바로 푼다. (2026-09-17 사장님) */
        val lastSummary: String? = null,
        /** 그 요약이 언제 통화 건지 ("9월 13일"). */
        val lastSummaryWhen: String? = null,
        /** 받은 뒤(통화 중)인지 — 카드를 안 내리고 표시만 바꾼다. */
        val talking: Boolean = false
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

/**
 * 전화 미리보기 카드 — 프로토 확정안(2026-09-17 사장님: "b랑 c 안을 좀 복합", "통화내내 사라지지않았으면",
 * "신규인지 구분도 확실해야함"). 검은 유리 카드에 B안 내용을 담는다.
 *
 * ⚠️ 왜 Compose 가 아니라 옛날 View 인가 — 이 오버레이 창(수동 lifecycle)에서는 **Compose 가 안 그려졌다**.
 *   창은 맨 위인데 화면캡처에 아무것도 안 나왔다(2026-08-31 실측). View.onDraw 는 확실히 호출된다.
 *   같은 실수를 다시 하지 말 것.
 *
 * 신규 구분: 색만으로 하지 않는다(햇빛·색약). **띠 색 + 칩 + "처음 걸려온 번호예요" 상자**로 글자까지 말해준다.
 */
private class CallerCardView(
    context: Context,
    private val onTap: () -> Unit
) : LinearLayout(context) {

    private val dm = context.resources.displayMetrics
    private fun dp(v: Float): Int = (v * dm.density + 0.5f).toInt()

    private val strip = View(context)
    private val nameTv = mkText(17f, 0xFFFFFFFF.toInt(), bold = true)
    private val chipTv = mkText(10.5f, 0xFFFFFFFF.toInt(), bold = true)
    private val subTv = mkText(11.5f, 0xFF8E9BAC.toInt())
    private val addrTv = mkText(12.5f, 0xFFC3CDDA.toInt())
    private val moneyTv = mkText(12.5f, 0xFFC3CDDA.toInt())
    private val newTitleTv = mkText(12.5f, 0xFFFFC24D.toInt(), bold = true)
    private val newDescTv = mkText(11.5f, 0xFFE3D3B4.toInt())
    private val newBox = LinearLayout(context)
    private val sumLabelTv = mkText(9.5f, 0xFF8E9BAC.toInt(), bold = true)
    private val sumTextTv = mkText(11.5f, 0xFFD5DDE7.toInt())
    private val sumBox = LinearLayout(context)
    private val msgLabelTv = mkText(9.5f, 0xFF8E9BAC.toInt(), bold = true)
    private val msgTextTv = mkText(11.5f, 0xFFD5DDE7.toInt())
    private val msgBox = LinearLayout(context)
    private val divider = View(context)
    private val footTv = mkText(10.5f, 0xFF8E9BAC.toInt())

    private fun mkText(sp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        textSize = sp
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setLineSpacing(0f, 1.25f)
    }

    private fun roundBg(color: Int, radius: Float, strokeColor: Int = 0, strokeDp: Float = 0f) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != 0) setStroke(dp(strokeDp), strokeColor)
        }

    init {
        orientation = VERTICAL
        setPadding(dp(10f), 0, dp(10f), 0)

        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            background = roundBg(0xEE0F141C.toInt(), 17f, 0x22FFFFFF, 1f)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            elevation = dp(8f).toFloat()
            isClickable = true
            setOnClickListener { onTap() }
        }
        card.addView(strip, LayoutParams(LayoutParams.MATCH_PARENT, dp(4f)))

        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(13f), dp(11f), dp(13f), dp(12f))
        }

        val head = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        head.addView(nameTv)
        chipTv.setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
        head.addView(chipTv, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(7f)
        })
        body.addView(head)
        body.addView(subTv, rowLp(2f))
        body.addView(addrTv, rowLp(6f))
        body.addView(moneyTv, rowLp(2f))

        // 신규 상자 — 색이 아니라 **글자로** 신규임을 말한다.
        newBox.orientation = VERTICAL
        newBox.background = roundBg(0x24F59F0B, 12f, 0x52F59F0B, 1f)
        newBox.setPadding(dp(11f), dp(9f), dp(11f), dp(10f))
        newBox.addView(newTitleTv)
        newBox.addView(newDescTv, rowLp(2f))
        body.addView(newBox, rowLp(9f))

        body.addView(panel(sumBox, sumLabelTv, sumTextTv), rowLp(9f))
        body.addView(panel(msgBox, msgLabelTv, msgTextTv), rowLp(7f))

        divider.setBackgroundColor(0x1FFFFFFF)
        body.addView(divider, LayoutParams(LayoutParams.MATCH_PARENT, dp(1f)).apply { topMargin = dp(9f) })
        body.addView(footTv, rowLp(7f))

        card.addView(body)
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun rowLp(topDp: Float) =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(topDp) }

    private fun panel(box: LinearLayout, label: TextView, text: TextView): LinearLayout {
        box.orientation = VERTICAL
        box.background = roundBg(0x12FFFFFF, 11f)
        box.setPadding(dp(10f), dp(8f), dp(10f), dp(9f))
        box.addView(label)
        box.addView(text, rowLp(2f))
        return box
    }

    /** 상태 색 — 신규=노랑, 예정=초록, 완료=빨강, 그 외 기존=파랑. 조회 중엔 회색. */
    private fun stripColor(st: IncomingCallOverlay.CallerState): Int = when {
        st.loading -> 0xFFAEB6C2.toInt()
        st.status == IncomingCallOverlay.CallerStatus.NEW -> 0xFFF59F0B.toInt()
        st.status == IncomingCallOverlay.CallerStatus.SCHEDULED -> 0xFF12B886.toInt()
        st.status == IncomingCallOverlay.CallerStatus.COMPLETED -> 0xFFF0436A.toInt()
        else -> 0xFF3182F6.toInt()
    }

    private fun chipTextColor(st: IncomingCallOverlay.CallerState): Int = when (st.status) {
        IncomingCallOverlay.CallerStatus.NEW -> 0xFFFFC24D.toInt()
        IncomingCallOverlay.CallerStatus.SCHEDULED -> 0xFF3FE0AE.toInt()
        IncomingCallOverlay.CallerStatus.COMPLETED -> 0xFFFF8FA9.toInt()
        else -> 0xFF7FB4FF.toInt()
    }

    fun bind(st: IncomingCallOverlay.CallerState) {
        val isNew = !st.loading && st.status == IncomingCallOverlay.CallerStatus.NEW
        strip.setBackgroundColor(stripColor(st))

        nameTv.text = st.displayName
        nameTv.textSize = if (isNew) 19f else 17f

        val chipLabel = when {
            st.loading -> "찾는 중…"
            isNew -> "✨ 신규"
            st.scheduleLabel != null -> st.scheduleLabel
            st.status == IncomingCallOverlay.CallerStatus.COMPLETED -> "✅ 시공 완료"
            else -> "기존 손님"
        }
        chipTv.text = chipLabel
        chipTv.setTextColor(chipTextColor(st))
        chipTv.background = roundBg((stripColor(st) and 0x00FFFFFF) or (0x38 shl 24), 999f)

        // 이름이 번호 그대로면 아래 번호줄은 중복이라 안 띄운다.
        val formatted = PhoneNumberFormatter.format(st.phoneNumber)
        subTv.text = if (st.displayName == formatted) "저장 안 된 번호" else formatted
        subTv.visibility = View.VISIBLE

        show(addrTv, st.address?.let { "📍  $it" })
        show(moneyTv, st.moneyLabel?.let { "💰  $it" })

        newBox.visibility = if (isNew) View.VISIBLE else View.GONE
        if (isNew) {
            newTitleTv.text = "처음 걸려온 번호예요"
            newDescTv.text = "주고받은 문자도, 지난 통화도 없어요.\n새 문의일 가능성이 높아요."
        }

        val hasSum = !st.lastSummary.isNullOrBlank()
        sumBox.visibility = if (hasSum) View.VISIBLE else View.GONE
        if (hasSum) {
            sumLabelTv.text = "지난 통화 요약" + (st.lastSummaryWhen?.let { " · $it" } ?: "")
            sumTextTv.text = st.lastSummary
        }

        val lastMsg = st.messages.lastOrNull()
        msgBox.visibility = if (lastMsg != null) View.VISIBLE else View.GONE
        if (lastMsg != null) {
            msgLabelTv.text = if (lastMsg.sent) "내가 보낸 마지막 문자" else "마지막 받은 문자"
            msgTextTv.text = lastMsg.body.take(90)
        }

        footTv.text = when {
            st.talking -> "📌 통화 중 · 끊을 때까지 남아 있어요"
            isNew -> "탭하면 열려요 · 끊으면 바로 손님 등록"
            else -> "탭하면 이 손님 대화로"
        }
    }

    private fun show(tv: TextView, text: String?) {
        if (text.isNullOrBlank()) { tv.visibility = View.GONE } else { tv.visibility = View.VISIBLE; tv.text = text }
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
