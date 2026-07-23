package com.detailline.callfollowcrm.ai

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
 * 박람회(Expo) Phase 1 서버 연동 — "종이 계약서 없애기". (2026-07-22 사장님)
 *   스펙: docs/SERVER_HANDOFF_expo_phase1.md · 확정: docs/EXPO_DECISIONS.md
 *   격리 원칙: 박람회 데이터는 서버 expo_* 전용, 기존 정산/고객과 안 섞임.
 *
 * 흐름: 방장이 방 개설(코드) → 상품/단가 등록 → 팀원 코드로 합류 →
 *       상담원 계약서 열기(QR) → 고객폰 웹에서 작성·서명·제출 → 앱이 폴링으로 수신 → 계약서 사본.
 *   분배·진행률(확정6·8)은 Phase 3 → 여기 없음(서버도 스키마만).
 */
class ExpoRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ── 데이터 모델 ──
    data class Room(
        val roomId: String, val role: String, val name: String,
        val code: String?,               // 방장(owner)에게만 내려옴
        val memberCount: Int, val productCount: Int, val contractCount: Int,
        val closed: Boolean
    )
    data class Member(val name: String, val role: String, val phone: String) // phone: 팀원에겐 마스킹
    data class Product(val productId: Long, val kind: String, val name: String, val unitPrice: Long, val sort: Int)
    data class RoomDetail(val name: String, val myRole: String, val members: List<Member>, val catalog: List<Product>)
    data class Session(val sessionId: String, val secret: String, val url: String, val qrUrl: String)
    data class Submission(
        val contractId: Long, val customerName: String, val customerPhoneMasked: String,
        val products: String, val finalAmount: Long, val status: String,
        val agentName: String, val assignedName: String?, val createdAtMs: Long,
        val apartment: String, val dongHo: String, val address: String, val note: String,
        /** 시공 예정일(ms). 0 = 미정. 박람회 달력에 이 날짜로 표시. */
        val scheduledAtMs: Long,
        /** 고객 전화(전체) — 팀이 전화 걸 수 있게. 서버가 내려주면 채워짐(없으면 masked 만). */
        val customerPhone: String
    )
    data class Submissions(val count: Int, val totalAmount: Long, val items: List<Submission>)
    /** 상품 등록 입력(방장) — product_id 없이 kind/name/unit_price 만 보냄. */
    data class ProductDraft(val kind: String, val name: String, val unitPrice: Long)

    // ── 실시간 계약서 (Phase 4) ──
    data class LiveItem(val productId: Long, val kind: String, val name: String, val unitPrice: Long, val qty: Int, val line: Long)
    data class LiveState(
        val status: String, val contractId: Long?,
        val catalog: List<Product>, val items: List<LiveItem>,
        val productTotal: Long, val discount: Long,
        val depositEnabled: Boolean, val depositAmount: Long, val finalAmount: Long,
        val customerName: String, val customerPhone: String,
        val apartment: String, val dongHo: String, val address: String,
        val signaturePresent: Boolean, val note: String,
        /** 고객이 서명 후 [완료]를 눌렀는지. 상담사가 수정(live/agent)하면 서버가 false 로 되돌림. (Phase4 완료흐름) */
        val customerConfirmed: Boolean
    )
    /** 상담사 상품 선택(체크/수량/할인/계약금) — 서버에 push. 서버가 final_amount 재계산. */
    data class AgentPush(val productTotal: Long, val discount: Long, val finalAmount: Long)
    data class Finalized(val contractId: Long, val finalAmount: Long, val receiptUrl: String)

    // ── 공통 ──
    private fun digits(s: String) = s.filter { it.isDigit() }

    private fun postJson(path: String, payload: JSONObject): JSONObject {
        val req = Request.Builder().url("$baseUrl$path")
            .post(payload.toString().toRequestBody(jsonMedia)).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} ${body.take(200)}")
            if (body.isBlank()) throw IOException("empty body")
            return JSONObject(body)
        }
    }

    private fun getJson(path: String): JSONObject {
        val req = Request.Builder().url("$baseUrl$path").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} ${body.take(200)}")
            if (body.isBlank()) throw IOException("empty body")
            return JSONObject(body)
        }
    }

    private fun parseProduct(o: JSONObject) = Product(
        productId = o.optLong("product_id"),
        kind = o.optString("kind", "product"),
        name = o.optString("name"),
        unitPrice = o.optLong("unit_price"),
        sort = o.optInt("sort")
    )

    private fun JSONObject.catalog(key: String = "catalog"): List<Product> =
        optJSONArray(key)?.let { arr -> (0 until arr.length()).map { parseProduct(arr.getJSONObject(it)) } } ?: emptyList()

    // ── 방 (Room) ──
    suspend fun createRoom(ownerPhone: String, name: String, ownerName: String?): Result<Room> =
        withContext(Dispatchers.IO) {
            runCatching {
                val o = postJson("/api/expo/room/create", JSONObject().apply {
                    put("owner_phone", digits(ownerPhone))
                    put("name", name.trim())
                    ownerName?.takeIf { it.isNotBlank() }?.let { put("owner_name", it.trim()) }
                })
                Room(o.getString("room_id"), "owner", o.optString("name", name),
                    o.optString("code").takeIf { it.isNotBlank() }, 1, 0, 0, false)
            }
        }

    suspend fun joinRoom(code: String, phone: String, name: String): Result<Room> =
        withContext(Dispatchers.IO) {
            runCatching {
                val o = postJson("/api/expo/room/join", JSONObject().apply {
                    put("code", code.trim())
                    put("phone", digits(phone))
                    put("name", name.trim())
                })
                Room(o.getString("room_id"), o.optString("role", "member"), o.optString("name"),
                    null, 0, 0, 0, false)
            }
        }

    suspend fun rooms(phone: String): Result<List<Room>> = withContext(Dispatchers.IO) {
        runCatching {
            val o = getJson("/api/expo/rooms?phone=${digits(phone)}")
            o.optJSONArray("rooms")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val r = arr.getJSONObject(i)
                    Room(
                        r.getString("room_id"), r.optString("role", "member"), r.optString("name"),
                        r.optString("code").takeIf { it.isNotBlank() },
                        r.optInt("memberCount"), r.optInt("productCount"),
                        r.optInt("contractCount"), r.optBoolean("closed")
                    )
                }
            } ?: emptyList()
        }
    }

    suspend fun roomDetail(roomId: String, phone: String): Result<RoomDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val o = getJson("/api/expo/room/$roomId?phone=${digits(phone)}")
            val members = o.optJSONArray("members")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val m = arr.getJSONObject(i)
                    Member(m.optString("name"), m.optString("role", "member"), m.optString("phone"))
                }
            } ?: emptyList()
            RoomDetail(o.optString("name"), o.optString("myRole", "member"), members, o.catalog())
        }
    }

    // ── 상품 카탈로그 ──
    suspend fun setProducts(roomId: String, ownerPhone: String, products: List<ProductDraft>): Result<List<Product>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val arr = JSONArray()
                products.forEach {
                    arr.put(JSONObject().apply {
                        put("kind", it.kind)
                        put("name", it.name.trim())
                        put("unit_price", it.unitPrice)
                    })
                }
                val o = postJson("/api/expo/products/set", JSONObject().apply {
                    put("room_id", roomId)
                    put("owner_phone", digits(ownerPhone))
                    put("products", arr)
                })
                o.catalog()
            }
        }

    suspend fun getProducts(roomId: String): Result<List<Product>> = withContext(Dispatchers.IO) {
        runCatching { getJson("/api/expo/products?room_id=$roomId").catalog() }
    }

    // ── 계약서 세션 (QR) ──
    suspend fun createSession(roomId: String, agentPhone: String): Result<Session> = withContext(Dispatchers.IO) {
        runCatching {
            val o = postJson("/api/expo/contract/session", JSONObject().apply {
                put("room_id", roomId)
                put("agent_phone", digits(agentPhone))
            })
            Session(o.getString("session_id"), o.optString("secret"), o.optString("url"), o.getString("qrUrl"))
        }
    }

    // ── 팀 접수서 목록 ──
    suspend fun submissions(roomId: String, phone: String): Result<Submissions> = withContext(Dispatchers.IO) {
        runCatching {
            val o = getJson("/api/expo/submissions?room_id=$roomId&phone=${digits(phone)}")
            val items = o.optJSONArray("items")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val s = arr.getJSONObject(i)
                    Submission(
                        contractId = s.optLong("contract_id"),
                        customerName = s.optString("customer_name"),
                        customerPhoneMasked = s.optString("customer_phone_masked"),
                        products = s.optString("products"),
                        finalAmount = s.optLong("final_amount"),
                        status = s.optString("status", "submitted"),
                        agentName = s.optString("agent_name"),
                        assignedName = s.optString("assigned_name").takeIf { it.isNotBlank() && it != "null" },
                        createdAtMs = s.optLong("created_at_ms"),
                        apartment = s.optString("apartment"),
                        dongHo = s.optString("dong_ho"),
                        address = s.optString("address"),
                        note = s.optString("note"),
                        scheduledAtMs = s.optLong("scheduled_at_ms"),
                        customerPhone = s.optString("customer_phone")
                    )
                }
            } ?: emptyList()
            Submissions(o.optInt("count", items.size), o.optLong("totalAmount"), items)
        }
    }

    // ── 실시간 계약서 API (상담사 앱) ──
    /** 상담사 선택 push — 체크/수량/할인/계약금. 서버가 final_amount 재계산해 반환. (디바운스 권장) */
    suspend fun liveAgentPush(
        sessionId: String, secret: String, items: List<Pair<Long, Int>>,
        discount: Long, depositEnabled: Boolean, depositAmount: Long, note: String = ""
    ): Result<AgentPush> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray()
            items.forEach { (pid, qty) -> arr.put(JSONObject().put("product_id", pid).put("qty", qty)) }
            val o = postJson("/api/expo/contract/live/agent", JSONObject().apply {
                put("session_id", sessionId); put("secret", secret)
                put("items", arr); put("discount", discount)
                put("deposit_enabled", depositEnabled); put("deposit_amount", depositAmount)
                put("note", note)   // 특이사항/비고. 서버 저장은 cowork 대기(현재 무시돼도 안전).
            })
            AgentPush(o.optLong("product_total"), o.optLong("discount"), o.optLong("final_amount"))
        }
    }

    /** 합쳐진 라이브 상태 (앱·웹이 1.5초 폴링). 고객 정보·서명 도착 여부 포함. */
    suspend fun liveGet(sessionId: String, secret: String): Result<LiveState> = withContext(Dispatchers.IO) {
        runCatching {
            val o = getJson("/api/expo/contract/live/$sessionId?k=$secret")
            val items = o.optJSONArray("items")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val it = arr.getJSONObject(i)
                    LiveItem(it.optLong("product_id"), it.optString("kind", "product"), it.optString("name"),
                        it.optLong("unit_price"), it.optInt("qty"), it.optLong("line"))
                }
            } ?: emptyList()
            LiveState(
                status = o.optString("status", "live"),
                contractId = o.optLong("contract_id").takeIf { it > 0 },
                catalog = o.catalog(),
                items = items,
                productTotal = o.optLong("product_total"),
                discount = o.optLong("discount"),
                depositEnabled = o.optBoolean("deposit_enabled"),
                depositAmount = o.optLong("deposit_amount"),
                finalAmount = o.optLong("final_amount"),
                customerName = o.optString("customer_name"),
                customerPhone = o.optString("customer_phone"),
                apartment = o.optString("apartment"),
                dongHo = o.optString("dong_ho"),
                address = o.optString("address"),
                signaturePresent = o.optBoolean("signature_present"),
                note = o.optString("note"),
                customerConfirmed = o.optBoolean("customer_confirmed")
            )
        }
    }

    /** 상담사 [완료] — 라이브 상태를 계약서로 굳힘. 이미 완료면 already 로 안전. */
    suspend fun finalize(sessionId: String, secret: String): Result<Finalized> = withContext(Dispatchers.IO) {
        runCatching {
            val o = postJson("/api/expo/contract/finalize", JSONObject().apply {
                put("session_id", sessionId); put("secret", secret)
            })
            Finalized(o.optLong("contract_id"), o.optLong("final_amount"), o.optString("receiptUrl"))
        }
    }

    /** 계약에 시공 예정일 지정(박람회 달력용). scheduledAtMs=0 이면 미정으로 되돌림. 방 멤버(phone)만. */
    suspend fun schedule(contractId: Long, phone: String, scheduledAtMs: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            postJson("/api/expo/contract/schedule", JSONObject().apply {
                put("contract_id", contractId)
                put("phone", digits(phone))
                put("scheduled_at_ms", scheduledAtMs)
            })
            Unit
        }
    }

    /** 시공자 배정(분배) — 계약을 팀원에게. assignedPhone="" 이면 배정 해제. 계약자≠시공자. 방 멤버만. */
    suspend fun assign(contractId: Long, phone: String, assignedPhone: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            postJson("/api/expo/contract/assign", JSONObject().apply {
                put("contract_id", contractId)
                put("phone", digits(phone))
                put("assigned_phone", digits(assignedPhone))
            })
            Unit
        }
    }

    /** 계약서 메모(특이사항) 저장/수정 — 방 멤버. 서버 /contract/memo 엔드포인트 필요(대기). */
    suspend fun setMemo(contractId: Long, phone: String, memo: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            postJson("/api/expo/contract/memo", JSONObject().apply {
                put("contract_id", contractId)
                put("phone", digits(phone))
                put("memo", memo)
            })
            Unit
        }
    }

    /** 계약서 사본/영수증 웹 URL (PDF·공유용). */
    fun receiptUrl(contractId: Long): String = "$baseUrl/expo/r/$contractId"
}
