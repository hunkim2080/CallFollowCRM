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

    private val client: OkHttpClient = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(55, TimeUnit.SECONDS)   // 전체 호출 상한 — 재시도/route 누적 hang 방지 (2026-08-12 오프라인 감사)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun refine(input: String, context: RefineContext): Result<String> =
        withContext(Dispatchers.IO) {
            var lastError: Throwable = IOException("refine 실패")
            // 일시적 먹통(502/503/504 · 타임아웃 · 연결 끊김)엔 한 번 더 시도. (2026-10-14 사장님)
            //   클라우드플레어 터널이 1~2초 깜빡이면 사장님이 '다듬기 실패'를 보던 것 방지.
            //   ❌ 재시도 안 함: 한도 초과(RefineQuotaException)·4xx·빈 응답 — 같은 결과거나 LLM 비용만 중복된다.
            for (attempt in 0..1) {
                val result = runCatching { requestRefine(input, context) }
                result.getOrNull()?.let { return@withContext Result.success(it) }
                lastError = result.exceptionOrNull() ?: lastError
                if (attempt == 0 && isTransient(lastError)) {
                    kotlinx.coroutines.delay(RETRY_DELAY_MS)
                } else {
                    return@withContext Result.failure(lastError)
                }
            }
            Result.failure(lastError)
        }

    /** 단일 호출 — 성공 시 polished 문자열, 실패 시 throw. (재시도 판단은 [refine] 이 한다) */
    private fun requestRefine(input: String, context: RefineContext): String {
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
            // 📞 가장 최근 통화 요약 — 서버가 다듬기 프롬프트에 녹인다. 빈값이면 안 보냄. (2026-10-06 사장님)
            context.callSummary?.takeIf { it.isNotBlank() }?.let { put("call_summary", it) }
        }
        val req = Request.Builder()
            .url("$baseUrl/api/refine")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        return client.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                // 429 = 구글 무료 한도. 고장이 아니므로 화면에 이유를 그대로 말해준다.
                //   서버가 detail.code 로 quota_day(오늘 몫 끝) / quota_minute(순간 몰림) 을 준다.
                if (resp.code == 429) throw RefineQuotaException(bodyStr.contains("quota_day"))
                throw IOException("HTTP ${resp.code}: ${resp.message}")
            }
            if (bodyStr.isEmpty()) throw IOException("빈 응답")
            JSONObject(bodyStr).optString("polished").trim().ifEmpty {
                throw IOException("polished 필드 비어있음")
            }
        }
    }

    /** 다시 시도해볼 만한 '일시적' 실패인가 — 게이트웨이 깜빡임·네트워크 순간 끊김만 true. */
    private fun isTransient(e: Throwable): Boolean {
        if (e is RefineQuotaException) return false   // 한도는 재시도해도 같다
        if (e is java.net.SocketTimeoutException || e is java.net.ConnectException ||
            e is java.net.UnknownHostException || e is java.io.InterruptedIOException
        ) return true
        val msg = e.message ?: return false
        return msg.contains("HTTP 502") || msg.contains("HTTP 503") || msg.contains("HTTP 504") ||
            msg.contains("timeout", ignoreCase = true) || msg.contains("reset")
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        /** 일시적 실패 후 재시도까지 쉬는 시간 — 터널 깜빡임이 가라앉을 짧은 틈. */
        private const val RETRY_DELAY_MS = 600L
    }
}
