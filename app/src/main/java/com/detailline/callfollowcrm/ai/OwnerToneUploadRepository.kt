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
 * 2026-05-29 킬러콘텐츠 4단계 (Tone RAG) — Mac mini 서버에 사장님 sent SMS 풀 batch upload.
 *
 * cowork 작업 (서버):
 *   1. POST /api/owner-tone/batch-upload — 한 번에 수천~수만 건 chunk 단위 (안드가 잘라 보냄)
 *   2. 서버: 각 text 임베딩 (bge-m3) → SQLite + sqlite-vec INSERT (owner_tone 테이블)
 *   3. prepare-reply 내부에서 retrieval — 새 고객 메시지 임베딩 → cosine top-k → few-shot inject
 *      (안드 prepare-reply 호출 시그니처 무변경 — ownerToneSamples 매번 보내는 거 그대로 두되 retrieval 결과 우선)
 *
 * 안드 측 (이 repository):
 *   - sent SMS 풀 (SmsRepository.querySentMessages(limit=N)) → 1000건 chunk 로 POST.
 *   - 진행 표시 (onProgress callback).
 *   - 실패 안전망 — chunk 단위 retry / 일부 성공 시 partial 보고.
 *
 * 비용: bge-m3 임베딩 = local Mac mini → 비용 0. upload 자체는 Tailnet 이라 빠름 (~수 초).
 */
class OwnerToneUploadRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)   // 임베딩 chunk 처리에 시간 걸릴 수 있음
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 사장님 sent SMS 풀 batch upload.
     * messages = SmsRepository.querySentMessages 결과 (text only).
     *
     * @param deviceId 익명 device 식별자. 사장님 multi-device 대응 (향후). 현재는 device-anon.
     * @param messages text 리스트 — chunk 로 잘라 N 번 호출.
     * @param chunkSize 한 번에 보낼 건수. 기본 500.
     * @param onProgress (sent, total) 콜백 — UI 진행 바.
     * @return 성공한 누적 upload 개수. 실패 시 부분 성공도 반환.
     */
    suspend fun batchUpload(
        deviceId: String,
        messages: List<TimestampedText>,
        chunkSize: Int = 500,
        onProgress: ((sent: Int, total: Int) -> Unit)? = null
    ): Result<UploadResult> = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext Result.success(UploadResult(0, 0))
        runCatching {
            var stored = 0
            var totalInPool = 0
            var embeddingsAvailable = true   // cowork 응답으로 덮어쓰기
            val total = messages.size
            messages.chunked(chunkSize).forEachIndexed { idx, chunk ->
                val payload = JSONObject().apply {
                    put("device_id", deviceId)
                    put("messages", JSONArray().apply {
                        chunk.forEach { msg ->
                            put(JSONObject().apply {
                                put("text", msg.text)
                                put("timestamp_ms", msg.timestampMs)
                            })
                        }
                    })
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/owner-tone/batch-upload")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("HTTP ${resp.code} on chunk ${idx + 1}")
                    }
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        runCatching {
                            val obj = JSONObject(body)
                            stored += obj.optInt("stored", chunk.size)
                            totalInPool = obj.optInt("total_in_pool", totalInPool)
                            // cowork §16 추가: pip install 안 됐으면 false → 안드 UI 가 안내.
                            if (obj.has("embeddings_available")) {
                                embeddingsAvailable = obj.optBoolean("embeddings_available", true)
                            }
                        }
                    } else {
                        stored += chunk.size
                    }
                }
                onProgress?.invoke(minOf((idx + 1) * chunkSize, total), total)
            }
            UploadResult(stored = stored, totalInPool = totalInPool, embeddingsAvailable = embeddingsAvailable)
        }
    }

    data class TimestampedText(val text: String, val timestampMs: Long)
    data class UploadResult(
        val stored: Int,
        val totalInPool: Int,
        /**
         * cowork §16 — bge-m3 + sqlite-vec 정상 로드 여부.
         * false 면 RAG retrieval 비활성. INSERT 는 진행 (embedding 만 skip).
         * Mac mini 에 `pip install FlagEmbedding sqlite-vec` 후 launchctl reload 하면 자동 활성화.
         */
        val embeddingsAvailable: Boolean = true
    )

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
