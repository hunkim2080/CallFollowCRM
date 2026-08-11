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
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)   // 전체 호출 상한 — 재시도/route 누적 hang 방지 (2026-08-12 오프라인 감사)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    data class Issued(
        val token: String,
        val url: String,
        val issuedAtMs: Long,
        val expiresAtMs: Long
    )

    /** 견적 한 줄 — price/area 는 만원 단위(프로토 lineTotal 그대로). */
    data class QuoteIssueItem(val name: String, val price: Int, val unit: String, val area: Double? = null)

    data class IssuedQuote(
        val token: String,
        val url: String,
        val smsDraft: String,
        val issuedAtMs: Long,
        val expiresAtMs: Long,
        /** 서버가 같은 현장 미제출 접수서가 있어 새로 안 만들고 기존 링크를 재사용(갱신)했는지. (추가95② 2026-07-06 cowork) */
        val reused: Boolean = false
    )

    /**
     * 시공접수서 발급 v2 (맥미니 §19.2, 2026-06-03) — POST /api/quote/issue.
     *   req: 견적(items 만원)+시공일+계약금+사업자정보. res: smsDraft(앱이 SMS 본문 prefill → 사장님 ▶).
     */
    suspend fun issueQuote(
        customerName: String,
        customerPhone: String,
        items: List<QuoteIssueItem>,
        total: Int,
        workYear: Int, workMonth: Int, workDay: Int, workDays: Int,
        depositMode: String, depositValue: Int,
        bizName: String, bizOwner: String, bizNo: String, bizAddr: String,
        bizPhone: String, bizSeal: String, bizValidDays: Int,
        devicePhone: String, deviceId: String,
        ownerMemo: String = "",
        vatIncluded: Boolean = false
    ): Result<IssuedQuote> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("customerName", customerName)
                put("customerPhone", customerPhone)
                put("items", JSONArray().apply {
                    items.forEach { it ->
                        put(JSONObject().apply {
                            put("name", it.name)
                            put("price", it.price)
                            put("unit", it.unit)
                            it.area?.let { a -> put("area", a) }
                        })
                    }
                })
                put("total", total)
                put("workYear", workYear); put("workMonth", workMonth)
                put("workDay", workDay); put("workDays", workDays)
                put("depositMode", depositMode); put("depositValue", depositValue)
                put("biz", JSONObject().apply {
                    put("name", bizName); put("owner", bizOwner); put("bizNo", bizNo)
                    put("addr", bizAddr); put("phone", bizPhone); put("seal", bizSeal)
                    put("validDays", bizValidDays)
                })
                put("devicePhone", devicePhone)
                put("deviceId", deviceId)
                // 특이사항 메모 — 서버가 owner_memo 로 저장·폼 표시 (SERVER_HANDOFF_intake_fixes ③). (2026-07-06)
                if (ownerMemo.isNotBlank()) put("ownerMemo", ownerMemo)
                // 부가세 별도(false)/포함(true) — 서버가 접수서에 명시(분쟁 방지). (2026-07-06 사장님, 서버 render=cowork)
                put("vatIncluded", vatIncluded)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/quote/issue")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val obj = JSONObject(resp.body?.string().orEmpty())
                IssuedQuote(
                    token = obj.getString("token"),
                    url = obj.getString("url"),
                    smsDraft = obj.optString("smsDraft"),
                    issuedAtMs = obj.optLong("issuedAtMs"),
                    expiresAtMs = obj.optLong("expiresAtMs"),
                    reused = obj.optBoolean("reused", false)
                )
            }
        }
    }

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

    data class QuoteSubmission(
        val token: String,
        val customerName: String,
        val customerPhone: String,
        val submittedAtMs: Long?,
        val address: String?,
        val dong: String?,
        val memo: String?,
        val confirmedDateIso: String?,
        val workYear: Int, val workMonth: Int, val workDay: Int, val workDays: Int,
        val total: Int,
        /** 계약금 — depositMode: none|ratio|fixed, depositValue: %(ratio) 또는 만원(fixed). 프로토 depVal 그대로. */
        val depositMode: String,
        val depositValue: Int,
        val source: String?,
        /** 견적서 발급 때 사장님이 고른 시공 항목 이름들 (접수 확인 문자 '시공내용'). 서버 estimate_items[].name. */
        val estimateItems: List<String> = emptyList()
    )

    /** 사장님 폴링 — GET /api/quote/submissions. submittedAtMs 비-null = 고객 제출 완료. */
    suspend fun submissions(devicePhone: String, sinceMs: Long): Result<List<QuoteSubmission>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dp = java.net.URLEncoder.encode(devicePhone, "UTF-8")
                val req = Request.Builder()
                    .url("$baseUrl/api/quote/submissions?devicePhone=$dp&sinceMs=$sinceMs&limit=50")
                    .get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val obj = JSONObject(resp.body?.string().orEmpty())
                    val arr = obj.optJSONArray("items") ?: JSONArray()
                    (0 until arr.length()).map { i ->
                        val itObj = arr.getJSONObject(i)
                        val payload = itObj.optJSONObject("payload")
                        val survey = itObj.optJSONObject("survey")
                        fun JSONObject?.str(k: String) = this?.optString(k)?.takeIf { s -> s.isNotBlank() && s != "null" }
                        QuoteSubmission(
                            token = itObj.getString("token"),
                            customerName = itObj.optString("customerName"),
                            customerPhone = itObj.optString("customerPhone"),
                            submittedAtMs = itObj.optLong("submittedAtMs").takeIf { v -> v > 0 },
                            address = payload.str("address"),
                            dong = payload.str("dong"),
                            memo = payload.str("memo"),
                            confirmedDateIso = itObj.optString("confirmedDate").takeIf { s -> s.isNotBlank() && s != "null" },
                            workYear = itObj.optInt("workYear"), workMonth = itObj.optInt("workMonth"),
                            workDay = itObj.optInt("workDay"), workDays = itObj.optInt("workDays", 1),
                            total = itObj.optInt("total"),
                            depositMode = itObj.optString("depositMode").takeIf { s -> s.isNotBlank() && s != "null" } ?: "none",
                            depositValue = itObj.optInt("depositValue"),
                            source = survey.str("source"),
                            estimateItems = itObj.optJSONArray("estimate_items")?.let { arr ->
                                (0 until arr.length()).mapNotNull { idx ->
                                    arr.optJSONObject(idx)?.optString("name")?.takeIf { nm -> nm.isNotBlank() && nm != "null" }
                                }
                            } ?: emptyList()
                        )
                    }
                }
            }
        }
}
