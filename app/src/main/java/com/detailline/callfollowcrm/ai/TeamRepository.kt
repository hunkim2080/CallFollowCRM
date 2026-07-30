package com.detailline.callfollowcrm.ai

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
 * 팀 관리 (비즈니스 요금제) — 맥미니 서버 팀 API.  2026-06-05.
 *
 * 프로토 #s-team 1:1. 팀원은 앱 설치 X — 사장님이 보낸 링크(/team/member/{token})로 본인 일정만 봄.
 *   - POST   /api/team/member/invite       팀원 추가 + 링크 발급 (sms_draft 반환, 자동발송 X)
 *   - GET    /api/team/members             팀원 목록
 *   - DELETE /api/team/member/{member_id}  팀원 제외 (링크 차단)
 *   - GET    /api/team/events              팀원 이벤트 (출발/사진/도착) — 출발 알림용
 *
 * owner_phone = 사장님 사업자 전화(AppPreferences.bizPhone). 서버 tier 검사는 invite 에만(미가입 시 403).
 */
class TeamRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    data class TeamMember(
        val memberId: String,
        val phone: String,
        val name: String,
        val role: String,          // "owner" | "worker"
        val tint: Int,
        val createdAtMs: Long
    )

    data class InviteResult(
        val memberId: String,
        val name: String,
        val role: String,
        val token: String,
        val url: String,
        val smsDraft: String
    )

    data class TeamEvent(
        val eventId: Long,
        val memberId: String?,
        val memberName: String?,
        val eventType: String,     // "departed" | "photo" | "arrived"
        val payload: JSONObject?,
        val createdAtMs: Long
    )

    /** 팀원 목록. */
    suspend fun members(ownerPhone: String): Result<List<TeamMember>> = withContext(Dispatchers.IO) {
        runCatching {
            val op = java.net.URLEncoder.encode(ownerPhone, "UTF-8")
            val req = Request.Builder()
                .url("$baseUrl/api/team/members?owner_phone=$op")
                .get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("items")
                    ?: org.json.JSONArray()
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    TeamMember(
                        memberId = o.getString("member_id"),
                        phone = o.optString("phone"),
                        name = o.optString("name"),
                        role = o.optString("role", "worker"),
                        tint = o.optInt("tint"),
                        createdAtMs = o.optLong("created_at_ms")
                    )
                }
            }
        }
    }

    /** 팀원 추가 + 링크 발급. 동일 phone 팀원이 이미 있으면 서버가 재사용(같은 member_id) + 새 토큰. */
    suspend fun invite(
        ownerPhone: String,
        name: String,
        phone: String,
        role: String = "worker",
        tint: Int = 0
    ): Result<InviteResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("owner_phone", ownerPhone)
                put("name", name)
                put("phone", phone)
                put("role", role)
                put("tint", tint)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/team/member/invite")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val o = JSONObject(resp.body?.string().orEmpty())
                InviteResult(
                    memberId = o.getString("member_id"),
                    name = o.optString("name", name),
                    role = o.optString("role", role),
                    token = o.optString("token"),
                    url = o.optString("url"),
                    smsDraft = o.optString("sms_draft")
                )
            }
        }
    }

    /** 팀원 제외 (링크 차단). */
    suspend fun remove(memberId: String, ownerPhone: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val op = java.net.URLEncoder.encode(ownerPhone, "UTF-8")
            val mid = java.net.URLEncoder.encode(memberId, "UTF-8")
            val req = Request.Builder()
                .url("$baseUrl/api/team/member/$mid?owner_phone=$op")
                .delete().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                Unit
            }
        }
    }

    /** 배정된 일정 1건 — schedule-snapshot 의 item. */
    data class SnapshotItem(
        val whenLabel: String,         // "오늘" | "5/30" 등
        val customerLabel: String,     // 현장/고객 표시명
        val customerPhone: String?,    // 고객 전화 — 팀원 사진을 이 고객에 연결하는 키(+팀원 연락용)
        val time: String?,             // "09:00"
        val addr: String?,
        val workSummary: String?,
        val teamMemo: String?,         // 사장님 → 직원 전달 메모 (고객 메모 아님)
        val days: Int,
        val isToday: Boolean,
        val scheduledAtMs: Long
    )

    /**
     * 팀원 일정 snapshot push — 팀원 웹뷰(/team/member/{token})에 표시될 배정 일정 통째 갱신.
     *   활성 토큰 없음(404) 시 호출부가 invite(reuse) 후 재시도.
     */
    suspend fun pushScheduleSnapshot(memberId: String, items: List<SnapshotItem>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val arr = org.json.JSONArray()
                items.forEach { it2 ->
                    arr.put(JSONObject().apply {
                        put("when", it2.whenLabel)
                        put("customer_label", it2.customerLabel)
                        it2.customerPhone?.let { put("customer_phone", it) }
                        it2.time?.let { put("time", it) }
                        it2.addr?.let { put("addr", it) }
                        it2.workSummary?.let { put("work_summary", it) }
                        it2.teamMemo?.let { put("team_memo", it) }
                        put("days", it2.days)
                        put("is_today", it2.isToday)
                        put("scheduled_at_ms", it2.scheduledAtMs)
                    })
                }
                val payload = JSONObject().apply {
                    put("member_id", memberId)
                    put("items", arr)
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/team/schedule-snapshot")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    Unit
                }
            }
        }

    /** 팀원 이벤트(출발/사진/도착) 최신순. 출발 알림 표시용. */
    suspend fun events(ownerPhone: String, sinceMs: Long = 0L, limit: Int = 30): Result<List<TeamEvent>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val op = java.net.URLEncoder.encode(ownerPhone, "UTF-8")
                val req = Request.Builder()
                    .url("$baseUrl/api/team/events?owner_phone=$op&since_ms=$sinceMs&limit=$limit")
                    .get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("events")
                        ?: org.json.JSONArray()
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        TeamEvent(
                            eventId = o.optLong("event_id"),
                            memberId = o.optString("member_id").takeIf { it.isNotBlank() },
                            memberName = o.optString("member_name").takeIf { it.isNotBlank() && it != "null" },
                            eventType = o.optString("event_type"),
                            payload = o.optJSONObject("payload"),
                            createdAtMs = o.optLong("created_at_ms")
                        )
                    }
                }
            }
        }
}
