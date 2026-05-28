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
 * cowork 작업 (서버):
 *   1. 신규 DB 테이블 `customer_personas` (phone PK, summary_json, generated_at_ms, model).
 *   2. prepare-reply 호출 시 자동으로:
 *      a. 해당 phone 의 페르소나 24h 이내 있으면 reuse + prepare-reply prompt 에 inject
 *      b. 없거나 stale 면 Haiku 4.5 로 페르소나 새로 생성 (recent_messages + memo 입력)
 *      c. DB INSERT + prompt inject
 *   3. 신규 endpoint `GET /api/customer-persona/{phone}` — 안드 CustomerDetail 카드용.
 *      cache 만 조회 (없으면 null). 새로 생성은 prepare-reply 가 책임.
 *
 * 안드 측 (이 repository):
 *   - GET 호출만. cache fail = null 반환 (UI 가 placeholder 표시).
 *   - cache 없으면 자동 trigger 안 함 (cowork 가 prepare-reply 시 자동 생성).
 *
 * 비용: 페르소나 1건 Haiku 호출 ~₩3. 24h cache 라 사용자 1명당 하루 1회 호출. 비용 0.
 */
class CustomerPersonaRepository(
    private val baseUrl: String = "http://100.86.114.49:8000"
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * 페르소나 조회. cache 만 — 없으면 null. (cowork 의 prepare-reply 가 자동 생성.)
     */
    suspend fun fetch(phone: String): Result<CustomerPersona?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/api/customer-persona/$phone")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    404 -> null   // 아직 페르소나 없음 — prepare-reply 호출 후 생성됨
                    in 200..299 -> {
                        val body = resp.body?.string() ?: throw IOException("빈 응답")
                        parsePersona(body)
                    }
                    else -> throw IOException("HTTP ${resp.code}")
                }
            }
        }
    }

    private fun parsePersona(body: String): CustomerPersona? {
        val json = JSONObject(body)
        return CustomerPersona(
            phone = json.optString("phone"),
            communicationStyle = json.optString("communication_style").takeIf { it.isNotBlank() },
            budgetSignal = json.optString("budget_signal").takeIf { it.isNotBlank() },
            location = json.optString("location").takeIf { it.isNotBlank() },
            schedulePattern = json.optString("schedule_pattern").takeIf { it.isNotBlank() },
            ownerMemo = json.optString("owner_memo").takeIf { it.isNotBlank() },
            generatedAtMs = json.optLong("generated_at_ms"),
            model = json.optString("model").takeIf { it.isNotBlank() }
        )
    }
}

/**
 * 고객 페르소나 — cowork 가 Haiku 로 자동 생성.
 *
 * 모든 필드 nullable: AI 가 추출 못 한 항목은 빈 string 또는 null. UI 는 null 이면 줄 자체 숨김.
 */
data class CustomerPersona(
    val phone: String,
    /** 예: "단답형, 답장 느림 (평균 4시간)" */
    val communicationStyle: String?,
    /** 예: "비싸지 않으면 OK (2025-05-15 언급)" */
    val budgetSignal: String?,
    /** 예: "송파구 잠실엘스 32평 화이트 톤 선호" */
    val location: String?,
    /** 예: "주말 오전 선호" */
    val schedulePattern: String?,
    /** 예: "아이 어림, 무독성 강조 필요" */
    val ownerMemo: String?,
    val generatedAtMs: Long,
    val model: String?
) {
    /** 페르소나가 비어있는지 — 모든 필드 null 이면 UI 가 placeholder 표시. */
    val isEmpty: Boolean
        get() = communicationStyle.isNullOrBlank() &&
            budgetSignal.isNullOrBlank() &&
            location.isNullOrBlank() &&
            schedulePattern.isNullOrBlank() &&
            ownerMemo.isNullOrBlank()
}
