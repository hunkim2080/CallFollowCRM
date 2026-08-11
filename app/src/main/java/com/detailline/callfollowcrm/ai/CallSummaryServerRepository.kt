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
 * 통화 요약 (에이닷 통화요약 인입) — 맥미니 서버 §18 (2026-06-02).
 *
 * 에이닷 통화요약 원문을 보내면 Haiku 4.5 가 한 줄 + 불릿 + (선택)후속 문자 초안으로 압축.
 *   POST /api/call-summary  → {one_line, bullets[], suggested_followup_sms|null, ...}
 *   캐시: (phone, "call-summary", started_at_ms) → 같은 통화 재호출은 LLM 비용 0.
 *
 * 실패해도 앱은 에이닷 원문 파싱 결과를 그대로 쓰므로(graceful), 이 호출은 "품질 향상" 용.
 */
class CallSummaryServerRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL,
    /** 사장님(owner) 본인 phone(digits) — 서버 베타 화이트리스트 가드용. 비면 안 보냄. (2026-06-20 cowork 계약) */
    private val ownerPhone: () -> String = { "" }
) {
    private val client = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)   // Haiku 응답 ~2~4초
        .callTimeout(45, TimeUnit.SECONDS)   // 전체 호출 상한 — 재시도/route 누적 hang 방지 (2026-08-12 오프라인 감사)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    data class Summary(
        val oneLine: String?,
        val bullets: List<String>,
        val followupSms: String?,
        /** 한눈에 보는 짧은 제목(서버 LLM). 없으면 앱이 oneLine 으로 폴백. (2026-06-28 사장님) */
        val title: String? = null
    )

    suspend fun summarize(
        phone: String,
        rawText: String,
        startedAtMs: Long,
        direction: String = "incoming",
        durationSec: Int = 0,
        customerName: String? = null,
        customerMemo: String? = null,
        ownerToneSamples: List<String> = emptyList()
    ): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("phone", phone)
                ownerPhone().filter { it.isDigit() }.takeIf { it.length >= 9 }?.let { put("owner_phone", it) }
                put("raw_text", rawText.take(8000))
                put("direction", direction)
                put("duration_sec", durationSec)
                put("started_at_ms", startedAtMs)
                customerName?.takeIf { it.isNotBlank() }?.let { put("customer_name", it) }
                customerMemo?.takeIf { it.isNotBlank() }?.let { put("customer_memo", it) }
                if (ownerToneSamples.isNotEmpty()) put("owner_tone_samples", JSONArray(ownerToneSamples.take(10)))
            }
            val req = Request.Builder()
                .url("$baseUrl/api/call-summary")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) throw IOException("empty body")
                val obj = JSONObject(body)
                val bullets = obj.optJSONArray("bullets")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                } ?: emptyList()
                Summary(
                    oneLine = obj.optString("one_line").takeIf { it.isNotBlank() },
                    bullets = bullets,
                    followupSms = obj.optString("suggested_followup_sms").takeIf { it.isNotBlank() && it != "null" },
                    title = obj.optString("title").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }
    }
}
