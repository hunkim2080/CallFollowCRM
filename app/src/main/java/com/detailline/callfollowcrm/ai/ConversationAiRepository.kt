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
            val resp = callServer("$baseUrl/api/card-summary", ctx.toJson())
            val card = resp.optString("summary").takeIf { it.isNotBlank() } ?: return@runCatching
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

            val card = runCatching { callServer("$baseUrl/api/card-summary", body) }
                .getOrNull()?.optString("summary")?.takeIf { it.isNotBlank() }
                ?: existing?.cardSummary

            val convoResp = runCatching { callServer("$baseUrl/api/conversation-summary", body) }.getOrNull()
            val convoJson = convoResp?.optJSONArray("summary_lines")?.toString() ?: existing?.conversationSummaryJson
            val stage = convoResp?.optString("current_stage")?.takeIf { it.isNotBlank() } ?: existing?.conversationStage

            val nextResp = runCatching { callServer("$baseUrl/api/next-action-suggest", body) }.getOrNull()
            val nextJson = nextResp?.toString() ?: existing?.nextActionJson

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
