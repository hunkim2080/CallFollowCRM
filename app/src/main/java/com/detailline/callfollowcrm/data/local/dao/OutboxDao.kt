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

    // 🔑 LATEST 합치기 — payload 를 갈면 **opKey 도 새로** 준다(Fable #1):
    //   같은 opKey 로 새 payload 를 보내면 서버가 **옛 응답을 캐시로 돌려줘** 새 내용(완료·계좌)이 조용히 버려진다.
    //   새 opKey = 서버가 새로 처리한다. attempts 도 새 시작(새 뜻엔 새 재시도 예산).
    @Query("UPDATE outbox SET payloadJson = :payloadJson, opKey = :opKey, attempts = 0, nextAttemptAtMs = 0 WHERE id = :id")
    suspend fun swapPayload(id: Long, payloadJson: String, opKey: String)

    // 아래 셋은 **보낼 때 본 opKey 와 아직 같을 때만** 쓴다 — 보내는 사이에 payload 가 갈렸으면(새 opKey)
    //   그 행은 **건드리지 않는다**(새 내용을 지우거나 백오프 걸지 않게). 반환값 0 = 그새 바뀜.
    @Query("DELETE FROM outbox WHERE id = :id AND opKey = :opKey")
    suspend fun deleteDone(id: Long, opKey: String): Int

    @Query("UPDATE outbox SET attempts = :attempts, nextAttemptAtMs = :nextAt, lastError = :error WHERE id = :id AND opKey = :opKey")
    suspend fun markRetryIf(id: Long, opKey: String, attempts: Int, nextAt: Long, error: String?): Int

    @Query("UPDATE outbox SET status = 'dead', deadReason = :reason, lastError = :error WHERE id = :id AND opKey = :opKey")
    suspend fun markDeadIf(id: Long, opKey: String, reason: String, error: String?): Int

    /** 사장님 「그만 보내기」(id 로 바로 삭제 — 조건 없음). */
    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    /** 홈 띠·목록용 — dead + pending **전부**(시각·주인 거르기는 Kotlin 에서, 시간 흘러도 반영되게). (Fable #7) */
    @Query("SELECT * FROM outbox WHERE status = 'dead' OR status = 'pending' ORDER BY createdAtMs ASC")
    fun observeDeadAndPending(): Flow<List<OutboxEntity>>

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

    /** 사장님이 「다시 보내기」 — 죽었든 미뤘든 **지금 바로** 다시 시도하게 되돌린다. */
    @Query("UPDATE outbox SET status = 'pending', attempts = 0, nextAttemptAtMs = 0, lastError = NULL, deadReason = NULL WHERE id = :id")
    suspend fun resetForRetry(id: Long)

    /** 이 (종류,대상)의 아직 안 보낸 행들(오래된 순). 댓글 「보내는 중」 표시에 쓴다. */
    @Query("SELECT * FROM outbox WHERE kind = :kind AND targetKey = :target AND status = 'pending' ORDER BY createdAtMs ASC")
    suspend fun pendingForKindTarget(kind: String, target: String): List<OutboxEntity>
}
