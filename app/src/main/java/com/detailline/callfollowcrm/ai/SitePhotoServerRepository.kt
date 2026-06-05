package com.detailline.callfollowcrm.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private val client = OkHttpClient.Builder()
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

    /** "data:image/jpeg;base64,XXXX" → Bitmap. 실패 시 null. */
    private fun decodeDataUrl(dataUrl: String?): Bitmap? {
        if (dataUrl.isNullOrBlank()) return null
        return runCatching {
            val b64 = dataUrl.substringAfter(",", dataUrl)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}
