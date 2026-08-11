package com.detailline.callfollowcrm.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 현장 사진 서버 조회 (§25, 2026-06-05) — 고객별로 팀원+사장님이 올린 사진을 가져와 고객 카드에 표시.
 *   GET /api/site-photos?owner_phone=&customer_phone= → photos[]{image_data_url(base64), uploader_kind, uploader_name, uploaded_at_ms}
 *   base64 → Bitmap 디코드까지 여기서(IO). 썸네일(서버 1MB 컷)이라 디코드 부담 적음.
 */
class SitePhotoServerRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class RemotePhoto(
        val photoId: Long,
        val bitmap: Bitmap?,
        val uploaderName: String,   // "사장님" | 팀원 이름
        val isOwner: Boolean,
        val uploadedAtMs: Long
    )

    /** 팀원이 그 고객(현장)에 남긴 현장 메모 한 건. readAtMs/replyText = 양방향 확인·답글. */
    data class RemoteNote(
        val eventId: Long,
        val text: String,
        val memberName: String,
        val createdAtMs: Long,
        val readAtMs: Long?,
        val replyText: String?,
        val replyAtMs: Long?
    )

    /** 고객별 팀원 현장 메모 — GET /api/team/notes. 조회 = 확인(서버가 read_at 박음). */
    suspend fun fetchNotes(ownerPhone: String, customerPhone: String): Result<List<RemoteNote>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val op = java.net.URLEncoder.encode(ownerPhone, "UTF-8")
                val cp = java.net.URLEncoder.encode(customerPhone, "UTF-8")
                val req = Request.Builder()
                    .url("$baseUrl/api/team/notes?owner_phone=$op&customer_phone=$cp&limit=100")
                    .get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("notes")
                        ?: org.json.JSONArray()
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        RemoteNote(
                            eventId = o.optLong("event_id"),
                            text = o.optString("text"),
                            memberName = o.optString("member_name").ifBlank { "팀원" },
                            createdAtMs = o.optLong("created_at_ms"),
                            readAtMs = o.optLong("read_at_ms").takeIf { it > 0 },
                            replyText = o.optString("reply_text").takeIf { it.isNotBlank() && it != "null" },
                            replyAtMs = o.optLong("reply_at_ms").takeIf { it > 0 }
                        )
                    }
                }
            }
        }

    /** 사장님이 팀원 현장 메모에 답글 — POST /api/team/note/reply. */
    suspend fun replyNote(ownerPhone: String, eventId: Long, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("owner_phone", ownerPhone)
                    put("event_id", eventId)
                    put("text", text)
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder().url("$baseUrl/api/team/note/reply").post(body).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    Unit
                }
            }
        }

    /** 사장님이 그 현장 사진 삭제 (퇴사한 팀원 사진 포함). owner_phone 일치로 서버 인증. */
    suspend fun deletePhotoAsOwner(ownerPhone: String, photoId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val op = java.net.URLEncoder.encode(ownerPhone, "UTF-8")
                val req = Request.Builder()
                    .url("$baseUrl/api/team/photo/$photoId?owner_phone=$op")
                    .delete().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    Unit
                }
            }
        }

    suspend fun fetch(ownerPhone: String, customerPhone: String): Result<List<RemotePhoto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val op = java.net.URLEncoder.encode(ownerPhone, "UTF-8")
                val cp = java.net.URLEncoder.encode(customerPhone, "UTF-8")
                val req = Request.Builder()
                    .url("$baseUrl/api/site-photos?owner_phone=$op&customer_phone=$cp&limit=50")
                    .get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("photos")
                        ?: org.json.JSONArray()
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        RemotePhoto(
                            photoId = o.optLong("photo_id"),
                            bitmap = decodeDataUrl(o.optString("image_data_url")),
                            uploaderName = o.optString("uploader_name").ifBlank { "팀원" },
                            isOwner = o.optString("uploader_kind") == "owner",
                            uploadedAtMs = o.optLong("uploaded_at_ms")
                        )
                    }
                }
            }
        }

    /** "data:image/jpeg;base64,XXXX" → 축소 Bitmap. 원본 해상도 통째 디코딩 시 OOM(오프라인 감사 rank5) → 다운샘플. */
    private fun decodeDataUrl(dataUrl: String?): Bitmap? =
        com.detailline.callfollowcrm.util.ImageDownsample.decodeDataUrl(dataUrl)
}
