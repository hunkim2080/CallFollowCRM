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
import com.detailline.callfollowcrm.util.TwoWeekSchedule
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
            // 칩 우선순위를 정하려고 — **아직 안 끝난 예약이 있으면** 그게 제일 급한 말이다.
            //   ⚠️ 날짜가 남아 있다고 '다음 시공'이 아니다. 끝낸 건도 예약 날짜는 그대로 남는다
            //     (실제 자료: 6/11 시공 완료인데 예약일도 6/11 → 칩이 "1번 시공" 으로 안 바뀌었다. 2026-09-19)
            val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
            val upcoming = customer?.workCompletedAt == null &&
                (customer?.scheduledWorkDate ?: 0L) >= todayStart
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

            // 🧾 **지난 시공** — 사장님: "기존고객은 언제 시공했었는지.. 얼마를 받았었는지.."
            //   A/S 든 추가 시공이든 이 두 개를 모르면 통화가 안 된다.
            val pastJobs = runCatching {
                customer?.id?.let { cid ->
                    container.jobRepository.byCustomerOnce(cid)
                        .filter { it.workCompletedAt != null && it.cancelledAt == null }
                        .sortedByDescending { it.workCompletedAt ?: 0L }
                } ?: emptyList()
            }.getOrDefault(emptyList())
            val doneCount = pastJobs.size
            val pastLines = pastJobs.take(2).map { j ->
                // 돈은 정산 코드를 **그대로** 쓴다. 규칙을 다시 짜면 틀린다.
                val row = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(j)
                // ⚠️ 건 메모를 '부위'로 쓰면 안 된다. 실제 사장님 메모는 **견적 내역**("모서리 2줄 4만원")
                //   이라 "6월 11일 · 모서리 2줄 4만원 · 54만원 받음" 처럼 **금액이 두 번** 나왔다.
                //   (2026-09-19 실제 자료로 확인) 사장님이 원한 건 "언제 · 얼마 받았나" 둘뿐이다.
                buildString {
                    append(monthDay(j.workCompletedAt ?: 0L))
                    if (row.received > 0L) { append(" · "); append(wonText(row.received)); append(" 받음") }
                    else { append(" · 받은 돈 없음") }
                }
            }

            // 🔁 **몇 번째 통화인가** — 계약 전인데 또 거는 사람을 가려내려고. (2026-09-19 사장님)
            //   지금 통화는 아직 기록에 없으니 **지난 기록 + 1** 이 이번 통화 번호다.
            val records = runCatching {
                container.callRecordRepository.observeByPhoneSuffix(digits.takeLast(8)).first()
            }.getOrDefault(emptyList())
            val callNo = records.size + 1
            val firstAt = records.mapNotNull { it.startedAt ?: it.endedAt }.minOrNull()

            // 📅 **2주 일정** — 사장님: "전화와서 언제 스케줄되냐 가장 많이 물어보거든?" (2026-09-19)
            //   전화받은 그 자리에서 "언제 되냐"에 답하려고. 실패해도 카드는 떠야 하니 통째로 감싼다.
            val twoWeeks = runCatching { loadTwoWeeks(container) }.getOrDefault(emptyList())

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
                // 🔁 시공을 한 적도, 잡은 적도 없는데 **또 건다** = 사려는 사람.
                //   (시공을 잡았으면 위에서 SCHEDULED 로 빠진다 — 그쪽이 할 말이 더 많다)
                callNo >= 2 -> CallerStatus.REPEAT
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
                    lastSummaryWhen = if (locked) null else lastSumWhen,
                    // 잠금화면에선 일정도 가린다 — 돈·문자와 같은 이유(옆 사람 노출).
                    schedule = if (locked) emptyList() else twoWeeks,
                    callNo = callNo,
                    firstContactAt = firstAt,
                    doneCount = doneCount,
                    scheduleUpcoming = upcoming,
                    pastJobLines = if (locked) emptyList() else pastLines
                )
            }
        }
    }

    /**
     * 오늘부터 2주치 일정을 모은다 — **시공(건) + A/S + 시공막내 간단 일정**.
     *
     * ⚠️ **구글 캘린더는 안 읽는다.** 사장님: *"시공막내 캘린더만 읽자. 그래야 시공막내를
     *   더 열심히 사용하지 ㅎ"* (2026-09-19). 개인·가족 일정이 통화 카드에 안 뜨는 것도 덤이다.
     *
     * 규칙은 전부 [TwoWeekSchedule] 에 있다 — 여기선 DB 모양만 맞춰 넘긴다.
     */
    private suspend fun loadTwoWeeks(
        container: com.detailline.callfollowcrm.data.AppContainer
    ): List<TwoWeekSchedule.Day> {
        val customers = container.customerRepository.allOnce()
        val nameOf = HashMap<Long, String>(customers.size)
        val addrOf = HashMap<Long, String>(customers.size)
        for (c in customers) {
            nameOf[c.id] = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber)
            c.address?.trim()?.takeIf { it.isNotBlank() }?.let { addrOf[c.id] = it }
        }

        val src = ArrayList<TwoWeekSchedule.Source>()

        // 시공 — **건(jobs)이 일정의 주인**이다. 취소한 건은 뺀다(cancelledAt).
        for (j in container.jobRepository.allOnce()) {
            val day = j.scheduledWorkDate ?: continue
            if (j.cancelledAt != null) continue
            src += TwoWeekSchedule.Source(
                tone = TwoWeekSchedule.Tone.JOB,
                dayStartMs = day,
                days = j.scheduledWorkDays,
                minutes = j.scheduledWorkMinutes,
                who = nameOf[j.customerId] ?: "손님",
                // 건에 주소가 없으면 고객 주소를 쓴다 — 1차는 고객 쪽에만 있는 경우가 있다.
                detail = j.address?.trim()?.takeIf { it.isNotBlank() } ?: addrOf[j.customerId].orEmpty()
            )
        }

        // A/S — 고객 표에 붙어 있고 주소는 고객 주소를 쓴다(A/S 전용 주소 칸이 없다).
        for (c in customers) {
            val day = c.asScheduledDate ?: continue
            src += TwoWeekSchedule.Source(
                tone = TwoWeekSchedule.Tone.AS,
                dayStartMs = day,
                days = c.asScheduledDays,
                minutes = null,
                who = nameOf[c.id] ?: "손님",
                detail = addrOf[c.id].orEmpty()
            )
        }

        // 내 일정 — 장모님댁·병원 같은 것. 사장님: "지역이 아니어도 보이면 좋을듯."
        for (e in container.simpleEventRepository.allOnce()) {
            src += TwoWeekSchedule.Source(
                tone = TwoWeekSchedule.Tone.EVENT,
                dayStartMs = e.dayStartMs,
                days = 1,
                minutes = e.minutes,
                who = e.title,
                detail = e.memo
            )
        }

        return TwoWeekSchedule.days(src)
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
    enum class CallerStatus { NEW, REPEAT, SCHEDULED, COMPLETED, EXISTING }

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
        val talking: Boolean = false,
        /**
         * 오늘부터 2주 일정. "언제 되냐"에 전화받은 자리에서 답하려고. (2026-09-19 사장님)
         * 잠금화면에선 비어 있다.
         */
        val schedule: List<TwoWeekSchedule.Day> = emptyList(),
        /** 이번이 몇 번째 통화인지(지난 기록 + 1). 계약 전인데 또 거는 사람을 가려낸다. */
        val callNo: Int = 1,
        /** 이 번호와 처음 통화한 때 — "9월 8일부터 문의 중". */
        val firstContactAt: Long? = null,
        /** 끝낸 시공이 몇 번인지 — 칩이 "기존 손님" 대신 "2번 시공" 이 된다. */
        val doneCount: Int = 0,
        /** 다음 시공이 잡혀 있는지. 잡혀 있으면 그 날짜가 칩에서 제일 급한 말이다. */
        val scheduleUpcoming: Boolean = false,
        /** 지난 시공 최대 2줄 — "6월 12일 · 거실·주방 · 180만원 받음". 잠금화면에선 비어 있다. */
        val pastJobLines: List<String> = emptyList()
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
    IncomingCallOverlay.CallerStatus.REPEAT -> NewPalette
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

    // ── 🧾 지난 시공 (2026-09-19) ──
    private val pastBox = LinearLayout(context)
    private val pastLabelTv = mkText(9.5f, 0xFF7FB4FF.toInt(), bold = true)
    private val pastBigTv = mkText(12.5f, 0xFFFFFFFF.toInt(), bold = true)
    private val pastSmallTv = mkText(10.5f, 0xFF9FB4D0.toInt())

    // ── 📅 2주 일정 (2026-09-19) ──
    private val schedBox = LinearLayout(context)
    private val schedLabelTv = mkText(9.5f, 0xFF5FD9B2.toInt(), bold = true)
    private val schedFreeTv = mkText(12f, 0xFFFFFFFF.toInt(), bold = true)
    private val schedHintTv = mkText(9.5f, 0xFF7FB4FF.toInt(), bold = true)
    private val weekHeadRow = LinearLayout(context)
    private val weekRow1 = LinearLayout(context)
    private val weekRow2 = LinearLayout(context)
    private val dayDetailBox = LinearLayout(context)
    private val dayDetailTitleTv = mkText(11.5f, 0xFFFFFFFF.toInt(), bold = true)
    private val dayDetailBody = LinearLayout(context)
    /** 칸 14개 — 한 번 만들고 다시 칠한다. */
    private val dayCells = ArrayList<DayCell>(14)
    /** 접었다 폈다 하는 버튼 — 시공했던 손님은 기억이 먼저라 일정을 접어 둔다. */
    private val schedToggleTv = mkText(11.5f, 0xFFCFE0FF.toInt(), bold = true)
    private var schedExpanded = false
    /** 신규는 접기 버튼 없이 항상 펼친다 — 볼 게 일정밖에 없다. */
    private var schedAlwaysOpen = false
    private var selectedDay = -1L
    private var lastDays: List<TwoWeekSchedule.Day> = emptyList()

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

        // 🧾 지난 시공 — 요약·문자보다 **위**에. 이걸 모르면 통화가 안 된다. (2026-09-19 사장님)
        pastBox.orientation = VERTICAL
        pastBox.background = roundBg(0x213182F6, 11f, 0x4D3182F6, 1f)
        pastBox.setPadding(dp(10f), dp(8f), dp(10f), dp(9f))
        pastBox.addView(pastLabelTv)
        pastBox.addView(pastBigTv, rowLp(3f))
        pastBox.addView(pastSmallTv, rowLp(2f))
        body.addView(pastBox, rowLp(8f))

        body.addView(panel(sumBox, sumLabelTv, sumTextTv), rowLp(9f))
        body.addView(panel(msgBox, msgLabelTv, msgTextTv), rowLp(7f))

        // 📅 2주 일정 — 접기 버튼 + 달력. (2026-09-19 사장님)
        schedToggleTv.gravity = android.view.Gravity.CENTER
        schedToggleTv.background = roundBg(0x17FFFFFF, 11f, 0x29FFFFFF, 1f)
        schedToggleTv.setPadding(0, dp(9f), 0, dp(9f))
        schedToggleTv.isClickable = true
        schedToggleTv.setOnClickListener {
            schedExpanded = !schedExpanded
            applySchedVisibility()
        }
        body.addView(schedToggleTv, rowLp(9f))
        body.addView(buildSchedBox(), rowLp(9f))

        divider.setBackgroundColor(0x1FFFFFFF)
        body.addView(divider, LayoutParams(LayoutParams.MATCH_PARENT, dp(1f)).apply { topMargin = dp(9f) })
        body.addView(footTv, rowLp(7f))

        card.addView(body)
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** 칸 하나 — 날짜 + 최대 두 줄. 누르면 그날이 펴진다. */
    private inner class DayCell(context: Context) {
        val root = LinearLayout(context)
        val dayTv = mkText(11f, 0xFFB6C2D0.toInt(), bold = true)
        val line1 = mkText(8f, 0xFF8E9BAC.toInt(), bold = true)
        val line2 = mkText(8f, 0xFF8E9BAC.toInt(), bold = true)

        init {
            root.orientation = VERTICAL
            root.gravity = android.view.Gravity.CENTER_HORIZONTAL
            root.minimumHeight = dp(46f)
            root.setPadding(dp(2f), dp(4f), dp(2f), dp(5f))
            root.isClickable = true
            for (tv in listOf(dayTv, line1, line2)) {
                tv.gravity = android.view.Gravity.CENTER
                tv.maxLines = 1
                tv.ellipsize = android.text.TextUtils.TruncateAt.END
                tv.setLineSpacing(0f, 1.0f)
            }
            root.addView(dayTv)
            root.addView(line1)
            root.addView(line2)
        }
    }

    /**
     * 📅 2주 달력. **칸 색은 둘뿐** — 비었음(초록) / 그 외(회색).
     *   ⚠️ 일 있는 날을 빨갛게 칠하지 않는다. "받지 마라"는 말이 되기 때문. (2026-09-19 사장님)
     */
    private fun buildSchedBox(): LinearLayout {
        schedBox.orientation = VERTICAL
        schedBox.background = roundBg(0x1712B886, 12f, 0x4212B886, 1f)
        schedBox.setPadding(dp(9f), dp(9f), dp(9f), dp(10f))
        schedBox.addView(schedLabelTv)
        schedBox.addView(schedFreeTv, rowLp(4f))

        for (row in listOf(weekHeadRow, weekRow1, weekRow2)) {
            row.orientation = HORIZONTAL
        }
        for (i in 0 until 7) {
            val h = mkText(8.5f, 0xFF6C7888.toInt(), bold = true)
            h.gravity = android.view.Gravity.CENTER
            weekHeadRow.addView(h, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        schedBox.addView(weekHeadRow, rowLp(8f))

        for (i in 0 until 14) {
            val cell = DayCell(context)
            dayCells.add(cell)
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(1.5f); rightMargin = dp(1.5f)
            }
            (if (i < 7) weekRow1 else weekRow2).addView(cell.root, lp)
        }
        schedBox.addView(weekRow1, rowLp(2f))
        schedBox.addView(weekRow2, rowLp(3f))

        schedHintTv.gravity = android.view.Gravity.CENTER
        schedBox.addView(schedHintTv, rowLp(7f))

        // 누른 날 상세 — 통화 중이라 **딴 화면으로 안 나간다.** 카드 안에서 편다.
        dayDetailBox.orientation = VERTICAL
        dayDetailBox.background = roundBg(0x59000000, 11f, 0x24FFFFFF, 1f)
        dayDetailBox.setPadding(dp(11f), dp(9f), dp(11f), dp(10f))
        dayDetailBody.orientation = VERTICAL
        dayDetailBox.addView(dayDetailTitleTv)
        dayDetailBox.addView(dayDetailBody, rowLp(6f))
        dayDetailBox.visibility = View.GONE
        schedBox.addView(dayDetailBox, rowLp(8f))
        return schedBox
    }

    private fun applySchedVisibility() {
        schedBox.visibility = if (schedExpanded) View.VISIBLE else View.GONE
        schedToggleTv.text = if (schedExpanded) "📅  일정 접기" else schedToggleLabel()
        if (!schedExpanded) hideDayDetail()
    }

    private fun schedToggleLabel(): String {
        val free = lastDays.count { it.isFree }
        return if (free > 0) "📅  내 일정 보기 · 2주 안에 빈 날 $free" else "📅  내 일정 보기"
    }

    private fun hideDayDetail() {
        selectedDay = -1L
        dayDetailBox.visibility = View.GONE
        paintCells()
    }

    private fun bindSchedule(days: List<TwoWeekSchedule.Day>) {
        lastDays = days
        if (days.isEmpty()) {
            schedToggleTv.visibility = View.GONE
            schedBox.visibility = View.GONE
            return
        }
        schedToggleTv.visibility = if (schedAlwaysOpen) View.GONE else View.VISIBLE
        if (schedAlwaysOpen) schedExpanded = true

        schedLabelTv.text = "내 일정 · 2주"
        val free = TwoWeekSchedule.freeDaysLabel(days)
        schedFreeTv.text = if (free != null) "빈 날 — $free" else "2주가 꽉 찼어요"
        schedFreeTv.setTextColor(if (free != null) 0xFFFFFFFF.toInt() else 0xFFFFC24D.toInt())
        schedHintTv.text = "칸을 누르면 그날이 열려요"

        for (i in 0 until 7) {
            (weekHeadRow.getChildAt(i) as TextView).text = days.getOrNull(i)?.weekday ?: ""
        }
        for (i in dayCells.indices) {
            val day = days.getOrNull(i) ?: continue
            val cell = dayCells[i]
            cell.dayTv.text = day.dayOfMonth.toString()
            val lines = TwoWeekSchedule.cellLines(day)
            bindLine(cell.line1, lines.getOrNull(0))
            bindLine(cell.line2, lines.getOrNull(1))
            cell.root.setOnClickListener { onDayTap(day) }
        }
        paintCells()
        applySchedVisibility()
    }

    private fun bindLine(
        tv: TextView,
        line: TwoWeekSchedule.Line?
    ) {
        if (line == null) { tv.visibility = View.GONE; return }
        tv.visibility = View.VISIBLE
        tv.text = line.text
        tv.setTextColor(toneColor(line.tone))
    }

    /** 시공=회색 · A/S=주황 · 내 일정=보라 · 비었음=초록. 색이 셋이라 안 헷갈린다. */
    private fun toneColor(tone: TwoWeekSchedule.Tone): Int = when (tone) {
        TwoWeekSchedule.Tone.JOB -> 0xFF8E9BAC.toInt()
        TwoWeekSchedule.Tone.AS -> 0xFFFFC24D.toInt()
        TwoWeekSchedule.Tone.EVENT -> 0xFFC4AFFF.toInt()
        TwoWeekSchedule.Tone.EMPTY -> 0xFF3FE0AE.toInt()
    }

    private fun paintCells() {
        for (i in dayCells.indices) {
            val day = lastDays.getOrNull(i) ?: continue
            val cell = dayCells[i]
            val picked = day.dayStartMs == selectedDay
            cell.root.background = when {
                picked -> roundBg(0x38FFFFFF, 8f, 0x99FFFFFF.toInt(), 1f)
                day.isFree -> roundBg(0x3D12B886, 8f, 0x733FE0AE, 1f)
                day.isToday -> roundBg(0x0DFFFFFF, 8f, 0x66FFFFFF, 1.5f)
                else -> roundBg(0x0DFFFFFF, 8f)
            }
            cell.dayTv.setTextColor(
                if (picked || day.isFree || day.isToday) 0xFFFFFFFF.toInt() else 0xFFB6C2D0.toInt()
            )
        }
    }

    /** 칸을 누르면 그날이 펴진다. 같은 칸을 또 누르면 접힌다. */
    private fun onDayTap(day: TwoWeekSchedule.Day) {
        if (selectedDay == day.dayStartMs) { hideDayDetail(); return }
        selectedDay = day.dayStartMs
        dayDetailTitleTv.text = "${monthOfDay(day.dayStartMs)} ${day.dayOfMonth}일 (${day.weekday})"
        dayDetailBody.removeAllViews()
        if (day.isFree) {
            val tv = mkText(11f, 0xFF8E9BAC.toInt())
            tv.text = "아무것도 없어요. 이 날 받으시면 됩니다."
            dayDetailBody.addView(tv)
        } else {
            for ((n, item) in day.items.withIndex()) {
                dayDetailBody.addView(detailRow(item), rowLp(if (n == 0) 0f else 7f))
            }
        }
        dayDetailBox.visibility = View.VISIBLE
        paintCells()
    }

    private fun detailRow(
        item: TwoWeekSchedule.Item
    ): LinearLayout {
        // 왼쪽에 색 막대 하나 — 종류(시공/A/S/내 일정)를 색으로 말한다.
        //   setStroke 는 네 면을 다 그려서 상자가 된다. 막대를 따로 세워야 한다.
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        val bar = View(context).apply { setBackgroundColor(toneColor(item.tone)) }
        row.addView(bar, LayoutParams(dp(2f), LayoutParams.MATCH_PARENT))

        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(9f), 0, 0, 0)
        }
        val head = mkText(11f, 0xFFFFFFFF.toInt(), bold = true)
        val mark = if (item.tone == TwoWeekSchedule.Tone.AS) "🔧 A/S · " else ""
        head.text = mark + item.time + " · " + item.who
        texts.addView(head)
        val detail = item.detail.trim()
        if (detail.isNotEmpty()) {
            val sub = mkText(10f, 0xFF8E9BAC.toInt())
            sub.text = detail
            sub.maxLines = 2
            sub.ellipsize = android.text.TextUtils.TruncateAt.END
            texts.addView(sub, rowLp(1f))
        }
        row.addView(texts, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        return row
    }

    /**
     * "9월 8일" — 언제부터 문의 중인지. 해가 다르면 **연도를 말한다.**
     *   작년 11월 건이 그냥 "11월 8일" 로 나와 **미래처럼 읽혔다**(실제 자료, 2026-09-20).
     */
    private fun monthDayOf(ms: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        val nowY = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val y = cal.get(java.util.Calendar.YEAR)
        val md = "${cal.get(java.util.Calendar.MONTH) + 1}월 ${cal.get(java.util.Calendar.DAY_OF_MONTH)}일"
        return when {
            y == nowY -> md
            y == nowY - 1 -> "작년 $md"
            else -> "${y}년 $md"
        }
    }

    private fun monthOfDay(ms: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return "${cal.get(java.util.Calendar.MONTH) + 1}월"
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
        // 🔁 또 거는 사람 = 보라. 신규(노랑)도 기존(파랑)도 아닌 **그 사이**라 색도 따로.
        st.status == IncomingCallOverlay.CallerStatus.REPEAT -> 0xFF8B5CF6.toInt()
        st.status == IncomingCallOverlay.CallerStatus.SCHEDULED -> 0xFF12B886.toInt()
        st.status == IncomingCallOverlay.CallerStatus.COMPLETED -> 0xFFF0436A.toInt()
        else -> 0xFF3182F6.toInt()
    }

    private fun chipTextColor(st: IncomingCallOverlay.CallerState): Int = when (st.status) {
        IncomingCallOverlay.CallerStatus.NEW -> 0xFFFFC24D.toInt()
        IncomingCallOverlay.CallerStatus.REPEAT -> 0xFFC4AFFF.toInt()
        IncomingCallOverlay.CallerStatus.SCHEDULED -> 0xFF3FE0AE.toInt()
        IncomingCallOverlay.CallerStatus.COMPLETED -> 0xFFFF8FA9.toInt()
        else -> 0xFF7FB4FF.toInt()
    }

    fun bind(st: IncomingCallOverlay.CallerState) {
        val isNew = !st.loading && st.status == IncomingCallOverlay.CallerStatus.NEW
        strip.setBackgroundColor(stripColor(st))

        nameTv.text = st.displayName
        nameTv.textSize = if (isNew) 19f else 17f

        val isRepeat = !st.loading && st.status == IncomingCallOverlay.CallerStatus.REPEAT
        val chipLabel = when {
            st.loading -> "찾는 중…"
            isNew -> "✨ 신규"
            // 🔁 **그 숫자 자체가 신호**다 — 세 번 거는 사람은 사려는 사람.
            isRepeat -> "🔁 ${st.callNo}번째 통화"
            // 다음 시공이 잡혀 있으면 그 날짜가 제일 급하다.
            st.scheduleUpcoming && st.scheduleLabel != null -> st.scheduleLabel
            // 아니면 **"2번 시공"** — "기존 손님" 보다 훨씬 많은 말을 한다. (2026-09-19 사장님)
            //   언제 했는지는 바로 밑 '지난 시공' 줄에 있으니 칩에서 또 말하지 않는다.
            st.doneCount > 0 -> "${st.doneCount}번 시공"
            st.scheduleLabel != null -> st.scheduleLabel
            st.status == IncomingCallOverlay.CallerStatus.COMPLETED -> "✅ 시공 완료"
            else -> "기존 손님"
        }
        chipTv.text = chipLabel
        chipTv.setTextColor(chipTextColor(st))
        chipTv.background = roundBg((stripColor(st) and 0x00FFFFFF) or (0x38 shl 24), 999f)

        // 이름이 번호 그대로면 아래 번호줄은 중복이라 안 띄운다.
        val formatted = PhoneNumberFormatter.format(st.phoneNumber)
        val base = if (st.displayName == formatted) "저장 안 된 번호" else formatted
        subTv.text = if (isRepeat) {
            // 언제부터 재고 중인지 — 오래 재고 있으면 이번엔 밀어붙일 때다.
            val since = st.firstContactAt?.let { " · ${monthDayOf(it)}부터 문의 중" } ?: ""
            "아직 시공 전$since"
        } else base
        subTv.visibility = View.VISIBLE

        show(addrTv, st.address?.let { "📍  $it" })
        show(moneyTv, st.moneyLabel?.let { "💰  $it" })

        newBox.visibility = if (isNew) View.VISIBLE else View.GONE
        if (isNew) {
            newTitleTv.text = "처음 걸려온 번호예요 · 새 문의"
            // "주고받은 문자도, 지난 통화도 없어요"는 뺐다 — **이미 아는 얘기**라 자리만 먹는다.
            //   그 자리에 2주 일정이 들어간다. (2026-09-19 사장님)
            newDescTv.visibility = View.GONE
        }

        // 📅 처음 거는 사람은 보여줄 과거가 없으니 **일정을 바로 펼친다.**
        //   시공했던 손님은 기억(요약·문자)이 먼저라 버튼으로 접어 둔다.
        // 처음 거는 사람은 보여줄 과거가 없어서, **또 거는 사람은 사려는 사람**이라 바로 펼친다.
        schedAlwaysOpen = isNew || isRepeat
        bindSchedule(st.schedule)

        // 🧾 지난 시공 — 없으면 아예 안 띄운다.
        pastBox.visibility = if (st.pastJobLines.isEmpty()) View.GONE else View.VISIBLE
        if (st.pastJobLines.isNotEmpty()) {
            pastLabelTv.text = "지난 시공"
            pastBigTv.text = st.pastJobLines[0]
            val more = st.pastJobLines.getOrNull(1)
            pastSmallTv.visibility = if (more == null) View.GONE else View.VISIBLE
            if (more != null) pastSmallTv.text = "그 전 — $more"
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
            isNew -> "누르면 열려요 · 끊으면 바로 손님 등록"
            else -> "누르면 이 손님 대화로"
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
