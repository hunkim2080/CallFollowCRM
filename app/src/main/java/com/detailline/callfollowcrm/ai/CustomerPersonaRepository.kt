package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 2026-05-29 킬러콘텐츠 5단계 — 고객 페르소나.
 *
 * cowork (21:00 §17) 구현:
 *   - DB customer_personas (phone PK, persona_text, model_used, source_message_count, generated_at_ms, last_refresh_started_ms)
 *   - prepare-reply 가 자동으로 24h TTL 캐시 + Haiku 4.5 백그라운드 refresh
 *   - GET /api/customer-persona/{phone} — 200 응답 (없으면 persona_text=null + stale=true)
 *
 * 안드 측 (이 repository):
 *   - GET 호출. persona_text 가 비어있으면 isEmpty=true → UI 카드 숨김.
 *   - 옛 5 필드 응답 스키마 (communication_style / budget_signal / ...) 도 fallback 으로 지원.
 *     cowork 가 향후 5 필드 분리 모드로 바꾸거나 두 모드 공존해도 깨지지 않게.
 */
class CustomerPersonaRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * 페르소나 조회. cache 만 — 없거나 persona_text 가 비어있으면 null.
     */
    suspend fun fetch(phone: String): Result<CustomerPersona?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/api/customer-persona/$phone")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    404 -> null   // 옛 안드 사양 fallback (cowork v17 은 404 안 반환하지만 안전망)
                    in 200..299 -> {
                        val body = resp.body?.string() ?: throw IOException("빈 응답")
                        parsePersona(phone, body)
                    }
                    else -> throw IOException("HTTP ${resp.code}")
                }
            }
        }
    }

    private fun parsePersona(phone: String, body: String): CustomerPersona? {
        val json = JSONObject(body)
        // 2026-05-29 cowork §17 결정: 단일 persona_text 자유 텍스트 (한 줄 요약).
        //   "이 고객은 단답형이고 가격에 민감, 송파구 잠실엘스 32평 거주." 형태.
        //   optStringClean = JSON null 을 문자열 "null" 로 돌려주는 org.json 함정 방지(필드마다).
        val personaText = json.optStringClean("persona_text")
        // 옛 5 필드 스키마 (안드 사양) 도 fallback. cowork 가 향후 분리 모드 도입 시 자동 호환.
        val communicationStyle = json.optStringClean("communication_style")
        val budgetSignal = json.optStringClean("budget_signal")
        val location = json.optStringClean("location")
        val schedulePattern = json.optStringClean("schedule_pattern")
        val ownerMemo = json.optStringClean("owner_memo")
        val stale = json.optBoolean("stale", false)

        val allEmpty = personaText == null &&
            communicationStyle == null && budgetSignal == null &&
            location == null && schedulePattern == null && ownerMemo == null
        // 내용도 없고 생성 중(stale)도 아니면 = 보여줄 게 없음 → null (UI 카드 숨김).
        //   내용은 없지만 stale 이면 → 빈 페르소나 반환해서 UI 가 "분석 중" 으로 자연스럽게 표시.
        if (allEmpty && !stale) return null

        val phoneOut = json.optStringClean("phone") ?: phone
        return CustomerPersona(
            phone = phoneOut,
            personaText = personaText,
            communicationStyle = communicationStyle,
            budgetSignal = budgetSignal,
            location = location,
            schedulePattern = schedulePattern,
            ownerMemo = ownerMemo,
            generatedAtMs = json.optLong("generated_at_ms"),
            model = json.optStringClean("model_used") ?: json.optStringClean("model"),
            sourceMessageCount = json.optInt("source_message_count", 0),
            stale = stale,
            refreshStartedAtMs = json.optLong("last_refresh_started_ms").takeIf { it > 0 }
        )
    }

    /** org.json 함정 방지: 키 없음/JSON null/"null"·빈문자 → null. 그 외엔 trim 한 값. */
    private fun JSONObject.optStringClean(key: String): String? {
        if (isNull(key)) return null
        val v = optString(key).trim()
        return v.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }
}

/**
 * 고객 페르소나 — cowork 가 Haiku 4.5 로 자동 생성 (24h TTL, 비동기 refresh).
 *
 * 현재 cowork §17 사양: `personaText` 한 줄. UI 가 우선 표시.
 * 옛 5 필드 (communicationStyle / budgetSignal / ...) 는 fallback — cowork 가 미래에 분리 모드 도입 시 자동 활성화.
 */
data class CustomerPersona(
    val phone: String,
    /** 2026-05-29 cowork §17 — 한 줄 자유 텍스트 ("이 고객은 ..."). 우선 표시. */
    val personaText: String?,
    /** 옛 안드 사양 5 필드 — 모두 null 이지만 fallback 으로 보존. */
    val communicationStyle: String?,
    val budgetSignal: String?,
    val location: String?,
    val schedulePattern: String?,
    val ownerMemo: String?,
    val generatedAtMs: Long,
    val model: String?,
    /** cowork 가 페르소나 만든 근거 메시지 수 — UI 신뢰도 표시. */
    val sourceMessageCount: Int = 0,
    /** cowork 가 24h TTL 만료 → 백그라운드 refresh 중. UI 가 "갱신 중" 작은 표시 가능. */
    val stale: Boolean = false,
    /** stale=true 일 때 refresh 시작 시각. null = refresh 아직 시작 안 됨. */
    val refreshStartedAtMs: Long? = null
) {
    /** 페르소나가 비어있는지. 표시할 게 하나라도 있으면 false. */
    val isEmpty: Boolean
        get() = personaText.isNullOrBlank() &&
            communicationStyle.isNullOrBlank() &&
            budgetSignal.isNullOrBlank() &&
            location.isNullOrBlank() &&
            schedulePattern.isNullOrBlank() &&
            ownerMemo.isNullOrBlank()
}
