package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.data.local.dao.AiSummaryDao
import com.detailline.callfollowcrm.data.local.entity.AiSummaryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
 * P0+P1+P2 대화 AI Repository.
 *
 * 서버 endpoint 3개 (RINGGO_SERVER_P0P1P2_UPGRADE.md):
 *  - POST /api/card-summary       — HomeScreen 카드 한 줄 요약
 *  - POST /api/conversation-summary — ChatScreen 상단 3-5줄 요약
 *  - POST /api/next-action-suggest  — AI 제안 박스 (다음 액션)
 *
 * 결과는 [AiSummaryEntity] 한 row 에 통합 저장 (phoneSuffix 키).
 * 무효화: latestMessageTimestampMs 가 기존 캐시보다 크면 stale → 재호출.
 *
 * 서버 미구현 상태에서도 안전 — 호출 실패 silent, 기존 캐시 그대로 표시.
 */
class ConversationAiRepository(
    private val dao: AiSummaryDao,
    private val baseUrl: String = "http://100.86.114.49:8000"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    fun observe(suffix: String): Flow<AiSummaryEntity?> = dao.observe(suffix)
    fun observeMany(suffixes: List<String>): Flow<List<AiSummaryEntity>> = dao.observeMany(suffixes)
    suspend fun get(suffix: String): AiSummaryEntity? = dao.get(suffix)

    /**
     * P0 (card-summary) 만 호출 — HomeScreen 가시 카드 prefetch 용. 가벼움.
     * stale 일 때만 호출. fresh 면 no-op.
     */
    suspend fun ensureCardSummary(ctx: SummaryContext): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = dao.get(ctx.phoneSuffix)
            if (existing != null && existing.cardSummary != null
                && existing.latestMessageTimestampMs >= ctx.latestMessageTimestampMs) {
                return@runCatching
            }
            // 2026-05-30 사장님 #8 통점 fix:
            //   기존: 빈 응답이면 early return → cache 미저장 → 다음 호출 시 또 stale → 또 호출 → 또 빈
            //   → UI 가 영영 "요약 작성 중" (114 같은 광고 메시지에서 무한 로딩).
            //   해결: 호출 후 빈 응답이라도 cache 에 빈 string ("") 저장 + latestMessageTimestampMs 갱신.
            //   UI 는 null = 시도 안 함, "" = 시도했으나 응답 없음 으로 구분.
            //   네트워크 실패 (예외) 는 그대로 throw — runCatching 가 try/catch.
            val resp = callServer("$baseUrl/api/card-summary", ctx.toJson())
            val card = resp.optString("summary").takeIf { it.isNotBlank() } ?: ""
            val merged = (existing ?: AiSummaryEntity(phoneSuffix = ctx.phoneSuffix)).copy(
                cardSummary = card,
                latestMessageTimestampMs = ctx.latestMessageTimestampMs,
                generatedAtMs = System.currentTimeMillis()
            )
            dao.upsert(merged)
        }
    }

    /**
     * P0 + P1 + P2 모두 호출 — ChatScreen 진입 시.
     * 캐시 stale 일 때만 호출. 세 endpoint 순차 호출.
     */
    suspend fun ensureFullSummary(ctx: SummaryContext): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = dao.get(ctx.phoneSuffix)
            val isFresh = existing != null
                && existing.cardSummary != null
                && existing.conversationSummaryJson != null
                && existing.nextActionJson != null
                && existing.latestMessageTimestampMs >= ctx.latestMessageTimestampMs
            if (isFresh) return@runCatching

            val body = ctx.toJson()

            // 2026-05-30 사장님 #8 통점 fix:
            //   기존: 빈 응답이면 existing 값 그대로 fallback → existing 도 null 이면 cache 의 필드들이 null →
            //   UI 의 SummaryLoadingPlaceholder 가 영영 "대화 요약 작성 중" (114 광고 메시지에서 무한 로딩).
            //   해결: 빈 응답이라도 "" sentinel 저장 + latestMessageTimestampMs 갱신.
            //   UI 는 null = 시도 안 함, "" = 시도했으나 응답 없음 으로 구분.
            val card = runCatching { callServer("$baseUrl/api/card-summary", body) }
                .getOrNull()?.optString("summary")?.takeIf { it.isNotBlank() }
                ?: existing?.cardSummary
                ?: ""   // 빈 응답 + existing 없음 = "시도했으나 응답 없음" sentinel

            val convoResp = runCatching { callServer("$baseUrl/api/conversation-summary", body) }.getOrNull()
            val convoJson = convoResp?.optJSONArray("summary_lines")?.toString()
                ?: existing?.conversationSummaryJson
                ?: "[]"   // 빈 응답 = "[]" sentinel
            val stage = convoResp?.optString("current_stage")?.takeIf { it.isNotBlank() }
                ?: existing?.conversationStage

            val nextResp = runCatching { callServer("$baseUrl/api/next-action-suggest", body) }.getOrNull()
            val nextJson = nextResp?.toString()
                ?: existing?.nextActionJson
                ?: "{}"   // 빈 응답 = "{}" sentinel

            val merged = (existing ?: AiSummaryEntity(phoneSuffix = ctx.phoneSuffix)).copy(
                cardSummary = card,
                conversationSummaryJson = convoJson,
                conversationStage = stage,
                nextActionJson = nextJson,
                latestMessageTimestampMs = ctx.latestMessageTimestampMs,
                generatedAtMs = System.currentTimeMillis()
            )
            dao.upsert(merged)
        }
    }

    private fun callServer(url: String, jsonBody: String): JSONObject {
        val req = Request.Builder().url(url).post(jsonBody.toRequestBody(JSON)).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return JSONObject(resp.body?.string() ?: "{}")
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * 세 endpoint 의 공통 입력. toJson() 으로 직렬화.
 */
data class SummaryContext(
    val phone: String,
    val phoneSuffix: String,
    val customerName: String? = null,
    val customerStatus: String? = null,
    val customerMemo: String? = null,
    val leadHeat: String? = null,
    val depositPaid: Boolean = false,
    val scheduledWorkDate: Long? = null,
    val recentMessages: List<HistoryMessage> = emptyList(),
    val ownerToneSamples: List<String> = emptyList(),
    /** P3 — 사장님의 다른 시공 일정 (현재 고객 제외, 앞으로 14일 내). 일정 답변 근거. */
    val otherUpcomingSchedulesMs: List<Long> = emptyList(),
    /** 무효화 기준. 0 이면 메시지 없어도 호출 가능. */
    val latestMessageTimestampMs: Long = 0L
) {
    fun toJson(): String = JSONObject().apply {
        put("phone", phone)
        customerName?.let { put("customer_name", it) }
        customerStatus?.let { put("customer_status", it) }
        customerMemo?.let { put("customer_memo", it) }
        leadHeat?.let { put("lead_heat", it) }
        put("deposit_paid", depositPaid)
        scheduledWorkDate?.let { put("scheduled_work_date", it) }
        put("recent_messages", JSONArray().apply {
            recentMessages.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("body", msg.body)
                    put("timestamp_ms", msg.timestampMs)
                })
            }
        })
        put("call_summaries", JSONArray()) // 추후 — 에이닷 통화 요약 연동 시
        put("owner_tone_samples", JSONArray().apply {
            ownerToneSamples.forEach { put(it) }
        })
        // P3 — 다른 고객 이름은 leak 금지. ms 만 전달, 서버가 요일/오전오후로 가공해서 prompt 에 주입.
        put("other_upcoming_schedules_ms", JSONArray().apply {
            otherUpcomingSchedulesMs.forEach { put(it) }
        })
    }.toString()
}

/**
 * Next Action 파싱 helper — nextActionJson 을 UI 에서 쓰기 좋은 형태로.
 */
data class NextAction(
    val actionType: String,
    val title: String,
    val subtitle: String?,
    val primaryLabel: String?,
    val primaryAction: String?,
    val urgency: String
) {
    companion object {
        fun parse(json: String?): NextAction? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val obj = JSONObject(json)
                val actionType = obj.optString("action_type").takeIf { it.isNotBlank() } ?: return@runCatching null
                if (actionType == "none") return@runCatching null
                val primary = obj.optJSONObject("primary_action")
                NextAction(
                    actionType = actionType,
                    title = obj.optString("title"),
                    subtitle = obj.optString("subtitle").takeIf { it.isNotBlank() },
                    primaryLabel = primary?.optString("label")?.takeIf { it.isNotBlank() },
                    primaryAction = primary?.optString("action")?.takeIf { it.isNotBlank() },
                    urgency = obj.optString("urgency", "medium")
                )
            }.getOrNull()
        }
    }
}

fun parseConversationLines(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}
