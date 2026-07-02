package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 2026-07-02 템플릿 제목 자동 작명(사장님) — 문자 본문을 주면 Haiku 가 짧은 제목을 지어 반환.
 *   "템플릿으로 저장" 시 휴리스틱 제목으로 즉시 저장 → 이 호출로 더 나은 제목으로 갱신(실패하면 휴리스틱 유지).
 *   서버 계약: docs/SERVER_HANDOFF_name_template.md (POST /name-template, Haiku 4.5, 매우 저렴).
 *   유료 LLM 은 맥미니 서버에서만(에이닷 STT 와 구분). 실패/오프라인이면 null → 앱은 휴리스틱 제목 그대로.
 */
class TemplateNameRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 실패/빈응답이면 null. 호출부는 null 이면 자체 휴리스틱 제목을 유지한다. */
    suspend fun suggestTitle(body: String): String? = withContext(Dispatchers.IO) {
        if (body.isBlank()) return@withContext null
        runCatching {
            val payload = JSONObject().apply { put("body", body) }
            val req = Request.Builder()
                .url("$baseUrl/name-template")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val b = resp.body?.string().orEmpty()
                if (b.isBlank()) return@use null
                JSONObject(b).optString("title").trim()
                    .replace("\n", " ")
                    .take(20)
                    .takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
