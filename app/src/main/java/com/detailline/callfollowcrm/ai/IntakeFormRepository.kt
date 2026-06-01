package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 시공접수서 (고객 자가확인 폼) — 맥미니 서버 §19 (2026-06-02).
 *
 * 서버가 발급한 링크를 사장님이 고객에게 보내면, 고객이 브라우저에서 주소·시공범위를 직접 입력.
 *   - POST /api/intake-form/issue  → {token, url, issued_at_ms, expires_at_ms}
 *   - GET  /api/intake-form/status → 제출 여부 polling (추후)
 *
 * 자동발송 X 정책: 서버는 URL 만 발급, 앱이 SMS 본문에 prefill → 사장님이 ▶ 직접 발송.
 */
class IntakeFormRepository(
    private val baseUrl: String = "http://100.86.114.49:8000"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    data class Issued(
        val token: String,
        val url: String,
        val issuedAtMs: Long,
        val expiresAtMs: Long
    )

    /** 접수서 발급. 성공 시 [Issued] (특히 url), 실패 시 Result.failure. */
    suspend fun issue(
        phone: String,
        customerName: String? = null,
        deviceId: String? = null,
        ownerPhone: String? = null,
        expectedScope: List<String> = emptyList()
    ): Result<Issued> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("phone", phone)
                customerName?.takeIf { it.isNotBlank() }?.let { put("customer_name", it) }
                deviceId?.takeIf { it.isNotBlank() }?.let { put("device_id", it) }
                ownerPhone?.takeIf { it.isNotBlank() }?.let { put("owner_phone", it) }
                if (expectedScope.isNotEmpty()) put("expected_scope", JSONArray(expectedScope))
            }
            val req = Request.Builder()
                .url("$baseUrl/api/intake-form/issue")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val obj = JSONObject(body)
                Issued(
                    token = obj.getString("token"),
                    url = obj.getString("url"),
                    issuedAtMs = obj.optLong("issued_at_ms"),
                    expiresAtMs = obj.optLong("expires_at_ms")
                )
            }
        }
    }
}
