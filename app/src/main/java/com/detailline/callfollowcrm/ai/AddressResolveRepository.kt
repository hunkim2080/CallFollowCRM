package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 주소 확인 — 문자에서 감지한 주소를 **지도에서 찾아 진짜 주소로** 바꿔본다. (2026-09-16 사장님)
 *
 * 왜:
 *   고객은 "천호동 래미안 101동 1502호" 처럼 말한다. 사람은 알아듣지만 내비게이션은 못 알아듣는다.
 *   서버가 카카오 지도에서 찾아 "서울 강동구 천호동 393-5 (래미안강동팰리스)" 로 바꿔주면,
 *   사장님이 **맞는지 눈으로 확인하고** 등록할 수 있다.
 *
 * 서버: POST /api/address-resolve  (카카오 검색 → 실패하면 AI 가 본문에서 추출)
 *   - candidate_keywords: 지도에서 찾아볼 말(감지한 주소, 건물명 등)
 *   - context_text: 원문 문자 — 카카오가 못 찾을 때 AI 가 읽을 재료
 *
 * ⚠️ 못 찾아도 **실패가 아니다**. 사장님이 문자에서 본 그대로 등록하면 된다 → null 로 조용히 넘어간다.
 */
class AddressResolveRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * 지도에서 찾은 결과.
     * @param resolved  지번 주소 ("서울 강동구 천호동 393-5")
     * @param roadAddress 도로명 주소 (없을 수 있음)
     * @param placeName 장소명 ("래미안강동팰리스")
     * @param confidence 0.9=지도에서 바로 찾음 / 0.8=AI가 뽑은 걸 지도가 확인 / 0.6=AI만 (사장님 확인 필요)
     * @param source "kakao" · "llm+kakao" · "llm"
     */
    data class Resolved(
        val resolved: String,
        val roadAddress: String?,
        val placeName: String?,
        val confidence: Double,
        val source: String?
    )

    /**
     * @param candidates 지도에서 찾아볼 말. 보통 [감지한 주소 전체, 동호수 뺀 앞부분] 순.
     * @param contextText 원문 문자(있으면). 지도가 못 찾을 때 서버 AI 가 읽는다.
     * @return 못 찾으면 null — 호출부는 "문자에 적힌 그대로" 로 진행하면 된다.
     */
    suspend fun resolve(candidates: List<String>, contextText: String? = null): Resolved? =
        withContext(Dispatchers.IO) {
            val cleaned = candidates.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(4)
            if (cleaned.isEmpty() && contextText.isNullOrBlank()) return@withContext null
            val body = JSONObject().apply {
                put("candidate_keywords", JSONArray().apply { cleaned.forEach { put(it) } })
                contextText?.takeIf { it.isNotBlank() }?.let { put("context_text", it.take(1500)) }
            }
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/address-resolve")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val addr = json.optString("resolved").takeIf { it.isNotBlank() && it != "null" }
                        ?: return@use null
                    Resolved(
                        resolved = addr,
                        roadAddress = json.optString("road_address").takeIf { it.isNotBlank() && it != "null" },
                        placeName = json.optString("place_name").takeIf { it.isNotBlank() && it != "null" },
                        confidence = json.optDouble("confidence", 0.0),
                        source = json.optString("source").takeIf { it.isNotBlank() && it != "null" }
                    )
                }
            }.getOrNull()
        }
}
