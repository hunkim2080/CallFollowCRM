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
 * 베타 사용 체크/통계 핑 — 앱 진입(MainActivity.onResume)마다 POST /api/beta/check {phone}.
 *   서버가 호출마다 use_count++ / last_seen_ms 갱신 → admin 대시보드 '최근 앱 실행 / 사용 수' 실시간.
 *   캐싱 없음(무조건 호출). 응답 { ok, name?, reason? }. 네트워크 실패는 null(=막지 않음, fail-open).
 *   (2026-06-21 cowork 요청, SYNC 06-20 15:30)
 */
class BetaCheckRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    data class Result(val ok: Boolean, val name: String?, val reason: String?)

    /** phone(digits) 로 체크 + 통계 갱신. 실패/네트워크 오류면 null(통계용이라 진입 막지 않음). */
    suspend fun check(phone: String): Result? = withContext(Dispatchers.IO) {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 9) return@withContext null
        runCatching {
            val body = JSONObject().put("phone", digits).toString().toRequestBody(jsonMedia)
            val req = Request.Builder().url("$baseUrl/api/beta/check").post(body).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val o = JSONObject(resp.body?.string() ?: "{}")
                Result(
                    ok = o.optBoolean("ok", false),
                    name = o.optString("name").takeIf { it.isNotBlank() },
                    reason = o.optString("reason").takeIf { it.isNotBlank() }
                )
            }
        }.getOrNull()
    }
}
