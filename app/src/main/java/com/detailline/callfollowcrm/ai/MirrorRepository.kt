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
 * 본폰 "일정 미러 링크" (2026-07-13) — 업무폰 일정을 읽기전용으로 본폰에서 보기.
 *   서버(cowork 추가119)가 링크·비번게이트·뷰어 HTML(/mirror/{token})을 전부 그림. 앱은 스냅샷만 push.
 *   설계: docs/SERVER_HANDOFF_mirror.md · 앱측 지시: docs/ANDROID_HANDOFF_mirror_app.md.
 *
 * 옵트인 필수 — 사장님이 직접 켤 때만 발급/전송. 자동 활성화 금지(처리방침 §2-2-2 명시).
 *
 *   POST /api/mirror/issue        링크 발급(비번 1회 내려줌 · 해시만 저장 = 다시 못 봄)
 *   POST /api/mirror/pair/code    두 번째 업무폰 합치기용 6자리 코드(10분)
 *   POST /api/mirror/pair         코드로 기존 링크에 합류
 *   POST /api/mirror/snapshot     일정+돈 스냅샷 통째 갱신(덮어쓰기)
 *   POST /api/mirror/revoke       링크 폐기(본폰 분실) → 이후 410
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

    /** 발급 결과. pin 은 서버가 만들었을 때만(딱 한 번) 내려옴 — 저장 금지, 사장님께 크게 보여주고 버림. */
    data class IssueResult(
        val token: String,
        val url: String,
        val label: String,
        val pin: String?,
        val pinNotice: String?
    )

    data class PairCodeResult(val code: String, val validMinutes: Int)

    /** 뷰어에 그릴 현장 1건. date=YYYY-MM-DD(필수). 나머지 선택. */
    data class MirrorItem(
        val date: String,
        val time: String?,
        val days: Int,
        val name: String,
        val address: String?,
        val phone: String?,
        val memo: String?,
        val completed: Boolean
    )

    /** 링크 발급. 이미 그 업무폰이 링크에 속하면 서버가 기존 것 재사용(label 만 갱신). */
    suspend fun issue(
        ownerPhone: String,
        label: String,
        tint: Int = 0,
        pin: String? = null
    ): Result<IssueResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("owner_phone", ownerPhone)
                put("label", label)
                put("tint", tint)
                pin?.takeIf { it.isNotBlank() }?.let { put("pin", it) }
            }
            val req = Request.Builder()
                .url("$baseUrl/api/mirror/issue")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val o = JSONObject(resp.body?.string().orEmpty())
                IssueResult(
                    token = o.getString("token"),
                    url = o.optString("url"),
                    label = o.optString("label", label),
                    pin = o.optString("pin").takeIf { it.isNotBlank() && it != "null" },
                    pinNotice = o.optString("pinNotice").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }
    }

    /** 두 번째 업무폰 합치기용 코드 발급(6자리·10분·1회). 이 폰(또는 이미 등록된 폰)에서 호출. */
    suspend fun pairCode(ownerPhone: String): Result<PairCodeResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply { put("owner_phone", ownerPhone) }
            val req = Request.Builder()
                .url("$baseUrl/api/mirror/pair/code")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val o = JSONObject(resp.body?.string().orEmpty())
                PairCodeResult(
                    code = o.optString("code"),
                    validMinutes = o.optInt("validMinutes", 10)
                )
            }
        }
    }

    /** 코드로 기존 링크에 합류(두 번째 업무폰). 성공하면 이 폰도 같은 token 을 씀 → 서버가 token 반환. */
    suspend fun pair(
        code: String,
        ownerPhone: String,
        label: String,
        tint: Int = 1
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("code", code)
                put("owner_phone", ownerPhone)
                put("label", label)
                put("tint", tint)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/mirror/pair")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val o = JSONObject(resp.body?.string().orEmpty())
                o.optString("token")
            }
        }
    }

    /** 일정+돈 스냅샷 통째 갱신(덮어쓰기). 항상 전체를 보낸다. money 는 사업장별 → 뷰어가 합산. */
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

    /** 링크 폐기(본폰 분실). 이후 그 링크는 410. */
    suspend fun revoke(ownerPhone: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("owner_phone", ownerPhone)
                put("token", token)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/mirror/revoke")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                Unit
            }
        }
    }
}
