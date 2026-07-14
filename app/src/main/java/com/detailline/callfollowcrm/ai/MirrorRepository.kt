package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.AppConfig
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
 * 본폰 미러 v2 "공유 신청/수락" (2026-07-14, 사장님 확정) — docs/SERVER_HANDOFF_mirror_v2.md.
 *   본폰(빈 달력, 웹)이 업무폰 고정 코드를 입력해 "공유 신청" → 업무폰이 수락 → 공유.
 *   협업 현장 요청(수락/거절) 시스템과 동일 컨셉. 규칙: "업무폰이 코드 만들고, 본폰이 넣는다."
 *   옵트인 필수(사장님이 켤 때만). 본폰 열기 비번은 선택(기본 없음).
 *
 *   POST /api/mirror/mycode      업무폰 고정 공유 코드 조회/생성(idempotent)
 *   GET  /api/mirror/shares      수락 대기 신청 + 공유중 목록(앱이 폴링)
 *   POST /api/mirror/respond     신청 수락/거절
 *   POST /api/mirror/disconnect  공유중 해제(그 본폰에서 이 사업장 빠짐)
 *   POST /api/mirror/snapshot    일정+돈 스냅샷 갱신(기존 유지)
 */
class MirrorRepository(
    private val baseUrl: String = AppConfig.BASE_URL
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** 공유 신청 1건(수락 대기). home_phone = 신청한 본폰 번호(이름표). */
    data class ShareRequest(val id: Long, val homePhone: String, val createdAtMs: Long)

    /** 공유중 연결 1건. */
    data class Connection(val id: Long, val homePhone: String, val sinceMs: Long)

    data class Shares(val pending: List<ShareRequest>, val accepted: List<Connection>)

    /** mycode 응답 — code=본폰에 넣을 코드, qrUrl=QR에 실을 URL(서버가 QR 자동수락용 시크릿을 넣을 수 있음). */
    data class MyCode(val code: String, val qrUrl: String?)

    /** 뷰어에 그릴 현장 1건. date=YYYY-MM-DD(필수). total=총금액(원, 0=미입력). phone=하이픈 포함. */
    data class MirrorItem(
        val date: String,
        val time: String?,
        val days: Int,
        val name: String,
        val address: String?,
        val phone: String?,
        val memo: String?,
        val completed: Boolean,
        val total: Long
    )

    /** 이 업무폰의 고정 공유 코드 조회/생성. label·tint 갱신. 앱이 "내 공유 코드"+QR 표시용으로 호출. */
    suspend fun myCode(ownerPhone: String, label: String, tint: Int = 0): Result<MyCode> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("owner_phone", ownerPhone)
                    put("label", label)
                    put("tint", tint)
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/mirror/mycode")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val o = JSONObject(resp.body?.string().orEmpty())
                    MyCode(
                        code = o.optString("code"),
                        // 서버가 qrUrl(자동수락 시크릿 포함)을 주면 그걸 QR에, 없으면 앱이 homeUrl?code= 로 폴백.
                        qrUrl = o.optString("qrUrl").takeIf { it.isNotBlank() && it != "null" }
                    )
                }
            }
        }

    /** 수락 대기 신청 + 공유중 목록. */
    suspend fun shares(ownerPhone: String): Result<Shares> = withContext(Dispatchers.IO) {
        runCatching {
            val op = java.net.URLEncoder.encode(ownerPhone, "UTF-8")
            val req = Request.Builder()
                .url("$baseUrl/api/mirror/shares?owner_phone=$op")
                .get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val o = JSONObject(resp.body?.string().orEmpty())
                val pending = (o.optJSONArray("pending") ?: JSONArray()).let { arr ->
                    (0 until arr.length()).map { i ->
                        val e = arr.getJSONObject(i)
                        ShareRequest(e.optLong("id"), e.optString("home_phone"), e.optLong("created_at_ms"))
                    }
                }
                val accepted = (o.optJSONArray("accepted") ?: JSONArray()).let { arr ->
                    (0 until arr.length()).map { i ->
                        val e = arr.getJSONObject(i)
                        Connection(e.optLong("id"), e.optString("home_phone"), e.optLong("since_ms"))
                    }
                }
                Shares(pending, accepted)
            }
        }
    }

    /** 신청 수락(accept=true) / 거절(false). */
    suspend fun respond(ownerPhone: String, shareId: Long, accept: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("owner_phone", ownerPhone)
                    put("share_id", shareId)
                    put("accept", accept)
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/mirror/respond")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    Unit
                }
            }
        }

    /** 공유중 해제 — 그 본폰 달력에서 이 사업장이 빠짐. */
    suspend fun disconnect(ownerPhone: String, shareId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("owner_phone", ownerPhone)
                    put("share_id", shareId)
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/mirror/disconnect")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    Unit
                }
            }
        }

    /** 일정+돈 스냅샷 통째 갱신(덮어쓰기). 본폰이 수락하면 이 데이터를 봄. */
    suspend fun pushSnapshot(
        ownerPhone: String,
        label: String,
        items: List<MirrorItem>,
        todayIn: Long,
        unpaid: Long,
        unpaidCount: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray()
            items.forEach { it ->
                arr.put(JSONObject().apply {
                    put("date", it.date)
                    it.time?.let { t -> put("time", t) }
                    put("days", it.days)
                    put("name", it.name)
                    it.address?.let { a -> put("address", a) }
                    it.phone?.let { p -> put("phone", p) }
                    it.memo?.let { m -> put("memo", m) }
                    put("completed", it.completed)
                    if (it.total > 0L) put("total", it.total)   // 총금액(원) — 뷰어가 "얼마짜리 현장" 표시
                })
            }
            val payload = JSONObject().apply {
                put("owner_phone", ownerPhone)
                put("label", label)
                put("items", arr)
                put("money", JSONObject().apply {
                    put("todayIn", todayIn)
                    put("unpaid", unpaid)
                    put("unpaidCount", unpaidCount)
                })
            }
            val req = Request.Builder()
                .url("$baseUrl/api/mirror/snapshot")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                Unit
            }
        }
    }
}
