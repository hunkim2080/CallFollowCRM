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
    private val client = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)   // 전체 호출 상한 — 재시도/route 누적 hang 방지 (2026-08-12 오프라인 감사)
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
        val partnerName: String? = null, // 내가 공유한 현장(by-me)에서 "누구랑" — 협업자(B) 상호. 서버 partner_name(미구현이면 null).
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

    /** 협업 현장 한 줄 댓글 1건 — 협업 사장끼리 현장 논의. (2026-07-01, 서버 shared_comments) */
    data class SiteComment(
        val id: Long,
        val authorPhone: String,     // 작성자 사장 번호(숫자만) — 나/상대 구분용
        val authorName: String,
        val body: String,
        val createdAtMs: Long,       // epoch ms
        /** 📮 아직 서버로 못 보낸 것(우체통 대기) — 화면에 「보내는 중」. (2단계) */
        val pending: Boolean = false
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

    /** 협업 월별 양방향 집계 — received=내가 받은(수입), given=내가 준(지출). docs/SERVER_HANDOFF_collab_monthly.md (2026-08-01) */
    data class MonthlyRecord(
        val ym: String,                    // "2026-07"
        val availableMonths: List<String>, // 데이터 있는 달, 최신순
        val received: DirectionAgg,
        val given: DirectionAgg
    )
    data class DirectionAgg(
        val count: Int, val totalWage: Int, val paidTotal: Int,
        val partners: List<PartnerMonth>
    )
    data class PartnerMonth(
        val partnerPhone: String, val partnerName: String,
        val count: Int, val totalWage: Int, val paidTotal: Int, val lastAtMs: Long,
        val sites: List<SiteRow>
    )
    data class SiteRow(
        val shareId: String, val atMs: Long, val title: String, val wage: Int,
        val paid: Boolean?   // null = 로컬 폴백(입금여부 서버만 알아서 모름)
    )

    companion object {
        private val KST: java.time.ZoneId = java.time.ZoneId.of("Asia/Seoul")

        /**
         * 🧬 **협업을 달로 묶는 규칙 — 여기 한 곳.** (2026-09-30 사장님 "두벌로 되는거 있는지체크했니")
         *
         *   전엔 「협업 기록」 화면과 「요청 카드」가 **각자** 셌다 —
         *   기록 화면은 **수락된 것만**, 카드는 **전부**. 같은 물음에 답이 둘이었다.
         *   ("9월 7곳" vs "9월 5곳" 이 한 앱 안에서 나올 수 있었다)
         *
         *   ⚠️ **수락한 것만 센다.** 거절했거나 아직 답 안 한 요청은 「함께한 현장」이 아니다.
         *   ⚠️ 달은 **서울 시간** 기준. 자정 근처 건이 앞뒤 달로 갈리면 안 된다.
         */
        fun countedForHistory(site: SharedSite): Boolean =
            site.status == "accepted" && site.scheduledAtMs > 0L

        /** epoch ms → "yyyy-MM" (서울 시간). */
        fun ymOf(ms: Long): String {
            val d = java.time.Instant.ofEpochMilli(ms).atZone(KST).toLocalDate()
            return "%04d-%02d".format(d.year, d.monthValue)
        }
    }

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

    /** B 수락/거절. partnerName = B 사업자명(상호) → A 화면 "OO 사장님" 표기용(여러 협업자 구분).
     *   reason = 거절 사유(선택) → A(요청자)에게 전달돼 "왜 거절했는지" 알림/표시. (2026-07-08 사장님, 서버=cowork) */
    suspend fun respond(shareId: String, partnerPhone: String, accept: Boolean, partnerName: String? = null, reason: String? = null): Result<Unit> =
        post("$baseUrl/api/shared/respond", JSONObject().apply {
            put("share_id", shareId); put("partner_phone", phoneKey(partnerPhone)); put("accept", accept)
            partnerName?.takeIf { it.isNotBlank() }?.let { put("partner_name", it) }
            if (!accept) reason?.trim()?.takeIf { it.isNotBlank() }?.let { put("reason", it) }
        })

    /** B 진행(출발/도착/완료). 완료 시 bank/account 를 payload 로 실어 보냄 → A 에게 계좌 전달.
     *   partnerName = B 사업자명(상호) → A 화면 "OO 사장님" 표기용(여러 협업자 구분). */
    suspend fun progress(
        shareId: String,
        partnerPhone: String,
        step: Progress,
        bank: String? = null,
        accountNo: String? = null,
        holder: String? = null,
        auto: Boolean = false,
        partnerName: String? = null,
        opKey: String? = null
    ): Result<Unit> = post("$baseUrl/api/shared/progress", JSONObject().apply {
        put("share_id", shareId)
        put("partner_phone", phoneKey(partnerPhone))
        put("step", step.name.lowercase())
        partnerName?.takeIf { it.isNotBlank() }?.let { put("partner_name", it) }
        if (auto) put("auto", true)   // §E 3km geofence 자동 도착
        if (step == Progress.COMPLETED && !accountNo.isNullOrBlank()) {
            put("payload", JSONObject().apply {
                bank?.let { put("bank", it) }
                put("account_no", accountNo)
                holder?.let { put("holder", it) }
            })
        }
    }, opKey)

    // 🧹 markPaid(/api/shared/paid) 제거 — 앱에서 부르는 곳이 0곳이었다(Fable 감사). 서버 길은 남겨둠(무해).

    /**
     * A(현장 주인)가 시공일정을 바꾸면 → 그 현장 협업 사장(B)에게 "일정 변경: 옛날짜 → 새날짜" 알림. (2026-07-16 사장님)
     *   서버: shared_sites.scheduled_at_ms 갱신 + FCM(type=collab_reschedule) 을 B(참여자)에게 push.
     *   old_at_ms 를 같이 보내 서버/B 가 "21일(수) → 23일(금)" 처럼 옛→새를 보여줄 수 있게 한다.
     *   서버 미구현(404) 시 Result 실패 → 호출부가 조용히 무시(로컬 일정은 이미 바뀌어 있음).
     *   ⚠️ 서버 핸드오프: docs/SERVER_HANDOFF_collab_reschedule.md
     */
    suspend fun reschedule(
        shareId: String,
        ownerPhone: String,
        newScheduledAtMs: Long,
        oldScheduledAtMs: Long? = null,
        timeLabel: String? = null
    ): Result<Unit> = post("$baseUrl/api/shared/reschedule", JSONObject().apply {
        put("share_id", shareId)
        put("owner_phone", phoneKey(ownerPhone))
        put("scheduled_at_ms", newScheduledAtMs)
        oldScheduledAtMs?.let { put("old_scheduled_at_ms", it) }
        timeLabel?.takeIf { it.isNotBlank() }?.let { put("time_label", it) }
    })

    /**
     * A(현장 주인)가 현장 주소를 바꾸면 → 그 현장 협업 사장(B)에게 새 주소 전파 + "주소 변경" 알림. (2026-08-02 사장님 버그신고)
     *   서버: shared_sites.addr(+customer_label) 갱신 + FCM(type=collab_address_change) 을 B(참여자)에게 push.
     *   reschedule 와 같은 철학(A 변경 → B 동기화). 서버 미구현(404) 시 Result 실패 → 호출부가 조용히 무시(로컬은 이미 바뀜).
     *   ⚠️ 서버 핸드오프: docs/SERVER_HANDOFF_collab_update_address.md
     */
    suspend fun updateAddress(
        shareId: String,
        ownerPhone: String,
        addr: String,
        customerLabel: String? = null
    ): Result<Unit> = post("$baseUrl/api/shared/update-address", JSONObject().apply {
        put("share_id", shareId)
        put("owner_phone", phoneKey(ownerPhone))
        put("addr", addr)
        customerLabel?.takeIf { it.isNotBlank() }?.let { put("customer_label", it) }
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

    /** 협업 월별 양방향 집계(§collab_monthly). 서버 미구현(404)/실패 시 Result 실패 → 호출부가 withMe+byMe 로컬 폴백. */
    suspend fun monthly(phone: String, ym: String? = null): Result<MonthlyRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val b = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("api/shared/monthly")
                .addQueryParameter("phone", phoneKey(phone))
            ym?.takeIf { it.isNotBlank() }?.let { b.addQueryParameter("ym", it) }
            val req = Request.Builder().url(b.build()).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                parseMonthly(resp.body?.string().orEmpty())
            }
        }
    }

    private fun parseMonthly(body: String): MonthlyRecord {
        val o = JSONObject(body.ifBlank { "{}" })
        val months = o.optJSONArray("available_months")?.let { a ->
            (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()
        return MonthlyRecord(
            ym = o.optString("ym"),
            availableMonths = months,
            received = parseDirection(o.optJSONObject("received")),
            given = parseDirection(o.optJSONObject("given"))
        )
    }

    private fun parseDirection(o: JSONObject?): DirectionAgg {
        if (o == null) return DirectionAgg(0, 0, 0, emptyList())
        val parr = o.optJSONArray("partners") ?: JSONArray()
        val partners = (0 until parr.length()).mapNotNull { i ->
            val p = parr.optJSONObject(i) ?: return@mapNotNull null
            val sarr = p.optJSONArray("sites") ?: JSONArray()
            val sites = (0 until sarr.length()).mapNotNull { j ->
                val s = sarr.optJSONObject(j) ?: return@mapNotNull null
                SiteRow(
                    shareId = s.optString("share_id"),
                    atMs = s.optLong("at_ms"),
                    title = s.optString("title").ifBlank { "협업 현장" },
                    wage = s.optInt("wage"),
                    paid = if (s.has("paid") && !s.isNull("paid")) s.optBoolean("paid") else null
                )
            }
            PartnerMonth(
                partnerPhone = p.optString("partner_phone"),
                partnerName = p.optString("partner_name").ifBlank { "사장님" },
                count = p.optInt("count"),
                totalWage = p.optInt("total_wage"),
                paidTotal = p.optInt("paid_total"),
                lastAtMs = p.optLong("last_at_ms"),
                sites = sites
            )
        }
        return DirectionAgg(
            count = o.optInt("count"),
            totalWage = o.optInt("total_wage"),
            paidTotal = o.optInt("paid_total"),
            partners = partners
        )
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

    /** 증거 사진 삭제(§F) — 올린 본인만(서버가 uploader 검증). 서버 미구현(404)/거부(403) 시 Result 실패 → 화면이 안내.
     *   partner_phone = 삭제 요청자(=업로더) phone. upload 와 같은 필드명. (2026-07-07 사장님) */
    suspend fun deletePhoto(shareId: String, photoId: Long, requesterPhone: String): Result<Unit> =
        post("$baseUrl/api/shared/photo/delete", JSONObject().apply {
            put("share_id", shareId)
            put("photo_id", photoId)
            put("partner_phone", phoneKey(requesterPhone))
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

    /** 그 협업 현장의 한 줄 댓글 목록(오래된→최신). 서버 미구현/실패 시 빈 목록(graceful). */
    suspend fun comments(shareId: String, phone: String, limit: Int = 100): Result<List<SiteComment>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegments("api/shared/comments")
                    .addQueryParameter("site_id", shareId)
                    .addQueryParameter("phone", phoneKey(phone))
                    .addQueryParameter("limit", limit.toString())
                    .build()
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("comments") ?: JSONArray()
                    (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        SiteComment(
                            id = o.optLong("id"),
                            authorPhone = phoneKey(o.optString("author_phone")),
                            authorName = o.optString("author_name").takeIf { it.isNotBlank() && it != "null" } ?: "사장님",
                            body = o.optString("body"),
                            createdAtMs = o.optLong("created_at")
                        )
                    }
                }
            }
        }

    /** 협업 현장에 한 줄 댓글 작성. author_name 없으면 서버가 번호로 표시. 실패 시 Result 실패(화면이 안내). */
    suspend fun postComment(shareId: String, authorPhone: String, authorName: String?, body: String, opKey: String? = null): Result<Unit> =
        post("$baseUrl/api/shared/comment", JSONObject().apply {
            put("site_id", shareId)
            put("author_phone", phoneKey(authorPhone))
            authorName?.takeIf { it.isNotBlank() }?.let { put("author_name", it) }
            put("body", body)
        }, opKey)

    /** "data:image/jpeg;base64,XXXX" 또는 raw base64 → 축소 Bitmap. 원본 해상도 통째 디코딩 시 OOM(오프라인 감사 rank5) → 다운샘플. */
    private fun decodeDataUrl(dataUrl: String?): android.graphics.Bitmap? =
        com.detailline.callfollowcrm.util.ImageDownsample.decodeDataUrl(dataUrl)

    // 🧹 ownerExists(/api/owner/exists) 제거 — 부르는 곳이 0곳이었다. 서버 길은 남겨둠(무해).

    private suspend fun post(url: String, payload: JSONObject, opKey: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val b = Request.Builder().url(url)
                    .post(payload.toString().toRequestBody(jsonMedia))
                // 📮 멱등키 — 우체통 재시도 때 같은 키면 서버가 한 번만 반영(§5). 없으면 지금과 동일.
                opKey?.takeIf { it.isNotBlank() }?.let { b.header("X-Op-Key", it) }
                val req = b.build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                }
                Unit
            }
        }

    private fun parseSites(body: String): List<SharedSite> {
        if (body.isBlank()) return emptyList()
        val arr: JSONArray = JSONObject(body).optJSONArray("sites") ?: return emptyList()
        return mergeSameSite((0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            SharedSite(
                shareId = o.optString("share_id"),
                ownerPhone = o.optString("owner_phone"),
                ownerName = o.optString("owner_name").ifBlank { "사장님" },
                partnerName = o.optString("partner_name").takeIf { it.isNotBlank() && it != "null" },
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
        })
    }

    /**
     * 🤝 **같은 현장이 두 벌이면 한 벌로.** (2026-09-30 사장님 "상률로32 이현장 왜2개로 표시되지")
     *
     * 같은 사장(ownerPhone) · 같은 주소 · 같은 시각이면 **같은 현장**이다.
     * 실제로 그렇게 두 번 온 것이 4건 있었다 — 보낸 분이 며칠 뒤 또 보낸 것이다.
     *
     * 남길 쪽 = **내용이 더 찬 쪽.** 나중 것이 일당도 시간도 비어 있는 경우가 있었다
     * (그래서 일정에 「00:00 · 금액 없음」으로 떴다). 그냥 최신을 남기면 **적힌 걸 잃는다.**
     *
     * ⚠️ 주소가 없으면 합치지 않는다 — 주소 없는 현장끼리 엮으면 **다른 현장이 사라진다.**
     * ⚠️ 시각이 다르면 합치지 않는다 — 오전·오후 두 번 가는 진짜 두 건일 수 있다.
     */
    internal fun mergeSameSite(list: List<SharedSite>): List<SharedSite> {
        if (list.size < 2) return list
        val out = LinkedHashMap<String, SharedSite>()
        val loose = ArrayList<SharedSite>()
        for (site in list) {
            val addr = site.addr?.filter { !it.isWhitespace() }.orEmpty()
            if (addr.isBlank() || site.scheduledAtMs <= 0L) { loose.add(site); continue }
            val key = phoneKey(site.ownerPhone) + "|" + addr + "|" + site.scheduledAtMs
            val prev = out[key]
            out[key] = if (prev == null || richness(site) > richness(prev)) site else prev
        }
        return out.values.toList() + loose
    }

    /** 어느 쪽이 더 찼나 — 일당·시간·전해둘 말·진행이 있으면 그쪽이 진짜다. */
    internal fun richness(s: SharedSite): Int =
        (if (s.dailyWage != null) 2 else 0) +
            (if (!s.timeLabel.isNullOrBlank()) 2 else 0) +
            (if (!s.workSummary.isNullOrBlank()) 1 else 0) +
            (if (!s.memo.isNullOrBlank()) 1 else 0) +
            (if (s.progress != Progress.ASSIGNED) 1 else 0)

    private fun phoneKey(phone: String): String = phone.filter { it.isDigit() }
}

/**
 * 화면 표시용 현장 이름 — 공유할 때 주소가 안 잡혀 "이 현장"/"협업 현장"으로 굳은 라벨 대신,
 *   주소가 있으면 주소를 보여준다. 일정·협업현장 어디서나 같은 규칙. (2026-06-20 사장님)
 *   우선순위: 주소 라벨(지역+건물) > 진짜 현장명 > 주소 원문 > "협업 현장".
 *   ※ 주소가 아예 없는 현장은 보여줄 주소가 없어 "협업 현장"으로 떨어짐(= 그 현장에 주소를 등록해야 주소가 뜸).
 */
fun siteDisplayName(site: SharedSiteRepository.SharedSite): String {
    com.detailline.callfollowcrm.util.AddressExtractor.siteLabel(site.addr).takeIf { it.isNotBlank() }?.let { return it }
    site.title.takeIf { it.isNotBlank() && it != "이 현장" && it != "협업 현장" }?.let { return it }
    site.addr?.takeIf { it.isNotBlank() }?.let { return it }
    return "협업 현장"
}
