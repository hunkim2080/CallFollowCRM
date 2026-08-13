package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 시공막내 웹 사진 캘린더(읽기전용 뷰어) — 앱측 배선.
 *   서버 완료: docs/SERVER_HANDOFF_web_photo_calendar_SERVER_DONE.md (B안 확정).
 *   서버엔 고객/일정 테이블이 없어, 앱이 뷰어용 경량 스케줄 피드를 '덮어쓰기' push → 서버가 이걸로 웹 달력/현장목록을 그림.
 *   QR 로그인 = 노트북 웹이 QR 표시 → 폰이 스캔(딥링크 /web/authorize?t=) → 이 authorize 호출로 승인(폰=열쇠).
 *   서버는 전부 읽기전용 — 앱은 push / authorize / logout 만.
 *
 *   POST /api/web/schedule-feed        피드 덮어쓰기(달력 데이터원)
 *   POST /api/web/authorize            QR 티켓 승인(폰이 owner 증명)
 *   POST /api/web/logout-all?owner_phone=   이 owner 의 웹 세션 전부 무효(폰 원격 로그아웃)
 */
class WebFeedRepository(
    private val baseUrl: String = AppConfig.BASE_URL
) {
    private val client = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)   // 전체 호출 상한 — 재시도/route 누적 hang 방지(오프라인 감사)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** 뷰어 캘린더용 현장 1건. 서버 web_schedule_feed 계약(customer_digits 끝8 정규화로 team_site_photos 조인). */
    data class FeedItem(
        val customerDigits: String,   // 숫자만
        val name: String,
        val apartment: String,        // 앱엔 아파트/동호 분리 필드 없음 → 고객 주소(free-text) 그대로
        val dongHo: String,           // 분리 필드 없어 "" (지어내지 않음)
        val workDate: String,         // "YYYY-MM-DD" 시공일
        val category: String,         // 시공종류(카테고리명), 없으면 ""
        val completed: Boolean,
        /**
         * 이 고객(현장)에 연결된 협업 share_id 목록. 직원(협업 사장)이 올린 사진은 team_site_photos 에
         * customer_phone=NULL·share_id=X 로 저장되어 웹 뷰어가 못 잇는다 → 이 지도로 그 고객 현장에 붙임. (2026-08-13)
         */
        val shareIds: List<String> = emptyList()
    )

    /** authorize 결과 — 만료(410)와 일반 실패를 구분해 안내 문구를 다르게. */
    enum class AuthResult { OK, EXPIRED, ERROR }

    /** 스케줄 피드 덮어쓰기 push. items = 시공일이 잡힌 현장 전부. */
    suspend fun pushFeed(ownerPhone: String, items: List<FeedItem>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val arr = JSONArray()
                items.forEach { item ->
                    arr.put(JSONObject().apply {
                        put("customer_digits", item.customerDigits)
                        put("name", item.name)
                        put("apartment", item.apartment)
                        put("dong_ho", item.dongHo)
                        put("work_date", item.workDate)
                        put("category", item.category)
                        put("completed", item.completed)
                        if (item.shareIds.isNotEmpty()) {
                            put("share_ids", JSONArray().apply { item.shareIds.forEach { put(it) } })
                        }
                    })
                }
                val body = JSONObject().apply {
                    put("owner_phone", ownerPhone)
                    put("items", arr)
                }.toString().toRequestBody(jsonMedia)
                val req = Request.Builder().url("$baseUrl/api/web/schedule-feed").post(body).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    Unit
                }
            }
        }

    /** QR 티켓 승인 — 폰이 로그인된 owner 임을 서버에 증명. 410=티켓 만료(60초 초과). */
    suspend fun authorize(ticket: String, ownerPhone: String): AuthResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("ticket", ticket)
                    put("owner_phone", ownerPhone)
                }.toString().toRequestBody(jsonMedia)
                val req = Request.Builder().url("$baseUrl/api/web/authorize").post(body).build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> AuthResult.OK
                        resp.code == 410 -> AuthResult.EXPIRED
                        else -> AuthResult.ERROR
                    }
                }
            }.getOrDefault(AuthResult.ERROR)
        }

    /** 폰 원격 로그아웃 — 이 owner 의 웹 세션 전부 무효. */
    suspend fun logoutAll(ownerPhone: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val op = java.net.URLEncoder.encode(ownerPhone, "UTF-8")
                val req = Request.Builder()
                    .url("$baseUrl/api/web/logout-all?owner_phone=$op")
                    .post("".toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    Unit
                }
            }
        }
}
