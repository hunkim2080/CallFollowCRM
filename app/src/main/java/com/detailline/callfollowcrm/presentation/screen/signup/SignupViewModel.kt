package com.detailline.callfollowcrm.presentation.screen.signup

import android.content.Context
import android.provider.Telephony
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.ai.AuthException
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 회원가입(폰 인증번호) — docs/ANDROID_HANDOFF_signup_auth.md.
 *   전화번호 → [인증번호 받기] → 6자리 입력 → 검증 → enrolled/member/waitlisted.
 *   인증문자는 READ_SMS 로 자동 감지해 자동 입력(있으면). 수동 입력은 항상 가능.
 */
class SignupViewModel(private val container: AppContainer) : ViewModel() {

    enum class Phase { PHONE, CODE, WAITLIST }

    data class UiState(
        val phase: Phase = Phase.PHONE,
        val phone: String = "",
        val code: String = "",
        val loading: Boolean = false,
        val error: String? = null,     // 일회성 토스트 문구
        val info: String? = null,      // 일회성 안내 토스트
        val resendSec: Int = 0         // >0 = 재발송까지 남은 초(카운트다운)
    )

    /** enrolled/member 최종 결과 — 화면이 받아서 bizPhone 저장 + 화면 전환. waitlisted 는 phase 로 처리. */
    data class Outcome(val status: String, val phone: String, val freeUntilMs: Long?, val freeDays: Int?)

    private val _state = MutableStateFlow(
        // 대기열이면(pendingWaitlist) 저장된 번호로 대기 화면부터.
        if (container.preferences.pendingWaitlist)
            UiState(phase = Phase.WAITLIST, phone = container.preferences.bizPhone)
        else UiState()
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _outcomes = MutableSharedFlow<Outcome>(extraBufferCapacity = 1)
    val outcomes: SharedFlow<Outcome> = _outcomes.asSharedFlow()

    private var smsJob: Job? = null
    private var resendJob: Job? = null

    fun onPhoneChange(v: String) { _state.update { it.copy(phone = v) } }
    fun onCodeChange(v: String) {
        val digits = v.filter { it.isDigit() }.take(6)
        _state.update { it.copy(code = digits) }
    }
    fun consumeError() { _state.update { it.copy(error = null) } }
    fun consumeInfo() { _state.update { it.copy(info = null) } }

    val phoneOk: Boolean get() = _state.value.phone.filter { it.isDigit() }.length >= 10

    /** ① 인증번호 발송. */
    fun requestCode() {
        val s = _state.value
        if (s.loading) return
        val digits = s.phone.filter { it.isDigit() }
        if (digits.length < 10) { _state.update { it.copy(error = "전화번호를 정확히 입력해주세요") }; return }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            container.authRepository.requestCode(s.phone)
                .onSuccess {
                    _state.update { st -> st.copy(phase = Phase.CODE, loading = false, code = "", info = "인증번호를 보냈어요. 잠시만요…") }
                    startResendCountdown(60)
                    startSmsAutoRead(System.currentTimeMillis() - 3000)
                }
                .onFailure { e ->
                    val msg = (e as? AuthException)?.message ?: "발송에 실패했어요. 잠시 후 다시 시도해주세요"
                    _state.update { st -> st.copy(loading = false, error = msg) }
                    // 60초 간격 제한이면 카운트다운으로 재발송 막기.
                    if ((e as? AuthException)?.httpCode == 429 && msg.contains("1분")) startResendCountdown(60)
                }
        }
    }

    /** ② 인증번호 검증. */
    fun verify() {
        val s = _state.value
        if (s.loading) return
        if (s.code.length != 6) { _state.update { it.copy(error = "인증번호 6자리를 입력해주세요") }; return }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            container.authRepository.verifyCode(s.phone, s.code)
                .onSuccess { v ->
                    smsJob?.cancel()
                    when (v.status) {
                        "waitlisted" -> {
                            // 번호는 인증됨 → 저장 + 대기열 플래그. 다음 실행에도 대기 화면.
                            container.preferences.bizPhone = s.phone.filter { it.isDigit() }
                            container.preferences.pendingWaitlist = true
                            _state.update { st -> st.copy(phase = Phase.WAITLIST, loading = false) }
                        }
                        else -> { // enrolled / member
                            container.preferences.pendingWaitlist = false
                            v.freeUntilMs?.let { container.preferences.signupFreeUntilMs = it }
                            _state.update { st -> st.copy(loading = false) }
                            _outcomes.tryEmit(Outcome(v.status, s.phone.filter { it.isDigit() }, v.freeUntilMs, v.freeDays))
                        }
                    }
                }
                .onFailure { e ->
                    val msg = (e as? AuthException)?.message ?: "인증에 실패했어요. 다시 시도해주세요"
                    _state.update { st -> st.copy(loading = false, error = msg) }
                }
        }
    }

    /** 대기열 화면에서 '다시 확인' — verify 재호출 말고 /api/beta/check 폴링(등업되면 통과). */
    fun recheckWaitlist() {
        val phone = _state.value.phone.filter { it.isDigit() }
        if (_state.value.loading || phone.length < 9) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val r = container.betaCheckRepository.check(phone)
            _state.update { it.copy(loading = false) }
            if (r?.ok == true) {
                container.preferences.pendingWaitlist = false
                _outcomes.tryEmit(Outcome("member", phone, container.preferences.signupFreeUntilMs.takeIf { it > 0L }, null))
            } else {
                _state.update { it.copy(info = "아직 자리가 나지 않았어요. 준비되면 문자로 알려드릴게요 🙂") }
            }
        }
    }

    fun backToPhone() {
        smsJob?.cancel(); resendJob?.cancel()
        _state.update { UiState(phone = it.phone) }
    }

    private fun startResendCountdown(sec: Int) {
        resendJob?.cancel()
        resendJob = viewModelScope.launch {
            var n = sec
            while (n > 0) {
                _state.update { it.copy(resendSec = n) }
                delay(1000); n--
            }
            _state.update { it.copy(resendSec = 0) }
        }
    }

    /** 인증문자 자동 읽기 — 수신함에서 '인증번호 NNNNNN' 을 찾아 자동 입력+검증. best-effort(READ_SMS). */
    private fun startSmsAutoRead(sinceMs: Long) {
        smsJob?.cancel()
        smsJob = viewModelScope.launch {
            repeat(30) {   // 2초 × 30 = 60초
                delay(2000)
                if (_state.value.phase != Phase.CODE) return@launch
                val code = withContext(Dispatchers.IO) { readCodeFromInbox(container.appContext, sinceMs) }
                if (code != null && _state.value.code != code) {
                    _state.update { it.copy(code = code, info = "인증번호를 자동으로 입력했어요") }
                    verify()
                    return@launch
                }
            }
        }
    }

    override fun onCleared() { smsJob?.cancel(); resendJob?.cancel() }

    private companion object {
        private val SIX = Regex("(\\d{6})")
        /** 수신함에서 최근('sinceMs' 이후) '인증번호'가 든 문자의 6자리 코드. 실패/권한없음이면 null. */
        fun readCodeFromInbox(ctx: Context, sinceMs: Long): String? = runCatching {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(sinceMs.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                var scanned = 0
                while (c.moveToNext() && scanned < 8) {
                    scanned++
                    val body = if (bodyIdx >= 0) c.getString(bodyIdx) ?: "" else ""
                    if (body.contains("인증번호") || body.contains("시공막내")) {
                        SIX.find(body)?.groupValues?.get(1)?.let { return@use it }
                    }
                }
                null
            }
        }.getOrNull()
    }
}
