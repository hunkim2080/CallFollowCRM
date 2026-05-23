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
 * 맥미니 Ollama 호출 구현.
 *
 * 서버: Tailnet 100.86.114.49:11434 — 폰과 맥미니 모두 동일 Tailscale 계정 로그인 필수.
 * 모델: gpt-oss:20b (20.9B MoE, MXFP4).
 * 호출: POST /api/chat — non-stream 우선. stream 은 다음 마일스톤에서 NDJSON 파싱.
 *
 * 응답 함정: message.thinking 은 gpt-oss 내부 추론. UI 에 절대 노출하지 말 것 → content 만 추출.
 */
class OllamaRefineRepository(
    private val baseUrl: String = "http://100.86.114.49:11434",
    private val model: String = "gpt-oss:20b"
) : RefineRepository {

    // 첫 호출 시 모델 로드 ~10초 + 추론 3~5초 → 30초 read timeout.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun refine(input: String, system: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("model", model)
                    put("stream", false)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", system ?: DEFAULT_SYSTEM)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", input)
                        })
                    })
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/chat")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${resp.message}")
                    val bodyStr = resp.body?.string() ?: throw IOException("빈 응답")
                    val message = JSONObject(bodyStr).optJSONObject("message")
                        ?: throw IOException("message 누락")
                    // thinking 필드는 의도적으로 무시 (UI 노출 금지).
                    message.optString("content").trim().ifEmpty {
                        throw IOException("content 비어있음")
                    }
                }
            }
        }

    override fun refineStream(input: String, system: String?): Flow<String> = flow {
        refine(input, system).onSuccess { emit(it) }
    }.flowOn(Dispatchers.IO)

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        // 줄눈 시공 사장님이 고객에게 보낼 카톡/문자 다듬기용 프롬프트.
        // 사장님 실제 사용 피드백 기반으로 튜닝해나갈 것.
        private val DEFAULT_SYSTEM = """
            너는 줄눈 시공 사장님이 고객에게 보낼 메시지를 다듬는 도우미다.
            사장님이 쓴 문장을 받으면, 같은 의미를 유지하면서 자연스럽고 정중한 한국어 문장으로 다듬어서 돌려준다.

            규칙:
            - 결과 문장만 답하라. 인사말·설명·따옴표·이모지를 절대 새로 넣지 마라.
            - 원문에 없는 정보(가격, 날짜, 시간, 이름)를 임의로 추가/변경하지 마라.
            - 원문 길이와 비슷하게. 늘리지 마라.
            - 고객에게 보내는 메시지이므로 반말이면 존댓말로 바꿔라.
            - 사장님은 친절하지만 군더더기 없이 핵심부터 전하는 톤이다.

            원문을 그대로 출력하지 말고 반드시 다듬어서 답하라.
        """.trimIndent()
    }
}
