package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.SuggestionEventEntity

@Dao
interface SuggestionEventDao {

    @Insert
    suspend fun insert(event: SuggestionEventEntity): Long

    /** Pending events (서버 보고 안 됨) — batch upload 용. */
    @Query("SELECT * FROM suggestion_events WHERE reportedToServer = 0 ORDER BY createdAtMs ASC LIMIT :limit")
    suspend fun pendingForUpload(limit: Int = 100): List<SuggestionEventEntity>

    @Query("UPDATE suggestion_events SET reportedToServer = 1 WHERE id IN (:ids)")
    suspend fun markReported(ids: List<Long>)

    /** 채택률 표시용 — 최근 N일 기준. SENT_AS_IS / REFINED_THEN_SENT 가 "그대로 채택" 카운트. */
    @Query(
        """
        SELECT action, COUNT(*) AS cnt FROM suggestion_events
        WHERE createdAtMs >= :sinceMs
        GROUP BY action
        """
    )
    suspend fun actionCountsSince(sinceMs: Long): List<ActionCount>

    @Query("SELECT AVG(editDistance) FROM suggestion_events WHERE action = 'EDITED' AND createdAtMs >= :sinceMs")
    suspend fun avgEditDistanceSince(sinceMs: Long): Double?

    @Query("SELECT COUNT(*) FROM suggestion_events WHERE createdAtMs >= :sinceMs")
    suspend fun totalCountSince(sinceMs: Long): Int

    data class ActionCount(val action: String, val cnt: Int)

    // 2026-05-29 킬러콘텐츠 6단계 — 자동 학습 루프.

    /**
     * 시나리오별 채택률 통계. 사장님이 어떤 상황에서 추천을 가장 잘 받는지 발견.
     * adopted = SENT_AS_IS + REFINED_THEN_SENT.
     */
    @Query(
        """
        SELECT
            scenario,
            COUNT(*) AS total,
            SUM(CASE WHEN action IN ('SENT_AS_IS','REFINED_THEN_SENT') THEN 1 ELSE 0 END) AS adopted,
            AVG(CASE WHEN action = 'EDITED' THEN editDistance END) AS avgEdit
        FROM suggestion_events
        WHERE scenario IS NOT NULL AND scenario != ''
          AND createdAtMs >= :sinceMs
        GROUP BY scenario
        ORDER BY total DESC
        """
    )
    suspend fun scenarioStatsSince(sinceMs: Long): List<ScenarioStat>

    /**
     * intent_key 별 채택률 — 어떤 의도가 사장님이 가장 자주 그대로 보내는지.
     * scenario 무관, 전체에서 ranking. (e.g., quote 가 가장 채택률 높으면 "사장님은 가격 답변에 만족")
     */
    @Query(
        """
        SELECT
            intentKey AS intent_key,
            intentLabel AS intent_label,
            COUNT(*) AS total,
            SUM(CASE WHEN action IN ('SENT_AS_IS','REFINED_THEN_SENT') THEN 1 ELSE 0 END) AS adopted
        FROM suggestion_events
        WHERE intentKey IS NOT NULL AND intentKey != ''
          AND createdAtMs >= :sinceMs
        GROUP BY intentKey, intentLabel
        ORDER BY total DESC
        """
    )
    suspend fun intentStatsSince(sinceMs: Long): List<IntentStat>

    data class ScenarioStat(
        val scenario: String,
        val total: Int,
        val adopted: Int,
        val avgEdit: Double?
    )

    data class IntentStat(
        val intent_key: String,
        val intent_label: String?,
        val total: Int,
        val adopted: Int
    )
}
