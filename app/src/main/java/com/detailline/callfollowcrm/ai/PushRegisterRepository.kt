package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * FCM 토큰을 서버에 등록 — POST /api/push/register { phone, token, platform:"android" }.
 *   서버가 phone↔token 매핑을 보관했다가 협업 요청 발생 시 그 토큰으로 즉시 푸시.
 *   실패해도 안전(Result) — 폴링 알림이 안전망. 핸드오프: docs/SERVER_HANDOFF_fcm_push.md
 */
class PushRegisterRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)   // 전체 호출 상한 — 재시도/route 누적 hang 방지 (2026-08-12 오프라인 감사)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun register(phone: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val p = phone.filter { it.isDigit() }
            if (p.length < 9 || token.isBlank()) throw IOException("invalid phone/token")
            val payload = JSONObject().apply {
                put("phone", p); put("token", token); put("platform", "android")
            }
            val req = Request.Builder().url("$baseUrl/api/push/register")
                .post(payload.toString().toRequestBody(jsonMedia)).build()
            client.newCall(req).execute().use { resp ->
                // 번호는 로그에 안 남김(개인정보) — 결과 코드만.
                android.util.Log.i("PushRegister", "register code=${resp.code}")
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            }
        }.onFailure { android.util.Log.w("PushRegister", "register FAILED: ${it.message}") }
    }
}
