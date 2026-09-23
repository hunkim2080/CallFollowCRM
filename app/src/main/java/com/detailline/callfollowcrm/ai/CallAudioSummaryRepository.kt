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
        /** 화자분리 세그먼트 JSON 배열 문자열 = [{"speaker","text"}]. 서버 exaone 화자추정. null/빈=앱이 평문 폴백. (2026-08-14) */
        val transcriptSegmentsJson: String? = null,
        /** 한눈에 보는 짧은 제목(서버 LLM). 없으면 앱이 oneLine 으로 폴백. (2026-06-28 사장님) */
        val title: String? = null,
        /** 서버가 DB 캐시에서 즉시 응답했는지(=이미 처리된 통화). force_refresh 로 재처리 가능. */
        val cached: Boolean = false,
        /** 통화 키워드 태그(부위·문제·일정 ≤3, # 없이). 통화카드 해시태그용. 서버 tags[]. (2026-08-17) */
        val tags: List<String> = emptyList()
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
            // 긴 통화는 '맡겨두고 물어보기'. 짧은 통화는 지금까지처럼 한 번에.
            val longCall = durationSec >= ASYNC_DURATION_SEC || audioBytes.size >= ASYNC_BYTES
            if (longCall) {
                try {
                    return@runCatching summarizeAsync(body, phone, startedAtMs)
                } catch (_: NotSupportedException) {
                    // 서버가 아직 새 경로를 모른다 → 아래 예전 방식으로
                }
            }
            val req = Request.Builder()
                .url("$baseUrl/api/call-audio-summary")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) throw IOException("empty body")
                parse(JSONObject(raw))
            }
        }
    }

    /** 서버 응답 → Result. 동기·비동기 두 경로가 **같은 파서**를 쓴다(두 벌 금지). */
    private fun parse(obj: JSONObject): Result {
        // 서버가 주는 새 모양 — [{time,speaker,text}]. 있으면 `시작-끝|화자|문장` 줄로 만든다.
        //   없으면(옛 통화·옛 서버) 지금처럼 bullets 를 그대로. (2026-09-24)
        val rows = obj.optJSONArray("bullet_rows")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val text = o.optString("text").trim()
                if (text.isBlank()) return@mapNotNull null
                o.optString("time").trim() + "|" + o.optString("speaker").trim() + "|" + text
            }
        }.orEmpty()
        val legacy = obj.optJSONArray("bullets")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()
        val bullets = rows.ifEmpty { legacy }
        val tags = obj.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).mapNotNull {
                arr.optString(it).trim().removePrefix("#").trim().takeIf { s -> s.isNotBlank() }
            }
        } ?: emptyList()
        return Result(
            oneLine = obj.optString("one_line").takeIf { it.isNotBlank() },
            bullets = bullets,
            followupSms = obj.optString("suggested_followup_sms").takeIf { it.isNotBlank() && it != "null" },
            transcript = obj.optString("transcript").takeIf { it.isNotBlank() },
            transcriptSegmentsJson = obj.optJSONArray("transcript_segments")
                ?.takeIf { it.length() > 0 }?.toString(),
            title = obj.optString("title").takeIf { it.isNotBlank() && it != "null" },
            cached = obj.optBoolean("cached", false),
            tags = tags
        )
    }

    /**
     * 긴 통화 — **접수만 시키고 끊었다가, 다 됐는지 물어보며 기다린다.** (2026-09-17 사장님 지시)
     *
     * 왜: 26분 통화(24MB)는 받아쓰기가 100초를 넘겨 중간 게이트웨이가 먼저 끊는다(504).
     *   앱은 실패로 보고 또 보내고, 서버는 같은 파일을 처음부터 다시 갈았다 — 실측 6번.
     *   그래서 '보내고 붙잡고 기다리기'를 '맡겨두고 물어보기'로 바꾼다.
     *
     * 서버가 이 경로를 모르면(404) 예전 방식으로 되돌아간다 — 서버가 먼저 안 올라가도 안 깨지게.
     */
    private fun summarizeAsync(body: MultipartBody, phone: String, startedAtMs: Long): Result {
        val startReq = Request.Builder().url("$baseUrl/api/call-audio-summary/start").post(body).build()
        client.newCall(startReq).execute().use { resp ->
            if (resp.code == 404) throw NotSupportedException()
            if (!resp.isSuccessful) throw IOException("HTTP ${'$'}{resp.code}")
            val raw = resp.body?.string().orEmpty()
            val obj = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            // 이미 돼 있으면 곧장 결과가 온다.
            if (obj.optString("status") == "ready") return parse(obj)
        }

        val digits = phone.filter { it.isDigit() }
        val url = "$baseUrl/api/call-audio-summary/result?phone=$digits&started_at_ms=$startedAtMs"
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
            val obj = runCatching {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                    if (!r.isSuccessful) null else r.body?.string()?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
                }
            }.getOrNull() ?: continue          // 잠깐 네트워크가 끊겨도 계속 물어본다
            when (obj.optString("status")) {
                "ready" -> return parse(obj)
                "error" -> throw IOException(obj.optString("detail").ifBlank { "서버 처리 실패" })
                // processing / none → 계속 기다린다
            }
        }
        throw IOException("요약이 아직 안 끝났어요 (긴 통화라 시간이 더 걸려요)")
    }

    /** 서버가 아직 새 경로를 모를 때 — 예전 방식으로 되돌리기 위한 신호. */
    private class NotSupportedException : IOException("async not supported")

    companion object {
        /** 이보다 길거나 크면 '맡겨두고 물어보기'. 게이트웨이 100초 제한에 한참 못 미치게 여유를 둔다. */
        private const val ASYNC_DURATION_SEC = 180          // 3분
        private const val ASYNC_BYTES = 3 * 1024 * 1024     // 3MB
        private const val POLL_INTERVAL_MS = 8_000L
        private const val POLL_TIMEOUT_MS = 15 * 60 * 1000L // 최장 15분 (한 시간짜리 통화까진 아직)
    }
}
