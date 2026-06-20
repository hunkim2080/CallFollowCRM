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
 * 맥미니 FastAPI 서버의 `POST /api/refine` 호출 — ✨ 다듬기.
 *
 * 2026-05-28 사장님 결정:
 *   - LLM = Gemini 2.5 Flash (서버 측에서 선택, 키는 Mac mini 만)
 *   - 입력에 컨텍스트 (recent_messages + owner_tone_samples + customer hint) 포함
 *     → "사장님 톤 + 대화 흐름 맞춤" 다듬기 = 진짜 품질 향상
 *
 * Endpoint 사양 (cowork 가 박을 것):
 *   POST /api/refine
 *   Request:
 *   {
 *     "raw": "사장님이 친 문장",
 *     "recent_messages": [ { "role": "owner|customer", "body": "...", "timestamp_ms": 0 } ],
 *     "owner_tone_samples": ["...", ...],
 *     "customer_name": "김철수",
 *     "customer_memo": "..."
 *   }
 *   Response 200: { "polished": "..." }
 *   Response 5xx / 빈 응답 → Result.failure → "AI 서버 연결 실패" 토스트
 *
 * 서버 endpoint 미구현 시 graceful fallback (Result.failure).
 */
class RemoteRefineRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL,
    /** 사장님(owner) 본인 phone(digits) — 서버 베타 화이트리스트 가드용. 비면 안 보냄. (2026-06-20 cowork 계약) */
    private val ownerPhone: () -> String = { "" }
) : RefineRepository {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun refine(input: String, context: RefineContext): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("raw", input)
                    ownerPhone().filter { it.isDigit() }.takeIf { it.length >= 9 }?.let { put("owner_phone", it) }
                    // 컨텍스트 — 비어있어도 항상 키는 보냄 (서버 측에서 일관 처리).
                    put("recent_messages", JSONArray().apply {
                        context.recentMessages.forEach { hm ->
                            put(JSONObject().apply {
                                put("role", hm.role)
                                put("body", hm.body)
                                put("timestamp_ms", hm.timestampMs)
                            })
                        }
                    })
                    put("owner_tone_samples", JSONArray().apply {
                        context.ownerToneSamples.forEach { put(it) }
                    })
                    if (context.customerName != null) put("customer_name", context.customerName)
                    if (context.customerMemo != null) put("customer_memo", context.customerMemo)
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/refine")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("HTTP ${resp.code}: ${resp.message}")
                    }
                    val bodyStr = resp.body?.string() ?: throw IOException("빈 응답")
                    JSONObject(bodyStr).optString("polished").trim().ifEmpty {
                        throw IOException("polished 필드 비어있음")
                    }
                }
            }
        }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
