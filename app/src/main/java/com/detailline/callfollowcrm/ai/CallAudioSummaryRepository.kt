package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 통화 녹음(m4a) → 맥미니 §26 `/api/call-audio-summary` (2026-06-08).
 *
 * 무료 녹음(에이닷 "녹음 파일 공유") 을 서버로 올리면, 맥미니가 로컬 Whisper 로 받아쓰기(무료)
 * → 기존 Haiku 요약 → {one_line, bullets[], suggested_followup_sms, transcript} 동기 응답.
 *
 * 비용: API 0(STT 로컬), Haiku 요약만. 캐시: (phone, "call-audio-summary", started_at_ms).
 * STT 가 통화 1분당 ~10초라 read timeout 을 길게(120s) 잡는다.
 */
class CallAudioSummaryRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL,
    /** 사장님(owner) 본인 phone(digits) — 서버 베타 화이트리스트 가드용. 비면 안 보냄. (2026-06-20 cowork 계약) */
    private val ownerPhone: () -> String = { "" }
) {
    private val client = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)    // 오디오 업로드(수 MB)
        .readTimeout(120, TimeUnit.SECONDS)    // STT + LLM (긴 통화 대비)
        .callTimeout(210, TimeUnit.SECONDS)    // 전체 상한(업로드60+STT/LLM120 여유) — 긴 통화 요약은 안 자르되 무한 hang만 차단 (2026-08-12 오프라인 감사)
        .build()

    data class Result(
        val oneLine: String?,
        val bullets: List<String>,
        val followupSms: String?,
        val transcript: String?,
        /** 한눈에 보는 짧은 제목(서버 LLM). 없으면 앱이 oneLine 으로 폴백. (2026-06-28 사장님) */
        val title: String? = null,
        /** 서버가 DB 캐시에서 즉시 응답했는지(=이미 처리된 통화). force_refresh 로 재처리 가능. */
        val cached: Boolean = false
    )

    suspend fun summarize(
        audioBytes: ByteArray,
        fileName: String,
        phone: String,
        startedAtMs: Long,
        direction: String = "incoming",
        durationSec: Int = 0,
        customerName: String? = null,
        customerMemo: String? = null,
        ownerToneSamples: List<String> = emptyList(),
        /** true 면 서버 캐시 무시 + 새 STT/LLM 재처리 (사장님이 "다시 요약" 선택 시). */
        forceRefresh: Boolean = false
    ): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            val audioMedia = "application/octet-stream".toMediaType()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, audioBytes.toRequestBody(audioMedia))
                .addFormDataPart("phone", phone)
                .addFormDataPart("started_at_ms", startedAtMs.toString())
                .addFormDataPart("direction", direction)
                .addFormDataPart("duration_sec", durationSec.toString())
                .apply {
                    ownerPhone().filter { it.isDigit() }.takeIf { it.length >= 9 }?.let { addFormDataPart("owner_phone", it) }
                    if (forceRefresh) addFormDataPart("force_refresh", "true")
                    customerName?.takeIf { it.isNotBlank() }?.let { addFormDataPart("customer_name", it) }
                    customerMemo?.takeIf { it.isNotBlank() }?.let { addFormDataPart("customer_memo", it) }
                    if (ownerToneSamples.isNotEmpty()) {
                        addFormDataPart("owner_tone_samples", JSONArray(ownerToneSamples.take(10)).toString())
                    }
                }
                .build()
            val req = Request.Builder()
                .url("$baseUrl/api/call-audio-summary")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) throw IOException("empty body")
                val obj = JSONObject(raw)
                val bullets = obj.optJSONArray("bullets")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                } ?: emptyList()
                Result(
                    oneLine = obj.optString("one_line").takeIf { it.isNotBlank() },
                    bullets = bullets,
                    followupSms = obj.optString("suggested_followup_sms").takeIf { it.isNotBlank() && it != "null" },
                    transcript = obj.optString("transcript").takeIf { it.isNotBlank() },
                    title = obj.optString("title").takeIf { it.isNotBlank() && it != "null" },
                    cached = obj.optBoolean("cached", false)
                )
            }
        }
    }
}
