package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
 * 맥미니 FastAPI 서버의 `POST /api/refine` 호출 — Claude Sonnet 4.6 기반 ✨ 다듬기.
 *
 * 2026-05-27 사장님 결정으로 [OllamaRefineRepository] 에서 교체.
 *   - Ollama (gpt-oss:20b) 는 별개 프로세스라 launchctl 재기동 시 영향 + 답변 품질 부족.
 *   - 서버의 사장님 톤 코퍼스 + prompt caching 활용 → 품질 ↑ + 비용 ↓.
 *
 * 서버 endpoint 사양 (cowork 가 박을 것):
 *   POST /api/refine
 *   Request:  { "raw": "...", "owner_tone_samples": ["...", ...] }   // samples 는 optional
 *   Response: { "polished": "..." }
 *   실패: 5xx 또는 빈 본문 → Result.failure (ChatViewModel 이 "AI 서버 연결 실패" 토스트)
 *
 * 서버 endpoint 미구현 시 (404) — graceful fallback 으로 Result.failure. 클라이언트가
 * 사장님께 토스트 안내. 사장님이 cowork 에게 endpoint 추가 요청.
 */
class ClaudeRefineRepository(
    private val baseUrl: String = "http://100.86.114.49:8000"
) : RefineRepository {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun refine(input: String, system: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("raw", input)
                    // owner_tone_samples 는 서버가 사장님 톤 prompt 에 inject — 클라이언트는 빈 배열로 두고
                    // 서버가 DB 의 보낸 SMS 코퍼스를 활용하도록 유도. (서버에 코퍼스 없으면 system prompt
                    // 만으로 다듬기 — 그래도 Ollama 보단 품질 좋음.)
                    put("owner_tone_samples", JSONArray())
                    if (system != null) put("system", system)
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

    override fun refineStream(input: String, system: String?): Flow<String> = flow {
        refine(input, system).onSuccess { emit(it) }
    }.flowOn(Dispatchers.IO)

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
