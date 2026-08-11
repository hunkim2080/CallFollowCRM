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

// 서버 §21 GET /api/tone/profile 응답 (내 말투 학습 화면용).
data class ToneTrait(val k: String, val v: String)
data class ToneExample(val question: String, val plain: String, val mine: String)
data class ToneProfile(
    val analyzed: Boolean,
    val sampleCount: Int,
    val learnRatePct: Int,
    val traits: List<ToneTrait>,
    val example: ToneExample?,
    val editCount: Int
)

class PhaseOneApiRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = Net.builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        // 전체 호출 상한 — slowClient(readTimeout 45s)가 이 base 를 newBuilder 로 상속하므로 그보다 넉넉히. (2026-08-12 오프라인 감사)
        .callTimeout(80, TimeUnit.SECONDS)
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

    /**
     * 서버 §21 GET /api/tone/profile?device_id= — 학습률·말투 특징·before/after.
     *   LLM 3종 병렬 호출(캐시 미스 시)이라 read timeout 을 넉넉히(45s) 둠.
     *   실패/미분석이면 analyzed=false 로 와도 sampleCount/learnRatePct 는 채워져 옴 (hero 표시용).
     */
    suspend fun fetchToneProfile(deviceId: String = "owner-anon"): Result<ToneProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/api/tone/profile?device_id=$deviceId")
                .get()
                .build()
            val slowClient = client.newBuilder().readTimeout(45, TimeUnit.SECONDS).build()
            slowClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string() ?: "{}")
                val traits = mutableListOf<ToneTrait>()
                val tarr = json.optJSONArray("traits") ?: JSONArray()
                for (i in 0 until tarr.length()) {
                    val o = tarr.optJSONObject(i) ?: continue
                    val v = o.optString("v")
                    if (v.isNotBlank()) traits += ToneTrait(o.optString("k"), v)
                }
                val exObj = json.optJSONObject("example")
                val example = exObj?.let {
                    ToneExample(it.optString("question"), it.optString("plain"), it.optString("mine"))
                        .takeIf { e -> e.plain.isNotBlank() || e.mine.isNotBlank() }
                }
                ToneProfile(
                    analyzed = json.optBoolean("analyzed", false),
                    sampleCount = json.optInt("sampleCount", 0),
                    learnRatePct = json.optInt("learnRatePct", 0),
                    traits = traits,
                    example = example,
                    editCount = json.optInt("editCount", 0)
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
