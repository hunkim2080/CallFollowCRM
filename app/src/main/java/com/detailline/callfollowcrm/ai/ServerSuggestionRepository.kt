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
 * 맥미니 자체 서버(공개 도메인 api.si0in.kr) 기반 SuggestionRepository.
 *
 * 서버 사양: RINGGO_SERVER_SPEC.md (POST /prepare-reply, GET /suggestions/{phone}).
 * Ollama 11434 는 별개 — Tailnet 전용이라 OllamaRefineRepository 가 직접 IP 사용.
 *
 * prepare 는 fire-and-forget — 서버가 즉시 200 응답하고 백그라운드에서 LLM 호출.
 * fetch 는 캐시 조회. 신선도 판정은 호출자 책임.
 */
class ServerSuggestionRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
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
                    context.deviceId?.takeIf { it.isNotBlank() }?.let { put("deviceId", it) }
                    context.ownerTrade?.takeIf { it.isNotBlank() }?.let { put("ownerTrade", it) }
                    context.priceList?.takeIf { it.isNotBlank() }?.let { put("priceList", it) }
                    context.principles?.takeIf { it.isNotEmpty() }?.let { ps ->
                        put("principles", JSONArray().apply { ps.forEach { put(it) } })
                    }
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
                            // P3 — 이 고객의 시공 예약일 (있으면). 일정 관련 답변 근거.
                            c.scheduledWorkDateMs?.let { put("scheduledWorkDateMs", it) }
                        })
                    }
                    if (context.ownerToneSamples.isNotEmpty()) {
                        put("ownerToneSamples", JSONArray().apply {
                            context.ownerToneSamples.forEach { put(it) }
                        })
                    }
                    // P3 — 다른 시공 일정 (현재 고객 제외, 14일 내). 다른 고객 이름은 leak 금지 → ms 만.
                    //   서버가 요일/오전오후로 가공해서 prompt 에 주입.
                    if (context.otherUpcomingSchedulesMs.isNotEmpty()) {
                        put("otherUpcomingSchedulesMs", JSONArray().apply {
                            context.otherUpcomingSchedulesMs.forEach { put(it) }
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
            "ready", "READY" -> SuggestionStatus.READY
            "generating", "GENERATING" -> SuggestionStatus.GENERATING
            else -> SuggestionStatus.MISSING
        }
        if (status != SuggestionStatus.READY) {
            return SuggestionFetchResult(status, null)
        }
        // 2026-05-28 v2: suggestions 가 두 가지 형태로 들어올 수 있음.
        //   - 옛: ["답변1", "답변2", "답변3"]               (legacy string list)
        //   - 새: [{intent_key, label, text, why}, ...]    (의도 분화)
        // 서버 배포 전/후 어느 쪽이든 깨지지 않게 둘 다 지원.
        val arr = json.optJSONArray("suggestions") ?: JSONArray()
        val choices = (0 until arr.length()).mapNotNull { i ->
            when (val item = arr.opt(i)) {
                is JSONObject -> {
                    val text = item.optString("text").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    ReplyChoice(
                        text = text,
                        label = item.optString("label").takeIf { it.isNotBlank() },
                        intentKey = item.optString("intent_key").takeIf { it.isNotBlank() }
                            ?: item.optString("intentKey").takeIf { it.isNotBlank() },
                        why = item.optString("why").takeIf { it.isNotBlank() }
                    )
                }
                is String -> item.takeIf { it.isNotBlank() }?.let { ReplyChoice(text = it) }
                else -> null
            }
        }
        val suggestions = ReplySuggestions(
            phone = json.optString("phone"),
            basedOnMessage = json.optString("basedOnMessage"),
            basedOnReceivedAtMs = json.optLong("basedOnReceivedAtMs"),
            generatedAtMs = json.optLong("generatedAtMs"),
            suggestions = choices,
            scenario = json.optString("scenario").takeIf { it.isNotBlank() },
            scenarioConfidence = if (json.has("scenario_confidence")) {
                json.optDouble("scenario_confidence").takeIf { !it.isNaN() }
            } else null,
            scenarioReason = json.optString("scenario_reason").takeIf { it.isNotBlank() }
        )
        return SuggestionFetchResult(SuggestionStatus.READY, suggestions)
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
