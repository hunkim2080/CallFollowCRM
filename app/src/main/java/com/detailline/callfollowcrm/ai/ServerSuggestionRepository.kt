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
 * 맥미니 자체 서버(Tailnet :8000) 기반 SuggestionRepository.
 *
 * 서버 사양: RINGGO_SERVER_SPEC.md (POST /prepare-reply, GET /suggestions/{phone}).
 * Ollama 11434 와 별개 포트. 같은 Tailnet IP.
 *
 * prepare 는 fire-and-forget — 서버가 즉시 200 응답하고 백그라운드에서 LLM 호출.
 * fetch 는 캐시 조회. 신선도 판정은 호출자 책임.
 */
class ServerSuggestionRepository(
    private val baseUrl: String = "http://100.86.114.49:8000"
) : SuggestionRepository {

    // prepare 는 폰이 빠르게 끊겨야 하므로 짧은 타임아웃, fetch 도 단순 GET 이라 짧게.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    override suspend fun requestPrepare(context: PrepareContext): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("phone", context.phone)
                    put("latestMessage", context.latestMessage)
                    put("latestMessageReceivedAtMs", context.latestMessageReceivedAtMs)
                    put("recentHistory", JSONArray().apply {
                        context.recentHistory.forEach { msg ->
                            put(JSONObject().apply {
                                put("role", msg.role)
                                put("body", msg.body)
                                put("timestampMs", msg.timestampMs)
                            })
                        }
                    })
                    context.customer?.let { c ->
                        put("customer", JSONObject().apply {
                            put("name", c.name ?: JSONObject.NULL)
                            put("memo", c.memo ?: JSONObject.NULL)
                            put("leadHeat", c.leadHeat ?: JSONObject.NULL)
                            put("depositPaid", c.depositPaid)
                        })
                    }
                    if (context.ownerToneSamples.isNotEmpty()) {
                        put("ownerToneSamples", JSONArray().apply {
                            context.ownerToneSamples.forEach { put(it) }
                        })
                    }
                }
                val req = Request.Builder()
                    .url("$baseUrl/prepare-reply")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                }
            }
        }

    override suspend fun fetch(phone: String): Result<SuggestionFetchResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/suggestions/$phone")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val bodyStr = resp.body?.string() ?: throw IOException("빈 응답")
                    parseFetchResult(bodyStr)
                }
            }
        }

    private fun parseFetchResult(bodyStr: String): SuggestionFetchResult {
        val json = JSONObject(bodyStr)
        val status = when (json.optString("status")) {
            "ready" -> SuggestionStatus.READY
            "generating" -> SuggestionStatus.GENERATING
            else -> SuggestionStatus.MISSING
        }
        if (status != SuggestionStatus.READY) {
            return SuggestionFetchResult(status, null)
        }
        val arr = json.optJSONArray("suggestions") ?: JSONArray()
        val list = (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).takeIf { it.isNotBlank() }
        }
        val suggestions = ReplySuggestions(
            phone = json.optString("phone"),
            basedOnMessage = json.optString("basedOnMessage"),
            basedOnReceivedAtMs = json.optLong("basedOnReceivedAtMs"),
            generatedAtMs = json.optLong("generatedAtMs"),
            suggestions = list
        )
        return SuggestionFetchResult(SuggestionStatus.READY, suggestions)
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
