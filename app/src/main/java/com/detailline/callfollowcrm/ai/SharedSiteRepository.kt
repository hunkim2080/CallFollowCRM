package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 협업 현장 (사장 ↔ 사장 공유) — 맥미니 서버 협업 API.  2026-06-08.
 * 기획: docs/SPEC_shared_sites_owner_to_owner.md · 핸드오프: docs/SERVER_HANDOFF_collab_sites.md
 *
 * 팀 API(/api/team/…) 와 같은 스타일. 차이: "팀원(웹뷰)" 이 아니라 **RING-GO 앱 사장(bizPhone)** 끼리
 * 한 현장만 공유. 서버가 상대 번호의 가입 여부로 인앱/링크 분기.
 *
 *  - GET  /api/shared/with-me?phone=B          내가(협업자) 공유받은 현장 목록
 *  - POST /api/shared/invite                   현장 주인(A)이 상대 사장(B)에게 현장 공유 요청
 *  - POST /api/shared/respond                  B가 수락/거절
 *  - POST /api/shared/progress                 B가 출발/도착/완료 (완료 시 계좌 payload)
 *  - POST /api/shared/paid                     A가 입금완료 표시 → B 알림
 *  - GET  /api/owner/exists?phone=             상대가 가입 사장인지 (인앱/링크 분기용)
 *
 * 모든 호출은 실패해도 안전(Result). 서버 미구현 시 with-me 는 빈 목록 → 화면은 "공유받은 현장 없음".
 */
class SharedSiteRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** 진행 단계 — 팀 이벤트(departed/arrived/completed)와 동일 어휘. */
    enum class Progress { ASSIGNED, DEPARTED, ARRIVED, COMPLETED;
        companion object {
            fun from(s: String?): Progress = when (s?.lowercase()) {
                "departed" -> DEPARTED; "arrived" -> ARRIVED; "completed" -> COMPLETED; else -> ASSIGNED
            }
        }
    }

    /** B(협업자)가 공유받은 현장 1건. 고객 전화번호는 절대 포함 안 됨(벽). */
    data class SharedSite(
        val shareId: String,
        val ownerPhone: String,      // 현장 주인 사장 번호 (연락용 — 고객 번호 아님)
        val ownerName: String,
        val title: String,           // 현장 표시명 (예: "강동 천호동 현장")
        val addr: String?,
        val scheduledAtMs: Long,
        val timeLabel: String?,      // "09:00"
        val workSummary: String?,    // 시공 범위
        val dailyWage: Int? = null,  // 그날 일당(만원). A가 공유 시 입력, 없으면 null
        val memo: String?,           // 대표님 전달사항
        val status: String,          // "pending" | "accepted" | "declined"
        val progress: Progress,
        val createdAtMs: Long
    )

    /** 협업 현장 증거 사진 1장 — 서버 §F (POST /api/shared/photo, GET /api/shared/photos). */
    data class SharedPhoto(
        val photoId: Long,
        val bitmap: android.graphics.Bitmap?,
        val label: String?,          // "시공 전" 등
        val note: String?,
        val uploaderKind: String,    // "owner" | "partner"
        val uploaderName: String,
        val uploadedAtMs: Long
    )

    /** 업체별(나를 부른 사장님) 집계 — 서버 §B. 전체 이력 기준(with-me 윈도우 밖 과거 포함). */
    data class Partner(
        val ownerPhone: String,
        val ownerName: String,
        val count: Int,        // 함께한 현장 수
        val totalWage: Int,    // 완료 현장 일당 합(만원)
        val paidTotal: Int,    // 입금 완료된 합(만원)
        val lastAtMs: Long     // 최근 현장 시각
    )

    data class InviteResult(
        val shareId: String,
        val route: String,           // "inapp" (상대도 앱 사장) | "link" (웹링크)
        val url: String?,            // route=link 일 때
        val smsDraft: String?,
        val deduped: Boolean = false // 서버가 같은 현장 미완 협업 있어 새로 안 만들고 기존 반환(=새 알람 안 감)
    )

    /** A(현장 주인)가 받아보는 협업 진행 이벤트. */
    data class OwnerEvent(
        val eventId: String,
        val shareId: String,
        val title: String,
        val partnerName: String,
        val step: String,             // "departed" | "arrived" | "completed"
        val atMs: Long,
        val account: JSONObject? = null,
        val dailyWage: Int? = null    // 서버가 shared_sites JOIN 으로 echo (없으면 null)
    )

    /** 내가 공유받은 협업 현장 목록. 서버 없거나 실패 시 빈 목록(graceful). */
    suspend fun withMe(phone: String, sinceMs: Long = 0L, limit: Int = 50): Result<List<SharedSite>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegments("api/shared/with-me")
                    .addQueryParameter("phone", phoneKey(phone))
                    .addQueryParameter("since_ms", sinceMs.toString())
                    .addQueryParameter("limit", limit.toString())
                    .build()
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val body = resp.body?.string().orEmpty()
                    parseSites(body)
                }
            }
        }

    /** 내가(A) 요청해 만든 협업 현장 목록(by-me). 거절/종료된 건도 status 로 옴 → 일정 뱃지 self-heal 용. 실패 시 빈 목록. */
    suspend fun byMe(phone: String, limit: Int = 80): Result<List<SharedSite>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegments("api/shared/by-me")
                    .addQueryParameter("phone", phoneKey(phone))
                    .addQueryParameter("limit", limit.toString())
                    .build()
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    parseSites(resp.body?.string().orEmpty())
                }
            }
        }

    /** A(주인)가 상대 사장 번호로 현장 공유 요청. */
    suspend fun invite(
        ownerPhone: String,
        partnerPhone: String,
        title: String,
        addr: String?,
        scheduledAtMs: Long,
        workSummary: String?,
        memo: String?,
        customerLabel: String?,
        dailyWage: Int? = null,
        timeLabel: String? = null,
        ownerName: String? = null
    ): Result<InviteResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("owner_phone", phoneKey(ownerPhone))
                put("partner_phone", phoneKey(partnerPhone))
                put("title", title)
                addr?.let { put("addr", it) }
                put("scheduled_at_ms", scheduledAtMs)
                workSummary?.let { put("work_summary", it) }
                memo?.let { put("memo", it) }
                customerLabel?.let { put("customer_label", it) }
                dailyWage?.let { put("daily_wage", it) }
                timeLabel?.let { put("time_label", it) }
                ownerName?.takeIf { it.isNotBlank() }?.let { put("owner_name", it) }  // A 사업자명(상호) → B 화면 "OO과 함께"
            }
            val req = Request.Builder().url("$baseUrl/api/shared/invite")
                .post(payload.toString().toRequestBody(jsonMedia)).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val obj = JSONObject(resp.body?.string().orEmpty())
                InviteResult(
                    shareId = obj.optString("share_id"),
                    route = obj.optString("route").ifBlank { "link" },
                    url = obj.optString("url").takeIf { it.isNotBlank() },
                    smsDraft = obj.optString("sms_draft").takeIf { it.isNotBlank() },
                    deduped = obj.optBoolean("deduped", false)
                )
            }
        }
    }

    /** B 수락/거절. */
    suspend fun respond(shareId: String, partnerPhone: String, accept: Boolean): Result<Unit> =
        post("$baseUrl/api/shared/respond", JSONObject().apply {
            put("share_id", shareId); put("partner_phone", phoneKey(partnerPhone)); put("accept", accept)
        })

    /** B 진행(출발/도착/완료). 완료 시 bank/account 를 payload 로 실어 보냄 → A 에게 계좌 전달. */
    suspend fun progress(
        shareId: String,
        partnerPhone: String,
        step: Progress,
        bank: String? = null,
        accountNo: String? = null,
        holder: String? = null,
        auto: Boolean = false
    ): Result<Unit> = post("$baseUrl/api/shared/progress", JSONObject().apply {
        put("share_id", shareId)
        put("partner_phone", phoneKey(partnerPhone))
        put("step", step.name.lowercase())
        if (auto) put("auto", true)   // §E 3km geofence 자동 도착
        if (step == Progress.COMPLETED && !accountNo.isNullOrBlank()) {
            put("payload", JSONObject().apply {
                bank?.let { put("bank", it) }
                put("account_no", accountNo)
                holder?.let { put("holder", it) }
            })
        }
    })

    /** A 입금완료 → B 알림. */
    suspend fun markPaid(shareId: String, ownerPhone: String): Result<Unit> =
        post("$baseUrl/api/shared/paid", JSONObject().apply {
            put("share_id", shareId); put("owner_phone", phoneKey(ownerPhone))
        })

    /** A(현장 주인)용 협업 진행 이벤트. 서버 미구현(404) 시 Result 실패 → 호출부가 조용히 무시. */
    suspend fun ownerEvents(ownerPhone: String, sinceMs: Long = 0L, limit: Int = 50): Result<List<OwnerEvent>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegments("api/shared/owner-events")
                    .addQueryParameter("phone", phoneKey(ownerPhone))
                    .addQueryParameter("since_ms", sinceMs.toString())
                    .addQueryParameter("limit", limit.toString())
                    .build()
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("events") ?: JSONArray()
                    (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        val shareId = o.optString("share_id")
                        val step = o.optString("step").lowercase()
                        val atMs = o.optLong("at_ms")
                        OwnerEvent(
                            eventId = o.optString("event_id").ifBlank { "$shareId:$step:$atMs" },
                            shareId = shareId,
                            title = o.optString("title").ifBlank { "협업 현장" },
                            partnerName = o.optString("partner_name").ifBlank { "협업 사장님" },
                            step = step,
                            atMs = atMs,
                            account = o.optJSONObject("account"),
                            dailyWage = o.optInt("daily_wage", 0).takeIf { it > 0 }
                        )
                    }
                }
            }
        }

    /** 업체별 집계(§B). 서버 미구현(404)/실패 시 Result 실패 → 호출부가 로컬 그룹핑으로 폴백. */
    suspend fun partners(phone: String): Result<List<Partner>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("api/shared/partners")
                .addQueryParameter("phone", phoneKey(phone))
                .build()
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("partners") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    Partner(
                        ownerPhone = o.optString("owner_phone"),
                        ownerName = o.optString("owner_name").ifBlank { "사장님" },
                        count = o.optInt("count"),
                        totalWage = o.optInt("total_wage"),
                        paidTotal = o.optInt("paid_total"),
                        lastAtMs = o.optLong("last_at_ms")
                    )
                }
            }
        }
    }

    /** 증거 사진 업로드(§F) — 업로더(owner/partner) 본인 번호 + base64(raw). */
    suspend fun uploadPhoto(
        shareId: String,
        uploaderPhone: String,
        imageBase64: String,
        label: String? = null,
        note: String? = null
    ): Result<Unit> = post("$baseUrl/api/shared/photo", JSONObject().apply {
        put("share_id", shareId)
        put("partner_phone", phoneKey(uploaderPhone))
        put("image_base64", imageBase64)
        label?.let { put("label", it) }
        note?.let { put("note", it) }
    })

    /** 그 협업 현장의 모든 증거 사진(owner+partner). 서버 미구현/실패 시 빈 목록(graceful). */
    suspend fun photos(shareId: String, phone: String, limit: Int = 50): Result<List<SharedPhoto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegments("api/shared/photos")
                    .addQueryParameter("share_id", shareId)
                    .addQueryParameter("phone", phoneKey(phone))
                    .addQueryParameter("limit", limit.toString())
                    .build()
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("photos") ?: JSONArray()
                    (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        SharedPhoto(
                            photoId = o.optLong("photo_id"),
                            bitmap = decodeDataUrl(o.optString("image_data_url")),
                            label = o.optString("label").takeIf { it.isNotBlank() && it != "null" },
                            note = o.optString("note").takeIf { it.isNotBlank() && it != "null" },
                            uploaderKind = o.optString("uploader_kind"),
                            uploaderName = o.optString("uploader_name").ifBlank { "사장님" },
                            uploadedAtMs = o.optLong("uploaded_at_ms")
                        )
                    }
                }
            }
        }

    /** A 본인이 보낸 pending 협업 요청 취소(§dedup cancel). accepted 이후엔 서버가 409 → 조용히 무시. */
    suspend fun cancel(shareId: String, ownerPhone: String): Result<Unit> =
        post("$baseUrl/api/shared/cancel", JSONObject().apply {
            put("share_id", shareId); put("owner_phone", phoneKey(ownerPhone))
        })

    /**
     * 협업 해제(끝내기) — 수락된 협업도 양쪽 누구든 끝낼 수 있음. 상대에게 알림 + 기록 보존 + 재요청 가능(dedup 풀림).
     *   by="owner"(A 해제) | "partner"(B 그만하기). 서버 미구현이면 Result 실패 → 호출부가 로컬만 정리.
     */
    suspend fun endCollab(shareId: String, phone: String, asOwner: Boolean): Result<Unit> =
        post("$baseUrl/api/shared/end", JSONObject().apply {
            put("share_id", shareId)
            put("phone", phoneKey(phone))
            put("by", if (asOwner) "owner" else "partner")
        })

    /** "data:image/jpeg;base64,XXXX" 또는 raw base64 → Bitmap. 실패 시 null. */
    private fun decodeDataUrl(dataUrl: String?): android.graphics.Bitmap? {
        if (dataUrl.isNullOrBlank()) return null
        return runCatching {
            val b64 = dataUrl.substringAfter(",", dataUrl)
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    /** 상대 번호가 가입 사장인지(인앱 vs 링크 분기). 서버 없으면 false(=링크 경로). */
    suspend fun ownerExists(phone: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("api/owner/exists")
                .addQueryParameter("phone", phoneKey(phone))
                .build()
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                JSONObject(resp.body?.string().orEmpty()).optBoolean("registered", false)
            }
        }
    }

    private suspend fun post(url: String, payload: JSONObject): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url)
                    .post(payload.toString().toRequestBody(jsonMedia)).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                }
                Unit
            }
        }

    private fun parseSites(body: String): List<SharedSite> {
        if (body.isBlank()) return emptyList()
        val arr: JSONArray = JSONObject(body).optJSONArray("sites") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            SharedSite(
                shareId = o.optString("share_id"),
                ownerPhone = o.optString("owner_phone"),
                ownerName = o.optString("owner_name").ifBlank { "사장님" },
                title = o.optString("title").ifBlank { "협업 현장" },
                addr = o.optString("addr").takeIf { it.isNotBlank() },
                scheduledAtMs = o.optLong("scheduled_at_ms"),
                timeLabel = o.optString("time_label").takeIf { it.isNotBlank() },
                workSummary = o.optString("work_summary").takeIf { it.isNotBlank() },
                dailyWage = o.optInt("daily_wage", 0).takeIf { it > 0 },
                memo = o.optString("memo").takeIf { it.isNotBlank() },
                status = o.optString("status").ifBlank { "accepted" },
                progress = Progress.from(o.optString("progress")),
                createdAtMs = o.optLong("created_at_ms")
            )
        }
    }

    private fun phoneKey(phone: String): String = phone.filter { it.isDigit() }
}
