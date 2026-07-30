package com.detailline.callfollowcrm.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 로그인 세션 토큰 저장고 (보안 §D-4 · docs/SERVER_HANDOFF_session_token.md).
 *
 * verify-code 성공 시 서버가 준 `sessionToken` 을 암호화 저장하고,
 * [com.detailline.callfollowcrm.ai.SessionAuthInterceptor] 가 모든 api.si0in.kr 요청에
 * `Authorization: Bearer <token>` 로 붙인다.
 *
 *  - 저장: EncryptedSharedPreferences(권장). 기기 keystore 문제로 실패하면 일반 SharedPreferences 폴백(동작 우선).
 *  - 토큰 형식/서명/만료 검증은 **서버 소관**. 앱은 값을 보관·부착만 하고 파싱/신뢰하지 않는다(계약).
 *  - 프로세스 전역 싱글턴 — 인터셉터가 DI 없이 [current] 로 읽는다.
 *  - OTP 로그인이 꺼져 있으면(AppConfig.SMS_SIGNUP_ENABLED=false) 토큰이 생기지 않아 전체가 무영향(inert).
 */
class SessionTokenStore private constructor(context: Context) {

    private val prefs: SharedPreferences = buildPrefs(context.applicationContext)

    @Volatile
    var token: String? = prefs.getString(KEY_TOKEN, null)
        private set

    @Volatile
    var expMs: Long = prefs.getLong(KEY_EXP, 0L)
        private set

    /** 401(토큰 만료·무효) 감지 시 true → AppRoot 가 재로그인(OTP)로 유도. */
    private val _needsReauth = MutableStateFlow(false)
    val needsReauth: StateFlow<Boolean> = _needsReauth

    /** verify-code 성공 시 저장. */
    fun save(token: String, expMs: Long) {
        this.token = token
        this.expMs = expMs
        _needsReauth.value = false
        runCatching { prefs.edit().putString(KEY_TOKEN, token).putLong(KEY_EXP, expMs).apply() }
    }

    /** 서버가 401 → 토큰 폐기 + 재로그인 신호. */
    fun invalidate() {
        token = null
        expMs = 0L
        runCatching { prefs.edit().remove(KEY_TOKEN).remove(KEY_EXP).apply() }
        _needsReauth.value = true
    }

    /** 재로그인 화면으로 이동시킨 뒤 신호 소비(중복 이동 방지). */
    fun clearReauthFlag() { _needsReauth.value = false }

    fun hasValidToken(nowMs: Long): Boolean = !token.isNullOrBlank() && expMs > nowMs

    companion object {
        private const val KEY_TOKEN = "session_token"
        private const val KEY_EXP = "session_token_exp_ms"
        private const val FILE = "session_secure"

        @Volatile
        private var INSTANCE: SessionTokenStore? = null

        /** 인터셉터가 DI 없이 토큰을 읽기 위한 전역 참조(초기화 전이면 null → 헤더 미부착). */
        val current: SessionTokenStore? get() = INSTANCE

        fun get(context: Context): SessionTokenStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionTokenStore(context.applicationContext).also { INSTANCE = it }
            }

        private fun buildPrefs(ctx: Context): SharedPreferences = runCatching {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as SharedPreferences
        }.getOrElse { e ->
            // 일부 기기에서 keystore/keyset 손상으로 create 가 던질 수 있음 → 손상 파일 지우고 일반 prefs 폴백(동작 우선).
            Log.w("SessionTokenStore", "EncryptedSharedPreferences 실패, 일반 prefs 폴백: ${e.message}")
            runCatching { ctx.deleteSharedPreferences(FILE) }
            ctx.getSharedPreferences("${FILE}_plain", Context.MODE_PRIVATE)
        }
    }
}
