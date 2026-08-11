package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 서버가 detail 문구를 그대로 주면 토스트로 보여주기 위한 예외(코드+문구). */
class AuthException(val httpCode: Int, override val message: String) : Exception(message)

/**
 * 회원가입(폰 인증번호) — 서버 추가86(cowork). docs/ANDROID_HANDOFF_signup_auth.md.
 *   ① POST /api/auth/request-code {phone}      → { ok, expiresInSec }
 *   ② POST /api/auth/verify-code  {phone,code} → { ok, status(enrolled|member|waitlisted), freeUntilMs?, freeDays? }
 *   실패는 Result.failure(AuthException(code, detail)) — 화면이 detail 을 토스트로 보여줌.
 */
class AuthRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)   // 전체 호출 상한 — 재시도/route 누적 hang 방지 (2026-08-12 오프라인 감사)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    data class CodeRequested(val expiresInSec: Int)
    data class Verified(
        val status: String,
        val freeUntilMs: Long?,
        val freeDays: Int?,
        // 보안 §B-1 계약: 인증 성공(member/enrolled) 시 서버가 세션토큰 발급. waitlisted 는 토큰 없음(=미인증).
        val sessionToken: String?,
        val sessionTokenExpMs: Long
    )

    private fun defaultMsg(code: Int): String = when (code) {
        400 -> "입력을 확인해주세요"
        429 -> "잠시 후 다시 시도해주세요"
        502, 503 -> "지금은 문자 발송이 준비 중이에요. 잠시 후 다시 시도해주세요"
        else -> "네트워크 오류가 났어요 (코드 $code)"
    }

    /** ① 인증번호 발송 요청. phone 은 하이픈 있어도 됨(서버가 처리). */
    suspend fun requestCode(phone: String): Result<CodeRequested> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().put("phone", phone.trim()).toString().toRequestBody(jsonMedia)
            val req = Request.Builder().url("$baseUrl/api/auth/request-code").post(payload).build()
            client.newCall(req).execute().use { resp ->
                val o = parseBody(resp.body?.string())
                if (!resp.isSuccessful) {
                    throw AuthException(resp.code, o.optString("detail").ifBlank { defaultMsg(resp.code) })
                }
                CodeRequested(expiresInSec = o.optInt("expiresInSec", 300))
            }
        }
    }

    /** ② 인증번호 검증. 결과 status = enrolled / member / waitlisted. */
    suspend fun verifyCode(phone: String, code: String): Result<Verified> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("phone", phone.filter { it.isDigit() })
                .put("code", code.trim())
                .toString().toRequestBody(jsonMedia)
            val req = Request.Builder().url("$baseUrl/api/auth/verify-code").post(payload).build()
            client.newCall(req).execute().use { resp ->
                val o = parseBody(resp.body?.string())
                if (!resp.isSuccessful) {
                    throw AuthException(resp.code, o.optString("detail").ifBlank { defaultMsg(resp.code) })
                }
                Verified(
                    status = o.optString("status").ifBlank { "member" },
                    freeUntilMs = if (o.has("freeUntilMs") && !o.isNull("freeUntilMs")) o.optLong("freeUntilMs") else null,
                    freeDays = if (o.has("freeDays") && !o.isNull("freeDays")) o.optInt("freeDays") else null,
                    sessionToken = o.optString("sessionToken").ifBlank { null },
                    sessionTokenExpMs = if (o.has("sessionTokenExpMs") && !o.isNull("sessionTokenExpMs")) o.optLong("sessionTokenExpMs") else 0L
                )
            }
        }
    }

    /** 개인정보 동의 기록 — POST /api/consent (docType: "required" | "optional_quality"). best-effort. (추가97 2026-07-06) */
    suspend fun postConsent(phone: String, docType: String, agreed: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("phone", phone.filter { it.isDigit() })
                .put("docType", docType)
                .put("agreed", agreed)
                .toString().toRequestBody(jsonMedia)
            val req = Request.Builder().url("$baseUrl/api/consent").post(payload).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw AuthException(resp.code, "동의 기록 실패")
            }
            Unit
        }
    }

    private fun parseBody(text: String?): JSONObject =
        runCatching { JSONObject(text ?: "{}") }.getOrDefault(JSONObject())
}
