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

    /**
     * 📵 **그 인증문자, 못 갔다.** (2026-09-30 사장님)
     *   say = 쓰는 분께 보여줄 말 · act = 다음에 누를 것(report/rephone/retry).
     *   **말과 다음 할 일을 서버가 준다** — 문구를 고치려고 앱을 새로 올리지 않아도 된다.
     */
    data class SmsFail(val code: String, val reason: String, val say: String, val act: String)
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
        // 숫자 코드는 사장님께 아무 뜻이 없다. 무슨 일인지 + 뭘 하면 되는지만.
        else -> "인터넷이 잠깐 끊겼어요 — 연결 확인 후 다시 해주세요"
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

    /**
     * 📵 **그 문자 갔어요?** — 코드 입력 화면에서 한 번 물어본다. (2026-09-30 사장님)
     *
     * 서버는 45초쯤에 통신사 회신을 받아 이미 알고 있다. 전엔 그걸 **사장님 슬랙에만** 말하고
     * 쓰는 분에겐 아무 말도 안 했다 — 오지 않을 문자를 기다리게 뒀다.
     *
     * ⚠️ **모르면 아무 말도 안 한다.** 못 간 게 확실할 때만 SmsFail 을 돌려준다 —
     *    인터넷이 잠깐 끊긴 걸 「문자 안 갔어요」로 잘못 말하면 될 가입도 막는다.
     */
    suspend fun smsResult(phone: String): SmsFail? = withContext(Dispatchers.IO) {
        runCatching {
            val digits = phone.filter { it.isDigit() }
            if (digits.length < 9) return@runCatching null
            val req = Request.Builder().url("$baseUrl/api/auth/sms-result?phone=$digits").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val o = parseBody(resp.body?.string())
                if (o.optString("state") != "failed") return@use null
                val say = o.optString("say").trim()
                if (say.isEmpty()) return@use null
                SmsFail(
                    code = o.optString("code"),
                    reason = o.optString("reason"),
                    say = say,
                    act = o.optString("act").ifBlank { "retry" }
                )
            }
        }.getOrNull()
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
