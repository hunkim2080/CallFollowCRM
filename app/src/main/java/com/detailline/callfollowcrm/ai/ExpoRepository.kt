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
        .callTimeout(50, TimeUnit.SECONDS)   // 전체 호출 상한 — 재시도/route 누적 hang 방지 (2026-08-12 오프라인 감사)
        .build()

    /** OCR(Vision) 는 응답이 느려 별도 긴 타임아웃. */
    private val ocrClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(115, TimeUnit.SECONDS)  // OCR 전체 상한 — 느린 Vision 응답은 안 자르되 무한 hang만 차단 (2026-08-12 오프라인 감사)
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
    /** 방 단위 박람회 기본정보(8차) — 방장이 설정, 고객·계약서 공용. */
    data class RoomInfo(
        val apartment: String, val unitTypes: List<String>, val terms: String,
        val bizName: String, val bizNo: String, val repPhone: String, val officePhone: String,
        /** 방 템플릿(줄눈 등). 비면 자유상품 방. 새 방은 기본 "julnun". */
        val templateId: String = ""
    )
    data class RoomDetail(val name: String, val myRole: String, val members: List<Member>, val catalog: List<Product>, val info: RoomInfo?)
    data class Session(val sessionId: String, val secret: String, val url: String, val qrUrl: String)
    data class Submission(
        val contractId: Long, val customerName: String, val customerPhoneMasked: String,
        val products: String, val finalAmount: Long, val status: String,
        val agentName: String, val assignedName: String?, val assignedPhone: String = "", val createdAtMs: Long,
        val apartment: String, val dongHo: String, val address: String, val note: String,
        /** 시공 예정일(ms). 0 = 미정. 박람회 달력에 이 날짜로 표시. */
        val scheduledAtMs: Long,
        /** 고객 전화(전체) — 팀이 전화 걸 수 있게. 서버가 내려주면 채워짐(없으면 masked 만). */
        val customerPhone: String,
        /** 템플릿 계약이면 template_id (submissions 요약). 자유상품이면 "". */
        val templateId: String = "",
        /** 템플릿 계약의 실제 선택(줄눈 항목·재질/실리콘/청소) — 앱 계약서 '시공 내역' 구조화용. */
        val tplPick: TplPick? = null
    )
    /** 시공 내역(계약서 상세) — 줄눈=항목·재질, 실리콘/청소=항목명. */
    data class TplPick(
        val julnun: List<Pair<String, String>>,   // (항목, 재질)
        val silicone: List<String>,
        val cleaning: List<String>
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
        val customerConfirmed: Boolean,
        /** 템플릿 방(줄눈 등)이면 template_id·선택결과. 자유상품 방이면 templateId="" (기존 items 사용). */
        val templateId: String = "", val moveInDate: String = "", val templateSel: TemplateSel? = null
    )

    // ── 계약서 템플릿 (추가151) ──
    /** 템플릿 정의 — 앱이 이걸로 체크리스트 렌더. section.type = matrix(항목×재질) | checklist(항목만). */
    data class TplSection(val key: String, val type: String, val title: String, val materials: List<String>, val items: List<String>)
    data class TplPriceGroup(val key: String, val title: String, val fields: List<String>)  // fields 예: [total,deposit,balance]
    data class TemplateDef(
        val id: String, val name: String, val headerFields: List<String>,
        val sections: List<TplSection>, val priceGroups: List<TplPriceGroup>, val totals: List<String>
    )
    /** 선택 결과(서버 저장분 파싱) — 계약서 렌더용. matrix: 섹션키→[(항목,재질)], checklist: 섹션키→[항목], prices: 그룹키→(필드→금액). */
    data class TemplateSel(
        val matrix: Map<String, List<Pair<String, String>>>,
        val checklist: Map<String, List<String>>,
        val prices: Map<String, Map<String, Long>>,
        val grandTotal: Long, val payer: String
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

    private fun postJsonOcr(path: String, payload: JSONObject): JSONObject {
        val req = Request.Builder().url("$baseUrl$path")
            .post(payload.toString().toRequestBody(jsonMedia)).build()
        ocrClient.newCall(req).execute().use { resp ->
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
            RoomDetail(o.optString("name"), o.optString("myRole", "member"), members, o.catalog(), parseRoomInfo(o.optJSONObject("info")))
        }
    }

    private fun parseRoomInfo(o: JSONObject?): RoomInfo? {
        if (o == null) return null
        val types = o.optJSONArray("unit_types")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } ?: emptyList()
        return RoomInfo(
            apartment = o.optString("apartment"), unitTypes = types, terms = o.optString("terms"),
            bizName = o.optString("biz_name"), bizNo = o.optString("biz_no"),
            repPhone = o.optString("rep_phone"), officePhone = o.optString("office_phone"),
            templateId = o.optString("template_id")
        )
    }

    /** 방장이 박람회 기본정보 설정/수정 (개설 후). 방장만. */
    suspend fun setRoomInfo(roomId: String, ownerPhone: String, info: RoomInfo): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val types = JSONArray().apply { info.unitTypes.forEach { put(it) } }
            postJson("/api/expo/room/info", JSONObject().apply {
                put("room_id", roomId)
                put("owner_phone", digits(ownerPhone))
                put("apartment", info.apartment.trim())
                put("unit_types", types)
                put("terms", info.terms.trim())
                put("biz_name", info.bizName.trim())
                put("biz_no", info.bizNo.trim())
                put("rep_phone", digits(info.repPhone))
                put("office_phone", digits(info.officePhone))
            })
            Unit
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

    // ── 템플릿 (추가151) ──
    private fun JSONObject.strList(key: String): List<String> =
        optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()

    /** 템플릿 정의 로드 (앱이 체크리스트 렌더). 없으면 실패. */
    suspend fun getTemplate(id: String): Result<TemplateDef> = withContext(Dispatchers.IO) {
        runCatching {
            val t = getJson("/api/expo/template/$id").getJSONObject("template")
            val sections = t.optJSONArray("sections")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val s = arr.getJSONObject(i)
                    TplSection(s.optString("key"), s.optString("type"), s.optString("title"), s.strList("materials"), s.strList("items"))
                }
            } ?: emptyList()
            val pg = t.optJSONArray("price_groups")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val p = arr.getJSONObject(i)
                    TplPriceGroup(p.optString("key"), p.optString("title"), p.strList("fields"))
                }
            } ?: emptyList()
            TemplateDef(t.optString("id"), t.optString("name"), t.strList("header_fields"), sections, pg, t.strList("totals"))
        }
    }

    /** live GET 의 template dict(선택결과) → TemplateSel. matrix=오브젝트배열, checklist=문자열배열. */
    private fun parseTemplateSel(t: JSONObject?): TemplateSel? {
        if (t == null) return null
        val matrix = HashMap<String, List<Pair<String, String>>>()
        val checklist = HashMap<String, List<String>>()
        val prices = HashMap<String, Map<String, Long>>()
        var grand = 0L; var payer = ""
        val keys = t.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k == "prices") {
                val p = t.optJSONObject("prices") ?: continue
                val pk = p.keys()
                while (pk.hasNext()) {
                    val gk = pk.next()
                    when (gk) {
                        "grand_total" -> grand = p.optLong("grand_total")
                        "payer" -> payer = p.optString("payer")
                        else -> p.optJSONObject(gk)?.let { g ->
                            val m = HashMap<String, Long>(); val fk = g.keys()
                            while (fk.hasNext()) { val f = fk.next(); m[f] = g.optLong(f) }
                            prices[gk] = m
                        }
                    }
                }
            } else {
                val arr = t.optJSONArray(k) ?: continue
                if (arr.length() > 0 && arr.optJSONObject(0) != null)
                    matrix[k] = (0 until arr.length()).map { val o = arr.getJSONObject(it); o.optString("item") to o.optString("material") }
                else
                    checklist[k] = (0 until arr.length()).map { arr.optString(it) }
            }
        }
        return TemplateSel(matrix, checklist, prices, grand, payer)
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
    /** submissions item 의 template(선택결과) → TplPick. 없으면 null. */
    private fun parseTplPick(o: JSONObject?): TplPick? {
        if (o == null) return null
        val julnun = o.optJSONArray("julnun")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val x = a.optJSONObject(i) ?: return@mapNotNull null
                val item = x.optString("item"); if (item.isBlank()) return@mapNotNull null
                item to x.optString("material")
            }
        } ?: emptyList()
        fun strs(key: String): List<String> = o.optJSONArray(key)?.let { a ->
            (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
        } ?: emptyList()
        val sili = strs("silicone"); val clean = strs("cleaning")
        if (julnun.isEmpty() && sili.isEmpty() && clean.isEmpty()) return null
        return TplPick(julnun, sili, clean)
    }

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
                        assignedPhone = s.optString("assigned_phone"),
                        createdAtMs = s.optLong("created_at_ms"),
                        apartment = s.optString("apartment"),
                        dongHo = s.optString("dong_ho"),
                        address = s.optString("address"),
                        note = s.optString("note"),
                        scheduledAtMs = s.optLong("scheduled_at_ms"),
                        customerPhone = s.optString("customer_phone"),
                        templateId = s.optString("template_id"),
                        tplPick = parseTplPick(s.optJSONObject("template"))
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

    /** 템플릿 계약 — 상담사 체크+가격 push. matrix: 섹션키→(항목→재질), checklist: 섹션키→선택항목, prices: 그룹키→(필드→금액). 서버가 grand_total 로 final 저장. */
    suspend fun liveAgentTemplate(
        sessionId: String, secret: String,
        matrix: Map<String, Map<String, String>>,
        checklist: Map<String, Set<String>>,
        prices: Map<String, Map<String, Long>>,
        grandTotal: Long, payer: String, note: String = ""
    ): Result<AgentPush> = withContext(Dispatchers.IO) {
        runCatching {
            val tpl = JSONObject()
            matrix.forEach { (secKey, itemMat) ->
                val arr = JSONArray()
                itemMat.forEach { (item, mat) -> arr.put(JSONObject().put("item", item).put("material", mat)) }
                tpl.put(secKey, arr)
            }
            checklist.forEach { (secKey, items) ->
                val arr = JSONArray(); items.forEach { arr.put(it) }; tpl.put(secKey, arr)
            }
            val pricesObj = JSONObject()
            prices.forEach { (gk, fields) -> val g = JSONObject(); fields.forEach { (f, v) -> g.put(f, v) }; pricesObj.put(gk, g) }
            pricesObj.put("grand_total", grandTotal); pricesObj.put("payer", payer)
            tpl.put("prices", pricesObj)
            val o = postJson("/api/expo/contract/live/agent", JSONObject().apply {
                put("session_id", sessionId); put("secret", secret)
                put("template", tpl); put("note", note)
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
                customerConfirmed = o.optBoolean("customer_confirmed"),
                templateId = o.optString("template_id"),
                moveInDate = o.optString("move_in_date"),
                templateSel = parseTemplateSel(o.optJSONObject("template"))
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

    data class BizReg(val bizName: String, val bizNo: String, val repName: String, val address: String)

    /** 사업자등록증 사진 OCR → 업체명·사업자번호 등 추출. image=dataURL(base64). 서버 Vision(Gemini). */
    suspend fun ocrBizReg(imageDataUrl: String): Result<BizReg> = withContext(Dispatchers.IO) {
        runCatching {
            val o = postJsonOcr("/api/expo/ocr/bizreg", JSONObject().apply { put("image", imageDataUrl) })
            BizReg(o.optString("biz_name"), o.optString("biz_no"), o.optString("rep_name"), o.optString("address"))
        }
    }

    /** 약관 사진 OCR → 약관 전문 텍스트. */
    suspend fun ocrTerms(imageDataUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            postJsonOcr("/api/expo/ocr/terms", JSONObject().apply { put("image", imageDataUrl) }).optString("text")
        }
    }

    /** 계약서 사본/영수증 웹 URL (PDF·공유용). */
    fun receiptUrl(contractId: Long): String = "$baseUrl/expo/r/$contractId"
}
