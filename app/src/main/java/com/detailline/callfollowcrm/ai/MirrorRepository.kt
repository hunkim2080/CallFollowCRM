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
    private val client = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)   // 전체 호출 상한 — 재시도/route 누적 hang 방지 (2026-08-12 오프라인 감사)
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

    /** 미수 현장 1건 — 본폰 뷰어의 "미수금 N건" 탭 시 "어디서 얼마 못 받았나" 목록용. */
    data class Receivable(
        val name: String,
        val amount: Long,          // 미수 잔액(원)
        val address: String?,
        val phone: String?,        // 하이픈 포함
        val overdueDays: Int?,     // 시공/완료 후 경과일(있으면)
        // 이 미수가 걸린 날 = YYYY-MM-DD (완료일 우선, 없으면 시공 예약일. 둘 다 없으면 null).
        //   뷰어가 "미수금 탭 → 달력에 그 날짜를 연한 빨강으로" 칠하는 데 쓴다. (2026-07-15 사장님)
        val date: String? = null
    )

    /**
     * 뷰어에 그릴 현장 1건. date=YYYY-MM-DD(필수). phone=하이픈 포함.
     *
     * total 의 뜻이 collab 여부에 따라 다르다:
     *   - collab=false (내 고객)  → **총금액**(받을 시공비 전체, 원). 0=미입력.
     *   - collab=true  (협업 현장) → **내 일당**(원). 남의 고객 시공비는 안 보내고 알 필요도 없다. 0=미입력.
     * 그래서 뷰어는 collab 이면 금액 라벨을 "일당"으로 그려야 한다(안 그러면 남의 매출로 오해).
     * collab=true 는 phone 이 항상 null (고객 번호는 벽 — SPEC_shared_sites_owner_to_owner.md §1).
     */
    data class MirrorItem(
        val date: String,
        val time: String?,
        val days: Int,
        val name: String,
        val address: String?,
        val phone: String?,
        val memo: String?,
        val completed: Boolean,
        val total: Long,
        val collab: Boolean = false
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

    /**
     * 일정+돈 스냅샷 통째 갱신(덮어쓰기). 본폰이 수락하면 이 데이터를 봄.
     *   todayIn=오늘 들어온 돈(원), totalIn=지금까지 받은 돈 누적(원, 사장님이 보고 싶어하는 값).
     */
    suspend fun pushSnapshot(
        ownerPhone: String,
        label: String,
        items: List<MirrorItem>,
        todayIn: Long,
        totalIn: Long,
        unpaid: Long,
        unpaidCount: Int,
        receivables: List<Receivable> = emptyList()
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
                    // 금액(원). collab=false 면 총금액, collab=true 면 내 일당 — 뜻이 다르니 뷰어는 collab 을 보고 라벨을 정한다.
                    if (it.total > 0L) put("total", it.total)
                    if (it.collab) put("collab", true)          // 협업 현장 딱지 (없으면 내 현장)
                })
            }
            val payload = JSONObject().apply {
                put("owner_phone", ownerPhone)
                put("label", label)
                put("items", arr)
                put("money", JSONObject().apply {
                    put("todayIn", todayIn)
                    // 지금까지 받은 돈 누적(원) — 사장님: "오늘 입금이 아니라 지금까지 입금된 금액이 나와야 할 듯".
                    //   todayIn 도 같이 보내 뷰어가 갈아탈 때까지 깨지지 않게(기존 키 유지).
                    put("totalIn", totalIn)
                    put("unpaid", unpaid)
                    put("unpaidCount", unpaidCount)
                    // 미수 현장 목록 — 뷰어 "미수금 N건" 탭 시 어디서 얼마 못 받았나 표시.
                    if (receivables.isNotEmpty()) {
                        put("receivables", JSONArray().apply {
                            receivables.forEach { r ->
                                put(JSONObject().apply {
                                    put("name", r.name)
                                    put("amount", r.amount)
                                    r.address?.let { put("address", it) }
                                    r.phone?.let { put("phone", it) }
                                    r.overdueDays?.let { put("overdueDays", it) }
                                    r.date?.let { put("date", it) }   // 달력에 미수 날짜 칠하기용
                                })
                            }
                        })
                    }
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
