package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow

/** 우체통 표 접근. 합치기·순서 **셈은 domain/outbox/OutboxRules** 에 있고, 여기선 읽고/쓰기만 한다. */
@Dao
interface OutboxDao {

    @Insert
    suspend fun insert(row: OutboxEntity): Long

    /** 같은 targetKey 의 pending 행들(합치기 판단용). */
    @Query("SELECT * FROM outbox WHERE status = 'pending' AND targetKey = :targetKey ORDER BY id ASC")
    suspend fun pendingForTarget(targetKey: String): List<OutboxEntity>

    /** 지금 보낼 수 있는 pending 행들(시각 도래). 넣은 순서. */
    @Query("SELECT * FROM outbox WHERE status = 'pending' AND nextAttemptAtMs <= :now ORDER BY id ASC")
    suspend fun dueNow(now: Long): List<OutboxEntity>

    @Query("UPDATE outbox SET payloadJson = :payloadJson, nextAttemptAtMs = 0 WHERE id = :id")
    suspend fun swapPayload(id: Long, payloadJson: String)

    @Query("UPDATE outbox SET attempts = :attempts, nextAttemptAtMs = :nextAt, lastError = :error WHERE id = :id")
    suspend fun markRetry(id: Long, attempts: Int, nextAt: Long, error: String?)

    @Query("UPDATE outbox SET status = 'dead', deadReason = :reason, lastError = :error WHERE id = :id")
    suspend fun markDead(id: Long, reason: String, error: String?)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM outbox WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    /** 사장님께 보일 것: dead 이거나 하루 넘게 못 보낸 pending. (띠·진단용) */
    @Query(
        """
        SELECT * FROM outbox
        WHERE status = 'dead' OR (status = 'pending' AND createdAtMs <= :staleBefore)
        ORDER BY createdAtMs ASC
        """
    )
    fun observeTrouble(staleBefore: Long): Flow<List<OutboxEntity>>

    /** 진단 본문용(한 번 읽기). */
    @Query(
        """
        SELECT * FROM outbox
        WHERE status = 'dead' OR (status = 'pending' AND createdAtMs <= :staleBefore)
        ORDER BY createdAtMs ASC
        """
    )
    suspend fun troubleNow(staleBefore: Long): List<OutboxEntity>

    /** 30일 지난 dead 자동 삭제. */
    @Query("DELETE FROM outbox WHERE status = 'dead' AND createdAtMs <= :before")
    suspend fun purgeOldDead(before: Long)

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'pending'")
    suspend fun pendingCount(): Int

    /** 이 종류로 이미 우체통에 있는 targetKey 들(pending·dead 다). 사진 feeder 가 이미 넣은(또는 죽은) 걸 또 안 넣게. */
    @Query("SELECT DISTINCT targetKey FROM outbox WHERE kind = :kind")
    suspend fun targetsForKind(kind: String): List<String>
}
