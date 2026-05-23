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

data class IntentCandidate(val label: String, val confidence: Double)
data class IntentClassifyResult(val primary: String, val intents: List<IntentCandidate>)
data class ReplyCard(val text: String)
data class ReplySuggestResult(
    val intentLabels: List<String>,
    val replies: List<ReplyCard>,
    val priceCandidates: List<String>,
    val dateCandidates: List<String>
)
data class StyleLearnResult(
    val sampleCount: Int,
    val kindness: Int,
    val avgLength: Int,
    val emojiPerMessage: Double
)

class PhaseOneApiRepository(
    private val baseUrl: String = "http://100.86.114.49:8000"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun warmup(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/health").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            }
        }
    }

    suspend fun classify(message: String): Result<IntentClassifyResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply { put("message", message) }.toString()
            val req = Request.Builder()
                .url("$baseUrl/api/intent/classify")
                .post(body.toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string() ?: "{}")
                val intents = mutableListOf<IntentCandidate>()
                val arr = json.optJSONArray("intents") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    intents += IntentCandidate(
                        label = item.optString("label"),
                        confidence = item.optDouble("confidence", 0.0)
                    )
                }
                IntentClassifyResult(
                    primary = json.optString("primary", "PRICE"),
                    intents = intents
                )
            }
        }
    }

    suspend fun suggest(message: String): Result<ReplySuggestResult> = withContext(Dispatchers.IO) {
        runCatching {
            val classify = classify(message).getOrDefault(IntentClassifyResult("PRICE", emptyList()))
            val reqBody = JSONObject().apply {
                put("customerMessage", message)
                put("intent", classify.primary)
            }.toString()
            val req = Request.Builder()
                .url("$baseUrl/api/reply/suggest")
                .post(reqBody.toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string() ?: "{}")
                val suggestions = json.optJSONArray("suggestions") ?: JSONArray()
                val replies = mutableListOf<ReplyCard>()
                for (i in 0 until suggestions.length()) {
                    val item = suggestions.optJSONObject(i)
                    if (item != null) {
                        val text = item.optString("text")
                        if (text.isNotBlank()) replies += ReplyCard(text)
                    }
                }
                val inline = json.optJSONObject("inlineFields")
                val price = inline?.optJSONObject("price")?.optJSONArray("candidates") ?: JSONArray()
                val date = inline?.optJSONObject("date")?.optJSONArray("candidates") ?: JSONArray()
                ReplySuggestResult(
                    intentLabels = classify.intents.take(2).map { it.label },
                    replies = replies,
                    priceCandidates = jsonArrayToList(price),
                    dateCandidates = jsonArrayToList(date)
                )
            }
        }
    }

    suspend fun learnStyle(samples: List<String>): Result<StyleLearnResult> = withContext(Dispatchers.IO) {
        runCatching {
            val reqBody = JSONObject().apply {
                put("profileId", "owner-default")
                put("samples", JSONArray().apply {
                    val now = System.currentTimeMillis()
                    samples.forEach { body ->
                        put(
                            JSONObject().apply {
                                put("sentAtMs", now)
                                put("to", "00000000000")
                                put("body", body)
                            }
                        )
                    }
                })
            }.toString()
            val req = Request.Builder()
                .url("$baseUrl/api/style-profile/learn")
                .post(reqBody.toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string() ?: "{}")
                val stats = json.optJSONObject("stats")
                StyleLearnResult(
                    sampleCount = json.optInt("sampleCount", samples.size),
                    kindness = stats?.optInt("kindness", 0) ?: 0,
                    avgLength = stats?.optInt("avgLength", 0) ?: 0,
                    emojiPerMessage = stats?.optDouble("emojiPerMessage", 0.0) ?: 0.0
                )
            }
        }
    }

    private fun jsonArrayToList(arr: JSONArray): List<String> {
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val v = arr.optString(i)
            if (v.isNotBlank()) out += v
        }
        return out
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
