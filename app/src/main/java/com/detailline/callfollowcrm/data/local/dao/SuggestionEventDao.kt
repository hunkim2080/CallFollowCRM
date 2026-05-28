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
}
