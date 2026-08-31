package com.detailline.callfollowcrm.data.calendar

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 구글 캘린더 접근 인증 (Google Identity **Authorization API**).
 *
 * 동생분 google_sign_in 7.x(Credential Manager) 흐름을 Kotlin 의 Authorization API 로 이식.
 * - 캘린더 scope 액세스 토큰을 받아 [CalendarApi] 호출(Bearer)에 쓴다.
 * - 이미 동의돼 있으면 **UI 없이(silent)** 토큰을 재발급 → 백그라운드 동기화용.
 * - 처음이거나 재동의가 필요하면 [AuthResult.NeedsConsent] 로 IntentSender 를 돌려주고,
 *   호출측(설정 화면)이 그걸 띄워 동의를 받은 뒤 [tokenFromConsentResult] 로 마무리한다.
 *
 * 액세스 토큰은 짧게 살아(~1h) **저장하지 않는다.** 매 동기화 전에 [getTokenSilently] 로 새로 받는다.
 * (구글이 동의를 캐시하므로 재호출은 UI 없이 즉시 새 토큰을 준다.)
 */
class GoogleCalendarConnection(private val appContext: Context) {

    private val calendarScope = Scope("https://www.googleapis.com/auth/calendar")

    sealed interface AuthResult {
        data class Success(val accessToken: String) : AuthResult
        /** 사용자 동의 UI 필요 — 이 IntentSender 를 띄우고 결과를 [tokenFromConsentResult] 로 넘긴다. */
        data class NeedsConsent(val intentSender: IntentSender) : AuthResult
    }

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(calendarScope))
            .build()

    /**
     * 인증 시도. 이미 동의됐으면 [AuthResult.Success](토큰), 아니면 [AuthResult.NeedsConsent].
     * 이 함수 자체는 UI 를 띄우지 않는다(동의 UI 는 호출측이 IntentSender 로 띄움).
     */
    suspend fun authorize(): AuthResult = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(appContext).authorize(request())
            .addOnSuccessListener { result ->
                val token = result.accessToken
                when {
                    token != null && !result.hasResolution() ->
                        cont.resume(AuthResult.Success(token))
                    result.pendingIntent != null ->
                        cont.resume(AuthResult.NeedsConsent(result.pendingIntent!!.intentSender))
                    token != null ->
                        cont.resume(AuthResult.Success(token))
                    else ->
                        cont.resumeWithException(IllegalStateException("액세스 토큰도 동의 요청도 없음"))
                }
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** UI 없이 토큰만 (백그라운드 동기화용). 동의가 필요하거나 실패하면 null. */
    suspend fun getTokenSilently(): String? =
        when (val r = runCatching { authorize() }.getOrNull()) {
            is AuthResult.Success -> r.accessToken
            else -> null
        }

    /** 동의 UI 결과(Intent)에서 액세스 토큰 뽑기. 실패하면 null. */
    fun tokenFromConsentResult(data: Intent?): String? =
        try {
            Identity.getAuthorizationClient(appContext)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        } catch (e: Exception) {
            null
        }
}
