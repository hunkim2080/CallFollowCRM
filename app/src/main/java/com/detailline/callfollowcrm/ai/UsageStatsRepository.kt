package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 맥미니 서버의 토큰 사용량 통계 조회.
 *   RINGGO_SERVER_P0P1P2_UPGRADE.md §12 의 GET /api/usage-stats endpoint 호출.
 *
 * 서버 미구현 (404) 또는 네트워크 실패 = Result.failure → UI 가 "서버 모니터링 미구현" 표시.
 * 2026-05-27 사장님 결정: 진짜 토큰 낭비 파악용 모니터링 도구.
 */
class UsageStatsRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = Net.builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    enum class Period(val raw: String) {
        TODAY("today"), MONTH("month"), ALL("all")
    }

    data class EndpointStats(
        val endpoint: String,
        val calls: Int,
        val inputTokens: Long,
        val outputTokens: Long,
        val cacheReadTokens: Long,
        val cacheCreateTokens: Long,
        val costKrw: Int
    )

    data class UsageStats(
        val period: Period,
        val totalCalls: Int,
        val totalInputTokens: Long,
        val totalOutputTokens: Long,
        val totalCacheReadTokens: Long,
        val totalCacheCreateTokens: Long,
        val totalCostKrw: Int,
        val byEndpoint: List<EndpointStats>
    ) {
        val totalTokens: Long
            get() = totalInputTokens + totalOutputTokens + totalCacheReadTokens + totalCacheCreateTokens
    }

    suspend fun fetch(period: Period): Result<UsageStats> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/api/usage-stats?period=${period.raw}")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string() ?: "{}")
                parse(period, json)
            }
        }
    }

    private fun parse(period: Period, json: JSONObject): UsageStats {
        val total = json.optJSONObject("total") ?: JSONObject()
        val byEndpointArr = json.optJSONArray("by_endpoint")
        val byEndpoint = buildList {
            if (byEndpointArr != null) {
                for (i in 0 until byEndpointArr.length()) {
                    val o = byEndpointArr.optJSONObject(i) ?: continue
                    add(
                        EndpointStats(
                            endpoint = o.optString("endpoint"),
                            calls = o.optInt("calls"),
                            inputTokens = o.optLong("input_tokens"),
                            outputTokens = o.optLong("output_tokens"),
                            cacheReadTokens = o.optLong("cache_read_tokens"),
                            cacheCreateTokens = o.optLong("cache_create_tokens"),
                            costKrw = o.optInt("cost_krw")
                        )
                    )
                }
            }
        }
        return UsageStats(
            period = period,
            totalCalls = total.optInt("calls"),
            totalInputTokens = total.optLong("input_tokens"),
            totalOutputTokens = total.optLong("output_tokens"),
            totalCacheReadTokens = total.optLong("cache_read_tokens"),
            totalCacheCreateTokens = total.optLong("cache_create_tokens"),
            totalCostKrw = total.optInt("cost_krw"),
            byEndpoint = byEndpoint
        )
    }
}
