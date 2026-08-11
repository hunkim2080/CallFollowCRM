package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.data.repository.SmsRepository
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
 * 2026-07-02 문자 기반 가격 추출 — 사장님이 고객에게 보낸 견적 문자를 서버에 보내
 * "이 사장님은 이런 항목을 이 가격에, 이런 기준으로 판다"를 구조화해 돌려받는다.
 *
 * 흐름: SmsRepository.querySentPricingCandidates() → 이 repository.extract() → 2단계 확인화면 → insert.
 * 서버 계약: docs/SERVER_HANDOFF_extract_pricing.md (POST /extract-pricing).
 *   - priceWon 은 '원 단위' 그대로(앱 ×10000 안 함). basisText = 가격 기준(nullable).
 * 프라이버시: 사장님 본인이 '보낸' 문자 본문만 전송. 반드시 동의(toneUploadConsented) 후에만 호출.
 */
class PricingExtractRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {

    private val client: OkHttpClient = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)   // Claude 1콜 추출에 시간 걸릴 수 있음
        .callTimeout(120, TimeUnit.SECONDS)  // 전체 호출 상한 — 추출은 안 자르되 무한 hang만 차단 (2026-08-12 오프라인 감사)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ExtractedItem(
        val title: String,
        /** 원 단위. 서버가 준 값 그대로 저장(앱 ×10000 금지). */
        val priceWon: Long,
        /** "FLAT" | "PYEONG" */
        val unit: String,
        /** "NEW" | "OLD" | "COMMON" */
        val category: String,
        /** 가격 기준 한 줄. 조건부일 때만, 없으면 null. */
        val basisText: String?,
        val confidence: Double
    )

    /**
     * 후보 문자를 서버로 보내 구조화 항목을 추출.
     * @return 추출 항목 리스트(빈 리스트면 뽑을 게 없었던 것). 네트워크/파싱 실패는 Result.failure.
     */
    suspend fun extract(
        deviceId: String,
        ownerTrade: String?,
        candidates: List<SmsRepository.PricingCandidate>
    ): Result<List<ExtractedItem>> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext Result.success(emptyList())
        runCatching {
            val payload = JSONObject().apply {
                put("deviceId", deviceId)
                put("ownerTrade", ownerTrade ?: "")
                put("candidates", JSONArray().apply {
                    candidates.forEach { c ->
                        put(JSONObject().apply {
                            put("body", c.body)
                            put("dateMs", c.dateMs)
                        })
                    }
                })
            }
            val req = Request.Builder()
                .url("$baseUrl/extract-pricing")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                parseItems(body)
            }
        }
    }

    private fun parseItems(body: String): List<ExtractedItem> {
        if (body.isBlank()) return emptyList()
        val arr = JSONObject(body).optJSONArray("items") ?: return emptyList()
        val out = ArrayList<ExtractedItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title").trim()
            if (title.isEmpty()) continue
            val price = o.optLong("priceWon", -1)
            if (price <= 0) continue
            val basis = o.optString("basisText").trim().takeIf { it.isNotEmpty() && it != "null" }
            out += ExtractedItem(
                title = title,
                priceWon = price,
                unit = o.optString("unit", "FLAT").uppercase().let { if (it == "PYEONG") "PYEONG" else "FLAT" },
                category = o.optString("category", "COMMON").uppercase().let {
                    if (it == "NEW" || it == "OLD") it else "COMMON"
                },
                basisText = basis,
                confidence = o.optDouble("confidence", 0.0)
            )
        }
        return out
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
