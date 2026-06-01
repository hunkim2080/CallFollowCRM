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
 * 시공접수서 (고객 자가확인 폼) — 맥미니 서버 §19 (2026-06-02 1:1 재작성).
 *
 * 프로토 design-preview/ringgo-redesign.html 의 openQuote/finalizeQuote 1:1.
 * 서버 폼은 사장님 확정 시공일·견적 항목·계약금까지 "표시만" → 고객은 주소·연락처만 입력.
 *
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

    /** 견적 항목 1줄 — 프로토 estimate_items[] 의 한 원소. price_man = 만원 단위. */
    data class EstimateItem(
        val name: String,
        val priceMan: Int,
        val unit: String? = null,
        val area: Double? = null
    )

    /**
     * 접수서 발급. 성공 시 [Issued] (특히 url), 실패 시 Result.failure.
     *
     *   §19 (2026-06-02) schema — 사장님 견적 데이터까지 함께 전송하면 서버 폼이 그걸 "표시"함.
     *   견적 미입력 시(전부 null/0) 서버 폼이 "미정" 으로 노출.
     */
    suspend fun issue(
        phone: String,
        customerName: String? = null,
        deviceId: String? = null,
        ownerPhone: String? = null,
        bizName: String? = null,
        scheduledAtMs: Long = 0L,
        scheduledDays: Int = 1,
        estimateItems: List<EstimateItem> = emptyList(),
        totalMan: Int = 0,
        depositAmountKrw: Long = 0L,
        depositMode: String = "none",
        depositRatioPct: Int? = null
    ): Result<Issued> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("phone", phone)
                customerName?.takeIf { it.isNotBlank() }?.let { put("customer_name", it) }
                deviceId?.takeIf { it.isNotBlank() }?.let { put("device_id", it) }
                ownerPhone?.takeIf { it.isNotBlank() }?.let { put("owner_phone", it) }
                bizName?.takeIf { it.isNotBlank() }?.let { put("biz_name", it) }
                put("scheduled_at_ms", scheduledAtMs)
                put("scheduled_days", scheduledDays)
                put("estimate_items", JSONArray().apply {
                    estimateItems.forEach { item ->
                        put(JSONObject().apply {
                            put("name", item.name)
                            put("price_man", item.priceMan)
                            item.unit?.let { put("unit", it) }
                            item.area?.let { put("area", it) }
                        })
                    }
                })
                put("total_man", totalMan)
                put("deposit_mode", depositMode)
                put("deposit_amount_krw", depositAmountKrw)
                if (depositMode == "ratio" && depositRatioPct != null) {
                    put("deposit_ratio_pct", depositRatioPct)
                }
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
