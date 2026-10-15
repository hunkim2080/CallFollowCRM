package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.domain.inbox.ServerClassifyGate
import com.detailline.callfollowcrm.domain.inbox.ThreadVerdict
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
 * 상담함/문자함 Phase 2 — 애매한 문자를 서버(Haiku)에 물어 consult/general 을 받는다. (2026-10-15 앱 연결)
 *
 *   서버: `server/main.py` `thread_classify` / `thread_classify_batch` (`ThreadClassifyRequest`, `_classify_one`).
 *   - 1건: POST /api/thread/classify         → {bucket, confidence, reason, cached?}
 *   - 묶음: POST /api/thread/classify-batch  → {ok, count, results:[{…, suffix}]}  (≤20, 결과 순서 = 보낸 순서)
 *   서버가 precision 을 강제한다 — general 은 confidence ≥ 0.9 일 때만, 저장고객/답장이력이면 무조건 consult,
 *   같은 내용은 해시 캐시(재요청 0원). 실패해도 500 대신 consult 로 응답.
 *
 *   「물어볼까」 판단(비용 가드)은 여기 없다 — domain/inbox/ServerClassifyGate 가 하고,
 *   부르는 자리는 data/repository/ThreadServerClassifier **한 곳**이다. 화면·스캔에서 직접 부르지 않는다.
 *
 *   실패 = Result.failure (조용히 삼키지 않는다). 다만 이건 사장님 입력 저장이 아니라 **분류 보정**이라,
 *   부르는 쪽은 실패를 로그만 남기고 **상담함 유지**(안전 기본값)로 끝낸다.
 */
class ThreadClassifyServerRepository(
    private val ownerPhone: () -> String,
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {

    private val client: OkHttpClient = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)    // Haiku 1콜 — 보통 1~2초, 캐시면 즉시
        .callTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 서버에 보내는 스레드 1건 — `ThreadClassifyRequest` 와 같은 모양. */
    data class Input(
        val suffix: String,
        val address: String,
        val isSavedCustomer: Boolean,
        val hasOwnerReply: Boolean,
        /** 시간순, ≤5 (ServerClassifyGate.payloadMessages 가 만든다). */
        val messages: List<ServerClassifyGate.Msg>
    )

    /** 1건 분류. 네트워크/HTTP/파싱 실패는 Result.failure. */
    suspend fun classify(input: Input): Result<ThreadVerdict> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = toJson(input).apply { putDevicePhone(this) }
            val req = Request.Builder()
                .url("$baseUrl/api/thread/classify")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) throw IOException("빈 응답")
                parseOne(JSONObject(body))
            }
        }
    }

    /**
     * 묶음 분류(≤20). 결과는 보낸 순서대로. 서버가 건너뛴 항목(형식 불량)이 있으면 개수가 모자랄 수 있어
     * 부르는 쪽은 suffix 로 맞춘다 — 그래서 (suffix → verdict) 로 돌려준다.
     *   ⚠️ 지금은 부르는 곳이 없다(스캔/백필은 비용 가드로 서버를 안 부른다). 나중에 「재접속 뒤 N건」 같은
     *   제한된 자리에서만 쓸 것.
     */
    suspend fun classifyBatch(inputs: List<Input>): Result<Map<String, ThreadVerdict>> = withContext(Dispatchers.IO) {
        if (inputs.isEmpty()) return@withContext Result.success(emptyMap())
        runCatching {
            val payload = JSONObject().apply {
                putDevicePhone(this)
                put("threads", JSONArray().apply { inputs.take(BATCH_MAX).forEach { put(toJson(it)) } })
            }
            val req = Request.Builder()
                .url("$baseUrl/api/thread/classify-batch")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) throw IOException("빈 응답")
                val arr = JSONObject(body).optJSONArray("results") ?: JSONArray()
                val out = LinkedHashMap<String, ThreadVerdict>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val suffix = o.optString("suffix").trim()
                    if (suffix.isEmpty()) continue
                    out[suffix] = parseOne(o)
                }
                out
            }
        }
    }

    private fun putDevicePhone(o: JSONObject) {
        ownerPhone().filter { it.isDigit() }.takeIf { it.isNotBlank() }?.let { o.put("device_phone", it) }
    }

    private fun toJson(input: Input): JSONObject = JSONObject().apply {
        put("suffix", input.suffix)
        put("address", input.address)
        put("is_saved_customer", input.isSavedCustomer)
        put("has_owner_reply", input.hasOwnerReply)
        put("messages", JSONArray().apply {
            input.messages.forEach { m ->
                put(JSONObject().apply {
                    put("role", if (m.fromOwner) "owner" else "customer")
                    put("body", m.body.take(BODY_MAX))
                    put("ts", m.ts)
                })
            }
        })
    }

    /** 칸이 빠지거나 이상하면 ThreadVerdict.of 가 consult 로 떨어뜨린다. */
    private fun parseOne(o: JSONObject): ThreadVerdict = ThreadVerdict.of(
        bucket = if (o.has("bucket")) o.optString("bucket") else null,
        confidence = if (o.has("confidence")) o.optDouble("confidence") else null,
        reason = if (o.has("reason")) o.optString("reason") else null
    )

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        /** 서버 `_CLASSIFY_BATCH_MAX` 와 같은 수. */
        const val BATCH_MAX = 20
        /** 서버도 300자만 본다(`_classify_one`) — 더 보내봐야 버린다. */
        private const val BODY_MAX = 300
    }
}
