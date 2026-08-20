package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 앱 데이터 서버 백업 (데이터 안전 2단계, 2026-08-21 사장님).
 *   재설치·기기변경·Play 전환 시 로컬-only 데이터 영구소실 방어.
 *   POST /api/app-backup  {owner_phone, session_token?, blob_b64, fmt} — 텍스트코어 덤프 저장(owner별 최신 1개).
 *   GET  /api/app-backup  ?owner_phone&session_token — 최신 복원.
 *   GET  /api/app-backup/status — 있나/언제.
 *   인증: 토큰 있으면 동봉(WebFeedRepository 패턴). 없으면 owner_phone(베타 허용, 서버 _web_push_auth).
 */
class BackupRepository(
    private val ownerPhone: () -> String = { "" },
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = Net.builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(70, TimeUnit.SECONDS)   // 백업 blob 업/다운로드 여유
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun owner(): String? = ownerPhone().filter { it.isDigit() }.takeIf { it.length >= 9 }
    private fun token(): String? =
        com.detailline.callfollowcrm.data.SessionTokenStore.current?.token?.takeIf { it.isNotBlank() }

    data class Status(val has: Boolean, val sizeBytes: Long, val updatedAtMs: Long)

    /** 백업 blob(base64) 서버 저장. 성공 여부. */
    suspend fun push(blobB64: String): Boolean = withContext(Dispatchers.IO) {
        val op = owner() ?: return@withContext false
        runCatching {
            val body = JSONObject().apply {
                put("owner_phone", op)
                token()?.let { put("session_token", it) }
                put("blob_b64", blobB64)
                put("fmt", 1)
            }.toString().toRequestBody(jsonMedia)
            val req = Request.Builder().url("$baseUrl/api/app-backup").post(body).build()
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** 서버 최신 백업 blob(base64) 가져오기. 없으면 null. */
    suspend fun pull(): String? = withContext(Dispatchers.IO) {
        val op = owner() ?: return@withContext null
        runCatching {
            var url = "$baseUrl/api/app-backup?owner_phone=$op"
            token()?.let { url += "&session_token=" + java.net.URLEncoder.encode(it, "UTF-8") }
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val o = JSONObject(resp.body?.string() ?: "{}")
                if (!o.optBoolean("has", false)) null
                else o.optString("blob_b64").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    /** 서버에 백업 있나/언제/용량. */
    suspend fun status(): Status? = withContext(Dispatchers.IO) {
        val op = owner() ?: return@withContext null
        runCatching {
            var url = "$baseUrl/api/app-backup/status?owner_phone=$op"
            token()?.let { url += "&session_token=" + java.net.URLEncoder.encode(it, "UTF-8") }
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val o = JSONObject(resp.body?.string() ?: "{}")
                Status(o.optBoolean("has", false), o.optLong("size", 0), o.optLong("updated_at_ms", 0))
            }
        }.getOrNull()
    }
}
